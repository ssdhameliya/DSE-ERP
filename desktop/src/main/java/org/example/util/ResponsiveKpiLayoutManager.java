package org.example.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
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
 * Phase 4 responsive KPI layout authority.
 *
 * <p>Any KPI container explicitly marked with the {@code erp-kpi-section}
 * style class is balanced from its currently managed cards. HBox rows give
 * every managed card the same growth basis; single-row GridPane KPI bands get
 * one percentage column per managed card ({@code 100 / cardCount}). This keeps
 * the existing card styling/content intact while allowing cards to be added,
 * removed, shown or hidden without screen-specific width code.</p>
 *
 * <p>This class owns KPI width distribution only. It does not change colours,
 * icons, card content, event handlers, navigation or any business state.</p>
 */
public final class ResponsiveKpiLayoutManager {
    public static final String KPI_SECTION_STYLE = "erp-kpi-section";
    private static final String INSTALLED = "erp.kpi.layout.installed";
    private static final String PENDING = "erp.kpi.layout.pending";
    private static final String ORIGINAL_COLUMN = "erp.kpi.original.column";
    private static final double MIN_COMFORTABLE_CARD = 170.0;

    private ResponsiveKpiLayoutManager() {}

    /** Installs responsive balancing when the supplied node is a marked KPI container. */
    public static void install(Node node) {
        if (!(node instanceof Pane pane) || !pane.getStyleClass().contains(KPI_SECTION_STYLE)) return;
        prepareFlexibleContainer(pane);
        if (Boolean.TRUE.equals(pane.getProperties().get(INSTALLED))) {
            rebalance(pane);
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

        rebalance(pane);
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
    }

    private static void requestRebalance(Pane pane) {
        if (pane == null) return;
        if (Platform.isFxApplicationThread() && pane.getWidth() > 80) {
            pane.getProperties().remove(PENDING);
            rebalance(pane);
            return;
        }
        if (Boolean.TRUE.equals(pane.getProperties().get(PENDING))) return;
        pane.getProperties().put(PENDING, true);
        Platform.runLater(() -> {
            pane.getProperties().remove(PENDING);
            rebalance(pane);
        });
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
        applyDensity(row, cards.size());
        for (Node card : cards) {
            HBox.setHgrow(card, Priority.ALWAYS);
            markCard(card);
            prepareFlexibleCard(card);
        }
    }

    private static void rebalanceGrid(GridPane grid) {
        List<Node> cards = managedCards(grid);
        cards.sort(Comparator.comparingInt(ResponsiveKpiLayoutManager::stableColumnIndex));
        applyDensity(grid, cards.size());

        grid.getColumnConstraints().clear();
        if (cards.isEmpty()) return;

        // 9.0.79 UI contract: KPI sections NEVER wrap. One managed card = one column.
        int columns = cards.size();
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints();
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
            markCard(card);
            prepareGridCard(card);
        }
    }

    private static void rebalanceFlow(FlowPane flow) {
        List<Node> cards = managedCards(flow);
        applyDensity(flow, cards.size());
        if (cards.isEmpty()) return;
        double width = flow.getWidth();
        if (!Double.isFinite(width) || width <= 80) return;
        int columns = cards.size();
        double cardWidth = Math.max(0, (width - Math.max(0, columns - 1) * flow.getHgap()) / columns);
        // Equal widths guarantee a single horizontal row. FlowPane wrapping is only a
        // final layout fallback when a caller forces a width below the supported desktop minimum.
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

    private static void applyDensity(Pane pane, int count) {
        pane.getStyleClass().removeAll("erp-kpi-density-normal", "erp-kpi-density-compact", "erp-kpi-density-dense");
        String density = count >= 7 ? "erp-kpi-density-dense" : count >= 5 ? "erp-kpi-density-compact" : "erp-kpi-density-normal";
        pane.getStyleClass().add(density);
        if (!pane.getStyleClass().contains("erp-kpi-single-row")) pane.getStyleClass().add("erp-kpi-single-row");
    }

    private static void markCard(Node card) {
        if (card != null && !card.getStyleClass().contains("erp-kpi-card")) card.getStyleClass().add("erp-kpi-card");
    }

    private static int responsiveColumnCount(int cardCount, double width, double gap) {
        if (cardCount <= 1) return Math.max(1, cardCount);
        int fit = cardCount;
        if (Double.isFinite(width) && width > 80) {
            fit = Math.max(1, Math.min(cardCount, (int) Math.floor((width + gap) / (MIN_COMFORTABLE_CARD + gap))));
        }
        if (fit <= 1 || fit == cardCount) return fit;
        // Prefer a full final row when a near-by column count divides evenly.
        for (int columns = fit; columns >= 2; columns--) {
            if (cardCount % columns == 0) return columns;
        }
        // Otherwise avoid a visually orphaned single card on the last row.
        for (int columns = fit; columns >= 2; columns--) {
            int remainder = cardCount % columns;
            if (remainder == 0 || remainder >= (int) Math.ceil(columns / 2.0)) return columns;
        }
        return Math.min(fit, 2);
    }

    private static List<Node> managedCards(Pane pane) {
        List<Node> cards = new ArrayList<>();
        for (Node child : pane.getChildrenUnmodifiable()) {
            if (child != null && child.isManaged()) cards.add(child);
        }
        return cards;
    }

    private static void prepareGridCard(Node card) {
        if (card instanceof Region region) {
            // Percentage columns own width. Preserve the card's computed preferred width so
            // the first layout pulse cannot collapse the grid and temporarily create extra rows.
            region.setMinWidth(0);
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
