package org.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Properties;

/**
 * Stores the selected DSE ERP workspace outside the application bundle.
 * The small pointer file remains in the operating-system application-data folder,
 * while all business data may live on another internal drive or external volume.
 */
public final class WorkspaceManager {
    private static final String APP_NAME = "DSE ERP";
    private static final String WORKSPACE_KEY = "workspace.path";
    private static final Path POINTER_FOLDER = resolvePointerFolder();
    private static final Path POINTER_FILE = POINTER_FOLDER.resolve("workspace.properties");
    private static final Path PENDING_MOVE_FILE = POINTER_FOLDER.resolve("workspace-move.properties");
    private static final Path MANAGED_SHARED_ROOT = POINTER_FOLDER.resolve("SharedClient").toAbsolutePath().normalize();

    private static Path workspaceRoot;

    private WorkspaceManager() {}

    public static synchronized void initialize() {
        try {
            Files.createDirectories(POINTER_FOLDER);
            applyPendingMoveIfPresent();

            if (Files.isRegularFile(POINTER_FILE)) {
                Properties properties = readProperties(POINTER_FILE);
                String value = properties.getProperty(WORKSPACE_KEY, "").trim();
                if (!value.isBlank()) {
                    Path candidate = Path.of(value).toAbsolutePath().normalize();
                    if (Files.isDirectory(candidate)) {
                        // A workstation promoted from LOCAL to SHARED_CLIENT must no longer depend on
                        // the old business workspace being mounted. Preserve that workspace untouched
                        // and move only the client configuration to DSE ERP managed application data.
                        if (isSharedClientConfig(candidate) && !candidate.equals(MANAGED_SHARED_ROOT)) {
                            migrateSharedClientConfiguration(candidate, MANAGED_SHARED_ROOT);
                            writePointer(MANAGED_SHARED_ROOT);
                            workspaceRoot = MANAGED_SHARED_ROOT;
                            ensureStructure(workspaceRoot);
                        } else {
                            workspaceRoot = candidate;
                            ensureStructure(candidate);
                        }
                        return;
                    }
                }
            }

            // Missing/invalid pointers are recoverable. The setup/recovery screen always
            // offers "Use Existing Workspace" so an upgrade never forces data recreation.
            workspaceRoot = null;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize the DSE ERP workspace.", exception);
        }
    }

    public static synchronized boolean isConfigured() {
        return workspaceRoot != null && Files.isDirectory(workspaceRoot);
    }

