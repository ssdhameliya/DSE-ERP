package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.api.reporting.ReportingApiClient;
import org.example.api.reporting.ReportingApiClient.*;
import org.example.api.support.SupportApiClient;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.ReportingSavedConfig;
import org.example.service.SessionService;
import org.example.service.UnifiedReportExportService;
import org.example.util.*;

import java.io.File;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Unified 9.0.46 report viewer used by every Report Center definition. */
public class ReportViewerController implements ScreenLifecycle {
    private static final String LOAD_KEY="report-viewer-load";
    @FXML private Label lblTitle,lblDescription,lblActiveFilters,lblRecords,lblPage;
    @FXML private StackPane viewerPageIcon,reportSearchIcon;
    @FXML private DatePicker dpFrom,dpTo;
    @FXML private ComboBox<String> cmbDatePreset,cmbParty,cmbItem,cmbSalesperson,cmbDocumentStatus,cmbPaymentStatus,cmbReturnStatus,cmbGstRate,cmbWarehouse,cmbBankStatus,cmbGroup,cmbSort,cmbDirection;
    @FXML private ComboBox<Integer> cmbRows;
    @FXML private TextField txtMinAmount,txtMaxAmount,txtSearch;
    @FXML private Button btnApply,btnCollapseFilters,btnFirst,btnPrev,btnNext,btnLast;
    @FXML private VBox filterPanel;
    @FXML private VBox boxPeriod,boxFrom,boxTo,boxParty,boxItem,boxSalesperson,boxDocumentStatus,boxPaymentStatus,boxReturnStatus,boxGstRate,boxWarehouse,boxBankStatus,boxMinAmount,boxMaxAmount;
    @FXML private GridPane filterGrid;
    @FXML private MenuButton btnColumns;
    @FXML private TableView<ReportRow> tblReport;

    private final ReportingApiClient api=new ReportingApiClient();
    private final SupportApiClient supportApi=new SupportApiClient();
    private String reportId="SALES_REGISTER";
    private ReportResult current;
    private ReportRequest pendingSavedRequest;
    private String pendingDatePreset;
    private final LinkedHashSet<String> visibleKeys=new LinkedHashSet<>();
    private final Map<String,String> sortLabelToKey=new LinkedHashMap<>();
    private int page;
    private boolean filtersCollapsed;
    private boolean rendering;
    private boolean initializing=true;
    private ReportDefinition definition;

    @FXML public void initialize(){
        cmbDatePreset.getItems().setAll("Today","Yesterday","This Week","Last Week","This Month","Last Month","This Quarter","Last Quarter","This Financial Year","Last Financial Year","Last 7 Days","Last 30 Days","Custom");
        cmbDatePreset.setValue("This Month");
        cmbRows.getItems().setAll(25,50,100,250);cmbRows.setValue(25);
        cmbDirection.getItems().setAll("Descending","Ascending");cmbDirection.setValue("Descending");
        applyDatePreset("This Month");
        cmbDatePreset.valueProperty().addListener((o,a,b)->{if(!initializing&&b!=null&&!"Custom".equals(b)){applyDatePreset(b);page=0;}});
        cmbRows.valueProperty().addListener((o,a,b)->{if(!initializing&&b!=null){page=0;requestLoad();}});
        cmbGroup.valueProperty().addListener((o,a,b)->{if(!initializing&&!rendering&&current!=null){page=0;requestLoad();}});
        cmbSort.valueProperty().addListener((o,a,b)->{if(!initializing&&!rendering&&current!=null){page=0;requestLoad();}});
        cmbDirection.valueProperty().addListener((o,a,b)->{if(!initializing&&!rendering&&current!=null){page=0;requestLoad();}});
        txtSearch.setOnAction(e->applyFilters());
        RegisterUiSupport.configureHeaderSearch(txtSearch,reportSearchIcon,"Search invoice / party / item / reference...");
        filterGrid.widthProperty().addListener((o,a,b)->reflowFilterGrid());
        tblReport.setPlaceholder(new Label("No transactions found for the selected criteria."));
        updateReportIdentityIcon();
        loadFilterOptions();
        consumeContext();
        loadDefinition();
        initializing=false;
        requestLoad();
    }


