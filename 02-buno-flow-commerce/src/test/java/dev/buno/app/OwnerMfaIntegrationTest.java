package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated CI PostgreSQL; no real account, email, key material or runtime secrets. */
@SpringBootTest(properties={
  "commerce.mfa.owner-required=true",
  "commerce.mfa.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
  "commerce.edge-required=false",
  "commerce.security.ip-rate-limit-enabled=false",
  "commerce.mail.enabled=false"
})
@AutoConfigureMockMvc
class OwnerMfaIntegrationTest {
  private static final java.util.concurrent.atomic.AtomicInteger MOCK_SOCKET=new java.util.concurrent.atomic.AtomicInteger();
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  private static final String OLD_PASSWORD="strong-password-for-owner-qa";

  private JsonNode postApi(String path,Map<String,?> body,int status) throws Exception {
    var response=mvc.perform(post("/api/v1"+path)
      .with(req->{req.setRemoteAddr("192.0.2."+((MOCK_SOCKET.incrementAndGet()%240)+10));return req;})
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(body)))
      .andExpect(status().is(status)).andReturn();
    String data=response.getResponse().getContentAsString();
    return data.isBlank()?json.createObjectNode():json.readTree(data);
  }
  private JsonNode register() throws Exception {
    return postApi("/auth/register",Map.of(
      "email","owner-mfa-"+UUID.randomUUID()+"@example.test",
      "password",OLD_PASSWORD,"storeName","Loja QA 2FA"),200);
  }
  private static String digest(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8)));
  }
  private static byte[] decodeBase32(String secret) {
    int bits=0,buffer=0,offset=0;
    byte[] decoded=new byte[20];
    for(char c:secret.toCharArray()){
      int n="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
      if(n<0)throw new IllegalArgumentException("bad alphabet");
      buffer=(buffer<<5)|n;bits+=5;
      if(bits>=8){bits-=8;decoded[offset++]=(byte)((buffer>>>bits)&255);}
    }
    assertEquals(20,offset);
    return decoded;
  }
  private record Enrolled(String email,String storeId,String accessToken,String recoveryCode) {}
  private Enrolled enroll(String email,String initialChallenge,String store) throws Exception {
    JsonNode setup=postApi("/auth/mfa/enroll/start",
      Map.of("challengeToken",initialChallenge),200);
    String secret=setup.get("secret").asText();
    assertEquals(32,secret.length());
    assertTrue(setup.get("otpauthUri").asText().startsWith("otpauth://totp/"));
    UUID uid=db.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,email);
    String cipher=db.queryForObject("SELECT secret_ciphertext FROM owner_mfa WHERE user_id=?",
      String.class,uid);
    assertFalse(cipher.contains(secret),"TOTP must be encrypted, not saved as Base32");
    String otp=OwnerMfaService.totp(decodeBase32(secret),Instant.now().getEpochSecond()/30);
    JsonNode completed=postApi("/auth/mfa/enroll/complete",
      Map.of("challengeToken",initialChallenge,"code",otp),200);
    assertEquals("OWNER",completed.get("role").asText());
    assertEquals(store,completed.get("storeId").asText());
    assertTrue(completed.get("accessToken").asText().matches("[0-9a-f]{64}"));
    assertEquals(8,completed.get("recoveryCodes").size());
    assertEquals(8,db.queryForObject(
      "SELECT count(*) FROM owner_mfa_recovery_codes WHERE user_id=?",Integer.class,uid));
    return new Enrolled(email,store,completed.get("accessToken").asText(),
      completed.get("recoveryCodes").get(0).asText());
  }
  @Test void ownerHasNoPrivilegeBeforeTotpAndOldUnverifiedSessionsAreDenied() throws Exception {
    String email="owner-mfa-"+UUID.randomUUID()+"@example.test";
    JsonNode initial=postApi("/auth/register",Map.of(
      "email",email,"password",OLD_PASSWORD,"storeName","Loja Sem Sessão"),200);
    assertTrue(initial.get("mfaRequired").asBoolean());
    assertTrue(initial.get("enrollmentRequired").asBoolean());
    assertFalse(initial.has("accessToken"),"Registration must never issue session before MFA");
    String challenge=initial.get("challengeToken").asText();
    String store=initial.get("storeId").asText();
    mvc.perform(get("/api/v1/me")
      .header("Authorization","Bearer "+challenge)
      .header("X-Store-Id",store))
      .andExpect(status().isUnauthorized());
    UUID uid=db.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,email);
    String legacy="b".repeat(64);
    db.update("INSERT INTO user_sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,now()+interval '1 hour',false)",
      digest(legacy),uid);
    mvc.perform(get("/api/v1/dashboard")
      .header("Authorization","Bearer "+legacy)
      .header("X-Store-Id",store))
      .andExpect(status().isUnauthorized());

    Enrolled owner=enroll(email,challenge,store);
    mvc.perform(get("/api/v1/me")
      .header("Authorization","Bearer "+owner.accessToken())
      .header("X-Store-Id",store))
      .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me")
      .header("Authorization","Bearer "+legacy)
      .header("X-Store-Id",store))
      .andExpect(status().isUnauthorized());
    postApi("/auth/mfa/enroll/complete",Map.of("challengeToken",challenge,"code","123456"),401);
  }

  @Test void passwordRequiresMfaAndRecoveryCodeIsSingleUse() throws Exception {
    String email="owner-mfa-"+UUID.randomUUID()+"@example.test";
    JsonNode initial=postApi("/auth/register",Map.of(
      "email",email,"password",OLD_PASSWORD,"storeName","Loja Recuperação"),200);
    String store=initial.get("storeId").asText();
    Enrolled enrolled=enroll(email,initial.get("challengeToken").asText(),store);
    JsonNode login=postApi("/auth/login",Map.of("email",email,"password",OLD_PASSWORD),200);
    assertTrue(login.get("mfaRequired").asBoolean());
    assertFalse(login.get("enrollmentRequired").asBoolean());
    assertFalse(login.has("accessToken"));

    JsonNode success=postApi("/auth/mfa/login/verify",Map.of(
      "challengeToken",login.get("challengeToken").asText(),
      "recoveryCode",enrolled.recoveryCode()),200);
    assertTrue(success.get("accessToken").asText().matches("[0-9a-f]{64}"));
    postApi("/auth/mfa/login/verify",Map.of(
      "challengeToken",login.get("challengeToken").asText(),
      "recoveryCode",enrolled.recoveryCode()),401);
    JsonNode another=postApi("/auth/login",Map.of("email",email,"password",OLD_PASSWORD),200);
    postApi("/auth/mfa/login/verify",Map.of(
      "challengeToken",another.get("challengeToken").asText(),
      "recoveryCode",enrolled.recoveryCode()),401);

    mvc.perform(get("/api/v1/store")
      .header("Authorization","Bearer "+success.get("accessToken").asText())
      .header("X-Store-Id",store))
      .andExpect(status().isOk());
  }

  @Test void fiveWrongTotpCodesLockChallengeAndDoNotIssueToken() throws Exception {
    String email="owner-mfa-"+UUID.randomUUID()+"@example.test";
    JsonNode initial=postApi("/auth/register",Map.of(
      "email",email,"password",OLD_PASSWORD,"storeName","Loja Bloqueio"),200);
    String challenge=initial.get("challengeToken").asText();
    JsonNode enrollment=postApi("/auth/mfa/enroll/start",Map.of("challengeToken",challenge),200);
    String actual=OwnerMfaService.totp(decodeBase32(enrollment.get("secret").asText()),
      Instant.now().getEpochSecond()/30);
    String wrong=actual.equals("000000")?"999999":"000000";
    for(int i=0;i<5;i++) {
      postApi("/auth/mfa/enroll/complete",Map.of("challengeToken",challenge,"code",wrong),401);
    }
    postApi("/auth/mfa/enroll/complete",Map.of("challengeToken",challenge,"code",actual),401);
    UUID uid=db.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,email);
    assertEquals(0,db.queryForObject("SELECT count(*) FROM owner_mfa WHERE user_id=? AND enabled_at IS NOT NULL",
      Integer.class,uid));
    assertEquals(5,db.queryForObject("SELECT attempts FROM owner_mfa_challenges WHERE token_hash=?",
      Integer.class,digest(challenge)));
  }

  @Test void passwordResetRevokesOutstandingMfaChallengeButKeepsTotpEnrollment() throws Exception {
    String email="owner-mfa-"+UUID.randomUUID()+"@example.test";
    JsonNode initial=postApi("/auth/register",Map.of(
      "email",email,"password",OLD_PASSWORD,"storeName","Loja Reset QA"),200);
    String store=initial.get("storeId").asText();
    Enrolled enrolled=enroll(email,initial.get("challengeToken").asText(),store);
    JsonNode challenge=postApi("/auth/login",Map.of(
      "email",email,"password",OLD_PASSWORD),200);
    String resetToken="f".repeat(64);
    UUID uid=db.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,email);
    db.update("INSERT INTO password_resets(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '20 minutes')",
      digest(resetToken),uid);
    postApi("/auth/reset/complete",Map.of(
      "token",resetToken,"newPassword","new-secure-password-after-reset"),204);
    postApi("/auth/mfa/login/verify",Map.of(
      "challengeToken",challenge.get("challengeToken").asText(),
      "recoveryCode",enrolled.recoveryCode()),401);
    postApi("/auth/login",Map.of(
      "email",email,"password",OLD_PASSWORD),401);
    JsonNode next=postApi("/auth/login",Map.of(
      "email",email,"password","new-secure-password-after-reset"),200);
    assertTrue(next.get("mfaRequired").asBoolean());
    assertFalse(next.get("enrollmentRequired").asBoolean());
    assertFalse(next.has("accessToken"),"Password reset must not disable MFA");
    mvc.perform(get("/api/v1/me")
      .header("Authorization","Bearer "+enrolled.accessToken())
      .header("X-Store-Id",store))
      .andExpect(status().isUnauthorized());
  }

  @Test void staffWithoutOwnerMembershipStillReceivesRegularSession() throws Exception {
    UUID uid=UUID.randomUUID(),store=UUID.randomUUID();
    String email="staff-mfa-"+UUID.randomUUID()+"@example.test";
    db.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)",
      uid,email,new BCryptPasswordEncoder(12).encode(OLD_PASSWORD));
    db.update("INSERT INTO stores(id,name) VALUES(?,?)",store,"Loja Staff QA");
    db.update("INSERT INTO memberships(store_id,user_id,role) VALUES(?,?,'STAFF')",store,uid);
    JsonNode login=postApi("/auth/login",Map.of("email",email,"password",OLD_PASSWORD),200);
    assertEquals("STAFF",login.get("role").asText());
    assertTrue(login.get("accessToken").asText().matches("[0-9a-f]{64}"));
    mvc.perform(get("/api/v1/me")
      .header("Authorization","Bearer "+login.get("accessToken").asText())
      .header("X-Store-Id",store.toString()))
      .andExpect(status().isOk());
  }

  @Test void rfc6238Sha1VectorMatchesReferenceAtUnix59Seconds() {
    assertEquals("287082",OwnerMfaService.totp("12345678901234567890".getBytes(StandardCharsets.US_ASCII),1));
  }
}
