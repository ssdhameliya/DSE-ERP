package org.example.shortcut;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.example.config.ConfigManager;
import org.example.service.PermissionService;
import org.example.service.SessionService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central persisted keyboard shortcut registry.
 *
 * <p>Bindings are stored in workspace config.properties so each workspace can
 * choose its own keys. Global bindings are permission-aware; editor bindings
 * are scoped to the relevant Studio and may reuse keys used by another editor.
 * The registry is the single source of truth for execution, Settings, and help.</p>
 */
public final class ShortcutRegistry {
    public enum Scope { GLOBAL, PDF_STUDIO, EXCEL_STUDIO, MASTER_DATA }

    public enum Action {
        GLOBAL_SEARCH("global.search", "Global Search", "Application Actions", "Shortcut+K", Scope.GLOBAL, null),
        SAVE_CURRENT("global.saveCurrent", "Save Current", "Application Actions", "Shortcut+S", Scope.GLOBAL, null),
        EDIT_CURRENT("global.editCurrent", "Edit Current / Selected", "Application Actions", "Shortcut+E", Scope.GLOBAL, null),
        REFRESH_CURRENT("global.refreshCurrent", "Refresh Current Page", "Application Actions", "F5", Scope.GLOBAL, null),
        NEW_CURRENT("global.newCurrent", "New in Current Page", "Application Actions", "Shortcut+N", Scope.GLOBAL, null),
        OPEN_SELECTED("global.openSelected", "Open Selected", "Application Actions", "ENTER", Scope.GLOBAL, null),
        DELETE_SELECTED("global.deleteSelected", "Delete Selected", "Application Actions", "DELETE", Scope.GLOBAL, null),
        PRINT_CURRENT("global.printCurrent", "Print Current", "Application Actions", "Shortcut+P", Scope.GLOBAL, null),
        EXPORT_CURRENT("global.exportCurrent", "Export Current", "Application Actions", "Shortcut+Shift+E", Scope.GLOBAL, null),
        CLOSE_BACK("global.closeBack", "Close / Back", "Application Actions", "ESC", Scope.GLOBAL, null),
        NEW_SALE("global.newSale", "New Sale", "Quick Create & Navigation", "F9", Scope.GLOBAL, "SALES.VIEW"),
        NEW_QUOTATION("global.newQuotation", "New Quotation", "Quick Create & Navigation", "F3", Scope.GLOBAL, "QUOTATION.VIEW"),
        ITEM_MASTER("global.itemMaster", "Item Master", "Quick Create & Navigation", "F4", Scope.GLOBAL, "INVENTORY.VIEW"),
        MASTERS("global.masters", "Masters", "Quick Create & Navigation", "F10", Scope.GLOBAL, "MASTERS.VIEW"),
        BANK_STATEMENT("global.bankStatement", "Bank Statement", "Quick Create & Navigation", "F6", Scope.GLOBAL, "BANK_EXPENSE.VIEW"),
        BANK_ENTRY("global.bankEntry", "Bank Entry", "Quick Create & Navigation", "F7", Scope.GLOBAL, "BANK_EXPENSE.VIEW"),
        EXPENSE_ENTRY("global.expenseEntry", "Expense Entry", "Quick Create & Navigation", "F8", Scope.GLOBAL, "BANK_EXPENSE.VIEW"),

        PDF_UNDO("pdf.undo", "Undo", "PDF Studio", "Shortcut+Z", Scope.PDF_STUDIO, null),
        PDF_REDO("pdf.redo", "Redo", "PDF Studio", "Shortcut+Y", Scope.PDF_STUDIO, null),
        PDF_DUPLICATE("pdf.duplicate", "Duplicate Selected", "PDF Studio", "Shortcut+D", Scope.PDF_STUDIO, null),
        PDF_DELETE("pdf.delete", "Delete Selected", "PDF Studio", "DELETE", Scope.PDF_STUDIO, null),

