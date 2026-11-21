package dev.buno.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fail-closed HTTP allowlist for Verify's independently revocable service tokens. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class VerifyServiceCredentialFilter extends OncePerRequestFilter {
  private final JdbcTemplate db;
  @Value("${commerce.verify.required:false}") private boolean verifyGateRequired;
  public VerifyServiceCredentialFilter(JdbcTemplate db){this.db=db;}

  @Override
  protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
      throws ServletException,IOException {
    if(!verifyGateRequired || !req.getRequestURI().startsWith("/api/v1/")) {
      chain.doFilter(req,res);return;
    }
    String header=req.getHeader("Authorization");
    if(header==null || !header.startsWith("Bearer ") ||
       !header.substring(7).matches("[0-9a-f]{64}")) {
      chain.doFilter(req,res);return;
    }
    String hash;
    try{
      hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(header.substring(7).getBytes(StandardCharsets.UTF_8)));
    }catch(java.security.NoSuchAlgorithmException e){throw new ServletException(e);}
    // Query includes revoked/expired rows, so invalidated service tokens never
    // fall through to a broader API surface even if their hash remains stored.
    Integer service=db.queryForObject("SELECT count(*) FROM verify_service_credentials WHERE token_hash=?",
                                      Integer.class,hash);
    if(service!=null && service>0){
      String method=req.getMethod(),path=req.getRequestURI();
      boolean allowed=("GET".equals(method) && path.equals("/api/v1/me")) ||
        ("GET".equals(method) && path.matches("/api/v1/orders/[a-fA-F0-9-]{36}(/history)?")) ||
        ("PATCH".equals(method) && path.matches("/api/v1/orders/[a-fA-F0-9-]{36}/status"));
      if(!allowed){
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().print("{\"message\":\"Verify service scope violation\"}");
        return;
      }
    }
    chain.doFilter(req,res);
  }
}
