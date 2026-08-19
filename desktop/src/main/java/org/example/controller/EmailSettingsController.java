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
    @FXML private Label lblClock,lblMessage,lblEmailError,lblPasswordError;
    @FXML private TextField txtSmtpEmail;
    @FXML private PasswordField txtSmtpPassword;
    @FXML private Button btnSave,btnBack;

    @FXML public void initialize(){
        txtSmtpEmail.setText(ConfigManager.get("smtp.email","shailesh.rockstar007@yahoo.com")); txtSmtpPassword.setText(ConfigManager.get("smtp.appPassword",""));
        BrandImagePresenter.applicationBanner(imgBrandLogo, brandLogoBox); BrandImagePresenter.contain(imgBrandMark, brandMarkBox);
        applyBranding();
        if (lblVersion != null) lblVersion.setText("Version " + BuildInfo.version());
        btnSave.setGraphic(IconFactory.icon("save")); btnBack.setGraphic(IconFactory.icon("return"));
        txtSmtpEmail.textProperty().addListener((o,a,b)->{if(b!=null&&!b.isBlank())clear(txtSmtpEmail,lblEmailError);});
        txtSmtpPassword.textProperty().addListener((o,a,b)->{if(b!=null&&!b.isBlank())clear(txtSmtpPassword,lblPasswordError);});
        Platform.runLater(this::installResponsiveBranding);
    }

    private void applyBranding(){
        lblBrandName.setText(BrandingService.applicationName()); lblBrandTagline.setText(BrandingService.tagline()); lblBrandDescription.setText(BrandingService.loginDescription());
        Image logo=BrandingService.applicationBrandImage(); if(logo!=null&&!logo.isError()){imgBrandLogo.setImage(logo);imgBrandLogo.setManaged(true);imgBrandLogo.setVisible(true);}
        Image mark=BrandingService.applicationMarkImage(); boolean available=mark!=null&&!mark.isError();
        imgBrandMark.setImage(available?mark:null); imgBrandMark.setManaged(available); imgBrandMark.setVisible(available);
        if(brandMarkBox!=null){brandMarkBox.setManaged(available);brandMarkBox.setVisible(available);}
        lblBrandMark.setManaged(false); lblBrandMark.setVisible(false);
    }

    private void installResponsiveBranding(){
        if(brandPanel==null||brandPanel.getScene()==null)return;
        Runnable resize=()->brandPanel.setPrefWidth(Math.max(360,Math.min(690,brandPanel.getScene().getWidth()*.46)));
        brandPanel.getScene().widthProperty().addListener((o,a,b)->resize.run()); resize.run();
    }

    @FXML private void save(){
        clear(txtSmtpEmail,lblEmailError); clear(txtSmtpPassword,lblPasswordError); boolean ok=true;
        String email=txtSmtpEmail.getText()==null?"":txtSmtpEmail.getText().trim();
        if(email.isEmpty()){error(txtSmtpEmail,lblEmailError,"Yahoo email address is required.");ok=false;} else if(!EMAIL.matcher(email).matches()){error(txtSmtpEmail,lblEmailError,"Enter a valid email address.");ok=false;}
        if(txtSmtpPassword.getText()==null||txtSmtpPassword.getText().isBlank()){error(txtSmtpPassword,lblPasswordError,"Yahoo app password is required.");ok=false;}
        if(!ok){message("Please correct the highlighted fields.",true);return;}
        ConfigManager.set("smtp.email",email); ConfigManager.set("smtp.appPassword",txtSmtpPassword.getText()); message("Email settings saved successfully.",false); leaveSettings();
    }
    @FXML private void back(){leaveSettings();}
    private void leaveSettings(){if(txtSmtpEmail.getScene()!=null&&NavigationManager.navigateOrReport("/fxml/pages/Settings.fxml"))return;SceneManager.showLogin();}
    private void error(Control f,Label l,String t){l.setText(t);l.setManaged(true);l.setVisible(true);if(!f.getStyleClass().contains("invalid-field"))f.getStyleClass().add("invalid-field");}
    private void clear(Control f,Label l){l.setManaged(false);l.setVisible(false);f.getStyleClass().remove("invalid-field");}
    private void message(String t,boolean error){lblMessage.setText(t);lblMessage.getStyleClass().removeAll("message-error","message-success");lblMessage.getStyleClass().add(error?"message-error":"message-success");}
}
