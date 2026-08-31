package org.example.controller;

import org.example.util.BusinessClock;
import org.example.documentstudio.service.ExcelOutputService;

import org.example.util.OwnedDialog;
import org.example.util.OwnedTextInputDialog;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.api.support.SupportApiClient;
import org.example.config.ConfigManager;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.util.UiTaskExecutor;
import org.example.util.FxDebouncer;
import org.example.util.PerformanceMonitor;
import org.example.util.PlatformUiSupport;
import org.example.util.ScreenRefreshPolicy;
import org.example.service.*;
import org.example.util.IconFactory;
import org.example.util.TableSelectionSupport;
import org.example.util.SemanticTableCells;
import org.example.util.UiActionIcons;
import org.example.util.InvoicePaymentDetailsDialog;
import org.example.util.RegisterPageState;
import org.example.util.RegisterUiSupport;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

public class SalesListController implements ScreenLifecycle {
    @FXML private Label lblTotalSales,lblInvoiceCount,lblTodaySales,lblTodayCount,lblPending,lblPendingCount,lblOverdue,lblOverdueCount,lblDueSoon,lblDueSoonCount,lblEmailRate;
    @FXML private StackPane salesTitleIcon,salesHeaderSearchIcon,totalSalesIcon,todaySalesIcon,pendingSalesIcon,overdueSalesIcon,dueSoonIcon,emailRateIcon;
    @FXML private Button btnNewSale,btnSaveView,btnResetFilters,btnRefreshSales,btnExportExcel,btnExportPdf,btnPrintRegister;
    @FXML private Button btnAllDatesRange,btnTodayRange,btnYesterdayRange,btnSevenDaysRange,btnThirtyDaysRange,btnCustomRange,btnCloseDetails,btnApproveSale,btnRejectSale;
    @FXML private TextField txtSearch,txtInvoice,txtAmountFrom,txtAmountTo;
    @FXML private ComboBox<String> cmbCustomer,cmbPaymentStatus,cmbMailStatus,cmbWhatsappStatus,cmbInvoiceType,cmbDocumentStatus;
    @FXML private DatePicker dpFrom,dpTo;
    @FXML private ToggleButton btnAdvanced;
    @FXML private javafx.scene.layout.GridPane advancedFilters;
    @FXML private FlowPane activeFilterChips;
    @FXML private MenuButton savedViewsMenu;
    @FXML private TableView<Sales> tableSales;
    @FXML private TableColumn<Sales,String> colInvoice,colDate,colCustomer,colMobile,colGstin,colDue,colStatus,colPaymentStatus,colReturnStatus,colMail;
    @FXML private TableColumn<Sales,Double> colTotal,colPaid,colBalance;
    @FXML private TableColumn<Sales,Void> colAction;
    @FXML private ComboBox<Integer> cmbPageSize;
    @FXML private Label lblPageInfo,lblPageNumber,lblFooterTotal,lblFooterPaid,lblFooterBalance;
    @FXML private PieChart dueChart;
    @FXML private BarChart<Number,String> customerChart;
    @FXML private LineChart<String,Number> salesChart;
    @FXML private SplitPane mainSplit;
    @FXML private javafx.scene.layout.VBox detailDrawer,approvalActionBox;
    @FXML private Label lblDetailInvoice,lblDetailDate,lblDetailStatus,lblDetailCustomer,lblDetailContact,lblDetailAmount,lblDetailPaid,lblDetailBalance,lblDetailDue,lblDetailCharges,lblDetailGstAmount,lblDetailTotalCharges,lblDetailChargeTax,lblDetailGstType,lblDetailGstin,lblDetailBillingAddress,lblDetailDeliveryAddress,lblDetailTransporter,lblDetailDoorDelivery,lblDetailVehicle,lblDetailContactPerson,lblDetailContactMobile;
    @FXML private Label capInvoiceAmount,capPaidAmount,capBalance,capDueDate,capGstAmount,capTotalCharges,capChargeGst,capCharges,capBillingAddress,capDeliveryAddress,capGstType,capGstin,capTransporter,capVehicle,capContactPerson,capContactMobile;

    private final SalesService service=new SalesService();
    private final LookupService lookupService=new LookupService();
    private boolean explicitRefreshPending;
    private final SupportApiClient support=new SupportApiClient();
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private List<Sales> allSales=new ArrayList<>(),filteredSales=new ArrayList<>();
    private final FxDebouncer filterDebouncer=new FxDebouncer(java.time.Duration.ofMillis(220));
    private final RegisterPageState pageState = new RegisterPageState();
    private Sales selected;
    private boolean linkedRecordReloadInProgress;
    private boolean applyingSavedView;
    private String pendingSavedViewName;

