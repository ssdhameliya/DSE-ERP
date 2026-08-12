package org.example.api;

import java.net.http.HttpRequest;
import java.time.Instant;

public final class ApiSession {
    private static volatile String accessToken;
    private static volatile Instant expiresAt;

    private ApiSession() {
    }

    public static void establish(String token, String expiry) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Authentication token is missing");
        accessToken = token;
        expiresAt = expiry == null || expiry.isBlank() ? null : Instant.parse(expiry);
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

    public static void clear() {
        accessToken = null;
        expiresAt = null;
    }

    public static final class AuthenticationRequiredException extends IllegalStateException {
        public AuthenticationRequiredException(String message) {
            super(message);
        }
    }
}

