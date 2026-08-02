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
import org.example.database.DatabaseManager;
import org.example.navigation.NavigationManager;
import org.example.service.InvoicePdfService;
import org.example.service.ReturnWorkflowService;

import java.awt.Desktop;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Details, refund processing and branded printing for one purchase return. */
public class PurchaseReturnDetailsController {
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

    /** Reloads the return header and item information from SQLite. */
    private void load() {
        String key = PurchaseReturnContext.value();
        if (key == null) return;
        List<Item> items = new ArrayList<>();
        String sql = "SELECT r.*,COALESCE(pm.name,'') supplier_name," +
            "COALESCE(im.description,r.item_code) item_name,COALESCE(im.unit,'Nos') item_unit," +
            "COALESCE(pl.rate,0) item_rate,COALESCE(pl.gst_percent,0) item_tax," +
            "ph.payment_terms,ph.currency " +
            "FROM return_register r LEFT JOIN party_master pm ON pm.id=r.party_id " +
            "LEFT JOIN item_master im ON im.item_code=r.item_code " +
            "LEFT JOIN purchase_header ph ON ph.invoice_no=r.invoice_no " +
            "LEFT JOIN purchase_line pl ON pl.purchase_id=ph.id AND pl.item_code=r.item_code " +
            "WHERE r.return_no=?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                boolean first = true;
                while (result.next()) {
                    if (first) {
                        no.setText(result.getString("return_no"));
                        date.setText(result.getString("return_date"));
                        purchase.setText(result.getString("invoice_no"));
                        supplier.setText(result.getString("supplier_name"));
                        type.setText(result.getString("return_type"));
                        terms.setText(fallback(result.getString("payment_terms"), "Not specified"));
                        currency.setText(fallback(result.getString("currency"), "INR - Indian Rupee"));
                        created.setText(text(result.getString("created_at")));
                        updated.setText(text(result.getString("updated_at")));
                        attachment.setText(text(result.getString("attachment_path")));
                        notes.setText(text(result.getString("notes")));
                        first = false;
                    }
                    items.add(new Item(result.getString("item_name"),
                        result.getString("item_code"), result.getDouble("quantity"),
                        result.getString("item_unit"), result.getDouble("item_rate"),
                        result.getDouble("item_tax"), result.getDouble("amount"),
                        text(result.getString("reason"))));
                }
            }
        } catch (Exception exception) {
            showError(exception);
        }
        table.getItems().setAll(items);
        total.setText(currency(items.stream().mapToDouble(Item::amount).sum()));
        loadSummary(key);
    }

    /** Shows the real document-level return and refund state. */
    private void loadSummary(String returnNo) {
        String sql = "SELECT SUM(amount),SUM(COALESCE(refund_amount,0)),MAX(status) FROM return_register WHERE return_no=?";
        try (Connection connection=DatabaseManager.getConnection();PreparedStatement statement=connection.prepareStatement(sql)) {
            statement.setString(1,returnNo);
            try(ResultSet result=statement.executeQuery()) {
                if(!result.next()) return;
                double documentTotal=result.getDouble(1),paid=result.getDouble(2);
                String main=result.getString(3);
                String refundState=paid<=0.0001?"PENDING":paid+0.0001>=documentTotal?"REFUNDED":"PARTIAL";
                String returnState="CANCELLED".equalsIgnoreCase(main)?"CANCELLED":refundState.equals("REFUNDED")?"COMPLETED":refundState.equals("PARTIAL")?"PARTIAL":fallback(main,"PENDING");
                total.setText(currency(documentTotal));refund.setText(currency(paid));status.setText(returnState);refundStatus.setText(refundState);
            }
        } catch(Exception exception){showError(exception);}
    }

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

    private void update(String column, String value) {
        if (!Set.of("notes", "attachment_path").contains(column)) return;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE return_register SET " + column +
                     "=?,updated_at=datetime('now') WHERE return_no=?")) {
            statement.setString(1, value);
            statement.setString(2, no.getText());
            statement.executeUpdate();
        } catch (Exception exception) {
            showError(exception);
        }
    }

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
