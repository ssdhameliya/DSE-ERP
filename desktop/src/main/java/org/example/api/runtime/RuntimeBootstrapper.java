package org.example.api.runtime;

import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.shared.RuntimeContract;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/**
 * 5.1.37 runtime foundation.
 *
 * Ensures managed PostgreSQL and the packaged Spring Boot backend are running before API-backed JavaFX screens open.
 */
public final class RuntimeBootstrapper {
    private static final Duration DEFAULT_START_TIMEOUT = Duration.ofSeconds(45);
    private static final int DEFAULT_MANAGED_SERVER_PORT = 58080;
    private static final int SERVER_PORT_SEARCH_LIMIT = 20;
    private static volatile Process managedServer;
    private static volatile boolean startedByDesktop;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RuntimeBootstrapper() {}

    public static synchronized RuntimeApiClient.RuntimeStatus ensureServerReady() {
        ManagedPostgresRuntime.ensureReady();
        prepareManagedServerEndpoint();
        verifyPackagedRuntime();
        ensureInternalBridgeToken();
        RuntimeApiClient client = new RuntimeApiClient();
        RuntimeApiClient.RuntimeStatus current = tryStatus(client);
        if (current != null && current.ready()) {
            try {
                requireCompatible(current);
                return current;
            } catch (IllegalStateException incompatible) {
                if (!isLocalApiEndpoint()) throw incompatible;
                // Never reuse a stale localhost backend from a previous IntelliJ/app run.
                // Move this desktop session to a free managed port and start the current server.
                int port = findAvailableServerPort(DEFAULT_MANAGED_SERVER_PORT);
                ConfigManager.applyRuntimeApiBaseUrl("http://127.0.0.1:" + port);
                client = new RuntimeApiClient();
            }
        }

        if (managedServer == null || !managedServer.isAlive()) {
            Path serverJar = locateServerJar();
            managedServer = startServer(serverJar);
            startedByDesktop = true;
        }

        Instant deadline = Instant.now().plus(DEFAULT_START_TIMEOUT);
        IllegalStateException last = null;
        while (Instant.now().isBefore(deadline)) {
            if (managedServer != null && !managedServer.isAlive()) {
                throw new IllegalStateException("DSE ERP backend stopped during startup. Check: " + serverLogPath());
            }
            try {
                RuntimeApiClient.RuntimeStatus status = client.status();
                if (status.ready()) {
                    requireCompatible(status);
                    return status;
                }
                last = new IllegalStateException(status.message());
            } catch (IllegalStateException exception) {
                last = exception;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while starting DSE ERP backend", exception);
            }
        }
        throw new IllegalStateException("DSE ERP backend did not become READY within "
                + DEFAULT_START_TIMEOUT.toSeconds() + " seconds. Check the managed database runtime and " + serverLogPath(), last);
    }


    private static void requireCompatible(RuntimeApiClient.RuntimeStatus status) {
        if (!RuntimeContract.SERVICE_NAME.equals(status.service())) {
            throw new IllegalStateException("The configured DSE ERP backend port is serving a different application: " + status.service());
        }
        if (!RuntimeContract.API_REVISION.equals(status.apiRevision())) {
            throw new IllegalStateException("DSE ERP backend API mismatch. Desktop requires "
                    + RuntimeContract.API_REVISION + " but server reports " + status.apiRevision()
                    + ". Stop the old backend and restart DSE ERP.");
        }
        if (!RuntimeContract.APP_VERSION.equals(status.version())) {
            throw new IllegalStateException("DSE ERP backend version mismatch. Desktop is "
                    + RuntimeContract.APP_VERSION + " but server is " + status.version()
                    + ". A stale backend is running and must not be reused.");
        }
    }

    private static RuntimeApiClient.RuntimeStatus tryStatus(RuntimeApiClient client) {
        try { return client.status(); }
        catch (IllegalStateException ignored) { return null; }
    }

