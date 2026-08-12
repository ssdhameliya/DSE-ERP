package org.example.api.runtime;

import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 5.1.31 managed PostgreSQL runtime.
 *
 * Fresh workspaces use a private PostgreSQL cluster owned by DSE ERP. Existing installations
 * that explicitly configure db.url or DSE_DB_URL remain external and are never reconfigured.
 */
public final class ManagedPostgresRuntime {
    private static final String DATABASE = "dse_erp";
    private static final String APP_USER = "dse_erp_app";
    private static final String OWNER_USER = "dse_erp_owner";
    private static final int DEFAULT_PORT = 55432;
    private static final String INSTANCE_MARKER = ".dse-erp-managed-instance";
    private static final int PORT_SEARCH_LIMIT = 20;
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(45);
    private static volatile boolean prepared;
    private static volatile boolean startedByDesktop;
    private static volatile Path activeHome;
    private static volatile Path activeData;

    private ManagedPostgresRuntime() {}

    public static synchronized RuntimeDatabase ensureReady() {
        if (prepared && ConfigManager.isPostgreSql()) {
            return new RuntimeDatabase(ConfigManager.getDbUrl(), ConfigManager.getDbUsername(), true,
                    activeHome, activeData);
        }
        if (!WorkspaceManager.isConfigured()) {
            throw new IllegalStateException("Workspace must be configured before PostgreSQL can be prepared.");
        }
        if (!ConfigManager.isPostgreSql()) {
            return new RuntimeDatabase(ConfigManager.getDbUrl(), ConfigManager.getDbUsername(), false, null, null);
        }
        if (!shouldManage()) {
            prepared = true;
            return new RuntimeDatabase(ConfigManager.getDbUrl(), ConfigManager.getDbUsername(), false, null, null);
        }

        Path home = locatePostgresHome();
        Path data = WorkspaceManager.getDatabaseFolder().resolve("PostgreSQL").resolve("data");
        Path stateFile = WorkspaceManager.getConfigurationFolder().resolve("runtime-postgres.properties");
        Path log = WorkspaceManager.getLogsFolder().resolve("postgresql.log");

        try {
            Files.createDirectories(data.getParent());
            Files.createDirectories(log.getParent());

            boolean stateExists = Files.isRegularFile(stateFile);
            boolean clusterExists = Files.isRegularFile(data.resolve("PG_VERSION"));
            RuntimeState state;

            if (!stateExists && !clusterExists) {
                // Genuine first installation for this workspace.
                state = newRuntimeState();
                initializeCluster(home, data, state);
                configureCluster(data, state.port());
                saveState(stateFile, state);
                writeInstanceMarker(data, state.instanceId());
            } else if (stateExists && clusterExists) {
                // Normal restart/upgrade: ALWAYS reuse the existing database.
                state = loadState(stateFile);
                bindOrVerifyInstanceIdentity(stateFile, data, state);
            } else if (stateExists) {
                throw new IllegalStateException(
                        "Managed database files are missing but this workspace already has a database identity. "
                        + "DSE ERP will not create an empty replacement. Restore the PostgreSQL data folder or a backup.");
            } else {
                throw new IllegalStateException(
                        "Managed PostgreSQL data exists but its runtime identity/credentials file is missing. "
                        + "DSE ERP will not reset or replace this database. Restore runtime-postgres.properties or a backup.");
            }

            int port = state.port();
            if (!isClusterRunning(home, data)) {
                if (isPortListening(port)) {
                    port = findAvailablePort(DEFAULT_PORT);
                    state = state.withPort(port);
                    saveState(stateFile, state);
                    configureCluster(data, port);
                }
                startCluster(home, data, log, port);
                startedByDesktop = true;
            }

            waitForPort(port, Duration.ofSeconds(25));
            ensureRoleAndDatabase(home, port, state);
            String url = "jdbc:postgresql://127.0.0.1:" + port + "/" + DATABASE;
            ConfigManager.applyRuntimeDatabase(url, APP_USER, state.appPassword());
            activeHome = home;
            activeData = data;
            prepared = true;
            return new RuntimeDatabase(url, APP_USER, true, home, data);
        } catch (Exception exception) {
            throw new IllegalStateException("Managed PostgreSQL could not be prepared. Runtime: " + home
                    + " | Data: " + data + " | Reason: " + exception.getMessage(), exception);
        }
    }

