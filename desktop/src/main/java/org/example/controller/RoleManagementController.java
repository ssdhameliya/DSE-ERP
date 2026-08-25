package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.api.admin.AdminApiClient;
import org.example.navigation.ScreenLifecycle;
import org.example.util.OwnedAlert;
import org.example.util.UiTaskExecutor;

import java.util.List;

public class RoleManagementController implements ScreenLifecycle {
    @FXML private TableView<RoleRow> roleTable;
    @FXML private TableColumn<RoleRow,String> colName,colDescription,colStatus;
    @FXML private TableColumn<RoleRow,Number> colUsers;
    @FXML private Label lblRoles,lblAssigned,lblCustom;
    private final ObservableList<RoleRow> rows=FXCollections.observableArrayList();
    private final AdminApiClient api=new AdminApiClient();

    @FXML private void initialize(){
        colName.setCellValueFactory(v->v.getValue().name);
        colDescription.setCellValueFactory(v->v.getValue().description);
        colUsers.setCellValueFactory(v->v.getValue().users);
        colStatus.setCellValueFactory(v->v.getValue().status);
        roleTable.setItems(rows);
        refresh();
    }

    @FXML private void refresh(){
        UiTaskExecutor.submitLatest(
                "role-management-load",
                api::roles,
                this::applyRoles,
                failure->alert("Roles could not be loaded",message(failure))
        );
    }

    private void applyRoles(List<AdminApiClient.RoleDto> roles){
        rows.clear();
        if(roles!=null) for(var r:roles) rows.add(new RoleRow(r.id(),r.code(),r.displayName(),r.description(),(int)r.userCount(),r.active()));
        lblRoles.setText(String.valueOf(rows.size()));
        lblAssigned.setText(String.valueOf(rows.stream().mapToInt(x->x.users.get()).sum()));
        lblCustom.setText(String.valueOf(rows.size()));
    }

    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("role-management-");}
    @FXML private void addRole(){openRoleMaster();}
    @FXML private void editRole(){openRoleMaster();}
    @FXML private void deleteRole(){openRoleMaster();}
    private void openRoleMaster(){MasterDataController.requestCategory("ROLE");DashboardController.navigateFromChildPage("Master Data","/fxml/pages/Masterdata.fxml");}
    @FXML private void openPermissions(){RoleRow x=roleTable.getSelectionModel().getSelectedItem();PermissionMatrixController.requestRole(x==null?null:x.code);DashboardController.navigateFromChildPage("Permission Matrix","/fxml/pages/PermissionMatrix.fxml");}
    @FXML private void back(){DashboardController.navigateFromChildPage("User Access & Permissions","/fxml/pages/UserAccess.fxml");}
    private void alert(String h,String c){Alert a=new OwnedAlert(Alert.AlertType.ERROR);a.setHeaderText(h);a.setContentText(c);a.showAndWait();}
    private static String message(Throwable failure){Throwable c=failure;while(c!=null&&(c.getMessage()==null||c.getMessage().isBlank())&&c.getCause()!=c)c=c.getCause();return c==null||c.getMessage()==null?"Unexpected error.":c.getMessage();}
    public static class RoleRow{final long id;final String code;final StringProperty name=new SimpleStringProperty(),description=new SimpleStringProperty(),status=new SimpleStringProperty();final IntegerProperty users=new SimpleIntegerProperty();RoleRow(long i,String c,String n,String d,int u,boolean a){id=i;code=c==null?"":c;name.set(n==null||n.isBlank()?code:n);description.set(d==null?"":d);users.set(u);status.set(a?"Active":"Inactive");}}
}
