package org.example.controller;

import org.example.util.BusinessClock;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.api.support.SupportApiClient;
import org.example.config.WorkspaceManager;
import org.example.api.master.MasterApiClient;
import org.example.api.quotation.QuotationApiClient;
import org.example.model.Item;
import org.example.model.Party;
import org.example.navigation.NavigationManager;
import org.example.service.SessionService;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Full-page quotation workspace with inline line entry and a persistent summary. */
public final class QuotationEditorController {
    @FXML private Label lblPageTitle,lblPageSubtitle,lblSubtotal,lblDiscount,lblTaxable,lblGst,lblGrandTotal,lblLineCount,lblAttachmentName;
    @FXML private ComboBox<CustomerChoice> cmbCustomer;
    @FXML private TextField txtItemSearch;
    @FXML private StackPane itemSearchIconBox;
    @FXML private ComboBox<String> cmbSource;
    @FXML private DatePicker dpDate,dpValid,dpFollowUp;
    @FXML private TextField txtQuantity,txtGst,txtDiscount;
    @FXML private TextArea txtRemarks;
    @FXML private TableView<LineRow> tableLines;
    @FXML private TableColumn<LineRow,String> colItem,colCode;
    @FXML private TableColumn<LineRow,Number> colQty,colRate,colGst,colDiscount,colAmount;
    @FXML private TableColumn<LineRow,Void> colAction;
    @FXML private Button btnAdd,btnPreview,btnDraft,btnSaveSend,btnAttachmentAdd,btnAttachmentPreview,btnAttachmentRemove;
    @FXML private StackPane quotationTitleIcon;

    private final QuotationApiClient api=new QuotationApiClient();
    private final MasterApiClient masters=new MasterApiClient();
    private final SupportApiClient supportApi=new SupportApiClient();
    private Integer quotationId;
    private boolean dirty;
    private final ObservableList<ItemChoice> allItemChoices=FXCollections.observableArrayList();
    private final ContextMenu itemSuggestions=new ContextMenu();
    private ItemChoice selectedItem;
    private boolean updatingItemSearch;
    private Path selectedAttachment;
    private boolean attachmentRemovalPending;
    private String existingAttachment="";

    @FXML private void initialize(){
        if(quotationTitleIcon!=null)quotationTitleIcon.getChildren().setAll(IconFactory.icon("quotation",24));
        btnAdd.setGraphic(IconFactory.compactIcon("add",16));
        btnPreview.setGraphic(IconFactory.compactIcon("view",16));btnDraft.setGraphic(IconFactory.compactIcon("save",16));btnSaveSend.setGraphic(IconFactory.compactIcon("send",16));
        if(btnAttachmentAdd!=null)btnAttachmentAdd.setGraphic(IconFactory.compactIcon("attachment",15));if(btnAttachmentPreview!=null)btnAttachmentPreview.setGraphic(IconFactory.compactIcon("view",15));if(btnAttachmentRemove!=null)btnAttachmentRemove.setGraphic(IconFactory.compactIcon("delete",15));
        cmbSource.setItems(FXCollections.observableArrayList("Direct","Email","WhatsApp","Website","Referral","Other"));cmbSource.setValue("Direct");
        dpDate.setValue(BusinessClock.today());dpValid.setValue(BusinessClock.today().plusDays(30));dpFollowUp.setValue(BusinessClock.today().plusDays(7));
        configureTable();loadChoices();configureItemSearch();quotationId=QuotationEditorContext.consume();if(quotationId!=null)loadQuotation(quotationId);
        tableLines.getItems().addListener((javafx.collections.ListChangeListener<LineRow>)c->{dirty=true;updateTotals();});
        txtRemarks.textProperty().addListener((o,a,b)->dirty=true);
    }

