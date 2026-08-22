package org.example.documentstudio.service;

import org.example.documentstudio.model.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Heuristic mapper used by PDF Studio V2 after importing a PDF.
 *
 * <p>The mapper never changes ERP data. It compares extracted PDF text with the selected real
 * ERP record and the document field catalogue. High-confidence value matches can be applied
 * automatically; lower-confidence label matches are surfaced for review.</p>
 */
public final class PdfAutoMappingService {
    private PdfAutoMappingService() {}

    public record Mapping(PdfTextRegion region, String fieldKey, String expression, double confidence, String reason) {}
    public record ChargeRegion(int pageIndex, double x, double y, double width, double height, double rowHeight, List<PdfTextRegion> sourceRegions) {
        public ChargeRegion { sourceRegions = sourceRegions == null ? List.of() : List.copyOf(sourceRegions); }
    }
    public record Analysis(List<Mapping> mappings, int detected, int highConfidence, int needsReview, int unmapped) {
        public Analysis {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
            detected = Math.max(0, detected);
            highConfidence = Math.max(0, highConfidence);
            needsReview = Math.max(0, needsReview);
            unmapped = Math.max(0, unmapped);
        }
        public int mappedCount() { return highConfidence + needsReview; }
        public int percentage() { return detected <= 0 ? 0 : (int)Math.round(mappedCount() * 100.0 / detected); }
    }

