package org.example.service;

import org.example.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Resolves database role permissions for the signed-in user. */
public final class PermissionService {
    private PermissionService() {}

    public static boolean allowed(String permissionKey) {
        if (SessionService.current() == null) return false;
        String role = SessionService.current().getRole();
        if (role != null && (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("ADMINISTRATOR"))) return true;
        String sql = "SELECT COALESCE(rp.allowed,0) FROM role_permission rp " +
            "JOIN permissions p ON p.id=rp.permission_id WHERE rp.role_id=? AND p.permission_key=? AND p.active=1";
        try (Connection c=DatabaseManager.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setInt(1, SessionService.current().getRoleId()); p.setString(2, permissionKey);
            try (ResultSet r=p.executeQuery()) { return r.next() && r.getInt(1)==1; }
        } catch (Exception ignored) { return false; }
    }
}
