package org.example.server.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PreAuthMfaSecurityContractTest {
    @Test
    void passwordAuthenticatedMfaEnrollmentAndRecoveryEndpointsRemainPublic() throws Exception {
        String source = Files.readString(resolveSecurityConfig());
        assertTrue(source.contains("\"/api/auth/login/mfa/enrollment\""));
        assertTrue(source.contains("\"/api/auth/login/mfa/complete\""));
        assertTrue(source.contains("\"/api/auth/login/mfa/resend\""));
        assertTrue(source.contains("\"/api/auth/login/mfa/recovery/request\""));
        assertTrue(source.contains("\"/api/auth/login/mfa/recovery/complete\""));
        assertTrue(source.contains(".permitAll()"), "pre-auth MFA endpoints must remain before the permitAll boundary");
    }

    private static Path resolveSecurityConfig() {
        Path direct = Path.of("src/main/java/org/example/server/security/SecurityConfig.java");
        if (Files.exists(direct)) {
            return direct;
        }
        Path reactor = Path.of("server/src/main/java/org/example/server/security/SecurityConfig.java");
        if (Files.exists(reactor)) {
            return reactor;
        }
        throw new IllegalStateException("SecurityConfig.java not found from test working directory");
    }
    @Test
    void authenticatorEnrollmentMayUseTheAuthenticationRowLock() throws Exception {
        Path authService = resolveAuthService();
        String source = Files.readString(authService);
        int method = source.indexOf("public AuthDtos.LoginMfaEnrollmentResponse loginMfaEnrollment");
        assertTrue(method > 0, "loginMfaEnrollment method must exist");
        String prefix = source.substring(Math.max(0, method - 120), method);
        assertTrue(prefix.contains("@Transactional"), "enrollment must run in a transaction");
        assertTrue(!prefix.contains("readOnly = true"),
                "enrollment loads the authentication row with a lock, so PostgreSQL cannot run it in a read-only transaction");
    }

    private static Path resolveAuthService() {
        Path direct = Path.of("src/main/java/org/example/server/auth/AuthService.java");
        if (Files.exists(direct)) return direct;
        Path reactor = Path.of("server/src/main/java/org/example/server/auth/AuthService.java");
        if (Files.exists(reactor)) return reactor;
        throw new IllegalStateException("AuthService.java not found from test working directory");
    }

}
