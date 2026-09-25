package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.PdfTextRegion;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateFieldDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic PDF Studio physical-block detector shared by mapping, package import/export and validation setup.
 * It has no customer/template coordinates and never classifies blocks by page halves.
 */
public final class PdfStudioFlowBlockDetector {
    private PdfStudioFlowBlockDetector() { }

    public static void normalize(DocumentTemplate template, Path sourcePdf) {
        if (template == null) return;
        normalize(template, template.getElements(), sourcePdf);
    }

    public static void normalize(DocumentTemplate template, List<TemplateElement> elements, Path sourcePdf) {
        if (template == null || elements == null || elements.isEmpty()) return;
        Map<Integer,List<PdfImageExtractionService.VectorRegion>> vectors = new HashMap<>();
        Map<Integer,List<PdfTextRegion>> text = new HashMap<>();

        List<TemplateElement> mapped = elements.stream().filter(Objects::nonNull)
                .filter(e -> e.getType() == ElementType.FIELD || e.getType() == ElementType.IMAGE_FIELD)
                .filter(e -> !e.getFieldKey().isBlank())
                .filter(e -> !e.getFieldKey().startsWith("item.") && !e.getFieldKey().startsWith("charge."))
                .sorted(Comparator.comparingInt(TemplateElement::getPageIndex)
                        .thenComparingDouble(TemplateElement::getY).thenComparingDouble(TemplateElement::getX))
                .toList();

        Map<String,List<TemplateElement>> groups = new LinkedHashMap<>();
        List<TemplateElement> loose = new ArrayList<>();
        for (TemplateElement e : mapped) {
            TemplateFieldDefinition def = TemplateFieldCatalog.findPdf(template.getDocumentType(), e.getFieldKey());
            if (def != null) ManualTemplateMappingService.applyFieldSemantics(e, def);
            String group = e.getFlowGroupId();
            // A Review Mapping block is an explicit user-confirmed/detected identity and wins over
            // later geometric re-detection. Import/export must never silently replace it.
            if (group.isBlank() && !e.getMappingBlockId().isBlank()) group = e.getMappingBlockId();
            if (group.isBlank()) group = vectorGroup(e, sourcePdf, vectors);
            if (group.isBlank()) loose.add(e);
            else {
                e.setFlowGroupId(group);
                groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(e);
            }
        }

        // Borderless blocks: group by real geometric proximity. One field is a valid block and there is no size cap.
        int sequence = 0;
        List<TemplateElement> cluster = new ArrayList<>();
        int page = -1;
        double minX = 0, maxX = 0, bottom = 0;
        String clusterHint = "";
        for (TemplateElement e : loose) {
            boolean samePage = page == e.getPageIndex();
            double hGap = cluster.isEmpty() ? 0 : Math.max(0, Math.max(minX, e.getX()) - Math.min(maxX, e.getX() + e.getWidth()));
            double vGap = cluster.isEmpty() ? 0 : e.getY() - bottom;
            String hint = semanticHint(template, e);
            boolean semanticallyCompatible = cluster.isEmpty() || compatibleHints(clusterHint, hint);
            boolean nearby = samePage && semanticallyCompatible
                    && vGap <= Math.max(55, e.getHeight() * 4.0) && hGap <= 110;
            if (!cluster.isEmpty() && !nearby) {
                addProximityGroup(groups, cluster, page, sequence++);
                cluster.clear();
                clusterHint = "";
            }
            if (cluster.isEmpty()) {
                page = e.getPageIndex(); minX = e.getX(); maxX = e.getX() + e.getWidth(); bottom = e.getY() + e.getHeight();
                clusterHint = hint;
            } else {
                minX = Math.min(minX, e.getX()); maxX = Math.max(maxX, e.getX() + e.getWidth()); bottom = Math.max(bottom, e.getY() + e.getHeight());
                if (clusterHint.isBlank() || "HEADER".equals(clusterHint) || "GENERIC".equals(clusterHint)) clusterHint = hint;
            }
            cluster.add(e);
        }
        if (!cluster.isEmpty()) addProximityGroup(groups, cluster, page, sequence);

        for (Map.Entry<String,List<TemplateElement>> group : groups.entrySet()) {
            List<TemplateElement> values = group.getValue();
            if (values.isEmpty()) continue;
            String type = blockType(template, values);
            String label = blockLabel(type);
            if ("GENERIC".equals(type)) {
                label = inferHeading(values, sourcePdf, text);
                if (label.isBlank()) label = "Detected Source Block";
            }
            for (TemplateElement e : values) {
                e.setFlowGroupId(group.getKey());
                if (e.getMappingBlockId().isBlank()) e.setMappingBlockId(group.getKey());
                if (e.getMappingBlockType().isBlank()) e.setMappingBlockType(type);
                if (e.getMappingBlockLabel().isBlank()) e.setMappingBlockLabel(label);
                TemplateFieldDefinition def = TemplateFieldCatalog.findPdf(template.getDocumentType(), e.getFieldKey());
                if (def != null && def.multiline()) ManualTemplateMappingService.applyDetectedFlowBlock(e, def, group.getKey());
                else if (e.getFlowRole().isBlank()) e.setFlowRole("FLOW_MEMBER");
            }
        }
    }