    private static final Map<String, List<String>> LABEL_ALIASES = Map.ofEntries(
            // Sales
            Map.entry("sales.number", List.of("invoice no", "invoice number", "sales invoice no")),
            Map.entry("sales.date", List.of("invoice date", "sales date")),
            Map.entry("sales.referenceNo", List.of("po no", "purchase order no", "customer po", "reference no")),
            Map.entry("sales.orderNo", List.of("order no", "sales order no")),
            Map.entry("sales.poDate", List.of("po date", "purchase order date")),
            Map.entry("sales.billingAddress", List.of("billing address", "bill to")),
            Map.entry("sales.deliveryAddress", List.of("delivery address", "ship to", "shipping address")),
            Map.entry("sales.billingGstin", List.of("billing gstin", "billing gst-in")),
            Map.entry("sales.deliveryGstin", List.of("delivery gstin", "shipping gstin", "delivery gst-in")),
            Map.entry("sales.transporter", List.of("transporter", "transport")),
            Map.entry("sales.transporterGstin", List.of("transporter gstin", "transport gstin")),
            Map.entry("sales.contactPerson", List.of("contact person", "contact name")),
            Map.entry("sales.contactMobile", List.of("contact details", "contact mobile", "mobile")),
            Map.entry("sales.paymentTerms", List.of("payment terms", "credit terms")),

            // Purchase
            Map.entry("purchase.number", List.of("purchase no", "purchase invoice no", "invoice no", "document no")),
            Map.entry("purchase.date", List.of("purchase date", "invoice date", "document date")),
            Map.entry("purchase.referenceNo", List.of("supplier invoice no", "reference no")),
            Map.entry("purchase.orderNo", List.of("po no", "purchase order no", "order no")),
            Map.entry("purchase.poDate", List.of("po date", "purchase order date")),
            Map.entry("purchase.billingAddress", List.of("billing address", "bill to")),
            Map.entry("purchase.deliveryAddress", List.of("delivery address", "ship to")),
            Map.entry("purchase.billingGstin", List.of("billing gstin", "billing gst-in")),
            Map.entry("purchase.deliveryGstin", List.of("delivery gstin", "delivery gst-in")),
            Map.entry("purchase.transporter", List.of("transporter", "transport")),
            Map.entry("purchase.transporterGstin", List.of("transporter gstin", "transport gstin")),
            Map.entry("purchase.contactPerson", List.of("contact person", "contact name")),
            Map.entry("purchase.contactMobile", List.of("contact details", "contact mobile", "mobile")),
            Map.entry("purchase.paymentTerms", List.of("payment terms", "credit terms")),

            // Other supported ERP document types
            Map.entry("quotation.number", List.of("quotation no", "quote no")),
            Map.entry("quotation.date", List.of("quotation date", "quote date")),
            Map.entry("delivery.number", List.of("challan no", "delivery challan no")),
            Map.entry("delivery.date", List.of("challan date", "delivery date")),
            Map.entry("return.number", List.of("return no", "credit note no", "debit note no", "note no")),
            Map.entry("return.date", List.of("return date", "credit note date", "debit note date")),
            Map.entry("receipt.number", List.of("receipt no", "receipt number")),
            Map.entry("receipt.date", List.of("receipt date")),
            Map.entry("receipt.amount", List.of("receipt amount", "amount received")),

            // Party / totals / payment / company
            Map.entry("customer.name", List.of("customer", "buyer", "party name")),
            Map.entry("customer.gstin", List.of("customer gstin", "buyer gstin", "gst-in", "gstin")),
            Map.entry("supplier.name", List.of("supplier", "vendor", "party name")),
            Map.entry("supplier.gstin", List.of("supplier gstin", "vendor gstin", "gst-in", "gstin")),
            Map.entry("party.name", List.of("party name", "customer", "supplier")),
            Map.entry("party.gstin", List.of("party gstin", "gst-in", "gstin")),
            Map.entry("totals.subtotal", List.of("basic amount", "subtotal", "sub total")),
            Map.entry("totals.grossBeforeTax", List.of("taxable amount", "gross total", "gross before tax")),
            Map.entry("totals.cgstAmount", List.of("cgst")),
            Map.entry("totals.sgstAmount", List.of("sgst")),
            Map.entry("totals.igstAmount", List.of("igst")),
            Map.entry("totals.roundOff", List.of("round off", "rounding")),
            Map.entry("totals.roundedGrandTotal", List.of("grand total", "net total", "invoice total", "amount payable")),
            Map.entry("totals.grandTotal", List.of("grand total", "net total", "invoice total", "amount payable")),
            Map.entry("totals.amountInWords", List.of("amount in words", "inr")),
            Map.entry("payment.bankName", List.of("bank name")),
            Map.entry("payment.branch", List.of("branch")),
            Map.entry("payment.accountNumber", List.of("a/c no", "account no", "account number")),
            Map.entry("payment.ifsc", List.of("ifsc", "ifsc code")),
            Map.entry("company.gstin", List.of("supplier gst", "company gst", "our gstin")),
            Map.entry("company.name", List.of("for,"))
    );
    /** Finds a saved ERP record whose document id is visibly printed in the imported PDF. */
    public static Optional<DocumentSample> findLikelySample(DocumentType type, List<PdfTextRegion> regions) {
        if (!DocumentDataService.supportsRealData(type) || regions == null || regions.isEmpty()) return Optional.empty();
        String pageText = normalize(regions.stream().map(PdfTextRegion::text).reduce("", (a,b) -> a + " " + b));
        return DocumentDataService.listSamples(type).stream()
                .filter(Objects::nonNull)
                .filter(sample -> !normalize(sample.id()).isBlank())
                .filter(sample -> pageText.contains(normalize(sample.id())))
                .findFirst();
    }

