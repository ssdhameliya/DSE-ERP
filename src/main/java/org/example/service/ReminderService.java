package org.example.service;

import org.example.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simple in-app reminder scheduler. Reads reminders from the DB (reminders table) and schedules them.
 * When a reminder triggers, it will call the ReminderHandler interface.
 */
public final class ReminderService {

    private static final ScheduledExecutorService SCHED = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reminder-scheduler");
        t.setDaemon(true);
        return t;
    });

    private static final List<ScheduledFuture<?>> scheduled = new ArrayList<>();

    public interface ReminderHandler {
        void onReminder(long id, String module, long refId, String method);
    }

    private ReminderService() {}

    public static void start(ReminderHandler handler) {
        // load pending reminders
        try (Connection con = DatabaseManager.getConnection()) {
            String sql = "SELECT id,module,ref_id,trigger_time,method FROM reminders WHERE status = 'PENDING'";
            try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String module = rs.getString("module");
                    long refId = rs.getLong("ref_id");
                    String method = rs.getString("method");
                    String trigger = rs.getString("trigger_time");
                    // Expecting ISO-8601 or epoch millis in DB; try parse epoch
                    long epochMillis;
                    try {
                        epochMillis = Long.parseLong(trigger);
                    } catch (NumberFormatException ex) {
                        // try parse as ISO instant
                        ZonedDateTime z = ZonedDateTime.parse(trigger);
                        epochMillis = z.toInstant().toEpochMilli();
                    }
                    long delay = epochMillis - Instant.now().toEpochMilli();
                    if (delay < 0) delay = 0;
                    ScheduledFuture<?> f = SCHED.schedule(() -> {
                        try { handler.onReminder(id, module, refId, method); } catch (Exception ignore) {}
                    }, delay, TimeUnit.MILLISECONDS);
                    scheduled.add(f);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void stop() {
        for (ScheduledFuture<?> f : scheduled) if (!f.isDone()) f.cancel(false);
        SCHED.shutdownNow();
    }

}
