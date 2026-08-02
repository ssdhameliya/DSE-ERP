package org.example.controller;


import org.example.util.IconFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.database.DatabaseManager;
import org.example.navigation.NavigationManager;
import java.sql.*;
import java.util.*;

public class CommunicationCenterController {
    @FXML private Label lblTotal,lblSuccess,lblFailed,lblChannels;
    @FXML private TextField txtSearch; @FXML private ComboBox<String> cmbChannel,cmbStatus;
    @FXML private TableView<Row> table; @FXML private TableColumn<Row,String> colTime,colEntity,colChannel,colRecipient,colSubject,colStatus,colError,colUser;
    private List<Row> all=List.of();
    @FXML public void initialize(){
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        configureExplicitTableHeaderIcons();colTime.setCellValueFactory(v->v.getValue().time);colEntity.setCellValueFactory(v->v.getValue().entity);colChannel.setCellValueFactory(v->v.getValue().channel);colRecipient.setCellValueFactory(v->v.getValue().recipient);colSubject.setCellValueFactory(v->v.getValue().subject);colStatus.setCellValueFactory(v->v.getValue().status);colError.setCellValueFactory(v->v.getValue().error);colUser.setCellValueFactory(v->v.getValue().user);cmbChannel.getItems().setAll("All Channels","EMAIL","WHATSAPP");String requested=CommunicationScreenContext.take();cmbChannel.setValue(requested==null||requested.isBlank()?"All Channels":requested);cmbStatus.getItems().setAll("All Statuses","SENT","FAILED");cmbStatus.setValue("All Statuses");txtSearch.textProperty().addListener((o,a,b)->filter());cmbChannel.valueProperty().addListener((o,a,b)->filter());cmbStatus.valueProperty().addListener((o,a,b)->filter());refresh();}
    @FXML public void refresh(){List<Row>x=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM communication_log ORDER BY id DESC")){while(r.next())x.add(new Row(r.getString("created_at"),r.getString("entity_type")+" #"+r.getInt("entity_id"),r.getString("channel"),r.getString("recipient"),r.getString("subject"),r.getString("status"),r.getString("error_message"),r.getString("created_by")));}catch(Exception e){new Alert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}all=x;lblTotal.setText(String.valueOf(x.size()));lblSuccess.setText(String.valueOf(x.stream().filter(r->!r.status.get().equals("FAILED")).count()));lblFailed.setText(String.valueOf(x.stream().filter(r->r.status.get().equals("FAILED")).count()));long email=x.stream().filter(r->r.channel.get().equals("EMAIL")).count();lblChannels.setText(email+" / "+(x.size()-email));filter();}
    private void filter(){String q=txtSearch.getText()==null?"":txtSearch.getText().toLowerCase();table.getItems().setAll(all.stream().filter(r->q.isBlank()||(r.entity.get()+r.recipient.get()+r.subject.get()).toLowerCase().contains(q)).filter(r->cmbChannel.getValue().startsWith("All")||r.channel.get().equals(cmbChannel.getValue())).filter(r->cmbStatus.getValue().startsWith("All")||r.status.get().equals(cmbStatus.getValue())).toList());}
    @FXML private void openEmailSettings(){NavigationManager.getInstance().loadPage("/fxml/pages/EmailSettings.fxml");}
    public static final class Row{final SimpleStringProperty time,entity,channel,recipient,subject,status,error,user;Row(String a,String b,String c,String d,String e,String f,String g,String h){time=new SimpleStringProperty(a);entity=new SimpleStringProperty(b);channel=new SimpleStringProperty(c);recipient=new SimpleStringProperty(d==null?"":d);subject=new SimpleStringProperty(e==null?"":e);status=new SimpleStringProperty(f);error=new SimpleStringProperty(g==null?"":g);user=new SimpleStringProperty(h==null?"":h);}}


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colTime, "calendar");
        IconFactory.applyTableHeaderIcon(colEntity, "document");
        IconFactory.applyTableHeaderIcon(colChannel, "communication");
        IconFactory.applyTableHeaderIcon(colRecipient, "customer");
        IconFactory.applyTableHeaderIcon(colSubject, "document");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colError, "error");
        IconFactory.applyTableHeaderIcon(colUser, "user");
    }
}
