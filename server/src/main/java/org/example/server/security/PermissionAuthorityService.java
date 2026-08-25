package org.example.server.security;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Resolves the saved Permission Matrix into Spring Security authorities. */
@Service
public class PermissionAuthorityService {
    public record EffectivePermissionView(String module, String action, String description) {}

    private final JpaNativeRepository jdbc;

    public PermissionAuthorityService(JpaNativeRepository jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<String> permissionKeys(String role) {
        String code = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (code.isBlank()) return List.of();
        if ("ADMIN".equals(code)) {
            return jdbc.query("SELECT permission_key FROM permissions WHERE active=1 ORDER BY permission_key",
                    (row, index) -> row.getString(1));
        }
        return jdbc.query("SELECT p.permission_key FROM role_permission rp JOIN permissions p ON p.id=rp.permission_id " +
                        "WHERE UPPER(TRIM(COALESCE(rp.role_code,'')))=? AND p.active=1 AND COALESCE(rp.allowed,0)=1 ORDER BY p.permission_key",
                (row, index) -> row.getString(1), code);
    }

    @Transactional(readOnly = true)
    public List<EffectivePermissionView> effectivePermissions(String role) {
        String code = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (code.isBlank()) return List.of();
        if ("ADMIN".equals(code)) {
            return jdbc.query("SELECT module_name,action_name,COALESCE(description,'') FROM permissions WHERE active=1 ORDER BY module_name,action_name",
                    (row, index) -> new EffectivePermissionView(row.getString(1), row.getString(2), row.getString(3)));
        }
        return jdbc.query("SELECT p.module_name,p.action_name,COALESCE(p.description,'') FROM role_permission rp JOIN permissions p ON p.id=rp.permission_id " +
                        "WHERE UPPER(TRIM(COALESCE(rp.role_code,'')))=? AND p.active=1 AND COALESCE(rp.allowed,0)=1 ORDER BY p.module_name,p.action_name",
                (row, index) -> new EffectivePermissionView(row.getString(1), row.getString(2), row.getString(3)), code);
    }

}
