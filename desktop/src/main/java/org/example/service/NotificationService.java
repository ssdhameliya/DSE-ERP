package org.example.service;

import org.example.api.insights.InsightsApiClient;
import org.example.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Notification helper backed exclusively by the typed Spring insights API. */
public final class NotificationService {
    public enum Category {
        SALES, PURCHASES, QUOTATIONS, RETURNS, PAYMENTS, INVENTORY,
        REMINDERS, COMMUNICATION, BACKUP, UPDATE, SECURITY, SYSTEM
    }

    public record NotificationItem(long id, String title, String message, String severity,
                                   boolean read, String targetFxml, String referenceNo,
                                   long createdAt) {
        @Override public String toString() { return title + "\n" + message; }
    }

    private static final InsightsApiClient API = new InsightsApiClient();
    private NotificationService() {}

    public static void add(String s) { if (s != null) createNotification("Notification", s, "INFO"); }
    public static void createNotification(String title, String message, String severity) {
        createNotification(title, message, severity, null, null);
    }
    public static void createNotification(String title, String message, String severity,
                                          String targetFxml, String referenceNo) {
        createNotification(inferCategory(title, message, targetFxml), title, message, severity, targetFxml, referenceNo);
    }
    public static void createNotification(Category category, String title, String message, String severity,
                                          String targetFxml, String referenceNo) {
        if (!isAllowed(category, severity)) return;
        try {
            API.createNotification(new InsightsApiClient.NotificationCreate(
                    title, message, severity == null ? "INFO" : severity, targetFxml, referenceNo));
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private static boolean isAllowed(Category category, String severity) {
        String normalizedSeverity = severity == null ? "INFO" : severity.trim().toUpperCase();
        if (normalizedSeverity.equals("ERROR") || normalizedSeverity.equals("CRITICAL") || normalizedSeverity.equals("FATAL")) return true;
        if (!Boolean.parseBoolean(ConfigManager.get("notifications.enabled", "true"))) return false;
        String key = switch (category == null ? Category.SYSTEM : category) {
            case SALES -> "sales"; case PURCHASES -> "purchases"; case QUOTATIONS -> "quotations";
            case RETURNS -> "returns"; case PAYMENTS -> "payments"; case INVENTORY -> "inventory";
            case REMINDERS -> "reminders"; case COMMUNICATION -> "communication";
            case BACKUP, UPDATE, SECURITY, SYSTEM -> "system";
        };
        return Boolean.parseBoolean(ConfigManager.get("notifications.category." + key, "true"));
    }

    private static Category inferCategory(String title, String message, String targetFxml) {
        String text = ((title == null ? "" : title) + " " + (message == null ? "" : message) + " " + (targetFxml == null ? "" : targetFxml)).toLowerCase();
        if (text.contains("quotation")) return Category.QUOTATIONS;
        if (text.contains("return") || text.contains("refund")) return Category.RETURNS;
        if (text.contains("payment") || text.contains("paid") || text.contains("receipt")) return Category.PAYMENTS;
        if (text.contains("stock") || text.contains("inventory") || text.contains("item")) return Category.INVENTORY;
        if (text.contains("reminder") || text.contains("follow-up") || text.contains("follow up")) return Category.REMINDERS;
        if (text.contains("email") || text.contains("whatsapp") || text.contains("communication")) return Category.COMMUNICATION;
        if (text.contains("backup") || text.contains("restore")) return Category.BACKUP;
        if (text.contains("update")) return Category.UPDATE;
        if (text.contains("security") || text.contains("login") || text.contains("password")) return Category.SECURITY;
        if (text.contains("purchase") || text.contains("supplier")) return Category.PURCHASES;
        return Category.SALES;
    }

    public static List<NotificationItem> findRecent(int limit) {
        try {
            return API.notifications(limit).stream().map(x -> new NotificationItem(
                    x.id(), x.title(), x.message(), x.severity(), x.read(), x.targetFxml(), x.referenceNo(), x.createdAt())).toList();
        } catch (Exception ex) { ex.printStackTrace(); return List.of(); }
    }
    public static int unreadCount() { try { return (int) API.unreadCount(); } catch (Exception ex) { ex.printStackTrace(); return 0; } }
    public static void markRead(long id) { API.markRead(id); }
    public static void markAllRead() { API.markAllRead(); }
    public static void delete(long id) { API.deleteNotification(id); }

    public static String getAll() {
        StringJoiner sj = new StringJoiner("\n");
        for (NotificationItem x : findRecent(500)) sj.add((x.title() == null || x.title().isBlank() ? "" : x.title() + ": ") + safe(x.message()));
        return sj.toString();
    }
    public static List<String> getItems() {
        List<String> out = new ArrayList<>();
        for (NotificationItem x : findRecent(50)) {
            String marker = "ERROR".equalsIgnoreCase(x.severity()) ? "!" : "WARN".equalsIgnoreCase(x.severity()) ? "*" : "i";
            out.add(marker + "  " + safe(x.title()) + "\n    " + safe(x.message()));
        }
        return out;
    }
    private static String safe(String value) { return value == null ? "" : value; }
    public static void clear() { API.clearNotifications(); }
}
