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
import org.example.api.returns.ReturnApiClient;
import org.example.api.support.SupportApiClient;
import org.example.controller.LinkedRecordContext;
import org.example.controller.EmailTransmissionDialog;
import org.example.controller.ReturnRefundContext;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.service.ExcelOutputService;
import org.example.navigation.NavigationManager;
import org.example.service.*;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;

/**
 * Centered, spacious Document Inspection Window Pop-up for Sales Returns and Purchase Returns.
 * Replaces the cramped inline side drawer with a rich, multi-tab inspection interface.
 */
public final class ReturnDocumentInspectionDialog {

    private ReturnDocumentInspectionDialog() {}

    public static void show(Node ownerNode, String returnNo, boolean isSalesReturn, ReturnApiClient returnApiClient, SupportApiClient supportApiClient, Runnable onUpdated) {
        if (returnNo == null || returnNo.isBlank()) return;

        final ReturnApiClient api = (returnApiClient != null) ? returnApiClient : new ReturnApiClient();
        final SupportApiClient support = (supportApiClient != null) ? supportApiClient : new SupportApiClient();

        ReturnApiClient.Details details;
        try {
            details = api.details(returnNo);
        } catch (Exception ex) {
            ToastManager.error(ownerNode, "Failed to load return details", ex.getMessage());
            return;
        }

        final ReturnApiClient.Details currentDetails = details;

        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(ownerNode);
        dialog.setTitle((isSalesReturn ? "Sales Return Inspection — " : "Purchase Return Inspection — ") + currentDetails.no());
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("erp-document-inspection-dialog", "return-inspection-dialog", "approved-ui");
        PopupTableWorkspace.applyInspectionSizing(dialog, ownerNode);

        // Header Hero
        HBox headerHero = buildHeaderHero(currentDetails, isSalesReturn, dialog);

        // Overview Cards
        HBox metricStrip = buildOverviewCards(currentDetails, isSalesReturn);

        // TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().addAll("erp-inspection-tabs", "approved-tab-pane");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1: Returned Items
        Tab itemsTab = new Tab("Returned Lines (" + (currentDetails.lines() == null ? 0 : currentDetails.lines().size()) + ")");
        itemsTab.setGraphic(IconFactory.compactIcon("item", 14));
        itemsTab.setContent(buildItemsTabContent(currentDetails));

        // Tab 2: Refunds & Settlements
        Tab refundsTab = new Tab("Refunds & Settlements");
        refundsTab.setGraphic(IconFactory.compactIcon("payment", 14));
        refundsTab.setContent(buildRefundsTabContent(currentDetails, api, dialog, isSalesReturn));

        // Tab 3: Notes & Origin
        Tab notesTab = new Tab("Notes & Original Document");
        notesTab.setGraphic(IconFactory.compactIcon("notes", 14));
        notesTab.setContent(buildNotesTabContent(currentDetails, isSalesReturn, dialog));

        tabPane.getTabs().addAll(itemsTab, refundsTab, notesTab);

        // Action Dock
        HBox actionDock = buildActionDock(currentDetails, isSalesReturn, dialog, api, support, onUpdated);

        VBox contentBox = new VBox(12, headerHero, metricStrip, tabPane, actionDock);
        contentBox.setPadding(new Insets(16, 20, 16, 20));
        contentBox.getStyleClass().addAll("document-inspection-content", "erp-ui-standard");
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        pane.setContent(contentBox);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Node defaultButtonBar = pane.lookup(".button-bar");
        if (defaultButtonBar != null) {
            defaultButtonBar.setVisible(false);
            defaultButtonBar.setManaged(false);
        }

        dialog.showAndWait();
    }

