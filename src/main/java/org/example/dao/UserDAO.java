package org.example.dao;

import org.example.database.DatabaseManager;
import org.example.config.ConfigManager;
import org.example.model.AppUser;
import org.example.persistence.SpringPersistence;
import org.example.persistence.entity.UserEntity;
import org.example.persistence.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.*;

public class UserDAO {
    private static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();

    public AppUser authenticate(String identity, String password) {
        if (ConfigManager.isSqlite()) return authenticateWithJdbc(identity, password);
        try {
            UserEntity entity = repository().findActiveByIdentity(identity.trim()).orElse(null);
            if (entity == null || !passwordMatches(password, entity.getPassword())) return null;
            if (!isBcrypt(entity.getPassword())) {
                repository().updatePassword(entity.getId(), PASSWORDS.encode(password));
            }
            return map(entity);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not sign in", exception);
        }
    }

    public AppUser findActiveByIdentity(String identity) {
        if (ConfigManager.isSqlite()) return findActiveWithJdbc(identity);
        try {
            return repository().findActiveByIdentity(identity.trim()).map(this::map).orElse(null);
        } catch (RuntimeException exception) {
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
            statement.setString(2, PASSWORDS.encode(user.getPassword()));
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
            p.setString(1, PASSWORDS.encode(password));
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

    private UserRepository repository() {
        return SpringPersistence.bean(UserRepository.class);
    }

    private AppUser authenticateWithJdbc(String identity, String password) {
        AppUser user = findActiveWithJdbc(identity);
        if (user == null || !passwordMatches(password, user.getPassword())) return null;
        if (!isBcrypt(user.getPassword())) {
            String encoded = PASSWORDS.encode(password);
            updatePasswordWithJdbc(user.getId(), encoded);
            user.setPassword(encoded);
        }
        return user;
    }

    private AppUser findActiveWithJdbc(String identity) {
        String sql = "SELECT u.*,r.role_name resolved_role FROM users u JOIN roles r ON r.id=u.role_id AND r.active=1 "
                + "WHERE (lower(u.username)=lower(?) OR lower(u.email)=lower(?)) "
                + "AND u.active=1 AND COALESCE(u.locked,0)=0";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identity.trim());
            statement.setString(2, identity.trim());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load user account", exception);
        }
    }

    private void updatePasswordWithJdbc(int id, String encodedPassword) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users SET password=? WHERE id=?")) {
            statement.setString(1, encodedPassword);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not upgrade password security", exception);
        }
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        return storedPassword != null && (isBcrypt(storedPassword)
                ? PASSWORDS.matches(rawPassword, storedPassword)
                : storedPassword.equals(rawPassword));
    }

    private boolean isBcrypt(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$")
                || password.startsWith("$2y$"));
    }

    private AppUser map(UserEntity entity) {
        AppUser user = new AppUser();
        user.setId(entity.getId());
        user.setUsername(entity.getUsername());
        user.setPassword(entity.getPassword());
        user.setFullName(entity.getFullName());
        user.setRole(entity.getRoleName());
        if (entity.getRoleId() != null) user.setRoleId(entity.getRoleId());
        user.setEmail(entity.getEmail());
        user.setActive(entity.isActive());
        user.setDepartment(entity.getDepartment());
        user.setBranch(entity.getBranch());
        user.setAccessLevel(entity.getAccessLevel());
        user.setLocked(entity.isLocked());
        user.setMfaEnabled(entity.isMfaEnabled());
        return user;
    }
}
