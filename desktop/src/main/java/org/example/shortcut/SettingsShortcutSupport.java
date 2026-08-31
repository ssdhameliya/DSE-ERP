package org.example.shortcut;

import org.example.shortcut.ShortcutRegistry.Action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Pure Settings shortcut vocabulary and validation helpers.
 *
 * <p>Phase 6 keeps JavaFX controls and event handlers in SettingsController,
 * while moving category/scope/description/normalization rules here so the
 * controller does not own reusable shortcut business vocabulary.</p>
 */
public final class SettingsShortcutSupport {
    private SettingsShortcutSupport() { }

    public static List<Action> managerActions() {
        return ShortcutRegistry.availableActions().stream().filter(action -> {
            String category = category(action);
            return "Application Actions".equals(category)
                    || "Quick Create".equals(category)
                    || "Navigation".equals(category);
        }).toList();
    }

    public static List<String> categories() {
        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        for (Action action : managerActions()) {
            String category = category(action);
            if (!categories.contains(category)) categories.add(category);
        }
        return categories;
    }

    public static List<String> validate(
            Map<Action, String> values,
            Map<Action, ShortcutRegistry.Scope> scopes
    ) {
        return ShortcutRegistry.validateActions(values, scopes, managerActions());
    }

    public static String category(Action action) {
        if (action == Action.GLOBAL_SEARCH) return "Application Actions";
        String category = action == null || action.category() == null || action.category().isBlank()
                ? "Application Actions" : action.category();
        return "Search & Filter".equals(category) ? "Application Actions" : category;
    }

    public static String categoryIcon(String category) {
        if (category == null) return "adjust";
        return switch (category) {
            case "Quick Create" -> "register";
            case "Navigation" -> "link";
            default -> "adjust";
        };
    }

    public static List<ShortcutRegistry.Scope> scopesForAction(Action action) {
        if (action == null) return List.of(ShortcutRegistry.Scope.GLOBAL);
        if (action.scope() == ShortcutRegistry.Scope.PDF_STUDIO) return List.of(ShortcutRegistry.Scope.PDF_STUDIO);
        if (action.scope() == ShortcutRegistry.Scope.EXCEL_STUDIO) return List.of(ShortcutRegistry.Scope.EXCEL_STUDIO);
        if (action.scope() == ShortcutRegistry.Scope.MASTER_DATA) return List.of(ShortcutRegistry.Scope.MASTER_DATA);
        return Arrays.asList(ShortcutRegistry.Scope.values());
    }

    public static ShortcutRegistry.Scope scopeFromLabel(String value, ShortcutRegistry.Scope fallback) {
        return ShortcutRegistry.Scope.fromStored(
                value,
                fallback == null ? ShortcutRegistry.Scope.GLOBAL : fallback
        );
    }

    public static String scopeHint(ShortcutRegistry.Scope scope) {
        if (scope == null) scope = ShortcutRegistry.Scope.GLOBAL;
        return switch (scope) {
            case GLOBAL -> "Runs across the ERP when the signed-in user has permission for the selected action.";
            case CURRENT_SCREEN -> "Runs only in the currently active page context; useful for Save, Edit, Refresh and other contextual commands.";
            case SALES -> "Runs only while a Sales or Quotation screen is active.";
            case PURCHASE -> "Runs only while a Purchase screen is active.";
            case INVENTORY -> "Runs only in Inventory or Item Master screens.";
            case CUSTOMERS -> "Runs only in Customer screens.";
            case SUPPLIERS -> "Runs only in Supplier screens.";
            case REPORTS -> "Runs only in Reports.";
            case COMMUNICATION -> "Runs only in Communication screens.";
            case SETTINGS -> "Runs only inside Settings.";
            case PDF_STUDIO -> "Runs only while PDF Studio owns the keyboard context.";
            case EXCEL_STUDIO -> "Runs only while Excel Studio owns the keyboard context.";
            case MASTER_DATA -> "Runs only inside the Master Data workspace.";
        };
    }

    public static String description(Action action) {
        return switch (action) {
            case GLOBAL_SEARCH -> "Opens Global Search across every ERP module permitted for the signed-in user.";
            case SAVE_CURRENT -> "Saves the current record or document when the active screen supports Save.";
            case EDIT_CURRENT -> "Edits the current or selected record when the active screen supports Edit.";
            case REFRESH_CURRENT -> "Refreshes the data on the current application page.";
            case NEW_CURRENT -> "Creates a new record in the current page when that page supports New.";
            case OPEN_SELECTED -> "Opens the currently selected record.";
            case DELETE_SELECTED -> "Deletes the selected record after the screen's normal permission and confirmation checks.";
            case PRINT_CURRENT -> "Prints the current document or page when printing is supported.";
            case EXPORT_CURRENT -> "Exports the current data when the active screen supports Export.";
            case CLOSE_BACK -> "Closes the current editor or returns to the previous application view.";
            case NEW_SALE -> "Opens a new Sales Invoice quickly.";
            case NEW_PURCHASE -> "Opens a new Purchase document quickly.";
            case NEW_QUOTATION -> "Opens a new Quotation quickly.";
            case ITEM_MASTER -> "Navigates directly to Item Master.";
            case MASTERS -> "Navigates directly to Master Data.";
            case BANK_STATEMENT -> "Navigates directly to Bank Statement reconciliation.";
            case BANK_ENTRY -> "Opens Bank Entry.";
            case EXPENSE_ENTRY -> "Opens Expense Entry.";
            default -> "Opens or executes " + action.label() + " using the selected shortcut scope.";
        };
    }

    public static String display(String raw) {
        if (raw == null || raw.isBlank()) return "Disabled";
        return raw.replace("Shortcut", "Ctrl/Cmd");
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String raw = value.trim();
        if (raw.equalsIgnoreCase("Disabled") || raw.equalsIgnoreCase("None")) return "";
        return raw.replaceAll("(?i)ctrl/cmd", "Shortcut")
                .replaceAll("(?i)cmd", "Shortcut")
                .replaceAll("(?i)command", "Shortcut")
                .replaceAll("(?i)control", "Shortcut")
                .replaceAll("(?i)ctrl", "Shortcut")
                .replaceAll("\\s*\\+\\s*", "+");
    }
}
