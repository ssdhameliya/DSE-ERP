package org.example.documentstudio.service;

import org.example.documentstudio.model.*;

import java.util.*;
import java.util.stream.Collectors;

/** Search/ranking for ERP fields used by both the left palette and contextual inspector picker. */
public final class TemplateFieldSearchService {
    private static final Map<String,List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("qty", List.of("quantity")),
            Map.entry("product", List.of("item", "description", "code")),
            Map.entry("remark", List.of("remarks", "description with remarks")),
            Map.entry("remarks", List.of("remark", "description with remarks")),
            Map.entry("hsn", List.of("hsn", "sac")),
            Map.entry("gst", List.of("gst", "cgst", "sgst", "igst", "tax")),
            Map.entry("tax", List.of("gst", "cgst", "sgst", "igst", "taxable")),
            Map.entry("invoice", List.of("document", "number", "sales", "purchase")),
            Map.entry("bill", List.of("billing", "customer", "party")),
            Map.entry("ship", List.of("shipping", "delivery")),
            Map.entry("vendor", List.of("supplier", "party")),
            Map.entry("price", List.of("rate", "amount")),
            Map.entry("total", List.of("grand total", "amount", "rounded")),
            Map.entry("round", List.of("round off", "rounded grand total")),
            Map.entry("po", List.of("purchase order", "reference", "order number"))
    );

    private TemplateFieldSearchService() {}

    public static List<TemplateFieldDefinition> search(DocumentType type, String query,
                                                        TemplateMappingRequirement context,
                                                        Set<String> alreadyMapped) {
        List<TemplateFieldDefinition> fields = TemplateFieldCatalog.pdfFieldsFor(type);
        if (fields.isEmpty()) return List.of();
        String normalized = normalize(query);
        Set<String> contextKeys = context == null ? Set.of() : Set.copyOf(context.acceptedFields());
        Set<String> mapped = alreadyMapped == null ? Set.of() : alreadyMapped;
        return fields.stream()
                .map(field -> Map.entry(field, score(field, normalized, context, contextKeys, mapped)))
                .filter(entry -> normalized.isBlank() || entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<TemplateFieldDefinition,Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().category())
                        .thenComparing(e -> e.getKey().label()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static int score(TemplateFieldDefinition field, String query, TemplateMappingRequirement context,
                             Set<String> contextKeys, Set<String> mapped) {
        String label = normalize(field.label());
        String key = normalize(field.key());
        String category = normalize(field.category());
        int score = 0;
        if (contextKeys.contains(field.key())) score += 1000;
        if (context != null) {
            String requirement = normalize(context.label() + " " + String.join(" ", context.searchAliases()));
            score += tokenScore(label + " " + key + " " + category, requirement) * 4;
        }
        if (!query.isBlank()) {
            if (label.equals(query)) score += 500;
            if (label.startsWith(query)) score += 300;
            if (label.contains(query)) score += 180;
            if (key.contains(query)) score += 130;
            if (category.contains(query)) score += 80;
            score += tokenScore(label + " " + key + " " + category, expanded(query)) * 20;
        } else score += 10;
        if (mapped.contains(field.key())) score -= 20;
        return score;
    }

    private static int tokenScore(String haystack, String needle) {
        int hits = 0;
        for (String token : needle.split("\\s+")) if (token.length() > 1 && haystack.contains(token)) hits++;
        return hits;
    }

    private static String expanded(String query) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>(List.of(query.split("\\s+")));
        for (String token : List.copyOf(tokens)) tokens.addAll(SYNONYMS.getOrDefault(token, List.of()));
        return String.join(" ", tokens);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
