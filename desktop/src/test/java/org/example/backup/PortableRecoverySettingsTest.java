package org.example.backup;

import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class PortableRecoverySettingsTest {
    @TempDir Path temp;

    @Test
    void recoveryPackageIncludesPortableSettingsButNotDeploymentCredentials() throws Exception {
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(workspace);
        try (AutoCloseable ignored = WorkspaceTestSupport.useTransientWorkspace(workspace)) {
            ConfigManager.load();
            ConfigManager.set("application.displayName", "Recovered ERP");
            ConfigManager.set("update.checkAtStartup", "false");
            ConfigManager.set("shortcut.test.action", "F9");
            ConfigManager.set("server.baseUrl", "https://must-not-backup.example");
            ConfigManager.set("db.password", "must-not-backup");
            Path brand = ConfigManager.getConfigurationFolder().resolve("brand.png");
            Files.write(brand, new byte[]{1,2,3,4});
            ConfigManager.set("application.brandImagePath", brand.toString());

            Path zip = temp.resolve("recovery.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                out.putNextEntry(new ZipEntry("manifest.properties")); out.write("format.version=1\n".getBytes()); out.closeEntry();
                out.putNextEntry(new ZipEntry("database.pgbackup")); out.write(new byte[256]); out.closeEntry();
            }
            PortableRecoverySettings.augment(zip);
            try (ZipFile result = new ZipFile(zip.toFile())) {
                assertNotNull(result.getEntry("settings/portable.properties"));
                assertNotNull(result.getEntry("settings/assets/application-brand.png"));
                Properties p = new Properties();
                try (InputStream in = result.getInputStream(result.getEntry("settings/portable.properties"))) { p.load(in); }
                assertEquals("Recovered ERP", p.getProperty("application.displayName"));
                assertEquals("F9", p.getProperty("shortcut.test.action"));
                assertFalse(p.containsKey("server.baseUrl"));
                assertFalse(p.containsKey("db.password"));
            }
        }
    }
}
