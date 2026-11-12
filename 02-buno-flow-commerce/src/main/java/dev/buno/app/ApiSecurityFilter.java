package dev.buno.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defense-in-depth for MVP single-instance deployments.
 * Production behind multiple app instances needs a centralized rate limiter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ApiSecurityFilter extends OncePerRequestFilter {
  private static final long WINDOW_MS=10*60*1000L;
  private static final int MAX_ATTEMPTS=20;
  @Value("${commerce.edge-required:false}") private boolean edgeRequired;
  @Value("${commerce.edge-secret:}") private String edgeSecret;
  @Value("${commerce.security.ip-rate-limit-enabled:false}") private boolean distributedLimiterEnabled;
  private final ConcurrentHashMap<String,Counter> loginAttempts=new ConcurrentHashMap<>();
  private static final class Counter { long since; int used; Counter(long now){since=now;used=0;} }

  @Override
  protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
      throws ServletException,IOException {
    res.setHeader("X-Content-Type-Options","nosniff");
    res.setHeader("Referrer-Policy","no-referrer");
    res.setHeader("X-Frame-Options","DENY");
    res.setHeader("Cache-Control","no-store");
    String path=req.getRequestURI();
    String method=req.getMethod();
    // Network-facing Railway endpoint must be called only by Vercel's
    // server-side gateway, never with a secret embedded in a browser bundle.
    // This guard is intentionally independent of user authentication.
    if (edgeRequired && path.startsWith("/api/v1/")) {
      if (edgeSecret.isBlank()) {
        res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        res.setContentType("application/json");
        res.getWriter().print("{\"message\":\"Staging gateway not configured\"}");
        return;
      }
      String supplied=req.getHeader("X-Buno-Edge-Key");
      byte[] actual=(supplied==null?"":supplied).getBytes(StandardCharsets.UTF_8);
      if (!MessageDigest.isEqual(edgeSecret.getBytes(StandardCharsets.UTF_8),actual)) {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().print("{\"message\":\"Staging gateway required\"}");
        return;
      }
    }

    if (!distributedLimiterEnabled && "POST".equals(method) && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/reset/request"))) {
      // Use socket address only; untrusted X-Forwarded-For cannot bypass the limiter.
      String key=req.getRemoteAddr();
      long now=System.currentTimeMillis();
      Counter c=loginAttempts.computeIfAbsent(key,k->new Counter(now));
      boolean exceeded;
      synchronized(c){
        if(now-c.since>WINDOW_MS){c.since=now;c.used=0;}
        exceeded=++c.used>MAX_ATTEMPTS;
      }
      if(exceeded){res.setStatus(429);res.setContentType("application/json");res.getWriter().print("{\"message\":\"Muitas tentativas; tente novamente mais tarde\"}");return;}
      if(loginAttempts.size()>10_000) loginAttempts.entrySet().removeIf(e->now-e.getValue().since>WINDOW_MS);
    }
    // A malformed or absent auth header must be rejected before Spring dispatches a handler.
    if(path.startsWith("/api/v1/") && !path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register") && !path.equals("/api/v1/auth/reset/request") && !path.equals("/api/v1/auth/reset/complete") &&
       !"OPTIONS".equals(method)){
      String header=req.getHeader("Authorization");
      String store=req.getHeader("X-Store-Id");
      if(header==null||!header.startsWith("Bearer ")||store==null||store.isBlank()){
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);res.setContentType("application/json");
        res.getWriter().print("{\"message\":\"Autenticação necessária\"}");return;
      }
    }
    chain.doFilter(req,res);
  }
}
