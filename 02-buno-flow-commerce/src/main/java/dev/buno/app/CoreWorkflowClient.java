package dev.buno.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class CoreWorkflowClient {
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String readKey;

    public CoreWorkflowClient(ObjectMapper mapper,
                              @Value("${buno.core-url:}") String baseUrl,
                              @Value("${buno.core-read-key:}") String readKey) {
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.baseUrl = baseUrl;
        this.readKey = readKey;
    }

    public JsonNode findByOrder(Long orderId) {
        if (!(baseUrl.startsWith("https://") || baseUrl.startsWith("http://"))
                || readKey.length() < 24 || readKey.startsWith("REPLACE_")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Buno Core nao configurado");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                baseUrl.replaceAll("/+$", "") + "/v1/workflows?source=flow-commerce&resource_id=" + orderId))
                .GET().timeout(Duration.ofSeconds(5))
                .header("X-Buno-Key", readKey).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Buno Core retornou erro");
            }
            return mapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha de comunicacao com Buno Core");
        }
    }
}
