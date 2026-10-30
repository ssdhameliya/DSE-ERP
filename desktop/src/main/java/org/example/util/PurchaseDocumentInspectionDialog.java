package org.example.util;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.api.support.SupportApiClient;
import org.example.controller.EmailTransmissionDialog;
import org.example.controller.LinkedRecordContext;
import org.example.controller.PurchaseScreenContext;
import org.example.documentstudio.service.ExcelOutputService;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.model.PurchaseCharge;
import org.example.model.PurchaseLine;
import org.example.navigation.NavigationManager;
import org.example.service.*;
import org.example.theme.ThemeManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;

/**
 * Centered, spacious Document Inspection Window Pop-up for Purchase Orders / Invoices.
 * Replaces the cramped inline side drawer with a rich, multi-tab inspection interface.
 */
public final class PurchaseDocumentInspectionDialog {

    private PurchaseDocumentInspectionDialog() {}

    public static void show(Node ownerNode, Purchase purchaseSummary, SupportApiClient supportClient, Runnable onUpdated) {
        if (purchaseSummary == null) return;

        final Purchase currentPurchase = purchaseSummary;
        final SupportApiClient support = (supportClient != null) ? supportClient : new SupportApiClient();
        final PurchaseService purchaseService = new PurchaseService();

        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(ownerNode);
        dialog.setTitle("Purchase Invoice — " + currentPurchase.getInvoiceNo());
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("erp-document-inspection-dialog", "purchase-inspection-dialog", "approved-ui");
        pane.getProperties().put("dse.dialog.presented", true);
        PopupTableWorkspace.applyInspectionSizing(dialog, ownerNode);

        boolean isDark = ThemeManager.isDark();
        String hostBg = isDark ? "#0d1527" : "#ffffff";
        String hostBorder = isDark ? "rgba(148, 163, 184, 0.22)" : "#cbd5e1";
        String hostShadow = isDark
                ? "dropshadow(gaussian, rgba(0, 0, 0, 0.70), 32, 0.2, 0, 10)"
                : "dropshadow(gaussian, rgba(15, 23, 42, 0.20), 32, 0.15, 0, 10)";

        StackPane host = new StackPane();
        host.getStyleClass().addAll("document-inspection-host", "erp-ui-standard");
        host.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 14; -fx-border-color: %s; -fx-border-radius: 14; -fx-border-width: 1; -fx-effect: %s;",
                hostBg, hostBorder, hostShadow));

        Classy3DLoadingView loadingSkeleton = Classy3DLoadingView.createTableSkeleton(
                "Loading Purchase Invoice — " + currentPurchase.getInvoiceNo(),
                "Synchronizing supplier bill items, tax credits, and payment allocations…");
        host.getChildren().setAll(loadingSkeleton);
        pane.setContent(host);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        dialog.setOnShowing(ev -> {
            Node defaultButtonBar = pane.lookup(".button-bar");
            if (defaultButtonBar != null) {
                defaultButtonBar.setVisible(false);
                defaultButtonBar.setManaged(false);
            }
        });

        UiTaskExecutor.submitLatest("purchase-inspection-" + currentPurchase.getInvoiceNo(),
                () -> {
                    if (currentPurchase.getLines() != null && !currentPurchase.getLines().isEmpty()) {
                        return currentPurchase;
                    }
                    try {
                        Purchase full = purchaseService.getByInvoice(currentPurchase.getInvoiceNo());
                        return full != null ? full : currentPurchase;
                    } catch (Exception ex) {
                        return currentPurchase;
                    }
                },
                resolvedPurchase -> {
                    renderContent(dialog, host, resolvedPurchase, purchaseService, support, onUpdated);
                },
                ex -> {
                    renderContent(dialog, host, currentPurchase, purchaseService, support, onUpdated);
                });

        dialog.showAndWait();
    }

    private static void renderContent(Dialog<?> dialog, StackPane host, Purchase currentPurchase, PurchaseService purchaseService, SupportApiClient support, Runnable onUpdated) {
        // Header Hero
        HBox headerHero = buildHeaderHero(currentPurchase, dialog);

        // Overview Cards
        HBox metricStrip = buildOverviewCards(currentPurchase);

        // TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().addAll("erp-inspection-tabs", "approved-tab-pane");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1: Inward Items
        Tab itemsTab = new Tab("Purchased Items (" + (currentPurchase.getLines() == null ? 0 : currentPurchase.getLines().size()) + ")");
        itemsTab.setGraphic(IconFactory.compactIcon("item", 14));
        itemsTab.setContent(buildItemsTabContent(currentPurchase, purchaseService, itemsTab));

        // Tab 2: Payments & Allocations (lazy-loaded on tab click)
        Tab paymentsTab = new Tab("Payments & Allocations");
        paymentsTab.setGraphic(IconFactory.compactIcon("payment", 14));
        paymentsTab.setContent(Classy3DLoadingView.createTableSkeleton("Loading Payments…", "Synchronizing payment allocations…"));
        java.util.concurrent.atomic.AtomicBoolean paymentsLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);
        paymentsTab.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected && paymentsLoaded.compareAndSet(false, true)) {
                paymentsTab.setContent(buildPaymentsTabContent(currentPurchase, support, dialog, onUpdated));
            }
        });

        // Tab 3: Document Attachments (lazy-loaded on tab click)
        Tab attachmentsTab = new Tab("Attachments");
        attachmentsTab.setGraphic(IconFactory.compactIcon("document", 14));
        attachmentsTab.setContent(Classy3DLoadingView.createTableSkeleton("Loading Attachments…", "Synchronizing document files…"));
        java.util.concurrent.atomic.AtomicBoolean attachmentsLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);
        attachmentsTab.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected && attachmentsLoaded.compareAndSet(false, true)) {
                attachmentsTab.setContent(buildAttachmentsTabContent(currentPurchase, support, dialog));
            }
        });

        // Tab 4: Logistics & Notes (lazy-loaded on tab click)
        Tab logisticsTab = new Tab("Logistics & Terms");
        logisticsTab.setGraphic(IconFactory.compactIcon("delivery", 14));
        logisticsTab.setContent(Classy3DLoadingView.createTableSkeleton("Loading Logistics…", "Synchronizing terms and notes…"));
        java.util.concurrent.atomic.AtomicBoolean logisticsLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);
        logisticsTab.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected && logisticsLoaded.compareAndSet(false, true)) {
                logisticsTab.setContent(buildLogisticsTabContent(currentPurchase));
            }
        });

        tabPane.getTabs().addAll(itemsTab, paymentsTab, attachmentsTab, logisticsTab);

        // Action Dock
        HBox actionDock = buildActionDock(currentPurchase, dialog, purchaseService, support, onUpdated);

        VBox contentBox = new VBox(12, headerHero, metricStrip, tabPane, actionDock);
        contentBox.setPadding(new Insets(16, 20, 16, 20));
        contentBox.getStyleClass().addAll("document-inspection-content", "erp-ui-standard");
        contentBox.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        host.getChildren().setAll(contentBox);
    }

    private static HBox buildHeaderHero(Purchase purchase, Dialog<?> dialog) {
        Label iconLabel = new Label();
        iconLabel.setGraphic(IconFactory.icon("purchase", 32));
        iconLabel.getStyleClass().add("inspection-hero-icon");

        Label docTypeLabel = new Label("PURCHASE INVOICE");
        docTypeLabel.getStyleClass().add("inspection-kicker");

        Label docNoLabel = new Label(purchase.getInvoiceNo());
        docNoLabel.getStyleClass().addAll("inspection-hero-title", "sales-detail-invoice");

        Button btnCopy = new Button();
        btnCopy.setGraphic(IconFactory.compactIcon("copy", 13));
        btnCopy.setTooltip(new Tooltip("Copy invoice number"));
        btnCopy.getStyleClass().addAll("icon-button", "copy-ref-button", "approved-button");
        btnCopy.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(purchase.getInvoiceNo());
            clipboard.setContent(content);
            ToastManager.info(dialog.getDialogPane(), "Copied", purchase.getInvoiceNo() + " copied to clipboard.");
        });

        HBox docNoBox = new HBox(8, docNoLabel, btnCopy);
        docNoBox.setAlignment(Pos.CENTER_LEFT);

        String dateStr = purchase.getInvoiceDate() != null ? BusinessClock.formatDate(purchase.getInvoiceDate()) : "—";
        String timeStr = purchase.getCreatedAt() != null && !purchase.getCreatedAt().isBlank()
                ? BusinessClock.formatTimestamp(purchase.getCreatedAt())
                : "";
        Label metaLabel = new Label("Purchase Date: " + dateStr + (timeStr.isBlank() ? "" : "  •  Created: " + timeStr));
        metaLabel.getStyleClass().add("inspection-meta-label");

        VBox titleBox = new VBox(2, docTypeLabel, docNoBox, metaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status Badges Strip
        HBox statusStrip = new HBox(8);
        statusStrip.setAlignment(Pos.CENTER_RIGHT);

        String docStatus = safe(purchase.getDocumentStatus()).toUpperCase(Locale.ROOT);
        if (docStatus.isBlank()) docStatus = "PENDING APPROVAL";
        Label docStatusPill = createPill(docStatus,
                docStatus.equals("APPROVED") ? "pill-success" : docStatus.contains("REJECT") || docStatus.contains("CANCEL") ? "pill-danger" : "pill-warning",
                docStatus.equals("APPROVED") ? "complete" : docStatus.contains("REJECT") ? "error" : "reminder");

        String payStatus = safe(purchase.getPaymentStatus()).toUpperCase(Locale.ROOT);
        if (payStatus.isBlank()) payStatus = "PENDING";
        Label payStatusPill = createPill(payStatus,
                payStatus.equals("PAID") ? "pill-success" : payStatus.equals("PARTIAL") ? "pill-warning" : "pill-danger",
                payStatus.equals("PAID") ? "payment" : payStatus.equals("PARTIAL") ? "balance" : "reminder");

        statusStrip.getChildren().addAll(docStatusPill, payStatusPill);

        String retStatus = safe(purchase.getReturnStatus()).toUpperCase(Locale.ROOT);
        if (!retStatus.isBlank() && !"N/A".equals(retStatus)) {
            statusStrip.getChildren().add(createPill(retStatus, "pill-warning", "return"));
        }

        Button btnHeroClose = new Button();
        btnHeroClose.setGraphic(SemanticIconManager.compact("close", 14));
        btnHeroClose.getStyleClass().addAll("dse-dialog-close", "icon-button");
        btnHeroClose.setTooltip(new Tooltip("Close"));
        btnHeroClose.setOnAction(e -> dialog.close());
        statusStrip.getChildren().add(btnHeroClose);

        HBox hero = new HBox(12, iconLabel, titleBox, spacer, statusStrip);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("inspection-hero-bar");

        final double[] dragDelta = new double[2];
        hero.setOnMousePressed(mouseEvent -> {
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                Window w = dialog.getDialogPane().getScene().getWindow();
                dragDelta[0] = w.getX() - mouseEvent.getScreenX();
                dragDelta[1] = w.getY() - mouseEvent.getScreenY();
            }
        });
        hero.setOnMouseDragged(mouseEvent -> {
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                Window w = dialog.getDialogPane().getScene().getWindow();
                w.setX(mouseEvent.getScreenX() + dragDelta[0]);
                w.setY(mouseEvent.getScreenY() + dragDelta[1]);
            }
        });

        return hero;
    }

    private static Label createPill(String text, String pillClass, String iconSemantic) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("status-pill", pillClass);
        pill.setGraphic(IconFactory.compactIcon(iconSemantic, 13));
        pill.setGraphicTextGap(5);
        return pill;
    }

    private static HBox buildOverviewCards(Purchase purchase) {
        Party supp = purchase.getSupplier();
        String supplierName = supp != null ? safe(supp.getName()) : "Unknown Supplier";
        String mobile = supp != null && !safe(supp.getPhone()).isBlank() ? supp.getPhone() : "—";
        String gstin = supp != null && !safe(supp.getGstin()).isBlank() ? supp.getGstin() : (!safe(purchase.getBillingGstin()).isBlank() ? purchase.getBillingGstin() : "Unregistered");
        VBox card1 = createSummaryCard("Supplier Details", "supplier",
                List.of(
                        new FieldPair("Party Name", supplierName, "supplier", true),
                        new FieldPair("Contact Mobile", mobile, "phone", false),
                        new FieldPair("Supplier GSTIN", gstin, "tax", false)
                ));

        VBox card2 = createSummaryCard("Purchase Breakdown", "currency",
                List.of(
                        new FieldPair("Taxable Subtotal", money(purchase.getSubtotal()), "currency", false),
                        new FieldPair("GST Amount", money(purchase.getGstAmount()), "tax", false),
                        new FieldPair("Total Purchase", money(purchase.getTotalAmount()), "currency", true)
                ));

        String dueStr = purchase.getDueDate() != null ? BusinessClock.formatDate(purchase.getDueDate()) : "Not set";
        VBox card3 = createSummaryCard("Disbursement Status", "payment",
                List.of(
                        new FieldPair("Total Paid", money(purchase.getPaidAmount()), "complete", false),
                        new FieldPair("Pending Payable", money(purchase.getBalanceAmount()), purchase.getBalanceAmount() > .009 ? "balance" : "complete", true),
                        new FieldPair("Payment Due", dueStr, "calendar", false)
                ));

        String gstType = !safe(purchase.getGstType()).isBlank() ? purchase.getGstType() : "Standard";
        String refNo = !safe(purchase.getReferenceNo()).isBlank() ? purchase.getReferenceNo() : "—";
        String transporter = !safe(purchase.getTransporter()).isBlank() ? purchase.getTransporter() : "Direct";
        VBox card4 = createSummaryCard("Tax & Logistics", "delivery",
                List.of(
                        new FieldPair("Supplier Ref", refNo, "document", false),
                        new FieldPair("GST Treatment", gstType, "tax", false),
                        new FieldPair("Transporter", transporter, "delivery", false)
                ));

        HBox strip = new HBox(12, card1, card2, card3, card4);
        strip.getStyleClass().add("inspection-overview-strip");
        HBox.setHgrow(card1, Priority.ALWAYS);
        HBox.setHgrow(card2, Priority.ALWAYS);
        HBox.setHgrow(card3, Priority.ALWAYS);
        HBox.setHgrow(card4, Priority.ALWAYS);
        return strip;
    }

    private static record FieldPair(String label, String value, String semantic, boolean bold) {}

    private static VBox createSummaryCard(String title, String iconSemantic, List<FieldPair> fields) {
        Label titleLabel = new Label(title);
        titleLabel.setGraphic(IconFactory.compactIcon(iconSemantic, 14));
        titleLabel.setGraphicTextGap(6);
        titleLabel.getStyleClass().add("inspection-card-title");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        int row = 0;
        for (FieldPair fp : fields) {
            Label lbl = new Label(fp.label() + ":");
            lbl.getStyleClass().add("inspection-card-caption");

            Label val = new Label(fp.value());
            val.getStyleClass().add("inspection-card-value");
            if (fp.bold()) val.getStyleClass().add("inspection-value-bold");
            if (fp.semantic().equals("complete")) val.getStyleClass().add("summary-paid");
            else if (fp.semantic().equals("balance")) val.getStyleClass().add("summary-balance");

            grid.add(lbl, 0, row);
            grid.add(val, 1, row);
            row++;
        }

        VBox card = new VBox(8, titleLabel, grid);
        card.getStyleClass().addAll("inspection-card", "erp-detail-drawer-card");
        card.setPadding(new Insets(10, 12, 10, 12));
        return card;
    }

    private static Node buildItemsTabContent(Purchase purchase, PurchaseService purchaseService, Tab itemsTab) {
        TableView<PurchaseLine> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-standard", "inspection-items-table");
        table.setPlaceholder(new Label("No line items associated with this purchase invoice."));

        table.setFixedCellSize(36.0);

        TableColumn<PurchaseLine, Number> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(46);
        colIndex.setMinWidth(40);
        colIndex.setSortable(false);
        colIndex.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(table.getItems().indexOf(v.getValue()) + 1));
        colIndex.setCellFactory(col -> new TableCell<>() {
            { setAlignment(Pos.CENTER); }
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.valueOf(item));
            }
        });
        IconFactory.applyTableHeaderIcon(colIndex, "index");

        TableColumn<PurchaseLine, String> colCode = new TableColumn<>("Item Code");
        colCode.setPrefWidth(125);
        colCode.setMinWidth(95);
        colCode.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().getItemCode())));
        colCode.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().addAll("erp-table-link-cell", "erp-table-value-colour-blue");
                setCursor(javafx.scene.Cursor.HAND);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                    setOnMouseClicked(null);
                    return;
                }
                setText(item);
                setTooltip(new Tooltip("Item: " + item + " (Click to copy code)"));
                setOnMouseClicked(e -> {
                    if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(item);
                        clipboard.setContent(content);
                        ToastManager.info(getTableView(), "Copied", item + " copied to clipboard.");
                    }
                });
            }
        });
        IconFactory.applyTableHeaderIcon(colCode, "item");

        TableColumn<PurchaseLine, String> colDesc = new TableColumn<>("Item Description");
        colDesc.setPrefWidth(220);
        colDesc.setMinWidth(150);
        colDesc.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().getItemDescription())));
        colDesc.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-purple");
                setAlignment(Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colDesc, "document");

        TableColumn<PurchaseLine, String> colHsn = new TableColumn<>("HSN / SAC");
        colHsn.setPrefWidth(95);
        colHsn.setMinWidth(75);
        colHsn.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().getItemHsn())));
        colHsn.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-indigo");
                setAlignment(Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colHsn, "tax");

        TableColumn<PurchaseLine, String> colUnit = new TableColumn<>("Unit");
        colUnit.setPrefWidth(70);
        colUnit.setMinWidth(55);
        colUnit.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().getItemUnit())));
        colUnit.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-teal");
                setAlignment(Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colUnit, "unit");

        TableColumn<PurchaseLine, String> colQty = new TableColumn<>("Quantity");
        colQty.setPrefWidth(85);
        colQty.setMinWidth(70);
        colQty.setCellValueFactory(v -> new ReadOnlyStringWrapper(String.format(Locale.ROOT, "%,.2f", v.getValue().getQuantity())));
        colQty.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-orange");
                setAlignment(Pos.CENTER_RIGHT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colQty, "quantity");

        TableColumn<PurchaseLine, String> colRate = new TableColumn<>("Rate");
        colRate.setPrefWidth(95);
        colRate.setMinWidth(75);
        colRate.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().getRate())));
        colRate.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-green");
                setAlignment(Pos.CENTER_RIGHT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colRate, "rate");

        TableColumn<PurchaseLine, String> colDiscount = new TableColumn<>("Discount");
        colDiscount.setPrefWidth(95);
        colDiscount.setMinWidth(75);
        colDiscount.setCellValueFactory(v -> {
            double dAmt = v.getValue().getDiscountAmount();
            double dPct = v.getValue().getDiscountPercent();
            if (dAmt > 0.009) return new ReadOnlyStringWrapper(money(dAmt) + " (" + String.format(Locale.ROOT, "%.1f%%", dPct) + ")");
            return new ReadOnlyStringWrapper("—");
        });
        colDiscount.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-orange");
                setAlignment(Pos.CENTER_RIGHT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colDiscount, "discount");

        TableColumn<PurchaseLine, String> colGst = new TableColumn<>("GST %");
        colGst.setPrefWidth(80);
        colGst.setMinWidth(65);
        colGst.setCellValueFactory(v -> new ReadOnlyStringWrapper(String.format(Locale.ROOT, "%.1f%%", v.getValue().getGstPercent())));
        colGst.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-orange");
                setAlignment(Pos.CENTER_RIGHT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colGst, "tax");

        TableColumn<PurchaseLine, String> colTax = new TableColumn<>("GST Amount");
        colTax.setPrefWidth(100);
        colTax.setMinWidth(80);
        colTax.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().getGstAmount())));
        colTax.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-teal");
                setAlignment(Pos.CENTER_RIGHT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colTax, "tax-amount");

        TableColumn<PurchaseLine, String> colTotal = new TableColumn<>("Line Total");
        colTotal.setPrefWidth(120);
        colTotal.setMinWidth(100);
        colTotal.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().getTotalAmount())));
        colTotal.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("erp-table-value-colour-green");
                setStyle("-fx-font-weight: bold;");
                setAlignment(Pos.CENTER_RIGHT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        IconFactory.applyTableHeaderIcon(colTotal, "total");

        table.getColumns().addAll(colIndex, colCode, colDesc, colHsn, colUnit, colQty, colRate, colDiscount, colGst, colTax, colTotal);
        DynamicTableLayoutManager.install(table);

        List<PurchaseLine> lines = purchase.getLines() != null ? purchase.getLines() : List.of();
        table.setItems(FXCollections.observableArrayList(lines));
        VBox.setVgrow(table, Priority.ALWAYS);

        double sumQty = lines.stream().mapToDouble(PurchaseLine::getQuantity).sum();
        double sumTax = lines.stream().mapToDouble(PurchaseLine::getGstAmount).sum();
        double sumTotal = lines.stream().mapToDouble(PurchaseLine::getTotalAmount).sum();

        Label lblCount = new Label(lines.size() + (lines.size() == 1 ? " line item" : " line items"));
        lblCount.getStyleClass().add("muted-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblTotals = new Label(String.format(Locale.ROOT, "Total Qty: %,.2f   •   Total Tax: %s   •   Grand Total: %s",
                sumQty, money(sumTax), money(sumTotal)));
        lblTotals.getStyleClass().addAll("inspection-footer-totals", "summary-amount");

        HBox footer = new HBox(12, lblCount, spacer, lblTotals);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 6, 4, 6));

        VBox container = new VBox(8, table, footer);
        Runnable renderCharges = () -> {
            if (purchase.getCharges() != null && !purchase.getCharges().isEmpty()) {
                HBox chargesBox = new HBox(10);
                chargesBox.setAlignment(Pos.CENTER_LEFT);
                chargesBox.setPadding(new Insets(6, 10, 6, 10));
                chargesBox.getStyleClass().add("inspection-charges-box");

                Label chargeTitle = new Label("Additional Charges:");
                chargeTitle.setGraphic(IconFactory.compactIcon("tax", 13));
                chargeTitle.getStyleClass().add("inspection-card-title");
                chargesBox.getChildren().add(chargeTitle);

                for (PurchaseCharge pc : purchase.getCharges()) {
                    Label cPill = new Label(pc.getChargeType() + ": " + money(pc.getAmount()) +
                            (pc.isTaxable() ? " (GST " + String.format(Locale.ROOT, "%.1f%%", pc.getGstPercent()) + ")" : ""));
                    cPill.getStyleClass().addAll("status-pill", "pill-neutral");
                    chargesBox.getChildren().add(cPill);
                }
                container.getChildren().add(1, chargesBox);
            }
        };

        if (purchase.getCharges() != null && !purchase.getCharges().isEmpty()) {
            renderCharges.run();
        }

        // Asynchronously load inward lines if empty, ensuring instant pop-up opening without UI lag
        if (lines.isEmpty() && purchaseService != null && purchase.getInvoiceNo() != null && !purchase.getInvoiceNo().isBlank()) {
            table.setPlaceholder(Classy3DLoadingView.createTableSkeleton("Loading Purchased Items…", "Streaming line items, tax breakdowns, and SKU details for " + purchase.getInvoiceNo()));
            UiTaskExecutor.submitLatest("purchase-inspection-lines-" + purchase.getInvoiceNo(),
                    () -> purchaseService.getByInvoice(purchase.getInvoiceNo()),
                    loaded -> {
                        if (loaded != null && loaded.getLines() != null && !loaded.getLines().isEmpty()) {
                            purchase.setLines(loaded.getLines());
                            if (loaded.getCharges() != null) purchase.setCharges(loaded.getCharges());
                            table.getItems().setAll(loaded.getLines());
                            double q = loaded.getLines().stream().mapToDouble(PurchaseLine::getQuantity).sum();
                            double t = loaded.getLines().stream().mapToDouble(PurchaseLine::getGstAmount).sum();
                            double m = loaded.getLines().stream().mapToDouble(PurchaseLine::getTotalAmount).sum();
                            lblCount.setText(loaded.getLines().size() + (loaded.getLines().size() == 1 ? " line item" : " line items"));
                            lblTotals.setText(String.format(Locale.ROOT, "Total Qty: %,.2f   •   Total Tax: %s   •   Grand Total: %s",
                                    q, money(t), money(m)));
                            if (itemsTab != null) {
                                itemsTab.setText("Purchased Items (" + loaded.getLines().size() + ")");
                            }
                            if (container.getChildren().size() <= 2) {
                                renderCharges.run();
                            }
                        } else {
                            table.setPlaceholder(new Label("No line items associated with this purchase invoice."));
                            lblCount.setText("0 line items");
                        }
                    },
                    err -> {
                        table.setPlaceholder(new Label("Unable to load purchased items: " + err.getMessage()));
                        lblCount.setText("0 line items");
                    });
        }

        container.setPadding(new Insets(8, 0, 0, 0));
        return container;
    }

    private static Node buildPaymentsTabContent(Purchase purchase, SupportApiClient support, Dialog<?> dialog, Runnable onUpdated) {
        TableView<SupportApiClient.PaymentRow> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-standard", "invoice-payment-table");
        table.setPlaceholder(Classy3DLoadingView.createTableSkeleton("Loading Payment History…", "Retrieving payment proofs, receipts, and allocations for " + purchase.getInvoiceNo()));

        TableColumn<SupportApiClient.PaymentRow, String> colDate = new TableColumn<>("Payment Date");
        colDate.setPrefWidth(110);
        colDate.setCellValueFactory(v -> new ReadOnlyStringWrapper(BusinessClock.formatDate(v.getValue().date())));
        IconFactory.applyTableHeaderIcon(colDate, "calendar");

        TableColumn<SupportApiClient.PaymentRow, String> colRef = new TableColumn<>("Reference No.");
        colRef.setPrefWidth(140);
        colRef.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().reference())));
        IconFactory.applyTableHeaderIcon(colRef, "document");

        TableColumn<SupportApiClient.PaymentRow, String> colParty = new TableColumn<>("Paid To");
        colParty.setPrefWidth(200);
        colParty.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().receivedFrom())));
        IconFactory.applyTableHeaderIcon(colParty, "supplier");

        TableColumn<SupportApiClient.PaymentRow, String> colMode = new TableColumn<>("Payment Mode");
        colMode.setPrefWidth(120);
        colMode.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().mode())));
        IconFactory.applyTableHeaderIcon(colMode, "payment");

        table.setFixedCellSize(36.0);

        TableColumn<SupportApiClient.PaymentRow, String> colAmount = new TableColumn<>("Amount");
        colAmount.setPrefWidth(130);
        colAmount.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().amount())));
        colAmount.setCellFactory(c -> SemanticTableCells.moneyString("green"));
        IconFactory.applyTableHeaderIcon(colAmount, "currency");

        TableColumn<SupportApiClient.PaymentRow, String> colNotes = new TableColumn<>("Notes / Remarks");
        colNotes.setPrefWidth(220);
        colNotes.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().notes())));
        IconFactory.applyTableHeaderIcon(colNotes, "notes");

        table.getColumns().addAll(colDate, colRef, colParty, colMode, colAmount, colNotes);
        DynamicTableLayoutManager.install(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label lblHistorySummary = new Label("Loading disbursement history…");
        lblHistorySummary.getStyleClass().add("muted-label");

        boolean isPaid = purchase.getBalanceAmount() <= 0.009;
        Button btnRecordPayment = new Button("Record Payment");
        btnRecordPayment.setGraphic(IconFactory.compactIcon("payment", 14));
        btnRecordPayment.getStyleClass().addAll("approved-button", "approved-primary-button");
        btnRecordPayment.setDisable(isPaid);
        if (isPaid) {
            btnRecordPayment.setTooltip(new Tooltip("Bill is already fully paid."));
        } else {
            btnRecordPayment.setTooltip(new Tooltip("Record payment for " + purchase.getInvoiceNo() + " (Pending: " + money(purchase.getBalanceAmount()) + ")"));
        }
        btnRecordPayment.setOnAction(e -> {
            dialog.close();
            PurchaseScreenContext.select(purchase.getInvoiceNo());
            NavigationManager.navigateOrReport("/fxml/pages/PurchasePayment.fxml");
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(12, lblHistorySummary, spacer, btnRecordPayment);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 6, 4, 6));

        VBox container = new VBox(8, table, footer);
        container.setPadding(new Insets(8, 0, 0, 0));

        UiTaskExecutor.submitLatest("purchase-inspection-payments-" + purchase.getId(),
                () -> support.payments("PURCHASE", purchase.getId()),
                records -> {
                    table.getItems().setAll(records);
                    if (records.isEmpty()) {
                        table.setPlaceholder(new Label("No payments recorded yet for this purchase invoice."));
                    }
                    double recordedSum = records.stream().mapToDouble(SupportApiClient.PaymentRow::amount).sum();
                    lblHistorySummary.setText(String.format(Locale.ROOT,
                            "%d payment(s) recorded • Total Disbursed: %s • Remaining Pending: %s",
                            records.size(), money(recordedSum), money(purchase.getBalanceAmount())));
                },
                err -> lblHistorySummary.setText("Unable to load payment history: " + err.getMessage()));

        return container;
    }

    public record InspectionAttachment(
            String category,
            String badgeStyle,
            String fileName,
            String details,
            String iconSemantic,
            AttachmentFetcher fetcher
    ) {
        @FunctionalInterface
        public interface AttachmentFetcher {
            SupportApiClient.DownloadedAttachment fetch() throws Exception;
        }
    }

    private static Node buildAttachmentsTabContent(Purchase purchase, SupportApiClient support, Dialog<?> dialog) {
        ListView<InspectionAttachment> listView = new ListView<>();
        listView.getStyleClass().addAll("approved-table", "erp-table-standard", "inspection-attachments-list");
        listView.setPlaceholder(Classy3DLoadingView.createTableSkeleton("Loading Document Attachments…", "Fetching invoice PDFs, payment receipts, and delivery documents for " + purchase.getInvoiceNo()));
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(InspectionAttachment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label icon = new Label();
                icon.setGraphic(IconFactory.compactIcon(item.iconSemantic(), 18));

                Label pill = new Label(item.category());
                pill.getStyleClass().addAll("status-pill", item.badgeStyle());

                Label name = new Label(item.fileName());
                name.getStyleClass().add("inspection-value-bold");

                HBox titleLine = new HBox(8, name, pill);
                titleLine.setAlignment(Pos.CENTER_LEFT);

                Label meta = new Label(item.details());
                meta.getStyleClass().add("muted-label");

                VBox details = new VBox(2, titleLine, meta);

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button btnOpen = new Button("Open File");
                btnOpen.setGraphic(IconFactory.compactIcon("view", 13));
                btnOpen.getStyleClass().addAll("approved-button", "approved-secondary-button");
                btnOpen.setTooltip(new Tooltip("Open and preview file in default application"));
                btnOpen.setOnAction(e -> {
                    UiTaskExecutor.submitAction("view-purchase-att-" + item.fileName().hashCode(), () -> {
                        var downloaded = item.fetcher().fetch();
                        return AttachmentPreviewSupport.materializeRequired(downloaded, item.fileName());
                    }, file -> {
                        try {
                            if (file != null && java.awt.Desktop.isDesktopSupported()) {
                                java.awt.Desktop.getDesktop().open(file.toFile());
                            }
                        } catch (Exception ex) {
                            ToastManager.error(dialog.getDialogPane(), "Failed to open file", ex.getMessage());
                        }
                    }, ex -> ToastManager.error(dialog.getDialogPane(), "Failed to open attachment", ex.getMessage()));
                });

                Button btnDownload = new Button("Download");
                btnDownload.setGraphic(IconFactory.compactIcon("download", 13));
                btnDownload.getStyleClass().addAll("approved-button", "approved-secondary-button");
                btnDownload.setTooltip(new Tooltip("Save attachment to disk"));
                btnDownload.setOnAction(e -> {
                    UiTaskExecutor.submitAction("download-purchase-att-" + item.fileName().hashCode(), () -> {
                        return item.fetcher().fetch();
                    }, downloaded -> {
                        if (downloaded == null || downloaded.data() == null) {
                            ToastManager.error(dialog.getDialogPane(), "Download error", "Attachment file content is empty.");
                            return;
                        }
                        FileChooser chooser = new FileChooser();
                        chooser.setTitle("Save Attachment");
                        String defaultName = (downloaded.fileName() != null && !downloaded.fileName().isBlank()) ? downloaded.fileName() : item.fileName();
                        chooser.setInitialFileName(defaultName);
                        File target = chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
                        if (target != null) {
                            try {
                                Files.write(target.toPath(), downloaded.data());
                                ToastManager.success(dialog.getDialogPane(), "Download complete", "Saved to " + target.getName());
                            } catch (Exception writeEx) {
                                ToastManager.error(dialog.getDialogPane(), "Save failed", writeEx.getMessage());
                            }
                        }
                    }, ex -> ToastManager.error(dialog.getDialogPane(), "Failed to download", ex.getMessage()));
                });

                HBox actions = new HBox(8, btnOpen, btnDownload);
                actions.setAlignment(Pos.CENTER_RIGHT);

                HBox row = new HBox(12, icon, details, sp, actions);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        Label statusLabel = new Label("Loading attachments and payment proofs…");
        statusLabel.getStyleClass().add("muted-label");

        VBox container = new VBox(8, listView, statusLabel);
        container.setPadding(new Insets(8, 0, 0, 0));

        UiTaskExecutor.submitLatest("purchase-inspection-attachments-" + purchase.getId(),
                () -> {
                    List<InspectionAttachment> items = new ArrayList<>();
                    Set<String> seenNames = new HashSet<>();

                    // 1. Document attachments from server table
                    try {
                        List<SupportApiClient.AttachmentMeta> docMetas = support.documentAttachments("PURCHASE", purchase.getId());
                        for (var m : docMetas) {
                            seenNames.add(m.fileName().toLowerCase(Locale.ROOT));
                            items.add(new InspectionAttachment(
                                    "Order Attachment",
                                    "pill-info",
                                    m.fileName(),
                                    (m.createdBy() != null && !m.createdBy().isBlank() ? "Uploaded by " + m.createdBy() + "  •  " : "") +
                                            (m.createdAt() != null ? BusinessClock.formatTimestamp(m.createdAt()) : ""),
                                    "document",
                                    () -> support.documentAttachment("PURCHASE", purchase.getId(), m.id())
                            ));
                        }
                    } catch (Exception ignored) {}

                    // 2. Primary document attachment if available
                    String primaryPath = safe(purchase.getAttachmentPath());
                    if (!primaryPath.isBlank()) {
                        String fn = Path.of(primaryPath.replace("\\", "/")).getFileName().toString();
                        if (!seenNames.contains(fn.toLowerCase(Locale.ROOT))) {
                            seenNames.add(fn.toLowerCase(Locale.ROOT));
                            items.add(new InspectionAttachment(
                                    "Order Attachment",
                                    "pill-info",
                                    fn,
                                    "Primary purchase document attachment",
                                    "document",
                                    () -> {
                                        try {
                                            return support.documentAttachment("PURCHASE", purchase.getId());
                                        } catch (Exception ex) {
                                            Path p = Path.of(primaryPath);
                                            if (Files.isRegularFile(p)) {
                                                return new SupportApiClient.DownloadedAttachment(p.getFileName().toString(), Files.readAllBytes(p));
                                            }
                                            throw ex;
                                        }
                                    }
                            ));
                        }
                    } else if (items.isEmpty()) {
                        try {
                            var primary = support.documentAttachment("PURCHASE", purchase.getId());
                            if (primary != null && primary.data() != null && primary.data().length > 0) {
                                String fn = (primary.fileName() != null && !primary.fileName().isBlank()) ? primary.fileName() : "purchase-attachment.pdf";
                                seenNames.add(fn.toLowerCase(Locale.ROOT));
                                items.add(new InspectionAttachment(
                                        "Order Attachment",
                                        "pill-info",
                                        fn,
                                        "Primary purchase document attachment",
                                        "document",
                                        () -> primary
                                ));
                            }
                        } catch (Exception ignored) {}
                    }

                    // 3. Payment attachments (Payment proofs)
                    try {
                        List<SupportApiClient.PaymentRow> payments = support.payments("PURCHASE", purchase.getId());
                        for (var p : payments) {
                            String proofRef = safe(p.attachment());
                            if (!proofRef.isBlank()) {
                                String fn = "Payment-Proof-" + (!safe(p.reference()).isBlank() ? p.reference() : String.valueOf(p.id()));
                                try {
                                    String rawFn = Path.of(proofRef.replace("\\", "/")).getFileName().toString();
                                    if (!rawFn.isBlank()) fn = rawFn;
                                } catch (Exception ignored) {}

                                String detailsText = "Payment Ref: " + (!safe(p.reference()).isBlank() ? p.reference() : ("#" + p.id())) +
                                        "  •  " + money(p.amount()) + " (" + safe(p.mode()) + ")" +
                                        (!safe(p.date()).isBlank() ? "  •  " + BusinessClock.formatDate(p.date()) : "");

                                final int paymentId = p.id();
                                items.add(new InspectionAttachment(
                                        "Payment Proof",
                                        "pill-success",
                                        fn,
                                        detailsText,
                                        "payment",
                                        () -> support.paymentAttachment(paymentId)
                                ));
                            }
                        }
                    } catch (Exception ignored) {}

                    return items;
                },
                items -> {
                    listView.getItems().setAll(items);
                    if (items.isEmpty()) {
                        listView.setPlaceholder(new Label("No order attachments or payment proofs have been uploaded for this purchase invoice."));
                        statusLabel.setText("No files attached.");
                    } else {
                        long orderCount = items.stream().filter(i -> "Order Attachment".equals(i.category())).count();
                        long paymentCount = items.stream().filter(i -> "Payment Proof".equals(i.category())).count();
                        statusLabel.setText(String.format(Locale.ROOT, "%d attachment(s) available (%d order attachment%s, %d payment proof%s)",
                                items.size(), orderCount, orderCount == 1 ? "" : "s", paymentCount, paymentCount == 1 ? "" : "s"));
                    }
                },
                err -> statusLabel.setText("Failed to load attachments: " + err.getMessage()));

        return container;
    }

    private static Node buildLogisticsTabContent(Purchase purchase) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(14, 16, 14, 16));
        grid.getStyleClass().addAll("inspection-detail-grid", "erp-detail-drawer-card");

        int r = 0;
        addDetailRow(grid, r++, "Supplier Billing Address", !safe(purchase.getBillingAddress()).isBlank() ? purchase.getBillingAddress() : "Not set", "location");
        addDetailRow(grid, r++, "Delivery / Receiving Address", !safe(purchase.getDeliveryAddress()).isBlank() ? purchase.getDeliveryAddress() : "Same as Billing", "location");
        addDetailRow(grid, r++, "Transporter Name", !safe(purchase.getTransporter()).isBlank() ? purchase.getTransporter() : "Not Applicable", "delivery");
        addDetailRow(grid, r++, "Vehicle Number", !safe(purchase.getVehicleNumber()).isBlank() ? purchase.getVehicleNumber() : "Not Applicable", "delivery");
        addDetailRow(grid, r++, "Supplier Reference No.", !safe(purchase.getReferenceNo()).isBlank() ? purchase.getReferenceNo() : "—", "document");
        addDetailRow(grid, r++, "Payment Terms", !safe(purchase.getPaymentTerms()).isBlank() ? purchase.getPaymentTerms() : "—", "payment");
        addDetailRow(grid, r++, "Contact Person", !safe(purchase.getContactPerson()).isBlank() ? purchase.getContactPerson() : "—", "user");
        addDetailRow(grid, r++, "Contact Mobile", !safe(purchase.getSupplier() != null ? purchase.getSupplier().getPhone() : "").isBlank() ? purchase.getSupplier().getPhone() : "—", "phone");
        addDetailRow(grid, r++, "Remarks / Notes", !safe(purchase.getNotes()).isBlank() ? purchase.getNotes() : "No notes recorded.", "notes");

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("erp-inner-scroll", "inspection-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private static void addDetailRow(GridPane grid, int row, String caption, String value, String semantic) {
        Label capLbl = new Label(caption);
        capLbl.setGraphic(IconFactory.compactIcon(semantic, 14));
        capLbl.setGraphicTextGap(6);
        capLbl.getStyleClass().addAll("inspection-card-caption", "erp-drawer-caption");
        capLbl.setMinWidth(180);

        Label valLbl = new Label(value);
        valLbl.getStyleClass().addAll("inspection-card-value", "erp-drawer-value");
        valLbl.setWrapText(true);

        grid.add(capLbl, 0, row);
        grid.add(valLbl, 1, row);
    }

    private static HBox buildActionDock(Purchase purchase, Dialog<?> dialog, PurchaseService service, SupportApiClient support, Runnable onUpdated) {
        HBox dock = new HBox(8);
        dock.setAlignment(Pos.CENTER_LEFT);
        dock.getStyleClass().add("inspection-action-dock");
        String status = safe(purchase.getDocumentStatus()).trim().toUpperCase(Locale.ROOT);
        boolean inactive = "CANCELLED".equals(status) || "DELETED".equals(status);
        boolean financiallyLocked = isFinanciallyLocked(purchase);
        boolean approvalLocked = isApprovalLocked(purchase);

        // 1. Edit Purchase
        Button btnEdit = new Button("Edit Purchase");
        btnEdit.setGraphic(IconFactory.compactIcon("edit", 14));
        btnEdit.getStyleClass().addAll("approved-button", "approved-amber-button");
        boolean canEdit = !inactive && !financiallyLocked;
        btnEdit.setDisable(!canEdit);
        if (!canEdit) {
            btnEdit.setTooltip(new Tooltip(financiallyLocked ? "Financially locked (Payments already recorded). Use Purchase Return instead." : "Cancelled or deleted purchase bills cannot be edited."));
        } else {
            btnEdit.setTooltip(new Tooltip("Edit purchase bill #" + purchase.getInvoiceNo()));
        }
        btnEdit.setOnAction(e -> {
            dialog.close();
            PurchaseScreenContext.select(purchase.getInvoiceNo());
            NavigationManager.navigateOrReport("/fxml/pages/Purchase.fxml");
        });

        // 2. Duplicate Purchase
        Button btnDuplicate = new Button("Duplicate Purchase");
        btnDuplicate.setGraphic(IconFactory.compactIcon("copy", 14));
        btnDuplicate.getStyleClass().addAll("approved-button", "approved-indigo-button");
        btnDuplicate.setOnAction(e -> {
            dialog.close();
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(ResourceLocator.require("/fxml/pages/Purchase.fxml"));
                javafx.scene.Parent root = loader.load();
                ProfessionalUiEnhancer.enhance(root);
                org.example.controller.PurchaseController controller = loader.getController();
                Purchase full = service.getByInvoice(purchase.getInvoiceNo());
                if (full != null) {
                    controller.loadPurchase(full);
                    controller.prepareDuplicate();
                    NavigationManager.getInstance().showPreparedPage("/fxml/pages/Purchase.fxml", root, controller);
                }
            } catch (Exception ex) {
                AppDialogService.error(dialog.getDialogPane(), "Purchase", "Duplicate Purchase", "Failed to duplicate purchase bill: " + ex.getMessage());
            }
        });

        // 3. Purchase Order PDF
        Button btnPrint = new Button("Purchase Order PDF");
        btnPrint.setGraphic(IconFactory.compactIcon("print", 14));
        btnPrint.getStyleClass().addAll("approved-button", "approved-purple-button");
        btnPrint.setOnAction(e -> {
            UiTaskExecutor.submitAction("purchase-pdf-" + purchase.getId(), () -> {
                Purchase full = service.getByInvoice(purchase.getInvoiceNo());
                return ManagedInvoicePdfService.purchase(full != null ? full : purchase);
            }, path -> {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Failed to open PDF", ex.getMessage());
                }
            }, err -> ToastManager.error(dialog.getDialogPane(), "PDF generation failed", err.getMessage()));
        });

        // 4. Inward Barcode
        Button btnBarcode = new Button("Barcode");
        btnBarcode.setGraphic(IconFactory.compactIcon("barcode", 14));
        btnBarcode.getStyleClass().addAll("approved-button", "approved-cyan-button");
        btnBarcode.setOnAction(e -> OrderDispatchBarcodeDialog.showForPurchase(dialog.getDialogPane(), purchase));

        // 5. Excel
        Button btnExcel = new Button("Excel");
        btnExcel.setGraphic(IconFactory.compactIcon("excel", 14));
        btnExcel.getStyleClass().addAll("approved-button", "approved-emerald-button");
        btnExcel.setOnAction(e -> {
            try {
                Purchase full = service.getByInvoice(purchase.getInvoiceNo());
                Path path = ExcelOutputService.purchase(full != null ? full : purchase);
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
            } catch (Exception ex) {
                ToastManager.error(dialog.getDialogPane(), "Failed to open Excel", ex.getMessage());
            }
        });

        // 6. Email
        Button btnEmail = new Button("Email");
        btnEmail.setGraphic(IconFactory.compactIcon("email", 14));
        btnEmail.getStyleClass().addAll("approved-button", "approved-sky-button");
        btnEmail.setOnAction(e -> {
            UiTaskExecutor.submitAction("purchase-prepare-email-" + purchase.getId(), () -> {
                Purchase full = service.getByInvoice(purchase.getInvoiceNo());
                if (full == null) throw new IllegalStateException("Purchase order " + purchase.getInvoiceNo() + " was not found. Refresh the register and try again.");
                Path pdf = ManagedInvoicePdfService.purchase(full);
                MessageTemplateService.FormattedMessage msg = MessageTemplateService.formatPurchaseEmail(full);
                String sigImg = MessageTemplateService.getSignatureImagePath();
                Path sigPath = (sigImg != null && !sigImg.isBlank()) ? Path.of(sigImg) : null;
                String target = full.getSupplier() == null ? "" : safe(full.getSupplier().getEmail()).trim();
                return new EmailTransmissionDialog.EmailDraft(
                        "Transmit Purchase Order via Email",
                        "Direct SMTP delivery to supplier billing contact",
                        "Purchase Order",
                        full.getInvoiceNo(),
                        target,
                        msg.subject(),
                        msg.body(),
                        pdf,
                        sigPath
                );
            }, draft -> {
                Optional<EmailTransmissionDialog.TransmissionResult> opt = EmailTransmissionDialog.show(dialog.getDialogPane(), draft);
                if (opt.isEmpty()) return;
                EmailTransmissionDialog.TransmissionResult result = opt.get();
                LiveOperationDialog.run(dialog.getDialogPane(), "Sending Purchase Order Email", "email", "Connecting to SMTP server...", reporter -> {
                    reporter.stage(1, 3, "Connecting to SMTP and transmitting email to " + result.recipientEmail() + "...");
                    EmailService.send(result.recipientEmail(), result.subject(), result.body(), result.attachmentPdf(), result.signatureImage());
                    reporter.stage(2, 3, "Recording communication status in audit register...");
                    service.markEmailSent(purchase.getId());
                    try {
                        String actor = SessionService.current() != null ? SessionService.current().getUsername() : "Admin";
                        support.communication(new SupportApiClient.CommunicationRequest(
                                "PURCHASE", purchase.getId(), "EMAIL", result.recipientEmail(), result.subject(), "SENT", null, actor
                        ));
                    } catch (Exception ignored) {}
                    reporter.stage(3, 3, "Email delivery complete.");
                    reporter.log("Delivered purchase order #" + purchase.getInvoiceNo() + " to " + result.recipientEmail());
                }, () -> {
                    ToastManager.success(dialog.getDialogPane(), "Email sent", "Purchase order #" + purchase.getInvoiceNo() + " emailed successfully to " + result.recipientEmail() + ".");
                    if (onUpdated != null) onUpdated.run();
                }, failure -> {
                    try {
                        String actor = SessionService.current() != null ? SessionService.current().getUsername() : "Admin";
                        support.communication(new SupportApiClient.CommunicationRequest(
                                "PURCHASE", purchase.getId(), "EMAIL", result.recipientEmail(), result.subject(), "FAILED", failure.getMessage(), actor
                        ));
                    } catch (Exception ignored) {}
                    ToastManager.error(dialog.getDialogPane(), "Email delivery failed", failure.getMessage());
                });
            }, failure -> ToastManager.error(dialog.getDialogPane(), "Failed to prepare email draft", failure.getMessage()));
        });

        // 7. Record Payment (Enabled if pending, disabled if paid or approval locked)
        boolean isPaid = purchase.getBalanceAmount() <= 0.009;
        boolean canPay = !isPaid && !inactive && !approvalLocked;
        Button btnRecordPayment = new Button("Record Payment");
        btnRecordPayment.setGraphic(IconFactory.compactIcon("payment", 14));
        btnRecordPayment.getStyleClass().addAll("approved-button", canPay ? "approved-violet-button" : "approved-slate-button");
        btnRecordPayment.setDisable(!canPay);
        if (isPaid) {
            btnRecordPayment.setTooltip(new Tooltip("Bill is already fully paid (No pending balance)."));
        } else if (approvalLocked) {
            btnRecordPayment.setTooltip(new Tooltip("Payments cannot be recorded on unapproved or rejected purchase bills."));
        } else {
            btnRecordPayment.setTooltip(new Tooltip("Record payment for " + purchase.getInvoiceNo() + " (Pending: " + money(purchase.getBalanceAmount()) + ")"));
        }
        btnRecordPayment.setOnAction(e -> {
            dialog.close();
            PurchaseScreenContext.select(purchase.getInvoiceNo());
            NavigationManager.navigateOrReport("/fxml/pages/PurchasePayment.fxml");
        });

        // 8. Create Purchase Return or View Existing Return
        String retStatus = safe(purchase.getReturnStatus()).toUpperCase(Locale.ROOT);
        boolean isAlreadyReturned = "PARTIALLY RETURNED".equals(retStatus) || "FULLY RETURNED".equals(retStatus) || "RETURNED".equals(retStatus);
        Button btnReturn = new Button();
        if (isAlreadyReturned) {
            btnReturn.setText("View Return");
            btnReturn.setGraphic(IconFactory.compactIcon("view", 14));
            btnReturn.getStyleClass().addAll("approved-button", "approved-secondary-button");
            btnReturn.setTooltip(new Tooltip("View Purchase Return for " + purchase.getInvoiceNo() + " (" + retStatus + ")"));
            btnReturn.setOnAction(e -> {
                dialog.close();
                LinkedRecordContext.open("PURCHASE_RETURN", null, purchase.getInvoiceNo(), "VIEW", "Purchase Bill " + purchase.getInvoiceNo());
                NavigationManager.navigateOrReport("/fxml/pages/PurchaseReturns.fxml");
            });
        } else {
            boolean returnEligible = isReturnEligible(purchase);
            btnReturn.setText("Create Purchase Return");
            btnReturn.setGraphic(IconFactory.compactIcon("return", 14));
            btnReturn.getStyleClass().addAll("approved-button", "approved-secondary-button");
            btnReturn.setDisable(!returnEligible);
            if (!returnEligible) {
                btnReturn.setTooltip(new Tooltip("Purchase Returns require an approved, settled bill."));
            } else {
                btnReturn.setTooltip(new Tooltip("Create Purchase Return for " + purchase.getInvoiceNo()));
            }
            btnReturn.setOnAction(e -> {
                dialog.close();
                Purchase full = (service != null) ? service.getByInvoice(purchase.getInvoiceNo()) : null;
                if (full == null) full = purchase;
                List<ReturnEditorService.InvoiceItem> items = (full.getLines() != null ? full.getLines() : List.<PurchaseLine>of()).stream()
                        .map(line -> new ReturnEditorService.InvoiceItem(
                                line.getItemCode(),
                                line.getItemDescription(),
                                line.getQuantity(),
                                line.getRate(),
                                line.getDiscountPercent(),
                                line.getGstPercent()))
                        .toList();
                Window ownerWindow = dialog.getOwner() != null ? dialog.getOwner() : (dialog.getDialogPane() != null && dialog.getDialogPane().getScene() != null ? dialog.getDialogPane().getScene().getWindow() : null);
                ReturnEditorService.show(ownerWindow, ReturnEditorService.Type.PURCHASE,
                        purchase.getInvoiceNo(),
                        purchase.getSupplier() != null ? purchase.getSupplier().getName() : "Supplier",
                        purchase.getSupplier() != null ? purchase.getSupplier().getId() : 0,
                        items).ifPresent(no -> {
                            if (onUpdated != null) onUpdated.run();
                            ToastManager.success(dialog.getDialogPane(), "Return Created", "Purchase return created: " + no);
                        });
            });
        }

        // 9. Audit Trail
        Button btnAudit = new Button("Audit Trail");
        btnAudit.setGraphic(IconFactory.compactIcon("history", 14));
        btnAudit.getStyleClass().addAll("approved-button", "approved-slate-button");
        btnAudit.setOnAction(e -> ActivityTimelineDialog.show(dialog.getDialogPane(), "PURCHASE", purchase.getId(), purchase.getInvoiceNo()));

        boolean isPendingApproval = "PENDING APPROVAL".equalsIgnoreCase(safe(purchase.getDocumentStatus()).trim());
        boolean isAdmin = SessionService.isAdmin();

        dock.getChildren().addAll(btnEdit, btnDuplicate, btnPrint, btnBarcode, btnExcel, btnEmail, btnRecordPayment, btnReturn, btnAudit);

        if (isPendingApproval && isAdmin) {
            Button btnApprove = new Button("Approve Purchase");
            btnApprove.setGraphic(IconFactory.compactIcon("complete", 14));
            btnApprove.getStyleClass().addAll("approved-button", "approved-success-button");
            btnApprove.setOnAction(e -> {
                try {
                    service.approve(purchase.getInvoiceNo());
                    NotificationService.add("Purchase " + purchase.getInvoiceNo() + " approved.");
                    ToastManager.success(dialog.getDialogPane(), "Approved", "Purchase order approved successfully.");
                    if (onUpdated != null) onUpdated.run();
                    dialog.close();
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Approval failed", ex.getMessage());
                }
            });

            Button btnReject = new Button("Reject Purchase");
            btnReject.setGraphic(IconFactory.compactIcon("cancel", 14));
            btnReject.getStyleClass().addAll("approved-button", "approved-danger-button");
            btnReject.setOnAction(e -> {
                OwnedTextInputDialog td = new OwnedTextInputDialog();
                td.setTitle("Reject Purchase");
                td.setHeaderText("Reject " + purchase.getInvoiceNo());
                td.setContentText("Reason:");
                td.showAndWait().map(String::trim).filter(r -> !r.isBlank()).ifPresent(reason -> {
                    try {
                        service.reject(purchase.getInvoiceNo(), reason);
                        NotificationService.add("Purchase " + purchase.getInvoiceNo() + " rejected.");
                        if (onUpdated != null) onUpdated.run();
                        dialog.close();
                    } catch (Exception ex) {
                        ToastManager.error(dialog.getDialogPane(), "Rejection failed", ex.getMessage());
                    }
                });
            });

            dock.getChildren().addAll(btnApprove, btnReject);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button("Close");
        btnClose.setGraphic(IconFactory.compactIcon("cancel", 14));
        btnClose.getStyleClass().addAll("approved-button", "approved-danger-ghost-button");
        btnClose.setOnAction(e -> dialog.close());

        dock.getChildren().addAll(spacer, btnClose);
        return dock;
    }

    private static String money(double value) {
        return NumberFormat.getCurrencyInstance(Locale.of("en", "IN")).format(value).replace("₹", "₹ ");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.ROOT, "%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    private static boolean isFinanciallyLocked(Purchase p) {
        if (p == null) return false;
        String payment = safe(p.getBasePaymentStatus()).toUpperCase(Locale.ROOT);
        return p.getPaidAmount() > 0.009 || payment.contains("PAID") || payment.contains("SETTLED") || payment.contains("PARTIAL");
    }

    private static boolean isFullyPaid(Purchase p) {
        if (p == null) return false;
        String payment = safe(p.getBasePaymentStatus()).trim().toUpperCase(Locale.ROOT);
        return payment.equals("PAID") || payment.equals("SETTLED") || p.getPaidAmount() + 0.009 >= p.getTotalAmount();
    }

    private static boolean isApprovalLocked(Purchase p) {
        if (p == null) return false;
        String status = safe(p.getDocumentStatus()).trim().toUpperCase(Locale.ROOT);
        return status.equals("PENDING APPROVAL") || status.equals("REJECTED");
    }

    private static boolean isReturnEligible(Purchase p) {
        if (p == null || !isFullyPaid(p)) return false;
        String document = safe(p.getDocumentStatus()).trim().toUpperCase(Locale.ROOT);
        String current = safe(p.getPaymentStatus()).trim().toUpperCase(Locale.ROOT);
        return "APPROVED".equals(document) && !Set.of("RETURN APPROVAL PENDING", "RETURN PENDING", "RETURN PARTIAL").contains(current);
    }
}
