package org.example.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration lifetime;

    public TokenService(@Value("${dse.auth.token-hours:8}") long tokenHours) {
        lifetime = Duration.ofHours(Math.max(1, Math.min(tokenHours, 24)));
    }

    public IssuedToken issue(AuthenticatedUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(lifetime);
        sessions.put(value, new Session(user, expiresAt));
        return new IssuedToken(value, expiresAt);
    }

    public Optional<AuthenticatedUser> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Session session = sessions.get(token);
        if (session == null) return Optional.empty();
        if (!session.expiresAt.isAfter(Instant.now())) {
            sessions.remove(token, session);
            return Optional.empty();
        }
        return Optional.of(session.user);
    }

    public void revoke(String token) {
        if (token != null) sessions.remove(token);
    }

    public void revokeUser(int userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().user.id() == userId);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    private record Session(AuthenticatedUser user, Instant expiresAt) {
    }
}

