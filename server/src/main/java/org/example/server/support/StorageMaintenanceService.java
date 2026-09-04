package org.example.server.support;

import jakarta.annotation.PostConstruct;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Server-owned workspace retention. This service NEVER targets Documents,
 * Attachments, Backups or Database. Only operational files are eligible.
 */
@Service
@Transactional
public class StorageMaintenanceService {
    private final JpaNativeRepository jdbc;
    private final Path workspace;

    public StorageMaintenanceService(JpaNativeRepository jdbc, @Value("${dse.workspace.path:}") String workspacePath) {
        this.jdbc = jdbc;
        this.workspace = workspacePath == null || workspacePath.isBlank()
                ? null : Path.of(workspacePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initializeFolders() {
        if (workspace == null) return;
        try { ensureStructure(); } catch (IOException ignored) { }
    }

    public StorageDtos.Status status() {
        CurrentUser.require();
        if (workspace == null) throw new IllegalStateException("Server workspace path is not configured");
        StorageDtos.Policy p = policy();
        long documents = size(workspace.resolve("Documents"));
        long attachments = size(workspace.resolve("Attachments"));
        long reports = size(workspace.resolve("Reports"));
        long exports = size(workspace.resolve("Exports"));
        long logs = size(workspace.resolve("Logs"));
        long backups = size(workspace.resolve("Backups"));
        long temp = size(workspace.resolve("Temp"));
        long total = documents + attachments + reports + exports + logs + backups + temp;
        return new StorageDtos.Status(workspace.toString(), documents, attachments, reports, exports, logs,
                backups, temp, total, setting("storage.cleanup.lastAt", ""),
                setting("storage.cleanup.lastSummary", "Never run"), p);
    }

    public StorageDtos.Policy policy() {
        return new StorageDtos.Policy(
                boundedInt("storage.logs.retentionDays", 30, 1, 3650),
                boundedInt("storage.reports.retentionDays", 365, 1, 3650),
                boundedInt("storage.exports.retentionDays", 90, 1, 3650),
                boundedInt("storage.diagnostics.retentionDays", 30, 1, 3650),
                boundedInt("storage.importResults.retentionDays", 90, 1, 3650),
                boundedInt("storage.temp.retentionDays", 7, 1, 365),
                Boolean.parseBoolean(setting("storage.logs.compress", "true")));
    }

    public StorageDtos.CleanupResult cleanup(boolean dryRun) {
        requireAdmin();
        if (workspace == null) throw new IllegalStateException("Server workspace path is not configured");
        try { ensureStructure(); } catch (IOException e) { throw new IllegalStateException("Unable to prepare workspace storage", e); }
        StorageDtos.Policy p = policy();
        Counter counter = new Counter();

        // Operational files only. Business evidence is deliberately excluded.
        cleanupTree(workspace.resolve("Reports"), p.reportRetentionDays(), dryRun, counter, false);
        cleanupTree(workspace.resolve("Exports"), p.exportRetentionDays(), dryRun, counter, true);
        cleanupTree(workspace.resolve("Imports/Results"), p.importResultRetentionDays(), dryRun, counter, false);
        cleanupTree(workspace.resolve("Temp"), p.tempRetentionDays(), dryRun, counter, false);
        cleanupLogs(workspace.resolve("Logs"), p.logRetentionDays(), p.compressLogs(), dryRun, counter);

        String when = BusinessClock.nowUtcText();
        String summary = "Deleted " + counter.deleted + " files; compressed " + counter.compressed
                + "; reclaimed " + counter.reclaimed + " bytes" + (dryRun ? " (preview)" : "");
        if (!dryRun) {
            putSetting("storage.cleanup.lastAt", when);
            putSetting("storage.cleanup.lastSummary", summary);
        }
        return new StorageDtos.CleanupResult(dryRun, counter.deleted, counter.compressed, counter.reclaimed, when, summary);
    }

    /** Daily server-owned cleanup; failure never prevents normal ERP operation. */
    @Scheduled(initialDelayString = "${dse.storage.cleanup-initial-delay-ms:120000}",
               fixedDelayString = "${dse.storage.cleanup-delay-ms:86400000}")
    public void scheduledCleanup() {
        if (workspace == null) return;
        try {
            // Scheduled runs have no request CurrentUser, so perform the same safe policy directly.
            StorageDtos.Policy p = policy(); Counter counter = new Counter(); ensureStructure();
            cleanupTree(workspace.resolve("Reports"), p.reportRetentionDays(), false, counter, false);
            cleanupTree(workspace.resolve("Exports"), p.exportRetentionDays(), false, counter, true);
            cleanupTree(workspace.resolve("Imports/Results"), p.importResultRetentionDays(), false, counter, false);
            cleanupTree(workspace.resolve("Temp"), p.tempRetentionDays(), false, counter, false);
            cleanupLogs(workspace.resolve("Logs"), p.logRetentionDays(), p.compressLogs(), false, counter);
            String summary = "Deleted " + counter.deleted + " files; compressed " + counter.compressed
                    + "; reclaimed " + counter.reclaimed + " bytes";
            putSetting("storage.cleanup.lastAt", BusinessClock.nowUtcText());
            putSetting("storage.cleanup.lastSummary", summary);
        } catch (Exception ignored) { }
    }

    private void cleanupTree(Path root, int days, boolean dryRun, Counter counter, boolean diagnosticsAware) {
        if (!insideWorkspace(root) || !Files.isDirectory(root)) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink() || !attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    int effectiveDays = diagnosticsAware && file.normalize().startsWith(workspace.resolve("Exports/Diagnostics").normalize())
                            ? boundedInt("storage.diagnostics.retentionDays", 30, 1, 3650) : days;
                    Instant effectiveCutoff = Instant.now().minus(Duration.ofDays(effectiveDays));
                    if (attrs.lastModifiedTime().toInstant().isBefore(effectiveCutoff)) delete(file, attrs.size(), dryRun, counter);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dryRun && !dir.equals(root)) {
                        try (var stream = Files.list(dir)) { if (stream.findAny().isEmpty()) Files.deleteIfExists(dir); }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) { }
    }

    private void cleanupLogs(Path root, int retentionDays, boolean compress, boolean dryRun, Counter counter) {
        if (!insideWorkspace(root) || !Files.isDirectory(root)) return;
        Instant deleteCutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        Instant compressCutoff = Instant.now().minus(Duration.ofDays(1));
        List<Path> active = List.of(
                root.resolve("Desktop/desktop.log").normalize(),
                root.resolve("Server/dse-erp-server.log").normalize(),
                root.resolve("PostgreSQL/postgresql.log").normalize());
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink() || !attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    Path normalized = file.toAbsolutePath().normalize();
                    if (active.contains(normalized)) return FileVisitResult.CONTINUE;
                    Instant modified = attrs.lastModifiedTime().toInstant();
                    if (modified.isBefore(deleteCutoff)) {
                        delete(file, attrs.size(), dryRun, counter);
                    } else if (compress && modified.isBefore(compressCutoff) && !file.getFileName().toString().endsWith(".gz")) {
                        compress(file, dryRun, counter);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) { }
    }

    private void compress(Path file, boolean dryRun, Counter counter) throws IOException {
        long before = Files.size(file);
        if (dryRun) { counter.compressed++; return; }
        Path target = file.resolveSibling(file.getFileName() + ".gz");
        if (Files.exists(target)) return;
        try (InputStream in = Files.newInputStream(file); OutputStream raw = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW);
             GZIPOutputStream out = new GZIPOutputStream(raw)) { in.transferTo(out); }
        long after = Files.size(target);
        Files.deleteIfExists(file);
        counter.compressed++;
        counter.reclaimed += Math.max(0, before - after);
    }

