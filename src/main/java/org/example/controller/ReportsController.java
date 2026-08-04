package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.database.DatabaseManager;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.BusinessReportService;
import org.example.util.*;

import java.io.File;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class ReportsController implements ScreenLifecycle {
    private static final String TASK_KEY = "reports-load";
    @FXML private Label lblPurchase,lblSales,lblStock,lblLowStock,lblProfit,lblReceivables,lblCustomers,lblMargin;
    @FXML private StackPane reportSalesIcon,reportPurchaseIcon,reportProfitIcon,reportReceivableIcon,reportStockIcon,reportCustomerIcon;
    @FXML private DatePicker dpFrom,dpTo;
    @FXML private ComboBox<String> cmbReportType,cmbParty,cmbItem,cmbSalesPerson;
    @FXML private LineChart<String,Number> chartTrend;
    @FXML private BarChart<String,Number> chartCustomers;
    @FXML private PieChart chartItems,chartComparison;
    @FXML private ProgressBar profitProgress;
    @FXML private ListView<String> paymentSummary,stockSummary;
    @FXML private TableView<String[]> tblSales,tblPurchases;
    @FXML private TableColumn<String[],String> colSaleNo,colSaleDate,colSaleParty,colSaleAmount,colSaleStatus;
    @FXML private TableColumn<String[],String> colPurchaseNo,colPurchaseDate,colPurchaseParty,colPurchaseAmount,colPurchaseStatus;
    @FXML private Button btnRefresh,btnApply,btnReset,btnViewSales,btnViewPurchases;
    @FXML private MenuButton btnExport;
    @FXML private MenuItem miExcel,miPdf;
    private final BusinessReportService reportService=new BusinessReportService();
    private volatile boolean loaded;

    @FXML public void initialize(){
        configureMacPerformanceMode();
        configureExplicitTableHeaderIcons(); configureIcons(); configureStatusCells();
        dpFrom.setValue(LocalDate.now().withDayOfMonth(1)); dpTo.setValue(LocalDate.now());
        cmbReportType.getItems().setAll("All Reports","Sales","Purchase","Inventory","Payments"); cmbReportType.getSelectionModel().selectFirst();
        setCell(colSaleNo,0);setCell(colSaleDate,1);setCell(colSaleParty,2);setCell(colSaleAmount,3);setCell(colSaleStatus,4);
        setCell(colPurchaseNo,0);setCell(colPurchaseDate,1);setCell(colPurchaseParty,2);setCell(colPurchaseAmount,3);setCell(colPurchaseStatus,4);
        applyFilters(readFilters()); configureReportTables(); refresh();
    }
    private void setCell(TableColumn<String[],String> column,int index){column.setCellValueFactory(v->new SimpleStringProperty(v.getValue()[index]));}
    private void loadFiltersAsync(){
        UiTaskExecutor.submitLatest("reports-filters", this::readFilters, this::applyFilters, error -> PerformanceMonitor.event("reports-filters-error", error.getMessage()));
    }
    private FilterData readFilters(){ return new FilterData(list("SELECT name FROM party_master WHERE COALESCE(is_active,1)=1 ORDER BY name"), list("SELECT description FROM item_master WHERE COALESCE(is_active,1)=1 ORDER BY description"), list("SELECT DISTINCT salesperson FROM sales_header WHERE COALESCE(salesperson,'')<>'' ORDER BY salesperson")); }
    private void applyFilters(FilterData data){ setOptions(cmbParty,"All Customers / Suppliers",data.parties()); setOptions(cmbItem,"All Items",data.items()); setOptions(cmbSalesPerson,"All Sales Persons",data.salespeople()); }
    private void setOptions(ComboBox<String> box,String all,List<String> values){box.getItems().setAll(all);box.getItems().addAll(values);box.getSelectionModel().selectFirst();}
    private List<String> list(String sql){List<String> out=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())out.add(r.getString(1));}catch(SQLException e){PerformanceMonitor.event("report-filter-query",e.getMessage());}return out;}

    @FXML private void resetFilters(){cmbReportType.getSelectionModel().selectFirst();cmbParty.getSelectionModel().selectFirst();cmbItem.getSelectionModel().selectFirst();cmbSalesPerson.getSelectionModel().selectFirst();dpFrom.setValue(LocalDate.now().withDayOfMonth(1));dpTo.setValue(LocalDate.now());refresh();}
    @FXML private void refresh(){
        if(dpFrom.getValue()==null||dpTo.getValue()==null||dpFrom.getValue().isAfter(dpTo.getValue())){error("Choose a valid reporting date range.");return;}
        String from=dpFrom.getValue().toString(),to=dpTo.getValue().toString();
        setBusy(true);
        try{ReportData data=loadReport(from,to);applyReport(data);loaded=true;ScreenRefreshPolicy.markRefreshed("reports");}
        catch(Exception failure){error("Could not load report data: "+failure.getMessage());}
        finally{setBusy(false);}
    }
    private ReportData loadReport(String from,String to) throws SQLException {
        PerformanceMonitor.start("reports-query-bundle");
        try {
            double sales=number("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE invoice_date BETWEEN ? AND ?",from,to);
            double purchase=number("SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE invoice_date BETWEEN ? AND ?",from,to);
            double receivables=number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header WHERE invoice_date BETWEEN ? AND ?",from,to);
            double stock=number("SELECT COALESCE(SUM(opening_stock*purchase_price),0) FROM item_master"); double profit=sales-purchase;
            long low=(long)number("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)");
            long customers=(long)number("SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active,1)=1");
            List<Point> trend=queryPoints("SELECT invoice_date,SUM(total_amount) FROM sales_header WHERE invoice_date BETWEEN ? AND ? GROUP BY invoice_date ORDER BY invoice_date",from,to);
            List<Point> customerPoints=queryPoints("SELECT COALESCE(pm.name,'Unknown Customer'),SUM(sh.total_amount) amount FROM sales_header sh LEFT JOIN party_master pm ON pm.id=sh.customer_id WHERE sh.invoice_date BETWEEN ? AND ? GROUP BY pm.id,pm.name ORDER BY amount DESC LIMIT 5",from,to);
            List<Point> itemPoints=queryPoints("SELECT COALESCE(im.description,sl.item_code),SUM(sl.line_total) amount FROM sales_line sl JOIN sales_header sh ON sh.id=sl.sales_id LEFT JOIN item_master im ON im.item_code=sl.item_code WHERE sh.invoice_date BETWEEN ? AND ? GROUP BY sl.item_code,im.description ORDER BY amount DESC LIMIT 5",from,to);
            List<String[]> salesRows=queryRows("SELECT sh.invoice_no,sh.invoice_date,pm.name,sh.total_amount,COALESCE(sh.payment_status,'PENDING') FROM sales_header sh LEFT JOIN party_master pm ON pm.id=sh.customer_id WHERE sh.invoice_date BETWEEN ? AND ? ORDER BY sh.invoice_date DESC,sh.id DESC LIMIT 8",from,to);
            List<String[]> purchaseRows=queryRows("SELECT ph.invoice_no,ph.invoice_date,pm.name,ph.total_amount,COALESCE(ph.payment_status,'PENDING') FROM purchase_header ph LEFT JOIN party_master pm ON pm.id=ph.supplier_id WHERE ph.invoice_date BETWEEN ? AND ? ORDER BY ph.invoice_date DESC,ph.id DESC LIMIT 8",from,to);
            double salesPaid=number("SELECT COALESCE(SUM(paid_amount),0) FROM sales_header WHERE invoice_date BETWEEN ? AND ?",from,to);
            double payables=number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM purchase_header WHERE invoice_date BETWEEN ? AND ?",from,to);
            double purchasesPaid=number("SELECT COALESCE(SUM(paid_amount),0) FROM purchase_header WHERE invoice_date BETWEEN ? AND ?",from,to);
            long items=(long)number("SELECT COUNT(*) FROM item_master"),out=(long)number("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=0");
            return new ReportData(sales,purchase,profit,receivables,stock,low,customers,trend,customerPoints,itemPoints,salesRows,purchaseRows,salesPaid,payables,purchasesPaid,items,out);
        } finally { PerformanceMonitor.finish("reports-query-bundle"); }
    }
    private void applyReport(ReportData d){
        lblSales.setText(money(d.sales()));lblPurchase.setText(money(d.purchase()));lblProfit.setText(money(d.profit()));lblReceivables.setText(money(d.receivables()));lblStock.setText(money(d.stock()));
        lblLowStock.setText(d.low()+" low-stock items");lblCustomers.setText(String.valueOf(d.customers()));
        double margin=d.sales()==0?0:(d.profit()/d.sales())*100;lblMargin.setText(String.format("%.2f%%",margin));profitProgress.setProgress(Math.max(0,Math.min(1,margin/100)));
        XYChart.Series<String,Number> trend=new XYChart.Series<>();trend.setName("Sales");d.trend().forEach(p->trend.getData().add(new XYChart.Data<>(p.label(),p.value())));if(chartTrend!=null&&chartTrend.isManaged())chartTrend.getData().setAll(trend);
        XYChart.Series<String,Number> customers=new XYChart.Series<>();customers.setName("Sales by customer");d.customerPoints().forEach(p->customers.getData().add(new XYChart.Data<>(p.label(),p.value())));if(chartCustomers!=null&&chartCustomers.isManaged())chartCustomers.getData().setAll(customers);
        if(chartItems!=null&&chartItems.isManaged())chartItems.setData(FXCollections.observableArrayList(d.itemPoints().stream().map(p->new PieChart.Data(p.label(),p.value())).toList()));
        if(chartComparison!=null&&chartComparison.isManaged())chartComparison.setData(FXCollections.observableArrayList(new PieChart.Data("Sales",d.sales()),new PieChart.Data("Purchases",d.purchase())));
        tblSales.getItems().setAll(d.salesRows());tblPurchases.getItems().setAll(d.purchaseRows());
        paymentSummary.getItems().setAll("Total Receivables     "+money(d.receivables()),"Received Amount      "+money(d.salesPaid()),"Total Payables        "+money(d.payables()),"Paid Amount           "+money(d.purchasesPaid()));
        stockSummary.getItems().setAll("Total Items           "+d.items(),"Low Stock Items       "+d.low(),"Out of Stock          "+d.out(),"Stock Value           "+money(d.stock()));
    }
    private void setBusy(boolean busy){btnRefresh.setDisable(busy);btnApply.setDisable(busy);btnReset.setDisable(busy);tblSales.setDisable(busy);tblPurchases.setDisable(busy);}
    private List<Point> queryPoints(String sql,String from,String to)throws SQLException{List<Point> out=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,from);p.setString(2,to);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new Point(Objects.toString(r.getString(1),"—"),r.getDouble(2)));}}return out;}
    private List<String[]> queryRows(String sql,String from,String to)throws SQLException{List<String[]> out=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,from);p.setString(2,to);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new String[]{r.getString(1),r.getString(2),Objects.toString(r.getString(3),"—"),money(r.getDouble(4)),r.getString(5)});}}return out;}
    private double number(String sql,String... values)throws SQLException{try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setString(i+1,values[i]);try(ResultSet r=p.executeQuery()){return r.next()?r.getDouble(1):0;}}}

    @Override public void onScreenShown(boolean reusedFromCache){ if(!loaded || ScreenRefreshPolicy.shouldRefresh("reports", ScreenRefreshPolicy.Mode.WHEN_STALE)) refresh(); }
    @Override public void onScreenHidden(){ UiTaskExecutor.cancelPrefix("reports-"); }
    @FXML private void exportPdf(){export("PDF Report","business-report.pdf","*.pdf",true);}@FXML private void exportExcel(){export("Excel Report","business-report.xlsx","*.xlsx",false);}
    @FXML private void viewAllSales(){navigate("/fxml/pages/SalesList.fxml");}@FXML private void viewAllPurchases(){navigate("/fxml/pages/PurchaseList.fxml");}
    private void navigate(String page){StackPane content=(StackPane)dpFrom.getScene().lookup("#contentPane");if(content!=null)new NavigationManager(content).loadPage(page);}
    private void export(String title,String name,String ext,boolean pdf){FileChooser f=new FileChooser();f.setTitle(title);f.setInitialFileName(name);f.getExtensionFilters().add(new FileChooser.ExtensionFilter(title,ext));File selected=f.showSaveDialog(dpFrom.getScene().getWindow());if(selected==null)return;Path path=selected.toPath();String suffix=pdf?".pdf":".xlsx";if(!path.toString().toLowerCase(Locale.ROOT).endsWith(suffix))path=Path.of(path+suffix);final Path target=path;UiTaskExecutor.submitLatest("reports-export",()->{if(pdf)reportService.exportPdf(target,dpFrom.getValue(),dpTo.getValue());else reportService.exportExcel(target,dpFrom.getValue(),dpTo.getValue());return target;},done->new OwnedAlert(Alert.AlertType.INFORMATION,"Report created successfully:\n"+done).showAndWait(),e->error("Could not create report: "+e.getMessage()));}
    private String money(double n){return "₹ "+String.format("%,.2f",n);}private void error(String message){Alert a=new OwnedAlert(Alert.AlertType.ERROR,message);a.setHeaderText("Reporting error");a.showAndWait();}
    private void configureReportTables(){
        tblSales.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tblPurchases.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colSaleNo.setMinWidth(110); colSaleDate.setMinWidth(95); colSaleParty.setMinWidth(135); colSaleAmount.setMinWidth(105); colSaleStatus.setMinWidth(100);
        colPurchaseNo.setMinWidth(110); colPurchaseDate.setMinWidth(95); colPurchaseParty.setMinWidth(135); colPurchaseAmount.setMinWidth(105); colPurchaseStatus.setMinWidth(100);
    }
    private void configureIcons(){btnRefresh.setGraphic(IconFactory.icon("refresh",16));btnApply.setGraphic(IconFactory.icon("filter",16));btnReset.setGraphic(IconFactory.icon("reset",16));btnExport.setGraphic(IconFactory.icon("export",16));btnViewSales.setGraphic(IconFactory.icon("view",15));btnViewPurchases.setGraphic(IconFactory.icon("view",15));miExcel.setGraphic(IconFactory.icon("excel",15));miPdf.setGraphic(IconFactory.icon("pdf",15));
        reportSalesIcon.getChildren().setAll(IconFactory.icon("sales",22)); reportPurchaseIcon.getChildren().setAll(IconFactory.icon("purchase",22));
        reportProfitIcon.getChildren().setAll(IconFactory.icon("chart",22)); reportReceivableIcon.getChildren().setAll(IconFactory.icon("payment",22));
        reportStockIcon.getChildren().setAll(IconFactory.icon("inventory",22)); reportCustomerIcon.getChildren().setAll(IconFactory.icon("customer",22));
    }
    private void configureStatusCells(){statusCell(colSaleStatus);statusCell(colPurchaseStatus);}
    private void statusCell(TableColumn<String[],String> column){column.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String value,boolean empty){super.updateItem(value,empty);getStyleClass().removeAll("report-status-paid","report-status-pending","report-status-other");if(empty||value==null){setText(null);setGraphic(null);return;}setText(value);String v=value.toUpperCase(Locale.ROOT);getStyleClass().add(v.contains("PAID")||v.contains("COMPLETED")?"report-status-paid":v.contains("PENDING")?"report-status-pending":"report-status-other");}});}
    private void configureExplicitTableHeaderIcons(){IconFactory.applyTableHeaderIcon(colSaleNo,"document");IconFactory.applyTableHeaderIcon(colSaleDate,"calendar");IconFactory.applyTableHeaderIcon(colSaleParty,"customer");IconFactory.applyTableHeaderIcon(colSaleAmount,"currency");IconFactory.applyTableHeaderIcon(colSaleStatus,"status");IconFactory.applyTableHeaderIcon(colPurchaseNo,"document");IconFactory.applyTableHeaderIcon(colPurchaseDate,"calendar");IconFactory.applyTableHeaderIcon(colPurchaseParty,"supplier");IconFactory.applyTableHeaderIcon(colPurchaseAmount,"currency");IconFactory.applyTableHeaderIcon(colPurchaseStatus,"status");}
    private record FilterData(List<String> parties,List<String> items,List<String> salespeople){}
    private record Point(String label,double value){}
    private record ReportData(double sales,double purchase,double profit,double receivables,double stock,long low,long customers,List<Point> trend,List<Point> customerPoints,List<Point> itemPoints,List<String[]> salesRows,List<String[]> purchaseRows,double salesPaid,double payables,double purchasesPaid,long items,long out){}

    private void configureMacPerformanceMode(){
        if(chartTrend!=null)chartTrend.setAnimated(false);if(chartCustomers!=null)chartCustomers.setAnimated(false);if(chartItems!=null)chartItems.setAnimated(false);if(chartComparison!=null)chartComparison.setAnimated(false);
        if(org.example.util.PlatformUiSupport.isMac()){
            for(javafx.scene.Node chart:new javafx.scene.Node[]{chartTrend,chartCustomers,chartItems,chartComparison})if(chart!=null){chart.setVisible(false);chart.setManaged(false);}
        }
    }
}
