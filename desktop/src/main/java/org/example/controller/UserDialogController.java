package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.example.api.admin.AdminApiClient;
import org.example.service.NotificationService;
import org.example.util.IconFactory;

import java.util.Locale;
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
    private final AdminApiClient api = new AdminApiClient();

    @FXML
    public void initialize() {
        cmbRole.setConverter(new StringConverter<>() {
            @Override public String toString(String value) { return displayRole(value); }
            @Override public String fromString(String value) { return value; }
        });
        loadRoles();
        cmbAccess.getItems().setAll("FULL ACCESS", "STANDARD", "LIMITED ACCESS", "READ ONLY");
        cmbAccess.setValue("STANDARD");
        chkActive.setSelected(true);

        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        txtConfirmVisible.textProperty().bindBidirectional(txtConfirm.textProperty());
        btnPasswordEye.setGraphic(IconFactory.compactIcon("view", 14));
        btnConfirmEye.setGraphic(IconFactory.compactIcon("view", 14));
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
            txtFullName.setText(nvl(u.fullName()));
            txtUsername.setText(nvl(u.username()));
            txtEmail.setText(nvl(u.email()));
            txtDepartment.setText(nvl(u.department()));
            txtBranch.setText(nvl(u.branch()));
            selectRole(nvl(u.role()));
            cmbAccess.setValue(blank(u.accessLevel(), "STANDARD"));
            chkActive.setSelected(u.active());
            chkLocked.setSelected(u.locked());
            applyRoleSecurityPolicy();
        } catch (Exception e) {
            message("Unable to load user: " + e.getMessage(), true);
        }
    }

    private void loadRoles() {
        cmbRole.getItems().clear();
        try {
            cmbRole.getItems().setAll(api.roles().stream()
                    .filter(AdminApiClient.RoleDto::active)
                    .map(AdminApiClient.RoleDto::name)
                    .filter(value -> value != null && !value.isBlank())
                    .map(UserDialogController::canonicalRole)
                    .distinct()
                    .toList());
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
        boolean mfaRequired = !"ADMIN".equals(canonicalRole(cmbRole.getValue()));
        chkMfa.setSelected(mfaRequired);
        chkMfa.setDisable(true);
        chkMfa.setTooltip(new Tooltip(mfaRequired
                ? "MFA is mandatory for this role and is enforced by the server."
                : "Admin sign-in uses the password factor only by policy."));
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
                    chkMfa.isSelected()));
            NotificationService.add(editingUserId == null
                    ? "User " + txtUsername.getText().trim() + " created with " + displayRole(cmbRole.getValue()) + " access."
                    : "User " + originalUsername + " updated.");
            close();
        } catch (Exception e) {
            String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (m.contains("unique") || m.contains("duplicate")) invalid(txtUsername, "This username already exists.");
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
        if ("SALE".equals(role) || "USER".equals(role)) return "SALES"; // legacy data only; Role Master remains authoritative.
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
