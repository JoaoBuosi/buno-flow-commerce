package dev.buno.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Opt-in server-side proof gate for a single sensitive order transition.
 * The signing key belongs ONLY on Verify and the Flow backend, never in an agent or browser.
 * The database additionally consumes the operation ID exactly once.
 */
@Component
public final class VerifiedCommandGuard {
  private final boolean required;
  private final String secret;

  public VerifiedCommandGuard(@Value("${commerce.verify.required:false}") boolean required,
                              @Value("${commerce.verify.signing-secret:}") String secret) {
    this.required=required;
    this.secret=secret==null?"":secret;
  }

  public record Proof(UUID operationId, long expectedVersion) {}

  public Proof authorize(UUID storeId, UUID orderId, String target, String operation,
                         String expiresText, String versionText, String signature) {
    if(!required || !"PROCESSING".equals(target)) return null;
    if(secret.length()<32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Verify gate not provisioned");
    try {
      if(operation==null || !operation.matches("op_[0-9a-f]{32}") ||
         signature==null || !signature.matches("[0-9a-f]{64}"))
        throw new IllegalArgumentException("proof format");
      String compact=operation.substring(3);
      UUID op=UUID.fromString(compact.substring(0,8)+"-"+compact.substring(8,12)+"-"+
                              compact.substring(12,16)+"-"+compact.substring(16,20)+"-"+
                              compact.substring(20));
      long expires=Long.parseLong(expiresText);
      long version=Long.parseLong(versionText);
      long now=Instant.now().getEpochSecond();
      if(version<0 || expires<now || expires>now+120) throw new IllegalArgumentException("stale proof");
      String message=message(storeId,orderId,operation,expires,version);
      String expected=sign(secret,message);
      if(!MessageDigest.isEqual(
             expected.getBytes(StandardCharsets.US_ASCII),
             signature.getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException("signature");
      return new Proof(op,version);
    } catch(RuntimeException exc) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify authorization required");
    }
  }

  public static String message(UUID storeId,UUID orderId,String operation,long expires,long version) {
    return "buno-verify-v1\nPATCH\n/api/v1/orders/"+orderId+"/status\n"+storeId+
      "\n"+operation+"\n"+expires+"\n"+version+"\nPAID\nPROCESSING";
  }

  public static String sign(String secret,String message) {
    try {
      Mac mac=Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch(Exception exc) {
      throw new IllegalStateException("Signing unavailable",exc);
    }
  }
}
