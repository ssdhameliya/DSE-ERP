package org.example.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.api.supplier360.Supplier360ApiClient;
import org.example.api.support.SupportApiClient;
import org.example.model.Party;
import org.example.navigation.NavigationManager;
import org.example.theme.ThemeManager;
import org.example.util.*;

import java.awt.Desktop;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.List;

/** Supplier-side counterpart of Customer360Controller using the same UI/lifecycle patterns. */
public final class Supplier360Controller {
    @FXML private Label lblTitle,lblCode,lblGstin,lblContact,lblPhone,lblEmail,lblAddress,lblStatus;
    @FXML private Label lblOutstanding,lblPurchaseCount,lblPurchases,lblLastPayment;
    @FXML private StackPane pageIcon,outstandingIcon,purchaseCountIcon,purchasesIcon,paymentIcon;
    @FXML private TabPane tabs;
    @FXML private Tab tabOverview,tabContacts,tabPurchases,tabPayments,tabNotes,tabDocuments;

    @FXML private TableView<Supplier360ApiClient.PurchaseRow> tblRecentPurchases,tblPurchases;
    @FXML private TableView<Supplier360ApiClient.PaymentRow> tblRecentPayments,tblPayments;
    @FXML private TableView<Supplier360ApiClient.ContactRow> tblContacts;
    @FXML private TableView<Supplier360ApiClient.NoteRow> tblNotes;
    @FXML private TableView<SupportApiClient.AttachmentMeta> tblDocuments;

    @FXML private TableColumn<Supplier360ApiClient.PurchaseRow,String> rpNo,rpDate,rpAmount,rpOutstanding,pNo,pDate,pAmount,pPaid,pOutstanding,pStatus;
    @FXML private TableColumn<Supplier360ApiClient.PaymentRow,String> rpayDate,rpayInvoice,rpayAmount,rpayMode,payDate,payInvoice,payMode,payReference,payAmount,payNotes;
    @FXML private TableColumn<Supplier360ApiClient.ContactRow,String> cName,cDesignation,cDepartment,cMobile,cEmail,cPrimary;
    @FXML private TableColumn<Supplier360ApiClient.NoteRow,String> nDate,nBy,nText;
    @FXML private TableColumn<SupportApiClient.AttachmentMeta,String> dName,dUploaded,dBy;

    private final Supplier360ApiClient api=new Supplier360ApiClient();
    private final SupportApiClient support=new SupportApiClient();
    private Party supplier;
    private final java.util.Set<Tab> loaded=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @FXML public void initialize(){
        supplier=Supplier360Context.consume();
        configureIcons();configureTables();configureInteractions();configureTabs();
        if(supplier==null||supplier.getId()<=0){AppDialogService.warning(tabs,"Supplier 360°","No supplier selected","Open Supplier 360° from the Suppliers register.");backToSuppliers();return;}
        renderParty(supplier);loadSummary();
    }

    private void configureIcons(){icon(pageIcon,"supplier",26);icon(outstandingIcon,"balance",22);icon(purchaseCountIcon,"purchase",22);icon(purchasesIcon,"amount",22);icon(paymentIcon,"payment",22);}
    private void icon(StackPane p,String semantic,int size){if(p!=null)p.getChildren().setAll(IconFactory.icon(semantic,size));}

