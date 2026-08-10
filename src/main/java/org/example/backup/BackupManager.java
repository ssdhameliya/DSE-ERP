package org.example.backup;

import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Central backup and staged-restore implementation for SQLite and PostgreSQL.
 * Restore is applied before DatabaseManager.initialize(), while no application connection is open.
 */
@SuppressWarnings({"SqlResolve", "SqlDialectInspection"})
public final class BackupManager {

    public static final String APPLICATION_ID = "DSE_ERP";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final Logger LOGGER = Logger.getLogger(BackupManager.class.getName());
    private static final ReentrantLock BACKUP_LOCK = new ReentrantLock();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String PENDING_RESTORE_KEY = "backup.restore.pending";
    private static final String LAST_SUCCESS_KEY = "backup.last.success";
    private static final String LAST_SCHEDULED_DATE_KEY = "backup.last.scheduled.date";

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "users", "item_master", "party_master", "sales_header", "sales_line",
            "purchase_header", "purchase_line", "application_setting"
    );
    private static final Set<String> LEGACY_NOT_VALID_CONSTRAINTS = Set.of(
            "quotation_line_item_code_fkey", "quotation_line_quotation_id_fkey",
            "return_register_item_code_fkey", "role_permission_role_id_fkey",
            "stock_adjustment_item_code_fkey"
    );

    private BackupManager() {}

    public static Path databasePath() {
        if (ConfigManager.isPostgreSql()) {
            return Path.of(ConfigManager.get("postgres.dataPath", "D:\\PostgreSQL\\18\\data"));
        }
        return ConfigManager.getDatabasePath();
    }

    public static Path backupFolder() {
        return ConfigManager.getBackupFolder();
    }

    public static void ensureFolders() throws IOException {
        if (ConfigManager.isSqlite()) {
            Path parent = databasePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        }
        Files.createDirectories(backupFolder());
        Files.createDirectories(ConfigManager.getBackupTrashFolder());
    }

    public static Path createManualBackup() throws Exception {
        Path backup = createBackup("DSE-ERP", "MANUAL");
        applyRetention(readRetentionDays());
        return backup;
    }

    public static Path createSafetyBackup() throws Exception {
        return createBackup("Before-Restore", "SAFETY");
    }

    public static Path createBackup(String prefix, String source) throws Exception {
        BACKUP_LOCK.lockInterruptibly();
        try {
            ensureFolders();
            Path target = uniquePath(backupFolder(), prefix,
                    ConfigManager.isPostgreSql() ? ".pgbackup" : ".db");
            if (ConfigManager.isPostgreSql()) createPostgresSnapshot(target);
            else createConsistentSnapshot(databasePath(), target);
            ValidationResult result = validateBackup(target);
            if (!result.valid()) {
                Files.deleteIfExists(target);
                throw new IllegalStateException(result.message());
            }
            recordBackup(target, source, result, "VERIFIED");
            ConfigManager.set(LAST_SUCCESS_KEY, Instant.now().toString());
            return target;
        } finally {
            BACKUP_LOCK.unlock();
        }
    }

    /**
     * Uses SQLite itself to checkpoint WAL and create a self-contained snapshot.
     */
    public static void createConsistentSnapshot(Path sourceDatabase, Path target) throws Exception {
        if (!Files.exists(sourceDatabase)) {
            throw new IllegalStateException("The active ERP database was not found: " + sourceDatabase);
        }
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(target);

        String url = sqliteUrl(sourceDatabase);
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=10000");
            statement.execute("PRAGMA wal_checkpoint(FULL)");
            statement.execute("VACUUM INTO '" + escapeSqlPath(target) + "'");
        }
    }

    public static ValidationResult validateBackup(Path file) throws Exception {
        if (file == null || !Files.isRegularFile(file)) {
            return ValidationResult.invalid("The selected backup file does not exist.");
        }
        if (Files.size(file) == 0) {
            return ValidationResult.invalid("The selected backup file is empty.");
        }
        if (isPostgresBackup(file)) return validatePostgresBackup(file);

        Set<String> tables = new HashSet<>();
        String applicationId = null;
        Integer schemaVersion = null;

        try (Connection connection = DriverManager.getConnection(sqliteUrl(file));
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only=ON");
            statement.execute("PRAGMA busy_timeout=10000");

            try (ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                    return ValidationResult.invalid("The selected file failed SQLite integrity validation.");
                }
            }

            try (ResultSet result = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (result.next()) tables.add(result.getString(1));
            }

            Set<String> missing = new TreeSet<>(REQUIRED_TABLES);
            missing.removeAll(tables);
            if (!missing.isEmpty()) {
                return ValidationResult.invalid(
                        "This is not a compatible DSE ERP backup. Missing table(s): " + String.join(", ", missing));
            }

            if (tables.contains("application_metadata")) {
                try (ResultSet result = statement.executeQuery(
                        "SELECT metadata_key, metadata_value FROM application_metadata")) {
                    while (result.next()) {
                        String key = result.getString(1);
                        String value = result.getString(2);
                        if ("application.id".equals(key)) applicationId = value;
                        if ("schema.version".equals(key)) {
                            try { schemaVersion = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }

        if (applicationId != null && !APPLICATION_ID.equals(applicationId)) {
            return ValidationResult.invalid("The selected database belongs to another application.");
        }
        if (schemaVersion != null && schemaVersion > CURRENT_SCHEMA_VERSION) {
            return ValidationResult.invalid(
                    "This backup was created by a newer ERP schema (" + schemaVersion
                            + "). Update the application before restoring it.");
        }

        String compatibility = schemaVersion == null
                ? "Legacy compatible backup"
                : schemaVersion < CURRENT_SCHEMA_VERSION
                ? "Compatible; database migrations will run after restart"
                : "Fully compatible";
        return ValidationResult.valid(applicationId, schemaVersion, compatibility);
    }

    public static Path importBackup(Path externalFile) throws Exception {
        ValidationResult result = validateBackup(externalFile);
        if (!result.valid()) throw new IllegalStateException(result.message());

        ensureFolders();
        Path target = uniquePath(backupFolder(), "Imported",
                isPostgresBackup(externalFile) ? ".pgbackup" : ".db");
        Files.copy(externalFile, target, StandardCopyOption.COPY_ATTRIBUTES);
        ValidationResult copiedResult = validateBackup(target);
        if (!copiedResult.valid()) {
            Files.deleteIfExists(target);
            throw new IllegalStateException("The imported copy could not be validated: " + copiedResult.message());
        }
        recordBackup(target, "IMPORTED", copiedResult, "VERIFIED");
        return target;
    }

    /**
     * Copies a validated backup to a staging file. The live DB remains untouched until next startup.
     */
    public static void stageRestore(Path selectedBackup) throws Exception {
        ValidationResult result = validateBackup(selectedBackup);
        if (!result.valid()) throw new IllegalStateException(result.message());
        if (ConfigManager.isPostgreSql() && !isPostgresBackup(selectedBackup)) {
            throw new IllegalStateException("A PostgreSQL installation can restore only .pgbackup files. " +
                    "Use the SQLite migration utility for legacy .db files.");
        }

        ensureFolders();
        Path pending = ConfigManager.getPendingRestoreFile();
        Path temporary = pending.resolveSibling(pending.getFileName() + ".tmp");
        Files.copy(selectedBackup, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

        ValidationResult staged = validateBackup(temporary);
        if (!staged.valid()) {
            Files.deleteIfExists(temporary);
            throw new IllegalStateException("The staged restore file failed validation: " + staged.message());
        }
        atomicReplace(temporary, pending);
        ConfigManager.set(PENDING_RESTORE_KEY, "true");
        ConfigManager.set("backup.restore.source", selectedBackup.toAbsolutePath().toString());
        ConfigManager.set("backup.restore.staged_at", Instant.now().toString());
    }

    public static boolean hasPendingRestore() {
        return "true".equalsIgnoreCase(ConfigManager.get(PENDING_RESTORE_KEY, "false"))
                && Files.isRegularFile(ConfigManager.getPendingRestoreFile());
    }

    /**
     * Must be called after ConfigManager.load() and before DatabaseManager.initialize().
     */
    public static RestoreResult applyPendingRestoreIfPresent() {
        if (!hasPendingRestore()) return RestoreResult.none();

        if (ConfigManager.isPostgreSql()) return applyPendingPostgresRestore();

        Path pending = ConfigManager.getPendingRestoreFile();
        Path database = databasePath();
        Path safety = null;
        try {
            ensureFolders();
            ValidationResult pendingValidation = validateBackup(pending);
            if (!pendingValidation.valid()) {
                clearPendingRestore();
                return RestoreResult.failed("Pending restore was rejected: " + pendingValidation.message(), null);
            }

            if (Files.exists(database)) {
                safety = createSafetyBackup();
            }

            Path temporary = database.resolveSibling(database.getFileName() + ".restore.tmp");
            Files.copy(pending, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            ValidationResult temporaryValidation = validateBackup(temporary);
            if (!temporaryValidation.valid()) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException(temporaryValidation.message());
            }

            deleteSidecarFiles(database);
            atomicReplace(temporary, database);
            deleteSidecarFiles(database);

            ValidationResult finalValidation = validateBackup(database);
            if (!finalValidation.valid()) {
                throw new IllegalStateException("Restored database failed final validation: " + finalValidation.message());
            }

            Files.deleteIfExists(pending);
            clearPendingRestore();
            ConfigManager.set("backup.restore.last_success", Instant.now().toString());
            return RestoreResult.applied(database, safety);
        } catch (Exception failure) {
            LOGGER.log(Level.SEVERE, "Pending database restore failed", failure);
            try {
                if (safety != null && Files.isRegularFile(safety)) {
                    Path rollbackTemp = database.resolveSibling(database.getFileName() + ".rollback.tmp");
                    Files.copy(safety, rollbackTemp, StandardCopyOption.REPLACE_EXISTING);
                    atomicReplace(rollbackTemp, database);
                }
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                LOGGER.log(Level.SEVERE, "Restore rollback also failed", rollbackFailure);
            }
            quarantinePendingRestore();
            return RestoreResult.failed(
                    "The staged restore could not be applied. The existing database was preserved or rolled back.", failure);
        }
    }

    private static RestoreResult applyPendingPostgresRestore() {
        Path pending = ConfigManager.getPendingRestoreFile();
        Path safety = null;
        try {
            if (!isPostgresBackup(pending)) {
                clearPendingRestore();
                return RestoreResult.failed("Pending restore is not a PostgreSQL backup.", null);
            }
            ValidationResult validation = validatePostgresBackup(pending);
            if (!validation.valid()) {
                clearPendingRestore();
                return RestoreResult.failed(validation.message(), null);
            }
            safety = createSafetyBackup();
            restorePostgresBackup(pending);
            Files.deleteIfExists(pending);
            clearPendingRestore();
            ConfigManager.set("backup.restore.last_success", Instant.now().toString());
            return RestoreResult.applied(databasePath(), safety);
        } catch (Exception failure) {
            LOGGER.log(Level.SEVERE, "Pending PostgreSQL restore failed", failure);
            quarantinePendingRestore();
            return RestoreResult.failed(
                    "The PostgreSQL restore could not be applied; the safety backup was preserved.", failure);
        }
    }

    public static Optional<Path> createScheduledBackupIfDue() {
        String schedule = readSetting("backup.schedule", "MANUAL").toUpperCase(Locale.ROOT);
        if ("MANUAL".equals(schedule)) return Optional.empty();

        LocalDate today = LocalDate.now();
        LocalDate lastDate = parseDate(ConfigManager.get(LAST_SCHEDULED_DATE_KEY, ""));
        boolean due = switch (schedule) {
            case "DAILY" -> lastDate == null || lastDate.isBefore(today);
            case "WEEKLY" -> lastDate == null || ChronoUnit.DAYS.between(lastDate, today) >= 7;
            case "MONTHLY" -> lastDate == null
                    || lastDate.getYear() != today.getYear()
                    || lastDate.getMonth() != today.getMonth();
            default -> false;
        };
        if (!due) return Optional.empty();

        try {
            Path backup = createBackup("Scheduled", "SCHEDULED");
            ConfigManager.set(LAST_SCHEDULED_DATE_KEY, today.toString());
            applyRetention(readRetentionDays());
            return Optional.of(backup);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Scheduled backup failed", exception);
            return Optional.empty();
        }
    }

    public static int applyRetention(int retentionDays) throws IOException {
        ensureFolders();
        int days = Math.max(1, retentionDays);
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        List<Path> managed;
        try (Stream<Path> stream = Files.list(backupFolder())) {
            managed = stream.filter(BackupManager::isManagedBackup)
                    .sorted(Comparator.comparing(BackupManager::modified).reversed())
                    .toList();
        }

        Set<Path> alwaysKeep = new HashSet<>(managed.stream().limit(3).toList());
        int moved = 0;
        for (Path file : managed) {
            if (alwaysKeep.contains(file)) continue;
            if (file.getFileName().toString().startsWith("Before-Restore-")) continue;
            if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                moveToTrash(file);
                moved++;
            }
        }
        purgeTrash(Duration.ofDays(7));
        return moved;
    }

    public static void deleteBackupSafely(Path backup) throws Exception {
        ValidationResult result = validateBackup(backup);
        if (result.valid() && countValidBackups() <= 1) {
            throw new IllegalStateException(
                    "This is the last valid backup. Create another verified backup before deleting it.");
        }
        moveToTrash(backup);
    }

    public static int countValidBackups() throws IOException {
        int count = 0;
        if (!Files.isDirectory(backupFolder())) return 0;
        try (Stream<Path> stream = Files.list(backupFolder())) {
            for (Path path : stream.filter(BackupManager::isManagedBackup).toList()) {
                try {
                    if (validateBackup(path).valid()) count++;
                } catch (Exception ignored) {}
            }
        }
        return count;
    }

    public static void ensureApplicationMetadata() {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS application_metadata ("
                    + "metadata_key TEXT PRIMARY KEY, metadata_value TEXT NOT NULL)");
            upsertMetadata(connection, "application.id", APPLICATION_ID);
            upsertMetadata(connection, "schema.version", String.valueOf(CURRENT_SCHEMA_VERSION));
            upsertMetadata(connection, "application.version", "2.0");
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Application metadata could not be initialized", exception);
        }
    }

    public static Optional<BackupMetadata> metadataFor(Path file) {
        if (file == null) return Optional.empty();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT source_type,integrity_status,schema_version,application_id " +
                             "FROM backup_history WHERE file_name=?")) {
            statement.setString(1, file.getFileName().toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Integer version = result.getObject(3) == null ? null : result.getInt(3);
                return Optional.of(new BackupMetadata(
                        result.getString(1), result.getString(2), version, result.getString(4)));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static void updateValidationStatus(Path file, ValidationResult validation) {
        if (file == null) return;
        String status = validation != null && validation.valid() ? "VERIFIED" : "INVALID";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO backup_history(
                         file_name, original_name, source_type, created_at,
                         file_size, integrity_status, schema_version, application_id
                     ) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,?,?)
                     ON CONFLICT(file_name) DO UPDATE SET
                         integrity_status=excluded.integrity_status,
                         schema_version=excluded.schema_version,
                         application_id=excluded.application_id,
                         file_size=excluded.file_size
                     """)) {
            statement.setString(1, file.getFileName().toString());
            statement.setString(2, file.getFileName().toString());
            statement.setString(3, metadataFor(file).map(BackupMetadata::source).orElse("MANUAL"));
            statement.setLong(4, Files.exists(file) ? Files.size(file) : 0L);
            statement.setString(5, status);
            if (validation == null || validation.schemaVersion() == null) statement.setNull(6, Types.INTEGER);
            else statement.setInt(6, validation.schemaVersion());
            statement.setString(7, validation == null ? null : validation.applicationId());
            statement.executeUpdate();
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Backup validation status could not be stored", exception);
        }
    }

    public static String readSetting(String key, String defaultValue) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT setting_value FROM application_setting WHERE setting_key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : defaultValue;
            }
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    public static int readRetentionDays() {
        try {
            return Math.max(1, Integer.parseInt(readSetting("backup.retention", "30")));
        } catch (NumberFormatException ignored) {
            return 30;
        }
    }

    private static void recordBackup(Path file, String source, ValidationResult validation, String status) {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO backup_history(
                         file_name, original_name, source_type, created_at,
                         file_size, integrity_status, schema_version, application_id
                     ) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,?,?)
                     ON CONFLICT(file_name) DO UPDATE SET
                         source_type=excluded.source_type,
                         file_size=excluded.file_size,
                         integrity_status=excluded.integrity_status,
                         schema_version=excluded.schema_version,
                         application_id=excluded.application_id
                     """)) {
            statement.setString(1, file.getFileName().toString());
            statement.setString(2, file.getFileName().toString());
            statement.setString(3, source);
            statement.setLong(4, Files.size(file));
            statement.setString(5, status);
            if (validation.schemaVersion() == null) statement.setNull(6, Types.INTEGER);
            else statement.setInt(6, validation.schemaVersion());
            statement.setString(7, validation.applicationId());
            statement.executeUpdate();
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Backup history metadata could not be stored", exception);
        }
    }

    private static void upsertMetadata(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO application_metadata(metadata_key,metadata_value) VALUES(?,?) "
                        + "ON CONFLICT(metadata_key) DO UPDATE SET metadata_value=excluded.metadata_value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private static void quarantinePendingRestore() {
        Path pending = ConfigManager.getPendingRestoreFile();
        try {
            if (Files.isRegularFile(pending)) {
                Files.createDirectories(backupFolder());
                Path quarantine = uniquePath(backupFolder(), "Failed-Restore",
                        isPostgresBackup(pending) ? ".pgbackup" : ".db");
                Files.move(pending, quarantine, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Failed restore file could not be quarantined", exception);
        }
        ConfigManager.remove(PENDING_RESTORE_KEY);
        ConfigManager.remove("backup.restore.source");
        ConfigManager.remove("backup.restore.staged_at");
    }

    private static void clearPendingRestore() {
        try { Files.deleteIfExists(ConfigManager.getPendingRestoreFile()); } catch (IOException ignored) {}
        ConfigManager.remove(PENDING_RESTORE_KEY);
        ConfigManager.remove("backup.restore.source");
        ConfigManager.remove("backup.restore.staged_at");
    }

    private static void deleteSidecarFiles(Path database) throws IOException {
        Files.deleteIfExists(Path.of(database + "-wal"));
        Files.deleteIfExists(Path.of(database + "-shm"));
        Files.deleteIfExists(Path.of(database + "-journal"));
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path uniquePath(Path folder, String prefix, String extension) {
        String timestamp = LocalDateTime.now().format(FILE_TS);
        Path candidate = folder.resolve(prefix + "-" + timestamp + extension);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = folder.resolve(prefix + "-" + timestamp + "-" + suffix++ + extension);
        }
        return candidate;
    }

    private static String sqliteUrl(Path path) {
        ensureSqliteDriver();
        return "jdbc:sqlite:" + path.toAbsolutePath().normalize();
    }

    private static void ensureSqliteDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is unavailable", exception);
        }
    }

    private static String escapeSqlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("'", "''");
    }

    private static boolean isManagedBackup(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path)
                && (filename.endsWith(".db") || filename.endsWith(".pgbackup"))
                && !path.equals(ConfigManager.getPendingRestoreFile());
    }

    private static void createPostgresSnapshot(Path target) throws Exception {
        Files.createDirectories(target.toAbsolutePath().getParent());
        List<String> connection = postgresConnectionArguments();
        List<String> command = new ArrayList<>();
        command.add(postgresTool("pg_dump.exe").toString());
        command.addAll(connection);
        command.add("--format=custom");
        command.add("--no-owner");
        command.add("--file=" + target.toAbsolutePath());
        command.add(postgresDatabaseName());
        runPostgresTool(command);
    }

    private static ValidationResult validatePostgresBackup(Path file) throws Exception {
        List<String> command = List.of(postgresTool("pg_restore.exe").toString(), "--list", file.toAbsolutePath().toString());
        String listing = runPostgresTool(command);
        Set<String> missing = new TreeSet<>();
        for (String table : REQUIRED_TABLES) {
            if (!listing.matches("(?s).*\\bTABLE(?: DATA)?\\s+public\\s+" + Pattern.quote(table) + "\\b.*")) {
                missing.add(table);
            }
        }
        if (!missing.isEmpty()) {
            return ValidationResult.invalid("PostgreSQL backup is missing table(s): " + String.join(", ", missing));
        }
        return ValidationResult.valid(APPLICATION_ID, CURRENT_SCHEMA_VERSION, "Fully compatible PostgreSQL backup");
    }

    private static void restorePostgresBackup(Path backup) throws Exception {
        String listing = runPostgresTool(List.of(
                postgresTool("pg_restore.exe").toString(), "--list", backup.toAbsolutePath().toString()));
        Path useList = Files.createTempFile("dse-erp-restore-", ".list");
        try {
            List<String> filtered = listing.lines()
                    .filter(line -> LEGACY_NOT_VALID_CONSTRAINTS.stream().noneMatch(line::contains))
                    .toList();
            Files.write(useList, filtered, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(postgresTool("pg_restore.exe").toString());
            command.addAll(postgresConnectionArguments());
            command.add("--clean");
            command.add("--if-exists");
            command.add("--no-owner");
            command.add("--no-privileges");
            command.add("--single-transaction");
            command.add("--use-list=" + useList.toAbsolutePath());
            command.add("--dbname=" + postgresDatabaseName());
            command.add(backup.toAbsolutePath().toString());
            dropLegacyNotValidConstraints();
            try {
                runPostgresTool(command);
                addLegacyNotValidConstraints();
            } catch (Exception failure) {
                try { addLegacyNotValidConstraints(); } catch (Exception repairFailure) {
                    failure.addSuppressed(repairFailure);
                }
                throw failure;
            }
        } finally {
            Files.deleteIfExists(useList);
        }
    }

    private static void addLegacyNotValidConstraints() throws SQLException {
        String[] statements = {
                "ALTER TABLE quotation_line ADD CONSTRAINT quotation_line_item_code_fkey FOREIGN KEY(item_code) REFERENCES item_master(item_code) NOT VALID",
                "ALTER TABLE quotation_line ADD CONSTRAINT quotation_line_quotation_id_fkey FOREIGN KEY(quotation_id) REFERENCES quotation_header(id) ON DELETE CASCADE NOT VALID",
                "ALTER TABLE return_register ADD CONSTRAINT return_register_item_code_fkey FOREIGN KEY(item_code) REFERENCES item_master(item_code) NOT VALID",
                "ALTER TABLE role_permission ADD CONSTRAINT role_permission_role_id_fkey FOREIGN KEY(role_id) REFERENCES roles(id) ON DELETE CASCADE NOT VALID",
                "ALTER TABLE stock_adjustment ADD CONSTRAINT stock_adjustment_item_code_fkey FOREIGN KEY(item_code) REFERENCES item_master(item_code) NOT VALID"
        };
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        }
    }

    private static void dropLegacyNotValidConstraints() throws SQLException {
        String[] statements = {
                "ALTER TABLE quotation_line DROP CONSTRAINT IF EXISTS quotation_line_item_code_fkey",
                "ALTER TABLE quotation_line DROP CONSTRAINT IF EXISTS quotation_line_quotation_id_fkey",
                "ALTER TABLE return_register DROP CONSTRAINT IF EXISTS return_register_item_code_fkey",
                "ALTER TABLE role_permission DROP CONSTRAINT IF EXISTS role_permission_role_id_fkey",
                "ALTER TABLE stock_adjustment DROP CONSTRAINT IF EXISTS stock_adjustment_item_code_fkey"
        };
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        }
    }

    private static boolean isPostgresBackup(Path file) {
        try (var input = Files.newInputStream(file)) {
            return "PGDMP".equals(new String(input.readNBytes(5), StandardCharsets.US_ASCII));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static List<String> postgresConnectionArguments() {
        URI uri = URI.create(ConfigManager.getDbUrl().substring("jdbc:".length()));
        int port = uri.getPort() < 0 ? 5432 : uri.getPort();
        return List.of("--host=" + uri.getHost(), "--port=" + port,
                "--username=" + ConfigManager.getDbUsername());
    }

    private static String postgresDatabaseName() {
        URI uri = URI.create(ConfigManager.getDbUrl().substring("jdbc:".length()));
        String path = uri.getPath();
        return path == null || path.length() <= 1 ? "dse_erp" : path.substring(1);
    }

    private static Path postgresTool(String executable) {
        return Path.of(ConfigManager.get("postgres.binPath", "D:\\PostgreSQL\\18\\pgsql\\bin"), executable);
    }

    private static String runPostgresTool(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", ConfigManager.getDbPassword());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("PostgreSQL backup command failed: " + output.trim());
        return output;
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0; }
    }

    private static void moveToTrash(Path file) throws IOException {
        Files.createDirectories(ConfigManager.getBackupTrashFolder());
        Path target = ConfigManager.getBackupTrashFolder().resolve(
                Instant.now().toEpochMilli() + "-" + file.getFileName());
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void purgeTrash(Duration age) throws IOException {
        Path trash = ConfigManager.getBackupTrashFolder();
        if (!Files.isDirectory(trash)) return;
        Instant cutoff = Instant.now().minus(age);
        try (Stream<Path> stream = Files.list(trash)) {
            for (Path file : stream.toList()) {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (Exception ignored) { return null; }
    }

    public record BackupMetadata(
            String source,
            String status,
            Integer schemaVersion,
            String applicationId
    ) {}

    public record ValidationResult(
            boolean valid,
            String message,
            String applicationId,
            Integer schemaVersion,
            String compatibility
    ) {
        public static ValidationResult valid(String applicationId, Integer schemaVersion, String compatibility) {
            return new ValidationResult(true, "Backup validation passed.", applicationId, schemaVersion, compatibility);
        }
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, null, null, "Incompatible");
        }
    }

    public record RestoreResult(
            boolean attempted,
            boolean applied,
            String message,
            Path database,
            Path safetyBackup,
            Throwable failure
    ) {
        public static RestoreResult none() {
            return new RestoreResult(false, false, "No pending restore.", null, null, null);
        }
        public static RestoreResult applied(Path database, Path safety) {
            return new RestoreResult(true, true, "The staged database restore was applied successfully.", database, safety, null);
        }
        public static RestoreResult failed(String message, Throwable failure) {
            return new RestoreResult(true, false, message, null, null, failure);
        }
    }
}
