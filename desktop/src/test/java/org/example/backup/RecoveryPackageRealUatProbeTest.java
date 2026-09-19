package org.example.backup;

import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="dse.uat.recovery.package", matches=".+")
class RecoveryPackageRealUatProbeTest {
    @TempDir Path temp;
    @Test
    void realRecoveredUatPackageAcceptsPortableSettingsLayerAndStillVerifies() throws Exception {
        Path source=Path.of(System.getProperty("dse.uat.recovery.package"));
        Path copy=temp.resolve("uat-recovery-"+org.example.update.BuildInfo.version()+"-test.zip"); Files.copy(source,copy);
        Path workspace=temp.resolve("workspace"); Files.createDirectories(workspace);
        try(AutoCloseable ignored= WorkspaceTestSupport.useTransientWorkspace(workspace)){
            ConfigManager.load();
            ConfigManager.set("application.displayName","DSE ERP UAT Recovery Probe");
            ConfigManager.set("shortcut.session.dashboard","F11");
            ConfigManager.set("server.baseUrl","https://should-not-be-portable.invalid");
            Path mark=ConfigManager.getConfigurationFolder().resolve("app-mark.png"); Files.write(mark,new byte[]{1,2,3,4}); ConfigManager.set("application.markImagePath",mark.toString());
            PortableRecoverySettings.augment(copy);
            Path extract=temp.resolve("extract");
            Properties manifest=LocalRecoveryManager.extractAndVerify(copy,extract);
            assertTrue(Files.isRegularFile(extract.resolve("database.pgbackup")));
            assertTrue(Files.isRegularFile(extract.resolve("settings/portable.properties")));
            assertTrue(Files.isRegularFile(extract.resolve("settings/assets/application-mark.png")));
            Properties portable=new Properties(); try(InputStream in=Files.newInputStream(extract.resolve("settings/portable.properties"))){portable.load(in);}
            assertFalse(portable.containsKey("server.baseUrl"));
            System.out.println("RECOVERY_UAT_VERIFY environment="+manifest.getProperty("environment")+" sourceVersion="+manifest.getProperty("application.version")+" database="+manifest.getProperty("database.name")+" portableKeys="+portable.size()+" settingsAsset=true");
        }
    }
}
