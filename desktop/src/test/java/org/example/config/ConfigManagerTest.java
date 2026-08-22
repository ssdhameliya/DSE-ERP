package org.example.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {
    @Test
    void ordinarySettingLookupDoesNotReenterDeploymentModeDetection() {
        assertDoesNotThrow(() ->
                org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                        assertEquals("fallback", ConfigManager.get("test.unmapped.setting", "fallback"))));
    }

    @Test
    void configuredSharedClientModeCanBeReadWithoutRecursion() {
        if (System.getenv("DSE_DEPLOYMENT_MODE") != null) return;

        ConfigManager.setWithoutSaving("deployment.mode", "SHARED_CLIENT");
        try {
            assertEquals(DeploymentMode.SHARED_CLIENT, ConfigManager.getDeploymentMode());
        } finally {
            ConfigManager.setWithoutSaving("deployment.mode", null);
        }
    }
}
