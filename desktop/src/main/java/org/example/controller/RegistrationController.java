package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.example.api.auth.AuthApiClient;
import org.example.model.AppUser;
import org.example.service.BrandingService;
import org.example.service.BrandImagePresenter;
import org.example.service.UserService;
import org.example.update.BuildInfo;
import org.example.util.*;

import java.util.regex.Pattern;

public class RegistrationController {
    private static final Pattern EMAIL=Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML private ImageView imgBrandLogo,imgBrandMark;
    @FXML private StackPane brandLogoBox,brandMarkBox;
    @FXML private VBox brandPanel;
    @FXML private Label lblBrandMark,lblBrandName,lblBrandTagline,lblBrandDescription,lblVersion,lblMessage,
            lblNameError,lblUsernameError,lblEmailError,lblRoleError,lblPasswordError,lblConfirmError,
            lblCaptchaQuestion,lblCaptchaError,lblOtpError,lblAuthenticatorStatus;
    @FXML private TextField txtName,txtUsername,txtEmail,txtCaptcha,txtOtp,txtRole;
    @FXML private PasswordField txtPassword,txtConfirm;
    @FXML private Button btnRefreshCaptcha,btnSendOtp,btnVerifyEmail,btnBack;

    private final UserService users=new UserService();
    private AppUser pending;
    private AuthApiClient.RoleOption registrationRole;
    private String captchaId,challengeId;
    private Long registrationId;

    @FXML public void initialize(){
        BrandImagePresenter.applicationBanner(imgBrandLogo,brandLogoBox);
        BrandImagePresenter.contain(imgBrandMark,brandMarkBox);
        applyBranding();
        if(lblVersion!=null)lblVersion.setText("Version "+BuildInfo.version());
        UiActionIcons.apply(btnRefreshCaptcha,"refresh","Refresh CAPTCHA");
        UiActionIcons.apply(btnSendOtp,"email","Send email OTP");
        UiActionIcons.apply(btnVerifyEmail,"validate","Verify Email OTP");
        UiActionIcons.apply(btnBack,"return","Back to login");
        try{
            var roles=users.registrationRoles();
            registrationRole=roles.isEmpty()?null:roles.getFirst();
            txtRole.setText(registrationRole==null?"Unavailable":registrationRole.displayName());
            if(registrationRole==null) message("Self-registration role is not configured. Ask an Administrator to configure it.",true);
        }catch(Exception e){
            txtRole.setText("Unavailable");
            message("Unable to load registration role: "+e.getMessage(),true);
        }
        refreshCaptcha();
    }

    private void applyBranding(){
        lblBrandName.setText(BrandingService.applicationName());
        lblBrandTagline.setText(BrandingService.tagline());
        lblBrandDescription.setText(BrandingService.loginDescription());
        Image logo=BrandingService.applicationBrandImage();
        if(logo!=null&&!logo.isError()){imgBrandLogo.setImage(logo);imgBrandLogo.setManaged(true);imgBrandLogo.setVisible(true);}
        Image mark=BrandingService.applicationMarkImage();boolean a=mark!=null&&!mark.isError();
        imgBrandMark.setImage(a?mark:null);imgBrandMark.setManaged(a);imgBrandMark.setVisible(a);brandMarkBox.setManaged(a);brandMarkBox.setVisible(a);
        lblBrandMark.setManaged(false);lblBrandMark.setVisible(false);
    }

    @FXML private void refreshCaptcha(){
        try{var c=users.registrationCaptcha();captchaId=c.challengeId();lblCaptchaQuestion.setText(c.question());txtCaptcha.clear();}
        catch(Exception e){message(e.getMessage(),true);}
    }

    @FXML private void sendOtp(){
        if(!validateAccount())return;
        if(txtCaptcha.getText()==null||txtCaptcha.getText().isBlank()){fieldError(txtCaptcha,lblCaptchaError,"CAPTCHA answer is required.");return;}
        pending=new AppUser();pending.setFullName(txtName.getText().trim());pending.setUsername(txtUsername.getText().trim());pending.setEmail(txtEmail.getText().trim());pending.setPassword(txtPassword.getText());pending.setRole(registrationRole.code());pending.setMfaEnabled(true);
        try{
            var c=users.requestRegistrationOtp(pending,captchaId,txtCaptcha.getText().trim());
            challengeId=c.challengeId();
            message(c.message()+". Enter the email OTP below.",false);
            txtOtp.requestFocus();
        }catch(Exception e){
            message(RegistrationErrorPolicy.userMessage(e),true);
            if(RegistrationErrorPolicy.isCaptchaFailure(e)) refreshCaptcha();
        }
    }

    @FXML private void verifyEmail(){
        clear(lblOtpError,txtOtp);
        if(pending==null||challengeId==null){message("Complete CAPTCHA and send the email OTP first.",true);return;}
        if(txtOtp.getText()==null||txtOtp.getText().isBlank()){fieldError(txtOtp,lblOtpError,"Email OTP is required.");return;}
        try{
            var setup=users.verifyRegistrationEmail(pending,challengeId,txtOtp.getText().trim());
            registrationId=setup.registrationId();
            lblAuthenticatorStatus.setText("Email verified. Complete authenticator setup in the QR scanner window.");
            showAuthenticatorSetup(setup);
        }catch(Exception e){message(e.getMessage(),true);}
    }

