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
        boolean hasItemTable = template.getElements().stream().anyMatch(e -> e != null && e.isVisible() && e.getType() == ElementType.ITEM_TABLE);

        List<TemplateRequirementState> states = new ArrayList<>();
        List<TemplateValidationIssue> issues = new ArrayList<>();
        for (TemplateMappingRequirement requirement : TemplateRequirementCatalog.requirementsFor(template.getDocumentType())) {
            List<String> hits = switch (requirement.kind()) {
                case FIELD -> requirement.acceptedFields().stream().filter(mapped::contains).toList();
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
        validateAddressBoxes(template, issues);
        return new Result(states, issues, mapped, columns);
    }

    public static Set<String> mappedFields(DocumentTemplate template) {
        LinkedHashSet<String> mapped = new LinkedHashSet<>();
        if (template == null) return mapped;
        for (TemplateElement element : template.getElements()) {
            if (element == null || !element.isVisible()) continue;
            if (!element.getFieldKey().isBlank()) mapped.add(element.getFieldKey());
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
            if (element == null || !element.isVisible() || element.getType() != ElementType.ITEM_TABLE) continue;
            for (String column : element.getTableColumns()) {
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
            if (table == null || !table.isVisible() || table.getType() != ElementType.ITEM_TABLE) continue;
            double body = Math.max(0, table.getHeight() - table.getHeaderHeight());
            int rows = Math.max(1, (int)Math.floor(body / Math.max(1, table.getRowHeight())));
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
                        "Only " + rows + " item rows fit in the mapped area, so long documents may use many pages.",
                        "Increase the Item Table area or reduce row height, then preview a 25-item document.",
                        "ITEM_TABLE_PAGE_FLOW"));
            }
        }
    }

    private static void validateAddressBoxes(DocumentTemplate template, List<TemplateValidationIssue> issues) {
        for (TemplateElement element : template.getElements()) {
            if (element == null || !element.isVisible()) continue;
            String key = element.getFieldKey();
            if (key == null || !key.toLowerCase(Locale.ROOT).contains("address")) continue;
            if (!"WRAP".equals(element.getTextFit())) continue;
            double lineHeight = Math.max(1, element.getFontSize() * Math.max(1.0, element.getLineSpacing()));
            if (element.getHeight() + .01 < lineHeight * 2) {
                String label = TemplateFieldCatalog.findPdf(template.getDocumentType(), key) == null ? "Address" : TemplateFieldCatalog.findPdf(template.getDocumentType(), key).label();
                issues.add(new TemplateValidationIssue(Severity.WARNING,
                        label + " has space for less than two wrapped lines",
                        "Address Layout → " + label,
                        "Long customer, supplier or company addresses may be clipped even though Wrap is enabled.",
                        "Increase the field height or use a flow-aware Auto Height address region.",
                        "ADDRESS_WRAP"));
            }
        }
    }
}
