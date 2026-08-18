package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedChoiceDialog;

import org.example.util.OwnedAlert;

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
import org.example.api.support.SupportApiClient;
import org.example.navigation.NavigationManager;
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
import javafx.application.Platform;
import javafx.scene.Node;
import org.example.util.IconFactory;
import org.example.theme.ThemeManager;
import org.example.config.WorkspaceManager;
import org.example.util.PlatformUiSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.time.LocalDate;
import java.util.List;
import java.io.File;


public class PurchaseController {
    @FXML private Button btnAddSupplier;
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
    @FXML private TextArea txtBillingAddress;


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



    private final ObservableList<Item> allItems=FXCollections.observableArrayList();

    private final ItemService itemService =
        new ItemService();


    private final PartyService partyService =
        new PartyService();


    private final PurchaseService purchaseService =
        new PurchaseService();
    private final LookupService lookupService = new LookupService();
    private final SupportApiClient supportApi = new SupportApiClient();

    private Purchase editingPurchase = null;
    private final ContextMenu itemSuggestions = new ContextMenu();
    private Item selectedItem;
    private boolean updatingItemSearch;

    @FXML
    private Button btnAddLine;
    @FXML private DatePicker dpDueDate, dpDeliveryDate;
    @FXML private ComboBox<String> cmbWarehouse,cmbPaymentTerms,cmbCurrency,cmbGstTreatment,cmbTransporter,cmbDiscountType;
    @FXML private TextField txtReference,txtLrAwb,txtDiscount;
    @FXML private Label lblAttachment;
    private File attachment;
    private boolean attachmentRemoved;




