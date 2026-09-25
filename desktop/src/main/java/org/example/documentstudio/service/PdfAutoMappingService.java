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
    /** Raw label/value candidate for Review Mapping. suggestedField may intentionally be blank. */
    public record SourceCandidate(PdfTextRegion region, String sourceLabel, String sourceValue, String suggestedField,
                                  String expression, double confidence, String reason) {
        public SourceCandidate {
            sourceLabel = sourceLabel == null ? "" : sourceLabel.trim();
            sourceValue = sourceValue == null ? "" : sourceValue.trim();
            suggestedField = suggestedField == null ? "" : suggestedField.trim();
            expression = expression == null ? "" : expression;
            confidence = Math.max(0, Math.min(1, confidence));
            reason = reason == null ? "" : reason;
        }
    }
    public record ItemHeaderCell(String label, double x, double y, double width, double height,
                                 String suggestedField, double confidence) {
        public ItemHeaderCell {
            label = label == null ? "" : label.trim();
            suggestedField = suggestedField == null ? "" : suggestedField.trim();
            confidence = Math.max(0, Math.min(1, confidence));
        }
    }
    public record ItemHeaderLayout(int pageIndex, double x, double y, double width, double height,
                                   List<ItemHeaderCell> cells) {
        public ItemHeaderLayout { cells = cells == null ? List.of() : List.copyOf(cells); }
    }
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

    // Field aliases are owned by TemplateFieldCatalog; this detector consumes that metadata.

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
        List<TemplateFieldDefinition> fields = TemplateFieldCatalog.pdfFieldsFor(type);
        Map<String,TemplateFieldDefinition> byKey = new LinkedHashMap<>();
        for (TemplateFieldDefinition field : fields) byKey.putIfAbsent(field.key(), field);
        Set<String> allowedKeys = byKey.keySet();

        List<Mapping> out = new ArrayList<>();
        Set<PdfTextRegion> used = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> mappedKeys = new HashSet<>();

        // First pass: compare printed values with the selected real ERP sample.
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

        // Second pass: catalogue alias + physical label/value proximity. Inline "Label : Value"
        // is parsed directly instead of asking nearestValueRegion() to return the label itself.
        for (PdfTextRegion label : regions) {
            String labelNorm = normalize(label.text());
            if (labelNorm.isBlank()) continue;
            for (TemplateFieldDefinition field : fields) {
                String key = field.key();
                if (mappedKeys.contains(key)) continue;
                Optional<String> aliasMatch = aliasesFor(field).stream().filter(a -> labelNorm.contains(normalize(a)))
                        .max(Comparator.comparingInt(String::length));
                if (aliasMatch.isEmpty()) continue;
                PdfTextRegion valueRegion;
                String expression;
                if (looksLikeLabelAndValue(label.text())) {
                    valueRegion = PdfTextExtractionService.valueHitRegion(label).orElse(null);
                    if (valueRegion == null || normalize(valueRegion.text()).isBlank()) continue;
                } else {
                    valueRegion = nearestValueRegion(label, regions, used);
                    if (valueRegion == null) continue;
                }
                String raw = clean(values.get(key));
                if (!raw.isBlank() && normalize(valueRegion.text()).contains(normalize(raw)))
                    expression = replaceValue(valueRegion.text(), raw, "{{" + key + "}}");
                else expression = "{{" + key + "}}";
                out.add(new Mapping(valueRegion, key, expression, .74, "Catalogue label/proximity match"));
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

    /**
     * Detect source label/value candidates independently of whether semantic matching succeeds.
     * This is the input for the generic Review Mapping popup, so an unknown Custom/XYZ value can
     * remain visible as REVIEW_REQUIRED instead of disappearing from the session.
     */
    public static List<SourceCandidate> detectReviewCandidates(DocumentType type, List<PdfTextRegion> regions,
                                                                TemplateData data, List<Mapping> knownMappings) {
        if (regions == null || regions.isEmpty()) return List.of();
        List<TemplateFieldDefinition> fields = TemplateFieldCatalog.pdfFieldsFor(type);
        Map<String,Mapping> mappedByRegion = new LinkedHashMap<>();
        if (knownMappings != null) for (Mapping mapping : knownMappings) {
            if (mapping != null && mapping.region() != null) mappedByRegion.put(regionKey(mapping.region()), mapping);
        }
        List<SourceCandidate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Preserve all successful value/label mappings first.
        for (Mapping mapping : mappedByRegion.values()) {
            String label = nearestLabelText(mapping.region(), regions);
            addCandidate(out, seen, new SourceCandidate(mapping.region(), label, mapping.region().text(), mapping.fieldKey(),
                    mapping.expression(), mapping.confidence(), mapping.reason()));
        }

        // Discover inline label:value pairs and physically adjacent label/value pairs even when the
        // ERP catalogue has no suggestion. Structure is detected first; semantics are optional.
        for (PdfTextRegion labelRegion : regions) {
            if (labelRegion == null) continue;
            String rawLabel = clean(labelRegion.text());
            String normalized = normalize(rawLabel);
            if (!looksLikePotentialLabel(rawLabel, normalized)) continue;

            PdfTextRegion valueRegion = null;
            String sourceLabel = stripLabelDelimiter(rawLabel);
            if (looksLikeLabelAndValue(rawLabel)) {
                int colon = rawLabel.indexOf(':');
                if (colon > 0) sourceLabel = stripLabelDelimiter(rawLabel.substring(0, colon));
                valueRegion = PdfTextExtractionService.valueHitRegion(labelRegion).orElse(null);
            } else {
                valueRegion = nearestValueRegion(labelRegion, regions, Collections.emptySet());
            }
            if (valueRegion == null || normalize(valueRegion.text()).isBlank()) continue;
            if (sameTextAndGeometry(labelRegion, valueRegion) && !looksLikeLabelAndValue(rawLabel)) continue;

            TemplateFieldDefinition suggested = suggestFieldByLabel(fields, sourceLabel);
            String key = suggested == null ? "" : suggested.key();
            double confidence = suggested == null ? .35 : .74;
            String expression = key.isBlank() ? "" : "{{" + key + "}}";
            Mapping existing = mappedByRegion.get(regionKey(valueRegion));
            if (existing != null) {
                key = existing.fieldKey(); expression = existing.expression(); confidence = Math.max(confidence, existing.confidence());
            }
            addCandidate(out, seen, new SourceCandidate(valueRegion, sourceLabel, valueRegion.text(), key, expression,
                    confidence, key.isBlank() ? "Detected source label/value" : "Catalogue label/value suggestion"));
        }

        out.sort(Comparator.comparingInt((SourceCandidate c) -> c.region().pageIndex())
                .thenComparingDouble(c -> c.region().y()).thenComparingDouble(c -> c.region().x()));
        return List.copyOf(out);
    }

    /** Backward-compatible bounding region for callers that only need the detected table header area. */
    public static Optional<PdfTextRegion> detectItemHeader(List<PdfTextRegion> regions) {
        return detectItemHeaderLayout(regions).map(layout -> {
            ItemHeaderCell style = layout.cells().isEmpty() ? null : layout.cells().getFirst();
            double fontSize = style == null ? 8 : Math.max(6, style.height() * .82);
            return new PdfTextRegion(layout.pageIndex(),
                    layout.cells().stream().map(ItemHeaderCell::label).filter(v -> !v.isBlank()).reduce("", (a,b) -> a.isBlank()?b:a+" | "+b),
                    layout.x(), layout.y(), layout.width(), layout.height(), fontSize,
                    "HELVETICA", true, false, "#172033", 0);
        });
    }

    /**
     * Detects physical item-header cells, their left-to-right geometry and suggested ERP meaning.
     * Suggestions never define the runtime contract by themselves; the editor can confirm or replace
     * every binding and the physical source order is retained.
     */
    public static Optional<ItemHeaderLayout> detectItemHeaderLayout(List<PdfTextRegion> regions) {
        if (regions == null || regions.isEmpty()) return Optional.empty();
        ItemHeaderLayout best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        Set<String> visitedRows = new HashSet<>();
        for (PdfTextRegion seed : regions) {
            double seedCenter = seed.y() + seed.height() / 2.0;
            double tolerance = Math.max(5.0, seed.height() * .75);
            String rowKey = seed.pageIndex() + ":" + Math.round(seedCenter / Math.max(2.0, tolerance));
            if (!visitedRows.add(rowKey)) continue;
            List<PdfTextRegion> row = regions.stream()
                    .filter(Objects::nonNull)
                    .filter(r -> r.pageIndex() == seed.pageIndex())
                    .filter(r -> Math.abs((r.y()+r.height()/2.0) - seedCenter) <= tolerance)
                    .sorted(Comparator.comparingDouble(PdfTextRegion::x)).toList();
            List<ItemHeaderCell> cells = new ArrayList<>();
            for (PdfTextRegion region : row) cells.addAll(headerCells(region));
            cells = mergeNearbyHeaderCells(cells);
            if (cells.size() < 3) continue;
            double minX = cells.stream().mapToDouble(ItemHeaderCell::x).min().orElse(seed.x());
            double maxX = cells.stream().mapToDouble(c -> c.x()+c.width()).max().orElse(seed.x()+seed.width());
            double spread = maxX - minX;
            if (spread < 120) continue;

            long mapped = cells.stream().filter(c -> !c.suggestedField().isBlank()).count();
            long strong = cells.stream().filter(c -> c.confidence() >= .90).count();
            long compactLabels = cells.stream().filter(c -> c.label().trim().length() <= 40).count();
            double avgGap = averageGap(cells);
            // Semantics improve ranking but never gate physical recognition. Thus a completely
            // unknown customer table can still be selected from its horizontal header structure.
            double score = cells.size() * 4.0 + mapped * 9.0 + strong * 2.0 + compactLabels
                    + Math.min(8.0, spread / 90.0) + Math.min(4.0, avgGap / 12.0);
            if (cells.stream().anyMatch(c -> c.label().length() > 90)) score -= 8;
            if (score > bestScore) {
                double minY = cells.stream().mapToDouble(ItemHeaderCell::y).min().orElse(seed.y());
                double maxY = cells.stream().mapToDouble(c -> c.y()+c.height()).max().orElse(seed.y()+seed.height());
                bestScore = score;
                best = new ItemHeaderLayout(seed.pageIndex(), minX, minY, Math.max(40,spread), Math.max(10,maxY-minY), cells);
            }
        }
        return Optional.ofNullable(best);
    }

    private record HeaderMatch(TemplateFieldDefinition field, String alias, int start, int end, double confidence) {}

    private static List<ItemHeaderCell> headerCells(PdfTextRegion region) {
        String normalized = normalize(region.text());
        if (normalized.isBlank()) return List.of();
        List<HeaderMatch> matches = headerMatches(normalized);
        if (matches.isEmpty()) {
            // Physical detection is independent of semantic recognition.
            return List.of(new ItemHeaderCell(region.text(), region.x(), region.y(), region.width(), region.height(), "", 0));
        }
        if (matches.size() == 1) {
            HeaderMatch m = matches.getFirst();
            return List.of(new ItemHeaderCell(region.text(), region.x(), region.y(), region.width(), region.height(), m.field().key(), m.confidence()));
        }
        List<ItemHeaderCell> out = new ArrayList<>();
        for (int i=0;i<matches.size();i++) {
            HeaderMatch m=matches.get(i);
            int left=m.start();
            int right=i+1<matches.size()?matches.get(i+1).start():normalized.length();
            double x=region.x()+region.width()*(left/(double)Math.max(1,normalized.length()));
            double end=region.x()+region.width()*(right/(double)Math.max(1,normalized.length()));
            out.add(new ItemHeaderCell(m.alias(),x,region.y(),Math.max(8,end-x),region.height(),m.field().key(),m.confidence()*.96));
        }
        return out;
    }

    private static List<HeaderMatch> headerMatches(String normalized) {
        List<HeaderMatch> candidates=new ArrayList<>();
        for(TemplateFieldDefinition field:TemplateFieldCatalog.pdfItemFields()){
            for(String rawAlias:aliasesFor(field)){
                String alias=normalize(rawAlias); if(alias.isBlank())continue;
                int from=0;
                while(from<normalized.length()){
                    int at=normalized.indexOf(alias,from); if(at<0)break;
                    boolean left=at==0||normalized.charAt(at-1)==' ';
                    int end=at+alias.length();
                    boolean right=end==normalized.length()||normalized.charAt(end)==' ';
                    if(left&&right)candidates.add(new HeaderMatch(field,rawAlias,at,end,.98));
                    from=at+1;
                }
            }
        }
        candidates.sort(Comparator.comparingInt(HeaderMatch::start)
                .thenComparing((a,b)->Integer.compare(b.end()-b.start(),a.end()-a.start())));
        List<HeaderMatch> selected=new ArrayList<>(); int occupied=-1;
        for(HeaderMatch c:candidates){ if(c.start()<occupied)continue; selected.add(c); occupied=c.end(); }
        return selected;
    }

    private static List<ItemHeaderCell> mergeNearbyHeaderCells(List<ItemHeaderCell> cells) {
        if(cells.isEmpty())return List.of();
        List<ItemHeaderCell> sorted=cells.stream().sorted(Comparator.comparingDouble(ItemHeaderCell::x)).toList();
        List<ItemHeaderCell> out=new ArrayList<>();
        for(ItemHeaderCell c:sorted){
            if(out.isEmpty()){out.add(c);continue;}
            ItemHeaderCell last=out.getLast();
            double overlap=Math.min(last.x()+last.width(),c.x()+c.width())-Math.max(last.x(),c.x());
            if(overlap>Math.min(last.width(),c.width())*.55 && Objects.equals(last.suggestedField(),c.suggestedField())){
                double x=Math.min(last.x(),c.x()), end=Math.max(last.x()+last.width(),c.x()+c.width());
                out.set(out.size()-1,new ItemHeaderCell(last.label()+" "+c.label(),x,Math.min(last.y(),c.y()),end-x,
                        Math.max(last.height(),c.height()),last.suggestedField(),Math.max(last.confidence(),c.confidence())));
            }else out.add(c);
        }
        return out;
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
                candidate = TemplateFieldCatalog.pdfFieldsFor(type).stream().filter(f -> allowed.contains(f.key()))
                        .anyMatch(f -> aliasesFor(f).stream().anyMatch(a -> n.contains(normalize(a))));
            }
            if (candidate && seen.add(r.pageIndex() + ":" + Math.round(r.x()) + ":" + Math.round(r.y()) + ":" + n)) count++;
        }
        if (detectItemHeader(regions).isPresent()) count++;
        if (detectChargeRegion(regions, data).isPresent()) count++;
        return count;
    }

    private static List<String> aliasesFor(TemplateFieldDefinition field) {
        if (field == null) return List.of();
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (field.label() != null && !field.label().isBlank()) aliases.add(field.label());
        aliases.addAll(field.aliases());
        return List.copyOf(aliases);
    }

    private static TemplateFieldDefinition suggestFieldByLabel(List<TemplateFieldDefinition> fields, String label) {
        String normalized = normalize(label);
        if (normalized.isBlank()) return null;
        TemplateFieldDefinition best = null; int bestLength = -1;
        for (TemplateFieldDefinition field : fields) {
            for (String alias : aliasesFor(field)) {
                String a = normalize(alias);
                if (a.isBlank()) continue;
                boolean match = normalized.equals(a) || normalized.contains(a) || a.contains(normalized);
                if (match && a.length() > bestLength) { best = field; bestLength = a.length(); }
            }
        }
        return best;
    }

    private static boolean looksLikePotentialLabel(String raw, String normalized) {
        if (raw == null || normalized == null || normalized.isBlank()) return false;
        String t = raw.trim();
        if (t.length() < 2 || t.length() > 80) return false;
        if (t.matches("[₹$€£]?\\s*[0-9,./:%+\\-() ]+")) return false;
        if (t.contains(":")) return t.indexOf(':') > 0;
        int words = normalized.split(" ").length;
        return words <= 7 && !t.endsWith(".");
    }

    private static String stripLabelDelimiter(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s*[:\\-–—]+\\s*$", "").trim();
    }

    private static String nearestLabelText(PdfTextRegion value, List<PdfTextRegion> all) {
        if (value == null || all == null) return "";
        double cy = value.y() + value.height()/2.0;
        return all.stream().filter(Objects::nonNull).filter(r -> r != value && r.pageIndex() == value.pageIndex())
                .filter(r -> r.x()+r.width() <= value.x()+8)
                .filter(r -> Math.abs((r.y()+r.height()/2.0)-cy) <= Math.max(10,value.height()*1.4))
                .filter(r -> looksLikePotentialLabel(r.text(), normalize(r.text())))
                .min(Comparator.comparingDouble(r -> value.x()-(r.x()+r.width())))
                .map(PdfTextRegion::text).map(PdfAutoMappingService::stripLabelDelimiter).orElse("");
    }

    private static void addCandidate(List<SourceCandidate> out, Set<String> seen, SourceCandidate candidate) {
        if (candidate == null || candidate.region() == null) return;
        String key = regionKey(candidate.region()) + "|" + normalize(candidate.sourceLabel());
        if (seen.add(key)) out.add(candidate);
    }

    private static String regionKey(PdfTextRegion r) {
        if (r == null) return "";
        return r.pageIndex()+":"+Math.round(r.x()*10)+":"+Math.round(r.y()*10)+":"+Math.round(r.width()*10)+":"+Math.round(r.height()*10);
    }

    private static boolean sameTextAndGeometry(PdfTextRegion a, PdfTextRegion b) {
        if (a == null || b == null) return false;
        return a.pageIndex()==b.pageIndex() && Math.abs(a.x()-b.x())<.1 && Math.abs(a.y()-b.y())<.1
                && Math.abs(a.width()-b.width())<.1 && Math.abs(a.height()-b.height())<.1
                && Objects.equals(a.text(), b.text());
    }

    private static double averageGap(List<ItemHeaderCell> cells) {
        if (cells == null || cells.size() < 2) return 0;
        List<ItemHeaderCell> sorted = cells.stream().sorted(Comparator.comparingDouble(ItemHeaderCell::x)).toList();
        double sum=0; int count=0;
        for(int i=1;i<sorted.size();i++) { sum += Math.max(0, sorted.get(i).x()-(sorted.get(i-1).x()+sorted.get(i-1).width())); count++; }
        return count==0?0:sum/count;
    }

    private static PdfTextRegion nearestValueRegion(PdfTextRegion label, List<PdfTextRegion> all, Set<PdfTextRegion> used) {
        PdfTextRegion best = null; double bestScore = Double.MAX_VALUE;
        for (PdfTextRegion candidate : all) {
            if (candidate == label || used.contains(candidate) || candidate.pageIndex() != label.pageIndex()) continue;
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
