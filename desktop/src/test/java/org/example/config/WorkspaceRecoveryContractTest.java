package org.example.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceRecoveryContractTest {
    @Test
    void existingWorkspaceInspectionIsNonDestructiveAndRecognizesMarkers() throws Exception {
        Path root = Files.createTempDirectory("dse-existing-workspace-");
        try {
            Files.createDirectories(root.resolve("Config"));
            Files.createDirectories(root.resolve("Database/PostgreSQL/data"));
            Files.writeString(root.resolve("Config/config.properties"), "setup.completed=false\n");
            Files.writeString(root.resolve("Database/PostgreSQL/data/PG_VERSION"), "18\n");
            var result = WorkspaceManager.inspectExisting(root);
            assertTrue(result.valid());
            assertTrue(result.configPresent());
            assertTrue(result.databasePresent());
            assertTrue(result.postgresClusterPresent());
            assertEquals("false", java.util.Properties.class.cast(load(root.resolve("Config/config.properties"))).getProperty("setup.completed"));
        } finally {
            try (var walk = Files.walk(root)) { walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); }
        }
    }

    @Test
    void invalidFolderIsRejectedWithoutCreatingWorkspaceStructure() throws Exception {
        Path root = Files.createTempDirectory("dse-invalid-workspace-");
        try {
            var result = WorkspaceManager.inspectExisting(root);
            assertFalse(result.valid());
            assertFalse(Files.exists(root.resolve("Database")));
            assertFalse(Files.exists(root.resolve("Config")));
        } finally { Files.deleteIfExists(root); }
    }

    private static Object load(Path file) throws Exception {
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(file)) { p.load(in); }
        return p;
    }
}
