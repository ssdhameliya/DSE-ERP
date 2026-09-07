package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.Locale;

/**
 * DSE ERP visual design-system classifier.
 *
 * <p>This class adds semantic/canonical style classes only. It deliberately
 * leaves FXML structure, controller handlers, calculations and navigation
 * untouched. The two runtime theme files own the actual Light/Dark styling.</p>
 */
public final class UiDesignSystem {
    public static final String ROOT = "erp-ui-standard";
    public static final String SURFACE = "erp-unified-surface";
    public static final String INPUT = "erp-control-input";
    public static final String SEARCH = "erp-realtime-search";
    public static final String TABLE = "erp-table-standard";
    public static final String LINK = "erp-link-standard";
    public static final String BUTTON = "erp-control-button";

    private UiDesignSystem() {}

    /** Marks the page/dialog root as using the final visual system. */
    public static void markRoot(Node root) {
        add(root, ROOT);
    }

    /** Classifies one node. Safe to call repeatedly. */
    public static void decorate(Node node) {
        if (node == null) return;

        if (node instanceof TableView<?> table) {
            add(table, TABLE);
            add(table, "approved-table");
        }

        if (node instanceof TextInputControl input) {
            add(input, INPUT);
            add(input, "approved-input");
            if (isSearchControl(input)) {
                add(input, SEARCH);
                if (input.getAccessibleText() == null || input.getAccessibleText().isBlank()) {
                    input.setAccessibleText("Search records in real time");
                }
            }
        } else if (node instanceof ComboBoxBase<?> || node instanceof DatePicker
                || node instanceof ChoiceBox<?> || node instanceof Spinner<?> || node instanceof ColorPicker) {
            add(node, INPUT);
            add(node, "approved-input");
        }

        if (node instanceof Hyperlink link) {
            add(link, LINK);
        }

        if (node instanceof ButtonBase button && !isSidebarControl(button)) {
            add(button, BUTTON);
            add(button, "approved-button");
            applyButtonRole(button);
        }

        if (node instanceof Pane pane && isVisualSurface(pane)) {
            add(pane, SURFACE);
        }
    }

    private static void applyButtonRole(ButtonBase button) {
        String styles = styles(button);
        String text = safe(button.getText()).toLowerCase(Locale.ROOT);

        // Preserve explicit canonical semantics first.
        if (containsAny(styles, "approved-primary-button", "erp-button-primary", "primary-button")) {
            role(button, "primary"); return;
        }
        if (containsAny(styles, "approved-danger-button", "erp-button-danger", "danger-button")) {
            role(button, "danger"); return;
        }
        if (containsAny(styles, "approved-success-button", "erp-button-success")) {
            role(button, "success"); return;
        }
        if (containsAny(styles, "approved-warning-button", "erp-button-warning")) {
            role(button, "warning"); return;
        }
        if (containsAny(styles, "approved-secondary-button", "erp-button-secondary", "secondary-button")) {
            role(button, "secondary"); return;
        }
        if (containsAny(styles, "approved-row-action", "table-action", "row-action")) {
            role(button, "row-action"); return;
        }

        // Table action menus and obvious universal icon buttons remain compact.
        if (button instanceof MenuButton && (text.isBlank() || text.equals("actions") || text.equals("...") || text.equals("⋮"))) {
            role(button, "row-action"); return;
        }
        if (text.isBlank()) {
            role(button, "icon"); return;
        }

        if (containsAny(text, "delete", "reject", "remove", "void", "reverse", "discard")) {
            role(button, "danger");
        } else if (containsAny(text, "approve", "reconcile", "complete", "confirm match", "apply adjustment")) {
            role(button, "success");
        } else if (containsAny(text, "save", "create", "add ", "new ", "import", "generate", "submit", "sign in", "login")) {
            role(button, "primary");
        } else {
            role(button, "secondary");
        }
    }

    private static void role(ButtonBase button, String role) {
        button.getStyleClass().removeIf(s -> s != null && s.startsWith("erp-button-role-"));
        add(button, "erp-button-role-" + role);
    }

    private static boolean isSearchControl(TextInputControl input) {
        String id = safe(input.getId());
        String prompt = input instanceof TextField tf ? safe(tf.getPromptText()) : "";
        String styles = styles(input);
        String all = (id + " " + prompt + " " + styles).toLowerCase(Locale.ROOT);
        return all.contains("search") || all.contains("find records") || all.contains("filter records");
    }

    private static boolean isVisualSurface(Pane pane) {
        String s = styles(pane);
        if (s.isBlank()) return false;
        if (containsAny(s, "sidebar", "titlebar", "toolbar", "header", "footer", "canvas", "viewport",
                "scroll", "split-pane", "root", "overlay", "menu", "breadcrumb",
                "auth-", "splash-", "login-", "brand-panel", "row", "cell", "actions")) return false;

        // a generic word such as section/panel/workspace/detail must not
        // automatically become another shadowed card. That old broad classifier
        // created nested surfaces throughout Settings, Notifications, Shortcuts
        // and Global Search and made every focus/selection pulse much more costly.
        if (Boolean.TRUE.equals(pane.getProperties().get("erp.surface"))) return true;
        if (containsAny(s, "detail-drawer", "erp-detail-drawer-card", "inspector-card", "profile-card",
                "summary-card", "metric-card", "shortcut-list-card", "shortcut-editor-card")) return true;
        return s.contains("card") && !containsAny(s, "icon", "header", "action", "row", "cell");
    }

    private static boolean isSidebarControl(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("erp-sidebar")) return true;
        }
        return false;
    }

    private static String styles(Node node) {
        return String.join(" ", node.getStyleClass()).toLowerCase(Locale.ROOT);
    }

    private static String safe(String text) { return text == null ? "" : text.trim(); }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) return false;
        for (String needle : needles) if (needle != null && !needle.isBlank() && value.contains(needle)) return true;
        return false;
    }

    private static void add(Node node, String style) {
        if (node != null && style != null && !style.isBlank() && !node.getStyleClass().contains(style)) {
            node.getStyleClass().add(style);
        }
    }
}
