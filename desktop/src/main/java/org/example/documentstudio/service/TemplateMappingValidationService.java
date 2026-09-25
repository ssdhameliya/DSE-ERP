package org.example.documentstudio.service;

import org.example.documentstudio.model.*;
import org.example.documentstudio.model.TemplateMappingRequirement.Kind;
import org.example.documentstudio.model.TemplateMappingRequirement.Level;
import org.example.documentstudio.model.TemplateValidationIssue.Severity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Central PDF Studio readiness evaluator shared by checklist, validation and activation UI. */
public final class TemplateMappingValidationService {
    private static final Pattern BINDING = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

    private TemplateMappingValidationService() {}

    public record Result(List<TemplateRequirementState> requirements, List<TemplateValidationIssue> issues,
                         Set<String> mappedFields, Set<String> itemColumns) {
        public Result {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            issues = issues == null ? List.of() : List.copyOf(issues);
            mappedFields = mappedFields == null ? Set.of() : Set.copyOf(mappedFields);
            itemColumns = itemColumns == null ? Set.of() : Set.copyOf(itemColumns);
        }
        public long requiredCount() { return requirements.stream().filter(s -> s.requirement().level() == Level.REQUIRED).count(); }
        public long requiredMapped() { return requirements.stream().filter(s -> s.requirement().level() == Level.REQUIRED && s.satisfied()).count(); }
        public long errorCount() { return issues.stream().filter(TemplateValidationIssue::error).count(); }
        public long warningCount() { return issues.size() - errorCount(); }
        public boolean readyForDefault() { return errorCount() == 0; }
    }

    public static Result evaluate(DocumentTemplate template) {
        if (template == null || !template.getDocumentType().isErpConnected()) return new Result(List.of(), List.of(), Set.of(), Set.of());
        Set<String> mapped = mappedFields(template);
        Set<String> columns = itemColumns(template);
        boolean hasItemTable = template.getElements().stream().anyMatch(e -> e != null && PdfStyleResolver.effectivelyVisible(template, e) && e.getType() == ElementType.ITEM_TABLE);
        boolean hasDynamicFinancialSummary = template.getElements().stream().anyMatch(e -> e != null
                && PdfStyleResolver.effectivelyVisible(template, e)
                && e.getType() == ElementType.BLOCK
                && "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId()));

        List<TemplateRequirementState> states = new ArrayList<>();
        List<TemplateValidationIssue> issues = new ArrayList<>();
        for (TemplateMappingRequirement requirement : TemplateRequirementCatalog.requirementsFor(template.getDocumentType())) {
            List<String> hits = switch (requirement.kind()) {
                case FIELD -> {
                    List<String> fieldHits = requirement.acceptedFields().stream().filter(mapped::contains).toList();
                    if (fieldHits.isEmpty() && hasDynamicFinancialSummary
                            && ("GRAND_TOTAL".equals(requirement.id()) || "TAX_SUMMARY".equals(requirement.id())))
                        yield List.of("DYNAMIC_FINANCIAL_SUMMARY");
                    yield fieldHits;
                }
                case ITEM_COLUMN -> requirement.acceptedFields().stream().filter(columns::contains).toList();
                case STRUCTURE -> hasItemTable ? List.of("ITEM_TABLE") : List.of();
            };
            boolean satisfied = !hits.isEmpty();
            states.add(new TemplateRequirementState(requirement, satisfied, hits));
            if (!satisfied && requirement.level() == Level.REQUIRED) {
                issues.add(new TemplateValidationIssue(Severity.ERROR,
                        requirement.label() + " is not mapped",
                        requirement.category() + " → " + requirement.label(),
                        requirement.explanation(), requirement.fixInstruction(), requirement.id()));
            }
        }

        validateItemGeometry(template, issues);
        validateItemBindings(template, issues);
        validateFlowFields(template, issues);
        validateSourceReplacementSafety(template, issues);
        validateRepeatingFieldPlacement(template, issues);
        validateFlowAnchors(template, issues);
        return new Result(states, issues, mapped, columns);
    }

    public static Set<String> mappedFields(DocumentTemplate template) {
        LinkedHashSet<String> mapped = new LinkedHashSet<>();
        if (template == null) return mapped;
        for (TemplateElement element : template.getElements()) {
            if (element == null || !PdfStyleResolver.effectivelyVisible(template, element)) continue;
            if ((element.getType() == ElementType.FIELD || element.getType() == ElementType.IMAGE_FIELD) && !element.getFieldKey().isBlank())
                mapped.add(element.getFieldKey());
            if (element.getType() != ElementType.TEXT) continue;
            String text = element.getText();
            if (text == null || text.isBlank()) continue;
            Matcher matcher = BINDING.matcher(text);
            while (matcher.find()) mapped.add(matcher.group(1));
        }
        return mapped;
    }

    public static Set<String> itemColumns(DocumentTemplate template) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        if (template == null) return columns;
        for (TemplateElement element : template.getElements()) {
            if (element == null || !PdfStyleResolver.effectivelyVisible(template, element) || element.getType() != ElementType.ITEM_TABLE) continue;
            if(!element.getTableColumnBindings().isEmpty()){
                for(TemplateColumnBinding binding:element.getTableColumnBindings()){
                    String key=itemColumnKey(binding.getFieldKey());if(!key.isBlank())columns.add(key);
                }
            }else for (String column : element.getTableColumns()) {
                String key = itemColumnKey(column);
                if (!key.isBlank()) columns.add(key);
            }
        }
        return columns;
    }

