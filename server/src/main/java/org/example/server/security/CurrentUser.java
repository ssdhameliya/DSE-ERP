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

    public static void requireManagerOrAdmin(String operation) {
        if (isSales()) {
            throw new SecurityException(operation + " requires Manager or Admin access");
        }
    }
}

