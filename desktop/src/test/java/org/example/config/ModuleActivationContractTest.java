package org.example.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleActivationContractTest {

    @Test
    void testModuleFlagsDefaultToTrue() {
        assertTrue(ConfigManager.getBoolean("module.gl.enabled", true), "GL module should default to enabled");
        assertTrue(ConfigManager.getBoolean("module.gst.enabled", true), "GST module should default to enabled");
        assertTrue(ConfigManager.getBoolean("module.automation.threeWayMatch", true), "3-way match should default to enabled");
        assertTrue(ConfigManager.getBoolean("module.briefing.daily", true), "Daily briefing should default to enabled");
        assertTrue(ConfigManager.getBoolean("module.inventory.batchSerialTracking", true), "Batch tracking should default to enabled");
    }

    @Test
    void testModuleFlagsCanBeToggled() {
        String testKey = "module.gl.enabled";
        try {
            ConfigManager.setWithoutSaving(testKey, "false");
            assertFalse(ConfigManager.getBoolean(testKey, true), "Flag must be toggleable to false");

            ConfigManager.setWithoutSaving(testKey, "true");
            assertTrue(ConfigManager.getBoolean(testKey, false), "Flag must be toggleable to true");
        } finally {
            ConfigManager.setWithoutSaving(testKey, null);
        }
    }

    @Test
    void testAllModuleKeysAreConfigurable() {
        String[] keys = {
                "module.gl.enabled", "module.gl.autoPost", "module.gl.trialBalance",
                "module.gst.enabled", "module.gst.auto2bMatch", "module.gst.posGating",
                "module.tds.enabled",
                "module.automation.threeWayMatch", "module.automation.autoDraftDebitNote",
                "module.automation.stockAutoReorder", "module.automation.creditHardStop",
                "module.briefing.daily", "module.briefing.whatsapp", "module.briefing.email",
                "module.inventory.batchSerialTracking"
        };

        for (String key : keys) {
            boolean value = ConfigManager.getBoolean(key, true);
            assertTrue(value, "Module key " + key + " must be readable with default true");
        }
    }
}
