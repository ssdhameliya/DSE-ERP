package org.example.controller;

import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.example.api.quotation.QuotationApiClient;
import org.example.api.master.MasterApiClient;
import org.example.api.support.SupportApiClient;
import org.example.model.Party;
import org.example.navigation.NavigationManager;
import org.example.service.PartyService;
import org.example.config.ConfigManager;
import org.example.service.LookupService;
import org.example.service.SessionService;
import org.example.util.AttachmentPreviewSupport;
import org.example.util.BusinessClock;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.ScreenRefreshPolicy;
import org.example.util.UiTaskExecutor;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/** Sale-style Quotation create/edit workspace backed by the shared Master Data lookup service. */
public final class QuotationEditorController {
    @FXML private Label lblPageTitle,lblPageSubtitle,lblSubtotal,lblDiscount,lblTaxable,lblGst,lblGrandTotal,lblLineCount,lblAttachmentName;
    @FXML private ComboBox<CustomerChoice> cmbCustomer;
    @FXML private ComboBox<String> cmbSource;
    @FXML private DatePicker dpDate,dpValid,dpFollowUp;
    @FXML private TextField txtItemSearch,txtQuantity,txtRate,txtGst,txtDiscount;
    @FXML private TextArea txtRemarks;
    @FXML private TableView<LineRow> tableLines;
    @FXML private TableColumn<LineRow,String> colItem,colCode;
    @FXML private TableColumn<LineRow,Number> colQty,colRate,colGst,colDiscount,colAmount;
    @FXML private Button btnAdd,btnDeleteLine,btnSave,btnCancel,btnAttachmentAdd,btnAttachmentPreview,btnAttachmentRemove;
    @FXML private StackPane quotationTitleIcon,itemSearchIconBox;

    private final QuotationApiClient api=new QuotationApiClient();
    private final MasterApiClient masterApi=new MasterApiClient();
    private final PartyService partyService=new PartyService();
    private final LookupService lookupService=new LookupService();
    private final SupportApiClient supportApi=new SupportApiClient();
    private final ObservableList<ItemChoice> itemCache=FXCollections.observableArrayList();
    private final ContextMenu itemSuggestions=new ContextMenu();
    private final PauseTransition itemSearchDebounce=new PauseTransition(Duration.millis(170));
    private final PauseTransition customerSearchDebounce=new PauseTransition(Duration.millis(180));

    private Integer quotationId;
    private ItemChoice selectedItem;
    private LineRow editingLine;
    private int editingIndex=-1;
    private boolean updatingItemSearch,updatingCustomerSearch,dirty;
    private Path selectedAttachment;
    private boolean attachmentRemovalPending;
    private String existingAttachment="";

    @FXML private void initialize(){
        if(quotationTitleIcon!=null)quotationTitleIcon.getChildren().setAll(IconFactory.icon("quotation",24));
        if(itemSearchIconBox!=null)itemSearchIconBox.getChildren().setAll(IconFactory.compactIcon("search",16));
        btnAdd.setGraphic(IconFactory.compactIcon("add",16));
        btnDeleteLine.setGraphic(IconFactory.compactIcon("delete",16));
        btnSave.setGraphic(IconFactory.compactIcon("save",16));
        btnCancel.setGraphic(IconFactory.compactIcon("cancel",16));
        btnAttachmentAdd.setGraphic(IconFactory.compactIcon("attachment",15));
        btnAttachmentPreview.setGraphic(IconFactory.compactIcon("view",15));
        btnAttachmentRemove.setGraphic(IconFactory.compactIcon("delete",15));

        dpDate.setValue(BusinessClock.today());
        dpValid.setValue(BusinessClock.today().plusDays(30));
        dpFollowUp.setValue(BusinessClock.today().plusDays(7));
        configureTable();
        configureItemSearch();
        configureCustomerSearch();
        configureSourceRefresh();
        prepareMasterControlsForBootstrap();
        quotationId=QuotationEditorContext.consume();
        // Load Customer/Source independently from the quote bootstrap so the create screen
        // never waits on a combined endpoint and cannot remain stuck in Loading state.
        loadQuotationMastersAsync();
        loadEditorBootstrapAsync();
        tableLines.getItems().addListener((javafx.collections.ListChangeListener<LineRow>)change->{dirty=true;updateTotals();});
        txtRemarks.textProperty().addListener((o,a,b)->dirty=true);
    }


