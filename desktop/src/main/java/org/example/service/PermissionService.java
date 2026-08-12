package org.example.service;

import java.util.Locale;
import java.util.Set;

/** Fixed desktop navigation matrix; the server independently enforces every API permission. */
public final class PermissionService {
    private static final Set<String> MANAGER_DENIED = Set.of(
            "usersview", "backupview", "settingsview"
    );
    private static final Set<String> SALES_ALLOWED = Set.of(
            "salesview", "quotationview", "customersview", "remindersview"
    );

    private PermissionService() {}

    public static boolean allowed(String permissionKey) {
        if (SessionService.current() == null) return false;
        String role = normalizeRole(SessionService.current().getRole());
        String wanted = normalize(permissionKey);
        if ("ADMIN".equals(role)) return true;
        if ("MANAGER".equals(role)) return !MANAGER_DENIED.contains(wanted);
        if ("SALES".equals(role)) return SALES_ALLOWED.contains(wanted);
        return false;
    }

    private static String normalizeRole(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("ADMINISTRATOR".equals(role)) return "ADMIN";
        if ("SALE".equals(role)) return "SALES";
        return role;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
