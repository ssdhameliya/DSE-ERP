package org.example.server.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side release gateway for DSE ERP desktop updates.
 *
 * <p>The GitHub credential is intentionally owned by the Spring server. Desktop
 * clients receive only release metadata and proxy asset URLs, so a private source
 * repository never requires shipping a GitHub token inside the desktop binary.</p>
 */
@Service
public final class UpdateDistributionService {
    private static final String USER_AGENT = "DSE-ERP-Server-Updater";
    private static final long PUBLISHED_ASSET_ALLOW_TTL_MILLIS = Duration.ofMinutes(5).toMillis();

    private final String owner;
    private final String repository;
    private final String token;
    private final String apiBase;
    private final HttpClient http;
    private final ObjectMapper json;
    private final ConcurrentHashMap<Long, Long> publishedAssetUntil = new ConcurrentHashMap<>();

    @Autowired
    public UpdateDistributionService(
            @Value("${dse.update.github.owner:ssdhameliya}") String owner,
            @Value("${dse.update.github.repository:DSE-ERP}") String repository,
            @Value("${dse.update.github.token:}") String token,
            @Value("${dse.update.github.api-base:https://api.github.com}") String apiBase,
            ObjectProvider<ObjectMapper> mapperProvider) {
        this(owner, repository, token, apiBase,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                mapperProvider.getIfAvailable(ObjectMapper::new));
    }

    UpdateDistributionService(String owner, String repository, String token, String apiBase,
                              HttpClient http, ObjectMapper json) {
        this.owner = requirePart(owner, "GitHub owner");
        this.repository = requirePart(repository, "GitHub repository");
        this.token = Objects.requireNonNullElse(token, "").trim();
        this.apiBase = stripTrailingSlash(Objects.requireNonNullElse(apiBase, "https://api.github.com").trim());
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
    }

    public ReleaseView latest(boolean includePrerelease) throws Exception {
        if (!includePrerelease) {
            JsonNode node = requestJson("/repos/%s/%s/releases/latest".formatted(owner, repository));
            return mapRelease(node);
        }
        for (JsonNode node : requestJson("/repos/%s/%s/releases?per_page=15".formatted(owner, repository))) {
            if (!node.path("draft").asBoolean(false)) return mapRelease(node);
        }
        throw new IllegalStateException("No suitable DSE ERP release is available in the selected update channel.");
    }

    public List<ReleaseView> releases(boolean includePrerelease, int limit) throws Exception {
        int bounded = Math.max(1, Math.min(100, limit));
        JsonNode root = requestJson("/repos/%s/%s/releases?per_page=%d".formatted(owner, repository, bounded));
        List<ReleaseView> result = new ArrayList<>();
        for (JsonNode node : root) {
            if (node.path("draft").asBoolean(false)) continue;
            if (!includePrerelease && node.path("prerelease").asBoolean(false)) continue;
            result.add(mapRelease(node));
        }
        return List.copyOf(result);
    }

    public ReleaseView byVersion(String version) throws Exception {
        String clean = normalizeVersion(version);
        HttpResponse<String> response = request("/repos/%s/%s/releases/tags/v%s".formatted(owner, repository, clean), "application/vnd.github+json");
        if (response.statusCode() == 404) {
            response = request("/repos/%s/%s/releases/tags/%s".formatted(owner, repository, clean), "application/vnd.github+json");
        }
        requireSuccess(response, "Published DSE ERP release " + clean + " was not found by the server update gateway.");
        JsonNode node = json.readTree(response.body());
        if (node.path("draft").asBoolean(false)) throw new IllegalStateException("Release " + clean + " is still a draft.");
        return mapRelease(node);
    }

