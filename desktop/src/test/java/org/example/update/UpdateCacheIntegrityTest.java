package org.example.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Regression for the 10.0.11 stale-installer same-size cache failure. */
class UpdateCacheIntegrityTest {
    @TempDir Path temp;

    @Test
    void sameSizeWrongInstallerIsNeverTrustedAndPurgeRemovesPartialToo() throws Exception {
        Path installer=temp.resolve("DSE-ERP-current-Windows-x64.exe");
        byte[] official="official-installer".getBytes();
        byte[] stale="stale----installer".getBytes();
        assertEquals(official.length,stale.length,"fixture must reproduce same-size stale cache");
        Files.write(installer,stale);
        Files.writeString(installer.resolveSibling(installer.getFileName()+".part"),"partial");
        String expected=sha256(temp.resolve("official.bin"),official);

        assertFalse(UpdateService.cachedInstallerMatches(installer,official.length,expected));
        UpdateService.purgeCachedInstaller(installer);
        assertFalse(Files.exists(installer));
        assertFalse(Files.exists(installer.resolveSibling(installer.getFileName()+".part")));
    }

    @Test
    void correctlyHashedCachedInstallerCanBeReused() throws Exception {
        Path installer=temp.resolve("DSE-ERP-current-macOS-arm64.dmg");
        byte[] official="verified-installer".getBytes();
        Files.write(installer,official);
        String expected=ChecksumVerifier.sha256(installer);
        assertTrue(UpdateService.cachedInstallerMatches(installer,official.length,expected));
    }

    private static String sha256(Path file,byte[] bytes) throws Exception {
        Files.write(file,bytes);
        return ChecksumVerifier.sha256(file);
    }
}
