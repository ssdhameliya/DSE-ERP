package org.example.server.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false) private String password;
    @Column(name = "full_name") private String fullName;
    @Column(nullable = false) private String role;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "role_id") private RoleEntity assignedRole;
    private String email;
    @Column(nullable = false) private Integer active = 1;
    private String department;
    private String branch;
    @Column(name = "access_level", nullable = false) private String accessLevel = "STANDARD";
    private Integer locked = 0;
    @Column(name = "failed_attempts") private Integer failedAttempts = 0;
    @Column(name = "mfa_enabled") private Integer mfaEnabled = 0;
    @Column(name = "last_login") private java.time.LocalDateTime lastLogin;

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public void setUsername(String username) { this.username = username; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setRole(String role) { this.role = role; }
    public void setAssignedRole(RoleEntity assignedRole) { this.assignedRole = assignedRole; }
    public void setEmail(String email) { this.email = email; }
    public void setActive(boolean active) { this.active = active ? 1 : 0; }
    public void setDepartment(String department) { this.department = department; }
    public void setBranch(String branch) { this.branch = branch; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public void setLocked(boolean locked) { this.locked = locked ? 1 : 0; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled ? 1 : 0; }

    public String getFullName() { return fullName; }
    public String getRoleName() { return assignedRole == null ? role : assignedRole.getName(); }
    public Integer getRoleId() { return assignedRole == null ? null : assignedRole.getId(); }
    public String getEmail() { return email; }
    public boolean isActive() { return Integer.valueOf(1).equals(active); }
    public String getDepartment() { return department; }
    public String getBranch() { return branch; }
    public String getAccessLevel() { return accessLevel; }
    public boolean isLocked() { return Integer.valueOf(1).equals(locked); }
    public boolean isMfaEnabled() { return Integer.valueOf(1).equals(mfaEnabled); }
    public void recordSuccessfulLogin() { this.lastLogin = java.time.LocalDateTime.now(); this.failedAttempts = 0; }
}
