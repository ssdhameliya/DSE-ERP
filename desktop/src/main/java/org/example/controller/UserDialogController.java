package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.example.api.admin.AdminApiClient;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.util.OwnedAlert;
import org.example.util.IconFactory;
import org.example.util.UiTaskExecutor;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Shared Add/Edit User form backed by the server-owned Role Master and security policy. */
public class UserDialogController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML private Label lblTitle, lblSubtitle, lblMessage, lblMfaPolicy, lblMfaStatus;
    @FXML private TextField txtFullName, txtUsername, txtEmail, txtDepartment, txtBranch;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private TextField txtPasswordVisible, txtConfirmVisible;
    @FXML private ComboBox<String> cmbRole, cmbAccess;
    @FXML private CheckBox chkActive, chkLocked, chkMfa;
    @FXML private Button btnSave, btnCancel, btnPasswordEye, btnConfirmEye, btnResetMfa;

    private Integer editingUserId;
    private String originalUsername;
    private long editingRowVersion;
    private AdminApiClient.UserDto savedResult;
    private boolean originalMfaEnabled;
    private boolean reauthenticationRequired;
    private AdminApiClient.MfaState currentMfaState;

    public AdminApiClient.UserDto getSavedResult() { return savedResult; }
    public boolean isReauthenticationRequired() { return reauthenticationRequired; }
    private String mfaPolicy = "REQUIRED";
    private final AdminApiClient api = new AdminApiClient();
    private final Map<String,String> roleDisplay = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        cmbRole.setConverter(new StringConverter<>() {
            @Override public String toString(String value) { return roleDisplay.getOrDefault(canonicalRole(value), displayRole(value)); }
            @Override public String fromString(String value) { return value; }
        });
        cmbAccess.getItems().setAll("FULL ACCESS", "STANDARD", "LIMITED ACCESS", "READ ONLY");
        cmbAccess.setValue("STANDARD");
        chkActive.setSelected(true);

        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        txtConfirmVisible.textProperty().bindBidirectional(txtConfirm.textProperty());
        btnPasswordEye.setGraphic(IconFactory.compactIcon("view", 14));
        btnConfirmEye.setGraphic(IconFactory.compactIcon("view", 14));
        btnPasswordEye.getProperties().put("erp.icon.skip", true);
        btnConfirmEye.getProperties().put("erp.icon.skip", true);
        btnPasswordEye.getProperties().put("erp-icon-preserve", true);
        btnConfirmEye.getProperties().put("erp-icon-preserve", true);
        btnPasswordEye.setTooltip(new Tooltip("Show password"));
        btnConfirmEye.setTooltip(new Tooltip("Show confirmation password"));
        btnSave.setGraphic(IconFactory.icon("save", 16));
        btnCancel.setGraphic(IconFactory.icon("cancel", 16));

        cmbRole.valueProperty().addListener((o, a, b) -> {
            clearInvalid(cmbRole);
            applyRoleSecurityPolicy();
        });
        chkMfa.selectedProperty().addListener((o,a,b)->updateMfaStatus());
        installLiveValidation();
        applyRoleSecurityPolicy();
        loadFormAsync(null);
    }

    public void editUser(int userId) {
        editingUserId = userId;
        lblTitle.setText("Edit User Account");
        lblSubtitle.setText("Update identity, role, access and account security");
        btnSave.setText("Update User");
        txtPassword.setPromptText("Leave blank to keep current password");
        txtPasswordVisible.setPromptText("Leave blank to keep current password");
        txtConfirm.setPromptText("Confirm new password");
        txtConfirmVisible.setPromptText("Confirm new password");
        loadFormAsync(userId);
    }

    private void loadFormAsync(Integer userId) {
        setFormLoading(true);
        UiTaskExecutor.submitLatest("user-dialog-bootstrap",
                () -> {
                    var roles = api.roles().stream().filter(AdminApiClient.RoleDto::active).toList();
                    String policy;
                    try { policy = new org.example.api.support.SupportApiClient().setting("security.auth.mfa.policy", "REQUIRED").trim().toUpperCase(Locale.ROOT); }
                    catch (Exception ignored) { policy = "REQUIRED"; }
                    AdminApiClient.UserDto user = userId == null ? null : api.user(userId);
                    AdminApiClient.MfaState mfaState = userId == null ? null : api.mfaState(userId);
                    return new UserDialogBootstrap(roles, policy, user, mfaState);
                },
                snapshot -> {
                    applyRoles(snapshot.roles());
                    mfaPolicy = snapshot.mfaPolicy();
                    if (snapshot.user() != null) applyUser(snapshot.user());
                    currentMfaState = snapshot.mfaState();
                    setFormLoading(false);
                    applyRoleSecurityPolicy();
                    updateMfaStatus();
                },
                failure -> {
                    setFormLoading(false);
                    message("Unable to load user settings: " + safeMessage(failure), true);
                });
    }

    private void applyRoles(java.util.List<AdminApiClient.RoleDto> roles) {
        String selected = canonicalRole(cmbRole.getValue());
        cmbRole.getItems().clear();
        roleDisplay.clear();
        for (var role : roles) {
            String code = canonicalRole(role.code());
            if (code.isBlank()) continue;
            roleDisplay.put(code, blank(role.displayName()) ? displayRole(code) : role.displayName().trim());
            cmbRole.getItems().add(code);
        }
        if (!selected.isBlank() && cmbRole.getItems().contains(selected)) cmbRole.setValue(selected);
        else if (cmbRole.getItems().contains("SALES")) cmbRole.setValue("SALES");
        else if (!cmbRole.getItems().isEmpty()) cmbRole.getSelectionModel().selectFirst();
    }

    private void applyUser(AdminApiClient.UserDto u) {
        originalUsername = u.username();
        editingRowVersion = u.rowVersion();
        txtFullName.setText(nvl(u.fullName()));
        txtUsername.setText(nvl(u.username()));
        txtEmail.setText(nvl(u.email()));
        txtDepartment.setText(nvl(u.department()));
        txtBranch.setText(nvl(u.branch()));
        selectRole(nvl(u.role()));
        cmbAccess.setValue(blank(u.accessLevel(), "STANDARD"));
        chkActive.setSelected(u.active());
        chkLocked.setSelected(u.locked());
        chkMfa.setSelected(u.mfaEnabled());
        originalMfaEnabled = u.mfaEnabled();
    }

    private void setFormLoading(boolean loading) {
        for (Control control : new Control[]{txtFullName, txtUsername, txtEmail, txtDepartment, txtBranch,
                txtPassword, txtConfirm, txtPasswordVisible, txtConfirmVisible, cmbRole, cmbAccess,
                chkActive, chkLocked, chkMfa, btnResetMfa}) {
            if (control != null) control.setDisable(loading);
        }
        if (btnSave != null) btnSave.setDisable(loading);
    }

    private record UserDialogBootstrap(java.util.List<AdminApiClient.RoleDto> roles, String mfaPolicy, AdminApiClient.UserDto user, AdminApiClient.MfaState mfaState) { }

    private static String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null || error.getMessage().isBlank() ? "Unexpected error" : error.getMessage();
    }

    private void selectRole(String role) {
        String canonical = canonicalRole(role);
        if (cmbRole.getItems().contains(canonical)) cmbRole.setValue(canonical);
        else cmbRole.setValue(canonical);
    }

    private void applyRoleSecurityPolicy() {
        boolean admin = "ADMIN".equals(canonicalRole(cmbRole.getValue()));
        String normalizedPolicy = mfaPolicy == null ? "REQUIRED" : mfaPolicy.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (lblMfaPolicy != null) lblMfaPolicy.setText(switch (normalizedPolicy) {
            case "ADMIN_CONTROLLED" -> "Admin Controlled";
            case "DISABLED" -> "Disabled";
            default -> "Required for non-Admin roles";
        });
        if ("ADMIN_CONTROLLED".equals(normalizedPolicy)) {
            chkMfa.setDisable(false);
            chkMfa.setTooltip(new Tooltip("Require Google/Microsoft Authenticator for this user."));
        } else {
            boolean required = !"DISABLED".equals(normalizedPolicy) && !admin;
            chkMfa.setSelected(required);
            chkMfa.setDisable(true);
            chkMfa.setTooltip(new Tooltip("DISABLED".equals(normalizedPolicy)
                    ? "MFA is disabled by the server authentication policy."
                    : required ? "MFA is required for this role by server policy." : "Admin is exempt while MFA policy is Required."));
        }
        updateMfaStatus();
    }

    private void updateMfaStatus() {
        if (lblMfaStatus == null) return;
        if (!chkMfa.isSelected()) {
            lblMfaStatus.setText("Not required");
            if (btnResetMfa != null) { btnResetMfa.setVisible(false); btnResetMfa.setManaged(false); }
            return;
        }
        String status = currentMfaState == null ? "ENROLLMENT_REQUIRED" : currentMfaState.status();
        boolean active = "ACTIVE".equalsIgnoreCase(status);
        lblMfaStatus.setText(active ? "Active — Google / Microsoft Authenticator" : "Enrollment required at next sign-in");
        if (btnResetMfa != null) {
            boolean show = editingUserId != null && active;
            btnResetMfa.setVisible(show); btnResetMfa.setManaged(show); btnResetMfa.setDisable(false);
        }
    }

    @FXML
    private void resetAuthenticator() {
        if (editingUserId == null) return;
        OwnedAlert confirm = new OwnedAlert(Alert.AlertType.CONFIRMATION,
                "Reset Authenticator for " + (originalUsername == null ? "this user" : originalUsername) + "?\n\n" +
                        "The existing authenticator will stop working. The user must scan a new QR code at the next sign-in.",
                ButtonType.YES, ButtonType.NO);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        btnResetMfa.setDisable(true);
        UiTaskExecutor.submitAction("user-mfa-reset", () -> api.resetMfa(editingUserId), state -> {
            currentMfaState = state;
            updateMfaStatus();
            message(state.message(), false);
            if (SessionService.current() != null && SessionService.current().getId() == editingUserId) {
                reauthenticationRequired = true;
                close();
            }
        }, failure -> {
            btnResetMfa.setDisable(false);
            message("Authenticator could not be reset: " + safeMessage(failure), true);
        });
    }

    @FXML private void togglePasswordVisibility() { togglePassword(txtPassword, txtPasswordVisible, btnPasswordEye, "password"); }
    @FXML private void toggleConfirmVisibility() { togglePassword(txtConfirm, txtConfirmVisible, btnConfirmEye, "confirmation password"); }

    private void togglePassword(PasswordField masked, TextField plain, Button button, String label) {
        boolean show = !plain.isVisible();
        int caret = show ? masked.getCaretPosition() : plain.getCaretPosition();
        plain.setVisible(show); plain.setManaged(show);
        masked.setVisible(!show); masked.setManaged(!show);
        button.setGraphic(IconFactory.compactIcon(show ? "hide" : "view", 14));
        button.setTooltip(new Tooltip((show ? "Hide " : "Show ") + label));
        TextField target = show ? plain : masked;
        target.requestFocus();
        target.positionCaret(Math.max(0, Math.min(caret, target.getLength())));
    }

    @FXML
    private void save() {
        clearInvalid();
        if (!validateForm()) return;
        AdminApiClient.UserSaveRequest request = new AdminApiClient.UserSaveRequest(editingUserId, txtUsername.getText().trim(),
                blank(txtPassword.getText()) ? null : txtPassword.getText(), txtFullName.getText().trim(),
                txtEmail.getText().trim(), canonicalRole(cmbRole.getValue()), txtDepartment.getText().trim(),
                cmbAccess.getValue(), txtBranch.getText().trim(), chkActive.isSelected(), chkLocked.isSelected(),
                chkMfa.isSelected(), editingRowVersion);
        btnSave.setDisable(true);
        UiTaskExecutor.submitAction("user-dialog-save", () -> api.saveUser(request), saved -> {
            savedResult = saved;
            if (editingUserId != null && SessionService.current() != null && SessionService.current().getId() == editingUserId
                    && originalMfaEnabled != saved.mfaEnabled()) reauthenticationRequired = true;
            NotificationService.add(editingUserId == null
                    ? "User " + txtUsername.getText().trim() + " created with " + roleDisplay.getOrDefault(canonicalRole(cmbRole.getValue()), displayRole(cmbRole.getValue())) + " access."
                    : "User " + originalUsername + " updated.");
            close();
        }, failure -> {
            btnSave.setDisable(false);
            String text = safeMessage(failure);
            String m = text.toLowerCase(Locale.ROOT);
            if (m.contains("username") && (m.contains("already") || m.contains("duplicate") || m.contains("unique"))) invalid(txtUsername, "This username already exists.");
            else if (m.contains("email") && (m.contains("already") || m.contains("duplicate") || m.contains("unique"))) invalid(txtEmail, "This email address is already assigned to another user.");
            else message("Unable to save user: " + text, true);
        });
    }

    private boolean validateForm() {
        if (blank(txtFullName.getText())) return invalid(txtFullName, "Full name is required.");
        if (blank(txtUsername.getText())) return invalid(txtUsername, "Username is required.");
        if (blank(txtEmail.getText())) return invalid(txtEmail, "Email address is required.");
        if (!EMAIL.matcher(txtEmail.getText().trim()).matches()) return invalid(txtEmail, "Enter a valid email address.");
        if (blank(cmbRole.getValue())) return invalid(cmbRole, "Select a role from Role Master.");
        if (cmbAccess.getValue() == null) return invalid(cmbAccess, "Select an access level.");
        boolean passwordRequired = editingUserId == null;
        if (passwordRequired && blank(txtPassword.getText())) return invalid(txtPassword, "Password is required.");
        if (!blank(txtPassword.getText()) && (txtPassword.getText().length() < 8
                || !txtPassword.getText().matches(".*[A-Za-z].*") || !txtPassword.getText().matches(".*[0-9].*")))
            return invalid(txtPassword, "Password needs 8 characters, a letter and a number.");
        if (!txtPassword.getText().equals(txtConfirm.getText())) return invalid(txtConfirm, "Passwords do not match.");
        return true;
    }

    private void installLiveValidation() {
        txtFullName.textProperty().addListener((o,a,b)->clearInvalid(txtFullName));
        txtUsername.textProperty().addListener((o,a,b)->clearInvalid(txtUsername));
        txtEmail.textProperty().addListener((o,a,b)->clearInvalid(txtEmail));
        txtPassword.textProperty().addListener((o,a,b)->clearInvalid(txtPassword));
        txtConfirm.textProperty().addListener((o,a,b)->clearInvalid(txtConfirm));
        cmbAccess.valueProperty().addListener((o,a,b)->clearInvalid(cmbAccess));
    }

    private boolean invalid(Control c, String text) { if (!c.getStyleClass().contains("validation-error")) c.getStyleClass().add("validation-error"); c.requestFocus(); message(text, true); return false; }
    private void clearInvalid() { for (Control c : new Control[]{txtFullName,txtUsername,txtEmail,txtPassword,txtConfirm,cmbRole,cmbAccess}) clearInvalid(c); lblMessage.setText(""); }
    private void clearInvalid(Control c) { if (c != null) c.getStyleClass().remove("validation-error"); }
    @FXML private void cancel() { close(); }
    private void close() { ((Stage) txtUsername.getScene().getWindow()).close(); }
    private void message(String text, boolean error) { lblMessage.setText(text); lblMessage.getStyleClass().setAll(error ? "dialog-error" : "dialog-success"); }

    private static String canonicalRole(String value) {
        if (value == null) return "";
        String role = value.trim().toUpperCase(Locale.ROOT);
        return role;
    }
    private static String displayRole(String value) {
        return switch (canonicalRole(value)) {
            case "ADMIN" -> "Admin";
            case "MANAGER" -> "Manager";
            case "SALES" -> "Sales";
            case "" -> "";
            default -> { String v = canonicalRole(value); yield v.charAt(0) + v.substring(1).toLowerCase(Locale.ROOT); }
        };
    }
    private static boolean blank(String v) { return v == null || v.isBlank(); }
    private static String nvl(String v) { return v == null ? "" : v; }
    private static String blank(String v, String fallback) { return blank(v) ? fallback : v; }
}
