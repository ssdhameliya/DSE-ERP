package org.example.navigation;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Duration;
import org.example.controller.LinkedRecordContext;
import org.example.util.PerformanceMonitor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Generic fallback that focuses an exact table record for search/notification deep links. */
public final class DeepLinkSupport {
    private DeepLinkSupport() {}

    public static void schedule(Node pageRoot) {
        if (pageRoot == null || LinkedRecordContext.peek() == null) return;
        Platform.runLater(() -> attempt(pageRoot));
        for (int delay : new int[]{250, 750, 1600}) {
            PauseTransition pause = new PauseTransition(Duration.millis(delay));
            pause.setOnFinished(e -> attempt(pageRoot));
            pause.play();
        }
    }

    private static void attempt(Node root) {
        LinkedRecordContext.Target target = LinkedRecordContext.peek();
        if (target == null) return;
        for (TableView<?> table : tables(root)) {
            Object match = findMatch(table, target);
            if (match == null) continue;
            @SuppressWarnings("unchecked") TableView<Object> typed = (TableView<Object>) table;
            typed.getSelectionModel().select(match);
            typed.scrollTo(match);
            typed.requestFocus();
            LinkedRecordContext.consumeAny();
            PerformanceMonitor.event("linked-navigation", target.module()+" -> "+target.documentNo()+" | generic-table-focus | source="+target.source());
            return;
        }
    }

    private static Object findMatch(TableView<?> table, LinkedRecordContext.Target target) {
        if (table.getItems() == null) return null;
        String ref = target.documentNo() == null ? "" : target.documentNo().trim().toLowerCase(Locale.ROOT);
        for (Object item : table.getItems()) {
            if (item == null) continue;
            if (target.recordId() != null && idOf(item) == target.recordId().longValue()) return item;
            if (ref.isBlank()) continue;
            for (TableColumn<?,?> column : flatten(table.getColumns())) {
                try {
                    @SuppressWarnings("unchecked") TableColumn<Object,Object> c=(TableColumn<Object,Object>)column;
                    ObservableValue<Object> ov=c.getCellObservableValue(item);
                    Object value=ov==null?null:ov.getValue();
                    if (value != null && String.valueOf(value).trim().toLowerCase(Locale.ROOT).contains(ref)) return item;
                } catch (Exception ignored) { }
            }
            String reflected = referenceText(item).toLowerCase(Locale.ROOT);
            if (!reflected.isBlank() && reflected.contains(ref)) return item;
        }
        return null;
    }

    private static long idOf(Object item) {
        for (String name : new String[]{"getId","id"}) {
            try {
                Method m=item.getClass().getMethod(name); Object v=m.invoke(item);
                if (v instanceof Number n) return n.longValue();
            } catch (Exception ignored) { }
        }
        return Long.MIN_VALUE;
    }

    private static String referenceText(Object item) {
        StringBuilder b=new StringBuilder();
        for (String name : new String[]{"getInvoiceNo","getQuotationNo","getReturnNo","getItemCode","getPartyCode","getReferenceNo","getVoucherNo","getTitle","getDescription","reference","no","code"}) {
            try {
                Method m=item.getClass().getMethod(name); Object v=m.invoke(item);
                if (v != null) b.append(' ').append(v);
            } catch (Exception ignored) { }
        }
        return b.toString();
    }

    private static List<TableView<?>> tables(Node root) {
        List<TableView<?>> out=new ArrayList<>(); collect(root,out); return out;
    }
    private static void collect(Node node,List<TableView<?>> out) {
        if (node instanceof TableView<?> t) out.add(t);
        if (node instanceof Parent p) for (Node child:p.getChildrenUnmodifiable()) collect(child,out);
    }
    private static List<TableColumn<?,?>> flatten(List<? extends TableColumn<?,?>> columns) {
        List<TableColumn<?,?>> out=new ArrayList<>();
        for (TableColumn<?,?> c:columns) { if(c.getColumns().isEmpty()) out.add(c); else out.addAll(flatten(c.getColumns())); }
        return out;
    }
}
