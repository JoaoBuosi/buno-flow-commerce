package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
  "commerce.mfa.owner-required=false",
  "commerce.mfa.pilot-email=",
  "commerce.edge-required=false",
  "commerce.security.ip-rate-limit-enabled=false",
  "commerce.mail.enabled=false"
})
@AutoConfigureMockMvc
class OwnerMfaSelfServiceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @DynamicPropertySource
  static void ephemeralTestKey(DynamicPropertyRegistry registry) {
    registry.add("commerce.mfa.encryption-key",
      ()->Base64.getEncoder().encodeToString(new byte[32]));
  }
  private static final String PASSWORD="synthetic-valid-password-for-qa";
  private int requestCount=0;

  private JsonNode request(String endpoint, Map<String,?> body, int status,
                           String bearer, String store) throws Exception {
    var builder=post("/api/v1"+endpoint)
      .with(req->{req.setRemoteAddr("192.0.2."+((++requestCount%230)+10));return req;})
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(body));
    if(bearer!=null)builder.header("Authorization","Bearer "+bearer);
    if(store!=null)builder.header("X-Store-Id",store);
    var response=mvc.perform(builder).andExpect(status().is(status)).andReturn();
    String responseBody=response.getResponse().getContentAsString();
    return responseBody.isEmpty()?json.createObjectNode():json.readTree(responseBody);
  }
  private JsonNode register(String email) throws Exception {
    return request("/auth/register",Map.of("email",email,"password",PASSWORD,
      "storeName","Loja Voluntária QA"),200,null,null);
  }
  private void expectMe(String bearer,String store,int status) throws Exception {
    mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+bearer)
      .header("X-Store-Id",store)).andExpect(status().is(status));
  }
  private static byte[] decode(String base32) {
    byte[] decoded=new byte[20];
    int bits=0,buffer=0,idx=0;
    for(char c:base32.toCharArray()) {
      int i="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
      if(i<0)throw new IllegalArgumentException("invalid alphabet");
      buffer=(buffer<<5)|i;bits+=5;
      if(bits>=8) {bits-=8;decoded[idx++]=(byte)((buffer>>>bits)&255);}
    }
    assertEquals(20,idx);
    return decoded;
  }

  @Test void ownerCanActivateBeforeGlobalPolicyWithoutAffectingOtherOwners() throws Exception {
    String ownerEmail="optin-"+UUID.randomUUID()+"@example.invalid";
    String otherEmail="other-"+UUID.randomUUID()+"@example.invalid";
    JsonNode owner=register(ownerEmail), other=register(otherEmail);
    assertTrue(owner.hasNonNull("accessToken"));
    assertTrue(other.hasNonNull("accessToken"));
    String oldToken=owner.get("accessToken").asText();
    String store=owner.get("storeId").asText();
    expectMe(oldToken,store,200);

    var before=mvc.perform(get("/api/v1/auth/mfa/status")
      .header("Authorization","Bearer "+oldToken).header("X-Store-Id",store))
      .andExpect(status().isOk()).andReturn();
    JsonNode status=json.readTree(before.getResponse().getContentAsString());
    assertFalse(status.get("enrolled").asBoolean());
    assertFalse(status.get("required").asBoolean());

    request("/auth/mfa/self/enroll",Map.of("currentPassword","incorrect-value"),
      401,oldToken,store);
    expectMe(oldToken,store,200);

    JsonNode challenge=request("/auth/mfa/self/enroll",
      Map.of("currentPassword",PASSWORD),200,oldToken,store);
    assertTrue(challenge.get("mfaRequired").asBoolean());
    assertFalse(challenge.has("accessToken"));
    expectMe(oldToken,store,200); // No lockout until enrollment is confirmed.
    String pending=challenge.get("challengeToken").asText();

    JsonNode setup=request("/auth/mfa/enroll/start",Map.of("challengeToken",pending),
      200,null,null);
    String otp=OwnerMfaService.totp(decode(setup.get("secret").asText()),
      Instant.now().getEpochSecond()/30);
    JsonNode verified=request("/auth/mfa/enroll/complete",
      Map.of("challengeToken",pending,"code",otp),200,null,null);
    assertEquals(8,verified.get("recoveryCodes").size());
    String secondToken=verified.get("accessToken").asText();
    expectMe(oldToken,store,401);
    expectMe(secondToken,store,200);
    request("/auth/mfa/self/enroll",Map.of("currentPassword",PASSWORD),409,secondToken,store);

    var after=mvc.perform(get("/api/v1/auth/mfa/status")
      .header("Authorization","Bearer "+secondToken).header("X-Store-Id",store))
      .andExpect(status().isOk()).andReturn();
    status=json.readTree(after.getResponse().getContentAsString());
    assertTrue(status.get("enrolled").asBoolean());
    assertTrue(status.get("required").asBoolean());

    JsonNode nextLogin=request("/auth/login",Map.of("email",ownerEmail,"password",PASSWORD),200,null,null);
    assertTrue(nextLogin.get("mfaRequired").asBoolean());
    assertFalse(nextLogin.has("accessToken"));
    // The one-time enrollment OTP cannot authenticate the next login.
    request("/auth/mfa/login/verify",
      Map.of("challengeToken",nextLogin.get("challengeToken").asText(),"code",otp),
      401,null,null);
    String recovery=verified.get("recoveryCodes").get(0).asText();
    JsonNode recovered=request("/auth/mfa/login/verify",Map.of(
      "challengeToken",nextLogin.get("challengeToken").asText(),"recoveryCode",recovery),
      200,null,null);
    expectMe(recovered.get("accessToken").asText(),store,200);

    JsonNode anotherLogin=request("/auth/login",Map.of("email",ownerEmail,"password",PASSWORD),200,null,null);
    request("/auth/mfa/login/verify",Map.of("challengeToken",anotherLogin.get("challengeToken").asText(),
      "recoveryCode",recovery),401,null,null);

    // Different OWNER remains unaffected while global enforcement is disabled.
    expectMe(other.get("accessToken").asText(),other.get("storeId").asText(),200);
    JsonNode otherLogin=request("/auth/login",Map.of("email",otherEmail,"password",PASSWORD),200,null,null);
    assertTrue(otherLogin.hasNonNull("accessToken"));
    assertFalse(otherLogin.has("mfaRequired"));
  }
}
