package org.example.service;

import org.example.model.AppUser;

import java.util.Locale;

public final class SessionService {
    private static AppUser current;

    private SessionService() {
    }

    public static void signIn(AppUser user) {
        ReferenceDataCache.invalidateAll();
        current = user;
    }

    public static AppUser current() {
        return current;
    }

    /** Canonical security role code used by all desktop permission decisions. */
    public static String canonicalRole(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        // Legacy databases/UI values used ADMINISTRATOR before ADMIN became the stable role code.
        return "ADMINISTRATOR".equals(role) ? "ADMIN" : role;
    }

    public static boolean isAdminRole(String value) {
        return "ADMIN".equals(canonicalRole(value));
    }

    public static boolean isAdmin() {
        return current != null && isAdminRole(current.getRole());
    }

    public static void clear() {
        ReferenceDataCache.invalidateAll();
        current = null;
        org.example.api.ApiSession.clear();
    }
}
