package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;

import org.example.util.IconFactory;
import org.example.util.ScreenRefreshPolicy;
import org.example.navigation.ScreenLifecycle;
import org.example.util.FxDebouncer;
import org.example.util.UiTaskExecutor;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.beans.property.*;import javafx.collections.FXCollections;import javafx.fxml.FXML;import javafx.geometry.Pos;import javafx.scene.chart.PieChart;import javafx.scene.control.*;import javafx.stage.FileChooser;import javafx.stage.Modality;import javafx.stage.Stage;import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.example.model.Item;import org.example.service.*;import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;import org.example.api.operations.OperationsApiClient;import org.example.config.ConfigManager;import java.io.*;import java.text.NumberFormat;import java.time.LocalDate;import java.util.*;
public class InventoryController implements ScreenLifecycle {
 @FXML private TextField txtSearch;@FXML private Button btnRefresh;@FXML private ComboBox<String>cmbCategory,cmbStatus;@FXML private TableView<Item>tableItems;@FXML private TableColumn<Item,String>colCode,colDescription,colCategory,colHsn,colUnit,colStatus;@FXML private TableColumn<Item,Double>colStock,colReserved,colAvailable,colMinimum,colValue;@FXML private TableColumn<Item,Void>colActions;@FXML private Label lblTotalItems,lblInStock,lblLowStock,lblOutStock,lblStockValue,lblSelectedItem,lblSelectedDetail,lblRecordCount;@FXML private PieChart categoryChart;@FXML private StackPane inventoryPageIcon,inventoryTotalIcon,inventoryStockIcon,inventoryLowIcon,inventoryOutIcon,inventoryValueIcon;
 private final ItemService service=new ItemService();private final OperationsApiClient operationsApi=new OperationsApiClient();private final FxDebouncer searchDebouncer=new FxDebouncer(java.time.Duration.ofMillis(220));private final NumberFormat money=NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));private List<Item>all=List.of();private Item selected;
 @FXML public void initialize(){
        installKpiIcons();configureExplicitTableHeaderIcons();colCode.setCellValueFactory(v->new SimpleStringProperty(v.getValue().getItemCode()));colDescription.setCellValueFactory(v->new SimpleStringProperty(v.getValue().getDescription()));colCategory.setCellValueFactory(v->new SimpleStringProperty(s(v.getValue().getCategory())));colHsn.setCellValueFactory(v->new SimpleStringProperty(s(v.getValue().getHsn())));colUnit.setCellValueFactory(v->new SimpleStringProperty(s(v.getValue().getUnit())));colStock.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getOpeningStock()).asObject());colReserved.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getReservedStock()).asObject());colAvailable.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getAvailableStock()).asObject());colMinimum.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getMinimumStock()).asObject());colValue.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().getOpeningStock()*v.getValue().getPurchasePrice()).asObject());colStatus.setCellValueFactory(v->new SimpleStringProperty(status(v.getValue())));for(TableColumn<Item,Double>c:List.of(colStock,colReserved,colAvailable,colMinimum))c.setCellFactory(x->numberCell());colValue.setCellFactory(x->moneyCell());colStatus.setCellFactory(x->statusCell());setupActions();configureTableInteractions();cmbStatus.getItems().setAll("All Status","In Stock","Low Stock","Out of Stock");cmbStatus.setValue("All Status");txtSearch.textProperty().addListener((o,a,b)->searchDebouncer.submit(this::filter));cmbCategory.valueProperty().addListener((o,a,b)->filter());cmbStatus.valueProperty().addListener((o,a,b)->filter());tableItems.getSelectionModel().selectedItemProperty().addListener((o,a,b)->showSelected(b));refresh();}

 private void installKpiIcons(){if(inventoryPageIcon!=null)inventoryPageIcon.getChildren().setAll(IconFactory.icon("inventory",24));setKpiIcon(inventoryTotalIcon,"item");setKpiIcon(inventoryStockIcon,"complete");setKpiIcon(inventoryLowIcon,"reminder");setKpiIcon(inventoryOutIcon,"error");setKpiIcon(inventoryValueIcon,"currency");}
 private void setKpiIcon(StackPane pane,String semantic){if(pane!=null)pane.getChildren().setAll(IconFactory.compactIcon(semantic,22));}

 /** Uses explicit row handlers so double-click editing and right-click actions are reliable. */
 private void configureTableInteractions(){
  tableItems.setRowFactory(table->{
   TableRow<Item> row=new TableRow<>();
   MenuItem add=new MenuItem("Add Item",IconFactory.compactIcon("add",16)),
       edit=new MenuItem("Edit Item",IconFactory.compactIcon("edit",16)),
       adjust=new MenuItem("Adjust Stock",IconFactory.compactIcon("adjust",16)),
       history=new MenuItem("View Stock History",IconFactory.compactIcon("history",16)),
       delete=new MenuItem("Delete Item",IconFactory.compactIcon("delete",16));
   ContextMenu menu=new ContextMenu(edit,adjust,history,new SeparatorMenuItem(),delete);
   add.setOnAction(e->openItemDialog(null));
   edit.setOnAction(e->{selectRow(row);openItemDialog(row.getItem());});
   adjust.setOnAction(e->{selectRow(row);adjust(row.getItem());});
   history.setOnAction(e->{selectRow(row);history(row.getItem());});
   delete.setOnAction(e->{selectRow(row);deleteItem(row.getItem());});
   row.setOnContextMenuRequested(e->{if(row.isEmpty()){menu.hide();return;}selectRow(row);menu.show(row,e.getScreenX(),e.getScreenY());e.consume();});
   row.setOnMouseClicked(e->{if(!row.isEmpty()&&e.getButton()==MouseButton.PRIMARY&&e.getClickCount()==2){selectRow(row);openItemDialog(row.getItem());e.consume();}});
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
   refresh();
  }catch(Exception e){error(e);}
 }

 private void deleteItem(Item item){
  if(item==null)return;
  Alert confirm=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Delete item '"+item.getDescription()+"'? This cannot be undone.",ButtonType.YES,ButtonType.NO);
  confirm.setHeaderText("Confirm item deletion");
  if(confirm.showAndWait().orElse(ButtonType.NO)==ButtonType.YES){
   try{service.delete(item.getItemCode());NotificationService.add("Item '"+item.getDescription()+"' was deleted.");new OwnedAlert(Alert.AlertType.INFORMATION,"Item deleted successfully.").showAndWait();refresh();}catch(Exception e){error(e);}
  }
 }
 private TableCell<Item,Double>numberCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e?null:String.format("%.2f",v));setAlignment(Pos.CENTER_RIGHT);}};}private TableCell<Item,Double>moneyCell(){return new TableCell<>(){protected void updateItem(Double v,boolean e){super.updateItem(v,e);setText(e?null:money.format(v));setAlignment(Pos.CENTER_RIGHT);}};}private TableCell<Item,String>statusCell(){return new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);getStyleClass().removeAll("pill-success","pill-warning","pill-danger");if(!e)getStyleClass().add("In Stock".equals(v)?"pill-success":"Low Stock".equals(v)?"pill-warning":"pill-danger");}};}
