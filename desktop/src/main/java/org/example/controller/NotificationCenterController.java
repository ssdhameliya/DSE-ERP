package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.navigation.DeepLinkRouter;
import org.example.navigation.ScreenLifecycle;
import org.example.service.NotificationService;
import org.example.service.NotificationService.NotificationItem;
import org.example.service.NotificationService.ResolvedLink;
import org.example.util.BusinessClock;
import org.example.util.IconFactory;
import org.example.util.ModernDialog;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Premium Notification Center with exact-record actions and stable category filtering. */
public final class NotificationCenterController implements ScreenLifecycle {
    private static final String ALL = "All Notifications";
    private static final List<String> BASE_FILTERS = List.of(ALL,"Unread","Action Needed","Approval","Sales","Purchases","Quotations","Returns","Payments","Inventory","Reminders","Communication","Security","System","Update","Backup");

    @FXML private StackPane notificationHeaderIcon,allSummaryIcon,unreadSummaryIcon,actionSummaryIcon,detailIcon;
    @FXML private TextField txtNotificationSearch;
    @FXML private ListView<CategoryOption> listNotificationCategories;
    @FXML private ListView<NotificationItem> listNotifications;
    @FXML private Label lblNotificationCount,lblAllCount,lblUnreadCount,lblActionCount,lblDetailTitle,lblDetailMessage,lblDetailMeta,lblDetailReference;
    @FXML private Button btnOpenRecord,btnToggleRead,btnDeleteNotification,btnMarkAllRead,btnRefresh,btnClearHistory;
    private List<NotificationItem> all = List.of();
    private long refreshSerial;

    private record CategoryOption(String name,long count) { @Override public String toString(){return name;} }

    @FXML public void initialize() {
        installIcons();
        listNotificationCategories.getSelectionModel().selectedItemProperty().addListener((o,a,b)->applyFilter());
        listNotificationCategories.setCellFactory(v -> new CategoryCell());
        txtNotificationSearch.textProperty().addListener((o,a,b)->applyFilter());
        listNotifications.setCellFactory(v -> new Cell());
        listNotifications.getSelectionModel().selectedItemProperty().addListener((o,a,b)->showDetail(b));
        listNotifications.setOnMouseClicked(e -> { if(e.getClickCount()==2) openRecord(); });
        refresh();
    }

    private void installIcons(){
        if(notificationHeaderIcon!=null)notificationHeaderIcon.getChildren().setAll(IconFactory.compactIcon("notification",22));
        if(allSummaryIcon!=null)allSummaryIcon.getChildren().setAll(IconFactory.compactIcon("layers",20));
        if(unreadSummaryIcon!=null)unreadSummaryIcon.getChildren().setAll(IconFactory.compactIcon("email",19));
        if(actionSummaryIcon!=null)actionSummaryIcon.getChildren().setAll(IconFactory.compactIcon("warning",20));
        if(detailIcon!=null)detailIcon.getChildren().setAll(IconFactory.compactIcon("notification",22));
        graphic(btnMarkAllRead,"complete"); graphic(btnRefresh,"refresh"); graphic(btnClearHistory,"delete"); graphic(btnOpenRecord,"view"); graphic(btnToggleRead,"complete"); graphic(btnDeleteNotification,"delete");
    }
    private void graphic(Button button,String semantic){if(button==null)return;button.setGraphic(IconFactory.compactIcon(semantic,14));button.getProperties().put("erp-icon-preserve",true);}

    @Override public void onScreenShown(boolean reused){ refresh(); }

    @FXML private void refresh() {
        long serial=++refreshSerial;
        lblNotificationCount.setText("Loading…");
        if(btnRefresh!=null)btnRefresh.setDisable(true);
        CompletableFuture.supplyAsync(() -> NotificationService.findRecent(500)).whenComplete((items,error) -> Platform.runLater(() -> {
            if(serial!=refreshSerial)return;
            if(btnRefresh!=null)btnRefresh.setDisable(false);
            all = error==null && items!=null ? List.copyOf(items) : List.of();
            updateSummary(); applyFilter();
        }));
    }