    public static synchronized boolean isSetupComplete() {
        if (!isConfigured()) return false;
        Path config = workspaceRoot.resolve("Config").resolve("config.properties");
        if (!Files.isRegularFile(config)) return false;
        try {
            return Boolean.parseBoolean(readProperties(config).getProperty("setup.completed", "false"));
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Inspects a user-selected folder without creating, deleting or rewriting anything.
     * Runtime/database verification is intentionally performed after this structural check.
     */
    public static synchronized ExistingWorkspaceInspection inspectExisting(Path selectedRoot) {
        if (selectedRoot == null) return new ExistingWorkspaceInspection(false, null,
                "Select the folder that contains your existing DSE ERP workspace.", false, false, false);
        Path normalized = selectedRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) return new ExistingWorkspaceInspection(false, normalized,
                "The selected folder does not exist or is not accessible. No files were changed.", false, false, false);
        Path config = normalized.resolve("Config").resolve("config.properties");
        Path database = normalized.resolve("Database");
        Path pgVersion = database.resolve("PostgreSQL").resolve("data").resolve("PG_VERSION");
        boolean hasConfig = Files.isRegularFile(config);
        boolean hasDatabase = Files.isDirectory(database);
        boolean hasPostgres = Files.isRegularFile(pgVersion);
        if (!hasConfig || !hasDatabase) {
            return new ExistingWorkspaceInspection(false, normalized,
                    "This folder is not a valid DSE ERP workspace. Expected Config/config.properties and Database. No files were changed.",
                    hasConfig, hasDatabase, hasPostgres);
        }
        return new ExistingWorkspaceInspection(true, normalized,
                "Existing DSE ERP workspace structure detected.", hasConfig, hasDatabase, hasPostgres);
    }

    /**
     * Connects to a structurally valid existing workspace. It never bootstraps or overwrites
     * company/users/database data; callers must verify the existing database through Setup API.
     */
    public static synchronized void configureExisting(Path selectedRoot) throws IOException {
        ExistingWorkspaceInspection inspection = inspectExisting(selectedRoot);
        if (!inspection.valid()) throw new IllegalArgumentException(inspection.message());
        verifyWritable(inspection.root());
        writePointer(inspection.root());
        workspaceRoot = inspection.root();
        // Add only non-destructive standard folders that may have been introduced by newer releases.
        ensureStructure(workspaceRoot);
    }

    /** Repairs only the local setup marker after the server proves the database already has users/admin. */
    public static synchronized void markSetupComplete() throws IOException {
        if (!isConfigured()) throw new IllegalStateException("DSE ERP workspace has not been configured yet.");
        Path config = workspaceRoot.resolve("Config").resolve("config.properties");
        Properties properties = Files.isRegularFile(config) ? readProperties(config) : new Properties();
        properties.setProperty("setup.completed", "true");
        Files.createDirectories(config.getParent());
        try (OutputStream output = Files.newOutputStream(config, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "DSE ERP workspace configuration");
        }
    }

    public static synchronized Path getWorkspaceRoot() {
        if (!isConfigured()) {
            throw new IllegalStateException("DSE ERP workspace has not been configured yet.");
        }
        return workspaceRoot;
    }

    public static synchronized Path getSuggestedWorkspace() {
        return Path.of(System.getProperty("user.home"), "DSE ERP Workspace")
                .toAbsolutePath().normalize();
    }

    public static synchronized void configure(Path selectedRoot) throws IOException {
        if (selectedRoot == null) throw new IllegalArgumentException("Workspace folder is required.");
        Path normalized = selectedRoot.toAbsolutePath().normalize();
        ensureStructure(normalized);
        verifyWritable(normalized);
        writePointer(normalized);
        workspaceRoot = normalized;
    }

    /**
     * Creates the small application-managed storage used by a shared client. Users never choose
     * this folder and it is not a business workspace: PostgreSQL and authoritative business data
     * remain on the company server.
     */
    public static synchronized void configureSharedClient() throws IOException {
        ensureStructure(MANAGED_SHARED_ROOT);
        verifyWritable(MANAGED_SHARED_ROOT);
        writePointer(MANAGED_SHARED_ROOT);
        workspaceRoot = MANAGED_SHARED_ROOT;
    }

    /**
     * Switches an existing LOCAL installation to an already-existing company server without
     * modifying the old local workspace. Only a sanitized client profile is written beneath
     * the application-data folder, then the persistent workspace pointer is moved to that
     * managed client storage. No database, attachment, document or business-setting upload is
     * performed here; local-company promotion remains a separate workflow.
     */
    public static synchronized void connectToExistingSharedClient(String serverUrl, String environment) throws IOException {
        Path sourceRoot = getWorkspaceRoot();
        if (sourceRoot.equals(MANAGED_SHARED_ROOT)) {
            // Idempotent safety: a managed Shared Client may arrive here when an old
            // environment override reported LOCAL. Update the verified connection in
            // place instead of throwing and leaving the user with a silent JavaFX error.
            updateManagedSharedClientConnection(serverUrl, environment);
            return;
        }

        try {
            createSharedClientConfigurationFromLocal(sourceRoot, MANAGED_SHARED_ROOT, serverUrl, environment);
            verifyWritable(MANAGED_SHARED_ROOT);
            if (!isSharedClientConfig(MANAGED_SHARED_ROOT)) {
                throw new IOException("The new managed Shared Client profile could not be verified.");
            }
            writePointer(MANAGED_SHARED_ROOT);
            workspaceRoot = MANAGED_SHARED_ROOT;
        } catch (IOException | RuntimeException failure) {
            // The LOCAL workspace itself is never modified. If activation failed after
            // staging the managed profile, restore the persistent pointer to LOCAL so
            // the workstation cannot start in a half-switched state.
            try { writePointer(sourceRoot); }
            catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            workspaceRoot = sourceRoot;
            throw failure;
        }
    }

    /** Updates only the verified company-server identity of an existing managed Shared Client. */
    public static synchronized void updateManagedSharedClientConnection(String serverUrl, String environment) throws IOException {
        if (!isConfigured() || !workspaceRoot.equals(MANAGED_SHARED_ROOT)) {
            throw new IllegalStateException("This PC is not using application-managed shared-client storage.");
        }
        Path config = MANAGED_SHARED_ROOT.resolve("Config").resolve("config.properties");
        if (!Files.isRegularFile(config)) throw new IOException("Managed Shared Client configuration is missing: " + config);
        Properties properties = readProperties(config);
        writeManagedSharedClientConfiguration(properties, MANAGED_SHARED_ROOT, serverUrl, environment);
        verifyWritable(MANAGED_SHARED_ROOT);
        if (!isSharedClientConfig(MANAGED_SHARED_ROOT)) {
            throw new IOException("The managed Shared Client connection could not be verified after saving.");
        }
        writePointer(MANAGED_SHARED_ROOT);
        workspaceRoot = MANAGED_SHARED_ROOT;
    }


    /**
     * Validates a target for an explicit company-server -> LOCAL disaster recovery.
     * An empty/new folder is accepted, as is an existing LOCAL workspace. Shared-client
     * storage or unrelated non-empty folders are rejected to prevent accidental overwrite.
     */
    public static synchronized LocalRecoveryTargetInspection inspectLocalRecoveryTarget(Path selectedRoot) {
        if (selectedRoot == null) return new LocalRecoveryTargetInspection(false, null, false,
                "Choose a new folder or an existing LOCAL DSE ERP workspace.");
        Path root = selectedRoot.toAbsolutePath().normalize();
        if (root.equals(MANAGED_SHARED_ROOT)) return new LocalRecoveryTargetInspection(false, root, false,
                "The managed Shared Client folder cannot become a LOCAL company workspace.");
        try {
            if (Files.exists(root) && !Files.isDirectory(root)) {
                return new LocalRecoveryTargetInspection(false, root, false, "The selected path is not a folder.");
            }
            if (!Files.exists(root)) return new LocalRecoveryTargetInspection(true, root, false,
                    "A new LOCAL recovery workspace will be created here.");
            try (var entries = Files.list(root)) {
                if (entries.findAny().isEmpty()) return new LocalRecoveryTargetInspection(true, root, false,
                        "The empty folder can be used as a new LOCAL recovery workspace.");
            }
            Path config = root.resolve("Config").resolve("config.properties");
            if (!Files.isRegularFile(config)) return new LocalRecoveryTargetInspection(false, root, false,
                    "The folder is not empty and is not an existing DSE ERP LOCAL workspace.");
            Properties properties = readProperties(config);
            if (DeploymentMode.parse(properties.getProperty("deployment.mode", "LOCAL")) != DeploymentMode.LOCAL) {
                return new LocalRecoveryTargetInspection(false, root, false,
                        "Select an existing LOCAL workspace, not another Shared Client workspace.");
            }
            return new LocalRecoveryTargetInspection(true, root, true,
                    "Existing LOCAL workspace detected. Its current database/files will be preserved before recovery is applied.");
        } catch (IOException exception) {
            return new LocalRecoveryTargetInspection(false, root, false,
                    "The selected folder could not be inspected: " + exception.getMessage());
        }
    }

    /**
     * Finalizes a staged disaster-recovery target and moves the workstation pointer only
     * after the recovery package has already been verified and written to the target.
     */
    public static synchronized void activateLocalRecoveryTarget(Path selectedRoot, String sourceServer,
                                                                 String backupSource, String stagedAt) throws IOException {
        LocalRecoveryTargetInspection inspection = inspectLocalRecoveryTarget(selectedRoot);
        if (!inspection.valid()) throw new IllegalArgumentException(inspection.message());
        Path root = inspection.root();
        ensureStructure(root);
        verifyWritable(root);

        Path config = root.resolve("Config").resolve("config.properties");
        Properties properties = Files.isRegularFile(config) ? readProperties(config) : new Properties();
        if (Files.isRegularFile(config)) {
            Path archive = root.resolve("Backups").resolve("LocalRecovery");
            Files.createDirectories(archive);
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.copy(config, archive.resolve("ConfigBeforeRecovery-" + stamp + ".properties"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
        properties.remove("db.url");
        properties.remove("db.username");
        properties.remove("db.password");
        properties.remove("postgres.binPath");
        properties.remove("postgres.dataPath");
        properties.setProperty("deployment.mode", DeploymentMode.LOCAL.name());
        properties.setProperty("deployment.environment", "LOCAL");
        properties.setProperty("server.baseUrl", "");
        properties.setProperty("setup.completed", "true");
        properties.setProperty("app.version", org.example.update.BuildInfo.version());
        properties.setProperty("backup.restore.pending", "true");
        properties.setProperty("backup.restore.source", backupSource == null ? "Company server recovery package" : backupSource);
        properties.setProperty("backup.restore.staged_at", stagedAt == null ? java.time.Instant.now().toString() : stagedAt);
        properties.setProperty("recovery.files.pending", "true");
        properties.setProperty("recovery.database.applied", "false");
        properties.setProperty("recovery.source.server", sourceServer == null ? "" : sourceServer);
        properties.setProperty("recovery.created_at", stagedAt == null ? java.time.Instant.now().toString() : stagedAt);
        Files.createDirectories(config.getParent());
        try (OutputStream output = Files.newOutputStream(config, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "DSE ERP LOCAL disaster-recovery configuration");
        }
        // Persist the next-start pointer but keep this running Shared Client bound to its
        // current managed storage until Platform.exit() completes. This avoids any mixed runtime.
        writePointer(root);
    }

    public static synchronized boolean isManagedSharedClientWorkspace() {
        return isConfigured() && workspaceRoot.equals(MANAGED_SHARED_ROOT);
    }

    public static Path getManagedSharedClientRoot() {
        return MANAGED_SHARED_ROOT;
    }

    /**
     * Schedules a workspace copy for the next application start, before managed services open.
     * The original workspace is intentionally retained as an additional recovery copy.
     */
    public static synchronized void stageMove(Path targetRoot) throws IOException {
        Path source = getWorkspaceRoot();
        Path target = targetRoot.toAbsolutePath().normalize();
        if (source.equals(target)) throw new IllegalArgumentException("The selected folder is already the active workspace.");
        if (target.startsWith(source)) throw new IllegalArgumentException("The new workspace cannot be inside the current workspace.");
        Files.createDirectories(POINTER_FOLDER);
        Properties properties = new Properties();
        properties.setProperty("source.path", source.toString());
        properties.setProperty("target.path", target.toString());
        try (OutputStream output = Files.newOutputStream(PENDING_MOVE_FILE)) {
            properties.store(output, "DSE ERP pending workspace move");
        }
    }

    public static boolean hasPendingMove() {
        return Files.isRegularFile(PENDING_MOVE_FILE);
    }

    /**
     * Test-only transient workspace switch. This deliberately does NOT write the persistent
     * workspace pointer, so IntelliJ/JUnit evidence runs can never replace the user's real
     * DSE ERP workspace selection. Package-private: production controllers cannot call it.
     */
    static synchronized Path configureTransientForTesting(Path selectedRoot) throws IOException {
        if (selectedRoot == null) throw new IllegalArgumentException("Workspace folder is required.");
        Path previous = workspaceRoot;
        Path normalized = selectedRoot.toAbsolutePath().normalize();
        ensureStructure(normalized);
        verifyWritable(normalized);
        workspaceRoot = normalized;
        return previous;
    }

    /** Restores the in-memory workspace after a transient test scope; no pointer file is touched. */
    static synchronized void restoreTransientForTesting(Path previousRoot) {
        workspaceRoot = previousRoot;
    }

    public static Path getDatabaseFolder() { return getWorkspaceRoot().resolve("Database"); }
    public static Path getConfigurationFolder() { return getWorkspaceRoot().resolve("Config"); }
    public static Path getBackupFolder() { return getWorkspaceRoot().resolve("Backups"); }
    public static Path getReportsFolder() { return getWorkspaceRoot().resolve("Reports"); }
    public static Path getImportsFolder() { return getWorkspaceRoot().resolve("Imports"); }
    public static Path getExportsFolder() { return getWorkspaceRoot().resolve("Exports"); }
    public static Path getAttachmentsFolder() { return getWorkspaceRoot().resolve("Attachments"); }
    public static Path getTemplatesFolder() { return getWorkspaceRoot().resolve("Templates"); }
    public static Path getLogsFolder() { return getWorkspaceRoot().resolve("Logs"); }
    public static Path getDocumentsFolder() { return getWorkspaceRoot().resolve("Documents"); }
    public static Path getDesktopLogsFolder() { return getLogsFolder().resolve("Desktop"); }
    public static Path getServerLogsFolder() { return getLogsFolder().resolve("Server"); }
    public static Path getPostgresLogsFolder() { return getLogsFolder().resolve("PostgreSQL"); }
    public static Path getArchivedLogsFolder() { return getLogsFolder().resolve("Archive"); }
    public static Path getTempFolder() { return getWorkspaceRoot().resolve("Temp"); }
    public static Path getUpdatesFolder() { return getWorkspaceRoot().resolve("Updates"); }

    public static Path getPointerFolder() { return POINTER_FOLDER; }

    private static void ensureStructure(Path root) throws IOException {
        Files.createDirectories(root);
        for (String folder : new String[]{
                "Database", "Config", "Backups", "Reports", "Imports", "Exports",
                "Attachments", "Templates", "Logs", "Temp", "Updates", "Documents"
        }) {
            Files.createDirectories(root.resolve(folder));
        }
        for (String folder : new String[]{
                "Documents/Sales", "Documents/Purchase", "Documents/Quotations",
                "Documents/Customers", "Documents/Suppliers", "Documents/Bank", "Documents/General",
                "Reports/Sales", "Reports/Purchase", "Reports/Inventory", "Reports/Payments",
                "Reports/Bank", "Reports/GST-Tax", "Reports/Financial", "Reports/Scheduled",
                "Exports/Excel", "Exports/CSV", "Exports/PDF", "Exports/Diagnostics", "Exports/General",
                "Imports/Results", "Logs/Desktop", "Logs/Server", "Logs/PostgreSQL", "Logs/Archive"
        }) {
            Files.createDirectories(root.resolve(folder));
        }
    }

    private static void verifyWritable(Path root) throws IOException {
        Path probe = root.resolve(".dse-erp-write-test");
        Files.writeString(probe, "ok", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.deleteIfExists(probe);
    }

    private static void writePointer(Path root) throws IOException {
        Files.createDirectories(POINTER_FOLDER);
        Properties properties = new Properties();
        properties.setProperty(WORKSPACE_KEY, root.toString());
        try (OutputStream output = Files.newOutputStream(POINTER_FILE)) {
            properties.store(output, "DSE ERP workspace location");
        }
    }

    private static void applyPendingMoveIfPresent() throws IOException {
        if (!Files.isRegularFile(PENDING_MOVE_FILE)) return;
        Properties properties = readProperties(PENDING_MOVE_FILE);
        Path source = Path.of(properties.getProperty("source.path")).toAbsolutePath().normalize();
        Path target = Path.of(properties.getProperty("target.path")).toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            Files.deleteIfExists(PENDING_MOVE_FILE);
            throw new IOException("The current workspace no longer exists: " + source);
        }
        copyTree(source, target);
        ensureStructure(target);
        writePointer(target);
        Files.deleteIfExists(PENDING_MOVE_FILE);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }


    private static boolean isSharedClientConfig(Path root) throws IOException {
        Path config = root.resolve("Config").resolve("config.properties");
        if (!Files.isRegularFile(config)) return false;
        Properties properties = readProperties(config);
        return DeploymentMode.parse(properties.getProperty("deployment.mode", "LOCAL")) == DeploymentMode.SHARED_CLIENT;
    }

    private static void migrateSharedClientConfiguration(Path sourceRoot, Path targetRoot) throws IOException {
        Path sourceConfig = sourceRoot.resolve("Config").resolve("config.properties");
        if (!Files.isRegularFile(sourceConfig)) {
            throw new IOException("Shared-client configuration is missing: " + sourceConfig);
        }
        Properties properties = readProperties(sourceConfig);
        if (DeploymentMode.parse(properties.getProperty("deployment.mode", "LOCAL")) != DeploymentMode.SHARED_CLIENT) {
            throw new IOException("The selected workspace is not configured as a shared client.");
        }
        writeManagedSharedClientConfiguration(properties, targetRoot,
                properties.getProperty("server.baseUrl", ""),
                properties.getProperty("deployment.environment", "UAT"));
    }

    private static void createSharedClientConfigurationFromLocal(Path sourceRoot, Path targetRoot,
                                                                  String serverUrl, String environment) throws IOException {
        Path sourceConfig = sourceRoot.resolve("Config").resolve("config.properties");
        if (!Files.isRegularFile(sourceConfig)) {
            throw new IOException("Local workspace configuration is missing: " + sourceConfig);
        }
        Properties properties = readProperties(sourceConfig);
        if (DeploymentMode.parse(properties.getProperty("deployment.mode", "LOCAL")) != DeploymentMode.LOCAL) {
            throw new IOException("The current workspace is not configured as LOCAL.");
        }
        writeManagedSharedClientConfiguration(properties, targetRoot, serverUrl, environment);
    }

    private static void writeManagedSharedClientConfiguration(Properties source, Path targetRoot,
                                                               String serverUrl, String environment) throws IOException {
        String normalizedServer = serverUrl == null ? "" : serverUrl.trim();
        String normalizedEnvironment = environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
        if (normalizedServer.isBlank()) throw new IOException("Company server URL is required.");
        if (!"UAT".equals(normalizedEnvironment) && !"PROD".equals(normalizedEnvironment)) {
            throw new IOException("Shared-client environment must be UAT or PROD.");
        }

        ensureStructure(targetRoot);
        Properties properties = new Properties();
        properties.putAll(source);

        // Shared clients never own a local database or local mail-server secret. Keep the old
        // LOCAL workspace untouched; remove local-only runtime credentials from the new profile.
        properties.remove("db.url");
        properties.remove("db.username");
        properties.remove("db.password");
        properties.remove("postgres.binPath");
        properties.remove("postgres.dataPath");
        properties.remove("smtp.appPassword");

        properties.setProperty("deployment.mode", DeploymentMode.SHARED_CLIENT.name());
        properties.setProperty("deployment.environment", normalizedEnvironment);
        properties.setProperty("server.baseUrl", normalizedServer);
        properties.setProperty("runtime.postgres.mode", "external");
        properties.setProperty("update.channel", "UAT".equals(normalizedEnvironment) ? "BETA" : "STABLE");
        properties.setProperty("setup.completed", "true");
        // Creating/migrating a shared-client profile is a connection-state transition, not an
        // application update. Stamp the profile with the running build so UpdateLifecycle does
        // not show a misleading "Update completed" toast on the first shared-client login.
        properties.setProperty("app.version", org.example.update.BuildInfo.version());

        Path targetConfig = targetRoot.resolve("Config").resolve("config.properties");
        Files.createDirectories(targetConfig.getParent());
        try (OutputStream output = Files.newOutputStream(targetConfig, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "DSE ERP shared-client configuration");
        }
    }

    private static Properties readProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

    private static Path resolvePointerFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) return Path.of(appData, APP_NAME).toAbsolutePath().normalize();
        }
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", APP_NAME)
                    .toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".dse-erp").toAbsolutePath().normalize();
    }

    public record ExistingWorkspaceInspection(boolean valid, Path root, String message,
                                              boolean configPresent, boolean databasePresent,
                                              boolean postgresClusterPresent) {}
    public record LocalRecoveryTargetInspection(boolean valid, Path root, boolean existingLocal, String message) {}
}
