package org.example.controller;

import org.example.util.BusinessClock;
import org.example.shared.DocumentCalculationEngine;

import org.example.util.OwnedChoiceDialog;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.AttachmentPreviewSupport;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.example.model.PurchaseCharge;
import org.example.api.support.SupportApiClient;
import org.example.navigation.NavigationManager;
import org.example.util.ScreenRefreshPolicy;
import org.example.navigation.ScreenLifecycle;
import org.example.service.ItemService;
import org.example.service.PartyService;
import org.example.service.PurchaseService;
import org.example.service.LookupService;
import org.example.service.NotificationService;
import org.example.service.InvoicePdfService;
import org.example.service.EmailService;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.StringConverter;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.scene.Node;
import org.example.util.IconFactory;
import org.example.theme.ThemeManager;
import org.example.config.WorkspaceManager;
import org.example.config.ConfigManager;
import org.example.util.PlatformUiSupport;
import org.example.util.UiTaskExecutor;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import java.io.File;


public class PurchaseController implements ScreenLifecycle {
    @FXML private Button btnAddSupplier;
    @FXML private Button btnManageCharges;
    @FXML private Button btnSavePurchase, btnRemoveLine;
    @FXML private Button btnSaveView, btnReset, btnRefresh;
    @FXML private MenuButton savedViewsMenu;
    @FXML private javafx.scene.layout.StackPane purchasePageIcon;


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


    @FXML private TextField txtItemSearch;
    @FXML private javafx.scene.layout.StackPane itemSearchIconBox;


    @FXML
    private TextArea txtRemarks;
    @FXML private TextArea txtBillingAddress, txtDeliveryAddress;
    @FXML private TextField txtBillingGstin, txtDeliveryGstin, txtTransporterGstin, txtVehicleNumber, txtContactPerson, txtContactPersonMobile, txtOrderNo;
    @FXML private DatePicker txtPoDate;
    @FXML private CheckBox chkSameAsBilling;


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
    @FXML private Label lblCharges, lblTaxCaption, lblChargeManagerSummary;
    @FXML private Label lblCgst, lblSgst, lblIgst, lblBottomTaxCaption;
    @FXML private HBox rowCgst, rowSgst, rowIgst;
    @FXML private Label lblTotalItems, lblBottomDiscount, lblBottomTax, lblBottomCharges, lblBottomNet, lblTaxableAmount, lblChargeCaption;



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



    private final ObservableList<Item> allItems=FXCollections.observableArrayList();

    private final ItemService itemService =
        new ItemService();


    private final PartyService partyService =
        new PartyService();


    private final PurchaseService purchaseService =
        new PurchaseService();
    private final LookupService lookupService = new LookupService();
    private final SupportApiClient supportApi = new SupportApiClient();
    private final PauseTransition supplierSearchDebounce = new PauseTransition(Duration.millis(180));
    private boolean updatingSupplierSearch;

    private Purchase editingPurchase = null;
    private boolean viewMode;
    private final ContextMenu itemSuggestions = new ContextMenu();
    private Item selectedItem;
    private boolean updatingItemSearch;

    @FXML
    private Button btnAddLine;
    @FXML private DatePicker dpDueDate, dpDeliveryDate;
    @FXML private ComboBox<String> cmbWarehouse,cmbPaymentTerms,cmbCurrency,cmbGstTreatment,cmbTransporter,cmbDiscountType,cmbGstType;
    @FXML private TextField txtReference,txtLrAwb,txtDiscount;
    @FXML private Label lblAttachment;
    @FXML private Button btnAttachmentAdd, btnAttachmentPreview, btnAttachmentRemove;
    @FXML private ListView<PurchaseAttachmentEntry> listAttachments;
    private final ObservableList<PurchaseAttachmentEntry> attachmentEntries = FXCollections.observableArrayList();
    private final java.util.Set<Long> attachmentRemovals = new java.util.LinkedHashSet<>();
    private record PurchaseAttachmentEntry(long id,String name,File localFile){
        boolean pending(){return id==0&&localFile!=null;}
        boolean legacy(){return id<0;}
        @Override public String toString(){return name==null||name.isBlank()?"Attachment":name;}
    }
    private final ObservableList<PurchaseCharge> invoiceCharges = FXCollections.observableArrayList();
    private final ObservableList<String> availableChargeTypes = FXCollections.observableArrayList();