    @FXML
    public void initialize(){
        if(purchasePageIcon!=null)purchasePageIcon.getChildren().setAll(IconFactory.icon("purchase",24));
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

                // Select the correct item in the text-search control.
                allItems.stream().filter(item -> item.getItemCode().equals(newLine.getItemCode()))
                    .findFirst().ifPresent(this::selectItem);
            }
        );

        cmbSupplier.setItems(
            FXCollections.observableArrayList(
                partyService.getByType("SUPPLIER")
            )
        );


        allItems.setAll(itemService.getAll());


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


        populateLookups();
        dpInvoiceDate.valueProperty().addListener((obs, oldDate, newDate) -> updateDueDate());
        cmbPaymentTerms.valueProperty().addListener((obs, oldTerm, newTerm) -> updateDueDate());
        cmbSupplier.valueProperty().addListener((obs, oldSupplier, supplier) -> populateSupplierAddress(supplier));
        Platform.runLater(this::cleanPurchaseActions);
        newPurchase();

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
    private void selectItem(Item item){
        selectedItem=item;updatingItemSearch=true;
        try{txtItemSearch.setText(item==null?"":itemDisplay(item));}finally{updatingItemSearch=false;}
        itemSuggestions.hide();
        if(item!=null){txtRate.setText(String.format(java.util.Locale.ROOT,"%.2f",item.getPurchasePrice()));txtGST.setText(String.format(java.util.Locale.ROOT,"%.2f",item.getGst()));txtLineDiscount.setText(String.format(java.util.Locale.ROOT,"%.2f",item.getDiscountPercent()));}
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

            tableLines.getItems().remove(line);

            recalculate();

        }

    }





    @FXML
    private void savePurchase(){ savePurchase("COMPLETED",false,false); }
    @FXML private void saveDraft(){
        if(editingPurchase!=null && !"DRAFT".equalsIgnoreCase(editingPurchase.getDocumentStatus())){
            new OwnedAlert(Alert.AlertType.INFORMATION,"A posted purchase cannot be moved back to Draft. Use Save to update permitted details.").showAndWait();
            return;
        }
        savePurchase("DRAFT",false,false);
    }
    @FXML private void saveAndPrint(){ savePurchase("COMPLETED",true,false); }
    @FXML private void saveAndEmail(){ savePurchase("COMPLETED",false,true); }
    private void savePurchase(String documentStatus, boolean print, boolean email){

        Purchase purchase = buildPurchase();
        if(purchase == null) return;
        purchase.setDocumentStatus(documentStatus);

        Path copiedAttachment = null;
        String oldAttachment = editingPurchase == null ? null : editingPurchase.getAttachmentPath();
        boolean persisted = false;
        try {
            if(editingPurchase != null){
                purchase.setId(editingPurchase.getId());
            } else if (purchaseService.existsInvoice(purchase.getInvoiceNo())) {
                // Re-check immediately before saving in case another window used the number.
                String freshInvoiceNo = purchaseService.nextInvoiceNo();
                txtInvoiceNo.setText(freshInvoiceNo);
                purchase.setInvoiceNo(freshInvoiceNo);
            }

            if (attachment != null) {
                copiedAttachment = copyManagedPurchaseAttachment(attachment.toPath(), purchase.getInvoiceNo());
                purchase.setAttachmentPath(WorkspaceManager.getWorkspaceRoot().relativize(copiedAttachment).toString());
            } else if (attachmentRemoved) {
                purchase.setAttachmentPath(null);
            }

            if(editingPurchase != null){
                purchaseService.update(purchase);
                NotificationService.add("Purchase " + purchase.getInvoiceNo() + " updated");
            } else {
                purchaseService.save(purchase);
                NotificationService.add("Purchase " + purchase.getInvoiceNo() + " saved");
            }
            persisted = true;
            if (copiedAttachment != null || attachmentRemoved) deleteManagedAttachmentQuietly(oldAttachment, copiedAttachment);

            if (!org.example.config.ConfigManager.isApiDataEnabled()) saveMetadata(purchase);
            Purchase full = purchaseService.getByInvoice(purchase.getInvoiceNo());
            if(print) java.awt.Desktop.getDesktop().open(InvoicePdfService.purchase(full).toFile());
            if(email){
                if(full.getSupplier().getEmail()==null||full.getSupplier().getEmail().isBlank())throw new IllegalStateException("Supplier email is missing");
                EmailService.send(full.getSupplier().getEmail(),"Purchase "+full.getInvoiceNo(),"Please find the purchase document attached.",InvoicePdfService.purchase(full));
                purchaseService.markEmailSent(full.getId());
            }

            org.example.util.ToastManager.success(tableLines,"Purchase saved","Purchase saved successfully.");
            NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml");
        } catch(Exception e){
            if(!persisted && copiedAttachment != null){try{Files.deleteIfExists(copiedAttachment);}catch(Exception ignored){}}
            new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();
        }
    }

    private Purchase buildPurchase(){
        if(dpInvoiceDate.getValue()==null){warn("Select invoice date");return null;}
        if(cmbSupplier.getValue()==null){warn("Select supplier");return null;}
        if(tableLines.getItems().isEmpty()){warn("Add items");return null;}
        if(cmbPaymentTerms.getValue()==null||cmbPaymentTerms.getValue().isBlank()){
            warn("Configure and select Payment Terms from Master Data.");
            return null;
        }

        Purchase purchase=new Purchase();
        purchase.setInvoiceNo(txtInvoiceNo.getText());
        purchase.setInvoiceDate(dpInvoiceDate.getValue());
        purchase.setSupplier(cmbSupplier.getValue());
        purchase.setLines(List.copyOf(tableLines.getItems()));

        double net=tableLines.getItems().stream().mapToDouble(PurchaseLine::getNetAmount).sum();
        double discount=tableLines.getItems().stream().mapToDouble(PurchaseLine::getDiscountAmount).sum();
        double gst=tableLines.getItems().stream().mapToDouble(PurchaseLine::getGstAmount).sum();
        purchase.setSubtotal(net);
        purchase.setGstAmount(gst);
        purchase.setTotalAmount(net+gst);
        purchase.setRemarks(txtRemarks.getText());
        purchase.setDueDate(dpDueDate.getValue());
        purchase.setDeliveryDate(dpDueDate.getValue());
        purchase.setWarehouse(editingPurchase==null?safeValue(cmbWarehouse.getValue(),"Main Warehouse"):safeValue(editingPurchase.getWarehouse(),cmbWarehouse.getValue()));
        purchase.setPaymentTerms(cmbPaymentTerms.getValue());
        purchase.setCurrency(editingPurchase==null?safeValue(cmbCurrency.getValue(),"INR - Indian Rupee"):safeValue(editingPurchase.getCurrency(),cmbCurrency.getValue()));
        purchase.setReferenceNo(txtReference.getText());
        purchase.setGstTreatment(editingPurchase==null?safeValue(cmbGstTreatment.getValue(),"Business Purchase"):safeValue(editingPurchase.getGstTreatment(),cmbGstTreatment.getValue()));
        purchase.setTransporter(cmbTransporter.getValue());
        purchase.setLrAwbNo(editingPurchase==null?"":safeValue(editingPurchase.getLrAwbNo(),""));
        purchase.setDiscountType("Item Level");
        purchase.setDiscountAmount(discount);
        purchase.setAttachmentPath(attachmentRemoved?null:(editingPurchase==null?null:editingPurchase.getAttachmentPath()));
        return purchase;
    }

    private Path copyManagedPurchaseAttachment(Path source,String invoiceNo)throws Exception{
        if(source==null||!Files.isRegularFile(source))throw new IllegalStateException("Selected purchase attachment is no longer available.");
        Path folder=WorkspaceManager.getAttachmentsFolder().resolve("Purchase").resolve(safeAttachmentSegment(invoiceNo));
        Files.createDirectories(folder);
        String name=sanitizeAttachmentFileName(source.getFileName()==null?"attachment":source.getFileName().toString());
        Path target=folder.resolve(System.currentTimeMillis()+"-"+name);
        return Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteManagedAttachmentQuietly(String reference,Path replacement){
        try{
            if(reference==null||reference.isBlank())return;
            Path old=resolvePurchaseAttachment(reference);
            Path attachments=WorkspaceManager.getAttachmentsFolder().toAbsolutePath().normalize();
            if(old!=null&&old.toAbsolutePath().normalize().startsWith(attachments)
                    &&(replacement==null||!old.toAbsolutePath().normalize().equals(replacement.toAbsolutePath().normalize())))Files.deleteIfExists(old);
        }catch(Exception ignored){}
    }

    private Path resolvePurchaseAttachment(String reference){
        if(reference==null||reference.isBlank())return null;
        Path path=Path.of(reference);
        return path.isAbsolute()?path:WorkspaceManager.getWorkspaceRoot().resolve(path).normalize();
    }
    private String safeAttachmentSegment(String value){return value==null?"purchase":value.replaceAll("[^A-Za-z0-9._-]","_");}
    private String sanitizeAttachmentFileName(String value){String name=value==null?"attachment":value.replaceAll("[^A-Za-z0-9._() -]","_").trim();return name.isBlank()?"attachment":name;}

    private void saveMetadata(Purchase p) { purchaseService.update(p); }

    @FXML private void saveNote(){
        String note=txtRemarks==null?"":txtRemarks.getText();
        if(editingPurchase==null||editingPurchase.getId()<=0){
            NotificationService.add("Purchase note is ready and will be saved with the Purchase.");
            return;
        }
        try{
            supportApi.notes("PURCHASE",editingPurchase.getId(),note);
            editingPurchase.setRemarks(note);
            NotificationService.add("Purchase note saved for "+editingPurchase.getInvoiceNo()+".");
        }catch(Exception e){warn("Unable to save purchase note: "+e.getMessage());}
    }

    @FXML private void chooseAttachment(){FileChooser f=new FileChooser();f.setTitle("Choose purchase attachment");File selected=f.showOpenDialog(tableLines.getScene().getWindow());if(selected!=null){attachment=selected;attachmentRemoved=false;lblAttachment.setText(selected.getName());}}
    @FXML private void saveAttachment(){
        if(editingPurchase==null||editingPurchase.getId()<=0){
            NotificationService.add(attachmentRemoved?"Purchase attachment removal will be saved with the Purchase.":"Purchase attachment is ready and will be saved with the Purchase.");
            return;
        }
        Path copied=null;
        try{
            String oldRef=editingPurchase.getAttachmentPath();
            if(attachmentRemoved){
                supportApi.attachment("PURCHASE",editingPurchase.getId(),"");
                deleteManagedAttachmentQuietly(oldRef,null);
                editingPurchase.setAttachmentPath(null);
                attachment=null; attachmentRemoved=false;
                if(lblAttachment!=null)lblAttachment.setText("No document selected");
                NotificationService.add("Purchase attachment removed for "+editingPurchase.getInvoiceNo()+".");
                return;
            }
            if(attachment==null){warn("Choose an attachment first.");return;}
            copied=copyManagedPurchaseAttachment(attachment.toPath(),editingPurchase.getInvoiceNo());
            String managed=WorkspaceManager.getWorkspaceRoot().toAbsolutePath().normalize().relativize(copied.toAbsolutePath().normalize()).toString();
            supportApi.attachment("PURCHASE",editingPurchase.getId(),managed);
            deleteManagedAttachmentQuietly(oldRef,copied);
            editingPurchase.setAttachmentPath(managed);
            attachment=null; attachmentRemoved=false;
            if(lblAttachment!=null)lblAttachment.setText(copied.getFileName().toString());
            NotificationService.add("Purchase attachment saved for "+editingPurchase.getInvoiceNo()+".");
        }catch(Exception e){
            if(copied!=null){try{Files.deleteIfExists(copied);}catch(Exception ignored){}}
            warn("Unable to save purchase attachment: "+e.getMessage());
        }
    }
    @FXML private void viewAttachment(){
        try{Path path=attachment!=null?attachment.toPath():attachmentRemoved||editingPurchase==null?null:resolvePurchaseAttachment(editingPurchase.getAttachmentPath());if(path==null||!Files.isRegularFile(path)){warn("No purchase attachment is available.");return;}java.awt.Desktop.getDesktop().open(path.toFile());}
        catch(Exception e){warn("Unable to open the purchase attachment: "+e.getMessage());}
    }
    @FXML private void removeAttachment(){attachment=null;attachmentRemoved=true;if(lblAttachment!=null)lblAttachment.setText("No document selected");}
    @FXML private void clearLines(){tableLines.getItems().clear();recalculate();}
    @FXML private void preview(){new OwnedAlert(Alert.AlertType.INFORMATION,"Preview is available after saving the purchase.").showAndWait();}
    public void prepareDuplicate(){editingPurchase=null;attachment=null;attachmentRemoved=false;if(lblAttachment!=null)lblAttachment.setText("No document selected");txtInvoiceNo.setText(purchaseService.nextInvoiceNo());}
    private double parse(String v){try{return v==null||v.isBlank()?0:Double.parseDouble(v);}catch(Exception e){return 0;}}private String str(LocalDate d){return d==null?null:d.toString();}





    @FXML
    private void newPurchase(){

        editingPurchase = null;
        attachment = null;
        attachmentRemoved = false;
        if (lblAttachment != null) lblAttachment.setText("");
        if (txtReference != null) txtReference.clear();
        if (txtLrAwb != null) txtLrAwb.clear();


        txtInvoiceNo.setText(
            purchaseService.nextInvoiceNo()
        );

        dpInvoiceDate.setValue(
            BusinessClock.today()
        );
        dpDueDate.setValue(BusinessClock.today().plusDays(15));
        dpDeliveryDate.setValue(BusinessClock.today());


        cmbSupplier.setValue(null);
        if(txtBillingAddress!=null)txtBillingAddress.clear();

        clearItemSearch();


        txtQuantity.clear();

        txtRate.clear();

        txtGST.clear();
        txtLineDiscount.clear();


        txtRemarks.clear();


        tableLines.getItems().clear();


        recalculate();


    }

    private void populateLookups() {
        List<String> paymentTerms;
        List<String> transporters;
        try { paymentTerms = lookupService.getValuesByCategoryCode("PAYMENT_TERMS"); }
        catch(Exception e){ paymentTerms = List.of(); }
        try { transporters = lookupService.getValuesByCategoryCode("TRANSPORTER"); }
        catch(Exception e){ transporters = List.of(); }

        cmbPaymentTerms.getItems().setAll(paymentTerms);
        cmbTransporter.getItems().setAll(transporters);

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
            cmbSupplier.getItems().setAll(partyService.getByType("SUPPLIER"));
            if (selected != null) cmbSupplier.getSelectionModel().select(selected);
        } catch (Exception ex) {
            new OwnedAlert(Alert.AlertType.ERROR, "Unable to open supplier form: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void populateSupplierAddress(Party supplier) {
        if(txtBillingAddress==null)return;
        String address=supplier==null?"":safeValue(supplier.getAddress(),"");
        txtBillingAddress.setText(address);
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
        try{int count=0;for(String row:Files.readAllLines(file.toPath())){if(row.isBlank()||row.toLowerCase().startsWith("item"))continue;String[]v=row.split(",");if(v.length<3)throw new IllegalArgumentException("CSV columns must be: item_code,quantity,rate,gst_percent");Item item=allItems.stream().filter(i->i.getItemCode().equalsIgnoreCase(v[0].trim())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown item code: "+v[0]));double q=Double.parseDouble(v[1].trim()),rate=Double.parseDouble(v[2].trim()),gst=v.length>3?Double.parseDouble(v[3].trim()):item.getGst();PurchaseLine line=new PurchaseLine();line.setItemCode(item.getItemCode());line.setItemDescription(item.getItemCode()+" - "+item.getDescription());line.setQuantity(q);line.setRate(rate);line.setGstPercent(gst);recalculateLine(line);tableLines.getItems().add(line);count++;}recalculate();org.example.util.ToastManager.success(tableLines,"Import complete",count+" purchase item(s) imported.");}catch(Exception e){new OwnedAlert(Alert.AlertType.ERROR,"Could not import items: "+e.getMessage()).showAndWait();}
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
        attachment = null;
        attachmentRemoved = false;


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
        if(txtBillingAddress!=null)txtBillingAddress.setText(purchase.getSupplier()==null?"":safeValue(purchase.getSupplier().getAddress(),""));



        tableLines.getItems().clear();



        if(purchase.getLines()!=null){

            tableLines.getItems()
                .addAll(
                    purchase.getLines()
                );

        }

        dpDueDate.setValue(purchase.getDueDate());dpDeliveryDate.setValue(purchase.getDeliveryDate());select(cmbWarehouse,purchase.getWarehouse());select(cmbPaymentTerms,purchase.getPaymentTerms());select(cmbCurrency,purchase.getCurrency());select(cmbGstTreatment,purchase.getGstTreatment());select(cmbTransporter,purchase.getTransporter());select(cmbDiscountType,purchase.getDiscountType());txtReference.setText(value(purchase.getReferenceNo()));txtLrAwb.setText(value(purchase.getLrAwbNo()));txtDiscount.setText(String.valueOf(purchase.getDiscountAmount()));if (lblAttachment != null) { String ref=purchase.getAttachmentPath(); if(ref==null||ref.isBlank())lblAttachment.setText(""); else { try{lblAttachment.setText(Path.of(ref).getFileName().toString());}catch(Exception ignored){lblAttachment.setText(ref);} } }


        recalculate();

    }    public void setViewMode(boolean value){

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
