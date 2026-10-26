package org.example.controller;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.api.recon.PurchaseReconApiClient;
import org.example.navigation.DeepLinkRouter;
import org.example.navigation.NavigationManager;
import org.example.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReconSupplierRecordsDialog extends OwnedDialog<ButtonType> {

    private final PurchaseReconApiClient.SupplierDto supplier;
    private final List<PurchaseReconApiClient.ReconDto> rawRecords;
    private final ObservableList<PurchaseReconApiClient.ReconDto> filteredRecords = FXCollections.observableArrayList();

    private final TableView<PurchaseReconApiClient.ReconDto> table = new TableView<>();
    private final TextField txtSearch = new TextField();
    private final Label lblShowing = new Label("Showing 0 records");

    public ReconSupplierRecordsDialog(Window owner, PurchaseReconApiClient.SupplierDto supplier, List<PurchaseReconApiClient.ReconDto> records) {
        this.supplier = supplier;
        this.rawRecords = records == null ? new ArrayList<>() : new ArrayList<>(records);
        initOwner(owner);
        String name = supplier != null ? safe(supplier.legalName()) : "Supplier";
        setTitle("Purchase Recon Records - " + name);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getStyleClass().addAll("approved-dialog", "erp-screen-root", "recon-records-dialog");
        pane.getButtonTypes().add(ButtonType.CLOSE);

        String subtitleText = supplier != null
                ? safe(supplier.legalName()) + " • Ref: " + safe(supplier.reference()) + " • GSTIN: " + (safe(supplier.gstin()).isBlank() ? "N/A" : supplier.gstin())
                : "Linked reconciliation transactions";

        AppDialogRenderer.configureWorkspace(this, "supplier", getTitle(),
                "Purchase Recon Records",
                subtitleText,
                "");
        PopupTableWorkspace.prepareDialog(this, 1220);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10, 14, 14, 14));
        root.setMinWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setPrefWidth(Region.USE_COMPUTED_SIZE);
        root.setMinHeight(460);

        // Header
        HBox header = buildHeader();
        root.setTop(header);

        // Center: search + table + footer
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(10, 0, 0, 0));

        HBox toolbar = buildToolbar();
        configureTable();
        HBox footer = buildFooter();

        VBox.setVgrow(table, Priority.ALWAYS);
        centerBox.getChildren().addAll(toolbar, table, footer);
        root.setCenter(centerBox);

        pane.setContent(root);

        // Apply initial filter
        applyFilter("");
    }

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().addAll("erp-page-identity-header", "approved-card");
        header.setPadding(new Insets(10, 14, 10, 14));

        StackPane icon = new StackPane();
        icon.getStyleClass().add("erp-page-header-icon");
        icon.getChildren().setAll(IconFactory.icon("supplier", 22));

        VBox text = new VBox(2);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label title = new Label("Purchase Recon Records");
        title.getStyleClass().add("section-title");

        String sub = supplier != null
                ? safe(supplier.legalName()) + " • Ref: " + safe(supplier.reference()) + " • GSTIN: " + (safe(supplier.gstin()).isBlank() ? "N/A" : supplier.gstin())
                : "Linked reconciliation transactions";
        Label subtitle = new Label(sub);
        subtitle.getStyleClass().add("muted-label");
        subtitle.setMaxWidth(Double.MAX_VALUE);
        text.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label totalBadge = new Label(rawRecords.size() + " total records");
        totalBadge.getStyleClass().addAll("metric-trend-pill", "trend-info");
        totalBadge.setMinWidth(Region.USE_PREF_SIZE);

        header.getChildren().addAll(icon, text, spacer, totalBadge);
        return header;
    }

    private HBox buildToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        txtSearch.setPromptText("Search reference, invoice no, notes, amounts...");
        txtSearch.setMinWidth(180);
        txtSearch.setPrefWidth(320);
        HBox.setHgrow(txtSearch, Priority.ALWAYS);

        StackPane searchIcon = new StackPane(IconFactory.compactIcon("search", 14));
        searchIcon.setMouseTransparent(true);
        searchIcon.setPadding(new Insets(0, 0, 0, 6));

        txtSearch.textProperty().addListener((obs, oldV, newV) -> applyFilter(newV));

        Button btnReset = new Button("Reset");
        btnReset.setMinWidth(Region.USE_PREF_SIZE);
        btnReset.setGraphic(IconFactory.compactIcon("refresh", 14));
        btnReset.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnReset.setOnAction(e -> {
            txtSearch.clear();
            applyFilter("");
        });

        Button btnExport = new Button("Export Excel");
        btnExport.setMinWidth(Region.USE_PREF_SIZE);
        btnExport.setGraphic(IconFactory.compactIcon("excel", 14));
        btnExport.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnExport.setOnAction(e -> exportToExcel());

        toolbar.getChildren().addAll(txtSearch, btnReset, btnExport);
        return toolbar;
    }

    private void configureTable() {
        PopupTableWorkspace.prepareTable(table, "erp-table-profile-responsive");
        table.getStyleClass().add("erp-purple-selection-table");
        table.setItems(filteredRecords);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // System Ref
        TableColumn<PurchaseReconApiClient.ReconDto, String> colRef = new TableColumn<>("System Ref");
        colRef.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().reference())));
        colRef.setMinWidth(120);
        colRef.setPrefWidth(140);
        colRef.setCellFactory(tc -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();
            {
                link.getStyleClass().add("table-link");
                link.setOnAction(e -> {
                    PurchaseReconApiClient.ReconDto item = getTableView().getItems().get(getIndex());
                    if (item != null && item.id() != null) {
                        openPurchaseRecon(item.id());
                    }
                });
            }
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                } else {
                    link.setText(val);
                    setGraphic(link);
                }
            }
        });

        // Supplier Invoice No
        TableColumn<PurchaseReconApiClient.ReconDto, String> colInvoice = new TableColumn<>("Supplier Invoice");
        colInvoice.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().supplierInvoiceNo())));
        colInvoice.setMinWidth(100);
        colInvoice.setPrefWidth(115);

        // Invoice Date
        TableColumn<PurchaseReconApiClient.ReconDto, String> colDate = new TableColumn<>("Invoice Date");
        colDate.setCellValueFactory(v -> new SimpleStringProperty(BusinessClock.formatDate(v.getValue().invoiceDate())));
        colDate.setMinWidth(90);
        colDate.setPrefWidth(100);

        // Taxable Value
        TableColumn<PurchaseReconApiClient.ReconDto, Number> colTaxable = new TableColumn<>("Taxable Value");
        colTaxable.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().taxableValue()));
        colTaxable.setCellFactory(x -> currencyCell());
        colTaxable.setMinWidth(95);
        colTaxable.setPrefWidth(110);

        // CGST
        TableColumn<PurchaseReconApiClient.ReconDto, Number> colCgst = new TableColumn<>("CGST");
        colCgst.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().cgst()));
        colCgst.setCellFactory(x -> currencyCell());
        colCgst.setMinWidth(75);
        colCgst.setPrefWidth(85);

        // SGST
        TableColumn<PurchaseReconApiClient.ReconDto, Number> colSgst = new TableColumn<>("SGST");
        colSgst.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().sgst()));
        colSgst.setCellFactory(x -> currencyCell());
        colSgst.setMinWidth(75);
        colSgst.setPrefWidth(85);

        // IGST
        TableColumn<PurchaseReconApiClient.ReconDto, Number> colIgst = new TableColumn<>("IGST");
        colIgst.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().igst()));
        colIgst.setCellFactory(x -> currencyCell());
        colIgst.setMinWidth(75);
        colIgst.setPrefWidth(85);

        // Other Adj
        TableColumn<PurchaseReconApiClient.ReconDto, Number> colOther = new TableColumn<>("Other Adj");
        colOther.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().otherAdjustment()));
        colOther.setCellFactory(x -> currencyCell());
        colOther.setMinWidth(75);
        colOther.setPrefWidth(85);

        // Total Invoice Value
        TableColumn<PurchaseReconApiClient.ReconDto, Number> colTotal = new TableColumn<>("Total Value");
        colTotal.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().invoiceValue()));
        colTotal.setCellFactory(x -> currencyCell());
        colTotal.setMinWidth(100);
        colTotal.setPrefWidth(115);

        // Status
        TableColumn<PurchaseReconApiClient.ReconDto, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().status())));
        colStatus.setCellFactory(x -> SemanticTableCells.status("status"));
        colStatus.setMinWidth(85);
        colStatus.setPrefWidth(95);

        // Multi-link navigation (Bank Statement / Bank Expense)
        TableColumn<PurchaseReconApiClient.ReconDto, Void> colLinks = new TableColumn<>("Linked Documents");
        colLinks.setMinWidth(160);
        colLinks.setPrefWidth(190);
        colLinks.setCellFactory(tc -> new TableCell<>() {
            private final FlowPane pane = new FlowPane(4, 4);
            @Override
            protected void updateItem(Void val, boolean empty) {
                super.updateItem(val, empty);
                pane.getChildren().clear();
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                PurchaseReconApiClient.ReconDto item = getTableView().getItems().get(getIndex());
                if (item.bankLinks() == null || item.bankLinks().isEmpty()) {
                    Label unlinked = new Label("Unlinked");
                    unlinked.getStyleClass().add("muted-label");
                    pane.getChildren().add(unlinked);
                } else {
                    for (PurchaseReconApiClient.BankLinkDto link : item.bankLinks()) {
                        if (link.statementTransactionId() != null) {
                            Hyperlink bankLnk = new Hyperlink("Bank: " + (safe(link.bankReference()).isBlank() ? "#" + link.statementTransactionId() : link.bankReference()));
                            bankLnk.setGraphic(IconFactory.compactIcon("reconcile", 12));
                            bankLnk.getStyleClass().add("table-link");
                            bankLnk.setOnAction(e -> openBankStatement(link.statementTransactionId(), link.bankReference()));
                            pane.getChildren().add(bankLnk);
                        }
                        if (link.financeEntryId() != null) {
                            Hyperlink expLnk = new Hyperlink("Exp: " + (safe(link.financeVoucherNo()).isBlank() ? "#" + link.financeEntryId() : link.financeVoucherNo()));
                            expLnk.setGraphic(IconFactory.compactIcon("expense", 12));
                            expLnk.getStyleClass().add("table-link");
                            expLnk.setOnAction(e -> openBankExpense(link.financeEntryId()));
                            pane.getChildren().add(expLnk);
                        }
                    }
                }
                setGraphic(pane);
            }
        });

        // Notes
        TableColumn<PurchaseReconApiClient.ReconDto, String> colNotes = new TableColumn<>("Notes");
        colNotes.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().notes())));
        colNotes.setMinWidth(110);
        colNotes.setPrefWidth(130);

        table.getColumns().setAll(List.of(colRef, colInvoice, colDate, colTaxable, colCgst, colSgst, colIgst, colOther, colTotal, colStatus, colLinks, colNotes));
    }

    private HBox buildFooter() {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 0, 0, 0));

        lblShowing.getStyleClass().addAll("finance-showing-label", "trend-info");

        footer.getChildren().add(lblShowing);
        return footer;
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredRecords.clear();
        if (q.isEmpty()) {
            filteredRecords.addAll(rawRecords);
        } else {
            for (PurchaseReconApiClient.ReconDto r : rawRecords) {
                if (matches(r, q)) {
                    filteredRecords.add(r);
                }
            }
        }
        lblShowing.setText("Showing " + filteredRecords.size() + " of " + rawRecords.size() + " records");
    }

    private boolean matches(PurchaseReconApiClient.ReconDto r, String q) {
        if (safe(r.reference()).toLowerCase(Locale.ROOT).contains(q)) return true;
        if (safe(r.supplierInvoiceNo()).toLowerCase(Locale.ROOT).contains(q)) return true;
        if (safe(r.invoiceDate()).toLowerCase(Locale.ROOT).contains(q)) return true;
        if (safe(r.notes()).toLowerCase(Locale.ROOT).contains(q)) return true;
        if (safe(r.status()).toLowerCase(Locale.ROOT).contains(q)) return true;
        if (String.valueOf(r.invoiceValue()).contains(q)) return true;
        if (String.valueOf(r.taxableValue()).contains(q)) return true;
        if (r.bankLinks() != null) {
            for (PurchaseReconApiClient.BankLinkDto b : r.bankLinks()) {
                if (safe(b.bankReference()).toLowerCase(Locale.ROOT).contains(q)) return true;
                if (safe(b.financeVoucherNo()).toLowerCase(Locale.ROOT).contains(q)) return true;
                if (String.valueOf(b.allocatedAmount()).contains(q)) return true;
            }
        }
        return false;
    }

    private void openPurchaseRecon(Integer reconId) {
        if (reconId == null) return;
        close();
        PurchaseReconScreenContext.select(reconId);
        DashboardController.navigateFromChild("Purchase Recon", "/fxml/pages/PurchaseRecon.fxml", null);
    }

    private void openBankStatement(Long statementTxId, String bankRef) {
        close();
        DeepLinkRouter.open("/fxml/pages/BankStatement.fxml", "BANK_STATEMENT", statementTxId, bankRef, "VIEW", "RECON_SUPPLIER");
    }

    private void openBankExpense(Integer financeEntryId) {
        close();
        BankExpenseController.requestLinkedEntry(BankExpenseController.Mode.EXPENSE, financeEntryId);
        DashboardController.navigateFromChild("Bank & Expense", "/fxml/pages/BankExpense.fxml", null);
    }

    private void exportToExcel() {
        if (filteredRecords.isEmpty()) {
            ToastManager.info(table, "Export Excel", "No records available to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        String safeName = supplier != null && !safe(supplier.legalName()).isBlank()
                ? supplier.legalName().replaceAll("[^a-zA-Z0-9_-]", "_")
                : "Supplier";
        chooser.setInitialFileName("Purchase_Recon_Records_" + safeName + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));
        Window win = getDialogPane().getScene().getWindow();
        File file = chooser.showSaveDialog(win);
        if (file == null) return;

        try (Workbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file)) {
            Sheet sheet = wb.createSheet("Purchase Recon Records");
            String[] headers = {
                    "System Ref", "Supplier Invoice", "Invoice Date", "Taxable Value",
                    "CGST", "SGST", "IGST", "Other Adj", "Invoice Total", "Linked Amount",
                    "Balance", "Status", "Notes"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowIdx = 1;
            for (PurchaseReconApiClient.ReconDto r : filteredRecords) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(r.reference()));
                row.createCell(1).setCellValue(safe(r.supplierInvoiceNo()));
                row.createCell(2).setCellValue(safe(r.invoiceDate()));
                row.createCell(3).setCellValue(r.taxableValue());
                row.createCell(4).setCellValue(r.cgst());
                row.createCell(5).setCellValue(r.sgst());
                row.createCell(6).setCellValue(r.igst());
                row.createCell(7).setCellValue(r.otherAdjustment());
                row.createCell(8).setCellValue(r.invoiceValue());
                row.createCell(9).setCellValue(r.linkedAmount());
                row.createCell(10).setCellValue(r.balance());
                row.createCell(11).setCellValue(safe(r.status()));
                row.createCell(12).setCellValue(safe(r.notes()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            ToastManager.success(table, "Export Completed", "Exported " + filteredRecords.size() + " records to " + file.getName());
        } catch (Exception ex) {
            AppDialogService.error(table, "Export Failed", "Could not export records to Excel", ex.getMessage());
        }
    }

    private static <S> TableCell<S, Number> currencyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Number val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(String.format(Locale.ENGLISH, "₹ %,.2f", val.doubleValue()));
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
