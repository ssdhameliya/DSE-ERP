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
    @Column(nullable = false) private Integer active;
    private String department;
    private String branch;
    @Column(name = "access_level") private String accessLevel;
    private Integer locked;
    @Column(name = "failed_attempts") private Integer failedAttempts;
    @Column(name = "mfa_enabled") private Integer mfaEnabled;
    @Column(name = "last_login") private java.time.LocalDateTime lastLogin;

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
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
