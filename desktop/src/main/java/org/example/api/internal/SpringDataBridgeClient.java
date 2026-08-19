package org.example.api.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

/**
 * Local HTTP client for the Spring-owned internal data bridge.
 * It never opens a PostgreSQL socket and never receives database credentials.
 */
public final class SpringDataBridgeClient {
    private final HttpClient http = org.example.api.ApiRuntime.HTTP;
    private final ObjectMapper json = org.example.api.ApiRuntime.JSON;
    private final String base;

    public SpringDataBridgeClient() {
        String value = ConfigManager.getDataApiBaseUrl();
        if (value == null || value.isBlank()) value = "http://127.0.0.1:8080";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        base = value;
    }

    public String beginTransaction() { return post("/api/internal/data-bridge/transaction", null, TransactionResponse.class).transactionId(); }
    public void commit(String id) { post("/api/internal/data-bridge/transaction/" + id + "/commit", null, TransactionResponse.class); }
    public void rollback(String id) { post("/api/internal/data-bridge/transaction/" + id + "/rollback", null, TransactionResponse.class); }
    public QueryResponse query(SqlRequest r) { return post("/api/internal/data-bridge/query", r, QueryResponse.class); }
    public UpdateResponse update(SqlRequest r) { return post("/api/internal/data-bridge/update", r, UpdateResponse.class); }
    public BatchResponse batch(BatchRequest r) { return post("/api/internal/data-bridge/batch", r, BatchResponse.class); }
    public boolean execute(SqlRequest r) { return post("/api/internal/data-bridge/execute", r, Boolean.class); }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-DSE-Internal-Token", ConfigManager.getRuntimeInternalBridgeToken());
            if (body == null) builder.POST(HttpRequest.BodyPublishers.noBody());
            else builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Spring data bridge error HTTP " + response.statusCode() + ": " + response.body());
            }
            return json.readValue(response.body(), type);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Spring data bridge request interrupted", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Cannot reach Spring data bridge at " + base, ex);
        }
    }

    public record SqlParameter(String type, String value) {}
    public record SqlRequest(String transactionId, String sql, List<SqlParameter> parameters, boolean returnGeneratedKeys) {}
    public record BatchRequest(String transactionId, String sql, List<List<SqlParameter>> batches, boolean returnGeneratedKeys) {}
    public record QueryResponse(List<String> columns, List<List<Object>> rows) {}
    public record UpdateResponse(int updateCount, List<String> generatedKeys) {}
    public record BatchResponse(List<Integer> updateCounts) {}
    public record TransactionResponse(String transactionId, boolean success, String message) {}
}
