package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.controller.DashboardController;
import org.example.documentstudio.model.*;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.example.documentstudio.service.PdfPermissionException;
import org.example.documentstudio.service.PdfTemplateRenderer;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.documentstudio.util.PdfPreviewSupport;
import org.example.navigation.ScreenLifecycle;
import org.example.util.ModernDialog;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Universal Document Library and entry point for DSE ERP 7.3.0. */
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
        String purchase = TemplateStorageService.defaultFor(DocumentType.PURCHASE_INVOICE)
                .map(DocumentTemplate::getName).orElse("Built-in Purchase");
        String quotation = TemplateStorageService.defaultFor(DocumentType.QUOTATION)
                .map(DocumentTemplate::getName).orElse("Built-in Quotation");
        lblPurchaseDefault.setText("Purchase: " + purchase + "   •   Quotation: " + quotation);
        applyFilters();
    }

    private void applyFilters() {
        if (cards == null) return;
        String search = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        Object type = cmbType.getValue();
        Object status = cmbStatus.getValue();
        List<DocumentTemplate> filtered = all.stream()
                .filter(t -> search.isBlank() || t.getName().toLowerCase().contains(search)
                        || t.getDocumentType().label().toLowerCase().contains(search)
                        || t.getCategory().label().toLowerCase().contains(search))
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

    /** Importing normally starts as a General PDF; ERP data can be connected later from the same editor. */
    @FXML
    private void importPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import PDF into Document Studio");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        TextInputDialog nameDialog = new TextInputDialog(stripExtension(file.getName()));
        nameDialog.setTitle("Document Name");
        nameDialog.setHeaderText("Import as a general editable PDF");
        nameDialog.setContentText("Document name:");
        var result = nameDialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;
        try {
            DocumentTemplate template = importPdfWithSecurity(file.toPath(), result.get().trim(), DocumentType.GENERAL_PDF);
            if (template != null) openDesigner(template);
        } catch (Exception error) {
            ModernDialog.error(root, "Import failed", "The PDF could not be imported", rootMessage(error));
        }
    }

    @FXML
    private void createBlank() {
        TextInputDialog nameDialog = new TextInputDialog("Untitled Document");
        nameDialog.setTitle("Blank PDF");
        nameDialog.setHeaderText("Create a blank A4 PDF document");
        nameDialog.setContentText("Document name:");
        var result = nameDialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;
        try {
            openDesigner(TemplateStorageService.createBlank(result.get().trim(), DocumentType.GENERAL_PDF));
        } catch (Exception error) {
            ModernDialog.error(root, "Document could not be created", "Document Studio", rootMessage(error));
        }
    }

    @FXML
    private void createErpTemplate() {
        List<DocumentType> erpTypes = Arrays.stream(DocumentType.values()).filter(DocumentType::isErpConnected).toList();
        ChoiceDialog<DocumentType> typeDialog = new ChoiceDialog<>(DocumentType.PURCHASE_INVOICE, erpTypes);
        typeDialog.setTitle("ERP Document Template");
        typeDialog.setHeaderText("Choose the ERP document type");
        typeDialog.setContentText("Document type:");
        var selectedType = typeDialog.showAndWait();
        if (selectedType.isEmpty()) return;
        DocumentType type = selectedType.get();

        ChoiceDialog<String> sourceDialog = new ChoiceDialog<>("Import Existing PDF", "Import Existing PDF", "Start Blank A4");
        sourceDialog.setTitle("Template Source");
        sourceDialog.setHeaderText("How would you like to start " + type.label() + "?");
        sourceDialog.setContentText("Start from:");
        var source = sourceDialog.showAndWait();
        if (source.isEmpty()) return;

        try {
            if (source.get().startsWith("Import")) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Import " + type.label() + " PDF");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
                var file = chooser.showOpenDialog(root.getScene().getWindow());
                if (file == null) return;
                String name = askName(stripExtension(file.getName()), type.label());
                if (name == null) return;
                DocumentTemplate template = importPdfWithSecurity(file.toPath(), name, type);
                if (template != null) openDesigner(template);
            } else {
                String name = askName("New " + type.label(), type.label());
                if (name == null) return;
                openDesigner(TemplateStorageService.createBlank(name, type));
            }
        } catch (Exception error) {
            ModernDialog.error(root, "Template could not be created", "Document Studio", rootMessage(error));
        }
    }

    private DocumentTemplate importPdfWithSecurity(Path source, String name, DocumentType type) throws Exception {
        try {
            return TemplateStorageService.importPdf(source, name, type, "");
        } catch (InvalidPasswordException passwordRequired) {
            Optional<String> password = askPdfPassword(
                    "Protected PDF",
                    "This PDF requires a password before it can be imported.",
                    "Enter the PDF password. It is used only for this import and is never saved.");
            if (password.isEmpty()) return null;
            try {
                return TemplateStorageService.importPdf(source, name, type, password.get());
            } catch (PdfPermissionException restricted) {
                Optional<String> ownerPassword = askPdfPassword(
                        "Owner password required",
                        "The supplied password opens the PDF, but editing or text extraction is restricted.",
                        "Enter the owner password to create an editable private workspace copy.");
                if (ownerPassword.isEmpty()) return null;
                return TemplateStorageService.importPdf(source, name, type, ownerPassword.get());
            }
        } catch (PdfPermissionException restricted) {
            Optional<String> ownerPassword = askPdfPassword(
                    "Owner password required",
                    "This PDF can be viewed, but its security permissions restrict editing or text extraction.",
                    "Enter the owner password to import it as an editable Document Studio copy.");
            if (ownerPassword.isEmpty()) return null;
            return TemplateStorageService.importPdf(source, name, type, ownerPassword.get());
        }
    }

    private Optional<String> askPdfPassword(String title, String header, String message) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType unlock = new ButtonType("Unlock & Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(unlock, ButtonType.CANCEL);
        PasswordField password = new PasswordField();
        password.setPromptText("PDF password");
        Label help = new Label(message);
        help.setWrapText(true);
        VBox content = new VBox(9, help, password);
        content.setPrefWidth(430);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == unlock ? password.getText() : null);
        Platform.runLater(password::requestFocus);
        return dialog.showAndWait();
    }

    private String askName(String initial, String type) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle("Template Name");
        dialog.setHeaderText("Name your " + type + " template");
        dialog.setContentText("Template name:");
        return dialog.showAndWait().map(String::trim).filter(v -> !v.isBlank()).orElse(null);
    }

    private VBox card(DocumentTemplate template) {
        VBox card = new VBox(9);
        card.getStyleClass().add("doc-template-card");
        card.setPrefWidth(300); card.setMinWidth(270); card.setMaxWidth(330);

        StackPane preview = new StackPane();
        preview.getStyleClass().add("doc-template-preview"); preview.setPrefHeight(165);
        Label loading = new Label("PDF Preview"); loading.getStyleClass().add("doc-template-preview-placeholder");
        preview.getChildren().add(loading); loadThumbnail(template, preview);

        Label name = new Label(template.getName()); name.getStyleClass().add("doc-template-name"); name.setWrapText(true);
        HBox identity = new HBox(6);
        Label category = badge(template.getCategory().label().toUpperCase(), template.getCategory() == TemplateCategory.GENERAL_PDF ? "doc-template-general" : "doc-template-erp");
        Label type = new Label(template.getDocumentType().label()); type.getStyleClass().add("doc-template-type");
        identity.getChildren().addAll(category, type);

        HBox badges = new HBox(6);
        badges.getChildren().add(badge(template.getStatus().name(), "doc-template-status-" + template.getStatus().name().toLowerCase()));
        if (template.isDefaultTemplate()) badges.getChildren().add(badge("★ DEFAULT", "doc-template-default"));
        badges.getChildren().add(badge("v" + template.getVersion(), "doc-template-version"));

        HBox actions = new HBox(7);
        Button edit = new Button("Edit"); edit.setOnAction(e -> openDesigner(template));
        Button previewButton = new Button("Preview"); previewButton.setOnAction(e -> previewTemplate(template));
        MenuButton more = new MenuButton("More");
        MenuItem setDefault = new MenuItem("Set as Default");
        setDefault.setDisable(!template.isErpConnected()); setDefault.setOnAction(e -> setDefault(template));
        MenuItem duplicate = new MenuItem("Duplicate"); duplicate.setOnAction(e -> duplicate(template));
        MenuItem archive = new MenuItem(template.getStatus() == TemplateStatus.ARCHIVED ? "Keep Archived" : "Archive");
        archive.setDisable(template.getStatus() == TemplateStatus.ARCHIVED); archive.setOnAction(e -> archive(template));
        MenuItem delete = new MenuItem("Delete"); delete.setOnAction(e -> delete(template));
        more.getItems().addAll(setDefault, duplicate, archive, new SeparatorMenuItem(), delete);
        edit.getStyleClass().addAll("approved-button", "approved-primary-button");
        previewButton.getStyleClass().addAll("approved-button", "approved-secondary-button");
        more.getStyleClass().add("approved-menu-button");
        HBox.setHgrow(edit, Priority.ALWAYS); edit.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(previewButton, Priority.ALWAYS); previewButton.setMaxWidth(Double.MAX_VALUE);
        actions.getChildren().addAll(edit, previewButton, more);
        card.getChildren().addAll(preview, name, identity, badges, actions);
        return card;
    }

    private void loadThumbnail(DocumentTemplate template, StackPane preview) {
        CompletableFuture.supplyAsync(() -> {
            try { return PdfPreviewSupport.renderPage(TemplateStorageService.sourcePdf(template), 0, 66); }
            catch (Exception ignored) { return null; }
        }).thenAccept(image -> {
            if (image == null) return;
            Platform.runLater(() -> {
                ImageView view = new ImageView(image); view.setPreserveRatio(true); view.setFitHeight(153); view.setFitWidth(280);
                view.getStyleClass().add("doc-template-thumbnail"); preview.getChildren().setAll(view);
            });
        });
    }

    private Label badge(String text, String style) {
        Label label = new Label(text); label.getStyleClass().addAll("doc-template-badge", style); return label;
    }

    private void openDesigner(DocumentTemplate template) {
        DocumentStudioContext.open(template.getId());
        DashboardController.navigateFromDocumentStudio("Document Studio", "/fxml/pages/PdfDesigner.fxml");
    }

    private void setDefault(DocumentTemplate template) {
        if (!template.isErpConnected()) {
            ModernDialog.info(root, "General PDF", "No default ERP binding", "General PDF documents are saved/exported directly and are not used as automatic ERP templates.");
            return;
        }
        try {
            TemplateStorageService.setDefault(template.getId());
            ModernDialog.success(root, "Default template updated", template.getName() + " is now the default for " + template.getDocumentType().label() + ".");
            refresh();
        } catch (Exception error) { ModernDialog.error(root, "Could not set default", "Document Studio", rootMessage(error)); }
    }

    private void duplicate(DocumentTemplate template) {
        try { DocumentTemplate copy = TemplateStorageService.duplicate(template); refresh(); ModernDialog.success(root, "Document duplicated", copy.getName() + " is ready to edit."); }
        catch (Exception error) { ModernDialog.error(root, "Could not duplicate", "Document Studio", rootMessage(error)); }
    }

    private void archive(DocumentTemplate template) {
        if (!ModernDialog.confirm(root, "Archive Document", "Archive " + template.getName() + "?", "It remains in the library history but cannot be used as the default.")) return;
        try { TemplateStorageService.archive(template); refresh(); }
        catch (Exception error) { ModernDialog.error(root, "Could not archive", "Document Studio", rootMessage(error)); }
    }

    private void delete(DocumentTemplate template) {
        if (!ModernDialog.confirm(root, "Delete Document", "Delete " + template.getName() + "?", "This removes the workspace copy, designer metadata and assets. The original PDF you imported outside DSE ERP is not touched.")) return;
        try { TemplateStorageService.delete(template); refresh(); }
        catch (Exception error) { ModernDialog.error(root, "Could not delete", "Document Studio", rootMessage(error)); }
    }

    private void previewTemplate(DocumentTemplate template) {
        try {
            Path output = org.example.config.WorkspaceManager.getTempFolder().resolve("document-studio-sample-" + template.getId() + ".pdf");
            PdfTemplateRenderer.renderSample(template, output);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(output.toFile());
            else ModernDialog.info(root, "Preview generated", "PDF ready", output.toString());
        } catch (Exception error) { ModernDialog.error(root, "Preview failed", "The document could not be rendered", rootMessage(error)); }
    }

    private static String stripExtension(String value) { int dot = value.lastIndexOf('.'); return dot > 0 ? value.substring(0, dot) : value; }
    private static String rootMessage(Throwable error) {
        Throwable root = error; while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }
}
