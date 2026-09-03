package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.example.api.admin.AdminApiClient;
import org.example.service.NotificationService;
import org.example.util.IconFactory;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Shared Add/Edit User form backed by the server-owned Role Master and security policy. */
public class UserDialogController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML private Label lblTitle, lblSubtitle, lblMessage;
    @FXML private TextField txtFullName, txtUsername, txtEmail, txtDepartment, txtBranch;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private TextField txtPasswordVisible, txtConfirmVisible;
    @FXML private ComboBox<String> cmbRole, cmbAccess;
    @FXML private CheckBox chkActive, chkLocked, chkMfa;
    @FXML private Button btnSave, btnCancel, btnPasswordEye, btnConfirmEye;

    private Integer editingUserId;
    private String originalUsername;
    private long editingRowVersion;
    private String mfaPolicy = "REQUIRED";
    private final AdminApiClient api = new AdminApiClient();
    private final Map<String,String> roleDisplay = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        cmbRole.setConverter(new StringConverter<>() {
            @Override public String toString(String value) { return roleDisplay.getOrDefault(canonicalRole(value), displayRole(value)); }
            @Override public String fromString(String value) { return value; }
        });
        loadRoles();
        cmbAccess.getItems().setAll("FULL ACCESS", "STANDARD", "LIMITED ACCESS", "READ ONLY");
        cmbAccess.setValue("STANDARD");
        chkActive.setSelected(true);
        try { mfaPolicy = new org.example.api.support.SupportApiClient().setting("security.auth.mfa.policy", "REQUIRED").trim().toUpperCase(Locale.ROOT); } catch (Exception ignored) { mfaPolicy="REQUIRED"; }

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
        installLiveValidation();
        applyRoleSecurityPolicy();
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
        try {
            var u = api.user(userId);
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
            applyRoleSecurityPolicy();
        } catch (Exception e) {
            message("Unable to load user: " + e.getMessage(), true);
        }
    }

    private void loadRoles() {
        cmbRole.getItems().clear();
        roleDisplay.clear();
        try {
            var roles = api.roles().stream().filter(AdminApiClient.RoleDto::active).toList();
            for (var role : roles) {
                String code = canonicalRole(role.code());
                if (code.isBlank()) continue;
                roleDisplay.put(code, blank(role.displayName()) ? displayRole(code) : role.displayName().trim());
                cmbRole.getItems().add(code);
            }
        } catch (Exception e) {
            message("Unable to load roles from Role Master: " + e.getMessage(), true);
        }
        if (cmbRole.getItems().contains("SALES")) cmbRole.setValue("SALES");
        else if (!cmbRole.getItems().isEmpty()) cmbRole.getSelectionModel().selectFirst();
    }

    private void selectRole(String role) {
        String canonical = canonicalRole(role);
        if (cmbRole.getItems().contains(canonical)) cmbRole.setValue(canonical);
        else cmbRole.setValue(canonical);
    }

    private void applyRoleSecurityPolicy() {
        boolean admin = "ADMIN".equals(canonicalRole(cmbRole.getValue()));
        if ("ADMIN_CONTROLLED".equals(mfaPolicy)) {
            chkMfa.setDisable(false);
            chkMfa.setTooltip(new Tooltip("Administrator controlled: this user will require MFA when selected."));
            return;
        }
        boolean required = !"DISABLED".equals(mfaPolicy) && !admin;
        chkMfa.setSelected(required);
        chkMfa.setDisable(true);
        chkMfa.setTooltip(new Tooltip("DISABLED".equals(mfaPolicy)
                ? "MFA is disabled by the server authentication policy."
                : required ? "MFA is required for non-Admin users by server policy." : "Admin is exempt while MFA policy is Required."));
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
        try {
            api.saveUser(new AdminApiClient.UserSaveRequest(editingUserId, txtUsername.getText().trim(),
                    blank(txtPassword.getText()) ? null : txtPassword.getText(), txtFullName.getText().trim(),
                    txtEmail.getText().trim(), canonicalRole(cmbRole.getValue()), txtDepartment.getText().trim(),
                    cmbAccess.getValue(), txtBranch.getText().trim(), chkActive.isSelected(), chkLocked.isSelected(),
                    chkMfa.isSelected(), editingRowVersion));
            NotificationService.add(editingUserId == null
                    ? "User " + txtUsername.getText().trim() + " created with " + roleDisplay.getOrDefault(canonicalRole(cmbRole.getValue()), displayRole(cmbRole.getValue())) + " access."
                    : "User " + originalUsername + " updated.");
            close();
        } catch (Exception e) {
            String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (m.contains("username") && (m.contains("already") || m.contains("duplicate") || m.contains("unique"))) invalid(txtUsername, "This username already exists.");
            else if (m.contains("email") && (m.contains("already") || m.contains("duplicate") || m.contains("unique"))) invalid(txtEmail, "This email address is already assigned to another user.");
            else message("Unable to save user: " + e.getMessage(), true);
        }
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
