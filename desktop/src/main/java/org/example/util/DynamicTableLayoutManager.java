package org.example.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Phase 5 single authority for ERP TableView column widths.
 *
 * <p>FXML, controllers, CSS and saved-view preferences no longer own column
 * widths. This manager measures each visible leaf column from its rendered
 * semantic header (icon + text), a representative sample of current row data,
 * and the width currently available to the TableView. When all natural widths
 * fit, remaining space is distributed by business-content flex weight. When
 * they do not fit, columns shrink only to readable header/content minima and
 * JavaFX horizontal scrolling is allowed rather than crushing text.</p>
 *
 * <p>The manager owns width only. It does not change sorting, selection,
 * editing, row actions, column visibility/order, cell factories, navigation,
 * filtering, paging or business state.</p>
 */
public final class DynamicTableLayoutManager {
    private static final String INSTALLED = "erp.table.dynamic-layout.installed";
    private static final String PENDING = "erp.table.dynamic-layout.pending";
    private static final String GENERATION = "erp.table.dynamic-layout.generation";
    private static final String VIEWPORT_NODES = "erp.table.dynamic-layout.viewport-nodes";
    private static final String ITEM_LISTENER = "erp.table.dynamic-layout.item-listener";
    private static final String COLUMN_LISTENER = "erp.table.dynamic-layout.column-listener";
    private static final String COLUMN_BOUND = "erp.table.dynamic-layout.column-bound";
    private static final String NATURAL_FLOOR = "erp.table.dynamic-layout.natural-floor";
    private static final String RENDERED_ACTION_WIDTH = "erp.table.dynamic-layout.rendered-action-width";
    private static final String SAMPLED_CONTENT_WIDTH = "erp.table.dynamic-layout.sampled-content-width";
    private static final double ACTION_CONTROL_MIN_WIDTH = 132.0;
    private static final double DENSE_ACTION_MIN_WIDTH = 118.0;
    private static final int SAMPLE_LIMIT = 48;
    private static final double CELL_HORIZONTAL_PADDING = 24.0;
    private static final double HEADER_HORIZONTAL_PADDING = 18.0;
    private static final double MIN_READABLE_COLUMN = 58.0;
    private static final double DENSE_MIN_READABLE_COLUMN = 44.0;
    private static final double MAX_EXPECTED_SKIN_CHROME = 36.0;
    private static final double VIEWPORT_STABILITY_TOLERANCE = 1.0;

    private DynamicTableLayoutManager() {}

    /** Installs the dynamic width authority once and immediately schedules sizing. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void install(TableView<?> table) {
        if (table == null) return;
        if (Boolean.TRUE.equals(table.getProperties().get(INSTALLED))) {
            requestLayout(table);
            return;
        }
        table.getProperties().put(INSTALLED, true);

        // DynamicTableLayoutManager owns leaf widths. Unconstrained policy lets
        // the measured widths stand and naturally exposes horizontal scrolling
        // when the readable minimums cannot fit the current viewport.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        bindColumns(table);
        bindItems(table, null, table.getItems());

        table.widthProperty().addListener((obs, oldValue, newValue) -> requestLayout(table));
        table.itemsProperty().addListener((obs, oldItems, newItems) -> bindItems(table, oldItems, newItems));
        table.sceneProperty().addListener((obs, oldScene, newScene) -> {
            bindViewportNodes(table);
            requestLayout(table);
        });
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            bindViewportNodes(table);
            requestLayout(table);
        });
        // TabPane/cached content often receives its usable width only when it is
        // activated. The generation scheduler below makes these transitions safe
        // even when several JavaFX layout pulses are emitted in quick succession.
        table.visibleProperty().addListener((obs, oldValue, newValue) -> { if (newValue) requestLayout(table); });
        table.managedProperty().addListener((obs, oldValue, newValue) -> { if (newValue) requestLayout(table); });

        if (!Boolean.TRUE.equals(table.getProperties().get(COLUMN_LISTENER))) {
            table.getProperties().put(COLUMN_LISTENER, true);
            table.getColumns().addListener((ListChangeListener<TableColumn>) change -> {
                bindColumns(table);
                requestLayout(table);
            });
        }
        bindViewportNodes(table);
        requestLayout(table);
    }

    /**
     * Public hook for drawers, tabs, navigation and data changes. Every request
     * advances a generation. A pass can therefore never commit measurements
     * taken from an older intermediate geometry after a newer width has arrived.
     */
    public static void requestLayout(TableView<?> table) {
        if (table == null) return;
        // Always coalesce width/item/skin changes into one next-pulse pass; the
        // generation counter additionally prevents a stale intermediate pass
        // from winning after a newer viewport change.
        long generation = generation(table) + 1L;
        table.getProperties().put(GENERATION, generation);
        if (Boolean.TRUE.equals(table.getProperties().get(PENDING))) return;
        table.getProperties().put(PENDING, true);
        Platform.runLater(() -> runScheduledLayout(table));
    }

