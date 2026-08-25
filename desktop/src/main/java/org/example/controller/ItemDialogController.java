package org.example.controller;

import org.example.util.OwnedAlert;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.model.Item;
import org.example.service.ItemService;
import org.example.service.LookupService;
import org.example.service.NotificationService;
import org.example.util.IconFactory;
import org.example.util.UiTaskExecutor;

import java.util.List;

public class ItemDialogController {
    @FXML private TextField txtItemCode, txtDescription, txtHSN, txtPurchasePrice,
            txtSellingPrice, txtOpeningStock, txtMinimumStock, txtLocation;
    @FXML private TextArea txtRemarks;
    @FXML private ComboBox<String> cmbCategory, cmbUnit, cmbGST, cmbDiscount;
    @FXML private Button btnSave, btnCancel;
    @FXML private Label lblTitle, lblSubtitle;
    @FXML private Label errDescription, errCategory, errUnit, errGst, errDiscount, errHSN, errSellingPrice,
            errPurchasePrice, errOpeningStock, errMinimumStock, errRemarks;
    @FXML private StackPane headerIconHolder;

    private final LookupService lookupService = new LookupService();
    private final ItemService service = new ItemService();
    private Item editingItem;

    @FXML
    public void initialize() {
        txtPurchasePrice.setText("0.00");
        txtSellingPrice.setText("0.00");
        txtOpeningStock.setText("0.00");
        txtMinimumStock.setText("0.00");

        btnSave.setGraphic(IconFactory.icon("save"));
        btnCancel.setGraphic(IconFactory.icon("cancel"));
        headerIconHolder.getChildren().setAll(IconFactory.icon("item", 24));
        installLiveValidation();
        loadBootstrapAsync();
    }

    private record ItemBootstrap(List<String> categories, List<String> units, List<String> gst,
                                 List<String> discounts) { }

    private void loadBootstrapAsync() {
        UiTaskExecutor.submitLatest(
                "item-dialog-bootstrap",
                () -> new ItemBootstrap(
                        lookupService.getValues("CATEGORY"),
                        lookupService.getValues("UNIT"),
                        lookupService.getValues("GST"),
                        lookupService.getValues("DISCOUNT")),
                this::applyBootstrap,
                failure -> showLoadError(failure)
        );
    }

    private void applyBootstrap(ItemBootstrap data) {
        String category = editingItem == null ? cmbCategory.getValue() : editingItem.getCategory();
        String unit = editingItem == null ? cmbUnit.getValue() : editingItem.getUnit();
        String gst = editingItem == null ? cmbGST.getValue() : formatLookup(editingItem.getGst());
        String discount = editingItem == null ? cmbDiscount.getValue() : formatLookup(editingItem.getDiscountPercent());
        cmbCategory.getItems().setAll(data.categories());
        cmbUnit.getItems().setAll(data.units());
        cmbGST.getItems().setAll(data.gst());
        cmbDiscount.getItems().setAll(data.discounts());
        if (category != null) cmbCategory.setValue(category);
        if (unit != null) cmbUnit.setValue(unit);
        if (gst != null) cmbGST.setValue(gst);
        if (discount != null) cmbDiscount.setValue(discount);
        else if (!cmbDiscount.getItems().isEmpty()) cmbDiscount.getSelectionModel().selectFirst();
        if (editingItem == null && (txtItemCode.getText() == null || txtItemCode.getText().isBlank())) requestNextCodeAsync();
    }

    private void requestNextCodeAsync() {
        UiTaskExecutor.submitLatest(
                "item-dialog-next-code",
                service::nextCode,
                code -> { if (editingItem == null && (txtItemCode.getText() == null || txtItemCode.getText().isBlank())) txtItemCode.setText(code == null ? "" : code); },
                this::showLoadError
        );
    }

    private void showLoadError(Throwable failure) {
        Alert alert = new OwnedAlert(Alert.AlertType.ERROR, message(failure));
        alert.setHeaderText("Item master data could not be loaded");
        alert.showAndWait();
    }

