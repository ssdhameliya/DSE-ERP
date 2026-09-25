package org.example.server.auth;

import org.example.server.master.RoleMasterService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.persistence.entity.UserEntity;
import org.example.server.persistence.repository.UserRepository;
import org.example.server.security.AuthenticatedUser;
import org.example.server.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthMfaEnrollmentScenarioTest {
    @Test
    void adminControlledMfaOffToOnForcesQrEnrollmentThenActivatesAfterValidCode() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthOtpService otp = mock(AuthOtpService.class);
        SmtpMailService mail = mock(SmtpMailService.class);
        TotpService totp = mock(TotpService.class);
        RegistrationCaptchaService captcha = mock(RegistrationCaptchaService.class);
        ScenarioDb db = new ScenarioDb();
        AuthService service = new AuthService(users, roles, passwords, tokens, otp, mail, db, totp, captcha);

        UserEntity admin = user(7, "admin", "ADMIN", true);
        admin.setMfaEnabled(true); // the Admin has just ticked Require MFA in User Access
        when(users.findForAuthentication("admin")).thenReturn(Optional.of(admin));
        when(users.findByIdForAuthentication(7)).thenReturn(Optional.of(admin));
        when(passwords.matches("Password1", admin.getPassword())).thenReturn(true);
        when(roles.isActive("ADMIN")).thenReturn(true);
        TotpService.Setup setup = new TotpService.Setup("BASE32SECRET", "ENC-SECRET", "otpauth://totp/DSE%20ERP%3Aadmin?secret=BASE32SECRET");
        when(totp.createSetup("admin", "")).thenReturn(setup);
        when(totp.issueLogin(7)).thenReturn("challenge-1");
        when(totp.peekLogin("challenge-1")).thenReturn(7);
        when(totp.existingSetup("admin", "", "ENC-SECRET")).thenReturn(setup);
        when(totp.verifyEncrypted("ENC-SECRET", "111111")).thenReturn(false);
        when(totp.verifyEncrypted("ENC-SECRET", "654321")).thenReturn(true);
        when(totp.consumeLogin("challenge-1")).thenReturn(7);
        when(tokens.issue(any(AuthenticatedUser.class))).thenReturn(new TokenService.IssuedToken("access-token", Instant.now().plusSeconds(3600)));

        AuthDtos.LoginResponse first = service.login(new AuthDtos.LoginRequest("admin", "Password1"), "127.0.0.1");
        assertTrue(first.success());
        assertTrue(first.mfaRequired());
        assertEquals("Authenticator enrollment", first.maskedDestination());
        assertEquals("challenge-1", first.challengeId());
        assertNull(first.accessToken(), "Password verification alone must not create an authenticated session");
        assertTrue(db.pending);
        assertEquals("ENC-SECRET", admin.getTotpSecretEnc());

        AuthDtos.LoginMfaEnrollmentResponse enrollment = service.loginMfaEnrollment(new AuthDtos.LoginMfaEnrollmentRequest("challenge-1"));
        assertEquals("BASE32SECRET", enrollment.manualSecret());
        assertTrue(enrollment.provisioningUri().startsWith("otpauth://"));

        AuthDtos.LoginResponse wrong = service.completeLoginMfa(new AuthDtos.LoginMfaCompleteRequest("challenge-1", "111111"));
        assertFalse(wrong.success());
        assertTrue(db.pending, "A wrong authenticator code must not activate enrollment");

        AuthDtos.LoginResponse correct = service.completeLoginMfa(new AuthDtos.LoginMfaCompleteRequest("challenge-1", "654321"));
        assertTrue(correct.success());
        assertFalse(correct.mfaRequired());
        assertEquals("access-token", correct.accessToken());
        assertFalse(db.pending, "Successful authenticator verification must mark enrollment active");
        verify(tokens).issue(argThat(u -> u.id() == 7 && "ADMIN".equals(u.role())));
    }

    @Test
    void adminControlledMfaCanEnrollWithoutEmailBecauseQrIsShownAfterPasswordVerification() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthOtpService otp = mock(AuthOtpService.class);
        SmtpMailService mail = mock(SmtpMailService.class);
        TotpService totp = mock(TotpService.class);
        RegistrationCaptchaService captcha = mock(RegistrationCaptchaService.class);
        ScenarioDb db = new ScenarioDb();
        AuthService service = new AuthService(users, roles, passwords, tokens, otp, mail, db, totp, captcha);
        UserEntity admin = user(8, "admin2", "ADMIN", true);
        admin.setMfaEnabled(true);
        when(users.findForAuthentication("admin2")).thenReturn(Optional.of(admin));
        when(passwords.matches("Password1", admin.getPassword())).thenReturn(true);
        when(roles.isActive("ADMIN")).thenReturn(true);
        when(totp.createSetup("admin2", "")).thenReturn(new TotpService.Setup("S", "E", "otpauth://test"));
        when(totp.issueLogin(8)).thenReturn("challenge-2");

        AuthDtos.LoginResponse first = service.login(new AuthDtos.LoginRequest("admin2", "Password1"), "127.0.0.1");
        assertTrue(first.success());
        assertTrue(first.mfaRequired());
        assertEquals("Authenticator enrollment", first.maskedDestination());
        verifyNoInteractions(mail);
    }


    @Test
    void lostPhoneEnrollmentPendingAllowsPasswordResetWithoutOldAuthenticatorCode() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthOtpService otp = mock(AuthOtpService.class);
        SmtpMailService mail = mock(SmtpMailService.class);
        TotpService totp = mock(TotpService.class);
        RegistrationCaptchaService captcha = mock(RegistrationCaptchaService.class);
        ScenarioDb db = new ScenarioDb(); db.pending = true;
        AuthService service = new AuthService(users, roles, passwords, tokens, otp, mail, db, totp, captcha);
        UserEntity user = user(9, "recovery", "ADMIN", true);
        user.setMfaEnabled(true);
        user.setTotpSecretEnc(null);
        when(users.findById(9)).thenReturn(Optional.of(user));
        when(otp.verifyWithoutConsume(AuthOtpService.Purpose.PASSWORD_RESET,"reset-1","123456","")).thenReturn(new AuthOtpService.Verified(9));
        when(passwords.encode("NewPassword1")).thenReturn("ENC-NEW");

        AuthDtos.OperationResponse response = service.completePasswordReset(
                new AuthDtos.PasswordResetCompleteRequest("reset-1","123456","","NewPassword1"));

        assertTrue(response.success());
        assertEquals("ENC-NEW",user.getPassword());
        verify(totp, never()).verifyEncrypted(anyString(), anyString());
        verify(otp).consume(AuthOtpService.Purpose.PASSWORD_RESET,"reset-1");
        verify(tokens).revokeUser(9);
    }

    @Test
    void lostPhoneSelfServiceUsesPasswordChallengeThenRegisteredEmailOtpBeforeReplacingSecret() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthOtpService otp = mock(AuthOtpService.class);
        SmtpMailService mail = mock(SmtpMailService.class);
        TotpService totp = mock(TotpService.class);
        RegistrationCaptchaService captcha = mock(RegistrationCaptchaService.class);
        ScenarioDb db = new ScenarioDb();
        AuthService service = new AuthService(users, roles, passwords, tokens, otp, mail, db, totp, captcha);

        UserEntity user = user(10, "lostphone", "ADMIN", true);
        user.setEmail("lost@example.com");
        user.setMfaEnabled(true);
        user.setTotpSecretEnc("ENC-OLD");
        when(users.findByIdForAuthentication(10)).thenReturn(Optional.of(user));
        when(roles.isActive("ADMIN")).thenReturn(true);
        when(totp.peekLogin("login-old")).thenReturn(10);
        when(otp.issue(eq(AuthOtpService.Purpose.MFA_RECOVERY), eq("user:10"), anyString(), eq(10),
                eq("lost@example.com"), eq(mail))).thenReturn(new AuthOtpService.Issued("recovery-1", true));
        when(otp.verify(eq(AuthOtpService.Purpose.MFA_RECOVERY), eq("recovery-1"), eq("246810"), anyString()))
                .thenReturn(new AuthOtpService.Verified(10));
        TotpService.Setup replacement = new TotpService.Setup("NEWSECRET", "ENC-NEW", "otpauth://new");
        when(totp.createSetup("lostphone", "lost@example.com")).thenReturn(replacement);
        when(totp.issueLogin(10)).thenReturn("login-new");

        AuthDtos.LoginMfaChallengeResponse requested =
                service.requestLoginMfaRecovery(new AuthDtos.LoginMfaRecoveryRequest("login-old"));
        assertTrue(requested.success());
        assertEquals("recovery-1", requested.challengeId());
        assertEquals("l***@example.com", requested.maskedDestination());
        assertEquals("ENC-OLD", user.getTotpSecretEnc(), "The old phone must remain valid until email verification succeeds");

        AuthDtos.LoginMfaEnrollmentResponse completed = service.completeLoginMfaRecovery(
                new AuthDtos.LoginMfaRecoveryCompleteRequest("login-old", "recovery-1", "246810"));
        assertEquals("login-new", completed.challengeId());
        assertEquals("NEWSECRET", completed.manualSecret());
        assertEquals("ENC-NEW", user.getTotpSecretEnc());
        assertTrue(db.pending, "Successful recovery must require verification of the new authenticator");
        verify(totp).invalidateUser(10);
        verify(tokens).revokeUser(10);
        verify(totp, never()).verifyEncrypted(eq("ENC-OLD"), anyString());
    }

    @Test
    void lostPhoneSelfServiceRefusesResetWhenRegisteredEmailIsMissing() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthOtpService otp = mock(AuthOtpService.class);
        SmtpMailService mail = mock(SmtpMailService.class);
        TotpService totp = mock(TotpService.class);
        RegistrationCaptchaService captcha = mock(RegistrationCaptchaService.class);
        ScenarioDb db = new ScenarioDb();
        AuthService service = new AuthService(users, roles, passwords, tokens, otp, mail, db, totp, captcha);
        UserEntity user = user(11, "noemail", "ADMIN", true);
        user.setMfaEnabled(true);
        user.setTotpSecretEnc("ENC-OLD");
        when(users.findByIdForAuthentication(11)).thenReturn(Optional.of(user));
        when(roles.isActive("ADMIN")).thenReturn(true);
        when(totp.peekLogin("login-no-email")).thenReturn(11);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.requestLoginMfaRecovery(new AuthDtos.LoginMfaRecoveryRequest("login-no-email")));
        assertTrue(failure.getMessage().contains("No registered email"));
        verifyNoInteractions(mail);
        assertEquals("ENC-OLD", user.getTotpSecretEnc());
    }

    private static UserEntity user(int id, String username, String role, boolean active) throws Exception {
        UserEntity user = new UserEntity();
        Field idField = UserEntity.class.getDeclaredField("id");
        idField.setAccessible(true); idField.set(user, id);
        user.setUsername(username);
        user.setPassword("$2a$12$test-bcrypt-placeholder");
        user.setFullName(username);
        user.setEmail("");
        user.setRole(role);
        user.setActive(active);
        user.setLocked(false);
        user.setApprovalStatus("APPROVED");
        user.setAccessLevel("STANDARD");
        return user;
    }

    private static final class ScenarioDb extends JpaNativeRepository {
        boolean pending;
        @Override public int update(String sql, Object... args) {
            String normalized = sql.replaceAll("\\s+", " ");
            if (normalized.contains("INSERT INTO auth_totp_enrollment_pending")) pending = true;
            if (normalized.contains("DELETE FROM auth_totp_enrollment_pending")) pending = false;
            return 1;
        }
        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            if (sql.contains("security.auth.mfa.policy")) return type.cast("ADMIN_CONTROLLED");
            if (sql.contains("auth_totp_enrollment_pending")) return type.cast(Long.valueOf(pending ? 1 : 0));
            if (sql.contains("login_throttle")) return type.cast(Long.valueOf(0));
            throw new AssertionError("Unexpected query: " + sql);
        }
    }
}
