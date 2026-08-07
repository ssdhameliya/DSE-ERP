package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedTextInputDialog;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.example.model.*;
import org.example.dao.LookupDAO;

import org.example.navigation.NavigationManager;

import org.example.service.ItemService;
import org.example.service.NotificationService;
import org.example.service.PartyService;
import org.example.service.SalesService;
import org.example.util.IconFactory;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;

import java.time.LocalDate;
import java.util.List;

public class SalesController {
    @FXML private Button btnAddCustomer;

    @FXML
    private VBox salesEntryRoot;

    @FXML
    private TextField txtInvoiceNo;

    @FXML
    private TextField txtQuantity;

    @FXML
    private TextField txtRate;

    @FXML
    private TextField txtGST;

    @FXML
    private TextField txtLineDiscount;

    @FXML
    private DatePicker dpInvoiceDate;
    @FXML private DatePicker dpDueDate;
    @FXML private ComboBox<String> cmbSalesPerson,cmbPaymentTerms;
    @FXML private ComboBox<String> cmbGstType,cmbTransporter,cmbChargeType,cmbDoorDelivery;
    @FXML private TextField txtOtherCharges,txtTransport,txtReference,txtAttachment;
    @FXML private TextField txtVehicleNumber,txtContactPerson,txtContactPersonMobile,txtTransportNote,txtOrderNo,txtGstin,txtChargeAmount;
    @FXML private CheckBox chkSameAsBilling;
    @FXML private TextArea txtInvoiceMessage;

    @FXML
    private ComboBox<Party> cmbCustomer;

    @FXML
    private ComboBox<Item> cmbItem;

    @FXML
    private TextArea txtRemarks;
    @FXML private TextArea txtBillingAddress;
    /** Displays the selected customer's billing address using the same control pattern as Create Purchase. */
    @FXML private ComboBox<String> cmbBillingAddress;
    @FXML private TextArea txtDeliveryAddress;

    @FXML
    private Label lblInvoiceDisplay;

    @FXML
    private Label lblNetAmount;

    @FXML
    private Label lblGst;

    @FXML
    private Label lblDiscount;

    @FXML
    private Label lblGrandTotal;

    @FXML private Label lblTotalItems, lblBottomDiscount, lblBottomTax, lblBottomCharges, lblBottomNet, lblTaxableAmount, lblChargeCaption, lblCharges;

    @FXML
    private TableView<SalesLine> tableLines;

    @FXML
    private TableColumn<SalesLine, String> colItem;

    @FXML
    private TableColumn<SalesLine, Double> colQuantity;

    @FXML
    private TableColumn<SalesLine, Double> colRate;

    @FXML
    private TableColumn<SalesLine, Double> colGst;

    @FXML
    private TableColumn<SalesLine, Double> colDiscount;

    @FXML
    private TableColumn<SalesLine, Double> colDiscountAmount;

    @FXML
    private TableColumn<SalesLine, Double> colGstAmount;

    @FXML
    private TableColumn<SalesLine, Double> colNetAmount;

    @FXML
    private TableColumn<SalesLine, Double> colTotal;

    @FXML
    private Button btnAddLine;
    @FXML private Button btnRemoveLine, btnSaveDraft;



    @FXML
    private Button btnSaveSale;

    //-------------------------------------------------------
    // Services
    //-------------------------------------------------------

    private final ItemService itemService =
        new ItemService();

    private final PartyService partyService =
        new PartyService();

    private final SalesService salesService =
        new SalesService();

    private final LookupDAO lookupDAO = new LookupDAO();

    //-------------------------------------------------------
    // Editing
    //-------------------------------------------------------

    private Sales editingSale = null;

    private SalesLine editingLine = null;

    private int editingIndex = -1;

    //-------------------------------------------------------
    // Initialize
    //-------------------------------------------------------

