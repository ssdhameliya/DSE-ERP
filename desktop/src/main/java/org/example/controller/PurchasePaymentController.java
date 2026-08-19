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
import org.example.api.support.SupportApiClient;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.model.Purchase;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.*;
import org.example.util.BusinessClock;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Purchase-only payment workspace.
 *
 * <p>The visual language mirrors the proven Sales payment workspace, while
 * accounting writes continue through the existing Support API and
 * PaymentIntegrityService. Sales payment controllers are intentionally not
 * reused or modified.</p>
 */
public final class PurchasePaymentController implements ScreenLifecycle {
    public record PaymentRow(int id, String date, String reference, String paidTo, String mode,
                             double amount, String status, String notes, String proofPath, String paymentType) {}

    @FXML private Label invoiceNo, invoiceStatus, supplier, supplierPhone, supplierEmail, invoiceDate, dueDate,
            total, paid, balance, after, summaryTotal, summaryPaid, summaryBalance, paidPercent,
            attachmentName, historyCount, recordPaymentTitle, summaryTitle, attachmentTitle, historyTitle;
    @FXML private ProgressBar paymentProgress;
    @FXML private DatePicker paymentDate;
    @FXML private ComboBox<String> mode, bankAccount;
    @FXML private TextField reference, amount, paidTo;
    @FXML private TextArea notes;
    @FXML private RadioButton fullPayment, partialPayment;
    @FXML private Button btnSavePayment;
    @FXML private VBox proofDropZone;
    @FXML private TableView<PaymentRow> historyTable;
    @FXML private TableColumn<PaymentRow, String> historyDate, historyReference, historyPaidTo,
            historyMode, historyStatus, historyNotes;
    @FXML private TableColumn<PaymentRow, Number> historyAmount;
    @FXML private TableColumn<PaymentRow, Void> historyAction;

    private final SupportApiClient supportApi = new SupportApiClient();
    private final PurchaseService purchaseService = new PurchaseService();
    private final LookupService lookupService = new LookupService();
    private final List<PaymentRow> allPayments = new ArrayList<>();
    private Purchase purchase;
    private Path selectedProof;
    private PaymentRow editingPayment;
    private boolean proofRemovalPending;

    @FXML public void initialize() {
        decorateSectionTitles();
        configureHistoryTable();
        configurePaymentForm();
        loadSelectedInvoice();
        Platform.runLater(this::wireProofDropZone);
    }

    @Override public void onScreenShown(boolean reusedFromCache) { if (reusedFromCache) loadSelectedInvoice(); }

    private void loadSelectedInvoice() {
        String selected = PurchaseScreenContext.invoice();
        if (selected == null || selected.isBlank()) return;
        purchase = purchaseService.getByInvoice(selected);
        if (purchase == null) {
            new OwnedAlert(Alert.AlertType.ERROR, "Unable to load the selected purchase invoice: " + selected).showAndWait();
            return;
        }
        configureInvoice();
        resetForm();
        loadHistory();
    }

    private void configureInvoice() {
        invoiceNo.setText(safe(purchase.getInvoiceNo()));
        invoiceStatus.setText(safeOr(purchase.getPaymentStatus(), "PENDING"));
        if (purchase.getSupplier() != null) {
            supplier.setText(safe(purchase.getSupplier().getName()));
            supplierPhone.setText(safe(purchase.getSupplier().getPhone()));
            supplierEmail.setText(safe(purchase.getSupplier().getEmail()));
            paidTo.setText(safe(purchase.getSupplier().getName()));
        }
        invoiceDate.setText(formatDate(purchase.getInvoiceDate()));
        dueDate.setText(formatDate(purchase.getDueDate()));
        refreshInvoiceAmounts();
    }

