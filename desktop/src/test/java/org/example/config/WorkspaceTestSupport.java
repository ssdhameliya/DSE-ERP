package org.example.config;

import java.io.IOException;
import java.nio.file.Path;

/** Test helper that isolates evidence workspaces without mutating the OS-level workspace pointer. */
public final class WorkspaceTestSupport {
    private WorkspaceTestSupport() {}

    public static AutoCloseable useTransientWorkspace(Path root) throws IOException {
        Path previous = WorkspaceManager.configureTransientForTesting(root);
        return () -> WorkspaceManager.restoreTransientForTesting(previous);
    }
}
