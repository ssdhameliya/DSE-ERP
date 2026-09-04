package org.example.document;

import org.example.model.Item;
import org.example.model.Party;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Shared Sales/Purchase display and lookup rules; contains no JavaFX state. */
public final class DocumentLookupPolicy {
    private DocumentLookupPolicy() { }

    public static String safe(String value) { return value == null ? "" : value.trim(); }

    public static String itemHaystack(Item item) {
        if (item == null) return "";
        return (safe(item.getItemCode()) + " " + safe(item.getDescription()) + " " + safe(item.getRemarks()) + " "
            + safe(item.getCategory()) + " " + safe(item.getHsn()) + " " + safe(item.getUnit()) + " " + item.getGst())
            .toLowerCase(Locale.ROOT);
    }

    public static String itemDisplay(Item item) {
        if (item == null) return "";
        String code = safe(item.getItemCode());
        String description = safe(item.getDescription());
        if (code.isBlank()) return description;
        return description.isBlank() ? code : code + " - " + description;
    }

    public static String itemSuggestionDisplay(Item item) {
        if (item == null) return "";
        return itemDisplay(item)
            + "  |  Category: " + valueOrDash(item.getCategory())
            + "  |  HSN: " + valueOrDash(item.getHsn())
            + "  |  Unit: " + valueOrDash(item.getUnit())
            + "  |  GST: " + String.format(Locale.ROOT, "%.2f%%", item.getGst());
    }

    public static String itemRemark(Item item) { return item == null ? "" : safe(item.getRemarks()); }

    public static String valueOrDash(String value) {
        String normalized = safe(value);
        return normalized.isBlank() ? "-" : normalized;
    }

    public static String partyDisplay(Party party) {
        return party == null ? "" : safe(party.getPartyCode()) + " - " + safe(party.getName());
    }

    public static String itemNameForDisplay(String itemCode, String persistedDescription, List<Item> items) {
        String code = safe(itemCode);
        if (!code.isBlank() && items != null) {
            for (Item item : items) {
                if (code.equalsIgnoreCase(safe(item.getItemCode()))) {
                    String name = itemDisplay(item);
                    if (!name.isBlank()) return name;
                }
            }
        }
        String fallback = safe(persistedDescription);
        int separator = fallback.indexOf(" - ");
        return separator >= 0 && separator + 3 < fallback.length() ? fallback.substring(separator + 3).trim() : fallback;
    }

    public static Optional<String> suggestedGstType(String companyGstin, String partyGstin, List<String> options) {
        String company = safe(companyGstin);
        String party = safe(partyGstin);
        if (company.length() < 2 || party.length() < 2
            || !company.substring(0, 2).matches("\\d{2}") || !party.substring(0, 2).matches("\\d{2}")) {
            return Optional.empty();
        }
        boolean interstate = !company.substring(0, 2).equals(party.substring(0, 2));
        if (options == null) return Optional.empty();
        return options.stream().filter(value -> {
            String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
            return interstate
                ? normalized.contains("IGST") || normalized.contains("INTER")
                : (normalized.contains("GST") && !normalized.contains("IGST"))
                    || normalized.contains("INTRA") || normalized.contains("CGST") || normalized.contains("SGST");
        }).findFirst();
    }

    public static String normalizedKey(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }
}
