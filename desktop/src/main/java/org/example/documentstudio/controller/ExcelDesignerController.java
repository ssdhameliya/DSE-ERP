package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.Cursor;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.example.controller.DashboardController;
import org.example.documentstudio.model.ExcelTemplate;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateData;
import org.example.documentstudio.model.DocumentSample;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.example.documentstudio.service.ExcelTemplateRenderer;
import org.example.documentstudio.service.ExcelWorkbookHistory;
import org.example.documentstudio.service.ExcelSelectionPolicy;
import org.example.documentstudio.service.ExcelDimensionPolicy;
import org.example.documentstudio.service.DocumentDataService;
import org.example.documentstudio.service.ExcelTemplateStorageService;
import org.example.documentstudio.service.TemplateFieldCatalog;
import org.example.util.IconFactory;
import org.example.util.ModernDialog;
import org.example.shortcut.ShortcutRegistry;
import org.example.shortcut.ShortcutRegistry.Action;
import org.example.navigation.NavigationGuardRegistry;
import org.example.shared.DocumentCalculationEngine;
import org.example.theme.ThemeManager;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Native JavaFX ERP workbook designer. The editor behaves like a spreadsheet:
 * click/drag and Shift extend a rectangular selection, arrows/Enter/Tab navigate, and F2/double-click/typing edits.
 * Workbook history is kept in memory so formatting, structure and mapping operations can undo/redo.
 */
public class ExcelDesignerController {
    private static final int VISIBLE_ROWS = 100;
    private static final int VISIBLE_COLS = 26; // A..Z
    private static final int HISTORY_LIMIT = 60;
    private static final Pattern ERP_TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");
    private boolean dirty;
    private static final Pattern FORMULA_CELL_REF = Pattern.compile("(?i)(?:'[^']+'!|[A-Za-z_][A-Za-z0-9_.]*!)?\\$?([A-Z]{1,3})\\$?([0-9]+)(?![A-Za-z0-9_])");
    private static final Pattern FORMULA_STRING_LITERAL = Pattern.compile("\"(?:[^\"]|\"\")*\"");
    private static final Pattern GRAND_TOTAL_LABEL = Pattern.compile("(?i).*\\b(grand\\s*total|net\\s*total|invoice\\s*total|total\\s*amount|amount\\s*payable)\\b.*");
    private static final List<String> FONT_NAMES = List.of("Aptos", "Calibri", "Arial", "Segoe UI", "Times New Roman", "Courier New");
    private static final List<Integer> FONT_SIZES = List.of(8,9,10,11,12,14,16,18,20,22,24,28,32,36,48,60,72);
    private static final List<String> NUMBER_FORMATS = List.of("General", "0", "0.00", "#,##0.00", "₹ #,##0.00", "0.00%", "dd-mm-yyyy", "dd-mmm-yyyy");

    @FXML private BorderPane root;
    @FXML private StackPane pageIcon;
    @FXML private Label lblName, lblType, lblVersion, lblCell, lblFormulaCell, lblFontColorState, lblFillColorState, lblBorderState, lblMergeState, lblMappingSummary, lblPreviewRecord;
    @FXML private Button btnUndo, btnRedo, btnBold, btnItalic, btnUnderline, btnAlignLeft, btnAlignCenter, btnAlignRight, btnWrap, btnNoFill, btnInsertItemRow, btnInsertChargeRow, btnCopyFormat, btnPasteFormat;
    @FXML private MenuButton btnBorders;
    @FXML private SplitMenuButton btnMerge;
    @FXML private ColorPicker pickerFontColor, pickerFillColor;
    @FXML private ComboBox<String> cmbSheet, cmbFont, cmbNumberFormat;
    @FXML private ComboBox<DocumentSample> cmbPreviewRecord;
    @FXML private ComboBox<Integer> cmbFontSize;
    @FXML private ListView<TemplateFieldDefinition> fieldList;
    @FXML private TextField txtFieldSearch, txtRange, txtFormula;
    @FXML private GridPane cellGrid;
    @FXML private HBox boxRecordPreview;

    private ExcelTemplate template;
    private Workbook workbook;
    private TextField focusedEditor;
    private int focusedRow = -1, focusedCol = -1;
    private int selectionAnchorRow = -1, selectionAnchorCol = -1, selectionEndRow = -1, selectionEndCol = -1;
    private String renderedSheetName;
    private final Map<String, TextField> editors = new HashMap<>();
    private final Map<String, CellRangeAddress> editorRegions = new HashMap<>();
    private final Map<String, String> editorBaseStyles = new HashMap<>();
    private final Map<Integer, Label> columnHeaders = new HashMap<>();
    private final Map<Integer, Label> rowHeaders = new HashMap<>();
    private int resizingColumn = -1, resizingRow = -1;
    private double resizeStartScreen = 0, resizeStartPixels = 0;
    private CellSnapshot clipboardCell;
    private FormatClipboard formatClipboard;
    private boolean updatingFormatControls;
    private boolean restoringHistory;
    private final ExcelWorkbookHistory history = new ExcelWorkbookHistory(HISTORY_LIMIT);
    private final Map<String, List<String>> mappedFieldAddresses = new LinkedHashMap<>();
    private final List<String> unknownWorkbookTokens = new ArrayList<>();
    private TemplateData selectedPreviewData;

    private record CellSnapshot(CellType type, String text, double number, boolean bool, String formula, CellStyle style) { }
    private record FormatClipboard(int rows,int cols,List<List<CellStyle>> styles) { }

