package org.example.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SafeRollbackResponsivenessContractTest {
    @Test
    void rollbackScreenRefreshRunsOutsideJavaFxThread() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/controller/SafeRollbackController.java"));
        int start = source.indexOf("private void refresh()");
        int end = source.indexOf("private void applyRollbackSnapshot", start);
        String block = source.substring(start, end);
        assertTrue(block.contains("UiTaskExecutor.submitLatest(\"safe-rollback-refresh\""));
        assertTrue(block.contains("service.candidates()"));
    }
}
