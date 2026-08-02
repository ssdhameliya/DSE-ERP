package org.example.controller;

import org.example.util.OwnedAlert;


import org.example.util.IconFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.database.DatabaseManager;
import org.example.service.BusinessReportService;
import org.example.navigation.NavigationManager;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class ReportsController {
    @FXML private Label lblPurchase,lblSales,lblStock,lblLowStock,lblProfit,lblReceivables,lblCustomers,lblMargin;
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

    @FXML public void initialize(){
        configureExplicitTableHeaderIcons();
        configureIcons();
        configureStatusCells();
        dpFrom.setValue(LocalDate.now().withDayOfMonth(1));dpTo.setValue(LocalDate.now());
        cmbReportType.getItems().setAll("All Reports","Sales","Purchase","Inventory","Payments");cmbReportType.getSelectionModel().selectFirst();
        setCell(colSaleNo,0);setCell(colSaleDate,1);setCell(colSaleParty,2);setCell(colSaleAmount,3);setCell(colSaleStatus,4);
        setCell(colPurchaseNo,0);setCell(colPurchaseDate,1);setCell(colPurchaseParty,2);setCell(colPurchaseAmount,3);setCell(colPurchaseStatus,4);
        loadFilters();refresh();
    }
    private void setCell(TableColumn<String[],String> column,int index){column.setCellValueFactory(v->new SimpleStringProperty(v.getValue()[index]));}
    private void loadFilters(){
        cmbParty.getItems().setAll(list("SELECT name FROM party_master WHERE COALESCE(is_active,1)=1 ORDER BY name"));
        cmbItem.getItems().setAll(list("SELECT description FROM item_master WHERE COALESCE(is_active,1)=1 ORDER BY description"));
        cmbSalesPerson.getItems().setAll(list("SELECT DISTINCT salesperson FROM sales_header WHERE COALESCE(salesperson,'')<>'' ORDER BY salesperson"));
        prependAll(cmbParty,"All Customers / Suppliers");prependAll(cmbItem,"All Items");prependAll(cmbSalesPerson,"All Sales Persons");
    }
    private void prependAll(ComboBox<String> box,String value){box.getItems().addFirst(value);box.getSelectionModel().selectFirst();}
    private List<String> list(String sql){List<String> out=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())out.add(r.getString(1));}catch(SQLException ignored){}return out;}

    @FXML private void resetFilters(){cmbReportType.getSelectionModel().selectFirst();cmbParty.getSelectionModel().selectFirst();cmbItem.getSelectionModel().selectFirst();cmbSalesPerson.getSelectionModel().selectFirst();dpFrom.setValue(LocalDate.now().withDayOfMonth(1));dpTo.setValue(LocalDate.now());refresh();}
    @FXML private void refresh(){
        if(dpFrom.getValue()==null||dpTo.getValue()==null||dpFrom.getValue().isAfter(dpTo.getValue())){error("Choose a valid reporting date range.");return;}
        String from=dpFrom.getValue().toString(),to=dpTo.getValue().toString();
        double sales=number("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE invoice_date BETWEEN ? AND ?",from,to);
        double purchase=number("SELECT COALESCE(SUM(total_amount),0) FROM purchase_header WHERE invoice_date BETWEEN ? AND ?",from,to);
        double receivables=number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM sales_header WHERE invoice_date BETWEEN ? AND ?",from,to);
        double stock=number("SELECT COALESCE(SUM(opening_stock*purchase_price),0) FROM item_master");double profit=sales-purchase;
        lblSales.setText(money(sales));lblPurchase.setText(money(purchase));lblProfit.setText(money(profit));lblReceivables.setText(money(receivables));lblStock.setText(money(stock));
        lblLowStock.setText(((long)number("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)"))+" low-stock items");
        lblCustomers.setText(String.valueOf((long)number("SELECT COUNT(*) FROM party_master WHERE party_type='CUSTOMER' AND COALESCE(is_active,1)=1")));
        double margin=sales==0?0:(profit/sales)*100;lblMargin.setText(String.format("%.2f%%",margin));profitProgress.setProgress(Math.max(0,Math.min(1,margin/100)));
        loadCharts(from,to,sales,purchase);loadTables(from,to);loadSummaries(from,to,receivables,stock);
    }
    private void loadCharts(String from,String to,double sales,double purchase){
        XYChart.Series<String,Number> trend=new XYChart.Series<>();trend.setName("Sales");
        query("SELECT invoice_date,SUM(total_amount) FROM sales_header WHERE invoice_date BETWEEN ? AND ? GROUP BY invoice_date ORDER BY invoice_date",from,to,(r)->trend.getData().add(new XYChart.Data<>(r.getString(1),r.getDouble(2))));chartTrend.getData().setAll(trend);
        XYChart.Series<String,Number> customers=new XYChart.Series<>();customers.setName("Sales by customer");
        query("SELECT pm.name,SUM(sh.total_amount) amount FROM sales_header sh JOIN party_master pm ON pm.id=sh.customer_id WHERE sh.invoice_date BETWEEN ? AND ? GROUP BY pm.id,pm.name ORDER BY amount DESC LIMIT 5",from,to,(r)->{String customer=r.getString(1);customers.getData().add(new XYChart.Data<>(customer==null||customer.isBlank()?"Unknown Customer":customer,r.getDouble(2)));});chartCustomers.getData().setAll(customers);
        List<PieChart.Data> items=new ArrayList<>();query("SELECT COALESCE(im.description,sl.item_code),SUM(sl.line_total) amount FROM sales_line sl JOIN sales_header sh ON sh.id=sl.sales_id LEFT JOIN item_master im ON im.item_code=sl.item_code WHERE sh.invoice_date BETWEEN ? AND ? GROUP BY sl.item_code,im.description ORDER BY amount DESC LIMIT 5",from,to,(r)->items.add(new PieChart.Data(r.getString(1),r.getDouble(2))));chartItems.setData(FXCollections.observableArrayList(items));
        chartComparison.setData(FXCollections.observableArrayList(new PieChart.Data("Sales",sales),new PieChart.Data("Purchases",purchase)));
    }
    private void loadTables(String from,String to){
        List<String[]> sales=new ArrayList<>();query("SELECT sh.invoice_no,sh.invoice_date,pm.name,sh.total_amount,COALESCE(sh.payment_status,'PENDING') FROM sales_header sh LEFT JOIN party_master pm ON pm.id=sh.customer_id WHERE sh.invoice_date BETWEEN ? AND ? ORDER BY sh.invoice_date DESC,sh.id DESC LIMIT 8",from,to,r->sales.add(row(r)));tblSales.getItems().setAll(sales);
        List<String[]> purchases=new ArrayList<>();query("SELECT ph.invoice_no,ph.invoice_date,pm.name,ph.total_amount,COALESCE(ph.payment_status,'PENDING') FROM purchase_header ph LEFT JOIN party_master pm ON pm.id=ph.supplier_id WHERE ph.invoice_date BETWEEN ? AND ? ORDER BY ph.invoice_date DESC,ph.id DESC LIMIT 8",from,to,r->purchases.add(row(r)));tblPurchases.getItems().setAll(purchases);
    }
    private String[] row(ResultSet r)throws SQLException{return new String[]{r.getString(1),r.getString(2),Objects.toString(r.getString(3),"—"),money(r.getDouble(4)),r.getString(5)};}
    private void loadSummaries(String from,String to,double receivables,double stock){
        double salesPaid=number("SELECT COALESCE(SUM(paid_amount),0) FROM sales_header WHERE invoice_date BETWEEN ? AND ?",from,to);double payables=number("SELECT COALESCE(SUM(total_amount-COALESCE(paid_amount,0)),0) FROM purchase_header WHERE invoice_date BETWEEN ? AND ?",from,to);double purchasesPaid=number("SELECT COALESCE(SUM(paid_amount),0) FROM purchase_header WHERE invoice_date BETWEEN ? AND ?",from,to);
        paymentSummary.getItems().setAll("Total Receivables     "+money(receivables),"Received Amount      "+money(salesPaid),"Total Payables        "+money(payables),"Paid Amount           "+money(purchasesPaid));
        long items=(long)number("SELECT COUNT(*) FROM item_master"),low=(long)number("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=COALESCE(minimum_stock,0)"),out=(long)number("SELECT COUNT(*) FROM item_master WHERE COALESCE(opening_stock,0)<=0");stockSummary.getItems().setAll("Total Items           "+items,"Low Stock Items       "+low,"Out of Stock          "+out,"Stock Value           "+money(stock));
    }
    private interface RowConsumer{void accept(ResultSet r)throws SQLException;}
    private void query(String sql,String from,String to,RowConsumer consumer){try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,from);p.setString(2,to);try(ResultSet r=p.executeQuery()){while(r.next())consumer.accept(r);}}catch(SQLException e){error("Could not load report data: "+e.getMessage());}}
    @FXML private void exportPdf(){export("PDF Report","business-report.pdf","*.pdf",true);}@FXML private void exportExcel(){export("Excel Report","business-report.xlsx","*.xlsx",false);}
    @FXML private void viewAllSales(){navigate("/fxml/pages/SalesList.fxml");}
    @FXML private void viewAllPurchases(){navigate("/fxml/pages/PurchaseList.fxml");}
    private void navigate(String page){StackPane content=(StackPane)dpFrom.getScene().lookup("#contentPane");if(content!=null)new NavigationManager(content).loadPage(page);}
    private void export(String title,String name,String ext,boolean pdf){FileChooser f=new FileChooser();f.setTitle(title);f.setInitialFileName(name);f.getExtensionFilters().add(new FileChooser.ExtensionFilter(title,ext));File selected=f.showSaveDialog(dpFrom.getScene().getWindow());if(selected==null)return;Path path=selected.toPath();String suffix=pdf?".pdf":".xlsx";if(!path.toString().toLowerCase(Locale.ROOT).endsWith(suffix))path=Path.of(path+suffix);try{if(pdf)reportService.exportPdf(path,dpFrom.getValue(),dpTo.getValue());else reportService.exportExcel(path,dpFrom.getValue(),dpTo.getValue());new OwnedAlert(Alert.AlertType.INFORMATION,"Report created successfully:\n"+path).showAndWait();}catch(Exception e){error("Could not create report: "+e.getMessage());}}
    private String money(double n){return "₹ "+String.format("%,.2f",n);}private double number(String sql,String... values){try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setString(i+1,values[i]);try(ResultSet r=p.executeQuery()){return r.next()?r.getDouble(1):0;}}catch(SQLException e){return 0;}}
    private void error(String message){Alert a=new OwnedAlert(Alert.AlertType.ERROR,message);a.setHeaderText("Reporting error");a.showAndWait();}

    private void configureIcons() {
        btnRefresh.setGraphic(IconFactory.icon("refresh", 16));
        btnApply.setGraphic(IconFactory.icon("filter", 16));
        btnReset.setGraphic(IconFactory.icon("reset", 16));
        btnExport.setGraphic(IconFactory.icon("export", 16));
        btnViewSales.setGraphic(IconFactory.icon("view", 15));
        btnViewPurchases.setGraphic(IconFactory.icon("view", 15));
        miExcel.setGraphic(IconFactory.icon("excel", 15));
        miPdf.setGraphic(IconFactory.icon("pdf", 15));
    }

    private void configureStatusCells() {
        statusCell(colSaleStatus);
        statusCell(colPurchaseStatus);
    }

    private void statusCell(TableColumn<String[], String> column) {
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("report-status-paid", "report-status-pending", "report-status-other");
                if (empty || value == null) { setText(null); setGraphic(null); return; }
                setText(value);
                String v=value.toUpperCase(Locale.ROOT);
                getStyleClass().add(v.contains("PAID") || v.contains("COMPLETED") ? "report-status-paid" : v.contains("PENDING") ? "report-status-pending" : "report-status-other");
            }
        });
    }

    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colSaleNo, "document");
        IconFactory.applyTableHeaderIcon(colSaleDate, "calendar");
        IconFactory.applyTableHeaderIcon(colSaleParty, "customer");
        IconFactory.applyTableHeaderIcon(colSaleAmount, "currency");
        IconFactory.applyTableHeaderIcon(colSaleStatus, "status");
        IconFactory.applyTableHeaderIcon(colPurchaseNo, "document");
        IconFactory.applyTableHeaderIcon(colPurchaseDate, "calendar");
        IconFactory.applyTableHeaderIcon(colPurchaseParty, "supplier");
        IconFactory.applyTableHeaderIcon(colPurchaseAmount, "currency");
        IconFactory.applyTableHeaderIcon(colPurchaseStatus, "status");
    }
}
