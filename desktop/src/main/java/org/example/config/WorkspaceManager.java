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

            // A missing pointer always means first-run selection. Never silently adopt
            // an old AppData/.dse-erp folder, because it may contain a zero-user database.
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

    private static Path detectExistingWorkspace() {
        Path platform = POINTER_FOLDER;
        if (containsExistingConfig(platform)) return platform;
        Path hidden = Path.of(System.getProperty("user.home"), ".dse-erp").toAbsolutePath().normalize();
        if (containsExistingConfig(hidden)) return hidden;
        return null;
    }

    private static boolean containsExistingConfig(Path folder) {
        return Files.isDirectory(folder) && (
                Files.isRegularFile(folder.resolve("config.properties"))
                        || Files.isRegularFile(folder.resolve("Config").resolve("config.properties"))
        );
    }

    private static void organizeExistingConfig(Path root) throws IOException {
        ensureStructure(root);
        Path oldConfig = root.resolve("config.properties");
        Path newConfig = root.resolve("Config").resolve("config.properties");
        if (Files.isRegularFile(oldConfig) && !Files.exists(newConfig)) {
            Files.move(oldConfig, newConfig, StandardCopyOption.REPLACE_EXISTING);
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
}
