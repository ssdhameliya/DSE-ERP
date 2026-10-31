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
import javafx.stage.Window;
import org.example.api.quotation.QuotationApiClient;
import org.example.api.support.SupportApiClient;
import org.example.controller.EmailTransmissionDialog;
import org.example.controller.LinkedRecordContext;
import org.example.controller.QuotationEditorContext;
import org.example.navigation.NavigationManager;
import org.example.service.EmailService;
import org.example.service.ManagedInvoicePdfService;
import org.example.service.MessageTemplateService;
import org.example.service.SessionService;
import org.example.theme.ThemeManager;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Centered, spacious Document Inspection Window Pop-up for Quotations / Estimates.
 * Replaces the cramped inline side drawer with a rich, multi-tab inspection interface.
 */
public final class QuotationDocumentInspectionDialog {

    private QuotationDocumentInspectionDialog() {}

    public static void show(Node ownerNode, QuotationApiClient.QuoteDto quote, QuotationApiClient quotationApi, SupportApiClient supportApi, Runnable onUpdated) {
        if (quote == null) return;

        final QuotationApiClient api = (quotationApi != null) ? quotationApi : new QuotationApiClient();
        final SupportApiClient support = (supportApi != null) ? supportApi : new SupportApiClient();

        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(ownerNode);
        dialog.setTitle("Quotation Inspection — " + quote.no());
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("erp-document-inspection-dialog", "quotation-inspection-dialog", "approved-ui");
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

        pane.setContent(host);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        dialog.setOnShowing(ev -> {
            Node defaultButtonBar = pane.lookup(".button-bar");
            if (defaultButtonBar != null) {
                defaultButtonBar.setVisible(false);
                defaultButtonBar.setManaged(false);
            }
        });

        // Instant Shell Mount (0ms): render Header Hero, Metric Cards, Tabs, and Action Dock synchronously
        renderContent(dialog, host, quote, api, support, onUpdated);

        dialog.showAndWait();
    }