    public static String itemColumnKey(String column) {
        if (column == null || column.isBlank()) return "";
        String key = column.trim();
        if (key.startsWith("item.")) return key;
        key = switch (key) {
            case "qty" -> "quantity";
            case "discount" -> "discountPercent";
            case "gst" -> "gstPercent";
            case "amount" -> "total";
            default -> key;
        };
        return "item." + key;
    }

    private static void validateItemGeometry(DocumentTemplate template, List<TemplateValidationIssue> issues) {
        if (!TemplateFieldCatalog.requiresItemRowForDefault(template.getDocumentType())) return;
        for (TemplateElement table : template.getElements()) {
            if (table == null || !PdfStyleResolver.effectivelyVisible(template, table) || table.getType() != ElementType.ITEM_TABLE) continue;
            int rows = effectiveItemRowsPerPage(template, table);
            if (rows <= 1 && "MAPPED_FIXED".equals(template.getLayoutMode())) {
                issues.add(new TemplateValidationIssue(Severity.ERROR,
                        "The Item Table can fit only " + rows + " item per page",
                        "Item Table → Multi-page Layout",
                        "A 25-item document would generate about 25 pages instead of flowing through normal continuation pages.",
                        "Increase the dynamic Item Table area, reduce row height, or change the template to the shared flow-aware page layout before making it Default.",
                        "ITEM_TABLE_PAGE_FLOW"));
            } else if (rows < 4 && !template.isStrictFixedLayout()) {
                issues.add(new TemplateValidationIssue(Severity.WARNING,
                        "The Item Table has a very small page capacity",
                        "Item Table → Multi-page Layout",
                        "Only " + rows + " item rows fit in the usable flow area, so long documents may use many pages.",
                        "Use Fix Next Issue to focus the Item Table, then increase its body area or reduce Row Height before previewing a long record.",
                        "ITEM_TABLE_PAGE_FLOW"));
            }
        }
    }

    /** Shared with Preview/Runtime through PdfStudioRuntimeFlowPlanner. */
    static int effectiveItemRowsPerPage(DocumentTemplate template, TemplateElement table) {
        if (template == null || table == null) return 1;
        PdfStudioRuntimeFlowPlanner.ItemFlowPlan plan = PdfStudioRuntimeFlowPlanner.plan(
                template, table, 842.0, 0);
        return plan == null ? 1 : Math.max(1, plan.finalRows());
    }

