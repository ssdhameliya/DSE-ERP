package org.example.config;

import org.example.documentstudio.model.DocumentType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Canonical workspace storage resolver. Business document generators and exports
 * must resolve their destination through this class instead of inventing folders.
 *
 * Business documents are permanent by default. Cleanup/retention is intentionally
 * handled separately and never targets Documents, Attachments, Backups or Database.
 */
public final class WorkspaceStorageManager {
    private WorkspaceStorageManager() { }

    public static Path documentsRoot() throws IOException {
        return ensure(WorkspaceManager.getWorkspaceRoot().resolve("Documents"));
    }

    public static Path reportsRoot() throws IOException {
        return ensure(WorkspaceManager.getReportsFolder());
    }

    public static Path exportsRoot() throws IOException {
        return ensure(WorkspaceManager.getExportsFolder());
    }

    public static Path desktopLogs() throws IOException {
        return ensure(WorkspaceManager.getLogsFolder().resolve("Desktop"));
    }

    public static Path serverLogs() throws IOException {
        return ensure(WorkspaceManager.getLogsFolder().resolve("Server"));
    }

    public static Path postgresLogs() throws IOException {
        return ensure(WorkspaceManager.getLogsFolder().resolve("PostgreSQL"));
    }

    public static Path archivedLogs() throws IOException {
        return ensure(WorkspaceManager.getLogsFolder().resolve("Archive"));
    }

    public static Path diagnostics() throws IOException {
        return ensure(WorkspaceManager.getExportsFolder().resolve("Diagnostics"));
    }

    public static Path reportFolder(String category, LocalDate date) throws IOException {
        LocalDate effective = date == null ? LocalDate.now() : date;
        return ensure(reportsRoot()
                .resolve(safeSegment(category == null || category.isBlank() ? "General" : category))
                .resolve(financialYear(effective))
                .resolve(String.format(Locale.ROOT, "%02d", effective.getMonthValue())));
    }

    public static Path exportFolder(String format) throws IOException {
        String normalized = format == null ? "General" : format.trim().toUpperCase(Locale.ROOT);
        return ensure(exportsRoot().resolve(switch (normalized) {
            case "XLS", "XLSX", "EXCEL" -> "Excel";
            case "CSV" -> "CSV";
            case "PDF" -> "PDF";
            case "DIAGNOSTIC", "DIAGNOSTICS", "ZIP" -> "Diagnostics";
            default -> "General";
        }));
    }

    /** Returns a full output file path and creates its module/FY/reference folder. */
    public static Path documentFile(DocumentType type, String reference, LocalDate documentDate, String fileName) throws IOException {
        LocalDate effectiveDate = documentDate == null ? LocalDate.now() : documentDate;
        String safeReference = safeSegment(reference == null || reference.isBlank() ? "Unreferenced" : reference);
        DocumentLocation location = location(type);
        Path folder = documentsRoot().resolve(location.module())
                .resolve(financialYear(effectiveDate))
                .resolve(location.documentFolder())
                .resolve(safeReference);
        return ensure(folder).resolve(safeFileName(fileName));
    }

    public static String financialYear(LocalDate date) {
        LocalDate effective = date == null ? LocalDate.now() : date;
        int start = effective.getMonthValue() >= 4 ? effective.getYear() : effective.getYear() - 1;
        return start + "-" + String.format(Locale.ROOT, "%02d", (start + 1) % 100);
    }

    public static String yearMonth(LocalDate date) {
        LocalDate effective = date == null ? LocalDate.now() : date;
        return YearMonth.from(effective).toString();
    }

    public static String safeSegment(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("\\s+", " ").replaceAll("[. ]+$", "");
        if (clean.isBlank() || clean.equals(".") || clean.equals("..")) return "Unspecified";
        return clean.length() > 100 ? clean.substring(0, 100).trim() : clean;
    }

    public static String safeFileName(String value) {
        String clean = value == null ? "file" : value.trim().replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("[. ]+$", "");
        return clean.isBlank() ? "file" : clean;
    }

    private static Path ensure(Path folder) throws IOException {
        Files.createDirectories(folder);
        return folder.toAbsolutePath().normalize();
    }

    private static DocumentLocation location(DocumentType type) {
        if (type == null) return new DocumentLocation("General", "Documents");
        return switch (type) {
            case SALES_INVOICE -> new DocumentLocation("Sales", "Tax-Invoices");
            case SALES_RETURN -> new DocumentLocation("Sales", "Returns");
            case DELIVERY_CHALLAN -> new DocumentLocation("Sales", "Delivery-Challans");
            case CREDIT_NOTE -> new DocumentLocation("Sales", "Credit-Notes");
            case PAYMENT_RECEIPT -> new DocumentLocation("Sales", "Payment-Receipts");
            case PURCHASE_INVOICE -> new DocumentLocation("Purchase", "Purchase-Invoices");
            case PURCHASE_RETURN -> new DocumentLocation("Purchase", "Returns");
            case PURCHASE_ORDER -> new DocumentLocation("Purchase", "Purchase-Orders");
            case DEBIT_NOTE -> new DocumentLocation("Purchase", "Debit-Notes");
            case QUOTATION -> new DocumentLocation("Quotations", "Quotations");
            case GENERAL_PDF, CUSTOM_ERP -> new DocumentLocation("General", "Documents");
        };
    }

    private record DocumentLocation(String module, String documentFolder) { }
}
