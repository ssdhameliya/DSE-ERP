package org.example.controller;

import org.example.util.BusinessClock;


import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
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

/** Modern database-backed Sales Return register. */
public class SalesReturnsController {
    private final ReturnApiClient returnApi=new ReturnApiClient();
    private final SupportApiClient supportApi=new SupportApiClient();
    public record Row(String no, String date, String invoice, String customer,
                      double amount, double refund, String reason, String status,
                      String refundStatus) {}

    @FXML private Label total, month, pending, approved, refund, pageInfo, lblDetailNo, lblDetailDate, lblDetailInvoice, lblDetailCustomer, lblDetailAmount, lblDetailRefund, lblDetailReason, lblDetailStatus, lblDetailRefundStatus;
    @FXML private TextField search;
    @FXML private ComboBox<String> customerFilter, statusFilter;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row, String> no, date, invoice, customer, reason, status, refundStatus;
    @FXML private TableColumn<Row, Number> amount;
    @FXML private TableColumn<Row, Void> action;
    @FXML private Button btnRefundSelected;
    @FXML private SplitPane mainSplit;
    @FXML private VBox detailDrawer;
    private final List<Row> all = new ArrayList<>();
    private Row selected;

    @FXML public void initialize() {
        configureExplicitTableHeaderIcons();
        no.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().no()));
        date.setCellValueFactory(x -> new SimpleStringProperty(BusinessClock.formatDate(x.getValue().date())));
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
        status.setCellFactory(column -> SemanticTableCells.status("return"));
        refundStatus.setCellFactory(column -> SemanticTableCells.status("refund"));
        installActions();
        installRows();
        configureDrawer();
        dpFrom.setValue(BusinessClock.today().minusMonths(6));
        dpTo.setValue(BusinessClock.today());
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
            final MenuButton menu = new MenuButton();
            {
                add("View Details", "view", e -> showDetails(row()));
                add("Edit Return", "edit", e -> edit(row()));
                add("Print / PDF", "print", e -> pdf(row()));
                add("Send Email", "email", e -> email(row()));
                add("View Original Sale", "sale", e -> original(row()));
                add("Record Refund", "payment", e -> recordRefund(row()));
                add("Notes / Remarks", "document", e -> notes(row()));
                add("Cancel Return", "cancel", e -> cancel(row()));
                add("Delete Return", "delete", e -> delete(row()));
                menu.getStyleClass().add("row-actions");menu.setGraphic(IconFactory.compactIcon("actions",16));menu.setText("Actions");menu.setContentDisplay(ContentDisplay.LEFT);menu.setGraphicTextGap(6);menu.setTooltip(new Tooltip("Actions"));
            }
            private Row row() { return getTableView().getItems().get(getIndex()); }
            private void add(String text, String icon, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
                MenuItem item = new MenuItem(text, IconFactory.compactIcon(icon, 16)); item.setOnAction(handler); menu.getItems().add(item);
            }
            @Override protected void updateItem(Void value, boolean empty) { super.updateItem(value, empty); if(!empty && getIndex()>=0 && getIndex()<getTableView().getItems().size()){ Row current=getTableView().getItems().get(getIndex()); for(MenuItem mi:menu.getItems()) if(mi.getText()!=null && mi.getText().startsWith("Record Refund")){mi.setDisable(isCancelled(current));mi.setText(isCancelled(current)?"Record Refund (Cancelled)":"Record Refund");}} setGraphic(empty ? null : menu); }
        });
    }

    private boolean interactiveTableTarget(Node target, TableRow<?> row) { for (Node node=target; node!=null && node!=row; node=node.getParent()) if (node instanceof ButtonBase || node instanceof TextInputControl || node instanceof ComboBoxBase<?>) return true; return false; }

    private void installRows() {
        table.setRowFactory(view -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getButton()!=javafx.scene.input.MouseButton.PRIMARY || event.getClickCount()!=1 || row.isEmpty() || interactiveTableTarget(event.getPickResult().getIntersectedNode(), row)) return; Row clicked=row.getItem(); if(detailDrawer.isVisible() && selected==clicked) closeDetails(); else { table.getSelectionModel().select(clicked); showDetails(clicked); } event.consume(); });
            MenuItem add = new MenuItem("Add Sales Return", IconFactory.compactIcon("add", 16)); add.setOnAction(e -> create());
            MenuItem edit = new MenuItem("Edit Return", IconFactory.compactIcon("edit", 16)); edit.setOnAction(e -> { if (!row.isEmpty()) edit(row.getItem()); });
            MenuItem remove = new MenuItem("Delete Return", IconFactory.compactIcon("delete", 16)); remove.setOnAction(e -> { if (!row.isEmpty()) delete(row.getItem()); });
            ContextMenu menu = new ContextMenu(edit, remove);
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
        try { for(ReturnApiClient.Summary r:returnApi.list("SALES RETURN")) all.add(new Row(r.no(),r.date(),r.invoice(),r.party(),r.total(),r.refund(),safe(r.reason()),safe(r.status()),safe(r.refundStatus()))); } catch(Exception e){ error(e); }
        customerFilter.setItems(FXCollections.observableArrayList("All Customers"));
        customerFilter.getItems().addAll(all.stream().map(Row::customer).filter(x -> !x.isBlank()).distinct().sorted().toList());
        if (customerFilter.getValue() == null) customerFilter.setValue("All Customers");
        statusFilter.setItems(FXCollections.observableArrayList("All Status", "PENDING", "APPROVED", "REJECTED", "COMPLETED", "CANCELLED"));
        if (statusFilter.getValue() == null) statusFilter.setValue("All Status");
        updateKpis(); filter();
    }

    private void updateKpis() {
        double sum = all.stream().mapToDouble(Row::amount).sum();
        double current = all.stream().filter(x -> parse(x.date()).getMonth() == BusinessClock.today().getMonth()).mapToDouble(Row::amount).sum();
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

    @FXML private void reset() { search.clear(); customerFilter.setValue("All Customers"); statusFilter.setValue("All Status"); dpFrom.setValue(BusinessClock.today().minusMonths(6)); dpTo.setValue(BusinessClock.today()); filter(); }
    @FXML private void refresh() { load(); }
    @FXML private void create() {
        info("Create a sales return from the Sales Register so the original invoice, stock and customer balance stay linked.");
        NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml");
    }
    private void pdf(Row row) { try { java.awt.Desktop.getDesktop().open(InvoicePdfService.refund(row.no(), true).toFile()); } catch (Exception e) { error(e); } }
    private void configureDrawer() {
        if (detailDrawer == null) return;
        detailDrawer.setManaged(false); detailDrawer.setVisible(false);
        if (mainSplit != null) mainSplit.setDividerPositions(1);
        decorateDrawerNode(detailDrawer);
        applyValueIcon(lblDetailNo,"return"); applyValueIcon(lblDetailCustomer,"customer"); applyValueIcon(lblDetailDate,"calendar"); applyValueIcon(lblDetailInvoice,"sale"); applyValueIcon(lblDetailAmount,"currency"); applyValueIcon(lblDetailRefund,"payment"); applyValueIcon(lblDetailReason,"document");
    }
    private void decorateDrawerNode(Node node) {
        if (node instanceof Label label && label.getGraphic()==null) { String semantic=drawerSemantic(label.getText()); if(semantic!=null){label.setGraphic(IconFactory.compactIcon(semantic,14));label.setGraphicTextGap(6);label.getProperties().put("erp-icon-preserve",true);} }
        if (node instanceof ButtonBase button && button.getGraphic()==null) { String semantic=drawerSemantic(button.getText()); if(semantic!=null){button.setGraphic(IconFactory.compactIcon(semantic,14));button.setGraphicTextGap(6);button.getProperties().put("erp-icon-preserve",true);} }
        if (node instanceof Parent parent) for (Node child: parent.getChildrenUnmodifiable()) decorateDrawerNode(child);
    }
    private void applyValueIcon(Label label,String semantic){if(label!=null&&label.getGraphic()==null){label.setGraphic(IconFactory.compactIcon(semantic,15));label.setGraphicTextGap(7);label.getProperties().put("erp-icon-preserve",true);}}
    private String drawerSemantic(String value){String t=safe(value).toLowerCase(Locale.ROOT);if(t.contains("return"))return"return";if(t.contains("invoice"))return"sale";if(t.contains("customer"))return"customer";if(t.contains("date"))return"calendar";if(t.contains("amount"))return"currency";if(t.contains("refund"))return"payment";if(t.contains("reason"))return"document";if(t.contains("status"))return"status";if(t.contains("pdf")||t.contains("print"))return"pdf";if(t.contains("email"))return"email";if(t.contains("original"))return"sale";if(t.contains("close"))return"cancel";return null;}
    private String returnSemantic(String value){String v=safe(value).toUpperCase(Locale.ROOT);if(v.contains("CANCEL")||v.contains("REJECT")||v.contains("FAIL"))return"cancel";if(v.contains("COMPLETE")||v.contains("APPROV"))return"complete";if(v.contains("PARTIAL")||v.contains("PROGRESS"))return"refresh";return"reminder";}
    private String returnColor(String value){String v=safe(value).toUpperCase(Locale.ROOT);if(v.contains("CANCEL")||v.contains("REJECT")||v.contains("FAIL"))return"#dc2626";if(v.contains("COMPLETE")||v.contains("APPROV")||v.contains("REFUND"))return"#16a34a";if(v.contains("PARTIAL")||v.contains("PROGRESS"))return"#2563eb";return"#d97706";}
    private void showDetails(Row row) { if(row==null)return; selected=row; detailDrawer.setManaged(true);detailDrawer.setVisible(true);mainSplit.setDividerPositions(.8);lblDetailNo.setText(row.no());lblDetailCustomer.setText(row.customer());lblDetailDate.setText(BusinessClock.formatDate(row.date()));lblDetailInvoice.setText(row.invoice());lblDetailAmount.setText(money(row.amount()));lblDetailRefund.setText(money(row.refund()));lblDetailReason.setText(safe(row.reason()).isBlank()?"Not set":row.reason());lblDetailStatus.setText(row.status());lblDetailStatus.setGraphic(IconFactory.statusIcon(returnSemantic(row.status()),returnColor(row.status())));lblDetailRefundStatus.setText(row.refundStatus());lblDetailRefundStatus.setGraphic(IconFactory.statusIcon(returnSemantic(row.refundStatus()),returnColor(row.refundStatus())));if(btnRefundSelected!=null){btnRefundSelected.setDisable(isCancelled(row));btnRefundSelected.setTooltip(isCancelled(row)?new Tooltip("Cancelled returns cannot be refunded."):null);}}
    @FXML private void closeDetails(){selected=null;if(detailDrawer!=null){detailDrawer.setManaged(false);detailDrawer.setVisible(false);}if(mainSplit!=null)mainSplit.setDividerPositions(1);if(table!=null)table.getSelectionModel().clearSelection();}
    @FXML private void pdfSelected(){if(selected!=null)pdf(selected);}
    @FXML private void emailSelected(){if(selected!=null)email(selected);}
    @FXML private void originalSelected(){if(selected!=null)original(selected);}
    @FXML private void refundSelected(){if(selected!=null)recordRefund(selected);}

    private void edit(Row row) { input(row.reason(), "Edit return reason - " + row.no(), "Reason:").ifPresent(value -> update(row.no(), "reason", value)); }
    private void notes(Row row) { input("", "Return notes - " + row.no(), "Notes:").ifPresent(value -> update(row.no(), "notes", value)); }
    private void original(Row row) { if(row==null)return; LinkedRecordContext.open("SALE",null,row.invoice(),"VIEW","Sales Return "+row.no()); NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml"); }
    private void recordRefund(Row row) { if(row==null)return; if(isCancelled(row)){ org.example.util.ModernDialog.warning(table, "Refund blocked", "Cancelled return", "A cancelled return cannot receive or record a refund."); return; } ReturnRefundContext.select(row.no()); NavigationManager.getInstance().loadPage("/fxml/pages/ReturnRefund.fxml"); }
    private boolean isCancelled(Row row) { return row != null && "CANCELLED".equalsIgnoreCase(safe(row.status()).trim()); }
    private void cancel(Row row) { if (!confirm("Cancel " + row.no() + " and reverse its stock movement?")) return; try { ReturnWorkflowService.cancel(row.no(), true); NotificationService.add(row.no() + " cancelled."); load(); } catch (Exception e) { error(e); } }
    private void delete(Row row) { if (!confirm("Delete " + row.no() + " and reverse every returned item?")) return; try { ReturnWorkflowService.delete(row.no(), true); load(); } catch (Exception e) { error(e); } }
    private void email(Row row) { try { String recipient=partyEmail(row.no()); if(recipient.isBlank()) throw new IllegalStateException("Customer email is missing. Update Customer Master before sending this return."); EmailService.send(recipient,"Sales Return "+row.no(),"Please find the sales return note attached.",InvoicePdfService.refund(row.no(),true)); info("Sales return emailed to "+recipient+"."); } catch(Exception e) { error(e); } }
    private Optional<String> input(String initial, String title, String prompt) {
        TextInputDialog dialog = new org.example.util.OwnedTextInputDialog(initial == null ? "" : initial);
        dialog.initOwner(table.getScene() == null ? null : table.getScene().getWindow());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait().map(String::trim).filter(value -> !value.isBlank());
    }

    private String partyEmail(String returnNo){ return supportApi.returnPartyEmail(returnNo); }
    private void update(String returnNo,String column,String value){if(!Set.of("reason","notes").contains(column))return;try{returnApi.update(returnNo,column,value);load();}catch(Exception e){error(e);}}

    @FXML private void export() {
        FileChooser chooser = new FileChooser(); chooser.setInitialFileName("Sales_Returns.csv"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(table.getScene().getWindow()); if (file == null) return;
        try (PrintWriter out = new PrintWriter(file)) { out.println("Return No,Date,Invoice,Customer,Amount,Reason,Status,Refund Status"); for (Row r : table.getItems()) out.printf("%s,%s,%s,%s,%.2f,%s,%s,%s%n", r.no(), r.date(), r.invoice(), csv(r.customer()), r.amount(), csv(r.reason()), r.status(), r.refundStatus()); }
        catch (Exception e) { error(e); }
    }

    private LocalDate parse(String value) { try { LocalDate parsed = BusinessClock.parseDate(value); return parsed == null ? LocalDate.MIN : parsed; } catch (Exception e) { return LocalDate.MIN; } }
    private String csv(String value) { return '"' + safe(value).replace("\"", "\"\"") + '"'; }
    private String money(double value) { return String.format("₹ %,.2f", value); }
    private String safe(String value) { return value == null ? "" : value; }
    private boolean confirm(String text) { return org.example.util.ModernDialog.confirm(table, "Confirmation", "Are you sure?", text); }
    private void info(String value) { org.example.util.ToastManager.success(table, "Completed", value); }
    private void error(Exception e) { org.example.util.ModernDialog.error(table, "Operation failed", "Something went wrong", e.getMessage() == null ? "Operation failed" : e.getMessage()); }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(no, "return");
        IconFactory.applyTableHeaderIcon(date, "calendar");
        IconFactory.applyTableHeaderIcon(invoice, "sale");
        IconFactory.applyTableHeaderIcon(customer, "customer");
        IconFactory.applyTableHeaderIcon(reason, "notes");
        IconFactory.applyTableHeaderIcon(amount, "currency");
        IconFactory.applyTableHeaderIcon(status, "status");
        IconFactory.applyTableHeaderIcon(refundStatus, "status");
        action.setText("Actions"); IconFactory.applyTableHeaderIcon(action, "actions");
    }
}
