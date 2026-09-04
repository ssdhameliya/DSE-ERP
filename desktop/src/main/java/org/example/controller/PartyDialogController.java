package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.model.Party;
import org.example.service.NotificationService;
import org.example.service.PartyService;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.UiTaskExecutor;

import java.util.regex.Pattern;

public class PartyDialogController {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{7,18}$");
    private static final Pattern GSTIN = Pattern.compile("^[0-9A-Za-z]{15}$");

    @FXML private Label lblTitle, lblSubtitle, lblEmailCaption;
    @FXML private Label errName, errPhone, errEmail, errGstin, errAddress, errOpeningBalance;
    @FXML private TextField txtCode, txtName, txtContact, txtPhone, txtEmail, txtGstin, txtOpeningBalance;
    @FXML private TextArea txtAddress;
    @FXML private CheckBox chkActive;
    @FXML private Button btnSave, btnCancel;
    @FXML private StackPane headerIconHolder;

    private final PartyService service = new PartyService();
    private String type;
    private Party editing;
    private Party savedResult;
    private Boolean savedCreated;

    public Party getSavedResult() { return savedResult; }
    public boolean wasSavedAsCreate() { return Boolean.TRUE.equals(savedCreated); }

    @FXML
    private void initialize() {
        btnSave.setGraphic(IconFactory.icon("save"));
        btnCancel.setGraphic(IconFactory.icon("cancel"));
        installLiveValidation();
    }

    public void configure(String type, Party party) {
        this.type = type;
        this.editing = party;
        boolean customer = "CUSTOMER".equals(type);
        String entity = customer ? "Customer" : "Supplier";
        boolean editingMode = party != null;

        lblTitle.setText((editingMode ? "Edit " : "Add ") + entity);
        lblSubtitle.setText(editingMode
                ? "Update " + entity.toLowerCase() + " information"
                : "Add a new " + entity.toLowerCase() + " to your records");
        btnSave.setText(editingMode ? "Update " + entity : "Save " + entity);
        if (lblEmailCaption != null) lblEmailCaption.setText(customer ? "Email" : "Email *");
        headerIconHolder.getChildren().setAll(IconFactory.icon(customer ? "customer" : "supplier", 24));
        headerIconHolder.getStyleClass().removeAll("customer-title-icon", "supplier-title-icon");
        headerIconHolder.getStyleClass().add(customer ? "customer-title-icon" : "supplier-title-icon");

        if (!editingMode) {
            txtCode.clear();
            txtOpeningBalance.setText("0.00");
            chkActive.setSelected(true);
            UiTaskExecutor.submitLatest(
                    "party-dialog-next-code-" + type,
                    () -> service.nextCode(type),
                    code -> { if (editing == null && this.type.equals(type)) txtCode.setText(code == null ? "" : code); },
                    failure -> showSaveError(entity, failure, "code could not be generated")
            );
            return;
        }

        txtCode.setText(safe(party.getPartyCode()));
        txtName.setText(safe(party.getName()));
        txtContact.setText(safe(party.getContactPerson()));
        txtPhone.setText(safe(party.getPhone()));
        txtEmail.setText(safe(party.getEmail()));
        txtGstin.setText(safe(party.getGstin()));
        txtAddress.setText(safe(party.getAddress()));
        txtOpeningBalance.setText(String.valueOf(party.getOpeningBalance()));
        chkActive.setSelected(party.isActive());
    }

    @FXML
    private void save() {
        if (!validateForm()) return;

        boolean created = editing == null;
        Party party = created ? new Party() : editing;
        party.setPartyType(type);
        party.setPartyCode(created ? "" : txtCode.getText().trim());
        party.setName(txtName.getText().trim());
        party.setContactPerson(txtContact.getText().trim());
        party.setPhone(txtPhone.getText().trim());
        party.setEmail(txtEmail.getText().trim());
        party.setGstin(txtGstin.getText().trim().toUpperCase());
        party.setAddress(txtAddress.getText().trim());
        party.setOpeningBalance(txtOpeningBalance.getText().isBlank()
                ? 0 : Double.parseDouble(txtOpeningBalance.getText().trim()));
        party.setActive(chkActive.isSelected());

        String entityName = "CUSTOMER".equals(type) ? "Customer" : "Supplier";
        btnSave.setDisable(true);
        UiTaskExecutor.submitAction(
                "party-dialog-save-" + type + "-" + party.getPartyCode(),
                () -> {
                    if (created) service.save(party); else service.update(party);
                    NotificationService.createNotification(
                            (created ? "Created " : "Updated ") + entityName,
                            party.getPartyCode() + " - " + party.getName(),
                            "INFO",
                            "CUSTOMER".equals(type) ? "/fxml/pages/Customer.fxml" : "/fxml/pages/Suppliers.fxml",
                            party.getPartyCode());
                    return party;
                },
                saved -> {
                    btnSave.setDisable(false);
                    savedResult = saved;
                    savedCreated = created;
                    close();
                    org.example.util.ToastManager.success((javafx.stage.Window) null,
                            entityName + (created ? " saved" : " updated"),
                            party.getPartyCode() + " - " + party.getName());
                },
                failure -> {
                    btnSave.setDisable(false);
                    showSaveError(entityName, failure, "could not be saved");
                }
        );
    }

