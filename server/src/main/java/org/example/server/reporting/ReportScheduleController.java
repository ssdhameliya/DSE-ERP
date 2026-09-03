package org.example.server.reporting;

import org.example.server.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reporting/schedules")
public class ReportScheduleController {
    private final ReportScheduleService service;

    public ReportScheduleController(ReportScheduleService service) { this.service = service; }

    private static void requireView() { CurrentUser.requirePermission("REPORTS.VIEW", "View scheduled reports"); }
    private static void requireCreate() { CurrentUser.requirePermission("REPORTS.CREATE", "Create scheduled reports"); }
    private static void requireEdit() { CurrentUser.requirePermission("REPORTS.EDIT", "Edit scheduled reports"); }
    private static void requireDelete() { CurrentUser.requirePermission("REPORTS.DELETE", "Delete scheduled reports"); }
    private static void requireExport() { CurrentUser.requirePermission("REPORTS.EXPORT", "Export scheduled reports"); }

    @GetMapping
    public ReportScheduleDtos.SchedulePage page() { requireView(); return service.pageForCurrentUser(); }

    @GetMapping("/saved-reports")
    public List<ReportScheduleDtos.SavedReportOption> savedReports() { requireView(); return service.savedReportsForCurrentUser(); }

    @PostMapping
    public ReportScheduleDtos.ScheduleRow create(@RequestBody ReportScheduleDtos.ScheduleRequest request) {
        requireCreate(); requireExport(); return service.create(request);
    }

    @PutMapping("/{id}")
    public ReportScheduleDtos.ScheduleRow update(@PathVariable long id, @RequestBody ReportScheduleDtos.ScheduleRequest request) {
        requireEdit(); requireExport(); return service.update(id, request);
    }

    @PostMapping("/{id}/run")
    public ReportScheduleDtos.Result run(@PathVariable long id) { requireEdit(); requireExport(); return service.runNow(id); }

    @PostMapping("/{id}/pause")
    public ReportScheduleDtos.Result pause(@PathVariable long id) { requireEdit(); service.pause(id); return new ReportScheduleDtos.Result(true, "Schedule paused"); }

    @PostMapping("/{id}/resume")
    public ReportScheduleDtos.Result resume(@PathVariable long id) { requireEdit(); service.resume(id); return new ReportScheduleDtos.Result(true, "Schedule resumed"); }

    @PostMapping("/{id}/duplicate")
    public ReportScheduleDtos.ScheduleRow duplicate(@PathVariable long id) { requireCreate(); requireExport(); return service.duplicate(id); }

    @DeleteMapping("/{id}")
    public ReportScheduleDtos.Result delete(@PathVariable long id) { requireDelete(); service.delete(id); return new ReportScheduleDtos.Result(true, "Schedule deleted"); }

    @GetMapping("/{id}/history")
    public List<ReportScheduleDtos.RunHistory> history(@PathVariable long id) { requireView(); return service.historyForCurrentUser(id); }
}
