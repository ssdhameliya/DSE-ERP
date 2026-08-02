package org.example.controller;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.database.DatabaseManager;
import org.example.navigation.NavigationManager;
import org.example.service.EmailService;
import org.example.service.InvoicePdfService;
import org.example.service.NotificationService;
import org.example.service.ReturnWorkflowService;
import org.example.util.IconFactory;
import org.example.util.TableSelectionSupport;

import java.io.File;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Modern database-backed Sales Return register. */
public class SalesReturnsController {
    public record Row(String no, String date, String invoice, String customer,
                      double amount, double refund, String reason, String status,
                      String refundStatus) {}

    @FXML private Label total, month, pending, approved, refund, pageInfo;
    @FXML private TextField search;
    @FXML private ComboBox<String> customerFilter, statusFilter;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row, String> no, date, invoice, customer, reason, status, refundStatus;
    @FXML private TableColumn<Row, Number> amount;
    @FXML private TableColumn<Row, Void> action;
    private final List<Row> all = new ArrayList<>();

    @FXML public void initialize() {
        configureExplicitTableHeaderIcons();
        no.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().no()));
        date.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().date()));
        invoice.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().invoice()));
        customer.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().customer()));
        amount.setCellValueFactory(x -> new SimpleDoubleProperty(x.getValue().amount()));
        reason.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().reason()));
        status.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().status()));
        refundStatus.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().refundStatus()));
        amount.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty); setText(empty ? null : money(value.doubleValue())); setAlignment(Pos.CENTER_RIGHT);
            }
        });
        status.setCellFactory(column -> statusCell("return"));
        refundStatus.setCellFactory(column -> statusCell("payment"));
        status.setGraphic(IconFactory.icon("return"));
        refundStatus.setGraphic(IconFactory.icon("payment"));
        installSelection();
        installActions();
        installRows();
        dpFrom.setValue(LocalDate.now().minusDays(7));
        dpTo.setValue(LocalDate.now());
        search.textProperty().addListener((o, a, b) -> filter());
        customerFilter.valueProperty().addListener((o, a, b) -> filter());
        statusFilter.valueProperty().addListener((o, a, b) -> filter());
        load();
    }

    @SuppressWarnings("unchecked")
    private void installSelection() {
        TableColumn<Row, Boolean> selection = (TableColumn<Row, Boolean>) (TableColumn<?, ?>) table.getColumns().getFirst();
        selection.setMinWidth(42); selection.setPrefWidth(42); selection.setMaxWidth(42);
        TableSelectionSupport.install(table, selection);
    }

    private void installActions() {
        action.setCellFactory(column -> new TableCell<>() {
            final MenuButton menu = new MenuButton("Actions");
            {
                add("View Details", "view", e -> details(row()));
                add("Edit Return", "edit", e -> edit(row()));
                add("Print / PDF", "print", e -> details(row()));
                add("Send Email", "email", e -> email(row()));
                add("View Original Sale", "sale", e -> original(row()));
                add("Record Refund", "payment", e -> recordRefund(row()));
                add("Attach Document", "attachment", e -> attach(row()));
                add("Notes / Remarks", "document", e -> notes(row()));
                add("Cancel Return", "cancel", e -> cancel(row()));
                add("Delete Return", "delete", e -> delete(row()));
                menu.getStyleClass().add("row-actions");
            }
            private Row row() { return getTableView().getItems().get(getIndex()); }
            private void add(String text, String icon, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
                MenuItem item = new MenuItem(text, IconFactory.icon(icon)); item.setOnAction(handler); menu.getItems().add(item);
            }
            @Override protected void updateItem(Void value, boolean empty) { super.updateItem(value, empty); setGraphic(empty ? null : menu); }
        });
    }

    private void installRows() {
        table.setRowFactory(view -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) details(row.getItem()); });
            MenuItem add = new MenuItem("Add Sales Return", IconFactory.icon("add")); add.setOnAction(e -> create());
            MenuItem edit = new MenuItem("Edit Return", IconFactory.icon("edit")); edit.setOnAction(e -> { if (!row.isEmpty()) edit(row.getItem()); });
            MenuItem remove = new MenuItem("Delete Return", IconFactory.icon("delete")); remove.setOnAction(e -> { if (!row.isEmpty()) delete(row.getItem()); });
            ContextMenu menu = new ContextMenu(add, edit, remove);
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private TableCell<Row, String> statusCell(String icon) {
        return new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(empty ? null : value); setGraphic(null);
                getStyleClass().removeAll("pill-success", "pill-warning", "pill-danger");
                if (empty || value == null) return;
                String normalized = value.toUpperCase(Locale.ROOT);
                boolean good = normalized.contains("APPROVED") || normalized.contains("COMPLETED") || normalized.contains("REFUNDED");
                boolean bad = normalized.contains("REJECT") || normalized.contains("CANCEL") || normalized.contains("FAILED");
                getStyleClass().add(good ? "pill-success" : bad ? "pill-danger" : "pill-warning");
                setGraphic(IconFactory.statusIcon(bad ? "error" : good ? "save" : icon, bad ? "#dc2626" : good ? "#16a34a" : "#2563eb"));
            }
        };
    }

    private void load() {
        all.clear();
        String sql = "SELECT r.return_no,MAX(r.return_date) return_date,MAX(r.invoice_no) invoice_no," +
            "MAX(COALESCE(p.name,'')) customer,SUM(r.amount) amount,SUM(COALESCE(r.refund_amount,0)) refund," +
            "MAX(COALESCE(r.reason,'')) reason," +
            "CASE WHEN MAX(r.status)='CANCELLED' THEN 'CANCELLED' " +
            "WHEN SUM(COALESCE(r.refund_amount,0))>=SUM(r.amount) AND SUM(r.amount)>0 THEN 'COMPLETED' " +
            "WHEN SUM(COALESCE(r.refund_amount,0))>0 THEN 'PARTIAL' ELSE MAX(COALESCE(r.status,'PENDING')) END status," +
            "CASE WHEN SUM(COALESCE(r.refund_amount,0))>=SUM(r.amount) AND SUM(r.amount)>0 THEN 'REFUNDED' " +
            "WHEN SUM(COALESCE(r.refund_amount,0))>0 THEN 'PARTIAL' ELSE 'PENDING' END refund_status " +
            "FROM return_register r LEFT JOIN party_master p ON p.id=r.party_id " +
            "WHERE r.return_type='SALES RETURN' GROUP BY r.return_no ORDER BY MAX(r.return_date) DESC,MAX(r.id) DESC";
        try (Connection c = DatabaseManager.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                all.add(new Row(r.getString("return_no"), r.getString("return_date"),
                    r.getString("invoice_no"), r.getString("customer"), r.getDouble("amount"),
                    r.getDouble("refund"), safe(r.getString("reason")), safe(r.getString("status")),
                    safe(r.getString("refund_status"))));
            }
        } catch (Exception e) { error(e); }
        customerFilter.setItems(FXCollections.observableArrayList("All Customers"));
        customerFilter.getItems().addAll(all.stream().map(Row::customer).filter(x -> !x.isBlank()).distinct().sorted().toList());
        if (customerFilter.getValue() == null) customerFilter.setValue("All Customers");
        statusFilter.setItems(FXCollections.observableArrayList("All Status", "PENDING", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED"));
        if (statusFilter.getValue() == null) statusFilter.setValue("All Status");
        updateKpis(); filter();
    }

    private void updateKpis() {
        double sum = all.stream().mapToDouble(Row::amount).sum();
        double current = all.stream().filter(x -> parse(x.date()).getMonth() == LocalDate.now().getMonth()).mapToDouble(Row::amount).sum();
        double accepted = all.stream().filter(x -> Set.of("APPROVED", "COMPLETED").contains(x.status())).mapToDouble(Row::amount).sum();
        double refunded = all.stream().mapToDouble(Row::refund).sum();
        total.setText(money(sum)); month.setText(money(current)); approved.setText(money(accepted)); pending.setText(money(sum - accepted)); refund.setText(money(refunded));
    }

    @FXML private void filter() {
        String query = safe(search.getText()).toLowerCase(Locale.ROOT);
        LocalDate from = dpFrom.getValue(), to = dpTo.getValue();
        String party = customerFilter.getValue(), state = statusFilter.getValue();
        List<Row> visible = all.stream()
            .filter(x -> (x.no() + x.invoice() + x.customer() + x.reason()).toLowerCase(Locale.ROOT).contains(query))
            .filter(x -> party == null || party.startsWith("All") || party.equals(x.customer()))
            .filter(x -> state == null || state.startsWith("All") || state.equals(x.status()))
            .filter(x -> from == null || !parse(x.date()).isBefore(from))
            .filter(x -> to == null || !parse(x.date()).isAfter(to)).toList();
        table.getItems().setAll(visible); pageInfo.setText("Showing " + visible.size() + " of " + all.size() + " returns");
    }

    @FXML private void reset() { search.clear(); customerFilter.setValue("All Customers"); statusFilter.setValue("All Status"); dpFrom.setValue(LocalDate.now().minusDays(7)); dpTo.setValue(LocalDate.now()); filter(); }
    @FXML private void refresh() { load(); }
    @FXML private void create() {
        info("Create a sales return from the Sales Register so the original invoice, stock and customer balance stay linked.");
        NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml");
    }
    private void details(Row row) { try { java.awt.Desktop.getDesktop().open(InvoicePdfService.refund(row.no(), true).toFile()); } catch (Exception e) { error(e); } }
    private void edit(Row row) { input(row.reason(), "Edit return reason - " + row.no(), "Reason:").ifPresent(value -> update(row.no(), "reason", value)); }
    private void notes(Row row) { input("", "Return notes - " + row.no(), "Notes:").ifPresent(value -> update(row.no(), "notes", value)); }
    private void attach(Row row) { FileChooser chooser = new FileChooser(); File file = chooser.showOpenDialog(table.getScene().getWindow()); if (file != null) update(row.no(), "attachment_path", file.getAbsolutePath()); }
    private void original(Row row) { NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml"); }
    private void recordRefund(Row row) { input(String.valueOf(Math.max(0, row.amount() - row.refund())), "Refund amount - " + row.no(), "Amount:").ifPresent(value -> { try { ReturnWorkflowService.recordRefund(row.no(), Double.parseDouble(value)); NotificationService.add(row.no() + " refund recorded."); load(); } catch (Exception e) { error(e); } }); }
    private void cancel(Row row) { if (!confirm("Cancel " + row.no() + " and reverse its stock movement?")) return; try { ReturnWorkflowService.cancel(row.no(), true); NotificationService.add(row.no() + " cancelled."); load(); } catch (Exception e) { error(e); } }
    private void delete(Row row) { if (!confirm("Delete " + row.no() + " and reverse every returned item?")) return; try { ReturnWorkflowService.delete(row.no(), true); load(); } catch (Exception e) { error(e); } }
    private void email(Row row) { try { String recipient=partyEmail(row.no()); if(recipient.isBlank()) throw new IllegalStateException("Customer email is missing. Update Customer Master before sending this return."); EmailService.send(recipient,"Sales Return "+row.no(),"Please find the sales return note attached.",InvoicePdfService.refund(row.no(),true)); info("Sales return emailed to "+recipient+"."); } catch(Exception e) { error(e); } }
    private String partyEmail(String returnNo) throws SQLException { try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("SELECT COALESCE(pm.email,'') FROM return_register rr LEFT JOIN party_master pm ON pm.id=rr.party_id WHERE rr.return_no=? LIMIT 1")){p.setString(1,returnNo);try(ResultSet r=p.executeQuery()){return r.next()?safe(r.getString(1)):"";}} }
    private void update(String returnNo,String column,String value){if(!Set.of("reason","notes","attachment_path").contains(column))return;try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE return_register SET "+column+"=?,updated_at=datetime('now') WHERE return_no=?")){p.setString(1,value);p.setString(2,returnNo);p.executeUpdate();load();}catch(Exception e){error(e);}}

    @FXML private void export() {
        FileChooser chooser = new FileChooser(); chooser.setInitialFileName("Sales_Returns.csv"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(table.getScene().getWindow()); if (file == null) return;
        try (PrintWriter out = new PrintWriter(file)) { out.println("Return No,Date,Invoice,Customer,Amount,Reason,Status,Refund Status"); for (Row r : table.getItems()) out.printf("%s,%s,%s,%s,%.2f,%s,%s,%s%n", r.no(), r.date(), r.invoice(), csv(r.customer()), r.amount(), csv(r.reason()), r.status(), r.refundStatus()); }
        catch (Exception e) { error(e); }
    }

    private Optional<String> input(String initial,String title,String label){TextInputDialog dialog=new TextInputDialog(initial);dialog.setHeaderText(title);dialog.setContentText(label);return dialog.showAndWait();}
    private LocalDate parse(String value) { try { return LocalDate.parse(value); } catch (Exception e) { return LocalDate.MIN; } }
    private String csv(String value) { return '"' + safe(value).replace("\"", "\"\"") + '"'; }
    private String money(double value) { return String.format("₹ %,.2f", value); }
    private String safe(String value) { return value == null ? "" : value; }
    private boolean confirm(String text) { return org.example.util.ModernDialog.confirm(table, "Confirmation", "Are you sure?", text); }
    private void info(String value) { org.example.util.ToastManager.success(table, "Completed", value); }
    private void error(Exception e) { org.example.util.ModernDialog.error(table, "Operation failed", "Something went wrong", e.getMessage() == null ? "Operation failed" : e.getMessage()); }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(no, "document");
        IconFactory.applyTableHeaderIcon(date, "calendar");
        IconFactory.applyTableHeaderIcon(invoice, "document");
        IconFactory.applyTableHeaderIcon(customer, "customer");
        IconFactory.applyTableHeaderIcon(reason, "document");
        IconFactory.applyTableHeaderIcon(amount, "currency");
        IconFactory.applyTableHeaderIcon(status, "status");
        IconFactory.applyTableHeaderIcon(refundStatus, "status");
        IconFactory.applyTableHeaderIcon(action, "actions");
    }
}
