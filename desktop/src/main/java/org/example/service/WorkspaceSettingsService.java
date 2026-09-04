package org.example.service;

import org.example.config.WorkspaceManager;

import java.awt.Desktop;
import java.nio.file.Path;

/** Non-JavaFX workspace operations used by Settings. */
public final class WorkspaceSettingsService {
    private WorkspaceSettingsService() { }

    public record Status(Path root, boolean pendingMove) { }

    public static Status status() {
        return new Status(WorkspaceManager.getWorkspaceRoot().toAbsolutePath().normalize(), WorkspaceManager.hasPendingMove());
    }

    public static void openWorkspaceFolder() throws Exception {
        Desktop.getDesktop().open(WorkspaceManager.getWorkspaceRoot().toFile());
    }

    public static Path exportDiagnostics() throws Exception {
        return DiagnosticBundleService.export();
    }

    public static WorkspaceManager.ExistingWorkspaceInspection inspectExisting(Path root) {
        return WorkspaceManager.inspectExisting(root);
    }

    public static void configureExisting(Path root) throws Exception {
        WorkspaceManager.configureExisting(root);
    }

    public static void stageMove(Path root) throws Exception {
        WorkspaceManager.stageMove(root);
    }
}
