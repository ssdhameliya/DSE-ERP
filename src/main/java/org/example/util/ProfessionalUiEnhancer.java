package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import javafx.application.Platform;
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
            for (Node child : parent.getChildrenUnmodifiable()) walk(child);
        }
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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        if (!table.getStyleClass().contains("erp-full-width-table")) {
            table.getStyleClass().add("erp-full-width-table");
        }

        decorateColumns(table.getColumns());
        installHeaderLifecycleRefresh(table);

        // Controllers add a number of business columns after FXML loading.
        // Keep header decoration live so those columns receive the exact same
        // icon-and-label treatment without requiring screen-specific code.
        if (!Boolean.TRUE.equals(table.getProperties().get("erp-column-listener"))) {
            table.getProperties().put("erp-column-listener", true);
            table.getColumns().addListener((ListChangeListener<TableColumn>) change ->
                Platform.runLater(() -> decorateColumns(table.getColumns())));
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
            if (selectionColumn && !Boolean.TRUE.equals(first.getProperties().get("erp-row-number"))) {
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
     * Re-applies table headers after the control is attached and after JavaFX creates
     * or replaces its skin. This is intentionally idempotent and fixes the startup
     * difference between launching directly in light mode and switching themes later.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installHeaderLifecycleRefresh(TableView table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-header-lifecycle"))) return;
        table.getProperties().put("erp-header-lifecycle", true);

        Runnable refresh = () -> Platform.runLater(() -> decorateColumns(table.getColumns()));

        table.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) refresh.run();
        });
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) refresh.run();
        });
        table.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null) refresh.run();
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
            if (semantic != null && !Boolean.TRUE.equals(column.getProperties().get("erp-header-preserve"))) {
                // Rebuild the complete icon+label graphic on every enhancement pass.
                // JavaFX can recreate TableHeaderRow nodes during initial CSS, theme
                // changes and skin installation. Reusing an old graphic is the cause
                // of missing icons on first light-mode load and stale/same icons later.
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
                column.setCellFactory(ignored -> new SemanticStatusCell(semantic));
            }
        }
    }


    @SuppressWarnings("rawtypes")
    private static void applyResponsiveWidth(TableColumn column,String heading,String semantic){
        String h=heading==null?"":heading.toLowerCase(Locale.ROOT);
        double min;
        if("actions".equals(semantic)) min=76;
        else if("quantity".equals(semantic)) min=62;
        else if("status".equals(semantic)||"email".equals(semantic)||"whatsapp".equals(semantic)) min=108;
        else if("calendar".equals(semantic)||"reminder".equals(semantic)) min=112;
        else if("currency".equals(semantic)||h.contains("amount")||h.contains("balance")||h.contains("paid")) min=118;
        else if("phone".equals(semantic)) min=118;
        else if("customer".equals(semantic)||"supplier".equals(semantic)||h.contains("description")||h.contains("subject")) min=145;
        else min=92;
        if(column.getMinWidth()<min)column.setMinWidth(min);
        if(column.getPrefWidth()<min)column.setPrefWidth(min);
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
