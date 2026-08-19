package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import org.example.model.AppUser;
import org.example.service.NotificationService;
import org.example.service.OtpService;
import org.example.service.SessionService;
import org.example.service.UserService;
import org.example.service.BrandingService;
import org.example.service.PermissionService;
import org.example.service.BrandImagePresenter;
import org.example.theme.ThemeManager;
import org.example.util.ButtonAction;
import org.example.util.ClockService;
import org.example.util.SceneManager;
import org.example.util.UiActionIcons;
import org.example.util.IconFactory;
import org.example.util.UiTaskExecutor;
import org.example.util.PerformanceMonitor;
import org.example.util.PerformanceBudgets;
import org.example.update.BuildInfo;
import org.example.config.ConfigManager;

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
    @FXML private Label lblBrandMark, lblBrandName, lblBrandTagline, lblBrandDescription, lblBrandQuote;
    @FXML private Label lblFooterCompany, lblFooterPhone, lblFooterEmail, lblFooterWebsite;
    @FXML private ImageView imgBrandLogo, imgBrandMark, imgLoginLogo;
    @FXML private VBox brandPanel;
    @FXML private StackPane brandLogoBox, brandMarkBox, loginLogoBox;
    @FXML private Button btnLogin, btnRegister, btnEmailSettings, btnForgotPassword;
    @FXML private Button btnSendResetOtp, btnResetPassword, btnBackToLogin;
    @FXML private VBox loginPanel, resetPanel, otpPanel;

    private final UserService users = new UserService();
    private AppUser pendingUser;
    private String resetChallengeId;
    private boolean passwordFirstKeyLogged;
    private boolean passwordFirstTextLogged;
    private long passwordFocusNanos;

    @FXML public void initialize() {
        if (lblVersion != null) lblVersion.setText("Version " + BuildInfo.version());
        BrandImagePresenter.applicationBanner(imgBrandLogo, brandLogoBox);
        BrandImagePresenter.contain(imgBrandMark, brandMarkBox);
        BrandImagePresenter.applicationBanner(imgLoginLogo, loginLogoBox);
        applyBranding();

        cmbRole.getItems().setAll("Admin", "Manager", "Sale");

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
        // Keep the recovery affordance explicit: generic page decoration must not
        // leave Forgot Password as text-only after a theme/skin refresh.
        btnForgotPassword.setGraphic(IconFactory.compactIcon("reset", 16));
        btnForgotPassword.getProperties().put("erp.icon.semantic", "reset");
        btnForgotPassword.getProperties().put("erp-icon-preserve", true);
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
        txtPassword.focusedProperty().addListener((obs, oldValue, focused) -> {
            if (focused) {
                passwordFocusNanos = System.nanoTime();
                passwordFirstKeyLogged = false;
                passwordFirstTextLogged = false;
                PerformanceMonitor.event("login-password-focus", "ready");
            }
        });
        txtPassword.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!passwordFirstKeyLogged && !event.getCode().isModifierKey()) {
                passwordFirstKeyLogged = true;
                long ms = passwordFocusNanos == 0 ? -1 : (System.nanoTime() - passwordFocusNanos) / 1_000_000L;
                PerformanceMonitor.event("login-password-first-key", "focus-to-key=" + ms + " ms | key=" + event.getCode());
            }
        });
        txtPassword.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!passwordFirstTextLogged && newValue != null && !newValue.isEmpty()) {
                passwordFirstTextLogged = true;
                long ms = passwordFocusNanos == 0 ? -1 : (System.nanoTime() - passwordFocusNanos) / 1_000_000L;
                PerformanceMonitor.event("login-password-first-text", "focus-to-text=" + ms + " ms");
            }
            resetPendingLogin();
        });
        txtResetIdentity.textProperty().addListener((obs, oldValue, newValue) -> resetChallengeId = null);
        cmbRole.valueProperty().addListener((obs, oldValue, newValue) -> {
            clearFieldError(cmbRole, lblRoleError);
            resetPendingLogin();
            updateLoginMode();
        });
        chkRemember.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (!selected) clearRememberedLogin();
        });
        javafx.application.Platform.runLater(() -> {
            // Prime the PasswordField CSS/skin once after the login scene is attached.
            // This avoids paying the first-control initialization cost on the user's
            // first password keystroke, especially on macOS Retina.
            long started = System.nanoTime();
            txtPassword.applyCss();
            txtPassword.getSkin();
            long ms = (System.nanoTime() - started) / 1_000_000L;
            if (ms >= 5) PerformanceMonitor.event("login-password-warmup", ms + " ms");
        });
    }

    private void applyBranding() {
        if (lblBrandName != null) lblBrandName.setText(BrandingService.applicationName());
        if (lblBrandTagline != null) lblBrandTagline.setText(BrandingService.tagline());
        if (lblBrandDescription != null) lblBrandDescription.setText(BrandingService.loginDescription());
        if (lblBrandQuote != null) lblBrandQuote.setText(BrandingService.loginDescription());
        Image logo = BrandingService.applicationBrandImage();
        Image mark = BrandingService.applicationMarkImage();
        if (lblFooterCompany != null) lblFooterCompany.setText(BrandingService.companyName());
        if (lblFooterPhone != null) lblFooterPhone.setText("Phone: " + configured("company.phone"));
        if (lblFooterEmail != null) lblFooterEmail.setText("Email: " + configured("company.email"));
        if (lblFooterWebsite != null) lblFooterWebsite.setText("Website: " + configured("company.website"));
        if (logo != null && !logo.isError()) {
            showLogo(imgBrandLogo, logo);
            showLogo(imgLoginLogo, logo);
        }
        showApplicationMark(mark);
    }

    private void showApplicationMark(Image mark) {
        boolean available = mark != null && !mark.isError();
        if (imgBrandMark != null) {
            imgBrandMark.setImage(available ? mark : null);
            imgBrandMark.setManaged(available);
            imgBrandMark.setVisible(available);
        }
        if (brandMarkBox != null) {
            brandMarkBox.setManaged(available);
            brandMarkBox.setVisible(available);
        }
        if (lblBrandMark != null) {
            lblBrandMark.setManaged(false);
            lblBrandMark.setVisible(false);
        }
    }



    private static void showLogo(ImageView view, Image logo) {
        if (view == null) return;
        view.setImage(logo);
        view.setManaged(true);
        view.setVisible(true);
    }

    private static String configured(String key) {
        String result = ConfigManager.get(key, "").trim();
        return result.isBlank() ? "—" : result;
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
            PermissionService.refresh();
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
        resetChallengeId = null;
        showResetPanel();
        message("Enter your email or username to receive a reset code.", false);
    }

    @FXML private void sendResetOtp() {
        clearResetErrors();
        String identity = txtResetIdentity.getText() == null ? "" : txtResetIdentity.getText().trim();
        if (identity.isBlank()) {
            showFieldError(txtResetIdentity, lblResetIdentityError, "Email or username is required.");
            return;
        }

        try {
            var challenge = users.requestPasswordReset(identity);
            resetChallengeId = challenge.challengeId();
            message(challenge.message() + ".", false);
            txtResetOtp.requestFocus();
        } catch (Exception exception) {
            resetChallengeId = null;
            message("Unable to send reset code: " + exception.getMessage(), true);
        }
    }

    @FXML private void resetPassword() {
        clearResetErrors();

        if (resetChallengeId == null) {
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
        if (password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            showFieldError(txtNewPassword, lblNewPasswordError,
                    "Use 8+ characters with a letter and number.");
            valid = false;
        }
        if (!password.equals(confirm)) {
            showFieldError(txtConfirmPassword, lblConfirmPasswordError,
                    "Passwords do not match.");
            valid = false;
        }
        if (!valid) return;

        try {
            users.completePasswordReset(resetChallengeId, otp, password);
            String identity = txtResetIdentity.getText().trim();
            resetChallengeId = null;
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
        resetChallengeId = null;
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

    private void showResetPanel() {
        if (loginPanel != null) {
            loginPanel.setManaged(false);
            loginPanel.setVisible(false);
        }
        if (resetPanel != null) {
            resetPanel.setManaged(true);
            resetPanel.setVisible(true);
        }
        txtResetIdentity.requestFocus();
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