    @FXML
    public void initialize() {
        if (btnAddCustomer != null) { btnAddCustomer.setGraphic(IconFactory.compactIcon("customer", 20)); btnAddCustomer.getProperties().put("erp-icon-preserve", true); }
        tableLines.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        configureExplicitTableHeaderIcons();

        setupTable();
        configureEmptyState();

        setupAmountFormatting();

        tableLines.setEditable(true);

        setupEditableColumns();
        Platform.runLater(this::decorateActions);
        cmbSalesPerson.getItems().setAll("Admin","Ajay Shah","Rahul Mehta");cmbSalesPerson.setValue("Admin");
        cmbPaymentTerms.getItems().setAll(lookupDAO.getValuesByCategoryCode("PAYMENT_TERMS"));
        if (cmbPaymentTerms.getItems().contains("15 Days")) cmbPaymentTerms.setValue("15 Days");
        else if (!cmbPaymentTerms.getItems().isEmpty()) cmbPaymentTerms.getSelectionModel().selectFirst();
        if (cmbChargeType != null) cmbChargeType.getItems().setAll(lookupDAO.getValuesByCategoryCode("CHARGES"));
        if (cmbDoorDelivery != null) { cmbDoorDelivery.getItems().setAll("Yes","No"); cmbDoorDelivery.setValue("No"); }
        dpInvoiceDate.valueProperty().addListener((o,a,b)->syncPoDateFromPaymentTerms());
        cmbPaymentTerms.valueProperty().addListener((o,a,b)->syncPoDateFromPaymentTerms());

        // Delivery Address can follow Billing Address or be entered independently.
        if (chkSameAsBilling != null) {
            chkSameAsBilling.selectedProperty().addListener((o, oldValue, same) -> syncDeliveryAddressState());
        }
        if (cmbBillingAddress != null) {
            cmbBillingAddress.valueProperty().addListener((o, oldValue, address) -> {
                if (chkSameAsBilling != null && chkSameAsBilling.isSelected()) {
                    txtDeliveryAddress.setText(address == null ? "" : address);
                }
            });
        }

        // Master-driven values use stable category codes, so renaming the visible
        // category in Master Data does not break Create Sale.
        cmbGstType.getItems().setAll(lookupDAO.getValuesByCategoryCode("GST_TYPE"));
        if (!cmbGstType.getItems().isEmpty()) cmbGstType.getSelectionModel().selectFirst();
        cmbTransporter.getItems().setAll(lookupDAO.getValuesByCategoryCode("TRANSPORTER"));
        cmbGstType.valueProperty().addListener((o,a,b) -> updateGstHeaders());
        updateGstHeaders();

        if (txtChargeAmount != null) txtChargeAmount.textProperty().addListener((o,a,b)->recalculate());

        tableLines.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldLine, newLine) -> {

                if (newLine == null)
                    return;

                editingLine = newLine;

                editingIndex =
                    tableLines.getSelectionModel()
                        .getSelectedIndex();

                txtQuantity.setText(
                    String.valueOf(
                        newLine.getQuantity()));

                txtRate.setText(
                    String.valueOf(
                        newLine.getRate()));

                txtGST.setText(
                    String.valueOf(
                        newLine.getGstPercent()));

                txtLineDiscount.setText(String.valueOf(newLine.getDiscountPercent()));

                for (Item item : cmbItem.getItems()) {

                    if (item.getItemCode()
                        .equals(newLine.getItemCode())) {

                        cmbItem.getSelectionModel()
                            .select(item);

                        break;
                    }
                }

            });

        //-------------------------------------------------------
        // Load Customers
        //-------------------------------------------------------

        cmbCustomer.setItems(

            FXCollections.observableArrayList(

                partyService.getByType("CUSTOMER")

            )

        );

        //-------------------------------------------------------
        // Load Items
        //-------------------------------------------------------

        cmbItem.setItems(

            FXCollections.observableArrayList(

                itemService.getAll()

            )

        );

        //-------------------------------------------------------
        // Customer Combo
        //-------------------------------------------------------

        cmbCustomer.setCellFactory(list ->
            new ListCell<>() {

                @Override
                protected void updateItem(
                    Party party,
                    boolean empty) {

                    super.updateItem(party, empty);

                    setText(

                        empty || party == null

                            ? null

                            : party.getPartyCode()
                              + " - "
                              + party.getName()

                    );

                }

            });

        cmbCustomer.setButtonCell(
            new ListCell<>() {

                @Override
                protected void updateItem(
                    Party party,
                    boolean empty) {

                    super.updateItem(party, empty);

                    setText(

                        empty || party == null

                            ? null

                            : party.getPartyCode()
                              + " - "
                              + party.getName()

                    );

                }

            });

        cmbCustomer.valueProperty().addListener((observable, oldCustomer, customer) -> {
            if (customer == null) {
                txtBillingAddress.clear();
                if (cmbBillingAddress != null) cmbBillingAddress.getItems().clear();
                if (txtGstin != null) txtGstin.clear();
                if (editingSale == null && txtDeliveryAddress != null) txtDeliveryAddress.clear();
                return;
            }
            String address = customer.getAddress() == null ? "" : customer.getAddress().trim();
            txtBillingAddress.setText(address);
            java.util.List<String> addresses = address.isBlank()
                ? java.util.List.of() : java.util.List.of(address);
            if (cmbBillingAddress != null) {
                cmbBillingAddress.getItems().setAll(addresses);
                cmbBillingAddress.getSelectionModel().select(address);
            }
            if (txtGstin != null && (editingSale == null || txtGstin.getText() == null || txtGstin.getText().isBlank())) {
                txtGstin.setText(customer.getGstin() == null ? "" : customer.getGstin());
            }
            if (editingSale == null && chkSameAsBilling != null) chkSameAsBilling.setSelected(true);
            syncDeliveryAddressState();
        });

        //-------------------------------------------------------
        // Item Combo
        //-------------------------------------------------------

        cmbItem.setCellFactory(list ->
            new ListCell<>() {

                @Override
                protected void updateItem(
                    Item item,
                    boolean empty) {

                    super.updateItem(item, empty);

                    setText(

                        empty || item == null

                            ? null

                            : item.getItemCode()
                              + " - "
                              + item.getDescription()

                    );

                }

            });

        cmbItem.setButtonCell(
            new ListCell<>() {

                @Override
                protected void updateItem(
                    Item item,
                    boolean empty) {

                    super.updateItem(item, empty);

                    setText(

                        empty || item == null

                            ? null

                            : item.getItemCode()
                              + " - "
                              + item.getDescription()

                    );

                }

            });

        // Selecting an item always uses current Item Master selling price and GST rate.
        cmbItem.valueProperty().addListener((observable, oldItem, item) -> {
            if (item != null) {
                txtRate.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getSellingPrice()));
                txtGST.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getGst()));
                txtLineDiscount.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getDiscountPercent()));
            }
        });

        newSale();

    }

    private void updateGstHeaders() {
        String type = cmbGstType == null ? "" : cmbGstType.getValue();
        boolean igst = type != null && type.trim().equalsIgnoreCase("IGST");
        String percentLabel = igst ? "IGST %" : "GST %";
        String amountLabel = igst ? "IGST Amount (₹)" : "GST Amount (₹)";
        if (colGst != null) colGst.setText(percentLabel);
        if (colGstAmount != null) colGstAmount.setText(amountLabel);
        if (txtGST != null) txtGST.setPromptText(percentLabel);
    }

    /** Opens the shared themed customer editor and refreshes the sale form. */
    @FXML
    private void addCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/PartyDialog.fxml"));
            Parent root = loader.load();
            loader.<PartyDialogController>getController().configure("CUSTOMER", null);
            Stage dialog = new Stage();
            PlatformUiSupport.configureDialogStage(dialog, cmbCustomer, "Add Customer", true);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene);
            dialog.setScene(scene);
            dialog.showAndWait();
            Party selected = cmbCustomer.getValue();
            cmbCustomer.getItems().setAll(partyService.getByType("CUSTOMER"));
            if (selected != null) cmbCustomer.getSelectionModel().select(selected);
        } catch (Exception ex) {
            new OwnedAlert(Alert.AlertType.ERROR,
                "Unable to open customer form: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    //-------------------------------------------------------
    // Setup Table
    //-------------------------------------------------------

    private void setupTable() {

        colItem.setCellValueFactory(
            new PropertyValueFactory<>("itemDescription"));

        colQuantity.setCellValueFactory(
            new PropertyValueFactory<>("quantity"));

        colRate.setCellValueFactory(
            new PropertyValueFactory<>("rate"));

        colGst.setCellValueFactory(
            new PropertyValueFactory<>("gstPercent"));

        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
        colDiscountAmount.setCellValueFactory(new PropertyValueFactory<>("discountAmount"));

        colGstAmount.setCellValueFactory(
            new PropertyValueFactory<>("gstAmount"));

        colNetAmount.setCellValueFactory(
            new PropertyValueFactory<>("netAmount"));

        colTotal.setCellValueFactory(
            new PropertyValueFactory<>("totalAmount"));

    }

    //-------------------------------------------------------
    // Amount Formatting
    //-------------------------------------------------------

    private void setupAmountFormatting() {

        colQuantity.setCellFactory(column ->
            new TextFieldTableCell<>(
                new DoubleStringConverter()
            ));

        colRate.setCellFactory(column ->
            new TextFieldTableCell<>(
                new DoubleStringConverter()
            ));

        colGst.setCellFactory(column ->
            new TextFieldTableCell<>(
                new DoubleStringConverter()
            ));

        colDiscount.setCellFactory(column ->
            new TextFieldTableCell<>(new DoubleStringConverter()));

        colDiscountAmount.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("₹ %.2f", value));
            }
        });

        colGstAmount.setCellFactory(column ->
            new TableCell<>() {

                @Override
                protected void updateItem(Double value, boolean empty) {

                    super.updateItem(value, empty);

                    if (empty || value == null) {

                        setText(null);

                    } else {

                        setText(
                            String.format("₹ %.2f", value)
                        );

                    }

                }

            });

        colNetAmount.setCellFactory(column ->
            new TableCell<>() {

                @Override
                protected void updateItem(Double value, boolean empty) {

                    super.updateItem(value, empty);

                    if (empty || value == null) {

                        setText(null);

                    } else {

                        setText(
                            String.format("₹ %.2f", value)
                        );

                    }

                }

            });

        colTotal.setCellFactory(column ->
            new TableCell<>() {

                @Override
                protected void updateItem(Double value, boolean empty) {

                    super.updateItem(value, empty);

                    if (empty || value == null) {

                        setText(null);

                    } else {

                        setText(
                            String.format("₹ %.2f", value)
                        );

                    }

                }

            });

    }

    //-------------------------------------------------------
    // Editable Columns
    //-------------------------------------------------------

    private void setupEditableColumns() {

        colQuantity.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            ));

        colQuantity.setOnEditCommit(event -> {

            SalesLine line = event.getRowValue();

            line.setQuantity(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });

        colRate.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            ));

        colRate.setOnEditCommit(event -> {

            SalesLine line = event.getRowValue();

            line.setRate(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });

        colGst.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            ));

        colGst.setOnEditCommit(event -> {

            SalesLine line = event.getRowValue();

            line.setGstPercent(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });

        colDiscount.setOnEditCommit(event -> {
            SalesLine line = event.getRowValue();
            double value = event.getNewValue() == null ? 0 : event.getNewValue();
            line.setDiscountPercent(Math.max(0, Math.min(100, value)));
            recalculateLine(line);
            tableLines.refresh();
            recalculate();
        });

    }

    //-------------------------------------------------------
    // Recalculate One Line
    //-------------------------------------------------------

    private void recalculateLine(SalesLine line) {

        line.recalculate();

    }

    //--------------------------------------------------
