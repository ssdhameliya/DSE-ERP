package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.example.api.admin.AdminApiClient;
import org.example.navigation.ScreenLifecycle;
import org.example.util.*;

import java.util.List;

public class RegistrationApprovalsController implements ScreenLifecycle {
    @FXML private StackPane pageIcon;
    @FXML private Label lblPending,lblVerified,lblMessage;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row,String> colUser,colEmail,colRole,colEmailVerified,colMfa,colRequested,colStatus;
    @FXML private ComboBox<AdminApiClient.RoleDto> cmbRole;
    @FXML private Button btnRefresh,btnApprove,btnReject;

    private final AdminApiClient api=new AdminApiClient();
    private final ObservableList<Row> rows=FXCollections.observableArrayList();

    @FXML public void initialize(){
        if(pageIcon!=null)pageIcon.getChildren().setAll(IconFactory.icon("security",24));
        colUser.setCellValueFactory(v->v.getValue().user);
        colEmail.setCellValueFactory(v->v.getValue().email);
        colRole.setCellValueFactory(v->v.getValue().role);
        colEmailVerified.setCellValueFactory(v->v.getValue().emailVerified);
        colMfa.setCellValueFactory(v->v.getValue().mfa);
        colRequested.setCellValueFactory(v->v.getValue().requested);
        colStatus.setCellValueFactory(v->v.getValue().status);
        colEmailVerified.setCellFactory(c->SemanticTableCells.status("status"));
        colMfa.setCellFactory(c->SemanticTableCells.status("status"));
        colStatus.setCellFactory(c->SemanticTableCells.status("status"));
        table.setItems(rows);
        DynamicTableLayoutManager.install(table);
        UiActionIcons.apply(btnRefresh,"refresh","Refresh pending registrations");
        UiActionIcons.apply(btnApprove,"complete","Approve selected registration");
        UiActionIcons.apply(btnReject,"delete","Reject selected registration");
        refresh();
    }

    @FXML private void refresh(){
        AdminApiClient.RoleDto selected=cmbRole.getValue();
        if(btnRefresh!=null)btnRefresh.setDisable(true);
        UiTaskExecutor.submitLatest("registration-approvals-refresh",
                ()->new ApprovalSnapshot(api.registrations(),api.roles()),
                snapshot->{
                    rows.setAll(snapshot.pending().stream().map(Row::new).toList());
                    var roles=snapshot.roles().stream().filter(r->r.active()&&!"ADMIN".equalsIgnoreCase(r.code())).toList();
                    cmbRole.getItems().setAll(roles);
                    if(selected!=null&&roles.stream().anyMatch(r->r.code().equalsIgnoreCase(selected.code())))
                        cmbRole.getSelectionModel().select(roles.stream().filter(r->r.code().equalsIgnoreCase(selected.code())).findFirst().orElse(null));
                    lblPending.setText(String.valueOf(rows.size()));
                    lblVerified.setText(String.valueOf(rows.stream().filter(r->"Verified".equals(r.mfa.get())).count()));
                    if(btnRefresh!=null)btnRefresh.setDisable(false);
                    message("Pending registration queue refreshed.",false);
                },
                failure->{if(btnRefresh!=null)btnRefresh.setDisable(false);message(failure.getMessage(),true);});
    }

    @FXML private void approve(){
        Row r=table.getSelectionModel().getSelectedItem();
        if(r==null){message("Select a pending registration first.",true);return;}
        AdminApiClient.RoleDto role=cmbRole.getValue();
        String code=role==null?r.source.requestedRole():role.code();
        if("ADMIN".equalsIgnoreCase(code)){message("Administrator accounts cannot be approved from self-registration.",true);return;}
        var confirm=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Approve "+r.source.fullName()+" as "+code+"?",ButtonType.YES,ButtonType.NO);
        if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
        message("Approving registration...",false);
        UiTaskExecutor.submitAction("registration-approve", () -> {
            api.approveRegistration(r.source.id(),code,r.source.rowVersion());
            return true;
        }, success -> {
            refresh();
            message("Registration approved. The user may now sign in with password + authenticator.",false);
        }, e -> message(e.getMessage() == null ? "Unable to approve registration" : e.getMessage(), true));
    }

    @FXML private void reject(){
        Row r=table.getSelectionModel().getSelectedItem();
        if(r==null){message("Select a pending registration first.",true);return;}
        OwnedTextInputDialog dialog=new OwnedTextInputDialog("");
        dialog.setTitle("Reject Registration");
        dialog.setHeaderText("Reason for rejecting "+r.source.fullName());
        String reason=dialog.showAndWait().orElse(null);
        if(reason==null)return;
        message("Rejecting registration...",false);
        UiTaskExecutor.submitAction("registration-reject", () -> {
            api.rejectRegistration(r.source.id(),reason,r.source.rowVersion());
            return true;
        }, success -> {
            refresh();
            message("Registration rejected.",false);
        }, e -> message(e.getMessage() == null ? "Unable to reject registration" : e.getMessage(), true));
    }

    @FXML private void back(){DashboardController.navigateFromChildPage("User Access & Permissions","/fxml/pages/UserAccess.fxml");}
    @Override public void onScreenHidden(){UiTaskExecutor.cancel("registration-approvals-refresh");}

    private void message(String text,boolean error){lblMessage.setText(text==null?"":text);lblMessage.getStyleClass().removeAll("message-error","message-success");lblMessage.getStyleClass().add(error?"message-error":"message-success");}
    private record ApprovalSnapshot(List<AdminApiClient.RegistrationRequestDto> pending,List<AdminApiClient.RoleDto> roles){}
    public static final class Row{final AdminApiClient.RegistrationRequestDto source;final StringProperty user,email,role,emailVerified,mfa,requested,status;Row(AdminApiClient.RegistrationRequestDto d){source=d;user=new SimpleStringProperty(d.fullName()+" ("+d.username()+")");email=new SimpleStringProperty(d.email());role=new SimpleStringProperty(d.requestedRole());emailVerified=new SimpleStringProperty(d.emailVerified()?"Verified":"Pending");mfa=new SimpleStringProperty(d.mfaVerified()?"Verified":"Pending");requested=new SimpleStringProperty(d.requestedAt());status=new SimpleStringProperty(d.status());}}
}
