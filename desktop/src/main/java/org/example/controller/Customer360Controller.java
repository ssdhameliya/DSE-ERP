package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.api.customer360.Customer360ApiClient;
import org.example.api.support.SupportApiClient;
import org.example.model.Party;
import org.example.navigation.NavigationManager;
import org.example.theme.ThemeManager;
import org.example.util.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.awt.Desktop;
import java.util.List;
import java.util.Locale;

/**
 * DSE ERP 9.0.52 Customer 360°.
 * This page is an aggregation/navigation hub for customer-owned quotations,
 * invoices, payments, contacts, notes and documents.
 */
public class Customer360Controller {
    @FXML private Label lblTitle,lblCode,lblGstin,lblContact,lblPhone,lblEmail,lblAddress,lblStatus;
    @FXML private Label lblOutstanding,lblOpenQuotes,lblSales,lblLastPayment;
    @FXML private StackPane pageIcon,outstandingIcon,quotesIcon,salesIcon,paymentIcon;
    @FXML private TabPane tabs;
    @FXML private Tab tabOverview,tabContacts,tabQuotations,tabInvoices,tabPayments,tabNotes,tabDocuments;

    @FXML private TableView<Customer360ApiClient.QuotationRow> tblRecentQuotes,tblQuotations;
    @FXML private TableView<Customer360ApiClient.InvoiceRow> tblRecentInvoices,tblInvoices;
    @FXML private TableView<Customer360ApiClient.PaymentRow> tblPayments;
    @FXML private TableView<Customer360ApiClient.ContactRow> tblContacts;
    @FXML private TableView<Customer360ApiClient.NoteRow> tblNotes;
    @FXML private TableView<SupportApiClient.AttachmentMeta> tblDocuments;

    @FXML private TableColumn<Customer360ApiClient.QuotationRow,String> rqNo,rqDate,rqAmount,rqStatus,qNo,qDate,qValid,qSalesperson,qAmount,qStatus,qFollowUp;
    @FXML private TableColumn<Customer360ApiClient.InvoiceRow,String> riNo,riDate,riAmount,riOutstanding,iNo,iDate,iAmount,iPaid,iOutstanding,iStatus;
    @FXML private TableColumn<Customer360ApiClient.PaymentRow,String> payNo,payDate,payInvoice,payMode,payReference,payAmount,payNotes;
    @FXML private TableColumn<Customer360ApiClient.ContactRow,String> cName,cDesignation,cDepartment,cMobile,cEmail,cPrimary;
    @FXML private TableColumn<Customer360ApiClient.NoteRow,String> nDate,nBy,nText;
    @FXML private TableColumn<SupportApiClient.AttachmentMeta,String> dName,dUploaded,dBy;

    private final Customer360ApiClient api=new Customer360ApiClient();
    private final SupportApiClient support=new SupportApiClient();
    private Party customer;
    private Customer360ApiClient.Summary summary;
    private final java.util.Set<Tab> loaded=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @FXML public void initialize(){
        customer=Customer360Context.consume();
        configureIcons(); configureTables(); configureInteractions(); configureTabs();
        if(customer==null||customer.getId()<=0){ModernDialog.warning(tabs,"Customer 360°","No customer selected","Open Customer 360° from the Customers register.");backToCustomers();return;}
        renderParty(customer); loadSummary();
    }

    private void configureIcons(){
        icon(pageIcon,"customer",26);icon(outstandingIcon,"balance",22);icon(quotesIcon,"quotation",22);icon(salesIcon,"amount",22);icon(paymentIcon,"payment",22);
    }
    private void icon(StackPane p,String semantic,int size){if(p!=null)p.getChildren().setAll(IconFactory.icon(semantic,size));}

