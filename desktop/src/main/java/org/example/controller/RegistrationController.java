package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.example.model.AppUser;
import org.example.api.auth.AuthApiClient;
import org.example.service.UserService;
import org.example.service.BrandingService;
import org.example.service.BrandImagePresenter;
import org.example.util.ClockService;
import org.example.util.IconFactory;
import org.example.util.SceneManager;
import org.example.update.BuildInfo;

import java.util.regex.Pattern;

public class RegistrationController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    @FXML private ImageView imgBrandLogo;
    @FXML private StackPane brandLogoBox;
    @FXML private Label lblBrandMark, lblBrandName, lblBrandTagline, lblBrandDescription, lblVersion;
    @FXML private Label lblClock, lblMessage, lblNameError, lblUsernameError, lblEmailError, lblRoleError, lblPasswordError, lblConfirmError, lblOtpError;
    @FXML private TextField txtName, txtUsername, txtEmail, txtOtp;
    @FXML private ComboBox<AuthApiClient.RoleOption> cmbRole;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private Button btnSendOtp, btnCreate, btnBack;
    private final UserService users = new UserService();
    private AppUser pending;
    private String challengeId;

    @FXML public void initialize() {
        BrandImagePresenter.applicationBanner(imgBrandLogo, brandLogoBox); applyBranding();
        if (lblVersion != null) lblVersion.setText("Version " + BuildInfo.version());
        btnSendOtp.setGraphic(IconFactory.icon("email")); btnCreate.setGraphic(IconFactory.icon("add")); btnBack.setGraphic(IconFactory.icon("return"));
        bindClear(txtName,lblNameError); bindClear(txtUsername,lblUsernameError); bindClear(txtEmail,lblEmailError); bindClear(txtPassword,lblPasswordError); bindClear(txtConfirm,lblConfirmError); bindClear(txtOtp,lblOtpError);
        cmbRole.valueProperty().addListener((o,a,b) -> { if (b != null) clearField(cmbRole, lblRoleError); invalidateChallenge(); });
        txtName.textProperty().addListener((o,a,b)->invalidateChallenge());
        txtUsername.textProperty().addListener((o,a,b)->invalidateChallenge());
        txtEmail.textProperty().addListener((o,a,b)->invalidateChallenge());
        txtPassword.textProperty().addListener((o,a,b)->invalidateChallenge());
        txtConfirm.textProperty().addListener((o,a,b)->invalidateChallenge());
        try {
            cmbRole.getItems().setAll(users.registrationRoles());
            cmbRole.getItems().stream().filter(r -> "SALES".equalsIgnoreCase(r.code())).findFirst().ifPresent(cmbRole::setValue);
        } catch (Exception e) {
            message("Unable to load account roles: " + e.getMessage(), true);
        }
    }

    private void applyBranding(){
        lblBrandName.setText(BrandingService.applicationName()); lblBrandTagline.setText(BrandingService.tagline()); lblBrandDescription.setText(BrandingService.loginDescription());
        Image logo=BrandingService.applicationBrandImage(); if(logo!=null&&!logo.isError()){imgBrandLogo.setImage(logo);imgBrandLogo.setManaged(true);imgBrandLogo.setVisible(true);lblBrandMark.setManaged(false);lblBrandMark.setVisible(false);}
    }

    @FXML private void sendOtp() {
        if (!validateAccountFields()) { message("Please correct the highlighted fields.", true); return; }
        pending = new AppUser(); pending.setFullName(txtName.getText().trim()); pending.setUsername(txtUsername.getText().trim()); pending.setEmail(txtEmail.getText().trim()); pending.setPassword(txtPassword.getText()); pending.setRole(cmbRole.getValue().code());
        try { var challenge=users.requestRegistrationOtp(pending); challengeId=challenge.challengeId(); message(challenge.message()+". Enter it to create your account.", false); txtOtp.requestFocus(); }
        catch(Exception e){ message(e.getMessage(), true); }
    }

    @FXML private void register() {
        clearField(txtOtp,lblOtpError);
        if (pending == null || challengeId == null) { message("Send the OTP first.", true); return; }
        if (txtOtp.getText()==null || txtOtp.getText().trim().isEmpty()) { error(txtOtp,lblOtpError,"Verification code is required."); message("Please enter the verification code.",true); return; }
        try { users.completeRegistration(pending,challengeId,txtOtp.getText().trim()); challengeId=null; message("Registration complete. You can now sign in as " + pending.getRole() + ".", false); }
        catch(Exception e){ message(e.getMessage(), true); }
    }

    private boolean validateAccountFields() {
        boolean ok=true;
        clearField(txtName,lblNameError); clearField(txtUsername,lblUsernameError); clearField(txtEmail,lblEmailError); clearField(cmbRole,lblRoleError); clearField(txtPassword,lblPasswordError); clearField(txtConfirm,lblConfirmError);
        if(blank(txtName)){error(txtName,lblNameError,"Full name is required.");ok=false;}
        if(blank(txtUsername)){error(txtUsername,lblUsernameError,"Username is required.");ok=false;}
        if(blank(txtEmail)){error(txtEmail,lblEmailError,"Email address is required.");ok=false;} else if(!EMAIL.matcher(txtEmail.getText().trim()).matches()){error(txtEmail,lblEmailError,"Enter a valid email address.");ok=false;}
        if(cmbRole.getValue()==null){error(cmbRole,lblRoleError,"Role is required.");ok=false;}
        if(blank(txtPassword)){error(txtPassword,lblPasswordError,"Password is required.");ok=false;} else if(txtPassword.getText().length()<8||!txtPassword.getText().matches(".*[A-Za-z].*")||!txtPassword.getText().matches(".*[0-9].*")){error(txtPassword,lblPasswordError,"Use 8+ characters with a letter and number.");ok=false;}
        if(blank(txtConfirm)){error(txtConfirm,lblConfirmError,"Confirm password is required.");ok=false;} else if(!txtPassword.getText().equals(txtConfirm.getText())){error(txtConfirm,lblConfirmError,"Passwords do not match.");ok=false;}
        return ok;
    }
    @FXML private void back(){SceneManager.showLogin();}
    private void invalidateChallenge(){pending=null;challengeId=null;}
    private boolean blank(TextInputControl c){return c.getText()==null||c.getText().trim().isEmpty();}
    private void bindClear(TextInputControl f,Label l){f.textProperty().addListener((o,a,b)->{if(b!=null&&!b.isBlank())clearField(f,l);});}
    private void error(Control f,Label l,String t){l.setText(t);l.setManaged(true);l.setVisible(true);if(!f.getStyleClass().contains("invalid-field"))f.getStyleClass().add("invalid-field");}
    private void clearField(Control f,Label l){l.setManaged(false);l.setVisible(false);f.getStyleClass().remove("invalid-field");}
    private void message(String t,boolean error){lblMessage.setText(t);lblMessage.getStyleClass().removeAll("message-error","message-success");lblMessage.getStyleClass().add(error?"message-error":"message-success");}
}
