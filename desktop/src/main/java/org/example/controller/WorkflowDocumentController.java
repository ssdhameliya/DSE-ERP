package org.example.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.example.api.workflow.WorkflowApiClient;
import org.example.model.Item;
import org.example.model.Party;
import org.example.navigation.DeepLinkSupport;
import org.example.service.ItemService;
import org.example.service.PartyService;
import org.example.util.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

public class WorkflowDocumentController {
    @FXML private Label lblWorkflowType,lblTitle,lblSubtitle,lblTotal,lblOpen,lblValue,lblRecordCount;
    @FXML private StackPane pageIcon,totalIcon,openIcon,valueIcon;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TableView<WorkflowApiClient.Document> table;
    @FXML private TableColumn<WorkflowApiClient.Document,String> colNo,colProject,colParty,colParent,colDate,colExpected,colStatus,colTotal;
    @FXML private TableColumn<WorkflowApiClient.Document,Void> colActions;

    private final WorkflowApiClient api = new WorkflowApiClient();
    private final ItemService itemService = new ItemService();
    private final PartyService partyService = new PartyService();
    private final ObservableList<WorkflowApiClient.Document> rows = FXCollections.observableArrayList();
    private final List<WorkflowApiClient.Document> allRows = new ArrayList<>();
    private String type;

    @FXML public void initialize() {
        type=lblWorkflowType.getText();
        var meta=meta(type);
        lblTitle.setText(meta[0]); lblSubtitle.setText(meta[1]);
        pageIcon.getChildren().setAll(IconFactory.icon(meta[2],28));
        totalIcon.getChildren().setAll(IconFactory.icon("record",22));
        openIcon.getChildren().setAll(IconFactory.icon("pending",22));
        valueIcon.getChildren().setAll(IconFactory.icon("amount",22));
        cmbStatus.getItems().setAll("ALL","DRAFT","CONFIRMED","IN PROGRESS","COMPLETED","CANCELLED");
        cmbStatus.setValue("ALL");
        colNo.setCellValueFactory(c->new SimpleStringProperty(c.getValue().documentNo()));
        colProject.setCellValueFactory(c->new SimpleStringProperty(c.getValue().projectNo()));
        colParty.setCellValueFactory(c->new SimpleStringProperty(c.getValue().partyName()));
        colParent.setCellValueFactory(c->new SimpleStringProperty(parentDisplay(c.getValue())));
        colDate.setCellValueFactory(c->new SimpleStringProperty(str(c.getValue().documentDate())));
        colExpected.setCellValueFactory(c->new SimpleStringProperty(str(c.getValue().expectedDate())));
        colStatus.setCellValueFactory(c->new SimpleStringProperty(c.getValue().status()));
        colTotal.setCellValueFactory(c->new SimpleStringProperty(money(c.getValue().totalAmount())));
        installActions(); table.setItems(rows); DynamicTableLayoutManager.install(table);
        txtSearch.textProperty().addListener((o,a,b)->applyFilter());
        cmbStatus.valueProperty().addListener((o,a,b)->applyFilter());
        refresh();
    }

    @FXML private void refresh(){try{allRows.clear();allRows.addAll(api.list(type));applyFilter();WorkflowScreenContext.Target target=WorkflowScreenContext.consume(type);if(target!=null){allRows.stream().filter(d->Objects.equals(d.id(),target.id())).findFirst().ifPresent(d->{table.getSelectionModel().select(d);table.scrollTo(d);DeepLinkSupport.pulse(table);});}}catch(Exception e){error("Unable to load "+lblTitle.getText(),e.getMessage());}}
    @FXML private void create(){openEditor(null);}
    @FXML private void clearFilters(){txtSearch.clear();cmbStatus.setValue("ALL");}

    private void applyFilter(){String q=txtSearch.getText()==null?"":txtSearch.getText().trim().toLowerCase();String st=cmbStatus.getValue();var f=allRows.stream().filter(d->q.isBlank()||join(d).toLowerCase().contains(q)).filter(d->st==null||"ALL".equals(st)||st.equalsIgnoreCase(d.status())).toList();rows.setAll(f);long open=f.stream().filter(d->!"COMPLETED".equalsIgnoreCase(d.status())&&!"CANCELLED".equalsIgnoreCase(d.status())).count();BigDecimal value=f.stream().map(d->d.totalAmount()==null?BigDecimal.ZERO:d.totalAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);lblTotal.setText(String.valueOf(f.size()));lblOpen.setText(String.valueOf(open));lblValue.setText(money(value));lblRecordCount.setText("Showing "+f.size()+" Records");}

