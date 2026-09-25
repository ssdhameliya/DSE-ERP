package org.example.documentstudio.service;

import org.example.documentstudio.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detached review model for PDF Studio auto/manual mappings.
 *
 * <p>The workspace edits this session first. {@link #commit()} is the single mutation point, so
 * Cancel can leave the template untouched and user-confirmed/manual overrides survive later
 * detector passes.</p>
 */
public final class PdfMappingReviewSession {
    public enum Section {
        HEADER("Header", "document"), BILLING("Billing", "customer"), DELIVERY("Delivery", "delivery"),
        TRANSPORT("Transport", "delivery"), DETECTED_BLOCKS("Detected Blocks", "mapping"),
        ITEMS("Items", "item"), FINANCIAL("Financial", "currency"), PAYMENT("Payment", "bank"),
        TERMS_FOOTER("Terms & Footer", "document");
        private final String label, icon;
        Section(String label, String icon) { this.label = label; this.icon = icon; }
        public String label() { return label; }
        public String icon() { return icon; }
    }

    public enum Kind { FIELD, ITEM_COLUMN, FINANCIAL, FINANCIAL_ROLE }

    /** A source value detected from the imported PDF but not yet committed as a TemplateElement. */
    public record DetectedBlockEntry(
            String id, String sourceLabel, String sourceValue, String suggestedField, double confidence,
            PdfTextRegion region, String expression, String sourceKey) {
        public DetectedBlockEntry {
            id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
            sourceLabel = sourceLabel == null ? "" : sourceLabel.trim();
            sourceValue = sourceValue == null ? "" : sourceValue.trim();
            suggestedField = suggestedField == null ? "" : suggestedField.trim();
            confidence = Math.max(0, Math.min(1, confidence));
            expression = expression == null ? "" : expression;
            sourceKey = sourceKey == null ? "" : sourceKey.trim();
        }
    }

    /** Generic physical block detected from PDF geometry/context before mappings are materialized. */
    public record DetectedBlock(
            String id, String heading, String type, int pageIndex, double x, double y, double width, double height,
            List<DetectedBlockEntry> entries) {
        public DetectedBlock {
            id = id == null || id.isBlank() ? "DETECTED|" + UUID.randomUUID() : id.trim();
            heading = heading == null || heading.isBlank() ? "Detected Source Block" : heading.trim();
            type = type == null || type.isBlank() ? "GENERIC" : type.trim().toUpperCase(Locale.ROOT);
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    /** Actual source row label matched to a dynamic ERP financial role. */
    public record FinancialRoleDetection(String sourceLabel, String displayFieldLabel, String fieldKey, double confidence) {
        public FinancialRoleDetection {
            sourceLabel = sourceLabel == null ? "" : sourceLabel.trim();
            displayFieldLabel = displayFieldLabel == null ? "" : displayFieldLabel.trim();
            fieldKey = fieldKey == null ? "" : fieldKey.trim();
            confidence = Math.max(0, Math.min(1, confidence));
        }
    }

    /** Navigation descriptor; generic detected blocks receive their own dynamic entry. */
    public record NavigationItem(String id, String label, String icon, Section section, String blockId, boolean dynamic) { }

    public static final class Entry {
        private final Kind kind;
        private Section section;
        private final String targetId;
        private final int columnIndex;
        private final String sourceLabel;
        private final String autoFieldKey;
        private final double confidence;
        private final boolean imageTarget;
        private final boolean readOnly;
        private String blockId;
        private String blockLabel;
        private String fieldKey;
        private String state;
        private String displayFieldLabel;
        private String sourceValue = "";
        private boolean pendingDetection;
        private PdfTextRegion detectedRegion;
        private String detectedExpression = "";
        private String detectedSourceKey = "";
        private double x, y, width, height, summaryLabelRatio;
        private String anchorMode, growthDirection;
        private String contentMode = "SINGLE_LINE";
        private String textFit = "SHRINK";
        private boolean autoHeight;
        private String overflowPolicy = "ERROR";
        private String flowGroupId = "";

        private Entry(Kind kind, Section section, String targetId, int columnIndex, String sourceLabel,
                      String fieldKey, String autoFieldKey, double confidence, String state, boolean imageTarget,
                      String blockId, String blockLabel, boolean readOnly, String displayFieldLabel) {
            this.kind = kind; this.section = section; this.targetId = targetId; this.columnIndex = columnIndex;
            this.sourceLabel = sourceLabel == null ? "" : sourceLabel.trim();
            this.fieldKey = fieldKey == null ? "" : fieldKey.trim();
            this.autoFieldKey = autoFieldKey == null ? "" : autoFieldKey.trim();
            this.confidence = Math.max(0, Math.min(1, confidence));
            this.state = normalizeState(state, this.fieldKey);
            this.imageTarget = imageTarget;
            this.blockId = blockId == null ? "" : blockId.trim();
            this.blockLabel = blockLabel == null ? "" : blockLabel.trim();
            this.readOnly = readOnly;
            this.displayFieldLabel = displayFieldLabel == null ? "" : displayFieldLabel.trim();
        }
        public Kind kind() { return kind; }
        public Section section() { return section; }
        public String targetId() { return targetId; }
        public int columnIndex() { return columnIndex; }
        public String sourceLabel() { return sourceLabel; }
        public String fieldKey() { return fieldKey; }
        public String autoFieldKey() { return autoFieldKey; }
        public double confidence() { return confidence; }
        public String state() { return state; }
        public boolean imageTarget() { return imageTarget; }
        public boolean readOnly() { return readOnly; }
        public String blockId() { return blockId; }
        public String blockLabel() { return blockLabel; }
        public String displayFieldLabel() { return displayFieldLabel; }
        public String sourceValue() { return sourceValue; }
        public boolean pendingDetection() { return pendingDetection; }
        public PdfTextRegion detectedRegion() { return detectedRegion; }
        public String detectedExpression() { return detectedExpression; }
        public String detectedSourceKey() { return detectedSourceKey; }
        public double x() { return x; } public double y() { return y; }
        public double width() { return width; } public double height() { return height; }
        public double summaryLabelRatio() { return summaryLabelRatio; }
        public String anchorMode() { return anchorMode; }
        public String growthDirection() { return growthDirection; }
        public String contentMode() { return contentMode; }
        public String textFit() { return textFit; }
        public boolean autoHeight() { return autoHeight; }
        public String overflowPolicy() { return overflowPolicy; }
        public String flowGroupId() { return flowGroupId; }
        public String behaviorSummary() {
            if ("MULTILINE".equals(contentMode)) return "Multiline • " + textFit + (autoHeight ? " • Auto Height" : "")
                    + " • Grow " + pretty(growthDirection);
            if ("IMAGE".equals(contentMode)) return "Image";
            return "Single Line • " + textFit;
        }

        private void setSection(Section section) { this.section = section; }
        private void setBlock(String id, String label) {
            this.blockId = id == null ? "" : id.trim();
            this.blockLabel = label == null ? "" : label.trim();
        }

        private void setRuntimeSemantics(TemplateElement element, TemplateFieldDefinition def) {
            if (def != null) {
                contentMode = def.contentMode().name();
                textFit = def.textFit(); autoHeight = def.autoHeight();
                growthDirection = def.growthDirection(); overflowPolicy = def.overflowPolicy();
            }
            if (element != null) {
                textFit = element.getTextFit(); autoHeight = element.isAutoHeight();
                growthDirection = element.getGrowthDirection(); overflowPolicy = element.getOverflowPolicy();
                flowGroupId = element.getFlowGroupId();
                if (def != null && def.image()) contentMode = "IMAGE";
                else if (def != null && def.multiline()) contentMode = "MULTILINE";
            }
        }

        private void setDetectedSemantics(TemplateFieldDefinition def, String detectedBlockId) {
            if (def != null) {
                contentMode = def.contentMode().name(); textFit = def.textFit(); autoHeight = def.autoHeight();
                growthDirection = def.growthDirection(); overflowPolicy = def.overflowPolicy();
                if (def.multiline() && detectedBlockId != null) flowGroupId = detectedBlockId;
            }
        }

        private static String pretty(String value) {
            if (value == null || value.isBlank()) return "Fixed";
            String v=value.toLowerCase(Locale.ROOT); return Character.toUpperCase(v.charAt(0))+v.substring(1);
        }

        public void chooseField(String key) {
            if (readOnly) return;
            fieldKey = key == null ? "" : key.trim();
            if (!fieldKey.isBlank()) setDetectedSemantics(TemplateFieldCatalog.find(fieldKey), blockId);
            if (fieldKey.isBlank()) state = "UNMAPPED";
            else if (!autoFieldKey.isBlank() && !autoFieldKey.equals(fieldKey)) state = "MANUAL_OVERRIDE";
            else state = "CONFIRMED";
        }
        public void confirm() { if (!readOnly && !fieldKey.isBlank()) state = "CONFIRMED"; }
        public void keepStatic() { if (!readOnly) { fieldKey = ""; state = "STATIC"; } }
        public void unmap() { if (!readOnly) { fieldKey = ""; state = "UNMAPPED"; } }
        public void resetAuto() {
            if (readOnly) return;
            fieldKey = autoFieldKey;
            state = autoFieldKey.isBlank() ? "REVIEW_REQUIRED" : "AUTO";
        }
        public void setFinancialGeometry(double x, double y, double width, double height, double ratio,
                                         String anchorMode, String growthDirection) {
            if (readOnly) return;
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.summaryLabelRatio = ratio;
            this.anchorMode = anchorMode == null ? "ABSOLUTE" : anchorMode;
            this.growthDirection = growthDirection == null ? "UP" : growthDirection;
            if ("AUTO".equals(state)) state = "MANUAL_OVERRIDE";
        }
        private static String normalizeState(String value, String fieldKey) {
            String s = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            return switch (s) {
                case "AUTO", "CONFIRMED", "MANUAL_OVERRIDE", "REVIEW_REQUIRED", "UNMAPPED", "STATIC" -> s;
                default -> fieldKey == null || fieldKey.isBlank() ? "UNMAPPED" : "CONFIRMED";
            };
        }
    }

    private final DocumentTemplate template;
    private final List<Entry> entries;

    private PdfMappingReviewSession(DocumentTemplate template, List<Entry> entries) {
        this.template = Objects.requireNonNull(template);
        this.entries = new ArrayList<>(entries);
    }

    public static PdfMappingReviewSession from(DocumentTemplate template) {
        return from(template, List.of(), Map.of());
    }

    public static PdfMappingReviewSession from(DocumentTemplate template, List<DetectedBlock> detectedBlocks,
                                                Map<String,List<FinancialRoleDetection>> detectedFinancialRoles) {
        if (template == null) throw new IllegalArgumentException("Template is required.");
        detectedBlocks = detectedBlocks == null ? List.of() : detectedBlocks;
        detectedFinancialRoles = detectedFinancialRoles == null ? Map.of() : detectedFinancialRoles;
        List<Entry> out = new ArrayList<>();
        List<TemplateElement> scalarElements = new ArrayList<>();
        Map<String, TemplateElement> scalarByEntryTarget = new LinkedHashMap<>();

        for (TemplateElement e : template.getElements()) {
            if (e == null || !e.isVisible()) continue;
            if (e.getType() == ElementType.WHITEOUT) continue;
            if (e.getType() == ElementType.ITEM_TABLE) {
                List<TemplateColumnBinding> bindings = e.getTableColumnBindings();
                if (bindings.isEmpty()) bindings = ManualTemplateMappingService.legacyItemBindings(e);
                for (int i = 0; i < bindings.size(); i++) {
                    TemplateColumnBinding b = bindings.get(i);
                    Entry entry = new Entry(Kind.ITEM_COLUMN, Section.ITEMS, e.getId(), i,
                            b.getSourceLabel(), b.getFieldKey(), b.getAutoDetectedFieldKey(),
                            b.getAutoDetectedConfidence() > 0 ? b.getAutoDetectedConfidence() : b.getConfidence(),
                            b.getMappingState(), false, "ITEM_TABLE:" + e.getId(), "ITEM TABLE", false, "");
                    out.add(entry);
                }
                continue;
            }
            if (e.getType() == ElementType.BLOCK && "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId())) {
                Entry entry = new Entry(Kind.FINANCIAL, Section.FINANCIAL, e.getId(), -1,
                        "Dynamic Financial Summary", "DYNAMIC_FINANCIAL_SUMMARY", "DYNAMIC_FINANCIAL_SUMMARY",
                        e.getAutoDetectedConfidence() > 0 ? e.getAutoDetectedConfidence() : .99, e.getMappingState(), false,
                        "FINANCIAL:" + e.getId(), "FINANCIAL SUMMARY", false, "");
                entry.x = e.getX(); entry.y = e.getY(); entry.width = e.getWidth(); entry.height = e.getHeight();
                entry.summaryLabelRatio = e.getSummaryLabelRatio(); entry.anchorMode = e.getFlowAnchorMode();
                entry.growthDirection = e.getGrowthDirection();
                out.add(entry);
                addFinancialRoles(out, e, detectedFinancialRoles.getOrDefault(e.getId(), List.of()));
                continue;
            }
            String key = e.getFieldKey();
            if (key == null || key.isBlank()) continue;
            if (key.startsWith("item.") || key.startsWith("charge.")) continue;
            // Renderer-private helper fields stay implementation details, not user mappings.
            if (key.startsWith("totals.breakdown")) continue;
            TemplateFieldDefinition def = TemplateFieldCatalog.findPdf(template.getDocumentType(), key);
            String label = e.getMappingSourceLabel();
            if (label == null || label.isBlank()) label = def == null ? key : def.label();
            Section section = sectionForElement(template.getDocumentType(), e, key, label);
            String blockId = !e.getMappingBlockId().isBlank() ? e.getMappingBlockId()
                    : !e.getFlowGroupId().isBlank() ? e.getFlowGroupId() : section.name();
            String blockLabel = !e.getMappingBlockLabel().isBlank() ? e.getMappingBlockLabel() : defaultBlockLabel(section);
            Entry entry = new Entry(Kind.FIELD, section, e.getId(), -1, label, key,
                    e.getAutoDetectedFieldKey(), e.getAutoDetectedConfidence(), e.getMappingState(),
                    e.getType() == ElementType.IMAGE || e.getType() == ElementType.IMAGE_FIELD || (def != null && def.image()),
                    blockId, blockLabel, false, "");
            entry.setRuntimeSemantics(e, def);
            out.add(entry);
            scalarElements.add(e);
            scalarByEntryTarget.put(e.getId(), e);
        }

        // Infer generic/semantic source blocks from shared PDF flow containers. This keeps the review
        // UI generic: an arbitrary XYZ/Export/Dispatch block can appear without a new controller.
        inferBlockSections(template.getDocumentType(), out, scalarByEntryTarget);

        // Merge source detections that have not yet become TemplateElements. They remain detached until
        // Save Mapping; Cancel therefore needs no scalar-element rollback.
        Set<String> committedSourceKeys = template.getElements().stream().filter(Objects::nonNull)
                .map(TemplateElement::getReplacementSourceKey).filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toSet());
        for (DetectedBlock block : detectedBlocks) {
            if (block == null) continue;
            Section section = sectionForBlockType(block.type());
            for (DetectedBlockEntry detected : block.entries()) {
                if (detected == null || detected.region() == null) continue;
                if (!detected.sourceKey().isBlank() && committedSourceKeys.contains(detected.sourceKey())) continue;
                String label = !detected.sourceLabel().isBlank() ? detected.sourceLabel() : detected.sourceValue();
                Entry entry = new Entry(Kind.FIELD, section, "DETECTED:" + detected.id(), -1, label,
                        detected.suggestedField(), detected.suggestedField(), detected.confidence(),
                        detected.suggestedField().isBlank() ? "REVIEW_REQUIRED" : "AUTO", false,
                        block.id(), block.heading(), false, "");
                entry.setDetectedSemantics(TemplateFieldCatalog.findPdf(template.getDocumentType(), detected.suggestedField()), block.id());
                entry.sourceValue = detected.sourceValue();
                entry.pendingDetection = true;
                entry.detectedRegion = detected.region();
                entry.detectedExpression = detected.expression();
                entry.detectedSourceKey = detected.sourceKey();
                out.add(entry);
            }
        }

        out.sort(Comparator.comparing((Entry e) -> e.section().ordinal())
                .thenComparing(Entry::blockLabel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.kind().ordinal()).thenComparing(Entry::sourceLabel, String.CASE_INSENSITIVE_ORDER));
        return new PdfMappingReviewSession(template, out);
    }

    private static void addFinancialRoles(List<Entry> out, TemplateElement financial, List<FinancialRoleDetection> detected) {
        String block = "FINANCIAL:" + financial.getId();
        record Role(String source, String label, String key) {}
        List<Role> defaults = List.of(
                new Role("BASIC AMOUNT", "Basic / Sub Total", "totals.basicAmount"),
                new Role("DISCOUNT", "Discount", "totals.discountAmount"),
                new Role("ADDITIONAL CHARGES", "Dynamic Charge Rows", "charge.* (dynamic rows)"),
                new Role("TAXABLE AMOUNT", "Taxable Amount", "totals.taxableAmount"),
                new Role("CGST", "Automatic CGST Row", "tax.cgstLabel / tax.primaryAmount"),
                new Role("SGST", "Automatic SGST Row", "tax.sgstLabel / tax.secondaryAmount"),
                new Role("IGST", "Automatic IGST Row", "tax.igstLabel / tax.primaryAmount"),
                new Role("ROUND OFF", "Rounding", "totals.roundOff"),
                new Role("GRAND TOTAL", "Rounded Grand Total", "totals.roundedGrandTotal")
        );
        List<FinancialRoleDetection> actual = detected == null ? List.of() : detected.stream()
                .filter(Objects::nonNull).filter(d -> !d.sourceLabel().isBlank() && !d.fieldKey().isBlank()).toList();
        Map<String,FinancialRoleDetection> byKey = new LinkedHashMap<>();
        for (FinancialRoleDetection role : actual) {
            if (!role.fieldKey().startsWith("charge.*")) byKey.putIfAbsent(role.fieldKey(), role);
        }
        for (Role role : defaults) {
            if (role.key().startsWith("charge.*")) {
                List<FinancialRoleDetection> charges = actual.stream().filter(d -> d.fieldKey().startsWith("charge.*")).toList();
                if (charges.isEmpty()) addFinancialRole(out, financial, block, role.source(), role.label(), role.key(), .99);
                else for (FinancialRoleDetection charge : charges)
                    addFinancialRole(out, financial, block, charge.sourceLabel(),
                            charge.displayFieldLabel().isBlank() ? role.label() : charge.displayFieldLabel(), role.key(), charge.confidence());
                continue;
            }
            FinancialRoleDetection match = byKey.get(role.key());
            addFinancialRole(out, financial, block, match == null ? role.source() : match.sourceLabel(),
                    match == null || match.displayFieldLabel().isBlank() ? role.label() : match.displayFieldLabel(),
                    role.key(), match == null ? .99 : match.confidence());
        }
    }

    private static void addFinancialRole(List<Entry> out, TemplateElement financial, String block, String source,
                                         String label, String key, double confidence) {
        out.add(new Entry(Kind.FINANCIAL_ROLE, Section.FINANCIAL, financial.getId(), -1, source, key, key,
                confidence, "AUTO", false, block, "FINANCIAL SUMMARY", true, label));
    }

    private static void inferBlockSections(DocumentType documentType, List<Entry> entries, Map<String, TemplateElement> byTarget) {
        Map<String,List<Entry>> groups = entries.stream()
                .filter(e -> e.kind() == Kind.FIELD)
                .filter(e -> !e.blockId().isBlank())
                .collect(Collectors.groupingBy(Entry::blockId, LinkedHashMap::new, Collectors.toList()));
        for (var group : groups.entrySet()) {
            List<Entry> values = group.getValue();
            if (values.isEmpty()) continue;
            boolean explicitGeneric = values.stream().map(Entry::targetId).map(byTarget::get).filter(Objects::nonNull)
                    .anyMatch(e -> "GENERIC".equals(e.getMappingBlockType()));
            Set<Section> semantics = values.stream().map(Entry::section).collect(Collectors.toCollection(LinkedHashSet::new));
            Section target;
            if (explicitGeneric) target = Section.DETECTED_BLOCKS;
            else if (semantics.size() == 1) target = semantics.iterator().next();
            else if (semantics.stream().allMatch(s -> s == Section.BILLING || s == Section.DELIVERY)) {
                boolean hasDelivery = values.stream().anyMatch(e -> "DELIVERY".equals(
                        TemplateFieldCatalog.reviewBlockType(documentType, e.fieldKey(), e.sourceLabel())));
                target = hasDelivery ? Section.DELIVERY : Section.BILLING;
            } else if (semantics.contains(Section.TRANSPORT) && semantics.stream().allMatch(s -> s == Section.TRANSPORT || s == Section.HEADER)) {
                target = Section.TRANSPORT;
            } else target = Section.DETECTED_BLOCKS;

            String explicitLabel = values.stream().map(Entry::targetId).map(byTarget::get).filter(Objects::nonNull)
                    .map(TemplateElement::getMappingBlockLabel).filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
            String label = !explicitLabel.isBlank() ? explicitLabel : defaultBlockLabel(target);
            if (target == Section.DETECTED_BLOCKS && (label.isBlank() || label.equals("DETECTED SOURCE BLOCK"))) label = "DETECTED SOURCE BLOCK";
            for (Entry entry : values) { entry.setSection(target); entry.setBlock(group.getKey(), label); }
        }
    }

    public DocumentTemplate template() { return template; }
    public List<Entry> entries() { return List.copyOf(entries); }
    public List<Entry> entries(Section section) { return entries.stream().filter(e -> e.section() == section).toList(); }
    public int count(Section section) { return (int) entries.stream().filter(e -> e.section() == section).count(); }
    public List<Entry> pendingEntries() { return entries.stream().filter(Entry::pendingDetection).toList(); }

    public List<NavigationItem> navigationItems() {
        List<NavigationItem> nav = new ArrayList<>();
        for (Section section : Section.values()) {
            if (section != Section.DETECTED_BLOCKS) {
                nav.add(new NavigationItem(section.name(), section.label(), section.icon(), section, "", false));
                continue;
            }
            Map<String,List<Entry>> blocks = entries(section).stream().collect(Collectors.groupingBy(
                    e -> e.blockId().isBlank() ? "DETECTED_SOURCE_BLOCK" : e.blockId(), LinkedHashMap::new, Collectors.toList()));
            if (blocks.isEmpty()) {
                nav.add(new NavigationItem(section.name(), section.label(), section.icon(), section, "", false));
            } else {
                for (var group : blocks.entrySet()) {
                    String label = group.getValue().stream().map(Entry::blockLabel).filter(v -> v != null && !v.isBlank())
                            .findFirst().orElse("Detected Source Block");
                    nav.add(new NavigationItem("BLOCK:" + group.getKey(), label, "mapping", section, group.getKey(), true));
                }
            }
        }
        return List.copyOf(nav);
    }

    public List<Entry> entries(NavigationItem item) {
        if (item == null) return List.of();
        if (!item.dynamic()) return entries(item.section());
        return entries.stream().filter(e -> e.section() == item.section()).filter(e -> Objects.equals(e.blockId(), item.blockId())).toList();
    }

    public int count(NavigationItem item) { return entries(item).size(); }
    public long reviewRequired() { return entries.stream().filter(e -> !e.readOnly()).filter(e -> "REVIEW_REQUIRED".equals(e.state()) || "UNMAPPED".equals(e.state())).count(); }
    public long autoCount() { return entries.stream().filter(e -> "AUTO".equals(e.state())).count(); }
    public long confirmedCount() { return entries.stream().filter(e -> !e.readOnly()).filter(e -> "CONFIRMED".equals(e.state())).count(); }
    public long overrideCount() { return entries.stream().filter(e -> !e.readOnly()).filter(e -> "MANUAL_OVERRIDE".equals(e.state())).count(); }

    public List<TemplateFieldDefinition> fieldOptions(Entry entry) {
        if (entry == null || entry.readOnly() || entry.kind() == Kind.FINANCIAL) return List.of();
        return TemplateFieldCatalog.pdfFieldsFor(template.getDocumentType()).stream()
                .filter(f -> entry.kind() != Kind.ITEM_COLUMN || f.key().startsWith("item."))
                .filter(f -> entry.kind() == Kind.ITEM_COLUMN || f.image() == entry.imageTarget())
                .distinct().toList();
    }

    /** Apply the reviewed session to the live template in one mutation pass. */
    public void commit() {
        Map<String, TemplateElement> byId = template.getElements().stream()
                .filter(Objects::nonNull).collect(Collectors.toMap(TemplateElement::getId, e -> e, (a,b) -> a, LinkedHashMap::new));
        Set<String> removeGroups = new HashSet<>();
        Set<String> removeIds = new HashSet<>();

        Map<String, List<Entry>> itemByTable = entries.stream().filter(e -> !e.pendingDetection()).filter(e -> e.kind() == Kind.ITEM_COLUMN)
                .collect(Collectors.groupingBy(Entry::targetId, LinkedHashMap::new, Collectors.toList()));
        for (var group : itemByTable.entrySet()) {
            TemplateElement table = byId.get(group.getKey());
            if (table == null || table.getType() != ElementType.ITEM_TABLE) continue;
            List<TemplateColumnBinding> bindings = new ArrayList<>(table.getTableColumnBindings());
            if (bindings.isEmpty()) bindings.addAll(ManualTemplateMappingService.legacyItemBindings(table));
            for (Entry entry : group.getValue()) {
                if (entry.columnIndex() < 0 || entry.columnIndex() >= bindings.size()) continue;
                TemplateColumnBinding b = bindings.get(entry.columnIndex()).copy();
                b.setFieldKey(entry.fieldKey());
                b.setMappingState(entry.state());
                if (!entry.autoFieldKey().isBlank()) b.setAutoDetectedFieldKey(entry.autoFieldKey());
                b.setAutoDetectedConfidence(entry.confidence());
                if ("CONFIRMED".equals(entry.state()) || "MANUAL_OVERRIDE".equals(entry.state())) b.setConfidence(1.0);
                bindings.set(entry.columnIndex(), b);
            }
            table.setTableColumnBindings(bindings);
            ManualTemplateMappingService.syncLegacyColumns(table);
        }

        for (Entry entry : entries) {
            if (entry.pendingDetection() || entry.kind() == Kind.ITEM_COLUMN || entry.kind() == Kind.FINANCIAL_ROLE) continue;
            TemplateElement element = byId.get(entry.targetId());
            if (element == null) continue;
            if (entry.kind() == Kind.FINANCIAL) {
                if ("STATIC".equals(entry.state()) || "UNMAPPED".equals(entry.state())) {
                    removeIds.add(element.getId());
                    if (!element.getReplacementGroupId().isBlank()) removeGroups.add(element.getReplacementGroupId());
                    continue;
                }
                element.setX(entry.x()); element.setY(entry.y()); element.setWidth(entry.width()); element.setHeight(entry.height());
                element.setSummaryLabelRatio(entry.summaryLabelRatio()); element.setFlowAnchorMode(entry.anchorMode());
                element.setGrowthDirection(entry.growthDirection()); element.setMappingState(entry.state());
                continue;
            }
            if ("STATIC".equals(entry.state()) || "UNMAPPED".equals(entry.state()) || entry.fieldKey().isBlank()) {
                if (!element.getReplacementGroupId().isBlank()) removeGroups.add(element.getReplacementGroupId());
                else removeIds.add(element.getId());
                continue;
            }
            TemplateFieldDefinition def = TemplateFieldCatalog.findPdf(template.getDocumentType(), entry.fieldKey());
            if (def != null) {
                ManualTemplateMappingService.mapField(element, def);
                ManualTemplateMappingService.applyDetectedFlowBlock(element, def, entry.blockId());
            }
            element.setMappingBlockId(entry.blockId());
            element.setMappingBlockLabel(entry.blockLabel());
            element.setMappingBlockType(sectionBlockType(entry.section()));
            element.setMappingState(entry.state());
            if (!entry.autoFieldKey().isBlank()) element.setAutoDetectedFieldKey(entry.autoFieldKey());
            element.setAutoDetectedConfidence(entry.confidence());
        }

        if (!removeGroups.isEmpty() || !removeIds.isEmpty()) {
            List<TemplateElement> kept = template.getElements().stream().filter(e -> e != null)
                    .filter(e -> !removeIds.contains(e.getId()))
                    .filter(e -> !removeGroups.contains(e.getReplacementGroupId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            template.setElements(kept);
        }
    }

    public void resetAllAuto() { entries.forEach(e -> { if (!e.readOnly() && !e.autoFieldKey().isBlank()) e.resetAuto(); }); }
    public void confirmAllHighConfidence() { entries.stream().filter(e -> !e.readOnly()).filter(e -> e.confidence() >= .90 && !e.fieldKey().isBlank()).forEach(Entry::confirm); }

    private static String sectionBlockType(Section section) {
        if (section == null) return "HEADER";
        return switch (section) {
            case BILLING -> "BILLING"; case DELIVERY -> "DELIVERY"; case TRANSPORT -> "TRANSPORT";
            case PAYMENT -> "PAYMENT"; case TERMS_FOOTER -> "TERMS_FOOTER"; case DETECTED_BLOCKS -> "GENERIC";
            case ITEMS -> "ITEMS"; case FINANCIAL -> "FINANCIAL"; default -> "HEADER";
        };
    }

    private static Section sectionForBlockType(String type) {
        String explicit = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (explicit) {
            case "BILLING" -> Section.BILLING; case "DELIVERY" -> Section.DELIVERY; case "TRANSPORT" -> Section.TRANSPORT;
            case "PAYMENT" -> Section.PAYMENT; case "TERMS_FOOTER" -> Section.TERMS_FOOTER;
            case "ITEMS" -> Section.ITEMS; case "FINANCIAL" -> Section.FINANCIAL; case "GENERIC" -> Section.DETECTED_BLOCKS;
            default -> Section.HEADER;
        };
    }

    private static Section sectionForElement(DocumentType documentType, TemplateElement element, String key, String sourceLabel) {
        String explicit = element == null ? "" : element.getMappingBlockType();
        if (!explicit.isBlank()) {
            return switch (explicit) {
                case "HEADER" -> Section.HEADER; case "BILLING" -> Section.BILLING; case "DELIVERY" -> Section.DELIVERY;
                case "TRANSPORT" -> Section.TRANSPORT; case "PAYMENT" -> Section.PAYMENT;
                case "TERMS_FOOTER" -> Section.TERMS_FOOTER; case "GENERIC" -> Section.DETECTED_BLOCKS;
                default -> semanticSection(documentType, key, sourceLabel);
            };
        }
        return semanticSection(documentType, key, sourceLabel);
    }

    private static Section semanticSection(DocumentType documentType, String key, String sourceLabel) {
        String block=TemplateFieldCatalog.reviewBlockType(documentType,key,sourceLabel);
        return sectionForBlockType(block);
    }

    private static String defaultBlockLabel(Section section) {
        return switch (section) {
            case HEADER -> "DOCUMENT HEADER";
            case BILLING -> "BILLING / BILL TO";
            case DELIVERY -> "DELIVERY / SHIP TO";
            case TRANSPORT -> "TRANSPORT DETAILS";
            case DETECTED_BLOCKS -> "DETECTED SOURCE BLOCK";
            case ITEMS -> "ITEM TABLE";
            case FINANCIAL -> "FINANCIAL SUMMARY";
            case PAYMENT -> "BANK / PAYMENT";
            case TERMS_FOOTER -> "TERMS & CONDITIONS";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
