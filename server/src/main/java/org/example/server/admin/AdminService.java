package org.example.server.admin;

import org.example.server.master.RoleMasterService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.security.TokenService;
import org.example.server.util.BusinessClock;
import org.example.server.web.ConcurrentEditException;
import org.example.shared.SecretValueCodec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private static final long USER_AUTHORITY_LOCK = 51018047L;
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
                        "u.department,u.access_level,u.branch,u.active,u.locked,u.mfa_enabled,COALESCE(NULLIF(u.last_login_utc,''),CAST(u.last_login AS text)),COALESCE(u.row_version,0) " +
                        "FROM users u ORDER BY u.full_name,u.username",
                (row, index) -> new AdminDtos.UserDto(row.getInt(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getString(5), row.getString(6), row.getString(7), row.getString(8), flag(row.getObject(9)),
                        flag(row.getObject(10)), flag(row.getObject(11)), BusinessClock.toUtcText(row.getObject(12)), row.getLong(13)));
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
    public AdminDtos.PermissionSetDto permissionSet(String role) {
        String code = role(role);
        jdbc.update("INSERT INTO role_permission_revision(role_code,row_version) VALUES(?,0) ON CONFLICT(role_code) DO NOTHING", code);
        Long revision = jdbc.queryForObject("SELECT row_version FROM role_permission_revision WHERE role_code=?", Long.class, code);
        return new AdminDtos.PermissionSetDto(revision == null ? 0L : revision, permissions(code));
    }

    @Transactional
    public void savePermissions(AdminDtos.PermissionSaveRequest request) {
        String code = role(request == null ? null : request.role());
        if ("ADMIN".equals(code)) return;
        jdbc.update("INSERT INTO role_permission_revision(role_code,row_version) VALUES(?,0) ON CONFLICT(role_code) DO NOTHING", code);
        Long currentRevision = jdbc.queryForObject("SELECT row_version FROM role_permission_revision WHERE role_code=? FOR UPDATE", Long.class, code);
        long actualRevision = currentRevision == null ? 0L : currentRevision;
        if (request.rowVersion() != actualRevision)
            throw new ConcurrentEditException("Permission Matrix for " + code);
        Map<Long, Boolean> requested = new LinkedHashMap<>();
        if (request.permissions() != null) {
            for (var permission : request.permissions()) {
                if (permission == null || permission.id() <= 0) throw new IllegalArgumentException("A valid permission id is required");
                requested.put(permission.id(), permission.allowed());
            }
        }
        if (!requested.isEmpty()) {
            Long valid = jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE active=1 AND id IN (" +
                    requested.keySet().stream().map(x -> "?").collect(java.util.stream.Collectors.joining(",")) + ")",
                    Long.class, requested.keySet().toArray());
            if (valid == null || valid != requested.size()) throw new IllegalArgumentException("One or more permissions are invalid or inactive. Refresh the Permission Matrix and try again.");
        }
        jdbc.update("DELETE FROM role_permission WHERE UPPER(TRIM(COALESCE(role_code,'')))=?", code);
        for (var permission : requested.entrySet()) {
            jdbc.update("INSERT INTO role_permission(role_code,permission_id,allowed) VALUES(?,?,?)",
                    code, permission.getKey(), permission.getValue() ? 1 : 0);
        }
        jdbc.update("UPDATE role_permission_revision SET row_version=row_version+1,updated_at=CURRENT_TIMESTAMP::text WHERE role_code=?", code);
    }

    @Transactional
    public AdminDtos.UserDto saveUser(AdminDtos.UserSaveRequest request) {
        if (request == null || request.username() == null || request.username().isBlank())
            throw new IllegalArgumentException("Username is required");
        String assignedRole = role(request.role());
        validateUniqueIdentity(request);
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
            jdbc.query("SELECT pg_advisory_xact_lock(?)", (row,index) -> row.getObject(1), USER_AUTHORITY_LOCK);
            Map<String,Object> lockedUser = jdbc.queryForMap("SELECT COALESCE(row_version,0) row_version FROM users WHERE id=? FOR UPDATE", request.id());
            long actualVersion = ((Number)lockedUser.get("row_version")).longValue();
            if (request.rowVersion() != actualVersion) throw new ConcurrentEditException("User account");
            boolean previousMfa = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT mfa_enabled FROM users WHERE id=?", Boolean.class, request.id()));
            boolean previousLocked = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT locked FROM users WHERE id=?", Boolean.class, request.id()));
            String previousRole = jdbc.queryForObject("SELECT COALESCE(role,'') FROM users WHERE id=?", String.class, request.id());
            if (request.id() == CurrentUser.require().id() && previousRole != null && !assignedRole.equalsIgnoreCase(previousRole.trim()))
                throw new IllegalArgumentException("You cannot change your own role. Another administrator must perform that change.");
            ensureActiveAdministratorRemains(request.id(), assignedRole, request.active(), request.locked());
            if (request.id() == CurrentUser.require().id() && (request.locked() || !request.active()))
                throw new IllegalArgumentException("You cannot lock or deactivate your own account");
            jdbc.update("UPDATE users SET username=?,full_name=?,email=?,role=?,role_id=NULL," +
                            "active=?,locked=?,mfa_enabled=?,department=?,branch=?,access_level=?,row_version=row_version+1 WHERE id=? AND row_version=?",
                    request.username().trim(), clean(request.fullName()), clean(request.email()), assignedRole,
                    request.active() ? 1 : 0, request.locked() ? 1 : 0, enforcedMfa ? 1 : 0,
                    clean(request.department()), clean(request.branch()), clean(request.accessLevel()), request.id(), request.rowVersion());
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
            if (previousRole == null || !assignedRole.equalsIgnoreCase(previousRole.trim())) {
                tokens.revokeUser(request.id());
                jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                        request.id(), "ROLE_CHANGED", "Role changed from " + (previousRole == null ? "" : previousRole.trim()) + " to " + assignedRole,
                        CurrentUser.require().username(), BusinessClock.nowUtcText());
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
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (row,index) -> row.getObject(1), USER_AUTHORITY_LOCK);
        jdbc.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE", id);
        ensureActiveAdministratorRemains(id, null, false, true);
        tokens.revokeUser(id);
        if (jdbc.update("DELETE FROM users WHERE id=?", id) != 1) throw new IllegalArgumentException("User not found");
    }

    @Transactional
    public void resetPassword(int id, String password) {
        validatePassword(password);
        if (jdbc.update("UPDATE users SET password=?,failed_attempts=0,mfa_failed_attempts=0,locked=0,lock_reason='NONE',row_version=row_version+1 WHERE id=?", passwords.encode(password), id) != 1)
            throw new IllegalArgumentException("User not found");
        tokens.revokeUser(id);
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                id, "ACCOUNT_UNLOCKED", "Password reset by administrator and sign-in lock cleared", CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    @Transactional
    public void setLocked(int id, boolean locked) {
        if (id == CurrentUser.require().id() && locked) throw new IllegalArgumentException("You cannot lock your own account");
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (row,index) -> row.getObject(1), USER_AUTHORITY_LOCK);
        jdbc.queryForMap("SELECT id FROM users WHERE id=? FOR UPDATE", id);
        if (locked) ensureActiveAdministratorRemains(id, null, true, true);
        int updated = locked
                ? jdbc.update("UPDATE users SET locked=1,lock_reason='ADMIN',row_version=row_version+1 WHERE id=?", id)
                : jdbc.update("UPDATE users SET locked=0,lock_reason='NONE',failed_attempts=0,mfa_failed_attempts=0,row_version=row_version+1 WHERE id=?", id);
        if (updated != 1) throw new IllegalArgumentException("User not found");
        if (locked) tokens.revokeUser(id);
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",
                id, locked ? "ACCOUNT_LOCKED_BY_ADMIN" : "ACCOUNT_UNLOCKED",
                locked ? "Account locked by administrator" : "Account unlocked and failed-attempt counters reset",
                CurrentUser.require().username(), BusinessClock.nowUtcText());
    }

    @Transactional(readOnly = true)
    public AdminDtos.RegistrationRoleDto registrationRole() {
        RoleMasterService.RoleDefinition role = roleMaster.selfRegistrationRole();
        return new AdminDtos.RegistrationRoleDto(role.code(), role.displayName());
    }

    @Transactional
    public AdminDtos.RegistrationRoleDto setRegistrationRole(AdminDtos.RegistrationRoleSaveRequest request) {
        if (request == null || request.role() == null || request.role().isBlank())
            throw new IllegalArgumentException("Select an active non-Admin Role Master entry");
        RoleMasterService.RoleDefinition role = roleMaster.setSelfRegistrationRole(request.role());
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('SECURITY',NULL,?,?,?,?)",
                "REGISTRATION_ROLE_CHANGED", "Public registration role set to " + role.code(), CurrentUser.require().username(), BusinessClock.nowUtcText());
        return new AdminDtos.RegistrationRoleDto(role.code(), role.displayName());
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

    private void ensureActiveAdministratorRemains(int targetId, String nextRole, boolean nextActive, boolean nextLocked) {
        Map<String,Object> current = jdbc.queryForMap("SELECT COALESCE(role,'') role,COALESCE(active,1) active,COALESCE(locked,0) locked FROM users WHERE id=?", targetId);
        String currentRole = String.valueOf(current.get("role")).trim();
        boolean currentActive = flag(current.get("active"));
        boolean currentLocked = flag(current.get("locked"));
        if (!"ADMIN".equalsIgnoreCase(currentRole) || !currentActive || currentLocked) return;
        String effectiveRole = nextRole == null ? currentRole : nextRole;
        if ("ADMIN".equalsIgnoreCase(effectiveRole) && nextActive && !nextLocked) return;
        Long others = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id<>? AND UPPER(TRIM(COALESCE(role,'')))='ADMIN' AND COALESCE(active,1)<>0 AND COALESCE(locked,0)=0", Long.class, targetId);
        if (others == null || others == 0) throw new IllegalStateException("At least one other active, unlocked administrator is required before removing this administrator's authority.");
    }

    private void validateUniqueIdentity(AdminDtos.UserSaveRequest request) {
        Integer id = request.id();
        String username = request.username() == null ? "" : request.username().trim();
        String email = clean(request.email());
        Long usernameMatches = id == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(TRIM(username))=LOWER(TRIM(?))", Long.class, username)
                : jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(TRIM(username))=LOWER(TRIM(?)) AND id<>?", Long.class, username, id);
        if (usernameMatches != null && usernameMatches > 0) throw new IllegalArgumentException("This username is already assigned to another user");
        if (email != null && !email.isBlank()) {
            Long emailMatches = id == null
                    ? jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(TRIM(COALESCE(email,'')))=LOWER(TRIM(?))", Long.class, email)
                    : jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(TRIM(COALESCE(email,'')))=LOWER(TRIM(?)) AND id<>?", Long.class, email, id);
            if (emailMatches != null && emailMatches > 0) throw new IllegalArgumentException("This email address is already assigned to another user");
        }
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
    @Transactional(readOnly = true)
    public List<AdminDtos.RegistrationRequestDto> registrations(String status) {
        String wanted=status==null||status.isBlank()?"PENDING_ADMIN_APPROVAL":status.trim().toUpperCase();
        return jdbc.query("SELECT r.id,r.username,r.full_name,r.email,r.requested_role,r.email_verified,r.mfa_verified,r.status,CAST(r.requested_at AS text),COALESCE(u.username,''),CAST(r.reviewed_at AS text),COALESCE(r.rejection_reason,''),r.row_version FROM registration_request r LEFT JOIN users u ON u.id=r.reviewed_by WHERE r.status=? ORDER BY r.requested_at ASC",
                (row,i)->new AdminDtos.RegistrationRequestDto(row.getLong(1),row.getString(2),row.getString(3),row.getString(4),row.getString(5),flag(row.getObject(6)),flag(row.getObject(7)),row.getString(8),row.getString(9),row.getString(10),row.getString(11),row.getString(12),row.getLong(13)),wanted);
    }

    @Transactional
    public AdminDtos.Ok approveRegistration(long id, AdminDtos.RegistrationDecisionRequest request) {
        var row=jdbc.queryForMap("SELECT id,username,password_hash,full_name,email,requested_role,totp_secret_enc,email_verified,mfa_verified,status,row_version FROM registration_request WHERE id=? FOR UPDATE",id);
        long version=((Number)row.get("row_version")).longValue(); if(request==null||request.rowVersion()!=version)throw new ConcurrentEditException("Registration request");
        if(!"PENDING_ADMIN_APPROVAL".equals(String.valueOf(row.get("status"))))throw new IllegalArgumentException("Registration request has already been processed");
        if(!flag(row.get("email_verified"))||!flag(row.get("mfa_verified")))throw new IllegalArgumentException("Registration identity verification is incomplete");
        String assigned=request.role()==null||request.role().isBlank()?String.valueOf(row.get("requested_role")):request.role().trim().toUpperCase();
        if("ADMIN".equals(assigned))throw new SecurityException("Administrator accounts cannot be created from self-registration. Use User Management.");
        var role=roleMaster.requireActive(assigned);
        String username=String.valueOf(row.get("username")),email=String.valueOf(row.get("email"));
        Long duplicates=jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(username)=LOWER(?) OR LOWER(COALESCE(email,''))=LOWER(?)",Long.class,username,email);if(duplicates!=null&&duplicates>0)throw new IllegalArgumentException("Username or email is already registered");
        Integer userId=jdbc.queryForObject("INSERT INTO users(username,password,full_name,role,email,active,locked,mfa_enabled,access_level,totp_secret_enc,approval_status,row_version) VALUES(?,?,?,?,?,1,0,1,'STANDARD',?,'APPROVED',0) RETURNING id",Integer.class,username,String.valueOf(row.get("password_hash")),String.valueOf(row.get("full_name")),role.code(),email,String.valueOf(row.get("totp_secret_enc")));
        var actor=CurrentUser.require();jdbc.update("UPDATE registration_request SET status='APPROVED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,rejection_reason=NULL,row_version=row_version+1 WHERE id=?",actor.id(),id);
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('USER',?,?,?,?,?)",userId,"REGISTRATION_APPROVED","Self-registration approved with role "+role.code(),actor.username(),BusinessClock.nowUtcText());
        return new AdminDtos.Ok(true,"Registration approved. The user can now sign in with password + authenticator.");
    }

    @Transactional
    public AdminDtos.Ok rejectRegistration(long id, AdminDtos.RegistrationDecisionRequest request) {
        var row=jdbc.queryForMap("SELECT username,status,row_version FROM registration_request WHERE id=? FOR UPDATE",id);long version=((Number)row.get("row_version")).longValue();if(request==null||request.rowVersion()!=version)throw new ConcurrentEditException("Registration request");
        if(!"PENDING_ADMIN_APPROVAL".equals(String.valueOf(row.get("status"))))throw new IllegalArgumentException("Registration request has already been processed");
        var actor=CurrentUser.require();String reason=request.reason()==null?"":request.reason().trim();jdbc.update("UPDATE registration_request SET status='REJECTED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,rejection_reason=?,row_version=row_version+1 WHERE id=?",actor.id(),reason,id);
        jdbc.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('REGISTRATION',?,?,?,?,?)",id,"REGISTRATION_REJECTED",reason.isBlank()?"Registration rejected":reason,actor.username(),BusinessClock.nowUtcText());return new AdminDtos.Ok(true,"Registration rejected");
    }

}
