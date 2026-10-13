package org.example.util;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.transform.Scale;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.service.BrandingService;
import org.example.service.PurchaseService;
import org.example.service.SalesService;
import org.example.shared.barcode.Code128Encoder;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Universal Order-Level Barcode Label System.
 * Generates dispatch / inward shipping labels across Sales, Purchase, Sales Returns, and Purchase Returns.
 * Layout adheres strictly to the approved specification (5-column manifest: Description, HSN Code, Item Remarks, Qty, Barcode).
 */
public final class OrderDispatchBarcodeDialog {

    public record OrderBarcodeItem(
            String description,
            String hsnCode,
            String remarks,
            double quantity,
            String unit,
            String barcode
    ) {}

    public record OrderBarcodeDocument(
            String documentType,
            String documentNo,
            String documentDate,
            String companyName,
            String companyGstin,
            String partyRole,
            String partyName,
            String partyGstin,
            String destination,
            int initialBoxIndex,
            int initialBoxTotal,
            List<OrderBarcodeItem> items
    ) {}

    private OrderDispatchBarcodeDialog() {}

    /**
     * Launches the order-level dispatch barcode label dialog for a Sales order.
     */
    public static void showForSale(Node owner, Sales sale) {
        if (sale == null) {
            AppDialogService.warning(owner, "Barcode Label", "No Sale Selected", "Please select a valid sales invoice to generate barcode label.");
            return;
        }

        Sales fullSale = sale;
        if (sale.getLines() == null || sale.getLines().isEmpty()) {
            try {
                Sales fetched = new SalesService().getByInvoice(sale.getInvoiceNo());
                if (fetched != null) fullSale = fetched;
            } catch (Exception ignored) {}
        }

        List<OrderBarcodeItem> items = new ArrayList<>();
        if (fullSale.getLines() != null) {
            for (SalesLine line : fullSale.getLines()) {
                String desc = line.getItemDescription() != null && !line.getItemDescription().isBlank()
                        ? line.getItemDescription() : (line.getItemCode() != null ? line.getItemCode() : "Material Item");
                String hsn = line.getItemHsn() != null && !line.getItemHsn().isBlank() ? line.getItemHsn() : "8471";
                String rem = line.getItemRemarks() != null && !line.getItemRemarks().isBlank() ? line.getItemRemarks() : "Standard Supply";
                double qty = line.getQuantity() > 0 ? line.getQuantity() : 1.0;
                String unit = line.getItemUnit() != null && !line.getItemUnit().isBlank() ? line.getItemUnit() : "NOS";
                String bar = line.getItemCode() != null && !line.getItemCode().isBlank() ? line.getItemCode() : fullSale.getInvoiceNo();
                items.add(new OrderBarcodeItem(desc, hsn, rem, qty, unit, bar));
            }
        }
        if (items.isEmpty()) {
            items.add(new OrderBarcodeItem("Consignment Goods", "8471", "Dispatch Consignment", 1.0, "LOT", fullSale.getInvoiceNo()));
        }

        String compName = BrandingService.companyName();
        String compGstin = ConfigManager.get("company.gstin", "24ABCDE1234F1Z5");
        String partyName = fullSale.getCustomer() != null && fullSale.getCustomer().getName() != null
                ? fullSale.getCustomer().getName() : "Valued Customer";
        String partyGstin = fullSale.getCustomer() != null && fullSale.getCustomer().getGstin() != null
                ? fullSale.getCustomer().getGstin().trim() : (fullSale.getGstin() != null ? fullSale.getGstin().trim() : "");
        String dest = fullSale.getDeliveryAddress() != null && !fullSale.getDeliveryAddress().isBlank()
                ? fullSale.getDeliveryAddress()
                : (fullSale.getCustomer() != null && fullSale.getCustomer().getAddress() != null ? fullSale.getCustomer().getAddress() : "Customer Destination");

        OrderBarcodeDocument doc = new OrderBarcodeDocument(
                "DISPATCH ORDER",
                fullSale.getInvoiceNo() != null ? fullSale.getInvoiceNo() : "INV-ORDER",
                fullSale.getInvoiceDate() != null ? BusinessClock.formatDate(fullSale.getInvoiceDate()) : BusinessClock.today().toString(),
                compName,
                compGstin,
                "Customer:",
                partyName,
                partyGstin,
                dest,
                1,
                1,
                items
        );

        show(owner, doc);
    }

