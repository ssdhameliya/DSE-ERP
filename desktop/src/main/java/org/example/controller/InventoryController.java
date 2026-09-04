package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;

import org.example.util.IconFactory;
import org.example.util.RegisterDetailDrawer;
import org.example.util.RegisterUiSupport;
import org.example.util.OperationalUiSupport;
import org.example.util.ScreenRefreshPolicy;
import org.example.util.PopupTableWorkspace;
import org.example.util.SemanticTableCells;
import org.example.navigation.ScreenLifecycle;
import org.example.util.FxDebouncer;
import org.example.util.UiTaskExecutor;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.beans.property.*;import javafx.collections.FXCollections;import javafx.fxml.FXML;import javafx.geometry.Pos;import javafx.scene.chart.PieChart;import javafx.scene.control.*;import javafx.stage.FileChooser;import javafx.stage.Modality;import javafx.stage.Stage;import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.example.model.Item;import org.example.service.*;import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;import org.example.api.operations.OperationsApiClient;import org.example.config.ConfigManager;import java.io.*;import java.text.NumberFormat;import java.time.LocalDate;import java.util.*;
public class InventoryController implements ScreenLifecycle {
 @FXML private TextField txtSearch;@FXML private Button btnRefresh;@FXML private ComboBox<String>cmbCategory,cmbStatus;@FXML private TableView<Item>tableItems;@FXML private TableColumn<Item,String>colCode,colDescription,colCategory,colHsn,colUnit,colStatus;@FXML private TableColumn<Item,Double>colGst;@FXML private TableColumn<Item,Double>colStock,colReserved,colAvailable,colMinimum,colValue;@FXML private TableColumn<Item,Void>colActions;@FXML private Label lblTotalItems,lblInStock,lblLowStock,lblOutStock,lblStockValue,lblSelectedItem,lblSelectedDetail,lblRecordCount;@FXML private PieChart categoryChart;@FXML private StackPane inventoryPageIcon,inventoryTotalIcon,inventoryStockIcon,inventoryLowIcon,inventoryOutIcon,inventoryValueIcon;
 private final ItemService service=new ItemService();private final OperationsApiClient operationsApi=new OperationsApiClient();private final FxDebouncer searchDebouncer=new FxDebouncer(java.time.Duration.ofMillis(220));private final NumberFormat money=NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));private List<Item>all=List.of();private Item selected;private Item detailItem;private RegisterDetailDrawer detailDrawer;private String pendingSelectItemCode;
 @FXML public void initialize(){
        installKpiIcons();colCode.setCellValueFactory(v->new SimpleStringProperty(v.getValue().getItemCode()));colDescription.setCellValueFactory(v->new SimpleStringProperty(v.getValue().getDescription()));colCategory.setCellValueFactory(v->new SimpleStringProperty(s(v.getValue().getCategory())));colHsn.setCellValueFactory(v->new SimpleStringProperty(s(v.getValue().getHsn())));colUnit.setCellValueFactory(v->new SimpleStringProperty(s(v.getValue().getUnit())));colGst.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getGst()).asObject());colGst.setCellFactory(x->numberCell());colStock.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getOpeningStock()).asObject());colReserved.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getReservedStock()).asObject());colAvailable.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getAvailableStock()).asObject());colMinimum.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getMinimumStock()).asObject());colValue.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getOpeningStock()*v.getValue().getPurchasePrice()).asObject());colStatus.setCellValueFactory(v->new SimpleStringProperty(status(v.getValue())));for(TableColumn<Item,Double>c:List.of(colStock,colReserved,colAvailable,colMinimum))c.setCellFactory(x->numberCell());colValue.setCellFactory(x->moneyCell());colStatus.setCellFactory(x->statusCell());setupActions();configureTableInteractions();cmbStatus.getItems().setAll("All Status","In Stock","Low Stock","Out of Stock");cmbStatus.setValue("All Status");txtSearch.textProperty().addListener((o,a,b)->searchDebouncer.submit(this::filter));cmbCategory.valueProperty().addListener((o,a,b)->filter());cmbStatus.valueProperty().addListener((o,a,b)->filter());tableItems.getSelectionModel().selectedItemProperty().addListener((o,a,b)->selected=b);installDetailDrawer();refresh();}

 private void installKpiIcons(){if(inventoryPageIcon!=null)inventoryPageIcon.getChildren().setAll(IconFactory.icon("inventory",24));setKpiIcon(inventoryTotalIcon,"item");setKpiIcon(inventoryStockIcon,"complete");setKpiIcon(inventoryLowIcon,"reminder");setKpiIcon(inventoryOutIcon,"error");setKpiIcon(inventoryValueIcon,"currency");}
 private void setKpiIcon(StackPane pane,String semantic){if(pane!=null)pane.getChildren().setAll(IconFactory.compactIcon(semantic,22));}

 /** Standard register contract: row click views details; edit/adjust/history remain explicit actions. */
 private void configureTableInteractions(){
  tableItems.setRowFactory(table->{
   TableRow<Item> row=new TableRow<>();
   MenuItem view=new MenuItem("View Item",IconFactory.compactIcon("view",16)),
       edit=new MenuItem("Edit Item",IconFactory.compactIcon("edit",16)),
       adjust=new MenuItem("Adjust Stock",IconFactory.compactIcon("adjust",16)),
       history=new MenuItem("View Stock History",IconFactory.compactIcon("history",16)),
       delete=new MenuItem("Delete Item",IconFactory.compactIcon("delete",16));
   ContextMenu menu=new ContextMenu(view,edit,adjust,history,new SeparatorMenuItem(),delete);IconFactory.decorateActionMenu(menu);
   view.setOnAction(e->{selectRow(row);showDetails(row.getItem());});
   edit.setOnAction(e->{selectRow(row);openItemDialog(row.getItem());});
   adjust.setOnAction(e->{selectRow(row);adjust(row.getItem());});
   history.setOnAction(e->{selectRow(row);history(row.getItem());});
   delete.setOnAction(e->{selectRow(row);deleteItem(row.getItem());});
   row.setOnContextMenuRequested(e->{if(row.isEmpty()){menu.hide();return;}selectRow(row);menu.show(row,e.getScreenX(),e.getScreenY());e.consume();});
   row.setOnMouseClicked(e->{if(row.isEmpty()||e.getButton()!=MouseButton.PRIMARY||e.getClickCount()!=1||RegisterUiSupport.isInteractiveTableTarget(e.getPickResult().getIntersectedNode(),row))return;Item clicked=row.getItem();if(detailDrawer!=null&&detailDrawer.isOpen()&&detailItem==clicked)closeDetails();else{selectRow(row);showDetails(clicked);}e.consume();});
   return row;
  });
 }

 private void selectRow(TableRow<Item> row){tableItems.getSelectionModel().select(row.getItem());tableItems.requestFocus();}

 private void openItemDialog(Item item){
  try{
   FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/Itemdialog.fxml"));
   Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);
   ItemDialogController controller=loader.getController();
   if(item!=null)controller.setItem(item);
   Stage stage=new Stage();
   PlatformUiSupport.configureDialogStage(stage, tableItems, item==null?"Add Item":"Edit Item", false);
   Scene scene=new Scene(root);
   ThemeManager.applyTheme(scene);
   stage.setScene(scene);
   stage.showAndWait();
   Item saved=controller.getSavedResult();
   if(saved!=null)pendingSelectItemCode=saved.getItemCode();
   refresh();
  }catch(Exception e){error(e);}
 }

 private void deleteItem(Item item){
  if(item==null)return;
  Alert confirm=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Delete item '"+item.getDescription()+"'? This cannot be undone.",ButtonType.YES,ButtonType.NO);
  confirm.setHeaderText("Confirm item deletion");
  if(confirm.showAndWait().orElse(ButtonType.NO)==ButtonType.YES){
   UiTaskExecutor.submitAction(
    "inventory-delete-"+item.getItemCode(),
    ()->{service.delete(item);return true;},
    ignored->{NotificationService.add("Item '"+item.getDescription()+"' was deleted.");org.example.util.ToastManager.success(tableItems,"Item deleted","Item deleted successfully.");refresh();},
    failure->error(asException(failure))
   );
  }
 }
 private TableCell<Item,Double>numberCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e?null:String.format("%.2f",v));setAlignment(Pos.CENTER_RIGHT);}};}private TableCell<Item,Double>moneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e?null:money.format(v));setAlignment(Pos.CENTER_RIGHT);}};}private TableCell<Item,String>statusCell(){return SemanticTableCells.status("inventory");}