// SAVE SALE
//--------------------------------------------------

    @FXML
    private void saveSale() {

        Sales sale = buildSale();

        if (sale == null)
            return;

        try {

            if (editingSale != null) {

                sale.setId(editingSale.getId());

                salesService.update(sale);

                NotificationService.add(
                    "Sales "
                        + sale.getInvoiceNo()
                        + " updated"
                );

            } else {

                salesService.save(sale);

                NotificationService.add(
                    "Sales "
                        + sale.getInvoiceNo()
                        + " saved"
                );

            }

            new OwnedAlert(
                Alert.AlertType.INFORMATION,
                "Sales saved successfully"
            ).showAndWait();

            NavigationManager.getInstance()
                .loadPage("/fxml/pages/SalesList.fxml");

        }
        catch (Exception e) {

            new OwnedAlert(
                Alert.AlertType.ERROR,
                e.getMessage()
            ).showAndWait();

        }

    }


//--------------------------------------------------
// BUILD SALES OBJECT
//--------------------------------------------------

    private Sales buildSale() {

        if (dpInvoiceDate.getValue() == null) {

            warn("Select invoice date");

            return null;

        }

        if (cmbCustomer.getValue() == null) {

            warn("Select customer");

            return null;

        }

        if (txtOrderNo != null && (txtOrderNo.getText() == null || txtOrderNo.getText().isBlank())) {
            txtOrderNo.setText(salesService.nextOrderNo());
        }

        if (txtDeliveryAddress != null && (txtDeliveryAddress.getText() == null || txtDeliveryAddress.getText().isBlank())) {
            warn("Enter delivery address");
            return null;
        }

        if (tableLines.getItems().isEmpty()) {

            warn("Add items");

            return null;

        }

        Sales sale = new Sales();

        sale.setInvoiceNo(
            txtInvoiceNo.getText()
        );

        sale.setInvoiceDate(
            dpInvoiceDate.getValue()
        );

        sale.setCustomer(
            cmbCustomer.getValue()
        );

        sale.setLines(
            List.copyOf(
                tableLines.getItems()
            )
        );

        double net =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getNetAmount
                )
                .sum();

        double discount = tableLines.getItems().stream().mapToDouble(SalesLine::getDiscountAmount).sum();

        double gst =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getGstAmount
                )
                .sum();

        double charges = number(txtChargeAmount);
        double total = net + gst + charges;

        sale.setSubtotal(net);
        sale.setDiscountAmount(discount);

        sale.setGstAmount(gst);

        sale.setTotalAmount(total);

        sale.setRemarks(txtRemarks == null ? "" : txtRemarks.getText());
        sale.setDueDate(calculatePaymentDueDate(dpInvoiceDate.getValue(), cmbPaymentTerms.getValue()));
        sale.setPoDate(dpDueDate == null ? null : dpDueDate.getValue());
        sale.setOrderNo(txtOrderNo == null ? "" : txtOrderNo.getText());
        sale.setSalesperson(cmbSalesPerson.getValue());
        sale.setNotes("");
        String billing = cmbBillingAddress == null ? txtBillingAddress.getText() : cmbBillingAddress.getValue();
        String shipping = txtDeliveryAddress == null ? "" : txtDeliveryAddress.getText();
        sale.setBillingAddress(billing == null ? "" : billing);
        sale.setDeliveryAddress(shipping == null ? "" : shipping);
        sale.setGstin(txtGstin == null ? "" : txtGstin.getText());
        sale.setPaymentTerms(cmbPaymentTerms.getValue());
        sale.setGstType(cmbGstType == null ? "" : cmbGstType.getValue());
        sale.setTransporter(cmbTransporter == null ? "" : cmbTransporter.getValue());
        sale.setDoorDelivery(cmbDoorDelivery == null || cmbDoorDelivery.getValue() == null ? "" : cmbDoorDelivery.getValue());
        sale.setVehicleNumber(txtVehicleNumber == null ? "" : txtVehicleNumber.getText());
        sale.setContactPerson(txtContactPerson == null ? "" : txtContactPerson.getText());
        sale.setContactPersonMobile(txtContactPersonMobile == null ? "" : txtContactPersonMobile.getText());
        sale.setChargeType(cmbChargeType == null || cmbChargeType.getValue() == null ? "" : cmbChargeType.getValue());
        sale.setChargeAmount(charges);
        sale.setTransportNote(txtTransportNote == null ? "" : txtTransportNote.getText());
        sale.setReferenceNo("");

        return sale;

    }


