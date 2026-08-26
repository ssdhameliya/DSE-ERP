package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.controller.DashboardController;
import org.example.documentstudio.model.*;
import org.example.documentstudio.service.*;
import org.example.documentstudio.util.PdfPreviewSupport;
import org.example.navigation.ScreenLifecycle;
import org.example.shortcut.ShortcutRegistry;
import org.example.shortcut.ShortcutRegistry.Action;
import org.example.util.ModernDialog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * PDF Studio 3 — non-destructive object-driven WYSIWYG PDF template editor.
 *
 * <p>The imported PDF remains the protected fidelity layer. Extracted text, raster images and
 * vector regions are always selectable hit targets; editing a detected source object materializes
 * an overlay and masks only that source region. The editor has no mutually exclusive text/image/
 * block/map modes: click anything and the inspector reflects that object immediately.</p>
 *
 * <p>This controller deliberately depends only on PDF Studio model/render/storage classes and the
 * shared ERP data gateway. Excel Studio is not referenced.</p>
 */
public class PdfStudioController implements ScreenLifecycle {
    private static final double BASE_SCALE = 1.15;
    private static final String FIELD_DRAG_PREFIX = "DSE_PDF_FIELD:";
    private static final String CREATE_DRAG_PREFIX = "DSE_PDF_CREATE:";
    private static final String LOGO_PATH_KEY = "company.logoPath";
    private static final String SIGNATURE_PATH_KEY = "company.signaturePath";
    private static final String QR_PATH_KEY = "payment.qrImagePath";

    @FXML private BorderPane root;
    @FXML private Label lblTemplateName, lblTemplateMeta, lblSaveState, lblZoom, lblPageSize, lblSelection, lblPageWarning;
    @FXML private Label lblMappingPercent, lblMappingSummary, lblInspectorType, lblInspectorHint;
    @FXML private ProgressBar mappingProgress;
    @FXML private Button btnDesignMode, btnDataPreviewMode, btnFinalMode, btnPublish, btnSaveDefault;
    @FXML private ComboBox<DocumentSample> cmbSampleDocument;
    @FXML private ComboBox<String> cmbFieldBinding, cmbFontFamily, cmbTextFit, cmbTextAlignment, cmbImageFit, cmbPageRule;
    @FXML private TextField txtFieldSearch;
    @FXML private ListView<TemplateFieldDefinition> lstFields;
    @FXML private ListView<String> lstPages;
    @FXML private ListView<TemplateElement> lstLayers;
    @FXML private ScrollPane canvasScroll;
    @FXML private StackPane canvasHolder;
    @FXML private Pane canvasPane;
    @FXML private Slider zoomSlider;
    @FXML private CheckBox chkSnap, chkBold, chkItalic, chkInheritParent, chkPaddingLinked, chkFillEnabled,
            chkStrokeEnabled, chkPreserveRatio, chkUseSourceTableDesign, chkLocked, chkVisible;
    @FXML private ColorPicker colorText, colorFill, colorStroke;
    @FXML private TextArea txtContent;
    @FXML private TextField txtFontSize, txtLineSpacing, txtX, txtY, txtWidth, txtHeight, txtRotation, txtOpacity,
            txtStrokeWidth, txtRadius, txtPadTop, txtPadRight, txtPadBottom, txtPadLeft,
            txtTableColumns, txtRowHeight, txtHeaderHeight;
    @FXML private VBox textSection, imageSection, repeaterSection;
    @FXML private TabPane leftTabs;

    private DocumentTemplate template;
    private Path sourcePdf;
    private Path originalPdf;
    private Path previewPdf;
    private boolean previewMode;
    private boolean dataPreviewMode;
    private int pageIndex;
    private int sourcePageCount = 1;
    private double pageWidth = 595;
    private double pageHeight = 842;
    private double scale = BASE_SCALE;

    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private PdfTextRegion selectedSourceText;
    private PdfImageRegion selectedSourceImage;
    private PdfImageExtractionService.VectorRegion selectedSourceVector;
    private final Map<Integer,List<PdfTextRegion>> textCache = new HashMap<>();
    private final Map<Integer,List<PdfImageRegion>> imageCache = new HashMap<>();
    private final Map<Integer,List<PdfImageExtractionService.VectorRegion>> vectorCache = new HashMap<>();
    private final Map<Integer,Image> sourcePageImages = new HashMap<>();
    private final Set<Integer> loadingPages = new HashSet<>();
    private final AtomicInteger renderSequence = new AtomicInteger();

    private final Deque<List<TemplateElement>> undo = new ArrayDeque<>();
    private final Deque<List<TemplateElement>> redo = new ArrayDeque<>();
    private TemplateElement formatClipboard;
    private TemplateData currentPreviewData;
    private PdfAutoMappingService.Analysis currentMappingAnalysis = new PdfAutoMappingService.Analysis(List.of(),0,0,0,0);
    private boolean inspectorSync;
    private boolean dragging;
    private double dragSceneX, dragSceneY;
    private final Map<String,double[]> dragOrigins = new HashMap<>();
    private TextArea inlineEditor;
    private final List<Line> smartGuideLines = new ArrayList<>();

    private enum Handle { NW, N, NE, E, SE, S, SW, W }

