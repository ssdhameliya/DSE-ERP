package org.example.backup;

import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Explicit company-server -> LOCAL disaster recovery.
 *
 * <p>This is intentionally not an automatic offline fallback. An administrator first downloads
 * a fresh, server-created recovery package while all company users are stopped, stages it into a
 * dedicated LOCAL workspace, and restarts DSE ERP. PostgreSQL is restored before any normal local
 * Spring activity starts; server-owned business files are applied only after that database restore
 * succeeds.</p>
 */
public final class LocalRecoveryManager {
    private static final String FILES_PENDING = "recovery.files.pending";
    private static final String DATABASE_APPLIED = "recovery.database.applied";
    private static final String DATABASE_FAILED = "recovery.database.failed";
    private static final String STAGED_FILES = "local-recovery-files";
    private static final String STAGED_MANIFEST = "local-recovery-manifest.properties";
    private static final List<String> FILE_ROOTS = List.of("Attachments", "Documents", "Templates");

    private LocalRecoveryManager() {}

    public static StageResult stageForLocal(Path packageFile, Path targetRoot, String sourceServer) throws Exception {
        if (packageFile == null || !Files.isRegularFile(packageFile)) {
            throw new IllegalArgumentException("The downloaded recovery package is missing.");
        }
        WorkspaceManager.LocalRecoveryTargetInspection inspection = WorkspaceManager.inspectLocalRecoveryTarget(targetRoot);
        if (!inspection.valid()) throw new IllegalArgumentException(inspection.message());

        Path extract = Files.createTempDirectory("dse-local-recovery-");
        try {
            Properties manifest = extractAndVerify(packageFile, extract);
            String createdAt = manifest.getProperty("createdAt", Instant.now().toString());
            String environment = manifest.getProperty("environment", "UNKNOWN").trim().toUpperCase(Locale.ROOT);
            String version = manifest.getProperty("application.version", "UNKNOWN").trim();
            String databaseName = manifest.getProperty("database.name", "").trim();
            if (!("UAT".equals(environment) || "PROD".equals(environment))) {
                throw new IllegalStateException("Recovery package environment is not UAT/PROD: " + environment);
            }

            Path target = inspection.root();
            Files.createDirectories(target.resolve("Temp"));
            Files.createDirectories(target.resolve("Backups").resolve("LocalRecovery"));

            Path pendingDatabase = target.resolve("Temp").resolve("restore-pending.pgbackup");
            Files.copy(extract.resolve("database.pgbackup"), pendingDatabase, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

            Path stagedFiles = target.resolve("Temp").resolve(STAGED_FILES);
            deleteTree(stagedFiles);
            Files.createDirectories(stagedFiles);
            Path extractedWorkspace = extract.resolve("workspace");
            for (String rootName : FILE_ROOTS) {
                Path source = extractedWorkspace.resolve(rootName);
                if (Files.isDirectory(source)) copyTree(source, stagedFiles.resolve(rootName));
                else Files.createDirectories(stagedFiles.resolve(rootName));
            }

            try (OutputStream out = Files.newOutputStream(target.resolve("Temp").resolve(STAGED_MANIFEST),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                manifest.store(out, "DSE ERP staged LOCAL recovery manifest");
            }

            String safeName = packageFile.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
            Path retainedPackage = target.resolve("Backups").resolve("LocalRecovery").resolve(safeName);
            Files.copy(packageFile, retainedPackage, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

            WorkspaceManager.activateLocalRecoveryTarget(target, sourceServer,
                    retainedPackage.toAbsolutePath().toString(), createdAt);
            return new StageResult(target, retainedPackage, inspection.existingLocal(), environment, version, databaseName, createdAt);
        } finally {
            deleteTree(extract);
        }
    }

    public static void markDatabaseRestoreSuccess() {
        if (!"true".equalsIgnoreCase(ConfigManager.get(FILES_PENDING, "false"))) return;
        ConfigManager.set(DATABASE_APPLIED, "true");
        ConfigManager.set(DATABASE_FAILED, "false");
    }

    public static void markDatabaseRestoreFailure() {
        if (!"true".equalsIgnoreCase(ConfigManager.get(FILES_PENDING, "false"))) return;
        ConfigManager.set(DATABASE_APPLIED, "false");
        ConfigManager.set(DATABASE_FAILED, "true");
    }

    public static FileApplyResult applyPendingFilesIfReady() {
        if (!"true".equalsIgnoreCase(ConfigManager.get(FILES_PENDING, "false"))) return FileApplyResult.none();
        if (!"true".equalsIgnoreCase(ConfigManager.get(DATABASE_APPLIED, "false"))) return FileApplyResult.none();

        Path workspace = WorkspaceManager.getWorkspaceRoot();
        Path staged = workspace.resolve("Temp").resolve(STAGED_FILES);
        if (!Files.isDirectory(staged)) {
            return FileApplyResult.failed("The LOCAL recovery database was restored, but staged business files are missing.", null);
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path archive = workspace.resolve("Backups").resolve("LocalRecovery").resolve("BeforeFiles-" + stamp);
        try {
            Files.createDirectories(archive);
            for (String name : FILE_ROOTS) {
                Path current = workspace.resolve(name);
                if (Files.exists(current)) copyTree(current, archive.resolve(name));
            }

            try {
                for (String name : FILE_ROOTS) {
                    Path destination = workspace.resolve(name);
                    deleteTree(destination);
                    Files.createDirectories(destination);
                    Path source = staged.resolve(name);
                    if (Files.isDirectory(source)) copyTree(source, destination);
                }
            } catch (Exception applyFailure) {
                for (String name : FILE_ROOTS) {
                    Path destination = workspace.resolve(name);
                    Path prior = archive.resolve(name);
                    deleteTree(destination);
                    if (Files.exists(prior)) copyTree(prior, destination); else Files.createDirectories(destination);
                }
                throw applyFailure;
            }

            deleteTree(staged);
            Files.deleteIfExists(workspace.resolve("Temp").resolve(STAGED_MANIFEST));
            ConfigManager.remove(FILES_PENDING);
            ConfigManager.remove(DATABASE_APPLIED);
            ConfigManager.remove(DATABASE_FAILED);
            ConfigManager.remove("recovery.source.server");
            ConfigManager.remove("recovery.created_at");
            return FileApplyResult.applied(archive);
        } catch (Exception failure) {
            return FileApplyResult.failed("The LOCAL recovery database was restored, but business files could not be applied. The pre-recovery files remain preserved.", failure);
        }
    }

    static Properties extractAndVerify(Path packageFile, Path extractRoot) throws Exception {
        Files.createDirectories(extractRoot);
        try (ZipFile zip = new ZipFile(packageFile.toFile())) {
            ZipEntry manifestEntry = zip.getEntry("manifest.properties");
            ZipEntry databaseEntry = zip.getEntry("database.pgbackup");
            if (manifestEntry == null || databaseEntry == null) {
                throw new IllegalStateException("Recovery package is missing manifest.properties or database.pgbackup.");
            }
            Properties manifest = new Properties();
            try (InputStream in = zip.getInputStream(manifestEntry)) { manifest.load(in); }
            if (!"1".equals(manifest.getProperty("format.version"))) {
                throw new IllegalStateException("Unsupported recovery package format.");
            }

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.isBlank() || name.startsWith("/") || name.matches("(?i)^[a-z]:.*")) {
                    throw new IllegalStateException("Unsafe recovery package path: " + name);
                }
                for (String segment : name.split("/", -1)) {
                    if (segment.equals(".") || segment.equals("..")) {
                        throw new IllegalStateException("Unsafe recovery package path: " + name);
                    }
                }
                boolean businessFile = FILE_ROOTS.stream().anyMatch(root ->
                        name.equals("workspace/" + root) || name.startsWith("workspace/" + root + "/"));
                if (!(name.equals("manifest.properties") || name.equals("database.pgbackup")
                        || name.equals("workspace/") || businessFile)) {
                    throw new IllegalStateException("Unexpected recovery package entry: " + name);
                }
                Path normalizedRoot = extractRoot.toAbsolutePath().normalize();
                Path target = normalizedRoot.resolve(name).normalize();
                if (!target.startsWith(normalizedRoot)) throw new IllegalStateException("Unsafe recovery package path: " + name);
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            String expected = manifest.getProperty("database.sha256", "").trim().toLowerCase(Locale.ROOT);
            if (expected.isBlank()) throw new IllegalStateException("Recovery package database checksum is missing.");
            String actual = sha256(extractRoot.resolve("database.pgbackup"));
            if (!expected.equals(actual)) throw new IllegalStateException("Recovery package database checksum verification failed.");
            return manifest;
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
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

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public record StageResult(Path workspace, Path retainedPackage, boolean existingLocal,
                              String sourceEnvironment, String sourceVersion, String databaseName, String createdAt) {}

    public record FileApplyResult(boolean attempted, boolean applied, String message, Path preservedFiles, Throwable failure) {
        public static FileApplyResult none() { return new FileApplyResult(false, false, "No LOCAL recovery files are pending.", null, null); }
        public static FileApplyResult applied(Path archive) { return new FileApplyResult(true, true, "LOCAL recovery business files applied.", archive, null); }
        public static FileApplyResult failed(String message, Throwable failure) { return new FileApplyResult(true, false, message, null, failure); }
    }
}
