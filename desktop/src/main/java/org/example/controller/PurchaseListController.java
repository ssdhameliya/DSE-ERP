package org.example.controller;

import org.example.util.BusinessClock;

import org.example.api.support.SupportApiClient;
import org.example.util.OwnedAlert;
import org.example.util.OwnedTextInputDialog;
import java.util.List;
import org.example.util.IconFactory;
import org.example.util.TableSelectionSupport;
import org.example.util.SemanticTableCells;
import org.example.util.UiActionIcons;
import org.example.util.ScreenRefreshPolicy;
import org.example.navigation.ScreenLifecycle;
import org.example.util.FxDebouncer;
import org.example.util.UiTaskExecutor;
import org.example.util.PerformanceMonitor;
import org.example.util.InvoicePaymentDetailsDialog;
import org.example.documentstudio.service.ExcelOutputService;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import com.itextpdf.layout.element.Table;
import com.itextpdf.kernel.pdf.*;import com.itextpdf.layout.Document;import com.itextpdf.layout.element.*;import javafx.beans.property.*;import javafx.collections.FXCollections;import javafx.fxml.*;import javafx.geometry.Pos;import javafx.scene.Parent;import javafx.scene.control.*;import javafx.scene.layout.*;import javafx.stage.FileChooser;import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.example.model.Purchase;import org.example.navigation.NavigationManager;import org.example.service.*;import java.io.*;import java.nio.file.Path;import java.text.NumberFormat;import java.time.LocalDate;import java.util.*;
public class PurchaseListController implements ScreenLifecycle{
 public PurchaseListController(){}
 @FXML private Label lblTotal,lblTotalCount,lblToday,lblTodayCount,lblPending,lblPendingCount,lblOverdue,lblOverdueCount,lblPaid,lblSummary,lblPageInfo,lblPageNumber,lblDetailInvoice,lblDetailSupplier,lblDetailContact,lblDetailAmount,lblDetailPaid,lblDetailBalance,lblDetailDue,lblDetailGst,lblDetailReference,lblDetailPaymentTerms,lblDetailContactPerson,lblDetailPhone,lblDetailEmail,lblDetailGstin,lblDetailCharges,lblDetailChargeTax,lblDetailGstType,lblDetailTransporter,lblDetailVehicle,lblDetailNotes,lblDetailAttachment,lblDetailBillingAddress,lblDetailDeliveryAddress;@FXML private Button btnNewPurchase,btnReset,btnRefresh,btnFirstPage,btnPreviousPage,btnNextPage,btnLastPage,btnExportExcel,btnExportPdf,btnTodayRange,btnYesterdayRange,btnSevenDaysRange,btnThirtyDaysRange,btnCustomRange;@FXML private TextField txtSearch;@FXML private ComboBox<String>cmbSupplier,cmbPaymentStatus,cmbMailStatus;@FXML private ComboBox<Integer>cmbPageSize;@FXML private DatePicker dpFrom,dpTo;@FXML private ToggleButton btnAdvanced;@FXML private GridPane advancedFilters;@FXML private TableView<Purchase>tablePurchase;@FXML private TableColumn<Purchase,String>colInvoice,colDate,colSupplier,colMobile,colDue,colStatus,colMail;@FXML private TableColumn<Purchase,Double>colAmount,colPaid,colBalance;@FXML private TableColumn<Purchase,Void>colActions;@FXML private SplitPane mainSplit;@FXML private VBox detailDrawer;@FXML private Button btnCloseDetails;@FXML private StackPane purchasePageIcon,purchaseTotalIcon,purchaseOrdersIcon,purchaseSuppliersIcon,purchaseItemsIcon,purchasePaidIcon;
 private final PurchaseService service=new PurchaseService();private final SupportApiClient support=new SupportApiClient();private final FxDebouncer searchDebouncer=new FxDebouncer(java.time.Duration.ofMillis(220));private final NumberFormat money=NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));private List<Purchase>all=List.of(),filtered=List.of();private Purchase selected;private int currentPage;
 @FXML public void initialize(){
        if(purchasePageIcon!=null)purchasePageIcon.getChildren().setAll(IconFactory.icon("purchase",24));installKpiIcons();configureExplicitTableHeaderIcons();applyRangeButtonIcons();configureExplicitButtonIcons();configureDetailsCloseButton();decorateDetailDrawer();colInvoice.setCellValueFactory(v->new SimpleStringProperty(v.getValue().getInvoiceNo()));colDate.setCellValueFactory(v->new SimpleStringProperty(BusinessClock.formatDate(v.getValue().getInvoiceDate())));colSupplier.setCellValueFactory(v->new SimpleStringProperty(v.getValue().getSupplier().getName()));colMobile.setCellValueFactory(v->new SimpleStringProperty(s(v.getValue().getSupplier().getPhone())));colAmount.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getTotalAmount()).asObject());colPaid.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getPaidAmount()).asObject());colBalance.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getBalanceAmount()).asObject());colDue.setCellValueFactory(v->new SimpleStringProperty(due(v.getValue())));colStatus.setCellValueFactory(v->new SimpleStringProperty(v.getValue().getPaymentStatus()));colMail.setCellValueFactory(v->new SimpleStringProperty(v.getValue().isEmailSent()?"Sent":"Not Sent"));colAmount.setCellFactory(x->totalMoneyCell());colPaid.setCellFactory(x->paidMoneyCell());colBalance.setCellFactory(x->balanceMoneyCell());colStatus.setCellFactory(x->pill());colMail.setCellFactory(x->pill());setupActions();cmbPaymentStatus.getItems().setAll("All","PENDING","PARTIAL","PAID","OVERDUE");cmbPaymentStatus.setValue("All");cmbMailStatus.getItems().setAll("All","Sent","Not Sent");cmbMailStatus.setValue("All");cmbSupplier.getItems().setAll("All Suppliers");cmbSupplier.setValue("All Suppliers");cmbPageSize.getItems().setAll(10,25,50,100);cmbPageSize.setValue(25);cmbPageSize.valueProperty().addListener((o,a,b)->{currentPage=0;renderPage();});dpFrom.setValue(BusinessClock.today().minusMonths(6));dpTo.setValue(BusinessClock.today());txtSearch.textProperty().addListener((o,a,b)->searchDebouncer.submit(this::filter));cmbSupplier.valueProperty().addListener((o,a,b)->filter());cmbPaymentStatus.valueProperty().addListener((o,a,b)->filter());cmbMailStatus.valueProperty().addListener((o,a,b)->filter());detailDrawer.setManaged(false);detailDrawer.setVisible(false);mainSplit.setDividerPositions(1);refresh();configureModernTable();installRegisterTools();}


 private void decorateDetailDrawer(){
  if(detailDrawer==null)return;
  drawerValue(lblDetailInvoice,"document");drawerValue(lblDetailSupplier,"supplier");drawerValue(lblDetailContact,"user");
  decoratePurchaseDrawerCaptions(detailDrawer);
 }
 private void decoratePurchaseDrawerCaptions(Node node){
  if(node instanceof Label label&&label.getGraphic()==null){String semantic=drawerSemantic(label.getText());if(semantic!=null){label.setGraphic(IconFactory.compactIcon(semantic,14));label.setGraphicTextGap(6);label.getStyleClass().add("erp-drawer-caption");label.getProperties().put("erp-icon-preserve",true);}}
  if(node instanceof ButtonBase button&&button.getGraphic()==null){String semantic=drawerSemantic(button.getText());if(semantic!=null)UiActionIcons.apply(button,semantic);}
  if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())decoratePurchaseDrawerCaptions(child);
 }
 private void drawerValue(Label label,String semantic){if(label==null)return;label.setGraphic(IconFactory.compactIcon(semantic,14));label.setGraphicTextGap(7);label.getStyleClass().add("erp-drawer-value");label.getProperties().put("erp-icon-preserve",true);}
 private String drawerSemantic(String text){String t=s(text).trim().toLowerCase(java.util.Locale.ROOT);if(t.equals("purchase details")||t.contains("invoice")||t.contains("reference"))return"document";if(t.contains("supplier"))return"supplier";if(t.contains("contact"))return"user";if(t.contains("phone")||t.contains("mobile"))return"phone";if(t.contains("email"))return"email";if(t.contains("gst")||t.contains("tax"))return"tax";if(t.contains("balance"))return"balance";if(t.contains("paid")||t.contains("payment"))return"payment";if(t.contains("amount")||t.contains("charge"))return"currency";if(t.contains("due"))return"calendar";if(t.contains("transport")||t.contains("vehicle"))return"delivery";if(t.contains("note"))return"notes";if(t.contains("attachment"))return"attachment";if(t.contains("address"))return"location";if(t.contains("excel"))return"excel";if(t.contains("pdf")||t.contains("print"))return"pdf";if(t.contains("close"))return"cancel";return null;}

 private void installKpiIcons(){
  setKpiIcon(purchaseTotalIcon,"purchase");setKpiIcon(purchaseOrdersIcon,"document");setKpiIcon(purchaseSuppliersIcon,"supplier");setKpiIcon(purchaseItemsIcon,"item");setKpiIcon(purchasePaidIcon,"payment");
 }
 private void setKpiIcon(StackPane pane,String semantic){if(pane!=null)pane.getChildren().setAll(IconFactory.compactIcon(semantic,22));}

 private TableCell<Purchase,Double> moneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:fmt(v));setAlignment(Pos.CENTER_RIGHT);}};}
 private TableCell<Purchase,Double> totalMoneyCell(){return coloredMoneyCell("register-amount-total","register-amount-total");}
 private TableCell<Purchase,Double> balanceMoneyCell(){return coloredMoneyCell("register-balance-open","register-balance-settled");}
 private TableCell<Purchase,Double> coloredMoneyCell(String positiveClass,String zeroClass){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:fmt(v));setAlignment(Pos.CENTER_RIGHT);getStyleClass().removeAll("register-amount-total","register-balance-open","register-balance-settled");if(!e&&v!=null){String style=v>.009?positiveClass:zeroClass;if(style!=null)getStyleClass().add(style);}}};}
 private TableCell<Purchase,Double> paidMoneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e||v==null?null:fmt(v));setAlignment(Pos.CENTER_RIGHT);getStyleClass().removeAll("register-paid-positive","register-paid-zero");if(!e&&v!=null)getStyleClass().add(v>.009?"register-paid-positive":"register-paid-zero");}};}
 private void configureExplicitButtonIcons(){
  setButtonIcon(btnNewPurchase,"purchase");setButtonIcon(btnAdvanced,"filter");setButtonIcon(btnReset,"refresh");setButtonIcon(btnRefresh,"refresh");
  setButtonIcon(btnFirstPage,"first");setButtonIcon(btnPreviousPage,"previous");setButtonIcon(btnNextPage,"next");setButtonIcon(btnLastPage,"last");
  setButtonIcon(btnExportExcel,"excel");setButtonIcon(btnExportPdf,"pdf");
 }
 private void setButtonIcon(ButtonBase button,String semantic){UiActionIcons.apply(button, semantic);}
 private boolean interactiveTableTarget(javafx.scene.Node target,TableRow<?> row){for(javafx.scene.Node node=target;node!=null&&node!=row;node=node.getParent())if(node instanceof ButtonBase||node instanceof TextInputControl||node instanceof ComboBoxBase<?>)return true;return false;}
 private void configureModernTable(){
 colStatus.setText("Doc Status");colStatus.setMinWidth(118);colStatus.setPrefWidth(118);colStatus.setCellValueFactory(v->new SimpleStringProperty(documentStatus(v.getValue())));
 colStatus.setCellFactory(x->SemanticTableCells.status("document"));colMail.setCellFactory(x->SemanticTableCells.status("email"));colDue.setCellFactory(x->SemanticTableCells.dueDate());
  tablePurchase.setRowFactory(tv->{TableRow<Purchase> row=new TableRow<>();row.setOnMouseClicked(e->{if(row.isEmpty()||e.getButton()!=javafx.scene.input.MouseButton.PRIMARY||e.getClickCount()!=1||interactiveTableTarget(e.getPickResult().getIntersectedNode(),row))return;Purchase clicked=row.getItem();if(detailDrawer.isVisible()&&selected!=null&&selected.getId()==clicked.getId())closeDetails();else{tablePurchase.getSelectionModel().select(clicked);details(clicked);}e.consume();});return row;});
  // Apply the resize policy after every dynamic column has been installed. This
  // makes the register consume the complete available width in both themes.
 }
 private void showPaymentDetails(Purchase purchase){try{InvoicePaymentDetailsDialog.show(tablePurchase,support,"PURCHASE",purchase.getId(),purchase.getInvoiceNo(),"Supplier",purchase.getSupplier()==null?"":purchase.getSupplier().getName(),purchase.getTotalAmount(),purchase.getPaidAmount(),purchase.getBalanceAmount());}catch(Exception e){error(e);}}
 private TableCell<Purchase,String>purchaseDueCell(){return new TableCell<>(){@Override protected void updateItem(String value,boolean empty){super.updateItem(value,empty);setText(empty?null:value);setGraphic(null);getStyleClass().removeAll("pill-success","pill-warning","pill-danger");if(!empty&&value!=null){boolean paid=value.startsWith("Paid"),overdue=value.startsWith("Overdue");getStyleClass().add(paid?"pill-success":overdue?"pill-danger":"pill-warning");setGraphic(IconFactory.statusIcon(overdue?"error":paid?"save":"reminder",overdue?"#dc2626":paid?"#16a34a":"#2563eb"));}}};}
 private TableCell<Purchase,String>pill(){return pill("document");}private TableCell<Purchase,String>pill(String icon){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);setGraphic(null);getStyleClass().removeAll("pill-success","pill-warning","pill-danger");if(!e&&v!=null){boolean returned="RETURNED".equalsIgnoreCase(v),partialReturn="PARTIALLY RETURNED".equalsIgnoreCase(v),good="COMPLETED".equals(v)||"Sent".equals(v)||returned,pending="IN PROGRESS".equals(v)||"PENDING".equals(v)||partialReturn;getStyleClass().add(good?"pill-success":pending?"pill-warning":"pill-danger");setGraphic(IconFactory.statusIcon(returned||partialReturn?"return":good?icon:pending?("document".equals(icon)?"reminder":icon):"error",good?"#16a34a":pending?"#2563eb":"#dc2626"));}}};}
 private String documentStatus(Purchase p){String stored=s(p.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);if(java.util.Set.of("DELETED","CANCELLED","RETURNED","PARTIALLY RETURNED","DRAFT").contains(stored))return stored;if(p.getBalanceAmount()<=.01)return "COMPLETED";if(p.getPaidAmount()>0)return "IN PROGRESS";return "PENDING";}
 private void installRegisterTools(){Node parent=tablePurchase.getParent();if(!(parent instanceof VBox box))return;HBox bar=null;for(Node n:box.getChildren())if(n instanceof HBox h&&h.getStyleClass().contains("export-bar")){bar=h;break;}if(bar==null||bar.getProperties().putIfAbsent("tools-installed",true)!=null)return;Button print=new Button("Print");print.setGraphic(IconFactory.icon("print"));print.getStyleClass().add("secondary-button");print.setOnAction(e->printRegister());bar.getChildren().add(Math.min(2,bar.getChildren().size()),print);}
 private void printRegister(){PrinterJob job=PrinterJob.createPrinterJob();if(job!=null&&job.showPrintDialog(tablePurchase.getScene().getWindow())){if(job.printPage(tablePurchase))job.endJob();}}
 private void setupActions(){
  colActions.setCellFactory(c->new TableCell<>(){
   final MenuButton m=new MenuButton();
   final MenuItem edit;
   final MenuItem payment;
   final MenuItem createReturn;
   final MenuItem cancel;
   final MenuItem delete;
   {
    m.getProperties().put("erp.icon.semantic","actions");m.setGraphic(IconFactory.compactIcon("actions",15));
    item("View Purchase","view",e->view(row()));
    edit=item("Edit Purchase","edit",e->edit(row()));
    item("Duplicate Purchase","copy",e->duplicate(row()));
    item("Preview / Download PDF","print",e->pdf(row()));
    item("View / Download Excel","excel",e->excel(row()));
    item("Send Email","email",e->email(row()));
    item("Send WhatsApp","whatsapp",e->whatsapp(row()));
    payment=item("View / Record Payments","payment",e->payment(row()));
    createReturn=item("Create Purchase Return","return",e->createReturn(row()));
    item("Notes / Remarks","notes",e->notes(row()));
    cancel=item("Cancel Purchase","cancel",e->cancelPurchase(row()));
    delete=item("Delete Purchase","delete",e->delete(row()));
    delete.getStyleClass().add("danger-menu-item");
    m.setOnShowing(e->updateAvailability());
    m.getStyleClass().add("row-actions");m.setGraphic(IconFactory.compactIcon("actions",16));m.setText("Actions");m.setContentDisplay(ContentDisplay.LEFT);m.setGraphicTextGap(6);m.setTooltip(new Tooltip("Actions"));IconFactory.decorateActionMenu(m);
   }
   private void updateAvailability(){
    Purchase current=getTableRow()==null?null:getTableRow().getItem();
    if(current==null){edit.setDisable(true);payment.setDisable(true);createReturn.setDisable(true);cancel.setDisable(true);delete.setDisable(true);return;}
    String status=s(current.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);
    boolean cancelled="CANCELLED".equals(status),deleted="DELETED".equals(status),inactive=cancelled||deleted;
    boolean locked=isFinanciallyLocked(current);
    edit.setDisable(inactive);
    payment.setDisable(inactive||"DRAFT".equals(status));
    createReturn.setDisable(!isReturnEligible(current));
    cancel.setDisable(locked||inactive);
    delete.setDisable(locked||deleted);
    cancel.setVisible(true);delete.setVisible(true);
   }
   private Purchase row(){Purchase value=getTableRow()==null?null:getTableRow().getItem();if(value==null)throw new IllegalStateException("This purchase row is no longer available. Refresh the register and try again.");return value;}
   private MenuItem item(String n,String icon,javafx.event.EventHandler<javafx.event.ActionEvent>h){MenuItem i=new MenuItem(n);i.getProperties().put("erp.icon.semantic",icon);i.setGraphic(IconFactory.compactIcon(icon,16));i.setOnAction(event->{try{h.handle(event);}catch(Throwable failure){error(failure);}});m.getItems().add(i);return i;}
   @Override protected void updateItem(Void v,boolean e){super.updateItem(v,e);setGraphic(e?null:m);setAlignment(Pos.CENTER);}
  });
 }

 @FXML public void refresh(){
  btnRefresh.setDisable(true);
  UiTaskExecutor.submitLatest("purchase-register-load",
   () -> {
    List<Purchase> rows=service.getAll();
    return rows==null?List.<Purchase>of():List.copyOf(rows);
   },
   this::applyPurchaseLoad,
   failure -> {btnRefresh.setDisable(false);this.error(failure);});
 }
 private void applyPurchaseLoad(List<Purchase> loaded){
  long started=System.nanoTime();
  all=loaded;
  cmbSupplier.getItems().setAll("All Suppliers");
  cmbSupplier.getItems().addAll(all.stream().map(Purchase::getSupplier).filter(Objects::nonNull).map(p->p.getName()).filter(Objects::nonNull).map(String::trim).filter(name->!name.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList());
  if(cmbSupplier.getValue()==null||cmbSupplier.getValue().startsWith("All"))cmbSupplier.setValue("All Suppliers");
  metrics();filter();openLinkedRecordIfRequested();ScreenRefreshPolicy.markRefreshed("purchase-register");btnRefresh.setDisable(false);
  long ms=(System.nanoTime()-started)/1_000_000L;PerformanceMonitor.event("controller-phase","purchase-register-apply | "+ms+" ms | rows="+all.size());
 }


 private void openLinkedRecordIfRequested(){LinkedRecordContext.Target target=LinkedRecordContext.consume("PURCHASE");if(target==null)return;Purchase purchase=all.stream().filter(x->(target.recordId()!=null&&x.getId()==target.recordId())||(!target.documentNo().isBlank()&&target.documentNo().equalsIgnoreCase(s(x.getInvoiceNo())))).findFirst().orElse(null);if(purchase==null){info("The linked Purchase is no longer available"+(target.documentNo().isBlank()?".":": "+target.documentNo()));return;}txtSearch.clear();cmbSupplier.setValue("All Suppliers");cmbPaymentStatus.setValue("All");cmbMailStatus.setValue("All");dpFrom.setValue(null);dpTo.setValue(null);filtered=all;int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue();int index=filtered.indexOf(purchase);currentPage=Math.max(0,index/size);renderPage();tablePurchase.getSelectionModel().select(purchase);tablePurchase.scrollTo(purchase);details(purchase);PerformanceMonitor.event("linked-navigation","PURCHASE -> "+purchase.getInvoiceNo()+" | source="+target.source());}
 private void metrics(){List<Purchase> active=all.stream().filter(this::isActiveFinancialDocument).toList();double total=active.stream().mapToDouble(Purchase::getTotalAmount).sum(),paid=active.stream().mapToDouble(Purchase::getPaidAmount).sum(),items=active.stream().mapToDouble(Purchase::getQuantity).sum();long suppliers=active.stream().map(Purchase::getSupplier).filter(Objects::nonNull).map(p->p.getId()).distinct().count();lblTotal.setText(fmt(total));lblTotalCount.setText("Active Docs");lblToday.setText(String.valueOf(active.size()));lblTodayCount.setText("Active Docs");lblPending.setText(String.valueOf(suppliers));lblPendingCount.setText("Suppliers");lblOverdue.setText(String.valueOf((long)items));lblOverdueCount.setText("Items");lblPaid.setText(fmt(paid));}
 private void filter(){String q=s(txtSearch.getText()).toLowerCase(),sup=cmbSupplier.getValue(),ps=cmbPaymentStatus.getValue(),mail=cmbMailStatus.getValue();filtered=all.stream().filter(p->q.isBlank()||(p.getInvoiceNo()+p.getSupplier().getName()+s(p.getSupplier().getPhone())+s(p.getSupplier().getGstin())).toLowerCase().contains(q)).filter(p->sup==null||sup.startsWith("All")||sup.equals(p.getSupplier().getName())).filter(p->dpFrom.getValue()==null||!p.getInvoiceDate().isBefore(dpFrom.getValue())).filter(p->dpTo.getValue()==null||!p.getInvoiceDate().isAfter(dpTo.getValue())).filter(p->matchesPaymentFilter(p,ps)).filter(p->mail==null||mail.equals("All")||mail.equals(p.isEmailSent()?"Sent":"Not Sent")).toList();currentPage=0;renderPage();List<Purchase> active=filtered.stream().filter(this::isActiveFinancialDocument).toList();lblSummary.setText(filtered.size()+" purchases • Active Total "+fmt(active.stream().mapToDouble(Purchase::getTotalAmount).sum())+" • Active Balance "+fmt(active.stream().mapToDouble(Purchase::getBalanceAmount).sum()));}
 private boolean matchesPaymentFilter(Purchase p,String filter){if(filter==null||filter.equals("All"))return true;if("OVERDUE".equals(filter))return isActiveFinancialDocument(p)&&p.getBalanceAmount()>.01&&p.getDueDate()!=null&&p.getDueDate().isBefore(BusinessClock.today());return filter.equalsIgnoreCase(p.getPaymentStatus());}
 private void renderPage(){int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue(),pages=Math.max(1,(int)Math.ceil(filtered.size()/(double)size));currentPage=Math.min(currentPage,pages-1);int from=Math.min(currentPage*size,filtered.size()),to=Math.min(from+size,filtered.size());tablePurchase.setItems(FXCollections.observableArrayList(filtered.subList(from,to)));lblPageNumber.setText((currentPage+1)+" / "+pages);lblPageInfo.setText(filtered.isEmpty()?"No entries":"Showing "+(from+1)+" to "+to+" of "+filtered.size()+" entries");}
 @FXML private void firstPage(){currentPage=0;renderPage();}@FXML private void previousPage(){if(currentPage>0)currentPage--;renderPage();}@FXML private void nextPage(){int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue(),pages=Math.max(1,(int)Math.ceil(filtered.size()/(double)size));if(currentPage<pages-1)currentPage++;renderPage();}@FXML private void lastPage(){int size=cmbPageSize.getValue()==null?25:cmbPageSize.getValue();currentPage=Math.max(0,(int)Math.ceil(filtered.size()/(double)size)-1);renderPage();}
 @FXML private void showToday(){applyDateRange(BusinessClock.today(),BusinessClock.today());}
 @FXML private void showYesterday(){LocalDate d=BusinessClock.today().minusDays(1);applyDateRange(d,d);}
 @FXML private void showSevenDays(){applyDateRange(BusinessClock.today().minusDays(6),BusinessClock.today());}
 @FXML private void showThirtyDays(){applyDateRange(BusinessClock.today().minusDays(29),BusinessClock.today());}
 @FXML private void showCustomRange(){dpFrom.requestFocus();}
 private void applyDateRange(LocalDate from,LocalDate to){dpFrom.setValue(from);dpTo.setValue(to);filter();}
 @FXML private void toggleAdvanced(){advancedFilters.setManaged(btnAdvanced.isSelected());advancedFilters.setVisible(btnAdvanced.isSelected());}@FXML private void resetFilters(){txtSearch.clear();cmbSupplier.setValue("All Suppliers");cmbPaymentStatus.setValue("All");cmbMailStatus.setValue("All");dpFrom.setValue(BusinessClock.today().minusMonths(6));dpTo.setValue(BusinessClock.today());filter();}
 @FXML private void newPurchase(){NavigationManager.navigateOrReport("/fxml/pages/Purchase.fxml");}
 private void edit(Purchase p){openPurchaseEditor(p,false);}
 private void view(Purchase p){openPurchaseEditor(p,true);}
 private void openPurchaseEditor(Purchase p,boolean viewOnly){
  try{
   FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Purchase.fxml"));
   Parent root=loader.load();
   org.example.util.ProfessionalUiEnhancer.enhance(root);
   PurchaseController controller=loader.getController();
   Purchase full=service.getByInvoice(p.getInvoiceNo());
   if(full==null)throw new IllegalStateException("Purchase invoice "+p.getInvoiceNo()+" was not found. Refresh the register and try again.");
   controller.loadPurchase(full);
   if(viewOnly)controller.setViewMode(true);
   NavigationManager.getInstance().showPreparedPage("/fxml/pages/Purchase.fxml",root,controller);
  }catch(Exception e){error(e);}
 }
 private void details(Purchase p){selected=p;detailDrawer.setManaged(true);detailDrawer.setVisible(true);mainSplit.setDividerPositions(.8);lblDetailInvoice.setText(p.getInvoiceNo());lblDetailSupplier.setText(p.getSupplier()==null?"Unknown Supplier":p.getSupplier().getName());lblDetailContact.setText(p.getSupplier()==null?"":s(p.getSupplier().getPhone())+"\n"+s(p.getSupplier().getEmail())+"\n"+s(p.getSupplier().getGstin()));lblDetailAmount.setText(fmt(p.getTotalAmount()));lblDetailPaid.setText(fmt(p.getPaidAmount()));lblDetailBalance.setText(fmt(p.getBalanceAmount()));lblDetailDue.setText(due(p));if(lblDetailGst!=null)lblDetailGst.setText(fmt(p.getGstAmount()));if(lblDetailReference!=null)lblDetailReference.setText(s(p.getReferenceNo()).isBlank()?"Not set":p.getReferenceNo());if(lblDetailPaymentTerms!=null)lblDetailPaymentTerms.setText(s(p.getPaymentTerms()).isBlank()?"Not set":p.getPaymentTerms());if(lblDetailContactPerson!=null)lblDetailContactPerson.setText(s(p.getContactPerson()).isBlank()?(p.getSupplier()==null||s(p.getSupplier().getContactPerson()).isBlank()?"Not set":p.getSupplier().getContactPerson()):p.getContactPerson());if(lblDetailPhone!=null)lblDetailPhone.setText(p.getSupplier()==null||s(p.getSupplier().getPhone()).isBlank()?"Not set":p.getSupplier().getPhone());if(lblDetailEmail!=null)lblDetailEmail.setText(p.getSupplier()==null||s(p.getSupplier().getEmail()).isBlank()?"Not set":p.getSupplier().getEmail());if(lblDetailGstin!=null)lblDetailGstin.setText(s(p.getBillingGstin()).isBlank()?(p.getSupplier()==null||s(p.getSupplier().getGstin()).isBlank()?"Not set":p.getSupplier().getGstin()):p.getBillingGstin());if(lblDetailCharges!=null)lblDetailCharges.setText(p.getCharges()==null||p.getCharges().isEmpty()?"Not Applicable":p.getCharges().stream().map(c->c.getChargeType()+" • "+fmt(c.getAmount())+(c.isTaxable()?" (GST "+String.format(java.util.Locale.ROOT,"%.2f%%",c.getGstPercent())+")":"")).collect(java.util.stream.Collectors.joining("\n")));if(lblDetailChargeTax!=null)lblDetailChargeTax.setText(fmt(p.getChargesTaxAmount()));if(lblDetailGstType!=null)lblDetailGstType.setText(s(p.getGstType()).isBlank()?"Not Applicable":p.getGstType());if(lblDetailTransporter!=null)lblDetailTransporter.setText(s(p.getTransporter()).isBlank()?"Not Applicable":p.getTransporter());if(lblDetailVehicle!=null)lblDetailVehicle.setText(s(p.getVehicleNumber()).isBlank()?"Not Applicable":p.getVehicleNumber());if(lblDetailNotes!=null)lblDetailNotes.setText(s(p.getNotes()).isBlank()?"No notes":p.getNotes());if(lblDetailAttachment!=null){try{int count=support.documentAttachments("PURCHASE",p.getId()).size();lblDetailAttachment.setText(count==0?"No attachments":count+" attachment"+(count==1?"":"s"));}catch(Exception ignored){lblDetailAttachment.setText(s(p.getAttachmentPath()).isBlank()?"No attachments":"1 attachment");}}if(lblDetailBillingAddress!=null)lblDetailBillingAddress.setText(s(p.getBillingAddress()).isBlank()?"Not set":p.getBillingAddress());if(lblDetailDeliveryAddress!=null)lblDetailDeliveryAddress.setText(s(p.getDeliveryAddress()).isBlank()?"Not set":p.getDeliveryAddress());}
private void configureDetailsCloseButton(){
     if(btnCloseDetails == null) return;
     btnCloseDetails.setGraphic(IconFactory.icon("cancel"));
     btnCloseDetails.getProperties().put("erp-icon-preserve", true);
     btnCloseDetails.setAccessibleText("Close purchase details");
 }
 @FXML private void closeDetails(){selected=null;detailDrawer.setManaged(false);detailDrawer.setVisible(false);mainSplit.setDividerPositions(1);if(tablePurchase!=null)tablePurchase.getSelectionModel().clearSelection();}
 @FXML private void paySelected(){if(selected!=null)payment(selected);}
 @FXML private void editSelected(){if(selected!=null)edit(selected);}
 @FXML private void excelSelected(){if(selected!=null)excel(selected);}
 @FXML private void emailSelected(){if(selected!=null)email(selected);}
 private void payment(Purchase p){if(p==null)return;String document=s(p.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);if("DRAFT".equals(document)){info("Post the Purchase before opening payments.");return;}if(java.util.Set.of("DELETED","CANCELLED").contains(document)){info("Payments are unavailable for deleted or cancelled purchases.");return;}PurchaseScreenContext.select(p.getInvoiceNo());NavigationManager.getInstance().loadPage("/fxml/pages/PurchasePayment.fxml");}
 private void pdf(Purchase p){try{Purchase full=requirePurchase(p);java.awt.Desktop.getDesktop().open(InvoicePdfService.purchase(full).toFile());}catch(Exception e){error(e);}}
 private void excel(Purchase p){try{Purchase full=requirePurchase(p);Path file=ExcelOutputService.purchase(full);if(java.awt.Desktop.isDesktopSupported())java.awt.Desktop.getDesktop().open(file.toFile());else info("Excel file created: "+file);}catch(Exception e){error(e);}}
 private void email(Purchase p){String stage="loading the purchase invoice";String recipient="";try{Purchase full=requirePurchase(p);if(full.getSupplier()==null)throw new IllegalStateException("No supplier is linked to "+full.getInvoiceNo()+".");recipient=s(full.getSupplier().getEmail()).trim();if(recipient.isBlank())throw new IllegalStateException("Supplier email is missing for "+full.getSupplier().getName()+". Update Supplier Master and try again.");stage="generating the purchase invoice PDF";Path pdf=InvoicePdfService.purchase(full);stage="sending the email";EmailService.send(recipient,"Purchase Invoice "+full.getInvoiceNo(),"Dear "+s(full.getSupplier().getName())+",\n\nPlease find the purchase invoice attached.\n\nRegards,\n"+org.example.config.ConfigManager.get("company.name","DSE ERP"),pdf);service.markEmailSent(full.getId());logCommunication(full.getId(),recipient,"Purchase Invoice "+full.getInvoiceNo(),"SENT",null);refresh();info("Email sent successfully to "+recipient+".");}catch(Exception failure){logCommunication(p.getId(),recipient,"Purchase Invoice "+p.getInvoiceNo(),"FAILED",stage+": "+rootMessage(failure));error(new IllegalStateException("Email failed while "+stage+".\n\n"+rootMessage(failure),failure));}}
 private Purchase requirePurchase(Purchase row){Purchase full=service.getByInvoice(row.getInvoiceNo());if(full==null)throw new IllegalStateException("Purchase invoice "+row.getInvoiceNo()+" was not found. Refresh the register and try again.");return full;}
 private void logCommunication(int id,String recipient,String subject,String status,String error){try{support.communication(new SupportApiClient.CommunicationRequest("PURCHASE",id,"EMAIL",recipient,subject,status,error,user()));}catch(Exception ignored){}}
 private String rootMessage(Throwable failure){Throwable root=failure;while(root.getCause()!=null)root=root.getCause();String message=root.getMessage();return message==null||message.isBlank()?root.getClass().getSimpleName():message;}
 private void whatsapp(Purchase p){try{Purchase full=service.getByInvoice(p.getInvoiceNo());String phone=s(full.getSupplier().getPhone()).replaceAll("\\D","");if(phone.length()==10)phone="91"+phone;if(phone.isBlank())throw new IllegalStateException("Supplier mobile number is missing. Update it in Supplier Master.");Path pdf=InvoicePdfService.purchase(full);WhatsappService.openWhatsappWithMessage(phone,PaymentMessageService.purchaseMessage(full),pdf,PaymentMessageService.configuredQrPath());support.communication(new SupportApiClient.CommunicationRequest("PURCHASE",full.getId(),"WHATSAPP",phone,"Purchase Invoice "+full.getInvoiceNo(),"SENT",null,user()));refresh();info("WhatsApp is ready. The purchase PDF and configured UPI QR are on the clipboard for attachment.");}catch(Exception e){error(e);}}
 private void delete(Purchase p){if(isFinanciallyLocked(p)){info("Paid, partially paid, or settled purchases cannot be deleted. Create a purchase return instead.");return;}String status=s(p.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);if("DELETED".equals(status)){info("This purchase is already marked as deleted.");return;}String prompt="Delete "+p.getInvoiceNo()+"?\n\nThe document will disappear from the normal Purchase Register, but its backend audit record will be retained as DELETED.\nAny inventory movement already posted by this purchase will be reversed safely.";if(new OwnedAlert(Alert.AlertType.CONFIRMATION,prompt,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;try{service.delete(p.getInvoiceNo());refresh();closeDetails();info(p.getInvoiceNo()+" deleted from the register. Backend audit record retained.");}catch(Exception e){error(e);}}
 private void duplicate(Purchase p){try{FXMLLoader l=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Purchase.fxml"));Parent root=l.load();org.example.util.ProfessionalUiEnhancer.enhance(root);PurchaseController c=l.getController();c.loadPurchase(service.getByInvoice(p.getInvoiceNo()));c.prepareDuplicate();NavigationManager.getInstance().showPreparedPage("/fxml/pages/Purchase.fxml",root,c);}catch(Exception e){error(e);}}
 private void viewPayments(Purchase p){if(p==null)return;PurchaseScreenContext.select(p.getInvoiceNo());NavigationManager.getInstance().loadPage("/fxml/pages/PurchasePayment.fxml");}

 private void notes(Purchase p){TextInputDialog d=new OwnedTextInputDialog(p.getRemarks());d.setHeaderText("Notes / Remarks • "+p.getInvoiceNo());d.showAndWait().ifPresent(v->{try{support.notes("PURCHASE",p.getId(),v);refresh();}catch(Exception e){error(e);}});}
 private void cancelPurchase(Purchase p){if(p==null)return;String status=s(p.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);if("CANCELLED".equals(status)){info("This purchase is already cancelled.");return;}if("DELETED".equals(status)){info("Deleted purchases cannot be cancelled.");return;}if(isFinanciallyLocked(p)){info("Paid, partially paid, or settled purchases cannot be cancelled. Create a purchase return instead.");return;}String prompt="Cancel "+p.getInvoiceNo()+"?\n\nThe document will remain visible with status CANCELLED and any posted inventory will be reversed safely.";if(new OwnedAlert(Alert.AlertType.CONFIRMATION,prompt,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;try{service.cancel(p.getInvoiceNo());refresh();closeDetails();info(p.getInvoiceNo()+" cancelled. The document remains visible in the register.");}catch(Exception e){error(e);}}
 private void createReturn(Purchase p){String document=s(p.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);if("DRAFT".equals(document)){info("Post the Purchase before creating a Purchase Return.");return;}if("DELETED".equals(document)||"CANCELLED".equals(document)){info("Deleted or cancelled purchases cannot create a Purchase Return.");return;}Purchase full=service.getByInvoice(p.getInvoiceNo());if(full==null){info("Purchase invoice not found. Refresh and try again.");return;}List<ReturnEditorService.InvoiceItem> items=full.getLines().stream().map(line->new ReturnEditorService.InvoiceItem(line.getItemCode(),line.getItemDescription(),line.getQuantity(),line.getRate(),line.getGstPercent())).toList();ReturnEditorService.show(tablePurchase.getScene().getWindow(),ReturnEditorService.Type.PURCHASE,p.getInvoiceNo(),p.getSupplier().getName(),p.getSupplier().getId(),items).ifPresent(no->{refresh();info("Purchase return created: "+no);});}
 @FXML private void exportPurchase(){File f=choose("Purchase_Register.xlsx","Excel","*.xlsx");if(f==null)return;try(Workbook w=new XSSFWorkbook();FileOutputStream o=new FileOutputStream(f)){Sheet sh=w.createSheet("Purchases");String[]h={"Invoice","Date","Supplier","Amount","Paid","Balance","Due","Status","Email"};Row r=sh.createRow(0);for(int i=0;i<h.length;i++)r.createCell(i).setCellValue(h[i]);int n=1;for(Purchase p:filtered){r=sh.createRow(n++);String[]v={p.getInvoiceNo(),BusinessClock.formatDate(p.getInvoiceDate()),p.getSupplier().getName(),String.valueOf(p.getTotalAmount()),String.valueOf(p.getPaidAmount()),String.valueOf(p.getBalanceAmount()),String.valueOf(p.getDueDate()),p.getPaymentStatus(),p.isEmailSent()?"Sent":"Not Sent"};for(int i=0;i<v.length;i++)r.createCell(i).setCellValue(v[i]);}w.write(o);}catch(Exception e){error(e);}}@FXML private void exportPdf(){File f=choose("Purchase_Register.pdf","PDF","*.pdf");if(f==null)return;try{org.example.service.BrandedRegisterPdfService.export(f.toPath(),"Purchase Register",new String[]{"Invoice","Date","Supplier","Amount","Paid","Balance","Due","Status"},filtered.stream().map(x->new String[]{x.getInvoiceNo(),BusinessClock.formatDate(x.getInvoiceDate()),x.getSupplier().getName(),fmt(x.getTotalAmount()),fmt(x.getPaidAmount()),fmt(x.getBalanceAmount()),due(x),x.getPaymentStatus()}).toList(),new float[]{2,1.3f,2.6f,1.4f,1.4f,1.4f,1.4f,1.2f});}catch(Exception e){error(e);}}

 private boolean isFinanciallyLocked(Purchase p){if(p==null)return false;String payment=s(p.getPaymentStatus()).toUpperCase(java.util.Locale.ROOT);return p.getPaidAmount()>.009||p.getBalanceAmount()<=.009||payment.contains("PAID")||payment.contains("SETTLED")||payment.contains("PARTIAL");}
 private boolean isFullyPaid(Purchase p){if(p==null)return false;String payment=s(p.getPaymentStatus()).trim().toUpperCase(java.util.Locale.ROOT);return p.getBalanceAmount()<=.009||payment.contains("PAID")||payment.contains("SETTLED");}
 private boolean isReturnEligible(Purchase p){if(p==null||!isFullyPaid(p))return false;String document=s(p.getDocumentStatus()).trim().toUpperCase(java.util.Locale.ROOT);return !java.util.Set.of("DRAFT","CANCELLED","DELETED","RETURNED").contains(document);}
 private boolean isActiveFinancialDocument(Purchase p){String document=s(p.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);return !document.contains("CANCEL")&&!document.contains("DELETE")&&!"DRAFT".equals(document);}
 private File choose(String n,String l,String x){FileChooser c=new FileChooser();c.setInitialFileName(n);c.getExtensionFilters().add(new FileChooser.ExtensionFilter(l,x));return c.showSaveDialog(tablePurchase.getScene().getWindow());}private String due(Purchase p){String document=s(p.getDocumentStatus()).toUpperCase(java.util.Locale.ROOT);if(document.contains("DELETE"))return"Deleted";if(document.contains("CANCEL"))return"Cancelled";String status=s(p.getPaymentStatus()).toUpperCase(java.util.Locale.ROOT);if(status.contains("CANCEL"))return"Cancelled";if(p.getBalanceAmount()<=.01)return"Paid";if(p.getDueDate()==null)return"Not set";long d=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),p.getDueDate());return d<0?"Overdue by "+Math.abs(d)+" days":d==0?"Due today":"Due in "+d+" days";}private String fmt(double v){return money.format(v).replace("₹","₹ ");}private String s(String v){return v==null?"":v;}private String user(){return SessionService.current()==null?"System":SessionService.current().getFullName();}private void info(String m){org.example.util.ToastManager.success(tablePurchase,"Completed",m);}private void error(Throwable e){e.printStackTrace();String message=e.getMessage()==null?"Operation failed":e.getMessage();org.example.util.ModernDialog.error(tablePurchase,"Operation failed","Something went wrong",message);}


    private void applyRangeButtonIcons() {
        applyIcon(btnTodayRange,"calendar"); applyIcon(btnYesterdayRange,"history");
        applyIcon(btnSevenDaysRange,"calendar"); applyIcon(btnThirtyDaysRange,"calendar"); applyIcon(btnCustomRange,"calendar");
    }
    private void applyIcon(ButtonBase b,String semantic){ if(b!=null){b.setGraphic(IconFactory.compactIcon(semantic,15));b.getProperties().put("erp-icon-preserve",true);} }

    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colInvoice, "document");
        IconFactory.applyTableHeaderIcon(colDate, "calendar");
        IconFactory.applyTableHeaderIcon(colSupplier, "supplier");
        IconFactory.applyTableHeaderIcon(colMobile, "phone");
        IconFactory.applyTableHeaderIcon(colAmount, "currency");
        IconFactory.applyTableHeaderIcon(colPaid, "complete");
        IconFactory.applyTableHeaderIcon(colBalance, "balance");
        IconFactory.applyTableHeaderIcon(colDue, "reminder");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colMail, "email");
        IconFactory.applyTableHeaderIcon(colActions, "actions");
    }

 @Override public void onScreenShown(boolean reusedFromCache){if(!reusedFromCache)return;if(ScreenRefreshPolicy.shouldRefresh("purchase-register",ScreenRefreshPolicy.Mode.WHEN_STALE))refresh();}
 @Override public void onScreenHidden(){UiTaskExecutor.cancel("purchase-register-load");searchDebouncer.cancel();}
}
