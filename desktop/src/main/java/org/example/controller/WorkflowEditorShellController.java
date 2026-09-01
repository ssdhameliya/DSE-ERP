package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared FXML-owned shell for Project Execution editors. */
public final class WorkflowEditorShellController {
    @FXML private StackPane headerIcon;
    @FXML private Label title;
    @FXML private Label subtitle;
    @FXML private GridPane formGrid;
    @FXML private VBox lineBox;
    @FXML private Button btnAddLine;
    @FXML private TextArea notes;

    public StackPane headerIcon(){ return headerIcon; }
    public Label title(){ return title; }
    public Label subtitle(){ return subtitle; }
    public GridPane formGrid(){ return formGrid; }
    public VBox lineBox(){ return lineBox; }
    public Button addLine(){ return btnAddLine; }
    public TextArea notes(){ return notes; }
}
