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

/** Actionable, full-screen notification workspace with exact-record navigation. */
public final class NotificationCenterController implements ScreenLifecycle {
    @FXML private TextField txtNotificationSearch;
    @FXML private ComboBox<String> cmbNotificationFilter;
    @FXML private ListView<NotificationItem> listNotifications;
    @FXML private Label lblNotificationCount,lblDetailTitle,lblDetailMessage,lblDetailMeta,lblDetailReference;
    @FXML private Button btnOpenRecord,btnToggleRead,btnDeleteNotification;
    private List<NotificationItem> all=List.of();

    @FXML public void initialize(){
        cmbNotificationFilter.getItems().setAll("All","Unread","Action Needed","Sales","Purchases","Quotations","Returns","Payments","Inventory","Reminders","Communication","Backup","Update","Security","System");cmbNotificationFilter.setValue("All");
        txtNotificationSearch.textProperty().addListener((o,a,b)->applyFilter());cmbNotificationFilter.valueProperty().addListener((o,a,b)->applyFilter());
        listNotifications.setCellFactory(v->new Cell()); listNotifications.getSelectionModel().selectedItemProperty().addListener((o,a,b)->showDetail(b));
        listNotifications.setOnMouseClicked(e->{if(e.getClickCount()==2)openRecord();}); refresh();
    }
    @Override public void onScreenShown(boolean reused){refresh();}

    @FXML private void refresh(){lblNotificationCount.setText("Loading…");CompletableFuture.supplyAsync(()->NotificationService.findRecent(300)).whenComplete((items,error)->Platform.runLater(()->{all=error==null&&items!=null?items:List.of();applyFilter();}));}
    @FXML private void markAllRead(){NotificationService.markAllRead();refresh();}
    @FXML private void toggleRead(){NotificationItem item=selected();if(item==null)return;if(item.read())NotificationService.markUnread(item.id());else NotificationService.markRead(item.id());refresh();}
    @FXML private void deleteSelected(){NotificationItem item=selected();if(item==null)return;NotificationService.delete(item.id());refresh();}
    @FXML private void openRecord(){NotificationItem item=selected();if(item==null)return;NotificationService.markRead(item.id());if(item.targetFxml()==null||item.targetFxml().isBlank()){lblDetailMeta.setText("This notification is informational and has no linked application record.");return;}String key=DeepLinkRouter.inferModuleKey(item.targetFxml(),item.category(),item.referenceNo());DeepLinkRouter.open(item.targetFxml(),key,null,item.referenceNo(),"Notification #"+item.id());}

    private void applyFilter(){
        String q=txtNotificationSearch.getText()==null?"":txtNotificationSearch.getText().trim().toLowerCase(Locale.ROOT);String mode=cmbNotificationFilter.getValue()==null?"All":cmbNotificationFilter.getValue();
        List<NotificationItem> visible=all.stream().filter(n->{String cat=safe(n.category());if("Unread".equals(mode)&&n.read())return false;if("Action Needed".equals(mode)&&(n.targetFxml()==null||n.targetFxml().isBlank()))return false;if(!List.of("All","Unread","Action Needed").contains(mode)&&!cat.equalsIgnoreCase(mode))return false;if(q.isBlank())return true;String hay=(safe(n.title())+" "+safe(n.message())+" "+safe(n.referenceNo())+" "+cat).toLowerCase(Locale.ROOT);return hay.contains(q);}).toList();
        listNotifications.getItems().setAll(visible);lblNotificationCount.setText(visible.size()+" shown • "+all.stream().filter(n->!n.read()).count()+" unread");if(!visible.isEmpty()&&listNotifications.getSelectionModel().getSelectedItem()==null)listNotifications.getSelectionModel().selectFirst();if(visible.isEmpty())showDetail(null);
    }
    private NotificationItem selected(){return listNotifications.getSelectionModel().getSelectedItem();}
    private void showDetail(NotificationItem n){boolean has=n!=null;btnOpenRecord.setDisable(!has||n.targetFxml()==null||n.targetFxml().isBlank());btnToggleRead.setDisable(!has);btnDeleteNotification.setDisable(!has);if(!has){lblDetailTitle.setText("Select a notification");lblDetailMessage.setText("Choose an item from the list to see its details and available actions.");lblDetailMeta.setText("");lblDetailReference.setText("");return;}lblDetailTitle.setText(safe(n.title()));lblDetailMessage.setText(safe(n.message()));Instant created=Instant.ofEpochMilli(n.createdAt());String when=DateTimeFormatter.ofPattern(BusinessClock.datePattern()+" • hh:mm a").withZone(BusinessClock.zone()).format(created);lblDetailMeta.setText(displayCategory(n.category())+" • "+safe(n.severity())+" • "+when+(n.read()?" • Read":" • Unread"));lblDetailReference.setText(n.referenceNo()==null||n.referenceNo().isBlank()?"No record reference":("Reference: "+n.referenceNo()));btnToggleRead.setText(n.read()?"Mark Unread":"Mark Read");}
    private String displayCategory(String c){if(c==null||c.isBlank())return "System";String s=c.toLowerCase(Locale.ROOT).replace('_',' ');return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private String safe(String s){return s==null?"":s;}

    private final class Cell extends ListCell<NotificationItem>{
        @Override protected void updateItem(NotificationItem n,boolean empty){super.updateItem(n,empty);setText(null);setGraphic(null);getStyleClass().remove("dse-notify-unread");if(empty||n==null)return;Label title=new Label(safe(n.title()));title.getStyleClass().add("dse-notify-row-title");Label msg=new Label(safe(n.message()));msg.setWrapText(true);msg.getStyleClass().add("dse-notify-row-message");Label meta=new Label(displayCategory(n.category())+(n.referenceNo()==null||n.referenceNo().isBlank()?"":" • "+n.referenceNo()));meta.getStyleClass().add("dse-notify-row-meta");VBox text=new VBox(3,title,msg,meta);HBox.setHgrow(text,Priority.ALWAYS);Label state=new Label(n.read()?"":"NEW");state.getStyleClass().add("dse-notify-new");HBox row=new HBox(10,IconFactory.icon(semantic(n),22),text,state);row.setAlignment(Pos.CENTER_LEFT);setGraphic(row);if(!n.read())getStyleClass().add("dse-notify-unread");}
        private String semantic(NotificationItem n){String c=safe(n.category()).toLowerCase(Locale.ROOT);if(c.contains("sale"))return "sale";if(c.contains("purchase"))return "purchase";if(c.contains("payment"))return "payment";if(c.contains("inventory"))return "item";if(c.contains("reminder"))return "reminder";if(c.contains("communication"))return "email";if(c.contains("security"))return "security";return "notification";}
    }
}
