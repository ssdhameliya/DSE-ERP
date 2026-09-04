package org.example.importing;

import org.example.service.ImportService;

import java.util.Locale;

/** Determines import completion semantics independently from JavaFX dialogs. */
public final class ImportResultPolicy {
    private ImportResultPolicy() { }
    public enum Semantic { INFORMATION, WARNING, ERROR }

    public record Presentation(Semantic semantic, String header, boolean warning, int succeeded) { }

    public static Presentation presentation(ImportService.ImportResult result, boolean dryRun) {
        int failed = result.failedCount();
        boolean warning = !dryRun && hasWarnings(result);
        int succeeded = result.imported + result.updated;
        Semantic semantic = dryRun || (failed == 0 && !warning)
            ? Semantic.INFORMATION
            : (succeeded > 0 || failed == 0 ? Semantic.WARNING : Semantic.ERROR);
        String header;
        if (dryRun) header = "Validation completed";
        else if (failed == 0 && succeeded == 0 && result.skipped > 0 && !warning) header = "Import completed — no changes required";
        else if (failed == 0 && !warning) header = "Import completed successfully";
        else if (failed == 0 || succeeded > 0) header = "Import completed with warnings";
        else header = "Import could not be completed";
        return new Presentation(semantic, header, warning, succeeded);
    }

    public static boolean hasWarnings(ImportService.ImportResult result) {
        if (result == null) return false;
        if (result.failedCount() > 0) return true;
        return result.details.stream().anyMatch(row ->
            (row.action != null && row.action.toUpperCase(Locale.ROOT).contains("WARNING"))
                || (row.message != null && row.message.toLowerCase(Locale.ROOT).contains("warning")));
    }
}
