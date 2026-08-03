package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

import java.util.Locale;

/**
 * Installs the approved v2.1.9 visual language once per scene graph.
 * The installer is deliberately idempotent and never invokes applyCss/layout,
 * which keeps navigation lightweight on macOS and Windows HiDPI displays.
 */
public final class ApprovedUiSystem {
    private static final String DONE = "erp.approved-ui.done";

    private ApprovedUiSystem() {}

    public static void install(Node root) {
        if (root == null || Boolean.TRUE.equals(root.getProperties().get(DONE))) return;
        root.getProperties().put(DONE, true);
        add(root, "approved-ui", "approved-screen");
        walk(root);
    }

    private static void walk(Node node) {
        classify(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) walk(child);
        }
    }

    private static void classify(Node node) {
        if (Boolean.TRUE.equals(node.getProperties().get(DONE))) return;
        node.getProperties().put(DONE, true);

        String id = lower(node.getId());
        String styles = String.join(" ", node.getStyleClass()).toLowerCase(Locale.ROOT);

        if (node instanceof TableView<?> table) {
            add(table, "approved-table");
            if (table.getFixedCellSize() <= 0) table.setFixedCellSize(42);
        } else if (node instanceof ListView<?> || node instanceof TreeView<?>) {
            add(node, "approved-list");
        } else if (node instanceof TextInputControl || node instanceof ComboBoxBase<?> || node instanceof DatePicker || node instanceof Spinner<?>) {
            add(node, "approved-input");
        } else if (node instanceof MenuButton menu) {
            add(menu, "approved-menu-button");
            String text = lower(menu.getText());
            if (text.contains("action") || id.contains("action") || styles.contains("action")) {
                add(menu, "approved-row-action", "erp-action-menu");
            }
        } else if (node instanceof Button button) {
            classifyButton(button, id, styles);
        } else if (node instanceof Label label) {
            classifyLabel(label, id, styles);
        } else if (node instanceof TitledPane) {
            add(node, "approved-card");
        } else if (node instanceof Region region) {
            if (containsAny(styles, "kpi", "metric", "stat-card")) add(region, "approved-kpi");
            if (containsAny(styles, "card", "panel", "section", "container") && !styles.contains("sidebar")) {
                add(region, "approved-surface");
            }
            if (containsAny(styles, "filter", "search-bar")) add(region, "approved-filter-bar");
            if (containsAny(styles, "toolbar", "action-bar", "button-bar")) add(region, "approved-toolbar");
            if (styles.contains("header") && !styles.contains("table")) add(region, "approved-section-header");
        }
    }

    private static void classifyButton(Button button, String id, String styles) {
        add(button, "approved-button");
        String key = (lower(button.getText()) + " " + id + " " + styles).trim();
        if (containsAny(key, "delete", "remove", "void", "deactivate", "lock-account", "clear-all")) {
            add(button, "approved-danger-button");
        } else if (containsAny(key, "save", "create", "new", "apply", "submit", "record", "login", "continue", "finish", "confirm", "send", "generate", "restore")) {
            add(button, "approved-primary-button");
        } else {
            add(button, "approved-secondary-button");
        }
        String text = button.getText() == null ? "" : button.getText().trim();
        if (text.isBlank() || text.equals("...") || text.equals("⋮") || text.equals("⚙") || text.equals("+")) {
            add(button, "approved-icon-button");
        }
    }

    private static void classifyLabel(Label label, String id, String styles) {
        String key = (lower(label.getText()) + " " + id + " " + styles).trim();
        if (containsAny(styles, "page-title", "screen-title", "content-title")) add(label, "approved-page-title");
        if (containsAny(styles, "subtitle", "description", "caption")) add(label, "approved-page-subtitle");
        if (containsAny(key, "status", "active", "inactive", "pending", "paid", "overdue", "completed", "failed", "cancelled")) {
            add(label, "approved-status");
            if (containsAny(key, "active", "paid", "completed", "success", "online", "sent", "verified")) add(label, "approved-status-success");
            else if (containsAny(key, "failed", "overdue", "inactive", "cancelled", "error", "unsent")) add(label, "approved-status-danger");
            else add(label, "approved-status-warning");
        }
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static void add(Node node, String... styles) {
        for (String style : styles) {
            if (style != null && !style.isBlank() && !node.getStyleClass().contains(style)) node.getStyleClass().add(style);
        }
    }
}
