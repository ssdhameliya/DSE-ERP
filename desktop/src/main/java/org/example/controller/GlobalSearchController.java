package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.navigation.DeepLinkRouter;
import org.example.navigation.ScreenLifecycle;
import org.example.service.GlobalSearchService;
import org.example.service.GlobalSearchService.SearchResult;
import org.example.util.IconFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Premium permission-aware Global Search workspace with exact-record navigation. */
public final class GlobalSearchController implements ScreenLifecycle {
    private static final String ALL = "All Results";

    @FXML private StackPane searchHeaderIcon;
    @FXML private TextField txtGlobalSearch;
    @FXML private Button btnSearch, btnClear;
    @FXML private ListView<ModuleOption> listModules;
    @FXML private Label lblResultSummary, lblVisibleCount, lblTotalCount, lblModuleCount;
    @FXML private HBox resultSummaryChips;
    @FXML private ProgressIndicator searchProgress;
    @FXML private VBox resultGroups;

    private List<SearchResult> lastResults = List.of();
    private long requestSerial;

    private record ModuleOption(String label, String module, long count) {
        @Override public String toString() { return label; }
    }

    @FXML public void initialize() {
        installIcons();
        configureModuleRail(Map.of());
        listModules.getSelectionModel().selectedItemProperty().addListener((o,a,b)->render());
        listModules.setCellFactory(v -> new ModuleCell());
        String query = GlobalSearchContext.consume();
        if (!query.isBlank()) { txtGlobalSearch.setText(query); Platform.runLater(this::search); }
        else Platform.runLater(txtGlobalSearch::requestFocus);
    }

    private void installIcons() {
        if (searchHeaderIcon != null) searchHeaderIcon.getChildren().setAll(IconFactory.compactIcon("search", 24));
        if (btnSearch != null) { btnSearch.setGraphic(IconFactory.compactIcon("search", 15)); btnSearch.getProperties().put("erp-icon-preserve", true); }
        if (btnClear != null) { btnClear.setGraphic(IconFactory.compactIcon("delete", 14)); btnClear.getProperties().put("erp-icon-preserve", true); }
    }

    @Override public void onScreenShown(boolean reused) {
        String incoming = GlobalSearchContext.consume();
        if (!incoming.isBlank()) { txtGlobalSearch.setText(incoming); search(); }
    }

    @FXML private void search() {
        String q = safe(txtGlobalSearch.getText()).trim();
        if (q.isBlank()) { lastResults = List.of(); configureModuleRail(Map.of()); render(); txtGlobalSearch.requestFocus(); return; }
        long serial = ++requestSerial;
        setBusy(true);
        showSummaryMessage("Searching all permitted modules…");
        CompletableFuture.supplyAsync(() -> new GlobalSearchService().search(q)).whenComplete((results,error) -> Platform.runLater(() -> {
            if (serial != requestSerial) return;
            setBusy(false);
            if (error != null) {
                lastResults = List.of(); configureModuleRail(Map.of());
                resultGroups.getChildren().clear();
                showSummaryMessage("Search could not be completed: " + safe(error.getMessage()));
                resultGroups.getChildren().add(emptyCard("Please check the server connection or try the search again."));
                return;
            }
            lastResults = results == null ? List.of() : List.copyOf(results);
            Map<String,Long> counts = lastResults.stream().collect(Collectors.groupingBy(SearchResult::module, LinkedHashMap::new, Collectors.counting()));
            configureModuleRail(counts);
            render();
        }));
    }

    @FXML private void clearSearch() {
        ++requestSerial; setBusy(false); txtGlobalSearch.clear(); lastResults = List.of();
        configureModuleRail(Map.of()); render(); txtGlobalSearch.requestFocus();
    }

    private void configureModuleRail(Map<String,Long> counts) {
        String selected = Optional.ofNullable(listModules.getSelectionModel().getSelectedItem()).map(ModuleOption::module).orElse(ALL);
        List<ModuleOption> options = new ArrayList<>();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        options.add(new ModuleOption(ALL, ALL, total));
        counts.forEach((module,count) -> options.add(new ModuleOption(module,module,count)));
        listModules.getItems().setAll(options);
        ModuleOption restore = options.stream().filter(x -> x.module().equals(selected)).findFirst().orElse(options.getFirst());
        listModules.getSelectionModel().select(restore);
    }

    private void render() {
        resultGroups.getChildren().clear();
        ModuleOption selected = listModules.getSelectionModel().getSelectedItem();
        String filter = selected == null ? ALL : selected.module();
        List<SearchResult> visible = lastResults.stream().filter(r -> ALL.equals(filter) || filter.equals(r.module())).toList();

        if (lastResults.isEmpty()) {
            showSummaryMessage("Search invoices, orders, parties, items, returns, payments, bank transactions, reminders, communications and master data.");
            resultGroups.getChildren().add(emptyCard("Type a value above to search the entire ERP."));
            return;
        }
        long modules = lastResults.stream().map(SearchResult::module).distinct().count();
        showSummaryCounts(visible.size(), lastResults.size(), modules);
        if (visible.isEmpty()) { resultGroups.getChildren().add(emptyCard("No matching records in this module.")); return; }
        Map<String,List<SearchResult>> grouped = visible.stream().collect(Collectors.groupingBy(SearchResult::module, LinkedHashMap::new, Collectors.toList()));
        grouped.forEach((module,rows) -> resultGroups.getChildren().add(group(module,rows)));
    }

