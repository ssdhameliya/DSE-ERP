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
