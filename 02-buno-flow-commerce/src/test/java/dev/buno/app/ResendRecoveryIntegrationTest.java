package dev.buno.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resend transport is mocked: CI must not contact third-party provider or consume API keys. */
@SpringBootTest(properties={
  "commerce.mail.enabled=true",
  "commerce.mail.provider=resend",
  "commerce.mail.resend-api-key=unit-test-value-not-real",
  "commerce.mail.from=onboarding@resend.dev",
  "commerce.mail.frontend-url=https://flowcommerce.example.test"
})
@AutoConfigureMockMvc
class ResendRecoveryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean ResendEmailSender resend;

  @BeforeEach void configured() {
    when(resend.configured()).thenReturn(true);
  }

  @Test void sendsThroughHttpsTransportWithoutExposingTokenInResponse() throws Exception {
    String email="resend-"+UUID.randomUUID()+"@example.test";
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of(
        "email",email,"password","initial-safe-password-2026","storeName","Loja QA HTTPS"))))
      .andExpect(status().isOk());
    var response=mvc.perform(post("/api/v1/auth/reset/request")
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of("email",email))))
      .andExpect(status().isAccepted()).andReturn();
    assertFalse(response.getResponse().getContentAsString().contains("reset_token="));
    var message=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(resend).send(message.capture());
    assertEquals(email,message.getValue().getTo()[0]);
    assertEquals("onboarding@resend.dev",message.getValue().getFrom());
    assertTrue(message.getValue().getText().contains("https://flowcommerce.example.test/?reset_token="));
  }

  @Test void duplicateResetInsideCooldownReturnsAcceptedButSendsOnlyOnce() throws Exception {
    String email="resend-throttled-"+UUID.randomUUID()+"@example.test";
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of(
        "email",email,"password","initial-safe-password-2026",
        "storeName","Loja QA Rate Limit"))))
      .andExpect(status().isOk());

    String request=json.writeValueAsString(Map.of("email",email));
    for(int attempt=0;attempt<3;attempt++) {
      mvc.perform(post("/api/v1/auth/reset/request")
        .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isAccepted());
    }
    // Same generic HTTP response, but no duplicate provider call or token rotation.
    verify(resend,times(1)).send(any(SimpleMailMessage.class));
    Integer rows=db.queryForObject("""
      SELECT count(*) FROM password_resets p JOIN users u ON u.id=p.user_id
      WHERE u.email=?
      """,Integer.class,email);
    assertEquals(1,rows,"Account must retain a single valid recovery link");

    String absent="missing-"+UUID.randomUUID()+"@example.test";
    mvc.perform(post("/api/v1/auth/reset/request")
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of("email",absent))))
      .andExpect(status().isAccepted());
    verify(resend,times(1)).send(any(SimpleMailMessage.class));
  }

  @Test void providerFailureRollsBackResetAndReturnsGenericAccepted() throws Exception {
    String email="resend-failure-"+UUID.randomUUID()+"@example.test";
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of(
        "email",email,"password","initial-safe-password-2026","storeName","Loja QA HTTPS"))))
      .andExpect(status().isOk());
    doThrow(new MailSendException("Simulated Resend error (no HTTP call)"))
      .when(resend).send(any(SimpleMailMessage.class));
    mvc.perform(post("/api/v1/auth/reset/request")
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(Map.of("email",email))))
      .andExpect(status().isAccepted());
    var pending=db.queryForObject("""
      SELECT count(*) FROM password_resets p JOIN users u ON u.id=p.user_id
      WHERE u.email=?
      """,Integer.class,email);
    assertEquals(0,pending);
  }
}
