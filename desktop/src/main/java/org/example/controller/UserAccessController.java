package org.example.controller;
import org.example.service.PermissionService;
import org.example.navigation.ScreenLifecycle;

import org.example.util.BusinessClock;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.OwnedTextInputDialog;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.example.api.admin.AdminApiClient;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;
import org.example.util.IconFactory;
import org.example.util.RegisterDetailDrawer;
import org.example.util.RegisterUiSupport;
import org.example.util.OperationalUiSupport;
import org.example.util.SemanticTableCells;


import java.time.LocalDate;
import java.util.*;

/** Premium database-backed user, role and permission administration. */
public class UserAccessController implements ScreenLifecycle {
    @FXML private Label iconTotalUsers, iconActiveUsers, iconRoles, iconLocked, iconLogins;
    @FXML private Label lblTotal,lblActive,lblRoles,lblLocked,lblLogins,lblRoleHint;
    @FXML private TextField txtSearch;
    @FXML private StackPane userAccessPageIcon;
    @FXML private ComboBox<String> cmbRole,cmbStatus,cmbBranch,cmbPermissionRole;
    @FXML private TabPane accessTabs;
    @FXML private Tab tabRoles,tabPermissions;

    @FXML private TableView<UserRow> table;
    @FXML private TableColumn<UserRow,String> colUser,colEmail,colRole,colDepartment,colAccess,colBranch,colStatus,colLastLogin,colMfa;
    @FXML private TableColumn<UserRow,Void> colActions;

    @FXML private TableView<RoleRow> roleTable;
    @FXML private TableColumn<RoleRow,String> colRoleName,colRoleDescription,colRoleStatus;
    @FXML private TableColumn<RoleRow,Number> colRoleUsers;
    @FXML private TableColumn<RoleRow,Void> colRoleActions;

    @FXML private TableView<PermissionRow> permissionTable;
    @FXML private TableColumn<PermissionRow,String> colPermissionModule,colPermissionAction,colPermissionDescription;
    @FXML private TableColumn<PermissionRow,Boolean> colPermissionAllowed;

    private final ObservableList<UserRow> users=FXCollections.observableArrayList();
    private final ObservableList<RoleRow> roles=FXCollections.observableArrayList();
    private final ObservableList<PermissionRow> permissions=FXCollections.observableArrayList();
    private final AdminApiClient adminApi=new AdminApiClient();
    private long permissionRowVersion;
    private final Map<String,String> roleDisplayNames=new LinkedHashMap<>();
    private FilteredList<UserRow> filtered;
    private RegisterDetailDrawer detailDrawer;
    private UserRow detailUser;

    @FXML public void initialize(){
        if(userAccessPageIcon!=null)userAccessPageIcon.getChildren().setAll(IconFactory.icon("users",24));
        installKpiIcons();
        configureUserTable(); configureRoleTable(); configurePermissionTable(); configureIcons();
        cmbStatus.getItems().setAll("All Statuses","Active","Inactive","Locked"); cmbStatus.setValue("All Statuses");
        cmbPermissionRole.setConverter(new StringConverter<>() { public String toString(String code){return code==null?"":roleDisplayNames.getOrDefault(code.toUpperCase(Locale.ROOT),code);} public String fromString(String value){return value;} });
        filtered=new FilteredList<>(users,r->true); table.setItems(filtered); roleTable.setItems(roles); permissionTable.setItems(permissions);
        installDetailDrawer();
        txtSearch.textProperty().addListener((o,a,b)->filter()); cmbRole.valueProperty().addListener((o,a,b)->filter());
        cmbStatus.valueProperty().addListener((o,a,b)->filter()); cmbBranch.valueProperty().addListener((o,a,b)->filter());
        cmbPermissionRole.valueProperty().addListener((o,a,b)->loadPermissions(b));
        refresh();
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        if (reusedFromCache) refresh();
    }

