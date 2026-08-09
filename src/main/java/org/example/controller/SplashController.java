package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class SplashController {

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label lblStatus;

    @FXML
    public void initialize() {

        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lblStatus.setText("Preparing your workspace...");

    }

}
