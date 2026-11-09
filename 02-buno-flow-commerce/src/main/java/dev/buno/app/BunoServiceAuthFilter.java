package dev.buno.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class BunoServiceAuthFilter extends OncePerRequestFilter {
    private final String tenant;
    private final String storeKey;
    private final String coreKey;

    public BunoServiceAuthFilter(@Value("${buno.tenant-id:}") String tenant,
                                 @Value("${buno.store-api-key:}") String storeKey,
                                 @Value("${buno.core-api-key:}") String coreKey) {
        this.tenant = tenant;
        this.storeKey = storeKey;
        this.coreKey = coreKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (tenant.isBlank() || storeKey.length() < 24 || coreKey.length() < 24
                || storeKey.startsWith("REPLACE_") || coreKey.startsWith("REPLACE_")
                || storeKey.equals(coreKey)) {
            fail(response, 503, "Credenciais do servidor nao configuradas");
            return;
        }
        String key = request.getHeader("X-Buno-Key");
        String role = key != null && MessageDigest.isEqual(
            storeKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))
            ? "STORE" : key != null && MessageDigest.isEqual(
            coreKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))
            ? "CORE" : null;
        if (role == null) {
            fail(response, 401, "Credencial de servico invalida");
            return;
        }
        request.setAttribute("bunoTenantId", tenant);
        request.setAttribute("bunoRole", role);
        chain.doFilter(request, response);
    }

    public static void requireRole(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operacao nao autorizada");
        }
    }

    private static void fail(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
