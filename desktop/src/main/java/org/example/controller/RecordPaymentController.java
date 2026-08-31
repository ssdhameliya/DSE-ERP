package org.example.controller;

import org.example.util.BusinessClock;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;

import org.example.api.support.SupportApiClient;
import org.example.model.Sales;
import org.example.navigation.NavigationManager;
import org.example.service.SalesService;
import org.example.service.LookupService;
import org.example.navigation.ScreenLifecycle;
import org.example.service.EmailService;
import org.example.service.InvoicePdfService;
import org.example.service.NotificationService;
import org.example.service.PaymentMessageService;
import org.example.service.WhatsappService;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.UiTaskExecutor;
import org.example.util.AttachmentPreviewSupport;

import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class RecordPaymentController implements ScreenLifecycle {
    private final SupportApiClient supportApi = new SupportApiClient();
    public record PaymentRow(int id, String date, String reference, String from, String mode,
                             double amount, String status, String notes, String receiptPath, String paymentType) {}

    @FXML private Label invoiceNo, invoiceStatus, customer, customerPhone, customerEmail, invoiceDate, dueDate,
            total, paid, balance, after, summaryTotal, summaryPaid, summaryBalance, paidPercent,
            timelineCreated, timelineEmail, timelineWhatsapp, timelinePayment, timelineCurrent,
            attachmentName, historyCount, recordPaymentTitle, summaryTitle, attachmentTitle, historyTitle;
    @FXML private ProgressBar paymentProgress;
    @FXML private DatePicker paymentDate, historyFromDate, historyToDate;
    @FXML private ComboBox<String> mode, bankAccount, historyModeFilter;
    @FXML private TextField reference, amount, receivedFrom;
    @FXML private TextArea notes;
    @FXML private RadioButton fullPayment, partialPayment;
    @FXML private Button btnSavePayment;
    @FXML private StackPane paymentPageIcon;
    @FXML private VBox historySection, proofDropZone;
    @FXML private TableView<PaymentRow> historyTable;
    @FXML private TableColumn<PaymentRow, String> historyDate, historyReference, historyFrom,
            historyMode, historyStatus, historyNotes;
    @FXML private TableColumn<PaymentRow, Number> historyAmount;
    @FXML private TableColumn<PaymentRow, Void> historyAction;

    private final List<PaymentRow> allPayments = new ArrayList<>();
    private Sales sale;
    private Path selectedAttachment;
    private PaymentRow editingPayment;
    private boolean proofRemovalPending;
    private final LookupService lookupService = new LookupService();

    @FXML public void initialize() {
        if (paymentPageIcon != null) paymentPageIcon.getChildren().setAll(IconFactory.icon("payment",24));
        decorateSectionTitles();
        configureHistoryTable();
        configurePaymentForm();
        loadSelectedInvoice();
        Platform.runLater(this::wireUi);
    }

    @Override public void onScreenShown(boolean reusedFromCache) { loadPaymentLookupsAsync(); loadSelectedInvoice(); }
    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("record-payment-");}

    private void loadSelectedInvoice() {
        String selected=SalesScreenContext.invoice();
        if(selected==null||selected.isBlank())return;
        UiTaskExecutor.submitLatest(
            "record-payment-invoice",
            () -> new SalesService().getByInvoice(selected),
            loaded -> {
                if (loaded == null) { new OwnedAlert(Alert.AlertType.ERROR, "Unable to load the selected invoice: "+selected).showAndWait(); return; }
                sale=loaded;allPayments.clear();configureInvoice();resetForm();refreshTimeline();loadHistory();
            },
            failure -> new OwnedAlert(Alert.AlertType.ERROR, message(failure)).showAndWait()
        );
    }

    private void configureInvoice() {
        invoiceNo.setText(sale.getInvoiceNo());
        customer.setText(sale.getCustomer().getName());
        customerPhone.setText(safe(sale.getCustomer().getPhone()));
        customerEmail.setText(safe(sale.getCustomer().getEmail()));
        invoiceDate.setText(formatDate(sale.getInvoiceDate()));
        dueDate.setText(formatDate(sale.getDueDate()));
        receivedFrom.setText(sale.getCustomer().getName());
        applyInvoiceAmounts();
    }

    private void configurePaymentForm() {
        paymentDate.setValue(BusinessClock.today());
        List<String> defaultModes=new ArrayList<>(List.of("Bank Transfer","Cash","Cheque","UPI","Card","Other"));
        applyPaymentLookups(defaultModes,configuredBankAccounts());
        ToggleGroup group = new ToggleGroup();
        fullPayment.setToggleGroup(group); partialPayment.setToggleGroup(group);
        fullPayment.setSelected(true);
        amount.textProperty().addListener((o,a,b)->updateBalancePreview());
        fullPayment.setOnAction(e -> selectFull());
        partialPayment.setOnAction(e -> selectPartial());
        historyModeFilter.valueProperty().addListener((o,a,b)->applyHistoryFilter());
        historyFromDate.valueProperty().addListener((o,a,b)->applyHistoryFilter());
        historyToDate.valueProperty().addListener((o,a,b)->applyHistoryFilter());
        mode.valueProperty().addListener((o,a,b)->bankAccount.setDisable(b==null||!(b.toLowerCase(Locale.ROOT).contains("bank")||b.equalsIgnoreCase("NEFT")||b.equalsIgnoreCase("RTGS"))));
        mode.setOnShowing(e->loadPaymentLookupsAsync());bankAccount.setOnShowing(e->loadPaymentLookupsAsync());
        loadPaymentLookupsAsync();
    }

    private record PaymentLookups(List<String> modes,List<String> accounts){}

    private void loadPaymentLookupsAsync(){
        UiTaskExecutor.submitLatest(
            "record-payment-lookups",
            () -> {
                List<String> modes=new ArrayList<>();
                try{modes.addAll(lookupService.getValuesByCategoryCode("PAYMENT_MODE"));}catch(Exception ignored){}
                if(modes.isEmpty())modes.addAll(List.of("Bank Transfer","Cash","Cheque","UPI","Card","Other"));
                List<String> accounts=new ArrayList<>();
                try{for(org.example.model.Lookup l:lookupService.getByCategoryCode("BANK_ACCOUNT")){if(l.isActive()&&l.getLookupValue()!=null&&!l.getLookupValue().isBlank()){String n=l.getDescription()==null?"":l.getDescription().trim();accounts.add(n.isBlank()?l.getLookupValue().trim():l.getLookupValue().trim()+" - "+n);}}}catch(Exception ignored){}
                if(accounts.isEmpty())accounts.addAll(configuredBankAccounts());
                return new PaymentLookups(List.copyOf(modes),List.copyOf(accounts));
            },
            lookups -> applyPaymentLookups(lookups.modes(),lookups.accounts()),
            failure -> System.err.println("Payment lookup load failed: "+message(failure))
        );
    }

    private List<String> configuredBankAccounts(){
        List<String> accounts=new ArrayList<>();
        String bank = ConfigManager.get("payment.bankName", "").trim(); String account = ConfigManager.get("payment.accountNumber", "").trim();
        if(!account.isBlank())accounts.add(bank.isBlank()?account:account+" - "+bank);
        return accounts;
    }

    private void applyPaymentLookups(List<String> modes,List<String> accounts){
        String selectedMode=mode.getValue();
        mode.setItems(FXCollections.observableArrayList(modes));
        if(selectedMode!=null&&modes.contains(selectedMode))mode.setValue(selectedMode);
        else if(modes.contains("Bank Transfer"))mode.setValue("Bank Transfer"); else if(!modes.isEmpty())mode.getSelectionModel().selectFirst();
        List<String> historyModes=new ArrayList<>();historyModes.add("All Modes");historyModes.addAll(modes);
        String historySelected=historyModeFilter.getValue();historyModeFilter.setItems(FXCollections.observableArrayList(historyModes));
        historyModeFilter.setValue(historySelected!=null&&historyModes.contains(historySelected)?historySelected:"All Modes");
        String selectedAccount=bankAccount.getValue();bankAccount.getItems().setAll(accounts);
        if(selectedAccount!=null&&accounts.contains(selectedAccount))bankAccount.setValue(selectedAccount);else if(!accounts.isEmpty())bankAccount.getSelectionModel().selectFirst();else bankAccount.setPromptText("Add BANK ACCOUNT values in Masters");
    }

    private void decorateSectionTitles() {
        setSectionIcon(recordPaymentTitle, "payment");
        setSectionIcon(summaryTitle, "report");
        setSectionIcon(attachmentTitle, "attachment");
        setSectionIcon(historyTitle, "history");
    }

    private static void setSectionIcon(Label label, String semantic) {
        if (label == null) return;
        label.setGraphic(IconFactory.compactIcon(semantic, 16));
        label.setContentDisplay(ContentDisplay.LEFT);
        label.setGraphicTextGap(7);
    }

    private void configureHistoryTable() {
        historyDate.setCellValueFactory(v->new SimpleStringProperty(v.getValue().date()));
        historyReference.setCellValueFactory(v->new SimpleStringProperty(v.getValue().reference()));
        historyFrom.setCellValueFactory(v->new SimpleStringProperty(v.getValue().from()));
        historyMode.setCellValueFactory(v->new SimpleStringProperty(v.getValue().mode()));
        historyAmount.setCellValueFactory(v->new SimpleDoubleProperty(v.getValue().amount()));
        historyAmount.setCellFactory(c->new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty); setText(empty || v == null ? null : money(v.doubleValue()));
            }
        });
        historyStatus.setCellValueFactory(v->new SimpleStringProperty(v.getValue().status()));
        historyNotes.setCellValueFactory(v->new SimpleStringProperty(v.getValue().notes()));
        historyAction.setCellFactory(c->new TableCell<>() {
            private final MenuButton actions = new MenuButton("Actions");
            private final MenuItem edit = new MenuItem("Edit Payment", IconFactory.compactIcon("edit",14));
            private final MenuItem view = new MenuItem("View Proof", IconFactory.compactIcon("attachment",14));
            private final MenuItem folder = new MenuItem("Remove Proof", IconFactory.compactIcon("delete",14));
            {
                actions.getStyleClass().addAll("approved-button","approved-secondary-button","row-actions");
                actions.setGraphic(IconFactory.compactIcon("actions",14));
                actions.getItems().addAll(edit,view,folder);
                edit.setOnAction(e->editPayment(row()));
                view.setOnAction(e->openReceipt(row()));
                folder.setOnAction(e->removeStoredProof(row()));
                IconFactory.decorateActionMenu(actions);
            }
            private PaymentRow row(){int i=getIndex();return i<0||i>=getTableView().getItems().size()?null:getTableView().getItems().get(i);}
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v,empty);
                if(empty){setGraphic(null);return;}
                PaymentRow row=row();
                boolean noProof=row==null||row.receiptPath()==null||row.receiptPath().isBlank();
                boolean bankReconciliation=row!=null&&"BANK_RECONCILIATION".equalsIgnoreCase(row.paymentType());view.setDisable(noProof);folder.setDisable(noProof||bankReconciliation);
                edit.setDisable(row==null||bankReconciliation);
                setGraphic(actions);
            }
        });
    }

    private void refreshInvoiceAmounts() {
        String selected=SalesScreenContext.invoice();
        if(selected==null||selected.isBlank())return;
        UiTaskExecutor.submitLatest("record-payment-totals",()->new SalesService().getByInvoice(selected),loaded->{if(loaded!=null){sale=loaded;applyInvoiceAmounts();}},failure->new OwnedAlert(Alert.AlertType.ERROR,message(failure)).showAndWait());
    }

    private void applyInvoiceAmounts() {
        double t=sale.getTotalAmount(), p=sale.getPaidAmount(), b=sale.getBalanceAmount();
        total.setText(money(t)); paid.setText(money(p)); balance.setText(money(b));
        summaryTotal.setText(money(t)); summaryPaid.setText(money(p)); summaryBalance.setText(money(b));
        double ratio=t<=0?0:Math.min(1,p/t);
        paymentProgress.setProgress(ratio);
        paidPercent.setText(String.format(Locale.ROOT,"%.0f%% Paid",ratio*100));
        String status = b<=0.005?"PAID":p>0.005?"PARTIAL":"PENDING";
        invoiceStatus.setText(status);
        invoiceStatus.getStyleClass().removeAll("status-paid", "status-partial", "status-pending", "status-overdue");
        if ("PAID".equals(status)) invoiceStatus.getStyleClass().add("status-paid");
        else if ("PARTIAL".equals(status)) invoiceStatus.getStyleClass().add("status-partial");
        else if (sale.getDueDate()!=null && sale.getDueDate().isBefore(BusinessClock.today())) invoiceStatus.getStyleClass().add("status-overdue");
        else invoiceStatus.getStyleClass().add("status-pending");
        updateBalancePreview();
    }

    private void updateBalancePreview() {
        if(sale==null)return;
        double allowable=sale.getBalanceAmount()+(editingPayment==null?0:editingPayment.amount());
        after.setText(money(Math.max(0,allowable-parseAmount(amount.getText()))));
    }

    @FXML private void selectFull(){ amount.setText(String.format(Locale.ROOT,"%.2f",sale.getBalanceAmount()+(editingPayment==null?0:editingPayment.amount()))); }
    @FXML private void selectPartial(){ double allowable=sale.getBalanceAmount()+(editingPayment==null?0:editingPayment.amount());if(parseAmount(amount.getText())>=allowable) amount.clear(); amount.requestFocus(); }

    @FXML private void save() {
        if(editingPayment!=null){saveEditedPayment();return;}
        try {
            validate();
            double value=Double.parseDouble(amount.getText().trim());
            var request=new SupportApiClient.PaymentRequest("SALE",sale.getId(),paymentDate.getValue().toString(),value,mode.getValue(),reference.getText().trim(),notes.getText().trim(),receivedFrom.getText().trim(),fullPayment.isSelected()?"FULL":"PARTIAL",null,"Admin");
            Path proof=selectedAttachment;String currentInvoice=sale.getInvoiceNo();
            if(btnSavePayment!=null)btnSavePayment.setDisable(true);
            UiTaskExecutor.submitAction("record-payment-save",()->{
                int paymentId=supportApi.recordPaymentWithId(request);String proofWarning=null;
                if(proof!=null){try{supportApi.uploadPaymentAttachment(paymentId,proof);}catch(Exception proofError){proofWarning="Payment was saved, but the proof could not be uploaded: "+message(proofError);}}
                return proofWarning;
            },proofWarning->{if(btnSavePayment!=null)btnSavePayment.setDisable(false);NotificationService.add("Payment received for "+currentInvoice);org.example.util.ToastManager.success(amount,"Payment saved","Payment saved successfully.");org.example.util.ScreenRefreshPolicy.invalidate("sales-register");resetForm();refreshInvoiceAmounts();loadHistory();if(proofWarning!=null)new OwnedAlert(Alert.AlertType.WARNING,proofWarning).showAndWait();},failure->{if(btnSavePayment!=null)btnSavePayment.setDisable(false);new OwnedAlert(Alert.AlertType.ERROR,message(failure)).showAndWait();});
        } catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }

    private void saveEditedPayment(){
        try{
            validate();
            double newValue=parseAmount(amount.getText());double difference=newValue-editingPayment.amount();
            String confirmation="ACCOUNTING CONFIRMATION\n\n"+"Old Amount: "+money(editingPayment.amount())+"\n"+"New Amount: "+money(newValue)+"\n"+"Difference: "+signedMoney(difference)+"\n\n"+"This changes the invoice balance and payment status.\n\nContinue?";
            ButtonType choice=new OwnedAlert(Alert.AlertType.CONFIRMATION,confirmation,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO);if(choice!=ButtonType.YES)return;
            PaymentRow current=editingPayment;Path proof=selectedAttachment;boolean removeProof=proofRemovalPending;String currentInvoice=sale.getInvoiceNo();
            var request=new SupportApiClient.PaymentUpdateRequest(paymentDate.getValue().toString(),newValue,mode.getValue(),reference.getText().trim(),notes.getText().trim(),receivedFrom.getText().trim());
            if(btnSavePayment!=null)btnSavePayment.setDisable(true);
            UiTaskExecutor.submitAction("record-payment-update-"+current.id(),()->{supportApi.updatePayment(current.id(),request);if(removeProof)supportApi.deletePaymentAttachment(current.id());else if(proof!=null)supportApi.uploadPaymentAttachment(current.id(),proof);return true;},ignored->{if(btnSavePayment!=null)btnSavePayment.setDisable(false);NotificationService.add("Payment updated for "+currentInvoice);org.example.util.ToastManager.success(amount,"Payment updated","Payment updated and invoice totals recalculated.");org.example.util.ScreenRefreshPolicy.invalidate("sales-register");resetForm();refreshInvoiceAmounts();loadHistory();},failure->{if(btnSavePayment!=null)btnSavePayment.setDisable(false);new OwnedAlert(Alert.AlertType.ERROR,message(failure)).showAndWait();});
        }catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }



    private void validate() throws IOException {
        if(paymentDate.getValue()==null) throw new IllegalArgumentException("Select a payment date.");
        if(mode.getValue()==null || mode.getValue().isBlank()) throw new IllegalArgumentException("Select a payment mode.");
        if(receivedFrom.getText()==null || receivedFrom.getText().isBlank()) throw new IllegalArgumentException("Enter who made the payment.");
        double value=parseAmount(amount.getText());
        if(value<=0) throw new IllegalArgumentException("Received amount must be greater than zero.");
        double allowable=sale.getBalanceAmount()+(editingPayment==null?0:editingPayment.amount());
        if(value>allowable+0.005)
            throw new IllegalArgumentException("Enter an amount up to "+money(allowable)+".");
        if("Bank Transfer".equalsIgnoreCase(mode.getValue()) && bankAccount.getValue()==null)
            throw new IllegalArgumentException("Select or configure a bank account.");
        if(selectedAttachment!=null && Files.size(selectedAttachment)>5*1024*1024)
            throw new IllegalArgumentException("Payment proof must be 5 MB or smaller.");
    }


    private void resetForm() {
        editingPayment=null;
        if(btnSavePayment!=null)btnSavePayment.setText("Save Payment");
        reference.clear(); notes.clear(); paymentDate.setValue(BusinessClock.today()); selectedAttachment=null; proofRemovalPending=false;
        if(sale!=null&&sale.getCustomer()!=null)receivedFrom.setText(sale.getCustomer().getName());
        attachmentName.setText("No file selected");
        fullPayment.setSelected(true);
        amount.setText(String.format(Locale.ROOT,"%.2f",sale==null?0:sale.getBalanceAmount()));
    }

    private void loadHistory() {
        Sales current=sale;if(current==null)return;
        int saleId=current.getId();String customerName=current.getCustomer()==null?"":current.getCustomer().getName();
        UiTaskExecutor.submitLatest(
            "record-payment-history",
            () -> supportApi.payments("SALE",saleId),
            rows -> {
                allPayments.clear();
                for(var r:rows)allPayments.add(new PaymentRow(r.id(),r.date(),safe(r.reference()),safeOr(r.receivedFrom(),customerName),safe(r.mode()),r.amount(),"Recorded",safe(r.notes()),safe(r.attachment()),safe(r.paymentType())));
                historyCount.setText(allPayments.size()+" Payment"+(allPayments.size()==1?"":"s"));applyHistoryFilter();refreshTimeline();
            },
            failure -> new OwnedAlert(Alert.AlertType.ERROR,message(failure)).showAndWait()
        );
    }

    private void applyHistoryFilter() {
        String selected=historyModeFilter.getValue()==null?"All Modes":historyModeFilter.getValue();
        LocalDate from=historyFromDate.getValue(), to=historyToDate.getValue();
        historyTable.getItems().setAll(allPayments.stream().filter(row->{
            boolean modeOk=selected.startsWith("All")||row.mode().equalsIgnoreCase(selected);
            LocalDate d; try{d=LocalDate.parse(row.date());}catch(Exception e){d=null;}
            return modeOk && (from==null || (d!=null&&!d.isBefore(from))) && (to==null || (d!=null&&!d.isAfter(to)));
        }).collect(Collectors.toList()));
        updateHistoryTableHeight();
    }

    private void updateHistoryTableHeight() {
        int rowCount = historyTable.getItems().size();
        double headerHeight = 40.0;
        double rowHeight = historyTable.getFixedCellSize() > 0 ? historyTable.getFixedCellSize() : 42.0;
        double height = headerHeight + (rowCount * rowHeight) + 3.0;
        historyTable.setMinHeight(height);
        historyTable.setPrefHeight(height);
        historyTable.setMaxHeight(height);
        historyTable.setPlaceholder(new Label(rowCount == 0 ? "No payment records" : ""));
    }

    @FXML private void clearHistoryFilter(){ historyModeFilter.setValue("All Modes"); historyFromDate.setValue(null); historyToDate.setValue(null); applyHistoryFilter(); }
    @FXML private void history(){ historySection.requestFocus(); historyTable.requestFocus(); if(!historyTable.getItems().isEmpty())historyTable.getSelectionModel().selectFirst(); }

    @FXML private void exportHistory() {
        FileChooser ch=new FileChooser(); ch.setInitialFileName("Payment_History_"+sale.getInvoiceNo()+".csv");
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File","*.csv"));
        File out=ch.showSaveDialog(historyTable.getScene().getWindow()); if(out==null)return;
        try(Writer w=new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)){
            w.write("Date,Reference,Received From,Mode,Amount,Status,Notes,Receipt\n");
            for(PaymentRow r:historyTable.getItems()) w.write(csv(r.date())+","+csv(r.reference())+","+csv(r.from())+","+csv(r.mode())+","+r.amount()+","+csv(r.status())+","+csv(r.notes())+","+csv(r.receiptPath())+"\n");
        }catch(IOException e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }

    @FXML private void resetPayment(){ resetForm(); }

    @FXML private void browseFile() {
        FileChooser ch=new FileChooser(); ch.setTitle("Choose payment proof");
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("Proof files","*.pdf","*.png","*.jpg","*.jpeg"));
        File f=ch.showOpenDialog(amount.getScene().getWindow());
        if(f!=null){selectedAttachment=f.toPath();proofRemovalPending=false;attachmentName.setText(f.getName());}
    }

    @FXML private void previewProof(){
        Path local=selectedAttachment;PaymentRow current=editingPayment;
        if(local!=null){openProofPath(local);return;}
        if(current==null||proofRemovalPending||safe(current.receiptPath()).isBlank()){new OwnedAlert(Alert.AlertType.ERROR,"No payment proof is attached.").showAndWait();return;}
        UiTaskExecutor.submitLatest("record-payment-proof-preview-"+current.id(),()->materializePaymentProof(supportApi.paymentAttachment(current.id())),this::openProofPath,failure->new OwnedAlert(Alert.AlertType.ERROR,message(failure)).showAndWait());
    }

    private void openProofPath(Path path){try{if(path==null||!Files.isRegularFile(path))throw new IOException("The payment proof is unavailable.");Desktop.getDesktop().open(path.toFile());}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}}

    @FXML private void removeProof(){
        boolean hasSelected=selectedAttachment!=null;boolean hasExisting=editingPayment!=null&&!safe(editingPayment.receiptPath()).isBlank()&&!proofRemovalPending;if(!hasSelected&&!hasExisting)return;
        if(new OwnedAlert(Alert.AlertType.CONFIRMATION,"Remove the payment proof?",ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
        selectedAttachment=null;proofRemovalPending=hasExisting;attachmentName.setText(proofRemovalPending?"Proof will be removed when payment is updated":"No file selected");
    }

    private void openReceipt(PaymentRow row) {
        if(row==null||safe(row.receiptPath()).isBlank())return;
        UiTaskExecutor.submitLatest("record-payment-proof-open-"+row.id(),()->materializePaymentProof(supportApi.paymentAttachment(row.id())),this::openProofPath,failure->new OwnedAlert(Alert.AlertType.ERROR,message(failure)).showAndWait());
    }

    private void editPayment(PaymentRow row){
        if(row==null)return;
        if("BANK_RECONCILIATION".equalsIgnoreCase(row.paymentType())){
            new OwnedAlert(Alert.AlertType.WARNING,"Bank-reconciled payments must be changed from Bank Statement using the reversal/reconciliation workflow.").showAndWait();
            return;
        }
        editingPayment=row;
        try{paymentDate.setValue(LocalDate.parse(row.date()));}catch(Exception ignored){paymentDate.setValue(BusinessClock.today());}
        if(!mode.getItems().contains(row.mode()))mode.getItems().add(row.mode());
        mode.setValue(row.mode());
        reference.setText(row.reference());
        receivedFrom.setText(row.from());
        amount.setText(String.format(Locale.ROOT,"%.2f",row.amount()));
        notes.setText(row.notes());
        selectedAttachment=null; proofRemovalPending=false;
        attachmentName.setText(row.receiptPath()==null||row.receiptPath().isBlank()?"No proof attached":"Existing proof: "+Path.of(row.receiptPath()).getFileName());
        if ("FULL".equalsIgnoreCase(row.paymentType())) fullPayment.setSelected(true); else partialPayment.setSelected(true);
        if(btnSavePayment!=null)btnSavePayment.setText("Update Payment");
        amount.requestFocus();amount.selectAll();
        updateBalancePreview();
    }

    private void removeStoredProof(PaymentRow row){
        if(row==null||safe(row.receiptPath()).isBlank()||"BANK_RECONCILIATION".equalsIgnoreCase(row.paymentType()))return;
        if(new OwnedAlert(Alert.AlertType.CONFIRMATION,"Remove this payment proof?",ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
        UiTaskExecutor.submitAction("record-payment-proof-delete-"+row.id(),()->{supportApi.deletePaymentAttachment(row.id());return true;},ignored->{loadHistory();org.example.util.ToastManager.success(historyTable,"Proof removed","Payment proof removed.");},failure->new OwnedAlert(Alert.AlertType.ERROR,message(failure)).showAndWait());
    }

    private Path materializePaymentProof(SupportApiClient.DownloadedAttachment download)throws IOException{
        return AttachmentPreviewSupport.materialize(download,"payment-proof");
    }

    @FXML private void downloadPdf(){ try{Path p=InvoicePdfService.sales(sale); Desktop.getDesktop().open(p.getParent().toFile());}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}}
    @FXML private void printInvoice(){ try{Desktop.getDesktop().print(InvoicePdfService.sales(sale).toFile());}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}}
    @FXML private void cancel(){ if(sale!=null)LinkedRecordContext.open("SALE",sale.getId(),sale.getInvoiceNo(),"VIEW","Record Payment"); NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml"); }

    @FXML private void sendReceipt() {
        try {
            PaymentRow row=latestPayment();
            Path pdf=InvoicePdfService.sales(sale);
            EmailService.send(sale.getCustomer().getEmail(),"Payment receipt - "+sale.getInvoiceNo(),
                    "Thank you. We have recorded your payment of "+money(row.amount())+" for invoice "+sale.getInvoiceNo()+".",pdf);
            org.example.util.ToastManager.success(amount,"Receipt sent","Receipt sent successfully.");
        }catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }

    @FXML private void emailInvoice() {
        try {
            EmailService.send(sale.getCustomer().getEmail(),"Sales Invoice "+sale.getInvoiceNo(),PaymentMessageService.salesMessage(sale),InvoicePdfService.sales(sale));
            org.example.util.ToastManager.success(amount,"Email sent","Invoice emailed successfully.");
        }catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }

    @FXML private void whatsappReceipt() {
        try {
            PaymentRow row=latestPayment();
            String message="Hello "+sale.getCustomer().getName()+", payment of "+money(row.amount())+" has been recorded for invoice "+sale.getInvoiceNo()+". Balance: "+money(sale.getBalanceAmount())+".";
            WhatsappService.openWhatsappWithMessage(sale.getCustomer().getPhone().replaceAll("\\D",""),message,InvoicePdfService.sales(sale));
        }catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }

    @FXML private void downloadStatement(){ exportHistory(); }

    private PaymentRow latestPayment(){ if(allPayments.isEmpty())throw new IllegalStateException("No payment has been recorded yet."); return allPayments.get(0); }

    private void refreshTimeline() {
        timelineCreated.setText("Invoice Created\n"+safeOr(sale.getCreatedAt(),formatDate(sale.getInvoiceDate()))+"\nAdmin");
        timelineEmail.setText("Invoice Emailed\n"+(sale.isEmailSent()?"Completed":"Pending"));
        timelineWhatsapp.setText("WhatsApp Sent\n"+(sale.isWhatsappSent()?"Completed":"Pending"));
        if(allPayments.isEmpty()) timelinePayment.setText("Payment Recorded\nNot yet");
        else timelinePayment.setText("Payment Recorded\n"+allPayments.get(0).date()+"\n"+money(allPayments.get(0).amount()));
        timelineCurrent.setText("Current Status\n"+invoiceStatus.getText()+"\nBalance: "+money(sale.getBalanceAmount()));
    }

    private void wireUi(){
        if (proofDropZone == null) return;
        proofDropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != proofDropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        proofDropZone.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            boolean completed = false;
            if (board.hasFiles() && !board.getFiles().isEmpty()) {
                Path candidate = board.getFiles().get(0).toPath();
                String lower = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".pdf") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    selectedAttachment = candidate;
                    proofRemovalPending = false;
                    attachmentName.setText(candidate.getFileName().toString());
                    completed = true;
                } else {
                    new OwnedAlert(Alert.AlertType.WARNING, "Choose a PDF, PNG, JPG or JPEG payment proof.").showAndWait();
                }
            }
            event.setDropCompleted(completed);
            event.consume();
        });
    }
    private static String message(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String value = root.getMessage();
        return value == null || value.isBlank() ? root.getClass().getSimpleName() : value;
    }

    private String mask(String v){return v.length()<=4?v:"••••"+v.substring(v.length()-4);}
    private double parseAmount(String v){try{return Double.parseDouble(v==null?"":v.trim());}catch(Exception e){return 0;}}
    private String money(double v){return String.format("₹ %,.2f",Math.max(0,v));}
    private String signedMoney(double v){return String.format(Locale.ROOT,"%s₹ %,.2f",v<0?"-":v>0?"+":"",Math.abs(v));}
    private String safe(String v){return v==null?"":v;}
    private String safeOr(String v,String fallback){return v==null||v.isBlank()?fallback:v;}
    private String csv(String v){return "\""+safe(v).replace("\"","\"\"")+"\"";}
    private String formatDate(LocalDate d){return d==null?"Not set":BusinessClock.formatDate(d);}
}
