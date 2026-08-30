package org.example.server.reporting;

import org.example.server.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reporting/schedules")
public class ReportScheduleController {
    private final ReportScheduleService service;

    public ReportScheduleController(ReportScheduleService service) { this.service = service; }

    private static void requireReports() { CurrentUser.requirePermission("REPORTS.VIEW", "Manage scheduled reports"); }

    @GetMapping
    public ReportScheduleDtos.SchedulePage page() { requireReports(); return service.pageForCurrentUser(); }

    @GetMapping("/saved-reports")
    public List<ReportScheduleDtos.SavedReportOption> savedReports() { requireReports(); return service.savedReportsForCurrentUser(); }

    @PostMapping
    public ReportScheduleDtos.ScheduleRow create(@RequestBody ReportScheduleDtos.ScheduleRequest request) {
        requireReports(); return service.create(request);
    }

    @PutMapping("/{id}")
    public ReportScheduleDtos.ScheduleRow update(@PathVariable long id, @RequestBody ReportScheduleDtos.ScheduleRequest request) {
        requireReports(); return service.update(id, request);
    }

    @PostMapping("/{id}/run")
    public ReportScheduleDtos.Result run(@PathVariable long id) { requireReports(); return service.runNow(id); }

    @PostMapping("/{id}/pause")
    public ReportScheduleDtos.Result pause(@PathVariable long id) { requireReports(); service.pause(id); return new ReportScheduleDtos.Result(true, "Schedule paused"); }

    @PostMapping("/{id}/resume")
    public ReportScheduleDtos.Result resume(@PathVariable long id) { requireReports(); service.resume(id); return new ReportScheduleDtos.Result(true, "Schedule resumed"); }

    @PostMapping("/{id}/duplicate")
    public ReportScheduleDtos.ScheduleRow duplicate(@PathVariable long id) { requireReports(); return service.duplicate(id); }

    @DeleteMapping("/{id}")
    public ReportScheduleDtos.Result delete(@PathVariable long id) { requireReports(); service.delete(id); return new ReportScheduleDtos.Result(true, "Schedule deleted"); }

    @GetMapping("/{id}/history")
    public List<ReportScheduleDtos.RunHistory> history(@PathVariable long id) { requireReports(); return service.historyForCurrentUser(id); }
}
