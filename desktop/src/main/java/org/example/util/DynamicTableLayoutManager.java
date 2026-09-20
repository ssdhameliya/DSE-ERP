package org.example.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single, low-cost width authority for every ERP TableView.
 *
 * <p>The visual contract is deliberately unchanged: every visible header keeps
 * its semantic icon/colour, all visible columns fill the complete viewport and
 * dense register values may ellipsize rather than forcing a horizontal scroll.
 * The important difference is lifecycle ownership. Column widths are derived
 * only from stable header semantics + viewport width; normal VirtualFlow row
 * recycling and vertical scrolling never trigger content scans or width work.</p>
 *
 * <p>FXML/controllers may still own visibility/order/cell factories. This class
 * owns width only. It recalculates on structural geometry changes (table width,
 * drawer/window resize, column visibility/schema, first skin/show), not on
 * scrolling, hover, row selection or cell realization.</p>
 */
public final class DynamicTableLayoutManager {
    private static final String INSTALLED = "erp.table.dynamic-layout.installed";
    private static final String PENDING = "erp.table.dynamic-layout.pending";
    private static final String GENERATION = "erp.table.dynamic-layout.generation";
    private static final String ITEM_LISTENER = "erp.table.dynamic-layout.item-listener";
    private static final String COLUMN_LISTENER = "erp.table.dynamic-layout.column-listener";
    private static final String COLUMN_BOUND = "erp.table.dynamic-layout.column-bound";
    private static final String HEADER_MINIMUM = "erp.table.dynamic-layout.header-minimum";
    private static final String LAST_AVAILABLE = "erp.table.dynamic-layout.last-available";
    private static final String SETTLE_PASSES = "erp.table.dynamic-layout.settle-passes";

    /** Keeps the current Sales-style Actions button fully usable. */
    private static final double ACTION_CONTROL_MIN_WIDTH = 118.0;
    private static final double MIN_READABLE_COLUMN = 58.0;
    private static final double DENSE_MIN_READABLE_COLUMN = 44.0;
    private static final double ABSOLUTE_MIN_COLUMN = 34.0;
    private static final double LAYOUT_TOLERANCE = 0.75;

    private DynamicTableLayoutManager() { }

    /** Installs the shared viewport-fill policy once. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void install(TableView<?> table) {
        if (table == null) return;
        if (Boolean.TRUE.equals(table.getProperties().get(INSTALLED))) {
            requestLayout(table);
            return;
        }
        table.getProperties().put(INSTALLED, true);

        // JavaFX owns final scrollbar/border compensation. Our pref widths are
        // weighted targets; the constrained policy closes every final pixel so
        // there is no right-side filler and no horizontal scrollbar in normal ERP
        // register/dialog layouts.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        bindColumns(table);
        bindItems(table, null, table.getItems());

        table.widthProperty().addListener((obs, oldValue, newValue) -> requestSettledLayout(table));
        table.itemsProperty().addListener((obs, oldItems, newItems) -> bindItems(table, oldItems, newItems));
        table.sceneProperty().addListener((obs, oldScene, newScene) -> requestSettledLayout(table));
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> requestSettledLayout(table));
        table.visibleProperty().addListener((obs, oldValue, newValue) -> { if (newValue) requestSettledLayout(table); });
        table.managedProperty().addListener((obs, oldValue, newValue) -> { if (newValue) requestSettledLayout(table); });

        if (!Boolean.TRUE.equals(table.getProperties().get(COLUMN_LISTENER))) {
            table.getProperties().put(COLUMN_LISTENER, true);
            table.getColumns().addListener((ListChangeListener<TableColumn>) change -> {
                bindColumns(table);
                requestLayout(table);
            });
        }
        requestSettledLayout(table);
    }

    /** Coalesces structural geometry changes into one next-pulse calculation. */
    public static void requestLayout(TableView<?> table) {
        if (table == null) return;
        long generation = generation(table) + 1L;
        table.getProperties().put(GENERATION, generation);
        if (Boolean.TRUE.equals(table.getProperties().get(PENDING))) return;
        table.getProperties().put(PENDING, true);
        Platform.runLater(() -> runScheduledLayout(table));
    }

    private static void runScheduledLayout(TableView<?> table) {
        if (table == null) return;
        long observed = generation(table);
        layoutNow(table);
        table.getProperties().remove(PENDING);
        if (generation(table) != observed) {
            requestLayout(table);
            return;
        }
        Object settle = table.getProperties().get(SETTLE_PASSES);
        int remaining = settle instanceof Number number ? number.intValue() : 0;
        if (remaining > 0) {
            table.getProperties().put(SETTLE_PASSES, remaining - 1);
            requestLayout(table);
        } else {
            table.getProperties().remove(SETTLE_PASSES);
        }
    }

