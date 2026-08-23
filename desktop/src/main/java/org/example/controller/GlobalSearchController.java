package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
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

/** Full-width, permission-aware Global Search workspace with exact-record navigation. */
public final class GlobalSearchController implements ScreenLifecycle {
    private static final String ALL = "All Results";

    @FXML private TextField txtGlobalSearch;
    @FXML private ListView<String> listModules;
    @FXML private Label lblResultSummary;
    @FXML private ProgressIndicator searchProgress;
    @FXML private VBox resultGroups;

    private final Map<String,String> moduleByDisplay = new LinkedHashMap<>();
    private List<SearchResult> lastResults = List.of();
    private long requestSerial;

    @FXML public void initialize() {
        configureModuleRail(Map.of());
        listModules.getSelectionModel().selectedItemProperty().addListener((o,a,b)->render());
        listModules.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(empty || item == null ? null : IconFactory.compactIcon(icon(moduleByDisplay.getOrDefault(item, item)), 15));
            }
        });
        String query = GlobalSearchContext.consume();
        if (!query.isBlank()) { txtGlobalSearch.setText(query); Platform.runLater(this::search); }
        else Platform.runLater(txtGlobalSearch::requestFocus);
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
        lblResultSummary.setText("Searching all permitted modules…");
        CompletableFuture.supplyAsync(() -> new GlobalSearchService().search(q)).whenComplete((results,error) -> Platform.runLater(() -> {
            if (serial != requestSerial) return;
            setBusy(false);
            if (error != null) {
                lastResults = List.of(); configureModuleRail(Map.of());
                lblResultSummary.setText("Search could not be completed: " + safe(error.getMessage())); render(); return;
            }
            lastResults = results == null ? List.of() : results;
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
        moduleByDisplay.clear();
        String allDisplay = ALL + (counts.isEmpty() ? "" : "  •  " + counts.values().stream().mapToLong(Long::longValue).sum());
        moduleByDisplay.put(allDisplay, ALL);
        for (Map.Entry<String,Long> e : counts.entrySet()) {
            String display = e.getKey() + "  •  " + e.getValue();
            moduleByDisplay.put(display, e.getKey());
        }
        listModules.getItems().setAll(moduleByDisplay.keySet());
        if (!listModules.getItems().isEmpty()) listModules.getSelectionModel().selectFirst();
    }

    private void render() {
        resultGroups.getChildren().clear();
        String display = listModules.getSelectionModel().getSelectedItem();
        String filter = moduleByDisplay.getOrDefault(display, ALL);
        List<SearchResult> visible = lastResults.stream().filter(r -> ALL.equals(filter) || filter.equals(r.module())).toList();

        if (lastResults.isEmpty()) {
            lblResultSummary.setText("Search invoices, orders, parties, items, returns, payments, bank transactions, reminders, communications and master data.");
            resultGroups.getChildren().add(emptyCard("Type a value above to search the entire ERP."));
            return;
        }
        long modules = lastResults.stream().map(SearchResult::module).distinct().count();
        lblResultSummary.setText(visible.size() + " visible • " + lastResults.size() + " total result(s) across " + modules + " module(s)");
        if (visible.isEmpty()) { resultGroups.getChildren().add(emptyCard("No matching records in this module.")); return; }
        Map<String,List<SearchResult>> grouped = visible.stream().collect(Collectors.groupingBy(SearchResult::module, LinkedHashMap::new, Collectors.toList()));
        grouped.forEach((module,rows) -> resultGroups.getChildren().add(group(module,rows)));
    }

    private VBox emptyCard(String text) {
        Label empty = new Label(text); empty.setWrapText(true); empty.getStyleClass().add("dse-search-empty");
        VBox card = new VBox(empty); card.getStyleClass().addAll("dse-search-group-card","dse-search-empty-card"); return card;
    }

    private VBox group(String module, List<SearchResult> rows) {
        Label title = new Label(module); title.getStyleClass().add("dse-search-group-title"); title.setGraphic(IconFactory.icon(icon(module),18));
        Label count = new Label(rows.size() + " match" + (rows.size()==1?"":"es")); count.getStyleClass().add("dse-search-count");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(8,title,spacer,count); head.setAlignment(Pos.CENTER_LEFT); head.getStyleClass().add("dse-search-group-header");
        VBox card = new VBox(0,head); card.getStyleClass().add("dse-search-group-card");
        for (SearchResult r : rows) card.getChildren().add(row(r));
        return card;
    }

    private HBox row(SearchResult r) {
        Label ref = new Label(safe(r.reference())); ref.getStyleClass().add("dse-search-ref");
        Label desc = new Label(safe(r.description())); desc.getStyleClass().add("dse-search-desc");
        Label detail = new Label(safe(r.detail())); detail.getStyleClass().add("dse-search-detail"); detail.setWrapText(true);
        VBox text = new VBox(2,ref,desc,detail); HBox.setHgrow(text,Priority.ALWAYS);
        Button view = new Button("View"); view.getStyleClass().addAll("approved-button","approved-primary-button","dse-search-view");
        view.setGraphic(IconFactory.compactIcon("view",14));
        view.setOnAction(e -> DeepLinkRouter.open(r.targetFxml(),r.moduleKey(),r.recordId(),r.reference(),"Global Search"));
        HBox row = new HBox(12,IconFactory.icon(icon(r.module()),20),text,view); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("dse-search-result-row");
        row.setOnMouseClicked(e -> { if (e.getClickCount()==2) view.fire(); });
        return row;
    }

    private void setBusy(boolean busy) { searchProgress.setVisible(busy); searchProgress.setManaged(busy); }
    private String icon(String module) {
        String v = safe(module).toLowerCase(Locale.ROOT);
        if(v.contains("sale"))return "sale"; if(v.contains("purchase"))return "purchase"; if(v.contains("item")||v.contains("inventory"))return "item";
        if(v.contains("customer"))return "customer"; if(v.contains("supplier"))return "supplier"; if(v.contains("payment"))return "payment";
        if(v.contains("return"))return "return"; if(v.contains("quotation"))return "quotation"; if(v.contains("bank")||v.contains("expense"))return "bank";
        if(v.contains("reminder"))return "reminder"; if(v.contains("communication"))return "email"; if(v.contains("user"))return "users"; return "master";
    }
    private String safe(String s){return s==null?"":s;}
}
