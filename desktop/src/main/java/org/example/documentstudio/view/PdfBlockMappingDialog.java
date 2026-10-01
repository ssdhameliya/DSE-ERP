package org.example.documentstudio.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.documentstudio.engine.UniversalBlockDefinition;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.PdfTextRegion;
import org.example.documentstudio.model.TemplateColumnBinding;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.service.ManualTemplateMappingService;
import org.example.documentstudio.service.PdfAutoMappingService;
import org.example.documentstudio.service.PdfImageExtractionService;
import org.example.documentstudio.service.PdfSourceTableDetectionService;
import org.example.documentstudio.service.PdfTextExtractionService;
import org.example.documentstudio.service.TemplateFieldCatalog;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.util.AppDialogService;
import org.example.util.SemanticIconManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated, focused block-wise mapping dialog and embedded view.
 * Maps one logical block at a time without seeing dozens of unrelated fields.
 */
public final class PdfBlockMappingDialog extends BorderPane {

    public record MappingRowState(
            TemplateElement element,
            String sourceLabel,
            String sourceValue,
            String currentFieldKey,
            boolean isStatic
    ) {}

    private final DocumentTemplate template;
    private final UniversalBlockDefinition blockDef;
    private final Runnable onCommit;
    private final Runnable onBack;
    private final VBox contentList = new VBox(12);
    private final List<MappingRowState> rowStates = new ArrayList<>();

    public static boolean show(Node parentNode, DocumentTemplate template, UniversalBlockDefinition blockDef, Runnable onCommit) {
        PdfBlockMappingDialog dialogContent = new PdfBlockMappingDialog(template, blockDef, onCommit, null);

        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType saveBtn = new ButtonType("Save Block", ButtonBar.ButtonData.OK_DONE);

        Optional<ButtonType> result = AppDialogService.workspace(
                parentNode,
                "block-mapping",
                blockDef.displayName(),
                "Block-Wise Mapping: " + blockDef.displayName(),
                blockDef.description(),
                dialogContent,
                880,
                580,
                cancelBtn,
                saveBtn
        );

        if (result.isPresent() && result.get().equals(saveBtn)) {
            dialogContent.commitChanges();
            return true;
        }
        return false;
    }

    public PdfBlockMappingDialog(DocumentTemplate template, UniversalBlockDefinition blockDef, Runnable onCommit) {
        this(template, blockDef, onCommit, null);
    }

    public PdfBlockMappingDialog(DocumentTemplate template, UniversalBlockDefinition blockDef, Runnable onCommit, Runnable onBack) {
        this.template = Objects.requireNonNull(template);
        this.blockDef = Objects.requireNonNull(blockDef);
        this.onCommit = onCommit;
        this.onBack = onBack;

        setMinSize(820, 480);
        setPrefSize(860, 540);
        getStyleClass().add("dse-block-mapping-dialog");

        // Header info
        VBox headerBox = buildHeader();
        setTop(headerBox);
        BorderPane.setMargin(headerBox, new Insets(0, 0, 16, 0));

        // Center: list of rows
        ScrollPane scroller = new ScrollPane(contentList);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentList.setFillWidth(true);
        contentList.setPadding(new Insets(8));
        setCenter(scroller);

        populateRows();
    }

    private VBox buildHeader() {
        Label title = new Label(blockDef.displayName());
        title.getStyleClass().add("dse-workspace-section-title");

        Label desc = new Label(blockDef.description());
        desc.setWrapText(true);
        desc.getStyleClass().add("dse-workspace-section-help");

        Node iconNode = SemanticIconManager.compact(blockDef.icon(), 20);
        HBox titleLine = new HBox(10, iconNode, title);
        titleLine.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        if (onBack != null) {
            Button btnBack = new Button("← Back to All Blocks");
            btnBack.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 6; -fx-padding: 6 12; -fx-cursor: hand;");
            btnBack.setOnAction(e -> onBack.run());
            topRow.getChildren().add(btnBack);
        }

        topRow.getChildren().addAll(titleLine, spacer);

        if (onBack != null) {
            Button btnSave = new Button("Save Block ✓");
            btnSave.setStyle("-fx-background-color: #166534; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand;");
            btnSave.setOnAction(e -> {
                commitChanges();
                onBack.run();
            });
            topRow.getChildren().add(btnSave);
        }

        VBox header = new VBox(6, topRow, desc);
        header.getStyleClass().add("dse-workspace-nav-card");
        header.setPadding(new Insets(12, 16, 12, 16));
        return header;
    }