    public static Analysis analyze(DocumentType type, List<PdfTextRegion> regions, TemplateData data) {
        if (regions == null || regions.isEmpty()) return new Analysis(List.of(), 0, 0, 0, 0);
        Map<String,String> values = data == null ? Map.of() : data.values();
        Set<String> allowedKeys = new LinkedHashSet<>();
        for (TemplateFieldDefinition field : TemplateFieldCatalog.pdfFieldsFor(type)) allowedKeys.add(field.key());

        List<Mapping> out = new ArrayList<>();
        Set<PdfTextRegion> used = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> mappedKeys = new HashSet<>();

        // First pass: actual printed value matches. This is the strongest signal because it compares
        // the imported PDF against the authoritative selected ERP record.
        for (PdfTextRegion region : regions) {
            String regionNorm = normalize(region.text());
            if (regionNorm.isBlank()) continue;
            Candidate best = null;
            for (Map.Entry<String,String> entry : values.entrySet()) {
                if (!allowedKeys.contains(entry.getKey())) continue;
                String raw = clean(entry.getValue());
                String valueNorm = normalize(raw);
                if (valueNorm.length() < 2 || isWeakValue(valueNorm)) continue;
                double score = scoreValue(regionNorm, valueNorm);
                if (score <= 0) continue;
                if (best == null || score > best.score + .0001
                        || (Math.abs(score-best.score) <= .0001 && valueNorm.length() > normalize(best.rawValue).length()))
                    best = new Candidate(entry.getKey(), raw, score);
            }
            if (best != null && best.score >= .90) {
                String expression = replaceValue(region.text(), best.rawValue, "{{" + best.key + "}}");
                out.add(new Mapping(region, best.key, expression, Math.min(.99, best.score), "ERP value match"));
                used.add(region); mappedKeys.add(best.key);
            }
        }

        // Second pass: label/proximity suggestions. We only suggest a nearby value region and leave
        // it amber for review; labels themselves remain static design content.
        for (int i = 0; i < regions.size(); i++) {
            PdfTextRegion label = regions.get(i);
            String labelNorm = normalize(label.text());
            if (labelNorm.isBlank()) continue;
            for (Map.Entry<String,List<String>> alias : LABEL_ALIASES.entrySet()) {
                String key = alias.getKey();
                if (!allowedKeys.contains(key) || mappedKeys.contains(key)) continue;
                boolean match = alias.getValue().stream().map(PdfAutoMappingService::normalize)
                        .anyMatch(a -> !a.isBlank() && labelNorm.contains(a));
                if (!match) continue;
                PdfTextRegion valueRegion = nearestValueRegion(label, regions, used);
                if (valueRegion == null) continue;
                String raw = clean(values.get(key));
                String expression;
                if (!raw.isBlank() && normalize(valueRegion.text()).contains(normalize(raw))) {
                    expression = replaceValue(valueRegion.text(), raw, "{{" + key + "}}");
                } else if (looksLikeLabelAndValue(label.text()) && valueRegion == label) {
                    expression = label.text() + " {{" + key + "}}";
                } else {
                    expression = "{{" + key + "}}";
                }
                out.add(new Mapping(valueRegion, key, expression, .74, "Label/proximity match"));
                used.add(valueRegion); mappedKeys.add(key);
            }
        }

        int detected = detectedMappableRegions(type, regions, values, data);
        int high = (int)out.stream().filter(m -> m.confidence() >= .90).count();
        int review = out.size() - high;
        detected = Math.max(detected, out.size());
        int unmapped = Math.max(0, detected - out.size());
        return new Analysis(out, detected, high, review, unmapped);
    }

    /** True when the page visually resembles an item table and should offer an item repeater. */
    public static Optional<PdfTextRegion> detectItemHeader(List<PdfTextRegion> regions) {
        if (regions == null) return Optional.empty();
        return regions.stream().filter(r -> {
            String n = normalize(r.text());
            int hits = 0;
            for (String token : List.of("hsn", "product description", "description", "qty", "quantity", "rate", "unit", "amount", "sr no"))
                if (n.contains(token)) hits++;
            return hits >= 3;
        }).findFirst();
    }