    private static void validateItemBindings(DocumentTemplate template,List<TemplateValidationIssue> issues){
        for(TemplateElement table:template.getElements()){
            if(table==null||!PdfStyleResolver.effectivelyVisible(template,table)||table.getType()!=ElementType.ITEM_TABLE)continue;
            List<TemplateColumnBinding> bindings=table.getTableColumnBindings();if(bindings.isEmpty())continue;
            Set<String> seen=new HashSet<>();
            for(int i=0;i<bindings.size();i++){
                TemplateColumnBinding binding=bindings.get(i);String key=itemColumnKey(binding.getFieldKey());
                String header=binding.getSourceLabel().isBlank()?"column "+(i+1):"'"+binding.getSourceLabel()+"'";
                boolean staticSource = "STATIC".equals(binding.getMappingState());
                if(key.isBlank() && !staticSource)issues.add(new TemplateValidationIssue(Severity.ERROR,
                        "Item header "+header+" is not mapped","Item Table → Header Mapping",
                        "Every dynamic physical source column must have an explicit ERP meaning, or be explicitly kept as source artwork, before this template can be Default.",
                        "Drop the correct Item ERP field onto the "+header+" header cell, or choose Keep Static in Review Mapping.","ITEM_HEADER_MAPPING"));
                else if(!key.isBlank() && !seen.add(key))issues.add(new TemplateValidationIssue(Severity.ERROR,
                        "Item field "+key+" is mapped to more than one source column","Item Table → Header Mapping",
                        "Duplicate physical mappings can put the same ERP value under two different PDF headers.",
                        "Keep the field on only the intended source header.","ITEM_HEADER_DUPLICATE"));
                if(binding.getWidth()<=0)issues.add(new TemplateValidationIssue(Severity.ERROR,
                        "Item header "+header+" has no captured width","Item Table → Source Geometry",
                        "The renderer cannot preserve the imported PDF column geometry without a physical width.",
                        "Re-detect the Item Table or resize the source column mapping.","ITEM_HEADER_GEOMETRY"));
            }
            if(table.isUseSourceTableDesign()&&!table.isSourceStyleCaptured())issues.add(new TemplateValidationIssue(Severity.WARNING,
                    "Item Table source styling was not captured","Item Table → Source Style",
                    "This table can render data but may not reproduce the imported border/background appearance when rows are rebuilt.",
                    "Re-detect the source table so PDF Studio can capture its grid and background style.","ITEM_SOURCE_STYLE"));
        }
    }

