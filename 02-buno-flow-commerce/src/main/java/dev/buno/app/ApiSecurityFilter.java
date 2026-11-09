package dev.buno.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    if("POST".equals(method) && (path.equals("/api/v1/auth/login") || (path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/reset/request"))){
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
