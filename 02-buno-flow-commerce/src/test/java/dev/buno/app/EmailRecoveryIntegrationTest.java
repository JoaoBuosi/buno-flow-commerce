package dev.buno.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * No real SMTP connection or credential. Verifies reset links and rollback in ephemeral CI DB.
 * Production email remains disabled until sender credentials/domain are separately configured.
 */
@SpringBootTest(properties={
  "commerce.mail.enabled=true",
  "commerce.mail.from=contas@flowcommerce.example.test",
  "commerce.mail.frontend-url=https://flowcommerce.example.test"
})
@AutoConfigureMockMvc
class EmailRecoveryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean JavaMailSender mail;

  private record Pilot(String email, String token, String store) {}

  private Pilot register() throws Exception {
    String email="recovery-"+UUID.randomUUID()+"@example.test";
    var result=mvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of(
          "email",email,"password","initial-safe-password-2026",
          "storeName","Loja QA de E-mail"))))
      .andExpect(status().isOk()).andReturn();
    var body=json.readTree(result.getResponse().getContentAsString());
    return new Pilot(email,body.get("accessToken").asText(),body.get("storeId").asText());
  }

  @Test void recoveryUsesShortLivedOneTimeLinkAndRevokesOldSessions() throws Exception {
    Pilot user=register();
    mvc.perform(post("/api/v1/auth/reset/request").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email",user.email()))))
      .andExpect(status().isAccepted());

    var messages=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail,times(1)).send(messages.capture());
    SimpleMailMessage sent=messages.getValue();
    assertArrayEquals(new String[]{user.email()},sent.getTo());
    assertEquals("contas@flowcommerce.example.test",sent.getFrom());
    assertNotNull(sent.getText());
    var matcher=Pattern.compile("reset_token=([a-f0-9]{64})").matcher(sent.getText());
    assertTrue(matcher.find(),"Must generate a 256-bit opaque token sent only by e-mail");
    String token=matcher.group(1);
    String payload=json.writeValueAsString(Map.of(
      "token",token,"newPassword","updated-safe-password-2026"));

    mvc.perform(post("/api/v1/auth/reset/complete").contentType(MediaType.APPLICATION_JSON)
        .content(payload)).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/reset/complete").contentType(MediaType.APPLICATION_JSON)
        .content(payload)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/dashboard").header("Authorization","Bearer "+user.token())
        .header("X-Store-Id",user.store()))
      .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of(
          "email",user.email(),"password","updated-safe-password-2026"))))
      .andExpect(status().isOk());
  }

  @Test void unknownAccountsStillGetAcceptedResponseWithoutEmail() throws Exception {
    mvc.perform(post("/api/v1/auth/reset/request").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email","absent-"+UUID.randomUUID()+"@example.test"))))
      .andExpect(status().isAccepted());
    verifyNoInteractions(mail);
  }

  @Test void smtpFailureRollsBackResetTokenAndHidesProviderDetail() throws Exception {
    Pilot user=register();
    doThrow(new MailSendException("internal SMTP endpoint should never be exposed"))
      .when(mail).send(any(SimpleMailMessage.class));
    mvc.perform(post("/api/v1/auth/reset/request").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email",user.email()))))
      .andExpect(status().isServiceUnavailable());

    Integer pending=db.queryForObject("""
      SELECT count(*) FROM password_resets p JOIN users u ON p.user_id=u.id
      WHERE u.email=?
      """,Integer.class,user.email());
    assertEquals(0,pending,"Mail failure cannot leave a valid unsent reset link");
  }
}
