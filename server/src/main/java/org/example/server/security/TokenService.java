package org.example.server.security;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Server-authoritative signed bearer sessions.
 *
 * <p>R12 retains the signed-session model and no longer treats a row in {@code auth_session} as the sole proof of
 * identity. The bearer itself is HMAC-signed with a company-database key and
 * carries a per-user authentication version. Every request re-checks the user
 * in PostgreSQL and checks the shared revocation table. This keeps logout,
 * password/role changes, account lock and multi-server revocation authoritative
 * while allowing a valid login to survive a Spring restart or loss of a
 * non-authoritative session-registry row.</p>
 *
 * <p>{@code auth_session} is retained as an audit/active-session registry and
 * stores only SHA-256 token hashes. Raw bearer tokens and the signing key are
 * never returned by any server API.</p>
 */
@Service
public class TokenService {
    private static final int NONCE_BYTES = 24;
    private static final int SIGNING_KEY_BYTES = 48;

    private final SecureRandom random = new SecureRandom();
    private final Duration lifetime;
    private final JpaNativeRepository database;
    private volatile byte[] cachedSigningKey;

    public TokenService(@Value("${dse.auth.token-hours:8}") long tokenHours,
                        JpaNativeRepository database) {
        lifetime = Duration.ofHours(Math.max(1, Math.min(tokenHours, 24)));
        this.database = database;
    }

