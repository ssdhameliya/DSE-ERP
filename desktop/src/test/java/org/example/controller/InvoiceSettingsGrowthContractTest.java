package org.example.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceSettingsGrowthContractTest {
    @Test
    void addressAndTermsEditorsCanUseAvailableVerticalSpace() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/pages/settings/InvoiceSettingsPanel.fxml"));
        assertTrue(fxml.contains("fx:id=\"txtCompanyAddress\" prefRowCount=\"4\""));
        assertTrue(fxml.contains("fx:id=\"txtShipAddress\" prefRowCount=\"4\""));
        assertTrue(fxml.contains("fx:id=\"txtInvoiceTerms\" prefRowCount=\"5\""));
        assertFalse(fxml.contains("maxHeight=\"92\""));
        assertTrue(fxml.contains("txtInvoiceTerms\" prefRowCount=\"5\" minHeight=\"120\" prefHeight=\"150\" maxHeight=\"Infinity\""));
    }
}
