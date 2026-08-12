package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.example.model.AppUser;
import org.example.service.NotificationService;
import org.example.service.OtpService;
import org.example.service.SessionService;
import org.example.service.UserService;
import org.example.theme.ThemeManager;
import org.example.util.ButtonAction;
import org.example.util.ClockService;
import org.example.util.SceneManager;
import org.example.util.UiActionIcons;
import org.example.util.UiTaskExecutor;
import org.example.util.PerformanceMonitor;
import org.example.util.PerformanceBudgets;
import org.example.update.BuildInfo;

import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Role-aware authentication for DSE ERP.
 *
 * <p>ADMIN users sign in with credentials only. MANAGER and SALES users
 * require an email OTP after their credentials and selected role are verified.
 * "Remember Me" stores only the identity and selected role, never the password.
 * Password resets always require an email OTP, including for administrators.</p>
 */
public class LoginController {
    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginController.class);
    private static final String PREF_REMEMBER = "login.remember";
    private static final String PREF_IDENTITY = "login.identity";
    private static final String PREF_ROLE = "login.role";

    @FXML private TextField txtUsername, txtOtp, txtResetIdentity, txtResetOtp;
    @FXML private PasswordField txtPassword, txtNewPassword, txtConfirmPassword;
    @FXML private ComboBox<String> cmbRole;
    @FXML private CheckBox chkRemember;
    @FXML private ToggleButton btnTheme;
    @FXML private Label lblClock, lblMessage, lblUsernameError, lblPasswordError, lblRoleError, lblOtpError, lblVersion;
    @FXML private Label lblResetIdentityError, lblResetOtpError, lblNewPasswordError, lblConfirmPasswordError;
    @FXML private Button btnLogin, btnRegister, btnEmailSettings, btnForgotPassword;
    @FXML private Button btnSendResetOtp, btnResetPassword, btnBackToLogin;
    @FXML private VBox loginPanel, resetPanel, otpPanel;

    private final UserService users = new UserService();
    private AppUser pendingUser;
    private AppUser resetUser;

    @FXML public void initialize() {
        if (lblVersion != null) lblVersion.setText("Version " + BuildInfo.version());
        ClockService.start(lblClock);

        cmbRole.getItems().setAll("Admin", "Manager", "Sales");

        // Render the selected role explicitly in the closed ComboBox.
        // Some JavaFX skins do not repaint the button cell reliably after a
        // popup selection.  Binding the button-cell text directly to the
        // ComboBox value avoids that skin issue completely.
        ListCell<String> roleButtonCell = new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
            }
        };
        roleButtonCell.getStyleClass().add("auth-role-selected-cell");
        roleButtonCell.setText("Select your role");
        cmbRole.setButtonCell(roleButtonCell);

        // Keep the visible button-cell text synchronized with the selected
        // value rather than relying on the skin to copy the popup item text.
        cmbRole.valueProperty().addListener((obs, oldRole, newRole) -> {
            roleButtonCell.setText(
                    newRole == null || newRole.isBlank() ? "Select your role" : newRole
            );
            roleButtonCell.requestLayout();
        });

        restoreRememberedLogin();

        txtOtp.setDisable(true);
        UiActionIcons.apply(btnLogin, ButtonAction.LOGIN);
        UiActionIcons.apply(btnRegister, ButtonAction.ADD);
        UiActionIcons.apply(btnEmailSettings, ButtonAction.EMAIL);
        UiActionIcons.apply(btnForgotPassword, "reset", "Reset forgotten password");
        UiActionIcons.apply(btnSendResetOtp, ButtonAction.EMAIL);
        UiActionIcons.apply(btnResetPassword, "save", "Save new password");
        UiActionIcons.apply(btnBackToLogin, ButtonAction.CANCEL);

        refreshThemeButton();
        updateLoginMode();
        showLoginPanel();

        installLiveClear(txtUsername, lblUsernameError);
        installLiveClear(txtPassword, lblPasswordError);
        installLiveClear(txtOtp, lblOtpError);
        installLiveClear(txtResetIdentity, lblResetIdentityError);
        installLiveClear(txtResetOtp, lblResetOtpError);
        installLiveClear(txtNewPassword, lblNewPasswordError);
        installLiveClear(txtConfirmPassword, lblConfirmPasswordError);

        txtUsername.textProperty().addListener((obs, oldValue, newValue) -> resetPendingLogin());
        txtPassword.textProperty().addListener((obs, oldValue, newValue) -> resetPendingLogin());
        cmbRole.valueProperty().addListener((obs, oldValue, newValue) -> {
            clearFieldError(cmbRole, lblRoleError);
            resetPendingLogin();
            updateLoginMode();
        });
        chkRemember.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (!selected) clearRememberedLogin();
        });
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

        if (pendingUser != null) {
            verifyLoginOtp();
            return;
        }

        if (!validateCredentials()) return;

        String identity = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String selectedRole = selectedDatabaseRole();

        setLoginBusy(true, "SIGNING IN...");
        PerformanceMonitor.start("login-click");
        UiTaskExecutor.submitLatest("login-authentication", () -> {
            AppUser user = users.authenticate(identity, password);
            if (user == null) return new LoginAttempt(null, false);
            String actualRole = normalizeRole(user.getRole());
            boolean sent = "ADMIN".equals(actualRole)
                    || !actualRole.equals(selectedRole)
                    || OtpService.issueAndSend(user.getEmail());
            return new LoginAttempt(user, sent);
        }, attempt -> {
            setLoginBusy(false, null);
            AppUser user = attempt.user();
            if (user == null) {
                PerformanceMonitor.finish("login-click");
                message("Invalid email/username or password.", true);
                return;
            }

            String actualRole = normalizeRole(user.getRole());
            if (!actualRole.equals(selectedRole)) {
                showFieldError(cmbRole, lblRoleError,
                        "Selected role does not match this user account.");
                message("Please select the role assigned to this account.", true);
                PerformanceMonitor.finish("login-click");
                return;
            }

            if ("ADMIN".equals(actualRole)) {
                completeLogin(user);
                return;
            }

            pendingUser = user;
            txtOtp.setDisable(false);
            txtOtp.requestFocus();
            btnLogin.setText("VERIFY OTP");
            message(attempt.otpSent()
                    ? "Verification code sent to " + user.getEmail() + "."
                    : "A verification code was already sent. Please enter it below.", false);
            PerformanceMonitor.finish("login-click");
        }, exception -> {
            setLoginBusy(false, null);
            PerformanceMonitor.finish("login-click");
            pendingUser = null;
            OtpService.clear();
            updateLoginMode();
            message("Login failed: " + exception.getMessage(), true);
        });
    }

    private boolean validateCredentials() {
        boolean valid = true;

        if (txtUsername.getText() == null || txtUsername.getText().trim().isEmpty()) {
            showFieldError(txtUsername, lblUsernameError, "Email or username is required.");
            valid = false;
        }
        if (txtPassword.getText() == null || txtPassword.getText().isBlank()) {
            showFieldError(txtPassword, lblPasswordError, "Password is required.");
            valid = false;
        }
        if (cmbRole.getValue() == null || cmbRole.getValue().isBlank()) {
            showFieldError(cmbRole, lblRoleError, "Role is required.");
            valid = false;
        }

        if (!valid) message("Please correct the highlighted fields.", true);
        return valid;
    }

    private void verifyLoginOtp() {
        if (txtOtp.getText() == null || txtOtp.getText().trim().isEmpty()) {
            showFieldError(txtOtp, lblOtpError, "Verification code is required.");
            message("Please enter the verification code.", true);
            return;
        }

        if (!OtpService.verify(txtOtp.getText().trim())) {
            showFieldError(txtOtp, lblOtpError, "The verification code is invalid or expired.");
            message("The verification code is invalid or expired.", true);
            return;
        }

        AppUser authenticated = pendingUser;
        pendingUser = null;
        PerformanceMonitor.start("login-click");
        completeLogin(authenticated);
    }

    private void completeLogin(AppUser user) {
        setLoginBusy(true, "OPENING ERP...");
        UiTaskExecutor.submitLatest("login-complete", () -> {
            users.recordSuccessfulLogin(user.getId());
            NotificationService.add("Signed in successfully.");
            return user;
        }, authenticated -> {
            saveRememberedLogin();
            SessionService.signIn(authenticated);
            SceneManager.showDashboard();
            setLoginBusy(false, null);
            long elapsed = PerformanceMonitor.finish("login-click");
            if (elapsed >= 0) PerformanceBudgets.record("login", elapsed, PerformanceBudgets.LOGIN_MS);
        }, failure -> {
            setLoginBusy(false, null);
            PerformanceMonitor.finish("login-click");
            message("Login failed: " + failure.getMessage(), true);
        });
    }

    private void setLoginBusy(boolean busy, String text) {
        btnLogin.setDisable(busy);
        if (busy && text != null) btnLogin.setText(text);
        else updateLoginMode();
    }

    private record LoginAttempt(AppUser user, boolean otpSent) { }

    private void resetPendingLogin() {
        if (pendingUser == null) return;
        pendingUser = null;
        OtpService.clear();
        txtOtp.clear();
        txtOtp.setDisable(true);
        updateLoginMode();
    }

    private void updateLoginMode() {
        String role = selectedDatabaseRole();
        boolean otpRole = "MANAGER".equals(role) || "SALES".equals(role);

        // ADMIN never needs a login OTP, so keep the OTP area completely out
        // of the layout. Manager/Sales reveal the area immediately when the
        // role is selected; the field becomes editable only after an OTP has
        // actually been issued.
        if (otpPanel != null) {
            otpPanel.setManaged(otpRole);
            otpPanel.setVisible(otpRole);
        }

        if (!otpRole) {
            pendingUser = null;
            OtpService.clear();
            txtOtp.clear();
            txtOtp.setDisable(true);
            btnLogin.setText("LOGIN");
            return;
        }

        if (pendingUser == null) {
            txtOtp.clear();
            txtOtp.setDisable(true);
            btnLogin.setText("LOGIN / SEND OTP");
        } else {
            txtOtp.setDisable(false);
            btnLogin.setText("VERIFY OTP");
        }
    }

    @FXML private void forgotPassword() {
        resetPendingLogin();
        OtpService.clear();
        clearResetErrors();
        resetUser = null;

        String identity = txtUsername.getText() == null ? "" : txtUsername.getText().trim();
        txtResetIdentity.setText(identity);
        txtResetOtp.clear();
        txtNewPassword.clear();
        txtConfirmPassword.clear();

        loginPanel.setManaged(false);
        loginPanel.setVisible(false);
        resetPanel.setManaged(true);
        resetPanel.setVisible(true);
        lblMessage.setText("");
        txtResetIdentity.requestFocus();
    }

    @FXML private void sendResetOtp() {
        clearResetErrors();
        String identity = txtResetIdentity.getText() == null ? "" : txtResetIdentity.getText().trim();
        if (identity.isBlank()) {
            showFieldError(txtResetIdentity, lblResetIdentityError, "Email or username is required.");
            return;
        }

        try {
            AppUser user = users.findActiveByIdentity(identity);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                // Keep the wording deliberately neutral so the reset screen does
                // not disclose whether an arbitrary account exists.
                message("If the account is eligible, a reset code will be sent to its registered email.", false);
                return;
            }

            resetUser = user;
            boolean sent = OtpService.issueAndSend(user.getEmail());
            message(sent
                    ? "Password reset code sent to " + maskedEmail(user.getEmail()) + "."
                    : "A reset code was already sent. Please check your email.", false);
            txtResetOtp.requestFocus();
        } catch (Exception exception) {
            resetUser = null;
            OtpService.clear();
            message("Unable to send reset code: " + exception.getMessage(), true);
        }
    }

    @FXML private void resetPassword() {
        clearResetErrors();

        if (resetUser == null) {
            message("Send a password reset code first.", true);
            txtResetIdentity.requestFocus();
            return;
        }

        boolean valid = true;
        String otp = txtResetOtp.getText() == null ? "" : txtResetOtp.getText().trim();
        String password = txtNewPassword.getText() == null ? "" : txtNewPassword.getText();
        String confirm = txtConfirmPassword.getText() == null ? "" : txtConfirmPassword.getText();

        if (otp.isBlank()) {
            showFieldError(txtResetOtp, lblResetOtpError, "Reset code is required.");
            valid = false;
        }
        if (password.length() < 6) {
            showFieldError(txtNewPassword, lblNewPasswordError,
                    "New password must contain at least 6 characters.");
            valid = false;
        }
        if (!password.equals(confirm)) {
            showFieldError(txtConfirmPassword, lblConfirmPasswordError,
                    "Passwords do not match.");
            valid = false;
        }
        if (!valid) return;

        if (!OtpService.verify(otp)) {
            showFieldError(txtResetOtp, lblResetOtpError, "The reset code is invalid or expired.");
            message("The reset code is invalid or expired.", true);
            return;
        }

        try {
            users.changePassword(resetUser.getId(), password);
            String identity = txtResetIdentity.getText().trim();
            resetUser = null;
            showLoginPanel();
            txtUsername.setText(identity);
            txtPassword.clear();
            message("Password updated successfully. Sign in with your new password.", false);
            txtPassword.requestFocus();
        } catch (Exception exception) {
            message("Unable to update password: " + exception.getMessage(), true);
        }
    }

    @FXML private void backToLogin() {
        resetUser = null;
        OtpService.clear();
        showLoginPanel();
        lblMessage.setText("");
    }

    private void showLoginPanel() {
        if (loginPanel != null) {
            loginPanel.setManaged(true);
            loginPanel.setVisible(true);
        }
        if (resetPanel != null) {
            resetPanel.setManaged(false);
            resetPanel.setVisible(false);
        }
    }

    private String selectedDatabaseRole() {
        String display = cmbRole == null ? null : cmbRole.getValue();
        if (display == null) return "";
        return normalizeRole(display);
    }

    private String normalizeRole(String role) {
        if (role == null) return "";
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        // Backward compatibility for databases created before the SALES role
        // migration in 3.0.10.
        if ("USER".equals(normalized)) return "SALES";
        return normalized;
    }

    private void restoreRememberedLogin() {
        boolean remember = PREFS.getBoolean(PREF_REMEMBER, false);
        chkRemember.setSelected(remember);
        if (!remember) {
            if (cmbRole.getValue() == null) cmbRole.setValue("Admin");
            return;
        }

        txtUsername.setText(PREFS.get(PREF_IDENTITY, ""));
        String savedRole = PREFS.get(PREF_ROLE, "Admin");
        if (!cmbRole.getItems().contains(savedRole)) savedRole = "Admin";
        cmbRole.setValue(savedRole);
    }

    private void saveRememberedLogin() {
        if (!chkRemember.isSelected()) {
            clearRememberedLogin();
            return;
        }
        PREFS.putBoolean(PREF_REMEMBER, true);
        PREFS.put(PREF_IDENTITY, txtUsername.getText().trim());
        PREFS.put(PREF_ROLE, cmbRole.getValue());
    }

    private void clearRememberedLogin() {
        PREFS.remove(PREF_REMEMBER);
        PREFS.remove(PREF_IDENTITY);
        PREFS.remove(PREF_ROLE);
    }

    private String maskedEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) return "registered email";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return "*".repeat(Math.max(1, local.length())) + domain;
        return local.substring(0, 2) + "***" + domain;
    }

    @FXML private void register() { SceneManager.showRegistration(); }
    @FXML private void openEmailSettings() { SceneManager.loadEmailSettings(); }

    private void installLiveClear(TextInputControl field, Label error) {
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) clearFieldError(field, error);
        });
    }

    private void clearErrors() {
        clearFieldError(txtUsername, lblUsernameError);
        clearFieldError(txtPassword, lblPasswordError);
        clearFieldError(cmbRole, lblRoleError);
        clearFieldError(txtOtp, lblOtpError);
    }

    private void clearResetErrors() {
        clearFieldError(txtResetIdentity, lblResetIdentityError);
        clearFieldError(txtResetOtp, lblResetOtpError);
        clearFieldError(txtNewPassword, lblNewPasswordError);
        clearFieldError(txtConfirmPassword, lblConfirmPasswordError);
    }

    private void showFieldError(Control field, Label label, String text) {
        label.setText(text);
        label.setManaged(true);
        label.setVisible(true);
        if (!field.getStyleClass().contains("invalid-field")) field.getStyleClass().add("invalid-field");
        field.requestFocus();
    }

    private void clearFieldError(Control field, Label label) {
        if (label != null) {
            label.setManaged(false);
            label.setVisible(false);
        }
        if (field != null) field.getStyleClass().remove("invalid-field");
    }

    private void message(String text, boolean error) {
        lblMessage.setText(text);
        lblMessage.getStyleClass().removeAll("message-error", "message-success");
        lblMessage.getStyleClass().add(error ? "message-error" : "message-success");
    }
}
