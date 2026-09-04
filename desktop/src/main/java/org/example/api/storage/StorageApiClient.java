package org.example.api.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class StorageApiClient {
    private final ObjectMapper json = ApiRuntime.JSON;

    public Status status() { return get("/api/storage/status", Status.class); }
    public CleanupResult previewCleanup() { return post("/api/storage/cleanup?dryRun=true", CleanupResult.class); }
    public CleanupResult cleanNow() { return post("/api/storage/cleanup?dryRun=false", CleanupResult.class); }

    private <T> T get(String path, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET();
            ApiSession.authorize(builder);
            HttpResponse<String> response = ApiRuntime.HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Storage API error (" + response.statusCode() + "): " + response.body());
            return json.readValue(response.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(e);
        } catch (IOException e) { throw new IllegalStateException("Unable to read storage status", e); }
    }

    private <T> T post(String path, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(90)).header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody());
            ApiSession.authorize(builder);
            HttpResponse<String> response = ApiRuntime.HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Storage API error (" + response.statusCode() + "): " + response.body());
            return json.readValue(response.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(e);
        } catch (IOException e) { throw new IllegalStateException("Unable to run storage cleanup", e); }
    }

    private static String base() {
        String base = ConfigManager.getDataApiBaseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    public record Policy(int logRetentionDays, int reportRetentionDays, int exportRetentionDays,
                         int diagnosticRetentionDays, int importResultRetentionDays,
                         int tempRetentionDays, boolean compressLogs) { }
    public record Status(String workspace, long documentsBytes, long attachmentsBytes, long reportsBytes,
                         long exportsBytes, long logsBytes, long backupsBytes, long tempBytes,
                         long totalManagedBytes, String lastCleanupAt, String lastCleanupSummary,
                         Policy policy) { }
    public record CleanupResult(boolean dryRun, int filesDeleted, int filesCompressed,
                                long bytesReclaimed, String completedAt, String summary) { }
}
