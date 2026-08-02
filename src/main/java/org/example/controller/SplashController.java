package org.example.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;
import org.example.util.SceneManager;

public class SplashController {

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label lblStatus;

    @FXML
    public void initialize() {

        progressBar.setProgress(0);

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.seconds(0.5), e -> {
                progressBar.setProgress(0.25);
                lblStatus.setText("Loading Configuration...");
            }),

            new KeyFrame(Duration.seconds(1.2), e -> {
                progressBar.setProgress(0.50);
                lblStatus.setText("Loading User Interface...");
            }),

            new KeyFrame(Duration.seconds(2), e -> {
                progressBar.setProgress(0.75);
                lblStatus.setText("Preparing Login...");
            }),

            new KeyFrame(Duration.seconds(3), e -> {
                progressBar.setProgress(1.0);
                SceneManager.showLogin();
            })
        );

        timeline.play();

    }

}
