package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.controller.DashboardController;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateStatus;
import org.example.documentstudio.service.PdfTemplateRenderer;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.documentstudio.util.PdfPreviewSupport;
import org.example.navigation.ScreenLifecycle;
import org.example.util.IconFactory;
import org.example.util.ModernDialog;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Template library and entry point for the 7.2.5 Document Studio. */
public class DocumentStudioController implements ScreenLifecycle {
    @FXML private VBox root;
    @FXML private FlowPane cards;
    @FXML private VBox emptyState;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<Object> cmbType;
    @FXML private ComboBox<Object> cmbStatus;
    @FXML private Label lblTotal;
    @FXML private Label lblActive;
    @FXML private Label lblPurchaseDefault;

    private List<DocumentTemplate> all = List.of();

    @FXML
    public void initialize() {
        cmbType.setItems(FXCollections.observableArrayList());
        cmbType.getItems().add("All document types");
        cmbType.getItems().addAll(DocumentType.values());
        cmbType.getSelectionModel().selectFirst();
        cmbStatus.setItems(FXCollections.observableArrayList("All status", TemplateStatus.DRAFT,
                TemplateStatus.ACTIVE, TemplateStatus.ARCHIVED));
        cmbStatus.getSelectionModel().selectFirst();
        txtSearch.textProperty().addListener((obs, old, value) -> applyFilters());
        cmbType.valueProperty().addListener((obs, old, value) -> applyFilters());
        cmbStatus.valueProperty().addListener((obs, old, value) -> applyFilters());
        refresh();
    }

    @Override public void onScreenShown(boolean reused) { refresh(); }

    @FXML
    public void refresh() {
        all = TemplateStorageService.listAll();
        lblTotal.setText(Integer.toString(all.size()));
        lblActive.setText(Long.toString(all.stream().filter(t -> t.getStatus() == TemplateStatus.ACTIVE).count()));
        lblPurchaseDefault.setText(TemplateStorageService.defaultFor(DocumentType.PURCHASE_INVOICE)
                .map(DocumentTemplate::getName).orElse("Built-in DSE ERP"));
        applyFilters();
    }