    private VBox emptyCard(String text) {
        Label empty = new Label(text); empty.setWrapText(true); empty.getStyleClass().add("dse-global-search-v3-empty");
        VBox card = new VBox(empty); card.getStyleClass().addAll("dse-global-search-v3-group-card","dse-global-search-v3-empty-card"); return card;
    }

    private VBox group(String module, List<SearchResult> rows) {
        String semantic = icon(module);
        Label title = new Label(module); title.getStyleClass().addAll("dse-global-search-v3-group-title", "dse-global-search-v3-group-title-"+slug(module)); title.setGraphic(IconFactory.compactIcon(semantic,18));
        Label count = new Label(rows.size() + " match" + (rows.size()==1?"":"es")); count.getStyleClass().addAll("dse-global-search-v3-count", "dse-global-search-v3-count-"+slug(module));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(8,title,spacer,count); head.setAlignment(Pos.CENTER_LEFT); head.getStyleClass().addAll("dse-global-search-v3-group-header", "dse-global-search-v3-group-header-"+slug(module));
        VBox card = new VBox(0,head); card.getStyleClass().addAll("dse-global-search-v3-group-card", "dse-global-search-v3-group-"+slug(module));
        for (SearchResult r : rows) card.getChildren().add(row(r));
        return card;
    }

    private HBox row(SearchResult r) {
        Node glyph = IconFactory.compactIcon(icon(r.module()), 18);
        StackPane iconWell = new StackPane(glyph); iconWell.getStyleClass().addAll("dse-global-search-v3-result-icon","dse-global-search-v3-result-icon-"+slug(r.module()));
        iconWell.setMinSize(36,36); iconWell.setPrefSize(36,36); iconWell.setMaxSize(36,36);

        Label ref = new Label(safe(r.reference())); ref.getStyleClass().addAll("dse-global-search-v3-ref", "dse-global-search-v3-ref-"+slug(r.module())); ref.setMinWidth(150); ref.setPrefWidth(185); ref.setMaxWidth(220);
        Label desc = new Label(safe(r.description())); desc.setWrapText(true); desc.setMaxWidth(Double.MAX_VALUE); desc.getStyleClass().addAll("dse-global-search-v3-desc", "dse-global-search-v3-desc-"+slug(r.module())); HBox.setHgrow(desc, Priority.ALWAYS);
        HBox primary = new HBox(10,ref,separator(),desc); primary.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(primary,Priority.ALWAYS);

        FlowPane details = new FlowPane(8,6); details.setAlignment(Pos.CENTER_LEFT); details.setPrefWrapLength(620); details.setMaxWidth(Double.MAX_VALUE); details.getStyleClass().add("dse-global-search-v3-detail-fields");
        List<String> tokens=detailTokens(r.detail());
        for (int i=0;i<tokens.size();i++) {
            if(i>0){Label dot=new Label("•");dot.getStyleClass().add("dse-global-search-v3-token-separator");details.getChildren().add(dot);}
            String token=tokens.get(i);
            Label label = new Label(token); label.setWrapText(false);
            label.getStyleClass().add("dse-global-search-v3-token");
            String statusClass = tokenClass(token);
            if (!statusClass.isBlank()) label.getStyleClass().add(statusClass);
            details.getChildren().add(label);
        }
        VBox content=new VBox(7,primary);content.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(content,Priority.ALWAYS);
        if(!tokens.isEmpty())content.getChildren().add(details);

        Button view = new Button("View"); view.setMinWidth(82); view.getStyleClass().add("dse-global-search-v3-view");
        view.setGraphic(IconFactory.compactIcon("view",13)); view.getProperties().put("erp-icon-preserve",true);
        view.setOnAction(e -> DeepLinkRouter.open(r.targetFxml(),r.moduleKey(),r.recordId(),r.reference(),"Global Search"));
        view.setTooltip(new Tooltip("Open the exact " + r.module() + " record"));

        HBox row = new HBox(12,iconWell,content,view); row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dse-global-search-v3-result-row");
        row.setOnMouseClicked(e -> { if (e.getClickCount()==2) view.fire(); });
        return row;
    }

    private Region separator() { Region r = new Region(); r.getStyleClass().add("dse-global-search-v3-separator"); r.setMinWidth(1); r.setPrefWidth(1); r.setMaxWidth(1); r.setMinHeight(20); return r; }

