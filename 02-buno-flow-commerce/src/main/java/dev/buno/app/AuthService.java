package dev.buno.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
  private final SecureRandom random = new SecureRandom();
  private final OwnerMfaService mfa;
  public AuthService(JdbcTemplate db, OwnerMfaService mfa) { this.db = db; this.mfa=mfa; }
  public record Session(UUID userId, UUID storeId, String email, String role) {}
  public record Token(String accessToken, UUID storeId, String role) {}
  public record EnrollmentComplete(String accessToken, UUID storeId, String role,
                                   java.util.List<String> recoveryCodes) {}

  @Transactional
  public Object register(String email, String password, String storeName) {
    if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ||
        password == null || password.length() < 12 || password.length() > 72 ||
        storeName == null || storeName.isBlank() || storeName.length() > 120) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados inválidos; senha deve ter 12 a 72 caracteres");
    }
    email = email.trim().toLowerCase(java.util.Locale.ROOT);
    if (db.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, email) > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
    UUID userId = UUID.randomUUID(), storeId = UUID.randomUUID();
    db.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)", userId,email,passwords.encode(password));
    db.update("INSERT INTO stores(id,name) VALUES(?,?)", storeId,storeName.trim());
    db.update("INSERT INTO memberships(store_id,user_id,role) VALUES(?,?,'OWNER')",storeId,userId);
    return mfa.required() ? mfa.challenge(userId,storeId) : issue(userId,storeId,"OWNER",false);
  }
  @Transactional
  public Object login(String email, String password) {
    if(email == null || password == null) throw unauth();
    var users=db.queryForList("SELECT id,password_hash FROM users WHERE email=?",email.trim().toLowerCase(java.util.Locale.ROOT));
    if(users.size()!=1 || !passwords.matches(password,(String)users.get(0).get("password_hash"))) throw unauth();
    UUID uid=(UUID) users.get(0).get("id");
    var memberships=db.queryForList("SELECT store_id,role FROM memberships WHERE user_id=? ORDER BY role",uid);
    if(memberships.isEmpty()) throw unauth();
    UUID storeId=(UUID)memberships.get(0).get("store_id");
    String role=(String)memberships.get(0).get("role");
    if(mfa.required() && "OWNER".equals(role)) return mfa.challenge(uid,storeId);
    return issue(uid,storeId,role,false);
  }
  private Token issue(UUID uid,UUID storeId,String role,boolean verifiedMfa) {
    byte[] raw=new byte[32];random.nextBytes(raw);
    String token=HexFormat.of().formatHex(raw);
    db.update("INSERT INTO user_sessions(token_hash,user_id,expires_at,mfa_verified) VALUES(?,?,?,?)",
        hash(token),uid,java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusHours(12),verifiedMfa);
    return new Token(token,storeId,role);
  }
  public Session require(String auth,String storeHeader) {
    if(auth==null || !auth.startsWith("Bearer ") || storeHeader==null) throw unauth();
    UUID store;
    try { store=UUID.fromString(storeHeader); } catch(Exception e){throw unauth();}
    String token=auth.substring(7);
    // Reject malformed/oversized bearer values before hashing or querying the DB.
    if(!token.matches("[0-9a-f]{64}")) throw unauth();
    var matches=db.queryForList(
      "SELECT u.id AS user_id,u.email,m.role,s.mfa_verified FROM user_sessions s JOIN users u ON u.id=s.user_id JOIN memberships m ON m.user_id=u.id WHERE s.token_hash=? AND s.expires_at>now() AND m.store_id=?",
      hash(token),store);
    if(matches.size()!=1) throw unauth();
    Map<String,Object> m=matches.get(0);
    if(mfa.required() && "OWNER".equals(m.get("role")) &&
       !Boolean.TRUE.equals(m.get("mfa_verified")))throw unauth();
    return new Session((UUID)m.get("user_id"),store,(String)m.get("email"),(String)m.get("role"));
  }
  @Transactional
  public Token changePassword(Session caller,String oldPassword,String newPassword) {
    if(oldPassword==null || newPassword==null || newPassword.length()<12 || newPassword.length()>72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Senha inválida");
    var hashes=db.queryForList("SELECT password_hash FROM users WHERE id=? FOR UPDATE",caller.userId());
    if(hashes.size()!=1 || !passwords.matches(oldPassword,(String)hashes.get(0).get("password_hash")))throw unauth();
    if(oldPassword.equals(newPassword))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A nova senha deve ser diferente");
    db.update("UPDATE users SET password_hash=? WHERE id=?",passwords.encode(newPassword),caller.userId());
    db.update("DELETE FROM user_sessions WHERE user_id=?",caller.userId());
    db.update("DELETE FROM owner_mfa_challenges WHERE user_id=?",caller.userId());
    // Caller passed require() and therefore has a verified OWNER session if MFA is active.
    return issue(caller.userId(),caller.storeId(),caller.role(),
      mfa.required() && "OWNER".equals(caller.role()));
  }
  public OwnerMfaService.Enrollment startMfaEnrollment(String challengeToken) {
    return mfa.startEnrollment(challengeToken);
  }
  public EnrollmentComplete completeMfaEnrollment(String challengeToken,String code) {
    OwnerMfaService.Verification result=mfa.finishEnrollment(challengeToken,code);
    if(result==null)throw unauth();
    Token token=issue(result.userId(),result.storeId(),"OWNER",true);
    return new EnrollmentComplete(token.accessToken(),token.storeId(),token.role(),result.recoveryCodes());
  }
  public Token completeMfaLogin(String challengeToken,String code,String recoveryCode) {
    OwnerMfaService.Verification result=mfa.verify(challengeToken,code,recoveryCode);
    if(result==null)throw unauth();
    return issue(result.userId(),result.storeId(),"OWNER",true);
  }
  public void logout(String auth) {
    if(auth!=null && auth.startsWith("Bearer ") &&
       auth.length()==71 && auth.substring(7).matches("[0-9a-f]{64}"))
      db.update("DELETE FROM user_sessions WHERE token_hash=?",hash(auth.substring(7)));
  }
  private static String hash(String token){
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalStateException(e);}
  }
  private static ResponseStatusException unauth(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credenciais inválidas");}
}
