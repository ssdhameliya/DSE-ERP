package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.api.insights.InsightsApiClient;
import org.example.api.reporting.ReportingApiClient;
import org.example.api.reporting.ReportScheduleApiClient;
import org.example.api.reporting.ReportScheduleApiClient.ScheduleRow;
import org.example.api.reporting.ReportScheduleApiClient.ScheduleRequest;
import org.example.api.reporting.ReportScheduleApiClient.SavedReportOption;
import org.example.api.reporting.ReportScheduleApiClient.RunHistory;
import org.example.api.reporting.ReportingApiClient.ReportDefinition;
import org.example.api.reporting.ReportingApiClient.ReportRequest;
import org.example.api.support.SupportApiClient;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.BusinessReportService;
import org.example.service.ReportingSavedConfig;
import org.example.service.SessionService;
import org.example.util.*;

import java.io.File;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class ReportsController implements ScreenLifecycle {
    private static final String TASK_KEY = "reports-load";
    private static volatile int pendingTab = -1;

    @FXML private Label lblPageTitle,lblPageSubtitle,lblPurchase,lblSales,lblStock,lblLowStock,lblProfit,lblReceivables,lblCustomers,lblMargin,lblReportCenterCategory;
    @FXML private Label lblActiveSchedules,lblNextRun,lblReportsMonth,lblScheduleFailures;
    @FXML private StackPane reportPageIcon,reportSalesIcon,reportPurchaseIcon,reportProfitIcon,reportReceivableIcon,reportStockIcon,reportCustomerIcon,reportCenterSearchIcon,savedReportSearchIcon,scheduleSearchIcon;
    @FXML private DatePicker dpFrom,dpTo;
    @FXML private ComboBox<String> cmbDashboardPeriod,cmbReportType,cmbParty,cmbItem,cmbSalesPerson,cmbScheduleStatus,cmbScheduleFormat;
    @FXML private ProgressBar profitProgress;
    @FXML private ListView<String> paymentSummary,stockSummary,topCustomersSummary,topItemsSummary,performanceSummary,reportCategories;
    @FXML private TableView<String[]> tblSales,tblPurchases;
    @FXML private TableView<ScheduleRow> tblSchedules;
    @FXML private TableColumn<String[],String> colSaleNo,colSaleDate,colSaleParty,colSaleAmount,colSaleStatus;
    @FXML private TableColumn<String[],String> colPurchaseNo,colPurchaseDate,colPurchaseParty,colPurchaseAmount,colPurchaseStatus;
    @FXML private TableColumn<ScheduleRow,String> colScheduleName,colScheduleReport,colScheduleFrequency,colScheduleTime,colScheduleFormat,colScheduleDelivery,colScheduleNextRun,colScheduleStatus;
    @FXML private TableColumn<ScheduleRow,Void> colScheduleActions;
    @FXML private Button btnRefresh,btnApply,btnReset,btnViewSales,btnViewPurchases,btnContextAction;
    @FXML private MenuButton btnExport;
    @FXML private MenuItem miExcel,miPdf,miCsv;
    @FXML private TabPane reportTabs;
    @FXML private TextField txtReportSearch,txtSavedSearch,txtScheduleSearch;
    @FXML private TilePane reportCards;

    @FXML private TableView<SavedReportRow> tblSavedReports;
    @FXML private TableColumn<SavedReportRow,String> colSavedName,colSavedBase,colSavedPreset,colSavedGrouping,colSavedSorting,colSavedColumns,colSavedStatus;
    @FXML private TableColumn<SavedReportRow,Void> colSavedActions;

    private final BusinessReportService reportService = new BusinessReportService();
    private final InsightsApiClient insightsApi = new InsightsApiClient();
    private final SupportApiClient supportApi = new SupportApiClient();
    private final ReportingApiClient reportingApi = new ReportingApiClient();
    private final ReportScheduleApiClient scheduleApi = new ReportScheduleApiClient();
    private final List<ReportDefinition> reportDefinitions = new ArrayList<>();
    private final Set<String> favoriteReportIds = new LinkedHashSet<>();
    private final List<SavedReportRow> allSavedReports = new ArrayList<>();
    private final List<ScheduleRow> allSchedules = new ArrayList<>();
    private volatile boolean loaded;
    private volatile boolean loadRequested;
    private boolean initializingPeriod = true;

    public static void requestTab(int index) { pendingTab = Math.max(0, Math.min(3, index)); }

    @FXML public void initialize() {
        configureIcons();
        RegisterUiSupport.configureHeaderSearch(txtReportSearch,reportCenterSearchIcon,"Search reports, e.g. GST...");
        RegisterUiSupport.configureHeaderSearch(txtSavedSearch,savedReportSearchIcon,"Search saved reports...");
        RegisterUiSupport.configureHeaderSearch(txtScheduleSearch,scheduleSearchIcon,"Search schedules...");
        configureStatusCells();
        configureReportTables();
        configureSavedReportsTable();
        configureScheduleTable();

        cmbDashboardPeriod.getItems().setAll("Today","Yesterday","This Week","Last Week","This Month","Last Month","This Quarter","Last Quarter","This Financial Year","Last Financial Year","Last 7 Days","Last 30 Days","Custom");
        cmbDashboardPeriod.setValue("This Month");
        applyDashboardPeriod("This Month");
        cmbDashboardPeriod.valueProperty().addListener((o,a,b) -> {
            if (!initializingPeriod && b != null && !"Custom".equals(b)) {
                applyDashboardPeriod(b);
                requestRefresh();
            }
        });

        cmbReportType.getItems().setAll("All Reports","Sales","Purchase","Inventory","Payments");
        cmbReportType.getSelectionModel().selectFirst();
        setCell(colSaleNo,0); setCell(colSaleDate,1); setCell(colSaleParty,2); setCell(colSaleAmount,3); setCell(colSaleStatus,4);
        setCell(colPurchaseNo,0); setCell(colPurchaseDate,1); setCell(colPurchaseParty,2); setCell(colPurchaseAmount,3); setCell(colPurchaseStatus,4);

        configureReportSearch();
        configureReportCardGrid();
        configureSavedSearch();
        configureReportCategories();
        configureTabBehavior();
        loadFiltersAsync();
        loadReportDefinitions();
        loadFavorites();
        loadSavedReports();

        initializingPeriod = false;
        applyPendingTab();
        requestRefresh();
    }

    private void setCell(TableColumn<String[],String> column,int index){ column.setCellValueFactory(v -> new SimpleStringProperty(part(v.getValue(), index))); }
    private static String part(String[] values,int index){ return values != null && index >= 0 && index < values.length && values[index] != null ? values[index] : ""; }

    private void configureReportCardGrid(){
        if(reportCards==null)return;
        reportCards.widthProperty().addListener((o,a,b)->resizeReportCards(b==null?0:b.doubleValue()));
        javafx.application.Platform.runLater(()->resizeReportCards(reportCards.getWidth()));
    }
    private void resizeReportCards(double width){
        if(reportCards==null||!Double.isFinite(width)||width<120)return;
        double gap=Math.max(0,reportCards.getHgap());
        int columns=Math.max(1,Math.min(3,(int)Math.floor((width+gap)/(300.0+gap))));
        double tile=Math.max(260.0,(width-Math.max(0,columns-1)*gap)/columns);
        reportCards.setPrefColumns(columns);
        reportCards.setPrefTileWidth(tile);
    }

    private void configureTabBehavior(){
        if(reportTabs == null) return;
        reportTabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b) -> applyTabHeader(b == null ? 0 : b.intValue()));
        applyTabHeader(reportTabs.getSelectionModel().getSelectedIndex());
    }
    private void applyPendingTab(){
        int next = pendingTab;
        pendingTab = -1;
        if(reportTabs != null && next >= 0 && next < reportTabs.getTabs().size()) reportTabs.getSelectionModel().select(next);
    }
    private void applyTabHeader(int index){
        boolean dashboard = index == 0;
        cmbDashboardPeriod.setVisible(dashboard); cmbDashboardPeriod.setManaged(dashboard);
        btnRefresh.setVisible(dashboard); btnRefresh.setManaged(dashboard);
        btnExport.setVisible(dashboard); btnExport.setManaged(dashboard);
        btnContextAction.setVisible(!dashboard); btnContextAction.setManaged(!dashboard);
        if(dashboard){ lblPageTitle.setText("Reports Dashboard"); lblPageSubtitle.setText("Real-time financial and operational summary"); }
        else { lblPageTitle.setText("Reports"); lblPageSubtitle.setText("Financial and operational intelligence"); }
        if(index == 1) btnContextAction.setText("Recent Exports");
        else if(index == 2) btnContextAction.setText("New Saved Report");
        else if(index == 3) btnContextAction.setText("+ New Schedule");
        if(index == 2) loadSavedReports();
        if(index == 3) loadSchedules();
    }
    @FXML private void headerContextAction(){
        int index = reportTabs == null ? 0 : reportTabs.getSelectionModel().getSelectedIndex();
        if(index == 1) info("Export History", "Export history is intentionally separate from report definitions. Current exports are available directly from each Report Viewer.");
        else if(index == 2) openReportCenterTab();
        else if(index == 3) newSchedule();
    }

    private void applyDashboardPeriod(String preset){
        LocalDate today = BusinessClock.today(), from = today, to = today;
        switch(preset){
            case "Yesterday" -> { from = today.minusDays(1); to = from; }
            case "This Week" -> from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "Last Week" -> { to = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1); from = to.minusDays(6); }
            case "This Month" -> from = today.withDayOfMonth(1);
            case "Last Month" -> { LocalDate p=today.minusMonths(1); from=p.withDayOfMonth(1); to=p.withDayOfMonth(p.lengthOfMonth()); }
            case "This Quarter" -> { int m=((today.getMonthValue()-1)/3)*3+1; from=LocalDate.of(today.getYear(),m,1); }
            case "Last Quarter" -> { LocalDate p=today.minusMonths(3); int m=((p.getMonthValue()-1)/3)*3+1; from=LocalDate.of(p.getYear(),m,1); to=from.plusMonths(3).minusDays(1); }
            case "This Financial Year" -> { int y=today.getMonthValue()>=4?today.getYear():today.getYear()-1; from=LocalDate.of(y,4,1); to=LocalDate.of(y+1,3,31); }
            case "Last Financial Year" -> { int y=today.getMonthValue()>=4?today.getYear()-1:today.getYear()-2; from=LocalDate.of(y,4,1); to=LocalDate.of(y+1,3,31); }
            case "Last 7 Days" -> from=today.minusDays(6);
            case "Last 30 Days" -> from=today.minusDays(29);
            default -> { }
        }
        dpFrom.setValue(from); dpTo.setValue(to);
    }

    private void loadFiltersAsync(){
        UiTaskExecutor.submitLatest("reports-filters", this::readFilters, this::applyFilters, error -> PerformanceMonitor.event("reports-filters-error", String.valueOf(error.getMessage())));
    }
    private FilterData readFilters(){ var f=insightsApi.reportFilters(); return new FilterData(f.parties(), f.items(), f.salespeople()); }
    private void applyFilters(FilterData data){ setOptions(cmbParty,"All Customers / Suppliers",data.parties()); setOptions(cmbItem,"All Items",data.items()); setOptions(cmbSalesPerson,"All Sales Persons",data.salespeople()); }
    private void setOptions(ComboBox<String> box,String all,List<String> values){ String selected=box.getValue(); box.getItems().setAll(all); if(values!=null)box.getItems().addAll(values); if(selected!=null&&box.getItems().contains(selected))box.setValue(selected); else box.getSelectionModel().selectFirst(); }

    @FXML private void resetFilters(){
        cmbReportType.getSelectionModel().selectFirst(); cmbParty.getSelectionModel().selectFirst(); cmbItem.getSelectionModel().selectFirst(); cmbSalesPerson.getSelectionModel().selectFirst();
        initializingPeriod=true; cmbDashboardPeriod.setValue("This Month"); applyDashboardPeriod("This Month"); initializingPeriod=false; refresh();
    }
    @FXML private void refresh(){ requestRefresh(); }
    private void requestRefresh(){
        if(dpFrom.getValue()==null||dpTo.getValue()==null||dpFrom.getValue().isAfter(dpTo.getValue())){ error("Choose a valid reporting date range."); return; }
        String from=dpFrom.getValue().toString(),to=dpTo.getValue().toString();
        String reportType=selected(cmbReportType,"All Reports"),party=selected(cmbParty,"All Customers / Suppliers"),item=selected(cmbItem,"All Items"),salesperson=selected(cmbSalesPerson,"All Sales Persons");
        loadRequested=true; setBusy(true);
        UiTaskExecutor.submitLatest(TASK_KEY, () -> loadReport(from,to,reportType,party,item,salesperson), data -> {
            applyReportCore(data); loaded=true; loadRequested=false; ScreenRefreshPolicy.markRefreshed("reports"); setBusy(false);
        }, failure -> { loadRequested=false; setBusy(false); error("Could not load report data: "+root(failure)); });
    }
    private ReportData loadReport(String from,String to,String reportType,String party,String item,String salesperson) {
        var d=insightsApi.report(from,to,reportType,party,item,salesperson);
        List<Point> cp=d.customerPoints().stream().map(x->new Point(x.label(),x.value())).toList();
        List<Point> ip=d.itemPoints().stream().map(x->new Point(x.label(),x.value())).toList();
        List<String[]> sr=d.salesRows().stream().map(x->new String[]{x.number(),x.date(),x.party(),money(x.amount()),x.status()}).toList();
        List<String[]> pr=d.purchaseRows().stream().map(x->new String[]{x.number(),x.date(),x.party(),money(x.amount()),x.status()}).toList();
        return new ReportData(d.sales(),d.purchase(),d.profit(),d.receivables(),d.stock(),d.low(),d.customers(),cp,ip,sr,pr,d.salesPaid(),d.payables(),d.purchasesPaid(),d.items(),d.out(),d.salesCount(),d.purchaseCount(),d.averageSale());
    }
    private void applyReportCore(ReportData d){
        setMetric(lblSales,d.sales()); setMetric(lblPurchase,d.purchase()); setMetric(lblProfit,d.profit()); setMetric(lblReceivables,d.receivables()); setMetric(lblStock,d.stock());
        lblLowStock.setText(d.low()+" low-stock items"); lblCustomers.setText(String.valueOf(d.customers()));
        double margin=d.sales()==0?0:(d.profit()/d.sales())*100; lblMargin.setText(String.format("%.2f%%",margin)); profitProgress.setProgress(Math.max(0,Math.min(1,Math.abs(margin)/100)));
        tblSales.getItems().setAll(d.salesRows()); tblPurchases.getItems().setAll(d.purchaseRows());
        paymentSummary.getItems().setAll("Total Receivables  •  "+money(d.receivables()),"Received Amount   •  "+money(d.salesPaid()),"Total Payables    •  "+money(d.payables()),"Paid Amount       •  "+money(d.purchasesPaid()));
        stockSummary.getItems().setAll("Total Items       •  "+d.items(),"Low Stock Items   •  "+d.low(),"Out of Stock      •  "+d.out(),"Stock Value       •  "+money(d.stock()));
        topCustomersSummary.getItems().setAll(summaryLines(d.customerPoints(),"No customer sales in selected period"));
        topItemsSummary.getItems().setAll(summaryLines(d.itemPoints(),"No item sales in selected period"));
        performanceSummary.getItems().setAll("Gross Profit      •  "+money(d.profit()),"Sales Invoices    •  "+d.salesCount(),"Purchase Invoices •  "+d.purchaseCount(),"Average Sale      •  "+money(d.averageSale()));
    }
    private List<String> summaryLines(List<Point> points,String empty){ if(points==null||points.isEmpty())return List.of(empty); List<String> rows=new ArrayList<>(); int rank=1; for(Point point:points)rows.add((rank++)+". "+point.label()+"  •  "+money(point.value())); return rows; }
    private static String selected(ComboBox<String> box,String all){ String value=box==null?null:box.getValue(); if(value==null||value.isBlank()||value.equalsIgnoreCase(all)||value.toUpperCase(Locale.ROOT).startsWith("ALL "))return ""; return value.trim(); }
    private void setMetric(Label label,double value){ String full=money(value); label.setTooltip(new Tooltip(full)); label.setText(compactMoney(value)); }
    private String compactMoney(double value){ double abs=Math.abs(value); String sign=value<0?"-":""; if(abs>=10_000_000)return sign+"₹ "+String.format("%.2f Cr",abs/10_000_000d); if(abs>=100_000)return sign+"₹ "+String.format("%.2f L",abs/100_000d); return money(value); }
    private void setBusy(boolean busy){ btnRefresh.setDisable(busy); btnApply.setDisable(busy); btnReset.setDisable(busy); tblSales.setDisable(busy); tblPurchases.setDisable(busy); }

    private void configureReportCategories(){
        if(reportCategories==null)return;
        reportCategories.setCellFactory(list->new ListCell<>(){
            @Override protected void updateItem(String value,boolean empty){
                super.updateItem(value,empty);
                if(empty||value==null){setText(null);setGraphic(null);return;}
                setText(value);String semantic=reportCategorySemantic(value);
                setGraphic(IconFactory.compactIcon(semantic,14));setGraphicTextGap(6);
                getProperties().put("erp.icon.semantic",semantic);
            }
        });
        reportCategories.getSelectionModel().selectedItemProperty().addListener((o,a,b)->renderReportCards());
    }
    private void configureReportSearch(){ if(txtReportSearch!=null)txtReportSearch.textProperty().addListener((o,a,b)->renderReportCards()); }
    private void loadReportDefinitions(){
        UiTaskExecutor.submitLatest("report-center-definitions", reportingApi::definitions, defs -> {
            reportDefinitions.clear(); if(defs!=null)reportDefinitions.addAll(defs);
            LinkedHashSet<String> cats=new LinkedHashSet<>(); cats.add("★ Favorites"); for(ReportDefinition d:reportDefinitions)cats.add(d.category());
            reportCategories.getItems().setAll(cats); if(reportCategories.getSelectionModel().getSelectedItem()==null){ String first=cats.contains("Sales")?"Sales":cats.stream().findFirst().orElse("★ Favorites"); reportCategories.getSelectionModel().select(first); }
            renderReportCards(); loadSavedReports();
        }, e -> error("Could not load Report Center definitions: "+root(e)));
    }
    private void loadFavorites(){
        Integer uid=currentUserId();
        UiTaskExecutor.submitLatest("report-favorites-load",()->supportApi.savedViews("REPORT_FAVORITES",uid),views->{
            favoriteReportIds.clear();
            if(views!=null)for(SupportApiClient.SavedView v:views)if("__favorites__".equals(v.name())&&v.data()!=null&&!v.data().isBlank())for(String id:v.data().split(","))if(!id.isBlank())favoriteReportIds.add(id.trim());
            renderReportCards();
        },e->PerformanceMonitor.event("report-favorites-load",root(e)));
    }
    private void persistFavorites(){
        Integer uid=currentUserId(); String payload=String.join(",",favoriteReportIds);
        UiTaskExecutor.submitAction("report-favorites-save",()->{supportApi.saveView(uid,"REPORT_FAVORITES","__favorites__",payload);return true;},x->{},e->PerformanceMonitor.event("report-favorites-save",root(e)));
    }
    private void renderReportCards(){
        if(reportCards==null||reportCategories==null)return;
        String category=reportCategories.getSelectionModel().getSelectedItem(); if(category==null)category="Sales";
        String query=txtReportSearch==null||txtReportSearch.getText()==null?"":txtReportSearch.getText().trim().toLowerCase(Locale.ROOT);
        String heading="★ Favorites".equals(category)?"FAVORITE REPORTS":category.toUpperCase(Locale.ROOT)+" REPORTS"; if(lblReportCenterCategory!=null)lblReportCenterCategory.setText(heading);
        reportCards.getChildren().clear();
        for(ReportDefinition def:reportDefinitions){
            boolean categoryMatch="★ Favorites".equals(category)?favoriteReportIds.contains(def.id()):category.equalsIgnoreCase(def.category());
            boolean searchMatch=query.isBlank()||(def.title()+" "+def.description()+" "+def.category()).toLowerCase(Locale.ROOT).contains(query);
            if(categoryMatch&&searchMatch)reportCards.getChildren().add(createReportCard(def));
        }
        if(reportCards.getChildren().isEmpty()){ Label empty=new Label(query.isBlank()?"No reports in this category yet.":"No reports match your search."); empty.getStyleClass().add("page-subtitle"); reportCards.getChildren().add(empty); }
    }
    private Node createReportCard(ReportDefinition def){
        VBox card=new VBox(7); card.getStyleClass().addAll("report-center-card","report-card"); card.setMinWidth(260); card.setPrefWidth(330); card.setMinHeight(112); card.setPrefHeight(112);
        HBox top=new HBox(8); top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label title=new Label(def.title()); title.getStyleClass().add("report-card-title"); title.setGraphic(IconFactory.compactIcon(reportSemantic(def),16));
        Region spacer=new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button star=new Button(favoriteReportIds.contains(def.id())?"★":"☆"); star.getStyleClass().add("report-favorite-button");
        star.setOnAction(e->{ if(favoriteReportIds.contains(def.id()))favoriteReportIds.remove(def.id());else favoriteReportIds.add(def.id()); persistFavorites(); renderReportCards(); });
        top.getChildren().addAll(title,spacer,star);
        Label description=new Label(def.description()); description.setWrapText(true); description.getStyleClass().add("page-subtitle");
        Region grow=new Region(); VBox.setVgrow(grow, Priority.ALWAYS);
        Button open=new Button("Open Report"); open.getStyleClass().addAll("approved-button","approved-primary-button","report-card-open-button"); open.setGraphic(IconFactory.compactIcon("view",15));open.setGraphicTextGap(6);open.setOnAction(e->openUnified(def.id(),defaultGroup(def)));
        card.getChildren().addAll(top,description,grow,open); return card;
    }
    private String defaultGroup(ReportDefinition def){ if(def==null||def.groupByOptions()==null||def.groupByOptions().isEmpty())return "None"; return def.groupByOptions().stream().filter(x->"None".equalsIgnoreCase(x)).findFirst().orElse("None"); }
    private String reportSemantic(ReportDefinition def){
        if(def==null)return "report";String id=safe(def.id()).toUpperCase(Locale.ROOT);
        return switch(id){
            case "SALES_REGISTER" -> "invoice";
            case "SALES_BY_CUSTOMER" -> "customer";
            case "SALES_BY_ITEM","ITEM_LEDGER" -> "item";
            case "PURCHASE_REGISTER" -> "purchase";
            case "RETURNS_ANALYSIS" -> "return";
            case "GST_TAX" -> "tax";
            case "RECEIVABLE_AGEING","PAYABLE_AGEING" -> "payment";
            case "STOCK_SUMMARY" -> "inventory";
            case "BANK_RECONCILIATION" -> "bank";
            case "PROFITABILITY" -> "report";
            default -> reportCategorySemantic(def.category());
        };
    }
    private String reportCategorySemantic(String category){ String c=safe(category).toLowerCase(Locale.ROOT); if(c.contains("favorite"))return "favorite"; if(c.contains("purchase"))return "purchase"; if(c.contains("return"))return "return"; if(c.contains("gst")||c.contains("tax"))return "tax"; if(c.contains("receiv")||c.contains("payable"))return "payment"; if(c.contains("inventory"))return "inventory"; if(c.contains("bank"))return "bank"; if(c.contains("profit"))return "report"; return "sale"; }

    @FXML private void openSalesRegister(){openUnified("SALES_REGISTER","None");}
    @FXML private void openPurchaseRegister(){openUnified("PURCHASE_REGISTER","None");}
    @FXML private void openReturns(){openUnified("RETURNS_ANALYSIS","Return Type");}
    @FXML private void openInventory(){openUnified("STOCK_SUMMARY","Category");}
    @FXML private void openReceivables(){openUnified("RECEIVABLE_AGEING","Age Bucket");}
    @FXML private void openPayables(){openUnified("PAYABLE_AGEING","Age Bucket");}
    @FXML private void openProfitability(){openUnified("PROFITABILITY","Customer");}
    private void openUnified(String reportId,String groupBy){ ReportViewContext.open(reportId,groupBy,dpFrom.getValue()==null?null:dpFrom.getValue().toString(),dpTo.getValue()==null?null:dpTo.getValue().toString()); navigate("/fxml/pages/ReportViewer.fxml"); }

    private void configureSavedReportsTable(){
        if(tblSavedReports==null)return;
        colSavedName.setCellValueFactory(v->new SimpleStringProperty(v.getValue().name()));
        colSavedBase.setCellValueFactory(v->new SimpleStringProperty(v.getValue().baseReport()));
        colSavedPreset.setCellValueFactory(v->new SimpleStringProperty(v.getValue().datePreset()));
        colSavedGrouping.setCellValueFactory(v->new SimpleStringProperty(v.getValue().grouping()));
        colSavedSorting.setCellValueFactory(v->new SimpleStringProperty(v.getValue().sorting()));
        colSavedColumns.setCellValueFactory(v->new SimpleStringProperty(v.getValue().columns()));
        colSavedStatus.setCellValueFactory(v->new SimpleStringProperty("NOT SCHEDULED"));
        colSavedStatus.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v,boolean empty){super.updateItem(v,empty);setText(empty?null:v);getStyleClass().removeAll("report-status-paid","report-status-pending");if(!empty)getStyleClass().add("report-status-other");}});
        colSavedActions.setCellFactory(c->new TableCell<>(){
            final MenuButton actions=new MenuButton("Actions");
            final MenuItem open=new MenuItem("Open Report"), schedule=new MenuItem("Schedule");
            {
                actions.getStyleClass().addAll("row-actions","table-action-menu","approved-row-action","report-row-actions");
                actions.setGraphic(IconFactory.compactIcon("actions",15));actions.setGraphicTextGap(6);actions.getProperties().put("erp.icon.semantic","actions");
                open.setGraphic(IconFactory.compactIcon("view",14));schedule.setGraphic(IconFactory.compactIcon("calendar",14));
                actions.getItems().addAll(open,schedule);IconFactory.decorateActionMenu(actions);
                open.setOnAction(e->{SavedReportRow row=getTableRow()==null?null:getTableRow().getItem();if(row!=null)applySavedReport(row);});
                schedule.setOnAction(e->{SavedReportRow row=getTableRow()==null?null:getTableRow().getItem();if(row!=null)scheduleSavedReport(row);});
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:actions);}
        });
        tblSavedReports.setPlaceholder(new Label("No saved reports yet. Open a report from Report Center and choose Save Report."));
    }
    private void configureSavedSearch(){ if(txtSavedSearch!=null)txtSavedSearch.textProperty().addListener((o,a,b)->filterSavedReports()); }
    @FXML public void loadSavedReports(){
        if(tblSavedReports==null)return; Integer uid=currentUserId();
        UiTaskExecutor.submitLatest("reports-saved",()->supportApi.savedViews("REPORT_CENTER",uid),views->{
            allSavedReports.clear(); if(views!=null)for(SupportApiClient.SavedView v:views){SavedReportRow row=toSavedReportRow(v);if(row!=null)allSavedReports.add(row);} filterSavedReports();
        },e->{tblSavedReports.getItems().clear();PerformanceMonitor.event("reports-saved-error",root(e));});
    }
    private SavedReportRow toSavedReportRow(SupportApiClient.SavedView view){
        if(view==null||view.name()==null)return null; ReportingSavedConfig saved=ReportingSavedConfig.decode(view.data());
        if(saved!=null&&saved.request()!=null){ReportRequest q=saved.request();String group=blank(q.groupBy())?"None":titleCase(q.groupBy());String sorting=blank(q.sortKey())?"Default":titleCase(q.sortKey())+" "+("ASC".equalsIgnoreCase(q.sortDirection())?"↑":"↓");String cols=q.visibleColumns()==null||q.visibleColumns().isEmpty()?"Standard":q.visibleColumns().size()+" selected";return new SavedReportRow(view.name(),reportTitle(q.reportId()),blank(saved.datePreset())?"Custom":saved.datePreset(),group,sorting,cols,saved,q);}
        ReportRequest legacy=legacyRequest(view.data()); if(legacy==null)return null; return new SavedReportRow(view.name(),reportTitle(legacy.reportId()),"Custom",blank(legacy.groupBy())?"None":titleCase(legacy.groupBy()),"Date ↓","Standard",null,legacy);
    }
    private ReportRequest legacyRequest(String data){
        if(data==null||data.startsWith("REPORT_V2:"))return null; String[]x=data.split("\\|",-1); if(x.length<6)return null;
        String reportId=switch(safe(x[0]).trim().toUpperCase(Locale.ROOT)){case "PURCHASE"->"PURCHASE_REGISTER";case "INVENTORY"->"STOCK_SUMMARY";case "PAYMENTS"->"RECEIVABLE_AGEING";default->"SALES_REGISTER";};
        return new ReportRequest(reportId,safe(x[4]),safe(x[5]),normalizeAll(x[1]),normalizeAll(x[2]),normalizeAll(x[3]),"","","","","","","","","date","DESC",null,null,0,25,List.of());
    }
    private void filterSavedReports(){ String q=txtSavedSearch==null||txtSavedSearch.getText()==null?"":txtSavedSearch.getText().trim().toLowerCase(Locale.ROOT); List<SavedReportRow> rows=allSavedReports.stream().filter(r->q.isBlank()||(r.name()+" "+r.baseReport()+" "+r.datePreset()+" "+r.grouping()).toLowerCase(Locale.ROOT).contains(q)).toList(); tblSavedReports.getItems().setAll(rows); }
    private void applySavedReport(SavedReportRow row){ if(row==null||row.request()==null)return; String preset=row.savedConfig()==null?row.datePreset():row.savedConfig().datePreset(); ReportViewContext.openSaved(row.request(),blank(preset)?"Custom":preset); navigate("/fxml/pages/ReportViewer.fxml"); }
    @FXML private void openReportCenterTab(){ if(reportTabs!=null)reportTabs.getSelectionModel().select(1); }

    private void configureScheduleTable(){
        if(tblSchedules==null)return;
        colScheduleName.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().name())));
        colScheduleReport.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().savedReport())));
        colScheduleFrequency.setCellValueFactory(v->new SimpleStringProperty(scheduleFrequencyLabel(v.getValue())));
        colScheduleTime.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().time())));
        colScheduleFormat.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().format())));
        colScheduleDelivery.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().delivery())));
        colScheduleNextRun.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().nextRun())));
        colScheduleStatus.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().status())));
        colScheduleStatus.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String value,boolean empty){
                super.updateItem(value,empty);getStyleClass().removeAll("report-status-paid","report-status-pending","report-status-other");
                if(empty||value==null){setText(null);return;}setText(value);getStyleClass().add("ACTIVE".equalsIgnoreCase(value)?"report-status-paid":"report-status-pending");
            }
        });
        colScheduleActions.setCellFactory(c->new TableCell<>(){
            private final MenuButton actions=new MenuButton("Actions");
            private final MenuItem run=new MenuItem("Run Now"), edit=new MenuItem("Edit"), toggle=new MenuItem("Pause"), duplicate=new MenuItem("Duplicate"), history=new MenuItem("View History"), delete=new MenuItem("Delete");
            {
                actions.getStyleClass().addAll("row-actions","table-action-menu","approved-row-action","report-row-actions");
                actions.setGraphic(IconFactory.compactIcon("actions",15));actions.setGraphicTextGap(6);actions.getProperties().put("erp.icon.semantic","actions");
                run.setGraphic(IconFactory.compactIcon("refresh",14));edit.setGraphic(IconFactory.compactIcon("edit",14));toggle.setGraphic(IconFactory.compactIcon("status",14));duplicate.setGraphic(IconFactory.compactIcon("copy",14));history.setGraphic(IconFactory.compactIcon("history",14));delete.setGraphic(IconFactory.compactIcon("delete",14));
                actions.getItems().addAll(run,edit,toggle,new SeparatorMenuItem(),duplicate,history,new SeparatorMenuItem(),delete);IconFactory.decorateActionMenu(actions);
                run.setOnAction(e->withRow(this,ReportsController.this::runScheduleNow));
                edit.setOnAction(e->withRow(this,ReportsController.this::editSchedule));
                toggle.setOnAction(e->withRow(this,ReportsController.this::toggleSchedule));
                duplicate.setOnAction(e->withRow(this,ReportsController.this::duplicateSchedule));
                history.setOnAction(e->withRow(this,ReportsController.this::showScheduleHistory));
                delete.setOnAction(e->withRow(this,ReportsController.this::deleteSchedule));
            }
            @Override protected void updateItem(Void value,boolean empty){
                super.updateItem(value,empty);ScheduleRow row=getTableRow()==null?null:getTableRow().getItem();
                if(empty||row==null){setGraphic(null);return;}toggle.setText("ACTIVE".equalsIgnoreCase(row.status())?"Pause":"Resume");setGraphic(actions);
            }
        });
        tblSchedules.setPlaceholder(new Label("No schedules configured yet. Create a Saved Report first, then select + New Schedule."));
        cmbScheduleStatus.getItems().setAll("All Status","ACTIVE","PAUSED");cmbScheduleStatus.setValue("All Status");
        cmbScheduleFormat.getItems().setAll("All Formats","PDF","XLSX","PDF + XLSX","CSV");cmbScheduleFormat.setValue("All Formats");
        if(txtScheduleSearch!=null)txtScheduleSearch.textProperty().addListener((o,a,b)->filterSchedules());
        cmbScheduleStatus.valueProperty().addListener((o,a,b)->filterSchedules());
        cmbScheduleFormat.valueProperty().addListener((o,a,b)->filterSchedules());
        lblActiveSchedules.setText("0");lblNextRun.setText("Not scheduled");lblReportsMonth.setText("0");lblScheduleFailures.setText("0");
    }

    private static void withRow(TableCell<ScheduleRow,?> cell,java.util.function.Consumer<ScheduleRow> action){
        ScheduleRow row=cell==null||cell.getTableRow()==null?null:cell.getTableRow().getItem();if(row!=null)action.accept(row);
    }

    @FXML public void loadSchedules(){
        if(tblSchedules==null)return;
        UiTaskExecutor.submitLatest("reports-schedules-load",scheduleApi::page,this::applySchedulePage,e->{PerformanceMonitor.event("reports-schedules-error",root(e));tblSchedules.setPlaceholder(new Label("Scheduled Reports could not be loaded: "+root(e)));});
    }

    private void applySchedulePage(ReportScheduleApiClient.SchedulePage page){
        allSchedules.clear();if(page!=null&&page.schedules()!=null)allSchedules.addAll(page.schedules());filterSchedules();
        ReportScheduleApiClient.ScheduleSummary m=page==null?null:page.summary();
        lblActiveSchedules.setText(String.valueOf(m==null?0:m.activeSchedules()));
        String next=m==null?safe(""):safe(m.nextRun());lblNextRun.setText(next.isBlank()?"Not scheduled":next);
        if(m!=null&&!safe(m.nextSchedule()).isBlank())lblNextRun.setTooltip(new Tooltip(m.nextSchedule()));else lblNextRun.setTooltip(null);
        lblReportsMonth.setText(String.valueOf(m==null?0:m.reportsThisMonth()));
        lblScheduleFailures.setText(String.valueOf(m==null?0:m.failuresLast30Days()));
    }

    private void filterSchedules(){
        if(tblSchedules==null)return;
        String q=txtScheduleSearch==null?"":safe(txtScheduleSearch.getText()).trim().toLowerCase(Locale.ROOT);
        String status=cmbScheduleStatus==null?"All Status":safe(cmbScheduleStatus.getValue());
        String format=cmbScheduleFormat==null?"All Formats":safe(cmbScheduleFormat.getValue());
        List<ScheduleRow> rows=allSchedules.stream().filter(r->{
            boolean text=q.isBlank()||(safe(r.name())+" "+safe(r.savedReport())+" "+safe(r.reportTitle())+" "+safe(r.recipients())).toLowerCase(Locale.ROOT).contains(q);
            boolean st=status.startsWith("All")||status.equalsIgnoreCase(r.status());
            boolean fm=format.startsWith("All")||format.equalsIgnoreCase(r.format());return text&&st&&fm;
        }).toList();tblSchedules.getItems().setAll(rows);
    }

    private String scheduleFrequencyLabel(ScheduleRow row){
        if(row==null)return "";String f=safe(row.frequency()).toUpperCase(Locale.ROOT);
        return switch(f){
            case "DAILY"->"Daily";
            case "WEEKLY"->"Every "+weekday(row.dayOfWeek());
            case "MONTHLY"->ordinal(row.dayOfMonth())+" of month";
            case "QUARTERLY"->"Quarterly · "+ordinal(row.dayOfMonth());
            case "YEARLY"->monthName(row.monthOfYear())+" "+ordinal(row.dayOfMonth());
            default->titleCase(f);
        };
    }
    private static String weekday(Integer value){try{return DayOfWeek.of(value==null?1:value).getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH);}catch(Exception e){return "Monday";}}
    private static String monthName(Integer value){try{return java.time.Month.of(value==null?1:value).getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH);}catch(Exception e){return "Jan";}}
    private static String ordinal(Integer value){int n=value==null?1:value;int mod=n%100;String suffix=(mod>=11&&mod<=13)?"th":switch(n%10){case 1->"st";case 2->"nd";case 3->"rd";default->"th";};return n+suffix;}

    @FXML private void newSchedule(){loadScheduleOptions(null,null);}
    private void editSchedule(ScheduleRow row){if(row!=null)loadScheduleOptions(row,null);}
    private void scheduleSavedReport(SavedReportRow row){if(row!=null)loadScheduleOptions(null,row.name());}
    private void loadScheduleOptions(ScheduleRow row,String preferredSavedReport){
        UiTaskExecutor.submitLatest("reports-schedule-options",scheduleApi::savedReports,options->{
            if(options==null||options.isEmpty()){info("Scheduled Reports","Create and save a report in Report Viewer before creating a schedule.");return;}
            showScheduleDialog(row,options,preferredSavedReport);
        },e->error("Could not load Saved Reports for scheduling: "+root(e)));
    }

    private void showScheduleDialog(ScheduleRow existing,List<SavedReportOption> options,String preferredSavedReport){
        OwnedDialog<ScheduleRequest> dialog=new OwnedDialog<>(tblSchedules);dialog.setTitle(existing==null?"New Schedule":"Edit Schedule");DialogPresentation.configureWorkspace(dialog,"info");
        DialogPane pane=dialog.getDialogPane();ButtonType saveType=new ButtonType(existing==null?"Create Schedule":"Save Changes",ButtonBar.ButtonData.OK_DONE);pane.getButtonTypes().setAll(ButtonType.CANCEL,saveType);
        GridPane grid=new GridPane();grid.setHgap(12);grid.setVgap(10);grid.setPadding(new javafx.geometry.Insets(8));
        ColumnConstraints a=new ColumnConstraints();a.setMinWidth(145);ColumnConstraints b=new ColumnConstraints();b.setHgrow(Priority.ALWAYS);grid.getColumnConstraints().addAll(a,b);
        TextField name=new TextField(existing==null?"":safe(existing.name()));name.setPromptText("e.g. Daily Sales Summary");name.getStyleClass().add("approved-input");
        ComboBox<SavedReportOption> saved=new ComboBox<>(FXCollections.observableArrayList(options));saved.setMaxWidth(Double.MAX_VALUE);saved.getStyleClass().add("approved-input");
        if(existing!=null)options.stream().filter(x->x.name().equals(existing.savedReport())).findFirst().ifPresent(saved::setValue);
        else if(!blank(preferredSavedReport))options.stream().filter(x->x.name().equals(preferredSavedReport)).findFirst().ifPresent(saved::setValue);
        if(saved.getValue()==null)saved.getSelectionModel().selectFirst();
        ComboBox<String> frequency=new ComboBox<>();frequency.getItems().setAll("Daily","Weekly","Monthly","Quarterly","Yearly");frequency.setMaxWidth(Double.MAX_VALUE);frequency.getStyleClass().add("approved-input");frequency.setValue(existing==null?"Daily":titleCase(existing.frequency()));
        ComboBox<String> weekday=new ComboBox<>();for(DayOfWeek d:DayOfWeek.values())weekday.getItems().add(d.getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH));weekday.setMaxWidth(Double.MAX_VALUE);weekday.getStyleClass().add("approved-input");weekday.getSelectionModel().select(existing==null||existing.dayOfWeek()==null?0:Math.max(0,existing.dayOfWeek()-1));
        Spinner<Integer> day=new Spinner<>(1,31,existing==null||existing.dayOfMonth()==null?1:existing.dayOfMonth());day.setEditable(true);day.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> month=new ComboBox<>();for(java.time.Month m:java.time.Month.values())month.getItems().add(m.getDisplayName(java.time.format.TextStyle.FULL,Locale.ENGLISH));month.setMaxWidth(Double.MAX_VALUE);month.getStyleClass().add("approved-input");month.getSelectionModel().select(existing==null||existing.monthOfYear()==null?0:Math.max(0,existing.monthOfYear()-1));
        HBox dayControls=new HBox(8,weekday,day,month);HBox.setHgrow(weekday,Priority.ALWAYS);HBox.setHgrow(month,Priority.ALWAYS);
        TextField time=new TextField(existing==null?"08:00":safe(existing.time()));time.setPromptText("HH:mm");time.getStyleClass().add("approved-input");
        ComboBox<String> format=new ComboBox<>();format.getItems().setAll("PDF","XLSX","PDF + XLSX","CSV");format.setMaxWidth(Double.MAX_VALUE);format.getStyleClass().add("approved-input");format.setValue(existing==null?"PDF":safe(existing.format()));
        ComboBox<String> delivery=new ComboBox<>();delivery.getItems().setAll("Email","Archive","Email + Archive");delivery.setMaxWidth(Double.MAX_VALUE);delivery.getStyleClass().add("approved-input");delivery.setValue(existing==null?"Email":safe(existing.delivery()));
        TextField recipients=new TextField(existing==null?"":safe(existing.recipients()));recipients.setPromptText("accounts@company.com; owner@company.com");recipients.getStyleClass().add("approved-input");
        Label hint=new Label("The Saved Report keeps its dynamic date preset. For example, ‘Previous Month’ advances automatically every run.");hint.setWrapText(true);hint.getStyleClass().add("page-subtitle");
        addScheduleField(grid,0,"Schedule Name",name);addScheduleField(grid,1,"Saved Report",saved);addScheduleField(grid,2,"Frequency",frequency);addScheduleField(grid,3,"Run Day",dayControls);addScheduleField(grid,4,"Time",time);addScheduleField(grid,5,"Output",format);addScheduleField(grid,6,"Delivery",delivery);addScheduleField(grid,7,"Recipients",recipients);grid.add(hint,0,8,2,1);
        Runnable sync=()->{
            String f=safe(frequency.getValue()).toUpperCase(Locale.ROOT);boolean weekly="WEEKLY".equals(f),monthly=Set.of("MONTHLY","QUARTERLY","YEARLY").contains(f),yearly="YEARLY".equals(f);
            weekday.setVisible(weekly);weekday.setManaged(weekly);day.setVisible(monthly);day.setManaged(monthly);month.setVisible(yearly);month.setManaged(yearly);
            boolean email=safe(delivery.getValue()).toUpperCase(Locale.ROOT).contains("EMAIL");recipients.setDisable(!email);recipients.setPromptText(email?"accounts@company.com; owner@company.com":"Not required for archive-only delivery");
        };frequency.valueProperty().addListener((o,x,y)->sync.run());delivery.valueProperty().addListener((o,x,y)->sync.run());sync.run();
        pane.setContent(grid);
        dialog.setResultConverter(bt->{if(bt!=saveType)return null;SavedReportOption option=saved.getValue();String f=safe(frequency.getValue()).toUpperCase(Locale.ROOT);Integer dow="WEEKLY".equals(f)?weekday.getSelectionModel().getSelectedIndex()+1:null;Integer dom=Set.of("MONTHLY","QUARTERLY","YEARLY").contains(f)?day.getValue():null;Integer moy="YEARLY".equals(f)?month.getSelectionModel().getSelectedIndex()+1:null;return new ScheduleRequest(name.getText(),option==null?"":option.name(),f,dow,dom,moy,time.getText(),format.getValue(),delivery.getValue(),recipients.getText());});
        dialog.showAndWait().ifPresent(request->{
            if(existing==null)UiTaskExecutor.submitAction("report-schedule-create",()->scheduleApi.create(request),x->{ToastManager.success(tblSchedules,"Schedule created","Scheduled Report is active and will run automatically on the server.");loadSchedules();},e->error("Could not create schedule: "+root(e)));
            else UiTaskExecutor.submitAction("report-schedule-update-"+existing.id(),()->scheduleApi.update(existing.id(),request),x->{ToastManager.success(tblSchedules,"Schedule updated","The next run has been recalculated.");loadSchedules();},e->error("Could not update schedule: "+root(e)));
        });
    }
    private static void addScheduleField(GridPane grid,int row,String title,Node control){Label label=new Label(title);label.getStyleClass().add("field-label");grid.add(label,0,row);grid.add(control,1,row);GridPane.setHgrow(control,Priority.ALWAYS);}

    private void runScheduleNow(ScheduleRow row){
        if(row==null)return;UiTaskExecutor.submitAction("report-schedule-run-"+row.id(),()->scheduleApi.run(row.id()),x->{ToastManager.success(tblSchedules,"Scheduled Report completed",safe(x.message()));loadSchedules();},e->{error("Scheduled Report failed: "+root(e));loadSchedules();});
    }
    private void toggleSchedule(ScheduleRow row){
        if(row==null)return;boolean active="ACTIVE".equalsIgnoreCase(row.status());UiTaskExecutor.submitAction("report-schedule-toggle-"+row.id(),()->active?scheduleApi.pause(row.id()):scheduleApi.resume(row.id()),x->{ToastManager.success(tblSchedules,active?"Schedule paused":"Schedule resumed",active?"Automatic runs are paused.":"The next automatic run has been scheduled.");loadSchedules();},e->error("Could not update schedule: "+root(e)));
    }
    private void duplicateSchedule(ScheduleRow row){
        if(row==null)return;UiTaskExecutor.submitAction("report-schedule-duplicate-"+row.id(),()->scheduleApi.duplicate(row.id()),x->{ToastManager.success(tblSchedules,"Schedule duplicated","The copy is created paused so you can review it before enabling.");loadSchedules();},e->error("Could not duplicate schedule: "+root(e)));
    }
    private void deleteSchedule(ScheduleRow row){
        if(row==null)return;Alert confirm=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Delete schedule ‘"+row.name()+"’? Its run history will also be removed.",ButtonType.CANCEL,ButtonType.OK);confirm.setHeaderText("Delete Scheduled Report");if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        UiTaskExecutor.submitAction("report-schedule-delete-"+row.id(),()->scheduleApi.delete(row.id()),x->{ToastManager.success(tblSchedules,"Schedule deleted","The Scheduled Report was removed.");loadSchedules();},e->error("Could not delete schedule: "+root(e)));
    }
    private void showScheduleHistory(ScheduleRow row){
        if(row==null)return;UiTaskExecutor.submitLatest("report-schedule-history-"+row.id(),()->scheduleApi.history(row.id()),history->showHistoryDialog(row,history),e->error("Could not load schedule history: "+root(e)));
    }
    private void showHistoryDialog(ScheduleRow row,List<RunHistory> history){
        OwnedDialog<Void> dialog=new OwnedDialog<>(tblSchedules);dialog.setTitle("Schedule History — "+row.name());DialogPresentation.configureWorkspace(dialog,"info");dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        ListView<String> list=new ListView<>();list.setPrefSize(850,420);list.getStyleClass().add("approved-list");
        if(history==null||history.isEmpty())list.getItems().add("No runs have been recorded yet.");else for(RunHistory h:history){String line=h.startedAt()+"  •  "+h.status()+"  •  "+h.reportTitle()+"  •  "+h.rowCount()+" rows  •  "+h.triggeredBy();if(h.error()!=null&&!h.error().isBlank())line+="\n    "+h.error();list.getItems().add(line);}
        VBox box=new VBox(8,new Label("Latest 100 runs"),list);box.setPadding(new javafx.geometry.Insets(8));VBox.setVgrow(list,Priority.ALWAYS);dialog.getDialogPane().setContent(box);dialog.showAndWait();
    }

    @FXML private void exportPdf(){exportDashboard("PDF Report","business-report.pdf","pdf");}
    @FXML private void exportExcel(){exportDashboard("Excel Report","business-report.xlsx","xlsx");}
    @FXML private void exportCsv(){exportDashboard("CSV Report","business-report.csv","csv");}
    private void exportDashboard(String title,String name,String format){
        org.example.service.PermissionService.require("REPORTS.EXPORT", "Export Reports");
        FileChooser f=new FileChooser();f.setTitle(title);String suffix="."+format.toLowerCase(Locale.ROOT);String stamp=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));String base=name.substring(0,name.length()-suffix.length());f.setInitialFileName(base+"-"+stamp+suffix);try{Path folder=org.example.config.WorkspaceStorageManager.reportFolder("Financial",dpFrom.getValue());if(java.nio.file.Files.isDirectory(folder))f.setInitialDirectory(folder.toFile());}catch(Exception ignored){}f.getExtensionFilters().add(new FileChooser.ExtensionFilter(title,"*"+suffix));File selected=f.showSaveDialog(dpFrom.getScene().getWindow());if(selected==null)return;Path path=selected.toPath();if(!path.toString().toLowerCase(Locale.ROOT).endsWith(suffix))path=Path.of(path+suffix);final Path target=path;UiTaskExecutor.submitAction("reports-export-"+format,()->{switch(format){case "pdf"->reportService.exportPdf(target,dpFrom.getValue(),dpTo.getValue());case "xlsx"->reportService.exportExcel(target,dpFrom.getValue(),dpTo.getValue());case "csv"->reportService.exportCsv(target,dpFrom.getValue(),dpTo.getValue());default->throw new IllegalArgumentException("Unsupported export format: "+format);}return target;},done->ToastManager.success(dpFrom,"Report created","Report created successfully:\n"+done),e->error("Could not create report: "+root(e)));
    }

    private void navigate(String page){NavigationManager.navigateOrReport(page);}
    private String money(double n){return "₹ "+String.format(Locale.of("en","IN"),"%,.2f",n);}
    private void error(String message){Alert a=new OwnedAlert(Alert.AlertType.ERROR,message);a.setHeaderText("Reporting error");a.showAndWait();}
    private void info(String header,String message){Alert a=new OwnedAlert(Alert.AlertType.INFORMATION,message);a.setHeaderText(header);a.showAndWait();}
    private static Integer currentUserId(){return SessionService.current()==null?null:SessionService.current().getId();}
    private static String normalizeAll(String value){String v=safe(value).trim();return v.toUpperCase(Locale.ROOT).startsWith("ALL ")||"ALL".equalsIgnoreCase(v)?"":v;}
    private String reportTitle(String id){return reportDefinitions.stream().filter(d->d.id().equalsIgnoreCase(safe(id))).map(ReportDefinition::title).findFirst().orElse(titleCase(id));}
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static String safe(String value){return value==null?"":value;}
    private static String root(Throwable e){Throwable x=e;while(x!=null&&x.getCause()!=null&&x.getCause()!=x)x=x.getCause();return x==null?"Unknown error":x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    private static String titleCase(String v){if(v==null||v.isBlank())return "";StringBuilder b=new StringBuilder();for(String p:v.toLowerCase(Locale.ROOT).split("[ _]+")){if(p.isBlank())continue;if(!b.isEmpty())b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}

    private void configureReportTables(){
        org.example.util.DynamicTableLayoutManager.install(tblSales);
        org.example.util.DynamicTableLayoutManager.install(tblPurchases);
    }
    private void configureIcons(){
        if(reportPageIcon!=null)reportPageIcon.getChildren().setAll(IconFactory.icon("report",24));
        if(reportTabs!=null&&reportTabs.getTabs().size()>=4){reportTabs.getTabs().get(0).setGraphic(IconFactory.compactIcon("dashboard",14));reportTabs.getTabs().get(1).setGraphic(IconFactory.compactIcon("report",14));reportTabs.getTabs().get(2).setGraphic(IconFactory.compactIcon("save",14));reportTabs.getTabs().get(3).setGraphic(IconFactory.compactIcon("calendar",14));}
        btnRefresh.setGraphic(IconFactory.icon("refresh",16));btnApply.setGraphic(IconFactory.icon("filter",16));btnReset.setGraphic(IconFactory.icon("reset",16));btnExport.setGraphic(IconFactory.icon("export",16));btnViewSales.setGraphic(IconFactory.icon("view",15));btnViewPurchases.setGraphic(IconFactory.icon("view",15));miExcel.setGraphic(IconFactory.icon("excel",15));miPdf.setGraphic(IconFactory.icon("pdf",15));miCsv.setGraphic(IconFactory.icon("document",15));
        reportSalesIcon.getChildren().setAll(IconFactory.icon("sales",22));reportPurchaseIcon.getChildren().setAll(IconFactory.icon("purchase",22));reportProfitIcon.getChildren().setAll(IconFactory.icon("chart",22));reportReceivableIcon.getChildren().setAll(IconFactory.icon("payment",22));reportStockIcon.getChildren().setAll(IconFactory.icon("inventory",22));reportCustomerIcon.getChildren().setAll(IconFactory.icon("customer",22));
    }
    private void configureStatusCells(){statusCell(colSaleStatus);statusCell(colPurchaseStatus);}
    private void statusCell(TableColumn<String[],String> column){column.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String value,boolean empty){super.updateItem(value,empty);getStyleClass().removeAll("report-status-paid","report-status-pending","report-status-other");if(empty||value==null){setText(null);return;}setText(value);String v=value.toUpperCase(Locale.ROOT);getStyleClass().add(v.contains("PAID")||v.contains("COMPLETED")?"report-status-paid":v.contains("PENDING")?"report-status-pending":"report-status-other");}});}
    @Override public void onScreenShown(boolean reusedFromCache){ applyPendingTab(); loadFiltersAsync(); loadSavedReports(); if(!loadRequested&&(!loaded||ScreenRefreshPolicy.shouldRefresh("reports", ScreenRefreshPolicy.Mode.WHEN_STALE)))requestRefresh(); }
    @Override public void onScreenHidden(){ loadRequested=false; UiTaskExecutor.cancelPrefix("reports-"); }

    private record FilterData(List<String> parties,List<String> items,List<String> salespeople){}
    private record Point(String label,double value){}
    private record ReportData(double sales,double purchase,double profit,double receivables,double stock,long low,long customers,List<Point> customerPoints,List<Point> itemPoints,List<String[]> salesRows,List<String[]> purchaseRows,double salesPaid,double payables,double purchasesPaid,long items,long out,long salesCount,long purchaseCount,double averageSale){}
    private record SavedReportRow(String name,String baseReport,String datePreset,String grouping,String sorting,String columns,ReportingSavedConfig savedConfig,ReportRequest request){}
}