    private void configurePaymentForm() {
        paymentDate.setValue(BusinessClock.today());
        List<String> modes;
        try { modes = new ArrayList<>(lookupService.getValuesByCategoryCode("PAYMENT_MODE")); }
        catch (Exception ignored) { modes = new ArrayList<>(); }
        if (modes.isEmpty()) modes.addAll(List.of("Bank Transfer", "Cash", "Cheque", "UPI", "Card", "Other"));
        mode.setItems(FXCollections.observableArrayList(modes));
        if (modes.contains("Bank Transfer")) mode.setValue("Bank Transfer");
        else if (!modes.isEmpty()) mode.getSelectionModel().selectFirst();

        ToggleGroup paymentType = new ToggleGroup();
        fullPayment.setToggleGroup(paymentType);
        partialPayment.setToggleGroup(paymentType);
        partialPayment.setSelected(true);
        fullPayment.setOnAction(e -> selectFull());
        partialPayment.setOnAction(e -> selectPartial());
        amount.textProperty().addListener((o, a, b) -> updateBalancePreview());

        List<String> accounts = new ArrayList<>();
        try {
            for (org.example.model.Lookup lookup : lookupService.getByType("BANK ACCOUNT")) {
                if (!lookup.isActive() || lookup.getLookupValue() == null || lookup.getLookupValue().isBlank()) continue;
                String description = lookup.getDescription() == null ? "" : lookup.getDescription().trim();
                accounts.add(description.isBlank() ? lookup.getLookupValue().trim()
                        : lookup.getLookupValue().trim() + " - " + description);
            }
        } catch (Exception ignored) { }
        if (accounts.isEmpty()) {
            String bank = ConfigManager.get("payment.bankName", "").trim();
            String account = ConfigManager.get("payment.accountNumber", "").trim();
            if (!account.isBlank()) accounts.add(bank.isBlank() ? account : account + " - " + bank);
        }
        bankAccount.getItems().setAll(accounts);
        if (!accounts.isEmpty()) bankAccount.getSelectionModel().selectFirst();
        else bankAccount.setPromptText("Add BANK ACCOUNT values in Masters");
        mode.valueProperty().addListener((o, a, b) -> updateBankAccountState(b));
        updateBankAccountState(mode.getValue());
    }

