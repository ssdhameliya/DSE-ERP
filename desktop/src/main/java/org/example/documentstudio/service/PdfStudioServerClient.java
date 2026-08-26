package org.example.documentstudio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Company-server client dedicated to the isolated PDF Studio 3 template store. */
final class PdfStudioServerClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String base = ConfigManager.getDataApiBaseUrl().replaceAll("/+$", "");

    List<ResourceMeta> list() {
        try {
            var response = send("GET", "/api/pdf-studio/templates", null, "application/json");
            return json.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception error) { throw failure(error); }
    }

    byte[] get(String key) {
        try { return sendBytes("GET", path(key), null).body(); }
        catch (Exception error) { throw failure(error); }
    }

    void put(String key, String fileName, byte[] content) {
        try {
            String path = path(key) + "?filename=" + enc(fileName);
            sendBytes("PUT", path, content);
        } catch (Exception error) { throw failure(error); }
    }

    void delete(String key) {
        try { sendBytes("DELETE", path(key), null); }
        catch (Exception error) { throw failure(error); }
    }

    private HttpResponse<String> send(String method, String path, byte[] body, String accept) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(90)).header("Accept", accept);
        ApiSession.authorize(builder);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        requireOk(response.statusCode(), response.body());
        return response;
    }

    private HttpResponse<byte[]> sendBytes(String method, String path, byte[] body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(90)).header("Accept", "application/octet-stream");
        ApiSession.authorize(builder);
        if (body != null) builder.header("Content-Type", "application/octet-stream");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        requireOk(response.statusCode(), response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8));
        return response;
    }

    private static String path(String key) { return "/api/pdf-studio/templates/" + enc(key); }
    private static String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private static void requireOk(int status, String body) { if (status < 200 || status >= 300) throw new IllegalStateException("PDF Studio server API error (" + status + "): " + body); }
    private static IllegalStateException failure(Exception error) { if (error instanceof InterruptedException) Thread.currentThread().interrupt(); return new IllegalStateException("Cannot synchronize PDF Studio templates with the company server", error); }

    record ResourceMeta(String key, String fileName, String contentType, String checksum, String updatedAt, long size) {}
}
