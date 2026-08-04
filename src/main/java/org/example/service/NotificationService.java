package org.example.service;

import org.example.database.DatabaseManager;
import org.example.config.ConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.ArrayList;
import java.util.List;

/**
 * Notification helper: stores notifications in notifications table and provides a tiny API.
 */
public final class NotificationService {

    /** Immutable row displayed by the notification center. */
    public record NotificationItem(long id, String title, String message, String severity,
                                   boolean read, String targetFxml, String referenceNo,
                                   long createdAt) {
        @Override public String toString() {
            return title + "\n" + message;
        }
    }

    private NotificationService() {}

    /**
     * Convenience: store a notification using a default title and severity.
     * @param s notification text (ignored if null)
     */
    public static void add(String s) {
        if (s == null) return;
        createNotification("Notification", s, "INFO");
    }

    /**
     * Store a notification with explicit title, message and severity.
     *
     * @param title    notification title
     * @param message  notification body
     * @param severity INFO/WARN/ERROR etc.
     */
    public static void createNotification(String title, String message, String severity) {
        createNotification(title, message, severity, null, null);
    }

    /** Stores a notification that can optionally navigate to a related screen. */
    public static void createNotification(String title, String message, String severity,
                                          String targetFxml, String referenceNo) {
        if (!isAllowed(title, message, targetFxml)) return;
        String insert = "INSERT INTO notifications(title,message,severity,is_read,target_fxml,reference_no,created_at) VALUES(?,?,?,?,?,?,?)";
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(insert)) {
            ps.setString(1, title);
            ps.setString(2, message);
            ps.setString(3, severity == null ? "INFO" : severity);
            ps.setInt(4, 0);
            ps.setString(5, targetFxml);
            ps.setString(6, referenceNo);
            ps.setLong(7, Instant.now().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }


    private static boolean isAllowed(String title, String message, String targetFxml) {
        if (!Boolean.parseBoolean(ConfigManager.get("notifications.enabled", "true"))) return false;
        String text = ((title == null ? "" : title) + " " + (message == null ? "" : message) + " " + (targetFxml == null ? "" : targetFxml)).toLowerCase();
        String category;
        if (text.contains("quotation")) category = "quotations";
        else if (text.contains("return") || text.contains("refund")) category = "returns";
        else if (text.contains("payment") || text.contains("paid") || text.contains("receipt")) category = "payments";
        else if (text.contains("stock") || text.contains("inventory") || text.contains("item")) category = "inventory";
        else if (text.contains("reminder") || text.contains("follow-up") || text.contains("follow up")) category = "reminders";
        else if (text.contains("email") || text.contains("whatsapp") || text.contains("communication")) category = "communication";
        else if (text.contains("backup") || text.contains("restore") || text.contains("import") || text.contains("update")) category = "system";
        else if (text.contains("purchase") || text.contains("supplier")) category = "purchases";
        else category = "sales";
        return Boolean.parseBoolean(ConfigManager.get("notifications.category." + category, "true"));
    }

    /** Returns the newest notification rows for the notification center. */
    public static List<NotificationItem> findRecent(int limit) {
        String sql = "SELECT id,title,message,severity,is_read,target_fxml,reference_no,created_at " +
            "FROM notifications ORDER BY created_at DESC LIMIT ?";
        List<NotificationItem> items = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(new NotificationItem(
                    rs.getLong("id"), safe(rs.getString("title")), safe(rs.getString("message")),
                    safe(rs.getString("severity")), rs.getInt("is_read") != 0,
                    rs.getString("target_fxml"), rs.getString("reference_no"), rs.getLong("created_at")));
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return items;
    }

    /** Returns the unread count shown on the header bell badge. */
    public static int unreadCount() {
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(
            "SELECT COUNT(*) FROM notifications WHERE is_read=0"); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) { ex.printStackTrace(); return 0; }
    }

    /** Marks one notification as read after it is opened. */
    public static void markRead(long id) {
        executeUpdate("UPDATE notifications SET is_read=1 WHERE id=?", id);
    }

    /** Marks all notifications as read without deleting history. */
    public static void markAllRead() {
        executeUpdate("UPDATE notifications SET is_read=1");
    }

    /** Removes a single notification; primarily used by retention and automated verification. */
    public static void delete(long id) {
        executeUpdate("DELETE FROM notifications WHERE id=?", id);
    }

    private static void executeUpdate(String sql, Object... parameters) {
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) ps.setObject(i + 1, parameters[i]);
            ps.executeUpdate();
        } catch (SQLException ex) { ex.printStackTrace(); }
    }

    /**
     * Return all stored notification messages joined with a newline. Returns an empty string
     * when there are no notifications.
     *
     * @return joined notification messages
     */
    public static String getAll() {
        String sql = "SELECT title, message FROM notifications ORDER BY created_at ASC";
        StringJoiner sj = new StringJoiner("\n");
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String title = rs.getString("title");
                String msg = rs.getString("message");
                if (title == null) title = "";
                if (msg == null) msg = "";
                if (!title.isBlank()) sj.add(title + ": " + msg);
                else sj.add(msg);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return sj.length() == 0 ? "" : sj.toString();
    }

    public static List<String> getItems() {
        String sql = "SELECT title,message,severity,created_at FROM notifications ORDER BY created_at DESC LIMIT 50";
        List<String> items = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String severity = rs.getString("severity");
                String marker = "ERROR".equalsIgnoreCase(severity) ? "!" : "WARN".equalsIgnoreCase(severity) ? "*" : "i";
                items.add(marker + "  " + safe(rs.getString("title")) + "\n    " + safe(rs.getString("message")));
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return items;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    /**
     * Delete all notifications from the database. Use with caution.
     */
    public static void clear() {
        String sql = "DELETE FROM notifications";
        try (Connection con = DatabaseManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}