    /** Detects the printed invoice-level charge rows that can be replaced by one ERP charge repeater. */
    public static Optional<ChargeRegion> detectChargeRegion(List<PdfTextRegion> regions, TemplateData data) {
        if (regions == null || regions.isEmpty() || data == null || data.charges().isEmpty()) return Optional.empty();
        Map<Integer,List<PdfTextRegion>> byPage = new LinkedHashMap<>();
        for (PdfTextRegion region : regions) byPage.computeIfAbsent(region.pageIndex(), ignored -> new ArrayList<>()).add(region);

        ChargeRegion best = null;
        int bestMatches = 0;
        for (Map.Entry<Integer,List<PdfTextRegion>> page : byPage.entrySet()) {
            List<PdfTextRegion> pageRegions = page.getValue();
            LinkedHashSet<PdfTextRegion> matched = new LinkedHashSet<>();
            int matchedCharges = 0;
            for (TemplateCharge charge : data.charges()) {
                if (charge == null) continue;
                String type = normalize(charge.type());
                if (type.isBlank()) continue;
                PdfTextRegion typeRegion = pageRegions.stream()
                        .filter(r -> normalize(r.text()).contains(type) || type.contains(normalize(r.text())))
                        .min(Comparator.comparingDouble(PdfTextRegion::y)).orElse(null);
                if (typeRegion == null) continue;
                matchedCharges++;
                matched.add(typeRegion);
                Set<String> numeric = new LinkedHashSet<>();
                for (double value : List.of(charge.amount(), charge.taxAmount(), charge.total())) {
                    numeric.add(normalize(numberText(value)));
                    numeric.add(normalize(moneyText(value)));
                }
                for (PdfTextRegion candidate : pageRegions) {
                    if (candidate == typeRegion) continue;
                    double dy = Math.abs((candidate.y()+candidate.height()/2) - (typeRegion.y()+typeRegion.height()/2));
                    if (dy > Math.max(12, typeRegion.height()*1.8)) continue;
                    String n = normalize(candidate.text());
                    if (n.isBlank()) continue;
                    if (numeric.stream().anyMatch(v -> !v.isBlank() && (n.equals(v) || n.contains(v)))) matched.add(candidate);
                }
            }
            if (matchedCharges == 0 || matched.isEmpty()) continue;
            double minX = matched.stream().mapToDouble(PdfTextRegion::x).min().orElse(0);
            double minY = matched.stream().mapToDouble(PdfTextRegion::y).min().orElse(0);
            double maxX = matched.stream().mapToDouble(r -> r.x()+r.width()).max().orElse(minX+1);
            double maxY = matched.stream().mapToDouble(r -> r.y()+r.height()).max().orElse(minY+1);
            double rowHeight = Math.max(12, matched.stream().mapToDouble(PdfTextRegion::height).average().orElse(9) + 4);
            double height = Math.max(rowHeight * Math.max(1, matchedCharges), maxY-minY+3);
            ChargeRegion candidate = new ChargeRegion(page.getKey(), Math.max(0,minX-2), Math.max(0,minY-1),
                    Math.max(80,maxX-minX+4), height, rowHeight, new ArrayList<>(matched));
            if (best == null || matchedCharges > bestMatches) { best = candidate; bestMatches = matchedCharges; }
        }
        return Optional.ofNullable(best);
    }

    private static String numberText(double value) {
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(Locale.ENGLISH, "%.2f", value);
    }

    private static String moneyText(double value) { return String.format(Locale.ENGLISH, "%,.2f", value); }

    private static int detectedMappableRegions(DocumentType type, List<PdfTextRegion> regions, Map<String,String> values, TemplateData data) {
        int count = 0;
        Set<String> seen = new HashSet<>();
        Set<String> allowed = new HashSet<>();
        for (TemplateFieldDefinition f : TemplateFieldCatalog.pdfFieldsFor(type)) allowed.add(f.key());
        for (PdfTextRegion r : regions) {
            String n = normalize(r.text());
            if (n.isBlank()) continue;
            boolean candidate = values.entrySet().stream().anyMatch(e -> allowed.contains(e.getKey())
                    && normalize(e.getValue()).length() >= 2 && n.contains(normalize(e.getValue())));
            if (!candidate) {
                candidate = LABEL_ALIASES.entrySet().stream().anyMatch(e -> allowed.contains(e.getKey())
                        && e.getValue().stream().anyMatch(a -> n.contains(normalize(a))));
            }
            if (candidate && seen.add(Math.round(r.x()) + ":" + Math.round(r.y()) + ":" + n)) count++;
        }
        if (detectItemHeader(regions).isPresent()) count++;
        if (detectChargeRegion(regions, data).isPresent()) count++;
        return count;
    }

