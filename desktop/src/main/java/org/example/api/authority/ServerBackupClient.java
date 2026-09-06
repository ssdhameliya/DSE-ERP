package org.example.api.authority;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Company-server backup/restore authority used by LOCAL and SHARED_CLIENT desktop modes. */
public final class ServerBackupClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private String base() { return ConfigManager.getDataApiBaseUrl().replaceAll("/+$", "") + "/api/authority/backups"; }

    public List<BackupFile> list() { return Arrays.asList(request("GET", base(), null, BackupFile[].class)); }
    public BackupFile create() { return request("POST", base(), new byte[0], BackupFile.class); }
    public DatabaseMetrics metrics() { return request("GET", base() + "/metrics", null, DatabaseMetrics.class); }

    public BackupFile importBackup(Path file) {
        try {
            String name = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8);
            return request("POST", base() + "/import?filename=" + name, Files.readAllBytes(file), BackupFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("The selected backup could not be read.", e);
        }
    }

    public Validation validate(String name) { return request("POST", base() + "/" + enc(name) + "/validate", new byte[0], Validation.class); }
    public Message stageRestore(String name) { return request("POST", base() + "/" + enc(name) + "/restore/stage", new byte[0], Message.class); }
    public Message delete(String name) { return request("DELETE", base() + "/" + enc(name), null, Message.class); }

    public RecoveryPackage downloadRecoveryPackage(Path target) {
        if (target == null) throw new IllegalArgumentException("Recovery package target is required.");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + "/recovery-package"))
                    .timeout(Duration.ofMinutes(10))
                    .header("Accept", "application/zip")
                    .POST(HttpRequest.BodyPublishers.noBody());
            ApiSession.authorize(builder);
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 401) throw new ApiSession.AuthenticationRequiredException("Please sign in again");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body(), StandardCharsets.UTF_8);
                throw new IllegalStateException(message(body, "Recovery package request failed (HTTP " + response.statusCode() + ")"));
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length < 128) throw new IllegalStateException("The company server returned an empty recovery package.");
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.write(target, bytes);
            return new RecoveryPackage(target,
                    response.headers().firstValue("X-DSE-Recovery-Database-SHA256").orElse(""),
                    response.headers().firstValue("X-DSE-Recovery-Environment").orElse(""),
                    response.headers().firstValue("X-DSE-Recovery-Version").orElse(""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Recovery package download was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot download the disaster-recovery package from the company server", e);
        }
    }

    private String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20"); }

    private <T> T request(String method, String uri, byte[] body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(120)).header("Accept", "application/json");
            ApiSession.authorize(builder);
            if ("GET".equals(method)) builder.GET();
            else if ("DELETE".equals(method)) builder.DELETE();
            else builder.header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) throw new ApiSession.AuthenticationRequiredException("Please sign in again");
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException(message(response.body(), "Server backup request failed (HTTP " + response.statusCode() + ")"));
            return json.readValue(response.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Server backup request was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach the DSE ERP company server", e);
        }
    }

    private String message(String body, String fallback) {
        try {
            var node = json.readTree(body);
            if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) return node.get("message").asText();
        } catch (Exception ignored) { }
        return fallback;
    }

    public record BackupFile(String name, long size, String createdAt, String source) {}
    public record Validation(boolean valid, String message) {}
    public record DatabaseMetrics(String databaseName, long sizeBytes, boolean ready) {}
    public record Message(String message) {}
    public record RecoveryPackage(Path file, String databaseSha256, String environment, String applicationVersion) {}
}
