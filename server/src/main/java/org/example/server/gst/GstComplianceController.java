package org.example.server.gst;

import org.example.server.persistence.entity.Gstr2bReconciliationEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/gst")
public class GstComplianceController {

    private final GstComplianceService service;

    public GstComplianceController(GstComplianceService service) {
        this.service = service;
    }

    @GetMapping("/reconciliation/{period}")
    public List<GstDtos.Gstr2bRecordDto> listReconciliation(@PathVariable String period) {
        return service.listReconciliation(period);
    }

    @GetMapping("/metrics/{period}")
    public GstDtos.ReconciliationMetricsDto getMetrics(@PathVariable String period) {
        return service.getMetrics(period);
    }

    @PostMapping("/reconciliation/{period}/import")
    public List<GstDtos.Gstr2bRecordDto> import2b(
            @PathVariable String period,
            @RequestBody List<Gstr2bReconciliationEntity> records,
            @RequestParam(required = false) String user) {
        return service.process2bImport(period, records, user);
    }

    @GetMapping("/gstr3b/{period}")
    public GstDtos.Gstr3bSummaryDto calculateGstr3b(@PathVariable String period) {
        return service.calculateGstr3b(period);
    }
}
