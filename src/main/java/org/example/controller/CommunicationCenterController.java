package org.example.controller;

import org.example.util.OwnedAlert;


import org.example.util.IconFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.concurrent.Task;
import org.example.service.EmailService;
import org.example.service.WhatsappService;
import org.example.database.DatabaseManager;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import java.sql.*;
import java.util.*;
import org.example.util.UiTaskExecutor;
import org.example.util.PerformanceMonitor;

public class CommunicationCenterController implements ScreenLifecycle {
    @FXML private Label lblTotal,lblSuccess,lblFailed,lblChannels;
    @FXML private StackPane communicationTotalIcon,communicationSuccessIcon,communicationFailedIcon,communicationChannelIcon;
    @FXML private TextField txtSearch; @FXML private ComboBox<String> cmbChannel,cmbStatus;
    @FXML private TableView<Row> table; @FXML private TableColumn<Row,String> colTime,colEntity,colChannel,colRecipient,colSubject,colStatus,colError,colUser; @FXML private TableColumn<Row,Void> colActions;
    private List<Row> all=List.of();
    @FXML public void initialize(){
        installKpiIcons();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        configureExplicitTableHeaderIcons();
        colTime.setCellValueFactory(v->v.getValue().time);
        colEntity.setCellValueFactory(v->v.getValue().entity);
        colChannel.setCellValueFactory(v->v.getValue().channel);
        colRecipient.setCellValueFactory(v->v.getValue().recipient);
        colSubject.setCellValueFactory(v->v.getValue().subject);
        colStatus.setCellValueFactory(v->v.getValue().status);
        colError.setCellValueFactory(v->v.getValue().error);
        colUser.setCellValueFactory(v->v.getValue().user);
        configureActions();

        // Configure both filters completely before any listener or filter pass runs.
        // Previously the requested channel triggered filter() while cmbStatus was still null,
        // which aborted controller initialization when the top Email/WhatsApp shortcut was used.
        cmbChannel.getItems().setAll("All Channels","EMAIL","WHATSAPP");
        cmbChannel.setValue("All Channels");
        cmbStatus.getItems().setAll("All Statuses","SENT","FAILED");
        cmbStatus.setValue("All Statuses");

        txtSearch.textProperty().addListener((o,a,b)->filter());
        cmbChannel.valueProperty().addListener((o,a,b)->filter());
        cmbStatus.valueProperty().addListener((o,a,b)->filter());
        refresh();
    }
    private void installKpiIcons(){setKpiIcon(communicationTotalIcon,"communication");setKpiIcon(communicationSuccessIcon,"complete");setKpiIcon(communicationFailedIcon,"error");setKpiIcon(communicationChannelIcon,"email");}
    private void setKpiIcon(StackPane pane,String semantic){if(pane!=null)pane.getChildren().setAll(IconFactory.compactIcon(semantic,22));}

