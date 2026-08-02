package org.example.update;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class GitHubReleaseClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public UpdateRelease latest(String owner, String repository, boolean includePrerelease) throws Exception {
        requirePart(owner, "GitHub owner"); requirePart(repository, "GitHub repository");
        String endpoint = includePrerelease
                ? "https://api.github.com/repos/%s/%s/releases?per_page=15".formatted(owner, repository)
                : "https://api.github.com/repos/%s/%s/releases/latest".formatted(owner, repository);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "DSE-ERP-Updater").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) throw new IllegalStateException("No published GitHub Release was found for " + owner + "/" + repository + ".");
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("GitHub returned HTTP " + response.statusCode() + ".");
        Object parsed = MiniJson.parse(response.body());
        if (parsed instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?,?> map && !bool(map,"draft") && (includePrerelease || !bool(map,"prerelease"))) return mapRelease(map);
            }
            throw new IllegalStateException("No suitable release is available in the selected channel.");
        }
        return mapRelease((Map<?,?>) parsed);
    }

    private UpdateRelease mapRelease(Map<?,?> map) {
        List<UpdateRelease.Asset> assets = new ArrayList<>();
        Object rawAssets = map.get("assets");
        if (rawAssets instanceof List<?> list) for (Object raw : list) if (raw instanceof Map<?,?> asset) {
            assets.add(new UpdateRelease.Asset(str(asset,"name"), number(asset,"size"), URI.create(str(asset,"browser_download_url")), str(asset,"content_type")));
        }
        String published = str(map,"published_at");
        return new UpdateRelease(str(map,"tag_name"), str(map,"name"), str(map,"body"),
                published.isBlank() ? Instant.EPOCH : Instant.parse(published), bool(map,"prerelease"), assets,
                URI.create(str(map,"html_url")));
    }

    private static void requirePart(String value, String label) { if (value == null || !value.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException(label + " is not configured correctly."); }
    private static String str(Map<?,?> map,String key){ Object v=map.get(key); return v==null?"":String.valueOf(v); }
    private static boolean bool(Map<?,?> map,String key){ return Boolean.TRUE.equals(map.get(key)); }
    private static long number(Map<?,?> map,String key){ Object v=map.get(key); return v instanceof Number n?n.longValue():0; }
}
