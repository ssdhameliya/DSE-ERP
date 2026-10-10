package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.navigation.ScreenLifecycle;
import org.example.util.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

public class DebitNoteRegisterController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiCountIcon, kpiAmountIcon, kpiTaxIcon, kpiStockIcon;
    @FXML private Label kpiTotalCount, kpiTotalAmount, kpiTaxReversed, kpiReturnedCount;

    @FXML private Button btnNewDebitNote, btnRefresh;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbReasonFilter;

    @FXML private SplitPane mainSplit;
    @FXML private TableView<DebitNoteRow> tblDebitNotes;
    @FXML private TableColumn<DebitNoteRow, String> colNoteNo, colDate, colParty, colInvoiceRef, colReason;
    @FXML private TableColumn<DebitNoteRow, String> colTaxable, colTax, colTotal, colStatus, colActions;

    @FXML private VBox detailDrawer;
    @FXML private Label lblDrawerTitle, lblDrawerSubtitle;
    @FXML private Label lblDetailNoteNo, lblDetailDate, lblDetailParty, lblDetailInvoice, lblDetailReason;
    @FXML private Label lblDetailTaxable, lblDetailCgst, lblDetailSgst, lblDetailIgst, lblDetailTotal;
    @FXML private Label lblDetailStock, lblDetailStatus;
    @FXML private Button btnDrawerAudit, btnCloseDrawer;

    private final ObservableList<DebitNoteRow> masterList = FXCollections.observableArrayList();
    private final ObservableList<DebitNoteRow> filteredList = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private DebitNoteRow selectedRow;

    public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("debit_note", 22));
        if (kpiCountIcon != null) kpiCountIcon.getChildren().setAll(IconFactory.compactIcon("document", 16));
        if (kpiAmountIcon != null) kpiAmountIcon.getChildren().setAll(IconFactory.compactIcon("wallet", 16));
        if (kpiTaxIcon != null) kpiTaxIcon.getChildren().setAll(IconFactory.compactIcon("tax", 16));
        if (kpiStockIcon != null) kpiStockIcon.getChildren().setAll(IconFactory.compactIcon("inventory", 16));

        configureTableColumns();
        DynamicTableLayoutManager.install(tblDebitNotes);

        cmbReasonFilter.getItems().setAll("All Reasons", "Purchase Return", "Rate Difference", "Supplier Discount", "Correction");
        cmbReasonFilter.setValue("All Reasons");
        cmbReasonFilter.valueProperty().addListener((obs, o, n) -> applyFilters());

        RealtimeSearchSupport.installLocal(txtSearch, this::applyFilters);

        tblDebitNotes.setRowFactory(tv -> {
            TableRow<DebitNoteRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
                    showDetails(row.getItem());
                }
            });
            return row;
        });

        RegisterUiSupport.hideDrawer(detailDrawer, mainSplit, tblDebitNotes);
        OperationalUiSupport.installEscapeClose(mainSplit, () -> detailDrawer != null && detailDrawer.isVisible(), this::closeDetails);
        refreshData();
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        refreshData();
    }

    private static String apiUrl(String path) {
        String base = ConfigManager.getDataApiBaseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    private void configureTableColumns() {
        colNoteNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().debitNoteNo));
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().noteDate));
        colParty.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().partyName));
        colInvoiceRef.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().originalPurchaseNo != null ? d.getValue().originalPurchaseNo : "-"));
        colReason.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().reasonCode));
        colTaxable.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().taxableAmount)));
        colTax.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().cgstAmount.add(d.getValue().sgstAmount).add(d.getValue().igstAmount))));
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().totalAmount)));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnCancel = new Button("Cancel");
            {
                btnCancel.getStyleClass().addAll("approved-button", "approved-secondary-button", "btn-sm");
                btnCancel.setOnAction(e -> {
                    DebitNoteRow row = getTableRow().getItem();
                    if (row != null) cancelNote(row);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    DebitNoteRow row = getTableRow().getItem();
                    btnCancel.setDisable(!"ACTIVE".equalsIgnoreCase(row.status));
                    HBox box = new HBox(6, btnCancel);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblDebitNotes.setItems(filteredList);
    }

    @FXML
    public void refreshData() {
        UiTaskExecutor.submitLatest(
            "debit-notes-fetch",
            () -> {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/debit-notes")))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(b);
                HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return ApiRuntime.JSON.readValue(resp.body(), new TypeReference<List<DebitNoteRow>>() {});
                }
                return Collections.<DebitNoteRow>emptyList();
            },
            data -> {
                masterList.setAll(data != null ? data : Collections.emptyList());
                applyFilters();
                updateKpis();
            },
            err -> AppDialogService.error(tblDebitNotes, "Debit Notes", "Failed to Load Debit Notes", err.getMessage())
        );
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
        String reason = cmbReasonFilter.getValue();

        List<DebitNoteRow> matched = masterList.stream().filter(row -> {
            boolean matchesSearch = search.isEmpty()
                || (row.debitNoteNo != null && row.debitNoteNo.toLowerCase(Locale.ROOT).contains(search))
                || (row.partyName != null && row.partyName.toLowerCase(Locale.ROOT).contains(search))
                || (row.originalPurchaseNo != null && row.originalPurchaseNo.toLowerCase(Locale.ROOT).contains(search));
            boolean matchesReason = reason == null || "All Reasons".equals(reason)
                || (row.reasonCode != null && row.reasonCode.equalsIgnoreCase(reason));
            return matchesSearch && matchesReason;
        }).toList();

        filteredList.setAll(matched);
    }

    private void updateKpis() {
        int count = masterList.size();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal taxReversed = BigDecimal.ZERO;
        int returned = 0;

        for (DebitNoteRow r : masterList) {
            if ("ACTIVE".equalsIgnoreCase(r.status)) {
                totalAmount = totalAmount.add(r.totalAmount != null ? r.totalAmount : BigDecimal.ZERO);
                taxReversed = taxReversed.add(r.cgstAmount != null ? r.cgstAmount : BigDecimal.ZERO)
                    .add(r.sgstAmount != null ? r.sgstAmount : BigDecimal.ZERO)
                    .add(r.igstAmount != null ? r.igstAmount : BigDecimal.ZERO);
                if (r.adjustInventory) {
                    returned++;
                }
            }
        }

        kpiTotalCount.setText(String.valueOf(count));
        kpiTotalAmount.setText(currency.format(totalAmount));
        kpiTaxReversed.setText(currency.format(taxReversed));
        kpiReturnedCount.setText(String.valueOf(returned));
    }

    private void showDetails(DebitNoteRow row) {
        if (row == null) return;
        this.selectedRow = row;
        lblDetailNoteNo.setText(row.debitNoteNo);
        lblDetailDate.setText(row.noteDate != null ? row.noteDate : "-");
        lblDetailParty.setText(row.partyName != null ? row.partyName : "-");
        lblDetailInvoice.setText(row.originalPurchaseNo != null ? row.originalPurchaseNo : "-");
        lblDetailReason.setText(row.reasonCode != null ? row.reasonCode : "-");
        lblDetailTaxable.setText(currency.format(row.taxableAmount));
        lblDetailCgst.setText(currency.format(row.cgstAmount));
        lblDetailSgst.setText(currency.format(row.sgstAmount));
        lblDetailIgst.setText(currency.format(row.igstAmount));
        lblDetailTotal.setText(currency.format(row.totalAmount));
        lblDetailStock.setText(row.adjustInventory ? "Yes (Inventory deducted)" : "No (Price diff only)");
        lblDetailStatus.setText(row.status);

        RegisterUiSupport.showDrawer(detailDrawer, mainSplit, 0.68);
    }

    @FXML
    public void closeDetails() {
        selectedRow = null;
        RegisterUiSupport.hideDrawer(detailDrawer, mainSplit, tblDebitNotes);
    }

    @FXML
    public void createDebitNote() {
        Dialog<Map<String, Object>> dialog = new OwnedDialog<>(tblDebitNotes);
        dialog.setTitle("New Purchase Debit Note");
        dialog.setHeaderText("Issue a Debit Note to Supplier with automatic ITC reversal");

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dp.getStyleClass().addAll("approved-dialog", "erp-dialog");

        TextField txtSupplier = new TextField();
        txtSupplier.setPromptText("Supplier Name");
        TextField txtBillNo = new TextField();
        txtBillNo.setPromptText("Original Purchase Bill No (optional)");
        ComboBox<String> cmbReason = new ComboBox<>();
        cmbReason.getItems().addAll("Purchase Return", "Rate Difference", "Supplier Discount", "Correction");
        cmbReason.setValue("Purchase Return");

        TextField txtTaxable = new TextField();
        txtTaxable.setPromptText("Taxable Amount (₹)");
        TextField txtGstRate = new TextField("18");
        txtGstRate.setPromptText("GST % (e.g. 18)");

        CheckBox chkAdjustInventory = new CheckBox("Adjust Inventory (Deduct returned stock)");
        chkAdjustInventory.setSelected(true);

        VBox content = new VBox(10,
            new Label("Supplier:"), txtSupplier,
            new Label("Original Bill No:"), txtBillNo,
            new Label("Reason:"), cmbReason,
            new Label("Taxable Amount (₹):"), txtTaxable,
            new Label("GST Rate (%):"), txtGstRate,
            chkAdjustInventory
        );
        dp.setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Map<String, Object> req = new HashMap<>();
                req.put("partyName", txtSupplier.getText().trim());
                req.put("originalPurchaseNo", txtBillNo.getText().trim());
                req.put("reasonCode", cmbReason.getValue());
                try {
                    req.put("taxableAmount", new BigDecimal(txtTaxable.getText().trim()));
                } catch (Exception e) {
                    req.put("taxableAmount", BigDecimal.ZERO);
                }
                try {
                    req.put("gstRate", new BigDecimal(txtGstRate.getText().trim()));
                } catch (Exception e) {
                    req.put("gstRate", new BigDecimal("18"));
                }
                req.put("adjustInventory", chkAdjustInventory.isSelected());
                return req;
            }
            return null;
        });

        Optional<Map<String, Object>> result = dialog.showAndWait();
        result.ifPresent(req -> {
            UiTaskExecutor.submitAction(
                "debit-note-create",
                () -> {
                    String json = ApiRuntime.JSON.writeValueAsString(req);
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/debit-notes")))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));
                    ApiSession.authorize(b);
                    HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                        return "SUCCESS";
                    }
                    throw new RuntimeException("Server error: " + resp.body());
                },
                res -> {
                    AppDialogService.success(tblDebitNotes, "Debit Note Created", "Debit note created and posted to general ledger successfully.");
                    refreshData();
                },
                err -> AppDialogService.error(tblDebitNotes, "Debit Notes", "Creation Failed", err.getMessage())
            );
        });
    }

    private void cancelNote(DebitNoteRow row) {
        if (!AppDialogService.confirm(tblDebitNotes, "Cancel Debit Note", "Cancel Debit Note " + row.debitNoteNo + "?", "This will reverse GL postings and restock items.")) {
            return;
        }

        UiTaskExecutor.submitAction(
            "debit-note-cancel-" + row.id,
            () -> {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/debit-notes/" + row.id + "/cancel")))
                    .timeout(Duration.ofSeconds(20))
                    .PUT(HttpRequest.BodyPublishers.noBody());
                ApiSession.authorize(b);
                HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return "SUCCESS";
                }
                throw new RuntimeException("Failed to cancel: " + resp.body());
            },
            res -> {
                AppDialogService.success(tblDebitNotes, "Cancelled", "Debit Note cancelled successfully.");
                refreshData();
            },
            err -> AppDialogService.error(tblDebitNotes, "Debit Notes", "Cancellation Failed", err.getMessage())
        );
    }

    @FXML
    public void auditSelected() {
        if (selectedRow != null) {
            ActivityTimelineDialog.show(tblDebitNotes, "DEBIT_NOTE", selectedRow.id != null ? selectedRow.id.intValue() : 0, selectedRow.debitNoteNo);
        }
    }

    public static class DebitNoteRow {
        public Long id;
        public String debitNoteNo;
        public String noteDate;
        public String partyName;
        public String originalPurchaseNo;
        public String reasonCode;
        public BigDecimal taxableAmount = BigDecimal.ZERO;
        public BigDecimal cgstAmount = BigDecimal.ZERO;
        public BigDecimal sgstAmount = BigDecimal.ZERO;
        public BigDecimal igstAmount = BigDecimal.ZERO;
        public BigDecimal totalAmount = BigDecimal.ZERO;
        public boolean adjustInventory;
        public String status;
    }
}
