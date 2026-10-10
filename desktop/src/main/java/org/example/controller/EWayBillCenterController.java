package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.api.ApiRuntime;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.navigation.ScreenLifecycle;
import org.example.util.*;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;

public class EWayBillCenterController implements ScreenLifecycle {

    @FXML private StackPane pageIcon;
    @FXML private StackPane kpiTotalIcon, kpiActiveIcon, kpiEligibleIcon, kpiValueIcon;
    @FXML private Label kpiTotalBills, kpiActiveBills, kpiPendingInvoices, kpiTotalValue;

    @FXML private Button btnRefresh;
    @FXML private TabPane tabPane;
    @FXML private Tab tabActiveBills, tabEligibleSales;

    @FXML private TextField txtSearchBills, txtSearchSales;
    @FXML private ComboBox<String> cmbStatusFilter;

    @FXML private TableView<EWayBillSummaryRow> tblBills;
    @FXML private TableColumn<EWayBillSummaryRow, String> colBillNo, colBillDate, colDocNo, colParty, colVehicle, colDistance, colAmount, colStatus, colActions;
    @FXML private Label lblBillsCount, lblBillsFilteredCount, lblBillsTotalAmount;

    @FXML private Button btnGenerateSelected;
    @FXML private TableView<EligibleSaleRow> tblEligibleSales;
    @FXML private TableColumn<EligibleSaleRow, String> colSaleInvoiceNo, colSaleDate, colSaleCustomer, colSaleGstin, colSaleCity, colSaleAmount, colSaleAction;
    @FXML private Label lblSalesCount, lblSalesFilteredCount, lblSalesTotalAmount;

    private final ObservableList<EWayBillSummaryRow> masterBills = FXCollections.observableArrayList();
    private final ObservableList<EWayBillSummaryRow> filteredBills = FXCollections.observableArrayList();

