package org.example.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.api.audit.AuditApiClient;
import org.example.util.BusinessClock;

import java.util.*;

/**
 * Authoritative record-level Audit Trail. The class name is retained so existing
 * transaction controllers keep binary/source compatibility while the backing data
 * now comes from the centralized server audit store.
 */
public final class ActivityTimelineDialog {
    private ActivityTimelineDialog() { }

    public static void show(Node owner,String entityType,int entityId,String reference){
        if(owner==null||entityId<=0)return;
        AuditApiClient api=new AuditApiClient();
        UiTaskExecutor.submitLatest("audit-trail-"+entityType+"-"+entityId,
                ()->api.record(entityType,entityId),
                rows->showRows(owner,entityType,reference,rows),
                failure->new OwnedAlert(Alert.AlertType.ERROR,"Audit Trail could not be loaded.\n\n"+rootMessage(failure)).showAndWait());
    }

    private static void showRows(Node owner,String entityType,String reference,List<AuditApiClient.EventRow> rows){
        Dialog<Void> dialog=new OwnedDialog<>(owner);
        dialog.setTitle("Audit Trail");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStyleClass().add("audit-dialog");

        VBox root=new VBox(12);root.setPadding(new Insets(14));root.setPrefWidth(940);
        HBox head=new HBox(12);head.setAlignment(Pos.CENTER_LEFT);
        Label icon=new Label("◷");icon.getStyleClass().add("audit-title-icon");
        VBox titles=new VBox(2,new Label("Audit Trail"),new Label(pretty(entityType)+" • "+safe(reference)));
        titles.getChildren().get(0).getStyleClass().add("audit-title");titles.getChildren().get(1).getStyleClass().add("audit-subtitle");
        head.getChildren().addAll(icon,titles);
        root.getChildren().add(head);

        root.getChildren().add(summary(rows));
        HBox tabs=new HBox(7);tabs.setAlignment(Pos.CENTER_LEFT);
        for(String t:List.of("All","Changes","Financial","Documents","Communication")){Label l=new Label(t);l.getStyleClass().addAll("audit-filter-chip",t.equals("All")?"audit-filter-chip-active":"audit-filter-chip-idle");tabs.getChildren().add(l);}Region grow=new Region();HBox.setHgrow(grow,Priority.ALWAYS);Label scope=new Label("Screen scope • this record only");scope.getStyleClass().add("audit-subtitle");tabs.getChildren().addAll(grow,scope);root.getChildren().add(tabs);

        VBox events=new VBox(8);
        if(rows==null||rows.isEmpty()){Label empty=new Label("No audit event has been recorded for this record yet.");empty.getStyleClass().add("muted-label");events.getChildren().add(empty);}else for(var row:rows)events.getChildren().add(eventCard(row));
        ScrollPane scroll=new ScrollPane(events);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(500);scroll.getStyleClass().add("audit-scroll");root.getChildren().add(scroll);
        Label note=new Label("Historical events show only values captured by the version that created them. New Audit Trail events store field-level old → new values.");note.setWrapText(true);note.getStyleClass().add("audit-legacy-note");root.getChildren().add(note);
        dialog.getDialogPane().setContent(root);dialog.showAndWait();
    }

    private static Node summary(List<AuditApiClient.EventRow> rows){
        List<AuditApiClient.EventRow> r=rows==null?List.of():rows;
        AuditApiClient.EventRow created=findLast(r,"CREATED"),approved=findLast(r,"APPROVED"),payment=findLastPrefix(r,"PAYMENT"),latest=r.isEmpty()?null:r.getFirst();
        GridPane g=new GridPane();g.setHgap(9);for(int i=0;i<4;i++){ColumnConstraints c=new ColumnConstraints();c.setPercentWidth(25);g.getColumnConstraints().add(c);}
        g.add(summaryCard("Created",when(created),created==null?"No creation event":safe(created.detail()),"purple"),0,0);
        g.add(summaryCard("Approved",when(approved),approved==null?"Not approved / legacy gap":user(approved),"green"),1,0);
        g.add(summaryCard("Payment",paymentValue(payment),payment==null?"No payment event":when(payment),"blue"),2,0);
        g.add(summaryCard("Last Updated",when(latest),latest==null?"No events":pretty(latest.action()),"orange"),3,0);return g;
    }
    private static VBox summaryCard(String title,String value,String sub,String color){Label a=new Label(title);a.getStyleClass().add("audit-summary-title");Label b=new Label(value);b.getStyleClass().addAll("audit-summary-value","audit-color-"+color);Label c=new Label(sub);c.setWrapText(true);c.getStyleClass().add("audit-summary-subtitle");VBox v=new VBox(3,a,b,c);v.getStyleClass().addAll("audit-summary-card","audit-summary-"+color);return v;}

