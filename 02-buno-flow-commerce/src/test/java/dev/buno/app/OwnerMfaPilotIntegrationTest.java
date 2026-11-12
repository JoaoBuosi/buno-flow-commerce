package dev.buno.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
  "commerce.mfa.owner-required=false",
  "commerce.mfa.pilot-email=owner-mfa-pilot@example.invalid",
  "commerce.mfa.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
  "commerce.security.ip-rate-limit-enabled=false",
  "commerce.mail.enabled=false"
})
@AutoConfigureMockMvc
class OwnerMfaPilotIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  private com.fasterxml.jackson.databind.JsonNode postAuth(String path,Map<String,String> values) throws Exception{
    var result=mvc.perform(post("/api/v1/auth/"+path)
      .with(req->{req.setRemoteAddr("192.0.2."+((UUID.randomUUID().hashCode()&127)+40));return req;})
      .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(values)))
      .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  @Test void onlyExactPilotOwnerRequiresMfaAndExistingOwnerStillWorks() throws Exception {
    String nonPilot="regular-"+UUID.randomUUID()+"@example.invalid";
    var regular=postAuth("register",Map.of(
      "email",nonPilot,"password","valid-password-for-normal-owner-2026","storeName","Loja Existente"));
    assertTrue(regular.hasNonNull("accessToken"),"Normal owners retain their sessions");
    mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+regular.get("accessToken").asText())
      .header("X-Store-Id",regular.get("storeId").asText()))
      .andExpect(status().isOk());

    var pilot=postAuth("register",Map.of(
      "email","owner-mfa-pilot@example.invalid",
      "password","valid-password-for-pilot-owner-2026","storeName","Loja Piloto"));
    assertTrue(pilot.get("mfaRequired").asBoolean());
    assertTrue(pilot.get("enrollmentRequired").asBoolean());
    assertFalse(pilot.has("accessToken"));
    mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+pilot.get("challengeToken").asText())
      .header("X-Store-Id",pilot.get("storeId").asText()))
      .andExpect(status().isUnauthorized());

    var normalLogin=postAuth("login",Map.of("email",nonPilot,
      "password","valid-password-for-normal-owner-2026"));
    assertTrue(normalLogin.hasNonNull("accessToken"));
    var pilotLogin=postAuth("login",Map.of("email","owner-mfa-pilot@example.invalid",
      "password","valid-password-for-pilot-owner-2026"));
    assertTrue(pilotLogin.get("mfaRequired").asBoolean());
    assertFalse(pilotLogin.has("accessToken"));
    mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+regular.get("accessToken").asText())
      .header("X-Store-Id",regular.get("storeId").asText()))
      .andExpect(status().isOk());
  }
}
