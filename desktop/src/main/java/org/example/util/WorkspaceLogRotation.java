package org.example.util;

import org.example.config.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Size rotation for active desktop-managed logs. Retention/deletion is server-owned. */
public final class WorkspaceLogRotation {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private WorkspaceLogRotation() { }

    public static void rotateIfNeeded(Path activeFile, long maxBytes, String component) throws IOException {
        if (activeFile == null || maxBytes <= 0 || !Files.isRegularFile(activeFile) || Files.size(activeFile) < maxBytes) return;
        Path archiveRoot = WorkspaceManager.isConfigured()
                ? WorkspaceManager.getArchivedLogsFolder()
                : activeFile.toAbsolutePath().normalize().getParent().resolve("Archive");
        Path targetFolder = archiveRoot.resolve(component == null || component.isBlank() ? "General" : component);
        Files.createDirectories(targetFolder);
        String name = activeFile.getFileName() == null ? "application.log" : activeFile.getFileName().toString();
        Path target = targetFolder.resolve(name + "." + LocalDateTime.now().format(STAMP));
        Files.move(activeFile, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
