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
import org.example.service.PermissionService;
import org.example.service.ReturnWorkflowService;
import org.example.util.IconFactory;
import org.example.util.UiTaskExecutor;
import org.example.util.TableSelectionSupport;
import org.example.util.SemanticTableCells;
import org.example.util.RegisterPageState;
import org.example.util.RegisterUiSupport;
import org.example.util.ScreenRefreshPolicy;

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
    @FXML private Button btnRefundSelected, btnPrevPage, btnNextPage, btnEditReasonSelected, btnApproveSelected, btnRejectSelected, btnRefreshReturns;
    @FXML private SplitPane mainSplit;
    @FXML private VBox detailDrawer;
    private List<Row> all = List.of();
    private Row selected;
    private boolean explicitRefreshPending;
    private final RegisterPageState pageState=new RegisterPageState(); private static final int PAGE_SIZE=25;
    private boolean suppressAutoFilter;

    @FXML public void initialize() {
        installKpiIcons();
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
        dpFrom.setValue(null); dpTo.setValue(null);
        org.example.util.PartySearchUi.install(supplier,"SUPPLIER","All Suppliers","purchase-returns-supplier-search");
        supplier.valueProperty().addListener((o, a, b) -> filter());
        status.valueProperty().addListener((o, a, b) -> filter());
        search.textProperty().addListener((o, a, b) -> filter());
        dpFrom.valueProperty().addListener((o,a,b)->filter());
        dpTo.valueProperty().addListener((o,a,b)->filter());
        javafx.application.Platform.runLater(this::load);
    }

    @SuppressWarnings("unchecked")
    private void installSelection() {
        TableColumn<Row, Boolean> selection = (TableColumn<Row, Boolean>) (TableColumn<?, ?>) table.getColumns().getFirst();
        TableSelectionSupport.install(table, selection);
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
                setGraphic(IconFactory.statusIcon(bad ? "error" : good ? "save" : icon, bad ? "danger" : good ? "success" : "info"));
            }
        };
    }

    private void installActions() {
        cAction.setCellFactory(column -> new TableCell<>() {
            final MenuButton menu = new MenuButton();
            {
                add("View Details", "view", e -> showDetails(row())); add("Edit Reason", "edit", e -> edit(row()));
                add("Print / PDF", "print", e -> pdf(row())); add("View / Download Excel", "excel", e -> excel(row())); add("Send Email", "email", e -> email(row()));
                add("View Original Purchase", "purchase", e -> original(row()));
                add("Approve Return", "approve", e -> approveReturn(row()));
                add("Reject Return", "reject", e -> rejectReturn(row()));
                add("Record Refund", "payment", e -> recordRefund(row()));
                add("Cancel Return", "cancel", e -> cancel(row())); add("Delete Return", "delete", e -> delete(row()));
                menu.getStyleClass().add("row-actions");menu.setGraphic(IconFactory.compactIcon("actions",16));menu.setText("Actions");menu.setContentDisplay(ContentDisplay.LEFT);menu.setGraphicTextGap(6);menu.setTooltip(new Tooltip("Actions"));IconFactory.decorateActionMenu(menu);
            }
            private Row row() { return getTableView().getItems().get(getIndex()); }
            private void add(String name, String icon, javafx.event.EventHandler<javafx.event.ActionEvent> handler) { MenuItem item = new MenuItem(name, IconFactory.compactIcon(icon, 16)); item.setOnAction(handler); menu.getItems().add(item); }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                if(!empty && getIndex()>=0 && getIndex()<getTableView().getItems().size()){
                    Row current=getTableView().getItems().get(getIndex());
                    boolean waiting=isPendingApproval(current), approved=isApproved(current);
                    for(MenuItem mi:menu.getItems()){
                        if(mi.getText()==null)continue;
                        if(mi.getText().startsWith("Approve Return"))mi.setDisable(!waiting||!PermissionService.allowed("PURCHASE.APPROVE"));
                        else if(mi.getText().startsWith("Reject Return"))mi.setDisable(!waiting||!PermissionService.allowed("PURCHASE.APPROVE"));
                        else if(mi.getText().startsWith("Record Refund")){mi.setDisable(!approved);mi.setText(approved?"Record Refund":"Record Refund (Approval Required)");}
                        else if(mi.getText().startsWith("Cancel Return")||mi.getText().startsWith("Delete Return"))mi.setDisable(!canCancelOrDelete(current));
                    }
                }
                setGraphic(empty ? null : menu);
            }
        });
    }

    private void installRows() {
        table.setRowFactory(view -> {
            TableRow<Row> row = new TableRow<>(); row.setOnMouseClicked(e -> { if(e.getButton()!=javafx.scene.input.MouseButton.PRIMARY || e.getClickCount()!=1 || row.isEmpty() || RegisterUiSupport.isInteractiveTableTarget(e.getPickResult().getIntersectedNode(),row))return; Row clicked=row.getItem(); if(detailDrawer.isVisible() && selected==clicked)closeDetails(); else{table.getSelectionModel().select(clicked);showDetails(clicked);}e.consume(); });
            MenuItem add = new MenuItem("Add Purchase Return", IconFactory.compactIcon("add", 16)); add.setOnAction(e -> create());
            MenuItem edit = new MenuItem("Edit Reason", IconFactory.compactIcon("edit", 16)); edit.setOnAction(e -> { if (!row.isEmpty()) edit(row.getItem()); });
            MenuItem delete = new MenuItem("Delete Return", IconFactory.compactIcon("delete", 16)); delete.setOnAction(e -> { if (!row.isEmpty()) delete(row.getItem()); });
            ContextMenu menu = new ContextMenu(edit, delete); menu.setOnShowing(e->{Row current=row.isEmpty()?null:row.getItem();edit.setDisable(current==null||isCancelled(current));delete.setDisable(!canCancelOrDelete(current));}); IconFactory.decorateActionMenu(menu); row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu)); return row;
        });
    }

    @FXML private void load(){String selectedSupplier=supplier.getValue(),selectedStatus=status.getValue();LocalDate from=dpFrom.getValue(),to=dpTo.getValue();int requested=pageState.currentPage();org.example.util.OperationalUiSupport.showLoading(table,"Loading purchase returns…");UiTaskExecutor.submitLatest("purchase-returns-load",()->returnApi.page("PURCHASE RETURN",requested,PAGE_SIZE,search.getText(),selectedSupplier,selectedStatus,str(from),str(to)),this::applyPage,failure->{finishExplicitRefresh(false);org.example.util.OperationalUiSupport.showError(table,"Purchase returns could not load",failure);error(asException(failure));});}
    private void applyPage(ReturnApiClient.Page page){pageState.runApplying(()->{List<Row> loaded=new ArrayList<>();if(page!=null&&page.rows()!=null)for(ReturnApiClient.Summary r:page.rows())loaded.add(new Row(r.no(),r.date(),r.invoice(),r.party(),r.total(),r.refund(),safe(r.reason()),safe(r.status()),safe(r.refundStatus())));all=List.copyOf(loaded);pageState.apply(page==null?0:page.page(),page==null?0:page.totalPages(),page==null?0:page.totalRows());String selectedSupplier=supplier.getValue();org.example.util.PartySearchUi.preserveSelection(supplier,selectedSupplier,"All Suppliers");status.setItems(FXCollections.observableArrayList("All Status","PENDING APPROVAL","APPROVED","REJECTED","CANCELLED"));if(status.getValue()==null)status.setValue("All Status");table.getItems().setAll(all);if(all.isEmpty())org.example.util.OperationalUiSupport.showEmpty(table,"No purchase returns found","Adjust the filters or create a return from Purchase Register.");applyKpis(page==null?null:page.metrics());updatePageInfo();ScreenRefreshPolicy.markRefreshed("purchase-returns");finishExplicitRefresh(true);});}
    private void applyKpis(ReturnApiClient.Metrics m){if(m==null)return;total.setText(money(m.total()));count.setText(String.valueOf(m.count()));if(monthCount!=null)monthCount.setText(String.valueOf(m.monthCount()));refund.setText(money(m.refundAmount()));average.setText(money(m.average()));}
    private void updatePageInfo(){pageInfo.setText(pageState.rangeWithPageText(PAGE_SIZE,all.size(),"returns"));RegisterUiSupport.updatePageNavigation(pageState,btnPrevPage,btnNextPage);}
    @FXML private void filter(){if(suppressAutoFilter||pageState.isApplyingServerPage())return;pageState.reset();load();}
    @FXML private void previousPage(){if(pageState.previous())load();}
    @FXML private void nextPage(){if(pageState.next())load();}
    @FXML private void reset(){suppressAutoFilter=true;try{search.clear();supplier.setValue("All Suppliers");status.setValue("All Status");dpFrom.setValue(null);dpTo.setValue(null);}finally{suppressAutoFilter=false;}pageState.reset();load();}
    @FXML private void refreshWithFeedback(){explicitRefreshPending=true;if(btnRefreshReturns!=null){btnRefreshReturns.setDisable(true);btnRefreshReturns.setText("Refreshing...");}load();}
    private void finishExplicitRefresh(boolean success){if(!explicitRefreshPending)return;explicitRefreshPending=false;if(btnRefreshReturns!=null){btnRefreshReturns.setDisable(false);btnRefreshReturns.setText("Refresh");}if(success)org.example.util.ToastManager.info(table,"Refreshed","Purchase Returns is up to date.");}
    @Override public void onScreenShown(boolean reusedFromCache){org.example.util.OperationalUiSupport.focusWorkArea(table);load();}
    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("purchase-returns-");}

    @FXML private void create() { info("Create a purchase return from the Purchase Register so stock and supplier balances remain linked."); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml"); }
    private void configureDrawer() {if(detailDrawer==null)return;RegisterUiSupport.hideDrawer(detailDrawer,mainSplit,table);org.example.util.OperationalUiSupport.installEscapeClose(mainSplit,()->detailDrawer.isVisible(),this::closeDetails);decorateDrawerNode(detailDrawer);applyValueIcon(lblDetailNo,"return");applyValueIcon(lblDetailSupplier,"supplier");applyValueIcon(lblDetailDate,"calendar");applyValueIcon(lblDetailInvoice,"purchase");applyValueIcon(lblDetailAmount,"currency");applyValueIcon(lblDetailRefund,"payment");applyValueIcon(lblDetailReason,"document");}
    private void decorateDrawerNode(Node node){if(node instanceof Label l&&l.getGraphic()==null){String sem=drawerSemantic(l.getText());if(sem!=null){l.setGraphic(IconFactory.compactIcon(sem,14));l.setGraphicTextGap(6);l.getProperties().put("erp-icon-preserve",true);}}if(node instanceof ButtonBase b&&b.getGraphic()==null){String sem=drawerSemantic(b.getText());if(sem!=null){b.setGraphic(IconFactory.compactIcon(sem,14));b.setGraphicTextGap(6);b.getProperties().put("erp-icon-preserve",true);}}if(node instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())decorateDrawerNode(child);}
    private void applyValueIcon(Label l,String sem){if(l!=null&&l.getGraphic()==null){l.setGraphic(IconFactory.compactIcon(sem,15));l.setGraphicTextGap(7);l.getProperties().put("erp-icon-preserve",true);}}
    private String drawerSemantic(String value){String t=safe(value).toLowerCase(Locale.ROOT);if(t.contains("return"))return"return";if(t.contains("purchase")||t.contains("original"))return"purchase";if(t.contains("supplier"))return"supplier";if(t.contains("date"))return"calendar";if(t.contains("amount"))return"currency";if(t.contains("refund"))return"payment";if(t.contains("reason"))return"document";if(t.contains("status"))return"status";if(t.contains("pdf")||t.contains("print"))return"pdf";if(t.contains("email"))return"email";if(t.contains("detail"))return"view";if(t.contains("close"))return"cancel";return null;}
    private String returnSemantic(String value){String v=safe(value).toUpperCase(Locale.ROOT);if(v.contains("CANCEL")||v.contains("REJECT")||v.contains("FAIL"))return"cancel";if(v.contains("COMPLETE")||v.contains("APPROV")||v.contains("REFUND"))return"complete";if(v.contains("PARTIAL")||v.contains("PROGRESS"))return"refresh";return"reminder";}
    private String returnState(String value){String v=safe(value).toUpperCase(Locale.ROOT);if(v.contains("CANCEL")||v.contains("REJECT")||v.contains("FAIL"))return"danger";if(v.contains("COMPLETE")||v.contains("APPROV")||v.contains("REFUND"))return"success";if(v.contains("PARTIAL")||v.contains("PROGRESS"))return"info";return"warning";}
    private void showDetails(Row row){if(row==null)return;selected=row;RegisterUiSupport.showDrawer(detailDrawer,mainSplit,.8);lblDetailNo.setText(row.no());lblDetailSupplier.setText(row.supplier());lblDetailDate.setText(BusinessClock.formatDate(row.date()));lblDetailInvoice.setText(row.invoice());lblDetailAmount.setText(money(row.total()));lblDetailRefund.setText(money(row.refund()));lblDetailReason.setText(safe(row.reason()).isBlank()?"Not set":row.reason());lblDetailStatus.setText(row.status());lblDetailStatus.setGraphic(IconFactory.statusIcon(returnSemantic(row.status()),returnState(row.status())));lblDetailRefundStatus.setText(row.refundStatus());lblDetailRefundStatus.setGraphic(IconFactory.statusIcon(returnSemantic(row.refundStatus()),returnState(row.refundStatus())));if(btnRefundSelected!=null){btnRefundSelected.setDisable(!isApproved(row));btnRefundSelected.setTooltip(!isApproved(row)?new Tooltip("Refund/settlement can be recorded only after Admin approves the Return."):null);}updateSelectedActions(row);}
    @FXML private void closeDetails(){selected=null;RegisterUiSupport.hideDrawer(detailDrawer,mainSplit,table);}
    @FXML private void pdfSelected(){if(selected!=null)pdf(selected);}
    @FXML private void emailSelected(){if(selected!=null)email(selected);}
    @FXML private void originalSelected(){if(selected!=null)original(selected);}
    @FXML private void refundSelected(){if(selected!=null)recordRefund(selected);}
    @FXML private void editReasonSelected(){if(selected!=null)edit(selected);}
    @FXML private void approveSelected(){if(selected!=null)approveReturn(selected);}
    @FXML private void rejectSelected(){if(selected!=null)rejectReturn(selected);}
    private void updateSelectedActions(Row row){
        boolean waiting=isPendingApproval(row), allowed=PermissionService.allowed("PURCHASE.APPROVE");
        if(btnApproveSelected!=null){btnApproveSelected.setManaged(waiting);btnApproveSelected.setVisible(waiting);btnApproveSelected.setDisable(!allowed);}
        if(btnRejectSelected!=null){btnRejectSelected.setManaged(waiting);btnRejectSelected.setVisible(waiting);btnRejectSelected.setDisable(!allowed);}
        if(btnEditReasonSelected!=null)btnEditReasonSelected.setDisable(row==null||isCancelled(row));
    }
    private boolean canCancelOrDelete(Row row){return row!=null&&(isPendingApproval(row)||isApproved(row))&&row.refund()<=0.0001;}


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
    private void recordRefund(Row row) {
        if(row==null)return;
        if(!isApproved(row)){org.example.util.ModernDialog.warning(table,"Refund blocked","Return approval required","Refund/settlement can be recorded only after Admin approves the Return.");return;}
        ReturnRefundContext.select(row.no()); NavigationManager.getInstance().loadPage("/fxml/pages/ReturnRefund.fxml");
    }
    private void approveReturn(Row row){
        if(row==null||!isPendingApproval(row))return;
        if(!confirm("Approve "+row.no()+"?\n\nApproval posts the Return stock movement and starts the Return settlement due period."))return;
        UiTaskExecutor.submitSerial("purchase-return-approve-"+row.no(),()->{ReturnWorkflowService.approve(row.no());return true;},ignored->{
            ScreenRefreshPolicy.invalidate("purchase-returns");ScreenRefreshPolicy.invalidate("purchase-register");
            NotificationService.add(row.no()+" approved.");org.example.util.ToastManager.success(table,"Return approved",row.no()+" was approved successfully.");load();
        },failure->error(asException(failure)));
    }
    private void rejectReturn(Row row){
        if(row==null||!isPendingApproval(row))return;
        input("", "Reject Return", "Reason:").ifPresent(reason->UiTaskExecutor.submitSerial("purchase-return-reject-"+row.no(),()->{ReturnWorkflowService.reject(row.no(),reason);return true;},ignored->{
            ScreenRefreshPolicy.invalidate("purchase-returns");ScreenRefreshPolicy.invalidate("purchase-register");
            NotificationService.add(row.no()+" rejected.");org.example.util.ToastManager.success(table,"Return rejected",row.no()+" was rejected successfully.");load();
        },failure->error(asException(failure))));
    }
    private boolean isPendingApproval(Row row){return row!=null&&"PENDING APPROVAL".equalsIgnoreCase(safe(row.status()).trim());}
    private boolean isApproved(Row row){return row!=null&&"APPROVED".equalsIgnoreCase(safe(row.status()).trim());}
    private boolean isCancelled(Row row) { return row != null && "CANCELLED".equalsIgnoreCase(safe(row.status()).trim()); }
    private void cancel(Row row) { if (!confirm("Cancel " + row.no() + " and reverse its stock movement?")) return; UiTaskExecutor.submitSerial("purchase-return-cancel-"+row.no(),()->{ReturnWorkflowService.cancel(row.no(),false);return true;},ignored->{ScreenRefreshPolicy.invalidate("purchase-returns");ScreenRefreshPolicy.invalidate("purchase-register");NotificationService.add(row.no()+" cancelled.");org.example.util.ToastManager.success(table,"Return cancelled",row.no()+" was cancelled successfully.");load();},failure->error(asException(failure))); }
    private void delete(Row row) { if (!confirm("Delete " + row.no() + " from the Return Register?\n\nIt will disappear from normal UI, but the backend audit record will be retained as DELETED. Active return stock movement will be reversed safely.")) return; UiTaskExecutor.submitSerial("purchase-return-delete-"+row.no(),()->{ReturnWorkflowService.delete(row.no(),false);return true;},ignored->{ScreenRefreshPolicy.invalidate("purchase-returns");ScreenRefreshPolicy.invalidate("purchase-register");NotificationService.add(row.no()+" deleted from register; audit record retained.");org.example.util.ToastManager.success(table,"Return deleted",row.no()+" was removed from the register; the audit record was retained.");load();},failure->error(asException(failure))); }
    private void update(String no,String column,String value){if(!Set.of("reason","notes").contains(column))return;UiTaskExecutor.submitAction("purchase-return-update-"+no+"-"+column,()->{returnApi.update(no,column,value);return true;},ignored->{org.example.util.ToastManager.success(table,"Return updated",no+" "+column+" was updated.");load();},failure->error(asException(failure)));}

    @FXML private void export() {
        org.example.service.PermissionService.require("PURCHASE.EXPORT", "Export Purchase Returns");
        FileChooser chooser = new FileChooser(); chooser.setInitialFileName("Purchase_Returns.csv"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(table.getScene().getWindow()); if (file == null) return;
        String q=search.getText(),party=supplier.getValue(),state=status.getValue(),from=str(dpFrom.getValue()),to=str(dpTo.getValue());
        UiTaskExecutor.submitAction("purchase-returns-export",()->{List<ReturnApiClient.Summary> rows=returnApi.allFiltered("PURCHASE RETURN",q,party,state,from,to);try(PrintWriter out=new PrintWriter(file)){out.println("Return No,Date,Purchase No,Supplier,Total,Refund,Status,Refund Status");for(ReturnApiClient.Summary r:rows)out.printf("%s,%s,%s,%s,%.2f,%.2f,%s,%s%n",r.no(),r.date(),r.invoice(),csv(r.party()),r.total(),r.refund(),r.status(),r.refundStatus());}return rows.size();},count->info("Purchase Returns exported • "+count+" records."),failure->error(asException(failure)));
    }


    private LocalDate parse(String value) { try { LocalDate parsed = BusinessClock.parseDate(value); return parsed == null ? LocalDate.MIN : parsed; } catch (Exception e) { return LocalDate.MIN; } }
    private String safe(String value) { return value == null ? "" : value; }
    private String csv(String value) { String text=spreadsheetSafe(value); return '"' + text.replace("\"", "\"\"") + '"'; }
    private String spreadsheetSafe(String value) { String text=safe(value),t=text.stripLeading(); if(t.isEmpty())return text; char c=t.charAt(0); boolean numericNegative=c=='-'&&t.matches("-\\d+(?:\\.\\d+)?"); return c=='='||c=='+'||c=='@'||(c=='-'&&!numericNegative)?"'"+text:text; }
    private String money(double value) { return String.format("₹ %,.2f", value); }
    private boolean confirm(String text) { return org.example.util.ModernDialog.confirm(table, "Confirmation", "Are you sure?", text); }
    private void info(String value) { org.example.util.ToastManager.success(table, "Completed", value); }
    private void error(Exception e) { org.example.util.ModernDialog.error(table, "Operation failed", "Something went wrong", e.getMessage() == null ? "Operation failed" : e.getMessage()); }
    private void installKpiIcons(){setKpi(iconTotal,"return");setKpi(iconMonth,"calendar");setKpi(iconCount,"document");setKpi(iconRefund,"payment");setKpi(iconAverage,"currency");}
    private void setKpi(StackPane pane,String semantic){if(pane!=null)pane.getChildren().setAll(IconFactory.compactIcon(semantic,22));}

    private Exception asException(Throwable failure){return failure instanceof Exception e?e:new RuntimeException(failure);}
    private static String str(Object v){return v==null?"":String.valueOf(v);}

}
