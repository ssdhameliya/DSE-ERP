package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;

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
                             double amount, String status, String notes, String receiptPath) {}

    @FXML private Label invoiceNo, invoiceStatus, customer, customerPhone, customerEmail, invoiceDate, dueDate,
            total, paid, balance, after, summaryTotal, summaryPaid, summaryBalance, paidPercent,
            timelineCreated, timelineEmail, timelineWhatsapp, timelinePayment, timelineCurrent,
            attachmentName, historyCount;
    @FXML private ProgressBar paymentProgress;
    @FXML private DatePicker paymentDate, historyFromDate, historyToDate;
    @FXML private ComboBox<String> mode, bankAccount, historyModeFilter;
    @FXML private TextField reference, amount, receivedFrom;
    @FXML private TextArea notes;
    @FXML private RadioButton fullPayment, partialPayment, advancePayment;
    @FXML private VBox historySection, proofDropZone;
    @FXML private TableView<PaymentRow> historyTable;
    @FXML private TableColumn<PaymentRow, String> historyDate, historyReference, historyFrom,
            historyMode, historyStatus, historyNotes;
    @FXML private TableColumn<PaymentRow, Number> historyAmount;
    @FXML private TableColumn<PaymentRow, Void> historyReceipt, historyAction;

    private final List<PaymentRow> allPayments = new ArrayList<>();
    private Sales sale;
    private Path selectedAttachment;
    private final LookupService lookupService = new LookupService();

    @FXML public void initialize() {
        configureHistoryTable();
        configurePaymentForm();
        loadSelectedInvoice();
        Platform.runLater(this::wireUi);
    }

    @Override public void onScreenShown(boolean reusedFromCache) { loadSelectedInvoice(); }

    private void loadSelectedInvoice() {
        String selected=SalesScreenContext.invoice();
        if(selected==null||selected.isBlank())return;
        sale = new SalesService().getByInvoice(selected);
        if (sale == null) { new OwnedAlert(Alert.AlertType.ERROR, "Unable to load the selected invoice: "+selected).showAndWait(); return; }
        configureInvoice();
        resetForm();
        loadHistory();
        refreshTimeline();
    }

    private void configureInvoice() {
        invoiceNo.setText(sale.getInvoiceNo());
        customer.setText(sale.getCustomer().getName());
        customerPhone.setText(safe(sale.getCustomer().getPhone()));
        customerEmail.setText(safe(sale.getCustomer().getEmail()));
        invoiceDate.setText(formatDate(sale.getInvoiceDate()));
        dueDate.setText(formatDate(sale.getDueDate()));
        receivedFrom.setText(sale.getCustomer().getName());
        refreshInvoiceAmounts();
    }

    private void configurePaymentForm() {
        paymentDate.setValue(LocalDate.now());
        List<String> modes;
        try { modes=new ArrayList<>(lookupService.getValuesByCategoryCode("PAYMENT_MODE")); } catch(Exception e){ modes=new ArrayList<>(); }
        if(modes.isEmpty()) modes.addAll(List.of("Bank Transfer","Cash","Cheque","UPI","Card","Other"));
        mode.setItems(FXCollections.observableArrayList(modes));
        if(modes.contains("Bank Transfer"))mode.setValue("Bank Transfer"); else if(!modes.isEmpty())mode.getSelectionModel().selectFirst();
        ToggleGroup group = new ToggleGroup();
        fullPayment.setToggleGroup(group); partialPayment.setToggleGroup(group); advancePayment.setToggleGroup(group);
        partialPayment.setSelected(true);
        amount.textProperty().addListener((o,a,b)->updateBalancePreview());
        fullPayment.setOnAction(e -> selectFull());
        partialPayment.setOnAction(e -> selectPartial());
        advancePayment.setOnAction(e -> selectAdvance());

        List<String> historyModes=new ArrayList<>(); historyModes.add("All Modes"); historyModes.addAll(modes);
        historyModeFilter.setItems(FXCollections.observableArrayList(historyModes));
        historyModeFilter.setValue("All Modes");
        historyModeFilter.valueProperty().addListener((o,a,b)->applyHistoryFilter());
        historyFromDate.valueProperty().addListener((o,a,b)->applyHistoryFilter());
        historyToDate.valueProperty().addListener((o,a,b)->applyHistoryFilter());

        List<String> accounts=new ArrayList<>();
        try{for(org.example.model.Lookup l:lookupService.getByType("BANK ACCOUNT")){if(l.isActive()&&l.getLookupValue()!=null&&!l.getLookupValue().isBlank()){String n=l.getDescription()==null?"":l.getDescription().trim();accounts.add(n.isBlank()?l.getLookupValue().trim():l.getLookupValue().trim()+" - "+n);}}}catch(Exception ignored){}
        if(accounts.isEmpty()){
            String bank = ConfigManager.get("payment.bankName", "").trim(); String account = ConfigManager.get("payment.accountNumber", "").trim();
            if(!account.isBlank())accounts.add(bank.isBlank()?account:account+" - "+bank);
        }
        bankAccount.getItems().setAll(accounts); if(!accounts.isEmpty())bankAccount.getSelectionModel().selectFirst(); else bankAccount.setPromptText("Add BANK ACCOUNT values in Masters");
        mode.valueProperty().addListener((o,a,b)->bankAccount.setDisable(b==null||!(b.toLowerCase(Locale.ROOT).contains("bank")||b.equalsIgnoreCase("NEFT")||b.equalsIgnoreCase("RTGS"))));
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
        historyReceipt.setCellFactory(c->new TableCell<>() {
            private final Button button = new Button("View Proof");
            { button.getStyleClass().addAll("approved-button","approved-secondary-button");
              button.setGraphic(IconFactory.compactIcon("attachment",14));
              button.setOnAction(e->openReceipt(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v,empty);
                if (empty) setGraphic(null);
                else {
                    PaymentRow row=getTableView().getItems().get(getIndex());
                    button.setDisable(row.receiptPath()==null || row.receiptPath().isBlank());
                    setGraphic(button);
                }
            }
        });
        historyAction.setCellFactory(c->new TableCell<>() {
            private final Button button = new Button("⋯");
            { button.getStyleClass().addAll("approved-button","approved-secondary-button");
              button.setOnAction(e->showRowMenu(button,getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v,empty); setGraphic(empty?null:button); }
        });
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        IconFactory.applyTableHeaderIcon(historyDate, "calendar");
        IconFactory.applyTableHeaderIcon(historyMode, "payment");
        IconFactory.applyTableHeaderIcon(historyReference, "document");
        IconFactory.applyTableHeaderIcon(historyFrom, "customer");
        IconFactory.applyTableHeaderIcon(historyAmount, "currency");
        IconFactory.applyTableHeaderIcon(historyStatus, "status");
        IconFactory.applyTableHeaderIcon(historyNotes, "notes");
        IconFactory.applyTableHeaderIcon(historyReceipt, "attachment");
        IconFactory.applyTableHeaderIcon(historyAction, "actions");
        historyTable.setFixedCellSize(42);
    }

    private void refreshInvoiceAmounts() {
        sale = new SalesService().getByInvoice(SalesScreenContext.invoice());
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
        else if (sale.getDueDate()!=null && sale.getDueDate().isBefore(LocalDate.now())) invoiceStatus.getStyleClass().add("status-overdue");
        else invoiceStatus.getStyleClass().add("status-pending");
        updateBalancePreview();
    }

    private void updateBalancePreview() {
        after.setText(money(Math.max(0,sale.getBalanceAmount()-parseAmount(amount.getText()))));
    }

    @FXML private void selectFull(){ amount.setText(String.format(Locale.ROOT,"%.2f",sale.getBalanceAmount())); }
    @FXML private void selectPartial(){ if(parseAmount(amount.getText())>=sale.getBalanceAmount()) amount.clear(); amount.requestFocus(); }
    @FXML private void selectAdvance(){ amount.clear(); amount.requestFocus(); }

    @FXML private void save() {
        Path storedProof=null;
        try {
            validate();
            double value=Double.parseDouble(amount.getText().trim());
            storedProof=selectedAttachment==null?null:storeAttachment(selectedAttachment);
            supportApi.recordPayment(new SupportApiClient.PaymentRequest("SALE",sale.getId(),paymentDate.getValue().toString(),value,mode.getValue(),reference.getText().trim(),notes.getText().trim(),receivedFrom.getText().trim(),fullPayment.isSelected()?"FULL":advancePayment.isSelected()?"ADVANCE":"PARTIAL",storedProof==null?null:storedProof.toString(),"Admin"));
            NotificationService.add("Payment received for "+sale.getInvoiceNo());
            new OwnedAlert(Alert.AlertType.INFORMATION,"Payment saved successfully.").showAndWait();
            resetForm(); refreshInvoiceAmounts(); loadHistory(); refreshTimeline();
        } catch(Exception e){
            if(storedProof!=null) try{Files.deleteIfExists(storedProof);}catch(Exception ignored){}
            new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();
        }
    }

    private void validate() throws IOException {
        if(paymentDate.getValue()==null) throw new IllegalArgumentException("Select a payment date.");
        if(mode.getValue()==null || mode.getValue().isBlank()) throw new IllegalArgumentException("Select a payment mode.");
        if(receivedFrom.getText()==null || receivedFrom.getText().isBlank()) throw new IllegalArgumentException("Enter who made the payment.");
        double value=parseAmount(amount.getText());
        if(value<=0) throw new IllegalArgumentException("Received amount must be greater than zero.");
        if(!advancePayment.isSelected() && value>sale.getBalanceAmount()+0.005)
            throw new IllegalArgumentException("Enter an amount up to "+money(sale.getBalanceAmount())+".");
        if("Bank Transfer".equalsIgnoreCase(mode.getValue()) && bankAccount.getValue()==null)
            throw new IllegalArgumentException("Select or configure a bank account.");
        if(selectedAttachment!=null && Files.size(selectedAttachment)>5*1024*1024)
            throw new IllegalArgumentException("Payment proof must be 5 MB or smaller.");
    }

    private Path storeAttachment(Path source) throws IOException {
        String ext=""; String name=source.getFileName().toString(); int dot=name.lastIndexOf('.');
        if(dot>=0) ext=name.substring(dot);
        Path dir=ConfigManager.getConfigFolder().resolve("PaymentProofs").resolve(sale.getInvoiceNo());
        Files.createDirectories(dir);
        Path target=dir.resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").format(LocalDateTime.now())+ext);
        return Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
    }

    private void resetForm() {
        reference.clear(); notes.clear(); paymentDate.setValue(LocalDate.now()); selectedAttachment=null;
        attachmentName.setText("No file selected");
        amount.setText(String.format(Locale.ROOT,"%.2f",sale.getBalanceAmount()));
    }

    private void loadHistory() {
        allPayments.clear();
        try {
            for (var r : supportApi.payments("SALE", sale.getId()))
                allPayments.add(new PaymentRow(r.id(),r.date(),safe(r.reference()),safeOr(r.receivedFrom(),sale.getCustomer().getName()),safe(r.mode()),r.amount(),"Recorded",safe(r.notes()),safe(r.attachment())));
        } catch(Exception e){ new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait(); }
        historyCount.setText(allPayments.size()+" Payment"+(allPayments.size()==1?"":"s"));
        applyHistoryFilter();
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
        if(f!=null){selectedAttachment=f.toPath(); attachmentName.setText(f.getName());}
    }

    private void openReceipt(PaymentRow row) {
        try {
            Path p=Path.of(row.receiptPath());
            if(!Files.isRegularFile(p)) throw new IOException("The stored payment proof is missing.");
            Desktop.getDesktop().open(p.toFile());
        }catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }

    private void showRowMenu(Button owner, PaymentRow row) {
        ContextMenu menu=new ContextMenu();
        MenuItem view=new MenuItem("View payment proof"); view.setDisable(row.receiptPath().isBlank()); view.setOnAction(e->openReceipt(row));
        MenuItem openFolder=new MenuItem("Open proof folder"); openFolder.setDisable(row.receiptPath().isBlank());
        openFolder.setOnAction(e->{try{Desktop.getDesktop().open(Path.of(row.receiptPath()).getParent().toFile());}catch(Exception ex){new OwnedAlert(Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}});
        menu.getItems().addAll(view,openFolder); menu.show(owner, javafx.geometry.Side.BOTTOM,0,0);
    }

    @FXML private void downloadPdf(){ try{Path p=InvoicePdfService.sales(sale); Desktop.getDesktop().open(p.getParent().toFile());}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}}
    @FXML private void printInvoice(){ try{Desktop.getDesktop().print(InvoicePdfService.sales(sale).toFile());}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}}
    @FXML private void cancel(){ NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml"); }

    @FXML private void sendReceipt() {
        try {
            PaymentRow row=latestPayment();
            Path pdf=InvoicePdfService.sales(sale);
            EmailService.send(sale.getCustomer().getEmail(),"Payment receipt - "+sale.getInvoiceNo(),
                    "Thank you. We have recorded your payment of "+money(row.amount())+" for invoice "+sale.getInvoiceNo()+".",pdf);
            new OwnedAlert(Alert.AlertType.INFORMATION,"Receipt sent successfully.").showAndWait();
        }catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    }

    @FXML private void emailInvoice() {
        try {
            EmailService.send(sale.getCustomer().getEmail(),"Sales Invoice "+sale.getInvoiceNo(),PaymentMessageService.salesMessage(sale),InvoicePdfService.sales(sale));
            new OwnedAlert(Alert.AlertType.INFORMATION,"Invoice emailed successfully.").showAndWait();
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
    private String mask(String v){return v.length()<=4?v:"••••"+v.substring(v.length()-4);}
    private double parseAmount(String v){try{return Double.parseDouble(v==null?"":v.trim());}catch(Exception e){return 0;}}
    private String money(double v){return String.format("₹ %,.2f",Math.max(0,v));}
    private String safe(String v){return v==null?"":v;}
    private String safeOr(String v,String fallback){return v==null||v.isBlank()?fallback:v;}
    private String csv(String v){return "\""+safe(v).replace("\"","\"\"")+"\"";}
    private String formatDate(LocalDate d){return d==null?"Not set":d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));}
}
