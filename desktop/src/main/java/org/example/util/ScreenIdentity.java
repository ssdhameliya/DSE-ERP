package org.example.util;

import java.util.Locale;

/** Central semantic identity for the active ERP screen/dialog title. */
public final class ScreenIdentity {
    private ScreenIdentity() { }

    public enum Kind { CREATE, EDIT, VIEW, LIST, SETTINGS, REPORT, DEFAULT }

    public static Kind kind(String title) {
        String value = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("add ") || value.startsWith("create ") || value.startsWith("new ")) return Kind.CREATE;
        if (value.startsWith("edit ") || value.startsWith("update ")) return Kind.EDIT;
        if (value.contains("setting")) return Kind.SETTINGS;
        if (value.contains("report")) return Kind.REPORT;
        if (value.contains("register") || value.contains("list") || value.contains("master")
                || value.contains("dashboard") || value.contains("audit") || value.contains("history")) return Kind.LIST;
        if (value.startsWith("view ") || value.contains("profile") || value.contains("360")
                || value.contains("detail") || value.contains("center")) return Kind.VIEW;
        return Kind.DEFAULT;
    }

    public static String styleClass(String title) {
        return switch (kind(title)) {
            case CREATE -> "screen-title-create";
            case EDIT -> "screen-title-edit";
            case VIEW -> "screen-title-view";
            case LIST -> "screen-title-list";
            case SETTINGS -> "screen-title-settings";
            case REPORT -> "screen-title-report";
            case DEFAULT -> "screen-title-default";
        };
    }
}
