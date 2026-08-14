package org.example.update;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class GitHubReleaseClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public UpdateRelease latest(String owner, String repository, boolean includePrerelease) throws Exception {
        requirePart(owner, "GitHub owner"); requirePart(repository, "GitHub repository");
        String endpoint = includePrerelease
                ? "https://api.github.com/repos/%s/%s/releases?per_page=15".formatted(owner, repository)
                : "https://api.github.com/repos/%s/%s/releases/latest".formatted(owner, repository);
        HttpResponse<String> response = null;
        Exception last = null;
        for (int attempt=1; attempt<=3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(90)).header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "DSE-ERP-Updater").GET().build();
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode()!=408 && response.statusCode()!=429 && response.statusCode()<500) break;
                last = new IllegalStateException("GitHub returned HTTP " + response.statusCode() + ".");
            } catch (Exception failure) { last=failure; }
            if (attempt<3) Thread.sleep(attempt*1500L);
        }
        if(response==null) throw new IllegalStateException("GitHub release check failed after 3 attempts.",last);
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


    /** Loads one exact published release so Safe Rollback can retrieve a previous installer. */
    public UpdateRelease byVersion(String owner, String repository, String version) throws Exception {
        requirePart(owner, "GitHub owner"); requirePart(repository, "GitHub repository");
        String clean = Objects.requireNonNullElse(version, "").trim();
        if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
        if (!clean.matches("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?"))
            throw new IllegalArgumentException("Rollback version is not valid: " + version);
        HttpResponse<String> response = request("https://api.github.com/repos/%s/%s/releases/tags/v%s".formatted(owner, repository, clean));
        if (response.statusCode() == 404)
            response = request("https://api.github.com/repos/%s/%s/releases/tags/%s".formatted(owner, repository, clean));
        if (response.statusCode() == 404)
            throw new IllegalStateException("Published GitHub Release " + clean + " was not found for " + owner + "/" + repository + ".");
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("GitHub returned HTTP " + response.statusCode() + " while loading release " + clean + ".");
        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map<?,?> map)) throw new IllegalStateException("GitHub returned an invalid release response.");
        if (bool(map, "draft")) throw new IllegalStateException("Release " + clean + " is still a draft.");
        return mapRelease(map);
    }

    private HttpResponse<String> request(String endpoint) throws Exception {
        HttpResponse<String> response = null; Exception last = null;
        for (int attempt=1; attempt<=3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(90)).header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "DSE-ERP-Updater").GET().build();
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode()!=408 && response.statusCode()!=429 && response.statusCode()<500) return response;
                last = new IllegalStateException("GitHub returned HTTP " + response.statusCode() + ".");
            } catch (Exception failure) { last=failure; }
            if (attempt<3) Thread.sleep(attempt*1500L);
        }
        if (response != null) return response;
        throw new IllegalStateException("GitHub release request failed after 3 attempts.", last);
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
