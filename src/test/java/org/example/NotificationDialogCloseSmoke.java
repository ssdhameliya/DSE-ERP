package org.example;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.example.theme.ThemeManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies that the modal notification center can be closed and releases its owner window. */
public final class NotificationDialogCloseSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        DatabaseManager.initialize();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.startup(() -> {
            try {
                Parent root = FXMLLoader.load(NotificationDialogCloseSmoke.class.getResource("/fxml/pages/Dashboard.fxml"));
                Stage owner = new Stage();
                owner.setScene(new Scene(root, 1500, 900));
                ThemeManager.applyTheme(owner.getScene());
                owner.show();

                Button notifications = (Button) root.lookup("#btnNotifications");
                if (notifications == null) throw new IllegalStateException("Notification header button was not found");

                PauseTransition closeDialog = new PauseTransition(Duration.millis(500));
                closeDialog.setOnFinished(event -> {
                    try {
                        Window dialogWindow = Window.getWindows().stream()
                            .filter(window -> window != owner && window.isShowing())
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Notification dialog did not open"));

                        if (!(dialogWindow.getScene().getRoot() instanceof DialogPane pane)) {
                            throw new IllegalStateException("Notification root is not a DialogPane");
                        }
                        if (!Boolean.TRUE.equals(pane.getProperties().get("erp-dialog-custom"))) {
                            throw new IllegalStateException("Custom dialog preserve marker is missing");
                        }
                        if (pane.getStyleClass().contains("erp-modern-dialog")) {
                            throw new IllegalStateException("Legacy dialog class leaked into custom notification dialog");
                        }
                        if (pane.getGraphic() != null) {
                            throw new IllegalStateException("Notification DialogPane received a duplicate outer graphic");
                        }

                        for (String label : new String[] {"Mark all read", "Clear history", "Close"}) {
                            Button action = dialogWindow.getScene().getRoot().lookupAll(".button").stream()
                                .filter(Button.class::isInstance)
                                .map(Button.class::cast)
                                .filter(button -> label.equals(button.getText()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException(label + " button was not found"));
                            if (action.getGraphic() == null) {
                                throw new IllegalStateException(label + " button icon is missing");
                            }
                        }

                        Button close = dialogWindow.getScene().getRoot().lookupAll(".button").stream()
                            .filter(Button.class::isInstance)
                            .map(Button.class::cast)
                            .filter(button -> "Close".equals(button.getText()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Notification Close button was not found"));
                        close.fire();

                        PauseTransition verify = new PauseTransition(Duration.millis(250));
                        verify.setOnFinished(verifyEvent -> {
                            try {
                                boolean dialogStillOpen = Window.getWindows().stream()
                                    .anyMatch(window -> window != owner && window.isShowing());
                                if (dialogStillOpen) throw new IllegalStateException("Notification dialog remained open");
                                if (!owner.isShowing()) throw new IllegalStateException("Owner window was closed unexpectedly");
                                System.out.println("NOTIFICATION_CLOSE_OK");
                            } catch (Throwable error) {
                                failure.set(error);
                            } finally {
                                owner.close();
                                done.countDown();
                                Platform.exit();
                            }
                        });
                        verify.play();
                    } catch (Throwable error) {
                        failure.set(error);
                        owner.close();
                        done.countDown();
                        Platform.exit();
                    }
                });
                closeDialog.play();
                Platform.runLater(notifications::fire);
            } catch (Throwable error) {
                failure.set(error);
                done.countDown();
                Platform.exit();
            }
        });

        if (!done.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Notification close smoke test timed out");
        if (failure.get() != null) throw new RuntimeException("Notification close smoke test failed", failure.get());
    }
}
