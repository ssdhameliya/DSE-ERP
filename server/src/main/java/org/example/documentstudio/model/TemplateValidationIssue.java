package org.example.documentstudio.model;

/** Plain-language validation result shown by PDF Studio. */
public record TemplateValidationIssue(
        Severity severity,
        String title,
        String location,
        String detail,
        String fix,
        String requirementId) {

    public enum Severity { ERROR, WARNING }

    public TemplateValidationIssue {
        severity = severity == null ? Severity.ERROR : severity;
        title = safe(title);
        location = safe(location);
        detail = safe(detail);
        fix = safe(fix);
        requirementId = safe(requirementId);
    }

    public boolean error() { return severity == Severity.ERROR; }

    public String userMessage() {
        StringBuilder out = new StringBuilder();
        if (!location.isBlank()) out.append(location).append(" — ");
        out.append(title);
        if (!detail.isBlank()) out.append("\n").append(detail);
        if (!fix.isBlank()) out.append("\nFix: ").append(fix);
        return out.toString();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
