package org.example.server.auth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
final class AuthOtpService {
    enum Purpose { REGISTRATION, PASSWORD_RESET }

    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, String> latestByKey = new ConcurrentHashMap<>();

    synchronized Issued issue(Purpose purpose, String key, String binding, Integer userId,
                              String recipient, SmtpMailService mail) {
        cleanup();
        String lookupKey = purpose + ":" + key;
        String latestId = latestByKey.get(lookupKey);
        Challenge latest = latestId == null ? null : challenges.get(latestId);
        Instant now = Instant.now();
        if (latest != null && latest.issuedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            return new Issued(latestId, false);
        }
        if (latestId != null) challenges.remove(latestId);

        String code = String.format("%06d", random.nextInt(1_000_000));
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String challengeId = randomToken(24);
        if (recipient != null && !recipient.isBlank()) mail.sendOtp(recipient, purposeLabel(purpose), code);
        Challenge challenge = new Challenge(purpose, binding, userId, salt, hash(salt, code), now,
                now.plus(LIFETIME), 0);
        challenges.put(challengeId, challenge);
        latestByKey.put(lookupKey, challengeId);
        return new Issued(challengeId, true);
    }

    synchronized Verified verify(Purpose purpose, String challengeId, String code, String binding) {
        cleanup();
        Challenge challenge = challengeId == null ? null : challenges.get(challengeId);
        if (challenge == null || challenge.purpose() != purpose || !challenge.binding().equals(binding)) {
            throw new IllegalArgumentException("The verification code is invalid or expired");
        }
        if (challenge.attempts() >= MAX_ATTEMPTS || code == null
                || !MessageDigest.isEqual(challenge.hash().getBytes(StandardCharsets.US_ASCII),
                hash(challenge.salt(), code.trim()).getBytes(StandardCharsets.US_ASCII))) {
            int attempts = challenge.attempts() + 1;
            if (attempts >= MAX_ATTEMPTS) challenges.remove(challengeId);
            else challenges.put(challengeId, challenge.withAttempts(attempts));
            throw new IllegalArgumentException("The verification code is invalid or expired");
        }
        challenges.remove(challengeId);
        latestByKey.entrySet().removeIf(entry -> challengeId.equals(entry.getValue()));
        return new Verified(challenge.userId());
    }

    private void cleanup() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        latestByKey.entrySet().removeIf(entry -> !challenges.containsKey(entry.getValue()));
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

    private static String purposeLabel(Purpose purpose) {
        return purpose == Purpose.REGISTRATION ? "registration" : "password reset";
    }

    record Issued(String challengeId, boolean sent) {}
    record Verified(Integer userId) {}
    private record Challenge(Purpose purpose, String binding, Integer userId, byte[] salt, String hash,
                             Instant issuedAt, Instant expiresAt, int attempts) {
        Challenge withAttempts(int value) {
            return new Challenge(purpose, binding, userId, salt, hash, issuedAt, expiresAt, value);
        }
    }
}
