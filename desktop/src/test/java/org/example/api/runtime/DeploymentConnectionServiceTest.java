package org.example.api.runtime;

import org.example.config.DeploymentMode;
import org.junit.jupiter.api.Test;

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

    @Test void rejectsDatabaseUrlsCredentialsAndApiPaths() {
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("jdbc:postgresql://server/db"));
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("https://user:secret@server"));
        assertThrows(IllegalArgumentException.class, () -> DeploymentConnectionService.normalize("https://server/api/runtime/health"));
    }
}