    public void setItem(Item item) {
        this.editingItem = item;
        lblTitle.setText("Edit Item");
        lblSubtitle.setText("Update item information");
        btnSave.setText("Update Item");

        txtItemCode.setText(safe(item.getItemCode()));
        txtDescription.setText(safe(item.getDescription()));
        cmbCategory.setValue(item.getCategory());
        cmbUnit.setValue(item.getUnit());
        txtHSN.setText(safe(item.getHsn()));
        cmbGST.setValue(formatLookup(item.getGst()));
        cmbDiscount.setValue(formatLookup(item.getDiscountPercent()));
        txtPurchasePrice.setText(String.valueOf(item.getPurchasePrice()));
        txtSellingPrice.setText(String.valueOf(item.getSellingPrice()));
        txtOpeningStock.setText(String.valueOf(item.getOpeningStock()));
        txtMinimumStock.setText(String.valueOf(item.getMinimumStock()));
        txtLocation.setText(safe(item.getLocation()));
        txtRemarks.setText(safe(item.getRemarks()));
    }

    @FXML
    private void saveItem() {
        if (!validateForm()) return;

        Item item = editingItem == null ? new Item() : editingItem;
        item.setItemCode(txtItemCode.getText().trim());
        item.setDescription(txtDescription.getText().trim());
        item.setCategory(cmbCategory.getValue());
        item.setBrand(null);
        item.setMaterial(null);
        item.setSize(null);
        item.setUnit(cmbUnit.getValue());
        item.setHsn(txtHSN.getText().trim());
        item.setGst(parseLookupDouble(cmbGST));
        item.setDiscountPercent(parseLookupDouble(cmbDiscount));
        item.setPurchasePrice(parseFieldDouble(txtPurchasePrice));
        item.setSellingPrice(parseFieldDouble(txtSellingPrice));
        item.setOpeningStock(parseFieldDouble(txtOpeningStock));
        item.setMinimumStock(parseFieldDouble(txtMinimumStock));
        item.setLocation(txtLocation.getText().trim());
        item.setRemarks(txtRemarks.getText().trim());

        boolean created = editingItem == null;
        btnSave.setDisable(true);
        UiTaskExecutor.submitAction(
                "item-dialog-save-" + item.getItemCode(),
                () -> {
                    if (created) service.save(item); else service.update(item);
                    NotificationService.createNotification(
                            created ? "Item created" : "Item updated",
                            item.getItemCode() + " - " + item.getDescription(),
                            "INFO", "/fxml/pages/ItemMaster.fxml", item.getItemCode());
                    return item;
                },
                saved -> {
                    btnSave.setDisable(false);
                    org.example.util.ToastManager.success(txtItemCode,
                            created ? "Item Created" : "Item Updated",
                            created ? "Item added successfully." : "Item updated successfully.");
                    closeDialog();
                },
                failure -> {
                    btnSave.setDisable(false);
                    Alert alert = new OwnedAlert(Alert.AlertType.ERROR, message(failure));
                    alert.setHeaderText("Item could not be saved");
                    alert.showAndWait();
                }
        );
    }

    private static String message(Throwable failure) {
        Throwable current = failure;
        while (current != null && (current.getMessage() == null || current.getMessage().isBlank()) && current.getCause() != current)
            current = current.getCause();
        return current == null || current.getMessage() == null ? "Unexpected error." : current.getMessage();
    }

