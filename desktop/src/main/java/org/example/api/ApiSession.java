package org.example.api;

import java.net.http.HttpRequest;
import java.time.Instant;

public final class ApiSession {
    private static volatile String accessToken;
    private static volatile Instant expiresAt;
    private static volatile String apiBaseUrl;

    private ApiSession() {
    }

    public static void establish(String token, String expiry, String baseUrl) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Authentication token is missing");
        String normalizedBase = normalizeBaseUrl(baseUrl);
        if (normalizedBase.isBlank()) throw new IllegalArgumentException("Authentication server address is missing");
        accessToken = token;
        expiresAt = expiry == null || expiry.isBlank() ? null : Instant.parse(expiry);
        apiBaseUrl = normalizedBase;
    }

    /** Compatibility overload for tests/older callers; new login code must bind the issuing server explicitly. */
    public static void establish(String token, String expiry) {
        establish(token, expiry, org.example.config.ConfigManager.getDataApiBaseUrlUnbound());
    }

    public static HttpRequest.Builder authorize(HttpRequest.Builder request) {
        String token = accessToken;
        if (token == null || token.isBlank() || (expiresAt != null && !expiresAt.isAfter(Instant.now()))) {
            clear();
            throw new AuthenticationRequiredException("Please sign in again");
        }
        return request.header("Authorization", "Bearer " + token);
    }

    public static String token() {
        return accessToken;
    }

    public static String boundApiBaseUrl() {
        return apiBaseUrl;
    }

    public static boolean isEstablished() {
        return accessToken != null && !accessToken.isBlank();
    }

    public static void rebindApiBaseUrl(String baseUrl) {
        if (!isEstablished()) return;
        String normalizedBase = normalizeBaseUrl(baseUrl);
        if (normalizedBase.isBlank()) throw new IllegalArgumentException("Authentication server address is missing");
        apiBaseUrl = normalizedBase;
    }

    public static void clear() {
        accessToken = null;
        expiresAt = null;
        apiBaseUrl = null;
    }

    private static String normalizeBaseUrl(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static AuthenticationRequiredException rejected(String operation, String responseBody) {
        String code = jsonField(responseBody, "code");
        String message = jsonField(responseBody, "message");
        String prefix = operation == null || operation.isBlank() ? "ERP request" : operation.trim();
        String detail = code.isBlank() ? "AUTHENTICATION_REJECTED" : code;
        if (!message.isBlank() && !"Authentication required".equalsIgnoreCase(message)) {
            detail += " - " + message;
        }
        return new AuthenticationRequiredException(prefix + " was rejected by the ERP server (" + detail + "). Please sign in again.");
    }

    private static String jsonField(String json, String field) {
        if (json == null || json.isBlank() || field == null || field.isBlank()) return "";
        String marker = "\"" + field + "\"";
        int key = json.indexOf(marker);
        if (key < 0) return "";
        int colon = json.indexOf(':', key + marker.length());
        if (colon < 0) return "";
        int start = json.indexOf('\"', colon + 1);
        if (start < 0) return "";
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { out.append(c); escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '\"') break;
            out.append(c);
        }
        return out.toString().trim();
    }

    public static final class AuthenticationRequiredException extends IllegalStateException {
        public AuthenticationRequiredException(String message) {
            super(message);
        }
    }
}
