package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OPT-IN REAL RESEND QA. Excluded from normal Surefire test discovery by *IT suffix.
 * Run only with an EPHEMERAL, EMPTY LOCAL PostgreSQL and a controlled mailbox.
 * Tokens stay in process memory. They are NEVER printed or committed.
 */
@SpringBootTest(properties={
  "commerce.mail.enabled=true",
  "commerce.mail.provider=resend",
  "commerce.mail.from=onboarding@resend.dev",
  "commerce.mail.frontend-url=https://flow-commerce-qa.invalid",
  "commerce.pilot-only=false",
  "commerce.pilot-invite-code="
})
@AutoConfigureMockMvc
class LiveResendRecoveryQaIT {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoSpyBean ResendEmailSender resend;
  private static final Pattern TOKEN=Pattern.compile("reset_token=([a-f0-9]{64})");

  private void verifyPreconditions() {
    assertEquals("yes", System.getenv("FLOW_QA_REAL_EMAIL_APPROVED"),
      "Opt-in flag required");
    assertEquals("127.0.0.1", System.getenv("FLOW_QA_DATABASE_HOST"),
      "Only isolated loopback PostgreSQL is permitted");
    assertNotNull(System.getenv("RESEND_API_KEY"), "Provider key missing");
    assertTrue(resend.configured(), "Provider not configured");
    assertEquals(0, db.queryForObject("SELECT count(*) FROM users",Integer.class),
      "QA DB must be empty");
    assertEquals(0, db.queryForObject("SELECT count(*) FROM password_resets",Integer.class),
      "QA DB must have no reset data");
  }

  private JsonNode api(String path,Map<String,String> data,int expected) throws Exception {
    var response=mvc.perform(post("/api/v1"+path)
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(data)))
      .andExpect(status().is(expected)).andReturn();
    String body=response.getResponse().getContentAsString();
    return body.isBlank()?json.createObjectNode():json.readTree(body);
  }

  private String extract(SimpleMailMessage message) {
    assertEquals("onboarding@resend.dev",message.getFrom());
    String email=System.getenv("FLOW_QA_APPROVED_EMAIL");
    assertNotNull(email);
    assertArrayEquals(new String[]{email},message.getTo());
    var token=TOKEN.matcher(message.getText());
    assertTrue(token.find(),"A one-time recovery URL must be present");
    assertTrue(message.getText().contains(
      "https://buno-flow-commerce-staging-web.vercel.app/?reset_token="));
    return token.group(1);
  }

  private String hash(String input) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
      .digest(input.getBytes(StandardCharsets.UTF_8)));
  }

  @Test void fullRealEmailAndOneTimeRecoveryAgainstIsolatedPostgres() throws Exception {
    verifyPreconditions();
    String email=System.getenv("FLOW_QA_APPROVED_EMAIL");
    assertEquals("joaovbuosi@gmail.com", email,
      "Only approved Resend account owner mailbox can receive QA email");
    String initialPassword="QaInitial-"+UUID.randomUUID()+"-2026";
    String nextPassword="QaUpdated-"+UUID.randomUUID()+"-2026";
    var registration=api("/auth/register",Map.of(
      "email",email,"password",initialPassword,"storeName","Flow Commerce Ephemeral QA"),200);
    String originalSession=registration.get("accessToken").asText();
    String store=registration.get("storeId").asText();
    assertFalse(originalSession.isBlank());

    api("/auth/reset/request",Map.of("email",email),202);
    var messages=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(resend,times(1)).send(messages.capture());
    String expiredToken=extract(messages.getValue());
    var expiry=db.queryForObject(
      "SELECT expires_at FROM password_resets WHERE token_hash=?",
      OffsetDateTime.class,hash(expiredToken));
    assertNotNull(expiry);
    var seconds=java.time.Duration.between(OffsetDateTime.now(java.time.ZoneOffset.UTC), expiry).toSeconds();
    assertTrue(seconds>17*60 && seconds<=20*60,"Recovery token TTL must be around 20 minutes");

    db.update("UPDATE password_resets SET expires_at=now()-interval '1 second' WHERE token_hash=?",
      hash(expiredToken));
    api("/auth/reset/complete",Map.of("token",expiredToken,
      "newPassword","NotAccepted-Ephemeral-2026"),400);
    var oldSessionBeforeReset=mvc.perform(get("/api/v1/me")
      .header("Authorization","Bearer "+originalSession)
      .header("X-Store-Id",store)).andReturn();
    assertEquals(200,oldSessionBeforeReset.getResponse().getStatus(),
      "Invalid token must not revoke active sessions");

    Thread.sleep(1200); // avoid provider rate-limit during consecutive real sends
    api("/auth/reset/request",Map.of("email",email),202);
    messages=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(resend,times(2)).send(messages.capture());
    String goodToken=extract(messages.getAllValues().get(1));
    assertFalse(expiredToken.equals(goodToken));
    assertEquals(1,db.queryForObject("SELECT count(*) FROM password_resets",Integer.class));
    assertEquals(0,db.queryForObject(
      "SELECT count(*) FROM password_resets WHERE token_hash=?",Integer.class,hash(expiredToken)),
      "Re-request must invalidate old token");

    api("/auth/reset/complete",Map.of("token",goodToken,"newPassword",nextPassword),204);
    api("/auth/reset/complete",Map.of("token",goodToken,"newPassword",nextPassword),400);
    var oldSessionAfterReset=mvc.perform(get("/api/v1/me")
      .header("Authorization","Bearer "+originalSession)
      .header("X-Store-Id",store)).andReturn();
    assertEquals(401,oldSessionAfterReset.getResponse().getStatus());
    api("/auth/login",Map.of("email",email,"password",initialPassword),401);
    JsonNode newLogin=api("/auth/login",Map.of("email",email,"password",nextPassword),200);
    assertTrue(newLogin.hasNonNull("accessToken"));
    assertEquals(1,db.queryForObject(
      "SELECT count(*) FROM password_resets WHERE used_at IS NOT NULL",Integer.class));

    System.out.println("LIVE_RESEND_QA_PASS: two actual provider sends accepted, 20min TTL, expired, rotated, one-time, old session revoked, new login verified; isolated database only");
  }
}