private void setupActions(){colActions.setCellFactory(c->new TableCell<>(){final MenuButton m=new MenuButton();{MenuItem a=new MenuItem("Adjust Stock",IconFactory.compactIcon("adjust",16)),h=new MenuItem("View History",IconFactory.compactIcon("history",16));a.setOnAction(e->adjust(getTableView().getItems().get(getIndex())));h.setOnAction(e->history(getTableView().getItems().get(getIndex())));m.getItems().addAll(a,h);m.getStyleClass().add("row-actions");m.setGraphic(IconFactory.compactIcon("actions",16));m.setText("Actions");m.setContentDisplay(ContentDisplay.LEFT);m.setGraphicTextGap(6);m.setTooltip(new Tooltip("Actions"));}protected void updateItem(Void v,boolean e){super.updateItem(v,e);setGraphic(e?null:m);}});}
 @FXML public void refresh(){
  btnRefresh.setDisable(true);
  try{
   List<Item> rows=service.getAll();all=rows==null?List.of():List.copyOf(rows);
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
   filter();ScreenRefreshPolicy.markRefreshed("inventory");
  }catch(Throwable failure){all=List.of();filter();error(failure instanceof Exception e?e:new RuntimeException(failure));}
  finally{btnRefresh.setDisable(false);}
 }
 private void filter(){String q=s(txtSearch.getText()).toLowerCase();String cat=cmbCategory.getValue(),st=cmbStatus.getValue();tableItems.getItems().setAll(all.stream().filter(i->q.isBlank()||(i.getItemCode()+i.getDescription()+s(i.getHsn())+s(i.getLocation())).toLowerCase().contains(q)).filter(i->cat==null||cat.startsWith("All")||cat.equals(i.getCategory())).filter(i->st==null||st.startsWith("All")||st.equals(status(i))).toList());updateRecordCount();}
 private void updateRecordCount(){if(lblRecordCount!=null){int n=tableItems==null?0:tableItems.getItems().size();lblRecordCount.setText("Showing "+n+" Record"+(n==1?"":"s"));}}
 private String status(Item i){return i.getOpeningStock()<=0?"Out of Stock":i.getOpeningStock()<=i.getMinimumStock()?"Low Stock":"In Stock";}private void showSelected(Item i){selected=i;if(i==null){lblSelectedItem.setText("No item selected");lblSelectedDetail.setText("");return;}lblSelectedItem.setText(i.getItemCode()+" • "+i.getDescription());lblSelectedDetail.setText("Category: "+s(i.getCategory())+"\nLocation: "+s(i.getLocation())+"\nIn stock: "+i.getOpeningStock()+" "+s(i.getUnit())+"\nReserved: "+i.getReservedStock()+"\nAvailable: "+i.getAvailableStock()+"\nMinimum: "+i.getMinimumStock());}
 @FXML private void adjustStock(){
  Dialog<Item> pick=new OwnedDialog<>();
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
  if(tableItems.getScene()!=null && tableItems.getScene().getWindow()!=null) pick.initOwner(tableItems.getScene().getWindow());
  pick.initModality(Modality.WINDOW_MODAL);
  pick.showAndWait().ifPresent(this::adjust);
}@FXML private void adjustSelectedStock(){if(selected==null)warn("Select an item first.");else adjust(selected);}
 private void adjust(Item item){Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Stock Adjustment");d.setHeaderText(item.getItemCode()+" • Current "+item.getOpeningStock());d.getDialogPane().getStyleClass().addAll("modern-dialog","stock-premium-dialog");d.getDialogPane().setGraphic(IconFactory.icon("stock",30));ComboBox<String>type=new ComboBox<>(FXCollections.observableArrayList("ADD","REMOVE","SET"));type.setValue("ADD");TextField qty=new TextField(),reason=new TextField(),ref=new TextField();GridPane g=new GridPane();g.getStyleClass().add("inventory-stock-adjust-form");g.addRow(0,new Label("Type"),type);g.addRow(1,new Label("Quantity"),qty);g.addRow(2,new Label("Reason"),reason);g.addRow(3,new Label("Reference"),ref);qty.setPromptText("Enter quantity");reason.setPromptText("Required reason");ref.setPromptText("Optional reference");type.setMaxWidth(Double.MAX_VALUE);qty.setMaxWidth(Double.MAX_VALUE);reason.setMaxWidth(Double.MAX_VALUE);ref.setMaxWidth(Double.MAX_VALUE);d.getDialogPane().setContent(g);ButtonType apply=new ButtonType("Apply Adjustment",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(apply,ButtonType.CANCEL);Button applyButton=(Button)d.getDialogPane().lookupButton(apply);applyButton.setGraphic(IconFactory.compactIcon("adjust",16));Button cancelButton=(Button)d.getDialogPane().lookupButton(ButtonType.CANCEL);cancelButton.setGraphic(IconFactory.compactIcon("cancel",16));d.showAndWait().filter(b->b==apply).ifPresent(b->{try{double q=Double.parseDouble(qty.getText());if(q<0||reason.getText().isBlank())throw new IllegalArgumentException("Enter a valid quantity and reason");String user=SessionService.current()==null?"System":SessionService.current().getFullName();operationsApi.adjustStock(new OperationsApiClient.StockAdjustmentRequest(item.getItemCode(),type.getValue(),q,reason.getText(),ref.getText(),user));NotificationService.add("Stock adjusted for "+item.getItemCode());refresh();}catch(Exception e){error(e);}});}
 @FXML private void viewSelectedHistory(){if(selected==null)warn("Select an item first.");else history(selected);}private void history(Item item){TableView<String[]>t=new TableView<>();String[]names={"Date","Type","Quantity","Reason","Reference","User"};for(int i=0;i<names.length;i++){final int n=i;TableColumn<String[],String>c=new TableColumn<>(names[i]);c.setCellValueFactory(v->new SimpleStringProperty(v.getValue()[n]));String[] semantics={"calendar","category","quantity","notes","reference","user"};IconFactory.applyTableHeaderIcon(c,semantics[i]);t.getColumns().add(c);}try{for(var r:operationsApi.stockHistory(item.getItemCode()))t.getItems().add(new String[]{s(r.date()),s(r.type()),String.format("%+.2f",r.quantity()),s(r.reason()),s(r.reference()),s(r.user())});}catch(Exception e){error(e);}Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Stock History - "+item.getItemCode());d.getDialogPane().getStyleClass().addAll("modern-dialog","stock-premium-dialog");d.getDialogPane().setGraphic(IconFactory.icon("history",30));d.setHeaderText(item.getDescription()+" • Complete stock movement history");d.getDialogPane().setPrefSize(940,520);t.getStyleClass().addAll("professional-table","entity-table","erp-table-profile-history");t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);d.getDialogPane().setContent(t);d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);Button closeButton=(Button)d.getDialogPane().lookupButton(ButtonType.CLOSE);closeButton.setGraphic(IconFactory.compactIcon("cancel",16));if(tableItems.getScene()!=null&&tableItems.getScene().getWindow()!=null)d.initOwner(tableItems.getScene().getWindow());d.initModality(Modality.WINDOW_MODAL);d.showAndWait();}
 @FXML private void exportExcel(){FileChooser f=new FileChooser();f.setInitialFileName("Stock_Register.xlsx");f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel","*.xlsx"));File out=f.showSaveDialog(tableItems.getScene().getWindow());if(out==null)return;try(Workbook w=new XSSFWorkbook();FileOutputStream o=new FileOutputStream(out)){Sheet sh=w.createSheet("Stock");String[]h={"Code","Item","Category","HSN","Unit","In Stock","Reserved","Available","Minimum","Value","Status"};Row r=sh.createRow(0);for(int i=0;i<h.length;i++)r.createCell(i).setCellValue(h[i]);int n=1;for(Item i:tableItems.getItems()){r=sh.createRow(n++);Object[]v={i.getItemCode(),i.getDescription(),i.getCategory(),i.getHsn(),i.getUnit(),i.getOpeningStock(),i.getReservedStock(),i.getAvailableStock(),i.getMinimumStock(),i.getOpeningStock()*i.getPurchasePrice(),status(i)};for(int j=0;j<v.length;j++)if(v[j]instanceof Number z)r.createCell(j).setCellValue(z.doubleValue());else r.createCell(j).setCellValue(s(String.valueOf(v[j])));}w.write(o);}catch(Exception e){error(e);}}
 private String s(String v){return v==null?"":v;}private void warn(String m){new OwnedAlert(Alert.AlertType.WARNING,m).showAndWait();}private void error(Exception e){e.printStackTrace();new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?"Operation failed":e.getMessage()).showAndWait();}


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colCode, "identity");
        IconFactory.applyTableHeaderIcon(colDescription, "item");
        IconFactory.applyTableHeaderIcon(colCategory, "category");
        IconFactory.applyTableHeaderIcon(colHsn, "tax");
        IconFactory.applyTableHeaderIcon(colUnit, "unit");
        IconFactory.applyTableHeaderIcon(colStock, "quantity");
        IconFactory.applyTableHeaderIcon(colReserved, "quantity");
        IconFactory.applyTableHeaderIcon(colAvailable, "quantity");
        IconFactory.applyTableHeaderIcon(colMinimum, "minimum");
        IconFactory.applyTableHeaderIcon(colValue, "currency");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colActions, "actions");
    }

 @Override public void onScreenShown(boolean reusedFromCache){if(!reusedFromCache||ScreenRefreshPolicy.shouldRefresh("inventory",ScreenRefreshPolicy.Mode.WHEN_STALE))refresh();}
 @Override public void onScreenHidden(){UiTaskExecutor.cancel("inventory-load");searchDebouncer.cancel();}
}