    private static Node eventCard(AuditApiClient.EventRow row){
        VBox card=new VBox(7);card.getStyleClass().add("audit-event-card");
        HBox top=new HBox(8);top.setAlignment(Pos.CENTER_LEFT);
        Label dot=new Label(symbol(row));dot.getStyleClass().addAll("audit-event-dot",categoryClass(row));
        VBox titleBox=new VBox(1);Label title=new Label(pretty(row.action()));title.getStyleClass().add("audit-event-title");Label time=new Label(formatTime(row.createdAt()));time.getStyleClass().add("audit-event-time");titleBox.getChildren().addAll(title,time);
        Region grow=new Region();HBox.setHgrow(grow,Priority.ALWAYS);
        Label category=new Label(pretty(row.category()));category.getStyleClass().addAll("audit-badge",categoryClass(row));
        Label source=new Label(safe(row.legacySource()).isBlank()?"LIVE":"LEGACY");source.getStyleClass().addAll("audit-badge",safe(row.legacySource()).isBlank()?"audit-badge-live":"audit-badge-legacy");
        Label who=new Label(user(row));who.getStyleClass().add("audit-user");top.getChildren().addAll(dot,titleBox,grow,category,source,who);card.getChildren().add(top);
        if(!safe(row.detail()).isBlank()){Label detail=new Label(row.detail());detail.setWrapText(true);detail.getStyleClass().add("audit-detail");card.getChildren().add(detail);}
        if(row.changes()!=null&&!row.changes().isEmpty())card.getChildren().add(changeTable(row.changes()));
        else if(!safe(row.legacySource()).isBlank()){Label legacy=new Label("Detailed old/new values were not captured by the historical version for this event.");legacy.getStyleClass().add("audit-legacy-inline");card.getChildren().add(legacy);}
        return card;
    }
    private static GridPane changeTable(List<AuditApiClient.ChangeRow> changes){GridPane g=new GridPane();g.getStyleClass().add("audit-change-grid");g.setHgap(8);g.setVgap(1);ColumnConstraints c1=new ColumnConstraints();c1.setPercentWidth(28);ColumnConstraints c2=new ColumnConstraints();c2.setPercentWidth(36);ColumnConstraints c3=new ColumnConstraints();c3.setPercentWidth(36);g.getColumnConstraints().addAll(c1,c2,c3);addCell(g,0,0,"Field","audit-change-head");addCell(g,1,0,"Old Value","audit-change-head");addCell(g,2,0,"New Value","audit-change-head");int r=1;for(var c:changes){addCell(g,0,r,safe(c.fieldName()),"audit-change-field");addCell(g,1,r,display(c.oldValue()),"audit-old-value");addCell(g,2,r,display(c.newValue()),"audit-new-value");r++;}return g;}
    private static void addCell(GridPane g,int c,int r,String value,String style){Label l=new Label(value);l.setWrapText(true);l.setMaxWidth(Double.MAX_VALUE);l.getStyleClass().add(style);g.add(l,c,r);}

    private static AuditApiClient.EventRow findLast(List<AuditApiClient.EventRow> r,String action){for(int i=r.size()-1;i>=0;i--)if(action.equalsIgnoreCase(r.get(i).action()))return r.get(i);return null;}
    private static AuditApiClient.EventRow findLastPrefix(List<AuditApiClient.EventRow> r,String prefix){for(var x:r)if(safe(x.action()).startsWith(prefix))return x;return null;}
    private static String when(AuditApiClient.EventRow e){return e==null?"—":formatTime(e.createdAt());}
    private static String paymentValue(AuditApiClient.EventRow e){if(e==null)return "—";for(var c:e.changes())if(safe(c.fieldName()).toUpperCase(Locale.ROOT).contains("AMOUNT"))return display(c.newValue());return pretty(e.action());}
    private static String user(AuditApiClient.EventRow e){return e==null?"—":(safe(e.createdBy()).isBlank()?"System":e.createdBy());}
    private static String symbol(AuditApiClient.EventRow e){String c=safe(e.category());if(c.equals("FINANCIAL"))return "₹";if(c.equals("DOCUMENT"))return "▤";if(c.equals("COMMUNICATION"))return "✉";if(safe(e.action()).contains("APPROV"))return "✓";return "•";}
    private static String categoryClass(AuditApiClient.EventRow e){String c=safe(e.category());return switch(c){case "FINANCIAL"->"audit-blue";case "DOCUMENT"->"audit-purple";case "COMMUNICATION"->"audit-teal";case "LIFECYCLE"->safe(e.action()).contains("DELETE")||safe(e.action()).contains("REJECT")?"audit-red":"audit-green";default->safe(e.action()).contains("UPDATE")?"audit-orange":"audit-purple";};}
    private static String formatTime(String v){String s=safe(v);try{return BusinessClock.formatTimestamp(s);}catch(Exception ignored){return s;}}
    private static String display(String s){return safe(s).isBlank()?"—":s;}
    private static String safe(String v){return v==null?"":v.trim();}
    private static String pretty(String v){String s=safe(v).replace('_',' ');if(s.isBlank())return "—";StringBuilder b=new StringBuilder();for(String p:s.toLowerCase(Locale.ROOT).split(" ")){if(!b.isEmpty())b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}
    private static String rootMessage(Throwable failure){Throwable c=failure;while(c!=null&&c.getCause()!=null&&c.getCause()!=c)c=c.getCause();String m=c==null?"Unknown error":c.getMessage();return m==null||m.isBlank()?String.valueOf(c):m;}
}