    private void updateReportIdentityIcon(){
        if(viewerPageIcon==null)return;String id=safe(reportId).toUpperCase(Locale.ROOT);String semantic=switch(id){
            case "SALES_REGISTER" -> "invoice";
            case "SALES_BY_CUSTOMER" -> "customer";
            case "SALES_BY_ITEM","ITEM_LEDGER" -> "item";
            case "PURCHASE_REGISTER" -> "purchase";
            case "RETURNS_ANALYSIS" -> "return";
            case "GST_TAX" -> "tax";
            case "RECEIVABLE_AGEING","PAYABLE_AGEING" -> "payment";
            case "STOCK_SUMMARY" -> "inventory";
            case "BANK_RECONCILIATION" -> "bank";
            default -> "report";
        };viewerPageIcon.getChildren().setAll(IconFactory.icon(semantic,24));
    }

    private void consumeContext(){
        ReportViewContext.Selection s=ReportViewContext.consume();if(s==null)return;
        String nextReport=(s.reportId()==null||s.reportId().isBlank())?reportId:s.reportId();
        if(!nextReport.equalsIgnoreCase(reportId)){
            reportId=nextReport; current=null; visibleKeys.clear(); sortLabelToKey.clear(); page=0;
        }else reportId=nextReport;
        pendingSavedRequest=s.request();pendingDatePreset=s.datePreset();
        if(pendingSavedRequest!=null)applySavedRequestToControls(pendingSavedRequest,pendingDatePreset);
        else{
            if(s.from()!=null&&!s.from().isBlank())try{dpFrom.setValue(LocalDate.parse(s.from()));}catch(Exception ignored){}
            if(s.to()!=null&&!s.to().isBlank())try{dpTo.setValue(LocalDate.parse(s.to()));}catch(Exception ignored){}
            if(s.groupBy()!=null&&!s.groupBy().isBlank())cmbGroup.setValue(titleCase(s.groupBy()));
        }
    }


    private void loadDefinition(){
        UiTaskExecutor.submitLatest("report-viewer-definition",api::definitions,defs->{
            definition=defs==null?null:defs.stream().filter(d->d.id()!=null&&d.id().equalsIgnoreCase(reportId)).findFirst().orElse(null);
            updateReportIdentityIcon();configureFilterVisibility();
        },e->PerformanceMonitor.event("report-viewer-definition",String.valueOf(e.getMessage())));
    }

    private void configureFilterVisibility(){
        Set<String> supported=new LinkedHashSet<>();
        if(definition!=null&&definition.supportedFilters()!=null)for(String f:definition.supportedFilters())if(f!=null)supported.add(f.trim().toUpperCase(Locale.ROOT));
        boolean fallback=supported.isEmpty();
        boolean period=fallback||supported.contains("PERIOD");
        showBox(boxPeriod,period);showBox(boxFrom,period);showBox(boxTo,period);
        showBox(boxParty,fallback||supported.contains("PARTY"));
        showBox(boxItem,fallback||supported.contains("ITEM"));
        showBox(boxSalesperson,fallback||supported.contains("SALESPERSON"));
        showBox(boxDocumentStatus,fallback||supported.contains("DOCUMENT STATUS"));
        showBox(boxPaymentStatus,fallback||supported.contains("PAYMENT STATUS"));
        showBox(boxReturnStatus,fallback||supported.contains("RETURN STATUS"));
        showBox(boxGstRate,fallback||supported.contains("GST RATE"));
        showBox(boxWarehouse,fallback||supported.contains("WAREHOUSE"));
        showBox(boxBankStatus,fallback||supported.contains("BANK STATUS"));
        boolean amount=fallback||supported.contains("AMOUNT");showBox(boxMinAmount,amount);showBox(boxMaxAmount,amount);
        reflowFilterGrid();
    }
    private void showBox(VBox box,boolean show){if(box!=null){box.setVisible(show);box.setManaged(show);}}

