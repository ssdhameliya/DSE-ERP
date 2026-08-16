package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.OwnedTextInputDialog;


import org.example.util.IconFactory;
import com.itextpdf.kernel.pdf.PdfDocument;import com.itextpdf.kernel.pdf.PdfWriter;import com.itextpdf.layout.Document;import com.itextpdf.layout.element.Paragraph;import com.itextpdf.layout.element.Table;
import javafx.beans.property.*;import javafx.collections.FXCollections;import javafx.fxml.FXML;import javafx.geometry.Insets;import javafx.geometry.Pos;import javafx.scene.chart.*;import javafx.scene.control.*;import javafx.scene.layout.*;import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.example.config.ConfigManager;import org.example.api.quotation.QuotationApiClient;import org.example.api.master.MasterApiClient;import org.example.api.support.SupportApiClient;import org.example.api.insights.InsightsApiClient;import org.example.model.Party;import org.example.model.Item;import org.example.service.*;
import java.io.*;import java.nio.file.*;import java.text.NumberFormat;import java.time.LocalDate;import java.util.*;

public class QuotationController implements org.example.navigation.ScreenLifecycle {
 @FXML private Label lblTotalValue,lblTotalCount,lblPendingValue,lblPendingCount,lblAcceptedValue,lblAcceptedCount,lblExpiredValue,lblExpiredCount,lblConversion,lblAverage,lblFilteredSummary;
 @FXML private TextField txtSearch,txtNumber,txtAmountFrom,txtAmountTo; @FXML private TextArea txtDetailNotes; @FXML private ComboBox<String> cmbCustomer,cmbStatus,cmbSalesperson,cmbFollowUp,cmbSource; @FXML private DatePicker dpFrom,dpTo,dpValid; @FXML private ToggleButton btnAdvanced; @FXML private GridPane advancedFilters; @FXML private FlowPane activeFilterChips; @FXML private MenuButton savedViewsMenu;
 @FXML private Button btnNewQuotation,btnReset,btnRefresh,btnSaveView,btnApplyFilters,btnExportExcel,btnExportPdf,btnTodayRange,btnYesterdayRange,btnSevenDaysRange,btnThirtyDaysRange,btnCustomRange;
 @FXML private StackPane pageIcon,totalMetricIcon,pendingMetricIcon,acceptedMetricIcon,expiredMetricIcon,conversionMetricIcon,averageMetricIcon;
 @FXML private TableView<QuoteRow> table; @FXML private TableColumn<QuoteRow,String> colNo,colDate,colCustomer,colValid,colStatus,colFollowUp,colConverted,colSalesperson,colCreatedBy; @FXML private TableColumn<QuoteRow,Number> colAmount; @FXML private TableColumn<QuoteRow,Void> colActions;
 @FXML private LineChart<String,Number> trendChart; @FXML private PieChart statusChart; @FXML private SplitPane mainSplit; @FXML private VBox detailDrawer; @FXML private Label lblDetailNo,lblDetailDate,lblDetailStatus,lblDetailCustomer,lblDetailContact,lblDetailAmount,lblDetailDiscount,lblDetailValid,lblDetailFollow,lblDetailConverted;
 private final NumberFormat money=NumberFormat.getCurrencyInstance(Locale.of("en", "IN")); private final QuotationApiClient quotationApi=new QuotationApiClient(); private final MasterApiClient masterApi=new MasterApiClient(); private final SupportApiClient supportApi=new SupportApiClient(); private final InsightsApiClient insightsApi=new InsightsApiClient(); private List<QuoteRow> all=List.of(),filtered=List.of(); private QuoteRow selected;
 @FXML public void initialize(){
        configureExplicitTableHeaderIcons();configureUiIcons();configureEmptyState();colNo.setCellValueFactory(v->v.getValue().no);colDate.setCellValueFactory(v->v.getValue().date);colCustomer.setCellValueFactory(v->v.getValue().customer);colValid.setCellValueFactory(v->v.getValue().valid);colStatus.setCellValueFactory(v->v.getValue().status);colFollowUp.setCellValueFactory(v->v.getValue().followUp);colConverted.setCellValueFactory(v->v.getValue().converted);colSalesperson.setCellValueFactory(v->v.getValue().salesperson);colCreatedBy.setCellValueFactory(v->v.getValue().createdBy);colAmount.setCellValueFactory(v->v.getValue().amount);colAmount.setCellFactory(c->new TableCell<>(){protected void updateItem(Number v,boolean e){super.updateItem(v,e);setText(e||v==null?null:fmt(v.doubleValue()));setAlignment(Pos.CENTER_RIGHT);}});colStatus.setCellFactory(c->new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);getStyleClass().removeAll("pill-success","pill-warning","pill-danger","pill-neutral");if(!e)getStyleClass().add("ACCEPTED".equals(v)?"pill-success":"EXPIRED".equals(v)||"REJECTED".equals(v)?"pill-danger":"SENT".equals(v)?"pill-success":"pill-warning");}});setupActions();setupFilters();dpFrom.setValue(BusinessClock.today().minusDays(6));dpTo.setValue(BusinessClock.today());dpValid.setValue(null);configureResponsiveTable();if(org.example.util.PlatformUiSupport.isMac()){if(trendChart!=null){trendChart.setManaged(false);trendChart.setVisible(false);}if(statusChart!=null){statusChart.setManaged(false);statusChart.setVisible(false);}}detailDrawer.setMinWidth(320);detailDrawer.setPrefWidth(350);detailDrawer.setMaxWidth(410);detailDrawer.setManaged(false);detailDrawer.setVisible(false);mainSplit.setDividerPositions(1);table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null)showDetails(b);});txtSearch.textProperty().addListener((o,a,b)->applyFilters());refresh();}

 @FXML private void showToday(){applyDateRange(BusinessClock.today(),BusinessClock.today());}
 @FXML private void showYesterday(){LocalDate d=BusinessClock.today().minusDays(1);applyDateRange(d,d);}
 @FXML private void showSevenDays(){applyDateRange(BusinessClock.today().minusDays(6),BusinessClock.today());}
 @FXML private void showThirtyDays(){applyDateRange(BusinessClock.today().minusDays(29),BusinessClock.today());}
 @FXML private void showCustomRange(){dpFrom.requestFocus();}
 private void applyDateRange(LocalDate from,LocalDate to){dpFrom.setValue(from);dpTo.setValue(to);applyFilters();}
 private void setupFilters(){cmbStatus.getItems().setAll("All","DRAFT","SENT","ACCEPTED","REJECTED","EXPIRED");cmbStatus.setValue("All");cmbFollowUp.getItems().setAll("All","Overdue","Today","Next 7 Days","Not Set");cmbFollowUp.setValue("All");cmbSource.getItems().setAll("All","Direct","Email","WhatsApp","Website","Referral","Other");cmbSource.setValue("All");cmbSalesperson.getItems().setAll("All");cmbSalesperson.setValue("All");cmbCustomer.getItems().setAll("All customers");cmbCustomer.setValue("All customers");}
 @FXML public void refresh(){
  org.example.util.UiTaskExecutor.submitLatest("quotation-load", this::loadQuotationRows, this::applyQuotationRows, failure -> error(failure instanceof Exception ex ? ex : new RuntimeException(failure)));
 }
 private List<QuoteRow> loadQuotationRows() throws Exception {long started=System.nanoTime();List<QuoteRow> rows=quotationApi.list().stream().map(QuoteRow::new).toList();long ms=(System.nanoTime()-started)/1_000_000L;if(ms>=20)org.example.util.PerformanceMonitor.event("controller-phase","quotation-query | "+ms+" ms");return rows;}
 private void applyQuotationRows(List<QuoteRow> rows){
  long started=System.nanoTime();all=rows;
  cmbCustomer.getItems().setAll("All customers");cmbCustomer.getItems().addAll(all.stream().map(q->q.customer.get()).distinct().sorted().toList());
  cmbSalesperson.getItems().setAll("All");cmbSalesperson.getItems().addAll(all.stream().map(q->q.salesperson.get()).filter(x->!x.isBlank()).distinct().sorted().toList());
  updateStats();loadSavedViews();applyFilters();
  if(!org.example.util.PlatformUiSupport.isMac())javafx.application.Platform.runLater(this::updateCharts);
  long ms=(System.nanoTime()-started)/1_000_000L;if(ms>=20)org.example.util.PerformanceMonitor.event("controller-phase","quotation-apply | "+ms+" ms");
 }
 @Override public void onScreenShown(boolean reused){if(!reused) return; if(org.example.util.ScreenRefreshPolicy.shouldRefresh("quotations",org.example.util.ScreenRefreshPolicy.Mode.WHEN_STALE,java.time.Duration.ofSeconds(60)))refresh();}
 @Override public void onScreenHidden(){org.example.util.UiTaskExecutor.cancelPrefix("quotation-");}
 // Filters may fire while dropdowns are still being populated; null means "All".
 @FXML public void applyFilters(){
  String q=low(txtSearch==null?null:txtSearch.getText()),n=low(txtNumber==null?null:txtNumber.getText());
  LocalDate from=dpFrom==null?null:dpFrom.getValue(),to=dpTo==null?null:dpTo.getValue();
  filtered=all.stream()
    .filter(x->q.isBlank()||low(x.no.get()+" "+x.customer.get()+" "+x.phone+" "+x.email+" "+x.gstin).contains(q))
    .filter(x->n.isBlank()||low(x.no.get()).contains(n))
    .filter(x->cmbCustomer==null||all(cmbCustomer)||x.customer.get().equals(cmbCustomer.getValue()))
    .filter(x->cmbStatus==null||all(cmbStatus)||x.status.get().equals(cmbStatus.getValue()))
    .filter(x->from==null||x.quoteDate==null||!x.quoteDate.isBefore(from))
    .filter(x->to==null||x.quoteDate==null||!x.quoteDate.isAfter(to))
    .toList();
  table.getItems().setAll(filtered);
  lblFilteredSummary.setText(filtered.size()+" quotations • "+fmt(filtered.stream().mapToDouble(x->x.amount.get()).sum()));
 }
 private boolean all(ComboBox<String> box){String value=box.getValue();return value==null||value.startsWith("All");}
 private boolean followMatch(QuoteRow q){String f=cmbFollowUp.getValue();if(f==null||f.equals("All"))return true;if(q.followDate==null)return f.equals("Not Set");long d=java.time.temporal.ChronoUnit.DAYS.between(BusinessClock.today(),q.followDate);return f.equals("Overdue")?d<0:f.equals("Today")?d==0:f.equals("Next 7 Days")&&d>=0&&d<=7;}
 private void updateStats(){double total=sum(all),pending=sum(all.stream().filter(x->x.status.get().equals("DRAFT")||x.status.get().equals("SENT")).toList()),accepted=sum(all.stream().filter(x->x.status.get().equals("ACCEPTED")).toList()),expired=sum(all.stream().filter(x->x.status.get().equals("EXPIRED")).toList());long pc=all.stream().filter(x->x.status.get().equals("DRAFT")||x.status.get().equals("SENT")).count(),ac=all.stream().filter(x->x.status.get().equals("ACCEPTED")).count(),ec=all.stream().filter(x->x.status.get().equals("EXPIRED")).count();lblTotalValue.setText(fmt(total));lblTotalCount.setText(all.size()+" quotations");lblPendingValue.setText(fmt(pending));lblPendingCount.setText(pc+" quotations");lblAcceptedValue.setText(fmt(accepted));lblAcceptedCount.setText(ac+" quotations");lblExpiredValue.setText(fmt(expired));lblExpiredCount.setText(ec+" quotations");lblConversion.setText(all.isEmpty()?"0%":String.format("%.1f%%",ac*100.0/all.size()));lblAverage.setText(fmt(all.isEmpty()?0:total/all.size()));}
 private double sum(List<QuoteRow>x){return x.stream().mapToDouble(q->q.amount.get()).sum();}
 private void updateCharts(){if(trendChart==null||statusChart==null)return;Map<String,Double>months=new TreeMap<>();Map<String,Long>statuses=new LinkedHashMap<>();for(QuoteRow q:all){months.merge(q.date.get().substring(0,7),q.amount.get(),Double::sum);statuses.merge(q.status.get(),1L,Long::sum);}XYChart.Series<String,Number>s=new XYChart.Series<>();months.entrySet().stream().skip(Math.max(0,months.size()-8)).forEach(e->s.getData().add(new XYChart.Data<>(e.getKey(),e.getValue())));trendChart.getData().setAll(s);statusChart.getData().setAll(statuses.entrySet().stream().map(e->new PieChart.Data(e.getKey(),e.getValue())).toList());}
