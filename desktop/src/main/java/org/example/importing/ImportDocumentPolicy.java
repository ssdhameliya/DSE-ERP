package org.example.importing;

import org.example.config.ConfigManager;
import org.example.model.*;
import org.example.service.PartyService;
import org.example.shared.DocumentCalculationEngine;
import org.example.shared.ReferenceFormatRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Business policies shared by Sales/Purchase spreadsheet imports. */
public final class ImportDocumentPolicy {
    private ImportDocumentPolicy() { }

    public static void requireReference(Map<String, String> formats, String key, String value,
                                        LocalDate documentDate, String label) {
        String format = formats == null ? null : formats.get(key);
        if (format == null || format.isBlank()) {
            throw new IllegalStateException(label + " format is not configured in REFERENCE FORMAT (" + key + ")");
        }
        if (!ReferenceFormatRules.matches(format, value, documentDate)) {
            throw new IllegalArgumentException(label + " '" + value + "' does not match " + key + " format " + format);
        }
    }

    public static String normalizeTaxType(String value, String partyReference, boolean sales) {
        String text = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (text.contains("IGST") || text.contains("INTER")) return "IGST";
        if (text.equals("GST") || text.contains("INTRA") || text.contains("CGST") || text.contains("SGST")) return "GST";

        String companyGstin = ConfigManager.get("company.gstin", "").trim();
        try {
            PartyService partyService = new PartyService();
            String type = sales ? "CUSTOMER" : "SUPPLIER";
            Party party = partyService.getByType(type).stream()
                .filter(candidate -> candidate.getPartyCode().equalsIgnoreCase(partyReference))
                .findFirst().orElse(null);
            String partyGstin = party == null || party.getGstin() == null ? "" : party.getGstin().trim();
            if (companyGstin.length() >= 2 && partyGstin.length() >= 2
                && companyGstin.substring(0, 2).matches("\\d{2}")
                && partyGstin.substring(0, 2).matches("\\d{2}")) {
                return companyGstin.substring(0, 2).equals(partyGstin.substring(0, 2)) ? "GST" : "IGST";
            }
        } catch (Exception ignored) { }
        return "GST";
    }

    public static Path resolveAttachment(Path workbookFile, String attachment) {
        if (attachment == null || attachment.isBlank()) return null;
        Path source;
        try { source = Path.of(attachment.trim()); }
        catch (Exception invalid) { throw new IllegalArgumentException("attachment_file is not a valid path: " + attachment); }
        if (!source.isAbsolute()) {
            Path parent = workbookFile.toAbsolutePath().normalize().getParent();
            source = (parent == null ? Path.of("") : parent).resolve(source).normalize();
        }
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("attachment_file was not found: " + attachment);
        return source;
    }

    public static String taxDescription(String taxType, double gstPercent) {
        if ("IGST".equalsIgnoreCase(taxType)) {
            return String.format(Locale.ROOT, "IGST %.2f%% calculated from line values", gstPercent);
        }
        double half = gstPercent / 2.0;
        return String.format(Locale.ROOT, "GST %.2f%% calculated as CGST %.2f%% + SGST %.2f%%", gstPercent, half, half);
    }

    public static void applySalesTotals(Sales document) {
        List<DocumentCalculationEngine.LineInput> lines = document.getLines().stream()
            .map(line -> new DocumentCalculationEngine.LineInput(
                line.getQuantity(), line.getRate(), line.getDiscountPercent(), line.getGstPercent()))
            .toList();
        List<DocumentCalculationEngine.ChargeInput> charges = document.getCharges().stream()
            .map(charge -> new DocumentCalculationEngine.ChargeInput(
                charge.getAmount(), charge.isTaxable(), charge.getGstPercent()))
            .toList();
        DocumentCalculationEngine.Totals totals = DocumentCalculationEngine.totals(
            lines, charges, DocumentCalculationEngine.taxMode(document.getGstType()));
        document.setSubtotal(totals.itemTaxable());
        document.setGstAmount(totals.taxAmount());
        document.setTotalAmount(totals.grandTotal());
    }

    public static void applyPurchaseTotals(Purchase document) {
        List<DocumentCalculationEngine.LineInput> lines = document.getLines().stream()
            .map(line -> new DocumentCalculationEngine.LineInput(
                line.getQuantity(), line.getRate(), line.getDiscountPercent(), line.getGstPercent()))
            .toList();
        List<DocumentCalculationEngine.ChargeInput> charges = document.getCharges().stream()
            .map(charge -> new DocumentCalculationEngine.ChargeInput(
                charge.getAmount(), charge.isTaxable(), charge.getGstPercent()))
            .toList();
        DocumentCalculationEngine.Totals totals = DocumentCalculationEngine.totals(
            lines, charges, DocumentCalculationEngine.taxMode(document.getGstType()));
        document.setSubtotal(totals.itemTaxable());
        document.setGstAmount(totals.taxAmount());
        document.setTotalAmount(totals.grandTotal());
    }

    public static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root == null ? null : root.getMessage();
        return message == null || message.isBlank()
            ? (root == null ? "Import save failed" : root.getClass().getSimpleName())
            : message.trim();
    }
}
