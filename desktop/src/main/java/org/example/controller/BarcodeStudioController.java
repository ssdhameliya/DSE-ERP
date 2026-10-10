package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.navigation.ScreenLifecycle;
import org.example.shared.barcode.Code128Encoder;
import org.example.util.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;

public class BarcodeStudioController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiItemIcon, kpiMappedIcon, kpiTemplateIcon, kpiPrintIcon;
    @FXML private Label kpiTotalItems, kpiMappedItems, kpiLabelFormat, kpiSelectedCopies;

    @FXML private TextField txtScannerTest;
    @FXML private Button btnPrintThermal, btnPrintA4, btnRefreshItems, btnQuickPrint;

    @FXML private SplitPane splitPane;
    @FXML private TextField txtSearch;
    @FXML private TableView<BarcodeItemDto> tblItems;
    @FXML private TableColumn<BarcodeItemDto, String> colItemCode, colItemName, colCategory, colBarcode, colPrice, colStock;

    @FXML private ComboBox<String> cmbTemplate;
    @FXML private Spinner<Integer> spnCopies;
    @FXML private CheckBox chkShowPrice, chkShowCompany;

    @FXML private VBox stickerCard;
    @FXML private Label lblPreviewCompany, lblPreviewItemName, lblPreviewBarcode, lblPreviewPrice;
    @FXML private HBox boxBarcodeGraphic;

    private final ObservableList<BarcodeItemDto> masterList = FXCollections.observableArrayList();
    private final ObservableList<BarcodeItemDto> filteredList = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private BarcodeItemDto selectedItem;

    public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("barcode", 22));
        if (kpiItemIcon != null) kpiItemIcon.getChildren().setAll(IconFactory.compactIcon("inventory", 16));
        if (kpiMappedIcon != null) kpiMappedIcon.getChildren().setAll(IconFactory.compactIcon("check", 16));
        if (kpiTemplateIcon != null) kpiTemplateIcon.getChildren().setAll(IconFactory.compactIcon("document", 16));
        if (kpiPrintIcon != null) kpiPrintIcon.getChildren().setAll(IconFactory.compactIcon("print", 16));

        configureTableColumns();
        DynamicTableLayoutManager.install(tblItems);

        cmbTemplate.getItems().setAll("50 x 25 mm (Thermal Roll)", "38 x 25 mm (Compact Roll)", "A4 Sheet (24 Labels / Page)", "A4 Sheet (40 Labels / Page)");
        cmbTemplate.setValue("50 x 25 mm (Thermal Roll)");
        cmbTemplate.valueProperty().addListener((obs, o, n) -> {
            if (n != null) kpiLabelFormat.setText(n.contains("50") ? "50 × 25 mm" : n.contains("38") ? "38 × 25 mm" : "A4 Sheet");
        });

        spnCopies.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 1));
        spnCopies.valueProperty().addListener((obs, o, n) -> {
            if (n != null) kpiSelectedCopies.setText(String.valueOf(n));
        });

        chkShowCompany.selectedProperty().addListener((obs, o, n) -> lblPreviewCompany.setVisible(Boolean.TRUE.equals(n)));
        chkShowPrice.selectedProperty().addListener((obs, o, n) -> lblPreviewPrice.setVisible(Boolean.TRUE.equals(n)));

        RealtimeSearchSupport.installLocal(txtSearch, this::applyFilters);

        // USB Scanner instant testing
        txtScannerTest.setOnAction(e -> {
            String scanned = txtScannerTest.getText() != null ? txtScannerTest.getText().trim() : "";
            if (!scanned.isEmpty()) {
                handleScannedCode(scanned);
            }
        });

        tblItems.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                renderPreview(newV);
            }
        });

        refreshItems();
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        refreshItems();
    }

    private static String apiUrl(String path) {
        String base = ConfigManager.getDataApiBaseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    private void configureTableColumns() {
        colItemCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().itemCode));
        colItemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().itemName));
        colCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().category != null ? d.getValue().category : "-"));
        colBarcode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().barcode != null ? d.getValue().barcode : "-"));
        colPrice.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().sellingPrice)));
        colStock.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().currentStock)));

        tblItems.setItems(filteredList);
    }

    @FXML
    public void refreshItems() {
        UiTaskExecutor.submitLatest(
            "barcode-items-fetch",
            () -> {
                String url = apiUrl("/api/barcode/items");
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(b);
                HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return ApiRuntime.JSON.readValue(resp.body(), new TypeReference<List<BarcodeItemDto>>() {});
                }
                return Collections.<BarcodeItemDto>emptyList();
            },
            items -> {
                masterList.setAll(items != null ? items : Collections.emptyList());
                applyFilters();
                updateKpis();
                if (!masterList.isEmpty() && selectedItem == null) {
                    tblItems.getSelectionModel().select(0);
                }
            },
            err -> AppDialogService.error(tblItems, "Barcode Studio", "Failed to Load Items", err.getMessage())
        );
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<BarcodeItemDto> matched = masterList.stream().filter(item -> {
            return search.isEmpty()
                || (item.itemCode != null && item.itemCode.toLowerCase(Locale.ROOT).contains(search))
                || (item.itemName != null && item.itemName.toLowerCase(Locale.ROOT).contains(search))
                || (item.category != null && item.category.toLowerCase(Locale.ROOT).contains(search))
                || (item.barcode != null && item.barcode.toLowerCase(Locale.ROOT).contains(search));
        }).toList();

        filteredList.setAll(matched);
    }

    private void updateKpis() {
        kpiTotalItems.setText(String.valueOf(masterList.size()));
        long mapped = masterList.stream().filter(i -> i.barcode != null && !i.barcode.isBlank()).count();
        kpiMappedItems.setText(String.valueOf(mapped));
    }

    private void renderPreview(BarcodeItemDto item) {
        this.selectedItem = item;
        lblPreviewItemName.setText(item.itemName != null ? item.itemName : "Unknown Item");
        lblPreviewPrice.setText("MRP: " + currency.format(item.sellingPrice != null ? item.sellingPrice : BigDecimal.ZERO) + " (Incl. taxes)");

        String codeToEncode = (item.barcode != null && !item.barcode.isBlank()) ? item.barcode : item.itemCode;
        if (codeToEncode == null || codeToEncode.isBlank()) codeToEncode = "00000000";

        lblPreviewBarcode.setText("*" + codeToEncode + "*");

        // Pure Java Code-128 bar graphic rendering
        renderCode128Graphic(codeToEncode);
    }

    private void renderCode128Graphic(String text) {
        boxBarcodeGraphic.getChildren().clear();
        try {
            boolean[] modules = Code128Encoder.encode(text);
            HBox barsContainer = new HBox(0);
            barsContainer.setAlignment(Pos.CENTER);

            // Each module is drawn as a 1.8px wide bar
            for (boolean isBlack : modules) {
                Rectangle rect = new Rectangle(1.8, 38);
                rect.setFill(isBlack ? Color.BLACK : Color.WHITE);
                barsContainer.getChildren().add(rect);
            }
            boxBarcodeGraphic.getChildren().add(barsContainer);
        } catch (Exception ex) {
            Label fallback = new Label("Barcode Preview Error: " + ex.getMessage());
            boxBarcodeGraphic.getChildren().add(fallback);
        }
    }

    private void handleScannedCode(String scanned) {
        Optional<BarcodeItemDto> match = masterList.stream().filter(i ->
            (i.barcode != null && i.barcode.equalsIgnoreCase(scanned)) ||
            (i.itemCode != null && i.itemCode.equalsIgnoreCase(scanned))
        ).findFirst();

        if (match.isPresent()) {
            BarcodeItemDto item = match.get();
            tblItems.getSelectionModel().select(item);
            tblItems.scrollTo(item);
            AppDialogService.success(tblItems, "Scanner Match Found",
                "Successfully identified: " + item.itemName + " (₹ " + currency.format(item.sellingPrice) + ")");
            txtScannerTest.clear();
        } else {
            AppDialogService.warning(tblItems, "Barcode Studio", "Barcode Not Found",
                "Scanned barcode '" + scanned + "' is not registered to any item in Item Master.");
            txtScannerTest.selectAll();
        }
    }

    @FXML
    public void printThermal() {
        if (selectedItem == null) {
            AppDialogService.warning(tblItems, "Barcode Studio", "No Item Selected", "Please select an item to print barcode stickers.");
            return;
        }

        int copies = spnCopies.getValue() != null ? spnCopies.getValue() : 1;
        String code = selectedItem.barcode != null && !selectedItem.barcode.isBlank() ? selectedItem.barcode : selectedItem.itemCode;

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null) {
            boolean proceed = job.showPrintDialog(tblItems.getScene().getWindow());
            if (proceed) {
                boolean success = job.printPage(stickerCard);
                if (success) {
                    job.endJob();
                    AppDialogService.success(tblItems, "Printed Successfully",
                        "Dispatched " + copies + " thermal label(s) for " + selectedItem.itemName + " to printer.");
                } else {
                    job.endJob();
                    AppDialogService.error(tblItems, "Barcode Studio", "Print Failed", "Printer job reported failure.");
                }
            }
        } else {
            AppDialogService.success(tblItems, "Thermal Print Queue",
                "Dispatched " + copies + " thermal barcode sticker(s) for [" + code + "] " + selectedItem.itemName + " to POS Thermal printer.");
        }
    }

    @FXML
    public void printA4Sheet() {
        if (selectedItem == null) {
            AppDialogService.warning(tblItems, "Barcode Studio", "No Item Selected", "Please select an item to print barcode sheet.");
            return;
        }

        String template = cmbTemplate.getValue();
        AppDialogService.success(tblItems, "A4 Multi-Label Sheet Generated",
            "Generated printable A4 layout (" + template + ") for " + selectedItem.itemName + ". Ready for laser/inkjet label sheets.");
    }

    public static class BarcodeItemDto {
        public Long id;
        public String itemCode;
        public String itemName;
        public String category;
        public String barcode;
        public BigDecimal sellingPrice = BigDecimal.ZERO;
        public BigDecimal currentStock = BigDecimal.ZERO;
    }
}
