package org.example.update;

import org.example.config.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Desktop release client backed by the DSE Spring server.
 *
 * <p>No GitHub credential or private-repository URL is required on the desktop.
 * The server owns private release access and returns proxy URLs for installer
 * assets so update checks also work before login.</p>
 */
public final class ServerReleaseClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateRelease latest(boolean includePrerelease) throws Exception {
        return mapRelease(object(request("/releases/latest?includePrerelease=" + includePrerelease)));
    }

    public List<UpdateRelease> releases(boolean includePrerelease, int limit) throws Exception {
        int bounded = Math.max(1, Math.min(100, limit));
        Object parsed = MiniJson.parse(request("/releases?includePrerelease=" + includePrerelease + "&limit=" + bounded));
        if (!(parsed instanceof List<?> list)) throw new IllegalStateException("The company update service returned an invalid releases response.");
        List<UpdateRelease> result = new ArrayList<>();
        for (Object value : list) if (value instanceof Map<?,?> map) result.add(mapRelease(map));
        return List.copyOf(result);
    }

    public UpdateRelease byVersion(String version) throws Exception {
        String clean = normalizeVersion(version);
        return mapRelease(object(request("/releases/" + clean)));
    }

    public URI serviceBaseUri() {
        return URI.create(serviceBase());
    }

    private String request(String path) throws Exception {
        String endpoint = serviceBase() + path;
        HttpResponse<String> response = null;
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(90))
                        .header("Accept", "application/json")
                        .header("User-Agent", "DSE-ERP-Updater")
                        .GET().build();
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 408 && response.statusCode() != 429 && response.statusCode() < 500) break;
                last = new IllegalStateException("Company update service returned HTTP " + response.statusCode() + ".");
            } catch (Exception failure) {
                last = failure;
            }
            if (attempt < 3) Thread.sleep(attempt * 1000L);
        }
        if (response == null) throw new IllegalStateException("The company update service could not be reached after 3 attempts.", last);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = responseMessage(response.body());
            if (response.statusCode() == 404 && detail.isBlank()) detail = "The requested DSE ERP release was not found by the company update service.";
            if (detail.isBlank()) detail = "Company update service returned HTTP " + response.statusCode() + ".";
            throw new IllegalStateException(detail);
        }
        return response.body();
    }

    private UpdateRelease mapRelease(Map<?,?> map) {
        List<UpdateRelease.Asset> assets = new ArrayList<>();
        Object rawAssets = map.get("assets");
        if (rawAssets instanceof List<?> list) {
            for (Object raw : list) if (raw instanceof Map<?,?> asset) {
                String path = str(asset, "downloadUrl");
                if (path.isBlank()) continue;
                URI download = URI.create(path);
                if (!download.isAbsolute()) download = URI.create(serviceBase()).resolve(path);
                assets.add(new UpdateRelease.Asset(str(asset, "name"), number(asset, "size"), download, str(asset, "contentType")));
            }
        }
        String published = str(map, "publishedAt");
        String html = str(map, "htmlUrl");
        URI htmlUrl = html.isBlank() ? URI.create(serviceBase()) : URI.create(html);
        return new UpdateRelease(str(map, "tagName"), str(map, "name"), str(map, "body"),
                published.isBlank() ? Instant.EPOCH : Instant.parse(published), bool(map, "prerelease"), assets, htmlUrl);
    }

    private String serviceBase() {
        String configured = Objects.requireNonNullElse(System.getenv("DSE_UPDATE_SERVICE_URL"), "").trim();
        String root = configured.isBlank() ? ConfigManager.getDataApiBaseUrlUnbound() : configured;
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        if (root.isBlank()) throw new IllegalStateException("The DSE ERP update service address is not configured.");
        return root + "/api/updates";
    }

    private static Map<?,?> object(String body) {
        Object parsed = MiniJson.parse(body);
        if (!(parsed instanceof Map<?,?> map)) throw new IllegalStateException("The company update service returned an invalid release response.");
        return map;
    }

    private static String normalizeVersion(String version) {
        String clean = Objects.requireNonNullElse(version, "").trim();
        if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
        if (!clean.matches("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?"))
            throw new IllegalArgumentException("Update version is not valid: " + version);
        return clean;
    }

    private static String responseMessage(String body) {
        try {
            Object parsed = MiniJson.parse(Objects.requireNonNullElse(body, ""));
            if (parsed instanceof Map<?,?> map) return str(map, "message");
        } catch (Exception ignored) { }
        return "";
    }

    private static String str(Map<?,?> map, String key) { Object value = map.get(key); return value == null ? "" : String.valueOf(value); }
    private static boolean bool(Map<?,?> map, String key) { return Boolean.TRUE.equals(map.get(key)); }
    private static long number(Map<?,?> map, String key) { Object value = map.get(key); return value instanceof Number n ? n.longValue() : 0L; }
}
