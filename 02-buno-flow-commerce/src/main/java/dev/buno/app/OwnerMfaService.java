package dev.buno.app;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** RFC 6238 TOTP second factor for OWNER. Enrollment and challenges never grant API sessions. */
@Service
public class OwnerMfaService {
  public record Challenge(boolean mfaRequired, boolean enrollmentRequired, String challengeToken,
                          UUID storeId, String role) {}
  public record Enrollment(String secret, String otpauthUri) {}
  public record Verification(UUID userId, UUID storeId, List<String> recoveryCodes) {}
  private record Pending(UUID userId, UUID storeId, int attempts) {}

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private final JdbcTemplate db;
  private final SecureRandom random = new SecureRandom();
  private final boolean required;
  private final SecretKeySpec encryptionKey;

  public OwnerMfaService(JdbcTemplate db, Environment environment) {
    this.db = db;
    this.required = environment.getProperty("commerce.mfa.owner-required", Boolean.class, false);
    String configured = environment.getProperty("commerce.mfa.encryption-key", "");
    byte[] key;
    try {
      key = configured.isBlank() ? new byte[0] : Base64.getDecoder().decode(configured);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("Invalid OWNER MFA key encoding");
    }
    if (required && key.length != 32) {
      throw new IllegalStateException("OWNER MFA requires a private, 32-byte AES key (Base64)");
    }
    this.encryptionKey = key.length == 32 ? new SecretKeySpec(key, "AES") : null;
  }

  public boolean required() { return required; }

