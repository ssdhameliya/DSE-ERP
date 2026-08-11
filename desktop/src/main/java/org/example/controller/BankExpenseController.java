package org.example.controller;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
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

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class BankExpenseController implements ScreenLifecycle {
    public enum Mode { BANK, EXPENSE }
    private static volatile Mode requestedMode;
    private static volatile ExpensePrefill requestedExpensePrefill;
    public record ExpensePrefill(long statementTransactionId,String date,double amount,String reference,String description,String accountName,String paymentMode){}
    public static void requestExpensePrefill(ExpensePrefill prefill){ requestedExpensePrefill=prefill; requestedMode=Mode.EXPENSE; }
    private static ExpensePrefill consumeExpensePrefill(){ExpensePrefill p=requestedExpensePrefill;requestedExpensePrefill=null;return p;}
    public static void requestMode(Mode mode) { requestedMode = mode == null ? Mode.BANK : mode; }

    private static Mode consumeRequestedMode() {
        Mode requested = requestedMode;
        requestedMode = null;
        return requested;
    }

    @FXML private Label lblTitle, lblSubtitle, formTitle, listTitle;
    @FXML private Button btnBankMode, btnExpenseMode, btnBankRecon, saveButton, addButton;
    @FXML private Label kpi1Label,kpi1Value,kpi1Note,kpi2Label,kpi2Value,kpi2Note,kpi3Label,kpi3Value,kpi3Note,kpi4Label,kpi4Value,kpi4Note;
    @FXML private DatePicker entryDate;
    @FXML private VBox bankOnlyFields, expenseOnlyFields, billBox;
    @FXML private ComboBox<String> bankAccount, expenseCategory, expenseAccount, paymentMode, typeFilter, periodFilter;
    @FXML private RadioButton creditRadio, debitRadio;
    @FXML private TextField referenceNo, amount, searchField;
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
    private Long reconciliationStatementId;
    private final BankStatementApiClient bankStatementApi = new BankStatementApiClient();
    private int currentPage = 0;
    private static final int PAGE_SIZE = 8;

    @FXML public void initialize() {
        entryDate.setValue(LocalDate.now());
        creditRadio.setToggleGroup(typeGroup); debitRadio.setToggleGroup(typeGroup); creditRadio.setSelected(true);
        loadMasterLookups();
        loadAccounts();
        configureTable();
        if(btnBankMode!=null)btnBankMode.setGraphic(IconFactory.compactIcon("bank",15));
        if(btnExpenseMode!=null)btnExpenseMode.setGraphic(IconFactory.compactIcon("payment",15));
        if(btnBankRecon!=null)btnBankRecon.setGraphic(IconFactory.compactIcon("link",15));
        periodFilter.setItems(FXCollections.observableArrayList("3 Months","6 Months","This Month","This Year","All Time")); periodFilter.setValue("3 Months");
        Mode initialMode = consumeRequestedMode();
        mode = initialMode == null ? Mode.BANK : initialMode;
        applyMode(mode);
        applyRequestedExpensePrefill();
    }

    private void applyRequestedExpensePrefill(){
        ExpensePrefill p=consumeExpensePrefill(); if(p==null)return;
        mode=Mode.EXPENSE; applyMode(Mode.EXPENSE); reconciliationStatementId=p.statementTransactionId();
        try{entryDate.setValue(LocalDate.parse(p.date()));}catch(Exception ignored){}
        amount.setText(String.format(Locale.ROOT,"%.2f",p.amount())); referenceNo.setText(safe(p.reference(),"")); description.setText(safe(p.description(),""));
        if(p.accountName()!=null&&!p.accountName().isBlank()){ if(!expenseAccount.getItems().contains(p.accountName()))expenseAccount.getItems().add(0,p.accountName()); expenseAccount.setValue(p.accountName()); }
        if(p.paymentMode()!=null&&!p.paymentMode().isBlank()){ if(!paymentMode.getItems().contains(p.paymentMode()))paymentMode.getItems().add(0,p.paymentMode()); paymentMode.setValue(p.paymentMode()); }
        saveButton.setText("Create Expense from Statement");
    }

    private void loadMasterLookups() {
        String selectedPaymentMode = paymentMode == null ? null : paymentMode.getValue();
        List<String> paymentModes = lookupService.getValuesByCategoryCode("PAYMENT_MODE");
        List<String> expenseCategories = lookupService.getValuesByCategoryCode("EXPENSE_CATEGORY");

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
    }

    private void loadAccounts() {
        List<String> accounts = new ArrayList<>();
        try {
            for (org.example.model.Lookup l : lookupService.getByType("BANK ACCOUNT")) {
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
        mode = next; currentPage = 0; clearForm();
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
        colMatch.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(String text, boolean empty){ super.updateItem(text,empty); setText(null); setGraphic(null); if(empty||text==null||text.isBlank()||getIndex()<0||getIndex()>=getTableView().getItems().size())return; EntryRow row=getTableView().getItems().get(getIndex()); Hyperlink link=new Hyperlink(text); link.getStyleClass().add("bank-match-link"); link.setGraphic(IconFactory.compactIcon("link",13)); link.setOnAction(e->openLinked(row)); setGraphic(link);} });
        colType.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(String s, boolean empty){ super.updateItem(s,empty); setText(empty?null:s); getStyleClass().removeAll("finance-chip-green","finance-chip-red","finance-chip-purple","finance-chip-blue","finance-chip-orange","finance-chip-teal"); if(!empty&&s!=null)getStyleClass().add(chipStyle(s)); }});
        colAction.setCellFactory(c->new TableCell<>() { private final Button edit=new Button("Edit"), del=new Button("Delete"); private final javafx.scene.layout.HBox box=new javafx.scene.layout.HBox(5,edit,del); { edit.getStyleClass().addAll("approved-button","approved-secondary-button","finance-row-action"); del.getStyleClass().addAll("approved-button","approved-danger-button","finance-row-action"); edit.setOnAction(e->editRow(getTableView().getItems().get(getIndex()))); del.setOnAction(e->deleteRow(getTableView().getItems().get(getIndex()))); } @Override protected void updateItem(Void v, boolean empty){super.updateItem(v,empty);setGraphic(empty?null:box);} });
        table.setPlaceholder(new Label("No entries found")); configureHeaderIcons();
    }

    private void configureHeaderIcons(){ IconFactory.applyTableHeaderIcon(colDate,"calendar"); IconFactory.applyTableHeaderIcon(colType, mode==Mode.EXPENSE?"category":"status"); IconFactory.applyTableHeaderIcon(colDescription,"document"); IconFactory.applyTableHeaderIcon(colAccount,"bank"); IconFactory.applyTableHeaderIcon(colMode,"payment"); IconFactory.applyTableHeaderIcon(colReference,"reference"); IconFactory.applyTableHeaderIcon(colAmount,"currency"); IconFactory.applyTableHeaderIcon(colMatch,"link"); IconFactory.applyTableHeaderIcon(colAction,"actions"); }

    private void loadMetrics() {
        try {
            OperationsApiClient.FinanceMetrics m = financeService.metrics();
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
        } catch (Exception e) { error("Unable to load finance metrics: "+e.getMessage()); }
    }
    private void setKpi(Label l,Label v,Label n,String a,String b,String c){l.setText(a);v.setText(b);n.setText(c);}

    @FXML private void saveEntry() {
        try {
            double value = Double.parseDouble(amount.getText().trim());
            if (value <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
            if (entryDate.getValue() == null) throw new IllegalArgumentException("Select a date.");
            String rawType= mode==Mode.BANK ? (creditRadio.isSelected()?"BANK DEPOSIT":"BANK WITHDRAWAL") : "EXPENSE";
            String category= mode==Mode.EXPENSE ? text(expenseCategory) : (creditRadio.isSelected()?"Deposit":"Withdrawal");
            String account= mode==Mode.BANK ? bankAccount.getValue() : expenseAccount.getValue();
            if (mode==Mode.EXPENSE && reconciliationStatementId!=null && editingId==null) {
                bankStatementApi.expense(reconciliationStatementId,new BankStatementApiClient.ExpenseRequest(category,account,paymentMode.getValue(),description.getText().trim(),selectedBill==null?null:selectedBill.getAbsolutePath(),currentUser()));
                reconciliationStatementId=null;
            } else {
                OperationsApiClient.FinanceEntry dto = new OperationsApiClient.FinanceEntry(editingId, null, rawType, entryDate.getValue().toString(), category, referenceNo.getText().trim(), value, paymentMode.getValue(), description.getText().trim(), account, selectedBill==null?null:selectedBill.getAbsolutePath(), false);
                if (editingId == null) financeService.save(dto); else financeService.update(dto);
            }
            NotificationService.add((editingId==null?"Finance entry created":"Finance entry updated")+": "+rawType+" "+money(value));
            clearForm(); loadMetrics(); applyFilters();
        } catch (Exception e) { error(e.getMessage()); }
    }

    private void validate(){ if(entryDate.getValue()==null)throw new IllegalArgumentException("Select a date."); if(description.getText().trim().isEmpty())throw new IllegalArgumentException("Enter a description."); if(amount.getText().trim().isEmpty())throw new IllegalArgumentException("Enter an amount."); double v; try{v=Double.parseDouble(amount.getText().replace(",","").trim());}catch(Exception e){throw new IllegalArgumentException("Enter a valid amount.");} if(v<=0)throw new IllegalArgumentException("Amount must be greater than zero."); if(paymentMode.getItems().isEmpty())throw new IllegalArgumentException("No Payment Mode is configured in Master Data."); if(paymentMode.getValue()==null)throw new IllegalArgumentException("Select payment mode."); if(mode==Mode.BANK&&bankAccount.getValue()==null)throw new IllegalArgumentException("Select bank account."); if(mode==Mode.EXPENSE&&expenseCategory.getItems().isEmpty())throw new IllegalArgumentException("No Expense Category is configured in Master Data."); if(mode==Mode.EXPENSE&&(expenseCategory.getValue()==null||expenseCategory.getValue().isBlank()||expenseAccount.getValue()==null))throw new IllegalArgumentException("Select expense category and account."); }

    @FXML private void clearForm(){ reconciliationStatementId=null; if(entryDate!=null)entryDate.setValue(LocalDate.now()); if(referenceNo!=null)referenceNo.clear(); if(description!=null)description.clear(); if(amount!=null)amount.clear(); if(creditRadio!=null)creditRadio.setSelected(true); if(expenseCategory!=null)expenseCategory.getSelectionModel().clearSelection(); editingId=null; selectedBill=null; if(billName!=null)billName.setText("No file selected"); if(saveButton!=null)saveButton.setText(mode==Mode.EXPENSE?"Save Expense":"Save Entry"); }
    @FXML private void focusForm(){ if(mode==Mode.EXPENSE)expenseCategory.requestFocus(); else bankAccount.requestFocus(); }
    @FXML private void chooseBill(){ FileChooser f=new FileChooser(); f.setTitle("Choose expense bill"); f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bill files","*.pdf","*.png","*.jpg","*.jpeg")); selectedBill=f.showOpenDialog(table.getScene().getWindow()); if(selectedBill!=null)billName.setText(selectedBill.getName()); }

    @FXML private void applyFilters(){ currentPage=0; reloadRows(); }
    private void reloadRows(){
        filtered.clear();
        String q=searchField==null?"":searchField.getText().trim().toLowerCase(Locale.ROOT); String filter=typeFilter==null?null:typeFilter.getValue(); String period=periodFilter==null?null:periodFilter.getValue();
        try {
            for (OperationsApiClient.FinanceEntry e : financeService.getAll()) {
                String raw=safe(e.voucherType(),""); boolean include=mode==Mode.BANK?(raw.equalsIgnoreCase("BANK DEPOSIT")||raw.equalsIgnoreCase("BANK WITHDRAWAL")):raw.equalsIgnoreCase("EXPENSE"); if(!include)continue;
                String type=mode==Mode.BANK?(raw.toUpperCase(Locale.ROOT).contains("DEPOSIT")?"Deposit":"Withdrawal"):safe(e.category(),"Other");
                EntryRow row=new EntryRow(e.id()==null?0:e.id(),e.voucherDate(),type,safe(e.notes(),""),safe(e.accountName(),""),safe(e.paymentMode(),""),safe(e.referenceNo(),""),e.amount(),raw,null,"",null,"");
                if(!matchesPeriod(row.date.get(),period))continue; if(filter!=null&&!filter.startsWith("All")&&!filter.equalsIgnoreCase(type))continue; String hay=(type+" "+row.description.get()+" "+row.account.get()+" "+row.reference.get()).toLowerCase(Locale.ROOT); if(!q.isEmpty()&&!hay.contains(q))continue; filtered.add(row);
            }
        } catch(Exception e){error("Unable to load entries: "+e.getMessage());}
        renderPage();
    }
    private boolean matchesPeriod(String date,String period){ if(period==null||"All Time".equals(period))return true; if(date==null)return false; try{LocalDate d=LocalDate.parse(date);LocalDate now=LocalDate.now();return switch(period){case "3 Months"->!d.isBefore(now.minusMonths(3));case "6 Months"->!d.isBefore(now.minusMonths(6));case "This Year"->d.getYear()==now.getYear();default->d.getYear()==now.getYear()&&d.getMonth()==now.getMonth();};}catch(Exception e){return false;} }
    private void renderPage(){ int pages=Math.max(1,(filtered.size()+PAGE_SIZE-1)/PAGE_SIZE); if(currentPage>=pages)currentPage=pages-1; int from=Math.min(currentPage*PAGE_SIZE,filtered.size()),to=Math.min(from+PAGE_SIZE,filtered.size()); table.getItems().setAll(filtered.subList(from,to)); showingLabel.setText(filtered.isEmpty()?"Showing 0 to 0 of 0 entries":"Showing "+(from+1)+" to "+to+" of "+filtered.size()+" entries"); pageLabel.setText((currentPage+1)+" / "+pages); }
    @FXML private void previousPage(){if(currentPage>0){currentPage--;renderPage();}} @FXML private void nextPage(){int pages=Math.max(1,(filtered.size()+PAGE_SIZE-1)/PAGE_SIZE);if(currentPage+1<pages){currentPage++;renderPage();}}

    private void editRow(EntryRow row){ if(row==null)return; editingId=row.id; entryDate.setValue(LocalDate.parse(row.date.get())); description.setText(row.description.get()); referenceNo.setText(row.reference.get()); amount.setText(String.valueOf(row.amount.get())); paymentMode.setValue(row.paymentMode.get()); if(mode==Mode.BANK){ if(row.rawType.contains("DEPOSIT"))creditRadio.setSelected(true);else debitRadio.setSelected(true); if(!row.account.get().isBlank())bankAccount.setValue(row.account.get()); }else{expenseCategory.setValue(row.type.get()); if(!row.account.get().isBlank())expenseAccount.setValue(row.account.get());} saveButton.setText(mode==Mode.BANK?"Update Entry":"Update Expense"); focusForm(); }
    private void deleteRow(EntryRow row){
        if(row==null)return;
        Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Delete this entry? This action cannot be undone.");
        a.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{try{financeService.delete(row.id);loadMetrics();applyFilters();}catch(Exception e){error(e.getMessage());}});
    }

    private void openLinked(EntryRow row){ if(row==null)return; String type=safe(row.linkedTargetType,"").toUpperCase(Locale.ROOT); if("SALE".equals(type)&&!safe(row.linkedDocumentNo,"").isBlank()){SalesScreenContext.select(row.linkedDocumentNo);org.example.navigation.NavigationManager.getInstance().loadPage("/fxml/pages/RecordPayment.fxml");return;} if("PURCHASE".equals(type)&&!safe(row.linkedDocumentNo,"").isBlank()){PurchaseScreenContext.select(row.linkedDocumentNo);org.example.navigation.NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml");return;} if(row.statementTransactionId!=null){DashboardController.navigateFromChild("Bank Statement","/fxml/pages/BankStatement.fxml",null);return;} info("Match / Link","No reconciliation link is available for this entry."); }





    private static String mask(String s){return s.length()<=4?s:"••••"+s.substring(s.length()-4);} private static String safe(String s,String d){return s==null||s.isBlank()?d:s;} private static String money(double v){return String.format(Locale.ENGLISH,"₹ %,.2f",v);} private static String text(ComboBox<String> c){String e=c.isEditable()?c.getEditor().getText():c.getValue();return e==null?"":e.trim();}
    private String chipStyle(String s){String x=s.toLowerCase(Locale.ROOT); if(x.contains("deposit"))return"finance-chip-green";if(x.contains("withdraw"))return"finance-chip-red";if(x.contains("office"))return"finance-chip-purple";if(x.contains("travel")||x.contains("transport"))return"finance-chip-blue";if(x.contains("marketing")||x.contains("maintenance"))return"finance-chip-orange";return"finance-chip-teal";}
    private static String currentUser(){var u=org.example.service.SessionService.current();return u==null?"User":safe(u.getFullName(),"User");}
    private void info(String header,String text){OwnedAlert a=new OwnedAlert(Alert.AlertType.INFORMATION,text);a.setHeaderText(header);a.showAndWait();}
    private void error(String text){new OwnedAlert(Alert.AlertType.ERROR,text==null?"Operation failed":text).showAndWait();}

    public static final class EntryRow { final int id; final SimpleStringProperty date,type,description,account,paymentMode,reference,match; final SimpleDoubleProperty amount; final String rawType,linkedTargetType,linkedDocumentNo; final Long statementTransactionId; final Integer linkedTargetId; EntryRow(int id,String d,String t,String desc,String acc,String pm,String ref,double amt,String raw,Long statementId,String targetType,Integer targetId,String documentNo){this.id=id;date=new SimpleStringProperty(d);type=new SimpleStringProperty(t);description=new SimpleStringProperty(desc);account=new SimpleStringProperty(acc);paymentMode=new SimpleStringProperty(pm);reference=new SimpleStringProperty(ref);amount=new SimpleDoubleProperty(amt);rawType=raw==null?"":raw.toUpperCase(Locale.ROOT);statementTransactionId=statementId;linkedTargetType=targetType==null?"":targetType;linkedTargetId=targetId;linkedDocumentNo=documentNo==null?"":documentNo;String display="";if(!linkedDocumentNo.isBlank())display=linkedDocumentNo;else if(statementId!=null)display="Bank Statement";match=new SimpleStringProperty(display);} }
}
