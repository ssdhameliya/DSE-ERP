package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedTextInputDialog;


import org.example.util.IconFactory;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import org.example.api.returns.ReturnApiClient;
import org.example.navigation.NavigationManager;
import org.example.service.InvoicePdfService;
import org.example.service.ReturnWorkflowService;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Details, refund processing and branded printing for one purchase return. */
public class PurchaseReturnDetailsController {
    private final ReturnApiClient returnApi=new ReturnApiClient();
    public record Item(String name, String code, double qty, String unit,
                       double rate, double tax, double amount, String reason) {
    }

    @FXML private Label no;
    @FXML private Label status;
    @FXML private Label date;
    @FXML private Label purchase;
    @FXML private Label supplier;
    @FXML private Label type;
    @FXML private Label terms;
    @FXML private Label currency;
    @FXML private Label refundStatus;
    @FXML private Label total;
    @FXML private Label refund;
    @FXML private Label created;
    @FXML private Label updated;
    @FXML private Label attachment;
    @FXML private Label notes;
    @FXML private TableView<Item> table;
    @FXML private TableColumn<Item, String> cName;
    @FXML private TableColumn<Item, String> cCode;
    @FXML private TableColumn<Item, String> cUnit;
    @FXML private TableColumn<Item, String> cReason;
    @FXML private TableColumn<Item, Number> cQty;
    @FXML private TableColumn<Item, Number> cRate;
    @FXML private TableColumn<Item, Number> cTax;
    @FXML private TableColumn<Item, Number> cAmount;

    @FXML
    public void initialize() {
        configureExplicitTableHeaderIcons();
        cName.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().name()));
        cCode.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().code()));
        cUnit.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().unit()));
        cReason.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().reason()));
        cQty.setCellValueFactory(value -> new SimpleDoubleProperty(value.getValue().qty()));
        cRate.setCellValueFactory(value -> new SimpleDoubleProperty(value.getValue().rate()));
        cTax.setCellValueFactory(value -> new SimpleDoubleProperty(value.getValue().tax()));
        cAmount.setCellValueFactory(value -> new SimpleDoubleProperty(value.getValue().amount()));
        load();
    }

    /** Reloads the return header and item information from the ERP database. */
    private void load() {
        String key=PurchaseReturnContext.value();if(key==null)return;
        try{ReturnApiClient.Details d=returnApi.details(key);no.setText(d.no());date.setText(d.date());purchase.setText(d.invoice());supplier.setText(d.party());type.setText(d.type());terms.setText(fallback(d.paymentTerms(),"Not specified"));currency.setText(fallback(d.currency(),"INR - Indian Rupee"));created.setText(text(d.createdAt()));updated.setText(text(d.updatedAt()));attachment.setText(text(d.attachment()));notes.setText(text(d.notes()));List<Item> items=d.lines()==null?List.of():d.lines().stream().map(x->new Item(x.name(),x.code(),x.quantity(),x.unit(),x.rate(),x.tax(),x.amount(),text(x.reason()))).toList();table.getItems().setAll(items);total.setText(currency(d.total()));refund.setText(currency(d.refund()));status.setText(text(d.status()));refundStatus.setText(text(d.refundStatus()));}catch(Exception exception){showError(exception);}
    }

    /** Shows the real document-level return and refund state. */
    private void loadSummary(String returnNo){load();}

    @FXML
    private void edit() {
        TextInputDialog dialog = new OwnedTextInputDialog(notes.getText());
        dialog.setHeaderText("Notes / Remarks");
        dialog.showAndWait().ifPresent(value -> {
            update("notes", value);
            notes.setText(value);
        });
    }

    @FXML
    private void attach() {
        FileChooser chooser = new FileChooser();
        File file = chooser.showOpenDialog(table.getScene().getWindow());
        if (file != null) {
            update("attachment_path", file.getAbsolutePath());
            attachment.setText(file.getName());
        }
    }

    /** Records the refund and derives PENDING, PARTIAL or REFUNDED from amounts. */
    @FXML
    private void refund() {
        TextInputDialog dialog = new OwnedTextInputDialog(total.getText().replaceAll("[^0-9.]", ""));
        dialog.setHeaderText("Refund amount");
        dialog.showAndWait().ifPresent(value -> {
            try {
                double amount = Double.parseDouble(value);
                ReturnWorkflowService.recordRefund(no.getText(),amount);
                load();
            } catch (Exception exception) {
                showError(exception);
            }
        });
    }

    /** Opens the same professional refund note used by email and download actions. */
    @FXML
    private void print() {
        try {
            Desktop.getDesktop().open(InvoicePdfService.refund(no.getText(), false).toFile());
        } catch (Exception exception) {
            showError(exception);
        }
    }

    @FXML
    private void back() {
        NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseReturns.fxml");
    }

    private void update(String column,String value){if(!Set.of("notes","attachment_path").contains(column))return;try{returnApi.update(no.getText(),column,value);}catch(Exception exception){showError(exception);}}

    private String currency(double value) {
        return String.format("\u20B9 %,.2f", value);
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String fallback(String value,String defaultValue){String result=text(value).trim();return result.isEmpty()?defaultValue:result;}

    private void showError(Exception exception) {
        new OwnedAlert(Alert.AlertType.ERROR,
            exception.getMessage() == null ? "Operation failed." : exception.getMessage())
            .showAndWait();
    }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(cName, "item");
        IconFactory.applyTableHeaderIcon(cCode, "document");
        IconFactory.applyTableHeaderIcon(cUnit, "unit");
        IconFactory.applyTableHeaderIcon(cReason, "document");
        IconFactory.applyTableHeaderIcon(cQty, "quantity");
        IconFactory.applyTableHeaderIcon(cRate, "currency");
        IconFactory.applyTableHeaderIcon(cTax, "tax");
        IconFactory.applyTableHeaderIcon(cAmount, "currency");
    }
}
