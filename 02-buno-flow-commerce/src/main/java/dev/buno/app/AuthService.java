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
  public AuthService(JdbcTemplate db) { this.db = db; }
  public record Session(UUID userId, UUID storeId, String email, String role) {}
  public record Token(String accessToken, UUID storeId, String role) {}

  @Transactional
  public Token register(String email, String password, String storeName) {
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
    return issue(userId,storeId,"OWNER");
  }
  public Token login(String email, String password) {
    if(email == null || password == null) throw unauth();
    var users=db.queryForList("SELECT id,password_hash FROM users WHERE email=?",email.trim().toLowerCase(java.util.Locale.ROOT));
    if(users.size()!=1 || !passwords.matches(password,(String)users.get(0).get("password_hash"))) throw unauth();
    UUID uid=(UUID) users.get(0).get("id");
    var memberships=db.queryForList("SELECT store_id,role FROM memberships WHERE user_id=? ORDER BY role",uid);
    if(memberships.isEmpty()) throw unauth();
    return issue(uid,(UUID)memberships.get(0).get("store_id"),(String)memberships.get(0).get("role"));
  }
  private Token issue(UUID uid,UUID storeId,String role) {
    byte[] raw=new byte[32];random.nextBytes(raw);
    String token=HexFormat.of().formatHex(raw);
    db.update("INSERT INTO user_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",
        hash(token),uid,Instant.now().plus(12,ChronoUnit.HOURS));
    return new Token(token,storeId,role);
  }
  public Session require(String auth,String storeHeader) {
    if(auth==null || !auth.startsWith("Bearer ") || storeHeader==null) throw unauth();
    UUID store;
    try { store=UUID.fromString(storeHeader); } catch(Exception e){throw unauth();}
    String token=auth.substring(7);
    var matches=db.queryForList(
      "SELECT u.id AS user_id,u.email,m.role FROM user_sessions s JOIN users u ON u.id=s.user_id JOIN memberships m ON m.user_id=u.id WHERE s.token_hash=? AND s.expires_at>now() AND m.store_id=?",
      hash(token),store);
    if(matches.size()!=1) throw unauth();
    Map<String,Object> m=matches.get(0);
    return new Session((UUID)m.get("user_id"),store,(String)m.get("email"),(String)m.get("role"));
  }
  public void logout(String auth) {
    if(auth!=null && auth.startsWith("Bearer ")) db.update("DELETE FROM user_sessions WHERE token_hash=?",hash(auth.substring(7)));
  }
  private static String hash(String token){
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalStateException(e);}
  }
  private static ResponseStatusException unauth(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Credenciais inválidas");}
}
