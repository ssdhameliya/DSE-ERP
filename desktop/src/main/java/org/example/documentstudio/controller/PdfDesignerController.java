package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.example.config.WorkspaceManager;
import org.example.controller.DashboardController;
import org.example.dao.PurchaseDAO;
import org.example.documentstudio.model.*;
import org.example.documentstudio.service.PdfTemplateRenderer;
import org.example.documentstudio.service.TemplateFieldCatalog;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.documentstudio.util.PdfPreviewSupport;
import org.example.model.Purchase;
import org.example.navigation.ScreenLifecycle;
import org.example.util.ModernDialog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Canva-style PDF overlay designer introduced in 7.2.5.
 *
 * <p>The imported PDF remains immutable. Every edit is an overlay object whose
 * x/y/width/height are stored in PDF points. The same saved template is then
 * consumed by InvoicePdfService, so Preview, Email, WhatsApp and Print share a
 * single document design.</p>
 */
public class PdfDesignerController implements ScreenLifecycle {
    private static final double BASE_SCALE = 1.15;

    @FXML private javafx.scene.layout.BorderPane root;
    @FXML private Label lblTemplateName, lblTemplateMeta, lblSaveState, lblZoom, lblPageSize, lblSelection;
    @FXML private ComboBox<Purchase> cmbSampleInvoice;
    @FXML private Button btnPreview;
    @FXML private Slider zoomSlider;
    @FXML private CheckBox chkSnap;
    @FXML private TextField txtFieldSearch;
    @FXML private ListView<TemplateFieldDefinition> lstFields;
    @FXML private ListView<String> lstPages;
    @FXML private ScrollPane canvasScroll;
    @FXML private StackPane canvasHolder;
    @FXML private Pane canvasPane;
    @FXML private TextArea txtContent;
    @FXML private TextField txtX, txtY, txtWidth, txtHeight, txtFontSize, txtTableColumns, txtRowHeight;
    @FXML private CheckBox chkBold, chkLocked;
    @FXML private ColorPicker colorText, colorFill, colorStroke;

    private DocumentTemplate template;
    private Path sourcePdf;
    private Path previewPdf;
    private boolean previewMode;
    private int pageIndex;
    private double pageWidth = 595;
    private double pageHeight = 842;
    private int sourcePageCount = 1;
    private double scale = BASE_SCALE;
    private TemplateElement selected;
    private String selectedId;
    private final Deque<List<TemplateElement>> undo = new ArrayDeque<>();
    private final Deque<List<TemplateElement>> redo = new ArrayDeque<>();
    private final AtomicInteger renderSequence = new AtomicInteger();
    private boolean dragging;
    private double dragSceneX, dragSceneY, dragStartX, dragStartY;

    @FXML
    public void initialize() {
        String id = DocumentStudioContext.consume();
        template = TemplateStorageService.find(id).orElse(null);
        if (template == null) {
            Platform.runLater(() -> {
                ModernDialog.error(root, "Template unavailable", "Document Studio", "The selected template could not be found.");
                backToLibrary();
            });
            return;
        }
        try {
            sourcePdf = TemplateStorageService.sourcePdf(template);
            var size = PdfPreviewSupport.pageSize(sourcePdf, 0);
            pageWidth = size.width(); pageHeight = size.height(); sourcePageCount = size.pageCount();
        } catch (Exception error) {
            Platform.runLater(() -> ModernDialog.error(root, "PDF could not be opened", "Document Studio", rootMessage(error)));
        }

        lblTemplateName.setText(template.getName());
        refreshMeta();
        configureFields();
        configurePages(sourcePageCount);
        configureSampleInvoices();
        configureProperties();
        zoomSlider.valueProperty().addListener((obs, old, value) -> {
            scale = BASE_SCALE * value.doubleValue() / 100.0;
            lblZoom.setText(Math.round(value.doubleValue()) + "%");
            renderCanvas();
        });
        lstPages.getSelectionModel().selectedIndexProperty().addListener((obs, old, value) -> {
            if (value.intValue() >= 0 && value.intValue() != pageIndex) {
                pageIndex = value.intValue(); selected = null; selectedId = null; renderCanvas();
            }
        });
        Platform.runLater(this::installKeyboardShortcuts);
        renderCanvas();
    }

