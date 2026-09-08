package org.example.util;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real JavaFX geometry regression for the central table/KPI lifecycle.
 *
 * <p>The class is intentionally opt-in and also requires a graphical DISPLAY.
 * Normal Maven verification stays deterministic; release verification enables
 * {@code -Ddse.ui.runtime.regression=true} under Linux Xvfb.
 * It protects the shared managers rather than any single business screen.</p>
 */
class CentralUiRuntimeRegressionTest {
    private Stage stage;

    @BeforeAll
    static void startJavaFx() throws Exception {
        assumeTrue(Boolean.getBoolean("dse.ui.runtime.regression"),
                "JavaFX geometry regression is an explicit release-verification test");
        assumeTrue(System.getenv("DISPLAY") != null && !System.getenv("DISPLAY").isBlank(),
                "JavaFX geometry regression requires DISPLAY/Xvfb");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // JavaFX toolkit is already running in this test VM.
        }
        fx(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @AfterEach
    void closeStage() throws Exception {
        if (stage != null) {
            fx(() -> {
                stage.hide();
                stage.close();
                stage = null;
                return null;
            });
        }
    }

    @Test
    void tableReturnsToSameGeometryAfterRepeatedDrawerCycles() throws Exception {
        AtomicReference<TableView<String>> tableRef = new AtomicReference<>();
        AtomicReference<Region> drawerRef = new AtomicReference<>();
        AtomicReference<SplitPane> splitRef = new AtomicReference<>();

        fx(() -> {
            TableView<String> table = sampleTable();
            VBox drawer = new VBox(new Label("Invoice Details"), new Label("Amount and customer details"));
            drawer.setMinWidth(300);
            drawer.setPrefWidth(340);
            drawer.setMaxWidth(380);
            drawer.setManaged(false);
            drawer.setVisible(false);

            SplitPane split = new SplitPane(table, drawer);
            VBox.setVgrow(split, Priority.ALWAYS);
            VBox root = new VBox(split);
            VBox.setVgrow(split, Priority.ALWAYS);
            RegisterUiSupport.hideDrawer(drawer, split, table);

            // Deliberately enhance before Scene attachment: this is how NavigationManager
            // invokes the global enhancer for cached business pages.
            ProfessionalUiEnhancer.enhance(root);

            stage = new Stage();
            stage.setScene(new Scene(root, 1200, 720));
            stage.show();

            tableRef.set(table);
            drawerRef.set(drawer);
            splitRef.set(split);
            return null;
        });
        settle(10);

        double baseline = fx(() -> tableRef.get().getWidth());
        double baselineColumnSum = fx(() -> visibleColumnWidth(tableRef.get()));
        assertTrue(baseline > 850, "closed-drawer table must own the wide workspace; width=" + baseline);

        for (int cycle = 0; cycle < 12; cycle++) {
            fx(() -> {
                RegisterUiSupport.showDrawer(drawerRef.get(), splitRef.get(), 0.74);
                return null;
            });
            settle(7);
            double narrowed = fx(() -> tableRef.get().getWidth());
            assertTrue(narrowed < baseline - 150, "drawer must actually narrow the table viewport");

            fx(() -> {
                RegisterUiSupport.hideDrawer(drawerRef.get(), splitRef.get(), tableRef.get());
                return null;
            });
            settle(7);

            double restored = fx(() -> tableRef.get().getWidth());
            double restoredColumnSum = fx(() -> visibleColumnWidth(tableRef.get()));
            assertEquals(baseline, restored, 2.0,
                    "closing the drawer must restore the exact table viewport");
            assertEquals(baselineColumnSum, restoredColumnSum, 3.0,
                    "stale narrow geometry must not win after drawer close");
        }

        assertEquals(Boolean.TRUE,
                fx(() -> tableRef.get().getProperties().get("erp.table.dynamic-layout.installed")),
                "the central table manager must own the table");
    }

    @Test
    void hiddenTabAndScrollPaneReceiveCentralTableAndKpiEnhancement() throws Exception {
        AtomicReference<TabPane> tabsRef = new AtomicReference<>();
        AtomicReference<GridPane> kpiRef = new AtomicReference<>();
        AtomicReference<List<Region>> cardsRef = new AtomicReference<>();
        AtomicReference<TableView<String>> tableRef = new AtomicReference<>();

        fx(() -> {
            GridPane kpis = new GridPane();
            kpis.setHgap(10);
            kpis.getStyleClass().add("erp-kpi-section");
            List<Region> cards = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                VBox card = new VBox(new Label("KPI " + i), new Label("₹ " + (i * 1000)));
                card.getStyleClass().add("report-kpi-card");
                cards.add(card);
                kpis.add(card, i - 1, 0);
            }

            TableView<String> table = sampleTable();
            VBox hiddenContent = new VBox(10, kpis, table);
            VBox.setVgrow(table, Priority.ALWAYS);
            ScrollPane scroll = new ScrollPane(hiddenContent);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);

            TabPane tabs = new TabPane();
            tabs.getTabs().addAll(
                    new Tab("Dashboard", new VBox(new Label("Dashboard"))),
                    new Tab("Scheduled", scroll)
            );
            tabs.getSelectionModel().select(0);

            // Same pre-attachment lifecycle used by cached Reports pages.
            ProfessionalUiEnhancer.enhance(tabs);

            stage = new Stage();
            stage.setScene(new Scene(tabs, 1200, 720));
            stage.show();

            tabsRef.set(tabs);
            kpiRef.set(kpis);
            cardsRef.set(cards);
            tableRef.set(table);
            return null;
        });
        settle(8);

