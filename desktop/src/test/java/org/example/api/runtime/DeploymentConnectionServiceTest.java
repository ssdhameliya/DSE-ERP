package org.example.api.runtime;

import org.example.config.DeploymentMode;
import org.example.shared.RuntimeContract;
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

    @Test void compatibleOlderDesktopMayConnectToNewerServer() {
        RuntimeApiClient.RuntimeStatus status = status("9.1.2", "build-912", "9.0.95", RuntimeContract.API_REVISION);
        assertDoesNotThrow(() -> DeploymentConnectionService.validateCompatibility(status, "9.0.96", "build-96"));
        assertTrue(DeploymentConnectionService.isCompatibleClientUpdateAvailable(status, "9.0.96"));
        assertEquals("9.0.95", DeploymentConnectionService.effectiveMinimumSupportedDesktopVersion(status));
    }


    @Test void tenZeroOneMayRemainCompatibleWithTenZeroTenServer() {
        RuntimeApiClient.RuntimeStatus status = status("10.0.10", "10.0.10", "10.0.1", RuntimeContract.API_REVISION);
        assertDoesNotThrow(() -> DeploymentConnectionService.validateCompatibility(status, "10.0.1", "10.0.1"));
        assertTrue(DeploymentConnectionService.isCompatibleClientUpdateAvailable(status, "10.0.1"));
        assertEquals("10.0.1", DeploymentConnectionService.effectiveMinimumSupportedDesktopVersion(status));
    }

    @Test void desktopBelowServerMinimumRequiresUpdate() {
        RuntimeApiClient.RuntimeStatus status = status("9.1.2", "build-912", "9.0.95", RuntimeContract.API_REVISION);
        DeploymentConnectionService.ClientUpdateRequiredException failure = assertThrows(
                DeploymentConnectionService.ClientUpdateRequiredException.class,
                () -> DeploymentConnectionService.validateCompatibility(status, "9.0.94", "build-94"));
        assertEquals("9.1.2", failure.requiredVersion());
        assertEquals("9.0.95", failure.minimumSupportedDesktopVersion());
    }

    @Test void missingMinimumUsesStrictServerVersionFallback() {
        RuntimeApiClient.RuntimeStatus status = status("9.1.2", "build-912", null, RuntimeContract.API_REVISION);
        assertEquals("9.1.2", DeploymentConnectionService.effectiveMinimumSupportedDesktopVersion(status));
        assertThrows(DeploymentConnectionService.ClientUpdateRequiredException.class,
                () -> DeploymentConnectionService.validateCompatibility(status, "9.1.1", "build-911"));
    }

    @Test void apiRevisionMismatchAlwaysBlocksEvenInsideVersionWindow() {
        RuntimeApiClient.RuntimeStatus status = status("9.1.2", "build-912", "9.0.90", "different-api");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeploymentConnectionService.validateCompatibility(status, "9.1.1", "build-911"));
        assertTrue(failure.getMessage().contains("API is not compatible"));
    }

    @Test void sameVersionStillRequiresExactBuild() {
        RuntimeApiClient.RuntimeStatus status = status("9.1.2", "server-build", "9.0.95", RuntimeContract.API_REVISION);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeploymentConnectionService.validateCompatibility(status, "9.1.2", "desktop-build"));
        assertTrue(failure.getMessage().contains("build is not compatible"));
    }

    @Test void newerDesktopCannotSilentlyUseOlderServer() {
        RuntimeApiClient.RuntimeStatus status = status("9.1.1", "build-911", "9.0.95", RuntimeContract.API_REVISION);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeploymentConnectionService.validateCompatibility(status, "9.1.2", "build-912"));
        assertTrue(failure.getMessage().contains("company server is older"));
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
        assertTrue(main.contains("offerCompatibleClientUpdate"));
        assertTrue(main.contains("offerRequiredClientUpdateAtStartup"));
    }

    @Test void rejectsDatabaseUrlsCredentialsAndApiPaths() {
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("jdbc:postgresql://server/db"));
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("https://user:secret@server"));
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("https://server/api/runtime/health"));
    }

    private static RuntimeApiClient.RuntimeStatus status(String version, String build, String minimum, String apiRevision) {
        return new RuntimeApiClient.RuntimeStatus(
                true,
                RuntimeContract.SERVICE_NAME,
                version,
                apiRevision,
                build,
                minimum,
                "UAT",
                "postgresql",
                "dse_erp_uat",
                "READY",
                "Asia/Kolkata",
                "2026-09-10",
                "2026-09-10T00:00:00Z",
                "dd/MM/yyyy",
                "ISO_DATE_UTC_INSTANT",
                "UTC");
    }
}
