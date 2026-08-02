package org.example.util;

import javafx.fxml.FXMLLoader;
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
        primaryStage.setTitle("JavaApp ERP");
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(700);

    }

    /** Opens authentication before the permission-aware application shell. */
    public static void showSplash() {
        load("/fxml/pages/Login.fxml");
    }

    public static void showLogin() {
        load("/fxml/pages/Login.fxml");
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

            Scene scene = new Scene(root, 1440, 900);

            ThemeManager.applyTheme(scene);

            primaryStage.setScene(scene);

            primaryStage.centerOnScreen();

            primaryStage.show();

        } catch (Exception e) {

            e.printStackTrace();

        }

    }

    public static void showPurchaseList() {

        load("/fxml/pages/PurchaseList.fxml");

    }

}
