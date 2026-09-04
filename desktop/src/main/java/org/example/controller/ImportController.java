package org.example.controller;

import javafx.scene.layout.StackPane;
import org.example.util.OwnedAlert;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.service.ImportService;
import org.example.importing.ImportModuleRegistry;
import org.example.importing.ImportMappingSupport;
import org.example.importing.ImportTemplateService;
import org.example.importing.ImportResultReportService;
import org.example.importing.ImportResultPolicy;
import org.example.importing.ImportPreviewService;
import org.example.importing.PurchaseReconImportCoordinator;
import org.example.importing.BankStatementImportCoordinator;
import org.example.shared.RuntimeContract;
import org.example.api.recon.PurchaseReconApiClient;
import org.example.util.IconFactory;
import org.example.util.SpreadsheetLayoutDetector;
import org.example.util.BusinessClock;
import org.example.util.ModernDialog;
import org.example.util.SemanticTableCells;
import org.example.navigation.NavigationGuardRegistry;
import org.example.navigation.NavigationManager;
import org.example.config.WorkspaceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for the shared ERP Excel import screen.
 *
 * Features:
 * - Drag and drop Excel files
 * - Module-specific templates
 * - Automatic column mapping
 * - Responsive column mapping display
 * - Responsive data preview
 * - Dry-run validation
 * - Background import execution
 */
public class ImportController {

    /* =========================================================
       FXML CONTROLS
       ========================================================= */

    @FXML private ComboBox<String> cmbImportModule;
    @FXML private ComboBox<String> cmbImportMode;
    @FXML private TextField txtImportNote;

    @FXML private GridPane gridMapping;
    @FXML private ScrollPane mappingScrollPane;

    @FXML private Label lblChosenFile;
    @FXML private Label lblDropIcon;
    @FXML private StackPane importPageIcon;
    @FXML private Label lblMappingCount;
    @FXML private Label lblPreviewCount;
    @FXML private Label lblPreviewStatus;
    @FXML private Label lblReadyStatus;
    @FXML private Label lblProgressStatus;
    @FXML private Label lblProgressPercent;

    @FXML private Button btnChooseFile;
    @FXML private Button btnRunImport;
    @FXML private Button btnAutoMap;
    @FXML private Button btnResetMapping;
    @FXML private Button btnImportGuide;

    @FXML private VBox dropZone;
    @FXML private VBox progressContainer;

    @FXML private TableView<Map<String, String>> tblPreview;
    @FXML private TableView<Map<String, String>> tblValidation;
    @FXML private TabPane reviewTabs;
    @FXML private Tab dataPreviewTab, validationResultsTab;

    @FXML private ProgressBar progressBar;
    @FXML private CheckBox chkDryRun;
    @FXML private VBox stepSelect, stepUpload, stepMap, stepReview, importCompletedPanel;
    @FXML private Label step1Badge, step2Badge, step3Badge, step4Badge, lblImportCompletedSummary;
    @FXML private javafx.scene.layout.Region wizardLine1, wizardLine2, wizardLine3;
    @FXML private StackPane itemChoiceIcon, customerChoiceIcon, supplierChoiceIcon, salesChoiceIcon, purchaseChoiceIcon, purchaseReconChoiceIcon, masterChoiceIcon, bankStatementChoiceIcon, selectedFileExcelIcon;
    @FXML private Button btnDownloadItemTemplate, btnDownloadCustomerTemplate, btnDownloadSupplierTemplate,
                         btnDownloadSalesTemplate, btnDownloadPurchaseTemplate, btnDownloadPurchaseReconTemplate, btnDownloadMasterTemplate;

    /* =========================================================
       SERVICES AND STATE
       ========================================================= */

    private final ImportService importService = new ImportService();
    private final ImportTemplateService importTemplateService = new ImportTemplateService();
    private final ImportPreviewService importPreviewService = new ImportPreviewService();
    private final PurchaseReconImportCoordinator purchaseReconImportCoordinator = new PurchaseReconImportCoordinator();
    private final BankStatementImportCoordinator bankStatementImportCoordinator = new BankStatementImportCoordinator();

    /*
     * LinkedHashMap is important because it keeps domain fields
     * and preview columns in a predictable order.
     */
    private final Map<String, ComboBox<String>> mappingControls =
        new LinkedHashMap<>();

    private final Map<String, Label> mappingStatusLabels =
        new LinkedHashMap<>();

    private final Map<String, Label> requiredStatusLabels =
        new LinkedHashMap<>();

    private File selectedFile;

    private SpreadsheetLayoutDetector.Layout selectedLayout;

    private List<String> currentHeaders = List.of();