    @FXML private void markAllRead(){ NotificationService.markAllRead(); refresh(); }
    @FXML private void clearHistory(){
        if(all.isEmpty()) return;
        if(!ModernDialog.confirm(listNotifications,"Clear Notification History?","Delete all notification history?","This permanently clears the notification history for this workspace. This cannot be undone.")) return;
        NotificationService.clear(); refresh();
    }
    @FXML private void toggleRead(){ NotificationItem item=selected(); if(item==null)return; if(item.read())NotificationService.markUnread(item.id());else NotificationService.markRead(item.id()); refresh(); }
    @FXML private void deleteSelected(){ NotificationItem item=selected(); if(item==null)return; NotificationService.delete(item.id()); refresh(); }

    @FXML private void openRecord() {
        NotificationItem item = selected(); if(item==null)return;
        NotificationService.markRead(item.id());
        String ref=safe(item.referenceNo()).trim();
        if(item.recordId()!=null){
            openResolved(item,new ResolvedLink(true,item.moduleKey(),item.recordId(),ref,item.targetFxml()));
            return;
        }
        if(ref.isBlank()){
            lblDetailReference.setText("This notification is informational and has no exact ERP record reference.");
            return;
        }
        btnOpenRecord.setDisable(true);btnOpenRecord.setText("Resolving…");
        CompletableFuture.supplyAsync(() -> NotificationService.resolveExact(item)).whenComplete((resolved,error)->Platform.runLater(()->{
            btnOpenRecord.setText(actionButtonText(item));btnOpenRecord.setDisable(false);
            if(error!=null||resolved==null||!resolved.found()||resolved.recordId()==null){
                lblDetailReference.setText("The exact linked record could not be resolved. It may have been deleted or you may no longer have permission to open it.");
                return;
            }
            openResolved(item,resolved);
        }));
    }

    private void openResolved(NotificationItem item,ResolvedLink resolved){
        String target=!safe(resolved.targetFxml()).isBlank()?resolved.targetFxml():item.targetFxml();
        String key=!safe(resolved.moduleKey()).isBlank()?resolved.moduleKey():item.moduleKey();
        String ref=!safe(resolved.reference()).isBlank()?resolved.reference():item.referenceNo();
        if(safe(target).isBlank()){lblDetailReference.setText("The linked record exists but no application destination is available for it.");return;}
        DeepLinkRouter.open(target,key,resolved.recordId(),ref,safe(item.actionCode()).isBlank()?"VIEW":item.actionCode(),"Notification #"+item.id());
    }

    private void updateSummary() {
        long unread=all.stream().filter(n->!n.read()).count();
        long actionable=all.stream().filter(this::actionable).count();
        lblAllCount.setText(String.valueOf(all.size())); lblUnreadCount.setText(String.valueOf(unread)); lblActionCount.setText(String.valueOf(actionable));
        String selected=Optional.ofNullable(listNotificationCategories.getSelectionModel().getSelectedItem()).map(CategoryOption::name).orElse(ALL);
        LinkedHashMap<String,Long> counts=new LinkedHashMap<>();
        for(String name:BASE_FILTERS)counts.put(name,countFor(name));
        for(NotificationItem n:all){String d=displayCategory(n.category());counts.putIfAbsent(d,countFor(d));}
        List<CategoryOption> options=counts.entrySet().stream().filter(e->BASE_FILTERS.contains(e.getKey())||e.getValue()>0).map(e->new CategoryOption(e.getKey(),e.getValue())).toList();
        listNotificationCategories.getItems().setAll(options);
        listNotificationCategories.getSelectionModel().select(options.stream().filter(x->x.name().equals(selected)).findFirst().orElse(options.isEmpty()?null:options.getFirst()));
    }

    private long countFor(String mode){if(ALL.equals(mode))return all.size();if("Unread".equals(mode))return all.stream().filter(n->!n.read()).count();if("Action Needed".equals(mode))return all.stream().filter(this::actionable).count();return all.stream().filter(n->displayCategory(n.category()).equalsIgnoreCase(mode)).count();}