private void setupActions(){colActions.setCellFactory(c->new TableCell<>(){final MenuButton m=new MenuButton();{MenuItem v=new MenuItem("View Item",IconFactory.compactIcon("view",16)),eItem=new MenuItem("Edit Item",IconFactory.compactIcon("edit",16)),a=new MenuItem("Adjust Stock",IconFactory.compactIcon("adjust",16)),h=new MenuItem("View History",IconFactory.compactIcon("history",16));v.setOnAction(e->{Item i=getTableView().getItems().get(getIndex());tableItems.getSelectionModel().select(i);showDetails(i);});eItem.setOnAction(e->openItemDialog(getTableView().getItems().get(getIndex())));a.setOnAction(e->adjust(getTableView().getItems().get(getIndex())));h.setOnAction(e->history(getTableView().getItems().get(getIndex())));m.getItems().addAll(v,eItem,a,h);m.getStyleClass().add("row-actions");m.setGraphic(IconFactory.compactIcon("actions",16));m.setText("Actions");m.setContentDisplay(ContentDisplay.LEFT);m.setGraphicTextGap(6);m.setTooltip(new Tooltip("Actions"));IconFactory.decorateActionMenu(m);}protected void updateItem(Void v,boolean e){super.updateItem(v,e);setGraphic(e?null:m);}});}
 @FXML public void refresh(){
  btnRefresh.setDisable(true);
  org.example.util.OperationalUiSupport.showLoading(tableItems,"Loading inventory…");
  UiTaskExecutor.submitLatest(
   "inventory-load",
   ()->{List<Item> rows=service.getAll();return rows==null?List.<Item>of():List.copyOf(rows);},
   this::applyInventoryLoad,
   failure->{btnRefresh.setDisable(false);all=List.of();filter();org.example.util.OperationalUiSupport.showError(tableItems,"Inventory could not load",failure);error(asException(failure));}
  );
 }
 private void applyInventoryLoad(List<Item> rows){
   all=rows==null?List.of():List.copyOf(rows);
   cmbCategory.getItems().setAll("All Categories");
   cmbCategory.getItems().addAll(all.stream().map(Item::getCategory).filter(Objects::nonNull).distinct().sorted().toList());
   if(cmbCategory.getValue()==null)cmbCategory.setValue("All Categories");
   lblTotalItems.setText(String.valueOf(all.size()));
   lblInStock.setText(String.valueOf(all.stream().filter(i->i.getOpeningStock()>i.getMinimumStock()).count()));
   lblLowStock.setText(String.valueOf(all.stream().filter(i->i.getOpeningStock()>0&&i.getOpeningStock()<=i.getMinimumStock()).count()));
   lblOutStock.setText(String.valueOf(all.stream().filter(i->i.getOpeningStock()<=0).count()));
   lblStockValue.setText(money.format(all.stream().mapToDouble(i->i.getOpeningStock()*i.getPurchasePrice()).sum()));
   if(categoryChart!=null){
    categoryChart.setAnimated(false);
    if(org.example.util.PlatformUiSupport.isMac()){categoryChart.getData().clear();categoryChart.setVisible(false);categoryChart.setManaged(false);}
    else{Map<String,Double>cat=new HashMap<>();for(Item i:all)cat.merge(s(i.getCategory()).isBlank()?"Other":i.getCategory(),i.getOpeningStock()*i.getPurchasePrice(),Double::sum);categoryChart.getData().setAll(cat.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(7).map(e->new PieChart.Data(e.getKey(),e.getValue())).toList());}
   }
   filter();
   if(pendingSelectItemCode!=null&&!pendingSelectItemCode.isBlank()){selectSavedInventoryItem(pendingSelectItemCode);pendingSelectItemCode=null;}
   if(tableItems.getItems().isEmpty())org.example.util.OperationalUiSupport.showEmpty(tableItems,"No stock records found","Adjust the search or create an item in Item Master.");ScreenRefreshPolicy.markRefreshed("inventory");btnRefresh.setDisable(false);
 }
 private void selectSavedInventoryItem(String code){
  Item match=tableItems.getItems().stream().filter(i->code.equalsIgnoreCase(i.getItemCode())).findFirst().orElse(null);
  if(match==null){Item inAll=all.stream().filter(i->code.equalsIgnoreCase(i.getItemCode())).findFirst().orElse(null);if(inAll!=null){txtSearch.clear();cmbCategory.setValue("All Categories");cmbStatus.setValue("All Status");filter();match=tableItems.getItems().stream().filter(i->code.equalsIgnoreCase(i.getItemCode())).findFirst().orElse(null);}}
  if(match!=null){tableItems.getSelectionModel().select(match);tableItems.scrollTo(match);showDetails(match);}
 }
 private void filter(){String q=s(txtSearch.getText()).toLowerCase();String cat=cmbCategory.getValue(),st=cmbStatus.getValue();tableItems.getItems().setAll(all.stream().filter(i->q.isBlank()||(i.getItemCode()+i.getDescription()+s(i.getCategory())+s(i.getHsn())+s(i.getUnit())+i.getGst()+s(i.getLocation())).toLowerCase().contains(q)).filter(i->cat==null||cat.startsWith("All")||cat.equals(i.getCategory())).filter(i->st==null||st.startsWith("All")||st.equals(status(i))).toList());updateRecordCount();}
 private void updateRecordCount(){if(lblRecordCount!=null){int n=tableItems==null?0:tableItems.getItems().size();lblRecordCount.setText("Showing "+n+" Record"+(n==1?"":"s"));}}
 private String status(Item i){return i.getOpeningStock()<=0?"Out of Stock":i.getOpeningStock()<=i.getMinimumStock()?"Low Stock":"In Stock";}
 private void installDetailDrawer(){detailDrawer=new RegisterDetailDrawer();detailDrawer.setCloseAction(this::closeDetails);detailDrawer.attachBesideTable(tableItems);OperationalUiSupport.installEscapeClose(tableItems,detailDrawer::isOpen,this::closeDetails);}
 private void showDetails(Item i){if(i==null||detailDrawer==null)return;selected=i;detailItem=i;String stockStatus=status(i);detailDrawer.showRecord("Stock Details",i.getItemCode()+" • "+i.getDescription(),List.of(RegisterDetailDrawer.field("Item Code",i.getItemCode(),"identity"),RegisterDetailDrawer.field("Item Name",i.getDescription(),"item"),RegisterDetailDrawer.field("Category",i.getCategory(),"category"),RegisterDetailDrawer.field("HSN / SAC",i.getHsn(),"tax"),RegisterDetailDrawer.field("Unit",i.getUnit(),"unit"),RegisterDetailDrawer.field("GST %",String.format(Locale.ENGLISH,"%.2f",i.getGst()),"tax"),RegisterDetailDrawer.field("In Stock",String.format(Locale.ENGLISH,"%,.2f",i.getOpeningStock()),"quantity"),RegisterDetailDrawer.field("Reserved",String.format(Locale.ENGLISH,"%,.2f",i.getReservedStock()),"warning"),RegisterDetailDrawer.field("Available",String.format(Locale.ENGLISH,"%,.2f",i.getAvailableStock()),"complete"),RegisterDetailDrawer.field("Minimum Stock",String.format(Locale.ENGLISH,"%,.2f",i.getMinimumStock()),"minimum"),RegisterDetailDrawer.field("Stock Value",money.format(i.getOpeningStock()*i.getPurchasePrice()),"currency"),RegisterDetailDrawer.field("Location",i.getLocation(),"location"),RegisterDetailDrawer.field("Stock Status",stockStatus,RegisterDetailDrawer.statusSemantic(stockStatus))));Button edit=new Button("Edit Item");edit.getStyleClass().addAll("approved-button","approved-primary-button");edit.setGraphic(IconFactory.compactIcon("edit",14));edit.setOnAction(e->openItemDialog(i));Button adjustButton=new Button("Stock Adjustment");adjustButton.getStyleClass().addAll("approved-button","approved-secondary-button");adjustButton.setGraphic(IconFactory.compactIcon("adjust",14));adjustButton.setOnAction(e->adjust(i));Button historyButton=new Button("Stock History");historyButton.getStyleClass().addAll("approved-button","approved-secondary-button");historyButton.setGraphic(IconFactory.compactIcon("history",14));historyButton.setOnAction(e->history(i));detailDrawer.setActions(edit,adjustButton,historyButton);if(lblSelectedItem!=null)lblSelectedItem.setText(i.getItemCode()+" • "+i.getDescription());if(lblSelectedDetail!=null)lblSelectedDetail.setText("In stock: "+i.getOpeningStock()+" • Available: "+i.getAvailableStock());}
 private void closeDetails(){detailItem=null;if(detailDrawer!=null)detailDrawer.hideDrawer();if(tableItems!=null)tableItems.getSelectionModel().clearSelection();}

 @FXML private void adjustStock(){
  Dialog<Item> pick=new OwnedDialog<>(tableItems);
  pick.setTitle("Select Item");
  pick.setHeaderText("Choose an inventory item to adjust");
  pick.getDialogPane().getStyleClass().addAll("modern-dialog","stock-premium-dialog");
  pick.getDialogPane().setGraphic(IconFactory.icon("select",28));
  ComboBox<Item> box=new ComboBox<>(FXCollections.observableArrayList(all));
  box.setMaxWidth(Double.MAX_VALUE);
  box.setPromptText("Select item");
  box.setConverter(new javafx.util.StringConverter<>(){public String toString(Item i){return i==null?"":i.getItemCode()+" - "+i.getDescription();}public Item fromString(String s){return null;}});
  GridPane content=new GridPane();content.getStyleClass().add("inventory-stock-select-form");
  content.add(new Label("Item"),0,0);content.add(box,0,1);GridPane.setHgrow(box,javafx.scene.layout.Priority.ALWAYS);
  pick.getDialogPane().setContent(content);
  pick.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
  pick.setResultConverter(b->b==ButtonType.OK?box.getValue():null);
  pick.showAndWait().ifPresent(this::adjust);
} private void adjust(Item item){Dialog<ButtonType>d=new OwnedDialog<>(tableItems);d.setTitle("Stock Adjustment");d.setHeaderText(item.getItemCode()+" • Current "+item.getOpeningStock());d.getDialogPane().getStyleClass().addAll("modern-dialog","stock-premium-dialog");d.getDialogPane().setGraphic(IconFactory.icon("stock",30));ComboBox<String>type=new ComboBox<>(FXCollections.observableArrayList("ADD","REMOVE","SET"));type.setValue("ADD");TextField qty=new TextField(),reason=new TextField(),ref=new TextField();GridPane g=new GridPane();g.getStyleClass().add("inventory-stock-adjust-form");g.addRow(0,new Label("Type"),type);g.addRow(1,new Label("Quantity"),qty);g.addRow(2,new Label("Reason"),reason);g.addRow(3,new Label("Reference"),ref);qty.setPromptText("Enter quantity");reason.setPromptText("Required reason");ref.setPromptText("Optional reference");type.setMaxWidth(Double.MAX_VALUE);qty.setMaxWidth(Double.MAX_VALUE);reason.setMaxWidth(Double.MAX_VALUE);ref.setMaxWidth(Double.MAX_VALUE);d.getDialogPane().setContent(g);ButtonType apply=new ButtonType("Apply Adjustment",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(apply,ButtonType.CANCEL);Button applyButton=(Button)d.getDialogPane().lookupButton(apply);applyButton.setGraphic(IconFactory.compactIcon("adjust",16));Button cancelButton=(Button)d.getDialogPane().lookupButton(ButtonType.CANCEL);cancelButton.setGraphic(IconFactory.compactIcon("cancel",16));d.showAndWait().filter(b->b==apply).ifPresent(b->{try{double q=Double.parseDouble(qty.getText());if(q<0||reason.getText().isBlank())throw new IllegalArgumentException("Enter a valid quantity and reason");String user=SessionService.current()==null?"System":SessionService.current().getFullName();var request=new OperationsApiClient.StockAdjustmentRequest(item.getItemCode(),type.getValue(),q,reason.getText(),ref.getText(),user);UiTaskExecutor.submitAction("inventory-adjust-"+item.getItemCode(),()->{operationsApi.adjustStock(request);return true;},ignored->{NotificationService.add("Stock adjusted for "+item.getItemCode());org.example.util.ToastManager.success(tableItems,"Stock adjusted",item.getItemCode()+" stock was updated successfully.");refresh();},failure->error(asException(failure)));}catch(Exception e){error(e);}});}
 private void history(Item item){
  UiTaskExecutor.submitLatest(
   "inventory-history-"+item.getItemCode(),
   ()->operationsApi.stockHistory(item.getItemCode()),
   rows->showHistory(item,rows),
   failure->error(asException(failure))
  );
 }
 private void showHistory(Item item,List<OperationsApiClient.StockHistoryEntry> rows){
  TableView<String[]> t=new TableView<>();
  String[] names={"Date","Type","Quantity","Reason","Reference","User"};
  String[] semantics={"calendar","category","quantity","notes","reference","user"};
  for(int i=0;i<names.length;i++){
   final int n=i;
   TableColumn<String[],String> c=new TableColumn<>(names[i]);
   c.setCellValueFactory(v->new SimpleStringProperty(v.getValue()[n]));
   IconFactory.applyTableHeaderIcon(c,semantics[i]);
   if(i==2)c.setCellFactory(column->new TableCell<>(){
    @Override protected void updateItem(String value,boolean empty){
     super.updateItem(value,empty);setText(empty?null:value);
     getStyleClass().removeAll("erp-quantity-positive","erp-quantity-negative","erp-quantity-neutral");
     if(!empty&&value!=null){
      if(value.startsWith("+"))getStyleClass().add("erp-quantity-positive");
      else if(value.startsWith("-"))getStyleClass().add("erp-quantity-negative");
      else getStyleClass().add("erp-quantity-neutral");
     }
    }
   });
   t.getColumns().add(c);
  }
  for(var r:rows)t.getItems().add(new String[]{s(r.date()),s(r.type()),String.format("%+.2f",r.quantity()),s(r.reason()),s(r.reference()),s(r.user())});
  PopupTableWorkspace.prepareTable(t,"erp-table-profile-history");
  t.getStyleClass().addAll("professional-table","entity-table");
  t.setPrefHeight(410);
  String unit=s(item.getUnit());
  String suffix=unit.isBlank()?"":" "+unit;
  HBox metrics=PopupTableWorkspace.metricStrip(
   PopupTableWorkspace.metricCard("Current Stock",String.format(Locale.ENGLISH,"%,.2f%s",item.getOpeningStock(),suffix),"inventory"),
   PopupTableWorkspace.metricCard("Reserved",String.format(Locale.ENGLISH,"%,.2f%s",item.getReservedStock(),suffix),"warning"),
   PopupTableWorkspace.metricCard("Available",String.format(Locale.ENGLISH,"%,.2f%s",item.getAvailableStock(),suffix),"complete")
  );
  Label recordCount=PopupTableWorkspace.footerText(t.getItems().size()+" movement record"+(t.getItems().size()==1?"":"s"));
  VBox content=PopupTableWorkspace.content(metrics,t,recordCount);
  Dialog<ButtonType>d=new OwnedDialog<>(tableItems);
  d.setTitle("Stock History - "+item.getItemCode());
  d.setHeaderText(item.getDescription()+" • Complete stock movement history");
  PopupTableWorkspace.prepareDialog(d,960);
  d.getDialogPane().setContent(content);
  d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
  Button closeButton=(Button)d.getDialogPane().lookupButton(ButtonType.CLOSE);closeButton.setGraphic(IconFactory.compactIcon("cancel",16));
  d.showAndWait();

 }
 @FXML private void exportExcel(){org.example.service.PermissionService.require("INVENTORY.EXPORT", "Export Inventory");FileChooser f=new FileChooser();f.setInitialFileName("Stock_Register.xlsx");f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel","*.xlsx"));File out=f.showSaveDialog(tableItems.getScene().getWindow());if(out==null)return;try(Workbook w=new XSSFWorkbook();FileOutputStream o=new FileOutputStream(out)){Sheet sh=w.createSheet("Stock");String[]h={"Code","Item","Category","HSN","Unit","GST %","In Stock","Reserved","Available","Minimum","Value","Status"};Row r=sh.createRow(0);for(int i=0;i<h.length;i++)r.createCell(i).setCellValue(h[i]);int n=1;for(Item i:tableItems.getItems()){r=sh.createRow(n++);Object[]v={i.getItemCode(),i.getDescription(),i.getCategory(),i.getHsn(),i.getUnit(),i.getGst(),i.getOpeningStock(),i.getReservedStock(),i.getAvailableStock(),i.getMinimumStock(),i.getOpeningStock()*i.getPurchasePrice(),status(i)};for(int j=0;j<v.length;j++)if(v[j]instanceof Number z)r.createCell(j).setCellValue(z.doubleValue());else r.createCell(j).setCellValue(s(String.valueOf(v[j])));}w.write(o);}catch(Exception e){error(e);}}
 private Exception asException(Throwable failure){return failure instanceof Exception e?e:new RuntimeException(failure);}
 private String s(String v){return v==null?"":v;}private void warn(String m){new OwnedAlert(Alert.AlertType.WARNING,m).showAndWait();}private void error(Exception e){e.printStackTrace();new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?"Operation failed":e.getMessage()).showAndWait();}
 @Override public void onScreenShown(boolean reusedFromCache){org.example.util.OperationalUiSupport.focusWorkArea(tableItems);if(!reusedFromCache||ScreenRefreshPolicy.shouldRefresh("inventory",ScreenRefreshPolicy.Mode.WHEN_STALE))refresh();}
 @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("inventory-");searchDebouncer.cancel();}
}
