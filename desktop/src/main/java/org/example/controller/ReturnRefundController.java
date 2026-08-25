package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.api.returns.ReturnApiClient;
import org.example.api.support.SupportApiClient;
import org.example.config.ConfigManager;
import org.example.navigation.NavigationManager;
import org.example.service.LookupService;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.util.BusinessClock;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.AttachmentPreviewSupport;
import org.example.util.UiTaskExecutor;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Shared full-page refund workspace for Sales Return and Purchase Return. */
public class ReturnRefundController {
    private final ReturnApiClient api=new ReturnApiClient();
    private final SupportApiClient support=new SupportApiClient();
    private final LookupService lookups=new LookupService();
    private ReturnApiClient.Details details;
    private Path pendingProof;

    @FXML private Label pageTitle,pageSubtitle,returnNo,party,originalDocument,returnDate,total,refunded,balance,after,summaryTotal,summaryRefunded,summaryBalance,refundPercent,proofName,historyCount,partyFieldLabel,amountFieldLabel;
    @FXML private DatePicker refundDate;
    @FXML private ComboBox<String> mode,bankAccount;
    @FXML private TextField reference,refundedParty,amount;
    @FXML private TextArea notes;
    @FXML private RadioButton fullRefund,partialRefund;
    @FXML private ProgressBar refundProgress;
    @FXML private TableView<ReturnApiClient.RefundRow> historyTable;
    @FXML private TableColumn<ReturnApiClient.RefundRow,String> cDate,cMode,cReference,cParty,cStatus,cNotes;
    @FXML private TableColumn<ReturnApiClient.RefundRow,Number> cAmount;
    @FXML private TableColumn<ReturnApiClient.RefundRow,Void> cAction;
    @FXML private VBox proofDropZone;
    @FXML private StackPane refundPageIcon;

    @FXML public void initialize(){if(refundPageIcon!=null)refundPageIcon.getChildren().setAll(IconFactory.icon("refund",24));configureForm();configureHistory();load();}

    private void configureForm(){
        refundDate.setValue(BusinessClock.today());
        List<String> modes=new ArrayList<>();try{modes.addAll(lookups.getValuesByCategoryCode("PAYMENT_MODE"));}catch(Exception ignored){}
        if(modes.isEmpty())modes.addAll(List.of("Bank Transfer","Cash","Cheque","UPI","Card","Other"));
        mode.setItems(FXCollections.observableArrayList(modes));if(modes.contains("Bank Transfer"))mode.setValue("Bank Transfer");else mode.getSelectionModel().selectFirst();
        List<String> accounts=new ArrayList<>();try{for(var l:lookups.getByType("BANK ACCOUNT"))if(l.isActive()&&l.getLookupValue()!=null&&!l.getLookupValue().isBlank()){String d=l.getDescription()==null?"":l.getDescription().trim();accounts.add(d.isBlank()?l.getLookupValue().trim():l.getLookupValue().trim()+" - "+d);}}catch(Exception ignored){}
        if(accounts.isEmpty()){String bank=ConfigManager.get("payment.bankName","").trim(),acct=ConfigManager.get("payment.accountNumber","").trim();if(!acct.isBlank())accounts.add(bank.isBlank()?acct:acct+" - "+bank);}
        bankAccount.getItems().setAll(accounts);if(!accounts.isEmpty())bankAccount.getSelectionModel().selectFirst();else bankAccount.setPromptText("Add BANK ACCOUNT in Masters");
        ToggleGroup g=new ToggleGroup();fullRefund.setToggleGroup(g);partialRefund.setToggleGroup(g);fullRefund.setSelected(true);
        fullRefund.setOnAction(e->selectFull());partialRefund.setOnAction(e->{amount.clear();amount.requestFocus();});amount.textProperty().addListener((o,a,b)->updateAfter());
        mode.valueProperty().addListener((o,a,b)->bankAccount.setDisable(b==null||!(b.toLowerCase(Locale.ROOT).contains("bank")||b.equalsIgnoreCase("NEFT")||b.equalsIgnoreCase("RTGS"))));
    }

