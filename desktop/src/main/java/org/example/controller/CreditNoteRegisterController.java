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

public class CreditNoteRegisterController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiCountIcon, kpiAmountIcon, kpiTaxIcon, kpiStockIcon;
    @FXML private Label kpiTotalCount, kpiTotalAmount, kpiTaxReversed, kpiRestockedCount;

    @FXML private Button btnNewCreditNote, btnRefresh;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbReasonFilter;

    @FXML private SplitPane mainSplit;
    @FXML private TableView<CreditNoteRow> tblCreditNotes;
    @FXML private TableColumn<CreditNoteRow, String> colNoteNo, colDate, colParty, colInvoiceRef, colReason;
    @FXML private TableColumn<CreditNoteRow, String> colTaxable, colTax, colTotal, colStatus, colActions;

    @FXML private VBox detailDrawer;
    @FXML private Label lblDrawerTitle, lblDrawerSubtitle;
    @FXML private Label lblDetailNoteNo, lblDetailDate, lblDetailParty, lblDetailInvoice, lblDetailReason;
    @FXML private Label lblDetailTaxable, lblDetailCgst, lblDetailSgst, lblDetailIgst, lblDetailTotal;
    @FXML private Label lblDetailStock, lblDetailStatus;
    @FXML private Button btnDrawerAudit, btnCloseDrawer;

    private final ObservableList<CreditNoteRow> masterList = FXCollections.observableArrayList();
    private final ObservableList<CreditNoteRow> filteredList = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private CreditNoteRow selectedRow;

    public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("credit_note", 22));
        if (kpiCountIcon != null) kpiCountIcon.getChildren().setAll(IconFactory.compactIcon("document", 16));
        if (kpiAmountIcon != null) kpiAmountIcon.getChildren().setAll(IconFactory.compactIcon("wallet", 16));
        if (kpiTaxIcon != null) kpiTaxIcon.getChildren().setAll(IconFactory.compactIcon("tax", 16));
        if (kpiStockIcon != null) kpiStockIcon.getChildren().setAll(IconFactory.compactIcon("inventory", 16));

        configureTableColumns();
        IconFactory.applyTableHeaderIcon(colActions, "actions");
        DynamicTableLayoutManager.install(tblCreditNotes);
        Platform.runLater(() -> {
            if (tblCreditNotes.getScene() != null && tblCreditNotes.getScene().getRoot() != null) {
                ProfessionalUiEnhancer.enhance(tblCreditNotes.getScene().getRoot());
            } else if (tblCreditNotes.getParent() != null) {
                ProfessionalUiEnhancer.enhance(tblCreditNotes.getParent());
            }
        });

        cmbReasonFilter.getItems().setAll("All Reasons", "Sales Return", "Rate Difference", "Post-Sale Discount", "Correction");
        cmbReasonFilter.setValue("All Reasons");
        cmbReasonFilter.valueProperty().addListener((obs, o, n) -> applyFilters());

        RealtimeSearchSupport.installLocal(txtSearch, this::applyFilters);

        tblCreditNotes.setRowFactory(tv -> {
            TableRow<CreditNoteRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
                    showDetails(row.getItem());
                }
            });
            return row;
        });

        RegisterUiSupport.hideDrawer(detailDrawer, mainSplit, tblCreditNotes);
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
        colNoteNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().creditNoteNo));
        colDate.setCellValueFactory(d -> new SimpleStringProperty(BusinessClock.formatDate(d.getValue().noteDate)));
        colParty.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().partyName));
        colInvoiceRef.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().originalInvoiceNo != null ? d.getValue().originalInvoiceNo : "-"));
        colReason.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().reasonCode));
        colTaxable.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().taxableAmount)));
        colTax.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().cgstAmount.add(d.getValue().sgstAmount).add(d.getValue().igstAmount))));
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().totalAmount)));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnBarcode = new Button("Barcode");
            private final Button btnCancel = new Button("Cancel");
            {
                btnBarcode.getStyleClass().addAll("approved-button", "approved-secondary-button", "btn-sm");
                btnBarcode.setOnAction(e -> {
                    CreditNoteRow row = getTableRow().getItem();
                    if (row != null) printBarcode(row);
                });
                btnCancel.getStyleClass().addAll("approved-button", "approved-secondary-button", "btn-sm");
                btnCancel.setOnAction(e -> {
                    CreditNoteRow row = getTableRow().getItem();
                    if (row != null) cancelNote(row);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    CreditNoteRow row = getTableRow().getItem();
                    btnCancel.setDisable(!"ACTIVE".equalsIgnoreCase(row.status));
                    HBox box = new HBox(6, btnBarcode, btnCancel);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblCreditNotes.setItems(filteredList);
    }

    @FXML
    public void refreshData() {
        UiTaskExecutor.submitLatest(
            "credit-notes-fetch",
            () -> {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/credit-notes")))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(b);
                HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return ApiRuntime.JSON.readValue(resp.body(), new TypeReference<List<CreditNoteRow>>() {});
                }
                return Collections.<CreditNoteRow>emptyList();
            },
            data -> {
                masterList.setAll(data != null ? data : Collections.emptyList());
                applyFilters();
                updateKpis();
            },
            err -> AppDialogService.error(tblCreditNotes, "Credit Notes", "Failed to Load Credit Notes", err.getMessage())
        );
    }

    private void applyFilters() {
        String search = txtSearch.getText() != null ? txtSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
        String reason = cmbReasonFilter.getValue();

        List<CreditNoteRow> matched = masterList.stream().filter(row -> {
            boolean matchesSearch = search.isEmpty()
                || (row.creditNoteNo != null && row.creditNoteNo.toLowerCase(Locale.ROOT).contains(search))
                || (row.partyName != null && row.partyName.toLowerCase(Locale.ROOT).contains(search))
                || (row.originalInvoiceNo != null && row.originalInvoiceNo.toLowerCase(Locale.ROOT).contains(search));
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
        int restocked = 0;

        for (CreditNoteRow r : masterList) {
            if ("ACTIVE".equalsIgnoreCase(r.status)) {
                totalAmount = totalAmount.add(r.totalAmount != null ? r.totalAmount : BigDecimal.ZERO);
                taxReversed = taxReversed.add(r.cgstAmount != null ? r.cgstAmount : BigDecimal.ZERO)
                    .add(r.sgstAmount != null ? r.sgstAmount : BigDecimal.ZERO)
                    .add(r.igstAmount != null ? r.igstAmount : BigDecimal.ZERO);
                if (r.adjustInventory) {
                    restocked++;
                }
            }
        }

        kpiTotalCount.setText(String.valueOf(count));
        kpiTotalAmount.setText(currency.format(totalAmount));
        kpiTaxReversed.setText(currency.format(taxReversed));
        kpiRestockedCount.setText(String.valueOf(restocked));
    }

    private void showDetails(CreditNoteRow row) {
        if (row == null) return;
        this.selectedRow = row;
        lblDetailNoteNo.setText(row.creditNoteNo);
        lblDetailDate.setText(row.noteDate != null ? row.noteDate : "-");
        lblDetailParty.setText(row.partyName != null ? row.partyName : "-");
        lblDetailInvoice.setText(row.originalInvoiceNo != null ? row.originalInvoiceNo : "-");
        lblDetailReason.setText(row.reasonCode != null ? row.reasonCode : "-");
        lblDetailTaxable.setText(currency.format(row.taxableAmount));
        lblDetailCgst.setText(currency.format(row.cgstAmount));
        lblDetailSgst.setText(currency.format(row.sgstAmount));
        lblDetailIgst.setText(currency.format(row.igstAmount));
        lblDetailTotal.setText(currency.format(row.totalAmount));
        lblDetailStock.setText(row.adjustInventory ? "Yes (Inventory added back)" : "No (Price diff only)");
        lblDetailStatus.setText(row.status);

        RegisterUiSupport.showDrawer(detailDrawer, mainSplit, 0.68);
    }

    @FXML
    public void closeDetails() {
        selectedRow = null;
        RegisterUiSupport.hideDrawer(detailDrawer, mainSplit, tblCreditNotes);
    }

    @FXML
    public void createCreditNote() {
        Dialog<Map<String, Object>> dialog = new OwnedDialog<>(tblCreditNotes);
        dialog.setTitle("New Sales Credit Note");
        dialog.setHeaderText("Issue a Credit Note to Customer with automatic GST tax reversal");

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dp.getStyleClass().addAll("approved-dialog", "erp-dialog");

        ComboBox<String> cmbCustomer = new ComboBox<>();
        cmbCustomer.setPromptText("Search & select customer...");
        cmbCustomer.setMaxWidth(Double.MAX_VALUE);
        PartySearchUi.install(cmbCustomer, "CUSTOMER", "Select Customer", "credit-note-party-search");

        ComboBox<String> cmbInvoiceNo = new ComboBox<>();
        cmbInvoiceNo.setPromptText("Select Original Invoice (optional)");
        cmbInvoiceNo.setMaxWidth(Double.MAX_VALUE);
        cmbInvoiceNo.setEditable(true);

        ComboBox<String> cmbReason = new ComboBox<>();
        org.example.service.LookupService lookupService = new org.example.service.LookupService();
        List<String> reasons = lookupService.getValuesByCategoryCode("CREDIT_NOTE_REASON");
        if (reasons == null || reasons.isEmpty()) {
            reasons = List.of("Sales Return", "Rate Difference", "Post-Sale Discount", "Correction");
        }
        cmbReason.getItems().setAll(reasons);
        cmbReason.setValue(reasons.get(0));
        cmbReason.setMaxWidth(Double.MAX_VALUE);

        TextField txtTaxable = new TextField();
        txtTaxable.setPromptText("Taxable Amount (₹)");

        ComboBox<String> cmbGstRate = new ComboBox<>();
        List<String> gstRates = lookupService.getValuesByCategoryCode("GST");
        if (gstRates == null || gstRates.isEmpty()) {
            gstRates = List.of("0", "5", "12", "18", "28");
        }
        cmbGstRate.getItems().setAll(gstRates);
        cmbGstRate.setValue(gstRates.contains("18") ? "18" : gstRates.get(0));
        cmbGstRate.setMaxWidth(Double.MAX_VALUE);

        CheckBox chkAdjustInventory = new CheckBox("Adjust Inventory (Restock returned items)");
        chkAdjustInventory.setSelected(true);

        // Cascading: when customer is selected, load customer invoices
        cmbCustomer.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank() || "Select Customer".equalsIgnoreCase(newVal.trim())) {
                cmbInvoiceNo.getItems().clear();
                return;
            }
            UiTaskExecutor.submitAction("credit-note-load-invoices", () -> {
                org.example.service.SalesService salesService = new org.example.service.SalesService();
                return salesService.allFiltered(null, null, newVal.trim(), null, null, null, null, null, null, null, null, null, null);
            }, invoices -> {
                List<String> invNumbers = new ArrayList<>();
                for (org.example.model.Sales s : invoices) {
                    if (s != null && s.getInvoiceNo() != null && !s.getInvoiceNo().isBlank()) {
                        invNumbers.add(s.getInvoiceNo());
                    }
                }
                cmbInvoiceNo.getItems().setAll(invNumbers);
            }, err -> {});
        });

        // When invoice is chosen, auto-fill taxable amount
        cmbInvoiceNo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                UiTaskExecutor.submitAction("credit-note-load-invoice-details", () -> {
                    org.example.service.SalesService salesService = new org.example.service.SalesService();
                    return salesService.getByInvoice(newVal.trim());
                }, sale -> {
                    if (sale != null && sale.getTotalAmount() > 0) {
                        txtTaxable.setText(String.format(Locale.ROOT, "%.2f", sale.getTotalAmount()));
                    }
                }, err -> {});
            }
        });

        VBox content = new VBox(10,
            new Label("Customer:"), cmbCustomer,
            new Label("Original Invoice No:"), cmbInvoiceNo,
            new Label("Reason:"), cmbReason,
            new Label("Taxable Amount (₹):"), txtTaxable,
            new Label("GST Rate (%):"), cmbGstRate,
            chkAdjustInventory
        );
        content.setPrefWidth(420);
        dp.setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String party = cmbCustomer.getValue();
                if (party == null || "Select Customer".equalsIgnoreCase(party.trim())) {
                    party = cmbCustomer.getEditor() != null ? cmbCustomer.getEditor().getText().trim() : "";
                }
                String inv = cmbInvoiceNo.getValue();
                if (inv == null && cmbInvoiceNo.getEditor() != null) {
                    inv = cmbInvoiceNo.getEditor().getText().trim();
                }

                Map<String, Object> req = new HashMap<>();
                req.put("partyName", party != null ? party.trim() : "");
                req.put("originalInvoiceNo", inv != null ? inv.trim() : "");
                req.put("reasonCode", cmbReason.getValue());
                BigDecimal taxableAmt = BigDecimal.ZERO;
                try {
                    taxableAmt = new BigDecimal(txtTaxable.getText().trim());
                } catch (Exception ignored) {}
                req.put("taxableAmount", taxableAmt);

                BigDecimal rate = new BigDecimal("18");
                try {
                    rate = new BigDecimal(cmbGstRate.getValue().trim());
                } catch (Exception ignored) {}
                req.put("gstRate", rate);
                req.put("adjustInventory", chkAdjustInventory.isSelected());

                // Synthesize 1 line item for accurate tax split and GL journal calculation
                BigDecimal taxTotal = taxableAmt.multiply(rate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal halfTax = taxTotal.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                Map<String, Object> line = new HashMap<>();
                line.put("itemDescription", "Credit Note Adjustment: " + cmbReason.getValue());
                line.put("quantity", BigDecimal.ONE);
                line.put("unitPrice", taxableAmt);
                line.put("gstRate", rate);
                line.put("taxableValue", taxableAmt);
                line.put("cgstAmount", halfTax);
                line.put("sgstAmount", taxTotal.subtract(halfTax));
                line.put("igstAmount", BigDecimal.ZERO);
                line.put("totalLineAmount", taxableAmt.add(taxTotal));
                req.put("lines", List.of(line));

                return req;
            }
            return null;
        });

        Optional<Map<String, Object>> result = dialog.showAndWait();
        result.ifPresent(req -> {
            UiTaskExecutor.submitAction(
                "credit-note-create",
                () -> {
                    String json = ApiRuntime.JSON.writeValueAsString(req);
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/credit-notes")))
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
                    AppDialogService.success(tblCreditNotes, "Credit Note Created", "Credit note created and posted to general ledger successfully.");
                    refreshData();
                },
                err -> AppDialogService.error(tblCreditNotes, "Credit Notes", "Creation Failed", err.getMessage())
            );
        });
    }

    private void cancelNote(CreditNoteRow row) {
        if (!AppDialogService.confirm(tblCreditNotes, "Cancel Credit Note", "Cancel Credit Note " + row.creditNoteNo + "?", "This will reverse GL postings and adjust inventory.")) {
            return;
        }

        UiTaskExecutor.submitAction(
            "credit-note-cancel-" + row.id,
            () -> {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/credit-notes/" + row.id + "/cancel")))
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
                AppDialogService.success(tblCreditNotes, "Cancelled", "Credit Note cancelled successfully.");
                refreshData();
            },
            err -> AppDialogService.error(tblCreditNotes, "Credit Notes", "Cancellation Failed", err.getMessage())
        );
    }

    @FXML
    public void auditSelected() {
        if (selectedRow != null) {
            ActivityTimelineDialog.show(tblCreditNotes, "CREDIT_NOTE", selectedRow.id != null ? selectedRow.id.intValue() : 0, selectedRow.creditNoteNo);
        }
    }

    @FXML
    public void printBarcodeSelected() {
        if (selectedRow != null) printBarcode(selectedRow);
    }

    private void printBarcode(CreditNoteRow row) {
        if (row == null) return;
        OrderDispatchBarcodeDialog.showForCreditNote(
            tblCreditNotes,
            row.creditNoteNo,
            row.noteDate,
            row.partyName,
            "",
            row.originalInvoiceNo,
            row.reasonCode,
            row.totalAmount != null ? row.totalAmount.doubleValue() : 0.0
        );
    }

    public static class CreditNoteRow {
        public Long id;
        public String creditNoteNo;
        public String noteDate;
        public String partyName;
        public String originalInvoiceNo;
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