    private void configureTable(){
        colItem.setCellValueFactory(v->new SimpleStringProperty(lineDisplay(v.getValue().code.get(),v.getValue().description.get())));
        colCode.setCellValueFactory(v->v.getValue().code);
        tableLines.getColumns().remove(colCode);
        colQty.setCellValueFactory(v->v.getValue().quantity);colRate.setCellValueFactory(v->v.getValue().rate);colGst.setCellValueFactory(v->v.getValue().gst);colDiscount.setCellValueFactory(v->v.getValue().discount);colAmount.setCellValueFactory(v->v.getValue().total);
        IconFactory.applyTableHeaderIcon(colItem,"item");
        IconFactory.applyTableHeaderIcon(colQty,"quantity");
        IconFactory.applyTableHeaderIcon(colRate,"currency");
        IconFactory.applyTableHeaderIcon(colGst,"tax");
        IconFactory.applyTableHeaderIcon(colDiscount,"discount");
        IconFactory.applyTableHeaderIcon(colAmount,"currency");
        IconFactory.applyTableHeaderIcon(colAction,"actions");
        for(TableColumn<LineRow,Number> column:List.of(colQty,colRate,colGst,colDiscount,colAmount))column.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Number value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:String.format(Locale.ENGLISH,"%,.2f",value.doubleValue()));setAlignment(Pos.CENTER_RIGHT);}});
        colAction.setCellFactory(c->new TableCell<>(){final Button remove=new Button("Remove",IconFactory.compactIcon("delete",14));{remove.getStyleClass().addAll("approved-button","danger-button");remove.setOnAction(e->{LineRow row=getTableRow()==null?null:(LineRow)getTableRow().getItem();if(row!=null)tableLines.getItems().remove(row);});}@Override protected void updateItem(Void value,boolean empty){super.updateItem(value,empty);setGraphic(empty?null:remove);}});
    }

    private String lineDisplay(String code,String persistedDescription){
        String normalized=safe(code).trim();
        for(ItemChoice item:allItemChoices){
            if(item.code.equalsIgnoreCase(normalized)) return item.toString();
        }
        return safe(persistedDescription).trim();
    }

    private void loadChoices(){
        try{
            cmbCustomer.getItems().setAll(masters.parties("CUSTOMER").stream().map(CustomerChoice::new).toList());
            allItemChoices.setAll(masters.items().stream().map(ItemChoice::new).toList());
        }catch(Exception e){error(e);}
    }

    private void configureItemSearch(){
        if(itemSearchIconBox!=null)itemSearchIconBox.getChildren().setAll(IconFactory.compactIcon("search", 16));
        itemSuggestions.getStyleClass().add("erp-item-suggestions");
        txtItemSearch.textProperty().addListener((obs,oldText,text)->{if(updatingItemSearch)return;selectedItem=null;refreshItemSuggestions(text);});
        txtItemSearch.focusedProperty().addListener((obs,oldValue,focused)->{if(focused&&!txtItemSearch.getText().isBlank())refreshItemSuggestions(txtItemSearch.getText());else if(!focused)itemSuggestions.hide();});
        txtItemSearch.setOnKeyPressed(event->{
            if(event.getCode()==javafx.scene.input.KeyCode.ESCAPE)itemSuggestions.hide();
            if(event.getCode()==javafx.scene.input.KeyCode.ENTER){ItemChoice match=resolveTypedItem(txtItemSearch.getText());if(match!=null)selectItem(match);}
        });
    }
    private void refreshItemSuggestions(String text){
        String q=text==null?"":text.trim().toLowerCase(Locale.ROOT);if(q.isBlank()||!txtItemSearch.isFocused()){itemSuggestions.hide();return;}
        List<ItemChoice> matches=allItemChoices.stream().filter(item->item.searchText().contains(q)).limit(12).toList();itemSuggestions.getItems().clear();
        for(ItemChoice item:matches){MenuItem option=new MenuItem(item.toString(),IconFactory.compactIcon("item",15));option.setOnAction(event->selectItem(item));itemSuggestions.getItems().add(option);}
        if(matches.isEmpty())itemSuggestions.hide();else if(!itemSuggestions.isShowing())itemSuggestions.show(txtItemSearch,javafx.geometry.Side.BOTTOM,0,2);
    }
    private void selectItem(ItemChoice item){selectedItem=item;updatingItemSearch=true;try{txtItemSearch.setText(item==null?"":item.toString());}finally{updatingItemSearch=false;}itemSuggestions.hide();if(item!=null){txtGst.setText(String.format(Locale.ENGLISH,"%.2f",item.gst));txtDiscount.setText(String.format(Locale.ENGLISH,"%.2f",item.discount));}}
    private void clearItemSearch(){selectItem(null);}
    private ItemChoice resolveTypedItem(String text){if(selectedItem!=null)return selectedItem;String value=text==null?"":text.trim();if(value.isBlank())return null;return allItemChoices.stream().filter(item->item.toString().equalsIgnoreCase(value)||item.code.equalsIgnoreCase(value)||item.description.equalsIgnoreCase(value)).findFirst().orElse(null);}


    private void loadQuotation(int id){
        try{
            QuotationApiClient.QuoteDto quote=api.list().stream().filter(q->q.id()==id).findFirst().orElseThrow(()->new IllegalStateException("Quotation was not found."));
            cmbCustomer.getItems().stream().filter(c->c.id==quote.customerId()).findFirst().ifPresent(cmbCustomer::setValue);
            dpDate.setValue(parse(quote.date()));dpValid.setValue(parse(quote.valid()));dpFollowUp.setValue(parse(quote.followUp()));cmbSource.setValue(blank(quote.source())?"Direct":quote.source());txtRemarks.setText(safe(quote.remarks()));
            tableLines.getItems().setAll(api.lines(id).stream().map(LineRow::new).toList());
            existingAttachment=safe(quote.attachment());selectedAttachment=null;attachmentRemovalPending=false;updateAttachmentLabel();
            lblPageTitle.setText("Edit Quotation");lblPageSubtitle.setText(quote.no()+"  |  "+quote.customer());dirty=false;updateTotals();
        }catch(Exception e){error(e);}
    }

    @FXML private void addItem(){
        try{ItemChoice item=Objects.requireNonNull(resolveTypedItem(txtItemSearch.getText()),"Select an item.");double qty=positive(txtQuantity.getText(),"Quantity");double gst=percent(txtGst.getText(),"GST");double discount=percent(txtDiscount.getText(),"Discount");tableLines.getItems().add(new LineRow(item.code,item.description,qty,item.rate,gst,discount));clearItemSearch();txtQuantity.setText("1.00");txtGst.setText("0.00");txtDiscount.setText("0.00");}catch(Exception e){error(e);}
    }
    @FXML private void preview(){updateTotals();new OwnedAlert(Alert.AlertType.INFORMATION,"Customer: "+(cmbCustomer.getValue()==null?"Not selected":cmbCustomer.getValue())+"\nItems: "+tableLines.getItems().size()+"\nGrand total: "+lblGrandTotal.getText()+"\nValid until: "+dpValid.getValue()).showAndWait();}
    @FXML private void saveDraft(){save(false);}
    @FXML private void saveAndSend(){save(true);}
    private void save(boolean send){
        try{
            CustomerChoice customer=Objects.requireNonNull(cmbCustomer.getValue(),"Select a customer.");if(dpDate.getValue()==null||dpValid.getValue()==null)throw new IllegalArgumentException("Quotation date and valid-until date are required.");if(dpValid.getValue().isBefore(dpDate.getValue()))throw new IllegalArgumentException("Valid-until date cannot be before quotation date.");if(tableLines.getItems().isEmpty())throw new IllegalArgumentException("Add at least one item.");
            double gross=tableLines.getItems().stream().mapToDouble(r->r.quantity.get()*r.rate.get()).sum(),discount=tableLines.getItems().stream().mapToDouble(r->r.discountAmount.get()).sum(),taxable=gross-discount,gst=tableLines.getItems().stream().mapToDouble(r->r.gstAmount.get()).sum(),total=taxable+gst;
            List<QuotationApiClient.LineDto> lines=tableLines.getItems().stream().map(r->new QuotationApiClient.LineDto(r.code.get(),r.description.get(),r.quantity.get(),r.rate.get(),r.gst.get(),r.discount.get(),r.total.get())).toList();
            QuotationApiClient.QuoteDto saved=api.save(new QuotationApiClient.SaveRequest(quotationId,dpDate.getValue().toString(),dpValid.getValue().toString(),customer.id,taxable,discount,gst,total,txtRemarks.getText(),dpFollowUp.getValue()==null?"":dpFollowUp.getValue().toString(),user(),cmbSource.getValue(),user(),lines));
            quotationId=saved.id();persistAttachment(saved.id());if(send)api.markSent(saved.id(),cmbSource.getValue());dirty=false;org.example.util.ToastManager.success(tableLines,send?"Quotation sent":"Quotation saved",send?"Quotation saved and marked as sent.":"Quotation draft saved successfully.");backToRegister();
        }catch(Exception e){error(e);}
    }
    private void backToRegister(){NavigationManager manager=NavigationManager.getInstance();if(manager!=null){manager.invalidate("/fxml/pages/Quotations.fxml");manager.loadPage("/fxml/pages/Quotations.fxml");}}
    @FXML private void chooseAttachment(){FileChooser chooser=new FileChooser();chooser.setTitle("Attach quotation document");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents","*.pdf","*.png","*.jpg","*.jpeg","*.doc","*.docx","*.xls","*.xlsx","*.csv","*.txt"));File file=chooser.showOpenDialog(tableLines.getScene().getWindow());if(file!=null){selectedAttachment=file.toPath();attachmentRemovalPending=false;updateAttachmentLabel();}}
    @FXML private void previewAttachment(){try{Path file=selectedAttachment;if(file==null&&!attachmentRemovalPending&&quotationId!=null&&!existingAttachment.isBlank())file=materializeAttachment(supportApi.documentAttachment("QUOTATION",quotationId));if(file==null||!Files.isRegularFile(file))throw new IllegalStateException("No quotation attachment is available.");Desktop.getDesktop().open(file.toFile());}catch(Exception e){error(e);}}
    @FXML private void removeAttachment(){if(selectedAttachment==null&&existingAttachment.isBlank())return;if(new OwnedAlert(Alert.AlertType.CONFIRMATION,"Remove the quotation attachment?",ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;selectedAttachment=null;attachmentRemovalPending=!existingAttachment.isBlank();updateAttachmentLabel();}
    private void persistAttachment(int id){if(attachmentRemovalPending){supportApi.deleteDocumentAttachment("QUOTATION",id);existingAttachment="";attachmentRemovalPending=false;}else if(selectedAttachment!=null){existingAttachment=supportApi.uploadDocumentAttachment("QUOTATION",id,selectedAttachment);selectedAttachment=null;}updateAttachmentLabel();}
    private void updateAttachmentLabel(){if(lblAttachmentName==null)return;if(selectedAttachment!=null)lblAttachmentName.setText(selectedAttachment.getFileName().toString());else if(attachmentRemovalPending)lblAttachmentName.setText("Attachment will be removed when saved");else if(!existingAttachment.isBlank())lblAttachmentName.setText("Attached: "+fileName(existingAttachment));else lblAttachmentName.setText("No document attached");}
    private Path materializeAttachment(SupportApiClient.DownloadedAttachment download)throws Exception{if(download==null||download.data()==null||download.data().length==0)return null;Path folder=WorkspaceManager.getTempFolder().resolve("AttachmentPreview");Files.createDirectories(folder);String name=fileName(download.fileName());if(name.isBlank())name="quotation-attachment";Path target=folder.resolve(System.currentTimeMillis()+"-"+name.replaceAll("[^A-Za-z0-9._-]","_"));Files.write(target,download.data());target.toFile().deleteOnExit();return target;}
    private static String fileName(String value){if(value==null||value.isBlank())return "";String normalized=value.replace('\\','/');int slash=normalized.lastIndexOf('/');return slash>=0?normalized.substring(slash+1):normalized;}
    private void updateTotals(){double gross=tableLines.getItems().stream().mapToDouble(r->r.quantity.get()*r.rate.get()).sum(),discount=tableLines.getItems().stream().mapToDouble(r->r.discountAmount.get()).sum(),taxable=gross-discount,gst=tableLines.getItems().stream().mapToDouble(r->r.gstAmount.get()).sum();lblSubtotal.setText(money(gross));lblDiscount.setText("- "+money(discount));lblTaxable.setText(money(taxable));lblGst.setText(money(gst));lblGrandTotal.setText(money(taxable+gst));lblLineCount.setText(tableLines.getItems().size()+" line item(s)");}
    private static double positive(String v,String name){double n=Double.parseDouble(v.trim());if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException(name+" must be greater than zero.");return n;}
    private static double percent(String v,String name){double n=v==null||v.isBlank()?0:Double.parseDouble(v.trim());if(!Double.isFinite(n)||n<0||n>100)throw new IllegalArgumentException(name+" must be between 0 and 100.");return n;}
    private static String money(double v){return "₹ "+String.format(Locale.ENGLISH,"%,.2f",v);}private static String safe(String v){return v==null?"":v;}private static boolean blank(String v){return v==null||v.isBlank();}private static LocalDate parse(String v){try{return blank(v)?null:LocalDate.parse(v.substring(0,10));}catch(Exception e){return null;}}private static String user(){return SessionService.current()==null?"System":SessionService.current().getFullName();}
    private void error(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?"Quotation operation failed.":e.getMessage()).showAndWait();}

    private static final class CustomerChoice{final int id;final String name;CustomerChoice(Party p){id=p.getId();name=p.getName();}@Override public String toString(){return name;}}
    private static final class ItemChoice{final String code,description,remarks,hsn;final double rate,gst,discount;ItemChoice(Item i){code=safe(i.getItemCode());description=safe(i.getDescription());remarks=safe(i.getRemarks());hsn=safe(i.getHsn());rate=i.getSellingPrice();gst=i.getGst();discount=i.getDiscountPercent();}String searchText(){return (code+" "+description+" "+remarks+" "+hsn).toLowerCase(Locale.ROOT);}@Override public String toString(){if(remarks.isBlank())return description;if(description.isBlank())return remarks;return remarks+" • "+description;}}
    public static final class LineRow{final StringProperty code=new SimpleStringProperty(),description=new SimpleStringProperty();final DoubleProperty quantity=new SimpleDoubleProperty(),rate=new SimpleDoubleProperty(),gst=new SimpleDoubleProperty(),discount=new SimpleDoubleProperty(),discountAmount=new SimpleDoubleProperty(),gstAmount=new SimpleDoubleProperty(),total=new SimpleDoubleProperty();LineRow(QuotationApiClient.LineDto l){this(l.code(),l.description(),l.quantity(),l.rate(),l.gst(),l.discount());}LineRow(String c,String d,double q,double r,double g,double disc){code.set(c);description.set(d);quantity.set(q);rate.set(r);gst.set(g);discount.set(disc);double gross=q*r,discountValue=gross*disc/100,taxable=gross-discountValue;discountAmount.set(discountValue);gstAmount.set(taxable*g/100);total.set(taxable+gstAmount.get());}}
}
