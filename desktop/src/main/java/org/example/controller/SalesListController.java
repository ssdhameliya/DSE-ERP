package org.example.controller;

import org.example.util.BusinessClock;

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
import org.example.api.insights.InsightsApiClient;
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
    @FXML private Button btnNewSale,btnResetFilters,btnRefreshSales,btnApplyFilters,btnExportExcel,btnExportPdf,btnPrintRegister;
    @FXML private Button btnTodayRange,btnYesterdayRange,btnSevenDaysRange,btnThirtyDaysRange,btnCustomRange,btnCloseDetails;
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
    @FXML private javafx.scene.layout.VBox detailDrawer;
    @FXML private Label lblDetailInvoice,lblDetailDate,lblDetailStatus,lblDetailCustomer,lblDetailContact,lblDetailAmount,lblDetailPaid,lblDetailBalance,lblDetailDue,lblDetailCharges,lblDetailGstAmount,lblDetailTotalCharges,lblDetailChargeTax,lblDetailGstType,lblDetailGstin,lblDetailTransporter,lblDetailDoorDelivery,lblDetailVehicle,lblDetailContactPerson,lblDetailContactMobile;

    private final SalesService service=new SalesService();
    private final SupportApiClient support=new SupportApiClient();
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private List<Sales> allSales=new ArrayList<>(),filteredSales=new ArrayList<>();
    private int currentPage=0;
    private Sales selected;

    @FXML public void initialize(){
        configureColumns();configureFilters();configureActions();configurePaging();configureVisualIcons();
        configureExplicitTableHeaderIcons();
        simplifyFilters();
        detailDrawer.setVisible(false);detailDrawer.setManaged(false);mainSplit.setDividerPositions(1.0);
        tableSales.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null)showDetails(b);});
        // Keep row selection single-purpose. Invoice/payment actions are available from the explicit Action menu and detail drawer.
        tableSales.setRowFactory(view -> new TableRow<>());
        txtSearch.textProperty().addListener((o,a,b)->applyFilters());
    }

    private void showPaymentDetails(Sales sale) {
        try {
            InvoicePaymentDetailsDialog.show(tableSales, support, "SALE", sale.getId(), sale.getInvoiceNo(),
                    "Customer", sale.getCustomer() == null ? "" : sale.getCustomer().getName(),
                    sale.getTotalAmount(), sale.getPaidAmount(), sale.getBalanceAmount());
        } catch (Exception exception) { error(exception); }
    }


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
        setButtonIcon(btnApplyFilters,"filter");
        setButtonIcon(btnExportExcel,"excel");
        setButtonIcon(btnExportPdf,"pdf");
        setButtonIcon(btnPrintRegister,"print");
        setButtonIcon(btnTodayRange,"calendar");
        setButtonIcon(btnYesterdayRange,"calendar");
        setButtonIcon(btnSevenDaysRange,"calendar");
        setButtonIcon(btnThirtyDaysRange,"calendar");
        setButtonIcon(btnCustomRange,"calendar"); setButtonIcon(btnCloseDetails,"close");
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
    private TableCell<Sales,String> statusCell(String semantic){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("pill-success","pill-warning","pill-danger","pill-neutral");if(!e&&v!=null){boolean good=v.equalsIgnoreCase("COMPLETED")||v.equalsIgnoreCase("PAID")||v.equalsIgnoreCase("SENT");boolean pending=v.equalsIgnoreCase("IN PROGRESS")||v.equalsIgnoreCase("PARTIAL")||v.equalsIgnoreCase("PENDING");getStyleClass().add(good?"pill-success":pending?"pill-warning":"pill-danger");String icon = good ? semantic : (pending ? ("status".equals(semantic)?"reminder":semantic) : "error");setGraphic(IconFactory.compactIcon(icon,15));}}};}
    private TableCell<Sales,String> dueCell(){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("due-overdue","due-soon","due-paid");if(!e&&v!=null){boolean paid=v.equals("Paid"),overdue=v.startsWith("Overdue");getStyleClass().add(overdue?"due-overdue":paid?"due-paid":"due-soon");setGraphic(IconFactory.compactIcon(overdue?"error":paid?"complete":"reminder",15));}}};}
    private String documentStatus(Sales sale){
        String stored = safe(sale.getDocumentStatus()).trim();
        String normalized = stored.toUpperCase(java.util.Locale.ROOT);
        if (java.util.Set.of("CANCELLED","DELETED","RETURNED","DRAFT").contains(normalized)) return normalized;
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
        dpFrom.setValue(BusinessClock.today().minusDays(6));
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
        colAction.setCellFactory(c->new TableCell<>(){final MenuButton menu=new MenuButton();{
            menu.getProperties().put("erp.icon.semantic", "actions");
            menu.setGraphic(IconFactory.compactIcon("actions",15));
            add("Sale Invoice","pdf",e->openSaleInvoicePdf(row()));add("Edit Sale","edit",e->edit(row()));add("Duplicate Sale","sale",e->duplicate(row()));add("Print / Download PDF","print",e->openPdf(row()));add("Send Email","email",e->sendEmail(row()));add("Send WhatsApp","whatsapp",e->sendWhatsapp(row()));add("View / Record Payments","payment",e->openPayment(row()));add("Create Sales Return","return",e->createReturn(row()));add("Attach Document","attachment",e->attach(row()));add("Notes / Remarks","document",e->notes(row()));add("Send Reminder","reminder",e->createReminder(row()));MenuItem del=add("Delete Sale","delete",e->delete(row()));del.getStyleClass().add("danger-menu-item");menu.setOnShowing(e->{Sales current=getTableRow()==null?null:getTableRow().getItem();String status=current==null?"":safe(current.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);boolean locked=isFinanciallyLocked(current);del.setDisable(locked||"DELETED".equals(status));del.setVisible(!locked);});menu.getStyleClass().add("row-actions");menu.setGraphic(IconFactory.compactIcon("actions",16));menu.setText("Actions");menu.setContentDisplay(ContentDisplay.LEFT);menu.setGraphicTextGap(6);menu.setTooltip(new Tooltip("Actions"));}
            private Sales row(){Sales value=getTableRow()==null?null:getTableRow().getItem();if(value==null)throw new IllegalStateException("This sales row is no longer available. Refresh the register and try again.");return value;}
            private MenuItem add(String t,String icon,javafx.event.EventHandler<ActionEvent> h){MenuItem i=new MenuItem(t);i.setGraphic(IconFactory.icon(icon));i.setOnAction(event->{try{h.handle(event);}catch(Throwable failure){error(failure);}});menu.getItems().add(i);return i;}
            protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:menu);setAlignment(Pos.CENTER);}});
    }

    @FXML public void refresh(){
        UiTaskExecutor.submitLatest("sales-register-load", () -> new ArrayList<>(service.getAll()), this::applyLoadedSales, this::error);
    }
    private void applyLoadedSales(ArrayList<Sales> loaded){
        long started=System.nanoTime();
        allSales=loaded;
        cmbCustomer.getItems().setAll("All customers");cmbCustomer.getItems().addAll(allSales.stream().map(s->s.getCustomer().getName()).filter(Objects::nonNull).distinct().sorted().toList());if(cmbCustomer.getValue()==null)cmbCustomer.setValue("All customers");
        loadSavedViews();updateMetrics();applyFilters();
        if(!PlatformUiSupport.isMac()) javafx.application.Platform.runLater(this::updateCharts);
        long ms=(System.nanoTime()-started)/1_000_000L;if(ms>=20)PerformanceMonitor.event("controller-phase","sales-register-apply | "+ms+" ms");
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
        if(allSales.isEmpty() || ScreenRefreshPolicy.shouldRefresh("sales-register", ScreenRefreshPolicy.Mode.WHEN_STALE, java.time.Duration.ofSeconds(60))) refresh();
    }
    @Override public void onScreenHidden(){UiTaskExecutor.cancel("sales-register-load");}

    private void showDetails(Sales sale){
        selected=sale;
        detailDrawer.setManaged(true);
        detailDrawer.setVisible(true);
        mainSplit.setDividerPositions(.81);
        lblDetailInvoice.setText(sale.getInvoiceNo());
        lblDetailDate.setText(sale.getInvoiceDate().format(BusinessClock.dateFormatter()));
        lblDetailStatus.setText(documentStatus(sale));
        lblDetailCustomer.setText(sale.getCustomer().getName());
        lblDetailContact.setText(safe(sale.getCustomer().getPhone())+"\n"+safe(sale.getCustomer().getEmail())+"\n"+safe(sale.getCustomer().getGstin()));
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
        if (lblDetailTransporter != null) lblDetailTransporter.setText(safe(sale.getTransporter()).isBlank() ? "Not Applicable" : sale.getTransporter());
        if (lblDetailDoorDelivery != null) lblDetailDoorDelivery.setText(safe(sale.getDoorDelivery()).isBlank() ? "Not Applicable" : sale.getDoorDelivery());
        if (lblDetailVehicle != null) lblDetailVehicle.setText(safe(sale.getVehicleNumber()).isBlank() ? "Not Applicable" : sale.getVehicleNumber());
        if (lblDetailContactPerson != null) lblDetailContactPerson.setText(safe(sale.getContactPerson()).isBlank() ? "Not Applicable" : sale.getContactPerson());
        if (lblDetailContactMobile != null) lblDetailContactMobile.setText(safe(sale.getContactPersonMobile()).isBlank() ? "Not Applicable" : sale.getContactPersonMobile());
    }
    @FXML private void closeDetails(){selected=null;detailDrawer.setVisible(false);detailDrawer.setManaged(false);mainSplit.setDividerPositions(1);tableSales.getSelectionModel().clearSelection();}
    private Sales requireSelected(){if(selected==null){warning("Select an invoice first.");return null;}return selected;}
    @FXML private void emailSelected(){Sales s=requireSelected();if(s!=null)sendEmail(s);}@FXML private void whatsappSelected(){Sales s=requireSelected();if(s!=null)sendWhatsapp(s);}@FXML private void editSelectedSale(){Sales s=requireSelected();if(s!=null)edit(s);}@FXML private void recordSelectedPayment(){Sales s=requireSelected();if(s!=null)openPayment(s);}@FXML private void remindSelected(){Sales s=requireSelected();if(s!=null)createReminder(s);}
    private void openInvoiceDetails(Sales s){openPayment(s);}
    private void openPayment(Sales s){
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
        StackPane pane = tableSales.getScene() == null ? null : (StackPane) tableSales.getScene().lookup("#contentPane");
        NavigationManager manager = NavigationManager.forPane(pane);
        if (manager == null) {
            warning(screenName + " could not be opened because the application navigation shell is not available.");
            return;
        }
        if (manager.loadPage(fxml)) return;
        // loadPage can reject a click while another navigation is finishing. Retry once on the next FX pulse.
        javafx.application.Platform.runLater(() -> {
            if (!manager.loadPage(fxml)) {
                warning(screenName + " could not be opened. Please try again.");
            }
        });
    }

    @FXML private void showToday(){applyDateRange(BusinessClock.today(),BusinessClock.today());}
    @FXML private void showYesterday(){LocalDate day=BusinessClock.today().minusDays(1);applyDateRange(day,day);}
    @FXML private void showSevenDays(){applyDateRange(BusinessClock.today().minusDays(6),BusinessClock.today());}
    @FXML private void showThirtyDays(){applyDateRange(BusinessClock.today().minusDays(29),BusinessClock.today());}
    @FXML private void focusCustomRange(){dpFrom.requestFocus();}
    private void applyDateRange(LocalDate from,LocalDate to){dpFrom.setValue(from);dpTo.setValue(to);applyFilters();}

    @FXML private void toggleAdvanced(){advancedFilters.setManaged(btnAdvanced.isSelected());advancedFilters.setVisible(btnAdvanced.isSelected());}
    @FXML private void resetFilters(){txtSearch.clear();txtInvoice.clear();txtAmountFrom.clear();txtAmountTo.clear();dpFrom.setValue(BusinessClock.today().minusDays(6));dpTo.setValue(BusinessClock.today());cmbCustomer.setValue("All customers");cmbPaymentStatus.setValue("All");cmbPaymentDue.setValue("All");cmbMailStatus.setValue("All");cmbWhatsappStatus.setValue("All");cmbInvoiceType.setValue("All");applyFilters();}
    private void renderChips(){activeFilterChips.getChildren().clear();addChip("From",dpFrom.getValue());addChip("To",dpTo.getValue());addChip("Payment",nonAll(cmbPaymentStatus));addChip("Due",nonAll(cmbPaymentDue));addChip("Email",nonAll(cmbMailStatus));addChip("WhatsApp",nonAll(cmbWhatsappStatus));}
    private Object nonAll(ComboBox<String>b){return b.getValue()==null||b.getValue().equals("All")?null:b.getValue();}private void addChip(String name,Object value){if(value==null)return;Label chip=new Label(name+": "+value);chip.getStyleClass().add("filter-chip");activeFilterChips.getChildren().add(chip);}

    @FXML private void saveCurrentView(){TextInputDialog d=new OwnedTextInputDialog();d.setTitle("Save Filter View");d.setHeaderText("Save the current sales filters");d.setContentText("View name:");d.showAndWait().map(String::trim).filter(x->!x.isBlank()).ifPresent(name->{String data=String.join("|",safe(txtInvoice.getText()),safe(cmbCustomer.getValue()),str(dpFrom.getValue()),str(dpTo.getValue()),safe(cmbPaymentStatus.getValue()),safe(cmbPaymentDue.getValue()),safe(cmbMailStatus.getValue()),safe(cmbWhatsappStatus.getValue()),safe(cmbInvoiceType.getValue()),safe(txtAmountFrom.getText()),safe(txtAmountTo.getText()));try{Integer uid=SessionService.current()==null?null:SessionService.current().getId();support.saveView(uid,"SALES_REGISTER",name,data);loadSavedViews();info("Saved view created.");}catch(Exception e){error(e);}});}
    private void loadSavedViews(){savedViewsMenu.getItems().clear();try{Integer uid=SessionService.current()==null?null:SessionService.current().getId();for(SupportApiClient.SavedView v:support.savedViews("SALES_REGISTER",uid)){MenuItem i=new MenuItem(v.name());i.setOnAction(e->applySaved(v.data()));savedViewsMenu.getItems().add(i);}}catch(Exception ignored){}if(savedViewsMenu.getItems().isEmpty())savedViewsMenu.getItems().add(new MenuItem("No saved views"));}
    private void applySaved(String data){String[]x=data.split("\\|",-1);if(x.length<11)return;txtInvoice.setText(x[0]);cmbCustomer.setValue(x[1]);dpFrom.setValue(date(x[2]));dpTo.setValue(date(x[3]));cmbPaymentStatus.setValue(x[4]);cmbPaymentDue.setValue(x[5]);cmbMailStatus.setValue(x[6]);cmbWhatsappStatus.setValue(x[7]);cmbInvoiceType.setValue(x[8]);txtAmountFrom.setText(x[9]);txtAmountTo.setText(x[10]);applyFilters();}

    @FXML private void newSale(){StackPane pane=(StackPane)tableSales.getScene().lookup("#contentPane");if(pane!=null)NavigationManager.forPane(pane).loadPage("/fxml/pages/Sale.fxml");}
    private void edit(Sales sale){try{FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Sale.fxml"));Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);((SalesController)loader.getController()).loadSale(service.getByInvoice(sale.getInvoiceNo()));StackPane pane=(StackPane)tableSales.getScene().lookup("#contentPane");pane.getChildren().setAll(root);}catch(Exception e){error(e);}}
    private void viewSale(Sales sale){try{FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Sale.fxml"));Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);SalesController controller=loader.getController();controller.loadSale(service.getByInvoice(sale.getInvoiceNo()));controller.setViewMode(true);StackPane pane=(StackPane)tableSales.getScene().lookup("#contentPane");pane.getChildren().setAll(root);}catch(Exception e){error(e);}}
    private void openSaleInvoicePdf(Sales sale){try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice not found. Refresh and try again.");Path pdf=InvoicePdfService.salesBodyOnly(full);java.awt.Desktop.getDesktop().open(pdf.toFile());log("SALE",sale.getId(),"PDF_OPENED_BODY_ONLY",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void openPdf(Sales sale){try{Path p=InvoicePdfService.sales(service.getByInvoice(sale.getInvoiceNo()));java.awt.Desktop.getDesktop().open(p.toFile());log("SALE",sale.getId(),"PDF_OPENED",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void sendEmail(Sales sale){String stage="loading the sales invoice";try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice "+sale.getInvoiceNo()+" was not found. Refresh the register and try again.");if(full.getCustomer()==null)throw new IllegalStateException("No customer is linked to "+full.getInvoiceNo()+".");String recipient=safe(full.getCustomer().getEmail()).trim();if(recipient.isBlank())throw new IllegalStateException("Customer email is missing for "+full.getCustomer().getName()+". Update Customer Master and try again.");stage="generating the sales invoice PDF";Path pdf=InvoicePdfService.sales(full);stage="sending the email";EmailService.send(recipient,"Sales Invoice "+full.getInvoiceNo(),"Dear "+safe(full.getCustomer().getName())+",\n\nPlease find your sales invoice attached.\n\nRegards,\n"+org.example.config.ConfigManager.get("company.name","DSE ERP"),pdf);service.markEmailSent(full.getId());communication("SALE",full.getId(),"EMAIL",recipient,"Sales Invoice "+full.getInvoiceNo(),"SENT",null);refresh();info("Invoice emailed successfully to "+recipient+".");}catch(Exception failure){String recipient=sale.getCustomer()==null?"":safe(sale.getCustomer().getEmail());communication("SALE",sale.getId(),"EMAIL",recipient,"Sales Invoice "+sale.getInvoiceNo(),"FAILED",stage+": "+rootMessage(failure));error(new IllegalStateException("Email failed while "+stage+".\n\n"+rootMessage(failure),failure));}}
    private void sendWhatsapp(Sales sale){try{Sales full=service.getByInvoice(sale.getInvoiceNo());String phone=digits(full.getCustomer().getPhone());if(phone.length()==10)phone="91"+phone;if(phone.isBlank()){warning("Customer mobile number is not available. Update it in Customer Master.");return;}String missing=PaymentMessageService.missingPaymentConfiguration();if(missing!=null)warning(missing+" The invoice can still be shared without a payment link.");Path pdf=InvoicePdfService.sales(full);WhatsappService.openWhatsappWithMessage(phone,PaymentMessageService.salesMessage(full),pdf,PaymentMessageService.configuredQrPath());info("WhatsApp is ready. The invoice and configured UPI QR are on the clipboard for attachment.");support.markWhatsapp("SALE",full.getId());communication("SALE",full.getId(),"WHATSAPP",phone,"Sales Invoice "+full.getInvoiceNo(),"SENT",null);refresh();}catch(Exception e){error(e);}}
    private void recordPayment(Sales sale){if(sale.getBalanceAmount()<=0){info("This invoice is already fully paid.");return;}Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Record Payment");d.setHeaderText(sale.getInvoiceNo()+" • Balance "+money(sale.getBalanceAmount()));TextField amount=new TextField(String.format(Locale.ROOT,"%.2f",sale.getBalanceAmount())),ref=new TextField(),notes=new TextField();ComboBox<String>mode=new ComboBox<>(FXCollections.observableArrayList("Cash","Bank","UPI","Cheque","Card","Other"));mode.setValue("Bank");DatePicker date=new DatePicker(BusinessClock.today());javafx.scene.layout.GridPane g=new javafx.scene.layout.GridPane();g.setHgap(10);g.setVgap(10);g.addRow(0,new Label("Date"),date);g.addRow(1,new Label("Amount"),amount);g.addRow(2,new Label("Mode"),mode);g.addRow(3,new Label("Reference"),ref);g.addRow(4,new Label("Notes"),notes);d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Record",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{double paid=Double.parseDouble(amount.getText());if(paid<=0||paid>sale.getBalanceAmount()+.01)throw new IllegalArgumentException("Payment must be between 0 and "+sale.getBalanceAmount());support.recordPayment(new SupportApiClient.PaymentRequest("SALE",sale.getId(),date.getValue().toString(),paid,mode.getValue(),ref.getText(),notes.getText(),sale.getCustomer()==null?"":sale.getCustomer().getName(),"RECEIPT",null,user()));log("SALE",sale.getId(),"PAYMENT_RECORDED",money(paid));refresh();info("Payment recorded.");}catch(Exception e){error(e);}});}
    private void createReminder(Sales sale){DatePicker due=new DatePicker(sale.getDueDate()==null?BusinessClock.today().plusDays(1):sale.getDueDate());TextInputDialog dialog=new OwnedTextInputDialog("Payment reminder for "+sale.getInvoiceNo());dialog.setTitle("Create Reminder");dialog.setHeaderText("Reminder date: "+due.getValue());dialog.setContentText("Reminder text:");dialog.showAndWait().ifPresent(text->{try{String priority=sale.getDueDate()!=null&&sale.getDueDate().isBefore(BusinessClock.today())?"URGENT":"NORMAL";String notes="Customer: "+sale.getCustomer().getName()+"; Balance: "+money(sale.getBalanceAmount());new InsightsApiClient().saveReminder(new InsightsApiClient.ReminderDto(null,text,sale.getInvoiceNo(),due.getValue().toString(),priority,notes,"OPEN",org.example.service.SessionService.current()==null?"System":org.example.service.SessionService.current().getUsername(),null));NotificationService.add("Reminder created for "+sale.getInvoiceNo());info("Reminder created.");}catch(Exception e){error(e);}});}
    private void attach(Sales sale){FileChooser chooser=new FileChooser();File file=chooser.showOpenDialog(tableSales.getScene().getWindow());if(file==null)return;try{support.attachment("SALE",sale.getId(),file.getAbsolutePath());log("SALE",sale.getId(),"DOCUMENT_ATTACHED",file.getName());info("Document attached to "+sale.getInvoiceNo()+".");}catch(Exception e){error(e);}}
    private void notes(Sales sale){TextInputDialog dialog=new OwnedTextInputDialog(safe(sale.getNotes()));dialog.setTitle("Sales Notes");dialog.setHeaderText("Notes / Remarks • "+sale.getInvoiceNo());dialog.showAndWait().ifPresent(value->{try{support.notes("SALE",sale.getId(),value);log("SALE",sale.getId(),"NOTES_UPDATED","Invoice notes updated");refresh();}catch(Exception e){error(e);}});}

    private boolean isFinanciallyLocked(Sales sale){
        if(sale==null)return false;
        String payment=safe(sale.getPaymentStatus()).toUpperCase(java.util.Locale.ROOT);
        String document=safe(sale.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);
        return sale.getPaidAmount()>.009 || sale.getBalanceAmount()<=.009 || payment.contains("PAID") || payment.contains("SETTLED") || document.contains("COMPLETED");
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
            info(sale.getInvoiceNo()+" deleted. Stock restored and record retained.");
        }catch(Exception e){error(e);}
    }
    private void createReturn(Sales sale){Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null){warning("Sales invoice not found. Refresh and try again.");return;}List<ReturnEditorService.InvoiceItem> items=full.getLines().stream().map(line->new ReturnEditorService.InvoiceItem(line.getItemCode(),line.getItemDescription(),line.getQuantity(),line.getRate(),line.getGstPercent())).toList();ReturnEditorService.show(tableSales.getScene().getWindow(),ReturnEditorService.Type.SALES,sale.getInvoiceNo(),sale.getCustomer().getName(),sale.getCustomer().getId(),items).ifPresent(no->{refresh();info("Sales return created: "+no);});}
    private void duplicate(Sales sale){if(!confirm("Duplicate "+sale.getInvoiceNo()+" as a new sales invoice?"))return;try{String no=support.duplicateSale(sale.getId(),user());refresh();info("Created "+no);}catch(Exception e){error(e);}}
    private void delete(Sales sale){
        if (sale == null) return;
        String status = safe(sale.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);
        if ("DELETED".equals(status)) { info("This sale is already marked as deleted."); return; }
        if (isFinanciallyLocked(sale)) { info("Paid, partially paid, or settled sales cannot be deleted. Use the sales return/reversal workflow instead."); return; }
        String stockText = "CANCELLED".equals(status)
            ? "Stock was already restored when this sale was cancelled."
            : "Stock will be restored.";
        if(!confirm("Delete "+sale.getInvoiceNo()+"?\n\nThe record will NOT be removed. It will remain in the Sales Register with status DELETED and zero outstanding balance.\n"+stockText))return;
        try{
            service.delete(sale.getInvoiceNo());
            log("SALE",sale.getId(),"DELETED",sale.getInvoiceNo());
            refresh();
            closeDetails();
            info(sale.getInvoiceNo()+" marked as deleted. Audit record retained.");
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
    private boolean confirm(String text){return org.example.util.ModernDialog.confirm(tableSales,"Confirmation","Are you sure?",text);}private void info(String m){org.example.util.ToastManager.success(tableSales,"Completed",m);}private void warning(String m){org.example.util.ModernDialog.warning(tableSales,"Warning","Please review",m);org.example.util.ToastManager.warning(tableSales,"Warning",m);}private void error(Throwable e){e.printStackTrace();String message=rootMessage(e);org.example.util.ModernDialog.error(tableSales,"Operation failed","Something went wrong",message);}private String rootMessage(Throwable failure){Throwable root=failure;while(root.getCause()!=null)root=root.getCause();String message=root.getMessage();return message==null||message.isBlank()?root.getClass().getSimpleName():message;}
}
