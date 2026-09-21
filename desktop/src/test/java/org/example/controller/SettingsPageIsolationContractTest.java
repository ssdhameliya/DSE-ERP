package org.example.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SettingsPageIsolationContractTest {
    @Test
    void settingsSaveIsBoundToTheActiveSectionOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/controller/SettingsController.java"));
        assertTrue(source.contains("private boolean saveValues(Section section)"));
        assertTrue(source.contains("switch (section)"));
        assertTrue(source.contains("case COMPANY -> { saveCompanyDetails(); commitPendingAssets(Section.COMPANY); }"));
        assertTrue(source.contains("case PAYMENT -> { savePaymentDetails(); commitPendingAssets(Section.PAYMENT); }"));
        assertTrue(source.contains("case INVOICE -> { saveInvoiceIdentity(); commitPendingAssets(Section.INVOICE); }"));
        String saveBlock = source.substring(source.indexOf("private boolean saveValues(Section section)"), source.indexOf("private void saveSecuritySettings()"));
        assertFalse(saveBlock.contains("loadedPanels.containsKey"));
        assertTrue(source.contains("pendingAssets.put(configKey"), "Images must be staged before page save");
    }
    @Test
    void testEmailActionIsVisibleOnlyOnEmailSettings() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/controller/SettingsController.java"));
        int start = source.indexOf("private void selectSection(");
        int end = source.indexOf("private void updateSaveButtonLabel()", start);
        String block = source.substring(start, end);
        assertTrue(block.contains("boolean emailSection = activeSection == Section.EMAIL"));
        assertTrue(block.contains("btnTestEmail.setVisible(emailSection)"));
        assertTrue(block.contains("btnTestEmail.setManaged(emailSection)"));
    }

}
