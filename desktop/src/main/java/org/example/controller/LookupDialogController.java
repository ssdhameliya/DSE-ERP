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

    @FXML
    public void initialize() {
        spnOrder.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
        btnSave.setGraphic(IconFactory.icon("save"));
        btnCancel.setGraphic(IconFactory.icon("cancel"));
        headerIconHolder.getChildren().setAll(IconFactory.icon("master", 24));
        txtValue.textProperty().addListener((o, a, b) -> clearError(txtValue, errValue));
    }

    public void setLookupType(String type) {
        this.lookupType = type;
        txtCode.clear();
        UiTaskExecutor.submitLatest(
                "lookup-dialog-next-code-" + type,
                () -> service.generateNextCode(type),
                code -> { if (editingLookup == null && java.util.Objects.equals(this.lookupType, type)) txtCode.setText(code == null ? "" : code); },
                failure -> showOperationError("Unable to generate master code", failure)
        );
        lblTitle.setText("Add Master");
        boolean role = "ROLE".equalsIgnoreCase(type == null ? "" : type.trim());
        lblSubtitle.setText(role ? "Add an application role. The Role Name is the value used by Login, User Access and Permissions." : "Add a reusable value to " + type);
        if (lblCodeLabel != null) lblCodeLabel.setText(role ? "Master ID" : "Code *");
        if (lblValueLabel != null) lblValueLabel.setText(role ? "Role Name *" : "Value *");
        if (lblCodeHint != null) lblCodeHint.setText(role ? "Technical ID only — not used for role validation" : "System-generated master identifier");
        txtValue.setPromptText(role ? "Example: Purchase" : "Enter a meaningful value");
        btnSave.setText("Save Master");
    }

    public void setLookup(Lookup lookup) {
        this.editingLookup = lookup;
        this.lookupType = lookup.getLookupType();
        txtCode.setText(lookup.getLookupCode());
        txtValue.setText(lookup.getLookupValue());
        txtDescription.setText(lookup.getDescription());
        spnOrder.getValueFactory().setValue(lookup.getDisplayOrder());
        chkActive.setSelected(lookup.isActive());
        lblTitle.setText("Edit Master");
        boolean role = "ROLE".equalsIgnoreCase(lookupType == null ? "" : lookupType.trim());
        lblSubtitle.setText(role ? "Update the Role Name. Assigned users and permissions are migrated atomically when a role is renamed." : "Update the selected " + lookupType + " value");
        if (lblCodeLabel != null) lblCodeLabel.setText(role ? "Master ID" : "Code *");
        if (lblValueLabel != null) lblValueLabel.setText(role ? "Role Name *" : "Value *");
        if (lblCodeHint != null) lblCodeHint.setText(role ? "Technical ID only — not used for role validation" : "System-generated master identifier");
        btnSave.setText("Update Master");
    }

    @FXML
    private void save() {
        if (!validateForm()) return;

        Lookup lookup = editingLookup == null ? new Lookup() : editingLookup;
        lookup.setLookupType(lookupType);
        lookup.setLookupCode(txtCode.getText().trim());
        lookup.setLookupValue(txtValue.getText().trim());
        lookup.setDescription(txtDescription.getText().trim());
        lookup.setDisplayOrder(spnOrder.getValue());
        lookup.setActive(chkActive.isSelected());

        boolean created = editingLookup == null;
        btnSave.setDisable(true);
        UiTaskExecutor.submitAction(
                "lookup-dialog-save-" + lookup.getLookupType() + "-" + lookup.getLookupCode(),
                () -> { if (created) service.save(lookup); else service.update(lookup); return lookup; },
                savedLookup -> {
                    btnSave.setDisable(false);
                    saved = true;
                    close();
                    org.example.util.ToastManager.success((javafx.stage.Window) null,
                            "Master value " + (created ? "saved" : "updated"),
                            lookup.getLookupCode() + " - " + lookup.getLookupValue());
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
        if (txtCode.getText() == null || txtCode.getText().isBlank()) {
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

    public boolean wasSaved() { return saved; }

    @FXML private void cancel() { close(); }
    private void close() { ((Stage) txtCode.getScene().getWindow()).close(); }
}
