package org.example.server.admin;

import org.example.server.master.RoleMasterService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.security.TokenService;
import org.example.server.util.BusinessClock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {
    private final JpaNativeRepository jdbc;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final RoleMasterService roleMaster;

    public AdminService(JpaNativeRepository jdbc, PasswordEncoder passwords, TokenService tokens,
                        RoleMasterService roleMaster) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokens = tokens;
        this.roleMaster = roleMaster;
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.UserDto> users() {
        return jdbc.query("SELECT u.id,u.username,u.full_name,u.email,COALESCE(NULLIF(TRIM(u.role),''),'SALES')," +
                        "u.department,u.access_level,u.branch,u.active,u.locked,u.mfa_enabled,COALESCE(NULLIF(u.last_login_utc,''),CAST(u.last_login AS text)) " +
                        "FROM users u ORDER BY u.full_name,u.username",
                (row, index) -> new AdminDtos.UserDto(row.getInt(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getString(5), row.getString(6), row.getString(7), row.getString(8), flag(row.getObject(9)),
                        flag(row.getObject(10)), flag(row.getObject(11)), BusinessClock.toUtcText(row.getObject(12))));
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserDto user(int id) {
        return users().stream().filter(value -> value.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    /** Role options are projected directly from Master Data ROLE lookups. */
    @Transactional(readOnly = true)
    public List<AdminDtos.RoleDto> roles() {
        return roleMaster.activeRoles().stream()
                .map(role -> new AdminDtos.RoleDto(role.id(), role.code(), role.displayName(), role.description(),
                        role.active(), role.userCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.PermissionDto> permissions(String role) {
        String code = role(role);
        boolean admin = "ADMIN".equals(code);
        return jdbc.query("SELECT p.id,p.module_name,p.action_name,p.description,COALESCE(rp.allowed,0) " +
                        "FROM permissions p LEFT JOIN role_permission rp ON rp.permission_id=p.id " +
                        "AND UPPER(TRIM(COALESCE(rp.role_code,'')))=? WHERE p.active=1 ORDER BY p.module_name,p.action_name",
                (row, index) -> new AdminDtos.PermissionDto(row.getLong(1), row.getString(2), row.getString(3),
                        row.getString(4), admin || flag(row.getObject(5))), code);
    }

    @Transactional
    public void savePermissions(AdminDtos.PermissionSaveRequest request) {
        String code = role(request == null ? null : request.role());
        if ("ADMIN".equals(code)) return;
        jdbc.update("DELETE FROM role_permission WHERE UPPER(TRIM(COALESCE(role_code,'')))=?", code);
        if (request.permissions() != null) for (var permission : request.permissions()) {
            jdbc.update("INSERT INTO role_permission(role_code,permission_id,allowed) VALUES(?,?,?)",
                    code, permission.id(), permission.allowed() ? 1 : 0);
        }
    }

    @Transactional
    public AdminDtos.UserDto saveUser(AdminDtos.UserSaveRequest request) {
        if (request == null || request.username() == null || request.username().isBlank())
            throw new IllegalArgumentException("Username is required");
        String assignedRole = role(request.role());
        boolean enforcedMfa = !"ADMIN".equals(assignedRole);
        String encoded = null;
        if (request.id() == null || (request.password() != null && !request.password().isBlank())) {
            validatePassword(request.password());
            encoded = passwords.encode(request.password());
        }
        if (request.id() == null) {
            jdbc.update("INSERT INTO users(username,password,full_name,role,role_id,email,active,locked,lock_reason,mfa_enabled,department,branch,access_level) " +
                            "VALUES(?,?,?,?,NULL,?,?,?,?,?,?,?,?)",
                    request.username().trim(), encoded, clean(request.fullName()), assignedRole,
                    clean(request.email()), request.active() ? 1 : 0, request.locked() ? 1 : 0,
                    request.locked() ? "ADMIN" : "NONE", enforcedMfa ? 1 : 0,
                    clean(request.department()), clean(request.branch()), clean(request.accessLevel()));
        } else {
            boolean previousMfa = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT mfa_enabled FROM users WHERE id=?", Boolean.class, request.id()));
            boolean previousLocked = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT locked FROM users WHERE id=?", Boolean.class, request.id()));
            if (request.id() == CurrentUser.require().id() && (request.locked() || !request.active()))
                throw new IllegalArgumentException("You cannot lock or deactivate your own account");
            jdbc.update("UPDATE users SET username=?,full_name=?,email=?,role=?,role_id=NULL," +
                            "active=?,locked=?,mfa_enabled=?,department=?,branch=?,access_level=? WHERE id=?",
                    request.username().trim(), clean(request.fullName()), clean(request.email()), assignedRole,
                    request.active() ? 1 : 0, request.locked() ? 1 : 0, enforcedMfa ? 1 : 0,
                    clean(request.department()), clean(request.branch()), clean(request.accessLevel()), request.id());
            if (previousLocked && !request.locked()) {
                jdbc.update("UPDATE users SET failed_attempts=0,mfa_failed_attempts=0,lock_reason='NONE' WHERE id=?", request.id());
                jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                        request.id(), "ACCOUNT_UNLOCKED", "Account unlocked and failed-attempt counters reset",
                        CurrentUser.require().username(), BusinessClock.nowUtcText());
            } else if (!previousLocked && request.locked()) {
                jdbc.update("UPDATE users SET lock_reason='ADMIN' WHERE id=?", request.id());
                tokens.revokeUser(request.id());
                jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                        request.id(), "ACCOUNT_LOCKED_BY_ADMIN", "Account locked by administrator",
                        CurrentUser.require().username(), BusinessClock.nowUtcText());
            }
            if (encoded != null) {
                jdbc.update("UPDATE users SET password=? WHERE id=?", encoded, request.id());
                tokens.revokeUser(request.id());
            }
            if (previousMfa != enforcedMfa) {
                tokens.revokeUser(request.id());
                jdbc.update("UPDATE users SET mfa_failed_attempts=0 WHERE id=?", request.id());
                jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) " +
                                "VALUES('USER',?,?,?,?,?)", request.id(),
                        enforcedMfa ? "MFA_ENFORCED" : "MFA_ADMIN_EXEMPT",
                        enforcedMfa ? "MFA enforced by Role Master policy" : "Admin role is exempt from login OTP by policy",
                        CurrentUser.require().username(), BusinessClock.nowUtcText());
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
        if (jdbc.update("UPDATE users SET password=?,failed_attempts=0,mfa_failed_attempts=0,locked=0,lock_reason='NONE' WHERE id=?", passwords.encode(password), id) != 1)
            throw new IllegalArgumentException("User not found");
        tokens.revokeUser(id);
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                id, "ACCOUNT_UNLOCKED", "Password reset by administrator and sign-in lock cleared", CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    @Transactional
    public void setLocked(int id, boolean locked) {
        if (id == CurrentUser.require().id() && locked) throw new IllegalArgumentException("You cannot lock your own account");
        int updated = locked
                ? jdbc.update("UPDATE users SET locked=1,lock_reason='ADMIN' WHERE id=?", id)
                : jdbc.update("UPDATE users SET locked=0,lock_reason='NONE',failed_attempts=0,mfa_failed_attempts=0 WHERE id=?", id);
        if (updated != 1) throw new IllegalArgumentException("User not found");
        if (locked) tokens.revokeUser(id);
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                id, locked ? "ACCOUNT_LOCKED_BY_ADMIN" : "ACCOUNT_UNLOCKED",
                locked ? "Account locked by administrator" : "Account unlocked and failed-attempt counters reset",
                CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    /** Role definitions are edited only through Master Data ROLE lookups. */
    public AdminDtos.RoleDto saveRole(AdminDtos.RoleSaveRequest request) {
        throw new IllegalArgumentException("Manage role definitions in Master Data > Role");
    }

    public void deleteRole(int id) {
        throw new IllegalArgumentException("Manage role definitions in Master Data > Role");
    }

    @Transactional
    public void audit(AdminDtos.AuditRequest request) {
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                request.userId(), request.action(), request.detail(), CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    private String role(String value) {
        return roleMaster.requireActive(value).code();
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