    private void reflowFilterGrid(){
        if(filterGrid==null)return;
        List<VBox> boxes=List.of(boxPeriod,boxParty,boxItem,boxSalesperson,boxDocumentStatus,boxPaymentStatus,boxFrom,boxTo,boxReturnStatus,boxGstRate,boxWarehouse,boxBankStatus,boxMinAmount,boxMaxAmount);
        List<VBox> visible=boxes.stream().filter(Objects::nonNull).filter(Node::isManaged).toList();
        if(visible.isEmpty())return;
        double width=filterGrid.getWidth();
        int columns=width>0?Math.max(1,Math.min(5,(int)Math.floor(width/205.0))):4;
        filterGrid.getColumnConstraints().clear();
        for(int i=0;i<columns;i++){ColumnConstraints c=new ColumnConstraints();c.setPercentWidth(100.0/columns);c.setHgrow(Priority.ALWAYS);c.setFillWidth(true);filterGrid.getColumnConstraints().add(c);}
        for(int i=0;i<visible.size();i++){VBox box=visible.get(i);GridPane.setColumnIndex(box,i%columns);GridPane.setRowIndex(box,i/columns);GridPane.setHgrow(box,Priority.ALWAYS);}
    }

    private void loadFilterOptions(){
        UiTaskExecutor.submitLatest("report-viewer-filters",api::filters,this::applyFilterOptions,e->PerformanceMonitor.event("report-viewer-filters",String.valueOf(e.getMessage())));
    }
    private void applyFilterOptions(ReportFilters f){
        setOptions(cmbParty,"All Parties",f.parties());setOptions(cmbItem,"All Items",f.items());setOptions(cmbSalesperson,"All Salespersons",f.salespeople());
        setOptions(cmbDocumentStatus,"All Document Statuses",f.documentStatuses());setOptions(cmbPaymentStatus,"All Payment Statuses",f.paymentStatuses());setOptions(cmbReturnStatus,"All Return Statuses",f.returnStatuses());setOptions(cmbGstRate,"All GST Rates",f.gstRates());setOptions(cmbWarehouse,"All Warehouses",f.warehouses());setOptions(cmbBankStatus,"All Bank Statuses",f.bankStatuses());
        if(pendingSavedRequest!=null)applySavedRequestToControls(pendingSavedRequest,pendingDatePreset);
    }
    private <T> void setOptions(ComboBox<T> box,T all,List<T> values){T selected=box.getValue();box.getItems().setAll(all);if(values!=null)for(T v:values)if(v!=null&&!box.getItems().contains(v))box.getItems().add(v);if(selected!=null&&box.getItems().contains(selected))box.setValue(selected);else box.getSelectionModel().selectFirst();}

    @FXML private void applyFilters(){page=0;requestLoad();}
    @FXML private void resetFilters(){
        initializing=true;cmbDatePreset.setValue("This Month");applyDatePreset("This Month");first(cmbParty);first(cmbItem);first(cmbSalesperson);first(cmbDocumentStatus);first(cmbPaymentStatus);first(cmbReturnStatus);first(cmbGstRate);first(cmbWarehouse);first(cmbBankStatus);txtMinAmount.clear();txtMaxAmount.clear();txtSearch.clear();cmbGroup.getSelectionModel().selectFirst();cmbSort.getSelectionModel().selectFirst();cmbDirection.setValue("Descending");page=0;initializing=false;requestLoad();
    }
    private void first(ComboBox<?> c){if(c!=null&&!c.getItems().isEmpty())c.getSelectionModel().selectFirst();}

    private void requestLoad(){
        if(dpFrom.getValue()==null||dpTo.getValue()==null||dpFrom.getValue().isAfter(dpTo.getValue())){error("Choose a valid reporting date range.");return;}
        ReportRequest request;
        try{request=requestFor(page,cmbRows.getValue()==null?25:cmbRows.getValue());}catch(IllegalArgumentException ex){error(ex.getMessage());return;}
        setBusy(true);
        UiTaskExecutor.submitLatest(LOAD_KEY,()->api.run(request),this::applyResult,e->{setBusy(false);error("Could not run report: "+root(e));});
    }

