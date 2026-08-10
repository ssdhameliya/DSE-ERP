package org.example.controller;

import org.example.util.OwnedChoiceDialog;

import org.example.util.OwnedAlert;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.TableCell;
import org.example.model.Item;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;
import org.example.navigation.NavigationManager;
import org.example.service.ItemService;
import org.example.service.PartyService;
import org.example.service.PurchaseService;
import org.example.service.NotificationService;
import org.example.service.InvoicePdfService;
import org.example.service.EmailService;
import org.example.database.DatabaseManager;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import javafx.application.Platform;
import javafx.scene.Node;
import org.example.util.IconFactory;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;
import java.nio.file.Files;

import java.time.LocalDate;
import java.util.List;
import java.sql.*;
import java.io.File;


public class PurchaseController {
    @FXML private Button btnAddSupplier;


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


    @FXML
    private ComboBox<Party> cmbSupplier;


    @FXML
    private ComboBox<Item> cmbItem;


    @FXML
    private TextArea txtRemarks;


    private PurchaseLine editingLine = null;

    private int editingIndex = -1;


    @FXML
    private Label lblNetAmount;

    @FXML
    private Label lblGst;

    @FXML
    private Label lblDiscount;

    @FXML
    private Label lblGrandTotal;



    @FXML
    private TableView<PurchaseLine> tableLines;


    @FXML
    private TableColumn<PurchaseLine,String> colItem;


    @FXML
    private TableColumn<PurchaseLine,Double> colQuantity;


    @FXML
    private TableColumn<PurchaseLine,Double> colRate;


    @FXML
    private TableColumn<PurchaseLine,Double> colGst;

    @FXML
    private TableColumn<PurchaseLine,Double> colDiscount;

    @FXML
    private TableColumn<PurchaseLine,Double> colDiscountAmount;


    @FXML
    private TableColumn<PurchaseLine,Double> colGstAmount;


    @FXML
    private TableColumn<PurchaseLine,Double> colNetAmount;


    @FXML
    private TableColumn<PurchaseLine,Double> colTotal;



    private final ItemService itemService =
        new ItemService();


    private final PartyService partyService =
        new PartyService();


    private final PurchaseService purchaseService =
        new PurchaseService();

    private Purchase editingPurchase = null;

    @FXML
    private Button btnAddLine;
    @FXML private DatePicker dpDueDate, dpDeliveryDate;
    @FXML private ComboBox<String> cmbWarehouse,cmbPaymentTerms,cmbCurrency,cmbGstTreatment,cmbTransporter,cmbDiscountType;
    @FXML private TextField txtReference,txtLrAwb,txtDiscount;
    @FXML private Label lblAttachment;
    private File attachment;