    private boolean rebuildingMapping;
    private volatile boolean preflightPassed;
    private ImportService.ImportResult lastPreflightResult;
    private int currentWizardStep = 1;
    private boolean importRunning;
    private boolean importCompleted;
    private String completedModule;

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    @FXML
    private void initialize() {
        if (importPageIcon != null) importPageIcon.getChildren().setAll(IconFactory.icon("import", 24));

        cmbImportModule.setItems(
            FXCollections.observableArrayList(
                "Item Master",
                "Customers/CRM",
                "Suppliers/HRM",
                "Sales",
                "Purchases",
                "Master Categories and Values",
                "Purchase Recon",
                "Bank Statement"
            )
        );

        cmbImportModule.getSelectionModel().selectFirst();
        configureImportModeForModule(cmbImportModule.getValue());

        String requestedModule = ImportScreenContext.consume();

        if (
            requestedModule != null
                && cmbImportModule.getItems().contains(requestedModule)
        ) {
            cmbImportModule.setValue(requestedModule);
        }
        configureImportModeForModule(cmbImportModule.getValue());

        configureIcons();
        configurePreviewTable();

        progressContainer.setVisible(false);
        progressContainer.setManaged(false);

        btnRunImport.setDisable(true);

        showWizardStep(1);
        Platform.runLater(() -> NavigationGuardRegistry.install(btnRunImport, this::allowNavigationAway));

        cmbImportModule.valueProperty().addListener(
            (observable, oldValue, newValue) -> {
                configureImportModeForModule(newValue);

                if (rebuildingMapping || selectedFile == null) {
                    return;
                }

                invalidatePreflight("Import type changed • Run validation again");
                reloadSelectedWorkbookForModule();
            }
        );
        cmbImportMode.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (Objects.equals(oldValue, newValue) || selectedFile == null) return;
            invalidatePreflight("Import mode changed • Run validation again");
            updateMappingSummary();
        });
    }


    private void configureImportModeForModule(String module){
        if(cmbImportMode==null)return;
        String current=cmbImportMode.getValue();
        if("Sales".equals(module)||"Purchases".equals(module)){
            cmbImportMode.setItems(FXCollections.observableArrayList("Create new • Skip existing posted documents"));
            cmbImportMode.setTooltip(new Tooltip("Existing posted Sales/Purchase documents are protected from generic bulk updates."));
            cmbImportMode.getSelectionModel().selectFirst();
        }else if("Purchase Recon".equals(module)){
            cmbImportMode.setItems(FXCollections.observableArrayList("Reconciliation duplicate policy (managed automatically)"));
            cmbImportMode.setTooltip(new Tooltip("Purchase Recon applies its own duplicate and reconciliation rules."));
            cmbImportMode.getSelectionModel().selectFirst();
        }else if("Bank Statement".equals(module)){
            cmbImportMode.setItems(FXCollections.observableArrayList("Statement duplicate policy (managed automatically)"));
            cmbImportMode.setTooltip(new Tooltip("Bank Statement applies its own statement/batch duplicate rules."));
            cmbImportMode.getSelectionModel().selectFirst();
        }else{
            cmbImportMode.setItems(FXCollections.observableArrayList(
                    "Update non-blank fields (recommended)","Create new only","Create or update","Skip existing"));
            cmbImportMode.setTooltip(new Tooltip("Choose how matching existing master records are handled. Changing this policy requires validation again."));
            if(current!=null&&cmbImportMode.getItems().contains(current))cmbImportMode.setValue(current);
            else cmbImportMode.getSelectionModel().selectFirst();
        }
        cmbImportMode.setDisable(importRunning||isFixedImportPolicy(module));
    }

    private boolean isFixedImportPolicy(String module){
        return "Sales".equals(module)||"Purchases".equals(module)||"Purchase Recon".equals(module)||"Bank Statement".equals(module);
    }

    private ImportService.ImportMode selectedImportMode(){
        String module=cmbImportModule==null?null:cmbImportModule.getValue();
        if("Sales".equals(module)||"Purchases".equals(module))return ImportService.ImportMode.CREATE_ONLY;
        if("Purchase Recon".equals(module)||"Bank Statement".equals(module))return ImportService.ImportMode.SKIP_EXISTING;
        int index=cmbImportMode==null?-1:cmbImportMode.getSelectionModel().getSelectedIndex();
        return switch(index){case 1->ImportService.ImportMode.CREATE_ONLY;case 2->ImportService.ImportMode.UPSERT;case 3->ImportService.ImportMode.SKIP_EXISTING;default->ImportService.ImportMode.UPDATE_NON_BLANK;};
    }

    @FXML private void selectItemMaster(){ selectModuleAndContinue("Item Master"); }
    @FXML private void selectCustomers(){ selectModuleAndContinue("Customers/CRM"); }
    @FXML private void selectSuppliers(){ selectModuleAndContinue("Suppliers/HRM"); }
    @FXML private void selectSales(){ selectModuleAndContinue("Sales"); }
    @FXML private void selectPurchases(){ selectModuleAndContinue("Purchases"); }
    @FXML private void selectMasterValues(){ selectModuleAndContinue("Master Categories and Values"); }
    @FXML private void selectPurchaseRecon(){ selectModuleAndContinue("Purchase Recon"); }
    @FXML private void selectBankStatement(){ selectModuleAndContinue("Bank Statement"); }
    private void selectModuleAndContinue(String module){ cmbImportModule.setValue(module); showWizardStep(2); }
    @FXML private void wizardBackToSelect(){ showWizardStep(1); }
    @FXML private void wizardBackToUpload(){ showWizardStep(2); }
    @FXML private void wizardBackToMap(){ showWizardStep(3); }
    @FXML private void wizardContinueUpload(){ if(selectedFile==null){showWarning("Choose a file","Select an import file before continuing.");return;} showWizardStep(3); }
    @FXML private void wizardContinueMap(){ if(!requiredMappingsComplete()){showWarning("Required mappings are missing","Map all required fields before continuing.");return;} showWizardStep(4); runPreflightValidation(); }
    private void showWizardStep(int step){
        currentWizardStep = Math.max(1, Math.min(4, step));
        if (importCompletedPanel != null && step != 4) { importCompletedPanel.setVisible(false); importCompletedPanel.setManaged(false); }
        VBox[] panes={stepSelect,stepUpload,stepMap,stepReview};
        for(int i=0;i<panes.length;i++) {
            if(panes[i]!=null){
                panes[i].setVisible(i==step-1);
                panes[i].setManaged(i==step-1);
            }
        }

        Label[] badges={step1Badge,step2Badge,step3Badge,step4Badge};
        for(int i=0;i<badges.length;i++) {
            if(badges[i]==null) continue;
            badges[i].getStyleClass().removeAll("wizard-active","wizard-done","wizard-pending");
            if(i < step-1) {
                badges[i].setText("✓");
                badges[i].getStyleClass().add("wizard-done");
            } else {
                badges[i].setText(String.valueOf(i+1));
                badges[i].getStyleClass().add(i==step-1 ? "wizard-active" : "wizard-pending");
            }
        }

        javafx.scene.layout.Region[] lines={wizardLine1,wizardLine2,wizardLine3};
        for(int i=0;i<lines.length;i++) {
            if(lines[i]==null) continue;
            lines[i].getStyleClass().removeAll("wizard-line-done","wizard-line-active","wizard-line-pending");
            if(i < step-1) lines[i].getStyleClass().add("wizard-line-done");
            else if(i == step-1 && step < 4) lines[i].getStyleClass().add("wizard-line-active");
            else lines[i].getStyleClass().add("wizard-line-pending");
        }
    }

    private void configureIcons() {

        btnChooseFile.setGraphic(IconFactory.compactIcon("folder", 16));
        btnRunImport.setGraphic(IconFactory.compactIcon("import", 16));
        lblDropIcon.setGraphic(IconFactory.icon("import", 30));

        if (btnImportGuide != null) btnImportGuide.setGraphic(IconFactory.compactIcon("document", 16));
        if (btnAutoMap != null) btnAutoMap.setGraphic(IconFactory.compactIcon("settings", 16));
        if (btnResetMapping != null) btnResetMapping.setGraphic(IconFactory.compactIcon("reset", 16));

        Button[] templateButtons = {
            btnDownloadItemTemplate, btnDownloadCustomerTemplate, btnDownloadSupplierTemplate,
            btnDownloadSalesTemplate, btnDownloadPurchaseTemplate, btnDownloadPurchaseReconTemplate, btnDownloadMasterTemplate
        };
        for (Button button : templateButtons) {
            if (button != null) button.setGraphic(IconFactory.compactIcon("excel", 16));
        }

        if (itemChoiceIcon != null) itemChoiceIcon.getChildren().setAll(IconFactory.icon("item", 46));
        if (customerChoiceIcon != null) customerChoiceIcon.getChildren().setAll(IconFactory.icon("customer", 46));
        if (supplierChoiceIcon != null) supplierChoiceIcon.getChildren().setAll(IconFactory.icon("supplier", 46));
        if (salesChoiceIcon != null) salesChoiceIcon.getChildren().setAll(IconFactory.icon("sale", 46));
        if (purchaseChoiceIcon != null) purchaseChoiceIcon.getChildren().setAll(IconFactory.icon("purchase", 46));
        if (purchaseReconChoiceIcon != null) purchaseReconChoiceIcon.getChildren().setAll(IconFactory.icon("reconcile", 46));
        if (bankStatementChoiceIcon != null) bankStatementChoiceIcon.getChildren().setAll(IconFactory.icon("bank", 46));
        if (masterChoiceIcon != null) masterChoiceIcon.getChildren().setAll(IconFactory.icon("master", 46));
        if (selectedFileExcelIcon != null) selectedFileExcelIcon.getChildren().setAll(IconFactory.icon("excel", 30));
    }

    private void configurePreviewTable() {

        tblPreview.setPlaceholder(
            new Label("Select an import file to preview its data.")
        );
    }

    /* =========================================================
       MODULE HELPERS
       ========================================================= */

    private List<String> getDomainFieldsForModule() {
        return ImportModuleRegistry.fields(cmbImportModule.getValue());
    }


    private Set<String> getRequiredFieldsForModule() {
        return ImportModuleRegistry.requiredFields(cmbImportModule.getValue());
    }


    private String getDataTypeForField(String field) {
        return ImportModuleRegistry.dataType(field);
    }


    private String humanize(String field) {
        return ImportModuleRegistry.humanize(field);
    }


    /* =========================================================
       FILE INSPECTION AND MAPPING
       ========================================================= */

    private List<String> readHeaders(File file) {
        ImportPreviewService.Inspection inspection = importPreviewService.inspect(
            file, cmbImportModule.getValue(), getDomainFieldsForModule());
        selectedLayout = inspection.layout();
        return inspection.headers();
    }

    private Map<String, String> generateAutoMapping(
        List<String> headers
    ) {
        return ImportMappingSupport.autoMap(getDomainFieldsForModule(), headers);
    }




    /* =========================================================
       RESPONSIVE MAPPING GRID
       ========================================================= */

    private void buildMappingGrid(
        List<String> headers,
        Map<String, String> autoMapping
    ) {

        rebuildingMapping = true;

        try {

            gridMapping.getChildren().clear();
            gridMapping.getColumnConstraints().clear();

            mappingControls.clear();
            mappingStatusLabels.clear();
            requiredStatusLabels.clear();

            configureMappingGridColumns();

            addMappingHeader();

            List<String> domainFields =
                getDomainFieldsForModule();

            Set<String> requiredFields =
                getRequiredFieldsForModule();

            int rowIndex = 1;

            for (String field : domainFields) {

                addMappingRow(
                    rowIndex,
                    field,
                    headers,
                    autoMapping.get(field),
                    requiredFields.contains(field)
                );

                rowIndex++;
            }

            updateMappingSummary();
            schedulePreviewRefresh();

        } finally {
            rebuildingMapping = false;
        }
    }

    private void configureMappingGridColumns() {

        ColumnConstraints handleColumn =
            new ColumnConstraints(36);

        ColumnConstraints systemFieldColumn =
            new ColumnConstraints();

        systemFieldColumn.setMinWidth(160);
        systemFieldColumn.setPrefWidth(210);
        systemFieldColumn.setHgrow(Priority.SOMETIMES);

        ColumnConstraints excelColumn =
            new ColumnConstraints();

        excelColumn.setMinWidth(300);
        excelColumn.setPrefWidth(450);
        excelColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints typeColumn =
            new ColumnConstraints(130);

        ColumnConstraints requiredColumn =
            new ColumnConstraints(95);

        ColumnConstraints statusColumn =
            new ColumnConstraints(115);

        gridMapping
            .getColumnConstraints()
            .addAll(
                handleColumn,
                systemFieldColumn,
                excelColumn,
                typeColumn,
                requiredColumn,
                statusColumn
            );
    }

    private void addMappingHeader() {

        addMappingHeaderLabel("", 0);
        addMappingHeaderLabel("System Field", 1);
        addMappingHeaderLabel("Excel Column", 2);
        addMappingHeaderLabel("Data Type / Format", 3);
        addMappingHeaderLabel("Required", 4);
        addMappingHeaderLabel("Status", 5);
    }

    private void addMappingHeaderLabel(
        String text,
        int column
    ) {

        Label label = new Label(text);
        label.getStyleClass().add("import-mapping-header");

        GridPane.setHgrow(label, Priority.ALWAYS);
        label.setMaxWidth(Double.MAX_VALUE);

        gridMapping.add(label, column, 0);
    }

    private void addMappingRow(
        int rowIndex,
        String field,
        List<String> headers,
        String mappedHeader,
        boolean required
    ) {

        Label dragHandle = new Label("⋮⋮");
        dragHandle.getStyleClass().add("import-mapping-handle");

        Label fieldLabel = new Label(humanize(field));
        fieldLabel.getStyleClass().add("import-mapping-field");

        ComboBox<String> mappingCombo =
            new ComboBox<>(
                FXCollections.observableArrayList(headers)
            );

        mappingCombo.setPromptText("Select Excel column");
        mappingCombo.setMaxWidth(Double.MAX_VALUE);
        mappingCombo.getStyleClass().add(
            "import-mapping-combo"
        );

        if (mappedHeader != null) {
            mappingCombo.setValue(mappedHeader);
        }

        Label dataTypeLabel =
            new Label(getDataTypeForField(field));

        dataTypeLabel
            .getStyleClass()
            .add("import-data-type");

        Label requiredLabel =
            new Label(required ? "Required" : "Optional");

        requiredLabel
            .getStyleClass()
            .add(
                required
                    ? "import-required"
                    : "import-optional"
            );

        Label statusLabel = new Label();

        mappingControls.put(field, mappingCombo);
        mappingStatusLabels.put(field, statusLabel);
        requiredStatusLabels.put(field, requiredLabel);

        updateRowMappingStatus(
            field,
            mappingCombo.getValue()
        );

        mappingCombo
            .valueProperty()
            .addListener(
                (
                    observable,
                    oldValue,
                    newValue
                ) -> {

                    if (rebuildingMapping) {
                        return;
                    }

                    updateRowMappingStatus(
                        field,
                        newValue
                    );

                    invalidatePreflight("Mappings changed • Run validation again");
                    updateMappingSummary();
                    schedulePreviewRefresh();
                }
            );

        addMappingCell(dragHandle, 0, rowIndex);
        addMappingCell(fieldLabel, 1, rowIndex);
        addMappingCell(mappingCombo, 2, rowIndex);
        addMappingCell(dataTypeLabel, 3, rowIndex);
        addMappingCell(requiredLabel, 4, rowIndex);
        addMappingCell(statusLabel, 5, rowIndex);
    }

    private void addMappingCell(
        Control control,
        int column,
        int row
    ) {

        control.getStyleClass().add(
            "import-mapping-cell"
        );

        control.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(control, Priority.ALWAYS);

        gridMapping.add(control, column, row);
    }

    private void updateRowMappingStatus(
        String field,
        String mappedHeader
    ) {

        Label statusLabel =
            mappingStatusLabels.get(field);

        if (statusLabel == null) {
            return;
        }

        statusLabel
            .getStyleClass()
            .removeAll(
                "import-mapped-status",
                "import-unmapped-status"
            );

        if (
            mappedHeader != null
                && !mappedHeader.isBlank()
        ) {

            statusLabel.setText("Mapped");
            statusLabel
                .getStyleClass()
                .add("import-mapped-status");

        } else {

            statusLabel.setText("Not mapped");
            statusLabel
                .getStyleClass()
                .add("import-unmapped-status");
        }
    }

    private void updateMappingSummary() {

        int total = mappingControls.size();

        long mapped =
            mappingControls
                .values()
                .stream()
                .filter(
                    combo ->
                        combo.getValue() != null
                            && !combo
                            .getValue()
                            .isBlank()
                )
                .count();

        lblMappingCount.setText(
            mapped + " of " + total + " fields mapped"
        );

        boolean mappingsComplete = requiredMappingsComplete();
        btnRunImport.setDisable(!isImportReady());

        if (selectedFile == null) {
            lblReadyStatus.setText(
                "Bank Statement".equals(cmbImportModule.getValue()) ? "Choose a bank statement CSV to begin" : "Choose an Excel file to begin"
            );
        } else if (!mappingsComplete) {
            lblReadyStatus.setText(
                "Map all required fields before importing"
            );
        } else if (!preflightPassed) {
            lblReadyStatus.setText("Run validation before importing");
        } else {
            lblReadyStatus.setText("All validations passed • Ready to import " + selectedFile.getName());
        }
    }

    private boolean isImportReady() {
        return !importRunning && selectedFile != null && preflightPassed;
    }

    private void invalidatePreflight(String status) {
        preflightPassed = false;
        lastPreflightResult = null;
        if (btnRunImport != null) btnRunImport.setDisable(true);
        if (status != null && !status.isBlank() && lblReadyStatus != null) lblReadyStatus.setText(status);
    }

    private boolean requiredMappingsComplete() {

        Set<String> requiredFields =
            getRequiredFieldsForModule();

        for (String field : requiredFields) {

            ComboBox<String> combo =
                mappingControls.get(field);

            if (
                combo == null
                    || combo.getValue() == null
                    || combo
                    .getValue()
                    .isBlank()
            ) {
                return false;
            }
        }

        return true;
    }

    @FXML
    private void autoMapColumns() {

        if (selectedFile == null) {
            showWarning(
                "No workbook selected",
                "Choose an Excel workbook first."
            );
            return;
        }

        Map<String, String> autoMapping =
            generateAutoMapping(currentHeaders);

        rebuildingMapping = true;

        try {

            mappingControls.forEach(
                (field, combo) ->
                    combo.setValue(
                        autoMapping.get(field)
                    )
            );

        } finally {
            rebuildingMapping = false;
        }

        invalidatePreflight("Mappings changed • Run validation again");
        refreshAllMappingStatuses();
        updateMappingSummary();
        schedulePreviewRefresh();
    }

    @FXML
    private void resetMapping() {

        rebuildingMapping = true;

        try {
            mappingControls
                .values()
                .forEach(
                    combo ->
                        combo
                            .getSelectionModel()
                            .clearSelection()
                );
        } finally {
            rebuildingMapping = false;
        }

        invalidatePreflight("Mappings changed • Run validation again");
        refreshAllMappingStatuses();
        updateMappingSummary();
        schedulePreviewRefresh();
    }

    private void refreshAllMappingStatuses() {

        mappingControls.forEach(
            (field, combo) ->
                updateRowMappingStatus(
                    field,
                    combo.getValue()
                )
        );
    }

    /* =========================================================
       RESPONSIVE PREVIEW TABLE
       ========================================================= */

    private void schedulePreviewRefresh() {

        if (selectedFile == null) {

            tblPreview
                .getColumns()
                .clear();

            tblPreview
                .getItems()
                .clear();

            lblPreviewCount.setText("0 rows");
            lblPreviewStatus.setText(
                "No preview loaded"
            );

            return;
        }

        buildPreviewColumns();
        loadPreviewRows();
    }

    private void buildPreviewColumns() {

        tblPreview.getColumns().clear();
        tblPreview.setRowFactory(null);

        List<String> mappedFields =
            getMappedFieldsInDomainOrder();

        if ("Purchase Recon".equals(cmbImportModule.getValue())) {
            TableColumn<Map<String, String>, String> sheetColumn = new TableColumn<>("Sheet");
            sheetColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getOrDefault("_source_sheet", "")));
            IconFactory.applyTableHeaderIcon(sheetColumn, "document");
            tblPreview.getColumns().add(sheetColumn);
            TableColumn<Map<String, String>, String> rowColumn = new TableColumn<>("Row");
            rowColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getOrDefault("_source_row", "")));
            IconFactory.applyTableHeaderIcon(rowColumn, "reference");
            tblPreview.getColumns().add(rowColumn);
        }

        for (String field : mappedFields) {

            TableColumn<Map<String, String>, String> column =
                new TableColumn<>(humanize(field));

            column.setCellValueFactory(
                cellData ->
                    new SimpleStringProperty(
                        cellData
                            .getValue()
                            .getOrDefault(field, "")
                    )
            );
            IconFactory.applyTableHeaderIcon(column, semanticIconForField(field));

            tblPreview
                .getColumns()
                .add(column);
        }
    }

    private List<String> getMappedFieldsInDomainOrder() {

        List<String> mappedFields =
            new ArrayList<>();

        for (String field : getDomainFieldsForModule()) {

            ComboBox<String> combo =
                mappingControls.get(field);

            if (
                combo != null
                    && combo.getValue() != null
                    && !combo
                    .getValue()
                    .isBlank()
            ) {
                mappedFields.add(field);
            }
        }

        return mappedFields;
    }

    private String semanticIconForField(String field) {
        return switch (field == null ? "" : field) {
            case "invoice_date", "transaction_date", "value_date" -> "calendar";
            case "party_code" -> "reference";
            case "contact_person", "name" -> "customer";
            case "item_code", "description", "remarks" -> "item";
            case "category", "category_code", "category_name", "value", "value_code" -> "category";
            case "unit" -> "unit";
            case "hsn", "gst", "gst_percent", "gstin", "discount_percent" -> "tax";
            case "quantity", "opening_stock", "minimum_stock" -> "quantity";
            case "purchase_price", "selling_price", "rate", "amount", "balance", "opening_balance", "paid_amount",
                 "charge_1_amount", "charge_2_amount" -> "currency";
            case "charge_1_type", "charge_2_type", "additional_charges" -> "expense";
            case "charge_1_taxable", "charge_2_taxable", "charge_1_gst_percent", "charge_2_gst_percent" -> "tax";
            case "attachment_file", "attachment_files" -> "attachment";
            case "email" -> "email";
            case "phone" -> "phone";
            case "address", "location" -> "location";
            case "reference" -> "link";
            case "direction" -> "status";
            case "is_active" -> "status";
            default -> "document";
        };
    }

    private void loadPreviewRows() {
        try {
            ImportPreviewService.Preview preview = importPreviewService.preview(
                selectedFile, cmbImportModule.getValue(), collectCurrentMapping(), getMappedFieldsInDomainOrder());
            tblPreview.getItems().setAll(preview.rows());
            String suffix = "Purchase Recon".equals(cmbImportModule.getValue())
                ? " • " + preview.sheetCount() + " sheet" + (preview.sheetCount() == 1 ? "" : "s")
                : "";
            lblPreviewCount.setText(preview.rows().size() + " rows shown" + suffix);
            lblPreviewStatus.setText(preview.message());
            lblPreviewStatus.getStyleClass().removeAll("import-success-text", "import-warning-text");
            lblPreviewStatus.getStyleClass().add(preview.success() ? "import-success-text" : "import-warning-text");
        } catch (Exception exception) {
            tblPreview.getItems().clear();
            lblPreviewCount.setText("0 rows");
            lblPreviewStatus.setText("Preview could not be loaded: " + safeMessage(exception));
            lblPreviewStatus.getStyleClass().removeAll("import-success-text", "import-warning-text");
            lblPreviewStatus.getStyleClass().add("import-warning-text");
        }
    }

    /* =========================================================
       FILE SELECTION AND DRAG/DROP
       ========================================================= */

    @FXML
    private void onChooseImportFile() {

        FileChooser chooser = new FileChooser();

        boolean bankStatement = "Bank Statement".equals(cmbImportModule.getValue());
        chooser.setTitle(bankStatement ? "Select Bank Statement CSV" : "Select Excel File");

        chooser
            .getExtensionFilters()
            .add(
                bankStatement
                    ? new FileChooser.ExtensionFilter("Bank statement CSV", "*.csv")
                    : new FileChooser.ExtensionFilter("Excel workbooks", "*.xlsx", "*.xls")
            );

        File file =
            chooser.showOpenDialog(
                btnChooseFile
                    .getScene()
                    .getWindow()
            );

        if (file != null) {
            selectFile(file);
        }
    }

    @FXML
    private void onDragOver(DragEvent event) {

        if (
            event
                .getDragboard()
                .hasFiles()
                && isSupportedImportFile(
                event
                    .getDragboard()
                    .getFiles()
                    .getFirst()
            )
        ) {
            event.acceptTransferModes(
                TransferMode.COPY
            );
        }

        event.consume();
    }

    @FXML
    private void onDragEntered(DragEvent event) {

        if (
            event
                .getDragboard()
                .hasFiles()
        ) {

            if (
                !dropZone
                    .getStyleClass()
                    .contains("drag-active")
            ) {
                dropZone
                    .getStyleClass()
                    .add("drag-active");
            }
        }

        event.consume();
    }

    @FXML
    private void onDragExited(DragEvent event) {

        dropZone
            .getStyleClass()
            .remove("drag-active");

        event.consume();
    }

    @FXML
    private void onDragDropped(DragEvent event) {

        dropZone
            .getStyleClass()
            .remove("drag-active");

        boolean accepted = false;

        if (
            event
                .getDragboard()
                .hasFiles()
        ) {

            File file =
                event
                    .getDragboard()
                    .getFiles()
                    .getFirst();

            if (isSupportedImportFile(file)) {

                selectFile(file);
                accepted = true;

            } else {

                showWarning(
                    "Unsupported file",
                    "Bank Statement".equals(cmbImportModule.getValue()) ? "Please choose a Kotak CSV statement (.csv)." : "Please choose an Excel workbook (.xlsx or .xls)."
                );
            }
        }

        event.setDropCompleted(accepted);
        event.consume();
    }

    private boolean isSupportedImportFile(File file) {

        String name =
            file == null
                ? ""
                : file
                .getName()
                .toLowerCase(Locale.ROOT);

        if ("Bank Statement".equals(cmbImportModule.getValue())) return name.endsWith(".csv");
        return name.endsWith(".xlsx") || name.endsWith(".xls");
    }

    private void selectFile(File file) {

        if (!isSupportedImportFile(file)) {

            showWarning(
                "Unsupported file",
                "Bank Statement".equals(cmbImportModule.getValue()) ? "Please choose a Kotak CSV statement (.csv)." : "Please choose an Excel workbook (.xlsx or .xls)."
            );

            return;
        }

        List<String> headers;

        try {

            headers = readHeaders(file);

        } catch (Exception exception) {

            showWarning(
                "Workbook cannot be read",
                safeMessage(exception)
            );

            return;
        }

        if (headers.isEmpty()) {

            showWarning(
                "Workbook cannot be read",
                "No usable column headings were found "
                    + "in any worksheet."
            );

            return;
        }

        selectedFile = file;
        currentHeaders = List.copyOf(headers);
        invalidatePreflight("File changed • Run validation again");

        long sizeInKb =
            Math.max(
                1,
                file.length() / 1024
            );

        lblChosenFile.setText(
            file.getName()
                + "  •  "
                + sizeInKb
                + " KB"
        );

        buildMappingGrid(
            currentHeaders,
            generateAutoMapping(currentHeaders)
        );

        progressBar.setProgress(0);

        updateMappingSummary();
    }

    private void reloadSelectedWorkbookForModule() {

        if (selectedFile == null) {
            return;
        }

        try {

            currentHeaders =
                List.copyOf(
                    readHeaders(selectedFile)
                );

            buildMappingGrid(
                currentHeaders,
                generateAutoMapping(currentHeaders)
            );

        } catch (Exception exception) {

            showWarning(
                "Workbook cannot be read",
                safeMessage(exception)
            );
        }
    }

    private void runPreflightValidation() {
        if (selectedFile == null || !requiredMappingsComplete()) return;
        preflightPassed = false;
        lastPreflightResult = null;
        if (tblValidation != null) { tblValidation.getColumns().clear(); tblValidation.getItems().clear(); }
        if (reviewTabs != null && dataPreviewTab != null) reviewTabs.getSelectionModel().select(dataPreviewTab);
        btnRunImport.setDisable(true);
        lblReadyStatus.setText("Validating format, mandatory fields and master references...");
        lblPreviewStatus.setText("Validation in progress...");
        Map<String,String> mapping = collectCurrentMapping();
        ImportService.ImportMode mode = selectedImportMode();
        String module = cmbImportModule.getValue();
        Task<ImportService.ImportResult> task = new Task<>() {
            @Override protected ImportService.ImportResult call() throws Exception {
                return executeImport(module, mapping, true, mode);
            }
        };
        task.setOnSucceeded(e -> {
            lastPreflightResult = task.getValue();
            showValidationTable(lastPreflightResult);
            preflightPassed = lastPreflightResult != null && lastPreflightResult.failedCount() == 0
                && lastPreflightResult.details.stream().noneMatch(r -> "FAILED".equalsIgnoreCase(r.status));
            lblReadyStatus.getStyleClass().removeAll("import-success-text","import-warning-text");
            if (preflightPassed) {
                lblReadyStatus.setText("All validations passed • Ready to import");
                lblReadyStatus.getStyleClass().add("import-success-text");
            } else {
                lblReadyStatus.setText("Validation failed • Fix the red rows before import");
                lblReadyStatus.getStyleClass().add("import-warning-text");
            }
            if (reviewTabs != null && validationResultsTab != null && !preflightPassed) reviewTabs.getSelectionModel().select(validationResultsTab);
            updateMappingSummary();
        });
        task.setOnFailed(e -> {
            preflightPassed = false;
            if (tblValidation != null) tblValidation.getItems().clear();
            lblPreviewStatus.setText("Validation failed: " + safeMessage(task.getException()));
            lblReadyStatus.setText("Validation failed • Import blocked");
            updateMappingSummary();
        });
        Thread thread = new Thread(task, "dse-import-preflight");
        thread.setDaemon(true);
        thread.start();
    }

    private void showValidationTable(ImportService.ImportResult result) {
        tblValidation.getColumns().clear();
        addValidationColumn("Sheet / Rows", "rows");
        addValidationColumn("Record / Reference", "reference");
        addValidationColumn("Mandatory", "mandatory");
        addValidationColumn("Format", "format");
        addValidationColumn("Master Match", "master");
        addValidationColumn("Data / Duplicate", "data");
        addValidationColumn("Result / Error", "message");
        List<Map<String,String>> rows = new ArrayList<>();
        if (result != null) {
            for (ImportService.ImportRowResult row : result.details) {
                Map<String,String> values = new LinkedHashMap<>();
                values.put("rows", row.sourceRows);
                values.put("reference", row.reference);
                boolean failed = "FAILED".equalsIgnoreCase(row.status);
                values.put("mandatory", failed ? "Review" : "✓ Passed");
                values.put("format", failed ? "Review" : "✓ Passed");
                values.put("master", failed ? "Review" : "✓ Passed");
                values.put("data", failed ? "Review" : (row.action == null || row.action.isBlank() ? "✓ Passed" : row.action));
                boolean noteworthy = row.action != null && !row.action.isBlank() && !row.action.equalsIgnoreCase("VALIDATED") && !row.action.equalsIgnoreCase("CREATED");
                values.put("message", failed ? (row.message == null ? "Validation failed" : row.message)
                    : noteworthy && row.message != null && !row.message.isBlank() ? row.message : "✓ All validations passed");
                values.put("_status", row.status);
                rows.add(values);
            }
        }
        tblValidation.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Map<String,String> item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("import-validation-pass-row","import-validation-fail-row");
                if (!empty && item != null) getStyleClass().add("FAILED".equalsIgnoreCase(item.get("_status")) ? "import-validation-fail-row" : "import-validation-pass-row");
            }
        });
        tblValidation.getItems().setAll(rows);
        int failed = result == null ? 0 : result.failedCount();
        int passed = result == null ? 0 : Math.max(0, result.details.size() - failed);
        lblPreviewCount.setText((passed + failed) + " checked • " + passed + " passed • " + failed + " failed");
        lblPreviewStatus.getStyleClass().removeAll("import-success-text","import-warning-text");
        if (failed == 0) {
            lblPreviewStatus.setText("✓ All validations passed");
            lblPreviewStatus.getStyleClass().add("import-success-text");
        } else {
            lblPreviewStatus.setText("✕ Validation errors found");
            lblPreviewStatus.getStyleClass().add("import-warning-text");
        }
    }

    private void addValidationColumn(String title, String key) {
        TableColumn<Map<String,String>,String> c = new TableColumn<>(title);
        c.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getOrDefault(key, "")));
        if (Set.of("mandatory", "format", "master", "data").contains(key)) {
            c.setCellFactory(column -> SemanticTableCells.status("validation"));
        }
        tblValidation.getColumns().add(c);
    }

    /* =========================================================
       BACKGROUND IMPORT
       ========================================================= */

    @FXML
    private void onRunImport() {
        runImport();
    }

    private void runImport() {

        if (selectedFile == null) {

            showWarning(
                "No file selected",
                "Choose an Excel workbook first."
            );

            return;
        }

        if (!requiredMappingsComplete()) {

            showWarning(
                "Required mappings are missing",
                "Map all required fields before "
                    + "running the import."
            );

            return;
        }

        if (!preflightPassed) {
            showWarning("Validation required", "Run Review & Validate first. Import stays blocked until every row passes.");
            return;
        }

        Map<String, String> mapping =
            collectCurrentMapping();

        boolean dryRun =
            chkDryRun.isSelected();
        ImportService.ImportMode importMode = selectedImportMode();

        String module =
            cmbImportModule.getValue();

        setImportRunning(true);

        Task<ImportService.ImportResult> task =
            new Task<>() {

                @Override
                protected ImportService.ImportResult call()
                    throws Exception {

                    return executeImport(
                        module,
                        mapping,
                        dryRun,
                        importMode
                    );
                }
            };

        task.setOnSucceeded(event -> {

            setImportRunning(false);

            ImportService.ImportResult result =
                task.getValue();

            showResult(result);

            if (!dryRun && result.failedCount() == 0) {
                importCompleted = true;
                completedModule = module;
                showCompletedState(result);
            }

            if (
                !dryRun
                    && result.imported
                    + result.updated
                    > 0
            ) {

                org.example.service.NotificationService
                    .createNotification(
                        "Data import completed",
                        module
                            + ": "
                            + result.imported
                            + " created, "
                            + result.updated
                            + " updated.",
                        "INFO",
                        targetFor(module),
                        selectedFile.getName()
                    );
            }
        });

        task.setOnFailed(event -> {

            setImportRunning(false);

            Throwable exception =
                task.getException();

            ModernDialog.error(btnRunImport, "Import Error", "Import failed", safeMessage(exception));
        });

        Thread thread =
            new Thread(
                task,
                "dse-data-import"
            );

        thread.setDaemon(true);
        thread.start();
    }

    private ImportService.ImportResult executeImport(
        String module,
        Map<String, String> mapping,
        boolean dryRun,
        ImportService.ImportMode importMode
    ) throws Exception {

        if (!dryRun) {
            if (importMode == ImportService.ImportMode.UPDATE_NON_BLANK) {
                org.example.service.PermissionService.require("IMPORT.EDIT", "update existing records through Data Import");
            } else if (importMode == ImportService.ImportMode.UPSERT) {
                org.example.service.PermissionService.require("IMPORT.CREATE", "create records through Data Import");
                org.example.service.PermissionService.require("IMPORT.EDIT", "update existing records through Data Import");
            } else {
                org.example.service.PermissionService.require("IMPORT.CREATE", "create records through Data Import");
            }
        }

        return switch (module) {

            case "Customers/CRM" ->
                importService.importCustomers(
                    selectedFile.toPath(),
                    mapping,
                    dryRun,
                    importMode,
                    this::updateProgress
                );

            case "Suppliers/HRM" ->
                importService.importSuppliers(
                    selectedFile.toPath(),
                    mapping,
                    dryRun,
                    importMode,
                    this::updateProgress
                );

            case "Sales" ->
                importService.importSales(
                    selectedFile.toPath(),
                    mapping,
                    dryRun,
                    importMode,
                    this::updateProgress
                );

            case "Purchases" ->
                importService.importPurchases(
                    selectedFile.toPath(),
                    mapping,
                    dryRun,
                    importMode,
                    this::updateProgress
                );

            case "Master Categories and Values" ->
                importService.importMasterValues(
                    selectedFile.toPath(),
                    mapping,
                    dryRun,
                    importMode,
                    this::updateProgress
                );

            case "Purchase Recon" -> importPurchaseRecon(dryRun, mapping);

            case "Bank Statement" -> importBankStatement(dryRun);

            default ->
                importService.importItems(
                    selectedFile.toPath(),
                    mapping,
                    dryRun,
                    importMode,
                    this::updateProgress
                );
        };
    }

    private ImportService.ImportResult importPurchaseRecon(boolean dryRun, Map<String,String> mapping) throws Exception {
        return purchaseReconImportCoordinator.execute(
            selectedFile.toPath(), mapping, txtImportNote == null ? "" : txtImportNote.getText(), dryRun);
    }

    private ImportService.ImportResult importBankStatement(boolean dryRun) throws Exception {
        var parsed = new org.example.bank.KotakBankStatementCsvParser().parse(selectedFile.toPath());
        updateProgress(parsed.rows().size(), parsed.rows().size());
        return bankStatementImportCoordinator.execute(selectedFile.toPath(), dryRun);
    }

    private Map<String, String> collectCurrentMapping() {

        Map<String, String> mapping =
            new LinkedHashMap<>();

        for (String field : getDomainFieldsForModule()) {

            ComboBox<String> combo =
                mappingControls.get(field);

            if (
                combo != null
                    && combo.getValue() != null
                    && !combo
                    .getValue()
                    .isBlank()
            ) {

                mapping.put(
                    field,
                    combo.getValue()
                );
            }
        }

        return mapping;
    }

    private void setImportRunning(boolean running) {

        importRunning = running;
        progressContainer.setManaged(running);
        progressContainer.setVisible(running);

        btnRunImport.setDisable(!isImportReady());

        btnChooseFile.setDisable(running);
        cmbImportModule.setDisable(running);
        cmbImportMode.setDisable(running || isFixedImportPolicy(cmbImportModule.getValue()));
        btnAutoMap.setDisable(running);
        btnResetMapping.setDisable(running);
        chkDryRun.setDisable(running);

        if (running) {

            progressBar.setProgress(0);
            lblProgressPercent.setText("0%");

            lblProgressStatus.setText(
                chkDryRun.isSelected()
                    ? "Validating workbook..."
                    : "Importing data..."
            );
        }
    }

    private void updateProgress(
        int processed,
        int total
    ) {

        double progress =
            total <= 0
                ? 0
                : Math.min(
                1,
                (double) processed / total
            );

        int percentage =
            (int) Math.round(progress * 100);

        Platform.runLater(() -> {

            progressBar.setProgress(progress);

            lblProgressPercent.setText(
                percentage + "%"
            );

            lblProgressStatus.setText(
                "Processed "
                    + processed
                    + " of "
                    + total
                    + " rows"
            );
        });
    }

    /* =========================================================
       RESULT AND NAVIGATION
       ========================================================= */

    private void showResult(ImportService.ImportResult result) {
        Path report = null;
        try {
            report = writeImportResultReport(result);
        } catch (Exception exception) {
            System.err.println("Could not write import result report: " + safeMessage(exception));
        }

        boolean dryRun = chkDryRun.isSelected();
        ImportResultPolicy.Presentation presentation = ImportResultPolicy.presentation(result, dryRun);
        int succeeded = presentation.succeeded();
        boolean warning = presentation.warning();
        Alert.AlertType type = switch (presentation.semantic()) {
            case INFORMATION -> Alert.AlertType.INFORMATION;
            case WARNING -> Alert.AlertType.WARNING;
            case ERROR -> Alert.AlertType.ERROR;
        };
        Alert alert = new OwnedAlert(type);
        alert.setTitle("Import Result");
        alert.setHeaderText(presentation.header());

        StringBuilder message = new StringBuilder()
            .append("Processed: ").append(result.processed).append("\n")
            .append("Passed: ").append(result.passedCount()).append("\n")
            .append("Imported (new): ").append(result.imported).append("\n")
            .append("Updated: ").append(result.updated).append("\n")
            .append("Skipped: ").append(result.skipped).append("\n")
            .append("Failed: ").append(result.failedCount());
        if (!dryRun && succeeded > 0 && (warning || result.failedCount() > 0)) {
            message.append("\n\nSuccessfully imported records remain saved. Review the result report for warnings or failed rows.");
        }

        if (report != null) {
            message.append("\n\nDetailed Excel result report generated.");
        }
        alert.setContentText(message.toString());

        ButtonType openReport = new ButtonType("Open Result Report", ButtonBar.ButtonData.LEFT);
        if (report != null) {
            alert.getButtonTypes().setAll(openReport, ButtonType.OK);
        } else {
            alert.getButtonTypes().setAll(ButtonType.OK);
        }

        Optional<ButtonType> selected = alert.showAndWait();
        if (report != null && selected.isPresent() && selected.get() == openReport) {
            openReport(report);
        }
    }


    private Path writeImportResultReport(ImportService.ImportResult result) throws Exception {
        return ImportResultReportService.write(result, new ImportResultReportService.Context(
            cmbImportModule.getValue(), selectedFile == null ? "" : selectedFile.getName(), chkDryRun.isSelected()));
    }


    private void openReport(Path report) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(report.toFile());
            } else {
                new OwnedAlert(Alert.AlertType.INFORMATION,
                    "Result report saved to:\n" + report, ButtonType.OK).showAndWait();
            }
        } catch (Exception exception) {
            new OwnedAlert(Alert.AlertType.INFORMATION,
                "Result report saved to:\n" + report + "\n\nCould not open it automatically.",
                ButtonType.OK).showAndWait();
        }
    }

    private boolean allowNavigationAway(String destination) {
        if (destination != null && destination.endsWith("/Import.fxml")) return true;
        if (importRunning) {
            ModernDialog.warning(btnRunImport, "Import in progress", "Please wait for the import to finish",
                "Navigation is temporarily locked so the import cannot be left in an uncertain state.");
            return false;
        }
        boolean hasProgress = currentWizardStep > 1 && selectedFile != null && !importCompleted;
        if (!hasProgress) { NavigationGuardRegistry.clear(btnRunImport); return true; }
        boolean leave = ModernDialog.confirm(btnRunImport, "Leave Data Import?", "Discard the current import setup?",
            "You are in step " + currentWizardStep + " of 4. Leaving now will discard the selected file, mappings and validation progress.");
        if (leave) NavigationGuardRegistry.clear(btnRunImport);
        return leave;
    }

    private void showCompletedState(ImportService.ImportResult result) {
        if (importCompletedPanel == null) return;
        importCompletedPanel.setManaged(true);
        importCompletedPanel.setVisible(true);
        if (lblImportCompletedSummary != null) {
            lblImportCompletedSummary.setText("Processed " + result.processed + " • " + result.imported + " created • "
                + result.updated + " updated • " + result.skipped + " skipped");
        }
        btnRunImport.setDisable(true);
        boolean warning = ImportResultPolicy.hasWarnings(result);
        lblReadyStatus.setText(warning ? "Import completed with warnings" : "Import completed successfully");
        lblReadyStatus.getStyleClass().removeAll("import-warning-text", "import-success-text");
        String stateClass = warning ? "import-warning-text" : "import-success-text";
        if (!lblReadyStatus.getStyleClass().contains(stateClass)) lblReadyStatus.getStyleClass().add(stateClass);
    }

    @FXML private void viewImportedRecords() {
        String module = completedModule == null ? cmbImportModule.getValue() : completedModule;
        ImportViewContext.request(module);
        String target = targetFor(module);
        NavigationGuardRegistry.clear(btnRunImport);
        NavigationManager.navigateOrReport(target);
    }

    @FXML private void startAnotherImport() {
        importCompleted = false; completedModule = null; selectedFile = null; selectedLayout = null; currentHeaders = List.of();
        preflightPassed = false; lastPreflightResult = null; mappingControls.clear(); mappingStatusLabels.clear(); requiredStatusLabels.clear();
        lblChosenFile.setText("No file selected"); tblPreview.getItems().clear(); tblPreview.getColumns().clear();
        if (importCompletedPanel != null) { importCompletedPanel.setVisible(false); importCompletedPanel.setManaged(false); }
        NavigationGuardRegistry.install(btnRunImport, this::allowNavigationAway);
        showWizardStep(1); updateMappingSummary();
    }

    @FXML private void closeImport() {
        NavigationGuardRegistry.clear(btnRunImport);
        NavigationManager.navigateOrReport("/fxml/pages/DashboardHome.fxml");
    }

    private String targetFor(String module) {
        return ImportModuleRegistry.target(module);
    }


    /* =========================================================
       TEMPLATE DOWNLOADS
       ========================================================= */

    @FXML
    private void downloadTemplate() {
        org.example.service.PermissionService.require("IMPORT.EXPORT", "download an import template");

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Import Template");
        chooser.setInitialFileName(cmbImportModule.getValue().replaceAll("[^A-Za-z0-9]+", "_") + "_Template.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook", "*.xlsx"));
        File target = chooser.showSaveDialog(cmbImportModule.getScene().getWindow());
        if (target == null) return;

        try {
            importTemplateService.write(target.toPath(), cmbImportModule.getValue(), RuntimeContract.APP_VERSION);
            org.example.util.ToastManager.success(btnRunImport, "Template created",
                "Template saved to: " + target.getAbsolutePath());
        } catch (Exception exception) {
            Alert alert = new OwnedAlert(Alert.AlertType.ERROR,
                "Could not create template: " + safeMessage(exception), ButtonType.OK);
            alert.setHeaderText("Template creation failed");
            alert.showAndWait();
        }
    }



    private void downloadTemplateFor(
        String module
    ) {

        cmbImportModule.setValue(module);
        downloadTemplate();
    }

    @FXML
    private void downloadItemTemplate() {
        downloadTemplateFor("Item Master");
    }

    @FXML
    private void downloadCustomerTemplate() {
        downloadTemplateFor("Customers/CRM");
    }

    @FXML
    private void downloadSupplierTemplate() {
        downloadTemplateFor("Suppliers/HRM");
    }

    @FXML
    private void downloadSalesTemplate() {
        downloadTemplateFor("Sales");
    }

    @FXML
    private void downloadPurchaseTemplate() {
        downloadTemplateFor("Purchases");
    }

    @FXML
    private void downloadPurchaseReconTemplate() {
        downloadTemplateFor("Purchase Recon");
    }

    @FXML
    private void downloadMasterTemplate() {
        downloadTemplateFor(
            "Master Categories and Values"
        );
    }




    /* =========================================================
       IMPORT GUIDE
       ========================================================= */

    @FXML
    private void showImportGuide() {

        Alert alert =
            new OwnedAlert(
                Alert.AlertType.INFORMATION
            );

        alert.setTitle("Data Import Guide");
        alert.setHeaderText(
            "How to import ERP data"
        );

        alert.setContentText(
            """
            1. Select the destination module.
            2. Download the matching Excel template.
            3. Keep the template header row unchanged.
            4. Enter or paste your records into the workbook.
            5. Choose or drag the completed workbook here.
            6. Review the automatic column mappings.
            7. Check the first 50 rows in Data Preview.
            8. Select “Validate only” for a safe test.
            9. Click Import Data when everything is correct.
            """
        );

        alert.showAndWait();
    }

    /* =========================================================
       GENERAL HELPERS
       ========================================================= */

    private void showWarning(
        String title,
        String message
    ) {

        Alert alert =
            new OwnedAlert(
                Alert.AlertType.WARNING,
                message,
                ButtonType.OK
            );

        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) {
            return "An unknown error occurred.";
        }

        if (
            throwable.getMessage() == null
                || throwable
                .getMessage()
                .isBlank()
        ) {
            return throwable
                .getClass()
                .getSimpleName();
        }

        return throwable.getMessage();
    }
}
