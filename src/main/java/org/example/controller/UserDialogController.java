package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.database.DatabaseManager;
import org.example.service.NotificationService;
import org.example.util.IconFactory;

import java.sql.*;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared premium Add/Edit User form backed by the existing users and roles tables. */
public class UserDialogController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML private Label lblTitle, lblSubtitle, lblMessage;
    @FXML private TextField txtFullName, txtUsername, txtEmail, txtDepartment, txtBranch;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private ComboBox<String> cmbRole, cmbAccess;
    @FXML private CheckBox chkActive, chkLocked, chkMfa;
    @FXML private Button btnSave, btnCancel;

    private Integer editingUserId;
    private String originalUsername;

    @FXML public void initialize() {
        loadRoles();
        cmbAccess.getItems().setAll("FULL ACCESS", "STANDARD", "LIMITED ACCESS", "READ ONLY");
        cmbAccess.setValue("STANDARD");
        chkActive.setSelected(true);
        btnSave.setGraphic(IconFactory.icon("save", 16));
        btnCancel.setGraphic(IconFactory.icon("cancel", 16));
        installLiveValidation();
    }

    public void editUser(int userId) {
        editingUserId = userId;
        lblTitle.setText("Edit User Account");
        lblSubtitle.setText("Update identity, role, access and account security");
        btnSave.setText("Update User");
        txtPassword.setPromptText("Leave blank to keep current password");
        txtConfirm.setPromptText("Confirm new password");
        String sql = "SELECT u.*,COALESCE(r.role_name,u.role,'SALES') resolved_role FROM users u LEFT JOIN roles r ON r.id=u.role_id WHERE u.id=?";
        try (Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setInt(1,userId);
            try (ResultSet r=p.executeQuery()) {
                if (!r.next()) throw new SQLException("User record was not found.");
                originalUsername = r.getString("username");
                txtFullName.setText(nvl(r.getString("full_name")));
                txtUsername.setText(nvl(r.getString("username")));
                txtEmail.setText(nvl(r.getString("email")));
                txtDepartment.setText(nvl(r.getString("department")));
                txtBranch.setText(nvl(r.getString("branch")));
                cmbRole.setValue(nvl(r.getString("resolved_role")));
                cmbAccess.setValue(blank(r.getString("access_level"),"STANDARD"));
                chkActive.setSelected(r.getInt("active")==1);
                chkLocked.setSelected(r.getInt("locked")==1);
                chkMfa.setSelected(r.getInt("mfa_enabled")==1);
            }
        } catch (Exception e) {
            message("Unable to load user: "+e.getMessage(), true);
        }
    }

    private void loadRoles() {
        cmbRole.getItems().clear();
        try (Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement("SELECT role_name FROM roles WHERE active=1 ORDER BY role_name"); ResultSet r=p.executeQuery()) {
            while(r.next()) cmbRole.getItems().add(r.getString(1));
        } catch(Exception e){ message("Unable to load roles: "+e.getMessage(),true); }
        if(cmbRole.getItems().contains("SALES")) cmbRole.setValue("SALES");
        else if(!cmbRole.getItems().isEmpty()) cmbRole.getSelectionModel().selectFirst();
    }

    @FXML private void save() {
        clearInvalid();
        if (!validateForm()) return;
        if (editingUserId == null) insertUser(); else updateUser();
    }

    private boolean validateForm() {
        if (blank(txtFullName.getText())) return invalid(txtFullName,"Full name is required.");
        if (blank(txtUsername.getText())) return invalid(txtUsername,"Username is required.");
        if (blank(txtEmail.getText())) return invalid(txtEmail,"Email address is required.");
        if (!EMAIL.matcher(txtEmail.getText().trim()).matches()) return invalid(txtEmail,"Enter a valid email address.");
        if (cmbRole.getValue()==null) return invalid(cmbRole,"Select a role.");
        if (cmbAccess.getValue()==null) return invalid(cmbAccess,"Select an access level.");
        boolean passwordRequired = editingUserId == null;
        if (passwordRequired && blank(txtPassword.getText())) return invalid(txtPassword,"Password is required.");
        if (!blank(txtPassword.getText()) && txtPassword.getText().length()<6) return invalid(txtPassword,"Password must contain at least 6 characters.");
        if (!txtPassword.getText().equals(txtConfirm.getText())) return invalid(txtConfirm,"Passwords do not match.");
        return true;
    }

    private void insertUser() {
        String sql="INSERT INTO users(username,password,full_name,role,role_id,email,active,locked,mfa_enabled,department,branch,access_level) VALUES(?,?,?,?,(SELECT id FROM roles WHERE role_name=?),?,?,?,?,?,?,?)";
        try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement(sql)){
            bindCommon(p, false);
            p.executeUpdate();
            NotificationService.add("User "+txtUsername.getText().trim()+" created with "+cmbRole.getValue()+" access.");
            close();
        } catch(SQLException e){
            if (e.getMessage()!=null && e.getMessage().toLowerCase(Locale.ROOT).contains("unique")) invalid(txtUsername,"This username already exists.");
            else message("Unable to create user: "+e.getMessage(),true);
        }
    }

    private void updateUser() {
        boolean updatePassword=!blank(txtPassword.getText());
        String sql="UPDATE users SET username=?,full_name=?,email=?,role=?,role_id=(SELECT id FROM roles WHERE role_name=?),active=?,locked=?,mfa_enabled=?,department=?,branch=?,access_level=?"+(updatePassword?",password=?":"")+" WHERE id=?";
        try(Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement(sql)){
            int i=1;
            p.setString(i++,txtUsername.getText().trim());
            p.setString(i++,txtFullName.getText().trim());
            p.setString(i++,txtEmail.getText().trim());
            p.setString(i++,cmbRole.getValue()); p.setString(i++,cmbRole.getValue());
            p.setInt(i++,chkActive.isSelected()?1:0); p.setInt(i++,chkLocked.isSelected()?1:0); p.setInt(i++,chkMfa.isSelected()?1:0);
            p.setString(i++,txtDepartment.getText().trim()); p.setString(i++,txtBranch.getText().trim()); p.setString(i++,cmbAccess.getValue());
            if(updatePassword) p.setString(i++,txtPassword.getText());
            p.setInt(i,editingUserId);
            p.executeUpdate();
            NotificationService.add("User "+originalUsername+" updated.");
            close();
        } catch(SQLException e){
            if (e.getMessage()!=null && e.getMessage().toLowerCase(Locale.ROOT).contains("unique")) invalid(txtUsername,"This username already exists.");
            else message("Unable to update user: "+e.getMessage(),true);
        }
    }

    private void bindCommon(PreparedStatement p, boolean ignored) throws SQLException {
        p.setString(1,txtUsername.getText().trim()); p.setString(2,txtPassword.getText()); p.setString(3,txtFullName.getText().trim());
        p.setString(4,cmbRole.getValue()); p.setString(5,cmbRole.getValue()); p.setString(6,txtEmail.getText().trim());
        p.setInt(7,chkActive.isSelected()?1:0); p.setInt(8,chkLocked.isSelected()?1:0); p.setInt(9,chkMfa.isSelected()?1:0);
        p.setString(10,txtDepartment.getText().trim()); p.setString(11,txtBranch.getText().trim()); p.setString(12,cmbAccess.getValue());
    }

    private void installLiveValidation(){
        txtFullName.textProperty().addListener((o,a,b)->clearInvalid(txtFullName)); txtUsername.textProperty().addListener((o,a,b)->clearInvalid(txtUsername));
        txtEmail.textProperty().addListener((o,a,b)->clearInvalid(txtEmail)); txtPassword.textProperty().addListener((o,a,b)->clearInvalid(txtPassword));
        txtConfirm.textProperty().addListener((o,a,b)->clearInvalid(txtConfirm)); cmbRole.valueProperty().addListener((o,a,b)->clearInvalid(cmbRole));
        cmbAccess.valueProperty().addListener((o,a,b)->clearInvalid(cmbAccess));
    }
    private boolean invalid(Control c,String text){ c.getStyleClass().add("validation-error"); c.requestFocus(); message(text,true); return false; }
    private void clearInvalid(){ for(Control c:new Control[]{txtFullName,txtUsername,txtEmail,txtPassword,txtConfirm,cmbRole,cmbAccess}) clearInvalid(c); lblMessage.setText(""); }
    private void clearInvalid(Control c){ c.getStyleClass().remove("validation-error"); }
    @FXML private void cancel(){close();}
    private void close(){((Stage)txtUsername.getScene().getWindow()).close();}
    private void message(String text,boolean error){lblMessage.setText(text);lblMessage.getStyleClass().setAll(error?"dialog-error":"dialog-success");}
    private static boolean blank(String v){return v==null||v.isBlank();} private static String nvl(String v){return v==null?"":v;} private static String blank(String v,String f){return blank(v)?f:v;}
}
