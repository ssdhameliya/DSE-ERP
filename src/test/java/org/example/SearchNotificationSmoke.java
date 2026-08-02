package org.example;

import org.example.database.DatabaseManager;
import org.example.service.GlobalSearchService;
import org.example.service.NotificationService;

/** Verifies cross-module search and the unread notification lifecycle against SQLite. */
public final class SearchNotificationSmoke {
    public static void main(String[] args) {
        DatabaseManager.initialize();

        GlobalSearchService search = new GlobalSearchService();
        if (search.search("a").isEmpty())
            throw new AssertionError("Global ERP search returned no records for seeded/current data");

        int before = NotificationService.unreadCount();
        NotificationService.createNotification("Automated verification", "Notification lifecycle test", "INFO");
        var created = NotificationService.findRecent(1).getFirst();
        if (NotificationService.unreadCount() != before + 1)
            throw new AssertionError("Unread badge count did not increase");
        NotificationService.markRead(created.id());
        if (NotificationService.unreadCount() != before)
            throw new AssertionError("Unread badge count did not decrease");
        NotificationService.delete(created.id());
        System.out.println("SEARCH_NOTIFICATION_OK results=" + search.search("a").size());
    }
}