    private void configureTables(){
        rqNo.setCellValueFactory(c->s(c.getValue().no()));rqDate.setCellValueFactory(c->s(c.getValue().date()));rqAmount.setCellValueFactory(c->s(money(c.getValue().amount())));rqStatus.setCellValueFactory(c->s(c.getValue().status()));
        riNo.setCellValueFactory(c->s(c.getValue().invoiceNo()));riDate.setCellValueFactory(c->s(c.getValue().invoiceDate()));riAmount.setCellValueFactory(c->s(money(c.getValue().totalAmount())));riOutstanding.setCellValueFactory(c->s(money(c.getValue().outstanding())));
        qNo.setCellValueFactory(c->s(c.getValue().no()));qDate.setCellValueFactory(c->s(c.getValue().date()));qValid.setCellValueFactory(c->s(c.getValue().valid()));qSalesperson.setCellValueFactory(c->s(c.getValue().salesperson()));qAmount.setCellValueFactory(c->s(money(c.getValue().amount())));qStatus.setCellValueFactory(c->s(c.getValue().status()));qFollowUp.setCellValueFactory(c->s(c.getValue().followUp()));
        iNo.setCellValueFactory(c->s(c.getValue().invoiceNo()));iDate.setCellValueFactory(c->s(c.getValue().invoiceDate()));iAmount.setCellValueFactory(c->s(money(c.getValue().totalAmount())));iPaid.setCellValueFactory(c->s(money(c.getValue().paidAmount())));iOutstanding.setCellValueFactory(c->s(money(c.getValue().outstanding())));iStatus.setCellValueFactory(c->s(paymentStatus(c.getValue())));
        payNo.setCellValueFactory(c->s(String.valueOf(c.getValue().id())));payDate.setCellValueFactory(c->s(c.getValue().paymentDate()));payInvoice.setCellValueFactory(c->s(c.getValue().invoiceNo()));payMode.setCellValueFactory(c->s(c.getValue().paymentMode()));payReference.setCellValueFactory(c->s(c.getValue().referenceNo()));payAmount.setCellValueFactory(c->s(money(c.getValue().amount())));payNotes.setCellValueFactory(c->s(c.getValue().notes()));
        cName.setCellValueFactory(c->s(c.getValue().name()));cDesignation.setCellValueFactory(c->s(c.getValue().designation()));cDepartment.setCellValueFactory(c->s(c.getValue().department()));cMobile.setCellValueFactory(c->s(c.getValue().mobile()));cEmail.setCellValueFactory(c->s(c.getValue().email()));cPrimary.setCellValueFactory(c->s(c.getValue().primary()?"Primary":""));
        nDate.setCellValueFactory(c->s(c.getValue().updatedAt().isBlank()?c.getValue().createdAt():c.getValue().updatedAt()));nBy.setCellValueFactory(c->s(c.getValue().updatedBy().isBlank()?c.getValue().createdBy():c.getValue().updatedBy()));nText.setCellValueFactory(c->s(c.getValue().note()));
        dName.setCellValueFactory(c->s(c.getValue().fileName()));dUploaded.setCellValueFactory(c->s(c.getValue().createdAt()));dBy.setCellValueFactory(c->s(c.getValue().createdBy()));
        for(TableView<?> t:List.of(tblRecentQuotes,tblRecentInvoices,tblContacts,tblQuotations,tblInvoices,tblPayments,tblNotes,tblDocuments))DynamicTableLayoutManager.install(t);
    }

    private void configureInteractions(){
        tblQuotations.setRowFactory(t->row(this::openQuotation));tblRecentQuotes.setRowFactory(t->row(this::openQuotation));
        tblInvoices.setRowFactory(t->row(this::openInvoice));tblRecentInvoices.setRowFactory(t->row(this::openInvoice));
        tblPayments.setRowFactory(t->row(p->{if(p!=null&&!p.invoiceNo().isBlank()){LinkedRecordContext.open("SALE",null,p.invoiceNo(),"VIEW","CUSTOMER_360");NavigationManager.navigateOrReport("/fxml/pages/SalesList.fxml");}}));
    }
    private <T> TableRow<T> row(java.util.function.Consumer<T> open){TableRow<T> r=new TableRow<>();r.setOnMouseClicked(e->{if(!r.isEmpty()&&e.getButton()==javafx.scene.input.MouseButton.PRIMARY&&e.getClickCount()==2)open.accept(r.getItem());});return r;}

