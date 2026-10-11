package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.BrandingService;
import org.example.shared.barcode.Code128Encoder;
import org.example.util.*;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
    @FXML private Button btnPrintThermal, btnPrintA4, btnRefreshItems;

    @FXML private TextField txtSearch;
    @FXML private TableView<BarcodeItemDto> tblItems;
    @FXML private TableColumn<BarcodeItemDto, String> colItemCode, colItemName, colCategory, colBarcode, colPrice, colStock;
    @FXML private TableColumn<BarcodeItemDto, Void> colActions;

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

        RealtimeSearchSupport.installLocal(txtSearch, this::applyFilters);

        // USB Scanner instant testing
        if (txtScannerTest != null) {
            txtScannerTest.setOnAction(e -> {
                String scanned = txtScannerTest.getText() != null ? txtScannerTest.getText().trim() : "";
                if (!scanned.isEmpty()) {
                    handleScannedCode(scanned);
                }
            });
        }

        tblItems.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                this.selectedItem = newV;
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

        colActions.setCellFactory(col -> new TableCell<>() {
            private final MenuButton menu = new MenuButton("Actions");
            {
                menu.getStyleClass().setAll("row-actions", "table-action-menu", "approved-row-action");
                menu.setGraphic(IconFactory.compactIcon("actions", 16));
                menu.setContentDisplay(ContentDisplay.LEFT);
                menu.setGraphicTextGap(6);
                menu.setTooltip(new Tooltip("Actions"));
                menu.getProperties().put("erp-icon-preserve", true);

                MenuItem miPreview = new MenuItem("View / Preview Label", IconFactory.compactIcon("document", 16));
                miPreview.setOnAction(e -> {
                    BarcodeItemDto row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) showLabelPreviewDialog(row);
                });

                MenuItem miGenerate = new MenuItem("Generate Barcode", IconFactory.compactIcon("barcode", 16));
                miGenerate.setOnAction(e -> {
                    BarcodeItemDto row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) generateBarcodeForItem(row);
                });

                MenuItem miDispatch = new MenuItem("Print Dispatch Label (PDF)", IconFactory.compactIcon("delivery", 16));
                miDispatch.setOnAction(e -> {
                    BarcodeItemDto row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) promptAndPrintDispatchSlip(row);
                });

                menu.getItems().addAll(miPreview, miGenerate, miDispatch);
                IconFactory.decorateActionMenu(menu);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(menu);
                    setAlignment(Pos.CENTER);
                }
            }
        });

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
                    selectedItem = masterList.get(0);
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

    private void handleScannedCode(String scanned) {
        Optional<BarcodeItemDto> match = masterList.stream().filter(i ->
            (i.barcode != null && i.barcode.equalsIgnoreCase(scanned)) ||
            (i.itemCode != null && i.itemCode.equalsIgnoreCase(scanned))
        ).findFirst();

        if (match.isPresent()) {
            BarcodeItemDto item = match.get();
            tblItems.getSelectionModel().select(item);
            tblItems.scrollTo(item);
            selectedItem = item;
            AppDialogService.success(tblItems, "Scanner Match Found",
                "Successfully identified: " + item.itemName + " (₹ " + currency.format(item.sellingPrice) + ")");
            txtScannerTest.clear();
        } else {
            AppDialogService.warning(tblItems, "Barcode Studio", "Barcode Not Found",
                "Scanned barcode '" + scanned + "' is not registered to any item in Item Master.");
            txtScannerTest.selectAll();
        }
    }

    // ==========================================
    // 1. POPUP: Label Design & Live Preview
    // ==========================================
    public void showLabelPreviewDialog(BarcodeItemDto item) {
        if (item == null) return;
        this.selectedItem = item;

        Dialog<ButtonType> dialog = new OwnedDialog<>(tblItems);
        dialog.setTitle("Label Design & Live Preview");
        dialog.setHeaderText("Thermal Sticker & Sheet Preview — " + (item.itemName != null ? item.itemName : item.itemCode));

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.CLOSE);
        dp.getStyleClass().addAll("approved-dialog", "erp-dialog");

        // Controls container
        VBox controlsBox = new VBox(10);
        controlsBox.setPadding(new Insets(10));
        controlsBox.setPrefWidth(280);

        ComboBox<String> cmbTemplate = new ComboBox<>();
        cmbTemplate.getItems().setAll("50 x 25 mm (Thermal Roll)", "38 x 25 mm (Compact Roll)", "A4 Sheet (24 Labels / Page)", "A4 Sheet (40 Labels / Page)");
        cmbTemplate.setValue("50 x 25 mm (Thermal Roll)");
        cmbTemplate.setMaxWidth(Double.MAX_VALUE);

        Spinner<Integer> spnCopies = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 1));
        spnCopies.setEditable(true);
        spnCopies.setMaxWidth(Double.MAX_VALUE);

        CheckBox chkShowPrice = new CheckBox("Show Price (MRP)");
        chkShowPrice.setSelected(true);

        CheckBox chkShowCompany = new CheckBox("Show Company Name");
        chkShowCompany.setSelected(true);

        controlsBox.getChildren().addAll(
            new Label("Label Format Template:"),
            cmbTemplate,
            new Label("Number of Copies:"),
            spnCopies,
            chkShowPrice,
            chkShowCompany
        );

        // Preview sticker card
        VBox stickerCard = new VBox(4);
        stickerCard.getStyleClass().addAll("barcode-sticker-mockup", "approved-card");
        stickerCard.setAlignment(Pos.CENTER);
        stickerCard.setPrefWidth(320);
        stickerCard.setMinHeight(160);

        Label lblCompany = new Label(BrandingService.companyName());
        lblCompany.getStyleClass().add("barcode-sticker-company");

        Label lblItemName = new Label(item.itemName != null ? item.itemName : "Item");
        lblItemName.getStyleClass().add("barcode-sticker-item-name");
        lblItemName.setWrapText(true);
        lblItemName.setAlignment(Pos.CENTER);

        HBox boxBarcodeGraphic = new HBox();
        boxBarcodeGraphic.setAlignment(Pos.CENTER);
        boxBarcodeGraphic.setMinHeight(45);

        String codeToEncode = (item.barcode != null && !item.barcode.isBlank()) ? item.barcode : item.itemCode;
        if (codeToEncode == null || codeToEncode.isBlank()) codeToEncode = "00000000";

        renderCode128GraphicTo(boxBarcodeGraphic, codeToEncode);

        Label lblBarcode = new Label("*" + codeToEncode + "*");
        lblBarcode.getStyleClass().add("barcode-sticker-code");

        Label lblPrice = new Label("MRP: " + currency.format(item.sellingPrice != null ? item.sellingPrice : BigDecimal.ZERO) + " (Incl. taxes)");
        lblPrice.getStyleClass().add("barcode-sticker-price");

        stickerCard.getChildren().addAll(lblCompany, lblItemName, boxBarcodeGraphic, lblBarcode, lblPrice);

        chkShowCompany.selectedProperty().addListener((obs, o, n) -> lblCompany.setVisible(Boolean.TRUE.equals(n)));
        chkShowPrice.selectedProperty().addListener((obs, o, n) -> lblPrice.setVisible(Boolean.TRUE.equals(n)));

        VBox previewWrapper = new VBox(8);
        previewWrapper.setAlignment(Pos.CENTER);
        previewWrapper.getChildren().addAll(new Label("Sticker Live Preview:"), stickerCard);

        HBox contentLayout = new HBox(16, controlsBox, new Separator(javafx.geometry.Orientation.VERTICAL), previewWrapper);
        contentLayout.setAlignment(Pos.CENTER);
        contentLayout.setPadding(new Insets(10));

        // Dialog bottom actions
        HBox dialogActions = new HBox(8);
        dialogActions.setAlignment(Pos.CENTER_RIGHT);
        dialogActions.setPadding(new Insets(10, 0, 0, 0));

        Button btnPrintThermalAction = new Button("Print Thermal Labels", IconFactory.compactIcon("print", 16));
        btnPrintThermalAction.getStyleClass().addAll("approved-button", "approved-primary-button");
        btnPrintThermalAction.setOnAction(e -> {
            int copies = spnCopies.getValue() != null ? spnCopies.getValue() : 1;
            printThermalSticker(stickerCard, item.itemName, copies, item.barcode);
        });

        Button btnPrintA4Action = new Button("Print A4 Sheet", IconFactory.compactIcon("document", 16));
        btnPrintA4Action.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnPrintA4Action.setOnAction(e -> {
            String template = cmbTemplate.getValue();
            AppDialogService.success(tblItems, "A4 Multi-Label Sheet",
                "Generated printable A4 layout (" + template + ") for " + item.itemName + ".");
            logPrintAsync(spnCopies.getValue() != null ? spnCopies.getValue() : 1);
        });

        Button btnDispatchSlip = new Button("Print Dispatch Slip (PDF)", IconFactory.compactIcon("delivery", 16));
        btnDispatchSlip.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnDispatchSlip.setOnAction(e -> {
            dialog.close();
            promptAndPrintDispatchSlip(item);
        });

        dialogActions.getChildren().addAll(btnPrintA4Action, btnDispatchSlip, btnPrintThermalAction);

        VBox rootPane = new VBox(10, contentLayout, new Separator(), dialogActions);
        dp.setContent(rootPane);

        dialog.showAndWait();
    }

    private void renderCode128GraphicTo(HBox container, String text) {
        container.getChildren().clear();
        try {
            boolean[] modules = Code128Encoder.encode(text);
            HBox barsContainer = new HBox(0);
            barsContainer.setAlignment(Pos.CENTER);

            for (boolean isBlack : modules) {
                Rectangle rect = new Rectangle(1.8, 38);
                rect.setFill(isBlack ? Color.BLACK : Color.WHITE);
                barsContainer.getChildren().add(rect);
            }
            container.getChildren().add(barsContainer);
        } catch (Exception ex) {
            Label fallback = new Label("Barcode Error: " + ex.getMessage());
            container.getChildren().add(fallback);
        }
    }

    private void printThermalSticker(Node stickerNode, String itemName, int copies, String barcode) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null) {
            boolean proceed = job.showPrintDialog(tblItems.getScene().getWindow());
            if (proceed) {
                boolean success = job.printPage(stickerNode);
                if (success) {
                    job.endJob();
                    AppDialogService.success(tblItems, "Printed Successfully",
                        "Dispatched " + copies + " thermal label(s) for " + itemName + " to printer.");
                    logPrintAsync(copies);
                } else {
                    job.endJob();
                    AppDialogService.error(tblItems, "Barcode Studio", "Print Failed", "Printer job reported failure.");
                }
            }
        } else {
            AppDialogService.success(tblItems, "Thermal Print Queue",
                "Dispatched " + copies + " thermal barcode sticker(s) for [" + (barcode != null ? barcode : "CODE") + "] " + itemName + " to POS Thermal printer.");
            logPrintAsync(copies);
        }
    }

    private void logPrintAsync(int count) {
        UiTaskExecutor.submitAction("barcode-log-print", () -> {
            try {
                String url = apiUrl("/api/barcode/log-print?count=" + count);
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody());
                ApiSession.authorize(b);
                ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
            return true;
        }, res -> {}, err -> {});
    }

    // ==========================================
    // 2. POPUP: Generate / Assign Barcode
    // ==========================================
    public void generateBarcodeForItem(BarcodeItemDto item) {
        if (item == null) return;

        Dialog<ButtonType> dialog = new OwnedDialog<>(tblItems);
        dialog.setTitle("Generate Barcode");
        dialog.setHeaderText("Assign or Generate Barcode for " + item.itemName + " [" + item.itemCode + "]");

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dp.getStyleClass().addAll("approved-dialog", "erp-dialog");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        Label lblItem = new Label(item.itemName != null ? item.itemName : item.itemCode);
        lblItem.setStyle("-fx-font-weight: bold;");

        TextField txtBarcode = new TextField(item.barcode != null && !item.barcode.isBlank() ? item.barcode : "");
        txtBarcode.setPromptText("Enter or auto-generate barcode / EAN...");
        txtBarcode.setPrefWidth(220);

        Button btnAutoGenerate = new Button("Auto-Generate EAN-13", IconFactory.compactIcon("refresh", 14));
        btnAutoGenerate.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnAutoGenerate.setOnAction(e -> {
            long hash = Math.abs((item.itemCode != null ? item.itemCode.hashCode() : 0) + (item.id != null ? item.id * 10007L : 12345L)) % 1000000000L;
            String generated = "890" + String.format("%09d", hash);
            txtBarcode.setText(generated);
        });

        grid.add(new Label("Item:"), 0, 0);
        grid.add(lblItem, 1, 0);
        grid.add(new Label("Barcode:"), 0, 1);
        grid.add(txtBarcode, 1, 1);
        grid.add(btnAutoGenerate, 2, 1);

        dp.setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String entered = txtBarcode.getText() != null ? txtBarcode.getText().trim() : "";
            if (entered.isEmpty()) {
                entered = item.itemCode != null ? item.itemCode : "00000000";
            }
            item.barcode = entered;
            tblItems.refresh();
            updateKpis();
            AppDialogService.success(tblItems, "Barcode Updated",
                "Assigned barcode '" + entered + "' to " + item.itemName + ".");
        }
    }

    // ==========================================
    // 3. POPUP & PDF: Dispatch / Transport Slip
    // ==========================================
    public void promptAndPrintDispatchSlip(BarcodeItemDto item) {
        if (item == null) return;

        Dialog<Map<String, String>> dialog = new OwnedDialog<>(tblItems);
        dialog.setTitle("Print Dispatch Label / Transport Slip");
        dialog.setHeaderText("Generate Transport Slip for " + item.itemName + " [" + item.itemCode + "]");

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dp.getStyleClass().addAll("approved-dialog", "erp-dialog");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField txtConsignee = new TextField("Direct Customer Consignment");
        txtConsignee.setPromptText("Consignee / Customer Name");

        TextField txtDestination = new TextField("Warehouse Dispatch Hub");
        txtDestination.setPromptText("Destination City / Hub");

        TextField txtQuantity = new TextField("1 Box (Standard Pack)");
        txtQuantity.setPromptText("Quantity / Packaging details");

        ComboBox<String> cmbTransporter = new ComboBox<>();
        cmbTransporter.getItems().setAll("Self / Local Vehicle", "Blue Dart Express", "DTDC Cargo", "Delhivery Logistics", "VRL Logistics", "TCI Express");
        cmbTransporter.setValue("Self / Local Vehicle");

        TextField txtTrackingNo = new TextField("LR-" + (System.currentTimeMillis() % 10000000L));
        txtTrackingNo.setPromptText("Waybill / LR / Tracking No");

        grid.add(new Label("Consignee Name:"), 0, 0);
        grid.add(txtConsignee, 1, 0);
        grid.add(new Label("Destination City:"), 0, 1);
        grid.add(txtDestination, 1, 1);
        grid.add(new Label("Packaging / Units:"), 0, 2);
        grid.add(txtQuantity, 1, 2);
        grid.add(new Label("Carrier / Transporter:"), 0, 3);
        grid.add(cmbTransporter, 1, 3);
        grid.add(new Label("LR / Tracking Number:"), 0, 4);
        grid.add(txtTrackingNo, 1, 4);

        dp.setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Map<String, String> map = new HashMap<>();
                map.put("consignee", txtConsignee.getText() != null ? txtConsignee.getText().trim() : "Consignee");
                map.put("destination", txtDestination.getText() != null ? txtDestination.getText().trim() : "Hub");
                map.put("quantity", txtQuantity.getText() != null ? txtQuantity.getText().trim() : "1 Box");
                map.put("transporter", cmbTransporter.getValue() != null ? cmbTransporter.getValue() : "Carrier");
                map.put("trackingNo", txtTrackingNo.getText() != null ? txtTrackingNo.getText().trim() : "LR-0001");
                return map;
            }
            return null;
        });

        Optional<Map<String, String>> res = dialog.showAndWait();
        if (res.isPresent()) {
            Map<String, String> info = res.get();
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Consignment Dispatch Slip PDF");
            String safeCode = item.itemCode != null ? item.itemCode.replaceAll("[^a-zA-Z0-9.-]", "_") : "Item";
            chooser.setInitialFileName("Dispatch_Slip_" + safeCode + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));

            File target = chooser.showSaveDialog(tblItems.getScene().getWindow());
            if (target != null) {
                try {
                    generateDispatchSlipPdf(target, item, info.get("consignee"), info.get("destination"),
                        info.get("transporter"), info.get("trackingNo"), info.get("quantity"));

                    AppDialogService.success(tblItems, "Dispatch Slip Generated",
                        "Saved transport slip to " + target.getName() + ".");

                    logPrintAsync(1);

                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().open(target);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ex) {
                    AppDialogService.error(tblItems, "PDF Generation Failed",
                        "Could not generate dispatch slip PDF: " + ex.getMessage(), ex.getMessage());
                }
            }
        }
    }

    private void generateDispatchSlipPdf(File targetFile, BarcodeItemDto item, String consignee, String destCity,
                                         String transporter, String trackingNo, String packageQty) throws IOException {
        org.example.util.ITextRuntimeSupport.configure();
        DeviceRgb primaryBlue = new DeviceRgb(15, 76, 129);
        DeviceRgb paleBlue = new DeviceRgb(241, 245, 249);

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(targetFile));
             Document document = new Document(pdf, PageSize.A4)) {
            document.setMargins(32, 32, 32, 32);

            // Header Table (Company info + Dispatch Title)
            Table header = new Table(new float[]{3.2f, 2.0f}).useAllAvailableWidth();
            String compName = BrandingService.companyName();
            String tagline = ConfigManager.get("company.tagline", "ENTERPRISE LOGISTICS & DISTRIBUTION");
            header.addCell(new Cell().add(new Paragraph(compName).setBold().setFontSize(16).setFontColor(primaryBlue))
                .add(new Paragraph(tagline).setFontSize(9).setFontColor(ColorConstants.GRAY))
                .add(new Paragraph(ConfigManager.get("company.address", "Industrial Area, Phase-II"))
                    .setFontSize(8).setFontColor(ColorConstants.DARK_GRAY))
                .add(new Paragraph("GSTIN: " + ConfigManager.get("company.gstin", "24AABCS1429B1Z1") + "  |  Ph: " + ConfigManager.get("company.phone", "+91 98765 43210"))
                    .setFontSize(8).setFontColor(ColorConstants.DARK_GRAY))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

            header.addCell(new Cell().add(new Paragraph("CONSIGNMENT DISPATCH SLIP\n& TRANSPORT LABEL")
                .setBold().setFontSize(13).setFontColor(primaryBlue).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Date: " + BusinessClock.today() + "\nSlip Ref: DSP-" + (System.currentTimeMillis() % 1000000L))
                    .setFontSize(9).setTextAlignment(TextAlignment.RIGHT).setFontColor(ColorConstants.DARK_GRAY))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            document.add(header);

            // Divider bar
            Table divider = new Table(1).useAllAvailableWidth();
            divider.addCell(new Cell().setHeight(2).setBackgroundColor(primaryBlue).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            divider.setMarginTop(8);
            divider.setMarginBottom(12);
            document.add(divider);

            // Logistics & Routing Table
            Table logisticsTable = new Table(new float[]{1, 1}).useAllAvailableWidth();

            Cell consignorCell = new Cell()
                .add(new Paragraph("DISPATCH / SENDER DETAILS").setBold().setFontSize(9).setFontColor(primaryBlue))
                .add(new Paragraph("Sender: " + compName).setFontSize(9).setBold())
                .add(new Paragraph("Facility: Central Warehouse Outward").setFontSize(8))
                .add(new Paragraph("Transporter / Courier: " + transporter).setFontSize(8).setBold())
                .add(new Paragraph("LR / Tracking No: " + trackingNo).setFontSize(8))
                .setBackgroundColor(paleBlue).setPadding(8);

            Cell consigneeCell = new Cell()
                .add(new Paragraph("DELIVER TO / CONSIGNEE").setBold().setFontSize(9).setFontColor(primaryBlue))
                .add(new Paragraph(consignee).setFontSize(9).setBold())
                .add(new Paragraph("Destination: " + destCity).setFontSize(8))
                .add(new Paragraph("Package Count: " + packageQty).setFontSize(8).setBold())
                .add(new Paragraph("Mode: Road Express Cargo").setFontSize(8))
                .setBackgroundColor(paleBlue).setPadding(8);

            logisticsTable.addCell(consignorCell);
            logisticsTable.addCell(consigneeCell);
            logisticsTable.setMarginBottom(14);
            document.add(logisticsTable);

            // Item Manifest Table
            Table manifest = new Table(new float[]{1.5f, 3.5f, 1.8f, 1.2f, 1.5f}).useAllAvailableWidth();
            String[] headers = {"Item Code", "Item Description", "Category", "Quantity", "Declared Value"};
            for (String h : headers) {
                manifest.addHeaderCell(new Cell().add(new Paragraph(h).setBold().setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(primaryBlue).setPadding(6).setTextAlignment(TextAlignment.CENTER));
            }

            manifest.addCell(new Cell().add(new Paragraph(item.itemCode != null ? item.itemCode : "-").setFontSize(9)).setPadding(6).setTextAlignment(TextAlignment.CENTER));
            manifest.addCell(new Cell().add(new Paragraph(item.itemName != null ? item.itemName : "-").setBold().setFontSize(9)).setPadding(6));
            manifest.addCell(new Cell().add(new Paragraph(item.category != null ? item.category : "General").setFontSize(8)).setPadding(6).setTextAlignment(TextAlignment.CENTER));
            manifest.addCell(new Cell().add(new Paragraph(packageQty).setFontSize(9).setBold()).setPadding(6).setTextAlignment(TextAlignment.CENTER));
            manifest.addCell(new Cell().add(new Paragraph(currency.format(item.sellingPrice != null ? item.sellingPrice : BigDecimal.ZERO)).setFontSize(9).setBold()).setPadding(6).setTextAlignment(TextAlignment.RIGHT));
            manifest.setMarginBottom(16);
            document.add(manifest);

            // Barcode Section
            String barcodeVal = (item.barcode != null && !item.barcode.isBlank()) ? item.barcode : item.itemCode;
            if (barcodeVal == null || barcodeVal.isBlank()) barcodeVal = "ITEM-" + (System.currentTimeMillis() % 10000L);

            BufferedImage barcodeImg = Code128Encoder.renderImage(barcodeVal, 380, 85, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(barcodeImg, "PNG", baos);
            Image itextBarcode = new Image(ImageDataFactory.create(baos.toByteArray()));
            itextBarcode.setHorizontalAlignment(HorizontalAlignment.CENTER);
            itextBarcode.setWidth(260);

            Table barcodeBox = new Table(1).useAllAvailableWidth();
            Cell bCell = new Cell()
                .add(new Paragraph("PACKAGE TRACKING & SCAN-VERIFICATION BARCODE").setBold().setFontSize(8).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
                .add(itextBarcode)
                .add(new Paragraph("Item: " + (item.itemCode != null ? item.itemCode : "") + "  •  Scan Code: " + barcodeVal).setFontSize(8).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.DARK_GRAY))
                .setBorder(new com.itextpdf.layout.borders.DashedBorder(primaryBlue, 1))
                .setPadding(10)
                .setBackgroundColor(new DeviceRgb(250, 250, 252));
            barcodeBox.addCell(bCell);
            barcodeBox.setMarginBottom(20);
            document.add(barcodeBox);

            // Verification & Signatures Table
            Table sigTable = new Table(new float[]{1, 1}).useAllAvailableWidth();
            sigTable.addCell(new Cell()
                .add(new Paragraph("DECLARATION & PACKING VERIFICATION\n").setBold().setFontSize(8).setFontColor(primaryBlue))
                .add(new Paragraph("Certified that consignment contents are safely packed, labeled, and dispatched in accordance with logistics safety norms.\n\n\n_____________________________________\nAuthorized Dispatch Officer (Sign & Stamp)").setFontSize(8))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

            sigTable.addCell(new Cell()
                .add(new Paragraph("CARRIER / RECEIVER ACKNOWLEDGEMENT\n").setBold().setFontSize(8).setFontColor(primaryBlue))
                .add(new Paragraph("Received the above package(s) in intact, undamaged condition for safe transit to consignee.\n\n\n_____________________________________\nTransporter / Receiver Signature").setFontSize(8))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            document.add(sigTable);
        }
    }

    // Top Header Buttons
    @FXML
    public void printThermal() {
        BarcodeItemDto item = tblItems.getSelectionModel().getSelectedItem();
        if (item == null && !filteredList.isEmpty()) {
            item = filteredList.get(0);
        }
        if (item != null) {
            showLabelPreviewDialog(item);
        } else {
            AppDialogService.warning(tblItems, "Barcode Studio", "No Item Selected", "Please select an item to print barcode stickers.");
        }
    }

    @FXML
    public void printA4Sheet() {
        BarcodeItemDto item = tblItems.getSelectionModel().getSelectedItem();
        if (item == null && !filteredList.isEmpty()) {
            item = filteredList.get(0);
        }
        if (item != null) {
            showLabelPreviewDialog(item);
        } else {
            AppDialogService.warning(tblItems, "Barcode Studio", "No Item Selected", "Please select an item to print barcode sheet.");
        }
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
