package org.example.controller;

import org.example.shortcut.ShortcutRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class QuickDatePresetsAndNavigationContractTest {

    @Test
    void testQuickDatePresetsInSalesAndPurchaseRegisters() throws Exception {
        String salesController = Files.readString(Path.of("src/main/java/org/example/controller/SalesListController.java"));
        assertTrue(salesController.contains("showThisFinancialYear()"), "SalesListController must support This Financial Year preset");
        assertTrue(salesController.contains("showThisQuarter()"), "SalesListController must support This Quarter preset");

        String salesFxml = Files.readString(Path.of("src/main/resources/fxml/pages/SalesList.fxml"));
        assertTrue(salesFxml.contains("onAction=\"#showThisFinancialYear\""), "SalesList.fxml must have This FY button");
        assertTrue(salesFxml.contains("onAction=\"#showThisQuarter\""), "SalesList.fxml must have This Quarter button");

        String purchaseController = Files.readString(Path.of("src/main/java/org/example/controller/PurchaseListController.java"));
        assertTrue(purchaseController.contains("showThisFinancialYear()"), "PurchaseListController must support This Financial Year preset");
        assertTrue(purchaseController.contains("showThisQuarter()"), "PurchaseListController must support This Quarter preset");

        String purchaseFxml = Files.readString(Path.of("src/main/resources/fxml/pages/PurchaseList.fxml"));
        assertTrue(purchaseFxml.contains("onAction=\"#showThisFinancialYear\""), "PurchaseList.fxml must have This FY button");
        assertTrue(purchaseFxml.contains("onAction=\"#showThisQuarter\""), "PurchaseList.fxml must have This Quarter button");
    }

    @Test
    void testDashboardInteractiveKpiCards() throws Exception {
        String dashboardController = Files.readString(Path.of("src/main/java/org/example/controller/DashboardHomeController.java"));
        assertTrue(dashboardController.contains("openSalesFromDashboard()"));
        assertTrue(dashboardController.contains("openPurchaseFromDashboard()"));
        assertTrue(dashboardController.contains("openReceivablesFromDashboard()"));
        assertTrue(dashboardController.contains("openPayablesFromDashboard()"));
        assertTrue(dashboardController.contains("openInventoryFromDashboard()"));

        String dashboardFxml = Files.readString(Path.of("src/main/resources/fxml/pages/DashboardHome.fxml"));
        assertTrue(dashboardFxml.contains("onMouseClicked=\"#openSalesFromDashboard\""));
        assertTrue(dashboardFxml.contains("onMouseClicked=\"#openPurchaseFromDashboard\""));
        assertTrue(dashboardFxml.contains("onMouseClicked=\"#openReceivablesFromDashboard\""));
        assertTrue(dashboardFxml.contains("onMouseClicked=\"#openPayablesFromDashboard\""));
        assertTrue(dashboardFxml.contains("onMouseClicked=\"#openInventoryFromDashboard\""));
    }

    @Test
    void testGlobalSearchAllowedInTextInput() {
        assertTrue(ShortcutRegistry.allowInTextInput(ShortcutRegistry.Action.GLOBAL_SEARCH),
                "Global Search (Ctrl+K) must be permitted inside text input targets");
    }
}
