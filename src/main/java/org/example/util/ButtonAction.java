package org.example.util;

/**
 * Stable business actions used to assign button icons independently of
 * visible text, localization, or controller-specific wording.
 */
public enum ButtonAction {
    LOGIN("login", "Sign in"),
    ADD("add", "Add new record"),
    EDIT("edit", "Edit selected record"),
    SAVE("save", "Save changes"),
    DELETE("delete", "Delete selected record"),
    CANCEL("cancel", "Cancel changes"),
    SEARCH("search", "Search records"),
    REFRESH("refresh", "Refresh data"),
    PRINT("print", "Print document"),
    EXPORT("export", "Export data"),
    IMPORT("import", "Import data"),
    EMAIL("email", "Send by email"),
    WHATSAPP("whatsapp", "Send by WhatsApp"),
    PAYMENT("payment", "Record payment"),
    DOWNLOAD("download", "Download file"),
    UPLOAD("upload", "Upload file"),
    VIEW("view", "View details"),
    CLOSE("close", "Close"),
    ACTIONS("actions", "More actions");

    private final String semantic;
    private final String tooltip;

    ButtonAction(String semantic, String tooltip) {
        this.semantic = semantic;
        this.tooltip = tooltip;
    }

    public String semantic() {
        return semantic;
    }

    public String tooltip() {
        return tooltip;
    }
}