    @FXML public void initialize(){
        configureColumns();configureFilters();configureActions();configurePaging();configureVisualIcons();configureDetailFieldIcons();refreshShortcutLabels();loadSavedViews();org.example.util.RegisterColumnPreferences.install(tableSales,"SALES_REGISTER");
        RegisterUiSupport.configureHeaderSearch(txtSearch,salesHeaderSearchIcon,"Search invoice, customer, mobile or GSTIN...");
        simplifyFilters();
        RegisterUiSupport.hideDrawer(detailDrawer,mainSplit,tableSales);
        org.example.util.OperationalUiSupport.installEscapeClose(mainSplit, () -> detailDrawer != null && detailDrawer.isVisible(), this::closeDetails);
        // One shared drawer interaction: first click opens, clicking the same row again closes,
        // and clicking a different row replaces the drawer contents immediately.
        tableSales.setRowFactory(view -> {
            TableRow<Sales> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY || event.getClickCount() != 1 || row.isEmpty() || RegisterUiSupport.isInteractiveTableTarget(event.getPickResult().getIntersectedNode(), row)) return;
                Sales clicked = row.getItem();
                if (detailDrawer.isVisible() && selected != null && selected.getId() == clicked.getId()) closeDetails();
                else { tableSales.getSelectionModel().select(clicked); showDetails(clicked); }
                event.consume();
            });
            return row;
        });
        txtSearch.textProperty().addListener((o,a,b)->{if(!applyingSavedView)filterDebouncer.submit(this::applyFilters);});
    }


    private void showPaymentDetails(Sales sale) {
        try {
            InvoicePaymentDetailsDialog.show(tableSales, support, "SALE", sale.getId(), sale.getInvoiceNo(),
                    "Customer", sale.getCustomer() == null ? "" : sale.getCustomer().getName(),
                    sale.getTotalAmount(), sale.getPaidAmount(), sale.getBalanceAmount());
        } catch (Exception exception) { error(exception); }
    }


    private void configureDetailFieldIcons(){
        detailIcon(capInvoiceAmount,"currency","sales-detail-icon-money"); detailIcon(capPaidAmount,"complete","sales-detail-icon-paid");
        detailIcon(capBalance,"balance","sales-detail-icon-balance"); detailIcon(capDueDate,"reminder","sales-detail-icon-date");
        detailIcon(capGstAmount,"tax","sales-detail-icon-tax"); detailIcon(capTotalCharges,"payment","sales-detail-icon-charge");
        detailIcon(capChargeGst,"tax","sales-detail-icon-tax"); detailIcon(capCharges,"document","sales-detail-icon-charge");
        detailIcon(capBillingAddress,"business","sales-detail-icon-address"); detailIcon(capDeliveryAddress,"purchase","sales-detail-icon-delivery");
        detailIcon(capGstType,"tax","sales-detail-icon-tax"); detailIcon(capGstin,"document","sales-detail-icon-tax");
        detailIcon(capTransporter,"purchase","sales-detail-icon-transport"); detailIcon(capVehicle,"bank","sales-detail-icon-vehicle");
        detailIcon(capContactPerson,"user","sales-detail-icon-person"); detailIcon(capContactMobile,"phone","sales-detail-icon-phone");
    }
    private void detailIcon(Label label,String semantic,String style){if(label==null)return;label.setGraphic(IconFactory.compactIcon(semantic,14));label.setGraphicTextGap(6);label.getStyleClass().addAll("sales-detail-caption",style);}

    private void configureVisualIcons(){
        setIcon(salesTitleIcon,"sale",22);
        setIcon(salesHeaderSearchIcon,"search",16);
        setIcon(totalSalesIcon,"payment",24);
        setIcon(todaySalesIcon,"sale",24);
        setIcon(pendingSalesIcon,"reminder",24);
        setIcon(overdueSalesIcon,"error",24);
        setIcon(dueSoonIcon,"calendar",24);
        setIcon(emailRateIcon,"email",24);
        setButtonIcon(btnNewSale,"sale");
        setButtonIcon(btnSaveView,"save");
        setButtonIcon(savedViewsMenu,"view");
        setButtonIcon(btnResetFilters,"reset");
        setButtonIcon(btnRefreshSales,"refresh");
        setButtonIcon(btnExportExcel,"excel");
        setButtonIcon(btnExportPdf,"pdf");
        setButtonIcon(btnPrintRegister,"print");
        setButtonIcon(btnAllDatesRange,"calendar");
        setButtonIcon(btnTodayRange,"calendar");
        setButtonIcon(btnYesterdayRange,"calendar");
        setButtonIcon(btnSevenDaysRange,"calendar");
        setButtonIcon(btnThirtyDaysRange,"calendar");
        setButtonIcon(btnCustomRange,"calendar"); setButtonIcon(btnCloseDetails,"close");
        decorateSalesDrawer();
    }

    private void decorateSalesDrawer(){
        // Keep compact identity icons on the top values and put the business
        // semantic colour on each field caption. This is easier to scan than
        // repeating an icon beside every value in the drawer.
        drawerValue(lblDetailInvoice,"document");
        drawerValue(lblDetailDate,"calendar");
        drawerValue(lblDetailCustomer,"customer");
        drawerValue(lblDetailContact,"user");
        decorateSalesDrawerCaptions(detailDrawer);
    }
    private void decorateSalesDrawerCaptions(Node node){
        if(node instanceof Label label && label.getGraphic()==null){
            String semantic=salesDrawerCaptionSemantic(label.getText());
            if(semantic!=null){
                label.setGraphic(IconFactory.compactIcon(semantic,14));
                label.setGraphicTextGap(6);
                label.getStyleClass().add("erp-drawer-caption");
                label.getProperties().put("erp-icon-preserve",true);
            }
        }
        if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())decorateSalesDrawerCaptions(child);
    }
    private String salesDrawerCaptionSemantic(String text){
        String value=safe(text).trim().toLowerCase(java.util.Locale.ROOT);
        return switch(value){
            case "invoice details" -> "document";
            case "customer" -> "customer";
            case "invoice amount", "total charges", "charges" -> "currency";
            case "paid amount" -> "payment";
            case "balance" -> "balance";
            case "due date" -> "calendar";
            case "gst amount", "charge gst", "gst type", "gstin", "tax & transport" -> "tax";
            case "billing address", "delivery address" -> "location";
            case "transporter", "vehicle no." -> "delivery";
            case "contact person" -> "user";
            case "contact mobile" -> "phone";
            default -> null;
        };
    }
    private void drawerValue(Label label,String semantic){
        if(label==null)return;
        label.setGraphic(IconFactory.compactIcon(semantic,14));
        label.setGraphicTextGap(7);
        label.getStyleClass().add("erp-drawer-value");
        label.getProperties().put("erp-icon-preserve",true);
    }


    private void setIcon(StackPane holder,String semantic,int size){
        if(holder==null)return;
        holder.getChildren().setAll(IconFactory.icon(semantic,size));
    }

    private void setButtonIcon(ButtonBase button,String semantic){
        if(button==null)return;
        UiActionIcons.apply(button, semantic);
    }

    private void simplifyFilters(){
        hide(btnAdvanced);hide(btnCustomRange);
        if(advancedFilters!=null){advancedFilters.setVisible(false);advancedFilters.setManaged(false);}
    }
    private void place(Node control,int column){Node box=control.getParent();javafx.scene.layout.GridPane.setRowIndex(box,0);javafx.scene.layout.GridPane.setColumnIndex(box,column);}
    private void hide(Node node){if(node==null)return;Node target=node.getParent() instanceof javafx.scene.layout.VBox?node.getParent():node;target.setVisible(false);target.setManaged(false);}

    private void configureColumns(){
        colInvoice.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().getInvoiceNo()));
        colDate.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().getInvoiceDate().format(BusinessClock.dateFormatter())));
        colCustomer.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().getCustomer().getName()));
        colMobile.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(safe(v.getValue().getCustomer().getPhone())));
        colGstin.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(safe(v.getValue().getCustomer().getGstin())));
        colTotal.setCellValueFactory(v->new javafx.beans.property.SimpleDoubleProperty(v.getValue().getTotalAmount()).asObject());
        colPaid.setCellValueFactory(v->new javafx.beans.property.SimpleDoubleProperty(v.getValue().getPaidAmount()).asObject());
        colBalance.setCellValueFactory(v->new javafx.beans.property.SimpleDoubleProperty(v.getValue().getBalanceAmount()).asObject());
        colDue.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(dueLabel(v.getValue())));
        colStatus.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(documentStatus(v.getValue())));
        colPaymentStatus.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(paymentStatusDisplay(v.getValue())));
        colMail.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().isEmailSent()?"Sent":"Not Sent"));
        colTotal.setCellFactory(x->totalMoneyCell());
        colPaid.setCellFactory(x->paidMoneyCell());
        colBalance.setCellFactory(x->balanceMoneyCell());
        colStatus.setCellFactory(x->SemanticTableCells.status("document"));
        colPaymentStatus.setCellFactory(x->SemanticTableCells.status("payment"));
        if(colReturnStatus!=null){colReturnStatus.setCellValueFactory(v->new SimpleStringProperty(v.getValue()==null?"N/A":v.getValue().getReturnStatus()));colReturnStatus.setCellFactory(x->SemanticTableCells.status("return"));}
        colMail.setCellFactory(x->SemanticTableCells.status("email"));
        colDue.setCellFactory(x->SemanticTableCells.dueDate());
        tableSales.setPlaceholder(new Label("No sales invoices match the selected filters"));
    }
