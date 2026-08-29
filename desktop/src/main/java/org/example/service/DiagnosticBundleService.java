package org.example.service;

import org.example.api.runtime.RuntimeBootstrapper;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.shared.RuntimeContract;
import org.example.model.AppUser;
import org.example.util.DesktopLog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a support-safe diagnostics ZIP. Business documents/database contents are never included. */
public final class DiagnosticBundleService {
    private static final long MAX_LOG_BYTES = 5L * 1024L * 1024L;
    private DiagnosticBundleService() { }

    public static Path export() {
        try {
            Path folder = WorkspaceManager.getExportsFolder().resolve("Diagnostics");
            Files.createDirectories(folder);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
            Path zip = folder.resolve("DSE-ERP-Diagnostics-" + stamp + ".zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip, StandardOpenOption.CREATE_NEW))) {
                writeText(out, "diagnostic-info.properties", info());
                writeText(out, "config-sanitized.properties", sanitizedConfig());
                addLog(out, DesktopLog.path(), "logs/desktop.log");
                addLog(out, RuntimeBootstrapper.serverLogPath(), "logs/server.log");
                if (WorkspaceManager.isConfigured()) addLog(out, WorkspaceManager.getLogsFolder().resolve("postgresql.log"), "logs/postgresql.log");
            }
            DesktopLog.info("Diagnostics", "BUNDLE_EXPORTED", zip.toString());
            return zip;
        } catch (IOException exception) {
            throw new IllegalStateException("Diagnostic package could not be created", exception);
        }
    }

    private static String info() {
        Properties p = new Properties();
        p.setProperty("generatedAt", Instant.now().toString());
        p.setProperty("applicationVersion", RuntimeContract.APP_VERSION);
        p.setProperty("buildRevision", RuntimeContract.BUILD_REVISION);
        p.setProperty("apiRevision", RuntimeContract.API_REVISION);
        p.setProperty("javaVersion", System.getProperty("java.version", ""));
        p.setProperty("javaVendor", System.getProperty("java.vendor", ""));
        p.setProperty("osName", System.getProperty("os.name", ""));
        p.setProperty("osVersion", System.getProperty("os.version", ""));
        p.setProperty("osArch", System.getProperty("os.arch", ""));
        p.setProperty("deploymentMode", ConfigManager.isSharedClient() ? "SHARED_CLIENT" : "LOCAL");
        p.setProperty("workspaceConfigured", Boolean.toString(WorkspaceManager.isConfigured()));
        if (WorkspaceManager.isConfigured()) p.setProperty("workspace", WorkspaceManager.getWorkspaceRoot().toString());
        AppUser user = SessionService.current();
        if (user != null) {
            p.setProperty("user", Objects.toString(user.getUsername(), ""));
            p.setProperty("role", Objects.toString(user.getRole(), ""));
        }
        return propertiesText(p);
    }

    private static String sanitizedConfig() {
        Properties safe = new Properties();
        Path file = WorkspaceManager.getConfigurationFolder().resolve("config.properties");
        if (!Files.isRegularFile(file)) return "# config.properties is not present\n";
        try (InputStream in = Files.newInputStream(file)) {
            Properties source = new Properties(); source.load(in);
            for (String key : source.stringPropertyNames()) {
                String lower = key.toLowerCase(Locale.ROOT);
                if (lower.matches(".*(password|secret|token|credential|smtp\\.password).*")) safe.setProperty(key, "<redacted>");
                else safe.setProperty(key, source.getProperty(key, ""));
            }
            return propertiesText(safe);
        } catch (IOException e) {
            return "# Unable to read config.properties: " + e.getMessage() + "\n";
        }
    }

    private static String propertiesText(Properties properties) {
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "DSE ERP diagnostics");
            return writer.toString();
        } catch (IOException impossible) { return ""; }
    }

    private static void writeText(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void addLog(ZipOutputStream out, Path path, String name) throws IOException {
        if (path == null || !Files.isRegularFile(path)) return;
        byte[] bytes = tail(path, MAX_LOG_BYTES);
        out.putNextEntry(new ZipEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    private static byte[] tail(Path path, long maxBytes) throws IOException {
        long size = Files.size(path), start = Math.max(0, size - maxBytes), length = size - start;
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(start);
            byte[] data = new byte[(int) length];
            raf.readFully(data);
            return data;
        }
    }
}