    private void showSaveError(String entityName, Throwable failure, String action) {
        Throwable current = failure;
        while (current != null && (current.getMessage() == null || current.getMessage().isBlank()) && current.getCause() != current)
            current = current.getCause();
        String detail = current == null || current.getMessage() == null ? "Unexpected error." : current.getMessage();
        new OwnedAlert(Alert.AlertType.ERROR, entityName + " " + action + ": " + detail).showAndWait();
    }

    private boolean validateForm() {
        clearErrors();
        boolean valid = true;

        if (txtName.getText() == null || txtName.getText().isBlank()) {
            showError(txtName, errName, "Name is required.");
            valid = false;
        }
        String phone = txtPhone.getText() == null ? "" : txtPhone.getText().trim();
        if (phone.isBlank()) {
            showError(txtPhone, errPhone, "Phone number is required.");
            valid = false;
        } else if (!PHONE.matcher(phone).matches()) {
            showError(txtPhone, errPhone, "Enter a valid phone number.");
            valid = false;
        }
        String email = txtEmail.getText() == null ? "" : txtEmail.getText().trim();
        boolean supplier = "SUPPLIER".equals(type);
        if (supplier && email.isBlank()) {
            showError(txtEmail, errEmail, "Supplier email is required.");
            valid = false;
        } else if (!email.isBlank() && !EMAIL.matcher(email).matches()) {
            showError(txtEmail, errEmail, "Enter a valid email address.");
            valid = false;
        }
        String gstin = txtGstin.getText() == null ? "" : txtGstin.getText().trim();
        if (!gstin.isBlank() && !GSTIN.matcher(gstin).matches()) {
            showError(txtGstin, errGstin, "GSTIN must contain exactly 15 letters or digits.");
            valid = false;
        }
        if (txtAddress.getText() == null || txtAddress.getText().isBlank()) {
            showError(txtAddress, errAddress, "Address is required.");
            valid = false;
        }
        String balance = txtOpeningBalance.getText() == null ? "" : txtOpeningBalance.getText().trim();
        if (!balance.isBlank()) {
            try { Double.parseDouble(balance); }
            catch (NumberFormatException exception) {
                showError(txtOpeningBalance, errOpeningBalance, "Opening balance must be a valid number.");
                valid = false;
            }
        }
        return valid;
    }

    private void installLiveValidation() {
        txtName.textProperty().addListener((o, a, b) -> clearError(txtName, errName));
        txtPhone.textProperty().addListener((o, a, b) -> clearError(txtPhone, errPhone));
        txtEmail.textProperty().addListener((o, a, b) -> clearError(txtEmail, errEmail));
        txtGstin.textProperty().addListener((o, a, b) -> clearError(txtGstin, errGstin));
        txtAddress.textProperty().addListener((o, a, b) -> clearError(txtAddress, errAddress));
        txtOpeningBalance.textProperty().addListener((o, a, b) -> clearError(txtOpeningBalance, errOpeningBalance));
    }

    private void showError(Control control, Label label, String message) {
        if (!control.getStyleClass().contains("validation-error")) control.getStyleClass().add("validation-error");
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
        if (!control.isFocused()) control.requestFocus();
    }

    private void clearError(Control control, Label label) {
        control.getStyleClass().remove("validation-error");
        label.setVisible(false);
        label.setManaged(false);
        label.setText("");
    }

    private void clearErrors() {
        clearError(txtName, errName);
        clearError(txtPhone, errPhone);
        clearError(txtEmail, errEmail);
        clearError(txtGstin, errGstin);
        clearError(txtAddress, errAddress);
        clearError(txtOpeningBalance, errOpeningBalance);
    }

    @FXML private void cancel() { close(); }
    private void close() { ((Stage) txtCode.getScene().getWindow()).close(); }
    private String safe(String value) { return value == null ? "" : value; }
}
