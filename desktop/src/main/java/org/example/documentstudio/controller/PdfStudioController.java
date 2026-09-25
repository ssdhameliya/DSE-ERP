package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
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
import org.example.documentstudio.view.PdfMappingReviewWorkspace;
import org.example.navigation.ScreenLifecycle;
import org.example.shortcut.ShortcutRegistry;
import org.example.shortcut.ShortcutRegistry.Action;
import org.example.util.AppDialogService;

import java.awt.Desktop;
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
    @FXML private Label lblRequiredSummary, lblIssueSummary, lblIssueBar, lblBindingContext;
    @FXML private ProgressBar mappingProgress;
    @FXML private Button btnDesignMode, btnDataPreviewMode, btnFinalMode, btnPublish, btnSaveDefault, btnPublishDefault, btnFixNext, btnMapSelectedField;
    @FXML private ToggleButton tglRequired, tglAll, tglMapped;
    @FXML private ComboBox<DocumentSample> cmbSampleDocument;
    @FXML private ComboBox<String> cmbFontFamily, cmbTextFit, cmbTextAlignment, cmbImageFit, cmbPageRule,
            cmbFlowAnchorMode, cmbGrowthDirection, cmbOverflowPolicy;
    @FXML private TextField txtFieldSearch, txtInspectorFieldSearch;
    @FXML private ListView<TemplateFieldDefinition> lstFields, lstInspectorFieldSuggestions;
    @FXML private ListView<TemplateRequirementState> lstRequirements;
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
            txtTableColumns, txtRowHeight, txtHeaderHeight, txtFlowAnchorId, txtFlowGap, txtFlowGroupId, txtFlowRole;
    @FXML private CheckBox chkAutoHeight;
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
    /** Visual sub-region selected inside a source text line (for example only the GSTIN value after the fixed label). */
    private PdfTextRegion selectedSourceTextHit;
    private PdfFormFieldRegion selectedSourceForm;
    private PdfImageRegion selectedSourceImage;
    private PdfImageExtractionService.VectorRegion selectedSourceVector;
    private final Map<Integer,List<PdfTextRegion>> textCache = new HashMap<>();
    private final Map<Integer,List<PdfFormFieldRegion>> formCache = new HashMap<>();
    private final Map<Integer,List<PdfImageRegion>> imageCache = new HashMap<>();
    private final Map<Integer,List<PdfImageExtractionService.VectorRegion>> vectorCache = new HashMap<>();
    private final Map<Integer,Image> sourcePageImages = new HashMap<>();
    private final Set<Integer> loadingPages = new HashSet<>();
    private final AtomicInteger renderSequence = new AtomicInteger();
    private final AtomicInteger previewLoadGeneration = new AtomicInteger();

    private final PdfStudioHistory history = new PdfStudioHistory(50);
    private TemplateElement formatClipboard;
    private TemplateData currentPreviewData;
    private PdfAutoMappingService.Analysis currentMappingAnalysis = new PdfAutoMappingService.Analysis(List.of(),0,0,0,0);
    private boolean inspectorSync;
    private String selectedBindingKey = "";
    private boolean dragging;
    private double dragSceneX, dragSceneY;
    private final Map<String,double[]> dragOrigins = new HashMap<>();
    private TextArea inlineEditor;
    private final List<Line> smartGuideLines = new ArrayList<>();
    private Scene shortcutScene;
    private EventHandler<KeyEvent> shortcutHandler;

    private enum Handle { NW, N, NE, E, SE, S, SW, W }

    @FXML
    public void initialize() {
        String id = DocumentStudioContext.consume();
        template = TemplateStorageService.find(id).orElse(null);
        if (template == null) {
            Platform.runLater(() -> {
                AppDialogService.error(root, "Template unavailable", "PDF Studio", "The selected template could not be found.");
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
            Platform.runLater(() -> AppDialogService.error(root, "PDF could not be opened", "PDF Studio", rootMessage(error)));
            return;
        }

        configureInspectorControls();
        configureFields();
        configureRequirementUi();
        configureInspectorFieldSearch();
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
        installKeyboardShortcuts();
        if (template != null) {
            refreshMeta();
            refreshRequirementUi();
            renderCanvas();
            ensurePageObjects(pageIndex);
        }
    }

    @Override public void onScreenHidden() {
        previewLoadGeneration.incrementAndGet();
        detachKeyboardShortcuts();
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    private void configureInspectorControls() {
        colorText.setValue(Color.web("#172033"));
        colorFill.setValue(Color.WHITE);
        colorStroke.setValue(Color.web("#94A3B8"));
        cmbFontFamily.setItems(FXCollections.observableArrayList("HELVETICA", "ARIAL", "TIMES", "COURIER"));
        cmbTextFit.setItems(FXCollections.observableArrayList("SHRINK", "WRAP", "CLIP", "FIXED"));
        cmbTextAlignment.setItems(FXCollections.observableArrayList("LEFT", "CENTER", "RIGHT"));
        cmbImageFit.setItems(FXCollections.observableArrayList("FIT", "FILL", "STRETCH"));
        cmbPageRule.setItems(FXCollections.observableArrayList("AUTO", "FIXED", "FIRST", "EVERY", "CONTINUATION", "LAST"));
        cmbFlowAnchorMode.setItems(FXCollections.observableArrayList("ABSOLUTE", "TOP", "BOTTOM", "AFTER", "BEFORE"));
        cmbGrowthDirection.setItems(FXCollections.observableArrayList("FIXED", "DOWN", "UP", "BOTH"));
        cmbOverflowPolicy.setItems(FXCollections.observableArrayList("ERROR", "PAGINATE", "SHRINK", "CLIP"));
        cmbFontFamily.getSelectionModel().select("HELVETICA");
        cmbTextFit.getSelectionModel().select("SHRINK");
        cmbTextAlignment.getSelectionModel().select("LEFT");
        cmbImageFit.getSelectionModel().select("FIT");
        cmbPageRule.getSelectionModel().select("AUTO");
        cmbFlowAnchorMode.getSelectionModel().select("ABSOLUTE");
        cmbGrowthDirection.getSelectionModel().select("FIXED");
        cmbOverflowPolicy.getSelectionModel().select("ERROR");
        clearInspector();
    }

    private void configureFields() {
        lstFields.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(TemplateFieldDefinition item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("erp-field-search-mapped", "erp-field-search-recommended");
                if (empty || item == null) { setText(null); return; }
                TemplateMappingValidationService.Result readiness = TemplateMappingValidationService.evaluate(template);
                boolean mapped = readiness.mappedFields().contains(item.key()) || readiness.itemColumns().contains(item.key());
                TemplateMappingRequirement context = selectedRequirement();
                boolean recommended = context != null && context.acceptedFields().contains(item.key());
                setText(item.label() + "\n" + item.category() + "  •  " + item.key());
                if (mapped) getStyleClass().add("erp-field-search-mapped");
                if (recommended) getStyleClass().add("erp-field-search-recommended");
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
        refreshFieldList("");
    }

    private void configureRequirementUi() {
        if (lstRequirements == null) return;
        ToggleGroup group = new ToggleGroup();
        if (tglRequired != null) { tglRequired.setToggleGroup(group); tglRequired.setSelected(true); }
        if (tglAll != null) tglAll.setToggleGroup(group);
        if (tglMapped != null) tglMapped.setToggleGroup(group);
        group.selectedToggleProperty().addListener((obs, old, value) -> {
            if (value == null && old != null) old.setSelected(true);
            refreshRequirementUi();
        });
        lstRequirements.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(TemplateRequirementState state, boolean empty) {
                super.updateItem(state, empty);
                getStyleClass().removeAll("erp-requirement-mapped", "erp-requirement-missing", "erp-requirement-optional");
                if (empty || state == null || state.requirement() == null) { setText(null); return; }
                TemplateMappingRequirement r = state.requirement();
                String badge = state.satisfied() ? "✓ MAPPED" : r.level().name();
                String via = state.satisfied() && !state.satisfiedBy().isEmpty()
                        ? "\n→ " + friendlyFieldName(state.satisfiedBy().getFirst()) : "";
                setText(r.label() + "    " + badge + via);
                if (state.satisfied()) getStyleClass().add("erp-requirement-mapped");
                else if (r.level() == TemplateMappingRequirement.Level.REQUIRED) getStyleClass().add("erp-requirement-missing");
                else getStyleClass().add("erp-requirement-optional");
            }
        });
        lstRequirements.getSelectionModel().selectedItemProperty().addListener((obs, old, state) -> {
            if (state == null || state.requirement() == null) return;
            TemplateMappingRequirement r = state.requirement();
            if (lblBindingContext != null) {
                lblBindingContext.setText(r.label() + " • " + r.level().name() + "\n" + r.explanation());
            }
            refreshFieldList(txtFieldSearch == null ? "" : txtFieldSearch.getText());
            refreshInspectorSuggestions(txtInspectorFieldSearch == null ? "" : txtInspectorFieldSearch.getText());
        });
        lstRequirements.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) focusSelectedRequirement();
        });
        refreshRequirementUi();
    }

    private void configureInspectorFieldSearch() {
        if (txtInspectorFieldSearch == null || lstInspectorFieldSuggestions == null) return;
        lstInspectorFieldSuggestions.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(TemplateFieldDefinition item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.label() + "\n" + item.category() + "  •  " + item.key());
            }
        });
        txtInspectorFieldSearch.textProperty().addListener((obs, old, value) -> {
            if (!inspectorSync) refreshInspectorSuggestions(value);
        });
        lstInspectorFieldSuggestions.getSelectionModel().selectedItemProperty().addListener((obs, old, field) -> {
            if (inspectorSync || field == null) return;
            selectedBindingKey = field.key();
            inspectorSync = true;
            try { txtInspectorFieldSearch.setText(field.label()); }
            finally { inspectorSync = false; }
        });
        lstInspectorFieldSuggestions.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateManualMappingState());
        lstInspectorFieldSuggestions.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) applySuggestedBinding();
        });
        refreshInspectorSuggestions("");
        updateManualMappingState();
    }

    private void updateManualMappingState() {
        if (btnMapSelectedField == null) return;
        TemplateFieldDefinition field = lstInspectorFieldSuggestions == null ? null : lstInspectorFieldSuggestions.getSelectionModel().getSelectedItem();
        TemplateElement selected = selectedElement();
        boolean textTarget = selectedSourceText != null || selectedSourceForm != null || (selected != null && isTextLike(selected));
        boolean imageTarget = selectedSourceImage != null || selectedSourceVector != null || (selected != null && isImageLike(selected));
        boolean compatibleTarget = field != null && (field.image() ? imageTarget : textTarget);
        boolean enabled = !previewMode && compatibleTarget;
        btnMapSelectedField.setDisable(!enabled);
        if (field == null) {
            btnMapSelectedField.setText("Select ERP Field to Map");
        } else if (!compatibleTarget) {
            btnMapSelectedField.setText(field.image() ? "Select PDF Image to Map" : "Select PDF Text to Map");
        } else {
            btnMapSelectedField.setText("Map " + field.label());
        }
    }

    private void refreshFieldList(String query) {
        if (template == null || lstFields == null) return;
        TemplateMappingValidationService.Result readiness = TemplateMappingValidationService.evaluate(template);
        List<TemplateFieldDefinition> fields = TemplateFieldSearchService.search(template.getDocumentType(), query,
                selectedRequirement(), union(readiness.mappedFields(), readiness.itemColumns()));
        lstFields.setItems(FXCollections.observableArrayList(fields));
        lstFields.refresh();
    }

    private void refreshInspectorSuggestions(String query) {
        if (template == null || lstInspectorFieldSuggestions == null) return;
        TemplateMappingValidationService.Result readiness = TemplateMappingValidationService.evaluate(template);
        List<TemplateFieldDefinition> fields = TemplateFieldSearchService.search(template.getDocumentType(), query,
                selectedRequirement(), union(readiness.mappedFields(), readiness.itemColumns()));
        lstInspectorFieldSuggestions.setItems(FXCollections.observableArrayList(fields.stream().limit(12).toList()));
        if (!selectedBindingKey.isBlank()) {
            fields.stream().filter(f -> f.key().equals(selectedBindingKey)).findFirst()
                    .ifPresent(f -> lstInspectorFieldSuggestions.getSelectionModel().select(f));
        }
    }

    private void refreshRequirementUi() {
        if (template == null || lstRequirements == null) return;
        TemplateMappingValidationService.Result result = TemplateMappingValidationService.evaluate(template);
        List<TemplateRequirementState> states = result.requirements();
        if (tglRequired != null && tglRequired.isSelected())
            states = states.stream().filter(s -> s.requirement().level() == TemplateMappingRequirement.Level.REQUIRED).toList();
        else if (tglMapped != null && tglMapped.isSelected())
            states = states.stream().filter(TemplateRequirementState::satisfied).toList();
        lstRequirements.setItems(FXCollections.observableArrayList(states));
        lstRequirements.refresh();
        if (lblRequiredSummary != null) {
            lblRequiredSummary.setText(result.requiredMapped() + " / " + result.requiredCount() + " required mapped");
            setSemanticStatus(lblRequiredSummary, result.readyForDefault() ? "success" : "error");
        }
        if (lblIssueSummary != null) {
            lblIssueSummary.setText(result.errorCount() + " errors • " + result.warningCount() + " warnings");
            setSemanticStatus(lblIssueSummary, result.errorCount() > 0 ? "error" : result.warningCount() > 0 ? "warning" : "success");
        }
        if (lblIssueBar != null) lblIssueBar.setText(result.readyForDefault()
                ? (result.warningCount() == 0 ? "Template mapping is ready" : result.warningCount() + " warning(s) to review")
                : result.errorCount() + " required issue(s) must be fixed before Publish / Default");
        if (btnFixNext != null) {
            btnFixNext.setDisable(result.issues().isEmpty());
            btnFixNext.setText(result.readyForDefault() && result.warningCount() > 0 ? "Fix Next Issue" : "Fix Next Required");
        }
        refreshFieldList(txtFieldSearch == null ? "" : txtFieldSearch.getText());
    }

    private void setSemanticStatus(Node node, String state) {
        if (node == null) return;
        node.getStyleClass().removeAll("erp-status-neutral", "erp-status-success", "erp-status-warning", "erp-status-error");
        node.getStyleClass().add("erp-status-" + (state == null || state.isBlank() ? "neutral" : state));
    }

    private TemplateMappingRequirement selectedRequirement() {
        TemplateRequirementState state = lstRequirements == null ? null : lstRequirements.getSelectionModel().getSelectedItem();
        return state == null ? null : state.requirement();
    }

    private Set<String> union(Set<String> a, Set<String> b) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (a != null) out.addAll(a); if (b != null) out.addAll(b); return out;
    }

    private String friendlyFieldName(String key) {
        TemplateFieldDefinition field = TemplateFieldCatalog.findPdf(template.getDocumentType(), key);
        if (field != null) return field.label();
        if (key != null && key.startsWith("item.")) {
            field = TemplateFieldCatalog.findPdf(template.getDocumentType(), key);
            if (field != null) return field.label();
        }
        return key == null ? "" : key;
    }

    @FXML private void fixNextIssue() {
        if (template == null || lstRequirements == null) return;
        TemplateMappingValidationService.Result result = TemplateMappingValidationService.evaluate(template);
        Optional<TemplateRequirementState> missing = result.requirements().stream()
                .filter(s -> s.requirement().level() == TemplateMappingRequirement.Level.REQUIRED && !s.satisfied()).findFirst();
        if (missing.isPresent()) {
            if (tglRequired != null) tglRequired.setSelected(true);
            refreshRequirementUi();
            lstRequirements.getItems().stream().filter(s -> s.requirement().id().equals(missing.get().requirement().id()))
                    .findFirst().ifPresent(s -> lstRequirements.getSelectionModel().select(s));
            focusSelectedRequirement();
            return;
        }
        if (focusFirstActionableIssue(result)) return;
        reviewIssues();
    }

    private void focusSelectedRequirement() {
        TemplateMappingRequirement requirement = selectedRequirement();
        if (requirement == null) return;
        TemplateFieldDefinition preferred = requirement.acceptedFields().stream()
                .map(key -> TemplateFieldCatalog.findPdf(template.getDocumentType(), key))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        String query = preferred == null ? requirement.label() : preferred.label();
        if (txtFieldSearch != null) {
            txtFieldSearch.setText(query);
            txtFieldSearch.requestFocus();
        }
        refreshFieldList(query);
        if (preferred != null) {
            lstFields.getItems().stream().filter(f -> preferred.key().equals(f.key())).findFirst()
                    .ifPresent(f -> lstFields.getSelectionModel().select(f));
        } else if (!lstFields.getItems().isEmpty()) {
            lstFields.getSelectionModel().selectFirst();
        }
        if (lblInspectorHint != null) {
            String suggested = preferred == null ? requirement.label() : preferred.label() + " (" + preferred.key() + ")";
            lblInspectorHint.setText("Next required mapping: " + requirement.label() + ". Suggested ERP field: " + suggested
                    + ". Click the matching printed PDF value, then click Map. The field list has been filtered for you.");
        }
    }

    private boolean focusFirstActionableIssue(TemplateMappingValidationService.Result result) {
        if (result == null) return false;
        // Required errors remain first priority, but actionable warnings must not become dead-end
        // information dialogs once all required mappings are complete.
        for (boolean errorsFirst : new boolean[]{true, false}) {
            for (TemplateValidationIssue issue : result.issues()) {
                if (issue.error() != errorsFirst) continue;
                if ("ITEM_HEADER_MAPPING".equals(issue.requirementId())) {
                    if (focusFirstUnmappedItemHeader()) return true;
                }
                if ("ITEM_TABLE_PAGE_FLOW".equals(issue.requirementId())) {
                    Optional<TemplateElement> table = template.getElements().stream()
                            .filter(e -> e != null && e.getType() == ElementType.ITEM_TABLE && PdfStyleResolver.effectivelyVisible(template, e))
                            .findFirst();
                    if (table.isPresent()) {
                        selectOnlyWithoutRender(table.get());
                        populateInspector(table.get());
                        renderCanvas();
                        if (lblInspectorHint != null) lblInspectorHint.setText(
                                "Item Table flow capacity needs attention. PDF Studio has selected the table for you. "
                                        + "Increase the table body area or reduce Row Height, then use Preview with Selected Record. "
                                        + "FLOW_FIXED templates are validated against the same usable closing region as runtime.");
                        return true;
                    }
                }
                if (issue.requirementId()!=null && issue.requirementId().startsWith("MULTILINE_")) {
                    Optional<TemplateElement> flowField = template.getElements().stream()
                            .filter(e -> e != null && PdfStyleResolver.effectivelyVisible(template, e))
                            .filter(e -> {
                                TemplateFieldDefinition def=pdfField(e.getFieldKey());
                                return def!=null&&def.multiline();
                            }).findFirst();
                    if (flowField.isPresent()) {
                        selectOnlyWithoutRender(flowField.get());
                        populateInspector(flowField.get());
                        renderCanvas();
                        if (lblInspectorHint != null) lblInspectorHint.setText(
                                "Multiline flow needs review. PDF Studio selected the detected flow field. "
                                        + "Review Mapping shows Wrap, Auto Height, Grow direction and its source block before publishing.");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean focusFirstUnmappedItemHeader() {
        for (TemplateElement table : template.getElements()) {
            if (table == null || table.getType() != ElementType.ITEM_TABLE || !PdfStyleResolver.effectivelyVisible(template, table)) continue;
            List<TemplateColumnBinding> bindings = table.getTableColumnBindings();
            for (int i = 0; i < bindings.size(); i++) {
                TemplateColumnBinding binding = bindings.get(i);
                if (binding != null && !ManualTemplateMappingService.normalizeItemColumn(binding.getFieldKey()).isBlank()) continue;
                String label = binding == null ? "" : binding.getSourceLabel();
                String suggestedKey = suggestedItemFieldForHeader(label);
                TemplateFieldDefinition suggested = suggestedKey.isBlank() ? null : TemplateFieldCatalog.findPdf(template.getDocumentType(), suggestedKey);
                selectOnlyWithoutRender(table);
                populateInspector(table);
                renderCanvas();
                if (txtFieldSearch != null) {
                    String query = suggested == null ? label : suggested.label();
                    txtFieldSearch.setText(query == null ? "" : query);
                    refreshFieldList(txtFieldSearch.getText());
                    if (suggested != null) {
                        lstFields.getItems().stream().filter(f -> suggested.key().equals(f.key())).findFirst()
                                .ifPresent(f -> lstFields.getSelectionModel().select(f));
                    }
                }
                if (lblInspectorHint != null) {
                    lblInspectorHint.setText("Unmapped Item header: “" + (label == null || label.isBlank() ? "column " + (i + 1) : label) + "”. "
                            + (suggested == null ? "Choose the matching item.* ERP field" : "Suggested ERP field: " + suggested.label() + " (" + suggested.key() + ")")
                            + ", then drag it onto that physical header cell. You do not map item rows individually.");
                }
                return true;
            }
        }
        return false;
    }

    private String suggestedItemFieldForHeader(String sourceLabel) {
        String n = sourceLabel == null ? "" : sourceLabel.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return switch (n) {
            case "sr. no.", "sr no", "serial", "#" -> "item.serial";
            case "hsn code", "hsn", "hsn/sac", "hsn / sac" -> "item.hsn";
            case "product description", "description", "particulars", "item description" -> "item.descriptionWithRemarks";
            case "qty", "quantity", "nos", "nos." -> "item.quantity";
            case "unit rate", "rate", "unit price", "price" -> "item.rate";
            case "unit", "uom" -> "item.unit";
            case "amount (inr)", "amount", "taxable", "net value" -> "item.taxable";
            default -> "";
        };
    }


    @FXML private void reviewMapping() {
        showMappingReview(null, null, buildDetectedReviewBlocks(currentMappingAnalysis, false));
    }

    private boolean showMappingReview(PdfMappingReviewSession.Section initialSection, List<TemplateElement> rollbackSnapshot) {
        return showMappingReview(initialSection, rollbackSnapshot, List.of());
    }

    private boolean showMappingReview(PdfMappingReviewSession.Section initialSection, List<TemplateElement> rollbackSnapshot,
                                      List<PdfMappingReviewSession.DetectedBlock> detectedBlocks) {
        if (template == null || previewMode) return false;
        PdfMappingReviewSession session = PdfMappingReviewSession.from(template, detectedBlocks, detectFinancialReviewRoles());
        if (session.entries().isEmpty()) {
            AppDialogService.info(root, "Nothing to review", "Review Auto Mapping",
                    "No detected or mapped ERP values are available yet. Choose a real Preview Record, run Auto Map, Detect Item Headers, or map a printed value first.");
            return false;
        }
        PdfMappingReviewWorkspace workspace = new PdfMappingReviewWorkspace(session, initialSection);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType save = new ButtonType("Save Mapping", ButtonBar.ButtonData.OK_DONE);
        Optional<ButtonType> result = AppDialogService.workspace(root, "mapping", "Review Auto Mapping",
                "Review detected ERP mappings",
                "Auto detection is a suggestion layer. Confirm, change, keep static or reset mappings here; source PDF geometry stays protected.",
                workspace, 1180, 760, cancel, save);
        if (result.isPresent() && result.get().equals(save)) {
            Map<String,String> beforeFields = template.getElements().stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(TemplateElement::getId, TemplateElement::getFieldKey, (a,b)->a, LinkedHashMap::new));
            checkpoint();
            session.commit();
            materializePendingReviewEntries(session.pendingEntries());
            // If a reviewed scalar field changed semantic meaning, rebuild its protected source-value
            // geometry (e.g. Billing Address becomes multiline) without changing the source artwork.
            List<PdfTextRegion> allText = null;
            for (TemplateElement element : new ArrayList<>(template.getElements())) {
                if (element == null || element.getFieldKey().isBlank() || element.getReplacementSourceKey().isBlank()) continue;
                String before = beforeFields.getOrDefault(element.getId(), "");
                if (Objects.equals(before, element.getFieldKey())) continue;
                if (!(element.getType()==ElementType.TEXT || element.getType()==ElementType.FIELD)) continue;
                if (allText == null) allText = extractAllText();
                PdfTextRegion source = allText.stream().filter(r -> sourceKey(r).equals(element.getReplacementSourceKey())).findFirst().orElse(null);
                if (source != null) remapSourceTextGeometry(element, source, element.getFieldKey());
            }
            autosave(); refreshRequirementUi(); refreshLayers(); renderCanvas(); analyzeMapping(false);
            if (lblSaveState != null) lblSaveState.setText("Reviewed mapping saved ✓");
            if (lblInspectorHint != null) lblInspectorHint.setText("Review Mapping saved. Confirmed/manual mappings are preserved if Auto Detect runs again.");
            return true;
        }
        if (rollbackSnapshot != null) {
            template.setElements(rollbackSnapshot.stream().map(TemplateElement::snapshotCopy).collect(Collectors.toCollection(ArrayList::new)));
            autosave(); refreshRequirementUi(); refreshLayers(); renderCanvas(); analyzeMapping(false);
            if (lblSaveState != null) lblSaveState.setText("Auto-detection review cancelled — previous mapping restored");
        }
        return false;
    }

    private List<TemplateElement> snapshotElements() {
        if (template == null) return List.of();
        return template.getElements().stream().filter(Objects::nonNull).map(TemplateElement::snapshotCopy)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @FXML private void reviewIssues() {
        TemplateMappingValidationService.Result result = TemplateMappingValidationService.evaluate(template);
        if (result.issues().isEmpty()) {
            AppDialogService.success(root, "Template mapping is ready", "All required mappings are complete. You can preview, publish and make this template Default.");
            return;
        }
        String body = result.issues().stream().map(issue ->
                (issue.error() ? "ERROR — " : "WARNING — ") + issue.userMessage()).collect(Collectors.joining("\n\n"));
        if (result.errorCount() == 0) {
            boolean fix = AppDialogService.warningConfirm(root, "Template issues", "PDF Studio",
                    body + "\n\nChoose Fix Next Issue and PDF Studio will take you to the affected mapping/layout control.",
                    "Fix Next Issue", "Close");
            if (fix) focusFirstActionableIssue(result);
        } else {
            AppDialogService.error(root, "Template issues", "PDF Studio", body);
        }
    }

    @FXML private void applySuggestedBinding() {
        TemplateFieldDefinition field = lstInspectorFieldSuggestions == null ? null : lstInspectorFieldSuggestions.getSelectionModel().getSelectedItem();
        if (field == null || previewMode) {
            updateManualMappingState();
            return;
        }
        selectedBindingKey = field.key();
        TemplateMappingValidationService.Result before = TemplateMappingValidationService.evaluate(template);
        PdfTextRegion mappedSourceText = selectedSourceText;
        PdfFormFieldRegion mappedSourceForm = selectedSourceForm;
        PdfImageExtractionService.VectorRegion mappedSourceVector = selectedSourceVector;
        TemplateElement e;
        if(field.image() && mappedSourceVector!=null){
            checkpoint();
            e=mapSourceVectorToImageField(mappedSourceVector,field);
        }else e = editableSelectionFromSource();
        boolean compatible = e != null && (field.image() ? isImageLike(e) : isTextLike(e));
        if (!compatible) {
            if (lblInspectorHint != null) {
                lblInspectorHint.setText(field.image()
                        ? "Click the PDF image or signature/vector artwork you want to replace, then choose an ERP image field such as Authorized Signature or UPI Payment QR."
                        : "Click the PDF text you want to replace, then choose an ERP field. The Map button will enable when both are selected.");
            }
            updateManualMappingState();
            return;
        }
        if(!(field.image() && mappedSourceVector!=null)) checkpoint();
        ManualTemplateMappingService.mapField(e, field);
        if (mappedSourceText != null && !field.image()) remapSourceTextGeometry(e, mappedSourceText, field.key());
        if (mappedSourceForm != null && !field.image()) configureFlowIdentity(e, field.key());
        autosave();
        TemplateMappingValidationService.Result after = TemplateMappingValidationService.evaluate(template);
        populateInspector(e);
        renderCanvas();
        updateManualMappingState();

        String fieldName = field.label();
        long mappedDelta = after.requiredMapped() - before.requiredMapped();
        long errorDelta = before.errorCount() - after.errorCount();
        String change = mappedDelta > 0 || errorDelta > 0
                ? " • readiness updated"
                : " • mapping saved";
        if (lblSaveState != null) {
            lblSaveState.setText("Mapped " + fieldName + " ✓" + change + " • "
                    + after.requiredMapped() + " / " + after.requiredCount() + " required • "
                    + after.errorCount() + " errors");
        }
        if (lblInspectorHint != null) {
            lblInspectorHint.setText("Mapped to " + fieldName + ". The readiness counters above were recalculated immediately.");
        }
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
        cmbGrowthDirection.setOnAction(e -> applyInspectorSilently());
        cmbOverflowPolicy.setOnAction(e -> applyInspectorSilently());
        chkAutoHeight.setOnAction(e -> applyInspectorSilently());
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
            Scene scene = root.getScene();
            if (scene == shortcutScene && shortcutHandler != null) return;
            detachKeyboardShortcuts();
            shortcutScene = scene;
            shortcutHandler = event -> {
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
            };
            scene.addEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
        });
    }

    private void detachKeyboardShortcuts() {
        if (shortcutScene != null && shortcutHandler != null)
            shortcutScene.removeEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
        shortcutScene = null;
        shortcutHandler = null;
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
        int generation = previewLoadGeneration.incrementAndGet();
        currentPreviewData = null;
        if (sample == null) {
            currentPreviewData = DocumentDataService.sample(template.getDocumentType());
            analyzeMapping(allowAutoApply);
            if(dataPreviewMode)renderCanvas();
            return;
        }
        String requestedId = sample.id();
        CompletableFuture.supplyAsync(() -> DocumentDataService.load(template.getDocumentType(), requestedId))
                .thenAccept(data -> Platform.runLater(() -> {
                    DocumentSample selected = cmbSampleDocument.getValue();
                    if (generation != previewLoadGeneration.get() || selected == null || !Objects.equals(selected.id(), requestedId)) return;
                    currentPreviewData = data;
                    analyzeMapping(allowAutoApply);
                    if(dataPreviewMode)renderCanvas();
                }))
                .exceptionally(error -> { Platform.runLater(() -> {
                    if (generation != previewLoadGeneration.get()) return;
                    currentPreviewData = null;
                    AppDialogService.error(root, "Record could not be loaded", "PDF Studio", rootMessage(error));
                }); return null; });
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
            if (allowAutoApply && shouldApplyInitialAutoMap(analysis)) applyAutoMappings(analysis, true, false);
        }));
    }

    private boolean shouldApplyInitialAutoMap(PdfAutoMappingService.Analysis analysis) {
        return template.getElements().isEmpty() && analysis.highConfidence() > 0;
    }

    @FXML private void autoMapNow() {
        if (template == null || previewMode) return;
        if (!template.getDocumentType().isErpConnected()) {
            AppDialogService.info(root, "ERP data is not connected", "Auto Map", "Connect this template to an ERP document type before using Auto Map.");
            return;
        }
        List<TemplateElement> current = new ArrayList<>(template.getElements());
        boolean repeatableRegion = wouldAddItemRepeater(current) || wouldAddChargeRepeater(current);
        if (currentMappingAnalysis.mappings().isEmpty() && !repeatableRegion) {
            analyzeMapping(false);
            AppDialogService.info(root, "Mapping analyzed", "Auto Map", "No mappable printed ERP values or repeating item/charge regions were found yet. Choose a real preview record and run Auto Map again.");
            return;
        }
        List<TemplateElement> reviewRollback = snapshotElements();
        List<PdfMappingReviewSession.DetectedBlock> detectedBlocks = buildDetectedReviewBlocks(currentMappingAnalysis, false);
        // Structural repeaters need to exist for review because their geometry is a real template object.
        // Scalar detections stay detached until Save Mapping.
        applyAutoMappings(currentMappingAnalysis, false, false);
        showMappingReview(null, reviewRollback, detectedBlocks);
    }

    private void applyAutoMappings(PdfAutoMappingService.Analysis analysis, boolean highOnly) {
        applyAutoMappings(analysis, highOnly, true);
    }

    private void applyAutoMappings(PdfAutoMappingService.Analysis analysis, boolean highOnly, boolean includeScalarMappings) {
        List<PdfAutoMappingService.Mapping> mappings = analysis.mappings().stream()
                .filter(m -> !highOnly || m.confidence() >= .90)
                .filter(m -> !hasReplacementFor(sourceKey(m.region())))
                .filter(m -> !isDynamicFinancialMapping(m.fieldKey()))
                .toList();
        List<TemplateElement> list = new ArrayList<>(template.getElements());
        boolean itemRepeaterAdded = wouldAddItemRepeater(list);
        boolean chargeRepeaterAdded = wouldAddChargeRepeater(list);
        if ((mappings.isEmpty() || !includeScalarMappings) && !itemRepeaterAdded && !chargeRepeaterAdded) return;

        checkpoint();
        if (includeScalarMappings) for (PdfAutoMappingService.Mapping mapping : mappings) {
            TemplateElement mapped = addSourceTextReplacement(list, mapping.region(), mapping.expression(), mapping.fieldKey());
            mapped.markAutoDetectedMapping(mapping.region().text(), mapping.fieldKey(), mapping.confidence());
        }
        itemRepeaterAdded = autoCreateItemRepeaterIfDetected(list);
        chargeRepeaterAdded = autoCreateChargeRepeaterIfDetected(list);
        assignDetectedReviewBlocks(list);
        template.setElements(list);
        autosave();
        renderCanvas();
        updateMappingUi(analysis);
        List<String> summary = new ArrayList<>();
        if (includeScalarMappings && !mappings.isEmpty()) summary.add(mappings.size() + " field" + (mappings.size() == 1 ? "" : "s"));
        if (itemRepeaterAdded) summary.add("item repeater");
        if (chargeRepeaterAdded) summary.add("charge repeater");
        lblSaveState.setText("Auto mapped " + String.join(" + ", summary) + " ✓");
    }


    /**
     * Converts reviewed-but-uncommitted source detections into real template elements only after the
     * user chooses Save Mapping. This is deliberately one generic materialization path for every
     * scalar/label-value block; adding a future Export/XYZ block never needs another controller.
     */
    private void materializePendingReviewEntries(List<PdfMappingReviewSession.Entry> pendingEntries) {
        if (pendingEntries == null || pendingEntries.isEmpty()) return;
        List<TemplateElement> list = new ArrayList<>(template.getElements());
        Set<String> materializedSourceKeys = list.stream().filter(Objects::nonNull)
                .map(TemplateElement::getReplacementSourceKey).filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (PdfMappingReviewSession.Entry entry : pendingEntries) {
            if (entry == null || entry.detectedRegion() == null) continue;
            if (entry.fieldKey().isBlank() || "STATIC".equals(entry.state()) || "UNMAPPED".equals(entry.state())) continue;
            if (!entry.detectedSourceKey().isBlank() && materializedSourceKeys.contains(entry.detectedSourceKey())) continue;
            String expression = entry.detectedExpression();
            if (expression == null || expression.isBlank() || !Objects.equals(entry.fieldKey(), entry.autoFieldKey())) {
                expression = "{{" + entry.fieldKey() + "}}";
            }
            TemplateElement mapped = addSourceTextReplacement(list, entry.detectedRegion(), expression, entry.fieldKey());
            mapped.markAutoDetectedMapping(entry.sourceLabel().isBlank() ? entry.sourceValue() : entry.sourceLabel(),
                    entry.autoFieldKey(), entry.confidence());
            mapped.setMappingState(entry.state());
            mapped.setMappingBlockId(entry.blockId());
            mapped.setMappingBlockLabel(entry.blockLabel());
            mapped.setMappingBlockType(reviewBlockType(entry.section()));
            TemplateFieldDefinition definition = pdfField(entry.fieldKey());
            if (definition != null) ManualTemplateMappingService.applyDetectedFlowBlock(mapped, definition, entry.blockId());
            if (!entry.detectedSourceKey().isBlank()) materializedSourceKeys.add(entry.detectedSourceKey());
        }
        assignDetectedReviewBlocks(list);
        template.setElements(list);
    }

    /** Build a detached source model from Auto Map candidates before scalar TemplateElements exist. */
    private List<PdfMappingReviewSession.DetectedBlock> buildDetectedReviewBlocks(PdfAutoMappingService.Analysis analysis, boolean highOnly) {
        if (analysis == null || template == null) return List.of();
        List<PdfTextRegion> allText = extractAllText();
        List<PdfAutoMappingService.SourceCandidate> candidates = PdfAutoMappingService.detectReviewCandidates(
                        template.getDocumentType(), allText, currentPreviewData, analysis.mappings()).stream()
                .filter(Objects::nonNull).filter(c -> c.region() != null)
                .filter(c -> !highOnly || c.confidence() >= .90)
                .filter(c -> !hasReplacementFor(sourceKey(c.region())))
                .filter(c -> !isDynamicFinancialMapping(c.suggestedField()))
                .filter(c -> c.suggestedField().isBlank()
                        || (!c.suggestedField().startsWith("item.") && !c.suggestedField().startsWith("charge.")))
                .toList();
        if (candidates.isEmpty()) return List.of();

        Map<String,List<PdfAutoMappingService.SourceCandidate>> groups = new LinkedHashMap<>();
        List<PdfAutoMappingService.SourceCandidate> loose = new ArrayList<>();
        for (PdfAutoMappingService.SourceCandidate candidate : candidates) {
            String flow = sourceFlowGroup(candidate.region());
            if (flow.isBlank()) loose.add(candidate);
            else groups.computeIfAbsent(flow, k -> new ArrayList<>()).add(candidate);
        }

        // Borderless blocks are grouped by actual proximity. This is structural and has no fixed
        // 10/20-field business cap; a large non-repeating block remains a block.
        loose.sort(Comparator.comparingInt((PdfAutoMappingService.SourceCandidate c) -> c.region().pageIndex())
                .thenComparingDouble(c -> c.region().y()).thenComparingDouble(c -> c.region().x()));
        int cluster = 0;
        List<PdfAutoMappingService.SourceCandidate> current = new ArrayList<>();
        double clusterMinX = 0, clusterMaxX = 0, clusterBottom = 0;
        int clusterPage = -1;
        for (PdfAutoMappingService.SourceCandidate candidate : loose) {
            PdfTextRegion r = candidate.region();
            boolean samePage = clusterPage == r.pageIndex();
            double horizontalGap = current.isEmpty() ? 0 : Math.max(0,
                    Math.max(clusterMinX, r.x()) - Math.min(clusterMaxX, r.x() + r.width()));
            double verticalGap = current.isEmpty() ? 0 : r.y() - clusterBottom;
            boolean nearby = samePage && verticalGap <= Math.max(55, r.height() * 4.0) && horizontalGap <= 110;
            if (!current.isEmpty() && !nearby) {
                groups.put("PROX|" + clusterPage + "|" + cluster++, new ArrayList<>(current));
                current.clear();
            }
            if (current.isEmpty()) {
                clusterPage = r.pageIndex(); clusterMinX = r.x(); clusterMaxX = r.x() + r.width();
                clusterBottom = r.y() + r.height();
            } else {
                clusterMinX = Math.min(clusterMinX, r.x()); clusterMaxX = Math.max(clusterMaxX, r.x() + r.width());
                clusterBottom = Math.max(clusterBottom, r.y() + r.height());
            }
            current.add(candidate);
        }
        if (!current.isEmpty()) groups.put("PROX|" + clusterPage + "|" + cluster, new ArrayList<>(current));

        List<PdfMappingReviewSession.DetectedBlock> blocks = new ArrayList<>();
        int blockSequence = 0;
        for (var grouped : groups.entrySet()) {
            List<PdfAutoMappingService.SourceCandidate> values = grouped.getValue();
            if (values.isEmpty()) continue;
            Set<String> types = values.stream().map(c -> reviewBlockType(c.suggestedField(), c.sourceLabel()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            String type;
            if (types.size() == 1) type = types.iterator().next();
            else if (types.stream().allMatch(v -> v.equals("BILLING") || v.equals("DELIVERY")))
                type = types.contains("DELIVERY") ? "DELIVERY" : "BILLING";
            else if (types.contains("TRANSPORT") && types.stream().allMatch(v -> v.equals("TRANSPORT") || v.equals("HEADER")))
                type = "TRANSPORT";
            else type = "GENERIC";

            List<PdfTextRegion> regions = values.stream().map(PdfAutoMappingService.SourceCandidate::region).toList();
            String heading = "GENERIC".equals(type) ? inferSourceRegionBlockHeading(regions, allText) : reviewBlockLabel(type);
            if (heading == null || heading.isBlank()) heading = "Detected Source Block";
            int page = regions.getFirst().pageIndex();
            double minX = regions.stream().mapToDouble(PdfTextRegion::x).min().orElse(0);
            double minY = regions.stream().mapToDouble(PdfTextRegion::y).min().orElse(0);
            double maxX = regions.stream().mapToDouble(r -> r.x() + r.width()).max().orElse(minX);
            double maxY = regions.stream().mapToDouble(r -> r.y() + r.height()).max().orElse(minY);
            String blockId = "DETECTED|" + page + "|" + blockSequence++ + "|" + normalizeDetectedHeading(heading);
            List<PdfMappingReviewSession.DetectedBlockEntry> entries = new ArrayList<>();
            int entryIndex = 0;
            for (PdfAutoMappingService.SourceCandidate candidate : values) {
                PdfTextRegion r = candidate.region();
                String label = candidate.sourceLabel().isBlank()
                        ? inferDetectedSourceLabel(r, candidate.suggestedField(), allText) : candidate.sourceLabel();
                entries.add(new PdfMappingReviewSession.DetectedBlockEntry(
                        blockId + "|" + entryIndex++, label, candidate.sourceValue(), candidate.suggestedField(),
                        candidate.confidence(), r, candidate.expression(), sourceKey(r)));
            }
            blocks.add(new PdfMappingReviewSession.DetectedBlock(blockId, heading, type, page, minX, minY,
                    Math.max(1, maxX - minX), Math.max(1, maxY - minY), entries));
        }
        return List.copyOf(blocks);
    }

    private String inferDetectedSourceLabel(PdfTextRegion value, String fieldKey, List<PdfTextRegion> allText) {
        if (value == null) return "Detected PDF value";
        List<PdfTextRegion> page = allText == null ? List.of() : allText.stream()
                .filter(Objects::nonNull).filter(r -> r.pageIndex() == value.pageIndex())
                .filter(r -> !sourceKey(r).equals(sourceKey(value))).toList();
        double valueMidY = value.y() + value.height() / 2.0;
        Optional<PdfTextRegion> left = page.stream().filter(r -> r.x() + r.width() <= value.x() + 8)
                .filter(r -> Math.abs((r.y() + r.height() / 2.0) - valueMidY) <= Math.max(9, value.height()))
                .filter(r -> usableDetectedLabel(r.text()))
                .min(Comparator.comparingDouble(r -> value.x() - (r.x() + r.width())));
        if (left.isPresent()) return cleanDetectedLabel(left.get().text());
        Optional<PdfTextRegion> above = page.stream().filter(r -> r.y() + r.height() <= value.y() + 5)
                .filter(r -> value.y() - (r.y() + r.height()) <= 42)
                .filter(r -> rangesOverlap(value.x(), value.x() + value.width(), r.x(), r.x() + r.width(), 8))
                .filter(r -> usableDetectedLabel(r.text()))
                .min(Comparator.comparingDouble(r -> value.y() - (r.y() + r.height())));
        if (above.isPresent()) return cleanDetectedLabel(above.get().text());
        TemplateFieldDefinition def = TemplateFieldCatalog.findPdf(template.getDocumentType(), fieldKey);
        return def == null ? cleanDetectedLabel(value.text()) : def.label();
    }

    private boolean usableDetectedLabel(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() < 2 || t.length() > 80) return false;
        String normalized = PdfAutoMappingService.normalize(t);
        if (normalized.isBlank()) return false;
        return !t.matches("[₹$€£]?\\s*[0-9,./:%+\\-() ]+");
    }

    private String cleanDetectedLabel(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s*[:\\-–—]+\\s*$", "").trim();
    }

    private boolean rangesOverlap(double a1, double a2, double b1, double b2, double tolerance) {
        return Math.min(a2, b2) - Math.max(a1, b1) >= -Math.max(0, tolerance);
    }

    private String normalizeDetectedHeading(String text) {
        String n = text == null ? "" : text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return n.isBlank() ? "BLOCK" : n;
    }

    private String reviewBlockType(PdfMappingReviewSession.Section section) {
        if (section == null) return "HEADER";
        return switch (section) {
            case BILLING -> "BILLING"; case DELIVERY -> "DELIVERY"; case TRANSPORT -> "TRANSPORT";
            case PAYMENT -> "PAYMENT"; case TERMS_FOOTER -> "TERMS_FOOTER"; case DETECTED_BLOCKS -> "GENERIC";
            case ITEMS -> "ITEMS"; case FINANCIAL -> "FINANCIAL"; default -> "HEADER";
        };
    }

    private boolean isDynamicFinancialMapping(String fieldKey) {
        String k = fieldKey == null ? "" : fieldKey.toLowerCase(Locale.ROOT);
        return k.startsWith("totals.") || k.startsWith("tax.") || k.startsWith("charge.");
    }

    /** Detect the literal labels printed inside each dynamic financial block for transparent review. */
    private Map<String,List<PdfMappingReviewSession.FinancialRoleDetection>> detectFinancialReviewRoles() {
        if (template == null) return Map.of();
        List<TemplateElement> summaries = template.getElements().stream().filter(Objects::nonNull)
                .filter(e -> e.getType() == ElementType.BLOCK && "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId())).toList();
        if (summaries.isEmpty()) return Map.of();
        List<PdfTextRegion> all = extractAllText();
        Map<String,List<PdfMappingReviewSession.FinancialRoleDetection>> result = new LinkedHashMap<>();
        for (TemplateElement summary : summaries) {
            List<PdfTextRegion> inside = all.stream().filter(r -> r.pageIndex() == summary.getPageIndex())
                    .filter(r -> r.y() + r.height() >= summary.getY() - 15 && r.y() <= summary.getY() + summary.getHeight() + 15)
                    .filter(r -> rangesOverlap(summary.getX(), summary.getX() + summary.getWidth(), r.x(), r.x() + r.width(), 10))
                    .sorted(Comparator.comparingDouble(PdfTextRegion::y).thenComparingDouble(PdfTextRegion::x)).toList();
            List<PdfMappingReviewSession.FinancialRoleDetection> roles = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (PdfTextRegion region : inside) {
                String source = cleanFinancialSourceLabel(region.text());
                String n = PdfAutoMappingService.normalize(source);
                if (n.isBlank()) continue;
                String label = "", key = "";
                if (n.contains("basic amount") || n.equals("subtotal") || n.contains("sub total")) { label="Basic / Sub Total"; key="totals.basicAmount"; }
                else if (n.startsWith("discount")) { label="Discount"; key="totals.discountAmount"; }
                else if (n.contains("taxable amount") || n.contains("gross before tax")) { label="Taxable Amount"; key="totals.taxableAmount"; }
                else if (n.contains("cgst")) { label="Automatic CGST Row"; key="tax.cgstLabel / tax.primaryAmount"; }
                else if (n.contains("sgst")) { label="Automatic SGST Row"; key="tax.sgstLabel / tax.secondaryAmount"; }
                else if (n.contains("igst")) { label="Automatic IGST Row"; key="tax.igstLabel / tax.primaryAmount"; }
                else if (n.contains("round off") || n.equals("rounding")) { label="Rounding"; key="totals.roundOff"; }
                else if (n.contains("grand total") || n.contains("net total") || n.contains("amount payable")) { label="Rounded Grand Total"; key="totals.roundedGrandTotal"; }
                else if (matchesChargeSource(n)) { label="Dynamic Charge Rows"; key="charge.* (dynamic rows)"; }
                if (!key.isBlank() && seen.add(key.startsWith("charge.*") ? key + "|" + n : key)) {
                    roles.add(new PdfMappingReviewSession.FinancialRoleDetection(source, label, key, .99));
                }
            }
            result.put(summary.getId(), List.copyOf(roles));
        }
        return Map.copyOf(result);
    }

    private boolean matchesChargeSource(String normalized) {
        if (normalized == null || normalized.isBlank()) return false;
        if (currentPreviewData != null) {
            for (TemplateCharge charge : currentPreviewData.charges()) {
                String type = PdfAutoMappingService.normalize(charge.type());
                if (!type.isBlank() && (normalized.contains(type) || type.contains(normalized))) return true;
            }
        }
        return normalized.contains("packing") || normalized.contains("forwarding") || normalized.contains("freight")
                || normalized.contains("additional charge") || normalized.contains("other charge") || normalized.contains("handling charge");
    }

    private String cleanFinancialSourceLabel(String text) {
        if (text == null) return "";
        String cleaned = text.trim().replaceAll("\\s*[:=]\\s*[₹$€£]?\\s*[-+]?\\d[\\d,]*(?:\\.\\d+)?%?\\s*$", "")
                .replaceAll("\\s+[₹$€£]?\\s*[-+]?\\d[\\d,]*(?:\\.\\d+)?%?\\s*$", "").trim();
        return cleanDetectedLabel(cleaned.isBlank() ? text : cleaned);
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
        Optional<PdfAutoMappingService.ItemHeaderLayout> layoutOpt = PdfAutoMappingService.detectItemHeaderLayout(allRegions);
        if (layoutOpt.isEmpty()) return false;
        PdfAutoMappingService.ItemHeaderLayout header = layoutOpt.get();
        List<PdfTextRegion> regions = allRegions.stream().filter(r -> r.pageIndex() == header.pageIndex()).toList();
        double[] page = pageSizeFor(header.pageIndex());
        double localPageHeight = page[1];

        PdfImageExtractionService.VectorRegion grid = sourceGridFor(header);
        double x = grid == null ? Math.max(0, header.x()) : Math.max(0, grid.x());
        double width = grid == null ? Math.max(80, header.width()) : Math.max(80, grid.width());
        double top = grid == null ? header.y() : Math.min(header.y(), grid.y());
        double bottom = grid == null
                ? Math.min(localPageHeight - 8, top + Math.max(160, localPageHeight * .38))
                : Math.min(localPageHeight - 8, grid.y() + grid.height());
        if (grid == null) {
            bottom = PdfSourceTableDetectionService.inferPhysicalTableBottom(header, regions, localPageHeight);
        }

        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, header.pageIndex(), x, top, width, Math.max(70, bottom - top));
        String replacementGroup="item-table-"+UUID.randomUUID();
        table.setReplacementGroupId(replacementGroup);
        table.setSourceReplacementMode("OBJECT");
        table.setUseSourceTableDesign(true);
        table.setSourceStyleCaptured(false);
        table.setFlowRole("ITEM_TABLE");
        table.setGrowthDirection("DOWN");
        table.setOverflowPolicy("PAGINATE");
        table.setHeaderHeight(Math.max(14, (header.y() + header.height()) - top + 2));
        table.setRowHeight(inferSourceRowHeight(grid, header, regions));

        PdfTextRegion headerStyle = nearestHeaderStyle(header, regions);
        if (headerStyle != null) {
            table.setFontSize(Math.max(5, headerStyle.fontSize()));
            table.setFontFamily(fontHint(headerStyle.fontName()));
            table.setTextColor(headerStyle.textColor());
            table.setBold(headerStyle.bold());
        } else table.setFontSize(7.5);

        String bodyColor = sampleBackgroundColor(header.pageIndex(), x + 2, header.y() + header.height() + 2,
                Math.max(8, width - 4), Math.max(8, Math.min(30, bottom - header.y() - header.height() - 4)));
        table.setFillColor(bodyColor);
        table.setFillEnabled(true);
        table.setSourceMaskSafe(isBackgroundUniform(header.pageIndex(), x, header.y() + header.height(), width,
                Math.max(8, Math.min(40, bottom - header.y() - header.height()))));
        if (grid != null) applySourceGridStyle(table, grid);
        else { table.setStrokeEnabled(false); table.setStrokeWidth(0); }

        if (grid != null) PdfSourceTableDetectionService.applySourceTableGeometry(table, grid, header);
        List<TemplateColumnBinding> bindings = sourceColumnBindings(header, table, grid);
        table.setTableColumnBindings(bindings);
        ManualTemplateMappingService.syncLegacyColumns(table);
        list.add(table);
        maskPrintedItemBody(list, table, regions);
        return true;
    }

    /** Detect source item-header geometry and auto-bind only clear ERP meanings; users correct ambiguous headers manually. */
    @FXML private void detectItemHeaders() {
        if (previewMode || template == null) return;
        List<TemplateElement> reviewRollback = snapshotElements();
        try {
            checkpoint();
            List<PdfTextRegion> regions = extractAllText();
            Optional<PdfAutoMappingService.ItemHeaderLayout> header = PdfAutoMappingService.detectItemHeaderLayout(regions);
            if (header.isEmpty()) {
                if (lblInspectorHint != null) lblInspectorHint.setText("No reliable item header row was detected on this page. Create an Item Table manually or select a clearer source PDF.");
                return;
            }

            List<TemplateElement> list = new ArrayList<>(template.getElements());
            TemplateElement table = list.stream()
                    .filter(e -> e.getType() == ElementType.ITEM_TABLE && e.getPageIndex() == pageIndex)
                    .findFirst().orElse(null);
            if (table == null) {
                boolean added = autoCreateItemRepeaterIfDetected(list);
                if (!added) {
                    if (lblInspectorHint != null) lblInspectorHint.setText("The item header was detected but an Item Table could not be created safely. Review the source table area and try again.");
                    return;
                }
                table = list.stream().filter(e -> e.getType() == ElementType.ITEM_TABLE && e.getPageIndex() == pageIndex).reduce((a,b)->b).orElse(null);
            } else {
                PdfImageExtractionService.VectorRegion sourceGrid = sourceGridFor(header.get());
                if (sourceGrid != null) PdfSourceTableDetectionService.applySourceTableGeometry(table, sourceGrid, header.get());
                List<TemplateColumnBinding> detected = sourceColumnBindings(header.get(), table, sourceGrid);
                table.setTableColumnBindings(ManualTemplateMappingService.mergeDetectedColumnBindings(table.getTableColumnBindings(), detected));
                ManualTemplateMappingService.syncLegacyColumns(table);
                if (sourceGrid != null) {
                    applySourceGridStyle(table, sourceGrid);
                    table.setSourceMaskSafe(isBackgroundUniform(table.getPageIndex(), table.getX(),
                            table.getY() + table.getHeaderHeight(), table.getWidth(),
                            Math.max(8, Math.min(40, table.getHeight() - table.getHeaderHeight()))));
                }
            }

            template.setElements(list);
            autosave();
            if (table != null) selectOnlyWithoutRender(table);
            refreshRequirementUi();
            if (table != null) populateInspector(table);
            renderCanvas();
            long missing = table == null ? 0 : table.getTableColumnBindings().stream().filter(b -> b.getFieldKey() == null || b.getFieldKey().isBlank()).count();
            if (lblSaveState != null) lblSaveState.setText(missing == 0
                    ? "Item headers detected and auto-mapped ✓"
                    : "Item headers detected ✓ • " + missing + " column(s) need confirmation");
            if (lblInspectorHint != null) lblInspectorHint.setText(missing == 0
                    ? "All clear item headers were auto-mapped from the printed PDF. Review Mapping shows every detected header and lets you amend the ERP meaning without changing its geometry."
                    : "Clear item headers were auto-mapped. Review Mapping opens the detected header list so ambiguous columns can be corrected without moving the PDF grid.");
            showMappingReview(PdfMappingReviewSession.Section.ITEMS, reviewRollback);
        } catch (Exception error) {
            AppDialogService.error(root,"Item headers could not be detected","PDF Studio",rootMessage(error));
        }
    }

    private List<TemplateColumnBinding> sourceColumnBindings(PdfAutoMappingService.ItemHeaderLayout header, TemplateElement table) {
        PdfImageExtractionService.VectorRegion grid = sourceGridFor(header);
        if (grid != null) PdfSourceTableDetectionService.applySourceTableGeometry(table, grid, header);
        return PdfSourceTableDetectionService.sourceColumnBindings(header, table, grid);
    }

    private List<TemplateColumnBinding> sourceColumnBindings(PdfAutoMappingService.ItemHeaderLayout header, TemplateElement table,
                                                              PdfImageExtractionService.VectorRegion grid) {
        return PdfSourceTableDetectionService.sourceColumnBindings(header, table, grid);
    }

    private String headerAlignment(String fieldKey) {
        String k=fieldKey==null?"":fieldKey;
        if(k.endsWith("quantity")||k.endsWith("unit")||k.contains("Percent")||k.endsWith("serial"))return "CENTER";
        if(k.endsWith("rate")||k.endsWith("total")||k.contains("Amount")||k.endsWith("taxable"))return "RIGHT";
        return "LEFT";
    }

    private PdfTextRegion nearestHeaderStyle(PdfAutoMappingService.ItemHeaderLayout header,List<PdfTextRegion> regions){
        return regions.stream().filter(r->r.pageIndex()==header.pageIndex())
                .filter(r->Math.abs((r.y()+r.height()/2)-(header.y()+header.height()/2))<=Math.max(8,header.height()))
                .min(Comparator.comparingDouble(r->Math.abs(r.x()-header.x()))).orElse(null);
    }

    private PdfImageExtractionService.VectorRegion sourceGridFor(PdfAutoMappingService.ItemHeaderLayout header){
        List<PdfImageExtractionService.VectorRegion> vectors=vectorCache.computeIfAbsent(header.pageIndex(),page->{
            try{return PdfImageExtractionService.extractVectors(sourcePdf,page);}catch(Exception ignored){return List.of();}
        });
        return PdfSourceTableDetectionService.sourceGridFor(header,vectors).orElse(null);
    }

    private double inferSourceRowHeight(PdfImageExtractionService.VectorRegion grid,PdfAutoMappingService.ItemHeaderLayout header,List<PdfTextRegion> regions){
        return PdfSourceTableDetectionService.inferSourceRowHeight(grid,header,regions);
    }

    private void applySourceGridStyle(TemplateElement table,PdfImageExtractionService.VectorRegion grid){
        PdfSourceTableDetectionService.captureSourceStyle(table, grid);
    }

    private void maskPrintedItemBody(List<TemplateElement> list,TemplateElement table,List<PdfTextRegion> regions){
        double headerBottom=table.getY()+table.getHeaderHeight(), bottom=table.getY()+table.getHeight();
        for(PdfTextRegion r:regions){
            if(r.y()+r.height()<=headerBottom+1||r.y()>=bottom-1)continue;
            if(r.x()+r.width()<table.getX()+1||r.x()>table.getX()+table.getWidth()-1)continue;
            String key=sourceKey(r);
            if(hasReplacementFor(key)||list.stream().anyMatch(e->key.equals(e.getReplacementSourceKey())))continue;
            TemplateElement mask=sourceMask(r,key); mask.setSourceMaskSafe(table.isSourceMaskSafe()); mask.setReplacementGroupId(table.getReplacementGroupId()); list.add(mask);
        }
    }

    private boolean autoCreateChargeRepeaterIfDetected(List<TemplateElement> list) {
        if (list.stream().anyMatch(e -> e.getType() == ElementType.CHARGE_TABLE)) return false;
        if (currentPreviewData == null || currentPreviewData.charges().isEmpty()) return false;
        Optional<PdfAutoMappingService.ChargeRegion> detected = PdfAutoMappingService.detectChargeRegion(extractAllText(), currentPreviewData);
        if (detected.isEmpty()) return false;
        PdfAutoMappingService.ChargeRegion region = detected.get();
        TemplateElement table = TemplateElement.of(ElementType.CHARGE_TABLE, region.pageIndex(), region.x(), region.y(), region.width(), Math.max(region.height(), region.rowHeight()));
        String replacementGroup="charge-table-"+UUID.randomUUID();
        table.setReplacementGroupId(replacementGroup);table.setSourceReplacementMode("OBJECT");
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
            TemplateElement mask=sourceMask(source,key);mask.setReplacementGroupId(replacementGroup);list.add(mask);
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
        lblMappingPercent.setText("Source " + pct + "%");
        lblMappingSummary.setText("Source regions: " + mapped + " mapped • " + review + " review • " + unmapped + " untouched");
    }

    // ---------------------------------------------------------------------
    // Canvas loading and rendering
    // ---------------------------------------------------------------------

    private void ensurePageObjects(int page) {
        if (previewMode || loadingPages.contains(page)) return;
        if (textCache.containsKey(page) && formCache.containsKey(page) && imageCache.containsKey(page) && vectorCache.containsKey(page)) return;
        loadingPages.add(page);
        CompletableFuture.runAsync(() -> {
            try {
                textCache.computeIfAbsent(page, p -> {
                    try { return PdfTextExtractionService.extract(sourcePdf, p); } catch (Exception e) { return List.of(); }
                });
                formCache.computeIfAbsent(page, p -> {
                    try { return PdfFormFieldExtractionService.extract(originalPdf, p); } catch (Exception e) { return List.of(); }
                });
                imageCache.computeIfAbsent(page, p -> {
                    try { return PdfImageExtractionService.extract(sourcePdf, p, WorkspaceManager.getTempFolder().resolve("pdf-studio-v3-images")); }
                    catch (Exception e) { return List.of(); }
                });
                vectorCache.computeIfAbsent(page, p -> {
                    try { return PdfImageExtractionService.extractVectors(sourcePdf, p); } catch (Exception e) { return List.of(); }
                });
            } finally {
                Platform.runLater(() -> {
                    loadingPages.remove(page);
                    if (pageIndex == page && !previewMode) { updateSourceCapability(page); renderCanvas(); }
                });
            }
        });
    }

    private PdfSourceCapabilityService.Capability sourceCapability(int page) {
        return PdfSourceCapabilityService.analyze(pageWidth, pageHeight,
                textCache.getOrDefault(page, List.of()), formCache.getOrDefault(page, List.of()),
                imageCache.getOrDefault(page, List.of()), vectorCache.getOrDefault(page, List.of()));
    }

    private void updateSourceCapability(int page) {
        if (lblPageWarning == null || previewMode) return;
        PdfSourceCapabilityService.Capability capability = sourceCapability(page);
        lblPageWarning.setText(capability.userMessage());
        if (lblInspectorHint != null && !capability.exactValueReplacementSupported()
                && selectedIds.isEmpty() && selectedSourceText == null && selectedSourceForm == null)
            lblInspectorHint.setText(capability.userMessage());
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
                // Keep broad PDF vector/grid hit areas behind Studio objects, but place the
                // precise text/form/image hit targets above those objects. This prevents a
                // transparent Section/Block or Item Table from swallowing clicks intended for
                // source values such as CONTACT DETAILS : <name/number>.
                addDetectedVectorTargets();
                for (TemplateElement e : template.getElements()) if (e.getPageIndex() == pageIndex && PdfStyleResolver.effectivelyVisible(template,e)) canvasPane.getChildren().add(elementNode(e));
                addDetectedValueTargets();
            }
            refreshLayers();
            updatePageWarning();
        }));
    }

    private void addDetectedVectorTargets() {
        // Broad vector/table regions are deliberately behind Studio objects. Precise source
        // values are added later by addDetectedValueTargets() so they always win hit-testing.
        for (PdfImageExtractionService.VectorRegion region : vectorCache.getOrDefault(pageIndex,List.of())) {
            if (hasReplacementFor(region.sourceKey())) continue;
            canvasPane.getChildren().add(sourceVectorNode(region));
        }
    }

    private void addDetectedValueTargets() {
        // These are the user's primary Guided Mapping click targets. They must stay above
        // transparent Blocks/Sections/Item Tables; otherwise a broad object can swallow the
        // click and make a visible value appear impossible to map.
        for (PdfImageRegion region : imageCache.getOrDefault(pageIndex,List.of())) {
            if (hasReplacementFor(sourceKey(region))) continue;
            canvasPane.getChildren().add(sourceImageNode(region));
        }
        for (PdfFormFieldRegion region : formCache.getOrDefault(pageIndex,List.of())) {
            if (hasReplacementFor(sourceKey(region))) continue;
            canvasPane.getChildren().add(sourceFormNode(region));
        }
        for (PdfTextRegion region : textCache.getOrDefault(pageIndex,List.of())) {
            if (hasReplacementFor(sourceKey(region))) continue;
            canvasPane.getChildren().add(sourceTextNode(region));
        }
    }

    private Node sourceTextNode(PdfTextRegion region) {
        Optional<PdfTextRegion> valueHit = PdfTextExtractionService.valueHitRegion(region);
        if (valueHit.isEmpty()) {
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

        // A common PDF source pattern stores "GST-IN : 24..." (and similar label/value rows)
        // as one native text object. Keep the full source object for safe replacement, but expose
        // separate click targets so the user can click the actual changing value instead of the label.
        PdfTextRegion value = valueHit.get();
        Pane group = new Pane();
        place(group, region.x(), region.y(), region.width(), region.height());
        group.setPickOnBounds(false);
        double valueOffset = Math.max(0, (value.x()-region.x())*scale);
        double fullW = Math.max(1, region.width()*scale), fullH = Math.max(1, region.height()*scale);

        Region labelHit = new Region();
        labelHit.getStyleClass().add("pdf-v2-source-text-target");
        labelHit.setLayoutX(0); labelHit.setLayoutY(0);
        labelHit.setPrefSize(Math.max(1,valueOffset),fullH);
        labelHit.setMinSize(Math.max(1,valueOffset),fullH); labelHit.setMaxSize(Math.max(1,valueOffset),fullH);
        if (Objects.equals(selectedSourceText, region) && Objects.equals(selectedSourceTextHit, region)) labelHit.getStyleClass().add("pdf-v2-source-selected");
        String label = region.text().substring(0, Math.max(0, region.text().indexOf(':')+1)).trim();
        Tooltip.install(labelHit, new Tooltip("Fixed PDF label • normally leave untouched\n" + label));
        labelHit.setOnMouseClicked(event -> { selectSourceText(region, region); event.consume(); });

        Region valueTarget = new Region();
        valueTarget.getStyleClass().add("pdf-v2-source-text-target");
        valueTarget.setLayoutX(valueOffset); valueTarget.setLayoutY(0);
        valueTarget.setPrefSize(Math.max(1,fullW-valueOffset),fullH);
        valueTarget.setMinSize(Math.max(1,fullW-valueOffset),fullH); valueTarget.setMaxSize(Math.max(1,fullW-valueOffset),fullH);
        if (Objects.equals(selectedSourceText, region) && Objects.equals(selectedSourceTextHit, value)) valueTarget.getStyleClass().add("pdf-v2-source-selected");
        Tooltip.install(valueTarget, new Tooltip("Detected PDF value • click to map\n" + value.text()));
        valueTarget.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                TemplateElement e = materializeSourceText(region);
                if (e != null) { selectOnly(e); Platform.runLater(() -> beginInlineEdit(e)); }
            } else selectSourceText(region, value);
            event.consume();
        });
        group.getChildren().addAll(labelHit,valueTarget);
        return group;
    }

    private Node sourceFormNode(PdfFormFieldRegion region) {
        Region hit = new Region();
        hit.getStyleClass().add("pdf-v2-source-text-target");
        if (Objects.equals(selectedSourceForm, region)) hit.getStyleClass().add("pdf-v2-source-selected");
        place(hit, region.x(), region.y(), region.width(), region.height());
        String sample = region.sampleValue().isBlank() ? "" : "\nSample: " + abbreviate(region.sampleValue(), 80);
        Tooltip.install(hit, new Tooltip("PDF form field • click to map\n" + region.displayName() + sample));
        hit.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                TemplateElement e = materializeSourceForm(region);
                if (e != null) selectOnly(e);
            } else selectSourceForm(region);
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
        if (geometryLocked(e)) wrapper.getStyleClass().add("pdf-v2-object-locked");
        if (e.getX() < 0 || e.getY() < 0 || e.getX()+e.getWidth() > pageWidth || e.getY()+e.getHeight() > pageHeight)
            wrapper.getStyleClass().add("pdf-v2-object-outside");
        place(wrapper, e.getX(), e.getY(), e.getWidth(), e.getHeight());
        wrapper.setRotate(e.getRotation());
        wrapper.setOpacity(PdfStyleResolver.effective(template, e).getOpacity());

        if (selectedIds.contains(e.getId()) && !geometryLocked(e)) addResizeHandles(wrapper, e);
        wrapper.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            if (event.isShiftDown()) toggleSelection(e); else if (!selectedIds.contains(e.getId())) selectOnly(e);
            if (!geometryLocked(e) && event.getClickCount() < 2) startDrag(event);
            event.consume();
        });
        wrapper.setOnMouseDragged(event -> { if (dragging) dragSelection(event); event.consume(); });
        wrapper.setOnMouseReleased(event -> { if (dragging) { dragging=false; autosave(); renderCanvas(); } event.consume(); });
        wrapper.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2 && isTextLike(e) && !geometryLocked(e)) beginInlineEdit(e);
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
            if ("FILL".equals(e.getImageFit()) && view.getImage() != null) {
                double iw=view.getImage().getWidth(), ih=view.getImage().getHeight();
                double target=innerW/innerH, source=iw/ih;
                if (source > target) { double vw=ih*target; view.setViewport(new Rectangle2D((iw-vw)/2.0,0,vw,ih)); }
                else { double vh=iw/target; view.setViewport(new Rectangle2D(0,(ih-vh)/2.0,iw,vh)); }
                view.setPreserveRatio(false);
            }
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
        if (e.getType()==ElementType.ITEM_TABLE) return itemRepeaterDisplayText(e);
        if (e.getType()==ElementType.CHARGE_TABLE) return "CHARGE REPEATER\n" + e.getTableColumns().stream().map(String::toUpperCase).collect(Collectors.joining("  |  "));
        if (e.getType()==ElementType.RECTANGLE) return "";
        if (e.getType()==ElementType.BLOCK && "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId())) return "FINANCIAL SUMMARY\nAutomatic GST / IGST / Charges";
        if (e.getType()==ElementType.BLOCK) return selectedIds.contains(e.getId()) ? "Section / Group" : "";
        if (e.getType()==ElementType.WHITEOUT) return "";
        String text = e.getText();
        if (dataPreviewMode && currentPreviewData != null) text = resolveExpression(text, currentPreviewData);
        return text;
    }


    private String itemRepeaterDisplayText(TemplateElement e) {
        if (e == null) return "ITEM REPEATER";
        // A source-designed table already has its physical headers/grid in the protected PDF.
        // Do not paint engineering/debug text across the user's source rows; Mapping Coach/Inspector
        // remains the place to review physical header -> ERP semantics.
        if (e.isUseSourceTableDesign()) return "";
        List<TemplateColumnBinding> bindings = e.getTableColumnBindings();
        if (bindings != null && !bindings.isEmpty()) {
            String mapped = bindings.stream().map(binding -> {
                String label = binding == null || binding.getSourceLabel() == null || binding.getSourceLabel().isBlank()
                        ? "COLUMN" : binding.getSourceLabel().trim();
                String key = binding == null ? "" : ManualTemplateMappingService.normalizeItemColumn(binding.getFieldKey());
                String semantic = key == null || key.isBlank() ? "?" : switch (key) {
                    case "descriptionWithRemarks" -> "description+remarks";
                    case "discountPercent" -> "discount%";
                    case "gstPercent" -> "gst%";
                    default -> key;
                };
                return label.toUpperCase(Locale.ROOT) + "→" + semantic;
            }).collect(Collectors.joining("  |  "));
            return "ITEM REPEATER • PHYSICAL HEADERS\n" + mapped;
        }
        return "ITEM REPEATER\n" + e.getTableColumns().stream().map(String::toUpperCase).collect(Collectors.joining("  |  "));
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
        if (geometryLocked(e)) return;
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
        populateInspector(e); refreshLayers(); renderCanvas(); updateManualMappingState();
    }

    private void toggleSelection(TemplateElement e) {
        clearSourceSelection();
        if (!selectedIds.remove(e.getId())) selectedIds.add(e.getId());
        refreshSelectionInspector(); renderCanvas();
    }

    private void selectSourceText(PdfTextRegion region) { selectSourceText(region, region); }
    private void selectSourceText(PdfTextRegion region, PdfTextRegion hitRegion) {
        selectedIds.clear(); selectedSourceText=region; selectedSourceTextHit=hitRegion==null?region:hitRegion; selectedSourceForm=null; selectedSourceImage=null; selectedSourceVector=null;
        populateInspector(selectedSourceTextHit); renderCanvas(); updateManualMappingState();
    }
    private void selectSourceForm(PdfFormFieldRegion region) {
        selectedIds.clear(); selectedSourceText=null; selectedSourceForm=region; selectedSourceImage=null; selectedSourceVector=null;
        populateInspector(region); renderCanvas(); updateManualMappingState();
    }
    private void selectSourceImage(PdfImageRegion region) {
        selectedIds.clear(); selectedSourceText=null; selectedSourceForm=null; selectedSourceImage=region; selectedSourceVector=null;
        populateInspector(region); renderCanvas();
    }
    private void selectSourceVector(PdfImageExtractionService.VectorRegion region) {
        selectedIds.clear(); selectedSourceText=null; selectedSourceForm=null; selectedSourceImage=null; selectedSourceVector=region;
        populateInspector(region); renderCanvas();
    }

    private void clearSelection() {
        selectedIds.clear(); clearSourceSelection(); clearInspector(); refreshLayers(); renderCanvas();
    }
    private void clearSourceSelection() { selectedSourceText=null; selectedSourceTextHit=null; selectedSourceForm=null; selectedSourceImage=null; selectedSourceVector=null; }

    private void refreshSelectionInspector() {
        if (selectedIds.size()==1) populateInspector(selectedElement());
        else if (selectedIds.size()>1) populateMixedInspector(selectedIds.size());
        else clearInspector();
    }

    private TemplateElement selectedElement() { return PdfStudioSelectionPolicy.single(selectedIds, template == null ? List.of() : template.getElements()); }
    private List<TemplateElement> selectedElements() { return PdfStudioSelectionPolicy.selected(selectedIds, template == null ? List.of() : template.getElements()); }
    private List<TemplateElement> selectedElementsWithDescendants() { return PdfStudioSelectionPolicy.selectedWithDescendants(selectedIds, template == null ? List.of() : template.getElements()); }
    private Set<String> idsWithDescendants(Collection<String> roots) { return PdfStudioSelectionPolicy.idsWithDescendants(roots, template == null ? List.of() : template.getElements()); }
    private TemplateElement findById(String id) { return PdfStudioSelectionPolicy.find(template == null ? List.of() : template.getElements(), id); }

    private void populateInspector(TemplateElement e) {
        if (e == null) { clearInspector(); return; }
        TemplateElement style = PdfStyleResolver.effective(template, e);
        inspectorSync=true;
        try {
            lblInspectorType.setText(displayName(e));
            String guidance;
            if (e.getType() == ElementType.ITEM_TABLE) {
                guidance = "Item Table selected. Map each detected physical header once with an item.* ERP field. Use Validate Mapping to find any unmapped header; use Advanced Design only if Validate reports row-capacity or layout problems.";
            } else if (e.getReplacementGroupId() != null && e.getReplacementGroupId().equals("DYNAMIC_FINANCIAL_SUMMARY")) {
                guidance = "Financial Summary selected. ERP controls the dynamic Discount / Charges / GST / IGST / Round Off rows automatically. Do not map those calculation rows individually.";
            } else if (e.getFieldKey() != null && !e.getFieldKey().isBlank()) {
                guidance = "Mapped to " + e.getFieldKey() + ". Preview with a real record to confirm the value. Source position/style is preserved; open Advanced Design only if Preview shows a real correction is needed.";
            } else {
                guidance = "Studio object selected. If this is intended to show ERP data, choose the matching ERP field below and Map. Otherwise leave protected PDF artwork untouched.";
            }
            lblInspectorHint.setText(guidance);
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
            cmbFlowAnchorMode.setValue(e.getFlowAnchorMode()); txtFlowAnchorId.setText(e.getFlowAnchorId()); txtFlowGap.setText(fmt(e.getFlowGap()));
            cmbGrowthDirection.setValue(e.getGrowthDirection()); cmbOverflowPolicy.setValue(e.getOverflowPolicy()); txtFlowGroupId.setText(e.getFlowGroupId()); txtFlowRole.setText(e.getFlowRole()); chkAutoHeight.setSelected(e.isAutoHeight());
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
            lblInspectorType.setText("Detected PDF Text"); lblInspectorHint.setText(mappingCoachHint(r));
            txtContent.setText(r.text()); txtX.setText(fmt(r.x())); txtY.setText(fmt(r.y())); txtWidth.setText(fmt(r.width())); txtHeight.setText(fmt(r.height()));
            txtFontSize.setText(fmt(r.fontSize())); cmbFontFamily.setValue(fontHint(r.fontName())); chkBold.setSelected(r.bold()); chkItalic.setSelected(r.italic()); colorText.setValue(color(r.textColor(),Color.web("#172033"))); txtRotation.setText(fmt(r.rotation())); txtOpacity.setText("100");
            txtLineSpacing.setText("1.22"); txtStrokeWidth.setText("0"); txtRadius.setText("0"); txtPadTop.setText("0"); txtPadRight.setText("0"); txtPadBottom.setText("0"); txtPadLeft.setText("0");
            chkFillEnabled.setSelected(false); chkStrokeEnabled.setSelected(false); chkInheritParent.setSelected(false); chkLocked.setSelected(false); chkVisible.setSelected(true); cmbPageRule.setValue("AUTO");
            textSection.setVisible(true);textSection.setManaged(true);imageSection.setVisible(false);imageSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
            lblSelection.setText("Detected text: " + abbreviate(r.text(),42));
        } finally { inspectorSync=false; }
    }

    private String mappingCoachHint(PdfTextRegion r) {
        String raw = r == null || r.text() == null ? "" : r.text().trim();
        String n = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        String fullRaw = selectedSourceText != null && selectedSourceText.text() != null ? selectedSourceText.text().trim() : raw;
        String full = fullRaw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        if (full.contains("contact details"))
            return "Contact value selected: “" + abbreviate(raw, 42) + "”. Recommended ERP field: Transport Contact (transport.contact). The fixed CONTACT DETAILS : label stays untouched; map only this changing value.";
        if (full.startsWith("transporter") || full.contains("transporter :"))
            return "Transporter value selected. Recommended ERP field: Transporter Name (transport.name). The printed TRANSPORTER : label stays untouched.";
        if (full.contains("vehicle") && full.contains(":"))
            return "Vehicle value selected. Recommended ERP field: Vehicle Number (transport.vehicleNumber). Keep the printed VEHICLE : label untouched.";

        if (template != null && currentPreviewData != null && r != null) {
            try {
                PdfAutoMappingService.Analysis analysis = PdfAutoMappingService.analyze(template.getDocumentType(), List.of(r), currentPreviewData);
                Optional<PdfAutoMappingService.Mapping> exact = analysis.mappings().stream()
                        .filter(m -> m.confidence() >= .90 && m.fieldKey() != null && !m.fieldKey().isBlank())
                        .findFirst();
                if (exact.isPresent()) {
                    String key = exact.get().fieldKey();
                    return "Selected PDF value: “" + abbreviate(raw, 48) + "”. Suggested ERP field: "
                            + friendlyFieldName(key) + " (" + key + "). Next: select that ERP field below, then click Map. PDF Studio will preserve the source position/style.";
                }
            } catch (Exception ignored) { }
        }

        if (n.matches(".*(gstin|gst-in|gst no|gst number).*"))
            return "GST value selected. Choose Billing GSTIN or Delivery GSTIN for the section you clicked, then click Map. Keep the printed GST-IN label as source artwork.";
        String itemHeaderField = suggestedItemFieldForHeader(n);
        if (!itemHeaderField.isBlank())
            return "Item header selected: “" + abbreviate(raw, 34) + "”. Recommended ERP field: " + itemHeaderField
                    + ". Next: use Detect Item Headers if needed, select this ERP field below, then Map it to this physical header. Map each column once - never map individual item rows.";
        if (n.contains("terms") || n.contains("condition"))
            return "Terms text selected. Map the first real terms line to company.terms; PDF Studio will treat connected lines as one wrapped Auto Height block. Do not map every line separately.";
        if (n.length() >= 35 || n.contains(","))
            return "This may be a multi-line value such as an address. If it is Billing/Delivery Address, choose party.billingAddress or party.deliveryAddress and map the first real address line only; connected lines are captured automatically.";
        if (n.contains("basic amount") || n.contains("taxable amount") || n.contains("grand total") || n.contains("cgst") || n.contains("sgst") || n.contains("igst") || n.contains("round off"))
            return "Calculation text selected. Do not map calculation rows one by one. Select the complete calculation grid/block and use Financial Summary so GST/IGST/charges remain dynamic.";
        return "Selected PDF value: “" + abbreviate(raw, 48) + "”. Next: search for the ERP field that represents this value, select it, then click Map. Drop/map onto existing source text to preserve alignment; use + Text only for a genuinely blank area.";
    }

    private void populateInspector(PdfFormFieldRegion r) {
        clearInspectorFieldsOnly(); inspectorSync=true;
        try {
            lblInspectorType.setText("Detected PDF Form Field");
            lblInspectorHint.setText("Fillable PDF field selected. Choose the ERP field below and click Map. The source widget is already cleared safely in the Studio working copy.");
            txtContent.setText(r.sampleValue()); txtX.setText(fmt(r.x())); txtY.setText(fmt(r.y())); txtWidth.setText(fmt(r.width())); txtHeight.setText(fmt(r.height()));
            txtFontSize.setText(fmt(Math.max(7, Math.min(11, r.height()*.55)))); cmbFontFamily.setValue("HELVETICA"); chkBold.setSelected(false); chkItalic.setSelected(false); colorText.setValue(Color.web("#172033")); txtRotation.setText("0"); txtOpacity.setText("100");
            txtLineSpacing.setText("1.22"); txtStrokeWidth.setText("0"); txtRadius.setText("0"); txtPadTop.setText("0"); txtPadRight.setText("0"); txtPadBottom.setText("0"); txtPadLeft.setText("0");
            chkFillEnabled.setSelected(false); chkStrokeEnabled.setSelected(false); chkInheritParent.setSelected(false); chkLocked.setSelected(false); chkVisible.setSelected(true); cmbPageRule.setValue("AUTO");
            textSection.setVisible(true);textSection.setManaged(true);imageSection.setVisible(false);imageSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
            lblSelection.setText("Form field: " + abbreviate(r.displayName(),42));
            if (txtInspectorFieldSearch != null) txtInspectorFieldSearch.setText(r.displayName().replace('_',' '));
            refreshInspectorSuggestions(r.displayName().replace('_',' '));
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
            boolean signatureLike=likelySignatureVector(r);
            lblInspectorType.setText("Detected " + r.kind());
            lblInspectorHint.setText(signatureLike
                    ? "This looks like signature/vector artwork. To make the signature dynamic, choose Company → Authorized Signature (company.signature) below and click Map. PDF Studio will replace this vector artwork with the configured ERP signature image."
                    : "This is PDF artwork / grid geometry, not a normal ERP value. Usually leave it untouched. If this is the calculation grid, select the complete calculation area and use Financial Summary; otherwise click the actual printed value you want to map.");
            txtX.setText(fmt(r.x()));txtY.setText(fmt(r.y()));txtWidth.setText(fmt(r.width()));txtHeight.setText(fmt(r.height()));txtRotation.setText("0");txtOpacity.setText("100");
            var primitive = r.primitives().isEmpty() ? null : r.primitives().getFirst();
            if (primitive != null) { colorFill.setValue(color(primitive.fillColor(),Color.WHITE)); colorStroke.setValue(color(primitive.strokeColor(),Color.web("#94A3B8"))); txtStrokeWidth.setText(fmt(primitive.strokeWidth())); chkFillEnabled.setSelected(primitive.filled()); chkStrokeEnabled.setSelected(primitive.stroked()); }
            else { txtStrokeWidth.setText("1"); chkFillEnabled.setSelected(false); chkStrokeEnabled.setSelected(true); }
            txtRadius.setText("0");txtPadTop.setText("0");txtPadRight.setText("0");txtPadBottom.setText("0");txtPadLeft.setText("0");chkInheritParent.setSelected(false);chkLocked.setSelected(false);chkVisible.setSelected(true);cmbPageRule.setValue("AUTO");
            textSection.setVisible(false);textSection.setManaged(false);imageSection.setVisible(false);imageSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
            lblSelection.setText(r.kind());
            if(signatureLike && txtInspectorFieldSearch!=null){
                txtInspectorFieldSearch.setText("signature");
                refreshInspectorSuggestions("signature");
                if(lstInspectorFieldSuggestions!=null) lstInspectorFieldSuggestions.getItems().stream()
                        .filter(f->"company.signature".equals(f.key())).findFirst()
                        .ifPresent(f->lstInspectorFieldSuggestions.getSelectionModel().select(f));
            }
        } finally { inspectorSync=false; }
        updateManualMappingState();
    }

    private boolean likelySignatureVector(PdfImageExtractionService.VectorRegion r){
        if(r==null||r.pageIndex()<0)return false;
        double padX=Math.max(24,r.width()*.55), padY=Math.max(18,r.height()*1.8);
        return textCache.getOrDefault(r.pageIndex(),List.of()).stream().anyMatch(t->{
            String n=PdfAutoMappingService.normalize(t.text());
            if(!(n.contains("authorized signatory")||n.contains("authorised signatory")||n.startsWith("for ")))return false;
            double cx=t.x()+t.width()/2.0,cy=t.y()+t.height()/2.0;
            return cx>=r.x()-padX&&cx<=r.x()+r.width()+padX&&cy>=r.y()-padY&&cy<=r.y()+r.height()+padY;
        });
    }

    private void populateMixedInspector(int count) {
        clearInspectorFieldsOnly(); inspectorSync=true;
        try {
            lblInspectorType.setText(count + " objects selected"); lblInspectorHint.setText("Alignment, distribution, duplicate, delete, layer order and format actions apply to the whole selection. Blank inspector values mean Mixed.");
            lblSelection.setText(count + " objects selected");
        } finally { inspectorSync=false; }
    }

    private void clearInspector() { clearInspectorFieldsOnly(); lblInspectorType.setText("Guided Mapping"); lblInspectorHint.setText("Step 1: click the existing printed PDF value you want to replace. Step 2: choose the matching ERP field below. Step 3: click Map. For item columns use Detect Item Headers; for calculations use Financial Summary."); lblSelection.setText("Nothing selected"); updateManualMappingState(); }
    private void clearInspectorFieldsOnly() {
        inspectorSync=true;
        try {
            for (TextField f : List.of(txtFontSize,txtLineSpacing,txtX,txtY,txtWidth,txtHeight,txtRotation,txtOpacity,txtStrokeWidth,txtRadius,txtPadTop,txtPadRight,txtPadBottom,txtPadLeft,txtTableColumns,txtRowHeight,txtHeaderHeight,txtFlowAnchorId,txtFlowGap,txtFlowGroupId,txtFlowRole)) f.clear();
            txtContent.clear(); selectedBindingKey=""; if(txtInspectorFieldSearch!=null)txtInspectorFieldSearch.clear(); if(lstInspectorFieldSuggestions!=null)lstInspectorFieldSuggestions.getSelectionModel().clearSelection(); chkBold.setSelected(false);chkItalic.setSelected(false);chkInheritParent.setSelected(false);chkFillEnabled.setSelected(false);chkStrokeEnabled.setSelected(false);chkLocked.setSelected(false);chkVisible.setSelected(true);
            chkPaddingLinked.setSelected(true);chkPreserveRatio.setSelected(true);chkUseSourceTableDesign.setSelected(false);
            colorText.setValue(Color.web("#172033"));colorFill.setValue(Color.WHITE);colorStroke.setValue(Color.web("#94A3B8"));
            cmbFontFamily.setValue("HELVETICA");cmbTextFit.setValue("SHRINK");cmbTextAlignment.setValue("LEFT");cmbImageFit.setValue("FIT");cmbPageRule.setValue("AUTO");
            cmbFlowAnchorMode.setValue("ABSOLUTE");cmbGrowthDirection.setValue("FIXED");cmbOverflowPolicy.setValue("ERROR");chkAutoHeight.setSelected(false);
            textSection.setVisible(false);textSection.setManaged(false);imageSection.setVisible(false);imageSection.setManaged(false);repeaterSection.setVisible(false);repeaterSection.setManaged(false);
        } finally { inspectorSync=false; }
    }

    private void selectBinding(String key) {
        selectedBindingKey = key == null ? "" : key.trim();
        if (txtInspectorFieldSearch == null) return;
        inspectorSync = true;
        try {
            TemplateFieldDefinition field = selectedBindingKey.isBlank() ? null : TemplateFieldCatalog.findPdf(template.getDocumentType(), selectedBindingKey);
            txtInspectorFieldSearch.setText(field == null ? "" : field.label());
        } finally { inspectorSync = false; }
        refreshInspectorSuggestions(txtInspectorFieldSearch.getText());
    }

    @FXML private void showErpFields() {
        if (leftTabs != null && leftTabs.getTabs().size() > 1) leftTabs.getSelectionModel().select(1);
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
            e.setFieldKey(selectedBindingKey);
            e.setFontFamily(cmbFontFamily.getValue()); e.setTextFit(cmbTextFit.getValue()); e.setTextAlignment(cmbTextAlignment.getValue());
            e.setFontSize(parse(txtFontSize,e.getFontSize())); e.setLineSpacing(parse(txtLineSpacing,e.getLineSpacing())); e.setBold(chkBold.isSelected()); e.setItalic(chkItalic.isSelected());
            e.setTextColor(hex(colorText.getValue())); e.setFillColor(hex(colorFill.getValue())); e.setStrokeColor(hex(colorStroke.getValue()));
            double oldX=e.getX(), oldY=e.getY();
            if (!geometryLocked(e)) {
                e.setX(parse(txtX,e.getX())); e.setY(parse(txtY,e.getY())); e.setWidth(parse(txtWidth,e.getWidth())); e.setHeight(parse(txtHeight,e.getHeight())); e.setRotation(parse(txtRotation,e.getRotation()));
            }
            e.setOpacity(parse(txtOpacity,e.getOpacity()*100)/100.0);
            if (e.getType()==ElementType.BLOCK && !geometryLocked(e) && (Math.abs(e.getX()-oldX)>.0001 || Math.abs(e.getY()-oldY)>.0001))
                translateDescendants(e, e.getX()-oldX, e.getY()-oldY);
            e.setStrokeWidth(parse(txtStrokeWidth,e.getStrokeWidth())); e.setBorderRadius(parse(txtRadius,e.getBorderRadius()));
            double top=parse(txtPadTop,e.getPaddingTop());
            if (chkPaddingLinked.isSelected()) { e.setPaddingTop(top);e.setPaddingRight(top);e.setPaddingBottom(top);e.setPaddingLeft(top); }
            else { e.setPaddingTop(top);e.setPaddingRight(parse(txtPadRight,e.getPaddingRight()));e.setPaddingBottom(parse(txtPadBottom,e.getPaddingBottom()));e.setPaddingLeft(parse(txtPadLeft,e.getPaddingLeft())); }
            e.setFillEnabled(chkFillEnabled.isSelected());e.setStrokeEnabled(chkStrokeEnabled.isSelected());
            e.setImageFit(cmbImageFit.getValue());e.setPreserveAspectRatio(chkPreserveRatio.isSelected());
            if (isRepeater(e)) {
                List<String> requested=Arrays.stream(txtTableColumns.getText().split(",")).map(String::trim).filter(s->!s.isBlank()).toList();
                if(e.getType()==ElementType.ITEM_TABLE&&!e.getTableColumnBindings().isEmpty()){
                    if(requested.size()!=e.getTableColumnBindings().size())throw new IllegalArgumentException("Source-aware Item Table columns are tied to the detected PDF header cells. Map fields by dropping them onto the physical headers instead of changing the column count.");
                    List<TemplateColumnBinding> bindings=new ArrayList<>(e.getTableColumnBindings());
                    for(int i=0;i<bindings.size();i++){TemplateColumnBinding b=bindings.get(i).copy();b.setFieldKey("item."+ManualTemplateMappingService.normalizeItemColumn(requested.get(i)));bindings.set(i,b);}
                    e.setTableColumnBindings(bindings);ManualTemplateMappingService.syncLegacyColumns(e);
                }else e.setTableColumns(requested);
                e.setRowHeight(parse(txtRowHeight,e.getRowHeight()));e.setHeaderHeight(parse(txtHeaderHeight,e.getHeaderHeight()));e.setUseSourceTableDesign(chkUseSourceTableDesign.isSelected());
            }
            e.setLocked(chkLocked.isSelected());e.setVisible(chkVisible.isSelected());e.setPageRule(cmbPageRule.getValue());
            e.setFlowAnchorMode(cmbFlowAnchorMode.getValue());e.setFlowAnchorId(txtFlowAnchorId.getText());e.setFlowGap(parse(txtFlowGap,e.getFlowGap()));
            e.setGrowthDirection(cmbGrowthDirection.getValue());e.setOverflowPolicy(cmbOverflowPolicy.getValue());e.setFlowGroupId(txtFlowGroupId.getText());e.setFlowRole(txtFlowRole.getText());e.setAutoHeight(chkAutoHeight.isSelected());
            if (e.isInheritParentStyle()) PdfStyleResolver.updateOverrides(template, e);
            autosave(); populateInspector(e); renderCanvas();
        } catch (Exception error) {
            if (showError) AppDialogService.error(root,"Properties could not be applied","PDF Studio",rootMessage(error));
        }
    }

    private TemplateElement editableSelectionFromSource() {
        TemplateElement e=selectedElement(); if (e!=null) return e;
        if (selectedSourceText!=null) { e=materializeSourceText(selectedSourceText); if(e!=null)selectOnlyWithoutRender(e); return e; }
        if (selectedSourceForm!=null) { e=materializeSourceForm(selectedSourceForm); if(e!=null)selectOnlyWithoutRender(e); return e; }
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
        List<PdfTextRegion> replacementRegions=replacementTextRegions(region,fieldKey);
        PdfTextRegion valueRegion=combinedReplacementRegion(region,replacementRegions,fieldKey);
        boolean safe=replacementRegions.stream().map(r->valueOnlyRegion(r,fieldKey))
                .allMatch(r->isBackgroundUniform(r.pageIndex(),r.x(),r.y(),r.width(),r.height()));
        for(PdfTextRegion source:replacementRegions){
            PdfTextRegion maskRegion=valueOnlyRegion(source,fieldKey);
            TemplateElement mask=sourceMask(maskRegion,sourceKey(source));mask.setReplacementGroupId(group);mask.setSourceMaskSafe(safe);list.add(mask);
        }
        TemplateElement text=TemplateElement.of(ElementType.TEXT,valueRegion.pageIndex(),valueRegion.x(),valueRegion.y(),valueRegion.width(),Math.max(valueRegion.height(),valueRegion.fontSize()*1.25));
        text.setText(sourceAwareExpression(region,expression,fieldKey));text.setFieldKey(fieldKey);text.setFontSize(valueRegion.fontSize());text.setFontFamily(fontHint(valueRegion.fontName()));text.setBold(valueRegion.bold());text.setItalic(valueRegion.italic());text.setTextColor(valueRegion.textColor());text.setRotation(valueRegion.rotation());text.setFillEnabled(false);text.setStrokeEnabled(false);text.setTextFit("SHRINK");text.setReplacementGroupId(group);text.setReplacementSourceKey(key);
        text.setSourceStyleCaptured(true);text.setSourceMaskSafe(safe);text.setSourceReplacementMode("OBJECT");
        ManualTemplateMappingService.configureMultilineMapping(text,fieldKey);configureFlowIdentity(text,fieldKey);list.add(text);return text;
    }

    /** Rebuild source masks after the user chooses the field so labels/borders stay protected. */
    private void remapSourceTextGeometry(TemplateElement element,PdfTextRegion source,String fieldKey){
        if(element==null||source==null||fieldKey==null||element.getReplacementGroupId()==null)return;
        String group=element.getReplacementGroupId();
        List<TemplateElement> list=new ArrayList<>(template.getElements());
        list.removeIf(candidate->candidate.getType()==ElementType.WHITEOUT&&group.equals(candidate.getReplacementGroupId()));
        List<PdfTextRegion> regions=replacementTextRegions(source,fieldKey);
        int insert=Math.max(0,list.indexOf(element));
        for(PdfTextRegion printed:regions){
            PdfTextRegion maskRegion=valueOnlyRegion(printed,fieldKey);
            TemplateElement mask=sourceMask(maskRegion,sourceKey(printed));mask.setReplacementGroupId(group);
            mask.setSourceMaskSafe(isBackgroundUniform(maskRegion.pageIndex(),maskRegion.x(),maskRegion.y(),maskRegion.width(),maskRegion.height()));list.add(insert++,mask);
        }
        PdfTextRegion box=combinedReplacementRegion(source,regions,fieldKey);
        element.setX(box.x());element.setY(box.y());element.setWidth(box.width());element.setHeight(Math.max(box.height(),box.fontSize()*1.25));
        element.setReplacementSourceKey(sourceKey(source));
        String liveExpression=sourceAwareExpression(source,"{{"+fieldKey+"}}",fieldKey);
        if(!liveExpression.equals("{{"+fieldKey+"}}")){
            element.setType(ElementType.TEXT);
            element.setText(liveExpression);
        }
        element.setSourceStyleCaptured(true);element.setSourceReplacementMode("OBJECT");
        element.setSourceMaskSafe(regions.stream().map(r->valueOnlyRegion(r,fieldKey)).allMatch(r->isBackgroundUniform(r.pageIndex(),r.x(),r.y(),r.width(),r.height())));
        ManualTemplateMappingService.configureMultilineMapping(element,fieldKey);configureFlowIdentity(element,fieldKey);
        template.setElements(list);
    }

    private List<PdfTextRegion> replacementTextRegions(PdfTextRegion selected,String fieldKey){
        if(selected==null)return List.of();
        TemplateFieldDefinition definition=pdfField(fieldKey);
        if(definition==null||!definition.multiline())return List.of(selected);
        List<PdfTextRegion> pageRegions=textCache.computeIfAbsent(selected.pageIndex(),page->{
            try{return PdfTextExtractionService.extract(sourcePdf,page);}catch(Exception ignored){return List.of();}
        });
        Optional<PdfImageExtractionService.VectorRegion> container=sourceFlowContainer(selected);
        java.util.function.Predicate<PdfTextRegion> sameBlock=r->{
            if(r==null||r.pageIndex()!=selected.pageIndex())return false;
            if(container.isPresent()){
                var c=container.get();double cx=r.x()+r.width()/2.0,cy=r.y()+r.height()/2.0;
                return cx>=c.x()-2&&cx<=c.x()+c.width()+2&&cy>=c.y()-2&&cy<=c.y()+c.height()+2;
            }
            double left=Math.max(0,selected.x()-16),right=selected.x()+Math.max(selected.width(),180);
            double overlap=Math.min(right,r.x()+r.width())-Math.max(left,r.x());
            return overlap>Math.min(24,Math.max(8,Math.min(selected.width(),r.width())*.22));
        };
        double maxScan=container.map(c->Math.min(c.y()+c.height(),selected.y()+Math.max(220,selected.height()*14.0)))
                .orElse(selected.y()+Math.max(180.0,selected.height()*12.0));

        List<PdfAutoMappingService.Mapping> known=currentMappingAnalysis==null?List.of():currentMappingAnalysis.mappings();
        List<PdfAutoMappingService.SourceCandidate> sourceCandidates=PdfAutoMappingService.detectReviewCandidates(
                template.getDocumentType(),pageRegions,currentPreviewData,known);
        double cutoff=sourceCandidates.stream().filter(c->c.region()!=null).filter(c->sameBlock.test(c.region()))
                .filter(c->c.region().y()>selected.y()+Math.max(2,selected.height()*.45))
                .filter(c->!Objects.equals(c.suggestedField(),fieldKey))
                .mapToDouble(c->c.region().y()).min().orElse(maxScan);
        final double scanCutoff=Math.min(maxScan,cutoff);

        Set<String> knownBoundaryValues=sourceCandidates.stream().filter(c->c.region()!=null)
                .filter(c->c.region().y()>=scanCutoff-.25).map(c->sourceKey(c.region())).collect(Collectors.toSet());
        List<PdfTextRegion> candidates=pageRegions.stream().filter(sameBlock)
                .filter(r->r.y()>=selected.y()-0.5&&r.y()+r.height()<=scanCutoff+0.25)
                .filter(r->!knownBoundaryValues.contains(sourceKey(r))||sourceKey(r).equals(sourceKey(selected)))
                .sorted(Comparator.comparingDouble(PdfTextRegion::y).thenComparingDouble(PdfTextRegion::x)).toList();
        List<PdfTextRegion> out=new ArrayList<>();double previousBottom=selected.y();
        for(PdfTextRegion r:candidates){
            double gap=r.y()-previousBottom;
            if(!out.isEmpty()&&gap>Math.max(24,selected.height()*2.8))break;
            out.add(r);previousBottom=Math.max(previousBottom,r.y()+r.height());
        }
        if(out.isEmpty())return List.of(selected);
        if(out.stream().noneMatch(r->sourceKey(r).equals(sourceKey(selected))))out.add(0,selected);
        return out;
    }

    private TemplateFieldDefinition pdfField(String fieldKey){
        if(fieldKey==null||fieldKey.isBlank())return null;
        return template==null?TemplateFieldCatalog.find(fieldKey):TemplateFieldCatalog.findPdf(template.getDocumentType(),fieldKey);
    }

    private boolean isMultilineFieldKey(String key){
        TemplateFieldDefinition field=pdfField(key);return field!=null&&field.multiline();
    }

    private boolean isPartyAddressField(String key){
        TemplateFieldDefinition field=pdfField(key);return field!=null&&"PARTY_ADDRESS".equals(field.blockRole());
    }

    /**
     * Annotate mapped values with logical source-block metadata for the centralized Review Auto Mapping workspace.
     * The detector remains generic: it groups values that share the same imported-PDF container/flow geometry,
     * prefers a semantic ERP block when the fields agree, and falls back to a generic Detected Block for mixed
     * values (for example a future XYZ/Export/Dispatch section). No template name or coordinate is hard-coded.
     */
    private void assignDetectedReviewBlocks(List<TemplateElement> elements){
        PdfStudioFlowBlockDetector.normalize(template, elements, sourcePdf);
    }

    private String reviewBlockType(String fieldKey,String sourceLabel){
        return TemplateFieldCatalog.reviewBlockType(template==null?null:template.getDocumentType(),fieldKey,sourceLabel);
    }

    private String reviewBlockLabel(String type){
        return switch(type==null?"":type){
            case "BILLING"->"Billing / Bill To"; case "DELIVERY"->"Delivery / Ship To";
            case "TRANSPORT"->"Transport Details"; case "PAYMENT"->"Bank / Payment";
            case "TERMS_FOOTER"->"Terms & Conditions"; case "GENERIC"->"Detected Source Block";
            default->"Document Header";
        };
    }

    private String inferSourceBlockHeading(List<TemplateElement> values,List<PdfTextRegion> source){
        if(values==null||values.isEmpty()||source==null||source.isEmpty())return "";
        int page=values.getFirst().getPageIndex();
        double minX=values.stream().mapToDouble(TemplateElement::getX).min().orElse(0);
        double maxX=values.stream().mapToDouble(e->e.getX()+e.getWidth()).max().orElse(minX+1);
        double minY=values.stream().mapToDouble(TemplateElement::getY).min().orElse(0);
        return source.stream().filter(r->r.pageIndex()==page)
                .filter(r->r.y()+r.height()<=minY+8&&r.y()>=Math.max(0,minY-55))
                .filter(r->{double overlap=Math.min(maxX,r.x()+r.width())-Math.max(minX,r.x());return overlap>Math.min(30,Math.max(8,r.width()*.25));})
                .filter(r->{String t=r.text()==null?"":r.text().trim();return t.length()>=3&&t.length()<=70;})
                .sorted(Comparator.comparingDouble((PdfTextRegion r)->Math.abs(minY-(r.y()+r.height())))
                        .thenComparing((PdfTextRegion r)->-r.fontSize()))
                .map(PdfTextRegion::text).map(String::trim)
                .filter(t->!t.isBlank()).findFirst().orElse("");
    }


    private String inferSourceRegionBlockHeading(List<PdfTextRegion> values,List<PdfTextRegion> source){
        if(values==null||values.isEmpty()||source==null||source.isEmpty())return "";
        int page=values.getFirst().pageIndex();
        double minX=values.stream().mapToDouble(PdfTextRegion::x).min().orElse(0);
        double maxX=values.stream().mapToDouble(r->r.x()+r.width()).max().orElse(minX+1);
        double minY=values.stream().mapToDouble(PdfTextRegion::y).min().orElse(0);
        Set<String> valueKeys=values.stream().map(this::sourceKey).collect(Collectors.toSet());
        return source.stream().filter(r->r.pageIndex()==page).filter(r->!valueKeys.contains(sourceKey(r)))
                .filter(r->r.y()+r.height()<=minY+8&&r.y()>=Math.max(0,minY-65))
                .filter(r->{double overlap=Math.min(maxX,r.x()+r.width())-Math.max(minX,r.x());return overlap>Math.min(30,Math.max(8,r.width()*.20));})
                .filter(r->{String t=r.text()==null?"":r.text().trim();return t.length()>=3&&t.length()<=70&&usableDetectedLabel(t);})
                .sorted(Comparator.comparingDouble((PdfTextRegion r)->Math.abs(minY-(r.y()+r.height())))
                        .thenComparing((PdfTextRegion r)->-r.fontSize()))
                .map(PdfTextRegion::text).map(this::cleanDetectedLabel).filter(t->!t.isBlank()).findFirst().orElse("");
    }

    private Optional<PdfImageExtractionService.VectorRegion> sourceFlowContainer(PdfTextRegion region){
        if(region==null)return Optional.empty();
        List<PdfImageExtractionService.VectorRegion> vectors=vectorCache.computeIfAbsent(region.pageIndex(),page->{
            try{return PdfImageExtractionService.extractVectors(sourcePdf,page);}catch(Exception ignored){return List.of();}
        });
        double cx=region.x()+region.width()/2.0,cy=region.y()+region.height()/2.0;
        return vectors.stream().filter(v->("BLOCK".equals(v.kind())||"TABLE / GRID".equals(v.kind())))
                .filter(v->cx>=v.x()-1&&cx<=v.x()+v.width()+1&&cy>=v.y()-1&&cy<=v.y()+v.height()+1)
                .filter(v->v.width()>=region.width()*.75&&v.height()>=region.height())
                .min(Comparator.comparingDouble(v->v.width()*v.height()));
    }

    private Optional<PdfImageExtractionService.VectorRegion> sourceFlowContainer(TemplateElement element){
        if(element==null)return Optional.empty();
        List<PdfImageExtractionService.VectorRegion> vectors=vectorCache.computeIfAbsent(element.getPageIndex(),page->{
            try{return PdfImageExtractionService.extractVectors(sourcePdf,page);}catch(Exception ignored){return List.of();}
        });
        double cx=element.getX()+element.getWidth()/2.0,cy=element.getY()+element.getHeight()/2.0;
        return vectors.stream().filter(v->("BLOCK".equals(v.kind())||"TABLE / GRID".equals(v.kind())))
                .filter(v->cx>=v.x()-1&&cx<=v.x()+v.width()+1&&cy>=v.y()-1&&cy<=v.y()+v.height()+1)
                .filter(v->v.width()>=element.getWidth()*.75&&v.height()>=element.getHeight())
                .min(Comparator.comparingDouble(v->v.width()*v.height()));
    }

    private String sourceFlowGroup(PdfTextRegion region){
        return sourceFlowContainer(region).map(v->"SRCFLOW|"+v.sourceKey()).orElse("");
    }

    private String sourceFlowGroup(TemplateElement element){
        return sourceFlowContainer(element).map(v->"SRCFLOW|"+v.sourceKey()).orElse("");
    }

    private void configureFlowIdentity(TemplateElement element,String fieldKey){
        if(element==null||fieldKey==null||fieldKey.isBlank())return;
        TemplateFieldDefinition field=pdfField(fieldKey);
        if(field==null)return;
        String sourceGroup=sourceFlowGroup(element);
        if(!sourceGroup.isBlank())element.setFlowGroupId(sourceGroup);
        else if(!element.getMappingBlockId().isBlank())element.setFlowGroupId(element.getMappingBlockId());
        if(field.multiline()){
            ManualTemplateMappingService.applyFieldSemantics(element,field);
            if(!field.blockRole().isBlank())element.setFlowRole(field.blockRole());
        }else if(!sourceGroup.isBlank()&&element.getFlowRole().isBlank()){
            element.setFlowRole("FLOW_MEMBER");
        }
    }

    private PdfTextRegion combinedReplacementRegion(PdfTextRegion selected,List<PdfTextRegion> regions,String fieldKey){
        if(selected==null)return null;
        TemplateFieldDefinition field=pdfField(fieldKey);
        PdfTextRegion box;
        if(field==null||!field.multiline()||regions==null||regions.size()<=1)box=valueOnlyRegion(selected,fieldKey);
        else{
            List<PdfTextRegion> valueRegions=regions.stream().map(r->valueOnlyRegion(r,fieldKey)).toList();
            double minX=valueRegions.stream().mapToDouble(PdfTextRegion::x).min().orElse(selected.x());
            double minY=valueRegions.stream().mapToDouble(PdfTextRegion::y).min().orElse(selected.y());
            double maxX=valueRegions.stream().mapToDouble(r->r.x()+r.width()).max().orElse(selected.x()+selected.width());
            double maxY=valueRegions.stream().mapToDouble(r->r.y()+r.height()).max().orElse(selected.y()+selected.height());
            box=new PdfTextRegion(selected.pageIndex(),selected.text(),minX,minY,Math.max(8,maxX-minX),Math.max(selected.height(),maxY-minY),selected.fontSize(),selected.fontName(),selected.bold(),selected.italic(),selected.textColor(),selected.rotation());
        }
        return field!=null&&field.multiline()?expandMultilineReplacementWidth(box):box;
    }

    /** Expand only to the detected source container; no page-half/two-column assumption is allowed. */
    private PdfTextRegion expandMultilineReplacementWidth(PdfTextRegion box){
        if(box==null)return null;
        Optional<PdfImageExtractionService.VectorRegion> container=sourceFlowContainer(box);
        if(container.isEmpty())return box;
        double blockRight=container.get().x()+container.get().width()-3;
        double expanded=Math.max(box.width(),blockRight-box.x());
        if(expanded<=box.width()+2)return box;
        return new PdfTextRegion(box.pageIndex(),box.text(),box.x(),box.y(),expanded,box.height(),
                box.fontSize(),box.fontName(),box.bold(),box.italic(),box.textColor(),box.rotation());
    }

    /** Preserve inline source labels generically while replacing their changing value. */
    private String sourceAwareExpression(PdfTextRegion region,String expression,String fieldKey){
        if(region==null||expression==null||fieldKey==null)return expression;
        String raw=region.text()==null?"":region.text();int colon=raw.indexOf(':');
        if(colon<0||colon>Math.min(55,raw.length()-1))return expression;
        String prefix=raw.substring(0,colon+1);
        return prefix+" {{"+fieldKey+"}}";
    }

    private PdfTextRegion valueOnlyRegion(PdfTextRegion region,String fieldKey){
        if(region==null)return null;
        // Inline label:value rows become one movable source-aware text element. This keeps the fixed
        // label visually attached when an earlier Auto Height field grows and moves followers.
        String raw=region.text()==null?"":region.text();int colon=raw.indexOf(':');
        if(colon>=1&&colon<=Math.min(55,raw.length()-1))return region;
        return region;
    }

    private TemplateElement sourceMask(PdfTextRegion region,String key){
        double inset=Math.min(.65,Math.max(.15,Math.min(region.width(),region.height())*.04));
        TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),region.x()+inset,region.y()+inset,Math.max(1,region.width()-inset*2),Math.max(1,region.height()-inset*2));
        mask.setFillColor(sampleBackgroundColor(region.pageIndex(), region.x(), region.y(), region.width(), region.height()));mask.setStrokeColor(mask.getFillColor());mask.setStrokeWidth(0);mask.setLocked(true);mask.setReplacementSourceKey(key);mask.setSourceMaskSafe(isBackgroundUniform(region.pageIndex(),region.x(),region.y(),region.width(),region.height()));return mask;
    }

    private TemplateElement materializeSourceForm(PdfFormFieldRegion region) {
        if(region==null)return null;String key=sourceKey(region);TemplateElement existing=primaryReplacement(key);if(existing!=null)return existing;
        checkpoint();
        TemplateElement text=TemplateElement.of(ElementType.TEXT,region.pageIndex(),region.x(),region.y(),region.width(),region.height());
        text.setText(region.sampleValue().isBlank()?region.displayName():region.sampleValue());
        text.setFontFamily("HELVETICA");text.setFontSize(Math.max(7,Math.min(11,region.height()*.55)));text.setTextFit("SHRINK");
        text.setFillEnabled(false);text.setStrokeEnabled(false);text.setReplacementSourceKey(key);text.setReplacementGroupId("form-"+UUID.randomUUID());text.setSourceReplacementMode("FORM");text.setSourceMaskSafe(true);
        List<TemplateElement> list=new ArrayList<>(template.getElements());list.add(text);template.setElements(list);autosave();return text;
    }

    private TemplateElement materializeSourceImage(PdfImageRegion region) {
        if(region==null)return null;String key=sourceKey(region);TemplateElement existing=primaryReplacement(key);if(existing!=null)return existing;
        try {
            checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());String group="replace-"+UUID.randomUUID();
            TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),region.x(),region.y(),region.width(),region.height());mask.setFillColor(sampleBackgroundColor(region.pageIndex(),region.x(),region.y(),region.width(),region.height()));mask.setStrokeColor(mask.getFillColor());mask.setLocked(true);mask.setReplacementGroupId(group);mask.setReplacementSourceKey(key);list.add(mask);
            TemplateElement image=TemplateElement.of(ElementType.IMAGE,region.pageIndex(),region.x(),region.y(),region.width(),region.height());image.setImagePath(TemplateStorageService.importAsset(template,region.extractedImage()));image.setReplacementGroupId(group);image.setReplacementSourceKey(key);image.setFillEnabled(false);image.setStrokeEnabled(false);list.add(image);
            template.setElements(list);autosave();return image;
        } catch(Exception error){AppDialogService.error(root,"Image could not be converted","PDF Studio",rootMessage(error));return null;}
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

    private TemplateElement mapSourceVectorToImageField(PdfImageExtractionService.VectorRegion region, TemplateFieldDefinition field){
        if(region==null||field==null||!field.image())return null;
        String key=region.sourceKey();
        TemplateElement existing=primaryReplacement(key);
        if(existing!=null&&isImageLike(existing)){
            existing.setType(ElementType.IMAGE_FIELD);existing.setFieldKey(field.key());existing.setText(field.label());
            existing.setImageFit("FIT");existing.setPreserveAspectRatio(true);existing.setFillEnabled(false);existing.setStrokeEnabled(false);
            return existing;
        }
        List<TemplateElement> list=new ArrayList<>(template.getElements());
        list.removeIf(e->key.equals(e.getReplacementSourceKey()));
        String group="vector-image-"+UUID.randomUUID();
        double pad=.75;
        double x=Math.max(0,region.x()-pad),y=Math.max(0,region.y()-pad),w=region.width()+pad*2,h=region.height()+pad*2;
        boolean safe=isBackgroundUniform(region.pageIndex(),x,y,w,h);
        TemplateElement mask=TemplateElement.of(ElementType.WHITEOUT,region.pageIndex(),x,y,w,h);
        mask.setFillColor(sampleBackgroundColor(region.pageIndex(),x,y,w,h));mask.setStrokeColor(mask.getFillColor());mask.setStrokeWidth(0);mask.setLocked(true);
        mask.setReplacementGroupId(group);mask.setReplacementSourceKey(key);mask.setSourceMaskSafe(safe);list.add(mask);
        TemplateElement image=TemplateElement.of(ElementType.IMAGE_FIELD,region.pageIndex(),region.x(),region.y(),region.width(),region.height());
        image.setFieldKey(field.key());image.setText(field.label());image.setImageFit("FIT");image.setPreserveAspectRatio(true);image.setFillEnabled(false);image.setStrokeEnabled(false);
        image.setReplacementGroupId(group);image.setReplacementSourceKey(key);image.setSourceReplacementMode("MASK");image.setSourceMaskSafe(safe);image.setSourceStyleCaptured(true);
        if("company.signature".equals(field.key()))image.setPageRule("LAST");
        list.add(image);template.setElements(list);return image;
    }

    private TemplateElement primaryReplacement(String sourceKey){return template.getElements().stream().filter(e->sourceKey.equals(e.getReplacementSourceKey())&&e.getType()!=ElementType.WHITEOUT).findFirst().orElse(null);}
    private boolean hasReplacementFor(String sourceKey){return template.getElements().stream().anyMatch(e->sourceKey.equals(e.getReplacementSourceKey()));}
    private String sourceKey(PdfTextRegion r){return "PDF_TEXT|"+r.pageIndex()+"|"+round(r.x())+"|"+round(r.y())+"|"+round(r.width())+"|"+round(r.height())+"|"+PdfAutoMappingService.normalize(r.text());}
    private String sourceKey(PdfFormFieldRegion r){return "PDF_FORM|"+r.pageIndex()+"|"+r.fieldName()+"|"+round(r.x())+"|"+round(r.y())+"|"+round(r.width())+"|"+round(r.height());}
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
    @FXML private void addItemRepeater(){TemplateElement e=newElement(ElementType.ITEM_TABLE,Math.max(300,pageWidth-50),220);e.setTableColumns(List.of());e.setUseSourceTableDesign(false);e.setFontSize(8);addElement(e,null);if(lblInspectorHint!=null)lblInspectorHint.setText("Item Table created. Drag Item ERP fields onto it in the same left-to-right order as the PDF columns.");}
    @FXML private void addChargeRepeater(){TemplateElement e=newElement(ElementType.CHARGE_TABLE,Math.max(250,pageWidth*.48),120);e.setTableColumns(List.of("type","amount","gstPercent","taxAmount","total"));e.setUseSourceTableDesign(false);e.setFontSize(8);addElement(e,null);}
    @FXML private void addFinancialSummary(){
        List<TemplateElement> reviewRollback=snapshotElements();
        PdfImageExtractionService.VectorRegion source=selectedSourceVector;
        TemplateElement e;
        if(source!=null&&source.pageIndex()==pageIndex){
            e=TemplateElement.of(ElementType.BLOCK,pageIndex,source.x(),source.y(),source.width(),source.height());
            e.setSourceStyleCaptured(true);
            e.setSourceMaskSafe(isBackgroundUniform(pageIndex,source.x(),source.y(),source.width(),source.height()));
            e.setFillEnabled(true);e.setFillColor(dominantFill(source,sampleBackgroundColor(pageIndex,source.x(),source.y(),source.width(),source.height())));
            PdfImageExtractionService.VectorRegion grid=financialGridRegion(source).orElse(source);
            applySourceGridStyle(e,grid);
            e.setSummaryLabelRatio(inferSummaryLabelRatio(grid));
            double sourceRowHeight=inferSummaryRowHeight(grid);if(sourceRowHeight>0)e.setRowHeight(sourceRowHeight);
            Optional<PdfImageExtractionService.VectorRegion> totalBand=financialTotalBand(source);
            if(totalBand.isPresent()){
                PdfImageExtractionService.VectorRegion band=totalBand.get();
                double bodyBottom=source.y()+source.height();
                double gap=Math.max(0,band.y()-bodyBottom);
                double combinedBottom=Math.max(bodyBottom,band.y()+band.height());
                e.setHeight(combinedBottom-source.y());
                e.setSummaryTotalHeight(band.height());
                e.setSummaryTotalGap(gap);
                e.setSummaryTotalFillColor(dominantFill(band,e.getFillColor()));
            }else{
                String totalFill=bottomAccentFill(source,e.getFillColor());if(!totalFill.equalsIgnoreCase(e.getFillColor()))e.setSummaryTotalFillColor(totalFill);
            }
            PdfTextRegion style=textCache.getOrDefault(pageIndex,List.of()).stream().filter(r->inside(source,r))
                    .min(Comparator.comparingDouble(PdfTextRegion::y)).orElse(null);
            if(style!=null){e.setFontFamily(fontHint(style.fontName()));e.setFontSize(Math.max(5,style.fontSize()));e.setTextColor(style.textColor());}
        }else{
            e=newElement(ElementType.BLOCK,Math.max(180,pageWidth*.32),150);e.setFillEnabled(true);e.setFillColor("#FFFFFF");e.setStrokeEnabled(true);e.setStrokeColor("#AFC2D8");
        }
        e.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");e.setPageRule("LAST");e.setFlowRole("FINANCIAL_SUMMARY");e.setGrowthDirection("UP");e.setOverflowPolicy("ERROR");
        e.setMappingSourceLabel("Financial Summary");e.setAutoDetectedFieldKey("DYNAMIC_FINANCIAL_SUMMARY");e.setAutoDetectedConfidence(source==null?0.0:0.96);e.setMappingState(source==null?"REVIEW_REQUIRED":"AUTO");
        if(source!=null)e.setSourceReplacementMode("OBJECT");
        addElement(e,null);
        if(source!=null){
            List<TemplateElement> list=new ArrayList<>(template.getElements());
            double financialBottom=e.getY()+e.getHeight();
            for(PdfTextRegion printed:textCache.getOrDefault(pageIndex,List.of())){
                double cx=printed.x()+printed.width()/2.0,cy=printed.y()+printed.height()/2.0;
                if(cx<e.getX()||cx>e.getX()+e.getWidth()||cy<e.getY()||cy>financialBottom)continue;
                String key=sourceKey(printed);if(list.stream().anyMatch(existing->key.equals(existing.getReplacementSourceKey())))continue;
                TemplateElement marker=sourceMask(printed,key);marker.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");marker.setSourceMaskSafe(false);list.add(marker);
            }
            template.setElements(list);autosave();renderCanvas();
        }
        if(lblInspectorHint!=null)lblInspectorHint.setText(source==null
                ?"Financial Summary added. Review Mapping lets you inspect/adjust the block geometry, label split, anchor and growth before publishing."
                :"Financial Summary captured from the selected PDF calculation block. Review Mapping shows the detected geometry/style behavior while ERP remains responsible for dynamic rows.");
        showMappingReview(PdfMappingReviewSession.Section.FINANCIAL,reviewRollback);
    }


    /**
     * Find the grid that physically belongs to the selected Financial Summary block. Imported PDFs
     * often expose the outer rounded rectangle as one vector region and its row/split rules as a
     * second TABLE / GRID region. The source geometry, not a product/customer constant, is the
     * authority for the row rhythm and label/value split.
     */
    private Optional<PdfImageExtractionService.VectorRegion> financialGridRegion(PdfImageExtractionService.VectorRegion source){
        if(source==null)return Optional.empty();
        if("TABLE / GRID".equals(source.kind()))return Optional.of(source);
        return vectorCache.getOrDefault(source.pageIndex(),List.of()).stream()
                .filter(v->"TABLE / GRID".equals(v.kind()))
                .filter(v->horizontalOverlap(source,v)>=Math.min(source.width(),v.width())*.72)
                .filter(v->verticalOverlap(source,v)>=Math.min(source.height(),v.height())*.55)
                .min(Comparator.comparingDouble(v->Math.abs(v.x()-source.x())+Math.abs(v.y()-source.y())
                        +Math.abs(v.width()-source.width())+Math.abs(v.height()-source.height())));
    }

    /** Detect a physically separate Grand Total/accent strip immediately below the calculation body. */
    private Optional<PdfImageExtractionService.VectorRegion> financialTotalBand(PdfImageExtractionService.VectorRegion source){
        if(source==null)return Optional.empty();
        double bodyBottom=source.y()+source.height();
        double maxGap=Math.max(16,Math.min(30,source.height()*.35));
        return vectorCache.getOrDefault(source.pageIndex(),List.of()).stream()
                .filter(v->!Objects.equals(v.sourceKey(),source.sourceKey()))
                .filter(v->v.primitives().stream().anyMatch(PdfImageExtractionService.VectorPrimitive::filled))
                .filter(v->v.height()>=5&&v.height()<=Math.max(38,source.height()*.60))
                .filter(v->v.y()>=bodyBottom-1&&v.y()-bodyBottom<=maxGap)
                .filter(v->horizontalOverlap(source,v)>=Math.min(source.width(),v.width())*.78)
                .filter(v->Math.abs(v.width()-source.width())<=Math.max(18,source.width()*.14))
                .min(Comparator.comparingDouble(v->Math.max(0,v.y()-bodyBottom)*4
                        +Math.abs(v.x()-source.x())+Math.abs(v.width()-source.width())));
    }

    private double inferSummaryRowHeight(PdfImageExtractionService.VectorRegion region){
        if(region==null)return 0;
        List<Double> rules=region.primitives().stream()
                .filter(p->p.width()>=region.width()*.55&&p.height()<=3.0)
                .map(p->p.y()+p.height()/2.0).sorted().toList();
        if(rules.size()<2)return 0;
        List<Double> gaps=new ArrayList<>();
        for(int i=1;i<rules.size();i++){
            double gap=rules.get(i)-rules.get(i-1);
            if(gap>=6&&gap<=32)gaps.add(gap);
        }
        if(gaps.isEmpty())return 0;
        gaps.sort(Double::compareTo);
        return gaps.get(gaps.size()/2);
    }

    private double horizontalOverlap(PdfImageExtractionService.VectorRegion a,PdfImageExtractionService.VectorRegion b){
        return Math.max(0,Math.min(a.x()+a.width(),b.x()+b.width())-Math.max(a.x(),b.x()));
    }
    private double verticalOverlap(PdfImageExtractionService.VectorRegion a,PdfImageExtractionService.VectorRegion b){
        return Math.max(0,Math.min(a.y()+a.height(),b.y()+b.height())-Math.max(a.y(),b.y()));
    }

    private boolean inside(PdfImageExtractionService.VectorRegion box,PdfTextRegion text){
        double cx=text.x()+text.width()/2.0,cy=text.y()+text.height()/2.0;
        return cx>=box.x()&&cx<=box.x()+box.width()&&cy>=box.y()&&cy<=box.y()+box.height();
    }
    private String dominantFill(PdfImageExtractionService.VectorRegion region,String fallback){
        Map<String,Long> fills=region.primitives().stream().filter(PdfImageExtractionService.VectorPrimitive::filled)
                .collect(Collectors.groupingBy(PdfImageExtractionService.VectorPrimitive::fillColor,Collectors.counting()));
        return fills.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(fallback);
    }
    private String bottomAccentFill(PdfImageExtractionService.VectorRegion region,String fallback){
        return region.primitives().stream().filter(PdfImageExtractionService.VectorPrimitive::filled)
                .filter(p->p.y()+p.height()/2.0>=region.y()+region.height()*.65)
                .max(Comparator.comparingDouble(p->p.width()*p.height())).map(PdfImageExtractionService.VectorPrimitive::fillColor).orElse(fallback);
    }
    private double inferSummaryLabelRatio(PdfImageExtractionService.VectorRegion region){
        return region.primitives().stream().filter(p->p.width()<=3&&p.height()>=region.height()*.25)
                .mapToDouble(p->(p.x()-region.x())/Math.max(1,region.width())).filter(v->v>=.35&&v<=.85)
                .boxed().min(Comparator.comparingDouble(v->Math.abs(v-.66))).orElse(.66);
    }

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
            case "ITEMS"->{TemplateElement e=elementAt(ElementType.ITEM_TABLE,x,y,Math.max(300,pageWidth-x-15),220);e.setTableColumns(List.of());addElement(e,null);if(lblInspectorHint!=null)lblInspectorHint.setText("Drag Item ERP fields onto the Item Table to map each repeated column once.");}
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
        if (repeater != null && addFieldToRepeater(repeater, field, x)) return;

        if (field.image()) {
            PdfImageRegion sourceImage = findSourceImageAt(x, y);
            if (sourceImage != null) {
                TemplateElement e = materializeSourceImage(sourceImage);
                if (e != null) {
                    checkpoint();
                    e.setType(ElementType.IMAGE_FIELD);
                    e.setFieldKey(field.key());
                    e.setText(field.label());
                    e.setFillEnabled(false);
                    e.setStrokeEnabled(false);
                    autosave();
                    selectOnlyWithoutRender(e);
                    populateInspector(e);
                    renderCanvas();
                    return;
                }
            }
            PdfImageExtractionService.VectorRegion sourceVector=findSourceVectorAt(x,y);
            if(sourceVector!=null){
                checkpoint();
                TemplateElement e=mapSourceVectorToImageField(sourceVector,field);
                if(e!=null){autosave();selectOnlyWithoutRender(e);populateInspector(e);renderCanvas();return;}
            }
        }
        PdfFormFieldRegion sourceForm=findSourceFormAt(x,y);
        if(sourceForm!=null && !field.image()){TemplateElement e=materializeSourceForm(sourceForm);if(e!=null){checkpoint();ManualTemplateMappingService.mapField(e,field);configureFlowIdentity(e,field.key());autosave();selectOnlyWithoutRender(e);populateInspector(e);renderCanvas();}return;}
        PdfTextRegion source=findSourceTextAt(x,y);
        if(source!=null && !field.image()){checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());TemplateElement e=addSourceTextReplacement(list,source,"{{"+field.key()+"}}",field.key());ManualTemplateMappingService.configureMultilineMapping(e, field.key());template.setElements(list);autosave();selectOnlyWithoutRender(e);populateInspector(e);renderCanvas();return;}
        if(!field.image()){
            PdfSourceCapabilityService.Capability capability=sourceCapability(pageIndex);
            PdfImageRegion raster=findSourceImageAt(x,y);
            if(raster!=null && capability.kind()==PdfSourceCapabilityService.Kind.FLATTENED_IMAGE){
                AppDialogService.info(root,"Flattened PDF region","PDF Studio",
                        "This printed value is part of a raster/scanned image, so PDF Studio cannot remove only the old text without damaging the artwork. Use an editable/native PDF, or place the ERP field in a clean blank area intentionally.");
                if(lblInspectorHint!=null)lblInspectorHint.setText(capability.userMessage());
                return;
            }
        }
        TemplateElement e=elementAt(field.image()?ElementType.IMAGE_FIELD:ElementType.TEXT,x-60,y-12,field.image()?150:140,field.image()?80:28);e.setFieldKey(field.key());e.setText(field.image()?field.label():"{{"+field.key()+"}}");e.setFillEnabled(false);e.setStrokeEnabled(false);
        e.setSourceReplacementMode("OVERLAY");
        if(!field.image()){ManualTemplateMappingService.configureMultilineMapping(e,field.key());configureFlowIdentity(e,field.key());}
        addElement(e,null);
        if(lblInspectorHint!=null)lblInspectorHint.setText("Placed as an ERP overlay in an empty region. No source PDF value was removed. To replace printed content, drop the field directly on detected PDF text.");
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

    private boolean addFieldToRepeater(TemplateElement repeater, TemplateFieldDefinition field, double dropX) {
        String prefix = repeater.getType()==ElementType.ITEM_TABLE ? "item." : "charge.";
        if (!field.key().startsWith(prefix)) return false;
        checkpoint();
        String mappedLabel = field.label();
        if (repeater.getType()==ElementType.ITEM_TABLE && !repeater.getTableColumnBindings().isEmpty()) {
            int index=sourceColumnAt(repeater,dropX);
            if(index<0){
                lblSaveState.setText("Drop the field inside a detected item header column");
                return true;
            }
            TemplateColumnBinding before=repeater.getTableColumnBindings().get(index);
            ManualTemplateMappingService.mapItemColumn(repeater,field.key(),index);
            mappedLabel=(before.getSourceLabel().isBlank()?"Column "+(index+1):before.getSourceLabel())+" → "+field.label();
        } else {
            String column = field.key().substring(prefix.length());
            List<String> columns = new ArrayList<>(repeater.getTableColumns());
            if (!columns.contains(column)) columns.add(column);
            repeater.setTableColumns(columns);
        }
        autosave();
        selectOnlyWithoutRender(repeater); populateInspector(repeater); renderCanvas();
        lblSaveState.setText("Mapped " + mappedLabel + " ✓");
        if(lblInspectorHint!=null && repeater.getType()==ElementType.ITEM_TABLE && !repeater.getTableColumnBindings().isEmpty())
            lblInspectorHint.setText("Header mapped to ERP meaning. Drop another Item field onto its physical PDF header column; source order and widths are preserved.");
        return true;
    }

    private int sourceColumnAt(TemplateElement table,double pageX){
        double local=pageX-table.getX();
        List<TemplateColumnBinding> bindings=table.getTableColumnBindings();
        for(int i=0;i<bindings.size();i++){
            TemplateColumnBinding b=bindings.get(i);
            if(local>=b.getXOffset()-1&&local<=b.getXOffset()+Math.max(1,b.getWidth())+1)return i;
        }
        return -1;
    }

    @FXML private void addSelectedField(){TemplateFieldDefinition f=lstFields.getSelectionModel().getSelectedItem();if(f!=null)dropField(f,pageWidth/2,pageHeight/2);}

    private PdfFormFieldRegion findSourceFormAt(double x,double y){return formCache.getOrDefault(pageIndex,List.of()).stream().filter(r->x>=r.x()&&x<=r.x()+r.width()&&y>=r.y()&&y<=r.y()+r.height()).findFirst().orElse(null);}
    private PdfTextRegion findSourceTextAt(double x,double y){return textCache.getOrDefault(pageIndex,List.of()).stream().filter(r->x>=r.x()&&x<=r.x()+r.width()&&y>=r.y()&&y<=r.y()+r.height()).findFirst().orElse(null);}
    private PdfImageRegion findSourceImageAt(double x,double y){return imageCache.getOrDefault(pageIndex,List.of()).stream().filter(r->x>=r.x()&&x<=r.x()+r.width()&&y>=r.y()&&y<=r.y()+r.height()).findFirst().orElse(null);}
    private PdfImageExtractionService.VectorRegion findSourceVectorAt(double x,double y){
        return vectorCache.getOrDefault(pageIndex,List.of()).stream()
                .filter(r->x>=r.x()&&x<=r.x()+r.width()&&y>=r.y()&&y<=r.y()+r.height())
                .sorted(Comparator.comparingInt((PdfImageExtractionService.VectorRegion r)->"PATH".equals(r.kind())?0:1)
                        .thenComparingDouble(r->r.width()*r.height()))
                .findFirst().orElse(null);
    }

    private void chooseImageForNewObject(){
        FileChooser chooser=imageChooser("Add Image");var file=chooser.showOpenDialog(root.getScene().getWindow());if(file==null)return;
        try{TemplateElement e=newElement(ElementType.IMAGE,180,100);e.setImagePath(TemplateStorageService.importAsset(template,file.toPath()));e.setFillEnabled(false);e.setStrokeEnabled(false);addElement(e,null);}catch(Exception error){AppDialogService.error(root,"Image could not be added","PDF Studio",rootMessage(error));}
    }

    @FXML private void replaceSelectedImage(){
        TemplateElement e=editableSelectionFromSource();if(e==null)return;
        if(e.getType()!=ElementType.IMAGE&&e.getType()!=ElementType.IMAGE_FIELD){AppDialogService.info(root,"Select an image","Replace Image","Click an imported image or Studio image first.");return;}
        FileChooser chooser=imageChooser("Replace Image");var file=chooser.showOpenDialog(root.getScene().getWindow());if(file==null)return;
        try{checkpoint();e.setType(ElementType.IMAGE);e.setFieldKey("");e.setImagePath(TemplateStorageService.importAsset(template,file.toPath()));autosave();populateInspector(e);renderCanvas();}catch(Exception error){AppDialogService.error(root,"Image could not be replaced","PDF Studio",rootMessage(error));}
    }
    private FileChooser imageChooser(String title){FileChooser c=new FileChooser();c.setTitle(title);c.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg"));return c;}

    // ---------------------------------------------------------------------
    // Direct manipulation / grouping / formatting
    // ---------------------------------------------------------------------

    private void startDrag(MouseEvent event){dragging=true;dragSceneX=event.getSceneX();dragSceneY=event.getSceneY();dragOrigins.clear();for(TemplateElement e:selectedElementsWithDescendants())dragOrigins.put(e.getId(),new double[]{e.getX(),e.getY()});checkpoint();}
    private void dragSelection(MouseEvent event){
        double dx=(event.getSceneX()-dragSceneX)/scale,dy=(event.getSceneY()-dragSceneY)/scale;
        for(Map.Entry<String,double[]> entry:dragOrigins.entrySet()){
            TemplateElement e=findById(entry.getKey());if(e==null||geometryLocked(e))continue;double[] o=entry.getValue();
            e.setX(chkSnap.isSelected()?snap(o[0]+dx):o[0]+dx);e.setY(chkSnap.isSelected()?snap(o[1]+dy):o[1]+dy);
        }
        if(chkSnap.isSelected())applySmartGuides();else clearSmartGuides();
        renderCanvasFast();refreshSelectionInspector();
    }

    private void applySmartGuides(){
        clearSmartGuides();
        List<TemplateElement> selected=selectedElementsWithDescendants().stream().filter(e->!geometryLocked(e)).toList();
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

    private void nudgeSelection(KeyCode code,double step){if(selectedIds.isEmpty()||previewMode)return;checkpoint();for(TemplateElement e:selectedElementsWithDescendants()){if(geometryLocked(e))continue;if(code==KeyCode.LEFT)e.setX(e.getX()-step);if(code==KeyCode.RIGHT)e.setX(e.getX()+step);if(code==KeyCode.UP)e.setY(e.getY()-step);if(code==KeyCode.DOWN)e.setY(e.getY()+step);}autosave();renderCanvas();}

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
            if(selectedSourceForm!=null){TemplateElement e=materializeSourceForm(selectedSourceForm);if(e!=null)deleteSelected();return;}
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
    private void alignSelected(String mode){List<TemplateElement> list=selectedRootElements().stream().filter(e->!geometryLocked(e)).toList();if(list.isEmpty())return;checkpoint();double minX=list.stream().mapToDouble(TemplateElement::getX).min().orElse(0),maxX=list.stream().mapToDouble(e->e.getX()+e.getWidth()).max().orElse(pageWidth),minY=list.stream().mapToDouble(TemplateElement::getY).min().orElse(0),maxY=list.stream().mapToDouble(e->e.getY()+e.getHeight()).max().orElse(pageHeight);for(TemplateElement e:list){double ox=e.getX(),oy=e.getY();double nx=ox,ny=oy;switch(mode){case"LEFT"->nx=minX;case"RIGHT"->nx=maxX-e.getWidth();case"CENTER_H"->nx=(minX+maxX-e.getWidth())/2;case"TOP"->ny=minY;case"BOTTOM"->ny=maxY-e.getHeight();case"CENTER_V"->ny=(minY+maxY-e.getHeight())/2;}translateTree(e,nx-ox,ny-oy);}autosave();renderCanvas();}
    @FXML private void distributeHorizontal(){distribute(true);}@FXML private void distributeVertical(){distribute(false);}
    private void distribute(boolean horizontal){List<TemplateElement> list=new ArrayList<>(selectedRootElements().stream().filter(e->!geometryLocked(e)).toList());if(list.size()<3)return;checkpoint();if(horizontal){list.sort(Comparator.comparingDouble(TemplateElement::getX));double start=list.getFirst().getX(),end=list.getLast().getX()+list.getLast().getWidth(),total=list.stream().mapToDouble(TemplateElement::getWidth).sum(),gap=(end-start-total)/(list.size()-1);double cursor=start;for(TemplateElement e:list){double dx=cursor-e.getX();translateTree(e,dx,0);cursor+=e.getWidth()+gap;}}else{list.sort(Comparator.comparingDouble(TemplateElement::getY));double start=list.getFirst().getY(),end=list.getLast().getY()+list.getLast().getHeight(),total=list.stream().mapToDouble(TemplateElement::getHeight).sum(),gap=(end-start-total)/(list.size()-1);double cursor=start;for(TemplateElement e:list){double dy=cursor-e.getY();translateTree(e,0,dy);cursor+=e.getHeight()+gap;}}autosave();renderCanvas();}

    @FXML private void bringToFront(){reorder(Integer.MAX_VALUE);}@FXML private void sendToBack(){reorder(Integer.MIN_VALUE);}@FXML private void moveForward(){reorder(1);}@FXML private void moveBackward(){reorder(-1);}
    private void reorder(int direction){if(selectedIds.isEmpty())return;checkpoint();List<TemplateElement> list=new ArrayList<>(template.getElements());List<TemplateElement> selected=selectedElementsSnapshotFrom(list);list.removeAll(selected);if(direction==Integer.MAX_VALUE)list.addAll(selected);else if(direction==Integer.MIN_VALUE)list.addAll(0,selected);else{int anchor=direction>0?Math.min(list.size(),Math.max(0,highestOriginalIndex(selected)+direction)):Math.max(0,lowestOriginalIndex(selected)+direction);list.addAll(Math.min(anchor,list.size()),selected);}template.setElements(list);autosave();renderCanvas();}
    private int highestOriginalIndex(List<TemplateElement> s){return s.stream().mapToInt(e->indexOfId(template.getElements(),e.getId())).max().orElse(0);}private int lowestOriginalIndex(List<TemplateElement>s){return s.stream().mapToInt(e->indexOfId(template.getElements(),e.getId())).min().orElse(0);}private int indexOfId(List<TemplateElement> list,String id){for(int i=0;i<list.size();i++)if(Objects.equals(list.get(i).getId(),id))return i;return -1;}
    @FXML private void toggleLock(){if(selectedIds.isEmpty())return;checkpoint();boolean lock=selectedElements().stream().anyMatch(e->!e.isLocked());for(TemplateElement e:selectedElements())e.setLocked(lock);autosave();refreshSelectionInspector();renderCanvas();}
    @FXML private void toggleVisibility(){if(selectedIds.isEmpty())return;checkpoint();boolean show=selectedElements().stream().anyMatch(e->!e.isVisible());for(TemplateElement e:selectedElements())e.setVisible(show);autosave();refreshSelectionInspector();renderCanvas();}

    private void beginInlineEdit(TemplateElement e){
        if(e==null||!isTextLike(e)||geometryLocked(e)||previewMode)return;closeInlineEditor(false);inlineEditor=new TextArea(e.getText());inlineEditor.setWrapText(true);inlineEditor.getStyleClass().add("pdf-v2-inline-editor");inlineEditor.setLayoutX(e.getX()*scale);inlineEditor.setLayoutY(e.getY()*scale);inlineEditor.setPrefSize(Math.max(60,e.getWidth()*scale),Math.max(30,e.getHeight()*scale));inlineEditor.setStyle(styleFor(e));inlineEditor.focusedProperty().addListener((obs,old,focused)->{if(!focused&&old)closeInlineEditor(true);});inlineEditor.setOnKeyPressed(event->{if(event.getCode()==KeyCode.ESCAPE){closeInlineEditor(false);event.consume();}else if(event.getCode()==KeyCode.ENTER&&(event.isControlDown()||event.isMetaDown())){closeInlineEditor(true);event.consume();}});canvasPane.getChildren().add(inlineEditor);Platform.runLater(()->inlineEditor.requestFocus());
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

    @FXML private void viewJsonData() {
        if (template == null) return;
        TemplateData data;
        try { data = previewData(); }
        catch (Exception error) { data = currentPreviewData == null ? DocumentDataService.sample(template.getDocumentType()) : currentPreviewData; }
        TextArea json = new TextArea(ErpDocumentJsonService.pretty(template.getDocumentType(), data));
        json.setEditable(false);
        json.setWrapText(false);
        json.setPrefColumnCount(90);
        json.setPrefRowCount(32);
        json.setMinHeight(320);
        json.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        json.getStyleClass().add("pdf-json-viewer");
        Label help = new Label("Read-only ERP JSON used by this template. Drag fields from the mapper; JSON is generated by the application and is never edited manually.");
        help.setWrapText(true);
        VBox content = new VBox(9, help, json);
        content.setMinSize(640, 420);
        content.setPrefSize(860, 650);
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(json, Priority.ALWAYS);
        org.example.util.OwnedDialog<Void> dialog = new org.example.util.OwnedDialog<>();
        dialog.setTitle("PDF Studio JSON Data");
        dialog.setHeaderText(template.getDocumentType().label() + " • JSON contract v" + ErpDocumentJsonService.SCHEMA_VERSION);
        org.example.util.AppDialogRenderer.configureWorkspace(dialog, "document");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(900, 700);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    @FXML private void validateMapping() {
        if (template == null) return;
        TemplateMappingValidationService.Result result = TemplateMappingValidationService.evaluate(template);
        refreshRequirementUi();
        if (result.issues().isEmpty()) {
            AppDialogService.success(root, "Template mapping is ready",
                    result.requiredMapped() + " / " + result.requiredCount() + " required fields are mapped. " +
                            "The template can be previewed and published.");
            return;
        }
        if (result.errorCount() == 0) {
            String body = result.issues().stream().map(TemplateValidationIssue::userMessage).collect(Collectors.joining("\n\n"));
            AppDialogService.info(root, "Template mapping is ready with warnings", "PDF Studio",
                    "All required mappings are complete. Review these optional items before production use:\n\n" + body);
            return;
        }
        showValidationIssues("Mapping needs attention", result);
    }

    /** Backward-compatible text view used by older controller tests. */
    private List<String> mappingValidationIssues() {
        return TemplateMappingValidationService.evaluate(template).issues().stream()
                .filter(TemplateValidationIssue::error).map(TemplateValidationIssue::userMessage).toList();
    }

    private void showValidationIssues(String title, TemplateMappingValidationService.Result result) {
        String body = result.issues().stream().map(issue ->
                (issue.error() ? "ERROR — " : "WARNING — ") + issue.userMessage()).collect(Collectors.joining("\n\n"));
        AppDialogService.error(root, title, "PDF Studio", body);
    }

    private boolean allowWarnings(String action, TemplateMappingValidationService.Result result) {
        List<TemplateValidationIssue> warnings = result.issues().stream().filter(issue -> !issue.error()).toList();
        if (warnings.isEmpty()) return true;
        String body = warnings.stream().map(TemplateValidationIssue::userMessage).collect(Collectors.joining("\n\n"));
        return AppDialogService.confirm(root, action + " with warnings?",
                warnings.size() + " optional warning" + (warnings.size() == 1 ? "" : "s") + " remain.",
                body + "\n\nThese warnings do not block the document, but review them before using this template in production.");
    }

    @FXML private void publishAndSetDefault() {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "publish and activate a PDF template");
        if (template == null) return;
        if (template.getDocumentType().isGeneral() || !DocumentFlowRegistry.isAutomatic(template.getDocumentType())) {
            AppDialogService.info(root, "Design-only template", "PDF Studio", "Choose an automatic ERP document type before setting a system default.");
            return;
        }
        TemplateMappingValidationService.Result readiness = TemplateMappingValidationService.evaluate(template);
        if (!readiness.readyForDefault()) {
            showValidationIssues("Cannot publish this template as Default", readiness);
            fixNextIssue();
            return;
        }
        if (!allowWarnings("Publish & Set as Default", readiness)) return;
        if (!AppDialogService.confirm(root, "Publish & Set as Default",
                "Publish " + template.getName() + " and activate it for " + template.getDocumentType().label() + "?",
                "All required mappings are complete. The published snapshot will be certified for multi-page flow before activation. Standard document generation remains the safety fallback.")) return;
        try {
            TemplateStorageService.saveDraft(template);
            TemplateStorageService.publish(template);
            TemplateStorageService.activateAndSetDefault(template);
            refreshMeta(); refreshRequirementUi(); updateDefaultButton();
            lblSaveState.setText("ACTIVE runtime v" + template.getActiveVersion());
            AppDialogService.success(root, "Template is now the default",
                    template.getName() + " passed mapping and document-flow validation and is active for " + template.getDocumentType().label() + ".");
        } catch (Exception error) {
            AppDialogService.error(root, "Template could not become Default", "PDF Studio", activationFriendlyMessage(error));
        }
    }

    @FXML private void saveDraft(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "save a PDF template draft");
        if(template==null)return;
        try{
            TemplateStorageService.saveDraft(template);
            refreshMeta(); refreshRequirementUi(); updateDefaultButton();
            lblSaveState.setText("Draft saved • production unchanged");
            AppDialogService.success(root,"Draft saved",template.getName()+" was saved as a working draft. Current PDF/Print/Preview/Email generation is unchanged.");
        }catch(Exception e){AppDialogService.error(root,"Save failed","PDF Studio",rootMessage(e));}
    }

    @FXML private void publishTemplate(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "publish a PDF template");
        if(template==null)return;
        TemplateMappingValidationService.Result readiness = TemplateMappingValidationService.evaluate(template);
        if (!readiness.readyForDefault()) {
            showValidationIssues("Cannot publish this template", readiness);
            fixNextIssue();
            return;
        }
        if (!allowWarnings("Publish", readiness)) return;
        try{
            TemplateStorageService.publish(template);
            refreshMeta(); refreshRequirementUi(); updateDefaultButton();
            lblSaveState.setText("Published candidate v"+template.getPublishedVersion()+" • production unchanged");
            AppDialogService.success(root,"Template published",template.getName()+" passed required mapping validation and is ready for preview/default certification. Publishing does not change current document generation.");
        }catch(Exception e){AppDialogService.error(root,"Publish failed","PDF Studio",rootMessage(e));}
    }

    @FXML private void markDefault(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "activate a default PDF template");
        if(template==null)return;
        if(template.getDocumentType().isGeneral()||!DocumentFlowRegistry.isAutomatic(template.getDocumentType())){
            AppDialogService.info(root,"Design-only template","PDF Studio","Choose an ERP document type before marking this template as a system default.");
            return;
        }
        if(template.getPublishedVersion()<=0||template.isUnpublishedChanges()){
            AppDialogService.info(root,"Publish required","PDF Studio","Publish the current design first. Draft and preview changes never affect production.");
            return;
        }
        TemplateMappingValidationService.Result readiness = TemplateMappingValidationService.evaluate(template);
        if (!readiness.readyForDefault()) {
            showValidationIssues("Cannot make this template Default", readiness);
            fixNextIssue();
            return;
        }
        if (!allowWarnings("Mark as Default", readiness)) return;
        if(!AppDialogService.confirm(root,"Mark as System Default",
                "Activate "+template.getName()+" for "+template.getDocumentType().label()+"?",
                "The published snapshot will be certified for required mappings and multi-page behavior. Later draft edits remain isolated until you explicitly publish and activate again."))return;
        try{
            TemplateStorageService.activateAndSetDefault(template);
            refreshMeta(); refreshRequirementUi(); updateDefaultButton();
            lblSaveState.setText("ACTIVE runtime v"+template.getActiveVersion());
            AppDialogService.success(root,"System default activated",template.getName()+" v"+template.getActiveVersion()+" is now active for "+template.getDocumentType().label()+". Standard generation remains the automatic fallback if Studio rendering fails.");
        }catch(Exception e){AppDialogService.error(root,"Default could not be activated","PDF Studio",activationFriendlyMessage(e));}
    }

    private String activationFriendlyMessage(Throwable error) {
        String message = rootMessage(error);
        if (message.contains("generated") && message.contains("pages"))
            return "Multi-page layout is not safe yet. " + message + "\nFix: Open Item Table and increase the dynamic table area or reduce the row height, then preview a 25-item document.";
        if (message.toLowerCase(Locale.ROOT).contains("source") && message.toLowerCase(Locale.ROOT).contains("missing"))
            return "The template source PDF is missing.\nFix: Re-import the source PDF for this template before publishing it as Default.";
        if (message.toLowerCase(Locale.ROOT).contains("mapping"))
            return message + "\nFix the exact field shown in the Mapping Checklist, then try again.";
        return message;
    }

    @FXML private void showDesignMode(){
        if(template==null)return;
        previewMode=false; dataPreviewMode=false; previewPdf=null;
        configurePages(sourcePageCount); clearSelection(); updateModeButtons(); renderCanvas(); ensurePageObjects(pageIndex);
    }

    @FXML private void showDataPreviewMode(){
        if(template==null)return;
        try{
            // Show the actual renderer output for the selected ERP record. Painting live values
            // over the protected source PDF leaves stale address/GSTIN fragments visible.
            previewPdf=WorkspaceManager.getTempFolder().resolve("pdf-studio-v3-record-preview-"+template.getId()+".pdf");
            PdfStudioRenderer.render(template,previewData(),previewPdf);
            previewMode=true; dataPreviewMode=true;
            var size=PdfPreviewSupport.pageSize(previewPdf,0);
            configurePages(size.pageCount()); clearSelection(); updateModeButtons(); renderCanvas();
        }catch(Exception e){
            previewMode=false; dataPreviewMode=false; previewPdf=null; updateModeButtons();
            AppDialogService.error(root,"Record preview failed","PDF Studio",rootMessage(e));
        }
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
            AppDialogService.error(root,"Final PDF preview failed","PDF Studio",rootMessage(e));
        }
    }

    private void updateModeButtons(){
        if(btnDesignMode!=null)btnDesignMode.setDisable(!previewMode&&!dataPreviewMode);
        if(btnDataPreviewMode!=null)btnDataPreviewMode.setDisable(dataPreviewMode);
        if(btnFinalMode!=null)btnFinalMode.setDisable(previewMode&&!dataPreviewMode);
    }

    @FXML private void downloadMappingGuide(){
        try {
            Path guide = PdfStudioHelpService.exportToUserDownloadLocation();
            boolean opened = false;
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(guide.toFile());
                    opened = true;
                }
            } catch (Exception ignored) { }
            AppDialogService.success(root, "Mapping guide downloaded", "The PDF Studio mapping guide was saved to:\n" + guide
                    + (opened ? "\n\nIt has also been opened in your PDF viewer so you can keep it beside PDF Studio while mapping."
                              : "\n\nOpen this PDF from the saved location and keep it beside PDF Studio while mapping."));
        } catch (Exception error) {
            AppDialogService.error(root, "Mapping guide unavailable", "PDF Studio", rootMessage(error));
        }
    }

    @FXML private void exportPdf(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EXPORT_PDF", "export PDF output");if(template==null)return;FileChooser chooser=new FileChooser();chooser.setTitle("Export PDF");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files","*.pdf"));chooser.setInitialFileName(template.getName().replaceAll("[^A-Za-z0-9._-]","-")+".pdf");var file=chooser.showSaveDialog(root.getScene().getWindow());if(file==null)return;try{PdfStudioRenderer.render(template,previewData(),file.toPath());AppDialogService.success(root,"Test PDF exported",file.getAbsolutePath()+" • Production templates were not changed.");}catch(Exception e){AppDialogService.error(root,"Export failed","PDF Studio",rootMessage(e));}}

    private TemplateData previewData(){DocumentSample sample=cmbSampleDocument.getValue();if(sample==null)return currentPreviewData==null?DocumentDataService.sample(template.getDocumentType()):currentPreviewData;try{TemplateData data=DocumentDataService.load(template.getDocumentType(),sample.id());currentPreviewData=data;return data;}catch(Exception e){currentPreviewData=null;throw new IllegalStateException("Selected ERP record "+sample.id()+" could not be loaded. Preview/export cancelled.",e);}}

    @FXML private void appendBlankPage(){if(previewMode)return;if(template!=null&&template.isStrictFixedLayout()){AppDialogService.info(root,"Fixed PDF layout","PDF Studio","This template is STRICT FIXED. Page structure cannot be changed; import a new PDF template instead.");return;}try{sourcePageCount=TemplateStorageService.appendBlankPage(template,pageIndex);history.clear();configurePages(sourcePageCount);pageIndex=sourcePageCount-1;lstPages.getSelectionModel().select(pageIndex);clearObjectCaches();renderCanvas();ensurePageObjects(pageIndex);}catch(Exception e){AppDialogService.error(root,"Page could not be added","PDF Studio",rootMessage(e));}}
    @FXML private void deleteCurrentPage(){if(previewMode)return;if(template!=null&&template.isStrictFixedLayout()){AppDialogService.info(root,"Fixed PDF layout","PDF Studio","This template is STRICT FIXED. Page structure cannot be changed; import a new PDF template instead.");return;}if(!AppDialogService.confirm(root,"Delete Page","Delete page "+(pageIndex+1)+"?","Only the workspace template copy is changed."))return;try{sourcePageCount=TemplateStorageService.deletePage(template,pageIndex);history.clear();pageIndex=Math.max(0,Math.min(pageIndex,sourcePageCount-1));configurePages(sourcePageCount);clearObjectCaches();clearSelection();renderCanvas();ensurePageObjects(pageIndex);}catch(Exception e){AppDialogService.error(root,"Page could not be deleted","PDF Studio",rootMessage(e));}}
    @FXML private void rotatePageLeft(){rotate(-90);}@FXML private void rotatePageRight(){rotate(90);}private void rotate(int degrees){if(previewMode)return;if(template!=null&&template.isStrictFixedLayout()){AppDialogService.info(root,"Fixed PDF layout","PDF Studio","This template is STRICT FIXED. Page rotation cannot be changed; import a new PDF template instead.");return;}try{TemplateStorageService.rotatePage(template,pageIndex,degrees);history.clear();var size=PdfPreviewSupport.pageSize(sourcePdf,pageIndex);pageWidth=size.width();pageHeight=size.height();clearObjectCaches();clearSelection();renderCanvas();ensurePageObjects(pageIndex);}catch(Exception e){AppDialogService.error(root,"Page could not be rotated","PDF Studio",rootMessage(e));}}

    @FXML private void fitWidth(){Platform.runLater(()->{double available=Math.max(260,canvasScroll.getViewportBounds().getWidth()-70);setZoomForScale(available/Math.max(1,pageWidth));});}
    @FXML private void fitPage(){Platform.runLater(()->{double w=Math.max(260,canvasScroll.getViewportBounds().getWidth()-70),h=Math.max(260,canvasScroll.getViewportBounds().getHeight()-70);setZoomForScale(Math.min(w/Math.max(1,pageWidth),h/Math.max(1,pageHeight)));});}
    private void setZoomForScale(double target){double pct=target/BASE_SCALE*100.0;zoomSlider.setValue(Math.max(zoomSlider.getMin(),Math.min(zoomSlider.getMax(),pct)));}

    // ---------------------------------------------------------------------
    // Undo/redo and persistence
    // ---------------------------------------------------------------------

    @FXML private void undo(){
        if(previewMode||!history.canUndo())return;
        List<TemplateElement> previous=history.undo(template.getElements());
        if(previous==null)return;
        template.setElements(previous);selectedIds.clear();autosave();clearInspector();renderCanvas();
    }
    @FXML private void redo(){
        if(previewMode||!history.canRedo())return;
        List<TemplateElement> next=history.redo(template.getElements());
        if(next==null)return;
        template.setElements(next);selectedIds.clear();autosave();clearInspector();renderCanvas();
    }
    private void checkpoint(){history.checkpoint(template.getElements());}
    private List<TemplateElement> snapshot(List<TemplateElement> source){return PdfStudioHistory.snapshot(source);}
    private void autosave(){try{TemplateStorageService.saveDraft(template);lblSaveState.setText("Draft saved • production unchanged");}catch(Exception e){lblSaveState.setText("Save failed");}refreshMeta();refreshRequirementUi();if(currentMappingAnalysis!=null)updateMappingUi(currentMappingAnalysis);updateDefaultButton();}

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
        if(btnPublishDefault!=null)btnPublishDefault.setDisable(template.getStatus()==TemplateStatus.ARCHIVED||!automatic);
    }
    private void updatePageWarning(){if(template==null)return;long outside=template.getElements().stream().filter(e->e.getPageIndex()==pageIndex&&PdfStyleResolver.effectivelyVisible(template,e)).filter(e->e.getX()<0||e.getY()<0||e.getX()+e.getWidth()>pageWidth||e.getY()+e.getHeight()>pageHeight).count();lblPageWarning.setText(outside==0?"":outside+" object(s) extend outside page • export will clip");}
    private void clearObjectCaches(){textCache.clear();formCache.clear();imageCache.clear();vectorCache.clear();sourcePageImages.clear();loadingPages.clear();}
    private String displayName(TemplateElement e){if(e==null)return "Object";return switch(e.getType()){case TEXT->"Text • "+abbreviate(e.getText(),28);case FIELD->"ERP Field • "+e.getFieldKey();case IMAGE->"Image";case IMAGE_FIELD->"ERP Image • "+e.getFieldKey();case BLOCK->"DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId())?"Financial Summary":"Section / Group";case RECTANGLE->"Rectangle";case WHITEOUT->"Source Mask";case LINE->"Line";case PATH->"Vector Path";case ITEM_TABLE->"Item Repeater";case CHARGE_TABLE->"Charge Repeater";};}
    private boolean isTextLike(TemplateElement e){return e!=null&&(e.getType()==ElementType.TEXT||e.getType()==ElementType.FIELD);}
    private boolean isImageLike(TemplateElement e){return e!=null&&(e.getType()==ElementType.IMAGE||e.getType()==ElementType.IMAGE_FIELD);}
    private boolean isRepeater(TemplateElement e){return e!=null&&(e.getType()==ElementType.ITEM_TABLE||e.getType()==ElementType.CHARGE_TABLE);}
    private String fontHint(String name){
        String n=name==null?"":name.toUpperCase(Locale.ROOT).replaceFirst("^[A-Z]{6}\\+","");
        if(n.contains("TIMES"))return"TIMES";if(n.contains("COURIER")||n.contains("MONO"))return"COURIER";
        if(n.contains("HELVETICA"))return"HELVETICA";if(n.contains("ARIAL"))return"ARIAL";
        n=n.replaceAll("(?i)([-_ ]?(BOLD|SEMIBOLD|DEMI|BLACK|ITALIC|OBLIQUE|REGULAR|ROMAN|PSMT|MT))+$","").trim();
        return n.isBlank()?"HELVETICA":n;
    }
    private String bindingKey(String value){if(value==null||value.startsWith("—"))return"";int i=value.indexOf("  •");return i<0?value.trim():value.substring(0,i).trim();}
    private double parse(TextField f,double fallback){try{String s=f.getText()==null?"":f.getText().trim().replace(",","");return s.isBlank()?fallback:Double.parseDouble(s);}catch(Exception ignored){return fallback;}}
    private String fmt(double v){return Math.abs(v-Math.rint(v))<.0001?Long.toString(Math.round(v)):String.format(Locale.ENGLISH,"%.2f",v);}
    private String hex(Color c){if(c==null)return"#000000";return String.format(Locale.ROOT,"#%02X%02X%02X",Math.round((float)c.getRed()*255),Math.round((float)c.getGreen()*255),Math.round((float)c.getBlue()*255));}
    private Color color(String value,Color fallback){try{return Color.web(value);}catch(Exception e){return fallback;}}
    private String abbreviate(String s,int max){String t=s==null?"":s.replace('\n',' ').trim();return t.length()<=max?t:t.substring(0,Math.max(0,max-1))+"…";}
    private String rootMessage(Throwable error){Throwable t=error;while(t.getCause()!=null&&t.getCause()!=t)t=t.getCause();String m=t.getMessage();return m==null||m.isBlank()?t.getClass().getSimpleName():m;}


    private boolean geometryLocked(TemplateElement e){return PdfStyleResolver.effectivelyLocked(template,e);}
    private List<TemplateElement> selectedRootElements(){
        Set<String> ids=new LinkedHashSet<>(selectedIds);
        return selectedElements().stream().filter(e->e.getParentId().isBlank()||!ids.contains(e.getParentId())).toList();
    }
    private void translateTree(TemplateElement root,double dx,double dy){
        if(root==null||geometryLocked(root)||(Math.abs(dx)<.0001&&Math.abs(dy)<.0001))return;
        Set<String> ids=idsWithDescendants(Set.of(root.getId()));
        for(TemplateElement e:template.getElements())if(ids.contains(e.getId())&&!geometryLocked(e)){e.setX(e.getX()+dx);e.setY(e.getY()+dy);}
    }
    private void translateDescendants(TemplateElement root,double dx,double dy){
        if(root==null)return; Set<String> ids=idsWithDescendants(Set.of(root.getId())); ids.remove(root.getId());
        for(TemplateElement e:template.getElements())if(ids.contains(e.getId())&&!geometryLocked(e)){e.setX(e.getX()+dx);e.setY(e.getY()+dy);}
    }

    private boolean isBackgroundUniform(int page,double x,double y,double width,double height){
        Image image=sourcePageImages.get(page);
        if(image==null||image.getPixelReader()==null||pageWidth<=0||pageHeight<=0)return true;
        var reader=image.getPixelReader();int iw=Math.max(1,(int)Math.round(image.getWidth())),ih=Math.max(1,(int)Math.round(image.getHeight()));
        int left=clampInt((int)Math.floor(x/pageWidth*iw),0,iw-1),right=clampInt((int)Math.ceil((x+width)/pageWidth*iw),0,iw-1);
        int top=clampInt((int)Math.floor(y/pageHeight*ih),0,ih-1),bottom=clampInt((int)Math.ceil((y+height)/pageHeight*ih),0,ih-1);
        int margin=Math.max(2,(int)Math.round(iw/pageWidth*2.0));Map<Integer,Integer> buckets=new HashMap<>();int samples=0;
        for(int py=Math.max(0,top-margin);py<=Math.min(ih-1,bottom+margin);py+=Math.max(1,margin/2))for(int px=Math.max(0,left-margin);px<=Math.min(iw-1,right+margin);px+=Math.max(1,margin/2)){
            boolean ring=px<left||px>right||py<top||py>bottom;if(!ring)continue;int argb=reader.getArgb(px,py);int r=((argb>>16)&0xFF)/24*24,g=((argb>>8)&0xFF)/24*24,b=(argb&0xFF)/24*24;int key=(r<<16)|(g<<8)|b;buckets.merge(key,1,Integer::sum);samples++;}
        int dominant=buckets.values().stream().mapToInt(Integer::intValue).max().orElse(samples);
        return samples<8||dominant/(double)Math.max(1,samples)>=.52;
    }

    private String sampleBackgroundColor(int page, double x, double y, double width, double height) {
        Image image = sourcePageImages.get(page);
        if (image == null || image.getPixelReader() == null || pageWidth <= 0 || pageHeight <= 0) return "#FFFFFF";
        var reader = image.getPixelReader();
        int iw = Math.max(1, (int)Math.round(image.getWidth())), ih = Math.max(1, (int)Math.round(image.getHeight()));
        int left=clampInt((int)Math.floor(x/pageWidth*iw),0,iw-1), right=clampInt((int)Math.ceil((x+width)/pageWidth*iw),0,iw-1);
        int top=clampInt((int)Math.floor(y/pageHeight*ih),0,ih-1), bottom=clampInt((int)Math.ceil((y+height)/pageHeight*ih),0,ih-1);
        int margin=Math.max(2,(int)Math.round(iw/pageWidth*2.0)); Map<Integer,Integer> colors=new HashMap<>();
        List<Integer> samples=new ArrayList<>();
        for(int py=Math.max(0,top-margin);py<=Math.min(ih-1,bottom+margin);py++) for(int px=Math.max(0,left-margin);px<=Math.min(iw-1,right+margin);px++) {
            boolean ring=px<left||px>right||py<top||py>bottom; if(!ring)continue; int argb=reader.getArgb(px,py); samples.add(argb);
            int r=((argb>>16)&0xFF)/16*16,g=((argb>>8)&0xFF)/16*16,b=(argb&0xFF)/16*16,key=(r<<16)|(g<<8)|b;colors.merge(key,1,Integer::sum);
        }
        int bucket=colors.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xFFFFFF);
        long rs=0,gs=0,bs=0,count=0;
        for(int argb:samples){int r=(argb>>16)&0xFF,g=(argb>>8)&0xFF,b=argb&0xFF;int key=((r/16*16)<<16)|((g/16*16)<<8)|(b/16*16);if(key==bucket){rs+=r;gs+=g;bs+=b;count++;}}
        int rgb=count==0?0xFFFFFF:(((int)(rs/count))<<16)|(((int)(gs/count))<<8)|((int)(bs/count));
        return String.format(Locale.ROOT,"#%06X",rgb&0xFFFFFF);
    }
    private int clampInt(int value,int min,int max){return PdfStudioGeometryPolicy.clamp(value,min,max);}

    private String resolveExpression(String text,TemplateData data){
        if(text==null||text.isBlank()||data==null)return text==null?"":text;String result=text;java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}").matcher(text);StringBuffer out=new StringBuffer();while(m.find()){String key=m.group(1);String value=data.value(key);m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(value));}m.appendTail(out);return out.toString();
    }

}
