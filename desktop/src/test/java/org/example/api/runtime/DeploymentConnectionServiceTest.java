package org.example.api.runtime;

import org.example.config.DeploymentMode;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentConnectionServiceTest {
    @Test void localRemainsTheBackwardCompatibleDefault() {
        assertEquals(DeploymentMode.LOCAL, DeploymentMode.parse(null));
        assertEquals(DeploymentMode.LOCAL, DeploymentMode.parse("unknown"));
        assertEquals(DeploymentMode.LOCAL, DeploymentMode.parse("local"));
        assertEquals(DeploymentMode.SHARED_CLIENT, DeploymentMode.parse("shared_client"));
    }

    @Test void normalizesSupportedCompanyServerAddresses() {
        assertEquals("https://erp.company.local", DeploymentConnectionService.normalize(" https://erp.company.local/ "));
        assertEquals("http://192.168.1.50:8080", DeploymentConnectionService.normalize("http://192.168.1.50:8080"));
    }

    @Test void selectedEnvironmentIsValidatedBeforeSavingAChangedSharedProfile() {
        assertDoesNotThrow(() -> DeploymentConnectionService.validateEnvironment("PROD", "PROD"));
        assertDoesNotThrow(() -> DeploymentConnectionService.validateEnvironment("UAT", "LOCAL"));
        IllegalStateException mismatch = assertThrows(IllegalStateException.class,
                () -> DeploymentConnectionService.validateEnvironment("UAT", "PROD"));
        assertTrue(mismatch.getMessage().contains("Selected environment is PROD"));
    }

    @Test void connectionFailureDiagnosticDoesNotClaimItCanStartARemoteServer() {
        String message = RuntimeApiClient.connectionFailureMessage(
                "https://api.example.test", new ConnectException("Connection refused"));
        assertTrue(message.contains("Connection refused"));
        assertFalse(message.contains("attempt to start"));
        assertFalse(message.contains("automatically"));
    }

    @Test void sharedStartupWrapperDoesNotClaimItCanStartTheRemoteServer() throws Exception {
        String main = Files.readString(Path.of("src/main/java/org/example/app/Main.java"));
        assertTrue(main.contains("Could not connect to the company server."));
        assertTrue(main.contains("The Shared Client does not start or replace the remote company server."));
        assertTrue(main.contains("String startupMessage = ConfigManager.isSharedClient()"));
    }

    @Test void rejectsDatabaseUrlsCredentialsAndApiPaths() {
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("jdbc:postgresql://server/db"));
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("https://user:secret@server"));
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("https://server/api/runtime/health"));
    }
}