    /**
     * Launches the order-level barcode label dialog for a Purchase order.
     */
    public static void showForPurchase(Node owner, Purchase purchase) {
        if (purchase == null) {
            AppDialogService.warning(owner, "Barcode Label", "No Purchase Selected", "Please select a valid purchase order to generate barcode label.");
            return;
        }

        Purchase fullPurchase = purchase;
        if (purchase.getLines() == null || purchase.getLines().isEmpty()) {
            try {
                Purchase fetched = new PurchaseService().getByInvoice(purchase.getInvoiceNo());
                if (fetched != null) fullPurchase = fetched;
            } catch (Exception ignored) {}
        }

        List<OrderBarcodeItem> items = new ArrayList<>();
        if (fullPurchase.getLines() != null) {
            for (PurchaseLine line : fullPurchase.getLines()) {
                String desc = line.getItemDescription() != null && !line.getItemDescription().isBlank()
                        ? line.getItemDescription() : (line.getItemCode() != null ? line.getItemCode() : "Material Item");
                String hsn = line.getItemHsn() != null && !line.getItemHsn().isBlank() ? line.getItemHsn() : "8471";
                String rem = line.getItemRemarks() != null && !line.getItemRemarks().isBlank() ? line.getItemRemarks() : "Inward Raw Material";
                double qty = line.getQuantity() > 0 ? line.getQuantity() : 1.0;
                String unit = line.getItemUnit() != null && !line.getItemUnit().isBlank() ? line.getItemUnit() : "NOS";
                String bar = line.getItemCode() != null && !line.getItemCode().isBlank() ? line.getItemCode() : fullPurchase.getInvoiceNo();
                items.add(new OrderBarcodeItem(desc, hsn, rem, qty, unit, bar));
            }
        }
        if (items.isEmpty()) {
            items.add(new OrderBarcodeItem("Goods Receipt Materials", "8471", "Goods Inward", 1.0, "LOT", fullPurchase.getInvoiceNo()));
        }

        String compName = BrandingService.companyName();
        String compGstin = ConfigManager.get("company.gstin", "24ABCDE1234F1Z5");
        String partyName = fullPurchase.getSupplier() != null && fullPurchase.getSupplier().getName() != null
                ? fullPurchase.getSupplier().getName() : "Material Supplier";
        String partyGstin = fullPurchase.getSupplier() != null && fullPurchase.getSupplier().getGstin() != null
                ? fullPurchase.getSupplier().getGstin().trim() : (fullPurchase.getBillingGstin() != null ? fullPurchase.getBillingGstin().trim() : "");
        String dest = fullPurchase.getWarehouse() != null && !fullPurchase.getWarehouse().isBlank()
                ? fullPurchase.getWarehouse() : "Central Inward Warehouse";

        OrderBarcodeDocument doc = new OrderBarcodeDocument(
                "PURCHASE INWARD / GOODS RECEIPT",
                fullPurchase.getInvoiceNo() != null ? fullPurchase.getInvoiceNo() : "PO-ORDER",
                fullPurchase.getInvoiceDate() != null ? BusinessClock.formatDate(fullPurchase.getInvoiceDate()) : BusinessClock.today().toString(),
                compName,
                compGstin,
                "Supplier:",
                partyName,
                partyGstin,
                dest,
                1,
                1,
                items
        );

        show(owner, doc);
    }

    /**
     * Launches the order-level barcode label dialog for a Sales Return (Credit Note).
     */
    public static void showForCreditNote(Node owner, String noteNo, String noteDate, String partyName, String partyGstin, String origInv, String reason, double totalAmt) {
        String compName = BrandingService.companyName();
        String compGstin = ConfigManager.get("company.gstin", "24ABCDE1234F1Z5");

        List<OrderBarcodeItem> items = new ArrayList<>();
        items.add(new OrderBarcodeItem(
                "Returned Sales Item(s) [" + (reason != null && !reason.isBlank() ? reason : "Sales Return") + "]",
                "8471",
                "Orig Inv: " + (origInv != null ? origInv : "-"),
                1.0,
                "LOT",
                noteNo != null ? noteNo : "CN-RETURN"
        ));

        OrderBarcodeDocument doc = new OrderBarcodeDocument(
                "SALES RETURN INWARD",
                noteNo != null ? noteNo : "CN-ORDER",
                noteDate != null ? noteDate : BusinessClock.today().toString(),
                compName,
                compGstin,
                "Customer:",
                partyName != null ? partyName : "Customer",
                partyGstin != null ? partyGstin : "",
                "Returns Receiving Bay / Inspection",
                1,
                1,
                items
        );

        show(owner, doc);
    }

    /**
     * Launches the order-level barcode label dialog for a Purchase Return (Debit Note).
     */
    public static void showForDebitNote(Node owner, String noteNo, String noteDate, String supplierName, String supplierGstin, String origBill, String reason, double totalAmt) {
        String compName = BrandingService.companyName();
        String compGstin = ConfigManager.get("company.gstin", "24ABCDE1234F1Z5");

        List<OrderBarcodeItem> items = new ArrayList<>();
        items.add(new OrderBarcodeItem(
                "Returned Material [Supplier Return - " + (reason != null && !reason.isBlank() ? reason : "Return") + "]",
                "8471",
                "Orig Bill: " + (origBill != null ? origBill : "-"),
                1.0,
                "LOT",
                noteNo != null ? noteNo : "DN-RETURN"
        ));

        OrderBarcodeDocument doc = new OrderBarcodeDocument(
                "PURCHASE RETURN DISPATCH",
                noteNo != null ? noteNo : "DN-ORDER",
                noteDate != null ? noteDate : BusinessClock.today().toString(),
                compName,
                compGstin,
                "Supplier:",
                supplierName != null ? supplierName : "Supplier",
                supplierGstin != null ? supplierGstin : "",
                "Supplier Return Dispatch RTV",
                1,
                1,
                items
        );

        show(owner, doc);
    }