    private void configureHistory(){
        cDate.setCellValueFactory(x->new SimpleStringProperty(BusinessClock.formatDate(x.getValue().date())));
        cMode.setCellValueFactory(x->new SimpleStringProperty(x.getValue().mode()));cReference.setCellValueFactory(x->new SimpleStringProperty(x.getValue().reference()));cParty.setCellValueFactory(x->new SimpleStringProperty(x.getValue().refundedParty()));cStatus.setCellValueFactory(x->new SimpleStringProperty(x.getValue().status()));cNotes.setCellValueFactory(x->new SimpleStringProperty(x.getValue().notes()));cAmount.setCellValueFactory(x->new SimpleDoubleProperty(x.getValue().amount()));
        cAmount.setCellFactory(x->new TableCell<>(){@Override protected void updateItem(Number n,boolean empty){super.updateItem(n,empty);setText(empty||n==null?null:money(n.doubleValue()));setAlignment(Pos.CENTER_RIGHT);}});
        cAction.setCellFactory(x->new TableCell<>(){private final MenuButton menu=new MenuButton("Actions");private final MenuItem preview=new MenuItem("Preview Proof",IconFactory.compactIcon("view",14));private final MenuItem remove=new MenuItem("Remove Proof",IconFactory.compactIcon("delete",14));{menu.getStyleClass().add("row-actions");menu.setGraphic(IconFactory.compactIcon("actions",14));menu.getItems().addAll(preview,remove);preview.setOnAction(e->previewRow(row()));remove.setOnAction(e->removeRowProof(row()));IconFactory.decorateActionMenu(menu);}private ReturnApiClient.RefundRow row(){int i=getIndex();return i<0||i>=getTableView().getItems().size()?null:getTableView().getItems().get(i);}@Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);if(empty){setGraphic(null);return;}var r=row();boolean none=r==null||safe(r.attachment()).isBlank();boolean bank=r!=null&&"BANK_RECONCILIATION".equalsIgnoreCase(r.refundType());preview.setDisable(none);remove.setDisable(none||bank);setGraphic(menu);}});
    }

    private void load(){String no=ReturnRefundContext.value();if(no==null||no.isBlank())return;try{details=api.details(no);boolean sales="SALES RETURN".equalsIgnoreCase(details.type());pageTitle.setText(sales?"Sales Return Refund":"Purchase Return Refund");pageSubtitle.setText("Record refund • review history • manage proof documents");partyFieldLabel.setText(sales?"Refunded To *":"Received From *");amountFieldLabel.setText(sales?"Amount Refunded *":"Refund Received *");returnNo.setText(details.no());party.setText(details.party());originalDocument.setText(details.invoice());returnDate.setText(BusinessClock.formatDate(details.date()));refundedParty.setText(details.party());refreshAmounts();loadHistory();resetForm(false);}catch(Exception e){error(e);}}
    private void refreshAmounts(){double b=Math.max(0,details.total()-details.refund());total.setText(money(details.total()));refunded.setText(money(details.refund()));balance.setText(money(b));summaryTotal.setText(money(details.total()));summaryRefunded.setText(money(details.refund()));summaryBalance.setText(money(b));double pct=details.total()<=0?0:Math.min(1,details.refund()/details.total());refundProgress.setProgress(pct);refundPercent.setText(String.format(Locale.ROOT,"%.0f%% refunded",pct*100));updateAfter();}
    private void loadHistory(){List<ReturnApiClient.RefundRow> rows=api.refunds(details.no());historyTable.getItems().setAll(rows);historyCount.setText(rows.size()+ (rows.size()==1?" Refund":" Refunds"));}
    private void selectFull(){if(details!=null)amount.setText(String.format(Locale.ROOT,"%.2f",Math.max(0,details.total()-details.refund())));}
    private void updateAfter(){if(details==null)return;after.setText(money(Math.max(0,details.total()-details.refund()-parse(amount.getText()))));}

    @FXML private void save(){try{validate();double value=parse(amount.getText());String type=fullRefund.isSelected()?"FULL":"PARTIAL";String user=SessionService.current()==null?"User":safe(SessionService.current().getFullName());int id=api.recordRefund(details.no(),new ReturnApiClient.RefundCreateRequest(refundDate.getValue().toString(),value,mode.getValue(),safe(reference.getText()),bankAccount.isDisabled()?"":safe(bankAccount.getValue()),safe(refundedParty.getText()),safe(notes.getText()),type,user));if(pendingProof!=null){try{support.uploadReturnRefundAttachment(id,pendingProof);}catch(Exception upload){error(new IllegalStateException("Refund was saved, but the proof could not be uploaded. The refund remains recorded. "+message(upload),upload));pendingProof=null;load();return;}}NotificationService.add(details.no()+" refund recorded.");org.example.util.ToastManager.success(amount,"Refund recorded","Refund transaction saved successfully.");pendingProof=null;load();}catch(Exception e){error(e);}}
    private void validate(){if(details==null)throw new IllegalStateException("Return is not loaded.");if(refundDate.getValue()==null)throw new IllegalArgumentException("Refund date is required.");if(mode.getValue()==null||mode.getValue().isBlank())throw new IllegalArgumentException("Payment mode is required.");double v=parse(amount.getText()),remaining=Math.max(0,details.total()-details.refund());if(v<=0)throw new IllegalArgumentException("Refund amount must be greater than zero.");if(v>remaining+.001)throw new IllegalArgumentException("Refund amount exceeds the remaining balance of "+money(remaining)+".");if(!bankAccount.isDisabled()&&(bankAccount.getValue()==null||bankAccount.getValue().isBlank()))throw new IllegalArgumentException("Bank Account is required for bank refunds.");}
    @FXML private void reset(){resetForm(true);}private void resetForm(boolean focus){refundDate.setValue(BusinessClock.today());reference.clear();notes.clear();fullRefund.setSelected(true);selectFull();pendingProof=null;proofName.setText("No file selected");if(details!=null)refundedParty.setText(details.party());if(focus)amount.requestFocus();updateAfter();}
    @FXML private void browseFile(){FileChooser fc=new FileChooser();fc.setTitle("Select refund proof");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Proof files","*.pdf","*.png","*.jpg","*.jpeg"));var f=fc.showOpenDialog(historyTable.getScene().getWindow());if(f==null)return;if(f.length()>25L*1024*1024){error(new IllegalArgumentException("Attachment is larger than 25 MB."));return;}pendingProof=f.toPath();proofName.setText(f.getName());}
    @FXML private void previewProof(){if(pendingProof==null){info("Refund proof","Choose a proof file first, or use Actions in Refund History to preview a saved proof.");return;}try{Desktop.getDesktop().open(pendingProof.toFile());}catch(Exception e){error(e);}}
    @FXML private void removeProof(){pendingProof=null;proofName.setText("No file selected");}
    private void previewRow(ReturnApiClient.RefundRow r){if(r==null)return;UiTaskExecutor.submitLatest("return-refund-proof-preview-"+r.id(),()->AttachmentPreviewSupport.materializeRequired(support.returnRefundAttachment(r.id()),"refund-proof.pdf"),this::openSavedProof,this::error);}private void openSavedProof(Path p){try{if(p==null||!Files.isRegularFile(p))throw new IOException("No proof file is stored.");Desktop.getDesktop().open(p.toFile());}catch(Exception e){error(e);}}
    private void removeRowProof(ReturnApiClient.RefundRow r){if(r==null)return;Alert a=new org.example.util.OwnedAlert(Alert.AlertType.CONFIRMATION,"Remove the saved proof from this refund?",ButtonType.YES,ButtonType.NO);a.setHeaderText("Remove Refund Proof");if(a.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;try{support.deleteReturnRefundAttachment(r.id());loadHistory();}catch(Exception e){error(e);}}
    
    @FXML private void back(){NavigationManager.getInstance().loadPage("SALES RETURN".equalsIgnoreCase(details==null?"":details.type())?"/fxml/pages/SalesReturns.fxml":"/fxml/pages/PurchaseReturns.fxml");}
    private static double parse(String x){try{return Double.parseDouble(safe(x).replace(",",""));}catch(Exception e){return 0;}}private static String money(double x){return String.format(Locale.ROOT,"₹ %,.2f",x);}private static String safe(String x){return x==null?"":x.trim();}private static String message(Throwable e){return e==null||e.getMessage()==null?"Unexpected error":e.getMessage();}
    private void info(String h,String m){new OwnedAlert(Alert.AlertType.INFORMATION,m).showAndWait();}private void error(Throwable e){new OwnedAlert(Alert.AlertType.ERROR,message(e)).showAndWait();}
}
