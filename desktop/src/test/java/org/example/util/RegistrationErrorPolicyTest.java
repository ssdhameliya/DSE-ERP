package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationErrorPolicyTest {
    @Test
    void smtpAuthenticationFailureDoesNotLeakProviderMessageOrResetCaptcha() {
        var failure = new IllegalStateException("535 5.7.8 Username and Password not accepted. BadCredentials");
        assertEquals("Verification email is temporarily unavailable. Please contact your administrator or try again later.",
                RegistrationErrorPolicy.userMessage(failure));
        assertFalse(RegistrationErrorPolicy.isCaptchaFailure(failure));
    }

    @Test
    void captchaFailureRemainsIdentifiableForRefresh() {
        var failure = new IllegalArgumentException("CAPTCHA verification failed. Refresh it and try again.");
        assertTrue(RegistrationErrorPolicy.isCaptchaFailure(failure));
        assertTrue(RegistrationErrorPolicy.userMessage(failure).contains("CAPTCHA"));
    }
}
