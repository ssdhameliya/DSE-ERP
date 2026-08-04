package org.example.controller;

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
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.database.DatabaseManager;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;
import org.example.util.IconFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Premium database-backed user, role and permission administration. */
public class UserAccessController {
    @FXML private Label iconTotalUsers, iconActiveUsers, iconRoles, iconLocked, iconLogins;
    @FXML private Label lblTotal,lblActive,lblRoles,lblLocked,lblLogins,lblRoleHint;
    @FXML private TextField txtSearch;
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
    private FilteredList<UserRow> filtered;

    @FXML public void initialize(){
        installKpiIcons();
        configureUserTable(); configureRoleTable(); configurePermissionTable(); configureIcons();
        cmbStatus.getItems().setAll("All Statuses","Active","Inactive","Locked"); cmbStatus.setValue("All Statuses");
        filtered=new FilteredList<>(users,r->true); table.setItems(filtered); roleTable.setItems(roles); permissionTable.setItems(permissions);
        txtSearch.textProperty().addListener((o,a,b)->filter()); cmbRole.valueProperty().addListener((o,a,b)->filter());
        cmbStatus.valueProperty().addListener((o,a,b)->filter()); cmbBranch.valueProperty().addListener((o,a,b)->filter());
        cmbPermissionRole.valueProperty().addListener((o,a,b)->loadPermissions(b));
        refresh();
    }