    private ReportRequest requestFor(int requestedPage,int size){
        return new ReportRequest(reportId,dpFrom.getValue().toString(),dpTo.getValue().toString(),selected(cmbParty),selected(cmbItem),selected(cmbSalesperson),selectedUpper(cmbDocumentStatus),selectedUpper(cmbPaymentStatus),selectedUpper(cmbReturnStatus),selected(cmbGstRate),selected(cmbWarehouse),selectedUpper(cmbBankStatus),text(txtSearch),selected(cmbGroup),sortKey(),"Ascending".equals(cmbDirection.getValue())?"ASC":"DESC",number(txtMinAmount),number(txtMaxAmount),requestedPage,size,new ArrayList<>(visibleKeys));
    }

    private void applyResult(ReportResult result){
        rendering=true;
        current=result;page=result.page();lblTitle.setText(result.title());lblDescription.setText(result.description());updateReportIdentityIcon();
        if(visibleKeys.isEmpty())for(ReportColumn c:result.columns())if(c.defaultVisible())visibleKeys.add(c.key());
        configureColumns(result);configureGroups(result);configureSort(result);renderFilters(result);tblReport.getItems().setAll(result.rows());
        lblRecords.setText("Showing "+(result.rows().isEmpty()?0:(page*result.size()+1))+" to "+Math.min(result.totalRows(),(long)(page*result.size()+result.rows().size()))+" of "+result.totalRows()+" entries");
        lblPage.setText((result.page()+1)+" / "+Math.max(1,result.totalPages()));boolean first=result.page()<=0,last=result.page()>=result.totalPages()-1;btnFirst.setDisable(first);btnPrev.setDisable(first);btnNext.setDisable(last);btnLast.setDisable(last);setBusy(false);rendering=false;pendingSavedRequest=null;
    }