    private void configureTabs(){
        tabs.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadTab(b));
        loaded.add(tabOverview);
    }

    private void loadSummary(){
        UiTaskExecutor.submitLatest("customer-360-summary-"+customer.getId(),()->api.summary(customer.getId()),s->{summary=s;renderSummary(s);},this::showError);
    }
    private void renderSummary(Customer360ApiClient.Summary s){
        if(s==null)return;customer=party(s.customer());renderParty(customer);
        lblOutstanding.setText(money(s.outstandingReceivable()));lblOpenQuotes.setText(money(s.openQuotationValue())+"  •  "+s.openQuotationCount()+" open");lblSales.setText(money(s.totalSales()));lblLastPayment.setText(money(s.lastPaymentAmount())+(safe(s.lastPaymentDate()).isBlank()?"":"  •  "+s.lastPaymentDate()));
        tblRecentQuotes.getItems().setAll(nz(s.recentQuotations()));tblRecentInvoices.getItems().setAll(nz(s.recentInvoices()));
    }
    private void renderParty(Party p){lblTitle.setText("Customer 360° — "+safe(p.getName()));lblCode.setText(safe(p.getPartyCode()));lblGstin.setText(safe(p.getGstin()));lblContact.setText(safe(p.getContactPerson()));lblPhone.setText(safe(p.getPhone()));lblEmail.setText(safe(p.getEmail()));lblAddress.setText(safe(p.getAddress()));lblStatus.setText(p.isActive()?"Active":"Inactive");}

    private void loadTab(Tab tab){if(tab==null||loaded.contains(tab)||customer==null)return;loaded.add(tab);int id=customer.getId();
        if(tab==tabContacts)load("customer-360-contacts-"+id,()->api.contacts(id),x->tblContacts.getItems().setAll(x));
        else if(tab==tabQuotations)load("customer-360-quotes-"+id,()->api.quotations(id),x->tblQuotations.getItems().setAll(x));
        else if(tab==tabInvoices)load("customer-360-invoices-"+id,()->api.invoices(id),x->tblInvoices.getItems().setAll(x));
        else if(tab==tabPayments)load("customer-360-payments-"+id,()->api.payments(id),x->tblPayments.getItems().setAll(x));
        else if(tab==tabNotes)load("customer-360-notes-"+id,()->api.notes(id),x->tblNotes.getItems().setAll(x));
        else if(tab==tabDocuments)load("customer-360-docs-"+id,()->support.documentAttachments("CUSTOMER",id),x->tblDocuments.getItems().setAll(x));
    }
    private <T> void load(String key,java.util.concurrent.Callable<List<T>> task,java.util.function.Consumer<List<T>> apply){UiTaskExecutor.submitLatest(key,task,x->apply.accept(x==null?List.of():x),e->{loaded.remove(tabs.getSelectionModel().getSelectedItem());showError(e);});}

    @FXML private void backToCustomers(){NavigationManager.navigateOrReport("/fxml/pages/Customer.fxml");}
    @FXML private void editCustomer(){if(customer==null)return;try{FXMLLoader l=new FXMLLoader(ResourceLocator.require("/fxml/pages/PartyDialog.fxml"));Parent root=l.load();ProfessionalUiEnhancer.enhance(root);PartyDialogController c=l.getController();c.configure("CUSTOMER",customer);Stage stage=new Stage();PlatformUiSupport.configureDialogStage(stage,tabs,"Edit Customer",false);Scene scene=new Scene(root);ThemeManager.applyTheme(scene);stage.setScene(scene);stage.showAndWait();refreshCustomerFromMaster();}catch(Exception e){showError(e);}}
    private void refreshCustomerFromMaster(){UiTaskExecutor.submitLatest("customer-360-master-refresh",()->new org.example.service.PartyService().search("CUSTOMER",customer.getPartyCode(),20),rows->{rows.stream().filter(p->p.getId()==customer.getId()).findFirst().ifPresent(p->{customer=p;renderParty(p);loadSummary();});},this::showError);}
    @FXML private void newSale(){if(customer==null)return;CustomerSaleContext.select(customer.getId());NavigationManager.navigateOrReport("/fxml/pages/Sale.fxml");}
    @FXML private void refresh(){if(customer==null)return;loaded.clear();loaded.add(tabOverview);loadSummary();loadTab(tabs.getSelectionModel().getSelectedItem());}

    @FXML private void addContact(){editContact(null);}
    @FXML private void editContact(){editContact(tblContacts.getSelectionModel().getSelectedItem());}
    private void editContact(Customer360ApiClient.ContactRow current){if(customer==null)return;OwnedDialog<Customer360ApiClient.ContactSave> dlg=new OwnedDialog<>(tblContacts);dlg.setTitle(current==null?"Add Customer Contact":"Edit Customer Contact");dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);dlg.getDialogPane().getStyleClass().addAll("modern-dialog","approved-dialog","customer-360-standard-dialog");javafx.scene.layout.GridPane g=new javafx.scene.layout.GridPane();g.setHgap(10);g.setVgap(9);g.setPadding(new javafx.geometry.Insets(10));TextField name=new TextField(current==null?"":current.name()),designation=new TextField(current==null?"":current.designation()),department=new TextField(current==null?"":current.department()),mobile=new TextField(current==null?"":current.mobile()),email=new TextField(current==null?"":current.email());CheckBox primary=new CheckBox("Primary contact");primary.setSelected(current!=null&&current.primary());TextArea notes=new TextArea(current==null?"":current.notes());notes.setPrefRowCount(3);int r=0;add(g,"Name",name,r++);add(g,"Designation",designation,r++);add(g,"Department",department,r++);add(g,"Mobile",mobile,r++);add(g,"Email",email,r++);g.add(primary,1,r++);add(g,"Notes",notes,r);dlg.getDialogPane().setContent(g);Button contactOk=(Button)dlg.getDialogPane().lookupButton(ButtonType.OK),contactCancel=(Button)dlg.getDialogPane().lookupButton(ButtonType.CANCEL);if(contactOk!=null){contactOk.getStyleClass().addAll("approved-button","approved-primary-button");UiActionIcons.apply(contactOk,"save","Save contact");}if(contactCancel!=null){contactCancel.getStyleClass().addAll("approved-button","approved-secondary-button");UiActionIcons.apply(contactCancel,"return","Cancel");}dlg.setResultConverter(b->b==ButtonType.OK?new Customer360ApiClient.ContactSave(current==null?null:current.id(),name.getText(),designation.getText(),department.getText(),mobile.getText(),email.getText(),primary.isSelected(),notes.getText(),current==null?0:current.rowVersion()):null);dlg.showAndWait().ifPresent(v->UiTaskExecutor.submitAction("customer-360-contact-save",()->api.saveContact(customer.getId(),v),x->reloadContacts(),this::showError));}
    @FXML private void deleteContact(){var r=tblContacts.getSelectionModel().getSelectedItem();if(r==null)return;if(!confirm("Delete contact '"+r.name()+"'?"))return;UiTaskExecutor.submitAction("customer-360-contact-delete",()->{api.deleteContact(customer.getId(),r.id(),r.rowVersion());return null;},x->reloadContacts(),this::showError);}
    private void reloadContacts(){loaded.remove(tabContacts);loadTab(tabContacts);}

    @FXML private void addNote(){editNote(null);}@FXML private void editNote(){editNote(tblNotes.getSelectionModel().getSelectedItem());}
    private void editNote(Customer360ApiClient.NoteRow current){OwnedDialog<Customer360ApiClient.NoteSave> dlg=new OwnedDialog<>(tblNotes);dlg.setTitle(current==null?"Add Customer Note":"Edit Customer Note");dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);dlg.getDialogPane().getStyleClass().addAll("modern-dialog","approved-dialog","customer-360-standard-dialog");TextArea area=new TextArea(current==null?"":current.note());area.setWrapText(true);area.setPrefRowCount(8);dlg.getDialogPane().setContent(area);Button noteOk=(Button)dlg.getDialogPane().lookupButton(ButtonType.OK),noteCancel=(Button)dlg.getDialogPane().lookupButton(ButtonType.CANCEL);if(noteOk!=null){noteOk.getStyleClass().addAll("approved-button","approved-primary-button");UiActionIcons.apply(noteOk,"save","Save note");}if(noteCancel!=null){noteCancel.getStyleClass().addAll("approved-button","approved-secondary-button");UiActionIcons.apply(noteCancel,"return","Cancel");}dlg.setResultConverter(b->b==ButtonType.OK?new Customer360ApiClient.NoteSave(current==null?null:current.id(),area.getText(),current==null?0:current.rowVersion()):null);dlg.showAndWait().ifPresent(v->UiTaskExecutor.submitAction("customer-360-note-save",()->api.saveNote(customer.getId(),v),x->reloadNotes(),this::showError));}
    @FXML private void deleteNote(){var r=tblNotes.getSelectionModel().getSelectedItem();if(r==null)return;if(!confirm("Delete this customer note?"))return;UiTaskExecutor.submitAction("customer-360-note-delete",()->{api.deleteNote(customer.getId(),r.id(),r.rowVersion());return null;},x->reloadNotes(),this::showError);}
    private void reloadNotes(){loaded.remove(tabNotes);loadTab(tabNotes);}

    @FXML private void addDocument(){FileChooser fc=new FileChooser();fc.setTitle("Add Customer Document");java.io.File f=fc.showOpenDialog(tblDocuments.getScene().getWindow());if(f==null)return;UiTaskExecutor.submitAction("customer-360-document-add",()->support.addDocumentAttachment("CUSTOMER",customer.getId(),f.toPath()),x->reloadDocuments(),this::showError);}
    @FXML private void viewDocument(){var meta=tblDocuments.getSelectionModel().getSelectedItem();if(meta==null)return;UiTaskExecutor.submitLatest("customer-360-document-preview-"+meta.id(),()->AttachmentPreviewSupport.materializeRequired(support.documentAttachment("CUSTOMER",customer.getId(),meta.id()),meta.fileName()),this::openPreview,this::showError);}
    private void openPreview(java.nio.file.Path path){try{if(path==null||!Files.isRegularFile(path))throw new IllegalStateException("The customer document is unavailable.");if(!Desktop.isDesktopSupported())throw new IllegalStateException("Document preview is not supported on this computer.");Desktop.getDesktop().open(path.toFile());}catch(Exception e){showError(e);}}
    @FXML private void deleteDocument(){var meta=tblDocuments.getSelectionModel().getSelectedItem();if(meta==null)return;if(!confirm("Delete document '"+meta.fileName()+"'?"))return;UiTaskExecutor.submitAction("customer-360-document-delete",()->{support.deleteDocumentAttachment("CUSTOMER",customer.getId(),meta.id());return null;},x->reloadDocuments(),this::showError);}
    private void reloadDocuments(){loaded.remove(tabDocuments);loadTab(tabDocuments);}

    private void openQuotation(Customer360ApiClient.QuotationRow q){if(q==null)return;LinkedRecordContext.open("QUOTATION",q.id(),q.no(),"VIEW","CUSTOMER_360");NavigationManager.navigateOrReport("/fxml/pages/Quotations.fxml");}
    private void openInvoice(Customer360ApiClient.InvoiceRow r){if(r==null)return;LinkedRecordContext.open("SALE",r.id(),r.invoiceNo(),"VIEW","CUSTOMER_360");NavigationManager.navigateOrReport("/fxml/pages/SalesList.fxml");}

    private static void add(javafx.scene.layout.GridPane g,String label,javafx.scene.Node n,int row){Label l=new Label(label);String semantic=IconFactory.semanticForLabel(label);if(semantic!=null){l.setGraphic(IconFactory.icon(semantic,14));IconFactory.applySemanticLabelColour(l,semantic);l.getProperties().put("erp-icon-preserve",true);}l.getStyleClass().add("field-label");g.add(l,0,row);g.add(n,1,row);javafx.scene.layout.GridPane.setHgrow(n,javafx.scene.layout.Priority.ALWAYS);if(n instanceof javafx.scene.layout.Region x)x.setMaxWidth(Double.MAX_VALUE);}
    private boolean confirm(String s){return new OwnedAlert(Alert.AlertType.CONFIRMATION,s,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    private void showError(Throwable e){ModernDialog.error(tabs,"Customer 360°","Operation could not be completed",e==null?"Unexpected error":safe(e.getMessage()));}
    private static SimpleStringProperty s(String v){return new SimpleStringProperty(safe(v));}
    private static String safe(String v){return v==null?"":v;}
    private static String money(BigDecimal v){return "₹ "+(v==null?BigDecimal.ZERO:v).setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private static String paymentStatus(Customer360ApiClient.InvoiceRow r){String p=safe(r.paymentStatus());return p.isBlank()?safe(r.documentStatus()):p;}
    private static <T> List<T> nz(List<T> v){return v==null?List.of():v;}
    private static Party party(Customer360ApiClient.Customer c){Party p=new Party();p.setId(c.id());p.setRowVersion(c.rowVersion());p.setPartyType("CUSTOMER");p.setPartyCode(c.code());p.setName(c.name());p.setContactPerson(c.contactPerson());p.setPhone(c.phone());p.setEmail(c.email());p.setGstin(c.gstin());p.setAddress(c.address());p.setOpeningBalance(c.openingBalance()==null?0:c.openingBalance().doubleValue());p.setActive(c.active());return p;}
}
