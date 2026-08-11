package org.example.service;

import org.example.model.AppUser;

public final class SessionService {
    private static AppUser current;

    private SessionService() {
    }

    public static void signIn(AppUser user) {
        current = user;
    }

    public static AppUser current() {
        return current;
    }

    public static boolean isAdmin() {
        return current != null && "ADMIN".equals(current.getRole());
    }

    public static void clear() {
        current = null;
    }
}