    private void configureUserTable(){
        colUser.setCellValueFactory(v->v.getValue().user); colEmail.setCellValueFactory(v->v.getValue().email); colRole.setCellValueFactory(v->v.getValue().role);
        colDepartment.setCellValueFactory(v->v.getValue().department); colAccess.setCellValueFactory(v->v.getValue().access); colBranch.setCellValueFactory(v->v.getValue().branch);
        colStatus.setCellValueFactory(v->v.getValue().status); colLastLogin.setCellValueFactory(v->v.getValue().lastLogin); colMfa.setCellValueFactory(v->v.getValue().mfa);
        colActions.setCellFactory(c->userActionCell());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        colUser.setMinWidth(82); colUser.setPrefWidth(95);
        colEmail.setMinWidth(135); colEmail.setPrefWidth(170);
        colRole.setMinWidth(68); colRole.setPrefWidth(78);
        colDepartment.setMinWidth(72); colDepartment.setPrefWidth(88);
        colAccess.setMinWidth(72); colAccess.setPrefWidth(82);
        colBranch.setMinWidth(62); colBranch.setPrefWidth(72);
        colStatus.setMinWidth(72); colStatus.setPrefWidth(82);
        colLastLogin.setMinWidth(98); colLastLogin.setPrefWidth(112);
        colMfa.setMinWidth(48); colMfa.setPrefWidth(56);
        colActions.setMinWidth(58); colActions.setPrefWidth(64);
        table.setRowFactory(tv->{ TableRow<UserRow> row=new TableRow<>(); row.setOnMouseClicked(e->{if(!row.isEmpty()&&e.getButton()==MouseButton.PRIMARY&&e.getClickCount()==2)edit(row.getItem());}); return row;});
    }
    private void configureRoleTable(){
        colRoleName.setCellValueFactory(v->v.getValue().name); colRoleDescription.setCellValueFactory(v->v.getValue().description);
        colRoleUsers.setCellValueFactory(v->v.getValue().users); colRoleStatus.setCellValueFactory(v->v.getValue().status); colRoleActions.setCellFactory(c->roleActionCell());
        roleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colRoleName.setMinWidth(100); colRoleDescription.setMinWidth(220); colRoleUsers.setMinWidth(62); colRoleStatus.setMinWidth(78); colRoleActions.setMinWidth(70);
        roleTable.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null){cmbPermissionRole.setValue(b.name.get());lblRoleHint.setText(b.description.get());}});
    }
    private void configurePermissionTable(){
        colPermissionModule.setCellValueFactory(v->v.getValue().module); colPermissionAction.setCellValueFactory(v->v.getValue().action);
        colPermissionDescription.setCellValueFactory(v->v.getValue().description); colPermissionAllowed.setCellValueFactory(v->v.getValue().allowed);
        colPermissionAllowed.setCellFactory(CheckBoxTableCell.forTableColumn(colPermissionAllowed)); colPermissionAllowed.setEditable(true); permissionTable.setEditable(true);
    }

    @FXML private void refresh(){ loadUsers(); loadRoles(); refreshFilters(); updateMetrics(); filter(); }
    private void loadUsers(){
        users.clear(); String sql="SELECT u.*,COALESCE(r.role_name,u.role,'USER') resolved_role FROM users u LEFT JOIN roles r ON r.id=u.role_id ORDER BY u.full_name,u.username";
        try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())users.add(new UserRow(r));}
        catch(Exception e){error("Users could not be loaded",e);}
    }
    private void loadRoles(){
        String selected=cmbPermissionRole.getValue(); roles.clear(); cmbPermissionRole.getItems().clear();
        String sql="SELECT r.id,r.role_name,r.description,r.active,COUNT(u.id) user_count FROM roles r LEFT JOIN users u ON u.role_id=r.id OR (u.role_id IS NULL AND u.role=r.role_name) GROUP BY r.id ORDER BY r.role_name";
        try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next()){RoleRow row=new RoleRow(r);roles.add(row);cmbPermissionRole.getItems().add(row.name.get());}}
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
        long today=users.stream().filter(x->x.lastLogin.get().startsWith(LocalDate.now().toString())).count(); lblLogins.setText(String.valueOf(today));
    }

    private void loadPermissions(String roleName){
        permissions.clear(); if(roleName==null)return; boolean admin=roleName.equalsIgnoreCase("ADMIN")||roleName.equalsIgnoreCase("ADMINISTRATOR");
        String sql="SELECT p.id,p.module_name,p.action_name,p.description,COALESCE(rp.allowed,0) allowed FROM permissions p JOIN roles r ON r.role_name=? LEFT JOIN role_permission rp ON rp.permission_id=p.id AND rp.role_id=r.id WHERE p.active=1 ORDER BY p.module_name,p.action_name";
        try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,roleName);try(ResultSet r=p.executeQuery()){while(r.next())permissions.add(new PermissionRow(r,admin));}}
        catch(Exception e){error("Permissions could not be loaded",e);}
        lblRoleHint.setText(admin?"Administrator receives full access by system policy.":"Current saved permission picture for "+roleName+".");
        permissionTable.setDisable(admin);
    }

    @FXML private void savePermissions(){
        String role=cmbPermissionRole.getValue(); if(role==null)return;
        if(role.equalsIgnoreCase("ADMIN")||role.equalsIgnoreCase("ADMINISTRATOR")){warning("Administrator always has full access and does not require manual permission changes.");return;}
        String sql="INSERT INTO role_permission(role_id,permission_id,allowed) VALUES((SELECT id FROM roles WHERE role_name=?),?,?) ON CONFLICT(role_id,permission_id) DO UPDATE SET allowed=excluded.allowed";
        try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement(sql)){for(PermissionRow row:permissions){p.setString(1,role);p.setInt(2,row.id);p.setInt(3,row.allowed.get()?1:0);p.addBatch();}p.executeBatch();NotificationService.add(role+" permissions updated.");}
        catch(Exception e){error("Permissions could not be saved",e);}
    }
    @FXML private void resetPermissions(){loadPermissions(cmbPermissionRole.getValue());}
    @FXML private void showPermissionMatrix(){accessTabs.getSelectionModel().select(tabPermissions);if(cmbPermissionRole.getValue()==null&&!roles.isEmpty())cmbPermissionRole.setValue(roles.get(0).name.get());}
    @FXML private void manageRoles(){accessTabs.getSelectionModel().select(tabRoles);}

    @FXML private void addUser(){openUserDialog(null);} @FXML private void editSelected(){edit(table.getSelectionModel().getSelectedItem());}
    private void edit(UserRow row){if(row!=null)openUserDialog(row.id);}
    private void openUserDialog(Integer userId){
        try{FXMLLoader loader=new FXMLLoader(getClass().getResource("/fxml/pages/UserDialog.fxml"));Parent root=loader.load();UserDialogController controller=loader.getController();if(userId!=null)controller.editUser(userId);
            Stage stage=new Stage();PlatformUiSupport.configureDialogStage(stage, table, userId==null?"Add New User":"Edit User", true);Scene scene=new Scene(root);ThemeManager.applyTheme(scene);stage.setScene(scene);stage.setMinWidth(860);stage.setMinHeight(620);stage.showAndWait();refresh();}
        catch(Exception e){error("User form could not be opened",e);}
    }
    @FXML private void resetSelected(){resetPassword(table.getSelectionModel().getSelectedItem());}
    private void resetPassword(UserRow row){if(row==null){warning("Select a user first.");return;}TextInputDialog d=new OwnedTextInputDialog();d.setTitle("Reset Password");d.setHeaderText("Set a temporary password for "+row.user.get());d.setContentText("Temporary password:");d.showAndWait().map(String::trim).filter(x->x.length()>=6).ifPresent(password->{try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE users SET password=?,failed_attempts=0,locked=0 WHERE id=?")){p.setString(1,password);p.setInt(2,row.id);p.executeUpdate();audit(row.id,"PASSWORD_RESET",row.user.get());NotificationService.add("Password reset for "+row.user.get()+".");refresh();}catch(Exception e){error("Password could not be reset",e);}});}
    private void toggleLock(UserRow row){if(row==null)return;try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("UPDATE users SET locked=? WHERE id=?")){p.setInt(1,row.locked?0:1);p.setInt(2,row.id);p.executeUpdate();audit(row.id,row.locked?"USER_UNLOCKED":"USER_LOCKED",row.user.get());refresh();}catch(Exception e){error("Lock status could not be changed",e);}}
    private void deleteUser(UserRow row){if(row==null)return;if("admin".equalsIgnoreCase(row.user.get())){warning("The primary administrator cannot be deleted.");return;}if(!confirm("Delete user '"+row.user.get()+"'?"))return;try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM users WHERE id=?")){p.setInt(1,row.id);p.executeUpdate();refresh();}catch(Exception e){error("User could not be deleted",e);}}

    @FXML private void addRole(){promptRole(null);} @FXML private void editRole(){promptRole(roleTable.getSelectionModel().getSelectedItem());}
    private void promptRole(RoleRow existing){
        Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle(existing==null?"Add Role":"Edit Role");TextField name=new TextField(existing==null?"":existing.name.get());TextArea desc=new TextArea(existing==null?"":existing.description.get());desc.setPrefRowCount(3);CheckBox active=new CheckBox("Active role");active.setSelected(existing==null||existing.active);
        VBox box=new VBox(10,new Label("Role Name *"),name,new Label("Description *"),desc,active);box.getStyleClass().add("erp-role-dialog");d.getDialogPane().getProperties().put("erp-dialog-custom",true);d.getDialogPane().getStyleClass().addAll("modern-dialog","user-role-dialog");d.getDialogPane().setContent(box);ButtonType save=new ButtonType(existing==null?"Save Role":"Update Role",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(save,ButtonType.CANCEL);
        d.setResultConverter(button->{if(button!=save)return button;if(name.getText().isBlank()||desc.getText().isBlank()){warning("Role name and description are required.");return null;}return button;});
        d.showAndWait().filter(save::equals).ifPresent(b->{String role=name.getText().trim().toUpperCase(Locale.ROOT);try(Connection c=DatabaseManager.getConnection()){c.setAutoCommit(false);if(existing==null){try(PreparedStatement p=c.prepareStatement("INSERT INTO roles(role_name,description,active) VALUES(?,?,?)")){p.setString(1,role);p.setString(2,desc.getText().trim());p.setInt(3,active.isSelected()?1:0);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO role_permission(role_id,permission_id,allowed) SELECT (SELECT id FROM roles WHERE role_name=?),id,0 FROM permissions")){p.setString(1,role);p.executeUpdate();}}else{try(PreparedStatement p=c.prepareStatement("UPDATE roles SET role_name=?,description=?,active=? WHERE id=?")){p.setString(1,role);p.setString(2,desc.getText().trim());p.setInt(3,active.isSelected()?1:0);p.setInt(4,existing.id);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("UPDATE users SET role=? WHERE role_id=?")){p.setString(1,role);p.setInt(2,existing.id);p.executeUpdate();}}c.commit();refresh();cmbPermissionRole.setValue(role);}catch(Exception e){error("Role could not be saved",e);}});
    }
    @FXML private void deleteRole(){RoleRow row=roleTable.getSelectionModel().getSelectedItem();if(row==null){warning("Select a role first.");return;}if(row.name.get().equalsIgnoreCase("ADMIN")){warning("The ADMIN role cannot be deleted.");return;}if(row.users.get()>0){warning("Move users to another role before deleting this role.");return;}if(!confirm("Delete role '"+row.name.get()+"'?"))return;try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM roles WHERE id=?")){p.setInt(1,row.id);p.executeUpdate();refresh();}catch(Exception e){error("Role could not be deleted",e);}}

    private TableCell<UserRow,Void> userActionCell(){return new TableCell<>(){final MenuButton menu=createActionMenu();{menu.getStyleClass().add("user-action-menu");}protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()){setGraphic(null);return;}UserRow row=getTableView().getItems().get(getIndex());menu.getItems().setAll(mi("Edit User","edit",e->edit(row)),mi("Reset Password","lock",e->resetPassword(row)),mi(row.locked?"Unlock Account":"Lock Account",row.locked?"reopen":"lock",e->toggleLock(row)),mi("View Role Permissions","permission",e->{cmbPermissionRole.setValue(row.role.get());showPermissionMatrix();}),new SeparatorMenuItem(),mi("Delete User","delete",e->deleteUser(row)));setGraphic(menu);}};}
    private TableCell<RoleRow,Void> roleActionCell(){return new TableCell<>(){final MenuButton menu=createActionMenu();protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()){setGraphic(null);return;}RoleRow row=getTableView().getItems().get(getIndex());menu.getItems().setAll(mi("Edit Role","edit",e->{roleTable.getSelectionModel().select(row);editRole();}),mi("Manage Permissions","permission",e->{cmbPermissionRole.setValue(row.name.get());showPermissionMatrix();}),mi("Delete Role","delete",e->{roleTable.getSelectionModel().select(row);deleteRole();}));setGraphic(menu);}};}
    private MenuButton createActionMenu(){MenuButton m=new MenuButton();m.setGraphic(IconFactory.icon("actions",15));m.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);m.getStyleClass().add("table-action-menu");return m;}
    private MenuItem mi(String text,String icon,javafx.event.EventHandler<javafx.event.ActionEvent>handler){MenuItem i=new MenuItem(text,IconFactory.compactIcon(icon,16));i.setOnAction(handler);return i;}
    private void filter(){String q=txtSearch.getText()==null?"":txtSearch.getText().toLowerCase(Locale.ROOT);filtered.setPredicate(r->{boolean text=q.isBlank()||(r.user.get()+" "+r.fullName+" "+r.email.get()+" "+r.department.get()+" "+r.branch.get()).toLowerCase(Locale.ROOT).contains(q);boolean role=cmbRole.getValue()==null||cmbRole.getValue().startsWith("All")||r.role.get().equals(cmbRole.getValue());boolean status=cmbStatus.getValue()==null||cmbStatus.getValue().startsWith("All")||r.status.get().equals(cmbStatus.getValue());boolean branch=cmbBranch.getValue()==null||cmbBranch.getValue().startsWith("All")||r.branch.get().equals(cmbBranch.getValue());return text&&role&&status&&branch;});}
    private void audit(int userId,String action,String detail){try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by) VALUES('USER',?,?,?,?)")){p.setInt(1,userId);p.setString(2,action);p.setString(3,detail);p.setString(4,SessionService.current()==null?"System":SessionService.current().getUsername());p.executeUpdate();}catch(Exception ignored){}}
    private boolean confirm(String text){return new OwnedAlert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    private void warning(String message){new OwnedAlert(Alert.AlertType.WARNING,message).showAndWait();} private void error(String message,Exception e){e.printStackTrace();new OwnedAlert(Alert.AlertType.ERROR,message+".\n\n"+e.getMessage()).showAndWait();}

    private void configureIcons(){
        IconFactory.applyTableHeaderIcon(colUser,"user");IconFactory.applyTableHeaderIcon(colEmail,"email");IconFactory.applyTableHeaderIcon(colRole,"role");IconFactory.applyTableHeaderIcon(colDepartment,"category");IconFactory.applyTableHeaderIcon(colAccess,"security");IconFactory.applyTableHeaderIcon(colBranch,"location");IconFactory.applyTableHeaderIcon(colStatus,"status");IconFactory.applyTableHeaderIcon(colLastLogin,"calendar");IconFactory.applyTableHeaderIcon(colMfa,"security");IconFactory.applyTableHeaderIcon(colActions,"actions");
        IconFactory.applyTableHeaderIcon(colRoleName,"role");IconFactory.applyTableHeaderIcon(colRoleDescription,"document");IconFactory.applyTableHeaderIcon(colRoleUsers,"users");IconFactory.applyTableHeaderIcon(colRoleStatus,"status");IconFactory.applyTableHeaderIcon(colRoleActions,"actions");IconFactory.applyTableHeaderIcon(colPermissionModule,"category");IconFactory.applyTableHeaderIcon(colPermissionAction,"security");IconFactory.applyTableHeaderIcon(colPermissionDescription,"document");IconFactory.applyTableHeaderIcon(colPermissionAllowed,"complete");
    }

    public static final class UserRow{final int id;final SimpleStringProperty user,email,role,department,access,branch,status,lastLogin,mfa;final String fullName;final boolean active,locked;UserRow(ResultSet r)throws SQLException{id=r.getInt("id");user=new SimpleStringProperty(r.getString("username"));fullName=blank(r.getString("full_name"),r.getString("username"));email=new SimpleStringProperty(blank(r.getString("email"),"—"));role=new SimpleStringProperty(blank(r.getString("resolved_role"),"USER"));department=new SimpleStringProperty(blank(r.getString("department"),"—"));access=new SimpleStringProperty(blank(r.getString("access_level"),"STANDARD"));branch=new SimpleStringProperty(blank(r.getString("branch"),"—"));active=r.getInt("active")==1;locked=r.getInt("locked")==1;status=new SimpleStringProperty(locked?"Locked":active?"Active":"Inactive");lastLogin=new SimpleStringProperty(blank(r.getString("last_login"),"Never"));mfa=new SimpleStringProperty(r.getInt("mfa_enabled")==1?"Enabled":"—");}}
    public static final class RoleRow{final int id;final SimpleStringProperty name,description,status;final SimpleIntegerProperty users;final boolean active;RoleRow(ResultSet r)throws SQLException{id=r.getInt("id");name=new SimpleStringProperty(r.getString("role_name"));description=new SimpleStringProperty(blank(r.getString("description"),"No description"));users=new SimpleIntegerProperty(r.getInt("user_count"));active=r.getInt("active")==1;status=new SimpleStringProperty(active?"Active":"Inactive");}}
    public static final class PermissionRow{final int id;final SimpleStringProperty module,action,description;final SimpleBooleanProperty allowed;PermissionRow(ResultSet r,boolean admin)throws SQLException{id=r.getInt("id");module=new SimpleStringProperty(r.getString("module_name"));action=new SimpleStringProperty(r.getString("action_name"));description=new SimpleStringProperty(blank(r.getString("description"),"—"));allowed=new SimpleBooleanProperty(admin||r.getInt("allowed")==1);}}
    private static String blank(String v,String fallback){return v==null||v.isBlank()?fallback:v;}

    private void installKpiIcons(){
        setKpiIcon(iconTotalUsers,"users");setKpiIcon(iconActiveUsers,"complete");setKpiIcon(iconRoles,"role");setKpiIcon(iconLocked,"lock");setKpiIcon(iconLogins,"login");
    }
    private void setKpiIcon(Label label,String semantic){if(label!=null){label.setText("");label.setGraphic(IconFactory.compactIcon(semantic,24));label.getProperties().put("erp-icon-preserve",true);}}
}
