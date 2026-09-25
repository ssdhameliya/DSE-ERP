package org.example.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UserMfaEnrollmentUiContractTest {
    @Test
    void userDialogUsesCentralizedEntityDialogAndShowsRealAuthenticatorLifecycle() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/pages/UserDialog.fxml"));
        assertTrue(fxml.contains("entity-dialog,premium-entity-dialog"));
        assertTrue(fxml.contains("entity-dialog-header,premium-dialog-header"));
        assertTrue(fxml.contains("entity-dialog-actions,premium-dialog-actions"));
        assertTrue(fxml.contains("fx:id=\"lblMfaPolicy\""));
        assertTrue(fxml.contains("fx:id=\"lblMfaStatus\""));
        assertTrue(fxml.contains("fx:id=\"btnResetMfa\""));
        assertTrue(fxml.contains("Reset invalidates the old phone and requires a new QR enrollment"));
        assertFalse(fxml.contains("user-dialog-brand"), "The obsolete decorative left rail must not return");
        assertFalse(fxml.contains("Admin: password only"), "MFA wording must follow the live server policy");
    }

    @Test
    void nextLoginEnrollmentUsesQrManualKeyAndAsyncVerification() throws Exception {
        String login = Files.readString(Path.of("src/main/java/org/example/controller/LoginController.java"));
        assertTrue(login.contains("QrCodeImageFactory.create(setup.provisioningUri(), 240)"));
        assertTrue(login.contains("Manual setup key"));
        assertTrue(login.contains("Google or Microsoft Authenticator"));
        assertTrue(login.contains("UiTaskExecutor.submitAction(\"login-mfa-enrollment-verification\""));
        assertTrue(login.contains("users.completeLoginMfa(challengeId, otp)"));
    }


    @Test
    void resetRecoveryKeepsQrHelpReachableAndUserDialogRefreshesRowVersion() throws Exception {
        String loginFxml = Files.readString(Path.of("src/main/resources/fxml/pages/Login.fxml"));
        String login = Files.readString(Path.of("src/main/java/org/example/controller/LoginController.java"));
        String dialog = Files.readString(Path.of("src/main/java/org/example/controller/UserDialogController.java"));
        String access = Files.readString(Path.of("src/main/java/org/example/controller/UserAccessController.java"));
        assertTrue(loginFxml.contains("fx:id=\"btnResendOtp\" text=\"Get Help\""));
        assertTrue(login.contains("pendingEnrollmentSetup"));
        assertTrue(login.contains("Click Get Help to reopen the QR code and setup key"));
        assertTrue(login.contains("showAuthenticatorEnrollment(pendingEnrollmentSetup)"));
        assertTrue(login.contains("if (btnResendOtp != null) btnResendOtp.setDisable(false)"),
                "Get Help must remain enabled whenever the login form is not busy");
        assertTrue(dialog.contains("AdminApiClient.UserDto refreshed = resettingCurrentUser ? null : api.user(editingUserId)"));
        assertTrue(dialog.contains("editingRowVersion = result.user().rowVersion()"));
        assertTrue(dialog.contains("resettingCurrentUser ? null : api.user(editingUserId)"),
                "Self-reset revokes the current token and must not attempt an authenticated refresh afterward");
        assertFalse(access.contains("if(!row.mfaEnabled){warning(\"Authenticator is not required for this user.\")"),
                "Desktop must not override the server's effective MFA policy with a stale stored flag");
        assertFalse(access.contains("resetMfaButton.setDisable(!row.mfaEnabled)"));
    }

    @Test
    void getHelpProvidesLostPhoneEmailRecoveryAndNewQrEnrollment() throws Exception {
        String login = Files.readString(Path.of("src/main/java/org/example/controller/LoginController.java"));
        String api = Files.readString(Path.of("src/main/java/org/example/api/auth/AuthApiClient.java"));
        assertTrue(login.contains("Lost / Changed Phone"));
        assertTrue(login.contains("Verify your registered email"));
        assertTrue(login.contains("users.requestLoginMfaRecovery(loginChallengeId)"));
        assertTrue(login.contains("users.completeLoginMfaRecovery(loginChallengeId, challenge.challengeId(), otp)"));
        assertTrue(login.contains("pendingEnrollmentSetup = setup"));
        assertTrue(login.contains("showAuthenticatorEnrollment(setup)"));
        assertTrue(api.contains("/api/auth/login/mfa/recovery/request"));
        assertTrue(api.contains("/api/auth/login/mfa/recovery/complete"));
    }

    @Test
    void userSaveErrorsStayInFixedDialogFooterInsteadOfScrollableBottom() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/pages/UserDialog.fxml"));
        int centerEnd = fxml.indexOf("</center>");
        int bottom = fxml.indexOf("<bottom>");
        int message = fxml.indexOf("fx:id=\"lblMessage\"");
        assertTrue(centerEnd >= 0 && bottom > centerEnd && message > bottom,
                "Save/update errors must render in the fixed footer, outside the ScrollPane");
        assertTrue(fxml.substring(bottom).contains("maxWidth=\"Infinity\""));
        assertTrue(fxml.contains("styleClass=\"validation-message,dialog-status-message\""));
        String controller = Files.readString(Path.of("src/main/java/org/example/controller/UserDialogController.java"));
        assertTrue(controller.contains("setAll(\"validation-message\", \"dialog-status-message\""),
                "Runtime feedback must preserve centralized validation styling instead of falling back to black text");
        assertFalse(controller.contains("setAll(error ? \"dialog-error\" : \"dialog-success\")"));
        for (String theme : java.util.List.of("light-theme.css", "dark-theme.css")) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains(".dialog-status-message.dialog-status-error"));
            assertTrue(css.contains(".dialog-status-message.dialog-status-success"));
        }
    }

    @Test
    void sharedClientDoesNotExposeLegacyPreLoginEmailConfiguration() throws Exception {
        String login = Files.readString(Path.of("src/main/java/org/example/controller/LoginController.java"));
        assertTrue(login.contains("ConfigManager.isSharedClient() && btnEmailSettings != null"));
        assertTrue(login.contains("btnEmailSettings.setVisible(false)"));
        assertTrue(login.contains("btnEmailSettings.setManaged(false)"));
        assertTrue(login.contains("Company email settings are managed after Admin sign-in under Settings → Email."));
    }

    @Test
    void authenticatorResetDoesNotBlockJavaFxThread() throws Exception {
        String access = Files.readString(Path.of("src/main/java/org/example/controller/UserAccessController.java"));
        assertTrue(access.contains("UiTaskExecutor.submitAction(\"user-access-mfa-reset\""));
        assertFalse(access.contains("try{var state=adminApi.resetMfa(row.id)"));
    }
}
