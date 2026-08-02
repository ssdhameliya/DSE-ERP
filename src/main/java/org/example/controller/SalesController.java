package org.example.controller;

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
    @FXML private ComboBox<String> cmbSalesPerson,cmbPaymentTerms,cmbDeliveryStatus;
    @FXML private TextField txtOtherCharges,txtTransport,txtReference,txtAttachment;
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
        tableLines.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        configureExplicitTableHeaderIcons();

        setupTable();
        configureEmptyState();

        setupAmountFormatting();

        tableLines.setEditable(true);

        setupEditableColumns();
        Platform.runLater(this::decorateActions);
        cmbSalesPerson.getItems().setAll("Admin","Ajay Shah","Rahul Mehta");cmbSalesPerson.setValue("Admin");
        cmbPaymentTerms.getItems().setAll("Due on Receipt","7 Days","15 Days","30 Days","45 Days");cmbPaymentTerms.setValue("15 Days");
        dpInvoiceDate.valueProperty().addListener((o,a,b)->updateDueDate());
        cmbPaymentTerms.valueProperty().addListener((o,a,b)->updateDueDate());
        cmbDeliveryStatus.getItems().setAll("To be Delivered","Partially Delivered","Delivered");cmbDeliveryStatus.setValue("To be Delivered");
        txtOtherCharges.textProperty().addListener((o,a,b)->recalculate());txtTransport.textProperty().addListener((o,a,b)->recalculate());

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
                if (editingSale == null) txtDeliveryAddress.clear();
                return;
            }
            String address = customer.getAddress() == null ? "" : customer.getAddress().trim();
            txtBillingAddress.setText(address);
            if (cmbBillingAddress != null) {
                cmbBillingAddress.getItems().setAll(address.isBlank() ? java.util.List.of() : java.util.List.of(address));
                cmbBillingAddress.getSelectionModel().select(address);
            }
            if (editingSale == null || txtDeliveryAddress.getText() == null
                || txtDeliveryAddress.getText().isBlank()) txtDeliveryAddress.setText(address);
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

    private void updateDueDate(){if(dpInvoiceDate.getValue()==null)return;String value=cmbPaymentTerms.getValue();int days=0;if(value!=null){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+)").matcher(value);if(m.find())days=Integer.parseInt(m.group(1));}dpDueDate.setValue(dpInvoiceDate.getValue().plusDays(days));}

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
            new Alert(Alert.AlertType.ERROR,
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

            new Alert(
                Alert.AlertType.INFORMATION,
                "Sales saved successfully"
            ).showAndWait();

            NavigationManager.getInstance()
                .loadPage("/fxml/pages/SalesList.fxml");

        }
        catch (Exception e) {

            new Alert(
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

        double total = net + gst + number(txtOtherCharges) + number(txtTransport);

        sale.setSubtotal(net);
        sale.setDiscountAmount(discount);

        sale.setGstAmount(gst);

        sale.setTotalAmount(total);

        sale.setRemarks(
            txtRemarks.getText()+"\nReference: "+txtReference.getText()+"\nTransport: "+txtTransport.getText()+"\nOther Charges: "+txtOtherCharges.getText()+"\nAttachment: "+txtAttachment.getText()
        );
        sale.setDueDate(dpDueDate.getValue());
        sale.setSalesperson(cmbSalesPerson.getValue());
        sale.setNotes(txtInvoiceMessage.getText());
        sale.setDeliveryAddress(txtDeliveryAddress.getText());
        sale.setPaymentTerms(cmbPaymentTerms.getValue());
        sale.setTransporter(txtTransport.getText());
        sale.setReferenceNo(txtReference.getText());

        return sale;

    }


//--------------------------------------------------
// NEW SALE
//--------------------------------------------------

    @FXML
    private void newSale() {

        editingSale = null;

        txtInvoiceNo.setText(
            salesService.nextInvoiceNo()
        );

        dpInvoiceDate.setValue(
            LocalDate.now()
        );
        dpDueDate.setValue(LocalDate.now().plusDays(15));

        cmbCustomer.setValue(null);

        cmbItem.setValue(null);

        txtQuantity.clear();

        txtRate.clear();

        txtGST.clear();
        txtLineDiscount.clear();

        txtRemarks.clear();
        txtBillingAddress.clear();
        txtDeliveryAddress.clear();

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

        double total = net + gst + number(txtOtherCharges) + number(txtTransport);

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

    }

    private double number(TextField field){try{return field==null||field.getText()==null||field.getText().isBlank()?0:Double.parseDouble(field.getText().replace(",",""));}catch(Exception e){return 0;}}

    @FXML private void addMultipleItems(){new Alert(Alert.AlertType.INFORMATION,"Select an item, enter quantity/rate/tax and click Add Item. Repeat for each required item.").showAndWait();}
    @FXML private void scanBarcode(){TextInputDialog d=new TextInputDialog();d.setHeaderText("Scan or enter item code");d.showAndWait().ifPresent(code->cmbItem.getItems().stream().filter(i->i.getItemCode().equalsIgnoreCase(code.trim())).findFirst().ifPresentOrElse(cmbItem::setValue,()->warn("Item code not found")));}
    @FXML private void attachFile(){javafx.stage.FileChooser c=new javafx.stage.FileChooser();java.io.File f=c.showOpenDialog(tableLines.getScene().getWindow());if(f!=null)txtAttachment.setText(f.getAbsolutePath());}
    @FXML private void preview(){Sales sale=buildSale();if(sale!=null)new Alert(Alert.AlertType.INFORMATION,"Invoice "+sale.getInvoiceNo()+"\nCustomer: "+sale.getCustomer().getName()+"\nItems: "+sale.getLines().size()+"\nTotal: "+String.format("₹ %,.2f",sale.getTotalAmount())).showAndWait();}
    @FXML private void saveDraft(){Sales sale=buildSale();if(sale==null)return;sale.setRemarks("DRAFT\n"+sale.getRemarks());try{salesService.save(sale);NotificationService.add("Draft sales invoice "+sale.getInvoiceNo()+" saved.");cancel();}catch(Exception e){warn(e.getMessage());}}


//--------------------------------------------------
// WARNING
//--------------------------------------------------

    private void warn(String msg) {

        new Alert(
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
        dpDueDate.setValue(sale.getDueDate());
        cmbSalesPerson.setValue(sale.getSalesperson().isBlank()?"Admin":sale.getSalesperson());
        txtInvoiceMessage.setText(sale.getNotes());
        txtDeliveryAddress.setText(sale.getDeliveryAddress());
        cmbPaymentTerms.setValue(sale.getPaymentTerms().isBlank() ? "15 Days" : sale.getPaymentTerms());
        txtTransport.setText(sale.getTransporter());
        txtReference.setText(sale.getReferenceNo());

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

        btnAddLine.setDisable(value);

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