    /**
     * Managed is the safe default for desktop installations and IntelliJ full-stack runs.
     * A legacy config may still contain the old localhost:5432 default; that must not disable
     * the managed runtime, otherwise a migrated workspace unexpectedly depends on a system service.
     * Only an explicit external mode, environment URL, or genuinely custom configured URL opts out.
     */
    private static boolean shouldManage() {
        String mode = System.getenv().getOrDefault("DSE_POSTGRES_MODE",
                ConfigManager.get("runtime.postgres.mode", "managed")).trim().toLowerCase(Locale.ROOT);
        if ("external".equals(mode)) return false;

        /*
         * Once a workspace owns a managed PostgreSQL identity, that ownership is
         * sticky across upgrades. Old environment/config values must never make an
         * existing workspace silently switch databases. External DB use requires
         * the explicit runtime.postgres.mode/DSE_POSTGRES_MODE = external switch.
         */
        Path stateFile = WorkspaceManager.getConfigurationFolder().resolve("runtime-postgres.properties");
        Path data = WorkspaceManager.getDatabaseFolder().resolve("PostgreSQL").resolve("data");
        if (Files.isRegularFile(stateFile) || Files.isRegularFile(data.resolve("PG_VERSION"))) return true;

        String environment = ConfigManager.getEnvironmentDbUrl();
        if (environment != null && !isLegacyLocalDefault(environment)) return false;
        String configured = ConfigManager.getConfiguredDbUrl();
        return configured == null || isLegacyLocalDefault(configured);
    }


    private static boolean isLegacyLocalDefault(String url) {
        if (url == null) return false;
        String normalized = url.trim().toLowerCase(Locale.ROOT)
                .replace("jdbc:postgresql://localhost:", "jdbc:postgresql://127.0.0.1:");
        return normalized.equals("jdbc:postgresql://127.0.0.1:5432/dse_erp");
    }

    private static Path locatePostgresHome() {
        List<Path> candidates = new ArrayList<>();
        String explicit = System.getProperty("dse.erp.postgres.home", System.getenv("DSE_POSTGRES_HOME"));
        if (explicit == null || explicit.isBlank()) {
            // Keep development and native packaging on the same runtime override name.
            // package-windows.ps1/package-macos.sh already use DSE_POSTGRES_RUNTIME_DIR.
            explicit = System.getenv("DSE_POSTGRES_RUNTIME_DIR");
        }
        if (explicit != null && !explicit.isBlank()) candidates.add(Path.of(explicit));

        try {
            Path code = Path.of(ManagedPostgresRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            Path folder = Files.isDirectory(code) ? code : code.getParent();
            if (folder != null) candidates.add(folder.resolve("runtime/postgresql"));
        } catch (Exception ignored) {}

        Path cwd = Path.of("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve("runtime/postgresql"));
        candidates.add(cwd.resolve("../runtime/postgresql").normalize());

        if (!isPackagedRuntime()) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                candidates.add(Path.of("D:/PostgreSQL/18/pgsql"));
                candidates.add(Path.of("C:/Program Files/PostgreSQL/18"));
            } else if (os.contains("mac")) {
                candidates.add(Path.of("/opt/homebrew/opt/postgresql@18"));
                candidates.add(Path.of("/usr/local/opt/postgresql@18"));
                candidates.add(Path.of("/Library/PostgreSQL/18"));
            }
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(bin(candidate, "initdb"))
                    && Files.isRegularFile(bin(candidate, "pg_ctl"))
                    && Files.isRegularFile(bin(candidate, "psql"))) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException(isPackagedRuntime()
                ? "DSE ERP installation is incomplete: bundled PostgreSQL runtime is missing."
                : "PostgreSQL 18 runtime was not found. Development can set DSE_POSTGRES_HOME.");
    }