    private void configureTables(){
        rpNo.setCellValueFactory(c->s(c.getValue().invoiceNo()));rpDate.setCellValueFactory(c->s(c.getValue().invoiceDate()));rpAmount.setCellValueFactory(c->s(money(c.getValue().totalAmount())));rpOutstanding.setCellValueFactory(c->s(money(c.getValue().outstanding())));
        rpayDate.setCellValueFactory(c->s(c.getValue().paymentDate()));rpayInvoice.setCellValueFactory(c->s(c.getValue().invoiceNo()));rpayAmount.setCellValueFactory(c->s(money(c.getValue().amount())));rpayMode.setCellValueFactory(c->s(c.getValue().paymentMode()));
        pNo.setCellValueFactory(c->s(c.getValue().invoiceNo()));pDate.setCellValueFactory(c->s(c.getValue().invoiceDate()));pAmount.setCellValueFactory(c->s(money(c.getValue().totalAmount())));pPaid.setCellValueFactory(c->s(money(c.getValue().paidAmount())));pOutstanding.setCellValueFactory(c->s(money(c.getValue().outstanding())));pStatus.setCellValueFactory(c->s(paymentStatus(c.getValue())));
        payDate.setCellValueFactory(c->s(c.getValue().paymentDate()));payInvoice.setCellValueFactory(c->s(c.getValue().invoiceNo()));payMode.setCellValueFactory(c->s(c.getValue().paymentMode()));payReference.setCellValueFactory(c->s(c.getValue().referenceNo()));payAmount.setCellValueFactory(c->s(money(c.getValue().amount())));payNotes.setCellValueFactory(c->s(c.getValue().notes()));
        cName.setCellValueFactory(c->s(c.getValue().name()));cDesignation.setCellValueFactory(c->s(c.getValue().designation()));cDepartment.setCellValueFactory(c->s(c.getValue().department()));cMobile.setCellValueFactory(c->s(c.getValue().mobile()));cEmail.setCellValueFactory(c->s(c.getValue().email()));cPrimary.setCellValueFactory(c->s(c.getValue().primary()?"Primary":""));
        nDate.setCellValueFactory(c->s(c.getValue().updatedAt().isBlank()?c.getValue().createdAt():c.getValue().updatedAt()));nBy.setCellValueFactory(c->s(c.getValue().updatedBy().isBlank()?c.getValue().createdBy():c.getValue().updatedBy()));nText.setCellValueFactory(c->s(c.getValue().note()));
        dName.setCellValueFactory(c->s(c.getValue().fileName()));dUploaded.setCellValueFactory(c->s(c.getValue().createdAt()));dBy.setCellValueFactory(c->s(c.getValue().createdBy()));
        for(TableView<?> t:List.of(tblRecentPurchases,tblRecentPayments,tblPurchases,tblPayments,tblContacts,tblNotes,tblDocuments))DynamicTableLayoutManager.install(t);
    }

    private void configureInteractions(){
        tblPurchases.setRowFactory(t->row(this::openPurchase));tblRecentPurchases.setRowFactory(t->row(this::openPurchase));
        tblPayments.setRowFactory(t->row(this::openPayment));tblRecentPayments.setRowFactory(t->row(this::openPayment));
    }
    private <T>TableRow<T> row(java.util.function.Consumer<T> open){TableRow<T> r=new TableRow<>();r.setOnMouseClicked(e->{if(!r.isEmpty()&&e.getButton()==javafx.scene.input.MouseButton.PRIMARY&&e.getClickCount()==1)open.accept(r.getItem());});return r;}
    private void configureTabs(){tabs.getSelectionModel().selectedItemProperty().addListener((o,a,b)->loadTab(b));loaded.add(tabOverview);}

    private void loadSummary(){UiTaskExecutor.submitLatest("supplier-360-summary-"+supplier.getId(),()->api.summary(supplier.getId()),this::renderSummary,this::showError);}
    private void renderSummary(Supplier360ApiClient.Summary summary){if(summary==null)return;supplier=party(summary.supplier());renderParty(supplier);lblOutstanding.setText(money(summary.outstandingPayable()));lblPurchaseCount.setText(summary.purchaseCount()+" purchases");lblPurchases.setText(money(summary.totalPurchases()));lblLastPayment.setText(money(summary.lastPaymentAmount())+(safe(summary.lastPaymentDate()).isBlank()?"":"  •  "+summary.lastPaymentDate()));tblRecentPurchases.getItems().setAll(nz(summary.recentPurchases()));tblRecentPayments.getItems().setAll(nz(summary.recentPayments()));}
    private void renderParty(Party p){lblTitle.setText("Supplier 360° — "+safe(p.getName()));lblCode.setText(safe(p.getPartyCode()));lblGstin.setText(safe(p.getGstin()));lblContact.setText(safe(p.getContactPerson()));lblPhone.setText(safe(p.getPhone()));lblEmail.setText(safe(p.getEmail()));lblAddress.setText(safe(p.getAddress()));lblStatus.setText(p.isActive()?"Active":"Inactive");}

