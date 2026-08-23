package org.example.shared;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared presentation metadata for the permission catalog. The database remains the
 * source of truth for which permission keys exist; unknown future modules still
 * render automatically using the fallback metadata below.
 */
public final class PermissionCatalog {
    public static final List<String> COMMON_ACTIONS = List.of("VIEW", "CREATE", "EDIT", "DELETE", "EXPORT", "APPROVE");

    public record ModuleMeta(String key, String label, String category, String iconKey, int order) {}
    public record Template(String key, String label, String description) {
        @Override public String toString() { return label; }
    }

    public static final Template READ_ONLY = new Template("READ_ONLY", "Read Only", "Grant view access without change permissions");
    public static final Template SALES_STANDARD = new Template("SALES_STANDARD", "Sales Standard", "Sales, quotation and customer workflows with safe day-to-day actions");
    public static final Template MANAGER_STANDARD = new Template("MANAGER_STANDARD", "Manager Standard", "Broad business access without protected administration actions");
    public static final Template BUSINESS_FULL = new Template("BUSINESS_FULL", "Business Full Access", "All business capabilities while protected administration stays separate");
    public static final List<Template> TEMPLATES = List.of(READ_ONLY, SALES_STANDARD, MANAGER_STANDARD, BUSINESS_FULL);

    private static final Map<String, ModuleMeta> MODULES = Map.ofEntries(
            entry("DASHBOARD", "Dashboard", "Dashboard & Insights", "dashboard", 10),
            entry("SALES", "Sales Register", "Sales", "sales", 20),
            entry("QUOTATION", "Quotation", "Sales", "quotation", 21),
            entry("CUSTOMERS", "Customers", "Sales", "customer", 22),
            entry("COMMUNICATION", "Communication", "Sales", "email", 23),
            entry("REMINDERS", "Reminders", "Sales", "reminder", 24),
            entry("PURCHASE", "Purchase Register", "Purchase", "purchase", 30),
            entry("SUPPLIERS", "Suppliers", "Purchase", "supplier", 31),
            entry("INVENTORY", "Inventory / Item Master", "Inventory", "inventory", 40),
            entry("IMPORT", "Data Import", "Inventory", "import", 41),
            entry("BANK_EXPENSE", "Finance & Banking", "Finance & Banking", "bank", 50),
            entry("MASTERS", "Masters", "Masters", "master", 60),
            entry("REPORTS", "Reports", "Reports", "report", 70),
            entry("DOCUMENT_STUDIO", "Document Studio", "Document Studio", "document", 80),
            entry("USERS", "User Access", "Administration", "users", 90),
            entry("SETTINGS", "Settings", "Administration", "settings", 91),
            entry("BACKUP", "Backup & Restore", "Administration", "backup", 92),
            entry("APPLICATION_UPDATES", "Application Updates", "Administration", "update", 93),
            entry("SAFE_ROLLBACK", "Safe Rollback", "Administration", "rollback", 94)
    );

    private static final Set<String> SALES_MODULES = Set.of(
            "DASHBOARD", "SALES", "QUOTATION", "CUSTOMERS", "COMMUNICATION", "REMINDERS", "REPORTS", "INVENTORY");
    private static final Set<String> ADMINISTRATION_MODULES = Set.of(
            "USERS", "SETTINGS", "BACKUP", "APPLICATION_UPDATES", "SAFE_ROLLBACK");

    private PermissionCatalog() {}

    public static ModuleMeta module(String key) {
        String normalized = normalize(key);
        ModuleMeta known = MODULES.get(normalized);
        if (known != null) return known;
        String label = title(normalized.replace('_', ' '));
        return new ModuleMeta(normalized, label, "Other", "permission", 999);
    }

    public static boolean isCommonAction(String action) {
        return COMMON_ACTIONS.contains(normalize(action));
    }

    public static String actionLabel(String action) {
        String normalized = normalize(action);
        return switch (normalized) {
            case "EXPORT_PDF" -> "Export PDF";
            case "MANAGE_TEMPLATES" -> "Manage Templates";
            case "MANAGE_ROLES" -> "Manage Roles";
            case "MANAGE_PERMISSIONS" -> "Manage Permissions";
            default -> title(normalized.replace('_', ' '));
        };
    }

    public static boolean templateAllows(Template template, String module, String action) {
        if (template == null) return false;
        String m = normalize(module);
        String a = normalize(action);
        return switch (template.key()) {
            case "READ_ONLY" -> !ADMINISTRATION_MODULES.contains(m) && "VIEW".equals(a);
            case "SALES_STANDARD" -> salesTemplate(m, a);
            case "MANAGER_STANDARD" -> managerTemplate(m, a);
            case "BUSINESS_FULL" -> !ADMINISTRATION_MODULES.contains(m);
            default -> false;
        };
    }

    private static boolean salesTemplate(String module, String action) {
        if (!SALES_MODULES.contains(module)) return false;
        if ("DASHBOARD".equals(module) || "REPORTS".equals(module) || "INVENTORY".equals(module)) {
            return "VIEW".equals(action) || "EXPORT".equals(action);
        }
        if ("REMINDERS".equals(module)) {
            return Set.of("VIEW", "CREATE", "EDIT", "COMPLETE", "SNOOZE").contains(action);
        }
        if ("COMMUNICATION".equals(module)) {
            return Set.of("VIEW", "CREATE", "EDIT", "RESEND").contains(action);
        }
        return Set.of("VIEW", "CREATE", "EDIT", "EXPORT").contains(action);
    }

    private static boolean managerTemplate(String module, String action) {
        if (ADMINISTRATION_MODULES.contains(module)) return false;
        if ("DOCUMENT_STUDIO".equals(module)) {
            return Set.of("VIEW", "CREATE", "EDIT", "EXPORT_PDF", "MANAGE_TEMPLATES").contains(action);
        }
        if ("BANK_EXPENSE".equals(module)) {
            return Set.of("VIEW", "CREATE", "EDIT", "EXPORT", "RECONCILE", "APPROVE").contains(action);
        }
        return !"DELETE".equals(action) || Set.of("SALES", "PURCHASE", "QUOTATION", "CUSTOMERS", "SUPPLIERS", "INVENTORY", "MASTERS").contains(module);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Map.Entry<String, ModuleMeta> entry(String key, String label, String category, String icon, int order) {
        return Map.entry(key, new ModuleMeta(key, label, category, icon, order));
    }

    private static String title(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : value.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
