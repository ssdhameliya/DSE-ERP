package org.example.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import org.example.api.support.SupportApiClient;
import org.example.model.Sales;
import org.example.navigation.NavigationManager;
import org.example.service.SalesService;
import org.example.util.OwnedAlert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class PaymentHistoryController {
    @FXML private Label invoiceNo;
    @FXML private Label customer;
    @FXML private Label total;
    @FXML private Label paid;
    @FXML private Label balance;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row, Number> index;
    @FXML private TableColumn<Row, String> date;
    @FXML private TableColumn<Row, String> reference;
    @FXML private TableColumn<Row, String> from;
    @FXML private TableColumn<Row, String> mode;
    @FXML private TableColumn<Row, Number> amount;
    @FXML private TableColumn<Row, String> status;
    @FXML private TableColumn<Row, String> notes;
    @FXML private DatePicker filterDate;
    @FXML private ComboBox<String> filterMode;
    @FXML private ComboBox<String> filterStatus;

    private final SupportApiClient api = new SupportApiClient();
    private final SalesService sales = new SalesService();
    private final ObservableList<Row> allRows = FXCollections.observableArrayList();
    private Sales sale;

    @FXML
    public void initialize() {
        index.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(allRows.indexOf(value.getValue()) + 1));
        date.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().date()));
        reference.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().reference()));
        from.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().receivedFrom()));
        mode.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().mode()));
        amount.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().amount()));
        status.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().status()));
        notes.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().notes()));
        filterMode.getItems().setAll("All Modes", "Cash", "Bank", "UPI", "Cheque", "Card", "Other");
        filterStatus.getItems().setAll("All Status", "Recorded");
        filterMode.setValue("All Modes");
        filterStatus.setValue("All Status");
        String selected = SalesScreenContext.invoice();
        if (selected == null || selected.isBlank()) {
            error("No sales invoice was selected.");
            return;
        }
        sale = sales.getByInvoice(selected);
        if (sale == null) {
            error("The selected sales invoice could not be loaded.");
            return;
        }
        showInvoice();
        load();
    }

    private void showInvoice() {
        invoiceNo.setText(sale.getInvoiceNo());
        customer.setText(sale.getCustomer() == null ? "—" : sale.getCustomer().getName());
        total.setText(money(sale.getTotalAmount()));
        paid.setText(money(sale.getPaidAmount()));
        balance.setText(money(sale.getBalanceAmount()));
    }

    private void load() {
        try {
            allRows.clear();
            for (var payment : api.payments("SALE", sale.getId())) {
                allRows.add(new Row(payment.date(), payment.reference(), payment.receivedFrom(), payment.mode(),
                        payment.amount(), "Recorded", payment.notes()));
            }
            table.setItems(allRows);
        } catch (RuntimeException exception) {
            error("Payment history could not be loaded: " + exception.getMessage());
        }
    }

    @FXML
    private void filter() {
        LocalDate selectedDate = filterDate.getValue();
        String selectedMode = filterMode.getValue();
        String selectedStatus = filterStatus.getValue();
        List<Row> rows = allRows.stream()
                .filter(row -> selectedDate == null || selectedDate.toString().equals(row.date()))
                .filter(row -> selectedMode == null || selectedMode.startsWith("All") || selectedMode.equalsIgnoreCase(row.mode()))
                .filter(row -> selectedStatus == null || selectedStatus.startsWith("All") || selectedStatus.equalsIgnoreCase(row.status()))
                .toList();
        table.setItems(FXCollections.observableArrayList(rows));
    }

    @FXML
    private void clear() {
        filterDate.setValue(null);
        filterMode.setValue("All Modes");
        filterStatus.setValue("All Status");
        table.setItems(allRows);
    }

    @FXML
    private void export() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Payment History");
        chooser.setInitialFileName(sale.getInvoiceNo().replaceAll("[^A-Za-z0-9._-]", "_") + "-payments.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        var file = chooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;
        StringBuilder csv = new StringBuilder("Date,Reference,Received From,Mode,Amount,Status,Notes\n");
        for (Row row : table.getItems()) csv.append(csv(row.date())).append(',').append(csv(row.reference())).append(',')
                .append(csv(row.receivedFrom())).append(',').append(csv(row.mode())).append(',')
                .append(String.format(Locale.ROOT, "%.2f", row.amount())).append(',').append(csv(row.status())).append(',')
                .append(csv(row.notes())).append('\n');
        try { Files.writeString(file.toPath(), csv, StandardCharsets.UTF_8); }
        catch (IOException exception) { error("Export failed: " + exception.getMessage()); }
    }

    @FXML private void record() { SalesScreenContext.select(sale.getInvoiceNo()); NavigationManager.getInstance().loadPage("/fxml/pages/RecordPayment.fxml"); }
    @FXML private void invoice() { SalesScreenContext.select(sale.getInvoiceNo()); NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml"); }

    private static String money(double value) { return String.format(Locale.of("en", "IN"), "₹%,.2f", value); }
    private static String csv(String value) { return '"' + (value == null ? "" : value.replace("\"", "\"\"")) + '"'; }
    private void error(String message) { new OwnedAlert(Alert.AlertType.ERROR, message).showAndWait(); }

    public record Row(String date, String reference, String receivedFrom, String mode, double amount, String status, String notes) {
    }
}
