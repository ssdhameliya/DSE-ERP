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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.controller.DashboardController;
import org.example.documentstudio.model.*;
import org.example.documentstudio.service.DocumentFlowRegistry;
import org.example.documentstudio.service.PdfTemplateRenderer;
import org.example.documentstudio.service.DocumentDataService;
import org.example.documentstudio.service.PdfTextExtractionService;
import org.example.documentstudio.service.PdfImageExtractionService;
import org.example.documentstudio.service.PdfFormFieldService;
import org.example.documentstudio.service.TemplateFieldCatalog;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.documentstudio.util.PdfPreviewSupport;
import org.example.navigation.ScreenLifecycle;
import org.example.util.ModernDialog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Universal PDF and ERP template designer.
 *
 * <p>7.3.0 keeps the safe 7.2.7 existing-PDF editing model and generalizes
 * the same canvas for General PDF, Purchase, Quotation and future ERP document types.
 * Existing PDF content is never rewritten in-place. Detected text is selected,
 * then a whiteout + editable replacement overlay is stored in the template.
 * This keeps the original customer PDF immutable while giving the user a true
 * edit/replace workflow.</p>
 */
public class PdfDesignerController implements ScreenLifecycle {
    private static final double BASE_SCALE = 1.15;
    private static final String LOGO_PATH_KEY = "company.logoPath";
    private static final String APPLICATION_BRAND_PATH_KEY = "application.brandImagePath";
    private static final String SIGNATURE_PATH_KEY = "company.signaturePath";
    private static final String QR_PATH_KEY = "payment.qrImagePath";

    @FXML private javafx.scene.layout.BorderPane root;
    @FXML private Label lblTemplateName, lblTemplateMeta, lblSaveState, lblZoom, lblPageSize, lblSelection;
    @FXML private Label lblExistingOriginal, lblImageSource, lblOpacity;
    @FXML private ComboBox<DocumentSample> cmbSampleDocument;
    @FXML private Label lblPreviewData;
    @FXML private ComboBox<String> cmbImageFit;
    @FXML private ColorPicker colorText, colorFill, colorStroke;
    @FXML private Button btnPreview, btnApplyProperties, btnConvertExisting, btnConvertImportedImage, btnSaveDefault, btnConnectData, btnOriginalView, btnDesignView;
    @FXML private ToggleButton btnEditExistingText, btnEditImportedImage, btnEditFormField, btnAreaSelect;
    @FXML private Slider zoomSlider, sldOpacity;
    @FXML private CheckBox chkSnap, chkBold, chkLocked, chkPreserveRatio;
    @FXML private TextField txtFieldSearch;
    @FXML private ListView<TemplateFieldDefinition> lstFields;
    @FXML private ListView<String> lstPages;
    @FXML private ScrollPane canvasScroll;
    @FXML private StackPane canvasHolder;
    @FXML private Pane canvasPane;
    @FXML private TextArea txtContent;
    @FXML private TextField txtX, txtY, txtWidth, txtHeight, txtFontSize, txtStrokeWidth;
    @FXML private TextField txtTableColumns, txtRowHeight, txtHeaderHeight, txtRotation;
    @FXML private TabPane leftTabs, propertiesTabs;
    @FXML private javafx.scene.layout.VBox existingTextSection, existingFormSection, tablePropertiesSection, imagePropertiesSection;

