package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.model.AppUser;
import org.example.service.NotificationService;
import org.example.service.OtpService;
import org.example.service.SessionService;
import org.example.service.UserService;
import org.example.theme.ThemeManager;
import org.example.util.ClockService;
import org.example.util.ButtonAction;
import org.example.util.UiActionIcons;
import org.example.util.SceneManager;

public class LoginController {
    @FXML private TextField txtUsername, txtOtp;
    @FXML private PasswordField txtPassword;
    @FXML private ToggleButton btnTheme;
    @FXML private Label lblClock, lblMessage, lblUsernameError, lblPasswordError, lblOtpError;
    @FXML private Button btnLogin, btnRegister, btnEmailSettings;

    private final UserService users = new UserService();
    private AppUser pendingUser;

    @FXML public void initialize() {
        ClockService.start(lblClock);
        txtOtp.setDisable(true);
        UiActionIcons.apply(btnLogin, ButtonAction.LOGIN);
        UiActionIcons.apply(btnRegister, ButtonAction.ADD);
        UiActionIcons.apply(btnEmailSettings, ButtonAction.EMAIL);
        refreshThemeButton();
        installLiveClear(txtUsername, lblUsernameError);
        installLiveClear(txtPassword, lblPasswordError);
        installLiveClear(txtOtp, lblOtpError);
    }

    @FXML private void toggleTheme() {
        ThemeManager.toggle(btnTheme.getScene());
        refreshThemeButton();
    }

    private void refreshThemeButton() {
        boolean dark = ThemeManager.getCurrentTheme() == ThemeManager.Theme.DARK;
        btnTheme.setText(dark ? "Light Mode" : "Dark Mode");
        UiActionIcons.apply(btnTheme, dark ? "sun" : "moon",
                dark ? "Switch to light mode" : "Switch to dark mode");
    }

    @FXML private void login() {
        clearErrors();
        if (pendingUser == null) {
            if (!validateCredentials()) return;
            authenticateUser();
        } else {
            if (txtOtp.getText() == null || txtOtp.getText().trim().isEmpty()) {
                showFieldError(txtOtp, lblOtpError, "Verification code is required.");
                message("Please correct the highlighted field.", true);
                return;
            }
            verifyOtp();
        }
    }

    private boolean validateCredentials() {
        boolean valid = true;
        if (txtUsername.getText() == null || txtUsername.getText().trim().isEmpty()) {
            showFieldError(txtUsername, lblUsernameError, "Email or username is required."); valid = false;
        }
        if (txtPassword.getText() == null || txtPassword.getText().isBlank()) {
            showFieldError(txtPassword, lblPasswordError, "Password is required."); valid = false;
        }
        if (!valid) message("Please correct the highlighted fields.", true);
        return valid;
    }

    private void authenticateUser() {
        String identity = txtUsername.getText().trim();
        String password = txtPassword.getText();
        try { pendingUser = users.authenticate(identity, password); }
        catch (Exception ex) { message("Login failed: " + ex.getMessage(), true); return; }
        if (pendingUser == null) { message("Invalid email/username or password.", true); return; }
        try {
            OtpService.issueAndSend(pendingUser.getEmail());
            txtOtp.setDisable(false); txtOtp.requestFocus();
            message("Verification code sent to " + pendingUser.getEmail() + ".", false);
        } catch (Exception exception) { pendingUser = null; message(exception.getMessage(), true); }
    }

    private void verifyOtp() {
        if (!OtpService.verify(txtOtp.getText().trim())) {
            showFieldError(txtOtp, lblOtpError, "The verification code is invalid or expired.");
            message("The verification code is invalid or expired.", true); return;
        }
        SessionService.signIn(pendingUser);
        NotificationService.add("Signed in successfully.");
        SceneManager.showDashboard();
    }

    @FXML private void register() { SceneManager.showRegistration(); }
    @FXML private void openEmailSettings() { SceneManager.loadEmailSettings(); }

    private void installLiveClear(TextInputControl field, Label error) {
        field.textProperty().addListener((obs, oldValue, newValue) -> { if (newValue != null && !newValue.isBlank()) clearFieldError(field, error); });
    }
    private void clearErrors() { clearFieldError(txtUsername,lblUsernameError); clearFieldError(txtPassword,lblPasswordError); clearFieldError(txtOtp,lblOtpError); }
    private void showFieldError(Control field, Label label, String text) { label.setText(text); label.setManaged(true); label.setVisible(true); if(!field.getStyleClass().contains("invalid-field")) field.getStyleClass().add("invalid-field"); field.requestFocus(); }
    private void clearFieldError(Control field, Label label) { label.setManaged(false); label.setVisible(false); field.getStyleClass().remove("invalid-field"); }
    private void message(String text, boolean error) { lblMessage.setText(text); lblMessage.getStyleClass().removeAll("message-error","message-success"); lblMessage.getStyleClass().add(error ? "message-error" : "message-success"); }
}