    private void configureColumns(ReportResult result){
        tblReport.getColumns().clear();btnColumns.getItems().clear();
        for(int index=0;index<result.columns().size();index++){
            ReportColumn meta=result.columns().get(index);CheckMenuItem item=new CheckMenuItem(meta.label());item.setSelected(visibleKeys.contains(meta.key()));item.selectedProperty().addListener((o,a,b)->{if(b)visibleKeys.add(meta.key());else visibleKeys.remove(meta.key());configureColumns(current);});btnColumns.getItems().add(item);
            if(!visibleKeys.contains(meta.key()))continue;
            final int p=index;TableColumn<ReportRow,String> col=new TableColumn<>(meta.label());col.setSortable(false);col.setCellValueFactory(v->new SimpleStringProperty(value(v.getValue(),p)));
            IconFactory.applyTableHeaderIcon(col,semanticForColumn(meta.key()));
            col.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v,boolean empty){super.updateItem(v,empty);if(empty||v==null){setText(null);getStyleClass().remove("report-number-cell");return;}setText(display(meta,v));if(meta.numeric()&&!getStyleClass().contains("report-number-cell"))getStyleClass().add("report-number-cell");setStyle(meta.numeric()?"-fx-alignment:CENTER-RIGHT;":"");}});
            tblReport.getColumns().add(col);
        }
    }
    private void configureGroups(ReportResult r){String selected=cmbGroup.getValue();List<String> values=new ArrayList<>();values.add("None");if(r.groupByOptions()!=null)for(String v:r.groupByOptions())if(v!=null&&!"NONE".equalsIgnoreCase(v)&&!values.contains(v))values.add(v);cmbGroup.getItems().setAll(values);if(selected!=null&&values.stream().anyMatch(x->x.equalsIgnoreCase(selected)))cmbGroup.setValue(values.stream().filter(x->x.equalsIgnoreCase(selected)).findFirst().orElse("None"));else cmbGroup.getSelectionModel().selectFirst();}
    private void configureSort(ReportResult r){String selected=cmbSort.getValue();String savedKey=pendingSavedRequest==null?null:pendingSavedRequest.sortKey();sortLabelToKey.clear();for(ReportColumn c:r.columns()){sortLabelToKey.put(c.label(),c.key());}cmbSort.getItems().setAll(sortLabelToKey.keySet());if(savedKey!=null&&!savedKey.isBlank()){String savedLabel=sortLabelToKey.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(savedKey)).map(Map.Entry::getKey).findFirst().orElse(null);if(savedLabel!=null){cmbSort.setValue(savedLabel);return;}}if(selected!=null&&sortLabelToKey.containsKey(selected))cmbSort.setValue(selected);else{String preferred=r.columns().stream().filter(c->"date".equals(c.key())).map(ReportColumn::label).findFirst().orElse(r.columns().isEmpty()?null:r.columns().getFirst().label());if(preferred!=null)cmbSort.setValue(preferred);}}
    private String semanticForColumn(String key){String k=safe(key).toLowerCase(Locale.ROOT);if(k.contains("date")||k.contains("month"))return "calendar";if(k.contains("customer")||k.contains("party"))return "customer";if(k.contains("supplier"))return "supplier";if(k.contains("item")||k.contains("quantity")||k.contains("qty"))return "item";if(k.contains("gst")||k.contains("tax"))return "tax";if(k.contains("status"))return "status";if(k.contains("amount")||k.contains("total")||k.contains("paid")||k.contains("outstanding")||k.contains("profit")||k.contains("cost")||k.contains("value"))return "currency";if(k.contains("invoice")||k.contains("reference")||k.contains("document"))return "document";if(k.contains("bank"))return "bank";return "report";}
    private void renderFilters(ReportResult r){StringJoiner j=new StringJoiner("  •  ");for(var e:r.appliedFilters().entrySet())j.add(e.getKey()+": "+e.getValue());lblActiveFilters.setText("Active filters: "+j);}

    @FXML private void firstPage(){if(page!=0){page=0;requestLoad();}}
    @FXML private void previousPage(){if(page>0){page--;requestLoad();}}
    @FXML private void nextPage(){if(current!=null&&page<current.totalPages()-1){page++;requestLoad();}}
    @FXML private void lastPage(){if(current!=null&&current.totalPages()>0){page=current.totalPages()-1;requestLoad();}}

    @FXML private void toggleFilters(){filtersCollapsed=!filtersCollapsed;filterGrid.setVisible(!filtersCollapsed);filterGrid.setManaged(!filtersCollapsed);btnCollapseFilters.setText(filtersCollapsed?"Expand ▼":"Collapse ▲");}
    @FXML private void refreshReport(){requestLoad();}
    @FXML private void goDashboard(){ReportsController.requestTab(0);NavigationManager.navigateOrReport("/fxml/pages/Reports.fxml");}
    @FXML private void goReportCenter(){ReportsController.requestTab(1);NavigationManager.navigateOrReport("/fxml/pages/Reports.fxml");}
    @FXML private void goSavedReports(){ReportsController.requestTab(2);NavigationManager.navigateOrReport("/fxml/pages/Reports.fxml");}
    @FXML private void goScheduled(){ReportsController.requestTab(3);NavigationManager.navigateOrReport("/fxml/pages/Reports.fxml");}
    @FXML private void showInformation(){String formula=switch(reportId){case "SALES_REGISTER","SALES_BY_CUSTOMER","SALES_BY_ITEM"->"Gross Sales are original approved invoice values. Approved Sales Returns are deducted only from Net Sales. Original invoice Payment Status is never replaced by Return refund status.";case "PURCHASE_REGISTER"->"Gross Purchases are posted approved purchase values. Approved Purchase Returns are deducted from Net Purchases. Supplier payment and Return refund settlement remain separate.";case "RECEIVABLE_AGEING","PAYABLE_AGEING"->"Outstanding is original document value minus posted payments. Return refund obligations are reported in the Return lifecycle and are not rewritten into historical payment rows.";case "PROFITABILITY"->"Profit = net taxable sales after approved Returns minus historical unit-cost COGS after the corresponding returned quantities.";default->"This report is calculated by the server-owned 9.0.46 reporting engine. UI, PDF, Excel, CSV and Print consume the same ReportResult.";};Alert a=new OwnedAlert(Alert.AlertType.INFORMATION,formula);a.setHeaderText(lblTitle.getText()+" — calculation rules");a.showAndWait();}

    @FXML private void saveReport(){
        if(current==null)return;TextInputDialog d=new OwnedTextInputDialog();d.setHeaderText("Save Report");d.setContentText("Name:");d.showAndWait().map(String::trim).filter(x->!x.isBlank()).ifPresent(name->{ReportRequest req=requestFor(0,cmbRows.getValue()==null?25:cmbRows.getValue());String payload=ReportingSavedConfig.encode(name,cmbDatePreset.getValue(),req);Integer uid=SessionService.current()==null?null:SessionService.current().getId();UiTaskExecutor.submitAction("save-unified-report",()->{supportApi.saveView(uid,"REPORT_CENTER",name,payload);return true;},x->ToastManager.success(tblReport,"Saved","Report configuration saved with filters, grouping, sorting, columns and date mode."),e->error("Could not save report: "+root(e)));});
    }

    @FXML private void exportPdf(){export("PDF Report","pdf");}
    @FXML private void exportExcel(){export("Excel Report","xlsx");}
    @FXML private void exportCsv(){export("CSV Report","csv");}
    @FXML private void printReport(){if(current==null)return;setBusy(true);UiTaskExecutor.submitAction("print-unified-report",()->{ReportResult all=loadAllForExport();return UnifiedReportExportService.print(all,visibleKeys);},p->{setBusy(false);ToastManager.success(tblReport,"Print","Report sent to the system PDF print handler.");},e->{setBusy(false);error("Could not print report: "+root(e));});}

    private void export(String title,String ext){
        if(current==null)return;FileChooser chooser=new FileChooser();chooser.setTitle(title);chooser.setInitialFileName(fileBase()+"."+ext);chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(title,"*."+ext));File file=chooser.showSaveDialog(tblReport.getScene().getWindow());if(file==null)return;Path target=file.toPath();if(!target.toString().toLowerCase(Locale.ROOT).endsWith("."+ext))target=Path.of(target+"."+ext);Path finalTarget=target;setBusy(true);
        UiTaskExecutor.submitAction("export-unified-report-"+ext,()->{ReportResult all=loadAllForExport();switch(ext){case "pdf"->UnifiedReportExportService.pdf(finalTarget,all,visibleKeys,true,true);case "xlsx"->UnifiedReportExportService.excel(finalTarget,all,visibleKeys);case "csv"->UnifiedReportExportService.csv(finalTarget,all,visibleKeys);default->throw new IllegalArgumentException("Unsupported export format");}return finalTarget;},p->{setBusy(false);ToastManager.success(tblReport,"Export complete","Created: "+p);},e->{setBusy(false);error("Could not export report: "+root(e));});
    }
    private ReportResult loadAllForExport(){ReportRequest first=requestFor(0,250);ReportResult result=api.run(first);if(result.totalPages()<=1)return result;List<ReportRow> all=new ArrayList<>(result.rows());for(int p=1;p<result.totalPages();p++){ReportRequest next=new ReportRequest(first.reportId(),first.from(),first.to(),first.party(),first.item(),first.salesperson(),first.documentStatus(),first.paymentStatus(),first.returnStatus(),first.gstRate(),first.warehouse(),first.bankStatus(),first.search(),first.groupBy(),first.sortKey(),first.sortDirection(),first.minAmount(),first.maxAmount(),p,250,first.visibleColumns());all.addAll(api.run(next).rows());}return new ReportResult(result.reportId(),result.title(),result.description(),result.periodFrom(),result.periodTo(),result.metrics(),result.columns(),all,result.totalRows(),0,all.size(),1,result.groupByOptions(),result.appliedFilters(),result.totals(),result.generatedAt(),result.generatedBy());}

    private void applySavedRequestToControls(ReportRequest q,String datePreset){
        if(q==null)return;reportId=q.reportId()==null||q.reportId().isBlank()?reportId:q.reportId();initializing=true;String preset=datePreset==null||datePreset.isBlank()?"Custom":datePreset;if(cmbDatePreset.getItems().contains(preset)){cmbDatePreset.setValue(preset);if(!"Custom".equals(preset))applyDatePreset(preset);else{setDate(dpFrom,q.from());setDate(dpTo,q.to());}}else{cmbDatePreset.setValue("Custom");setDate(dpFrom,q.from());setDate(dpTo,q.to());}
        setIfPresent(cmbParty,q.party());setIfPresent(cmbItem,q.item());setIfPresent(cmbSalesperson,q.salesperson());setIfPresent(cmbDocumentStatus,q.documentStatus());setIfPresent(cmbPaymentStatus,q.paymentStatus());setIfPresent(cmbReturnStatus,q.returnStatus());setIfPresent(cmbGstRate,q.gstRate());setIfPresent(cmbWarehouse,q.warehouse());setIfPresent(cmbBankStatus,q.bankStatus());txtSearch.setText(safe(q.search()));txtMinAmount.setText(q.minAmount()==null?"":String.valueOf(q.minAmount()));txtMaxAmount.setText(q.maxAmount()==null?"":String.valueOf(q.maxAmount()));if(q.visibleColumns()!=null&&!q.visibleColumns().isEmpty()){visibleKeys.clear();visibleKeys.addAll(q.visibleColumns());}if(q.groupBy()!=null&&!q.groupBy().isBlank())cmbGroup.setValue(titleCase(q.groupBy()));if(q.sortDirection()!=null)cmbDirection.setValue("ASC".equalsIgnoreCase(q.sortDirection())?"Ascending":"Descending");if(q.size()!=null&&cmbRows.getItems().contains(q.size()))cmbRows.setValue(q.size());page=0;initializing=false;
    }
    private void setIfPresent(ComboBox<String> box,String value){if(box==null||value==null||value.isBlank())return;String match=box.getItems().stream().filter(x->x!=null&&x.equalsIgnoreCase(value)).findFirst().orElse(null);if(match==null){box.getItems().add(value);match=value;}box.setValue(match);}
    private void setDate(DatePicker picker,String value){try{if(value!=null&&!value.isBlank())picker.setValue(LocalDate.parse(value));}catch(Exception ignored){}}

    private void applyDatePreset(String preset){
        LocalDate today=BusinessClock.today(),from=today,to=today;switch(preset){
            case "Yesterday"->{from=today.minusDays(1);to=from;}
            case "This Week"->{from=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
            case "Last Week"->{to=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);from=to.minusDays(6);}
            case "This Month"->{from=today.withDayOfMonth(1);}
            case "Last Month"->{LocalDate p=today.minusMonths(1);from=p.withDayOfMonth(1);to=p.withDayOfMonth(p.lengthOfMonth());}
            case "This Quarter"->{int m=((today.getMonthValue()-1)/3)*3+1;from=LocalDate.of(today.getYear(),m,1);}
            case "Last Quarter"->{LocalDate p=today.minusMonths(3);int m=((p.getMonthValue()-1)/3)*3+1;from=LocalDate.of(p.getYear(),m,1);to=from.plusMonths(3).minusDays(1);}
            case "This Financial Year"->{int y=today.getMonthValue()>=4?today.getYear():today.getYear()-1;from=LocalDate.of(y,4,1);to=LocalDate.of(y+1,3,31);}
            case "Last Financial Year"->{int y=today.getMonthValue()>=4?today.getYear()-1:today.getYear()-2;from=LocalDate.of(y,4,1);to=LocalDate.of(y+1,3,31);}
            case "Last 7 Days"->{from=today.minusDays(6);}
            case "Last 30 Days"->{from=today.minusDays(29);}
            default->{ }
        }dpFrom.setValue(from);dpTo.setValue(to);
    }

    @FXML private void viewSelectedSource(){ ReportRow row=tblReport==null?null:tblReport.getSelectionModel().getSelectedItem(); if(row!=null)drillDown(row); }

    private void drillDown(ReportRow row){if(row==null||row.targetFxml()==null||row.targetFxml().isBlank())return;String ref=row.referenceNo();if(row.targetFxml().contains("SalesList")&&ref!=null)SalesScreenContext.select(ref);if(row.targetFxml().contains("PurchaseList")&&ref!=null)PurchaseScreenContext.select(ref);NavigationManager.navigateOrReport(row.targetFxml());}

    private void setBusy(boolean busy){btnApply.setDisable(busy);tblReport.setDisable(busy);btnColumns.setDisable(busy);}
    private String sortKey(){if(pendingSavedRequest!=null&&pendingSavedRequest.sortKey()!=null&&!pendingSavedRequest.sortKey().isBlank())return pendingSavedRequest.sortKey();String label=cmbSort.getValue();return label==null?"":sortLabelToKey.getOrDefault(label,label);}
    private static String selected(ComboBox<String> box){String v=box==null?null:box.getValue();if(v==null||v.isBlank()||v.toUpperCase(Locale.ROOT).startsWith("ALL ")||"NONE".equalsIgnoreCase(v))return "";return v.trim();}
    private static String selectedUpper(ComboBox<String> box){return selected(box).toUpperCase(Locale.ROOT);}
    private static String text(TextField f){return f==null||f.getText()==null?"":f.getText().trim();}
    private static Double number(TextField f){String v=text(f);if(v.isBlank())return null;try{return Double.valueOf(v.replace(",","").replace("₹","").trim());}catch(Exception e){throw new IllegalArgumentException("Amount filters must contain valid numbers.");}}
    private static String value(ReportRow row,int p){return row==null||row.values()==null||p<0||p>=row.values().size()?"":safe(row.values().get(p));}
    private static String display(ReportColumn c,String raw){if(raw==null)return "";if("DATE".equals(c.type()))return BusinessClock.formatDate(raw);if(!c.numeric())return raw;try{double v=Double.parseDouble(raw);if("MONEY".equals(c.type()))return money(v);if("PERCENT".equals(c.type()))return String.format("%,.2f%%",v);return strip(v);}catch(Exception e){return raw;}}
    private static String metric(ReportMetric m){if("MONEY".equals(m.format()))return money(m.value());if("PERCENT".equals(m.format()))return String.format("%,.2f%%",m.value());if("COUNT".equals(m.format()))return String.format("%,.0f",m.value());return strip(m.value());}
    private static String money(double value){return "₹ "+String.format(Locale.of("en", "IN"), "%,.2f", value);}
    private static String strip(double v){String s=String.format(Locale.ROOT,"%.4f",v);return s.replaceAll("\\.?0+$","");}
    private static String safe(String s){return s==null?"":s;}
    private static String root(Throwable e){Throwable x=e;while(x.getCause()!=null&&x.getCause()!=x)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    private static String titleCase(String v){if(v==null||v.isBlank())return "None";StringBuilder b=new StringBuilder();for(String p:v.toLowerCase(Locale.ROOT).split("[ _]+")){if(!b.isEmpty())b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}
    private String fileBase(){return (current==null?reportId:current.title()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","")+"-"+dpFrom.getValue()+"-to-"+dpTo.getValue();}
    private void error(String message){Alert a=new OwnedAlert(Alert.AlertType.ERROR,message);a.setHeaderText("Reporting error");a.showAndWait();}

    @Override public void onScreenShown(boolean reusedFromCache){if(reusedFromCache){consumeContext();loadDefinition();loadFilterOptions();requestLoad();}}
    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("report-viewer-");}
}
