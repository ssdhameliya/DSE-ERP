package org.example.server.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.example.server.persistence.JpaNativeRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageMaintenanceServiceTest {
    @TempDir Path temp;

    @Test
    void scheduledCleanupNeverDeletesBusinessEvidence() throws Exception {
        JpaNativeRepository jdbc = mock(JpaNativeRepository.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), any())).thenThrow(new RuntimeException("defaults"));
        StorageMaintenanceService service = new StorageMaintenanceService(jdbc, temp.toString());
        service.initializeFolders();

        Path document = oldFile("Documents/Sales/2026-27/Tax-Invoices/S-1/invoice.pdf", 500);
        Path attachment = oldFile("Attachments/SALE/1/proof.pdf", 500);
        Path backup = oldFile("Backups/weekly.pgbackup", 500);
        Path database = oldFile("Database/PostgreSQL/data/business.dat", 500);

        Path report = oldFile("Reports/Sales/2025-26/old.pdf", 500);
        Path export = oldFile("Exports/CSV/old.csv", 500);
        Path importResult = oldFile("Imports/Results/old.xlsx", 500);
        Path tempFile = oldFile("Temp/old.tmp", 500);
        Path log = oldFile("Logs/Archive/Server/old.log", 500);

        service.scheduledCleanup();

        assertTrue(Files.exists(document), "business documents are permanent");
        assertTrue(Files.exists(attachment), "attachments are permanent");
        assertTrue(Files.exists(backup), "backups use their own policy");
        assertTrue(Files.exists(database), "database records/files are never cleanup targets");

        assertFalse(Files.exists(report));
        assertFalse(Files.exists(export));
        assertFalse(Files.exists(importResult));
        assertFalse(Files.exists(tempFile));
        assertFalse(Files.exists(log));
    }

    @Test
    void scheduledCleanupCompressesEligibleOldArchivedLogs() throws Exception {
        JpaNativeRepository jdbc = mock(JpaNativeRepository.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), any())).thenThrow(new RuntimeException("defaults"));
        StorageMaintenanceService service = new StorageMaintenanceService(jdbc, temp.toString());
        service.initializeFolders();

        Path log = file("Logs/Archive/Server/server.log.1", "0123456789");
        Files.setLastModifiedTime(log, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        service.scheduledCleanup();

        assertFalse(Files.exists(log));
        assertTrue(Files.exists(log.resolveSibling(log.getFileName() + ".gz")));
    }

    private Path oldFile(String relative, long days) throws Exception {
        Path p = file(relative, "evidence");
        Files.setLastModifiedTime(p, FileTime.from(Instant.now().minus(days, ChronoUnit.DAYS)));
        return p;
    }

    private Path file(String relative, String content) throws Exception {
        Path p = temp.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p;
    }
}