    private static void runScheduledLayout(TableView<?> table) {
        if (table == null) return;
        long observed = generation(table);
        bindViewportNodes(table);
        layoutNow(table);
        if (generation(table) != observed) {
            Platform.runLater(() -> runScheduledLayout(table));
            return;
        }
        table.getProperties().remove(PENDING);
    }

    /** Reflows every TableView below a container after a viewport geometry change. */
    public static void requestLayoutIn(Node root) {
        if (root == null) return;
        Runnable pass = () -> {
            try {
                if (root instanceof TableView<?> table) requestLayout(table);
                for (Node node : root.lookupAll(".table-view")) {
                    if (node instanceof TableView<?> table) requestLayout(table);
                }
            } catch (RuntimeException ignored) { }
        };
        if (Platform.isFxApplicationThread()) pass.run();
        else Platform.runLater(pass);
    }

    private static long generation(TableView<?> table) {
        Object value = table.getProperties().get(GENERATION);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * Observe the live VirtualFlow/clipped viewport once a skin exists. This is
     * the missing lifecycle link for cached tabs and drawer close/open cycles:
     * when JavaFX updates its internal viewport one pulse after the outer
     * TableView, a fresh generation is now scheduled automatically.
     */
    @SuppressWarnings("unchecked")
    private static void bindViewportNodes(TableView<?> table) {
        if (table == null || table.getSkin() == null) return;
        IdentityHashMap<Node, Boolean> bound;
        Object existing = table.getProperties().get(VIEWPORT_NODES);
        if (existing instanceof IdentityHashMap<?, ?> map) {
            bound = (IdentityHashMap<Node, Boolean>) map;
        } else {
            bound = new IdentityHashMap<>();
            table.getProperties().put(VIEWPORT_NODES, bound);
        }
        try {
            bindViewportNode(table, table.lookup(".virtual-flow"), bound);
            bindViewportNode(table, table.lookup(".clipped-container"), bound);
            for (Node node : table.lookupAll(".scroll-bar")) bindViewportNode(table, node, bound);
        } catch (RuntimeException ignored) { }
    }

    private static void bindViewportNode(TableView<?> table, Node node, IdentityHashMap<Node, Boolean> bound) {
        if (node == null || bound.put(node, Boolean.TRUE) != null) return;
        node.layoutBoundsProperty().addListener(obs -> requestLayout(table));
        node.visibleProperty().addListener(obs -> requestLayout(table));
        if (node instanceof Region region) {
            region.widthProperty().addListener(obs -> requestLayout(table));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bindColumns(TableView<?> table) {
        for (TableColumn<?, ?> column : leafColumns(table.getColumns())) {
            prepareColumn(column);
            if (!Boolean.TRUE.equals(column.getProperties().get(COLUMN_BOUND))) {
                column.getProperties().put(COLUMN_BOUND, true);
                column.visibleProperty().addListener((obs, oldValue, newValue) -> requestLayout(table));
                column.graphicProperty().addListener((obs, oldValue, newValue) -> requestLayout(table));
                column.textProperty().addListener((obs, oldValue, newValue) -> requestLayout(table));
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bindItems(TableView<?> table, ObservableList oldItems, ObservableList newItems) {
        Object existing = table.getProperties().remove(ITEM_LISTENER);
        if (oldItems != null && existing instanceof ListChangeListener listener) oldItems.removeListener(listener);
        if (newItems != null) {
            ListChangeListener listener = change -> {
                invalidateContentMeasures(table);
                requestLayout(table);
            };
            newItems.addListener(listener);
            table.getProperties().put(ITEM_LISTENER, listener);
        }
        invalidateContentMeasures(table);
        requestLayout(table);
    }

    private static void prepareColumn(TableColumn<?, ?> column) {
        if (column == null || !column.getColumns().isEmpty()) return;
        // Clear every legacy/fxml constraint. These are bounds only; current
        // widths are calculated below from live content and viewport size.
        column.setMinWidth(0);
        column.setMaxWidth(Double.MAX_VALUE);
    }

    private static void layoutNow(TableView<?> table) {
        if (table == null) return;
        List<TableColumn<?, ?>> columns = new ArrayList<>(table.getVisibleLeafColumns());
        if (columns.isEmpty()) return;

        double available = contentViewportWidth(table);
        if (!Double.isFinite(available) || available < 80) return;

        List<ColumnMeasure> measures = new ArrayList<>(columns.size());
        double naturalTotal = 0;
        double minimumTotal = 0;
        for (TableColumn<?, ?> column : columns) {
            prepareColumn(column);
            ColumnMeasure measure = measure(table, column, available);
            measures.add(measure);
            naturalTotal += measure.natural();
            minimumTotal += measure.minimum();
        }

        double[] widths = new double[measures.size()];
        boolean denseFit = false;
        if (naturalTotal <= available) {
            double extra = available - naturalTotal;
            double weightTotal = measures.stream().mapToDouble(ColumnMeasure::flexWeight).sum();
            for (int i = 0; i < measures.size(); i++) {
                ColumnMeasure measure = measures.get(i);
                widths[i] = measure.natural() + (weightTotal <= 0 ? 0 : extra * measure.flexWeight() / weightTotal);
            }
        } else if (minimumTotal < available) {
            // Shrink only the portion above each readable minimum.
            double shrinkNeeded = naturalTotal - available;
            double shrinkable = measures.stream().mapToDouble(m -> m.natural() - m.minimum()).sum();
            for (int i = 0; i < measures.size(); i++) {
                ColumnMeasure measure = measures.get(i);
                double ownShrinkable = measure.natural() - measure.minimum();
                double shrink = shrinkable <= 0 ? 0 : shrinkNeeded * ownShrinkable / shrinkable;
                widths[i] = Math.max(measure.minimum(), measure.natural() - shrink);
            }
        } else if (fitWithVisibleActionColumn(columns, measures, widths, available)) {
            // Register/detail layouts can make the table narrower than the sum of
            // every header's ideal width. Row actions are the one control that
            // must remain directly usable without horizontal scrolling. Preserve
            // the complete rendered Actions control and compact the data columns
            // just enough to keep the right-most action column inside the live
            // viewport. Text/header cells can ellipsize and expose their existing
            // tooltips; action text/graphic/arrow may never be clipped.
        } else if (fitDenseViewport(columns, measures, widths, available)) {
            denseFit = true;
            // Reporting/Schedule tables can have many columns. The application
            // contract is viewport fit first: compact below the normal readable
            // minima only when necessary, while preserving a usable Actions cell.
        } else {
            // Truly tiny windows remain horizontally scrollable instead of forcing
            // columns below the absolute dense floor.
            for (int i = 0; i < measures.size(); i++) widths[i] = Math.max(DENSE_MIN_READABLE_COLUMN, measures.get(i).minimum());
        }

        double verifiedAvailable = contentViewportWidth(table);
        if (!Double.isFinite(verifiedAvailable)
                || Math.abs(verifiedAvailable - available) > VIEWPORT_STABILITY_TOLERANCE) {
            requestLayout(table);
            return;
        }

        closeResidual(columns, widths, available);
        for (int i = 0; i < columns.size(); i++) {
            TableColumn<?, ?> column = columns.get(i);
            double floor = denseFit ? DENSE_MIN_READABLE_COLUMN : MIN_READABLE_COLUMN;
            double width = Math.max(floor, widths[i]);
            if (Math.abs(column.getPrefWidth() - width) > 0.5) column.setPrefWidth(width);
        }
    }


    /**
     * Returns the current usable viewport without trusting a stale internal
     * VirtualFlow width. The outer TableView width is authoritative during large
     * drawer/sidebar transitions; skin measurements are accepted only when they
     * differ by normal control chrome (scrollbar/border) amounts.
     */
    private static double contentViewportWidth(TableView<?> table) {
        double tableWidth = table.getWidth();
        if (!Double.isFinite(tableWidth) || tableWidth < 1) return tableWidth;
        Insets insets = table.getInsets();
        double outer = Math.max(1, tableWidth - insets.getLeft() - insets.getRight());

        double verticalScrollbar = 0;
        try {
            for (Node node : table.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar
                        && bar.getOrientation() == Orientation.VERTICAL
                        && bar.isVisible() && bar.isManaged()) {
                    double width = bar.getWidth();
                    if (Double.isFinite(width) && width > 1) verticalScrollbar = Math.max(verticalScrollbar, width);
                }
            }
        } catch (RuntimeException ignored) { }
        double available = Math.max(1, outer - verticalScrollbar);

        double internal = -1;
        try {
            Node clipped = table.lookup(".clipped-container");
            if (clipped != null) {
                double width = clipped.getLayoutBounds().getWidth();
                if (Double.isFinite(width) && width > 1) internal = width;
            }
            if (internal < 1) {
                Node flow = table.lookup(".virtual-flow");
                if (flow != null) {
                    double width = flow.getLayoutBounds().getWidth();
                    if (Double.isFinite(width) && width > 1) internal = width;
                }
            }
        } catch (RuntimeException ignored) { }

        // A hundreds-of-pixels disagreement is a known one-pulse stale-skin
        // state during drawer close/open. Do not let that older width win.
        if (internal > 1 && Math.abs(available - internal) <= MAX_EXPECTED_SKIN_CHROME) {
            available = Math.min(available, internal);
        }
        return Math.max(1, available);
    }

    private static void invalidateContentMeasures(TableView<?> table) {
        if (table == null) return;
        for (TableColumn<?, ?> column : leafColumns(table.getColumns())) {
            column.getProperties().remove(SAMPLED_CONTENT_WIDTH);
            column.getProperties().remove(RENDERED_ACTION_WIDTH);
        }
    }

    /**
     * Final dense-table fallback used by Reporting/Schedule-style grids. Normal
     * readable minima remain preferred; this only activates when those minima do
     * not fit the real viewport.
     */
    private static boolean fitDenseViewport(List<TableColumn<?, ?>> columns,
                                            List<ColumnMeasure> measures,
                                            double[] widths,
                                            double available) {
        int actionIndex = -1;
        for (int i = 0; i < columns.size(); i++) {
            String heading = headerLabel(columns.get(i));
            if ("actions".equals(headerSemantic(columns.get(i), heading))) { actionIndex = i; break; }
        }
        double[] floors = new double[measures.size()];
        double floorTotal = 0;
        for (int i = 0; i < measures.size(); i++) {
            double floor = i == actionIndex
                    ? Math.min(Math.max(DENSE_ACTION_MIN_WIDTH, measures.get(i).minimum()), ACTION_CONTROL_MIN_WIDTH)
                    : Math.min(measures.get(i).minimum(), denseFloor(columns.get(i)));
            floors[i] = Math.max(DENSE_MIN_READABLE_COLUMN, floor);
            floorTotal += floors[i];
        }
        if (floorTotal > available) return false;

        double extra = available - floorTotal;
        double weight = 0;
        for (int i = 0; i < measures.size(); i++) if (i != actionIndex) weight += measures.get(i).flexWeight();
        for (int i = 0; i < measures.size(); i++) {
            if (i == actionIndex) widths[i] = floors[i];
            else widths[i] = floors[i] + (weight <= 0 ? 0 : extra * measures.get(i).flexWeight() / weight);
        }
        return true;
    }

    private static double denseFloor(TableColumn<?, ?> column) {
        String heading = headerLabel(column);
        String semantic = headerSemantic(column, heading);
        String key = (semantic + " " + heading).toLowerCase(Locale.ROOT);
        if (isLongTextSemantic(semantic, heading)) return 70.0;
        if (key.contains("status") || key.contains("payment") || key.contains("return")) return 62.0;
        if (key.contains("invoice") || key.contains("document") || key.contains("reference")) return 60.0;
        if (key.contains("amount") || key.contains("total") || key.contains("balance") || key.contains("paid")) return 58.0;
        return DENSE_MIN_READABLE_COLUMN;
    }

    /** Closes fractional/right-edge residue so fitted tables finish at the viewport edge. */
    private static void closeResidual(List<TableColumn<?, ?>> columns, double[] widths, double available) {
        double used = 0;
        for (double width : widths) used += width;
        double residual = available - used;
        if (Math.abs(residual) <= 0.25 || widths.length == 0) return;
        int target = widths.length - 1;
        for (int i = widths.length - 1; i >= 0; i--) {
            String heading = headerLabel(columns.get(i));
            if (!"actions".equals(headerSemantic(columns.get(i), heading))) { target = i; break; }
        }
        if (residual > 0) widths[target] += residual;
        else {
            double reducible = Math.max(0, widths[target] - DENSE_MIN_READABLE_COLUMN);
            widths[target] -= Math.min(-residual, reducible);
        }
    }


    private static boolean fitWithVisibleActionColumn(List<TableColumn<?, ?>> columns,
                                                      List<ColumnMeasure> measures,
                                                      double[] widths,
                                                      double available) {
        int actionIndex = -1;
        for (int i = 0; i < columns.size(); i++) {
            String heading = headerLabel(columns.get(i));
            if ("actions".equals(headerSemantic(columns.get(i), heading))) {
                actionIndex = i;
                break;
            }
        }
        if (actionIndex < 0) return false;

        ColumnMeasure action = measures.get(actionIndex);
        double actionWidth = Math.max(ACTION_CONTROL_MIN_WIDTH, action.minimum());
        double remaining = available - actionWidth;
        if (remaining <= 0) return false;

        double compactTotal = 0;
        double naturalDataTotal = 0;
        double shrinkable = 0;
        double[] compactMinimums = new double[measures.size()];
        for (int i = 0; i < measures.size(); i++) {
            if (i == actionIndex) continue;
            ColumnMeasure measure = measures.get(i);
            // Phase 3 readability rule: never squeeze a normal business column
            // below its semantic readable minimum just to keep Actions visible.
            // If those minima cannot fit, the table scrolls horizontally rather
            // than turning headers into 2–4 character fragments.
            double compact = measure.minimum();
            compactMinimums[i] = compact;
            compactTotal += compact;
            naturalDataTotal += measure.natural();
            shrinkable += Math.max(0, measure.natural() - compact);
        }
        if (compactTotal > remaining) return false;

        widths[actionIndex] = actionWidth;
        double targetDataWidth = remaining;
        double shrinkNeeded = Math.max(0, naturalDataTotal - targetDataWidth);
        for (int i = 0; i < measures.size(); i++) {
            if (i == actionIndex) continue;
            ColumnMeasure measure = measures.get(i);
            double compact = compactMinimums[i];
            double ownShrinkable = Math.max(0, measure.natural() - compact);
            double shrink = shrinkable <= 0 ? 0 : shrinkNeeded * ownShrinkable / shrinkable;
            widths[i] = Math.max(compact, measure.natural() - shrink);
        }

        // Floating-point rounding can leave a few pixels beyond the viewport.
        // Remove that residue from non-action columns only so the action control
        // remains completely visible, including its dropdown arrow/hit area.
        double used = 0;
        for (double width : widths) used += width;
        double excess = used - available;
        if (excess > 0.5) {
            for (int i = measures.size() - 1; i >= 0 && excess > 0.5; i--) {
                if (i == actionIndex) continue;
                double floor = compactMinimums[i];
                double reducible = Math.max(0, widths[i] - floor);
                double reduction = Math.min(excess, reducible);
                widths[i] -= reduction;
                excess -= reduction;
            }
        }
        return true;
    }

    private static ColumnMeasure measure(TableView<?> table, TableColumn<?, ?> column, double available) {
        String heading = headerLabel(column);
        String semantic = headerSemantic(column, heading);
        double header = headerWidth(column, heading);
        double content = sampledContentWidth(table, column);

        // A real checkbox header has no text but still needs comfortable hit-area.
        if (isSelectionColumn(column, semantic, heading)) {
            double natural = Math.max(header, 48.0);
            return new ColumnMeasure(natural, Math.max(MIN_READABLE_COLUMN, header), 0.45);
        }

        // Action cells commonly contain a MenuButton/Button while their observable
        // value is Void. Measure the actual rendered control whenever the virtual
        // flow has created one; this prevents the right edge/arrow from being
        // clipped when a detail drawer reduces the viewport. The header fallback
        // remains content-derived rather than an FXML/controller pixel width.
        double renderedControl = renderedCellControlWidth(table, column);
        if ("actions".equals(semantic)) content = Math.max(content, Math.max(ACTION_CONTROL_MIN_WIDTH, Math.max(renderedControl, header + 38.0)));

        double minimum = readableMinimum(semantic, heading, header);
        if ("actions".equals(semantic)) minimum = Math.max(minimum, Math.max(ACTION_CONTROL_MIN_WIDTH, renderedControl));
        double natural = Math.max(minimum, Math.max(header, content));

        // Do not make a column visibly contract every time paging/filtering swaps
        // the item list. Preserve the largest natural width seen for this column
        // during the screen lifetime; viewport reflow still redistributes/shrinks
        // toward readable minima when the available area becomes smaller.
        Object cached = column.getProperties().get(NATURAL_FLOOR);
        double previousNatural = cached instanceof Number n ? n.doubleValue() : 0.0;
        natural = Math.max(natural, previousNatural);

        // A single very long memo/address must not consume the whole table. This
        // cap is proportional to the current viewport, not a fixed pixel width.
        double capShare = isLongTextSemantic(semantic, heading) ? 0.42 : 0.30;
        natural = Math.min(natural, Math.max(minimum, available * capShare));
        if (natural > previousNatural + 0.5) column.getProperties().put(NATURAL_FLOOR, natural);
        return new ColumnMeasure(natural, minimum, flexWeight(semantic, heading));
    }

    private static double renderedCellControlWidth(TableView<?> table, TableColumn<?, ?> column) {
        if (table == null || column == null || table.getSkin() == null) return 0;
        Object cached = column.getProperties().get(RENDERED_ACTION_WIDTH);
        if (cached instanceof Number n && n.doubleValue() > 0) return n.doubleValue();
        double max = 0;
        try {
            for (Node node : table.lookupAll(".table-cell")) {
                if (!(node instanceof TableCell<?, ?> cell) || cell.getTableColumn() != column || cell.isEmpty()) continue;
                double width = 0;
                Node graphic = cell.getGraphic();
                if (graphic instanceof Region region) {
                    width = region.prefWidth(-1);
                    if (!Double.isFinite(width) || width <= 0) width = region.getLayoutBounds().getWidth();
                } else if (graphic != null) {
                    width = graphic.getLayoutBounds().getWidth();
                }
                if (Double.isFinite(width) && width > 0) max = Math.max(max, width + 24.0);
            }
        } catch (RuntimeException ignored) { }
        if (max > 0) column.getProperties().put(RENDERED_ACTION_WIDTH, max);
        return max;
    }

    private static double headerWidth(TableColumn<?, ?> column, String heading) {
        Node graphic = column.getGraphic();
        double graphicWidth = 0;
        if (graphic instanceof Region region) {
            graphicWidth = region.prefWidth(-1);
            if (!Double.isFinite(graphicWidth) || graphicWidth <= 0) graphicWidth = region.getLayoutBounds().getWidth();
        } else if (graphic != null) {
            graphicWidth = graphic.getLayoutBounds().getWidth();
        }
        double textFallback = textWidth(heading, Font.getDefault()) + HEADER_HORIZONTAL_PADDING + 22.0;
        return Math.max(textFallback, graphicWidth > 0 ? graphicWidth + HEADER_HORIZONTAL_PADDING : 0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static double sampledContentWidth(TableView<?> table, TableColumn<?, ?> column) {
        Object cached = column.getProperties().get(SAMPLED_CONTENT_WIDTH);
        if (cached instanceof Number n) return n.doubleValue();
        ObservableList<?> items = table.getItems();
        if (items == null || items.isEmpty()) {
            column.getProperties().put(SAMPLED_CONTENT_WIDTH, 0.0);
            return 0;
        }
        int count = items.size();
        int samples = Math.min(SAMPLE_LIMIT, count);
        double max = 0;
        for (int sample = 0; sample < samples; sample++) {
            int index = samples == 1 ? 0 : (int) Math.round(sample * (count - 1.0) / (samples - 1.0));
            Object value;
            try {
                value = ((TableColumn) column).getCellData(items.get(index));
            } catch (RuntimeException ignored) {
                continue;
            }
            String display = displayText(value);
            if (display.isBlank()) continue;
            max = Math.max(max, textWidth(display, Font.getDefault()) + CELL_HORIZONTAL_PADDING);
        }
        column.getProperties().put(SAMPLED_CONTENT_WIDTH, max);
        return max;
    }

    private static String displayText(Object value) {
        if (value == null) return "";
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (Math.rint(d) == d) return String.format(Locale.ROOT, "%,.0f", d);
            return String.format(Locale.ROOT, "%,.2f", d);
        }
        String text = String.valueOf(value).replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return text.length() <= 96 ? text : text.substring(0, 96);
    }

    private static double textWidth(String value, Font font) {
        if (value == null || value.isBlank()) return 0;
        Text text = new Text(value);
        text.setFont(font == null ? Font.getDefault() : font);
        return Math.ceil(text.getLayoutBounds().getWidth());
    }

    private static String headerLabel(TableColumn<?, ?> column) {
        Object stored = column.getProperties().get("erp-header-label");
        if (stored instanceof String value && !value.isBlank()) return value.trim();
        return column.getText() == null ? "" : column.getText().trim();
    }

    private static String headerSemantic(TableColumn<?, ?> column, String heading) {
        Object stored = column.getProperties().get("erp-header-semantic");
        if (stored instanceof String value && !value.isBlank()) return value.trim().toLowerCase(Locale.ROOT);
        String resolved = UiSemanticRegistry.headerSemantic(heading);
        return resolved == null ? "" : resolved.toLowerCase(Locale.ROOT);
    }

    private static boolean isSelectionColumn(TableColumn<?, ?> column, String semantic, String heading) {
        if ("select".equals(semantic)) return true;
        if (Boolean.TRUE.equals(column.getProperties().get("erp-global-checkbox"))) return true;
        if (column.getGraphic() instanceof CheckBox) return true;
        String id = column.getId() == null ? "" : column.getId().toLowerCase(Locale.ROOT);
        return heading.equals("#") || heading.equals("✓") || heading.equalsIgnoreCase("select") || id.contains("select");
    }

    /**
     * Readability-first minimum for constrained tables. Long headers may wrap to
     * two lines, so the minimum is intentionally smaller than their one-line
     * natural width but never falls back to the former 44px fragments.
     */
    private static double readableMinimum(String semantic, String heading, double oneLineHeaderWidth) {
        String key = (semantic + " " + heading).toLowerCase(Locale.ROOT);
        double semanticFloor;
        if (key.contains("actions")) semanticFloor = ACTION_CONTROL_MIN_WIDTH;
        else if (key.contains("select")) semanticFloor = 48.0;
        else if (isLongTextSemantic(semantic, heading)) semanticFloor = 86.0;
        else if (key.contains("invoice") || key.contains("reference") || key.contains("document")
            || key.contains("account") || key.contains("email") || key.contains("gst")) semanticFloor = 74.0;
        else if (key.contains("payment") || key.contains("return") || key.contains("status")) semanticFloor = 78.0;
        else if (key.contains("amount") || key.contains("balance") || key.contains("paid")
            || key.contains("pending") || key.contains("total") || key.contains("rate")) semanticFloor = 72.0;
        else if (key.contains("date") || key.contains("due") || key.contains("mobile")
            || key.contains("quantity") || key.contains("qty")) semanticFloor = 64.0;
        else semanticFloor = 68.0;

        // JavaFX Label wrapping may split a word character-by-character when the
        // label becomes narrower than that word. Keep enough room for the longest
        // header token plus the semantic icon/spacing so headers wrap only between
        // words. This still lets dense registers fit Actions in the first viewport.
        double noBreakFloor = longestHeaderTokenWidth(heading) + 44.0;
        double wrappedFloor = Math.max(noBreakFloor, Math.min(oneLineHeaderWidth, semanticFloor));
        return Math.max(MIN_READABLE_COLUMN, wrappedFloor);
    }

    private static double longestHeaderTokenWidth(String heading) {
        if (heading == null || heading.isBlank()) return 0;
        Font headerFont = Font.font(Font.getDefault().getFamily(), FontWeight.EXTRA_BOLD, 11.5);
        double max = 0;
        for (String token : heading.trim().split("\\s+")) {
            if (!token.isBlank()) max = Math.max(max, textWidth(token, headerFont));
        }
        return max;
    }

    private static boolean isLongTextSemantic(String semantic, String heading) {
        String key = semantic + " " + heading.toLowerCase(Locale.ROOT);
        return key.contains("customer") || key.contains("supplier") || key.contains("description")
            || key.contains("address") || key.contains("notes") || key.contains("remarks")
            || key.contains("narration") || key.contains("item") || key.contains("party")
            || key.contains("name") || key.contains("details");
    }

    private static double flexWeight(String semantic, String heading) {
        String key = semantic + " " + heading.toLowerCase(Locale.ROOT);
        if (isLongTextSemantic(semantic, heading)) return 3.0;
        if (key.contains("email") || key.contains("invoice") || key.contains("reference")
            || key.contains("document") || key.contains("code") || key.contains("account")) return 1.8;
        if (key.contains("actions") || key.contains("select")) return 0.6;
        if (key.contains("date") || key.contains("status") || key.contains("quantity")
            || key.contains("tax") || key.contains("percent") || key.contains("number")) return 1.0;
        return 1.35;
    }

    private static List<TableColumn<?, ?>> leafColumns(List<? extends TableColumn<?, ?>> roots) {
        List<TableColumn<?, ?>> result = new ArrayList<>();
        for (TableColumn<?, ?> root : roots) collectLeaf(root, result);
        return result;
    }

    private static void collectLeaf(TableColumn<?, ?> column, List<TableColumn<?, ?>> result) {
        if (column.getColumns().isEmpty()) {
            result.add(column);
            return;
        }
        for (TableColumn<?, ?> child : column.getColumns()) collectLeaf(child, result);
    }

    private record ColumnMeasure(double natural, double minimum, double flexWeight) { }
}
