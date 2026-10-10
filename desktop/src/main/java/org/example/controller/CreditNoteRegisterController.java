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
        DynamicTableLayoutManager.install(tblCreditNotes);

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
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().noteDate));
        colParty.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().partyName));
        colInvoiceRef.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().originalInvoiceNo != null ? d.getValue().originalInvoiceNo : "-"));
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
                    HBox box = new HBox(6, btnCancel);
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

        TextField txtCustomer = new TextField();
        txtCustomer.setPromptText("Customer Name");
        TextField txtInvoiceNo = new TextField();
        txtInvoiceNo.setPromptText("Original Invoice No (optional)");
        ComboBox<String> cmbReason = new ComboBox<>();
        cmbReason.getItems().addAll("Sales Return", "Rate Difference", "Post-Sale Discount", "Correction");
        cmbReason.setValue("Sales Return");

        TextField txtTaxable = new TextField();
        txtTaxable.setPromptText("Taxable Amount (₹)");
        TextField txtGstRate = new TextField("18");
        txtGstRate.setPromptText("GST % (e.g. 18)");

        CheckBox chkAdjustInventory = new CheckBox("Adjust Inventory (Restock returned items)");
        chkAdjustInventory.setSelected(true);

        VBox content = new VBox(10,
            new Label("Customer:"), txtCustomer,
            new Label("Original Invoice No:"), txtInvoiceNo,
            new Label("Reason:"), cmbReason,
            new Label("Taxable Amount (₹):"), txtTaxable,
            new Label("GST Rate (%):"), txtGstRate,
            chkAdjustInventory
        );
        dp.setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Map<String, Object> req = new HashMap<>();
                req.put("partyName", txtCustomer.getText().trim());
                req.put("originalInvoiceNo", txtInvoiceNo.getText().trim());
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
