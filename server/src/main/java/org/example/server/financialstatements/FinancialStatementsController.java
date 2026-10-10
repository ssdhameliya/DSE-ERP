package org.example.server.financialstatements;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/financial-statements", "/api/financial"})
public class FinancialStatementsController {

    private final FinancialStatementsService service;

    public FinancialStatementsController(FinancialStatementsService service) {
        this.service = service;
    }

    @GetMapping("/pnl")
    public FinancialStatementsDtos.ProfitAndLossDto getProfitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.getProfitAndLoss(fromDate, toDate);
    }

    @GetMapping("/profit-and-loss")
    public FinancialStatementsDtos.DesktopProfitAndLossDto getDesktopProfitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.getDesktopProfitAndLoss(fromDate, toDate);
    }

    @GetMapping("/balance-sheet")
    public FinancialStatementsDtos.DesktopBalanceSheetDto getBalanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return service.getDesktopBalanceSheet(asOfDate);
    }
}