    /**
     * Displays the interactive Order Barcode Preview and Print Dialog.
     */
    public static void show(Node owner, OrderBarcodeDocument doc) {
        OwnedDialog<Void> dialog = new OwnedDialog<>(owner);
        dialog.setTitle("Order Barcode Label Studio • " + doc.documentNo());
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        // Explicitly configure as workspace dialog so AppDialogRenderer applies .dse-dialog-workspace
        // and WindowUtilsFx.fitDialogToOwnerScreen ensures unconstrained width and visible header [X] close button
        AppDialogRenderer.configureWorkspace(
                dialog,
                "info",
                "Order Barcode Label Studio",
                "Order Barcode Label Studio • " + doc.documentNo(),
                "",
                ""
        );

        // State properties for dynamic reactive label updates
        SimpleIntegerProperty boxIndexProp = new SimpleIntegerProperty(doc.initialBoxIndex() > 0 ? doc.initialBoxIndex() : 1);
        SimpleIntegerProperty boxTotalProp = new SimpleIntegerProperty(doc.initialBoxTotal() > 0 ? doc.initialBoxTotal() : 1);
        SimpleStringProperty destinationProp = new SimpleStringProperty(doc.destination() != null ? doc.destination() : "Warehouse Logistics");

        // Top Controls Bar
        HBox controlsBar = new HBox(8);
        controlsBar.setAlignment(Pos.CENTER_LEFT);
        controlsBar.setPadding(new Insets(10, 14, 10, 14));
        controlsBar.setStyle("-fx-background-color: -fx-background-secondary; -fx-background-radius: 6; -fx-border-color: -fx-border-color; -fx-border-radius: 6;");

        Label lblBox = new Label("Package:");
        lblBox.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Spinner<Integer> spBoxIndex = new Spinner<>(1, 999, boxIndexProp.get());
        spBoxIndex.setPrefWidth(65);
        spBoxIndex.setMinWidth(60);
        spBoxIndex.setEditable(true);
        spBoxIndex.valueProperty().addListener((o, prev, cur) -> { if (cur != null) boxIndexProp.set(cur); });

        Label lblOf = new Label("of");
        lblOf.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Spinner<Integer> spBoxTotal = new Spinner<>(1, 999, boxTotalProp.get());
        spBoxTotal.setPrefWidth(65);
        spBoxTotal.setMinWidth(60);
        spBoxTotal.setEditable(true);
        spBoxTotal.valueProperty().addListener((o, prev, cur) -> { if (cur != null) boxTotalProp.set(cur); });

        Label lblDest = new Label("Destination:");
        lblDest.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        TextField txtDest = new TextField(destinationProp.get());
        txtDest.setPromptText("Delivery destination");
        txtDest.setPrefWidth(220);
        txtDest.setMinWidth(140);
        HBox.setHgrow(txtDest, Priority.ALWAYS);
        txtDest.textProperty().addListener((o, prev, cur) -> destinationProp.set(cur != null ? cur.trim() : ""));

        Button btnPrintThermal = new Button("Print Direct");
        btnPrintThermal.getStyleClass().addAll("approved-button", "approved-primary-button");
        btnPrintThermal.setGraphic(IconFactory.compactIcon("print", 16));
        btnPrintThermal.setMinWidth(Region.USE_PREF_SIZE);

        Button btnExportPdf = new Button("Export PDF (4\" × 6\")");
        btnExportPdf.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnExportPdf.setGraphic(IconFactory.compactIcon("pdf", 16));
        btnExportPdf.setMinWidth(Region.USE_PREF_SIZE);

        controlsBar.getChildren().addAll(lblBox, spBoxIndex, lblOf, spBoxTotal, lblDest, txtDest, btnPrintThermal, btnExportPdf);

        // Central Live Preview Card
        VBox labelCard = buildLiveLabelCard(doc, boxIndexProp, boxTotalProp, destinationProp);

        StackPane previewWrapper = new StackPane(labelCard);
        previewWrapper.setAlignment(Pos.TOP_CENTER);
        previewWrapper.setPadding(new Insets(14));
        previewWrapper.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(previewWrapper);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 4;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Actions wiring
        btnPrintThermal.setOnAction(e -> {
            try {
                PrinterJob job = PrinterJob.createPrinterJob();
                if (job != null && job.showPrintDialog(dialog.getDialogPane().getScene().getWindow())) {
                    PageLayout pageLayout = job.getJobSettings().getPageLayout();
                    double printableWidth = pageLayout.getPrintableWidth();
                    double printableHeight = pageLayout.getPrintableHeight();

                    // Build an isolated print card node without dropshadow to prevent clipping on physical / virtual printers
                    VBox printCard = buildLiveLabelCard(doc, boxIndexProp, boxTotalProp, destinationProp);
                    printCard.setEffect(null);

                    double cardWidth = 540.0;
                    double cardHeight = 650.0;
                    double scaleX = printableWidth / cardWidth;
                    double scaleY = printableHeight / cardHeight;
                    double scaleFactor = Math.min(scaleX, scaleY);

                    Scale scale = new Scale(scaleFactor, scaleFactor);
                    printCard.getTransforms().add(scale);
                    try {
                        boolean ok = job.printPage(printCard);
                        if (ok) {
                            job.endJob();
                            AppDialogService.success(owner, "Printing Sent", "Order barcode label sent to printer.");
                        }
                    } finally {
                        printCard.getTransforms().remove(scale);
                    }
                }
            } catch (Exception ex) {
                AppDialogService.error(owner, "Printing Error", "Could not print label: " + ex.getMessage(), ex.getMessage());
            }
        });

        btnExportPdf.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save 4\" × 6\" Order Barcode Label PDF");
            String safeDocNo = doc.documentNo().replaceAll("[^a-zA-Z0-9_-]", "_");
            chooser.setInitialFileName("Order_Label_" + safeDocNo + "_Box" + boxIndexProp.get() + "of" + boxTotalProp.get() + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));

            File target = chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if (target != null) {
                try {
                    exportLabelPdf(target, doc, boxIndexProp.get(), boxTotalProp.get(), destinationProp.get());
                    AppDialogService.success(owner, "PDF Saved", "Label exported successfully to " + target.getName());
                    if (Desktop.isDesktopSupported()) {
                        try { Desktop.getDesktop().open(target); } catch (Exception ignored) {}
                    }
                } catch (Exception ex) {
                    AppDialogService.error(owner, "Export Failed", "Could not export PDF: " + ex.getMessage(), ex.getMessage());
                }
            }
        });

        VBox rootLayout = new VBox(12, controlsBar, scrollPane);
        rootLayout.setPadding(new Insets(10));
        rootLayout.setStyle("-fx-background-color: -fx-background;");

        DialogPane dp = dialog.getDialogPane();
        dp.setMinWidth(880);
        dp.setPrefWidth(980);
        dp.setMinHeight(680);
        dp.setPrefHeight(780);
        dp.setContent(rootLayout);
        dp.getButtonTypes().setAll(ButtonType.CLOSE);

        Button closeBtn = (Button) dp.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.getStyleClass().addAll("approved-button", "approved-secondary-button");
            closeBtn.setOnAction(e -> dialog.close());
        }

        // Ensure ESC key instantly closes the dialog
        dialog.setOnShowing(ev -> {
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                scene.addEventFilter(KeyEvent.KEY_PRESSED, k -> {
                    if (k.getCode() == KeyCode.ESCAPE) {
                        dialog.close();
                        k.consume();
                    }
                });
            }
        });

        dialog.showAndWait();
    }

    /**
     * Builds the live visual JavaFX card representing the physical 4" × 6" shipping barcode label.
     */
    private static VBox buildLiveLabelCard(
            OrderBarcodeDocument doc,
            SimpleIntegerProperty boxIndexProp,
            SimpleIntegerProperty boxTotalProp,
            SimpleStringProperty destinationProp
    ) {
        VBox card = new VBox(8);
        card.setMinWidth(540);
        card.setPrefWidth(540);
        card.setMaxWidth(540);
        card.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #0f4c81; " +
                "-fx-border-width: 2px; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px; " +
                "-fx-padding: 14px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 10, 0, 0, 2);"
        );

        // 1. Header (Company Name, Seller GSTIN, Doc Type Banner, Doc No, Date)
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox companyBox = new VBox(2);
        companyBox.setAlignment(Pos.CENTER_LEFT);
        Label lblComp = new Label(doc.companyName());
        lblComp.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: #0f4c81;");
        Label lblSellerGstin = new Label("Seller GSTIN: " + (doc.companyGstin() != null && !doc.companyGstin().isBlank() ? doc.companyGstin() : "24ABCDE1234F1Z5"));
        lblSellerGstin.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #334155;");
        companyBox.getChildren().addAll(lblComp, lblSellerGstin);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox bannerBox = new VBox(2);
        bannerBox.setAlignment(Pos.CENTER_RIGHT);

        Label lblBanner = new Label(doc.documentType());
        lblBanner.setStyle(
                "-fx-background-color: #0f4c81; -fx-text-fill: #ffffff; " +
                "-fx-font-size: 11px; -fx-font-weight: bold; " +
                "-fx-padding: 3 8 3 8; -fx-background-radius: 4px;"
        );

        Label lblDocNo = new Label("Doc No: " + doc.documentNo());
        lblDocNo.setStyle("-fx-font-size: 12px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");

        Label lblDate = new Label("Date: " + doc.documentDate());
        lblDate.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");

        bannerBox.getChildren().addAll(lblBanner, lblDocNo, lblDate);
        header.getChildren().addAll(companyBox, spacer, bannerBox);
        card.getChildren().add(header);

        // Divider
        Separator sep1 = new Separator();
        sep1.setStyle("-fx-border-color: #cbd5e1; -fx-border-width: 1 0 0 0;");
        card.getChildren().add(sep1);

        // 2. Logistics & Routing Bar (Party Name, Party GSTIN, Destination, Box count)
        GridPane logisticsGrid = new GridPane();
        logisticsGrid.setHgap(12);
        logisticsGrid.setVgap(4);
        logisticsGrid.setPadding(new Insets(8));
        logisticsGrid.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 4px; -fx-border-color: #cbd5e1; -fx-border-radius: 4px;");

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(58);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(42);
        logisticsGrid.getColumnConstraints().addAll(c1, c2);

        Label lblParty = new Label(doc.partyRole() + " " + doc.partyName());
        lblParty.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        lblParty.setWrapText(true);

        Label lblPartyGstin = new Label((doc.partyRole().toLowerCase().contains("supplier") ? "Supplier GSTIN: " : "Cust GSTIN: ") +
                (!doc.partyGstin().isBlank() ? doc.partyGstin() : "Unregistered"));
        lblPartyGstin.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #334155;");

        Label lblDestination = new Label("Destination: " + destinationProp.get());
        lblDestination.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        lblDestination.setWrapText(true);
        destinationProp.addListener((o, prev, cur) -> lblDestination.setText("Destination: " + cur));

        Label lblBoxInfo = new Label("Package: Box " + boxIndexProp.get() + " of " + boxTotalProp.get());
        lblBoxInfo.setStyle("-fx-font-size: 11px; -fx-font-weight: 900; -fx-text-fill: #0f4c81;");
        Runnable updateBoxLabel = () -> lblBoxInfo.setText("Package: Box " + boxIndexProp.get() + " of " + boxTotalProp.get());
        boxIndexProp.addListener((o, p, c) -> updateBoxLabel.run());
        boxTotalProp.addListener((o, p, c) -> updateBoxLabel.run());

        logisticsGrid.add(lblParty, 0, 0);
        logisticsGrid.add(lblDestination, 1, 0);
        logisticsGrid.add(lblPartyGstin, 0, 1);
        logisticsGrid.add(lblBoxInfo, 1, 1);
        card.getChildren().add(logisticsGrid);

        // 3. Center Master Barcode & QR Code Section
        HBox barcodeSection = new HBox(20);
        barcodeSection.setAlignment(Pos.CENTER);
        barcodeSection.setPadding(new Insets(6, 0, 6, 0));

        // Master Code-128 barcode
        VBox orderBarcodeBox = new VBox(2);
        orderBarcodeBox.setAlignment(Pos.CENTER);
        try {
            BufferedImage bcImg = Code128Encoder.renderImage(doc.documentNo(), 330, 62, true);
            ImageView ivBarcode = new ImageView(toFxImage(bcImg));
            ivBarcode.setFitWidth(310);
            ivBarcode.setPreserveRatio(true);
            orderBarcodeBox.getChildren().add(ivBarcode);
        } catch (Exception ex) {
            Label fb = new Label("*" + doc.documentNo() + "*");
            fb.setStyle("-fx-font-family: monospace; -fx-font-size: 14px; -fx-font-weight: bold;");
            orderBarcodeBox.getChildren().add(fb);
        }

        // 2D QR Code
        VBox qrBox = new VBox(2);
        qrBox.setAlignment(Pos.CENTER);
        try {
            double totalQty = doc.items().stream().mapToDouble(OrderBarcodeItem::quantity).sum();
            String qrPayload = doc.documentNo() + "|" + doc.documentDate() + "|" + doc.companyGstin() + "|" +
                    doc.partyGstin() + "|Items:" + doc.items().size() + "|Qty:" + totalQty;
            Image qrImage = QrCodeImageFactory.create(qrPayload, 82);
            ImageView ivQr = new ImageView(qrImage);
            ivQr.setFitWidth(76);
            ivQr.setFitHeight(76);
            qrBox.getChildren().add(ivQr);
        } catch (Exception ignored) {}

        barcodeSection.getChildren().addAll(orderBarcodeBox, qrBox);
        card.getChildren().add(barcodeSection);

        // 4. Manifest Table (Strictly 5 Columns: Description, HSN Code, Item Remarks, Qty, Barcode)
        VBox tableContainer = new VBox();
        tableContainer.setPrefWidth(512);
        tableContainer.setMaxWidth(512);
        tableContainer.setStyle("-fx-border-color: #0f4c81; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        // Table Header
        GridPane tblHeader = new GridPane();
        tblHeader.setStyle("-fx-background-color: #0f4c81; -fx-padding: 5 6 5 6;");
        setManifestGridColumns(tblHeader);

        Label thDesc = headerLabel("Description", Pos.CENTER_LEFT);
        Label thHsn = headerLabel("HSN Code", Pos.CENTER);
        Label thRemarks = headerLabel("Item Remarks", Pos.CENTER_LEFT);
        Label thQty = headerLabel("Qty", Pos.CENTER_RIGHT);
        Label thBarcode = headerLabel("Barcode", Pos.CENTER);

        tblHeader.add(thDesc, 0, 0);
        tblHeader.add(thHsn, 1, 0);
        tblHeader.add(thRemarks, 2, 0);
        tblHeader.add(thQty, 3, 0);
        tblHeader.add(thBarcode, 4, 0);
        tableContainer.getChildren().add(tblHeader);

        // Table Rows
        double totalQuantitySum = 0;
        int rowIndex = 0;
        for (OrderBarcodeItem itm : doc.items()) {
            totalQuantitySum += itm.quantity();
            GridPane row = new GridPane();
            row.setStyle("-fx-padding: 4 6 4 6; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0; -fx-background-color: " +
                    (rowIndex % 2 == 0 ? "#ffffff;" : "#f8fafc;"));
            setManifestGridColumns(row);

            Label tdDesc = new Label(itm.description());
            tdDesc.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
            tdDesc.setWrapText(true);

            Label tdHsn = new Label(itm.hsnCode());
            tdHsn.setStyle("-fx-font-size: 8.5px; -fx-text-fill: #334155;");
            tdHsn.setAlignment(Pos.CENTER);

            Label tdRemarks = new Label(itm.remarks());
            tdRemarks.setStyle("-fx-font-size: 8.5px; -fx-font-style: italic; -fx-text-fill: #475569;");
            tdRemarks.setWrapText(true);

            Label tdQty = new Label(String.format(Locale.ROOT, "%.2f %s", itm.quantity(), itm.unit()));
            tdQty.setStyle("-fx-font-size: 9px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
            tdQty.setAlignment(Pos.CENTER_RIGHT);

            Node barcodeNode;
            try {
                BufferedImage miniBc = Code128Encoder.renderImage(itm.barcode(), 90, 18, false);
                ImageView ivMini = new ImageView(toFxImage(miniBc));
                ivMini.setFitWidth(70);
                ivMini.setFitHeight(16);
                barcodeNode = ivMini;
            } catch (Exception ex) {
                Label fb = new Label(itm.barcode());
                fb.setStyle("-fx-font-size: 8px; -fx-font-family: monospace;");
                barcodeNode = fb;
            }

            row.add(tdDesc, 0, 0);
            row.add(tdHsn, 1, 0);
            row.add(tdRemarks, 2, 0);
            row.add(tdQty, 3, 0);
            row.add(barcodeNode, 4, 0);

            tableContainer.getChildren().add(row);
            rowIndex++;
        }

        // Table Footer Summary Row
        GridPane tblFooter = new GridPane();
        tblFooter.setStyle("-fx-background-color: #e2e8f0; -fx-padding: 5 6 5 6;");
        setManifestGridColumns(tblFooter);

        Label tfItems = new Label("Total Items: " + doc.items().size());
        tfItems.setStyle("-fx-font-size: 9px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");

        Label tfQty = new Label(String.format(Locale.ROOT, "Total Qty: %.2f", totalQuantitySum));
        tfQty.setStyle("-fx-font-size: 9px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        tfQty.setAlignment(Pos.CENTER_RIGHT);

        tblFooter.add(tfItems, 0, 0, 2, 1);
        tblFooter.add(tfQty, 2, 0, 3, 1);
        tableContainer.getChildren().add(tblFooter);

        card.getChildren().add(tableContainer);

        // 5. Verification & Security Footer
        VBox verificationBox = new VBox(4);
        verificationBox.setAlignment(Pos.CENTER);
        verificationBox.setPadding(new Insets(6, 0, 0, 0));

        Label lblSeal = new Label("DISPATCH VERIFICATION & SECURITY SEAL");
        lblSeal.setStyle("-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #64748b; -fx-letter-spacing: 1px;");

        HBox signRow = new HBox(16);
        signRow.setAlignment(Pos.CENTER);
        Label s1 = new Label("Checked By: _________________");
        s1.setStyle("-fx-font-size: 8px; -fx-text-fill: #475569;");
        Label s2 = new Label("Packed By: _________________");
        s2.setStyle("-fx-font-size: 8px; -fx-text-fill: #475569;");
        Label s3 = new Label("Carrier Sign: _________________");
        s3.setStyle("-fx-font-size: 8px; -fx-text-fill: #475569;");

        signRow.getChildren().addAll(s1, s2, s3);
        verificationBox.getChildren().addAll(lblSeal, signRow);
        card.getChildren().add(verificationBox);

        return card;
    }

    private static void setManifestGridColumns(GridPane grid) {
        grid.setMinWidth(498);
        grid.setPrefWidth(498);
        grid.setMaxWidth(498);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPrefWidth(165); // Description
        c0.setMinWidth(150);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPrefWidth(55);  // HSN Code
        c1.setMinWidth(50);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPrefWidth(110); // Item Remarks
        c2.setMinWidth(100);

        ColumnConstraints c3 = new ColumnConstraints();
        c3.setPrefWidth(80);  // Qty
        c3.setMinWidth(70);

        ColumnConstraints c4 = new ColumnConstraints();
        c4.setPrefWidth(88);  // Barcode
        c4.setMinWidth(80);

        grid.getColumnConstraints().setAll(c0, c1, c2, c3, c4);
    }

    private static Label headerLabel(String text, Pos align) {
        Label l = new Label(text);
        l.setAlignment(align);
        l.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        return l;
    }

    private static Image toFxImage(BufferedImage bimg) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bimg, "PNG", baos);
            return new Image(new ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Exports the clean vector 4" × 6" shipping label PDF matching the physical sticker specification.
     */
    public static void exportLabelPdf(
            File targetFile,
            OrderBarcodeDocument doc,
            int boxIndex,
            int boxTotal,
            String destination
    ) throws IOException {
        org.example.util.ITextRuntimeSupport.configure();
        DeviceRgb primaryBlue = new DeviceRgb(15, 76, 129);
        DeviceRgb paleBlue = new DeviceRgb(241, 245, 249);
        DeviceRgb darkText = new DeviceRgb(15, 23, 42);

        // Standard 4" x 6" shipping label page size: 288 x 432 pt
        PageSize labelPageSize = new PageSize(288, 432);

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(targetFile));
             Document document = new Document(pdf, labelPageSize)) {
            document.setMargins(10, 10, 10, 10);

            // 1. Header (Company Name, Seller GSTIN, Doc Type, Doc No, Date)
            Table header = new Table(new float[]{3.2f, 2.0f}).useAllAvailableWidth();
            String compName = doc.companyName();
            String compGstin = doc.companyGstin() != null ? doc.companyGstin() : "24ABCDE1234F1Z5";

            header.addCell(new Cell().add(new Paragraph(compName).setBold().setFontSize(13).setFontColor(primaryBlue))
                    .add(new Paragraph("Seller GSTIN: " + compGstin).setBold().setFontSize(7).setFontColor(ColorConstants.DARK_GRAY))
                    .setBorder(Border.NO_BORDER));

            header.addCell(new Cell().add(new Paragraph(doc.documentType()).setBold().setFontSize(8).setFontColor(ColorConstants.WHITE)
                            .setBackgroundColor(primaryBlue).setTextAlignment(TextAlignment.CENTER).setPadding(2))
                    .add(new Paragraph("Doc: " + doc.documentNo()).setBold().setFontSize(8).setFontColor(darkText).setTextAlignment(TextAlignment.RIGHT))
                    .add(new Paragraph("Date: " + doc.documentDate()).setFontSize(7).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER));
            document.add(header);

            // Divider line
            Table div = new Table(1).useAllAvailableWidth();
            div.addCell(new Cell().setHeight(1.5f).setBackgroundColor(primaryBlue).setBorder(Border.NO_BORDER));
            div.setMarginTop(4);
            div.setMarginBottom(6);
            document.add(div);

            // 2. Logistics & Routing Table
            Table logistics = new Table(new float[]{1.6f, 1.4f}).useAllAvailableWidth();
            logistics.setBackgroundColor(paleBlue);
            logistics.setPadding(4);

            Cell partyCell = new Cell()
                    .add(new Paragraph(doc.partyRole() + " " + doc.partyName()).setBold().setFontSize(7.5f).setFontColor(darkText))
                    .add(new Paragraph((doc.partyRole().toLowerCase().contains("supplier") ? "Supplier GSTIN: " : "Cust GSTIN: ") +
                            (!doc.partyGstin().isBlank() ? doc.partyGstin() : "Unregistered")).setFontSize(6.5f).setBold().setFontColor(ColorConstants.DARK_GRAY))
                    .setBorder(Border.NO_BORDER).setPadding(4);

            Cell routeCell = new Cell()
                    .add(new Paragraph("Destination: " + destination).setBold().setFontSize(7).setFontColor(darkText))
                    .add(new Paragraph("Package: Box " + boxIndex + " of " + boxTotal).setBold().setFontSize(8).setFontColor(primaryBlue))
                    .setBorder(Border.NO_BORDER).setPadding(4);

            logistics.addCell(partyCell);
            logistics.addCell(routeCell);
            logistics.setMarginBottom(6);
            document.add(logistics);

            // 3. Center Order Barcode & QR Code Section
            Table barcodeTable = new Table(new float[]{3.4f, 1.2f}).useAllAvailableWidth();

            // Render crisp Code-128 barcode
            BufferedImage barcodeImg = Code128Encoder.renderImage(doc.documentNo(), 340, 58, true);
            ByteArrayOutputStream bcBaos = new ByteArrayOutputStream();
            ImageIO.write(barcodeImg, "PNG", bcBaos);
            com.itextpdf.layout.element.Image itextBarcode = new com.itextpdf.layout.element.Image(ImageDataFactory.create(bcBaos.toByteArray()));
            itextBarcode.setWidth(185);
            itextBarcode.setHorizontalAlignment(HorizontalAlignment.CENTER);

            Cell bcCell = new Cell().add(itextBarcode).setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);

            // Render 2D QR Code
            double totalQty = doc.items().stream().mapToDouble(OrderBarcodeItem::quantity).sum();
            String qrPayload = doc.documentNo() + "|" + doc.documentDate() + "|" + doc.companyGstin() + "|" +
                    doc.partyGstin() + "|Items:" + doc.items().size() + "|Qty:" + totalQty;
            BarcodeQRCode qr = new BarcodeQRCode(qrPayload);
            com.itextpdf.layout.element.Image qrImage = new com.itextpdf.layout.element.Image(qr.createFormXObject(pdf))
                    .setWidth(52).setHeight(52).setHorizontalAlignment(HorizontalAlignment.CENTER);

            Cell qrCell = new Cell().add(qrImage).setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);

            barcodeTable.addCell(bcCell);
            barcodeTable.addCell(qrCell);
            barcodeTable.setMarginBottom(6);
            document.add(barcodeTable);

            // 4. Manifest Table (Strictly 5 Columns: Description, HSN Code, Item Remarks, Qty, Barcode)
            Table manifest = new Table(new float[]{2.5f, 1.0f, 1.7f, 1.1f, 1.4f}).useAllAvailableWidth();
            String[] headers = {"Description", "HSN Code", "Item Remarks", "Qty", "Barcode"};
            for (String h : headers) {
                manifest.addHeaderCell(new Cell().add(new Paragraph(h).setBold().setFontSize(7).setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(primaryBlue).setPadding(3).setTextAlignment(TextAlignment.CENTER));
            }

            double totalQtySum = 0;
            for (OrderBarcodeItem item : doc.items()) {
                totalQtySum += item.quantity();
                manifest.addCell(new Cell().add(new Paragraph(item.description()).setBold().setFontSize(7).setFontColor(darkText)).setPadding(3));
                manifest.addCell(new Cell().add(new Paragraph(item.hsnCode()).setFontSize(6.5f)).setPadding(3).setTextAlignment(TextAlignment.CENTER));
                manifest.addCell(new Cell().add(new Paragraph(item.remarks()).setItalic().setFontSize(6.5f).setFontColor(ColorConstants.GRAY)).setPadding(3));
                manifest.addCell(new Cell().add(new Paragraph(String.format(Locale.ROOT, "%.2f %s", item.quantity(), item.unit())).setBold().setFontSize(7)).setPadding(3).setTextAlignment(TextAlignment.RIGHT));

                // Mini barcode
                try {
                    BufferedImage miniImg = Code128Encoder.renderImage(item.barcode(), 90, 18, false);
                    ByteArrayOutputStream miniBaos = new ByteArrayOutputStream();
                    ImageIO.write(miniImg, "PNG", miniBaos);
                    com.itextpdf.layout.element.Image itextMini = new com.itextpdf.layout.element.Image(ImageDataFactory.create(miniBaos.toByteArray()));
                    itextMini.setWidth(42);
                    itextMini.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    manifest.addCell(new Cell().add(itextMini).setPadding(2).setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE));
                } catch (Exception ex) {
                    manifest.addCell(new Cell().add(new Paragraph(item.barcode()).setFontSize(6)).setPadding(3).setTextAlignment(TextAlignment.CENTER));
                }
            }

            // Totals Row
            Cell tfItemsCell = new Cell(1, 2)
                    .add(new Paragraph("Total Items: " + doc.items().size()).setBold().setFontSize(7).setFontColor(darkText))
                    .setBackgroundColor(paleBlue).setPadding(3);
            Cell tfQtyCell = new Cell(1, 3)
                    .add(new Paragraph(String.format(Locale.ROOT, "Total Qty: %.2f", totalQtySum)).setBold().setFontSize(7).setFontColor(darkText))
                    .setBackgroundColor(paleBlue).setPadding(3).setTextAlignment(TextAlignment.RIGHT);
            manifest.addCell(tfItemsCell);
            manifest.addCell(tfQtyCell);

            manifest.setMarginBottom(8);
            document.add(manifest);

            // 5. Verification & Security Footer
            Paragraph sealHeading = new Paragraph("DISPATCH VERIFICATION & SECURITY SEAL")
                    .setBold().setFontSize(6.5f).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(3);
            document.add(sealHeading);

            Table signTable = new Table(new float[]{1, 1, 1}).useAllAvailableWidth();
            signTable.addCell(new Cell().add(new Paragraph("Checked By:\n____________").setFontSize(6).setFontColor(ColorConstants.DARK_GRAY)).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER));
            signTable.addCell(new Cell().add(new Paragraph("Packed By:\n____________").setFontSize(6).setFontColor(ColorConstants.DARK_GRAY)).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER));
            signTable.addCell(new Cell().add(new Paragraph("Carrier Sign:\n____________").setFontSize(6).setFontColor(ColorConstants.DARK_GRAY)).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER));
            document.add(signTable);
        }
    }
}
