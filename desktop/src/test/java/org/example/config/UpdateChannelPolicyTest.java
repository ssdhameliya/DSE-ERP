package org.example.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateChannelPolicyTest {
    @Test
    void sharedUatAlwaysUsesBetaEvenWhenStoredChannelIsStable() {
        if (hasDeploymentOverrides()) return;
        ConfigManager.setWithoutSaving("deployment.mode", "SHARED_CLIENT");
        ConfigManager.setWithoutSaving("deployment.environment", "UAT");
        ConfigManager.setWithoutSaving("update.channel", "STABLE");
        try {
            assertTrue(ConfigManager.isUpdateChannelManagedByEnvironment());
            assertEquals("BETA", ConfigManager.getEffectiveUpdateChannel());
        } finally {
            clear();
        }
    }

    @Test
    void sharedProdAlwaysUsesStableEvenWhenStoredChannelIsBeta() {
        if (hasDeploymentOverrides()) return;
        ConfigManager.setWithoutSaving("deployment.mode", "SHARED_CLIENT");
        ConfigManager.setWithoutSaving("deployment.environment", "PROD");
        ConfigManager.setWithoutSaving("update.channel", "BETA");
        try {
            assertTrue(ConfigManager.isUpdateChannelManagedByEnvironment());
            assertEquals("STABLE", ConfigManager.getEffectiveUpdateChannel());
        } finally {
            clear();
        }
    }

    @Test
    void localInstallationRetainsManualStableOrBetaChoice() {
        if (System.getenv("DSE_DEPLOYMENT_MODE") != null) return;
        ConfigManager.setWithoutSaving("deployment.mode", "LOCAL");
        try {
            ConfigManager.setWithoutSaving("update.channel", "BETA");
            assertFalse(ConfigManager.isUpdateChannelManagedByEnvironment());
            assertEquals("BETA", ConfigManager.getEffectiveUpdateChannel());
            ConfigManager.setWithoutSaving("update.channel", "STABLE");
            assertEquals("STABLE", ConfigManager.getEffectiveUpdateChannel());
        } finally {
            clear();
        }
    }


    @Test
    void legacySharedClientConfigurationIsSelfHealedToItsEnvironmentChannelOnLoad() throws Exception {
        if (hasDeploymentOverrides()) return;
        assertPersistedSharedChannel("UAT", "STABLE", "BETA");
        assertPersistedSharedChannel("PROD", "BETA", "STABLE");
    }

    private static void assertPersistedSharedChannel(String environment, String storedChannel, String expected) throws Exception {
        Path root = Files.createTempDirectory("dse-update-channel-");
        try {
            Files.createDirectories(root.resolve("Config"));
            Files.writeString(root.resolve("Config/config.properties"), String.join("\n",
                    "deployment.mode=SHARED_CLIENT",
                    "deployment.environment=" + environment,
                    "server.baseUrl=https://example.invalid",
                    "update.channel=" + storedChannel,
                    "setup.completed=true",
                    ""));
            try (AutoCloseable ignored = WorkspaceTestSupport.useTransientWorkspace(root)) {
                ConfigManager.load();
                assertEquals(expected, ConfigManager.getEffectiveUpdateChannel());
                Properties persisted = new Properties();
                try (var in = Files.newInputStream(root.resolve("Config/config.properties"))) {
                    persisted.load(in);
                }
                assertEquals(expected, persisted.getProperty("update.channel"));
                assertEquals("external", persisted.getProperty("runtime.postgres.mode"));
            }
        } finally {
            clear();
            try (var walk = Files.walk(root)) {
                for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static boolean hasDeploymentOverrides() {
        return System.getenv("DSE_DEPLOYMENT_MODE") != null
                || System.getenv("DSE_DEPLOYMENT_ENVIRONMENT") != null;
    }

    private static void clear() {
        ConfigManager.setWithoutSaving("deployment.mode", null);
        ConfigManager.setWithoutSaving("deployment.environment", null);
        ConfigManager.setWithoutSaving("update.channel", null);
    }
}
