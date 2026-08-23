package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.navigation.DeepLinkRouter;
import org.example.navigation.ScreenLifecycle;
import org.example.service.NotificationService;
import org.example.service.NotificationService.NotificationItem;
import org.example.util.BusinessClock;
import org.example.util.IconFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Full Notification Center with category rail, actionable details and exact-record navigation. */
public final class NotificationCenterController implements ScreenLifecycle {
    private static final String ALL = "All Notifications";
    private static final List<String> BASE_FILTERS = List.of(ALL,"Unread","Action Needed","Approval","Sales","Purchases","Quotations","Returns","Payments","Inventory","Reminders","Communication","Security","System");

    @FXML private TextField txtNotificationSearch;
    @FXML private ListView<String> listNotificationCategories;
    @FXML private ListView<NotificationItem> listNotifications;
    @FXML private Label lblNotificationCount,lblAllCount,lblUnreadCount,lblActionCount,lblDetailTitle,lblDetailMessage,lblDetailMeta,lblDetailReference;
    @FXML private Button btnOpenRecord,btnToggleRead,btnDeleteNotification;
    private List<NotificationItem> all = List.of();

    @FXML public void initialize() {
        listNotificationCategories.getItems().setAll(BASE_FILTERS);
        listNotificationCategories.getSelectionModel().selectFirst();
        listNotificationCategories.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item,empty); setText(empty?null:item);
                setGraphic(empty||item==null?null:IconFactory.compactIcon(categorySemantic(item),15));
            }
        });
        txtNotificationSearch.textProperty().addListener((o,a,b)->applyFilter());
        listNotificationCategories.getSelectionModel().selectedItemProperty().addListener((o,a,b)->applyFilter());
        listNotifications.setCellFactory(v -> new Cell());
        listNotifications.getSelectionModel().selectedItemProperty().addListener((o,a,b)->showDetail(b));
        listNotifications.setOnMouseClicked(e -> { if(e.getClickCount()==2) openRecord(); });
        refresh();
    }

    @Override public void onScreenShown(boolean reused){ refresh(); }

    @FXML private void refresh() {
        lblNotificationCount.setText("Loading…");
        CompletableFuture.supplyAsync(() -> NotificationService.findRecent(500)).whenComplete((items,error) -> Platform.runLater(() -> {
            all = error==null && items!=null ? items : List.of();
            updateSummary(); applyFilter();
        }));
    }

    @FXML private void markAllRead(){ NotificationService.markAllRead(); refresh(); }
    @FXML private void toggleRead(){ NotificationItem item=selected(); if(item==null)return; if(item.read())NotificationService.markUnread(item.id());else NotificationService.markRead(item.id()); refresh(); }
    @FXML private void deleteSelected(){ NotificationItem item=selected(); if(item==null)return; NotificationService.delete(item.id()); refresh(); }

    @FXML private void openRecord() {
        NotificationItem item = selected(); if(item==null)return;
        NotificationService.markRead(item.id());
        if(item.targetFxml()==null || item.targetFxml().isBlank()) {
            lblDetailMeta.setText("This notification is informational and has no linked application record."); return;
        }
        String key = safe(item.moduleKey()).isBlank()
                ? DeepLinkRouter.inferModuleKey(item.targetFxml(),item.category(),item.referenceNo())
                : item.moduleKey();
        DeepLinkRouter.open(item.targetFxml(),key,item.recordId(),item.referenceNo(),"Notification #"+item.id());
    }

    private void updateSummary() {
        long unread=all.stream().filter(n->!n.read()).count();
        long actionable=all.stream().filter(this::actionable).count();
        lblAllCount.setText(String.valueOf(all.size())); lblUnreadCount.setText(String.valueOf(unread)); lblActionCount.setText(String.valueOf(actionable));
        Set<String> available=new LinkedHashSet<>(BASE_FILTERS);
        for(NotificationItem n:all){String d=displayCategory(n.category());if(!d.isBlank())available.add(d);}
        String selected=listNotificationCategories.getSelectionModel().getSelectedItem();
        listNotificationCategories.getItems().setAll(available);
        if(selected!=null&&available.contains(selected))listNotificationCategories.getSelectionModel().select(selected);else listNotificationCategories.getSelectionModel().selectFirst();
    }

    private void applyFilter() {
        String q=safe(txtNotificationSearch.getText()).trim().toLowerCase(Locale.ROOT);
        String mode=Optional.ofNullable(listNotificationCategories.getSelectionModel().getSelectedItem()).orElse(ALL);
        List<NotificationItem> visible=all.stream().filter(n->matchesMode(n,mode)).filter(n->{
            if(q.isBlank())return true;
            String hay=(safe(n.title())+" "+safe(n.message())+" "+safe(n.referenceNo())+" "+safe(n.category())+" "+safe(n.moduleKey())).toLowerCase(Locale.ROOT);
            return hay.contains(q);
        }).toList();
        NotificationItem selected=listNotifications.getSelectionModel().getSelectedItem();
        listNotifications.getItems().setAll(visible);
        lblNotificationCount.setText(visible.size()+" shown • "+all.stream().filter(n->!n.read()).count()+" unread");
        if(selected!=null&&visible.contains(selected))listNotifications.getSelectionModel().select(selected);
        else if(!visible.isEmpty())listNotifications.getSelectionModel().selectFirst();
        else showDetail(null);
    }

    private boolean matchesMode(NotificationItem n,String mode){
        if(ALL.equals(mode))return true;
        if("Unread".equals(mode))return !n.read();
        if("Action Needed".equals(mode))return actionable(n);
        return displayCategory(n.category()).equalsIgnoreCase(mode);
    }

    private boolean actionable(NotificationItem n){return n!=null&&((n.targetFxml()!=null&&!n.targetFxml().isBlank())||(n.actionCode()!=null&&!n.actionCode().isBlank()));}
    private NotificationItem selected(){return listNotifications.getSelectionModel().getSelectedItem();}

    private void showDetail(NotificationItem n) {
        boolean has=n!=null;
        btnOpenRecord.setDisable(!has||n.targetFxml()==null||n.targetFxml().isBlank()); btnToggleRead.setDisable(!has); btnDeleteNotification.setDisable(!has);
        if(!has){lblDetailTitle.setText("Select a notification");lblDetailMessage.setText("Choose an item from the list to see its details and available actions.");lblDetailMeta.setText("");lblDetailReference.setText("");btnOpenRecord.setText("Open Record");return;}
        lblDetailTitle.setText(safe(n.title())); lblDetailMessage.setText(safe(n.message()));
        Instant created=Instant.ofEpochMilli(n.createdAt());
        String when=DateTimeFormatter.ofPattern(BusinessClock.datePattern()+" • hh:mm a").withZone(BusinessClock.zone()).format(created);
        lblDetailMeta.setText(displayCategory(n.category())+" • "+safe(n.severity())+" • "+when+(n.read()?" • Read":" • Unread"));
        String ref=safe(n.referenceNo()); String module=safe(n.moduleKey());
        lblDetailReference.setText(ref.isBlank()?"No record reference":("Reference: "+ref+(module.isBlank()?"":" • "+module)));
        btnToggleRead.setText(n.read()?"Mark Unread":"Mark Read");
        btnOpenRecord.setText("APPROVE".equalsIgnoreCase(safe(n.actionCode()))?"Review Approval":"Open Record");
        btnOpenRecord.setGraphic(IconFactory.compactIcon("APPROVE".equalsIgnoreCase(safe(n.actionCode()))?"complete":"view",14));
    }

    private String displayCategory(String c){if(c==null||c.isBlank())return"System";String value=c.trim().replace('_',' ').toLowerCase(Locale.ROOT);return Character.toUpperCase(value.charAt(0))+value.substring(1);}
    private String safe(String s){return s==null?"":s;}
    private String categorySemantic(String item){String c=safe(item).toLowerCase(Locale.ROOT);if(c.contains("approval"))return"complete";if(c.contains("sale"))return"sale";if(c.contains("purchase"))return"purchase";if(c.contains("payment"))return"payment";if(c.contains("inventory"))return"item";if(c.contains("reminder"))return"reminder";if(c.contains("communication"))return"email";if(c.contains("security"))return"security";if(c.contains("unread"))return"notification";if(c.contains("action"))return"actions";return"notification";}

    private final class Cell extends ListCell<NotificationItem> {
        @Override protected void updateItem(NotificationItem n,boolean empty){
            super.updateItem(n,empty);setText(null);setGraphic(null);getStyleClass().remove("dse-notify-unread");if(empty||n==null)return;
            Label title=new Label(safe(n.title()));title.getStyleClass().add("dse-notify-row-title");
            Label msg=new Label(safe(n.message()));msg.setWrapText(true);msg.setMaxHeight(42);msg.getStyleClass().add("dse-notify-row-message");
            String ref=safe(n.referenceNo());Label meta=new Label(displayCategory(n.category())+(ref.isBlank()?"":" • "+ref));meta.getStyleClass().add("dse-notify-row-meta");
            VBox text=new VBox(3,title,msg,meta);HBox.setHgrow(text,Priority.ALWAYS);
            VBox stateBox=new VBox(4);stateBox.setAlignment(Pos.TOP_RIGHT);
            if(!n.read()){Label state=new Label("NEW");state.getStyleClass().add("dse-notify-new");stateBox.getChildren().add(state);}
            if("APPROVE".equalsIgnoreCase(safe(n.actionCode()))){Label action=new Label("ACTION");action.getStyleClass().add("dse-notify-action-badge");stateBox.getChildren().add(action);}
            HBox row=new HBox(10,IconFactory.icon(categorySemantic(n.category()),22),text,stateBox);row.setAlignment(Pos.CENTER_LEFT);setGraphic(row);if(!n.read())getStyleClass().add("dse-notify-unread");
        }
    }
}
