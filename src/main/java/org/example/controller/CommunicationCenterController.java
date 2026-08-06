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
import org.example.service.InvoicePdfService;
import org.example.service.SalesService;
import org.example.service.PurchaseService;
import java.nio.file.Path;
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
            while(r.next()){
                String type=r.getString("entity_type");
                int entityId=r.getInt("entity_id");
                String subject=r.getString("subject");
                x.add(new Row(r.getString("created_at"),documentLabel(c,type,entityId,subject),type,entityId,r.getString("channel"),r.getString("recipient"),subject,r.getString("status"),r.getString("error_message"),r.getString("created_by")));
            }
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

    private void configureDataCellIcons() {
        colTime.setCellFactory(c -> semanticTextCell(value -> "calendar"));
        colEntity.setCellFactory(c -> semanticTextCell(value -> {
            String v = value == null ? "" : value.toUpperCase(Locale.ROOT);
            if (v.startsWith("SAL-")) return "sale";
            if (v.startsWith("QT-")) return "quotation";
            if (v.startsWith("PUR-") || v.startsWith("PO-")) return "purchase";
            return "document";
        }));
        colChannel.setCellFactory(c -> semanticTextCell(value ->
            "WHATSAPP".equalsIgnoreCase(value) ? "whatsapp" : "email"));
        colRecipient.setCellFactory(c -> semanticTextCell(value -> "customer"));
        colSubject.setCellFactory(c -> semanticTextCell(value -> "document"));
        colStatus.setCellFactory(c -> semanticTextCell(value -> {
            String v = value == null ? "" : value.toUpperCase(Locale.ROOT);
            return v.contains("SENT") || v.contains("SUCCESS") ? "complete" : "error";
        }));
        colError.setCellFactory(c -> semanticTextCell(value ->
            value == null || value.isBlank() ? "status" : "warning"));
        colUser.setCellFactory(c -> semanticTextCell(value -> "user"));
    }

    private TableCell<Row, String> semanticTextCell(java.util.function.Function<String, String> semantic) {
        return new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(value);
                setGraphic(IconFactory.compactIcon(semantic.apply(value), 13));
                setContentDisplay(ContentDisplay.LEFT);
                setGraphicTextGap(7);
                setAlignment(Pos.CENTER_LEFT);
            }
        };
    }

    private void configureActions(){
        if(colActions==null)return;
        colActions.setCellFactory(c->new TableCell<>(){
            final Button resend=new Button("Re-send Email",IconFactory.compactIcon("email",14));
            {resend.getProperties().put("erp.icon.skip", true);resend.getStyleClass().addAll("approved-button","action-email","communication-resend-button");resend.setContentDisplay(ContentDisplay.LEFT);resend.setGraphicTextGap(7);resend.setMinWidth(132);resend.setPrefWidth(132);resend.setMaxWidth(132);resend.setTooltip(new Tooltip("Re-send email with the original document PDF"));resend.setOnAction(e->{Row row=getTableRow().getItem();if(row!=null)resend(row);});}
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);Row row=empty?null:getTableRow().getItem();setGraphic(row==null||!"EMAIL".equalsIgnoreCase(row.channel.get())?null:resend);setAlignment(Pos.CENTER);}
        });
    }
    private void resend(Row row){
        Task<Void> task=new Task<>(){@Override protected Void call() throws Exception{
            String channel=row.channel.get();
            String recipient=row.recipient.get();
            String subject=row.subject.get().isBlank()?row.entity.get():row.subject.get();
            Path attachment=originalDocumentPdf(row);
            if("EMAIL".equalsIgnoreCase(channel)){
                String body="Please find the original document attached.\n\nResent from DSE ERP Communication Center.";
                if(attachment!=null) EmailService.send(recipient,subject,body,attachment);
                else EmailService.send(recipient,subject,body);
            } else {
                String phone=recipient.replaceAll("\\D","");
                if(phone.length()==10)phone="91"+phone;
                WhatsappService.openWhatsappWithMessage(phone,subject,attachment);
            }
            try(Connection c=DatabaseManager.getConnection();PreparedStatement ps=c.prepareStatement(
                    "INSERT INTO communication_log(entity_type,entity_id,channel,recipient,subject,status,created_by) VALUES(?,?,?,?,?,'SENT',?)")){
                ps.setString(1,row.entityType);
                ps.setInt(2,row.entityId);
                ps.setString(3,channel);
                ps.setString(4,recipient);
                ps.setString(5,subject);
                ps.setString(6,"System");
                ps.executeUpdate();
            }
            return null;}};
        task.setOnSucceeded(e->{org.example.util.ToastManager.success(table,"Resent",row.entity.get()+" was resent successfully.");refresh();});
        task.setOnFailed(e->new OwnedAlert(Alert.AlertType.ERROR,task.getException()==null?"Resend failed":task.getException().getMessage()).showAndWait());
        Thread t=new Thread(task,"communication-resend");t.setDaemon(true);t.start();
    }

    private String documentLabel(Connection c,String type,int id,String subject){
        if(type==null)return subject==null?"Document":subject;
        String sql=switch(type.toUpperCase(Locale.ROOT)){
            case "SALE" -> "SELECT invoice_no FROM sales_header WHERE id=?";
            case "PURCHASE" -> "SELECT invoice_no FROM purchase_header WHERE id=?";
            case "QUOTATION" -> "SELECT quotation_no FROM quotation_header WHERE id=?";
            default -> null;
        };
        if(sql!=null && id>0){
            try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(r.next())return r.getString(1);}}catch(Exception ignored){}
        }
        if(subject!=null&&!subject.isBlank()){
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(SAL-[A-Z0-9-]+|INV-[A-Z0-9-]+|PUR-[A-Z0-9-]+|QT-[A-Z0-9-]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(subject);
            if(m.find())return m.group(1);
        }
        return type+" #"+id;
    }

    private Path originalDocumentPdf(Row row) throws Exception{
        String type=row.entityType==null?"":row.entityType.toUpperCase(Locale.ROOT);
        String number=row.entity.get();
        return switch(type){
            case "SALE" -> {
                var sale=new SalesService().getByInvoice(number);
                yield sale==null?null:InvoicePdfService.sales(sale);
            }
            case "PURCHASE" -> {
                var purchase=new PurchaseService().getByInvoice(number);
                yield purchase==null?null:InvoicePdfService.purchase(purchase);
            }
            case "QUOTATION" -> InvoicePdfService.quotation(number);
            default -> null;
        };
    }

    @FXML private void openEmailSettings(){NavigationManager.getInstance().loadPage("/fxml/pages/EmailSettings.fxml");}
    public static final class Row{
        final SimpleStringProperty time,entity,channel,recipient,subject,status,error,user;
        final String entityType;
        final int entityId;
        Row(String timeValue,String entityValue,String type,int id,String channelValue,String recipientValue,String subjectValue,String statusValue,String errorValue,String userValue){
            time=new SimpleStringProperty(timeValue);
            entity=new SimpleStringProperty(entityValue);
            entityType=type;
            entityId=id;
            channel=new SimpleStringProperty(channelValue);
            recipient=new SimpleStringProperty(recipientValue==null?"":recipientValue);
            subject=new SimpleStringProperty(subjectValue==null?"":subjectValue);
            status=new SimpleStringProperty(statusValue);
            error=new SimpleStringProperty(errorValue==null?"":errorValue);
            user=new SimpleStringProperty(userValue==null?"":userValue);
        }
    }



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
        IconFactory.applyTableHeaderIcon(colActions, "email");
    }
}
