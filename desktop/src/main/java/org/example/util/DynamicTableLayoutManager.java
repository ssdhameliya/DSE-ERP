package org.example.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final String ITEM_LISTENER = "erp.table.dynamic-layout.item-listener";
    private static final String COLUMN_LISTENER = "erp.table.dynamic-layout.column-listener";
    private static final String COLUMN_BOUND = "erp.table.dynamic-layout.column-bound";
    private static final String NATURAL_FLOOR = "erp.table.dynamic-layout.natural-floor";
    private static final int SAMPLE_LIMIT = 48;
    private static final double TABLE_CHROME_ALLOWANCE = 20.0;
    private static final double CELL_HORIZONTAL_PADDING = 24.0;
    private static final double HEADER_HORIZONTAL_PADDING = 18.0;
    private static final double MIN_READABLE_COLUMN = 44.0;

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
        table.sceneProperty().addListener((obs, oldScene, newScene) -> requestLayout(table));
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> requestLayout(table));

        if (!Boolean.TRUE.equals(table.getProperties().get(COLUMN_LISTENER))) {
            table.getProperties().put(COLUMN_LISTENER, true);
            table.getColumns().addListener((ListChangeListener<TableColumn>) change -> {
                bindColumns(table);
                requestLayout(table);
            });
        }
        requestLayout(table);
    }

    /**
     * Public hook for drawers/saved views that deliberately change available width/visibility.
     *
     * <p>When the TableView already has a skin and a usable width, perform the
     * sizing pass immediately on the JavaFX thread. This prevents a default
     * JavaFX column layout from being painted for one pulse before the ERP
     * widths are applied. A deferred pass is used only while the control is not
     * layout-ready yet.</p>
     */
    public static void requestLayout(TableView<?> table) {
        if (table == null) return;
        if (Platform.isFxApplicationThread() && isLayoutReady(table)) {
            table.getProperties().remove(PENDING);
            layoutNow(table);
            return;
        }
        if (Boolean.TRUE.equals(table.getProperties().get(PENDING))) return;
        table.getProperties().put(PENDING, true);
        Platform.runLater(() -> {
            table.getProperties().remove(PENDING);
            layoutNow(table);
        });
    }

    /** Reflows every TableView below a container after a drawer/split layout change. */
    public static void requestLayoutIn(Node root) {
        if (root == null) return;
        Runnable pass = () -> {
            try {
                root.applyCss();
                if (root instanceof Region region) region.layout();
                if (root instanceof TableView<?> table) requestLayout(table);
                for (Node node : root.lookupAll(".table-view")) {
                    if (node instanceof TableView<?> table) requestLayout(table);
                }
            } catch (RuntimeException ignored) { }
        };
        if (Platform.isFxApplicationThread()) Platform.runLater(pass);
        else Platform.runLater(pass);
    }

    private static boolean isLayoutReady(TableView<?> table) {
        return table.getSkin() != null && Double.isFinite(table.getWidth()) && table.getWidth() >= 80;
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
            ListChangeListener listener = change -> requestLayout(table);
            newItems.addListener(listener);
            table.getProperties().put(ITEM_LISTENER, listener);
        }
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

        double tableWidth = table.getWidth();
        if (!Double.isFinite(tableWidth) || tableWidth < 80) return;
        Insets insets = table.getInsets();
        double available = Math.max(1,
            tableWidth - TABLE_CHROME_ALLOWANCE - insets.getLeft() - insets.getRight());

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
        } else {
            // The table is narrower than the readable header minima. Keep those
            // minima and let JavaFX provide horizontal scrolling.
            for (int i = 0; i < measures.size(); i++) widths[i] = measures.get(i).minimum();
        }

        for (int i = 0; i < columns.size(); i++) {
            TableColumn<?, ?> column = columns.get(i);
            double width = Math.max(MIN_READABLE_COLUMN, widths[i]);
            if (Math.abs(column.getPrefWidth() - width) > 0.5) column.setPrefWidth(width);
        }
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
        if ("actions".equals(semantic)) content = Math.max(content, Math.max(renderedControl, header + 38.0));

        double minimum = Math.max(MIN_READABLE_COLUMN, header);
        if ("actions".equals(semantic) && renderedControl > 0) minimum = Math.max(minimum, renderedControl);
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
        double max = 0;
        try {
            for (Node node : table.lookupAll(".table-cell")) {
                if (!(node instanceof TableCell<?, ?> cell) || cell.getTableColumn() != column || cell.isEmpty()) continue;
                double width = 0;
                Node graphic = cell.getGraphic();
                if (graphic instanceof Region region) {
                    region.applyCss();
                    width = region.prefWidth(-1);
                    if (!Double.isFinite(width) || width <= 0) width = region.getLayoutBounds().getWidth();
                } else if (graphic != null) {
                    width = graphic.getLayoutBounds().getWidth();
                }
                if (width <= 0) {
                    cell.applyCss();
                    width = cell.prefWidth(-1);
                }
                if (Double.isFinite(width) && width > 0) max = Math.max(max, width + 8.0);
            }
        } catch (RuntimeException ignored) { }
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
        ObservableList<?> items = table.getItems();
        if (items == null || items.isEmpty()) return 0;
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
