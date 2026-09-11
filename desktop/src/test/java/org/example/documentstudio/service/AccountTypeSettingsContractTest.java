package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for the master-backed bank Account Type enhancement. */
class AccountTypeSettingsContractTest {

    @Test
    void paymentSettingsUsesMasterBackedAccountTypeAndPersistsSharedPaymentKey() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/pages/settings/PaymentSettingsPanel.fxml"));
        String controller = Files.readString(Path.of("src/main/java/org/example/controller/SettingsController.java"));

        assertTrue(fxml.contains("fx:id=\"cmbAccountType\""));
        assertTrue(fxml.contains("text=\"Account Type\""));
        assertTrue(controller.contains("lookupValuesByCategoryCode(\"ACCOUNT_TYPE\")"));
        assertTrue(controller.contains("putSetting(\"payment.accountType\", valueOrEmpty(cmbAccountType))"));
    }

    @Test
    void accountTypeIsAvailableToBothExcelAndPdfStudio() {
        List<String> excel = TemplateFieldCatalog.excelFieldsFor(DocumentType.SALES_INVOICE).stream()
                .map(TemplateFieldDefinition::key).toList();
        List<String> pdf = TemplateFieldCatalog.pdfFieldsFor(DocumentType.SALES_INVOICE).stream()
                .map(TemplateFieldDefinition::key).toList();

        assertTrue(excel.contains("payment.accountType"), "Excel Studio must expose payment.accountType");
        assertTrue(pdf.contains("payment.accountType"), "PDF Studio must expose payment.accountType");
    }

    @Test
    void templateDataFactorySynchronizesConfiguredAccountType() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/documentstudio/service/TemplateDataFactory.java"));
        String autoMap = Files.readString(Path.of("src/main/java/org/example/documentstudio/service/PdfAutoMappingService.java"));
        assertTrue(source.contains("put(v, \"payment.accountType\", ConfigManager.get(\"payment.accountType\", \"\"))"));
        assertTrue(autoMap.contains("Map.entry(\"payment.accountType\""), "PDF auto-map must recognize Account Type labels");
    }
}
