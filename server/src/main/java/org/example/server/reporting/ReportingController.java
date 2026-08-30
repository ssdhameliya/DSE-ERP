package org.example.server.reporting;

import org.example.server.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reporting")
public class ReportingController {
    private final ReportingService service;

    public ReportingController(ReportingService service) {
        this.service = service;
    }

    @GetMapping("/definitions")
    public List<ReportingDtos.ReportDefinition> definitions() {
        CurrentUser.requirePermission("REPORTS.VIEW", "View report definitions");
        return service.definitions();
    }

    @GetMapping("/filters")
    public ReportingDtos.ReportFilters filters() {
        CurrentUser.requirePermission("REPORTS.VIEW", "View report filters");
        return service.filters();
    }

    @PostMapping("/run")
    public ReportingDtos.ReportResult run(@RequestBody ReportingDtos.ReportRequest request) {
        CurrentUser.requirePermission("REPORTS.VIEW", "Run reports");
        return service.run(request, CurrentUser.require().username());
    }
}