    private static PdfTextRegion nearestValueRegion(PdfTextRegion label, List<PdfTextRegion> all, Set<PdfTextRegion> used) {
        PdfTextRegion best = null; double bestScore = Double.MAX_VALUE;
        for (PdfTextRegion candidate : all) {
            if (candidate == label || used.contains(candidate)) continue;
            double dy = Math.abs((candidate.y() + candidate.height()/2) - (label.y() + label.height()/2));
            double dx = candidate.x() - (label.x() + label.width());
            boolean right = dx >= -3 && dx <= 260 && dy <= Math.max(16, label.height() * 1.8);
            boolean below = candidate.y() >= label.y() && candidate.y() - (label.y()+label.height()) <= 26
                    && Math.abs(candidate.x()-label.x()) <= 55;
            if (!right && !below) continue;
            double score = (right ? Math.max(0, dx) : 80) + dy * 4;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private record Candidate(String key, String rawValue, double score) {}

    private static double scoreValue(String region, String value) {
        if (region.equals(value)) return .995;
        String compactRegion = compact(region), compactValue = compact(value);
        if (compactRegion.equals(compactValue)) return .994;
        if (region.contains(value) || (!compactValue.isBlank() && compactRegion.contains(compactValue))) {
            double coverage = compactValue.length() / (double)Math.max(1, compactRegion.length());
            return Math.min(.989, .90 + .089 * coverage);
        }
        if ((value.contains(region) || (!compactRegion.isBlank() && compactValue.contains(compactRegion))) && compactRegion.length() >= 6) {
            double coverage = compactRegion.length() / (double)Math.max(1, compactValue.length());
            return Math.min(.94, .89 + .05 * coverage);
        }
        return 0;
    }

    private static String compact(String normalized) { return normalized == null ? "" : normalized.replace(" ", ""); }

    private static boolean isWeakValue(String value) {
        if (value.isBlank()) return true;
        if (value.matches("[0.,%₹$€£-]+")) return value.replaceAll("[^0-9]", "").length() < 3;
        return Set.of("yes", "no", "na", "n a", "0", "0 00").contains(value);
    }

    private static boolean looksLikeLabelAndValue(String text) { return text != null && text.contains(":"); }

    private static String replaceValue(String source, String rawValue, String token) {
        if (source == null || source.isBlank() || rawValue == null || rawValue.isBlank()) return token;
        String lower = source.toLowerCase(Locale.ROOT);
        String needle = rawValue.toLowerCase(Locale.ROOT).trim();
        int index = lower.indexOf(needle);
        if (index >= 0) return source.substring(0,index) + token + source.substring(index + rawValue.trim().length());

        Double targetNumber = parseNumber(rawValue);
        if (targetNumber != null) {
            java.util.regex.Matcher matcher = Pattern.compile("(?<![A-Za-z0-9])[-+]?\\d[\\d,]*(?:\\.\\d+)?%?").matcher(source);
            while (matcher.find()) {
                Double candidate = parseNumber(matcher.group());
                if (candidate != null && Math.abs(candidate-targetNumber) <= Math.max(.0001, Math.abs(targetNumber)*1e-9))
                    return source.substring(0,matcher.start()) + token + source.substring(matcher.end());
            }
        }
        return token;
    }

    private static Double parseNumber(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replace(",", "").replace("₹", "").replace("$", "").replace("€", "").replace("£", "");
        boolean percent = cleaned.endsWith("%");
        if (percent) cleaned = cleaned.substring(0, cleaned.length()-1).trim();
        if (!cleaned.matches("[-+]?\\d+(?:\\.\\d+)?")) return null;
        try { return Double.parseDouble(cleaned); } catch (NumberFormatException ignored) { return null; }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public static String normalize(String value) {
        if (value == null) return "";
        String v = value.toLowerCase(Locale.ROOT)
                .replace('\u00a0',' ')
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[^a-z0-9%]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return v;
    }
}
