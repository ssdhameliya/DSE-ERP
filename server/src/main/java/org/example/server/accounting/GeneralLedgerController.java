package org.example.server.accounting;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/accounting")
public class GeneralLedgerController {

    private final GeneralLedgerService service;

    public GeneralLedgerController(GeneralLedgerService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public List<GeneralLedgerDtos.AccountDto> listAccounts() {
        return service.listAccounts();
    }

    @PostMapping("/accounts")
    public GeneralLedgerDtos.AccountDto createAccount(@RequestBody GeneralLedgerDtos.AccountDto dto) {
        return service.createAccount(dto);
    }

    @GetMapping("/journal-entries")
    public List<GeneralLedgerDtos.JournalEntryDto> listEntries() {
        return service.listRecentEntries();
    }

    @PostMapping("/journal-entries")
    public GeneralLedgerDtos.JournalEntryDto postEntry(@RequestBody GeneralLedgerDtos.CreateJournalRequest req) {
        return service.postJournalEntry(req);
    }

    @GetMapping("/trial-balance")
    public List<GeneralLedgerDtos.TrialBalanceItemDto> trialBalance() {
        return service.getTrialBalance();
    }

    @GetMapping("/financial-statements")
    public GeneralLedgerDtos.FinancialStatementDto financialStatements() {
        return service.getFinancialStatements();
    }
}
