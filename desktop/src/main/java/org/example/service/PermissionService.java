package org.example.service;

import org.example.api.auth.AuthApiClient;

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
        refresh(false);
    }

    /** Login-time refresh: permission/API failures are fatal instead of silently falling back. */
    public static synchronized void refreshStrict() {
        refresh(true);
    }

    private static void refresh(boolean strict) {
        cachedRole = null; cachedAllowed = Set.of(); cacheLoaded = false;
        if (SessionService.current() != null) ensureLoaded(normalizeRole(SessionService.current().getRole()), strict);
    }

    private static synchronized void ensureLoaded(String role) {
        ensureLoaded(role, false);
    }

    private static synchronized void ensureLoaded(String role, boolean strict) {
        if (cacheLoaded && role.equals(cachedRole)) return;
        try {
            Set<String> allowed = new HashSet<>();
            for (var p : new AuthApiClient().effectivePermissions()) {
                allowed.add(normalize(p.module()+"."+p.action()));
            }
            cachedRole = role; cachedAllowed = Set.copyOf(allowed); cacheLoaded = true;
        } catch (org.example.api.ApiSession.AuthenticationRequiredException authenticationFailure) {
            cachedRole = role; cachedAllowed = Set.of(); cacheLoaded = false;
            throw authenticationFailure;
        } catch (Exception failure) {
            cachedRole = role; cachedAllowed = Set.of(); cacheLoaded = false;
            if (strict) {
                throw failure instanceof RuntimeException runtime ? runtime
                        : new IllegalStateException("Unable to load effective permissions", failure);
            }
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
