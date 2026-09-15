package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import org.example.api.audit.AuditApiClient;
import org.example.util.ActivityTimelineDialog;
import org.example.util.BusinessClock;
import org.example.util.DynamicTableLayoutManager;
import org.example.util.RealtimeSearchSupport;
import org.example.util.ResponsiveKpiLayoutManager;
import org.example.util.UiTaskExecutor;
import java.util.*;

public final class GlobalAuditController {
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row,String> colDate,colModule,colRecordType,colReference,colAction,colCategory,colField,colOld,colNew,colUser,colSource;
    @FXML private TableColumn<Row,Void> colView;
    @FXML private ComboBox<String> cmbModule,cmbAction;
    @FXML private TextField txtUser,txtReference,txtSearch;
    @FXML private Label lblTotal,lblChanges,lblFinancial,lblDocuments,lblCommunications,lblPage;
    @FXML private GridPane auditKpiGrid;
    private final AuditApiClient api=new AuditApiClient(); private int page=0,totalPages=0;

    @FXML private void initialize(){
        bind(colDate,Row::date);bind(colModule,Row::module);bind(colRecordType,Row::recordType);bind(colReference,Row::reference);bind(colAction,Row::action);bind(colCategory,Row::category);bind(colField,Row::field);bind(colOld,Row::oldValue);bind(colNew,Row::newValue);bind(colUser,Row::user);bind(colSource,Row::source);
        cmbModule.getItems().setAll("All","SALE","PURCHASE","QUOTATION","SALES_RETURN","PURCHASE_RETURN","PARTY","ITEM","FINANCE","PURCHASE_RECON","MASTER_LOOKUP","MASTER_CATEGORY");cmbModule.getSelectionModel().selectFirst();
        cmbAction.getItems().setAll("All","CREATED","UPDATED","APPROVED","REJECTED","DELETED","CANCELLED","PAYMENT_RECORDED","PAYMENT_EDITED","REFUND_RECORDED","EMAIL_SENT","EMAIL_FAILED","PDF_VIEWED","EXCEL_VIEWED","ATTACHMENT_ADDED","CONVERTED");cmbAction.getSelectionModel().selectFirst();
        colAction.setCellFactory(c->badgeCell("audit-purple"));colCategory.setCellFactory(c->badgeCell("audit-blue"));
        colOld.setCellFactory(c->valueCell("audit-old-value"));colNew.setCellFactory(c->valueCell("audit-new-value"));
        ResponsiveKpiLayoutManager.install(auditKpiGrid);
        DynamicTableLayoutManager.install(table);
        RealtimeSearchSupport.installRemote(txtSearch, () -> { page=0; load(); });
        colView.setCellFactory(c->new TableCell<>(){final Button b=new Button("View");{b.getStyleClass().add("secondary-button");b.setOnAction(e->{Row r=getTableRow()==null?null:getTableRow().getItem();if(r!=null)ActivityTimelineDialog.show(table,r.entityType(),(int)r.entityId(),r.reference());});}protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:b);}});
        refresh();
    }
    private void bind(TableColumn<Row,String> c,java.util.function.Function<Row,String> f){c.setCellValueFactory(x->new SimpleStringProperty(f.apply(x.getValue())));}
    private TableCell<Row,String> badgeCell(String base){return new TableCell<>(){protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setText(empty?null:item);getStyleClass().removeAll("audit-green","audit-red","audit-blue","audit-orange","audit-purple","audit-teal");if(!empty){String u=item==null?"":item.toUpperCase(Locale.ROOT);getStyleClass().add(u.contains("DELETE")||u.contains("REJECT")||u.contains("FAIL")?"audit-red":u.contains("PAYMENT")||u.contains("FINANCIAL")?"audit-blue":u.contains("DOCUMENT")||u.contains("PDF")||u.contains("EXCEL")?"audit-purple":u.contains("COMM")||u.contains("EMAIL")?"audit-teal":u.contains("APPROV")||u.contains("CREATE")||u.contains("LIFECYCLE")?"audit-green":base);}}};}
    private TableCell<Row,String> valueCell(String cls){return new TableCell<>(){protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setText(empty?null:(item==null||item.isBlank()?"—":item));getStyleClass().removeAll("audit-old-value","audit-new-value");if(!empty)getStyleClass().add(cls);}};}

    @FXML private void refresh(){load();}
    private void load(){String module=value(cmbModule),action=value(cmbAction);UiTaskExecutor.submitLatest("global-audit",()->api.global(page,100,module,action,txtUser.getText(),txtReference.getText(),txtSearch.getText()),this::show,this::error);}
    private void show(AuditApiClient.GlobalPage p){totalPages=p.totalPages();page=p.page();lblTotal.setText(String.valueOf(p.total()));lblChanges.setText(String.valueOf(p.businessChanges()));lblFinancial.setText(String.valueOf(p.financialEvents()));lblDocuments.setText(String.valueOf(p.documentEvents()));lblCommunications.setText(String.valueOf(p.communications()));List<Row> rows=new ArrayList<>();for(var e:p.rows()){if(e.changes()==null||e.changes().isEmpty())rows.add(Row.of(e,null));else for(var c:e.changes())rows.add(Row.of(e,c));}table.setItems(FXCollections.observableArrayList(rows));lblPage.setText("Page "+(page+1)+(totalPages>0?" of "+totalPages:"")+" • "+p.total()+" events");}
    @FXML private void clearFilters(){cmbModule.getSelectionModel().selectFirst();cmbAction.getSelectionModel().selectFirst();txtUser.clear();txtReference.clear();txtSearch.clear();page=0;load();}
    @FXML private void previousPage(){if(page>0){page--;load();}}
    @FXML private void nextPage(){if(page+1<totalPages){page++;load();}}
    private String value(ComboBox<String> c){String v=c.getValue();return v==null||"All".equalsIgnoreCase(v)?"":v;}
    private void error(Throwable t){Throwable x=t;while(x.getCause()!=null&&x.getCause()!=x)x=x.getCause();new org.example.util.OwnedAlert(Alert.AlertType.ERROR,"Audit Trail could not be loaded.\n\n"+(x.getMessage()==null?x.toString():x.getMessage())).showAndWait();}
    private static String prettyType(String t){return switch(t==null?"":t){case "SALE"->"Sales Invoice";case "PURCHASE"->"Purchase Invoice";case "QUOTATION"->"Quotation";case "SALES_RETURN"->"Sales Return";case "PURCHASE_RETURN"->"Purchase Return";case "PARTY"->"Customer / Supplier";case "ITEM"->"Item";default->(t==null?"":t.replace('_',' '));};}
    public record Row(long eventId,String entityType,long entityId,String date,String module,String recordType,String reference,String action,String category,String field,String oldValue,String newValue,String user,String source){static Row of(AuditApiClient.EventRow e,AuditApiClient.ChangeRow c){return new Row(e.id(),e.entityType(),e.entityId(),formatTimestamp(e.createdAt()),e.entityType(),prettyType(e.entityType()),e.referenceNo(),e.action(),e.category(),c==null?"":c.fieldName(),c==null?"":c.oldValue(),c==null?"":c.newValue(),e.createdBy(),e.legacySource()==null||e.legacySource().isBlank()?e.source():"LEGACY / "+e.legacySource());}}
    private static String formatTimestamp(String value){String raw=value==null?"":value.trim();if(raw.isBlank())return "—";try{return BusinessClock.formatTimestamp(raw);}catch(Exception ignored){return raw;}}
}
