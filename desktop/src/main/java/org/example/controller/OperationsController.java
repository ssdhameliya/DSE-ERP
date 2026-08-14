package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;


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
import org.example.api.returns.ReturnApiClient;
import org.example.api.operations.OperationsApiClient;
import org.example.api.insights.InsightsApiClient;
import org.example.api.admin.AdminApiClient;
import org.example.api.master.MasterApiClient;
import org.example.model.Party;
import org.example.model.Item;
import org.example.service.NotificationService;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;

import java.time.LocalDate;
import java.util.*;

public class OperationsController {
    private final ReturnApiClient returnApi=new ReturnApiClient();
    private final OperationsApiClient operationsApi=new OperationsApiClient();
    private final InsightsApiClient insightsApi=new InsightsApiClient();
    private final AdminApiClient adminApi=new AdminApiClient();
    private final MasterApiClient masterApi=new MasterApiClient();
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
        LocalDate first=BusinessClock.today().withDayOfMonth(1);
        long returns=returnTable.getItems().stream().filter(r->{try{return !LocalDate.parse(r.date.get()).isBefore(first);}catch(Exception e){return false;}}).count();
        double expenses=financeTable.getItems().stream().filter(r->"EXPENSE".equalsIgnoreCase(r.type.get())).filter(r->{try{return !LocalDate.parse(r.date.get()).isBefore(first);}catch(Exception e){return false;}}).mapToDouble(r->r.amount.get()).sum();
        double receipts=financeTable.getItems().stream().filter(r->"CUSTOMER RECEIPT".equalsIgnoreCase(r.type.get())).filter(r->{try{return !LocalDate.parse(r.date.get()).isBefore(first);}catch(Exception e){return false;}}).mapToDouble(r->r.amount.get()).sum();
        long reminders=reminderTable.getItems().stream().filter(r->"OPEN".equalsIgnoreCase(r.status.get())).count();
        lblReturns.setText(String.valueOf(returns));lblExpenses.setText(String.format("₹ %,.2f",expenses));lblReceipts.setText(String.format("₹ %,.2f",receipts));lblReminders.setText(String.valueOf(reminders));
    }
    private void loadReturns(){List<ReturnRow>x=new ArrayList<>();try{for(String type:List.of("SALES RETURN","PURCHASE RETURN")){for(ReturnApiClient.Summary summary:returnApi.list(type)){ReturnApiClient.Details d=returnApi.details(summary.no());if(d.lines()!=null)for(ReturnApiClient.Line l:d.lines())x.add(new ReturnRow(d.no(),d.type(),d.date(),d.invoice(),d.party(),l.name(),l.quantity(),l.amount(),l.reason()));}}}catch(Exception e){error(e);}returnTable.getItems().setAll(x);}
    private void loadFinance(){List<FinanceRow>x=new ArrayList<>();try{Map<Integer,String> parties=new HashMap<>();for(Party p:masterApi.parties("CUSTOMER"))parties.put(p.getId(),p.getName());for(Party p:masterApi.parties("SUPPLIER"))parties.put(p.getId(),p.getName());for(OperationsApiClient.FinanceEntry f:operationsApi.finance())x.add(new FinanceRow(f.voucherNo(),f.voucherType(),f.voucherDate(),f.partyId()==null?"":parties.getOrDefault(f.partyId(),""),f.category(),f.referenceNo(),f.paymentMode(),f.amount()));}catch(Exception e){error(e);}financeTable.getItems().setAll(x);}
    private void loadReminders(){List<ReminderRow>x=new ArrayList<>();try{for(InsightsApiClient.ReminderDto r:insightsApi.reminders())x.add(new ReminderRow(r.id()==null?0:r.id(),r.title(),r.referenceNo(),r.dueDate(),r.priority(),r.status()));}catch(Exception e){error(e);}reminderTable.getItems().setAll(x);}
    private void loadUsers(){List<UserRow>x=new ArrayList<>();try{for(AdminApiClient.UserDto u:adminApi.users())x.add(new UserRow(u.username(),u.fullName(),u.email(),u.role(),u.active()?"Active":"Disabled"));}catch(Exception e){error(e);}userTable.getItems().setAll(x);}

    @FXML private void newReturn(){
        Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Record Return");ComboBox<String>type=new ComboBox<>();type.getItems().setAll("SALES RETURN","PURCHASE RETURN");type.setValue("SALES RETURN");DatePicker date=new DatePicker(BusinessClock.today());TextField invoice=new TextField(),qty=new TextField("1"),amount=new TextField("0"),reason=new TextField();ComboBox<Choice>party=new ComboBox<>(),item=new ComboBox<>();
        try{List<Choice> parties=new ArrayList<>();for(Party p:masterApi.parties("CUSTOMER"))parties.add(new Choice(p.getId(),p.getName()));for(Party p:masterApi.parties("SUPPLIER"))parties.add(new Choice(p.getId(),p.getName()));party.getItems().setAll(parties);List<Choice> items=new ArrayList<>();for(Item i:masterApi.items())items.add(new Choice(i.getId(),i.getItemCode()+" - "+i.getDescription()));item.getItems().setAll(items);}catch(Exception e){error(e);}
        GridPane g=form();add(g,0,"Type",type,"Date",date);add(g,1,"Invoice",invoice,"Party",party);add(g,2,"Item",item,"Quantity",qty);add(g,3,"Amount",amount,"Reason",reason);d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Save",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{if(item.getValue()==null||party.getValue()==null)throw new IllegalArgumentException("Select party and item");double q=Double.parseDouble(qty.getText()),a=Double.parseDouble(amount.getText());String itemCode=item.getValue().name.split(" - ",2)[0];returnApi.create(new ReturnApiClient.CreateRequest(type.getValue(),invoice.getText(),party.getValue().id,date.getValue().toString(),List.of(new ReturnApiClient.CreateLine(itemCode,q,a,reason.getText()))));NotificationService.add(type.getValue()+" recorded.");loadReturns();loadMetrics();}catch(Exception e){error(e);}});
    }

    @FXML private void newFinance(){
        Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Add Financial Voucher");ComboBox<String>type=new ComboBox<>();type.getItems().setAll("EXPENSE","SUPPLIER PAYMENT","CUSTOMER RECEIPT");type.setValue("EXPENSE");DatePicker date=new DatePicker(BusinessClock.today());ComboBox<Choice>party=new ComboBox<>();try{List<Choice> ps=new ArrayList<>();for(Party p:masterApi.parties("CUSTOMER"))ps.add(new Choice(p.getId(),p.getName()));for(Party p:masterApi.parties("SUPPLIER"))ps.add(new Choice(p.getId(),p.getName()));party.getItems().setAll(ps);}catch(Exception e){error(e);}TextField category=new TextField(),reference=new TextField(),amount=new TextField(),notes=new TextField();ComboBox<String>mode=new ComboBox<>();mode.getItems().setAll("Cash","Bank","UPI","Cheque","Card","Other");mode.setValue("Bank");GridPane g=form();add(g,0,"Type",type,"Date",date);add(g,1,"Party",party,"Category",category);add(g,2,"Reference",reference,"Amount",amount);add(g,3,"Mode",mode,"Notes",notes);d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Save",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{double a=Double.parseDouble(amount.getText());if(a<=0)throw new IllegalArgumentException("Amount must be greater than zero");operationsApi.saveFinance(new OperationsApiClient.FinanceEntry(null,operationsApi.nextVoucher(),type.getValue(),date.getValue().toString(),party.getValue()==null?null:party.getValue().id,category.getText(),reference.getText(),a,mode.getValue(),notes.getText(),null,null,false));NotificationService.add(type.getValue()+" voucher recorded.");loadFinance();loadMetrics();}catch(Exception e){error(e);}});
    }

    @FXML private void newReminder(){Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Add Reminder");TextField title=new TextField(),reference=new TextField(),notes=new TextField();DatePicker due=new DatePicker(BusinessClock.today());ComboBox<String>priority=new ComboBox<>();priority.getItems().setAll("LOW","NORMAL","HIGH","URGENT");priority.setValue("NORMAL");GridPane g=form();add(g,0,"Title",title,"Reference",reference);add(g,1,"Due date",due,"Priority",priority);add(g,2,"Notes",notes,"",new Label());d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Save",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{if(title.getText().isBlank())throw new IllegalArgumentException("Enter a title");insightsApi.saveReminder(new InsightsApiClient.ReminderDto(null,title.getText(),reference.getText(),due.getValue().toString(),priority.getValue(),notes.getText(),"OPEN","System",null));NotificationService.add("Reminder added: "+title.getText());loadReminders();loadMetrics();}catch(Exception e){error(e);}});}

    @FXML private void newUser(){Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Add User");TextField username=new TextField(),full=new TextField(),email=new TextField();PasswordField password=new PasswordField();ComboBox<String>role=new ComboBox<>();role.getItems().setAll("ADMIN","MANAGER","SALES");role.setValue("SALES");role.setConverter(new javafx.util.StringConverter<>(){public String toString(String value){return "SALES".equals(value)?"Sale":"MANAGER".equals(value)?"Manager":"Admin";}public String fromString(String value){return value;}});GridPane g=form();add(g,0,"Username",username,"Full name",full);add(g,1,"Email",email,"Password",password);add(g,2,"Role",role,"",new Label());d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(new ButtonType("Create user",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);d.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{if(username.getText().isBlank()||password.getText().isBlank())throw new IllegalArgumentException("Username and password are required");adminApi.saveUser(new AdminApiClient.UserSaveRequest(null,username.getText(),password.getText(),full.getText(),email.getText(),role.getValue(),null,null,null,true,false,false));NotificationService.add("User "+username.getText()+" created.");loadUsers();}catch(Exception e){error(e);}});}

    /** Opens a full registration-style user form and refreshes the user table. */
    @FXML private void openUserRegistration() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/pages/UserDialog.fxml"));
            Stage stage = new Stage();
            PlatformUiSupport.configureDialogStage(stage, tabs, "Add User", true);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene);
            stage.setScene(scene);
            stage.showAndWait();
            loadUsers();
        } catch (Exception exception) { error(exception); }
    }

    private void setupReminderActions(){mAction.setCellFactory(c->new TableCell<>(){final Button done=new Button("Complete");final HBox box=new HBox(done);{box.setAlignment(Pos.CENTER);done.setOnAction(e->{ReminderRow r=getTableView().getItems().get(getIndex());try{insightsApi.reminderStatus(r.id,"COMPLETED",null);loadReminders();loadMetrics();}catch(Exception x){error(x);}});}protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:box);if(!empty)done.setDisable(getTableView().getItems().get(getIndex()).status.get().equals("COMPLETED"));}});}
    private GridPane form(){GridPane g=new GridPane();g.setHgap(10);g.setVgap(10);return g;}private void add(GridPane g,int row,String l1,Control c1,String l2,Control c2){g.addRow(row,new Label(l1),c1,new Label(l2),c2);}private void error(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?"Operation failed":e.getMessage()).showAndWait();}
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