    private DocumentTemplate template;
    private Path sourcePdf;
    private Path originalPdf;
    private Path previewPdf;
    private boolean previewMode;
    private boolean originalMode;
    private boolean areaSelectionMode;
    private Rectangle areaSelectionRect;
    private double areaStartX, areaStartY;
    private boolean existingTextMode;
    private boolean existingImageMode;
    private boolean formFieldMode;
    private int pageIndex;
    private double pageWidth = 595;
    private double pageHeight = 842;
    private int sourcePageCount = 1;
    private double scale = BASE_SCALE;
    private TemplateElement selected;
    private String selectedId;
    private PdfTextRegion selectedPdfText;
    private PdfImageRegion selectedPdfImage;
    private PdfFormFieldRegion selectedPdfForm;
    private final Map<Integer, List<PdfTextRegion>> textRegionCache = new HashMap<>();
    private final Set<Integer> textRegionLoading = new HashSet<>();
    private final Map<Integer, List<PdfImageRegion>> imageRegionCache = new HashMap<>();
    private final Set<Integer> imageRegionLoading = new HashSet<>();
    private final Map<Integer, List<PdfFormFieldRegion>> formRegionCache = new HashMap<>();
    private final Set<Integer> formRegionLoading = new HashSet<>();
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
            originalPdf = TemplateStorageService.originalPdf(template);
            var size = PdfPreviewSupport.pageSize(sourcePdf, 0);
            pageWidth = size.width();
            pageHeight = size.height();
            sourcePageCount = size.pageCount();
        } catch (Exception error) {
            Platform.runLater(() -> ModernDialog.error(root, "PDF could not be opened", "Document Studio", rootMessage(error)));
        }

        lblTemplateName.setText(template.getName());
        refreshMeta();
        configureFields();
        configurePages(sourcePageCount);
        configureSampleDocuments();
        configureProperties();
        updateModeControls();

        zoomSlider.valueProperty().addListener((obs, old, value) -> {
            scale = BASE_SCALE * value.doubleValue() / 100.0;
            lblZoom.setText(Math.round(value.doubleValue()) + "%");
            renderCanvas();
        });
        sldOpacity.valueProperty().addListener((obs, old, value) -> lblOpacity.setText(Math.round(value.doubleValue()) + "%"));
        lstPages.getSelectionModel().selectedIndexProperty().addListener((obs, old, value) -> {
            if (value.intValue() >= 0 && value.intValue() != pageIndex) {
                pageIndex = value.intValue();
                clearSelection();
                renderCanvas();
            }
        });
        Platform.runLater(this::installKeyboardShortcuts);
        installAreaSelectionHandlers();
        renderCanvas();
    }

    @Override
    public void onScreenShown(boolean reused) {
        if (template != null) renderCanvas();
    }

    private void configureFields() {
        lstFields.setItems(FXCollections.observableArrayList(TemplateFieldCatalog.fieldsFor(template.getDocumentType())));
        lstFields.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TemplateFieldDefinition item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.category() + "  •  " + item.label());
            }
        });
        lstFields.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) addSelectedField();
        });
        txtFieldSearch.textProperty().addListener((obs, old, value) -> {
            String q = value == null ? "" : value.trim().toLowerCase();
            lstFields.setItems(FXCollections.observableArrayList(TemplateFieldCatalog.fieldsFor(template.getDocumentType()).stream()
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

    private void configureSampleDocuments() {
        boolean erp = template.getDocumentType().isErpConnected();
        cmbSampleDocument.setDisable(!erp);
        cmbSampleDocument.setPromptText(erp ? "Sample / choose saved document" : "General PDF - no ERP data");
        if (lblPreviewData != null) lblPreviewData.setText(erp ? "Preview Data:" : "Preview:");
        cmbSampleDocument.getItems().clear();
        cmbSampleDocument.getItems().add(null);
        cmbSampleDocument.getSelectionModel().selectFirst();
        if (!erp) return;
        CompletableFuture.supplyAsync(() -> DocumentDataService.listSamples(template.getDocumentType()))
                .thenAccept(list -> Platform.runLater(() -> {
                    if (template == null) return;
                    cmbSampleDocument.getItems().clear();
                    cmbSampleDocument.getItems().add(null);
                    cmbSampleDocument.getItems().addAll(list);
                    cmbSampleDocument.getSelectionModel().selectFirst();
                }));
    }

    private void configureProperties() {
        colorText.setValue(Color.web("#172033"));
        colorFill.setValue(Color.WHITE);
        colorStroke.setValue(Color.web("#94A3B8"));
        cmbImageFit.setItems(FXCollections.observableArrayList("FIT", "FILL", "STRETCH"));
        cmbImageFit.getSelectionModel().select("FIT");
        clearProperties();
    }

    private void installKeyboardShortcuts() {
        if (root.getScene() == null) return;
        root.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        root.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), this::redo);
        root.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), this::duplicateSelected);
        root.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DELETE && selected != null && !isTextInput(event.getTarget())) {
                deleteSelected();
                event.consume();
            }
        });
    }

    private boolean isTextInput(Object target) {
        return target instanceof TextInputControl || target instanceof ComboBoxBase<?>;
    }

    @FXML private void backToLibrary() {
        DashboardController.navigateFromDocumentStudio("Document Studio", "/fxml/pages/DocumentStudio.fxml");
    }

    @FXML private void addHeading() {
        TextInputDialog dialog = new org.example.util.OwnedTextInputDialog(defaultHeading());
        dialog.setTitle("Add Heading");
        dialog.setHeaderText("Add a heading to the document");
        dialog.setContentText("Heading:");
        dialog.showAndWait().filter(v -> !v.isBlank()).ifPresent(value -> {
            TemplateElement e = newElement(ElementType.TEXT, 250, 42);
            e.setText(value);
            e.setBold(true);
            e.setFontSize(18);
            addElement(e);
        });
    }

    @FXML private void addText() {
        TextInputDialog dialog = new org.example.util.OwnedTextInputDialog("New Text");
        dialog.setTitle("Add Text");
        dialog.setHeaderText("Add text to the PDF template");
        dialog.setContentText("Text:");
        dialog.showAndWait().filter(v -> !v.isBlank()).ifPresent(value -> {
            TemplateElement e = newElement(ElementType.TEXT, 190, 38);
            e.setText(value);
            addElement(e);
        });
    }

    @FXML private void addImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            TemplateElement e = newElement(ElementType.IMAGE, 170, 100);
            e.setImagePath(TemplateStorageService.importAsset(template, file.toPath()));
            addElement(e);
            propertiesTabs.getTabs().get(1).setDisable(false);
            propertiesTabs.getSelectionModel().select(1);
        } catch (Exception error) {
            ModernDialog.error(root, "Image could not be added", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void addCompanyLogo() { addConfiguredImage(LOGO_PATH_KEY, "Company Logo"); }
    @FXML private void addSignature() { addConfiguredImage(SIGNATURE_PATH_KEY, "Authorized Signature"); }
    @FXML private void addApplicationBrand() { addConfiguredImage(APPLICATION_BRAND_PATH_KEY, "Application Brand Image"); }
    @FXML private void addPaymentQr() { addConfiguredImage(QR_PATH_KEY, "Payment QR"); }

    private void addConfiguredImage(String key, String label) {
        String configured = ConfigManager.get(key, "");
        if (configured == null || configured.isBlank() || !Files.isRegularFile(Path.of(configured))) {
            ModernDialog.info(root, label + " not configured", "Document Studio",
                    "Configure this image first under Settings → Company & Billing / Payment & Bank, then add it to the template.");
            return;
        }
        try {
            TemplateElement e = newElement(ElementType.IMAGE, 180, 95);
            e.setImagePath(TemplateStorageService.importAsset(template, Path.of(configured)));
            e.setPreserveAspectRatio(true);
            e.setImageFit("FIT");
            addElement(e);
            propertiesTabs.getTabs().get(1).setDisable(false);
            propertiesTabs.getSelectionModel().select(1);
        } catch (Exception error) {
            ModernDialog.error(root, label + " could not be added", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void addWhiteout() {
        TemplateElement e = newElement(ElementType.WHITEOUT, 190, 44);
        addElement(e);
    }

    @FXML private void toggleAreaSelectionMode() {
        if (previewMode) {
            if (btnAreaSelect != null) btnAreaSelect.setSelected(false);
            return;
        }
        areaSelectionMode = btnAreaSelect != null && btnAreaSelect.isSelected();
        if (areaSelectionMode) {
            existingTextMode = false; existingImageMode = false; formFieldMode = false;
            btnEditExistingText.setSelected(false); btnEditImportedImage.setSelected(false); btnEditFormField.setSelected(false);
            clearSelection();
            lblSaveState.setText("Drag over the original value to hide it");
        } else {
            lblSaveState.setText("Ready");
            renderCanvas();
        }
    }

    private void installAreaSelectionHandlers() {
        canvasPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!areaSelectionMode || previewMode) return;
            var local = canvasPane.sceneToLocal(event.getSceneX(), event.getSceneY());
            areaStartX = clamp(local.getX(), 0, canvasPane.getWidth());
            areaStartY = clamp(local.getY(), 0, canvasPane.getHeight());
            areaSelectionRect = new Rectangle(areaStartX, areaStartY, 1, 1);
            areaSelectionRect.setFill(Color.rgb(37, 99, 235, 0.14));
            areaSelectionRect.setStroke(Color.web("#2563EB"));
            areaSelectionRect.getStrokeDashArray().setAll(5.0, 4.0);
            areaSelectionRect.setMouseTransparent(true);
            canvasPane.getChildren().add(areaSelectionRect);
            event.consume();
        });
        canvasPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!areaSelectionMode || areaSelectionRect == null || previewMode) return;
            var local = canvasPane.sceneToLocal(event.getSceneX(), event.getSceneY());
            double x = clamp(local.getX(), 0, canvasPane.getWidth());
            double y = clamp(local.getY(), 0, canvasPane.getHeight());
            areaSelectionRect.setX(Math.min(areaStartX, x));
            areaSelectionRect.setY(Math.min(areaStartY, y));
            areaSelectionRect.setWidth(Math.abs(x - areaStartX));
            areaSelectionRect.setHeight(Math.abs(y - areaStartY));
            event.consume();
        });
        canvasPane.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (!areaSelectionMode || areaSelectionRect == null || previewMode) return;
            Rectangle selection = areaSelectionRect;
            areaSelectionRect = null;
            double width = selection.getWidth(), height = selection.getHeight();
            if (width >= 4 && height >= 4) {
                TemplateElement mask = TemplateElement.of(ElementType.WHITEOUT, pageIndex,
                        selection.getX() / scale, selection.getY() / scale, width / scale, height / scale);
                String group = "MANUAL_AREA|" + UUID.randomUUID();
                mask.setReplacementGroupId(group);
                mask.setReplacementSourceKey(group);
                areaSelectionMode = false;
                if (btnAreaSelect != null) btnAreaSelect.setSelected(false);
                addElement(mask);
                lblSaveState.setText("Area hidden — add Text or an ERP Field on top");
            } else {
                canvasPane.getChildren().remove(selection);
            }
            event.consume();
        });
    }

    @FXML private void addRectangle() {
        TemplateElement e = newElement(ElementType.RECTANGLE, 190, 75);
        e.setFillColor("#FFFFFF");
        e.setStrokeColor("#2563EB");
        addElement(e);
    }

    @FXML private void addLine() {
        TemplateElement e = newElement(ElementType.LINE, 190, 1);
        e.setStrokeColor("#64748B");
        addElement(e);
    }

    @FXML private void addItemTable() {
        TemplateElement e = newElement(ElementType.ITEM_TABLE, Math.max(280, pageWidth - 70), Math.min(340, pageHeight * 0.4));
        e.setX(35);
        e.setY(Math.min(300, pageHeight * 0.35));
        e.setRowHeight(22);
        e.setHeaderHeight(24);
        addElement(e);
    }

    @FXML private void addSelectedField() {
        TemplateFieldDefinition field = lstFields.getSelectionModel().getSelectedItem();
        if (field == null) {
            ModernDialog.info(root, "Choose a field", "ERP Data", "Select an ERP field from the DATA tab first.");
            return;
        }
        TemplateElement e = newElement(field.image() ? ElementType.IMAGE_FIELD : ElementType.FIELD,
                field.image() ? 155 : 200, field.image() ? 85 : 32);
        e.setFieldKey(field.key());
        e.setText(field.label());
        addElement(e);
    }

    @FXML private void toggleExistingTextMode() {
        if (previewMode) {
            btnEditExistingText.setSelected(false);
            return;
        }
        existingTextMode = btnEditExistingText.isSelected();
        if (existingTextMode) {
            existingImageMode = false;
            formFieldMode = false;
            btnEditImportedImage.setSelected(false);
            btnEditFormField.setSelected(false);
        }
        clearSelection();
        if (existingTextMode) {
            lblSaveState.setText("Detecting PDF text…");
            ensureTextRegions(pageIndex, true);
        } else {
            lblSaveState.setText("Ready");
            renderCanvas();
        }
    }

    @FXML private void toggleImportedImageMode() {
        if (previewMode) {
            btnEditImportedImage.setSelected(false);
            return;
        }
        existingImageMode = btnEditImportedImage.isSelected();
        if (existingImageMode) {
            existingTextMode = false;
            formFieldMode = false;
            btnEditExistingText.setSelected(false);
            btnEditFormField.setSelected(false);
        }
        clearSelection();
        if (existingImageMode) {
            lblSaveState.setText("Detecting imported images…");
            ensureImageRegions(pageIndex, true);
        } else {
            lblSaveState.setText("Ready");
            renderCanvas();
        }
    }

    @FXML private void toggleFormFieldMode() {
        if (previewMode) {
            btnEditFormField.setSelected(false);
            return;
        }
        formFieldMode = btnEditFormField.isSelected();
        if (formFieldMode) {
            existingTextMode = false;
            existingImageMode = false;
            btnEditExistingText.setSelected(false);
            btnEditImportedImage.setSelected(false);
        }
        clearSelection();
        if (formFieldMode) {
            lblSaveState.setText("Detecting PDF form fields…");
            ensureFormRegions(pageIndex, true);
        } else {
            lblSaveState.setText("Ready");
            renderCanvas();
        }
    }

    @FXML private void convertExistingToField() {
        if (selectedPdfText == null) {
            ModernDialog.info(root, "Select existing PDF text", "Convert to ERP Field",
                    "Turn on Edit Existing PDF Text, click the printed text you want to replace, then select an ERP field from the DATA tab.");
            return;
        }
        TemplateFieldDefinition field = lstFields.getSelectionModel().getSelectedItem();
        if (field == null) {
            leftTabs.getSelectionModel().select(1);
            ModernDialog.info(root, "Choose an ERP field", "Convert to ERP Field",
                    "Select the matching field in the DATA tab, then choose Convert Selected PDF Text to Field again.");
            return;
        }
        checkpoint();
        PdfTextRegion region = selectedPdfText;
        String replacementKey = textReplacementKey(region);
        TemplateElement mask = replacementMask(region, replacementKey);
        TemplateElement fieldElement = TemplateElement.of(field.image() ? ElementType.IMAGE_FIELD : ElementType.FIELD,
                pageIndex, region.x(), region.y(), Math.max(region.width(), field.image() ? 145 : 90),
                Math.max(region.height(), field.image() ? 70 : region.fontSize() * 1.35));
        fieldElement.setFieldKey(field.key());
        fieldElement.setText(field.label());
        fieldElement.setFontSize(region.fontSize());
        fieldElement.setReplacementGroupId(replacementKey);
        fieldElement.setReplacementSourceKey(replacementKey);
        List<TemplateElement> updated = replacementBase(replacementKey);
        updated.add(mask);
        updated.add(fieldElement);
        template.setElements(updated);
        selectedPdfText = null;
        selected = fieldElement;
        selectedId = fieldElement.getId();
        autosave();
        renderCanvas();
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
        updated.add(e);
        template.setElements(updated);
        selectedPdfText = null;
        selectedId = e.getId();
        selected = e;
        autosave();
        renderCanvas();
    }

    @FXML private void duplicateSelected() {
        if (selected == null || previewMode) return;
        checkpoint();
        TemplateElement copy = selected.copy();
        copy.setReplacementGroupId("");
        copy.setReplacementSourceKey("");
        copy.setX(Math.min(pageWidth - copy.getWidth(), selected.getX() + 12));
        copy.setY(Math.min(pageHeight - copy.getHeight(), selected.getY() + 12));
        List<TemplateElement> updated = new ArrayList<>(template.getElements());
        updated.add(copy);
        template.setElements(updated);
        selected = copy;
        selectedPdfText = null;
        selectedId = copy.getId();
        autosave();
        renderCanvas();
    }

    @FXML private void deleteSelected() {
        if (selected == null || previewMode) return;
        if (selected.isLocked()) {
            ModernDialog.info(root, "Object is locked", "Unlock before deleting", "Clear Lock object or use Arrange → Toggle Lock first.");
            return;
        }
        checkpoint();
        String id = selected.getId();
        String group = selected.getReplacementGroupId();
        template.setElements(template.getElements().stream().filter(e -> {
            if (!group.isBlank() && group.equals(e.getReplacementGroupId())) return false;
            return !e.getId().equals(id);
        }).toList());
        clearSelection();
        autosave();
        renderCanvas();
    }

    @FXML private void applyProperties() {
        if (previewMode) return;
        if (selectedPdfText != null) {
            replaceExistingPdfText();
            return;
        }
        if (selectedPdfForm != null) {
            replaceExistingFormField();
            return;
        }
        if (selected == null) return;
        try {
            checkpoint();
            if (selected.getType() == ElementType.TEXT) selected.setText(txtContent.getText());
            selected.setX(parse(txtX, selected.getX()));
            selected.setY(parse(txtY, selected.getY()));
            selected.setWidth(parse(txtWidth, selected.getWidth()));
            selected.setHeight(parse(txtHeight, selected.getHeight()));
            selected.setFontSize(parse(txtFontSize, selected.getFontSize()));
            selected.setStrokeWidth(parse(txtStrokeWidth, selected.getStrokeWidth()));
            selected.setBold(chkBold.isSelected());
            selected.setLocked(chkLocked.isSelected());
            selected.setTextColor(hex(colorText.getValue()));
            selected.setFillColor(hex(colorFill.getValue()));
            selected.setStrokeColor(hex(colorStroke.getValue()));
            if (selected.getType() == ElementType.ITEM_TABLE) {
                List<String> columns = Arrays.stream(txtTableColumns.getText().split(","))
                        .map(String::trim).filter(s -> !s.isBlank()).toList();
                if (!columns.isEmpty()) selected.setTableColumns(columns);
                selected.setRowHeight(parse(txtRowHeight, selected.getRowHeight()));
                selected.setHeaderHeight(parse(txtHeaderHeight, selected.getHeaderHeight()));
            }
            if (isImageElement(selected)) {
                selected.setImageFit(cmbImageFit.getValue());
                selected.setPreserveAspectRatio(chkPreserveRatio.isSelected());
                selected.setOpacity(sldOpacity.getValue() / 100.0);
                selected.setRotation(parse(txtRotation, selected.getRotation()));
            }
            autosave();
            renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Properties could not be applied", "Check the numeric values", rootMessage(error));
        }
    }

    private void replaceExistingPdfText() {
        try {
            PdfTextRegion region = selectedPdfText;
            if (region == null) return;
            checkpoint();
            String replacementKey = textReplacementKey(region);
            TemplateElement mask = replacementMask(region, replacementKey);
            List<TemplateElement> updated = replacementBase(replacementKey);
            updated.add(mask);

            String replacement = txtContent.getText() == null ? "" : txtContent.getText();
            TemplateElement replacementElement = null;
            if (!replacement.isBlank()) {
                replacementElement = TemplateElement.of(ElementType.TEXT, pageIndex,
                        parse(txtX, region.x()), parse(txtY, region.y()),
                        parse(txtWidth, region.width()), parse(txtHeight, Math.max(region.height(), region.fontSize() * 1.45)));
                replacementElement.setText(replacement);
                replacementElement.setFontSize(parse(txtFontSize, region.fontSize()));
                replacementElement.setBold(chkBold.isSelected());
                replacementElement.setTextColor(hex(colorText.getValue()));
                replacementElement.setReplacementGroupId(replacementKey);
                replacementElement.setReplacementSourceKey(replacementKey);
                updated.add(replacementElement);
            }
            template.setElements(updated);
            selectedPdfText = null;
            selected = replacementElement == null ? mask : replacementElement;
            selectedId = selected.getId();
            autosave();
            renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Existing text could not be replaced", "Document Studio", rootMessage(error));
        }
    }

    private TemplateElement replacementMask(PdfTextRegion region, String replacementKey) {
        double padX = Math.max(2.0, region.fontSize() * 0.16);
        double padY = Math.max(2.0, region.fontSize() * 0.20);
        double x = Math.max(0, region.x() - padX);
        double y = Math.max(0, region.y() - padY);
        TemplateElement mask = TemplateElement.of(ElementType.WHITEOUT, pageIndex,
                x, y,
                Math.min(pageWidth - x, region.width() + padX * 2),
                Math.min(pageHeight - y, region.height() + padY * 2));
        mask.setLocked(true);
        mask.setReplacementGroupId(replacementKey);
        mask.setReplacementSourceKey(replacementKey);
        return mask;
    }

    private String textReplacementKey(PdfTextRegion region) {
        return "PDF_TEXT|" + region.pageIndex() + "|" + roundedKey(region.x()) + "|" + roundedKey(region.y())
                + "|" + roundedKey(region.width()) + "|" + roundedKey(region.height());
    }

    private String formReplacementKey(PdfFormFieldRegion region) {
        return "PDF_FORM|" + region.pageIndex() + "|" + safeKey(region.fieldName()) + "|" + roundedKey(region.x()) + "|" + roundedKey(region.y());
    }

    private List<TemplateElement> replacementBase(String replacementKey) {
        List<TemplateElement> updated = new ArrayList<>();
        for (TemplateElement element : template.getElements()) {
            if (replacementKey.equals(element.getReplacementGroupId()) || replacementKey.equals(element.getReplacementSourceKey())) continue;
            updated.add(element);
        }
        return updated;
    }

    private String roundedKey(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private String safeKey(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private void replaceExistingFormField() {
        try {
            PdfFormFieldRegion region = selectedPdfForm;
            if (region == null) return;
            checkpoint();
            String replacementKey = formReplacementKey(region);
            double padX = 2.0, padY = 2.0;
            double maskX = Math.max(0, region.x() - padX), maskY = Math.max(0, region.y() - padY);
            TemplateElement mask = TemplateElement.of(ElementType.WHITEOUT, pageIndex,
                    maskX, maskY,
                    Math.min(pageWidth - maskX, region.width() + padX * 2),
                    Math.min(pageHeight - maskY, region.height() + padY * 2));
            mask.setLocked(true);
            mask.setReplacementGroupId(replacementKey);
            mask.setReplacementSourceKey(replacementKey);
            List<TemplateElement> updated = replacementBase(replacementKey);
            updated.add(mask);
            String replacement = txtContent.getText() == null ? "" : txtContent.getText();
            TemplateElement replacementElement = null;
            if (!replacement.isBlank()) {
                replacementElement = TemplateElement.of(ElementType.TEXT, pageIndex,
                        parse(txtX, region.x()), parse(txtY, region.y()),
                        parse(txtWidth, region.width()), parse(txtHeight, region.height()));
                replacementElement.setText(replacement);
                replacementElement.setFontSize(parse(txtFontSize, Math.max(8, Math.min(12, region.height() * 0.55))));
                replacementElement.setBold(chkBold.isSelected());
                replacementElement.setTextColor(hex(colorText.getValue()));
                replacementElement.setReplacementGroupId(replacementKey);
                replacementElement.setReplacementSourceKey(replacementKey);
                updated.add(replacementElement);
            }
            template.setElements(updated);
            selectedPdfForm = null;
            selected = replacementElement == null ? mask : replacementElement;
            selectedId = selected.getId();
            autosave();
            renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "PDF form field could not be replaced", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void convertImportedImage() {
        if (selectedPdfImage == null || previewMode) {
            ModernDialog.info(root, "Select an imported image", "Document Studio", "Turn on Edit Imported Image and click a detected raster image first.");
            return;
        }
        try {
            checkpoint();
            PdfImageRegion region = selectedPdfImage;
            TemplateElement mask = TemplateElement.of(ElementType.WHITEOUT, pageIndex,
                    Math.max(0, region.x()), Math.max(0, region.y()), region.width(), region.height());
            mask.setLocked(true);
            TemplateElement image = TemplateElement.of(ElementType.IMAGE, pageIndex,
                    region.x(), region.y(), region.width(), region.height());
            image.setImagePath(TemplateStorageService.importAsset(template, region.extractedImage()));
            image.setPreserveAspectRatio(true);
            image.setImageFit("FIT");
            List<TemplateElement> updated = new ArrayList<>(template.getElements());
            updated.add(mask);
            updated.add(image);
            template.setElements(updated);
            selectedPdfImage = null;
            selected = image;
            selectedId = image.getId();
            autosave();
            propertiesTabs.getTabs().get(1).setDisable(false);
            propertiesTabs.getSelectionModel().select(1);
            renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Imported image could not be converted", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void replaceSelectedImage() {
        if (selected == null || selected.getType() != ElementType.IMAGE) {
            ModernDialog.info(root, "Select an uploaded image", "Replace Image", "Only template image objects can be replaced directly. ERP image fields are supplied by live data.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Replace Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            checkpoint();
            selected.setImagePath(TemplateStorageService.importAsset(template, file.toPath()));
            autosave();
            renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Image could not be replaced", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void saveDraft() {
        if (template == null) return;
        try {
            TemplateStorageService.save(template);
            lblSaveState.setText("Saved ✓");
            ModernDialog.success(root, "Template saved", template.getName() + " was saved to this workspace.");
        } catch (Exception error) {
            ModernDialog.error(root, "Save failed", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void saveAndDefault() {
        if (template == null) return;
        try {
            if (template.getDocumentType().isGeneral()) {
                TemplateStorageService.save(template);
                lblSaveState.setText("Saved ✓");
                ModernDialog.success(root, "General PDF saved", template.getName() + " is saved in the Document Library. Use Export PDF to create a standalone PDF file.");
                return;
            }
            if (!DocumentFlowRegistry.isAutomatic(template.getDocumentType())) {
                TemplateStorageService.save(template);
                lblSaveState.setText("Saved ✓");
                ModernDialog.success(root, "Template saved",
                        template.getName() + " is saved as a design-only template. Automatic runtime default is not enabled for " + template.getDocumentType().label() + ".");
                return;
            }
            TemplateStorageService.activateAndSetDefault(template);
            refreshMeta();
            lblSaveState.setText("Saved & Default ✓");
            ModernDialog.success(root, "Default template updated",
                    "It will now be used automatically for " + template.getDocumentType().label() + " PDF generation. Sales PDF generation remains unchanged.");
        } catch (Exception error) {
            ModernDialog.error(root, "Save failed", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void showOriginal() {
        if (template == null) return;
        try {
            originalPdf = TemplateStorageService.originalPdf(template);
            originalMode = true;
            previewMode = true; // existing mutation guards also protect Original mode
            previewPdf = null;
            existingTextMode = false; existingImageMode = false; formFieldMode = false; areaSelectionMode = false;
            btnEditExistingText.setSelected(false); btnEditImportedImage.setSelected(false); btnEditFormField.setSelected(false);
            if (btnAreaSelect != null) btnAreaSelect.setSelected(false);
            setDesignEditingEnabled(false);
            clearSelection();
            btnPreview.setText("Final Preview");
            var info = PdfPreviewSupport.pageSize(originalPdf, 0);
            configurePages(info.pageCount());
            renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Original PDF could not be shown", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void showDesign() {
        if (template == null) return;
        originalMode = false;
        previewMode = false;
        previewPdf = null;
        areaSelectionMode = false;
        if (btnAreaSelect != null) btnAreaSelect.setSelected(false);
        setDesignEditingEnabled(true);
        clearSelection();
        btnPreview.setText("Final Preview");
        configurePages(sourcePageCount);
        renderCanvas();
    }

    @FXML private void togglePreview() {
        if (template == null) return;
        if (previewMode && !originalMode) {
            showDesign();
            return;
        }
        // Moving directly from Original to Final Preview is allowed.
        originalMode = false;
        previewMode = false;
        try {
            TemplateStorageService.save(template);
            previewPdf = WorkspaceManager.getTempFolder().resolve("document-studio-preview-" + template.getId() + ".pdf");
            TemplateData data = previewData();
            PdfTemplateRenderer.render(template, data, previewPdf);
            previewMode = true;
            existingTextMode = false;
            existingImageMode = false;
            formFieldMode = false;
            areaSelectionMode = false;
            btnEditExistingText.setSelected(false);
            btnEditImportedImage.setSelected(false);
            btnEditFormField.setSelected(false);
            if (btnAreaSelect != null) btnAreaSelect.setSelected(false);
            setDesignEditingEnabled(false);
            clearSelection();
            btnPreview.setText("Back to Design");
            var info = PdfPreviewSupport.pageSize(previewPdf, 0);
            configurePages(info.pageCount());
            renderCanvas();
        } catch (Exception error) {
            previewMode = false;
            setDesignEditingEnabled(true);
            ModernDialog.error(root, "Preview failed", "The document could not be rendered", rootMessage(error));
        }
    }

    private void setDesignEditingEnabled(boolean enabled) {
        btnEditExistingText.setDisable(!enabled);
        btnEditImportedImage.setDisable(!enabled);
        btnEditFormField.setDisable(!enabled);
        if (btnAreaSelect != null) btnAreaSelect.setDisable(!enabled);
    }

    private TemplateData previewData() {
        DocumentSample selectedSample = cmbSampleDocument == null ? null : cmbSampleDocument.getValue();
        return selectedSample == null
                ? DocumentDataService.sample(template.getDocumentType())
                : DocumentDataService.load(template.getDocumentType(), selectedSample.id());
    }

    @FXML private void exportTestPdf() {
        if (template == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(template.getName().replaceAll("[^A-Za-z0-9._-]", "-") + ".pdf");
        var file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            TemplateStorageService.save(template);
            PdfTemplateRenderer.render(template, previewData(), file.toPath());
            ModernDialog.success(root, "PDF exported", file.getAbsolutePath());
        } catch (Exception error) {
            ModernDialog.error(root, "Export failed", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void connectErpData() {
        if (template == null || previewMode) return;
        List<DocumentType> choices = Arrays.stream(DocumentType.values())
                .filter(type -> type != DocumentType.GENERAL_PDF)
                .toList();
        DocumentType initial = template.getDocumentType().isGeneral() ? DocumentType.PURCHASE_INVOICE : template.getDocumentType();
        ChoiceDialog<DocumentType> dialog = new org.example.util.OwnedChoiceDialog<>(initial, choices);
        dialog.setTitle("Connect ERP Data");
        dialog.setHeaderText(template.getDocumentType().isGeneral() ? "Turn this PDF into a reusable ERP template" : "Change the ERP document type");
        dialog.setContentText("Document type:");
        var result = dialog.showAndWait();
        if (result.isEmpty()) return;
        try {
            TemplateStorageService.changeDocumentType(template, result.get());
            lstFields.setItems(FXCollections.observableArrayList(TemplateFieldCatalog.fieldsFor(template.getDocumentType())));
            configureSampleDocuments();
            updateModeControls();
            refreshMeta();
            lblSaveState.setText("ERP data connected ✓");
        } catch (Exception error) {
            ModernDialog.error(root, "Could not connect ERP data", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void disconnectErpData() {
        if (template == null || template.getDocumentType().isGeneral() || previewMode) return;
        if (!ModernDialog.confirm(root, "Convert to General PDF", "Disconnect ERP data from this document?",
                "Designer objects remain on the page, but dynamic ERP fields will render blank until another ERP document type is connected.")) return;
        try {
            TemplateStorageService.changeDocumentType(template, DocumentType.GENERAL_PDF);
            lstFields.setItems(FXCollections.observableArrayList());
            configureSampleDocuments();
            updateModeControls();
            refreshMeta();
        } catch (Exception error) {
            ModernDialog.error(root, "Could not disconnect ERP data", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void appendBlankPage() {
        if (template == null || previewMode) return;
        try {
            sourcePageCount = TemplateStorageService.appendBlankPage(template, pageIndex);
            configurePages(sourcePageCount);
            pageIndex = sourcePageCount - 1;
            lstPages.getSelectionModel().select(pageIndex);
            textRegionCache.clear();
            renderCanvas();
            lblSaveState.setText("Blank page added ✓");
        } catch (Exception error) {
            ModernDialog.error(root, "Page could not be added", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void deleteCurrentPage() {
        if (template == null || previewMode) return;
        if (!ModernDialog.confirm(root, "Delete Page", "Delete page " + (pageIndex + 1) + "?",
                "The workspace copy of this template page and objects on that page will be removed. Your original imported file outside DSE ERP is not changed.")) return;
        try {
            sourcePageCount = TemplateStorageService.deletePage(template, pageIndex);
            pageIndex = Math.max(0, Math.min(pageIndex, sourcePageCount - 1));
            configurePages(sourcePageCount);
            textRegionCache.clear(); clearSelection(); renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Page could not be deleted", "Document Studio", rootMessage(error));
        }
    }

    @FXML private void rotatePageLeft() { rotateCurrentPage(-90); }
    @FXML private void rotatePageRight() { rotateCurrentPage(90); }

    private void rotateCurrentPage(int degrees) {
        if (template == null || previewMode) return;
        try {
            TemplateStorageService.rotatePage(template, pageIndex, degrees);
            var size = PdfPreviewSupport.pageSize(sourcePdf, pageIndex);
            pageWidth = size.width(); pageHeight = size.height();
            textRegionCache.remove(pageIndex); renderCanvas();
        } catch (Exception error) {
            ModernDialog.error(root, "Page could not be rotated", "Document Studio", rootMessage(error));
        }
    }

    private void updateModeControls() {
        if (template == null) return;
        boolean automatic = DocumentFlowRegistry.isAutomatic(template.getDocumentType());
        boolean erp = template.getDocumentType().isErpConnected();
        if (btnSaveDefault != null) btnSaveDefault.setText(automatic ? "Save & Set Default" : "Save Document");
        if (btnConnectData != null) btnConnectData.setText(erp ? "Change ERP Data" : "Connect ERP Data");
        if (btnPreview != null) btnPreview.setText("Final Preview");
    }

    private String defaultHeading() {
        if (template == null) return "Document";
        return switch (template.getDocumentType()) {
            case PURCHASE_INVOICE -> "Purchase Invoice";
            case PURCHASE_RETURN -> "Purchase Return";
            case PURCHASE_ORDER -> "Purchase Order";
            case QUOTATION -> "Quotation";
            case DELIVERY_CHALLAN -> "Delivery Challan";
            case CREDIT_NOTE -> "Credit Note";
            case DEBIT_NOTE -> "Debit Note";
            case PAYMENT_RECEIPT -> "Payment Receipt";
            case SALES_INVOICE -> "Sales Invoice";
            case SALES_RETURN -> "Sales Return";
            case CUSTOM_ERP -> "Business Document";
            case GENERAL_PDF -> "Document Heading";
        };
    }

    @FXML private void fitWidth() {
        Platform.runLater(() -> {
            double available = Math.max(250, canvasScroll.getViewportBounds().getWidth() - 50);
            setZoomForScale(available / Math.max(1, pageWidth));
        });
    }

    @FXML private void fitPage() {
        Platform.runLater(() -> {
            double availableW = Math.max(250, canvasScroll.getViewportBounds().getWidth() - 55);
            double availableH = Math.max(250, canvasScroll.getViewportBounds().getHeight() - 55);
            setZoomForScale(Math.min(availableW / Math.max(1, pageWidth), availableH / Math.max(1, pageHeight)));
        });
    }

    private void setZoomForScale(double targetScale) {
        double percent = targetScale / BASE_SCALE * 100.0;
        zoomSlider.setValue(clamp(percent, zoomSlider.getMin(), zoomSlider.getMax()));
    }

    @FXML private void undo() {
        if (undo.isEmpty() || previewMode) return;
        redo.push(copyElements(template.getElements()));
        template.setElements(undo.pop());
        clearSelection();
        autosave();
        renderCanvas();
    }

    @FXML private void redo() {
        if (redo.isEmpty() || previewMode) return;
        undo.push(copyElements(template.getElements()));
        template.setElements(redo.pop());
        clearSelection();
        autosave();
        renderCanvas();
    }

    private void checkpoint() {
        undo.push(copyElements(template.getElements()));
        while (undo.size() > 40) undo.removeLast();
        redo.clear();
    }

    private void autosave() {
        lblSaveState.setText("Saving…");
        try {
            TemplateStorageService.save(template);
            lblSaveState.setText("Saved ✓");
        } catch (Exception error) {
            lblSaveState.setText("Save failed");
        }
        refreshMeta();
    }

    private void renderCanvas() {
        if (template == null || sourcePdf == null) return;
        Path pdf = originalMode && originalPdf != null ? originalPdf : (previewMode && previewPdf != null ? previewPdf : sourcePdf);
        int sequence = renderSequence.incrementAndGet();
        try {
            var size = PdfPreviewSupport.pageSize(pdf, pageIndex);
            pageWidth = size.width();
            pageHeight = size.height();
            lblPageSize.setText(String.format(Locale.ENGLISH, "%.0f × %.0f pt  •  Page %d/%d", pageWidth, pageHeight, pageIndex + 1, size.pageCount()));
            double canvasW = pageWidth * scale;
            double canvasH = pageHeight * scale;
            canvasPane.setMinSize(canvasW, canvasH);
            canvasPane.setPrefSize(canvasW, canvasH);
            canvasPane.setMaxSize(canvasW, canvasH);
            canvasHolder.setMinSize(canvasW + 80, canvasH + 80);
            canvasHolder.setPrefSize(canvasW + 80, canvasH + 80);
            canvasPane.getChildren().clear();
            Label loading = new Label("Rendering page…");
            loading.getStyleClass().add("pdf-designer-loading");
            loading.relocate(20, 20);
            canvasPane.getChildren().add(loading);
            CompletableFuture.supplyAsync(() -> {
                try { return PdfPreviewSupport.renderPage(pdf, pageIndex, 105); }
                catch (Exception ignored) { return null; }
            }).thenAccept(image -> Platform.runLater(() -> {
                if (sequence != renderSequence.get()) return;
                canvasPane.getChildren().clear();
                if (image != null) {
                    ImageView background = new ImageView(image);
                    background.setPreserveRatio(false);
                    background.setFitWidth(canvasW);
                    background.setFitHeight(canvasH);
                    background.setMouseTransparent(true);
                    canvasPane.getChildren().add(background);
                }
                if (!previewMode) {
                    for (TemplateElement e : template.getElements()) {
                        if (e.getPageIndex() == pageIndex) canvasPane.getChildren().add(elementNode(e));
                    }
                    if (existingTextMode) addTextTargetsIfReady(sequence);
                    if (existingImageMode) addImageTargetsIfReady(sequence);
                    if (formFieldMode) addFormTargetsIfReady(sequence);
                    restoreSelectionReference();
                } else {
                    clearProperties();
                }
            }));
        } catch (Exception error) {
            canvasPane.getChildren().setAll(new Label("Unable to render PDF page: " + rootMessage(error)));
        }
    }

    private void ensureTextRegions(int targetPage, boolean showMessageWhenEmpty) {
        if (textRegionCache.containsKey(targetPage)) {
            List<PdfTextRegion> regions = textRegionCache.get(targetPage);
            if (showMessageWhenEmpty && regions.isEmpty()) showNoTextDetected();
            renderCanvas();
            return;
        }
        if (!textRegionLoading.add(targetPage)) return;
        CompletableFuture.supplyAsync(() -> {
            try { return PdfTextExtractionService.extract(sourcePdf, targetPage); }
            catch (Exception ignored) { return List.<PdfTextRegion>of(); }
        }).thenAccept(regions -> Platform.runLater(() -> {
            textRegionLoading.remove(targetPage);
            textRegionCache.put(targetPage, regions);
            lblSaveState.setText("Ready");
            if (showMessageWhenEmpty && regions.isEmpty()) showNoTextDetected();
            if (targetPage == pageIndex && existingTextMode && !previewMode) renderCanvas();
        }));
    }

    private void ensureImageRegions(int targetPage, boolean showMessageWhenEmpty) {
        if (imageRegionCache.containsKey(targetPage)) {
            List<PdfImageRegion> regions = imageRegionCache.get(targetPage);
            if (showMessageWhenEmpty && regions.isEmpty()) showNoImagesDetected();
            renderCanvas();
            return;
        }
        if (!imageRegionLoading.add(targetPage)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                Path cache = WorkspaceManager.getTempFolder().resolve("document-studio-image-cache").resolve(template.getId());
                return PdfImageExtractionService.extract(sourcePdf, targetPage, cache);
            } catch (Exception error) {
                System.err.println("[DocumentStudio] image detection failed: " + rootMessage(error));
                return List.<PdfImageRegion>of();
            }
        }).thenAccept(regions -> Platform.runLater(() -> {
            imageRegionLoading.remove(targetPage);
            imageRegionCache.put(targetPage, regions);
            lblSaveState.setText("Ready");
            if (showMessageWhenEmpty && regions.isEmpty()) showNoImagesDetected();
            if (targetPage == pageIndex && existingImageMode && !previewMode) renderCanvas();
        }));
    }

    private void ensureFormRegions(int targetPage, boolean showMessageWhenEmpty) {
        if (formRegionCache.containsKey(targetPage)) {
            List<PdfFormFieldRegion> regions = formRegionCache.get(targetPage);
            if (showMessageWhenEmpty && regions.isEmpty()) showNoFormFieldsDetected();
            renderCanvas();
            return;
        }
        if (!formRegionLoading.add(targetPage)) return;
        CompletableFuture.supplyAsync(() -> {
            try { return PdfFormFieldService.extract(sourcePdf, targetPage); }
            catch (Exception error) {
                System.err.println("[DocumentStudio] form detection failed: " + rootMessage(error));
                return List.<PdfFormFieldRegion>of();
            }
        }).thenAccept(regions -> Platform.runLater(() -> {
            formRegionLoading.remove(targetPage);
            formRegionCache.put(targetPage, regions);
            lblSaveState.setText("Ready");
            if (showMessageWhenEmpty && regions.isEmpty()) showNoFormFieldsDetected();
            if (targetPage == pageIndex && formFieldMode && !previewMode) renderCanvas();
        }));
    }

    private void showNoImagesDetected() {
        ModernDialog.info(root, "No raster images detected", "Imported PDF",
                "This page may use vector artwork, outlines, or one flattened/scanned background. Use Replace / Hide Area for complex graphics, then add a normal Image object on top.");
    }

    private void showNoFormFieldsDetected() {
        // A normal business PDF is usually printed/static rather than an AcroForm.
        // Fall through to the existing-text editor instead of presenting this as an error.
        formFieldMode = false;
        existingImageMode = false;
        existingTextMode = true;
        if (btnEditFormField != null) btnEditFormField.setSelected(false);
        if (btnEditImportedImage != null) btnEditImportedImage.setSelected(false);
        if (btnEditExistingText != null) btnEditExistingText.setSelected(true);
        lblSaveState.setText("No interactive fields — edit printed PDF text");
        ensureTextRegions(pageIndex, false);
        ModernDialog.info(root, "Printed PDF detected", "Edit PDF Content",
                "This page has no interactive form widgets, so Document Studio switched to Edit Existing PDF Text automatically. " +
                "Click printed text to replace it. For flattened scans, vector artwork, or any area that cannot be selected, use Select / Hide Area and place editable Text, Image, or ERP Data on top.");
    }

    private void addImageTargetsIfReady(int sequence) {
        List<PdfImageRegion> regions = imageRegionCache.get(pageIndex);
        if (regions == null) {
            ensureImageRegions(pageIndex, false);
            return;
        }
        for (PdfImageRegion region : regions) {
            if (sequence != renderSequence.get()) return;
            if (isImageRegionCovered(region)) continue;
            canvasPane.getChildren().add(existingImageNode(region));
        }
    }

    private void addFormTargetsIfReady(int sequence) {
        List<PdfFormFieldRegion> regions = formRegionCache.get(pageIndex);
        if (regions == null) {
            ensureFormRegions(pageIndex, false);
            return;
        }
        for (PdfFormFieldRegion region : regions) {
            if (sequence != renderSequence.get()) return;
            if (isFormRegionCovered(region)) continue;
            canvasPane.getChildren().add(existingFormNode(region));
        }
    }

    private boolean isImageRegionCovered(PdfImageRegion region) {
        return isCovered(region.pageIndex(), region.x(), region.y(), region.width(), region.height());
    }

    private boolean isFormRegionCovered(PdfFormFieldRegion region) {
        return isCovered(region.pageIndex(), region.x(), region.y(), region.width(), region.height());
    }

    private boolean isCovered(int regionPage, double x, double y, double width, double height) {
        for (TemplateElement e : template.getElements()) {
            if (e.getPageIndex() != regionPage || e.getType() != ElementType.WHITEOUT) continue;
            double left = Math.max(e.getX(), x);
            double top = Math.max(e.getY(), y);
            double right = Math.min(e.getX() + e.getWidth(), x + width);
            double bottom = Math.min(e.getY() + e.getHeight(), y + height);
            if (right > left && bottom > top) {
                double overlap = (right - left) * (bottom - top);
                double area = width * height;
                if (area > 0 && overlap / area > 0.55) return true;
            }
        }
        return false;
    }

    private void showNoTextDetected() {
        ModernDialog.info(root, "No editable text detected", "Imported PDF",
                "This page may be scanned, flattened, or converted to outlines. Use Replace / Hide Area for scanned content. OCR is intentionally not used in this release so the original template stays predictable.");
    }

    private void addTextTargetsIfReady(int sequence) {
        List<PdfTextRegion> regions = textRegionCache.get(pageIndex);
        if (regions == null) {
            ensureTextRegions(pageIndex, false);
            return;
        }
        for (PdfTextRegion region : regions) {
            if (sequence != renderSequence.get()) return;
            if (isRegionCovered(region)) continue;
            canvasPane.getChildren().add(existingTextNode(region));
        }
    }

    private boolean isRegionCovered(PdfTextRegion region) {
        for (TemplateElement e : template.getElements()) {
            if (e.getPageIndex() != region.pageIndex() || e.getType() != ElementType.WHITEOUT) continue;
            double left = Math.max(e.getX(), region.x());
            double top = Math.max(e.getY(), region.y());
            double right = Math.min(e.getX() + e.getWidth(), region.x() + region.width());
            double bottom = Math.min(e.getY() + e.getHeight(), region.y() + region.height());
            if (right > left && bottom > top) {
                double overlap = (right - left) * (bottom - top);
                double area = region.width() * region.height();
                if (area > 0 && overlap / area > 0.55) return true;
            }
        }
        return false;
    }

    private Node existingTextNode(PdfTextRegion region) {
        Label label = new Label(region.text());
        label.setWrapText(false);
        label.setMouseTransparent(true);
        StackPane wrapper = new StackPane(label);
        wrapper.getStyleClass().add("pdf-existing-text-target");
        if (selectedPdfText == region) wrapper.getStyleClass().add("pdf-existing-text-selected");
        wrapper.setLayoutX(region.x() * scale);
        wrapper.setLayoutY(region.y() * scale);
        wrapper.setPrefSize(Math.max(12, region.width() * scale), Math.max(10, region.height() * scale));
        wrapper.setMinSize(Math.max(12, region.width() * scale), Math.max(10, region.height() * scale));
        wrapper.setMaxSize(Math.max(12, region.width() * scale), Math.max(10, region.height() * scale));
        wrapper.setOnMouseClicked(event -> {
            selectExisting(region);
            if (event.getClickCount() >= 2) {
                propertiesTabs.getSelectionModel().select(0);
                txtContent.requestFocus();
                txtContent.selectAll();
            }
            event.consume();
        });
        return wrapper;
    }

    private Node existingImageNode(PdfImageRegion region) {
        Label label = new Label("IMAGE");
        label.setMouseTransparent(true);
        StackPane wrapper = new StackPane(label);
        wrapper.getStyleClass().add("pdf-existing-image-target");
        if (Objects.equals(selectedPdfImage, region)) wrapper.getStyleClass().add("pdf-existing-image-selected");
        wrapper.setLayoutX(region.x() * scale);
        wrapper.setLayoutY(region.y() * scale);
        wrapper.setPrefSize(Math.max(12, region.width() * scale), Math.max(12, region.height() * scale));
        wrapper.setMinSize(Math.max(12, region.width() * scale), Math.max(12, region.height() * scale));
        wrapper.setMaxSize(Math.max(12, region.width() * scale), Math.max(12, region.height() * scale));
        wrapper.setOnMouseClicked(event -> { selectExistingImage(region); event.consume(); });
        return wrapper;
    }

    private Node existingFormNode(PdfFormFieldRegion region) {
        Label label = new Label(region.fieldName());
        label.setMouseTransparent(true);
        StackPane wrapper = new StackPane(label);
        wrapper.getStyleClass().add("pdf-existing-form-target");
        if (Objects.equals(selectedPdfForm, region)) wrapper.getStyleClass().add("pdf-existing-form-selected");
        wrapper.setLayoutX(region.x() * scale);
        wrapper.setLayoutY(region.y() * scale);
        wrapper.setPrefSize(Math.max(16, region.width() * scale), Math.max(12, region.height() * scale));
        wrapper.setMinSize(Math.max(16, region.width() * scale), Math.max(12, region.height() * scale));
        wrapper.setMaxSize(Math.max(16, region.width() * scale), Math.max(12, region.height() * scale));
        wrapper.setOnMouseClicked(event -> { selectExistingForm(region); event.consume(); });
        return wrapper;
    }

    private Node elementNode(TemplateElement e) {
        Node visual;
        if (e.getType() == ElementType.LINE) {
            Line line = new Line(0, 0, e.getWidth() * scale, Math.max(1, e.getHeight() * scale));
            line.setStroke(Color.web(e.getStrokeColor()));
            line.setStrokeWidth(Math.max(1, e.getStrokeWidth() * scale));
            visual = line;
        } else if (e.getType() == ElementType.IMAGE && !e.getImagePath().isBlank()) {
            visual = customImage(e);
        } else {
            Label label = new Label(elementDisplay(e));
            label.setWrapText(true);
            label.setAlignment(Pos.CENTER_LEFT);
            label.setStyle(styleFor(e));
            visual = label;
        }
        StackPane wrapper = new StackPane(visual);
        wrapper.getStyleClass().addAll("pdf-designer-object", "pdf-object-" + e.getType().name().toLowerCase());
        wrapper.getProperties().put("templateElementId", e.getId());
        if (Objects.equals(e.getId(), selectedId)) wrapper.getStyleClass().add("pdf-designer-object-selected");
        if (e.isLocked()) wrapper.getStyleClass().add("pdf-designer-object-locked");
        wrapper.setLayoutX(e.getX() * scale);
        wrapper.setLayoutY(e.getY() * scale);
        wrapper.setPrefSize(Math.max(4, e.getWidth() * scale), Math.max(4, e.getHeight() * scale));
        wrapper.setMinSize(Math.max(4, e.getWidth() * scale), Math.max(4, e.getHeight() * scale));
        wrapper.setMaxSize(Math.max(4, e.getWidth() * scale), Math.max(4, e.getHeight() * scale));
        if (isImageElement(e)) {
            wrapper.setOpacity(e.getOpacity());
            wrapper.setRotate(e.getRotation());
        }
        wrapper.setOnMousePressed(event -> {
            select(e);
            dragging = !e.isLocked();
            dragSceneX = event.getSceneX();
            dragSceneY = event.getSceneY();
            dragStartX = e.getX();
            dragStartY = e.getY();
            if (dragging) checkpoint();
            event.consume();
        });
        wrapper.setOnMouseDragged(event -> {
            if (!dragging || e.isLocked()) return;
            double dx = (event.getSceneX() - dragSceneX) / scale;
            double dy = (event.getSceneY() - dragSceneY) / scale;
            double nx = clamp(dragStartX + dx, 0, Math.max(0, pageWidth - e.getWidth()));
            double ny = clamp(dragStartY + dy, 0, Math.max(0, pageHeight - e.getHeight()));
            if (chkSnap.isSelected()) {
                nx = Math.round(nx / 4.0) * 4.0;
                ny = Math.round(ny / 4.0) * 4.0;
            }
            e.setX(nx);
            e.setY(ny);
            wrapper.setLayoutX(nx * scale);
            wrapper.setLayoutY(ny * scale);
            populateProperties(e);
            event.consume();
        });
        wrapper.setOnMouseReleased(event -> {
            if (dragging) autosave();
            dragging = false;
        });
        wrapper.setOnMouseClicked(event -> {
            select(e);
            event.consume();
        });
        return wrapper;
    }

    private Node customImage(TemplateElement e) {
        try {
            Path path = TemplateStorageService.resolveAsset(template, e.getImagePath());
            if (path != null && Files.isRegularFile(path)) {
                Image image = new Image(path.toUri().toString());
                ImageView view = new ImageView(image);
                view.setPreserveRatio(e.isPreserveAspectRatio() && !"STRETCH".equals(e.getImageFit()));
                view.setFitWidth(e.getWidth() * scale);
                view.setFitHeight(e.getHeight() * scale);
                return view;
            }
        } catch (Exception ignored) { }
        return new Label("Image");
    }

    private String elementDisplay(TemplateElement e) {
        return switch (e.getType()) {
            case TEXT -> e.getText();
            case FIELD, IMAGE_FIELD -> {
                TemplateFieldDefinition f = TemplateFieldCatalog.find(template.getDocumentType(), e.getFieldKey());
                yield (e.getType() == ElementType.IMAGE_FIELD ? "▧ " : "") + (f == null ? e.getFieldKey() : f.label());
            }
            case IMAGE -> "Image";
            case WHITEOUT -> "Replace / Hide Area";
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
        selectedPdfText = null;
        selectedPdfImage = null;
        selectedPdfForm = null;
        selected = e;
        selectedId = e.getId();
        populateProperties(e);
        for (Node node : canvasPane.getChildren()) {
            node.getStyleClass().remove("pdf-designer-object-selected");
            node.getStyleClass().remove("pdf-existing-text-selected");
            node.getStyleClass().remove("pdf-existing-image-selected");
            node.getStyleClass().remove("pdf-existing-form-selected");
            if (Objects.equals(node.getProperties().get("templateElementId"), selectedId))
                node.getStyleClass().add("pdf-designer-object-selected");
        }
    }

    private void selectExisting(PdfTextRegion region) {
        selected = null;
        selectedId = null;
        selectedPdfImage = null;
        selectedPdfForm = null;
        selectedPdfText = region;
        populateExistingProperties(region);
        for (Node node : canvasPane.getChildren()) node.getStyleClass().remove("pdf-designer-object-selected");
        renderCanvas();
    }

    private void selectExistingImage(PdfImageRegion region) {
        selected = null;
        selectedId = null;
        selectedPdfText = null;
        selectedPdfForm = null;
        selectedPdfImage = region;
        lblSelection.setText("IMPORTED PDF IMAGE  •  convert to edit/move/replace");
        lblExistingOriginal.setText("Raster image • " + fmt(region.width()) + " × " + fmt(region.height()) + " pt");
        lblExistingOriginal.setVisible(true);
        lblExistingOriginal.setManaged(true);
        existingTextSection.setVisible(false);
        existingTextSection.setManaged(false);
        existingFormSection.setVisible(false);
        existingFormSection.setManaged(false);
        txtContent.clear();
        txtContent.setDisable(true);
        txtX.setText(fmt(region.x())); txtY.setText(fmt(region.y()));
        txtWidth.setText(fmt(region.width())); txtHeight.setText(fmt(region.height()));
        btnConvertExisting.setVisible(false); btnConvertExisting.setManaged(false);
        btnConvertImportedImage.setVisible(true); btnConvertImportedImage.setManaged(true);
        lblImageSource.setText("Imported PDF raster image. Convert it to a normal Studio image object before moving, resizing or replacing it.");
        propertiesTabs.getTabs().get(1).setDisable(false);
        propertiesTabs.getSelectionModel().select(1);
        renderCanvas();
    }

    private void selectExistingForm(PdfFormFieldRegion region) {
        selected = null;
        selectedId = null;
        selectedPdfText = null;
        selectedPdfImage = null;
        selectedPdfForm = region;
        lblSelection.setText("PDF FORM FIELD  •  " + region.fieldName());
        lblExistingOriginal.setText("Original value: " + (region.value() == null || region.value().isBlank() ? "(empty)" : region.value()));
        lblExistingOriginal.setVisible(true);
        lblExistingOriginal.setManaged(true);
        existingTextSection.setVisible(false); existingTextSection.setManaged(false);
        existingFormSection.setVisible(true); existingFormSection.setManaged(true);
        btnConvertExisting.setVisible(false); btnConvertExisting.setManaged(false);
        btnConvertImportedImage.setVisible(false); btnConvertImportedImage.setManaged(false);
        txtContent.setDisable(false);
        txtContent.setText(region.value() == null ? "" : region.value());
        txtX.setText(fmt(region.x())); txtY.setText(fmt(region.y()));
        txtWidth.setText(fmt(region.width())); txtHeight.setText(fmt(region.height()));
        txtFontSize.setText(fmt(Math.max(8, Math.min(12, region.height() * 0.55))));
        txtStrokeWidth.setText("0");
        chkBold.setSelected(false);
        colorText.setValue(Color.web("#172033"));
        btnApplyProperties.setText("Replace Form Field");
        propertiesTabs.getSelectionModel().select(0);
        renderCanvas();
    }

    private void restoreSelectionReference() {
        if (selectedId == null) return;
        selected = template.getElements().stream().filter(e -> e.getId().equals(selectedId)).findFirst().orElse(null);
        if (selected != null) populateProperties(selected);
        else clearProperties();
    }

    private void populateProperties(TemplateElement e) {
        selectedPdfText = null;
        selectedPdfImage = null;
        selectedPdfForm = null;
        btnConvertImportedImage.setVisible(false);
        btnConvertImportedImage.setManaged(false);
        existingFormSection.setVisible(false);
        existingFormSection.setManaged(false);
        lblSelection.setText(e.getType().name().replace('_', ' ') + (e.isLocked() ? "  •  LOCKED" : ""));
        lblExistingOriginal.setVisible(false);
        lblExistingOriginal.setManaged(false);
        existingTextSection.setVisible(false);
        existingTextSection.setManaged(false);
        existingFormSection.setVisible(false);
        existingFormSection.setManaged(false);
        btnConvertImportedImage.setVisible(false);
        btnConvertImportedImage.setManaged(false);
        btnConvertExisting.setVisible(false);
        btnConvertExisting.setManaged(false);
        btnApplyProperties.setText("Apply");

        boolean textEditable = e.getType() == ElementType.TEXT;
        txtContent.setDisable(!textEditable);
        if (e.getType() == ElementType.TEXT) txtContent.setText(e.getText());
        else if (e.getType() == ElementType.FIELD || e.getType() == ElementType.IMAGE_FIELD) {
            TemplateFieldDefinition f = TemplateFieldCatalog.find(template.getDocumentType(), e.getFieldKey());
            txtContent.setText(f == null ? e.getFieldKey() : f.label() + "  [" + e.getFieldKey() + "]");
        } else txtContent.setText(e.getType() == ElementType.IMAGE ? e.getImagePath() : "");

        txtX.setText(fmt(e.getX()));
        txtY.setText(fmt(e.getY()));
        txtWidth.setText(fmt(e.getWidth()));
        txtHeight.setText(fmt(e.getHeight()));
        txtFontSize.setText(fmt(e.getFontSize()));
        txtStrokeWidth.setText(fmt(e.getStrokeWidth()));
        chkBold.setSelected(e.isBold());
        chkLocked.setSelected(e.isLocked());
        colorText.setValue(Color.web(e.getTextColor()));
        colorFill.setValue(Color.web(e.getFillColor()));
        colorStroke.setValue(Color.web(e.getStrokeColor()));

        boolean table = e.getType() == ElementType.ITEM_TABLE;
        tablePropertiesSection.setVisible(table);
        tablePropertiesSection.setManaged(table);
        txtTableColumns.setText(String.join(",", e.getTableColumns()));
        txtRowHeight.setText(fmt(e.getRowHeight()));
        txtHeaderHeight.setText(fmt(e.getHeaderHeight()));

        boolean image = isImageElement(e);
        lblImageSource.setText(image ? imageSourceLabel(e) : "Select an image object to use Image controls.");
        cmbImageFit.getSelectionModel().select(e.getImageFit());
        chkPreserveRatio.setSelected(e.isPreserveAspectRatio());
        sldOpacity.setValue(e.getOpacity() * 100.0);
        txtRotation.setText(fmt(e.getRotation()));
        if (image) {
            propertiesTabs.getTabs().get(1).setDisable(false);
        } else {
            if (propertiesTabs.getSelectionModel().getSelectedIndex() == 1) propertiesTabs.getSelectionModel().select(0);
            propertiesTabs.getTabs().get(1).setDisable(true);
        }
    }

    private void populateExistingProperties(PdfTextRegion region) {
        selectedPdfImage = null;
        selectedPdfForm = null;
        btnConvertImportedImage.setVisible(false);
        btnConvertImportedImage.setManaged(false);
        existingFormSection.setVisible(false);
        existingFormSection.setManaged(false);
        lblSelection.setText("EXISTING PDF TEXT  •  double-click to edit");
        lblExistingOriginal.setText("Original: " + region.text());
        lblExistingOriginal.setVisible(true);
        lblExistingOriginal.setManaged(true);
        existingTextSection.setVisible(true);
        existingTextSection.setManaged(true);
        btnConvertExisting.setVisible(true);
        btnConvertExisting.setManaged(true);
        btnApplyProperties.setText("Replace Existing Text");
        propertiesTabs.getSelectionModel().select(0);
        propertiesTabs.getTabs().get(1).setDisable(true);

        txtContent.setDisable(false);
        txtContent.setText(region.text());
        txtX.setText(fmt(region.x()));
        txtY.setText(fmt(region.y()));
        txtWidth.setText(fmt(region.width()));
        txtHeight.setText(fmt(Math.max(region.height(), region.fontSize() * 1.45)));
        txtFontSize.setText(fmt(region.fontSize()));
        txtStrokeWidth.setText("0");
        chkBold.setSelected(false);
        chkLocked.setSelected(false);
        colorText.setValue(Color.web("#172033"));
        colorFill.setValue(Color.WHITE);
        colorStroke.setValue(Color.WHITE);
        tablePropertiesSection.setVisible(false);
        tablePropertiesSection.setManaged(false);
    }

    private void clearSelection() {
        selected = null;
        selectedId = null;
        selectedPdfText = null;
        selectedPdfImage = null;
        selectedPdfForm = null;
        clearProperties();
    }

    private void clearProperties() {
        if (lblSelection == null) return;
        lblSelection.setText(originalMode ? "Original PDF — protected read-only source" : previewMode ? "Final Preview — rendered output" :
                (existingTextMode ? "Click highlighted PDF text to replace or convert it" :
                        existingImageMode ? "Click a highlighted raster image to convert it into an editable object" :
                                formFieldMode ? "Click a highlighted PDF form field to replace its value" : "Select an object on the page"));
        lblExistingOriginal.setText("");
        lblExistingOriginal.setVisible(false);
        lblExistingOriginal.setManaged(false);
        existingTextSection.setVisible(false);
        existingTextSection.setManaged(false);
        existingFormSection.setVisible(false);
        existingFormSection.setManaged(false);
        btnConvertImportedImage.setVisible(false);
        btnConvertImportedImage.setManaged(false);
        btnConvertExisting.setVisible(false);
        btnConvertExisting.setManaged(false);
        btnApplyProperties.setText("Apply");
        txtContent.clear();
        txtX.clear(); txtY.clear(); txtWidth.clear(); txtHeight.clear();
        txtFontSize.clear(); txtStrokeWidth.clear(); txtTableColumns.clear(); txtRowHeight.clear(); txtHeaderHeight.clear(); txtRotation.clear();
        txtContent.setDisable(true);
        tablePropertiesSection.setVisible(false);
        tablePropertiesSection.setManaged(false);
        propertiesTabs.getTabs().get(1).setDisable(true);
        lblImageSource.setText("Select an image object");
        cmbImageFit.getSelectionModel().select("FIT");
        chkPreserveRatio.setSelected(true);
        sldOpacity.setValue(100);
    }

    private boolean isImageElement(TemplateElement e) {
        return e != null && (e.getType() == ElementType.IMAGE || e.getType() == ElementType.IMAGE_FIELD);
    }

    private String imageSourceLabel(TemplateElement e) {
        if (e.getType() == ElementType.IMAGE_FIELD) {
            TemplateFieldDefinition field = TemplateFieldCatalog.find(template.getDocumentType(), e.getFieldKey());
            return field == null ? e.getFieldKey() : "ERP image field: " + field.label();
        }
        String path = e.getImagePath();
        if (path == null || path.isBlank()) return "Image source not configured";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return "Template image: " + (slash >= 0 ? path.substring(slash + 1) : path);
    }

    @FXML private void bringToFront() { reorderSelected(Integer.MAX_VALUE); }
    @FXML private void sendToBack() { reorderSelected(Integer.MIN_VALUE); }
    @FXML private void moveForward() { reorderSelected(1); }
    @FXML private void moveBackward() { reorderSelected(-1); }

    private void reorderSelected(int direction) {
        if (selected == null || previewMode) return;
        List<TemplateElement> list = new ArrayList<>(template.getElements());
        int index = indexOfId(list, selected.getId());
        if (index < 0) return;
        int target;
        if (direction == Integer.MAX_VALUE) target = list.size() - 1;
        else if (direction == Integer.MIN_VALUE) target = 0;
        else target = clampIndex(index + direction, 0, list.size() - 1);
        if (target == index) return;
        checkpoint();
        TemplateElement element = list.remove(index);
        list.add(target, element);
        template.setElements(list);
        autosave();
        renderCanvas();
    }

    @FXML private void alignLeft() { alignSelected(12.0, null); }
    @FXML private void alignCenter() { alignSelected((pageWidth - selectedWidth()) / 2.0, null); }
    @FXML private void alignRight() { alignSelected(Math.max(0, pageWidth - selectedWidth() - 12), null); }
    @FXML private void alignTop() { alignSelected(null, 12.0); }
    @FXML private void alignMiddle() { alignSelected(null, (pageHeight - selectedHeight()) / 2.0); }
    @FXML private void alignBottom() { alignSelected(null, Math.max(0, pageHeight - selectedHeight() - 12)); }

    private double selectedWidth() { return selected == null ? 0 : selected.getWidth(); }
    private double selectedHeight() { return selected == null ? 0 : selected.getHeight(); }

    private void alignSelected(Double x, Double y) {
        if (selected == null || previewMode || selected.isLocked()) return;
        checkpoint();
        if (x != null) selected.setX(x);
        if (y != null) selected.setY(y);
        autosave();
        renderCanvas();
    }

    @FXML private void toggleSelectedLock() {
        if (selected == null || previewMode) return;
        checkpoint();
        selected.setLocked(!selected.isLocked());
        autosave();
        renderCanvas();
    }

    private static int indexOfId(List<TemplateElement> list, String id) {
        for (int i = 0; i < list.size(); i++) if (Objects.equals(list.get(i).getId(), id)) return i;
        return -1;
    }

    private static int clampIndex(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void refreshMeta() {
        lblTemplateMeta.setText(template.getDocumentType().label() + "  •  " + template.getStatus()
                + "  •  v" + template.getVersion() + (template.isDefaultTemplate() ? "  •  ★ DEFAULT" : ""));
    }

    private static double parse(TextField field, double fallback) {
        String text = field.getText();
        if (text == null || text.isBlank()) return fallback;
        return Double.parseDouble(text.trim());
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < .001 ? Long.toString(Math.round(v)) : String.format(Locale.ENGLISH, "%.1f", v);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String hex(Color c) {
        Color color = c == null ? Color.web("#172033") : c;
        return String.format("#%02X%02X%02X", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
    }

    private static List<TemplateElement> copyElements(List<TemplateElement> source) {
        List<TemplateElement> out = new ArrayList<>();
        for (TemplateElement e : source) {
            TemplateElement c = new TemplateElement();
            c.setId(e.getId());
            c.setType(e.getType());
            c.setPageIndex(e.getPageIndex());
            c.setX(e.getX()); c.setY(e.getY()); c.setWidth(e.getWidth()); c.setHeight(e.getHeight());
            c.setText(e.getText()); c.setFieldKey(e.getFieldKey()); c.setFontSize(e.getFontSize()); c.setBold(e.isBold());
            c.setTextColor(e.getTextColor()); c.setFillColor(e.getFillColor()); c.setStrokeColor(e.getStrokeColor()); c.setStrokeWidth(e.getStrokeWidth());
            c.setImagePath(e.getImagePath()); c.setOpacity(e.getOpacity()); c.setRotation(e.getRotation()); c.setPreserveAspectRatio(e.isPreserveAspectRatio()); c.setImageFit(e.getImageFit());
            c.setLocked(e.isLocked()); c.setReplacementGroupId(e.getReplacementGroupId()); c.setReplacementSourceKey(e.getReplacementSourceKey());
            c.setTableColumns(e.getTableColumns()); c.setRowHeight(e.getRowHeight()); c.setHeaderHeight(e.getHeaderHeight());
            out.add(c);
        }
        return out;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }
}
