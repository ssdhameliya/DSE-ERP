package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.model.Lookup;
import org.example.service.LookupService;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;

public class LookupDialogController {
    @FXML private TextField txtCode, txtValue;
    @FXML private TextArea txtDescription;
    @FXML private Spinner<Integer> spnOrder;
    @FXML private CheckBox chkActive;
    @FXML private Label lblTitle, lblSubtitle, errCode, errValue;
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
        txtCode.setText(service.generateNextCode(type));
        lblTitle.setText("Add Master");
        lblSubtitle.setText("Add a reusable value to " + type);
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
        lblSubtitle.setText("Update the selected " + lookupType + " value");
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
        try {
            if (created) service.save(lookup); else service.update(lookup);
            saved = true;
            close();
            javafx.application.Platform.runLater(() -> org.example.util.ToastManager.success((javafx.stage.Window) null,
                "Master value " + (created ? "saved" : "updated"),
                lookup.getLookupCode() + " - " + lookup.getLookupValue()));
        } catch (Exception exception) {
            new OwnedAlert(
                    Alert.AlertType.ERROR,
                    "Unable to save master value: "
                            + (exception.getMessage() == null ? "Unexpected error." : exception.getMessage())
            ).showAndWait();
        }
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
