package org.example.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceStartupPolicyTest {
    @Test
    void configuredWorkspaceDoesNotOpenChooserEvenWhenLegacySetupMarkerNeedsRepair() {
        assertFalse(Main.requiresWorkspaceChooser(true),
                "A saved valid workspace must start automatically; setup state is verified later by the server.");
    }

    @Test
    void missingWorkspaceStillOpensChooserForGenuineFirstRunOrRecovery() {
        assertTrue(Main.requiresWorkspaceChooser(false));
    }
}
