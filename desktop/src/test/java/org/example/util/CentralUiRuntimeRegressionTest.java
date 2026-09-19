package org.example.util;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.css.PseudoClass;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
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


    @Test
    void semanticDecorationReachesScrollPaneAndHiddenTabLogicalContent() throws Exception {
        AtomicReference<Label> scrollLabelRef = new AtomicReference<>();
        AtomicReference<Label> tabLabelRef = new AtomicReference<>();

        fx(() -> {
            Label scrollLabel = new Label("Item Code *");
            scrollLabel.getStyleClass().add("field-label");
            ScrollPane scroll = new ScrollPane(new VBox(scrollLabel));

            Label tabLabel = new Label("Selling Price");
            tabLabel.getStyleClass().add("field-label");
            TabPane tabs = new TabPane(new Tab("Visible", new VBox(new Label("Visible"))),
                    new Tab("Hidden", new VBox(tabLabel)));
            tabs.getSelectionModel().select(0);

            VBox root = new VBox(scroll, tabs);
            ProfessionalUiEnhancer.enhance(root);
            stage = new Stage();
            stage.setScene(new Scene(root, 900, 600));
            stage.show();
            scrollLabelRef.set(scrollLabel);
            tabLabelRef.set(tabLabel);
            return null;
        });
        settle(5);

        for (Label label : List.of(scrollLabelRef.get(), tabLabelRef.get())) {
            assertNotNull(fx(label::getGraphic),
                    "semantic field icon must reach JavaFX logical content before/after skin attachment");
            assertTrue(fx(() -> label.getStyleClass().stream().anyMatch(x -> x.startsWith("erp-field-label-colour-"))),
                    "semantic field colour class must reach JavaFX logical content");
        }
    }


    @Test
    void shellIconGeometryAndSemanticTableHeaderColoursRemainVisibleAcrossThemes() throws Exception {
        AtomicReference<List<Button>> iconButtonsRef = new AtomicReference<>();
        AtomicReference<ToggleButton> themeButtonRef = new AtomicReference<>();
        AtomicReference<TableView<String>> tableRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();

        fx(() -> {
            Button menu = shellIconButton("menu");
            Button reminder = shellIconButton("reminder");
            Button notification = shellIconButton("notification");
            Button email = shellIconButton("email");
            Button whatsapp = shellIconButton("whatsapp");
            Button shortcut = shellIconButton("shortcut");
            ToggleButton theme = new ToggleButton("Light");
            theme.getStyleClass().add("theme-switch");
            UiActionIcons.apply(theme, "sun", "Light theme");

            HBox top = new HBox(10, menu, reminder, notification, email, whatsapp, shortcut, theme);
            top.getStyleClass().add("erp-topbar");
            TableView<String> table = sampleTable();
            VBox root = new VBox(12, top, table);
            root.getStyleClass().add("erp-ui-standard");
            VBox.setVgrow(table, Priority.ALWAYS);
            ProfessionalUiEnhancer.enhance(root);

            stage = new Stage();
            Scene scene = new Scene(root, 1280, 720);
            stage.setScene(scene);
            scene.getStylesheets().setAll(ResourceLocator.require("/css/light-theme.css").toExternalForm());
            stage.show();

            iconButtonsRef.set(List.of(menu, reminder, notification, email, whatsapp, shortcut));
            themeButtonRef.set(theme);
            tableRef.set(table);
            sceneRef.set(scene);
            return null;
        });
        settle(10);

        assertShellGraphicsFit(iconButtonsRef.get(), themeButtonRef.get());
        assertHeaderColourFamilies(tableRef.get(), 5);

        fx(() -> {
            sceneRef.get().getStylesheets().setAll(ResourceLocator.require("/css/dark-theme.css").toExternalForm());
            return null;
        });
        settle(6);
        assertShellGraphicsFit(iconButtonsRef.get(), themeButtonRef.get());
        assertHeaderColourFamilies(tableRef.get(), 5);
    }


    @Test
    void labeledAuditViewActionKeepsEyeAndTextVisibleAndFitsItsColumn() throws Exception {
        AtomicReference<TableView<String>> tableRef = new AtomicReference<>();
        AtomicReference<Button> buttonRef = new AtomicReference<>();
        AtomicReference<TableColumn<String, Void>> columnRef = new AtomicReference<>();

        fx(() -> {
            TableView<String> table = new TableView<>();
            TableColumn<String, String> ref = new TableColumn<>("Reference");
            ref.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue()));
            TableColumn<String, Void> view = new TableColumn<>("View");
            view.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
                final Button b = new Button();
                {
                    UiActionIcons.applyLabeledTableAction(b, "View", "view", "View audit details");
                    buttonRef.set(b);
                }
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : b);
                }
            });
            table.getColumns().setAll(ref, view);
            table.getItems().setAll("JI/25-2026/0110");
            ProfessionalUiEnhancer.enhance(table);
            DynamicTableLayoutManager.install(table);
            VBox root = new VBox(table);
            VBox.setVgrow(table, Priority.ALWAYS);
            stage = new Stage();
            Scene scene = new Scene(root, 720, 320);
            scene.getStylesheets().setAll(ResourceLocator.require("/css/light-theme.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            tableRef.set(table);
            columnRef.set(view);
            return null;
        });
        settle(10);

        Button button = fx(() -> tableRef.get().lookupAll(".table-action-button").stream()
                .filter(Node::isVisible)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .findFirst().orElse(null));
        assertNotNull(button, "Visible Audit View action must be realized");
        assertEquals("View", fx(button::getText), "Audit action must retain visible View label");
        assertNotNull(fx(button::getGraphic), "Audit action must retain eye icon");
        double buttonWidth = fx(button::getWidth);
        double prefWidth = fx(() -> button.prefWidth(-1));
        assertTrue(buttonWidth > 55, "Audit View icon+label must have usable width; width=" + buttonWidth + " pref=" + prefWidth);
        assertTrue(fx(columnRef.get()::getWidth) + 0.5 >= buttonWidth,
                "Dynamic table layout must measure direct action controls so View does not clip");
    }

    @Test
    void ownerThemeResendHoverAndReminderDrawerRemainStableInRealJavaFx() throws Exception {
        AtomicReference<Scene> ownerSceneRef = new AtomicReference<>();
        AtomicReference<Dialog<Void>> dialogRef = new AtomicReference<>();
        AtomicReference<String> themeAtShowing = new AtomicReference<>();
        AtomicReference<Button> resendRef = new AtomicReference<>();
        AtomicReference<VBox> drawerRef = new AtomicReference<>();
        AtomicReference<Button> editRef = new AtomicReference<>();
        AtomicReference<Button> completeRef = new AtomicReference<>();

        fx(() -> {
            Button ownerAnchor = new Button("Owner");
            Button resend = new Button("Re-send");
            resend.getStyleClass().addAll("approved-button", "approved-secondary-button", "communication-resend-button");
            UiActionIcons.apply(resend, "refresh", "Resend email");

            TableView<String> table = sampleTable();
            Button edit = new Button("Edit Reminder");
            Button complete = new Button("Mark Complete");
            edit.setMaxWidth(Double.MAX_VALUE);
            complete.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(edit, Priority.ALWAYS);
            HBox.setHgrow(complete, Priority.ALWAYS);
            HBox actionRow = new HBox(9, edit, complete);
            VBox drawer = new VBox(12, new Label("Reminder Details"),
                    new Label("REF-2026-000123456789"), new Label("High priority customer follow-up"), actionRow);
            drawer.getStyleClass().addAll("erp-detail-drawer-card", "reminder-detail-panel");
            drawer.setMinWidth(360); drawer.setPrefWidth(390); drawer.setMaxWidth(430);
            drawer.setManaged(false); drawer.setVisible(false);

            SplitPane split = new SplitPane(table, drawer);
            VBox root = new VBox(10, ownerAnchor, resend, split);
            root.getStyleClass().add("erp-ui-standard");
            VBox.setVgrow(split, Priority.ALWAYS);

            stage = new Stage();
            Scene ownerScene = new Scene(root, 1280, 760);
            ownerScene.getStylesheets().setAll(ResourceLocator.require("/css/light-theme.css").toExternalForm());
            stage.setScene(ownerScene);
            stage.show();
            RegisterUiSupport.showDrawer(drawer, split, 0.64);

            OwnedDialog<Void> dialog = new OwnedDialog<>(ownerAnchor);
            dialog.setTitle("Audit Trail");
            dialog.getDialogPane().setContent(new VBox(new Label("Audit Trail"), new Label("Record change details")));
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, event -> {
                Scene scene = dialog.getDialogPane().getScene();
                if (scene != null && !scene.getStylesheets().isEmpty()) themeAtShowing.set(scene.getStylesheets().getFirst());
            });
            dialog.show();

            ownerSceneRef.set(ownerScene);
            dialogRef.set(dialog);
            resendRef.set(resend);
            drawerRef.set(drawer);
            editRef.set(edit);
            completeRef.set(complete);
            return null;
        });
        settle(8);

        String lightUrl = ResourceLocator.require("/css/light-theme.css").toExternalForm();
        assertEquals(lightUrl, themeAtShowing.get(), "record audit dialog must inherit owner theme before first visible frame");
        assertEquals(lightUrl, fx(() -> dialogRef.get().getDialogPane().getScene().getStylesheets().getFirst()),
                "record audit dialog theme must not snap after showing");

        String baseLight = fx(() -> backgroundColour(resendRef.get()));
        assertTrue(!"0x16a34aff".equalsIgnoreCase(baseLight), "Re-send must not use the legacy dark green override");
        fx(() -> {
            resendRef.get().pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
            resendRef.get().applyCss();
            return null;
        });
        settle(2);
        String hoverLight = fx(() -> backgroundColour(resendRef.get()));
        assertTrue(!"0x16a34aff".equalsIgnoreCase(hoverLight), "Re-send hover must remain under approved secondary styling");

        assertTrue(fx(() -> drawerRef.get().getWidth()) >= 350, "Reminder detail drawer must not collapse below readable width");
        assertTrue(fx(() -> editRef.get().getWidth()) >= 130, "Edit Reminder action must not clip");
        assertTrue(fx(() -> completeRef.get().getWidth()) >= 130, "Mark Complete action must not clip");

        fx(() -> {
            dialogRef.get().close();
            ownerSceneRef.get().getStylesheets().setAll(ResourceLocator.require("/css/dark-theme.css").toExternalForm());
            resendRef.get().pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), false);
            return null;
        });
        settle(4);
        String baseDark = fx(() -> backgroundColour(resendRef.get()));
        assertTrue(!"0x16a34aff".equalsIgnoreCase(baseDark), "Dark theme Re-send must not resurrect the legacy green override");
    }

    private static String backgroundColour(Button button) {
        if (button.getBackground() == null || button.getBackground().getFills().isEmpty()) return "";
        return String.valueOf(button.getBackground().getFills().getFirst().getFill());
    }

    private static Button shellIconButton(String semantic) {
        Button button = new Button();
        button.getStyleClass().addAll("top-icon", "approved-button", "approved-secondary-button", "approved-icon-button");
        UiActionIcons.apply(button, semantic, semantic);
        return button;
    }

    private static void assertShellGraphicsFit(List<Button> buttons, ToggleButton theme) throws Exception {
        for (Button button : buttons) {
            assertNotNull(fx(button::getGraphic), "shell icon graphic must exist");
            assertTrue(fx(() -> button.getGraphic().isVisible()), "shell icon graphic must be visible");
            double graphicWidth = fx(() -> button.getGraphic().getLayoutBounds().getWidth());
            double buttonWidth = fx(button::getWidth);
            assertTrue(graphicWidth > 8, "shell icon graphic must have rendered width");
            assertTrue(graphicWidth <= buttonWidth - 2,
                    "shell icon graphic must fit the 44px control: graphic=" + graphicWidth + " button=" + buttonWidth);
        }
        assertNotNull(fx(theme::getGraphic), "theme switch icon must exist");
        double themeGraphic = fx(() -> theme.getGraphic().getLayoutBounds().getWidth());
        assertTrue(themeGraphic > 8 && themeGraphic < theme.getWidth() - 20,
                "theme glyph must remain visible beside Light/Dark text");
    }

    private static void assertHeaderColourFamilies(TableView<?> table, int minimumDistinct) throws Exception {
        java.util.Set<String> colours = fx(() -> {
            java.util.Set<String> found = new java.util.LinkedHashSet<>();
            for (TableColumn<?, ?> column : table.getVisibleLeafColumns()) {
                FontIcon icon = findFontIcon(column.getGraphic());
                if (icon != null && icon.getIconColor() != null) found.add(icon.getIconColor().toString());
            }
            return found;
        });
        assertTrue(colours.size() >= minimumDistinct,
                "semantic table headers must not collapse to one blue family; colours=" + colours);
    }

    private static FontIcon findFontIcon(Node node) {
        if (node instanceof FontIcon icon) return icon;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                FontIcon found = findFontIcon(child);
                if (found != null) return found;
            }
        }
        return null;
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
