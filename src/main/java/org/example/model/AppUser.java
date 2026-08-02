package org.example.model;

public class AppUser {
    private int id;
    private String username;
    private String password;
    private String fullName;
    private String role;
    private int roleId;
    private String email;
    private boolean active;
    private String department;
    private String branch;
    private String accessLevel;
    private boolean locked;
    private boolean mfaEnabled;

    public int getId() {
        return id;
    }

    public void setId(int value) {
        id = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String value) {
        password = value;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String value) {
        fullName = value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String value) {
        role = value;
    }
    public int getRoleId() { return roleId; }
    public void setRoleId(int value) { roleId = value; }

    public String getEmail() {
        return email;
    }

    public void setEmail(String value) {
        email = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        active = value;
    }

    public String getDepartment() { return department; }
    public void setDepartment(String value) { department = value; }
    public String getBranch() { return branch; }
    public void setBranch(String value) { branch = value; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String value) { accessLevel = value; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean value) { locked = value; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean value) { mfaEnabled = value; }
}