    @FXML public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("excel", 24));
        installButtonIcons();
        String id = ExcelStudioContext.consume();
        if (id == null) {
            ModernDialog.error(root, "Excel Studio", "No Excel template was selected", "Return to Excel Studio and choose an Excel template.");
            return;
        }
        try {
            template = ExcelTemplateStorageService.find(id).orElseThrow(() -> new IOException("Excel template was not found."));
            workbook = ExcelTemplateStorageService.openWorkbookDetached(template);
            lblName.setText(template.getName());
            lblType.setText(template.getDocumentType().label());
            lblVersion.setText("v" + template.getVersion());

            cmbSheet.getItems().setAll(sheetNames());
            if (!cmbSheet.getItems().isEmpty()) cmbSheet.getSelectionModel().selectFirst();
            cmbSheet.valueProperty().addListener((o,a,b) -> renderSheet());

            cmbFont.setItems(FXCollections.observableArrayList(FONT_NAMES));
            cmbFontSize.setItems(FXCollections.observableArrayList(FONT_SIZES));
            cmbNumberFormat.setItems(FXCollections.observableArrayList(NUMBER_FORMATS));
            pickerFontColor.setValue(Color.BLACK);
            pickerFillColor.setValue(Color.TRANSPARENT);

            configureFieldPalette();
            configureRepeatingRowTools();
            configureRecordPreview();
            txtFieldSearch.textProperty().addListener((o,a,b) -> filterFields(b));
            root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalShortcut);
            renderSheet();
            IconFactory.decorate(root);
            Platform.runLater(() -> {
                preserveAllButtonLabels(root);
                NavigationGuardRegistry.install(root, this::allowNavigationAway);
            });
            updateUndoRedoButtons();
            updateFormatClipboardButtons();
        } catch (Exception error) {
            ModernDialog.error(root, "Excel template could not be opened", "Excel Studio", rootMessage(error));
        }
    }

    private void installButtonIcons() {
        if (btnUndo != null) btnUndo.setGraphic(IconFactory.compactIcon("undo", 14));
        if (btnRedo != null) btnRedo.setGraphic(IconFactory.compactIcon("redo", 14));
        if (btnCopyFormat != null) btnCopyFormat.setGraphic(IconFactory.compactIcon("copy", 14));
        if (btnPasteFormat != null) btnPasteFormat.setGraphic(IconFactory.compactIcon("check", 14));
    }

    /** Prevent HBox layout pressure or display scaling from clipping any Excel Studio button label. */
    private void preserveAllButtonLabels(Node node) {
        if (node == null) return;
        if (node instanceof ButtonBase button && button.getText() != null && !button.getText().isBlank()) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) preserveAllButtonLabels(child);
    }

    private void handleGlobalShortcut(KeyEvent event) {
        if (event == null || event.isConsumed()) return;
        if (ShortcutRegistry.matches(event, Action.EXCEL_REDO_ALT) || ShortcutRegistry.matches(event, Action.EXCEL_REDO)) {
            redo(); event.consume(); return;
        }
        if (ShortcutRegistry.matches(event, Action.EXCEL_UNDO)) { undo(); event.consume(); return; }
        if (handleWorkspaceArrowNavigation(event)) event.consume();
    }

    /**
     * Formatting buttons legitimately take JavaFX focus when clicked. Keep arrow keys
     * spreadsheet-owned in that case so the active selection remains keyboard navigable,
     * while controls that use arrows themselves (text inputs, combo boxes, menus and lists)
     * retain their native JavaFX keyboard behavior.
     */
    private boolean handleWorkspaceArrowNavigation(KeyEvent event) {
        if (focusedRow < 0 || focusedCol < 0 || focusedEditor == null || focusedEditor.isEditable()) return false;
        KeyCode code = event.getCode();
        if (code != KeyCode.UP && code != KeyCode.DOWN && code != KeyCode.LEFT && code != KeyCode.RIGHT) return false;
        Object target = event.getTarget();
        if (target == focusedEditor || target instanceof TextInputControl || target instanceof ComboBoxBase<?>
                || target instanceof MenuButton || target instanceof ListView<?> || target instanceof TableView<?>
                || target instanceof TreeView<?> || target instanceof ScrollBar || target instanceof Slider) return false;
        switch (code) {
            case UP -> { if (event.isShiftDown()) extendSelection(-1, 0); else moveSelection(-1, 0); }
            case DOWN -> { if (event.isShiftDown()) extendSelection(1, 0); else moveSelection(1, 0); }
            case LEFT -> { if (event.isShiftDown()) extendSelection(0, -1); else moveSelection(0, -1); }
            case RIGHT -> { if (event.isShiftDown()) extendSelection(0, 1); else moveSelection(0, 1); }
            default -> { return false; }
        }
        return true;
    }

    private void configureFieldPalette() {
        fieldList.setItems(FXCollections.observableArrayList(TemplateFieldCatalog.excelFieldsFor(template.getDocumentType())));
        fieldList.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(TemplateFieldDefinition field, boolean empty) {
                super.updateItem(field, empty);
                if (empty || field == null) { setText(null); setGraphic(null); return; }
                List<String> addresses = mappedFieldAddresses.getOrDefault(field.key(), List.of());
                String mapping = addresses.isEmpty() ? "○ Not mapped" : "✓ " + String.join(", ", addresses.stream().limit(3).toList()) + (addresses.size() > 3 ? "…" : "");
                setText(field.category() + " • " + field.label() + "   " + mapping);
                setWrapText(true);
                setMaxWidth(Double.MAX_VALUE);
                setGraphic(IconFactory.compactIcon(field.image() ? "image" : (addresses.isEmpty() ? "document" : "check"), 14));
                String current = previewValue(field.key());
                String valueLine = current.isBlank() ? "" : "\nCurrent preview value: " + current;
                setTooltip(new Tooltip((field.image() ? "Image placeholder: " : "Field: ") + "{{" + field.key() + "}}" + valueLine));
            }
        });
        fieldList.setOnMouseClicked(e -> { if (e.getClickCount() == 2) insertField(); });
        fieldList.setOnDragDetected(e -> {
            TemplateFieldDefinition field = fieldList.getSelectionModel().getSelectedItem();
            if (field == null) return;
            Dragboard board = fieldList.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(field.key());
            board.setContent(content);
            e.consume();
        });
    }

    private void configureRepeatingRowTools() {
        DocumentType type = template == null ? null : template.getDocumentType();
        boolean items = TemplateFieldCatalog.supportsItemRows(type);
        boolean charges = TemplateFieldCatalog.supportsChargeRows(type);
        if (btnInsertItemRow != null) { btnInsertItemRow.setVisible(items); btnInsertItemRow.setManaged(items); }
        if (btnInsertChargeRow != null) { btnInsertChargeRow.setVisible(charges); btnInsertChargeRow.setManaged(charges); }
    }

    private List<String> sheetNames() {
        List<String> names = new ArrayList<>();
        for (int i=0; i<workbook.getNumberOfSheets(); i++) names.add(workbook.getSheetName(i));
        return names;
    }

    private Sheet activeSheet() {
        String name = cmbSheet.getValue();
        return name == null ? workbook.getSheetAt(0) : workbook.getSheet(name);
    }

    private Sheet editorSheet() {
        Sheet sheet = renderedSheetName == null ? null : workbook.getSheet(renderedSheetName);
        return sheet == null ? activeSheet() : sheet;
    }

    private void filterFields(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        fieldList.getItems().setAll(TemplateFieldCatalog.excelFieldsFor(template.getDocumentType()).stream()
                .filter(f -> q.isBlank() || f.label().toLowerCase(Locale.ROOT).contains(q)
                        || f.key().toLowerCase(Locale.ROOT).contains(q)
                        || f.category().toLowerCase(Locale.ROOT).contains(q))
                .toList());
    }

    private void renderSheet() {
        if (workbook == null || cmbSheet.getValue() == null) return;
        if (!restoringHistory) saveVisibleCells(false);
        int restoreRow = focusedRow;
        int restoreCol = focusedCol;
        int restoreAnchorRow = selectionAnchorRow;
        int restoreAnchorCol = selectionAnchorCol;
        int restoreEndRow = selectionEndRow;
        int restoreEndCol = selectionEndCol;

        cellGrid.getChildren().clear();
        cellGrid.getColumnConstraints().clear();
        cellGrid.getRowConstraints().clear();
        editors.clear();
        editorRegions.clear();
        editorBaseStyles.clear();
        columnHeaders.clear();
        rowHeaders.clear();
        focusedEditor = null;

        Sheet sheet = activeSheet();
        renderedSheetName = sheet.getSheetName();

        ColumnConstraints rowHeaderColumn = fixedColumn(54);
        cellGrid.getColumnConstraints().add(rowHeaderColumn);
        for (int c = 0; c < VISIBLE_COLS; c++) cellGrid.getColumnConstraints().add(fixedColumn(ExcelDimensionPolicy.columnWidthPixels(sheet, c)));
        cellGrid.getRowConstraints().add(fixedRow(28));
        for (int r = 0; r < VISIBLE_ROWS; r++) cellGrid.getRowConstraints().add(fixedRow(ExcelDimensionPolicy.rowHeightPixels(sheet, r)));

        Label corner = new Label("");
        corner.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        corner.getStyleClass().add("excel-grid-header");
        corner.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                setSelection(new CellRangeAddress(0, VISIBLE_ROWS - 1, 0, VISIBLE_COLS - 1), 0, 0);
                requestEditorFocus(0, 0);
            }
        });
        cellGrid.add(corner, 0, 0);

        for (int c = 0; c < VISIBLE_COLS; c++) {
            Label h = new Label(columnName(c));
            h.setAlignment(Pos.CENTER);
            h.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            h.getStyleClass().add("excel-grid-header");
            final int cc = c;
            configureColumnHeader(h, cc, sheet);
            columnHeaders.put(cc, h);
            cellGrid.add(h, c + 1, 0);
        }

        for (int r = 0; r < VISIBLE_ROWS; r++) {
            Label rh = new Label(Integer.toString(r + 1));
            rh.setAlignment(Pos.CENTER_RIGHT);
            rh.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            rh.getStyleClass().add("excel-grid-row-header");
            final int rr = r;
            configureRowHeader(rh, rr, sheet);
            rowHeaders.put(rr, rh);
            cellGrid.add(rh, 0, r + 1);

            Row row = sheet.getRow(r);
            for (int c = 0; c < VISIBLE_COLS; c++) {
                CellRangeAddress merged = mergedRegionAt(sheet, r, c);
                if (merged != null && (merged.getFirstRow() != r || merged.getFirstColumn() != c)) continue;

                Cell cell = row == null ? null : row.getCell(c);
                TextField editor = new TextField(cellText(cell));
                editor.getStyleClass().add("excel-cell-editor");
                editor.setMinSize(0, 0);
                editor.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                editor.setEditable(false);

                int rowIndex = r, colIndex = c;
                CellRangeAddress visualRegion = merged == null
                        ? new CellRangeAddress(r, r, c, c)
                        : copyRange(merged);

                editor.focusedProperty().addListener((o, a, b) -> {
                    if (b) adoptFocusedEditor(editor, rowIndex, colIndex);
                    else if (editor.isEditable()) endEdit(editor, rowIndex, colIndex, true);
                });
                editor.setOnMousePressed(e -> {
                    if (e.getButton() != MouseButton.PRIMARY || editor.isEditable()) return;
                    if (e.isShiftDown()) extendSelectionTo(visualRegion);
                    else setSelection(visualRegion, rowIndex, colIndex);
                    adoptFocusedEditor(editor, rowIndex, colIndex);
                });
                editor.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 2) beginEdit(editor, false, null);
                });
                editor.setOnDragDetected(e -> {
                    if (!editor.isEditable()) {
                        editor.startFullDrag();
                        e.consume();
                    }
                });
                editor.setOnMouseDragEntered(e -> {
                    if (!editor.isEditable() && e.isPrimaryButtonDown()) {
                        extendSelectionTo(visualRegion);
                        adoptFocusedEditor(editor, rowIndex, colIndex);
                    }
                });
                editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> handleCellKeyPressed(editor, rowIndex, colIndex, e));
                editor.addEventFilter(KeyEvent.KEY_TYPED, e -> handleCellKeyTyped(editor, e));
                configureCellDrop(editor, rowIndex, colIndex);
                applyEditorVisual(cell, editor);

                String key = rowIndex + ":" + colIndex;
                editors.put(key, editor);
                editorRegions.put(key, visualRegion);
                editorBaseStyles.put(key, editor.getStyle());
                cellGrid.add(editor, colIndex + 1, rowIndex + 1);
                if (visualRegion.getNumberOfCells() > 1) {
                    GridPane.setColumnSpan(editor, Math.min(VISIBLE_COLS - 1, visualRegion.getLastColumn()) - visualRegion.getFirstColumn() + 1);
                    GridPane.setRowSpan(editor, Math.min(VISIBLE_ROWS - 1, visualRegion.getLastRow()) - visualRegion.getFirstRow() + 1);
                    editor.getStyleClass().add("excel-cell-merged");
                }
            }
        }

        int rr = restoreRow < 0 ? 0 : Math.min(restoreRow, VISIBLE_ROWS - 1);
        int cc = restoreCol < 0 ? 0 : Math.min(restoreCol, VISIBLE_COLS - 1);
        focusedRow = rr;
        focusedCol = cc;
        if (restoreAnchorRow >= 0 && restoreAnchorCol >= 0 && restoreEndRow >= 0 && restoreEndCol >= 0) {
            selectionAnchorRow = clampRow(restoreAnchorRow);
            selectionAnchorCol = clampCol(restoreAnchorCol);
            selectionEndRow = clampRow(restoreEndRow);
            selectionEndCol = clampCol(restoreEndCol);
        } else {
            CellRangeAddress region = regionForCell(sheet, rr, cc);
            selectionAnchorRow = region.getFirstRow();
            selectionAnchorCol = region.getFirstColumn();
            selectionEndRow = region.getLastRow();
            selectionEndCol = region.getLastColumn();
        }
        refreshSelectionUi();
        refreshMappingUi();
        Platform.runLater(() -> requestEditorFocus(focusedRow, focusedCol));
    }

    private ColumnConstraints fixedColumn(double pixels) {
        double width=Math.max(24,Math.min(1800,pixels));
        ColumnConstraints constraint=new ColumnConstraints(width,width,width);
        constraint.setHgrow(Priority.NEVER);
        return constraint;
    }

    private RowConstraints fixedRow(double pixels) {
        double height=Math.max(18,Math.min(560,pixels));
        RowConstraints constraint=new RowConstraints(height,height,height);
        constraint.setVgrow(Priority.NEVER);
        return constraint;
    }

    /** Excel stores widths in 1/256 character units; this keeps the JavaFX grid visually aligned with the workbook. */
    private void configureColumnHeader(Label header,int col,Sheet sheet) {
        header.setOnMouseMoved(e->header.setCursor(e.getX()>=Math.max(0,header.getWidth()-7)?Cursor.H_RESIZE:Cursor.DEFAULT));
        header.setOnMouseExited(e->{if(resizingColumn!=col)header.setCursor(Cursor.DEFAULT);});
        header.setOnMousePressed(e->{
            if(e.getButton()!=MouseButton.PRIMARY)return;
            if(e.getX()>=Math.max(0,header.getWidth()-7)){
                commitActiveEdit();saveVisibleCells(false);recordUndoPoint();
                resizingColumn=col;resizingRow=-1;resizeStartScreen=e.getScreenX();resizeStartPixels=ExcelDimensionPolicy.columnWidthPixels(sheet,col);
                header.setCursor(Cursor.H_RESIZE);e.consume();
                return;
            }
            selectColumnHeader(col,e.isShiftDown());
            e.consume();
        });
        header.setOnMouseDragged(e->{
            if(resizingColumn!=col)return;
            double pixels=Math.max(24,Math.min(1800,resizeStartPixels+(e.getScreenX()-resizeStartScreen)));
            sheet.setColumnWidth(col,ExcelDimensionPolicy.pixelsToColumnWidth(pixels));
            ColumnConstraints constraint=cellGrid.getColumnConstraints().get(col+1);
            double actual=ExcelDimensionPolicy.columnWidthPixels(sheet,col);constraint.setMinWidth(actual);constraint.setPrefWidth(actual);constraint.setMaxWidth(actual);
            e.consume();
        });
        header.setOnMouseReleased(e->{if(resizingColumn==col){resizingColumn=-1;header.setCursor(Cursor.DEFAULT);refreshSelectionUi();e.consume();}});
        header.setOnMouseClicked(e->{
            if(e.getButton()==MouseButton.PRIMARY&&e.getClickCount()>=2&&e.getX()>=Math.max(0,header.getWidth()-9)){
                autoFitColumn(col);e.consume();
            }
        });
    }

    private void configureRowHeader(Label header,int row,Sheet sheet) {
        header.setOnMouseMoved(e->header.setCursor(e.getY()>=Math.max(0,header.getHeight()-6)?Cursor.V_RESIZE:Cursor.DEFAULT));
        header.setOnMouseExited(e->{if(resizingRow!=row)header.setCursor(Cursor.DEFAULT);});
        header.setOnMousePressed(e->{
            if(e.getButton()!=MouseButton.PRIMARY)return;
            if(e.getY()>=Math.max(0,header.getHeight()-6)){
                commitActiveEdit();saveVisibleCells(false);recordUndoPoint();
                resizingRow=row;resizingColumn=-1;resizeStartScreen=e.getScreenY();resizeStartPixels=ExcelDimensionPolicy.rowHeightPixels(sheet,row);
                header.setCursor(Cursor.V_RESIZE);e.consume();
                return;
            }
            selectRowHeader(row,e.isShiftDown());
            e.consume();
        });
        header.setOnMouseDragged(e->{
            if(resizingRow!=row)return;
            double pixels=Math.max(18,Math.min(560,resizeStartPixels+(e.getScreenY()-resizeStartScreen)));
            Row target=sheet.getRow(row);if(target==null)target=sheet.createRow(row);target.setHeightInPoints(ExcelDimensionPolicy.pixelsToRowPoints(pixels));
            RowConstraints constraint=cellGrid.getRowConstraints().get(row+1);
            double actual=ExcelDimensionPolicy.rowHeightPixels(sheet,row);constraint.setMinHeight(actual);constraint.setPrefHeight(actual);constraint.setMaxHeight(actual);
            e.consume();
        });
        header.setOnMouseReleased(e->{if(resizingRow==row){resizingRow=-1;header.setCursor(Cursor.DEFAULT);refreshSelectionUi();e.consume();}});
        header.setOnMouseClicked(e->{
            if(e.getButton()==MouseButton.PRIMARY&&e.getClickCount()>=2&&e.getY()>=Math.max(0,header.getHeight()-8)){
                autoFitRow(row);e.consume();
            }
        });
    }

    private void selectColumnHeader(int col,boolean extend) {
        int activeRow=clampRow(focusedRow<0?0:focusedRow);
        if(extend&&selectionAnchorCol>=0){
            int anchor=selectionAnchorCol;
            setSelection(new CellRangeAddress(0,VISIBLE_ROWS-1,Math.min(anchor,col),Math.max(anchor,col)),activeRow,col);
        }else setSelection(new CellRangeAddress(0,VISIBLE_ROWS-1,col,col),activeRow,col);
        requestEditorFocus(activeRow,col);
    }

    private void selectRowHeader(int row,boolean extend) {
        int activeCol=clampCol(focusedCol<0?0:focusedCol);
        if(extend&&selectionAnchorRow>=0){
            int anchor=selectionAnchorRow;
            setSelection(new CellRangeAddress(Math.min(anchor,row),Math.max(anchor,row),0,VISIBLE_COLS-1),row,activeCol);
        }else setSelection(new CellRangeAddress(row,row,0,VISIBLE_COLS-1),row,activeCol);
        requestEditorFocus(row,activeCol);
    }

    private void autoFitColumn(int col) {
        try{
            commitActiveEdit();saveVisibleCells(false);recordUndoPoint();
            editorSheet().autoSizeColumn(col);
            if(editorSheet().getColumnWidth(col)<256)editorSheet().setColumnWidth(col,256);
            renderSheet();
        }catch(Exception error){ModernDialog.error(root,"Column could not be AutoFit","Excel Studio",rootMessage(error));}
    }

    private void autoFitRow(int rowIndex) {
        try{
            commitActiveEdit();saveVisibleCells(false);recordUndoPoint();
            Sheet sheet=editorSheet();Row row=sheet.getRow(rowIndex);if(row==null)row=sheet.createRow(rowIndex);
            row.setHeightInPoints(ExcelDimensionPolicy.estimateAutoRowHeightPoints(sheet,rowIndex,this::cellText));
            renderSheet();
        }catch(Exception error){ModernDialog.error(root,"Row could not be AutoFit","Excel Studio",rootMessage(error));}
    }

    private void handleCellKeyPressed(TextField editor, int row, int col, KeyEvent event) {
        if (!editor.isEditable()) {
            if (ShortcutRegistry.matches(event, Action.EXCEL_COPY)) { copyCell(); event.consume(); return; }
            if (ShortcutRegistry.matches(event, Action.EXCEL_PASTE)) { pasteCell(); event.consume(); return; }
            // Undo/redo are handled once at the Excel Studio root so they are not executed twice.
            if (ShortcutRegistry.matches(event, Action.EXCEL_UNDO) || ShortcutRegistry.matches(event, Action.EXCEL_REDO)
                    || ShortcutRegistry.matches(event, Action.EXCEL_REDO_ALT)) return;
            if (ShortcutRegistry.matches(event, Action.EXCEL_EDIT)) { beginEdit(editor, false, null); event.consume(); return; }
            if (ShortcutRegistry.matches(event, Action.EXCEL_CLEAR)) { clearSelectedCells(); event.consume(); return; }
        }
        if (editor.isEditable()) {
            if (event.getCode() == KeyCode.ESCAPE) { cancelEdit(editor, row, col); event.consume(); return; }
            if (event.getCode() == KeyCode.ENTER) { endEdit(editor, row, col, true); moveSelection(event.isShiftDown() ? -1 : 1, 0); event.consume(); return; }
            if (event.getCode() == KeyCode.TAB) { endEdit(editor, row, col, true); moveSelection(0, event.isShiftDown() ? -1 : 1); event.consume(); }
            return;
        }
        // Arrow/Enter/Tab are spreadsheet navigation semantics, not command shortcuts.
        switch (event.getCode()) {
            case UP -> { if (event.isShiftDown()) extendSelection(-1, 0); else moveSelection(-1, 0); event.consume(); }
            case DOWN -> { if (event.isShiftDown()) extendSelection(1, 0); else moveSelection(1, 0); event.consume(); }
            case LEFT -> { if (event.isShiftDown()) extendSelection(0, -1); else moveSelection(0, -1); event.consume(); }
            case RIGHT -> { if (event.isShiftDown()) extendSelection(0, 1); else moveSelection(0, 1); event.consume(); }
            case ENTER -> { moveSelection(event.isShiftDown() ? -1 : 1, 0); event.consume(); }
            case TAB -> { moveSelection(0, event.isShiftDown() ? -1 : 1); event.consume(); }
            default -> { }
        }
    }

    private void handleCellKeyTyped(TextField editor, KeyEvent event) {
        if (editor.isEditable() || event.isControlDown() || event.isMetaDown() || event.isAltDown()) return;
        String text = event.getCharacter();
        if (text == null || text.isEmpty() || text.chars().allMatch(Character::isISOControl)) return;
        beginEdit(editor, true, text);
        event.consume();
    }

    private void beginEdit(TextField editor, boolean replace, String initial) {
        if (editor == null) return;
        editor.setEditable(true);
        if (!editor.getStyleClass().contains("excel-cell-editing")) editor.getStyleClass().add("excel-cell-editing");
        if (replace) editor.setText(initial == null ? "" : initial);
        editor.requestFocus();
        editor.positionCaret(editor.getText() == null ? 0 : editor.getText().length());
    }

    private void endEdit(TextField editor, int row, int col, boolean trackHistory) {
        if (editor == null) return;
        commitCell(row, col, editor, trackHistory);
        refreshMappingUi();
        editor.setEditable(false);
        editor.getStyleClass().remove("excel-cell-editing");
        syncFormulaBar();
    }

    private void cancelEdit(TextField editor, int row, int col) {
        Cell cell = cellAt(editorSheet(), row, col, false);
        editor.setText(cellText(cell));
        editor.setEditable(false);
        editor.getStyleClass().remove("excel-cell-editing");
        syncFormulaBar();
    }

    private void commitActiveEdit() {
        if (focusedEditor != null && focusedEditor.isEditable()) endEdit(focusedEditor, focusedRow, focusedCol, true);
    }

    private void moveSelection(int rowDelta, int colDelta) {
        CellRangeAddress currentMerged = mergedRegionAt(editorSheet(), focusedRow, focusedCol);
        int baseRow = focusedRow;
        int baseCol = focusedCol;
        if (currentMerged != null) {
            if (rowDelta > 0) baseRow = currentMerged.getLastRow();
            else if (rowDelta < 0) baseRow = currentMerged.getFirstRow();
            if (colDelta > 0) baseCol = currentMerged.getLastColumn();
            else if (colDelta < 0) baseCol = currentMerged.getFirstColumn();
        }
        focusCell(clampRow(baseRow + rowDelta), clampCol(baseCol + colDelta), true);
    }

    private void extendSelection(int rowDelta, int colDelta) {
        int row = clampRow(selectionEndRow + rowDelta);
        int col = clampCol(selectionEndCol + colDelta);
        CellRangeAddress region = regionForCell(editorSheet(), row, col);
        extendSelectionTo(region);
        requestEditorFocus(region.getFirstRow(), region.getFirstColumn());
    }

    private void extendSelectionTo(CellRangeAddress targetRegion) {
        if (selectionAnchorRow < 0 || selectionAnchorCol < 0) {
            setSelection(targetRegion, targetRegion.getFirstRow(), targetRegion.getFirstColumn());
            return;
        }
        selectionEndRow = selectionAnchorRow <= targetRegion.getFirstRow() ? targetRegion.getLastRow() : targetRegion.getFirstRow();
        selectionEndCol = selectionAnchorCol <= targetRegion.getFirstColumn() ? targetRegion.getLastColumn() : targetRegion.getFirstColumn();
        focusedRow = targetRegion.getFirstRow();
        focusedCol = targetRegion.getFirstColumn();
        refreshSelectionUi();
    }

    private void focusCell(int row, int col, boolean collapseSelection) {
        CellRangeAddress region = regionForCell(editorSheet(), clampRow(row), clampCol(col));
        if (collapseSelection) setSelection(region, region.getFirstRow(), region.getFirstColumn());
        else {
            focusedRow = region.getFirstRow();
            focusedCol = region.getFirstColumn();
            refreshSelectionUi();
        }
        requestEditorFocus(region.getFirstRow(), region.getFirstColumn());
    }

    private void setFocusedCell(TextField editor, int row, int col, boolean collapseSelection) {
        if (collapseSelection) setSelection(regionForCell(editorSheet(), row, col), row, col);
        adoptFocusedEditor(editor, row, col);
    }

    private void adoptFocusedEditor(TextField editor, int row, int col) {
        focusedEditor = editor;
        focusedRow = row;
        focusedCol = col;
        if (selectionAnchorRow < 0) setSelection(regionForCell(editorSheet(), row, col), row, col);
        else refreshSelectionUi();
    }

    private void setSelection(CellRangeAddress range, int activeRow, int activeCol) {
        selectionAnchorRow = clampRow(range.getFirstRow());
        selectionAnchorCol = clampCol(range.getFirstColumn());
        selectionEndRow = clampRow(range.getLastRow());
        selectionEndCol = clampCol(range.getLastColumn());
        focusedRow = clampRow(activeRow);
        focusedCol = clampCol(activeCol);
        refreshSelectionUi();
    }

    private void requestEditorFocus(int row, int col) {
        TextField editor = editorForCell(row, col);
        if (editor == null) return;
        focusedEditor = editor;
        editor.requestFocus();
    }

    private TextField editorForCell(int row, int col) {
        TextField direct = editors.get(row + ":" + col);
        if (direct != null) return direct;
        CellRangeAddress merged = mergedRegionAt(editorSheet(), row, col);
        return merged == null ? null : editors.get(merged.getFirstRow() + ":" + merged.getFirstColumn());
    }

    private void configureCellDrop(TextField editor, int row, int col) {
        editor.setOnDragOver(e -> { if (e.getGestureSource() != editor && e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.COPY); e.consume(); });
        editor.setOnDragDropped(e -> {
            Dragboard board = e.getDragboard();
            boolean ok = false;
            if (board.hasString()) {
                String key = board.getString();
                if (TemplateFieldCatalog.excelFieldsFor(template.getDocumentType()).stream().anyMatch(f -> f.key().equals(key))) {
                    setFocusedCell(editor, row, col, true);
                    editor.setText("{{" + key + "}}");
                    commitCell(row, col, editor, true);
                    refreshMappingUi();
                    markImagePlaceholder(editor, key);
                    ok = true;
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    private void refreshSelectionUi() {
        CellRangeAddress range = currentRange();
        for (var entry : editors.entrySet()) {
            TextField editor = entry.getValue();
            editor.getStyleClass().removeAll("excel-cell-selected", "excel-cell-range-selected");
            String base=editorBaseStyles.getOrDefault(entry.getKey(),editor.getStyle()==null?"":editor.getStyle());
            editor.setStyle(base);
            CellRangeAddress visualRegion = editorRegions.get(entry.getKey());
            if (visualRegion == null || !intersects(visualRegion, range)) continue;
            editor.getStyleClass().add("excel-cell-range-selected");
            boolean active=visualRegion.isInRange(focusedRow, focusedCol);
            if(active)editor.getStyleClass().add("excel-cell-selected");
            applySelectionOverlay(editor,visualRegion,range,active);
        }
        for(var entry:columnHeaders.entrySet()){
            entry.getValue().getStyleClass().remove("excel-grid-header-selected");
            if(entry.getKey()>=range.getFirstColumn()&&entry.getKey()<=range.getLastColumn())entry.getValue().getStyleClass().add("excel-grid-header-selected");
        }
        for(var entry:rowHeaders.entrySet()){
            entry.getValue().getStyleClass().remove("excel-grid-row-header-selected");
            if(entry.getKey()>=range.getFirstRow()&&entry.getKey()<=range.getLastRow())entry.getValue().getStyleClass().add("excel-grid-row-header-selected");
        }
        String address = columnName(Math.max(0, focusedCol)) + (Math.max(0, focusedRow) + 1);
        lblCell.setText(address);
        if (lblFormulaCell != null) lblFormulaCell.setText(address);
        if (txtRange != null) txtRange.setText(rangeText(range));
        syncFormulaBar();
        syncFormatControls(range);
    }

    /** Selection is drawn on top of the workbook style so the original fill/font remain visible underneath. */
    private void applySelectionOverlay(TextField editor,CellRangeAddress visual,CellRangeAddress range,boolean active){
        StringBuilder css=new StringBuilder(editor.getStyle()==null?"":editor.getStyle());
        css.append("-fx-effect:innershadow(gaussian,rgba(37,99,235,").append(active?"0.88":"0.48").append("),").append(active?"8":"5").append(",0.65,0,0);");
        if(active){
            css.append("-fx-border-color:#2563eb;-fx-border-width:2.6;-fx-border-style:solid;");
        }else{
            boolean top=visual.getFirstRow()<=range.getFirstRow()&&visual.getLastRow()>=range.getFirstRow();
            boolean right=visual.getFirstColumn()<=range.getLastColumn()&&visual.getLastColumn()>=range.getLastColumn();
            boolean bottom=visual.getFirstRow()<=range.getLastRow()&&visual.getLastRow()>=range.getLastRow();
            boolean left=visual.getFirstColumn()<=range.getFirstColumn()&&visual.getLastColumn()>=range.getFirstColumn();
            if(top||right||bottom||left){
                css.append("-fx-border-color:").append(top?"#2563eb":"transparent").append(' ').append(right?"#2563eb":"transparent").append(' ').append(bottom?"#2563eb":"transparent").append(' ').append(left?"#2563eb":"transparent").append(';');
                css.append("-fx-border-width:").append(top?"2":"0").append(' ').append(right?"2":"0").append(' ').append(bottom?"2":"0").append(' ').append(left?"2":"0").append(';');
                css.append("-fx-border-style:solid;");
            }
        }
        editor.setStyle(css.toString());
    }

    private CellRangeAddress regionForCell(Sheet sheet, int row, int col) {
        CellRangeAddress merged = mergedRegionAt(sheet, row, col);
        return merged == null ? new CellRangeAddress(row, row, col, col) : copyRange(merged);
    }

    private CellRangeAddress mergedRegionAt(Sheet sheet, int row, int col) {
        if (sheet == null) return null;
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress merged = sheet.getMergedRegion(i);
            if (merged.isInRange(row, col)) return merged;
        }
        return null;
    }

    private int clampRow(int row) { return ExcelSelectionPolicy.clamp(row, VISIBLE_ROWS); }
    private int clampCol(int col) { return ExcelSelectionPolicy.clamp(col, VISIBLE_COLS); }

    private void clearSelectedCells() {
        if (focusedRow < 0) return;
        commitActiveEdit();
        saveVisibleCells(false);
        CellRangeAddress range = currentRange();
        boolean anyValue = false;
        for (int r = range.getFirstRow(); r <= range.getLastRow() && !anyValue; r++) {
            for (int c = range.getFirstColumn(); c <= range.getLastColumn(); c++) {
                Cell cell = cellAt(editorSheet(), r, c, false);
                if (cell != null && cell.getCellType() != CellType.BLANK) { anyValue = true; break; }
            }
        }
        if (!anyValue) return;
        recordUndoPoint();
        for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
            for (int c = range.getFirstColumn(); c <= range.getLastColumn(); c++) {
                Cell cell = cellAt(editorSheet(), r, c, false);
                if (cell != null) cell.setBlank();
                TextField visible = editorForCell(r, c);
                if (visible != null && !visible.isEditable()) visible.setText("");
            }
        }
        syncFormulaBar();
        refreshMappingUi();
        refreshSelectionUi();
    }

    private void syncFormulaBar(){
        if(txtFormula==null||focusedRow<0||focusedCol<0)return;
        TextField editor=editors.get(focusedRow+":"+focusedCol);
        txtFormula.setText(editor==null?cellText(cellAt(editorSheet(),focusedRow,focusedCol,false)):editor.getText());
    }

    @FXML private void formulaCommit(){
        if(focusedRow<0)return;
        TextField editor=editors.get(focusedRow+":"+focusedCol);
        if(editor==null)return;
        editor.setText(txtFormula.getText()==null?"":txtFormula.getText());
        commitCell(focusedRow,focusedCol,editor,true);
        refreshMappingUi();
        editor.setEditable(false);
        editor.getStyleClass().remove("excel-cell-editing");
        editor.requestFocus();
    }

    @FXML private void rangeEntered(){
        if(txtRange==null)return;
        try{
            CellRangeAddress range=CellRangeAddress.valueOf(txtRange.getText().trim().toUpperCase(Locale.ROOT));
            if(range.getFirstRow()<0||range.getFirstColumn()<0||range.getLastRow()>=VISIBLE_ROWS||range.getLastColumn()>=VISIBLE_COLS)
                throw new IllegalArgumentException("Excel Studio currently displays A1:Z100.");
            setSelection(range,range.getFirstRow(),range.getFirstColumn());
            requestEditorFocus(range.getFirstRow(),range.getFirstColumn());
        }catch(Exception e){ModernDialog.info(root,"Invalid range","Excel Studio","Enter a visible cell or range such as A1 or A1:D4 (up to Z100).");}
    }

    private String cellText(Cell cell) {
        if(cell==null)return "";
        return switch(cell.getCellType()){
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)?cell.getLocalDateTimeCellValue().toLocalDate().toString():number(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> "="+cell.getCellFormula();
            default -> "";
        };
    }

    private void commitCell(int rowIndex,int colIndex,TextField editor,boolean trackHistory){
        Cell existing=cellAt(editorSheet(),rowIndex,colIndex,false);
        String before=cellText(existing);
        String value=editor.getText()==null?"":editor.getText();
        if(Objects.equals(before,value))return;
        Cell cell=cellAt(editorSheet(),rowIndex,colIndex,true);
        try{
            validateCellValue(value);
        }catch(IllegalArgumentException error){
            editor.setText(before);
            if(txtFormula!=null&&rowIndex==focusedRow&&colIndex==focusedCol)txtFormula.setText(before);
            ModernDialog.error(root,"Invalid Excel formula","Excel Studio",error.getMessage());
            return;
        }
        if(trackHistory)recordUndoPoint();
        writeCellValue(cell,value);
        applyEditorVisual(cell,editor);
        editorBaseStyles.put(rowIndex+":"+colIndex,editor.getStyle());
        updateUndoRedoButtons();
    }

    private void validateCellValue(String value){
        if(value==null||!value.startsWith("=")||value.length()<=1)return;
        String checkName="_DSE_FORMULA_CHECK_"+System.nanoTime();
        int checkIndex=-1;
        try{
            Sheet check=workbook.createSheet(checkName);
            checkIndex=workbook.getSheetIndex(check);
            check.createRow(0).createCell(0).setCellFormula(value.substring(1));
        }catch(Exception error){
            throw new IllegalArgumentException("The formula was not saved because Excel could not parse it: "+rootMessage(error),error);
        }finally{
            if(checkIndex>=0&&checkIndex<workbook.getNumberOfSheets())workbook.removeSheetAt(checkIndex);
        }
    }

    static void writeCellValue(Cell cell,String value){
        if(value==null||value.isBlank()){cell.setBlank();return;}
        if(value.startsWith("=")&&value.length()>1){cell.setCellFormula(value.substring(1));return;}
        // POI's setCellValue(...) only changes the cached result when the cell is
        // currently a formula cell.  Remove the old formula first; otherwise the
        // ERP token looks correct in the designer but the old formula returns when
        // the saved template is reopened (for example IGST and Grand Total cells).
        if(cell.getCellType()==CellType.FORMULA)cell.setCellFormula(null);
        if(value.matches("-?\\d+(\\.\\d+)?")){try{cell.setCellValue(Double.parseDouble(value));return;}catch(Exception ignored){}}
        if("true".equalsIgnoreCase(value)||"false".equalsIgnoreCase(value)){cell.setCellValue(Boolean.parseBoolean(value));return;}
        cell.setCellValue(value);
    }

    private Cell cellAt(Sheet sheet,int rowIndex,int colIndex,boolean create){
        Row row=sheet.getRow(rowIndex);if(row==null&&create)row=sheet.createRow(rowIndex);if(row==null)return null;
        Cell cell=row.getCell(colIndex);if(cell==null&&create)cell=row.createCell(colIndex);return cell;
    }

    private void saveVisibleCells(boolean trackHistory){
        if(workbook==null||renderedSheetName==null)return;
        for(var entry:editors.entrySet()){
            String[] parts=entry.getKey().split(":");
            commitCell(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),entry.getValue(),trackHistory);
        }
    }

    private void recordUndoPoint(){
        if(restoringHistory||workbook==null)return;
        dirty=true;
        try{history.checkpoint(workbook);}
        catch(Exception e){System.err.println("[ExcelStudio] Could not capture undo state: "+e.getMessage());}
        updateUndoRedoButtons();
    }


    private byte[] snapshotWorkbook() throws IOException {
        return ExcelWorkbookHistory.snapshot(workbook);
    }


    @FXML private void undo(){
        commitActiveEdit();
        if(!history.canUndo()){updateUndoRedoButtons();return;}
        try{
            byte[] previous=history.undo(workbook);
            restoreWorkbook(previous);
            dirty=true;
        }catch(Exception e){ModernDialog.error(root,"Undo failed","Excel Studio",rootMessage(e));}
        updateUndoRedoButtons();
    }


    @FXML private void redo(){
        commitActiveEdit();
        if(!history.canRedo()){updateUndoRedoButtons();return;}
        try{
            byte[] next=history.redo(workbook);
            restoreWorkbook(next);
            dirty=true;
        }catch(Exception e){ModernDialog.error(root,"Redo failed","Excel Studio",rootMessage(e));}
        updateUndoRedoButtons();
    }


    private void restoreWorkbook(byte[] snapshot) throws Exception{
        if(snapshot==null||snapshot.length==0)throw new IOException("The workbook history snapshot is empty.");
        String sheetName=renderedSheetName;
        int row=focusedRow,col=focusedCol;
        Workbook previous=workbook;
        Workbook candidate=null;
        restoringHistory=true;
        try{
            // Open and validate the replacement first. Never close the last known-good workbook until the swap succeeds.
            candidate=WorkbookFactory.create(new ByteArrayInputStream(snapshot));
            if(candidate.getNumberOfSheets()<1)throw new IOException("The workbook history snapshot has no worksheets.");
            workbook=candidate;
            clipboardCell=null;
            formatClipboard=null;
            updateFormatClipboardButtons();
            renderedSheetName=null;
            editors.clear();
            cmbSheet.getItems().setAll(sheetNames());
            if(sheetName!=null&&workbook.getSheet(sheetName)!=null)cmbSheet.setValue(sheetName);
            else if(!cmbSheet.getItems().isEmpty())cmbSheet.getSelectionModel().selectFirst();
            focusedRow=Math.max(0,row);focusedCol=Math.max(0,col);
            renderSheet();
            candidate=null; // workbook now owns the successfully rendered candidate
            if(previous!=null&&previous!=workbook)try{previous.close();}catch(Exception ignored){}
        }catch(Exception error){
            Workbook failed=workbook;
            workbook=previous;
            if(failed!=null&&failed!=previous)try{failed.close();}catch(Exception ignored){}
            if(candidate!=null&&candidate!=previous)try{candidate.close();}catch(Exception ignored){}
            renderedSheetName=null;
            editors.clear();
            if(previous!=null){
                cmbSheet.getItems().setAll(sheetNames());
                if(sheetName!=null&&previous.getSheet(sheetName)!=null)cmbSheet.setValue(sheetName);
                else if(!cmbSheet.getItems().isEmpty())cmbSheet.getSelectionModel().selectFirst();
                try{renderSheet();}catch(Exception ignored){}
            }
            throw error;
        }finally{restoringHistory=false;}
    }

    private void updateUndoRedoButtons(){
        if(btnUndo!=null)btnUndo.setDisable(!history.canUndo());
        if(btnRedo!=null)btnRedo.setDisable(!history.canRedo());
    }


    @FXML private void insertField(){
        TemplateFieldDefinition field=fieldList.getSelectionModel().getSelectedItem();
        if(field==null){ModernDialog.info(root,"Choose a field","Excel Studio","Select an ERP field from the field palette first.");return;}
        if(focusedEditor==null){ModernDialog.info(root,"Choose a cell","Excel Studio","Select the workbook cell where the field should be inserted, or drag the field onto a cell.");return;}
        focusedEditor.setText("{{"+field.key()+"}}");commitCell(focusedRow,focusedCol,focusedEditor,true);refreshMappingUi();markImagePlaceholder(focusedEditor,field.key());focusedEditor.requestFocus();syncFormulaBar();
    }

    private void markImagePlaceholder(TextField editor,String key){
        boolean image=TemplateFieldCatalog.excelFieldsFor(template.getDocumentType()).stream().anyMatch(f->f.key().equals(key)&&f.image());
        editor.getStyleClass().remove("excel-cell-image-placeholder");if(image)editor.getStyleClass().add("excel-cell-image-placeholder");
    }

    @FXML private void insertItemRow(){
        if(!TemplateFieldCatalog.supportsItemRows(template.getDocumentType())){ModernDialog.info(root,"Items are not used","Excel Studio",template.getDocumentType().label()+" does not use repeating item rows.");return;}
        if(focusedRow<0){ModernDialog.info(root,"Choose a row","Excel Studio","Select a cell in the row that should repeat for line items.");return;}
        String[] values={"{{item.serial}}","{{item.code}}","{{item.descriptionWithRemarks}}","{{item.hsn}}","{{item.quantity}}","{{item.unit}}","{{item.rate}}","{{item.discountPercent}}","{{item.discountAmount}}","{{item.taxable}}","{{item.gstPercent}}","{{item.cgstPercent}}","{{item.cgstAmount}}","{{item.sgstPercent}}","{{item.sgstAmount}}","{{item.igstPercent}}","{{item.igstAmount}}","{{item.gstAmount}}","{{item.total}}"};
        placeRepeatingRow(values);
    }

    @FXML private void insertChargeRow(){
        if(!TemplateFieldCatalog.supportsChargeRows(template.getDocumentType())){ModernDialog.info(root,"Charges are not used","Excel Studio",template.getDocumentType().label()+" does not expose repeating charge rows.");return;}
        if(focusedRow<0){ModernDialog.info(root,"Choose a row","Excel Studio","Select a cell in the row that should repeat for additional charges.");return;}
        String[] values={"{{charge.serial}}","{{charge.type}}","{{charge.amount}}","{{charge.taxable}}","{{charge.taxableAmount}}","{{charge.gstPercent}}","{{charge.cgstPercent}}","{{charge.cgstAmount}}","{{charge.sgstPercent}}","{{charge.sgstAmount}}","{{charge.igstPercent}}","{{charge.igstAmount}}","{{charge.taxAmount}}","{{charge.total}}"};
        placeRepeatingRow(values);
    }

    private void placeRepeatingRow(String[] values){
        commitActiveEdit();recordUndoPoint();
        for(int c=0;c<Math.min(values.length,VISIBLE_COLS);c++){TextField editor=editors.get(focusedRow+":"+c);if(editor!=null)editor.setText(values[c]);}
        saveVisibleCells(false);syncFormulaBar();refreshMappingUi();
    }

    @FXML private void bold(){
        boolean makeBold=!allSelectedMatch(snapshot->snapshot.bold());
        applyStyleToRange(cell->{Font font=cloneFont(workbook.getFontAt(cell.getCellStyle().getFontIndex()));font.setBold(makeBold);CellStyle style=cloneStyle(cell);style.setFont(font);cell.setCellStyle(style);});
    }
    @FXML private void italic(){
        boolean makeItalic=!allSelectedMatch(snapshot->snapshot.italic());
        applyStyleToRange(cell->{Font font=cloneFont(workbook.getFontAt(cell.getCellStyle().getFontIndex()));font.setItalic(makeItalic);CellStyle style=cloneStyle(cell);style.setFont(font);cell.setCellStyle(style);});
    }
    @FXML private void underline(){
        boolean makeUnderline=!allSelectedMatch(snapshot->snapshot.underline());
        applyStyleToRange(cell->{Font font=cloneFont(workbook.getFontAt(cell.getCellStyle().getFontIndex()));font.setUnderline(makeUnderline?Font.U_SINGLE:Font.U_NONE);CellStyle style=cloneStyle(cell);style.setFont(font);cell.setCellStyle(style);});
    }

    @FXML private void alignLeft(){alignment(HorizontalAlignment.LEFT);}
    @FXML private void alignCenter(){alignment(HorizontalAlignment.CENTER);}
    @FXML private void alignRight(){alignment(HorizontalAlignment.RIGHT);}
    private void alignment(HorizontalAlignment alignment){applyStyleToRange(cell->{CellStyle style=cloneStyle(cell);style.setAlignment(alignment);cell.setCellStyle(style);});}

    @FXML private void wrapText(){
        boolean makeWrap=!allSelectedMatch(snapshot->snapshot.wrap());
        applyStyleToRange(cell->{CellStyle style=cloneStyle(cell);style.setWrapText(makeWrap);cell.setCellStyle(style);});
    }

    @FXML private void fontChanged(){
        if(updatingFormatControls)return;
        String value=cmbFont.getValue();
        if(value==null||value.isBlank())return;
        applyStyleToRange(cell->{Font font=cloneFont(workbook.getFontAt(cell.getCellStyle().getFontIndex()));font.setFontName(value);CellStyle style=cloneStyle(cell);style.setFont(font);cell.setCellStyle(style);});
    }
    @FXML private void fontSizeChanged(){
        if(updatingFormatControls)return;
        Integer value=cmbFontSize.getValue();
        if(value==null)return;
        applyStyleToRange(cell->{Font font=cloneFont(workbook.getFontAt(cell.getCellStyle().getFontIndex()));font.setFontHeightInPoints(value.shortValue());CellStyle style=cloneStyle(cell);style.setFont(font);cell.setCellStyle(style);});
    }
    @FXML private void numberFormatChanged(){
        if(updatingFormatControls)return;
        String format=cmbNumberFormat.getValue();
        if(format==null||format.isBlank())return;
        applyStyleToRange(cell->{CellStyle style=cloneStyle(cell);style.setDataFormat(workbook.createDataFormat().getFormat(format));cell.setCellStyle(style);});
    }
    @FXML private void fontColorChanged(){
        if(updatingFormatControls)return;
        Color value=pickerFontColor.getValue();
        if(value==null)return;
        applyStyleToRange(cell->{Font font=cloneFont(workbook.getFontAt(cell.getCellStyle().getFontIndex()));setFontColor(font,value);CellStyle style=cloneStyle(cell);style.setFont(font);cell.setCellStyle(style);});
    }
    @FXML private void fillColorChanged(){
        if(updatingFormatControls)return;
        Color value=pickerFillColor.getValue();
        if(value==null)return;
        applyStyleToRange(cell->{CellStyle style=cloneStyle(cell);setFillColor(style,value);cell.setCellStyle(style);});
    }
    @FXML private void clearFill(){
        applyStyleToRange(cell->{CellStyle style=cloneStyle(cell);style.setFillPattern(FillPatternType.NO_FILL);cell.setCellStyle(style);});
    }

    @FXML private void borderAll(){applyBorders(BorderCommand.ALL);}
    @FXML private void borderOutside(){applyBorders(BorderCommand.OUTSIDE);}
    @FXML private void borderInside(){applyBorders(BorderCommand.INSIDE);}
    @FXML private void borderTop(){applyBorders(BorderCommand.TOP);}
    @FXML private void borderBottom(){applyBorders(BorderCommand.BOTTOM);}
    @FXML private void borderLeft(){applyBorders(BorderCommand.LEFT);}
    @FXML private void borderRight(){applyBorders(BorderCommand.RIGHT);}
    @FXML private void borderInsideHorizontal(){applyBorders(BorderCommand.INSIDE_HORIZONTAL);}
    @FXML private void borderInsideVertical(){applyBorders(BorderCommand.INSIDE_VERTICAL);}
    @FXML private void clearBorders(){applyBorders(BorderCommand.NONE);}

    private enum BorderCommand { ALL, OUTSIDE, INSIDE, TOP, BOTTOM, LEFT, RIGHT, INSIDE_HORIZONTAL, INSIDE_VERTICAL, NONE }

    private void applyBorders(BorderCommand command){
        if(focusedRow<0)return;
        commitActiveEdit();
        saveVisibleCells(false);
        recordUndoPoint();
        CellRangeAddress range=currentRange();
        Sheet sheet=editorSheet();
        for(int r=range.getFirstRow();r<=range.getLastRow();r++){
            for(int c=range.getFirstColumn();c<=range.getLastColumn();c++){
                Cell cell=cellAt(sheet,r,c,true);
                CellStyle style=cloneStyle(cell);
                if(command==BorderCommand.NONE){
                    style.setBorderTop(BorderStyle.NONE);style.setBorderRight(BorderStyle.NONE);style.setBorderBottom(BorderStyle.NONE);style.setBorderLeft(BorderStyle.NONE);
                }else if(command==BorderCommand.ALL){
                    setBorder(style,true,true,true,true);
                }else{
                    boolean top=false,right=false,bottom=false,left=false;
                    if(command==BorderCommand.OUTSIDE||command==BorderCommand.TOP)top=r==range.getFirstRow();
                    if(command==BorderCommand.OUTSIDE||command==BorderCommand.BOTTOM)bottom=r==range.getLastRow();
                    if(command==BorderCommand.OUTSIDE||command==BorderCommand.LEFT)left=c==range.getFirstColumn();
                    if(command==BorderCommand.OUTSIDE||command==BorderCommand.RIGHT)right=c==range.getLastColumn();
                    if(command==BorderCommand.INSIDE||command==BorderCommand.INSIDE_HORIZONTAL){top|=r>range.getFirstRow();bottom|=r<range.getLastRow();}
                    if(command==BorderCommand.INSIDE||command==BorderCommand.INSIDE_VERTICAL){left|=c>range.getFirstColumn();right|=c<range.getLastColumn();}
                    setBorder(style,top,right,bottom,left);
                }
                cell.setCellStyle(style);
            }
        }
        refreshVisibleStyles(range);
        refreshSelectionUi();
        requestEditorFocus(focusedRow,focusedCol);
    }

    private void setBorder(CellStyle style,boolean top,boolean right,boolean bottom,boolean left){
        if(top)style.setBorderTop(BorderStyle.THIN);
        if(right)style.setBorderRight(BorderStyle.THIN);
        if(bottom)style.setBorderBottom(BorderStyle.THIN);
        if(left)style.setBorderLeft(BorderStyle.THIN);
    }

    private void applyStyleToRange(Consumer<Cell> action){
        if(focusedRow<0)return;
        commitActiveEdit();
        saveVisibleCells(false);
        recordUndoPoint();
        CellRangeAddress range=currentRange();
        Sheet sheet=editorSheet();
        for(int r=range.getFirstRow();r<=range.getLastRow();r++)for(int c=range.getFirstColumn();c<=range.getLastColumn();c++)action.accept(cellAt(sheet,r,c,true));
        refreshVisibleStyles(range);
        refreshSelectionUi();
        requestEditorFocus(focusedRow,focusedCol);
    }

    private CellRangeAddress currentRange(){
        return ExcelSelectionPolicy.range(selectionAnchorRow, selectionAnchorCol, selectionEndRow, selectionEndCol, focusedRow, focusedCol);
    }

    @FXML private void mergeAndCenter(){mergeRange(true);}
    @FXML private void mergeCells(){mergeRange(false);}

    private void mergeRange(boolean center){
        if(focusedRow<0)return;
        commitActiveEdit();
        saveVisibleCells(false);
        CellRangeAddress range=currentRange();
        if(range.getNumberOfCells()<=1){ModernDialog.info(root,"Select a range","Excel Studio","Select two or more cells with the mouse, Shift+Arrow, Shift+Click, or the Range box before merging.");return;}
        try{
            recordUndoPoint();
            editorSheet().addMergedRegion(copyRange(range));
            if(center){Cell cell=cellAt(editorSheet(),range.getFirstRow(),range.getFirstColumn(),true);CellStyle style=cloneStyle(cell);style.setAlignment(HorizontalAlignment.CENTER);cell.setCellStyle(style);}
            renderSheet();
            setSelection(range,range.getFirstRow(),range.getFirstColumn());
            requestEditorFocus(range.getFirstRow(),range.getFirstColumn());
        }catch(Exception e){ModernDialog.error(root,"Cells could not be merged","Excel Studio",rootMessage(e));}
    }

    @FXML private void unmergeCells(){
        if(focusedRow<0)return;
        commitActiveEdit();
        saveVisibleCells(false);
        CellRangeAddress target=currentRange();
        Sheet sheet=editorSheet();
        List<Integer> remove=new ArrayList<>();
        for(int i=0;i<sheet.getNumMergedRegions();i++)if(intersects(sheet.getMergedRegion(i),target))remove.add(i);
        if(remove.isEmpty())return;
        recordUndoPoint();
        for(int i=remove.size()-1;i>=0;i--)sheet.removeMergedRegion(remove.get(i));
        renderSheet();
        setSelection(target,target.getFirstRow(),target.getFirstColumn());
        requestEditorFocus(target.getFirstRow(),target.getFirstColumn());
    }

    @FXML private void setRowHeight(){
        if(focusedRow<0)return;String value=ask("Row Height","Row height in points for selected row(s):",Double.toString(editorSheet().getRow(focusedRow)==null?15:editorSheet().getRow(focusedRow).getHeightInPoints()));if(value==null)return;
        try{float height=Float.parseFloat(value);if(height<2||height>409)throw new IllegalArgumentException("Use a height between 2 and 409 points.");commitActiveEdit();saveVisibleCells(false);recordUndoPoint();CellRangeAddress range=currentRange();for(int r=range.getFirstRow();r<=range.getLastRow();r++){Row row=editorSheet().getRow(r);if(row==null)row=editorSheet().createRow(r);row.setHeightInPoints(height);}renderSheet();}catch(Exception e){ModernDialog.error(root,"Invalid row height","Excel Studio",rootMessage(e));}
    }

    @FXML private void decreaseRowHeight(){adjustRowHeight(-3f);}
    @FXML private void increaseRowHeight(){adjustRowHeight(3f);}
    private void adjustRowHeight(float delta){
        if(focusedRow<0)return;
        try{commitActiveEdit();saveVisibleCells(false);recordUndoPoint();CellRangeAddress range=currentRange();Sheet sheet=editorSheet();for(int r=range.getFirstRow();r<=range.getLastRow();r++){Row row=sheet.getRow(r);if(row==null)row=sheet.createRow(r);float current=row.getHeightInPoints()>0?row.getHeightInPoints():sheet.getDefaultRowHeightInPoints();row.setHeightInPoints(Math.max(2f,Math.min(409f,current+delta)));}renderSheet();setSelection(range,range.getFirstRow(),range.getFirstColumn());}catch(Exception e){ModernDialog.error(root,"Row height could not be changed","Excel Studio",rootMessage(e));}
    }

    @FXML private void setColumnWidth(){
        if(focusedCol<0)return;String value=ask("Column Width","Column width in characters for selected column(s):",String.format(Locale.ROOT,"%.1f",editorSheet().getColumnWidth(focusedCol)/256d));if(value==null)return;
        try{double width=Double.parseDouble(value);if(width<1||width>255)throw new IllegalArgumentException("Use a width between 1 and 255 characters.");commitActiveEdit();saveVisibleCells(false);recordUndoPoint();CellRangeAddress range=currentRange();for(int c=range.getFirstColumn();c<=range.getLastColumn();c++)editorSheet().setColumnWidth(c,(int)Math.round(width*256));renderSheet();}catch(Exception e){ModernDialog.error(root,"Invalid column width","Excel Studio",rootMessage(e));}
    }

    @FXML private void decreaseColumnWidth(){adjustColumnWidth(-2d);}
    @FXML private void increaseColumnWidth(){adjustColumnWidth(2d);}
    private void adjustColumnWidth(double delta){
        if(focusedCol<0)return;
        try{commitActiveEdit();saveVisibleCells(false);recordUndoPoint();CellRangeAddress range=currentRange();Sheet sheet=editorSheet();for(int c=range.getFirstColumn();c<=range.getLastColumn();c++){double current=sheet.getColumnWidth(c)/256d;sheet.setColumnWidth(c,(int)Math.round(Math.max(1d,Math.min(255d,current+delta))*256d));}renderSheet();setSelection(range,range.getFirstRow(),range.getFirstColumn());}catch(Exception e){ModernDialog.error(root,"Column width could not be changed","Excel Studio",rootMessage(e));}
    }

    @FXML private void autoFitRows(){
        if(focusedRow<0)return;try{commitActiveEdit();saveVisibleCells(false);recordUndoPoint();CellRangeAddress range=currentRange();Sheet sheet=editorSheet();for(int r=range.getFirstRow();r<=range.getLastRow();r++){Row row=sheet.getRow(r);if(row==null)row=sheet.createRow(r);row.setHeightInPoints(ExcelDimensionPolicy.estimateAutoRowHeightPoints(sheet,r,this::cellText));}renderSheet();}catch(Exception e){ModernDialog.error(root,"Rows could not be AutoFit","Excel Studio",rootMessage(e));}
    }

    @FXML private void autoFitColumns(){
        if(focusedCol<0)return;try{commitActiveEdit();saveVisibleCells(false);recordUndoPoint();CellRangeAddress range=currentRange();Sheet sheet=editorSheet();for(int c=range.getFirstColumn();c<=range.getLastColumn();c++){sheet.autoSizeColumn(c);if(sheet.getColumnWidth(c)<256)sheet.setColumnWidth(c,256);}renderSheet();}catch(Exception e){ModernDialog.error(root,"Columns could not be AutoFit","Excel Studio",rootMessage(e));}
    }

    @FXML private void insertRow(){if(focusedRow<0)return;commitActiveEdit();saveVisibleCells(false);recordUndoPoint();Sheet sheet=editorSheet();if(sheet.getLastRowNum()>=focusedRow)sheet.shiftRows(focusedRow,sheet.getLastRowNum(),1,true,false);sheet.createRow(focusedRow);renderSheet();}
    @FXML private void deleteRow(){if(focusedRow<0)return;commitActiveEdit();saveVisibleCells(false);recordUndoPoint();Sheet sheet=editorSheet();Row row=sheet.getRow(focusedRow);if(row!=null)sheet.removeRow(row);if(focusedRow<sheet.getLastRowNum())sheet.shiftRows(focusedRow+1,sheet.getLastRowNum(),-1,true,false);renderSheet();}

    @FXML private void insertColumn(){if(focusedCol<0)return;commitActiveEdit();saveVisibleCells(false);recordUndoPoint();Sheet sheet=editorSheet();for(Row row:sheet){short last=row.getLastCellNum();if(last<0)continue;for(int c=last;c>focusedCol;c--)copyCellValue(row.getCell(c-1),row.createCell(c));Cell target=row.getCell(focusedCol);if(target!=null)target.setBlank();}renderSheet();}
    @FXML private void deleteColumn(){if(focusedCol<0)return;commitActiveEdit();saveVisibleCells(false);recordUndoPoint();Sheet sheet=editorSheet();for(Row row:sheet){short last=row.getLastCellNum();if(last<0)continue;for(int c=focusedCol;c<last-1;c++)copyCellValue(row.getCell(c+1),row.getCell(c)==null?row.createCell(c):row.getCell(c));Cell tail=row.getCell(last-1);if(tail!=null)row.removeCell(tail);}renderSheet();}

    @FXML private void copyCell(){if(focusedRow<0)return;commitActiveEdit();saveVisibleCells(false);Cell cell=cellAt(editorSheet(),focusedRow,focusedCol,false);if(cell==null){clipboardCell=null;return;}CellStyle style=workbook.createCellStyle();style.cloneStyleFrom(cell.getCellStyle());clipboardCell=new CellSnapshot(cell.getCellType(),cell.getCellType()==CellType.STRING?cell.getStringCellValue():"",cell.getCellType()==CellType.NUMERIC?cell.getNumericCellValue():0,cell.getCellType()==CellType.BOOLEAN&&cell.getBooleanCellValue(),cell.getCellType()==CellType.FORMULA?cell.getCellFormula():"",style);}
    @FXML private void pasteCell(){if(focusedRow<0||clipboardCell==null)return;commitActiveEdit();recordUndoPoint();Cell target=cellAt(editorSheet(),focusedRow,focusedCol,true);target.setCellStyle(clipboardCell.style());switch(clipboardCell.type()){case STRING->target.setCellValue(clipboardCell.text());case NUMERIC->target.setCellValue(clipboardCell.number());case BOOLEAN->target.setCellValue(clipboardCell.bool());case FORMULA->target.setCellFormula(clipboardCell.formula());default->target.setBlank();}renderSheet();}

    /** Copies only cell formatting (font/fill/borders/alignment/wrap/number format), never values or formulas. */
    @FXML private void copyFormat(){
        if(focusedRow<0||workbook==null)return;
        commitActiveEdit();saveVisibleCells(false);
        CellRangeAddress source=currentRange();
        List<List<CellStyle>> styles=new ArrayList<>();
        for(int r=source.getFirstRow();r<=source.getLastRow();r++){
            List<CellStyle> rowStyles=new ArrayList<>();
            for(int c=source.getFirstColumn();c<=source.getLastColumn();c++){
                Cell sourceCell=cellAt(editorSheet(),r,c,false);
                rowStyles.add(sourceCell==null?workbook.getCellStyleAt(0):sourceCell.getCellStyle());
            }
            styles.add(List.copyOf(rowStyles));
        }
        formatClipboard=new FormatClipboard(source.getLastRow()-source.getFirstRow()+1,source.getLastColumn()-source.getFirstColumn()+1,List.copyOf(styles));
        updateFormatClipboardButtons();
        ModernDialog.success(root,"Format copied","Select the destination cell or range, then choose Paste Format. Values and formulas will not be changed.");
    }

    /** Applies the copied format to the current target selection. Multi-cell source patterns tile across a larger target. */
    @FXML private void pasteFormat(){
        if(focusedRow<0||formatClipboard==null||workbook==null){
            ModernDialog.info(root,"Copy a format first","Excel Studio","Select the source cell or range and choose Copy Format first.");
            return;
        }
        commitActiveEdit();saveVisibleCells(false);recordUndoPoint();
        CellRangeAddress target=currentRange();
        for(int r=target.getFirstRow();r<=target.getLastRow();r++)for(int c=target.getFirstColumn();c<=target.getLastColumn();c++){
            int sr=(r-target.getFirstRow())%formatClipboard.rows();
            int sc=(c-target.getFirstColumn())%formatClipboard.cols();
            CellStyle sourceStyle=formatClipboard.styles().get(sr).get(sc);
            CellStyle pasted=workbook.createCellStyle();pasted.cloneStyleFrom(sourceStyle);
            cellAt(editorSheet(),r,c,true).setCellStyle(pasted);
        }
        refreshVisibleStyles(target);refreshSelectionUi();
    }

    private void updateFormatClipboardButtons(){
        if(btnPasteFormat!=null)btnPasteFormat.setDisable(formatClipboard==null);
        if(btnCopyFormat!=null)btnCopyFormat.setTooltip(new Tooltip("Copy formatting only from the selected cell/range"));
        if(btnPasteFormat!=null)btnPasteFormat.setTooltip(new Tooltip(formatClipboard==null?"Copy a format first":"Apply copied formatting to the selected destination range"));
    }

    @FXML private void freezePane(){if(focusedRow<0)return;commitActiveEdit();recordUndoPoint();editorSheet().createFreezePane(Math.max(0,focusedCol),Math.max(0,focusedRow));ModernDialog.success(root,"Freeze pane updated","Rows above and columns left of "+lblCell.getText()+" will remain visible.");}
    @FXML private void unfreezePane(){commitActiveEdit();recordUndoPoint();editorSheet().createFreezePane(0,0);ModernDialog.success(root,"Freeze pane removed","The active worksheet is no longer frozen.");}

    @FXML private void addSheet(){
        String name=ask("New Worksheet","Worksheet name:","Sheet"+(workbook.getNumberOfSheets()+1));if(name==null||name.isBlank())return;
        try{commitActiveEdit();recordUndoPoint();workbook.createSheet(name.trim());cmbSheet.getItems().setAll(sheetNames());cmbSheet.setValue(name.trim());}catch(Exception e){ModernDialog.error(root,"Worksheet could not be added","Excel Studio",rootMessage(e));}
    }
    @FXML private void renameSheet(){String old=activeSheet().getSheetName();String name=ask("Rename Worksheet","Worksheet name:",old);if(name==null||name.isBlank()||name.equals(old))return;try{commitActiveEdit();saveVisibleCells(false);recordUndoPoint();int index=workbook.getSheetIndex(activeSheet());workbook.setSheetName(index,name.trim());renderedSheetName=name.trim();cmbSheet.getItems().setAll(sheetNames());cmbSheet.setValue(name.trim());}catch(Exception e){ModernDialog.error(root,"Worksheet could not be renamed","Excel Studio",rootMessage(e));}}
    @FXML private void deleteSheet(){if(workbook.getNumberOfSheets()<=1){ModernDialog.info(root,"Worksheet required","Excel Studio","An Excel template must keep at least one worksheet.");return;}int index=workbook.getSheetIndex(activeSheet());if(!ModernDialog.confirm(root,"Delete Worksheet","Delete "+activeSheet().getSheetName()+"?","This removes the worksheet from this template."))return;commitActiveEdit();saveVisibleCells(false);recordUndoPoint();workbook.removeSheetAt(index);renderedSheetName=null;editors.clear();cmbSheet.getItems().setAll(sheetNames());cmbSheet.getSelectionModel().select(Math.max(0,index-1));}

    @FXML private void save(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "save an Excel template");try{commitActiveEdit();saveVisibleCells(false);ExcelTemplateStorageService.saveWorkbook(template,workbook);dirty=false;lblVersion.setText("v"+template.getVersion());ModernDialog.success(root,"Excel template saved",template.getName()+" was saved. Previous workbook versions remain in template history.");}catch(Exception e){ModernDialog.error(root,"Save failed","Excel Studio",rootMessage(e));}}
    @FXML private void saveDefault(){
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.MANAGE_TEMPLATES", "set the default Excel template");
        try{
            commitActiveEdit();saveVisibleCells(false);
            TemplateData data=previewDataForValidation(true);
            validateDefaultTemplate(data);
            Path shadow=writeWorkingCopy("excel-studio-default-check-");
            Path rendered=org.example.config.WorkspaceManager.getTempFolder().resolve("excel-studio-default-render-"+template.getId()+".xlsx");
            try{
                ExcelTemplateRenderer.renderWorkbook(shadow,template.getDocumentType(),data,rendered);
                validateRenderedOutput(rendered,data);
            }finally{Files.deleteIfExists(shadow);Files.deleteIfExists(rendered);}
            ExcelTemplateStorageService.saveWorkbook(template,workbook);
            ExcelTemplateStorageService.activateAndSetDefault(template);
            dirty=false;
            lblVersion.setText("v"+template.getVersion());
            ModernDialog.success(root,"Default Excel template updated",template.getName()+" is now the validated default for "+template.getDocumentType().label()+". Document-type mapping, repeating rows, and ERP fields were rendered successfully.");
        }catch(Exception e){ModernDialog.error(root,"Default could not be activated","Excel Studio",rootMessage(e));}
    }
    @FXML private void preview(){
        try{
            commitActiveEdit();saveVisibleCells(false);
            TemplateData data=previewDataForValidation(false);
            Path tmp=org.example.config.WorkspaceManager.getTempFolder().resolve("excel-studio-preview-"+template.getId()+".xlsx");
            Path shadow=writeWorkingCopy("excel-studio-working-");
            try{ExcelTemplateRenderer.renderWorkbook(shadow,template.getDocumentType(),data,tmp);}finally{Files.deleteIfExists(shadow);}
            if(Desktop.isDesktopSupported())Desktop.getDesktop().open(tmp.toFile());else ModernDialog.info(root,"Preview generated","Excel Studio",tmp.toString());
        }catch(Exception e){ModernDialog.error(root,"Preview failed","Excel Studio",rootMessage(e));}
    }
    @FXML private void download(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.MANAGE_TEMPLATES", "download an Excel template");try{commitActiveEdit();saveVisibleCells(false);FileChooser chooser=new FileChooser();chooser.setTitle("Save Excel Template");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook","*.xlsx"));chooser.setInitialFileName(template.getName().replaceAll("[^A-Za-z0-9._ -]","_")+".xlsx");var file=chooser.showSaveDialog(root.getScene().getWindow());if(file==null)return;Files.write(file.toPath(),snapshotWorkbook());ModernDialog.success(root,"Template exported",file.getName()+" was saved.");}catch(Exception e){ModernDialog.error(root,"Export failed","Excel Studio",rootMessage(e));}}
    @FXML private void back(){if(!allowNavigationAway("Excel Studio"))return;closeWorkbook();DocumentStudioContext.selectMode(DocumentStudioContext.Mode.EXCEL);DashboardController.navigateFromDocumentStudio("Excel Studio","/fxml/pages/DocumentStudio.fxml");}

    private boolean allowNavigationAway(String destination){
        commitActiveEdit();
        saveVisibleCells(false);
        if(!dirty){NavigationGuardRegistry.clear(root);return true;}
        String target=destination==null||destination.isBlank()?"another screen":destination;
        boolean leave=ModernDialog.confirm(root,"Leave Excel Studio?","Discard unsaved template changes?","You have changes that have not been saved. Choose Cancel to stay in Excel Studio and save them, or confirm to continue to "+target+" without saving.");
        if(leave)NavigationGuardRegistry.clear(root);
        return leave;
    }

    private void configureRecordPreview(){
        if(cmbPreviewRecord==null||template==null)return;
        boolean erp=template.getDocumentType()!=null&&template.getDocumentType().isErpConnected();
        if(boxRecordPreview!=null){boxRecordPreview.setVisible(erp);boxRecordPreview.setManaged(erp);}
        cmbPreviewRecord.setVisible(erp);cmbPreviewRecord.setManaged(erp);
        if(lblPreviewRecord!=null)lblPreviewRecord.setText("Preview "+template.getDocumentType().label());
        if(!erp)return;
        cmbPreviewRecord.valueProperty().addListener((o,a,b)->loadSelectedPreviewRecord(b));
        reloadPreviewRecords();
    }

    @FXML private void reloadPreviewRecords(){
        if(cmbPreviewRecord==null||template==null)return;
        DocumentType type=template.getDocumentType();
        selectedPreviewData=null;
        try{
            if(!DocumentDataService.supportsRealData(type)){
                cmbPreviewRecord.getItems().clear();
                cmbPreviewRecord.setValue(null);
                cmbPreviewRecord.setDisable(true);
                cmbPreviewRecord.setPromptText("Sample preview • no live connector");
                selectedPreviewData=DocumentDataService.sample(type);
                refreshMappingUi();
                return;
            }
            cmbPreviewRecord.setDisable(false);
            List<DocumentSample> records=DocumentDataService.listSamples(type);
            String currentId=cmbPreviewRecord.getValue()==null?null:cmbPreviewRecord.getValue().id();
            cmbPreviewRecord.getItems().setAll(records);
            DocumentSample keep=currentId==null?null:records.stream().filter(record->currentId.equals(record.id())).findFirst().orElse(null);
            if(keep!=null)cmbPreviewRecord.setValue(keep);
            else if(!records.isEmpty())cmbPreviewRecord.getSelectionModel().selectFirst();
            else{cmbPreviewRecord.setValue(null);cmbPreviewRecord.setPromptText("No live "+type.label()+" records");refreshMappingUi();}
        }catch(Exception error){
            selectedPreviewData=null;
            cmbPreviewRecord.getItems().clear();
            cmbPreviewRecord.setValue(null);
            cmbPreviewRecord.setPromptText("Live records unavailable");
            refreshMappingUi();
            System.err.println("[ExcelStudio] "+type.label()+" preview list could not be loaded: "+rootMessage(error));
        }
    }

    private void loadSelectedPreviewRecord(DocumentSample sample){
        selectedPreviewData=null;
        if(template==null){refreshMappingUi();return;}
        DocumentType type=template.getDocumentType();
        if(sample!=null&&sample.id()!=null&&!sample.id().isBlank())try{
            selectedPreviewData=DocumentDataService.load(type,sample.id());
        }catch(Exception error){System.err.println("[ExcelStudio] "+type.label()+" preview record could not be loaded: "+rootMessage(error));}
        else if(!DocumentDataService.supportsRealData(type))selectedPreviewData=DocumentDataService.sample(type);
        refreshMappingUi();
    }

    private TemplateData previewDataForValidation(boolean activatingDefault) throws IOException{
        if(template==null||template.getDocumentType()==null)throw new IOException("Excel template document type is missing.");
        DocumentType type=template.getDocumentType();
        if(DocumentDataService.supportsRealData(type)){
            DocumentSample sample=cmbPreviewRecord==null?null:cmbPreviewRecord.getValue();
            if(sample==null||sample.id()==null||sample.id().isBlank())
                throw new IOException((activatingDefault?"A real ":"Select a real ")+type.label()+" record "+(activatingDefault?"is required before making this template default.":"before previewing this template.")+" Reload the record list after creating a record if necessary.");
            try{
                selectedPreviewData=DocumentDataService.load(type,sample.id());
                if(selectedPreviewData==null)throw new IOException("The selected "+type.label()+" record returned no data.");
                refreshMappingUi();
                return selectedPreviewData;
            }catch(IOException error){throw error;}catch(Exception error){throw new IOException("The selected "+type.label()+" record could not be loaded: "+rootMessage(error),error);}
        }
        selectedPreviewData=DocumentDataService.sample(type);
        refreshMappingUi();
        return selectedPreviewData;
    }

    private Path writeWorkingCopy(String prefix) throws IOException{
        Path shadow=org.example.config.WorkspaceManager.getTempFolder().resolve(prefix+template.getId()+".xlsx");
        byte[] snapshot=snapshotWorkbook();
        Files.write(shadow,snapshot);
        return shadow;
    }

    private void validateDefaultTemplate(TemplateData data) throws IOException{
        refreshMappingUi();
        DocumentType type=template.getDocumentType();
        if(!unknownWorkbookTokens.isEmpty())throw new IOException("Unsupported ERP field(s) for "+type.label()+": "+String.join(", ",unknownWorkbookTokens));

        List<String> required=TemplateFieldCatalog.requiredExcelFieldsFor(type);
        List<String> missing=new ArrayList<>(required.stream()
                .filter(key->mappedFieldAddresses.getOrDefault(key,List.of()).isEmpty())
                .map(key->requiredMappingLabel(type,key)).toList());
        if(requiresGrandTotalMapping(type)&&!hasUsableGrandTotalMapping())
            missing.add("Grand Total using totals.grandTotal, totals.roundedGrandTotal, or a valid Excel formula on a Grand/Net Total row");
        if(TemplateFieldCatalog.requiresItemRowForDefault(type)&&!hasCompleteItemRepeatingRow())
            missing.add("one repeating item row with Description, Item Remarks, or Description + Remarks + Quantity + Rate + Line Amount (use Insert Full Item Row)");
        if(!missing.isEmpty())throw new IOException(type.label()+" Excel mapping is incomplete. Add these mappings before making it default: "+String.join("; ",missing)+". Select a cell, choose the field in the ERP Field Palette, and click Insert Selected Field.");

        if(TemplateFieldCatalog.requiresItemRowForDefault(type)&&data.items().isEmpty())
            throw new IOException("The selected "+type.label()+" record contains no line items, so the repeating item row cannot be validated.");

        List<String> renderUnknown=ExcelTemplateRenderer.unknownTokens(workbook,type,data);
        if(!renderUnknown.isEmpty())throw new IOException("Unsupported ERP field(s) for "+type.label()+": "+String.join(", ",renderUnknown));
    }

    private String requiredMappingLabel(DocumentType type,String key){
        return TemplateFieldCatalog.excelFieldsFor(type).stream()
                .filter(field->field.key().equals(key))
                .map(field->field.label()+" ({{"+key+"}})")
                .findFirst().orElse("{{"+key+"}}");
    }

    private void validateRenderedOutput(Path rendered,TemplateData data) throws IOException{
        DocumentType type=template.getDocumentType();
        Map<String,String> identities=validationIdentityValues(type,data);
        String firstItem=data.items().isEmpty()?"":data.items().get(0).getDescription();
        if(firstItem.isBlank()&&!data.items().isEmpty())firstItem=data.items().get(0).getItemCode();
        if(TemplateFieldCatalog.requiresItemRowForDefault(type)&&firstItem.isBlank())
            throw new IOException("The selected "+type.label()+" record has no usable first-item identity for validation.");
        for(Map.Entry<String,String> identity:identities.entrySet())if(identity.getValue()==null||identity.getValue().isBlank())
            throw new IOException("The selected "+type.label()+" record is missing "+identity.getKey()+" and cannot validate a default template.");

        try(Workbook check=WorkbookFactory.create(rendered.toFile())){
            List<String> unresolved=new ArrayList<>();
            Map<String,Boolean> found=new LinkedHashMap<>();
            identities.forEach((label,value)->found.put(label,false));
            boolean itemFound=!TemplateFieldCatalog.requiresItemRowForDefault(type);
            for(int si=0;si<check.getNumberOfSheets();si++)for(Row row:check.getSheetAt(si))for(Cell cell:row){
                if(cell.getCellType()!=CellType.STRING)continue;
                String value=cell.getStringCellValue();
                Matcher matcher=ERP_TOKEN.matcher(value);
                while(matcher.find())unresolved.add(matcher.group(1)+" @ "+check.getSheetName(si)+"!"+cell.getAddress().formatAsString());
                for(Map.Entry<String,String> identity:identities.entrySet())if(!identity.getValue().isBlank()&&value.contains(identity.getValue()))found.put(identity.getKey(),true);
                if(!firstItem.isBlank()&&value.contains(firstItem))itemFound=true;
            }
            if(!unresolved.isEmpty())throw new IOException("Rendered workbook still contains unresolved ERP fields: "+String.join(", ",unresolved));
            List<String> missing=found.entrySet().stream().filter(entry->!entry.getValue()).map(Map.Entry::getKey).toList();
            if(!missing.isEmpty()||!itemFound)throw new IOException(type.label()+" validation failed: rendered workbook did not contain "+String.join(", ",missing)+(missing.isEmpty()?"":itemFound?"":" and ")+(itemFound?"":"the first item value")+".");
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Rendered workbook validation failed: "+rootMessage(e),e);}
    }

    private Map<String,String> validationIdentityValues(DocumentType type,TemplateData data){
        Map<String,String> values=new LinkedHashMap<>();
        switch(type){
            case SALES_INVOICE->{values.put("invoice number",data.value("sales.number"));values.put("customer name",data.value("customer.name"));}
            case PURCHASE_INVOICE,PURCHASE_ORDER->{values.put("purchase number",data.value("purchase.number"));values.put("supplier name",data.value("supplier.name"));}
            case PURCHASE_RETURN,SALES_RETURN,CREDIT_NOTE,DEBIT_NOTE->{values.put("return / note number",data.value("return.number"));values.put("party name",data.value("party.name"));}
            case QUOTATION->{values.put("quotation number",data.value("quotation.number"));values.put("customer name",data.value("customer.name"));}
            case DELIVERY_CHALLAN->{values.put("challan number",data.value("delivery.number"));values.put("customer name",data.value("customer.name"));}
            case PAYMENT_RECEIPT->{values.put("receipt number",data.value("receipt.number"));values.put("party name",data.value("receipt.partyName"));}
            case CUSTOM_ERP,GENERAL_PDF->{ }
        }
        return values;
    }

    private boolean hasCompleteItemRepeatingRow(){
        for(int si=0;si<workbook.getNumberOfSheets();si++){
            for(Row row:workbook.getSheetAt(si)){
                Set<String> rowKeys=new HashSet<>();
                Map<String,Integer> tokenColumns=new HashMap<>();
                for(Cell cell:row){
                    if(cell.getCellType()!=CellType.STRING)continue;
                    Matcher matcher=ERP_TOKEN.matcher(cell.getStringCellValue());
                    while(matcher.find())if(matcher.group(1).startsWith("item.")){rowKeys.add(matcher.group(1));tokenColumns.putIfAbsent(matcher.group(1),cell.getColumnIndex());}
                }
                boolean hasDescription=rowKeys.contains("item.description")
                        ||rowKeys.contains("item.remarks")
                        ||rowKeys.contains("item.descriptionWithRemarks");
                boolean hasCore=hasDescription&&rowKeys.contains("item.quantity")&&rowKeys.contains("item.rate");
                boolean hasLineAmount=rowKeys.contains("item.taxable")||rowKeys.contains("item.total")||hasValidSameRowLineFormula(row,tokenColumns);
                if(hasCore&&hasLineAmount)return true;
            }
        }
        return false;
    }

    private boolean requiresGrandTotalMapping(DocumentType type){
        if(type==null)return false;
        return TemplateFieldCatalog.excelFieldsFor(type).stream().anyMatch(field->field.key().equals("totals.grandTotal")||field.key().equals("totals.roundedGrandTotal"));
    }

    private boolean hasUsableGrandTotalMapping(){
        if(!mappedFieldAddresses.getOrDefault("totals.grandTotal",List.of()).isEmpty())return true;
        if(!mappedFieldAddresses.getOrDefault("totals.roundedGrandTotal",List.of()).isEmpty())return true;
        for(int si=0;si<workbook.getNumberOfSheets();si++){
            Sheet sheet=workbook.getSheetAt(si);
            for(Row row:sheet){
                boolean totalLabel=false;
                for(Cell cell:row){
                    if(cell.getCellType()==CellType.STRING&&isGrandTotalLabel(cell.getStringCellValue())){totalLabel=true;break;}
                }
                if(!totalLabel)continue;
                for(Cell cell:row)if(cell.getCellType()==CellType.FORMULA&&!cell.getCellFormula().isBlank())return true;
            }
        }
        return false;
    }

    private boolean isGrandTotalLabel(String text){
        String normalized=(text==null?"":text).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
        return normalized.contains("GRANDTOTAL")||normalized.contains("NETTOTAL")||normalized.contains("INVOICETOTAL")||normalized.contains("TOTALAMOUNT")||normalized.contains("AMOUNTPAYABLE");
    }

    /** Accept an Excel-computed row total when the formula is genuinely tied to cells on the repeating item row. */
    private boolean hasValidSameRowLineFormula(Row row,Map<String,Integer> tokenColumns){
        if(row==null||tokenColumns==null||tokenColumns.isEmpty())return false;
        int excelRow=row.getRowNum()+1;
        Set<Integer> mappedColumns=new HashSet<>(tokenColumns.values());
        for(Cell cell:row){
            if(cell.getCellType()!=CellType.FORMULA)continue;
            String formulaCode=FORMULA_STRING_LITERAL.matcher(cell.getCellFormula()).replaceAll("");
            Matcher refs=FORMULA_CELL_REF.matcher(formulaCode);
            int sameRowRefs=0;boolean touchesMappedItem=false;
            while(refs.find()){
                int refRow;try{refRow=Integer.parseInt(refs.group(2));}catch(Exception ignored){continue;}
                if(refRow!=excelRow)continue;
                sameRowRefs++;
                int refCol;try{refCol=CellReference.convertColStringToIndex(refs.group(1));}catch(Exception ignored){continue;}
                if(mappedColumns.contains(refCol))touchesMappedItem=true;
            }
            if(sameRowRefs>=2&&touchesMappedItem)return true;
        }
        return false;
    }

    private void refreshMappingUi(){
        if(workbook==null||template==null)return;
        mappedFieldAddresses.clear();unknownWorkbookTokens.clear();
        Set<String> supported=TemplateFieldCatalog.excelFieldsFor(template.getDocumentType()).stream().map(TemplateFieldDefinition::key).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for(int si=0;si<workbook.getNumberOfSheets();si++){
            Sheet sheet=workbook.getSheetAt(si);
            for(Row row:sheet)for(Cell cell:row){
                if(cell.getCellType()!=CellType.STRING)continue;
                Matcher matcher=ERP_TOKEN.matcher(cell.getStringCellValue());
                while(matcher.find()){
                    String key=matcher.group(1);
                    String address=sheet.getSheetName()+"!"+cell.getAddress().formatAsString();
                    if(supported.contains(key))mappedFieldAddresses.computeIfAbsent(key,k->new ArrayList<>()).add(address);
                    else unknownWorkbookTokens.add(key+" @ "+address);
                }
            }
        }
        int mapped=(int)supported.stream().filter(k->!mappedFieldAddresses.getOrDefault(k,List.of()).isEmpty()).count();
        if(lblMappingSummary!=null){
            String preview=selectedPreviewData==null?"":" • Preview: "+selectedPreviewData.items().size()+" item(s), "+selectedPreviewData.charges().size()+" charge(s)";
            lblMappingSummary.setText(mapped+" / "+supported.size()+" ERP fields mapped"+(unknownWorkbookTokens.isEmpty()?"":" • "+unknownWorkbookTokens.size()+" unknown")+preview);
        }
        if(fieldList!=null)fieldList.refresh();
    }

    private String previewValue(String key){
        if(selectedPreviewData==null||key==null)return "";
        if(key.startsWith("item.")){
            if(selectedPreviewData.items().isEmpty())return "";
            var item=selectedPreviewData.items().get(0);
            DocumentCalculationEngine.LineResult line=DocumentCalculationEngine.line(item.getQuantity(),item.getRate(),item.getDiscountPercent(),item.getGstPercent());
            PreviewTaxSplit split=previewTaxSplit(item.getGstPercent(),line.taxAmount(),selectedPreviewData.gstType());
            return switch(key){
                case "item.serial"->Integer.toString(item.getSerialNo());
                case "item.code"->item.getItemCode();case "item.description","item.descriptionWithRemarks"->descriptionWithRemarks(item.getDescription(),item.getRemarks());case "item.remarks"->item.getRemarks();
                case "item.category"->item.getCategory();case "item.brand"->item.getBrand();case "item.material"->item.getMaterial();case "item.size"->item.getSize();case "item.hsn"->item.getHsn();
                case "item.quantity"->number(item.getQuantity());case "item.unit"->item.getUnit();case "item.rate"->number(item.getRate());
                case "item.discountPercent"->number(item.getDiscountPercent());case "item.discountAmount"->number(line.discountAmount());case "item.taxable"->number(line.taxableAmount());
                case "item.gstPercent"->number(item.getGstPercent());case "item.gstAmount"->number(line.taxAmount());case "item.total"->number(line.totalAmount());
                case "item.cgstPercent"->number(split.cgstPercent());case "item.cgstAmount"->number(split.cgstAmount());
                case "item.sgstPercent"->number(split.sgstPercent());case "item.sgstAmount"->number(split.sgstAmount());
                case "item.igstPercent"->number(split.igstPercent());case "item.igstAmount"->number(split.igstAmount());
                case "item.location"->item.getLocation();case "item.purchasePrice"->number(item.getPurchasePrice());case "item.sellingPrice"->number(item.getSellingPrice());
                case "item.availableStock"->number(item.getAvailableStock());case "item.openingStock"->number(item.getOpeningStock());case "item.minimumStock"->number(item.getMinimumStock());case "item.reservedStock"->number(item.getReservedStock());
                case "item.masterGstPercent"->number(item.getMasterGstPercent());case "item.masterDiscountPercent"->number(item.getMasterDiscountPercent());
                default->"";
            };
        }
        if(key.startsWith("charge.")){
            if(selectedPreviewData.charges().isEmpty())return "";
            List<String> values=new ArrayList<>();
            for(int i=0;i<selectedPreviewData.charges().size();i++){
                var c=selectedPreviewData.charges().get(i);
                DocumentCalculationEngine.ChargeResult chargeResult=DocumentCalculationEngine.charge(c.amount(),c.taxable(),c.gstPercent());
                PreviewTaxSplit split=previewTaxSplit(c.gstPercent(),chargeResult.taxAmount(),selectedPreviewData.gstType());
                String value=switch(key){
                    case "charge.serial"->Integer.toString(i+1);case "charge.type"->c.type();case "charge.amount"->number(c.amount());case "charge.taxable"->c.taxable()?"Yes":"No";
                    case "charge.taxableAmount"->number(chargeResult.taxableAmount());
                    case "charge.gstPercent"->number(c.taxable()?c.gstPercent():0);case "charge.taxAmount"->number(chargeResult.taxAmount());case "charge.total"->number(chargeResult.totalAmount());
                    case "charge.cgstPercent"->number(split.cgstPercent());case "charge.cgstAmount"->number(split.cgstAmount());
                    case "charge.sgstPercent"->number(split.sgstPercent());case "charge.sgstAmount"->number(split.sgstAmount());
                    case "charge.igstPercent"->number(split.igstPercent());case "charge.igstAmount"->number(split.igstAmount());default->"";
                };
                values.add("#"+(i+1)+" "+value);
                if(values.size()>=4&&selectedPreviewData.charges().size()>4){values.add("… +"+(selectedPreviewData.charges().size()-4)+" more");break;}
            }
            return String.join("; ",values);
        }
        String direct=selectedPreviewData.value(key);
        if(!direct.isBlank())return direct;
        return ExcelTemplateRenderer.derivedExcelValues(selectedPreviewData).getOrDefault(key,"");
    }

    private record PreviewTaxSplit(double cgstPercent,double cgstAmount,double sgstPercent,double sgstAmount,double igstPercent,double igstAmount) { }
    private PreviewTaxSplit previewTaxSplit(double rate,double tax,String gstType){
        double safeRate=DocumentCalculationEngine.percent(rate),safeTax=DocumentCalculationEngine.money(tax);
        if(DocumentCalculationEngine.taxMode(gstType)==DocumentCalculationEngine.TaxMode.IGST)return new PreviewTaxSplit(0,0,0,0,safeRate,safeTax);
        double cgstRate=safeRate/2d,cgst=DocumentCalculationEngine.money(safeTax/2d);
        return new PreviewTaxSplit(cgstRate,cgst,safeRate-cgstRate,DocumentCalculationEngine.money(safeTax-cgst),0,0);
    }

    private record PaintState(Color color, boolean empty) { }
    private record FormatSnapshot(String fontName,int fontSize,boolean bold,boolean italic,boolean underline,HorizontalAlignment alignment,boolean wrap,String numberFormat,PaintState fontColor,PaintState fillColor) { }
    private record UniformValue<T>(T value,boolean mixed) { }

    private void syncFormatControls(CellRangeAddress range){
        if(workbook==null||range==null)return;
        List<FormatSnapshot> snapshots=formatSnapshots(range);
        if(snapshots.isEmpty())return;
        updatingFormatControls=true;
        try{
            UniformValue<String> fontName=uniform(snapshots,FormatSnapshot::fontName);
            UniformValue<Integer> fontSize=uniform(snapshots,FormatSnapshot::fontSize);
            UniformValue<String> numberFormat=uniform(snapshots,FormatSnapshot::numberFormat);
            setComboState(cmbFont,fontName,"Mixed");
            setComboState(cmbFontSize,fontSize,"Mixed");
            setComboState(cmbNumberFormat,numberFormat,"Mixed");

            setToggleState(btnBold,uniform(snapshots,FormatSnapshot::bold));
            setToggleState(btnItalic,uniform(snapshots,FormatSnapshot::italic));
            setToggleState(btnUnderline,uniform(snapshots,FormatSnapshot::underline));
            setToggleState(btnWrap,uniform(snapshots,FormatSnapshot::wrap));
            setAlignmentState(uniform(snapshots,FormatSnapshot::alignment));

            UniformValue<PaintState> fontColor=uniform(snapshots,FormatSnapshot::fontColor);
            UniformValue<PaintState> fillColor=uniform(snapshots,FormatSnapshot::fillColor);
            setPickerState(pickerFontColor,lblFontColorState,fontColor,"Automatic");
            setPickerState(pickerFillColor,lblFillColorState,fillColor,"No fill");
            setNoFillState(fillColor);

            String borderState=borderState(range);
            if(lblBorderState!=null){lblBorderState.setText(borderState);lblBorderState.setTooltip(new Tooltip(borderState));}
            setControlState(btnBorders,!"None".equals(borderState)&&!"Mixed".equals(borderState),"Mixed".equals(borderState));

            String mergeState=mergeState(range);
            if(lblMergeState!=null){lblMergeState.setText(mergeState);lblMergeState.setTooltip(new Tooltip(mergeState));}
            if(btnMerge!=null){
                btnMerge.getStyleClass().removeAll("excel-format-active","excel-format-mixed");
                if("Merged".equals(mergeState))btnMerge.getStyleClass().add("excel-format-active");
                else if(mergeState.startsWith("Mixed")||mergeState.startsWith("Inside"))btnMerge.getStyleClass().add("excel-format-mixed");
            }
        }catch(Exception ignored){}
        finally{updatingFormatControls=false;}
    }

    private List<FormatSnapshot> formatSnapshots(CellRangeAddress range){
        List<FormatSnapshot> snapshots=new ArrayList<>();
        Sheet sheet=editorSheet();
        for(int r=range.getFirstRow();r<=range.getLastRow();r++)for(int c=range.getFirstColumn();c<=range.getLastColumn();c++)snapshots.add(formatSnapshot(cellAt(sheet,r,c,false)));
        return snapshots;
    }

    private FormatSnapshot formatSnapshot(Cell cell){
        CellStyle style=cell==null?workbook.getCellStyleAt(0):cell.getCellStyle();
        Font font=workbook.getFontAt(style.getFontIndex());
        String format=style.getDataFormatString();
        if(format==null||format.isBlank())format="General";
        return new FormatSnapshot(font.getFontName(),(int)font.getFontHeightInPoints(),font.getBold(),font.getItalic(),font.getUnderline()!=Font.U_NONE,
                style.getAlignment(),style.getWrapText(),format,fontPaint(font),fillPaint(style));
    }

    private boolean allSelectedMatch(Predicate<FormatSnapshot> predicate){
        List<FormatSnapshot> snapshots=formatSnapshots(currentRange());
        return !snapshots.isEmpty()&&snapshots.stream().allMatch(predicate);
    }

    private <T> UniformValue<T> uniform(List<FormatSnapshot> snapshots,Function<FormatSnapshot,T> getter){
        T first=getter.apply(snapshots.get(0));
        for(int i=1;i<snapshots.size();i++)if(!Objects.equals(first,getter.apply(snapshots.get(i))))return new UniformValue<>(first,true);
        return new UniformValue<>(first,false);
    }

    private <T> void setComboState(ComboBox<T> combo,UniformValue<T> state,String mixedPrompt){
        if(combo==null)return;
        if(state.mixed()){
            combo.setValue(null);
            combo.setPromptText(mixedPrompt);
            setMixedClass(combo,true);
        }else{
            T value=state.value();
            if(value!=null&&!combo.getItems().contains(value))combo.getItems().add(value);
            combo.setValue(value);
            combo.setPromptText("");
            setMixedClass(combo,false);
        }
    }

    private void setToggleState(Button button,UniformValue<Boolean> state){
        if(button==null)return;
        button.getStyleClass().removeAll("excel-format-active","excel-format-mixed");
        if(state.mixed())button.getStyleClass().add("excel-format-mixed");
        else if(Boolean.TRUE.equals(state.value()))button.getStyleClass().add("excel-format-active");
    }

    private void setAlignmentState(UniformValue<HorizontalAlignment> state){
        for(Button button:List.of(btnAlignLeft,btnAlignCenter,btnAlignRight))if(button!=null)button.getStyleClass().removeAll("excel-format-active","excel-format-mixed");
        if(state.mixed()){
            for(Button button:List.of(btnAlignLeft,btnAlignCenter,btnAlignRight))if(button!=null)button.getStyleClass().add("excel-format-mixed");
            return;
        }
        HorizontalAlignment value=state.value();
        if((value==HorizontalAlignment.LEFT||value==HorizontalAlignment.GENERAL)&&btnAlignLeft!=null)btnAlignLeft.getStyleClass().add("excel-format-active");
        else if(value==HorizontalAlignment.CENTER&&btnAlignCenter!=null)btnAlignCenter.getStyleClass().add("excel-format-active");
        else if(value==HorizontalAlignment.RIGHT&&btnAlignRight!=null)btnAlignRight.getStyleClass().add("excel-format-active");
    }

    private void setPickerState(ColorPicker picker,Label label,UniformValue<PaintState> state,String emptyLabel){
        if(picker==null)return;
        picker.getStyleClass().removeAll("excel-format-active","excel-format-mixed");
        if(state.mixed()){
            picker.setValue(Color.TRANSPARENT);
            picker.getStyleClass().add("excel-format-mixed");
            if(label!=null)label.setText("Mixed");
            picker.setTooltip(new Tooltip("Mixed colors in the selected range"));
        }else{
            PaintState paint=state.value();
            boolean empty=paint==null||paint.empty();
            picker.setValue(empty?Color.TRANSPARENT:paint.color());
            if(!empty)picker.getStyleClass().add("excel-format-active");
            if(label!=null)label.setText(empty?emptyLabel:colorLabel(paint.color()));
            picker.setTooltip(new Tooltip(empty?emptyLabel:colorLabel(paint.color())));
        }
    }

    private void setNoFillState(UniformValue<PaintState> state){
        if(btnNoFill==null)return;
        btnNoFill.getStyleClass().removeAll("excel-format-active","excel-format-mixed");
        if(state.mixed())btnNoFill.getStyleClass().add("excel-format-mixed");
        else{
            PaintState paint=state.value();
            if(paint==null||paint.empty())btnNoFill.getStyleClass().add("excel-format-active");
        }
    }

    private void setControlState(Control control,boolean active,boolean mixed){
        if(control==null)return;
        control.getStyleClass().removeAll("excel-format-active","excel-format-mixed");
        if(mixed)control.getStyleClass().add("excel-format-mixed");
        else if(active)control.getStyleClass().add("excel-format-active");
    }

    private void setMixedClass(Control control,boolean mixed){
        if(control==null)return;
        control.getStyleClass().remove("excel-format-mixed");
        if(mixed)control.getStyleClass().add("excel-format-mixed");
    }

    private String colorLabel(Color color){
        if(color==null||color.getOpacity()==0)return "Transparent";
        if(closeColor(color,Color.YELLOW))return "Yellow";
        if(closeColor(color,Color.BLACK))return "Black";
        if(closeColor(color,Color.WHITE))return "White";
        if(closeColor(color,Color.RED))return "Red";
        if(closeColor(color,Color.BLUE))return "Blue";
        if(closeColor(color,Color.GREEN))return "Green";
        if(closeColor(color,Color.ORANGE))return "Orange";
        return toCss(color).toUpperCase(Locale.ROOT);
    }

    private boolean closeColor(Color a,Color b){return Math.abs(a.getRed()-b.getRed())<0.01&&Math.abs(a.getGreen()-b.getGreen())<0.01&&Math.abs(a.getBlue()-b.getBlue())<0.01;}

    private PaintState fontPaint(Font font){
        if(font instanceof XSSFFont xssf){
            XSSFColor color=xssf.getXSSFColor();
            Color fx=xssfColorToFx(color);
            if(fx!=null)return new PaintState(fx,false);
        }
        short index=font.getColor();
        if(index==IndexedColors.AUTOMATIC.getIndex())return new PaintState(Color.BLACK,true);
        Color fx=indexedColorToFx(index);
        return new PaintState(fx==null?Color.BLACK:fx,false);
    }

    private PaintState fillPaint(CellStyle style){
        if(style==null||style.getFillPattern()==FillPatternType.NO_FILL)return new PaintState(Color.TRANSPARENT,true);
        if(style instanceof XSSFCellStyle xssf){
            Color fx=xssfColorToFx(xssf.getFillForegroundXSSFColor());
            if(fx!=null)return new PaintState(fx,false);
        }
        Color fx=indexedColorToFx(style.getFillForegroundColor());
        return new PaintState(fx==null?Color.TRANSPARENT:fx,fx==null);
    }

    private void setFontColor(Font font,Color color){
        if(font instanceof XSSFFont xssf)xssf.setColor(toXssfColor(color));
        else font.setColor(IndexedColors.BLACK.getIndex());
    }

    private void setFillColor(CellStyle style,Color color){
        if(color==null||color.getOpacity()==0){style.setFillPattern(FillPatternType.NO_FILL);return;}
        if(style instanceof XSSFCellStyle xssf)xssf.setFillForegroundColor(toXssfColor(color));
        else style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private XSSFColor toXssfColor(Color color){
        byte[] rgb=new byte[]{(byte)Math.round(color.getRed()*255),(byte)Math.round(color.getGreen()*255),(byte)Math.round(color.getBlue()*255)};
        return new XSSFColor(rgb,new DefaultIndexedColorMap());
    }

    private Color xssfColorToFx(XSSFColor color){
        if(color==null)return null;
        String argb=color.getARGBHex();
        if(argb!=null&&argb.length()>=6){
            String rgb=argb.substring(argb.length()-6);
            try{return Color.web("#"+rgb);}catch(Exception ignored){}
        }
        byte[] rgb=color.getRGB();
        if(rgb!=null&&rgb.length>=3)return Color.rgb(Byte.toUnsignedInt(rgb[0]),Byte.toUnsignedInt(rgb[1]),Byte.toUnsignedInt(rgb[2]));
        return null;
    }

    private Color indexedColorToFx(short index){
        try{
            byte[] rgb=new DefaultIndexedColorMap().getRGB(index);
            if(rgb!=null&&rgb.length>=3)return Color.rgb(Byte.toUnsignedInt(rgb[0]),Byte.toUnsignedInt(rgb[1]),Byte.toUnsignedInt(rgb[2]));
        }catch(Exception ignored){}
        return null;
    }

    private String borderState(CellRangeAddress range){
        if(range.getNumberOfCells()==1){
            CellStyle single=styleAt(range.getFirstRow(),range.getFirstColumn());
            List<String> sides=new ArrayList<>();
            EnumSet<BorderStyle> singleStyles=EnumSet.noneOf(BorderStyle.class);
            if(single.getBorderTop()!=BorderStyle.NONE){sides.add("Top");singleStyles.add(single.getBorderTop());}
            if(single.getBorderRight()!=BorderStyle.NONE){sides.add("Right");singleStyles.add(single.getBorderRight());}
            if(single.getBorderBottom()!=BorderStyle.NONE){sides.add("Bottom");singleStyles.add(single.getBorderBottom());}
            if(single.getBorderLeft()!=BorderStyle.NONE){sides.add("Left");singleStyles.add(single.getBorderLeft());}
            if(sides.isEmpty())return "None";
            String suffix=singleStyles.size()==1?" • "+singleStyles.iterator().next().name().replace('_',' '):"";
            return sides.size()==4?"All"+suffix:String.join(" + ",sides)+suffix;
        }
        boolean any=false,allFour=true,perimeter=true,internal=false;
        EnumSet<BorderStyle> styles=EnumSet.noneOf(BorderStyle.class);
        for(int r=range.getFirstRow();r<=range.getLastRow();r++)for(int c=range.getFirstColumn();c<=range.getLastColumn();c++){
            CellStyle s=styleAt(r,c);
            boolean top=s.getBorderTop()!=BorderStyle.NONE,right=s.getBorderRight()!=BorderStyle.NONE,bottom=s.getBorderBottom()!=BorderStyle.NONE,left=s.getBorderLeft()!=BorderStyle.NONE;
            any|=top||right||bottom||left;
            allFour&=top&&right&&bottom&&left;
            if(r==range.getFirstRow())perimeter&=top;
            if(r==range.getLastRow())perimeter&=bottom;
            if(c==range.getFirstColumn())perimeter&=left;
            if(c==range.getLastColumn())perimeter&=right;
            if(r>range.getFirstRow()&&top)internal=true;
            if(r<range.getLastRow()&&bottom)internal=true;
            if(c>range.getFirstColumn()&&left)internal=true;
            if(c<range.getLastColumn()&&right)internal=true;
            if(top)styles.add(s.getBorderTop());if(right)styles.add(s.getBorderRight());if(bottom)styles.add(s.getBorderBottom());if(left)styles.add(s.getBorderLeft());
        }
        if(!any)return "None";
        String suffix=styles.size()==1?" • "+styles.iterator().next().name().replace('_',' '):"";
        if(allFour)return "All"+suffix;
        if(perimeter&&!internal)return "Outside"+suffix;
        if(internal&&!perimeter)return "Inside"+suffix;
        return "Mixed";
    }

    private String mergeState(CellRangeAddress range){
        Sheet sheet=editorSheet();
        boolean any=false,exact=false,contains=false;
        int hits=0;
        for(int i=0;i<sheet.getNumMergedRegions();i++){
            CellRangeAddress merged=sheet.getMergedRegion(i);
            if(intersects(merged,range)){
                any=true;hits++;
                if(sameRange(merged,range))exact=true;
                if(merged.getFirstRow()<=range.getFirstRow()&&merged.getLastRow()>=range.getLastRow()&&merged.getFirstColumn()<=range.getFirstColumn()&&merged.getLastColumn()>=range.getLastColumn())contains=true;
            }
        }
        if(exact&&hits==1)return "Merged";
        if(contains&&hits==1)return "Inside merged";
        if(any)return "Mixed merge";
        return "Not merged";
    }

    private boolean sameRange(CellRangeAddress a,CellRangeAddress b){return a.getFirstRow()==b.getFirstRow()&&a.getLastRow()==b.getLastRow()&&a.getFirstColumn()==b.getFirstColumn()&&a.getLastColumn()==b.getLastColumn();}

    private CellStyle styleAt(int row,int col){Cell cell=cellAt(editorSheet(),row,col,false);return cell==null?workbook.getCellStyleAt(0):cell.getCellStyle();}

    private void refreshVisibleStyles(CellRangeAddress range){
        for(var entry:editors.entrySet()){
            CellRangeAddress visualRegion=editorRegions.get(entry.getKey());
            if(visualRegion==null||!intersects(visualRegion,range))continue;
            applyEditorVisual(cellAt(editorSheet(),visualRegion.getFirstRow(),visualRegion.getFirstColumn(),false),entry.getValue());
            editorBaseStyles.put(entry.getKey(),entry.getValue().getStyle());
        }
    }

    private void applyEditorVisual(Cell cell,TextField editor){
        if(editor==null)return;
        editor.setStyle("");
        editor.getStyleClass().removeAll("excel-cell-image-placeholder","excel-cell-underlined");
        try{
            CellStyle style=cell==null?workbook.getCellStyleAt(0):cell.getCellStyle();
            Font font=workbook.getFontAt(style.getFontIndex());
            StringBuilder css=new StringBuilder();
            if(font.getBold())css.append("-fx-font-weight:bold;");
            if(font.getItalic())css.append("-fx-font-style:italic;");
            if(font.getUnderline()!=Font.U_NONE)editor.getStyleClass().add("excel-cell-underlined");
            if(font.getFontName()!=null)css.append("-fx-font-family:'").append(font.getFontName().replace("'","")).append("';");
            css.append("-fx-font-size:").append(font.getFontHeightInPoints()).append("px;");

            PaintState fontPaint=fontPaint(font);
            PaintState fillPaint=fillPaint(style);
            Color displayText=accessibleWorkbookText(fontPaint,fillPaint);
            if(displayText!=null)css.append("-fx-text-fill:").append(toCss(displayText)).append(';');
            if(!fillPaint.empty())css.append("-fx-control-inner-background:").append(toCss(fillPaint.color())).append(";-fx-background-color:").append(toCss(fillPaint.color())).append(';');

            if(hasAnyBorder(style)){
                css.append("-fx-border-color:").append(borderColorCss(style,"top")).append(' ').append(borderColorCss(style,"right")).append(' ').append(borderColorCss(style,"bottom")).append(' ').append(borderColorCss(style,"left")).append(';');
                css.append("-fx-border-width:").append(borderWidth(style.getBorderTop())).append(' ').append(borderWidth(style.getBorderRight())).append(' ').append(borderWidth(style.getBorderBottom())).append(' ').append(borderWidth(style.getBorderLeft())).append(';');
                css.append("-fx-border-style:").append(borderCssStyle(style.getBorderTop())).append(' ').append(borderCssStyle(style.getBorderRight())).append(' ').append(borderCssStyle(style.getBorderBottom())).append(' ').append(borderCssStyle(style.getBorderLeft())).append(';');
            }
            editor.setStyle(css.toString());
            editor.setAlignment(switch(style.getAlignment()){case CENTER->Pos.CENTER;case RIGHT->Pos.CENTER_RIGHT;default->Pos.CENTER_LEFT;});
            String text=cellText(cell);
            if(text!=null)TemplateFieldCatalog.excelFieldsFor(template.getDocumentType()).stream().filter(TemplateFieldDefinition::image).filter(f->text.contains("{{"+f.key()+"}}" )).findFirst().ifPresent(f->editor.getStyleClass().add("excel-cell-image-placeholder"));
        }catch(Exception ignored){}
    }

    private Color accessibleWorkbookText(PaintState fontPaint,PaintState fillPaint){
        Color background=!fillPaint.empty()?fillPaint.color():(darkThemeActive()?Color.web("#0f1d2e"):Color.WHITE);
        Color requested=!fontPaint.empty()?fontPaint.color():(darkThemeActive()?Color.web("#edf5ff"):Color.web("#172033"));
        if(contrastRatio(requested,background)>=3.0)return requested;
        return contrastRatio(Color.WHITE,background)>=contrastRatio(Color.BLACK,background)?Color.WHITE:Color.BLACK;
    }
    private boolean darkThemeActive(){return ThemeManager.getCurrentTheme()==ThemeManager.Theme.DARK;}
    private double contrastRatio(Color a,Color b){double la=luminance(a),lb=luminance(b);return (Math.max(la,lb)+0.05)/(Math.min(la,lb)+0.05);}
    private double luminance(Color color){return 0.2126*linear(color.getRed())+0.7152*linear(color.getGreen())+0.0722*linear(color.getBlue());}
    private double linear(double value){return value<=0.03928?value/12.92:Math.pow((value+0.055)/1.055,2.4);}

    private boolean hasAnyBorder(CellStyle style){return style.getBorderTop()!=BorderStyle.NONE||style.getBorderRight()!=BorderStyle.NONE||style.getBorderBottom()!=BorderStyle.NONE||style.getBorderLeft()!=BorderStyle.NONE;}
    private double borderWidth(BorderStyle style){return switch(style){case NONE->0;case HAIR->0.5;case MEDIUM,MEDIUM_DASHED,MEDIUM_DASH_DOT,MEDIUM_DASH_DOT_DOT->2;case THICK,DOUBLE->3;default->1;};}
    private String borderCssStyle(BorderStyle style){return switch(style){case NONE->"solid";case DOTTED,HAIR->"dotted";case DASHED,MEDIUM_DASHED,DASH_DOT,MEDIUM_DASH_DOT,DASH_DOT_DOT,MEDIUM_DASH_DOT_DOT,SLANTED_DASH_DOT->"dashed";default->"solid";};}

    private String borderColorCss(CellStyle style,String side){
        Color color=null;
        if(style instanceof XSSFCellStyle xssf){
            XSSFColor xssfColor=switch(side){case "top"->xssf.getTopBorderXSSFColor();case "right"->xssf.getRightBorderXSSFColor();case "bottom"->xssf.getBottomBorderXSSFColor();default->xssf.getLeftBorderXSSFColor();};
            color=xssfColorToFx(xssfColor);
        }
        if(color==null){short index=switch(side){case "top"->style.getTopBorderColor();case "right"->style.getRightBorderColor();case "bottom"->style.getBottomBorderColor();default->style.getLeftBorderColor();};color=indexedColorToFx(index);}
        return toCss(color==null?Color.BLACK:color);
    }

    private String toCss(Color color){return String.format(Locale.ROOT,"#%02x%02x%02x",Math.round(color.getRed()*255),Math.round(color.getGreen()*255),Math.round(color.getBlue()*255));}

    private Font cloneFont(Font old){
        Font font=workbook.createFont();
        if(old==null)return font;
        font.setFontName(old.getFontName());font.setFontHeight(old.getFontHeight());font.setBold(old.getBold());font.setItalic(old.getItalic());font.setStrikeout(old.getStrikeout());font.setTypeOffset(old.getTypeOffset());font.setUnderline(old.getUnderline());font.setCharSet(old.getCharSet());
        if(old instanceof XSSFFont source&&font instanceof XSSFFont target&&source.getXSSFColor()!=null)target.setColor(source.getXSSFColor());else font.setColor(old.getColor());
        return font;
    }
    private CellStyle cloneStyle(Cell cell){CellStyle style=workbook.createCellStyle();if(cell!=null&&cell.getCellStyle()!=null)style.cloneStyleFrom(cell.getCellStyle());return style;}
    private void copyCellValue(Cell source,Cell target){if(target==null)return;if(source==null){target.setBlank();return;}target.setCellStyle(source.getCellStyle());switch(source.getCellType()){case STRING->target.setCellValue(source.getStringCellValue());case NUMERIC->target.setCellValue(source.getNumericCellValue());case BOOLEAN->target.setCellValue(source.getBooleanCellValue());case FORMULA->target.setCellFormula(source.getCellFormula());case ERROR->target.setCellErrorValue(source.getErrorCellValue());default->target.setBlank();}}
    private String rangeText(CellRangeAddress range){String first=columnName(range.getFirstColumn())+(range.getFirstRow()+1);String last=columnName(range.getLastColumn())+(range.getLastRow()+1);return first.equals(last)?first:first+":"+last;}
    private CellRangeAddress copyRange(CellRangeAddress range){return new CellRangeAddress(range.getFirstRow(),range.getLastRow(),range.getFirstColumn(),range.getLastColumn());}
    private boolean intersects(CellRangeAddress a,CellRangeAddress b){return a.getFirstRow()<=b.getLastRow()&&a.getLastRow()>=b.getFirstRow()&&a.getFirstColumn()<=b.getLastColumn()&&a.getLastColumn()>=b.getFirstColumn();}
    private String ask(String title,String prompt,String initial){org.example.util.OwnedTextInputDialog d=new org.example.util.OwnedTextInputDialog(initial==null?"":initial);d.setTitle(title);d.setHeaderText(null);d.setContentText(prompt);return d.showAndWait().map(String::trim).orElse(null);}
    private void closeWorkbook(){NavigationGuardRegistry.clear(root);Workbook current=workbook;workbook=null;try{if(current!=null)current.close();}catch(Exception ignored){}}
    private static String columnName(int c){return CellReference.convertNumToColString(c);}
    private static String number(double d){return Math.rint(d)==d?String.format(Locale.ROOT,"%.0f",d):String.format(Locale.ROOT,"%.4f",d).replaceAll("0+$","").replaceAll("\\.$","");}
    private static String descriptionWithRemarks(String description,String remarks){String d=description==null?"":description.trim(),r=remarks==null?"":remarks.trim();return d.isBlank()?r:r.isBlank()?d:d+"\n"+r;}
    private static String rootMessage(Throwable e){Throwable r=e;while(r.getCause()!=null&&r.getCause()!=r)r=r.getCause();return r.getMessage()==null?r.getClass().getSimpleName():r.getMessage();}
}
