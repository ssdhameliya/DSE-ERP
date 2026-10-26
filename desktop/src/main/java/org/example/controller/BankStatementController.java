package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.converter.DoubleStringConverter;
import org.example.api.bank.BankStatementApiClient;
import org.example.config.ConfigManager;
import org.example.bank.KotakBankStatementCsvParser;
import org.example.navigation.NavigationManager;
import org.example.navigation.ScreenLifecycle;
import org.example.service.SessionService;
import org.example.service.LookupService;
import org.example.service.PermissionService;
import org.example.util.AppDialogRenderer;
import org.example.util.AppDialogService;
import org.example.util.BusinessClock;
import org.example.util.DatePickerFormatter;
import org.example.util.DynamicTableLayoutManager;
import org.example.util.IconFactory;
import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.LiveOperationDialog;
import org.example.util.OwnedChoiceDialog;
import org.example.util.OwnedTextInputDialog;
import org.example.util.PopupTableWorkspace;
import org.example.util.RealtimeSearchSupport;
import org.example.util.SemanticTableCells;
import org.example.util.UiTaskExecutor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import org.example.util.ToastManager;

public class BankStatementController implements ScreenLifecycle {
    @FXML private StackPane pageIcon,kpiTotalIcon,kpiUnmatchedIcon,kpiSuggestedIcon,kpiMatchedIcon,kpiExpenseIcon,kpiCreditsIcon,kpiDebitsIcon,kpiReconciledIcon,howIcon,historyIcon;
    @FXML private VBox cardKpiTotal,cardKpiUnmatched,cardKpiSuggested,cardKpiMatched,cardKpiExpense;
    @FXML private Button btnImport,btnSearch,btnReset,btnRefresh,btnPrevPage,btnNextPage,btnBrowseStatements,btnDeleteStatement,btnCloseHistory,btnHistoryPrev,btnHistoryNext,btnOpenStatement,btnHistoryDelete,btnAutoMatch;
    @FXML private CheckBox chkSelectAll;
    @FXML private Button btnBulkReview,btnBulkIgnore,btnMoveExpense,btnMoveBankEntry;
    @FXML private ComboBox<BankStatementApiClient.BatchDto> cmbBatch;
    @FXML private ComboBox<String> cmbStatus,cmbDirection,cmbHistoryStatus,cmbHistoryAccount;
    @FXML private ComboBox<Integer> cmbPageSize;
    @FXML private DatePicker fromDate,toDate,historyFrom,historyTo;
    @FXML private TextField txtSearch,txtHistorySearch;
    @FXML private TableView<Row> table;
    @FXML private TableView<BankStatementApiClient.BatchDto> tableHistory;
    @FXML private TableColumn<Row,String> colDate,colValueDate,colReference,colDescription,colStatus,colMatch;
    @FXML private TableColumn<Row,Boolean> colSelect;
    @FXML private TableColumn<Row,Number> colDebit,colCredit,colBalance;
    @FXML private TableColumn<Row,Void> colAction;
    @FXML private TableColumn<BankStatementApiClient.BatchDto,String> colHistoryImported,colHistoryBank,colHistoryPeriod,colHistoryStatus;
    @FXML private TableColumn<BankStatementApiClient.BatchDto,Number> colHistoryRows;
    @FXML private Label kpiTotal,kpiUnmatched,kpiSuggested,kpiMatched,kpiExpense,kpiCredits,kpiDebits,kpiReconciled,lblShowing,lblProgressText,lblBatchStatus,lblPage;
    @FXML private Label lblSelected,lblHistoryShowing,lblHistoryPage;
    @FXML private VBox statementHistoryDrawer;
    private OwnedDialog<Void> statementHistoryDialog;
    @FXML private StackPane statementWorkspace;
    @FXML private ProgressBar reconciliationProgress;

    private final BankStatementApiClient api = new BankStatementApiClient();
    private final KotakBankStatementCsvParser parser = new KotakBankStatementCsvParser();
    private final LookupService lookupService = new LookupService();
    private int currentPage;
    private int totalPages;
    private long totalRows;
    private boolean suppressFilterReload;
    private Long pendingLinkedTransactionId;
    private boolean explicitRefreshPending;
    private int historyPage,historyTotalPages;
    private long historyTotalRows;
    private final Map<String,String> historyAccountTokens=new LinkedHashMap<>();
    private List<BankAccountOption> bankAccountOptions=List.of();

    private record BankAccountOption(String accountNumber,String bankName){
        @Override public String toString(){return accountNumber+(bankName==null||bankName.isBlank()?"":" - "+bankName);}
    }

    @FXML public void initialize() {
        DatePickerFormatter.attach(fromDate);
        DatePickerFormatter.attach(toDate);
        DatePickerFormatter.attach(historyFrom);
        DatePickerFormatter.attach(historyTo);
        installIcons();
        cmbStatus.setItems(FXCollections.observableArrayList("All Status","UNMATCHED","SUGGESTED","MATCHED","EXPENSE","REVIEW","IGNORED"));
        cmbStatus.setValue("All Status");
        cmbDirection.setItems(FXCollections.observableArrayList("All","Credit","Debit"));
        cmbDirection.setValue("All");
        cmbPageSize.setItems(FXCollections.observableArrayList(25,50,100));
        cmbPageSize.setValue(50);
        cmbHistoryStatus.setItems(FXCollections.observableArrayList("All Status","IMPORTED","PARTIALLY RECONCILED","FULLY RECONCILED"));
        cmbHistoryStatus.setValue("All Status");
        refreshBankAccountMasterAsync();
        configureTable();
        configureBatchSelector();
        configureHistoryTable();
        if(statementHistoryDrawer!=null){statementHistoryDrawer.setManaged(false);statementHistoryDrawer.setVisible(false);}
        cmbStatus.valueProperty().addListener((o,a,b)->{ if(b!=null&&!suppressFilterReload) resetPageAndLoad(); });
        cmbDirection.valueProperty().addListener((o,a,b)->{ if(b!=null&&!suppressFilterReload) resetPageAndLoad(); });
        cmbBatch.valueProperty().addListener((o,a,b)->{
            updateDeleteButtons();
            if(b!=null){
                applyBatchPeriod();
                currentPage=0;
                loadBatch(b.id());
            } else {
                table.getItems().clear();
                clearMetrics();
                lblShowing.setText("Showing 0 records");
                if(lblBatchStatus!=null)lblBatchStatus.setText("NO STATEMENT");
                if(reconciliationProgress!=null)reconciliationProgress.setProgress(0);
                if(lblProgressText!=null)lblProgressText.setText("0 / 0 reconciled");
            }
        });
        RealtimeSearchSupport.installRemote(txtSearch, () -> { if(!suppressFilterReload) resetPageAndLoad(); });
        RealtimeSearchSupport.installRemote(txtHistorySearch, () -> { historyPage=0; loadStatementHistory(); });
        wireKpiCard(cardKpiTotal, "All Status");
        wireKpiCard(cardKpiUnmatched, "UNMATCHED");
        wireKpiCard(cardKpiSuggested, "SUGGESTED");
        wireKpiCard(cardKpiMatched, "MATCHED");
        wireKpiCard(cardKpiExpense, "EXPENSE");
        loadBatches();
    }

