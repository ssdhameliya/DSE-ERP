package org.example.service;

import org.example.api.storage.StorageApiClient;
import org.example.config.WorkspaceManager;
import org.example.config.WorkspaceStorageManager;

import java.awt.Desktop;
import java.nio.file.Path;

/** Non-JavaFX workspace and storage operations used by Settings. */
public final class WorkspaceSettingsService {
    private WorkspaceSettingsService() { }

    public record Status(Path root, boolean pendingMove) { }

    public static Status status() {
        return new Status(WorkspaceManager.getWorkspaceRoot().toAbsolutePath().normalize(), WorkspaceManager.hasPendingMove());
    }

    public static void openWorkspaceFolder() throws Exception { open(WorkspaceManager.getWorkspaceRoot()); }
    public static void openDocumentsFolder() throws Exception { open(WorkspaceStorageManager.documentsRoot()); }
    public static void openReportsFolder() throws Exception { open(WorkspaceStorageManager.reportsRoot()); }
    public static void openLogsFolder() throws Exception { open(WorkspaceManager.getLogsFolder()); }

    public static Path exportDiagnostics() throws Exception { return DiagnosticBundleService.export(); }
    public static StorageApiClient.Status storageStatus() { return new StorageApiClient().status(); }
    public static StorageApiClient.CleanupResult previewCleanup() { return new StorageApiClient().previewCleanup(); }
    public static StorageApiClient.CleanupResult cleanNow() { return new StorageApiClient().cleanNow(); }

    public static WorkspaceManager.ExistingWorkspaceInspection inspectExisting(Path root) { return WorkspaceManager.inspectExisting(root); }
    public static void configureExisting(Path root) throws Exception { WorkspaceManager.configureExisting(root); }
    public static void stageMove(Path root) throws Exception { WorkspaceManager.stageMove(root); }

    private static void open(Path folder) throws Exception {
        if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Opening folders is not supported on this computer");
        Desktop.getDesktop().open(folder.toFile());
    }
}