    private void updateBankAccountState(String paymentMode) {
        String value = safe(paymentMode).toLowerCase(Locale.ROOT);
        boolean bankMode = value.contains("bank") || value.equals("neft") || value.equals("rtgs") || value.equals("imps");
        bankAccount.setDisable(!bankMode);
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
        historyDate.setCellValueFactory(v -> new SimpleStringProperty(formatIsoDate(v.getValue().date())));
        historyReference.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().reference()));
        historyPaidTo.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().paidTo()));
        historyMode.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().mode()));
        historyAmount.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().amount()));
        historyAmount.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value.doubleValue()));
            }
        });
        historyStatus.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status()));
        historyNotes.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().notes()));
        historyAction.setCellFactory(c -> new TableCell<>() {
            private final MenuButton actions = new MenuButton("Actions");
            private final MenuItem edit = new MenuItem("Edit Payment", IconFactory.compactIcon("edit", 14));
            private final MenuItem view = new MenuItem("View Proof", IconFactory.compactIcon("attachment", 14));
            private final MenuItem folder = new MenuItem("Remove Proof", IconFactory.compactIcon("delete", 14));
            {
                actions.getStyleClass().addAll("approved-button", "approved-secondary-button", "row-actions");
                actions.setGraphic(IconFactory.compactIcon("actions", 14));
                actions.getItems().addAll(edit, view, folder);
                edit.setOnAction(e -> editPayment(row()));
                view.setOnAction(e -> openProof(row()));
                folder.setOnAction(e -> removeStoredProof(row()));
            }
            private PaymentRow row() {
                int index = getIndex();
                return index < 0 || index >= getTableView().getItems().size() ? null : getTableView().getItems().get(index);
            }
            @Override protected void updateItem(Void ignored, boolean empty) {
                super.updateItem(ignored, empty);
                if (empty) { setGraphic(null); return; }
                PaymentRow row = row();
                boolean noProof = row == null || row.proofPath() == null || row.proofPath().isBlank();
                view.setDisable(noProof);
                boolean bankReconciliation=row!=null&&"BANK_RECONCILIATION".equalsIgnoreCase(row.paymentType());folder.setDisable(noProof||bankReconciliation);
                edit.setDisable(row == null || bankReconciliation);
                setGraphic(actions);
            }
        });
        IconFactory.applyTableHeaderIcon(historyDate, "calendar");
        IconFactory.applyTableHeaderIcon(historyMode, "payment");
        IconFactory.applyTableHeaderIcon(historyReference, "document");
        IconFactory.applyTableHeaderIcon(historyPaidTo, "supplier");
        IconFactory.applyTableHeaderIcon(historyAmount, "currency");
        IconFactory.applyTableHeaderIcon(historyStatus, "status");
        IconFactory.applyTableHeaderIcon(historyNotes, "document");
        IconFactory.applyTableHeaderIcon(historyAction, "actions");
    }

    private void refreshInvoiceAmounts() {
        if (purchase == null) return;
        Purchase fresh = purchaseService.getByInvoice(purchase.getInvoiceNo());
        if (fresh != null) purchase = fresh;
        total.setText(money(purchase.getTotalAmount()));
        paid.setText(money(purchase.getPaidAmount()));
        balance.setText(money(purchase.getBalanceAmount()));
        summaryTotal.setText(money(purchase.getTotalAmount()));
        summaryPaid.setText(money(purchase.getPaidAmount()));
        summaryBalance.setText(money(purchase.getBalanceAmount()));
        invoiceStatus.setText(safeOr(purchase.getPaymentStatus(), "PENDING"));
        double ratio = purchase.getTotalAmount() <= .005 ? 0 : Math.min(1, purchase.getPaidAmount() / purchase.getTotalAmount());
        paymentProgress.setProgress(ratio);
        paidPercent.setText(String.format(Locale.ROOT, "%.0f%% paid", ratio * 100));
        updateBalancePreview();
    }

    private void updateBalancePreview() {
        if (purchase == null) return;
        double allowable = purchase.getBalanceAmount() + (editingPayment == null ? 0 : editingPayment.amount());
        after.setText(money(Math.max(0, allowable - parseAmount(amount.getText()))));
    }

    @FXML private void selectFull() {
        if (purchase == null) return;
        amount.setText(String.format(Locale.ROOT, "%.2f", purchase.getBalanceAmount() + (editingPayment == null ? 0 : editingPayment.amount())));
    }

    @FXML private void selectPartial() {
        if (purchase == null) return;
        double allowable = purchase.getBalanceAmount() + (editingPayment == null ? 0 : editingPayment.amount());
        if (parseAmount(amount.getText()) >= allowable) amount.clear();
        amount.requestFocus();
    }

    @FXML private void save() {
        if (editingPayment != null) { saveEditedPayment(); return; }
        try {
            validatePayment();
            double value = parseAmount(amount.getText());
            int paymentId=supportApi.recordPaymentWithId(new SupportApiClient.PaymentRequest(
                    "PURCHASE", purchase.getId(), paymentDate.getValue().toString(), value, mode.getValue(),
                    reference.getText().trim(), persistedNotes(), paidTo.getText().trim(),
                    fullPayment.isSelected() ? "FULL" : "PARTIAL", null, "System"));
            String proofWarning=null;
            if(selectedProof!=null){try{supportApi.uploadPaymentAttachment(paymentId,selectedProof);}catch(Exception proofError){proofWarning="Payment was saved, but the proof could not be uploaded: "+message(proofError);}}
            NotificationService.add("Supplier payment recorded for " + purchase.getInvoiceNo());
            org.example.util.ToastManager.success(amount, "Payment saved", "Supplier payment saved successfully.");
            refreshInvoiceAmounts(); resetForm(); loadHistory();
            if(proofWarning!=null)new OwnedAlert(Alert.AlertType.WARNING,proofWarning).showAndWait();
        } catch (Exception error) {new OwnedAlert(Alert.AlertType.ERROR, message(error)).showAndWait();}
    }

    private void saveEditedPayment() {
        try {
            validatePayment();
            double newValue = parseAmount(amount.getText());
            double difference = newValue - editingPayment.amount();
            String confirmation = "ACCOUNTING CONFIRMATION\n\n" +
                    "Old Amount: " + money(editingPayment.amount()) + "\n" +
                    "New Amount: " + money(newValue) + "\n" +
                    "Difference: " + signedMoney(difference) + "\n\n" +
                    "This changes the purchase balance and payment status.\n\nContinue?";
            ButtonType choice = new OwnedAlert(Alert.AlertType.CONFIRMATION, confirmation, ButtonType.YES, ButtonType.NO)
                    .showAndWait().orElse(ButtonType.NO);
            if (choice != ButtonType.YES) return;
            supportApi.updatePayment(editingPayment.id(), new SupportApiClient.PaymentUpdateRequest(
                    paymentDate.getValue().toString(), newValue, mode.getValue(), reference.getText().trim(),
                    persistedNotes(), paidTo.getText().trim()));
            persistEditedProof();
            NotificationService.add("Supplier payment updated for " + purchase.getInvoiceNo());
            org.example.util.ToastManager.success(amount, "Payment updated", "Payment updated and purchase totals recalculated.");
            refreshInvoiceAmounts();
            resetForm();
            loadHistory();
        } catch (Exception error) {
            new OwnedAlert(Alert.AlertType.ERROR, message(error)).showAndWait();
        }
    }

    private void persistEditedProof() throws IOException {if(editingPayment==null)return;if(proofRemovalPending){supportApi.deletePaymentAttachment(editingPayment.id());proofRemovalPending=false;return;}if(selectedProof==null)return;supportApi.uploadPaymentAttachment(editingPayment.id(),selectedProof);selectedProof=null;}

    private void validatePayment() throws IOException {
        if (paymentDate.getValue() == null) throw new IllegalArgumentException("Select a payment date.");
        if (mode.getValue() == null || mode.getValue().isBlank()) throw new IllegalArgumentException("Select a payment mode.");
        if (paidTo.getText() == null || paidTo.getText().isBlank()) throw new IllegalArgumentException("Enter who was paid.");
        double value = parseAmount(amount.getText());
        if (value <= 0) throw new IllegalArgumentException("Payment amount must be greater than zero.");
        double allowable = purchase.getBalanceAmount() + (editingPayment == null ? 0 : editingPayment.amount());
        if (value > allowable + .005) throw new IllegalArgumentException("Enter an amount up to " + money(allowable) + ".");
        if (selectedProof != null && Files.size(selectedProof) > 5L * 1024L * 1024L)
            throw new IllegalArgumentException("Payment proof must be 5 MB or smaller.");
    }

    private static String bankAccountFromNotes(String value) {
        String text = safe(value);
        if (!text.startsWith("Bank Account: ")) return "";
        String rest = text.substring("Bank Account: ".length());
        int split = rest.indexOf(" | ");
        return (split < 0 ? rest : rest.substring(0, split)).trim();
    }

    private static String userNotes(String value) {
        String text = safe(value);
        if (!text.startsWith("Bank Account: ")) return text;
        int split = text.indexOf(" | ");
        return split < 0 ? "" : text.substring(split + 3).trim();
    }

    private String persistedNotes() {
        String userNotes = notes.getText() == null ? "" : notes.getText().trim();
        if (bankAccount == null || bankAccount.isDisabled() || bankAccount.getValue() == null || bankAccount.getValue().isBlank()) return userNotes;
        String account = "Bank Account: " + bankAccount.getValue().trim();
        return userNotes.isBlank() ? account : account + " | " + userNotes;
    }


    private void resetForm() {
        editingPayment = null;
        if (btnSavePayment != null) btnSavePayment.setText("Save Payment");
        reference.clear();
        notes.clear();
        paymentDate.setValue(BusinessClock.today());
        selectedProof = null; proofRemovalPending = false;
        if (purchase != null && purchase.getSupplier() != null) paidTo.setText(safe(purchase.getSupplier().getName()));
        if (attachmentName != null) attachmentName.setText("No file selected");
        partialPayment.setSelected(true);
        amount.setText(String.format(Locale.ROOT, "%.2f", purchase == null ? 0 : purchase.getBalanceAmount()));
    }

    private void loadHistory() {
        allPayments.clear();
        if (purchase == null) return;
        try {
            String supplierName = purchase.getSupplier() == null ? "" : safe(purchase.getSupplier().getName());
            for (var row : supportApi.payments("PURCHASE", purchase.getId())) {
                String paidToValue = safeOr(row.receivedFrom(), supplierName);
                String status = "BANK_RECONCILIATION".equalsIgnoreCase(row.paymentType()) ? "Reconciled"
                        : "FULL".equalsIgnoreCase(row.paymentType()) ? "Full Payment"
                        : "PARTIAL".equalsIgnoreCase(row.paymentType()) ? "Partial Payment" : "Recorded";
                allPayments.add(new PaymentRow(row.id(), row.date(), safe(row.reference()), paidToValue,
                        safe(row.mode()), row.amount(), status, safe(row.notes()), safe(row.attachment()), safe(row.paymentType())));
            }
        } catch (Exception error) {
            new OwnedAlert(Alert.AlertType.ERROR, message(error)).showAndWait();
        }
        historyTable.getItems().setAll(allPayments);
        historyCount.setText(allPayments.size() + " Payment" + (allPayments.size() == 1 ? "" : "s"));
        updateHistoryTableHeight();
    }

    private void updateHistoryTableHeight() {
        int rowCount = historyTable.getItems().size();
        double headerHeight = 40.0;
        double rowHeight = historyTable.getFixedCellSize() > 0 ? historyTable.getFixedCellSize() : 42.0;
        double height = headerHeight + (rowCount * rowHeight) + 3.0;
        historyTable.setMinHeight(Math.max(190, height));
        historyTable.setPrefHeight(Math.max(190, height));
        historyTable.setPlaceholder(new Label(rowCount == 0 ? "No payment records" : ""));
    }

    @FXML private void resetPayment() { resetForm(); }

    @FXML private void browseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose supplier payment proof");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Proof files", "*.pdf", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(amount.getScene().getWindow());
        if (file != null) { proofRemovalPending=false; setSelectedProof(file.toPath()); }
    }

    private void wireProofDropZone() {
        if (proofDropZone == null) return;
        proofDropZone.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles() && dragboard.getFiles().size() == 1 && isAllowedProof(dragboard.getFiles().getFirst().toPath()))
                event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        proofDropZone.setOnDragDropped(event -> {
            boolean completed = false;
            if (event.getDragboard().hasFiles() && event.getDragboard().getFiles().size() == 1) {
                Path path = event.getDragboard().getFiles().getFirst().toPath();
                if (isAllowedProof(path)) { proofRemovalPending=false; setSelectedProof(path); completed = true; }
            }
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private static boolean isAllowedProof(Path path) {
        String value = path == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return value.endsWith(".pdf") || value.endsWith(".png") || value.endsWith(".jpg") || value.endsWith(".jpeg");
    }

    private void setSelectedProof(Path path) {
        selectedProof = path;
        attachmentName.setText(path == null ? "No file selected" : path.getFileName().toString());
    }

    @FXML private void previewProof(){try{Path path=selectedProof;if(path==null&&editingPayment!=null&&!proofRemovalPending&&!safe(editingPayment.proofPath()).isBlank())path=materializePaymentProof(supportApi.paymentAttachment(editingPayment.id()));if(path==null)throw new IOException("No payment proof is attached.");if(!Files.isRegularFile(path))throw new IOException("The payment proof is unavailable.");Desktop.getDesktop().open(path.toFile());}catch(Exception error){new OwnedAlert(Alert.AlertType.ERROR,message(error)).showAndWait();}}
    @FXML private void removeProof(){boolean hasSelected=selectedProof!=null;boolean hasExisting=editingPayment!=null&&!safe(editingPayment.proofPath()).isBlank()&&!proofRemovalPending;if(!hasSelected&&!hasExisting)return;if(new OwnedAlert(Alert.AlertType.CONFIRMATION,"Remove the payment proof?",ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;selectedProof=null;proofRemovalPending=hasExisting;attachmentName.setText(proofRemovalPending?"Proof will be removed when payment is updated":"No file selected");}

    private void editPayment(PaymentRow row) {
        if (row == null) return;
        if ("BANK_RECONCILIATION".equalsIgnoreCase(row.paymentType())) {
            new OwnedAlert(Alert.AlertType.WARNING,
                    "Bank-reconciled payments must be changed from Bank Statement using the reversal/reconciliation workflow.").showAndWait();
            return;
        }
        editingPayment = row;
        try { paymentDate.setValue(LocalDate.parse(row.date())); }
        catch (Exception ignored) { paymentDate.setValue(BusinessClock.today()); }
        if (!mode.getItems().contains(row.mode())) mode.getItems().add(row.mode());
        mode.setValue(row.mode());
        reference.setText(row.reference());
        paidTo.setText(row.paidTo());
        amount.setText(String.format(Locale.ROOT, "%.2f", row.amount()));
        String storedAccount = bankAccountFromNotes(row.notes());
        if (!storedAccount.isBlank()) {
            if (!bankAccount.getItems().contains(storedAccount)) bankAccount.getItems().add(storedAccount);
            bankAccount.setValue(storedAccount);
        }
        notes.setText(userNotes(row.notes()));
        selectedProof = null; proofRemovalPending=false;
        attachmentName.setText(row.proofPath() == null || row.proofPath().isBlank()
                ? "No proof attached" : "Existing proof: " + Path.of(row.proofPath()).getFileName());
        partialPayment.setSelected(true);
        if (btnSavePayment != null) btnSavePayment.setText("Update Payment");
        amount.requestFocus();
        amount.selectAll();
        updateBalancePreview();
    }

    private void openProof(PaymentRow row) {
        try{if(row==null||safe(row.proofPath()).isBlank())return;Path path=materializePaymentProof(supportApi.paymentAttachment(row.id()));if(path==null||!Files.isRegularFile(path))throw new IOException("The stored payment proof is missing.");Desktop.getDesktop().open(path.toFile());}
        catch(Exception error){new OwnedAlert(Alert.AlertType.ERROR,message(error)).showAndWait();}
    }

    private void removeStoredProof(PaymentRow row){
        if(row==null||safe(row.proofPath()).isBlank()||"BANK_RECONCILIATION".equalsIgnoreCase(row.paymentType()))return;
        if(new OwnedAlert(Alert.AlertType.CONFIRMATION,"Remove this payment proof?",ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
        try{supportApi.deletePaymentAttachment(row.id());loadHistory();org.example.util.ToastManager.success(historyTable,"Proof removed","Payment proof removed.");}
        catch(Exception error){new OwnedAlert(Alert.AlertType.ERROR,message(error)).showAndWait();}
    }

    private Path materializePaymentProof(SupportApiClient.DownloadedAttachment download)throws IOException{
        if(download==null||download.data()==null||download.data().length==0)return null;Path folder=WorkspaceManager.getTempFolder().resolve("AttachmentPreview");Files.createDirectories(folder);String raw=download.fileName()==null?"payment-proof":download.fileName();String name=raw.replaceAll("[^A-Za-z0-9._-]","_");if(name.isBlank())name="payment-proof";Path target=folder.resolve(System.currentTimeMillis()+"-"+name);Files.write(target,download.data());target.toFile().deleteOnExit();return target;
    }

    @FXML private void printInvoice() {
        try { Desktop.getDesktop().print(InvoicePdfService.purchase(purchase).toFile()); }
        catch (Exception error) { new OwnedAlert(Alert.AlertType.ERROR, message(error)).showAndWait(); }
    }

    @FXML private void emailInvoice() {
        String recipient = purchase == null || purchase.getSupplier() == null ? "" : safe(purchase.getSupplier().getEmail());
        String subject = purchase == null ? "Purchase Invoice" : "Purchase Invoice " + purchase.getInvoiceNo();
        String stage = "validating supplier email";
        try {
            if (purchase == null) throw new IllegalStateException("Purchase invoice is not loaded.");
            if (recipient.isBlank()) throw new IllegalStateException("Supplier email is missing. Update Supplier Master and try again.");
            stage = "generating the purchase invoice PDF";
            Path pdf = InvoicePdfService.purchase(purchase);
            stage = "sending the email";
            EmailService.send(recipient, subject,
                    "Dear " + safe(purchase.getSupplier().getName()) + ",\n\nPlease find the purchase invoice attached.\n\nRegards,\n" +
                            ConfigManager.get("company.name", "DSE ERP"), pdf);
            purchaseService.markEmailSent(purchase.getId());
            logCommunication(recipient, subject, "SENT", null);
            org.example.util.ToastManager.success(amount, "Email sent", "Purchase invoice emailed successfully.");
        } catch (Exception error) {
            logCommunication(recipient, subject, "FAILED", stage + ": " + message(error));
            new OwnedAlert(Alert.AlertType.ERROR, message(error)).showAndWait();
        }
    }

    private void logCommunication(String recipient, String subject, String status, String error) {
        if (purchase == null || purchase.getId() <= 0) return;
        try {
            String user = SessionService.current() == null ? "System" : SessionService.current().getFullName();
            supportApi.communication(new SupportApiClient.CommunicationRequest(
                    "PURCHASE", purchase.getId(), "EMAIL", safe(recipient), safe(subject), status, error, user));
        } catch (Exception ignored) { }
    }

    @FXML private void cancel() { if(purchase!=null)LinkedRecordContext.open("PURCHASE",purchase.getId(),purchase.getInvoiceNo(),"VIEW","Purchase Payment"); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml"); }

    private static double parseAmount(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Double.parseDouble(value.replace(",", "").replace("₹", "").trim()); }
        catch (Exception ignored) { return 0; }
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String safeOr(String value, String fallback) { String clean = safe(value); return clean.isBlank() ? safe(fallback) : clean; }
    private static String formatDate(LocalDate date) { return date == null ? "Not set" : BusinessClock.formatDate(date); }
    private static String formatIsoDate(String value) {
        try { return value == null || value.isBlank() ? "" : BusinessClock.formatDate(LocalDate.parse(value.substring(0, Math.min(10, value.length())))); }
        catch (Exception ignored) { return value == null ? "" : value; }
    }
    private static String money(double value) { return String.format(Locale.of("en", "IN"), "₹ %,.2f", value); }
    private static String signedMoney(double value) { return (value > 0 ? "+" : "") + money(value); }
    private static String message(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String value = root.getMessage();
        return value == null || value.isBlank() ? root.getClass().getSimpleName() : value;
    }
}