        EXCEL_UNDO("excel.undo", "Undo", "Excel Studio", "Shortcut+Z", Scope.EXCEL_STUDIO, null),
        EXCEL_REDO("excel.redo", "Redo", "Excel Studio", "Shortcut+Y", Scope.EXCEL_STUDIO, null),
        EXCEL_REDO_ALT("excel.redoAlt", "Redo (Alternate)", "Excel Studio", "Shortcut+Shift+Z", Scope.EXCEL_STUDIO, null),
        EXCEL_COPY("excel.copy", "Copy Cell", "Excel Studio", "Shortcut+C", Scope.EXCEL_STUDIO, null),
        EXCEL_PASTE("excel.paste", "Paste Cell", "Excel Studio", "Shortcut+V", Scope.EXCEL_STUDIO, null),
        EXCEL_EDIT("excel.edit", "Edit Active Cell", "Excel Studio", "F2", Scope.EXCEL_STUDIO, null),
        EXCEL_CLEAR("excel.clear", "Clear Selected Cell / Range", "Excel Studio", "DELETE", Scope.EXCEL_STUDIO, null),

        MASTER_DELETE("master.delete", "Delete Selected Master", "Master Data", "DELETE", Scope.MASTER_DATA, "MASTERS.VIEW"),
        MASTER_EDIT("master.edit", "Edit Selected Master", "Master Data", "ENTER", Scope.MASTER_DATA, "MASTERS.VIEW"),
        MASTER_REFRESH("master.refresh", "Refresh Master Data", "Master Data", "F5", Scope.MASTER_DATA, "MASTERS.VIEW"),
        MASTER_NEW("master.new", "New Master Entry", "Master Data", "Shortcut+N", Scope.MASTER_DATA, "MASTERS.VIEW");

        private final String id;
        private final String label;
        private final String category;
        private final String defaultBinding;
        private final Scope scope;
        private final String permission;

        Action(String id, String label, String category, String defaultBinding, Scope scope, String permission) {
            this.id = id;
            this.label = label;
            this.category = category;
            this.defaultBinding = defaultBinding;
            this.scope = scope;
            this.permission = permission;
        }

        public String id() { return id; }
        public String label() { return label; }
        public String category() { return category; }
        public String defaultBinding() { return defaultBinding; }
        public Scope scope() { return scope; }
        public String permission() { return permission; }
    }

    private static final String CONFIG_PREFIX = "shortcut.";

    private ShortcutRegistry() { }

    public static List<Action> actions() { return List.of(Action.values()); }

    public static List<Action> actions(Scope scope) {
        return actions().stream().filter(action -> action.scope() == scope).toList();
    }

    public static String configuredBinding(Action action) {
        if (action == null) return "";
        // Shortcut preferences are personal. Fall back to the legacy workspace-level
        // key so existing installations keep their current bindings until each user saves.
        String legacy = ConfigManager.get(CONFIG_PREFIX + action.id(), action.defaultBinding());
        return normalize(ConfigManager.get(storageKey(action), legacy));
    }

    /** Persistent key for the currently signed-in user's binding. */
    public static String storageKey(Action action) {
        if (action == null) return CONFIG_PREFIX + "unknown";
        var user = SessionService.current();
        String username = user == null ? "" : user.getUsername();
        if (username == null || username.isBlank()) return CONFIG_PREFIX + action.id();
        String safeUser = username.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return CONFIG_PREFIX + "user." + safeUser + "." + action.id();
    }

    public static String defaultBinding(Action action) {
        return action == null ? "" : action.defaultBinding();
    }

    public static KeyCombination combination(Action action) {
        String configured = configuredBinding(action);
        if (configured.isBlank()) return null;
        try { return KeyCombination.valueOf(configured); }
        catch (Exception ignored) {
            try { return KeyCombination.valueOf(action.defaultBinding()); }
            catch (Exception impossible) { return null; }
        }
    }

    public static boolean matches(KeyEvent event, Action action) {
        KeyCombination combination = combination(action);
        return event != null && combination != null && combination.match(event);
    }

    public static boolean permitted(Action action) {
        return action != null && (action.permission() == null || action.permission().isBlank() || PermissionService.allowed(action.permission()));
    }

    /** Human-readable current binding for help/settings. */
    public static String display(Action action) {
        String raw = configuredBinding(action);
        return raw.isBlank() ? "Disabled" : raw.replace("Shortcut", "Ctrl/Cmd");
    }

