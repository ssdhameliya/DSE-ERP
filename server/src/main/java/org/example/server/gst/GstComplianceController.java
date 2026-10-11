package org.example.server.gst;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.server.persistence.entity.Gstr2bReconciliationEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/gst")
public class GstComplianceController {

    private final GstComplianceService service;
    private final Gstr1Service gstr1Service;

    public GstComplianceController(GstComplianceService service, Gstr1Service gstr1Service) {
        this.service = service;
        this.gstr1Service = gstr1Service;
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

    @GetMapping("/reconciliation/{id}/notice")
    public GstDtos.SupplierNoticeDto getSupplierNotice(@PathVariable Long id) {
        return service.generateSupplierNotice(id);
    }

    @GetMapping("/gstr3b/{period}")
    public GstDtos.Gstr3bSummaryDto calculateGstr3b(@PathVariable String period) {
        return service.calculateGstr3b(period);
    }

    @GetMapping("/gstr1/{period}")
    public Gstr1Dtos.Gstr1FullReturnDto getGstr1(@PathVariable String period) {
        return gstr1Service.generateGstr1(period);
    }

    @GetMapping("/gstr1/{period}/json")
    public ObjectNode getGstr1Json(@PathVariable String period) {
        return gstr1Service.generateGovernmentJson(period);
    }
}
