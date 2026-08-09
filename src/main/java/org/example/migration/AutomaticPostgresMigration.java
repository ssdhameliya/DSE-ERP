package org.example.migration;

import org.example.backup.BackupManager;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.example.update.BuildInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Safely upgrades an unconfigured legacy SQLite workspace to an isolated PostgreSQL schema. */
public final class AutomaticPostgresMigration {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private AutomaticPostgresMigration() {}

    public static Result attempt() {
        Path source = ConfigManager.getLegacyDatabasePath();
        if (!Files.isRegularFile(source)) return Result.notRequired();
        if (!Boolean.parseBoolean(ConfigManager.get("migration.autoPostgres", "true"))) {
            return Result.notRequired();
        }
        // An administrator's explicit database choice always wins.
        if (ConfigManager.getConfiguredDbUrl() != null || ConfigManager.getEnvironmentDbUrl() != null) {
            return Result.notRequired();
        }

        String baseUrl = ConfigManager.get("migration.postgres.url",
                ConfigManager.getDefaultPostgresUrl()).trim();
        if (!baseUrl.startsWith("jdbc:postgresql:") || baseUrl.matches("(?i).*[?&]currentSchema=.*")) {
            return Result.fallback(null, new IllegalArgumentException(
                    "migration.postgres.url must be a PostgreSQL database URL without currentSchema"));
        }
        Path snapshot = ConfigManager.getBackupFolder().resolve("Before-PostgreSQL-Migration-"
                + FILE_TIME.format(Instant.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".db");
        String originalUrl = ConfigManager.getConfiguredDbUrl();
        try {
            BackupManager.createConsistentSnapshot(source, snapshot);
            BackupManager.ValidationResult validation = BackupManager.validateBackup(snapshot);
            if (!validation.valid()) throw new IllegalStateException(validation.message());

            String sha256 = SqliteToPostgresMigrator.sha256(snapshot);
            String schema = "dse_migration_" + sha256.substring(0, 12);
            String connectionUrl = withParameter(baseUrl, "connectTimeout", "5");
            ensureOwnedSchema(connectionUrl, schema, sha256);
            String targetUrl = withParameter(connectionUrl, "currentSchema", schema);

            ConfigManager.setWithoutSaving("db.url", targetUrl);
            DatabaseManager.initialize();
            SqliteToPostgresMigrator.MigrationReport report =
                    SqliteToPostgresMigrator.migrate(snapshot, sha256, schema);

            ConfigManager.setWithoutSaving("migration.status", "COMPLETED");
            ConfigManager.setWithoutSaving("migration.completedAt", Instant.now().toString());
            ConfigManager.setWithoutSaving("migration.source", source.toString());
            ConfigManager.setWithoutSaving("migration.safetyBackup", snapshot.toString());
            ConfigManager.setWithoutSaving("migration.applicationVersion", BuildInfo.version());
            ConfigManager.save();
            return Result.migrated(targetUrl, snapshot, report);
        } catch (Exception failure) {
            ConfigManager.setWithoutSaving("db.url", originalUrl);
            ConfigManager.setWithoutSaving("migration.status", "WAITING_FOR_POSTGRESQL");
            ConfigManager.setWithoutSaving("migration.lastAttempt", Instant.now().toString());
            ConfigManager.setWithoutSaving("migration.lastError", safeMessage(failure));
            try { ConfigManager.save(); }
            catch (Exception saveFailure) { failure.addSuppressed(saveFailure); }
            return Result.fallback(snapshot, failure);
        }
    }

    private static void ensureOwnedSchema(String baseUrl, String schema, String sha256) throws Exception {
        Class.forName("org.postgresql.Driver");
        String marker = "DSE ERP SQLite migration " + sha256;
        try (Connection connection = DriverManager.getConnection(baseUrl,
                ConfigManager.getDbUsername(), ConfigManager.getDbPassword())) {
            String existingComment = null;
            boolean exists;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT obj_description(oid,'pg_namespace') FROM pg_namespace WHERE nspname=?")) {
                query.setString(1, schema);
                try (ResultSet result = query.executeQuery()) {
                    exists = result.next();
                    if (exists) existingComment = result.getString(1);
                }
            }
            if (exists && !marker.equals(existingComment)) {
                throw new IllegalStateException("The PostgreSQL migration schema already exists but is not owned by this SQLite migration: " + schema);
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                    statement.execute("COMMENT ON SCHEMA " + schema + " IS '" + marker + "'");
                }
            }
        }
    }

    static String withParameter(String baseUrl, String name, String value) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + name + "=" + value;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public enum Status { NOT_REQUIRED, MIGRATED, SQLITE_FALLBACK }

    public record Result(Status status, String targetUrl, Path safetyBackup,
                         SqliteToPostgresMigrator.MigrationReport report, Throwable failure) {
        static Result notRequired() {
            return new Result(Status.NOT_REQUIRED, null, null, null, null);
        }
        static Result migrated(String targetUrl, Path safetyBackup,
                               SqliteToPostgresMigrator.MigrationReport report) {
            return new Result(Status.MIGRATED, targetUrl, safetyBackup, report, null);
        }
        static Result fallback(Path safetyBackup, Throwable failure) {
            return new Result(Status.SQLITE_FALLBACK, null,
                    safetyBackup != null && Files.isRegularFile(safetyBackup) ? safetyBackup : null,
                    null, failure);
        }
    }
}