    @FXML public void refresh(){
        UiTaskExecutor.submitLatest("communication-load", this::readRows, this::applyRows, error -> new OwnedAlert(Alert.AlertType.ERROR,error.getMessage()).showAndWait());
    }
    private List<Row> readRows() throws Exception {
        List<Row>x=new ArrayList<>();
        try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM communication_log ORDER BY id DESC")){
            while(r.next())x.add(new Row(r.getString("created_at"),r.getString("entity_type")+" #"+r.getInt("entity_id"),r.getString("channel"),r.getString("recipient"),r.getString("subject"),r.getString("status"),r.getString("error_message"),r.getString("created_by")));
        }
        return x;
    }
    private void applyRows(List<Row> x){
        long started=System.nanoTime(); all=x;lblTotal.setText(String.valueOf(x.size()));lblSuccess.setText(String.valueOf(x.stream().filter(r->!r.status.get().equals("FAILED")).count()));lblFailed.setText(String.valueOf(x.stream().filter(r->r.status.get().equals("FAILED")).count()));long email=x.stream().filter(r->r.channel.get().equals("EMAIL")).count();lblChannels.setText(email+" / "+(x.size()-email));filter();
        long ms=(System.nanoTime()-started)/1_000_000L;if(ms>=20)PerformanceMonitor.event("controller-phase","communication-apply | "+ms+" ms");
    }
    private void filter(){
        String q=txtSearch==null||txtSearch.getText()==null?"":txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        String channel=cmbChannel==null||cmbChannel.getValue()==null?"All Channels":cmbChannel.getValue();
        String status=cmbStatus==null||cmbStatus.getValue()==null?"All Statuses":cmbStatus.getValue();
        table.getItems().setAll(all.stream()
            .filter(r->q.isBlank()||(r.entity.get()+" "+r.recipient.get()+" "+r.subject.get()).toLowerCase(Locale.ROOT).contains(q))
            .filter(r->channel.startsWith("All")||r.channel.get().equalsIgnoreCase(channel))
            .filter(r->status.startsWith("All")||r.status.get().equalsIgnoreCase(status))
            .toList());
    }

    private void configureActions(){
        if(colActions==null)return;
        colActions.setCellFactory(c->new TableCell<>(){
            final Button resend=new Button("Resend",IconFactory.compactIcon("refresh",14));
            {resend.getStyleClass().addAll("approved-button","approved-primary-button","communication-resend-button");resend.setMinWidth(96);resend.setOnAction(e->{Row row=getTableRow().getItem();if(row!=null)resend(row);});}
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:resend);setAlignment(Pos.CENTER);}
        });
    }
    private void resend(Row row){
        Task<Void> task=new Task<>(){@Override protected Void call() throws Exception{
            String channel=row.channel.get();String recipient=row.recipient.get();String subject=row.subject.get().isBlank()?row.entity.get():row.subject.get();
            if("EMAIL".equalsIgnoreCase(channel)) EmailService.send(recipient,subject,"Resent from DSE ERP Communication Center.");
            else {String phone=recipient.replaceAll("\\D","");if(phone.length()==10)phone="91"+phone;WhatsappService.openWhatsappWithMessage(phone,subject,null);}
            try(Connection c=DatabaseManager.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO communication_log(entity_type,entity_id,channel,recipient,subject,status,created_by) VALUES(?,0,?,?,?,'SENT',?)")){ps.setString(1,"RESEND");ps.setString(2,channel);ps.setString(3,recipient);ps.setString(4,subject);ps.setString(5,"System");ps.executeUpdate();}
            return null;}};
        task.setOnSucceeded(e->{org.example.util.ToastManager.success(table,"Resent",row.channel.get()+" communication prepared successfully.");refresh();});
        task.setOnFailed(e->new OwnedAlert(Alert.AlertType.ERROR,task.getException()==null?"Resend failed":task.getException().getMessage()).showAndWait());
        Thread t=new Thread(task,"communication-resend");t.setDaemon(true);t.start();
    }

    @FXML private void openEmailSettings(){NavigationManager.getInstance().loadPage("/fxml/pages/EmailSettings.fxml");}
    public static final class Row{final SimpleStringProperty time,entity,channel,recipient,subject,status,error,user;Row(String a,String b,String c,String d,String e,String f,String g,String h){time=new SimpleStringProperty(a);entity=new SimpleStringProperty(b);channel=new SimpleStringProperty(c);recipient=new SimpleStringProperty(d==null?"":d);subject=new SimpleStringProperty(e==null?"":e);status=new SimpleStringProperty(f);error=new SimpleStringProperty(g==null?"":g);user=new SimpleStringProperty(h==null?"":h);}}



    private void applyRequestedChannel(String requested){
        String value=requested==null||requested.isBlank()?"All Channels":requested.toUpperCase(Locale.ROOT);
        if(cmbChannel!=null){cmbChannel.setValue(value);filter();}
    }
    @Override public void onScreenShown(boolean reusedFromCache){
        String requested=CommunicationScreenContext.take();
        applyRequestedChannel(requested);
        PerformanceMonitor.event("controller-phase","communication-channel-apply | channel="+(requested==null?"ALL":requested));
        if(all.isEmpty())refresh();
    }
    @Override public void onScreenHidden(){UiTaskExecutor.cancel("communication-load");}

    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colTime, "calendar");
        IconFactory.applyTableHeaderIcon(colEntity, "document");
        IconFactory.applyTableHeaderIcon(colChannel, "communication");
        IconFactory.applyTableHeaderIcon(colRecipient, "customer");
        IconFactory.applyTableHeaderIcon(colSubject, "document");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colError, "error");
        IconFactory.applyTableHeaderIcon(colUser, "user");
        IconFactory.applyTableHeaderIcon(colActions, "actions");
    }
}
