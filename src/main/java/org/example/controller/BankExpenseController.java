package org.example.controller;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;
import org.example.dao.LookupDAO;
import org.example.navigation.ScreenLifecycle;
import org.example.database.DatabaseManager;
import org.example.service.NotificationService;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class BankExpenseController implements ScreenLifecycle {
    public enum Mode { BANK, EXPENSE }
    private static volatile Mode requestedMode;
    public static void requestMode(Mode mode) { requestedMode = mode == null ? Mode.BANK : mode; }

    private static Mode consumeRequestedMode() {
        Mode requested = requestedMode;
        requestedMode = null;
        return requested;
    }

    @FXML private Label lblTitle, lblSubtitle, formTitle, listTitle;
    @FXML private Button btnBankMode, btnExpenseMode, saveButton, addButton;
    @FXML private Label kpi1Label,kpi1Value,kpi1Note,kpi2Label,kpi2Value,kpi2Note,kpi3Label,kpi3Value,kpi3Note,kpi4Label,kpi4Value,kpi4Note;
    @FXML private DatePicker entryDate;
    @FXML private VBox bankOnlyFields, expenseOnlyFields, billBox;
    @FXML private ComboBox<String> bankAccount, expenseCategory, expenseAccount, paymentMode, typeFilter, periodFilter;
    @FXML private RadioButton creditRadio, debitRadio;
    @FXML private TextField referenceNo, amount, searchField;
    @FXML private TextArea description;
    @FXML private Label billName, showingLabel, pageLabel;
    @FXML private TableView<EntryRow> table;
    @FXML private TableColumn<EntryRow,String> colDate,colType,colDescription,colAccount,colMode,colReference;
    @FXML private TableColumn<EntryRow,Number> colAmount;
    @FXML private TableColumn<EntryRow,Void> colAction;

    private final ToggleGroup typeGroup = new ToggleGroup();
    private final LookupDAO lookupDAO = new LookupDAO();
    private final List<EntryRow> filtered = new ArrayList<>();
    private Mode mode;
    private File selectedBill;
    private Integer editingId;
    private int currentPage = 0;
    private static final int PAGE_SIZE = 8;

    @FXML public void initialize() {
        ensureFinanceColumns();
        entryDate.setValue(LocalDate.now());
        creditRadio.setToggleGroup(typeGroup); debitRadio.setToggleGroup(typeGroup); creditRadio.setSelected(true);
        loadMasterLookups();
        loadAccounts();
        configureTable();
        periodFilter.setItems(FXCollections.observableArrayList("This Month","This Year","All Time")); periodFilter.setValue("This Month");
        Mode initialMode = consumeRequestedMode();
        mode = initialMode == null ? Mode.BANK : initialMode;
        applyMode(mode);
    }

    private void loadMasterLookups() {
        String selectedPaymentMode = paymentMode == null ? null : paymentMode.getValue();
        List<String> paymentModes = lookupDAO.getValuesByCategoryCode("PAYMENT_MODE");
        List<String> expenseCategories = lookupDAO.getValuesByCategoryCode("EXPENSE_CATEGORY");

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
        if (reusedFromCache) loadMasterLookups();
        Mode requested = consumeRequestedMode();
        if (requested != null && requested != mode) {
            applyMode(requested);
        } else if (reusedFromCache) {
            loadMetrics();
            applyFilters();
        }
    }

    private void loadAccounts() {
        String bank = ConfigManager.get("payment.bankName", "").trim();
        String number = ConfigManager.get("payment.accountNumber", "").trim();
        List<String> accounts = new ArrayList<>();
        if (!bank.isBlank()) accounts.add(number.isBlank() ? bank : bank + " - " + mask(number));
        accounts.add("Cash / General");
        bankAccount.getItems().setAll(accounts); expenseAccount.getItems().setAll(accounts);
        if (!accounts.isEmpty()) { bankAccount.setValue(accounts.get(0)); expenseAccount.setValue(accounts.get(0)); }
    }

    @FXML private void showBankMode(){ applyMode(Mode.BANK); }
    @FXML private void showExpenseMode(){ applyMode(Mode.EXPENSE); }

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
        colAccount.setCellValueFactory(v->v.getValue().account); colMode.setCellValueFactory(v->v.getValue().paymentMode); colReference.setCellValueFactory(v->v.getValue().reference); colAmount.setCellValueFactory(v->v.getValue().amount);
        colAmount.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(Number n, boolean empty){ super.updateItem(n,empty); if(empty||n==null){setText(null);setStyle("");return;} EntryRow row=getTableRow()==null?null:getTableRow().getItem(); setText(money(n.doubleValue())); boolean positive=row!=null && "DEPOSIT".equals(row.rawType); setStyle("-fx-text-fill:" + (positive ? "#22c55e" : "#ef4444") + ";-fx-font-weight:800;"); }});
        colType.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(String s, boolean empty){ super.updateItem(s,empty); setText(empty?null:s); getStyleClass().removeAll("finance-chip-green","finance-chip-red","finance-chip-purple","finance-chip-blue","finance-chip-orange","finance-chip-teal"); if(!empty&&s!=null)getStyleClass().add(chipStyle(s)); }});
        colAction.setCellFactory(c->new TableCell<>() { private final Button edit=new Button("Edit"), del=new Button("Delete"); private final javafx.scene.layout.HBox box=new javafx.scene.layout.HBox(5,edit,del); { edit.getStyleClass().addAll("approved-button","approved-secondary-button","finance-row-action"); del.getStyleClass().addAll("approved-button","approved-danger-button","finance-row-action"); edit.setOnAction(e->editRow(getTableView().getItems().get(getIndex()))); del.setOnAction(e->deleteRow(getTableView().getItems().get(getIndex()))); } @Override protected void updateItem(Void v, boolean empty){super.updateItem(v,empty);setGraphic(empty?null:box);} });
        table.setPlaceholder(new Label("No entries found")); configureHeaderIcons();
    }

    private void configureHeaderIcons(){ IconFactory.applyTableHeaderIcon(colDate,"calendar"); IconFactory.applyTableHeaderIcon(colType, mode==Mode.EXPENSE?"category":"status"); IconFactory.applyTableHeaderIcon(colDescription,"document"); IconFactory.applyTableHeaderIcon(colAccount,"bank"); IconFactory.applyTableHeaderIcon(colMode,"payment"); IconFactory.applyTableHeaderIcon(colReference,"reference"); IconFactory.applyTableHeaderIcon(colAmount,"currency"); IconFactory.applyTableHeaderIcon(colAction,"actions"); }

    private void loadMetrics() {
        if (mode == Mode.BANK) {
            double credits=scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='BANK DEPOSIT'");
            double debits=scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='BANK WITHDRAWAL'");
            long count=(long)scalar("SELECT COUNT(*) FROM finance_register WHERE UPPER(voucher_type) IN ('BANK DEPOSIT','BANK WITHDRAWAL') AND substr(voucher_date,1,7)=strftime('%Y-%m','now')");
            setKpi(kpi1Label,kpi1Value,kpi1Note,"Bank Balance",money(credits-debits),"In all accounts"); setKpi(kpi2Label,kpi2Value,kpi2Note,"Total Bank Entries",String.valueOf(count),"This month"); setKpi(kpi3Label,kpi3Value,kpi3Note,"Total Credits",money(monthAmount("BANK DEPOSIT")),"This month"); setKpi(kpi4Label,kpi4Value,kpi4Note,"Total Debits",money(monthAmount("BANK WITHDRAWAL")),"This month");
        } else {
            double month=scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE' AND substr(voucher_date,1,7)=strftime('%Y-%m','now')");
            double year=scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE' AND substr(voucher_date,1,4)=strftime('%Y','now')");
            long count=(long)scalar("SELECT COUNT(*) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE' AND substr(voucher_date,1,7)=strftime('%Y-%m','now')");
            String top="No expenses"; double topAmount=0; try(Connection c=DatabaseManager.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COALESCE(NULLIF(category,''),'Other'),SUM(amount) total FROM finance_register WHERE UPPER(voucher_type)='EXPENSE' GROUP BY 1 ORDER BY total DESC LIMIT 1")){ if(r.next()){top=r.getString(1);topAmount=r.getDouble(2);} }catch(Exception ignored){}
            long pending=(long)scalar("SELECT COUNT(*) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE' AND COALESCE(reconciled,0)=0"); double pendingAmt=scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='EXPENSE' AND COALESCE(reconciled,0)=0");
            setKpi(kpi1Label,kpi1Value,kpi1Note,"Total Expenses (This Month)",money(month),count+" entries"); setKpi(kpi2Label,kpi2Value,kpi2Note,"Total Expenses (This Year)",money(year),"Year to date"); setKpi(kpi3Label,kpi3Value,kpi3Note,"Top Expense Category",top,money(topAmount)); setKpi(kpi4Label,kpi4Value,kpi4Note,"Pending Reconcile",pending+" entries",money(pendingAmt));
        }
    }

    private double monthAmount(String type){return scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(voucher_type)='"+type+"' AND substr(voucher_date,1,7)=strftime('%Y-%m','now')");}
    private void setKpi(Label l,Label v,Label n,String a,String b,String c){l.setText(a);v.setText(b);n.setText(c);}

    @FXML private void saveEntry() {
        try {
            validate(); double value=Double.parseDouble(amount.getText().replace(",","").trim());
            String rawType= mode==Mode.BANK ? (creditRadio.isSelected()?"BANK DEPOSIT":"BANK WITHDRAWAL") : "EXPENSE";
            String category= mode==Mode.EXPENSE ? text(expenseCategory) : (creditRadio.isSelected()?"Deposit":"Withdrawal");
            String account= mode==Mode.BANK ? bankAccount.getValue() : expenseAccount.getValue();
            if (editingId == null) {
                try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement("INSERT INTO finance_register(voucher_no,voucher_type,voucher_date,category,reference_no,amount,payment_mode,notes,account_name,bill_path,reconciled) VALUES(?,?,?,?,?,?,?,?,?,?,0)")){
                    p.setString(1,nextVoucher()); p.setString(2,rawType); p.setString(3,entryDate.getValue().toString()); p.setString(4,category); p.setString(5,referenceNo.getText().trim()); p.setDouble(6,value); p.setString(7,paymentMode.getValue()); p.setString(8,description.getText().trim()); p.setString(9,account); p.setString(10,selectedBill==null?null:selectedBill.getAbsolutePath()); p.executeUpdate();
                }
            } else {
                try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement("UPDATE finance_register SET voucher_type=?,voucher_date=?,category=?,reference_no=?,amount=?,payment_mode=?,notes=?,account_name=?,bill_path=COALESCE(?,bill_path) WHERE id=?")){
                    p.setString(1,rawType); p.setString(2,entryDate.getValue().toString()); p.setString(3,category); p.setString(4,referenceNo.getText().trim()); p.setDouble(5,value); p.setString(6,paymentMode.getValue()); p.setString(7,description.getText().trim()); p.setString(8,account); p.setString(9,selectedBill==null?null:selectedBill.getAbsolutePath()); p.setInt(10,editingId); p.executeUpdate();
                }
            }
            NotificationService.add((mode==Mode.BANK?"Bank":"Expense")+" entry recorded."); clearForm(); loadMetrics(); applyFilters();
        } catch(Exception e){ error(e.getMessage()==null?"Unable to save entry":e.getMessage()); }
    }

    private void validate(){ if(entryDate.getValue()==null)throw new IllegalArgumentException("Select a date."); if(description.getText().trim().isEmpty())throw new IllegalArgumentException("Enter a description."); if(amount.getText().trim().isEmpty())throw new IllegalArgumentException("Enter an amount."); double v; try{v=Double.parseDouble(amount.getText().replace(",","").trim());}catch(Exception e){throw new IllegalArgumentException("Enter a valid amount.");} if(v<=0)throw new IllegalArgumentException("Amount must be greater than zero."); if(paymentMode.getItems().isEmpty())throw new IllegalArgumentException("No Payment Mode is configured in Master Data."); if(paymentMode.getValue()==null)throw new IllegalArgumentException("Select payment mode."); if(mode==Mode.BANK&&bankAccount.getValue()==null)throw new IllegalArgumentException("Select bank account."); if(mode==Mode.EXPENSE&&expenseCategory.getItems().isEmpty())throw new IllegalArgumentException("No Expense Category is configured in Master Data."); if(mode==Mode.EXPENSE&&(expenseCategory.getValue()==null||expenseCategory.getValue().isBlank()||expenseAccount.getValue()==null))throw new IllegalArgumentException("Select expense category and account."); }

    @FXML private void clearForm(){ if(entryDate!=null)entryDate.setValue(LocalDate.now()); if(referenceNo!=null)referenceNo.clear(); if(description!=null)description.clear(); if(amount!=null)amount.clear(); if(creditRadio!=null)creditRadio.setSelected(true); if(expenseCategory!=null)expenseCategory.getSelectionModel().clearSelection(); editingId=null; selectedBill=null; if(billName!=null)billName.setText("No file selected"); if(saveButton!=null)saveButton.setText(mode==Mode.EXPENSE?"Save Expense":"Save Entry"); }
    @FXML private void focusForm(){ if(mode==Mode.EXPENSE)expenseCategory.requestFocus(); else bankAccount.requestFocus(); }
    @FXML private void chooseBill(){ FileChooser f=new FileChooser(); f.setTitle("Choose expense bill"); f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bill files","*.pdf","*.png","*.jpg","*.jpeg")); selectedBill=f.showOpenDialog(table.getScene().getWindow()); if(selectedBill!=null)billName.setText(selectedBill.getName()); }

    @FXML private void applyFilters(){ currentPage=0; reloadRows(); }
    private void reloadRows(){
        filtered.clear(); String sql= mode==Mode.BANK ? "SELECT * FROM finance_register WHERE UPPER(voucher_type) IN ('BANK DEPOSIT','BANK WITHDRAWAL') ORDER BY voucher_date DESC,id DESC" : "SELECT * FROM finance_register WHERE UPPER(voucher_type)='EXPENSE' ORDER BY voucher_date DESC,id DESC";
        String q=searchField==null?"":searchField.getText().trim().toLowerCase(Locale.ROOT); String filter=typeFilter==null?null:typeFilter.getValue(); String period=periodFilter==null?null:periodFilter.getValue();
        try(Connection c=DatabaseManager.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)){ while(r.next()){ String raw=r.getString("voucher_type"); String type=mode==Mode.BANK?(raw.toUpperCase(Locale.ROOT).contains("DEPOSIT")?"Deposit":"Withdrawal"):safe(r.getString("category"),"Other"); EntryRow row=new EntryRow(r.getInt("id"),r.getString("voucher_date"),type,safe(r.getString("notes"),""),safe(r.getString("account_name"),""),safe(r.getString("payment_mode"),""),safe(r.getString("reference_no"),""),r.getDouble("amount"),raw); if(!matchesPeriod(row.date.get(),period))continue; if(filter!=null&&!filter.startsWith("All")&&!filter.equalsIgnoreCase(type))continue; String hay=(type+" "+row.description.get()+" "+row.account.get()+" "+row.reference.get()).toLowerCase(Locale.ROOT); if(!q.isEmpty()&&!hay.contains(q))continue; filtered.add(row); }}catch(Exception e){error("Unable to load entries: "+e.getMessage());}
        renderPage();
    }
    private boolean matchesPeriod(String date,String period){ if(period==null||"All Time".equals(period))return true; if(date==null)return false; String now=LocalDate.now().toString(); return "This Year".equals(period)?date.startsWith(now.substring(0,4)):date.startsWith(now.substring(0,7)); }
    private void renderPage(){ int pages=Math.max(1,(filtered.size()+PAGE_SIZE-1)/PAGE_SIZE); if(currentPage>=pages)currentPage=pages-1; int from=Math.min(currentPage*PAGE_SIZE,filtered.size()),to=Math.min(from+PAGE_SIZE,filtered.size()); table.getItems().setAll(filtered.subList(from,to)); showingLabel.setText(filtered.isEmpty()?"Showing 0 to 0 of 0 entries":"Showing "+(from+1)+" to "+to+" of "+filtered.size()+" entries"); pageLabel.setText((currentPage+1)+" / "+pages); }
    @FXML private void previousPage(){if(currentPage>0){currentPage--;renderPage();}} @FXML private void nextPage(){int pages=Math.max(1,(filtered.size()+PAGE_SIZE-1)/PAGE_SIZE);if(currentPage+1<pages){currentPage++;renderPage();}}

    private void editRow(EntryRow row){ if(row==null)return; editingId=row.id; entryDate.setValue(LocalDate.parse(row.date.get())); description.setText(row.description.get()); referenceNo.setText(row.reference.get()); amount.setText(String.valueOf(row.amount.get())); paymentMode.setValue(row.paymentMode.get()); if(mode==Mode.BANK){ if("DEPOSIT".equals(row.rawType))creditRadio.setSelected(true);else debitRadio.setSelected(true); if(!row.account.get().isBlank())bankAccount.setValue(row.account.get()); }else{expenseCategory.setValue(row.type.get()); if(!row.account.get().isBlank())expenseAccount.setValue(row.account.get());} saveButton.setText(mode==Mode.BANK?"Update Entry":"Update Expense"); focusForm(); }
    private void deleteRow(EntryRow row){ if(row==null)return; Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Delete this entry? This action cannot be undone."); a.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM finance_register WHERE id=?")){p.setInt(1,row.id);p.executeUpdate();loadMetrics();applyFilters();}catch(Exception e){error(e.getMessage());}}); }

    private void ensureFinanceColumns(){ addColumn("account_name","TEXT"); addColumn("bill_path","TEXT"); addColumn("reconciled","INTEGER NOT NULL DEFAULT 0"); }
    private void addColumn(String name,String ddl){ try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement()){s.execute("ALTER TABLE finance_register ADD COLUMN IF NOT EXISTS "+name+" "+ddl);}catch(Exception ignored){} }
    private String nextVoucher() throws SQLException { try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM finance_register")){return "VCH-"+LocalDate.now().getYear()+"-"+String.format("%05d",r.next()?r.getInt(1):1);} }
    private double scalar(String sql){try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){return r.next()?r.getDouble(1):0;}catch(Exception e){return 0;}}
    private static String mask(String s){return s.length()<=4?s:"••••"+s.substring(s.length()-4);} private static String safe(String s,String d){return s==null||s.isBlank()?d:s;} private static String money(double v){return String.format(Locale.ENGLISH,"₹ %,.2f",v);} private static String text(ComboBox<String> c){String e=c.isEditable()?c.getEditor().getText():c.getValue();return e==null?"":e.trim();}
    private String chipStyle(String s){String x=s.toLowerCase(Locale.ROOT); if(x.contains("deposit"))return"finance-chip-green";if(x.contains("withdraw"))return"finance-chip-red";if(x.contains("office"))return"finance-chip-purple";if(x.contains("travel")||x.contains("transport"))return"finance-chip-blue";if(x.contains("marketing")||x.contains("maintenance"))return"finance-chip-orange";return"finance-chip-teal";}
    private void error(String text){new OwnedAlert(Alert.AlertType.ERROR,text==null?"Operation failed":text).showAndWait();}

    public static final class EntryRow { final int id; final SimpleStringProperty date,type,description,account,paymentMode,reference; final SimpleDoubleProperty amount; final String rawType; EntryRow(int id,String d,String t,String desc,String acc,String pm,String ref,double amt,String raw){this.id=id;date=new SimpleStringProperty(d);type=new SimpleStringProperty(t);description=new SimpleStringProperty(desc);account=new SimpleStringProperty(acc);paymentMode=new SimpleStringProperty(pm);reference=new SimpleStringProperty(ref);amount=new SimpleDoubleProperty(amt);rawType=raw==null?"":raw.toUpperCase(Locale.ROOT);} }
}