    private void applyFilters() {
        if (cards == null) return;
        String search = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        Object type = cmbType.getValue();
        Object status = cmbStatus.getValue();
        List<DocumentTemplate> filtered = all.stream()
                .filter(t -> search.isBlank() || t.getName().toLowerCase().contains(search)
                        || t.getDocumentType().label().toLowerCase().contains(search))
                .filter(t -> !(type instanceof DocumentType dt) || t.getDocumentType() == dt)
                .filter(t -> !(status instanceof TemplateStatus st) || t.getStatus() == st)
                .toList();
        cards.getChildren().clear();
        for (DocumentTemplate template : filtered) cards.getChildren().add(card(template));
        boolean empty = filtered.isEmpty();
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);
        cards.setVisible(!empty);
        cards.setManaged(!empty);
    }

    @FXML
    private void importPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import PDF Template");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        DocumentType type = DocumentType.PURCHASE_INVOICE;
        TextInputDialog nameDialog = new TextInputDialog(stripExtension(file.getName()));
        nameDialog.setTitle("Template Name");
        nameDialog.setHeaderText("Name your imported " + type.label() + " template");
        nameDialog.setContentText("Template name:");
        var result = nameDialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;
        try {
            DocumentTemplate template = TemplateStorageService.importPdf(file.toPath(), result.get().trim(), type);
            openDesigner(template);
        } catch (Exception error) {
            ModernDialog.error(root, "Import failed", "The PDF could not be imported", rootMessage(error));
        }
    }

    @FXML
    private void createBlank() {
        DocumentType type = DocumentType.PURCHASE_INVOICE;
        TextInputDialog nameDialog = new TextInputDialog("New " + type.label());
        nameDialog.setTitle("Blank Template");
        nameDialog.setHeaderText("Create a blank A4 template");
        nameDialog.setContentText("Template name:");
        var result = nameDialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;
        try {
            openDesigner(TemplateStorageService.createBlank(result.get().trim(), type));
        } catch (Exception error) {
            ModernDialog.error(root, "Template could not be created", "Document Studio", rootMessage(error));
        }
    }

    private VBox card(DocumentTemplate template) {
        VBox card = new VBox(9);
        card.getStyleClass().add("doc-template-card");
        card.setPrefWidth(285);
        card.setMinWidth(260);
        card.setMaxWidth(310);

        StackPane preview = new StackPane();
        preview.getStyleClass().add("doc-template-preview");
        preview.setPrefHeight(165);
        Label loading = new Label("PDF Preview");
        loading.getStyleClass().add("doc-template-preview-placeholder");
        preview.getChildren().add(loading);
        loadThumbnail(template, preview);

        Label name = new Label(template.getName());
        name.getStyleClass().add("doc-template-name");
        name.setWrapText(true);
        Label type = new Label(template.getDocumentType().label());
        type.getStyleClass().add("doc-template-type");

        HBox badges = new HBox(6);
        Label status = badge(template.getStatus().name(), "doc-template-status-" + template.getStatus().name().toLowerCase());
        badges.getChildren().add(status);
        if (template.isDefaultTemplate()) badges.getChildren().add(badge("★ DEFAULT", "doc-template-default"));
        badges.getChildren().add(badge("v" + template.getVersion(), "doc-template-version"));

        HBox actions = new HBox(7);
        Button edit = new Button("Edit"); edit.setOnAction(e -> openDesigner(template));
        Button previewButton = new Button("Preview"); previewButton.setOnAction(e -> previewTemplate(template));
        MenuButton more = new MenuButton("More");
        MenuItem setDefault = new MenuItem("Set as Default"); setDefault.setOnAction(e -> setDefault(template));
        MenuItem duplicate = new MenuItem("Duplicate"); duplicate.setOnAction(e -> duplicate(template));
        MenuItem archive = new MenuItem(template.getStatus() == TemplateStatus.ARCHIVED ? "Keep Archived" : "Archive");
        archive.setDisable(template.getStatus() == TemplateStatus.ARCHIVED);
        archive.setOnAction(e -> archive(template));
        MenuItem delete = new MenuItem("Delete Template"); delete.setOnAction(e -> delete(template));
        more.getItems().addAll(setDefault, duplicate, archive, new SeparatorMenuItem(), delete);
        edit.getStyleClass().addAll("approved-button", "approved-primary-button");
        previewButton.getStyleClass().addAll("approved-button", "approved-secondary-button");
        more.getStyleClass().add("approved-menu-button");
        HBox.setHgrow(edit, Priority.ALWAYS); edit.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(previewButton, Priority.ALWAYS); previewButton.setMaxWidth(Double.MAX_VALUE);
        actions.getChildren().addAll(edit, previewButton, more);

        card.getChildren().addAll(preview, name, type, badges, actions);
        return card;
    }

    private void loadThumbnail(DocumentTemplate template, StackPane preview) {
        CompletableFuture.supplyAsync(() -> {
            try { return PdfPreviewSupport.renderPage(TemplateStorageService.sourcePdf(template), 0, 66); }
            catch (Exception ignored) { return null; }
        }).thenAccept(image -> {
            if (image == null) return;
            Platform.runLater(() -> {
                ImageView view = new ImageView(image);
                view.setPreserveRatio(true); view.setFitHeight(153); view.setFitWidth(265);
                view.getStyleClass().add("doc-template-thumbnail");
                preview.getChildren().setAll(view);
            });
        });
    }

    private Label badge(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().addAll("doc-template-badge", style);
        return label;
    }

    private void openDesigner(DocumentTemplate template) {
        DocumentStudioContext.open(template.getId());
        DashboardController.navigateFromDocumentStudio("PDF Designer", "/fxml/pages/PdfDesigner.fxml");
    }

    private void setDefault(DocumentTemplate template) {
        try {
            TemplateStorageService.setDefault(template.getId());
            ModernDialog.success(root, "Default template updated", template.getName() + " will now be used automatically for " + template.getDocumentType().label() + ".");
            refresh();
        } catch (Exception error) {
            ModernDialog.error(root, "Could not set default", "Document Studio", rootMessage(error));
        }
    }

    private void duplicate(DocumentTemplate template) {
        try {
            DocumentTemplate copy = TemplateStorageService.duplicate(template);
            refresh();
            ModernDialog.success(root, "Template duplicated", copy.getName() + " is ready to edit.");
        } catch (Exception error) {
            ModernDialog.error(root, "Could not duplicate template", "Document Studio", rootMessage(error));
        }
    }

    private void archive(DocumentTemplate template) {
        if (!ModernDialog.confirm(root, "Archive Template", "Archive " + template.getName() + "?",
                "The template will remain in history but cannot be selected as the active default.")) return;
        try { TemplateStorageService.archive(template); refresh(); }
        catch (Exception error) { ModernDialog.error(root, "Could not archive template", "Document Studio", rootMessage(error)); }
    }

    private void delete(DocumentTemplate template) {
        if (!ModernDialog.confirm(root, "Delete Template", "Delete " + template.getName() + "?",
                "This removes the imported PDF and designer metadata from this workspace. This action cannot be undone.")) return;
        try { TemplateStorageService.delete(template); refresh(); }
        catch (Exception error) { ModernDialog.error(root, "Could not delete template", "Document Studio", rootMessage(error)); }
    }

    private void previewTemplate(DocumentTemplate template) {
        try {
            Path output = org.example.config.WorkspaceManager.getTempFolder()
                    .resolve("document-studio-sample-" + template.getId() + ".pdf");
            PdfTemplateRenderer.renderSample(template, output);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(output.toFile());
            else ModernDialog.info(root, "Preview generated", "PDF ready", output.toString());
        } catch (Exception error) {
            ModernDialog.error(root, "Preview failed", "The template could not be rendered", rootMessage(error));
        }
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }
}