//--------------------------------------------------
// NEW SALE
//--------------------------------------------------

    private LocalDate calculatePaymentDueDate(LocalDate invoiceDate, String terms) {
        if (invoiceDate == null) return null;
        if (terms == null || terms.isBlank() || terms.equalsIgnoreCase("Due on Receipt")) return invoiceDate;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(terms);
        return matcher.find() ? invoiceDate.plusDays(Integer.parseInt(matcher.group(1))) : invoiceDate;
    }

    private void syncPoDateFromPaymentTerms() {
        if (dpDueDate == null) return;
        dpDueDate.setValue(calculatePaymentDueDate(
            dpInvoiceDate == null ? null : dpInvoiceDate.getValue(),
            cmbPaymentTerms == null ? null : cmbPaymentTerms.getValue()));
    }

    private void syncDeliveryAddressState() {
        if (txtDeliveryAddress == null) return;
        boolean same = chkSameAsBilling != null && chkSameAsBilling.isSelected();
        if (same) {
            String billing = cmbBillingAddress == null ? txtBillingAddress.getText() : cmbBillingAddress.getValue();
            txtDeliveryAddress.setText(billing == null ? "" : billing);
        }
        txtDeliveryAddress.setEditable(!same);
        txtDeliveryAddress.setDisable(false);
        txtDeliveryAddress.setOpacity(same ? 0.88 : 1.0);
    }

    private void newSale() {

        editingSale = null;

        txtInvoiceNo.setText(
            salesService.nextInvoiceNo()
        );

        dpInvoiceDate.setValue(
            LocalDate.now()
        );
        syncPoDateFromPaymentTerms();

        cmbCustomer.setValue(null);

        cmbItem.setValue(null);

        txtQuantity.clear();

        txtRate.clear();

        txtGST.clear();
        txtLineDiscount.clear();

        txtRemarks.clear();
        txtBillingAddress.clear();
        txtDeliveryAddress.clear();
        if (cmbBillingAddress != null) cmbBillingAddress.getItems().clear();
        if (chkSameAsBilling != null) chkSameAsBilling.setSelected(true);
        if (txtOrderNo != null) txtOrderNo.setText(salesService.nextOrderNo());
        if (txtGstin != null) txtGstin.clear();
        if (cmbGstType != null && !cmbGstType.getItems().isEmpty()) cmbGstType.getSelectionModel().selectFirst();
        if (cmbTransporter != null) cmbTransporter.setValue(null);
        if (cmbDoorDelivery != null) cmbDoorDelivery.setValue("No");
        if (txtVehicleNumber != null) txtVehicleNumber.clear();
        if (txtContactPerson != null) txtContactPerson.clear();
        if (txtContactPersonMobile != null) txtContactPersonMobile.clear();
        if (txtTransportNote != null) txtTransportNote.clear();
        if (cmbChargeType != null) cmbChargeType.setValue(null);
        if (txtChargeAmount != null) txtChargeAmount.setText("0");

        tableLines.getItems().clear();

        recalculate();

    }
    //--------------------------------------------------
