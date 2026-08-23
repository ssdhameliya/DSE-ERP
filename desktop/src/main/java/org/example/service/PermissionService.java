package org.example.service;

import org.example.api.admin.AdminApiClient;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Desktop permission facade backed by the saved server Permission Matrix. ADMIN
 * remains full access. If the admin API is temporarily unavailable the legacy
 * role defaults are used so navigation never crashes during startup.
 */
public final class PermissionService {
    private static final Set<String> MANAGER_DENIED = Set.of("usersview", "backupview", "settingsview");
    private static final Set<String> SALES_ALLOWED = Set.of("salesview", "quotationview", "customersview", "remindersview");
    private static volatile String cachedRole;
    private static volatile Set<String> cachedAllowed = Set.of();
    private static volatile boolean cacheLoaded;

    private PermissionService() {}

    public static boolean allowed(String permissionKey) {
        if (SessionService.current() == null) return false;
        String role = normalizeRole(SessionService.current().getRole());
        if ("ADMIN".equals(role)) return true;
        ensureLoaded(role);
        String wanted = normalize(permissionKey);
        if (cacheLoaded && role.equals(cachedRole)) return cachedAllowed.contains(wanted);
        return legacyAllowed(role, wanted);
    }

    /** Refresh after login or after an administrator saves the matrix. */
    public static synchronized void refresh() {
        cachedRole = null; cachedAllowed = Set.of(); cacheLoaded = false;
        if (SessionService.current() != null) ensureLoaded(normalizeRole(SessionService.current().getRole()));
    }

    private static synchronized void ensureLoaded(String role) {
        if (cacheLoaded && role.equals(cachedRole)) return;
        try {
            Set<String> allowed = new HashSet<>();
            for (var p : new AdminApiClient().permissions(role)) {
                if (p.allowed()) allowed.add(normalize(p.module()+"."+p.action()));
            }
            cachedRole = role; cachedAllowed = Set.copyOf(allowed); cacheLoaded = true;
        } catch (Exception ignored) {
            cachedRole = role; cachedAllowed = Set.of(); cacheLoaded = false;
        }
    }

    private static boolean legacyAllowed(String role, String wanted) {
        if ("MANAGER".equals(role)) return !MANAGER_DENIED.contains(wanted);
        if ("SALES".equals(role)) return SALES_ALLOWED.contains(wanted);
        return false;
    }

    private static String normalizeRole(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return role;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
