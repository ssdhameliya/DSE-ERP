package org.example.server.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return user;
    }

    public static boolean isSales() {
        return "SALES".equalsIgnoreCase(require().role());
    }

    public static boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if ("ADMIN".equalsIgnoreCase(require().role())) return true;
        String wanted = permission.trim().toUpperCase();
        return authentication.getAuthorities().stream().anyMatch(a -> wanted.equalsIgnoreCase(a.getAuthority()));
    }

    public static void requirePermission(String permission, String operation) {
        if (!hasPermission(permission)) throw new SecurityException(operation + " requires " + permission + " permission");
    }

    public static void requireManagerOrAdmin(String operation) {
        if (!(hasPermission("USERS.MANAGE_PERMISSIONS") || hasPermission("MASTERS.EDIT"))) {
            throw new SecurityException(operation + " requires an assigned management permission");
        }
    }
}

