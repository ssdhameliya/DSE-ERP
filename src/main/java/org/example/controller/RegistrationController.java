package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.model.AppUser;
import org.example.service.OtpService;
import org.example.service.UserService;
import org.example.util.ClockService;
import org.example.util.IconFactory;
import org.example.util.SceneManager;

import java.util.regex.Pattern;

public class RegistrationController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    @FXML private Label lblClock, lblMessage, lblNameError, lblUsernameError, lblEmailError, lblPasswordError, lblConfirmError, lblOtpError;
    @FXML private TextField txtName, txtUsername, txtEmail, txtOtp;
    @FXML private PasswordField txtPassword, txtConfirm;
    @FXML private Button btnSendOtp, btnCreate, btnBack;
    private final UserService users = new UserService();
    private AppUser pending;

    @FXML public void initialize() {
        ClockService.start(lblClock);
        btnSendOtp.setGraphic(IconFactory.icon("email")); btnCreate.setGraphic(IconFactory.icon("add")); btnBack.setGraphic(IconFactory.icon("return"));
        bindClear(txtName,lblNameError); bindClear(txtUsername,lblUsernameError); bindClear(txtEmail,lblEmailError); bindClear(txtPassword,lblPasswordError); bindClear(txtConfirm,lblConfirmError); bindClear(txtOtp,lblOtpError);
    }

    @FXML private void sendOtp() {
        if (!validateAccountFields()) { message("Please correct the highlighted fields.", true); return; }
        pending = new AppUser(); pending.setFullName(txtName.getText().trim()); pending.setUsername(txtUsername.getText().trim()); pending.setEmail(txtEmail.getText().trim()); pending.setPassword(txtPassword.getText());
        try { boolean sent=OtpService.issueAndSend(pending.getEmail()); message(sent?"OTP sent. Enter it to create your User account.":"OTP was already sent. Please use the latest code.", false); txtOtp.requestFocus(); }
        catch(Exception e){ message(e.getMessage(), true); }
    }

    @FXML private void register() {
        clearField(txtOtp,lblOtpError);
        if (pending == null) { message("Send the OTP first.", true); return; }
        if (txtOtp.getText()==null || txtOtp.getText().trim().isEmpty()) { error(txtOtp,lblOtpError,"Verification code is required."); message("Please enter the verification code.",true); return; }
        if (!OtpService.verify(txtOtp.getText().trim())) { error(txtOtp,lblOtpError,"The OTP is invalid or expired."); message("The OTP is invalid or expired.",true); return; }
        try { users.register(pending); message("Registration complete. You can now sign in as USER.", false); }
        catch(Exception e){ message(e.getMessage(), true); }
    }

    private boolean validateAccountFields() {
        boolean ok=true;
        clearField(txtName,lblNameError); clearField(txtUsername,lblUsernameError); clearField(txtEmail,lblEmailError); clearField(txtPassword,lblPasswordError); clearField(txtConfirm,lblConfirmError);
        if(blank(txtName)){error(txtName,lblNameError,"Full name is required.");ok=false;}
        if(blank(txtUsername)){error(txtUsername,lblUsernameError,"Username is required.");ok=false;}
        if(blank(txtEmail)){error(txtEmail,lblEmailError,"Email address is required.");ok=false;} else if(!EMAIL.matcher(txtEmail.getText().trim()).matches()){error(txtEmail,lblEmailError,"Enter a valid email address.");ok=false;}
        if(blank(txtPassword)){error(txtPassword,lblPasswordError,"Password is required.");ok=false;} else if(txtPassword.getText().length()<6){error(txtPassword,lblPasswordError,"Use at least 6 characters.");ok=false;}
        if(blank(txtConfirm)){error(txtConfirm,lblConfirmError,"Confirm password is required.");ok=false;} else if(!txtPassword.getText().equals(txtConfirm.getText())){error(txtConfirm,lblConfirmError,"Passwords do not match.");ok=false;}
        return ok;
    }
    @FXML private void back(){SceneManager.showLogin();}
    private boolean blank(TextInputControl c){return c.getText()==null||c.getText().trim().isEmpty();}
    private void bindClear(TextInputControl f,Label l){f.textProperty().addListener((o,a,b)->{if(b!=null&&!b.isBlank())clearField(f,l);});}
    private void error(Control f,Label l,String t){l.setText(t);l.setManaged(true);l.setVisible(true);if(!f.getStyleClass().contains("invalid-field"))f.getStyleClass().add("invalid-field");}
    private void clearField(Control f,Label l){l.setManaged(false);l.setVisible(false);f.getStyleClass().remove("invalid-field");}
    private void message(String t,boolean error){lblMessage.setText(t);lblMessage.getStyleClass().removeAll("message-error","message-success");lblMessage.getStyleClass().add(error?"message-error":"message-success");}
}