    private static void validateSourceReplacementSafety(DocumentTemplate template,List<TemplateValidationIssue> issues){
        for(TemplateElement e:template.getElements()){
            if(e==null||!PdfStyleResolver.effectivelyVisible(template,e)||e.getType()==ElementType.WHITEOUT)continue;
            boolean maskMode="MASK".equals(e.getSourceReplacementMode());
            boolean sourceReplacement=maskMode&&!e.getReplacementSourceKey().isBlank();
            boolean sourceDynamic=maskMode&&((e.getType()==ElementType.ITEM_TABLE&&e.isUseSourceTableDesign())
                    ||(e.getType()==ElementType.BLOCK&&"DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId())&&e.isSourceStyleCaptured()));
            // OBJECT suppresses native text operators and FORM uses widgets that are cleared in
            // the normalized source. Neither paints a sampled rectangle, so non-uniform artwork
            // underneath is safe and must not be rejected by the legacy mask-safety gate.
            if((sourceReplacement||sourceDynamic)&&!e.isSourceMaskSafe()){
                issues.add(new TemplateValidationIssue(Severity.ERROR,
                        "A mapped source region sits on non-uniform artwork","Source Replacement → "+(e.getFieldKey().isBlank()?e.getType().name():e.getFieldKey()),
                        "A sampled solid replacement could visibly damage a gradient, image, watermark or patterned background.",
                        "Use a clean/native source region or intentionally place the ERP field in a blank overlay region before making this template Default.",
                        "UNSAFE_SOURCE_MASK"));
            }
        }
    }

    private static void validateRepeatingFieldPlacement(DocumentTemplate template,List<TemplateValidationIssue> issues){
        for(TemplateElement e:template.getElements()){
            if(e==null||!PdfStyleResolver.effectivelyVisible(template,e)||e.getType()!=ElementType.FIELD)continue;
            String key=e.getFieldKey();if(key==null)continue;
            if(key.startsWith("item."))issues.add(new TemplateValidationIssue(Severity.ERROR,
                    key+" is mapped as a single field","Repeating Data → Item Table",
                    "A scalar item.* field outside an Item Table renders only one item and is not safe for multi-row documents.",
                    "Map this field to a physical Item Table header instead.","SCALAR_ITEM_FIELD"));
            if(key.startsWith("charge."))issues.add(new TemplateValidationIssue(Severity.ERROR,
                    key+" is mapped as a single field","Repeating Data → Charges",
                    "A scalar charge.* field outside a Charge Table or Dynamic Financial Summary cannot represent multiple charges.",
                    "Use a Charge Table or map the whole calculation area as Dynamic Financial Summary.","SCALAR_CHARGE_FIELD"));
        }
    }

    /** Validate the semantic multiline contract for every PDF Studio field, not only addresses. */
    private static void validateFlowFields(DocumentTemplate template, List<TemplateValidationIssue> issues) {
        for (TemplateElement element : template.getElements()) {
            if (element == null || !PdfStyleResolver.effectivelyVisible(template, element)
                    || element.getType() != ElementType.FIELD || element.getFieldKey().isBlank()) continue;
            TemplateFieldDefinition definition = TemplateFieldCatalog.findPdf(template.getDocumentType(), element.getFieldKey());
            if (definition == null || !definition.multiline()) continue;
            String label = definition.label();
            if (!definition.textFit().equals(element.getTextFit())) {
                issues.add(new TemplateValidationIssue(Severity.ERROR,
                        label + " is not using its multiline Wrap policy",
                        "Flow Layout → " + label,
                        "The saved mapping no longer matches the ERP field semantic contract shown in Review Mapping.",
                        "Re-apply the detected mapping semantics so this field uses " + definition.textFit() + ".",
                        "MULTILINE_TEXT_FIT"));
            }
            if (definition.autoHeight() && !element.isAutoHeight()) {
                issues.add(new TemplateValidationIssue(Severity.ERROR,
                        label + " has Auto Height disabled",
                        "Flow Layout → " + label,
                        "Runtime content can contain more lines than the sample PDF and must be allowed to grow safely.",
                        "Restore Auto Height from the field semantic contract before publishing.",
                        "MULTILINE_AUTO_HEIGHT"));
            }
            if (definition.autoHeight() && "FIXED".equals(element.getGrowthDirection())) {
                issues.add(new TemplateValidationIssue(Severity.ERROR,
                        label + " cannot grow with its content",
                        "Flow Layout → " + label,
                        "Auto Height requires a flow direction so later source content can move instead of overlapping.",
                        "Use the detected flow block and Grow " + definition.growthDirection() + ".",
                        "MULTILINE_GROWTH"));
            }
            if (definition.autoHeight() && element.getFlowGroupId().isBlank()) {
                issues.add(new TemplateValidationIssue(Severity.WARNING,
                        label + " has no detected flow block",
                        "Flow Layout → " + label,
                        "The multiline field can grow, but dependent content is not yet linked to a detected physical block.",
                        "Open Review Mapping and confirm the detected source block before publishing this layout.",
                        "MULTILINE_FLOW_GROUP"));
            }
            if (!element.isAutoHeight() && "WRAP".equals(element.getTextFit())) {
                double lineHeight = Math.max(1, element.getFontSize() * Math.max(1.0, element.getLineSpacing()));
                if (element.getHeight() + .01 < lineHeight * 2) {
                    issues.add(new TemplateValidationIssue(Severity.WARNING,
                            label + " has space for less than two wrapped lines",
                            "Flow Layout → " + label,
                            "Long runtime content may be clipped even though Wrap is enabled.",
                            "Use the field's Auto Height semantic contract or increase the detected flow region.",
                            "MULTILINE_WRAP"));
                }
            }
        }
    }

    private static void validateFlowAnchors(DocumentTemplate template,List<TemplateValidationIssue> issues){
        Map<String,TemplateElement> byId=new LinkedHashMap<>();
        for(TemplateElement e:template.getElements())if(e!=null&&!e.getId().isBlank())byId.put(e.getId(),e);
        for(TemplateElement e:template.getElements()){
            if(e==null||!PdfStyleResolver.effectivelyVisible(template,e))continue;
            String mode=e.getFlowAnchorMode();
            if(("AFTER".equals(mode)||"BEFORE".equals(mode))){
                if(e.getFlowAnchorId().isBlank()||!byId.containsKey(e.getFlowAnchorId())){
                    issues.add(new TemplateValidationIssue(Severity.ERROR,
                            "Smart Layout anchor is missing","Smart Layout → "+(e.getFieldKey().isBlank()?e.getType().name():e.getFieldKey()),
                            "AFTER/BEFORE positioning requires a valid anchor element.",
                            "Select the element and choose a valid Anchor ID, or change Anchor to ABSOLUTE/TOP/BOTTOM.","FLOW_ANCHOR_MISSING"));
                    continue;
                }
                Set<String> seen=new HashSet<>();TemplateElement cursor=e;
                while(cursor!=null&&( "AFTER".equals(cursor.getFlowAnchorMode())||"BEFORE".equals(cursor.getFlowAnchorMode()))){
                    if(!seen.add(cursor.getId())){
                        issues.add(new TemplateValidationIssue(Severity.ERROR,
                                "Smart Layout contains an anchor cycle","Smart Layout → Anchor Chain",
                                "Two or more blocks depend on each other, so their runtime position cannot be resolved.",
                                "Break the cycle by anchoring one block to TOP/BOTTOM/ABSOLUTE.","FLOW_ANCHOR_CYCLE"));
                        break;
                    }
                    cursor=byId.get(cursor.getFlowAnchorId());
                }
            }
        }
    }
}
