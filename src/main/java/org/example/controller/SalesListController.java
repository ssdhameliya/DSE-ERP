package org.example.controller;

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
import org.example.database.DatabaseManager;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.navigation.NavigationManager;
import org.example.service.*;
import org.example.util.IconFactory;
import org.example.util.TableSelectionSupport;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

public class SalesListController {
    @FXML private Label lblTotalSales,lblInvoiceCount,lblTodaySales,lblTodayCount,lblPending,lblPendingCount,lblOverdue,lblOverdueCount,lblDueSoon,lblDueSoonCount,lblEmailRate;
    @FXML private StackPane salesTitleIcon,totalSalesIcon,todaySalesIcon,pendingSalesIcon,overdueSalesIcon,dueSoonIcon,emailRateIcon;
    @FXML private Button btnNewSale,btnResetFilters,btnRefreshSales,btnApplyFilters,btnExportExcel,btnExportPdf,btnPrintRegister;
    @FXML private Button btnTodayRange,btnYesterdayRange,btnSevenDaysRange,btnThirtyDaysRange,btnCustomRange;
    @FXML private TextField txtSearch,txtInvoice,txtAmountFrom,txtAmountTo;
    @FXML private TextArea txtDetailNotes;
    @FXML private ComboBox<String> cmbCustomer,cmbPaymentStatus,cmbPaymentDue,cmbMailStatus,cmbWhatsappStatus,cmbInvoiceType;
    @FXML private DatePicker dpFrom,dpTo;
    @FXML private ToggleButton btnAdvanced;
    @FXML private javafx.scene.layout.GridPane advancedFilters;
    @FXML private FlowPane activeFilterChips;
    @FXML private MenuButton savedViewsMenu;
    @FXML private TableView<Sales> tableSales;
    @FXML private TableColumn<Sales,Boolean> colSelect;
    @FXML private TableColumn<Sales,String> colInvoice,colDate,colCustomer,colMobile,colGstin,colDue,colStatus,colMail,colWhatsapp;
    @FXML private TableColumn<Sales,Double> colTotal,colPaid,colBalance;
    @FXML private TableColumn<Sales,Void> colAction;
    @FXML private ComboBox<Integer> cmbPageSize;
    @FXML private Label lblPageInfo,lblPageNumber,lblFooterTotal,lblFooterPaid,lblFooterBalance;
    @FXML private PieChart dueChart;
    @FXML private BarChart<Number,String> customerChart;
    @FXML private LineChart<String,Number> salesChart;
    @FXML private SplitPane mainSplit;
    @FXML private javafx.scene.layout.VBox detailDrawer;
    @FXML private Label lblDetailInvoice,lblDetailDate,lblDetailStatus,lblDetailCustomer,lblDetailContact,lblDetailAmount,lblDetailPaid,lblDetailBalance,lblDetailDue;

    private final SalesService service=new SalesService();
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(new Locale("en","IN"));
    private final DateTimeFormatter dateFormat=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private List<Sales> allSales=new ArrayList<>(),filteredSales=new ArrayList<>();
    private int currentPage=0;
    private Sales selected;

