package org.example.update;

import org.example.database.DatabaseManager;
import java.sql.*;

/** Versioned application migrations. Add one idempotent migration block for every future schema version. */
public final class DatabaseMigrationManager {
    private DatabaseMigrationManager() {}

    public static MigrationResult migrate() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureVersionTable(connection);
                int from = currentVersion(connection);
                int target = BuildInfo.databaseMigrationVersion();
                if (from > target) throw new SQLException("Database migration version " + from + " is newer than this application supports (" + target + ").");
                for (int next = from + 1; next <= target; next++) apply(connection, next);
                setVersion(connection, target);
                connection.commit();
                return new MigrationResult(from, target, target > from);
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sql) throw sql;
                throw new SQLException("Database migration failed", exception);
            } finally { connection.setAutoCommit(true); }
        }
    }

    private static void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS app_schema_version (id INTEGER PRIMARY KEY CHECK(id=1), version INTEGER NOT NULL, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT OR IGNORE INTO app_schema_version(id, version) VALUES(1, 1)");
        }
    }
    private static int currentVersion(Connection connection) throws SQLException {
        try (Statement statement=connection.createStatement(); ResultSet rs=statement.executeQuery("SELECT version FROM app_schema_version WHERE id=1")) { return rs.next()?rs.getInt(1):1; }
    }
    private static void setVersion(Connection connection,int version) throws SQLException {
        try (PreparedStatement ps=connection.prepareStatement("UPDATE app_schema_version SET version=?, updated_at=CURRENT_TIMESTAMP WHERE id=1")){ps.setInt(1,version);ps.executeUpdate();}
    }
    private static void apply(Connection connection,int version) throws SQLException {
        // Version 1 is the current baseline. Future releases add idempotent migration cases here.
        switch (version) {
            case 1 -> { }
            default -> throw new SQLException("No migration implementation exists for schema version " + version + ".");
        }
    }
    public record MigrationResult(int fromVersion,int toVersion,boolean changed) {}
}