    private boolean validateForm() {
        clearAllErrors();
        boolean valid = true;

        if (txtDescription.getText() == null || txtDescription.getText().isBlank()) {
            showError(txtDescription, errDescription, "Description is required.");
            valid = false;
        }
        if (cmbCategory.getValue() == null || cmbCategory.getValue().isBlank()) {
            showError(cmbCategory, errCategory, "Please select a category.");
            valid = false;
        }
        if (cmbUnit.getValue() == null || cmbUnit.getValue().isBlank()) {
            showError(cmbUnit, errUnit, "Please select a unit.");
            valid = false;
        }
        if (txtHSN.getText() == null || txtHSN.getText().trim().isBlank()) {
            showError(txtHSN, errHSN, "HSN Code is required.");
            valid = false;
        }
        if (cmbGST.getValue() == null || cmbGST.getValue().isBlank()) {
            showError(cmbGST, errGst, "Please select GST %.");
            valid = false;
        } else {
            double gst = parseLookupDouble(cmbGST);
            if (gst < 0 || gst > 100) {
                showError(cmbGST, errGst, "GST must be between 0 and 100.");
                valid = false;
            }
        }

        if (cmbDiscount.getValue() == null || cmbDiscount.getValue().isBlank()) {
            showError(cmbDiscount, errDiscount, "Please select discount %.");
            valid = false;
        } else {
            double discount = parseLookupDouble(cmbDiscount);
            if (discount < 0 || discount > 100) {
                showError(cmbDiscount, errDiscount, "Discount must be between 0 and 100.");
                valid = false;
            }
        }

        if (txtRemarks.getText() == null || txtRemarks.getText().trim().isBlank()) {
            showError(txtRemarks, errRemarks, "Remarks are required.");
            valid = false;
        }
        valid &= validateNumber(txtSellingPrice, errSellingPrice, "Selling price", false);
        valid &= validateNumber(txtPurchasePrice, errPurchasePrice, "Purchase price", false);
        valid &= validateNumber(txtOpeningStock, errOpeningStock, "Opening stock", true);
        valid &= validateNumber(txtMinimumStock, errMinimumStock, "Minimum stock", false);
        return valid;
    }

    private boolean validateNumber(TextField field, Label errorLabel, String label, boolean required) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (required && text.isBlank()) {
            showError(field, errorLabel, label + " is required.");
            return false;
        }
        if (text.isBlank()) return true;
        try {
            double value = Double.parseDouble(text);
            if (value < 0) {
                showError(field, errorLabel, label + " cannot be negative.");
                return false;
            }
            return true;
        } catch (NumberFormatException ex) {
            showError(field, errorLabel, "Enter a valid " + label.toLowerCase() + ".");
            return false;
        }
    }

    private void installLiveValidation() {
        txtDescription.textProperty().addListener((o, a, b) -> clearError(txtDescription, errDescription));
        cmbCategory.valueProperty().addListener((o, a, b) -> clearError(cmbCategory, errCategory));
        cmbUnit.valueProperty().addListener((o, a, b) -> clearError(cmbUnit, errUnit));
        txtHSN.textProperty().addListener((o,a,b)->clearError(txtHSN,errHSN));
        cmbGST.valueProperty().addListener((o, a, b) -> clearError(cmbGST, errGst));
        cmbDiscount.valueProperty().addListener((o, a, b) -> clearError(cmbDiscount, errDiscount));
        txtSellingPrice.textProperty().addListener((o, a, b) -> clearError(txtSellingPrice, errSellingPrice));
        txtRemarks.textProperty().addListener((o, a, b) -> clearError(txtRemarks, errRemarks));
        txtPurchasePrice.textProperty().addListener((o, a, b) -> clearError(txtPurchasePrice, errPurchasePrice));
        txtOpeningStock.textProperty().addListener((o, a, b) -> clearError(txtOpeningStock, errOpeningStock));
        txtMinimumStock.textProperty().addListener((o, a, b) -> clearError(txtMinimumStock, errMinimumStock));
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
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private void clearAllErrors() {
        clearError(txtDescription, errDescription);
        clearError(cmbCategory, errCategory);
        clearError(cmbUnit, errUnit);
        clearError(txtHSN, errHSN);
        clearError(cmbGST, errGst);
        clearError(cmbDiscount, errDiscount);
        clearError(txtSellingPrice, errSellingPrice);
        clearError(txtRemarks, errRemarks);
        clearError(txtPurchasePrice, errPurchasePrice);
        clearError(txtOpeningStock, errOpeningStock);
        clearError(txtMinimumStock, errMinimumStock);
    }

    private double parseFieldDouble(TextField field) {
        String text = field.getText() == null ? "" : field.getText().trim();
        return text.isBlank() ? 0 : Double.parseDouble(text);
    }

    private double parseLookupDouble(ComboBox<String> box) {
        if (box == null || box.getValue() == null || box.getValue().isBlank()) return 0;
        try { return Double.parseDouble(box.getValue().replace("%", "").trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String formatLookup(double value) {
        if (Math.rint(value) == value) return String.valueOf((int) value);
        return String.valueOf(value);
    }

    @FXML
    private void closeDialog() { ((Stage) txtItemCode.getScene().getWindow()).close(); }

    private String safe(String value) { return value == null ? "" : value; }
}
