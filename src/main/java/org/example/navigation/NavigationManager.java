package org.example.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.application.Platform;
import org.example.util.ProfessionalUiEnhancer;
import org.example.util.ModernDialog;
import org.example.util.ToastManager;

import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public class NavigationManager {

    private static NavigationManager instance;

    private final StackPane contentPane;
    private static final AtomicBoolean NAVIGATION_IN_PROGRESS = new AtomicBoolean(false);
    private String currentPage;

    public NavigationManager(StackPane contentPane) {
        this.contentPane = contentPane;
        instance = this;
    }

    public static NavigationManager getInstance() {
        return instance;
    }

    /**
     * Loads a page atomically so controller failures or rapid repeated menu
     * clicks cannot close the application shell.
     *
     * @return true only after the new page has fully loaded
     */
    public boolean loadPage(String fxml) {
        if (fxml == null || fxml.isBlank()) return false;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> loadPage(fxml));
            return true;
        }
        if (!NAVIGATION_IN_PROGRESS.compareAndSet(false, true)) return false;
        try {
            URL url = getClass().getResource(fxml);
            if (url == null) {
                throw new IllegalStateException("Application screen was not found: " + fxml);
            }
            FXMLLoader loader = new FXMLLoader(url);
            Node page = loader.load();
            contentPane.getChildren().setAll(page);

            // Enhance once the page belongs to the live scene graph. Table header
            // graphics and Ikonli glyphs can be lost when they are created before
            // the TableView skin exists (most visible when the application starts
            // directly in light mode). The deferred pass runs after CSS/skin creation.
            ProfessionalUiEnhancer.enhance(page);
            Platform.runLater(() -> ProfessionalUiEnhancer.enhance(page));

            currentPage = fxml;
            ToastManager.info(page, "Screen ready", screenName(fxml) + " opened successfully.");
            return true;
        } catch (Throwable error) {
            error.printStackTrace();
            logFailure(fxml, error);
            ModernDialog.error(contentPane, "Screen could not be opened",
                "The ERP remains open", "Unable to open this screen.\n\n" + rootMessage(error));
            return false;
        } finally {
            NAVIGATION_IN_PROGRESS.set(false);
        }
    }

    public String getCurrentPage() { return currentPage; }

    private static String screenName(String fxml) {
        String name = fxml.substring(fxml.lastIndexOf('/') + 1).replace(".fxml", "");
        return name.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    /** Keeps intermittent production failures diagnosable without a console. */
    private static void logFailure(String fxml, Throwable error) {
        try {
            Path folder = org.example.config.ConfigManager.getConfigFolder();
            Files.createDirectories(folder);
            Path log = folder.resolve("navigation-errors.log");
            StringBuilder text = new StringBuilder()
                .append(System.lineSeparator()).append(LocalDateTime.now())
                .append(" screen=").append(fxml).append(System.lineSeparator());
            for (Throwable current = error; current != null; current = current.getCause()) {
                text.append(current.getClass().getName()).append(": ")
                    .append(current.getMessage()).append(System.lineSeparator());
                for (StackTraceElement element : current.getStackTrace())
                    text.append("  at ").append(element).append(System.lineSeparator());
            }
            Files.writeString(log, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Logging must never replace the original navigation error.
        }
    }

}
