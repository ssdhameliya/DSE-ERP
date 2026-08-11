package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.example.model.AppUser;
import org.example.service.SessionService;

public class ProfileController {
    @FXML
    private Label lblName;
    @FXML
    private Label lblUsername;
    @FXML
    private Label lblEmail;

    @FXML
    public void initialize() {
        AppUser user = SessionService.current();
        if (user == null) {
            return;
        }

        lblName.setText(user.getFullName());
        lblUsername.setText(user.getUsername());
        lblEmail.setText(user.getEmail());
    }
}
