package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedTextInputDialog;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.api.returns.ReturnApiClient;
import org.example.api.support.SupportApiClient;
import org.example.navigation.NavigationManager;
import org.example.service.EmailService;
import org.example.service.InvoicePdfService;
import org.example.service.NotificationService;
import org.example.service.ReturnWorkflowService;
import org.example.util.IconFactory;
import org.example.util.TableSelectionSupport;
import org.example.util.SemanticTableCells;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Modern database-backed Purchase Return register. */
public class PurchaseReturnsController {
    private final ReturnApiClient returnApi=new ReturnApiClient();
    private final SupportApiClient supportApi=new SupportApiClient();
    public record Row(String no, String date, String invoice, String supplier,
                      double total, double refund, String status, String refundStatus) {}

    @FXML private Label total, count, monthCount, refund, average, pageInfo;
    @FXML private StackPane iconTotal,iconMonth,iconCount,iconRefund,iconAverage;
    @FXML private TextField search;
    @FXML private ComboBox<String> supplier, status;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row, String> cNo, cDate, cInvoice, cSupplier, cStatus, cRefundStatus;
    @FXML private TableColumn<Row, Number> cTotal, cRefund;
    @FXML private TableColumn<Row, Void> cAction;
    private List<Row> all = List.of();

    @FXML public void initialize() {
        installKpiIcons();
        configureExplicitTableHeaderIcons();
        cNo.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().no()));
        cDate.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().date()));
        cInvoice.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().invoice()));
        cSupplier.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().supplier()));
        cTotal.setCellValueFactory(x -> new SimpleDoubleProperty(x.getValue().total()));
        cRefund.setCellValueFactory(x -> new SimpleDoubleProperty(x.getValue().refund()));
        cStatus.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().status()));
        cRefundStatus.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().refundStatus()));
        for (TableColumn<Row, Number> column : List.of(cTotal, cRefund)) column.setCellFactory(x -> moneyCell());
        cStatus.setCellFactory(x -> SemanticTableCells.status("return")); cRefundStatus.setCellFactory(x -> SemanticTableCells.status("refund"));
        installActions(); installRows();
        dpFrom.setValue(BusinessClock.today().minusDays(7)); dpTo.setValue(BusinessClock.today());
        supplier.valueProperty().addListener((o, a, b) -> filter());
        status.valueProperty().addListener((o, a, b) -> filter());
        search.textProperty().addListener((o, a, b) -> filter());
        load();
    }

    @SuppressWarnings("unchecked")
    private void installSelection() {
        TableColumn<Row, Boolean> selection = (TableColumn<Row, Boolean>) (TableColumn<?, ?>) table.getColumns().getFirst();
        selection.setMinWidth(42); selection.setPrefWidth(42); selection.setMaxWidth(42); TableSelectionSupport.install(table, selection);
    }

    private TableCell<Row, Number> moneyCell() { return new TableCell<>() { @Override protected void updateItem(Number value, boolean empty) { super.updateItem(value, empty); setText(empty ? null : money(value.doubleValue())); setAlignment(Pos.CENTER_RIGHT); } }; }
    private TableCell<Row, String> statusCell(String icon) {
        return new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(empty ? null : value); setGraphic(null); getStyleClass().removeAll("pill-success", "pill-warning", "pill-danger");
                if (empty || value == null) return; String normalized = value.toUpperCase(Locale.ROOT);
                boolean good = normalized.contains("COMPLETED") || normalized.contains("APPROVED") || normalized.contains("REFUNDED");
                boolean bad = normalized.contains("CANCEL") || normalized.contains("REJECT") || normalized.contains("FAILED");
                getStyleClass().add(good ? "pill-success" : bad ? "pill-danger" : "pill-warning");
                setGraphic(IconFactory.statusIcon(bad ? "error" : good ? "save" : icon, bad ? "#dc2626" : good ? "#16a34a" : "#2563eb"));
            }
        };
    }

    private void installActions() {
        cAction.setCellFactory(column -> new TableCell<>() {
            final MenuButton menu = new MenuButton();
            {
                add("View Details", "view", e -> view(row())); add("Edit Return", "edit", e -> edit(row()));
                add("Print / PDF", "print", e -> pdf(row())); add("Send Email", "email", e -> email(row()));
                add("View Original Purchase", "purchase", e -> original(row())); add("Record Refund", "payment", e -> recordRefund(row()));
                add("Attach Document", "attachment", e -> attach(row())); add("Notes / Remarks", "document", e -> notes(row()));
                add("Cancel Return", "cancel", e -> cancel(row())); add("Delete Return", "delete", e -> delete(row()));
                menu.getStyleClass().add("row-actions");menu.setGraphic(IconFactory.compactIcon("actions",16));menu.setText("Actions");menu.setContentDisplay(ContentDisplay.LEFT);menu.setGraphicTextGap(6);menu.setTooltip(new Tooltip("Actions"));
            }
            private Row row() { return getTableView().getItems().get(getIndex()); }
            private void add(String name, String icon, javafx.event.EventHandler<javafx.event.ActionEvent> handler) { MenuItem item = new MenuItem(name, IconFactory.compactIcon(icon, 16)); item.setOnAction(handler); menu.getItems().add(item); }
            @Override protected void updateItem(Void value, boolean empty) { super.updateItem(value, empty); setGraphic(empty ? null : menu); }
        });
    }

    private void installRows() {
        table.setRowFactory(view -> {
            TableRow<Row> row = new TableRow<>(); row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) view(row.getItem()); });
            MenuItem add = new MenuItem("Add Purchase Return", IconFactory.compactIcon("add", 16)); add.setOnAction(e -> create());
            MenuItem edit = new MenuItem("Edit Return", IconFactory.compactIcon("edit", 16)); edit.setOnAction(e -> { if (!row.isEmpty()) edit(row.getItem()); });
            MenuItem delete = new MenuItem("Delete Return", IconFactory.compactIcon("delete", 16)); delete.setOnAction(e -> { if (!row.isEmpty()) delete(row.getItem()); });
            ContextMenu menu = new ContextMenu(edit, delete); row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu)); return row;
        });
    }

    @FXML private void load() {
        List<Row> rows=new ArrayList<>();
        try{for(ReturnApiClient.Summary r:returnApi.list("PURCHASE RETURN"))rows.add(new Row(r.no(),r.date(),r.invoice(),r.party(),r.total(),r.refund(),safe(r.status()),safe(r.refundStatus())));}catch(Exception e){error(e);}
        all=rows; supplier.setItems(FXCollections.observableArrayList("All Suppliers"));supplier.getItems().addAll(rows.stream().map(Row::supplier).filter(x->!x.isBlank()).distinct().sorted().toList());if(supplier.getValue()==null)supplier.setValue("All Suppliers");
        status.setItems(FXCollections.observableArrayList("All Status","PENDING","APPROVED","COMPLETED","PARTIAL","CANCELLED"));if(status.getValue()==null)status.setValue("All Status");
        double sum=rows.stream().mapToDouble(Row::total).sum(),refunded=rows.stream().mapToDouble(Row::refund).sum();total.setText(money(sum));count.setText(String.valueOf(rows.size()));int thisMonth=(int)rows.stream().filter(row->parse(row.date()).getYear()==BusinessClock.today().getYear()&&parse(row.date()).getMonthValue()==BusinessClock.today().getMonthValue()).count();if(monthCount!=null)monthCount.setText(String.valueOf(thisMonth));refund.setText(money(refunded));average.setText(money(rows.isEmpty()?0:sum/rows.size()));filter();
    }

    @FXML private void filter() {
        String query = safe(search.getText()).toLowerCase(Locale.ROOT), selectedSupplier = supplier.getValue(), selectedStatus = status.getValue(); LocalDate from = dpFrom.getValue(), to = dpTo.getValue();
        List<Row> visible = all.stream().filter(x -> (x.no() + x.invoice() + x.supplier()).toLowerCase(Locale.ROOT).contains(query))
            .filter(x -> selectedSupplier == null || selectedSupplier.startsWith("All") || selectedSupplier.equals(x.supplier()))
            .filter(x -> selectedStatus == null || selectedStatus.startsWith("All") || selectedStatus.equals(x.status()))
            .filter(x -> from == null || !parse(x.date()).isBefore(from)).filter(x -> to == null || !parse(x.date()).isAfter(to)).toList();
        table.getItems().setAll(visible); pageInfo.setText("Showing " + visible.size() + " of " + all.size() + " returns");
    }

    @FXML private void reset() { search.clear(); supplier.setValue("All Suppliers"); status.setValue("All Status"); dpFrom.setValue(BusinessClock.today().minusDays(7)); dpTo.setValue(BusinessClock.today()); filter(); }
    @FXML private void create() { info("Create a purchase return from the Purchase Register so stock and supplier balances remain linked."); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml"); }
    private void view(Row row) { PurchaseReturnContext.select(row.no()); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseReturnDetails.fxml"); }
    private void original(Row row) { PurchaseScreenContext.select(row.invoice()); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml"); }
    private void edit(Row row) { input("Update return reason", "Reason:").ifPresent(v -> update(row.no(), "reason", v)); }
    private void notes(Row row) { input("Return notes", "Notes:").ifPresent(v -> update(row.no(), "notes", v)); }
    private void attach(Row row) { FileChooser chooser = new FileChooser(); File file = chooser.showOpenDialog(table.getScene().getWindow()); if (file != null) update(row.no(), "attachment_path", file.getAbsolutePath()); }
    private void pdf(Row row) { try { java.awt.Desktop.getDesktop().open(InvoicePdfService.refund(row.no(),false).toFile()); } catch(Exception e) { error(e); } }
    private void email(Row row) { try { String recipient=partyEmail(row.no()); if(recipient.isBlank()) throw new IllegalStateException("Supplier email is missing. Update Supplier Master before sending this return."); EmailService.send(recipient,"Purchase Return "+row.no(),"Please find the purchase return note attached.",InvoicePdfService.refund(row.no(),false)); info("Purchase return emailed to "+recipient+"."); } catch(Exception e) { error(e); } }
    private String partyEmail(String returnNo){return supportApi.returnPartyEmail(returnNo);}
    private void recordRefund(Row row) { TextInputDialog dialog = new OwnedTextInputDialog(String.valueOf(Math.max(0, row.total() - row.refund()))); dialog.setHeaderText("Refund amount - " + row.no()); dialog.showAndWait().ifPresent(value -> { try { ReturnWorkflowService.recordRefund(row.no(),Double.parseDouble(value)); NotificationService.add(row.no()+" refund recorded."); load(); } catch (Exception e) { error(e); } }); }
    private void cancel(Row row) { if (!confirm("Cancel " + row.no() + " and reverse its stock movement?")) return; try { ReturnWorkflowService.cancel(row.no(),false); NotificationService.add(row.no()+" cancelled."); load(); } catch(Exception e){error(e);} }
    private void delete(Row row) { if (!confirm("Delete " + row.no() + " and reverse every returned item?")) return; try { ReturnWorkflowService.delete(row.no(),false); load(); } catch (Exception e) { error(e); } }
    private void update(String no,String column,String value){if(!Set.of("reason","notes","attachment_path","status").contains(column))return;try{returnApi.update(no,column,value);load();}catch(Exception e){error(e);}}

    @FXML private void export() { FileChooser chooser = new FileChooser(); chooser.setInitialFileName("Purchase_Returns.csv"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv")); File file = chooser.showSaveDialog(table.getScene().getWindow()); if (file == null) return; try (PrintWriter out = new PrintWriter(file)) { out.println("Return No,Date,Purchase No,Supplier,Total,Refund,Status,Refund Status"); for (Row r : table.getItems()) out.printf("%s,%s,%s,%s,%.2f,%.2f,%s,%s%n", r.no(), r.date(), r.invoice(), csv(r.supplier()), r.total(), r.refund(), r.status(), r.refundStatus()); } catch (Exception e) { error(e); } }

    private Optional<String> input(String title, String label) { TextInputDialog d = new OwnedTextInputDialog(); d.setHeaderText(title); d.setContentText(label); return d.showAndWait(); }
    private LocalDate parse(String value) { try { return LocalDate.parse(value); } catch (Exception e) { return LocalDate.MIN; } }
    private String safe(String value) { return value == null ? "" : value; }
    private String csv(String value) { return '"' + safe(value).replace("\"", "\"\"") + '"'; }
    private String money(double value) { return String.format("₹ %,.2f", value); }
    private boolean confirm(String text) { return org.example.util.ModernDialog.confirm(table, "Confirmation", "Are you sure?", text); }
    private void info(String value) { org.example.util.ToastManager.success(table, "Completed", value); }
    private void error(Exception e) { org.example.util.ModernDialog.error(table, "Operation failed", "Something went wrong", e.getMessage() == null ? "Operation failed" : e.getMessage()); }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(cNo, "return");
        IconFactory.applyTableHeaderIcon(cDate, "calendar");
        IconFactory.applyTableHeaderIcon(cInvoice, "purchase");
        IconFactory.applyTableHeaderIcon(cSupplier, "supplier");
        IconFactory.applyTableHeaderIcon(cTotal, "currency");
        IconFactory.applyTableHeaderIcon(cStatus, "status");
        IconFactory.applyTableHeaderIcon(cRefund, "currency");
        IconFactory.applyTableHeaderIcon(cRefundStatus, "status");
        cAction.setText("Actions"); IconFactory.applyTableHeaderIcon(cAction, "actions");
    }

    private void installKpiIcons(){setKpi(iconTotal,"return");setKpi(iconMonth,"calendar");setKpi(iconCount,"document");setKpi(iconRefund,"payment");setKpi(iconAverage,"currency");}
    private void setKpi(StackPane pane,String semantic){if(pane!=null)pane.getChildren().setAll(IconFactory.compactIcon(semantic,22));}
}
