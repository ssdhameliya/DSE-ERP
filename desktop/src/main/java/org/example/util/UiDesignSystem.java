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
        if (containsAny(styles, "approved-amber-button", "erp-button-amber")) {
            role(button, "amber"); return;
        }
        if (containsAny(styles, "approved-indigo-button", "erp-button-indigo")) {
            role(button, "indigo"); return;
        }
        if (containsAny(styles, "approved-emerald-button", "erp-button-emerald")) {
            role(button, "emerald"); return;
        }
        if (containsAny(styles, "approved-sky-button", "erp-button-sky")) {
            role(button, "sky"); return;
        }
        if (containsAny(styles, "approved-purple-button", "erp-button-purple")) {
            role(button, "purple"); return;
        }
        if (containsAny(styles, "approved-cyan-button", "erp-button-cyan")) {
            role(button, "cyan"); return;
        }
        if (containsAny(styles, "approved-violet-button", "erp-button-violet")) {
            role(button, "violet"); return;
        }
        if (containsAny(styles, "approved-coral-button", "erp-button-coral")) {
            role(button, "coral"); return;
        }
        if (containsAny(styles, "approved-slate-button", "erp-button-slate")) {
            role(button, "slate"); return;
        }
        if (containsAny(styles, "approved-danger-ghost-button", "erp-button-danger-ghost")) {
            role(button, "danger-ghost"); return;
        }
        if (containsAny(styles, "copy-ref-button", "copy-button")) {
            role(button, "copy-pill"); return;
        }
        if (containsAny(styles, "approved-row-action", "table-action", "row-action")) {
            role(button, "row-action"); return;
        }

        // Navigation, tabs and pagers must remain neutral
        if (containsAny(styles, "report-nav-button", "workspace-tab", "tab-button", "nav-button", "nav-parent", "pager-button")) {
            role(button, "secondary"); return;
        }

        // Table action menus and obvious universal icon buttons remain compact.
        if (button instanceof MenuButton && (text.isBlank() || text.equals("actions") || text.equals("...") || text.contains("option"))) {
            role(button, "row-action"); return;
        }
        if (text.isBlank()) {
            role(button, "icon"); return;
        }

        // Contextual semantic roles based on action text (zero color collision):
        if (containsAny(text, "delete", "reject", "remove", "void", "reverse", "discard")) {
            role(button, "danger");
        } else if (containsAny(text, "approve", "reconcile", "complete", "confirm match", "apply adjustment")) {
            role(button, "success");
        } else if (containsAny(text, "refresh", "reload", "sync", "recalculate", "refreshing")) {
            role(button, "emerald");
        } else if (containsAny(text, "reset", "clear", "clear-filters", "clear history", "reset template", "undo")) {
            role(button, "coral");
        } else if (containsAny(text, "download", "pull data")) {
            role(button, "sky");
        } else if (containsAny(text, "view", "preview", "inspect", "drill", "360")) {
            role(button, "cyan");
        } else if (containsAny(text, "open", "browse", "choose file", "browse file", "attach", "folder")) {
            role(button, "indigo");
        } else if (containsAny(text, "excel", "spreadsheet", "export excel", "export xls", "csv")) {
            role(button, "emerald");
        } else if (containsAny(text, "email", "send email", "mail")) {
            role(button, "sky");
        } else if (containsAny(text, "edit", "modify", "rename", "change")) {
            role(button, "amber");
        } else if (containsAny(text, "duplicate", "clone", "repeat")) {
            role(button, "violet");
        } else if (containsAny(text, "pdf", "print", "tax invoice pdf")) {
            role(button, "purple");
        } else if (containsAny(text, "barcode", "label", "qr")) {
            role(button, "cyan");
        } else if (containsAny(text, "payment", "record payment", "pay now")) {
            role(button, "violet");
        } else if (containsAny(text, "return", "create return", "sales return", "purchase return")) {
            role(button, "coral");
        } else if (containsAny(text, "audit", "history", "log", "trail")) {
            role(button, "slate");
        } else if (containsAny(text, "close", "cancel", "dismiss")) {
            role(button, "danger-ghost");
        } else if (containsAny(styles, "approved-secondary-button", "erp-button-secondary", "secondary-button")) {
            role(button, "secondary");
        } else if (!text.contains("saved") && (text.equals("save") || text.startsWith("save ") || text.startsWith("save-")
                || containsAny(text, "create", "add ", "new ", "import", "generate", "submit", "sign in", "login"))) {
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
        return IconFactory.isNavigationControl(node);
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
