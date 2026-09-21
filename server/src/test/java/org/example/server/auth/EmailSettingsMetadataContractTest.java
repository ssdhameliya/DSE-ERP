package org.example.server.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmailSettingsMetadataContractTest {
    @Test
    void adminSettingsGetUsesNonDecryptingMetadataPath() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/server/authority/BusinessEmailController.java"));
        assertTrue(controller.contains("mail.currentSettingsSummary()"));
        assertFalse(controller.contains("var current = mail.currentSettings();"));
    }
}
