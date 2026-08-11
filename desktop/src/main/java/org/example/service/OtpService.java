package org.example.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

public final class OtpService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static String code;
    private static Instant expires;
    private static String recipient;
    private static Instant lastSent;

    private OtpService() {
    }

    public static String issue() {
        code = String.format("%06d", RANDOM.nextInt(1_000_000));
        expires = Instant.now().plusSeconds(600);
        return code;
    }

    /** Generates and sends exactly one OTP for a user action. Repeated JavaFX
     * events/double-clicks inside the cooldown reuse the already-sent code. */
    public static synchronized boolean issueAndSend(String email) throws Exception {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("A valid email address is required.");
        Instant now = Instant.now();
        if (normalized.equals(recipient) && lastSent != null && now.isBefore(lastSent.plusSeconds(15))) {
            return false;
        }
        String newCode = issue();
        EmailService.sendOtp(normalized, newCode);
        recipient = normalized;
        lastSent = now;
        return true;
    }

    /** Invalidates any outstanding OTP when the user changes authentication flow. */
    public static synchronized void clear() {
        code = null;
        expires = null;
        recipient = null;
        lastSent = null;
    }

    public static boolean verify(String candidate) {
        boolean valid = code != null && expires != null && Instant.now().isBefore(expires) && code.equals(candidate == null ? "" : candidate.trim());
        if (valid) {
            code = null;
            expires = null;
            recipient = null;
            lastSent = null;
        }
        return valid;
    }
}