    private void prepareMasterControlsForBootstrap(){
        // Keep the controls usable while background master loading is in progress.
        // This matches Create Sale: the actual data source is loaded independently.
        cmbCustomer.setDisable(false);
        cmbSource.setDisable(false);
        cmbCustomer.setPromptText("Select Customer");
        cmbSource.setPromptText("Select Source");
    }

    private record QuotationMasterBootstrap(List<Party> customers, List<String> sources) {}

    private void loadQuotationMastersAsync(){
        UiTaskExecutor.submitLatest("quotation-editor-masters",()->{
            List<Party> customers = List.of();
            List<String> sources = List.of();
            try {
                if (ConfigManager.isApiDataEnabled()) {
                    customers = safeLoad(() -> masterApi.salesEntryBootstrap().customers());
                } else {
                    customers = safeLoad(() -> partyService.search("CUSTOMER", "", 40));
                }
            } catch (Exception ex) {
                customers = safeLoad(() -> partyService.search("CUSTOMER", "", 40));
            }
            try {
                if (ConfigManager.isApiDataEnabled()) {
                    sources = safeLoad(() -> masterApi.lookupValuesByCategoryCode("QUOTATION_SOURCE"));
                } else {
                    sources = safeLoad(() -> lookupService.getValuesByCategoryCode("QUOTATION_SOURCE"));
                }
            } catch (Exception ex) {
                sources = safeLoad(() -> lookupService.getValuesByCategoryCode("QUOTATION_SOURCE"));
            }
            return new QuotationMasterBootstrap(
                customers == null ? List.of() : List.copyOf(customers),
                sources == null ? List.of() : List.copyOf(sources));
        }, this::applyQuotationMasters, failure -> {
            cmbCustomer.setDisable(false);
            cmbSource.setDisable(false);
            cmbCustomer.setPromptText("Search customer...");
            cmbSource.setPromptText("Select from Master Data...");
            System.err.println("Quotation masters could not be loaded: " + message(failure));
        });
    }

    private static <T> T safeLoad(java.util.function.Supplier<T> loader){
        try { return loader.get(); } catch (Exception ignored) { return null; }
    }

    private void applyQuotationMasters(QuotationMasterBootstrap masters){
        if (masters == null) return;
        List<CustomerChoice> customers = (masters.customers() == null ? List.<Party>of() : masters.customers())
            .stream().filter(Objects::nonNull).map(CustomerChoice::new).toList();
        updatingCustomerSearch = true;
        try { cmbCustomer.getItems().setAll(customers); }
        finally { updatingCustomerSearch = false; }
        cmbCustomer.setDisable(false);
        cmbCustomer.setPromptText(customers.isEmpty() ? "No active Customers available" : "Search customer...");

        List<String> sources = (masters.sources() == null ? List.<String>of() : masters.sources()).stream()
            .filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
        cmbSource.getItems().setAll(sources);
        if (!sources.isEmpty() && cmbSource.getValue() == null) cmbSource.getSelectionModel().selectFirst();
        cmbSource.setDisable(false);
        cmbSource.setPromptText(sources.isEmpty() ? "No active Source in Master Data" : "Select from Master Data...");
    }

    private void configureSourceRefresh(){
        cmbSource.setOnShowing(event -> { if(!cmbSource.isDisabled()) refreshQuotationSources(); });
    }

    private void refreshQuotationSources(){
        String selected=safe(cmbSource.getValue()).trim();
        UiTaskExecutor.submitLatest("quotation-source-refresh",()->lookupService.getValuesByCategoryCode("QUOTATION_SOURCE"),rows->{
            List<String> values=(rows==null?List.<String>of():rows).stream()
                    .filter(Objects::nonNull).map(String::trim).filter(v->!v.isBlank())
                    .distinct().toList();
            cmbSource.getItems().setAll(values);
            String match=values.stream().filter(v->v.equalsIgnoreCase(selected)).findFirst().orElse(null);
            if(match!=null)cmbSource.setValue(match);
            else if(!values.isEmpty())cmbSource.getSelectionModel().selectFirst();
            cmbSource.setPromptText(values.isEmpty()?"No active Source in Master Data":"Select from Master Data...");
        },failure->{
            cmbSource.setPromptText("Source could not be loaded");
            System.err.println("Quotation Source refresh failed: "+message(failure));
        });
    }