    @Transactional
    public IssuedToken issue(AuthenticatedUser user) {
        if (user == null || user.id() <= 0) throw new IllegalArgumentException("Authenticated user is required");
        UserState state = userState(user.id()).orElseThrow(() -> new IllegalArgumentException("User account is unavailable"));
        if (!state.active() || state.locked()) throw new SecurityException("User account is not available for sign in");

        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        Instant expiresAt = Instant.now().plus(lifetime);
        String value = SignedTokenCodec.encode(user.id(), state.authVersion(), expiresAt, nonce, signingKey(true));
        String tokenHash = hash(value);

        cleanupExpired();
        database.update("""
                INSERT INTO auth_session(token_hash,user_id,username,role_code,expires_at)
                VALUES (?,?,?,?,?)
                ON CONFLICT (token_hash) DO UPDATE SET
                    user_id=EXCLUDED.user_id,
                    username=EXCLUDED.username,
                    role_code=EXCLUDED.role_code,
                    expires_at=EXCLUDED.expires_at
                """, tokenHash, user.id(), state.username(), state.role(),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return new IssuedToken(value, expiresAt);
    }

    /** Compatibility facade used by existing security callers. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String token) {
        return inspect(token).user();
    }

    /** Returns a precise authentication result so the HTTP layer can diagnose 401s. */
    @Transactional(readOnly = true)
    public AuthenticationResult inspect(String token) {
        if (token == null || token.isBlank()) return AuthenticationResult.failure(Status.MISSING);
        byte[] key = signingKey(false);
        if (key == null) return AuthenticationResult.failure(Status.SERVER_KEY_MISSING);

        SignedTokenCodec.DecodeResult decoded = SignedTokenCodec.decode(token, key);
        if (!decoded.valid()) return AuthenticationResult.failure(switch (decoded.failure()) {
            case MISSING -> Status.MISSING;
            case INVALID_SIGNATURE -> Status.INVALID_SIGNATURE;
            case SERVER_KEY_MISSING -> Status.SERVER_KEY_MISSING;
            case MALFORMED, UNSUPPORTED_VERSION, NONE -> Status.MALFORMED;
        });

        SignedTokenCodec.Decoded bearer = decoded.token();
        if (!bearer.expiresAt().isAfter(Instant.now())) return AuthenticationResult.failure(Status.EXPIRED);

        UserState state = userState(bearer.userId()).orElse(null);
        if (state == null) return AuthenticationResult.failure(Status.ACCOUNT_UNAVAILABLE);
        if (!state.active() || state.locked()) return AuthenticationResult.failure(Status.ACCOUNT_UNAVAILABLE);
        if (state.authVersion() != bearer.authVersion()) return AuthenticationResult.failure(Status.REVOKED);

        Long revoked = database.queryForObject("""
                SELECT COUNT(*) FROM auth_token_revocation
                WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP
                """, Long.class, hash(token));
        if (revoked != null && revoked > 0) return AuthenticationResult.failure(Status.REVOKED);

        return AuthenticationResult.authenticated(new AuthenticatedUser(
                bearer.userId(), state.username(), state.role()));
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        Instant expiresAt = tokenExpiry(token).orElseGet(() -> Instant.now().plus(lifetime));
        database.update("""
                INSERT INTO auth_token_revocation(token_hash,expires_at)
                VALUES (?,?)
                ON CONFLICT (token_hash) DO UPDATE SET expires_at=EXCLUDED.expires_at, revoked_at=CURRENT_TIMESTAMP
                """, hash(token), OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        database.update("DELETE FROM auth_session WHERE token_hash=?", hash(token));
        cleanupExpired();
    }

    /**
     * Invalidates every bearer previously issued to the user across all server instances.
     * New logins read the incremented version and immediately receive a valid new token.
     */
    @Transactional
    public void revokeUser(int userId) {
        database.update("UPDATE users SET auth_version=COALESCE(auth_version,0)+1 WHERE id=?", userId);
        database.update("DELETE FROM auth_session WHERE user_id=?", userId);
    }

    private Optional<UserState> userState(int userId) {
        List<UserState> rows = database.query("""
                        SELECT username,COALESCE(NULLIF(TRIM(role),''),'SALES'),COALESCE(auth_version,0),active,locked
                        FROM users WHERE id=?
                        """,
                (row, index) -> new UserState(row.getString(1), row.getString(2), row.getLong(3),
                        row.getBoolean(4), row.getBoolean(5)), userId);
        return rows.stream().findFirst();
    }

    private Optional<Instant> tokenExpiry(String token) {
        byte[] key = signingKey(false);
        if (key == null) return Optional.empty();
        SignedTokenCodec.DecodeResult decoded = SignedTokenCodec.decode(token, key);
        return decoded.valid() ? Optional.of(decoded.token().expiresAt()) : Optional.empty();
    }

    private void cleanupExpired() {
        database.update("DELETE FROM auth_session WHERE expires_at<=CURRENT_TIMESTAMP");
        database.update("DELETE FROM auth_token_revocation WHERE expires_at<=CURRENT_TIMESTAMP");
    }

    /**
     * The signing key belongs to the company database, not one JVM. This lets a
     * session survive a local Spring restart and lets multiple server instances
     * validate the same bearer when they share PostgreSQL.
     */
    private byte[] signingKey(boolean createIfMissing) {
        byte[] cached = cachedSigningKey;
        if (cached != null) return cached.clone();

        List<String> values = database.query(
                "SELECT secret_base64 FROM auth_signing_key WHERE key_id=1",
                (row, index) -> row.getString(1));
        if (values.isEmpty() && createIfMissing) {
            byte[] generated = new byte[SIGNING_KEY_BYTES];
            random.nextBytes(generated);
            String encoded = Base64.getEncoder().encodeToString(generated);
            database.update("""
                    INSERT INTO auth_signing_key(key_id,secret_base64)
                    VALUES (1,?) ON CONFLICT (key_id) DO NOTHING
                    """, encoded);
            values = database.query("SELECT secret_base64 FROM auth_signing_key WHERE key_id=1",
                    (row, index) -> row.getString(1));
        }
        if (values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) return null;
        byte[] loaded;
        try {
            loaded = Base64.getDecoder().decode(values.getFirst().trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Authentication signing key is invalid", invalid);
        }
        if (loaded.length < 32) throw new IllegalStateException("Authentication signing key is too short");
        cachedSigningKey = loaded.clone();
        return loaded;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {}

    public enum Status {
        AUTHENTICATED("AUTHENTICATED"),
        MISSING("AUTH_TOKEN_MISSING"),
        MALFORMED("AUTH_TOKEN_MALFORMED"),
        INVALID_SIGNATURE("AUTH_TOKEN_INVALID"),
        EXPIRED("AUTH_TOKEN_EXPIRED"),
        REVOKED("AUTH_TOKEN_REVOKED"),
        ACCOUNT_UNAVAILABLE("AUTH_ACCOUNT_UNAVAILABLE"),
        SERVER_KEY_MISSING("AUTH_SERVER_KEY_MISSING");

        private final String code;
        Status(String code) { this.code = code; }
        public String code() { return code; }
    }

    public record AuthenticationResult(Optional<AuthenticatedUser> user, Status status) {
        static AuthenticationResult authenticated(AuthenticatedUser user) {
            return new AuthenticationResult(Optional.of(user), Status.AUTHENTICATED);
        }
        static AuthenticationResult failure(Status status) {
            return new AuthenticationResult(Optional.empty(), status);
        }
        public boolean authenticated() { return user.isPresent(); }
    }

    private record UserState(String username, String role, long authVersion, boolean active, boolean locked) {}
}
