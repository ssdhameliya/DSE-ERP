package org.example.server.authority;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.util.BusinessClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class ServerBackupService {
    private final Path workspaceRoot;
    private final Path root;
    private final String url;
    private final String user;
    private final String password;
    private final String postgresHome;
    private final boolean scheduledEnabled;
    private final String deploymentEnvironment;
    private final String applicationVersion;
    private final JpaNativeRepository db;

    public ServerBackupService(@Value("${dse.workspace.path:}") String workspace,
                               @Value("${spring.datasource.url}") String url,
                               @Value("${spring.datasource.username}") String user,
                               @Value("${spring.datasource.password}") String password,
                               @Value("${dse.postgres.home:}") String postgresHome,
                               @Value("${dse.backup.enabled:false}") boolean scheduledEnabled,
                               @Value("${dse.deployment.environment:LOCAL}") String deploymentEnvironment,
                               @Value("${dse.app.version:DEV}") String applicationVersion,
                               JpaNativeRepository db) {
        this.workspaceRoot = (workspace == null || workspace.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(workspace))
                .toAbsolutePath().normalize();
        this.root = workspaceRoot.resolve("Backups").resolve("Server");
        this.url = url;
        this.user = user;
        this.password = password;
        this.postgresHome = postgresHome == null ? "" : postgresHome.trim();
        this.scheduledEnabled = scheduledEnabled;
        this.deploymentEnvironment = deploymentEnvironment == null ? "LOCAL" : deploymentEnvironment.trim().toUpperCase(Locale.ROOT);
        this.applicationVersion = applicationVersion == null || applicationVersion.isBlank() ? "DEV" : applicationVersion.trim();
        this.db = db;
    }

    public synchronized BackupFile create(String source) throws IOException {
        Files.createDirectories(root);
        Path target = root.resolve("DSE-ERP-Server-" + BusinessClock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pgbackup");
        runPgDump(target);
        Validation validation = validate(target.getFileName().toString());
        if (!validation.valid()) { Files.deleteIfExists(target); throw new IOException("Backup verification failed: " + validation.message()); }
        retain(retention());
        return file(target, source == null || source.isBlank() ? "SERVER" : source);
    }

    public synchronized BackupFile importBackup(String originalName, InputStream data) throws IOException {
        if (data == null) throw new IOException("The selected backup is empty.");
        Files.createDirectories(root);
        String base = safeName(originalName == null ? "backup.pgbackup" : originalName);
        if (!base.toLowerCase(Locale.ROOT).endsWith(".pgbackup"))
            throw new IOException("Company-server restore requires a PostgreSQL .pgbackup file.");
        Path target = root.resolve("Imported-" + BusinessClock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + base);
        try {
            copyBounded(data, target, maxBackupUploadBytes());
            if (Files.size(target) < 16) throw new IOException("The selected backup is empty.");
            Validation validation = validate(target.getFileName().toString());
            if (!validation.valid()) throw new IOException(validation.message());
            retain(retention());
            return file(target, "IMPORTED");
        } catch (IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
    }

    public List<BackupFile> list() throws IOException {
        Files.createDirectories(root);
        try (var stream = Files.list(root)) {
            return stream.filter(p -> isOrdinaryBackupName(p.getFileName().toString()))
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

    public Path readablePath(String name) throws IOException {
        Path file = safe(name);
        if (!Files.isRegularFile(file)) throw new FileNotFoundException(name);
        return file;
    }

    public long size(String name) throws IOException { return Files.size(readablePath(name)); }

    public Validation validate(String name) throws IOException {
        Path file = safe(name);
        if (!Files.isRegularFile(file)) throw new FileNotFoundException(name);
        return validateFile(file);
    }

    private Validation validateFile(Path file) throws IOException {
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
        Validation content=validateArchiveListing(output);
        if(!content.valid())return content;
        return new Validation(true, "DSE ERP PostgreSQL backup structure is valid.");
    }


    /**
     * Creates a self-contained disaster-recovery package for an administrator.
     * The package contains a freshly validated PostgreSQL snapshot plus server-owned
     * business files. It intentionally excludes server configuration, environment files
     * and database credentials.
     */
    public synchronized RecoveryPackage createRecoveryPackage() throws IOException {
        Path recoveryRoot = workspaceRoot.resolve("Backups").resolve("Recovery");
        Files.createDirectories(recoveryRoot);
        String stamp = BusinessClock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "DSE-ERP-" + deploymentEnvironment + "-Recovery-" + stamp + ".zip";
        Path databaseSnapshot = recoveryRoot.resolve("recovery-" + UUID.randomUUID() + ".pgbackup");
        try {
            runPgDump(databaseSnapshot);
            Validation validation = validateFile(databaseSnapshot);
            if (!validation.valid()) throw new IOException("Recovery database validation failed: " + validation.message());

            String databaseName = metrics().databaseName();
            String databaseSha = sha256(databaseSnapshot);
            Path packageFile = recoveryRoot.resolve("package-" + UUID.randomUUID() + ".zip");
            boolean complete = false;
            try {
                try (OutputStream fileOut = Files.newOutputStream(packageFile, StandardOpenOption.CREATE_NEW);
                     ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(fileOut))) {
                    Properties manifest = new Properties();
                    manifest.setProperty("format.version", "1");
                    manifest.setProperty("createdAt", Instant.now().toString());
                    manifest.setProperty("application.version", applicationVersion);
                    manifest.setProperty("environment", deploymentEnvironment);
                    manifest.setProperty("database.name", databaseName == null ? "" : databaseName);
                    manifest.setProperty("database.file", "database.pgbackup");
                    manifest.setProperty("database.sha256", databaseSha);
                    manifest.setProperty("files", "Attachments,Documents,Templates");
                    ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
                    manifest.store(manifestBytes, "DSE ERP local disaster-recovery package");
                    putZipBytes(zip, "manifest.properties", manifestBytes.toByteArray());
                    putZipFile(zip, databaseSnapshot, "database.pgbackup");
                    addTree(zip, workspaceRoot.resolve("Attachments"), "workspace/Attachments");
                    addTree(zip, workspaceRoot.resolve("Documents"), "workspace/Documents");
                    addTree(zip, workspaceRoot.resolve("Templates"), "workspace/Templates");
                }
                complete = true;
                return new RecoveryPackage(filename, packageFile, Files.size(packageFile), databaseSha, databaseName, deploymentEnvironment, applicationVersion);
            } finally {
                if (!complete) Files.deleteIfExists(packageFile);
            }
        } finally {
            Files.deleteIfExists(databaseSnapshot);
        }
    }

    private static void putZipBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void putZipFile(ZipOutputStream zip, Path file, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static void addTree(ZipOutputStream zip, Path root, String prefix) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                Path relative = root.relativize(file);
                String entryName = prefix + "/" + relative.toString().replace('\\', '/');
                putZipFile(zip, file, entryName);
            }
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    public synchronized String stageRestore(String name, InputStream data) throws IOException {
        if (data == null) throw new IOException("Restore backup is empty");
        Files.createDirectories(root);
        Path candidate=root.resolve("restore-validation-"+UUID.randomUUID()+".pgbackup");
        try{
            copyBounded(data,candidate,maxBackupUploadBytes());
            if(Files.size(candidate)<16)throw new IOException("Restore backup is empty");
            Validation validation=validate(candidate.getFileName().toString());
            if(!validation.valid())throw new IOException(validation.message());
            stageValidatedCandidate(candidate,name);
        }finally{Files.deleteIfExists(candidate);}
        return "Restore staged on the server. Restart the company server with the staged-restore procedure before normal startup.";
    }

    public synchronized String stageStoredRestore(String name) throws IOException {
        Path file = readablePath(name);
        Validation validation = validate(name);
        if (!validation.valid()) throw new IOException(validation.message());
        Path candidate=root.resolve("restore-validation-"+UUID.randomUUID()+".pgbackup");
        try {
            Files.copy(file,candidate,StandardCopyOption.REPLACE_EXISTING);
            stageValidatedCandidate(candidate,name);
        } finally { Files.deleteIfExists(candidate); }
        return "Restore staged on the server. Restart the company server with the staged-restore procedure before normal startup.";
    }

    private void stageValidatedCandidate(Path candidate,String name) throws IOException {
        Path pending=root.resolve("restore-pending.pgbackup");
        try {
            Files.move(candidate,pending,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(candidate,pending,StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(root.resolve("restore-pending.marker"),"STAGED "+Instant.now()+" "+safeName(name));
    }

    public synchronized void deleteSafely(String name) throws IOException {
        Path file = safe(name);
        if (!Files.isRegularFile(file)) throw new FileNotFoundException(name);
        Path trash = root.resolve(".trash");
        Files.createDirectories(trash);
        String stamped = BusinessClock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + file.getFileName();
        Files.move(file, trash.resolve(stamped), StandardCopyOption.REPLACE_EXISTING);
        purgeTrash();
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
            String schedule = setting("backup.schedule", "WEEKLY").toUpperCase(Locale.ROOT);
            if ("MANUAL".equals(schedule)) return;
            LocalDate today = BusinessClock.today();
            String stateKey="backup.lastScheduled."+schedule;
            String previous=schedulerState(stateKey);
            boolean due = !today.toString().equals(previous) && (!"WEEKLY".equals(schedule) || today.getDayOfWeek() == DayOfWeek.SUNDAY);
            if (due) {
                create("SCHEDULED");
                saveSchedulerState(stateKey,today.toString());
                saveSchedulerState("backup.lastSuccessAt",BusinessClock.nowUtcText());
                saveSchedulerState("backup.lastError","");
            }
            purgeTrash();
        } catch (Exception e) {
            try {
                saveSchedulerState("backup.lastFailureAt",BusinessClock.nowUtcText());
                saveSchedulerState("backup.lastError",concise(e.getMessage()));
            } catch (Exception persistFailure) { e.addSuppressed(persistFailure); }
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


    private Validation validateArchiveListing(String output){
        String listing=output==null?"":output.toUpperCase(Locale.ROOT);
        for(String required:List.of("TABLE PUBLIC SALES_HEADER","TABLE PUBLIC PURCHASE_HEADER","TABLE PUBLIC PARTY_MASTER","TABLE PUBLIC ITEM_MASTER","TABLE PUBLIC APPLICATION_METADATA")){
            if(!listing.contains(required))return new Validation(false,"Backup is a PostgreSQL archive but not a complete DSE ERP backup (missing "+required.substring("TABLE PUBLIC ".length()).toLowerCase(Locale.ROOT)+").");
        }
        for(String forbidden:List.of(" EVENT TRIGGER "," FOREIGN DATA WRAPPER "," USER MAPPING "," SUBSCRIPTION "," PUBLICATION "," PROCEDURAL LANGUAGE ")){
            if(listing.contains(forbidden))return new Validation(false,"Backup contains unsupported database objects and cannot be restored safely.");
        }
        return new Validation(true,"DSE ERP archive contents validated.");
    }

    private int retention() {
        try {
            return Math.max(1, Math.min(365, Integer.parseInt(setting("backup.retention", "2"))));
        } catch (Exception e) {
            return 2;
        }
    }

    private String setting(String key, String fallback) {
        List<String> values=db.query("SELECT setting_value FROM application_setting WHERE setting_key=?",(r,i)->r.getString(1),key);
        String value=values.isEmpty()?null:values.getFirst();
        return value == null || value.isBlank() ? fallback : value;
    }

    private long maxBackupUploadBytes() {
        try { return Math.max(16L*1024*1024,Long.parseLong(setting("backup.maxUploadBytes","21474836480"))); }
        catch (NumberFormatException ignored) { return 21474836480L; }
    }

    private static void copyBounded(InputStream input,Path target,long maxBytes) throws IOException {
        long total=0;byte[] buffer=new byte[128*1024];
        try(OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            for(int read;(read=input.read(buffer))>=0;){if(read==0)continue;total+=read;if(total>maxBytes)throw new IOException("Backup exceeds the configured maximum upload size.");out.write(buffer,0,read);}
        }
    }

    private String schedulerState(String key) {
        List<String> values=db.query("SELECT state_value FROM backup_scheduler_state WHERE state_key=?",(r,i)->r.getString(1),key);
        return values.isEmpty()?null:values.getFirst();
    }

    private void saveSchedulerState(String key,String value) {
        db.update("INSERT INTO backup_scheduler_state(state_key,state_value,updated_at) VALUES(?,?,CURRENT_TIMESTAMP) ON CONFLICT(state_key) DO UPDATE SET state_value=excluded.state_value,updated_at=CURRENT_TIMESTAMP",key,value);
    }

    private void purgeTrash() throws IOException {
        Path trash=root.resolve(".trash");if(!Files.isDirectory(trash))return;
        int days;try{days=Math.max(1,Math.min(365,Integer.parseInt(setting("backup.trashRetentionDays","30"))));}catch(NumberFormatException ignored){days=30;}
        Instant cutoff=BusinessClock.nowUtc().minus(Duration.ofDays(days));
        try(var files=Files.list(trash)){for(Path file:files.toList())if(Files.isRegularFile(file)&&Files.getLastModifiedTime(file).toInstant().isBefore(cutoff))Files.deleteIfExists(file);}
    }

    private void retain(int count) throws IOException {
        List<Path> files;
        try (var stream = Files.list(root)) {
            files = stream.filter(p -> isOrdinaryBackupName(p.getFileName().toString()))
                    .sorted(Comparator.comparingLong(this::modified).reversed()).toList();
        }
        for (int i = count; i < files.size(); i++) Files.deleteIfExists(files.get(i));
    }

    static boolean isOrdinaryBackupName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".pgbackup")
                && !"restore-pending.pgbackup".equals(normalized)
                && !normalized.startsWith("restore-validation-");
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
    public record RecoveryPackage(String filename, Path file, long size, String databaseSha256, String databaseName, String environment, String applicationVersion) {}
}