    @FXML public void initialize(){
        tableSales.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableSelectionSupport.install(tableSales,colSelect);
        configureColumns();configureFilters();configureActions();configurePaging();configureVisualIcons();
        configureExplicitTableHeaderIcons();
        simplifyFilters();
        detailDrawer.setVisible(false);detailDrawer.setManaged(false);mainSplit.setDividerPositions(1.0);
        tableSales.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null)showDetails(b);});
        txtSearch.textProperty().addListener((o,a,b)->applyFilters());
        refresh();
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
        setButtonIcon(btnExportExcel,"download");
        setButtonIcon(btnExportPdf,"download");
        setButtonIcon(btnPrintRegister,"print");
        setButtonIcon(btnTodayRange,"calendar");
        setButtonIcon(btnYesterdayRange,"restore");
        setButtonIcon(btnSevenDaysRange,"calendar");
        setButtonIcon(btnThirtyDaysRange,"calendar");
        setButtonIcon(btnCustomRange,"calendar");
    }

    private void setIcon(StackPane holder,String semantic,int size){
        if(holder==null)return;
        holder.getChildren().setAll(IconFactory.icon(semantic,size));
    }

    private void setButtonIcon(ButtonBase button,String semantic){
        if(button==null)return;
        button.getProperties().put("erp.icon.semantic", semantic);
        button.setGraphic(IconFactory.icon(semantic,15));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(8);
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
        colDate.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().getInvoiceDate().format(dateFormat)));
        colCustomer.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().getCustomer().getName()));
        colMobile.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(safe(v.getValue().getCustomer().getPhone())));
        colGstin.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(safe(v.getValue().getCustomer().getGstin())));
        colTotal.setCellValueFactory(v->new javafx.beans.property.SimpleDoubleProperty(v.getValue().getTotalAmount()).asObject());
        colPaid.setCellValueFactory(v->new javafx.beans.property.SimpleDoubleProperty(v.getValue().getPaidAmount()).asObject());
        colBalance.setCellValueFactory(v->new javafx.beans.property.SimpleDoubleProperty(v.getValue().getBalanceAmount()).asObject());
        colDue.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(dueLabel(v.getValue())));
        colStatus.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(documentStatus(v.getValue())));
        colMail.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().isEmailSent()?"Sent":"Not Sent"));
        colWhatsapp.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().isWhatsappSent()?"Sent":"Not Sent"));
        colTotal.setCellFactory(x->moneyCell());
        colPaid.setCellFactory(x->paidMoneyCell());
        colBalance.setCellFactory(x->moneyCell());
        colStatus.setCellFactory(x->statusCell("document"));
        colMail.setCellFactory(x->statusCell("email"));
        colWhatsapp.setCellFactory(x->statusCell("whatsapp"));
        colDue.setCellFactory(x->dueCell());
        colDue.setGraphic(IconFactory.icon("reminder"));colStatus.setGraphic(IconFactory.icon("status"));colMail.setGraphic(IconFactory.icon("email"));colWhatsapp.setGraphic(IconFactory.icon("whatsapp"));
        tableSales.setPlaceholder(new Label("No sales invoices match the selected filters"));
    }

    private void configureExplicitTableHeaderIcons(){
        setHeaderIcon(colSelect,"quantity");
        setHeaderIcon(colInvoice,"document");
        setHeaderIcon(colDate,"calendar");
        setHeaderIcon(colCustomer,"customer");
        setHeaderIcon(colMobile,"phone");
        setHeaderIcon(colGstin,"tax");
        setHeaderIcon(colTotal,"currency");
        setHeaderIcon(colPaid,"complete");
        setHeaderIcon(colBalance,"payment");
        setHeaderIcon(colDue,"reminder");
        setHeaderIcon(colStatus,"status");
        setHeaderIcon(colMail,"email");
        setHeaderIcon(colWhatsapp,"whatsapp");
        setHeaderIcon(colAction,"actions");
    }

    private void setHeaderIcon(TableColumn<?,?> column,String semantic){
        if(column==null)return;
        column.setGraphic(IconFactory.compactIcon(semantic,14));
        column.getProperties().put("erp-header-preserve",true);
    }

    private TableCell<Sales,Double> moneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);}};}
    private TableCell<Sales,Double> paidMoneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:money(v));setAlignment(Pos.CENTER_RIGHT);getStyleClass().removeAll("sales-paid-positive","sales-paid-zero");if(!e&&v!=null)getStyleClass().add(v>.009?"sales-paid-positive":"sales-paid-zero");}};}
    private TableCell<Sales,String> statusCell(String semantic){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("pill-success","pill-warning","pill-danger","pill-neutral");if(!e&&v!=null){boolean good=v.equalsIgnoreCase("COMPLETED")||v.equalsIgnoreCase("PAID")||v.equalsIgnoreCase("SENT");boolean pending=v.equalsIgnoreCase("IN PROGRESS")||v.equalsIgnoreCase("PARTIAL")||v.equalsIgnoreCase("PENDING");getStyleClass().add(good?"pill-success":pending?"pill-warning":"pill-danger");String icon = good ? semantic : (pending ? ("status".equals(semantic)?"reminder":semantic) : "error");setGraphic(IconFactory.statusIcon(icon,good?"#16a34a":pending?"#2563eb":"#dc2626"));}}};}
    private TableCell<Sales,String> dueCell(){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("due-overdue","due-soon","due-paid");if(!e&&v!=null){boolean paid=v.equals("Paid"),overdue=v.startsWith("Overdue");getStyleClass().add(overdue?"due-overdue":paid?"due-paid":"due-soon");setGraphic(IconFactory.statusIcon(overdue?"error":paid?"save":"reminder",overdue?"#dc2626":paid?"#16a34a":"#2563eb"));}}};}
    private String documentStatus(Sales sale){if(sale.getBalanceAmount()<=.01)return "COMPLETED";if(sale.getPaidAmount()>0)return "IN PROGRESS";return "PENDING";}

    private void configureFilters(){
        cmbPaymentStatus.getItems().setAll("All","PENDING","PARTIAL","PAID","OVERDUE");cmbPaymentStatus.setValue("All");
        cmbPaymentDue.getItems().setAll("All","Overdue","Due Today","Next 7 Days","Next 30 Days");cmbPaymentDue.setValue("All");
        cmbMailStatus.getItems().setAll("All","Sent","Not Sent");cmbMailStatus.setValue("All");
        cmbWhatsappStatus.getItems().setAll("All","Sent","Not Sent");cmbWhatsappStatus.setValue("All");
        cmbInvoiceType.getItems().setAll("All","TAX INVOICE","PROFORMA","CASH MEMO");cmbInvoiceType.setValue("All");
        dpFrom.setValue(null);
        dpTo.setValue(null);
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
        colAction.setMinWidth(118);colAction.setPrefWidth(118);colAction.setMaxWidth(118);
        colAction.setCellFactory(c->new TableCell<>(){final MenuButton menu=new MenuButton("Actions");{
            menu.getProperties().put("erp.icon.semantic", "actions");
            menu.setGraphic(IconFactory.compactIcon("actions",15));
            add("View Details","view",e->openInvoiceDetails(row()));add("Edit Sale","edit",e->edit(row()));add("Duplicate Sale","sale",e->duplicate(row()));add("Print / Download PDF","print",e->openPdf(row()));add("Send Email","email",e->sendEmail(row()));add("Send WhatsApp","whatsapp",e->sendWhatsapp(row()));add("Record Payment","payment",e->openPayment(row()));add("View Payments","payment",e->openPaymentHistory(row()));add("Create Sales Return","return",e->createReturn(row()));add("Attach Document","attachment",e->attach(row()));add("Notes / Remarks","document",e->notes(row()));add("Send Reminder","reminder",e->createReminder(row()));add("Cancel Sale","cancel",e->cancelSale(row()));MenuItem del=add("Delete Sale","delete",e->delete(row()));del.getStyleClass().add("danger-menu-item");menu.getStyleClass().add("row-actions");}
            private Sales row(){Sales value=getTableRow()==null?null:getTableRow().getItem();if(value==null)throw new IllegalStateException("This sales row is no longer available. Refresh the register and try again.");return value;}
            private MenuItem add(String t,String icon,javafx.event.EventHandler<ActionEvent> h){MenuItem i=new MenuItem(t);i.setGraphic(IconFactory.icon(icon));i.setOnAction(event->{try{h.handle(event);}catch(Throwable failure){error(failure);}});menu.getItems().add(i);return i;}
            protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:menu);setAlignment(Pos.CENTER);}});
    }

    @FXML public void refresh(){
        allSales=new ArrayList<>(service.getAll());
        cmbCustomer.getItems().setAll("All customers");cmbCustomer.getItems().addAll(allSales.stream().map(s->s.getCustomer().getName()).filter(Objects::nonNull).distinct().sorted().toList());if(cmbCustomer.getValue()==null)cmbCustomer.setValue("All customers");
        loadSavedViews();updateMetrics();applyFilters();updateCharts();
    }

    @FXML public void applyFilters(){
        String global=lower(txtSearch.getText()),invoice=lower(txtInvoice.getText()),customer=cmbCustomer.getValue();double min=parseAmount(txtAmountFrom.getText(),Double.NEGATIVE_INFINITY),max=parseAmount(txtAmountTo.getText(),Double.POSITIVE_INFINITY);
        Predicate<Sales> p=s->{String hay=lower(s.getInvoiceNo()+" "+s.getCustomer().getName()+" "+safe(s.getCustomer().getPhone())+" "+safe(s.getCustomer().getGstin()));if(!global.isBlank()&&!hay.contains(global))return false;if(!invoice.isBlank()&&!lower(s.getInvoiceNo()).contains(invoice))return false;if(customer!=null&&!customer.startsWith("All")&&!customer.equals(s.getCustomer().getName()))return false;if(dpFrom.getValue()!=null&&s.getInvoiceDate().isBefore(dpFrom.getValue()))return false;if(dpTo.getValue()!=null&&s.getInvoiceDate().isAfter(dpTo.getValue()))return false;if(s.getTotalAmount()<min||s.getTotalAmount()>max)return false;if(!matches(cmbPaymentStatus,s.getPaymentStatus()))return false;if(!matches(cmbInvoiceType,s.getInvoiceType()))return false;if(!matches(cmbMailStatus,s.isEmailSent()?"Sent":"Not Sent"))return false;if(!matches(cmbWhatsappStatus,s.isWhatsappSent()?"Sent":"Not Sent"))return false;return matchesDue(s);};
        filteredSales=allSales.stream().filter(p).toList();currentPage=0;renderPage();renderChips();updateFooter();
    }

    private boolean matches(ComboBox<String> box,String value){String f=box.getValue();return f==null||f.equals("All")||f.equalsIgnoreCase(value);}
    private boolean matchesDue(Sales s){String f=cmbPaymentDue.getValue();if(f==null||f.equals("All"))return true;if(s.getDueDate()==null||s.getBalanceAmount()<=0)return false;long days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),s.getDueDate());return switch(f){case "Overdue"->days<0;case "Due Today"->days==0;case "Next 7 Days"->days>=0&&days<=7;case "Next 30 Days"->days>=0&&days<=30;default->true;};}

    private void renderPage(){int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue(),pages=Math.max(1,(int)Math.ceil(filteredSales.size()/(double)size));currentPage=Math.min(currentPage,pages-1);int from=Math.min(currentPage*size,filteredSales.size()),to=Math.min(from+size,filteredSales.size());tableSales.setItems(FXCollections.observableArrayList(filteredSales.subList(from,to)));lblPageNumber.setText((currentPage+1)+" / "+pages);lblPageInfo.setText(filteredSales.isEmpty()?"No entries":"Showing "+(from+1)+" to "+to+" of "+filteredSales.size()+" entries");}
    @FXML private void firstPage(){currentPage=0;renderPage();}@FXML private void previousPage(){if(currentPage>0)currentPage--;renderPage();}@FXML private void nextPage(){int pages=(int)Math.ceil(filteredSales.size()/(double)cmbPageSize.getValue());if(currentPage<pages-1)currentPage++;renderPage();}@FXML private void lastPage(){currentPage=Math.max(0,(int)Math.ceil(filteredSales.size()/(double)cmbPageSize.getValue())-1);renderPage();}

    private void updateMetrics(){double total=sum(allSales,Sales::getTotalAmount),today=sum(allSales.stream().filter(s->s.getInvoiceDate().equals(LocalDate.now())).toList(),Sales::getTotalAmount),pending=sum(allSales,Sales::getBalanceAmount);List<Sales> overdue=allSales.stream().filter(s->s.getBalanceAmount()>0&&s.getDueDate()!=null&&s.getDueDate().isBefore(LocalDate.now())).toList(),soon=allSales.stream().filter(s->s.getBalanceAmount()>0&&s.getDueDate()!=null&&!s.getDueDate().isBefore(LocalDate.now())&&!s.getDueDate().isAfter(LocalDate.now().plusDays(7))).toList();lblTotalSales.setText(money(total));lblInvoiceCount.setText(allSales.size()+" invoices");lblTodaySales.setText(money(today));lblTodayCount.setText(allSales.stream().filter(s->s.getInvoiceDate().equals(LocalDate.now())).count()+" invoices");lblPending.setText(money(pending));lblPendingCount.setText(allSales.stream().filter(s->s.getBalanceAmount()>0).count()+" invoices");lblOverdue.setText(money(sum(overdue,Sales::getBalanceAmount)));lblOverdueCount.setText(overdue.size()+" invoices");lblDueSoon.setText(money(sum(soon,Sales::getBalanceAmount)));lblDueSoonCount.setText(soon.size()+" invoices");long sent=allSales.stream().filter(Sales::isEmailSent).count();lblEmailRate.setText(allSales.isEmpty()?"0%":Math.round(sent*100.0/allSales.size())+"%");}
    private double sum(List<Sales> list,java.util.function.ToDoubleFunction<Sales> f){return list.stream().mapToDouble(f).sum();}
    private void updateFooter(){lblFooterTotal.setText(money(sum(filteredSales,Sales::getTotalAmount)));lblFooterPaid.setText(money(sum(filteredSales,Sales::getPaidAmount)));lblFooterBalance.setText(money(sum(filteredSales,Sales::getBalanceAmount)));}

    private void updateCharts(){
        if(dueChart==null||customerChart==null||salesChart==null)return;
        Map<String,Double> buckets=new LinkedHashMap<>();buckets.put("Due Today",0d);buckets.put("1-7 Days",0d);buckets.put("8-30 Days",0d);buckets.put("Over 30 Days",0d);for(Sales s:allSales)if(s.getBalanceAmount()>0&&s.getDueDate()!=null){long d=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),s.getDueDate());String k=d<=0?"Due Today":d<=7?"1-7 Days":d<=30?"8-30 Days":"Over 30 Days";buckets.merge(k,s.getBalanceAmount(),Double::sum);}dueChart.getData().setAll(buckets.entrySet().stream().filter(e->e.getValue()>0).map(e->new PieChart.Data(e.getKey(),e.getValue())).toList());
        Map<String,Double> customers=new HashMap<>();for(Sales s:allSales){String customerName=s.getCustomer()==null?null:s.getCustomer().getName();if(customerName==null||customerName.isBlank())customerName="Unknown Customer";customers.merge(customerName,s.getTotalAmount(),Double::sum);}XYChart.Series<Number,String> cs=new XYChart.Series<>();customers.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(5).forEach(e->cs.getData().add(new XYChart.Data<>(e.getValue(),e.getKey())));customerChart.getData().setAll(cs);
        Map<String,Double> months=new TreeMap<>();for(Sales s:allSales)months.merge(s.getInvoiceDate().toString().substring(0,7),s.getTotalAmount(),Double::sum);XYChart.Series<String,Number> ss=new XYChart.Series<>();months.entrySet().stream().skip(Math.max(0,months.size()-7)).forEach(e->ss.getData().add(new XYChart.Data<>(e.getKey(),e.getValue())));salesChart.getData().setAll(ss);
    }

    private void showDetails(Sales sale){selected=sale;detailDrawer.setManaged(true);detailDrawer.setVisible(true);mainSplit.setDividerPositions(.78);lblDetailInvoice.setText(sale.getInvoiceNo());lblDetailDate.setText(sale.getInvoiceDate().format(dateFormat));lblDetailStatus.setText(sale.getPaymentStatus());lblDetailCustomer.setText(sale.getCustomer().getName());lblDetailContact.setText(safe(sale.getCustomer().getPhone())+"\n"+safe(sale.getCustomer().getEmail())+"\n"+safe(sale.getCustomer().getGstin()));lblDetailAmount.setText(money(sale.getTotalAmount()));lblDetailPaid.setText(money(sale.getPaidAmount()));lblDetailBalance.setText(money(sale.getBalanceAmount()));lblDetailDue.setText(sale.getDueDate()==null?"Not set":sale.getDueDate().format(dateFormat)+" • "+dueLabel(sale));txtDetailNotes.setText(sale.getNotes());}
    @FXML private void closeDetails(){selected=null;detailDrawer.setVisible(false);detailDrawer.setManaged(false);mainSplit.setDividerPositions(1);tableSales.getSelectionModel().clearSelection();}
    private Sales requireSelected(){if(selected==null){warning("Select an invoice first.");return null;}return selected;}
    @FXML private void emailSelected(){Sales s=requireSelected();if(s!=null)sendEmail(s);}@FXML private void whatsappSelected(){Sales s=requireSelected();if(s!=null)sendWhatsapp(s);}@FXML private void recordSelectedPayment(){Sales s=requireSelected();if(s!=null)openPayment(s);}@FXML private void remindSelected(){Sales s=requireSelected();if(s!=null)createReminder(s);}
    private void openInvoiceDetails(Sales s){SalesScreenContext.select(s.getInvoiceNo());NavigationManager.getInstance().loadPage("/fxml/pages/SalesInvoiceDetails.fxml");}
    private void openPayment(Sales s){SalesScreenContext.select(s.getInvoiceNo());NavigationManager.getInstance().loadPage("/fxml/pages/RecordPayment.fxml");}
    private void openPaymentHistory(Sales s){SalesScreenContext.select(s.getInvoiceNo());NavigationManager.getInstance().loadPage("/fxml/pages/PaymentHistory.fxml");}
    @FXML private void saveSelectedNotes(){Sales s=requireSelected();if(s==null)return;try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE sales_header SET notes=? WHERE id=?")){p.setString(1,txtDetailNotes.getText());p.setInt(2,s.getId());p.executeUpdate();log("SALE",s.getId(),"NOTES_UPDATED","Invoice notes updated");refresh();info("Notes saved.");}catch(Exception e){error(e);}}

    @FXML private void showToday(){applyDateRange(LocalDate.now(),LocalDate.now());}
    @FXML private void showYesterday(){LocalDate day=LocalDate.now().minusDays(1);applyDateRange(day,day);}
    @FXML private void showSevenDays(){applyDateRange(LocalDate.now().minusDays(6),LocalDate.now());}
    @FXML private void showThirtyDays(){applyDateRange(LocalDate.now().minusDays(29),LocalDate.now());}
    @FXML private void focusCustomRange(){dpFrom.requestFocus();}
    private void applyDateRange(LocalDate from,LocalDate to){dpFrom.setValue(from);dpTo.setValue(to);applyFilters();}

    @FXML private void toggleAdvanced(){advancedFilters.setManaged(btnAdvanced.isSelected());advancedFilters.setVisible(btnAdvanced.isSelected());}
    @FXML private void resetFilters(){txtSearch.clear();txtInvoice.clear();txtAmountFrom.clear();txtAmountTo.clear();dpFrom.setValue(null);dpTo.setValue(null);cmbCustomer.setValue("All customers");cmbPaymentStatus.setValue("All");cmbPaymentDue.setValue("All");cmbMailStatus.setValue("All");cmbWhatsappStatus.setValue("All");cmbInvoiceType.setValue("All");applyFilters();}
    private void renderChips(){activeFilterChips.getChildren().clear();addChip("From",dpFrom.getValue());addChip("To",dpTo.getValue());addChip("Payment",nonAll(cmbPaymentStatus));addChip("Due",nonAll(cmbPaymentDue));addChip("Email",nonAll(cmbMailStatus));addChip("WhatsApp",nonAll(cmbWhatsappStatus));}
    private Object nonAll(ComboBox<String>b){return b.getValue()==null||b.getValue().equals("All")?null:b.getValue();}private void addChip(String name,Object value){if(value==null)return;Label chip=new Label(name+": "+value);chip.getStyleClass().add("filter-chip");activeFilterChips.getChildren().add(chip);}

    @FXML private void saveCurrentView(){TextInputDialog d=new TextInputDialog();d.setTitle("Save Filter View");d.setHeaderText("Save the current sales filters");d.setContentText("View name:");d.showAndWait().map(String::trim).filter(x->!x.isBlank()).ifPresent(name->{String data=String.join("|",safe(txtInvoice.getText()),safe(cmbCustomer.getValue()),str(dpFrom.getValue()),str(dpTo.getValue()),safe(cmbPaymentStatus.getValue()),safe(cmbPaymentDue.getValue()),safe(cmbMailStatus.getValue()),safe(cmbWhatsappStatus.getValue()),safe(cmbInvoiceType.getValue()),safe(txtAmountFrom.getText()),safe(txtAmountTo.getText()));try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO saved_filter(user_id,screen_key,view_name,filter_json) VALUES(?,?,?,?) ON CONFLICT(user_id,screen_key,view_name) DO UPDATE SET filter_json=excluded.filter_json")){if(SessionService.current()==null)p.setNull(1,Types.INTEGER);else p.setInt(1,SessionService.current().getId());p.setString(2,"SALES_REGISTER");p.setString(3,name);p.setString(4,data);p.executeUpdate();loadSavedViews();info("Saved view created.");}catch(Exception e){error(e);}});}
    private void loadSavedViews(){savedViewsMenu.getItems().clear();try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("SELECT view_name,filter_json FROM saved_filter WHERE screen_key='SALES_REGISTER' AND (user_id=? OR user_id IS NULL) ORDER BY view_name")){if(SessionService.current()==null)p.setNull(1,Types.INTEGER);else p.setInt(1,SessionService.current().getId());try(ResultSet r=p.executeQuery()){while(r.next()){String name=r.getString(1),data=r.getString(2);MenuItem i=new MenuItem(name);i.setOnAction(e->applySaved(data));savedViewsMenu.getItems().add(i);}}}catch(Exception ignored){}if(savedViewsMenu.getItems().isEmpty())savedViewsMenu.getItems().add(new MenuItem("No saved views"));}
    private void applySaved(String data){String[]x=data.split("\\|",-1);if(x.length<11)return;txtInvoice.setText(x[0]);cmbCustomer.setValue(x[1]);dpFrom.setValue(date(x[2]));dpTo.setValue(date(x[3]));cmbPaymentStatus.setValue(x[4]);cmbPaymentDue.setValue(x[5]);cmbMailStatus.setValue(x[6]);cmbWhatsappStatus.setValue(x[7]);cmbInvoiceType.setValue(x[8]);txtAmountFrom.setText(x[9]);txtAmountTo.setText(x[10]);applyFilters();}

    @FXML private void newSale(){StackPane pane=(StackPane)tableSales.getScene().lookup("#contentPane");if(pane!=null)new NavigationManager(pane).loadPage("/fxml/pages/Sale.fxml");}
    private void edit(Sales sale){try{FXMLLoader loader=new FXMLLoader(getClass().getResource("/fxml/pages/Sale.fxml"));Parent root=loader.load();((SalesController)loader.getController()).loadSale(service.getByInvoice(sale.getInvoiceNo()));StackPane pane=(StackPane)tableSales.getScene().lookup("#contentPane");pane.getChildren().setAll(root);}catch(Exception e){error(e);}}
    private void openPdf(Sales sale){try{Path p=InvoicePdfService.sales(service.getByInvoice(sale.getInvoiceNo()));java.awt.Desktop.getDesktop().open(p.toFile());log("SALE",sale.getId(),"PDF_OPENED",sale.getInvoiceNo());}catch(Exception e){error(e);}}
    private void sendEmail(Sales sale){String stage="loading the sales invoice";try{Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null)throw new IllegalStateException("Sales invoice "+sale.getInvoiceNo()+" was not found. Refresh the register and try again.");if(full.getCustomer()==null)throw new IllegalStateException("No customer is linked to "+full.getInvoiceNo()+".");String recipient=safe(full.getCustomer().getEmail()).trim();if(recipient.isBlank())throw new IllegalStateException("Customer email is missing for "+full.getCustomer().getName()+". Update Customer Master and try again.");stage="generating the sales invoice PDF";Path pdf=InvoicePdfService.sales(full);stage="sending the email";EmailService.send(recipient,"Sales Invoice "+full.getInvoiceNo(),"Dear "+safe(full.getCustomer().getName())+",\n\nPlease find your sales invoice attached.\n\nRegards,\n"+org.example.config.ConfigManager.get("company.name","DSE ERP"),pdf);service.markEmailSent(full.getId());communication("SALE",full.getId(),"EMAIL",recipient,"SENT",null);refresh();info("Invoice emailed successfully to "+recipient+".");}catch(Exception failure){String recipient=sale.getCustomer()==null?"":safe(sale.getCustomer().getEmail());communication("SALE",sale.getId(),"EMAIL",recipient,"FAILED",stage+": "+rootMessage(failure));error(new IllegalStateException("Email failed while "+stage+".\n\n"+rootMessage(failure),failure));}}
    private void sendWhatsapp(Sales sale){try{Sales full=service.getByInvoice(sale.getInvoiceNo());String phone=digits(full.getCustomer().getPhone());if(phone.length()==10)phone="91"+phone;if(phone.isBlank()){warning("Customer mobile number is not available. Update it in Customer Master.");return;}String missing=PaymentMessageService.missingPaymentConfiguration();if(missing!=null)warning(missing+" The invoice can still be shared without a payment link.");Path pdf=InvoicePdfService.sales(full);WhatsappService.openWhatsappWithMessage(phone,PaymentMessageService.salesMessage(full),pdf,PaymentMessageService.configuredQrPath());info("WhatsApp is ready. The invoice and configured UPI QR are on the clipboard for attachment.");try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE sales_header SET whatsapp_sent=1 WHERE id=?")){p.setInt(1,full.getId());p.executeUpdate();}communication("SALE",full.getId(),"WHATSAPP",phone,"SENT",null);refresh();}catch(Exception e){error(e);}}
    private void recordPayment(Sales sale){if(sale.getBalanceAmount()<=0){info("This invoice is already fully paid.");return;}Dialog<ButtonType>d=new Dialog<>();d.setTitle("Record Payment");d.setHeaderText(sale.getInvoiceNo()+" • Balance "+money(sale.getBalanceAmount()));TextField amount=new TextField(String.format(Locale.ROOT,"%.2f",sale.getBalanceAmount())),ref=new TextField(),notes=new TextField();ComboBox<String>mode=new ComboBox<>(FXCollections.observableArrayList("Cash","Bank","UPI","Cheque","Card","Other"));mode.setValue("Bank");DatePicker date=new DatePicker(LocalDate.now());javafx.scene.layout.GridPane g=new javafx.scene.layout.GridPane();g.setHgap(10);g.setVgap(10);g.addRow(0,new Label("Date"),date);g.addRow(1,new Label("Amount"),amount);g.addRow(2,new Label("Mode"),mode);g.addRow(3,new Label("Reference"),ref);g.addRow(4,new Label("Notes"),notes);d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Record",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{double paid=Double.parseDouble(amount.getText());if(paid<=0||paid>sale.getBalanceAmount()+.01)throw new IllegalArgumentException("Payment must be between 0 and "+sale.getBalanceAmount());try(Connection c=DatabaseManager.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("INSERT INTO payment_record(document_type,document_id,payment_date,amount,payment_mode,reference_no,notes,created_by) VALUES('SALE',?,?,?,?,?,?,?)");PreparedStatement u=c.prepareStatement("UPDATE sales_header SET paid_amount=COALESCE(paid_amount,0)+?,payment_status=CASE WHEN COALESCE(paid_amount,0)+?>=total_amount THEN 'PAID' ELSE 'PARTIAL' END WHERE id=?")){p.setInt(1,sale.getId());p.setString(2,date.getValue().toString());p.setDouble(3,paid);p.setString(4,mode.getValue());p.setString(5,ref.getText());p.setString(6,notes.getText());p.setString(7,user());p.executeUpdate();u.setDouble(1,paid);u.setDouble(2,paid);u.setInt(3,sale.getId());u.executeUpdate();c.commit();}catch(Exception e){c.rollback();throw e;}}log("SALE",sale.getId(),"PAYMENT_RECORDED",money(paid));refresh();info("Payment recorded.");}catch(Exception e){error(e);}});}
    private void createReminder(Sales sale){DatePicker due=new DatePicker(sale.getDueDate()==null?LocalDate.now().plusDays(1):sale.getDueDate());TextInputDialog dialog=new TextInputDialog("Payment reminder for "+sale.getInvoiceNo());dialog.setTitle("Create Reminder");dialog.setHeaderText("Reminder date: "+due.getValue());dialog.setContentText("Reminder text:");dialog.showAndWait().ifPresent(text->{try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO reminder_register(title,reference_no,due_date,priority,notes,status) VALUES(?,?,?,?,?,'OPEN')")){p.setString(1,text);p.setString(2,sale.getInvoiceNo());p.setString(3,due.getValue().toString());p.setString(4,sale.getDueDate()!=null&&sale.getDueDate().isBefore(LocalDate.now())?"URGENT":"NORMAL");p.setString(5,"Customer: "+sale.getCustomer().getName()+"; Balance: "+money(sale.getBalanceAmount()));p.executeUpdate();NotificationService.add("Reminder created for "+sale.getInvoiceNo());info("Reminder created.");}catch(Exception e){error(e);}});}
    private void attach(Sales sale){FileChooser chooser=new FileChooser();File file=chooser.showOpenDialog(tableSales.getScene().getWindow());if(file==null)return;try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE sales_header SET attachment_path=? WHERE id=?")){p.setString(1,file.getAbsolutePath());p.setInt(2,sale.getId());p.executeUpdate();log("SALE",sale.getId(),"DOCUMENT_ATTACHED",file.getName());info("Document attached to "+sale.getInvoiceNo()+".");}catch(Exception e){error(e);}}
    private void notes(Sales sale){TextInputDialog dialog=new TextInputDialog(safe(sale.getNotes()));dialog.setTitle("Sales Notes");dialog.setHeaderText("Notes / Remarks • "+sale.getInvoiceNo());dialog.showAndWait().ifPresent(value->{try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE sales_header SET notes=? WHERE id=?")){p.setString(1,value);p.setInt(2,sale.getId());p.executeUpdate();log("SALE",sale.getId(),"NOTES_UPDATED","Invoice notes updated");refresh();}catch(Exception e){error(e);}});}
    private void cancelSale(Sales sale){if(!confirm("Cancel "+sale.getInvoiceNo()+" and restore its stock?"))return;try(Connection c=DatabaseManager.getConnection()){c.setAutoCommit(false);try(PreparedStatement update=c.prepareStatement("UPDATE sales_header SET document_status='CANCELLED' WHERE id=? AND document_status<>'CANCELLED'")){update.setInt(1,sale.getId());if(update.executeUpdate()>0){try(PreparedStatement lines=c.prepareStatement("SELECT item_code,quantity FROM sales_line WHERE sales_id=?");PreparedStatement stock=c.prepareStatement("UPDATE item_master SET opening_stock=COALESCE(opening_stock,0)+? WHERE item_code=?")){lines.setInt(1,sale.getId());try(ResultSet result=lines.executeQuery()){while(result.next()){stock.setDouble(1,result.getDouble("quantity"));stock.setString(2,result.getString("item_code"));stock.addBatch();}}stock.executeBatch();}}c.commit();log("SALE",sale.getId(),"CANCELLED",sale.getInvoiceNo());refresh();}catch(Exception e){c.rollback();throw e;}}catch(Exception e){error(e);}}
    private void createReturn(Sales sale){Sales full=service.getByInvoice(sale.getInvoiceNo());if(full==null){warning("Sales invoice not found. Refresh and try again.");return;}List<ReturnEditorService.InvoiceItem> items=full.getLines().stream().map(line->new ReturnEditorService.InvoiceItem(line.getItemCode(),line.getItemDescription(),line.getQuantity(),line.getRate(),line.getGstPercent())).toList();ReturnEditorService.show(tableSales.getScene().getWindow(),ReturnEditorService.Type.SALES,sale.getInvoiceNo(),sale.getCustomer().getName(),sale.getCustomer().getId(),items).ifPresent(no->{refresh();info("Sales return created: "+no);});}
    private void duplicate(Sales sale){if(!confirm("Duplicate "+sale.getInvoiceNo()+" as a new sales invoice?"))return;try(Connection c=DatabaseManager.getConnection()){c.setAutoCommit(false);try{String no=nextInvoice(c);int id;try(PreparedStatement p=c.prepareStatement("INSERT INTO sales_header(invoice_no,invoice_date,customer_id,subtotal,gst_amount,total_amount,remarks,created_at,email_sent,due_date,paid_amount,payment_status,whatsapp_sent,invoice_type,salesperson,source,notes) SELECT ?,date('now'),customer_id,subtotal,gst_amount,total_amount,?,datetime('now'),0,date('now','+30 day'),0,'PENDING',0,invoice_type,salesperson,source,notes FROM sales_header WHERE id=?",Statement.RETURN_GENERATED_KEYS)){p.setString(1,no);p.setString(2,"Duplicated from "+sale.getInvoiceNo());p.setInt(3,sale.getId());p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();id=k.getInt(1);}}try(PreparedStatement q=c.prepareStatement("SELECT * FROM sales_line WHERE sales_id=?");PreparedStatement line=c.prepareStatement("INSERT INTO sales_line(sales_id,item_code,quantity,rate,discount_percent,discount_amount,gst_percent,line_total) VALUES(?,?,?,?,?,?,?,?)");PreparedStatement stock=c.prepareStatement("UPDATE item_master SET opening_stock=COALESCE(opening_stock,0)-? WHERE item_code=?")){q.setInt(1,sale.getId());try(ResultSet r=q.executeQuery()){while(r.next()){line.setInt(1,id);line.setString(2,r.getString("item_code"));line.setDouble(3,r.getDouble("quantity"));line.setDouble(4,r.getDouble("rate"));line.setDouble(5,r.getDouble("discount_percent"));line.setDouble(6,r.getDouble("discount_amount"));line.setDouble(7,r.getDouble("gst_percent"));line.setDouble(8,r.getDouble("line_total"));line.addBatch();stock.setDouble(1,r.getDouble("quantity"));stock.setString(2,r.getString("item_code"));stock.addBatch();}}line.executeBatch();stock.executeBatch();}c.commit();log("SALE",id,"DUPLICATED","From "+sale.getInvoiceNo());refresh();info("Created "+no);}catch(Exception e){c.rollback();throw e;}}catch(Exception e){error(e);}}
    private void delete(Sales sale){if(!confirm("Delete "+sale.getInvoiceNo()+"? Stock will be restored."))return;try{service.delete(sale.getInvoiceNo());log("SALE",sale.getId(),"DELETED",sale.getInvoiceNo());refresh();closeDetails();}catch(Exception e){error(e);}}

    @FXML private void exportSale(){File f=chooseSave("Export Sales Register","Sales_Register.xlsx","Excel","*.xlsx");if(f==null)return;try(Workbook w=new XSSFWorkbook();FileOutputStream out=new FileOutputStream(f)){Sheet sh=w.createSheet("Sales Register");String[]h={"Invoice No","Date","Customer","Mobile","GSTIN","Amount","Paid","Balance","Due Date","Payment Status","Email","WhatsApp"};Row row=sh.createRow(0);for(int i=0;i<h.length;i++)row.createCell(i).setCellValue(h[i]);int n=1;for(Sales s:filteredSales){row=sh.createRow(n++);Object[]v={s.getInvoiceNo(),s.getInvoiceDate().toString(),s.getCustomer().getName(),safe(s.getCustomer().getPhone()),safe(s.getCustomer().getGstin()),s.getTotalAmount(),s.getPaidAmount(),s.getBalanceAmount(),str(s.getDueDate()),s.getPaymentStatus(),s.isEmailSent()?"Sent":"Not Sent",s.isWhatsappSent()?"Sent":"Not Sent"};for(int i=0;i<v.length;i++){if(v[i] instanceof Number z)row.createCell(i).setCellValue(z.doubleValue());else row.createCell(i).setCellValue(String.valueOf(v[i]));}}for(int i=0;i<h.length;i++)sh.autoSizeColumn(i);w.write(out);info("Sales register exported.");}catch(Exception e){error(e);}}
    @FXML private void exportRegisterPdf(){File f=chooseSave("Export Sales Register PDF","Sales_Register.pdf","PDF","*.pdf");if(f==null)return;try{org.example.service.BrandedRegisterPdfService.export(f.toPath(),"Sales Register",new String[]{"Invoice","Date","Customer","Amount","Paid","Balance","Status"},filteredSales.stream().map(s->new String[]{s.getInvoiceNo(),s.getInvoiceDate().toString(),s.getCustomer().getName(),money(s.getTotalAmount()),money(s.getPaidAmount()),money(s.getBalanceAmount()),"PAID".equalsIgnoreCase(s.getPaymentStatus())?"COMPLETED":s.getPaymentStatus()}).toList(),new float[]{2,1.3f,2.6f,1.4f,1.4f,1.4f,1.2f});info("PDF exported.");}catch(Exception e){error(e);}}
    @FXML private void printRegister(){PrinterJob job=PrinterJob.createPrinterJob();if(job!=null&&job.showPrintDialog(tableSales.getScene().getWindow())){boolean ok=job.printPage(tableSales);if(ok)job.endJob();}}

    private File chooseSave(String title,String name,String label,String pattern){FileChooser c=new FileChooser();c.setTitle(title);c.setInitialFileName(name);c.getExtensionFilters().add(new FileChooser.ExtensionFilter(label,pattern));return c.showSaveDialog(tableSales.getScene().getWindow());}
    private void communication(String type,int id,String channel,String recipient,String status,String error){try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO communication_log(entity_type,entity_id,channel,recipient,status,error_message,created_by) VALUES(?,?,?,?,?,?,?)")){p.setString(1,type);p.setInt(2,id);p.setString(3,channel);p.setString(4,recipient);p.setString(5,status);p.setString(6,error);p.setString(7,user());p.executeUpdate();}catch(Exception ignored){}}
    private void log(String type,int id,String action,String detail){try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by) VALUES(?,?,?,?,?)")){p.setString(1,type);p.setInt(2,id);p.setString(3,action);p.setString(4,detail);p.setString(5,user());p.executeUpdate();}catch(Exception ignored){}}
    private String nextInvoice(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*)+1 FROM sales_header")){return "INV-"+LocalDate.now().getYear()+"-"+String.format("%04d",r.next()?r.getInt(1):1);}}
    private String user(){return SessionService.current()==null?"System":SessionService.current().getFullName();}
    private String dueLabel(Sales s){if(s.getBalanceAmount()<=.01)return "Paid";if(s.getDueDate()==null)return "Not set";long d=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),s.getDueDate());return d<0?"Overdue by "+Math.abs(d)+" days":d==0?"Due today":"Due in "+d+" days";}
    private String money(double v){return currency.format(v).replace("₹","₹ ");}private String safe(String v){return v==null?"":v;}private String lower(String v){return safe(v).toLowerCase(Locale.ROOT);}private String digits(String v){return safe(v).replaceAll("\\D","");}private String str(Object v){return v==null?"":v.toString();}private LocalDate date(String v){try{return v==null||v.isBlank()?null:LocalDate.parse(v);}catch(Exception e){return null;}}private double parseAmount(String v,double fallback){try{return v==null||v.isBlank()?fallback:Double.parseDouble(v.replace(",",""));}catch(Exception e){return fallback;}}
    private boolean confirm(String text){return org.example.util.ModernDialog.confirm(tableSales,"Confirmation","Are you sure?",text);}private void info(String m){org.example.util.ToastManager.success(tableSales,"Completed",m);}private void warning(String m){org.example.util.ModernDialog.warning(tableSales,"Warning","Please review",m);org.example.util.ToastManager.warning(tableSales,"Warning",m);}private void error(Throwable e){e.printStackTrace();String message=e.getMessage()==null?"Operation failed":e.getMessage();org.example.util.ModernDialog.error(tableSales,"Operation failed","Something went wrong",message);}private String rootMessage(Throwable failure){Throwable root=failure;while(root.getCause()!=null)root=root.getCause();String message=root.getMessage();return message==null||message.isBlank()?root.getClass().getSimpleName():message;}
}
