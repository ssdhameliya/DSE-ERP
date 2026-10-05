package org.example.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Universal live operation progress dialog for DSE ERP.
 * Prevents UI freezes during lengthy tasks (Email delivery, PDF generation,
 * WhatsApp messaging, imports, reconciliations) while giving the user real-time
 * visibility into ongoing stages, elapsed time, and event logs.
 */
public final class LiveOperationDialog {

    @FunctionalInterface
    public interface Operation<T> {
        T execute(ProgressReporter reporter) throws Exception;
    }

    @FunctionalInterface
    public interface VoidOperation {
        void execute(ProgressReporter reporter) throws Exception;
    }

    public interface ProgressReporter {
        void stage(String stageName);
        void stage(int currentStep, int totalSteps, String stageName);
        void progress(double fraction);
        void log(String detail);
        boolean isCancelled();
    }

    private LiveOperationDialog() {}

    public static <T> void execute(Node ownerNode, String title, String semantic,
                                   String initialStage, Operation<T> task,
                                   Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Window owner = DialogOwnerResolver.resolve(ownerNode);
        Dialog<Void> dialog = new Dialog<>();
        dialog.initStyle(PlatformUiSupport.isMac() ? StageStyle.UTILITY : StageStyle.TRANSPARENT);
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }
        AppDialogRenderer.install(dialog);
        AppDialogRenderer.configureWorkspace(dialog, semantic == null || semantic.isBlank() ? "process" : semantic,
                title, title, "", "");

        Label lblStage = new Label(initialStage);
        lblStage.setWrapText(true);
        lblStage.getStyleClass().add("section-title");

        ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        Label lblTimer = new Label("00:00 elapsed");
        lblTimer.getStyleClass().add("page-subtitle");

        Label lblStatus = new Label("In Progress");
        lblStatus.getStyleClass().add("page-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox metaBox = new HBox(8, lblTimer, spacer, lblStatus);
        metaBox.setAlignment(Pos.CENTER_LEFT);

        VBox boxLog = new VBox(4);
        boxLog.getStyleClass().add("settings-subcard");
        boxLog.setPadding(new Insets(8));
        Label initialLogLabel = new Label("• " + initialStage);
        initialLogLabel.setWrapText(true);
        initialLogLabel.getStyleClass().add("settings-section-description");
        boxLog.getChildren().add(initialLogLabel);

        VBox content = new VBox(10, lblStage, progressBar, metaBox, boxLog);
        content.setMinWidth(440);
        content.setPrefWidth(480);
        content.setMaxWidth(Double.MAX_VALUE);

        dialog.getDialogPane().setContent(content);

        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(cancelType);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        final long startTime = System.currentTimeMillis();

        Timeline timer = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            if (completed.get()) return;
            long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
            long mins = elapsedSeconds / 60;
            long secs = elapsedSeconds % 60;
            lblTimer.setText(String.format("%02d:%02d elapsed", mins, secs));
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();

        dialog.setOnCloseRequest(event -> {
            if (!completed.get()) {
                cancelled.set(true);
                lblStatus.setText("Cancelling...");
            }
        });

        List<String> logEntries = new ArrayList<>();
        logEntries.add(initialStage);

        ProgressReporter reporter = new ProgressReporter() {
            @Override
            public void stage(String stageName) {
                Platform.runLater(() -> {
                    lblStage.setText(stageName);
                    log(stageName);
                });
            }

            @Override
            public void stage(int currentStep, int totalSteps, String stageName) {
                Platform.runLater(() -> {
                    lblStage.setText(String.format("Step %d/%d: %s", currentStep, totalSteps, stageName));
                    if (totalSteps > 0) {
                        progressBar.setProgress((double) currentStep / (double) totalSteps);
                    }
                    log(stageName);
                });
            }

            @Override
            public void progress(double fraction) {
                Platform.runLater(() -> {
                    if (fraction < 0) {
                        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    } else {
                        progressBar.setProgress(Math.min(1.0, Math.max(0.0, fraction)));
                    }
                });
            }

            @Override
            public void log(String detail) {
                Platform.runLater(() -> {
                    logEntries.add(detail);
                    while (logEntries.size() > 4) {
                        logEntries.remove(0);
                    }
                    boxLog.getChildren().clear();
                    for (String entry : logEntries) {
                        Label row = new Label("• " + entry);
                        row.setWrapText(true);
                        row.getStyleClass().add("settings-section-description");
                        boxLog.getChildren().add(row);
                    }
                });
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        Thread.ofVirtual().name("dse-live-op-" + semantic).start(() -> {
            try {
                T result = task.execute(reporter);
                Platform.runLater(() -> {
                    completed.set(true);
                    timer.stop();
                    progressBar.setProgress(1.0);
                    lblStatus.setText("Completed");
                    lblStage.setText("Operation completed successfully");
                    dialog.close();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                });
            } catch (Throwable error) {
                Platform.runLater(() -> {
                    completed.set(true);
                    timer.stop();
                    progressBar.setProgress(0.0);
                    lblStatus.setText("Failed");
                    dialog.close();
                    if (onFailure != null) {
                        onFailure.accept(error);
                    } else {
                        new OwnedAlert(javafx.scene.control.Alert.AlertType.ERROR,
                                "Operation failed:\n\n" + error.getMessage()).showAndWait();
                    }
                });
            }
        });

        dialog.show();
    }

    public static void run(Node ownerNode, String title, String semantic,
                           String initialStage, VoidOperation task,
                           Runnable onSuccess, Consumer<Throwable> onFailure) {
        execute(ownerNode, title, semantic, initialStage, reporter -> {
            task.execute(reporter);
            return null;
        }, res -> {
            if (onSuccess != null) onSuccess.run();
        }, onFailure);
    }
}