    /** Reflows every TableView below a container after drawer/window geometry changes. */
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bindColumns(TableView<?> table) {
        for (TableColumn<?, ?> column : leafColumns(table.getColumns())) {
            prepareColumn(column);
            if (!Boolean.TRUE.equals(column.getProperties().get(COLUMN_BOUND))) {
                column.getProperties().put(COLUMN_BOUND, true);
                column.visibleProperty().addListener((obs, oldValue, newValue) -> requestLayout(table));
                column.graphicProperty().addListener((obs, oldValue, newValue) -> {
                    column.getProperties().remove(HEADER_MINIMUM);
                    requestLayout(table);
                });
                column.textProperty().addListener((obs, oldValue, newValue) -> {
                    column.getProperties().remove(HEADER_MINIMUM);
                    requestLayout(table);
                });
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bindItems(TableView<?> table, ObservableList oldItems, ObservableList newItems) {
        Object existing = table.getProperties().remove(ITEM_LISTENER);
        if (oldItems != null && existing instanceof ListChangeListener listener) oldItems.removeListener(listener);
        if (newItems != null) {
            // Item changes do not affect column widths. One coalesced settle request
            // is enough for JavaFX to account for a vertical scrollbar appearing or
            // disappearing; there is no row/content measurement and no scroll hook.
            ListChangeListener listener = change -> requestSettledLayout(table);
            newItems.addListener(listener);
            table.getProperties().put(ITEM_LISTENER, listener);
        }
        requestSettledLayout(table);
    }

    private static void requestSettledLayout(TableView<?> table) {
        if (table == null) return;
        table.getProperties().put(SETTLE_PASSES, 1);
        requestLayout(table);
    }

    private static void prepareColumn(TableColumn<?, ?> column) {
        if (column == null || !column.getColumns().isEmpty()) return;
        column.setMinWidth(0);
        column.setMaxWidth(Double.MAX_VALUE);
        column.setResizable(true);
    }

    private static void layoutNow(TableView<?> table) {
        if (table == null) return;
        List<TableColumn<?, ?>> columns = new ArrayList<>(table.getVisibleLeafColumns());
        if (columns.isEmpty()) return;

        double available = contentViewportWidth(table);
        if (!Double.isFinite(available) || available < 80) return;
        Object prior = table.getProperties().get(LAST_AVAILABLE);
        double priorAvailable = prior instanceof Number n ? n.doubleValue() : -1;
        if (priorAvailable > 0 && Math.abs(priorAvailable - available) <= LAYOUT_TOLERANCE) {
            // A row/item/skin pulse with unchanged structural width requires no work.
            return;
        }
        table.getProperties().put(LAST_AVAILABLE, available);

        List<ColumnProfile> profiles = new ArrayList<>(columns.size());
        double floorTotal = 0;
        double weightTotal = 0;
        for (TableColumn<?, ?> column : columns) {
            prepareColumn(column);
            ColumnProfile profile = profile(column);
            profiles.add(profile);
            floorTotal += profile.floor();
            weightTotal += profile.weight();
        }

        double[] widths = floorTotal <= available
                ? allocateFromFloors(profiles, available, floorTotal, weightTotal)
                : allocateDense(columns, profiles, available);
        closeResidual(widths, available);

        for (int i = 0; i < columns.size(); i++) {
            double width = Math.max(ABSOLUTE_MIN_COLUMN, widths[i]);
            TableColumn<?, ?> column = columns.get(i);
            if (Math.abs(column.getPrefWidth() - width) > 0.5) column.setPrefWidth(width);
        }
    }

    private static double[] allocateFromFloors(List<ColumnProfile> profiles,
                                               double available,
                                               double floorTotal,
                                               double weightTotal) {
        double[] widths = new double[profiles.size()];
        double extra = Math.max(0, available - floorTotal);
        for (int i = 0; i < profiles.size(); i++) {
            ColumnProfile p = profiles.get(i);
            widths[i] = p.floor() + (weightTotal <= 0 ? 0 : extra * p.weight() / weightTotal);
        }
        return widths;
    }

    /**
     * Emergency compact mode for a narrow drawer/window. It still fits every
     * visible column into the viewport; headers remain represented by their
     * icon/text graphics and values ellipsize rather than creating horizontal
     * scrolling. Normal desktop widths do not enter this branch.
     */
    private static double[] allocateDense(List<TableColumn<?, ?>> columns,
                                          List<ColumnProfile> profiles,
                                          double available) {
        double[] dense = new double[profiles.size()];
        double total = 0;
        for (int i = 0; i < profiles.size(); i++) {
            String heading = headerLabel(columns.get(i));
            String semantic = headerSemantic(columns.get(i), heading);
            double floor;
            if (isSelectionColumn(columns.get(i), semantic, heading)) floor = 36.0;
            else if ("actions".equals(semantic)) floor = Math.min(ACTION_CONTROL_MIN_WIDTH, 96.0);
            else floor = Math.max(ABSOLUTE_MIN_COLUMN, Math.min(profiles.get(i).floor(), denseFloor(semantic, heading)));
            dense[i] = floor;
            total += floor;
        }
        if (total <= available) {
            double extra = available - total;
            double weights = profiles.stream().mapToDouble(ColumnProfile::weight).sum();
            for (int i = 0; i < dense.length; i++) {
                dense[i] += weights <= 0 ? 0 : extra * profiles.get(i).weight() / weights;
            }
            return dense;
        }

        // Extremely small windows: proportionally compress while keeping Actions
        // and selection slightly more protected. This still avoids a second axis
        // scrollbar and never performs content measurement.
        double scale = Math.max(0.45, available / total);
        double scaledTotal = 0;
        for (int i = 0; i < dense.length; i++) {
            String semantic = headerSemantic(columns.get(i), headerLabel(columns.get(i)));
            double hardFloor = "actions".equals(semantic) ? 78.0 : isSelectionColumn(columns.get(i), semantic, headerLabel(columns.get(i))) ? 30.0 : ABSOLUTE_MIN_COLUMN;
            dense[i] = Math.max(hardFloor, dense[i] * scale);
            scaledTotal += dense[i];
        }
        if (scaledTotal > available) {
            double reducible = 0;
            for (int i = 0; i < dense.length; i++) {
                String semantic = headerSemantic(columns.get(i), headerLabel(columns.get(i)));
                double hardFloor = "actions".equals(semantic) ? 72.0 : ABSOLUTE_MIN_COLUMN;
                reducible += Math.max(0, dense[i] - hardFloor);
            }
            double needed = scaledTotal - available;
            for (int i = 0; i < dense.length && needed > 0.25; i++) {
                String semantic = headerSemantic(columns.get(i), headerLabel(columns.get(i)));
                double hardFloor = "actions".equals(semantic) ? 72.0 : ABSOLUTE_MIN_COLUMN;
                double own = Math.max(0, dense[i] - hardFloor);
                double reduction = reducible <= 0 ? 0 : Math.min(own, needed * own / reducible);
                dense[i] -= reduction;
            }
        }
        return dense;
    }

    /**
     * Width comes from the stable TableView box. A visible vertical scrollbar is
     * sampled only during structural resize/settle passes; it is never observed
     * by a listener, so ordinary scrolling cannot trigger column calculations.
     */
    private static double contentViewportWidth(TableView<?> table) {
        double width = table.getWidth();
        if (!Double.isFinite(width) || width < 1) return width;
        Insets insets = table.getInsets();
        double available = width - insets.getLeft() - insets.getRight() - 2.0;
        try {
            for (Node node : table.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar
                        && bar.getOrientation() == Orientation.VERTICAL
                        && bar.isVisible() && bar.isManaged()) {
                    double barWidth = bar.getWidth();
                    if (Double.isFinite(barWidth) && barWidth > 1) {
                        available -= barWidth;
                        break;
                    }
                }
            }
        } catch (RuntimeException ignored) { }
        return Math.max(1, available);
    }

    private static ColumnProfile profile(TableColumn<?, ?> column) {
        String heading = headerLabel(column);
        String semantic = headerSemantic(column, heading);
        double floor = headerMinimum(column, heading, semantic);
        return new ColumnProfile(floor, flexWeight(semantic, heading));
    }

    private static double headerMinimum(TableColumn<?, ?> column, String heading, String semantic) {
        Object cached = column.getProperties().get(HEADER_MINIMUM);
        if (cached instanceof Number n) return n.doubleValue();
        double minimum;
        if (isSelectionColumn(column, semantic, heading)) minimum = 48.0;
        else if ("actions".equals(semantic)) minimum = ACTION_CONTROL_MIN_WIDTH;
        else {
            Font headerFont = Font.font(Font.getDefault().getFamily(), FontWeight.EXTRA_BOLD, 11.5);
            double longestToken = longestHeaderTokenWidth(heading, headerFont);
            minimum = Math.max(readableMinimum(semantic, heading), longestToken + 36.0);
        }
        column.getProperties().put(HEADER_MINIMUM, minimum);
        return minimum;
    }

    private static double readableMinimum(String semantic, String heading) {
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
        return Math.max(MIN_READABLE_COLUMN, semanticFloor);
    }

    private static double denseFloor(String semantic, String heading) {
        String key = (semantic + " " + heading).toLowerCase(Locale.ROOT);
        if (isLongTextSemantic(semantic, heading)) return 66.0;
        if (key.contains("status") || key.contains("payment") || key.contains("return")) return 58.0;
        if (key.contains("invoice") || key.contains("document") || key.contains("reference")) return 56.0;
        if (key.contains("amount") || key.contains("total") || key.contains("balance") || key.contains("paid")) return 52.0;
        return DENSE_MIN_READABLE_COLUMN;
    }

    private static void closeResidual(double[] widths, double available) {
        if (widths.length == 0) return;
        double used = 0;
        for (double width : widths) used += width;
        widths[widths.length - 1] += available - used;
    }

    private static double longestHeaderTokenWidth(String heading, Font headerFont) {
        if (heading == null || heading.isBlank()) return 0;
        double max = 0;
        for (String token : heading.trim().split("\\s+")) {
            if (!token.isBlank()) max = Math.max(max, textWidth(token, headerFont));
        }
        return max;
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
        if (key.contains("actions") || key.contains("select")) return 0.65;
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

    private record ColumnProfile(double floor, double weight) { }
}