// RECALCULATE TOTALS
//--------------------------------------------------

    private void recalculate() {

        double net =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getNetAmount
                )
                .sum();

        double discount = tableLines.getItems().stream().mapToDouble(SalesLine::getDiscountAmount).sum();

        double gst =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getGstAmount
                )
                .sum();

        double charges = number(txtChargeAmount);
        double total = net + gst + charges;

        lblNetAmount.setText(
            String.format("₹ %.2f", net)
        );

        lblDiscount.setText(String.format("₹ %.2f", discount));

        lblGst.setText(
            String.format("₹ %.2f", gst)
        );

        lblGrandTotal.setText(
            String.format("₹ %.2f", total)
        );

        if (lblTotalItems != null) lblTotalItems.setText(Integer.toString(tableLines.getItems().size()));
        if (lblBottomDiscount != null) lblBottomDiscount.setText(String.format("₹ %.2f", discount));
        if (lblBottomTax != null) lblBottomTax.setText(String.format("₹ %.2f", gst));
        if (lblBottomCharges != null) lblBottomCharges.setText(String.format("₹ %.2f", charges));
        if (lblBottomNet != null) lblBottomNet.setText(String.format("₹ %.2f", total));
        if (lblTaxableAmount != null) lblTaxableAmount.setText(String.format("₹ %.2f", net));
        if (lblCharges != null) lblCharges.setText(String.format("₹ %.2f", charges));
        if (lblChargeCaption != null) {
            String chargeType = cmbChargeType == null ? "" : cmbChargeType.getValue();
            lblChargeCaption.setText(chargeType == null || chargeType.isBlank() ? "Charges" : "Charges • " + chargeType);
        }

    }

    private double number(TextField field){try{return field==null||field.getText()==null||field.getText().isBlank()?0:Double.parseDouble(field.getText().replace(",",""));}catch(Exception e){return 0;}}

    @FXML private void addMultipleItems(){new OwnedAlert(Alert.AlertType.INFORMATION,"Select an item, enter quantity/rate/tax and click Add Item. Repeat for each required item.").showAndWait();}
    @FXML private void scanBarcode(){TextInputDialog d=new OwnedTextInputDialog();d.setHeaderText("Scan or enter item code");d.showAndWait().ifPresent(code->cmbItem.getItems().stream().filter(i->i.getItemCode().equalsIgnoreCase(code.trim())).findFirst().ifPresentOrElse(cmbItem::setValue,()->warn("Item code not found")));}
    @FXML private void attachFile(){javafx.stage.FileChooser c=new javafx.stage.FileChooser();java.io.File f=c.showOpenDialog(tableLines.getScene().getWindow());if(f!=null)txtAttachment.setText(f.getAbsolutePath());}
    @FXML private void preview(){Sales sale=buildSale();if(sale!=null)new OwnedAlert(Alert.AlertType.INFORMATION,"Invoice "+sale.getInvoiceNo()+"\nCustomer: "+sale.getCustomer().getName()+"\nItems: "+sale.getLines().size()+"\nTotal: "+String.format("₹ %,.2f",sale.getTotalAmount())).showAndWait();}
    @FXML private void saveDraft(){Sales sale=buildSale();if(sale==null)return;sale.setRemarks("DRAFT\n"+sale.getRemarks());try{salesService.save(sale);NotificationService.add("Draft sales invoice "+sale.getInvoiceNo()+" saved.");cancel();}catch(Exception e){warn(e.getMessage());}}


