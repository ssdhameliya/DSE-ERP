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
                        workspaceRoot = candidate;
                        ensureStructure(candidate);
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

    public static Path getDatabaseFolder() { return getWorkspaceRoot().resolve("Database"); }
    public static Path getConfigurationFolder() { return getWorkspaceRoot().resolve("Config"); }
    public static Path getBackupFolder() { return getWorkspaceRoot().resolve("Backups"); }
    public static Path getReportsFolder() { return getWorkspaceRoot().resolve("Reports"); }
    public static Path getImportsFolder() { return getWorkspaceRoot().resolve("Imports"); }
    public static Path getExportsFolder() { return getWorkspaceRoot().resolve("Exports"); }
    public static Path getAttachmentsFolder() { return getWorkspaceRoot().resolve("Attachments"); }
    public static Path getTemplatesFolder() { return getWorkspaceRoot().resolve("Templates"); }
    public static Path getLogsFolder() { return getWorkspaceRoot().resolve("Logs"); }
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
}
