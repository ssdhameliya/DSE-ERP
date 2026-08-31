package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.api.admin.AdminApiClient;
import org.example.navigation.ScreenLifecycle;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.UiTaskExecutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoleManagementController implements ScreenLifecycle {
    @FXML private TableView<RoleRow> roleTable;
    @FXML private TableColumn<RoleRow,String> colName,colDescription,colStatus;
    @FXML private TableColumn<RoleRow,Number> colUsers;
    @FXML private Label lblRoles,lblAssigned,lblCustom,lblRegistrationRoleStatus;
    @FXML private ComboBox<String> cmbRegistrationRole;
    @FXML private Button btnSaveRegistrationRole;

    private final ObservableList<RoleRow> rows=FXCollections.observableArrayList();
    private final AdminApiClient api=new AdminApiClient();
    private final Map<String,String> registrationRoleCodes=new LinkedHashMap<>();

    @FXML private void initialize(){
        colName.setCellValueFactory(v->v.getValue().name);
        colDescription.setCellValueFactory(v->v.getValue().description);
        colUsers.setCellValueFactory(v->v.getValue().users);
        colStatus.setCellValueFactory(v->v.getValue().status);
        roleTable.setItems(rows);
        if(btnSaveRegistrationRole!=null){
            btnSaveRegistrationRole.setGraphic(IconFactory.compactIcon("save",15));
            btnSaveRegistrationRole.setContentDisplay(ContentDisplay.LEFT);
            btnSaveRegistrationRole.setGraphicTextGap(6);
        }
        refresh();
    }

    @FXML private void refresh(){
        UiTaskExecutor.submitLatest(
                "role-management-load",
                ()->new RoleSnapshot(api.roles(),api.registrationRole()),
                this::applySnapshot,
                failure->alert(Alert.AlertType.ERROR,"Roles could not be loaded",message(failure))
        );
    }

    private void applySnapshot(RoleSnapshot snapshot){
        List<AdminApiClient.RoleDto> roles=snapshot==null||snapshot.roles()==null?List.of():snapshot.roles();
        rows.clear();
        registrationRoleCodes.clear();
        if(cmbRegistrationRole!=null)cmbRegistrationRole.getItems().clear();
        for(var r:roles){
            rows.add(new RoleRow(r.id(),r.code(),r.displayName(),r.description(),(int)r.userCount(),r.active()));
            if(r.active()&&!"ADMIN".equalsIgnoreCase(r.code())){
                String display=displayRole(r.displayName(),r.code());
                registrationRoleCodes.put(display,r.code()==null?"":r.code().trim().toUpperCase(Locale.ROOT));
                if(cmbRegistrationRole!=null)cmbRegistrationRole.getItems().add(display);
            }
        }
        lblRoles.setText(String.valueOf(rows.size()));
        lblAssigned.setText(String.valueOf(rows.stream().mapToInt(x->x.users.get()).sum()));
        lblCustom.setText(String.valueOf(rows.stream().filter(x->!"ADMIN".equalsIgnoreCase(x.code)).count()));

        AdminApiClient.RegistrationRoleDto current=snapshot==null?null:snapshot.registrationRole();
        String currentCode=current==null||current.code()==null?"":current.code().trim().toUpperCase(Locale.ROOT);
        if(cmbRegistrationRole!=null){
            registrationRoleCodes.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(currentCode)).map(Map.Entry::getKey).findFirst()
                    .ifPresent(cmbRegistrationRole::setValue);
        }
        if(lblRegistrationRoleStatus!=null){
            String name=current==null?"Not configured":displayRole(current.displayName(),current.code());
            lblRegistrationRoleStatus.setText("Current: "+name+" • New self-registered users receive only this role's saved permissions.");
        }
    }

    @FXML private void saveRegistrationRole(){
        String display=cmbRegistrationRole==null?null:cmbRegistrationRole.getValue();
        String code=display==null?null:registrationRoleCodes.get(display);
        if(code==null||code.isBlank()){
            alert(Alert.AlertType.WARNING,"Select a registration role","Choose an active non-Admin role from Role Master.");
            return;
        }
        UiTaskExecutor.submitLatest(
                "role-management-registration-role-save",
                ()->api.setRegistrationRole(code),
                saved->{
                    if(lblRegistrationRoleStatus!=null)lblRegistrationRoleStatus.setText("Current: "+displayRole(saved.displayName(),saved.code())+" • Saved for public registration.");
                    alert(Alert.AlertType.INFORMATION,"Registration role updated","New public registrations will use "+displayRole(saved.displayName(),saved.code())+". Existing users are unchanged.");
                },
                failure->alert(Alert.AlertType.ERROR,"Registration role was not saved",message(failure))
        );
    }

    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("role-management-");}
    @FXML private void addRole(){openRoleMaster();}
    @FXML private void editRole(){openRoleMaster();}
    @FXML private void deleteRole(){openRoleMaster();}
    private void openRoleMaster(){MasterDataController.requestCategory("ROLE");DashboardController.navigateFromChildPage("Master Data","/fxml/pages/Masterdata.fxml");}
    @FXML private void openPermissions(){RoleRow x=roleTable.getSelectionModel().getSelectedItem();PermissionMatrixController.requestRole(x==null?null:x.code);DashboardController.navigateFromChildPage("Permission Matrix","/fxml/pages/PermissionMatrix.fxml");}
    @FXML private void back(){DashboardController.navigateFromChildPage("User Access & Permissions","/fxml/pages/UserAccess.fxml");}

    private void alert(Alert.AlertType type,String header,String content){Alert a=new OwnedAlert(type);a.setHeaderText(header);a.setContentText(content);a.showAndWait();}
    private static String message(Throwable failure){Throwable c=failure;while(c!=null&&(c.getMessage()==null||c.getMessage().isBlank())&&c.getCause()!=c)c=c.getCause();return c==null||c.getMessage()==null?"Unexpected error.":c.getMessage();}
    private static String displayRole(String display,String code){String d=display==null?"":display.trim();String c=code==null?"":code.trim();return d.isBlank()?c:d;}

    private record RoleSnapshot(List<AdminApiClient.RoleDto> roles,AdminApiClient.RegistrationRoleDto registrationRole){}
    public static class RoleRow{final long id;final String code;final StringProperty name=new SimpleStringProperty(),description=new SimpleStringProperty(),status=new SimpleStringProperty();final IntegerProperty users=new SimpleIntegerProperty();RoleRow(long i,String c,String n,String d,int u,boolean a){id=i;code=c==null?"":c;name.set(n==null||n.isBlank()?code:n);description.set(d==null?"":d);users.set(u);status.set(a?"Active":"Inactive");}}
}
