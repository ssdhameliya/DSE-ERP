package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.geometry.Insets;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.api.bank.BankStatementApiClient;
import org.example.bank.KotakBankStatementCsvParser;
import org.example.navigation.NavigationManager;
import org.example.service.SessionService;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.OwnedTextInputDialog;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class BankStatementController {
    @FXML private StackPane pageIcon,kpiTotalIcon,kpiUnmatchedIcon,kpiSuggestedIcon,kpiMatchedIcon,kpiExpenseIcon,kpiCreditsIcon,kpiDebitsIcon,kpiReconciledIcon,howIcon,flowImportIcon,flowReviewIcon,flowAuditIcon;
    @FXML private Button btnImport,btnSearch,btnReset,btnRefresh;
    @FXML private ComboBox<BankStatementApiClient.BatchDto> cmbBatch;
    @FXML private ComboBox<String> cmbStatus,cmbDirection;
    @FXML private DatePicker fromDate,toDate;
    @FXML private TextField txtSearch;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row,String> colDate,colValueDate,colReference,colDescription,colStatus,colMatch;
    @FXML private TableColumn<Row,Number> colDebit,colCredit,colBalance;
    @FXML private TableColumn<Row,Void> colAction;
    @FXML private Label kpiTotal,kpiUnmatched,kpiSuggested,kpiMatched,kpiExpense,kpiCredits,kpiDebits,kpiReconciled,lblShowing,lblProgressText,lblBatchStatus;
    @FXML private ProgressBar reconciliationProgress;

    private final BankStatementApiClient api = new BankStatementApiClient();
    private final KotakBankStatementCsvParser parser = new KotakBankStatementCsvParser();
    private final List<BankStatementApiClient.TransactionDto> all = new ArrayList<>();

    @FXML public void initialize() {
        installIcons();
        cmbStatus.setItems(FXCollections.observableArrayList("All Status","UNMATCHED","SUGGESTED","MATCHED","EXPENSE","REVIEW","IGNORED"));
        cmbStatus.setValue("All Status");
        cmbDirection.setItems(FXCollections.observableArrayList("All","Credit","Debit"));
        cmbDirection.setValue("All");
        configureTable();
        cmbStatus.valueProperty().addListener((o,a,b)->{ if(b!=null) applyFilters(); });
        cmbDirection.valueProperty().addListener((o,a,b)->{ if(b!=null) applyFilters(); });
        cmbBatch.valueProperty().addListener((o,a,b)->{ if(b!=null) loadBatch(b.id()); });
        loadBatches();
    }

    private void installIcons() {
        setIcon(pageIcon,"bank",24); setIcon(kpiTotalIcon,"document",18); setIcon(kpiUnmatchedIcon,"warning",18);
        setIcon(kpiSuggestedIcon,"link",18); setIcon(kpiMatchedIcon,"status",18); setIcon(kpiExpenseIcon,"payment",18);
        setIcon(kpiCreditsIcon,"payment",18); setIcon(kpiDebitsIcon,"payment",18); setIcon(kpiReconciledIcon,"status",18);
        setIcon(howIcon,"info",16); setIcon(flowImportIcon,"import",16); setIcon(flowReviewIcon,"view",16); setIcon(flowAuditIcon,"status",16);
        if(btnImport!=null) btnImport.setGraphic(IconFactory.compactIcon("import",16));
        if(btnSearch!=null) btnSearch.setGraphic(IconFactory.compactIcon("search",15));
        if(btnReset!=null) btnReset.setGraphic(IconFactory.compactIcon("return",15));
        if(btnRefresh!=null) btnRefresh.setGraphic(IconFactory.compactIcon("refresh",15));
    }
    private void setIcon(StackPane pane,String name,int size){ if(pane!=null)pane.getChildren().setAll(IconFactory.icon(name,size)); }

    private void configureTable() {
        colDate.setCellValueFactory(v->v.getValue().date); colValueDate.setCellValueFactory(v->v.getValue().valueDate);
        colReference.setCellValueFactory(v->v.getValue().reference); colDescription.setCellValueFactory(v->v.getValue().description);
        colDebit.setCellValueFactory(v->v.getValue().debit); colCredit.setCellValueFactory(v->v.getValue().credit); colBalance.setCellValueFactory(v->v.getValue().balance);
        colStatus.setCellValueFactory(v->v.getValue().status); colMatch.setCellValueFactory(v->v.getValue().match);
        moneyCell(colDebit,true); moneyCell(colCredit,false); moneyCell(colBalance,false);
        colStatus.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s,boolean empty){
                super.updateItem(s,empty); setText(empty?null:s);
                getStyleClass().removeAll("bank-status-unmatched","bank-status-suggested","bank-status-matched","bank-status-expense","bank-status-review","bank-status-ignored");
                if(!empty&&s!=null)getStyleClass().add("bank-status-"+s.toLowerCase(Locale.ROOT));
            }
        });
        colMatch.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String text,boolean empty){
                super.updateItem(text,empty); setText(null); setGraphic(null);
                if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()||text==null||text.isBlank())return;
                Row row=getTableView().getItems().get(getIndex());
                Hyperlink link=new Hyperlink(text); link.getStyleClass().add("bank-match-link"); link.setOnAction(e->openLinked(row)); setGraphic(link);
            }
        });
        colAction.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(Void v,boolean empty){
                super.updateItem(v,empty); if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()){setGraphic(null);return;}
                setGraphic(actionMenu(getTableView().getItems().get(getIndex())));
            }
        });
        IconFactory.applyTableHeaderIcon(colDate,"calendar"); IconFactory.applyTableHeaderIcon(colValueDate,"calendar");
        IconFactory.applyTableHeaderIcon(colReference,"reference"); IconFactory.applyTableHeaderIcon(colDescription,"document");
        IconFactory.applyTableHeaderIcon(colDebit,"payment"); IconFactory.applyTableHeaderIcon(colCredit,"payment");
        IconFactory.applyTableHeaderIcon(colBalance,"bank"); IconFactory.applyTableHeaderIcon(colStatus,"status");
        IconFactory.applyTableHeaderIcon(colMatch,"link"); IconFactory.applyTableHeaderIcon(colAction,"actions");
    }

    private void moneyCell(TableColumn<Row,Number> col,boolean debit){
        col.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Number n,boolean empty){
            super.updateItem(n,empty); if(empty||n==null){setText(null);setStyle("");return;} setText(String.format(Locale.ENGLISH,"%,.2f",n.doubleValue()));
            setStyle(n.doubleValue()>0?"-fx-text-fill:"+(debit?"#ef4444":"#16a34a")+";-fx-font-weight:800;":"");
        }});
    }

    private MenuButton actionMenu(Row row){
        MenuButton m=new MenuButton("•••"); m.getStyleClass().addAll("approved-button","approved-secondary-button","bank-row-action");
        String s=up(row.dto.status());
        add(m,"View / Edit Transaction","view",()->viewEdit(row));
        if(Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(s)){
            add(m,s.equals("SUGGESTED")?"Review Suggested Match":"Match Transaction","link",()->match(row));
            if(row.dto.debit()>0)add(m,"Move to Expense","payment",()->moveToExpense(row));
            add(m,"View Imported Statement","document",this::viewStatementSource);
            if(!"REVIEW".equals(s)) add(m,"Mark for Review","status",()->markReview(row));
            add(m,"Mark as Ignored","cancel",()->ignore(row));
        } else if("MATCHED".equals(s)){
            add(m,"View Match / Linked Invoice","link",()->openLinked(row));
            add(m,"View Bank Entry","bank",()->openFinance(row,BankExpenseController.Mode.BANK));
            add(m,"Unmatch / Reverse","return",()->reverse(row));
        } else if("EXPENSE".equals(s)){
            add(m,"View Expense","payment",()->openFinance(row,BankExpenseController.Mode.EXPENSE));
            add(m,"Unmatch / Reverse","return",()->reverse(row));
        } else if("IGNORED".equals(s)) add(m,"Return to Unmatched","return",()->reverse(row));
        add(m,"View Audit History","document",()->audit(row));
        return m;
    }
    private void add(MenuButton m,String text,String icon,Runnable action){MenuItem i=new MenuItem(text);i.setGraphic(IconFactory.compactIcon(icon,15));i.setOnAction(e->action.run());m.getItems().add(i);}

    @FXML private void importStatement(){
        FileChooser f=new FileChooser();f.setTitle("Import Bank Statement CSV");f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bank statement CSV","*.csv"));
        File file=f.showOpenDialog(table.getScene().getWindow()); if(file==null)return;
        try{
            var p=parser.parse(file.toPath());
            var req=new BankStatementApiClient.ImportRequest(p.bankName(),p.accountNumber(),p.accountHolder(),p.statementFrom(),p.statementTo(),p.currency(),p.openingBalance(),p.closingBalance(),p.sourceFingerprint(),p.sourceFileName(),p.sourceCsv(),user(),p.rows());
            var r=api.importStatement(req); info("Bank statement imported","Imported: "+r.importedRows()+"\nOverlapping duplicates skipped: "+r.duplicateRows()); loadBatches(); selectBatch(r.batch().id());
        }catch(Exception e){error(e);}
    }
    private void loadBatches(){try{Long selected=cmbBatch.getValue()==null?null:cmbBatch.getValue().id();var list=api.batches();cmbBatch.getItems().setAll(list);if(selected!=null)selectBatch(selected);else if(!list.isEmpty())cmbBatch.setValue(list.getFirst());}catch(Exception e){error(e);}}
    private void selectBatch(Long id){for(var b:cmbBatch.getItems())if(Objects.equals(b.id(),id)){cmbBatch.setValue(b);return;}}
    private void loadBatch(long id){try{
        applyBatchPeriod();
        all.clear();
        all.addAll(api.transactions(id));
        applyFilters();
        loadMetrics(id);
    }catch(Exception e){error(e);}}
    private void applyBatchPeriod(){
        var b=cmbBatch.getValue();
        if(b==null)return;
        fromDate.setValue(parseDate(b.statementFrom()));
        toDate.setValue(parseDate(b.statementTo()));
    }
    private void loadMetrics(long id){var m=api.metrics(id);kpiTotal.setText(""+m.total());kpiUnmatched.setText(""+m.unmatched());kpiSuggested.setText(""+m.suggested());kpiMatched.setText(""+m.matched());kpiExpense.setText(""+m.expenses());kpiCredits.setText(money(m.totalCredits()));kpiDebits.setText(money(m.totalDebits()));kpiReconciled.setText(String.format(Locale.ENGLISH,"%.1f%%",m.reconciledPercent()));lblProgressText.setText(m.reconciled()+" / "+m.total()+" reconciled");reconciliationProgress.setProgress(m.total()==0?0:m.reconciledPercent()/100d);lblBatchStatus.setText(m.batchStatus());}

    @FXML private void applyFilters(){
        String q=txtSearch.getText()==null?"":txtSearch.getText().trim().toLowerCase(Locale.ROOT); String status=cmbStatus.getValue(); String direction=cmbDirection==null?"All":cmbDirection.getValue(); LocalDate from=fromDate.getValue(),to=toDate.getValue(); List<Row> rows=new ArrayList<>();
        for(var t:all){LocalDate d=parseDate(t.transactionDate());if(from!=null&&d!=null&&d.isBefore(from))continue;if(to!=null&&d!=null&&d.isAfter(to))continue;if(status!=null&&!status.startsWith("All")&&!status.equalsIgnoreCase(t.status()))continue;if("Credit".equalsIgnoreCase(direction)&&t.credit()<=0)continue;if("Debit".equalsIgnoreCase(direction)&&t.debit()<=0)continue;String hay=(safe(t.description())+" "+safe(t.reference())+" "+t.debit()+" "+t.credit()+" "+safe(t.status())).toLowerCase(Locale.ROOT);if(!q.isBlank()&&!hay.contains(q))continue;rows.add(new Row(t));}
        table.getItems().setAll(rows);lblShowing.setText("Showing "+rows.size()+" of "+all.size()+" records");
    }
    @FXML private void resetFilters(){applyBatchPeriod();cmbStatus.setValue("All Status");if(cmbDirection!=null)cmbDirection.setValue("All");txtSearch.clear();applyFilters();}
    @FXML private void refresh(){if(cmbBatch.getValue()!=null)loadBatch(cmbBatch.getValue().id());else loadBatches();}

    private void match(Row row){
        try{
            List<BankStatementApiClient.CandidateDto> cs=api.suggest(row.dto.id());
            if(cs.isEmpty()){info("Match Transaction","No open Sales/Purchase transaction was found. You can move debit transactions to Expense or review later.");refresh();return;}
            var top=cs.getFirst();
            if(top.confidence()>=75&&Math.abs(top.outstanding()-bankAmount(row.dto))<=.01){
                Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Suggested Match\n\n"+top+"\n\nWhy suggested: amount/reference/party/date signals.\n\nConfirm this match?");
                a.setHeaderText(String.format(Locale.ENGLISH,"High Confidence Match • %.0f%%",top.confidence()));
                ButtonType find=new ButtonType("Find Another",ButtonBar.ButtonData.OTHER);ButtonType confirm=new ButtonType("Confirm Match",ButtonBar.ButtonData.OK_DONE);a.getButtonTypes().setAll(confirm,find,ButtonType.CANCEL);
                var r=a.showAndWait();if(r.isPresent()&&r.get()==confirm){confirm(row,List.of(top));return;}if(r.isEmpty()||r.get()==ButtonType.CANCEL)return;
            }
            showCandidatePicker(row,cs);
        }catch(Exception e){error(e);}
    }
    private void showCandidatePicker(Row row,List<BankStatementApiClient.CandidateDto> cs){
        Label title=new Label("Find a Sales / Purchase transaction"); title.getStyleClass().add("bank-dialog-title");
        Label bank=new Label(row.dto.transactionDate()+"  •  "+safe(row.dto.reference())+"  •  "+safe(row.dto.description())); bank.setWrapText(true);
        Label amount=new Label((row.dto.credit()>0?"Bank Credit: ":"Bank Debit: ")+money(bankAmount(row.dto))); amount.getStyleClass().add("bank-dialog-amount");
        VBox bankBox=new VBox(4,new Label("BANK TRANSACTION"),bank,amount); bankBox.getStyleClass().add("bank-dialog-section");

        ListView<BankStatementApiClient.CandidateDto> list=new ListView<>(FXCollections.observableArrayList(cs));
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); list.setPrefSize(760,310);
        list.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(BankStatementApiClient.CandidateDto c,boolean empty){super.updateItem(c,empty);if(empty||c==null){setGraphic(null);setText(null);return;}
            Label doc=new Label(c.type()+"  •  "+c.documentNo()+"  •  "+c.partyName()); doc.getStyleClass().add("bank-candidate-title");
            Label detail=new Label("Date: "+safe(c.documentDate())+"   Outstanding: "+money(c.outstanding())+"   Confidence: "+String.format(Locale.ENGLISH,"%.0f%%",c.confidence()));
            detail.getStyleClass().add("bank-candidate-note");
            VBox box=new VBox(2,doc,detail); box.setPadding(new Insets(5,7,5,7)); setGraphic(box); setText(null);}});
        Label hint=new Label("Select one or more open transactions. DSE ERP will allocate the bank amount across your selected records when you confirm."); hint.setWrapText(true); hint.getStyleClass().add("bank-dialog-help");
        VBox content=new VBox(10,title,bankBox,hint,list); content.setPadding(new Insets(4));
        Dialog<ButtonType>d=new OwnedDialog<>(); d.setTitle("Match Transaction"); d.setHeaderText("Review possible matches or choose another transaction"); d.getDialogPane().setContent(content);
        ButtonType confirm=new ButtonType("Confirm Selection",ButtonBar.ButtonData.OK_DONE); d.getDialogPane().getButtonTypes().addAll(confirm,ButtonType.CANCEL);
        d.showAndWait().filter(x->x==confirm).ifPresent(x->{var selected=new ArrayList<>(list.getSelectionModel().getSelectedItems());if(selected.isEmpty()){info("Match Transaction","Select at least one Sales or Purchase transaction before continuing.");return;}confirm(row,selected);});
    }
    private void confirm(Row row,List<BankStatementApiClient.CandidateDto> selected){
        double remaining=bankAmount(row.dto);List<BankStatementApiClient.AllocationRequest> alloc=new ArrayList<>();
        for(var c:selected){double suggested=Math.min(c.outstanding(),remaining);TextInputDialog d=new OwnedTextInputDialog(String.format(Locale.ROOT,"%.2f",suggested));d.setTitle("Allocate Payment");d.setHeaderText(c.documentNo()+" • "+c.partyName()+" • Outstanding "+money(c.outstanding()));d.setContentText("Allocation amount:");Optional<String>v=d.showAndWait();if(v.isEmpty())return;double amount=Double.parseDouble(v.get().replace(",","").trim());alloc.add(new BankStatementApiClient.AllocationRequest(c.type(),c.id(),amount));remaining-=amount;}
        try{var r=api.match(row.dto.id(),new BankStatementApiClient.MatchRequest(user(),alloc));info("Match Successful",r.message()+"\n\nBank Entry created and Sales/Purchase payment allocation updated.");refresh();}catch(Exception e){error(e);}
    }

    private void moveToExpense(Row row){
        BankExpenseController.requestExpensePrefill(new BankExpenseController.ExpensePrefill(row.dto.id(),row.dto.transactionDate(),row.dto.debit(),row.dto.reference(),row.dto.description(),cmbBatch.getValue()==null?"":cmbBatch.getValue().bankName()+" - "+cmbBatch.getValue().bankAccount(),"Bank Statement"));
        DashboardController.navigateFromChild("Expense Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.EXPENSE);
    }
    private void openFinance(Row row,BankExpenseController.Mode mode){DashboardController.navigateFromChild(mode==BankExpenseController.Mode.BANK?"Bank Entry":"Expense Entry","/fxml/pages/BankExpense.fxml",mode);}
    private void openLinked(Row row){
        var t=row.dto; String type=up(t.linkedTargetType()); Integer id=t.linkedTargetId();
        if(type.isBlank()){ if(t.suggestedMatchType()!=null){type=up(t.suggestedMatchType());id=t.suggestedMatchId();} }
        if("EXPENSE".equals(type)){openFinance(row,BankExpenseController.Mode.EXPENSE);return;}
        if("SALE".equals(type)){
            String no=safe(t.linkedDocumentNo()); if(no.isBlank()&&id!=null)no=""+id; SalesScreenContext.select(no); NavigationManager.getInstance().loadPage("/fxml/pages/RecordPayment.fxml"); return;
        }
        if("PURCHASE".equals(type)){
            String no=safe(t.linkedDocumentNo()); if(no.isBlank()&&id!=null)no=""+id; PurchaseScreenContext.select(no); NavigationManager.getInstance().loadPage("/fxml/pages/PurchaseList.fxml"); return;
        }
        audit(row);
    }

    private void viewEdit(Row row){
        var t=row.dto;
        GridPane grid=new GridPane(); grid.setHgap(14); grid.setVgap(8); grid.getStyleClass().add("bank-dialog-grid");
        int r=0; addDialogRow(grid,r++,"Transaction Date",safe(t.transactionDate())); addDialogRow(grid,r++,"Value Date",safe(t.valueDate()));
        addDialogRow(grid,r++,"Reference / Cheque",safe(t.reference())); addDialogRow(grid,r++,"Description / Narration",safe(t.description()));
        addDialogRow(grid,r++,"Debit",money(t.debit())); addDialogRow(grid,r++,"Credit",money(t.credit())); addDialogRow(grid,r++,"Balance",money(t.balance()));
        addDialogRow(grid,r++,"Reconciliation Status",safe(t.status())); addDialogRow(grid,r++,"Match / Link",safe(t.matchLink()).isBlank()?"Not linked yet":safe(t.matchLink()));
        Label evidence=new Label("Original imported bank values are preserved and cannot be overwritten. Add only an ERP note below."); evidence.setWrapText(true); evidence.getStyleClass().add("bank-dialog-help");
        TextArea note=new TextArea(); note.setPromptText("Add an internal ERP note for this bank transaction..."); note.setPrefRowCount(3); note.setWrapText(true);
        VBox content=new VBox(10,sectionTitle("Bank Transaction Details"),grid,evidence,new Label("ERP Note"),note); content.setPadding(new Insets(8)); content.setPrefWidth(610);
        Dialog<ButtonType>d=new OwnedDialog<>(); d.setTitle("Bank Transaction"); d.setHeaderText("View / Edit Transaction"); d.getDialogPane().setContent(content);
        ButtonType save=new ButtonType("Save Note",ButtonBar.ButtonData.OK_DONE); d.getDialogPane().getButtonTypes().addAll(save,ButtonType.CLOSE);
        d.showAndWait().filter(x->x==save).ifPresent(x->{try{api.updateNote(t.id(),new BankStatementApiClient.NoteRequest(note.getText().trim(),user()));info("Bank Transaction","ERP note saved successfully.");audit(row);}catch(Exception e){error(e);}});
    }
    private void viewStatementSource(){
        var b=cmbBatch.getValue(); if(b==null)return;
        try{
            var src=api.source(b.id());
            GridPane meta=new GridPane();meta.setHgap(14);meta.setVgap(7);meta.getStyleClass().add("bank-dialog-grid");int r=0;
            addDialogRow(meta,r++,"Bank",safe(b.bankName()));addDialogRow(meta,r++,"Account",safe(b.bankAccount()));addDialogRow(meta,r++,"Account Holder",safe(b.accountHolder()));
            addDialogRow(meta,r++,"Statement Period",safe(b.statementFrom())+"  to  "+safe(b.statementTo()));addDialogRow(meta,r++,"Source File",safe(src.fileName()));addDialogRow(meta,r++,"SHA-256",safe(src.fingerprint()));
            TextArea preview=new TextArea(safe(src.csvContent()));preview.setEditable(false);preview.setWrapText(false);preview.setPrefRowCount(12);preview.getStyleClass().add("bank-evidence-preview");
            Label help=new Label("This is the original imported CSV evidence retained for reconciliation and audit. Bank values are never changed by ERP matching.");help.setWrapText(true);help.getStyleClass().add("bank-dialog-help");
            VBox content=new VBox(10,sectionTitle("Imported Statement Evidence"),meta,help,new Label("CSV Evidence Preview"),preview);content.setPadding(new Insets(8));content.setPrefWidth(720);
            Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Imported Bank Statement");d.setHeaderText("Statement Source & Evidence");d.getDialogPane().setContent(content);d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
        }catch(Exception e){error(e);}
    }
    private Label sectionTitle(String text){Label l=new Label(text);l.getStyleClass().add("bank-dialog-title");return l;}
    private void addDialogRow(GridPane g,int row,String label,String value){Label a=new Label(label);a.getStyleClass().add("bank-dialog-label");Label b=new Label(value==null||value.isBlank()?"Not available":value);b.setWrapText(true);b.getStyleClass().add("bank-dialog-value");g.add(a,0,row);g.add(b,1,row);GridPane.setHgrow(b,Priority.ALWAYS);}
    private void markReview(Row row){try{api.review(row.dto.id(),new BankStatementApiClient.NoteRequest("Marked for review by user",user()));refresh();}catch(Exception e){error(e);}}
    private void ignore(Row row){Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Mark this bank transaction as intentionally ignored? It remains in the statement and audit history.");a.showAndWait().filter(x->x==ButtonType.OK).ifPresent(x->{try{api.ignore(row.dto.id(),new BankStatementApiClient.IgnoreRequest("Intentionally ignored during reconciliation",user()));refresh();}catch(Exception e){error(e);}});}
    private void reverse(Row row){Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Reverse this reconciliation? Linked payment/finance records will be safely reversed and the bank transaction will return to UNMATCHED.");a.showAndWait().filter(x->x==ButtonType.OK).ifPresent(x->{try{api.reverse(row.dto.id(),user());refresh();}catch(Exception e){error(e);}});}
    private void audit(Row row){
        try{
            var items=api.audit(row.dto.id());
            VBox list=new VBox(8);
            if(items.isEmpty())list.getChildren().add(new Label("No audit history found for this transaction."));
            for(var a:items){
                Label event=new Label(safe(a.eventType())+"  •  "+safe(a.createdAt()));event.getStyleClass().add("bank-audit-event");
                Label detail=new Label(safe(a.detail()));detail.setWrapText(true);detail.getStyleClass().add("bank-audit-detail");
                Label who=new Label("Performed by: "+safe(a.performedBy())+(safe(a.previousStatus()).isBlank()?"":"   •   "+safe(a.previousStatus())+" → "+safe(a.newStatus())));who.getStyleClass().add("bank-audit-meta");
                VBox card=new VBox(3,event,detail,who);card.getStyleClass().add("bank-audit-card");list.getChildren().add(card);
            }
            ScrollPane scroll=new ScrollPane(list);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(360);scroll.setPrefViewportWidth(650);
            VBox content=new VBox(10,sectionTitle("Complete Reconciliation History"),new Label("Bank transaction: "+safe(row.dto.reference())+"  •  "+safe(row.dto.description())),scroll);content.setPadding(new Insets(8));
            Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Audit History");d.setHeaderText("Audit Trail & Evidence");d.getDialogPane().setContent(content);d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
        }catch(Exception e){error(e);}
    }

    private static String user(){var u=SessionService.current();return u==null?"User":safe(u.getFullName());}
    private static double bankAmount(BankStatementApiClient.TransactionDto t){return t.credit()>0?t.credit():t.debit();}
    private static String money(double v){return String.format(Locale.ENGLISH,"%,.2f",v);} private static String safe(String s){return s==null?"":s;}
    private static String up(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT);} private static LocalDate parseDate(String s){try{return LocalDate.parse(s);}catch(Exception e){return null;}}
    private void info(String h,String t){Alert a=new OwnedAlert(Alert.AlertType.INFORMATION,t);a.setHeaderText(h);a.showAndWait();}
    private void error(Throwable e){Alert a=new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?e.toString():e.getMessage());a.setHeaderText("Bank Statement operation failed");a.showAndWait();}

    public static final class Row{
        final BankStatementApiClient.TransactionDto dto;final StringProperty date,valueDate,reference,description,status,match;final DoubleProperty debit,credit,balance;
        Row(BankStatementApiClient.TransactionDto t){dto=t;date=new SimpleStringProperty(safe(t.transactionDate()));valueDate=new SimpleStringProperty(safe(t.valueDate()));reference=new SimpleStringProperty(safe(t.reference()));description=new SimpleStringProperty(safe(t.description()));status=new SimpleStringProperty(safe(t.status()));match=new SimpleStringProperty(safe(t.matchLink()));debit=new SimpleDoubleProperty(t.debit());credit=new SimpleDoubleProperty(t.credit());balance=new SimpleDoubleProperty(t.balance());}
    }
}