    private void applyFilter() {
        String q=safe(txtNotificationSearch.getText()).trim().toLowerCase(Locale.ROOT);
        String mode=Optional.ofNullable(listNotificationCategories.getSelectionModel().getSelectedItem()).map(CategoryOption::name).orElse(ALL);
        List<NotificationItem> visible=all.stream().filter(n->matchesMode(n,mode)).filter(n->{
            if(q.isBlank())return true;
            String hay=(safe(n.title())+" "+safe(n.message())+" "+safe(n.referenceNo())+" "+safe(n.category())+" "+safe(n.moduleKey())).toLowerCase(Locale.ROOT);
            return hay.contains(q);
        }).toList();
        NotificationItem selected=listNotifications.getSelectionModel().getSelectedItem();
        listNotifications.getItems().setAll(visible);
        lblNotificationCount.setText(visible.size()+" shown  •  "+all.stream().filter(n->!n.read()).count()+" unread");
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

    private boolean actionable(NotificationItem n){
        if(n==null)return false;
        if("Approval".equalsIgnoreCase(displayCategory(n.category())))return true;
        String action=safe(n.actionCode()).trim().toUpperCase(Locale.ROOT);
        return Set.of("APPROVE","REVIEW","RESOLVE","PAY","PAYMENT","RECONCILE","COMPLETE","EXECUTE","CHECK","INSTALL").contains(action);
    }
    private boolean canOpen(NotificationItem n){return n!=null&&(n.recordId()!=null||!safe(n.referenceNo()).isBlank());}
    private NotificationItem selected(){return listNotifications.getSelectionModel().getSelectedItem();}

    private void showDetail(NotificationItem n) {
        boolean has=n!=null;
        btnOpenRecord.setDisable(!has||!canOpen(n)); btnToggleRead.setDisable(!has); btnDeleteNotification.setDisable(!has);
        if(!has){
            lblDetailTitle.setText("Select a notification");lblDetailMessage.setText("Choose an item from the list to see its details and available actions.");lblDetailMessage.setVisible(true);lblDetailMessage.setManaged(true);lblDetailMeta.setText("");lblDetailReference.setText("");btnOpenRecord.setText("Open Record");
            if(detailIcon!=null)detailIcon.getChildren().setAll(IconFactory.compactIcon("notification",22)); return;
        }
        String category=displayCategory(n.category());
        lblDetailTitle.setText(displayTitle(n));
        String detailMessage = displayMessage(n);
        lblDetailMessage.setText(detailMessage);
        lblDetailMessage.setVisible(!detailMessage.isBlank());
        lblDetailMessage.setManaged(!detailMessage.isBlank());
        lblDetailMeta.setGraphic(IconFactory.compactIcon(categorySemantic(category),13));
        lblDetailMeta.setText(category+"  •  "+safe(n.severity())+"  •  "+whenText(n.createdAt())+(n.read()?"  •  Read":"  •  Unread"));
        String ref=safe(n.referenceNo()); String module=safe(n.moduleKey());
        lblDetailReference.setGraphic(IconFactory.compactIcon("link",13));
        lblDetailReference.setText(ref.isBlank()?"No record reference":("Reference: "+ref+(module.isBlank()?"":"  •  "+module)));
        btnToggleRead.setText(n.read()?"Mark Unread":"Mark Read");
        btnOpenRecord.setText(actionButtonText(n));
        graphic(btnOpenRecord,"APPROVE".equalsIgnoreCase(safe(n.actionCode()))?"complete":"view");
        if(detailIcon!=null)detailIcon.getChildren().setAll(IconFactory.compactIcon(categorySemantic(category),22));
    }

    private String actionButtonText(NotificationItem n){return "APPROVE".equalsIgnoreCase(safe(n.actionCode()))?"Review Approval":"Open Record";}
    private String whenText(long epoch){return DateTimeFormatter.ofPattern(BusinessClock.datePattern()+" • hh:mm a").withZone(BusinessClock.zone()).format(Instant.ofEpochMilli(epoch));}
    private String rowTimestamp(long epoch){return DateTimeFormatter.ofPattern(BusinessClock.datePattern()+"  •  hh:mm a").withZone(BusinessClock.zone()).format(Instant.ofEpochMilli(epoch));}
    private boolean genericTitle(NotificationItem n){String t=safe(n==null?null:n.title()).trim();return t.isBlank()||"Notification".equalsIgnoreCase(t);}
    private String displayTitle(NotificationItem n){if(n==null)return"";return genericTitle(n)?safe(n.message()).trim():safe(n.title()).trim();}
    private String displayMessage(NotificationItem n){if(n==null)return"";return genericTitle(n)?"":safe(n.message()).trim();}
    private String displayCategory(String c){if(c==null||c.isBlank())return"System";String value=c.trim().replace('_',' ').toLowerCase(Locale.ROOT);return Character.toUpperCase(value.charAt(0))+value.substring(1);}
    private String safe(String s){return s==null?"":s;}
    private String categorySemantic(String item){String c=safe(item).toLowerCase(Locale.ROOT);if(c.contains("approval"))return"complete";if(c.contains("sale"))return"sale";if(c.contains("purchase"))return"purchase";if(c.contains("quotation"))return"quotation";if(c.contains("return"))return"return";if(c.contains("payment"))return"payment";if(c.contains("inventory"))return"item";if(c.contains("reminder"))return"reminder";if(c.contains("communication"))return"email";if(c.contains("security"))return"security";if(c.contains("backup"))return"backup";if(c.contains("update"))return"refresh";if(c.contains("unread"))return"notification";if(c.contains("action"))return"warning";return"notification";}
    private String slug(String value){String s=safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");return s.isBlank()?"system":s;}

    private final class CategoryCell extends ListCell<CategoryOption>{
        @Override protected void updateItem(CategoryOption option,boolean empty){
            super.updateItem(option,empty);setText(null);setGraphic(null);if(empty||option==null)return;
            Node icon=IconFactory.compactIcon(categorySemantic(option.name()),14);
            Label name=new Label(option.name());name.getStyleClass().add("dse-notification-v3-category-name");HBox.setHgrow(name,Priority.ALWAYS);
            Label count=new Label(String.valueOf(option.count()));count.getStyleClass().add("dse-notification-v3-category-count");
            HBox box=new HBox(8,icon,name,count);box.setAlignment(Pos.CENTER_LEFT);box.getStyleClass().add("dse-notification-v3-category-cell");setGraphic(box);
        }
    }

    private final class Cell extends ListCell<NotificationItem> {
        @Override protected void updateItem(NotificationItem n,boolean empty){
            super.updateItem(n,empty);setText(null);setGraphic(null);getStyleClass().removeAll("dse-notification-v3-unread-cell","dse-notification-v3-action-cell");
            if(empty||n==null)return;
            String category=displayCategory(n.category());
            StackPane iconWell=new StackPane(IconFactory.compactIcon(categorySemantic(category),19));iconWell.setMinSize(42,42);iconWell.setPrefSize(42,42);iconWell.setMaxSize(42,42);iconWell.getStyleClass().addAll("dse-notification-v3-row-icon","dse-notification-v3-row-icon-"+slug(category));

            Label title=new Label(displayTitle(n));title.getStyleClass().add("dse-notification-v3-row-title");
            VBox text=new VBox(3,title);HBox.setHgrow(text,Priority.ALWAYS);
            String message=displayMessage(n);
            if(!message.isBlank()){Label msg=new Label(message);msg.setWrapText(false);msg.setTextOverrun(OverrunStyle.ELLIPSIS);msg.getStyleClass().add("dse-notification-v3-row-message");text.getChildren().add(msg);}
            Label meta=new Label(category+(safe(n.referenceNo()).isBlank()?"":"  •  "+safe(n.referenceNo())));meta.getStyleClass().add("dse-notification-v3-row-meta");text.getChildren().add(meta);

            Label time=new Label(rowTimestamp(n.createdAt()));time.setAlignment(Pos.CENTER);time.setMinWidth(165);time.setPrefWidth(175);time.getStyleClass().add("dse-notification-v3-row-time");

            Label categoryBadge=new Label(category);categoryBadge.getStyleClass().addAll("dse-notification-v3-badge","dse-notification-v3-badge-"+slug(category));
            VBox right=new VBox(6,categoryBadge);right.setAlignment(Pos.CENTER_RIGHT);right.setMinWidth(92);
            HBox state=new HBox(5);state.setAlignment(Pos.CENTER_RIGHT);
            if(!n.read()){Label badge=new Label("NEW");badge.getStyleClass().add("dse-notification-v3-new");state.getChildren().add(badge);getStyleClass().add("dse-notification-v3-unread-cell");}
            if(actionable(n)){Label badge=new Label("ACTION");badge.getStyleClass().add("dse-notification-v3-action-badge");state.getChildren().add(badge);getStyleClass().add("dse-notification-v3-action-cell");}
            if(!state.getChildren().isEmpty())right.getChildren().add(state);

            HBox row=new HBox(12,iconWell,text,time,right);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("dse-notification-v3-row");setGraphic(row);
        }
    }
}
