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

/** Full-width grouped Global Search workspace. */
public final class GlobalSearchController implements ScreenLifecycle {
    @FXML private TextField txtGlobalSearch;
    @FXML private ComboBox<String> cmbModule;
    @FXML private Label lblResultSummary;
    @FXML private ProgressIndicator searchProgress;
    @FXML private VBox resultGroups;
    private List<SearchResult> lastResults=List.of();
    private long requestSerial;

    @FXML public void initialize(){
        cmbModule.getItems().setAll("All Modules"); cmbModule.setValue("All Modules");
        cmbModule.valueProperty().addListener((o,a,b)->render());
        String query=GlobalSearchContext.consume();
        if(!query.isBlank()){txtGlobalSearch.setText(query);Platform.runLater(this::search);}
        else Platform.runLater(txtGlobalSearch::requestFocus);
    }

    @Override public void onScreenShown(boolean reused){
        String incoming=GlobalSearchContext.consume();
        if(!incoming.isBlank()){txtGlobalSearch.setText(incoming);search();}
    }

    @FXML private void search(){
        String q=txtGlobalSearch.getText()==null?"":txtGlobalSearch.getText().trim();
        if(q.isBlank()){lastResults=List.of();render();txtGlobalSearch.requestFocus();return;}
        long serial=++requestSerial; searchProgress.setVisible(true); searchProgress.setManaged(true); lblResultSummary.setText("Searching all permitted modules…");
        CompletableFuture.supplyAsync(()->new GlobalSearchService().search(q)).whenComplete((results,error)->Platform.runLater(()->{
            if(serial!=requestSerial)return; searchProgress.setVisible(false);searchProgress.setManaged(false);
            if(error!=null){lastResults=List.of();lblResultSummary.setText("Search could not be completed: "+safe(error.getMessage()));render();return;}
            lastResults=results==null?List.of():results;
            LinkedHashSet<String> modules=lastResults.stream().map(SearchResult::module).collect(Collectors.toCollection(LinkedHashSet::new));
            String current=cmbModule.getValue(); cmbModule.getItems().setAll("All Modules");cmbModule.getItems().addAll(modules);
            cmbModule.setValue(current!=null&&cmbModule.getItems().contains(current)?current:"All Modules"); render();
        }));
    }

    @FXML private void clearSearch(){txtGlobalSearch.clear();lastResults=List.of();cmbModule.getItems().setAll("All Modules");cmbModule.setValue("All Modules");render();txtGlobalSearch.requestFocus();}

    private void render(){
        resultGroups.getChildren().clear();
        String filter=cmbModule.getValue()==null?"All Modules":cmbModule.getValue();
        List<SearchResult> visible=lastResults.stream().filter(r->"All Modules".equals(filter)||filter.equals(r.module())).toList();
        lblResultSummary.setText(lastResults.isEmpty()?"Search invoices, orders, parties, items, returns, payments, bank transactions, reminders, communications and master data.":visible.size()+" visible result(s) • "+lastResults.size()+" across all permitted modules");
        if(visible.isEmpty()){
            Label empty=new Label(lastResults.isEmpty()?"Type a value above to search the entire ERP.":"No results in this module.");empty.getStyleClass().add("dse-search-empty");resultGroups.getChildren().add(empty);return;
        }
        Map<String,List<SearchResult>> grouped=visible.stream().collect(Collectors.groupingBy(SearchResult::module,LinkedHashMap::new,Collectors.toList()));
        grouped.forEach((module,rows)->resultGroups.getChildren().add(group(module,rows)));
    }

    private VBox group(String module,List<SearchResult> rows){
        Label title=new Label(module);title.getStyleClass().add("dse-search-group-title");title.setGraphic(IconFactory.icon(icon(module),18));
        Label count=new Label(Integer.toString(rows.size()));count.getStyleClass().add("dse-search-count");
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);HBox head=new HBox(8,title,spacer,count);head.setAlignment(Pos.CENTER_LEFT);head.getStyleClass().add("dse-search-group-header");
        VBox card=new VBox(0,head);card.getStyleClass().add("dse-search-group-card");
        for(SearchResult r:rows)card.getChildren().add(row(r)); return card;
    }
    private HBox row(SearchResult r){
        Label ref=new Label(safe(r.reference()));ref.getStyleClass().add("dse-search-ref");
        Label desc=new Label(safe(r.description()));desc.getStyleClass().add("dse-search-desc");
        Label detail=new Label(safe(r.detail()));detail.getStyleClass().add("dse-search-detail");detail.setWrapText(true);
        VBox text=new VBox(2,ref,desc,detail);HBox.setHgrow(text,Priority.ALWAYS);
        Button view=new Button("View");view.getStyleClass().addAll("approved-button","approved-primary-button","dse-search-view");view.setGraphic(IconFactory.compactIcon("view",14));
        view.setOnAction(e->DeepLinkRouter.open(r.targetFxml(),r.moduleKey(),r.recordId(),r.reference(),"Global Search"));
        HBox row=new HBox(12,IconFactory.icon(icon(r.module()),20),text,view);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("dse-search-result-row");
        row.setOnMouseClicked(e->{if(e.getClickCount()==2)view.fire();}); return row;
    }
    private String icon(String module){String v=safe(module).toLowerCase(Locale.ROOT);if(v.contains("sale"))return "sale";if(v.contains("purchase"))return "purchase";if(v.contains("item")||v.contains("inventory"))return "item";if(v.contains("customer"))return "customer";if(v.contains("supplier"))return "supplier";if(v.contains("payment"))return "payment";if(v.contains("return"))return "return";if(v.contains("quotation"))return "quotation";if(v.contains("bank")||v.contains("expense"))return "bank";if(v.contains("reminder"))return "reminder";if(v.contains("communication"))return "email";if(v.contains("user"))return "users";return "master";}
    private String safe(String s){return s==null?"":s;}
}