private void setupActions(){colActions.setCellFactory(c->new TableCell<>(){final MenuButton m=new MenuButton("Actions");{add("View","view",e->showDetails(row()));add("Edit","edit",e->edit(row()));add("View PDF","pdf",e->openPdf(row()));add("Send Email","email",e->sendEmail(row()));add("Send WhatsApp","whatsapp",e->sendWhatsapp(row()));add("Create Follow Up","reminder",e->followUp(row()));add("Convert to Sale","sale",e->convert(row()));add("Duplicate","copy",e->duplicate(row()));add("Delete","delete",e->delete(row()));m.getStyleClass().add("row-actions");m.setGraphic(IconFactory.compactIcon("actions",16));m.setText("Actions");m.setContentDisplay(ContentDisplay.LEFT);m.setGraphicTextGap(6);m.setTooltip(new Tooltip("Actions"));m.getProperties().put("erp-icon-preserve",true);}private QuoteRow row(){int index=getIndex();return index<0||index>=getTableView().getItems().size()?null:getTableView().getItems().get(index);}private void add(String t,String semantic,javafx.event.EventHandler<javafx.event.ActionEvent>h){MenuItem i=new MenuItem(t);i.setGraphic(IconFactory.compactIcon(semantic,16));i.setOnAction(h);m.getItems().add(i);}protected void updateItem(Void v,boolean e){super.updateItem(v,e);setGraphic(e?null:m);setAlignment(Pos.CENTER);}});}
 @FXML private void newQuotation(){QuotationEditorContext.open(null);org.example.navigation.NavigationManager.getInstance().loadPage("/fxml/pages/QuotationEditor.fxml");}
 private void edit(QuoteRow q){if(q==null)return;QuotationEditorContext.open(q.id);org.example.navigation.NavigationManager.getInstance().loadPage("/fxml/pages/QuotationEditor.fxml");}

 private void editDialog(QuoteRow existing) {
  Dialog<ButtonType> dialog = new OwnedDialog<>(table);
  dialog.setTitle(existing == null ? "Create Quotation" : "Edit Quotation");
  dialog.setHeaderText(existing == null ? "Create a new quotation for your customer" : "Update quotation " + existing.no.get());

  ComboBox<Choice> customer = new ComboBox<>(FXCollections.observableArrayList(
      loadChoices()));
  customer.setPromptText("Select Customer");
  customer.setMaxWidth(Double.MAX_VALUE);
  if (existing != null) customer.getItems().stream().filter(x -> x.id == existing.customerId).findFirst().ifPresent(customer::setValue);

  Button quickCustomer = new Button();
  quickCustomer.setGraphic(IconFactory.icon("add", 16));
  quickCustomer.getStyleClass().addAll("icon-button", "quotation-quick-add");
  quickCustomer.setTooltip(new Tooltip("Add customer from Customer Master"));
  quickCustomer.setOnAction(e -> new OwnedAlert(Alert.AlertType.INFORMATION,
      "Use Customer Master to create a new customer, then reopen this quotation.").showAndWait());

  DatePicker date = new DatePicker(existing == null ? BusinessClock.today() : existing.quoteDate);
  DatePicker valid = new DatePicker(existing == null ? BusinessClock.today().plusDays(30) : existing.validDate);
  DatePicker follow = new DatePicker(existing == null ? BusinessClock.today().plusDays(7) : existing.followDate);
  ComboBox<String> source = new ComboBox<>(FXCollections.observableArrayList("Direct", "Email", "WhatsApp", "Website", "Referral", "Other"));
  source.setValue(existing == null || existing.source.isBlank() ? "Direct" : existing.source);
  source.setMaxWidth(Double.MAX_VALUE);
  TextArea remarks = new TextArea(existing == null ? "" : existing.remarks);
  remarks.setPromptText("Enter remarks (optional)...");
  remarks.setPrefRowCount(2);
  remarks.setWrapText(true);

  GridPane details = new GridPane();
  details.getStyleClass().add("quotation-details-grid");
  details.setHgap(18);
  details.setVgap(12);
  ColumnConstraints label1 = new ColumnConstraints(92);
  ColumnConstraints field1 = new ColumnConstraints();
  field1.setHgrow(Priority.ALWAYS);
  ColumnConstraints label2 = new ColumnConstraints(92);
  ColumnConstraints field2 = new ColumnConstraints();
  field2.setHgrow(Priority.ALWAYS);
  details.getColumnConstraints().addAll(label1, field1, label2, field2);

  HBox customerBox = new HBox(8, customer, quickCustomer);
  HBox.setHgrow(customer, Priority.ALWAYS);
  details.add(requiredLabel("Customer"), 0, 0);
  details.add(customerBox, 1, 0);
  details.add(requiredLabel("Follow Up"), 2, 0);
  details.add(follow, 3, 0);
  details.add(requiredLabel("Date"), 0, 1);
  details.add(date, 1, 1);
  details.add(requiredLabel("Source"), 2, 1);
  details.add(source, 3, 1);
  details.add(requiredLabel("Valid Upto"), 0, 2);
  details.add(valid, 1, 2);
  details.add(new Label("Remarks"), 2, 2);
  details.add(remarks, 3, 2);
  GridPane.setValignment(remarks, javafx.geometry.VPos.TOP);

  VBox detailsCard = quotationSection("quotation", "QUOTATION DETAILS", details);

  ComboBox<ItemChoice> item = new ComboBox<>(FXCollections.observableArrayList(loadItems()));
  item.setPromptText("Select Item / Scan / Search");
  item.setMaxWidth(Double.MAX_VALUE);
  TextField qty = numericField("1.00");
  TextField gst = numericField("0.00");
  TextField discount = numericField("0.00");
  Button add = new Button("Add Item");
  add.setGraphic(IconFactory.icon("add", 16));
  add.getStyleClass().addAll("primary-button", "quotation-add-item");

  TableView<LineRow> lines = new TableView<>();
  lines.getStyleClass().addAll("quotation-entry-table", "erp-table-profile-line-item");
  lines.setPrefHeight(245);
  lines.setMinHeight(210);
  lines.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
  lines.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

  TableColumn<LineRow, String> ci = new TableColumn<>("Item Details");
  ci.setCellValueFactory(v -> v.getValue().description);
  ci.setMinWidth(210);
  ci.setCellFactory(c -> new TableCell<>() {
   @Override protected void updateItem(String value, boolean empty) {
    super.updateItem(value, empty);
    if (empty || value == null) { setGraphic(null); return; }
    LineRow row = getTableRow() == null ? null : (LineRow) getTableRow().getItem();
    Label name = new Label(value);
    name.getStyleClass().add("quotation-item-name");
    Label sku = new Label(row == null ? "" : "SKU: " + row.code);
    sku.getStyleClass().add("quotation-item-sku");
    setGraphic(new VBox(2, name, sku));
   }
  });

  TableColumn<LineRow, Number> cq = numberColumn("Quantity", r -> r.quantity, 92);
  cq.setCellFactory(c -> new TableCell<>() {
   @Override protected void updateItem(Number value, boolean empty) {
    super.updateItem(value, empty);
    if (empty || value == null) { setGraphic(null); return; }
    Label main = new Label(String.format("%.2f", value.doubleValue()));
    main.getStyleClass().add("quotation-qty-main");
    Label unit = new Label("Nos");
    unit.getStyleClass().add("quotation-item-sku");
    setGraphic(new VBox(1, main, unit));
    setAlignment(Pos.CENTER);
   }
  });
  TableColumn<LineRow, Number> cg = numberColumn("GST Rate", r -> r.gst, 92);
  cg.setCellFactory(c -> percentCell());
  TableColumn<LineRow, Number> cd = numberColumn("Discount", r -> r.discount, 100);
  cd.setCellFactory(c -> percentCell());
  TableColumn<LineRow, Number> cr = numberColumn("Rate (₹)", r -> r.rate, 105);
  cr.setCellFactory(c -> moneyCell(false));
  TableColumn<LineRow, Number> cga = numberColumn("GST Amt (₹)", r -> r.gstAmount, 105);
  cga.setCellFactory(c -> moneyCell(false));
  TableColumn<LineRow, Number> ct = numberColumn("Amount (₹)", r -> r.total, 120);
  ct.setCellFactory(c -> moneyCell(true));
  TableColumn<LineRow, Void> ca = new TableColumn<>("Action");
  ca.setMinWidth(76);
  setQuotationHeader(ci, "item", "Item Details");
  setQuotationHeader(cq, "quantity", "Quantity");
  setQuotationHeader(cg, "tax", "GST Rate");
  setQuotationHeader(cd, "discount", "Discount");
  setQuotationHeader(cr, "currency", "Rate (₹)");
  setQuotationHeader(cga, "tax", "GST Amt (₹)");
  setQuotationHeader(ct, "currency", "Amount (₹)");
  setQuotationHeader(ca, "actions", "Action");
  ca.setCellFactory(c -> new TableCell<>() {
   private final Button remove = new Button();
   {
    remove.setGraphic(IconFactory.icon("delete", 14));
    remove.getStyleClass().addAll("icon-button", "danger-button", "quotation-row-delete");
    remove.setTooltip(new Tooltip("Remove item"));
    remove.setOnAction(e -> {
     LineRow row = getTableRow() == null ? null : (LineRow) getTableRow().getItem();
     if (row != null) lines.getItems().remove(row);
    });
   }
   @Override protected void updateItem(Void value, boolean empty) {
    super.updateItem(value, empty);
    setGraphic(empty ? null : remove);
    setAlignment(Pos.CENTER);
   }
  });
  lines.getColumns().addAll(ci, cq, cg, cd, cr, cga, ct, ca);

  Label subtotal = new Label("₹ 0.00");
  Label discountTotal = new Label("₹ 0.00");
  Label taxable = new Label("₹ 0.00");
  Label gstTotal = new Label("₹ 0.00");
  Label grand = new Label("₹ 0.00");
  grand.getStyleClass().add("quotation-grand-total");

  Runnable updateTotals = () -> updateQuotationTotals(lines.getItems(), subtotal, discountTotal, taxable, gstTotal, grand);
  lines.getItems().addListener((javafx.collections.ListChangeListener<LineRow>) change -> updateTotals.run());

  if (existing != null) lines.getItems().setAll(loadLines(existing.id));
  item.valueProperty().addListener((o, old, selectedItem) -> {
   if (selectedItem != null) {
    gst.setText(String.format("%.2f", selectedItem.gst));
    discount.setText(String.format("%.2f", selectedItem.discount));
   } else {
    gst.setText("0.00");
    discount.setText("0.00");
   }
  });
  add.setOnAction(e -> {
   try {
    ItemChoice selectedItem = item.getValue();
    if (selectedItem == null) throw new IllegalArgumentException("Select an item.");
    double q = positiveNumber(qty.getText(), "Quantity");
    double g = percentage(gst.getText(), "GST Rate");
    double dsc = percentage(discount.getText(), "Discount");
    lines.getItems().add(new LineRow(selectedItem.code, selectedItem.description, q, selectedItem.rate, g, dsc));
    item.getSelectionModel().clearSelection();
    qty.setText("1.00");
    gst.setText("0.00");
    discount.setText("0.00");
    updateTotals.run();
   } catch (Exception ex) { error(ex); }
  });

  GridPane itemEntry = new GridPane();
  itemEntry.getStyleClass().add("quotation-item-entry");
  itemEntry.setHgap(12);
  itemEntry.setVgap(5);
  ColumnConstraints itemCol = new ColumnConstraints();
  itemCol.setHgrow(Priority.ALWAYS);
  itemCol.setPercentWidth(34);
  itemEntry.getColumnConstraints().addAll(itemCol, new ColumnConstraints(145), new ColumnConstraints(130),
      new ColumnConstraints(140), new ColumnConstraints(130));
  itemEntry.add(requiredLabel("Item"), 0, 0);
  itemEntry.add(requiredLabel("Quantity"), 1, 0);
  itemEntry.add(requiredLabel("GST Rate"), 2, 0);
  itemEntry.add(new Label("Discount"), 3, 0);
  itemEntry.add(item, 0, 1);
  itemEntry.add(qty, 1, 1);
  itemEntry.add(gst, 2, 1);
  itemEntry.add(discount, 3, 1);
  itemEntry.add(add, 4, 1);

  VBox itemsContent = new VBox(12, itemEntry, lines);
  VBox itemsCard = quotationSection("item", "ADD ITEMS", itemsContent);
  VBox content = new VBox(12, detailsCard, itemsCard);
  content.getStyleClass().add("quotation-dialog-content");
  content.setPadding(new Insets(8, 10, 8, 10));

  ScrollPane scroll = new ScrollPane(content);
  scroll.setFitToWidth(true);
  scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
  scroll.getStyleClass().add("quotation-dialog-scroll");

  dialog.getDialogPane().setPrefSize(1000, 760);
  dialog.getDialogPane().setMinSize(900, 680);
  dialog.getDialogPane().getStyleClass().add("quotation-editor-dialog");
  dialog.getDialogPane().setContent(scroll);
  dialog.getDialogPane().getButtonTypes().addAll(
      new ButtonType("Save", ButtonBar.ButtonData.OK_DONE),
      new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));

  Button save = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(0));
  save.setGraphic(IconFactory.icon("save", 15));
  save.getStyleClass().add("save-button");
  Button cancel = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(1));
  cancel.setGraphic(IconFactory.icon("cancel", 15));
  cancel.getStyleClass().add("secondary-button");

  save.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
   try {
    if (customer.getValue() == null) throw new IllegalArgumentException("Select a customer.");
    if (date.getValue() == null) throw new IllegalArgumentException("Select the quotation date.");
    if (valid.getValue() == null) throw new IllegalArgumentException("Select the valid-until date.");
    if (valid.getValue().isBefore(date.getValue())) throw new IllegalArgumentException("Valid-until date cannot be before quotation date.");
    if (lines.getItems().isEmpty()) throw new IllegalArgumentException("Add at least one item.");
    saveQuote(existing, customer.getValue(), date.getValue(), valid.getValue(), follow.getValue(),
        existing == null ? user() : existing.salesperson.get(), source.getValue(), remarks.getText(), lines.getItems());
   } catch (Exception ex) {
    e.consume();
    error(ex);
   }
  });
  updateTotals.run();
  dialog.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).ifPresent(b -> refresh());
 }

 private Label requiredLabel(String text) {
  Label label = new Label(text);
  Label star = new Label(" *");
  star.getStyleClass().add("required-star");
  HBox box = new HBox(label, star);
  box.setAlignment(Pos.CENTER_LEFT);
  Label wrapper = new Label();
  wrapper.setGraphic(box);
  return wrapper;
 }

 private TextField numericField(String value) {
  TextField field = new TextField(value);
  field.setAlignment(Pos.CENTER_RIGHT);
  return field;
 }

 private void setQuotationHeader(TableColumn<?, ?> column, String semantic, String title) {
  column.setText(title);
  IconFactory.applyTableHeaderIcon(column, semantic);
 }

 private TableColumn<LineRow, Number> numberColumn(
     String title, java.util.function.Function<LineRow, javafx.beans.value.ObservableValue<Number>> property, double width) {
  TableColumn<LineRow, Number> column = new TableColumn<>(title);
  column.setCellValueFactory(v -> property.apply(v.getValue()));
  column.setMinWidth(width);
  return column;
 }

 private TableCell<LineRow, Number> percentCell() {
  return new TableCell<>() {
   @Override protected void updateItem(Number value, boolean empty) {
    super.updateItem(value, empty);
    setText(empty || value == null ? null : String.format("%.2f %%", value.doubleValue()));
    setAlignment(Pos.CENTER);
   }
  };
 }

 private TableCell<LineRow, Number> moneyCell(boolean bold) {
  return new TableCell<>() {
   @Override protected void updateItem(Number value, boolean empty) {
    super.updateItem(value, empty);
    setText(empty || value == null ? null : String.format("%,.2f", value.doubleValue()));
    setAlignment(Pos.CENTER_RIGHT);
    getStyleClass().remove("quotation-amount-strong");
    if (!empty && bold) getStyleClass().add("quotation-amount-strong");
   }
  };
 }

 private VBox quotationSection(String icon, String title, javafx.scene.Node content) {
  StackPane iconBox = new StackPane(IconFactory.icon(icon, 17));
  iconBox.getStyleClass().add("quotation-section-icon");
  Label heading = new Label(title);
  heading.getStyleClass().add("quotation-section-title");
  HBox header = new HBox(8, iconBox, heading);
  header.setAlignment(Pos.CENTER_LEFT);
  VBox card = new VBox(12, header, content);
  card.getStyleClass().add("quotation-section-card");
  return card;
 }

 private HBox quotationMetric(String icon, String title, Label value) {
  StackPane iconBox = new StackPane(IconFactory.icon(icon, 20));
  iconBox.getStyleClass().add("quotation-metric-icon");
  Label caption = new Label(title);
  caption.getStyleClass().add("quotation-metric-caption");
  value.getStyleClass().add("quotation-metric-value");
  HBox card = new HBox(10, iconBox, new VBox(2, caption, value));
  card.setAlignment(Pos.CENTER_LEFT);
  card.getStyleClass().add("quotation-mini-metric");
  return card;
 }

 private VBox quotationTotals(Label subtotal, Label discount, Label taxable, Label gst, Label grand) {
  GridPane grid = new GridPane();
  grid.setHgap(24);
  grid.setVgap(5);
  grid.addRow(0, new Label("•  Sub Total"), subtotal);
  grid.addRow(1, new Label("•  Discount"), discount);
  grid.addRow(2, new Label("•  Taxable Amount"), taxable);
  grid.addRow(3, new Label("•  Total GST"), gst);
  Separator separator = new Separator();
  grid.add(separator, 0, 4, 2, 1);
  Label grandTitle = new Label("Grand Total");
  grandTitle.getStyleClass().add("quotation-grand-title");
  grid.addRow(5, grandTitle, grand);
  GridPane.setHalignment(subtotal, javafx.geometry.HPos.RIGHT);
  GridPane.setHalignment(discount, javafx.geometry.HPos.RIGHT);
  GridPane.setHalignment(taxable, javafx.geometry.HPos.RIGHT);
  GridPane.setHalignment(gst, javafx.geometry.HPos.RIGHT);
  GridPane.setHalignment(grand, javafx.geometry.HPos.RIGHT);
  VBox box = new VBox(grid);
  box.getStyleClass().add("quotation-totals-card");
  return box;
 }

 private void updateQuotationTotals(List<LineRow> lines, Label subtotal, Label discount, Label taxable, Label gst, Label grand) {
  double gross = lines.stream().mapToDouble(x -> x.quantity.get() * x.rate.get()).sum();
  double discountValue = lines.stream().mapToDouble(x -> x.discountAmount.get()).sum();
  double taxableValue = lines.stream().mapToDouble(x -> x.taxable.get()).sum();
  double gstValue = lines.stream().mapToDouble(x -> x.gstAmount.get()).sum();
  double totalValue = lines.stream().mapToDouble(x -> x.total.get()).sum();
  subtotal.setText(fmt(gross));
  discount.setText("- " + fmt(discountValue));
  taxable.setText(fmt(taxableValue));
  gst.setText(fmt(gstValue));
  grand.setText(fmt(totalValue));
 }

 private double positiveNumber(String value, String field) {
  try {
   double number = Double.parseDouble(value == null ? "" : value.trim());
   if (!Double.isFinite(number) || number <= 0) throw new NumberFormatException();
   return number;
  } catch (NumberFormatException ex) {
   throw new IllegalArgumentException(field + " must be a number greater than zero.");
  }
 }

 private double percentage(String value, String field) {
  try {
   double number = Double.parseDouble(value == null || value.isBlank() ? "0" : value.trim());
   if (!Double.isFinite(number) || number < 0 || number > 100) throw new NumberFormatException();
   return number;
  } catch (NumberFormatException ex) {
   throw new IllegalArgumentException(field + " must be between 0 and 100.");
  }
 }

 private void saveQuote(QuoteRow old,Choice customer,LocalDate date,LocalDate valid,LocalDate follow,String salesperson,String source,String remarks,List<LineRow> lines) throws Exception {double gross=lines.stream().mapToDouble(x->x.quantity.get()*x.rate.get()).sum();double discountAmount=lines.stream().mapToDouble(x->x.discountAmount.get()).sum();double taxable=lines.stream().mapToDouble(x->x.taxable.get()).sum();double gstAmount=lines.stream().mapToDouble(x->x.gstAmount.get()).sum();double total=taxable+gstAmount;List<QuotationApiClient.LineDto> apiLines=lines.stream().map(x->new QuotationApiClient.LineDto(x.code,x.description.get(),x.quantity.get(),x.rate.get(),x.gst.get(),x.discount.get(),x.total.get())).toList();QuotationApiClient.QuoteDto saved=quotationApi.save(new QuotationApiClient.SaveRequest(old==null?null:old.id,date.toString(),str(valid),customer.id,taxable,discountAmount,gstAmount,total,remarks,str(follow),salesperson,source,user(),apiLines));log(saved.id(),old==null?"CREATED":"UPDATED",remarks+" | Gross "+gross+" | Discount "+discountAmount);}

 private void configureResponsiveTable(){
    colNo.setMinWidth(130); colDate.setMinWidth(90); colCustomer.setMinWidth(140); colAmount.setMinWidth(100); colValid.setMinWidth(95); colStatus.setMinWidth(90); colFollowUp.setMinWidth(95); colConverted.setMinWidth(105); colSalesperson.setMinWidth(105); colCreatedBy.setMinWidth(100);
 }
 private void showDetails(QuoteRow q){selected=q;detailDrawer.setManaged(true);detailDrawer.setVisible(true);mainSplit.setDividerPositions(.79);lblDetailNo.setText(q.no.get());lblDetailDate.setText(q.date.get());lblDetailStatus.setText(q.status.get());lblDetailCustomer.setText(q.customer.get());lblDetailContact.setText(q.phone+"\n"+q.email+"\n"+q.gstin);lblDetailAmount.setText(fmt(q.amount.get()));lblDetailDiscount.setText(fmt(q.discount));lblDetailValid.setText(q.valid.get());lblDetailFollow.setText(q.followUp.get());lblDetailConverted.setText(q.converted.get().isBlank()?"—":q.converted.get());txtDetailNotes.setText(q.remarks);}
 @FXML private void closeDetails(){selected=null;configureResponsiveTable();detailDrawer.setMinWidth(320);detailDrawer.setPrefWidth(350);detailDrawer.setMaxWidth(410);detailDrawer.setManaged(false);detailDrawer.setVisible(false);mainSplit.setDividerPositions(1);table.getSelectionModel().clearSelection();}private QuoteRow req(){if(selected==null)new OwnedAlert(Alert.AlertType.WARNING,"Select a quotation first.").showAndWait();return selected;}
 @FXML private void editSelected(){QuoteRow q=req();if(q!=null)edit(q);}@FXML private void pdfSelected(){QuoteRow q=req();if(q!=null)openPdf(q);}@FXML private void emailSelected(){QuoteRow q=req();if(q!=null)sendEmail(q);}@FXML private void whatsappSelected(){QuoteRow q=req();if(q!=null)sendWhatsapp(q);}@FXML private void convertSelected(){QuoteRow q=req();if(q!=null)convert(q);}@FXML private void followUpSelected(){QuoteRow q=req();if(q!=null)followUp(q);}@FXML private void saveNotes(){QuoteRow q=req();if(q==null)return;try{quotationApi.notes(q.id,txtDetailNotes.getText());refresh();}catch(Exception e){error(e);}}
 /** Returns the same branded quotation used by download, email and WhatsApp actions. */
 private Path quotePdf(QuoteRow q)throws Exception{return InvoicePdfService.quotation(q.no.get());}
 private void openPdf(QuoteRow q){try{java.awt.Desktop.getDesktop().open(quotePdf(q).toFile());log(q.id,"PDF_OPENED",q.no.get());}catch(Exception e){error(e);}}
 private void sendEmail(QuoteRow q){try{if(q.email.isBlank())throw new IllegalStateException("Customer email is missing");EmailService.send(q.email,"Quotation "+q.no.get(),"Please find our quotation attached.",quotePdf(q));quotationApi.markSent(q.id,"EMAIL");comm(q,"EMAIL",q.email,"SENT",null);refresh();}catch(Exception e){comm(q,"EMAIL",q.email,"FAILED",e.getMessage());error(e);}}
 private void sendWhatsapp(QuoteRow q){try{String phone=q.phone.replaceAll("\\D","");if(phone.length()==10)phone="91"+phone;if(phone.isBlank())throw new IllegalStateException("Customer mobile number is missing");WhatsappService.openWhatsappWithMessage(phone,PaymentMessageService.quotationMessage(q.id),quotePdf(q),PaymentMessageService.configuredQrPath());quotationApi.markSent(q.id,"WHATSAPP");comm(q,"WHATSAPP",phone,"SENT",null);refresh();info("WhatsApp is ready. The quotation PDF and configured UPI QR are on the clipboard for attachment.");}catch(Exception e){error(e);}}
 private void followUp(QuoteRow q){DatePicker date=new DatePicker(q.followDate==null?BusinessClock.today().plusDays(1):q.followDate);Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Create Follow Up");TextArea notes=new TextArea("Follow up for "+q.no.get());VBox box=new VBox(8,new Label("Follow-up date"),date,new Label("Notes"),notes);d.getDialogPane().setContent(box);d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);d.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{try{quotationApi.followUp(q.id,date.getValue().toString(),notes.getText());refresh();}catch(Exception e){error(e);}});}
 private void convert(QuoteRow q){if(q.converted.get()!=null&&!q.converted.get().isBlank()){info("Already converted to "+q.converted.get());return;}if(!confirm("Convert "+q.no.get()+" to a sales invoice and reduce stock?"))return;try{String invoice=quotationApi.convert(q.id,user());log(q.id,"CONVERTED",invoice);refresh();info("Sales invoice "+invoice+" created.");}catch(Exception e){error(e);}}
 private void duplicate(QuoteRow q){if(!confirm("Duplicate "+q.no.get()+"?"))return;try{quotationApi.duplicate(q.id,user());refresh();}catch(Exception e){error(e);}}
 private void delete(QuoteRow q){if("ACCEPTED".equalsIgnoreCase(q.status.get())){info("Accepted quotations cannot be deleted. Convert, duplicate, or retain the quotation for audit history.");return;}if(!confirm("Delete "+q.no.get()+"?"))return;try{quotationApi.delete(q.id);refresh();closeDetails();}catch(Exception e){error(e);}}
 @FXML private void toggleAdvanced(){advancedFilters.setManaged(btnAdvanced.isSelected());advancedFilters.setVisible(btnAdvanced.isSelected());}@FXML private void resetFilters(){txtSearch.clear();txtNumber.clear();txtAmountFrom.clear();txtAmountTo.clear();dpFrom.setValue(BusinessClock.today().minusDays(6));dpTo.setValue(BusinessClock.today());dpValid.setValue(null);cmbCustomer.setValue("All customers");cmbStatus.setValue("All");cmbSalesperson.setValue("All");cmbFollowUp.setValue("All");cmbSource.setValue("All");applyFilters();}
 private void renderChips(){activeFilterChips.getChildren().clear();chip("Status",cmbStatus.getValue());chip("Customer",cmbCustomer.getValue());chip("Follow Up",cmbFollowUp.getValue());chip("Source",cmbSource.getValue());}private void chip(String n,String v){if(v==null||v.startsWith("All"))return;Label l=new Label(n+": "+v);l.getStyleClass().add("filter-chip");activeFilterChips.getChildren().add(l);}
 @FXML private void saveCurrentView(){TextInputDialog d=new OwnedTextInputDialog();d.setHeaderText("Save current quotation filters");d.setContentText("View name:");d.showAndWait().filter(x->!x.isBlank()).ifPresent(name->{String data=String.join("|",safe(txtNumber.getText()),safe(cmbCustomer.getValue()),str(dpFrom.getValue()),str(dpTo.getValue()),safe(cmbStatus.getValue()),str(dpValid.getValue()),safe(cmbSalesperson.getValue()),safe(txtAmountFrom.getText()),safe(txtAmountTo.getText()),safe(cmbFollowUp.getValue()),safe(cmbSource.getValue()));try{Integer uid=SessionService.current()==null?null:SessionService.current().getId();supportApi.saveView(uid,"QUOTATION_REGISTER",name,data);loadSavedViews();}catch(Exception e){error(e);}});}
 private void loadSavedViews(){savedViewsMenu.getItems().clear();try{Integer uid=SessionService.current()==null?null:SessionService.current().getId();for(SupportApiClient.SavedView v:supportApi.savedViews("QUOTATION_REGISTER",uid)){MenuItem i=new MenuItem(v.name());i.setOnAction(e->applySaved(v.data()));savedViewsMenu.getItems().add(i);}}catch(Exception ignored){}if(savedViewsMenu.getItems().isEmpty())savedViewsMenu.getItems().add(new MenuItem("No saved views"));}private void applySaved(String d){String[]x=d.split("\\|",-1);if(x.length<11)return;txtNumber.setText(x[0]);cmbCustomer.setValue(x[1]);dpFrom.setValue(parseDate(x[2]));dpTo.setValue(parseDate(x[3]));cmbStatus.setValue(x[4]);dpValid.setValue(parseDate(x[5]));cmbSalesperson.setValue(x[6]);txtAmountFrom.setText(x[7]);txtAmountTo.setText(x[8]);cmbFollowUp.setValue(x[9]);cmbSource.setValue(x[10]);applyFilters();}
 @FXML private void exportExcel(){File f=choose("Quotation_Register.xlsx","Excel","*.xlsx");if(f==null)return;try(Workbook w=new XSSFWorkbook();FileOutputStream o=new FileOutputStream(f)){Sheet s=w.createSheet("Quotations");String[]h={"Quotation","Date","Customer","Amount","Valid Upto","Status","Follow Up","Converted To","Sales Person","Created By"};Row r=s.createRow(0);for(int i=0;i<h.length;i++)r.createCell(i).setCellValue(h[i]);int n=1;for(QuoteRow q:filtered){r=s.createRow(n++);String[]v={q.no.get(),q.date.get(),q.customer.get(),String.valueOf(q.amount.get()),q.valid.get(),q.status.get(),q.followUp.get(),q.converted.get(),q.salesperson.get(),q.createdBy.get()};for(int i=0;i<v.length;i++)r.createCell(i).setCellValue(v[i]);}w.write(o);}catch(Exception e){error(e);}}
 @FXML private void exportRegisterPdf(){File f=choose("Quotation_Register.pdf","PDF","*.pdf");if(f==null)return;try{org.example.service.BrandedRegisterPdfService.export(f.toPath(),"Quotation Register",new String[]{"Quotation","Date","Customer","Amount","Status"},filtered.stream().map(q->new String[]{q.no.get(),q.date.get(),q.customer.get(),fmt(q.amount.get()),q.status.get()}).toList(),new float[]{2,1.3f,3,1.5f,1.2f});}catch(Exception e){error(e);}}
 private File choose(String name,String label,String ext){FileChooser c=new FileChooser();c.setInitialFileName(name);c.getExtensionFilters().add(new FileChooser.ExtensionFilter(label,ext));return c.showSaveDialog(table.getScene().getWindow());}
 private List<Choice>loadChoices(){List<Choice>x=new ArrayList<>();try{for(Party p:masterApi.parties("CUSTOMER"))x.add(new Choice(p.getId(),p.getName()));}catch(Exception e){error(e);}return x;}private List<ItemChoice>loadItems(){List<ItemChoice>x=new ArrayList<>();try{for(Item i:masterApi.items())x.add(new ItemChoice(i.getItemCode(),i.getDescription(),i.getRemarks(),i.getSellingPrice(),i.getGst(),i.getDiscountPercent()));}catch(Exception e){error(e);}return x;}private List<LineRow>loadLines(int id){List<LineRow>x=new ArrayList<>();try{for(QuotationApiClient.LineDto l:quotationApi.lines(id))x.add(new LineRow(l.code(),l.description(),l.quantity(),l.rate(),l.gst(),l.discount()));}catch(Exception e){error(e);}return x;}
 private void log(int id,String action,String detail){try{supportApi.activity("QUOTATION",id,action,detail,user());}catch(Exception ignored){}}private void comm(QuoteRow q,String channel,String recipient,String status,String error){try{supportApi.communication(new SupportApiClient.CommunicationRequest("QUOTATION",q.id,channel,recipient,"Quotation "+q.no.get(),status,error,user()));}catch(Exception ignored){}}
 private String user(){return SessionService.current()==null?"System":SessionService.current().getFullName();}private String fmt(double v){return money.format(v).replace("₹","₹ ");}private String safe(String v){return v==null?"":v;}private String low(String v){return safe(v).toLowerCase(Locale.ROOT);}private String str(Object v){return v==null?"":v.toString();}private double num(String v,double d){try{return v==null||v.isBlank()?d:Double.parseDouble(v.replace(",",""));}catch(Exception e){return d;}}private LocalDate parseDate(String v){try{return v.isBlank()?null:LocalDate.parse(v);}catch(Exception e){return null;}}private boolean confirm(String m){return new OwnedAlert(Alert.AlertType.CONFIRMATION,m,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}private void info(String m){new OwnedAlert(Alert.AlertType.INFORMATION,m).showAndWait();}private void error(Exception e){e.printStackTrace();new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?"Operation failed":e.getMessage()).showAndWait();}
 static final class Choice{final int id;final String name;Choice(int i,String n){id=i;name=n;}public String toString(){return name;}}static final class ItemChoice{final String code,description,remarks;final double rate,gst,discount;ItemChoice(String c,String d,String m,double r,double g,double disc){code=c;description=d==null?"":d;remarks=m==null?"":m;rate=r;gst=g;discount=disc;}public String toString(){if(remarks.isBlank())return description;if(description.isBlank())return remarks;return remarks+" • "+description;}}static final class LineRow{final String code;final SimpleStringProperty description;final SimpleDoubleProperty quantity,rate,gst,discount,discountAmount,taxable,gstAmount,total;LineRow(String c,String d,double q,double r,double g){this(c,d,q,r,g,0);}LineRow(String c,String d,double q,double r,double g,double disc){code=c;description=new SimpleStringProperty(d);quantity=new SimpleDoubleProperty(q);rate=new SimpleDoubleProperty(r);gst=new SimpleDoubleProperty(g);discount=new SimpleDoubleProperty(disc);double gross=q*r;double discountValue=gross*disc/100.0;discountAmount=new SimpleDoubleProperty(discountValue);taxable=new SimpleDoubleProperty(gross-discountValue);gstAmount=new SimpleDoubleProperty(taxable.get()*g/100.0);total=new SimpleDoubleProperty(taxable.get()+gstAmount.get());}}
 public static final class QuoteRow{final int id,customerId;final SimpleStringProperty no,date,customer,valid,status,followUp,converted,salesperson,createdBy;final SimpleDoubleProperty amount;final LocalDate quoteDate,validDate,followDate;final String phone,email,gstin,source,remarks;final double discount;QuoteRow(QuotationApiClient.QuoteDto d){id=d.id();customerId=d.customerId();no=new SimpleStringProperty(d.no());date=new SimpleStringProperty(d.date());quoteDate=parse(d.date());customer=new SimpleStringProperty(d.customer());validDate=parse(d.valid());valid=new SimpleStringProperty(validDate==null?"":validDate.toString());status=new SimpleStringProperty(d.status());followDate=parse(d.followUp());followUp=new SimpleStringProperty(followDate==null?"—":followDate.toString());converted=new SimpleStringProperty(s(d.converted()));salesperson=new SimpleStringProperty(s(d.salesperson()));createdBy=new SimpleStringProperty(s(d.createdBy()));amount=new SimpleDoubleProperty(d.amount());phone=s(d.phone());email=s(d.email());gstin=s(d.gstin());source=s(d.source());remarks=s(d.remarks());discount=d.discount();}static LocalDate parse(String v){try{return v==null||v.isBlank()?null:LocalDate.parse(v.substring(0,Math.min(10,v.length())));}catch(Exception e){return null;}}static String s(String v){return v==null?"":v;}}


    private void configureUiIcons() {
        pageIcon.getChildren().setAll(IconFactory.icon("quotation", 24));
        totalMetricIcon.getChildren().setAll(IconFactory.icon("quotation", 22));
        pendingMetricIcon.getChildren().setAll(IconFactory.icon("reminder", 22));
        acceptedMetricIcon.getChildren().setAll(IconFactory.icon("complete", 22));
        expiredMetricIcon.getChildren().setAll(IconFactory.icon("cancel", 22));
        conversionMetricIcon.getChildren().setAll(IconFactory.icon("report", 22));
        averageMetricIcon.getChildren().setAll(IconFactory.icon("report", 22));

        applyButtonIcon(btnNewQuotation, "add");
        applyButtonIcon(btnReset, "reset");
        applyButtonIcon(btnRefresh, "refresh");
        applyButtonIcon(btnSaveView, "save");
        applyButtonIcon(btnApplyFilters, "filter");
        applyButtonIcon(btnExportExcel, "excel");
        applyButtonIcon(btnExportPdf, "pdf");
        applyButtonIcon(btnTodayRange, "calendar");
        applyButtonIcon(btnYesterdayRange, "history");
        applyButtonIcon(btnSevenDaysRange, "calendar");
        applyButtonIcon(btnThirtyDaysRange, "calendar");
        applyButtonIcon(btnCustomRange, "calendar");
        btnAdvanced.setGraphic(IconFactory.icon("filter", 15));
        btnAdvanced.getProperties().put("erp-icon-preserve", true);
        savedViewsMenu.setGraphic(IconFactory.icon("save", 15));
        savedViewsMenu.getProperties().put("erp-icon-preserve", true);
    }

    private void applyButtonIcon(ButtonBase button, String semantic) {
        if (button == null) return;
        button.setGraphic(IconFactory.icon(semantic, 15));
        button.getProperties().put("erp-icon-preserve", true);
    }

    private void configureEmptyState() {
        VBox empty = new VBox(8,
                IconFactory.icon("quotation", 34),
                new Label("No quotations found"),
                new Label("Create a quotation or adjust the active filters."));
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("quotation-empty-state");
        empty.getChildren().get(1).getStyleClass().add("quotation-empty-title");
        empty.getChildren().get(2).getStyleClass().add("quotation-empty-message");
        table.setPlaceholder(empty);
    }

    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colNo, "document");
        IconFactory.applyTableHeaderIcon(colDate, "calendar");
        IconFactory.applyTableHeaderIcon(colCustomer, "customer");
        IconFactory.applyTableHeaderIcon(colValid, "calendar");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colFollowUp, "reminder");
        IconFactory.applyTableHeaderIcon(colConverted, "document");
        IconFactory.applyTableHeaderIcon(colSalesperson, "user");
        IconFactory.applyTableHeaderIcon(colCreatedBy, "user");
        IconFactory.applyTableHeaderIcon(colAmount, "currency");
        IconFactory.applyTableHeaderIcon(colActions, "actions");
    }
}
