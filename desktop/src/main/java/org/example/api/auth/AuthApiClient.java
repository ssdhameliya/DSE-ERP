package org.example.api.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;
import org.example.api.ApiSession;
import org.example.model.AppUser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * HTTP client used by the JavaFX desktop for authentication-related operations.
 * The desktop never needs PostgreSQL credentials for these calls; it only knows
 * the Spring Boot API base URL.
 */
public final class AuthApiClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;

    public AuthApiClient() {
        this(ConfigManager.getAuthApiBaseUrl());
    }

    AuthApiClient(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.json = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AppUser authenticate(String identity, String password) {
        LoginResponse response = post("/api/auth/login",
                new LoginRequest(identity, password), LoginResponse.class);
        if (response == null || !response.success()) return null;
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("The running backend does not support secure bearer login. "
                    + "Restart DSE ERP so the current backend can be launched.");
        }
        ApiSession.establish(response.accessToken(), response.expiresAt());
        return toAppUser(response.user());
    }

    public AppUser findActiveByIdentity(String identity) {
        LookupResponse response = post("/api/auth/lookup",
                new LookupRequest(identity), LookupResponse.class);
        return response == null || !response.found() ? null : toAppUser(response.user());
    }

    public void recordSuccessfulLogin(int userId) {
        post("/api/auth/login-complete", new UserIdRequest(userId), OperationResponse.class);
    }

    public void changePassword(int userId, String password) {
        OperationResponse response = post("/api/auth/password", new ChangePasswordRequest(userId, password),
                OperationResponse.class);
        if (response == null || !response.success()) {
            throw new IllegalStateException(response == null ? "Password update failed" : response.message());
        }
    }

    public void register(AppUser user) {
        OperationResponse response = post("/api/auth/register",
                new RegisterRequest(user.getUsername(), user.getPassword(), user.getFullName(), user.getEmail(), user.getRole()),
                OperationResponse.class);
        if (response == null || !response.success()) {
            throw new IllegalStateException(response == null ? "Registration failed" : response.message());
        }
    }

    public void logout() {
        String token = ApiSession.token();
        try {
            if (token != null) postAuthenticated("/api/auth/logout", null, OperationResponse.class);
        } finally {
            ApiSession.clear();
        }
    }

    public List<RoleOption> registrationRoles() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/registration-roles"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET();
            ApiSession.authorize(builder);
            HttpRequest request = builder.build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Unable to load registration roles");
            }
            RoleOption[] options = json.readValue(response.body(), RoleOption[].class);
            return Arrays.asList(options);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Role request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load registration roles from " + baseUrl, exception);
        }
    }

    public boolean healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            String payload = json.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (!"/api/auth/login".equals(path)) ApiSession.authorize(builder);
            HttpRequest request = builder.build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = response.body() == null || response.body().isBlank()
                        ? "HTTP " + response.statusCode() : response.body();
                throw new IllegalStateException("Authentication API error: " + message);
            }
            return json.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Authentication API request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot reach authentication server at " + baseUrl, exception);
        }
    }

    private <T> T postAuthenticated(String path, Object body, Class<T> responseType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT).header("Accept", "application/json");
            ApiSession.authorize(builder);
            if (body == null) builder.POST(HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) { ApiSession.clear(); throw new ApiSession.AuthenticationRequiredException("Please sign in again"); }
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Authentication API error (" + response.statusCode() + ")");
            return json.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Authentication API request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot reach authentication server at " + baseUrl, exception);
        }
    }

    private static AppUser toAppUser(UserPayload user) {
        if (user == null) return null;
        AppUser result = new AppUser();
        result.setId(user.id());
        result.setUsername(user.username());
        result.setFullName(user.fullName());
        result.setRole(user.role());
        if (user.roleId() != null) result.setRoleId(user.roleId());
        result.setEmail(user.email());
        result.setActive(user.active());
        result.setDepartment(user.department());
        result.setBranch(user.branch());
        result.setAccessLevel(user.accessLevel());
        result.setLocked(user.locked());
        result.setMfaEnabled(user.mfaEnabled());
        return result;
    }

    private static String normalizeBaseUrl(String value) {
        String url = value == null || value.isBlank() ? "http://localhost:8080" : value.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    public record LoginRequest(String identity, String password) {}
    public record LookupRequest(String identity) {}
    public record UserIdRequest(int userId) {}
    public record ChangePasswordRequest(int userId, String password) {}
    public record RegisterRequest(String username, String password, String fullName, String email, String role) {}
    public record LoginResponse(boolean success, UserPayload user, String message, String accessToken, String expiresAt) {}
    public record LookupResponse(boolean found, UserPayload user) {}
    public record OperationResponse(boolean success, String message) {}
    public record RoleOption(String code, String displayName) {
        @Override public String toString() { return displayName; }
    }
    public record UserPayload(int id, String username, String fullName, String role, Integer roleId,
                              String email, boolean active, String department, String branch,
                              String accessLevel, boolean locked, boolean mfaEnabled) {}
}