    private void showAuthenticatorSetup(AuthApiClient.RegistrationMfaSetupResponse setup){
        OwnedDialog<Boolean> dlg=new OwnedDialog<>(txtOtp);
        dlg.setTitle("Set Up Authenticator");
        ButtonType cancel=ButtonType.CANCEL;
        ButtonType verify=new ButtonType("Verify & Submit for Approval",ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(cancel,verify);

        VBox content=new VBox(10);content.getStyleClass().add("authenticator-setup-card");
        Label title=new Label("Scan with Google or Microsoft Authenticator");title.getStyleClass().add("section-title");
        Label help=new Label("Scan the QR code, then enter the current 6-digit code. If scanning is unavailable, use the setup key below.");help.setWrapText(true);help.getStyleClass().add("auth-description");
        ImageView qr=new ImageView();qr.setFitWidth(240);qr.setFitHeight(240);qr.setPreserveRatio(true);qr.getStyleClass().add("authenticator-qr-image");
        try{qr.setImage(QrCodeImageFactory.create(setup.provisioningUri(),240));}
        catch(Exception e){help.setText(help.getText()+" QR rendering is unavailable on this workstation; use the setup key.");}

        TextField secret=new TextField(setup.manualSecret());secret.setEditable(false);secret.setFocusTraversable(false);secret.getStyleClass().addAll("auth-input","authenticator-secret");
        Button copy=new Button("Copy Setup Key");copy.getStyleClass().addAll("approved-button","approved-secondary-button");UiActionIcons.apply(copy,"copy","Copy setup key");
        copy.setOnAction(e->{ClipboardContent cc=new ClipboardContent();cc.putString(setup.manualSecret());Clipboard.getSystemClipboard().setContent(cc);});
        HBox secretRow=new HBox(8,secret,copy);HBox.setHgrow(secret,Priority.ALWAYS);

        TextField code=new TextField();code.setPromptText("Current 6-digit code");code.getStyleClass().add("auth-input");code.setMaxWidth(Double.MAX_VALUE);
        Label error=new Label();error.setManaged(false);error.setVisible(false);error.getStyleClass().add("field-error");
        content.getChildren().addAll(title,help,qr,new Label("Manual setup key"),secretRow,new Label("Authenticator code"),code,error);
        dlg.getDialogPane().setContent(content);

        Button verifyButton=(Button)dlg.getDialogPane().lookupButton(verify);
        UiActionIcons.apply(verifyButton,"security","Verify authenticator and submit for approval");
        verifyButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            String otp=code.getText()==null?"":code.getText().trim();
            if(!otp.matches("\\d{6}")){e.consume();error.setText("Enter the current 6-digit authenticator code.");error.setManaged(true);error.setVisible(true);code.requestFocus();return;}
            try{
                users.completeRegistrationMfa(registrationId,otp);
                lblAuthenticatorStatus.setText("✓ Authenticator verified. Registration is pending Administrator approval.");
                message("Registration submitted successfully. You cannot sign in until an Administrator approves the account.",false);
                btnSendOtp.setDisable(true);btnVerifyEmail.setDisable(true);txtOtp.setDisable(true);
                dlg.setResult(Boolean.TRUE);
            }catch(Exception ex){e.consume();error.setText(ex.getMessage()==null?"Authenticator verification failed.":ex.getMessage());error.setManaged(true);error.setVisible(true);code.requestFocus();}
        });
        javafx.application.Platform.runLater(code::requestFocus);
        dlg.showAndWait();
    }

    private boolean validateAccount(){
        clear(lblNameError,txtName);clear(lblUsernameError,txtUsername);clear(lblEmailError,txtEmail);clear(lblPasswordError,txtPassword);clear(lblConfirmError,txtConfirm);clear(lblRoleError,txtRole);
        boolean ok=true;
        if(blank(txtName)){fieldError(txtName,lblNameError,"Full name is required.");ok=false;}
        if(blank(txtUsername)){fieldError(txtUsername,lblUsernameError,"Username is required.");ok=false;}
        if(blank(txtEmail)||!EMAIL.matcher(txtEmail.getText().trim()).matches()){fieldError(txtEmail,lblEmailError,"Valid email is required.");ok=false;}
        if(registrationRole==null||"ADMIN".equalsIgnoreCase(registrationRole.code())){fieldError(txtRole,lblRoleError,"Administrator accounts cannot be self-registered.");ok=false;}
        if(blank(txtPassword)||txtPassword.getText().length()<8||!txtPassword.getText().matches(".*[A-Za-z].*")||!txtPassword.getText().matches(".*[0-9].*")){fieldError(txtPassword,lblPasswordError,"Use 8+ characters with a letter and number.");ok=false;}
        if(!txtPassword.getText().equals(txtConfirm.getText())){fieldError(txtConfirm,lblConfirmError,"Passwords do not match.");ok=false;}
        if(!ok)message("Please correct the highlighted fields.",true);return ok;
    }

    private boolean blank(TextInputControl c){return c.getText()==null||c.getText().trim().isEmpty();}
    private void fieldError(Control c,Label l,String m){l.setText(m);l.setManaged(true);l.setVisible(true);if(!c.getStyleClass().contains("invalid-field"))c.getStyleClass().add("invalid-field");}
    private void clear(Label l,Control c){l.setManaged(false);l.setVisible(false);c.getStyleClass().remove("invalid-field");}
    private void message(String t,boolean e){lblMessage.setText(t==null?"":t);lblMessage.getStyleClass().removeAll("message-error","message-success");lblMessage.getStyleClass().add(e?"message-error":"message-success");}
    @FXML private void back(){SceneManager.showLogin();}
}