    private void configureUserTable(){
        colUser.setCellValueFactory(v->v.getValue().user); colEmail.setCellValueFactory(v->v.getValue().email); colRole.setCellValueFactory(v->v.getValue().role);
        colDepartment.setCellValueFactory(v->v.getValue().department); colAccess.setCellValueFactory(v->v.getValue().access); colBranch.setCellValueFactory(v->v.getValue().branch);
        colStatus.setCellValueFactory(v->v.getValue().status); colLastLogin.setCellValueFactory(v->v.getValue().lastLogin); colMfa.setCellValueFactory(v->v.getValue().mfa);
        colStatus.setCellFactory(c->SemanticTableCells.status("status"));
        colMfa.setCellFactory(c->SemanticTableCells.status("status"));
        colActions.setCellFactory(c->userActionCell());
        table.setRowFactory(tv->{
            TableRow<UserRow> row=new TableRow<>();
            row.setOnMouseClicked(e->{
                if(row.isEmpty()||e.getButton()!=MouseButton.PRIMARY||RegisterUiSupport.isInteractiveTableTarget(e.getPickResult().getIntersectedNode(),row))return;
                if(e.getClickCount()==1){
                    UserRow clicked=row.getItem();
                    if(detailDrawer!=null&&detailDrawer.isOpen()&&detailUser==clicked)closeDetails();
                    else{table.getSelectionModel().select(clicked);showDetails(clicked);}
                    e.consume();
                }
            });
            return row;
        });
    }
    private void configureRoleTable(){
        colRoleName.setCellValueFactory(v->v.getValue().name); colRoleDescription.setCellValueFactory(v->v.getValue().description);
        colRoleUsers.setCellValueFactory(v->v.getValue().users); colRoleStatus.setCellValueFactory(v->v.getValue().status); colRoleStatus.setCellFactory(c->SemanticTableCells.status("status")); colRoleActions.setCellFactory(c->roleActionCell());
        roleTable.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null){cmbPermissionRole.setValue(b.code);lblRoleHint.setText(b.description.get());}});
    }
    private void configurePermissionTable(){
        colPermissionModule.setCellValueFactory(v->v.getValue().module); colPermissionAction.setCellValueFactory(v->v.getValue().action);
        colPermissionDescription.setCellValueFactory(v->v.getValue().description); colPermissionAllowed.setCellValueFactory(v->v.getValue().allowed);
        colPermissionAllowed.setCellFactory(CheckBoxTableCell.forTableColumn(colPermissionAllowed)); colPermissionAllowed.setEditable(true); permissionTable.setEditable(true);
    }

    @FXML private void refresh(){ loadRoles(); loadUsers(); refreshFilters(); updateMetrics(); filter(); }
    private void loadUsers(){
        closeDetails();
        users.clear();
        try{for(var u:adminApi.users())users.add(new UserRow(u,roleDisplayNames));}
        catch(Exception e){error("Users could not be loaded",e);}
    }
    private void loadRoles(){
        String selected=cmbPermissionRole.getValue(); roles.clear(); cmbPermissionRole.getItems().clear(); roleDisplayNames.clear();
        try{for(var r:adminApi.roles()){RoleRow row=new RoleRow(r);roles.add(row);roleDisplayNames.put(row.code,row.name.get());cmbPermissionRole.getItems().add(row.code);}}
        catch(Exception e){error("Roles could not be loaded",e);}
        if(selected!=null&&cmbPermissionRole.getItems().contains(selected))cmbPermissionRole.setValue(selected); else if(!cmbPermissionRole.getItems().isEmpty())cmbPermissionRole.getSelectionModel().selectFirst();
    }
    private void refreshFilters(){
        String selectedRole=cmbRole.getValue(),selectedBranch=cmbBranch.getValue(); cmbRole.getItems().setAll("All Roles");cmbBranch.getItems().setAll("All Branches");
        roles.stream().map(x->x.name.get()).forEach(cmbRole.getItems()::add); users.stream().map(x->x.branch.get()).filter(x->!x.equals("—")).distinct().sorted().forEach(cmbBranch.getItems()::add);
        cmbRole.setValue(selectedRole!=null&&cmbRole.getItems().contains(selectedRole)?selectedRole:"All Roles"); cmbBranch.setValue(selectedBranch!=null&&cmbBranch.getItems().contains(selectedBranch)?selectedBranch:"All Branches");
    }
    private void updateMetrics(){
        lblTotal.setText(String.valueOf(users.size())); lblActive.setText(String.valueOf(users.stream().filter(x->x.status.get().equals("Active")).count()));
        lblRoles.setText(String.valueOf(roles.size())); lblLocked.setText(String.valueOf(users.stream().filter(x->x.status.get().equals("Locked")).count()));
        long today=users.stream().filter(x->BusinessClock.today().equals(x.lastLoginDate)).count(); lblLogins.setText(String.valueOf(today));
    }

    private void loadPermissions(String roleName){
        permissions.clear(); permissionRowVersion=0L; if(roleName==null)return; boolean admin=org.example.service.SessionService.isAdminRole(roleName);
        try{var set=adminApi.permissionSet(roleName);permissionRowVersion=set.rowVersion();for(var p:set.permissions())permissions.add(new PermissionRow(p));}
        catch(Exception e){error("Permissions could not be loaded",e);}
        lblRoleHint.setText(admin?"Administrator receives full access by system policy.":"Current saved permission picture for "+roleName+".");
        permissionTable.setDisable(admin);
    }

    @FXML private void savePermissions(){
        String role=cmbPermissionRole.getValue(); if(role==null)return;
        if(org.example.service.SessionService.isAdminRole(role)){warning("Administrator always has full access and does not require manual permission changes.");return;}
        try{adminApi.savePermissions(role,permissions.stream().map(x->new AdminApiClient.PermissionSave(x.id,x.allowed.get())).toList(),permissionRowVersion);var set=adminApi.permissionSet(role);permissionRowVersion=set.rowVersion();PermissionService.refresh();NotificationService.add(role+" permissions updated.");}
        catch(Exception e){error("Permissions could not be saved. Reload the role if another administrator changed it first.",e);}
    }
    @FXML private void registrationApprovals(){ DashboardController.navigateFromChildPage("Registration Approvals", "/fxml/pages/RegistrationApprovals.fxml"); }
    @FXML private void showPermissionMatrix(){ DashboardController.navigateFromChildPage("Permission Matrix", "/fxml/pages/PermissionMatrix.fxml"); }
    @FXML private void manageRoles(){ openRoleMaster(); }

    @FXML private void addUser(){openUserDialog(null);} @FXML private void editSelected(){edit(table.getSelectionModel().getSelectedItem());}
    private void edit(UserRow row){if(row!=null)openUserDialog(row.id);}
    private void openUserDialog(Integer userId){
        try{FXMLLoader loader=new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/UserDialog.fxml"));Parent root=loader.load();org.example.util.ProfessionalUiEnhancer.enhance(root);UserDialogController controller=loader.getController();if(userId!=null)controller.editUser(userId);
            Stage stage=new Stage();PlatformUiSupport.configureDialogStage(stage, table, userId==null?"Add New User":"Edit User", true);Scene scene=new Scene(root);ThemeManager.applyTheme(scene);stage.setScene(scene);stage.setMinWidth(860);stage.setMinHeight(620);stage.showAndWait();refresh();}
        catch(Exception e){error("User form could not be opened",e);}
    }
    @FXML private void resetSelected(){resetPassword(table.getSelectionModel().getSelectedItem());}
    private void resetPassword(UserRow row){
        if(row==null){warning("Select a user first.");return;}TextInputDialog d=new OwnedTextInputDialog();d.setTitle("Reset Password");d.setHeaderText("Set a temporary password for "+row.user.get());d.setContentText("Temporary password:");
        d.showAndWait().map(String::trim).filter(x->x.length()>=6).ifPresent(password->{try{adminApi.resetPassword(row.id,password);audit(row.id,"PASSWORD_RESET",row.user.get());NotificationService.add("Password reset for "+row.user.get()+".");refresh();}catch(Exception e){error("Password could not be reset",e);}});
    }
    private void toggleLock(UserRow row){if(row==null)return;try{adminApi.setLocked(row.id,!row.locked);audit(row.id,row.locked?"USER_UNLOCKED":"USER_LOCKED",row.user.get());refresh();}catch(Exception e){error("Lock status could not be changed",e);}}
    private void deleteUser(UserRow row){if(row==null)return;if("admin".equalsIgnoreCase(row.user.get())){warning("The primary administrator cannot be deleted.");return;}if(!confirm("Delete user '"+row.user.get()+"'?"))return;try{adminApi.deleteUser(row.id);refresh();}catch(Exception e){error("User could not be deleted",e);}}

    @FXML private void addRole(){openRoleMaster();}
    @FXML private void editRole(){openRoleMaster();}
    @FXML private void deleteRole(){openRoleMaster();}
    private void openRoleMaster(){ MasterDataController.requestCategory("ROLE"); DashboardController.navigateFromChildPage("Master Data", "/fxml/pages/Masterdata.fxml"); }

    private TableCell<UserRow,Void> userActionCell(){return new TableCell<>(){final MenuButton menu=createActionMenu();{menu.getStyleClass().add("user-action-menu");}protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()){setGraphic(null);return;}UserRow row=getTableView().getItems().get(getIndex());menu.getItems().setAll(mi("View User","view",e->{table.getSelectionModel().select(row);showDetails(row);}),mi("Edit User","edit",e->edit(row)),mi("Reset Password","lock",e->resetPassword(row)),mi(row.locked?"Unlock Account":"Lock Account",row.locked?"reopen":"lock",e->toggleLock(row)),mi("View Role Permissions","permission",e->{cmbPermissionRole.setValue(row.roleCode);showPermissionMatrix();}),new SeparatorMenuItem(),mi("Delete User","delete",e->deleteUser(row)));setGraphic(menu);}};}
    private void installDetailDrawer(){
        detailDrawer=new RegisterDetailDrawer();
        detailDrawer.attachBesideTable(table);
        OperationalUiSupport.installEscapeClose(table, detailDrawer::isOpen, this::closeDetails);
    }
    private void showDetails(UserRow row){
        if(row==null||detailDrawer==null)return;
        detailUser=row;
        String status=row.locked?"Locked":row.active?"Active":"Inactive";
        String statusSemantic=row.locked?"locked":row.active?"active":"inactive";
        detailDrawer.showRecord(
            row.fullName,
            row.user.get(),
            List.of(
                RegisterDetailDrawer.field("Full Name",row.fullName,"user"),
                RegisterDetailDrawer.field("Username",row.user.get(),"user"),
                RegisterDetailDrawer.field("Email",row.email.get(),"email"),
                RegisterDetailDrawer.field("Role",row.role.get(),"role"),
                RegisterDetailDrawer.field("Department",row.department.get(),"category"),
                RegisterDetailDrawer.field("Access Level",row.access.get(),"security"),
                RegisterDetailDrawer.field("Branch",row.branch.get(),"location"),
                RegisterDetailDrawer.field("Account Status",status,statusSemantic),
                RegisterDetailDrawer.field("Locked",row.locked?"Yes":"No",row.locked?"lock":"complete"),
                RegisterDetailDrawer.field("MFA",row.mfa.get(),row.mfa.get().equalsIgnoreCase("Enabled")?"security":"status"),
                RegisterDetailDrawer.field("Last Login",row.lastLogin.get(),"calendar")
            )
        );
        Button editButton=new Button("Edit User",IconFactory.compactIcon("edit",15));
        editButton.getStyleClass().addAll("approved-button","approved-secondary-button");
        editButton.setOnAction(e->edit(row));
        Button resetButton=new Button("Reset Password",IconFactory.compactIcon("lock",15));
        resetButton.getStyleClass().addAll("approved-button","approved-secondary-button");
        resetButton.setOnAction(e->resetPassword(row));
        detailDrawer.setActions(editButton,resetButton);
    }
    private void closeDetails(){
        detailUser=null;
        if(detailDrawer!=null)detailDrawer.hideDrawer();
        if(table!=null)table.getSelectionModel().clearSelection();
    }

    private TableCell<RoleRow,Void> roleActionCell(){return new TableCell<>(){final MenuButton menu=createActionMenu();protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()){setGraphic(null);return;}RoleRow row=getTableView().getItems().get(getIndex());menu.getItems().setAll(mi("Open in Role Master","master",e->openRoleMaster()),mi("Manage Permissions","permission",e->{cmbPermissionRole.setValue(row.code);showPermissionMatrix();}));setGraphic(menu);}};}
    private MenuButton createActionMenu(){MenuButton m=new MenuButton("Actions");m.setGraphic(IconFactory.compactIcon("actions",15));m.setContentDisplay(ContentDisplay.LEFT);m.setGraphicTextGap(6);m.getStyleClass().add("table-action-menu");IconFactory.decorateActionMenu(m);return m;}
    private MenuItem mi(String text,String icon,javafx.event.EventHandler<javafx.event.ActionEvent>handler){MenuItem i=new MenuItem(text,IconFactory.compactIcon(icon,16));i.setOnAction(handler);return i;}
    private void filter(){String q=txtSearch.getText()==null?"":txtSearch.getText().toLowerCase(Locale.ROOT);filtered.setPredicate(r->{boolean text=q.isBlank()||(r.user.get()+" "+r.fullName+" "+r.email.get()+" "+r.department.get()+" "+r.branch.get()).toLowerCase(Locale.ROOT).contains(q);boolean role=cmbRole.getValue()==null||cmbRole.getValue().startsWith("All")||r.role.get().equals(cmbRole.getValue());boolean status=cmbStatus.getValue()==null||cmbStatus.getValue().startsWith("All")||r.status.get().equals(cmbStatus.getValue());boolean branch=cmbBranch.getValue()==null||cmbBranch.getValue().startsWith("All")||r.branch.get().equals(cmbBranch.getValue());return text&&role&&status&&branch;});}
    private void audit(int userId,String action,String detail){try{adminApi.audit(userId,action,detail+" | by="+(SessionService.current()==null?"System":SessionService.current().getUsername()));}catch(Exception ignored){}}
    private boolean confirm(String text){return new OwnedAlert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    private void warning(String message){new OwnedAlert(Alert.AlertType.WARNING,message).showAndWait();} private void error(String message,Exception e){e.printStackTrace();new OwnedAlert(Alert.AlertType.ERROR,message+".\n\n"+e.getMessage()).showAndWait();}

    private void configureIcons(){
    }

    public static final class UserRow{final int id;final String roleCode;final SimpleStringProperty user,email,role,department,access,branch,status,lastLogin,mfa;final String fullName;final boolean active,locked;final java.time.LocalDate lastLoginDate;UserRow(AdminApiClient.UserDto r,Map<String,String> roleNames){id=r.id();user=new SimpleStringProperty(r.username());fullName=blank(r.fullName(),r.username());email=new SimpleStringProperty(blank(r.email(),"—"));roleCode=blank(r.role(),"SALES").toUpperCase(Locale.ROOT);role=new SimpleStringProperty(roleNames.getOrDefault(roleCode,roleCode));department=new SimpleStringProperty(blank(r.department(),"—"));access=new SimpleStringProperty(blank(r.accessLevel(),"STANDARD"));branch=new SimpleStringProperty(blank(r.branch(),"—"));active=r.active();locked=r.locked();status=new SimpleStringProperty(locked?"Locked":active?"Active":"Inactive");lastLoginDate=BusinessClock.localDateOfTimestamp(r.lastLogin());lastLogin=new SimpleStringProperty(blank(r.lastLogin(),"Never").equals("Never")?"Never":BusinessClock.formatTimestamp(r.lastLogin()));mfa=new SimpleStringProperty(r.mfaEnabled()?"Enabled":"—");}}
    public static final class RoleRow{final int id;final String code;final SimpleStringProperty name,description,status;final SimpleIntegerProperty users;final boolean active;RoleRow(AdminApiClient.RoleDto r){id=r.id();code=blank(r.code(),"").toUpperCase(Locale.ROOT);name=new SimpleStringProperty(blank(r.displayName(),code));description=new SimpleStringProperty(blank(r.description(),"No description"));users=new SimpleIntegerProperty((int)r.userCount());active=r.active();status=new SimpleStringProperty(active?"Active":"Inactive");}}
    public static final class PermissionRow{final int id;final SimpleStringProperty module,action,description;final SimpleBooleanProperty allowed;PermissionRow(AdminApiClient.PermissionDto r){id=(int)r.id();module=new SimpleStringProperty(r.module());action=new SimpleStringProperty(r.action());description=new SimpleStringProperty(blank(r.description(),"—"));allowed=new SimpleBooleanProperty(r.allowed());}}
    private static String blank(String v,String fallback){return v==null||v.isBlank()?fallback:v;}

    private void installKpiIcons(){
        setKpiIcon(iconTotalUsers,"users");setKpiIcon(iconActiveUsers,"complete");setKpiIcon(iconRoles,"role");setKpiIcon(iconLocked,"lock");setKpiIcon(iconLogins,"login");
    }
    private void setKpiIcon(Label label,String semantic){if(label!=null){label.setText("");label.setGraphic(IconFactory.compactIcon(semantic,24));label.getProperties().put("erp-icon-preserve",true);}}
}
