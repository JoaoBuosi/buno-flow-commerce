package dev.buno.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Distributed IP limits for public authentication routes.
 * The Vercel proxy overwrites X-Buno-Client-IP from its provider-controlled
 * x-vercel-forwarded-for, then authenticates to Railway with X-Buno-Edge-Key.
 * All other Forwarded / X-Forwarded-For headers are deliberately ignored.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthIpRateLimitFilter extends OncePerRequestFilter {
  private static final int WINDOW_SECONDS=300;
  private static final Map<String,Integer> ROUTES=Map.of(
    "/api/v1/auth/login",30,
    "/api/v1/auth/register",12,
    "/api/v1/auth/reset/request",10,
    "/api/v1/auth/reset/complete",15,
    "/api/v1/auth/mfa/enroll/start",10,
    "/api/v1/auth/mfa/enroll/complete",10,
    "/api/v1/auth/mfa/login/verify",12,
    "/api/v1/auth/mfa/self/enroll",8
  );
  private final JdbcTemplate db;
  private final boolean enabled;
  private final boolean edgeRequired;
  private final String edgeSecret;
  private final String macSecret;

  public AuthIpRateLimitFilter(JdbcTemplate db,
      @Value("${commerce.security.ip-rate-limit-enabled:false}") boolean enabled,
      @Value("${commerce.edge-required:false}") boolean edgeRequired,
      @Value("${commerce.edge-secret:}") String edgeSecret,
      @Value("${commerce.security.ip-hash-key:}") String hashKey) {
    this.db=db;
    this.enabled=enabled;
    this.edgeRequired=edgeRequired;
    this.edgeSecret=edgeSecret;
    // Explicitly require high-entropy secret in staging: do not derive unsalted IP hashes.
    this.macSecret=!hashKey.isBlank()?hashKey:edgeSecret;
    if(enabled && (this.macSecret.length()<32 || (edgeRequired && edgeSecret.length()<32))) {
      throw new IllegalStateException("IP rate limit requires a server-only secret of at least 32 characters");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !enabled || !"POST".equals(request.getMethod()) ||
      !ROUTES.containsKey(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
      throws ServletException,IOException {
    final String edgeKey=req.getHeader("X-Buno-Edge-Key");
    boolean verifiedEdge=!edgeRequired || (edgeKey!=null &&
        MessageDigest.isEqual(edgeSecret.getBytes(StandardCharsets.UTF_8),
                              edgeKey.getBytes(StandardCharsets.UTF_8)));
    if(!verifiedEdge) {
      res.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    // Only the verified gateway may supply a client IP. Otherwise use the
    // direct TCP peer address; never trust arbitrary forwarded headers.
    String client= req.getRemoteAddr();
    if(edgeRequired) {
      String forwarded=req.getHeader("X-Buno-Client-IP");
      if(forwarded!=null && forwarded.length()<=45 &&
          forwarded.matches("[0-9A-Fa-f:.]{3,45}") && !forwarded.contains(",")) {
        client=forwarded;
      }
    }
    String family=req.getRequestURI().substring("/api/v1/auth/".length());
    String digest=hmac(client);
    long attempts;
    try {
      attempts=db.queryForObject("""
        INSERT INTO flow_commerce.auth_ip_rate_limits(ip_hash,route_family,window_started_at,requests)
        VALUES(?,?,now(),1)
        ON CONFLICT (ip_hash,route_family) DO UPDATE SET
          requests=CASE WHEN auth_ip_rate_limits.window_started_at < now()-make_interval(secs => ?)
                 THEN 1 ELSE auth_ip_rate_limits.requests+1 END,
          window_started_at=CASE WHEN auth_ip_rate_limits.window_started_at < now()-make_interval(secs => ?)
                 THEN now() ELSE auth_ip_rate_limits.window_started_at END
        RETURNING requests
        """,Long.class,digest,family,WINDOW_SECONDS,WINDOW_SECONDS);
    } catch(DataAccessException e) {
      // Fail closed when limits cannot be enforced, but never log IPs or secrets.
      res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }
    if(attempts>ROUTES.get(req.getRequestURI())) {
      res.setStatus(429);
      res.setHeader("Retry-After",String.valueOf(WINDOW_SECONDS));
      res.setHeader("Cache-Control","no-store");
      res.setContentType(MediaType.APPLICATION_JSON_VALUE);
      res.getWriter().write("{\"message\":\"Muitas tentativas. Tente novamente em alguns minutos.\"}");
      return;
    }
    chain.doFilter(req,res);
  }

  private String hmac(String client) {
    try {
      Mac m=Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(macSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
      return HexFormat.of().formatHex(m.doFinal(client.getBytes(StandardCharsets.UTF_8)));
    } catch(Exception e) {throw new IllegalStateException("IP hashing unavailable",e);}
  }

  @Scheduled(fixedDelay=21_600_000, initialDelay=300_000)
  public void cleanExpiredBuckets() {
    if(enabled) db.update("DELETE FROM flow_commerce.auth_ip_rate_limits WHERE window_started_at<now()-interval '48 hours'");
  }
}

@Configuration
@EnableScheduling
class AuthIpRateLimitScheduling {}
