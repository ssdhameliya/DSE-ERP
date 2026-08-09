package org.example.migration;

import org.example.database.DatabaseManager;
import org.example.update.BuildInfo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** One-way, read-only SQLite to PostgreSQL data migration utility. */
public final class SqliteToPostgresMigrator {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private SqliteToPostgresMigrator() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Usage: SqliteToPostgresMigrator <SQLite database file>");
        Path source = Path.of(String.join(" ", args)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("SQLite database not found: " + source);
        migrate(source);
    }

    public static void migrate(Path sqliteFile) throws Exception {
        String sourceSha256 = sha256(sqliteFile);
        String schema = "dse_migration_" + sourceSha256.substring(0, 12);
        DatabaseManager.initialize();
        migrate(sqliteFile, sourceSha256, schema);
    }

    public static MigrationReport migrate(Path sqliteFile, String sourceSha256,
                                          String expectedSchema) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toUri() + "?mode=ro");
             Connection postgres = DatabaseManager.getConnection()) {
            String schema = postgres.getSchema();
            if (!expectedSchema.equals(schema) || !schema.matches("dse_migration_[0-9a-f]{12}")) {
                throw new SQLException("SQLite migration requires its isolated PostgreSQL schema; active schema is " + schema);
            }
            List<String> tables = migrationOrder(sqlite, postgres, schema);
            if (sourceSha256.equals(readMetadata(postgres, "migration.sqlite.sha256"))) {
                return new MigrationReport(tables.size(), 0, true);
            }
            postgres.setAutoCommit(false);
            try {
                try (Statement statement = postgres.createStatement()) {
                    for (int i = tables.size() - 1; i >= 0; i--) {
                        statement.execute("TRUNCATE TABLE " + identifier(tables.get(i)) + " CASCADE");
                    }
                }
                Map<String, Integer> copied = new LinkedHashMap<>();
                for (String table : tables) copied.put(table, copyTable(sqlite, postgres, table));
                verifyCounts(sqlite, postgres, copied);
                resetSequences(postgres, schema, tables);
                writeMigrationMetadata(postgres, sqliteFile, sourceSha256);
                postgres.commit();
                int rows = copied.values().stream().mapToInt(Integer::intValue).sum();
                System.out.println("Migrated " + tables.size() + " tables and " + rows + " rows from " + sqliteFile);
                return new MigrationReport(tables.size(), rows, false);
            } catch (Exception exception) {
                postgres.rollback();
                throw exception;
            } finally {
                postgres.setAutoCommit(true);
            }
        }
    }

    private static List<String> migrationOrder(Connection sqlite, Connection postgres,
                                               String schema) throws SQLException {
        Set<String> postgresTables = new TreeSet<>();
        try (ResultSet result = postgres.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (result.next()) postgresTables.add(result.getString("TABLE_NAME"));
        }
        Set<String> remaining = new TreeSet<>();
        try (Statement statement = sqlite.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (result.next()) if (postgresTables.contains(result.getString(1))) remaining.add(result.getString(1));
        }
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String table : remaining) {
            Set<String> refs = new HashSet<>();
            try (Statement statement = sqlite.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA foreign_key_list(" + identifier(table) + ")")) {
                while (result.next()) if (remaining.contains(result.getString("table"))) refs.add(result.getString("table"));
            }
            refs.remove(table);
            dependencies.put(table, refs);
        }
        List<String> ordered = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<String> ready = remaining.stream()
                    .filter(table -> ordered.containsAll(dependencies.getOrDefault(table, Set.of())))
                    .toList();
            if (ready.isEmpty()) ready = List.of(remaining.iterator().next());
            ordered.addAll(ready);
            remaining.removeAll(ready);
        }
        return ordered;
    }

    private static int copyTable(Connection sqlite, Connection postgres, String table) throws SQLException {
        List<String> sourceColumns = columns(sqlite, null, table);
        Set<String> targetColumns = new HashSet<>(columns(postgres, postgres.getSchema(), table));
        List<String> columns = sourceColumns.stream().filter(targetColumns::contains).toList();
        if (columns.isEmpty()) return 0;
        String names = String.join(",", columns.stream().map(SqliteToPostgresMigrator::identifier).toList());
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        int count = 0;
        try (Statement read = sqlite.createStatement();
             ResultSet rows = read.executeQuery("SELECT " + names + " FROM " + identifier(table));
             PreparedStatement write = postgres.prepareStatement(
                     "INSERT INTO " + identifier(table) + "(" + names + ") VALUES(" + placeholders + ")")) {
            while (rows.next()) {
                for (int index = 1; index <= columns.size(); index++) write.setObject(index, rows.getObject(index));
                write.addBatch();
                if (++count % 500 == 0) write.executeBatch();
            }
            write.executeBatch();
        }
        System.out.println(table + ": " + count + " rows");
        return count;
    }

    private static void verifyCounts(Connection sqlite, Connection postgres,
                                     Map<String, Integer> copied) throws SQLException {
        for (Map.Entry<String, Integer> entry : copied.entrySet()) {
            String table = identifier(entry.getKey());
            int source = rowCount(sqlite, table);
            int target = rowCount(postgres, table);
            if (source != entry.getValue() || source != target) {
                throw new SQLException("Migration verification failed for " + table
                        + ": SQLite=" + source + ", copied=" + entry.getValue()
                        + ", PostgreSQL=" + target);
            }
        }
    }

    private static int rowCount(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + identifier(table))) {
            if (!result.next()) throw new SQLException("Unable to count table " + table);
            return result.getInt(1);
        }
    }

    private static List<String> columns(Connection connection, String schema, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, schema, table, "%")) {
            while (result.next()) columns.add(result.getString("COLUMN_NAME"));
        }
        return columns;
    }

    private static void resetSequences(Connection postgres, String schema,
                                       List<String> tables) throws SQLException {
        for (String table : tables) {
            if (!columns(postgres, schema, table).contains("id")) continue;
            String name = identifier(table);
            try (Statement statement = postgres.createStatement()) {
                statement.execute("SELECT setval(pg_get_serial_sequence('" + name + "','id'), " +
                        "GREATEST(COALESCE((SELECT MAX(id) FROM " + name + "),1),1), " +
                        "EXISTS(SELECT 1 FROM " + name + "))");
            } catch (SQLException ignored) {
                // Tables with a non-generated id intentionally have no sequence.
            }
        }
    }

    private static String readMetadata(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT metadata_value FROM application_metadata WHERE metadata_key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void writeMigrationMetadata(Connection connection, Path source,
                                               String sourceSha256) throws SQLException {
        String sql = "INSERT INTO application_metadata(metadata_key,metadata_value) VALUES(?,?) "
                + "ON CONFLICT(metadata_key) DO UPDATE SET metadata_value=EXCLUDED.metadata_value";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            putMetadata(statement, "migration.sqlite.sha256", sourceSha256);
            putMetadata(statement, "migration.sqlite.source", source.toAbsolutePath().normalize().toString());
            putMetadata(statement, "migration.completed_at", Instant.now().toString());
            putMetadata(statement, "migration.application_version", BuildInfo.version());
        }
    }

    private static void putMetadata(PreparedStatement statement, String key, String value) throws SQLException {
        statement.setString(1, key);
        statement.setString(2, value);
        statement.executeUpdate();
    }

    public static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String identifier(String value) {
        if (!IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException("Unsafe SQL identifier: " + value);
        return value;
    }

    public record MigrationReport(int tableCount, int rowCount, boolean alreadyMigrated) {}
}