    private void configureTable(){
        colItem.setCellValueFactory(v->v.getValue().description);
        colCode.setCellValueFactory(v->v.getValue().code);
        colQty.setCellValueFactory(v->v.getValue().quantity);
        colRate.setCellValueFactory(v->v.getValue().rate);
        colDiscount.setCellValueFactory(v->v.getValue().discount);
        colGst.setCellValueFactory(v->v.getValue().gst);
        colAmount.setCellValueFactory(v->v.getValue().total);
        IconFactory.applyTableHeaderIcon(colItem,"item");IconFactory.applyTableHeaderIcon(colCode,"document");IconFactory.applyTableHeaderIcon(colQty,"quantity");IconFactory.applyTableHeaderIcon(colRate,"currency");IconFactory.applyTableHeaderIcon(colDiscount,"discount");IconFactory.applyTableHeaderIcon(colGst,"tax");IconFactory.applyTableHeaderIcon(colAmount,"currency");
        for(TableColumn<LineRow,Number> column:List.of(colQty,colRate,colDiscount,colGst,colAmount))column.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Number value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:String.format(Locale.ENGLISH,"%,.2f",value.doubleValue()));setAlignment(Pos.CENTER_RIGHT);}});
        tableLines.getSelectionModel().selectedItemProperty().addListener((o,old,row)->editLine(row));
    }

    private void editLine(LineRow row){
        if(row==null){editingLine=null;editingIndex=-1;btnDeleteLine.setDisable(true);btnAdd.setText("Add Item");return;}
        editingLine=row;editingIndex=tableLines.getSelectionModel().getSelectedIndex();btnDeleteLine.setDisable(false);btnAdd.setText("Update Item");
        txtQuantity.setText(format(row.quantity.get()));txtRate.setText(format(row.rate.get()));txtDiscount.setText(format(row.discount.get()));txtGst.setText(format(row.gst.get()));
        ItemChoice cached=itemCache.stream().filter(i->i.code.equalsIgnoreCase(row.code.get())).findFirst().orElse(null);
        if(cached==null)cached=new ItemChoice(row.code.get(),row.description.get(),"","",row.rate.get(),row.gst.get(),row.discount.get());
        selectItem(cached,false);
    }

    private void configureItemSearch(){
        itemSuggestions.getStyleClass().add("erp-item-suggestions");
        itemSearchDebounce.setOnFinished(e->searchQuotationItems(txtItemSearch.getText()));
        txtItemSearch.textProperty().addListener((obs,oldText,text)->{if(updatingItemSearch)return;selectedItem=null;if(text==null||text.isBlank()){itemSearchDebounce.stop();itemSuggestions.hide();return;}itemSearchDebounce.playFromStart();});
        txtItemSearch.focusedProperty().addListener((obs,oldValue,focused)->{if(focused&&!txtItemSearch.getText().isBlank())itemSearchDebounce.playFromStart();else if(!focused)itemSuggestions.hide();});
        txtItemSearch.setOnKeyPressed(event->{
            if(event.getCode()==javafx.scene.input.KeyCode.ESCAPE)itemSuggestions.hide();
            if(event.getCode()==javafx.scene.input.KeyCode.ENTER){ItemChoice match=resolveTypedItem(txtItemSearch.getText());if(match!=null)selectItem(match,true);}
        });
    }

    private void searchQuotationItems(String text){
        String query=text==null?"":text.trim();if(query.isBlank()||!txtItemSearch.isFocused()){itemSuggestions.hide();return;}
        UiTaskExecutor.submitLatest("quotation-item-search",()->api.searchItems(query,12),rows->{
            if(!txtItemSearch.isFocused()||!safe(txtItemSearch.getText()).trim().equalsIgnoreCase(query))return;
            List<ItemChoice> matches=(rows==null?List.<QuotationApiClient.ItemChoiceDto>of():rows).stream().map(ItemChoice::new).toList();
            mergeItemCache(matches);showItemSuggestions(matches);
        },failure->{itemSuggestions.hide();error(asException(failure));});
    }

    private void mergeItemCache(List<ItemChoice> rows){for(ItemChoice row:rows){itemCache.removeIf(x->x.code.equalsIgnoreCase(row.code));itemCache.add(row);}}
    private void showItemSuggestions(List<ItemChoice> matches){itemSuggestions.getItems().clear();for(ItemChoice item:matches){MenuItem option=new MenuItem(item.toString(),IconFactory.compactIcon("item",15));option.setOnAction(event->selectItem(item,true));itemSuggestions.getItems().add(option);}if(matches.isEmpty())itemSuggestions.hide();else{itemSuggestions.hide();itemSuggestions.show(txtItemSearch,javafx.geometry.Side.BOTTOM,0,2);}}
    private ItemChoice resolveTypedItem(String text){if(selectedItem!=null)return selectedItem;String value=safe(text).trim();if(value.isBlank())return null;return itemCache.stream().filter(item->item.toString().equalsIgnoreCase(value)||item.code.equalsIgnoreCase(value)||item.description.equalsIgnoreCase(value)).findFirst().orElse(null);}
    private void selectItem(ItemChoice item,boolean applyMasterDefaults){selectedItem=item;updatingItemSearch=true;try{txtItemSearch.setText(item==null?"":item.toString());}finally{updatingItemSearch=false;}itemSuggestions.hide();if(item!=null&&applyMasterDefaults){txtRate.setText(format(item.rate));txtGst.setText(format(item.gst));txtDiscount.setText(format(item.discount));}}
    private void clearLineEditor(){selectedItem=null;updatingItemSearch=true;try{txtItemSearch.clear();}finally{updatingItemSearch=false;}txtQuantity.setText("1.00");txtRate.setText("0.00");txtDiscount.setText("0.00");txtGst.setText("0.00");editingLine=null;editingIndex=-1;btnAdd.setText("Add Item");btnDeleteLine.setDisable(true);tableLines.getSelectionModel().clearSelection();itemSuggestions.hide();}

    private void configureCustomerSearch(){
        cmbCustomer.setEditable(true);
        cmbCustomer.setConverter(new StringConverter<>(){@Override public String toString(CustomerChoice value){return value==null?"":value.display();}@Override public CustomerChoice fromString(String text){String q=safe(text).trim();if(q.isBlank())return null;return cmbCustomer.getItems().stream().filter(c->c.display().equalsIgnoreCase(q)||c.name.equalsIgnoreCase(q)||c.code.equalsIgnoreCase(q)).findFirst().orElse(null);}});
        customerSearchDebounce.setOnFinished(e->searchQuotationCustomers(cmbCustomer.getEditor().getText()));
        cmbCustomer.getEditor().textProperty().addListener((o,a,text)->{if(updatingCustomerSearch||!cmbCustomer.getEditor().isFocused())return;CustomerChoice selected=cmbCustomer.getValue();if(selected!=null&&selected.display().equalsIgnoreCase(safe(text))){customerSearchDebounce.stop();return;}customerSearchDebounce.playFromStart();});
        cmbCustomer.showingProperty().addListener((o,a,showing)->{if(showing&&cmbCustomer.getItems().isEmpty())searchQuotationCustomers("");});
    }
    private void searchQuotationCustomers(String text){String query=safe(text).trim();UiTaskExecutor.submitLatest("quotation-customer-search",()->partyService.search("CUSTOMER",query,30),rows->{if(cmbCustomer.getEditor().isFocused()&&!safe(cmbCustomer.getEditor().getText()).equalsIgnoreCase(query))return;CustomerChoice selected=cmbCustomer.getValue();List<CustomerChoice> choices=new ArrayList<>();if(rows!=null)for(Party p:rows)choices.add(new CustomerChoice(p));if(selected!=null&&choices.stream().noneMatch(c->c.id==selected.id))choices.add(0,selected);updatingCustomerSearch=true;try{cmbCustomer.getItems().setAll(choices);if(selected!=null)cmbCustomer.getItems().stream().filter(c->c.id==selected.id).findFirst().ifPresent(cmbCustomer::setValue);}finally{updatingCustomerSearch=false;}if(!choices.isEmpty()&&cmbCustomer.getEditor().isFocused()&&!cmbCustomer.isShowing())cmbCustomer.show();},failure->System.err.println("Quotation customer search: "+message(failure)));}
    private void selectQuotationCustomer(int customerId,String customerName){CustomerChoice local=cmbCustomer.getItems().stream().filter(c->c.id==customerId).findFirst().orElse(null);if(local!=null){cmbCustomer.setValue(local);return;}UiTaskExecutor.submitLatest("quotation-customer-selected",()->partyService.search("CUSTOMER",safe(customerName),30),rows->{CustomerChoice match=(rows==null?List.<Party>of():rows).stream().filter(p->p.getId()==customerId).findFirst().map(CustomerChoice::new).orElse(null);if(match!=null){cmbCustomer.getItems().add(0,match);cmbCustomer.setValue(match);}},failure->System.err.println("Quotation selected customer: "+message(failure)));}

    private void loadEditorBootstrapAsync(){
        Integer requestedId=quotationId;
        UiTaskExecutor.submitLatest("quotation-editor-bootstrap",()->api.editorBootstrap(requestedId),this::applyEditorBootstrap,failure->{
            System.err.println("Quotation editor bootstrap failed: "+message(failure)+"; using Sale-compatible independent master loading.");
            loadCustomerFallback();
            loadSourceFallback();
            if(requestedId!=null) error(asException(failure));
        });
    }

    private void loadCustomerFallback(){
        UiTaskExecutor.submitLatest("quotation-editor-customer-fallback",()->{
            // Match Create Sale: API-backed master data uses the consolidated sales
            // master endpoint; local mode reads the same PartyService used by Sale.
            if (ConfigManager.isApiDataEnabled()) return masterApi.salesEntryBootstrap().customers();
            return partyService.search("CUSTOMER","",40);
        },rows->{
            List<CustomerChoice> choices=new ArrayList<>();
            if(rows!=null) for(Party p:rows) choices.add(new CustomerChoice(p));
            CustomerChoice selected=cmbCustomer.getValue();
            if(selected!=null&&choices.stream().noneMatch(c->c.id==selected.id)) choices.add(0,selected);
            updatingCustomerSearch=true;
            try { cmbCustomer.getItems().setAll(choices); }
            finally { updatingCustomerSearch=false; }
            if(selected!=null) cmbCustomer.getItems().stream().filter(c->c.id==selected.id).findFirst().ifPresent(cmbCustomer::setValue);
            cmbCustomer.setDisable(false);
            cmbCustomer.setPromptText(choices.isEmpty()?"No active Customers available":"Search customer...");
        },failure->{
            cmbCustomer.setDisable(false);
            cmbCustomer.setPromptText("Search customer...");
            System.err.println("Quotation customer fallback failed: "+message(failure));
        });
    }

    private void loadSourceFallback(){
        UiTaskExecutor.submitLatest("quotation-editor-source-fallback",()->{
            if (ConfigManager.isApiDataEnabled()) return masterApi.lookupValuesByCategoryCode("QUOTATION_SOURCE");
            return lookupService.getValuesByCategoryCode("QUOTATION_SOURCE");
        },rows->{
            List<String> values=(rows==null?List.<String>of():rows).stream()
                    .filter(Objects::nonNull).map(String::trim).filter(v->!v.isBlank()).distinct().toList();
            String selected=safe(cmbSource.getValue()).trim();
            cmbSource.getItems().setAll(values);
            String match=values.stream().filter(v->v.equalsIgnoreCase(selected)).findFirst().orElse(null);
            if(match!=null) cmbSource.setValue(match);
            else if(!values.isEmpty()) cmbSource.getSelectionModel().selectFirst();
            cmbSource.setDisable(false);
            cmbSource.setPromptText(values.isEmpty()?"No active Source in Master Data":"Select from Master Data...");
        },failure->{
            cmbSource.setDisable(false);
            cmbSource.setPromptText("Select from Master Data...");
            System.err.println("Quotation source fallback failed: "+message(failure));
        });
    }
    private void applyEditorBootstrap(QuotationApiClient.EditorBootstrapDto data){
        List<QuotationApiClient.CustomerChoiceDto> customers=data==null||data.customers()==null?List.of():data.customers();
        List<String> sources=data==null||data.sources()==null?List.of():data.sources();
        if (!customers.isEmpty()) {
            updatingCustomerSearch = true;
            try { cmbCustomer.getItems().setAll(customers.stream().map(CustomerChoice::new).toList()); }
            finally { updatingCustomerSearch = false; }
        }
        if (!sources.isEmpty()) cmbSource.getItems().setAll(sources);
        cmbCustomer.setDisable(false);cmbSource.setDisable(false);
        cmbCustomer.setPromptText(customers.isEmpty()?"No active Customers available":"Search customer...");
        cmbSource.setPromptText(sources.isEmpty()?"No active Source in Master Data":"Select from Master Data...");
        if(!cmbSource.getItems().isEmpty())cmbSource.getSelectionModel().selectFirst();
        QuotationApiClient.QuoteDto quote=data==null?null:data.quote();
        if(quote!=null){selectQuotationCustomer(quote.customerId(),quote.customer());dpDate.setValue(parse(quote.date()));dpValid.setValue(parse(quote.valid()));dpFollowUp.setValue(parse(quote.followUp()));String savedSource=safe(quote.source()).trim();String activeSource=sources.stream().filter(v->v.equalsIgnoreCase(savedSource)).findFirst().orElse(null);cmbSource.setValue(activeSource!=null?activeSource:(savedSource.isBlank()?null:savedSource));txtRemarks.setText(safe(quote.remarks()));List<QuotationApiClient.LineDto> lines=data.lines()==null?List.of():data.lines();tableLines.getItems().setAll(lines.stream().map(LineRow::new).toList());existingAttachment=safe(quote.attachment());selectedAttachment=null;attachmentRemovalPending=false;lblPageTitle.setText("Edit Quotation");lblPageSubtitle.setText(quote.no()+"  |  "+quote.customer());dirty=false;}
        updateAttachmentLabel();updateTotals();tableLines.refresh();
    }

    @FXML private void addItem(){try{ItemChoice item=Objects.requireNonNull(resolveTypedItem(txtItemSearch.getText()),"Select an item from Item Master search.");double qty=positive(txtQuantity.getText(),"Quantity"),rate=nonNegative(txtRate.getText(),"Rate"),gst=percent(txtGst.getText(),"GST"),discount=percent(txtDiscount.getText(),"Discount");LineRow line=new LineRow(item.code,item.description,qty,rate,gst,discount);if(editingLine==null)tableLines.getItems().add(line);else{tableLines.getItems().set(editingIndex,line);}clearLineEditor();}catch(Exception e){error(e);}}
    @FXML private void deleteSelectedLine(){LineRow row=tableLines.getSelectionModel().getSelectedItem();if(row==null)return;tableLines.getItems().remove(row);clearLineEditor();}

    @FXML private void saveQuotation(){save();}
    private record SaveOutcome(QuotationApiClient.QuoteDto quote,String attachment){}
    private void save(){try{CustomerChoice customer=Objects.requireNonNull(cmbCustomer.getValue(),"Select a customer.");String source=safe(cmbSource.getValue()).trim();if(source.isBlank()||cmbSource.getItems().stream().noneMatch(v->v.equalsIgnoreCase(source)))throw new IllegalArgumentException("Select Quotation Source from Master Data.");if(dpDate.getValue()==null||dpValid.getValue()==null)throw new IllegalArgumentException("Quotation date and valid-until date are required.");if(dpValid.getValue().isBefore(dpDate.getValue()))throw new IllegalArgumentException("Valid-until date cannot be before quotation date.");if(tableLines.getItems().isEmpty())throw new IllegalArgumentException("Add at least one item.");updateTotals();double gross=tableLines.getItems().stream().mapToDouble(r->r.quantity.get()*r.rate.get()).sum(),discount=tableLines.getItems().stream().mapToDouble(r->r.discountAmount.get()).sum(),taxable=gross-discount,gst=tableLines.getItems().stream().mapToDouble(r->r.gstAmount.get()).sum(),total=taxable+gst;List<QuotationApiClient.LineDto> lines=tableLines.getItems().stream().map(r->new QuotationApiClient.LineDto(r.code.get(),r.description.get(),r.quantity.get(),r.rate.get(),r.gst.get(),r.discount.get(),r.total.get())).toList();Integer currentId=quotationId;Path pendingAttachment=selectedAttachment;boolean removeAttachment=attachmentRemovalPending;String currentAttachment=existingAttachment;var request=new QuotationApiClient.SaveRequest(currentId,dpDate.getValue().toString(),dpValid.getValue().toString(),customer.id,taxable,discount,gst,total,txtRemarks.getText(),dpFollowUp.getValue()==null?"":dpFollowUp.getValue().toString(),user(),source,user(),lines);setSaveBusy(true);UiTaskExecutor.submitAction("quotation-editor-save",()->{QuotationApiClient.QuoteDto saved=api.save(request);String attachment=currentAttachment;if(removeAttachment){supportApi.deleteDocumentAttachment("QUOTATION",saved.id());attachment="";}else if(pendingAttachment!=null)attachment=supportApi.uploadDocumentAttachment("QUOTATION",saved.id(),pendingAttachment);return new SaveOutcome(saved,attachment==null?"":attachment);},outcome->{setSaveBusy(false);quotationId=outcome.quote().id();existingAttachment=outcome.attachment();selectedAttachment=null;attachmentRemovalPending=false;dirty=false;ScreenRefreshPolicy.invalidate("quotations");org.example.util.ToastManager.success(tableLines,"Quotation saved","Quotation saved successfully.");backToRegister();},failure->{setSaveBusy(false);error(asException(failure));});}catch(Exception e){error(e);}}
    @FXML private void cancel(){if(dirty&&!tableLines.getItems().isEmpty()){ButtonType choice=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Discard unsaved quotation changes?",ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO);if(choice!=ButtonType.YES)return;}backToRegister();}
    private void setSaveBusy(boolean busy){btnSave.setDisable(busy);btnCancel.setDisable(busy);btnAttachmentAdd.setDisable(busy);btnAttachmentRemove.setDisable(busy||(!attachmentAvailable()));btnDeleteLine.setDisable(busy||tableLines.getSelectionModel().getSelectedItem()==null);}
    private void backToRegister(){NavigationManager manager=NavigationManager.getInstance();if(manager!=null){manager.invalidate("/fxml/pages/Quotations.fxml");manager.loadPage("/fxml/pages/Quotations.fxml");}}

    @FXML private void chooseAttachment(){FileChooser chooser=new FileChooser();chooser.setTitle("Attach quotation document");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents","*.pdf","*.png","*.jpg","*.jpeg","*.doc","*.docx","*.xls","*.xlsx","*.csv","*.txt"));File file=chooser.showOpenDialog(tableLines.getScene().getWindow());if(file!=null){selectedAttachment=file.toPath();attachmentRemovalPending=false;dirty=true;updateAttachmentLabel();}}
    @FXML private void previewAttachment(){Path local=selectedAttachment;if(local!=null){openAttachment(local);return;}if(attachmentRemovalPending||quotationId==null||existingAttachment.isBlank()){error(new IllegalStateException("No quotation attachment is available."));return;}int id=quotationId;UiTaskExecutor.submitLatest("quotation-editor-attachment-preview",()->AttachmentPreviewSupport.materialize(supportApi.documentAttachment("QUOTATION",id),"quotation-attachment"),this::openAttachment,failure->error(asException(failure)));}
    private void openAttachment(Path file){try{if(file==null||!Files.isRegularFile(file))throw new IllegalStateException("No quotation attachment is available.");Desktop.getDesktop().open(file.toFile());}catch(Exception e){error(e);}}
    @FXML private void removeAttachment(){if(!attachmentAvailable())return;ButtonType choice=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Delete the quotation attachment?",ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO);if(choice!=ButtonType.YES)return;selectedAttachment=null;attachmentRemovalPending=!existingAttachment.isBlank();dirty=true;updateAttachmentLabel();}
    private boolean attachmentAvailable(){return selectedAttachment!=null||(!attachmentRemovalPending&&!existingAttachment.isBlank());}
    private void updateAttachmentLabel(){boolean available=attachmentAvailable();if(selectedAttachment!=null)lblAttachmentName.setText(selectedAttachment.getFileName().toString());else if(attachmentRemovalPending)lblAttachmentName.setText("Attachment will be deleted when saved");else if(!existingAttachment.isBlank())lblAttachmentName.setText("Attached: "+fileName(existingAttachment));else lblAttachmentName.setText("No document attached");btnAttachmentPreview.setDisable(!available);btnAttachmentRemove.setDisable(!available);btnAttachmentAdd.setText(available?"Replace":"Add");}

    private void updateTotals(){double gross=tableLines.getItems().stream().mapToDouble(r->r.quantity.get()*r.rate.get()).sum(),discount=tableLines.getItems().stream().mapToDouble(r->r.discountAmount.get()).sum(),taxable=gross-discount,gst=tableLines.getItems().stream().mapToDouble(r->r.gstAmount.get()).sum();lblSubtotal.setText(money(gross));lblDiscount.setText("- "+money(discount));lblTaxable.setText(money(taxable));lblGst.setText(money(gst));lblGrandTotal.setText(money(taxable+gst));lblLineCount.setText(tableLines.getItems().size()+" line item(s)");}
    private static double positive(String v,String name){double n=Double.parseDouble(safe(v).trim());if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException(name+" must be greater than zero.");return n;}
    private static double nonNegative(String v,String name){double n=Double.parseDouble(safe(v).trim());if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException(name+" cannot be negative.");return n;}
    private static double percent(String v,String name){double n=safe(v).isBlank()?0:Double.parseDouble(v.trim());if(!Double.isFinite(n)||n<0||n>100)throw new IllegalArgumentException(name+" must be between 0 and 100.");return n;}
    private static String format(double v){return String.format(Locale.ENGLISH,"%.2f",v);}
    private static String money(double v){return "₹ "+String.format(Locale.ENGLISH,"%,.2f",v);}
    private static String safe(String v){return v==null?"":v;}
    private static LocalDate parse(String v){try{return safe(v).isBlank()?null:LocalDate.parse(v.substring(0,10));}catch(Exception e){return null;}}
    private static String user(){return SessionService.current()==null?"System":SessionService.current().getFullName();}
    private static String fileName(String value){String normalized=safe(value).replace('\\','/');int slash=normalized.lastIndexOf('/');return slash>=0?normalized.substring(slash+1):normalized;}
    private static String message(Throwable failure){return failure==null?"Unknown error":failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();}
    private Exception asException(Throwable failure){return failure instanceof Exception e?e:new RuntimeException(failure);}
    private void error(Exception e){new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?"Quotation operation failed.":e.getMessage()).showAndWait();}

    private static final class CustomerChoice{final int id;final String code,name;CustomerChoice(Party p){id=p.getId();code=safe(p.getPartyCode());name=safe(p.getName());}CustomerChoice(QuotationApiClient.CustomerChoiceDto p){id=p.id();code=safe(p.code());name=safe(p.name());}String display(){return code.isBlank()?name:code+" - "+name;}@Override public String toString(){return display();}}
    private static final class ItemChoice{final String code,description,remarks,hsn;final double rate,gst,discount;ItemChoice(QuotationApiClient.ItemChoiceDto i){this(i.code(),i.description(),i.remarks(),i.hsn(),i.rate(),i.gst(),i.discount());}ItemChoice(String code,String description,String remarks,String hsn,double rate,double gst,double discount){this.code=safe(code);this.description=safe(description);this.remarks=safe(remarks);this.hsn=safe(hsn);this.rate=rate;this.gst=gst;this.discount=discount;}@Override public String toString(){if(remarks.isBlank())return description.isBlank()?code:description;if(description.isBlank())return remarks;return remarks+" • "+description;}}
    public static final class LineRow{final StringProperty code=new SimpleStringProperty(),description=new SimpleStringProperty();final DoubleProperty quantity=new SimpleDoubleProperty(),rate=new SimpleDoubleProperty(),gst=new SimpleDoubleProperty(),discount=new SimpleDoubleProperty(),discountAmount=new SimpleDoubleProperty(),gstAmount=new SimpleDoubleProperty(),total=new SimpleDoubleProperty();LineRow(QuotationApiClient.LineDto l){this(l.code(),l.description(),l.quantity(),l.rate(),l.gst(),l.discount());}LineRow(String c,String d,double q,double r,double g,double disc){code.set(c);description.set(d);quantity.set(q);rate.set(r);gst.set(g);discount.set(disc);double gross=q*r,discountValue=gross*disc/100,taxable=gross-discountValue;discountAmount.set(discountValue);gstAmount.set(taxable*g/100);total.set(taxable+gstAmount.get());}}
}
