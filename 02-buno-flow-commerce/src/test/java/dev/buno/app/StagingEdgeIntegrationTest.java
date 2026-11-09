package dev.buno.app;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression tests: network-facing staging API is not an open registration API. */
@SpringBootTest(properties={
  "commerce.edge-required=true",
  "commerce.edge-secret=ci-server-only-edge-secret",
  "commerce.pilot-only=true",
  "commerce.pilot-invite-code=ci-pilot-invite"
})
@AutoConfigureMockMvc
class StagingEdgeIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test void missingOrWrongEdgeSecretIsRejectedBeforePublicAuthHandlers() throws Exception {
    String payload=mapper.writeValueAsString(Map.of(
      "email","edge-"+UUID.randomUUID()+"@example.test",
      "password","long-valid-password-2026",
      "storeName","Do not create me"));
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Pilot-Code","ci-pilot-invite")
        .contentType(MediaType.APPLICATION_JSON).content(payload))
      .andExpect(status().isForbidden())
      .andExpect(header().string("Cache-Control","no-store"));
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Buno-Edge-Key","wrong-secret")
        .header("X-Pilot-Code","ci-pilot-invite")
        .contentType(MediaType.APPLICATION_JSON).content(payload))
      .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/dashboard")
        .header("Authorization","Bearer forged")
        .header("X-Store-Id",UUID.randomUUID().toString()))
      .andExpect(status().isForbidden());
  }

  @Test void validGatewayDoesNotBypassUserAuthenticationOrPilotInvitation() throws Exception {
    mvc.perform(get("/api/v1/orders").header("X-Buno-Edge-Key","ci-server-only-edge-secret"))
      .andExpect(status().isUnauthorized());
    String payload=mapper.writeValueAsString(Map.of(
      "email","edge-"+UUID.randomUUID()+"@example.test",
      "password","long-valid-password-2026",
      "storeName","Protected QA"));
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Buno-Edge-Key","ci-server-only-edge-secret")
        .contentType(MediaType.APPLICATION_JSON).content(payload))
      .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Buno-Edge-Key","ci-server-only-edge-secret")
        .header("X-Pilot-Code","ci-pilot-invite")
        .contentType(MediaType.APPLICATION_JSON).content(payload))
      .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test void optionsWithoutGatewaySecretCannotProbeApi() throws Exception {
    mvc.perform(options("/api/v1/orders")
        .header("Origin","https://untrusted.test")
        .header("Access-Control-Request-Method","GET"))
      .andExpect(status().isForbidden());
  }
}
