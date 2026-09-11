package org.example.util;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PagerAndNotificationPreferencesContractTest {
    @Test
    void pagerSemanticsArePinnedToNavigationArrows() throws Exception {
        String iconFactory = Files.readString(Path.of("src/main/java/org/example/util/IconFactory.java"));
        assertTrue(iconFactory.contains("PAGER_SEMANTIC_PROPERTY"));
        assertTrue(iconFactory.contains("Set.of(\"first\", \"previous\", \"next\", \"last\")"));
        assertTrue(iconFactory.contains("case \"first\" -> \"fas-angle-double-left\""));
        assertTrue(iconFactory.contains("case \"previous\" -> \"fas-angle-left\""));
        assertTrue(iconFactory.contains("case \"next\" -> \"fas-angle-right\""));
        assertTrue(iconFactory.contains("case \"last\" -> \"fas-angle-double-right\""));

        String sales = Files.readString(Path.of("src/main/resources/fxml/pages/SalesList.fxml"));
        assertTrue(sales.contains("text=\"|‹\" onAction=\"#firstPage\""));
        assertTrue(sales.contains("text=\"‹\" onAction=\"#previousPage\""));
        assertTrue(sales.contains("text=\"›\" onAction=\"#nextPage\""));
        assertTrue(sales.contains("text=\"›|\" onAction=\"#lastPage\""));
    }

    @Test
    void notificationSettingsExposeAllOperationalGroupsAndValidXml() throws Exception {
        Path fxml = Path.of("src/main/resources/fxml/pages/settings/NotificationsSettingsPanel.fxml");
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fxml.toFile());
        String text = Files.readString(fxml);
        for (String id : List.of("chkNotifyBanking", "chkNotifyReports", "chkNotifyApproval", "chkNotifyImports",
                "chkNotifyBackup", "chkNotifyUpdate", "chkNotifySecurity", "chkNotificationToasts")) {
            assertTrue(text.contains("fx:id=\"" + id + "\""), id + " must be present");
        }
        assertTrue(text.contains("Security &amp; User Access (mandatory)"));
        assertTrue(text.contains("Reports and scheduled reports"));
        assertTrue(text.contains("Bank, expenses and reconciliation"));
    }

    @Test
    void desktopNotificationPreferencesAreServerBackedNotConfigManagerGated() throws Exception {
        String settings = Files.readString(Path.of("src/main/java/org/example/controller/SettingsController.java"));
        String notificationService = Files.readString(Path.of("src/main/java/org/example/service/NotificationService.java"));
        assertTrue(settings.contains("NotificationPreferenceService.save"));
        assertFalse(settings.contains("notifications.category."));
        assertFalse(notificationService.contains("ConfigManager.get(\"notifications."));
        assertTrue(notificationService.contains("BANKING, REPORTS"));
        assertTrue(notificationService.contains("APPROVAL, IMPORTS, BACKUP"));
    }
}