    @FXML
    public void initialize(){
        if (btnAddSupplier != null) { btnAddSupplier.setGraphic(IconFactory.compactIcon("supplier", 20)); btnAddSupplier.getProperties().put("erp-icon-preserve", true); }
        configureExplicitTableHeaderIcons();


        setupTable();

        setupAmountFormatting();
        tableLines.setEditable(true);
        setupEditableColumns();

        tableLines.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldLine, newLine) -> {

                if(newLine == null)
                    return;

                editingLine = newLine;
                editingIndex = tableLines.getSelectionModel().getSelectedIndex();

                txtQuantity.setText(String.valueOf(newLine.getQuantity()));
                txtRate.setText(String.valueOf(newLine.getRate()));
                txtGST.setText(String.valueOf(newLine.getGstPercent()));
                txtLineDiscount.setText(String.valueOf(newLine.getDiscountPercent()));

                // Select the correct item
                for(Item item : cmbItem.getItems()){

                    if(item.getItemCode().equals(newLine.getItemCode())){

                        cmbItem.getSelectionModel().select(item);
                        break;
                    }
                }
            }
        );

        cmbSupplier.setItems(
            FXCollections.observableArrayList(
                partyService.getByType("SUPPLIER")
            )
        );


        cmbItem.setItems(
            FXCollections.observableArrayList(
                itemService.getAll()
            )
        );


        cmbItem.setCellFactory(list ->
            new ListCell<>(){

                @Override
                protected void updateItem(Item item, boolean empty){

                    super.updateItem(item,empty);

                    setText(
                        empty || item==null
                            ? null
                            : item.getItemCode()
                              +" - "
                              +item.getDescription()
                    );
                }
            });


        cmbItem.setButtonCell(
            new ListCell<>(){

                @Override
                protected void updateItem(Item item, boolean empty){

                    super.updateItem(item,empty);

                    setText(
                        empty || item==null
                            ? null
                            : item.getItemCode()
                              +" - "
                              +item.getDescription()
                    );
                }
            });


        cmbSupplier.setCellFactory(list ->
            new ListCell<>(){

                @Override
                protected void updateItem(Party party, boolean empty){

                    super.updateItem(party,empty);

                    setText(
                        empty || party==null
                            ? null
                            : party.getPartyCode()
                              +" - "
                              +party.getName()
                    );

                }

            });


        cmbSupplier.setButtonCell(
            new ListCell<>(){

                @Override
                protected void updateItem(Party party, boolean empty){

                    super.updateItem(party,empty);

                    setText(
                        empty || party==null
                            ? null
                            : party.getPartyCode()
                              +" - "
                              +party.getName()
                    );

                }
            });


        populateLookups();
        dpInvoiceDate.valueProperty().addListener((obs, oldDate, newDate) -> updateDueDate());
        cmbPaymentTerms.valueProperty().addListener((obs, oldTerm, newTerm) -> updateDueDate());
        cmbSupplier.valueProperty().addListener((obs, oldSupplier, supplier) -> populateSupplierAddress(supplier));
        // Selecting an item always uses the purchase rate and GST defined in Item Master.
        cmbItem.valueProperty().addListener((obs, oldItem, item) -> {
            if (item != null) {
                txtRate.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getPurchasePrice()));
                txtGST.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getGst()));
                txtLineDiscount.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getDiscountPercent()));
            }
        });
        Platform.runLater(this::cleanPurchaseActions);
        newPurchase();

    }





    private void setupTable(){


        colItem.setCellValueFactory(
            new PropertyValueFactory<>("itemDescription")
        );


        colQuantity.setCellValueFactory(
            new PropertyValueFactory<>("quantity")
        );


        colRate.setCellValueFactory(
            new PropertyValueFactory<>("rate")
        );


        colGst.setCellValueFactory(
            new PropertyValueFactory<>("gstPercent")
        );

        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
        colDiscountAmount.setCellValueFactory(new PropertyValueFactory<>("discountAmount"));


        colGstAmount.setCellValueFactory(
            new PropertyValueFactory<>("gstAmount")
        );


        colNetAmount.setCellValueFactory(
            new PropertyValueFactory<>("netAmount")
        );


        colTotal.setCellValueFactory(
            new PropertyValueFactory<>("totalAmount")
        );

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



            PurchaseLine line =
                new PurchaseLine();


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
            line.calculateAmounts();



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

            warn("Enter valid quantity and rate");

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

        PurchaseLine line =
            tableLines
                .getSelectionModel()
                .getSelectedItem();


        if(line!=null){

            tableLines.getItems().remove(line);

            recalculate();

        }

    }





    @FXML
    private void savePurchase(){ savePurchase("COMPLETED",false,false); }
    @FXML private void saveDraft(){ savePurchase("DRAFT",false,false); }
    @FXML private void saveAndPrint(){ savePurchase("COMPLETED",true,false); }
    @FXML private void saveAndEmail(){ savePurchase("COMPLETED",false,true); }
    private void savePurchase(String documentStatus, boolean print, boolean email){

        Purchase purchase = buildPurchase();

        if(purchase == null)
            return;
        purchase.setDocumentStatus(documentStatus);

        try {

            if(editingPurchase != null){

                purchase.setId(editingPurchase.getId());

                purchaseService.update(purchase);

                NotificationService.add(
                    "Purchase "
                        + purchase.getInvoiceNo()
                        + " updated"
                );
            }
            else {
                /*
                 * A second instance of the screen can remain open while another
                 * purchase is saved. Re-check the number immediately before the
                 * insert and advance it if necessary.
                 */
                if (purchaseService.existsInvoice(purchase.getInvoiceNo())) {
                    String freshInvoiceNo = purchaseService.nextInvoiceNo();
                    txtInvoiceNo.setText(freshInvoiceNo);
                    purchase.setInvoiceNo(freshInvoiceNo);
                }
                purchaseService.save(purchase);
                NotificationService.add(
                    "Purchase "
                        + purchase.getInvoiceNo()
                        + " saved"
                );

            }
            if (!org.example.config.ConfigManager.isApiDataEnabled()) saveMetadata(purchase);
            Purchase full = purchaseService.getByInvoice(purchase.getInvoiceNo());
            if(print) java.awt.Desktop.getDesktop().open(InvoicePdfService.purchase(full).toFile());
            if(email){if(full.getSupplier().getEmail()==null||full.getSupplier().getEmail().isBlank())throw new IllegalStateException("Supplier email is missing");EmailService.send(full.getSupplier().getEmail(),"Purchase "+full.getInvoiceNo(),"Please find the purchase document attached.",InvoicePdfService.purchase(full));purchaseService.markEmailSent(full.getId());}


            new OwnedAlert(
                Alert.AlertType.INFORMATION,
                "Purchase saved successfully"
            ).showAndWait();



            NavigationManager.getInstance()
                .loadPage(
                    "/fxml/pages/PurchaseList.fxml"
                );


        }
        catch(Exception e){

            new OwnedAlert(
                Alert.AlertType.ERROR,
                e.getMessage()
            ).showAndWait();

        }

    }

    private Purchase buildPurchase(){
        if(dpInvoiceDate.getValue()==null){

            warn("Select invoice date");

            return null;

        }

        if(cmbSupplier.getValue()==null){

            warn("Select supplier");

            return null;

        }


        if(tableLines.getItems().isEmpty()){

            warn("Add items");

            return null;

        }



        Purchase purchase =
            new Purchase();


        purchase.setInvoiceNo(
            txtInvoiceNo.getText()
        );


        purchase.setInvoiceDate(
            dpInvoiceDate.getValue()
        );


        purchase.setSupplier(
            cmbSupplier.getValue()
        );


        purchase.setLines(
            List.copyOf(
                tableLines.getItems()
            )
        );



        double net =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    PurchaseLine::getNetAmount
                )
                .sum();



        double discount = tableLines.getItems().stream().mapToDouble(PurchaseLine::getDiscountAmount).sum();

        double gst =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    PurchaseLine::getGstAmount
                )
                .sum();



        double total =
            net + gst;



        purchase.setSubtotal(net);

        purchase.setGstAmount(gst);

        purchase.setTotalAmount(total);


        purchase.setRemarks(
            txtRemarks.getText()
        );
        purchase.setDueDate(dpDueDate.getValue());purchase.setDeliveryDate(dpDeliveryDate.getValue());purchase.setWarehouse(cmbWarehouse.getValue());purchase.setPaymentTerms(cmbPaymentTerms.getValue());purchase.setCurrency(cmbCurrency.getValue());purchase.setReferenceNo(txtReference.getText());purchase.setGstTreatment(cmbGstTreatment.getValue());purchase.setTransporter(cmbTransporter.getValue());purchase.setLrAwbNo(txtLrAwb.getText());purchase.setDiscountType("Item Level");purchase.setDiscountAmount(discount);purchase.setTotalAmount(net+gst);purchase.setAttachmentPath(attachment==null?null:attachment.getAbsolutePath());



        return purchase;

    }

    private void saveMetadata(Purchase p)throws Exception{try(Connection c=DatabaseManager.getConnection();PreparedStatement q=c.prepareStatement("UPDATE purchase_header SET due_date=?,delivery_date=?,document_status=?,warehouse=?,payment_terms=?,currency=?,reference_no=?,gst_treatment=?,transporter=?,lr_awb_no=?,discount_type=?,discount_amount=?,attachment_path=?,created_by=COALESCE(created_by,'Admin'),updated_at=CURRENT_TIMESTAMP WHERE invoice_no=?")){q.setString(1,str(p.getDueDate()));q.setString(2,str(p.getDeliveryDate()));q.setString(3,p.getDocumentStatus());q.setString(4,p.getWarehouse());q.setString(5,p.getPaymentTerms());q.setString(6,p.getCurrency());q.setString(7,p.getReferenceNo());q.setString(8,p.getGstTreatment());q.setString(9,p.getTransporter());q.setString(10,p.getLrAwbNo());q.setString(11,p.getDiscountType());q.setDouble(12,p.getDiscountAmount());q.setString(13,p.getAttachmentPath());q.setString(14,p.getInvoiceNo());q.executeUpdate();}}
    @FXML private void chooseAttachment(){FileChooser f=new FileChooser();attachment=f.showOpenDialog(tableLines.getScene().getWindow());if(attachment!=null)lblAttachment.setText(attachment.getName());}
    @FXML private void clearLines(){tableLines.getItems().clear();recalculate();}
    @FXML private void preview(){new OwnedAlert(Alert.AlertType.INFORMATION,"Preview is available after saving the purchase.").showAndWait();}
    public void prepareDuplicate(){editingPurchase=null;txtInvoiceNo.setText(purchaseService.nextInvoiceNo());}
    private double parse(String v){try{return v==null||v.isBlank()?0:Double.parseDouble(v);}catch(Exception e){return 0;}}private String str(LocalDate d){return d==null?null:d.toString();}





    @FXML
    private void newPurchase(){

        editingPurchase = null;


        txtInvoiceNo.setText(
            purchaseService.nextInvoiceNo()
        );

        dpInvoiceDate.setValue(
            LocalDate.now()
        );
        dpDueDate.setValue(LocalDate.now().plusDays(15));
        dpDeliveryDate.setValue(LocalDate.now());


        cmbSupplier.setValue(null);

        cmbItem.setValue(null);


        txtQuantity.clear();

        txtRate.clear();

        txtGST.clear();
        txtLineDiscount.clear();


        txtRemarks.clear();


        tableLines.getItems().clear();


        recalculate();


    }

    private void populateLookups() {
        cmbWarehouse.getItems().setAll("Main Warehouse", "Secondary Warehouse", "Transit Warehouse");
        cmbPaymentTerms.getItems().setAll("Due on Receipt", "7 Days", "15 Days", "30 Days", "45 Days", "60 Days");
        cmbCurrency.getItems().setAll("INR - Indian Rupee", "USD - US Dollar", "EUR - Euro", "GBP - British Pound");
        cmbGstTreatment.getItems().setAll("Business Purchase", "Registered Business", "Composition Dealer", "Unregistered Business", "Import");
        cmbTransporter.getItems().setAll("Self Transport", "Road Transport", "Courier", "Air Freight", "Customer Pickup");
        cmbDiscountType.getItems().setAll("Percentage", "Fixed Amount", "No Discount");
        cmbWarehouse.setValue("Main Warehouse"); cmbPaymentTerms.setValue("15 Days");
        cmbCurrency.setValue("INR - Indian Rupee"); cmbGstTreatment.setValue("Business Purchase");
        cmbTransporter.setValue("Self Transport"); cmbDiscountType.setValue("Percentage");
    }

    /** Keeps the stored purchase due date synchronized with date and payment terms. */
    private void updateDueDate() {
        LocalDate invoiceDate = dpInvoiceDate.getValue();
        if (invoiceDate == null) return;
        String term = cmbPaymentTerms.getValue();
        int days = 0;
        if (term != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(term);
            if (matcher.find()) days = Integer.parseInt(matcher.group(1));
        }
        LocalDate calculatedDate = invoiceDate.plusDays(days);
        dpDueDate.setValue(calculatedDate);
        dpDeliveryDate.setValue(calculatedDate);
    }

    /** Opens the standard themed supplier editor and refreshes this form afterwards. */
    @FXML
    private void addSupplier() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/PartyDialog.fxml"));
            Parent root = loader.load();
            loader.<PartyDialogController>getController().configure("SUPPLIER", null);
            Stage dialog = new Stage();
            PlatformUiSupport.configureDialogStage(dialog, cmbSupplier, "Add Supplier", true);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene);
            dialog.setScene(scene);
            dialog.showAndWait();
            Party selected = cmbSupplier.getValue();
            cmbSupplier.getItems().setAll(partyService.getByType("SUPPLIER"));
            if (selected != null) cmbSupplier.getSelectionModel().select(selected);
        } catch (Exception ex) {
            new OwnedAlert(Alert.AlertType.ERROR, "Unable to open supplier form: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void populateSupplierAddress(Party supplier) {
        if (tableLines.getScene() == null) return;
        for (Node node : tableLines.getScene().getRoot().lookupAll(".combo-box")) {
            if (node instanceof ComboBox<?> box && "Supplier billing address".equals(box.getPromptText())) {
                @SuppressWarnings("unchecked") ComboBox<String> addressBox = (ComboBox<String>) box;
                addressBox.getItems().clear();
                if (supplier != null && supplier.getAddress() != null && !supplier.getAddress().isBlank()) addressBox.getItems().add(supplier.getAddress());
                if (addressBox.getItems().isEmpty()) addressBox.getItems().add("Address not available - update Supplier Master");
                addressBox.getSelectionModel().selectFirst();
            }
        }
    }

    private void cleanPurchaseActions() {
        if (tableLines.getScene() == null) return;
        for (Node node : tableLines.getScene().getRoot().lookupAll(".button")) {
            if (!(node instanceof Button button)) continue;
            String text = button.getText() == null ? "" : button.getText();
            boolean duplicateAdd = text.contains("Add Item") && button != btnAddLine;
            if (duplicateAdd || text.contains("Add Multiple") || text.contains("Scan Barcode")) {
                button.setVisible(false); button.setManaged(false);
            }
            String lower=text.toLowerCase();
            String icon=lower.contains("select from po")?"purchase":lower.contains("import")?"download":lower.contains("clear")?"delete":lower.contains("save")?"document":lower.contains("preview")?"view":lower.contains("cancel")?"return":lower.contains("add")?"item":null;
            if(icon!=null&&button.getGraphic()==null)button.setGraphic(IconFactory.icon(icon));
            if(lower.contains("select from po"))button.setOnAction(e->selectFromPo());
            if(lower.contains("import items"))button.setOnAction(e->importPurchaseItems());
        }
    }

    private void selectFromPo(){
        List<Purchase> drafts=purchaseService.getAll().stream().filter(p->"DRAFT".equalsIgnoreCase(p.getDocumentStatus())).toList();
        if(drafts.isEmpty()){warn("No draft purchase orders are available. Save a purchase as Draft first.");return;}
        ChoiceDialog<Purchase> dialog=new OwnedChoiceDialog<>(drafts.getFirst(),drafts);dialog.setTitle("Select Purchase Order");dialog.setHeaderText("Choose a draft purchase order to load");dialog.setContentText("Purchase order:");dialog.showAndWait().ifPresent(p->loadPurchase(purchaseService.getByInvoice(p.getInvoiceNo())));
    }

    private void importPurchaseItems(){
        FileChooser chooser=new FileChooser();chooser.setTitle("Import Purchase Items");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File","*.csv"));File file=chooser.showOpenDialog(tableLines.getScene().getWindow());if(file==null)return;
        try{int count=0;for(String row:Files.readAllLines(file.toPath())){if(row.isBlank()||row.toLowerCase().startsWith("item"))continue;String[]v=row.split(",");if(v.length<3)throw new IllegalArgumentException("CSV columns must be: item_code,quantity,rate,gst_percent");Item item=cmbItem.getItems().stream().filter(i->i.getItemCode().equalsIgnoreCase(v[0].trim())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown item code: "+v[0]));double q=Double.parseDouble(v[1].trim()),rate=Double.parseDouble(v[2].trim()),gst=v.length>3?Double.parseDouble(v[3].trim()):item.getGst();PurchaseLine line=new PurchaseLine();line.setItemCode(item.getItemCode());line.setItemDescription(item.getItemCode()+" - "+item.getDescription());line.setQuantity(q);line.setRate(rate);line.setGstPercent(gst);recalculateLine(line);tableLines.getItems().add(line);count++;}recalculate();new OwnedAlert(Alert.AlertType.INFORMATION,count+" purchase item(s) imported.").showAndWait();}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,"Could not import items: "+e.getMessage()).showAndWait();}
    }





    private void recalculate(){


        double net =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    PurchaseLine::getNetAmount
                )
                .sum();


        double discount = tableLines.getItems().stream().mapToDouble(PurchaseLine::getDiscountAmount).sum();

        double gst =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    PurchaseLine::getGstAmount
                )
                .sum();


        double total =
            net + gst;



        lblNetAmount.setText(
            String.format("₹ %.2f",net)
        );


        lblDiscount.setText(String.format("₹ %.2f", discount));

        lblGst.setText(
            String.format("₹ %.2f",gst)
        );


        lblGrandTotal.setText(
            String.format("₹ %.2f",total)
        );

    }





    private void warn(String msg){

        new OwnedAlert(
            Alert.AlertType.WARNING,
            msg
        ).showAndWait();

    }





    @FXML
    private void cancel(){


        NavigationManager.getInstance()
            .loadPage(
                "/fxml/pages/PurchaseList.fxml"
            );

    }

    public void loadPurchase(Purchase purchase)
    {
        System.out.println(
            "Invoice = " + purchase.getInvoiceNo()
        );


        tableLines.getItems().clear();


        if(purchase.getLines()!=null &&
            !purchase.getLines().isEmpty()) {

            tableLines.getItems()
                .addAll(
                    purchase.getLines()
                );

        }
        else{

            System.out.println(
                "Lines = NULL"
            );

        }
        editingPurchase = purchase;


        txtInvoiceNo.setText(
            purchase.getInvoiceNo()
        );


        dpInvoiceDate.setValue(
            purchase.getInvoiceDate()
        );


        // FIX SUPPLIER SELECTION
        if(purchase.getSupplier()!=null){

            for(Party party : cmbSupplier.getItems()){

                if(party.getId() == purchase.getSupplier().getId()){

                    cmbSupplier.getSelectionModel()
                        .select(party);

                    break;
                }
            }
        }



        txtRemarks.setText(
            purchase.getRemarks()==null
                ? ""
                : purchase.getRemarks()
        );



        tableLines.getItems().clear();



        if(purchase.getLines()!=null){

            tableLines.getItems()
                .addAll(
                    purchase.getLines()
                );

        }

        dpDueDate.setValue(purchase.getDueDate());dpDeliveryDate.setValue(purchase.getDeliveryDate());select(cmbWarehouse,purchase.getWarehouse());select(cmbPaymentTerms,purchase.getPaymentTerms());select(cmbCurrency,purchase.getCurrency());select(cmbGstTreatment,purchase.getGstTreatment());select(cmbTransporter,purchase.getTransporter());select(cmbDiscountType,purchase.getDiscountType());txtReference.setText(value(purchase.getReferenceNo()));txtLrAwb.setText(value(purchase.getLrAwbNo()));txtDiscount.setText(String.valueOf(purchase.getDiscountAmount()));if(purchase.getAttachmentPath()!=null)lblAttachment.setText(purchase.getAttachmentPath());


        recalculate();

    }    public void setViewMode(boolean value){

    }
    private void select(ComboBox<String> box,String value){if(value!=null&&!value.isBlank()){if(!box.getItems().contains(value))box.getItems().add(value);box.setValue(value);}}private String value(String v){return v==null?"":v;}
    private void setupAmountFormatting() {


        colQuantity.setCellFactory(column -> {

            TextFieldTableCell<PurchaseLine, Double> cell =
                new TextFieldTableCell<>(
                    new DoubleStringConverter()
                );

            return cell;

        });


        colRate.setCellFactory(column -> {

            TextFieldTableCell<PurchaseLine, Double> cell =
                new TextFieldTableCell<>(
                    new DoubleStringConverter()
                );

            return cell;

        });


        colGst.setCellFactory(column -> {

            TextFieldTableCell<PurchaseLine, Double> cell =
                new TextFieldTableCell<>(
                    new DoubleStringConverter()
                );

            return cell;

        });

        colDiscount.setCellFactory(column -> new TextFieldTableCell<>(new DoubleStringConverter()));
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

    private void setupEditableColumns() {

        // Quantity
        colQuantity.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            )
        );

        colQuantity.setOnEditCommit(event -> {

            PurchaseLine line = event.getRowValue();

            line.setQuantity(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });


        // Rate
        colRate.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            )
        );

        colRate.setOnEditCommit(event -> {

            PurchaseLine line = event.getRowValue();

            line.setRate(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });


        // GST %
        colGst.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            )
        );

        colGst.setOnEditCommit(event -> {

            PurchaseLine line = event.getRowValue();

            line.setGstPercent(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });

        colDiscount.setOnEditCommit(event -> {
            PurchaseLine line = event.getRowValue();
            double value = event.getNewValue() == null ? 0 : event.getNewValue();
            line.setDiscountPercent(Math.max(0, Math.min(100, value)));
            recalculateLine(line);
            tableLines.refresh();
            recalculate();
        });

    }

    private void recalculateLine(PurchaseLine line) {

        line.calculateAmounts();

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