private TableCell<Sales,Double> moneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);}};}
    private TableCell<Sales,Double> totalMoneyCell(){return coloredMoneyCell("register-amount-total","register-amount-total");}
    private TableCell<Sales,Double> balanceMoneyCell(){return coloredMoneyCell("register-balance-open","register-balance-settled");}
    private TableCell<Sales,Double> coloredMoneyCell(String positiveClass,String zeroClass){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);getStyleClass().removeAll("register-amount-total","register-balance-open","register-balance-settled");if(!e&&v!=null){String style=v>.009?positiveClass:zeroClass;if(style!=null)getStyleClass().add(style);}}};}
    private TableCell<Sales,Double> paidMoneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);getStyleClass().removeAll("register-paid-positive","register-paid-zero");if(!e&&v!=null)getStyleClass().add(v>.009?"register-paid-positive":"register-paid-zero");}};}
    private TableCell<Sales,String> statusCell(String semantic){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("pill-success","pill-warning","pill-danger","pill-neutral");if(!e&&v!=null){boolean returned=v.equalsIgnoreCase("RETURNED"),partialReturn=v.equalsIgnoreCase("PARTIALLY RETURNED");boolean good=v.equalsIgnoreCase("COMPLETED")||v.equalsIgnoreCase("PAID")||v.equalsIgnoreCase("SENT")||returned;boolean pending=v.equalsIgnoreCase("IN PROGRESS")||v.equalsIgnoreCase("PARTIAL")||v.equalsIgnoreCase("PENDING")||v.equalsIgnoreCase("PENDING APPROVAL")||partialReturn;getStyleClass().add(good?"pill-success":pending?"pill-warning":"pill-danger");String icon = returned||partialReturn ? "return" : (good ? semantic : (pending ? ("status".equals(semantic)?"reminder":semantic) : "error"));setGraphic(IconFactory.compactIcon(icon,15));}}};}
    private String documentStatus(Sales sale){
        String stored=safe(sale==null?null:sale.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
        return stored.isBlank()?"PENDING APPROVAL":stored;
    }
    private String paymentStatusDisplay(Sales sale){
        if(sale==null)return "";
        String status=safe(sale.getPaymentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
        if("RETURN APPROVAL PENDING".equals(status))return "Return Pending Approval";
        if("RETURN PAID".equals(status))return "Return Paid";
        if("RETURN PARTIAL".equals(status))return "Return Partial";
        if("RETURN PENDING".equals(status))return "Return Pending";
        return status;
    }

    private void configureFilters(){
        cmbPaymentStatus.getItems().setAll("All","PENDING","PARTIAL","PAID","OVERDUE");cmbPaymentStatus.setValue("All");
        cmbMailStatus.getItems().setAll("All","Sent","Not Sent");cmbMailStatus.setValue("All");
        cmbWhatsappStatus.getItems().setAll("All","Sent","Not Sent");cmbWhatsappStatus.setValue("All");
        cmbInvoiceType.getItems().setAll("All","TAX INVOICE","PROFORMA","CASH MEMO");cmbInvoiceType.setValue("All");
        cmbDocumentStatus.getItems().setAll("All","DRAFT","PENDING APPROVAL","APPROVED","REJECTED","CANCELLED");cmbDocumentStatus.setValue("All");
        org.example.util.PartySearchUi.install(cmbCustomer,"CUSTOMER","All customers","sales-register-customer-search");
        dpFrom.setValue(BusinessClock.today().minusMonths(6));
        dpTo.setValue(BusinessClock.today());
        dpFrom.setPromptText("From date");
        dpTo.setPromptText("To date");
        for (ComboBox<String> box : List.of(cmbCustomer,cmbPaymentStatus,cmbMailStatus,cmbWhatsappStatus,cmbInvoiceType,cmbDocumentStatus))
            box.valueProperty().addListener((o,a,b)->{if(!applyingSavedView && !org.example.util.PartySearchUi.isInternalUpdate(box))applyFilters();});
        dpFrom.valueProperty().addListener((o,a,b)->applyFilters());
        dpTo.valueProperty().addListener((o,a,b)->applyFilters());
        txtInvoice.textProperty().addListener((o,a,b)->{if(!applyingSavedView)filterDebouncer.submit(this::applyFilters);});
        txtAmountFrom.textProperty().addListener((o,a,b)->{if(!applyingSavedView)filterDebouncer.submit(this::applyFilters);});
        txtAmountTo.textProperty().addListener((o,a,b)->{if(!applyingSavedView)filterDebouncer.submit(this::applyFilters);});
    }

    private void configurePaging(){cmbPageSize.getItems().setAll(10,25,50,100);cmbPageSize.setValue(25);cmbPageSize.valueProperty().addListener((o,a,b)->{pageState.reset();reloadPage();});}
    private void configureActions(){
        colAction.setCellFactory(c -> new TableCell<>() {
            final MenuButton menu = new MenuButton();
            final MenuItem edit;
            final MenuItem payment;
            final MenuItem createReturn;
            final MenuItem cancel;
            final MenuItem delete;
            {
                menu.getProperties().put("erp.icon.semantic", "actions");
                menu.setGraphic(IconFactory.compactIcon("actions", 15));
                add("Sale Invoice", "pdf", e -> openSaleInvoicePdf(row()));
                add("View Sale", "view", e -> viewSale(row()));
                add("Activity Timeline", "history", e -> org.example.util.ActivityTimelineDialog.show(tableSales,"SALE",row().getId(),row().getInvoiceNo()));
                edit = add("Edit Sale", "edit", e -> edit(row()));
                add("Duplicate Sale", "copy", e -> duplicate(row()));
                add("Print / Download PDF", "print", e -> openPdf(row()));
                add("View / Download Excel", "excel", e -> openExcel(row()));
                add("Send Email", "email", e -> sendEmail(row()));
                add("Send WhatsApp", "whatsapp", e -> sendWhatsapp(row()));
                payment = add("View / Record Payments", "payment", e -> openPayment(row()));
                createReturn = add("Create Sales Return", "return", e -> createReturn(row()));
                cancel = add("Cancel Sale", "cancel", e -> cancelSale(row()));
                delete = add("Delete Sale", "delete", e -> delete(row()));
                delete.getStyleClass().add("danger-menu-item");
                menu.setOnShowing(e -> updateActionAvailability());
                menu.getStyleClass().add("row-actions");
                menu.setGraphic(IconFactory.compactIcon("actions",16));
                menu.setText("Actions");
                menu.setContentDisplay(ContentDisplay.LEFT);
                menu.setGraphicTextGap(6);
                menu.setTooltip(new Tooltip("Actions"));IconFactory.decorateActionMenu(menu);
            }
            private void updateActionAvailability(){
                Sales current = getTableRow()==null ? null : getTableRow().getItem();
                if(current==null){
                    edit.setDisable(true);payment.setDisable(true);createReturn.setDisable(true);cancel.setDisable(true);delete.setDisable(true);return;
                }
                String status=safe(current.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
                boolean inactive="CANCELLED".equals(status)||"DELETED".equals(status);
                boolean locked=isFinanciallyLocked(current);
                edit.setDisable(inactive);
                payment.setDisable(inactive||isApprovalLocked(current));
                createReturn.setDisable(!isReturnEligible(current));
                cancel.setDisable(locked||inactive);
                delete.setDisable(locked||"DELETED".equals(status));
                // Cancel/Delete stay visible after payment so users can understand the
                // lifecycle rule; disabled state prevents the unsafe operation.
                cancel.setVisible(true);
                delete.setVisible(true);
            }
            private Sales row(){
                Sales value=getTableRow()==null?null:getTableRow().getItem();
                if(value==null)throw new IllegalStateException("This sales row is no longer available. Refresh the register and try again.");
                return value;
            }
            private MenuItem add(String text,String icon,javafx.event.EventHandler<ActionEvent> handler){
                MenuItem item=new MenuItem(text);
                item.getProperties().put("erp.icon.semantic",icon);
                item.setGraphic(IconFactory.compactIcon(icon,16));
                item.setOnAction(event->{try{handler.handle(event);}catch(Throwable failure){error(failure);}});
                menu.getItems().add(item);
                return item;
            }
            @Override protected void updateItem(Void value,boolean empty){
                super.updateItem(value,empty);
                setGraphic(empty?null:menu);
                setAlignment(Pos.CENTER);
            }
        });
    }

    @FXML public void refresh(){reloadPage();}
    @FXML private void refreshWithFeedback(){
        explicitRefreshPending=true;
        if(btnRefreshSales!=null){btnRefreshSales.setDisable(true);btnRefreshSales.setText("Refreshing...");}
        reloadPage();
    }
    private void finishExplicitRefresh(boolean success){
        if(!explicitRefreshPending)return;
        explicitRefreshPending=false;
        if(btnRefreshSales!=null){btnRefreshSales.setDisable(false);btnRefreshSales.setText("Refresh");}
        if(success)org.example.util.ToastManager.info(tableSales,"Refreshed","Sales Register is up to date.");
    }
    private void reloadPage(){
        int requestedPage=pageState.currentPage(),size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue();
        String customer=cmbCustomer.getValue();if(customer!=null&&customer.startsWith("All"))customer="";
        String payment=cmbPaymentStatus.getValue(),due="All",mail=cmbMailStatus.getValue(),whatsapp=cmbWhatsappStatus.getValue(),invoiceType=cmbInvoiceType.getValue(),documentStatus=cmbDocumentStatus.getValue();
        Double min=parseOptionalAmount(txtAmountFrom.getText()),max=parseOptionalAmount(txtAmountTo.getText());
        String selectedCustomer=customer;
        org.example.util.OperationalUiSupport.showLoading(tableSales,"Loading sales invoices…");
        UiTaskExecutor.submitLatest("sales-register-load",()->service.page(requestedPage,size,txtSearch.getText(),txtInvoice.getText(),selectedCustomer,dpFrom.getValue(),dpTo.getValue(),payment,due,mail,whatsapp,invoiceType,documentStatus,min,max),this::applyPage,failure->{pendingSavedViewName=null;finishExplicitRefresh(false);org.example.util.OperationalUiSupport.showError(tableSales,"Sales register could not load",failure);error(failure);});
    }
    private void applyPage(org.example.api.operations.OperationsApiClient.SalesPage loaded){
        pageState.runApplying(() -> {
            long started=System.nanoTime();allSales=new ArrayList<>(loaded.rows()==null?List.of():loaded.rows());filteredSales=allSales;pageState.apply(loaded.page(),loaded.totalPages(),loaded.totalRows());
            String selectedCustomer=cmbCustomer.getValue();org.example.util.PartySearchUi.preserveSelection(cmbCustomer,selectedCustomer,"All customers");
            renderPage();if(allSales.isEmpty())org.example.util.OperationalUiSupport.showEmpty(tableSales,"No sales invoices found","Adjust the filters or create a new Sale.");applyMetrics(loaded.metrics());if(loaded.metrics()!=null)lblInvoiceCount.setText(loaded.metrics().invoiceCount()+" active • "+loaded.totalRows()+" records");applyFooter(loaded.filteredTotals());renderChips();openLinkedRecordIfRequested();if(!PlatformUiSupport.isMac())javafx.application.Platform.runLater(()->updateCharts(loaded.metrics()));ScreenRefreshPolicy.markRefreshed("sales-register");finishExplicitRefresh(true);notifyAppliedSavedView();
            long ms=(System.nanoTime()-started)/1_000_000L;if(ms>=20)PerformanceMonitor.event("controller-phase","sales-register-page-apply | "+ms+" ms | rows="+allSales.size()+" | total="+pageState.totalRows());
        });
    }
    private void openLinkedRecordIfRequested(){
        LinkedRecordContext.Target target=LinkedRecordContext.peek();
        if(target==null||!"SALE".equals(target.module()))return;
        Sales sale=allSales.stream().filter(x->(target.recordId()!=null&&x.getId()==target.recordId())||(!target.documentNo().isBlank()&&target.documentNo().equalsIgnoreCase(safe(x.getInvoiceNo())))).findFirst().orElse(null);
        if(sale!=null){
            LinkedRecordContext.consume("SALE");linkedRecordReloadInProgress=false;
            tableSales.getSelectionModel().select(sale);tableSales.scrollTo(sale);tableSales.requestFocus();
            org.example.navigation.DeepLinkSupport.pulse(tableSales);org.example.navigation.DeepLinkSupport.highlight(tableSales,sale);
            showDetails(sale);PerformanceMonitor.event("linked-navigation","SALE -> "+sale.getInvoiceNo()+" | exact-row-highlight | source="+target.source());return;
        }
        if(target.documentNo().isBlank()){LinkedRecordContext.consume("SALE");warning("The linked Sale is not on the current result page. Use Search to locate it.");return;}
        if(linkedRecordReloadInProgress){linkedRecordReloadInProgress=false;LinkedRecordContext.consume("SALE");warning("The linked Sale could not be placed in the current register view: "+target.documentNo());return;}
        linkedRecordReloadInProgress=true;
        UiTaskExecutor.submitLatest("sales-linked-record",()->service.getByInvoice(target.documentNo()),found->{
            if(found==null){linkedRecordReloadInProgress=false;LinkedRecordContext.consume("SALE");warning("The linked Sale is no longer available: "+target.documentNo());return;}
            // Deep links temporarily narrow the register to the exact invoice so the row can be selected and highlighted.
            txtSearch.clear();txtInvoice.setText(found.getInvoiceNo());txtAmountFrom.clear();txtAmountTo.clear();cmbCustomer.setValue("All customers");cmbPaymentStatus.setValue("All");cmbMailStatus.setValue("All");cmbWhatsappStatus.setValue("All");cmbInvoiceType.setValue("All");cmbDocumentStatus.setValue("All");
            LocalDate invoiceDate=found.getInvoiceDate();if(invoiceDate!=null){dpFrom.setValue(invoiceDate);dpTo.setValue(invoiceDate);}
            pageState.reset();reloadPage();
        },failure->{linkedRecordReloadInProgress=false;LinkedRecordContext.consume("SALE");error(failure);});
    }
    @FXML public void applyFilters(){if(pageState.isApplyingServerPage()||applyingSavedView)return;pageState.reset();renderChips();reloadPage();}
    private void renderPage(){tableSales.setItems(FXCollections.observableArrayList(allSales));int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue();RegisterUiSupport.updatePageLabels(pageState,lblPageInfo,lblPageNumber,size,allSales.size(),"entries");if(pageState.totalRows()==0)lblPageInfo.setText("No entries");}
    @FXML private void firstPage(){if(pageState.first())reloadPage();}@FXML private void previousPage(){if(pageState.previous())reloadPage();}@FXML private void nextPage(){if(pageState.next())reloadPage();}@FXML private void lastPage(){if(pageState.last())reloadPage();}
    private void applyMetrics(org.example.api.operations.OperationsApiClient.SalesMetrics m){if(m==null)return;lblTotalSales.setText(money(m.totalSales()));lblInvoiceCount.setText(m.invoiceCount()+" invoices");lblTodaySales.setText(money(m.todaySales()));lblTodayCount.setText(m.todayCount()+" invoices");lblPending.setText(money(m.pendingBalance()));lblPendingCount.setText(m.pendingCount()+" invoices");lblOverdue.setText(money(m.overdueBalance()));lblOverdueCount.setText(m.overdueCount()+" invoices");lblDueSoon.setText(money(m.dueSoonBalance()));lblDueSoonCount.setText(m.dueSoonCount()+" invoices");lblEmailRate.setText(Math.round(m.emailRate())+"%");}
    private boolean isActiveFinancialDocument(Sales sale){String s=safe(sale.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);return !"CANCELLED".equals(s)&&!"DELETED".equals(s);}
    private double sum(List<Sales> list,java.util.function.ToDoubleFunction<Sales> f){return list.stream().mapToDouble(f).sum();}
    private void applyFooter(org.example.api.operations.OperationsApiClient.RegisterTotals totals){if(totals==null)return;lblFooterTotal.setText(money(totals.total()));lblFooterPaid.setText(money(totals.paid()));lblFooterBalance.setText(money(totals.balance()));}
    private void updateCharts(org.example.api.operations.OperationsApiClient.SalesMetrics m){if(m==null||PlatformUiSupport.isMac()||dueChart==null||customerChart==null||salesChart==null)return;dueChart.getData().setAll((m.dueBuckets()==null?List.<org.example.api.operations.OperationsApiClient.MetricPoint>of():m.dueBuckets()).stream().filter(e->e.value()>0).map(e->new PieChart.Data(e.label(),e.value())).toList());XYChart.Series<Number,String> cs=new XYChart.Series<>();for(var e:m.topCustomers()==null?List.<org.example.api.operations.OperationsApiClient.MetricPoint>of():m.topCustomers())cs.getData().add(new XYChart.Data<>(e.value(),e.label()));customerChart.getData().setAll(cs);XYChart.Series<String,Number> ss=new XYChart.Series<>();for(var e:m.monthlySales()==null?List.<org.example.api.operations.OperationsApiClient.MetricPoint>of():m.monthlySales())ss.getData().add(new XYChart.Data<>(e.label(),e.value()));salesChart.getData().setAll(ss);}
    private Double parseOptionalAmount(String text){String v=safe(text).replace(",","").replace("₹","").trim();if(v.isBlank())return null;try{return Double.parseDouble(v);}catch(Exception ignored){return null;}}

    @Override public void onScreenShown(boolean reusedFromCache){
        refreshShortcutLabels();
        org.example.util.OperationalUiSupport.focusWorkArea(tableSales);
        if (ImportViewContext.consume("Sales")) {
            dpFrom.setValue(BusinessClock.today().minusYears(20));
            dpTo.setValue(BusinessClock.today());
            pageState.reset();
            reloadPage();
            return;
        }
        loadSavedViews();
        if(reusedFromCache || allSales.isEmpty() || ScreenRefreshPolicy.shouldRefresh("sales-register", ScreenRefreshPolicy.Mode.WHEN_STALE, java.time.Duration.ofSeconds(60))) refresh();
    }

    private void refreshShortcutLabels(){
        org.example.shortcut.ShortcutRegistry.bindLabel(btnNewSale, org.example.shortcut.ShortcutRegistry.Action.NEW_SALE, "New Sale");
    }
    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("sales-");filterDebouncer.cancel();}

    private void showDetails(Sales sale){
        selected=sale;
        RegisterUiSupport.showDrawer(detailDrawer,mainSplit,.76);
        lblDetailInvoice.setText(sale.getInvoiceNo());
        lblDetailDate.setText(sale.getInvoiceDate().format(BusinessClock.dateFormatter()));
        lblDetailStatus.setText(documentStatus(sale));
        lblDetailCustomer.setText(sale.getCustomer()==null?"Unknown Customer":safe(sale.getCustomer().getName()));
        lblDetailContact.setText(sale.getCustomer()==null?"":safe(sale.getCustomer().getPhone())+"\n"+safe(sale.getCustomer().getEmail())+"\n"+safe(sale.getCustomer().getGstin()));
        lblDetailAmount.setText(money(sale.getTotalAmount()));
        lblDetailPaid.setText(money(sale.getPaidAmount()));
        lblDetailBalance.setText(money(sale.getBalanceAmount()));
        lblDetailDue.setText(sale.getDueDate()==null?"Not set":sale.getDueDate().format(BusinessClock.dateFormatter())+" • "+dueLabel(sale));
        if (lblDetailGstAmount != null) lblDetailGstAmount.setText(money(sale.getGstAmount()));
        if (lblDetailTotalCharges != null) lblDetailTotalCharges.setText(money(sale.getChargesAmount()));
        if (lblDetailChargeTax != null) lblDetailChargeTax.setText(money(sale.getChargesTaxAmount()));
        if (lblDetailCharges != null) {
            var charges = sale.getCharges();
            lblDetailCharges.setText(charges.isEmpty() ? "Not Applicable" : charges.stream()
                    .map(charge -> charge.getChargeType() + " • " + money(charge.getAmount())
                            + (charge.isTaxable() ? " (GST " + String.format(java.util.Locale.ROOT,"%.2f%%",charge.getGstPercent()) + ")" : ""))
                    .collect(java.util.stream.Collectors.joining("\n")));
        }
        if (lblDetailGstType != null) lblDetailGstType.setText(safe(sale.getGstType()).isBlank() ? "Not Applicable" : sale.getGstType());
        if (lblDetailGstin != null) lblDetailGstin.setText(safe(sale.getGstin()).isBlank() ? "Not Applicable" : sale.getGstin());
        if (lblDetailBillingAddress != null) {
            String billing = safe(sale.getBillingAddress());
            if (billing.isBlank() && sale.getCustomer() != null) billing = safe(sale.getCustomer().getAddress());
            lblDetailBillingAddress.setText(billing.isBlank() ? "Not set" : billing);
        }
        if (lblDetailDeliveryAddress != null) {
            String delivery = safe(sale.getDeliveryAddress());
            if (delivery.isBlank() && sale.isSameAsBilling()) {
                delivery = safe(sale.getBillingAddress());
                if (delivery.isBlank() && sale.getCustomer() != null) delivery = safe(sale.getCustomer().getAddress());
            }
            lblDetailDeliveryAddress.setText(delivery.isBlank() ? "Not set" : delivery);
        }
        if (lblDetailTransporter != null) lblDetailTransporter.setText(safe(sale.getTransporter()).isBlank() ? "Not Applicable" : sale.getTransporter());
        if (lblDetailDoorDelivery != null) lblDetailDoorDelivery.setText(safe(sale.getDoorDelivery()).isBlank() ? "Not Applicable" : sale.getDoorDelivery());
        if (lblDetailVehicle != null) lblDetailVehicle.setText(safe(sale.getVehicleNumber()).isBlank() ? "Not Applicable" : sale.getVehicleNumber());
        if (lblDetailContactPerson != null) lblDetailContactPerson.setText(safe(sale.getContactPerson()).isBlank() ? "Not Applicable" : sale.getContactPerson());
        if (lblDetailContactMobile != null) lblDetailContactMobile.setText(safe(sale.getContactPersonMobile()).isBlank() ? "Not Applicable" : sale.getContactPersonMobile());
        boolean pendingApproval="PENDING APPROVAL".equalsIgnoreCase(safe(sale.getDocumentStatus()));
        boolean admin=SessionService.isAdmin();
        if(approvalActionBox!=null){approvalActionBox.setManaged(pendingApproval&&admin);approvalActionBox.setVisible(pendingApproval&&admin);}
        if(btnApproveSale!=null)btnApproveSale.setDisable(!pendingApproval||!admin);
        if(btnRejectSale!=null)btnRejectSale.setDisable(!pendingApproval||!admin);
    }
    @FXML private void closeDetails(){selected=null;RegisterUiSupport.hideDrawer(detailDrawer,mainSplit,tableSales);}
    private Sales requireSelected(){if(selected==null){warning("Select an invoice first.");return null;}return selected;}
    @FXML private void emailSelected(){Sales s=requireSelected();if(s!=null)sendEmail(s);}@FXML private void whatsappSelected(){Sales s=requireSelected();if(s!=null)sendWhatsapp(s);}@FXML private void editSelectedSale(){Sales s=requireSelected();if(s!=null)edit(s);}@FXML private void recordSelectedPayment(){Sales s=requireSelected();if(s!=null)openPayment(s);}@FXML private void excelSelected(){Sales s=requireSelected();if(s!=null)openExcel(s);}
    @FXML private void approveSelectedSale(){Sales s=requireSelected();if(s==null)return;try{service.approve(s.getInvoiceNo());NotificationService.add("Sale "+s.getInvoiceNo()+" approved.");refresh();}catch(Exception e){error(e);}}
    @FXML private void rejectSelectedSale(){Sales s=requireSelected();if(s==null)return;OwnedTextInputDialog dialog=new OwnedTextInputDialog();dialog.setTitle("Reject Sale");dialog.setHeaderText("Reject "+s.getInvoiceNo());dialog.setContentText("Reason:");dialog.showAndWait().map(String::trim).filter(v->!v.isBlank()).ifPresent(reason->{try{service.reject(s.getInvoiceNo(),reason);NotificationService.add("Sale "+s.getInvoiceNo()+" rejected.");refresh();}catch(Exception e){error(e);}});}
    private void openPayment(Sales s){
        if(s!=null&&Set.of("PENDING APPROVAL","REJECTED").contains(safe(s.getDocumentStatus()).trim().toUpperCase(Locale.ROOT))){warning("Admin approval is required before recording a payment for this Sale.");return;}
        if (s == null || safe(s.getInvoiceNo()).isBlank()) {
            warning("Unable to open Record Payment because the selected invoice is not available.");
            return;
        }
        SalesScreenContext.select(s.getInvoiceNo());
        navigateSalesPage("/fxml/pages/RecordPayment.fxml", "Record Payment");
    }
    private void navigateSalesPage(String fxml, String screenName) {
        if (!NavigationManager.navigateOrReport(fxml)) {
            warning(screenName + " could not be opened. Please try again.");
        }
    }

    @FXML private void showAllDates(){dpFrom.setValue(null);dpTo.setValue(null);applyFilters();}
    @FXML private void showToday(){applyDateRange(BusinessClock.today(),BusinessClock.today());}
    @FXML private void showYesterday(){LocalDate day=BusinessClock.today().minusDays(1);applyDateRange(day,day);}
    @FXML private void showSevenDays(){applyDateRange(BusinessClock.today().minusDays(6),BusinessClock.today());}
    @FXML private void showThirtyDays(){applyDateRange(BusinessClock.today().minusDays(29),BusinessClock.today());}
    @FXML private void focusCustomRange(){dpFrom.requestFocus();}
    private void applyDateRange(LocalDate from,LocalDate to){dpFrom.setValue(from);dpTo.setValue(to);applyFilters();}

    @FXML private void toggleAdvanced(){advancedFilters.setManaged(btnAdvanced.isSelected());advancedFilters.setVisible(btnAdvanced.isSelected());}
    @FXML private void resetFilters(){txtSearch.clear();txtInvoice.clear();txtAmountFrom.clear();txtAmountTo.clear();dpFrom.setValue(BusinessClock.today().minusMonths(6));dpTo.setValue(BusinessClock.today());cmbCustomer.setValue("All customers");cmbPaymentStatus.setValue("All");cmbMailStatus.setValue("All");cmbWhatsappStatus.setValue("All");cmbInvoiceType.setValue("All");cmbDocumentStatus.setValue("All");applyFilters();}
    private void renderChips(){activeFilterChips.getChildren().clear();addChip("From",dpFrom.getValue());addChip("To",dpTo.getValue());addChip("Payment",nonAll(cmbPaymentStatus));addChip("Email",nonAll(cmbMailStatus));addChip("WhatsApp",nonAll(cmbWhatsappStatus));addChip("Document",nonAll(cmbDocumentStatus));}
    private Object nonAll(ComboBox<String>b){return b.getValue()==null||b.getValue().equals("All")?null:b.getValue();}private void addChip(String name,Object value){if(value==null)return;Label chip=new Label(name+": "+value);chip.getStyleClass().add("filter-chip");activeFilterChips.getChildren().add(chip);}

    @FXML private void saveCurrentView(){
        TextInputDialog d=new OwnedTextInputDialog();d.setTitle("Save Filter View");d.setHeaderText("Save the current sales filters");d.setContentText("View name:");
        d.showAndWait().map(String::trim).filter(x->!x.isBlank()).ifPresent(name->{
            String data=String.join("|",safe(txtInvoice.getText()),safe(cmbCustomer.getValue()),str(dpFrom.getValue()),str(dpTo.getValue()),safe(cmbPaymentStatus.getValue()),"All",safe(cmbMailStatus.getValue()),safe(cmbWhatsappStatus.getValue()),safe(cmbInvoiceType.getValue()),safe(txtAmountFrom.getText()),safe(txtAmountTo.getText()),safe(cmbDocumentStatus.getValue()),safe(txtSearch.getText()));
            Integer uid=SessionService.current()==null?null:SessionService.current().getId();
            UiTaskExecutor.submitAction("sales-save-view",()->{support.saveView(uid,"SALES_REGISTER",name,data);return true;},ignored->{loadSavedViews();org.example.util.ToastManager.success(tableSales,"Saved view created",name);},failure->error(failure instanceof Exception e?e:new RuntimeException(failure)));
        });
    }
    private MenuItem savedViewPlaceholder(){MenuItem i=new MenuItem("No saved views");i.getStyleClass().add("register-saved-view-item");i.setDisable(true);return i;}
    private void loadSavedViews(){
        if(savedViewsMenu==null)return;Integer uid=SessionService.current()==null?null:SessionService.current().getId();
        UiTaskExecutor.submitLatest("sales-saved-views",()->support.savedViews("SALES_REGISTER",uid),views->{savedViewsMenu.getItems().clear();for(SupportApiClient.SavedView v:views){MenuItem i=new MenuItem(v.name());i.getStyleClass().add("register-saved-view-item");i.setOnAction(e->applySaved(v.name(),v.data()));savedViewsMenu.getItems().add(i);}if(savedViewsMenu.getItems().isEmpty())savedViewsMenu.getItems().add(savedViewPlaceholder());},failure->{savedViewsMenu.getItems().setAll(savedViewPlaceholder());PerformanceMonitor.event("sales-saved-views",String.valueOf(failure.getMessage()));});
    }
    private void applySaved(String name,String data){String[]x=data==null?new String[0]:data.split("\\|",-1);if(x.length==0){warning("The saved view is empty and cannot be applied.");return;}filterDebouncer.cancel();applyingSavedView=true;try{txtInvoice.setText(part(x,0));String customer=part(x,1);org.example.util.PartySearchUi.preserveSelection(cmbCustomer,customer.isBlank()?"All customers":customer,"All customers");dpFrom.setValue(date(part(x,2)));dpTo.setValue(date(part(x,3)));selectSaved(cmbPaymentStatus,part(x,4),"All");selectSaved(cmbMailStatus,part(x,6),"All");selectSaved(cmbWhatsappStatus,part(x,7),"All");selectSaved(cmbInvoiceType,part(x,8),"All");txtAmountFrom.setText(part(x,9));txtAmountTo.setText(part(x,10));selectSaved(cmbDocumentStatus,part(x,11),"All");txtSearch.setText(part(x,12));pageState.reset();pendingSavedViewName=name;}finally{applyingSavedView=false;}filterDebouncer.cancel();renderChips();reloadPage();}
    private void notifyAppliedSavedView(){if(pendingSavedViewName==null)return;String name=pendingSavedViewName;pendingSavedViewName=null;org.example.util.ToastManager.info(tableSales,"Saved view applied",name+" filters are now active.");}
    private static String part(String[] values,int index){return values!=null&&index>=0&&index<values.length&&values[index]!=null?values[index]:"";}
    private static void selectSaved(ComboBox<String> box,String raw,String fallback){if(box==null)return;String value=raw==null||raw.isBlank()?fallback:raw.trim();String match=box.getItems().stream().filter(v->v!=null&&v.equalsIgnoreCase(value)).findFirst().orElse(null);if(match==null&&!value.equalsIgnoreCase(fallback)){box.getItems().add(value);match=value;}box.setValue(match!=null?match:fallback);}

    @FXML private void newSale(){NavigationManager.navigateOrReport("/fxml/pages/Sale.fxml");}
    private void edit(Sales sale){try{FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Sale.fxml"));Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);SalesController controller=loader.getController();controller.loadSale(service.getByInvoice(sale.getInvoiceNo()));NavigationManager.getInstance().showPreparedPage("/fxml/pages/Sale.fxml",root,controller);}catch(Exception e){error(e);}}
    private void viewSale(Sales sale){try{FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Sale.fxml"));Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);SalesController controller=loader.getController();controller.loadSale(service.getByInvoice(sale.getInvoiceNo()));controller.setViewMode(true);NavigationManager.getInstance().showPreparedPage("/fxml/pages/Sale.fxml",root,controller);}catch(Exception e){error(e);}}
    private void openSaleInvoicePdf(Sales sale){try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice not found. Refresh and try again.");Path pdf=InvoicePdfService.salesBodyOnly(full);java.awt.Desktop.getDesktop().open(pdf.toFile());log("SALE",sale.getId(),"PDF_OPENED_BODY_ONLY",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void openPdf(Sales sale){try{Path p=InvoicePdfService.sales(service.getByInvoice(sale.getInvoiceNo()));java.awt.Desktop.getDesktop().open(p.toFile());log("SALE",sale.getId(),"PDF_OPENED",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void openExcel(Sales sale){try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice "+sale.getInvoiceNo()+" was not found. Refresh the register and try again.");Path excel=ExcelOutputService.sales(full);if(java.awt.Desktop.isDesktopSupported())java.awt.Desktop.getDesktop().open(excel.toFile());else info("Excel file created: "+excel);log("SALE",sale.getId(),"EXCEL_OPENED",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void sendEmail(Sales sale){if(isApprovalLocked(sale)){warning("Admin approval is required before sending this Sale document.");return;}String stage="loading the sales invoice";try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice "+sale.getInvoiceNo()+" was not found. Refresh the register and try again.");if(full.getCustomer()==null)throw new IllegalStateException("No customer is linked to "+full.getInvoiceNo()+".");String recipient=safe(full.getCustomer().getEmail()).trim();if(recipient.isBlank())throw new IllegalStateException("Customer email is missing for "+full.getCustomer().getName()+". Update Customer Master and try again.");stage="generating the sales invoice PDF";Path pdf=InvoicePdfService.sales(full);stage="sending the email";EmailService.send(recipient,"Sales Invoice "+full.getInvoiceNo(),"Dear "+safe(full.getCustomer().getName())+",\n\nPlease find your sales invoice attached.\n\nRegards,\n"+org.example.config.ConfigManager.get("company.name","DSE ERP"),pdf);service.markEmailSent(full.getId());communication("SALE",full.getId(),"EMAIL",recipient,"Sales Invoice "+full.getInvoiceNo(),"SENT",null);refresh();info("Invoice emailed successfully to "+recipient+".");}catch(Exception failure){String recipient=sale.getCustomer()==null?"":safe(sale.getCustomer().getEmail());communication("SALE",sale.getId(),"EMAIL",recipient,"Sales Invoice "+sale.getInvoiceNo(),"FAILED",stage+": "+rootMessage(failure));error(new IllegalStateException("Email failed while "+stage+".\n\n"+rootMessage(failure),failure));}}
    private void sendWhatsapp(Sales sale){if(isApprovalLocked(sale)){warning("Admin approval is required before sharing this Sale document.");return;}try{Sales full=service.getByInvoice(sale.getInvoiceNo());String phone=digits(full.getCustomer().getPhone());if(phone.length()==10)phone="91"+phone;if(phone.isBlank()){warning("Customer mobile number is not available. Update it in Customer Master.");return;}String missing=PaymentMessageService.missingPaymentConfiguration();if(missing!=null)warning(missing+" The invoice can still be shared without a payment link.");Path pdf=InvoicePdfService.sales(full);WhatsappService.openWhatsappWithMessage(phone,PaymentMessageService.salesMessage(full),pdf,PaymentMessageService.configuredQrPath());info("WhatsApp is ready. The invoice and configured UPI QR are on the clipboard for attachment.");support.markWhatsapp("SALE",full.getId());communication("SALE",full.getId(),"WHATSAPP",phone,"Sales Invoice "+full.getInvoiceNo(),"SENT",null);refresh();}catch(Exception e){error(e);}}
    private void recordPayment(Sales sale){
        if(sale.getBalanceAmount()<=0){info("This invoice is already fully paid.");return;}
        List<String> paymentModes;
        try{paymentModes=lookupService.getValuesByCategoryCode("PAYMENT_MODE");}catch(Exception e){paymentModes=List.of();}
        if(paymentModes.isEmpty()){info("No Payment Mode is configured in Master Data.");return;}
        Dialog<ButtonType>d=new OwnedDialog<>();
        d.setTitle("Record Payment");
        d.setHeaderText(sale.getInvoiceNo()+" • Balance "+money(sale.getBalanceAmount()));
        TextField amount=new TextField(String.format(Locale.ROOT,"%.2f",sale.getBalanceAmount())),ref=new TextField(),notes=new TextField();
        ComboBox<String>mode=new ComboBox<>(FXCollections.observableArrayList(paymentModes));
        if(paymentModes.stream().anyMatch(v->"Bank".equalsIgnoreCase(v)))mode.setValue(paymentModes.stream().filter(v->"Bank".equalsIgnoreCase(v)).findFirst().orElse(paymentModes.getFirst()));
        else mode.setValue(paymentModes.getFirst());
        DatePicker date=new DatePicker(BusinessClock.today());
        javafx.scene.layout.GridPane g=new javafx.scene.layout.GridPane();
        g.setHgap(10);g.setVgap(10);
        g.addRow(0,new Label("Date"),date);g.addRow(1,new Label("Amount"),amount);g.addRow(2,new Label("Mode"),mode);g.addRow(3,new Label("Reference"),ref);g.addRow(4,new Label("Notes"),notes);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(new ButtonType("Record",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);
        d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{double paid=Double.parseDouble(amount.getText());if(paid<=0||paid>sale.getBalanceAmount()+.01)throw new IllegalArgumentException("Payment must be between 0 and "+sale.getBalanceAmount());support.recordPayment(new SupportApiClient.PaymentRequest("SALE",sale.getId(),date.getValue().toString(),paid,mode.getValue(),ref.getText(),notes.getText(),sale.getCustomer()==null?"":sale.getCustomer().getName(),"RECEIPT",null,user()));log("SALE",sale.getId(),"PAYMENT_RECORDED",money(paid));refresh();info("Payment recorded.");}catch(Exception e){error(e);}});
    }


    private boolean isFinanciallyLocked(Sales sale){
        if(sale==null)return false;
        String payment=safe(sale.getBasePaymentStatus()).toUpperCase(java.util.Locale.ROOT);
        return sale.getPaidAmount()>.009 || payment.contains("PAID") || payment.contains("SETTLED") || payment.contains("PARTIAL");
    }
    private boolean isFullyPaid(Sales sale){
        if(sale==null)return false;
        String payment=safe(sale.getBasePaymentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
        return payment.equals("PAID")||payment.equals("SETTLED")||sale.getPaidAmount()+.009>=sale.getTotalAmount();
    }
    private boolean isApprovalLocked(Sales sale){String status=safe(sale==null?null:sale.getDocumentStatus()).trim().toUpperCase(Locale.ROOT);return Set.of("PENDING APPROVAL","REJECTED").contains(status);}
    private boolean isReturnEligible(Sales sale){
        if(sale==null||!isFullyPaid(sale))return false;
        String document=safe(sale.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
        String current=safe(sale.getPaymentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
        return "APPROVED".equals(document)&&!Set.of("RETURN APPROVAL PENDING","RETURN PENDING","RETURN PARTIAL").contains(current);
    }

    private void cancelSale(Sales sale){
        if (sale == null) return;
        String status = safe(sale.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);
        if ("CANCELLED".equals(status)) { info("This sale is already cancelled."); return; }
        if ("DELETED".equals(status)) { info("Deleted sales cannot be cancelled."); return; }
        if (isFinanciallyLocked(sale)) { info("Paid, partially paid, or settled sales cannot be cancelled. Use the sales return/reversal workflow instead."); return; }
        if(!confirm("Cancel "+sale.getInvoiceNo()+"?\n\nStock will be restored and document status will become CANCELLED."))return;
        try{
            service.cancel(sale.getInvoiceNo());
            log("SALE",sale.getId(),"CANCELLED",sale.getInvoiceNo());
            refresh();
            closeDetails();
            info(sale.getInvoiceNo()+" cancelled. Stock restored and the document remains visible as CANCELLED.");
        }catch(Exception e){error(e);}
    }
    private void createReturn(Sales sale){Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null){warning("Sales invoice not found. Refresh and try again.");return;}List<ReturnEditorService.InvoiceItem> items=full.getLines().stream().map(line->new ReturnEditorService.InvoiceItem(line.getItemCode(),line.getItemDescription(),line.getQuantity(),line.getRate(),line.getGstPercent())).toList();ReturnEditorService.show(tableSales.getScene().getWindow(),ReturnEditorService.Type.SALES,sale.getInvoiceNo(),sale.getCustomer().getName(),sale.getCustomer().getId(),items).ifPresent(no->{refresh();info("Sales return created: "+no);});}
    private void duplicate(Sales sale){
        try{
            FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Sale.fxml"));
            Parent root=loader.load();
            org.example.util.ProfessionalUiEnhancer.enhance(root);
            SalesController controller=loader.getController();
            Sales full=service.getByInvoice(sale.getInvoiceNo());
            if(full==null)throw new IllegalStateException("Sales invoice "+sale.getInvoiceNo()+" was not found. Refresh the register and try again.");
            controller.loadSale(full);
            controller.prepareDuplicate();
            NavigationManager.getInstance().showPreparedPage("/fxml/pages/Sale.fxml",root,controller);
        }catch(Exception e){error(e);}
    }
    private void delete(Sales sale){
        if (sale == null) return;
        String status = safe(sale.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);
        if ("DELETED".equals(status)) { info("This sale is already marked as deleted."); return; }
        if (isFinanciallyLocked(sale)) { info("Paid, partially paid, or settled sales cannot be deleted. Use the sales return/reversal workflow instead."); return; }
        String stockText = "CANCELLED".equals(status)
            ? "Stock was already restored when this sale was cancelled."
            : "Stock will be restored.";
        if(!confirm("Delete "+sale.getInvoiceNo()+"?\n\nThe document will disappear from the normal Sales Register, but its backend audit record will be retained as DELETED.\n"+stockText))return;
        try{
            service.delete(sale.getInvoiceNo());
            log("SALE",sale.getId(),"DELETED",sale.getInvoiceNo());
            refresh();
            closeDetails();
            info(sale.getInvoiceNo()+" deleted from the register. Backend audit record retained.");
        }catch(Exception e){error(e);}
    }

    @FXML private void exportSale(){
        File f=chooseSave("Export Sales Register","Sales_Register.xlsx","Excel","*.xlsx");if(f==null)return;
        String customer=cmbCustomer.getValue();if(customer!=null&&customer.startsWith("All"))customer="";String selectedCustomer=customer;
        String q=txtSearch.getText(),invoice=txtInvoice.getText(),payment=cmbPaymentStatus.getValue(),due="All",mail=cmbMailStatus.getValue(),whatsapp=cmbWhatsappStatus.getValue(),invoiceType=cmbInvoiceType.getValue(),documentStatus=cmbDocumentStatus.getValue();LocalDate from=dpFrom.getValue(),to=dpTo.getValue();Double min=parseOptionalAmount(txtAmountFrom.getText()),max=parseOptionalAmount(txtAmountTo.getText());
        UiTaskExecutor.submitAction("sales-register-export-excel",()->{List<Sales> rows=service.allFiltered(q,invoice,selectedCustomer,from,to,payment,due,mail,whatsapp,invoiceType,documentStatus,min,max);writeSalesExcel(f,rows);return rows.size();},count->info("Sales register exported • "+count+" records."),this::error);
    }
    @FXML private void exportRegisterPdf(){
        File f=chooseSave("Export Sales Register PDF","Sales_Register.pdf","PDF","*.pdf");if(f==null)return;
        String customer=cmbCustomer.getValue();if(customer!=null&&customer.startsWith("All"))customer="";String selectedCustomer=customer;
        String q=txtSearch.getText(),invoice=txtInvoice.getText(),payment=cmbPaymentStatus.getValue(),due="All",mail=cmbMailStatus.getValue(),whatsapp=cmbWhatsappStatus.getValue(),invoiceType=cmbInvoiceType.getValue(),documentStatus=cmbDocumentStatus.getValue();LocalDate from=dpFrom.getValue(),to=dpTo.getValue();Double min=parseOptionalAmount(txtAmountFrom.getText()),max=parseOptionalAmount(txtAmountTo.getText());
        UiTaskExecutor.submitAction("sales-register-export-pdf",()->{List<Sales> rows=service.allFiltered(q,invoice,selectedCustomer,from,to,payment,due,mail,whatsapp,invoiceType,documentStatus,min,max);org.example.service.BrandedRegisterPdfService.export(f.toPath(),"Sales Register",new String[]{"Invoice","Date","Customer","Amount","Paid","Pending","Document Status","Payment Status"},rows.stream().map(x->new String[]{x.getInvoiceNo(),str(x.getInvoiceDate()),x.getCustomer()==null?"":safe(x.getCustomer().getName()),exportMoney(x.getTotalAmount()),exportMoney(x.getPaidAmount()),exportMoney(x.getBalanceAmount()),documentStatus(x),paymentStatusDisplay(x)}).toList(),new float[]{2,1.3f,2.4f,1.3f,1.3f,1.3f,1.5f,1.6f});return rows.size();},count->info("Sales register PDF exported • "+count+" records."),this::error);
    }
    private void writeSalesExcel(File f,List<Sales> rows)throws Exception{try(Workbook w=new XSSFWorkbook();FileOutputStream out=new FileOutputStream(f)){Sheet sh=w.createSheet("Sales Register");String[]h={"Invoice No","Date","Customer","Mobile","GSTIN","Amount","Paid","Pending","Due Date","Document Status","Payment Status","Email","WhatsApp"};Row row=sh.createRow(0);for(int i=0;i<h.length;i++)row.createCell(i).setCellValue(h[i]);int n=1;for(Sales x:rows){row=sh.createRow(n++);org.example.model.Party party=x.getCustomer();Object[]v={x.getInvoiceNo(),str(x.getInvoiceDate()),party==null?"":safe(party.getName()),party==null?"":safe(party.getPhone()),party==null?"":safe(party.getGstin()),x.getTotalAmount(),x.getPaidAmount(),x.getBalanceAmount(),dueLabel(x),documentStatus(x),paymentStatusDisplay(x),x.isEmailSent()?"Sent":"Not Sent",x.isWhatsappSent()?"Sent":"Not Sent"};for(int i=0;i<v.length;i++){if(v[i] instanceof Number z)row.createCell(i).setCellValue(z.doubleValue());else row.createCell(i).setCellValue(String.valueOf(v[i]));}}for(int i=0;i<h.length;i++)sh.autoSizeColumn(i);w.write(out);}}
    private String exportMoney(double value){return NumberFormat.getCurrencyInstance(Locale.of("en","IN")).format(value).replace("₹","₹ ");}
    @FXML private void printRegister(){PrinterJob job=PrinterJob.createPrinterJob();if(job!=null&&job.showPrintDialog(tableSales.getScene().getWindow())){boolean ok=job.printPage(tableSales);if(ok)job.endJob();}}
    private File chooseSave(String title,String name,String label,String pattern){FileChooser c=new FileChooser();c.setTitle(title);c.setInitialFileName(name);c.getExtensionFilters().add(new FileChooser.ExtensionFilter(label,pattern));return c.showSaveDialog(tableSales.getScene().getWindow());}
    private void communication(String type,int id,String channel,String recipient,String subject,String status,String error){try{support.communication(new SupportApiClient.CommunicationRequest(type,id,channel,recipient,subject,status,error,user()));}catch(Exception ignored){}}
    private void log(String type,int id,String action,String detail){try{support.activity(type,id,action,detail,user());}catch(Exception ignored){}}
    private String user(){return SessionService.current()==null?"System":SessionService.current().getFullName();}
    private String dueLabel(Sales s){
        String document=safe(s.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);
        if(document.contains("DELETE"))return "Deleted";
        if(document.contains("CANCEL"))return "Cancelled";
        String payment=safe(s.getPaymentStatus()).toUpperCase(java.util.Locale.ROOT);
        if("RETURN APPROVAL PENDING".equals(payment))return "Awaiting Approval";
        if("RETURN PAID".equals(payment))return "Settled";
        if("RETURN PENDING".equals(payment)||"RETURN PARTIAL".equals(payment)){
            LocalDate due=s.getReturnDueDate();
            if(due==null)return "Return Due";
            long d=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),due);
            return d<0?"Return Overdue by "+Math.abs(d)+" days":d==0?"Return Due Today":"Return Due in "+d+" Days";
        }
        if(s.getBalanceAmount()<=.01)return "Settled";
        if(s.getDueDate()==null)return "Not set";
        long d=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),s.getDueDate());
        return d<0?"Overdue by "+Math.abs(d)+" days":d==0?"Due today":"Due in "+d+" days";
    }
    private String money(double v){return currency.format(v).replace("₹","₹ ");}
    private String safe(String v){return v==null?"":v;}
    private String lower(String v){return safe(v).toLowerCase(Locale.ROOT);}
    private String digits(String v){return safe(v).replaceAll("\\D","");}
    private String str(Object v){return v==null?"":v.toString();}
    private LocalDate date(String v){try{return v==null||v.isBlank()?null:LocalDate.parse(v);}catch(Exception e){return null;}}
    private double parseAmount(String v,double fallback){try{return v==null||v.isBlank()?fallback:Double.parseDouble(v.replace(",",""));}catch(Exception e){return fallback;}}
    private boolean confirm(String text){return org.example.util.ModernDialog.confirm(tableSales,"Confirmation","Are you sure?",text);}private void info(String m){org.example.util.ToastManager.success(tableSales,"Completed",m);}private void warning(String m){org.example.util.ModernDialog.warning(tableSales,"Warning","Please review",m);}private void error(Throwable e){e.printStackTrace();String message=rootMessage(e);org.example.util.ModernDialog.error(tableSales,"Operation failed","Something went wrong",message);}private String rootMessage(Throwable failure){Throwable root=failure;while(root.getCause()!=null)root=root.getCause();String message=root.getMessage();return message==null||message.isBlank()?root.getClass().getSimpleName():message;}
}
