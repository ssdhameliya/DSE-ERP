package org.example.server.persistence.entity;

import org.example.server.util.BusinessClock;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false) private String password;
    @Column(name = "full_name") private String fullName;
    /** Stable security identity from ROLE Master lookup_value (ADMIN, PURCHASE, SALES, ...). The hidden ROLxxx lookup_code is never stored here. */
    @Column(nullable = false) private String role;
    private String email;
    @Column(nullable = false) private Integer active = 1;
    private String department;
    private String branch;
    @Column(name = "access_level", nullable = false) private String accessLevel = "STANDARD";
    private Integer locked = 0;
    @Column(name = "failed_attempts") private Integer failedAttempts = 0;
    @Column(name = "mfa_failed_attempts") private Integer mfaFailedAttempts = 0;
    @Column(name = "lock_reason") private String lockReason = "NONE";
    @Column(name = "mfa_enabled") private Integer mfaEnabled = 0;
    @Column(name = "last_login") private java.time.LocalDateTime lastLogin;
    @Column(name = "last_login_utc") private String lastLoginUtc;
    @Column(name = "totp_secret_enc") private String totpSecretEnc;
    @Column(name = "approval_status", nullable = false) private String approvalStatus = "APPROVED";

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public void setUsername(String username) { this.username = username; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setRole(String role) { this.role = role; }
    public void setEmail(String email) { this.email = email; }
    public void setActive(boolean active) { this.active = active ? 1 : 0; }
    public void setDepartment(String department) { this.department = department; }
    public void setBranch(String branch) { this.branch = branch; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public void setLocked(boolean locked) { this.locked = locked ? 1 : 0; }
    public void setLockReason(String lockReason) { this.lockReason = normalizeLockReason(lockReason); }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled ? 1 : 0; }
    public void setTotpSecretEnc(String value) { this.totpSecretEnc = value; }
    public void setApprovalStatus(String value) { this.approvalStatus = value == null || value.isBlank() ? "APPROVED" : value.trim().toUpperCase(java.util.Locale.ROOT); }

    public String getFullName() { return fullName; }
    public String getRoleName() { return role; }
    public Integer getRoleId() { return null; } // legacy transport field; Role Master identity is the case-insensitive ROLE lookup_value from v8.5.1.
    public String getEmail() { return email; }
    public boolean isActive() { return Integer.valueOf(1).equals(active); }
    public String getDepartment() { return department; }
    public String getBranch() { return branch; }
    public String getAccessLevel() { return accessLevel; }
    public boolean isLocked() { return Integer.valueOf(1).equals(locked); }
    public int getFailedAttempts() { return failedAttempts == null ? 0 : failedAttempts; }
    public int getMfaFailedAttempts() { return mfaFailedAttempts == null ? 0 : mfaFailedAttempts; }
    public String getLockReason() { return normalizeLockReason(lockReason); }
    public boolean isMfaEnabled() { return Integer.valueOf(1).equals(mfaEnabled); }
    public String getTotpSecretEnc() { return totpSecretEnc; }
    public String getApprovalStatus() { return approvalStatus == null || approvalStatus.isBlank() ? "APPROVED" : approvalStatus.trim().toUpperCase(java.util.Locale.ROOT); }
    public int recordFailedPasswordAttempt() { this.failedAttempts = getFailedAttempts() + 1; return this.failedAttempts; }
    public int recordFailedMfaAttempt() { this.mfaFailedAttempts = getMfaFailedAttempts() + 1; return this.mfaFailedAttempts; }
    public void resetPasswordFailures() { this.failedAttempts = 0; }
    public void resetMfaFailures() { this.mfaFailedAttempts = 0; }
    public void clearAutomaticLock() {
        if ("FAILED_PASSWORD".equals(getLockReason()) || "FAILED_MFA".equals(getLockReason())) {
            this.locked = 0;
            this.lockReason = "NONE";
        }
        resetPasswordFailures();
        resetMfaFailures();
    }
    public void recordSuccessfulLogin() {
        this.lastLogin = BusinessClock.now();
        this.lastLoginUtc = BusinessClock.nowUtcText();
        resetPasswordFailures();
        resetMfaFailures();
    }
    private static String normalizeLockReason(String value) {
        String normalized = value == null ? "NONE" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? "NONE" : normalized;
    }
}
