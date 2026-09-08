package org.example.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Single runtime width authority for every KPI container marked with
 * {@code erp-kpi-section}. Cards always stay in one row; density is selected
 * from the real usable pixels per visible card rather than from card count
 * alone, so the same screen behaves correctly at 1366px and large desktops.
 *
 * <p>This class owns KPI geometry only. Colours, icons, values, navigation and
 * business state remain unchanged.</p>
 */
public final class ResponsiveKpiLayoutManager {
    public static final String KPI_SECTION_STYLE = "erp-kpi-section";
    private static final String INSTALLED = "erp.kpi.layout.installed";
    private static final String PENDING = "erp.kpi.layout.pending";
    private static final String GENERATION = "erp.kpi.layout.generation";
    private static final String ORIGINAL_COLUMN = "erp.kpi.original.column";
    private static final double COMPACT_CARD_THRESHOLD = 225.0;
    private static final double DENSE_CARD_THRESHOLD = 170.0;

    private ResponsiveKpiLayoutManager() { }

    /** Installs responsive balancing when the supplied node is a marked KPI container. */
    public static void install(Node node) {
        if (!(node instanceof Pane pane) || !pane.getStyleClass().contains(KPI_SECTION_STYLE)) return;
        prepareFlexibleContainer(pane);
        if (Boolean.TRUE.equals(pane.getProperties().get(INSTALLED))) {
            requestRebalance(pane);
            return;
        }
        pane.getProperties().put(INSTALLED, true);

        for (Node child : pane.getChildrenUnmodifiable()) attachCardListener(pane, child);
        pane.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Node child : change.getAddedSubList()) attachCardListener(pane, child);
                }
            }
            requestRebalance(pane);
        });
        pane.widthProperty().addListener((obs, oldValue, newValue) -> requestRebalance(pane));
        pane.sceneProperty().addListener((obs, oldValue, newValue) -> requestRebalance(pane));
        pane.visibleProperty().addListener((obs, oldValue, newValue) -> { if (newValue) requestRebalance(pane); });
        pane.managedProperty().addListener((obs, oldValue, newValue) -> { if (newValue) requestRebalance(pane); });

        requestRebalance(pane);
    }

    /** Rebalances every marked KPI container under a changed viewport. */
    public static void requestLayoutIn(Node root) {
        if (root == null) return;
        Runnable pass = () -> {
            try {
                if (root instanceof Pane pane && pane.getStyleClass().contains(KPI_SECTION_STYLE)) requestRebalance(pane);
                for (Node node : root.lookupAll("." + KPI_SECTION_STYLE)) {
                    if (node instanceof Pane pane) requestRebalance(pane);
                }
            } catch (RuntimeException ignored) { }
        };
        if (Platform.isFxApplicationThread()) pass.run();
        else Platform.runLater(pass);
    }

    private static void attachCardListener(Pane pane, Node child) {
        if (child == null) return;
        if (pane instanceof GridPane grid && !child.getProperties().containsKey(ORIGINAL_COLUMN)) {
            Integer explicit = GridPane.getColumnIndex(child);
            int sourceOrder = grid.getChildrenUnmodifiable().indexOf(child);
            child.getProperties().put(ORIGINAL_COLUMN, explicit == null ? Math.max(0, sourceOrder) : explicit);
        }
        String key = "erp.kpi.layout.listener." + System.identityHashCode(pane);
        if (Boolean.TRUE.equals(child.getProperties().get(key))) return;
        child.getProperties().put(key, true);
        child.managedProperty().addListener((obs, oldValue, newValue) -> requestRebalance(pane));
        child.visibleProperty().addListener((obs, oldValue, newValue) -> requestRebalance(pane));
    }

    private static void requestRebalance(Pane pane) {
        if (pane == null) return;
        long generation = generation(pane) + 1L;
        pane.getProperties().put(GENERATION, generation);
        if (Boolean.TRUE.equals(pane.getProperties().get(PENDING))) return;
        pane.getProperties().put(PENDING, true);
        Platform.runLater(() -> runScheduledRebalance(pane));
    }

    private static void runScheduledRebalance(Pane pane) {
        if (pane == null) return;
        long observed = generation(pane);
        rebalance(pane);
        if (generation(pane) != observed) {
            Platform.runLater(() -> runScheduledRebalance(pane));
            return;
        }
        pane.getProperties().remove(PENDING);
    }

    private static long generation(Pane pane) {
        Object value = pane.getProperties().get(GENERATION);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static void rebalance(Pane pane) {
        if (pane instanceof HBox row) {
            rebalanceHBox(row);
        } else if (pane instanceof GridPane grid) {
            rebalanceGrid(grid);
        } else if (pane instanceof FlowPane flow) {
            rebalanceFlow(flow);
        }
    }

    private static void rebalanceHBox(HBox row) {
        List<Node> cards = managedCards(row);
        applyDensity(row, cards.size(), row.getSpacing());
        for (Node card : cards) {
            HBox.setHgrow(card, Priority.ALWAYS);
            markCard(card);
            prepareFlexibleCard(card);
        }
    }

    private static void rebalanceGrid(GridPane grid) {
        List<Node> cards = managedCards(grid);
        cards.sort(Comparator.comparingInt(ResponsiveKpiLayoutManager::stableColumnIndex));
        applyDensity(grid, cards.size(), grid.getHgap());

        grid.getColumnConstraints().clear();
        if (cards.isEmpty()) return;

        int columns = cards.size();
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setMinWidth(0);
            column.setPrefWidth(0);
            column.setMaxWidth(Double.MAX_VALUE);
            column.setPercentWidth(100.0d / columns);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }

        for (int i = 0; i < cards.size(); i++) {
            Node card = cards.get(i);
            GridPane.setColumnIndex(card, i);
            GridPane.setRowIndex(card, 0);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
            markCard(card);
            prepareGridCard(card);
        }
    }

    private static void rebalanceFlow(FlowPane flow) {
        List<Node> cards = managedCards(flow);
        applyDensity(flow, cards.size(), flow.getHgap());
        if (cards.isEmpty()) return;
        double width = usableWidth(flow);
        if (!Double.isFinite(width) || width <= 80) return;
        int columns = cards.size();
        double cardWidth = Math.max(0, (width - Math.max(0, columns - 1) * flow.getHgap()) / columns);
        flow.setPrefWrapLength(Double.MAX_VALUE);
        for (Node card : cards) {
            markCard(card);
            if (card instanceof Region region) {
                region.setMinWidth(0);
                region.setPrefWidth(cardWidth);
                region.setMaxWidth(cardWidth);
            }
        }
    }

    private static void applyDensity(Pane pane, int count, double gap) {
        pane.getStyleClass().removeAll("erp-kpi-density-normal", "erp-kpi-density-compact", "erp-kpi-density-dense");
        String density = densityFor(pane, count, gap);
        pane.getStyleClass().add(density);
        if (!pane.getStyleClass().contains("erp-kpi-single-row")) pane.getStyleClass().add("erp-kpi-single-row");
    }

    private static String densityFor(Pane pane, int count, double gap) {
        if (count <= 0) return "erp-kpi-density-normal";
        double width = usableWidth(pane);
        if (Double.isFinite(width) && width > 80) {
            double perCard = Math.max(0, (width - Math.max(0, count - 1) * gap) / count);
            if (perCard < DENSE_CARD_THRESHOLD) return "erp-kpi-density-dense";
            if (perCard < COMPACT_CARD_THRESHOLD) return "erp-kpi-density-compact";
            return "erp-kpi-density-normal";
        }
        // Pre-layout fallback only; live width becomes authoritative as soon as
        // the page/tab is attached to a scene.
        return count >= 7 ? "erp-kpi-density-dense" : count >= 5 ? "erp-kpi-density-compact" : "erp-kpi-density-normal";
    }

    private static double usableWidth(Pane pane) {
        if (!(pane instanceof Region region)) return pane.getLayoutBounds().getWidth();
        double width = region.getWidth();
        Insets insets = region.getInsets();
        return Math.max(0, width - insets.getLeft() - insets.getRight());
    }

    private static void markCard(Node card) {
        if (card != null && !card.getStyleClass().contains("erp-kpi-card")) card.getStyleClass().add("erp-kpi-card");
    }

    private static List<Node> managedCards(Pane pane) {
        List<Node> cards = new ArrayList<>();
        for (Node child : pane.getChildrenUnmodifiable()) {
            if (child != null && child.isManaged() && child.isVisible()) cards.add(child);
        }
        return cards;
    }

    private static void prepareGridCard(Node card) {
        if (card instanceof Region region) {
            region.setMinWidth(0);
            // USE_COMPUTED_SIZE is a safe fallback if a GridPane is observed
            // before its percentage constraints are installed; it avoids the
            // zero-width collapse previously visible in hidden Reports tabs.
            region.setPrefWidth(Region.USE_COMPUTED_SIZE);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static void prepareFlexibleContainer(Pane pane) {
        if (pane instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static void prepareFlexibleCard(Node card) {
        if (card instanceof Region region) {
            region.setMinWidth(0);
            region.setPrefWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static int stableColumnIndex(Node card) {
        Object original = card.getProperties().get(ORIGINAL_COLUMN);
        if (original instanceof Number number) return number.intValue();
        return columnIndex(card);
    }

    private static int columnIndex(Node card) {
        Integer index = GridPane.getColumnIndex(card);
        return index == null ? 0 : index;
    }
}