    @Override public void onScreenShown(boolean reused) { if (template != null) renderCanvas(); }

    private void configureFields() {
        lstFields.setItems(FXCollections.observableArrayList(TemplateFieldCatalog.purchaseFields()));
        lstFields.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(TemplateFieldDefinition item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                setText(item.category() + "  •  " + item.label());
            }
        });
        lstFields.setOnMouseClicked(event -> { if (event.getClickCount() == 2) addSelectedField(); });
        txtFieldSearch.textProperty().addListener((obs, old, value) -> {
            String q = value == null ? "" : value.trim().toLowerCase();
            lstFields.setItems(FXCollections.observableArrayList(TemplateFieldCatalog.purchaseFields().stream()
                    .filter(f -> q.isBlank() || f.label().toLowerCase().contains(q)
                            || f.category().toLowerCase().contains(q) || f.key().toLowerCase().contains(q))
                    .toList()));
        });
    }

    private void configurePages(int count) {
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < Math.max(1, count); i++) pages.add("Page " + (i + 1));
        lstPages.setItems(FXCollections.observableArrayList(pages));
        pageIndex = Math.max(0, Math.min(pageIndex, pages.size() - 1));
        lstPages.getSelectionModel().select(pageIndex);
    }

    private void configureSampleInvoices() {
        cmbSampleInvoice.setConverter(new StringConverter<>() {
            @Override public String toString(Purchase purchase) { return purchase == null ? "Sample Data" : purchase.getInvoiceNo(); }
            @Override public Purchase fromString(String string) { return null; }
        });
        CompletableFuture.supplyAsync(() -> {
            try { return new PurchaseDAO().getAll(); }
            catch (Exception ignored) { return List.<Purchase>of(); }
        }).thenAccept(list -> Platform.runLater(() -> {
            cmbSampleInvoice.getItems().clear();
            cmbSampleInvoice.getItems().add(null);
            cmbSampleInvoice.getItems().addAll(list.stream().filter(Objects::nonNull).limit(150).toList());
            cmbSampleInvoice.getSelectionModel().selectFirst();
        }));
    }

    private void configureProperties() {
        colorText.setValue(Color.web("#172033"));
        colorFill.setValue(Color.WHITE);
        colorStroke.setValue(Color.web("#94A3B8"));
        clearProperties();
    }

    private void installKeyboardShortcuts() {
        if (root.getScene() == null) return;
        root.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        root.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), this::redo);
        root.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), this::duplicateSelected);
        root.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DELETE && selected != null && !isTextInput(event.getTarget())) {
                deleteSelected(); event.consume();
            }
        });
    }

    private boolean isTextInput(Object target) {
        return target instanceof TextInputControl || target instanceof ComboBoxBase<?>;
    }

    @FXML private void backToLibrary() {
        DashboardController.navigateFromDocumentStudio("Purchase Document Studio", "/fxml/pages/DocumentStudio.fxml");
    }

    @FXML private void addText() {
        TextInputDialog dialog = new TextInputDialog("New Text");
        dialog.setTitle("Add Text"); dialog.setHeaderText("Add text to the PDF template"); dialog.setContentText("Text:");
        dialog.showAndWait().filter(v -> !v.isBlank()).ifPresent(value -> {
            TemplateElement e = newElement(ElementType.TEXT, 170, 34);
            e.setText(value); e.setBold(false); addElement(e);
        });
    }

    @FXML private void addImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            TemplateElement e = newElement(ElementType.IMAGE, 150, 90);
            e.setImagePath(TemplateStorageService.importAsset(template, file.toPath()));
            addElement(e);
        } catch (Exception error) {
            ModernDialog.error(root, "Image could not be added", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void addWhiteout() { addElement(newElement(ElementType.WHITEOUT, 180, 42)); }
    @FXML private void addRectangle() {
        TemplateElement e = newElement(ElementType.RECTANGLE, 180, 70);
        e.setFillColor("#FFFFFF"); e.setStrokeColor("#2563EB"); addElement(e);
    }
    @FXML private void addLine() {
        TemplateElement e = newElement(ElementType.LINE, 180, 1); e.setStrokeColor("#64748B"); addElement(e);
    }
    @FXML private void addItemTable() {
        TemplateElement e = newElement(ElementType.ITEM_TABLE, Math.max(260, pageWidth - 70), Math.min(330, pageHeight * 0.4));
        e.setX(35); e.setY(Math.min(300, pageHeight * 0.35)); e.setRowHeight(22); e.setHeaderHeight(24);
        addElement(e);
    }

    @FXML private void addSelectedField() {
        TemplateFieldDefinition field = lstFields.getSelectionModel().getSelectedItem();
        if (field == null) {
            ModernDialog.info(root, "Choose a field", "ERP Data", "Select a company, purchase, supplier or totals field first.");
            return;
        }
        TemplateElement e = newElement(field.image() ? ElementType.IMAGE_FIELD : ElementType.FIELD,
                field.image() ? 145 : 185, field.image() ? 80 : 30);
        e.setFieldKey(field.key()); e.setText(field.label());
        addElement(e);
    }

    private TemplateElement newElement(ElementType type, double width, double height) {
        double x = Math.max(20, (pageWidth - width) / 2.0);
        double y = Math.max(20, Math.min(pageHeight - height - 20, pageHeight * 0.22));
        return TemplateElement.of(type, pageIndex, x, y, width, height);
    }

    private void addElement(TemplateElement e) {
        if (previewMode) return;
        checkpoint();
        List<TemplateElement> updated = new ArrayList<>(template.getElements());
        updated.add(e); template.setElements(updated);
        selectedId = e.getId(); selected = e;
        autosave(); renderCanvas();
    }

    @FXML private void duplicateSelected() {
        if (selected == null || previewMode) return;
        checkpoint();
        TemplateElement copy = selected.copy();
        copy.setX(Math.min(pageWidth - copy.getWidth(), selected.getX() + 12));
        copy.setY(Math.min(pageHeight - copy.getHeight(), selected.getY() + 12));
        List<TemplateElement> updated = new ArrayList<>(template.getElements()); updated.add(copy); template.setElements(updated);
        selected = copy; selectedId = copy.getId(); autosave(); renderCanvas();
    }

    @FXML private void deleteSelected() {
        if (selected == null || previewMode) return;
        if (selected.isLocked()) {
            ModernDialog.info(root, "Object is locked", "Unlock before deleting", "Clear the Lock option in Properties, then apply the change.");
            return;
        }
        checkpoint();
        String id = selected.getId();
        template.setElements(template.getElements().stream().filter(e -> !e.getId().equals(id)).toList());
        selected = null; selectedId = null; autosave(); renderCanvas();
    }

    @FXML private void applyProperties() {
        if (selected == null || previewMode) return;
        try {
            checkpoint();
            if (selected.getType() == ElementType.TEXT) selected.setText(txtContent.getText());
            selected.setX(parse(txtX, selected.getX())); selected.setY(parse(txtY, selected.getY()));
            selected.setWidth(parse(txtWidth, selected.getWidth())); selected.setHeight(parse(txtHeight, selected.getHeight()));
            selected.setFontSize(parse(txtFontSize, selected.getFontSize())); selected.setBold(chkBold.isSelected()); selected.setLocked(chkLocked.isSelected());
            selected.setTextColor(hex(colorText.getValue())); selected.setFillColor(hex(colorFill.getValue())); selected.setStrokeColor(hex(colorStroke.getValue()));
            if (selected.getType() == ElementType.ITEM_TABLE) {
                List<String> columns = Arrays.stream(txtTableColumns.getText().split(","))
                        .map(String::trim).filter(s -> !s.isBlank()).toList();
                if (!columns.isEmpty()) selected.setTableColumns(columns);
                selected.setRowHeight(parse(txtRowHeight, selected.getRowHeight()));
            }
            autosave(); renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Properties could not be applied", "Check the numeric values", rootMessage(error));
        }
    }

    @FXML private void saveDraft() {
        if (template == null) return;
        try {
            TemplateStorageService.save(template);
            lblSaveState.setText("Saved ✓");
            ModernDialog.success(root, "Template saved", template.getName() + " was saved to this workspace.");
        } catch (Exception error) { ModernDialog.error(root, "Save failed", "Document Studio", rootMessage(error)); }
    }

    @FXML private void saveAndDefault() {
        if (template == null) return;
        try {
            TemplateStorageService.activateAndSetDefault(template);
            refreshMeta(); lblSaveState.setText("Saved & Default ✓");
            ModernDialog.success(root, "Default Purchase template updated",
                    template.getName() + " will now be used automatically by Purchase PDF generation, including Email and WhatsApp attachments. Sales PDF generation remains unchanged.");
        } catch (Exception error) { ModernDialog.error(root, "Save failed", "Document Studio", rootMessage(error)); }
    }

    @FXML private void togglePreview() {
        if (template == null) return;
        if (previewMode) {
            previewMode = false; previewPdf = null; btnPreview.setText("Preview Data");
            configurePages(sourcePageCount); renderCanvas(); return;
        }
        try {
            TemplateStorageService.save(template);
            previewPdf = WorkspaceManager.getTempFolder().resolve("document-studio-preview-" + template.getId() + ".pdf");
            Purchase selectedPurchase = cmbSampleInvoice.getValue();
            if (selectedPurchase == null) PdfTemplateRenderer.renderSample(template, previewPdf);
            else {
                Purchase full = new PurchaseDAO().getByInvoice(selectedPurchase.getInvoiceNo());
                if (full == null) throw new IllegalStateException("The selected invoice could not be loaded.");
                PdfTemplateRenderer.renderPurchase(template, full, previewPdf);
            }
            previewMode = true; selected = null; selectedId = null; btnPreview.setText("Back to Design");
            var info = PdfPreviewSupport.pageSize(previewPdf, 0); configurePages(info.pageCount()); renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Preview failed", "The template could not be rendered with data", rootMessage(error));
        }
    }

    @FXML private void exportTestPdf() {
        if (template == null) return;
        FileChooser chooser = new FileChooser(); chooser.setTitle("Export Test PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(template.getName().replaceAll("[^A-Za-z0-9._-]", "-") + "-preview.pdf");
        var file = chooser.showSaveDialog(root.getScene().getWindow()); if (file == null) return;
        try {
            TemplateStorageService.save(template);
            Purchase selectedPurchase = cmbSampleInvoice.getValue();
            if (selectedPurchase == null) PdfTemplateRenderer.renderSample(template, file.toPath());
            else {
                Purchase full = new PurchaseDAO().getByInvoice(selectedPurchase.getInvoiceNo());
                PdfTemplateRenderer.renderPurchase(template, full, file.toPath());
            }
            ModernDialog.success(root, "PDF exported", file.getAbsolutePath());
        } catch (Exception error) { ModernDialog.error(root, "Export failed", "Document Studio", rootMessage(error)); }
    }

    @FXML private void undo() {
        if (undo.isEmpty() || previewMode) return;
        redo.push(copyElements(template.getElements()));
        template.setElements(undo.pop()); selected = null; selectedId = null; autosave(); renderCanvas();
    }

    @FXML private void redo() {
        if (redo.isEmpty() || previewMode) return;
        undo.push(copyElements(template.getElements()));
        template.setElements(redo.pop()); selected = null; selectedId = null; autosave(); renderCanvas();
    }

    private void checkpoint() {
        undo.push(copyElements(template.getElements()));
        while (undo.size() > 40) undo.removeLast();
        redo.clear();
    }

    private void autosave() {
        lblSaveState.setText("Saving…");
        try { TemplateStorageService.save(template); lblSaveState.setText("Saved ✓"); }
        catch (Exception error) { lblSaveState.setText("Save failed"); }
        refreshMeta();
    }

    private void renderCanvas() {
        if (template == null || sourcePdf == null) return;
        Path pdf = previewMode && previewPdf != null ? previewPdf : sourcePdf;
        int sequence = renderSequence.incrementAndGet();
        try {
            var size = PdfPreviewSupport.pageSize(pdf, pageIndex);
            pageWidth = size.width(); pageHeight = size.height();
            lblPageSize.setText(String.format(Locale.ENGLISH, "%.0f × %.0f pt  •  Page %d/%d", pageWidth, pageHeight, pageIndex + 1, size.pageCount()));
            double canvasW = pageWidth * scale, canvasH = pageHeight * scale;
            canvasPane.setMinSize(canvasW, canvasH); canvasPane.setPrefSize(canvasW, canvasH); canvasPane.setMaxSize(canvasW, canvasH);
            canvasHolder.setMinSize(canvasW + 80, canvasH + 80); canvasHolder.setPrefSize(canvasW + 80, canvasH + 80);
            canvasPane.getChildren().clear();
            Label loading = new Label("Rendering page…"); loading.getStyleClass().add("pdf-designer-loading");
            loading.relocate(20, 20); canvasPane.getChildren().add(loading);
            CompletableFuture.supplyAsync(() -> {
                try { return PdfPreviewSupport.renderPage(pdf, pageIndex, 105); }
                catch (Exception ignored) { return null; }
            }).thenAccept(image -> Platform.runLater(() -> {
                if (sequence != renderSequence.get()) return;
                canvasPane.getChildren().clear();
                if (image != null) {
                    ImageView background = new ImageView(image); background.setPreserveRatio(false);
                    background.setFitWidth(canvasW); background.setFitHeight(canvasH); background.setMouseTransparent(true);
                    canvasPane.getChildren().add(background);
                }
                if (!previewMode) {
                    for (TemplateElement e : template.getElements()) if (e.getPageIndex() == pageIndex) canvasPane.getChildren().add(elementNode(e));
                    restoreSelectionReference();
                } else clearProperties();
            }));
        } catch (Exception error) {
            canvasPane.getChildren().setAll(new Label("Unable to render PDF page: " + rootMessage(error)));
        }
    }

    private Node elementNode(TemplateElement e) {
        Node visual;
        if (e.getType() == ElementType.LINE) {
            Line line = new Line(0, 0, e.getWidth() * scale, Math.max(1, e.getHeight() * scale));
            line.setStroke(Color.web(e.getStrokeColor())); line.setStrokeWidth(Math.max(1, e.getStrokeWidth() * scale));
            visual = line;
        } else if (e.getType() == ElementType.IMAGE && !e.getImagePath().isBlank()) {
            visual = customImage(e);
        } else {
            Label label = new Label(elementDisplay(e));
            label.setWrapText(true); label.setAlignment(Pos.CENTER_LEFT);
            label.setStyle(styleFor(e));
            visual = label;
        }
        StackPane wrapper = new StackPane(visual);
        wrapper.getStyleClass().addAll("pdf-designer-object", "pdf-object-" + e.getType().name().toLowerCase());
        wrapper.getProperties().put("templateElementId", e.getId());
        if (Objects.equals(e.getId(), selectedId)) wrapper.getStyleClass().add("pdf-designer-object-selected");
        if (e.isLocked()) wrapper.getStyleClass().add("pdf-designer-object-locked");
        wrapper.setLayoutX(e.getX() * scale); wrapper.setLayoutY(e.getY() * scale);
        wrapper.setPrefSize(Math.max(4, e.getWidth() * scale), Math.max(4, e.getHeight() * scale));
        wrapper.setMinSize(Math.max(4, e.getWidth() * scale), Math.max(4, e.getHeight() * scale));
        wrapper.setMaxSize(Math.max(4, e.getWidth() * scale), Math.max(4, e.getHeight() * scale));
        wrapper.setOnMousePressed(event -> {
            select(e); dragging = !e.isLocked(); dragSceneX = event.getSceneX(); dragSceneY = event.getSceneY(); dragStartX = e.getX(); dragStartY = e.getY();
            if (dragging) checkpoint(); event.consume();
        });
        wrapper.setOnMouseDragged(event -> {
            if (!dragging || e.isLocked()) return;
            double dx = (event.getSceneX() - dragSceneX) / scale, dy = (event.getSceneY() - dragSceneY) / scale;
            double nx = clamp(dragStartX + dx, 0, Math.max(0, pageWidth - e.getWidth()));
            double ny = clamp(dragStartY + dy, 0, Math.max(0, pageHeight - e.getHeight()));
            if (chkSnap.isSelected()) { nx = Math.round(nx / 4.0) * 4.0; ny = Math.round(ny / 4.0) * 4.0; }
            e.setX(nx); e.setY(ny); wrapper.setLayoutX(nx * scale); wrapper.setLayoutY(ny * scale); populateProperties(e); event.consume();
        });
        wrapper.setOnMouseReleased(event -> { if (dragging) autosave(); dragging = false; });
        wrapper.setOnMouseClicked(event -> { select(e); event.consume(); });
        return wrapper;
    }

    private Node customImage(TemplateElement e) {
        try {
            Path path = TemplateStorageService.resolveAsset(template, e.getImagePath());
            if (path != null && Files.isRegularFile(path)) {
                ImageView view = new ImageView(new Image(path.toUri().toString()));
                view.setPreserveRatio(false); view.setFitWidth(e.getWidth() * scale); view.setFitHeight(e.getHeight() * scale);
                return view;
            }
        } catch (Exception ignored) { }
        return new Label("Image");
    }

    private String elementDisplay(TemplateElement e) {
        return switch (e.getType()) {
            case TEXT -> e.getText();
            case FIELD, IMAGE_FIELD -> {
                TemplateFieldDefinition f = TemplateFieldCatalog.find(e.getFieldKey());
                yield (e.getType() == ElementType.IMAGE_FIELD ? "▧ " : "") + (f == null ? e.getFieldKey() : f.label());
            }
            case IMAGE -> "Image";
            case WHITEOUT -> "Whiteout / Replace Area";
            case RECTANGLE -> "Rectangle";
            case LINE -> "";
            case ITEM_TABLE -> "Dynamic Item Table\n" + e.getTableColumns().size() + " columns • Auto pagination";
        };
    }

    private String styleFor(TemplateElement e) {
        StringBuilder css = new StringBuilder();
        css.append("-fx-font-size:").append(Math.max(7, e.getFontSize() * scale)).append("px;");
        css.append("-fx-text-fill:").append(e.getTextColor()).append(";");
        if (e.isBold()) css.append("-fx-font-weight:bold;");
        if (e.getType() == ElementType.WHITEOUT) css.append("-fx-background-color:white;");
        else if (e.getType() == ElementType.RECTANGLE) css.append("-fx-background-color:").append(e.getFillColor()).append("; -fx-border-color:").append(e.getStrokeColor()).append(";");
        else if (e.getType() == ElementType.ITEM_TABLE) css.append("-fx-background-color:rgba(37,99,235,.10); -fx-border-color:#2563EB; -fx-alignment:center;");
        else if (e.getType() == ElementType.FIELD || e.getType() == ElementType.IMAGE_FIELD) css.append("-fx-background-color:rgba(37,99,235,.10); -fx-border-color:rgba(37,99,235,.65); -fx-padding:3 5;");
        return css.toString();
    }

    private void select(TemplateElement e) {
        selected = e; selectedId = e.getId(); populateProperties(e);
        for (Node node : canvasPane.getChildren()) {
            node.getStyleClass().remove("pdf-designer-object-selected");
            if (Objects.equals(node.getProperties().get("templateElementId"), selectedId))
                node.getStyleClass().add("pdf-designer-object-selected");
        }
    }

    private void restoreSelectionReference() {
        if (selectedId == null) return;
        selected = template.getElements().stream().filter(e -> e.getId().equals(selectedId)).findFirst().orElse(null);
        if (selected != null) populateProperties(selected); else clearProperties();
    }

    private void populateProperties(TemplateElement e) {
        lblSelection.setText(e.getType().name().replace('_', ' ') + (e.isLocked() ? "  •  LOCKED" : ""));
        boolean textEditable = e.getType() == ElementType.TEXT;
        txtContent.setDisable(!textEditable);
        if (e.getType() == ElementType.TEXT) txtContent.setText(e.getText());
        else if (e.getType() == ElementType.FIELD || e.getType() == ElementType.IMAGE_FIELD) {
            TemplateFieldDefinition f = TemplateFieldCatalog.find(e.getFieldKey()); txtContent.setText(f == null ? e.getFieldKey() : f.label() + "  [" + e.getFieldKey() + "]");
        } else txtContent.setText(e.getType() == ElementType.IMAGE ? e.getImagePath() : "");
        txtX.setText(fmt(e.getX())); txtY.setText(fmt(e.getY())); txtWidth.setText(fmt(e.getWidth())); txtHeight.setText(fmt(e.getHeight())); txtFontSize.setText(fmt(e.getFontSize()));
        chkBold.setSelected(e.isBold()); chkLocked.setSelected(e.isLocked());
        colorText.setValue(Color.web(e.getTextColor())); colorFill.setValue(Color.web(e.getFillColor())); colorStroke.setValue(Color.web(e.getStrokeColor()));
        txtTableColumns.setText(String.join(",", e.getTableColumns())); txtRowHeight.setText(fmt(e.getRowHeight()));
        boolean table = e.getType() == ElementType.ITEM_TABLE; txtTableColumns.setDisable(!table); txtRowHeight.setDisable(!table);
    }

    private void clearProperties() {
        if (lblSelection == null) return;
        lblSelection.setText(previewMode ? "Preview mode — displaying rendered ERP data" : "Select an object on the page");
        txtContent.clear(); txtX.clear(); txtY.clear(); txtWidth.clear(); txtHeight.clear(); txtFontSize.clear(); txtTableColumns.clear(); txtRowHeight.clear();
        txtContent.setDisable(true); txtTableColumns.setDisable(true); txtRowHeight.setDisable(true);
    }

    private void refreshMeta() {
        lblTemplateMeta.setText(template.getDocumentType().label() + "  •  " + template.getStatus()
                + "  •  v" + template.getVersion() + (template.isDefaultTemplate() ? "  •  ★ DEFAULT" : ""));
    }

    private static double parse(TextField field, double fallback) {
        String text = field.getText(); if (text == null || text.isBlank()) return fallback;
        return Double.parseDouble(text.trim());
    }
    private static String fmt(double v) { return Math.abs(v - Math.rint(v)) < .001 ? Long.toString(Math.round(v)) : String.format(Locale.ENGLISH, "%.1f", v); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static String hex(Color c) { return String.format("#%02X%02X%02X", Math.round(c.getRed()*255), Math.round(c.getGreen()*255), Math.round(c.getBlue()*255)); }

    private static List<TemplateElement> copyElements(List<TemplateElement> source) {
        List<TemplateElement> out = new ArrayList<>();
        for (TemplateElement e : source) {
            TemplateElement c = new TemplateElement(); c.setId(e.getId()); c.setType(e.getType()); c.setPageIndex(e.getPageIndex()); c.setX(e.getX()); c.setY(e.getY()); c.setWidth(e.getWidth()); c.setHeight(e.getHeight());
            c.setText(e.getText()); c.setFieldKey(e.getFieldKey()); c.setFontSize(e.getFontSize()); c.setBold(e.isBold()); c.setTextColor(e.getTextColor()); c.setFillColor(e.getFillColor()); c.setStrokeColor(e.getStrokeColor()); c.setStrokeWidth(e.getStrokeWidth()); c.setImagePath(e.getImagePath()); c.setLocked(e.isLocked()); c.setTableColumns(e.getTableColumns()); c.setRowHeight(e.getRowHeight()); c.setHeaderHeight(e.getHeaderHeight()); out.add(c);
        }
        return out;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error; while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }
}