    private final ObservableList<EligibleSaleRow> masterSales = FXCollections.observableArrayList();
    private final ObservableList<EligibleSaleRow> filteredSales = FXCollections.observableArrayList();

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    public void initialize() {
        if (pageIcon != null) pageIcon.getChildren().setAll(IconFactory.icon("delivery", 22));
        if (kpiTotalIcon != null) kpiTotalIcon.getChildren().setAll(IconFactory.compactIcon("document", 16));
        if (kpiActiveIcon != null) kpiActiveIcon.getChildren().setAll(IconFactory.compactIcon("delivery", 16));
        if (kpiEligibleIcon != null) kpiEligibleIcon.getChildren().setAll(IconFactory.compactIcon("warning", 16));
        if (kpiValueIcon != null) kpiValueIcon.getChildren().setAll(IconFactory.compactIcon("wallet", 16));

        configureBillsColumns();
        configureSalesColumns();

        DynamicTableLayoutManager.install(tblBills);
        DynamicTableLayoutManager.install(tblEligibleSales);

        cmbStatusFilter.getItems().setAll("All Statuses", "GENERATED", "IN_TRANSIT", "DELIVERED", "CANCELLED");
        cmbStatusFilter.setValue("All Statuses");
        cmbStatusFilter.valueProperty().addListener((obs, o, n) -> applyBillsFilter());

        RealtimeSearchSupport.installLocal(txtSearchBills, this::applyBillsFilter);
        RealtimeSearchSupport.installLocal(txtSearchSales, this::applySalesFilter);

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

    private void configureBillsColumns() {
        colBillNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().ewayBillNo != null ? d.getValue().ewayBillNo : "-"));
        colBillDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().ewayBillDate != null ? d.getValue().ewayBillDate : "-"));
        colDocNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().docNo));
        colParty.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().toPartyName));
        colVehicle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().vehicleNo != null ? d.getValue().vehicleNo : "-"));
        colDistance.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().actualDistKm > 0 ? d.getValue().actualDistKm + " km" : "-"));
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().totalValue)));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnJson = new Button("JSON");
            private final Button btnVehicle = new Button("Vehicle");
            {
                btnJson.getStyleClass().addAll("approved-button", "approved-secondary-button", "btn-sm");
                btnVehicle.getStyleClass().addAll("approved-button", "approved-secondary-button", "btn-sm");
                btnJson.setOnAction(e -> {
                    EWayBillSummaryRow row = getTableRow().getItem();
                    if (row != null) exportJson(row);
                });
                btnVehicle.setOnAction(e -> {
                    EWayBillSummaryRow row = getTableRow().getItem();
                    if (row != null) updateVehicle(row);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(6, btnJson, btnVehicle);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblBills.setItems(filteredBills);
    }

    private void configureSalesColumns() {
        colSaleInvoiceNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().invoiceNumber));
        colSaleDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().invoiceDate));
        colSaleCustomer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().customerName));
        colSaleGstin.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().customerGstin != null ? d.getValue().customerGstin : "Unregistered"));
        colSaleCity.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().destinationCity != null ? d.getValue().destinationCity : "-"));
        colSaleAmount.setCellValueFactory(d -> new SimpleStringProperty(currency.format(d.getValue().totalAmount)));

        colSaleAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnGen = new Button("Generate");
            {
                btnGen.getStyleClass().addAll("approved-button", "approved-primary-button", "btn-sm");
                btnGen.setOnAction(e -> {
                    EligibleSaleRow row = getTableRow().getItem();
                    if (row != null) generateEWayBill(row);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(btnGen);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        tblEligibleSales.setItems(filteredSales);
    }

    @FXML
    public void refreshData() {
        UiTaskExecutor.submitLatest(
            "eway-bills-fetch",
            () -> {
                // Fetch E-way bills
                String billsUrl = apiUrl("/api/eway-bills");
                HttpRequest.Builder billsReq = HttpRequest.newBuilder(URI.create(billsUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(billsReq);
                HttpResponse<String> billsResp = ApiRuntime.HTTP.send(billsReq.build(), HttpResponse.BodyHandlers.ofString());
                List<EWayBillSummaryRow> bills = Collections.emptyList();
                if (billsResp.statusCode() == 200) {
                    bills = ApiRuntime.JSON.readValue(billsResp.body(), new TypeReference<List<EWayBillSummaryRow>>() {});
                }

                // Fetch eligible sales
                String salesUrl = apiUrl("/api/eway-bills/eligible-sales");
                HttpRequest.Builder salesReq = HttpRequest.newBuilder(URI.create(salesUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(salesReq);
                HttpResponse<String> salesResp = ApiRuntime.HTTP.send(salesReq.build(), HttpResponse.BodyHandlers.ofString());
                List<EligibleSaleRow> sales = Collections.emptyList();
                if (salesResp.statusCode() == 200) {
                    sales = ApiRuntime.JSON.readValue(salesResp.body(), new TypeReference<List<EligibleSaleRow>>() {});
                }

                return new CenterData(bills, sales);
            },
            data -> {
                masterBills.setAll(data.bills != null ? data.bills : Collections.emptyList());
                masterSales.setAll(data.sales != null ? data.sales : Collections.emptyList());
                applyBillsFilter();
                applySalesFilter();
                updateKpis();
            },
            err -> AppDialogService.error(tabPane, "E-Way Bill", "Failed to Load E-Way Data", err.getMessage())
        );
    }

    private void applyBillsFilter() {
        String query = txtSearchBills.getText() != null ? txtSearchBills.getText().trim().toLowerCase(Locale.ROOT) : "";
        String status = cmbStatusFilter.getValue();

        List<EWayBillSummaryRow> list = masterBills.stream().filter(r -> {
            boolean matchesSearch = query.isEmpty()
                || (r.ewayBillNo != null && r.ewayBillNo.toLowerCase(Locale.ROOT).contains(query))
                || (r.docNo != null && r.docNo.toLowerCase(Locale.ROOT).contains(query))
                || (r.toPartyName != null && r.toPartyName.toLowerCase(Locale.ROOT).contains(query))
                || (r.vehicleNo != null && r.vehicleNo.toLowerCase(Locale.ROOT).contains(query));
            boolean matchesStatus = status == null || "All Statuses".equals(status)
                || (r.status != null && r.status.equalsIgnoreCase(status));
            return matchesSearch && matchesStatus;
        }).toList();

        filteredBills.setAll(list);
        if (lblBillsCount != null) {
            lblBillsCount.setText("Total Records: " + masterBills.size());
        }
        if (lblBillsFilteredCount != null) {
            lblBillsFilteredCount.setText(String.valueOf(list.size()));
        }
        if (lblBillsTotalAmount != null) {
            BigDecimal total = list.stream()
                .map(b -> b.totalValue != null ? b.totalValue : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblBillsTotalAmount.setText(currency.format(total));
        }
    }

    private void applySalesFilter() {
        String query = txtSearchSales.getText() != null ? txtSearchSales.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<EligibleSaleRow> list = masterSales.stream().filter(r -> {
            return query.isEmpty()
                || (r.invoiceNumber != null && r.invoiceNumber.toLowerCase(Locale.ROOT).contains(query))
                || (r.customerName != null && r.customerName.toLowerCase(Locale.ROOT).contains(query))
                || (r.customerGstin != null && r.customerGstin.toLowerCase(Locale.ROOT).contains(query));
        }).toList();

        filteredSales.setAll(list);
        if (lblSalesCount != null) {
            lblSalesCount.setText("Eligible Invoices: " + masterSales.size());
        }
        if (lblSalesFilteredCount != null) {
            lblSalesFilteredCount.setText(String.valueOf(list.size()));
        }
        if (lblSalesTotalAmount != null) {
            BigDecimal total = list.stream()
                .map(s -> s.totalAmount != null ? s.totalAmount : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblSalesTotalAmount.setText(currency.format(total));
        }
    }

    private void updateKpis() {
        kpiTotalBills.setText(String.valueOf(masterBills.size()));
        long inTransit = masterBills.stream().filter(b -> "IN_TRANSIT".equalsIgnoreCase(b.status) || "GENERATED".equalsIgnoreCase(b.status)).count();
        kpiActiveBills.setText(String.valueOf(inTransit));
        kpiPendingInvoices.setText(String.valueOf(masterSales.size()));

        BigDecimal totalVal = masterBills.stream()
            .map(b -> b.totalValue != null ? b.totalValue : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        kpiTotalValue.setText(currency.format(totalVal));
    }

    @FXML
    public void generateForSelectedSale() {
        EligibleSaleRow selected = tblEligibleSales.getSelectionModel().getSelectedItem();
        if (selected != null) {
            generateEWayBill(selected);
        } else {
            AppDialogService.warning(tblEligibleSales, "E-Way Bill", "Selection Required", "Please select an invoice from the table to generate an E-Way Bill.");
        }
    }

    private void generateEWayBill(EligibleSaleRow sale) {
        Dialog<Map<String, Object>> dialog = new OwnedDialog<>(tblEligibleSales);
        dialog.setTitle("Generate E-Way Bill");
        dialog.setHeaderText("Consignment for Invoice " + sale.invoiceNumber + " (₹ " + currency.format(sale.totalAmount) + ")");

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dp.getStyleClass().addAll("approved-dialog", "erp-dialog");

        TextField txtTransporterId = new TextField();
        txtTransporterId.setPromptText("Transporter GSTIN / ID");
        TextField txtTransporterName = new TextField();
        txtTransporterName.setPromptText("Transporter Name");
        TextField txtVehicleNo = new TextField();
        txtVehicleNo.setPromptText("Vehicle Number (e.g. MH12AB1234)");
        TextField txtDistance = new TextField("50");
        txtDistance.setPromptText("Approx Distance in KM");

        ComboBox<String> cmbMode = new ComboBox<>();
        cmbMode.getItems().addAll("Road", "Rail", "Air", "Ship");
        cmbMode.setValue("Road");

        VBox content = new VBox(10,
            new Label("Transportation Mode:"), cmbMode,
            new Label("Vehicle Number:"), txtVehicleNo,
            new Label("Distance (KM):"), txtDistance,
            new Label("Transporter Name:"), txtTransporterName,
            new Label("Transporter GSTIN / ID:"), txtTransporterId
        );
        dp.setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Map<String, Object> req = new HashMap<>();
                req.put("transMode", cmbMode.getValue());
                req.put("vehicleNo", txtVehicleNo.getText().trim());
                try {
                    req.put("distanceKm", Integer.parseInt(txtDistance.getText().trim()));
                } catch (Exception e) {
                    req.put("distanceKm", 50);
                }
                req.put("transporterName", txtTransporterName.getText().trim());
                req.put("transporterId", txtTransporterId.getText().trim());
                return req;
            }
            return null;
        });

        Optional<Map<String, Object>> res = dialog.showAndWait();
        res.ifPresent(req -> {
            UiTaskExecutor.submitAction(
                "eway-bill-generate",
                () -> {
                    String json = ApiRuntime.JSON.writeValueAsString(req);
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/eway-bills/generate/" + sale.saleId)))
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
                success -> {
                    AppDialogService.success(tabPane, "E-Way Bill Generated", "E-Way Bill registered and assigned to invoice " + sale.invoiceNumber);
                    refreshData();
                    tabPane.getSelectionModel().select(tabActiveBills);
                },
                err -> AppDialogService.error(tabPane, "E-Way Bill", "Generation Failed", err.getMessage())
            );
        });
    }

    private void updateVehicle(EWayBillSummaryRow bill) {
        Dialog<Map<String, Object>> dialog = new OwnedDialog<>(tblBills);
        dialog.setTitle("Update Vehicle Details (Part-B)");
        dialog.setHeaderText("Update transport vehicle for E-Way Bill: " + bill.ewayBillNo);

        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dp.getStyleClass().addAll("approved-dialog", "erp-dialog");

        TextField txtNewVehicle = new TextField(bill.vehicleNo != null ? bill.vehicleNo : "");
        txtNewVehicle.setPromptText("New Vehicle Number");

        ComboBox<String> cmbReason = new ComboBox<>();
        cmbReason.getItems().addAll("Transshipment", "Vehicle Breakdown", "First Time Update", "Consignment Split");
        cmbReason.setValue("Transshipment");

        TextField txtPlace = new TextField();
        txtPlace.setPromptText("Current Location / Transshipment Place");

        VBox content = new VBox(10,
            new Label("New Vehicle No:"), txtNewVehicle,
            new Label("Reason for Change:"), cmbReason,
            new Label("Current Location:"), txtPlace
        );
        dp.setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Map<String, Object> req = new HashMap<>();
                req.put("vehicleNo", txtNewVehicle.getText().trim());
                req.put("reasonCode", cmbReason.getValue());
                req.put("fromPlace", txtPlace.getText().trim());
                return req;
            }
            return null;
        });

        Optional<Map<String, Object>> res = dialog.showAndWait();
        res.ifPresent(req -> {
            UiTaskExecutor.submitAction(
                "eway-bill-update-vehicle-" + bill.id,
                () -> {
                    String json = ApiRuntime.JSON.writeValueAsString(req);
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl("/api/eway-bills/" + bill.id + "/update-vehicle")))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json));
                    ApiSession.authorize(b);
                    HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        return "SUCCESS";
                    }
                    throw new RuntimeException("Server error: " + resp.body());
                },
                success -> {
                    AppDialogService.success(tblBills, "Vehicle Updated", "Vehicle movement record updated in compliance system.");
                    refreshData();
                },
                err -> AppDialogService.error(tblBills, "E-Way Bill", "Update Failed", err.getMessage())
            );
        });
    }

    private void exportJson(EWayBillSummaryRow bill) {
        UiTaskExecutor.submitLatest(
            "eway-bill-json-" + bill.id,
            () -> {
                String url = apiUrl("/api/eway-bills/" + bill.id + "/export-json");
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
                ApiSession.authorize(b);
                HttpResponse<String> resp = ApiRuntime.HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return resp.body();
                }
                throw new RuntimeException("Failed to generate JSON: " + ApiRuntime.errorMessage(resp.statusCode(), resp.body()));
            },
            jsonContent -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Save NIC E-Way Bill JSON");
                fc.setInitialFileName("EWayBill_" + bill.docNo.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
                File file = fc.showSaveDialog(tblBills.getScene().getWindow());
                if (file != null) {
                    try {
                        Files.writeString(file.toPath(), jsonContent);
                        AppDialogService.success(tblBills, "Export Complete", "NIC schema JSON saved to: " + file.getAbsolutePath());
                    } catch (Exception e) {
                        AppDialogService.error(tblBills, "E-Way Bill", "Save Failed", e.getMessage());
                    }
                }
            },
            err -> AppDialogService.error(tblBills, "E-Way Bill", "Export Failed", err.getMessage())
        );
    }

    public static class EWayBillSummaryRow {
        public Long id;
        public String ewayBillNo;
        public String ewayBillDate;
        public String docNo;
        public String docDate;
        public String toPartyName;
        public String vehicleNo;
        public int actualDistKm;
        public BigDecimal totalValue = BigDecimal.ZERO;
        public String validUntil;
        public String status;
    }

    public static class EligibleSaleRow {
        public Long saleId;
        public String invoiceNumber;
        public String invoiceDate;
        public String customerName;
        public String customerGstin;
        public String destinationCity;
        public BigDecimal totalAmount = BigDecimal.ZERO;
    }

    private record CenterData(List<EWayBillSummaryRow> bills, List<EligibleSaleRow> sales) {}
}
