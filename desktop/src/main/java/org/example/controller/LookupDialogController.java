package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.model.Lookup;
import org.example.service.LookupService;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.UiTaskExecutor;

import java.util.Locale;

public class LookupDialogController {
    @FXML private TextField txtCode, txtValue;
    @FXML private TextArea txtDescription;
    @FXML private Spinner<Integer> spnOrder;
    @FXML private CheckBox chkActive;
    @FXML private Label lblTitle, lblSubtitle, lblCodeLabel, lblValueLabel, lblCodeHint, errCode, errValue;
    @FXML private Button btnSave, btnCancel;
    @FXML private StackPane headerIconHolder;

    private final LookupService service = new LookupService();
    private String lookupType;
    private Lookup editingLookup;
    private boolean saved;
    private boolean roleMode;
    private Lookup savedResult;

    @FXML
    public void initialize() {
        spnOrder.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
        btnSave.setGraphic(IconFactory.icon("save"));
        btnCancel.setGraphic(IconFactory.icon("cancel"));
        headerIconHolder.getChildren().setAll(IconFactory.icon("master", 24));
        txtValue.textProperty().addListener((o, a, b) -> {
            clearError(txtValue, errValue);
            if (roleMode && editingLookup == null) {
                txtCode.setText(roleDisplayCode(b));
                clearError(txtCode, errCode);
            }
        });
    }

    public void setLookupType(String type) {
        this.lookupType = type;
        this.roleMode = isRoleType(type);
        txtCode.clear();
        if (!roleMode) {
            UiTaskExecutor.submitLatest(
                    "lookup-dialog-next-code-" + type,
                    () -> service.generateNextCode(type),
                    code -> { if (editingLookup == null && java.util.Objects.equals(this.lookupType, type)) txtCode.setText(code == null ? "" : code); },
                    failure -> showOperationError("Unable to generate master code", failure)
            );
        }
        lblTitle.setText("Add Master");
        lblSubtitle.setText(roleMode ? "Add an application role. Role Name is the security identity used by Login, User Access and Permissions." : "Add a reusable value to " + type);
        if (lblCodeLabel != null) lblCodeLabel.setText(roleMode ? "Role Code" : "Code *");
        if (lblValueLabel != null) lblValueLabel.setText(roleMode ? "Role Name *" : "Value *");
        if (lblCodeHint != null) lblCodeHint.setText(roleMode ? "Derived from Role Name; the internal master ID is hidden." : "System-generated master identifier");
        txtCode.setEditable(!roleMode);
        txtCode.setFocusTraversable(!roleMode);
        txtValue.setPromptText(roleMode ? "Example: Purchase" : "Enter a meaningful value");
        btnSave.setText("Save Master");
    }

    public void setLookup(Lookup lookup) {
        this.editingLookup = lookup;
        this.lookupType = lookup.getLookupType();
        this.roleMode = isRoleType(lookupType);
        txtValue.setText(lookup.getLookupValue());
        txtCode.setText(roleMode ? roleDisplayCode(lookup.getLookupValue()) : lookup.getLookupCode());
        txtDescription.setText(lookup.getDescription());
        spnOrder.getValueFactory().setValue(lookup.getDisplayOrder());
        chkActive.setSelected(lookup.isActive());
        lblTitle.setText("Edit Master");
        lblSubtitle.setText(roleMode ? "Update the Role Name. Assigned users and permissions are migrated atomically when a role is renamed." : "Update the selected " + lookupType + " value");
        if (lblCodeLabel != null) lblCodeLabel.setText(roleMode ? "Role Code" : "Code *");
        if (lblValueLabel != null) lblValueLabel.setText(roleMode ? "Role Name *" : "Value *");
        if (lblCodeHint != null) lblCodeHint.setText(roleMode ? "Derived from Role Name; the internal master ID is hidden." : "System-generated master identifier");
        txtCode.setEditable(!roleMode);
        txtCode.setFocusTraversable(!roleMode);
        btnSave.setText("Update Master");
    }

    @FXML
    private void save() {
        if (!validateForm()) return;

        boolean created = editingLookup == null;
        Lookup lookup = created ? new Lookup() : editingLookup;
        lookup.setLookupType(lookupType);
        lookup.setLookupCode(created ? "" : (roleMode ? editingLookup.getLookupCode() : txtCode.getText().trim()));
        lookup.setLookupValue(txtValue.getText().trim());
        lookup.setDescription(txtDescription.getText().trim());
        lookup.setDisplayOrder(spnOrder.getValue());
        lookup.setActive(chkActive.isSelected());

        btnSave.setDisable(true);
        UiTaskExecutor.submitAction(
                "lookup-dialog-save-" + lookup.getLookupType() + "-" + lookup.getLookupCode(),
                () -> { if (created) service.save(lookup); else service.update(lookup); return lookup; },
                savedLookup -> {
                    btnSave.setDisable(false);
                    saved = true;
                    savedResult = savedLookup;
                    close();
                    String displayCode = roleMode ? roleDisplayCode(lookup.getLookupValue()) : lookup.getLookupCode();
                    org.example.util.ToastManager.success((javafx.stage.Window) null,
                            "Master value " + (created ? "saved" : "updated"),
                            displayCode + " - " + lookup.getLookupValue());
                },
                failure -> { btnSave.setDisable(false); showOperationError("Unable to save master value", failure); }
        );
    }

    private void showOperationError(String title, Throwable failure) {
        Throwable current = failure;
        while (current != null && (current.getMessage() == null || current.getMessage().isBlank()) && current.getCause() != current) current = current.getCause();
        String detail = current == null || current.getMessage() == null ? "Unexpected error." : current.getMessage();
        new OwnedAlert(Alert.AlertType.ERROR, title + ": " + detail).showAndWait();
    }

    private boolean validateForm() {
        clearError(txtCode, errCode);
        clearError(txtValue, errValue);
        boolean valid = true;
        if (!roleMode && (txtCode.getText() == null || txtCode.getText().isBlank())) {
            showError(txtCode, errCode, "Code could not be generated.");
            valid = false;
        }
        if (txtValue.getText() == null || txtValue.getText().isBlank()) {
            showError(txtValue, errValue, "Value is required.");
            valid = false;
        }
        return valid;
    }

    private void showError(Control control, Label label, String message) {
        if (!control.getStyleClass().contains("validation-error")) control.getStyleClass().add("validation-error");
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
        control.requestFocus();
    }

    private void clearError(Control control, Label label) {
        control.getStyleClass().remove("validation-error");
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }


    private static boolean isRoleType(String type) {
        return "ROLE".equalsIgnoreCase(type == null ? "" : type.trim());
    }

    private static String roleDisplayCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public boolean wasSaved() { return saved; }
    public Lookup getSavedResult() { return savedResult; }

    @FXML private void cancel() { close(); }
    private void close() { ((Stage) txtCode.getScene().getWindow()).close(); }
}