    private List<String> detailTokens(String detail) {
        String text = safe(detail).trim(); if (text.isBlank()) return List.of();
        List<String> tokens = Arrays.stream(text.split("\\s{2,}")) .map(String::trim).filter(x->!x.isBlank()).limit(4).toList();
        return tokens.isEmpty() ? List.of(text) : tokens;
    }

    private String tokenClass(String value) {
        String raw = safe(value).trim();
        String x = raw.toUpperCase(Locale.ROOT);
        if (x.contains("STOCK:") || x.startsWith("QTY ") || x.contains(" QTY ")) return "dse-global-search-v3-pill-stock";
        if (x.contains("CONFIRMED") || x.contains("COMPLETED") || x.contains("PAID") || x.contains("MATCHED") || x.contains("ACTIVE")) return "dse-global-search-v3-pill-success";
        if (x.contains("PENDING") || x.contains("DUE") || x.contains("REVIEW") || x.contains("SUGGESTED")) return "dse-global-search-v3-pill-warning";
        if (x.contains("REJECT") || x.contains("CANCEL") || x.contains("OVERDUE") || x.contains("UNMATCHED") || x.contains("DEBIT ₹")) return "dse-global-search-v3-pill-danger";
        if (x.startsWith("₹") || x.contains("CREDIT ₹")) return "dse-global-search-v3-pill-money";
        if (raw.matches("\\d{4}-\\d{2}-\\d{2}.*") || raw.matches("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}.*")) return "dse-global-search-v3-token-date";
        if (raw.contains("@")) return "dse-global-search-v3-token-email";
        if (raw.matches(".*\\d{8,}.*")) return "dse-global-search-v3-token-phone";
        return "dse-global-search-v3-token-accent";
    }

    private void showSummaryMessage(String message) {
        if (resultSummaryChips != null) { resultSummaryChips.setVisible(false); resultSummaryChips.setManaged(false); }
        if (lblResultSummary != null) { lblResultSummary.setText(message); lblResultSummary.setVisible(true); lblResultSummary.setManaged(true); }
    }

    private void showSummaryCounts(long visible, long total, long modules) {
        if (lblVisibleCount != null) lblVisibleCount.setText(visible + " visible");
        if (lblTotalCount != null) lblTotalCount.setText(total + " total result" + (total == 1 ? "" : "s"));
        if (lblModuleCount != null) lblModuleCount.setText(modules + " module" + (modules == 1 ? "" : "s"));
        if (resultSummaryChips != null) { resultSummaryChips.setVisible(true); resultSummaryChips.setManaged(true); }
        if (lblResultSummary != null) { lblResultSummary.setText(""); lblResultSummary.setVisible(false); lblResultSummary.setManaged(false); }
    }

    private void setBusy(boolean busy) { searchProgress.setVisible(busy); searchProgress.setManaged(busy); if(btnSearch!=null)btnSearch.setDisable(busy); }
    private String icon(String module) {
        String v = safe(module).toLowerCase(Locale.ROOT);
        if(v.contains("sale"))return "sale"; if(v.contains("purchase"))return "purchase"; if(v.contains("item")||v.contains("inventory"))return "item";
        if(v.contains("customer"))return "customer"; if(v.contains("supplier"))return "supplier"; if(v.contains("payment"))return "payment";
        if(v.contains("return"))return "return"; if(v.contains("quotation"))return "quotation"; if(v.contains("bank")||v.contains("expense"))return "bank";
        if(v.contains("reminder"))return "reminder"; if(v.contains("communication"))return "email"; if(v.contains("user"))return "users"; return "master";
    }
    private String slug(String value){String s=safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");return s.isBlank()?"generic":s;}
    private String safe(String s){return s==null?"":s;}

    private final class ModuleCell extends ListCell<ModuleOption> {
        @Override protected void updateItem(ModuleOption item, boolean empty) {
            super.updateItem(item, empty); setText(null); setGraphic(null);
            if (empty || item == null) return;
            String moduleSlug = slug(item.module());
            StackPane iconNode = new StackPane(IconFactory.compactIcon(icon(item.module()),18));
            iconNode.setMinSize(30,30); iconNode.setPrefSize(30,30); iconNode.setMaxSize(30,30);
            iconNode.getStyleClass().addAll("dse-global-search-v3-module-icon","dse-global-search-v3-module-icon-"+moduleSlug);
            Label name = new Label(item.label()); name.getStyleClass().addAll("dse-global-search-v3-module-name","dse-global-search-v3-module-name-"+moduleSlug); HBox.setHgrow(name,Priority.ALWAYS);
            Label count = new Label(String.valueOf(item.count())); count.getStyleClass().addAll("dse-global-search-v3-module-count","dse-global-search-v3-module-count-"+moduleSlug);
            HBox box = new HBox(9,iconNode,name,count); box.setAlignment(Pos.CENTER_LEFT); box.getStyleClass().addAll("dse-global-search-v3-module-cell","dse-global-search-v3-module-cell-"+moduleSlug);
            setGraphic(box);
        }
    }
}
