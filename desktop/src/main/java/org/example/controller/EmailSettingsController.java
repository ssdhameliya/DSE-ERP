package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import org.example.config.ConfigManager;
import org.example.service.BrandingService;
import org.example.service.BrandImagePresenter;
import org.example.service.EmailService;
import org.example.navigation.NavigationManager;
import org.example.util.ClockService;
import org.example.util.IconFactory;
import org.example.util.SceneManager;
import org.example.update.BuildInfo;

import java.util.regex.Pattern;

public class EmailSettingsController {
    private static final Pattern EMAIL=Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    @FXML private ImageView imgBrandLogo, imgBrandMark;
    @FXML private StackPane brandLogoBox, brandMarkBox;
    @FXML private VBox brandPanel;
    @FXML private Label lblBrandMark,lblBrandName,lblBrandTagline,lblBrandDescription,lblVersion;
    @FXML private Label lblClock,lblMessage,lblEmailError,lblPasswordError,lblHostError,lblPortError;
    @FXML private TextField txtSmtpEmail,txtSmtpHost,txtSmtpPort,txtSmtpPasswordVisible;
    @FXML private PasswordField txtSmtpPassword;
    @FXML private CheckBox chkShowSmtpPassword;
    @FXML private Button btnSave,btnBack,btnTestEmail;

    @FXML public void initialize(){
        txtSmtpEmail.setText(ConfigManager.getSmtpEmail()); txtSmtpPassword.setText(ConfigManager.getSmtpPassword()); txtSmtpPasswordVisible.setText(ConfigManager.getSmtpPassword()); txtSmtpHost.setText(ConfigManager.getSmtpHost()); txtSmtpPort.setText(ConfigManager.getSmtpPort());
        BrandImagePresenter.applicationBanner(imgBrandLogo, brandLogoBox); BrandImagePresenter.contain(imgBrandMark, brandMarkBox);
        applyBranding();
        if (lblVersion != null) lblVersion.setText("Version " + BuildInfo.version());
        btnSave.setGraphic(IconFactory.icon("save")); btnBack.setGraphic(IconFactory.icon("return")); if(btnTestEmail!=null)btnTestEmail.setGraphic(IconFactory.icon("email"));
        txtSmtpEmail.textProperty().addListener((o,a,b)->{if(b!=null&&!b.isBlank())clear(txtSmtpEmail,lblEmailError);});
        txtSmtpPassword.textProperty().addListener((o,a,b)->{if(b!=null&&!b.isBlank())clear(txtSmtpPassword,lblPasswordError);});
        txtSmtpPasswordVisible.textProperty().addListener((o,a,b)->{if(b!=null&&!b.isBlank())clear(txtSmtpPasswordVisible,lblPasswordError);});
        txtSmtpHost.textProperty().addListener((o,a,b)->clear(txtSmtpHost,lblHostError));
        txtSmtpPort.textProperty().addListener((o,a,b)->clear(txtSmtpPort,lblPortError));
        
    }

    private void applyBranding(){
        lblBrandName.setText(BrandingService.applicationName()); lblBrandTagline.setText(BrandingService.tagline()); lblBrandDescription.setText(BrandingService.loginDescription());
        Image logo=BrandingService.applicationBrandImage(); if(logo!=null&&!logo.isError()){imgBrandLogo.setImage(logo);imgBrandLogo.setManaged(true);imgBrandLogo.setVisible(true);}
        Image mark=BrandingService.applicationMarkImage(); boolean available=mark!=null&&!mark.isError();
        imgBrandMark.setImage(available?mark:null); imgBrandMark.setManaged(available); imgBrandMark.setVisible(available);
        if(brandMarkBox!=null){brandMarkBox.setManaged(available);brandMarkBox.setVisible(available);}
        lblBrandMark.setManaged(false); lblBrandMark.setVisible(false);
    }



    @FXML private void save(){
        if(!validateAndPersist())return;
        message("Email settings saved successfully.",false);
        leaveSettings();
    }

    @FXML private void testEmail(){
        if(!validateAndPersist())return;
        try{
            EmailService.send(txtSmtpEmail.getText().trim(),"DSE ERP email test","Your DSE ERP email configuration is working correctly.");
            message("Test email sent successfully.",false);
        }catch(RuntimeException failure){message(failure.getMessage()==null?"Test email failed. Check the SMTP settings and try again.":failure.getMessage(),true);}
    }

    @FXML private void togglePasswordVisibility(){
        boolean show=chkShowSmtpPassword!=null&&chkShowSmtpPassword.isSelected();
        if(show)txtSmtpPasswordVisible.setText(txtSmtpPassword.getText());else txtSmtpPassword.setText(txtSmtpPasswordVisible.getText());
        txtSmtpPassword.setVisible(!show);txtSmtpPassword.setManaged(!show);txtSmtpPasswordVisible.setVisible(show);txtSmtpPasswordVisible.setManaged(show);
    }

    private String passwordValue(){return chkShowSmtpPassword!=null&&chkShowSmtpPassword.isSelected()?txtSmtpPasswordVisible.getText():txtSmtpPassword.getText();}
    private boolean validateAndPersist(){
        clear(txtSmtpEmail,lblEmailError);clear(txtSmtpPassword,lblPasswordError);clear(txtSmtpHost,lblHostError);clear(txtSmtpPort,lblPortError);boolean ok=true;
        String email=txtSmtpEmail.getText()==null?"":txtSmtpEmail.getText().trim();String password=passwordValue()==null?"":passwordValue();String host=txtSmtpHost.getText()==null?"":txtSmtpHost.getText().trim();String port=txtSmtpPort.getText()==null?"":txtSmtpPort.getText().trim();
        if(email.isEmpty()){error(txtSmtpEmail,lblEmailError,"Sending email address is required.");ok=false;}else if(!EMAIL.matcher(email).matches()){error(txtSmtpEmail,lblEmailError,"Enter a valid email address.");ok=false;}
        if(password.isBlank()){error(chkShowSmtpPassword!=null&&chkShowSmtpPassword.isSelected()?txtSmtpPasswordVisible:txtSmtpPassword,lblPasswordError,"Email app password is required.");ok=false;}
        if(!port.matches("\\d{1,5}")){error(txtSmtpPort,lblPortError,"Enter a valid SMTP port.");ok=false;}else{int value=Integer.parseInt(port);if(value<1||value>65535){error(txtSmtpPort,lblPortError,"SMTP port must be between 1 and 65535.");ok=false;}}
        if(!ok){message("Please correct the highlighted fields.",true);return false;}
        ConfigManager.setWithoutSaving("smtp.email",email);ConfigManager.setWithoutSaving("smtp.appPassword",password);ConfigManager.setWithoutSaving("smtp.host",host);ConfigManager.setWithoutSaving("smtp.port",port);ConfigManager.save();
        txtSmtpPassword.setText(password);txtSmtpPasswordVisible.setText(password);return true;
    }
    @FXML private void back(){leaveSettings();}
    private void leaveSettings(){SceneManager.showLogin();}
    private void error(Control f,Label l,String t){l.setText(t);l.setManaged(true);l.setVisible(true);if(!f.getStyleClass().contains("invalid-field"))f.getStyleClass().add("invalid-field");}
    private void clear(Control f,Label l){l.setManaged(false);l.setVisible(false);f.getStyleClass().remove("invalid-field");}
    private void message(String t,boolean error){lblMessage.setText(t);lblMessage.getStyleClass().removeAll("message-error","message-success");lblMessage.getStyleClass().add(error?"message-error":"message-success");}
}
