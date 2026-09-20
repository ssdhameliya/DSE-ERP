package org.example.server.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalHardeningMigrationContractTest {
    @Test
    void duplicateRunCleanupRemainsCompatibleWithLegacyTextTimestamps() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V10_0_18__final_defect_hardening.sql")) {
            assertTrue(in != null, "final hardening migration must be packaged");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CURRENT_TIMESTAMP::text"),
                    "report_schedule_run.finished_at is TEXT in upgraded databases");
        }
    }
}
