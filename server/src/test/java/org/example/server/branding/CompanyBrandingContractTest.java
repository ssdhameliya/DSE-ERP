package org.example.server.branding;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CompanyBrandingContractTest {
    @Test void serverGeneratedEmailAndReportsUseCompanyIdentity() throws Exception {
        String smtp = Files.readString(Path.of("src/main/java/org/example/server/auth/SmtpMailService.java"));
        String email = Files.readString(Path.of("src/main/java/org/example/server/authority/BusinessEmailController.java"));
        String scheduled = Files.readString(Path.of("src/main/java/org/example/server/reporting/ReportScheduleService.java"));
        String export = Files.readString(Path.of("src/main/java/org/example/server/reporting/ScheduledReportExportService.java"));
        assertTrue(smtp.contains("public String companyName()"));
        assertTrue(smtp.contains("companyName() + \" \" + purpose + \" code\""));
        assertTrue(email.contains("mail.companyName() + \" email test\""));
        assertTrue(scheduled.contains("mail.companyName() + \" Scheduled Report - \""));
        assertTrue(export.contains("setting(\"company.name\", \"Company\") + \" | \" + result.title()"));
    }

    @Test void documentFallbacksNoLongerLeakLegacyCompanyNames() throws Exception {
        String renderer = Files.readString(Path.of("src/main/java/org/example/util/ProfessionalDocumentRenderer.java"));
        assertFalse(renderer.contains("DSE ERP SOLUTIONS PVT. LTD."));
        assertFalse(renderer.contains("DSE Engineers"));
        assertFalse(renderer.contains("JavaApp ERP"));
    }
}
