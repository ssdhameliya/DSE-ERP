package org.example.server.insights;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {
    private final InsightsService s;

    public InsightsController(InsightsService s) {
        this.s = s;
    }

    @GetMapping("/dashboard")
    public InsightDtos.DashboardBundle dashboard(@RequestParam(defaultValue = "This Month") String period) {
        return s.dashboard(period);
    }

    @GetMapping("/shell-counts")
    public InsightDtos.ShellCounts shellCounts() {
        return s.shellCounts();
    }

    @PostMapping("/communication/read")
    public InsightDtos.Ok markCommunicationRead(@RequestParam String channel) {
        s.markCommunicationRead(channel);
        return ok("Updated");
    }

    @GetMapping("/reports/filters")
    public InsightDtos.ReportFilters filters() {
        return s.reportFilters();
    }

    @GetMapping("/reports")
    public InsightDtos.ReportBundle reports(@RequestParam String from, @RequestParam String to) {
        return s.report(from, to);
    }

    @GetMapping("/reminders")
    public List<InsightDtos.ReminderDto> reminders() {
        return s.reminders();
    }

    @PostMapping("/reminders")
    public InsightDtos.ReminderDto addReminder(@RequestBody InsightDtos.ReminderDto d) {
        return s.createReminder(d);
    }

    @PutMapping("/reminders/{id}")
    public InsightDtos.ReminderDto editReminder(@PathVariable long id, @RequestBody InsightDtos.ReminderDto d) {
        // The route identity is authoritative.  Body IDs from old clients are ignored.
        return s.updateReminder(id, d);
    }

    @PostMapping("/reminders/{id}/status")
    public InsightDtos.Ok status(@PathVariable long id,
                                 @RequestParam String status,
                                 @RequestParam(required = false) String snoozedUntil) {
        s.setReminderStatus(id, status, snoozedUntil);
        return ok("Reminder updated");
    }

    @DeleteMapping("/reminders/{id}")
    public InsightDtos.Ok deleteReminder(@PathVariable long id) {
        s.deleteReminder(id);
        return ok("Reminder deleted");
    }

    @GetMapping("/notifications")
    public List<InsightDtos.NotificationDto> notifications(@RequestParam(defaultValue = "50") int limit) {
        return s.notifications(limit);
    }

    @GetMapping("/notifications/unread-count")
    public InsightDtos.CountDto unread() {
        return new InsightDtos.CountDto(s.unreadCount());
    }

    @PostMapping("/notifications")
    public InsightDtos.NotificationDto notify(@RequestBody InsightDtos.NotificationCreate d) {
        return s.createNotification(d);
    }

    @PostMapping("/notifications/{id}/read")
    public InsightDtos.Ok read(@PathVariable long id) {
        s.markRead(id);
        return ok("Updated");
    }

    @PostMapping("/notifications/{id}/unread")
    public InsightDtos.Ok unread(@PathVariable long id) {
        s.markUnread(id);
        return ok("Updated");
    }

    @PostMapping("/notifications/read-all")
    public InsightDtos.Ok readAll() {
        s.markAllRead();
        return ok("Updated");
    }

    @DeleteMapping("/notifications/{id}")
    public InsightDtos.Ok deleteNotification(@PathVariable long id) {
        s.deleteNotification(id);
        return ok("Deleted");
    }

    @DeleteMapping("/notifications")
    public InsightDtos.Ok clear() {
        s.clearNotifications();
        return ok("Cleared");
    }

    private InsightDtos.Ok ok(String m) {
        return new InsightDtos.Ok(true, m);
    }
}
