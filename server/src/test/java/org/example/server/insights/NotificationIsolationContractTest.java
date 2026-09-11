package org.example.server.insights;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NotificationIsolationContractTest {
    @Test
    void schemaHasPerUserPreferencesAndState() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V10_0_1__per_user_notifications.sql"));
        assertTrue(migration.contains("recipient_user_id"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS notification_preference"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS notification_user_state"));
        assertTrue(migration.contains("PRIMARY KEY (notification_id, user_id)"));
    }

    @Test
    void readDismissAndClearOperationsArePerUser() throws Exception {
        String service = Files.readString(Path.of("src/main/java/org/example/server/insights/InsightsService.java"));
        assertTrue(service.contains("CurrentUser.require().id()"));
        assertTrue(service.contains("notification_user_state"));
        assertTrue(service.contains("is_dismissed"));
        assertFalse(service.contains("UPDATE notifications SET is_read=1"));
        assertTrue(service.contains("deleteNotificationEvent(long id)"));
        assertEquals(1, service.split("DELETE FROM notifications WHERE id=\\?", -1).length - 1,
                "only the explicit admin event-delete path may physically delete a notification event");
        assertTrue(service.contains("UPPER(COALESCE(n.category,'SYSTEM'))='SECURITY'"));
    }

    @Test
    void preferencesExposeNewModulesAndScheduledReportsNotifyOwner() throws Exception {
        String service = Files.readString(Path.of("src/main/java/org/example/server/insights/InsightsService.java"));
        String scheduler = Files.readString(Path.of("src/main/java/org/example/server/reporting/ReportScheduleService.java"));
        for (String category : new String[]{"banking", "reports", "approval", "imports", "backup", "update", "security"}) {
            assertTrue(service.contains("\"" + category + "\""), category + " preference missing");
        }
        assertTrue(scheduler.contains("\"REPORTS\""));
        assertTrue(scheduler.contains("schedule.userId()"));
        assertTrue(scheduler.contains("Scheduled Report completed"));
    }
}