    private void wireKpiCard(VBox card, String statusValue) {
        if (card == null) return;
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> {
            if (cmbStatus != null) {
                cmbStatus.setValue(statusValue);
            }
        });
    }

    @Override public void onScreenShown(boolean reusedFromCache) {
        refreshBankAccountMasterAsync();
        org.example.util.OperationalUiSupport.focusWorkArea(table);
        LinkedRecordContext.Target target=LinkedRecordContext.consume("BANK_STATEMENT");
        if(target==null || target.recordId()==null) return;
        prepareForLinkedTransactionNavigation();
        long transactionId=target.recordId().longValue();
        pendingLinkedTransactionId=transactionId;
        UiTaskExecutor.submitLatest("bank-statement-linked-record",
            () -> {
                BankStatementApiClient.TransactionDto tx=api.transaction(transactionId);
                List<BankStatementApiClient.TransactionDto> rows=api.transactions(tx.importId());
                int index=-1;for(int i=0;i<rows.size();i++)if(Objects.equals(rows.get(i).id(),transactionId)){index=i;break;}
                return new Object[]{tx,Math.max(0,index)};
            },
            result -> {
                BankStatementApiClient.TransactionDto tx=(BankStatementApiClient.TransactionDto)result[0];
                int index=(Integer)result[1];
                suppressFilterReload=true;
                try{
                    cmbStatus.setValue("All Status");
                    cmbDirection.setValue("All");
                    String ref = (tx.reference() != null && !tx.reference().isBlank()) ? tx.reference() : (target.documentNo() != null ? target.documentNo() : "");
                    txtSearch.setText(ref);
                    fromDate.setValue(null);
                    toDate.setValue(null);
                    currentPage=0;
                }
                finally{suppressFilterReload=false;}
                loadBatches(tx.importId());
                javafx.application.Platform.runLater(() -> {currentPage=0;loadBatch(tx.importId());});
            },
            this::error);
    }

    private void installIcons() {
        setIcon(pageIcon,"bank",24); setIcon(kpiTotalIcon,"document",18); setIcon(kpiUnmatchedIcon,"warning",18);
        setIcon(kpiSuggestedIcon,"link",18); setIcon(kpiMatchedIcon,"status",18); setIcon(kpiExpenseIcon,"payment",18);
        setIcon(kpiCreditsIcon,"payment",18); setIcon(kpiDebitsIcon,"payment",18);
        if(kpiReconciledIcon!=null) setIcon(kpiReconciledIcon,"status",18);
        setIcon(howIcon,"info",16); setIcon(historyIcon,"history",18);
        if(btnAutoMatch!=null) btnAutoMatch.setGraphic(IconFactory.compactIcon("link",15));
        if(btnImport!=null) btnImport.setGraphic(IconFactory.compactIcon("import",16));
        if(btnBrowseStatements!=null) btnBrowseStatements.setGraphic(IconFactory.compactIcon("history",15));
        if(btnDeleteStatement!=null) btnDeleteStatement.setGraphic(IconFactory.compactIcon("delete",15));
        if(btnCloseHistory!=null) btnCloseHistory.setGraphic(IconFactory.compactIcon("cancel",14));
        if(btnOpenStatement!=null) btnOpenStatement.setGraphic(IconFactory.compactIcon("view",15));
        if(btnHistoryDelete!=null) btnHistoryDelete.setGraphic(IconFactory.compactIcon("delete",15));
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
        colSelect.setMinWidth(42);
        colSelect.setMaxWidth(46);
        colSelect.setPrefWidth(44);
        colSelect.setResizable(false);
        colSelect.getStyleClass().add("bank-select-column");
        colSelect.getProperties().put("erp-header-preserve",true);
        colDate.setCellValueFactory(v->new SimpleStringProperty(BusinessClock.formatDate(v.getValue().date.get()))); colValueDate.setCellValueFactory(v->new SimpleStringProperty(BusinessClock.formatDate(v.getValue().valueDate.get())));
        colReference.setCellValueFactory(v->v.getValue().reference);
        colReference.setCellFactory(c -> new TableCell<Row, String>() {
            private final javafx.scene.text.Text textNode = new javafx.scene.text.Text();
            {
                textNode.wrappingWidthProperty().bind(c.widthProperty().subtract(16));
                textNode.getStyleClass().addAll("bank-table-wrapped-text", "bank-table-ref-text");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    textNode.setText(item);
                    setGraphic(textNode);
                }
            }
        });
        colDescription.setCellValueFactory(v->v.getValue().description);
        colDescription.setCellFactory(c -> new TableCell<Row, String>() {
            private final javafx.scene.text.Text textNode = new javafx.scene.text.Text();
            {
                textNode.wrappingWidthProperty().bind(c.widthProperty().subtract(18));
                textNode.getStyleClass().add("bank-table-wrapped-text");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    textNode.setText(item);
                    setGraphic(textNode);
                }
            }
        });
        colDebit.setCellValueFactory(v->v.getValue().debit); colCredit.setCellValueFactory(v->v.getValue().credit); colBalance.setCellValueFactory(v->v.getValue().balance);
        colStatus.setCellValueFactory(v->v.getValue().status); colMatch.setCellValueFactory(v->v.getValue().match);
        colMatch.setMinWidth(220);
        moneyCell(colDebit,true); moneyCell(colCredit,false); moneyCell(colBalance,false);
        colStatus.setCellFactory(c->SemanticTableCells.status("bank"));
        colMatch.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String text,boolean empty){
                super.updateItem(text,empty); setText(null); setGraphic(null);
                if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()||text==null||text.isBlank())return;
                Row row=getTableView().getItems().get(getIndex());
                String voucher=row.dto==null?null:safe(row.dto.financeVoucherNo());
                if(voucher!=null&&!voucher.isBlank()&&!voucher.equalsIgnoreCase(text.trim())&&!text.startsWith("Suggested")){
                    HBox box=new HBox(6);
                    box.setAlignment(Pos.CENTER_LEFT);
                    Hyperlink docLink=new Hyperlink(text);
                    docLink.getStyleClass().addAll("bank-match-link","bank-erp-doc-link");
                    docLink.setGraphic(IconFactory.compactIcon("link",12));
                    docLink.setTooltip(new Tooltip("Open ERP Document: "+text));
                    docLink.setOnAction(e->openLinked(row));
                    Hyperlink voucherLink=new Hyperlink(voucher);
                    voucherLink.getStyleClass().addAll("bank-match-link","bank-voucher-link");
                    voucherLink.setGraphic(IconFactory.compactIcon("bank",12));
                    voucherLink.setTooltip(new Tooltip("Open Bank Entry Voucher: "+voucher));
                    voucherLink.setOnAction(e->openFinance(row,BankExpenseController.Mode.BANK));
                    box.getChildren().addAll(docLink,voucherLink);
                    setGraphic(box);
                } else {
                    Hyperlink link=new Hyperlink(text);
                    link.getStyleClass().add("bank-match-link");
                    link.setOnAction(e->openLinked(row));
                    setGraphic(link);
                }
            }
        });
        colAction.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(Void v,boolean empty){
                super.updateItem(v,empty); if(empty||getIndex()<0||getIndex()>=getTableView().getItems().size()){setGraphic(null);return;}
                setGraphic(actionMenu(getTableView().getItems().get(getIndex())));
            }
        });
    }

    private void moneyCell(TableColumn<Row,Number> col,boolean debit){
        col.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Number n,boolean empty){
            super.updateItem(n,empty); getStyleClass().removeAll("erp-value-positive","erp-value-negative"); if(empty||n==null){setText(null);return;} setText(String.format(Locale.ENGLISH,"%,.2f",n.doubleValue()));
            if(n.doubleValue()>0)getStyleClass().add(debit?"erp-value-negative":"erp-value-positive");
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
            if(row.dto.financeEntryId()!=null||"BANK_ENTRY".equals(up(row.dto.linkedTargetType())))
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
        IconFactory.decorateActionMenu(m);
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

    private void configureBatchSelector(){
        if(cmbBatch==null)return;
        javafx.util.Callback<ListView<BankStatementApiClient.BatchDto>,ListCell<BankStatementApiClient.BatchDto>> factory=list->new ListCell<>(){
            @Override protected void updateItem(BankStatementApiClient.BatchDto item,boolean empty){super.updateItem(item,empty);if(empty||item==null){setText(null);setGraphic(null);return;}setText(formatBatchLabel(item));setGraphic(IconFactory.compactIcon(batchStatusSemantic(item.status()),15));}
        };
        cmbBatch.setCellFactory(factory);cmbBatch.setButtonCell(factory.call(null));
    }
    private List<BankAccountOption> readBankAccountMaster(){
        List<BankAccountOption> options=new ArrayList<>();
        try{
            for(var lookup:lookupService.getByCategoryCode("BANK_ACCOUNT")){
                if(lookup==null||!lookup.isActive()||safe(lookup.getLookupValue()).isBlank())continue;
                options.add(new BankAccountOption(lookup.getLookupValue().trim(),safe(lookup.getDescription()).trim()));
            }
        }catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        return List.copyOf(options);
    }
    private void applyBankAccountMaster(List<BankAccountOption> options){
        String selected=cmbHistoryAccount==null?null:cmbHistoryAccount.getValue();
        historyAccountTokens.clear();for(BankAccountOption option:options)historyAccountTokens.put(option.toString(),option.accountNumber());
        bankAccountOptions=List.copyOf(options);
        if(cmbHistoryAccount!=null){cmbHistoryAccount.getItems().setAll(options.stream().map(BankAccountOption::toString).toList());cmbHistoryAccount.setEditable(true);cmbHistoryAccount.setPromptText(options.isEmpty()?"Configure BANK ACCOUNT in Master Data":"All Bank Accounts");if(selected!=null&&cmbHistoryAccount.getItems().contains(selected))cmbHistoryAccount.setValue(selected);cmbHistoryAccount.setOnShowing(e->refreshBankAccountMasterAsync());}
    }
    private void refreshBankAccountMasterAsync(){
        UiTaskExecutor.submitLatest("bank-statement-account-master",this::readBankAccountMaster,this::applyBankAccountMaster,failure->{});
    }
    private void configureBankAccountMaster(){applyBankAccountMaster(readBankAccountMaster());}
    private void configureHistoryTable(){
        if(tableHistory==null)return;
        DynamicTableLayoutManager.install(tableHistory);
        colHistoryImported.setCellValueFactory(v->new SimpleStringProperty(BusinessClock.formatTimestamp(v.getValue().importedAt())));
        colHistoryBank.setCellValueFactory(v->new SimpleStringProperty(batchBankLabel(v.getValue())));
        colHistoryPeriod.setCellValueFactory(v->new SimpleStringProperty(
            (v.getValue().statementFrom()==null?"":BusinessClock.formatDate(v.getValue().statementFrom()))
            + " to "
            + (v.getValue().statementTo()==null?"":BusinessClock.formatDate(v.getValue().statementTo()))
        ));
        colHistoryStatus.setCellValueFactory(v->new SimpleStringProperty(safe(v.getValue().status())));
        colHistoryRows.setCellValueFactory(v->new SimpleIntegerProperty(v.getValue().transactionCount()));
        colHistoryImported.setCellFactory(c->historyTextCell());
        colHistoryBank.setCellFactory(c->historyTextCell());
        colHistoryPeriod.setCellFactory(c->historyTextCell());
        colHistoryStatus.setCellFactory(c->SemanticTableCells.status("reconcile"));
        tableHistory.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{btnOpenStatement.setDisable(b==null);updateDeleteButtons();});
    }
    private TableCell<BankStatementApiClient.BatchDto,String> historyTextCell(){return new TableCell<>(){@Override protected void updateItem(String value,boolean empty){super.updateItem(value,empty);String text=empty?null:safe(value);setText(text);setTooltip(text==null||text.isBlank()?null:new Tooltip(text));}};}
    private boolean isStatementHistoryOpen(){return statementHistoryDialog!=null&&statementHistoryDialog.isShowing();}
    @FXML private void toggleStatementHistory(){
        if(statementHistoryDrawer==null||statementWorkspace==null)return;
        if(isStatementHistoryOpen()){closeStatementHistory();return;}
        if(statementHistoryDrawer.getParent()!=null) statementWorkspace.getChildren().remove(statementHistoryDrawer);
        statementHistoryDrawer.setManaged(true);
        statementHistoryDrawer.setVisible(true);
        statementHistoryDrawer.setMinWidth(0);
        statementHistoryDrawer.setPrefWidth(1040);
        statementHistoryDrawer.setMaxWidth(Double.MAX_VALUE);
        statementHistoryDialog=new OwnedDialog<>(statementWorkspace);
        statementHistoryDialog.setTitle("Bank Statement History");
        statementHistoryDialog.setHeaderText(null);
        statementHistoryDialog.setResizable(true);
        statementHistoryDialog.getDialogPane().getStyleClass().addAll("bank-workspace-dialog","bank-statement-history-dialog");
        statementHistoryDialog.getDialogPane().setContent(statementHistoryDrawer);

        Screen screen = Screen.getPrimary();
        if (statementWorkspace.getScene() != null && statementWorkspace.getScene().getWindow() != null) {
            Window owner = statementWorkspace.getScene().getWindow();
            screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()))
                    .stream().findFirst().orElse(Screen.getPrimary());
        }
        double usableHeight = Math.max(560, screen.getVisualBounds().getHeight() - 60);
        double targetHeight = Math.min(780, usableHeight);
        double usableWidth = Math.max(800, screen.getVisualBounds().getWidth() - 60);
        double targetWidth = Math.min(1080, usableWidth);
        statementHistoryDialog.getDialogPane().setPrefSize(targetWidth, targetHeight);
        statementHistoryDialog.getDialogPane().setMinSize(Math.min(900, targetWidth), Math.min(560, targetHeight));
        statementHistoryDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.application.Platform.runLater(() -> {
            Node closeBtn = statementHistoryDialog.getDialogPane().lookupButton(ButtonType.CLOSE);
            if (closeBtn != null) { closeBtn.setVisible(false); closeBtn.setManaged(false); }
            Node bar = statementHistoryDialog.getDialogPane().lookup(".button-bar");
            if (bar != null) { bar.setVisible(false); bar.setManaged(false); }
        });
        statementHistoryDialog.setOnCloseRequest(e->restoreStatementHistoryDrawer());
        statementHistoryDialog.setResultConverter(button->null);
        historyPage=0;
        loadStatementHistory();
        statementHistoryDialog.show();
    }
    private void restoreStatementHistoryDrawer(){
        if(statementHistoryDrawer==null||statementWorkspace==null)return;
        if(statementHistoryDrawer.getParent()==null)statementWorkspace.getChildren().add(statementHistoryDrawer);
        statementHistoryDrawer.setVisible(false);
        statementHistoryDrawer.setManaged(false);
        if(statementHistoryDialog!=null&&!statementHistoryDialog.isShowing())statementHistoryDialog=null;
    }
    @FXML private void closeStatementHistory(){if(statementHistoryDialog!=null){statementHistoryDialog.close();}else restoreStatementHistoryDrawer();}
    private void prepareForLinkedTransactionNavigation(){
        closeStatementHistory();
        if(tableHistory!=null)tableHistory.getSelectionModel().clearSelection();
        historyPage=0;
    }
    @FXML private void searchStatementHistory(){historyPage=0;loadStatementHistory();}
    @FXML private void resetStatementHistory(){txtHistorySearch.clear();if(cmbHistoryAccount!=null){cmbHistoryAccount.setValue(null);cmbHistoryAccount.getEditor().clear();}cmbHistoryStatus.setValue("All Status");historyFrom.setValue(null);historyTo.setValue(null);historyPage=0;loadStatementHistory();}
    @FXML private void previousStatementHistoryPage(){if(historyPage>0){historyPage--;loadStatementHistory();}}
    @FXML private void nextStatementHistoryPage(){if(historyPage+1<historyTotalPages){historyPage++;loadStatementHistory();}}
    @FXML private void openSelectedHistory(){BankStatementApiClient.BatchDto selected=tableHistory==null?null:tableHistory.getSelectionModel().getSelectedItem();if(selected==null)return;closeStatementHistory();loadBatches(selected.id());}
    @FXML private void deleteCurrentStatement(){deleteStatement(cmbBatch==null?null:cmbBatch.getValue());}
    @FXML private void deleteSelectedHistory(){deleteStatement(tableHistory==null?null:tableHistory.getSelectionModel().getSelectedItem());}
    private void updateDeleteButtons(){
        boolean allowed=PermissionService.allowed("BANK_EXPENSE.DELETE");
        if(btnDeleteStatement!=null)btnDeleteStatement.setDisable(!allowed||cmbBatch==null||cmbBatch.getValue()==null);
        if(btnHistoryDelete!=null)btnHistoryDelete.setDisable(!allowed||tableHistory==null||tableHistory.getSelectionModel().getSelectedItem()==null);
    }
    private void deleteStatement(BankStatementApiClient.BatchDto batch){
        if(batch==null)return;
        if(!PermissionService.allowed("BANK_EXPENSE.DELETE")){info("Delete Bank Statement","You do not have permission to delete Bank Statement imports.");return;}
        UiTaskExecutor.submitLatest("bank-statement-delete-preview-"+batch.id(),()->api.metrics(batch.id()),metrics->{
            if (metrics.matched() > 0 || metrics.reconciled() > 0) {
                int count = Math.max(metrics.matched(), metrics.reconciled());
                AppDialogService.warning(
                    table,
                    "Statement Deletion Blocked",
                    "Statement contains reconciled transactions",
                    "This Bank Statement cannot be deleted because " + count + " transaction" + (count == 1 ? " is" : "s are") + " already matched and reconciled with ERP records.\n\nPlease reverse or unmatch all reconciled transactions individually before deleting the statement."
                );
                return;
            }
            String impact="Transactions: "+metrics.total()+"\nExpense entries: "+metrics.expenses()+"\nIgnored: "+metrics.ignored()+"\n\nAny generated Bank / Expense entry will be reversed first. If any reversal fails, nothing will be deleted.";
            Alert first=new OwnedAlert(Alert.AlertType.CONFIRMATION,formatBatchLabel(batch)+"\n\n"+impact,ButtonType.CANCEL,new ButtonType("Continue",ButtonBar.ButtonData.OK_DONE));
            first.setHeaderText("Delete Bank Statement?");
            Optional<ButtonType> stepOne=first.showAndWait();
            if(stepOne.isEmpty()||stepOne.get().getButtonData()!=ButtonBar.ButtonData.OK_DONE)return;
            TextInputDialog typed=new OwnedTextInputDialog("");typed.setTitle("Final Confirmation");typed.setHeaderText("Permanently delete this Bank Statement import");typed.setContentText("Type DELETE to confirm:");
            Optional<String> confirmation=typed.showAndWait();
            if(confirmation.isEmpty())return;
            if(!"DELETE".equals(confirmation.get().trim())){info("Delete cancelled","Confirmation text did not match DELETE. The statement was not changed.");return;}
            String performedBy=user();
            UiTaskExecutor.submitSerial("bank-statement-delete-"+batch.id(),()->api.deleteBatch(batch.id(),"DELETE",performedBy),result->{
                table.getItems().clear();
                clearMetrics();
                lblShowing.setText("Showing 0 records");
                lblBatchStatus.setText("DELETED");
                if(reconciliationProgress!=null)reconciliationProgress.setProgress(0);
                if(lblProgressText!=null)lblProgressText.setText("0 / 0 reconciled");
                if(cmbBatch!=null){
                    cmbBatch.getItems().removeIf(b->Objects.equals(b.id(),batch.id()));
                    cmbBatch.setValue(null);
                }
                if(isStatementHistoryOpen())loadStatementHistory();
                loadBatches();
                org.example.util.ToastManager.success(table,"Bank Statement Deleted",result.message()+"\n\nTransactions removed: "+result.deletedTransactions()+"\nReconciliations reversed: "+result.reversedTransactions());
            },this::error);
        },this::error);
    }
    private void loadStatementHistory(){
        if(tableHistory==null)return;org.example.util.OperationalUiSupport.showLoading(tableHistory,"Loading statement history…");
        String status=cmbHistoryStatus==null||cmbHistoryStatus.getValue()==null||cmbHistoryStatus.getValue().startsWith("All")?"":cmbHistoryStatus.getValue();
        String account=historyAccountFilter(),q=txtHistorySearch==null?"":safe(txtHistorySearch.getText()).trim();
        String from=dateText(historyFrom==null?null:historyFrom.getValue()),to=dateText(historyTo==null?null:historyTo.getValue());int requested=historyPage;
        UiTaskExecutor.submitLatest("bank-statement-history",()->api.batchPage(requested,50,account,status,from,to,q),page->{historyPage=page.page();historyTotalPages=page.totalPages();historyTotalRows=page.totalRows();tableHistory.getItems().setAll(page.rows()==null?List.of():page.rows());if(tableHistory.getItems().isEmpty())org.example.util.OperationalUiSupport.showEmpty(tableHistory,"No statement imports found","Adjust the history filters or import a new statement.");updateHistoryFooter();},failure->{org.example.util.OperationalUiSupport.showError(tableHistory,"Statement history could not load",failure);error(failure);});
    }
    private String historyAccountFilter(){if(cmbHistoryAccount==null)return "";String text=cmbHistoryAccount.isEditable()?safe(cmbHistoryAccount.getEditor().getText()).trim():safe(cmbHistoryAccount.getValue()).trim();if(text.isBlank())return "";return historyAccountTokens.getOrDefault(text,text);}
    private void updateHistoryFooter(){int shown=tableHistory==null?0:tableHistory.getItems().size();long start=shown==0?0:(long)historyPage*50+1,end=shown==0?0:start+shown-1;if(lblHistoryShowing!=null)lblHistoryShowing.setText(shown==0?"Showing 0 statements":"Showing "+start+"–"+end+" of "+historyTotalRows);if(lblHistoryPage!=null)lblHistoryPage.setText(historyTotalPages<=0?"Page 0 of 0":"Page "+(historyPage+1)+" of "+historyTotalPages);if(btnHistoryPrev!=null)btnHistoryPrev.setDisable(historyPage<=0);if(btnHistoryNext!=null)btnHistoryNext.setDisable(historyTotalPages<=0||historyPage+1>=historyTotalPages);}
    private static String batchBankLabel(BankStatementApiClient.BatchDto b){String account=safe(b.bankAccount());String tail=account.length()>4?"****"+account.substring(account.length()-4):account;return safe(b.bankName())+(tail.isBlank()?"":" • "+tail);}
    private static String formatBatchLabel(BankStatementApiClient.BatchDto b){return batchBankLabel(b)+" • "+safe(b.statementFrom())+" to "+safe(b.statementTo())+" • "+safe(b.status());}
    private static String batchStatusSemantic(String status){String state=up(status);return state.contains("FULL")||state.equals("RECONCILED")?"complete":state.contains("PARTIAL")?"warning":"document";}

    record ImportAuditItem(
        int lineNo,
        int sourceIndex,
        String date,
        String valueDate,
        String description,
        String reference,
        double debit,
        double credit,
        double balance,
        String status,
        String remarks
    ) {}

    record ImportAuditSummary(
        String bankName,
        String accountNumber,
        String statementFrom,
        String statementTo,
        String fileName,
        int totalRows,
        int importedRows,
        int duplicateRows,
        int errorRows,
        BankStatementApiClient.BatchDto batch,
        boolean alreadyImported,
        List<ImportAuditItem> items
    ) {}

    @FXML private void importStatement(){
        configureBankAccountMaster();
        FileChooser f = new FileChooser();
        f.setTitle("Import Bank Statement CSV");
        f.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bank statement CSV", "*.csv"));
        File file = f.showOpenDialog(table.getScene().getWindow());
        if (file == null) return;
        String importedBy = user();

        LiveOperationDialog.execute(
            table,
            "Importing Bank Statement",
            "document",
            "Reading & analyzing CSV statement...",
            reporter -> {
                reporter.stage(1, 4, "Reading & analyzing CSV statement file...");
                reporter.progress(0.15);
                var audit = parser.parseWithAudit(file.toPath());
                reporter.progress(0.40);

                if (audit.statement() == null && audit.errorCount() > 0) {
                    List<ImportAuditItem> items = audit.auditRows().stream()
                            .map(r -> new ImportAuditItem(r.lineNo(), r.sourceIndex(), r.date(), r.valueDate(), r.description(), r.reference(), r.debit(), r.credit(), r.balance(), "INVALID_ERROR", r.error()))
                            .toList();
                    return new ImportAuditSummary("Unknown Bank", "", "", "", file.getName(), audit.totalRows(), 0, 0, audit.errorCount(), null, false, items);
                }

                var parsed = audit.statement();
                reporter.stage(2, 4, "Validating Master Data account (" + parsed.rows().size() + " rows)...");
                reporter.progress(0.60);

                BankAccountOption account = resolveBankAccountAuto(parsed.bankName(), parsed.accountNumber());
                String bankNameToUse = (account != null && !safe(account.bankName()).isBlank()) ? account.bankName() : parsed.bankName();
                String accountNumToUse = account != null ? account.accountNumber() : parsed.accountNumber();

                reporter.stage(3, 4, "Uploading transactions & checking duplicate fingerprints...");
                reporter.progress(0.85);

                BankStatementApiClient.ImportRequest request = new BankStatementApiClient.ImportRequest(
                    bankNameToUse, accountNumToUse, parsed.accountHolder(), parsed.statementFrom(), parsed.statementTo(),
                    parsed.currency(), parsed.openingBalance(), parsed.closingBalance(), parsed.sourceFingerprint(),
                    parsed.sourceFileName(), parsed.sourceCsv(), importedBy, false, parsed.rows());

                BankStatementApiClient.ImportResult result = api.importStatement(request);

                reporter.stage(4, 4, "Compiling statement import audit report...");
                reporter.progress(1.0);

                List<ImportAuditItem> items = new ArrayList<>();
                int importedCount = result.importedRows();
                int dupCount = result.duplicateRows();

                for (int i = 0; i < audit.auditRows().size(); i++) {
                    var ar = audit.auditRows().get(i);
                    if ("INVALID_ERROR".equals(ar.status())) {
                        items.add(new ImportAuditItem(ar.lineNo(), ar.sourceIndex(), ar.date(), ar.valueDate(), ar.description(), ar.reference(), ar.debit(), ar.credit(), ar.balance(), "INVALID_ERROR", ar.error()));
                    } else if (result.alreadyImported()) {
                        items.add(new ImportAuditItem(ar.lineNo(), ar.sourceIndex(), ar.date(), ar.valueDate(), ar.description(), ar.reference(), ar.debit(), ar.credit(), ar.balance(), "DUPLICATE_SKIPPED", "Statement already imported previously"));
                    } else {
                        boolean isDup = i >= importedCount && dupCount > 0;
                        items.add(new ImportAuditItem(ar.lineNo(), ar.sourceIndex(), ar.date(), ar.valueDate(), ar.description(), ar.reference(), ar.debit(), ar.credit(), ar.balance(), isDup ? "DUPLICATE_SKIPPED" : "IMPORTED", isDup ? "Duplicate fingerprint matched in database" : "Successfully recorded in batch"));
                    }
                }

                return new ImportAuditSummary(
                    bankNameToUse, accountNumToUse, parsed.statementFrom(), parsed.statementTo(), file.getName(),
                    audit.totalRows(), result.importedRows(), result.duplicateRows(), audit.errorCount(), result.batch(), result.alreadyImported(), items);
            },
            this::openImportSummaryDialog,
            this::error
        );
    }

    private void openImportSummaryDialog(ImportAuditSummary summary) {
        if (summary == null) return;

        if (reconciliationProgress != null) {
            reconciliationProgress.setProgress(summary.totalRows() == 0 ? 0 : (double) summary.importedRows() / summary.totalRows());
        }
        if (lblProgressText != null) {
            lblProgressText.setText(summary.importedRows() + " / " + summary.totalRows() + " imported");
        }

        Dialog<ButtonType> dialog = new OwnedDialog<>(table);
        dialog.setTitle("Bank Statement Import Summary");
        AppDialogRenderer.configureWorkspace(dialog, summary.errorRows() > 0 ? "warning" : "info",
                "Bank Statement Import Summary",
                "Import Summary: " + summary.fileName(),
                summary.bankName() + " • " + summary.accountNumber() + (summary.statementFrom().isBlank() ? "" : " • " + summary.statementFrom() + " to " + summary.statementTo()),
                "");

        VBox root = new VBox(14);
        root.setPrefWidth(640);
        root.setMinHeight(300);

        HBox metricsRow = new HBox(10);
        metricsRow.setAlignment(Pos.CENTER);

        VBox tileTotal = createMetricTile("Total Rows", String.valueOf(summary.totalRows()), "bank-import-metric-total");
        VBox tileImported = createMetricTile("Imported", String.valueOf(summary.importedRows()), "bank-import-metric-imported");
        VBox tileSkipped = createMetricTile("Duplicates Skipped", String.valueOf(summary.duplicateRows()), "bank-import-metric-skipped");
        VBox tileErrors = createMetricTile("Parse Errors", String.valueOf(summary.errorRows()), "bank-import-metric-error");

        HBox.setHgrow(tileTotal, Priority.ALWAYS);
        HBox.setHgrow(tileImported, Priority.ALWAYS);
        HBox.setHgrow(tileSkipped, Priority.ALWAYS);
        HBox.setHgrow(tileErrors, Priority.ALWAYS);
        metricsRow.getChildren().addAll(tileTotal, tileImported, tileSkipped, tileErrors);

        Label lblStatusMsg = new Label();
        lblStatusMsg.setWrapText(true);
        if (summary.alreadyImported()) {
            lblStatusMsg.setText("Notice: This exact bank statement was already imported previously. No duplicate records were re-created.");
        } else if (summary.errorRows() > 0) {
            lblStatusMsg.setText("Warning: Found " + summary.errorRows() + " transaction row(s) with formatting or validation errors. Download the audit report below to review individual line issues.");
        } else if (summary.importedRows() > 0) {
            lblStatusMsg.setText("Success: Statement imported successfully. " + summary.importedRows() + " new transactions added to reconciliation workspace.");
        } else {
            lblStatusMsg.setText("All transactions in this statement were already present in ERP records.");
        }
        lblStatusMsg.getStyleClass().add("settings-form-label");

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnDownload = new Button("Download Import Report (Excel / CSV)");
        btnDownload.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnDownload.setOnAction(e -> exportImportAuditExcel(summary));

        Button btnOpen = new Button("Open Statement");
        btnOpen.getStyleClass().addAll("approved-button", "approved-primary-button");
        btnOpen.setDisable(summary.batch() == null);
        btnOpen.setOnAction(e -> {
            dialog.setResult(ButtonType.OK);
            dialog.close();
            if (summary.batch() != null) {
                loadBatches(summary.batch().id());
            }
        });

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnClose.setOnAction(e -> {
            dialog.setResult(ButtonType.CLOSE);
            dialog.close();
            if (summary.batch() != null && !summary.alreadyImported() && summary.importedRows() > 0) {
                loadBatches(summary.batch().id());
            }
        });

        actionBox.getChildren().addAll(btnDownload, btnOpen, btnClose);
        root.getChildren().addAll(metricsRow, lblStatusMsg, actionBox);

        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private static VBox createMetricTile(String label, String value, String colorClass) {
        VBox box = new VBox(2);
        box.getStyleClass().add("bank-import-metric-tile");
        Label lblVal = new Label(value);
        lblVal.getStyleClass().addAll("bank-import-metric-num", colorClass);
        Label lblCap = new Label(label);
        lblCap.getStyleClass().add("bank-import-metric-label");
        box.getChildren().addAll(lblVal, lblCap);
        return box;
    }

    private void exportImportAuditExcel(ImportAuditSummary summary) {
        if (summary == null || summary.items().isEmpty()) {
            ToastManager.info(table, "Nothing to Export", "No audit transactions available to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Bank Statement Import Report");
        String safeName = (summary.fileName() != null ? summary.fileName() : "Statement").replaceAll("[^A-Za-z0-9._-]", "_");
        chooser.setInitialFileName("Import_Report_" + safeName + "_" + System.currentTimeMillis() + ".xlsx");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV File (*.csv)", "*.csv")
        );
        File target = chooser.showSaveDialog(table.getScene().getWindow());
        if (target == null) return;

        try {
            if (target.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                exportImportAuditCsv(target, summary);
            } else {
                exportImportAuditXlsx(target, summary);
            }
            ToastManager.success(table, "Report Exported", "Saved import audit report to " + target.getName());
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void exportImportAuditXlsx(File file, ImportAuditSummary summary) throws Exception {
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Import Audit");
            String[] headers = {
                "Line #", "Tx #", "Date", "Value Date", "Description / Narration",
                "Reference / Cheque", "Debit (INR)", "Credit (INR)", "Balance (INR)",
                "Import Status", "Audit Remarks"
            };
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (ImportAuditItem item : summary.items()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(item.lineNo());
                row.createCell(1).setCellValue(item.sourceIndex());
                row.createCell(2).setCellValue(safe(item.date()));
                row.createCell(3).setCellValue(safe(item.valueDate()));
                row.createCell(4).setCellValue(safe(item.description()));
                row.createCell(5).setCellValue(safe(item.reference()));
                row.createCell(6).setCellValue(item.debit());
                row.createCell(7).setCellValue(item.credit());
                row.createCell(8).setCellValue(item.balance());
                row.createCell(9).setCellValue(safe(item.status()));
                row.createCell(10).setCellValue(safe(item.remarks()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
        }
    }

    private void exportImportAuditCsv(File file, ImportAuditSummary summary) throws Exception {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            pw.println("Line #,Tx #,Date,Value Date,Description,Reference,Debit,Credit,Balance,Status,Remarks");
            for (ImportAuditItem item : summary.items()) {
                pw.printf(Locale.ROOT, "%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,\"%s\",\"%s\"%n",
                        item.lineNo(), item.sourceIndex(),
                        escapeCsv(item.date()), escapeCsv(item.valueDate()),
                        escapeCsv(item.description()), escapeCsv(item.reference()),
                        item.debit(), item.credit(), item.balance(),
                        escapeCsv(item.status()), escapeCsv(item.remarks()));
            }
        }
    }

    private static String escapeCsv(String val) {
        return val == null ? "" : val.replace("\"", "\"\"");
    }

    private BankAccountOption resolveBankAccountAuto(String parsedBank, String parsedAccount) {
        if (bankAccountOptions.isEmpty()) configureBankAccountMaster();
        String incoming = compactAccount(parsedAccount);
        if (!incoming.isBlank()) {
            for (BankAccountOption option : bankAccountOptions) {
                if (compactAccount(option.accountNumber()).equals(incoming)) return option;
            }
        }
        return bankAccountOptions.isEmpty() ? null : bankAccountOptions.getFirst();
    }

    private BankAccountOption resolveBankAccount(String parsedBank,String parsedAccount){
        if(bankAccountOptions.isEmpty())configureBankAccountMaster();
        if(bankAccountOptions.isEmpty()){info("Bank Account required","Configure at least one active BANK ACCOUNT in Master Data before importing a statement.");return null;}
        String incoming=compactAccount(parsedAccount);
        if(!incoming.isBlank())for(BankAccountOption option:bankAccountOptions)if(compactAccount(option.accountNumber()).equals(incoming))return option;
        ChoiceDialog<BankAccountOption> dialog=new OwnedChoiceDialog<>(bankAccountOptions.getFirst(),bankAccountOptions);dialog.setTitle("Select Bank Account");dialog.setHeaderText("Confirm the Bank Account for this statement");String detected=(safe(parsedBank)+" "+safe(parsedAccount)).trim();dialog.setContentText(detected.isBlank()?"The statement did not expose a recognized account. Select the account:":"Detected: "+detected+"\nSelect the matching Master Data account:");return dialog.showAndWait().orElse(null);
    }
    private static String compactAccount(String value){return safe(value).replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);}
    private void loadBatches(){loadBatches(null);}

    private void loadBatches(Long preferredBatchId){
        org.example.util.OperationalUiSupport.showLoading(table,"Loading bank statements…");
        Long selected = preferredBatchId != null
            ? preferredBatchId
            : cmbBatch.getValue()==null ? null : cmbBatch.getValue().id();
        UiTaskExecutor.submitLatest(
            "bank-statement-batches",
            () -> {List<BankStatementApiClient.BatchDto> list=new ArrayList<>(api.batches());if(preferredBatchId!=null&&list.stream().noneMatch(b->Objects.equals(b.id(),preferredBatchId)))list.add(0,api.batch(preferredBatchId));return list;},
            list -> {
                cmbBatch.getItems().setAll(list);
                if(selected!=null)selectBatch(selected);
                else if(!list.isEmpty())cmbBatch.setValue(list.getFirst());
                else {
                    table.getItems().clear();
                    totalPages=0;totalRows=0;updatePageFooter();
                    clearMetrics();
                    org.example.util.OperationalUiSupport.showEmpty(table,"No bank statements imported","Import a bank statement to start reconciliation.");
                    finishExplicitRefresh(true);
                }
            },
            failure->{finishExplicitRefresh(false);org.example.util.OperationalUiSupport.showError(table,"Bank statements could not load",failure);error(failure);}
        );
    }

    private void selectBatch(Long id){for(var b:cmbBatch.getItems())if(Objects.equals(b.id(),id)){cmbBatch.setValue(b);return;}}

    private void loadBatch(long id){
        org.example.util.OperationalUiSupport.showLoading(table,"Loading statement transactions…");
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
                if(rows.isEmpty())org.example.util.OperationalUiSupport.showEmpty(table,"No statement transactions found","Adjust the filters or select another imported statement.");
                chkSelectAll.setSelected(false);chkSelectAll.setIndeterminate(false);updateSelectionState();
                applyMetrics(loaded.metrics()); updatePageFooter();
                if(pendingLinkedTransactionId!=null){
                    Row match=rows.stream().filter(r->Objects.equals(r.dto.id(),pendingLinkedTransactionId)).findFirst().orElse(null);
                    if(match!=null){
                        table.getItems().setAll(match);
                        table.getSelectionModel().select(match);
                        table.scrollTo(match);
                        table.requestFocus();
                        pendingLinkedTransactionId=null;
                        org.example.navigation.DeepLinkSupport.pulse(table);
                    }
                }
                finishExplicitRefresh(true);
            },
            failure->{finishExplicitRefresh(false);org.example.util.OperationalUiSupport.showError(table,"Statement transactions could not load",failure);error(failure);}
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
        kpiCredits.setText(money(m.totalCredits()));kpiDebits.setText(money(m.totalDebits()));
        if(kpiReconciled!=null) kpiReconciled.setText(String.format(Locale.ENGLISH,"%.1f%%",m.reconciledPercent()));
        lblProgressText.setText(m.reconciled()+" / "+m.total()+" reconciled");
        reconciliationProgress.setProgress(m.total()==0?0:m.reconciledPercent()/100d);
        lblBatchStatus.setText(m.batchStatus());
        applyBatchStatusStyle(m.batchStatus());
    }

    private void clearMetrics(){
        kpiTotal.setText("0");kpiUnmatched.setText("0");kpiSuggested.setText("0");kpiMatched.setText("0");kpiExpense.setText("0");
        kpiCredits.setText("0.00");kpiDebits.setText("0.00");
        if(kpiReconciled!=null) kpiReconciled.setText("0.0%");
        lblProgressText.setText("0 / 0 reconciled");reconciliationProgress.setProgress(0);lblBatchStatus.setText("No statement selected");applyBatchStatusStyle("");
    }

    @FXML private void applyFilters(){resetPageAndLoad();}
    @FXML private void resetFilters(){
        suppressFilterReload=true;
        try{applyBatchPeriod();cmbStatus.setValue("All Status");if(cmbDirection!=null)cmbDirection.setValue("All");txtSearch.clear();currentPage=0;}
        finally{suppressFilterReload=false;}
        reloadCurrentPage();
    }
    @FXML private void refreshWithFeedback(){explicitRefreshPending=true;if(btnRefresh!=null){btnRefresh.setDisable(true);btnRefresh.setText("Refreshing...");}reloadCurrentPage();}
    private void refresh(){reloadCurrentPage();}
    private void finishExplicitRefresh(boolean success){if(!explicitRefreshPending)return;explicitRefreshPending=false;if(btnRefresh!=null){btnRefresh.setDisable(false);btnRefresh.setText("Refresh");}if(success)org.example.util.ToastManager.info(table,"Refreshed","Bank Statement is up to date.");}
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
        boolean allOpen=count>0 && selected.stream().allMatch(row->Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(up(row.dto.status())));
        boolean allDebit=allOpen && selected.stream().allMatch(row->row.dto.debit()>0);
        if(btnMoveExpense!=null)btnMoveExpense.setDisable(!allDebit);
        if(btnMoveBankEntry!=null)btnMoveBankEntry.setDisable(!allOpen);
        if(chkSelectAll!=null){
            chkSelectAll.setIndeterminate(count>0&&count<table.getItems().size());
            if(!chkSelectAll.isIndeterminate())chkSelectAll.setSelected(count>0&&!table.getItems().isEmpty());
        }
    }
    @FXML private void moveSelectedToExpense(){
        List<Row> rows=selectedRows();
        if(rows.isEmpty()){info("Move to Expense","Select one or more debit transactions.");return;}
        List<Row> invalid=rows.stream().filter(row->row.dto.debit()<=0||!Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(up(row.dto.status()))).toList();
        if(!invalid.isEmpty()){info("Move to Expense",invalid.size()+" selected transaction(s) are not eligible. Only open debit transactions can be moved together.");return;}
        UiTaskExecutor.submitLatest(
                "bank-statement-bulk-expense-lookups",
                () -> new BulkDialogLookups(safeLookup("EXPENSE_CATEGORY"), safeLookup("PAYMENT_MODE")),
                lookups -> showBulkExpenseDialog(rows, lookups.categories(), lookups.paymentModes()),
                this::error
        );
    }
    @FXML private void moveSelectedToBankEntry(){
        List<Row> rows=selectedRows();
        if(rows.isEmpty()){info("Move to Bank Entry","Select one or more open transactions.");return;}
        List<Row> invalid=rows.stream().filter(row->!Set.of("UNMATCHED","SUGGESTED","REVIEW").contains(up(row.dto.status()))).toList();
        if(!invalid.isEmpty()){info("Move to Bank Entry",invalid.size()+" selected transaction(s) are not eligible. Only open transactions can be moved together.");return;}
        UiTaskExecutor.submitLatest(
                "bank-statement-bulk-bank-lookups",
                () -> safeLookup("PAYMENT_MODE"),
                modes -> showBulkBankEntryDialog(rows, modes),
                this::error
        );
    }

    private void showBulkExpenseDialog(List<Row> rows,List<String> categories,List<String> modes){
        if(categories.isEmpty()){info("Move to Expense","No Expense Category is configured in Master Data. Configure a category before using bulk move.");return;}
        Dialog<ButtonType> dialog=new OwnedDialog<>();dialog.setTitle("Bulk Move to Expense");dialog.setHeaderText(rows.size()+" selected transactions • Total ₹ "+money(rows.stream().mapToDouble(r->r.dto.debit()).sum()));
        ComboBox<String> category=new ComboBox<>(FXCollections.observableArrayList(categories));category.setPromptText("Select category *");category.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> mode=new ComboBox<>(FXCollections.observableArrayList(modes));if(!modes.isEmpty())mode.getSelectionModel().selectFirst();mode.setMaxWidth(Double.MAX_VALUE);
        TextField account=new TextField(batchAccountName());TextArea notes=new TextArea();notes.setPromptText("Optional note applied to all selected transactions");notes.setPrefRowCount(2);
        GridPane grid=new GridPane();grid.setHgap(10);grid.setVgap(10);grid.addRow(0,new Label("Expense Category *"),category);grid.addRow(1,new Label("Bank Account"),account);grid.addRow(2,new Label("Payment Mode"),mode);grid.addRow(3,new Label("Common Note"),notes);GridPane.setHgrow(category,Priority.ALWAYS);GridPane.setHgrow(account,Priority.ALWAYS);GridPane.setHgrow(mode,Priority.ALWAYS);grid.setPrefWidth(560);
        dialog.getDialogPane().setContent(grid);ButtonType move=new ButtonType("Move "+rows.size()+" Entries",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,move);
        Node moveButton=dialog.getDialogPane().lookupButton(move);moveButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{if(category.getValue()==null||category.getValue().isBlank()){e.consume();info("Category required","Select one Expense Category. The same category will be applied to every selected transaction.");}});
        dialog.showAndWait().filter(move::equals).ifPresent(x->{
            List<Long> ids=rows.stream().map(r->r.dto.id()).toList();String performedBy=user();
            UiTaskExecutor.submitSerial("bank-statement-bulk-expense",()->api.bulkExpense(new BankStatementApiClient.BulkExpenseRequest(ids,category.getValue(),account.getText(),mode.getValue(),notes.getText(),null,performedBy)),
                    result->{success("Bulk Expense Complete",result.message());refresh();},this::error);
        });
    }

    private void showBulkBankEntryDialog(List<Row> rows,List<String> modes){
        Dialog<ButtonType> dialog=new OwnedDialog<>();dialog.setTitle("Bulk Move to Bank Entry");dialog.setHeaderText(rows.size()+" selected transactions • Total ₹ "+money(rows.stream().mapToDouble(r->bankAmount(r.dto)).sum()));
        TextField account=new TextField(batchAccountName());ComboBox<String> mode=new ComboBox<>(FXCollections.observableArrayList(modes));if(!modes.isEmpty())mode.getSelectionModel().selectFirst();mode.setMaxWidth(Double.MAX_VALUE);
        TextArea notes=new TextArea();notes.setPromptText("Optional note applied to all selected transactions");notes.setPrefRowCount(2);
        GridPane grid=new GridPane();grid.setHgap(10);grid.setVgap(10);grid.addRow(0,new Label("Bank Account"),account);grid.addRow(1,new Label("Payment Mode"),mode);grid.addRow(2,new Label("Common Note"),notes);GridPane.setHgrow(account,Priority.ALWAYS);GridPane.setHgrow(mode,Priority.ALWAYS);grid.setPrefWidth(560);
        dialog.getDialogPane().setContent(grid);ButtonType move=new ButtonType("Move "+rows.size()+" Entries",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,move);
        dialog.showAndWait().filter(move::equals).ifPresent(x->{
            List<Long> ids=rows.stream().map(r->r.dto.id()).toList();String performedBy=user();
            UiTaskExecutor.submitSerial("bank-statement-bulk-bank",()->api.bulkBankEntry(new BankStatementApiClient.BulkBankEntryRequest(ids,account.getText(),mode.getValue(),notes.getText(),performedBy)),
                    result->{success("Bulk Bank Entry Complete",result.message());refresh();},this::error);
        });
    }
    private List<String> safeLookup(String code){try{return lookupService.getValuesByCategoryCode(code);}catch(Exception ignored){return List.of();}}
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
                UiTaskExecutor.submitAction(
                    "bank-statement-bulk-"+action.toLowerCase(Locale.ROOT),
                    () -> {
                        int completed=0,failed=0;String firstFailure="";
                        for(Row row:rows){
                            try{
                                if("REVIEW".equals(action))api.review(row.dto.id(),new BankStatementApiClient.NoteRequest(reason,user(),row.dto.rowVersion()));
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
            () -> new MatchWorkspaceData(api.suggest(row.dto.id()), bankMatchRoundingTolerance()),
            workspace -> {
                List<BankStatementApiClient.CandidateDto> cs=workspace.candidates();
                if(cs.isEmpty()){info("Match Transaction","No eligible Sale, Purchase or Return refund transaction was found. You can move debit transactions to Expense or review later.");refresh();return;}
                var top=cs.getFirst();
                if(Boolean.getBoolean("dse.legacyBankMatchDialog")&&top.confidence()>=75&&Math.abs(top.outstanding()-bankAmount(row.dto))<=.01){
                    Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Suggested Match\n\n"+top+"\n\nWhy suggested: exact amount / useful party / date signals.\n\nConfirm this match?");
                    a.setHeaderText(String.format(Locale.ENGLISH,"High Confidence Match • %.0f%%",top.confidence()));
                    ButtonType find=new ButtonType("Find Another",ButtonBar.ButtonData.OTHER);ButtonType confirm=new ButtonType("Confirm Match",ButtonBar.ButtonData.OK_DONE);a.getButtonTypes().setAll(confirm,find,ButtonType.CANCEL);
                    var r=a.showAndWait();if(r.isPresent()&&r.get()==confirm){confirm(row,List.of(top));return;}if(r.isEmpty()||r.get()==ButtonType.CANCEL)return;
                }
                showCandidateWorkspace(row,cs,workspace.roundingTolerance());
            },
            this::error
        );
    }

    private void showCandidateWorkspace(Row bankRow,List<BankStatementApiClient.CandidateDto> candidates,double tolerance){
        double bankValue=bankAmount(bankRow.dto);
        List<CandidateRow> rows=new ArrayList<>();
        for(int i=0;i<candidates.size();i++){
            var candidate=candidates.get(i);
            boolean autoSelect=i==0&&candidate.confidence()>=75&&Math.abs(candidate.outstanding()-bankValue)<=.01;
            double allocation=autoSelect?bankValue:0d;
            CandidateRow row=new CandidateRow(candidate,allocation,autoSelect);
            rows.add(row);
        }

        TableView<CandidateRow> candidatesTable=new TableView<>(FXCollections.observableArrayList(rows));
        candidatesTable.getProperties().put("erp-keep-selection", true);
        PopupTableWorkspace.prepareTable(candidatesTable,"erp-table-profile-dialog");
        candidatesTable.setEditable(true);
        candidatesTable.setPrefSize(1280,460);
        TableColumn<CandidateRow,Boolean> selected=new TableColumn<>("Select");
        selected.setCellValueFactory(v->v.getValue().selected);
        selected.setCellFactory(CheckBoxTableCell.forTableColumn(selected));
        selected.setMinWidth(45); selected.setPrefWidth(45);

        TableColumn<CandidateRow,Number> score=new TableColumn<>("Score");
        score.setCellValueFactory(v->v.getValue().confidence);
        score.setMinWidth(65); score.setPrefWidth(70);

        TableColumn<CandidateRow,String> type=new TableColumn<>("Type");
        type.setCellValueFactory(v->v.getValue().type);
        type.setMinWidth(110); type.setPrefWidth(125);

        TableColumn<CandidateRow,String> document=new TableColumn<>("Document");
        document.setCellValueFactory(v->v.getValue().document);
        document.setMinWidth(160); document.setPrefWidth(180);

        TableColumn<CandidateRow,String> party=new TableColumn<>("Customer / Supplier");
        party.setCellValueFactory(v->v.getValue().party);
        party.setMinWidth(220); party.setPrefWidth(260);

        TableColumn<CandidateRow,String> date=new TableColumn<>("Date");
        date.setCellValueFactory(v->v.getValue().date);
        date.setMinWidth(100); date.setPrefWidth(110);

        TableColumn<CandidateRow,Number> total=new TableColumn<>("Document Total");
        total.setCellValueFactory(v->v.getValue().total);
        total.setMinWidth(130); total.setPrefWidth(140);

        TableColumn<CandidateRow,Number> paid=new TableColumn<>("Paid / Refunded");
        paid.setCellValueFactory(v->v.getValue().paid);
        paid.setMinWidth(130); paid.setPrefWidth(140);

        TableColumn<CandidateRow,Number> outstanding=new TableColumn<>("Outstanding");
        outstanding.setCellValueFactory(v->v.getValue().outstanding);
        outstanding.setMinWidth(130); outstanding.setPrefWidth(140);

        for(var c:List.of(total,paid,outstanding))c.setCellFactory(x->new TableCell<>(){@Override protected void updateItem(Number n,boolean empty){super.updateItem(n,empty);setText(empty||n==null?null:"₹ "+money(n.doubleValue()));}});

        TableColumn<CandidateRow,Double> allocation=new TableColumn<>("Allocation");
        allocation.setCellValueFactory(v->v.getValue().allocation.asObject());
        allocation.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        allocation.setOnEditCommit(e->{double value=e.getNewValue()==null?0:e.getNewValue();e.getRowValue().allocation.set(value);e.getRowValue().selected.set(value>.0001);});
        allocation.setMinWidth(130); allocation.setPrefWidth(140);

        candidatesTable.getColumns().setAll(selected,score,type,document,party,date,total,paid,outstanding,allocation);
        CheckBox selectAllCandidates=new CheckBox();
        selectAllCandidates.getProperties().put("erp.icon.skip",true);
        selectAllCandidates.setTooltip(new Tooltip("Select all visible match candidates"));
        selectAllCandidates.setOnAction(e->{
            if(!selectAllCandidates.isSelected()){rows.forEach(r->{r.allocation.set(0);r.selected.set(false);});candidatesTable.refresh();return;}
            double left=bankValue;
            for(CandidateRow r:rows){double value=Math.min(r.dto.outstanding(),Math.max(0,left));r.allocation.set(value);r.selected.set(value>.0001);left-=value;if(left<=.01)left=0;}
            candidatesTable.refresh();
        });
        rows.forEach(r->r.selected.addListener((o,a,b)->selectAllCandidates.setSelected(!rows.isEmpty()&&rows.stream().allMatch(x->x.selected.get()))));
        selected.setGraphic(selectAllCandidates);selected.getProperties().put("erp-header-preserve",true);
        IconFactory.applyTableHeaderIcon(score,"status");IconFactory.applyTableHeaderIcon(type,"category");
        IconFactory.applyTableHeaderIcon(document,"document");IconFactory.applyTableHeaderIcon(party,"customer");
        IconFactory.applyTableHeaderIcon(date,"calendar");IconFactory.applyTableHeaderIcon(total,"currency");
        IconFactory.applyTableHeaderIcon(paid,"complete");IconFactory.applyTableHeaderIcon(outstanding,"balance");
        IconFactory.applyTableHeaderIcon(allocation,"currency");

        Label allocatedValue=PopupTableWorkspace.metricValue("0.00","complete");
        Label remainingValue=PopupTableWorkspace.metricValue(money(bankValue),"warning");
        Label roundOffValue=PopupTableWorkspace.metricValue("₹ 0.00","warning");
        Label documentResidualValue=PopupTableWorkspace.metricValue("₹ 0.00","warning");
        Label matchStatusValue=PopupTableWorkspace.metricValue("Select a Transaction","warning");
        Label selectedStatus=PopupTableWorkspace.footerText("0 selected");
        Runnable refreshStatus=()->{
            double allocated=rows.stream().filter(r->r.selected.get()).mapToDouble(r->r.allocation.get()).sum();
            double remainingAmount=bankValue-allocated;
            double roundOff=rows.stream().filter(r->r.selected.get()).mapToDouble(r->roundingFor(r.dto.outstanding(),r.allocation.get(),tolerance)).sum();
            double documentResidual=rows.stream().filter(r->r.selected.get()).mapToDouble(r->{double ro=roundingFor(r.dto.outstanding(),r.allocation.get(),tolerance);return Math.max(0d,Math.round((r.dto.outstanding()-r.allocation.get()-ro)*100d)/100d);}).sum();
            long selectedRows=rows.stream().filter(r->r.selected.get()).count();
            allocatedValue.setText("₹ "+money(allocated));
            remainingValue.setText("₹ "+money(remainingAmount));
            roundOffValue.setText(signedMoney(roundOff));
            documentResidualValue.setText("₹ "+money(documentResidual));
            boolean bankComplete=Math.abs(remainingAmount)<=.01;
            boolean documentsComplete=selectedRows>0&&documentResidual<=.01;
            String state=selectedRows==0?"Select a Transaction":!bankComplete?"Bank Not Fully Allocated":documentsComplete?(Math.abs(roundOff)>.0001?"Full Match + Round-off":"Full Match"):"Document Remains Partial";
            matchStatusValue.setText(state);
            matchStatusValue.getStyleClass().removeAll("erp-popup-metric-success","erp-popup-metric-warning");
            matchStatusValue.getStyleClass().add(bankComplete&&documentsComplete?"erp-popup-metric-success":"erp-popup-metric-warning");
            selectedStatus.setText(selectedRows+" selected  •  Bank allocated ₹ "+money(allocated)+"  •  Round-off "+signedMoney(roundOff)+"  •  Bank remaining ₹ "+money(remainingAmount)+"  •  Document residual ₹ "+money(documentResidual));
        };
        rows.forEach(r->{r.selected.addListener((o,a,b)->{if(Boolean.TRUE.equals(b)&&r.allocation.get()<=.0001){double used=rows.stream().filter(x->x!=r&&x.selected.get()).mapToDouble(x->x.allocation.get()).sum();r.allocation.set(Math.min(r.dto.outstanding(),Math.max(0,bankValue-used)));}else if(Boolean.FALSE.equals(b))r.allocation.set(0);refreshStatus.run();});r.allocation.addListener((o,a,b)->refreshStatus.run());});
        refreshStatus.run();
        HBox metrics=PopupTableWorkspace.metricStrip(
            PopupTableWorkspace.metricCard("Bank Amount","₹ "+money(bankValue),"bank"),
            PopupTableWorkspace.metricCard("Allocated",allocatedValue,"complete"),
            PopupTableWorkspace.metricCard("Round-off",roundOffValue,"warning"),
            PopupTableWorkspace.metricCard("Bank Remaining",remainingValue,"warning"),
            PopupTableWorkspace.metricCard("Document Residual",documentResidualValue,"balance"),
            PopupTableWorkspace.metricCard("Settlement Status",matchStatusValue,"warning")
        );
        Label bank=new Label(safe(bankRow.dto.transactionDate())+"  •  "+safe(bankRow.dto.reference())+"  •  "+safe(bankRow.dto.description()));
        bank.setWrapText(true);bank.getStyleClass().add("bank-dialog-help");
        Label help=new Label("Suggestions stay strict: exact amount + useful party + date. When you manually confirm, a final document residual within the configured ₹"+money(tolerance)+" round-off tolerance is settled explicitly without changing the actual bank amount. The total actual allocation must still equal the bank amount.");
        help.setWrapText(true);help.getStyleClass().add("bank-dialog-help");
        VBox tableArea=new VBox(7,bank,help,candidatesTable);VBox.setVgrow(candidatesTable,Priority.ALWAYS);
        VBox content=PopupTableWorkspace.content(metrics,tableArea,selectedStatus);
        Dialog<ButtonType> dialog=new OwnedDialog<>();dialog.setTitle("Match Transaction");dialog.setHeaderText("Review possible matches and allocate the complete bank transaction.");dialog.getDialogPane().setContent(content);PopupTableWorkspace.prepareDialog(dialog,1320);
        ButtonType refreshType=new ButtonType("Refresh Suggestions",ButtonBar.ButtonData.OTHER);ButtonType confirm=new ButtonType("Confirm Match",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(refreshType,ButtonType.CANCEL,confirm);
        Button refreshButton=(Button)dialog.getDialogPane().lookupButton(refreshType);refreshButton.setGraphic(IconFactory.compactIcon("refresh",15));refreshButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();dialog.close();javafx.application.Platform.runLater(()->match(bankRow));});
        dialog.showAndWait().filter(confirm::equals).ifPresent(x->{
            List<BankStatementApiClient.AllocationRequest> allocations=new ArrayList<>();
            double allocated=0;
            for(CandidateRow row:rows){
                if(!row.selected.get())continue;
                double value=row.allocation.get();
                double roundOff=roundingFor(row.dto.outstanding(),value,tolerance);
                if(value<=0||value-row.dto.outstanding()>tolerance+.0001||(value-row.dto.outstanding()>.01&&Math.abs(roundOff)<=.0001)){info("Allocation needs attention","Each selected allocation must be greater than zero. Any final difference from outstanding must be within the configured ₹"+money(tolerance)+" round-off tolerance.");return;}
                allocations.add(new BankStatementApiClient.AllocationRequest(row.dto.type(),row.dto.id(),value));allocated+=value;
            }
            if(allocations.isEmpty()){info("Match Transaction","Select at least one eligible Sale, Purchase or Return refund transaction.");return;}
            if(Math.abs(allocated-bankValue)>.01){info("Allocation needs attention","Allocated amount must equal the bank amount. Remaining: "+money(bankValue-allocated));return;}
            double documentResidual=rows.stream().filter(r->r.selected.get()).mapToDouble(r->{double ro=roundingFor(r.dto.outstanding(),r.allocation.get(),tolerance);return Math.max(0d,Math.round((r.dto.outstanding()-r.allocation.get()-ro)*100d)/100d);}).sum();
            if(documentResidual>.01){Alert partial=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Bank allocation: ₹ "+money(allocated)+"\nDocument residual after this match: ₹ "+money(documentResidual)+"\n\nThis will leave one or more documents PARTIAL. Continue only if this bank transaction is genuinely a partial settlement.",ButtonType.CANCEL,new ButtonType("Confirm Partial Settlement",ButtonBar.ButtonData.OK_DONE));partial.setHeaderText("Partial Document Settlement");Optional<ButtonType> choice=partial.showAndWait();if(choice.isEmpty()||choice.get().getButtonData()!=ButtonBar.ButtonData.OK_DONE)return;}
            confirmAllocations(bankRow,allocations);
        });
    }

    private void confirmAllocations(Row row,List<BankStatementApiClient.AllocationRequest> allocations){
        String performedBy=user();
        UiTaskExecutor.submitSerial(
            "bank-statement-match-"+row.dto.id(),
            () -> api.match(row.dto.id(),new BankStatementApiClient.MatchRequest(performedBy,allocations)),
            result -> {success("Match Successful",result.message()+"\n\nBank Entry and payment/refund allocations were updated together.");refresh();},
            this::error
        );
    }
    private void confirm(Row row,List<BankStatementApiClient.CandidateDto> selected){
        double remaining=bankAmount(row.dto);List<BankStatementApiClient.AllocationRequest> alloc=new ArrayList<>();
        for(var c:selected){double suggested=Math.min(c.outstanding(),remaining);TextInputDialog d=new OwnedTextInputDialog(String.format(Locale.ROOT,"%.2f",suggested));d.setTitle("Allocate Payment");d.setHeaderText(c.documentNo()+" • "+c.partyName()+" • Outstanding "+money(c.outstanding()));d.setContentText("Allocation amount:");Optional<String>v=d.showAndWait();if(v.isEmpty())return;double amount=Double.parseDouble(v.get().replace(",","").trim());alloc.add(new BankStatementApiClient.AllocationRequest(c.type(),c.id(),amount));remaining-=amount;}
        String performedBy=user();
        UiTaskExecutor.submitSerial("bank-statement-match-"+row.dto.id(),
            () -> api.match(row.dto.id(),new BankStatementApiClient.MatchRequest(performedBy,alloc)),
            r -> {success("Match Successful",r.message()+"\n\nBank Entry created and payment/refund allocation updated.");refresh();},
            this::error);
    }

    private void moveToExpense(Row row){
        BankExpenseController.requestExpensePrefill(new BankExpenseController.ExpensePrefill(row.dto.id(),row.dto.transactionDate(),row.dto.debit(),row.dto.reference(),row.dto.description(),batchAccountName(),"Bank Statement"));
        DashboardController.navigateFromChild("Expense Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.EXPENSE);
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
    public static final class LinkedTargetItem {
        final String role;
        final String documentNo;
        final String type;
        final String typeLabel;
        final double amount;
        final double roundOff;
        final String iconName;
        final String chipClass;
        final Runnable onNavigate;

        LinkedTargetItem(String role, String documentNo, String type, String typeLabel, double amount, double roundOff, String iconName, String chipClass, Runnable onNavigate) {
            this.role = role;
            this.documentNo = documentNo;
            this.type = type;
            this.typeLabel = typeLabel;
            this.amount = amount;
            this.roundOff = roundOff;
            this.iconName = iconName;
            this.chipClass = chipClass;
            this.onNavigate = onNavigate;
        }
    }

    private List<LinkedTargetItem> collectLinkedTargets(Row row) {
        if (row == null || row.dto == null) return List.of();
        var t = row.dto;
        List<LinkedTargetItem> list = new ArrayList<>();
        List<BankStatementApiClient.AllocationDto> linked = t.linkedAllocations() == null ? List.of() : t.linkedAllocations();
        for (var a : linked) {
            String type = up(a.targetType());
            if ("EXPENSE".equals(type) || "BANK_ENTRY".equals(type)) continue;
            String docNo = safe(a.documentNo()).isBlank() ? (type + " #" + a.targetId()) : a.documentNo();
            String role = "ERP Document";
            String typeLabel = formatTypeLabel(type);
            String chipClass = chipClassFor(type);
            double amt = a.allocatedAmount();
            double roundOff = a.roundingAdjustment();
            list.add(new LinkedTargetItem(role, docNo, type, typeLabel, amt, roundOff, "link", chipClass, () -> openAllocation(a, "Bank Statement #" + t.id())));
        }
        if (list.isEmpty() && t.linkedTargetType() != null && !t.linkedTargetType().isBlank()) {
            String type = up(t.linkedTargetType());
            if (!"EXPENSE".equals(type) && !"BANK_ENTRY".equals(type) && !"MULTIPLE".equals(type)) {
                String docNo = safe(t.linkedDocumentNo()).isBlank() ? (type + " #" + t.linkedTargetId()) : t.linkedDocumentNo();
                String role = "ERP Document";
                String typeLabel = formatTypeLabel(type);
                String chipClass = chipClassFor(type);
                double amt = bankAmount(t);
                list.add(new LinkedTargetItem(role, docNo, type, typeLabel, amt, 0.0, "link", chipClass, () -> openLinkedTarget(type, t.linkedTargetId(), docNo, "Bank Statement #" + t.id(), row)));
            }
        }
        if (t.financeEntryId() != null || "BANK_ENTRY".equals(up(t.linkedTargetType()))) {
            Integer fid = t.financeEntryId() != null ? t.financeEntryId() : t.linkedTargetId();
            String vNo = !safe(t.financeVoucherNo()).isBlank() ? t.financeVoucherNo() : ("Bank Entry #" + fid);
            boolean isCredit = t.credit() > 0;
            String typeLabel = isCredit ? "Bank Deposit" : "Bank Withdrawal";
            String chipClass = isCredit ? "finance-chip-green" : "finance-chip-red";
            double amt = bankAmount(t);
            list.add(new LinkedTargetItem("Bank Voucher", vNo, "BANK_ENTRY", typeLabel, amt, 0.0, "bank", chipClass, () -> openFinance(row, BankExpenseController.Mode.BANK)));
        } else if ("EXPENSE".equals(up(t.status())) || "EXPENSE".equals(up(t.linkedTargetType()))) {
            Integer fid = t.financeEntryId() != null ? t.financeEntryId() : t.linkedTargetId();
            String vNo = !safe(t.financeVoucherNo()).isBlank() ? t.financeVoucherNo() : ("Expense #" + fid);
            double amt = t.debit() > 0 ? t.debit() : bankAmount(t);
            list.add(new LinkedTargetItem("Expense Voucher", vNo, "EXPENSE", "Bank Expense", amt, 0.0, "payment", "finance-chip-orange", () -> openFinance(row, BankExpenseController.Mode.EXPENSE)));
        }
        return list;
    }

    private static String chipClassFor(String type) {
        String t = up(type);
        if ("SALE".equals(t)) return "finance-chip-green";
        if ("PURCHASE".equals(t)) return "finance-chip-orange";
        if ("PURCHASE_RECON".equals(t)) return "finance-chip-blue";
        if ("SALES_RETURN".equals(t) || "PURCHASE_RETURN".equals(t)) return "finance-chip-red";
        if ("EXPENSE".equals(t)) return "finance-chip-orange";
        if ("BANK_ENTRY".equals(t)) return "finance-chip-teal";
        return "finance-chip-purple";
    }

    private static String formatTypeLabel(String type) {
        String t = up(type);
        if ("SALE".equals(t)) return "Sales Invoice";
        if ("PURCHASE".equals(t)) return "Purchase Bill";
        if ("PURCHASE_RECON".equals(t)) return "Purchase Recon";
        if ("SALES_RETURN".equals(t)) return "Sales Return";
        if ("PURCHASE_RETURN".equals(t)) return "Purchase Return";
        if ("EXPENSE".equals(t)) return "Expense Entry";
        if ("BANK_ENTRY".equals(t)) return "Bank Entry";
        return t.replace('_', ' ');
    }

    private void openLinked(Row row){
        List<LinkedTargetItem> targets = collectLinkedTargets(row);
        if(targets.isEmpty()){
            var t = row.dto;
            String type = up(t.linkedTargetType()); Integer id = t.linkedTargetId(); String no = safe(t.linkedDocumentNo());
            if(type.isBlank() && t.suggestedMatchType() != null){ type = up(t.suggestedMatchType()); id = t.suggestedMatchId(); }
            if(!type.isBlank()){
                openLinkedTarget(type, id, no, "Bank Statement #" + t.id(), row);
                return;
            }
            new OwnedAlert(Alert.AlertType.INFORMATION, "No linked ERP or Finance document is available for this transaction.").showAndWait();
            return;
        }
        if(targets.size() == 1){
            targets.getFirst().onNavigate.run();
            return;
        }
        showLinkedTargetsDialog(row, targets);
    }

    private void showLinkedTargetsDialog(Row row, List<LinkedTargetItem> targets){
        Dialog<ButtonType> dialog = new OwnedDialog<>(table);
        dialog.setTitle("Linked Transactions");
        dialog.setHeaderText("View and navigate to all ERP & Finance records linked to this bank entry.");
        TableView<LinkedTargetItem> linkedTable = new TableView<>(FXCollections.observableArrayList(targets));
        PopupTableWorkspace.prepareTable(linkedTable, "erp-table-profile-dialog");
        linkedTable.setPrefHeight(Math.max(180, Math.min(420, 75 + targets.size() * 48)));

        TableColumn<LinkedTargetItem, String> colRole = new TableColumn<>("Category");
        colRole.setMinWidth(140);
        colRole.setPrefWidth(150);
        colRole.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().role));
        colRole.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(null); setGraphic(null);
                if (empty || s == null) return;
                LinkedTargetItem item = getTableView().getItems().get(getIndex());
                Label chip = new Label(s);
                chip.getStyleClass().addAll("finance-chip", "ERP Document".equals(s) ? "finance-chip-purple" : "finance-chip-teal");
                chip.setGraphic(IconFactory.compactIcon(item.iconName, 13));
                chip.setGraphicTextGap(6);
                setGraphic(chip);
            }
        });

        TableColumn<LinkedTargetItem, String> colDoc = new TableColumn<>("Document / Voucher No");
        colDoc.setMinWidth(190);
        colDoc.setPrefWidth(210);
        colDoc.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().documentNo));
        colDoc.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(null); setGraphic(null);
                if (empty || s == null) return;
                Label lbl = new Label(s);
                lbl.getStyleClass().add("erp-primary-label");
                lbl.setStyle("-fx-font-weight: 700;");
                lbl.setTooltip(new Tooltip(s));
                setGraphic(lbl);
            }
        });

        TableColumn<LinkedTargetItem, String> colType = new TableColumn<>("Transaction Type");
        colType.setMinWidth(160);
        colType.setPrefWidth(170);
        colType.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().typeLabel));
        colType.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(null); setGraphic(null);
                if (empty || s == null) return;
                LinkedTargetItem item = getTableView().getItems().get(getIndex());
                Label chip = new Label(s);
                chip.getStyleClass().addAll("finance-chip", item.chipClass);
                String semantic = "SALE".equals(item.type) ? "credit" : "PURCHASE".equals(item.type) ? "debit" : "BANK_ENTRY".equals(item.type) ? "bank" : "category";
                chip.setGraphic(IconFactory.compactIcon(semantic, 13));
                chip.setGraphicTextGap(5);
                setGraphic(chip);
            }
        });

        TableColumn<LinkedTargetItem, Number> colAmount = new TableColumn<>("Allocated Amount");
        colAmount.setMinWidth(140);
        colAmount.setPrefWidth(150);
        colAmount.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().amount));
        colAmount.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().remove("erp-quantity-positive");
                setText(empty || value == null ? null : "₹ " + money(value.doubleValue()));
                if (!empty) getStyleClass().add("erp-quantity-positive");
            }
        });

        TableColumn<LinkedTargetItem, Number> colRoundOff = new TableColumn<>("Round-off");
        colRoundOff.setMinWidth(110);
        colRoundOff.setPrefWidth(120);
        colRoundOff.setCellValueFactory(v -> new SimpleDoubleProperty(v.getValue().roundOff));
        colRoundOff.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : (Math.abs(value.doubleValue()) < 0.0001 ? "—" : signedMoney(value.doubleValue())));
            }
        });

        TableColumn<LinkedTargetItem, Void> colAction = new TableColumn<>("Action");
        colAction.setMinWidth(140);
        colAction.setPrefWidth(150);
        colAction.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                var item = getTableView().getItems().get(getIndex());
                Button view = new Button(linkActionLabel(item.type));
                view.getStyleClass().addAll("approved-button", "approved-primary-button");
                view.setGraphic(IconFactory.compactIcon("view", 14));
                view.setOnAction(e -> {
                    dialog.close();
                    item.onNavigate.run();
                });
                setGraphic(view);
            }
        });

        IconFactory.applyTableHeaderIcon(colRole, "category");
        IconFactory.applyTableHeaderIcon(colDoc, "document");
        IconFactory.applyTableHeaderIcon(colType, "reference");
        IconFactory.applyTableHeaderIcon(colAmount, "currency");
        IconFactory.applyTableHeaderIcon(colRoundOff, "warning");
        IconFactory.applyTableHeaderIcon(colAction, "actions");
        linkedTable.getColumns().setAll(colRole, colDoc, colType, colAmount, colRoundOff, colAction);

        double totalAllocated = targets.stream().mapToDouble(x -> x.amount).sum();
        double roundOffTotal = targets.stream().mapToDouble(x -> x.roundOff).sum();
        double difference = bankAmount(row.dto) - totalAllocated;
        String bankReference = safe(row.dto.reference()).isBlank() ? "Bank #" + row.dto.id() : row.dto.reference();
        HBox metrics = PopupTableWorkspace.metricStrip(
            PopupTableWorkspace.metricCard("Bank Ref", bankReference, "bank"),
            PopupTableWorkspace.metricCard("Linked Count", String.valueOf(targets.size()), "link"),
            PopupTableWorkspace.metricCard("Allocated Total", "₹ " + money(totalAllocated), "currency"),
            PopupTableWorkspace.metricCard("Round-off", signedMoney(roundOffTotal), "warning"),
            PopupTableWorkspace.metricCard("Difference", "₹ " + money(difference), Math.abs(difference) <= .01 ? "complete" : "warning")
        );
        Label footer = PopupTableWorkspace.footerText(targets.size() + " linked records found • Click View on any record to navigate directly to its workspace.");
        VBox content = PopupTableWorkspace.content(metrics, linkedTable, footer);
        dialog.getDialogPane().setContent(content);
        PopupTableWorkspace.prepareDialog(dialog, 980);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private String linkActionLabel(String type){
        String t=up(type);
        if("SALE".equals(t)) return "View Sale";
        if("PURCHASE".equals(t)) return "View Purchase";
        if("PURCHASE_RECON".equals(t)) return "View Recon";
        if("SALES_RETURN".equals(t)) return "View Sales Return";
        if("PURCHASE_RETURN".equals(t)) return "View Purchase Return";
        if("BANK_ENTRY".equals(t)) return "View Bank Entry";
        if("EXPENSE".equals(t)) return "View Expense";
        return "View";
    }

    private void openAllocation(BankStatementApiClient.AllocationDto allocation,String source){if(allocation==null)return;String type=up(allocation.targetType());Integer id=("EXPENSE".equals(type)||"BANK_ENTRY".equals(type))&&allocation.financeEntryId()!=null?allocation.financeEntryId():allocation.targetId();openLinkedTarget(type,id,safe(allocation.documentNo()),source,null);}
    private void openLinkedTarget(String type,Integer id,String documentNo,String source,Row row){
        if("EXPENSE".equals(type)){Integer financeId=row!=null&&row.dto.financeEntryId()!=null?row.dto.financeEntryId():id;BankExpenseController.requestLinkedEntry(BankExpenseController.Mode.EXPENSE,financeId);DashboardController.navigateFromChild("Expense Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.EXPENSE);return;}
        if("BANK_ENTRY".equals(type)){Integer financeId=row!=null&&row.dto.financeEntryId()!=null?row.dto.financeEntryId():id;BankExpenseController.requestLinkedEntry(BankExpenseController.Mode.BANK,financeId);DashboardController.navigateFromChild("Bank Entry","/fxml/pages/BankExpense.fxml",BankExpenseController.Mode.BANK);return;}
        if("SALE".equals(type)){
            if(documentNo.isBlank()){org.example.util.AppDialogService.warning(table,"Linked record unavailable","Sale document not found","The linked Sale no longer has a usable invoice number.");return;}
            LinkedRecordContext.open("SALE", id, documentNo, "VIEW", "Bank Statement");
            DashboardController.navigateFromChild("Sales Register","/fxml/pages/SalesList.fxml",null);
            return;
        }
        if("PURCHASE".equals(type)){
            if(documentNo.isBlank()){org.example.util.AppDialogService.warning(table,"Linked record unavailable","Purchase document not found","The linked Purchase no longer has a usable invoice number.");return;}
            LinkedRecordContext.open("PURCHASE", id, documentNo, "VIEW", "Bank Statement");
            DashboardController.navigateFromChild("Purchase Register","/fxml/pages/PurchaseList.fxml",null);
            return;
        }
        if("PURCHASE_RECON".equals(type)){
            if(id==null){org.example.util.AppDialogService.warning(table,"Linked record unavailable","Purchase Recon not found","The linked Purchase Recon no longer has a usable record id.");return;}
            PurchaseReconScreenContext.select(id);
            DashboardController.navigateFromChild("Purchase Recon","/fxml/pages/PurchaseRecon.fxml",null);
            return;
        }
        if("SALES_RETURN".equals(type)){
            if(documentNo.isBlank()){new OwnedAlert(Alert.AlertType.WARNING,"The linked Sales Return record no longer has a document number.").showAndWait();return;}
            LinkedRecordContext.open("SALES_RETURN", id, documentNo, "VIEW", "Bank Statement");
            DashboardController.navigateFromChild("Sales Returns","/fxml/pages/SalesReturns.fxml",null);
            return;
        }
        if("PURCHASE_RETURN".equals(type)){
            if(documentNo.isBlank()){new OwnedAlert(Alert.AlertType.WARNING,"The linked Purchase Return record no longer has a document number.").showAndWait();return;}
            LinkedRecordContext.open("PURCHASE_RETURN", id, documentNo, "VIEW", "Bank Statement");
            DashboardController.navigateFromChild("Purchase Returns","/fxml/pages/PurchaseReturns.fxml",null);
            return;
        }
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
            UiTaskExecutor.submitAction("bank-statement-note-"+t.id(),
                () -> api.updateNote(t.id(),new BankStatementApiClient.NoteRequest(value,performedBy,t.rowVersion())),
                ignored -> {org.example.util.ToastManager.success(table,"Note saved","Bank transaction note was saved successfully.");refresh();},this::error);
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
            UiTaskExecutor.submitAction("bank-statement-review-"+row.dto.id(),
                () -> api.review(row.dto.id(),new BankStatementApiClient.NoteRequest(reason,performedBy,row.dto.rowVersion())),
                ignored -> {org.example.util.ToastManager.success(table,"Marked for review","Bank transaction was marked for review.");refresh();},this::error);
        });
    }
    private void ignore(Row row){
        requiredReason("Ignore Bank Transaction","Enter the reason this statement line should be excluded from reconciliation. The reason is retained in the audit history.").ifPresent(reason->{
            Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"This transaction will remain visible in the imported statement and audit trail, but will be excluded from reconciliation totals.\n\nReason: "+reason);
            a.setHeaderText("Confirm ignored transaction");
            a.showAndWait().filter(x->x==ButtonType.OK).ifPresent(x->{
                String performedBy=user();
                UiTaskExecutor.submitAction("bank-statement-ignore-"+row.dto.id(),
                    () -> api.ignore(row.dto.id(),new BankStatementApiClient.IgnoreRequest(reason,performedBy)),
                    ignored -> {org.example.util.ToastManager.success(table,"Transaction ignored","Bank transaction was excluded from reconciliation and retained in audit history.");refresh();},this::error);
            });
        });
    }
    private Optional<String> requiredReason(String title,String prompt){
        OwnedTextInputDialog dialog=new OwnedTextInputDialog("");dialog.setTitle(title);dialog.setHeaderText(prompt);dialog.setContentText("Required reason:");
        Optional<String> value=dialog.showAndWait().map(String::trim).filter(s->!s.isBlank());
        if(value.isEmpty())info(title,"A reason is required so the decision can be understood from the audit history.");
        return value;
    }
    private void reverse(Row row){Alert a=new OwnedAlert(Alert.AlertType.CONFIRMATION,"Reverse this reconciliation? Linked payment/finance records will be safely reversed and the bank transaction will return to UNMATCHED.");a.showAndWait().filter(x->x==ButtonType.OK).ifPresent(x->{String performedBy=user();UiTaskExecutor.submitSerial("bank-statement-reverse-"+row.dto.id(),()->api.reverse(row.dto.id(),performedBy),ignored->{org.example.util.ToastManager.success(table,"Reconciliation reversed","Bank transaction returned to UNMATCHED successfully.");refresh();},this::error);});}
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

    private static double bankMatchRoundingTolerance(){try{double value=Double.parseDouble(ConfigManager.get("payment.bankMatchRoundingTolerance","1.00").trim());return Math.max(0d,Math.min(5d,Math.round(value*100d)/100d));}catch(Exception ignored){return 1d;}}
    private static double roundingFor(double outstanding,double amount,double tolerance){double difference=Math.round((outstanding-amount)*100d)/100d;if(Math.abs(difference)<=.0001)return 0d;return tolerance>0&&Math.abs(difference)<=tolerance+.0001?difference:0d;}
    private static String signedMoney(double value){if(Math.abs(value)<=.0001)return "₹ 0.00";return (value>=0?"+":"-")+"₹ "+money(Math.abs(value));}
    private static String user(){var u=SessionService.current();return u==null?"User":safe(u.getFullName());}
    private static double bankAmount(BankStatementApiClient.TransactionDto t){return t.credit()>0?t.credit():t.debit();}
    private static String money(double v){return String.format(Locale.ENGLISH,"%,.2f",v);} private static String safe(String s){return s==null?"":s;}
    private static String up(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT);} private static LocalDate parseDate(String s){return BusinessClock.parseDate(s);}
    private void info(String h,String t){Alert a=new OwnedAlert(Alert.AlertType.INFORMATION,t);a.setHeaderText(h);a.showAndWait();}
    private void success(String h,String t){org.example.util.ToastManager.success(table,h,t);}
    private void error(Throwable e){Alert a=new OwnedAlert(Alert.AlertType.ERROR,e.getMessage()==null?e.toString():e.getMessage());a.setHeaderText("Bank Statement operation failed");a.showAndWait();}

    private record BulkResult(int completed,int failed,String firstFailure) { }
    private record BulkDialogLookups(List<String> categories,List<String> paymentModes) { }
    private record MatchWorkspaceData(List<BankStatementApiClient.CandidateDto> candidates,double roundingTolerance) { }

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
        CandidateRow(BankStatementApiClient.CandidateDto dto,double allocation,boolean selectedInitially){
            this.dto=dto;confidence.set(dto.confidence());type.set(safe(dto.type()));document.set(safe(dto.documentNo()));party.set(safe(dto.partyName()));date.set(safe(dto.documentDate()));
            total.set(dto.totalAmount());paid.set(dto.paidAmount());outstanding.set(dto.outstanding());this.allocation.set(allocation);selected.set(selectedInitially);
        }
    }
    public static final class AutoMatchItem {
        public final BooleanProperty selected = new SimpleBooleanProperty(true);
        public final BankStatementApiClient.TransactionDto tx;
        public final BankStatementApiClient.CandidateDto candidate;

        public AutoMatchItem(BankStatementApiClient.TransactionDto tx, BankStatementApiClient.CandidateDto candidate) {
            this.tx = tx;
            this.candidate = candidate;
        }

        public double bankAmount() {
            return tx.credit() > 0 ? tx.credit() : tx.debit();
        }
    }

    @FXML private void openAutoMatchDialog() {
        var batch = cmbBatch.getValue();
        if (batch == null) {
            info("Select Statement", "Please select a statement first to find auto-match suggestions.");
            return;
        }

        List<Row> userSelected = table.getItems().stream().filter(r -> r.selected.get()).toList();
        List<Long> targetTxIds = !userSelected.isEmpty() ? userSelected.stream().map(r -> r.dto.id()).toList() : null;

        LiveOperationDialog.execute(
                table,
                "Scanning Suggestions",
                "reconcile",
                "Analyzing bank transactions for suggestions…",
                reporter -> {
                    List<BankStatementApiClient.TransactionDto> allTx;
                    if (targetTxIds != null) {
                        allTx = targetTxIds.stream().map(id -> api.transaction(id.longValue())).toList();
                    } else {
                        allTx = api.transactions(batch.id().longValue());
                    }
                    List<AutoMatchItem> items = new ArrayList<>();
                    List<BankStatementApiClient.TransactionDto> scanTargets = allTx.stream().filter(tx -> {
                        String st = tx.status() == null ? "" : tx.status().toUpperCase(Locale.ROOT);
                        return !Set.of("MATCHED", "EXPENSE", "IGNORED").contains(st);
                    }).toList();

                    for (int i = 0; i < scanTargets.size(); i++) {
                        if (reporter.isCancelled()) break;
                        var tx = scanTargets.get(i);
                        reporter.stage(i + 1, scanTargets.size(), "Scanning: " + safe(tx.reference()));
                        reporter.progress((double) (i + 1) / Math.max(1, scanTargets.size()));
                        try {
                            List<BankStatementApiClient.CandidateDto> candidates = api.candidates(tx.id().longValue());
                            if (candidates != null && !candidates.isEmpty()) {
                                var top = candidates.getFirst();
                                double bankAmt = bankAmount(tx);
                                double diff = Math.abs(top.outstanding() - bankAmt);
                                if (top.confidence() >= 70.0 || diff <= 1.00 || (candidates.size() == 1 && top.confidence() >= 40.0)) {
                                    items.add(new AutoMatchItem(tx, top));
                                }
                            }
                        } catch (Exception ignored) { }
                    }
                    items.sort((a, b) -> Double.compare(b.candidate.confidence(), a.candidate.confidence()));
                    return items;
                },
                items -> {
                    if (items.isEmpty()) {
                        info("No Auto-Match Suggestions", "No eligible auto-match suggestions found for this statement batch.\nTry adjusting reconciliation filters or review unallocated bank entries.");
                        return;
                    }
                    showBulkAutoMatchDialog(items);
                },
                this::error
        );
    }

    private void showBulkAutoMatchDialog(List<AutoMatchItem> items) {
        Dialog<ButtonType> dialog = new OwnedDialog<>(table);
        dialog.setTitle("Auto Match Suggestions");
        dialog.setHeaderText("Review and approve high-confidence auto-matched bank transactions (" + items.size() + " suggestions)");
        AppDialogRenderer.configureWorkspace(dialog, "bank",
                "Auto Match Suggestions",
                "Review and approve high-confidence auto-matched bank transactions (" + items.size() + " suggestions)",
                "", "");

        ObservableList<AutoMatchItem> allItems = FXCollections.observableArrayList(items);
        TableView<AutoMatchItem> autoTable = new TableView<>(allItems);
        PopupTableWorkspace.prepareTable(autoTable, "erp-table-profile-dialog");
        autoTable.getProperties().put("erp-keep-selection", true);

        Label lblTotalSuggestions = PopupTableWorkspace.metricValue(String.valueOf(items.size()), "link");
        Label lblSelectedToMatch = PopupTableWorkspace.metricValue(String.valueOf(items.size()), "complete");
        double initialAmount = items.stream().mapToDouble(AutoMatchItem::bankAmount).sum();
        Label lblTotalAmount = PopupTableWorkspace.metricValue("₹ " + money(initialAmount), "currency");
        double avgConf = items.stream().mapToDouble(i -> i.candidate.confidence()).average().orElse(0.0);
        Label lblAvgConfidence = PopupTableWorkspace.metricValue(String.format(Locale.ENGLISH, "%.0f%%", avgConf), "status");

        HBox metrics = PopupTableWorkspace.metricStrip(
                PopupTableWorkspace.metricCard("Suggestions", lblTotalSuggestions, "link"),
                PopupTableWorkspace.metricCard("Selected", lblSelectedToMatch, "complete"),
                PopupTableWorkspace.metricCard("Total Amount", lblTotalAmount, "currency"),
                PopupTableWorkspace.metricCard("Avg Confidence", lblAvgConfidence, "status")
        );

        TextField txtFilter = new TextField();
        txtFilter.setPromptText("Filter suggestions by party, reference, document...");
        HBox.setHgrow(txtFilter, Priority.ALWAYS);

        CheckBox chkSelectAllModal = new CheckBox("Select All");
        chkSelectAllModal.setSelected(true);
        chkSelectAllModal.setOnAction(e -> {
            boolean sel = chkSelectAllModal.isSelected();
            for (AutoMatchItem it : autoTable.getItems()) {
                it.selected.set(sel);
            }
        });

        HBox filterBar = new HBox(12, chkSelectAllModal, txtFilter);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        TableColumn<AutoMatchItem, Boolean> colSel = new TableColumn<>("Select");
        colSel.setCellValueFactory(v -> v.getValue().selected);
        colSel.setCellFactory(CheckBoxTableCell.forTableColumn(colSel));
        colSel.setEditable(true);
        autoTable.setEditable(true);
        IconFactory.applyTableHeaderIcon(colSel, "select");

        TableColumn<AutoMatchItem, String> colDate = new TableColumn<>("Bank Date");
        colDate.setCellValueFactory(v -> new SimpleStringProperty(BusinessClock.formatDate(v.getValue().tx.transactionDate())));
        colDate.setCellFactory(c -> SemanticTableCells.dateString());
        IconFactory.applyTableHeaderIcon(colDate, "calendar");

        TableColumn<AutoMatchItem, String> colRef = new TableColumn<>("Reference / Cheque");
        colRef.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().tx.reference())));
        IconFactory.applyTableHeaderIcon(colRef, "reference");

        TableColumn<AutoMatchItem, String> colDesc = new TableColumn<>("Description / Narration");
        colDesc.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().tx.description())));
        IconFactory.applyTableHeaderIcon(colDesc, "notes");

        TableColumn<AutoMatchItem, String> colAmount = new TableColumn<>("Bank Amount");
        colAmount.setCellValueFactory(v -> {
            boolean isCredit = v.getValue().tx.credit() > 0;
            return new SimpleStringProperty((isCredit ? "+ ₹ " : "- ₹ ") + money(v.getValue().bankAmount()));
        });
        colAmount.setCellFactory(c -> new TableCell<AutoMatchItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("erp-table-value-colour-green", "erp-table-value-colour-orange");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item);
                if (getTableRow() != null && getTableRow().getItem() != null) {
                    AutoMatchItem rowItem = getTableRow().getItem();
                    if (rowItem.tx.credit() > 0) {
                        getStyleClass().add("erp-table-value-colour-green");
                    } else {
                        getStyleClass().add("erp-table-value-colour-orange");
                    }
                }
                setAlignment(Pos.CENTER_LEFT);
            }
        });
        IconFactory.applyTableHeaderIcon(colAmount, "payment");

        TableColumn<AutoMatchItem, String> colDoc = new TableColumn<>("Matched Document");
        colDoc.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().candidate.documentNo() + " (" + v.getValue().candidate.type() + ")"));
        colDoc.setCellFactory(tc -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();
            {
                link.getStyleClass().add("table-link");
                link.setOnAction(e -> {
                    AutoMatchItem it = getTableView().getItems().get(getIndex());
                    if (it != null && it.candidate != null) {
                        openLinkedTarget(it.candidate.type(), it.candidate.id(), it.candidate.documentNo(), "Auto Match", null);
                    }
                });
            }
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                } else {
                    link.setText(val);
                    setGraphic(link);
                }
            }
        });
        IconFactory.applyTableHeaderIcon(colDoc, "document");

        TableColumn<AutoMatchItem, String> colParty = new TableColumn<>("Party / Account");
        colParty.setCellValueFactory(v -> new SimpleStringProperty(safe(v.getValue().candidate.partyName())));
        IconFactory.applyTableHeaderIcon(colParty, "customer");

        TableColumn<AutoMatchItem, String> colDocDate = new TableColumn<>("Doc Date");
        colDocDate.setCellValueFactory(v -> new SimpleStringProperty(BusinessClock.formatDate(v.getValue().candidate.documentDate())));
        colDocDate.setCellFactory(c -> SemanticTableCells.dateString());
        IconFactory.applyTableHeaderIcon(colDocDate, "calendar");

        TableColumn<AutoMatchItem, String> colOuts = new TableColumn<>("Outstanding");
        colOuts.setCellValueFactory(v -> new SimpleStringProperty("₹ " + money(v.getValue().candidate.outstanding())));
        colOuts.setCellFactory(c -> SemanticTableCells.moneyString("blue"));
        IconFactory.applyTableHeaderIcon(colOuts, "balance");

        TableColumn<AutoMatchItem, String> colConf = new TableColumn<>("Confidence");
        colConf.setCellValueFactory(v -> new SimpleStringProperty(String.format(Locale.ENGLISH, "%.0f%%", v.getValue().candidate.confidence())));
        colConf.setCellFactory(c -> SemanticTableCells.status("complete"));
        IconFactory.applyTableHeaderIcon(colConf, "status");

        autoTable.getColumns().setAll(colSel, colDate, colRef, colDesc, colAmount, colDoc, colParty, colDocDate, colOuts, colConf);
        DynamicTableLayoutManager.install(autoTable);

        Runnable updateModalMetrics = () -> {
            long selCount = allItems.stream().filter(it -> it.selected.get()).count();
            double selAmount = allItems.stream().filter(it -> it.selected.get()).mapToDouble(AutoMatchItem::bankAmount).sum();
            lblSelectedToMatch.setText(String.valueOf(selCount));
            lblTotalAmount.setText("₹ " + money(selAmount));
        };
        allItems.forEach(it -> it.selected.addListener((obs, oldV, newV) -> updateModalMetrics.run()));

        txtFilter.textProperty().addListener((obs, oldV, q) -> {
            if (q == null || q.isBlank()) {
                autoTable.setItems(allItems);
            } else {
                String lq = q.toLowerCase(Locale.ROOT);
                autoTable.setItems(allItems.filtered(it ->
                        (it.candidate.partyName() != null && it.candidate.partyName().toLowerCase(Locale.ROOT).contains(lq)) ||
                        (it.candidate.documentNo() != null && it.candidate.documentNo().toLowerCase(Locale.ROOT).contains(lq)) ||
                        (it.tx.reference() != null && it.tx.reference().toLowerCase(Locale.ROOT).contains(lq)) ||
                        (it.tx.description() != null && it.tx.description().toLowerCase(Locale.ROOT).contains(lq))
                ));
            }
        });

        Label footer = PopupTableWorkspace.footerText("Confirming matches will link bank transactions and create corresponding Bank Entries and payment allocations.");
        VBox tableArea = new VBox(8, filterBar, autoTable);
        VBox.setVgrow(autoTable, Priority.ALWAYS);
        VBox.setVgrow(tableArea, Priority.ALWAYS);
        VBox content = PopupTableWorkspace.content(metrics, tableArea, footer);

        dialog.getDialogPane().setContent(content);
        PopupTableWorkspace.prepareDialog(dialog, 1200);

        ButtonType refreshType = new ButtonType("Refresh Suggestions", ButtonBar.ButtonData.OTHER);
        ButtonType confirmType = new ButtonType("Confirm Match", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(refreshType, ButtonType.CANCEL, confirmType);

        Button btnRefreshModal = (Button) dialog.getDialogPane().lookupButton(refreshType);
        if (btnRefreshModal != null) {
            btnRefreshModal.setGraphic(IconFactory.compactIcon("refresh", 15));
            btnRefreshModal.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                e.consume();
                dialog.close();
                javafx.application.Platform.runLater(this::openAutoMatchDialog);
            });
        }

        Button btnConfirmModal = (Button) dialog.getDialogPane().lookupButton(confirmType);
        if (btnConfirmModal != null) {
            btnConfirmModal.getStyleClass().addAll("approved-button", "approved-primary-button");
            btnConfirmModal.setGraphic(IconFactory.compactIcon("complete", 15));
        }

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == confirmType) {
            List<AutoMatchItem> confirmed = allItems.stream().filter(it -> it.selected.get()).toList();
            if (confirmed.isEmpty()) {
                info("Auto Match", "No transactions were selected to match.");
                return;
            }
            executeBulkAutoMatch(confirmed);
        }
    }

    private void executeBulkAutoMatch(List<AutoMatchItem> items) {
        String performedBy = user();
        List<String> failures = new ArrayList<>();
        LiveOperationDialog.execute(
                table,
                "Auto Match Progress",
                "process",
                "Matching " + items.size() + " bank transactions…",
                reporter -> {
                    int succeeded = 0;
                    int failed = 0;
                    for (int i = 0; i < items.size(); i++) {
                        if (reporter.isCancelled()) break;
                        var it = items.get(i);
                        String doc = it.candidate.documentNo();
                        String party = it.candidate.partyName();
                        reporter.stage(i + 1, items.size(), "Matching: " + doc + " (" + party + ")");
                        reporter.progress((double) (i + 1) / items.size());
                        try {
                            // Allocate full bank amount so server validation (allocatedSum == bankTxAmount) succeeds
                            // and server applies round-off tolerance logic to settle document within tolerance!
                            double amount = it.bankAmount();
                            var alloc = new BankStatementApiClient.AllocationRequest(it.candidate.type(), it.candidate.id(), amount);
                            var matchReq = new BankStatementApiClient.MatchRequest(performedBy, List.of(alloc));
                            var res = api.match(it.tx.id().longValue(), matchReq);
                            if (res.success()) {
                                succeeded++;
                            } else {
                                failed++;
                                failures.add(doc + " (" + party + "): " + res.message());
                            }
                        } catch (Exception ex) {
                            failed++;
                            failures.add(doc + " (" + party + "): " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                        }
                    }
                    return new int[]{succeeded, failed};
                },
                counts -> {
                    int succ = counts[0];
                    int fail = counts[1];
                    if (fail == 0) {
                        success("Auto Match Completed", "Successfully matched " + succ + " bank transaction(s).");
                    } else {
                        String msg = "Successfully matched: " + succ + "\nFailed to match: " + fail + "\n\nFailure Details:\n" + String.join("\n", failures);
                        Alert alert = new OwnedAlert(Alert.AlertType.WARNING, msg);
                        alert.setHeaderText("Auto Match Completed with Issues");
                        alert.showAndWait();
                    }
                    refresh();
                },
                this::error
        );
    }

    @Override public void onScreenHidden(){UiTaskExecutor.cancelPrefix("bank-statement-");}

}
