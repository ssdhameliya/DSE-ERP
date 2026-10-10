package org.example.server.reporting;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReportScheduleRobustnessTest {

    @Test
    void reportScheduleOwnerQueryUsesExplicitColumnAliases() throws Exception {
        String code = Files.readString(Path.of("src/main/java/org/example/server/reporting/ReportScheduleService.java"));
        assertTrue(code.contains("AS role"), "Query must alias role");
        assertTrue(code.contains("AS active"), "Query must alias active");
        assertTrue(code.contains("AS locked"), "Query must alias locked");
        assertTrue(code.contains("AS approval_status"), "Query must alias approval_status");
        assertTrue(code.contains("toBoolean"), "Must use robust toBoolean helper");
    }

    @Test
    void pdfExportersDoNotUseDiskTempFilesToAvoidWindowsFileLocking() throws Exception {
        String scheduledExport = Files.readString(Path.of("src/main/java/org/example/server/reporting/ScheduledReportExportService.java"));
        String unifiedExport = Files.readString(Path.of("../desktop/src/main/java/org/example/service/UnifiedReportExportService.java"));

        assertFalse(scheduledExport.contains("Files.createTempFile"), "ScheduledReportExportService should not use disk temp files");
        assertTrue(scheduledExport.contains("ByteArrayOutputStream"), "ScheduledReportExportService should render via ByteArrayOutputStream");

        assertFalse(unifiedExport.contains("Files.createTempFile(target"), "UnifiedReportExportService should not use disk temp files for exports");
        assertTrue(unifiedExport.contains("ByteArrayOutputStream"), "UnifiedReportExportService should render via ByteArrayOutputStream");
    }

    @Test
    void smtpServiceProvidesIsConfiguredHelper() throws Exception {
        String smtp = Files.readString(Path.of("src/main/java/org/example/server/auth/SmtpMailService.java"));
        assertTrue(smtp.contains("public boolean isConfigured()"), "SmtpMailService must expose isConfigured()");
    }
}
