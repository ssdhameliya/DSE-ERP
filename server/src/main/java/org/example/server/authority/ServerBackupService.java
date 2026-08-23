package org.example.server.authority;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ServerBackupService {
    private final Path root;
    private final String url;
    private final String user;
    private final String password;
    private final String postgresHome;
    private final boolean scheduledEnabled;
    private final JpaNativeRepository db;
    private volatile LocalDate lastScheduled;

    public ServerBackupService(@Value("${dse.workspace.path:}") String workspace,
                               @Value("${spring.datasource.url}") String url,
                               @Value("${spring.datasource.username}") String user,
                               @Value("${spring.datasource.password}") String password,
                               @Value("${dse.postgres.home:}") String postgresHome,
                               @Value("${dse.backup.enabled:false}") boolean scheduledEnabled,
                               JpaNativeRepository db) {
        this.root = (workspace == null || workspace.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(workspace))
                .toAbsolutePath().normalize().resolve("Backups").resolve("Server");
        this.url = url;
        this.user = user;
        this.password = password;
        this.postgresHome = postgresHome == null ? "" : postgresHome.trim();
        this.scheduledEnabled = scheduledEnabled;
        this.db = db;
    }

    public synchronized BackupFile create(String source) throws IOException {
        Files.createDirectories(root);
        Path target = root.resolve("DSE-ERP-Server-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pgbackup");
        runPgDump(target);
        retain(retention());
        return file(target, source == null || source.isBlank() ? "SERVER" : source);
    }

    public synchronized BackupFile importBackup(String originalName, byte[] data) throws IOException {
        if (data == null || data.length < 16) throw new IOException("The selected backup is empty.");
        Files.createDirectories(root);
        String base = safeName(originalName == null ? "backup.pgbackup" : originalName);
        if (!base.toLowerCase(Locale.ROOT).endsWith(".pgbackup"))
            throw new IOException("Company-server restore requires a PostgreSQL .pgbackup file.");
        Path target = root.resolve("Imported-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + base);
        Files.write(target, data, StandardOpenOption.CREATE_NEW);
        Validation validation = validate(target.getFileName().toString());
        if (!validation.valid()) {
            Files.deleteIfExists(target);
            throw new IOException(validation.message());
        }
        retain(retention());
        return file(target, "IMPORTED");
    }

    public List<BackupFile> list() throws IOException {
        Files.createDirectories(root);
        try (var stream = Files.list(root)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pgbackup"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .map(p -> {
                        try {
                            String source = p.getFileName().toString().startsWith("Imported-") ? "IMPORTED" : "SERVER";
                            return file(p, source);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).toList();
        }
    }

    public byte[] read(String name) throws IOException {
        Path file = safe(name);
        if (!Files.isRegularFile(file)) throw new FileNotFoundException(name);
        return Files.readAllBytes(file);
    }

    public Validation validate(String name) throws IOException {
        Path file = safe(name);
        if (!Files.isRegularFile(file)) throw new FileNotFoundException(name);
        ProcessBuilder builder = new ProcessBuilder(tool("pg_restore"), "--list", file.toString()).redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return new Validation(false, "Backup validation timed out.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (process.exitValue() != 0) return new Validation(false, "Backup validation failed: " + concise(output));
        return new Validation(true, "PostgreSQL backup structure is valid.");
    }

    public synchronized String stageRestore(String name, byte[] data) throws IOException {
        if (data == null || data.length < 16) throw new IOException("Restore backup is empty");
        Files.createDirectories(root);
        Path pending = root.resolve("restore-pending.pgbackup");
        Files.write(pending, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(root.resolve("restore-pending.marker"), "STAGED " + Instant.now() + " " + safeName(name));
        return "Restore staged on the server. Restart the company server with the staged-restore procedure before normal startup.";
    }

    public synchronized String stageStoredRestore(String name) throws IOException {
        Path file = safe(name);
        Validation validation = validate(name);
        if (!validation.valid()) throw new IOException(validation.message());
        return stageRestore(name, Files.readAllBytes(file));
    }

    public synchronized void deleteSafely(String name) throws IOException {
        Path file = safe(name);
        if (!Files.isRegularFile(file)) throw new FileNotFoundException(name);
        Path trash = root.resolve(".trash");
        Files.createDirectories(trash);
        String stamped = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + file.getFileName();
        Files.move(file, trash.resolve(stamped), StandardCopyOption.REPLACE_EXISTING);
    }

    public DatabaseMetrics metrics() {
        String databaseName = db.queryForObject("SELECT current_database()", String.class);
        Long bytes = db.queryForObject("SELECT pg_database_size(current_database())", Long.class);
        return new DatabaseMetrics(databaseName == null ? "PostgreSQL" : databaseName,
                bytes == null ? 0L : Math.max(0L, bytes), true);
    }

    @Scheduled(initialDelayString = "${dse.backup.scheduler-initial-delay-ms:300000}",
            fixedDelayString = "${dse.backup.scheduler-check-ms:3600000}")
    public void scheduled() {
        if (!scheduledEnabled) return;
        try {
            String schedule = setting("backup.schedule", "DAILY").toUpperCase(Locale.ROOT);
            if ("MANUAL".equals(schedule)) return;
            LocalDate today = LocalDate.now();
            boolean due = !today.equals(lastScheduled) && (!"WEEKLY".equals(schedule) || today.getDayOfWeek() == DayOfWeek.SUNDAY);
            if (due) {
                create("SCHEDULED");
                lastScheduled = today;
            }
        } catch (Exception e) {
            System.err.println("Scheduled server backup failed: " + e.getMessage());
        }
    }

    private void runPgDump(Path target) throws IOException {
        List<String> command = new ArrayList<>(List.of(tool("pg_dump"), "--format=custom", "--no-owner", "--no-privileges",
                "--file=" + target, "--username=" + user, jdbcToPostgresUrl(url)));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", password == null ? "" : password);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("Server backup timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(target) || Files.size(target) == 0) {
            Files.deleteIfExists(target);
            throw new IOException("pg_dump failed: " + concise(output));
        }
    }

    private int retention() {
        try {
            return Math.max(1, Math.min(365, Integer.parseInt(setting("backup.retention", "14"))));
        } catch (Exception e) {
            return 14;
        }
    }

    private String setting(String key, String fallback) {
        try {
            String value = db.queryForObject("SELECT setting_value FROM application_setting WHERE setting_key=?", String.class, key);
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void retain(int count) throws IOException {
        List<Path> files;
        try (var stream = Files.list(root)) {
            files = stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pgbackup"))
                    .sorted(Comparator.comparingLong(this::modified).reversed()).toList();
        }
        for (int i = count; i < files.size(); i++) Files.deleteIfExists(files.get(i));
    }

    private BackupFile file(Path path, String source) throws IOException {
        return new BackupFile(path.getFileName().toString(), Files.size(path), Files.getLastModifiedTime(path).toString(), source);
    }

    private Path safe(String name) {
        Path file = root.resolve(safeName(name)).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Invalid backup name");
        return file;
    }

    private static String safeName(String name) {
        return (name == null ? "backup.pgbackup" : name).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private long modified(Path file) {
        try { return Files.getLastModifiedTime(file).toMillis(); } catch (IOException e) { return 0; }
    }

    private String tool(String name) {
        if (postgresHome.isBlank()) return name;
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? name + ".exe" : name;
        return Path.of(postgresHome).resolve("bin").resolve(executable).toString();
    }

    private static String jdbcToPostgresUrl(String jdbc) { return jdbc.startsWith("jdbc:") ? jdbc.substring(5) : jdbc; }
    private static String concise(String value) {
        if (value == null || value.isBlank()) return "Unknown PostgreSQL error";
        String oneLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        return oneLine.length() > 500 ? oneLine.substring(0, 500) + "…" : oneLine;
    }

    public record BackupFile(String name, long size, String createdAt, String source) {}
    public record Validation(boolean valid, String message) {}
    public record DatabaseMetrics(String databaseName, long sizeBytes, boolean ready) {}
}