    /**
     * Captures a JavaFX key press as a portable persisted binding.
     * Ctrl on Windows/Linux and Command on macOS are stored as Shortcut.
     */
    public static String fromEvent(KeyEvent event) {
        if (event == null || event.getCode() == null || isModifierOnly(event.getCode())) return "";
        List<String> parts = new ArrayList<>();
        if (event.isControlDown() || event.isMetaDown()) parts.add("Shortcut");
        if (event.isAltDown()) parts.add("Alt");
        if (event.isShiftDown()) parts.add("Shift");
        parts.add(event.getCode().getName());
        return String.join("+", parts);
    }

    public static boolean isValidBinding(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return true; // blank intentionally disables an action
        try { KeyCombination.valueOf(normalized); return true; }
        catch (Exception ignored) { return false; }
    }

    /**
     * Validates syntax and collisions. Same key may be reused by PDF and Excel
     * because those scopes are mutually exclusive. Dashboard suppresses global
     * navigation while a dedicated editor owns the keyboard, so cross-scope reuse is safe.
     */
    public static List<String> validate(Map<Action, String> draft) {
        Map<Action, String> effective = new EnumMap<>(Action.class);
        for (Action action : Action.values()) {
            String value = draft != null && draft.containsKey(action) ? draft.get(action) : configuredBinding(action);
            value = normalize(value);
            if (!isValidBinding(value)) return List.of(action.label() + ": invalid key combination '" + value + "'.");
            effective.put(action, value);
        }

        List<String> errors = new ArrayList<>();
        Action[] values = Action.values();
        for (int i = 0; i < values.length; i++) {
            Action a = values[i];
            String av = effective.get(a);
            if (av == null || av.isBlank()) continue;
            for (int j = i + 1; j < values.length; j++) {
                Action b = values[j];
                String bv = effective.get(b);
                if (bv == null || bv.isBlank() || !sameBinding(av, bv)) continue;
                boolean conflicts = a.scope() == b.scope();
                if (conflicts) errors.add(displayRaw(av) + " is assigned to both " + a.label() + " and " + b.label() + ".");
            }
        }
        return List.copyOf(errors);
    }

    public static void save(Map<Action, String> values) {
        List<String> errors = validate(values);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        for (Action action : Action.values()) {
            if (values == null || !values.containsKey(action)) continue;
            String value = normalize(values.get(action));
            ConfigManager.setWithoutSaving(storageKey(action), value);
        }
        ConfigManager.save();
    }

    public static void reset(Action action) {
        if (action == null) return;
        ConfigManager.set(storageKey(action), action.defaultBinding());
    }

    public static Map<Action, String> defaults() {
        Map<Action, String> result = new LinkedHashMap<>();
        for (Action action : Action.values()) result.put(action, action.defaultBinding());
        return result;
    }

    /** Suppress global navigation keys while a dedicated editor owns keyboard semantics. */
    public static boolean isEditorTarget(Object target) {
        if (!(target instanceof Node node)) return false;
        for (Node current = node; current != null; current = current.getParent()) {
            if (Boolean.TRUE.equals(current.getProperties().get("dse.shortcut-capture"))) return true;
            if (current.getStyleClass().contains("excel-studio-root") || current.getStyleClass().contains("pdf-designer-root")
                    || current.getStyleClass().contains("pdf-studio-root")
                    || current.getStyleClass().contains("master-data-root")) return true;
        }
        return false;
    }

    private static boolean sameBinding(String a, String b) {
        return normalize(a).equalsIgnoreCase(normalize(b));
    }

    private static String displayRaw(String raw) { return raw.replace("Shortcut", "Ctrl/Cmd"); }

    private static boolean isModifierOnly(KeyCode code) {
        return code == KeyCode.SHIFT || code == KeyCode.CONTROL || code == KeyCode.ALT || code == KeyCode.META
               ;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String raw = value.trim();
        if (raw.equalsIgnoreCase("disabled") || raw.equalsIgnoreCase("none")) return "";
        // Keep JavaFX's canonical modifier words while tolerating what users type.
        raw = raw.replaceAll("(?i)ctrl/cmd", "Shortcut")
                .replaceAll("(?i)cmd", "Shortcut")
                .replaceAll("(?i)command", "Shortcut")
                .replaceAll("(?i)control", "Shortcut")
                .replaceAll("(?i)ctrl", "Shortcut")
                .replaceAll("\\s*\\+\\s*", "+");
        return raw;
    }
}
