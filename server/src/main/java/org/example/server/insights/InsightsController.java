package org.example.server.insights;

import org.example.server.security.CurrentUser;

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
        CurrentUser.requirePermission("COMMUNICATION.EDIT", "Update communication read state");
        s.markCommunicationRead(channel);
        return ok("Updated");
    }

    @GetMapping("/reports/filters")
    public InsightDtos.ReportFilters filters() {
        return s.reportFilters();
    }

    @GetMapping("/reports")
    public InsightDtos.ReportBundle reports(@RequestParam String from, @RequestParam String to,
                                             @RequestParam(defaultValue="All Reports") String reportType,
                                             @RequestParam(defaultValue="") String party,
                                             @RequestParam(defaultValue="") String item,
                                             @RequestParam(defaultValue="") String salesperson) {
        return s.report(from,to,reportType,party,item,salesperson);
    }

    @GetMapping("/reminders")
    public List<InsightDtos.ReminderDto> reminders() {
        CurrentUser.requirePermission("REMINDERS.VIEW", "View reminders");
        return s.reminders();
    }

    @PostMapping("/reminders")
    public InsightDtos.ReminderDto addReminder(@RequestBody InsightDtos.ReminderDto d) {
        CurrentUser.requirePermission("REMINDERS.CREATE", "Create reminder");
        return s.createReminder(d);
    }

    @PutMapping("/reminders/{id}")
    public InsightDtos.ReminderDto editReminder(@PathVariable long id, @RequestBody InsightDtos.ReminderDto d) {
        CurrentUser.requirePermission("REMINDERS.EDIT", "Edit reminder");
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
        CurrentUser.requirePermission("REMINDERS.DELETE", "Delete reminder");
        s.deleteReminder(id);
        return ok("Reminder deleted");
    }

    @GetMapping("/notifications")
    public List<InsightDtos.NotificationDto> notifications(@RequestParam(defaultValue = "50") int limit) {
        CurrentUser.requirePermission("COMMUNICATION.VIEW", "View notifications");
        return s.notifications(limit);
    }

    @GetMapping("/notifications/unread-count")
    public InsightDtos.CountDto unread() {
        CurrentUser.requirePermission("COMMUNICATION.VIEW", "View notifications");
        return new InsightDtos.CountDto(s.unreadCount());
    }

    @PostMapping("/notifications")
    public InsightDtos.NotificationDto notify(@RequestBody InsightDtos.NotificationCreate d) {
        CurrentUser.requirePermission("COMMUNICATION.CREATE", "Create notification");
        return s.createNotification(d);
    }

    @PostMapping("/notifications/{id}/read")
    public InsightDtos.Ok read(@PathVariable long id) {
        CurrentUser.requirePermission("COMMUNICATION.EDIT", "Update notification");
        s.markRead(id);
        return ok("Updated");
    }

    @PostMapping("/notifications/{id}/unread")
    public InsightDtos.Ok unread(@PathVariable long id) {
        CurrentUser.requirePermission("COMMUNICATION.EDIT", "Update notification");
        s.markUnread(id);
        return ok("Updated");
    }

    @PostMapping("/notifications/read-all")
    public InsightDtos.Ok readAll() {
        CurrentUser.requirePermission("COMMUNICATION.EDIT", "Update notifications");
        s.markAllRead();
        return ok("Updated");
    }

    @DeleteMapping("/notifications/{id}")
    public InsightDtos.Ok deleteNotification(@PathVariable long id) {
        CurrentUser.requirePermission("COMMUNICATION.DELETE", "Delete notification");
        s.deleteNotification(id);
        return ok("Deleted");
    }

    @DeleteMapping("/notifications")
    public InsightDtos.Ok clear() {
        CurrentUser.requirePermission("COMMUNICATION.DELETE", "Clear notifications");
        s.clearNotifications();
        return ok("Cleared");
    }

    private InsightDtos.Ok ok(String m) {
        return new InsightDtos.Ok(true, m);
    }
}
