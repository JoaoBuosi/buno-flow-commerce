package dev.buno.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
  "commerce.edge-required=true",
  "commerce.edge-secret=ci-server-only-edge-secret-of-at-least-32-characters",
  "commerce.security.ip-rate-limit-enabled=true"
})
@AutoConfigureMockMvc
class AuthIpRateLimitEdgeIntegrationTest {
  @Autowired MockMvc mvc;
  static final String KEY="ci-server-only-edge-secret-of-at-least-32-characters";

  @Test void verifiedGatewaySeparatesRealClientIpsBehindTheSameProxy() throws Exception {
    var fromA=post("/api/v1/auth/login")
      .header("X-Buno-Edge-Key",KEY)
      .header("X-Buno-Client-IP","203.0.113.44")
      .with(req->{req.setRemoteAddr("10.10.0.2");return req;})
      .header("Content-Type","application/json")
      .content("{\"email\":\"nobody@example.test\",\"password\":\"invalid\"}");
    for(int i=0;i<30;i++) mvc.perform(fromA).andExpect(status().isUnauthorized());
    mvc.perform(fromA).andExpect(status().isTooManyRequests());

    var fromB=post("/api/v1/auth/login")
      .header("X-Buno-Edge-Key",KEY)
      .header("X-Buno-Client-IP","203.0.113.45")
      .with(req->{req.setRemoteAddr("10.10.0.2");return req;})
      .header("Content-Type","application/json")
      .content("{\"email\":\"nobody@example.test\",\"password\":\"invalid\"}");
    mvc.perform(fromB).andExpect(status().isUnauthorized());
  }

  @Test void spoofedClientIpWithoutGatewaySecretCannotReachAuth() throws Exception {
    mvc.perform(post("/api/v1/auth/login")
      .header("X-Buno-Client-IP","203.0.113.88")
      .header("X-Forwarded-For","203.0.113.89")
      .header("Content-Type","application/json")
      .content("{\"email\":\"test@example.test\",\"password\":\"invalid\"}"))
      .andExpect(status().isForbidden());
  }
}
