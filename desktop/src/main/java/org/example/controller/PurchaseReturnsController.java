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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.api.returns.ReturnApiClient;
import org.example.api.support.SupportApiClient;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.EmailService;
import org.example.service.InvoicePdfService;
import org.example.service.NotificationService;
import org.example.service.ReturnWorkflowService;
import org.example.util.IconFactory;
import org.example.util.UiTaskExecutor;
import org.example.util.TableSelectionSupport;
import org.example.util.SemanticTableCells;
import org.example.util.RegisterPageState;
import org.example.util.RegisterUiSupport;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Modern database-backed Purchase Return register. */
public class PurchaseReturnsController implements ScreenLifecycle {
    private final ReturnApiClient returnApi=new ReturnApiClient();
    private final SupportApiClient supportApi=new SupportApiClient();
    public record Row(String no, String date, String invoice, String supplier,
                      double total, double refund, String reason, String status, String refundStatus) {}

    @FXML private Label total, count, monthCount, refund, average, pageInfo, lblDetailNo, lblDetailDate, lblDetailInvoice, lblDetailSupplier, lblDetailAmount, lblDetailRefund, lblDetailReason, lblDetailStatus, lblDetailRefundStatus;
    @FXML private StackPane iconTotal,iconMonth,iconCount,iconRefund,iconAverage;
    @FXML private TextField search;
    @FXML private ComboBox<String> supplier, status;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row, String> cNo, cDate, cInvoice, cSupplier, cStatus, cRefundStatus;
    @FXML private TableColumn<Row, Number> cTotal, cRefund;
    @FXML private TableColumn<Row, Void> cAction;
    @FXML private Button btnRefundSelected, btnPrevPage, btnNextPage;
    @FXML private SplitPane mainSplit;
    @FXML private VBox detailDrawer;
    private List<Row> all = List.of();
    private Row selected;
    private final RegisterPageState pageState=new RegisterPageState(); private static final int PAGE_SIZE=25;

