package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.util.OwnedAlert;
import javafx.scene.control.cell.CheckBoxTableCell;
import org.example.api.admin.AdminApiClient;
import org.example.util.IconFactory;
import org.example.service.PermissionService;

import java.util.*;

public class PermissionMatrixController {
    private static String requestedRole;
    public static void requestRole(String role){ requestedRole=role; }

    @FXML private ComboBox<String> cmbRole,cmbModule;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row,String> colModule,colAction,colDescription;
    @FXML private TableColumn<Row,Boolean> colAllowed;
    @FXML private Label lblGranted,lblDenied,lblModules,lblHint;
    @FXML private Button btnBack,btnSaveTop,btnGrantAll,btnRevokeAll,btnReset,btnSaveBottom;

    private final ObservableList<Row> rows=FXCollections.observableArrayList();
    private final FilteredList<Row> visibleRows=new FilteredList<>(rows, row->true);
    private final AdminApiClient api=new AdminApiClient();

    @FXML private void initialize(){
        colModule.setCellValueFactory(v->v.getValue().module);
        colAction.setCellValueFactory(v->v.getValue().action);
        colDescription.setCellValueFactory(v->v.getValue().description);
        colAllowed.setCellValueFactory(v->v.getValue().allowed);
        colAllowed.setCellFactory(CheckBoxTableCell.forTableColumn(colAllowed));
        IconFactory.applyTableHeaderIcon(colModule,"master"); IconFactory.applyTableHeaderIcon(colAction,"permission");
        IconFactory.applyTableHeaderIcon(colDescription,"notes"); IconFactory.applyTableHeaderIcon(colAllowed,"complete");
        if(btnBack!=null)btnBack.setGraphic(IconFactory.compactIcon("previous",16));
        if(btnSaveTop!=null)btnSaveTop.setGraphic(IconFactory.compactIcon("save",16));
        if(btnSaveBottom!=null)btnSaveBottom.setGraphic(IconFactory.compactIcon("save",16));
        if(btnGrantAll!=null)btnGrantAll.setGraphic(IconFactory.compactIcon("complete",16));
        if(btnRevokeAll!=null)btnRevokeAll.setGraphic(IconFactory.compactIcon("cancel",16));
        if(btnReset!=null)btnReset.setGraphic(IconFactory.compactIcon("reset",16));
        table.setEditable(true); colAllowed.setEditable(true); table.setItems(visibleRows);
        loadRoles();
        cmbRole.valueProperty().addListener((o,a,b)->load(b));
        cmbModule.valueProperty().addListener((o,a,b)->applyModuleFilter());
        if(requestedRole!=null&&cmbRole.getItems().contains(requestedRole))cmbRole.setValue(requestedRole);
        else if(!cmbRole.getItems().isEmpty())cmbRole.getSelectionModel().selectFirst();
        requestedRole=null;
    }

    private void loadRoles(){ try{cmbRole.getItems().setAll(api.roles().stream().filter(AdminApiClient.RoleDto::active).map(AdminApiClient.RoleDto::name).toList());}catch(Exception ignored){} }

    private void load(String role){
        rows.clear();
        if(role==null)return;
        try{ for(var p:api.permissions(role)) rows.add(new Row(p.id(),displayModule(p.module()),displayAction(p.action()),p.description(),p.allowed())); }catch(Exception e){ showError(e); }
        List<String> modules=rows.stream().map(x->x.module.get()).distinct().sorted().toList();
        cmbModule.getItems().setAll("All Modules"); cmbModule.getItems().addAll(modules); cmbModule.setValue("All Modules");
        boolean admin="ADMIN".equalsIgnoreCase(role);
        setEditingEnabled(!admin);
        lblHint.setText(admin?"Administrator receives full access by protected system policy.":"Configure granular access for "+role+". Use the module filter to focus one workflow.");
        lblModules.setText(String.valueOf(modules.size()));
        applyModuleFilter(); update();
    }

    private void setEditingEnabled(boolean enabled){
        colAllowed.setEditable(enabled);
        btnGrantAll.setDisable(!enabled); btnRevokeAll.setDisable(!enabled); btnSaveTop.setDisable(!enabled); btnSaveBottom.setDisable(!enabled);
    }

    private void applyModuleFilter(){
        String module=cmbModule==null?null:cmbModule.getValue();
        visibleRows.setPredicate(row->module==null||"All Modules".equals(module)||module.equals(row.module.get()));
        update();
    }

    @FXML private void save(){
        String role=cmbRole.getValue(); if(role==null||role.equalsIgnoreCase("ADMIN"))return;
        try{api.savePermissions(role,rows.stream().map(x->new AdminApiClient.PermissionSave(x.id,x.allowed.get())).toList()); PermissionService.refresh(); lblHint.setText("Permissions saved for "+role+".");}
        catch(Exception e){showError(e);} update();
    }
    @FXML private void reset(){load(cmbRole.getValue());}
    @FXML private void grantAll(){if(!"ADMIN".equalsIgnoreCase(cmbRole.getValue()))visibleRows.forEach(x->x.allowed.set(true));update();}
    @FXML private void revokeAll(){if(!"ADMIN".equalsIgnoreCase(cmbRole.getValue()))visibleRows.forEach(x->x.allowed.set(false));update();}
    @FXML private void back(){DashboardController.navigateFromChildPage("Role Management","/fxml/pages/RoleManagement.fxml");}
    private void update(){long g=visibleRows.stream().filter(x->x.allowed.get()).count();lblGranted.setText(String.valueOf(g));lblDenied.setText(String.valueOf(visibleRows.size()-g));}
    private void showError(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}
    private static String displayModule(String v){return v==null?"":v.replace('_',' ').toUpperCase(Locale.ROOT);}
    private static String displayAction(String v){return v==null?"":Arrays.stream(v.toLowerCase(Locale.ROOT).split("_")).map(x->x.isBlank()?x:Character.toUpperCase(x.charAt(0))+x.substring(1)).reduce((a,b)->a+" "+b).orElse("");}
    public static class Row{final long id;final StringProperty module=new SimpleStringProperty(),action=new SimpleStringProperty(),description=new SimpleStringProperty();final BooleanProperty allowed=new SimpleBooleanProperty();Row(long i,String m,String a,String d,boolean x){id=i;module.set(m);action.set(a);description.set(d==null?"":d);allowed.set(x);}}
}
