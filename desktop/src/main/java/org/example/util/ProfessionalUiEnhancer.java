package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.collections.ListChangeListener;

import java.util.Locale;

/**
 * Applies the ERP-wide table and date conventions after an FXML page is loaded.
 * Screen controllers remain responsible for business actions; this class keeps
 * resizing, placeholder selection columns and blank dates consistent.
 */
public final class ProfessionalUiEnhancer {
    private ProfessionalUiEnhancer() {}

    /** Enhances every supported control below the supplied page root. */
    public static void enhance(Node root) {
        if (root == null || Boolean.TRUE.equals(root.getProperties().get("erp-ui-enhanced"))) return;
        root.getProperties().put("erp-ui-enhanced", true);
        walk(root);
        SharedUiFramework.install(root);
    }

    private static void walk(Node node) {
        if (node instanceof TableView<?> table) enhanceTable(table);
        if (node instanceof DialogPane pane) enhanceDialog(pane);
        if (node instanceof Parent parent) {
            installDynamicChildEnhancement(parent);
            for (Node child : parent.getChildrenUnmodifiable()) walk(child);
        }
    }


    /** Enhances controls added after FXML loading, including dynamic dialog tables and action buttons. */
    private static void installDynamicChildEnhancement(Parent parent) {
        if (Boolean.TRUE.equals(parent.getProperties().get("erp-dynamic-child-listener"))) return;
        parent.getProperties().put("erp-dynamic-child-listener", true);
        parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                for (Node added : change.getAddedSubList()) {
                    walk(added);
                    IconFactory.decorate(added);
                    TablePerformanceOptimizer.apply(added);
                }
            }
        });
    }

    /**
     * Applies the shared visual language to legacy JavaFX Alert/Dialog instances.
     * New workflows use ModernDialog; this bridge keeps older controllers
     * consistent without changing their business handlers.
     */
    private static void enhanceDialog(DialogPane pane) {
        // Custom modern dialogs own their complete shell (title bar, graphic,
        // content and action bar). Applying the legacy bridge on top of them
        // creates duplicate icons, nested borders and conflicting padding.
        if (isCustomDialog(pane)) {
            return;
        }

        if (!pane.getStyleClass().contains("erp-modern-dialog")) {
            pane.getStyleClass().add("erp-modern-dialog");
        }
        String classes = String.join(" ", pane.getStyleClass()).toLowerCase(Locale.ROOT);
        String semantic = classes.contains("error") ? "error"
            : classes.contains("warning") ? "warning" : "notification";
        if (pane.getGraphic() == null) pane.setGraphic(IconFactory.icon(semantic, 38));

        Platform.runLater(() -> pane.getButtonTypes().forEach(type -> {
            Node button = pane.lookupButton(type);
            if (button instanceof ButtonBase action) {
                if (action.getText() == null || action.getText().isBlank()) action.setText(type.getText());
                IconFactory.decorate(action);
            }
        }));
    }


    /** Returns true when a dialog explicitly owns its visual presentation. */
    private static boolean isCustomDialog(DialogPane pane) {
        return Boolean.TRUE.equals(pane.getProperties().get("erp-dialog-custom"))
            || pane.getStyleClass().contains("modern-dialog");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void enhanceTable(TableView table) {
        applyTableProfile(table);
        if (!table.getStyleClass().contains("erp-full-width-table")) {
            table.getStyleClass().add("erp-full-width-table");
        }
        table.setMaxWidth(Double.MAX_VALUE);
        table.setMaxHeight(Double.MAX_VALUE);
        if (table.getParent() instanceof VBox) VBox.setVgrow(table, Priority.ALWAYS);
        if (table.getParent() instanceof HBox) HBox.setHgrow(table, Priority.ALWAYS);

        decorateColumns(table.getColumns());
        installHeaderLifecycleRefresh(table);
        installCellValueTooltips(table);
        installAdaptiveColumnFill(table);

        // Controllers add a number of business columns after FXML loading.
        // Keep header decoration live so those columns receive the exact same
        // icon-and-label treatment without requiring screen-specific code.
        if (!Boolean.TRUE.equals(table.getProperties().get("erp-column-listener"))) {
            table.getProperties().put("erp-column-listener", true);
            table.getColumns().addListener((ListChangeListener<TableColumn>) change -> {
                decorateColumns(table.getColumns());
                captureAdaptiveBaselines(table, true);
                resizeColumnsToUseAvailableWidth(table);
            });
        }

        if (!table.getColumns().isEmpty()) {
            TableColumn first = (TableColumn) table.getColumns().getFirst();
            String heading = first.getText() == null ? "" : first.getText().trim();
            String columnId = first.getId() == null ? "" : first.getId().toLowerCase(Locale.ROOT);
            // Keep real workflow checkboxes (for example multi-item returns), but convert
            // passive selection columns in register/master tables into readable row numbers.
            boolean keepSelection = Boolean.TRUE.equals(table.getProperties().get("erp-keep-selection"));
            boolean selectionColumn = !keepSelection && (heading.equals("#")
                    || heading.equals("✓")
                    || heading.equalsIgnoreCase("select")
                    || columnId.contains("select")
                    // Legacy controllers create the leading selection column in
                    // Java without an id or label, so a blank leading heading is
                    // itself the reliable cross-screen selection-column marker.
                    || heading.isBlank());
            if (selectionColumn && table.getStyleClass().contains("erp-hide-leading-index")) {
                first.setVisible(false);
                first.setMinWidth(0);
                first.setPrefWidth(0);
                first.setMaxWidth(0);
            } else if (selectionColumn && !Boolean.TRUE.equals(first.getProperties().get("erp-row-number"))) {
                first.getProperties().put("erp-row-number", true);
                first.getProperties().put("erp-header-label", "No.");
                first.getProperties().put("erp-header-semantic", "quantity");
                first.setText("");
                first.setGraphic(tableHeader("No.", "quantity"));
                first.setMinWidth(62);
                first.setPrefWidth(62);
                first.setMaxWidth(62);
                first.setSortable(false);
                first.setCellFactory(ignored -> new TableCell<Object, Object>() {
                    @Override
                    protected void updateItem(Object item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(null);
                        setText(empty || getIndex() < 0 ? null : Integer.toString(getIndex() + 1));
                        setAlignment(Pos.CENTER);
                    }
                });
            }
        }

        // Row context menus are owned by each controller. A global menu caused
        // duplicate/overlapping actions, especially on macOS.

    }


    /**
     * Uses otherwise-empty space inside the TableView without changing any
     * surrounding screen layout. Existing preferred widths remain the readable
     * baseline. When the table is wider than those baselines, the surplus is
     * distributed across ordinary data columns. When it is narrower, baseline
     * widths are restored and JavaFX may show a horizontal scrollbar.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installAdaptiveColumnFill(TableView table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-adaptive-column-fill"))) return;
        if (Boolean.TRUE.equals(table.getProperties().get("erp-preserve-resize-policy"))) return;

        String profile = String.valueOf(
            table.getProperties().getOrDefault("erp-table-profile", "responsive")
        );
        // Imported spreadsheets and permission matrices own dynamic/specialized
        // sizing and must not be altered by the shared register-table behavior.
        if ("import".equals(profile) || "permission".equals(profile)) return;

        table.getProperties().put("erp-adaptive-column-fill", true);
        captureAdaptiveBaselines(table, false);

        table.widthProperty().addListener((obs, oldWidth, newWidth) ->
            resizeColumnsToUseAvailableWidth(table)
        );
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                captureAdaptiveBaselines(table, false);
                resizeColumnsToUseAvailableWidth(table);
            }
        });

        resizeColumnsToUseAvailableWidth(table);
    }

    /**
     * Captures each visible leaf column's normal preferred width. Baselines are
     * intentionally independent of later adaptive expansion so repeated window
     * resizes never compound column widths.
     */
    @SuppressWarnings("rawtypes")
    private static void captureAdaptiveBaselines(TableView table, boolean includeNewColumns) {
        for (Object value : table.getVisibleLeafColumns()) {
            TableColumn column = (TableColumn) value;
            if (!includeNewColumns
                && column.getProperties().containsKey("erp-adaptive-base-pref")) {
                continue;
            }
            if (!column.getProperties().containsKey("erp-adaptive-base-pref")) {
                double baseline = Math.max(column.getMinWidth(), column.getPrefWidth());
                column.getProperties().put("erp-adaptive-base-pref", baseline);
            }
        }
    }

    /**
     * Expands only columns inside the table. It never resizes the TableView,
     * SplitPane, parent containers, filters, cards, pagination, or the screen.
     */
    @SuppressWarnings("rawtypes")
    private static void resizeColumnsToUseAvailableWidth(TableView table) {
        if (table.getWidth() <= 1 || table.getVisibleLeafColumns().isEmpty()) return;

        captureAdaptiveBaselines(table, false);

        java.util.List<TableColumn> visible = new java.util.ArrayList<>();
        java.util.List<TableColumn> flexible = new java.util.ArrayList<>();
        double baselineTotal = 0;

        for (Object value : table.getVisibleLeafColumns()) {
            TableColumn column = (TableColumn) value;
            if (!column.isVisible()) continue;

            Object stored = column.getProperties().get("erp-adaptive-base-pref");
            double baseline = stored instanceof Number number
                ? number.doubleValue()
                : Math.max(column.getMinWidth(), column.getPrefWidth());
            baseline = Math.max(column.getMinWidth(), baseline);

            visible.add(column);
            baselineTotal += baseline;
            if (isAdaptiveFlexibleColumn(column)) flexible.add(column);
        }

        if (visible.isEmpty()) return;

        // A small allowance prevents a one-pixel rounding overflow from creating
        // a horizontal scrollbar when the columns otherwise fit exactly.
        double available = Math.max(0, table.getWidth() - 3);

        // Restore readable baseline widths whenever the viewport is too narrow.
        // UNCONSTRAINED profiles can then expose a real horizontal scrollbar.
        if (available <= baselineTotal || flexible.isEmpty()) {
            for (TableColumn column : visible) {
                Number stored = (Number) column.getProperties().get("erp-adaptive-base-pref");
                if (stored != null) column.setPrefWidth(
                    Math.max(column.getMinWidth(), stored.doubleValue())
                );
            }
            return;
        }

        java.util.Map<TableColumn,Double> widths = new java.util.LinkedHashMap<>();
        for (TableColumn column : visible) {
            Number stored = (Number) column.getProperties().get("erp-adaptive-base-pref");
            if (stored != null) widths.put(column, Math.max(column.getMinWidth(), stored.doubleValue()));
        }
        double remaining = available - baselineTotal;
        java.util.List<TableColumn> active = new java.util.ArrayList<>(flexible);
        int guard = 0;
        while (remaining > .25 && !active.isEmpty() && guard++ < 12) {
            double weight = active.stream().mapToDouble(ProfessionalUiEnhancer::adaptiveColumnWeight).sum();
            double consumed = 0;
            java.util.List<TableColumn> capped = new java.util.ArrayList<>();
            for (TableColumn column : active) {
                double share = weight <= 0 ? remaining / active.size() : remaining * adaptiveColumnWeight(column) / weight;
                double current = widths.getOrDefault(column,column.getPrefWidth());
                double next = Math.min(column.getMaxWidth(), current + share);
                consumed += Math.max(0,next-current); widths.put(column,next);
                if (next + .25 >= column.getMaxWidth()) capped.add(column);
            }
            remaining -= consumed; active.removeAll(capped);
            if (consumed <= .01) break;
        }
        // Apply the final widths in one pass. Redistributing capped-column surplus
        // prevents the unused strip previously visible at the right edge.
        widths.forEach(TableColumn::setPrefWidth);
    }

    /** Keeps utility columns compact while allowing business data to absorb space. */
    @SuppressWarnings("rawtypes")
    private static boolean isAdaptiveFlexibleColumn(TableColumn column) {
        String heading = adaptiveHeading(column);
        String semantic = String.valueOf(
            column.getProperties().getOrDefault("erp-header-semantic", "")
        ).toLowerCase(Locale.ROOT);

        if ("actions".equals(semantic)) return false;
        if (heading.equals("#") || heading.equals("no.") || heading.equals("no")
            || heading.equals("select") || heading.equals("✓")) return false;
        if (column.getMaxWidth() <= 150) return false;
        return column.isResizable();
    }

    /** Gives descriptive fields more of the available surplus than numeric fields. */
    @SuppressWarnings("rawtypes")
    private static double adaptiveColumnWeight(TableColumn column) {
        String heading = adaptiveHeading(column);
        String semantic = String.valueOf(
            column.getProperties().getOrDefault("erp-header-semantic", "")
        ).toLowerCase(Locale.ROOT);

        if ("customer".equals(semantic) || "supplier".equals(semantic)
            || heading.contains("description") || heading.contains("address")
            || heading.contains("subject") || heading.contains("name")) return 1.8;
        if ("document".equals(semantic) || "status".equals(semantic)
            || "email".equals(semantic) || "whatsapp".equals(semantic)
            || "reminder".equals(semantic)) return 1.25;
        if ("currency".equals(semantic) || "calendar".equals(semantic)
            || "quantity".equals(semantic)) return 0.85;
        return 1.0;
    }

    @SuppressWarnings("rawtypes")
    private static String adaptiveHeading(TableColumn column) {
        Object stored = column.getProperties().get("erp-header-label");
        String heading = stored instanceof String value ? value : column.getText();
        return heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT);
    }


    /**
     * Shows the complete value when a normal text cell is visually clipped.
     * This keeps columns stable on hover and does not replace controller-owned
     * cell factories, graphics, editors, context menus, or business handlers.
     */
    @SuppressWarnings("rawtypes")
    private static void installCellValueTooltips(TableView table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-cell-tooltips"))) return;
        table.getProperties().put("erp-cell-tooltips", true);

        table.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            TableCell cell = findTableCell(event.getPickResult().getIntersectedNode());
            Object previous = table.getProperties().put("erp-hovered-table-cell", cell);
            if (previous instanceof TableCell previousCell && previousCell != cell) {
                clearManagedTooltip(previousCell);
            }
            if (cell == null || cell.isEmpty()) return;

            String value = cell.getText();
            if (value == null || value.isBlank()) {
                clearManagedTooltip(cell);
                return;
            }

            Text measurement = new Text(value);
            measurement.setFont(cell.getFont());
            double availableWidth = Math.max(0, cell.getWidth() - 18);
            boolean clipped = measurement.getLayoutBounds().getWidth() > availableWidth;

            if (clipped) {
                Tooltip tooltip = managedTooltip(cell);
                tooltip.setText(value);
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(460);
                cell.setTooltip(tooltip);
            } else {
                clearManagedTooltip(cell);
            }
        });

        table.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            Object previous = table.getProperties().remove("erp-hovered-table-cell");
            if (previous instanceof TableCell previousCell) {
                clearManagedTooltip(previousCell);
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private static TableCell findTableCell(Node node) {
        Node current = node;
        while (current != null && !(current instanceof TableCell)) {
            current = current.getParent();
        }
        return current instanceof TableCell cell ? cell : null;
    }

    private static Tooltip managedTooltip(TableCell<?, ?> cell) {
        Object existing = cell.getProperties().get("erp-managed-cell-tooltip");
        if (existing instanceof Tooltip tooltip) return tooltip;
        Tooltip tooltip = new Tooltip();
        cell.getProperties().put("erp-managed-cell-tooltip", tooltip);
        return tooltip;
    }

    private static void clearManagedTooltip(TableCell<?, ?> cell) {
        Object managed = cell.getProperties().get("erp-managed-cell-tooltip");
        if (managed != null && cell.getTooltip() == managed) {
            cell.setTooltip(null);
        }
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyTableProfile(TableView table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-preserve-resize-policy"))) return;
        String profile = detectTableProfile(table);
        table.getProperties().put("erp-table-profile", profile);
        String profileClass = "erp-table-profile-" + profile;
        if (!table.getStyleClass().contains(profileClass)) table.getStyleClass().add(profileClass);

        int columns = table.getColumns().size();
        switch (profile) {
            case "import", "permission" -> table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            case "register" -> table.setColumnResizePolicy(columns >= 9
                ? TableView.UNCONSTRAINED_RESIZE_POLICY
                : TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            case "master", "history", "administration" -> table.setColumnResizePolicy(columns >= 8
                ? TableView.UNCONSTRAINED_RESIZE_POLICY
                : TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            case "line-item", "detail", "dialog" ->
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            default -> table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        }
    }

    private static String detectTableProfile(TableView<?> table) {
        String styles = String.join(" ", table.getStyleClass()).toLowerCase(Locale.ROOT);
        String id = table.getId() == null ? "" : table.getId().toLowerCase(Locale.ROOT);
        String key = styles + " " + id;
        if (key.contains("import-preview")) return "import";
        if (key.contains("permission")) return "permission";
        if (key.contains("role-table") || key.contains("user-access") || key.contains("user-table")) return "administration";
        if (key.contains("report-table") || key.contains("dashboard") || key.contains("summary-table")) return "summary";
        if (key.contains("line-item") || key.contains("tablelines") || key.contains("entry-table")) return "line-item";
        if (key.contains("detail") || id.equals("items")) return "detail";
        if (key.contains("dialog-table") || key.contains("compact-table")) return "dialog";
        if (key.contains("history") || key.contains("communication") || key.contains("backup")
            || key.contains("reminder") || key.contains("update")) return "history";
        if (key.contains("entity") || key.contains("master") || key.contains("inventory")
            || key.contains("customer") || key.contains("supplier") || key.contains("item")) return "master";
        if (key.contains("register") || key.contains("sales") || key.contains("purchase")
            || key.contains("quotation") || key.contains("return") || key.contains("payment")
            || key.contains("operation")) return "register";
        return table.getColumns().size() >= 9 ? "register" : "responsive";
    }

    /**
     * Re-applies table headers after the control is attached and after JavaFX creates
     * or replaces its skin. This is intentionally idempotent and fixes the startup
     * difference between launching directly in light mode and switching themes later.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installHeaderLifecycleRefresh(TableView table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-header-lifecycle"))) return;
        table.getProperties().put("erp-header-lifecycle", true);

        // Column graphics do not depend on a Scene. A single skin callback is
        // sufficient for JavaFX-created header nodes and avoids two deferred
        // full-column traversals for every navigation.
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null && !Boolean.TRUE.equals(table.getProperties().get("erp-header-skin-pass"))) {
                table.getProperties().put("erp-header-skin-pass", true);
                Platform.runLater(() -> decorateColumns(table.getColumns()));
            }
        });
    }

    /** Recursively applies the same icon vocabulary to leaf and grouped headers. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void decorateColumns(java.util.List<TableColumn> columns) {
        for (TableColumn column : columns) {
            if (!column.getColumns().isEmpty()) decorateColumns(column.getColumns());
            String storedHeading = (String) column.getProperties().get("erp-header-label");
            String heading = storedHeading != null ? storedHeading
                : column.getText() == null ? "" : column.getText().trim();
            String columnId = column.getId() == null ? "" : column.getId().trim();
            String semantic = headerSemantic(heading, columnId);
            if (semantic == null && !heading.isBlank()) semantic = fallbackHeaderSemantic(heading, columnId);
            if (semantic != null && !Boolean.TRUE.equals(column.getProperties().get("erp-header-preserve"))) {
                String signature = heading + "|" + semantic;
                if (signature.equals(column.getProperties().get("erp-header-signature")) && column.getGraphic() != null) {
                    applyResponsiveWidth(column, heading, semantic);
                    continue;
                }
                column.getProperties().put("erp-header-signature", signature);
                column.getProperties().put("erp-header-label", heading);
                column.setText("");
                column.setGraphic(tableHeader(heading, semantic));
                if (!column.getStyleClass().contains("erp-icon-table-column")) {
                    column.getStyleClass().add("erp-icon-table-column");
                }
                column.getProperties().put("erp-header-semantic", semantic);
                applyResponsiveWidth(column, heading, semantic);
            }

            // Do not replace factories installed by business controllers. This
            // renderer is only for ordinary string status columns.
            if (isStatusHeading(heading)
                && column.getCellFactory() == TableColumn.DEFAULT_CELL_FACTORY) {
                final String statusSemantic = semantic;
                column.setCellFactory(ignored -> new SemanticStatusCell(statusSemantic));
            }
        }
    }


    @SuppressWarnings("rawtypes")
    private static void applyResponsiveWidth(TableColumn column, String heading, String semantic) {
        String h = heading == null ? "" : heading.toLowerCase(Locale.ROOT);
        TableView<?> table = column.getTableView();
        String profile = table == null ? "responsive"
            : String.valueOf(table.getProperties().getOrDefault("erp-table-profile", "responsive"));
        double min;
        if ("actions".equals(semantic)) min = 92;
        else if ("quantity".equals(semantic) && (h.equals("no.") || h.equals("#") || h.equals("qty"))) min = 68;
        else if ("status".equals(semantic) || "email".equals(semantic) || "whatsapp".equals(semantic)) min = 116;
        else if ("calendar".equals(semantic) || "reminder".equals(semantic)) min = 118;
        else if ("currency".equals(semantic) || h.contains("amount") || h.contains("balance") || h.contains("paid")) min = 126;
        else if ("phone".equals(semantic)) min = 122;
        else if ("customer".equals(semantic) || "supplier".equals(semantic) || h.contains("description")
            || h.contains("subject") || h.contains("address") || h.contains("error")) min = 160;
        else min = 104;

        if ("summary".equals(profile)) min = Math.min(min, 132);
        if ("dialog".equals(profile) || "detail".equals(profile)) min = Math.min(min, 118);
        if (column.getMinWidth() < min) column.setMinWidth(min);
        if (column.getPrefWidth() < min) column.setPrefWidth(min);
        if ("actions".equals(semantic)) { column.setMaxWidth(120); column.setSortable(false); }
        if (h.equals("no.") || h.equals("#")) column.setMaxWidth(72);
    }

    /** Builds a stable icon-and-label header that survives JavaFX skin rebuilds. */
    public static Node tableHeader(String label, String semantic) {
        Label title = new Label(label);
        title.getStyleClass().add("erp-table-header-label");
        HBox header = new HBox(6, IconFactory.headerIcon(semantic), title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMouseTransparent(true);
        header.getStyleClass().add("erp-table-header-content");
        return header;
    }

    private static boolean isStatusHeading(String heading) {
        String value = heading.toLowerCase(Locale.ROOT);
        return value.contains("status") || value.contains("payment due")
            || value.equals("email") || value.contains("whatsapp");
    }

    /** Maps every common ERP table heading to a meaningful business icon. */
    private static String headerSemantic(String heading, String columnId) {
        String value = heading == null ? "" : heading.toLowerCase(Locale.ROOT)
            .replace("&amp;", "and").replace("&", "and").replaceAll("\\s+", " ").trim();
        String id = columnId == null ? "" : columnId.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ").trim();
        String key = (value + " " + id).trim();

        if (value.equals("no.") || value.equals("no") || value.equals("sr.")
            || value.equals("sr. no.") || value.equals("row") || value.equals("#")
            || id.contains("serial") || id.contains("row number")) return "quantity";
        if ((value.isBlank() || value.equals("✓") || value.equals("select"))
            && !id.contains("select")) return null;

        if (key.contains("whatsapp") || key.contains("whats app")) return "whatsapp";
        if (key.contains("email") || key.contains("mail status")) return "email";
        if (key.contains("action") || key.contains("menu") || key.contains("option")) return "actions";
        if (key.contains("attachment") || key.contains("file attached")) return "attachment";
        if (key.contains("phone") || key.contains("mobile") || key.contains("contact number")) return "phone";

        if (key.contains("payment due") || key.contains("due date") || key.contains("follow up")
            || key.contains("reminder") || key.contains("valid upto") || key.contains("valid until")) return "reminder";
        if (key.contains("priority") || key.contains("severity")) return "warning";
        if (key.contains("status") || key.contains("state") || key.contains("result error")) return "status";
        if (key.contains("mfa") || key.contains("access") || key.contains("permission") || key.contains("role")) return "lock";

        if (key.contains("date") || key.contains("created on") || key.contains("created at")
            || key.contains("updated") || key.contains("last login") || key.contains("timestamp")
            || key.contains("time")) return "calendar";
        if (key.contains("supplier") || key.contains("vendor")) return "supplier";
        if (key.contains("customer supplier") || key.equals("party") || key.contains("party name")
            || key.contains("received from")) return "customer";
        if (key.equals("user") || key.contains("username") || key.contains("created by")
            || key.contains("updated by") || key.contains("employee") || key.contains("assignee")) return "user";
        if (key.contains("customer") || key.contains("sales person") || key.contains("salesperson")
            || key.contains("full name")) return "customer";
        if (key.contains("address") || key.contains("location") || key.contains("branch")
            || key.contains("department") || key.contains("city") || key.contains("state")) return "location";

        if (key.contains("gst") || key.contains("tax") || key.contains("vat")) return "tax";
        if (key.contains("pan") || key.contains("hsn") || key.contains("sku") || key.contains("barcode")
            || key.endsWith(" code") || key.equals("code") || key.contains(" id")) return "identity";

        if (key.contains("invoice") || key.contains("quotation") || key.contains("voucher")
            || key.contains("reference") || key.contains("document") || key.contains("converted to")
            || key.contains("order no") || key.contains("bill no") || key.endsWith(" no")
            || key.endsWith(" no.")) return "document";
        if (key.contains("return") || key.contains("refund")) return "return";
        if (key.contains("backup")) return "backup";
        if (key.contains("source") || key.contains("channel") || key.contains("mode")) return "import";

        if (key.contains("item") || key.contains("product") || key.contains("material")) return "item";
        if (key.contains("qty") || key.contains("quantity") || key.contains("stock")
            || key.contains("unit") || key.contains("available") || key.contains("reserved")
            || key.contains("size") || key.contains("in stock")) return "quantity";
        if (key.equals("type") || key.contains("movement type") || key.contains("transaction type")) return "category";
        if (key.contains("category") || key.contains("brand")) return "master";

        if (key.contains("amount") || value.equals("paid") || key.startsWith("paid ") || key.contains(" paid ")
            || key.contains("balance") || key.contains("rate") || key.contains("price")
            || key.contains("total") || key.contains("opening balance") || key.contains("allocate")
            || key.contains("receivable") || key.contains("payable")) return "currency";
        if (key.contains("reason") || key.contains("note") || key.contains("remark")
            || key.contains("description") || key.contains("subject")) return "notes";
        if (key.equals("value")) return "master";
        if (key.equals("user") || key.contains("created by") || key.contains("updated by")) return "user";

        // Unknown headings should not all receive the same document icon.
        return null;
    }


    /** Ensures every visible table heading receives a stable, colourful semantic icon. */
    private static String fallbackHeaderSemantic(String heading, String columnId) {
        String key = ((heading == null ? "" : heading) + " " + (columnId == null ? "" : columnId))
            .toLowerCase(Locale.ROOT);
        if (key.contains("version")) return "update";
        if (key.contains("path") || key.contains("folder")) return "folder";
        if (key.contains("host") || key.contains("port") || key.contains("provider")) return "settings";
        if (key.contains("module") || key.contains("feature")) return "master";
        if (key.contains("read") || key.contains("create") || key.contains("update") || key.contains("delete")) return "permission";
        if (key.contains("frequency") || key.contains("schedule")) return "calendar";
        if (key.contains("method") || key.contains("mode")) return "category";
        if (key.contains("name") || key.contains("title")) return "document";
        return "document";
    }

    /** Default icon-plus-label renderer for status columns without custom logic. */
    private static final class SemanticStatusCell extends TableCell<Object, Object> {
        private final String columnSemantic;

        private SemanticStatusCell(String columnSemantic) {
            this.columnSemantic = columnSemantic;
        }

        @Override protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("status-positive", "status-warning", "status-negative", "status-neutral");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String value = String.valueOf(item).trim();
            String state = state(value);
            String semantic;
            if ("email".equals(columnSemantic)) semantic = "email";
            else if ("whatsapp".equals(columnSemantic)) semantic = "whatsapp";
            else if ("reminder".equals(columnSemantic)) semantic = "reminder";
            else if ("document".equals(columnSemantic) || "status".equals(columnSemantic)) {
                semantic = state.equals("positive") ? "complete"
                    : state.equals("negative") ? "error"
                    : state.equals("warning") ? "status" : "document";
            } else {
                semantic = state.equals("positive") ? "complete"
                    : state.equals("negative") ? "error"
                    : columnSemantic == null ? "warning" : columnSemantic;
            }
            Label label = new Label(value);
            String colour = state.equals("positive") ? "#16a34a"
                : state.equals("negative") ? "#dc2626"
                : state.equals("warning") ? "#d97706" : "#2563eb";
            HBox content = new HBox(6, IconFactory.compactIcon(semantic, 15), label);
            content.setAlignment(Pos.CENTER_LEFT);
            content.getStyleClass().add("erp-status-content");
            setText(null);
            setGraphic(content);
            getStyleClass().add("status-" + state);
        }

        private static String state(String text) {
            String value = text.toLowerCase(Locale.ROOT);
            if (value.contains("not sent") || value.contains("failed") || value.contains("error")
                || value.contains("overdue") || value.contains("rejected") || value.contains("cancel")
                || value.contains("out of stock")) return "negative";
            if (value.contains("paid") || value.contains("complete") || value.contains("approved")
                || value.contains("refunded") || value.equals("sent") || value.contains("received")
                || value.contains("delivered") || value.contains("active") || value.contains("success")) return "positive";
            if (value.contains("pending") || value.contains("partial") || value.contains("due")
                || value.contains("open") || value.contains("draft") || value.contains("not set")) return "warning";
            return "neutral";
        }
    }

    /**
     * Reuses the screen's own business handler instead of duplicating CRUD logic.
     * The closest visible button whose label matches the requested action is fired.
     */
    private static void fireNamedAction(TableView<?> table, String... names) {
        Node root = table.getScene() == null ? table : table.getScene().getRoot();
        ButtonBase action = findAction(root, names);
        if (action != null && !action.isDisabled()) action.fire();
    }

    private static ButtonBase findAction(Node node, String... names) {
        if (node instanceof ButtonBase button && button.isVisible()) {
            String label = button.getText() == null ? "" : button.getText().toLowerCase();
            for (String name : names) if (label.contains(name)) return button;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ButtonBase found = findAction(child, names);
                if (found != null) return found;
            }
        }
        return null;
    }

}