//--------------------------------------------------
// WARNING
//--------------------------------------------------

    private void warn(String msg) {

        new OwnedAlert(
            Alert.AlertType.WARNING,
            msg
        ).showAndWait();

    }


//--------------------------------------------------
// CANCEL
//--------------------------------------------------

    @FXML
    private void cancel() {

        NavigationManager.getInstance()
            .loadPage("/fxml/pages/SalesList.fxml");

    }


//--------------------------------------------------
// LOAD SALE FOR EDIT
//--------------------------------------------------

    public void loadSale(Sales sale) {

        System.out.println(
            "Invoice = " + sale.getInvoiceNo()
        );

        editingSale = sale;

        txtInvoiceNo.setText(
            sale.getInvoiceNo()
        );

        lblInvoiceDisplay.setText(
            sale.getInvoiceNo()
        );

        dpInvoiceDate.setValue(
            sale.getInvoiceDate()
        );
        dpDueDate.setValue(sale.getPoDate());
        cmbSalesPerson.setValue(sale.getSalesperson().isBlank()?"Admin":sale.getSalesperson());
        txtInvoiceMessage.setText(sale.getNotes());
        txtDeliveryAddress.setText(sale.getDeliveryAddress());
        cmbPaymentTerms.setValue(sale.getPaymentTerms().isBlank() ? "15 Days" : sale.getPaymentTerms());
        if (cmbGstType != null) cmbGstType.setValue(sale.getGstType().isBlank()
            ? (cmbGstType.getItems().isEmpty() ? null : cmbGstType.getItems().get(0)) : sale.getGstType());
        if (cmbTransporter != null) cmbTransporter.setValue(sale.getTransporter());
        if (cmbDoorDelivery != null) cmbDoorDelivery.setValue(sale.getDoorDelivery().isBlank() ? "No" : sale.getDoorDelivery());
        if (txtVehicleNumber != null) txtVehicleNumber.setText(sale.getVehicleNumber());
        if (txtContactPerson != null) txtContactPerson.setText(sale.getContactPerson());
        if (txtContactPersonMobile != null) txtContactPersonMobile.setText(sale.getContactPersonMobile());
        if (cmbChargeType != null) cmbChargeType.setValue(sale.getChargeType().isBlank() ? null : sale.getChargeType());
        if (txtChargeAmount != null) txtChargeAmount.setText(String.valueOf(sale.getChargeAmount()));
        if (txtTransportNote != null) txtTransportNote.setText(sale.getTransportNote());
        if (txtOrderNo != null) txtOrderNo.setText(sale.getOrderNo());
        if (txtGstin != null) txtGstin.setText(sale.getGstin().isBlank() && sale.getCustomer() != null
            ? sale.getCustomer().getGstin() : sale.getGstin());
        txtReference.setText("");

        // Select customer

        if (sale.getCustomer() != null) {

            txtBillingAddress.setText(sale.getCustomer().getAddress() == null
                ? "" : sale.getCustomer().getAddress());

            for (Party party : cmbCustomer.getItems()) {

                if (party.getId()
                    == sale.getCustomer().getId()) {

                    cmbCustomer.getSelectionModel()
                        .select(party);

                    break;

                }

            }

        }

        if (cmbBillingAddress != null) {
            String billing = sale.getBillingAddress().isBlank()
                ? (sale.getCustomer() == null ? "" : sale.getCustomer().getAddress())
                : sale.getBillingAddress();
            if (billing != null && !billing.isBlank() && !cmbBillingAddress.getItems().contains(billing))
                cmbBillingAddress.getItems().add(billing);
            cmbBillingAddress.setValue(billing);
        }
        if (chkSameAsBilling != null) {
            String billing = cmbBillingAddress == null ? "" : cmbBillingAddress.getValue();
            chkSameAsBilling.setSelected(!sale.getDeliveryAddress().isBlank() && sale.getDeliveryAddress().equals(billing));
            syncDeliveryAddressState();
        }

        txtRemarks.setText(

            sale.getRemarks() == null

                ? ""

                : sale.getRemarks()

        );

        tableLines.getItems().clear();

        if (sale.getLines() != null) {

            tableLines.getItems()
                .addAll(
                    sale.getLines()
                );

        }

        recalculate();

    }