    private static String semanticHint(DocumentTemplate template, TemplateElement e) {
        String hint = TemplateFieldCatalog.reviewBlockType(template.getDocumentType(), e.getFieldKey(), e.getMappingSourceLabel());
        return hint == null ? "" : hint;
    }

    private static boolean compatibleHints(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return true;
        if (a.equals(b)) return true;
        if ("GENERIC".equals(a) || "GENERIC".equals(b)) return true;
        // Header values can sit inside a physical business block, but distinct business roles
        // must never be silently merged. Ambiguous mixes are sent to Review Mapping instead.
        return "HEADER".equals(a) || "HEADER".equals(b);
    }

    private static void addProximityGroup(Map<String,List<TemplateElement>> groups, List<TemplateElement> cluster,
                                          int page, int sequence) {
        String id = "PROXMAP|" + page + "|" + sequence;
        List<TemplateElement> copy = new ArrayList<>(cluster);
        for (TemplateElement e : copy) e.setFlowGroupId(id);
        groups.put(id, copy);
    }

    private static String vectorGroup(TemplateElement e, Path sourcePdf,
                                      Map<Integer,List<PdfImageExtractionService.VectorRegion>> cache) {
        if (sourcePdf == null || !Files.isRegularFile(sourcePdf)) return "";
        List<PdfImageExtractionService.VectorRegion> all = cache.computeIfAbsent(e.getPageIndex(), page -> {
            try { return PdfImageExtractionService.extractVectors(sourcePdf, page); }
            catch (Exception ignored) { return List.of(); }
        });
        double cx = e.getX() + e.getWidth() / 2.0, cy = e.getY() + e.getHeight() / 2.0;
        return all.stream().filter(v -> "BLOCK".equals(v.kind()) || "TABLE / GRID".equals(v.kind()))
                .filter(v -> cx >= v.x()-1 && cx <= v.x()+v.width()+1 && cy >= v.y()-1 && cy <= v.y()+v.height()+1)
                .filter(v -> v.width() >= e.getWidth()*.75 && v.height() >= e.getHeight())
                .min(Comparator.comparingDouble(v -> v.width()*v.height()))
                .map(v -> "SRCFLOW|" + v.sourceKey()).orElse("");
    }

    private static String blockType(DocumentTemplate template, List<TemplateElement> values) {
        Set<String> types = values.stream().map(e -> TemplateFieldCatalog.reviewBlockType(
                        template.getDocumentType(), e.getFieldKey(), e.getMappingSourceLabel()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (types.size() == 1) return types.iterator().next();
        if (types.stream().allMatch(v -> v.equals("BILLING") || v.equals("DELIVERY")))
            return types.size() == 1 ? types.iterator().next() : "GENERIC";
        if (types.contains("TRANSPORT") && types.stream().allMatch(v -> v.equals("TRANSPORT") || v.equals("HEADER"))) return "TRANSPORT";
        return "GENERIC";
    }

    private static String blockLabel(String type) {
        return switch (type == null ? "" : type) {
            case "BILLING" -> "Billing / Bill To";
            case "DELIVERY" -> "Delivery / Ship To";
            case "TRANSPORT" -> "Transport Details";
            case "PAYMENT" -> "Bank / Payment";
            case "TERMS_FOOTER" -> "Terms & Conditions";
            case "GENERIC" -> "Detected Source Block";
            default -> "Document Header";
        };
    }

    private static String inferHeading(List<TemplateElement> values, Path sourcePdf,
                                       Map<Integer,List<PdfTextRegion>> cache) {
        if (sourcePdf == null || !Files.isRegularFile(sourcePdf) || values.isEmpty()) return "";
        int page = values.getFirst().getPageIndex();
        List<PdfTextRegion> all = cache.computeIfAbsent(page, p -> {
            try { return PdfTextExtractionService.extract(sourcePdf, p); }
            catch (Exception ignored) { return List.of(); }
        });
        double minX = values.stream().mapToDouble(TemplateElement::getX).min().orElse(0);
        double maxX = values.stream().mapToDouble(e -> e.getX()+e.getWidth()).max().orElse(minX+1);
        double minY = values.stream().mapToDouble(TemplateElement::getY).min().orElse(0);
        return all.stream().filter(r -> r.y()+r.height() <= minY+8 && r.y() >= Math.max(0,minY-65))
                .filter(r -> Math.min(maxX,r.x()+r.width())-Math.max(minX,r.x()) > Math.min(30,Math.max(8,r.width()*.20)))
                .map(PdfTextRegion::text).filter(Objects::nonNull).map(String::trim)
                .filter(t -> t.length() >= 3 && t.length() <= 70)
                .sorted(Comparator.comparingInt(String::length)).findFirst().orElse("");
    }
}
