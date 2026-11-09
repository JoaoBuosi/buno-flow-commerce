package dev.buno.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** One-time password reset. No reset tokens or personal data are logged. */
@Service
public class PasswordResetService {
  private final JdbcTemplate db;
  private final JavaMailSender mail;
  private final boolean enabled;
  private final String sender;
  private final String frontendUrl;
  private final SecureRandom random=new SecureRandom();
  private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);

  public PasswordResetService(JdbcTemplate db,ObjectProvider<JavaMailSender> mailSender,
      @Value("${commerce.mail.enabled:false}") boolean enabled,
      @Value("${commerce.mail.from:}") String sender,
      @Value("${commerce.mail.frontend-url:http://localhost:5173}") String frontendUrl){
    this.db=db;this.mail=mailSender.getIfAvailable();this.enabled=enabled;
    this.sender=sender;this.frontendUrl=frontendUrl;
  }
  public record ResetRequest(String email){}
  public record ResetComplete(String token,String newPassword){}
  @Transactional
  public void request(String email){
    if(!enabled||mail==null||sender.isBlank()||!frontendUrl.startsWith("https://"))
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Envio de e-mail ainda não configurado");
    if(email==null||email.length()>254||!email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"E-mail inválido");
    String normalized=email.trim().toLowerCase(java.util.Locale.ROOT);
    var results=db.queryForList("SELECT id FROM users WHERE email=?",normalized);
    // Do not disclose whether an account exists.
    if(results.isEmpty())return;
    UUID user=(UUID)results.get(0).get("id");
    db.update("DELETE FROM password_resets WHERE user_id=?",user);
    byte[] randomBytes=new byte[32];random.nextBytes(randomBytes);
    String token=HexFormat.of().formatHex(randomBytes);
    db.update("INSERT INTO password_resets(token_hash,user_id,expires_at) VALUES(?,?,?)",
      sha(token),user,OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(20));
    var message=new SimpleMailMessage();
    message.setFrom(sender);
    message.setTo(normalized);
    message.setSubject("Buno Flow Commerce — recuperar acesso");
    message.setText("Para definir uma nova senha, abra este link (válido por 20 minutos):\n\n"+
      frontendUrl+"/?reset_token="+token+"\n\nSe você não solicitou, ignore esta mensagem.");
    mail.send(message);
  }
  @Transactional
  public void complete(String token,String newPassword){
    if(token==null||!token.matches("^[0-9a-f]{64}$")||
       newPassword==null||newPassword.length()<12||newPassword.length()>72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Token ou senha inválidos");
    var found=db.queryForList("""
        SELECT user_id FROM password_resets
         WHERE token_hash=? AND used_at IS NULL AND expires_at>now()
         FOR UPDATE
      """,sha(token));
    if(found.size()!=1)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solicitação inválida ou expirada");
    UUID user=(UUID)found.get(0).get("user_id");
    db.update("UPDATE users SET password_hash=? WHERE id=?",passwords.encode(newPassword),user);
    db.update("UPDATE password_resets SET used_at=now() WHERE token_hash=?",sha(token));
    db.update("DELETE FROM user_sessions WHERE user_id=?",user);
  }
  private static String sha(String input){
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalStateException("Hash failure",e);}
  }
}
