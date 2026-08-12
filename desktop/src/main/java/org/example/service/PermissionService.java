package org.example.service;

import org.example.api.admin.AdminApiClient;

/** Resolves server-owned role permissions for the signed-in user. */
public final class PermissionService {
    private PermissionService() {}
    public static boolean allowed(String permissionKey) {
        if (SessionService.current() == null) return false;
        String role = SessionService.current().getRole();
        if (role != null && (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("ADMINISTRATOR"))) return true;
        String wanted = normalize(permissionKey);
        try {
            return new AdminApiClient().permissions(role).stream().anyMatch(p -> p.allowed() &&
                (normalize(p.module()+"."+p.action()).equals(wanted) || normalize(p.action()).equals(wanted)));
        } catch (Exception ignored) { return false; }
    }
    private static String normalize(String v){return v==null?"":v.replaceAll("[^A-Za-z0-9]","").toLowerCase(java.util.Locale.ROOT);}
}