    static Path locateServerJar() {
        List<Path> candidates = new ArrayList<>();
        String explicit = System.getProperty("dse.erp.server.jar", System.getenv("DSE_SERVER_JAR"));
        if (explicit != null && !explicit.isBlank()) candidates.add(Path.of(explicit));

        try {
            URI location = RuntimeBootstrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path code = Path.of(location).toAbsolutePath().normalize();
            Path folder = Files.isDirectory(code) ? code : code.getParent();
            if (folder != null) {
                candidates.add(folder.resolve("dse-erp-server.jar"));
                candidates.add(folder.resolve("server").resolve("dse-erp-server.jar"));
            }
        } catch (Exception ignored) {}

        if (!isPackagedRuntime()) {
            /*
             * IntelliJ/source runs use a content-fingerprinted backend JAR.
             * Unchanged server/shared source reuses the previously verified executable
             * JAR immediately. Any relevant source/resource/POM change produces a new
             * filename, preserving the Windows file-lock/stale-backend protection.
             */
            Path cached = locateOrBuildDevelopmentServer();
            if (cached != null) return cached;
            throw new IllegalStateException("The current Spring backend could not be prepared. Check " + serverLogPath());
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }

        throw new IllegalStateException(isPackagedRuntime()
                ? "DSE ERP installation is incomplete: packaged Spring backend is missing."
                : "Spring backend JAR was not found and could not be built automatically. "
                    + "Run from the project root with Maven available, or set DSE_SERVER_JAR.");
    }