    @FXML
    public void initialize() {
        String id = DocumentStudioContext.consume();
        template = TemplateStorageService.find(id).orElse(null);
        if (template == null) {
            Platform.runLater(() -> {
                ModernDialog.error(root, "Template unavailable", "PDF Studio", "The selected template could not be found.");
                backToLibrary();
            });
            return;
        }
        try {
            TemplateStorageService.migrateToStudioV3(template);
            sourcePdf = TemplateStorageService.sourcePdf(template);
            originalPdf = TemplateStorageService.originalPdf(template);
            var size = PdfPreviewSupport.pageSize(sourcePdf, 0);
            pageWidth = size.width(); pageHeight = size.height(); sourcePageCount = size.pageCount();
        } catch (Exception error) {
            Platform.runLater(() -> ModernDialog.error(root, "PDF could not be opened", "PDF Studio", rootMessage(error)));
            return;
        }

        configureInspectorControls();
        configureFields();
        configurePages(sourcePageCount);
        configureLayers();
        configureSamples();
        configureDragAndDrop();
        configureCanvasSelection();
        installPropertyListeners();
        installKeyboardShortcuts();

        zoomSlider.valueProperty().addListener((obs, old, value) -> {
            scale = BASE_SCALE * value.doubleValue() / 100.0;
            lblZoom.setText(Math.round(value.doubleValue()) + "%");
            renderCanvas();
        });
        lstPages.getSelectionModel().selectedIndexProperty().addListener((obs, old, value) -> {
            int target = value == null ? -1 : value.intValue();
            if (target >= 0 && target != pageIndex) {
                pageIndex = target;
                clearSelection();
                renderCanvas();
                ensurePageObjects(pageIndex);
            }
        });
        lstLayers.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (inspectorSync || value == null) return;
            selectOnly(value);
        });

        refreshMeta();
        updateDefaultButton();
        updateModeButtons();
        renderCanvas();
        ensurePageObjects(pageIndex);
        Platform.runLater(this::fitWidth);
    }

    @Override public void onScreenShown(boolean reused) {
        if (template != null) {
            refreshMeta();
            renderCanvas();
            ensurePageObjects(pageIndex);
        }
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    private void configureInspectorControls() {
        colorText.setValue(Color.web("#172033"));
        colorFill.setValue(Color.WHITE);
        colorStroke.setValue(Color.web("#94A3B8"));
        cmbFontFamily.setItems(FXCollections.observableArrayList("HELVETICA", "TIMES", "COURIER"));
        cmbTextFit.setItems(FXCollections.observableArrayList("SHRINK", "WRAP", "CLIP", "FIXED"));
        cmbTextAlignment.setItems(FXCollections.observableArrayList("LEFT", "CENTER", "RIGHT"));
        cmbImageFit.setItems(FXCollections.observableArrayList("FIT", "FILL", "STRETCH"));
        cmbPageRule.setItems(FXCollections.observableArrayList("AUTO", "FIXED", "FIRST", "EVERY", "CONTINUATION", "LAST"));
        cmbFontFamily.getSelectionModel().select("HELVETICA");
        cmbTextFit.getSelectionModel().select("SHRINK");
        cmbTextAlignment.getSelectionModel().select("LEFT");
        cmbImageFit.getSelectionModel().select("FIT");
        cmbPageRule.getSelectionModel().select("AUTO");
        clearInspector();
    }

    private void configureFields() {
        refreshFieldList("");
        lstFields.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(TemplateFieldDefinition item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.category() + "  •  " + item.label());
            }
        });
        lstFields.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) addSelectedField();
        });
        txtFieldSearch.textProperty().addListener((obs, old, value) -> refreshFieldList(value));
        lstFields.setOnDragDetected(event -> {
            TemplateFieldDefinition field = lstFields.getSelectionModel().getSelectedItem();
            if (field == null || previewMode) return;
            Dragboard board = lstFields.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(FIELD_DRAG_PREFIX + field.key());
            board.setContent(content);
            event.consume();
        });
    }

    private void refreshFieldList(String query) {
        if (template == null || lstFields == null) return;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<TemplateFieldDefinition> fields = TemplateFieldCatalog.pdfFieldsFor(template.getDocumentType()).stream()
                .filter(f -> q.isBlank() || f.label().toLowerCase(Locale.ROOT).contains(q)
                        || f.category().toLowerCase(Locale.ROOT).contains(q)
                        || f.key().toLowerCase(Locale.ROOT).contains(q))
                .toList();
        lstFields.setItems(FXCollections.observableArrayList(fields));
        refreshFieldBindingChoices(fields);
    }

    private void refreshFieldBindingChoices(List<TemplateFieldDefinition> fields) {
        if (cmbFieldBinding == null) return;
        String previous = cmbFieldBinding.getValue();
        List<String> values = new ArrayList<>();
        values.add("— No single binding —");
        for (TemplateFieldDefinition f : fields) values.add(f.key() + "  •  " + f.label());
        inspectorSync = true;
        try {
            cmbFieldBinding.setItems(FXCollections.observableArrayList(values));
            if (previous != null && values.contains(previous)) cmbFieldBinding.setValue(previous);
            else cmbFieldBinding.getSelectionModel().selectFirst();
        } finally { inspectorSync = false; }
    }

    private void configurePages(int count) {
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < Math.max(1, count); i++) pages.add("Page " + (i + 1));
        lstPages.setItems(FXCollections.observableArrayList(pages));
        pageIndex = Math.max(0, Math.min(pageIndex, pages.size() - 1));
        lstPages.getSelectionModel().select(pageIndex);
    }

    private void configureLayers() {
        lstLayers.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(TemplateElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String name = displayName(item);
                setText((item.isVisible() ? "◉ " : "○ ") + (item.isLocked() ? "🔒 " : "") + name);
            }
        });
        refreshLayers();
    }

    private void refreshLayers() {
        if (template == null || lstLayers == null) return;
        List<TemplateElement> page = template.getElements().stream().filter(e -> e.getPageIndex() == pageIndex).toList();
        inspectorSync = true;
        try {
            lstLayers.setItems(FXCollections.observableArrayList(page));
            TemplateElement single = selectedElement();
            if (single != null) lstLayers.getSelectionModel().select(single);
        } finally { inspectorSync = false; }
    }

    private void configureSamples() {
        cmbSampleDocument.setItems(FXCollections.observableArrayList());
        cmbSampleDocument.getItems().add(null);
        cmbSampleDocument.getSelectionModel().selectFirst();
        boolean enabled = template.getDocumentType().isErpConnected() && DocumentDataService.supportsRealData(template.getDocumentType());
        cmbSampleDocument.setDisable(!enabled);
        cmbSampleDocument.setPromptText(enabled ? "Choose a real ERP record" : "No live-record connector for this document type");
        currentPreviewData = DocumentDataService.sample(template.getDocumentType());
        if (!enabled) {
            analyzeMapping(false);
            return;
        }
        CompletableFuture.supplyAsync(() -> DocumentDataService.listSamples(template.getDocumentType()))
                .thenAccept(samples -> Platform.runLater(() -> {
                    if (template == null) return;
                    cmbSampleDocument.getItems().setAll(samples);
                    cmbSampleDocument.getItems().addFirst(null);
                    cmbSampleDocument.getSelectionModel().selectFirst();
                    findLikelyRecordAndAnalyze(samples);
                }));
    }

    private void findLikelyRecordAndAnalyze(List<DocumentSample> samples) {
        ensurePageObjects(pageIndex);
        CompletableFuture.supplyAsync(() -> {
            try {
                List<PdfTextRegion> all = extractAllText();
                return PdfAutoMappingService.findLikelySample(template.getDocumentType(), all);
            } catch (Exception ignored) { return Optional.<DocumentSample>empty(); }
        }).thenAccept(found -> Platform.runLater(() -> {
            if (found.isPresent()) {
                DocumentSample sample = found.get();
                cmbSampleDocument.getSelectionModel().select(sample);
                // Loading a template must never rewrite/materialize imported PDF
                // objects on the user's behalf. Real data may be loaded for a
                // preview suggestion, but mappings are applied only when the user
                // explicitly chooses Auto Map Fields.
                loadPreviewData(sample, false);
            } else {
                currentPreviewData = DocumentDataService.sample(template.getDocumentType());
                analyzeMapping(false);
            }
        }));
    }

    private void configureDragAndDrop() {
        canvasPane.setOnDragOver(event -> {
            if (previewMode || !event.getDragboard().hasString()) return;
            String s = event.getDragboard().getString();
            if (s != null && (s.startsWith(FIELD_DRAG_PREFIX) || s.startsWith(CREATE_DRAG_PREFIX))) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        canvasPane.setOnDragDropped(event -> {
            if (previewMode || !event.getDragboard().hasString()) return;
            String payload = event.getDragboard().getString();
            var local = canvasPane.sceneToLocal(event.getSceneX(), event.getSceneY());
            double x = local.getX() / scale, y = local.getY() / scale;
            if (payload.startsWith(FIELD_DRAG_PREFIX)) {
                String key = payload.substring(FIELD_DRAG_PREFIX.length());
                TemplateFieldDefinition field = TemplateFieldCatalog.findPdf(template.getDocumentType(), key);
                if (field != null) dropField(field, x, y);
            } else if (payload.startsWith(CREATE_DRAG_PREFIX)) {
                createAt(payload.substring(CREATE_DRAG_PREFIX.length()), x, y);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private void configureCanvasSelection() {
        canvasPane.setOnMouseClicked(event -> {
            if (event.getTarget() != canvasPane) return;
            if (!event.isShiftDown()) clearSelection();
        });
    }

    private void installPropertyListeners() {
        // Controls with explicit commit semantics.
        for (TextField field : List.of(txtFontSize, txtLineSpacing, txtX, txtY, txtWidth, txtHeight, txtRotation, txtOpacity,
                txtStrokeWidth, txtRadius, txtPadTop, txtPadRight, txtPadBottom, txtPadLeft,
                txtTableColumns, txtRowHeight, txtHeaderHeight)) {
            field.setOnAction(e -> applyInspector());
            field.focusedProperty().addListener((obs, old, focused) -> { if (!focused && old) applyInspectorSilently(); });
        }
        txtContent.focusedProperty().addListener((obs, old, focused) -> { if (!focused && old) applyInspectorSilently(); });
        cmbFontFamily.setOnAction(e -> applyInspectorSilently());
        cmbTextFit.setOnAction(e -> applyInspectorSilently());
        cmbTextAlignment.setOnAction(e -> applyInspectorSilently());
        cmbImageFit.setOnAction(e -> applyInspectorSilently());
        cmbPageRule.setOnAction(e -> applyInspectorSilently());
        cmbFieldBinding.setOnAction(e -> bindingChanged());
        colorText.setOnAction(e -> applyInspectorSilently());
        colorFill.setOnAction(e -> applyInspectorSilently());
        colorStroke.setOnAction(e -> applyInspectorSilently());
        for (CheckBox box : List.of(chkBold, chkItalic, chkFillEnabled, chkStrokeEnabled,
                chkPreserveRatio, chkUseSourceTableDesign, chkLocked, chkVisible)) {
            box.setOnAction(e -> applyInspectorSilently());
        }
        chkInheritParent.setOnAction(e -> inheritanceChanged());
        chkPaddingLinked.setOnAction(e -> {
            if (chkPaddingLinked.isSelected() && !txtPadTop.getText().isBlank()) {
                inspectorSync = true;
                try { txtPadRight.setText(txtPadTop.getText()); txtPadBottom.setText(txtPadTop.getText()); txtPadLeft.setText(txtPadTop.getText()); }
                finally { inspectorSync = false; }
                applyInspectorSilently();
            }
        });
    }

    private void installKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (root == null || root.getScene() == null) return;
            root.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (isTextInput(event.getTarget())) return;
                if (ShortcutRegistry.matches(event, Action.PDF_UNDO)) { undo(); event.consume(); return; }
                if (ShortcutRegistry.matches(event, Action.PDF_REDO)) { redo(); event.consume(); return; }
                if (ShortcutRegistry.matches(event, Action.PDF_DUPLICATE)) { duplicateSelected(); event.consume(); return; }
                if (ShortcutRegistry.matches(event, Action.PDF_DELETE)) { deleteSelected(); event.consume(); return; }
                if (event.isControlDown() || event.isMetaDown()) {
                    if (event.getCode() == KeyCode.C) { copySelectedObjectsToClipboard(); event.consume(); return; }
                    if (event.getCode() == KeyCode.V) { pasteObjectsFromClipboard(); event.consume(); return; }
                }
                if (!selectedIds.isEmpty() && Set.of(KeyCode.LEFT,KeyCode.RIGHT,KeyCode.UP,KeyCode.DOWN).contains(event.getCode())) {
                    nudgeSelection(event.getCode(), event.isShiftDown() ? 10 : 1); event.consume();
                }
            });
        });
    }

    private boolean isTextInput(Object target) { return target instanceof TextInputControl || target instanceof ComboBoxBase<?>; }

    // ---------------------------------------------------------------------
    // Auto mapping
    // ---------------------------------------------------------------------

    @FXML private void sampleChanged() {
        if (inspectorSync || template == null) return;
        loadPreviewData(cmbSampleDocument.getValue(), false);
    }

    private void loadPreviewData(DocumentSample sample, boolean allowAutoApply) {
        if (sample == null) {
            currentPreviewData = DocumentDataService.sample(template.getDocumentType());
            analyzeMapping(allowAutoApply);
            if(dataPreviewMode)renderCanvas();
            return;
        }
        CompletableFuture.supplyAsync(() -> DocumentDataService.load(template.getDocumentType(), sample.id()))
                .thenAccept(data -> Platform.runLater(() -> {
                    currentPreviewData = data;
                    analyzeMapping(allowAutoApply);
                    if(dataPreviewMode)renderCanvas();
                }))
                .exceptionally(error -> { Platform.runLater(() -> ModernDialog.error(root, "Record could not be loaded", "PDF Studio", rootMessage(error))); return null; });
    }

    private void analyzeMapping(boolean allowAutoApply) {
        if (template == null || !template.getDocumentType().isErpConnected()) {
            updateMappingUi(new PdfAutoMappingService.Analysis(List.of(),0,0,0,0));
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                List<PdfTextRegion> all = extractAllText();
                return PdfAutoMappingService.analyze(template.getDocumentType(), all,
                        currentPreviewData == null ? DocumentDataService.sample(template.getDocumentType()) : currentPreviewData);
            } catch (Exception error) {
                return new PdfAutoMappingService.Analysis(List.of(),0,0,0,0);
            }
        }).thenAccept(analysis -> Platform.runLater(() -> {
            currentMappingAnalysis = analysis;
            updateMappingUi(analysis);
            if (allowAutoApply && shouldApplyInitialAutoMap(analysis)) applyAutoMappings(analysis, true);
        }));
    }

    private boolean shouldApplyInitialAutoMap(PdfAutoMappingService.Analysis analysis) {
        return template.getElements().isEmpty() && analysis.highConfidence() > 0;
    }

    @FXML private void autoMapNow() {
        if (template == null || previewMode) return;
        if (!template.getDocumentType().isErpConnected()) {
            ModernDialog.info(root, "ERP data is not connected", "Auto Map", "Connect this template to an ERP document type before using Auto Map.");
            return;
        }
        List<TemplateElement> current = new ArrayList<>(template.getElements());
        boolean repeatableRegion = wouldAddItemRepeater(current) || wouldAddChargeRepeater(current);
        if (currentMappingAnalysis.mappings().isEmpty() && !repeatableRegion) {
            analyzeMapping(false);
            ModernDialog.info(root, "Mapping analyzed", "Auto Map", "No mappable printed ERP values or repeating item/charge regions were found yet. Choose a real preview record and run Auto Map again.");
            return;
        }
        applyAutoMappings(currentMappingAnalysis, false);
    }

    private void applyAutoMappings(PdfAutoMappingService.Analysis analysis, boolean highOnly) {
        List<PdfAutoMappingService.Mapping> mappings = analysis.mappings().stream()
                .filter(m -> !highOnly || m.confidence() >= .90)
                .filter(m -> !hasReplacementFor(sourceKey(m.region())))
                .toList();
        List<TemplateElement> list = new ArrayList<>(template.getElements());
        boolean itemRepeaterAdded = wouldAddItemRepeater(list);
        boolean chargeRepeaterAdded = wouldAddChargeRepeater(list);
        if (mappings.isEmpty() && !itemRepeaterAdded && !chargeRepeaterAdded) return;

        checkpoint();
        for (PdfAutoMappingService.Mapping mapping : mappings) {
            addSourceTextReplacement(list, mapping.region(), mapping.expression(), mapping.fieldKey());
        }
        itemRepeaterAdded = autoCreateItemRepeaterIfDetected(list);
        chargeRepeaterAdded = autoCreateChargeRepeaterIfDetected(list);
        template.setElements(list);
        autosave();
        renderCanvas();
        updateMappingUi(analysis);
        List<String> summary = new ArrayList<>();
        if (!mappings.isEmpty()) summary.add(mappings.size() + " field" + (mappings.size() == 1 ? "" : "s"));
        if (itemRepeaterAdded) summary.add("item repeater");
        if (chargeRepeaterAdded) summary.add("charge repeater");
        lblSaveState.setText("Auto mapped " + String.join(" + ", summary) + " ✓");
    }

    private boolean wouldAddItemRepeater(List<TemplateElement> list) {
        if (list.stream().anyMatch(e -> e.getType() == ElementType.ITEM_TABLE)) return false;
        return currentPreviewData != null && !currentPreviewData.items().isEmpty()
                && PdfAutoMappingService.detectItemHeader(extractAllText()).isPresent();
    }

    private boolean wouldAddChargeRepeater(List<TemplateElement> list) {
        if (list.stream().anyMatch(e -> e.getType() == ElementType.CHARGE_TABLE)) return false;
        return currentPreviewData != null && !currentPreviewData.charges().isEmpty()
                && PdfAutoMappingService.detectChargeRegion(extractAllText(), currentPreviewData).isPresent();
    }

    private boolean autoCreateItemRepeaterIfDetected(List<TemplateElement> list) {
        if (list.stream().anyMatch(e -> e.getType() == ElementType.ITEM_TABLE)) return false;
        if (currentPreviewData == null || currentPreviewData.items().isEmpty()) return false;
        List<PdfTextRegion> allRegions = extractAllText();
        Optional<PdfTextRegion> headerOpt = PdfAutoMappingService.detectItemHeader(allRegions);
        if (headerOpt.isEmpty()) return false;
        PdfTextRegion header = headerOpt.get();
        List<PdfTextRegion> regions = allRegions.stream().filter(r -> r.pageIndex() == header.pageIndex()).toList();
        double[] page = pageSizeFor(header.pageIndex());
        double localPageWidth = page[0], localPageHeight = page[1];
        double x = Math.max(0, header.x());
        double width = Math.max(header.width(), localPageWidth - x - 8);
        double bottom = Math.min(localPageHeight - 8, header.y() + Math.max(160, localPageHeight * .38));
        for (PdfTextRegion r : regions) {
            String n = PdfAutoMappingService.normalize(r.text());
            if (r.y() > header.y() + 40 && (n.contains("basic amount") || n.contains("subtotal") || n.contains("bank name") || n.contains("gross total"))) {
                bottom = Math.min(bottom, Math.max(header.y() + 70, r.y() - 5));
                break;
            }
        }
        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, header.pageIndex(), x, header.y(), width, Math.max(70, bottom - header.y()));
        table.setUseSourceTableDesign(true);
        table.setHeaderHeight(Math.max(16, header.height() + 3));
        table.setRowHeight(20);
        table.setFontSize(7.5);
        table.setTableColumns(List.of("serial","hsn","descriptionWithRemarks","quantity","rate","unit","taxable"));
        table.setFillEnabled(false);
        table.setStrokeEnabled(false);
        list.add(table);
        maskPrintedFirstItemValues(list, header, bottom, regions);
        return true;
    }

    private void maskPrintedFirstItemValues(List<TemplateElement> list, PdfTextRegion header, double bottom, List<PdfTextRegion> regions) {
        if (currentPreviewData == null || currentPreviewData.items().isEmpty()) return;
        var item = currentPreviewData.items().getFirst();
        Set<String> values = new HashSet<>();
        addNorm(values, item.getHsn()); addNorm(values, item.getDescription()); addNorm(values, item.getRemarks());
        addNorm(values, item.getUnit()); addNorm(values, String.valueOf(item.getQuantity())); addNorm(values, String.valueOf(item.getRate()));
        addNorm(values, String.valueOf(item.getTaxableAmount())); addNorm(values, String.valueOf(item.getTotalAmount()));
        for (PdfTextRegion r : regions) {
            if (r.y() <= header.y()+header.height() || r.y() >= bottom) continue;
            String n = PdfAutoMappingService.normalize(r.text());
            if (n.isBlank() || values.stream().noneMatch(v -> !v.isBlank() && (n.equals(v) || n.contains(v)))) continue;
            String key = sourceKey(r);
            if (hasReplacementFor(key) || list.stream().anyMatch(e -> key.equals(e.getReplacementSourceKey()))) continue;
            list.add(sourceMask(r, key));
        }
    }

    private boolean autoCreateChargeRepeaterIfDetected(List<TemplateElement> list) {
        if (list.stream().anyMatch(e -> e.getType() == ElementType.CHARGE_TABLE)) return false;
        if (currentPreviewData == null || currentPreviewData.charges().isEmpty()) return false;
        Optional<PdfAutoMappingService.ChargeRegion> detected = PdfAutoMappingService.detectChargeRegion(extractAllText(), currentPreviewData);
        if (detected.isEmpty()) return false;
        PdfAutoMappingService.ChargeRegion region = detected.get();
        TemplateElement table = TemplateElement.of(ElementType.CHARGE_TABLE, region.pageIndex(), region.x(), region.y(), region.width(), Math.max(region.height(), region.rowHeight()));
        table.setUseSourceTableDesign(true);
        table.setHeaderHeight(0);
        table.setRowHeight(region.rowHeight());
        table.setFontSize(7.5);
        table.setTableColumns(List.of("type", "amount"));
        table.setFillEnabled(false);
        table.setStrokeEnabled(false);
        list.add(table);
        for (PdfTextRegion source : region.sourceRegions()) {
            String key = sourceKey(source);
            if (hasReplacementFor(key) || list.stream().anyMatch(e -> key.equals(e.getReplacementSourceKey()))) continue;
            list.add(sourceMask(source, key));
        }
        return true;
    }

    private double[] pageSizeFor(int page) {
        try {
            var size = PdfPreviewSupport.pageSize(sourcePdf, page);
            return new double[]{size.width(), size.height()};
        } catch (Exception ignored) {
            return new double[]{pageWidth, pageHeight};
        }
    }

    private void addNorm(Set<String> values, String value) { String n = PdfAutoMappingService.normalize(value); if (!n.isBlank()) values.add(n); }
    private void addNorm(Set<String> values, double value) { addNorm(values, String.valueOf(value)); }

    private List<PdfTextRegion> extractAllText() {
        List<PdfTextRegion> out = new ArrayList<>();
        for (int p = 0; p < sourcePageCount; p++) {
            try {
                List<PdfTextRegion> regions = textCache.computeIfAbsent(p, page -> {
                    try { return PdfTextExtractionService.extract(sourcePdf, page); }
                    catch (Exception ignored) { return List.of(); }
                });
                out.addAll(regions);
            } catch (Exception ignored) { }
        }
        return out;
    }

    private void updateMappingUi(PdfAutoMappingService.Analysis analysis) {
        if (analysis == null || analysis.detected() == 0) {
            mappingProgress.setProgress(0); lblMappingPercent.setText("0%"); lblMappingSummary.setText("No mappable regions detected yet");
            return;
        }
        int appliedText = (int) analysis.mappings().stream().filter(m -> hasReplacementFor(sourceKey(m.region()))).count();
        int repeaterBonus = 0;
        List<PdfTextRegion> detectedRegions = extractAllText();
        if (template.getElements().stream().anyMatch(e -> e.getType() == ElementType.ITEM_TABLE)
                && PdfAutoMappingService.detectItemHeader(detectedRegions).isPresent()) repeaterBonus++;
        if (template.getElements().stream().anyMatch(e -> e.getType() == ElementType.CHARGE_TABLE)
                && currentPreviewData != null && PdfAutoMappingService.detectChargeRegion(detectedRegions, currentPreviewData).isPresent()) repeaterBonus++;
        int mapped = Math.min(analysis.detected(), appliedText + repeaterBonus);
        int review = Math.min(Math.max(0, analysis.detected() - mapped),
                (int) analysis.mappings().stream().filter(m -> !hasReplacementFor(sourceKey(m.region()))).count());
        int unmapped = Math.max(0, analysis.detected() - mapped - review);
        int pct = (int) Math.round(mapped * 100.0 / Math.max(1, analysis.detected()));
        mappingProgress.setProgress(pct / 100.0);
        lblMappingPercent.setText(pct + "%");
        lblMappingSummary.setText(mapped + " mapped • " + review + " review • " + unmapped + " unmapped");
    }

    // ---------------------------------------------------------------------
    // Canvas loading and rendering
    // ---------------------------------------------------------------------

    private void ensurePageObjects(int page) {
        if (previewMode || loadingPages.contains(page)) return;
        if (textCache.containsKey(page) && imageCache.containsKey(page) && vectorCache.containsKey(page)) return;
        loadingPages.add(page);
        CompletableFuture.runAsync(() -> {
            try {
                textCache.computeIfAbsent(page, p -> {
                    try { return PdfTextExtractionService.extract(sourcePdf, p); } catch (Exception e) { return List.of(); }
                });
                imageCache.computeIfAbsent(page, p -> {
                    try { return PdfImageExtractionService.extract(sourcePdf, p, WorkspaceManager.getTempFolder().resolve("pdf-studio-v3-images")); }
                    catch (Exception e) { return List.of(); }
                });
                vectorCache.computeIfAbsent(page, p -> {
                    try { return PdfImageExtractionService.extractVectors(sourcePdf, p); } catch (Exception e) { return List.of(); }
                });
            } finally {
                Platform.runLater(() -> { loadingPages.remove(page); if (pageIndex == page && !previewMode) renderCanvas(); });
            }
        });
    }

    private void renderCanvas() {
        if (template == null || sourcePdf == null || canvasPane == null) return;
        closeInlineEditor(false);
        Path pdf = previewMode && previewPdf != null ? previewPdf : sourcePdf;
        int sequence = renderSequence.incrementAndGet();
        try {
            var size = PdfPreviewSupport.pageSize(pdf, pageIndex);
            pageWidth = size.width(); pageHeight = size.height();
            lblPageSize.setText(String.format(Locale.ENGLISH, "%.0f × %.0f pt • Page %d/%d", pageWidth, pageHeight, pageIndex+1, size.pageCount()));
        } catch (Exception ignored) { }
        double canvasW = pageWidth * scale, canvasH = pageHeight * scale;
        canvasPane.setPrefSize(canvasW, canvasH);
        canvasPane.setMinSize(canvasW, canvasH);
        canvasPane.setMaxSize(canvasW, canvasH);
        canvasHolder.setPrefSize(canvasW + 60, canvasH + 60);
        canvasPane.getChildren().clear();
        smartGuideLines.clear();

        CompletableFuture.supplyAsync(() -> {
            try { return PdfPreviewSupport.renderPage(pdf, pageIndex, (float)Math.max(72, 72*scale)); }
            catch (Exception e) { return null; }
        }).thenAccept(image -> Platform.runLater(() -> {
            if (renderSequence.get() != sequence || image == null) return;
            if (!previewMode) sourcePageImages.put(pageIndex, image);
            ImageView background = new ImageView(image);
            background.setFitWidth(canvasW); background.setFitHeight(canvasH); background.setMouseTransparent(true);
            canvasPane.getChildren().add(background);
            if (!previewMode) {
                addDetectedTargets();
                for (TemplateElement e : template.getElements()) if (e.getPageIndex() == pageIndex && PdfStyleResolver.effectivelyVisible(template,e)) canvasPane.getChildren().add(elementNode(e));
            }
            refreshLayers();
            updatePageWarning();
        }));
    }

    private void addDetectedTargets() {
        // Large vector/table regions can overlap text. Put them behind image/text targets so the
        // most specific object under the pointer wins without needing an edit mode.
        for (PdfImageExtractionService.VectorRegion region : vectorCache.getOrDefault(pageIndex,List.of())) {
            if (hasReplacementFor(region.sourceKey())) continue;
            canvasPane.getChildren().add(sourceVectorNode(region));
        }
        for (PdfImageRegion region : imageCache.getOrDefault(pageIndex,List.of())) {
            if (hasReplacementFor(sourceKey(region))) continue;
            canvasPane.getChildren().add(sourceImageNode(region));
        }
        for (PdfTextRegion region : textCache.getOrDefault(pageIndex,List.of())) {
            if (hasReplacementFor(sourceKey(region))) continue;
            canvasPane.getChildren().add(sourceTextNode(region));
        }
    }

    private Node sourceTextNode(PdfTextRegion region) {
        Region hit = new Region();
        hit.getStyleClass().add("pdf-v2-source-text-target");
        if (Objects.equals(selectedSourceText, region)) hit.getStyleClass().add("pdf-v2-source-selected");
        place(hit, region.x(), region.y(), region.width(), region.height());
        Tooltip.install(hit, new Tooltip("Detected PDF text • click to inspect • double-click to edit\n" + region.text()));
        hit.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                TemplateElement e = materializeSourceText(region);
                if (e != null) { selectOnly(e); Platform.runLater(() -> beginInlineEdit(e)); }
            } else selectSourceText(region);
            event.consume();
        });
        return hit;
    }

    private Node sourceImageNode(PdfImageRegion region) {
        Region hit = new Region();
        hit.getStyleClass().add("pdf-v2-source-image-target");
        if (Objects.equals(selectedSourceImage, region)) hit.getStyleClass().add("pdf-v2-source-selected");
        place(hit, region.x(), region.y(), region.width(), region.height());
        Tooltip.install(hit, new Tooltip("Detected PDF image • click to inspect • double-click to make editable"));
        hit.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                TemplateElement e = materializeSourceImage(region);
                if (e != null) selectOnly(e);
            } else selectSourceImage(region);
            event.consume();
        });
        return hit;
    }

    private Node sourceVectorNode(PdfImageExtractionService.VectorRegion region) {
        Region hit = new Region();
        hit.getStyleClass().add("pdf-v2-source-vector-target");
        if (Objects.equals(selectedSourceVector, region)) hit.getStyleClass().add("pdf-v2-source-selected");
        place(hit, region.x(), region.y(), region.width(), region.height());
        Tooltip.install(hit, new Tooltip("Detected PDF " + region.kind().toLowerCase(Locale.ROOT) + " • click to inspect • double-click to make editable"));
        hit.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                TemplateElement e = materializeSourceVector(region);
                if (e != null) selectOnly(e);
            } else selectSourceVector(region);
            event.consume();
        });
        return hit;
    }

    private Node elementNode(TemplateElement e) {
        Node visual = elementVisual(e);
        StackPane wrapper = new StackPane(visual);
        wrapper.getProperties().put("templateElementId", e.getId());
        wrapper.getStyleClass().addAll("pdf-v2-object", "pdf-v2-object-" + e.getType().name().toLowerCase(Locale.ROOT));
        if (selectedIds.contains(e.getId())) wrapper.getStyleClass().add("pdf-v2-object-selected");
        if (e.isLocked()) wrapper.getStyleClass().add("pdf-v2-object-locked");
        if (e.getX() < 0 || e.getY() < 0 || e.getX()+e.getWidth() > pageWidth || e.getY()+e.getHeight() > pageHeight)
            wrapper.getStyleClass().add("pdf-v2-object-outside");
        place(wrapper, e.getX(), e.getY(), e.getWidth(), e.getHeight());
        wrapper.setRotate(e.getRotation());
        wrapper.setOpacity(PdfStyleResolver.effective(template, e).getOpacity());

        if (selectedIds.contains(e.getId()) && !e.isLocked()) addResizeHandles(wrapper, e);
        wrapper.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            if (event.isShiftDown()) toggleSelection(e); else if (!selectedIds.contains(e.getId())) selectOnly(e);
            if (!e.isLocked() && event.getClickCount() < 2) startDrag(event);
            event.consume();
        });
        wrapper.setOnMouseDragged(event -> { if (dragging) dragSelection(event); event.consume(); });
        wrapper.setOnMouseReleased(event -> { if (dragging) { dragging=false; autosave(); renderCanvas(); } event.consume(); });
        wrapper.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2 && isTextLike(e) && !e.isLocked()) beginInlineEdit(e);
            event.consume();
        });
        return wrapper;
    }

    private Node elementVisual(TemplateElement e) {
        TemplateElement style = PdfStyleResolver.effective(template, e);
        if (e.getType() == ElementType.LINE) {
            Line line = new Line(0, 0, Math.max(1,e.getWidth()*scale), Math.max(0,e.getHeight()*scale));
            line.setStroke(style.isStrokeEnabled() ? Color.web(style.getStrokeColor()) : Color.TRANSPARENT);
            line.setStrokeWidth(Math.max(.5,style.getStrokeWidth()*scale));
            return line;
        }
        if (e.getType() == ElementType.PATH) {
            javafx.scene.shape.Path path = new javafx.scene.shape.Path();
            for (PathCommand command : e.getPathCommands()) {
                switch (command.getType()) {
                    case "M" -> path.getElements().add(new javafx.scene.shape.MoveTo(command.getX1()*e.getWidth()*scale, command.getY1()*e.getHeight()*scale));
                    case "L" -> path.getElements().add(new javafx.scene.shape.LineTo(command.getX1()*e.getWidth()*scale, command.getY1()*e.getHeight()*scale));
                    case "C" -> path.getElements().add(new javafx.scene.shape.CubicCurveTo(command.getX1()*e.getWidth()*scale,command.getY1()*e.getHeight()*scale,command.getX2()*e.getWidth()*scale,command.getY2()*e.getHeight()*scale,command.getX3()*e.getWidth()*scale,command.getY3()*e.getHeight()*scale));
                    case "Z" -> path.getElements().add(new javafx.scene.shape.ClosePath());
                }
            }
            path.setFill(style.isFillEnabled() && e.isPathFilled() ? Color.web(style.getFillColor()) : Color.TRANSPARENT);
            path.setStroke(style.isStrokeEnabled() && e.isPathStroked() ? Color.web(style.getStrokeColor()) : Color.TRANSPARENT);
            path.setStrokeWidth(Math.max(.5,style.getStrokeWidth()*scale));
            return path;
        }
        if ((e.getType()==ElementType.IMAGE || e.getType()==ElementType.IMAGE_FIELD) && imageFor(e) != null) {
            ImageView view = new ImageView(imageFor(e));
            view.setPreserveRatio(e.isPreserveAspectRatio() && !"STRETCH".equals(e.getImageFit()));
            double innerW = Math.max(1,(e.getWidth()-style.getPaddingLeft()-style.getPaddingRight())*scale);
            double innerH = Math.max(1,(e.getHeight()-style.getPaddingTop()-style.getPaddingBottom())*scale);
            view.setFitWidth(innerW); view.setFitHeight(innerH);
            StackPane box = new StackPane(view);
            box.setStyle(styleFor(e));
            box.setPadding(new javafx.geometry.Insets(style.getPaddingTop()*scale,style.getPaddingRight()*scale,style.getPaddingBottom()*scale,style.getPaddingLeft()*scale));
            return box;
        }
        Label label = new Label(displayText(e));
        label.setWrapText(true);
        label.setAlignment(switch (style.getTextAlignment()) { case "CENTER" -> Pos.CENTER; case "RIGHT" -> Pos.CENTER_RIGHT; default -> Pos.CENTER_LEFT; });
        label.setStyle(styleFor(e));
        label.setPadding(new javafx.geometry.Insets(style.getPaddingTop()*scale,style.getPaddingRight()*scale,style.getPaddingBottom()*scale,style.getPaddingLeft()*scale));
        return label;
    }

    private Image imageFor(TemplateElement e) {
        try {
            Path p = e.getType()==ElementType.IMAGE_FIELD
                    ? (dataPreviewMode && currentPreviewData != null ? currentPreviewData.image(e.getFieldKey()) : null)
                    : TemplateStorageService.resolveAsset(template, e.getImagePath());
            if (p != null && Files.isRegularFile(p)) return new Image(p.toUri().toString());
        } catch (Exception ignored) { }
        return null;
    }

    private String displayText(TemplateElement e) {
        if (e.getType()==ElementType.ITEM_TABLE) return "ITEM REPEATER\n" + e.getTableColumns().stream().map(String::toUpperCase).collect(Collectors.joining("  |  "));
        if (e.getType()==ElementType.CHARGE_TABLE) return "CHARGE REPEATER\n" + e.getTableColumns().stream().map(String::toUpperCase).collect(Collectors.joining("  |  "));
        if (e.getType()==ElementType.RECTANGLE) return "";
        if (e.getType()==ElementType.BLOCK) return selectedIds.contains(e.getId()) ? "Section / Group" : "";
        if (e.getType()==ElementType.WHITEOUT) return "";
        String text = e.getText();
        if (dataPreviewMode && currentPreviewData != null) text = resolveExpression(text, currentPreviewData);
        return text;
    }

    private String styleFor(TemplateElement e) {
        TemplateElement style = PdfStyleResolver.effective(template, e);
        String family = switch (style.getFontFamily()) { case "TIMES" -> "Times New Roman"; case "COURIER" -> "Courier New"; default -> "Arial"; };
        return "-fx-font-family:'"+family+"';-fx-font-size:"+(style.getFontSize()*scale)+"px;"
                + "-fx-font-weight:"+(style.isBold()?"bold":"normal")+";-fx-font-style:"+(style.isItalic()?"italic":"normal")+";"
                + "-fx-text-fill:"+style.getTextColor()+";"
                + "-fx-background-color:"+(style.isFillEnabled()?style.getFillColor():"transparent")+";"
                + "-fx-border-color:"+(style.isStrokeEnabled()?style.getStrokeColor():"transparent")+";"
                + "-fx-border-width:"+(style.getStrokeWidth()*scale)+";"
                + "-fx-background-radius:"+(style.getBorderRadius()*scale)+";-fx-border-radius:"+(style.getBorderRadius()*scale)+";";
    }

    private void addResizeHandles(StackPane wrapper, TemplateElement e) {
        for (Handle h : Handle.values()) {
            Region handle = new Region();
            handle.getStyleClass().add("pdf-v2-resize-handle");
            handle.setPrefSize(9,9); handle.setMinSize(9,9); handle.setMaxSize(9,9);
            StackPane.setAlignment(handle, alignmentFor(h));
            handle.setCursor(cursorFor(h));
            handle.setOnMousePressed(event -> { checkpoint(); handle.getProperties().put("sx",event.getSceneX()); handle.getProperties().put("sy",event.getSceneY()); handle.getProperties().put("x",e.getX()); handle.getProperties().put("y",e.getY()); handle.getProperties().put("w",e.getWidth()); handle.getProperties().put("h",e.getHeight()); event.consume(); });
            handle.setOnMouseDragged(event -> { resizeElementFromHandle(e,h,event,handle); event.consume(); });
            handle.setOnMouseReleased(event -> { autosave(); renderCanvas(); event.consume(); });
            wrapper.getChildren().add(handle);
        }
    }

    private Pos alignmentFor(Handle h) { return switch (h) { case NW->Pos.TOP_LEFT; case N->Pos.TOP_CENTER; case NE->Pos.TOP_RIGHT; case E->Pos.CENTER_RIGHT; case SE->Pos.BOTTOM_RIGHT; case S->Pos.BOTTOM_CENTER; case SW->Pos.BOTTOM_LEFT; case W->Pos.CENTER_LEFT; }; }
    private Cursor cursorFor(Handle h) { return switch (h) { case NW,SE->Cursor.NW_RESIZE; case NE,SW->Cursor.NE_RESIZE; case N,S->Cursor.V_RESIZE; case E,W->Cursor.H_RESIZE; }; }

    private void resizeElementFromHandle(TemplateElement e, Handle h, MouseEvent event, Region handle) {
        double sx=(double)handle.getProperties().get("sx"), sy=(double)handle.getProperties().get("sy");
        double ox=(double)handle.getProperties().get("x"), oy=(double)handle.getProperties().get("y"), ow=(double)handle.getProperties().get("w"), oh=(double)handle.getProperties().get("h");
        double dx=(event.getSceneX()-sx)/scale, dy=(event.getSceneY()-sy)/scale;
        double x=ox,y=oy,w=ow,hh=oh;
        if (Set.of(Handle.W,Handle.NW,Handle.SW).contains(h)) { x=ox+dx; w=ow-dx; }
        if (Set.of(Handle.E,Handle.NE,Handle.SE).contains(h)) w=ow+dx;
        if (Set.of(Handle.N,Handle.NW,Handle.NE).contains(h)) { y=oy+dy; hh=oh-dy; }
        if (Set.of(Handle.S,Handle.SW,Handle.SE).contains(h)) hh=oh+dy;
        if (w<1) { x-=1-w; w=1; } if (hh<1) { y-=1-hh; hh=1; }
        if (chkSnap.isSelected()) { x=snap(x);y=snap(y);w=Math.max(1,snap(w));hh=Math.max(1,snap(hh)); }
        e.setX(x); e.setY(y); e.setWidth(w); e.setHeight(hh);
        populateInspector(e); renderCanvasFast();
    }

    private void renderCanvasFast() {
        // Geometry feedback remains responsive without re-rendering the background PDF image.
        for (Node node : new ArrayList<>(canvasPane.getChildren())) {
            Object id = node.getProperties().get("templateElementId");
            if (!(id instanceof String sid)) continue;
            TemplateElement e = findById(sid);
            if (e == null) continue;
            place((Region)node,e.getX(),e.getY(),e.getWidth(),e.getHeight());
            node.setRotate(e.getRotation()); node.setOpacity(PdfStyleResolver.effective(template, e).getOpacity());
        }
        updatePageWarning();
    }

    private void place(Region node, double x, double y, double width, double height) {
        node.setLayoutX(x*scale); node.setLayoutY(y*scale);
        node.setPrefSize(Math.max(1,width*scale),Math.max(1,height*scale));
        node.setMinSize(Math.max(1,width*scale),Math.max(1,height*scale));
        node.setMaxSize(Math.max(1,width*scale),Math.max(1,height*scale));
    }

    // ---------------------------------------------------------------------
    // Selection / inspector
    // ---------------------------------------------------------------------

    private void selectOnly(TemplateElement e) {
        selectedIds.clear(); selectedIds.add(e.getId()); clearSourceSelection();
        populateInspector(e); refreshLayers(); renderCanvas();
    }

    private void toggleSelection(TemplateElement e) {
        clearSourceSelection();
        if (!selectedIds.remove(e.getId())) selectedIds.add(e.getId());
        refreshSelectionInspector(); renderCanvas();
    }

    private void selectSourceText(PdfTextRegion region) {
        selectedIds.clear(); selectedSourceText=region; selectedSourceImage=null; selectedSourceVector=null;
        populateInspector(region); renderCanvas();
    }
    private void selectSourceImage(PdfImageRegion region) {
        selectedIds.clear(); selectedSourceText=null; selectedSourceImage=region; selectedSourceVector=null;
        populateInspector(region); renderCanvas();
    }
    private void selectSourceVector(PdfImageExtractionService.VectorRegion region) {
        selectedIds.clear(); selectedSourceText=null; selectedSourceImage=null; selectedSourceVector=region;
        populateInspector(region); renderCanvas();
    }

    private void clearSelection() {
        selectedIds.clear(); clearSourceSelection(); clearInspector(); refreshLayers(); renderCanvas();
    }
    private void clearSourceSelection() { selectedSourceText=null; selectedSourceImage=null; selectedSourceVector=null; }

    private void refreshSelectionInspector() {
        if (selectedIds.size()==1) populateInspector(selectedElement());
        else if (selectedIds.size()>1) populateMixedInspector(selectedIds.size());
        else clearInspector();
    }

    private TemplateElement selectedElement() { return selectedIds.size()==1 ? findById(selectedIds.iterator().next()) : null; }
    private List<TemplateElement> selectedElements() { return selectedIds.stream().map(this::findById).filter(Objects::nonNull).toList(); }
    private List<TemplateElement> selectedElementsWithDescendants() { Set<String> ids=idsWithDescendants(selectedIds);return template.getElements().stream().filter(e->ids.contains(e.getId())).toList(); }
    private Set<String> idsWithDescendants(Collection<String> roots) { LinkedHashSet<String> ids=new LinkedHashSet<>(roots==null?List.of():roots);boolean changed;do{changed=false;for(TemplateElement e:template.getElements())if(!e.getParentId().isBlank()&&ids.contains(e.getParentId())&&ids.add(e.getId()))changed=true;}while(changed);return ids; }
    private TemplateElement findById(String id) { return template==null?null:template.getElements().stream().filter(e->Objects.equals(e.getId(),id)).findFirst().orElse(null); }

    private void populateInspector(TemplateElement e) {
        if (e == null) { clearInspector(); return; }
        TemplateElement style = PdfStyleResolver.effective(template, e);
        inspectorSync=true;
        try {
            lblInspectorType.setText(displayName(e));
            String inheritance = e.getParentId().isBlank() ? "Editable Studio object"
                    : "Child of block " + e.getParentId().substring(0,Math.min(8,e.getParentId().length()))
                    + (e.isInheritParentStyle() ? " • inheriting style" + (e.getStyleOverrides().isEmpty() ? "" : " • " + e.getStyleOverrides().size() + " override(s)") : "");
            lblInspectorHint.setText(inheritance);
            txtContent.setText(e.getText());
            selectBinding(e.getFieldKey());
            cmbFontFamily.setValue(style.getFontFamily()); cmbTextFit.setValue(style.getTextFit()); cmbTextAlignment.setValue(style.getTextAlignment());
            txtFontSize.setText(fmt(style.getFontSize())); txtLineSpacing.setText(fmt(style.getLineSpacing())); chkBold.setSelected(style.isBold()); chkItalic.setSelected(style.isItalic()); chkInheritParent.setSelected(e.isInheritParentStyle());
            colorText.setValue(color(style.getTextColor(),Color.web("#172033"))); colorFill.setValue(color(style.getFillColor(),Color.WHITE)); colorStroke.setValue(color(style.getStrokeColor(),Color.web("#94A3B8")));
            txtX.setText(fmt(e.getX())); txtY.setText(fmt(e.getY())); txtWidth.setText(fmt(e.getWidth())); txtHeight.setText(fmt(e.getHeight())); txtRotation.setText(fmt(e.getRotation())); txtOpacity.setText(fmt(style.getOpacity()*100));
            txtStrokeWidth.setText(fmt(style.getStrokeWidth())); txtRadius.setText(fmt(style.getBorderRadius()));
            txtPadTop.setText(fmt(style.getPaddingTop())); txtPadRight.setText(fmt(style.getPaddingRight())); txtPadBottom.setText(fmt(style.getPaddingBottom())); txtPadLeft.setText(fmt(style.getPaddingLeft()));
            chkFillEnabled.setSelected(style.isFillEnabled()); chkStrokeEnabled.setSelected(style.isStrokeEnabled());
            cmbImageFit.setValue(e.getImageFit()); chkPreserveRatio.setSelected(e.isPreserveAspectRatio());
            txtTableColumns.setText(String.join(",",e.getTableColumns())); txtRowHeight.setText(fmt(e.getRowHeight())); txtHeaderHeight.setText(fmt(e.getHeaderHeight())); chkUseSourceTableDesign.setSelected(e.isUseSourceTableDesign());
            chkLocked.setSelected(e.isLocked()); chkVisible.setSelected(e.isVisible()); cmbPageRule.setValue(e.getPageRule());
            boolean image=isImageLike(e), repeater=isRepeater(e), text=isTextLike(e) || e.getType()==ElementType.BLOCK;
            textSection.setVisible(text); textSection.setManaged(text);
            imageSection.setVisible(image); imageSection.setManaged(image);
            repeaterSection.setVisible(repeater); repeaterSection.setManaged(repeater);
            lblSelection.setText(displayName(e));
        } finally { inspectorSync=false; }
    }

    private void populateInspector(PdfTextRegion r) {
        clearInspectorFieldsOnly(); inspectorSync=true;
        try {
            lblInspectorType.setText("Detected PDF Text"); lblInspectorHint.setText("Click Apply or change any property to convert this source text into an editable object automatically.");
            txtContent.setText(r.text()); txtX.setText(fmt(r.x())); txtY.setText(fmt(r.y())); txtWidth.setText(fmt(r.width())); txtHeight.setText(fmt(r.height()));
            txtFontSize.setText(fmt(r.fontSize())); cmbFontFamily.setValue(fontHint(r.fontName())); chkBold.setSelected(r.bold()); chkItalic.setSelected(r.italic()); colorText.setValue(color(r.textColor(),Color.web("#172033"))); txtRotation.setText(fmt(r.rotation())); txtOpacity.setText("100");
            txtLineSpacing.setText("1.22"); txtStrokeWidth.setText("0"); txtRadius.setText("0"); txtPadTop.setText("0"); txtPadRight.setText("0"); txtPadBottom.setText("0"); txtPadLeft.setText("0");
            chkFillEnabled.setSelected(false); chkStrokeEnabled.setSelected(false); chkInheritParent.setSelected(false); chkLocked.setSelected(false); chkVisible.setSelected(true); cmbPageRule.setValue("AUTO");
            textSection.setVisible(true);textSection.setManaged(true);imageSection.setVisible(false);imageSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
            lblSelection.setText("Detected text: " + abbreviate(r.text(),42));
        } finally { inspectorSync=false; }
    }

    private void populateInspector(PdfImageRegion r) {
        clearInspectorFieldsOnly(); inspectorSync=true;
        try {
            lblInspectorType.setText("Detected PDF Image"); lblInspectorHint.setText("Change any property or choose Replace Image to convert this source image into an editable Studio image.");
            txtX.setText(fmt(r.x()));txtY.setText(fmt(r.y()));txtWidth.setText(fmt(r.width()));txtHeight.setText(fmt(r.height()));txtRotation.setText("0");txtOpacity.setText("100");
            txtStrokeWidth.setText("0");txtRadius.setText("0");txtPadTop.setText("0");txtPadRight.setText("0");txtPadBottom.setText("0");txtPadLeft.setText("0");
            chkFillEnabled.setSelected(false);chkStrokeEnabled.setSelected(false);chkInheritParent.setSelected(false);chkLocked.setSelected(false);chkVisible.setSelected(true);cmbImageFit.setValue("FIT");chkPreserveRatio.setSelected(true);cmbPageRule.setValue("AUTO");
            imageSection.setVisible(true);imageSection.setManaged(true);textSection.setVisible(false);textSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
            lblSelection.setText("Detected image");
        } finally { inspectorSync=false; }
    }

    private void populateInspector(PdfImageExtractionService.VectorRegion r) {
        clearInspectorFieldsOnly(); inspectorSync=true;
        try {
            lblInspectorType.setText("Detected " + r.kind()); lblInspectorHint.setText("Change any property to convert this imported PDF object into editable vector/block geometry.");
            txtX.setText(fmt(r.x()));txtY.setText(fmt(r.y()));txtWidth.setText(fmt(r.width()));txtHeight.setText(fmt(r.height()));txtRotation.setText("0");txtOpacity.setText("100");
            var primitive = r.primitives().isEmpty() ? null : r.primitives().getFirst();
            if (primitive != null) { colorFill.setValue(color(primitive.fillColor(),Color.WHITE)); colorStroke.setValue(color(primitive.strokeColor(),Color.web("#94A3B8"))); txtStrokeWidth.setText(fmt(primitive.strokeWidth())); chkFillEnabled.setSelected(primitive.filled()); chkStrokeEnabled.setSelected(primitive.stroked()); }
            else { txtStrokeWidth.setText("1"); chkFillEnabled.setSelected(false); chkStrokeEnabled.setSelected(true); }
            txtRadius.setText("0");txtPadTop.setText("0");txtPadRight.setText("0");txtPadBottom.setText("0");txtPadLeft.setText("0");chkInheritParent.setSelected(false);chkLocked.setSelected(false);chkVisible.setSelected(true);cmbPageRule.setValue("AUTO");
            textSection.setVisible(false);textSection.setManaged(false);imageSection.setVisible(false);imageSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
            lblSelection.setText(r.kind());
        } finally { inspectorSync=false; }
    }

    private void populateMixedInspector(int count) {
        clearInspectorFieldsOnly(); inspectorSync=true;
        try {
            lblInspectorType.setText(count + " objects selected"); lblInspectorHint.setText("Alignment, distribution, duplicate, delete, layer order and format actions apply to the whole selection. Blank inspector values mean Mixed.");
            lblSelection.setText(count + " objects selected");
        } finally { inspectorSync=false; }
    }

    private void clearInspector() { clearInspectorFieldsOnly(); lblInspectorType.setText("Select any text, image, block or table"); lblInspectorHint.setText("Properties appear automatically."); lblSelection.setText("Nothing selected"); }
    private void clearInspectorFieldsOnly() {
        inspectorSync=true;
        try {
            for (TextField f : List.of(txtFontSize,txtLineSpacing,txtX,txtY,txtWidth,txtHeight,txtRotation,txtOpacity,txtStrokeWidth,txtRadius,txtPadTop,txtPadRight,txtPadBottom,txtPadLeft,txtTableColumns,txtRowHeight,txtHeaderHeight)) f.clear();
            txtContent.clear(); chkBold.setSelected(false);chkItalic.setSelected(false);chkInheritParent.setSelected(false);chkFillEnabled.setSelected(false);chkStrokeEnabled.setSelected(false);chkLocked.setSelected(false);chkVisible.setSelected(true);
            chkPaddingLinked.setSelected(true);chkPreserveRatio.setSelected(true);chkUseSourceTableDesign.setSelected(false);
            colorText.setValue(Color.web("#172033"));colorFill.setValue(Color.WHITE);colorStroke.setValue(Color.web("#94A3B8"));
            cmbFontFamily.setValue("HELVETICA");cmbTextFit.setValue("SHRINK");cmbTextAlignment.setValue("LEFT");cmbImageFit.setValue("FIT");cmbPageRule.setValue("AUTO");
            textSection.setVisible(false);textSection.setManaged(false);imageSection.setVisible(false);imageSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
        } finally { inspectorSync=false; }
    }

    private void selectBinding(String key) {
        if (key==null || key.isBlank()) { cmbFieldBinding.getSelectionModel().selectFirst(); return; }
        cmbFieldBinding.getItems().stream().filter(v->v.startsWith(key+"  •")).findFirst().ifPresentOrElse(cmbFieldBinding::setValue,()->cmbFieldBinding.getSelectionModel().selectFirst());
    }

    private void bindingChanged() {
        if (inspectorSync || previewMode) return;
        TemplateElement e = editableSelectionFromSource();
        if (e == null || !isTextLike(e)) return;
        String item = cmbFieldBinding.getValue();
        String key = bindingKey(item);
        checkpoint();
        e.setFieldKey(key);
        if (!key.isBlank()) {
            if (e.getText().isBlank() || !e.getText().contains("{{")) e.setText("{{"+key+"}}");
        }
        autosave(); populateInspector(e); renderCanvas();
    }

    @FXML private void showErpFields() {
        if (leftTabs == null) return;
        leftTabs.getSelectionModel().select(1);
        if (txtFieldSearch != null) Platform.runLater(txtFieldSearch::requestFocus);
    }

    @FXML private void applyInspector() { applyInspectorInternal(true); }
    private void applyInspectorSilently() { applyInspectorInternal(false); }
    private void applyInspectorInternal(boolean showError) {
        if (inspectorSync || previewMode) return;
        TemplateElement e = editableSelectionFromSource();
        if (e == null) return;
        try {
            checkpoint();
            e.setText(txtContent.getText());
            e.setFieldKey(bindingKey(cmbFieldBinding.getValue()));
            e.setFontFamily(cmbFontFamily.getValue()); e.setTextFit(cmbTextFit.getValue()); e.setTextAlignment(cmbTextAlignment.getValue());
            e.setFontSize(parse(txtFontSize,e.getFontSize())); e.setLineSpacing(parse(txtLineSpacing,e.getLineSpacing())); e.setBold(chkBold.isSelected()); e.setItalic(chkItalic.isSelected());
            e.setTextColor(hex(colorText.getValue())); e.setFillColor(hex(colorFill.getValue())); e.setStrokeColor(hex(colorStroke.getValue()));
            e.setX(parse(txtX,e.getX())); e.setY(parse(txtY,e.getY())); e.setWidth(parse(txtWidth,e.getWidth())); e.setHeight(parse(txtHeight,e.getHeight())); e.setRotation(parse(txtRotation,e.getRotation())); e.setOpacity(parse(txtOpacity,e.getOpacity()*100)/100.0);
            e.setStrokeWidth(parse(txtStrokeWidth,e.getStrokeWidth())); e.setBorderRadius(parse(txtRadius,e.getBorderRadius()));
            double top=parse(txtPadTop,e.getPaddingTop());
            if (chkPaddingLinked.isSelected()) { e.setPaddingTop(top);e.setPaddingRight(top);e.setPaddingBottom(top);e.setPaddingLeft(top); }
            else { e.setPaddingTop(top);e.setPaddingRight(parse(txtPadRight,e.getPaddingRight()));e.setPaddingBottom(parse(txtPadBottom,e.getPaddingBottom()));e.setPaddingLeft(parse(txtPadLeft,e.getPaddingLeft())); }
            e.setFillEnabled(chkFillEnabled.isSelected());e.setStrokeEnabled(chkStrokeEnabled.isSelected());
            e.setImageFit(cmbImageFit.getValue());e.setPreserveAspectRatio(chkPreserveRatio.isSelected());
            if (isRepeater(e)) {
                e.setTableColumns(Arrays.stream(txtTableColumns.getText().split(",")).map(String::trim).filter(s->!s.isBlank()).toList());
                e.setRowHeight(parse(txtRowHeight,e.getRowHeight()));e.setHeaderHeight(parse(txtHeaderHeight,e.getHeaderHeight()));e.setUseSourceTableDesign(chkUseSourceTableDesign.isSelected());
            }
            e.setLocked(chkLocked.isSelected());e.setVisible(chkVisible.isSelected());e.setPageRule(cmbPageRule.getValue());
            if (e.isInheritParentStyle()) PdfStyleResolver.updateOverrides(template, e);
            autosave(); populateInspector(e); renderCanvas();
        } catch (Exception error) {
            if (showError) ModernDialog.error(root,"Properties could not be applied","PDF Studio",rootMessage(error));
        }
    }

    private TemplateElement editableSelectionFromSource() {
        TemplateElement e=selectedElement(); if (e!=null) return e;
        if (selectedSourceText!=null) { e=materializeSourceText(selectedSourceText); if(e!=null)selectOnlyWithoutRender(e); return e; }
        if (selectedSourceImage!=null) { e=materializeSourceImage(selectedSourceImage); if(e!=null)selectOnlyWithoutRender(e); return e; }
        if (selectedSourceVector!=null) { e=materializeSourceVector(selectedSourceVector); if(e!=null)selectOnlyWithoutRender(e); return e; }
        return null;
    }

    private void selectOnlyWithoutRender(TemplateElement e) { selectedIds.clear(); selectedIds.add(e.getId()); clearSourceSelection(); }

    private void inheritanceChanged() {
        if (inspectorSync || previewMode) return;
        TemplateElement e = editableSelectionFromSource();
        if (e == null) return;
        if (e.getParentId().isBlank() || findById(e.getParentId()) == null) {
            inspectorSync = true;
            try { chkInheritParent.setSelected(false); } finally { inspectorSync = false; }
            return;
        }
        checkpoint();
        if (chkInheritParent.isSelected()) {
            e.setInheritParentStyle(true);
            e.clearStyleOverrides();
        } else {
            PdfStyleResolver.freezeEffectiveStyle(template, e);
        }
        autosave();
        populateInspector(e);
        renderCanvas();
    }

    // ---------------------------------------------------------------------
    // Source materialization
    // ---------------------------------------------------------------------

    private TemplateElement materializeSourceText(PdfTextRegion region) {
        if (region==null) return null;
        String key=sourceKey(region);
        TemplateElement existing = primaryReplacement(key);
        if (existing!=null) return existing;
        checkpoint();
        List<TemplateElement> list=new ArrayList<>(template.getElements());
        TemplateElement text=addSourceTextReplacement(list,region,region.text(),"");
        template.setElements(list);autosave();return text;
    }

    private TemplateElement addSourceTextReplacement(List<TemplateElement> list, PdfTextRegion region, String expression, String fieldKey) {
        String key=sourceKey(region);String group="replace-"+UUID.randomUUID();
        TemplateElement mask=sourceMask(region,key);mask.setReplacementGroupId(group);list.add(mask);
        TemplateElement text=TemplateElement.of(ElementType.TEXT,region.pageIndex(),region.x(),region.y(),region.width(),Math.max(region.height(),region.fontSize()*1.25));
        text.setText(expression);text.setFieldKey(fieldKey);text.setFontSize(region.fontSize());text.setFontFamily(fontHint(region.fontName()));text.setBold(region.bold());text.setItalic(region.italic());text.setTextColor(region.textColor());text.setRotation(region.rotation());text.setFillEnabled(false);text.setStrokeEnabled(false);text.setTextFit("SHRINK");text.setReplacementGroupId(group);text.setReplacementSourceKey(key);list.add(text);return text;
    }

    private TemplateElement sourceMask(PdfTextRegion region,String key){
        TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),region.x()-1,region.y()-1,region.width()+2,region.height()+2);
        mask.setFillColor(sampleBackgroundColor(region.pageIndex(), region.x(), region.y(), region.width(), region.height()));mask.setStrokeColor(mask.getFillColor());mask.setStrokeWidth(0);mask.setLocked(true);mask.setReplacementSourceKey(key);return mask;
    }

    private TemplateElement materializeSourceImage(PdfImageRegion region) {
        if(region==null)return null;String key=sourceKey(region);TemplateElement existing=primaryReplacement(key);if(existing!=null)return existing;
        try {
            checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());String group="replace-"+UUID.randomUUID();
            TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),region.x(),region.y(),region.width(),region.height());mask.setFillColor(sampleBackgroundColor(region.pageIndex(),region.x(),region.y(),region.width(),region.height()));mask.setStrokeColor(mask.getFillColor());mask.setLocked(true);mask.setReplacementGroupId(group);mask.setReplacementSourceKey(key);list.add(mask);
            TemplateElement image=TemplateElement.of(ElementType.IMAGE,region.pageIndex(),region.x(),region.y(),region.width(),region.height());image.setImagePath(TemplateStorageService.importAsset(template,region.extractedImage()));image.setReplacementGroupId(group);image.setReplacementSourceKey(key);image.setFillEnabled(false);image.setStrokeEnabled(false);list.add(image);
            template.setElements(list);autosave();return image;
        } catch(Exception error){ModernDialog.error(root,"Image could not be converted","PDF Studio",rootMessage(error));return null;}
    }

    private TemplateElement materializeSourceVector(PdfImageExtractionService.VectorRegion region) {
        if(region==null)return null;String key=region.sourceKey();TemplateElement existing=primaryReplacement(key);if(existing!=null)return existing;
        checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());String group="replace-"+UUID.randomUUID();
        TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),region.x()-1,region.y()-1,region.width()+2,region.height()+2);mask.setFillColor(sampleBackgroundColor(region.pageIndex(),region.x(),region.y(),region.width(),region.height()));mask.setStrokeColor(mask.getFillColor());mask.setLocked(true);mask.setReplacementGroupId(group);mask.setReplacementSourceKey(key);list.add(mask);
        TemplateElement primary=null;
        for (var p:region.primitives()) {
            ElementType type=switch(p.kind()){case "RECTANGLE"->ElementType.BLOCK;case "PATH"->ElementType.PATH;default->ElementType.LINE;};
            TemplateElement e=TemplateElement.of(type,region.pageIndex(),p.x(),p.y(),p.width(),p.height());e.setFillColor(p.fillColor());e.setStrokeColor(p.strokeColor());e.setStrokeWidth(p.strokeWidth());e.setFillEnabled(p.filled());e.setStrokeEnabled(p.stroked());e.setPathCommands(p.pathCommands());e.setPathFilled(p.filled());e.setPathStroked(p.stroked());e.setReplacementGroupId(group);e.setReplacementSourceKey(key);list.add(e);if(primary==null)primary=e;
        }
        if(primary==null){primary=TemplateElement.of(ElementType.BLOCK,region.pageIndex(),region.x(),region.y(),region.width(),region.height());primary.setFillEnabled(false);primary.setReplacementGroupId(group);primary.setReplacementSourceKey(key);list.add(primary);}
        template.setElements(list);autosave();return primary;
    }

    private TemplateElement primaryReplacement(String sourceKey){return template.getElements().stream().filter(e->sourceKey.equals(e.getReplacementSourceKey())&&e.getType()!=ElementType.WHITEOUT).findFirst().orElse(null);}
    private boolean hasReplacementFor(String sourceKey){return template.getElements().stream().anyMatch(e->sourceKey.equals(e.getReplacementSourceKey()));}
    private String sourceKey(PdfTextRegion r){return "PDF_TEXT|"+r.pageIndex()+"|"+round(r.x())+"|"+round(r.y())+"|"+round(r.width())+"|"+round(r.height())+"|"+PdfAutoMappingService.normalize(r.text());}
    private String sourceKey(PdfImageRegion r){return "PDF_IMAGE|"+r.pageIndex()+"|"+round(r.x())+"|"+round(r.y())+"|"+round(r.width())+"|"+round(r.height());}
    private String round(double v){return String.valueOf(Math.round(v*10.0)/10.0);}

    // ---------------------------------------------------------------------
    // Create / drag palette
    // ---------------------------------------------------------------------

    @FXML private void addText(){addElement(newElement(ElementType.TEXT,180,36),"Text");}
    @FXML private void addHeading(){TemplateElement e=newElement(ElementType.TEXT,240,42);e.setText("Heading");e.setBold(true);e.setFontSize(18);addElement(e,null);}
    @FXML private void addBlock(){TemplateElement e=newElement(ElementType.BLOCK,220,100);e.setText("");e.setFillEnabled(false);e.setStrokeEnabled(true);e.setBorderRadius(6);addElement(e,null);}
    @FXML private void addRectangle(){TemplateElement e=newElement(ElementType.RECTANGLE,180,80);e.setText("");e.setFillEnabled(true);e.setStrokeEnabled(true);addElement(e,null);}
    @FXML private void addHideArea(){TemplateElement e=newElement(ElementType.WHITEOUT,180,80);e.setText("");e.setFillColor("#FFFFFF");e.setStrokeColor("#FFFFFF");e.setFillEnabled(true);e.setStrokeEnabled(false);addElement(e,null);}
    @FXML private void addLine(){TemplateElement e=newElement(ElementType.LINE,180,1);e.setFillEnabled(false);e.setStrokeEnabled(true);addElement(e,null);}
    @FXML private void addImage(){chooseImageForNewObject();}
    @FXML private void addItemRepeater(){TemplateElement e=newElement(ElementType.ITEM_TABLE,Math.max(300,pageWidth-50),220);e.setTableColumns(List.of("serial","hsn","descriptionWithRemarks","quantity","rate","unit","taxable"));e.setUseSourceTableDesign(false);e.setFontSize(8);addElement(e,null);}
    @FXML private void addChargeRepeater(){TemplateElement e=newElement(ElementType.CHARGE_TABLE,Math.max(250,pageWidth*.48),120);e.setTableColumns(List.of("type","amount","gstPercent","taxAmount","total"));e.setUseSourceTableDesign(false);e.setFontSize(8);addElement(e,null);}

    @FXML private void dragCreateText(MouseEvent e){startCreateDrag(e,"TEXT");}
    @FXML private void dragCreateHeading(MouseEvent e){startCreateDrag(e,"HEADING");}
    @FXML private void dragCreateBlock(MouseEvent e){startCreateDrag(e,"BLOCK");}
    @FXML private void dragCreateRectangle(MouseEvent e){startCreateDrag(e,"RECTANGLE");}
    @FXML private void dragCreateHideArea(MouseEvent e){startCreateDrag(e,"HIDE_AREA");}
    @FXML private void dragCreateLine(MouseEvent e){startCreateDrag(e,"LINE");}
    @FXML private void dragCreateImage(MouseEvent e){startCreateDrag(e,"IMAGE");}
    @FXML private void dragCreateItems(MouseEvent e){startCreateDrag(e,"ITEMS");}
    @FXML private void dragCreateCharges(MouseEvent e){startCreateDrag(e,"CHARGES");}
    private void startCreateDrag(MouseEvent event,String type){if(previewMode)return;Node source=(Node)event.getSource();Dragboard board=source.startDragAndDrop(TransferMode.COPY);ClipboardContent c=new ClipboardContent();c.putString(CREATE_DRAG_PREFIX+type);board.setContent(c);event.consume();}

    private void createAt(String type,double x,double y){
        switch(type){
            case "TEXT"->{TemplateElement e=elementAt(ElementType.TEXT,x,y,180,36);e.setText("Text");addElement(e,null);}
            case "HEADING"->{TemplateElement e=elementAt(ElementType.TEXT,x,y,240,42);e.setText("Heading");e.setBold(true);e.setFontSize(18);addElement(e,null);}
            case "BLOCK"->{TemplateElement e=elementAt(ElementType.BLOCK,x,y,220,100);e.setFillEnabled(false);e.setBorderRadius(6);addElement(e,null);}
            case "RECTANGLE"->addElement(elementAt(ElementType.RECTANGLE,x,y,180,80),null);
            case "HIDE_AREA"->{TemplateElement e=elementAt(ElementType.WHITEOUT,x,y,180,80);e.setFillColor("#FFFFFF");e.setStrokeColor("#FFFFFF");e.setFillEnabled(true);e.setStrokeEnabled(false);addElement(e,null);}
            case "LINE"->addElement(elementAt(ElementType.LINE,x,y,180,1),null);
            case "IMAGE"->{TemplateElement e=elementAt(ElementType.IMAGE,x,y,180,100);e.setFillEnabled(false);addElement(e,null);selectOnly(e);replaceSelectedImage();}
            case "ITEMS"->{TemplateElement e=elementAt(ElementType.ITEM_TABLE,x,y,Math.max(300,pageWidth-x-15),220);e.setTableColumns(List.of("serial","hsn","descriptionWithRemarks","quantity","rate","unit","taxable"));addElement(e,null);}
            case "CHARGES"->{TemplateElement e=elementAt(ElementType.CHARGE_TABLE,x,y,Math.max(250,pageWidth-x-15),120);e.setTableColumns(List.of("type","amount","gstPercent","taxAmount","total"));addElement(e,null);}
        }
    }

    private TemplateElement newElement(ElementType type,double width,double height){return elementAt(type,Math.max(12,(pageWidth-width)/2),Math.max(12,(pageHeight-height)/2),width,height);}
    private TemplateElement elementAt(ElementType type,double x,double y,double width,double height){TemplateElement e=TemplateElement.of(type,pageIndex,x,y,width,height);e.setFillEnabled(type==ElementType.RECTANGLE||type==ElementType.WHITEOUT);e.setStrokeEnabled(type!=ElementType.TEXT&&type!=ElementType.IMAGE&&type!=ElementType.IMAGE_FIELD);if(type==ElementType.TEXT)e.setTextFit("WRAP");return e;}
    private void addElement(TemplateElement e,String text){if(previewMode||e==null)return;checkpoint();if(text!=null)e.setText(text);List<TemplateElement> list=new ArrayList<>(template.getElements());list.add(e);template.setElements(list);autosave();selectOnlyWithoutRender(e);populateInspector(e);renderCanvas();}

    private void dropField(TemplateFieldDefinition field,double x,double y){
        if (field == null) return;
        TemplateElement repeater = repeaterAt(x,y);
        if (repeater == null && selectedIds.size() == 1 && isRepeater(selectedElement())) repeater = selectedElement();
        if (repeater != null && addFieldToRepeater(repeater, field)) return;

        PdfTextRegion source=findSourceTextAt(x,y);
        if(source!=null){checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());String expr=source.text();if(!expr.contains("{{"))expr=expr+" {{"+field.key()+"}}";TemplateElement e=addSourceTextReplacement(list,source,expr,field.key());template.setElements(list);autosave();selectOnlyWithoutRender(e);populateInspector(e);renderCanvas();return;}
        TemplateElement e=elementAt(field.image()?ElementType.IMAGE_FIELD:ElementType.TEXT,x-60,y-12,field.image()?150:140,field.image()?80:28);e.setFieldKey(field.key());e.setText(field.image()?field.label():"{{"+field.key()+"}}");e.setFillEnabled(false);e.setStrokeEnabled(false);addElement(e,null);
    }

    private TemplateElement repeaterAt(double x,double y) {
        List<TemplateElement> elements = template.getElements();
        for (int i=elements.size()-1;i>=0;i--) {
            TemplateElement e=elements.get(i);
            if(e.getPageIndex()!=pageIndex || !isRepeater(e) || !e.isVisible()) continue;
            if(x>=e.getX()&&x<=e.getX()+e.getWidth()&&y>=e.getY()&&y<=e.getY()+e.getHeight()) return e;
        }
        return null;
    }

    private boolean addFieldToRepeater(TemplateElement repeater, TemplateFieldDefinition field) {
        String prefix = repeater.getType()==ElementType.ITEM_TABLE ? "item." : "charge.";
        if (!field.key().startsWith(prefix)) return false;
        String column = field.key().substring(prefix.length());
        List<String> columns = new ArrayList<>(repeater.getTableColumns());
        if (!columns.contains(column)) { checkpoint(); columns.add(column); repeater.setTableColumns(columns); autosave(); }
        selectOnlyWithoutRender(repeater); populateInspector(repeater); renderCanvas();
        lblSaveState.setText(columns.contains(column) ? "Repeater field mapped ✓" : "Ready");
        return true;
    }

    @FXML private void addSelectedField(){TemplateFieldDefinition f=lstFields.getSelectionModel().getSelectedItem();if(f!=null)dropField(f,pageWidth/2,pageHeight/2);}

    private PdfTextRegion findSourceTextAt(double x,double y){return textCache.getOrDefault(pageIndex,List.of()).stream().filter(r->x>=r.x()&&x<=r.x()+r.width()&&y>=r.y()&&y<=r.y()+r.height()).findFirst().orElse(null);}

    private void chooseImageForNewObject(){
        FileChooser chooser=imageChooser("Add Image");var file=chooser.showOpenDialog(root.getScene().getWindow());if(file==null)return;
        try{TemplateElement e=newElement(ElementType.IMAGE,180,100);e.setImagePath(TemplateStorageService.importAsset(template,file.toPath()));e.setFillEnabled(false);e.setStrokeEnabled(false);addElement(e,null);}catch(Exception error){ModernDialog.error(root,"Image could not be added","PDF Studio",rootMessage(error));}
    }

    @FXML private void replaceSelectedImage(){
        TemplateElement e=editableSelectionFromSource();if(e==null)return;
        if(e.getType()!=ElementType.IMAGE&&e.getType()!=ElementType.IMAGE_FIELD){ModernDialog.info(root,"Select an image","Replace Image","Click an imported image or Studio image first.");return;}
        FileChooser chooser=imageChooser("Replace Image");var file=chooser.showOpenDialog(root.getScene().getWindow());if(file==null)return;
        try{checkpoint();e.setType(ElementType.IMAGE);e.setFieldKey("");e.setImagePath(TemplateStorageService.importAsset(template,file.toPath()));autosave();populateInspector(e);renderCanvas();}catch(Exception error){ModernDialog.error(root,"Image could not be replaced","PDF Studio",rootMessage(error));}
    }
    private FileChooser imageChooser(String title){FileChooser c=new FileChooser();c.setTitle(title);c.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg"));return c;}

    // ---------------------------------------------------------------------
    // Direct manipulation / grouping / formatting
    // ---------------------------------------------------------------------

    private void startDrag(MouseEvent event){dragging=true;dragSceneX=event.getSceneX();dragSceneY=event.getSceneY();dragOrigins.clear();for(TemplateElement e:selectedElementsWithDescendants())dragOrigins.put(e.getId(),new double[]{e.getX(),e.getY()});checkpoint();}
    private void dragSelection(MouseEvent event){
        double dx=(event.getSceneX()-dragSceneX)/scale,dy=(event.getSceneY()-dragSceneY)/scale;
        for(Map.Entry<String,double[]> entry:dragOrigins.entrySet()){
            TemplateElement e=findById(entry.getKey());if(e==null||e.isLocked())continue;double[] o=entry.getValue();
            e.setX(chkSnap.isSelected()?snap(o[0]+dx):o[0]+dx);e.setY(chkSnap.isSelected()?snap(o[1]+dy):o[1]+dy);
        }
        if(chkSnap.isSelected())applySmartGuides();else clearSmartGuides();
        renderCanvasFast();refreshSelectionInspector();
    }

    private void applySmartGuides(){
        clearSmartGuides();
        List<TemplateElement> selected=selectedElementsWithDescendants().stream().filter(e->!e.isLocked()).toList();
        if(selected.isEmpty())return;
        TemplateElement primary=selected.getFirst();
        Set<String> movingIds=idsWithDescendants(selectedIds);
        List<TemplateElement> others=template.getElements().stream()
                .filter(e->e.getPageIndex()==pageIndex&&PdfStyleResolver.effectivelyVisible(template,e)&&!movingIds.contains(e.getId())).toList();
        double tolerance=4.0,bestDx=Double.NaN,bestDy=Double.NaN,guideX=Double.NaN,guideY=Double.NaN;
        double[] px={primary.getX(),primary.getX()+primary.getWidth()/2.0,primary.getX()+primary.getWidth()};
        double[] py={primary.getY(),primary.getY()+primary.getHeight()/2.0,primary.getY()+primary.getHeight()};
        for(TemplateElement other:others){
            double[] ox={other.getX(),other.getX()+other.getWidth()/2.0,other.getX()+other.getWidth()};
            double[] oy={other.getY(),other.getY()+other.getHeight()/2.0,other.getY()+other.getHeight()};
            for(double a:px)for(double b:ox){double d=b-a;if(Math.abs(d)<=tolerance&&(Double.isNaN(bestDx)||Math.abs(d)<Math.abs(bestDx))){bestDx=d;guideX=b;}}
            for(double a:py)for(double b:oy){double d=b-a;if(Math.abs(d)<=tolerance&&(Double.isNaN(bestDy)||Math.abs(d)<Math.abs(bestDy))){bestDy=d;guideY=b;}}
        }
        if(!Double.isNaN(bestDx))for(TemplateElement e:selected)e.setX(e.getX()+bestDx);
        if(!Double.isNaN(bestDy))for(TemplateElement e:selected)e.setY(e.getY()+bestDy);
        if(!Double.isNaN(guideX))addSmartGuide(guideX*scale,0,guideX*scale,pageHeight*scale);
        if(!Double.isNaN(guideY))addSmartGuide(0,guideY*scale,pageWidth*scale,guideY*scale);
    }

    private void addSmartGuide(double x1,double y1,double x2,double y2){
        Line line=new Line(x1,y1,x2,y2);line.getStyleClass().add("pdf-v2-smart-guide");line.setMouseTransparent(true);smartGuideLines.add(line);canvasPane.getChildren().add(line);
    }
    private void clearSmartGuides(){canvasPane.getChildren().removeAll(smartGuideLines);smartGuideLines.clear();}
    private double snap(double v){return Math.round(v/4.0)*4.0;}

    private void nudgeSelection(KeyCode code,double step){if(selectedIds.isEmpty()||previewMode)return;checkpoint();for(TemplateElement e:selectedElementsWithDescendants()){if(e.isLocked())continue;if(code==KeyCode.LEFT)e.setX(e.getX()-step);if(code==KeyCode.RIGHT)e.setX(e.getX()+step);if(code==KeyCode.UP)e.setY(e.getY()-step);if(code==KeyCode.DOWN)e.setY(e.getY()+step);}autosave();renderCanvas();}

    @FXML private void duplicateSelected(){
        if(selectedIds.isEmpty()||previewMode)return;
        Set<String> roots=new LinkedHashSet<>(selectedIds);Set<String> expanded=idsWithDescendants(roots);
        checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());
        List<TemplateElement> originals=list.stream().filter(e->expanded.contains(e.getId())).toList();
        Map<String,TemplateElement> copies=new LinkedHashMap<>();
        for(TemplateElement e:originals){TemplateElement c=e.copy();c.setX(e.getX()+10);c.setY(e.getY()+10);copies.put(e.getId(),c);}
        for(TemplateElement e:originals){TemplateElement c=copies.get(e.getId());if(copies.containsKey(e.getParentId()))c.setParentId(copies.get(e.getParentId()).getId());list.add(c);}
        template.setElements(list);selectedIds.clear();for(String root:roots){TemplateElement c=copies.get(root);if(c!=null)selectedIds.add(c.getId());}
        autosave();refreshSelectionInspector();renderCanvas();
    }
    private List<TemplateElement> selectedElementsSnapshotFrom(List<TemplateElement> list){Set<String> ids=new LinkedHashSet<>(selectedIds);return list.stream().filter(e->ids.contains(e.getId())).toList();}

    @FXML private void deleteSelected(){
        if(previewMode)return;
        if(selectedIds.isEmpty()){
            if(selectedSourceText!=null){hideSourceText(selectedSourceText);return;}
            if(selectedSourceImage!=null){hideSourceImage(selectedSourceImage);return;}
            if(selectedSourceVector!=null){hideSourceVector(selectedSourceVector);return;}
            return;
        }
        checkpoint();
        Set<String> ids=idsWithDescendants(selectedIds);
        // Deleting a replacement for imported content keeps its source mask so the original PDF remains untouched.
        Set<String> replacementGroups=template.getElements().stream().filter(e->ids.contains(e.getId())).map(TemplateElement::getReplacementGroupId).filter(v->v!=null&&!v.isBlank()).collect(Collectors.toSet());
        List<TemplateElement> list=template.getElements().stream().filter(e->!ids.contains(e.getId()) || (e.getType()==ElementType.WHITEOUT && replacementGroups.contains(e.getReplacementGroupId()))).collect(Collectors.toCollection(ArrayList::new));
        template.setElements(list);selectedIds.clear();autosave();clearInspector();renderCanvas();
    }

    private void hideSourceText(PdfTextRegion region){
        if(region==null)return; checkpoint();
        List<TemplateElement> list=new ArrayList<>(template.getElements());
        String key=sourceKey(region); TemplateElement mask=sourceMask(region,key); mask.setReplacementGroupId("hide-"+UUID.randomUUID()); list.add(mask);
        template.setElements(list); clearSourceSelection(); autosave(); clearInspector(); renderCanvas();
    }
    private void hideSourceImage(PdfImageRegion region){
        if(region==null)return; checkpoint(); List<TemplateElement> list=new ArrayList<>(template.getElements()); String key=sourceKey(region);
        TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),region.x(),region.y(),region.width(),region.height()); mask.setFillColor(sampleBackgroundColor(region.pageIndex(),region.x(),region.y(),region.width(),region.height())); mask.setStrokeColor(mask.getFillColor()); mask.setStrokeWidth(0); mask.setLocked(true); mask.setReplacementSourceKey(key); mask.setReplacementGroupId("hide-"+UUID.randomUUID()); list.add(mask);
        template.setElements(list); clearSourceSelection(); autosave(); clearInspector(); renderCanvas();
    }
    private void hideSourceVector(PdfImageExtractionService.VectorRegion region){
        if(region==null)return; checkpoint(); List<TemplateElement> list=new ArrayList<>(template.getElements());
        TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),region.x(),region.y(),region.width(),region.height()); mask.setFillColor(sampleBackgroundColor(region.pageIndex(),region.x(),region.y(),region.width(),region.height())); mask.setStrokeColor(mask.getFillColor()); mask.setStrokeWidth(0); mask.setLocked(true); mask.setReplacementSourceKey(region.sourceKey()); mask.setReplacementGroupId("hide-"+UUID.randomUUID()); list.add(mask);
        template.setElements(list); clearSourceSelection(); autosave(); clearInspector(); renderCanvas();
    }

    @FXML private void copyFormat(){TemplateElement e=selectedElement();if(e==null)return;formatClipboard=PdfStyleResolver.effective(template,e).snapshotCopy();lblSaveState.setText("Format copied ✓");}
    @FXML private void pasteFormat(){if(formatClipboard==null||selectedIds.isEmpty())return;checkpoint();for(TemplateElement target:selectedElements()){copyStyle(formatClipboard,target,false);if(target.isInheritParentStyle())PdfStyleResolver.updateOverrides(template,target);}autosave();refreshSelectionInspector();renderCanvas();}
    private void copyStyle(TemplateElement s,TemplateElement t,boolean includeBox){PdfStyleResolver.copyStyle(s,t);if(includeBox){t.setImageFit(s.getImageFit());t.setPreserveAspectRatio(s.isPreserveAspectRatio());}}

    @FXML private void groupSelected(){
        List<TemplateElement> selected=selectedElements();if(selected.size()<2||previewMode)return;checkpoint();double minX=selected.stream().mapToDouble(TemplateElement::getX).min().orElse(0),minY=selected.stream().mapToDouble(TemplateElement::getY).min().orElse(0),maxX=selected.stream().mapToDouble(e->e.getX()+e.getWidth()).max().orElse(minX+1),maxY=selected.stream().mapToDouble(e->e.getY()+e.getHeight()).max().orElse(minY+1);
        TemplateElement block=TemplateElement.of(ElementType.BLOCK,pageIndex,minX-6,minY-6,maxX-minX+12,maxY-minY+12);block.setFillEnabled(false);block.setStrokeEnabled(true);block.setStrokeColor("#94A3B8");block.setBorderRadius(6);
        List<TemplateElement> list=new ArrayList<>(template.getElements());int first=list.size();for(TemplateElement e:selected)first=Math.min(first,indexOfId(list,e.getId()));list.add(Math.max(0,first),block);for(TemplateElement e:selected){e.setParentId(block.getId());e.setInheritParentStyle(false);e.clearStyleOverrides();}template.setElements(list);selectedIds.clear();selectedIds.add(block.getId());autosave();populateInspector(block);renderCanvas();
    }
    @FXML private void ungroupSelected(){if(selectedIds.isEmpty())return;checkpoint();Set<String> parentIds=new HashSet<>();for(TemplateElement e:selectedElements())if(e.getType()==ElementType.BLOCK)parentIds.add(e.getId());for(TemplateElement e:template.getElements())if(parentIds.contains(e.getParentId())){if(e.isInheritParentStyle())PdfStyleResolver.freezeEffectiveStyle(template,e);e.setParentId("");e.setInheritParentStyle(false);e.clearStyleOverrides();}autosave();renderCanvas();}

    @FXML private void alignLeft(){alignSelected("LEFT");}@FXML private void alignCenter(){alignSelected("CENTER_H");}@FXML private void alignRight(){alignSelected("RIGHT");}@FXML private void alignTop(){alignSelected("TOP");}@FXML private void alignMiddle(){alignSelected("CENTER_V");}@FXML private void alignBottom(){alignSelected("BOTTOM");}
    private void alignSelected(String mode){List<TemplateElement> list=selectedElements();if(list.isEmpty())return;checkpoint();double minX=list.stream().mapToDouble(TemplateElement::getX).min().orElse(0),maxX=list.stream().mapToDouble(e->e.getX()+e.getWidth()).max().orElse(pageWidth),minY=list.stream().mapToDouble(TemplateElement::getY).min().orElse(0),maxY=list.stream().mapToDouble(e->e.getY()+e.getHeight()).max().orElse(pageHeight);for(TemplateElement e:list){switch(mode){case"LEFT"->e.setX(minX);case"RIGHT"->e.setX(maxX-e.getWidth());case"CENTER_H"->e.setX((minX+maxX-e.getWidth())/2);case"TOP"->e.setY(minY);case"BOTTOM"->e.setY(maxY-e.getHeight());case"CENTER_V"->e.setY((minY+maxY-e.getHeight())/2);}}autosave();renderCanvas();}
    @FXML private void distributeHorizontal(){distribute(true);}@FXML private void distributeVertical(){distribute(false);}
    private void distribute(boolean horizontal){List<TemplateElement> list=new ArrayList<>(selectedElements());if(list.size()<3)return;checkpoint();if(horizontal){list.sort(Comparator.comparingDouble(TemplateElement::getX));double start=list.getFirst().getX(),end=list.getLast().getX()+list.getLast().getWidth(),total=list.stream().mapToDouble(TemplateElement::getWidth).sum(),gap=(end-start-total)/(list.size()-1);double cursor=start;for(TemplateElement e:list){e.setX(cursor);cursor+=e.getWidth()+gap;}}else{list.sort(Comparator.comparingDouble(TemplateElement::getY));double start=list.getFirst().getY(),end=list.getLast().getY()+list.getLast().getHeight(),total=list.stream().mapToDouble(TemplateElement::getHeight).sum(),gap=(end-start-total)/(list.size()-1);double cursor=start;for(TemplateElement e:list){e.setY(cursor);cursor+=e.getHeight()+gap;}}autosave();renderCanvas();}

    @FXML private void bringToFront(){reorder(Integer.MAX_VALUE);}@FXML private void sendToBack(){reorder(Integer.MIN_VALUE);}@FXML private void moveForward(){reorder(1);}@FXML private void moveBackward(){reorder(-1);}
    private void reorder(int direction){if(selectedIds.isEmpty())return;checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());List<TemplateElement> selected=selectedElementsSnapshotFrom(list);list.removeAll(selected);if(direction==Integer.MAX_VALUE)list.addAll(selected);else if(direction==Integer.MIN_VALUE)list.addAll(0,selected);else{int anchor=direction>0?Math.min(list.size(),Math.max(0,highestOriginalIndex(selected)+direction)):Math.max(0,lowestOriginalIndex(selected)+direction);list.addAll(Math.min(anchor,list.size()),selected);}template.setElements(list);autosave();renderCanvas();}
    private int highestOriginalIndex(List<TemplateElement> s){return s.stream().mapToInt(e->indexOfId(template.getElements(),e.getId())).max().orElse(0);}private int lowestOriginalIndex(List<TemplateElement>s){return s.stream().mapToInt(e->indexOfId(template.getElements(),e.getId())).min().orElse(0);}private int indexOfId(List<TemplateElement> list,String id){for(int i=0;i<list.size();i++)if(Objects.equals(list.get(i).getId(),id))return i;return -1;}
    @FXML private void toggleLock(){if(selectedIds.isEmpty())return;checkpoint();boolean lock=selectedElements().stream().anyMatch(e->!e.isLocked());for(TemplateElement e:selectedElements())e.setLocked(lock);autosave();refreshSelectionInspector();renderCanvas();}
    @FXML private void toggleVisibility(){if(selectedIds.isEmpty())return;checkpoint();boolean show=selectedElements().stream().anyMatch(e->!e.isVisible());for(TemplateElement e:selectedElements())e.setVisible(show);autosave();refreshSelectionInspector();renderCanvas();}

    private void beginInlineEdit(TemplateElement e){
        if(e==null||!isTextLike(e)||e.isLocked()||previewMode)return;closeInlineEditor(false);inlineEditor=new TextArea(e.getText());inlineEditor.setWrapText(true);inlineEditor.getStyleClass().add("pdf-v2-inline-editor");inlineEditor.setLayoutX(e.getX()*scale);inlineEditor.setLayoutY(e.getY()*scale);inlineEditor.setPrefSize(Math.max(60,e.getWidth()*scale),Math.max(30,e.getHeight()*scale));inlineEditor.setStyle(styleFor(e));inlineEditor.focusedProperty().addListener((obs,old,focused)->{if(!focused&&old)closeInlineEditor(true);});inlineEditor.setOnKeyPressed(event->{if(event.getCode()==KeyCode.ESCAPE){closeInlineEditor(false);event.consume();}else if(event.getCode()==KeyCode.ENTER&&(event.isControlDown()||event.isMetaDown())){closeInlineEditor(true);event.consume();}});canvasPane.getChildren().add(inlineEditor);Platform.runLater(()->inlineEditor.requestFocus());
    }
    private void closeInlineEditor(boolean commit){if(inlineEditor==null)return;TextArea editor=inlineEditor;inlineEditor=null;TemplateElement e=selectedElement();if(commit&&e!=null){checkpoint();e.setText(editor.getText());autosave();}canvasPane.getChildren().remove(editor);if(commit)renderCanvas();}

    // Clipboard is intentionally internal-to-Studio JSON-lite via ids; no system-sensitive data is exposed.
    private List<TemplateElement> objectClipboard=List.of();
    private Set<String> objectClipboardRoots=Set.of();
    private void copySelectedObjectsToClipboard(){Set<String> roots=new LinkedHashSet<>(selectedIds);Set<String> ids=idsWithDescendants(roots);objectClipboard=template.getElements().stream().filter(e->ids.contains(e.getId())).map(TemplateElement::snapshotCopy).toList();objectClipboardRoots=Set.copyOf(roots);lblSaveState.setText(objectClipboard.size()+" object(s) copied");}
    private void pasteObjectsFromClipboard(){if(objectClipboard.isEmpty())return;checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());Map<String,TemplateElement> copies=new LinkedHashMap<>();for(TemplateElement source:objectClipboard){TemplateElement c=source.copy();c.setPageIndex(pageIndex);c.setX(source.getX()+12);c.setY(source.getY()+12);copies.put(source.getId(),c);}for(TemplateElement source:objectClipboard){TemplateElement c=copies.get(source.getId());if(copies.containsKey(source.getParentId()))c.setParentId(copies.get(source.getParentId()).getId());list.add(c);}template.setElements(list);selectedIds.clear();for(String rootId:objectClipboardRoots){TemplateElement c=copies.get(rootId);if(c!=null)selectedIds.add(c.getId());}autosave();refreshSelectionInspector();renderCanvas();}

    // ---------------------------------------------------------------------
    // Pages, preview, save/export
    // ---------------------------------------------------------------------

    @FXML private void backToLibrary(){DocumentStudioContext.selectMode(DocumentStudioContext.Mode.PDF);DashboardController.navigateFromDocumentStudio("PDF Studio","/fxml/pages/DocumentStudio.fxml");}

    @FXML private void saveDraft(){
        if(template==null)return;
        try{
            TemplateStorageService.saveDraft(template);
            refreshMeta(); updateDefaultButton();
            lblSaveState.setText("Draft saved • production unchanged");
            ModernDialog.success(root,"Draft saved",template.getName()+" was saved as a working draft. Current PDF/Print/Preview/Email generation is unchanged.");
        }catch(Exception e){ModernDialog.error(root,"Save failed","PDF Studio",rootMessage(e));}
    }

    @FXML private void publishTemplate(){
        if(template==null)return;
        try{
            TemplateStorageService.publish(template);
            refreshMeta(); updateDefaultButton();
            lblSaveState.setText("Published candidate v"+template.getPublishedVersion()+" • production unchanged");
            ModernDialog.success(root,"Template published",template.getName()+" passed PDF validation and is ready for testing. Publishing does not change any current document-generation flow.");
        }catch(Exception e){ModernDialog.error(root,"Publish failed","PDF Studio",rootMessage(e));}
    }

    @FXML private void markDefault(){
        if(template==null)return;
        if(template.getDocumentType().isGeneral()||!DocumentFlowRegistry.isAutomatic(template.getDocumentType())){
            ModernDialog.info(root,"Design-only template","PDF Studio","Choose an ERP document type before marking this template as a system default.");
            return;
        }
        if(template.getPublishedVersion()<=0||template.isUnpublishedChanges()){
            ModernDialog.info(root,"Publish required","PDF Studio","Publish the current design first. Draft and preview changes never affect production.");
            return;
        }
        if(!ModernDialog.confirm(root,"Mark as System Default",
                "Activate "+template.getName()+" for "+template.getDocumentType().label()+"?",
                "This is the ONLY action in PDF Studio that can change runtime document generation. The validated published snapshot will be copied to an isolated active snapshot; later draft edits or publishing will not change that active version until you mark default again."))return;
        try{
            TemplateStorageService.activateAndSetDefault(template);
            refreshMeta(); updateDefaultButton();
            lblSaveState.setText("ACTIVE runtime v"+template.getActiveVersion());
            ModernDialog.success(root,"System default activated",template.getName()+" v"+template.getActiveVersion()+" is now the active "+template.getDocumentType().label()+" template. Built-in generation remains the safety fallback if rendering fails.");
        }catch(Exception e){ModernDialog.error(root,"Default could not be activated","PDF Studio",rootMessage(e));}
    }

    @FXML private void showDesignMode(){
        if(template==null)return;
        previewMode=false; dataPreviewMode=false; previewPdf=null;
        configurePages(sourcePageCount); clearSelection(); updateModeButtons(); renderCanvas(); ensurePageObjects(pageIndex);
    }

    @FXML private void showDataPreviewMode(){
        if(template==null)return;
        previewMode=false; dataPreviewMode=true; previewPdf=null;
        configurePages(sourcePageCount); clearSelection(); updateModeButtons(); renderCanvas(); ensurePageObjects(pageIndex);
    }

    @FXML private void showFinalMode(){
        if(template==null)return;
        try{
            previewPdf=WorkspaceManager.getTempFolder().resolve("pdf-studio-v3-final-"+template.getId()+".pdf");
            PdfStudioRenderer.render(template,previewData(),previewPdf);
            previewMode=true; dataPreviewMode=false;
            var size=PdfPreviewSupport.pageSize(previewPdf,0);
            configurePages(size.pageCount()); clearSelection(); updateModeButtons(); renderCanvas();
        }catch(Exception e){
            previewMode=false; dataPreviewMode=false; updateModeButtons();
            ModernDialog.error(root,"Final PDF preview failed","PDF Studio",rootMessage(e));
        }
    }

    private void updateModeButtons(){
        if(btnDesignMode!=null)btnDesignMode.setDisable(!previewMode&&!dataPreviewMode);
        if(btnDataPreviewMode!=null)btnDataPreviewMode.setDisable(dataPreviewMode&&!previewMode);
        if(btnFinalMode!=null)btnFinalMode.setDisable(previewMode);
    }

    @FXML private void exportPdf(){if(template==null)return;FileChooser chooser=new FileChooser();chooser.setTitle("Export PDF");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files","*.pdf"));chooser.setInitialFileName(template.getName().replaceAll("[^A-Za-z0-9._-]","-")+".pdf");var file=chooser.showSaveDialog(root.getScene().getWindow());if(file==null)return;try{PdfStudioRenderer.render(template,previewData(),file.toPath());ModernDialog.success(root,"Test PDF exported",file.getAbsolutePath()+" • Production templates were not changed.");}catch(Exception e){ModernDialog.error(root,"Export failed","PDF Studio",rootMessage(e));}}

    private TemplateData previewData(){DocumentSample sample=cmbSampleDocument.getValue();if(sample==null)return currentPreviewData==null?DocumentDataService.sample(template.getDocumentType()):currentPreviewData;try{return DocumentDataService.load(template.getDocumentType(),sample.id());}catch(Exception e){return currentPreviewData==null?DocumentDataService.sample(template.getDocumentType()):currentPreviewData;}}

    @FXML private void appendBlankPage(){if(previewMode)return;try{sourcePageCount=TemplateStorageService.appendBlankPage(template,pageIndex);configurePages(sourcePageCount);pageIndex=sourcePageCount-1;lstPages.getSelectionModel().select(pageIndex);clearObjectCaches();renderCanvas();ensurePageObjects(pageIndex);}catch(Exception e){ModernDialog.error(root,"Page could not be added","PDF Studio",rootMessage(e));}}
    @FXML private void deleteCurrentPage(){if(previewMode)return;if(!ModernDialog.confirm(root,"Delete Page","Delete page "+(pageIndex+1)+"?","Only the workspace template copy is changed."))return;try{sourcePageCount=TemplateStorageService.deletePage(template,pageIndex);pageIndex=Math.max(0,Math.min(pageIndex,sourcePageCount-1));configurePages(sourcePageCount);clearObjectCaches();clearSelection();renderCanvas();ensurePageObjects(pageIndex);}catch(Exception e){ModernDialog.error(root,"Page could not be deleted","PDF Studio",rootMessage(e));}}
    @FXML private void rotatePageLeft(){rotate(-90);}@FXML private void rotatePageRight(){rotate(90);}private void rotate(int degrees){if(previewMode)return;try{TemplateStorageService.rotatePage(template,pageIndex,degrees);var size=PdfPreviewSupport.pageSize(sourcePdf,pageIndex);pageWidth=size.width();pageHeight=size.height();clearObjectCaches();clearSelection();renderCanvas();ensurePageObjects(pageIndex);}catch(Exception e){ModernDialog.error(root,"Page could not be rotated","PDF Studio",rootMessage(e));}}

    @FXML private void fitWidth(){Platform.runLater(()->{double available=Math.max(260,canvasScroll.getViewportBounds().getWidth()-70);setZoomForScale(available/Math.max(1,pageWidth));});}
    @FXML private void fitPage(){Platform.runLater(()->{double w=Math.max(260,canvasScroll.getViewportBounds().getWidth()-70),h=Math.max(260,canvasScroll.getViewportBounds().getHeight()-70);setZoomForScale(Math.min(w/Math.max(1,pageWidth),h/Math.max(1,pageHeight)));});}
    private void setZoomForScale(double target){double pct=target/BASE_SCALE*100.0;zoomSlider.setValue(Math.max(zoomSlider.getMin(),Math.min(zoomSlider.getMax(),pct)));}

    // ---------------------------------------------------------------------
    // Undo/redo and persistence
    // ---------------------------------------------------------------------

    @FXML private void undo(){if(undo.isEmpty()||previewMode)return;redo.push(snapshot(template.getElements()));template.setElements(undo.pop());selectedIds.clear();autosave();clearInspector();renderCanvas();}
    @FXML private void redo(){if(redo.isEmpty()||previewMode)return;undo.push(snapshot(template.getElements()));template.setElements(redo.pop());selectedIds.clear();autosave();clearInspector();renderCanvas();}
    private void checkpoint(){undo.push(snapshot(template.getElements()));while(undo.size()>50)undo.removeLast();redo.clear();}
    private List<TemplateElement> snapshot(List<TemplateElement> source){return source.stream().map(TemplateElement::snapshotCopy).collect(Collectors.toCollection(ArrayList::new));}
    private void autosave(){try{TemplateStorageService.saveDraft(template);lblSaveState.setText("Draft saved • production unchanged");}catch(Exception e){lblSaveState.setText("Save failed");}refreshMeta();updateDefaultButton();}

    // ---------------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------------

    private void refreshMeta(){
        if(template==null)return;
        lblTemplateName.setText(template.getName());
        String lifecycle=template.getStatus().name();
        if(template.getPublishedVersion()>0)lifecycle+=" • published v"+template.getPublishedVersion();
        if(template.isRuntimeEnabled())lifecycle+=" • ACTIVE v"+template.getActiveVersion();
        if(template.isUnpublishedChanges())lifecycle+=" • draft changes";
        lblTemplateMeta.setText(template.getDocumentType().label()+" • "+lifecycle+" • original PDF protected");
    }
    private void updateDefaultButton(){
        if(template==null)return;
        boolean automatic=DocumentFlowRegistry.isAutomatic(template.getDocumentType());
        if(btnSaveDefault!=null){
            btnSaveDefault.setText(template.isRuntimeEnabled()?"Mark Default Again":"Mark as Default");
            btnSaveDefault.setDisable(!automatic||template.getPublishedVersion()<=0||template.isUnpublishedChanges());
        }
        if(btnPublish!=null)btnPublish.setDisable(template.getStatus()==TemplateStatus.ARCHIVED);
    }
    private void updatePageWarning(){if(template==null)return;long outside=template.getElements().stream().filter(e->e.getPageIndex()==pageIndex&&PdfStyleResolver.effectivelyVisible(template,e)).filter(e->e.getX()<0||e.getY()<0||e.getX()+e.getWidth()>pageWidth||e.getY()+e.getHeight()>pageHeight).count();lblPageWarning.setText(outside==0?"":outside+" object(s) extend outside page • export will clip");}
    private void clearObjectCaches(){textCache.clear();imageCache.clear();vectorCache.clear();sourcePageImages.clear();loadingPages.clear();}
    private String displayName(TemplateElement e){if(e==null)return "Object";return switch(e.getType()){case TEXT->"Text • "+abbreviate(e.getText(),28);case FIELD->"ERP Field • "+e.getFieldKey();case IMAGE->"Image";case IMAGE_FIELD->"ERP Image • "+e.getFieldKey();case BLOCK->"Section / Group";case RECTANGLE->"Rectangle";case WHITEOUT->"Source Mask";case LINE->"Line";case PATH->"Vector Path";case ITEM_TABLE->"Item Repeater";case CHARGE_TABLE->"Charge Repeater";};}
    private boolean isTextLike(TemplateElement e){return e!=null&&(e.getType()==ElementType.TEXT||e.getType()==ElementType.FIELD);}
    private boolean isImageLike(TemplateElement e){return e!=null&&(e.getType()==ElementType.IMAGE||e.getType()==ElementType.IMAGE_FIELD);}
    private boolean isRepeater(TemplateElement e){return e!=null&&(e.getType()==ElementType.ITEM_TABLE||e.getType()==ElementType.CHARGE_TABLE);}
    private String fontHint(String name){String n=name==null?"":name.toUpperCase(Locale.ROOT);if(n.contains("TIMES")||n.contains("SERIF"))return"TIMES";if(n.contains("COURIER")||n.contains("MONO"))return"COURIER";return"HELVETICA";}
    private String bindingKey(String value){if(value==null||value.startsWith("—"))return"";int i=value.indexOf("  •");return i<0?value.trim():value.substring(0,i).trim();}
    private double parse(TextField f,double fallback){try{String s=f.getText()==null?"":f.getText().trim().replace(",","");return s.isBlank()?fallback:Double.parseDouble(s);}catch(Exception ignored){return fallback;}}
    private String fmt(double v){return Math.abs(v-Math.rint(v))<.0001?Long.toString(Math.round(v)):String.format(Locale.ENGLISH,"%.2f",v);}
    private String hex(Color c){if(c==null)return"#000000";return String.format(Locale.ROOT,"#%02X%02X%02X",Math.round((float)c.getRed()*255),Math.round((float)c.getGreen()*255),Math.round((float)c.getBlue()*255));}
    private Color color(String value,Color fallback){try{return Color.web(value);}catch(Exception e){return fallback;}}
    private String abbreviate(String s,int max){String t=s==null?"":s.replace('\n',' ').trim();return t.length()<=max?t:t.substring(0,Math.max(0,max-1))+"…";}
    private String rootMessage(Throwable error){Throwable t=error;while(t.getCause()!=null&&t.getCause()!=t)t=t.getCause();String m=t.getMessage();return m==null||m.isBlank()?t.getClass().getSimpleName():m;}


    private String sampleBackgroundColor(int page, double x, double y, double width, double height) {
        Image image = sourcePageImages.get(page);
        if (image == null || image.getPixelReader() == null || pageWidth <= 0 || pageHeight <= 0) return "#FFFFFF";
        var reader = image.getPixelReader();
        int iw = Math.max(1, (int)Math.round(image.getWidth())), ih = Math.max(1, (int)Math.round(image.getHeight()));
        int left=clampInt((int)Math.floor(x/pageWidth*iw),0,iw-1), right=clampInt((int)Math.ceil((x+width)/pageWidth*iw),0,iw-1);
        int top=clampInt((int)Math.floor(y/pageHeight*ih),0,ih-1), bottom=clampInt((int)Math.ceil((y+height)/pageHeight*ih),0,ih-1);
        int margin=Math.max(2,(int)Math.round(iw/pageWidth*2.0)); Map<Integer,Integer> colors=new HashMap<>();
        for(int py=Math.max(0,top-margin);py<=Math.min(ih-1,bottom+margin);py++) for(int px=Math.max(0,left-margin);px<=Math.min(iw-1,right+margin);px++) {
            boolean ring=px<left||px>right||py<top||py>bottom; if(!ring)continue; int argb=reader.getArgb(px,py);
            int r=((argb>>16)&0xFF)/16*16,g=((argb>>8)&0xFF)/16*16,b=(argb&0xFF)/16*16,key=(r<<16)|(g<<8)|b;colors.merge(key,1,Integer::sum);
        }
        int rgb=colors.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xFFFFFF);
        return String.format(Locale.ROOT,"#%06X",rgb&0xFFFFFF);
    }
    private int clampInt(int value,int min,int max){return Math.max(min,Math.min(max,value));}

    private String resolveExpression(String text,TemplateData data){
        if(text==null||text.isBlank()||data==null)return text==null?"":text;String result=text;java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}").matcher(text);StringBuffer out=new StringBuffer();while(m.find()){String key=m.group(1);String value=data.value(key);m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(value));}m.appendTail(out);return out.toString();
    }

}
