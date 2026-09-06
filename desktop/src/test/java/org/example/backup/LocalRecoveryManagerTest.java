package org.example.backup;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LocalRecoveryManagerTest {
    @Test
    void verifiesDatabaseChecksumAndExtractsBusinessFiles() throws Exception {
        Path dir = Files.createTempDirectory("dse-recovery-package-");
        try {
            Path zip = dir.resolve("recovery.zip");
            byte[] db = "synthetic-postgresql-backup-bytes-for-contract-test".getBytes();
            Properties manifest = new Properties();
            manifest.setProperty("format.version", "1");
            manifest.setProperty("createdAt", "2026-09-07T00:00:00Z");
            manifest.setProperty("application.version", "contract-test-version");
            manifest.setProperty("environment", "UAT");
            manifest.setProperty("database.name", "dse_erp_uat");
            manifest.setProperty("database.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(db)));
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("manifest.properties"));
                manifest.store(out, "test");
                out.closeEntry();
                out.putNextEntry(new ZipEntry("database.pgbackup"));
                out.write(db);
                out.closeEntry();
                out.putNextEntry(new ZipEntry("workspace/Attachments/a.txt"));
                out.write("attachment".getBytes());
                out.closeEntry();
                out.putNextEntry(new ZipEntry("workspace/Documents/d.txt"));
                out.write("document".getBytes());
                out.closeEntry();
                out.putNextEntry(new ZipEntry("workspace/Templates/t.txt"));
                out.write("template".getBytes());
                out.closeEntry();
            }
            Path extract = dir.resolve("extract");
            Properties parsed = LocalRecoveryManager.extractAndVerify(zip, extract);
            assertEquals("UAT", parsed.getProperty("environment"));
            assertArrayEquals(db, Files.readAllBytes(extract.resolve("database.pgbackup")));
            assertEquals("attachment", Files.readString(extract.resolve("workspace/Attachments/a.txt")));
            assertEquals("document", Files.readString(extract.resolve("workspace/Documents/d.txt")));
            assertEquals("template", Files.readString(extract.resolve("workspace/Templates/t.txt")));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    void rejectsZipSlipEntry() throws Exception {
        Path dir = Files.createTempDirectory("dse-recovery-zipslip-");
        try {
            Path zip = dir.resolve("bad.zip");
            byte[] db = "synthetic-postgresql-backup-bytes-for-contract-test".getBytes();
            Properties manifest = new Properties();
            manifest.setProperty("format.version", "1");
            manifest.setProperty("database.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(db)));
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("manifest.properties"));
                manifest.store(out, "test");
                out.closeEntry();
                out.putNextEntry(new ZipEntry("database.pgbackup"));
                out.write(db);
                out.closeEntry();
                out.putNextEntry(new ZipEntry("workspace/../escape.txt"));
                out.write("bad".getBytes());
                out.closeEntry();
            }
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> LocalRecoveryManager.extractAndVerify(zip, dir.resolve("extract")));
            assertTrue(error.getMessage().contains("Unsafe recovery package path"));
        } finally {
            deleteTree(dir);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
