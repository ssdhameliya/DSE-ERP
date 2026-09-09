package org.example.documentstudio.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalSharedClientRoutingTest {
    @Test
    void sharedDesktopRoutesSalesAndPurchasePdfAndExcelThroughCanonicalServer() throws Exception {
        String pdf = Files.readString(Path.of("src/main/java/org/example/documentstudio/service/DocumentOutputService.java"));
        String excel = Files.readString(Path.of("src/main/java/org/example/documentstudio/service/ExcelOutputService.java"));
        String client = Files.readString(Path.of("src/main/java/org/example/api/authority/CanonicalDocumentClient.java"));

        assertTrue(pdf.contains("ConfigManager.isSharedClient()"));
        assertTrue(pdf.contains("new CanonicalDocumentClient().render"));
        assertTrue(excel.contains("canonical(DocumentType.SALES_INVOICE"));
        assertTrue(excel.contains("canonical(DocumentType.PURCHASE_INVOICE"));
        assertTrue(client.contains("/api/documents/render?type="));
    }

    @Test
    void sharedClientPersistsExternalPostgresOwnershipAndUpdateShutdownBypassesManagedDatabase() throws Exception {
        String config = Files.readString(Path.of("src/main/java/org/example/config/ConfigManager.java"));
        String runtime = Files.readString(Path.of("src/main/java/org/example/api/runtime/ManagedPostgresRuntime.java"));
        assertTrue(config.contains("properties.setProperty(\"runtime.postgres.mode\", \"external\")"));
        assertTrue(runtime.contains("if (ConfigManager.isSharedClient()) return;"));
    }
}
