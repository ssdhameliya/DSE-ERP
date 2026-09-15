package org.example.server.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UpdateDistributionServiceTest {
    private HttpServer api;
    private HttpServer asset;

    @AfterEach
    void stopServers() {
        if (api != null) api.stop(0);
        if (asset != null) asset.stop(0);
    }

    @Test
    void privateReleaseMetadataUsesServerTokenAndReturnsOnlyProxyAssetUrls() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/repos/ssdhameliya/DSE-ERP/releases/latest", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("""
                    {"tag_name":"v10.9.8","name":"DSE ERP","body":"Notes","published_at":"2026-09-14T17:57:36Z","prerelease":false,
                     "html_url":"https://github.com/ssdhameliya/DSE-ERP/releases/tag/v10.9.8",
                     "assets":[{"id":42,"name":"DSE-ERP-10.9.8-Windows-x64.exe","size":1234,"content_type":"application/octet-stream","browser_download_url":"https://github.com/private/asset"}]}
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        api.start();

        UpdateDistributionService service = service("server-secret", api.getAddress().getPort());
        var release = service.latest(false);

        assertEquals("Bearer server-secret", authorization.get());
        assertEquals("v10.9.8", release.tagName());
        assertEquals(1, release.assets().size());
        assertEquals("/api/updates/assets/42", release.assets().getFirst().downloadUrl());
        assertFalse(release.toString().contains("server-secret"));
        assertFalse(release.assets().getFirst().downloadUrl().contains("github.com"));
        assertTrue(release.htmlUrl().isBlank(), "private GitHub release URL must not be exposed to desktop clients");
    }

    @Test
    void assetRedirectNeverForwardsGithubAuthorizationAndPreservesRange() throws Exception {
        AtomicReference<String> firstAuthorization = new AtomicReference<>();
        AtomicReference<String> secondAuthorization = new AtomicReference<>();
        AtomicReference<String> firstRange = new AtomicReference<>();
        AtomicReference<String> secondRange = new AtomicReference<>();

        asset = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        asset.createContext("/signed-asset", exchange -> {
            secondAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            secondRange.set(exchange.getRequestHeaders().getFirst("Range"));
            byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().add("Content-Range", "bytes 10-19/20");
            exchange.sendResponseHeaders(206, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        asset.start();

        api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/repos/ssdhameliya/DSE-ERP/releases", exchange -> {
            byte[] body = "[{\"draft\":false,\"assets\":[{\"id\":42}]}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        api.createContext("/repos/ssdhameliya/DSE-ERP/releases/assets/42", exchange -> {
            firstAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            firstRange.set(exchange.getRequestHeaders().getFirst("Range"));
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + asset.getAddress().getPort() + "/signed-asset");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        api.start();

        UpdateDistributionService service = service("server-secret", api.getAddress().getPort());
        var response = service.openAsset(42, "bytes=10-");
        byte[] data;
        try (var input = response.body()) { data = input.readAllBytes(); }

        assertEquals(206, response.statusCode());
        assertEquals("Bearer server-secret", firstAuthorization.get());
        assertEquals("bytes=10-", firstRange.get());
        assertNull(secondAuthorization.get(), "GitHub token must not be forwarded to a redirected asset host");
        assertEquals("bytes=10-", secondRange.get());
        assertEquals("0123456789", new String(data, StandardCharsets.UTF_8));
    }

    @Test
    void assetProxyRejectsAssetsThatAreNotInPublishedReleases() throws Exception {
        api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/repos/ssdhameliya/DSE-ERP/releases", exchange -> {
            byte[] body = "[{\"draft\":true,\"assets\":[{\"id\":99}]}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        api.start();

        UpdateDistributionService service = service("server-secret", api.getAddress().getPort());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.openAsset(99, null));
        assertTrue(failure.getMessage().contains("published DSE ERP release"));
    }

    private static UpdateDistributionService service(String token, int port) {
        return new UpdateDistributionService(
                "ssdhameliya", "DSE-ERP", token, "http://127.0.0.1:" + port,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build(),
                new ObjectMapper());
    }
}
