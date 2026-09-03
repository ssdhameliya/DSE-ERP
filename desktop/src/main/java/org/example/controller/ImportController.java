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
    private final PurchaseReconApiClient purchaseReconApi = new PurchaseReconApiClient();

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
       MODULE FIELD DEFINITIONS
       ========================================================= */

    private static final List<String> ITEM_FIELDS = List.of(
        "item_code",
        "description",
        "category",
        "brand",
        "material",
        "size",
        "unit",
        "hsn",
        "gst",
        "discount_percent",
        "purchase_price",
        "selling_price",
        "remarks",
        "opening_stock",
        "minimum_stock",
        "location"
    );

    private static final List<String> CUSTOMER_FIELDS = List.of(
        "party_code",
        "name",
        "contact_person",
        "phone",
        "email",
        "gstin",
        "address",
        "opening_balance",
        "is_active"
    );

    private static final List<String> SUPPLIER_FIELDS = List.of(
        "party_code",
        "name",
        "contact_person",
        "phone",
        "email",
        "gstin",
        "address",
        "opening_balance",
        "is_active"
    );

    /** Purchase import intentionally mirrors the Sales one-sheet document contract. */
    private static final List<String> PURCHASE_DOCUMENT_FIELDS = List.of(
        "invoice_no", "invoice_date", "party_code", "item_code", "quantity", "rate", "gst_percent", "gst_type",
        "payment_terms", "paid_amount", "remarks",
        "charge_1_type", "charge_1_amount", "charge_1_taxable", "charge_1_gst_percent",
        "charge_2_type", "charge_2_amount", "charge_2_taxable", "charge_2_gst_percent",
        "additional_charges", "attachment_file", "attachment_files"
    );

    /** Sales optional invoice-level fields. Purchase deliberately uses the same shape for parity. */
    private static final List<String> SALES_DOCUMENT_FIELDS = List.of(
        "invoice_no",
        "invoice_date",
        "party_code",
        "item_code",
        "quantity",
        "rate",
        "gst_percent",
        "gst_type",
        "payment_terms",
        "paid_amount",
        "remarks",
        "charge_1_type",
        "charge_1_amount",
        "charge_1_taxable",
        "charge_1_gst_percent",
        "charge_2_type",
        "charge_2_amount",
        "charge_2_taxable",
        "charge_2_gst_percent",
        "attachment_file"
    );

    private static final List<String> MASTER_FIELDS = List.of(
        "category_code",
        "category_name",
        "category_description",
        "value_code",
        "value",
        "value_description",
        "display_order",
        "is_active"
    );


    private static final List<String> PURCHASE_RECON_FIELDS = List.of(
        "supplier_name", "supplier_gstin", "supplier_invoice_no", "invoice_date",
        "taxable_value", "cgst", "sgst", "igst", "invoice_value"
    );

    private static final List<String> BANK_STATEMENT_FIELDS = List.of(
        "transaction_date", "value_date", "description", "reference", "amount", "direction", "balance"
    );
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

        String module = cmbImportModule.getValue();

        return switch (module) {
            case "Customers/CRM" -> CUSTOMER_FIELDS;
            case "Suppliers/HRM" -> SUPPLIER_FIELDS;
            case "Sales" -> SALES_DOCUMENT_FIELDS;
            case "Purchases" -> PURCHASE_DOCUMENT_FIELDS;
            case "Master Categories and Values" -> MASTER_FIELDS;
            case "Purchase Recon" -> PURCHASE_RECON_FIELDS;
            case "Bank Statement" -> BANK_STATEMENT_FIELDS;
            default -> ITEM_FIELDS;
        };
    }

    private Set<String> getRequiredFieldsForModule() {

        String module = cmbImportModule.getValue();

        return switch (module) {

            case "Customers/CRM" -> Set.of("party_code", "name");
            case "Suppliers/HRM" -> Set.of("party_code", "name", "email");

            case "Sales", "Purchases" ->
                Set.of(
                    "invoice_no",
                    "invoice_date",
                    "party_code",
                    "item_code",
                    "quantity",
                    "rate"
                );

            case "Master Categories and Values" ->
                Set.of(
                    "category_code",
                    "category_name",
                    "value_code",
                    "value"
                );

            case "Purchase Recon" -> Set.of("supplier_name", "supplier_invoice_no", "invoice_date", "invoice_value");

            case "Bank Statement" -> Set.of("transaction_date","value_date","description","reference","amount","direction","balance");

            default ->
                Set.of(
                    "item_code",
                    "description",
                    "unit",
                    "hsn",
                    "remarks"
                );
        };
    }

    private String getDataTypeForField(String field) {

        return switch (field) {

            case "invoice_date" -> "Date";

            case "quantity",
                 "rate",
                 "gst",
                 "gst_percent",
                 "purchase_price",
                 "selling_price",
                 "opening_stock",
                 "minimum_stock",
                 "opening_balance",
                 "paid_amount",
                 "charge_1_amount",
                 "charge_1_gst_percent",
                 "charge_2_amount",
                 "charge_2_gst_percent",
                 "display_order",
                 "taxable_value",
                 "cgst",
                 "sgst",
                 "igst",
                 "invoice_value",
                 "amount",
                 "balance" -> "Number";

            case "is_active", "charge_1_taxable", "charge_2_taxable" -> "Boolean";

            case "email" -> "Email";

            case "phone" -> "Phone";

            default -> "Text";
        };
    }

    private String humanize(String field) {

        if (field == null || field.isBlank()) {
            return "";
        }

        String[] words = field.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {

            if (word.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            if (
                word.equalsIgnoreCase("gst")
                    || word.equalsIgnoreCase("gstin")
                    || word.equalsIgnoreCase("hsn")
            ) {
                result.append(word.toUpperCase(Locale.ROOT));
            } else {
                result.append(
                    Character.toUpperCase(word.charAt(0))
                );

                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        return result.toString();
    }

    /* =========================================================
       FILE INSPECTION AND MAPPING
       ========================================================= */

    private List<String> readHeaders(File file) {

        if ("Bank Statement".equals(cmbImportModule.getValue())) {
            return List.of("Transaction Date","Value Date","Description","Chq / Ref No.","Amount","Dr / Cr","Balance");
        }

        try (Workbook workbook = WorkbookFactory.create(file)) {

            selectedLayout =
                SpreadsheetLayoutDetector.detect(
                    workbook,
                    getDomainFieldsForModule()
                );

            return selectedLayout
                .headers()
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(header -> !header.isBlank())
                .distinct()
                .toList();

        } catch (Exception exception) {

            throw new IllegalArgumentException(
                "The workbook could not be inspected: "
                    + exception.getMessage(),
                exception
            );
        }
    }

    private Map<String, String> generateAutoMapping(
        List<String> headers
    ) {

        Map<String, String> autoMapping =
            new LinkedHashMap<>();

        for (String field : getDomainFieldsForModule()) {

            String normalizedField = normalize(field);

            for (String header : headers) {

                String normalizedHeader = normalize(header);

                if (
                    normalizedHeader.equals(normalizedField)
                        || areKnownAliases(
                        normalizedField,
                        normalizedHeader
                    )
                ) {

                    autoMapping.put(field, header);
                    break;
                }
            }
        }

        return autoMapping;
    }

    private boolean areKnownAliases(
        String normalizedField,
        String normalizedHeader
    ) {

        return switch (normalizedField) {

            case "gst" ->
                normalizedHeader.equals("gstpercent")
                    || normalizedHeader.equals("gstrate");

            case "gstpercent" ->
                normalizedHeader.equals("gst")
                    || normalizedHeader.equals("gstrate");

            case "partycode" ->
                normalizedHeader.equals("customercode")
                    || normalizedHeader.equals("suppliercode")
                    || normalizedHeader.equals("partyid");

            case "description" ->
                normalizedHeader.equals("itemname")
                    || normalizedHeader.equals("name");

            case "invoiceNo", "invoiceno" ->
                normalizedHeader.equals("billno")
                    || normalizedHeader.equals("documentno");

            case "invoiceDate", "invoicedate" ->
                normalizedHeader.equals("billdate")
                    || normalizedHeader.equals("documentdate");

            case "suppliername" -> normalizedHeader.equals("tradelegalname") || normalizedHeader.equals("tradename") || normalizedHeader.equals("legalname") || normalizedHeader.equals("supplier") || normalizedHeader.equals("suppliername");
            case "suppliergstin" -> normalizedHeader.equals("gstinofsupplier") || normalizedHeader.equals("suppliergstin") || normalizedHeader.equals("gstin");
            case "supplierinvoiceno" -> normalizedHeader.equals("invoicenumber") || normalizedHeader.equals("invoiceno") || normalizedHeader.equals("billno") || normalizedHeader.equals("supplierinvoiceno");
            case "taxablevalue" -> normalizedHeader.equals("taxablevalue") || normalizedHeader.equals("taxableamount");
            case "cgst" -> normalizedHeader.equals("centraltax") || normalizedHeader.equals("cgst") || normalizedHeader.equals("cgstamount");
            case "sgst" -> normalizedHeader.equals("stateuttax") || normalizedHeader.equals("statetax") || normalizedHeader.equals("sgst") || normalizedHeader.equals("sgstamount");
            case "igst" -> normalizedHeader.equals("integratedtax") || normalizedHeader.equals("igst") || normalizedHeader.equals("igstamount");
            case "invoicevalue" -> normalizedHeader.equals("invoicevalue") || normalizedHeader.equals("invoicetotal") || normalizedHeader.equals("totalinvoicevalue");

            case "isactive" ->
                normalizedHeader.equals("active")
                    || normalizedHeader.equals("status");

            case "transactiondate" -> normalizedHeader.equals("transactiondate");
            case "valuedate" -> normalizedHeader.equals("valuedate");
            case "reference" -> normalizedHeader.equals("chqrefno") || normalizedHeader.equals("referenceno");
            case "amount" -> normalizedHeader.equals("amount");
            case "direction" -> normalizedHeader.equals("drcr") || normalizedHeader.equals("debitcredit");
            case "balance" -> normalizedHeader.equals("balance");
            case "charge1type" -> normalizedHeader.equals("charge1") || normalizedHeader.equals("charge1name") || normalizedHeader.equals("additionalcharge1");
            case "charge1amount" -> normalizedHeader.equals("charge1value") || normalizedHeader.equals("additionalcharge1amount");
            case "charge1taxable" -> normalizedHeader.equals("charge1istaxable") || normalizedHeader.equals("charge1tax");
            case "charge1gstpercent" -> normalizedHeader.equals("charge1gst") || normalizedHeader.equals("charge1gstrate");
            case "charge2type" -> normalizedHeader.equals("charge2") || normalizedHeader.equals("charge2name") || normalizedHeader.equals("additionalcharge2");
            case "charge2amount" -> normalizedHeader.equals("charge2value") || normalizedHeader.equals("additionalcharge2amount");
            case "charge2taxable" -> normalizedHeader.equals("charge2istaxable") || normalizedHeader.equals("charge2tax");
            case "charge2gstpercent" -> normalizedHeader.equals("charge2gst") || normalizedHeader.equals("charge2gstrate");
            case "attachmentfile" -> normalizedHeader.equals("attachment") || normalizedHeader.equals("documentfile") || normalizedHeader.equals("attachmentpath");

            default -> false;
        };
    }

    private String normalize(String value) {

        return value == null
            ? ""
            : value
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
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

        List<Map<String, String>> previewData = new ArrayList<>();

        if ("Bank Statement".equals(cmbImportModule.getValue())) {
            try {
                var parsed = new org.example.bank.KotakBankStatementCsvParser().parse(selectedFile.toPath());
                for (var row : parsed.rows().stream().limit(50).toList()) {
                    Map<String,String> m = new LinkedHashMap<>();
                    m.put("transaction_date", row.transactionTimestamp()); m.put("value_date", row.valueDate());
                    m.put("description", row.description()); m.put("reference", row.reference());
                    m.put("amount", String.format(Locale.ROOT,"%.2f", row.debit()>0?row.debit():row.credit()));
                    m.put("direction", row.debit()>0?"DR":"CR"); m.put("balance", String.format(Locale.ROOT,"%.2f",row.balance()));
                    previewData.add(m);
                }
                tblPreview.getItems().setAll(previewData); lblPreviewCount.setText(previewData.size()+" rows shown"); lblPreviewStatus.setText("Kotak bank statement preview loaded successfully");
                return;
            } catch(Exception e) { lblPreviewStatus.setText("Bank statement preview failed: "+safeMessage(e)); return; }
        }

        if ("Purchase Recon".equals(cmbImportModule.getValue())) {
            loadPurchaseReconPreviewRows(previewData);
            return;
        }

        try (
            Workbook workbook =
                WorkbookFactory.create(selectedFile)
        ) {

            SpreadsheetLayoutDetector.Layout layout =
                SpreadsheetLayoutDetector.detect(
                    workbook,
                    mappingControls
                        .values()
                        .stream()
                        .map(ComboBox::getValue)
                        .filter(Objects::nonNull)
                        .toList()
                );

            Sheet sheet =
                workbook.getSheetAt(
                    layout.sheetIndex()
                );

            FormulaEvaluator evaluator =
                workbook
                    .getCreationHelper()
                    .createFormulaEvaluator();

            Row headerRow =
                sheet.getRow(
                    layout.headerRowIndex()
                );

            int lastRow =
                Math.min(
                    sheet.getLastRowNum(),
                    layout.headerRowIndex() + 50
                );

            List<String> mappedFields =
                getMappedFieldsInDomainOrder();

            for (
                int rowIndex =
                layout.headerRowIndex() + 1;
                rowIndex <= lastRow;
                rowIndex++
            ) {

                Row row = sheet.getRow(rowIndex);

                if (
                    SpreadsheetLayoutDetector
                        .isRowBlank(row, evaluator)
                ) {
                    continue;
                }

                Map<String, String> rowMap =
                    new LinkedHashMap<>();

                for (String domainField : mappedFields) {

                    ComboBox<String> mappingCombo =
                        mappingControls.get(domainField);

                    String excelHeader =
                        mappingCombo == null
                            ? null
                            : mappingCombo.getValue();

                    if (excelHeader == null) {
                        continue;
                    }

                    int columnIndex =
                        SpreadsheetLayoutDetector
                            .findHeaderIndex(
                                headerRow,
                                excelHeader,
                                evaluator
                            );

                    String value = "";

                    if (
                        columnIndex >= 0
                            && row.getCell(columnIndex)
                            != null
                    ) {

                        Cell previewCell = row.getCell(columnIndex);
                        boolean dateField = domainField != null && domainField.toLowerCase(Locale.ROOT).contains("date");
                        value = dateField
                            ? SpreadsheetLayoutDetector.formatForBusiness(previewCell, evaluator)
                            : SpreadsheetLayoutDetector.format(previewCell, evaluator);
                    }

                    rowMap.put(
                        domainField,
                        value
                    );
                }

                previewData.add(rowMap);
            }

            tblPreview
                .getItems()
                .setAll(previewData);

            lblPreviewCount.setText(
                previewData.size() + " rows shown"
            );

            if (previewData.isEmpty()) {

                lblPreviewStatus.setText(
                    "No usable data rows were found"
                );

                lblPreviewStatus
                    .getStyleClass()
                    .removeAll(
                        "import-success-text",
                        "import-warning-text"
                    );

                lblPreviewStatus
                    .getStyleClass()
                    .add("import-warning-text");

            } else {

                lblPreviewStatus.setText(
                    "Preview loaded successfully"
                );

                lblPreviewStatus
                    .getStyleClass()
                    .removeAll(
                        "import-success-text",
                        "import-warning-text"
                    );

                lblPreviewStatus
                    .getStyleClass()
                    .add("import-success-text");
            }

        } catch (Exception exception) {

            tblPreview
                .getItems()
                .clear();

            lblPreviewCount.setText("0 rows");

            lblPreviewStatus.setText(
                "Preview could not be loaded: "
                    + safeMessage(exception)
            );

            lblPreviewStatus
                .getStyleClass()
                .removeAll(
                    "import-success-text",
                    "import-warning-text"
                );

            lblPreviewStatus
                .getStyleClass()
                .add("import-warning-text");
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

    private void loadPurchaseReconPreviewRows(List<Map<String, String>> previewData) {
        try (Workbook workbook = WorkbookFactory.create(selectedFile)) {
            List<String> expected = mappingControls.values().stream().map(ComboBox::getValue).filter(Objects::nonNull).toList();
            List<SpreadsheetLayoutDetector.Layout> layouts = SpreadsheetLayoutDetector.detectAll(workbook, expected);
            if (layouts.isEmpty()) throw new IllegalArgumentException("No mapped Purchase Recon worksheet was found.");
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<String> mappedFields = getMappedFieldsInDomainOrder();
            int previewLimit = 100;
            for (SpreadsheetLayoutDetector.Layout layout : layouts) {
                Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
                Row headerRow = sheet.getRow(layout.headerRowIndex());
                for (int rowIndex = layout.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum() && previewData.size() < previewLimit; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (SpreadsheetLayoutDetector.isRowBlank(row, evaluator)) continue;
                    Map<String,String> rowMap = new LinkedHashMap<>();
                    rowMap.put("_source_sheet", sheet.getSheetName());
                    rowMap.put("_source_row", String.valueOf(rowIndex + 1));
                    for (String domainField : mappedFields) {
                        ComboBox<String> mappingCombo = mappingControls.get(domainField);
                        String excelHeader = mappingCombo == null ? null : mappingCombo.getValue();
                        if (excelHeader == null) continue;
                        int columnIndex = SpreadsheetLayoutDetector.findHeaderIndex(headerRow, excelHeader, evaluator);
                        String value = "";
                        if (columnIndex >= 0 && row != null && row.getCell(columnIndex) != null) {
                            Cell previewCell = row.getCell(columnIndex);
                            boolean dateField = domainField.toLowerCase(Locale.ROOT).contains("date");
                            value = dateField ? SpreadsheetLayoutDetector.formatForBusiness(previewCell, evaluator) : SpreadsheetLayoutDetector.format(previewCell, evaluator);
                        }
                        rowMap.put(domainField, value);
                    }
                    previewData.add(rowMap);
                }
                if (previewData.size() >= previewLimit) break;
            }
            tblPreview.getItems().setAll(previewData);
            lblPreviewCount.setText(previewData.size()+" rows shown • "+layouts.size()+" sheet"+(layouts.size()==1?"":"s"));
            lblPreviewStatus.getStyleClass().removeAll("import-warning-text");
            lblPreviewStatus.getStyleClass().add("import-success-text");
            lblPreviewStatus.setText(previewData.isEmpty()?"No usable Purchase Recon rows were found":"Mapped Purchase Recon data preview loaded across all matching sheets");
        } catch (Exception e) {
            tblPreview.getItems().clear();
            lblPreviewCount.setText("0 rows");
            lblPreviewStatus.setText("Purchase Recon preview failed: "+safeMessage(e));
            lblPreviewStatus.getStyleClass().removeAll("import-success-text");
            lblPreviewStatus.getStyleClass().add("import-warning-text");
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
        List<PurchaseReconApiClient.ImportRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(selectedFile)) {
            List<SpreadsheetLayoutDetector.Layout> layouts = SpreadsheetLayoutDetector.detectAll(workbook, mapping.values());
            if (layouts.isEmpty()) throw new IllegalArgumentException("No Purchase Recon worksheet matches the mapped columns.");
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (SpreadsheetLayoutDetector.Layout layout : layouts) {
                Sheet sheet = workbook.getSheetAt(layout.sheetIndex());
                Row header = sheet.getRow(layout.headerRowIndex());
                Map<String,Integer> indexes = new HashMap<>();
                for (Map.Entry<String,String> entry : mapping.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                    indexes.put(entry.getKey(), SpreadsheetLayoutDetector.findHeaderIndex(header, entry.getValue(), evaluator));
                }
                for (int rowIndex = layout.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (SpreadsheetLayoutDetector.isRowBlank(row, evaluator)) continue;
                    String supplierName = mappedText(row, indexes.get("supplier_name"), evaluator);
                    String gstin = mappedText(row, indexes.get("supplier_gstin"), evaluator);
                    String invoice = mappedText(row, indexes.get("supplier_invoice_no"), evaluator);
                    String invoiceDate = mappedDateIso(row, indexes.get("invoice_date"), evaluator);
                    rows.add(new PurchaseReconApiClient.ImportRow(
                        sheet.getSheetName(), rowIndex + 1, supplierName, gstin, invoice, invoiceDate,
                        mappedAmount(row, indexes.get("taxable_value"), evaluator),
                        mappedAmount(row, indexes.get("cgst"), evaluator),
                        mappedAmount(row, indexes.get("sgst"), evaluator),
                        mappedAmount(row, indexes.get("igst"), evaluator),
                        mappedAmount(row, indexes.get("invoice_value"), evaluator)
                    ));
                }
            }
        }

        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(selectedFile.toPath())));
        PurchaseReconApiClient.ImportResult result = purchaseReconApi.importRows(new PurchaseReconApiClient.ImportRequest(
            selectedFile.getName(), fingerprint, txtImportNote == null ? "" : txtImportNote.getText(), dryRun, rows
        ));

        List<ImportService.ImportRowResult> details = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (result.details() != null) {
            for (PurchaseReconApiClient.ImportRowResult row : result.details()) {
                boolean failed = "FAILED".equalsIgnoreCase(row.status());
                String reference = (row.supplierReference() == null || row.supplierReference().isBlank() ? "" : row.supplierReference() + " • ")
                    + (row.invoiceNo() == null ? "" : row.invoiceNo());
                String message = row.message() == null ? "" : row.message();
                String source = (row.sourceSheet() == null || row.sourceSheet().isBlank() ? "Sheet" : row.sourceSheet())
                    + " • Row " + (row.sourceRow() == null ? "?" : row.sourceRow());
                details.add(new ImportService.ImportRowResult(
                    source, reference, failed ? "FAILED" : "PASSED", row.action(), message, "", 0d
                ));
                if (failed) errors.add(source + ": " + message);
            }
        }
        int skipped = result.alreadyCurrentRows() + result.duplicateRows() + result.conflictRows() + result.ignoredRows();
        return new ImportService.ImportResult(
            result.totalRows(), dryRun ? 0 : result.importedRows(), 0, skipped, errors, details
        );
    }

    private String mappedText(Row row, Integer index, FormulaEvaluator evaluator) {
        if (row == null || index == null || index < 0) return "";
        Cell cell = row.getCell(index);
        return SpreadsheetLayoutDetector.format(cell, evaluator);
    }

    private String mappedDateIso(Row row, Integer index, FormulaEvaluator evaluator) {
        if (row == null || index == null || index < 0) return "";
        Cell cell = row.getCell(index);
        LocalDate excelDate = SpreadsheetLayoutDetector.dateValue(cell, evaluator);
        if (excelDate != null) return excelDate.toString();
        String value = SpreadsheetLayoutDetector.format(cell, evaluator);
        if (value.isBlank()) return "";
        try { return BusinessClock.parseDate(value).toString(); }
        catch (Exception ignored) { return value; }
    }

    private double mappedAmount(Row row, Integer index, FormulaEvaluator evaluator) {
        String value = mappedText(row, index, evaluator);
        if (value.isBlank()) return 0d;
        String normalized = value.replace(",", "").replace("₹", "").replace("INR", "").trim();
        boolean negative = normalized.startsWith("(") && normalized.endsWith(")");
        if (negative) normalized = normalized.substring(1, normalized.length() - 1);
        try { return (negative ? -1d : 1d) * Double.parseDouble(normalized); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    private ImportService.ImportResult importBankStatement(boolean dryRun) throws Exception {
        var parsed = new org.example.bank.KotakBankStatementCsvParser().parse(selectedFile.toPath());
        updateProgress(parsed.rows().size(), parsed.rows().size());
        var u=org.example.service.SessionService.current(); String user=u==null?"User":u.getFullName();
        var request = new org.example.api.bank.BankStatementApiClient.ImportRequest(parsed.bankName(),parsed.accountNumber(),parsed.accountHolder(),parsed.statementFrom(),parsed.statementTo(),parsed.currency(),parsed.openingBalance(),parsed.closingBalance(),parsed.sourceFingerprint(),parsed.sourceFileName(),parsed.sourceCsv(),user,dryRun,parsed.rows());
        var result = new org.example.api.bank.BankStatementApiClient().importStatement(request);
        boolean allExisting = !result.alreadyImported() && result.importedRows() == 0 && result.duplicateRows() > 0;
        String action=result.alreadyImported()||allExisting?"ALREADY CURRENT":(dryRun?"VALIDATED":"IMPORTED");
        String message=result.alreadyImported()?"This exact bank statement was imported previously. Open the existing statement from Bank Statement history.":
            (allExisting?"All transactions in this statement were already imported. No bank transactions will be overwritten.":(dryRun?"Server validation passed":"Bank statement imported"));
        var details = List.of(new ImportService.ImportRowResult("1-"+parsed.rows().size(), parsed.sourceFileName(), "PASSED", action, message, "", 0));
        return new ImportService.ImportResult(parsed.rows().size(),dryRun?0:result.importedRows(),0,result.duplicateRows(),List.of(),details);
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
        int failed = result.failedCount();
        boolean warning = !dryRun && hasImportWarnings(result);
        int succeeded = result.imported + result.updated;
        Alert.AlertType type = dryRun || (failed == 0 && !warning)
            ? Alert.AlertType.INFORMATION
            : (succeeded > 0 || failed == 0 ? Alert.AlertType.WARNING : Alert.AlertType.ERROR);
        Alert alert = new OwnedAlert(type);
        alert.setTitle("Import Result");
        if (dryRun) alert.setHeaderText("Validation completed");
        else if (failed == 0 && succeeded == 0 && result.skipped > 0 && !warning) alert.setHeaderText("Import completed — no changes required");
        else if (failed == 0 && !warning) alert.setHeaderText("Import completed successfully");
        else if (failed == 0 || succeeded > 0) alert.setHeaderText("Import completed with warnings");
        else alert.setHeaderText("Import could not be completed");

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

    private boolean hasImportWarnings(ImportService.ImportResult result) {
        if (result == null) return false;
        if (result.failedCount() > 0) return true;
        // A skipped existing/duplicate row is an informational no-op, not an import failure.
        // Only explicit row warnings should produce the warning state.
        return result.details.stream().anyMatch(row ->
            (row.action != null && row.action.toUpperCase(Locale.ROOT).contains("WARNING"))
                || (row.message != null && row.message.toLowerCase(Locale.ROOT).contains("warning")));
    }

    private Path writeImportResultReport(ImportService.ImportResult result) throws Exception {
        Path folder = WorkspaceManager.getImportsFolder().resolve("Results");
        Files.createDirectories(folder);

        String module = cmbImportModule.getValue() == null ? "Import"
            : cmbImportModule.getValue().replaceAll("[^A-Za-z0-9]+", "_");
        String stamp = BusinessClock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path target = folder.resolve("Import_Result_" + module + "_" + stamp + ".xlsx");

        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream output = new FileOutputStream(target.toFile())) {

            CellStyle headerStyle = createTemplateHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Summary");
            String[][] summaryRows = {
                {"Module", cmbImportModule.getValue() == null ? "" : cmbImportModule.getValue()},
                {"Source File", selectedFile == null ? "" : selectedFile.getName()},
                {"Mode", chkDryRun.isSelected() ? "Validate only" : "Import"},
                {"Processed", String.valueOf(result.processed)},
                {"Passed", String.valueOf(result.passedCount())},
                {"Imported", String.valueOf(result.imported)},
                {"Updated", String.valueOf(result.updated)},
                {"Skipped", String.valueOf(result.skipped)},
                {"Failed", String.valueOf(result.failedCount())},
                {"Business Date", BusinessClock.formatDate(BusinessClock.today())},
                {"Business Time", BusinessClock.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
                    + " " + BusinessClock.zoneAbbreviation()}
            };
            for (int i = 0; i < summaryRows.length; i++) {
                Row row = summary.createRow(i);
                row.createCell(0).setCellValue(summaryRows[i][0]);
                row.createCell(1).setCellValue(summaryRows[i][1]);
            }
            summary.setColumnWidth(0, 24 * 256);
            summary.setColumnWidth(1, 70 * 256);

            Sheet details = workbook.createSheet("Import Results");
            String[] headers = {"Source Row(s)", "Reference", "Status", "Action", "Tax Type", "GST %", "Message"};
            Row header = details.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            if (!result.details.isEmpty()) {
                for (ImportService.ImportRowResult detail : result.details) {
                    Row row = details.createRow(rowIndex++);
                    row.createCell(0).setCellValue(detail.sourceRows);
                    row.createCell(1).setCellValue(detail.reference);
                    row.createCell(2).setCellValue(detail.status);
                    row.createCell(3).setCellValue(detail.action);
                    row.createCell(4).setCellValue(detail.taxType);
                    row.createCell(5).setCellValue(detail.gstPercent);
                    row.createCell(6).setCellValue(detail.message);
                }
            } else {
                for (String error : result.errors) {
                    Row row = details.createRow(rowIndex++);
                    row.createCell(2).setCellValue("FAILED");
                    row.createCell(3).setCellValue("NONE");
                    row.createCell(6).setCellValue(error);
                }
                if (rowIndex == 1) {
                    Row row = details.createRow(rowIndex);
                    row.createCell(2).setCellValue("PASSED");
                    row.createCell(3).setCellValue("SUMMARY");
                    row.createCell(6).setCellValue("Import completed without row-level errors.");
                }
            }

            details.createFreezePane(0, 1);
            details.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(1, rowIndex - 1), 0, headers.length - 1));
            int[] widths = {16, 24, 14, 16, 14, 12, 70};
            for (int i = 0; i < widths.length; i++) details.setColumnWidth(i, widths[i] * 256);

            workbook.write(output);
        }
        return target;
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
        boolean warning = hasImportWarnings(result);
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

        return switch (module) {

            case "Customers/CRM" ->
                "/fxml/pages/Customer.fxml";

            case "Suppliers/HRM" ->
                "/fxml/pages/Suppliers.fxml";

            case "Sales" ->
                "/fxml/pages/SalesList.fxml";

            case "Purchases" ->
                "/fxml/pages/PurchaseList.fxml";

            case "Master Categories and Values" ->
                "/fxml/pages/Masterdata.fxml";

            case "Purchase Recon" ->
                "/fxml/pages/PurchaseRecon.fxml";

            case "Bank Statement" ->
                "/fxml/pages/BankStatement.fxml";

            default ->
                "/fxml/pages/ItemMaster.fxml";
        };
    }

    /* =========================================================
       TEMPLATE DOWNLOADS
       ========================================================= */

    @FXML
    private void downloadTemplate() {
        org.example.service.PermissionService.require("IMPORT.EXPORT", "download an import template");

        FileChooser chooser =
            new FileChooser();

        chooser.setTitle(
            "Save Import Template"
        );

        chooser.setInitialFileName(
            cmbImportModule
                .getValue()
                .replaceAll(
                    "[^A-Za-z0-9]+",
                    "_"
                )
                + "_Template.xlsx"
        );

        chooser
            .getExtensionFilters()
            .add(
                new FileChooser.ExtensionFilter(
                    "Excel workbook",
                    "*.xlsx"
                )
            );

        File target =
            chooser.showSaveDialog(
                cmbImportModule
                    .getScene()
                    .getWindow()
            );

        if (target == null) {
            return;
        }

        try (
            Workbook workbook =
                new XSSFWorkbook();

            FileOutputStream output =
                new FileOutputStream(target)
        ) {

            Sheet sheet =
                workbook.createSheet(
                    "Import Template"
                );

            Sheet instructions = workbook.createSheet("Instructions");
            String[][] guidance = {
                {"DSE ERP 9.0.62 Import Template", "Keep identifier and header names unchanged."},
                {"Recommended mode", "Update non-blank fields: blank spreadsheet cells preserve existing master data."},
                {"Create new only", "Existing identifiers are skipped; only new records are created."},
                {"Create or update", "Existing master records are replaced with supplied values."},
                {"Skip existing", "Existing identifiers are never changed."},
                {"Financial documents", "Existing posted Sales and Purchase invoices are always protected and skipped."},
                {"GST / IGST", "For Sales/Purchases use gst_type = GST for intra-state or IGST for inter-state. Enter gst_percent only; DSE ERP calculates tax amounts from line values."},
                {"GST calculation", "GST is calculated as CGST + SGST (equal halves); IGST applies the full GST rate as IGST. Do not enter tax amounts manually."},
                {"Unlimited Purchase charges", "Purchases may use additional_charges with entries separated by semicolons. Each entry is Type|Amount|Taxable|GSTPercent, for example Freight|250|true|18;Packing|100|false|0."},
                {"Multiple Purchase attachments", "Use attachment_files for semicolon-separated file paths. Paths may be absolute or relative to the import workbook. The older attachment_file column remains supported."},
                {"Safe process", "Run Validate only first, review the preview and generated result report, then import."},
                {"Identifiers", identifierGuidance(cmbImportModule.getValue())}
            };
            for (int i = 0; i < guidance.length; i++) {
                Row row = instructions.createRow(i);
                row.createCell(0).setCellValue(guidance[i][0]);
                row.createCell(1).setCellValue(guidance[i][1]);
            }
            instructions.setColumnWidth(0, 28 * 256);
            instructions.setColumnWidth(1, 92 * 256);

            CellStyle headerStyle =
                createTemplateHeaderStyle(
                    workbook
                );

            Row header =
                sheet.createRow(0);

            List<String> fields =
                getDomainFieldsForModule();

            for (
                int columnIndex = 0;
                columnIndex < fields.size();
                columnIndex++
            ) {

                Cell cell =
                    header.createCell(
                        columnIndex
                    );

                cell.setCellValue(
                    fields.get(columnIndex)
                );

                cell.setCellStyle(
                    headerStyle
                );

                sheet.setColumnWidth(
                    columnIndex,
                    Math.max(
                        14,
                        fields
                            .get(columnIndex)
                            .length() + 3
                    ) * 256
                );
            }

            List<List<String>> exampleRows = exampleRowsFor(cmbImportModule.getValue());
            int lastSampleRow = 0;
            for (int sampleIndex = 0; sampleIndex < exampleRows.size(); sampleIndex++) {
                Row sample = sheet.createRow(sampleIndex + 1);
                List<String> examples = exampleRows.get(sampleIndex);
                for (int columnIndex = 0; columnIndex < Math.min(fields.size(), examples.size()); columnIndex++) {
                    sample.createCell(columnIndex).setCellValue(examples.get(columnIndex));
                }
                lastSampleRow = sampleIndex + 1;
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(1, lastSampleRow), 0, fields.size() - 1));

            workbook.write(output);

            org.example.util.ToastManager.success(btnRunImport,
                "Template created",
                "Template saved to: " + target.getAbsolutePath());

        } catch (Exception exception) {

            Alert alert =
                new OwnedAlert(
                    Alert.AlertType.ERROR,
                    "Could not create template: "
                        + safeMessage(exception),
                    ButtonType.OK
                );

            alert.setHeaderText(
                "Template creation failed"
            );

            alert.showAndWait();
        }
    }

    private CellStyle createTemplateHeaderStyle(
        Workbook workbook
    ) {

        CellStyle style =
            workbook.createCellStyle();

        Font font =
            workbook.createFont();

        font.setBold(true);
        font.setColor(
            IndexedColors.WHITE.getIndex()
        );

        style.setFont(font);

        style.setFillForegroundColor(
            IndexedColors.ROYAL_BLUE.getIndex()
        );

        style.setFillPattern(
            FillPatternType.SOLID_FOREGROUND
        );

        return style;
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

    private List<List<String>> exampleRowsFor(String module) {
        if ("Sales".equals(module)) {
            return List.of(
                List.of("SAL-GST-0001", BusinessClock.formatDate(BusinessClock.today()), "CUS-0001", "ITEM-0001",
                    "2", "1500", "18", "GST", "15 Days", "0", "Sample intra-state sale with two optional charges",
                    "Freight", "250", "true", "18", "Packing", "100", "false", "0", ""),
                List.of("SAL-IGST-0002", BusinessClock.formatDate(BusinessClock.today()), "CUS-0002", "ITEM-0001",
                    "1", "2000", "18", "IGST", "15 Days", "0", "Sample inter-state sale; attachment is optional",
                    "", "", "", "", "", "", "", "", "")
            );
        }
        if ("Purchases".equals(module)) {
            return List.of(
                List.of("PUR-GST-0001", BusinessClock.formatDate(BusinessClock.today()), "SUP-0001", "ITEM-0001",
                    "10", "1200", "18", "GST", "15 Days", "0", "Sample intra-state purchase with unlimited charge syntax",
                    "Freight", "250", "true", "18", "Packing", "100", "false", "0",
                    "Insurance|75|true|18;Handling|50|false|0", "", ""),
                List.of("PUR-IGST-0002", BusinessClock.formatDate(BusinessClock.today()), "SUP-0002", "ITEM-0001",
                    "5", "1200", "18", "IGST", "15 Days", "0", "Sample inter-state purchase; multiple attachments are optional",
                    "", "", "", "", "", "", "", "",
                    "Freight|250|true|18", "", "invoice.pdf;quality-certificate.pdf")
            );
        }
        return List.of(exampleRowFor(module));
    }

    private List<String> exampleRowFor(
        String module
    ) {

        return switch (module) {

            case "Customers/CRM" ->
                List.of(
                    "CUS-0001",
                    "ABC Enterprises",
                    "Ravi Patel",
                    "9876543210",
                    "accounts@example.com",
                    "24AAAAA1111A1Z5",
                    "Ahmedabad, Gujarat",
                    "0",
                    "true"
                );

            case "Suppliers/HRM" ->
                List.of(
                    "SUP-0001",
                    "Steel Supplier Ltd",
                    "Amit Shah",
                    "9876500000",
                    "sales@supplier.example",
                    "24BBBBB2222B1Z4",
                    "Rajkot, Gujarat",
                    "0",
                    "true"
                );

            case "Sales" ->
                List.of(
                    "SAL-0001",
                    "2026-07-28",
                    "CUS-0001",
                    "ITEM-0001",
                    "2",
                    "1500",
                    "18",
                    "GST",
                    "15 Days",
                    "0",
                    "Sample sales invoice",
                    "Freight",
                    "250",
                    "true",
                    "18",
                    "Packing",
                    "100",
                    "false",
                    "0",
                    ""
                );

            case "Purchases" ->
                List.of(
                    "PUR-0001", "2026-07-28", "SUP-0001", "ITEM-0001", "10", "1200", "18", "GST", "15 Days", "0",
                    "Sample purchase invoice", "Freight", "250", "true", "18", "Packing", "100", "false", "0", ""
                );

            case "Purchase Recon" ->
                List.of(
                    "Shree Ram Engineering Works",
                    "24APCPJ0791E1Z9",
                    "25/26/61",
                    BusinessClock.formatDate(BusinessClock.today()),
                    "10620.00",
                    "955.80",
                    "955.80",
                    "0.00",
                    "12532.00"
                );

            case "Master Categories and Values" ->
                List.of(
                    "UNIT",
                    "Unit",
                    "Units of measure",
                    "UNT001",
                    "Nos",
                    "Number of items",
                    "1",
                    "true"
                );

            default ->
                List.of(
                    "ITEM-0001",
                    "MS Round Pipe",
                    "Pipe",
                    "Jasvi",
                    "Mild Steel",
                    "25 mm",
                    "Nos",
                    "73063000",
                    "18",
                    "0",
                    "1200",
                    "1500",
                    "Sample item",
                    "0",
                    "10",
                    "Main Warehouse"
                );
        };
    }

    private String identifierGuidance(String module) {
        return switch (module) {
            case "Customers/CRM", "Suppliers/HRM" -> "party_code identifies the record to create, update or skip.";
            case "Sales", "Purchases" -> "invoice_no groups all item rows into one document; existing posted documents are protected.";
            case "Master Categories and Values" -> "category_code + value_code identify a reusable master value.";
            case "Purchase Recon" -> "Recon Supplier is matched by GSTIN first, then normalized name; Supplier Invoice No. + financial year protects against duplicate Purchase Recon records.";
            case "Bank Statement" -> "The source fingerprint and transaction row protect against duplicate imports.";
            default -> "item_code identifies the item to create, update or skip.";
        };
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