    public static void verifyBundledRuntime() {
        if (!isPackagedRuntime()) return;
        Path home = locatePostgresHome();
        for (String command : List.of("initdb", "pg_ctl", "psql", "createdb")) {
            if (!Files.isRegularFile(bin(home, command))) {
                throw new IllegalStateException("DSE ERP installation is incomplete: PostgreSQL command missing: " + command);
            }
        }
    }

    private static boolean isPackagedRuntime() {
        return Boolean.parseBoolean(System.getProperty("dse.erp.packaged", "false"));
    }

    private static Path bin(Path home, String name) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return home.resolve("bin").resolve(windows ? name + ".exe" : name);
    }

    private static RuntimeState loadState(Path file) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read PostgreSQL runtime identity/credentials", e);
        }

        int port;
        try { port = Integer.parseInt(p.getProperty("port", Integer.toString(DEFAULT_PORT))); }
        catch (NumberFormatException ignored) { port = DEFAULT_PORT; }

        String owner = p.getProperty("owner.password");
        String app = p.getProperty("app.password");
        if (owner == null || owner.isBlank() || app == null || app.isBlank()) {
            throw new IllegalStateException(
                    "Managed PostgreSQL runtime credentials are incomplete. DSE ERP will not generate replacements for an existing database.");
        }

        String instanceId = p.getProperty("instance.id");
        return new RuntimeState(port, owner, app, instanceId == null ? "" : instanceId.trim());
    }

    private static RuntimeState newRuntimeState() {
        return new RuntimeState(DEFAULT_PORT, password(), password(), java.util.UUID.randomUUID().toString());
    }

    private static void bindOrVerifyInstanceIdentity(Path stateFile, Path data, RuntimeState loaded) {
        Path marker = data.resolve(INSTANCE_MARKER);
        RuntimeState state = loaded;

        // Upgrade adoption for managed databases created before the identity marker existed.
        if (state.instanceId() == null || state.instanceId().isBlank()) {
            state = new RuntimeState(state.port(), state.ownerPassword(), state.appPassword(),
                    java.util.UUID.randomUUID().toString());
            saveState(stateFile, state);
        }

        if (!Files.isRegularFile(marker)) {
            writeInstanceMarker(data, state.instanceId());
            return;
        }

        try {
            String dataId = Files.readString(marker, StandardCharsets.UTF_8).trim();
            if (!state.instanceId().equals(dataId)) {
                throw new IllegalStateException(
                        "Managed PostgreSQL identity mismatch. The workspace configuration and database data folder do not belong together. "
                        + "DSE ERP stopped to protect existing data.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to verify managed PostgreSQL database identity", e);
        }
    }

    private static void writeInstanceMarker(Path data, String instanceId) {
        try {
            Files.createDirectories(data);
            Files.writeString(data.resolve(INSTANCE_MARKER), instanceId + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist managed PostgreSQL database identity", e);
        }
    }

    private static void saveState(Path file, RuntimeState state) {
        Properties p = new Properties();
        p.setProperty("version", "1");
        p.setProperty("port", Integer.toString(state.port()));
        p.setProperty("owner.username", OWNER_USER);
        p.setProperty("owner.password", state.ownerPassword());
        p.setProperty("app.username", APP_USER);
        p.setProperty("app.password", state.appPassword());
        p.setProperty("database", DATABASE);
        p.setProperty("instance.id", state.instanceId());
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) { p.store(out, "DSE ERP managed PostgreSQL runtime"); }
            restrictPermissions(file);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save managed PostgreSQL runtime credentials", e);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACLs are inherited from the user's private workspace/config folder.
        }
    }

    private static String password() {
        byte[] bytes = new byte[30];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void initializeCluster(Path home, Path data, RuntimeState state) throws Exception {
        Files.createDirectories(data);
        Path passwordFile = Files.createTempFile(WorkspaceManager.getTempFolder(), "pg-owner-", ".pwd");
        try {
            Files.writeString(passwordFile, state.ownerPassword(), StandardCharsets.UTF_8);
            restrictPermissions(passwordFile);
            run(List.of(bin(home, "initdb").toString(), "-D", data.toString(), "-U", OWNER_USER,
                    "--pwfile=" + passwordFile, "--encoding=UTF8", "--locale=C",
                    "--auth-local=scram-sha-256", "--auth-host=scram-sha-256"), null, COMMAND_TIMEOUT);
        } finally {
            Files.deleteIfExists(passwordFile);
        }
    }

    private static void configureCluster(Path data, int port) throws IOException {
        Path config = data.resolve("postgresql.conf");
        String marker = "# DSE ERP managed settings";
        String content = Files.readString(config);
        int markerAt = content.indexOf(marker);
        if (markerAt >= 0) content = content.substring(0, markerAt).stripTrailing() + System.lineSeparator();
        content += System.lineSeparator() + marker + System.lineSeparator()
                + "listen_addresses = '127.0.0.1'" + System.lineSeparator()
                + "port = " + port + System.lineSeparator()
                + "max_connections = 40" + System.lineSeparator()
                + "shared_buffers = '64MB'" + System.lineSeparator()
                + "password_encryption = 'scram-sha-256'" + System.lineSeparator();
        Files.writeString(config, content, StandardCharsets.UTF_8);
    }

    private static boolean isClusterRunning(Path home, Path data) {
        try {
            ProcessResult result = run(List.of(bin(home, "pg_ctl").toString(), "-D", data.toString(), "status"), null,
                    Duration.ofSeconds(8), false);
            return result.exitCode() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void startCluster(Path home, Path data, Path log, int port) throws Exception {
        run(List.of(bin(home, "pg_ctl").toString(), "-D", data.toString(), "-l", log.toString(),
                "-w", "-t", "30", "-o", "-p " + port + " -h 127.0.0.1", "start"), null, COMMAND_TIMEOUT);
    }

    private static void ensureRoleAndDatabase(Path home, int port, RuntimeState state) throws Exception {
        Properties env = new Properties();
        env.setProperty("PGPASSWORD", state.ownerPassword());

        String roleExists;
        try {
            roleExists = scalar(home, port, env, "SELECT 1 FROM pg_roles WHERE rolname='" + APP_USER + "'");
        } catch (Exception exception) {
            throw new IllegalStateException("Managed PostgreSQL is ready on port " + port
                    + " but role verification failed: " + exception.getMessage(), exception);
        }
        if (!"1".equals(roleExists)) {
            String sql = "CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + sqlLiteral(state.appPassword()) + "';";
            psql(home, port, env, "postgres", sql);
        } else {
            psql(home, port, env, "postgres", "ALTER ROLE " + APP_USER + " PASSWORD '" + sqlLiteral(state.appPassword()) + "';");
        }
        String dbExists = scalar(home, port, env, "SELECT 1 FROM pg_database WHERE datname='" + DATABASE + "'");
        if (!"1".equals(dbExists)) {
            run(List.of(bin(home, "createdb").toString(), "-h", "127.0.0.1", "-p", Integer.toString(port),
                    "-U", OWNER_USER, "-O", APP_USER, DATABASE), env, COMMAND_TIMEOUT);
        }
        Properties appEnv = new Properties();
        appEnv.setProperty("PGPASSWORD", state.appPassword());
        run(List.of(bin(home, "psql").toString(), "-h", "127.0.0.1", "-p", Integer.toString(port),
                "-U", APP_USER, "-d", DATABASE, "-v", "ON_ERROR_STOP=1", "-tAc", "SELECT 1"),
                appEnv, Duration.ofSeconds(15));
    }

    private static String scalar(Path home, int port, Properties env, String sql) throws Exception {
        ProcessResult result = run(List.of(bin(home, "psql").toString(), "-h", "127.0.0.1", "-p", Integer.toString(port),
                "-U", OWNER_USER, "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-tAc", sql), env, Duration.ofSeconds(15));
        return result.output().trim();
    }

    private static void psql(Path home, int port, Properties env, String database, String sql) throws Exception {
        run(List.of(bin(home, "psql").toString(), "-h", "127.0.0.1", "-p", Integer.toString(port),
                "-U", OWNER_USER, "-d", database, "-v", "ON_ERROR_STOP=1", "-c", sql), env, Duration.ofSeconds(15));
    }

    private static String sqlLiteral(String value) { return value.replace("'", "''"); }

    private static int findAvailablePort(int start) {
        for (int p = start; p < start + PORT_SEARCH_LIMIT; p++) if (!isPortListening(p)) return p;
        throw new IllegalStateException("No free local PostgreSQL port was found between " + start + " and "
                + (start + PORT_SEARCH_LIMIT - 1));
    }

    private static boolean isPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void waitForPort(int port, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isPortListening(port)) return;
            Thread.sleep(250);
        }
        throw new IllegalStateException("Managed PostgreSQL did not start on 127.0.0.1:" + port);
    }

    private static ProcessResult run(List<String> command, Properties environment, Duration timeout) throws Exception {
        return run(command, environment, timeout, true);
    }

    private static ProcessResult run(List<String> command, Properties environment, Duration timeout, boolean failOnError) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (environment != null) {
            for (String name : environment.stringPropertyNames()) {
                pb.environment().put(name, environment.getProperty(name));
            }
        }

        Process process = pb.start();

        // Drain process output concurrently. Waiting first and reading afterwards can
        // deadlock on Windows if a PostgreSQL utility fills its stdout/stderr pipe.
        CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return process.getInputStream().readAllBytes();
            } catch (IOException exception) {
                return ("Unable to read PostgreSQL command output: " + exception.getMessage())
                        .getBytes(StandardCharsets.UTF_8);
            }
        });

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            throw new IllegalStateException("PostgreSQL command timed out after "
                    + timeout.toSeconds() + "s: " + command.getFirst());
        }

        byte[] bytes;
        try {
            bytes = outputFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception outputError) {
            bytes = ("PostgreSQL command output unavailable: " + outputError.getMessage())
                    .getBytes(StandardCharsets.UTF_8);
        }
        String output = new String(bytes, StandardCharsets.UTF_8);
        ProcessResult result = new ProcessResult(process.exitValue(), output);
        if (failOnError && result.exitCode() != 0) {
            throw new IllegalStateException("PostgreSQL command failed (" + result.exitCode() + "): " + output.strip());
        }
        return result;
    }

    public static synchronized void shutdownIfConfigured() {
        if (!startedByDesktop || activeHome == null || activeData == null) return;
        boolean stop = Boolean.parseBoolean(ConfigManager.get("runtime.postgres.stopOnExit", "false"));
        if (!stop) return;
        try {
            run(List.of(bin(activeHome, "pg_ctl").toString(), "-D", activeData.toString(), "-w", "-t", "15", "stop", "-m", "fast"),
                    null, Duration.ofSeconds(20));
            startedByDesktop = false;
        } catch (Exception exception) {
            System.err.println("Managed PostgreSQL shutdown failed: " + exception.getMessage());
        }
    }

    public record RuntimeDatabase(String jdbcUrl, String username, boolean managed, Path runtimeHome, Path dataDirectory) {}
    private record RuntimeState(int port, String ownerPassword, String appPassword, String instanceId) {
        RuntimeState withPort(int newPort) { return new RuntimeState(newPort, ownerPassword, appPassword, instanceId); }
    }
    private record ProcessResult(int exitCode, String output) {}
}