    /**
     * IntelliJ normally runs the desktop classes directly, so a packaged server JAR may not exist yet.
     * Build only the server reactor (and its shared dependency) on demand, then reuse the normal
     * packaged-server startup path. This is development-only and is never used by an installed app.
     */
    private static Path locateOrBuildDevelopmentServer() {
        Path root = findProjectRoot();
        if (root == null) return null;

        try {
            String fingerprint = developmentServerFingerprint(root);
            String shortFingerprint = fingerprint.substring(0, 16);
            String finalName = "dse-erp-server-dev-cache-" + shortFingerprint;
            Path cached = root.resolve("server/target/" + finalName + ".jar");

            if (Files.isRegularFile(cached) && isExecutableSpringBootJar(cached)) {
                return cached.toAbsolutePath().normalize();
            }

            return buildDevelopmentServer(root, finalName, cached);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Path buildDevelopmentServer(Path root, String finalName, Path expected) {
        try {
            List<String> command = new ArrayList<>();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            if (windows) {
                command.add("cmd.exe");
                command.add("/c");
                command.add("mvn");
            } else {
                command.add("mvn");
            }
            command.add("-q");
            command.add("-pl");
            command.add("server");
            command.add("-am");
            command.add("package");
            command.add("-DskipTests");
            command.add("-Ddse.server.finalName=" + finalName);

            Path log = serverLogPath();
            Files.createDirectories(log.getParent());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            Process build = builder.start();
            int exit = build.waitFor();
            if (exit != 0 || !Files.isRegularFile(expected) || !isExecutableSpringBootJar(expected)) return null;

            cleanupOldDevelopmentServerJars(expected);
            return expected.toAbsolutePath().normalize();
        } catch (Exception exception) {
            return null;
        }
    }

    private static String developmentServerFingerprint(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<Path> inputs = new ArrayList<>();

        for (Path fixed : List.of(
                root.resolve("pom.xml"),
                root.resolve("server/pom.xml"),
                root.resolve("shared/pom.xml"))) {
            if (Files.isRegularFile(fixed)) inputs.add(fixed);
        }

        for (Path sourceRoot : List.of(
                root.resolve("server/src"),
                root.resolve("shared/src"))) {
            if (!Files.exists(sourceRoot)) continue;
            try (var stream = Files.walk(sourceRoot)) {
                stream.filter(Files::isRegularFile).forEach(inputs::add);
            }
        }

        inputs.sort(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')));
        byte[] buffer = new byte[32 * 1024];
        for (Path input : inputs) {
            String relative = root.relativize(input).toString().replace('\\', '/');
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream in = Files.newInputStream(input)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            digest.update((byte) 0xff);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static boolean isExecutableSpringBootJar(Path jar) {
        try (java.util.jar.JarFile file = new java.util.jar.JarFile(jar.toFile())) {
            var manifest = file.getManifest();
            if (manifest == null) return false;
            String main = manifest.getMainAttributes().getValue("Main-Class");
            String start = manifest.getMainAttributes().getValue("Start-Class");
            return main != null && !main.isBlank() && start != null && !start.isBlank();
        } catch (IOException exception) {
            return false;
        }
    }

    private static void cleanupOldDevelopmentServerJars(Path keep) {
        Path target = keep.getParent();
        if (target == null) return;
        try (var stream = Files.list(target)) {
            stream.filter(path -> !path.equals(keep))
                    .filter(path -> path.getFileName().toString().startsWith("dse-erp-server-dev-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); }
                        catch (IOException ignored) { }
                    });
        } catch (IOException ignored) { }
    }

    private static Path findProjectRoot() {
        List<Path> starts = new ArrayList<>();
        starts.add(Path.of("").toAbsolutePath().normalize());
        try {
            Path code = Path.of(RuntimeBootstrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            starts.add(Files.isDirectory(code) ? code : code.getParent());
        } catch (Exception ignored) { }
        for (Path start : starts) {
            for (Path current = start; current != null; current = current.getParent()) {
                if (Files.isRegularFile(current.resolve("pom.xml"))
                        && Files.isRegularFile(current.resolve("server/pom.xml"))) return current;
            }
        }
        return null;
    }

    private static Process startServer(Path jar) {
        Path java = javaExecutable();
        Path log = serverLogPath();
        try {
            Files.createDirectories(log.getParent());
            ProcessBuilder builder = new ProcessBuilder(java.toString(), "-jar", jar.toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            builder.environment().put("DSE_DB_URL", ConfigManager.getDbUrl());
            builder.environment().put("DSE_DB_USERNAME", ConfigManager.getDbUsername());
            builder.environment().put("DSE_DB_PASSWORD", ConfigManager.getDbPassword());
            builder.environment().putIfAbsent("DSE_SERVER_PORT", serverPort());
            builder.environment().put("DSE_INTERNAL_BRIDGE_TOKEN", ConfigManager.getRuntimeInternalBridgeToken());
            return builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start packaged DSE ERP backend from " + jar, exception);
        }
    }

    private static void ensureInternalBridgeToken() {
        try {
            ConfigManager.getRuntimeInternalBridgeToken();
            return;
        } catch (IllegalStateException ignored) {
            byte[] bytes = new byte[32];
            SECURE_RANDOM.nextBytes(bytes);
            ConfigManager.applyRuntimeInternalBridgeToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        }
    }

    private static void prepareManagedServerEndpoint() {
        if (!isPackagedRuntime()) return;

        // Packaged DSE ERP owns its local Spring backend. Bundled config.properties
        // contains development fallback URLs and must never be treated as a user
        // override. Only explicit environment variables may opt into another API.
        if (System.getenv("DSE_AUTH_API_URL") != null || System.getenv("DSE_DATA_API_URL") != null) return;

        String current = ConfigManager.getDataApiBaseUrl();
        try {
            URI uri = URI.create(current);
            int port = uri.getPort() > 0 ? uri.getPort() : 8080;
            RuntimeApiClient.RuntimeStatus existing = tryStatus(new RuntimeApiClient());
            if (existing != null && existing.ready()) return;
            if (!isPortListening(port)) {
                ConfigManager.applyRuntimeApiBaseUrl("http://127.0.0.1:" + port);
                return;
            }
        } catch (Exception ignored) {}
        int port = findAvailableServerPort(DEFAULT_MANAGED_SERVER_PORT);
        ConfigManager.applyRuntimeApiBaseUrl("http://127.0.0.1:" + port);
    }

    private static boolean isLocalApiEndpoint() {
        try {
            URI uri = URI.create(ConfigManager.getDataApiBaseUrl());
            String host = uri.getHost();
            return host == null || host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1") || host.equals("::1");
        } catch (Exception ignored) {
            return true;
        }
    }

    private static int findAvailableServerPort(int start) {
        for (int port = start; port < start + SERVER_PORT_SEARCH_LIMIT; port++) {
            if (!isPortListening(port)) return port;
        }
        throw new IllegalStateException("No free local DSE ERP backend port was found between " + start + " and "
                + (start + SERVER_PORT_SEARCH_LIMIT - 1));
    }

    private static boolean isPortListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isPackagedRuntime() {
        return Boolean.parseBoolean(System.getProperty("dse.erp.packaged", "false"));
    }

    private static void verifyPackagedRuntime() {
        if (!isPackagedRuntime()) return;
        locateServerJar();
        ManagedPostgresRuntime.verifyBundledRuntime();
    }

    private static String serverPort() {
        try {
            URI uri = URI.create(ConfigManager.getDataApiBaseUrl());
            return Integer.toString(uri.getPort() > 0 ? uri.getPort() : 8080);
        } catch (Exception ignored) {
            return "8080";
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        if (!Files.isRegularFile(java)) throw new IllegalStateException("Bundled Java runtime executable not found: " + java);
        return java;
    }

    public static Path serverLogPath() {
        Path logs = WorkspaceManager.isConfigured()
                ? WorkspaceManager.getLogsFolder()
                : Path.of(System.getProperty("user.home"), ".dse-erp", "logs");
        return logs.resolve("dse-erp-server.log").toAbsolutePath().normalize();
    }

    public static synchronized void shutdownManagedServer() {
        if (!startedByDesktop || managedServer == null) return;
        Process process = managedServer;
        managedServer = null;
        startedByDesktop = false;
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
