package org.example.server.automation;

import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/automation")
public class SmartAutomationController {

    private final SmartAutomationService service;

    public SmartAutomationController(SmartAutomationService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<AutomationDtos.RuleDto> listRules() {
        return service.listRules();
    }

    @PostMapping("/rules/{code}/toggle")
    public AutomationDtos.RuleDto toggleRule(@PathVariable String code, @RequestParam boolean enabled) {
        return service.toggleRule(code, enabled);
    }

    @PostMapping("/rules/{code}/config")
    public AutomationDtos.RuleDto updateConfig(@PathVariable String code, @RequestBody String payload) {
        return service.updateRuleConfig(code, payload);
    }

    @GetMapping("/match-logs")
    public List<AutomationDtos.ThreeWayMatchDto> listMatchLogs() {
        return service.listMatchLogs();
    }

    @PostMapping("/match-logs/run")
    public AutomationDtos.ThreeWayMatchDto runMatch(
            @RequestParam(required = false) Long poId,
            @RequestParam(required = false) Long grnId,
            @RequestParam(required = false) Long billId,
            @RequestParam String poNo,
            @RequestParam String billNo,
            @RequestParam String supplier,
            @RequestParam BigDecimal poAmt,
            @RequestParam BigDecimal grnAmt,
            @RequestParam BigDecimal billAmt,
            @RequestParam(required = false) String user) {
        return service.runThreeWayMatch(poId, grnId, billId, poNo, billNo, supplier, poAmt, grnAmt, billAmt, user);
    }

    @GetMapping("/reorder-suggestions")
    public List<AutomationDtos.ReorderSuggestionDto> listSuggestions() {
        return service.listReorderSuggestions();
    }

    @PostMapping("/reorder-suggestions/scan")
    public List<AutomationDtos.ReorderSuggestionDto> scanReorder() {
        return service.scanAndGenerateReorderSuggestions();
    }
}