    private void loadTab(Tab tab){if(tab==null||loaded.contains(tab)||supplier==null)return;loaded.add(tab);int id=supplier.getId();
        if(tab==tabContacts)load("supplier-360-contacts-"+id,()->api.contacts(id),x->tblContacts.getItems().setAll(x));
        else if(tab==tabPurchases)load("supplier-360-purchases-"+id,()->api.purchases(id),x->tblPurchases.getItems().setAll(x));
        else if(tab==tabPayments)load("supplier-360-payments-"+id,()->api.payments(id),x->tblPayments.getItems().setAll(x));
        else if(tab==tabNotes)load("supplier-360-notes-"+id,()->api.notes(id),x->tblNotes.getItems().setAll(x));
        else if(tab==tabDocuments)load("supplier-360-docs-"+id,()->support.documentAttachments("SUPPLIER",id),x->tblDocuments.getItems().setAll(x));
    }
    private <T>void load(String key,java.util.concurrent.Callable<List<T>> task,java.util.function.Consumer<List<T>> apply){UiTaskExecutor.submitLatest(key,task,x->apply.accept(x==null?List.of():x),e->{loaded.remove(tabs.getSelectionModel().getSelectedItem());showError(e);});}

    @FXML private void backToSuppliers(){NavigationManager.navigateOrReport("/fxml/pages/Suppliers.fxml");}
    @FXML private void editSupplier(){if(supplier==null)return;try{FXMLLoader l=new FXMLLoader(ResourceLocator.require("/fxml/pages/PartyDialog.fxml"));Parent root=l.load();ProfessionalUiEnhancer.enhance(root);PartyDialogController c=l.getController();c.configure("SUPPLIER",supplier);Stage stage=new Stage();PlatformUiSupport.configureDialogStage(stage,tabs,"Edit Supplier",false);Scene scene=new Scene(root);ThemeManager.applyTheme(scene);stage.setScene(scene);stage.showAndWait();refreshSupplierFromMaster();}catch(Exception e){showError(e);}}
    private void refreshSupplierFromMaster(){UiTaskExecutor.submitLatest("supplier-360-master-refresh",()->new org.example.service.PartyService().search("SUPPLIER",supplier.getPartyCode(),20),rows->{rows.stream().filter(p->p.getId()==supplier.getId()).findFirst().ifPresent(p->{supplier=p;renderParty(p);loadSummary();});},this::showError);}
    @FXML private void newPurchase(){if(supplier==null)return;SupplierPurchaseContext.select(supplier);NavigationManager.navigateOrReport("/fxml/pages/Purchase.fxml");}
    @FXML private void refresh(){if(supplier==null)return;loaded.clear();loaded.add(tabOverview);loadSummary();loadTab(tabs.getSelectionModel().getSelectedItem());}