    @FXML public void initialize() {
        installKpiIcons();
        configureExplicitTableHeaderIcons();
        cNo.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().no()));
        cDate.setCellValueFactory(x -> new SimpleStringProperty(BusinessClock.formatDate(x.getValue().date())));
        cInvoice.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().invoice()));
        cSupplier.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().supplier()));
        cTotal.setCellValueFactory(x -> new SimpleDoubleProperty(x.getValue().total()));
        cRefund.setCellValueFactory(x -> new SimpleDoubleProperty(x.getValue().refund()));
        cStatus.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().status()));
        cRefundStatus.setCellValueFactory(x -> new SimpleStringProperty(x.getValue().refundStatus()));
        for (TableColumn<Row, Number> column : List.of(cTotal, cRefund)) column.setCellFactory(x -> moneyCell());
        cStatus.setCellFactory(x -> SemanticTableCells.status("return")); cRefundStatus.setCellFactory(x -> SemanticTableCells.status("refund"));
        installActions(); installRows(); configureDrawer();
        dpFrom.setValue(BusinessClock.today().minusMonths(6)); dpTo.setValue(BusinessClock.today());
        org.example.util.PartySearchUi.install(supplier,"SUPPLIER","All Suppliers","purchase-returns-supplier-search");
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
                add("View Details", "view", e -> showDetails(row())); add("Edit Return", "edit", e -> edit(row()));
                add("Print / PDF", "print", e -> pdf(row())); add("View / Download Excel", "excel", e -> excel(row())); add("Send Email", "email", e -> email(row()));
                add("View Original Purchase", "purchase", e -> original(row())); add("Record Refund", "payment", e -> recordRefund(row()));
 add("Notes / Remarks", "document", e -> notes(row()));
                add("Cancel Return", "cancel", e -> cancel(row())); add("Delete Return", "delete", e -> delete(row()));
                menu.getStyleClass().add("row-actions");menu.setGraphic(IconFactory.compactIcon("actions",16));menu.setText("Actions");menu.setContentDisplay(ContentDisplay.LEFT);menu.setGraphicTextGap(6);menu.setTooltip(new Tooltip("Actions"));IconFactory.decorateActionMenu(menu);
            }
            private Row row() { return getTableView().getItems().get(getIndex()); }
            private void add(String name, String icon, javafx.event.EventHandler<javafx.event.ActionEvent> handler) { MenuItem item = new MenuItem(name, IconFactory.compactIcon(icon, 16)); item.setOnAction(handler); menu.getItems().add(item); }
            @Override protected void updateItem(Void value, boolean empty) { super.updateItem(value, empty); if(!empty && getIndex()>=0 && getIndex()<getTableView().getItems().size()){ Row current=getTableView().getItems().get(getIndex()); for(MenuItem mi:menu.getItems()) if(mi.getText()!=null && mi.getText().startsWith("Record Refund")){mi.setDisable(isCancelled(current));mi.setText(isCancelled(current)?"Record Refund (Cancelled)":"Record Refund");}} setGraphic(empty ? null : menu); }
        });
    }

    private void installRows() {
        table.setRowFactory(view -> {
            TableRow<Row> row = new TableRow<>(); row.setOnMouseClicked(e -> { if(e.getButton()!=javafx.scene.input.MouseButton.PRIMARY || e.getClickCount()!=1 || row.isEmpty() || RegisterUiSupport.isInteractiveTableTarget(e.getPickResult().getIntersectedNode(),row))return; Row clicked=row.getItem(); if(detailDrawer.isVisible() && selected==clicked)closeDetails(); else{table.getSelectionModel().select(clicked);showDetails(clicked);}e.consume(); });
            MenuItem add = new MenuItem("Add Purchase Return", IconFactory.compactIcon("add", 16)); add.setOnAction(e -> create());
            MenuItem edit = new MenuItem("Edit Return", IconFactory.compactIcon("edit", 16)); edit.setOnAction(e -> { if (!row.isEmpty()) edit(row.getItem()); });
            MenuItem delete = new MenuItem("Delete Return", IconFactory.compactIcon("delete", 16)); delete.setOnAction(e -> { if (!row.isEmpty()) delete(row.getItem()); });
            ContextMenu menu = new ContextMenu(edit, delete); IconFactory.decorateActionMenu(menu); row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu)); return row;
        });
    }

    @FXML private void load(){String selectedSupplier=supplier.getValue(),selectedStatus=status.getValue();LocalDate from=dpFrom.getValue(),to=dpTo.getValue();int requested=pageState.currentPage();org.example.util.OperationalUiSupport.showLoading(table,"Loading purchase returns…");UiTaskExecutor.submitLatest("purchase-returns-load",()->returnApi.page("PURCHASE RETURN",requested,PAGE_SIZE,search.getText(),selectedSupplier,selectedStatus,str(from),str(to)),this::applyPage,failure->{org.example.util.OperationalUiSupport.showError(table,"Purchase returns could not load",failure);error(asException(failure));});}
    private void applyPage(ReturnApiClient.Page page){pageState.runApplying(()->{List<Row> loaded=new ArrayList<>();if(page!=null&&page.rows()!=null)for(ReturnApiClient.Summary r:page.rows())loaded.add(new Row(r.no(),r.date(),r.invoice(),r.party(),r.total(),r.refund(),safe(r.reason()),safe(r.status()),safe(r.refundStatus())));all=List.copyOf(loaded);pageState.apply(page==null?0:page.page(),page==null?0:page.totalPages(),page==null?0:page.totalRows());String selectedSupplier=supplier.getValue();org.example.util.PartySearchUi.preserveSelection(supplier,selectedSupplier,"All Suppliers");status.setItems(FXCollections.observableArrayList("All Status","PENDING","APPROVED","COMPLETED","PARTIAL","CANCELLED"));if(status.getValue()==null)status.setValue("All Status");table.getItems().setAll(all);if(all.isEmpty())org.example.util.OperationalUiSupport.showEmpty(table,"No purchase returns found","Adjust the filters or create a return from Purchase Register.");applyKpis(page==null?null:page.metrics());updatePageInfo();});}
    private void applyKpis(ReturnApiClient.Metrics m){if(m==null)return;total.setText(money(m.total()));count.setText(String.valueOf(m.count()));if(monthCount!=null)monthCount.setText(String.valueOf(m.monthCount()));refund.setText(money(m.refundAmount()));average.setText(money(m.average()));}
    private void updatePageInfo(){pageInfo.setText(pageState.rangeWithPageText(PAGE_SIZE,all.size(),"returns"));RegisterUiSupport.updatePageNavigation(pageState,btnPrevPage,btnNextPage);}
    @FXML private void filter(){if(pageState.isApplyingServerPage())return;pageState.reset();load();}
    @FXML private void previousPage(){if(pageState.previous())load();}
    @FXML private void nextPage(){if(pageState.next())load();}
    @FXML private void reset(){search.clear();supplier.setValue("All Suppliers");status.setValue("All Status");dpFrom.setValue(BusinessClock.today().minusMonths(6));dpTo.setValue(BusinessClock.today());pageState.reset();load();}
    @Override public void onScreenShown(boolean reusedFromCache){org.example.util.OperationalUiSupport.focusSearch(search);if(reusedFromCache)load();}
    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("purchase-returns-");}

    @FXML private void create() { info("Create a purchase return from the Purchase Register so stock and supplier balances remain linked."); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml"); }
    private void configureDrawer() {if(detailDrawer==null)return;RegisterUiSupport.hideDrawer(detailDrawer,mainSplit,table);org.example.util.OperationalUiSupport.installEscapeClose(mainSplit,()->detailDrawer.isVisible(),this::closeDetails);decorateDrawerNode(detailDrawer);applyValueIcon(lblDetailNo,"return");applyValueIcon(lblDetailSupplier,"supplier");applyValueIcon(lblDetailDate,"calendar");applyValueIcon(lblDetailInvoice,"purchase");applyValueIcon(lblDetailAmount,"currency");applyValueIcon(lblDetailRefund,"payment");applyValueIcon(lblDetailReason,"document");}
    private void decorateDrawerNode(Node node){if(node instanceof Label l&&l.getGraphic()==null){String sem=drawerSemantic(l.getText());if(sem!=null){l.setGraphic(IconFactory.compactIcon(sem,14));l.setGraphicTextGap(6);l.getProperties().put("erp-icon-preserve",true);}}if(node instanceof ButtonBase b&&b.getGraphic()==null){String sem=drawerSemantic(b.getText());if(sem!=null){b.setGraphic(IconFactory.compactIcon(sem,14));b.setGraphicTextGap(6);b.getProperties().put("erp-icon-preserve",true);}}if(node instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())decorateDrawerNode(child);}
    private void applyValueIcon(Label l,String sem){if(l!=null&&l.getGraphic()==null){l.setGraphic(IconFactory.compactIcon(sem,15));l.setGraphicTextGap(7);l.getProperties().put("erp-icon-preserve",true);}}
    private String drawerSemantic(String value){String t=safe(value).toLowerCase(Locale.ROOT);if(t.contains("return"))return"return";if(t.contains("purchase")||t.contains("original"))return"purchase";if(t.contains("supplier"))return"supplier";if(t.contains("date"))return"calendar";if(t.contains("amount"))return"currency";if(t.contains("refund"))return"payment";if(t.contains("reason"))return"document";if(t.contains("status"))return"status";if(t.contains("pdf")||t.contains("print"))return"pdf";if(t.contains("email"))return"email";if(t.contains("detail"))return"view";if(t.contains("close"))return"cancel";return null;}
    private String returnSemantic(String value){String v=safe(value).toUpperCase(Locale.ROOT);if(v.contains("CANCEL")||v.contains("REJECT")||v.contains("FAIL"))return"cancel";if(v.contains("COMPLETE")||v.contains("APPROV")||v.contains("REFUND"))return"complete";if(v.contains("PARTIAL")||v.contains("PROGRESS"))return"refresh";return"reminder";}
    private String returnColor(String value){String v=safe(value).toUpperCase(Locale.ROOT);if(v.contains("CANCEL")||v.contains("REJECT")||v.contains("FAIL"))return"#dc2626";if(v.contains("COMPLETE")||v.contains("APPROV")||v.contains("REFUND"))return"#16a34a";if(v.contains("PARTIAL")||v.contains("PROGRESS"))return"#2563eb";return"#d97706";}
    private void showDetails(Row row){if(row==null)return;selected=row;RegisterUiSupport.showDrawer(detailDrawer,mainSplit,.8);lblDetailNo.setText(row.no());lblDetailSupplier.setText(row.supplier());lblDetailDate.setText(BusinessClock.formatDate(row.date()));lblDetailInvoice.setText(row.invoice());lblDetailAmount.setText(money(row.total()));lblDetailRefund.setText(money(row.refund()));lblDetailReason.setText(safe(row.reason()).isBlank()?"Not set":row.reason());lblDetailStatus.setText(row.status());lblDetailStatus.setGraphic(IconFactory.statusIcon(returnSemantic(row.status()),returnColor(row.status())));lblDetailRefundStatus.setText(row.refundStatus());lblDetailRefundStatus.setGraphic(IconFactory.statusIcon(returnSemantic(row.refundStatus()),returnColor(row.refundStatus())));if(btnRefundSelected!=null){btnRefundSelected.setDisable(isCancelled(row));btnRefundSelected.setTooltip(isCancelled(row)?new Tooltip("Cancelled returns cannot be refunded."):null);}}
    @FXML private void closeDetails(){selected=null;RegisterUiSupport.hideDrawer(detailDrawer,mainSplit,table);}
    @FXML private void pdfSelected(){if(selected!=null)pdf(selected);}
    @FXML private void emailSelected(){if(selected!=null)email(selected);}
    @FXML private void originalSelected(){if(selected!=null)original(selected);}
    @FXML private void refundSelected(){if(selected!=null)recordRefund(selected);}

    private void original(Row row) { if(row==null)return; LinkedRecordContext.open("PURCHASE",null,row.invoice(),"VIEW","Purchase Return "+row.no()); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml"); }
    private void edit(Row row) { input("Update return reason", "Reason:").ifPresent(v -> update(row.no(), "reason", v)); }
    private void notes(Row row) { input("Return notes", "Notes:").ifPresent(v -> update(row.no(), "notes", v)); }
    private void pdf(Row row) { try { java.awt.Desktop.getDesktop().open(InvoicePdfService.refund(row.no(),false).toFile()); } catch(Exception e) { error(e); } }
    private void excel(Row row) { if(row==null)return; try { java.awt.Desktop.getDesktop().open(org.example.documentstudio.service.ExcelOutputService.generate(org.example.documentstudio.model.DocumentType.PURCHASE_RETURN,row.no()).toFile()); } catch(Exception e) { error(e); } }
    private void email(Row row) { try { String recipient=partyEmail(row.no()); if(recipient.isBlank()) throw new IllegalStateException("Supplier email is missing. Update Supplier Master before sending this return."); EmailService.send(recipient,"Purchase Return "+row.no(),"Please find the purchase return note attached.",InvoicePdfService.refund(row.no(),false)); info("Purchase return emailed to "+recipient+"."); } catch(Exception e) { error(e); } }
    private Optional<String> input(String title, String prompt) {
        return input("", title, prompt);
    }

    private Optional<String> input(String initial, String title, String prompt) {
        TextInputDialog dialog = new org.example.util.OwnedTextInputDialog(initial == null ? "" : initial);
        dialog.initOwner(table.getScene() == null ? null : table.getScene().getWindow());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait().map(String::trim).filter(value -> !value.isBlank());
    }

    private String partyEmail(String returnNo){return supportApi.returnPartyEmail(returnNo);}
    private void recordRefund(Row row) { if(row==null)return; if(isCancelled(row)){ org.example.util.ModernDialog.warning(table, "Refund blocked", "Cancelled return", "A cancelled return cannot receive or record a refund."); return; } ReturnRefundContext.select(row.no()); NavigationManager.getInstance().loadPage("/fxml/pages/ReturnRefund.fxml"); }
    private boolean isCancelled(Row row) { return row != null && "CANCELLED".equalsIgnoreCase(safe(row.status()).trim()); }
    private void cancel(Row row) { if (!confirm("Cancel " + row.no() + " and reverse its stock movement?")) return; UiTaskExecutor.submitSerial("purchase-return-cancel-"+row.no(),()->{ReturnWorkflowService.cancel(row.no(),false);return true;},ignored->{NotificationService.add(row.no()+" cancelled.");load();},failure->error(asException(failure))); }
    private void delete(Row row) { if (!confirm("Delete " + row.no() + " from the Return Register?\n\nIt will disappear from normal UI, but the backend audit record will be retained as DELETED. Active return stock movement will be reversed safely.")) return; UiTaskExecutor.submitSerial("purchase-return-delete-"+row.no(),()->{ReturnWorkflowService.delete(row.no(),false);return true;},ignored->{NotificationService.add(row.no()+" deleted from register; audit record retained.");load();},failure->error(asException(failure))); }
    private void update(String no,String column,String value){if(!Set.of("reason","notes").contains(column))return;UiTaskExecutor.submitAction("purchase-return-update-"+no+"-"+column,()->{returnApi.update(no,column,value);return true;},ignored->load(),failure->error(asException(failure)));}

    @FXML private void export() {
        FileChooser chooser = new FileChooser(); chooser.setInitialFileName("Purchase_Returns.csv"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(table.getScene().getWindow()); if (file == null) return;
        String q=search.getText(),party=supplier.getValue(),state=status.getValue(),from=str(dpFrom.getValue()),to=str(dpTo.getValue());
        UiTaskExecutor.submitAction("purchase-returns-export",()->{List<ReturnApiClient.Summary> rows=returnApi.allFiltered("PURCHASE RETURN",q,party,state,from,to);try(PrintWriter out=new PrintWriter(file)){out.println("Return No,Date,Purchase No,Supplier,Total,Refund,Status,Refund Status");for(ReturnApiClient.Summary r:rows)out.printf("%s,%s,%s,%s,%.2f,%.2f,%s,%s%n",r.no(),r.date(),r.invoice(),csv(r.party()),r.total(),r.refund(),r.status(),r.refundStatus());}return rows.size();},count->info("Purchase Returns exported • "+count+" records."),failure->error(asException(failure)));
    }


    private LocalDate parse(String value) { try { LocalDate parsed = BusinessClock.parseDate(value); return parsed == null ? LocalDate.MIN : parsed; } catch (Exception e) { return LocalDate.MIN; } }
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

    private Exception asException(Throwable failure){return failure instanceof Exception e?e:new RuntimeException(failure);}
    private static String str(Object v){return v==null?"":String.valueOf(v);}

}