        // Activate the previously hidden content repeatedly to exercise the logical
        // TabPane/ScrollPane discovery path and settled viewport coordinator.
        for (int i = 0; i < 8; i++) {
            int selected = i % 2 == 0 ? 1 : 0;
            fx(() -> {
                tabsRef.get().getSelectionModel().select(selected);
                return null;
            });
            settle(5);
        }
        fx(() -> {
            tabsRef.get().getSelectionModel().select(1);
            return null;
        });
        settle(10);

        assertEquals(Boolean.TRUE,
                fx(() -> kpiRef.get().getProperties().get("erp.kpi.layout.installed")),
                "hidden KPI content must receive the central manager");
        assertEquals(Boolean.TRUE,
                fx(() -> tableRef.get().getProperties().get("erp.table.dynamic-layout.installed")),
                "hidden table content must receive the central manager");

        List<Double> widths = fx(() -> cardsRef.get().stream().map(Region::getWidth).toList());
        double min = widths.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = widths.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        assertTrue(min > 120, "six KPI cards must not collapse on a 1200px viewport: " + widths);
        assertTrue(max - min <= 2.0, "KPI cards must share the row equally: " + widths);

        double edgeGap = fx(() -> {
            Bounds grid = kpiRef.get().localToScene(kpiRef.get().getBoundsInLocal());
            Region last = cardsRef.get().getLast();
            Bounds right = last.localToScene(last.getBoundsInLocal());
            return Math.abs(grid.getMaxX() - right.getMaxX());
        });
        assertTrue(edgeGap <= 3.0, "rightmost KPI must fill the available KPI row; gap=" + edgeGap);

        double tableWidth = fx(() -> tableRef.get().getWidth());
        double columnWidth = fx(() -> visibleColumnWidth(tableRef.get()));
        assertTrue(tableWidth > 900, "hidden-tab table must receive its real visible viewport");
        assertTrue(columnWidth > tableWidth * 0.90,
                "columns must expand into the real visible viewport after tab activation");

        // Semantic header decoration is part of the same global enhancement pass.
        assertNotNull(fx(() -> tableRef.get().getColumns().get(0).getGraphic()),
                "table headers in hidden tabs must receive semantic graphics");
    }

    private static TableView<String> sampleTable() {
        TableView<String> table = new TableView<>();
        String[] headings = {"Invoice No.", "Date", "Customer", "Mobile", "GSTIN", "Amount",
                "Paid", "Pending", "Status", "Actions"};
        for (String heading : headings) {
            TableColumn<String, String> column = new TableColumn<>(heading);
            column.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue()));
            table.getColumns().add(column);
        }
        for (int i = 0; i < 29; i++) {
            table.getItems().add("JI/2026-27/" + (100 + i) + " • SECURE INNOVATIVE • ₹ 405,920.00");
        }
        table.getStyleClass().add("erp-table");
        return table;
    }

    private static double visibleColumnWidth(TableView<?> table) {
        return table.getColumns().stream().filter(TableColumn::isVisible).mapToDouble(TableColumn::getWidth).sum();
    }

    private static void settle(int passes) throws Exception {
        for (int i = 0; i < passes; i++) {
            fx(() -> null);
            Thread.sleep(18L);
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure.get() != null) {
            Throwable t = failure.get();
            if (t instanceof Exception e) throw e;
            if (t instanceof Error e) throw e;
            throw new RuntimeException(t);
        }
        return result.get();
    }
}
