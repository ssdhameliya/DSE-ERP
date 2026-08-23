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
import org.example.util.PerformanceMonitor;
import org.example.util.PlatformUiSupport;
import org.example.util.ScreenRefreshPolicy;
import org.example.service.*;
import org.example.util.IconFactory;
import org.example.util.TableSelectionSupport;
import org.example.util.SemanticTableCells;
import org.example.util.UiActionIcons;
import org.example.util.InvoicePaymentDetailsDialog;

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
    @FXML private StackPane salesTitleIcon,totalSalesIcon,todaySalesIcon,pendingSalesIcon,overdueSalesIcon,dueSoonIcon,emailRateIcon;
    @FXML private Button btnNewSale,btnResetFilters,btnRefreshSales,btnExportExcel,btnExportPdf,btnPrintRegister;
    @FXML private Button btnTodayRange,btnYesterdayRange,btnSevenDaysRange,btnThirtyDaysRange,btnCustomRange,btnCloseDetails,btnApproveSale,btnRejectSale;
    @FXML private TextField txtSearch,txtInvoice,txtAmountFrom,txtAmountTo;
    @FXML private ComboBox<String> cmbCustomer,cmbPaymentStatus,cmbPaymentDue,cmbMailStatus,cmbWhatsappStatus,cmbInvoiceType;
    @FXML private DatePicker dpFrom,dpTo;
    @FXML private ToggleButton btnAdvanced;
    @FXML private javafx.scene.layout.GridPane advancedFilters;
    @FXML private FlowPane activeFilterChips;
    @FXML private MenuButton savedViewsMenu;
    @FXML private TableView<Sales> tableSales;
    @FXML private TableColumn<Sales,String> colInvoice,colDate,colCustomer,colMobile,colGstin,colDue,colStatus,colMail;
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
    private final SupportApiClient support=new SupportApiClient();
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private List<Sales> allSales=new ArrayList<>(),filteredSales=new ArrayList<>();
    private int currentPage=0;
    private Sales selected;

    @FXML public void initialize(){
        configureColumns();configureFilters();configureActions();configurePaging();configureVisualIcons();configureDetailFieldIcons();refreshShortcutLabels();
        configureExplicitTableHeaderIcons();
        simplifyFilters();
        detailDrawer.setVisible(false);detailDrawer.setManaged(false);mainSplit.setDividerPositions(1.0);
        // One shared drawer interaction: first click opens, clicking the same row again closes,
        // and clicking a different row replaces the drawer contents immediately.
        tableSales.setRowFactory(view -> {
            TableRow<Sales> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY || event.getClickCount() != 1 || row.isEmpty() || interactiveTableTarget(event.getPickResult().getIntersectedNode(), row)) return;
                Sales clicked = row.getItem();
                if (detailDrawer.isVisible() && selected != null && selected.getId() == clicked.getId()) closeDetails();
                else { tableSales.getSelectionModel().select(clicked); showDetails(clicked); }
                event.consume();
            });
            return row;
        });
        txtSearch.textProperty().addListener((o,a,b)->applyFilters());
    }

    private boolean interactiveTableTarget(Node target, TableRow<?> row) {
        for (Node node = target; node != null && node != row; node = node.getParent()) {
            if (node instanceof ButtonBase || node instanceof TextInputControl || node instanceof ComboBoxBase<?>) return true;
        }
        return false;
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
        setIcon(totalSalesIcon,"payment",24);
        setIcon(todaySalesIcon,"sale",24);
        setIcon(pendingSalesIcon,"reminder",24);
        setIcon(overdueSalesIcon,"error",24);
        setIcon(dueSoonIcon,"calendar",24);
        setIcon(emailRateIcon,"email",24);
        setButtonIcon(btnNewSale,"sale");
        setButtonIcon(btnResetFilters,"refresh");
        setButtonIcon(btnRefreshSales,"refresh");
        setButtonIcon(btnExportExcel,"excel");
        setButtonIcon(btnExportPdf,"pdf");
        setButtonIcon(btnPrintRegister,"print");
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
        hide(txtInvoice);hide(txtAmountFrom);hide(txtAmountTo);hide(cmbPaymentDue);hide(cmbWhatsappStatus);hide(cmbInvoiceType);hide(btnAdvanced);hide(savedViewsMenu);
        advancedFilters.setVisible(true);advancedFilters.setManaged(true);
        place(cmbCustomer,0);place(dpFrom,1);place(dpTo,2);place(cmbPaymentStatus,3);place(cmbMailStatus,4);
        for(Node child:advancedFilters.getChildren())if(child instanceof HBox actions){javafx.scene.layout.GridPane.setRowIndex(actions,0);javafx.scene.layout.GridPane.setColumnIndex(actions,5);if(!actions.getChildren().isEmpty()){Node save=actions.getChildren().getFirst();save.setVisible(false);save.setManaged(false);}}
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
        colMail.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().isEmailSent()?"Sent":"Not Sent"));
        colTotal.setCellFactory(x->totalMoneyCell());
        colPaid.setCellFactory(x->paidMoneyCell());
        colBalance.setCellFactory(x->balanceMoneyCell());
        colStatus.setCellFactory(x->SemanticTableCells.status("document"));
        colMail.setCellFactory(x->SemanticTableCells.status("email"));
        colDue.setCellFactory(x->SemanticTableCells.dueDate());
        tableSales.setPlaceholder(new Label("No sales invoices match the selected filters"));
    }

    private void configureExplicitTableHeaderIcons(){
        setHeaderIcon(colInvoice,"document");
        setHeaderIcon(colDate,"calendar");
        setHeaderIcon(colCustomer,"customer");
        setHeaderIcon(colMobile,"phone");
        setHeaderIcon(colGstin,"tax");
        setHeaderIcon(colTotal,"currency");
        setHeaderIcon(colPaid,"complete");
        setHeaderIcon(colBalance,"balance");
        setHeaderIcon(colDue,"reminder");
        setHeaderIcon(colStatus,"status");
        setHeaderIcon(colMail,"email");
        setHeaderIcon(colAction,"actions");
    }

    private void setHeaderIcon(TableColumn<?,?> column,String semantic){
        IconFactory.applyTableHeaderIcon(column, semantic);
    }

    private TableCell<Sales,Double> moneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);}};}
    private TableCell<Sales,Double> totalMoneyCell(){return coloredMoneyCell("register-amount-total","register-amount-total");}
    private TableCell<Sales,Double> balanceMoneyCell(){return coloredMoneyCell("register-balance-open","register-balance-settled");}
    private TableCell<Sales,Double> coloredMoneyCell(String positiveClass,String zeroClass){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);getStyleClass().removeAll("register-amount-total","register-balance-open","register-balance-settled");if(!e&&v!=null){String style=v>.009?positiveClass:zeroClass;if(style!=null)getStyleClass().add(style);}}};}
    private TableCell<Sales,Double> paidMoneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);getStyleClass().removeAll("register-paid-positive","register-paid-zero");if(!e&&v!=null)getStyleClass().add(v>.009?"register-paid-positive":"register-paid-zero");}};}
    private TableCell<Sales,String> statusCell(String semantic){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("pill-success","pill-warning","pill-danger","pill-neutral");if(!e&&v!=null){boolean returned=v.equalsIgnoreCase("RETURNED"),partialReturn=v.equalsIgnoreCase("PARTIALLY RETURNED");boolean good=v.equalsIgnoreCase("COMPLETED")||v.equalsIgnoreCase("PAID")||v.equalsIgnoreCase("SENT")||returned;boolean pending=v.equalsIgnoreCase("IN PROGRESS")||v.equalsIgnoreCase("PARTIAL")||v.equalsIgnoreCase("PENDING")||v.equalsIgnoreCase("PENDING APPROVAL")||partialReturn;getStyleClass().add(good?"pill-success":pending?"pill-warning":"pill-danger");String icon = returned||partialReturn ? "return" : (good ? semantic : (pending ? ("status".equals(semantic)?"reminder":semantic) : "error"));setGraphic(IconFactory.compactIcon(icon,15));}}};}
    private TableCell<Sales,String> dueCell(){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("due-overdue","due-soon","due-paid");if(!e&&v!=null){boolean paid=v.equals("Paid"),overdue=v.startsWith("Overdue");getStyleClass().add(overdue?"due-overdue":paid?"due-paid":"due-soon");setGraphic(IconFactory.compactIcon(overdue?"error":paid?"complete":"reminder",15));}}};}
    private String documentStatus(Sales sale){
        String stored = safe(sale.getDocumentStatus()).trim();
        String normalized = stored.toUpperCase(java.util.Locale.ROOT);
        if (java.util.Set.of("CANCELLED","DELETED","RETURNED","PARTIALLY RETURNED","DRAFT","PENDING APPROVAL","REJECTED").contains(normalized)) return normalized;
        if(sale.getBalanceAmount()<=.01)return "COMPLETED";
        if(sale.getPaidAmount()>0)return "IN PROGRESS";
        return "PENDING";
    }

    private void configureFilters(){
        cmbPaymentStatus.getItems().setAll("All","PENDING","PARTIAL","PAID","OVERDUE");cmbPaymentStatus.setValue("All");
        cmbPaymentDue.getItems().setAll("All","Overdue","Due Today","Next 7 Days","Next 30 Days");cmbPaymentDue.setValue("All");
        cmbMailStatus.getItems().setAll("All","Sent","Not Sent");cmbMailStatus.setValue("All");
        cmbWhatsappStatus.getItems().setAll("All","Sent","Not Sent");cmbWhatsappStatus.setValue("All");
        cmbInvoiceType.getItems().setAll("All","TAX INVOICE","PROFORMA","CASH MEMO");cmbInvoiceType.setValue("All");
        dpFrom.setValue(BusinessClock.today().minusMonths(6));
        dpTo.setValue(BusinessClock.today());
        dpFrom.setPromptText("Any date");
        dpTo.setPromptText("Any date");
        for (ComboBox<String> box : List.of(cmbCustomer,cmbPaymentStatus,cmbPaymentDue,cmbMailStatus,cmbWhatsappStatus,cmbInvoiceType))
            box.valueProperty().addListener((o,a,b)->applyFilters());
        dpFrom.valueProperty().addListener((o,a,b)->applyFilters());
        dpTo.valueProperty().addListener((o,a,b)->applyFilters());
        txtInvoice.textProperty().addListener((o,a,b)->applyFilters());
        txtAmountFrom.textProperty().addListener((o,a,b)->applyFilters());
        txtAmountTo.textProperty().addListener((o,a,b)->applyFilters());
    }

    private void configurePaging(){cmbPageSize.getItems().setAll(10,25,50,100);cmbPageSize.setValue(25);cmbPageSize.valueProperty().addListener((o,a,b)->{currentPage=0;renderPage();});}
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

    @FXML public void refresh(){
        UiTaskExecutor.submitLatest("sales-register-load", () -> new ArrayList<>(service.getAll()), this::applyLoadedSales, this::error);
    }
    private void applyLoadedSales(ArrayList<Sales> loaded){
        long started=System.nanoTime();
        allSales=loaded;
        cmbCustomer.getItems().setAll("All customers");cmbCustomer.getItems().addAll(allSales.stream().map(s->s.getCustomer().getName()).filter(Objects::nonNull).distinct().sorted().toList());if(cmbCustomer.getValue()==null)cmbCustomer.setValue("All customers");
        loadSavedViews();updateMetrics();applyFilters();
        openLinkedRecordIfRequested();
        if(!PlatformUiSupport.isMac()) javafx.application.Platform.runLater(this::updateCharts);
        long ms=(System.nanoTime()-started)/1_000_000L;if(ms>=20)PerformanceMonitor.event("controller-phase","sales-register-apply | "+ms+" ms");
    }


    private void openLinkedRecordIfRequested(){
        LinkedRecordContext.Target target=LinkedRecordContext.consume("SALE");if(target==null)return;
        Sales sale=allSales.stream().filter(x->(target.recordId()!=null&&x.getId()==target.recordId())||(!target.documentNo().isBlank()&&target.documentNo().equalsIgnoreCase(safe(x.getInvoiceNo())))).findFirst().orElse(null);
        if(sale==null){warning("The linked Sale is no longer available"+(target.documentNo().isBlank()?".":": "+target.documentNo()));return;}
        txtSearch.clear();txtInvoice.clear();cmbCustomer.setValue("All customers");cmbPaymentStatus.setValue("All");cmbPaymentDue.setValue("All");cmbMailStatus.setValue("All");cmbWhatsappStatus.setValue("All");cmbInvoiceType.setValue("All");dpFrom.setValue(null);dpTo.setValue(null);
        filteredSales=allSales;int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue();int index=filteredSales.indexOf(sale);currentPage=Math.max(0,index/size);renderPage();tableSales.getSelectionModel().select(sale);tableSales.scrollTo(sale);org.example.navigation.DeepLinkSupport.pulse(tableSales);showDetails(sale);
        PerformanceMonitor.event("linked-navigation","SALE -> "+sale.getInvoiceNo()+" | source="+target.source());
    }

    @FXML public void applyFilters(){
        String global=lower(txtSearch.getText()),invoice=lower(txtInvoice.getText()),customer=cmbCustomer.getValue();double min=parseAmount(txtAmountFrom.getText(),Double.NEGATIVE_INFINITY),max=parseAmount(txtAmountTo.getText(),Double.POSITIVE_INFINITY);
        Predicate<Sales> p=s->{String hay=lower(s.getInvoiceNo()+" "+s.getCustomer().getName()+" "+safe(s.getCustomer().getPhone())+" "+safe(s.getCustomer().getGstin()));if(!global.isBlank()&&!hay.contains(global))return false;if(!invoice.isBlank()&&!lower(s.getInvoiceNo()).contains(invoice))return false;if(customer!=null&&!customer.startsWith("All")&&!customer.equals(s.getCustomer().getName()))return false;if(dpFrom.getValue()!=null&&s.getInvoiceDate().isBefore(dpFrom.getValue()))return false;if(dpTo.getValue()!=null&&s.getInvoiceDate().isAfter(dpTo.getValue()))return false;if(s.getTotalAmount()<min||s.getTotalAmount()>max)return false;if(!matches(cmbPaymentStatus,s.getPaymentStatus()))return false;if(!matches(cmbInvoiceType,s.getInvoiceType()))return false;if(!matches(cmbMailStatus,s.isEmailSent()?"Sent":"Not Sent"))return false;if(!matches(cmbWhatsappStatus,s.isWhatsappSent()?"Sent":"Not Sent"))return false;return matchesDue(s);};
        filteredSales=allSales.stream().filter(p).toList();currentPage=0;renderPage();renderChips();updateFooter();
    }

    private boolean matches(ComboBox<String> box,String value){String f=box.getValue();return f==null||f.equals("All")||f.equalsIgnoreCase(value);}
    private boolean matchesDue(Sales s){String f=cmbPaymentDue.getValue();if(f==null||f.equals("All"))return true;if(s.getDueDate()==null||s.getBalanceAmount()<=0)return false;long days=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),s.getDueDate());return switch(f){case "Overdue"->days<0;case "Due Today"->days==0;case "Next 7 Days"->days>=0&&days<=7;case "Next 30 Days"->days>=0&&days<=30;default->true;};}

    private void renderPage(){int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue(),pages=Math.max(1,(int)Math.ceil(filteredSales.size()/(double)size));currentPage=Math.min(currentPage,pages-1);int from=Math.min(currentPage*size,filteredSales.size()),to=Math.min(from+size,filteredSales.size());tableSales.setItems(FXCollections.observableArrayList(filteredSales.subList(from,to)));lblPageNumber.setText((currentPage+1)+" / "+pages);lblPageInfo.setText(filteredSales.isEmpty()?"No entries":"Showing "+(from+1)+" to "+to+" of "+filteredSales.size()+" entries");}
    @FXML private void firstPage(){currentPage=0;renderPage();}@FXML private void previousPage(){if(currentPage>0)currentPage--;renderPage();}@FXML private void nextPage(){int pages=(int)Math.ceil(filteredSales.size()/(double)cmbPageSize.getValue());if(currentPage<pages-1)currentPage++;renderPage();}@FXML private void lastPage(){currentPage=Math.max(0,(int)Math.ceil(filteredSales.size()/(double)cmbPageSize.getValue())-1);renderPage();}

    private void updateMetrics(){
        List<Sales> active=allSales.stream().filter(this::isActiveFinancialDocument).toList();
        double total=sum(active,Sales::getTotalAmount),
            today=sum(active.stream().filter(s->s.getInvoiceDate().equals(BusinessClock.today())).toList(),Sales::getTotalAmount),
            pending=sum(active,Sales::getBalanceAmount);
        List<Sales> overdue=active.stream().filter(s->s.getBalanceAmount()>0&&s.getDueDate()!=null&&s.getDueDate().isBefore(BusinessClock.today())).toList(),
            soon=active.stream().filter(s->s.getBalanceAmount()>0&&s.getDueDate()!=null&&!s.getDueDate().isBefore(BusinessClock.today())&&!s.getDueDate().isAfter(BusinessClock.today().plusDays(7))).toList();
        lblTotalSales.setText(money(total));lblInvoiceCount.setText(active.size()+" invoices");
        lblTodaySales.setText(money(today));lblTodayCount.setText(active.stream().filter(s->s.getInvoiceDate().equals(BusinessClock.today())).count()+" invoices");
        lblPending.setText(money(pending));lblPendingCount.setText(active.stream().filter(s->s.getBalanceAmount()>0).count()+" invoices");
        lblOverdue.setText(money(sum(overdue,Sales::getBalanceAmount)));lblOverdueCount.setText(overdue.size()+" invoices");
        lblDueSoon.setText(money(sum(soon,Sales::getBalanceAmount)));lblDueSoonCount.setText(soon.size()+" invoices");
        long sent=active.stream().filter(Sales::isEmailSent).count();lblEmailRate.setText(active.isEmpty()?"0%":Math.round(sent*100.0/active.size())+"%");
    }
    private boolean isActiveFinancialDocument(Sales sale){String s=safe(sale.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);return !"CANCELLED".equals(s)&&!"DELETED".equals(s);}
    private double sum(List<Sales> list,java.util.function.ToDoubleFunction<Sales> f){return list.stream().mapToDouble(f).sum();}
    private void updateFooter(){List<Sales> active=filteredSales.stream().filter(this::isActiveFinancialDocument).toList();lblFooterTotal.setText(money(sum(active,Sales::getTotalAmount)));lblFooterPaid.setText(money(sum(active,Sales::getPaidAmount)));lblFooterBalance.setText(money(sum(active,Sales::getBalanceAmount)));}

    private void updateCharts(){
        if(PlatformUiSupport.isMac()||dueChart==null||customerChart==null||salesChart==null)return;
        Map<String,Double> buckets=new LinkedHashMap<>();buckets.put("Due Today",0d);buckets.put("1-7 Days",0d);buckets.put("8-30 Days",0d);buckets.put("Over 30 Days",0d);for(Sales s:allSales)if(s.getBalanceAmount()>0&&s.getDueDate()!=null){long d=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),s.getDueDate());String k=d<=0?"Due Today":d<=7?"1-7 Days":d<=30?"8-30 Days":"Over 30 Days";buckets.merge(k,s.getBalanceAmount(),Double::sum);}dueChart.getData().setAll(buckets.entrySet().stream().filter(e->e.getValue()>0).map(e->new PieChart.Data(e.getKey(),e.getValue())).toList());
        Map<String,Double> customers=new HashMap<>();for(Sales s:allSales){String customerName=s.getCustomer()==null?null:s.getCustomer().getName();if(customerName==null||customerName.isBlank())customerName="Unknown Customer";customers.merge(customerName,s.getTotalAmount(),Double::sum);}XYChart.Series<Number,String> cs=new XYChart.Series<>();customers.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(5).forEach(e->cs.getData().add(new XYChart.Data<>(e.getValue(),e.getKey())));customerChart.getData().setAll(cs);
        Map<String,Double> months=new TreeMap<>();for(Sales s:allSales)months.merge(s.getInvoiceDate().toString().substring(0,7),s.getTotalAmount(),Double::sum);XYChart.Series<String,Number> ss=new XYChart.Series<>();months.entrySet().stream().skip(Math.max(0,months.size()-7)).forEach(e->ss.getData().add(new XYChart.Data<>(e.getKey(),e.getValue())));salesChart.getData().setAll(ss);
    }

    @Override public void onScreenShown(boolean reusedFromCache){
        refreshShortcutLabels();
        if(allSales.isEmpty() || ScreenRefreshPolicy.shouldRefresh("sales-register", ScreenRefreshPolicy.Mode.WHEN_STALE, java.time.Duration.ofSeconds(60))) refresh();
    }

    private void refreshShortcutLabels(){
        if(btnNewSale==null)return;
        String key=org.example.shortcut.ShortcutRegistry.display(org.example.shortcut.ShortcutRegistry.Action.NEW_SALE);
        btnNewSale.setText("Disabled".equals(key)?"New Sale":"New Sale ("+key+")");
    }
    @Override public void onScreenHidden(){UiTaskExecutor.cancel("sales-register-load");}

    private void showDetails(Sales sale){
        selected=sale;
        detailDrawer.setManaged(true);
        detailDrawer.setVisible(true);
        mainSplit.setDividerPositions(.76);
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
    @FXML private void closeDetails(){selected=null;detailDrawer.setVisible(false);detailDrawer.setManaged(false);mainSplit.setDividerPositions(1);tableSales.getSelectionModel().clearSelection();}
    private Sales requireSelected(){if(selected==null){warning("Select an invoice first.");return null;}return selected;}
    @FXML private void emailSelected(){Sales s=requireSelected();if(s!=null)sendEmail(s);}@FXML private void whatsappSelected(){Sales s=requireSelected();if(s!=null)sendWhatsapp(s);}@FXML private void editSelectedSale(){Sales s=requireSelected();if(s!=null)edit(s);}@FXML private void recordSelectedPayment(){Sales s=requireSelected();if(s!=null)openPayment(s);}@FXML private void excelSelected(){Sales s=requireSelected();if(s!=null)openExcel(s);}
    @FXML private void approveSelectedSale(){Sales s=requireSelected();if(s==null)return;try{service.approve(s.getInvoiceNo());NotificationService.add("Sale "+s.getInvoiceNo()+" approved.");refresh();}catch(Exception e){error(e);}}
    @FXML private void rejectSelectedSale(){Sales s=requireSelected();if(s==null)return;OwnedTextInputDialog dialog=new OwnedTextInputDialog();dialog.setTitle("Reject Sale");dialog.setHeaderText("Reject "+s.getInvoiceNo());dialog.setContentText("Reason:");dialog.showAndWait().map(String::trim).filter(v->!v.isBlank()).ifPresent(reason->{try{service.reject(s.getInvoiceNo(),reason);NotificationService.add("Sale "+s.getInvoiceNo()+" rejected.");refresh();}catch(Exception e){error(e);}});}
    private void openInvoiceDetails(Sales s){openPayment(s);}
    private void openPayment(Sales s){
        if(s!=null&&Set.of("PENDING APPROVAL","REJECTED").contains(safe(s.getDocumentStatus()).trim().toUpperCase(Locale.ROOT))){warning("Admin approval is required before recording a payment for this Sale.");return;}
        if (s == null || safe(s.getInvoiceNo()).isBlank()) {
            warning("Unable to open Record Payment because the selected invoice is not available.");
            return;
        }
        SalesScreenContext.select(s.getInvoiceNo());
        navigateSalesPage("/fxml/pages/RecordPayment.fxml", "Record Payment");
    }
    private void openPaymentHistory(Sales s){
        if (s == null || safe(s.getInvoiceNo()).isBlank()) {
            warning("Unable to open Payment History because the selected invoice is not available.");
            return;
        }
        SalesScreenContext.select(s.getInvoiceNo());
        navigateSalesPage("/fxml/pages/PaymentHistory.fxml", "Payment History");
    }
    private void navigateSalesPage(String fxml, String screenName) {
        if (!NavigationManager.navigateOrReport(fxml)) {
            warning(screenName + " could not be opened. Please try again.");
        }
    }

    @FXML private void showToday(){applyDateRange(BusinessClock.today(),BusinessClock.today());}
    @FXML private void showYesterday(){LocalDate day=BusinessClock.today().minusDays(1);applyDateRange(day,day);}
    @FXML private void showSevenDays(){applyDateRange(BusinessClock.today().minusDays(6),BusinessClock.today());}
    @FXML private void showThirtyDays(){applyDateRange(BusinessClock.today().minusDays(29),BusinessClock.today());}
    @FXML private void focusCustomRange(){dpFrom.requestFocus();}
    private void applyDateRange(LocalDate from,LocalDate to){dpFrom.setValue(from);dpTo.setValue(to);applyFilters();}

    @FXML private void toggleAdvanced(){advancedFilters.setManaged(btnAdvanced.isSelected());advancedFilters.setVisible(btnAdvanced.isSelected());}
    @FXML private void resetFilters(){txtSearch.clear();txtInvoice.clear();txtAmountFrom.clear();txtAmountTo.clear();dpFrom.setValue(BusinessClock.today().minusMonths(6));dpTo.setValue(BusinessClock.today());cmbCustomer.setValue("All customers");cmbPaymentStatus.setValue("All");cmbPaymentDue.setValue("All");cmbMailStatus.setValue("All");cmbWhatsappStatus.setValue("All");cmbInvoiceType.setValue("All");applyFilters();}
    private void renderChips(){activeFilterChips.getChildren().clear();addChip("From",dpFrom.getValue());addChip("To",dpTo.getValue());addChip("Payment",nonAll(cmbPaymentStatus));addChip("Due",nonAll(cmbPaymentDue));addChip("Email",nonAll(cmbMailStatus));addChip("WhatsApp",nonAll(cmbWhatsappStatus));}
    private Object nonAll(ComboBox<String>b){return b.getValue()==null||b.getValue().equals("All")?null:b.getValue();}private void addChip(String name,Object value){if(value==null)return;Label chip=new Label(name+": "+value);chip.getStyleClass().add("filter-chip");activeFilterChips.getChildren().add(chip);}

    @FXML private void saveCurrentView(){TextInputDialog d=new OwnedTextInputDialog();d.setTitle("Save Filter View");d.setHeaderText("Save the current sales filters");d.setContentText("View name:");d.showAndWait().map(String::trim).filter(x->!x.isBlank()).ifPresent(name->{String data=String.join("|",safe(txtInvoice.getText()),safe(cmbCustomer.getValue()),str(dpFrom.getValue()),str(dpTo.getValue()),safe(cmbPaymentStatus.getValue()),safe(cmbPaymentDue.getValue()),safe(cmbMailStatus.getValue()),safe(cmbWhatsappStatus.getValue()),safe(cmbInvoiceType.getValue()),safe(txtAmountFrom.getText()),safe(txtAmountTo.getText()));try{Integer uid=SessionService.current()==null?null:SessionService.current().getId();support.saveView(uid,"SALES_REGISTER",name,data);loadSavedViews();info("Saved view created.");}catch(Exception e){error(e);}});}
    private void loadSavedViews(){savedViewsMenu.getItems().clear();try{Integer uid=SessionService.current()==null?null:SessionService.current().getId();for(SupportApiClient.SavedView v:support.savedViews("SALES_REGISTER",uid)){MenuItem i=new MenuItem(v.name());i.setOnAction(e->applySaved(v.data()));savedViewsMenu.getItems().add(i);}}catch(Exception ignored){}if(savedViewsMenu.getItems().isEmpty())savedViewsMenu.getItems().add(new MenuItem("No saved views"));}
    private void applySaved(String data){String[]x=data.split("\\|",-1);if(x.length<11)return;txtInvoice.setText(x[0]);cmbCustomer.setValue(x[1]);dpFrom.setValue(date(x[2]));dpTo.setValue(date(x[3]));cmbPaymentStatus.setValue(x[4]);cmbPaymentDue.setValue(x[5]);cmbMailStatus.setValue(x[6]);cmbWhatsappStatus.setValue(x[7]);cmbInvoiceType.setValue(x[8]);txtAmountFrom.setText(x[9]);txtAmountTo.setText(x[10]);applyFilters();}

    @FXML private void newSale(){NavigationManager.navigateOrReport("/fxml/pages/Sale.fxml");}
    private void edit(Sales sale){try{FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Sale.fxml"));Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);SalesController controller=loader.getController();controller.loadSale(service.getByInvoice(sale.getInvoiceNo()));NavigationManager.getInstance().showPreparedPage("/fxml/pages/Sale.fxml",root,controller);}catch(Exception e){error(e);}}
    private void viewSale(Sales sale){try{FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Sale.fxml"));Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);SalesController controller=loader.getController();controller.loadSale(service.getByInvoice(sale.getInvoiceNo()));controller.setViewMode(true);NavigationManager.getInstance().showPreparedPage("/fxml/pages/Sale.fxml",root,controller);}catch(Exception e){error(e);}}
    private void openSaleInvoicePdf(Sales sale){try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice not found. Refresh and try again.");Path pdf=InvoicePdfService.salesBodyOnly(full);java.awt.Desktop.getDesktop().open(pdf.toFile());log("SALE",sale.getId(),"PDF_OPENED_BODY_ONLY",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void openPdf(Sales sale){try{Path p=InvoicePdfService.sales(service.getByInvoice(sale.getInvoiceNo()));java.awt.Desktop.getDesktop().open(p.toFile());log("SALE",sale.getId(),"PDF_OPENED",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void openExcel(Sales sale){try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice "+sale.getInvoiceNo()+" was not found. Refresh the register and try again.");Path excel=ExcelOutputService.sales(full);if(java.awt.Desktop.isDesktopSupported())java.awt.Desktop.getDesktop().open(excel.toFile());else info("Excel file created: "+excel);log("SALE",sale.getId(),"EXCEL_OPENED",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void sendEmail(Sales sale){if(isApprovalLocked(sale)){warning("Admin approval is required before sending this Sale document.");return;}String stage="loading the sales invoice";try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice "+sale.getInvoiceNo()+" was not found. Refresh the register and try again.");if(full.getCustomer()==null)throw new IllegalStateException("No customer is linked to "+full.getInvoiceNo()+".");String recipient=safe(full.getCustomer().getEmail()).trim();if(recipient.isBlank())throw new IllegalStateException("Customer email is missing for "+full.getCustomer().getName()+". Update Customer Master and try again.");stage="generating the sales invoice PDF";Path pdf=InvoicePdfService.sales(full);stage="sending the email";EmailService.send(recipient,"Sales Invoice "+full.getInvoiceNo(),"Dear "+safe(full.getCustomer().getName())+",\n\nPlease find your sales invoice attached.\n\nRegards,\n"+org.example.config.ConfigManager.get("company.name","DSE ERP"),pdf);service.markEmailSent(full.getId());communication("SALE",full.getId(),"EMAIL",recipient,"Sales Invoice "+full.getInvoiceNo(),"SENT",null);refresh();info("Invoice emailed successfully to "+recipient+".");}catch(Exception failure){String recipient=sale.getCustomer()==null?"":safe(sale.getCustomer().getEmail());communication("SALE",sale.getId(),"EMAIL",recipient,"Sales Invoice "+sale.getInvoiceNo(),"FAILED",stage+": "+rootMessage(failure));error(new IllegalStateException("Email failed while "+stage+".\n\n"+rootMessage(failure),failure));}}
    private void sendWhatsapp(Sales sale){if(isApprovalLocked(sale)){warning("Admin approval is required before sharing this Sale document.");return;}try{Sales full=service.getByInvoice(sale.getInvoiceNo());String phone=digits(full.getCustomer().getPhone());if(phone.length()==10)phone="91"+phone;if(phone.isBlank()){warning("Customer mobile number is not available. Update it in Customer Master.");return;}String missing=PaymentMessageService.missingPaymentConfiguration();if(missing!=null)warning(missing+" The invoice can still be shared without a payment link.");Path pdf=InvoicePdfService.sales(full);WhatsappService.openWhatsappWithMessage(phone,PaymentMessageService.salesMessage(full),pdf,PaymentMessageService.configuredQrPath());info("WhatsApp is ready. The invoice and configured UPI QR are on the clipboard for attachment.");support.markWhatsapp("SALE",full.getId());communication("SALE",full.getId(),"WHATSAPP",phone,"Sales Invoice "+full.getInvoiceNo(),"SENT",null);refresh();}catch(Exception e){error(e);}}
    private void recordPayment(Sales sale){if(sale.getBalanceAmount()<=0){info("This invoice is already fully paid.");return;}Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Record Payment");d.setHeaderText(sale.getInvoiceNo()+" • Balance "+money(sale.getBalanceAmount()));TextField amount=new TextField(String.format(Locale.ROOT,"%.2f",sale.getBalanceAmount())),ref=new TextField(),notes=new TextField();ComboBox<String>mode=new ComboBox<>(FXCollections.observableArrayList("Cash","Bank","UPI","Cheque","Card","Other"));mode.setValue("Bank");DatePicker date=new DatePicker(BusinessClock.today());javafx.scene.layout.GridPane g=new javafx.scene.layout.GridPane();g.setHgap(10);g.setVgap(10);g.addRow(0,new Label("Date"),date);g.addRow(1,new Label("Amount"),amount);g.addRow(2,new Label("Mode"),mode);g.addRow(3,new Label("Reference"),ref);g.addRow(4,new Label("Notes"),notes);d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Record",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{double paid=Double.parseDouble(amount.getText());if(paid<=0||paid>sale.getBalanceAmount()+.01)throw new IllegalArgumentException("Payment must be between 0 and "+sale.getBalanceAmount());support.recordPayment(new SupportApiClient.PaymentRequest("SALE",sale.getId(),date.getValue().toString(),paid,mode.getValue(),ref.getText(),notes.getText(),sale.getCustomer()==null?"":sale.getCustomer().getName(),"RECEIPT",null,user()));log("SALE",sale.getId(),"PAYMENT_RECORDED",money(paid));refresh();info("Payment recorded.");}catch(Exception e){error(e);}});}


    private boolean isFinanciallyLocked(Sales sale){
        if(sale==null)return false;
        String payment=safe(sale.getPaymentStatus()).toUpperCase(java.util.Locale.ROOT);
        return sale.getPaidAmount()>.009 || sale.getBalanceAmount()<=.009 || payment.contains("PAID") || payment.contains("SETTLED") || payment.contains("PARTIAL");
    }
    private boolean isFullyPaid(Sales sale){
        if(sale==null)return false;
        String payment=safe(sale.getPaymentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
        return sale.getBalanceAmount()<=.009 || payment.contains("PAID") || payment.contains("SETTLED");
    }
    private boolean isApprovalLocked(Sales sale){String status=safe(sale==null?null:sale.getDocumentStatus()).trim().toUpperCase(Locale.ROOT);return Set.of("PENDING APPROVAL","REJECTED").contains(status);}
    private boolean isReturnEligible(Sales sale){
        if(sale==null||!isFullyPaid(sale))return false;
        String document=safe(sale.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
        return !java.util.Set.of("CANCELLED","DELETED","RETURNED").contains(document);
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

    @FXML private void exportSale(){File f=chooseSave("Export Sales Register","Sales_Register.xlsx","Excel","*.xlsx");if(f==null)return;try(Workbook w=new XSSFWorkbook();FileOutputStream out=new FileOutputStream(f)){Sheet sh=w.createSheet("Sales Register");String[]h={"Invoice No","Date","Customer","Mobile","GSTIN","Amount","Paid","Balance","Due Date","Payment Status","Email","WhatsApp"};Row row=sh.createRow(0);for(int i=0;i<h.length;i++)row.createCell(i).setCellValue(h[i]);int n=1;for(Sales s:filteredSales){row=sh.createRow(n++);Object[]v={s.getInvoiceNo(),s.getInvoiceDate().toString(),s.getCustomer().getName(),safe(s.getCustomer().getPhone()),safe(s.getCustomer().getGstin()),s.getTotalAmount(),s.getPaidAmount(),s.getBalanceAmount(),str(s.getDueDate()),s.getPaymentStatus(),s.isEmailSent()?"Sent":"Not Sent",s.isWhatsappSent()?"Sent":"Not Sent"};for(int i=0;i<v.length;i++){if(v[i] instanceof Number z)row.createCell(i).setCellValue(z.doubleValue());else row.createCell(i).setCellValue(String.valueOf(v[i]));}}for(int i=0;i<h.length;i++)sh.autoSizeColumn(i);w.write(out);info("Sales register exported.");}catch(Exception e){error(e);}}
    @FXML private void exportRegisterPdf(){File f=chooseSave("Export Sales Register PDF","Sales_Register.pdf","PDF","*.pdf");if(f==null)return;try{org.example.service.BrandedRegisterPdfService.export(f.toPath(),"Sales Register",new String[]{"Invoice","Date","Customer","Amount","Paid","Balance","Status"},filteredSales.stream().map(s->new String[]{s.getInvoiceNo(),s.getInvoiceDate().toString(),s.getCustomer().getName(),money(s.getTotalAmount()),money(s.getPaidAmount()),money(s.getBalanceAmount()),"PAID".equalsIgnoreCase(s.getPaymentStatus())?"COMPLETED":s.getPaymentStatus()}).toList(),new float[]{2,1.3f,2.6f,1.4f,1.4f,1.4f,1.2f});info("PDF exported.");}catch(Exception e){error(e);}}
    @FXML private void printRegister(){PrinterJob job=PrinterJob.createPrinterJob();if(job!=null&&job.showPrintDialog(tableSales.getScene().getWindow())){boolean ok=job.printPage(tableSales);if(ok)job.endJob();}}

    private File chooseSave(String title,String name,String label,String pattern){FileChooser c=new FileChooser();c.setTitle(title);c.setInitialFileName(name);c.getExtensionFilters().add(new FileChooser.ExtensionFilter(label,pattern));return c.showSaveDialog(tableSales.getScene().getWindow());}
    private void communication(String type,int id,String channel,String recipient,String subject,String status,String error){try{support.communication(new SupportApiClient.CommunicationRequest(type,id,channel,recipient,subject,status,error,user()));}catch(Exception ignored){}}
    private void log(String type,int id,String action,String detail){try{support.activity(type,id,action,detail,user());}catch(Exception ignored){}}
    private String user(){return SessionService.current()==null?"System":SessionService.current().getFullName();}
    private String dueLabel(Sales s){String document=safe(s.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);if(document.contains("DELETE"))return "Deleted";if(document.contains("CANCEL"))return "Cancelled";if(s.getBalanceAmount()<=.01)return "Paid";if(s.getDueDate()==null)return "Not set";long d=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),s.getDueDate());return d<0?"Overdue by "+Math.abs(d)+" days":d==0?"Due today":"Due in "+d+" days";}
    private String money(double v){return currency.format(v).replace("₹","₹ ");}private String safe(String v){return v==null?"":v;}private String lower(String v){return safe(v).toLowerCase(Locale.ROOT);}private String digits(String v){return safe(v).replaceAll("\\D","");}private String str(Object v){return v==null?"":v.toString();}private LocalDate date(String v){try{return v==null||v.isBlank()?null:LocalDate.parse(v);}catch(Exception e){return null;}}private double parseAmount(String v,double fallback){try{return v==null||v.isBlank()?fallback:Double.parseDouble(v.replace(",",""));}catch(Exception e){return fallback;}}
    private boolean confirm(String text){return org.example.util.ModernDialog.confirm(tableSales,"Confirmation","Are you sure?",text);}private void info(String m){org.example.util.ToastManager.success(tableSales,"Completed",m);}private void warning(String m){org.example.util.ModernDialog.warning(tableSales,"Warning","Please review",m);}private void error(Throwable e){e.printStackTrace();String message=rootMessage(e);org.example.util.ModernDialog.error(tableSales,"Operation failed","Something went wrong",message);}private String rootMessage(Throwable failure){Throwable root=failure;while(root.getCause()!=null)root=root.getCause();String message=root.getMessage();return message==null||message.isBlank()?root.getClass().getSimpleName():message;}
}
