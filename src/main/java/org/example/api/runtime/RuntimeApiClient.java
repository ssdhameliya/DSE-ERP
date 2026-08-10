package org.example.api.runtime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Phase-5 runtime boundary. Verifies the Spring server before JavaFX starts API-backed screens. */
public final class RuntimeApiClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String base;

    public RuntimeApiClient() {
        String b = ConfigManager.getDataApiBaseUrl();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.base = b;
    }

    public RuntimeStatus status() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/runtime/health"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Server returned HTTP " + response.statusCode());
            }
            return json.readValue(response.body(), RuntimeStatus.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot reach DSE ERP server at " + base + ". Start the Spring Boot server first.", exception);
        }
    }

    public void requireReady() {
        RuntimeStatus status = status();
        if (!status.ready()) {
            throw new IllegalStateException(status.message() == null || status.message().isBlank()
                    ? "DSE ERP server is not ready" : status.message());
        }
    }

    public record RuntimeStatus(boolean ready, String service, String version, String apiRevision, String database, String message) {}
}
