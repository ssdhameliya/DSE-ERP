package org.example.server.admin;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.example.server.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminService {
    private static final Set<String> ROLES = Set.of("ADMIN", "MANAGER", "SALES");

    private final JpaNativeRepository jdbc;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AdminService(JpaNativeRepository jdbc, PasswordEncoder passwords, TokenService tokens) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.UserDto> users() {
        return jdbc.query("SELECT u.id,u.username,u.full_name,u.email,COALESCE(r.role_name,u.role,'SALES')," +
                        "u.department,u.access_level,u.branch,u.active,u.locked,u.mfa_enabled,COALESCE(NULLIF(u.last_login_utc,''),CAST(u.last_login AS text)) " +
                        "FROM users u LEFT JOIN roles r ON r.id=u.role_id ORDER BY u.full_name,u.username",
                (row, index) -> new AdminDtos.UserDto(row.getInt(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getString(5), row.getString(6), row.getString(7), row.getString(8), flag(row.getObject(9)),
                        flag(row.getObject(10)), flag(row.getObject(11)), BusinessClock.toUtcText(row.getObject(12))));
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserDto user(int id) {
        return users().stream().filter(value -> value.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.RoleDto> roles() {
        return jdbc.query("SELECT r.id,r.role_name,r.description,r.active,COUNT(u.id) FROM roles r " +
                        "LEFT JOIN users u ON u.role_id=r.id OR (u.role_id IS NULL AND u.role=r.role_name) " +
                        "WHERE r.role_name IN ('ADMIN','MANAGER','SALES') AND r.active=1 GROUP BY r.id,r.role_name,r.description,r.active " +
                        "ORDER BY CASE r.role_name WHEN 'ADMIN' THEN 1 WHEN 'MANAGER' THEN 2 ELSE 3 END",
                (row, index) -> new AdminDtos.RoleDto(row.getInt(1), row.getString(2), row.getString(3),
                        flag(row.getObject(4)), row.getLong(5)));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.PermissionDto> permissions(String role) {
        String name = role(role);
        boolean admin = "ADMIN".equals(name);
        return jdbc.query("SELECT p.id,p.module_name,p.action_name,p.description,COALESCE(rp.allowed,0) " +
                        "FROM permissions p JOIN roles r ON r.role_name=? LEFT JOIN role_permission rp " +
                        "ON rp.permission_id=p.id AND rp.role_id=r.id WHERE p.active=1 ORDER BY p.module_name,p.action_name",
                (row, index) -> new AdminDtos.PermissionDto(row.getLong(1), row.getString(2), row.getString(3),
                        row.getString(4), admin || flag(row.getObject(5))), name);
    }

    @Transactional
    public void savePermissions(AdminDtos.PermissionSaveRequest request) {
        String name = role(request == null ? null : request.role());
        if ("ADMIN".equals(name)) return;
        Integer roleId = jdbc.queryForObject("SELECT id FROM roles WHERE role_name=? AND active=1", Integer.class, name);
        if (roleId == null) throw new IllegalArgumentException("Role not found");
        jdbc.update("DELETE FROM role_permission WHERE role_id=?", roleId);
        if (request.permissions() != null) for (var permission : request.permissions()) {
            jdbc.update("INSERT INTO role_permission(role_id,permission_id,allowed) VALUES(?,?,?)",
                    roleId, permission.id(), permission.allowed() ? 1 : 0);
        }
    }

    @Transactional
    public AdminDtos.UserDto saveUser(AdminDtos.UserSaveRequest request) {
        if (request == null || request.username() == null || request.username().isBlank())
            throw new IllegalArgumentException("Username is required");
        String assignedRole = role(request.role());
        String encoded = null;
        if (request.id() == null || (request.password() != null && !request.password().isBlank())) {
            validatePassword(request.password());
            encoded = passwords.encode(request.password());
        }
        if (request.id() == null) {
            jdbc.update("INSERT INTO users(username,password,full_name,role,role_id,email,active,locked,mfa_enabled,department,branch,access_level) " +
                            "VALUES(?,?,?,?,(SELECT id FROM roles WHERE role_name=? AND active=1),?,?,?,?,?,?,?)",
                    request.username().trim(), encoded, clean(request.fullName()), assignedRole, assignedRole,
                    clean(request.email()), request.active() ? 1 : 0, request.locked() ? 1 : 0,
                    request.mfaEnabled() ? 1 : 0, clean(request.department()), clean(request.branch()), clean(request.accessLevel()));
        } else {
            if (request.id() == CurrentUser.require().id() && (request.locked() || !request.active()))
                throw new IllegalArgumentException("You cannot lock or deactivate your own account");
            jdbc.update("UPDATE users SET username=?,full_name=?,email=?,role=?,role_id=(SELECT id FROM roles WHERE role_name=? AND active=1)," +
                            "active=?,locked=?,mfa_enabled=?,department=?,branch=?,access_level=? WHERE id=?",
                    request.username().trim(), clean(request.fullName()), clean(request.email()), assignedRole, assignedRole,
                    request.active() ? 1 : 0, request.locked() ? 1 : 0, request.mfaEnabled() ? 1 : 0,
                    clean(request.department()), clean(request.branch()), clean(request.accessLevel()), request.id());
            if (encoded != null) {
                jdbc.update("UPDATE users SET password=? WHERE id=?", encoded, request.id());
                tokens.revokeUser(request.id());
            }
        }
        return request.id() == null
                ? users().stream().filter(value -> value.username().equalsIgnoreCase(request.username().trim())).findFirst().orElseThrow()
                : user(request.id());
    }

    @Transactional
    public void deleteUser(int id) {
        if (id == CurrentUser.require().id()) throw new IllegalArgumentException("You cannot delete your own account");
        tokens.revokeUser(id);
        if (jdbc.update("DELETE FROM users WHERE id=?", id) != 1) throw new IllegalArgumentException("User not found");
    }

    @Transactional
    public void resetPassword(int id, String password) {
        validatePassword(password);
        if (jdbc.update("UPDATE users SET password=?,failed_attempts=0,locked=0 WHERE id=?", passwords.encode(password), id) != 1)
            throw new IllegalArgumentException("User not found");
        tokens.revokeUser(id);
    }

    @Transactional
    public void setLocked(int id, boolean locked) {
        if (id == CurrentUser.require().id() && locked) throw new IllegalArgumentException("You cannot lock your own account");
        if (jdbc.update("UPDATE users SET locked=? WHERE id=?", locked ? 1 : 0, id) != 1)
            throw new IllegalArgumentException("User not found");
        if (locked) tokens.revokeUser(id);
    }

    @Transactional
    public AdminDtos.RoleDto saveRole(AdminDtos.RoleSaveRequest request) {
        String name = role(request == null ? null : request.name());
        if (request.id() == null) throw new IllegalArgumentException("Only Admin, Manager and Sale roles are supported");
        String existing = jdbc.queryForObject("SELECT role_name FROM roles WHERE id=?", String.class, request.id());
        if (!name.equals(existing)) throw new IllegalArgumentException("Built-in roles cannot be renamed");
        if (!request.active()) throw new IllegalArgumentException("Built-in roles cannot be deactivated");
        jdbc.update("UPDATE roles SET description=?,active=1 WHERE id=?", clean(request.description()), request.id());
        return roles().stream().filter(value -> value.id() == request.id()).findFirst().orElseThrow();
    }

    public void deleteRole(int id) {
        throw new IllegalArgumentException("Admin, Manager and Sale roles cannot be deleted");
    }

    @Transactional
    public void audit(AdminDtos.AuditRequest request) {
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                request.userId(), request.action(), request.detail(), CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    private static String role(String value) {
        String name = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(name)) throw new IllegalArgumentException("Role must be Admin, Manager or Sale");
        return name;
    }

    private static void validatePassword(String value) {
        if (value == null || value.length() < 8 || !value.matches(".*[A-Za-z].*") || !value.matches(".*[0-9].*"))
            throw new IllegalArgumentException("Password must contain at least 8 characters, a letter and a number");
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean flag(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value);
        return "1".equals(text) || "t".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text);
    }
}

