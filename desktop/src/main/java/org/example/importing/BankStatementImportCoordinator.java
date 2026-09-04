package org.example.importing;

import org.example.api.bank.BankStatementApiClient;
import org.example.bank.KotakBankStatementCsvParser;
import org.example.service.ImportService;
import org.example.service.SessionService;

import java.nio.file.Path;
import java.util.List;

/** Executes Bank Statement imports and normalizes the result for the generic Import wizard. */
public final class BankStatementImportCoordinator {
    private final BankStatementApiClient api;
    private final KotakBankStatementCsvParser parser;

    public BankStatementImportCoordinator() {
        this(new BankStatementApiClient(), new KotakBankStatementCsvParser());
    }

    BankStatementImportCoordinator(BankStatementApiClient api, KotakBankStatementCsvParser parser) {
        this.api = api;
        this.parser = parser;
    }

    public ImportService.ImportResult execute(Path file, boolean dryRun) throws Exception {
        var parsed = parser.parse(file);
        var current = SessionService.current();
        String user = current == null ? "User" : current.getFullName();
        var request = new BankStatementApiClient.ImportRequest(
            parsed.bankName(), parsed.accountNumber(), parsed.accountHolder(), parsed.statementFrom(), parsed.statementTo(),
            parsed.currency(), parsed.openingBalance(), parsed.closingBalance(), parsed.sourceFingerprint(),
            parsed.sourceFileName(), parsed.sourceCsv(), user, dryRun, parsed.rows());
        var result = api.importStatement(request);
        boolean allExisting = !result.alreadyImported() && result.importedRows() == 0 && result.duplicateRows() > 0;
        String action = result.alreadyImported() || allExisting ? "ALREADY CURRENT" : (dryRun ? "VALIDATED" : "IMPORTED");
        String message = result.alreadyImported()
            ? "This exact bank statement was imported previously. Open the existing statement from Bank Statement history."
            : allExisting
                ? "All transactions in this statement were already imported. No bank transactions will be overwritten."
                : (dryRun ? "Server validation passed" : "Bank statement imported");
        var details = List.of(new ImportService.ImportRowResult(
            "1-" + parsed.rows().size(), parsed.sourceFileName(), "PASSED", action, message, "", 0));
        return new ImportService.ImportResult(
            parsed.rows().size(), dryRun ? 0 : result.importedRows(), 0, result.duplicateRows(), List.of(), details);
    }
}
