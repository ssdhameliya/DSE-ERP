package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.converter.DoubleStringConverter;
import org.example.api.bank.BankStatementApiClient;
import org.example.bank.KotakBankStatementCsvParser;
import org.example.navigation.NavigationManager;
import org.example.service.SessionService;
import org.example.util.BusinessClock;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.OwnedTextInputDialog;
import org.example.util.UiTaskExecutor;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class BankStatementController {
    @FXML private StackPane pageIcon,kpiTotalIcon,kpiUnmatchedIcon,kpiSuggestedIcon,kpiMatchedIcon,kpiExpenseIcon,kpiCreditsIcon,kpiDebitsIcon,kpiReconciledIcon,howIcon,flowImportIcon,flowReviewIcon,flowAuditIcon;
    @FXML private Button btnImport,btnSearch,btnReset,btnRefresh,btnPrevPage,btnNextPage;
    @FXML private CheckBox chkSelectAll;
    @FXML private Button btnBulkReview,btnBulkIgnore,btnMoveExpense,btnMoveBankEntry;
    @FXML private ComboBox<BankStatementApiClient.BatchDto> cmbBatch;
    @FXML private ComboBox<String> cmbStatus,cmbDirection;
    @FXML private ComboBox<Integer> cmbPageSize;
    @FXML private DatePicker fromDate,toDate;
    @FXML private TextField txtSearch;
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row,String> colDate,colValueDate,colReference,colDescription,colStatus,colMatch;
    @FXML private TableColumn<Row,Boolean> colSelect;
    @FXML private TableColumn<Row,Number> colDebit,colCredit,colBalance;
    @FXML private TableColumn<Row,Void> colAction;
    @FXML private Label kpiTotal,kpiUnmatched,kpiSuggested,kpiMatched,kpiExpense,kpiCredits,kpiDebits,kpiReconciled,lblShowing,lblProgressText,lblBatchStatus,lblPage;
    @FXML private Label lblSelected;
    @FXML private ProgressBar reconciliationProgress;

    private final BankStatementApiClient api = new BankStatementApiClient();
    private final KotakBankStatementCsvParser parser = new KotakBankStatementCsvParser();
    private int currentPage;
    private int totalPages;
    private long totalRows;
    private boolean suppressFilterReload;

    @FXML public void initialize() {
        installIcons();
        cmbStatus.setItems(FXCollections.observableArrayList("All Status","UNMATCHED","SUGGESTED","MATCHED","EXPENSE","REVIEW","IGNORED"));
        cmbStatus.setValue("All Status");
        cmbDirection.setItems(FXCollections.observableArrayList("All","Credit","Debit"));
        cmbDirection.setValue("All");
        cmbPageSize.setItems(FXCollections.observableArrayList(25,50,100));
        cmbPageSize.setValue(50);
        configureTable();
        cmbStatus.valueProperty().addListener((o,a,b)->{ if(b!=null&&!suppressFilterReload) resetPageAndLoad(); });
        cmbDirection.valueProperty().addListener((o,a,b)->{ if(b!=null&&!suppressFilterReload) resetPageAndLoad(); });
        cmbPageSize.valueProperty().addListener((o,a,b)->{ if(b!=null&&!suppressFilterReload) resetPageAndLoad(); });
        cmbBatch.valueProperty().addListener((o,a,b)->{ if(b!=null){applyBatchPeriod();currentPage=0;loadBatch(b.id());} });
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
        if(btnBulkReview!=null) btnBulkReview.setGraphic(IconFactory.compactIcon("status",15));
        if(btnBulkIgnore!=null) btnBulkIgnore.setGraphic(IconFactory.compactIcon("cancel",15));
        if(btnMoveExpense!=null) btnMoveExpense.setGraphic(IconFactory.compactIcon("payment",15));
        if(btnMoveBankEntry!=null) btnMoveBankEntry.setGraphic(IconFactory.compactIcon("bank",15));
    }
    private void setIcon(StackPane pane,String name,int size){ if(pane!=null)pane.getChildren().setAll(IconFactory.icon(name,size)); }

    private void configureTable() {
        table.getProperties().put("erp-keep-selection", true);
        colSelect.setCellValueFactory(v->v.getValue().selected);
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
        colSelect.setEditable(true);table.setEditable(true);
        CheckBox headerSelection=new CheckBox();
        headerSelection.getProperties().put("erp.icon.skip",true);
        chkSelectAll.getProperties().put("erp.icon.skip",true);
        headerSelection.setTooltip(new Tooltip("Select all visible transactions"));
        headerSelection.setOnAction(e->{chkSelectAll.setSelected(headerSelection.isSelected());selectAllVisible();});
        chkSelectAll.selectedProperty().addListener((o,a,b)->{if(!headerSelection.isIndeterminate())headerSelection.setSelected(b);});
        chkSelectAll.indeterminateProperty().addListener((o,a,b)->headerSelection.setIndeterminate(b));
        colSelect.setText(null);
        colSelect.setGraphic(headerSelection);
        colSelect.getStyleClass().add("bank-select-column");
        colSelect.getProperties().put("erp-header-preserve",true);
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
        IconFactory.applyTableHeaderIcon(colReference,"reference"); IconFactory.applyTableHeaderIcon(colDescription,"notes");
        IconFactory.applyTableHeaderIcon(colDebit,"debit"); IconFactory.applyTableHeaderIcon(colCredit,"credit");
        IconFactory.applyTableHeaderIcon(colBalance,"balance"); IconFactory.applyTableHeaderIcon(colStatus,"status");
        IconFactory.applyTableHeaderIcon(colMatch,"link"); IconFactory.applyTableHeaderIcon(colAction,"actions");
    }

    private void moneyCell(TableColumn<Row,Number> col,boolean debit){
        col.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Number n,boolean empty){
            super.updateItem(n,empty); if(empty||n==null){setText(null);setStyle("");return;} setText(String.format(Locale.ENGLISH,"%,.2f",n.doubleValue()));
            setStyle(n.doubleValue()>0?"-fx-text-fill:"+(debit?"#ef4444":"#16a34a")+";-fx-font-weight:800;":"");
        }});
    }

    private MenuButton actionMenu(Row row){
        // Keep the compact in-row menu used by the live v7.3.17 Bank Statement.
        // The v7.3.18 modal action chooser was an unintended UX redesign.
        MenuButton m=new MenuButton("Actions");
        m.getStyleClass().addAll("approved-button","approved-secondary-button","bank-row-action","table-action-menu");
        m.setGraphic(IconFactory.compactIcon("actions",15));
        m.setContentDisplay(ContentDisplay.LEFT);
        m.setGraphicTextGap(6);
        String s=up(row.dto.status());
        section(m,"VIEW");
        add(m,"View Transaction Details","view",()->viewEdit(row));
        add(m,"View Imported Statement","document",this::viewStatementSource);
        add(m,"View Audit History","history",()->audit(row));
        if(Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(s)){
            section(m,"RECONCILIATION");
            add(m,s.equals("SUGGESTED")?"Review Suggested Match":"Match Transaction","link",()->match(row));
            if(row.dto.debit()>0)add(m,"Move to Expense","payment",()->moveToExpense(row));
            section(m,"STATUS");
            if(!"REVIEW".equals(s)) add(m,"Mark for Review","status",()->markReview(row));
            add(m,"Mark as Ignored","cancel",()->ignore(row));
        } else if("MATCHED".equals(s)){
            section(m,"RECONCILIATION");
            add(m,"View Match / Linked Record","link",()->openLinked(row));
            if("BANK_ENTRY".equals(up(row.dto.linkedTargetType())))
                add(m,"View Bank Entry","bank",()->openFinance(row,BankExpenseController.Mode.BANK));
            section(m,"REVERSAL");
            add(m,"Unmatch / Reverse","return",()->reverse(row));
        } else if("EXPENSE".equals(s)){
            section(m,"RECONCILIATION");
            add(m,"View Expense","payment",()->openFinance(row,BankExpenseController.Mode.EXPENSE));
            section(m,"REVERSAL");
            add(m,"Unmatch / Reverse","return",()->reverse(row));
        } else if("IGNORED".equals(s)){
            section(m,"STATUS");
            add(m,"Return to Unmatched","return",()->reverse(row));
        }
        return m;
    }
    private void add(MenuButton m,String text,String icon,Runnable action){
        MenuItem i=new MenuItem(text);
        i.setGraphic(IconFactory.compactIcon(icon,15));
        i.setOnAction(e->action.run());
        m.getItems().add(i);
    }
    private void section(MenuButton m,String text){
        if(!m.getItems().isEmpty())m.getItems().add(new SeparatorMenuItem());
        MenuItem heading=new MenuItem(text);
        heading.setDisable(true);
        heading.getStyleClass().add("bank-menu-section");
        m.getItems().add(heading);
    }

    @FXML private void importStatement(){
        FileChooser f=new FileChooser();f.setTitle("Import Bank Statement CSV");f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bank statement CSV","*.csv"));
        File file=f.showOpenDialog(table.getScene().getWindow()); if(file==null)return;
        String importedBy=user();
        UiTaskExecutor.submitLatest(
            "bank-statement-import",
            () -> {
                var parsed=parser.parse(file.toPath());
                var request=new BankStatementApiClient.ImportRequest(parsed.bankName(),parsed.accountNumber(),parsed.accountHolder(),parsed.statementFrom(),parsed.statementTo(),parsed.currency(),parsed.openingBalance(),parsed.closingBalance(),parsed.sourceFingerprint(),parsed.sourceFileName(),parsed.sourceCsv(),importedBy,parsed.rows());
                return api.importStatement(request);
            },
            result -> { success("Bank statement imported","Imported: "+result.importedRows()+"\nOverlapping duplicates skipped: "+result.duplicateRows()); loadBatches(result.batch().id()); },
            this::error
        );
    }
    private void loadBatches(){loadBatches(null);}

    private void loadBatches(Long preferredBatchId){
        Long selected = preferredBatchId != null
            ? preferredBatchId
            : cmbBatch.getValue()==null ? null : cmbBatch.getValue().id();
        UiTaskExecutor.submitLatest(
            "bank-statement-batches",
            api::batches,
            list -> {
                cmbBatch.getItems().setAll(list);
                if(selected!=null)selectBatch(selected);
                else if(!list.isEmpty())cmbBatch.setValue(list.getFirst());
                else {
                    table.getItems().clear();
                    totalPages=0;totalRows=0;updatePageFooter();
                    clearMetrics();
                }
            },
            this::error
        );
    }

    private void selectBatch(Long id){for(var b:cmbBatch.getItems())if(Objects.equals(b.id(),id)){cmbBatch.setValue(b);return;}}

    private void loadBatch(long id){
        String status=selectedStatus(),direction=selectedDirection(),from=dateText(fromDate.getValue()),to=dateText(toDate.getValue()),query=safe(txtSearch.getText()).trim();
        int requestedPage=currentPage,pageSize=cmbPageSize.getValue()==null?50:cmbPageSize.getValue();
        UiTaskExecutor.submitLatest(
            "bank-statement-batch-load",
            () -> api.page(id,requestedPage,pageSize,status,direction,from,to,query),
            loaded -> {
                if(cmbBatch.getValue()==null || !Objects.equals(cmbBatch.getValue().id(), id)) return;
                currentPage=loaded.page(); totalPages=loaded.totalPages(); totalRows=loaded.totalRows();
                List<Row> rows=loaded.rows()==null?List.of():loaded.rows().stream().map(Row::new).toList();
                rows.forEach(row->row.selected.addListener((o,a,b)->updateSelectionState()));
                table.getItems().setAll(rows);
                chkSelectAll.setSelected(false);chkSelectAll.setIndeterminate(false);updateSelectionState();
                applyMetrics(loaded.metrics()); updatePageFooter();
            },
            this::error
        );
    }

    private void applyBatchPeriod(){
        var b=cmbBatch.getValue();
        if(b==null)return;
        fromDate.setValue(parseDate(b.statementFrom()));
        toDate.setValue(parseDate(b.statementTo()));
    }
    private void applyMetrics(BankStatementApiClient.Metrics m){
        if(m==null){clearMetrics();return;}
        kpiTotal.setText(""+m.total());kpiUnmatched.setText(""+m.unmatched());kpiSuggested.setText(""+m.suggested());kpiMatched.setText(""+m.matched());kpiExpense.setText(""+m.expenses());
        kpiCredits.setText(money(m.totalCredits()));kpiDebits.setText(money(m.totalDebits()));kpiReconciled.setText(String.format(Locale.ENGLISH,"%.1f%%",m.reconciledPercent()));
        lblProgressText.setText(m.reconciled()+" / "+m.total()+" reconciled");
        reconciliationProgress.setProgress(m.total()==0?0:m.reconciledPercent()/100d);
        lblBatchStatus.setText(m.batchStatus());
        applyBatchStatusStyle(m.batchStatus());
    }

    private void clearMetrics(){
        kpiTotal.setText("0");kpiUnmatched.setText("0");kpiSuggested.setText("0");kpiMatched.setText("0");kpiExpense.setText("0");
        kpiCredits.setText("0.00");kpiDebits.setText("0.00");kpiReconciled.setText("0.0%");
        lblProgressText.setText("0 / 0 reconciled");reconciliationProgress.setProgress(0);lblBatchStatus.setText("No statement selected");applyBatchStatusStyle("");
    }

    @FXML private void applyFilters(){resetPageAndLoad();}
    @FXML private void resetFilters(){
        suppressFilterReload=true;
        try{applyBatchPeriod();cmbStatus.setValue("All Status");if(cmbDirection!=null)cmbDirection.setValue("All");txtSearch.clear();currentPage=0;}
        finally{suppressFilterReload=false;}
        reloadCurrentPage();
    }
    @FXML private void refresh(){reloadCurrentPage();}
    @FXML private void previousPage(){if(currentPage>0){currentPage--;reloadCurrentPage();}}
    @FXML private void nextPage(){if(currentPage+1<totalPages){currentPage++;reloadCurrentPage();}}
    private void resetPageAndLoad(){currentPage=0;reloadCurrentPage();}
    private void reloadCurrentPage(){if(cmbBatch.getValue()!=null)loadBatch(cmbBatch.getValue().id());else loadBatches();}
    private String selectedStatus(){String v=cmbStatus==null?null:cmbStatus.getValue();return v==null||v.startsWith("All")?"":up(v);}
    private String selectedDirection(){String v=cmbDirection==null?null:cmbDirection.getValue();return v==null||v.equalsIgnoreCase("All")?"ALL":up(v);}
    private static String dateText(LocalDate value){return value==null?"":value.toString();}
    private void updatePageFooter(){
        int shown=table.getItems().size();
        long start=shown==0?0:(long)currentPage*(cmbPageSize.getValue()==null?50:cmbPageSize.getValue())+1;
        long end=shown==0?0:start+shown-1;
        lblShowing.setText(shown==0?"Showing 0 records":"Showing "+start+"–"+end+" of "+totalRows+" records");
        if(lblPage!=null)lblPage.setText(totalPages<=0?"Page 0 of 0":"Page "+(currentPage+1)+" of "+totalPages);
        if(btnPrevPage!=null)btnPrevPage.setDisable(currentPage<=0);
        if(btnNextPage!=null)btnNextPage.setDisable(totalPages<=0||currentPage+1>=totalPages);
    }

    private void applyBatchStatusStyle(String status){
        if(lblBatchStatus==null)return;
        lblBatchStatus.getStyleClass().removeAll("bank-batch-imported","bank-batch-partial","bank-batch-full");
        String value=up(status);
        if(value.contains("PARTIAL"))lblBatchStatus.getStyleClass().add("bank-batch-partial");
        else if(value.contains("FULL")||value.contains("RECONCILED"))lblBatchStatus.getStyleClass().add("bank-batch-full");
        else if(!value.isBlank())lblBatchStatus.getStyleClass().add("bank-batch-imported");
    }

    @FXML private void selectAllVisible(){boolean selected=chkSelectAll.isSelected();table.getItems().forEach(row->row.selected.set(selected));updateSelectionState();}
    private List<Row> selectedRows(){return table.getItems().stream().filter(row->row.selected.get()).toList();}
    private void updateSelectionState(){
        List<Row> selected=selectedRows(); int count=selected.size();
        if(lblSelected!=null)lblSelected.setText(count+" SELECTED");
        if(btnBulkReview!=null)btnBulkReview.setDisable(count==0);
        if(btnBulkIgnore!=null)btnBulkIgnore.setDisable(count==0);
        boolean one=count==1 && Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(up(selected.getFirst().dto.status()));
        if(btnMoveExpense!=null)btnMoveExpense.setDisable(!one || selected.getFirst().dto.debit()<=0);
        if(btnMoveBankEntry!=null)btnMoveBankEntry.setDisable(!one);
        if(chkSelectAll!=null){
            chkSelectAll.setIndeterminate(count>0&&count<table.getItems().size());
            if(!chkSelectAll.isIndeterminate())chkSelectAll.setSelected(count>0&&!table.getItems().isEmpty());
        }
    }
    @FXML private void moveSelectedToExpense(){
        List<Row> rows=selectedRows();
        if(rows.size()!=1){info("Move to Expense","Select exactly one debit transaction to create the Expense Entry.");return;}
        Row row=rows.getFirst();
        if(row.dto.debit()<=0 || !Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(up(row.dto.status()))){info("Move to Expense","Only one open debit transaction can be moved to Expense Entry.");return;}
        moveToExpense(row);
    }
    @FXML private void moveSelectedToBankEntry(){
        List<Row> rows=selectedRows();
        if(rows.size()!=1){info("Move to Bank Entry","Select exactly one open transaction to create the Bank Entry.");return;}
        Row row=rows.getFirst();
        if(!Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(up(row.dto.status()))){info("Move to Bank Entry","Only an open transaction can be moved to Bank Entry.");return;}
        moveToBankEntry(row);
    }
    @FXML private void bulkMarkReview(){bulkWithReason("Mark Selected for Review","Explain what must be checked for these bank transactions.","REVIEW");}
    @FXML private void bulkIgnore(){bulkWithReason("Ignore Selected Transactions","Enter the audit reason for excluding the selected bank transactions.","IGNORE");}
    private void bulkWithReason(String title,String prompt,String action){
        List<Row> selected=selectedRows();
        List<Row> rows=selected.stream().filter(row->Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(up(row.dto.status()))).toList();
        int skipped=selected.size()-rows.size();
        if(rows.isEmpty()){info(title,"None of the selected transactions can use this action.");return;}
        requiredReason(title,prompt).ifPresent(reason->{
            Alert confirmation=new OwnedAlert(Alert.AlertType.CONFIRMATION,
                    "Selected: "+selected.size()+"\nEligible: "+rows.size()+"\nSkipped: "+skipped+"\n\nReason: "+reason);
            confirmation.setHeaderText(title);
            confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(x->
                UiTaskExecutor.submitLatest(
                    "bank-statement-bulk-"+action.toLowerCase(Locale.ROOT),
                    () -> {
                        int completed=0,failed=0;String firstFailure="";
                        for(Row row:rows){
                            try{
                                if("REVIEW".equals(action))api.review(row.dto.id(),new BankStatementApiClient.NoteRequest(reason,user()));
                                else api.ignore(row.dto.id(),new BankStatementApiClient.IgnoreRequest(reason,user()));
                                completed++;
                            }catch(Exception e){failed++;if(firstFailure.isBlank())firstFailure=safe(e.getMessage());}
                        }
                        return new BulkResult(completed,failed,firstFailure);
                    },
                    bulk -> {
                        String result="Updated: "+bulk.completed()+"\nSkipped: "+skipped+"\nFailed: "+bulk.failed();
                        if(!bulk.firstFailure().isBlank())result+="\n\nFirst failure: "+bulk.firstFailure();
                        if(bulk.failed()==0)success(title,result);else new OwnedAlert(Alert.AlertType.WARNING,result).showAndWait();
                        refresh();
                    },
                    this::error
                )
            );
        });
    }

    private void match(Row row){
        UiTaskExecutor.submitLatest(
            "bank-statement-suggest-"+row.dto.id(),
            () -> api.suggest(row.dto.id()),
            cs -> {
                if(cs.isEmpty()){info("Match Transaction","No eligible Sale, Purchase or Return refund transaction was found. You can move debit transactions to Expense or review later.");refresh();return;}
                var top=cs.getFirst();
                if(Boolean.getBoolean("dse.legacyBankMatchDialog")&&top.confidence()>=75&&Math.abs(top.outstanding()-bankAmount(row.dto))<=.01){
                    Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Suggested Match\n\n"+top+"\n\nWhy suggested: amount/reference/party/date signals.\n\nConfirm this match?");
                    a.setHeaderText(String.format(Locale.ENGLISH,"High Confidence Match • %.0f%%",top.confidence()));
                    ButtonType find=new ButtonType("Find Another",ButtonBar.ButtonData.OTHER);ButtonType confirm=new ButtonType("Confirm Match",ButtonBar.ButtonData.OK_DONE);a.getButtonTypes().setAll(confirm,find,ButtonType.CANCEL);
                    var r=a.showAndWait();if(r.isPresent()&&r.get()==confirm){confirm(row,List.of(top));return;}if(r.isEmpty()||r.get()==ButtonType.CANCEL)return;
                }
                showCandidateWorkspace(row,cs);
            },
            this::error
        );
    }

    private void showCandidateWorkspace(Row bankRow,List<BankStatementApiClient.CandidateDto> candidates){
        double bankValue=bankAmount(bankRow.dto);
        List<CandidateRow> rows=new ArrayList<>();
        double remaining=bankValue;
        for(var candidate:candidates){
            double allocation=Math.min(candidate.outstanding(),Math.max(0,remaining));
            CandidateRow row=new CandidateRow(candidate,allocation);
            rows.add(row);
            remaining-=allocation;
        }

        TableView<CandidateRow> candidatesTable=new TableView<>(FXCollections.observableArrayList(rows));
        candidatesTable.getProperties().put("erp-keep-selection", true);
        candidatesTable.getStyleClass().addAll("approved-table", "erp-table-profile-dialog");
        candidatesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        candidatesTable.setEditable(true);
        candidatesTable.setPrefSize(1100,430);
        TableColumn<CandidateRow,Boolean> selected=new TableColumn<>("Select");
        selected.setCellValueFactory(v->v.getValue().selected);
        selected.setCellFactory(CheckBoxTableCell.forTableColumn(selected));
        selected.setPrefWidth(64);
        TableColumn<CandidateRow,Number> score=new TableColumn<>("Score");
        score.setCellValueFactory(v->v.getValue().confidence);
        score.setPrefWidth(70);
        TableColumn<CandidateRow,String> type=new TableColumn<>("Type");
        type.setCellValueFactory(v->v.getValue().type);
        TableColumn<CandidateRow,String> document=new TableColumn<>("Document");
        document.setCellValueFactory(v->v.getValue().document);
        document.setPrefWidth(130);
        TableColumn<CandidateRow,String> party=new TableColumn<>("Customer / Supplier");
        party.setCellValueFactory(v->v.getValue().party);
        party.setPrefWidth(190);
        TableColumn<CandidateRow,String> date=new TableColumn<>("Date");
        date.setCellValueFactory(v->v.getValue().date);
        TableColumn<CandidateRow,Number> total=new TableColumn<>("Document Total");
        total.setCellValueFactory(v->v.getValue().total);
        total.setPrefWidth(105);
        TableColumn<CandidateRow,Number> paid=new TableColumn<>("Paid / Refunded");
        paid.setCellValueFactory(v->v.getValue().paid);
        paid.setPrefWidth(90);
        TableColumn<CandidateRow,Number> outstanding=new TableColumn<>("Outstanding");
        outstanding.setCellValueFactory(v->v.getValue().outstanding);
        outstanding.setPrefWidth(105);
        TableColumn<CandidateRow,Double> allocation=new TableColumn<>("Allocation");
        allocation.setCellValueFactory(v->v.getValue().allocation.asObject());
        allocation.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        allocation.setOnEditCommit(e->{e.getRowValue().allocation.set(e.getNewValue()==null?0:e.getNewValue());e.getRowValue().selected.set(true);});
        allocation.setPrefWidth(110);
        candidatesTable.getColumns().setAll(selected,score,type,document,party,date,total,paid,outstanding,allocation);
        CheckBox selectAllCandidates=new CheckBox();
        selectAllCandidates.setTooltip(new Tooltip("Select all visible match candidates"));
        selectAllCandidates.setOnAction(e->{ rows.forEach(r->r.selected.set(selectAllCandidates.isSelected())); candidatesTable.refresh(); });
        rows.forEach(r->r.selected.addListener((o,a,b)->selectAllCandidates.setSelected(!rows.isEmpty()&&rows.stream().allMatch(x->x.selected.get()))));
        selected.setGraphic(selectAllCandidates);selected.getProperties().put("erp-header-preserve",true);
        IconFactory.applyTableHeaderIcon(score,"status");IconFactory.applyTableHeaderIcon(type,"category");
        IconFactory.applyTableHeaderIcon(document,"document");IconFactory.applyTableHeaderIcon(party,"customer");
        IconFactory.applyTableHeaderIcon(date,"calendar");IconFactory.applyTableHeaderIcon(total,"currency");
        IconFactory.applyTableHeaderIcon(paid,"complete");IconFactory.applyTableHeaderIcon(outstanding,"balance");
        IconFactory.applyTableHeaderIcon(allocation,"currency");

        Label title=sectionTitle("Match Bank Transaction");
        Label bank=new Label(safe(bankRow.dto.transactionDate())+"  |  "+safe(bankRow.dto.reference())+"  |  "+safe(bankRow.dto.description()));
        bank.setWrapText(true);
        Label amount=new Label((bankRow.dto.credit()>0?"Bank Credit: ":"Bank Debit: ")+money(bankValue));
        amount.getStyleClass().add("bank-dialog-amount");
        Label help=new Label("Select every eligible Sale, Purchase or Return refund included in this bank transaction and edit Allocation directly in the table. The total allocation must equal the bank amount and cannot exceed the outstanding amount.");
        help.setWrapText(true);help.getStyleClass().add("bank-dialog-help");
        Label allocationStatus=new Label();allocationStatus.getStyleClass().add("bank-dialog-help");
        Runnable refreshStatus=()->{
            double allocated=rows.stream().filter(r->r.selected.get()).mapToDouble(r->r.allocation.get()).sum();
            allocationStatus.setText("Allocated: "+money(allocated)+"   |   Remaining: "+money(bankValue-allocated));
        };
        rows.forEach(r->{r.selected.addListener((o,a,b)->refreshStatus.run());r.allocation.addListener((o,a,b)->refreshStatus.run());});
        refreshStatus.run();
        VBox transactionCard=new VBox(4,new Label("BANK TRANSACTION"),bank,amount);transactionCard.getStyleClass().add("bank-dialog-section");
        Label matchId=new Label("Match ID\nBNK-"+safe(bankRow.dto.transactionDate()).replace("-","")+"-"+bankRow.dto.id());
        matchId.getStyleClass().add("bank-match-id");
        HBox hero=dialogHero("link","Review and allocate the complete bank transaction","Match documents/refunds to allocate the bank amount. The total allocation must equal the bank amount.");
        Region heroSpace=new Region();HBox.setHgrow(heroSpace,Priority.ALWAYS);hero.getChildren().addAll(heroSpace,matchId);
        VBox content=new VBox(12,hero,transactionCard,help,candidatesTable,allocationStatus);
        content.setPadding(new Insets(8));content.setPrefWidth(1120);
        Dialog<ButtonType> dialog=new OwnedDialog<>();dialog.setTitle("Match Transaction");dialog.setHeaderText(null);dialog.getDialogPane().getStyleClass().addAll("bank-workspace-dialog","bank-match-dialog");dialog.getDialogPane().setContent(content);
        ButtonType refreshType=new ButtonType("Refresh Suggestions",ButtonBar.ButtonData.OTHER);ButtonType confirm=new ButtonType("Confirm Match",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(refreshType,ButtonType.CANCEL,confirm);
        Button refreshButton=(Button)dialog.getDialogPane().lookupButton(refreshType);refreshButton.setGraphic(IconFactory.compactIcon("refresh",15));refreshButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();dialog.close();javafx.application.Platform.runLater(()->match(bankRow));});
        dialog.showAndWait().filter(confirm::equals).ifPresent(x->{
            List<BankStatementApiClient.AllocationRequest> allocations=new ArrayList<>();
            double allocated=0;
            for(CandidateRow row:rows){
                if(!row.selected.get())continue;
                double value=row.allocation.get();
                if(value<=0||value-row.dto.outstanding()>.01){info("Allocation needs attention","Each selected allocation must be greater than zero and cannot exceed its outstanding amount.");return;}
                allocations.add(new BankStatementApiClient.AllocationRequest(row.dto.type(),row.dto.id(),value));allocated+=value;
            }
            if(allocations.isEmpty()){info("Match Transaction","Select at least one eligible Sale, Purchase or Return refund transaction.");return;}
            if(Math.abs(allocated-bankValue)>.01){info("Allocation needs attention","Allocated amount must equal the bank amount. Remaining: "+money(bankValue-allocated));return;}
            confirmAllocations(bankRow,allocations);
        });
    }

    private void confirmAllocations(Row row,List<BankStatementApiClient.AllocationRequest> allocations){
        String performedBy=user();
        UiTaskExecutor.submitLatest(
            "bank-statement-match-"+row.dto.id(),
            () -> api.match(row.dto.id(),new BankStatementApiClient.MatchRequest(performedBy,allocations)),
            result -> {success("Match Successful",result.message()+"\n\nBank Entry and payment/refund allocations were updated together.");refresh();},
            this::error
        );
    }
    private void showCandidatePicker(Row row,List<BankStatementApiClient.CandidateDto> cs){
        Label title=new Label("Find a Sale / Purchase / Return refund transaction"); title.getStyleClass().add("bank-dialog-title");
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
        d.showAndWait().filter(x->x==confirm).ifPresent(x->{var selected=new ArrayList<>(list.getSelectionModel().getSelectedItems());if(selected.isEmpty()){info("Match Transaction","Select at least one eligible Sale, Purchase or Return refund transaction before continuing.");return;}confirm(row,selected);});
    }
    private void confirm(Row row,List<BankStatementApiClient.CandidateDto> selected){
        double remaining=bankAmount(row.dto);List<BankStatementApiClient.AllocationRequest> alloc=new ArrayList<>();
        for(var c:selected){double suggested=Math.min(c.outstanding(),remaining);TextInputDialog d=new OwnedTextInputDialog(String.format(Locale.ROOT,"%.2f",suggested));d.setTitle("Allocate Payment");d.setHeaderText(c.documentNo()+" • "+c.partyName()+" • Outstanding "+money(c.outstanding()));d.setContentText("Allocation amount:");Optional<String>v=d.showAndWait();if(v.isEmpty())return;double amount=Double.parseDouble(v.get().replace(",","").trim());alloc.add(new BankStatementApiClient.AllocationRequest(c.type(),c.id(),amount));remaining-=amount;}
        String performedBy=user();
        UiTaskExecutor.submitLatest("bank-statement-match-"+row.dto.id(),
            () -> api.match(row.dto.id(),new BankStatementApiClient.MatchRequest(performedBy,alloc)),
            r -> {success("Match Successful",r.message()+"\n\nBank Entry created and payment/refund allocation updated.");refresh();},
            this::error);
    }

    private void moveToExpense(Row row){
        BankExpenseController.requestExpensePrefill(new BankExpenseController.ExpensePrefill(row.dto.id(),row.dto.transactionDate(),row.dto.debit(),row.dto.reference(),row.dto.description(),batchAccountName(),"Bank Statement"));
        DashboardController.navigateFromChild("Expense Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.EXPENSE);
    }
    private void moveToBankEntry(Row row){
        BankExpenseController.requestBankEntryPrefill(new BankExpenseController.BankEntryPrefill(row.dto.id(),row.dto.transactionDate(),row.dto.debit(),row.dto.credit(),row.dto.reference(),row.dto.description(),batchAccountName(),"Bank Statement"));
        DashboardController.navigateFromChild("Bank Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.BANK);
    }
    private String batchAccountName(){
        var batch=cmbBatch==null?null:cmbBatch.getValue();
        if(batch==null)return "";
        String bank=safe(batch.bankName()).trim(),account=safe(batch.bankAccount()).trim();
        if(bank.isBlank())return account;
        if(account.isBlank())return bank;
        return bank+" - "+account;
    }
    private void openFinance(Row row,BankExpenseController.Mode mode){
        Integer financeId=row==null||row.dto.financeEntryId()==null?null:row.dto.financeEntryId();
        if(financeId!=null)BankExpenseController.requestLinkedEntry(mode,financeId);else BankExpenseController.requestMode(mode);
        DashboardController.navigateFromChild(mode==BankExpenseController.Mode.BANK?"Bank Entry":"Expense Entry","/fxml/pages/BankExpense.fxml",mode);
    }
    private void openLinked(Row row){
        var t=row.dto;
        List<BankStatementApiClient.AllocationDto> linked=t.linkedAllocations()==null?List.of():t.linkedAllocations();
        if(linked.size()>1){showLinkedAllocations(row,linked);return;}
        if(linked.size()==1){openAllocation(linked.getFirst(),"Bank Statement #"+t.id());return;}
        String type=up(t.linkedTargetType()); Integer id=t.linkedTargetId(); String no=safe(t.linkedDocumentNo());
        if(type.isBlank()&&t.suggestedMatchType()!=null){type=up(t.suggestedMatchType());id=t.suggestedMatchId();}
        openLinkedTarget(type,id,no,"Bank Statement #"+t.id(),row);
    }
    private void showLinkedAllocations(Row row,List<BankStatementApiClient.AllocationDto> linked){
        Dialog<ButtonType> dialog=new OwnedDialog<>(table);dialog.setTitle("Linked Transactions");dialog.setHeaderText(null);
        VBox rows=new VBox(8);
        for(var allocation:linked){HBox line=new HBox(12);line.setAlignment(Pos.CENTER_LEFT);line.getStyleClass().add("linked-transaction-row");Label document=new Label(safe(allocation.documentNo()).isBlank()?up(allocation.targetType())+" #"+allocation.targetId():allocation.documentNo());document.getStyleClass().add("linked-transaction-document");HBox.setHgrow(document,Priority.ALWAYS);Label type=new Label(up(allocation.targetType()).replace('_',' '));type.getStyleClass().add("linked-transaction-type");Label amount=new Label(money(allocation.allocatedAmount()));amount.getStyleClass().add("linked-transaction-amount");Button view=new Button(linkActionLabel(allocation.targetType()));view.getStyleClass().addAll("approved-button","approved-primary-button");view.setGraphic(IconFactory.compactIcon("view",14));view.setOnAction(e->{dialog.close();openAllocation(allocation,"Bank Statement #"+row.dto.id());});line.getChildren().addAll(document,type,amount,view);rows.getChildren().add(line);}
        VBox content=new VBox(14,dialogHero("link","Linked Transactions",linked.size()+" transactions are linked to this bank entry. Open the exact linked workflow below."),rows);content.setPadding(new Insets(10));content.setPrefWidth(760);
        dialog.getDialogPane().getStyleClass().addAll("bank-workspace-dialog","linked-transactions-dialog");dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.showAndWait();
    }
    private String linkActionLabel(String type){String t=up(type);if("SALE".equals(t)||"PURCHASE".equals(t))return "View Payment";if(t.endsWith("RETURN"))return "View Refund";return "View";}
    private void openAllocation(BankStatementApiClient.AllocationDto allocation,String source){if(allocation==null)return;String type=up(allocation.targetType());Integer id=("EXPENSE".equals(type)||"BANK_ENTRY".equals(type))&&allocation.financeEntryId()!=null?allocation.financeEntryId():allocation.targetId();openLinkedTarget(type,id,safe(allocation.documentNo()),source,null);}
    private void openLinkedTarget(String type,Integer id,String documentNo,String source,Row row){
        if("EXPENSE".equals(type)){Integer financeId=row!=null&&row.dto.financeEntryId()!=null?row.dto.financeEntryId():id;BankExpenseController.requestLinkedEntry(BankExpenseController.Mode.EXPENSE,financeId);DashboardController.navigateFromChild("Expense Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.EXPENSE);return;}
        if("BANK_ENTRY".equals(type)){Integer financeId=row!=null&&row.dto.financeEntryId()!=null?row.dto.financeEntryId():id;BankExpenseController.requestLinkedEntry(BankExpenseController.Mode.BANK,financeId);DashboardController.navigateFromChild("Bank Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.BANK);return;}
        if("SALE".equals(type)){if(documentNo.isBlank()){org.example.util.ModernDialog.warning(table,"Linked record unavailable","Sale document not found","The linked Sale no longer has a usable invoice number.");return;}SalesScreenContext.select(documentNo);NavigationManager.getInstance().loadPage("/fxml/pages/RecordPayment.fxml");return;}
        if("PURCHASE".equals(type)){if(documentNo.isBlank()){org.example.util.ModernDialog.warning(table,"Linked record unavailable","Purchase document not found","The linked Purchase no longer has a usable invoice number.");return;}PurchaseScreenContext.select(documentNo);NavigationManager.getInstance().loadPage("/fxml/pages/PurchasePayment.fxml");return;}
        if("SALES_RETURN".equals(type)||"PURCHASE_RETURN".equals(type)){if(documentNo.isBlank()){new OwnedAlert(Alert.AlertType.WARNING,"The linked Return record no longer has a document number.").showAndWait();return;}ReturnRefundContext.select(documentNo);NavigationManager.getInstance().loadPage("/fxml/pages/ReturnRefund.fxml");return;}
        if(row!=null)audit(row);else new OwnedAlert(Alert.AlertType.INFORMATION,"The linked record type is not available for direct navigation.").showAndWait();
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
        note.setTextFormatter(new TextFormatter<String>(change->change.getControlNewText().length()<=500?change:null));
        Label counter=new Label("0 / 500");counter.getStyleClass().add("bank-note-counter");note.textProperty().addListener((o,a,b)->counter.setText(b.length()+" / 500"));
        VBox overview=new VBox(10,sectionTitle("Transaction Overview"),grid);overview.getStyleClass().add("bank-dialog-section");
        Label direction=new Label(t.credit()>0?"Bank Credit":"Bank Debit");Label amount=new Label("₹ "+money(bankAmount(t)));amount.getStyleClass().add("bank-transaction-hero-amount");
        VBox amountCard=new VBox(6,dialogIcon("bank",38),direction,amount);amountCard.getStyleClass().add("bank-transaction-amount-card");
        VBox side=new VBox(10,amountCard,metricCard("Account Balance","₹ "+money(t.balance()),"bank"),metricCard("Status",safe(t.status()),"status"));side.setPrefWidth(190);
        HBox details=new HBox(12,overview,side);HBox.setHgrow(overview,Priority.ALWAYS);
        Region counterSpace=new Region();HBox.setHgrow(counterSpace,Priority.ALWAYS);HBox noteFooter=new HBox(counterSpace,counter);
        VBox noteCard=new VBox(6,new Label("ERP Note (Internal)"),note,noteFooter);noteCard.getStyleClass().add("bank-dialog-section");
        VBox content=new VBox(12,dialogHero("view","View / Edit Transaction","Detailed information of the imported bank transaction."),details,evidence,noteCard); content.setPadding(new Insets(8)); content.setPrefWidth(760);
        Dialog<ButtonType>d=new OwnedDialog<>(); d.setTitle("Bank Transaction"); d.setHeaderText(null);d.getDialogPane().getStyleClass().addAll("bank-workspace-dialog","bank-transaction-dialog"); d.getDialogPane().setContent(content);
        ButtonType save=new ButtonType("Save Note",ButtonBar.ButtonData.OK_DONE); d.getDialogPane().getButtonTypes().addAll(save,ButtonType.CLOSE);
        d.showAndWait().filter(x->x==save).ifPresent(x->{
            String value=note.getText().trim(),performedBy=user();
            UiTaskExecutor.submitLatest("bank-statement-note-"+t.id(),
                () -> api.updateNote(t.id(),new BankStatementApiClient.NoteRequest(value,performedBy)),
                ignored -> {refresh();},this::error);
        });
    }
    private void viewStatementSource(){
        var batch=cmbBatch.getValue(); if(batch==null)return;
        UiTaskExecutor.submitLatest("bank-statement-source-"+batch.id(),
            () -> api.source(batch.id()),
            source -> renderStatementSource(batch,source),
            this::error);
    }
    private void renderStatementSource(BankStatementApiClient.BatchDto b,BankStatementApiClient.SourceDto src){
        GridPane meta=new GridPane();meta.setHgap(14);meta.setVgap(7);meta.getStyleClass().add("bank-dialog-grid");int r=0;
            addDialogRow(meta,r++,"Bank",safe(b.bankName()));addDialogRow(meta,r++,"Account",safe(b.bankAccount()));addDialogRow(meta,r++,"Account Holder",safe(b.accountHolder()));
            addDialogRow(meta,r++,"Statement Period",safe(b.statementFrom())+"  to  "+safe(b.statementTo()));addDialogRow(meta,r++,"Source File",safe(src.fileName()));addDialogRow(meta,r++,"SHA-256",safe(src.fingerprint()));
            ListView<String> preview=new ListView<>();String[] csvLines=safe(src.csvContent()).split("\\R",-1);for(int i=0;i<csvLines.length;i++)preview.getItems().add(String.format("%4d   %s",i+1,csvLines[i]));preview.setPrefHeight(300);preview.getStyleClass().add("bank-evidence-preview");
            Label help=new Label("This is the original imported CSV evidence retained for reconciliation and audit. Bank values are never changed by ERP matching.");help.setWrapText(true);help.getStyleClass().add("bank-dialog-help");
            Button copy=new Button("Copy SHA-256",IconFactory.compactIcon("copy",14));copy.getStyleClass().addAll("approved-button","approved-secondary-button");copy.setOnAction(e->{ClipboardContent cc=new ClipboardContent();cc.putString(safe(src.fingerprint()));Clipboard.getSystemClipboard().setContent(cc);});
            Region titleSpace=new Region();HBox.setHgrow(titleSpace,Priority.ALWAYS);HBox evidenceTitle=new HBox(8,sectionTitle("Imported Statement Evidence"),titleSpace,copy);evidenceTitle.setAlignment(Pos.CENTER_LEFT);
            VBox evidenceCard=new VBox(10,evidenceTitle,meta);evidenceCard.getStyleClass().add("bank-dialog-section");
            VBox previewCard=new VBox(8,new Label("CSV Evidence Preview"),preview);previewCard.getStyleClass().add("bank-dialog-section");
            VBox content=new VBox(12,dialogHero("document","Statement Source & Evidence","Review the source file and evidence of this imported statement."),evidenceCard,help,previewCard);content.setPadding(new Insets(8));content.setPrefWidth(780);
            Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Imported Bank Statement");d.setHeaderText(null);d.getDialogPane().getStyleClass().addAll("bank-workspace-dialog","bank-evidence-dialog");d.getDialogPane().setContent(content);d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
    }
    private Label sectionTitle(String text){Label l=new Label(text);l.getStyleClass().add("bank-dialog-title");return l;}
    private void addDialogRow(GridPane g,int row,String label,String value){Label a=new Label(label);a.getStyleClass().add("bank-dialog-label");Label b=new Label(value==null||value.isBlank()?"Not available":value);b.setWrapText(true);b.getStyleClass().add("bank-dialog-value");g.add(a,0,row);g.add(b,1,row);GridPane.setHgrow(b,Priority.ALWAYS);}
    private void markReview(Row row){
        requiredReason("Mark for Review","Explain what must be checked before this transaction is reconciled.").ifPresent(reason->{
            String performedBy=user();
            UiTaskExecutor.submitLatest("bank-statement-review-"+row.dto.id(),
                () -> api.review(row.dto.id(),new BankStatementApiClient.NoteRequest(reason,performedBy)),
                ignored -> refresh(),this::error);
        });
    }
    private void ignore(Row row){
        requiredReason("Ignore Bank Transaction","Enter the reason this statement line should be excluded from reconciliation. The reason is retained in the audit history.").ifPresent(reason->{
            Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"This transaction will remain visible in the imported statement and audit trail, but will be excluded from reconciliation totals.\n\nReason: "+reason);
            a.setHeaderText("Confirm ignored transaction");
            a.showAndWait().filter(x->x==ButtonType.OK).ifPresent(x->{
                String performedBy=user();
                UiTaskExecutor.submitLatest("bank-statement-ignore-"+row.dto.id(),
                    () -> api.ignore(row.dto.id(),new BankStatementApiClient.IgnoreRequest(reason,performedBy)),
                    ignored -> refresh(),this::error);
            });
        });
    }
    private Optional<String> requiredReason(String title,String prompt){
        OwnedTextInputDialog dialog=new OwnedTextInputDialog("");dialog.setTitle(title);dialog.setHeaderText(prompt);dialog.setContentText("Required reason:");
        Optional<String> value=dialog.showAndWait().map(String::trim).filter(s->!s.isBlank());
        if(value.isEmpty())info(title,"A reason is required so the decision can be understood from the audit history.");
        return value;
    }
    private void reverse(Row row){Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Reverse this reconciliation? Linked payment/finance records will be safely reversed and the bank transaction will return to UNMATCHED.");a.showAndWait().filter(x->x==ButtonType.OK).ifPresent(x->{String performedBy=user();UiTaskExecutor.submitLatest("bank-statement-reverse-"+row.dto.id(),()->api.reverse(row.dto.id(),performedBy),ignored->refresh(),this::error);});}
    private void audit(Row row){
        UiTaskExecutor.submitLatest("bank-statement-audit-"+row.dto.id(),
            () -> api.audit(row.dto.id()),
            items -> renderAudit(row,items),
            this::error);
    }
    private void renderAudit(Row row,List<BankStatementApiClient.AuditDto> items){
            VBox list=new VBox(8);
            if(items.isEmpty())list.getChildren().add(new Label("No audit history found for this transaction."));
            for(var a:items){
                Label event=new Label(safe(a.eventType())+"  •  "+BusinessClock.formatTimestamp(a.createdAt()));event.getStyleClass().add("bank-audit-event");
                Label detail=new Label(safe(a.detail()));detail.setWrapText(true);detail.getStyleClass().add("bank-audit-detail");
                Label who=new Label("Performed by: "+safe(a.performedBy())+(safe(a.previousStatus()).isBlank()?"":"   •   "+safe(a.previousStatus())+" → "+safe(a.newStatus())));who.getStyleClass().add("bank-audit-meta");
                Label badge=new Label(safe(a.newStatus()).isBlank()?"SUCCESS":safe(a.newStatus()));badge.getStyleClass().add("bank-audit-badge");
                Region eventSpace=new Region();HBox.setHgrow(eventSpace,Priority.ALWAYS);HBox eventLine=new HBox(8,dialogIcon("status",18),event,eventSpace,badge);eventLine.setAlignment(Pos.CENTER_LEFT);
                VBox card=new VBox(5,eventLine,detail,who);card.getStyleClass().add("bank-audit-card");list.getChildren().add(card);
            }
            ScrollPane scroll=new ScrollPane(list);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(360);scroll.setPrefViewportWidth(650);
            VBox content=new VBox(10,sectionTitle("Complete Reconciliation History"),new Label("Bank transaction: "+safe(row.dto.reference())+"  •  "+safe(row.dto.description())),scroll);content.setPadding(new Insets(8));
            content.getChildren().add(0,dialogHero("security","Audit Trail & Evidence","Complete history of reconciliation and evidence for this bank transaction."));content.setPrefWidth(760);
            Dialog<ButtonType>d=new OwnedDialog<>();d.setTitle("Audit History");d.setHeaderText(null);d.getDialogPane().getStyleClass().addAll("bank-workspace-dialog","bank-audit-dialog");d.getDialogPane().setContent(content);d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
    }

    private StackPane dialogIcon(String semantic,double size){StackPane pane=new StackPane(IconFactory.icon(semantic,size*.55));pane.getStyleClass().add("bank-dialog-icon");pane.setMinSize(size,size);pane.setPrefSize(size,size);pane.setMaxSize(size,size);return pane;}
    private HBox dialogHero(String semantic,String title,String subtitle){Label h=new Label(title);h.getStyleClass().add("bank-dialog-hero-title");Label s=new Label(subtitle);s.setWrapText(true);s.getStyleClass().add("bank-dialog-subtitle");HBox box=new HBox(12,dialogIcon(semantic,44),new VBox(3,h,s));box.setAlignment(Pos.CENTER_LEFT);box.getStyleClass().add("bank-dialog-hero");return box;}
    private VBox metricCard(String caption,String value,String semantic){Label c=new Label(caption);c.getStyleClass().add("bank-dialog-label");Label v=new Label(value);v.getStyleClass().add("bank-dialog-metric-value");VBox box=new VBox(4,new HBox(6,IconFactory.compactIcon(semantic,15),c),v);box.getStyleClass().add("bank-dialog-metric-card");return box;}

    private static String user(){var u=SessionService.current();return u==null?"User":safe(u.getFullName());}
    private static double bankAmount(BankStatementApiClient.TransactionDto t){return t.credit()>0?t.credit():t.debit();}
    private static String money(double v){return String.format(Locale.ENGLISH,"%,.2f",v);} private static String safe(String s){return s==null?"":s;}
    private static String up(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT);} private static LocalDate parseDate(String s){try{return LocalDate.parse(s);}catch(Exception e){return null;}}
    private void info(String h,String t){Alert a=new OwnedAlert(Alert.AlertType.INFORMATION,t);a.setHeaderText(h);a.showAndWait();}
    private void success(String h,String t){org.example.util.ToastManager.success(table,h,t);}
    private void error(Throwable e){Alert a=new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?e.toString():e.getMessage());a.setHeaderText("Bank Statement operation failed");a.showAndWait();}

    private record BulkResult(int completed,int failed,String firstFailure) { }

    public static final class Row{
        final BankStatementApiClient.TransactionDto dto;final BooleanProperty selected=new SimpleBooleanProperty(false);final StringProperty date,valueDate,reference,description,status,match;final DoubleProperty debit,credit,balance;
        Row(BankStatementApiClient.TransactionDto t){dto=t;date=new SimpleStringProperty(safe(t.transactionDate()));valueDate=new SimpleStringProperty(safe(t.valueDate()));reference=new SimpleStringProperty(safe(t.reference()));description=new SimpleStringProperty(safe(t.description()));status=new SimpleStringProperty(safe(t.status()));match=new SimpleStringProperty(safe(t.matchLink()));debit=new SimpleDoubleProperty(t.debit());credit=new SimpleDoubleProperty(t.credit());balance=new SimpleDoubleProperty(t.balance());}
    }

    private static final class CandidateRow{
        final BankStatementApiClient.CandidateDto dto;
        final BooleanProperty selected=new SimpleBooleanProperty(false);
        final DoubleProperty confidence=new SimpleDoubleProperty();
        final StringProperty type=new SimpleStringProperty(),document=new SimpleStringProperty(),party=new SimpleStringProperty(),date=new SimpleStringProperty();
        final DoubleProperty total=new SimpleDoubleProperty(),paid=new SimpleDoubleProperty(),outstanding=new SimpleDoubleProperty(),allocation=new SimpleDoubleProperty();
        CandidateRow(BankStatementApiClient.CandidateDto dto,double allocation){
            this.dto=dto;confidence.set(dto.confidence());type.set(safe(dto.type()));document.set(safe(dto.documentNo()));party.set(safe(dto.partyName()));date.set(safe(dto.documentDate()));
            total.set(dto.totalAmount());paid.set(dto.paidAmount());outstanding.set(dto.outstanding());this.allocation.set(allocation);selected.set(allocation>0);
        }
    }
}
