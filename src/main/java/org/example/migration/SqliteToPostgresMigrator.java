package org.example.migration;

import org.example.database.DatabaseManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
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
        Class.forName("org.sqlite.JDBC");
        DatabaseManager.initialize();
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toUri() + "?mode=ro");
             Connection postgres = DatabaseManager.getConnection()) {
            List<String> tables = migrationOrder(sqlite, postgres);
            postgres.setAutoCommit(false);
            try {
                try (Statement statement = postgres.createStatement()) {
                    statement.execute("SET session_replication_role = replica");
                    for (int i = tables.size() - 1; i >= 0; i--) {
                        statement.execute("TRUNCATE TABLE " + identifier(tables.get(i)) + " CASCADE");
                    }
                }
                for (String table : tables) copyTable(sqlite, postgres, table);
                resetSequences(postgres, tables);
                try (Statement statement = postgres.createStatement()) {
                    statement.execute("SET session_replication_role = origin");
                }
                postgres.commit();
                System.out.println("Migrated " + tables.size() + " tables from " + sqliteFile);
            } catch (Exception exception) {
                postgres.rollback();
                throw exception;
            }
        }
    }

    private static List<String> migrationOrder(Connection sqlite, Connection postgres) throws SQLException {
        Set<String> postgresTables = new TreeSet<>();
        try (ResultSet result = postgres.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
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

    private static void copyTable(Connection sqlite, Connection postgres, String table) throws SQLException {
        List<String> sourceColumns = columns(sqlite, null, table);
        Set<String> targetColumns = new HashSet<>(columns(postgres, "public", table));
        List<String> columns = sourceColumns.stream().filter(targetColumns::contains).toList();
        if (columns.isEmpty()) return;
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
    }

    private static List<String> columns(Connection connection, String schema, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, schema, table, "%")) {
            while (result.next()) columns.add(result.getString("COLUMN_NAME"));
        }
        return columns;
    }

    private static void resetSequences(Connection postgres, List<String> tables) throws SQLException {
        for (String table : tables) {
            if (!columns(postgres, "public", table).contains("id")) continue;
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

    private static String identifier(String value) {
        if (!IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException("Unsafe SQL identifier: " + value);
        return value;
    }
}
