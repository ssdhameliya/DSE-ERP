package org.example.controller;

import org.example.util.BusinessClock;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;
import org.example.service.LookupService;
import org.example.navigation.ScreenLifecycle;
import org.example.service.NotificationService;
import org.example.service.FinanceService;
import org.example.api.operations.OperationsApiClient;
import org.example.api.bank.BankStatementApiClient;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.UiTaskExecutor;
import org.example.util.RegisterDetailDrawer;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class BankExpenseController implements ScreenLifecycle {
    public enum Mode { BANK, EXPENSE }
    private static volatile Mode requestedMode;
    private static volatile ExpensePrefill requestedExpensePrefill;
    private static volatile BankEntryPrefill requestedBankEntryPrefill;
    private static volatile Integer requestedLinkedEntryId;
    private static volatile boolean requestedCreateEntry;
    public record ExpensePrefill(long statementTransactionId,String date,double amount,String reference,String description,String accountName,String paymentMode){}
    public record BankEntryPrefill(long statementTransactionId,String date,double debit,double credit,String reference,String description,String accountName,String paymentMode){}
    public static void requestExpensePrefill(ExpensePrefill prefill){ requestedExpensePrefill=prefill; requestedMode=Mode.EXPENSE; }
    public static void requestBankEntryPrefill(BankEntryPrefill prefill){ requestedBankEntryPrefill=prefill; requestedMode=Mode.BANK; }
    private static ExpensePrefill consumeExpensePrefill(){ExpensePrefill p=requestedExpensePrefill;requestedExpensePrefill=null;return p;}
    private static BankEntryPrefill consumeBankEntryPrefill(){BankEntryPrefill p=requestedBankEntryPrefill;requestedBankEntryPrefill=null;return p;}
    public static void requestMode(Mode mode) { requestedMode = mode == null ? Mode.BANK : mode; }
    public static void requestNewEntry(Mode mode){requestedMode=mode==null?Mode.BANK:mode;requestedCreateEntry=true;}
    public static void requestLinkedEntry(Mode mode,Integer entryId){requestedMode=mode==null?Mode.BANK:mode;requestedLinkedEntryId=entryId;}

    private static Mode consumeRequestedMode() {
        Mode requested = requestedMode;
        requestedMode = null;
        return requested;
    }

    @FXML private Label lblTitle, lblSubtitle, formTitle, listTitle;
    @FXML private Button btnBankMode, btnExpenseMode, btnBankRecon, saveButton, addButton, btnResetFilters, btnRefreshEntries;
    @FXML private Label kpi1Icon,kpi1Label,kpi1Value,kpi1Note,kpi2Icon,kpi2Label,kpi2Value,kpi2Note,kpi3Icon,kpi3Label,kpi3Value,kpi3Note,kpi4Icon,kpi4Label,kpi4Value,kpi4Note;
    @FXML private DatePicker entryDate, filterFrom, filterTo;
    @FXML private VBox bankOnlyFields, expenseOnlyFields, billBox, entryFormPanel;
    @FXML private HBox workspaceRow;
    @FXML private ComboBox<String> bankAccount, expenseCategory, expenseAccount, paymentMode, typeFilter;
    @FXML private RadioButton creditRadio, debitRadio;
    @FXML private TextField referenceNo, amount, searchField;
    @FXML private StackPane financeSearchIcon;
    @FXML private TextArea description;
    @FXML private Label billName, showingLabel, pageLabel;
    @FXML private TableView<EntryRow> table;
    @FXML private TableColumn<EntryRow,String> colDate,colType,colDescription,colAccount,colMode,colReference,colMatch;
    @FXML private TableColumn<EntryRow,Number> colAmount;
    @FXML private TableColumn<EntryRow,Void> colAction;

    private final ToggleGroup typeGroup = new ToggleGroup();
    private final LookupService lookupService = new LookupService();
    private final FinanceService financeService = new FinanceService();
    private final List<EntryRow> filtered = new ArrayList<>();
    private Mode mode;
    private File selectedBill;
    private Integer editingId;
    private long editingVersion;
    private Long reconciliationStatementId;
    private Double reconciliationAmount;
    private final BankStatementApiClient bankStatementApi = new BankStatementApiClient();
    private int currentPage = 0;
    private int totalPages = 0;
    private long totalRows = 0;
    private static final int PAGE_SIZE = 8;
    private Dialog<ButtonType> entryDialog;
    private RegisterDetailDrawer detailDrawer;
    private EntryRow detailRow;
    private boolean explicitRefreshPending;

    @FXML public void initialize() {
        entryDate.setValue(BusinessClock.today());
        creditRadio.setToggleGroup(typeGroup); debitRadio.setToggleGroup(typeGroup); creditRadio.setSelected(true);
        installKpiIcons();
        loadMasterLookups();
        loadAccounts();
        paymentMode.setOnShowing(e -> loadMasterLookups());
        expenseCategory.setOnShowing(e -> loadMasterLookups());
        bankAccount.setOnShowing(e -> loadAccounts());
        expenseAccount.setOnShowing(e -> loadAccounts());
        configureTable();
        if (workspaceRow != null && entryFormPanel != null) { workspaceRow.getChildren().remove(entryFormPanel); entryFormPanel.setManaged(true); entryFormPanel.setVisible(true); }
        installDetailDrawer();
        if(btnBankMode!=null)btnBankMode.setGraphic(IconFactory.compactIcon("bank",15));
        if(btnExpenseMode!=null)btnExpenseMode.setGraphic(IconFactory.compactIcon("payment",15));
        if(btnBankRecon!=null)btnBankRecon.setGraphic(IconFactory.compactIcon("link",15));
        if(financeSearchIcon!=null)financeSearchIcon.getChildren().setAll(IconFactory.compactIcon("search",14));
        if(btnResetFilters!=null)btnResetFilters.setGraphic(IconFactory.compactIcon("reset",14));
        if(btnRefreshEntries!=null)btnRefreshEntries.setGraphic(IconFactory.compactIcon("refresh",14));
        LocalDate today=BusinessClock.today();
        org.example.util.RegisterUiSupport.setCurrentYearRange(filterFrom,filterTo,today);
        if(filterFrom!=null)filterFrom.valueProperty().addListener((o,a,b)->applyFilters());
        if(filterTo!=null)filterTo.valueProperty().addListener((o,a,b)->applyFilters());
        Mode initialMode = consumeRequestedMode();
        mode = initialMode == null ? Mode.BANK : initialMode;
        applyMode(mode);
        // Statement transfer context is applied once the page is attached in onScreenShown().
        // This avoids an initialize/onScreenShown lifecycle race on the cached BankExpense page.
    }

    private void installKpiIcons(){setKpiIcon(kpi1Icon,"bank");setKpiIcon(kpi2Icon,"credit");setKpiIcon(kpi3Icon,"balance");setKpiIcon(kpi4Icon,"payment");}
    private void setKpiIcon(Label label,String semantic){if(label!=null){label.setText("");label.setGraphic(IconFactory.icon(semantic,20));label.getProperties().put("erp-icon-preserve",true);}}

    private void applyRequestedExpensePrefill(){
        ExpensePrefill p=requestedExpensePrefill; if(p==null)return;
        if(mode!=Mode.EXPENSE)applyMode(Mode.EXPENSE);
        reconciliationStatementId=p.statementTransactionId();
        reconciliationAmount=p.amount();
        try{entryDate.setValue(LocalDate.parse(p.date()));}catch(Exception ignored){}
        amount.setText(String.format(Locale.ROOT,"%.2f",p.amount()));
        amount.setEditable(false);
        referenceNo.setText(safe(p.reference(),""));
        description.setText(safe(p.description(),""));
        if(p.accountName()!=null&&!p.accountName().isBlank()){ if(!expenseAccount.getItems().contains(p.accountName()))expenseAccount.getItems().add(0,p.accountName()); expenseAccount.setValue(p.accountName()); }
        if(p.paymentMode()!=null&&!p.paymentMode().isBlank()){ if(!paymentMode.getItems().contains(p.paymentMode()))paymentMode.getItems().add(0,p.paymentMode()); paymentMode.setValue(p.paymentMode()); }
        saveButton.setText("Create Expense from Statement");
        requestedExpensePrefill=null; showEntryDialog();
    }

    private void applyRequestedBankEntryPrefill(){
        BankEntryPrefill p=requestedBankEntryPrefill; if(p==null)return;
        if(mode!=Mode.BANK)applyMode(Mode.BANK);
        reconciliationStatementId=p.statementTransactionId();
        double value=p.credit()>0?p.credit():p.debit();
        reconciliationAmount=value;
        try{entryDate.setValue(LocalDate.parse(p.date()));}catch(Exception ignored){}
        amount.setText(String.format(Locale.ROOT,"%.2f",value));
        amount.setEditable(false);
        referenceNo.setText(safe(p.reference(),"")); description.setText(safe(p.description(),""));
        if(p.credit()>0)creditRadio.setSelected(true);else debitRadio.setSelected(true);
        if(p.accountName()!=null&&!p.accountName().isBlank()){ if(!bankAccount.getItems().contains(p.accountName()))bankAccount.getItems().add(0,p.accountName()); bankAccount.setValue(p.accountName()); }
        if(p.paymentMode()!=null&&!p.paymentMode().isBlank()){ if(!paymentMode.getItems().contains(p.paymentMode()))paymentMode.getItems().add(0,p.paymentMode()); paymentMode.setValue(p.paymentMode()); }
        saveButton.setText("Create Bank Entry from Statement");
        requestedBankEntryPrefill=null; showEntryDialog();
    }

    private void loadMasterLookups() {
        String selectedPaymentMode = paymentMode == null ? null : paymentMode.getValue();
        List<String> paymentModes;
        List<String> expenseCategories;
        try {
            paymentModes = lookupService.getValuesByCategoryCode("PAYMENT_MODE");
            expenseCategories = lookupService.getValuesByCategoryCode("EXPENSE_CATEGORY");
        } catch (Exception failure) {
            System.err.println("Finance master lookup load failed: " + userMessage(failure));
            paymentModes = List.of();
            expenseCategories = List.of();
        }

        paymentMode.getItems().setAll(paymentModes);
        expenseCategory.getItems().setAll(expenseCategories);

        if (selectedPaymentMode != null && paymentModes.contains(selectedPaymentMode)) {
            paymentMode.setValue(selectedPaymentMode);
        } else if (!paymentModes.isEmpty()) {
            paymentMode.getSelectionModel().selectFirst();
        } else {
            paymentMode.getSelectionModel().clearSelection();
        }
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        LinkedRecordContext.Target linkedTarget=LinkedRecordContext.consume("FINANCE");
        if(linkedTarget!=null && linkedTarget.recordId()!=null){
            requestedLinkedEntryId=linkedTarget.recordId();
        }
        // BankExpense.fxml is intentionally cached. On reuse, refresh the master
        // values and consume the navigation request so the cached controller cannot
        // keep whichever tab happened to be open previously.
        if (reusedFromCache) { loadMasterLookups(); loadAccounts(); }
        Mode requested = consumeRequestedMode();
        if (requested != null && requested != mode) {
            applyMode(requested);
        } else if (reusedFromCache) {
            loadMetrics();
            applyFilters();
        }
        applyRequestedExpensePrefill();
        applyRequestedBankEntryPrefill();
        revealRequestedLinkedEntry();
        if(requestedCreateEntry){requestedCreateEntry=false;javafx.application.Platform.runLater(this::focusForm);}
        org.example.util.OperationalUiSupport.focusWorkArea(table);
    }

    private void revealRequestedLinkedEntry(){
        Integer id=requestedLinkedEntryId;if(id==null)return;
        requestedLinkedEntryId=null;
        UiTaskExecutor.submitLatest("finance-linked-entry",()->financeService.get(id),entry->{
            if(entry==null){org.example.util.ModernDialog.warning(table,"Linked record unavailable","Expense / Bank entry not found","The linked finance entry is no longer available.");return;}
            String raw=safe(entry.voucherType(),"").toUpperCase(Locale.ROOT);Mode entryMode=raw.contains("EXPENSE")?Mode.EXPENSE:Mode.BANK;if(mode!=entryMode)applyMode(entryMode);
            EntryRow row=toRow(entry);table.getItems().setAll(row);filtered.clear();filtered.add(row);totalRows=1;totalPages=1;currentPage=0;renderPage();table.getSelectionModel().select(row);table.scrollTo(row);showEntryDetails(row);
            org.example.util.PerformanceMonitor.event("linked-navigation",(entryMode==Mode.EXPENSE?"EXPENSE":"BANK_ENTRY")+" -> "+id);
        },failure->error("Unable to open linked finance entry: "+failure.getMessage()));
    }

    private void loadAccounts() {
        List<String> accounts = new ArrayList<>();
        try {
            for (org.example.model.Lookup l : lookupService.getByCategoryCode("BANK_ACCOUNT")) {
                if (!l.isActive() || l.getLookupValue()==null || l.getLookupValue().isBlank()) continue;
                String bankName = l.getDescription()==null?"":l.getDescription().trim();
                accounts.add(bankName.isBlank()?l.getLookupValue().trim():l.getLookupValue().trim()+" - "+bankName);
            }
        } catch (Exception ignored) {}
        if (accounts.isEmpty()) {
            String bank = ConfigManager.get("payment.bankName", "").trim();
            String number = ConfigManager.get("payment.accountNumber", "").trim();
            if (!number.isBlank()) accounts.add(bank.isBlank()?number:number+" - "+bank);
        }
        if (accounts.isEmpty()) accounts.add("Cash / General");
        bankAccount.getItems().setAll(accounts); expenseAccount.getItems().setAll(accounts);
        if (!accounts.isEmpty()) { bankAccount.setValue(accounts.get(0)); expenseAccount.setValue(accounts.get(0)); }
    }

    @FXML private void showBankMode(){ applyMode(Mode.BANK); }
    @FXML private void showExpenseMode(){ applyMode(Mode.EXPENSE); }
    @FXML private void showBankReconciliation(){ DashboardController.navigateFromChild("Bank Statement","/fxml/pages/BankStatement.fxml",null); }

    private void applyMode(Mode next) {
        mode = next; currentPage = 0; clearForm(); closeEntryDetails();
        boolean bank = mode == Mode.BANK;
        lblTitle.setText(bank ? "Bank Entry" : "Expense Entry");
        lblSubtitle.setText(bank ? "Manage all bank transactions in one place" : "Manage and track all your business expenses");
        formTitle.setText(bank ? "Add Bank Entry" : "Add Expense"); listTitle.setText(bank ? "Bank Entries" : "Expense Entries");
        saveButton.setText(bank ? "Save Entry" : "Save Expense"); addButton.setText(bank ? "Add Entry" : "Add Expense");
        bankOnlyFields.setVisible(bank); bankOnlyFields.setManaged(bank); expenseOnlyFields.setVisible(!bank); expenseOnlyFields.setManaged(!bank); billBox.setVisible(!bank); billBox.setManaged(!bank);
        styleModeButton(btnBankMode, bank); styleModeButton(btnExpenseMode, !bank);
        if (bank) {
            typeFilter.getItems().setAll("All Types", "Deposit", "Withdrawal");
        } else {
            List<String> categoryFilters = new ArrayList<>();
            categoryFilters.add("All Categories");
            categoryFilters.addAll(expenseCategory.getItems());
            typeFilter.getItems().setAll(categoryFilters);
        }
        typeFilter.getSelectionModel().selectFirst();
        colType.setText(bank ? "Type" : "Category"); colMode.setVisible(!bank);
        configureHeaderIcons(); loadMetrics(); applyFilters();
    }

    private void styleModeButton(Button button, boolean selected) {
        button.getStyleClass().removeAll("finance-mode-selected-bank","finance-mode-selected-expense");
        if (selected) button.getStyleClass().add(mode == Mode.BANK ? "finance-mode-selected-bank" : "finance-mode-selected-expense");
    }

    private void configureTable() {
        colDate.setCellValueFactory(v->v.getValue().date); colType.setCellValueFactory(v->v.getValue().type); colDescription.setCellValueFactory(v->v.getValue().description);
        colAccount.setCellValueFactory(v->v.getValue().account); colMode.setCellValueFactory(v->v.getValue().paymentMode); colReference.setCellValueFactory(v->v.getValue().reference); colAmount.setCellValueFactory(v->v.getValue().amount); colMatch.setCellValueFactory(v->v.getValue().match);
        colAmount.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(Number n, boolean empty){ super.updateItem(n,empty); if(empty||n==null){setText(null);setStyle("");return;} EntryRow row=getTableRow()==null?null:getTableRow().getItem(); setText(money(n.doubleValue())); boolean positive=row!=null && row.rawType.contains("DEPOSIT"); setStyle("-fx-text-fill:" + (positive ? "#22c55e" : "#ef4444") + ";-fx-font-weight:800;"); }});
        colMatch.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(String text, boolean empty){ super.updateItem(text,empty); setText(null); setGraphic(null); if(empty||text==null||text.isBlank()||getIndex()<0||getIndex()>=getTableView().getItems().size())return; EntryRow row=getTableView().getItems().get(getIndex()); Hyperlink link=new Hyperlink(row.statementTransactionId!=null?"View Bank Statement":text); link.getStyleClass().add("bank-match-link"); link.setGraphic(IconFactory.compactIcon(row.statementTransactionId!=null?"bank":"link",13)); link.setOnAction(e->{if(row.statementTransactionId!=null)openBankStatement(row);else openLinkedErp(row);}); setGraphic(link);} });
        colType.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(String s, boolean empty){ super.updateItem(s,empty); setText(empty?null:s); setGraphic(null); getStyleClass().removeAll("finance-chip-green","finance-chip-red","finance-chip-purple","finance-chip-blue","finance-chip-orange","finance-chip-teal"); if(!empty&&s!=null){getStyleClass().add(chipStyle(s));String v=s.toLowerCase(Locale.ROOT);String semantic=v.contains("deposit")?"credit":v.contains("withdraw")?"debit":v.contains("travel")||v.contains("transport")?"delivery":v.contains("office")?"notes":v.contains("marketing")||v.contains("maintenance")?"category":"payment";setGraphic(IconFactory.compactIcon(semantic,13));setGraphicTextGap(5);setContentDisplay(ContentDisplay.LEFT);}}});
        colAction.setCellFactory(c->new TableCell<>() { private final MenuButton actions=new MenuButton("Actions"); private EntryRow row; { actions.getStyleClass().addAll("bank-row-action","table-action-menu","approved-row-action"); actions.setGraphic(IconFactory.compactIcon("actions",15)); actions.setOnShowing(e->rebuild()); IconFactory.decorateActionMenu(actions); } private void rebuild(){actions.getItems().clear();if(row==null)return;String noun=mode==Mode.EXPENSE?"Expense":"Bank Entry";MenuItem view=new MenuItem("View "+noun,IconFactory.compactIcon("view",15));view.setOnAction(e->{table.getSelectionModel().select(row);showEntryDetails(row);});actions.getItems().add(view);if(row.statementTransactionId!=null){MenuItem statement=new MenuItem("Open Bank Statement",IconFactory.compactIcon("bank",15));statement.setOnAction(e->openBankStatement(row));actions.getItems().add(statement);}if(hasLinkedErp(row)){MenuItem linked=new MenuItem("Open Linked ERP Record",IconFactory.compactIcon("link",15));linked.setOnAction(e->openLinkedErp(row));actions.getItems().add(linked);}MenuItem edit=new MenuItem("Edit "+noun,IconFactory.compactIcon("edit",15));edit.setOnAction(e->editRow(row));MenuItem del=new MenuItem("Delete "+noun,IconFactory.compactIcon("delete",15));del.getStyleClass().add("danger-menu-item");del.setOnAction(e->deleteRow(row));actions.getItems().addAll(edit,del);} @Override protected void updateItem(Void v, boolean empty){super.updateItem(v,empty);row=empty||getIndex()<0||getIndex()>=getTableView().getItems().size()?null:getTableView().getItems().get(getIndex());if(row==null){actions.hide();actions.getItems().clear();setGraphic(null);}else{rebuild();setGraphic(actions);}} });
        table.setPlaceholder(new Label("No entries found"));
        table.setRowFactory(tv->{TableRow<EntryRow> row=new TableRow<>();row.setOnMouseClicked(e->{if(e.getButton()!=javafx.scene.input.MouseButton.PRIMARY||e.getClickCount()!=1||row.isEmpty()||org.example.util.RegisterUiSupport.isInteractiveTableTarget(e.getPickResult().getIntersectedNode(),row))return;EntryRow clicked=row.getItem();if(detailDrawer!=null&&detailDrawer.isOpen()&&detailRow==clicked){closeEntryDetails();}else{table.getSelectionModel().select(clicked);showEntryDetails(clicked);}e.consume();});return row;});
        colDate.setMinWidth(85);       colDate.setPrefWidth(95);
        colType.setMinWidth(100);      colType.setPrefWidth(115);
        colDescription.setMinWidth(180); colDescription.setPrefWidth(260);
        colAccount.setMinWidth(130);   colAccount.setPrefWidth(180);
        colMode.setMinWidth(90);       colMode.setPrefWidth(110);
        colReference.setMinWidth(115); colReference.setPrefWidth(150);
        colAmount.setMinWidth(110);    colAmount.setPrefWidth(130);
        colMatch.setMinWidth(125);     colMatch.setPrefWidth(150);
        configureHeaderIcons();
    }

    private void configureHeaderIcons(){ IconFactory.applyTableHeaderIcon(colDate,"calendar"); IconFactory.applyTableHeaderIcon(colType, mode==Mode.EXPENSE?"category":"status"); IconFactory.applyTableHeaderIcon(colDescription,"notes"); IconFactory.applyTableHeaderIcon(colAccount,"bank"); IconFactory.applyTableHeaderIcon(colMode,"payment"); IconFactory.applyTableHeaderIcon(colReference,"reference"); IconFactory.applyTableHeaderIcon(colAmount,"currency"); IconFactory.applyTableHeaderIcon(colMatch,"link"); IconFactory.applyTableHeaderIcon(colAction,"actions"); }

    private void loadMetrics() {
        Mode requestedMode = mode;
        UiTaskExecutor.submitLatest("finance-metrics-" + (requestedMode == null ? "unknown" : requestedMode.name()), financeService::metrics, m -> {
            if (requestedMode != mode || m == null) return;
            if (mode == Mode.BANK) {
                setKpi(kpi1Label,kpi1Value,kpi1Note,"Bank Balance",money(m.bankBalance()),"Current balance");
                setKpi(kpi2Label,kpi2Value,kpi2Note,"Deposits",money(m.deposits()),m.depositCount()+" entries");
                setKpi(kpi3Label,kpi3Value,kpi3Note,"Withdrawals",money(m.withdrawals()),m.withdrawalCount()+" entries");
                setKpi(kpi4Label,kpi4Value,kpi4Note,"Pending Reconcile",m.pendingReconcile()+" entries",money(m.pendingAmount()));
            } else {
                setKpi(kpi1Label,kpi1Value,kpi1Note,"Total Expenses (This Month)",money(m.monthExpenses()),m.monthExpenseCount()+" entries");
                setKpi(kpi2Label,kpi2Value,kpi2Note,"Total Expenses (This Year)",money(m.yearExpenses()),"Year to date");
                setKpi(kpi3Label,kpi3Value,kpi3Note,"Top Expense Category",safe(m.topExpenseCategory(),"No expenses"),money(m.topExpenseAmount()));
                setKpi(kpi4Label,kpi4Value,kpi4Note,"Pending Reconcile",m.pendingReconcile()+" entries",money(m.pendingAmount()));
            }
        }, failure -> {
            String unavailable = "Unavailable";
            setKpi(kpi1Label,kpi1Value,kpi1Note,mode==Mode.BANK?"Bank Balance":"Total Expenses (This Month)",unavailable,"Server data unavailable");
            setKpi(kpi2Label,kpi2Value,kpi2Note,mode==Mode.BANK?"Deposits":"Total Expenses (This Year)",unavailable,"Server data unavailable");
            setKpi(kpi3Label,kpi3Value,kpi3Note,mode==Mode.BANK?"Withdrawals":"Top Expense Category",unavailable,"Server data unavailable");
            setKpi(kpi4Label,kpi4Value,kpi4Note,"Pending Reconcile",unavailable,"Server data unavailable");
            System.err.println("Finance metrics load failed: " + userMessage(failure));
        });
    }
    private void setKpi(Label l,Label v,Label n,String a,String b,String c){l.setText(a);v.setText(b);n.setText(c);}

    @FXML private void saveEntry() {
        try {
            validate();
            double value = reconciliationAmount != null
                    ? reconciliationAmount
                    : Double.parseDouble(amount.getText().replace(",", "").trim());
            String rawType= mode==Mode.BANK ? (creditRadio.isSelected()?"BANK DEPOSIT":"BANK WITHDRAWAL") : "EXPENSE";
            String category= mode==Mode.EXPENSE ? text(expenseCategory) : (creditRadio.isSelected()?"Deposit":"Withdrawal");
            String account= mode==Mode.BANK ? bankAccount.getValue() : expenseAccount.getValue();
            if (mode==Mode.EXPENSE && reconciliationStatementId!=null && editingId==null) {
                bankStatementApi.expense(reconciliationStatementId,new BankStatementApiClient.ExpenseRequest(category,account,paymentMode.getValue(),description.getText().trim(),selectedBill==null?null:selectedBill.getAbsolutePath(),currentUser()));
                reconciliationStatementId=null;
            } else if (mode==Mode.BANK && reconciliationStatementId!=null && editingId==null) {
                bankStatementApi.bankEntry(reconciliationStatementId,new BankStatementApiClient.BankEntryRequest(account,paymentMode.getValue(),description.getText().trim(),currentUser()));
                reconciliationStatementId=null;
            } else {
                OperationsApiClient.FinanceEntry dto = new OperationsApiClient.FinanceEntry(editingId, null, rawType, entryDate.getValue().toString(), category, referenceNo.getText().trim(), value, paymentMode.getValue(), description.getText().trim(), account, selectedBill==null?null:selectedBill.getAbsolutePath(), false, editingId==null?0L:editingVersion);
                if (editingId == null) financeService.save(dto); else financeService.update(dto);
            }
            boolean wasUpdate = editingId != null;
            String actionLabel = mode == Mode.EXPENSE ? "Expense" : "Bank Entry";
            NotificationService.add((wasUpdate ? actionLabel + " updated" : actionLabel + " created") + ": " + money(value));
            clearForm(); closeEntryDialog(); loadMetrics(); applyFilters();
            success(
                wasUpdate ? actionLabel + " Updated" : actionLabel + " Saved",
                (wasUpdate ? actionLabel + " was updated successfully." : actionLabel + " was saved successfully.")
                    + "\n\nAmount: " + money(value)
            );
        } catch (Exception e) { error(userMessage(e)); }
    }

    private void validate(){ if(entryDate.getValue()==null)throw new IllegalArgumentException("Select a date."); if(description.getText().trim().isEmpty())throw new IllegalArgumentException("Enter a description."); if(amount.getText().trim().isEmpty())throw new IllegalArgumentException("Enter an amount."); double v; try{v=Double.parseDouble(amount.getText().replace(",","").trim());}catch(Exception e){throw new IllegalArgumentException("Enter a valid amount.");} if(v<=0)throw new IllegalArgumentException("Amount must be greater than zero."); if(paymentMode.getItems().isEmpty())throw new IllegalArgumentException("No Payment Mode is configured in Master Data."); if(paymentMode.getValue()==null)throw new IllegalArgumentException("Select payment mode."); if(mode==Mode.BANK&&bankAccount.getValue()==null)throw new IllegalArgumentException("Select bank account."); if(mode==Mode.EXPENSE&&expenseCategory.getItems().isEmpty())throw new IllegalArgumentException("No Expense Category is configured in Master Data."); if(mode==Mode.EXPENSE&&(expenseCategory.getValue()==null||expenseCategory.getValue().isBlank()||expenseAccount.getValue()==null))throw new IllegalArgumentException("Select expense category and account."); }

    @FXML private void clearForm(){ reconciliationStatementId=null; reconciliationAmount=null; if(entryDate!=null)entryDate.setValue(BusinessClock.today()); if(referenceNo!=null)referenceNo.clear(); if(description!=null)description.clear(); if(amount!=null){amount.clear();amount.setEditable(true);} if(creditRadio!=null)creditRadio.setSelected(true); if(expenseCategory!=null)expenseCategory.getSelectionModel().clearSelection(); editingId=null; editingVersion=0L; selectedBill=null; if(billName!=null)billName.setText("No file selected"); if(saveButton!=null)saveButton.setText(mode==Mode.EXPENSE?"Save Expense":"Save Entry"); }
    @FXML private void focusForm(){ clearForm(); showEntryDialog(); if(mode==Mode.EXPENSE)expenseCategory.requestFocus(); else bankAccount.requestFocus(); }
    private void showEntryDialog(){if(entryFormPanel==null)return;if(entryDialog==null){entryDialog=new OwnedDialog<>();entryDialog.setResizable(true);entryDialog.setTitle(mode==Mode.EXPENSE?"Expense Entry":"Bank Entry");entryDialog.getDialogPane().setContent(entryFormPanel);entryDialog.getDialogPane().setPrefWidth(820);entryDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);entryDialog.setOnHidden(e->{if(entryFormPanel!=null)entryFormPanel.setVisible(true);});}entryDialog.setTitle(editingId==null?(mode==Mode.EXPENSE?"New Expense Entry":"New Bank Entry"):(mode==Mode.EXPENSE?"Edit Expense Entry":"Edit Bank Entry"));if(!entryDialog.isShowing())entryDialog.show();}
    private void closeEntryDialog(){if(entryDialog!=null&&entryDialog.isShowing())entryDialog.close();}
    @FXML private void chooseBill(){ FileChooser f=new FileChooser(); f.setTitle("Choose expense bill"); f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bill files","*.pdf","*.png","*.jpg","*.jpeg")); selectedBill=f.showOpenDialog(table.getScene().getWindow()); if(selectedBill!=null)billName.setText(selectedBill.getName()); }

    @FXML private void applyFilters(){ currentPage=0; reloadRows(); }
    @FXML private void resetFilters(){
        if(searchField!=null)searchField.clear();
        if(typeFilter!=null&&!typeFilter.getItems().isEmpty())typeFilter.getSelectionModel().selectFirst();
        LocalDate today=BusinessClock.today();
        org.example.util.RegisterUiSupport.setCurrentYearRange(filterFrom,filterTo,today);
        currentPage=0;reloadRows();
    }
    @FXML private void refreshWithFeedback(){
        explicitRefreshPending=true;
        if(btnRefreshEntries!=null){btnRefreshEntries.setDisable(true);btnRefreshEntries.setText("Refreshing...");}
        reloadRows();loadMetrics();
    }
    private void finishExplicitRefresh(boolean success){
        if(!explicitRefreshPending)return;explicitRefreshPending=false;
        if(btnRefreshEntries!=null){btnRefreshEntries.setDisable(false);btnRefreshEntries.setText("Refresh");}
        if(success)org.example.util.ToastManager.info(table,"Refreshed",mode==Mode.EXPENSE?"Expense Entry is up to date.":"Bank Entry is up to date.");
    }
    private void reloadRows(){
        org.example.util.OperationalUiSupport.showLoading(table, mode==Mode.EXPENSE?"Loading expenses…":"Loading bank entries…");
        String q=searchField==null?"":searchField.getText().trim();String filter=typeFilter==null?"":safe(typeFilter.getValue(),"");Mode requestedMode=mode;int requestedPage=currentPage;LocalDate from=filterFrom==null?null:filterFrom.getValue();LocalDate to=filterTo==null?null:filterTo.getValue();
        UiTaskExecutor.submitLatest("finance-register-page",()->financeService.page(requestedPage,PAGE_SIZE,requestedMode==null?"":requestedMode.name(),"",filter,q,from,to),this::applyFinancePage,failure->{
            finishExplicitRefresh(false);
            org.example.util.OperationalUiSupport.showError(table,"Finance register could not load",new IllegalStateException(userMessage(failure)));
            System.err.println("Finance register load failed: " + userMessage(failure));
        });
    }
    private void applyFinancePage(OperationsApiClient.FinancePage page){
        filtered.clear();if(page!=null&&page.rows()!=null)for(var e:page.rows())filtered.add(toRow(e));currentPage=page==null?0:page.page();totalPages=page==null?0:page.totalPages();totalRows=page==null?0:page.totalRows();renderPage();if(filtered.isEmpty())org.example.util.OperationalUiSupport.showEmpty(table,mode==Mode.EXPENSE?"No expenses found":"No bank entries found","Adjust the filters or add a new entry.");finishExplicitRefresh(true);
    }
    private EntryRow toRow(OperationsApiClient.FinanceEntry e){String raw=safe(e.voucherType(),"");String type=raw.toUpperCase(Locale.ROOT).contains("DEPOSIT")?"Deposit":raw.toUpperCase(Locale.ROOT).contains("WITHDRAW")?"Withdrawal":safe(e.category(),"Other");return new EntryRow(e.id()==null?0:e.id(),e.voucherDate(),type,safe(e.notes(),""),safe(e.accountName(),""),safe(e.paymentMode(),""),safe(e.referenceNo(),""),e.amount(),raw,e.statementTransactionId(),safe(e.linkedTargetType(),""),e.linkedTargetId(),safe(e.linkedDocumentNo(),""),e.rowVersion());}
    private void renderPage(){table.getItems().setAll(filtered);long from=totalRows==0?0:(long)currentPage*PAGE_SIZE+1,to=totalRows==0?0:Math.min(totalRows,from+filtered.size()-1);showingLabel.setText(totalRows==0?"Showing 0 to 0 of 0 entries":"Showing "+from+" to "+to+" of "+totalRows+" entries");pageLabel.setText(totalPages<=0?"0 / 0":(currentPage+1)+" / "+totalPages);}
    @FXML private void previousPage(){if(currentPage>0){currentPage--;reloadRows();}} @FXML private void nextPage(){if(currentPage+1<totalPages){currentPage++;reloadRows();}}

    private void installDetailDrawer(){
        detailDrawer=new RegisterDetailDrawer();
        detailDrawer.setCloseAction(this::closeEntryDetails);
        if(workspaceRow!=null){workspaceRow.getChildren().add(detailDrawer);if(table!=null&&table.getParent()!=null)HBox.setHgrow(table.getParent(),javafx.scene.layout.Priority.ALWAYS);org.example.util.OperationalUiSupport.installEscapeClose(workspaceRow,detailDrawer::isOpen,this::closeEntryDetails);}
    }

    private void showEntryDetails(EntryRow row){
        if(row==null||detailDrawer==null)return;detailRow=row;String noun=mode==Mode.EXPENSE?"Expense":"Bank Entry";String status=row.type.get();
        detailDrawer.showRecord(noun+" Details",row.date.get()+" • "+status,List.of(
            RegisterDetailDrawer.field("Date",row.date.get(),"calendar"),
            RegisterDetailDrawer.field(mode==Mode.EXPENSE?"Category":"Type",status,RegisterDetailDrawer.statusSemantic(status)),
            RegisterDetailDrawer.field("Amount",money(row.amount.get()),row.rawType.contains("DEPOSIT")?"complete":row.rawType.contains("WITHDRAW")?"error":"currency"),
            RegisterDetailDrawer.field("Account",row.account.get(),"bank"),
            RegisterDetailDrawer.field("Payment Mode",row.paymentMode.get(),"payment"),
            RegisterDetailDrawer.field("Reference No.",row.reference.get(),"reference"),
            RegisterDetailDrawer.field("Description",row.description.get(),"notes"),
            RegisterDetailDrawer.field("Match / Link",row.match.get(),"link")
        ));
        Button edit=new Button("Edit "+noun);edit.getStyleClass().addAll("approved-button","approved-primary-button");edit.setGraphic(IconFactory.compactIcon("edit",14));edit.setOnAction(e->editRow(row));
        List<Button> actions=new ArrayList<>();actions.add(edit);
        if(row.statementTransactionId!=null){Button statement=new Button("Open Bank Statement");statement.getStyleClass().addAll("approved-button","approved-secondary-button");statement.setGraphic(IconFactory.compactIcon("bank",14));statement.setOnAction(e->openBankStatement(row));actions.add(statement);}
        if(hasLinkedErp(row)){Button linked=new Button("Open Linked ERP");linked.getStyleClass().addAll("approved-button","approved-secondary-button");linked.setGraphic(IconFactory.compactIcon("link",14));linked.setOnAction(e->openLinkedErp(row));actions.add(linked);}
        detailDrawer.setActions(actions.toArray(Button[]::new));
    }

    private void closeEntryDetails(){detailRow=null;if(detailDrawer!=null)detailDrawer.hideDrawer();if(table!=null)table.getSelectionModel().clearSelection();}

    private void editRow(EntryRow row){ if(row==null)return; editingId=row.id; editingVersion=row.rowVersion; entryDate.setValue(parseEntryDate(row.date.get())); description.setText(row.description.get()); referenceNo.setText(row.reference.get()); amount.setText(String.valueOf(row.amount.get())); paymentMode.setValue(row.paymentMode.get()); if(mode==Mode.BANK){ if(row.rawType.contains("DEPOSIT"))creditRadio.setSelected(true);else debitRadio.setSelected(true); if(!row.account.get().isBlank())bankAccount.setValue(row.account.get()); }else{expenseCategory.setValue(row.type.get()); if(!row.account.get().isBlank())expenseAccount.setValue(row.account.get());} saveButton.setText(mode==Mode.BANK?"Update Entry":"Update Expense"); showEntryDialog(); }
    private void deleteRow(EntryRow row){
        if(row==null)return;
        Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Delete this entry? This action cannot be undone.");
        a.setHeaderText("Confirm deletion");
        a.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{
            UiTaskExecutor.submitAction(
                "finance-delete-" + row.id,
                () -> { financeService.delete(row.id,row.rowVersion); return null; },
                ignored -> {
                    loadMetrics();
                    applyFilters();
                    success(mode==Mode.EXPENSE?"Expense Deleted":"Bank Entry Deleted",
                            (mode==Mode.EXPENSE?"Expense":"Bank entry")+" was deleted successfully.");
                },
                failure -> error(failure.getMessage())
            );
        });
    }

    private boolean hasLinkedErp(EntryRow row){return row!=null&&row.linkedTargetId!=null&&!safe(row.linkedTargetType,"").isBlank();}
    private void openBankStatement(EntryRow row){if(row==null||row.statementTransactionId==null){info("Bank Statement","No Bank Statement transaction is linked to this entry.");return;}LinkedRecordContext.open("BANK_STATEMENT",row.statementTransactionId.intValue(),row.reference.get(),"VIEW",mode==Mode.EXPENSE?"Expense Entry":"Bank Entry");DashboardController.navigateFromChild("Bank Statement","/fxml/pages/BankStatement.fxml",null);}
    private void openLinkedErp(EntryRow row){ if(row==null)return; String type=safe(row.linkedTargetType,"").toUpperCase(Locale.ROOT); if("SALE".equals(type)){LinkedRecordContext.open("SALE",row.linkedTargetId,row.linkedDocumentNo,"VIEW","Bank / Expense");org.example.navigation.NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml");return;} if("PURCHASE".equals(type)){LinkedRecordContext.open("PURCHASE",row.linkedTargetId,row.linkedDocumentNo,"VIEW","Bank / Expense");org.example.navigation.NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml");return;} if("PURCHASE_RECON".equals(type)&&row.linkedTargetId!=null){PurchaseReconScreenContext.select(row.linkedTargetId);DashboardController.navigateFromChild("Purchase Recon","/fxml/pages/PurchaseRecon.fxml",null);return;} info("Linked ERP Record","No linked ERP document is available for this entry."); }





    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("finance-");}

    private static String mask(String s){return s.length()<=4?s:"••••"+s.substring(s.length()-4);} private static String safe(String s,String d){return s==null||s.isBlank()?d:s;} private static String money(double v){return String.format(Locale.ENGLISH,"₹ %,.2f",v);} private static String text(ComboBox<String> c){String e=c.isEditable()?c.getEditor().getText():c.getValue();return e==null?"":e.trim();}
    private String chipStyle(String s){String x=s.toLowerCase(Locale.ROOT); if(x.contains("deposit"))return"finance-chip-green";if(x.contains("withdraw"))return"finance-chip-red";if(x.contains("office"))return"finance-chip-purple";if(x.contains("travel")||x.contains("transport"))return"finance-chip-blue";if(x.contains("marketing")||x.contains("maintenance"))return"finance-chip-orange";return"finance-chip-teal";}
    private static String currentUser(){var u=org.example.service.SessionService.current();return u==null?"User":safe(u.getFullName(),"User");}
    private void info(String header,String text){OwnedAlert a=new OwnedAlert(Alert.AlertType.INFORMATION,text);a.setHeaderText(header);a.showAndWait();}
    private void success(String header,String text){org.example.util.ToastManager.success(table,header,text);}
    private void error(String text){new OwnedAlert(Alert.AlertType.ERROR,text==null||text.isBlank()?"The operation could not be completed. Please try again.":text).showAndWait();}
    private static String userMessage(Throwable failure){Throwable root=failure;while(root!=null&&root.getCause()!=null&&root.getCause()!=root)root=root.getCause();String message=root==null?null:root.getMessage();if(message==null||message.isBlank()||"empty String".equalsIgnoreCase(message.trim()))return "The ERP could not complete this request. Please review the entered values and try again.";return message;}
    private static LocalDate parseEntryDate(String value){if(value==null||value.isBlank())return BusinessClock.today();String text=value.trim();for(var pattern:List.of(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))){try{return LocalDate.parse(text.length()>=10?text.substring(0,10):text,pattern);}catch(Exception ignored){}}return BusinessClock.today();}

    public static final class EntryRow { final int id; final long rowVersion; final SimpleStringProperty date,type,description,account,paymentMode,reference,match; final SimpleDoubleProperty amount; final String rawType,linkedTargetType,linkedDocumentNo; final Long statementTransactionId; final Integer linkedTargetId; EntryRow(int id,String d,String t,String desc,String acc,String pm,String ref,double amt,String raw,Long statementId,String targetType,Integer targetId,String documentNo,long rowVersion){this.id=id;this.rowVersion=rowVersion;date=new SimpleStringProperty(d);type=new SimpleStringProperty(t);description=new SimpleStringProperty(desc);account=new SimpleStringProperty(acc);paymentMode=new SimpleStringProperty(pm);reference=new SimpleStringProperty(ref);amount=new SimpleDoubleProperty(amt);rawType=raw==null?"":raw.toUpperCase(Locale.ROOT);statementTransactionId=statementId;linkedTargetType=targetType==null?"":targetType;linkedTargetId=targetId;linkedDocumentNo=documentNo==null?"":documentNo;String display="";if(statementId!=null)display="Bank Statement";if(!linkedDocumentNo.isBlank())display=display.isBlank()?linkedDocumentNo:display+" • "+linkedDocumentNo;match=new SimpleStringProperty(display);} }
}