    private void delete(Path file, long bytes, boolean dryRun, Counter counter) throws IOException {
        counter.deleted++;
        counter.reclaimed += Math.max(0, bytes);
        if (!dryRun) Files.deleteIfExists(file);
    }

    private void ensureStructure() throws IOException {
        for (String path : List.of(
                "Documents/Sales", "Documents/Purchase", "Documents/Quotations", "Documents/Customers",
                "Documents/Suppliers", "Documents/Bank", "Documents/General",
                "Reports/Sales", "Reports/Purchase", "Reports/Inventory", "Reports/Payments", "Reports/Bank",
                "Reports/GST-Tax", "Reports/Financial", "Reports/Scheduled",
                "Exports/Excel", "Exports/CSV", "Exports/PDF", "Exports/Diagnostics", "Exports/General",
                "Logs/Desktop", "Logs/Server", "Logs/PostgreSQL", "Logs/Archive", "Temp")) {
            Files.createDirectories(workspace.resolve(path));
        }
    }

    private long size(Path root) {
        if (!insideWorkspace(root) || !Files.exists(root)) return 0;
        final long[] total = {0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && !attrs.isSymbolicLink()) total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) { }
        return total[0];
    }

    private boolean insideWorkspace(Path path) {
        return workspace != null && path != null && path.toAbsolutePath().normalize().startsWith(workspace);
    }

    private int boundedInt(String key, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(setting(key, Integer.toString(def)).trim()))); }
        catch (Exception ignored) { return def; }
    }

    private String setting(String key, String def) {
        try {
            String value = jdbc.queryForObject("SELECT setting_value FROM application_setting WHERE setting_key=?", String.class, key);
            return value == null ? def : value;
        } catch (Exception ignored) { return def; }
    }

    private void putSetting(String key, String value) {
        try {
            jdbc.update("INSERT INTO application_setting(setting_key,setting_value,updated_at) VALUES(?,?,CURRENT_TIMESTAMP) "
                    + "ON CONFLICT(setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value,updated_at=CURRENT_TIMESTAMP", key, value);
        } catch (Exception ignored) { }
    }

    private static void requireAdmin() {
        if (!"ADMIN".equalsIgnoreCase(CurrentUser.require().role()))
            throw new SecurityException("Storage cleanup can be run only by an administrator");
    }

    private static final class Counter { int deleted; int compressed; long reclaimed; }
}
