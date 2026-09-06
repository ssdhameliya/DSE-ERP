package org.example.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalRecoveryTargetContractTest {
    @Test
    void acceptsEmptyFolderAndExistingLocalButRejectsSharedWorkspace() throws Exception {
        Path root = Files.createTempDirectory("dse-local-recovery-target-");
        try {
            Path empty = root.resolve("empty");
            Files.createDirectories(empty);
            var emptyInspection = WorkspaceManager.inspectLocalRecoveryTarget(empty);
            assertTrue(emptyInspection.valid());
            assertFalse(emptyInspection.existingLocal());

            Path local = root.resolve("local");
            Files.createDirectories(local.resolve("Config"));
            Files.writeString(local.resolve("Config/config.properties"),
                    "deployment.mode=LOCAL\ndeployment.environment=LOCAL\nserver.baseUrl=\n");
            var localInspection = WorkspaceManager.inspectLocalRecoveryTarget(local);
            assertTrue(localInspection.valid());
            assertTrue(localInspection.existingLocal());

            Path shared = root.resolve("shared");
            Files.createDirectories(shared.resolve("Config"));
            Files.writeString(shared.resolve("Config/config.properties"),
                    "deployment.mode=SHARED_CLIENT\ndeployment.environment=UAT\nserver.baseUrl=https://example.test\n");
            var sharedInspection = WorkspaceManager.inspectLocalRecoveryTarget(shared);
            assertFalse(sharedInspection.valid());
        } finally {
            try (var walk = Files.walk(root)) {
                for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
