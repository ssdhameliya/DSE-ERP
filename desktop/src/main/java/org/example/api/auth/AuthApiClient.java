package org.example.api.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.ConfigManager;
import org.example.api.ApiSession;
import org.example.api.runtime.RuntimeApiClient;
import org.example.shared.RuntimeContract;
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
    private final String fixedBaseUrl;
    private volatile String pendingLoginBaseUrl;

    public AuthApiClient() {
        this.fixedBaseUrl = null;
        this.http = org.example.api.ApiRuntime.HTTP;
        this.json = org.example.api.ApiRuntime.JSON;
    }

    AuthApiClient(String baseUrl) {
        this.fixedBaseUrl = normalizeBaseUrl(baseUrl);
        this.http = org.example.api.ApiRuntime.HTTP;
        this.json = org.example.api.ApiRuntime.JSON;
    }

    public LoginAttempt authenticate(String identity, String password) {
        String loginBase = preLoginBaseUrl();
        requireCompatibleRuntime(loginBase);
        pendingLoginBaseUrl = loginBase;
        LoginResponse response = postAt(loginBase, "/api/auth/login",
                new LoginRequest(identity, password), LoginResponse.class);
        if (response == null) return null;
        if (!response.success()) {
            throw new IllegalStateException(response.message() == null || response.message().isBlank()
                    ? "Invalid email/username or password." : response.message());
        }
        AppUser user = toAppUser(response.user());
        if (response.mfaRequired()) {
            if (response.challengeId() == null || response.challengeId().isBlank()) {
                throw new IllegalStateException("The authentication server did not return an MFA challenge");
            }
            return new LoginAttempt(user, true, response.challengeId(), response.maskedDestination());
        }
        establishSession(response, loginBase);
        pendingLoginBaseUrl = null;
        return new LoginAttempt(user, false, null, null);
    }

    public AppUser completeLoginMfa(String challengeId, String otp) {
        String loginBase = pendingLoginBaseUrl == null || pendingLoginBaseUrl.isBlank()
                ? preLoginBaseUrl() : pendingLoginBaseUrl;
        requireCompatibleRuntime(loginBase);
        LoginResponse response = postAt(loginBase, "/api/auth/login/mfa/complete",
                new LoginMfaCompleteRequest(challengeId, otp), LoginResponse.class);
        if (response == null || !response.success() || response.mfaRequired()) {
            throw new IllegalStateException(response == null ? "MFA verification failed" : response.message());
        }
        establishSession(response, loginBase);
        pendingLoginBaseUrl = null;
        return toAppUser(response.user());
    }

    public LoginMfaChallengeResponse resendLoginMfa(String challengeId) {
        String loginBase = pendingLoginBaseUrl == null || pendingLoginBaseUrl.isBlank()
                ? preLoginBaseUrl() : pendingLoginBaseUrl;
        requireCompatibleRuntime(loginBase);
        LoginMfaChallengeResponse response = postAt(loginBase, "/api/auth/login/mfa/resend",
                new LoginMfaResendRequest(challengeId), LoginMfaChallengeResponse.class);
        if (response == null || !response.success()) {
            throw new IllegalStateException(response == null ? "Unable to resend verification code" : response.message());
        }
        return response;
    }

    private void establishSession(LoginResponse response, String issuingBaseUrl) {
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("The authentication server did not return a secure session token");
        }
        requireCompatibleRuntime(issuingBaseUrl);
        ApiSession.establish(response.accessToken(), response.expiresAt(), issuingBaseUrl);
        try {
            verifySession();
            verifyBusinessSession();
        } catch (RuntimeException failure) {
            ApiSession.clear();
            throw failure;
        }
    }

    public void recordSuccessfulLogin(int userId) {
        post("/api/auth/login-complete", new UserIdRequest(userId), OperationResponse.class);
    }

    public void changePassword(int userId, String currentPassword, String password) {
        OperationResponse response = post("/api/auth/password", new ChangePasswordRequest(userId, currentPassword, password),
                OperationResponse.class);
        if (response == null || !response.success()) {
            throw new IllegalStateException(response == null ? "Password update failed" : response.message());
        }
    }

    public void register(AppUser user) {
        OperationResponse response = post("/api/auth/register",
                new RegisterRequest(user.getUsername(), user.getPassword(), user.getFullName(), user.getEmail(), user.getRole(),
                        user.isMfaEnabled()),
                OperationResponse.class);
        if (response == null || !response.success()) {
            throw new IllegalStateException(response == null ? "Registration failed" : response.message());
        }
    }

    public CaptchaResponse registrationCaptcha() {
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl()+"/api/auth/registration/captcha")).timeout(REQUEST_TIMEOUT).header("Accept","application/json").GET().build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException(apiErrorMessage(response));
            return json.readValue(response.body(),CaptchaResponse.class);
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("CAPTCHA request was interrupted",e);} catch(IOException e){throw new IllegalStateException("Cannot load registration CAPTCHA",e);}
    }

    public ChallengeResponse requestRegistrationOtp(AppUser user,String captchaChallengeId,String captchaAnswer) {
        ChallengeResponse response=post("/api/auth/registration/request",
                new RegistrationOtpRequest(user.getUsername(),user.getFullName(),user.getEmail(),user.getRole(),true,captchaChallengeId,captchaAnswer),ChallengeResponse.class);
        if(response==null||!response.success())throw new IllegalStateException(response==null?"Registration verification failed":response.message());
        return response;
    }

    public RegistrationMfaSetupResponse verifyRegistrationEmail(AppUser user,String challengeId,String otp) {
        RegistrationMfaSetupResponse response=post("/api/auth/registration/email/verify",
                new RegistrationEmailVerifyRequest(challengeId,otp,user.getUsername(),user.getPassword(),user.getFullName(),user.getEmail(),user.getRole(),true),RegistrationMfaSetupResponse.class);
        if(response==null||!response.success())throw new IllegalStateException(response==null?"Email verification failed":response.message());
        return response;
    }

    public void completeRegistrationMfa(long registrationId,String otp) {
        OperationResponse response=post("/api/auth/registration/mfa/complete",new RegistrationMfaCompleteRequest(registrationId,otp),OperationResponse.class);
        if(response==null||!response.success())throw new IllegalStateException(response==null?"Authenticator verification failed":response.message());
    }

    public ChallengeResponse requestPasswordReset(String identity) {
        ChallengeResponse response = post("/api/auth/password-reset/request",
                new PasswordResetOtpRequest(identity), ChallengeResponse.class);
        if (response == null || !response.success())
            throw new IllegalStateException(response == null ? "Password reset request failed" : response.message());
        return response;
    }

    public void completePasswordReset(String challengeId, String otp, String totp, String password) {
        OperationResponse response = post("/api/auth/password-reset/complete",
                new PasswordResetCompleteRequest(challengeId, otp, totp, password), OperationResponse.class);
        if (response == null || !response.success())
            throw new IllegalStateException(response == null ? "Password reset failed" : response.message());
    }

    public void extendSession() {
        String token = ApiSession.token();
        if (token == null || token.isBlank()) throw new IllegalStateException("No authenticated session to extend");
        SessionExtendResponse response = postAuthenticated("/api/auth/session/extend", null, SessionExtendResponse.class);
        if (response == null || !response.success() || response.accessToken() == null || response.accessToken().isBlank())
            throw new IllegalStateException(response == null ? "Session extension failed" : response.message());
        ApiSession.establish(response.accessToken(), response.expiresAt(), baseUrl());
    }

    public void logout() { logout("MANUAL_LOGOUT"); }

    public void logout(String reason) {
        String token = ApiSession.token();
        try {
            if (token != null) postAuthenticatedWithHeader("/api/auth/logout", "X-DSE-Logout-Reason", reason == null ? "MANUAL_LOGOUT" : reason, null, OperationResponse.class);
        } finally {
            ApiSession.clear();
        }
    }

    public List<RoleOption> loginRoles() { return roleOptions("/api/auth/login-roles", "login roles"); }

    public List<RoleOption> registrationRoles() { return roleOptions("/api/auth/registration-roles", "registration roles"); }

    private List<RoleOption> roleOptions(String path, String label) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Unable to load " + label + " from Role Master");
            }
            RoleOption[] options = json.readValue(response.body(), RoleOption[].class);
            return Arrays.asList(options);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Role request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load " + label + " from " + baseUrl(), exception);
        }
    }


    public void verifySession() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/session"))
                    .timeout(REQUEST_TIMEOUT).header("Accept", "application/json").GET();
            ApiSession.authorize(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw ApiSession.rejected("Login session verification", response.body());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(apiErrorMessage(response));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Session verification was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot verify the login session with " + baseUrl(), exception);
        }
    }

    /**
     * Prove the bearer token through the same secured business-data path used by normal
     * JavaFX screens. A login must not open the dashboard when only /api/auth endpoints
     * are reachable/authenticated.
     */
    public void verifyBusinessSession() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/profile"))
                    .timeout(REQUEST_TIMEOUT).header("Accept", "application/json").GET();
            ApiSession.authorize(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw ApiSession.rejected("Business session verification", response.body());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Login succeeded, but the ERP business API verification failed (HTTP " + response.statusCode() + ")");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Business session verification was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot verify the login session with the ERP business API at " + baseUrl(), exception);
        }
    }

    public List<EffectivePermission> effectivePermissions() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/effective-permissions"))
                    .timeout(REQUEST_TIMEOUT).header("Accept", "application/json").GET();
            ApiSession.authorize(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) { throw ApiSession.rejected("Permission request", response.body()); }
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException(apiErrorMessage(response));
            EffectivePermission[] values = json.readValue(response.body(), EffectivePermission[].class);
            return Arrays.asList(values);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Permission request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load effective permissions from " + baseUrl(), exception);
        }
    }
    public boolean healthCheck() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/auth/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return postAt(baseUrl(), path, body, responseType);
    }

    private <T> T postAt(String serverBaseUrl, String path, Object body, Class<T> responseType) {
        try {
            String payload = json.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(normalizeBaseUrl(serverBaseUrl) + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (!isPublicAuthPath(path)) ApiSession.authorize(builder);
            HttpRequest request = builder.build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 && responseType == LoginResponse.class) {
                return json.readValue(response.body(), responseType);
            }
            if (response.statusCode() == 401 || response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(apiErrorMessage(response));
            }
            return json.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Authentication API request was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot reach authentication server at " + normalizeBaseUrl(serverBaseUrl), exception);
        }
    }

    private <T> T postAuthenticatedWithHeader(String path, String header, String value, Object body, Class<T> responseType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(REQUEST_TIMEOUT).header("Accept", "application/json")
                    .header(header, value == null ? "" : value);
            ApiSession.authorize(builder);
            if (body == null) builder.POST(HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) { throw ApiSession.rejected("Authentication request", response.body()); }
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Authentication API error (" + response.statusCode() + ")");
            return json.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Authentication API request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot reach authentication server at " + baseUrl(), exception);
        }
    }

    private <T> T postAuthenticated(String path, Object body, Class<T> responseType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(REQUEST_TIMEOUT).header("Accept", "application/json");
            ApiSession.authorize(builder);
            if (body == null) builder.POST(HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) { throw ApiSession.rejected("Authentication request", response.body()); }
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Authentication API error (" + response.statusCode() + ")");
            return json.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Authentication API request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot reach authentication server at " + baseUrl(), exception);
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

    private String preLoginBaseUrl() {
        return fixedBaseUrl == null ? normalizeBaseUrl(ConfigManager.getDataApiBaseUrlUnbound()) : fixedBaseUrl;
    }

    private void requireCompatibleRuntime(String serverBaseUrl) {
        RuntimeApiClient.RuntimeStatus status = new RuntimeApiClient(normalizeBaseUrl(serverBaseUrl)).status();
        if (!status.ready()) {
            throw new IllegalStateException(status.message() == null || status.message().isBlank()
                    ? "The DSE ERP server is not ready" : status.message());
        }
        if (!RuntimeContract.SERVICE_NAME.equals(status.service())
                || !RuntimeContract.APP_VERSION.equals(status.version())
                || !RuntimeContract.API_REVISION.equals(status.apiRevision())
                || !RuntimeContract.BUILD_REVISION.equals(status.buildRevision())) {
            throw new IllegalStateException("Desktop/server version mismatch. This desktop requires DSE ERP "
                    + RuntimeContract.APP_VERSION + " build " + RuntimeContract.BUILD_REVISION
                    + ", but the running backend reports version " + status.version() + " build " + status.buildRevision()
                    + ". Stop the stale backend and restart DSE ERP.");
        }
    }

    private String baseUrl() {
        return fixedBaseUrl == null ? normalizeBaseUrl(ConfigManager.getAuthApiBaseUrl()) : fixedBaseUrl;
    }

    private static String normalizeBaseUrl(String value) {
        String url = value == null || value.isBlank() ? "http://localhost:8080" : value.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static boolean isPublicAuthPath(String path) {
        return "/api/auth/login".equals(path)
                || path.startsWith("/api/auth/login/mfa/")
                || path.startsWith("/api/auth/registration/")
                || path.startsWith("/api/auth/password-reset/");
    }

    private String apiErrorMessage(HttpResponse<String> response) {
        try {
            OperationResponse error = json.readValue(response.body(), OperationResponse.class);
            if (error.message() != null && !error.message().isBlank()) return error.message();
        } catch (Exception ignored) { }
        return "Authentication request failed (HTTP " + response.statusCode() + ")";
    }

    public record LoginRequest(String identity, String password) {}
    public record LoginMfaCompleteRequest(String challengeId, String otp) {}
    public record LoginMfaResendRequest(String challengeId) {}
    public record UserIdRequest(int userId) {}
    public record ChangePasswordRequest(int userId, String currentPassword, String password) {}
    public record RegisterRequest(String username, String password, String fullName, String email, String role,
                                  boolean mfaEnabled) {}
    public record CaptchaResponse(String challengeId,String question,String expiresIn) {}
    public record RegistrationOtpRequest(String username,String fullName,String email,String role,boolean mfaEnabled,String captchaChallengeId,String captchaAnswer) {}
    public record RegistrationEmailVerifyRequest(String challengeId,String otp,String username,String password,String fullName,String email,String role,boolean mfaEnabled) {}
    public record RegistrationMfaSetupResponse(boolean success,Long registrationId,String manualSecret,String provisioningUri,String message) {}
    public record RegistrationMfaCompleteRequest(long registrationId,String otp) {}
    public record PasswordResetOtpRequest(String identity) {}
    public record PasswordResetCompleteRequest(String challengeId,String otp,String totp,String password) {}
    public record ChallengeResponse(boolean success, String challengeId, String message) {}
    public record LoginMfaChallengeResponse(boolean success, String challengeId, String message,
                                            String maskedDestination) {}
    public record LoginResponse(boolean success, UserPayload user, String message, String accessToken, String expiresAt,
                                boolean mfaRequired, String challengeId, String maskedDestination) {}
    public record LoginAttempt(AppUser user, boolean mfaRequired, String challengeId, String maskedDestination) {}
    public record OperationResponse(boolean success, String message) {}
    public record EffectivePermission(String module, String action, String description) {}
    public record RoleOption(String code, String displayName) {
        @Override public String toString() { return displayName; }
    }
    public record UserPayload(int id, String username, String fullName, String role, Integer roleId,
                              String email, boolean active, String department, String branch,
                              String accessLevel, boolean locked, boolean mfaEnabled) {}

    public record SessionExtendResponse(boolean success,String message,String accessToken,String expiresAt){}
}