    @FXML
    public void initialize(){
        if(purchasePageIcon!=null)purchasePageIcon.getChildren().setAll(IconFactory.icon("purchase",24));
        if(btnSaveView!=null)btnSaveView.setGraphic(IconFactory.compactIcon("save",15));
        if(savedViewsMenu!=null)savedViewsMenu.setGraphic(IconFactory.compactIcon("view",15));
        if(btnReset!=null)btnReset.setGraphic(IconFactory.compactIcon("refresh",15));
        if(btnRefresh!=null)btnRefresh.setGraphic(IconFactory.compactIcon("refresh",15));
        if (btnAddSupplier != null) { btnAddSupplier.setGraphic(IconFactory.compactIcon("supplier", 20)); btnAddSupplier.getProperties().put("erp-icon-preserve", true); }
        if (chkSameAsBilling != null) {
            Region noActionIcon = new Region();
            noActionIcon.setMinSize(0, 0); noActionIcon.setPrefSize(0, 0); noActionIcon.setMaxSize(0, 0);
            chkSameAsBilling.setGraphic(noActionIcon); chkSameAsBilling.setGraphicTextGap(0);
            chkSameAsBilling.getProperties().put("erp-icon-preserve", true);
        }
        if (btnManageCharges != null) { btnManageCharges.setGraphic(IconFactory.compactIcon("payment", 15)); btnManageCharges.getProperties().put("erp-icon-preserve", true); }
        if (btnAttachmentAdd != null) { btnAttachmentAdd.setGraphic(IconFactory.compactIcon("attachment", 14)); btnAttachmentAdd.getProperties().put("erp-icon-preserve", true); }
        if (btnAttachmentPreview != null) { btnAttachmentPreview.setGraphic(IconFactory.compactIcon("view", 14)); btnAttachmentPreview.getProperties().put("erp-icon-preserve", true); }
        if (btnAttachmentRemove != null) { btnAttachmentRemove.setGraphic(IconFactory.compactIcon("delete", 14)); btnAttachmentRemove.getProperties().put("erp-icon-preserve", true); }
        if (listAttachments != null) {
            listAttachments.setItems(attachmentEntries);
            listAttachments.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            listAttachments.getSelectionModel().selectedItemProperty().addListener((o,a,b)->updateAttachmentButtons());
        }
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

                // Select the correct item in the text-search control.
                allItems.stream().filter(item -> item.getItemCode().equals(newLine.getItemCode()))
                    .findFirst().ifPresent(item -> selectItem(item, false));
            }
        );

        // Build the JavaFX form immediately. Supplier/item/master data is loaded
        // in one background bootstrap so opening Create Purchase never waits on
        // server/database calls on the JavaFX Application Thread.
        cmbSupplier.setItems(FXCollections.observableArrayList());

        configureItemSearch();

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

        configureSupplierSearch();

        applyLookupDefaults(List.of(), List.of(), List.of("GST", "IGST"), List.of());
        if (cmbGstType != null) cmbGstType.valueProperty().addListener((obs,a,b) -> updateGstHeaders());
        if (chkSameAsBilling != null) chkSameAsBilling.selectedProperty().addListener((obs,a,b) -> syncDeliveryAddressState());
        if (txtBillingAddress != null) txtBillingAddress.textProperty().addListener((obs,a,b) -> { if (chkSameAsBilling != null && chkSameAsBilling.isSelected() && txtDeliveryAddress != null) txtDeliveryAddress.setText(b); });
        if (txtBillingGstin != null) txtBillingGstin.textProperty().addListener((obs,a,b) -> { if (chkSameAsBilling != null && chkSameAsBilling.isSelected() && txtDeliveryGstin != null) txtDeliveryGstin.setText(b); });
        invoiceCharges.addListener((javafx.collections.ListChangeListener<PurchaseCharge>) change -> { updateChargeManagerSummary(); recalculate(); });
        dpInvoiceDate.valueProperty().addListener((obs, oldDate, newDate) -> updateDueDate());
        cmbPaymentTerms.valueProperty().addListener((obs, oldTerm, newTerm) -> updateDueDate());
        cmbSupplier.valueProperty().addListener((obs, oldSupplier, supplier) -> { populateSupplierAddress(supplier); suggestGstTypeFromGstin(); });
        Platform.runLater(this::cleanPurchaseActions);
        resetNewPurchaseForm();
        loadPurchaseBootstrapAsync();
        Platform.runLater(() -> {
            if (editingPurchase == null && (txtInvoiceNo.getText() == null || txtInvoiceNo.getText().isBlank())) requestNextPurchaseNoAsync();
        });

    }



    private void configureItemSearch(){
        if(itemSearchIconBox!=null)itemSearchIconBox.getChildren().setAll(IconFactory.compactIcon("search", 16));
        itemSuggestions.getStyleClass().add("erp-item-suggestions");
        txtItemSearch.textProperty().addListener((obs,oldText,text)->{
            if(updatingItemSearch)return;
            selectedItem=null;
            refreshItemSuggestions(text);
        });
        txtItemSearch.focusedProperty().addListener((obs,oldValue,focused)->{
            if(focused&&!txtItemSearch.getText().isBlank())refreshItemSuggestions(txtItemSearch.getText());
            else if(!focused)itemSuggestions.hide();
        });
        txtItemSearch.setOnKeyPressed(event->{
            if(event.getCode()==javafx.scene.input.KeyCode.ESCAPE)itemSuggestions.hide();
            if(event.getCode()==javafx.scene.input.KeyCode.ENTER){Item match=resolveTypedItem(txtItemSearch.getText());if(match!=null)selectItem(match);}
        });
    }
    private void refreshItemSuggestions(String text){
        String q=text==null?"":text.trim().toLowerCase(java.util.Locale.ROOT);
        if(q.isBlank()||!txtItemSearch.isFocused()){itemSuggestions.hide();return;}
        List<Item> matches=allItems.stream().filter(item->itemSearchHaystack(item).contains(q)).limit(12).toList();
        itemSuggestions.getItems().clear();
        for(Item item:matches){MenuItem option=new MenuItem(itemDisplay(item),IconFactory.compactIcon("item",15));option.setOnAction(event->selectItem(item));itemSuggestions.getItems().add(option);}
        if(matches.isEmpty())itemSuggestions.hide();else if(!itemSuggestions.isShowing())itemSuggestions.show(txtItemSearch,javafx.geometry.Side.BOTTOM,0,2);
    }
    private void selectItem(Item item){selectItem(item,true);}
    /** Preserve the persisted transaction line while the Item Master identity is selected during edit. */
    private void selectItem(Item item,boolean applyMasterDefaults){
        selectedItem=item;updatingItemSearch=true;
        try{txtItemSearch.setText(item==null?"":itemDisplay(item));}finally{updatingItemSearch=false;}
        itemSuggestions.hide();
        if(item!=null&&applyMasterDefaults){txtRate.setText(String.format(java.util.Locale.ROOT,"%.2f",item.getPurchasePrice()));txtGST.setText(String.format(java.util.Locale.ROOT,"%.2f",item.getGst()));txtLineDiscount.setText(String.format(java.util.Locale.ROOT,"%.2f",item.getDiscountPercent()));}
    }
    private void clearItemSearch(){selectItem(null);}
    private Item resolveTypedItem(String text){
        if(selectedItem!=null)return selectedItem;String value=text==null?"":text.trim();if(value.isBlank())return null;
        return allItems.stream().filter(item->itemDisplay(item).equalsIgnoreCase(value)||safeItem(item.getItemCode()).equalsIgnoreCase(value)||safeItem(item.getDescription()).equalsIgnoreCase(value)||safeItem(item.getRemarks()).equalsIgnoreCase(value)).findFirst().orElse(null);
    }
    private String itemSearchHaystack(Item item){return (safeItem(item.getItemCode())+" "+safeItem(item.getDescription())+" "+safeItem(item.getRemarks())+" "+safeItem(item.getHsn())).toLowerCase(java.util.Locale.ROOT);}
    private String itemDisplay(Item item){if(item==null)return "";String remarks=safeItem(item.getRemarks()),description=safeItem(item.getDescription());if(remarks.isBlank())return description;if(description.isBlank())return remarks;return remarks+" • "+description;}
    private String safeItem(String value){return value==null?"":value.trim();}
    private String itemNameForDisplay(String itemCode,String persistedDescription){String code=safeItem(itemCode);if(!code.isBlank())for(Item item:allItems)if(code.equalsIgnoreCase(safeItem(item.getItemCode()))){String name=itemDisplay(item);if(!name.isBlank())return name;}String fallback=safeItem(persistedDescription);int separator=fallback.indexOf(" - ");return separator>=0&&separator+3<fallback.length()?fallback.substring(separator+3).trim():fallback;}



    private void setupTable(){


        colItem.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
            itemNameForDisplay(value.getValue().getItemCode(), value.getValue().getItemDescription())));


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


        Item item = resolveTypedItem(txtItemSearch.getText());


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



            clearItemSearch();

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

        clearItemSearch();

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
            if (!confirmAction("Remove line", "Remove the selected purchase line?")) return;

            tableLines.getItems().remove(line);

            recalculate();

        }

    }





    @FXML
    private void savePurchase(){ savePurchase("COMPLETED",false,false); }
    @FXML private void saveAndPrint(){ savePurchase("COMPLETED",true,false); }
    @FXML private void saveAndEmail(){ savePurchase("COMPLETED",false,true); }
    private void savePurchase(String documentStatus, boolean print, boolean email){
        Purchase purchase = buildPurchase();
        if(purchase == null) return;
        purchase.setDocumentStatus(documentStatus);

        boolean editing = editingPurchase != null;
        if(editing) purchase.setId(editingPurchase.getId());
        List<Long> removals = new ArrayList<>(attachmentRemovals);
        List<PurchaseAttachmentEntry> attachments = new ArrayList<>(attachmentEntries);

        setPurchaseSaveBusy(true);
        UiTaskExecutor.submitAction(
            "purchase-save",
            () -> persistPurchase(purchase, editing, removals, attachments, print, email),
            saved -> {
                setPurchaseSaveBusy(false);
                attachmentRemovals.clear();
                if(saved != null && saved.getInvoiceNo() != null) txtInvoiceNo.setText(saved.getInvoiceNo());
                org.example.util.ToastManager.success(tableLines,"Purchase saved","Purchase saved successfully.");
                ScreenRefreshPolicy.invalidate("purchase-register");
                NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml");
            },
            failure -> {
                setPurchaseSaveBusy(false);
                new OwnedAlert(Alert.AlertType.ERROR, rootMessage(failure)).showAndWait();
            }
        );
    }

    private Purchase persistPurchase(Purchase purchase, boolean editing, List<Long> removals,
                                     List<PurchaseAttachmentEntry> attachments, boolean print, boolean email) throws Exception {
        if(!editing && purchaseService.existsInvoice(purchase.getInvoiceNo())) {
            // Re-check in the worker immediately before save in case another client used the number.
            purchase.setInvoiceNo(purchaseService.nextInvoiceNo());
        }

        if(editing) purchaseService.update(purchase); else purchaseService.save(purchase);
        notifyPurchaseStatus(purchase.getInvoiceNo());

        Purchase full = purchaseService.getByInvoice(purchase.getInvoiceNo());
        if(full == null || full.getId() <= 0)
            throw new IllegalStateException("Saved purchase could not be reloaded for attachment update.");
        persistAttachmentChanges(full, removals, attachments);

        if(!ConfigManager.isApiDataEnabled()) saveMetadata(purchase);
        if(print) java.awt.Desktop.getDesktop().open(InvoicePdfService.purchase(full).toFile());
        if(email){
            if(full.getSupplier().getEmail()==null||full.getSupplier().getEmail().isBlank())
                throw new IllegalStateException("Supplier email is missing");
            EmailService.send(full.getSupplier().getEmail(),"Purchase "+full.getInvoiceNo(),
                    "Please find the purchase document attached.",InvoicePdfService.purchase(full));
            purchaseService.markEmailSent(full.getId());
        }
        return full;
    }

    private void setPurchaseSaveBusy(boolean busy){
        if(btnSavePurchase!=null) btnSavePurchase.setDisable(busy || viewMode);
    }

    private Purchase buildPurchase(){
        if(dpInvoiceDate.getValue()==null){warn("Select invoice date");return null;}
        if(cmbSupplier.getValue()==null){warn("Select supplier");return null;}
        if(txtDeliveryAddress!=null&&(txtDeliveryAddress.getText()==null||txtDeliveryAddress.getText().isBlank())){warn("Enter delivery address");return null;}
        if(chkSameAsBilling!=null&&!chkSameAsBilling.isSelected()
                && normalized(txtBillingAddress==null?"":txtBillingAddress.getText()).equals(normalized(txtDeliveryAddress==null?"":txtDeliveryAddress.getText()))
                && normalized(txtBillingGstin==null?"":txtBillingGstin.getText()).equals(normalized(txtDeliveryGstin==null?"":txtDeliveryGstin.getText()))){
            warn("Delivery address and GSTIN still match billing details. Select 'Same as Billing Address' or update the delivery details.");return null;
        }
        if(tableLines.getItems().isEmpty()){warn("Add items");return null;}
        if(cmbPaymentTerms.getValue()==null||cmbPaymentTerms.getValue().isBlank()){warn("Configure and select Payment Terms from Master Data.");return null;}
        if(cmbGstType!=null&&(cmbGstType.getValue()==null||cmbGstType.getValue().isBlank())){warn("Configure and select GST Type from Master Data.");return null;}
        String chargeError=validateCharges(invoiceCharges);if(chargeError!=null){warn(chargeError);return null;}

        Purchase purchase=new Purchase();
        // Preserve lifecycle/payment state while editing. Purchase entry owns header, lines, charges and attachments only.
        if(editingPurchase!=null){
            purchase.setId(editingPurchase.getId());
            purchase.setCreatedAt(editingPurchase.getCreatedAt());
            purchase.setPaidAmount(editingPurchase.getPaidAmount());
            purchase.setPaymentStatus(editingPurchase.getPaymentStatus());
            purchase.setDocumentStatus(editingPurchase.getDocumentStatus());
            purchase.setEmailSent(editingPurchase.isEmailSent());
            purchase.setCreatedBy(editingPurchase.getCreatedBy());
            purchase.setAttachmentPath(editingPurchase.getAttachmentPath());
        }
        purchase.setInvoiceNo(txtInvoiceNo.getText());
        purchase.setInvoiceDate(dpInvoiceDate.getValue());
        purchase.setSupplier(cmbSupplier.getValue());
        purchase.setLines(List.copyOf(tableLines.getItems()));

        DocumentCalculationEngine.Totals totals=documentTotals();
        double discount=totals.discountAmount();
        purchase.setSubtotal(totals.itemTaxable());
        purchase.setGstAmount(totals.taxAmount());
        purchase.setTotalAmount(totals.grandTotal());
        purchase.setCharges(invoiceCharges.stream().map(PurchaseCharge::copy).toList());
        purchase.setRemarks(editingPurchase==null?"":safeValue(editingPurchase.getRemarks(),""));
        purchase.setDueDate(dpDueDate.getValue());
        purchase.setDeliveryDate(dpDeliveryDate==null?dpDueDate.getValue():dpDeliveryDate.getValue());
        purchase.setWarehouse(editingPurchase==null?safeValue(cmbWarehouse.getValue(),"Main Warehouse"):safeValue(editingPurchase.getWarehouse(),cmbWarehouse.getValue()));
        purchase.setPaymentTerms(cmbPaymentTerms.getValue());
        purchase.setCurrency(editingPurchase==null?safeValue(cmbCurrency.getValue(),"INR - Indian Rupee"):safeValue(editingPurchase.getCurrency(),cmbCurrency.getValue()));
        purchase.setReferenceNo(txtReference.getText());
        purchase.setGstTreatment(editingPurchase==null?safeValue(cmbGstTreatment.getValue(),"Business Purchase"):safeValue(editingPurchase.getGstTreatment(),cmbGstTreatment.getValue()));
        purchase.setTransporter(cmbTransporter.getValue());
        purchase.setLrAwbNo(editingPurchase==null?"":safeValue(editingPurchase.getLrAwbNo(),""));
        purchase.setDiscountType("Item Level");
        purchase.setDiscountAmount(discount);
        purchase.setAttachmentPath(editingPurchase==null?purchase.getAttachmentPath():editingPurchase.getAttachmentPath());
        purchase.setBillingAddress(value(txtBillingAddress==null?null:txtBillingAddress.getText()));
        purchase.setDeliveryAddress(value(txtDeliveryAddress==null?null:txtDeliveryAddress.getText()));
        purchase.setBillingGstin(value(txtBillingGstin==null?null:txtBillingGstin.getText()));
        purchase.setDeliveryGstin(value(txtDeliveryGstin==null?null:txtDeliveryGstin.getText()));
        purchase.setGstType(cmbGstType==null?purchase.getGstTreatment():safeValue(cmbGstType.getValue(),purchase.getGstTreatment()));
        purchase.setTransporterGstin(value(txtTransporterGstin==null?null:txtTransporterGstin.getText()));
        purchase.setVehicleNumber(value(txtVehicleNumber==null?null:txtVehicleNumber.getText()));
        purchase.setContactPerson(value(txtContactPerson==null?null:txtContactPerson.getText()));
        purchase.setContactPersonMobile(value(txtContactPersonMobile==null?null:txtContactPersonMobile.getText()));
        purchase.setNotes(value(txtRemarks==null?null:txtRemarks.getText()));
        purchase.setOrderNo(value(txtOrderNo==null?null:txtOrderNo.getText()));
        purchase.setPoDate(txtPoDate==null?null:txtPoDate.getValue());
        purchase.setSameAsBilling(chkSameAsBilling==null||chkSameAsBilling.isSelected());
        return purchase;
    }

    private String sanitizeAttachmentFileName(String value){String name=value==null?"attachment":value.replaceAll("[^A-Za-z0-9._() -]","_").trim();return name.isBlank()?"attachment":name;}

    private void saveMetadata(Purchase p) { purchaseService.update(p); }

    @FXML private void saveNote(){
        String note=txtRemarks==null?"":txtRemarks.getText();
        if(editingPurchase==null||editingPurchase.getId()<=0){
            NotificationService.add("Purchase note is ready and will be saved with the Purchase.");
            return;
        }
        int purchaseId=editingPurchase.getId();
        String invoiceNo=editingPurchase.getInvoiceNo();
        UiTaskExecutor.submitAction(
            "purchase-note-"+purchaseId,
            () -> { supportApi.notes("PURCHASE",purchaseId,note); return null; },
            ignored -> {
                if(editingPurchase!=null&&editingPurchase.getId()==purchaseId) editingPurchase.setNotes(note);
                NotificationService.add("Purchase note saved for "+invoiceNo+".");
            },
            failure -> warn("Unable to save purchase note: "+rootMessage(failure))
        );
    }

    @FXML private void chooseAttachment(){
        FileChooser chooser=new FileChooser();
        chooser.setTitle("Add purchase attachments");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents","*.pdf","*.png","*.jpg","*.jpeg","*.doc","*.docx","*.xls","*.xlsx","*.csv","*.txt","*.*"));
        List<File> files=chooser.showOpenMultipleDialog(tableLines.getScene().getWindow());
        if(files==null||files.isEmpty())return;
        for(File file:files){
            if(file==null||!file.isFile())continue;
            boolean duplicate=attachmentEntries.stream().anyMatch(a->a.localFile()!=null&&a.localFile().toPath().toAbsolutePath().normalize().equals(file.toPath().toAbsolutePath().normalize()));
            if(!duplicate)attachmentEntries.add(new PurchaseAttachmentEntry(0,file.getName(),file));
        }
        updateAttachmentButtons();
    }

    /** Applies staged attachment additions/removals only after the Purchase itself has saved successfully. */
    private void notifyPurchaseStatus(String invoiceNo){
        Purchase persisted=purchaseService.getByInvoice(invoiceNo);
        if(persisted==null)return;
        String status=safeValue(persisted.getDocumentStatus(), "").trim().toUpperCase(java.util.Locale.ROOT);
        if(status.isBlank())status="PENDING";
        if("PENDING APPROVAL".equals(status))return; // server already emits the exact approval notification
        NotificationService.createNotification(NotificationService.Category.PURCHASES,"Purchase "+invoiceNo+" • "+status,
                invoiceNo+" current document status: "+status+".","INFO","/fxml/pages/PurchaseList.fxml",invoiceNo);
    }

    private void persistAttachmentChanges(Purchase full, List<Long> removals, List<PurchaseAttachmentEntry> attachments){
        if(full==null||full.getId()<=0)return;
        for(Long id:removals){
            if(id==null)continue;
            if(id<0) supportApi.deleteDocumentAttachment("PURCHASE",full.getId());
            else supportApi.deleteDocumentAttachment("PURCHASE",full.getId(),id);
        }
        for(PurchaseAttachmentEntry entry:attachments){
            if(entry!=null&&entry.pending()&&entry.localFile()!=null)
                supportApi.addDocumentAttachment("PURCHASE",full.getId(),entry.localFile().toPath());
        }
    }

    @FXML private void saveAttachment(){
        if(editingPurchase==null||editingPurchase.getId()<=0){
            NotificationService.add("Purchase attachments are ready and will be saved with the Purchase.");return;
        }
        Purchase target=editingPurchase;
        List<Long> removals=new ArrayList<>(attachmentRemovals);
        List<PurchaseAttachmentEntry> attachments=new ArrayList<>(attachmentEntries);
        UiTaskExecutor.submitAction(
            "purchase-attachments-"+target.getId(),
            () -> { persistAttachmentChanges(target,removals,attachments); return loadAttachmentEntriesFromServer(target); },
            loaded -> {
                attachmentRemovals.clear();
                applyAttachmentEntries(target,loaded);
                NotificationService.add("Purchase attachments saved for "+target.getInvoiceNo()+".");
            },
            failure -> warn("Unable to save purchase attachments: "+rootMessage(failure))
        );
    }

    @FXML private void viewAttachment(){
        PurchaseAttachmentEntry entry=listAttachments==null?null:listAttachments.getSelectionModel().getSelectedItem();
        if(entry==null){warn("Select an attachment to preview.");return;}
        if(entry.pending()){
            openPurchaseAttachmentPath(entry.localFile()==null?null:entry.localFile().toPath());
            return;
        }
        if(editingPurchase==null||editingPurchase.getId()<=0){warn("The selected purchase attachment is not available.");return;}
        int purchaseId=editingPurchase.getId();
        UiTaskExecutor.submitLatest(
            "purchase-attachment-preview-"+purchaseId,
            () -> {
                SupportApiClient.DownloadedAttachment download=entry.legacy()?supportApi.documentAttachment("PURCHASE",purchaseId):supportApi.documentAttachment("PURCHASE",purchaseId,entry.id());
                return materializePurchaseAttachment(download);
            },
            this::openPurchaseAttachmentPath,
            failure -> warn("Unable to open the purchase attachment: "+rootMessage(failure))
        );
    }

    private void openPurchaseAttachmentPath(Path path){
        try{
            if(path==null||!Files.isRegularFile(path)){warn("The selected purchase attachment is not available.");return;}
            java.awt.Desktop.getDesktop().open(path.toFile());
        }catch(Exception e){warn("Unable to open the purchase attachment: "+e.getMessage());}
    }
    private Path materializePurchaseAttachment(SupportApiClient.DownloadedAttachment download)throws Exception{return AttachmentPreviewSupport.materialize(download,"purchase-attachment");}

    @FXML private void removeAttachment(){
        PurchaseAttachmentEntry entry=listAttachments==null?null:listAttachments.getSelectionModel().getSelectedItem();
        if(entry==null){warn("Select an attachment to remove.");return;}
        if(!confirmAction("Remove attachment","Remove "+entry.name()+" from this Purchase?"))return;
        if(!entry.pending())attachmentRemovals.add(entry.id());
        attachmentEntries.remove(entry);
        updateAttachmentButtons();
    }

    private void loadAttachmentEntries(Purchase purchase){
        attachmentEntries.clear();attachmentRemovals.clear();updateAttachmentButtons();
        if(purchase==null||purchase.getId()<=0)return;
        UiTaskExecutor.submitLatest(
            "purchase-attachments-load-"+purchase.getId(),
            () -> loadAttachmentEntriesFromServer(purchase),
            loaded -> applyAttachmentEntries(purchase,loaded),
            failure -> {
                // Attachments are secondary data; keep the Purchase usable if this read fails.
                applyAttachmentEntries(purchase,List.of());
            }
        );
    }

    private List<PurchaseAttachmentEntry> loadAttachmentEntriesFromServer(Purchase purchase){
        List<PurchaseAttachmentEntry> loaded=new ArrayList<>();
        if(purchase!=null&&purchase.getId()>0){
            try{for(SupportApiClient.AttachmentMeta meta:supportApi.documentAttachments("PURCHASE",purchase.getId()))
                loaded.add(new PurchaseAttachmentEntry(meta.id(),meta.fileName(),null));}
            catch(Exception ignored){}
            if(loaded.isEmpty()&&purchase.getAttachmentPath()!=null&&!purchase.getAttachmentPath().isBlank()){
                String name=purchase.getAttachmentPath();try{name=Path.of(name).getFileName().toString();}catch(Exception ignored){}
                loaded.add(new PurchaseAttachmentEntry(-1,name,null));
            }
        }
        return loaded;
    }

    private void applyAttachmentEntries(Purchase purchase,List<PurchaseAttachmentEntry> loaded){
        if(editingPurchase!=null&&purchase!=null&&editingPurchase.getId()!=purchase.getId())return;
        attachmentEntries.setAll(loaded==null?List.of():loaded);
        updateAttachmentButtons();
    }
    private void updateAttachmentButtons(){
        PurchaseAttachmentEntry selected=listAttachments==null?null:listAttachments.getSelectionModel().getSelectedItem();
        if(btnAttachmentPreview!=null)btnAttachmentPreview.setDisable(selected==null);
        if(btnAttachmentRemove!=null)btnAttachmentRemove.setDisable(viewMode || selected==null);
        if(lblAttachment!=null)lblAttachment.setText(attachmentEntries.isEmpty()?"No attachments":attachmentEntries.size()+" attachment"+(attachmentEntries.size()==1?"":"s"));
    }

    @FXML private void clearLines(){
        if(tableLines.getItems().isEmpty()) return;
        if(!confirmAction("Clear purchase items", "Remove all current lines from this purchase?")) return;
        tableLines.getItems().clear();recalculate();
    }
    @FXML private void preview(){new OwnedAlert(Alert.AlertType.INFORMATION,"Preview is available after saving the purchase.").showAndWait();}
    public void prepareDuplicate(){editingPurchase=null;attachmentEntries.clear();attachmentRemovals.clear();updateAttachmentButtons();txtInvoiceNo.clear();requestNextPurchaseNoAsync();}
    private double parse(String v){try{return v==null||v.isBlank()?0:Double.parseDouble(v);}catch(Exception e){return 0;}}private String str(LocalDate d){return d==null?null:d.toString();}





    @FXML
    private void newPurchase(){
        resetNewPurchaseForm();
        requestNextPurchaseNoAsync();
    }

    private void resetNewPurchaseForm(){
        editingPurchase = null;
        attachmentEntries.clear();
        attachmentRemovals.clear();
        updateAttachmentButtons();
        if (txtReference != null) txtReference.clear();
        if (txtLrAwb != null) txtLrAwb.clear();
        txtInvoiceNo.clear();
        dpInvoiceDate.setValue(BusinessClock.today());
        dpDueDate.setValue(BusinessClock.today().plusDays(15));
        dpDeliveryDate.setValue(BusinessClock.today());
        cmbSupplier.setValue(null);
        if(txtBillingAddress!=null)txtBillingAddress.clear();
        if(txtDeliveryAddress!=null)txtDeliveryAddress.clear();
        if(txtBillingGstin!=null)txtBillingGstin.clear();
        if(txtDeliveryGstin!=null)txtDeliveryGstin.clear();
        if(txtTransporterGstin!=null)txtTransporterGstin.clear();
        if(txtVehicleNumber!=null)txtVehicleNumber.clear();
        if(txtContactPerson!=null)txtContactPerson.clear();
        if(txtContactPersonMobile!=null)txtContactPersonMobile.clear();
        if(txtOrderNo!=null)txtOrderNo.clear();
        if(txtPoDate!=null)txtPoDate.setValue(null);
        if(chkSameAsBilling!=null)chkSameAsBilling.setSelected(true);syncDeliveryAddressState();
        invoiceCharges.clear();
        clearItemSearch();
        txtQuantity.clear();
        txtRate.clear();
        txtGST.clear();
        txtLineDiscount.clear();
        txtRemarks.clear();
        tableLines.getItems().clear();
        recalculate();
    }

    private void requestNextPurchaseNoAsync(){
        UiTaskExecutor.submitLatest(
            "create-purchase-next-number",
            purchaseService::nextInvoiceNo,
            number -> { if (editingPurchase == null) txtInvoiceNo.setText(number == null ? "" : number); },
            failure -> warn("Could not generate the next Purchase No: " + rootMessage(failure))
        );
    }

    private record PurchaseBootstrap(
        List<Party> suppliers, List<Item> items, List<String> paymentTerms,
        List<String> transporters, List<String> gstTypes, List<String> charges,
        List<String> errors) { }

    private void loadPurchaseBootstrapAsync() {
        UiTaskExecutor.submitLatest(
            "create-purchase-bootstrap",
            this::loadPurchaseBootstrap,
            this::applyPurchaseBootstrap,
            failure -> warn("Purchase master data could not be loaded: " + rootMessage(failure))
        );
    }

    private PurchaseBootstrap loadPurchaseBootstrap() {
        List<String> errors = new ArrayList<>();
        List<Party> suppliers = loadPurchaseValue("Suppliers", errors, () -> partyService.search("SUPPLIER", "", 40), List.of());
        List<Item> items = loadPurchaseValue("Items", errors, itemService::getAll, List.of());
        List<String> paymentTerms = loadPurchaseValue("Payment Terms", errors, () -> lookupService.getValuesByCategoryCode("PAYMENT_TERMS"), List.of());
        List<String> transporters = loadPurchaseValue("Transporters", errors, () -> lookupService.getValuesByCategoryCode("TRANSPORTER"), List.of());
        List<String> gstTypes = loadPurchaseValue("GST Types", errors, () -> lookupService.getValuesByCategoryCode("GST_TYPE"), List.of("GST", "IGST"));
        List<String> charges = loadPurchaseValue("Charges", errors, () -> lookupService.getValuesByCategoryCode("CHARGES"), List.of());
        return new PurchaseBootstrap(
            suppliers == null ? List.of() : List.copyOf(suppliers),
            items == null ? List.of() : List.copyOf(items),
            paymentTerms == null ? List.of() : List.copyOf(paymentTerms),
            transporters == null ? List.of() : List.copyOf(transporters),
            gstTypes == null || gstTypes.isEmpty() ? List.of("GST", "IGST") : List.copyOf(gstTypes),
            charges == null ? List.of() : List.copyOf(charges),
            List.copyOf(errors));
    }

    private <T> T loadPurchaseValue(String label, List<String> errors, java.util.concurrent.Callable<T> loader, T fallback) {
        try {
            T value = loader.call();
            return value == null ? fallback : value;
        } catch (Exception exception) {
            errors.add(label + ": " + rootMessage(exception));
            return fallback;
        }
    }

    private void applyPurchaseBootstrap(PurchaseBootstrap data) {
        cmbSupplier.getItems().setAll(data.suppliers());
        allItems.setAll(data.items());
        applyLookupDefaults(data.paymentTerms(), data.transporters(), data.gstTypes(), data.charges());

        if (editingPurchase != null) {
            selectPurchaseSupplier(editingPurchase.getSupplier());
            select(cmbPaymentTerms, editingPurchase.getPaymentTerms());
            select(cmbTransporter, editingPurchase.getTransporter());
            if (cmbGstType != null) select(cmbGstType, safeValue(editingPurchase.getGstType(), editingPurchase.getGstTreatment()));
            tableLines.refresh();
        }

        if (!data.errors().isEmpty()) {
            System.err.println("Create Purchase bootstrap warnings: " + String.join(" | ", data.errors()));
        }
    }

    private void applyLookupDefaults(List<String> paymentTerms, List<String> transporters, List<String> gstTypes, List<String> charges) {
        List<String> safeGstTypes = gstTypes == null || gstTypes.isEmpty() ? List.of("GST", "IGST") : gstTypes;
        cmbPaymentTerms.getItems().setAll(paymentTerms == null ? List.of() : paymentTerms);
        cmbTransporter.getItems().setAll(transporters == null ? List.of() : transporters);
        if(cmbGstType!=null){cmbGstType.getItems().setAll(safeGstTypes);if(!cmbGstType.getItems().isEmpty())cmbGstType.getSelectionModel().selectFirst();}
        availableChargeTypes.setAll(charges == null ? List.of() : charges);

        // Hidden compatibility fields retain stable defaults; they are no longer user-facing.
        cmbWarehouse.getItems().setAll("Main Warehouse");
        cmbCurrency.getItems().setAll("INR - Indian Rupee");
        cmbGstTreatment.getItems().setAll("Business Purchase");
        cmbDiscountType.getItems().setAll("Item Level");
        cmbWarehouse.setValue("Main Warehouse");
        cmbCurrency.setValue("INR - Indian Rupee");
        cmbGstTreatment.setValue("Business Purchase");
        cmbDiscountType.setValue("Item Level");

        if(cmbPaymentTerms.getItems().contains("15 Days"))cmbPaymentTerms.setValue("15 Days");
        else if(!cmbPaymentTerms.getItems().isEmpty())cmbPaymentTerms.getSelectionModel().selectFirst();
        if(!cmbTransporter.getItems().isEmpty())cmbTransporter.getSelectionModel().selectFirst();
    }

    private void selectPurchaseSupplier(Party supplier) {
        if (supplier == null) { cmbSupplier.setValue(null); return; }
        cmbSupplier.getItems().stream()
            .filter(candidate -> candidate.getId() == supplier.getId())
            .findFirst()
            .ifPresentOrElse(cmbSupplier::setValue, () -> {
                cmbSupplier.getItems().add(supplier);
                cmbSupplier.setValue(supplier);
            });
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? "Operation failed" : message;
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

    private void configureSupplierSearch() {
        cmbSupplier.setEditable(true);
        cmbSupplier.setConverter(new StringConverter<>() {
            @Override public String toString(Party party) { return supplierDisplay(party); }
            @Override public Party fromString(String text) {
                if (text == null || text.isBlank()) return null;
                String value = text.trim();
                return cmbSupplier.getItems().stream().filter(p -> supplierDisplay(p).equalsIgnoreCase(value) || safeValue(p.getPartyCode(), "").equalsIgnoreCase(value) || safeValue(p.getName(), "").equalsIgnoreCase(value)).findFirst().orElse(null);
            }
        });
        supplierSearchDebounce.setOnFinished(event -> searchSuppliers(cmbSupplier.getEditor().getText()));
        cmbSupplier.getEditor().textProperty().addListener((obs, oldText, text) -> {
            if (updatingSupplierSearch || !cmbSupplier.getEditor().isFocused()) return;
            Party selected = cmbSupplier.getValue();
            if (selected != null && supplierDisplay(selected).equalsIgnoreCase(safeValue(text, ""))) { supplierSearchDebounce.stop(); return; }
            supplierSearchDebounce.playFromStart();
        });
        cmbSupplier.showingProperty().addListener((obs, oldValue, showing) -> { if (showing && cmbSupplier.getItems().isEmpty()) searchSuppliers(""); });
    }

    private void searchSuppliers(String text) {
        String query = text == null ? "" : text.trim();
        UiTaskExecutor.submitLatest("create-purchase-supplier-search", () -> partyService.search("SUPPLIER", query, 30), suppliers -> {
            if (cmbSupplier.getEditor().isFocused() && !safeValue(cmbSupplier.getEditor().getText(), "").equalsIgnoreCase(query)) return;
            Party selected = cmbSupplier.getValue(); updatingSupplierSearch = true;
            try {
                List<Party> stable = new ArrayList<>(suppliers == null ? List.of() : suppliers);
                if (selected != null && stable.stream().noneMatch(p -> p.getId() == selected.getId())) stable.add(0, selected);
                cmbSupplier.getItems().setAll(stable);
                if (selected != null) selectPurchaseSupplier(selected);
            } finally { updatingSupplierSearch = false; }
            if (!cmbSupplier.getItems().isEmpty() && cmbSupplier.getEditor().isFocused() && !cmbSupplier.isShowing()) cmbSupplier.show();
        }, failure -> System.err.println("Create Purchase supplier search: " + rootMessage(failure)));
    }

    private String supplierDisplay(Party party) { return party == null ? "" : safeValue(party.getPartyCode(), "") + " - " + safeValue(party.getName(), ""); }

    /** Opens the standard themed supplier editor and refreshes this form afterwards. */
    @FXML
    private void addSupplier() {
        try {
            FXMLLoader loader = new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/PartyDialog.fxml"));
            Parent root = loader.load(); org.example.util.ProfessionalUiEnhancer.enhance(root);
            loader.<PartyDialogController>getController().configure("SUPPLIER", null);
            Stage dialog = new Stage();
            PlatformUiSupport.configureDialogStage(dialog, cmbSupplier, "Add Supplier", true);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene);
            dialog.setScene(scene);
            dialog.showAndWait();
            Party selected = cmbSupplier.getValue();
            UiTaskExecutor.submitLatest(
                "create-purchase-suppliers",
                () -> partyService.search("SUPPLIER", "", 40),
                suppliers -> {
                    cmbSupplier.getItems().setAll(suppliers == null ? List.of() : suppliers);
                    if (selected != null) selectPurchaseSupplier(selected);
                },
                failure -> warn("Supplier list could not be refreshed: " + rootMessage(failure))
            );
        } catch (Exception ex) {
            new OwnedAlert(Alert.AlertType.ERROR, "Unable to open supplier form: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void populateSupplierAddress(Party supplier) {
        String address=supplier==null?"":safeValue(supplier.getAddress(),"");
        String gstin=supplier==null?"":safeValue(supplier.getGstin(),"");
        if(txtBillingAddress!=null)txtBillingAddress.setText(address);
        if(txtBillingGstin!=null)txtBillingGstin.setText(gstin);
        syncDeliveryFromBilling();
    }

    private void syncDeliveryFromBilling(){
        if(chkSameAsBilling==null||!chkSameAsBilling.isSelected())return;
        if(txtDeliveryAddress!=null&&txtBillingAddress!=null)txtDeliveryAddress.setText(txtBillingAddress.getText());
        if(txtDeliveryGstin!=null&&txtBillingGstin!=null)txtDeliveryGstin.setText(txtBillingGstin.getText());
    }
    private void syncDeliveryAddressState(){
        boolean same=chkSameAsBilling!=null&&chkSameAsBilling.isSelected();
        if(same)syncDeliveryFromBilling();
        if(txtDeliveryAddress!=null){txtDeliveryAddress.setEditable(!same);txtDeliveryAddress.setDisable(false);txtDeliveryAddress.setOpacity(same?0.88:1.0);}
        if(txtDeliveryGstin!=null){txtDeliveryGstin.setEditable(!same);txtDeliveryGstin.setDisable(false);txtDeliveryGstin.setOpacity(same?0.88:1.0);}
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
        UiTaskExecutor.submitLatest(
            "purchase-draft-orders",
            () -> purchaseService.getAll().stream().filter(p->"DRAFT".equalsIgnoreCase(p.getDocumentStatus())).toList(),
            this::showPurchaseOrderPicker,
            failure -> warn("Unable to load draft purchase orders: "+rootMessage(failure))
        );
    }

    private void showPurchaseOrderPicker(List<Purchase> drafts){
        if(drafts==null||drafts.isEmpty()){warn("No draft purchase orders are available. Save a purchase as Draft first.");return;}
        ChoiceDialog<Purchase> dialog=new OwnedChoiceDialog<>(drafts.getFirst(),drafts);
        dialog.setTitle("Select Purchase Order");dialog.setHeaderText("Choose a draft purchase order to load");dialog.setContentText("Purchase order:");
        dialog.showAndWait().ifPresent(selected->UiTaskExecutor.submitLatest(
            "purchase-draft-order-load",
            ()->purchaseService.getByInvoice(selected.getInvoiceNo()),
            loaded->{if(loaded==null)warn("The selected draft purchase order is no longer available.");else loadPurchase(loaded);},
            failure->warn("Unable to load the selected purchase order: "+rootMessage(failure))
        ));
    }

    private void importPurchaseItems(){
        FileChooser chooser=new FileChooser();chooser.setTitle("Import Purchase Items");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File","*.csv"));File file=chooser.showOpenDialog(tableLines.getScene().getWindow());if(file==null)return;
        try{int count=0;for(String row:Files.readAllLines(file.toPath())){if(row.isBlank()||row.toLowerCase().startsWith("item"))continue;String[]v=row.split(",");if(v.length<3)throw new IllegalArgumentException("CSV columns must be: item_code,quantity,rate,gst_percent");Item item=allItems.stream().filter(i->i.getItemCode().equalsIgnoreCase(v[0].trim())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown item code: "+v[0]));double q=Double.parseDouble(v[1].trim()),rate=Double.parseDouble(v[2].trim()),gst=v.length>3?Double.parseDouble(v[3].trim()):item.getGst();PurchaseLine line=new PurchaseLine();line.setItemCode(item.getItemCode());line.setItemDescription(item.getItemCode()+" - "+item.getDescription());line.setQuantity(q);line.setRate(rate);line.setGstPercent(gst);recalculateLine(line);tableLines.getItems().add(line);count++;}recalculate();org.example.util.ToastManager.success(tableLines,"Import complete",count+" purchase item(s) imported.");}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,"Could not import items: "+e.getMessage()).showAndWait();}
    }



    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("purchase-");}

    private void recalculate(){
        DocumentCalculationEngine.Totals totals = documentTotals();
        lblNetAmount.setText(String.format("₹ %.2f", totals.itemTaxable()));
        lblDiscount.setText(String.format("₹ %.2f", totals.discountAmount()));
        lblGst.setText(String.format("₹ %.2f", totals.taxAmount()));
        if(lblCharges!=null)lblCharges.setText(String.format("₹ %.2f", totals.chargeAmount()));
        lblGrandTotal.setText(String.format("₹ %.2f", totals.grandTotal()));
        if(lblTotalItems!=null)lblTotalItems.setText(Integer.toString(tableLines.getItems().size()));
        if(lblBottomDiscount!=null)lblBottomDiscount.setText(String.format("₹ %.2f", totals.discountAmount()));
        if(lblBottomTax!=null)lblBottomTax.setText(String.format("₹ %.2f", totals.taxAmount()));
        if(lblBottomCharges!=null)lblBottomCharges.setText(String.format("₹ %.2f", totals.chargeAmount()));
        if(lblBottomNet!=null)lblBottomNet.setText(String.format("₹ %.2f", totals.grandTotal()));
        if(lblTaxableAmount!=null)lblTaxableAmount.setText(String.format("₹ %.2f", totals.taxableAmount()));
        if(lblChargeCaption!=null)lblChargeCaption.setText(invoiceCharges.isEmpty()?"Additional Charges":"Additional Charges • "+invoiceCharges.size());
        if(lblCgst!=null)lblCgst.setText(String.format("₹ %.2f", totals.cgstAmount()));
        if(lblSgst!=null)lblSgst.setText(String.format("₹ %.2f", totals.sgstAmount()));
        if(lblIgst!=null)lblIgst.setText(String.format("₹ %.2f", totals.igstAmount()));
        updateChargeManagerSummary();
        updateGstHeaders();
    }

    private DocumentCalculationEngine.Totals documentTotals(){
        List<DocumentCalculationEngine.LineInput> lines = tableLines.getItems().stream()
                .map(line -> new DocumentCalculationEngine.LineInput(line.getQuantity(), line.getRate(), line.getDiscountPercent(), line.getGstPercent()))
                .toList();
        List<DocumentCalculationEngine.ChargeInput> charges = invoiceCharges.stream()
                .map(charge -> new DocumentCalculationEngine.ChargeInput(charge.getAmount(), charge.isTaxable(), charge.getGstPercent()))
                .toList();
        String taxType = cmbGstType == null ? "GST" : safeValue(cmbGstType.getValue(), "GST");
        return DocumentCalculationEngine.totals(lines, charges, DocumentCalculationEngine.taxMode(taxType));
    }

    @FXML
    private void manageCharges(){
        List<PurchaseCharge> draft=invoiceCharges.stream().map(PurchaseCharge::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Dialog<ButtonType> dialog=new OwnedDialog<>();
        dialog.setTitle("Purchase Additional Charges");
        dialog.setHeaderText("Add purchase charges with the same GST / IGST calculation rules as the invoice");
        dialog.getDialogPane().getStyleClass().add("sales-charge-dialog");
        VBox rows=new VBox(9); rows.getStyleClass().add("sales-charge-editor-rows");
        Label totals=new Label(); totals.getStyleClass().add("sales-charge-editor-total");
        Button add=new Button("Add Charge",IconFactory.compactIcon("add",14));
        add.getStyleClass().addAll("approved-button","approved-primary-button","sales-charge-add");
        Label limit=new Label("Add as many purchase charges as required"); limit.getStyleClass().add("sales-charge-limit");
        Region spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox addBar=new HBox(10,add,spacer,limit); addBar.setAlignment(Pos.CENTER_LEFT);
        Runnable updateTotals=()->{
            double amount=draft.stream().mapToDouble(PurchaseCharge::getAmount).sum();
            double tax=draft.stream().mapToDouble(PurchaseCharge::getTaxAmount).sum();
            totals.setText(String.format("Charges ₹ %,.2f    GST ₹ %,.2f    Total ₹ %,.2f",amount,tax,amount+tax));
        };
        Runnable[] render=new Runnable[1];
        render[0]=()->{
            rows.getChildren().clear();
            for(int index=0;index<draft.size();index++){
                PurchaseCharge charge=draft.get(index);
                ComboBox<String> type=new ComboBox<>(FXCollections.observableArrayList(availableChargeTypes));
                if(!charge.getChargeType().isBlank()&&!type.getItems().contains(charge.getChargeType()))type.getItems().add(charge.getChargeType());
                type.setValue(charge.getChargeType().isBlank()?null:charge.getChargeType());type.setPromptText("Select charge...");type.setMaxWidth(Double.MAX_VALUE);
                TextField amount=new TextField(charge.getAmount()<=0?"":String.format(Locale.ROOT,"%.2f",charge.getAmount())); amount.setPromptText("Amount");
                ComboBox<String> tax=new ComboBox<>(FXCollections.observableArrayList("Non-taxable","Taxable 0%","Taxable 5%","Taxable 12%","Taxable 18%","Taxable 28%"));
                tax.setValue(charge.isTaxable()?"Taxable "+percentText(charge.getGstPercent()):"Non-taxable");
                Button remove=new Button("Remove",IconFactory.compactIcon("delete",13)); remove.getStyleClass().addAll("approved-button","approved-danger-button","sales-charge-remove");
                int rowIndex=index;
                type.valueProperty().addListener((o,a,b)->charge.setChargeType(b));
                amount.textProperty().addListener((o,a,b)->{charge.setAmount(parseAmount(b));updateTotals.run();});
                tax.valueProperty().addListener((o,a,b)->{applyTaxTreatment(charge,b);updateTotals.run();});
                remove.setOnAction(e->{draft.remove(rowIndex);render[0].run();});
                GridPane row=new GridPane();row.setHgap(8);row.setVgap(3);row.getStyleClass().add("sales-charge-editor-row");
                row.add(new Label("Charge "+(index+1)),0,0);row.add(new Label("Amount"),1,0);row.add(new Label("Tax Treatment"),2,0);
                row.add(type,0,1);row.add(amount,1,1);row.add(tax,2,1);row.add(remove,3,1);GridPane.setHgrow(type,Priority.ALWAYS);rows.getChildren().add(row);
            }
            if(draft.isEmpty()){Label empty=new Label("No additional charges. Select Add Charge when required.");empty.getStyleClass().add("sales-charge-editor-empty");rows.getChildren().add(empty);}
            updateTotals.run();
        };
        add.setOnAction(e->{draft.add(new PurchaseCharge("",0,true,18));render[0].run();});render[0].run();
        ScrollPane scroller=new ScrollPane(rows);scroller.setFitToWidth(true);scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);scroller.setPrefViewportHeight(230);scroller.getStyleClass().add("sales-charge-editor-scroll");
        VBox content=new VBox(12,scroller,addBar,new Separator(),totals);content.setPrefWidth(720);
        dialog.getDialogPane().setContent(content);dialog.getDialogPane().setMinSize(700,450);dialog.setResizable(true);
        ButtonType apply=new ButtonType("Apply Charges",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,apply);
        Node applyButton=dialog.getDialogPane().lookupButton(apply);
        applyButton.addEventFilter(javafx.event.ActionEvent.ACTION,event->{String error=validateCharges(draft);if(error!=null){event.consume();warn(error);}});
        dialog.showAndWait().filter(apply::equals).ifPresent(result->invoiceCharges.setAll(draft.stream().map(PurchaseCharge::copy).toList()));
    }

    private void updateChargeManagerSummary(){
        if(lblChargeManagerSummary==null)return;
        double amount=invoiceCharges.stream().mapToDouble(PurchaseCharge::getAmount).sum();
        lblChargeManagerSummary.setText(invoiceCharges.isEmpty()?"No additional charges":String.format("%d charge%s · ₹ %,.2f",invoiceCharges.size(),invoiceCharges.size()==1?"":"s",amount));
    }
    private String validateCharges(List<PurchaseCharge> charges){
        if(charges==null||charges.isEmpty())return null;
        java.util.Set<String> names=new java.util.HashSet<>();
        for(PurchaseCharge charge:charges){if(charge==null||charge.getChargeType().isBlank())return "Select a charge type for every charge row.";if(charge.getAmount()<=0)return "Charge amount must be greater than zero.";if(!names.add(normalized(charge.getChargeType())))return "The same charge type cannot be selected twice.";}
        return null;
    }
    private void applyTaxTreatment(PurchaseCharge charge,String treatment){if(treatment==null||treatment.startsWith("Non")){charge.setTaxable(false);charge.setGstPercent(0);return;}charge.setTaxable(true);java.util.regex.Matcher m=java.util.regex.Pattern.compile("([0-9.]+)").matcher(treatment);charge.setGstPercent(m.find()?Double.parseDouble(m.group(1)):0);}
    private double parseAmount(String value){try{return value==null||value.isBlank()?0:Double.parseDouble(value.replace(",","").trim());}catch(Exception e){return 0;}}
    private String percentText(double value){return Math.rint(value)==value?String.format(Locale.ROOT,"%.0f%%",value):String.format(Locale.ROOT,"%.2f%%",value);}
    private String normalized(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);}

    private void updateGstHeaders(){
        // Keep the create screen usable while GST master values are still loading.
        // Server-side document validation remains strict; this is only the initial
        // JavaFX presentation state before a master value is available.
        String type=cmbGstType==null?"GST":safeValue(cmbGstType.getValue(),"GST");
        boolean igst=DocumentCalculationEngine.taxMode(type)==DocumentCalculationEngine.TaxMode.IGST;
        if(colGst!=null)colGst.setText(igst?"IGST %":"GST %");
        if(colGstAmount!=null)colGstAmount.setText(igst?"IGST Amount (₹)":"GST Amount (₹)");
        if(txtGST!=null)txtGST.setPromptText(igst?"IGST %":"GST %");
        if(lblTaxCaption!=null)lblTaxCaption.setText(igst?"Total IGST":"Total GST");
        if(lblBottomTaxCaption!=null)lblBottomTaxCaption.setText(igst?"IGST":"GST");
        setTaxRowVisible(rowCgst,!igst);
        setTaxRowVisible(rowSgst,!igst);
        setTaxRowVisible(rowIgst,igst);
    }
    private void setTaxRowVisible(HBox row,boolean visible){if(row!=null){row.setVisible(visible);row.setManaged(visible);}}
    private void suggestGstTypeFromGstin(){
        if(cmbGstType==null||cmbGstType.getItems().isEmpty()||txtBillingGstin==null)return;
        String company=ConfigManager.get("company.gstin","").trim(),supplier=txtBillingGstin.getText()==null?"":txtBillingGstin.getText().trim();
        if(company.length()<2||supplier.length()<2||!company.substring(0,2).matches("\\d{2}")||!supplier.substring(0,2).matches("\\d{2}"))return;
        boolean interstate=!company.substring(0,2).equals(supplier.substring(0,2));
        cmbGstType.getItems().stream().filter(v->{String n=v==null?"":v.toUpperCase(Locale.ROOT);return interstate?(n.contains("IGST")||n.contains("INTER")):(n.contains("GST")&&!n.contains("IGST")||n.contains("INTRA")||n.contains("CGST")||n.contains("SGST"));}).findFirst().ifPresent(cmbGstType::setValue);
    }




    private boolean confirmAction(String title, String message) {
        OwnedAlert alert = new OwnedAlert(
            Alert.AlertType.CONFIRMATION,
            message,
            ButtonType.CANCEL,
            ButtonType.OK
        );
        alert.setTitle(title);
        alert.setHeaderText(title);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void warn(String msg){

        new OwnedAlert(
            Alert.AlertType.WARNING,
            msg
        ).showAndWait();

    }





    @FXML
    private void cancel(){
        boolean dirty = !tableLines.getItems().isEmpty() || !attachmentEntries.isEmpty() || !attachmentRemovals.isEmpty();
        if (dirty && !confirmAction("Discard changes", "Discard unsaved changes and return to the Purchase register?")) return;

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
        attachmentEntries.clear();
        attachmentRemovals.clear();


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



        txtRemarks.setText(purchase.getNotes()==null ? "" : purchase.getNotes());
        if(txtBillingAddress!=null)txtBillingAddress.setText(safeValue(purchase.getBillingAddress(),purchase.getSupplier()==null?"":purchase.getSupplier().getAddress()));
        if(txtDeliveryAddress!=null)txtDeliveryAddress.setText(safeValue(purchase.getDeliveryAddress(),purchase.getBillingAddress()));
        if(txtBillingGstin!=null)txtBillingGstin.setText(safeValue(purchase.getBillingGstin(),purchase.getSupplier()==null?"":purchase.getSupplier().getGstin()));
        if(txtDeliveryGstin!=null)txtDeliveryGstin.setText(safeValue(purchase.getDeliveryGstin(),purchase.getBillingGstin()));
        if(chkSameAsBilling!=null)chkSameAsBilling.setSelected(purchase.isSameAsBilling());syncDeliveryAddressState();
        if(cmbGstType!=null)select(cmbGstType,safeValue(purchase.getGstType(),purchase.getGstTreatment()));
        if(txtTransporterGstin!=null)txtTransporterGstin.setText(value(purchase.getTransporterGstin()));
        if(txtVehicleNumber!=null)txtVehicleNumber.setText(value(purchase.getVehicleNumber()));
        if(txtContactPerson!=null)txtContactPerson.setText(value(purchase.getContactPerson()));
        if(txtContactPersonMobile!=null)txtContactPersonMobile.setText(value(purchase.getContactPersonMobile()));
        if(txtOrderNo!=null)txtOrderNo.setText(value(purchase.getOrderNo()));
        if(txtPoDate!=null)txtPoDate.setValue(purchase.getPoDate());
        invoiceCharges.setAll(purchase.getCharges()==null?List.of():purchase.getCharges().stream().map(PurchaseCharge::copy).toList());

        tableLines.getItems().clear();



        if(purchase.getLines()!=null){

            tableLines.getItems()
                .addAll(
                    purchase.getLines()
                );

        }

        dpDueDate.setValue(purchase.getDueDate());dpDeliveryDate.setValue(purchase.getDeliveryDate());select(cmbWarehouse,purchase.getWarehouse());select(cmbPaymentTerms,purchase.getPaymentTerms());select(cmbCurrency,purchase.getCurrency());select(cmbGstTreatment,purchase.getGstTreatment());select(cmbTransporter,purchase.getTransporter());select(cmbDiscountType,purchase.getDiscountType());txtReference.setText(value(purchase.getReferenceNo()));txtLrAwb.setText(value(purchase.getLrAwbNo()));txtDiscount.setText(String.valueOf(purchase.getDiscountAmount()));loadAttachmentEntries(purchase);


        recalculate();

    }

    public void setViewMode(boolean value){
        viewMode = value;
        txtInvoiceNo.setDisable(value);
        dpInvoiceDate.setDisable(value);
        cmbSupplier.setDisable(value);
        txtBillingAddress.setDisable(value);
        txtDeliveryAddress.setDisable(value);
        if(txtBillingGstin!=null)txtBillingGstin.setDisable(value);
        if(txtDeliveryGstin!=null)txtDeliveryGstin.setDisable(value);
        if(chkSameAsBilling!=null)chkSameAsBilling.setDisable(value);
        if(cmbPaymentTerms!=null)cmbPaymentTerms.setDisable(value);
        if(txtPoDate!=null)txtPoDate.setDisable(value);
        if(txtOrderNo!=null)txtOrderNo.setDisable(value);
        if(cmbGstType!=null)cmbGstType.setDisable(value);
        if(cmbTransporter!=null)cmbTransporter.setDisable(value);
        if(txtTransporterGstin!=null)txtTransporterGstin.setDisable(value);
        if(txtVehicleNumber!=null)txtVehicleNumber.setDisable(value);
        if(txtContactPerson!=null)txtContactPerson.setDisable(value);
        if(txtContactPersonMobile!=null)txtContactPersonMobile.setDisable(value);
        txtRemarks.setDisable(value);
        txtItemSearch.setDisable(value);
        txtQuantity.setDisable(value);
        txtRate.setDisable(value);
        txtGST.setDisable(value);
        txtLineDiscount.setDisable(value);
        btnAddLine.setDisable(value);
        if(btnRemoveLine!=null)btnRemoveLine.setDisable(value);
        if(btnAddSupplier!=null)btnAddSupplier.setDisable(value);
        if(btnManageCharges!=null)btnManageCharges.setDisable(value);
        if(btnAttachmentAdd!=null)btnAttachmentAdd.setDisable(value);
        if(btnAttachmentPreview!=null)btnAttachmentPreview.setDisable(listAttachments==null || listAttachments.getSelectionModel().getSelectedItem()==null);
        if(btnAttachmentRemove!=null)btnAttachmentRemove.setDisable(value || listAttachments==null || listAttachments.getSelectionModel().getSelectedItem()==null);
        if(btnSavePurchase!=null)btnSavePurchase.setDisable(value);
        tableLines.setEditable(!value);
        tableLines.setDisable(value);
        if(listAttachments!=null)listAttachments.setDisable(false);
        updateAttachmentButtons();
    }
    private void select(ComboBox<String> box,String value){if(value!=null&&!value.isBlank()){if(!box.getItems().contains(value))box.getItems().add(value);box.setValue(value);}}private String value(String v){return v==null?"":v;}
    private String safeValue(String value,String fallback){return value==null||value.isBlank()?(fallback==null?"":fallback):value;}
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
            line.setDiscountPercent(value);
            recalculateLine(line);
            tableLines.refresh();
            recalculate();
        });

    }

    private void recalculateLine(PurchaseLine line) {

        line.calculateAmounts();

    }
}