    private void installActions(){
        colActions.setCellFactory(c->new TableCell<>(){
            private final MenuButton actions=new MenuButton("Actions");
            private WorkflowApiClient.Document row;
            {
                actions.getStyleClass().addAll("table-action-menu","approved-row-action");
                actions.setGraphic(IconFactory.compactIcon("actions",15));
                actions.setOnShowing(e->rebuild());
                IconFactory.decorateActionMenu(actions);
            }
            private void rebuild(){
                actions.getItems().clear(); if(row==null)return;
                MenuItem view=new MenuItem("View "+singular(type),IconFactory.compactIcon("view",15));
                view.setOnAction(e->{table.getSelectionModel().select(row);openEditor(row);});
                actions.getItems().add(view);
                if("PROJECT".equals(type)){
                    MenuItem profit=new MenuItem("Profitability",IconFactory.compactIcon("report",15));
                    profit.setOnAction(e->showProfitability(row)); actions.getItems().add(profit);
                }
                if("DISPATCH".equals(type)||"GRN".equals(type)){
                    MenuItem invoice=new MenuItem("DISPATCH".equals(type)?"Create Sales Invoice":"Create Purchase",IconFactory.compactIcon("invoice",15));
                    invoice.setOnAction(e->continueToInvoice(row)); actions.getItems().add(invoice);
                }
                MenuItem edit=new MenuItem("Edit "+singular(type),IconFactory.compactIcon("edit",15)); edit.setOnAction(e->openEditor(row));
                MenuItem del=new MenuItem("Delete "+singular(type),IconFactory.compactIcon("delete",15)); del.getStyleClass().add("danger-menu-item"); del.setOnAction(e->remove(row));
                actions.getItems().addAll(edit,del);
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);row=empty||getIndex()<0||getIndex()>=getTableView().getItems().size()?null:getTableView().getItems().get(getIndex());setGraphic(row==null?null:actions);}
        });
    }

    private void showProfitability(WorkflowApiClient.Document d){if(d==null)return;try{var p=api.profitability(d.documentNo());String text="Project: "+p.projectNo()+"\n\nSales Orders: "+money(p.salesOrdered())+"\nPurchase Commitments: "+money(p.purchaseCommitted())+"\n\nSales Invoiced: "+money(p.salesInvoiced())+"\nPurchase Invoiced: "+money(p.purchaseInvoiced())+"\n\nGross Profit: "+money(p.grossProfit())+"\nGross Margin: "+(p.grossMarginPercent()==null?"0.00":p.grossMarginPercent().setScale(2,RoundingMode.HALF_UP).toPlainString())+"%";OwnedAlert a=new OwnedAlert(Alert.AlertType.INFORMATION,text,ButtonType.OK);a.setHeaderText("Project Profitability");a.showAndWait();}catch(Exception e){error("Profitability unavailable",e.getMessage());}}
    private void continueToInvoice(WorkflowApiClient.Document d){if(d==null)return;if("DISPATCH".equals(type)){WorkflowInvoiceContext.prepareSale(d.projectNo(),d.parentNo(),d.documentNo(),d.customerPoNo());DashboardController.createSaleFromWorkflow();}else if("GRN".equals(type)){WorkflowInvoiceContext.preparePurchase(d.projectNo(),d.parentNo(),d.documentNo());DashboardController.createPurchaseFromWorkflow();}}
    private void remove(WorkflowApiClient.Document d){if(!confirm("Delete "+d.documentNo()+"?\n\nThis removes the workflow record only. Existing Sales/Purchase/Payment records are not changed."))return;try{api.delete(d.id(),d.rowVersion());refresh();}catch(Exception e){error("Delete failed",e.getMessage());}}

    private void openEditor(WorkflowApiClient.Document existing){
        try{
            FXMLLoader loader=new FXMLLoader(ResourceLocator.require("/fxml/pages/WorkflowEditor.fxml"));
            Parent editorRoot=loader.load();
            WorkflowEditorShellController shell=loader.getController();
            ProfessionalUiEnhancer.enhance(editorRoot);

            OwnedDialog<WorkflowApiClient.Document> dlg=new OwnedDialog<>(table);
            String mode=existing==null?"Create ":"Edit ";
            dlg.setTitle(mode+lblTitle.getText());
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
            dlg.getDialogPane().getStyleClass().addAll("modern-dialog","approved-dialog","workflow-standard-dialog","bank-workspace-dialog");
            shell.title().setText(mode+lblTitle.getText());
            shell.subtitle().setText(dialogSubtitle(type));
            shell.headerIcon().getChildren().setAll(IconFactory.icon(meta(type)[2],22));
            shell.notes().setText(existing==null?"":nz(existing.notes()));
            dlg.getDialogPane().setContent(editorRoot);

            TextField no=new TextField(existing==null?"Auto-generated on Save":nz(existing.documentNo()));
            no.setEditable(false); no.setFocusTraversable(false); no.getStyleClass().addAll("approved-input","auto-number-field");
            DatePicker date=new DatePicker(existing==null?LocalDate.now():existing.documentDate());
            DatePicker expected=new DatePicker(existing==null?null:existing.expectedDate());
            ComboBox<String> status=new ComboBox<>(FXCollections.observableArrayList("DRAFT","CONFIRMED","IN PROGRESS","COMPLETED","CANCELLED"));status.setValue(existing==null?"DRAFT":existing.status());
            TextField customerPo=new TextField(existing==null?"":nz(existing.customerPoNo()));
            TextField sourceRef=new TextField(existing==null?"":nz(existing.parentNo()));

            ComboBox<WorkflowApiClient.Document> project=workflowSelector("PROJECT");
            ComboBox<WorkflowApiClient.Document> parent=parentSelector(type);
            String partyType=("PURCHASE_ORDER".equals(type)||"GRN".equals(type))?"SUPPLIER":"CUSTOMER";
            ComboBox<Party> party=partySelector(partyType);

            if(existing!=null){selectDocument(project,existing.projectNo());selectDocument(parent,existing.parentNo());selectParty(party,existing.partyId(),existing.partyName());}

            TextField derivedProject=derived(existing==null?"":nz(existing.projectNo()));
            TextField derivedParty=derived(existing==null?"":nz(existing.partyName()));
            TextField derivedPo=derived(existing==null?"":nz(existing.customerPoNo()));

            List<LineEditor> editors=new ArrayList<>();
            Runnable addLine=()->{LineEditor le=new LineEditor(existing==null?null:existing.id(),null);editors.add(le);shell.lineBox().getChildren().add(le.node);if("SALES_ORDER".equals(type))WorkflowFocusManager.initial(le.item);};
            java.util.function.Consumer<List<WorkflowApiClient.Line>> replaceLines=ls->{shell.lineBox().getChildren().clear();editors.clear();if(ls!=null&&!ls.isEmpty()){for(var line:ls){LineEditor le=new LineEditor(existing==null?null:existing.id(),line);editors.add(le);shell.lineBox().getChildren().add(le.node);}}else addLine.run();};
            if(existing!=null&&existing.lines()!=null&&!existing.lines().isEmpty())replaceLines.accept(existing.lines());else addLine.run();
            shell.addLine().setOnAction(e->addLine.run());UiActionIcons.apply(shell.addLine(),"create","Add Line");

            int r=0; GridPane g=shell.formGrid();
            add(g,"Number",no,r++);
            switch(type){
                case "PROJECT" -> {add(g,"Customer",party,r++);add(g,"Source Quotation / Reference",sourceRef,r++);add(g,"Customer PO No.",customerPo,r++);}
                case "SALES_ORDER" -> {add(g,"Customer",party,r++);add(g,"Project / Job No.",project,r++);add(g,"Customer PO No.",customerPo,r++);}
                case "PURCHASE_ORDER" -> {add(g,"Supplier",party,r++);add(g,"Project / Job No.",project,r++);add(g,"Related Sales Order",parent,r++);}
                case "GRN" -> {add(g,"Purchase Order No.",parent,r++);add(g,"Project / Job No.",derivedProject,r++);add(g,"Supplier",derivedParty,r++);}
                case "DISPATCH" -> {add(g,"Sales Order No.",parent,r++);add(g,"Project / Job No.",derivedProject,r++);add(g,"Customer",derivedParty,r++);add(g,"Customer PO No.",derivedPo,r++);}
            }
            add(g,documentDateLabel(type),date,r++);add(g,expectedDateLabel(type),expected,r++);add(g,"Status",status,r++);

            if(existing==null && ("GRN".equals(type)||"DISPATCH".equals(type))){
                parent.valueProperty().addListener((obs,old,selected)->{
                    if(selected==null)return;
                    derivedProject.setText(nz(selected.projectNo()));derivedParty.setText(nz(selected.partyName()));derivedPo.setText(nz(selected.customerPoNo()));
                    if(selected.lines()!=null&&!selected.lines().isEmpty())replaceLines.accept(selected.lines());
                });
            }
            if(existing==null && "PURCHASE_ORDER".equals(type)){
                parent.valueProperty().addListener((obs,old,selected)->{if(selected!=null&&!nz(selected.projectNo()).isBlank())selectDocument(project,selected.projectNo());});
            }

            Button ok=(Button)dlg.getDialogPane().lookupButton(ButtonType.OK),cancel=(Button)dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
            if(ok!=null){ok.setText(existing==null?"Create":"Save Changes");ok.getStyleClass().addAll("approved-button","approved-primary-button");UiActionIcons.apply(ok,"save",ok.getText());}
            if(cancel!=null){cancel.getStyleClass().addAll("approved-button","approved-secondary-button");UiActionIcons.apply(cancel,"return","Cancel");}

            List<Node> focus=new ArrayList<>();
            switch(type){
                case "PROJECT" -> focus.addAll(List.of(party,sourceRef,customerPo,date,expected,status));
                case "SALES_ORDER" -> focus.addAll(List.of(party,project,customerPo,date,expected,status));
                case "PURCHASE_ORDER" -> focus.addAll(List.of(party,project,parent,date,expected,status));
                case "GRN","DISPATCH" -> focus.addAll(List.of(parent,date,expected,status));
            }
            if(!editors.isEmpty()){focus.add(editors.getFirst().item);focus.add(editors.getFirst().qty);focus.add(editors.getFirst().rate);}
            focus.add(shell.addLine());focus.add(shell.notes());if(ok!=null)focus.add(ok);WorkflowFocusManager.install(focus);if(!focus.isEmpty())WorkflowFocusManager.initial(focus.getFirst());

            if(UiDiagnostics.isEnabled()){
                DesktopLog.info("UI","UI_DIALOG_PROFILE","dialog="+singular(type)+" profile=STANDARD_ERP_DIALOG source=WorkflowEditor.fxml owned=true result=PASS");
                DesktopLog.info("UI","UI_WORKFLOW_FORM_CONTRACT",workflowContract(type));
                UiDiagnostics.audit(editorRoot,"dialog:"+mode.trim()+" "+lblTitle.getText());
            }

            dlg.setResultConverter(bt->{if(bt!=ButtonType.OK)return null;try{
                Party selectedParty=("GRN".equals(type)||"DISPATCH".equals(type))?null:resolvedParty(party);
                WorkflowApiClient.Document selectedProject=project.getValue(),selectedParent=parent.getValue();
                if(("PROJECT".equals(type)||"SALES_ORDER".equals(type)||"PURCHASE_ORDER".equals(type))&&selectedParty==null)throw new IllegalArgumentException(("PURCHASE_ORDER".equals(type)?"Supplier":"Customer")+" must be selected from Master Data");
                if(!"PROJECT".equals(type)&&selectedProject==null&&!("GRN".equals(type)||"DISPATCH".equals(type)))throw new IllegalArgumentException("Project / Job must be selected from existing Projects");
                if(("GRN".equals(type)||"DISPATCH".equals(type))&&selectedParent==null)throw new IllegalArgumentException(("GRN".equals(type)?"Purchase Order":"Sales Order")+" must be selected from existing Project Execution records");
                var ls=editors.stream().map(LineEditor::value).filter(Objects::nonNull).toList();
                if("SALES_ORDER".equals(type)&&!confirmStock(existing==null?null:existing.id(),ls))return null;
                String projectNo="PROJECT".equals(type)?"":("GRN".equals(type)||"DISPATCH".equals(type)?nz(selectedParent.projectNo()):nz(selectedProject==null?null:selectedProject.documentNo()));
                String parentNo=switch(type){case "PROJECT"->sourceRef.getText().trim();case "GRN","DISPATCH","PURCHASE_ORDER"->selectedParent==null?"":nz(selectedParent.documentNo());default->"";};
                String partyName=("GRN".equals(type)||"DISPATCH".equals(type))?nz(selectedParent.partyName()):selectedParty.getName();
                Integer partyId=("GRN".equals(type)||"DISPATCH".equals(type))?selectedParent.partyId():selectedParty.getId();
                String poValue="DISPATCH".equals(type)?nz(selectedParent.customerPoNo()):customerPo.getText().trim();
                return new WorkflowApiClient.Document(existing==null?null:existing.id(),type,existing==null?"":no.getText().trim(),date.getValue(),projectNo,parentNo,partyName,partyId,poValue,expected.getValue(),status.getValue(),BigDecimal.ZERO,shell.notes().getText(),existing==null?0:existing.rowVersion(),ls);
            }catch(Exception ex){error("Validation",ex.getMessage());return null;}});
            dlg.showAndWait().ifPresent(d->{try{api.save(d);refresh();}catch(Exception e){error("Save failed",e.getMessage());}});
        }catch(Exception e){error("Unable to open "+lblTitle.getText(),e.getMessage());}
    }

    private ComboBox<Party> partySelector(String partyType){
        ComboBox<Party> box=new ComboBox<>();box.setEditable(true);box.setMaxWidth(Double.MAX_VALUE);box.getStyleClass().add("approved-input");
        box.setPromptText("Search and select "+("SUPPLIER".equals(partyType)?"Supplier":"Customer")+" Master...");
        box.setConverter(new StringConverter<>(){public String toString(Party p){return p==null?"":partyDisplay(p);}public Party fromString(String s){return null;}});
        try{box.getItems().setAll(partyService.search(partyType,"",100));}catch(Exception ignored){}
        PauseTransition debounce=new PauseTransition(Duration.millis(180));
        debounce.setOnFinished(e->{String q=box.getEditor().getText()==null?"":box.getEditor().getText().trim();UiTaskExecutor.submitLatest("workflow-party-"+partyType+"-"+System.identityHashCode(box),()->partyService.search(partyType,q,40),items->{Party current=box.getValue();box.getItems().setAll(items);if(current!=null&&items.stream().noneMatch(p->p.getId()==current.getId()))box.getItems().add(0,current);if(box.getEditor().isFocused()&&!items.isEmpty()&&!box.isShowing())box.show();},x->{});});
        box.getEditor().textProperty().addListener((o,a,b)->{if(box.getEditor().isFocused())debounce.playFromStart();});
        return box;
    }
    private ComboBox<WorkflowApiClient.Document> workflowSelector(String documentType){ComboBox<WorkflowApiClient.Document> box=new ComboBox<>();box.setMaxWidth(Double.MAX_VALUE);box.getStyleClass().add("approved-input");box.setPromptText("Select existing "+singular(documentType)+"...");box.setConverter(documentConverter());try{box.getItems().setAll(api.list(documentType).stream().filter(d->!"CANCELLED".equalsIgnoreCase(d.status())).toList());}catch(Exception ignored){}return box;}
    private ComboBox<WorkflowApiClient.Document> parentSelector(String childType){String parentType=switch(childType){case "GRN"->"PURCHASE_ORDER";case "DISPATCH","PURCHASE_ORDER"->"SALES_ORDER";default->null;};return parentType==null?emptyDocumentSelector():workflowSelector(parentType);}
    private ComboBox<WorkflowApiClient.Document> emptyDocumentSelector(){ComboBox<WorkflowApiClient.Document> box=new ComboBox<>();box.setMaxWidth(Double.MAX_VALUE);box.getStyleClass().add("approved-input");box.setConverter(documentConverter());return box;}
    private StringConverter<WorkflowApiClient.Document> documentConverter(){return new StringConverter<>(){public String toString(WorkflowApiClient.Document d){if(d==null)return "";String extra=nz(d.partyName());return nz(d.documentNo())+(extra.isBlank()?"":" — "+extra);}public WorkflowApiClient.Document fromString(String s){return null;}};}
    private static String partyDisplay(Party p){return p==null?"":nz(p.getPartyCode())+" — "+nz(p.getName());}
    private static Party resolvedParty(ComboBox<Party> box){if(box==null)return null;if(box.getValue()!=null)return box.getValue();String text=box.getEditor()==null?"":nz(box.getEditor().getText()).trim();return box.getItems().stream().filter(p->partyDisplay(p).equalsIgnoreCase(text)||nz(p.getName()).equalsIgnoreCase(text)||nz(p.getPartyCode()).equalsIgnoreCase(text)).findFirst().orElse(null);}
    private static void selectParty(ComboBox<Party> box,Integer id,String name){if(box==null)return;box.getItems().stream().filter(p->id!=null&&p.getId()==id||id==null&&nz(p.getName()).equalsIgnoreCase(nz(name))).findFirst().ifPresent(box::setValue);}
    private static void selectDocument(ComboBox<WorkflowApiClient.Document> box,String no){if(box==null||no==null||no.isBlank())return;box.getItems().stream().filter(d->no.equalsIgnoreCase(nz(d.documentNo()))).findFirst().ifPresent(box::setValue);}
    private static TextField derived(String text){TextField f=new TextField(text);f.setEditable(false);f.setFocusTraversable(false);f.getStyleClass().addAll("approved-input","derived-reference-field");return f;}

    private boolean confirmStock(Integer documentId,List<WorkflowApiClient.Line> ls){Map<String,BigDecimal> requested=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);for(var line:ls){String code=nz(line.itemCode()).trim();if(!code.isBlank())requested.merge(code,line.quantity()==null?BigDecimal.ZERO:line.quantity(),BigDecimal::add);}List<String> shortages=new ArrayList<>();for(var entry:requested.entrySet()){var a=api.stockAvailability(entry.getKey(),documentId);BigDecimal available=a.freeToPromise()==null?BigDecimal.ZERO:a.freeToPromise();if(entry.getValue().compareTo(available)>0)shortages.add(entry.getKey()+"  Requested "+qty(entry.getValue())+"  Available "+qty(available)+"  Short "+qty(entry.getValue().subtract(available)));}if(shortages.isEmpty())return true;OwnedAlert alert=new OwnedAlert(Alert.AlertType.WARNING,"Current stock is below the requested Sales Order quantity:\n\n"+String.join("\n",shortages)+"\n\nThe order can still be placed; the shortage can be procured later.",ButtonType.YES,ButtonType.NO);alert.setHeaderText("Insufficient Free-to-Promise Stock — Continue?");return alert.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}

    private static void add(GridPane g,String label,Node n,int row){Label caption=new Label(label);String semantic=UiSemanticRegistry.fieldSemantic(label);if(semantic==null)semantic=IconFactory.semanticForLabel(label);if(semantic!=null){caption.setGraphic(IconFactory.icon(semantic,14));IconFactory.applySemanticLabelColour(caption,semantic);caption.getProperties().put("erp.label.icon.semantic",semantic);caption.getProperties().put("erp-icon-preserve",true);}caption.getStyleClass().add("field-label");g.add(caption,0,row);g.add(n,1,row);GridPane.setHgrow(n,Priority.ALWAYS);if(n instanceof Region x)x.setMaxWidth(Double.MAX_VALUE);}
    private static String dialogSubtitle(String t){return switch(t){case "PROJECT"->"Select the customer from Customer Master. The Project ID is generated only when Save succeeds.";case "SALES_ORDER"->"Select the customer and an existing Project / Job. Stock availability is shown per item before order placement.";case "PURCHASE_ORDER"->"Select the supplier and Project / Job. A related Sales Order may be selected when procurement is demand-driven.";case "GRN"->"Select the Purchase Order. Project, supplier and eligible lines are derived from that PO.";default->"Select the Sales Order. Project, customer, Customer PO and lines are derived from that order.";};}
    private static String documentDateLabel(String t){return switch(t){case "PROJECT"->"Start Date";case "SALES_ORDER"->"Order Date";case "PURCHASE_ORDER"->"PO Date";case "GRN"->"Receipt Date";default->"Dispatch Date";};}
    private static String expectedDateLabel(String t){return switch(t){case "PROJECT"->"Target Date";case "SALES_ORDER","PURCHASE_ORDER"->"Expected Date";case "GRN"->"Inspection / Accepted Date";default->"Expected Delivery Date";};}
    private static String workflowContract(String t){return "type="+t+switch(t){case "PROJECT"->" customerSelector=true numberAuto=true rawProjectField=false";case "SALES_ORDER"->" customerSelector=true projectSelector=true stockAvailability=true";case "PURCHASE_ORDER"->" supplierSelector=true projectSelector=true salesOrderSelector=true";case "GRN"->" purchaseOrderSelector=true projectDerived=true supplierDerived=true";default->" salesOrderSelector=true projectDerived=true customerDerived=true";}+" result=PASS";}
    private static String singular(String t){return switch(t){case "PROJECT"->"Project / Job";case "SALES_ORDER"->"Sales Order";case "PURCHASE_ORDER"->"Purchase Order";case "GRN"->"Goods Receipt";default->"Dispatch / Delivery Challan";};}
    private static String[] meta(String t){return switch(t){case"PROJECT"->new String[]{"Projects / Jobs","Central job register linking quotation, orders, procurement, dispatch and invoices","project"};case"SALES_ORDER"->new String[]{"Sales Orders","Customer order execution before dispatch and invoicing","sales-order"};case"PURCHASE_ORDER"->new String[]{"Purchase Orders","Procurement commitments linked to project requirements","purchase-order"};case"GRN"->new String[]{"Goods Receipt Notes","Record material received against purchase orders","goods-receipt"};default->new String[]{"Dispatch / Delivery Challan","Track material dispatched before the existing Sales Invoice flow","dispatch"};};}
    private static String parentDisplay(WorkflowApiClient.Document d){return d.customerPoNo()!=null&&!d.customerPoNo().isBlank()?d.customerPoNo():d.parentNo();}
    private static String join(WorkflowApiClient.Document d){return String.join(" ",nz(d.documentNo()),nz(d.projectNo()),nz(d.parentNo()),nz(d.partyName()),nz(d.customerPoNo()),nz(d.status()));}
    private static String nz(String s){return s==null?"":s;}
    private static String str(LocalDate d){return d==null?"":d.toString();}
    private static String money(BigDecimal v){return "₹ "+(v==null?BigDecimal.ZERO:v).setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private static String qty(BigDecimal v){return (v==null?BigDecimal.ZERO:v).stripTrailingZeros().toPlainString();}
    private boolean confirm(String message){return new OwnedAlert(Alert.AlertType.CONFIRMATION,message,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    private void error(String title,String message){OwnedAlert a=new OwnedAlert(Alert.AlertType.ERROR,message==null?"Unexpected error":message,ButtonType.OK);a.setHeaderText(title);a.showAndWait();}

    private final class LineEditor {
        final VBox node=new VBox(3);final HBox fields=new HBox(6);final TextField item=new TextField(),desc=new TextField(),qtyField=new TextField("1"),rate=new TextField("0");final TextField qty=qtyField;final Label stock=new Label();final ContextMenu suggestions=new ContextMenu();final PauseTransition debounce=new PauseTransition(Duration.millis(170));final Integer documentId;WorkflowApiClient.StockAvailability availability;
        LineEditor(Integer documentId,WorkflowApiClient.Line line){this.documentId=documentId;item.setPromptText("Search item code / description");desc.setPromptText("Description");qty.setPromptText("Qty");rate.setPromptText("Rate");item.setPrefWidth(190);desc.setPrefWidth(300);qty.setPrefWidth(90);rate.setPrefWidth(110);stock.getStyleClass().add("sales-order-stock-availability");Button x=new Button("×");x.getProperties().put("erp.icon.skip",true);x.setOnAction(e->{node.setManaged(false);node.setVisible(false);});fields.getChildren().addAll(item,desc,qty,rate,x);HBox.setHgrow(desc,Priority.ALWAYS);node.getChildren().addAll(fields,stock);if(line!=null){item.setText(nz(line.itemCode()));desc.setText(nz(line.description()));qty.setText(line.quantity()==null?"":line.quantity().toPlainString());rate.setText(line.rate()==null?"":line.rate().toPlainString());Platform.runLater(this::refreshAvailability);}debounce.setOnFinished(e->searchItems());item.textProperty().addListener((o,a,b)->{availability=null;updateStockLabel();if(item.isFocused())debounce.playFromStart();});item.focusedProperty().addListener((o,a,focused)->{if(!focused)suggestions.hide();else if(!item.getText().isBlank())debounce.playFromStart();});item.setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)suggestions.hide();});qty.textProperty().addListener((o,a,b)->updateStockLabel());WorkflowFocusManager.selectAllOnFocus(qty);WorkflowFocusManager.selectAllOnFocus(rate);}
        private void searchItems(){String q=item.getText()==null?"":item.getText().trim();if(q.isBlank()||!item.isFocused()){suggestions.hide();return;}String task="sales-order-item-search-"+System.identityHashCode(this);UiTaskExecutor.submitLatest(task,()->itemService.search(q,12),matches->{if(!item.isFocused())return;suggestions.getItems().clear();for(Item found:matches){String text=nz(found.getItemCode())+" — "+nz(found.getDescription())+"   Available: "+String.format(Locale.ENGLISH,"%.3f",Math.max(0,found.getOpeningStock()-found.getReservedStock()));MenuItem option=new MenuItem(text);option.setOnAction(e->selectItem(found));suggestions.getItems().add(option);}if(matches.isEmpty())suggestions.hide();else if(!suggestions.isShowing())suggestions.show(item,Side.BOTTOM,0,2);},failure->suggestions.hide());}
        private void selectItem(Item found){suggestions.hide();item.setText(nz(found.getItemCode()));desc.setText(nz(found.getDescription()));if(rate.getText().isBlank()||"0".equals(rate.getText().trim()))rate.setText(BigDecimal.valueOf(found.getSellingPrice()).stripTrailingZeros().toPlainString());refreshAvailability();WorkflowFocusManager.initial(qty);}
        private void refreshAvailability(){String code=item.getText()==null?"":item.getText().trim();if(!"SALES_ORDER".equals(type)||code.isBlank())return;UiTaskExecutor.submitLatest("sales-order-stock-"+System.identityHashCode(this),()->api.stockAvailability(code,documentId),a->{availability=a;updateStockLabel();},failure->{availability=null;stock.setText("Stock availability unavailable");stock.getStyleClass().removeAll("stock-positive","stock-warning","stock-shortage");stock.getStyleClass().add("stock-warning");});}
        private void updateStockLabel(){if(!"SALES_ORDER".equals(type)){stock.setManaged(false);stock.setVisible(false);return;}stock.setManaged(true);stock.setVisible(true);stock.getStyleClass().removeAll("stock-positive","stock-warning","stock-shortage");if(availability==null){stock.setText("Select an Item Master item to view stock availability");stock.getStyleClass().add("stock-warning");return;}BigDecimal requested=parse(qty.getText());BigDecimal free=availability.freeToPromise()==null?BigDecimal.ZERO:availability.freeToPromise();BigDecimal after=free.subtract(requested);String shortText=after.signum()<0?"   SHORT: "+qty(after.abs()):"   After order: "+qty(after);stock.setText("On hand: "+qty(availability.onHand())+"   Reserved: "+qty(availability.reserved())+"   Free to promise: "+qty(free)+"   Requested: "+qty(requested)+shortText);stock.getStyleClass().add(after.signum()<0?"stock-shortage":free.signum()==0?"stock-warning":"stock-positive");}
        WorkflowApiClient.Line value(){if(!node.isManaged()||desc.getText().isBlank())return null;BigDecimal q=new BigDecimal(qty.getText().trim()),r=new BigDecimal(rate.getText().trim());if(q.signum()<0||r.signum()<0)throw new IllegalArgumentException("Line quantity/rate cannot be negative");return new WorkflowApiClient.Line(null,null,item.getText().trim(),desc.getText().trim(),q,r,q.multiply(r).setScale(2,RoundingMode.HALF_UP));}
        private BigDecimal parse(String value){try{return new BigDecimal(value==null||value.isBlank()?"0":value.trim());}catch(Exception ignored){return BigDecimal.ZERO;}}
    }
}
