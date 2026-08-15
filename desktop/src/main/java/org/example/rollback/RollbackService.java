package org.example.rollback;

import org.example.backup.BackupManager;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.update.BuildInfo;
import org.example.update.ChecksumVerifier;
import org.example.update.GitHubReleaseClient;
import org.example.update.PlatformPackage;
import org.example.update.SemanticVersion;
import org.example.update.UpdateHistoryStore;
import org.example.update.UpdateInstallerLauncher;
import org.example.update.UpdateRelease;
import org.example.update.UpdateService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Production-safe application rollback coordinator.
 *
 * <p>Rollback intentionally replaces only the application package. It never
 * restores an older database automatically. A verified PostgreSQL safety backup
 * plus a Config/Templates workspace snapshot are created before the installer is
 * launched. Database restore remains a separate explicit Backup & Restore action.</p>
 */
public final class RollbackService {
    private static final Pattern VERSION = Pattern.compile("(?<!\\d)(\\d+\\.\\d+\\.\\d+)(?!\\d)");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());

    /* Versions already shipped on the unchanged schema generation used by 7.3.0. */
    private static final Map<String, Integer> KNOWN_SCHEMA = Map.of(
            "7.2.2", 1,
            "7.2.4", 1,
            "7.2.5", 1,
            "7.2.6", 1,
            "7.2.7", 1,
            "7.3.0", 1
    );

    private final UpdateService updateService = new UpdateService();
    private final GitHubReleaseClient releaseClient = new GitHubReleaseClient();

    public Path rootFolder() {
        return WorkspaceManager.getUpdatesFolder().resolve("Rollback");
    }

    public Path packagesFolder() {
        return rootFolder().resolve("Packages");
    }

    public Path recoveryFolder() {
        return rootFolder().resolve("RecoveryPoints");
    }

    public Path auditFile() {
        return rootFolder().resolve("rollback-history.tsv");
    }

    public void ensureFolders() throws IOException {
        Files.createDirectories(packagesFolder());
        Files.createDirectories(recoveryFolder());
    }

    /**
     * Finds retained installers that are older than the running application.
     * The regular Updates directory is also scanned so packages downloaded by
     * earlier updater versions can immediately participate in rollback.
     */
    public List<Candidate> candidates() {
        try {
            ensureFolders();
            Map<String, Path> unique = new LinkedHashMap<>();
            collectInstallers(packagesFolder(), unique);
            collectInstallers(WorkspaceManager.getUpdatesFolder(), unique);

            SemanticVersion current = SemanticVersion.parse(BuildInfo.version());
            List<Candidate> result = new ArrayList<>();
            for (Path path : unique.values()) {
                String version = versionFrom(path).orElse("");
                if (version.isBlank()) continue;
                if (SemanticVersion.parse(version).compareTo(current) >= 0) continue;
                int schema = targetSchema(path, version);
                Compatibility compatibility = compatibilityFor(schema);
                result.add(new Candidate(version, path, schema, compatibility,
                        Files.isRegularFile(path) ? safeSize(path) : 0L,
                        readPackageManifest(path).getProperty("sha256", "")));
            }
            result.sort(Comparator.comparing((Candidate c) -> SemanticVersion.parse(c.version())).reversed());
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public Candidate importPackage(Path source) throws Exception {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Select a valid DSE ERP installer package.");
        }
        if (!isSupportedInstaller(source)) {
            throw new IllegalArgumentException("Supported rollback packages are EXE/MSI on Windows and DMG/PKG on macOS.");
        }
        String version = versionFrom(source).orElseThrow(() ->
                new IllegalArgumentException("The installer filename must contain a version such as 7.2.2."));
        if (SemanticVersion.parse(version).compareTo(SemanticVersion.parse(BuildInfo.version())) >= 0) {
            throw new IllegalArgumentException("Rollback requires an older version than DSE ERP " + BuildInfo.version() + ".");
        }
        ensureFolders();
        Path target = packagesFolder().resolve(source.getFileName().toString());
        if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
        int schema = knownSchema(version);
        writePackageManifest(target, version, schema, "IMPORTED");
        appendAudit("PACKAGE_IMPORTED", version, "SUCCESS", target.toString());
        return candidateFor(target, version);
    }

    /** Downloads and verifies a specific published GitHub release for the current platform. */
    public Candidate downloadPublishedVersion(String requestedVersion, DoubleConsumer progress) throws Exception {
        String version = normalizeVersion(requestedVersion);
        if (version.isBlank()) throw new IllegalArgumentException("Enter a version such as 7.2.2.");
        if (SemanticVersion.parse(version).compareTo(SemanticVersion.parse(BuildInfo.version())) >= 0) {
            throw new IllegalArgumentException("Choose a version older than " + BuildInfo.version() + ".");
        }
        String owner = ConfigManager.get("update.github.owner", UpdateService.DEFAULT_GITHUB_OWNER).trim();
        String repo = ConfigManager.get("update.github.repository", UpdateService.DEFAULT_GITHUB_REPOSITORY).trim();
        UpdateRelease release = releaseClient.byVersion(owner, repo, version);
        UpdateRelease.Asset asset = PlatformPackage.select(release).orElseThrow(() ->
                new IllegalStateException("DSE ERP " + version + " does not contain an installer for " + PlatformPackage.current() + "."));
        Path downloaded = updateService.download(asset, progress == null ? ignored -> { } : progress);
        String expected = updateService.expectedChecksum(release, asset.name());
        if (expected.isBlank()) {
            throw new SecurityException("Release " + version + " has no SHA-256 checksum for " + asset.name() + ". Rollback download was not trusted.");
        }
        ChecksumVerifier.verify(downloaded, expected);
        ensureFolders();
        Path retained = packagesFolder().resolve(downloaded.getFileName().toString());
        Files.copy(downloaded, retained, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        int schema = knownSchema(version);
        writePackageManifest(retained, version, schema, "GITHUB_VERIFIED");
        appendAudit("PACKAGE_DOWNLOADED", version, "SUCCESS", retained.toString());
        return candidateFor(retained, version);
    }

    public Preparation prepareRollback(Candidate candidate) throws Exception {
        Objects.requireNonNull(candidate, "candidate");
        if (!Files.isRegularFile(candidate.installer())) {
            throw new IllegalStateException("The selected rollback installer is no longer available.");
        }
        Candidate refreshed = candidateFor(candidate.installer(), candidate.version());
        if (!refreshed.compatibility().safe()) {
            throw new IllegalStateException(refreshed.compatibility().message());
        }

        ensureFolders();
        String id = "RB-" + STAMP.format(Instant.now()) + "-" + BuildInfo.version() + "-to-" + candidate.version();
        Path point = recoveryFolder().resolve(id);
        Files.createDirectories(point);

        Path dbBackup;
        try {
            dbBackup = BackupManager.createBackup(
                    "Before-Rollback-" + safe(BuildInfo.version()) + "-to-" + safe(candidate.version()),
                    "ROLLBACK_SAFETY");
        } catch (Exception failure) {
            deleteTreeQuietly(point);
            appendAudit("ROLLBACK_PREPARE", candidate.version(), "FAILED", rootMessage(failure));
            throw failure;
        }

        Path workspaceSnapshot = point.resolve("workspace-config-templates.zip");
        snapshotWorkspace(workspaceSnapshot);
        Path installerCopy = packagesFolder().resolve(candidate.installer().getFileName());
        if (!candidate.installer().toAbsolutePath().normalize().equals(installerCopy.toAbsolutePath().normalize())) {
            Files.copy(candidate.installer(), installerCopy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }

        Properties manifest = new Properties();
        manifest.setProperty("rollback.id", id);
        manifest.setProperty("createdAt", Instant.now().toString());
        manifest.setProperty("fromVersion", BuildInfo.version());
        manifest.setProperty("toVersion", candidate.version());
        manifest.setProperty("currentDatabaseSchema", Integer.toString(BuildInfo.databaseMigrationVersion()));
        manifest.setProperty("targetDatabaseSchema", Integer.toString(candidate.databaseSchema()));
        manifest.setProperty("databasePolicy", "PRESERVE_CURRENT_DATABASE");
        manifest.setProperty("databaseSafetyBackup", dbBackup.toAbsolutePath().toString());
        manifest.setProperty("workspaceSnapshot", workspaceSnapshot.toAbsolutePath().toString());
        manifest.setProperty("attachmentsPolicy", "PRESERVE_IN_PLACE");
        manifest.setProperty("documentsPolicy", "PRESERVE_IN_PLACE");
        manifest.setProperty("installer", installerCopy.toAbsolutePath().toString());
        manifest.setProperty("installerSha256", checksumQuietly(installerCopy));
        manifest.setProperty("status", "READY");
        try (OutputStream output = Files.newOutputStream(point.resolve("rollback.properties"))) {
            manifest.store(output, "DSE ERP safe rollback recovery point");
        }

        appendAudit("ROLLBACK_PREPARE", candidate.version(), "READY",
                "Recovery=" + id + "; DB=" + dbBackup.getFileName());
        UpdateHistoryStore.append(candidate.version(), "ROLLBACK", "READY",
                "Preserve current database; Recovery=" + id + "; Backup=" + dbBackup.getFileName());
        return new Preparation(id, candidate.version(), installerCopy, dbBackup, workspaceSnapshot, point);
    }

    public UpdateInstallerLauncher.LaunchResult launch(Preparation preparation) throws Exception {
        Objects.requireNonNull(preparation, "preparation");
        UpdateInstallerLauncher.LaunchResult result = UpdateInstallerLauncher.launch(
                preparation.installer(), preparation.targetVersion());
        appendAudit("ROLLBACK_INSTALLER", preparation.targetVersion(), "STARTED",
                "Recovery=" + preparation.id() + "; Helper=" + result.helper());
        UpdateHistoryStore.append(preparation.targetVersion(), "ROLLBACK", "INSTALLER_STARTED",
                "Recovery=" + preparation.id() + "; Database preserved");
        return result;
    }

    public List<HistoryEntry> history() {
        Path file = auditFile();
        if (!Files.isRegularFile(file)) return List.of();
        try {
            List<HistoryEntry> rows = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] p = line.split("\\t", 5);
                if (p.length < 4) continue;
                rows.add(new HistoryEntry(Instant.parse(p[0]), p[1], p[2], p[3], p.length == 5 ? p[4] : ""));
            }
            Collections.reverse(rows);
            return rows;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public Optional<Path> latestRecoveryPoint() {
        if (!Files.isDirectory(recoveryFolder())) return Optional.empty();
        try (Stream<Path> stream = Files.list(recoveryFolder())) {
            return stream.filter(Files::isDirectory)
                    .max(Comparator.comparing(RollbackService::modified));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    public Compatibility compatibilityFor(int targetSchema) {
        int current = BuildInfo.databaseMigrationVersion();
        int min = BuildInfo.databaseMinCompatibleVersion();
        int max = BuildInfo.databaseMaxCompatibleVersion();
        if (targetSchema <= 0) {
            return new Compatibility(false, "Unknown", "Database compatibility metadata is not available for this package.");
        }
        if (targetSchema < min || targetSchema > max || targetSchema != current) {
            return new Compatibility(false, "Blocked",
                    "This installer expects database schema " + targetSchema + " while the current database is schema " + current + ". Use Full Recovery instead of application-only rollback.");
        }
        return new Compatibility(true, "Safe", "Application rollback can preserve the current database.");
    }

    private Candidate candidateFor(Path path, String version) {
        int schema = targetSchema(path, version);
        return new Candidate(version, path, schema, compatibilityFor(schema), safeSize(path),
                readPackageManifest(path).getProperty("sha256", ""));
    }

    private int targetSchema(Path installer, String version) {
        Properties manifest = readPackageManifest(installer);
        String configured = manifest.getProperty("databaseSchema", "").trim();
        if (!configured.isBlank()) {
            try { return Integer.parseInt(configured); } catch (NumberFormatException ignored) { }
        }
        if (wasVerifiedByUpdater(installer)) return knownSchema(version);
        return -1;
    }

    private boolean wasVerifiedByUpdater(Path installer) {
        String fileName = installer == null ? "" : installer.getFileName().toString();
        if (fileName.isBlank()) return false;
        return UpdateHistoryStore.read().stream().anyMatch(entry ->
                ("READY".equalsIgnoreCase(entry.result()) || "INSTALLER_STARTED".equalsIgnoreCase(entry.result()))
                        && entry.detail() != null && entry.detail().contains(fileName));
    }

    private int knownSchema(String version) {
        return KNOWN_SCHEMA.getOrDefault(version, -1);
    }

    private void writePackageManifest(Path installer, String version, int schema, String source) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", version);
        properties.setProperty("databaseSchema", Integer.toString(schema));
        properties.setProperty("source", source);
        properties.setProperty("capturedAt", Instant.now().toString());
        properties.setProperty("sha256", checksumQuietly(installer));
        try (OutputStream output = Files.newOutputStream(manifestPath(installer))) {
            properties.store(output, "DSE ERP retained rollback package");
        }
    }

    private Properties readPackageManifest(Path installer) {
        Properties result = new Properties();
        Path manifest = manifestPath(installer);
        if (!Files.isRegularFile(manifest)) return result;
        try (InputStream input = Files.newInputStream(manifest)) {
            result.load(input);
        } catch (IOException ignored) { }
        return result;
    }

    private static Path manifestPath(Path installer) {
        return installer.resolveSibling(installer.getFileName().toString() + ".rollback.properties");
    }

    private void collectInstallers(Path folder, Map<String, Path> result) throws IOException {
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> stream = Files.list(folder)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                if (!isSupportedInstaller(path)) continue;
                String key = path.getFileName().toString().toLowerCase(Locale.ROOT);
                result.putIfAbsent(key, path);
            }
        }
    }

    private static boolean isSupportedInstaller(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        PlatformPackage.Platform platform = PlatformPackage.current();
        return switch (platform) {
            case WINDOWS -> name.endsWith(".exe") || name.endsWith(".msi");
            case MACOS_X64, MACOS_ARM64 -> name.endsWith(".dmg") || name.endsWith(".pkg");
            default -> false;
        };
    }

    public static Optional<String> versionFrom(Path path) {
        if (path == null) return Optional.empty();
        Matcher matcher = VERSION.matcher(path.getFileName().toString());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private void snapshotWorkspace(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            addTree(zip, WorkspaceManager.getConfigurationFolder(), "Config");
            addTree(zip, WorkspaceManager.getTemplatesFolder(), "Templates");
            Properties note = new Properties();
            note.setProperty("createdAt", Instant.now().toString());
            note.setProperty("workspace", WorkspaceManager.getWorkspaceRoot().toString());
            note.setProperty("database", "Preserved in-place; verified pg_dump safety backup created separately");
            note.setProperty("attachments", "Preserved in-place; application-only rollback never modifies this folder");
            note.setProperty("documents", "Preserved in-place; application-only rollback never modifies this folder");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            note.store(out, "DSE ERP rollback snapshot manifest");
            zip.putNextEntry(new ZipEntry("snapshot.properties"));
            zip.write(out.toByteArray());
            zip.closeEntry();
        }
    }

    private static void addTree(ZipOutputStream zip, Path source, String prefix) throws IOException {
        if (!Files.exists(source)) return;
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String relative = source.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(prefix + "/" + relative));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    private synchronized void appendAudit(String action, String targetVersion, String result, String detail) {
        try {
            ensureFolders();
            String clean = Objects.requireNonNullElse(detail, "").replace('\t', ' ').replace('\n', ' ');
            String line = Instant.now() + "\t" + action + "\t" + targetVersion + "\t" + result + "\t" + clean + System.lineSeparator();
            Files.writeString(auditFile(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    private static long safeSize(Path path) {
        try { return Files.size(path); } catch (IOException ignored) { return 0L; }
    }

    private static String checksumQuietly(Path path) {
        if (path == null || !Files.isRegularFile(path)) return "";
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[131072];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeVersion(String value) {
        String clean = Objects.requireNonNullElse(value, "").trim();
        if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
        Matcher matcher = VERSION.matcher(clean);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String safe(String value) {
        return Objects.requireNonNullElse(value, "unknown").replaceAll("[^0-9A-Za-z._-]", "-");
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "Unknown error";
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static FileTime modified(Path path) {
        try { return Files.getLastModifiedTime(path); } catch (IOException ignored) { return FileTime.fromMillis(0); }
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (Exception ignored) { }
    }

    public record Candidate(String version, Path installer, int databaseSchema,
                            Compatibility compatibility, long sizeBytes, String sha256) { }

    public record Compatibility(boolean safe, String label, String message) { }

    public record Preparation(String id, String targetVersion, Path installer,
                              Path databaseBackup, Path workspaceSnapshot, Path recoveryPoint) { }

    public record HistoryEntry(Instant timestamp, String action, String targetVersion,
                               String result, String detail) { }
}