    @FXML private void addContact(){editContact(null);}@FXML private void editContact(){editContact(tblContacts.getSelectionModel().getSelectedItem());}
    private void editContact(Supplier360ApiClient.ContactRow current){if(supplier==null)return;OwnedDialog<Supplier360ApiClient.ContactSave> dlg=new OwnedDialog<>(tblContacts);dlg.setTitle(current==null?"Add Supplier Contact":"Edit Supplier Contact");dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);dlg.getDialogPane().getStyleClass().addAll("approved-dialog","customer-360-standard-dialog");javafx.scene.layout.GridPane g=new javafx.scene.layout.GridPane();g.setHgap(10);g.setVgap(9);g.setPadding(new javafx.geometry.Insets(10));TextField name=new TextField(current==null?"":current.name()),designation=new TextField(current==null?"":current.designation()),department=new TextField(current==null?"":current.department()),mobile=new TextField(current==null?"":current.mobile()),email=new TextField(current==null?"":current.email());CheckBox primary=new CheckBox("Primary contact");primary.setSelected(current!=null&&current.primary());TextArea notes=new TextArea(current==null?"":current.notes());notes.setPrefRowCount(3);int r=0;add(g,"Name",name,r++);add(g,"Designation",designation,r++);add(g,"Department",department,r++);add(g,"Mobile",mobile,r++);add(g,"Email",email,r++);g.add(primary,1,r++);add(g,"Notes",notes,r);dlg.getDialogPane().setContent(g);styleDialogButtons(dlg);dlg.setResultConverter(b->b==ButtonType.OK?new Supplier360ApiClient.ContactSave(current==null?null:current.id(),name.getText(),designation.getText(),department.getText(),mobile.getText(),email.getText(),primary.isSelected(),notes.getText(),current==null?0:current.rowVersion()):null);dlg.showAndWait().ifPresent(v->UiTaskExecutor.submitAction("supplier-360-contact-save",()->api.saveContact(supplier.getId(),v),x->{ToastManager.success(tblContacts,"Contact saved","Supplier contact was saved successfully.");reloadContacts();},this::showError));}
    @FXML private void deleteContact(){var r=tblContacts.getSelectionModel().getSelectedItem();if(r==null||!confirm("Delete contact '"+r.name()+"'?"))return;UiTaskExecutor.submitAction("supplier-360-contact-delete",()->{api.deleteContact(supplier.getId(),r.id(),r.rowVersion());return null;},x->{ToastManager.success(tblContacts,"Contact deleted","Supplier contact was deleted successfully.");reloadContacts();},this::showError);}private void reloadContacts(){loaded.remove(tabContacts);loadTab(tabContacts);}

    @FXML private void addNote(){editNote(null);}@FXML private void editNote(){editNote(tblNotes.getSelectionModel().getSelectedItem());}
    private void editNote(Supplier360ApiClient.NoteRow current){OwnedDialog<Supplier360ApiClient.NoteSave> dlg=new OwnedDialog<>(tblNotes);dlg.setTitle(current==null?"Add Supplier Note":"Edit Supplier Note");dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);dlg.getDialogPane().getStyleClass().addAll("approved-dialog","customer-360-standard-dialog");TextArea area=new TextArea(current==null?"":current.note());area.setWrapText(true);area.setPrefRowCount(8);dlg.getDialogPane().setContent(area);styleDialogButtons(dlg);dlg.setResultConverter(b->b==ButtonType.OK?new Supplier360ApiClient.NoteSave(current==null?null:current.id(),area.getText(),current==null?0:current.rowVersion()):null);dlg.showAndWait().ifPresent(v->UiTaskExecutor.submitAction("supplier-360-note-save",()->api.saveNote(supplier.getId(),v),x->{ToastManager.success(tblNotes,"Note saved","Supplier note was saved successfully.");reloadNotes();},this::showError));}
    @FXML private void deleteNote(){var r=tblNotes.getSelectionModel().getSelectedItem();if(r==null||!confirm("Delete this supplier note?"))return;UiTaskExecutor.submitAction("supplier-360-note-delete",()->{api.deleteNote(supplier.getId(),r.id(),r.rowVersion());return null;},x->{ToastManager.success(tblNotes,"Note deleted","Supplier note was deleted successfully.");reloadNotes();},this::showError);}private void reloadNotes(){loaded.remove(tabNotes);loadTab(tabNotes);}

    @FXML private void addDocument(){FileChooser fc=new FileChooser();fc.setTitle("Add Supplier Document");java.io.File f=fc.showOpenDialog(tblDocuments.getScene().getWindow());if(f==null)return;UiTaskExecutor.submitAction("supplier-360-document-add",()->support.addDocumentAttachment("SUPPLIER",supplier.getId(),f.toPath()),x->{ToastManager.success(tblDocuments,"Document added","Supplier document was added successfully.");reloadDocuments();},this::showError);}
    @FXML private void viewDocument(){var meta=tblDocuments.getSelectionModel().getSelectedItem();if(meta==null)return;UiTaskExecutor.submitLatest("supplier-360-document-preview-"+meta.id(),()->AttachmentPreviewSupport.materializeRequired(support.documentAttachment("SUPPLIER",supplier.getId(),meta.id()),meta.fileName()),this::openPreview,this::showError);}
    private void openPreview(java.nio.file.Path path){try{if(path==null||!Files.isRegularFile(path))throw new IllegalStateException("The supplier document is unavailable.");if(!Desktop.isDesktopSupported())throw new IllegalStateException("Document preview is not supported on this computer.");Desktop.getDesktop().open(path.toFile());}catch(Exception e){showError(e);}}
    @FXML private void deleteDocument(){var meta=tblDocuments.getSelectionModel().getSelectedItem();if(meta==null||!confirm("Delete document '"+meta.fileName()+"'?"))return;UiTaskExecutor.submitAction("supplier-360-document-delete",()->{support.deleteDocumentAttachment("SUPPLIER",supplier.getId(),meta.id());return null;},x->{ToastManager.success(tblDocuments,"Document deleted","Supplier document was deleted successfully.");reloadDocuments();},this::showError);}private void reloadDocuments(){loaded.remove(tabDocuments);loadTab(tabDocuments);}

    private void openPurchase(Supplier360ApiClient.PurchaseRow r){if(r==null)return;LinkedRecordContext.open("PURCHASE",r.id(),r.invoiceNo(),"VIEW","SUPPLIER_360");NavigationManager.navigateOrReport("/fxml/pages/PurchaseList.fxml");}
    private void openPayment(Supplier360ApiClient.PaymentRow r){if(r==null||safe(r.invoiceNo()).isBlank())return;LinkedRecordContext.open("PURCHASE",null,r.invoiceNo(),"VIEW","SUPPLIER_360");NavigationManager.navigateOrReport("/fxml/pages/PurchaseList.fxml");}

    private static void add(javafx.scene.layout.GridPane g,String label,javafx.scene.Node n,int row){Label l=new Label(label);String semantic=IconFactory.semanticForLabel(label);if(semantic!=null){l.setGraphic(IconFactory.icon(semantic,14));IconFactory.applySemanticLabelColour(l,semantic);l.getProperties().put("erp-icon-preserve",true);}l.getStyleClass().add("field-label");g.add(l,0,row);g.add(n,1,row);javafx.scene.layout.GridPane.setHgrow(n,javafx.scene.layout.Priority.ALWAYS);if(n instanceof javafx.scene.layout.Region x)x.setMaxWidth(Double.MAX_VALUE);}
    private static void styleDialogButtons(Dialog<?> dlg){Button ok=(Button)dlg.getDialogPane().lookupButton(ButtonType.OK),cancel=(Button)dlg.getDialogPane().lookupButton(ButtonType.CANCEL);if(ok!=null){ok.getStyleClass().addAll("approved-button","approved-primary-button");UiActionIcons.apply(ok,"save","Save");}if(cancel!=null){cancel.getStyleClass().addAll("approved-button","approved-secondary-button");UiActionIcons.apply(cancel,"return","Cancel");}}
    private boolean confirm(String text){return new OwnedAlert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    private void showError(Throwable e){AppDialogService.error(tabs,"Supplier 360°","Operation could not be completed",e==null?"Unexpected error":safe(e.getMessage()));}
    private static SimpleStringProperty s(String v){return new SimpleStringProperty(safe(v));}private static String safe(String v){return v==null?"":v;}private static String money(BigDecimal v){return "₹ "+(v==null?BigDecimal.ZERO:v).setScale(2,RoundingMode.HALF_UP).toPlainString();}private static String paymentStatus(Supplier360ApiClient.PurchaseRow r){String p=safe(r.paymentStatus());return p.isBlank()?safe(r.documentStatus()):p;}private static <T>List<T> nz(List<T> v){return v==null?List.of():v;}
    private static Party party(Supplier360ApiClient.Supplier c){Party p=new Party();p.setId(c.id());p.setRowVersion(c.rowVersion());p.setPartyType("SUPPLIER");p.setPartyCode(c.code());p.setName(c.name());p.setContactPerson(c.contactPerson());p.setPhone(c.phone());p.setEmail(c.email());p.setGstin(c.gstin());p.setAddress(c.address());p.setOpeningBalance(c.openingBalance()==null?0:c.openingBalance().doubleValue());p.setActive(c.active());return p;}
}
