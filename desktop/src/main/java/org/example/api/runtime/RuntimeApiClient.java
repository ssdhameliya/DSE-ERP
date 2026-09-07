package org.example.api.runtime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;
import org.example.shared.RuntimeContract;

import java.net.URI;
import java.net.ConnectException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
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
        this(ConfigManager.getDataApiBaseUrlUnbound());
    }

    public RuntimeApiClient(String baseUrl) {
        String b = baseUrl;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.base = b;
    }

    public RuntimeStatus status() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + RuntimeContract.HEALTH_PATH))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Server returned HTTP " + response.statusCode());
            }
            return json.readValue(response.body(), RuntimeStatus.class);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException(connectionFailureMessage(base, exception), exception);
        }
    }

    static String connectionFailureMessage(String base, Throwable failure) {
        Throwable root = failure;
        while (root != null && root.getCause() != null) root = root.getCause();
        String reason;
        if (root instanceof java.net.http.HttpTimeoutException) {
            reason = "Connection timed out.";
        } else if (root instanceof UnknownHostException) {
            reason = "DNS could not resolve the company server.";
        } else if (root instanceof SSLHandshakeException || root instanceof SSLException) {
            reason = "TLS/HTTPS certificate negotiation failed: " + safeMessage(root);
        } else if (root instanceof ConnectException) {
            reason = "The server refused the connection or the route is unavailable: " + safeMessage(root);
        } else {
            reason = safeMessage(root);
        }
        return "Cannot reach DSE ERP backend at " + base + ". " + reason;
    }

    private static String safeMessage(Throwable failure) {
        if (failure == null) return "Unknown connection failure.";
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public void requireReady() {
        RuntimeStatus status = status();
        if (!status.ready()) {
            throw new IllegalStateException(status.message() == null || status.message().isBlank()
                    ? "DSE ERP server is not ready" : status.message());
        }
    }

    public record RuntimeStatus(boolean ready, String service, String version, String apiRevision, String buildRevision,
                                String environment, String database, String databaseName, String message, String businessZone, String businessDate, String utcTime,
                                String dateFormat, String timePolicy, String databaseTimeZone) {}
}
