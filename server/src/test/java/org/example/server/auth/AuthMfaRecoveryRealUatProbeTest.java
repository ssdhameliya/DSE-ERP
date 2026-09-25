package org.example.server.auth;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.example.shared.RuntimeContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Opt-in lost-phone recovery probe against a restored UAT PostgreSQL snapshot.
 * SMTP transport is intentionally intercepted so the test never sends a real email;
 * the real database, password/TOTP state machine and AuthService transactions are used.
 */
@EnabledIfSystemProperty(named = "dse.uat.mfa.probe", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthMfaRecoveryRealUatProbeTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("DSE_IT_DB_URL"));
        registry.add("spring.datasource.username", () -> env("DSE_IT_DB_USERNAME", "nobody"));
        registry.add("spring.datasource.password", () -> env("DSE_IT_DB_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("dse.backup.enabled", () -> "false");
    }

    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SmtpMailService mail;

    @Test
    void lostPhoneRecoveryUsesPasswordThenRegisteredEmailThenNewAuthenticator() throws Exception {
        String password = System.getProperty("dse.uat.admin.password", "");
        Assumptions.assumeTrue(!password.isBlank(), "real-UAT admin test password not supplied");
        Path evidence = Path.of(System.getProperty("dse.uat.mfa.evidence", System.getProperty("java.io.tmpdir")))
                .toAbsolutePath().normalize();
        Files.createDirectories(evidence);

        String originalPolicy = jdbc.queryForObject(
                "SELECT setting_value FROM application_setting WHERE setting_key='security.auth.mfa.policy'", String.class);
        try {
            jdbc.update("UPDATE application_setting SET setting_value='ADMIN_CONTROLLED',updated_at=CURRENT_TIMESTAMP WHERE setting_key='security.auth.mfa.policy'");
            cleanAdminMfa();
            jdbc.update("UPDATE users SET mfa_enabled=1 WHERE id=1");

            // Provision and activate one authenticator first so this is genuinely a lost-phone scenario.
            AuthDtos.LoginResponse enrollmentLogin = auth.login(new AuthDtos.LoginRequest("admin", password), "127.0.0.1");
            assertTrue(enrollmentLogin.success());
            assertTrue(enrollmentLogin.mfaRequired());
            assertNull(enrollmentLogin.accessToken());
            AuthDtos.LoginMfaEnrollmentResponse initialSetup = auth.loginMfaEnrollment(
                    new AuthDtos.LoginMfaEnrollmentRequest(enrollmentLogin.challengeId()));
            AuthDtos.LoginResponse initialComplete = auth.completeLoginMfa(
                    new AuthDtos.LoginMfaCompleteRequest(enrollmentLogin.challengeId(), currentTotp(initialSetup.manualSecret())));
            assertNotNull(initialComplete.accessToken());
            String oldEncrypted = jdbc.queryForObject("SELECT totp_secret_enc FROM users WHERE id=1", String.class);
            assertNotNull(oldEncrypted);

            // Password verification creates the normal MFA challenge; no old TOTP is supplied to recovery.
            AuthDtos.LoginResponse lostPhoneLogin = auth.login(new AuthDtos.LoginRequest("admin", password), "127.0.0.1");
            assertTrue(lostPhoneLogin.mfaRequired());
            assertNull(lostPhoneLogin.accessToken());

            AtomicReference<String> emailedCode = new AtomicReference<>();
            doAnswer(invocation -> {
                emailedCode.set(invocation.getArgument(2, String.class));
                return null;
            }).when(mail).sendOtp(anyString(), anyString(), anyString());

            AuthDtos.LoginMfaChallengeResponse recovery = auth.requestLoginMfaRecovery(
                    new AuthDtos.LoginMfaRecoveryRequest(lostPhoneLogin.challengeId()));
            assertTrue(recovery.success());
            assertNotNull(emailedCode.get());
            assertEquals(oldEncrypted, jdbc.queryForObject("SELECT totp_secret_enc FROM users WHERE id=1", String.class),
                    "Requesting email verification must not invalidate the old authenticator yet");

            AuthDtos.LoginMfaEnrollmentResponse replacement = auth.completeLoginMfaRecovery(
                    new AuthDtos.LoginMfaRecoveryCompleteRequest(lostPhoneLogin.challengeId(), recovery.challengeId(), emailedCode.get()));
            assertNotNull(replacement.manualSecret());
            assertTrue(replacement.provisioningUri().startsWith("otpauth://"));
            String newEncrypted = jdbc.queryForObject("SELECT totp_secret_enc FROM users WHERE id=1", String.class);
            assertNotEquals(oldEncrypted, newEncrypted, "Old authenticator secret must be invalidated after email verification");
            Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM auth_totp_enrollment_pending WHERE user_id=1", Long.class);
            assertEquals(1L, pending == null ? 0L : pending.longValue());

            AuthDtos.LoginResponse replacementComplete = auth.completeLoginMfa(
                    new AuthDtos.LoginMfaCompleteRequest(replacement.challengeId(), currentTotp(replacement.manualSecret())));
            assertNotNull(replacementComplete.accessToken());
            pending = jdbc.queryForObject("SELECT COUNT(*) FROM auth_totp_enrollment_pending WHERE user_id=1", Long.class);
            assertEquals(0L, pending == null ? 0L : pending.longValue());

            String report = """
                    DSE ERP %s - RESTORED UAT MFA LOST-PHONE RECOVERY
                    ======================================================
                    Database: dse_erp_uat_repro (restored isolated UAT snapshot)
                    User: existing Admin id=1
                    Email transport: intercepted test transport; no real email sent

                    PASS - Password authentication produced MFA challenge without session token.
                    PASS - Get Help recovery issued a registered-email OTP challenge.
                    PASS - Old authenticator secret remained active until email OTP verification succeeded.
                    PASS - No old authenticator code was supplied to the recovery operation.
                    PASS - After email OTP, old authenticator secret was replaced and enrollment became required.
                    PASS - New QR/manual setup material was returned.
                    PASS - New authenticator 6-digit code completed enrollment and issued a session.
                    PASS - Enrollment-required marker cleared after new authenticator verification.
                    PASS - No password, access token, email OTP, setup secret or QR URI is written to this evidence file.

                    RESULT: PASS
                    """.formatted(RuntimeContract.appVersion());
            Files.writeString(evidence.resolve("02-REAL-UAT-MFA-LOST-PHONE-RECOVERY.txt"), report);
        } finally {
            cleanAdminMfa();
            jdbc.update("UPDATE application_setting SET setting_value=?,updated_at=CURRENT_TIMESTAMP WHERE setting_key='security.auth.mfa.policy'", originalPolicy);
        }
    }

    private void cleanAdminMfa() {
        jdbc.update("UPDATE users SET mfa_enabled=0,totp_secret_enc=NULL,mfa_failed_attempts=0,row_version=row_version+1 WHERE id=1");
        jdbc.update("DELETE FROM auth_totp_enrollment_pending WHERE user_id=1");
        jdbc.update("DELETE FROM auth_totp_login_challenge WHERE user_id=1");
        jdbc.update("DELETE FROM auth_session WHERE user_id=1");
    }

    private static String currentTotp(String secret) throws Exception {
        byte[] key = decode32(secret);
        long counter = Instant.now().getEpochSecond() / 30L;
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
        int offset = h[h.length - 1] & 0x0f;
        int binary = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
        return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
    }

    private static byte[] decode32(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String value = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char ch : value.toCharArray()) {
            int v = alphabet.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("Invalid base32 secret");
            buffer = (buffer << 5) | v; bits += 5;
            if (bits >= 8) { out.write((buffer >> (bits - 8)) & 0xff); bits -= 8; }
        }
        return out.toByteArray();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
    private static String env(String name, String fallback) {
        String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value;
    }
}
