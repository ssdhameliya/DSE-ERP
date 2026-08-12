package org.example.controller;
import javafx.fxml.FXML;import javafx.scene.control.*;import org.example.api.support.SupportApiClient;import org.example.navigation.NavigationManager;
public class PaymentHistoryController{
 @FXML private TableView<Row> table; @FXML private Label title; private int saleId; private final SupportApiClient api=new SupportApiClient();
 public void setSale(int id,String invoice){saleId=id;if(title!=null)title.setText("Payment History • "+invoice);load();}
 @FXML public void initialize(){if(saleId>0)load();}
 private void load(){if(table==null||saleId<=0)return;table.getItems().clear();try{for(var p:api.payments("SALE",saleId))table.getItems().add(new Row(p.date(),p.reference(),p.mode(),p.amount(),p.notes()));}catch(Exception ignored){}}
 @FXML private void close(){NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml");}
 public record Row(String date,String reference,String mode,double amount,String notes){}
}