    /**
     * Opens a private GitHub release asset for streaming. Authorization is sent only
     * to api.github.com. If GitHub redirects to its signed asset CDN, the second
     * request deliberately omits Authorization so the repository token is never
     * forwarded to another host.
     */
    public HttpResponse<InputStream> openAsset(long assetId, String range) throws Exception {
        if (assetId <= 0) throw new IllegalArgumentException("Invalid update asset id.");
        ensurePublishedAsset(assetId);
        String path = "/repos/%s/%s/releases/assets/%d".formatted(owner, repository, assetId);
        HttpRequest.Builder first = githubRequest(apiBase + path, "application/octet-stream")
                .timeout(Duration.ofMinutes(2));
        applyRange(first, range);
        HttpResponse<InputStream> response = http.send(first.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (isRedirect(response.statusCode())) {
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("GitHub asset redirect did not include a Location header."));
            try (InputStream ignored = response.body()) { }
            URI redirect = URI.create(apiBase + path).resolve(location);
            HttpRequest.Builder redirected = HttpRequest.newBuilder(redirect)
                    .timeout(Duration.ofMinutes(45))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/octet-stream");
            applyRange(redirected, range);
            return http.send(redirected.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        }
        return response;
    }

    private JsonNode requestJson(String path) throws Exception {
        HttpResponse<String> response = request(path, "application/vnd.github+json");
        requireSuccess(response, "No published DSE ERP release was found by the server update gateway.");
        return json.readTree(response.body());
    }

    private HttpResponse<String> request(String path, String accept) throws Exception {
        Exception last = null;
        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = githubRequest(apiBase + path, accept)
                        .timeout(Duration.ofSeconds(90)).GET().build();
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 408 && response.statusCode() != 429 && response.statusCode() < 500) return response;
                last = new IllegalStateException("GitHub returned HTTP " + response.statusCode() + ".");
            } catch (Exception failure) {
                last = failure;
            }
            if (attempt < 3) Thread.sleep(attempt * 1000L);
        }
        if (response != null) return response;
        throw new IllegalStateException("The server could not contact the DSE release source after 3 attempts.", last);
    }

    private HttpRequest.Builder githubRequest(String endpoint, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", USER_AGENT);
        if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private void requireSuccess(HttpResponse<String> response, String notFoundMessage) {
        if (response.statusCode() == 404) {
            String hint = token.isBlank()
                    ? " The repository may be private; configure DSE_GITHUB_UPDATE_TOKEN on the DSE ERP server."
                    : " Confirm that the configured server token can read this private repository.";
            throw new IllegalStateException(notFoundMessage + hint);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DSE release source returned HTTP " + response.statusCode() + ".");
        }
    }

    private ReleaseView mapRelease(JsonNode node) {
        List<AssetView> assets = new ArrayList<>();
        long expiresAt = System.currentTimeMillis() + PUBLISHED_ASSET_ALLOW_TTL_MILLIS;
        for (JsonNode asset : node.path("assets")) {
            long id = asset.path("id").asLong(0);
            if (id <= 0) continue;
            publishedAssetUntil.put(id, expiresAt);
            assets.add(new AssetView(
                    id,
                    asset.path("name").asText(""),
                    asset.path("size").asLong(0),
                    "/api/updates/assets/" + id,
                    asset.path("content_type").asText("application/octet-stream")));
        }
        String published = node.path("published_at").asText("");
        return new ReleaseView(
                node.path("tag_name").asText(""),
                node.path("name").asText(""),
                node.path("body").asText(""),
                published.isBlank() ? Instant.EPOCH.toString() : published,
                node.path("prerelease").asBoolean(false),
                List.copyOf(assets),
                "");
    }

    /**
     * Prevents the public proxy endpoint from becoming a generic private-repository asset reader.
     * Only assets belonging to published, non-draft releases are eligible. Normal update metadata
     * lookups populate this allow-list; a cache miss is revalidated against GitHub server-side.
     */
    private void ensurePublishedAsset(long assetId) throws Exception {
        long now = System.currentTimeMillis();
        Long allowedUntil = publishedAssetUntil.get(assetId);
        if (allowedUntil != null && allowedUntil >= now) return;

        JsonNode root = requestJson("/repos/%s/%s/releases?per_page=100".formatted(owner, repository));
        long expiresAt = now + PUBLISHED_ASSET_ALLOW_TTL_MILLIS;
        boolean found = false;
        for (JsonNode release : root) {
            if (release.path("draft").asBoolean(false)) continue;
            for (JsonNode asset : release.path("assets")) {
                long id = asset.path("id").asLong(0);
                if (id <= 0) continue;
                publishedAssetUntil.put(id, expiresAt);
                if (id == assetId) found = true;
            }
        }
        publishedAssetUntil.entrySet().removeIf(entry -> entry.getValue() < now);
        if (!found) throw new IllegalArgumentException("Update asset is not part of a published DSE ERP release.");
    }

    private static String normalizeVersion(String version) {
        String clean = Objects.requireNonNullElse(version, "").trim();
        if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
        if (!clean.matches("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException("Update version is not valid: " + version);
        }
        return clean;
    }

    private static String requirePart(String value, String label) {
        String clean = Objects.requireNonNullElse(value, "").trim();
        if (!clean.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException(label + " is not configured correctly.");
        return clean;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void applyRange(HttpRequest.Builder builder, String range) {
        String value = Objects.requireNonNullElse(range, "").trim();
        if (!value.isBlank() && value.toLowerCase(Locale.ROOT).startsWith("bytes=")) builder.header("Range", value);
    }

    public record ReleaseView(String tagName, String name, String body, String publishedAt,
                              boolean prerelease, List<AssetView> assets, String htmlUrl) { }

    public record AssetView(long id, String name, long size, String downloadUrl, String contentType) { }
}
