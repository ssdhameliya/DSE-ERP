package org.example.controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.model.Sales;
import org.example.util.ProfessionalUiEnhancer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "dse.uat.sales.rows", matches = ".+")
class SalesRegisterRuntimeUatProbeTest {
    private static volatile boolean toolkitStarted;

    @Test
    void realRecoveredUatRowsScrollWithLightweightActionsAndActionSelectsItsRow() throws Exception {
        Path rowsFile = Path.of(System.getProperty("dse.uat.sales.rows"));
        List<String> rows = Files.readAllLines(rowsFile);
        assertFalse(rows.isEmpty());
        ensureToolkit();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> evidence = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                SalesListController controller = new SalesListController();
                TableView<Sales> table = new TableView<>();
                table.setFixedCellSize(44);
                table.setPrefSize(1450, 720);
                TableColumn<Sales,Void> actions = new TableColumn<>("Actions");
                table.getColumns().add(actions);
                for (int i=0;i<12;i++) {
                    TableColumn<Sales,String> c = new TableColumn<>("C"+i);
                    c.setPrefWidth(105);
                    c.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(v.getValue().getInvoiceNo()));
                    table.getColumns().add(c);
                }
                for (String line : rows) {
                    String[] p = line.split("\\t", -1);
                    Sales sale = new Sales();
                    sale.setId(Integer.parseInt(p[0]));
                    sale.setInvoiceNo(p.length>1?p[1]:"UAT");
                    sale.setDocumentStatus(p.length>2?p[2]:"APPROVED");
                    table.getItems().add(sale);
                }
                set(controller, "tableSales", table);
                set(controller, "colAction", actions);
                Method configure = SalesListController.class.getDeclaredMethod("configureActions");
                configure.setAccessible(true);
                configure.invoke(controller);
                ProfessionalUiEnhancer.enhance(table);

                Stage stage = new Stage();
                stage.setScene(new Scene(new StackPane(table), 1450, 720));
                stage.show();
                table.applyCss(); table.layout();

                long allStart = System.nanoTime();
                for (int i=0;i<200;i++) { table.scrollTo(i%table.getItems().size()); table.applyCss(); table.layout(); }
                long allMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-allStart);

                int menuButtons = descendants(table).stream().filter(MenuButton.class::isInstance).toList().size();
                List<Button> actionButtons = descendants(table).stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).filter(b -> "Actions".equals(b.getText())).toList();
                assertEquals(0, menuButtons, "Sales Register should not create per-row MenuButtons");
                assertFalse(actionButtons.isEmpty());

                // Select a different row, then open an Actions button and prove that exact row becomes selected.
                table.getSelectionModel().selectFirst();
                Button button = actionButtons.stream().filter(b -> parentCellIndex(b) > 0).findFirst().orElse(actionButtons.getFirst());
                int actionIndex = parentCellIndex(button);
                button.fire();
                assertEquals(actionIndex, table.getSelectionModel().getSelectedIndex());

                actions.setVisible(false);
                table.applyCss(); table.layout();
                long baseStart = System.nanoTime();
                for (int i=0;i<200;i++) { table.scrollTo(i%table.getItems().size()); table.applyCss(); table.layout(); }
                long baseMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-baseStart);
                double ratio = baseMs == 0 ? 0d : (double)allMs/baseMs;
                evidence.set("SALES_REGISTER_UAT rows="+rows.size()+" scroll200_ms="+allMs+" no_action_ms="+baseMs+" ratio="+String.format(java.util.Locale.ROOT,"%.2f",ratio)+" visibleActionButtons="+actionButtons.size()+" menuButtons="+menuButtons+" selectedActionRow="+actionIndex);
                assertTrue(ratio < 1.8, "Actions column overhead should remain bounded: "+ratio);
                stage.close();
            } catch (Throwable t) { failure.set(t); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS));
        if (failure.get()!=null) throw new AssertionError(failure.get());
        System.out.println(evidence.get());
    }

    private static int parentCellIndex(javafx.scene.Node node) {
        for (javafx.scene.Node n=node; n!=null; n=n.getParent()) if (n instanceof TableCell<?,?> c) return c.getIndex();
        return -1;
    }
    private static java.util.Set<javafx.scene.Node> descendants(javafx.scene.Parent root) {
        java.util.Set<javafx.scene.Node> result = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<javafx.scene.Node> q = new java.util.ArrayDeque<>(); q.add(root);
        while(!q.isEmpty()){var n=q.removeFirst(); if(!result.add(n))continue; if(n instanceof javafx.scene.Parent p)q.addAll(p.getChildrenUnmodifiable());}
        return result;
    }
    private static void set(Object target,String name,Object value) throws Exception { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value); }
    private static synchronized void ensureToolkit() throws Exception {
        if (toolkitStarted) return;
        CountDownLatch latch=new CountDownLatch(1);
        Platform.startup(latch::countDown);
        assertTrue(latch.await(10,TimeUnit.SECONDS));
        toolkitStarted=true;
    }
}