//--------------------------------------------------
// VIEW MODE
//--------------------------------------------------

    public void setViewMode(boolean value) {

        txtInvoiceNo.setDisable(value);

        dpInvoiceDate.setDisable(value);

        cmbCustomer.setDisable(value);

        cmbItem.setDisable(value);

        txtQuantity.setDisable(value);

        txtRate.setDisable(value);

        txtGST.setDisable(value);
        txtLineDiscount.setDisable(value);

        txtRemarks.setDisable(value);
        txtBillingAddress.setDisable(value);
        txtDeliveryAddress.setDisable(value);
        if (cmbBillingAddress != null) cmbBillingAddress.setDisable(value);
        if (cmbGstType != null) cmbGstType.setDisable(value);
        if (cmbTransporter != null) cmbTransporter.setDisable(value);
        if (cmbDoorDelivery != null) cmbDoorDelivery.setDisable(value);
        if (txtVehicleNumber != null) txtVehicleNumber.setDisable(value);
        if (txtContactPerson != null) txtContactPerson.setDisable(value);
        if (txtContactPersonMobile != null) txtContactPersonMobile.setDisable(value);
        if (cmbChargeType != null) cmbChargeType.setDisable(value);
        if (txtChargeAmount != null) txtChargeAmount.setDisable(value);
        if (txtTransportNote != null) txtTransportNote.setDisable(value);
        if (txtOrderNo != null) txtOrderNo.setDisable(value);
        if (txtGstin != null) txtGstin.setDisable(value);
        if (chkSameAsBilling != null) chkSameAsBilling.setDisable(value);

        btnAddLine.setDisable(value);
        if (btnRemoveLine != null) btnRemoveLine.setDisable(value);
        if (btnSaveDraft != null) btnSaveDraft.setDisable(value);
        if (btnAddCustomer != null) btnAddCustomer.setDisable(value);

        btnSaveSale.setDisable(value);

        tableLines.setDisable(value);

    }
    @FXML
    private void addLine(){


        Item item = cmbItem.getValue();


        if(item==null){

            warn("Select item");

            return;
        }



        try{


            double qty =
                Double.parseDouble(txtQuantity.getText());


            double rate =
                Double.parseDouble(txtRate.getText());

            if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
            if (rate < 0) throw new IllegalArgumentException("Rate cannot be negative");
            double alreadyOnInvoice = tableLines.getItems().stream()
                .filter(line -> line != editingLine && item.getItemCode().equals(line.getItemCode()))
                .mapToDouble(SalesLine::getQuantity).sum();
            if (qty + alreadyOnInvoice > item.getOpeningStock()) {
                throw new IllegalArgumentException("Only " + item.getOpeningStock() + " units of " + item.getDescription() + " are available in stock");
            }


            double gst =
                item.getGst();
            double discount = item.getDiscountPercent();



            if(txtGST.getText()!=null &&
                !txtGST.getText().isBlank()){

                gst =
                    Double.parseDouble(txtGST.getText());

            }



            if (txtLineDiscount.getText() != null && !txtLineDiscount.getText().isBlank()) {
                discount = Double.parseDouble(txtLineDiscount.getText());
            }
            if (discount < 0 || discount > 100) throw new IllegalArgumentException("Discount must be between 0 and 100");



            SalesLine line =
                new SalesLine();


            line.setItemCode(
                item.getItemCode()
            );


            line.setItemDescription(
                item.getItemCode()
                    +" - "
                    +item.getDescription()
            );


            line.setQuantity(qty);


            line.setRate(rate);


            line.setGstPercent(gst);
            line.setDiscountPercent(discount);
            line.recalculate();



            if(editingLine == null){

                tableLines.getItems().add(line);

            }else{

                tableLines.getItems().set(editingIndex, line);

                editingLine = null;
                editingIndex = -1;

            }



            cmbItem.setValue(null);

            txtQuantity.clear();

            txtRate.clear();

            txtGST.clear();
            txtLineDiscount.clear();
            tableLines.getSelectionModel().clearSelection();


            recalculate();



        }
        catch(Exception e){
            warn(e instanceof NumberFormatException ? "Enter valid quantity and rate" : e.getMessage());

        }

    }

    private void configureEmptyState() {
        VBox placeholder = new VBox(8);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.getStyleClass().add("sales-entry-empty-state");

        Label icon = new Label();
        icon.setGraphic(IconFactory.icon("item", 34));
        icon.getStyleClass().add("sales-entry-empty-icon");

        Label title = new Label("No items added yet");
        title.getStyleClass().add("sales-entry-empty-title");

        Label message = new Label("Use the controls above to add invoice items");
        message.getStyleClass().add("sales-entry-empty-message");

        placeholder.getChildren().addAll(icon, title, message);
        tableLines.setPlaceholder(placeholder);
    }

    private void decorateActions() {
        if (salesEntryRoot == null) return;

        Node titleHolder = salesEntryRoot.lookup(".sales-entry-title-icon");
        if (titleHolder instanceof StackPane stackPane) {
            stackPane.getChildren().setAll(IconFactory.icon("sale", 24));
        }

        for (Node node : salesEntryRoot.lookupAll(".button")) {
            if (!(node instanceof Button button) || button.getGraphic() != null) continue;

            String text = button.getText() == null ? "" : button.getText().trim().toLowerCase();
            String key = text.contains("back") ? "return" :
                text.contains("preview") ? "view" :
                text.contains("pdf") ? "download" :
                text.contains("email") ? "email" :
                text.contains("whatsapp") ? "whatsapp" :
                text.contains("remove") ? "delete" :
                text.contains("cancel") ? "cancel" :
                text.contains("draft") ? "save" :
                text.contains("save") ? "print" :
                text.contains("add customer") ? "customer" :
                text.contains("add") ? "add" : null;

            if (key != null) {
                button.setGraphic(IconFactory.icon(key));
                button.getProperties().put("erp-icon-explicit", true);
            }
        }
    }

    @FXML
    private void cancelEdit() {

        editingLine = null;
        editingIndex = -1;

        cmbItem.setValue(null);

        txtQuantity.clear();
        txtRate.clear();
        txtGST.clear();

        tableLines.getSelectionModel().clearSelection();

        btnAddLine.setText("+ Add Line");
    }



    @FXML
    private void removeLine(){

        SalesLine line =
            tableLines
                .getSelectionModel()
                .getSelectedItem();


        if(line!=null){

            tableLines.getItems().remove(line);

            recalculate();

        }

    }




    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colItem, "item");
        IconFactory.applyTableHeaderIcon(colQuantity, "quantity");
        IconFactory.applyTableHeaderIcon(colRate, "currency");
        IconFactory.applyTableHeaderIcon(colGst, "tax");
        IconFactory.applyTableHeaderIcon(colDiscount, "discount");
        IconFactory.applyTableHeaderIcon(colDiscountAmount, "discount");
        IconFactory.applyTableHeaderIcon(colGstAmount, "tax");
        IconFactory.applyTableHeaderIcon(colNetAmount, "currency");
        IconFactory.applyTableHeaderIcon(colTotal, "currency");
    }
}
