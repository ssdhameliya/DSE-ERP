package org.example.controller;


import org.example.util.IconFactory;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.database.DatabaseManager;
import org.example.service.NotificationService;
import org.example.theme.ThemeManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class OperationsController {
    @FXML private Label lblReturns, lblExpenses, lblReceipts, lblReminders;
    private static int requestedTab = -1;
    @FXML private TabPane tabs;
    @FXML private TableView<ReturnRow> returnTable; @FXML private TableColumn<ReturnRow,String> rNo,rType,rDate,rInvoice,rParty,rItem,rReason; @FXML private TableColumn<ReturnRow,Number> rQty,rAmount;
    @FXML private TableView<FinanceRow> financeTable; @FXML private TableColumn<FinanceRow,String> fNo,fType,fDate,fParty,fCategory,fReference,fMode; @FXML private TableColumn<FinanceRow,Number> fAmount;
    @FXML private TableView<ReminderRow> reminderTable; @FXML private TableColumn<ReminderRow,String> mTitle,mReference,mDue,mPriority,mStatus; @FXML private TableColumn<ReminderRow,Void> mAction;
    @FXML private TableView<UserRow> userTable; @FXML private TableColumn<UserRow,String> uName,uFull,uEmail,uRole,uStatus;

    @FXML public void initialize(){
        configureExplicitTableHeaderIcons();
        rNo.setCellValueFactory(v->v.getValue().no);rType.setCellValueFactory(v->v.getValue().type);rDate.setCellValueFactory(v->v.getValue().date);rInvoice.setCellValueFactory(v->v.getValue().invoice);rParty.setCellValueFactory(v->v.getValue().party);rItem.setCellValueFactory(v->v.getValue().item);rQty.setCellValueFactory(v->v.getValue().qty);rAmount.setCellValueFactory(v->v.getValue().amount);rReason.setCellValueFactory(v->v.getValue().reason);
        fNo.setCellValueFactory(v->v.getValue().no);fType.setCellValueFactory(v->v.getValue().type);fDate.setCellValueFactory(v->v.getValue().date);fParty.setCellValueFactory(v->v.getValue().party);fCategory.setCellValueFactory(v->v.getValue().category);fReference.setCellValueFactory(v->v.getValue().reference);fMode.setCellValueFactory(v->v.getValue().mode);fAmount.setCellValueFactory(v->v.getValue().amount);
        mTitle.setCellValueFactory(v->v.getValue().title);mReference.setCellValueFactory(v->v.getValue().reference);mDue.setCellValueFactory(v->v.getValue().due);mPriority.setCellValueFactory(v->v.getValue().priority);mStatus.setCellValueFactory(v->v.getValue().status);setupReminderActions();
        uName.setCellValueFactory(v->v.getValue().username);uFull.setCellValueFactory(v->v.getValue().fullName);uEmail.setCellValueFactory(v->v.getValue().email);uRole.setCellValueFactory(v->v.getValue().role);uStatus.setCellValueFactory(v->v.getValue().status);refreshAll();
        if (requestedTab >= 0 && requestedTab < tabs.getTabs().size()) tabs.getSelectionModel().select(requestedTab);
        requestedTab = -1;
    }

    public static void selectInitialTab(int index) { requestedTab = index; }

    private void refreshAll(){loadReturns();loadFinance();loadReminders();loadUsers();loadMetrics();}
    private void loadMetrics(){
        lblReturns.setText(String.valueOf((long)scalar("SELECT COUNT(*) FROM return_register WHERE substr(return_date,1,7)=strftime('%Y-%m','now')")));
        lblExpenses.setText(String.format("₹ %,.2f",scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE voucher_type='EXPENSE' AND substr(voucher_date,1,7)=strftime('%Y-%m','now')")));
        lblReceipts.setText(String.format("₹ %,.2f",scalar("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE voucher_type='CUSTOMER RECEIPT' AND substr(voucher_date,1,7)=strftime('%Y-%m','now')")));
        lblReminders.setText(String.valueOf((long)scalar("SELECT COUNT(*) FROM reminder_register WHERE status='OPEN'")));
    }
    private double scalar(String sql){try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){return r.next()?r.getDouble(1):0;}catch(Exception e){return 0;}}
    private void loadReturns(){List<ReturnRow>x=new ArrayList<>();String sql="SELECT r.*,COALESCE(p.name,'') party,COALESCE(i.description,r.item_code) item FROM return_register r LEFT JOIN party_master p ON p.id=r.party_id LEFT JOIN item_master i ON i.item_code=r.item_code ORDER BY r.return_date DESC,r.id DESC";try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet q=s.executeQuery(sql)){while(q.next())x.add(new ReturnRow(q.getString("return_no"),q.getString("return_type"),q.getString("return_date"),q.getString("invoice_no"),q.getString("party"),q.getString("item"),q.getDouble("quantity"),q.getDouble("amount"),q.getString("reason")));}catch(Exception e){error(e);}returnTable.getItems().setAll(x);}
    private void loadFinance(){List<FinanceRow>x=new ArrayList<>();String sql="SELECT f.*,COALESCE(p.name,'') party FROM finance_register f LEFT JOIN party_master p ON p.id=f.party_id ORDER BY f.voucher_date DESC,f.id DESC";try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet q=s.executeQuery(sql)){while(q.next())x.add(new FinanceRow(q.getString("voucher_no"),q.getString("voucher_type"),q.getString("voucher_date"),q.getString("party"),q.getString("category"),q.getString("reference_no"),q.getString("payment_mode"),q.getDouble("amount")));}catch(Exception e){error(e);}financeTable.getItems().setAll(x);}
    private void loadReminders(){List<ReminderRow>x=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet q=s.executeQuery("SELECT * FROM reminder_register ORDER BY status='OPEN' DESC,due_date,id DESC")){while(q.next())x.add(new ReminderRow(q.getInt("id"),q.getString("title"),q.getString("reference_no"),q.getString("due_date"),q.getString("priority"),q.getString("status")));}catch(Exception e){error(e);}reminderTable.getItems().setAll(x);}
    private void loadUsers(){List<UserRow>x=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet q=s.executeQuery("SELECT * FROM users ORDER BY username")){while(q.next())x.add(new UserRow(q.getString("username"),q.getString("full_name"),q.getString("email"),q.getString("role"),q.getInt("active")==1?"Active":"Disabled"));}catch(Exception e){error(e);}userTable.getItems().setAll(x);}

    @FXML private void newReturn(){
        Dialog<ButtonType>d=new Dialog<>();d.setTitle("Record Return");ComboBox<String>type=new ComboBox<>();type.getItems().setAll("SALES RETURN","PURCHASE RETURN");type.setValue("SALES RETURN");DatePicker date=new DatePicker(LocalDate.now());TextField invoice=new TextField(),qty=new TextField("1"),amount=new TextField("0"),reason=new TextField();ComboBox<Choice>party=new ComboBox<>(),item=new ComboBox<>();party.getItems().setAll(choices("SELECT id,name FROM party_master ORDER BY name"));item.getItems().setAll(choices("SELECT id,item_code||' - '||description FROM item_master ORDER BY description"));GridPane g=form();add(g,0,"Type",type,"Date",date);add(g,1,"Invoice",invoice,"Party",party);add(g,2,"Item",item,"Quantity",qty);add(g,3,"Amount",amount,"Reason",reason);d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Save",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{if(item.getValue()==null)throw new IllegalArgumentException("Select an item");double q=Double.parseDouble(qty.getText()),a=Double.parseDouble(amount.getText());String itemCode=item.getValue().name.split(" - ",2)[0];try(Connection c=DatabaseManager.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("INSERT INTO return_register(return_no,return_type,return_date,invoice_no,party_id,item_code,quantity,amount,reason) VALUES(?,?,?,?,?,?,?,?,?)");PreparedStatement stock=c.prepareStatement("UPDATE item_master SET opening_stock=COALESCE(opening_stock,0)+? WHERE item_code=?")){p.setString(1,next("RET","return_register"));p.setString(2,type.getValue());p.setString(3,date.getValue().toString());p.setString(4,invoice.getText());if(party.getValue()==null)p.setNull(5,Types.INTEGER);else p.setInt(5,party.getValue().id);p.setString(6,itemCode);p.setDouble(7,q);p.setDouble(8,a);p.setString(9,reason.getText());p.executeUpdate();stock.setDouble(1,type.getValue().startsWith("SALES")?q:-q);stock.setString(2,itemCode);stock.executeUpdate();c.commit();}catch(Exception e){c.rollback();throw e;}}NotificationService.add(type.getValue()+" recorded.");loadReturns();}catch(Exception e){error(e);}});
    }

    @FXML private void newFinance(){
        Dialog<ButtonType>d=new Dialog<>();d.setTitle("Add Financial Voucher");ComboBox<String>type=new ComboBox<>();type.getItems().setAll("EXPENSE","SUPPLIER PAYMENT","CUSTOMER RECEIPT");type.setValue("EXPENSE");DatePicker date=new DatePicker(LocalDate.now());ComboBox<Choice>party=new ComboBox<>();party.getItems().setAll(choices("SELECT id,name FROM party_master ORDER BY name"));TextField category=new TextField(),reference=new TextField(),amount=new TextField(),notes=new TextField();ComboBox<String>mode=new ComboBox<>();mode.getItems().setAll("Cash","Bank","UPI","Cheque","Card","Other");mode.setValue("Bank");GridPane g=form();add(g,0,"Type",type,"Date",date);add(g,1,"Party",party,"Category",category);add(g,2,"Reference",reference,"Amount",amount);add(g,3,"Mode",mode,"Notes",notes);d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Save",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{double a=Double.parseDouble(amount.getText());if(a<=0)throw new IllegalArgumentException("Amount must be greater than zero");try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO finance_register(voucher_no,voucher_type,voucher_date,party_id,category,reference_no,amount,payment_mode,notes) VALUES(?,?,?,?,?,?,?,?,?)")){p.setString(1,next("VCH","finance_register"));p.setString(2,type.getValue());p.setString(3,date.getValue().toString());if(party.getValue()==null)p.setNull(4,Types.INTEGER);else p.setInt(4,party.getValue().id);p.setString(5,category.getText());p.setString(6,reference.getText());p.setDouble(7,a);p.setString(8,mode.getValue());p.setString(9,notes.getText());p.executeUpdate();}NotificationService.add(type.getValue()+" voucher recorded.");loadFinance();}catch(Exception e){error(e);}});
    }

    @FXML private void newReminder(){
        Dialog<ButtonType>d=new Dialog<>();d.setTitle("Add Reminder");TextField title=new TextField(),reference=new TextField(),notes=new TextField();DatePicker due=new DatePicker(LocalDate.now());ComboBox<String>priority=new ComboBox<>();priority.getItems().setAll("LOW","NORMAL","HIGH","URGENT");priority.setValue("NORMAL");GridPane g=form();add(g,0,"Title",title,"Reference",reference);add(g,1,"Due date",due,"Priority",priority);add(g,2,"Notes",notes,"",new Label());d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Save",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{if(title.getText().isBlank())throw new IllegalArgumentException("Enter a title");try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO reminder_register(title,reference_no,due_date,priority,notes) VALUES(?,?,?,?,?)")){p.setString(1,title.getText());p.setString(2,reference.getText());p.setString(3,due.getValue().toString());p.setString(4,priority.getValue());p.setString(5,notes.getText());p.executeUpdate();}NotificationService.add("Reminder added: "+title.getText());loadReminders();}catch(Exception e){error(e);}});
    }

    @FXML private void newUser(){
        Dialog<ButtonType>d=new Dialog<>();d.setTitle("Add User");TextField username=new TextField(),full=new TextField(),email=new TextField();PasswordField password=new PasswordField();ComboBox<String>role=new ComboBox<>();role.getItems().setAll("ADMIN","MANAGER","SALES","PURCHASE","VIEWER");role.setValue("VIEWER");GridPane g=form();add(g,0,"Username",username,"Full name",full);add(g,1,"Email",email,"Password",password);add(g,2,"Role",role,"",new Label());d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Create user",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{if(username.getText().isBlank()||password.getText().isBlank())throw new IllegalArgumentException("Username and password are required");try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password,full_name,role,email,active) VALUES(?,?,?,?,?,1)")){p.setString(1,username.getText());p.setString(2,password.getText());p.setString(3,full.getText());p.setString(4,role.getValue());p.setString(5,email.getText());p.executeUpdate();}NotificationService.add("User "+username.getText()+" created.");loadUsers();}catch(Exception e){error(e);}});
    }

    /** Opens a full registration-style user form and refreshes the user table. */
    @FXML private void openUserRegistration() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/pages/UserDialog.fxml"));
            Stage stage = new Stage();
            stage.initOwner(tabs.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle("Add User");
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene);
            stage.setScene(scene);
            stage.showAndWait();
            loadUsers();
        } catch (Exception exception) { error(exception); }
    }

    private void setupReminderActions(){mAction.setCellFactory(c->new TableCell<>(){final Button done=new Button("Complete");final HBox box=new HBox(done);{box.setAlignment(Pos.CENTER);done.setOnAction(e->{ReminderRow r=getTableView().getItems().get(getIndex());try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE reminder_register SET status='COMPLETED' WHERE id=?")){p.setInt(1,r.id);p.executeUpdate();loadReminders();}catch(Exception x){error(x);}});}protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:box);if(!empty)done.setDisable(getTableView().getItems().get(getIndex()).status.get().equals("COMPLETED"));}});}
    private List<Choice>choices(String sql){List<Choice>x=new ArrayList<>();try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())x.add(new Choice(r.getInt(1),r.getString(2)));}catch(Exception e){error(e);}return x;}
    private String next(String prefix,String table)throws SQLException{try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM "+table)){return prefix+"-"+LocalDate.now().getYear()+"-"+String.format("%05d",r.next()?r.getInt(1):1);}}
    private GridPane form(){GridPane g=new GridPane();g.setHgap(10);g.setVgap(10);return g;}private void add(GridPane g,int row,String l1,Control c1,String l2,Control c2){g.addRow(row,new Label(l1),c1,new Label(l2),c2);}private void error(Exception e){new Alert(Alert.AlertType.ERROR,e.getMessage()==null?"Operation failed":e.getMessage()).showAndWait();}
    static final class Choice{final int id;final String name;Choice(int i,String n){id=i;name=n;}public String toString(){return name;}}
    public static final class ReturnRow{final SimpleStringProperty no,type,date,invoice,party,item,reason;final SimpleDoubleProperty qty,amount;ReturnRow(String a,String b,String c,String d,String e,String f,double g,double h,String i){no=new SimpleStringProperty(a);type=new SimpleStringProperty(b);date=new SimpleStringProperty(c);invoice=new SimpleStringProperty(d);party=new SimpleStringProperty(e);item=new SimpleStringProperty(f);qty=new SimpleDoubleProperty(g);amount=new SimpleDoubleProperty(h);reason=new SimpleStringProperty(i);}}
    public static final class FinanceRow{final SimpleStringProperty no,type,date,party,category,reference,mode;final SimpleDoubleProperty amount;FinanceRow(String a,String b,String c,String d,String e,String f,String g,double h){no=new SimpleStringProperty(a);type=new SimpleStringProperty(b);date=new SimpleStringProperty(c);party=new SimpleStringProperty(d);category=new SimpleStringProperty(e);reference=new SimpleStringProperty(f);mode=new SimpleStringProperty(g);amount=new SimpleDoubleProperty(h);}}
    public static final class ReminderRow{final int id;final SimpleStringProperty title,reference,due,priority,status;ReminderRow(int i,String a,String b,String c,String d,String e){id=i;title=new SimpleStringProperty(a);reference=new SimpleStringProperty(b);due=new SimpleStringProperty(c);priority=new SimpleStringProperty(d);status=new SimpleStringProperty(e);}}
    public static final class UserRow{final SimpleStringProperty username,fullName,email,role,status;UserRow(String a,String b,String c,String d,String e){username=new SimpleStringProperty(a);fullName=new SimpleStringProperty(b);email=new SimpleStringProperty(c);role=new SimpleStringProperty(d);status=new SimpleStringProperty(e);}}


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(rNo, "document");
        IconFactory.applyTableHeaderIcon(rType, "return");
        IconFactory.applyTableHeaderIcon(rDate, "calendar");
        IconFactory.applyTableHeaderIcon(rInvoice, "document");
        IconFactory.applyTableHeaderIcon(rParty, "customer");
        IconFactory.applyTableHeaderIcon(rItem, "item");
        IconFactory.applyTableHeaderIcon(rReason, "document");
        IconFactory.applyTableHeaderIcon(rQty, "quantity");
        IconFactory.applyTableHeaderIcon(rAmount, "currency");
        IconFactory.applyTableHeaderIcon(fNo, "document");
        IconFactory.applyTableHeaderIcon(fType, "status");
        IconFactory.applyTableHeaderIcon(fDate, "calendar");
        IconFactory.applyTableHeaderIcon(fParty, "customer");
        IconFactory.applyTableHeaderIcon(fCategory, "category");
        IconFactory.applyTableHeaderIcon(fReference, "document");
        IconFactory.applyTableHeaderIcon(fMode, "payment");
        IconFactory.applyTableHeaderIcon(fAmount, "currency");
        IconFactory.applyTableHeaderIcon(mTitle, "reminder");
        IconFactory.applyTableHeaderIcon(mReference, "document");
        IconFactory.applyTableHeaderIcon(mDue, "calendar");
        IconFactory.applyTableHeaderIcon(mPriority, "warning");
        IconFactory.applyTableHeaderIcon(mStatus, "status");
        IconFactory.applyTableHeaderIcon(mAction, "actions");
        IconFactory.applyTableHeaderIcon(uName, "user");
        IconFactory.applyTableHeaderIcon(uFull, "user");
        IconFactory.applyTableHeaderIcon(uEmail, "email");
        IconFactory.applyTableHeaderIcon(uRole, "role");
        IconFactory.applyTableHeaderIcon(uStatus, "status");
    }
}