  @Transactional
  public Challenge challenge(UUID userId, UUID storeId) {
    if (!required) throw new IllegalStateException("OWNER MFA not enabled");
    boolean enrolled = Boolean.TRUE.equals(db.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM owner_mfa WHERE user_id=? AND enabled_at IS NOT NULL)",
      Boolean.class, userId));
    // New challenges revoke previous outstanding login/enrollment tokens.
    db.update("DELETE FROM owner_mfa_challenges WHERE user_id=?", userId);
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String token = HexFormat.of().formatHex(raw);
    db.update("""
      INSERT INTO owner_mfa_challenges(token_hash,user_id,store_id,purpose,expires_at)
      VALUES(?,?,?,?,?)
      """, sha(token),userId,storeId,enrolled ? "LOGIN" : "ENROLL",
      OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
    return new Challenge(true,!enrolled,token,storeId,"OWNER");
  }

  @Transactional
  public Enrollment startEnrollment(String token) {
    if(!required)throw unauthorized();
    Pending pending = find(token,"ENROLL");
    if (pending == null) throw unauthorized();
    var rows = db.queryForList(
      "SELECT secret_ciphertext,enabled_at FROM owner_mfa WHERE user_id=? FOR UPDATE",
      pending.userId());
    byte[] secret;
    if (rows.isEmpty()) {
      secret = new byte[20];
      random.nextBytes(secret);
      db.update("INSERT INTO owner_mfa(user_id,secret_ciphertext) VALUES(?,?)",
        pending.userId(), encrypt(pending.userId(),secret));
    } else {
      if (rows.get(0).get("enabled_at") != null) throw unauthorized();
      secret = decrypt(pending.userId(), (String) rows.get(0).get("secret_ciphertext"));
    }
    String email = db.queryForObject("SELECT email FROM users WHERE id=?",
      String.class,pending.userId());
    String encoded = base32(secret);
    String issuer = "Buno Flow Commerce";
    String url = "otpauth://totp/" + URLEncoder.encode(issuer + ":" + email,StandardCharsets.UTF_8)
      + "?secret=" + encoded
      + "&issuer=" + URLEncoder.encode(issuer,StandardCharsets.UTF_8)
      + "&algorithm=SHA1&digits=6&period=30";
    return new Enrollment(encoded,url);
  }

  /** Failure returns null to commit failed-attempt counters before the controller sends 401. */
  @Transactional
  public Verification finishEnrollment(String token, String code) {
    if(!required)return null;
    Pending pending = find(token,"ENROLL");
    if (pending == null) return null;
    db.update("UPDATE owner_mfa_challenges SET attempts=attempts+1 WHERE token_hash=?",sha(token));
    var rows = db.queryForList(
      "SELECT secret_ciphertext,enabled_at FROM owner_mfa WHERE user_id=? FOR UPDATE",
      pending.userId());
    if (rows.size()!=1 || rows.get(0).get("enabled_at")!=null) return null;
    byte[] secret = decrypt(pending.userId(),(String)rows.get(0).get("secret_ciphertext"));
    long step = matchingStep(secret,code,-1);
    if (step<0) return null;
    db.update("UPDATE owner_mfa SET enabled_at=now(),last_used_step=?,updated_at=now() WHERE user_id=?",
      step,pending.userId());
    List<String> codes = new ArrayList<>();
    for (int i=0;i<8;i++) {
      byte[] bytes=new byte[10];random.nextBytes(bytes);
      String codeValue=HexFormat.of().formatHex(bytes);
      db.update("INSERT INTO owner_mfa_recovery_codes(user_id,code_hash) VALUES(?,?)",
        pending.userId(),sha(codeValue));
      codes.add(codeValue);
    }
    // Any sessions issued before OWNER enforcement must not remain privileged.
    db.update("DELETE FROM user_sessions WHERE user_id=?",pending.userId());
    db.update("UPDATE owner_mfa_challenges SET consumed_at=now() WHERE token_hash=?",sha(token));
    return new Verification(pending.userId(),pending.storeId(),List.copyOf(codes));
  }

  /** Return null on incorrect MFA to persist attempt counts; never reveal which factor failed. */
  @Transactional
  public Verification verify(String token, String code, String recoveryCode) {
    if(!required)return null;
    Pending pending=find(token,"LOGIN");
    if (pending==null) return null;
    db.update("UPDATE owner_mfa_challenges SET attempts=attempts+1 WHERE token_hash=?",sha(token));
    var rows=db.queryForList(
      "SELECT secret_ciphertext,enabled_at,last_used_step FROM owner_mfa WHERE user_id=? FOR UPDATE",
      pending.userId());
    if(rows.size()!=1 || rows.get(0).get("enabled_at")==null)return null;
    boolean valid=false;
    if(recoveryCode!=null && recoveryCode.matches("[0-9a-f]{20}")) {
      valid=db.update("""
        UPDATE owner_mfa_recovery_codes SET used_at=now()
        WHERE user_id=? AND code_hash=? AND used_at IS NULL
        """,pending.userId(),sha(recoveryCode))==1;
    } else if(code!=null) {
      long last=((Number)rows.get(0).get("last_used_step")).longValue();
      long step=matchingStep(decrypt(pending.userId(),(String)rows.get(0).get("secret_ciphertext")),
        code,last);
      if(step>=0) {
        db.update("UPDATE owner_mfa SET last_used_step=?,updated_at=now() WHERE user_id=?",
          step,pending.userId());
        valid=true;
      }
    }
    if(!valid)return null;
    db.update("UPDATE owner_mfa_challenges SET consumed_at=now() WHERE token_hash=?",sha(token));
    return new Verification(pending.userId(),pending.storeId(),List.of());
  }

  private Pending find(String raw, String purpose) {
    if(raw==null || !raw.matches("[0-9a-f]{64}"))return null;
    var rows=db.queryForList("""
      SELECT user_id,store_id,attempts FROM owner_mfa_challenges
      WHERE token_hash=? AND purpose=? AND consumed_at IS NULL
        AND expires_at>now() AND attempts<5 FOR UPDATE
      """,sha(raw),purpose);
    if(rows.size()!=1)return null;
    Map<String,Object> m=rows.get(0);
    return new Pending((UUID)m.get("user_id"),(UUID)m.get("store_id"),((Number)m.get("attempts")).intValue());
  }

  static String totp(byte[] secret, long counter) {
    try {
      Mac mac=Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret,"HmacSHA1"));
      byte[] h=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
      int offset=h[h.length-1]&0x0f;
      int binary=((h[offset]&0x7f)<<24)|((h[offset+1]&0xff)<<16)
        |((h[offset+2]&0xff)<<8)|(h[offset+3]&0xff);
      return String.format(Locale.ROOT,"%06d",binary%1_000_000);
    } catch(Exception e) { throw new IllegalStateException("TOTP unavailable",e); }
  }

  static long matchingStep(byte[] secret, String submitted, long lastUsed) {
    if(submitted==null || !submitted.matches("[0-9]{6}"))return -1;
    long now=Instant.now().getEpochSecond()/30;
    // Minimal ±1 step tolerance; monotonic counter prevents re-use within skew window.
    for(long step=now-1;step<=now+1;step++) {
      if(step<=lastUsed)continue;
      if(MessageDigest.isEqual(totp(secret,step).getBytes(StandardCharsets.US_ASCII),
                               submitted.getBytes(StandardCharsets.US_ASCII)))return step;
    }
    return -1;
  }

  static String base32(byte[] bytes) {
    StringBuilder result=new StringBuilder();
    int buffer=0,bits=0;
    for(byte value:bytes) {
      buffer=(buffer<<8)|(value&0xff);
      bits+=8;
      while(bits>=5) {
        bits-=5;result.append(ALPHABET.charAt((buffer>>>bits)&31));
      }
    }
    if(bits>0)result.append(ALPHABET.charAt((buffer<<(5-bits))&31));
    return result.toString();
  }

  private String encrypt(UUID uid, byte[] raw) {
    try {
      byte[] nonce=new byte[12];random.nextBytes(nonce);
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE,encryptionKey,new GCMParameterSpec(128,nonce));
      cipher.updateAAD(uid.toString().getBytes(StandardCharsets.UTF_8));
      byte[] result=cipher.doFinal(raw);
      return encoded(nonce,result);
    } catch(Exception e) {throw new IllegalStateException("MFA encryption failed",e);}
  }
  private String encoded(byte[] nonce,byte[] data) {
    ByteBuffer buffer=ByteBuffer.allocate(nonce.length+data.length);
    buffer.put(nonce);buffer.put(data);
    return Base64.getEncoder().encodeToString(buffer.array());
  }
  private byte[] decrypt(UUID uid,String stored) {
    try {
      byte[] data=Base64.getDecoder().decode(stored);
      if(data.length<29)throw new IllegalArgumentException();
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,encryptionKey,new GCMParameterSpec(128,data,0,12));
      cipher.updateAAD(uid.toString().getBytes(StandardCharsets.UTF_8));
      return cipher.doFinal(data,12,data.length-12);
    } catch(Exception e) {throw new IllegalStateException("MFA decryption failed",e);}
  }
  private static String sha(String token) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
      .digest(token.getBytes(StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalStateException("Token hash unavailable",e);}
  }
  private static ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verificação em duas etapas inválida ou expirada");
  }
}