    private void populateRows() {
        contentList.getChildren().clear();
        rowStates.clear();

        if (blockDef.isTableGrid()) {
            populateTableGridRows();
        } else {
            populateScalarFieldRows();
        }
    }

    private void populateTableGridRows() {
        TemplateElement tableEl = findTableElement();
        if (tableEl == null || tableEl.getTableColumnBindings() == null || tableEl.getTableColumnBindings().isEmpty()) {
            Label empty = new Label("No item table grid detected. Please detect or add an Item Table first.");
            empty.getStyleClass().add("dse-workspace-empty");
            contentList.getChildren().add(empty);
            return;
        }

        Label notice = new Label("Physical columns detected from imported PDF. Match each column to its ERP line-item field:");
        notice.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        contentList.getChildren().add(notice);

        int colIdx = 1;
        for (TemplateColumnBinding col : tableEl.getTableColumnBindings()) {
            HBox row = buildColumnRow(colIdx++, col, tableEl);
            contentList.getChildren().add(row);
        }
    }

    private HBox buildColumnRow(int idx, TemplateColumnBinding col, TemplateElement tableEl) {
        Label num = new Label("#" + idx);
        num.setMinWidth(35);
        num.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748b;");

        Label label = new Label(col.getSourceLabel().isBlank() ? "(Column " + idx + ")" : col.getSourceLabel());
        label.setMinWidth(140);
        label.setPrefWidth(160);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label geom = new Label(String.format(Locale.ROOT, "Width: %.0f pt | Align: %s", col.getWidth(), col.getAlignment()));
        geom.setMinWidth(160);
        geom.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        ComboBox<String> fieldPicker = new ComboBox<>();
        List<String> options = new ArrayList<>(blockDef.candidateFieldKeys());
        options.sort(String::compareTo);
        options.add(0, ""); // unmapped / static option
        fieldPicker.setItems(FXCollections.observableArrayList(options));
        fieldPicker.setValue(col.getFieldKey());
        fieldPicker.setPrefWidth(220);
        HBox.setHgrow(fieldPicker, Priority.ALWAYS);

        fieldPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            col.setFieldKey(newVal == null ? "" : newVal.trim());
            if (tableEl != null) {
                ManualTemplateMappingService.syncLegacyColumns(tableEl);
            }
        });

        HBox row = new HBox(12, num, label, geom, fieldPicker);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;");
        return row;
    }

    private void populateScalarFieldRows() {
        List<TemplateElement> elements = findMatchingElements();

        if (elements.isEmpty()) {
            Label empty = new Label("No text elements in the template currently match this block. You can map text on the canvas to fields in this block.");
            empty.getStyleClass().add("dse-workspace-empty");
            contentList.getChildren().add(empty);
            return;
        }

        for (TemplateElement el : elements) {
            MappingRowState state = new MappingRowState(
                    el,
                    el.getMappingSourceLabel().isBlank() ? el.getText() : el.getMappingSourceLabel(),
                    el.getText(),
                    el.getFieldKey(),
                    "STATIC".equalsIgnoreCase(el.getMappingState())
            );
            rowStates.add(state);
            contentList.getChildren().add(buildScalarRow(state));
        }
    }

    private HBox buildScalarRow(MappingRowState state) {
        TemplateElement el = state.element();

        Label source = new Label(state.sourceLabel().isBlank() ? "PDF Text" : state.sourceLabel());
        source.setWrapText(true);
        source.setMinWidth(160);
        source.setPrefWidth(180);
        source.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

        ComboBox<String> fieldPicker = new ComboBox<>();
        List<String> options = new ArrayList<>(blockDef.candidateFieldKeys());
        options.sort(String::compareTo);
        options.add(0, ""); // unmapped option
        fieldPicker.setItems(FXCollections.observableArrayList(options));
        fieldPicker.setValue(el.getFieldKey());
        fieldPicker.setPrefWidth(240);
        HBox.setHgrow(fieldPicker, Priority.ALWAYS);

        Label statusChip = new Label(el.getFieldKey().isBlank() ? "Unmapped" : "Mapped ✓");
        statusChip.setMinWidth(85);
        statusChip.setAlignment(Pos.CENTER);
        statusChip.setStyle(el.getFieldKey().isBlank()
                ? "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-padding: 3 8; -fx-background-radius: 12;"
                : "-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-padding: 3 8; -fx-background-radius: 12;");

        fieldPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            String chosen = newVal == null ? "" : newVal.trim();
            el.setFieldKey(chosen);
            statusChip.setText(chosen.isBlank() ? "Unmapped" : "Mapped ✓");
            statusChip.setStyle(chosen.isBlank()
                    ? "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-padding: 3 8; -fx-background-radius: 12;"
                    : "-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-padding: 3 8; -fx-background-radius: 12;");
            if (!chosen.isBlank()) {
                var def = TemplateFieldCatalog.findPdf(template.getDocumentType(), chosen);
                if (def == null) def = TemplateFieldCatalog.find(chosen);
                if (def != null) {
                    ManualTemplateMappingService.mapField(el, def);
                } else {
                    el.setText("{{" + chosen + "}}");
                }
                el.setMappingState("CONFIRMED");
                el.setSourceReplacementMode("OBJECT");
                if (el.getReplacementGroupId() == null || el.getReplacementGroupId().isBlank()) {
                    el.setReplacementGroupId("grp-" + UUID.randomUUID());
                }
                ensureWhiteoutMask(el);
            } else {
                el.setText(el.getMappingSourceLabel().isBlank() ? "" : el.getMappingSourceLabel());
                el.setMappingState("UNMAPPED");
            }
        });

        Button btnStatic = new Button("Keep Static");
        btnStatic.setStyle("-fx-font-size: 11px;");
        btnStatic.setOnAction(e -> {
            fieldPicker.setValue("");
            el.setFieldKey("");
            el.setMappingState("STATIC");
            statusChip.setText("Static");
            statusChip.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #475569; -fx-padding: 3 8; -fx-background-radius: 12;");
        });

        HBox row = new HBox(12, source, fieldPicker, statusChip, btnStatic);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;");
        return row;
    }

    private void ensureWhiteoutMask(TemplateElement el) {
        if (el == null || el.getReplacementGroupId() == null || el.getReplacementGroupId().isBlank()) return;
        String group = el.getReplacementGroupId();
        boolean hasMask = template.getElements().stream()
                .anyMatch(e -> e != null && e.getType() == ElementType.WHITEOUT && group.equals(e.getReplacementGroupId()));
        if (!hasMask) {
            TemplateElement mask = TemplateElement.of(
                    ElementType.WHITEOUT,
                    el.getPageIndex(),
                    el.getX() - 0.5,
                    el.getY() - 0.5,
                    Math.max(1, el.getWidth() + 1.0),
                    Math.max(1, el.getHeight() + 1.0)
            );
            mask.setFillColor("#FFFFFF");
            mask.setStrokeColor("#FFFFFF");
            mask.setStrokeWidth(0);
            mask.setFillEnabled(true);
            mask.setStrokeEnabled(false);
            mask.setLocked(true);
            mask.setReplacementGroupId(group);
            mask.setReplacementSourceKey(el.getReplacementSourceKey());
            mask.setSourceMaskSafe(true);
            int idx = template.getElements().indexOf(el);
            if (idx >= 0) {
                template.getElements().add(idx, mask);
            } else {
                template.getElements().add(0, mask);
            }
        }
    }

    private List<TemplateElement> findMatchingElements() {
        if (template == null) return List.of();
        if (template.getElements() == null) template.setElements(new ArrayList<>());
        List<TemplateElement> matched = new ArrayList<>();

        for (TemplateElement el : template.getElements()) {
            if (el == null || el.getType() == ElementType.ITEM_TABLE || el.getType() == ElementType.WHITEOUT) continue;
            // Matches candidate keys or explicit block ID
            if (blockDef.id().equalsIgnoreCase(el.getMappingBlockId())
                    || blockDef.candidateFieldKeys().contains(el.getFieldKey())
                    || blockDef.candidateFieldKeys().contains(el.getAutoDetectedFieldKey())) {
                matched.add(el);
            }
        }
        if (!matched.isEmpty()) return matched;

        // Auto-detect candidate text regions from source PDF if elements are currently empty
        try {
            Path sourcePdf = TemplateStorageService.sourcePdf(template);
            if (sourcePdf != null && Files.isRegularFile(sourcePdf)) {
                List<PdfTextRegion> regions = PdfTextExtractionService.extract(sourcePdf, 0);
                List<PdfImageExtractionService.VectorRegion> vectors = PdfImageExtractionService.extractVectors(sourcePdf, 0);
                var analysis = PdfAutoMappingService.analyze(template.getDocumentType(), regions, null);
                for (PdfAutoMappingService.Mapping m : analysis.mappings()) {
                    if (blockDef.candidateFieldKeys().contains(m.fieldKey())) {
                        String srcKey = sourceKey(m.region());
                        boolean exists = template.getElements().stream()
                                .anyMatch(e -> srcKey.equals(e.getReplacementSourceKey()));
                        if (!exists) {
                            String groupId = "grp-" + UUID.randomUUID();
                            TemplateElement mask = TemplateElement.of(
                                    ElementType.WHITEOUT,
                                    0,
                                    m.region().x() - 0.5,
                                    m.region().y() - 0.5,
                                    Math.max(1, m.region().width() + 1.0),
                                    Math.max(1, m.region().height() + 1.0)
                            );
                            mask.setFillColor("#FFFFFF");
                            mask.setStrokeColor("#FFFFFF");
                            mask.setStrokeWidth(0);
                            mask.setFillEnabled(true);
                            mask.setStrokeEnabled(false);
                            mask.setLocked(true);
                            mask.setReplacementGroupId(groupId);
                            mask.setReplacementSourceKey(srcKey);
                            mask.setSourceMaskSafe(true);
                            template.getElements().add(mask);

                            TemplateElement el = TemplateElement.of(
                                    ElementType.TEXT,
                                    0,
                                    m.region().x(),
                                    m.region().y(),
                                    m.region().width(),
                                    Math.max(m.region().height(), m.region().fontSize() * 1.25)
                            );
                            el.setText(m.expression());
                            el.setFieldKey(m.fieldKey());
                            el.setFontSize(m.region().fontSize());
                            el.setMappingSourceLabel(m.region().text());
                            el.setMappingBlockId(blockDef.id());
                            el.setReplacementSourceKey(srcKey);
                            el.setReplacementGroupId(groupId);
                            el.setSourceReplacementMode("OBJECT");
                            el.markAutoDetectedMapping(m.region().text(), m.fieldKey(), m.confidence());
                            var def = TemplateFieldCatalog.findPdf(template.getDocumentType(), m.fieldKey());
                            if (def != null) {
                                ManualTemplateMappingService.applyFieldSemantics(el, def);
                                if (def.multiline()) {
                                    expandMultilineElement(el, vectors, regions);
                                }
                            }
                            template.getElements().add(el);
                            matched.add(el);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return matched;
    }

    private static void expandMultilineElement(TemplateElement el,
                                               List<PdfImageExtractionService.VectorRegion> vectors,
                                               List<PdfTextRegion> regions) {
        if (el == null) return;
        double cx = el.getX() + el.getWidth() / 2.0;
        double cy = el.getY() + el.getHeight() / 2.0;
        Optional<PdfImageExtractionService.VectorRegion> container = (vectors == null ? List.<PdfImageExtractionService.VectorRegion>of() : vectors).stream()
                .filter(v -> cx >= v.x() - 1 && cx <= v.x() + v.width() + 1 && cy >= v.y() - 1 && cy <= v.y() + v.height() + 1)
                .filter(v -> v.width() >= el.getWidth() * 0.75 && v.height() >= el.getHeight())
                .min(Comparator.comparingDouble(v -> v.width() * v.height()));
        if (container.isPresent()) {
            double availWidth = Math.max(el.getWidth(), container.get().x() + container.get().width() - 4.0 - el.getX());
            double availHeight = Math.max(el.getHeight(), container.get().y() + container.get().height() - 4.0 - el.getY());
            el.setWidth(availWidth);
            el.setHeight(Math.max(36.0, availHeight));
        } else if (regions != null && !regions.isEmpty()) {
            double blockWidth = regions.stream()
                    .filter(r -> Math.abs(r.x() - el.getX()) <= 15.0 && r.y() >= el.getY() && r.y() <= el.getY() + 80.0)
                    .mapToDouble(PdfTextRegion::width)
                    .max().orElse(el.getWidth());
            double totalSpanY = regions.stream()
                    .filter(r -> Math.abs(r.x() - el.getX()) <= 15.0 && r.y() >= el.getY() && r.y() <= el.getY() + 80.0)
                    .mapToDouble(r -> r.y() + r.height() - el.getY())
                    .max().orElse(el.getHeight());
            el.setWidth(Math.max(el.getWidth(), blockWidth));
            el.setHeight(Math.max(Math.max(el.getHeight(), totalSpanY), 36.0));
        } else {
            if (el.getHeight() < 36.0) el.setHeight(36.0);
        }
    }

    private TemplateElement findTableElement() {
        if (template == null) return null;
        if (template.getElements() == null) template.setElements(new ArrayList<>());
        for (TemplateElement el : template.getElements()) {
            if (el != null && el.getType() == ElementType.ITEM_TABLE) {
                el.setUseSourceTableDesign(true);
                if (el.getTableColumnBindings() == null || el.getTableColumnBindings().isEmpty()) {
                    try {
                        Path sourcePdf = TemplateStorageService.sourcePdf(template);
                        if (sourcePdf != null && Files.isRegularFile(sourcePdf)) {
                            var detection = PdfSourceTableDetectionService.detectItemTable(sourcePdf, el.getPageIndex());
                            if (detection.isPresent()) {
                                el.setTableColumnBindings(detection.get().table().getTableColumnBindings());
                                el.setHeaderHeight(detection.get().table().getHeaderHeight());
                                el.setRowHeight(detection.get().table().getRowHeight());
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return el;
            }
        }
        // Auto-detect table if missing and sourcePdf exists
        try {
            Path sourcePdf = TemplateStorageService.sourcePdf(template);
            if (sourcePdf != null && Files.isRegularFile(sourcePdf)) {
                var detection = PdfSourceTableDetectionService.detectItemTable(sourcePdf, 0);
                if (detection.isPresent()) {
                    TemplateElement table = detection.get().table();
                    table.setUseSourceTableDesign(true);
                    template.getElements().add(table);
                    return table;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void commitChanges() {
        if (onCommit != null) {
            onCommit.run();
        }
    }

    private static String sourceKey(PdfTextRegion r) {
        if (r == null) return "";
        return "PDF_TEXT|" + r.pageIndex() + "|" + Math.round(r.x() * 10.0) / 10.0 + "|" + Math.round(r.y() * 10.0) / 10.0 + "|" + Math.round(r.width() * 10.0) / 10.0 + "|" + Math.round(r.height() * 10.0) / 10.0 + "|" + r.text().trim().toLowerCase(Locale.ROOT);
    }
}
