package org.example.server.aging;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/aging", "/api/financial/aging"})
public class AgingAnalysisController {

    private final AgingAnalysisService service;

    public AgingAnalysisController(AgingAnalysisService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    public AgingAnalysisDtos.DesktopAgingReport getAgingReport(
            @RequestParam(defaultValue = "DEBTOR") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return service.getDesktopAgingReport(type, asOfDate);
    }

    @GetMapping("/receivables")
    public AgingAnalysisDtos.AgingSummaryDto getReceivablesAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return service.getReceivablesAging(asOfDate);
    }

    @GetMapping("/payables")
    public AgingAnalysisDtos.AgingSummaryDto getPayablesAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return service.getPayablesAging(asOfDate);
    }

    @PostMapping("/remind/{partyId}")
    public boolean sendPaymentReminder(@PathVariable Long partyId, @RequestParam(required = false) String user) {
        return service.sendPaymentReminder(partyId, user);
    }
}
