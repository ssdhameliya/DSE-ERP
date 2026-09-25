package org.example.server.auth;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
class AuthOtpService {
    enum Purpose { REGISTRATION, PASSWORD_RESET, LOGIN_MFA, MFA_RECOVERY }

    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final int RESEND_COOLDOWN_SECONDS = 30;
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final JpaNativeRepository db;

    AuthOtpService(JpaNativeRepository db) {
        this.db = db;
    }

    @Transactional
    synchronized Issued issue(Purpose purpose, String key, String binding, Integer userId,
                              String recipient, SmtpMailService mail) {
        cleanup();
        String safeKey = normalizeKey(key);
        List<Map<String,Object>> recent = db.queryForList("""
                SELECT challenge_id
                FROM auth_challenge
                WHERE purpose=? AND challenge_key=? AND expires_at>CURRENT_TIMESTAMP
                  AND issued_at > CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                ORDER BY issued_at DESC
                LIMIT 1
                """, purpose.name(), safeKey, RESEND_COOLDOWN_SECONDS);
        if (!recent.isEmpty()) {
            return new Issued(String.valueOf(recent.getFirst().get("challenge_id")), false);
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String challengeId = randomToken(24);
        String bindingHash = bindingHash(binding);
        if (recipient != null && !recipient.isBlank()) {
            mail.sendOtp(recipient, purposeLabel(purpose), code);
        }

        // A new challenge supersedes all older challenges for the same purpose/key.
        db.update("DELETE FROM auth_challenge WHERE purpose=? AND challenge_key=?", purpose.name(), safeKey);
        db.update("""
                INSERT INTO auth_challenge(challenge_id,purpose,challenge_key,binding_hash,user_id,recipient,
                                           salt_base64,code_hash,issued_at,expires_at,attempts)
                VALUES(?,?,?,?,?,?,?, ?,CURRENT_TIMESTAMP,
                       CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),0)
                """, challengeId, purpose.name(), safeKey, bindingHash, userId, clean(recipient),
                Base64.getEncoder().encodeToString(salt), hash(salt, code), LIFETIME.toSeconds());
        return new Issued(challengeId, true);
    }

    @Transactional
    synchronized Verified verify(Purpose purpose, String challengeId, String code, String binding) {
        return verifyInternal(purpose, challengeId, code, bindingHash(binding), true);
    }

    @Transactional
    synchronized Verified verify(Purpose purpose, String challengeId, String code) {
        cleanup();
        Map<String,Object> row = challengeRow(purpose, challengeId, true);
        return verifyInternal(purpose, challengeId, code, String.valueOf(row.get("binding_hash")), true);
    }

    /** Verify a challenge but keep it alive until a later factor succeeds. */
    @Transactional
    synchronized Verified verifyWithoutConsume(Purpose purpose, String challengeId, String code, String binding) {
        return verifyInternal(purpose, challengeId, code, bindingHash(binding), false);
    }

    @Transactional
    synchronized void consume(Purpose purpose, String challengeId) {
        if (challengeId == null || challengeId.isBlank()) return;
        db.update("DELETE FROM auth_challenge WHERE challenge_id=? AND purpose=?", challengeId, purpose.name());
    }

    @Transactional(readOnly = true)
    synchronized Integer challengeUser(Purpose purpose, String challengeId) {
        Map<String,Object> row = challengeRow(purpose, challengeId, false);
        Object value = row.get("user_id");
        return value instanceof Number number ? number.intValue() : null;
    }

    @Transactional
    synchronized void invalidate(Purpose purpose, String key) {
        cleanup();
        db.update("DELETE FROM auth_challenge WHERE purpose=? AND challenge_key=?", purpose.name(), normalizeKey(key));
    }

    private Verified verifyInternal(Purpose purpose, String challengeId, String code,
                                    String expectedBindingHash, boolean consume) {
        cleanup();
        Map<String,Object> challenge = challengeRow(purpose, challengeId, true);
        String actualBinding = String.valueOf(challenge.get("binding_hash"));
        if (!MessageDigest.isEqual(actualBinding.getBytes(StandardCharsets.US_ASCII),
                expectedBindingHash.getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }

        int attempts = number(challenge.get("attempts"));
        String provided = code == null ? "" : code.trim();
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(String.valueOf(challenge.get("salt_base64")));
        } catch (Exception exception) {
            db.update("DELETE FROM auth_challenge WHERE challenge_id=?", challengeId);
            throw invalid();
        }
        String expectedHash = String.valueOf(challenge.get("code_hash"));
        boolean matches = provided.matches("\\d{6}") && MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                hash(salt, provided).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int next = attempts + 1;
            if (next >= MAX_ATTEMPTS) db.update("DELETE FROM auth_challenge WHERE challenge_id=?", challengeId);
            else db.update("UPDATE auth_challenge SET attempts=? WHERE challenge_id=?", next, challengeId);
            throw invalid();
        }

        Object value = challenge.get("user_id");
        Integer userId = value instanceof Number number ? number.intValue() : null;
        if (consume) db.update("DELETE FROM auth_challenge WHERE challenge_id=?", challengeId);
        return new Verified(userId);
    }

    private Map<String,Object> challengeRow(Purpose purpose, String challengeId, boolean lock) {
        if (challengeId == null || challengeId.isBlank()) throw invalid();
        String sql = """
                SELECT challenge_id,purpose,challenge_key,binding_hash,user_id,salt_base64,code_hash,attempts
                FROM auth_challenge
                WHERE challenge_id=? AND purpose=? AND expires_at>CURRENT_TIMESTAMP
                """ + (lock ? " FOR UPDATE" : "");
        List<Map<String,Object>> rows = db.queryForList(sql, challengeId, purpose.name());
        if (rows.isEmpty()) throw invalid();
        return rows.getFirst();
    }

    private void cleanup() {
        db.update("DELETE FROM auth_challenge WHERE expires_at<=CURRENT_TIMESTAMP");
    }

    private String randomToken(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(byte[] salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("OTP hashing is unavailable", exception);
        }
    }

    private static String bindingHash(String binding) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = binding == null ? "" : binding;
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("OTP binding hashing is unavailable", exception);
        }
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("The verification code is invalid or expired");
    }

    private static String purposeLabel(Purpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> "registration";
            case PASSWORD_RESET -> "password reset";
            case LOGIN_MFA -> "sign-in verification";
            case MFA_RECOVERY -> "authenticator recovery";
        };
    }

    record Issued(String challengeId, boolean sent) {}
    record Verified(Integer userId) {}
}
