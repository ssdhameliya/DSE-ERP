package org.example.dao;

import org.example.database.DatabaseManager;
import org.example.model.AppUser;

import java.sql.*;

public class UserDAO {
    public AppUser authenticate(String identity, String password) {
        String sql = "SELECT u.*,r.role_name resolved_role FROM users u JOIN roles r ON r.id=u.role_id AND r.active=1 " +
            "WHERE (lower(u.username)=lower(?) OR lower(u.email)=lower(?)) AND u.password=? " +
            "AND u.active=1 AND COALESCE(u.locked,0)=0";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identity.trim());
            statement.setString(2, identity.trim());
            statement.setString(3, password);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not sign in", exception);
        }
    }

    public AppUser findActiveByIdentity(String identity) {
        String sql = "SELECT u.*,r.role_name resolved_role FROM users u JOIN roles r ON r.id=u.role_id AND r.active=1 " +
            "WHERE (lower(u.username)=lower(?) OR lower(u.email)=lower(?)) " +
            "AND u.active=1 AND COALESCE(u.locked,0)=0";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identity.trim());
            statement.setString(2, identity.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load user account", exception);
        }
    }

    public void recordSuccessfulLogin(int id) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users SET last_login=CURRENT_TIMESTAMP,failed_attempts=0 WHERE id=?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update sign-in history", exception);
        }
    }

    public void register(AppUser user) {
        String sql = "INSERT INTO users(username,password,full_name,role,role_id,email,active) VALUES(?,?,?,'SALES',(SELECT id FROM roles WHERE role_name='SALES'),?,1)";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPassword());
            statement.setString(3, user.getFullName());
            statement.setString(4, user.getEmail());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalArgumentException("Username or email is already registered.", exception);
        }
    }

    public void changePassword(int id, String password) {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement p = c.prepareStatement("UPDATE users SET password=? WHERE id=?")) {
            p.setString(1, password);
            p.setInt(2, id);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not change password", e);
        }
    }

    private AppUser map(ResultSet r) throws SQLException {
        AppUser u = new AppUser();
        u.setId(r.getInt("id"));
        u.setUsername(r.getString("username"));
        u.setPassword(r.getString("password"));
        u.setFullName(r.getString("full_name"));
        u.setRole(r.getString("resolved_role"));
        u.setRoleId(r.getInt("role_id"));
        u.setEmail(r.getString("email"));
        u.setActive(r.getBoolean("active"));
        u.setDepartment(r.getString("department"));
        u.setBranch(r.getString("branch"));
        u.setAccessLevel(r.getString("access_level"));
        u.setLocked(r.getBoolean("locked"));
        u.setMfaEnabled(r.getBoolean("mfa_enabled"));
        return u;
    }
}
