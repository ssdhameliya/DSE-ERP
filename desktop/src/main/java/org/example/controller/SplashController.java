package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import org.example.update.BuildInfo;
import org.example.service.BrandingService;

public class SplashController {
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus, progressPercent, elapsedTime, stageValue, systemValue, databaseValue, versionLabel;
    @FXML private Label workspaceStatus, postgresStatus, springStatus, schemaStatus, applicationStatus;
    @FXML private Label workspaceTime, postgresTime, springTime, schemaTime, applicationTime;
    @FXML private Label lblBrandMark, lblBrandName, lblBrandTagline, lblStarting;
    @FXML private ImageView imgBrandLogo;

    private long startedNanos;
    private Timeline clock;
    private int activeStage = 1;
    private long stageStartedNanos;

    @FXML
    public void initialize() {
        startedNanos = System.nanoTime();
        stageStartedNanos = startedNanos;
        versionLabel.setText("Version " + BuildInfo.version());
        applyBranding();
        systemValue.setText(systemMemoryLabel());
        databaseValue.setText("PostgreSQL");
        updateStage(1, "Loading workspace and configuration...");
        clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshElapsed()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    public void refreshBranding() {
        applyBranding();
    }

    private void applyBranding() {
        if (lblBrandName != null) lblBrandName.setText(BrandingService.applicationName());
        if (lblBrandTagline != null) lblBrandTagline.setText(BrandingService.tagline());
        if (lblStarting != null) lblStarting.setText(BrandingService.startingText());
        Image logo = BrandingService.logo();
        if (logo != null && !logo.isError() && imgBrandLogo != null) {
            imgBrandLogo.setImage(logo);
            imgBrandLogo.setManaged(true);
            imgBrandLogo.setVisible(true);
            if (lblBrandMark != null) {
                lblBrandMark.setManaged(false);
                lblBrandMark.setVisible(false);
            }
        } else {
            if (imgBrandLogo != null) {
                imgBrandLogo.setImage(null);
                imgBrandLogo.setManaged(false);
                imgBrandLogo.setVisible(false);
            }
            if (lblBrandMark != null) {
                lblBrandMark.setManaged(true);
                lblBrandMark.setVisible(true);
            }
        }
    }

    public void updateStage(int stage, String message) {
        int next = Math.max(1, Math.min(5, stage));
        if (next != activeStage) {
            completeStage(activeStage);
            activeStage = next;
            stageStartedNanos = System.nanoTime();
        }
        lblStatus.setText(message == null ? "" : message);
        stageValue.setText(activeStage + " / 5");
        double progress = switch (activeStage) {
            case 1 -> 0.10;
            case 2 -> 0.30;
            case 3 -> 0.55;
            case 4 -> 0.78;
            default -> 0.94;
        };
        progressBar.setProgress(progress);
        progressPercent.setText(Math.round(progress * 100) + "%");
        markInProgress(activeStage);
    }

    public void markReady(String message) {
        completeStage(activeStage);
        activeStage = 5;
        applicationStatus.setText("Completed");
        setState(applicationStatus, "splash-state-complete");
        applicationTime.setText(elapsedSince(stageStartedNanos));
        progressBar.setProgress(1.0);
        progressPercent.setText("100%");
        stageValue.setText("5 / 5");
        lblStatus.setText(message == null ? "Opening " + BrandingService.applicationName() + "..." : message);
        refreshElapsed();
    }

    private void completeStage(int stage) {
        Label status = status(stage);
        Label time = time(stage);
        if (status == null) return;
        status.setText("Completed");
        setState(status, "splash-state-complete");
        if (time != null && time.getText().isBlank()) time.setText(elapsedSince(stageStartedNanos));
    }

    private void markInProgress(int stage) {
        for (int i = 1; i <= 5; i++) {
            Label status = status(i);
            if (status == null) continue;
            if (i < stage) {
                status.setText("Completed");
                setState(status, "splash-state-complete");
            } else if (i == stage) {
                status.setText("In Progress...");
                setState(status, "splash-state-progress");
            } else {
                status.setText("Pending");
                setState(status, "splash-state-pending");
            }
        }
    }

    private Label status(int stage) {
        return switch (stage) {
            case 1 -> workspaceStatus; case 2 -> postgresStatus; case 3 -> springStatus;
            case 4 -> schemaStatus; case 5 -> applicationStatus; default -> null;
        };
    }

    private Label time(int stage) {
        return switch (stage) {
            case 1 -> workspaceTime; case 2 -> postgresTime; case 3 -> springTime;
            case 4 -> schemaTime; case 5 -> applicationTime; default -> null;
        };
    }

    private void setState(Label label, String state) {
        label.getStyleClass().removeAll("splash-state-complete", "splash-state-progress", "splash-state-pending");
        if (!label.getStyleClass().contains(state)) label.getStyleClass().add(state);
    }

    private void refreshElapsed() {
        long seconds = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000_000L);
        elapsedTime.setText(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60));
    }

    private String elapsedSince(long nanos) {
        double seconds = Math.max(0, System.nanoTime() - nanos) / 1_000_000_000.0;
        return String.format("(%.1fs)", seconds);
    }

    private String systemMemoryLabel() {
        try {
            long bytes = ((com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
            long gb = Math.max(1, Math.round(bytes / 1073741824.0));
            return gb + " GB RAM";
        } catch (Exception ignored) {
            return System.getProperty("os.arch", "System");
        }
    }
}
