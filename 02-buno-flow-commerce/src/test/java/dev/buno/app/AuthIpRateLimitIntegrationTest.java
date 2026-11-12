package dev.buno.app;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
  "commerce.security.ip-rate-limit-enabled=true",
  "commerce.security.ip-hash-key=integration-test-only-hmac-secret-more-than-32-characters",
  "commerce.edge-required=false",
  "commerce.mail.enabled=false"
})
@AutoConfigureMockMvc
class AuthIpRateLimitIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;

  private MockHttpServletRequestBuilder login(String remote,String spoof) {
    return post("/api/v1/auth/login")
      .with(req->{req.setRemoteAddr(remote);return req;})
      .header("Content-Type","application/json")
      .header("X-Buno-Client-IP",spoof)
      .header("X-Forwarded-For",spoof)
      .content("{\"email\":\"absent@example.test\",\"password\":\"incorrect\"}");
  }

  @Test void blocksAfterThirtyAttemptsEvenWhenClientForgesForwardedHeaders() throws Exception {
    String remote="198.51.100."+((UUID.randomUUID().hashCode()&0x7f)+1);
    for(int i=0;i<30;i++) {
      mvc.perform(login(remote,"203.0.113."+((i%50)+1)))
        .andExpect(status().isUnauthorized());
      Integer observed=db.queryForObject(
        "SELECT max(requests) FROM flow_commerce.auth_ip_rate_limits WHERE route_family='login'",
        Integer.class);
      assertEquals(i+1,observed,"Each request must consume exactly one IP-rate slot");
    }
    mvc.perform(login(remote,"203.0.113.250"))
      .andExpect(status().isTooManyRequests());

    // A second TCP client is tracked separately.
    mvc.perform(login("2001:db8::f00",remote)).andExpect(status().isUnauthorized());
    var limit=db.queryForObject(
      "SELECT max(requests) FROM flow_commerce.auth_ip_rate_limits WHERE route_family='login'",
      Integer.class);
    assertNotNull(limit);
    assertTrue(limit>=31);
    var invalid=db.queryForObject(
      "SELECT count(*) FROM flow_commerce.auth_ip_rate_limits WHERE ip_hash LIKE '%.%' OR ip_hash LIKE '%:%'",
      Integer.class);
    assertEquals(0,invalid,"Only hex HMAC digests, never raw IPs");
  }

  @Test void passwordResetLimitsApplyIndependentlyOfLoginLimit() throws Exception {
    String remote="192.0.2."+((UUID.randomUUID().hashCode()&0x7f)+1);
    for(int i=0;i<10;i++) {
      mvc.perform(post("/api/v1/auth/reset/request")
        .with(req->{req.setRemoteAddr(remote);return req;})
        .header("Content-Type","application/json")
        .content("{\"email\":\"not-a-registered-user@example.test\"}"))
        .andExpect(status().isServiceUnavailable()); // Mail is intentionally disabled in CI.
    }
    mvc.perform(post("/api/v1/auth/reset/request")
      .with(req->{req.setRemoteAddr(remote);return req;})
      .header("Content-Type","application/json")
      .content("{\"email\":\"not-a-registered-user@example.test\"}"))
      .andExpect(status().isTooManyRequests());
  }
}