    private static void renderContent(Dialog<?> dialog, StackPane host, QuotationApiClient.QuoteDto quote, QuotationApiClient api, SupportApiClient support, Runnable onUpdated) {
        // Header Hero
        HBox headerHero = buildHeaderHero(quote, dialog);

        // Overview Cards
        HBox metricStrip = buildOverviewCards(quote);

        // TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().addAll("erp-inspection-tabs", "approved-tab-pane");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1: Quoted Items
        Tab itemsTab = new Tab("Quoted Items");
        itemsTab.setGraphic(IconFactory.compactIcon("item", 14));
        itemsTab.setContent(buildItemsTabContent(quote, api));

        // Tab 2: Follow-up & Conversion (lazy-loaded on tab click)
        Tab followUpTab = new Tab("Follow-Up & Conversion");
        followUpTab.setGraphic(IconFactory.compactIcon("reminder", 14));
        followUpTab.setContent(Classy3DLoadingView.createTableSkeleton("Loading Follow-Up…", "Retrieving conversion status…"));
        java.util.concurrent.atomic.AtomicBoolean followUpLoaded = new java.util.concurrent.atomic.AtomicBoolean(false);
        followUpTab.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected && followUpLoaded.compareAndSet(false, true)) {
                followUpTab.setContent(buildFollowUpTabContent(quote));
            }
        });

        // Tab 3: Notes & Terms (Instant mount, all fields already in-memory)
        Tab notesTab = new Tab("Terms & Notes");
        notesTab.setGraphic(IconFactory.compactIcon("notes", 14));
        notesTab.setContent(buildNotesTabContent(quote));

        tabPane.getTabs().addAll(itemsTab, followUpTab, notesTab);

        // Action Dock
        HBox actionDock = buildActionDock(quote, dialog, api, support, onUpdated);

        VBox contentBox = new VBox(12, headerHero, metricStrip, tabPane, actionDock);
        contentBox.setPadding(new Insets(16, 20, 16, 20));
        contentBox.getStyleClass().addAll("document-inspection-content", "erp-ui-standard");
        contentBox.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        host.getChildren().setAll(contentBox);
    }

    private static HBox buildHeaderHero(QuotationApiClient.QuoteDto quote, Dialog<?> dialog) {
        Label iconLabel = new Label();
        iconLabel.setGraphic(IconFactory.icon("quotation", 32));
        iconLabel.getStyleClass().add("inspection-hero-icon");

        Label docTypeLabel = new Label("SALES QUOTATION");
        docTypeLabel.getStyleClass().add("inspection-kicker");

        Label docNoLabel = new Label(quote.no());
        docNoLabel.getStyleClass().addAll("inspection-hero-title", "sales-detail-invoice");

        Button btnCopy = new Button();
        btnCopy.setGraphic(IconFactory.compactIcon("copy", 13));
        btnCopy.setTooltip(new Tooltip("Copy quotation number"));
        btnCopy.getStyleClass().addAll("icon-button", "copy-ref-button", "approved-button");
        btnCopy.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(quote.no());
            clipboard.setContent(content);
            ToastManager.info(dialog.getDialogPane(), "Copied", quote.no() + " copied to clipboard.");
        });

        HBox docNoBox = new HBox(8, docNoLabel, btnCopy);
        docNoBox.setAlignment(Pos.CENTER_LEFT);

        String dateStr = BusinessClock.formatDate(quote.date());
        String validStr = BusinessClock.formatDate(quote.valid());
        Label metaLabel = new Label("Quote Date: " + dateStr + (validStr.isBlank() ? "" : "  •  Valid Until: " + validStr));
        metaLabel.getStyleClass().add("inspection-meta-label");

        VBox titleBox = new VBox(2, docTypeLabel, docNoBox, metaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status Badges Strip
        HBox statusStrip = new HBox(8);
        statusStrip.setAlignment(Pos.CENTER_RIGHT);

        String status = safe(quote.status()).toUpperCase(Locale.ROOT);
        if (status.isBlank()) status = "DRAFT";
        Label statusPill = createPill(status,
                status.equals("ACCEPTED") ? "pill-success" : status.equals("EXPIRED") || status.equals("REJECTED") ? "pill-danger" : status.equals("SENT") ? "pill-success" : "pill-warning",
                status.equals("ACCEPTED") ? "complete" : status.equals("EXPIRED") ? "error" : "reminder");

        statusStrip.getChildren().add(statusPill);

        if (!safe(quote.converted()).isBlank()) {
            Label convPill = createPill("Converted: " + quote.converted(), "pill-success", "sale");
            statusStrip.getChildren().add(convPill);
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

    private static HBox buildOverviewCards(QuotationApiClient.QuoteDto quote) {
        String custName = safe(quote.customer()).isBlank() ? "Unknown Customer" : quote.customer();
        String mobile = safe(quote.phone()).isBlank() ? "—" : quote.phone();
        String gstin = safe(quote.gstin()).isBlank() ? "Unregistered" : quote.gstin();
        VBox card1 = createSummaryCard("Customer Details", "customer",
                List.of(
                        new FieldPair("Party Name", custName, "customer", true),
                        new FieldPair("Contact Mobile", mobile, "phone", false),
                        new FieldPair("Customer GSTIN", gstin, "tax", false)
                ));

        VBox card2 = createSummaryCard("Quote Financials", "currency",
                List.of(
                        new FieldPair("Total Amount", money(quote.amount()), "currency", true),
                        new FieldPair("Special Discount", money(quote.discount()), "discount", false),
                        new FieldPair("Valid Until", BusinessClock.formatDate(quote.valid()), "calendar", false)
                ));

        String rep = safe(quote.salesperson()).isBlank() ? "Not Assigned" : quote.salesperson();
        String src = safe(quote.source()).isBlank() ? "Direct" : quote.source();
        String created = safe(quote.createdBy()).isBlank() ? "Admin" : quote.createdBy();
        VBox card3 = createSummaryCard("Sales & Origin", "user",
                List.of(
                        new FieldPair("Sales Representative", rep, "user", false),
                        new FieldPair("Lead Source", src, "source", false),
                        new FieldPair("Created By", created, "user", false)
                ));

        String follow = safe(quote.followUp()).isBlank() ? "None scheduled" : BusinessClock.formatDate(quote.followUp());
        String conv = safe(quote.converted()).isBlank() ? "Not converted" : quote.converted();
        VBox card4 = createSummaryCard("Lifecycle Status", "reminder",
                List.of(
                        new FieldPair("Follow-up Date", follow, "calendar", false),
                        new FieldPair("Sale Conversion", conv, safe(quote.converted()).isBlank() ? "reminder" : "complete", true),
                        new FieldPair("Current State", safe(quote.status()), "status", false)
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

            grid.add(lbl, 0, row);
            grid.add(val, 1, row);
            row++;
        }

        VBox card = new VBox(8, titleLabel, grid);
        card.getStyleClass().addAll("inspection-card", "erp-detail-drawer-card");
        card.setPadding(new Insets(10, 12, 10, 12));
        return card;
    }

    private static Node buildItemsTabContent(QuotationApiClient.QuoteDto quote, QuotationApiClient api) {
        TableView<QuotationApiClient.LineDto> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-standard", "inspection-items-table");
        table.setPlaceholder(Classy3DLoadingView.createTableSkeleton("Loading Quotation Lines…", "Streaming line items, discount structures, and tax allocations for " + quote.no()));

        table.setFixedCellSize(36.0);

        TableColumn<QuotationApiClient.LineDto, Number> colIndex = new TableColumn<>("#");
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

        TableColumn<QuotationApiClient.LineDto, String> colCode = new TableColumn<>("Item Code");
        colCode.setPrefWidth(125);
        colCode.setMinWidth(95);
        colCode.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().code())));
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

        TableColumn<QuotationApiClient.LineDto, String> colDesc = new TableColumn<>("Item Description");
        colDesc.setPrefWidth(240);
        colDesc.setMinWidth(160);
        colDesc.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().description())));
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

        TableColumn<QuotationApiClient.LineDto, String> colHsn = new TableColumn<>("HSN / SAC");
        colHsn.setPrefWidth(95);
        colHsn.setMinWidth(75);
        colHsn.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().hsn())));
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

        TableColumn<QuotationApiClient.LineDto, String> colUnit = new TableColumn<>("Unit");
        colUnit.setPrefWidth(70);
        colUnit.setMinWidth(55);
        colUnit.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().unit())));
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

        TableColumn<QuotationApiClient.LineDto, String> colQty = new TableColumn<>("Quantity");
        colQty.setPrefWidth(85);
        colQty.setMinWidth(70);
        colQty.setCellValueFactory(v -> new ReadOnlyStringWrapper(String.format(Locale.ROOT, "%,.2f", v.getValue().quantity())));
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

        TableColumn<QuotationApiClient.LineDto, String> colRate = new TableColumn<>("Rate");
        colRate.setPrefWidth(95);
        colRate.setMinWidth(75);
        colRate.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().rate())));
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

        TableColumn<QuotationApiClient.LineDto, String> colDiscount = new TableColumn<>("Discount");
        colDiscount.setPrefWidth(90);
        colDiscount.setMinWidth(70);
        colDiscount.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().discount())));
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

        TableColumn<QuotationApiClient.LineDto, String> colGst = new TableColumn<>("GST %");
        colGst.setPrefWidth(80);
        colGst.setMinWidth(65);
        colGst.setCellValueFactory(v -> new ReadOnlyStringWrapper(String.format(Locale.ROOT, "%.1f%%", v.getValue().gst())));
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

        TableColumn<QuotationApiClient.LineDto, String> colTotal = new TableColumn<>("Line Total");
        colTotal.setPrefWidth(120);
        colTotal.setMinWidth(100);
        colTotal.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().total())));
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

        table.getColumns().addAll(colIndex, colCode, colDesc, colHsn, colUnit, colQty, colRate, colDiscount, colGst, colTotal);
        DynamicTableLayoutManager.install(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label lblSummary = new Label("Loading quotation lines…");
        lblSummary.getStyleClass().add("muted-label");

        HBox footer = new HBox(12, lblSummary);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 6, 4, 6));

        VBox container = new VBox(8, table, footer);
        container.setPadding(new Insets(8, 0, 0, 0));

        UiTaskExecutor.submitLatest("quote-inspection-lines-" + quote.id(),
                () -> api.lines(quote.id()),
                lines -> {
                    table.getItems().setAll(lines);
                    if (lines.isEmpty()) {
                        table.setPlaceholder(new Label("No line items associated with this quotation."));
                    }
                    double totalQty = lines.stream().mapToDouble(QuotationApiClient.LineDto::quantity).sum();
                    double totalAmt = lines.stream().mapToDouble(QuotationApiClient.LineDto::total).sum();
                    lblSummary.setText(String.format(Locale.ROOT,
                            "%d item(s) • Total Quantity: %,.2f • Quote Total: %s",
                            lines.size(), totalQty, money(totalAmt)));
                },
                err -> lblSummary.setText("Failed to load line items: " + err.getMessage()));

        return container;
    }

    private static Node buildFollowUpTabContent(QuotationApiClient.QuoteDto quote) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(14, 16, 14, 16));
        grid.getStyleClass().addAll("inspection-detail-grid", "erp-detail-drawer-card");

        int r = 0;
        addDetailRow(grid, r++, "Current Status", safe(quote.status()), "status");
        addDetailRow(grid, r++, "Follow-Up Date", !safe(quote.followUp()).isBlank() ? BusinessClock.formatDate(quote.followUp()) : "Not scheduled", "calendar");
        addDetailRow(grid, r++, "Converted Sale Invoice", !safe(quote.converted()).isBlank() ? quote.converted() : "Not converted yet", "sale");
        addDetailRow(grid, r++, "Lead Source", !safe(quote.source()).isBlank() ? quote.source() : "Direct", "source");
        addDetailRow(grid, r++, "Assigned Salesperson", !safe(quote.salesperson()).isBlank() ? quote.salesperson() : "Unassigned", "user");
        addDetailRow(grid, r++, "Created By", !safe(quote.createdBy()).isBlank() ? quote.createdBy() : "Admin", "user");

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("erp-inner-scroll", "inspection-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private static Node buildNotesTabContent(QuotationApiClient.QuoteDto quote) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14, 16, 14, 16));
        box.getStyleClass().addAll("inspection-detail-grid", "erp-detail-drawer-card");

        Label cap = new Label("Terms, Conditions & Remarks:");
        cap.setGraphic(IconFactory.compactIcon("notes", 14));
        cap.getStyleClass().add("inspection-card-title");

        TextArea txt = new TextArea(safe(quote.remarks()).isBlank() ? "No special terms or notes recorded for this quotation." : quote.remarks());
        txt.setEditable(false);
        txt.setWrapText(true);
        txt.setPrefRowCount(8);
        txt.getStyleClass().addAll("approved-input", "read-only-textarea");
        VBox.setVgrow(txt, Priority.ALWAYS);

        box.getChildren().addAll(cap, txt);
        return box;
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

    private static HBox buildActionDock(QuotationApiClient.QuoteDto quote, Dialog<?> dialog, QuotationApiClient api, SupportApiClient support, Runnable onUpdated) {
        HBox dock = new HBox(8);
        dock.setAlignment(Pos.CENTER_LEFT);
        dock.getStyleClass().add("inspection-action-dock");
        dock.setPadding(new Insets(10, 0, 0, 0));

        boolean alreadyConverted = !safe(quote.converted()).isBlank();

        // 1. Edit Quotation
        Button btnEdit = new Button("Edit Quotation");
        btnEdit.setGraphic(IconFactory.compactIcon("edit", 14));
        btnEdit.getStyleClass().addAll("approved-button", "approved-amber-button");
        btnEdit.setDisable(alreadyConverted);
        if (alreadyConverted) {
            btnEdit.setTooltip(new Tooltip("Converted quotations are read-only. Duplicate the quotation to create a new revision."));
        } else {
            btnEdit.setTooltip(new Tooltip("Edit quotation #" + quote.no()));
        }
        btnEdit.setOnAction(e -> {
            dialog.close();
            QuotationEditorContext.open(quote.id());
            NavigationManager.getInstance().loadPage("/fxml/pages/QuotationEditor.fxml");
        });

        // 2. Duplicate Quotation
        Button btnDuplicate = new Button("Duplicate");
        btnDuplicate.setGraphic(IconFactory.compactIcon("copy", 14));
        btnDuplicate.getStyleClass().addAll("approved-button", "approved-indigo-button");
        btnDuplicate.setOnAction(e -> {
            String actor = SessionService.current() == null ? "Admin" : SessionService.current().getFullName();
            UiTaskExecutor.submitAction("quotation-duplicate-" + quote.id(), () -> {
                api.duplicate(quote.id(), actor);
                return true;
            }, ignored -> {
                ToastManager.success(dialog.getDialogPane(), "Quotation Duplicated", quote.no() + " was duplicated successfully.");
                if (onUpdated != null) onUpdated.run();
                dialog.close();
            }, err -> ToastManager.error(dialog.getDialogPane(), "Duplication failed", err.getMessage()));
        });

        // 3. Print / PDF
        Button btnPdf = new Button("Print / PDF");
        btnPdf.setGraphic(IconFactory.compactIcon("print", 14));
        btnPdf.getStyleClass().addAll("approved-button", "approved-primary-button");
        btnPdf.setOnAction(e -> {
            UiTaskExecutor.submitAction("quotation-pdf-" + quote.id(), () -> {
                return ManagedInvoicePdfService.quotation(quote.no());
            }, path -> {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Failed to open PDF", ex.getMessage());
                }
            }, err -> ToastManager.error(dialog.getDialogPane(), "PDF generation failed", err.getMessage()));
        });

        // 4. Excel
        Button btnExcel = new Button("Excel");
        btnExcel.setGraphic(IconFactory.compactIcon("excel", 14));
        btnExcel.getStyleClass().addAll("approved-button", "approved-emerald-button");
        btnExcel.setOnAction(e -> {
            UiTaskExecutor.submitAction("quotation-excel-" + quote.id(), () -> {
                return org.example.documentstudio.service.ExcelOutputService.generate(org.example.documentstudio.model.DocumentType.QUOTATION, quote.no());
            }, path -> {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Failed to open Excel", ex.getMessage());
                }
            }, err -> ToastManager.error(dialog.getDialogPane(), "Excel generation failed", err.getMessage()));
        });

        // 5. Email
        Button btnEmail = new Button("Email");
        btnEmail.setGraphic(IconFactory.compactIcon("email", 14));
        btnEmail.getStyleClass().addAll("approved-button", "approved-sky-button");
        btnEmail.setOnAction(e -> {
            UiTaskExecutor.submitAction("quotation-prepare-email-" + quote.id(), () -> {
                Path pdf = ManagedInvoicePdfService.quotation(quote.no());
                LocalDate qDate = null;
                try {
                    if (quote.date() != null && !quote.date().isBlank()) qDate = LocalDate.parse(quote.date().trim());
                } catch (Exception ignored) {}
                MessageTemplateService.FormattedMessage msg = MessageTemplateService.formatQuotationEmail(quote.no(), quote.customer(), qDate, quote.amount());
                String sigImg = MessageTemplateService.getSignatureImagePath();
                Path sigPath = (sigImg != null && !sigImg.isBlank()) ? Path.of(sigImg) : null;
                return new EmailTransmissionDialog.EmailDraft(
                        "Transmit Quotation via Email",
                        "Direct SMTP delivery to customer contact",
                        "Quotation",
                        quote.no(),
                        quote.email() == null ? "" : quote.email().trim(),
                        msg.subject(),
                        msg.body(),
                        pdf,
                        sigPath
                );
            }, draft -> {
                Optional<EmailTransmissionDialog.TransmissionResult> opt = EmailTransmissionDialog.show(dialog.getDialogPane(), draft);
                if (opt.isEmpty()) return;
                EmailTransmissionDialog.TransmissionResult result = opt.get();
                LiveOperationDialog.run(dialog.getDialogPane(), "Sending Quotation Email", "email", "Connecting to SMTP server...", reporter -> {
                    reporter.stage(1, 3, "Connecting to SMTP and transmitting email to " + result.recipientEmail() + "...");
                    EmailService.send(result.recipientEmail(), result.subject(), result.body(), result.attachmentPdf(), result.signatureImage());
                    reporter.stage(2, 3, "Recording communication history in audit register...");
                    api.markSent(quote.id(), "EMAIL", quote.rowVersion());
                    try {
                        support.communication(new SupportApiClient.CommunicationRequest(
                                "QUOTATION", quote.id(), "EMAIL", result.recipientEmail(), "Quotation " + quote.no(), "SENT", null,
                                SessionService.current() == null ? "System" : SessionService.current().getFullName()
                        ));
                    } catch (Exception ignored) {}
                    reporter.stage(3, 3, "Email delivery complete.");
                    reporter.log("Delivered quotation #" + quote.no() + " to " + result.recipientEmail());
                }, () -> {
                    ToastManager.success(dialog.getDialogPane(), "Quotation emailed", "Quotation " + quote.no() + " sent successfully to " + result.recipientEmail() + ".");
                    if (onUpdated != null) onUpdated.run();
                }, failure -> ToastManager.error(dialog.getDialogPane(), "Email failed", failure.getMessage()));
            }, failure -> ToastManager.error(dialog.getDialogPane(), "Email preparation failed", failure.getMessage()));
        });

        // 6. Schedule Follow Up
        Button btnFollowUp = new Button("Follow Up");
        btnFollowUp.setGraphic(IconFactory.compactIcon("reminder", 14));
        btnFollowUp.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnFollowUp.setOnAction(e -> {
            LocalDate followUpDate = BusinessClock.today().plusDays(1);
            if (quote.followUp() != null && !quote.followUp().isBlank()) {
                try {
                    followUpDate = LocalDate.parse(quote.followUp().trim());
                } catch (Exception ignored) {}
            }
            DatePicker date = DatePickerFormatter.attach(new DatePicker(followUpDate));
            Dialog<ButtonType> d = new OwnedDialog<>(dialog.getDialogPane());
            d.setTitle("Schedule Follow-Up");
            TextArea notes = new TextArea("Follow up for quotation " + quote.no());
            VBox box = new VBox(8, new Label("Follow-up date:"), date, new Label("Notes:"), notes);
            d.getDialogPane().setContent(box);
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            d.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                String due = date.getValue().toString();
                String text = notes.getText();
                UiTaskExecutor.submitAction("quotation-followup-" + quote.id(), () -> {
                    api.followUp(quote.id(), due, text, quote.rowVersion());
                    return true;
                }, ignored -> {
                    ToastManager.success(dialog.getDialogPane(), "Follow-up saved", quote.no() + " follow-up scheduled successfully.");
                    if (onUpdated != null) onUpdated.run();
                }, failure -> ToastManager.error(dialog.getDialogPane(), "Failed to save follow-up", failure.getMessage()));
            });
        });

        // 7. Convert to Sale
        Button btnConvert = new Button(alreadyConverted ? "View Converted Sale" : "Convert to Sale");
        btnConvert.setGraphic(IconFactory.compactIcon("sale", 14));
        btnConvert.getStyleClass().addAll("approved-button", alreadyConverted ? "approved-secondary-button" : "approved-primary-button");
        btnConvert.setOnAction(e -> {
            dialog.close();
            if (alreadyConverted) {
                LinkedRecordContext.open("SALE", null, quote.converted(), "VIEW", "Quotation " + quote.no());
                NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml");
            } else {
                try {
                    String actor = SessionService.current() == null ? "Admin" : SessionService.current().getFullName();
                    String saleInvoice = api.convert(quote.id(), actor, quote.rowVersion());
                    ToastManager.success(dialog.getDialogPane(), "Converted", "Quotation converted to " + saleInvoice);
                    if (onUpdated != null) onUpdated.run();
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Conversion failed", ex.getMessage());
                }
            }
        });

        // 8. Audit Trail
        Button btnAudit = new Button("Audit Trail");
        btnAudit.setGraphic(IconFactory.compactIcon("history", 14));
        btnAudit.getStyleClass().addAll("approved-button", "approved-slate-button");
        btnAudit.setOnAction(e -> {
            UiTaskExecutor.submitLatest("quote-audit-" + quote.no(),
                    () -> support.resolveRecord("QUOTATION", quote.no()),
                    res -> {
                        if (res.found() && res.recordId() != null) {
                            ActivityTimelineDialog.show(dialog.getDialogPane(), "QUOTATION", res.recordId().intValue(), quote.no());
                        }
                    },
                    err -> ToastManager.error(dialog.getDialogPane(), "Audit record not found", err.getMessage()));
        });

        dock.getChildren().addAll(btnEdit, btnDuplicate, btnPdf, btnExcel, btnEmail, btnFollowUp, btnConvert, btnAudit);

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
}
