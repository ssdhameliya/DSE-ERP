package org.example.api.authority;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Base64;

public final class BusinessEmailClient {
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private String base() { return ConfigManager.getDataApiBaseUrl().replaceAll("/+$", "") + "/api/authority/email"; }

    public void send(String recipient, String subject, String body, Path attachment) {
        try {
            String name = attachment == null ? null : attachment.getFileName().toString();
            String data = attachment == null ? null : Base64.getEncoder().encodeToString(Files.readAllBytes(attachment));
            request("POST", base(), new Request(recipient, subject, body, name, data), Result.class);
        } catch (Exception e) {
            throw e instanceof RuntimeException x ? x : new IllegalStateException(e);
        }
    }

    public void resend(String recipient, String subject, String body, Path attachment) {
        try {
            String name = attachment == null ? null : attachment.getFileName().toString();
            String data = attachment == null ? null : Base64.getEncoder().encodeToString(Files.readAllBytes(attachment));
            request("POST", base() + "/resend", new Request(recipient, subject, body, name, data), Result.class);
        } catch (Exception e) {
            throw e instanceof RuntimeException x ? x : new IllegalStateException(e);
        }
    }

    public Settings settings() { return request("GET", base() + "/settings", null, Settings.class); }
    public Settings saveSettings(Settings settings) { return request("PUT", base() + "/settings", settings, Settings.class); }
    public Result test(String recipient) { return request("POST", base() + "/test", new TestRequest(recipient), Result.class); }

    private <T> T request(String method, String uri, Object body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(90)).header("Accept", "application/json");
            ApiSession.authorize(builder);
            if ("GET".equals(method)) builder.GET();
            else builder.header("Content-Type", "application/json").method(method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) throw new ApiSession.AuthenticationRequiredException("Please sign in again");
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException(serverMessage(response.statusCode(), response.body()));
            return json.readValue(response.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw e instanceof RuntimeException x ? x : new IllegalStateException(e);
        }
    }

    private String serverMessage(int status, String body) {
        try {
            var node=json.readTree(body==null?"":body);
            String message=node.path("message").asText("").trim();
            if(!message.isBlank()) return message;
        } catch(Exception ignored) { }
        return "Company-server email request failed (HTTP " + status + ").";
    }

    private record Request(String recipient, String subject, String body, String attachmentName, String attachmentBase64) {}
    private record TestRequest(String recipient) {}
    public record Settings(String email, String appPassword, String host, Integer port, boolean passwordConfigured) {}
    public record Result(boolean success, String message) {}
}
