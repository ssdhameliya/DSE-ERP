package org.example.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePointerIsolationTest {
    @Test
    void transientTestWorkspaceDoesNotRewritePersistentPointer() throws Exception {
        Path pointer = WorkspaceManager.getPointerFolder().resolve("workspace.properties");
        byte[] before = Files.isRegularFile(pointer) ? Files.readAllBytes(pointer) : null;
        Path testRoot = Path.of("target", "workspace-pointer-isolation").toAbsolutePath();

        try (AutoCloseable ignored = WorkspaceTestSupport.useTransientWorkspace(testRoot)) {
            assertEquals(testRoot.normalize(), WorkspaceManager.getWorkspaceRoot());
            assertTrue(Files.isDirectory(testRoot.resolve("Documents")));
        }

        byte[] after = Files.isRegularFile(pointer) ? Files.readAllBytes(pointer) : null;
        assertArrayEquals(before, after, "Transient test workspace must never alter the persistent workspace pointer");
    }
}
