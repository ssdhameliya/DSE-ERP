package org.example.service;

import org.example.api.auth.AuthApiClient;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

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
    private static final Set<Runnable> CHANGE_LISTENERS = new CopyOnWriteArraySet<>();

    private PermissionService() {}

    public static boolean allowed(String permissionKey) {
        if (SessionService.current() == null) return false;
        String role = normalizeRole(SessionService.current().getRole());
        if (SessionService.isAdminRole(role)) return true;
        ensureLoaded(role);
        String wanted = normalize(permissionKey);
        if (cacheLoaded && role.equals(cachedRole)) return cachedAllowed.contains(wanted);
        return legacyAllowed(role, wanted);
    }

    /** Enforces a desktop-local action such as export, update installation or rollback. */
    public static void require(String permissionKey, String operation) {
        if (!allowed(permissionKey)) {
            String label = operation == null || operation.isBlank() ? "perform this action" : operation.trim();
            throw new SecurityException("Permission denied: " + permissionKey + " is required to " + label + ".");
        }
    }

    /** Subscribe a live shell/control surface to permission changes. */
    public static void addChangeListener(Runnable listener) {
        if (listener != null) CHANGE_LISTENERS.add(listener);
    }

    public static void removeChangeListener(Runnable listener) {
        if (listener != null) CHANGE_LISTENERS.remove(listener);
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
        if (SessionService.current() != null) {
            String role = normalizeRole(SessionService.current().getRole());
            // ADMIN is a system policy, not a permission-matrix row. Never make an
            // administrator's desktop access depend on the permission endpoint being available.
            if (SessionService.isAdminRole(role)) {
                cachedRole = role;
                cacheLoaded = true;
            } else {
                ensureLoaded(role, strict);
            }
        }
        notifyChangeListeners();
    }

    private static void notifyChangeListeners() {
        for (Runnable listener : CHANGE_LISTENERS) {
            try { listener.run(); } catch (RuntimeException ignored) { /* one view must not block others */ }
        }
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
        return SessionService.canonicalRole(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
