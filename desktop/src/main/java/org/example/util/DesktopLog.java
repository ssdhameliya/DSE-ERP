package org.example.util;

import org.example.config.WorkspaceManager;
import org.example.service.SessionService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Small structured desktop log that is independent of external logging configuration.
 * One JSON line per event makes support bundles machine-readable while keeping startup safe.
 */
public final class DesktopLog {
    private static final long MAX_BYTES = 8L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static volatile Path file;

    private DesktopLog() { }

    public static void initialize() {
        synchronized (LOCK) {
            try {
                Path folder = WorkspaceManager.isConfigured()
                        ? WorkspaceManager.getLogsFolder()
                        : Path.of(System.getProperty("user.home", "."), ".dse-erp", "Logs");
                Files.createDirectories(folder);
                file = folder.resolve("desktop.log");
                rotateIfNeeded();
                info("Desktop", "LOG_READY", "Structured desktop logging initialized");
            } catch (Exception ignored) {
                file = null; // Logging must never prevent the ERP from opening.
            }
        }
    }

    public static Path path() { return file; }
    public static void info(String component, String event, String detail) { write("INFO", component, event, detail, null); }
    public static void warn(String component, String event, String detail) { write("WARN", component, event, detail, null); }
    public static void error(String component, String event, String detail, Throwable failure) { write("ERROR", component, event, detail, failure); }

    private static void write(String level, String component, String event, String detail, Throwable failure) {
        Path target = file;
        if (target == null) return;
        synchronized (LOCK) {
            try {
                rotateIfNeeded();
                String user = SessionService.current() == null ? "" : Objects.toString(SessionService.current().getUsername(), "");
                String failureText = failure == null ? "" : failure.getClass().getSimpleName() + ": " + Objects.toString(rootMessage(failure), "");
                String line = "{\"time\":\"" + esc(Instant.now().toString()) + "\",\"level\":\"" + esc(level)
                        + "\",\"component\":\"" + esc(component) + "\",\"event\":\"" + esc(event)
                        + "\",\"user\":\"" + esc(user) + "\",\"detail\":\"" + esc(detail)
                        + "\",\"failure\":\"" + esc(failureText) + "\"}" + System.lineSeparator();
                Files.writeString(target, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) { }
        }
    }

    private static void rotateIfNeeded() throws IOException {
        if (file == null || !Files.exists(file) || Files.size(file) < MAX_BYTES) return;
        Path previous = file.resolveSibling("desktop.log.1");
        Files.deleteIfExists(previous);
        Files.move(file, previous, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root == null ? "" : Objects.toString(root.getMessage(), root.getClass().getSimpleName());
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
}
