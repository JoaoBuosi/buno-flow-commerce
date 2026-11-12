package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
  "commerce.mfa.owner-required=false",
  "commerce.mfa.self-enrollment-enabled=false",
  "commerce.mfa.pilot-email=",
  "commerce.mfa.encryption-key=",
  "commerce.edge-required=false",
  "commerce.security.ip-rate-limit-enabled=false",
  "commerce.mail.enabled=false"
})
@AutoConfigureMockMvc
class OwnerMfaEnrollmentGateIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test void noIndependentKeyOrOptInFlagMeansNoVoluntaryEnrollment() throws Exception {
    String email="self-enrollment-gated-"+UUID.randomUUID()+"@example.invalid";
    String password="synthetic-qa-password-required-2026";
    var signup=mvc.perform(post("/api/v1/auth/register")
      .with(req->{req.setRemoteAddr("192.0.2.233");return req;})
      .contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(Map.of(
        "email",email,"password",password,"storeName","QA Escrow Gate"))))
      .andExpect(status().isOk()).andReturn();
    JsonNode session=mapper.readTree(signup.getResponse().getContentAsString());
    String token="Bearer "+session.get("accessToken").asText();
    String store=session.get("storeId").asText();

    var state=mvc.perform(get("/api/v1/auth/mfa/status")
      .header("Authorization",token).header("X-Store-Id",store))
      .andExpect(status().isOk()).andReturn();
    JsonNode mfa=mapper.readTree(state.getResponse().getContentAsString());
    assertFalse(mfa.get("required").asBoolean());
    assertFalse(mfa.get("enrolled").asBoolean());
    assertFalse(mfa.get("enrollmentAvailable").asBoolean());

    mvc.perform(post("/api/v1/auth/mfa/self/enroll")
      .with(req->{req.setRemoteAddr("192.0.2.234");return req;})
      .header("Authorization",token).header("X-Store-Id",store)
      .contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(Map.of("currentPassword",password))))
      .andExpect(status().isServiceUnavailable());

    mvc.perform(get("/api/v1/me")
      .header("Authorization",token).header("X-Store-Id",store))
      .andExpect(status().isOk());
  }
}