    private static HBox buildHeaderHero(ReturnApiClient.Details details, boolean isSales, Dialog<?> dialog) {
        Label iconLabel = new Label();
        iconLabel.setGraphic(IconFactory.icon("return", 32));
        iconLabel.getStyleClass().add("inspection-hero-icon");

        Label docTypeLabel = new Label(isSales ? "CREDIT NOTE / SALES RETURN" : "DEBIT NOTE / PURCHASE RETURN");
        docTypeLabel.getStyleClass().add("inspection-kicker");

        Label docNoLabel = new Label(details.no());
        docNoLabel.getStyleClass().addAll("inspection-hero-title", "sales-detail-invoice");

        Button btnCopy = new Button();
        btnCopy.setGraphic(IconFactory.compactIcon("copy", 13));
        btnCopy.setTooltip(new Tooltip("Copy return reference number"));
        btnCopy.getStyleClass().addAll("icon-button", "copy-ref-button", "approved-button", "approved-secondary-button");
        btnCopy.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(details.no());
            clipboard.setContent(content);
            ToastManager.info(dialog.getDialogPane(), "Copied", details.no() + " copied to clipboard.");
        });

        HBox docNoBox = new HBox(8, docNoLabel, btnCopy);
        docNoBox.setAlignment(Pos.CENTER_LEFT);

        String dateStr = BusinessClock.formatDate(details.date());
        String timeStr = details.createdAt() != null && !details.createdAt().isBlank()
                ? BusinessClock.formatTimestamp(details.createdAt())
                : "";
        Label metaLabel = new Label("Return Date: " + dateStr + (timeStr.isBlank() ? "" : "  •  Created: " + timeStr));
        metaLabel.getStyleClass().add("inspection-meta-label");

        VBox titleBox = new VBox(2, docTypeLabel, docNoBox, metaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status Badges Strip
        HBox statusStrip = new HBox(8);
        statusStrip.setAlignment(Pos.CENTER_RIGHT);

        String status = safe(details.status()).toUpperCase(Locale.ROOT);
        if (status.isBlank()) status = "PENDING APPROVAL";
        Label statusPill = createPill(status,
                status.equals("APPROVED") ? "pill-success" : status.contains("REJECT") || status.contains("CANCEL") ? "pill-danger" : "pill-warning",
                status.equals("APPROVED") ? "complete" : status.contains("REJECT") ? "error" : "reminder");

        String refundStatus = safe(details.refundStatus()).toUpperCase(Locale.ROOT);
        if (refundStatus.isBlank()) refundStatus = "PENDING";
        Label refundPill = createPill(refundStatus,
                refundStatus.equals("REFUNDED") || refundStatus.equals("SETTLED") ? "pill-success" : refundStatus.equals("PARTIAL") ? "pill-warning" : "pill-danger",
                refundStatus.equals("REFUNDED") || refundStatus.equals("SETTLED") ? "payment" : "balance");

        statusStrip.getChildren().addAll(statusPill, refundPill);

        HBox hero = new HBox(12, iconLabel, titleBox, spacer, statusStrip);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("inspection-hero-bar");
        return hero;
    }

    private static Label createPill(String text, String pillClass, String iconSemantic) {
        Label pill = new Label(text);
        pill.getStyleClass().addAll("status-pill", pillClass);
        pill.setGraphic(IconFactory.compactIcon(iconSemantic, 13));
        pill.setGraphicTextGap(5);
        return pill;
    }

    private static HBox buildOverviewCards(ReturnApiClient.Details details, boolean isSales) {
        String partyTitle = isSales ? "Customer Details" : "Supplier Details";
        String partySemantic = isSales ? "customer" : "supplier";
        VBox card1 = createSummaryCard(partyTitle, partySemantic,
                List.of(
                        new FieldPair("Party Name", safe(details.party()), partySemantic, true),
                        new FieldPair("Original Invoice", safe(details.invoice()), "document", false),
                        new FieldPair("Return Type", safe(details.type()), "return", false)
                ));

        VBox card2 = createSummaryCard("Return Value", "currency",
                List.of(
                        new FieldPair("Return Total", money(details.total()), "currency", true),
                        new FieldPair("Refunded Amount", money(details.refund()), "complete", false),
                        new FieldPair("Pending Settlement", money(Math.max(0, details.total() - details.refund())),
                                (details.total() - details.refund()) > .009 ? "balance" : "complete", true)
                ));

        VBox card3 = createSummaryCard("Approval & Status", "status",
                List.of(
                        new FieldPair("Approval Status", safe(details.status()), "status", true),
                        new FieldPair("Settlement State", safe(details.refundStatus()), "payment", false),
                        new FieldPair("Return Currency", safe(details.currency()).isBlank() ? "INR" : details.currency(), "currency", false)
                ));

        VBox card4 = createSummaryCard("Timeline & Audit", "calendar",
                List.of(
                        new FieldPair("Return Date", BusinessClock.formatDate(details.date()), "calendar", false),
                        new FieldPair("Created", BusinessClock.formatTimestamp(details.createdAt()), "calendar", false),
                        new FieldPair("Last Updated", BusinessClock.formatTimestamp(details.updatedAt()), "calendar", false)
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

    private static Node buildItemsTabContent(ReturnApiClient.Details details) {
        TableView<ReturnApiClient.Line> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-standard", "inspection-items-table");
        table.setPlaceholder(new Label("No line items associated with this return."));

        TableColumn<ReturnApiClient.Line, Number> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(42);
        colIndex.setSortable(false);
        colIndex.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(table.getItems().indexOf(v.getValue()) + 1));
        colIndex.setStyle("-fx-alignment: CENTER;");

        TableColumn<ReturnApiClient.Line, String> colCode = new TableColumn<>("Item Code");
        colCode.setPrefWidth(120);
        colCode.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().code())));
        IconFactory.applyTableHeaderIcon(colCode, "item");

        TableColumn<ReturnApiClient.Line, String> colDesc = new TableColumn<>("Item Name");
        colDesc.setPrefWidth(220);
        colDesc.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().name())));
        IconFactory.applyTableHeaderIcon(colDesc, "document");

        TableColumn<ReturnApiClient.Line, String> colUnit = new TableColumn<>("Unit");
        colUnit.setPrefWidth(65);
        colUnit.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().unit())));

        TableColumn<ReturnApiClient.Line, String> colQty = new TableColumn<>("Returned Qty");
        colQty.setPrefWidth(95);
        colQty.setCellValueFactory(v -> new ReadOnlyStringWrapper(String.format(Locale.ROOT, "%,.2f", v.getValue().quantity())));
        colQty.setStyle("-fx-alignment: CENTER_RIGHT;");

        TableColumn<ReturnApiClient.Line, String> colRate = new TableColumn<>("Rate");
        colRate.setPrefWidth(95);
        colRate.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().rate())));
        colRate.setStyle("-fx-alignment: CENTER_RIGHT;");

        TableColumn<ReturnApiClient.Line, String> colTax = new TableColumn<>("Tax");
        colTax.setPrefWidth(95);
        colTax.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().tax())));
        colTax.setStyle("-fx-alignment: CENTER_RIGHT;");

        TableColumn<ReturnApiClient.Line, String> colTotal = new TableColumn<>("Line Amount");
        colTotal.setPrefWidth(115);
        colTotal.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().amount())));
        colTotal.setStyle("-fx-alignment: CENTER_RIGHT; -fx-font-weight: bold;");

        TableColumn<ReturnApiClient.Line, String> colReason = new TableColumn<>("Return Reason");
        colReason.setPrefWidth(180);
        colReason.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().reason())));
        IconFactory.applyTableHeaderIcon(colReason, "notes");

        table.getColumns().addAll(colIndex, colCode, colDesc, colUnit, colQty, colRate, colTax, colTotal, colReason);
        DynamicTableLayoutManager.install(table);

        List<ReturnApiClient.Line> lines = details.lines() != null ? details.lines() : List.of();
        table.setItems(FXCollections.observableArrayList(lines));
        VBox.setVgrow(table, Priority.ALWAYS);

        double sumQty = lines.stream().mapToDouble(ReturnApiClient.Line::quantity).sum();
        double sumTotal = lines.stream().mapToDouble(ReturnApiClient.Line::amount).sum();

        Label lblCount = new Label(lines.size() + (lines.size() == 1 ? " returned line" : " returned lines"));
        lblCount.getStyleClass().add("muted-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblTotals = new Label(String.format(Locale.ROOT, "Total Returned Qty: %,.2f   •   Return Total: %s",
                sumQty, money(sumTotal)));
        lblTotals.getStyleClass().addAll("inspection-footer-totals", "summary-amount");

        HBox footer = new HBox(12, lblCount, spacer, lblTotals);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 6, 4, 6));

        VBox container = new VBox(8, table, footer);
        container.setPadding(new Insets(8, 0, 0, 0));
        return container;
    }

    private static Node buildRefundsTabContent(ReturnApiClient.Details details, ReturnApiClient api, Dialog<?> dialog, boolean isSales) {
        TableView<ReturnApiClient.RefundRow> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-standard", "invoice-payment-table");
        table.setPlaceholder(new Label("No refund entries recorded yet."));

        TableColumn<ReturnApiClient.RefundRow, String> colDate = new TableColumn<>("Date");
        colDate.setPrefWidth(110);
        colDate.setCellValueFactory(v -> new ReadOnlyStringWrapper(BusinessClock.formatDate(v.getValue().date())));
        IconFactory.applyTableHeaderIcon(colDate, "calendar");

        TableColumn<ReturnApiClient.RefundRow, String> colRef = new TableColumn<>("Reference");
        colRef.setPrefWidth(140);
        colRef.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().reference())));
        IconFactory.applyTableHeaderIcon(colRef, "document");

        TableColumn<ReturnApiClient.RefundRow, String> colMode = new TableColumn<>("Mode");
        colMode.setPrefWidth(110);
        colMode.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().mode())));

        TableColumn<ReturnApiClient.RefundRow, String> colAccount = new TableColumn<>("Bank / Cash Account");
        colAccount.setPrefWidth(160);
        colAccount.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().bankAccount())));

        TableColumn<ReturnApiClient.RefundRow, String> colAmount = new TableColumn<>("Amount");
        colAmount.setPrefWidth(120);
        colAmount.setCellValueFactory(v -> new ReadOnlyStringWrapper(money(v.getValue().amount())));
        colAmount.setStyle("-fx-alignment: CENTER_RIGHT; -fx-font-weight: bold;");
        IconFactory.applyTableHeaderIcon(colAmount, "currency");

        TableColumn<ReturnApiClient.RefundRow, String> colNotes = new TableColumn<>("Notes");
        colNotes.setPrefWidth(200);
        colNotes.setCellValueFactory(v -> new ReadOnlyStringWrapper(safe(v.getValue().notes())));

        table.getColumns().addAll(colDate, colRef, colMode, colAccount, colAmount, colNotes);
        DynamicTableLayoutManager.install(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label lblSummary = new Label("Loading refund history…");
        lblSummary.getStyleClass().add("muted-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnRecord = new Button("Record Refund");
        btnRecord.setGraphic(IconFactory.compactIcon("payment", 14));
        btnRecord.getStyleClass().addAll("approved-button", "approved-primary-button");
        btnRecord.setOnAction(e -> {
            dialog.close();
            ReturnRefundContext.select(details.no());
            NavigationManager.getInstance().loadPage("/fxml/pages/ReturnRefund.fxml");
        });

        HBox footer = new HBox(12, lblSummary, spacer, btnRecord);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 6, 4, 6));

        VBox container = new VBox(8, table, footer);
        container.setPadding(new Insets(8, 0, 0, 0));

        UiTaskExecutor.submitLatest("return-inspection-refunds-" + details.no(),
                () -> api.refunds(details.no()),
                records -> {
                    table.getItems().setAll(records);
                    double totalRefunded = records.stream().mapToDouble(ReturnApiClient.RefundRow::amount).sum();
                    lblSummary.setText(String.format(Locale.ROOT,
                            "%d refund record(s) • Total Settled: %s • Pending: %s",
                            records.size(), money(totalRefunded), money(Math.max(0, details.total() - totalRefunded))));
                },
                err -> lblSummary.setText("Failed to load refunds: " + err.getMessage()));

        return container;
    }

    private static Node buildNotesTabContent(ReturnApiClient.Details details, boolean isSales, Dialog<?> dialog) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(14, 16, 14, 16));
        grid.getStyleClass().addAll("inspection-detail-grid", "erp-detail-drawer-card");

        int r = 0;
        addDetailRow(grid, r++, "Original Invoice Ref", safe(details.invoice()), "document");
        addDetailRow(grid, r++, isSales ? "Customer Name" : "Supplier Name", safe(details.party()), isSales ? "customer" : "supplier");
        addDetailRow(grid, r++, "Payment Terms", safe(details.paymentTerms()).isBlank() ? "Standard" : details.paymentTerms(), "payment");
        addDetailRow(grid, r++, "Notes / Remarks", safe(details.notes()).isBlank() ? "No notes recorded." : details.notes(), "notes");

        HBox actionRow = new HBox(10);
        actionRow.setPadding(new Insets(10, 0, 0, 0));
        Button btnViewOriginal = new Button("Open Original Invoice (" + details.invoice() + ")");
        btnViewOriginal.setGraphic(IconFactory.compactIcon(isSales ? "sales" : "purchase", 14));
        btnViewOriginal.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnViewOriginal.setOnAction(e -> {
            dialog.close();
            if (isSales) {
                LinkedRecordContext.open("SALE", null, details.invoice(), "VIEW", "Sales Return " + details.no());
                NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml");
            } else {
                LinkedRecordContext.open("PURCHASE", null, details.invoice(), "VIEW", "Purchase Return " + details.no());
                NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml");
            }
        });
        actionRow.getChildren().add(btnViewOriginal);

        VBox container = new VBox(10, grid, actionRow);
        ScrollPane scroll = new ScrollPane(container);
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

    private static HBox buildActionDock(ReturnApiClient.Details details, boolean isSales, Dialog<?> dialog, ReturnApiClient api, SupportApiClient support, Runnable onUpdated) {
        HBox dock = new HBox(8);
        dock.setAlignment(Pos.CENTER_LEFT);
        dock.getStyleClass().add("inspection-action-dock");
        dock.setPadding(new Insets(10, 0, 0, 0));

        boolean isCancelled = "CANCELLED".equalsIgnoreCase(safe(details.status()).trim()) || "DELETED".equalsIgnoreCase(safe(details.status()).trim());
        boolean isApproved = "APPROVED".equalsIgnoreCase(safe(details.status()).trim());
        boolean isPending = "PENDING APPROVAL".equalsIgnoreCase(safe(details.status()).trim());
        boolean canApprove = PermissionService.allowed(isSales ? "SALES.APPROVE" : "PURCHASE.APPROVE");

        // 1. Edit Reason
        Button btnEditReason = new Button("Edit Reason");
        btnEditReason.setGraphic(IconFactory.compactIcon("edit", 14));
        btnEditReason.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnEditReason.setDisable(isCancelled);
        if (isCancelled) {
            btnEditReason.setTooltip(new Tooltip("Cancelled or deleted returns cannot be edited."));
        } else {
            btnEditReason.setTooltip(new Tooltip("Edit reason for " + details.no()));
        }
        btnEditReason.setOnAction(e -> {
            OwnedTextInputDialog td = new OwnedTextInputDialog(safe(details.notes()));
            td.setTitle("Edit Return Reason");
            td.setHeaderText("Update reason for " + details.no());
            td.setContentText("Reason:");
            td.showAndWait().map(String::trim).filter(r -> !r.isBlank()).ifPresent(newReason -> {
                UiTaskExecutor.submitSerial("return-update-reason-" + details.no(), () -> {
                    api.update(details.no(), "reason", newReason, details.rowVersion());
                    return true;
                }, ok -> {
                    ScreenRefreshPolicy.invalidate(isSales ? "sales-returns" : "purchase-returns");
                    ToastManager.success(dialog.getDialogPane(), "Reason Updated", "Return reason was updated successfully.");
                    if (onUpdated != null) onUpdated.run();
                    dialog.close();
                }, failure -> ToastManager.error(dialog.getDialogPane(), "Update Failed", failure.getMessage()));
            });
        });

        // 2. Open Original Invoice / Purchase Bill
        Button btnOriginal = new Button(isSales ? "Original Invoice (" + details.invoice() + ")" : "Original Purchase (" + details.invoice() + ")");
        btnOriginal.setGraphic(IconFactory.compactIcon(isSales ? "sales" : "purchase", 14));
        btnOriginal.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnOriginal.setTooltip(new Tooltip("View original " + (isSales ? "sales invoice " : "purchase bill ") + details.invoice()));
        btnOriginal.setOnAction(e -> {
            dialog.close();
            if (isSales) {
                LinkedRecordContext.open("SALE", null, details.invoice(), "VIEW", "Sales Return " + details.no());
                NavigationManager.getInstance().loadPage("/fxml/pages/SalesList.fxml");
            } else {
                LinkedRecordContext.open("PURCHASE", null, details.invoice(), "VIEW", "Purchase Return " + details.no());
                NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml");
            }
        });

        // 3. Record Refund
        boolean canRefund = isApproved && !isCancelled;
        Button btnRefund = new Button("Record Refund");
        btnRefund.setGraphic(IconFactory.compactIcon("payment", 14));
        btnRefund.getStyleClass().addAll("approved-button", canRefund ? "approved-primary-button" : "approved-secondary-button");
        btnRefund.setDisable(!canRefund);
        if (!isApproved) {
            btnRefund.setTooltip(new Tooltip("Return approval required before recording refund / settlement."));
        } else if (isCancelled) {
            btnRefund.setTooltip(new Tooltip("Cancelled return cannot be refunded."));
        } else {
            btnRefund.setTooltip(new Tooltip("Record refund / settlement for " + details.no()));
        }
        btnRefund.setOnAction(e -> {
            dialog.close();
            ReturnRefundContext.select(details.no());
            NavigationManager.getInstance().loadPage("/fxml/pages/ReturnRefund.fxml");
        });

        // 4. Print / PDF
        Button btnPdf = new Button("Print / PDF");
        btnPdf.setGraphic(IconFactory.compactIcon("print", 14));
        btnPdf.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnPdf.setOnAction(e -> {
            UiTaskExecutor.submitAction("return-pdf-" + details.no(), () -> {
                return ManagedInvoicePdfService.refund(details.no(), isSales);
            }, path -> {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Failed to open PDF", ex.getMessage());
                }
            }, err -> ToastManager.error(dialog.getDialogPane(), "PDF generation failed", err.getMessage()));
        });

        // 5. Excel
        Button btnExcel = new Button("Excel");
        btnExcel.setGraphic(IconFactory.compactIcon("excel", 14));
        btnExcel.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnExcel.setOnAction(e -> {
            UiTaskExecutor.submitAction("return-excel-" + details.no(), () -> {
                return ExcelOutputService.generate(
                        isSales ? DocumentType.SALES_RETURN : DocumentType.PURCHASE_RETURN,
                        details.no()
                );
            }, path -> {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Failed to open Excel", ex.getMessage());
                }
            }, err -> ToastManager.error(dialog.getDialogPane(), "Excel export failed", err.getMessage()));
        });

        // 6. Email
        Button btnEmail = new Button("Email");
        btnEmail.setGraphic(IconFactory.compactIcon("email", 14));
        btnEmail.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnEmail.setOnAction(e -> {
            UiTaskExecutor.submitAction("return-prepare-email-" + details.no(), () -> {
                String recipient = safe(support.returnPartyEmail(details.no()));
                Path pdf = ManagedInvoicePdfService.refund(details.no(), isSales);
                MessageTemplateService.FormattedMessage msg = isSales ?
                        MessageTemplateService.formatSalesReturnEmail(details.no(), details.party(), details.date(), details.total()) :
                        MessageTemplateService.formatPurchaseReturnEmail(details.no(), details.party(), details.date(), details.total());
                String sigImg = MessageTemplateService.getSignatureImagePath();
                Path sigPath = (sigImg != null && !sigImg.isBlank()) ? Path.of(sigImg) : null;
                return new EmailTransmissionDialog.EmailDraft(
                        isSales ? "Transmit Sales Return via Email" : "Transmit Purchase Return via Email",
                        isSales ? "Direct SMTP delivery to customer billing contact" : "Direct SMTP delivery to supplier billing contact",
                        isSales ? "Sales Return" : "Purchase Return",
                        details.no(),
                        recipient,
                        msg.subject(),
                        msg.body(),
                        pdf,
                        sigPath
                );
            }, draft -> {
                Optional<EmailTransmissionDialog.TransmissionResult> opt = EmailTransmissionDialog.show(dialog.getDialogPane(), draft);
                if (opt.isEmpty()) return;
                EmailTransmissionDialog.TransmissionResult result = opt.get();
                LiveOperationDialog.run(dialog.getDialogPane(), "Sending " + (isSales ? "Sales" : "Purchase") + " Return Email", "email", "Connecting to SMTP server...", reporter -> {
                    reporter.stage(1, 2, "Connecting to SMTP and transmitting email to " + result.recipientEmail() + "...");
                    EmailService.send(result.recipientEmail(), result.subject(), result.body(), result.attachmentPdf(), result.signatureImage());
                    reporter.stage(2, 2, "Email delivery complete.");
                    reporter.log("Delivered return note #" + details.no() + " to " + result.recipientEmail());
                }, () -> {
                    ToastManager.success(dialog.getDialogPane(), "Email sent", "Return #" + details.no() + " emailed successfully to " + result.recipientEmail() + ".");
                    if (onUpdated != null) onUpdated.run();
                }, failure -> ToastManager.error(dialog.getDialogPane(), "Email delivery failed", failure.getMessage()));
            }, failure -> ToastManager.error(dialog.getDialogPane(), "Failed to prepare email draft", failure.getMessage()));
        });

        // 7. Audit Trail
        Button btnAudit = new Button("Audit Trail");
        btnAudit.setGraphic(IconFactory.compactIcon("history", 14));
        btnAudit.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnAudit.setOnAction(e -> {
            String module = isSales ? "SALES_RETURN" : "PURCHASE_RETURN";
            UiTaskExecutor.submitLatest("return-audit-" + details.no(),
                    () -> support.resolveRecord(module, details.no()),
                    res -> {
                        if (res.found() && res.recordId() != null) {
                            ActivityTimelineDialog.show(dialog.getDialogPane(), module, res.recordId().intValue(), details.no());
                        }
                    },
                    err -> ToastManager.error(dialog.getDialogPane(), "Audit record not found", err.getMessage()));
        });

        dock.getChildren().addAll(btnEditReason, btnOriginal, btnRefund, btnPdf, btnExcel, btnEmail, btnAudit);

        // Approval Buttons if pending
        if (isPending && canApprove) {
            Button btnApprove = new Button("Approve Return");
            btnApprove.setGraphic(IconFactory.compactIcon("complete", 14));
            btnApprove.getStyleClass().addAll("approved-button", "approved-success-button");
            btnApprove.setOnAction(e -> {
                try {
                    ReturnWorkflowService.approve(details.no());
                    ScreenRefreshPolicy.invalidate(isSales ? "sales-returns" : "purchase-returns");
                    NotificationService.add(details.no() + " approved.");
                    ToastManager.success(dialog.getDialogPane(), "Approved", "Return " + details.no() + " approved successfully.");
                    if (onUpdated != null) onUpdated.run();
                    dialog.close();
                } catch (Exception ex) {
                    ToastManager.error(dialog.getDialogPane(), "Approval failed", ex.getMessage());
                }
            });

            Button btnReject = new Button("Reject Return");
            btnReject.setGraphic(IconFactory.compactIcon("cancel", 14));
            btnReject.getStyleClass().addAll("approved-button", "approved-danger-button");
            btnReject.setOnAction(e -> {
                OwnedTextInputDialog td = new OwnedTextInputDialog();
                td.setTitle("Reject Return");
                td.setHeaderText("Reject " + details.no());
                td.setContentText("Reason:");
                td.showAndWait().map(String::trim).filter(r -> !r.isBlank()).ifPresent(reason -> {
                    try {
                        ReturnWorkflowService.reject(details.no(), reason);
                        ScreenRefreshPolicy.invalidate(isSales ? "sales-returns" : "purchase-returns");
                        NotificationService.add(details.no() + " rejected.");
                        ToastManager.success(dialog.getDialogPane(), "Rejected", "Return " + details.no() + " was rejected.");
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
        btnClose.getStyleClass().addAll("approved-button", "approved-secondary-button");
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
