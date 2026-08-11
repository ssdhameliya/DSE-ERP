package org.example.util;

import javafx.fxml.FXMLLoader;
import org.example.controller.SetupWizardController;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.theme.ThemeManager;

import java.io.IOException;

public class SceneManager {

    public static Stage primaryStage;



    private SceneManager() {
    }

    public static void initialize(Stage stage) {

        primaryStage = stage;
        primaryStage.setTitle("DSE ERP");
        WindowUtilsFx.applyAdaptiveMinimums(primaryStage, javafx.stage.Screen.getPrimary().getVisualBounds());

    }


    public static void showSetupWizard(Runnable onCompleted) {
        try {
            var url = SceneManager.class.getResource("/fxml/pages/SetupWizard.fxml");
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            SetupWizardController controller = loader.getController();
            controller.setOnCompleted(onCompleted);
            javafx.geometry.Rectangle2D usable = javafx.stage.Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(root, Math.min(1280, usable.getWidth()), Math.min(820, usable.getHeight()));
            ThemeManager.applyTheme(scene);
            PlatformUiSupport.installResponsiveClasses(scene);
            primaryStage.setScene(scene);
            if (!primaryStage.isShowing()) primaryStage.centerOnScreen();
            primaryStage.show();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to open first-run setup.", exception);
        }
    }

    /** Shows a non-interactive startup screen while configuration/database services initialize. */
    public static void showSplash() {
        load("/fxml/pages/Splash.fxml");
    }

    public static void showLogin() {
        load("/fxml/pages/Login.fxml");
    }
    /** Updates the startup splash from background runtime bootstrap work. */
    public static void updateSplashStatus(String message) {
        javafx.application.Platform.runLater(() -> {
            if (primaryStage == null || primaryStage.getScene() == null) return;
            javafx.scene.Node node = primaryStage.getScene().lookup("#lblStatus");
            if (node instanceof javafx.scene.control.Label label) label.setText(message);
        });
    }

    public static void showRegistration() {load("/fxml/pages/Registration.fxml");}
    public static void loadEmailSettings() { load("/fxml/pages/EmailSettings.fxml"); }


    public static void showDashboard() {
        load("/fxml/pages/Dashboard.fxml");
    }

    private static void load(String fxml) {

        try {

            System.out.println("Loading FXML: " + fxml);

            var url = SceneManager.class.getResource(fxml);

            System.out.println("URL = " + url);

            FXMLLoader loader = new FXMLLoader(url);

            Parent root = loader.load();

            javafx.geometry.Rectangle2D usable = WindowUtilsFx.visualBoundsFor(primaryStage);
            double width = Math.min(Math.max(primaryStage.getWidth(), 960), usable.getWidth());
            double height = Math.min(Math.max(primaryStage.getHeight(), 640), usable.getHeight());
            Scene scene = new Scene(root, width, height);

            ThemeManager.applyTheme(scene);

            PlatformUiSupport.installResponsiveClasses(scene);
            primaryStage.setScene(scene);
            if (!primaryStage.isShowing()) primaryStage.centerOnScreen();
            primaryStage.show();

        } catch (Exception e) {

            e.printStackTrace();

        }

    }

    public static void showPurchaseList() {

        load("/fxml/pages/PurchaseList.fxml");

    }

}
