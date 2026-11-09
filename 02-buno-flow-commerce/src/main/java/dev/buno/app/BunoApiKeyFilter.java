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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public final class BunoApiKeyFilter extends OncePerRequestFilter {
    private final String tenantId;
    private final Map<String, String> keys;

    public BunoApiKeyFilter(
            @Value("${buno.tenant-id:}") String tenantId,
            @Value("${buno.store-api-key:}") String storeKey,
            @Value("${buno.agent-api-key:}") String agentKey,
            @Value("${buno.approver-api-key:}") String approverKey,
            @Value("${buno.core-api-key:}") String coreKey) {
        this.tenantId = tenantId;
        this.keys = new LinkedHashMap<>();
        keys.put("STORE", storeKey);
        keys.put("AGENT", agentKey);
        keys.put("APPROVER", approverKey);
        keys.put("CORE", coreKey);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (tenantId.isBlank()
                || keys.values().stream().anyMatch(v -> v.length() < 24
                        || v.startsWith("REPLACE_") || v.equalsIgnoreCase("changeme"))
                || keys.values().stream().distinct().count() != keys.size()) {
            writeError(response, 503, "Credenciais do backend nao configuradas");
            return;
        }

        String provided = request.getHeader("X-Buno-Key");
        String role = null;
        if (provided != null) {
            for (Map.Entry<String, String> entry : keys.entrySet()) {
                if (MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                        entry.getValue().getBytes(StandardCharsets.UTF_8))) {
                    role = entry.getKey();
                    break;
                }
            }
        }
        if (role == null) {
            writeError(response, 401, "Credencial ausente ou invalida");
            return;
        }
        request.setAttribute("bunoTenantId", tenantId);
        request.setAttribute("bunoRole", role);
        chain.doFilter(request, response);
    }

    static void requireRole(String actual, String... allowed) {
        for (String role : allowed) {
            if (role.equals(actual)) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acao nao permitida para esta credencial");
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
