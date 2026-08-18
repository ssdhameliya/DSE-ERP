package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Spinner;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

/**
 * Central scalable icon provider for the complete ERP application.
 *
 * <p>Icons are rendered from the Ikonli FontAwesome 5 pack. They therefore
 * remain sharp at every display scale and do not depend on PNG or SVG files.
 * Semantic CSS classes provide consistent colour tiles in light and dark mode.
 * Existing controllers keep calling {@link #icon(String)}, so this replacement
 * does not change navigation or action handlers.</p>
 */
public final class IconFactory {
    private static final String[] BUTTON_VARIANTS = {
        "erp-button-primary", "erp-button-secondary", "erp-button-success",
        "erp-button-warning", "erp-button-danger", "erp-button-icon"
    };

    private IconFactory() {
    }

    public static Node icon(String name) {
        return icon(name, 24);
    }

    /** Creates a coloured icon tile sized for a button, menu or panel. */
    public static Node icon(String name, double size) {
        String semantic = normalize(name);
        FontIcon glyph = new FontIcon(literal(semantic));
        glyph.setIconSize(Math.max(12, (int) Math.round(size * 0.68)));
        glyph.getStyleClass().addAll("erp-ikonli-glyph", "erp-icon-glyph-" + colour(semantic));
        glyph.setMouseTransparent(true);
        glyph.getProperties().put("erp.icon.factory", true);
        glyph.getProperties().put("erp.icon.semantic", semantic);

        double tileSize = Math.max(24, size + 8);
        StackPane tile = new StackPane(glyph);
        tile.getStyleClass().addAll("erp-icon-holder", "erp-icon-tile", "erp-icon-" + colour(semantic));
        tile.setMinSize(tileSize, tileSize);
        tile.setPrefSize(tileSize, tileSize);
        tile.setMaxSize(tileSize, tileSize);
        tile.setMouseTransparent(true);
        tile.getProperties().put("erp.icon.factory", true);
        tile.getProperties().put("erp.icon.semantic", semantic);
        return tile;
    }

    /** Creates a standalone status glyph with an explicit business-state colour. */
    public static Node statusIcon(String name, String color) {
        String semantic = normalize(name);
        if ("save".equals(semantic)) semantic = "complete";
        FontIcon glyph = new FontIcon(literal(semantic));
        glyph.setIconSize(16);
        glyph.setStyle("-fx-icon-color: " + color + ";");
        glyph.getStyleClass().add("erp-status-glyph");
        glyph.setMouseTransparent(true);
        glyph.getProperties().put("erp.icon.factory", true);
        glyph.getProperties().put("erp.icon.semantic", semantic);
        return glyph;
    }

    /** Adds the shared icon vocabulary to every newly loaded page and dialog. */
    public static void decorate(Node node) {
        // A custom dialog supplies its own title icon and action presentation.
        // Do not infer icons from button text inside that shell, otherwise
        // labels such as "Mark all read" can become generic ellipsis icons.
        if (node instanceof DialogPane pane
                && (Boolean.TRUE.equals(pane.getProperties().get("erp-dialog-custom"))
                    || pane.getStyleClass().contains("modern-dialog"))) {
            // Custom dialogs own their buttons/action presentation, but field labels
            // still participate in the shared semantic icon vocabulary. Walk only
            // labels here so automatic button inference cannot regress dialog actions.
            decorateFieldLabelsOnly(pane);
            return;
        }

        // CheckBox already has a native checked/unchecked affordance.  Do not
        // decorate it with a second generic action/settings icon.
        if (node instanceof CheckBox checkBox) {
            Node graphic = checkBox.getGraphic();
            if (graphic != null && Boolean.TRUE.equals(graphic.getProperties().get("erp.icon.factory"))) {
                checkBox.setGraphic(null);
            }
            checkBox.getProperties().put("erp.icon.skip", true);
            return;
        }

        if (node instanceof Label label) {
            decorateFieldLabel(label);
        }

        if (node instanceof ButtonBase button) {
            String semantic = semantic(button);
            String originalText = clean(button.getText());
            // v3.0.4 guarantees a visible semantic graphic on every actionable
            // ButtonBase. Explicit mappings remain preferred; uncommon controls
            // receive a neutral document/action fallback rather than no icon.
            if (semantic == null && !Boolean.TRUE.equals(button.getProperties().get("erp.icon.skip"))) {
                semantic = originalText.isBlank() ? "actions" : "document";
            }
            if (semantic != null) {
                button.setText(clean(button.getText()));
                boolean sidebar = isInside(button, "erp-sidebar");
                double size = button.getStyleClass().contains("top-icon") ? 22 : sidebar ? 18 : 17;
                String presentation = sidebar ? "tile" : "glyph";
                String explicitSemantic = (String) button.getProperties().get("erp.icon.semantic");
                String resolvedSemantic = explicitSemantic == null || explicitSemantic.isBlank()
                    ? semantic : normalize(explicitSemantic);
                String iconKey = resolvedSemantic + ":" + presentation + ":" + size;

                // Respect graphics intentionally assigned by FXML/controllers. Automatic
                // decoration only fills blank controls or refreshes icons it owns itself.
                boolean decoratorOwnsGraphic = button.getProperties().containsKey("erp.icon.key");
                boolean factoryOwnsGraphic = button.getGraphic() != null
                    && Boolean.TRUE.equals(button.getGraphic().getProperties().get("erp.icon.factory"));
                if (button.getGraphic() == null || decoratorOwnsGraphic || factoryOwnsGraphic) {
                    if (!iconKey.equals(button.getProperties().get("erp.icon.key"))) {
                        button.setGraphic(sidebar ? icon(resolvedSemantic, size) : actionIcon(resolvedSemantic, size));
                        button.getProperties().put("erp.icon.key", iconKey);
                    }
                }
                applyButtonVariant(button, resolvedSemantic);
                button.setContentDisplay(originalText.isBlank()
                    ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
                button.setGraphicTextGap(7);
                if (!originalText.isBlank()) {
                    if (button.getAccessibleText() == null || button.getAccessibleText().isBlank()) {
                        button.setAccessibleText(originalText);
                    }
                    if (button.getTooltip() == null) {
                        button.setTooltip(new Tooltip(originalText));
                    }
                } else if (button.getAccessibleText() == null || button.getAccessibleText().isBlank()) {
                    button.setAccessibleText(resolvedSemantic.replace('-', ' '));
                }
            }
        }
        if (node instanceof MenuButton menu) {
            if (isTableActionMenu(menu)) {
                String tooltipText = clean(menu.getText());
                if (tooltipText.isBlank() || tooltipText.equals("...") || tooltipText.equals("⋮")) tooltipText = "Actions";
                menu.setText("Actions");
                menu.setContentDisplay(ContentDisplay.LEFT);
                menu.setGraphic(actionIcon("actions", 16));
                menu.setGraphicTextGap(6);
                // Geometry belongs to ui-components.css. Java supplies only semantics/behaviour.
                if (menu.getTooltip() == null) menu.setTooltip(new Tooltip("Open actions"));
            }
            decorateMenuItems(menu);
            if (!Boolean.TRUE.equals(menu.getProperties().get("erp.icons.bound"))) {
                menu.getProperties().put("erp.icons.bound", true);
                menu.showingProperty().addListener((obs, oldValue, showing) -> {
                    if (showing) decorateMenuItems(menu);
                });
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) decorate(child);
        }
    }


    private static void decorateFieldLabelsOnly(Node node) {
        if (node instanceof Label label) decorateFieldLabel(label);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) decorateFieldLabelsOnly(child);
        }
    }

    private static void decorateFieldLabel(Label label) {
        if (label == null || label.getGraphic() != null || Boolean.TRUE.equals(label.getProperties().get("erp.label.icon.skip"))) return;
        String text = clean(label.getText());
        if (text.isBlank()) return;
        String styles = String.join(" ", label.getStyleClass()).toLowerCase(Locale.ROOT);
        boolean fieldCaptionStyle = styles.contains("field-caption");
        if (styles.contains("erp-table-header-label") || styles.contains("page-title") || styles.contains("screen-title")
                || styles.contains("metric-value") || styles.contains("metric-note")
                || styles.contains("subtitle") || styles.contains("description")
                || (styles.contains("caption") && !fieldCaptionStyle)
                || styles.contains("help") || styles.contains("placeholder") || styles.contains("error")) return;
        boolean panelTitleStyle = styles.contains("section-title") || styles.contains("card-title")
                || styles.contains("panel-title") || styles.contains("subsection-title")
                || styles.contains("drawer-section") || styles.contains("summary-title")
                || styles.contains("history-title") || styles.contains("attachment-title")
                || styles.contains("approved-card-title") || styles.contains("backup-section-title")
                || styles.contains("safe-rollback-section-title") || styles.contains("setup-section-title")
                || styles.contains("purchase-section-title") || styles.contains("finance-section-title")
                || styles.contains("dialog-section-title") || styles.contains("backup-step-title")
                || styles.contains("doc-studio-section-title") || styles.contains("settings-section-title")
                || styles.contains("settings-subsection-title") || styles.contains("bank-recon-how-title")
                || styles.contains("bank-flow-step-title") || styles.contains("section-accent-title");
        boolean fieldStyle = styles.contains("field-label") || styles.contains("form-label") || styles.contains("filter-label")
                || styles.contains("meta-label") || styles.contains("detail-label") || styles.contains("field-caption")
                || styles.contains("inline-label") || styles.contains("field-blue") || styles.contains("field-orange")
                || styles.contains("field-cyan") || styles.contains("field-green") || styles.contains("field-red")
                || styles.contains("location-label") || isGridFieldLabel(label)
                || isStructurallyPairedFieldLabel(label);
        if (!fieldStyle && !panelTitleStyle) return;
        String semantic = semanticForLabel(text);
        if (semantic == null) semantic = panelTitleStyle ? "document" : "identity";
        Node graphic = compactIcon(semantic, panelTitleStyle ? 14 : 13);
        graphic.getStyleClass().add("erp-field-label-icon");
        label.setGraphic(graphic);
        label.setContentDisplay(ContentDisplay.LEFT);
        label.setGraphicTextGap(5);
        label.getProperties().put("erp.label.icon.semantic", semantic);
    }

    /**
     * Recognizes direct label/input pairs used throughout ordinary pages and
     * programmatic Add/Edit dialogs even when no field-label style class exists.
     */
    /** Recognizes label/input pairs placed directly in the same GridPane row. */
    private static boolean isGridFieldLabel(Label label) {
        Parent parent = label == null ? null : label.getParent();
        if (!(parent instanceof GridPane grid)) return false;
        Integer rowIndex = GridPane.getRowIndex(label);
        int row = rowIndex == null ? 0 : rowIndex;
        for (Node sibling : grid.getChildren()) {
            if (sibling == label || !isFieldInput(sibling)) continue;
            Integer siblingRowIndex = GridPane.getRowIndex(sibling);
            int siblingRow = siblingRowIndex == null ? 0 : siblingRowIndex;
            if (siblingRow == row) return true;
        }
        return false;
    }

    private static boolean isStructurallyPairedFieldLabel(Label label) {
        Parent parent = label == null ? null : label.getParent();
        if (!(parent instanceof VBox || parent instanceof HBox)) return false;
        if (!(parent instanceof Pane pane)) return false;
        if (pane.getChildren().size() > 6) return false;
        for (Node sibling : pane.getChildren()) {
            if (sibling != label && isFieldInput(sibling)) return true;
        }
        return false;
    }

    private static boolean isFieldInput(Node node) {
        return node instanceof TextInputControl
                || node instanceof ComboBoxBase<?>
                || node instanceof Spinner<?>
                || node instanceof ChoiceBox<?>;
    }

    private static boolean isTableActionMenu(MenuButton menu) {
        if (menu == null) return false;
        String styles = String.join(" ", menu.getStyleClass()).toLowerCase(Locale.ROOT);
        String text = clean(menu.getText()).toLowerCase(Locale.ROOT);
        return styles.contains("row-actions") || styles.contains("table-action")
            || styles.contains("user-action-menu") || styles.contains("approved-row-action")
            || styles.contains("backup-row-actions") || styles.contains("bank-row-action")
            || text.equals("actions") || text.equals("reminder actions")
            || text.equals("⋮") || text.equals("...");
    }

    private static void decorateMenuItems(MenuButton menu) {
        for (MenuItem item : menu.getItems()) {
            String label = clean(item.getText());
            String semantic = semantic(label);
            if (semantic == null && !label.isBlank()) semantic = "document";
            if (semantic == null) continue;
            item.setText(label);
            if (item.getGraphic() == null || Boolean.TRUE.equals(item.getProperties().get("erp.icon.decorated"))) {
                item.setGraphic(actionIcon(semantic, 16));
                item.getProperties().put("erp.icon.decorated", true);
            }
        }
    }

    /**
     * Uses a crisp semantic glyph inside action buttons. The larger coloured
     * tile returned by {@link #icon(String, double)} remains available for
     * navigation, KPI cards and other visual panels.
     */
    private static Node actionIcon(String semantic, double size) {
        FontIcon glyph = new FontIcon(literal(semantic));
        glyph.setIconSize(Math.max(14, (int) Math.round(size)));
        glyph.getStyleClass().addAll(
            "erp-action-glyph",
            "erp-action-glyph-" + colour(semantic)
        );
        glyph.setMouseTransparent(true);
        glyph.getProperties().put("erp.icon.factory", true);
        glyph.getProperties().put("erp.icon.semantic", semantic);
        return glyph;
    }

    /**
     * Creates the compact, coloured glyph used by table headers, context menus
     * and status labels. Keeping this public prevents individual screens from
     * inventing a different icon size or colour vocabulary.
     */
    public static Node compactIcon(String name, double size) {
        return actionIcon(normalize(name), size);
    }

    /**
     * Declares an explicit semantic for a TableColumn and renders it through the
     * same shared header renderer used by the global table enhancer. Controllers
     * keep choosing business meaning; IconFactory owns the presentation.
     */
    public static void applyTableHeaderIcon(TableColumn<?, ?> column, String semantic) {
        if (column == null) return;
        String normalized = normalize(semantic);
        Object stored = column.getProperties().get("erp-header-label");
        String heading = stored instanceof String value ? value : column.getText();
        heading = heading == null ? "" : heading.trim();
        if ("actions".equals(normalized) && heading.isBlank()) heading = "Actions";

        column.getProperties().put("erp-header-label", heading);
        column.getProperties().put("erp-header-semantic", normalized);
        column.getProperties().put("erp-header-explicit", true);
        column.getProperties().remove("erp-header-preserve");
        column.setText("");
        column.setGraphic(tableHeader(heading, normalized));
        if (!column.getStyleClass().contains("erp-icon-table-column")) {
            column.getStyleClass().add("erp-icon-table-column");
        }
    }

    /** One canonical icon-plus-label renderer for every ERP TableColumn header. */
    public static Node tableHeader(String label, String semantic) {
        Label title = new Label(label == null ? "" : label);
        title.getStyleClass().add("erp-table-header-label");
        HBox header = new HBox(6, compactIcon(semantic, 14), title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMouseTransparent(true);
        header.getStyleClass().add("erp-table-header-content");
        return header;
    }

    /** Public label lookup used by tables, dialogs and future ERP controls. */
    public static String semanticForLabel(String text) {
        return semantic(text);
    }

    /**
     * Resolves the visual identity of an ERP page from its destination title.
     * Register/detail wording is intentionally secondary to the business module,
     * e.g. "Sales Register" stays a sales/cart icon rather than a generic register icon.
     */
    public static String semanticForPageTitle(String title) {
        String value = title == null ? "" : title.toLowerCase(Locale.ROOT).trim();
        if (value.contains("sale")) return "sale";
        if (value.contains("purchase")) return "purchase";
        if (value.contains("quotation")) return "quotation";
        if (value.contains("bank")) return "bank";
        if (value.contains("expense") || value.contains("payment")) return "payment";
        if (value.contains("customer")) return "customer";
        if (value.contains("supplier")) return "supplier";
        if (value.contains("inventory")) return "inventory";
        if (value.contains("item")) return "item";
        if (value.contains("report")) return "report";
        if (value.contains("reminder")) return "reminder";
        if (value.contains("rollback")) return "rollback";
        if (value.contains("setting")) return "settings";
        if (value.contains("user") || value.contains("access")) return "user";
        if (value.contains("communication") || value.contains("email") || value.contains("whatsapp")) return "communication";
        if (value.contains("master")) return "master";
        if (value.equals("undo")) return "reset";
        if (value.equals("redo")) return "reopen";
        if (value.contains("fit page") || value.contains("fit width")) return "view";
        if (value.contains("heading")) return "document";
        if (value.equals("text")) return "notes";
        if (value.equals("image") || value.contains("imported image") || value.contains("company logo") || value.contains("app brand") || value.contains("signature")) return "attachment";
        if (value.contains("payment qr")) return "payment";
        if (value.contains("rectangle")) return "category";
        if (value.equals("line")) return "link";
        if (value.contains("dashboard")) return "dashboard";
        String resolved = semantic(title);
        return resolved == null ? "document" : resolved;
    }

    /** Assigns one shared visual role without changing the button action. */
    private static void applyButtonVariant(ButtonBase button, String semantic) {
        if (isInside(button, "erp-sidebar") || button.getStyleClass().contains("square-action")) {
            return;
        }

        button.getStyleClass().removeAll(BUTTON_VARIANTS);
        button.getStyleClass().removeIf(style -> style.startsWith("erp-semantic-"));
        button.getStyleClass().addAll("erp-action-button", "erp-semantic-" + colour(semantic));

        String text = button.getText() == null ? "" : button.getText().toLowerCase(Locale.ROOT);
        String variant;
        if (button.getStyleClass().contains("top-icon") || text.isBlank()) {
            variant = "erp-button-icon";
        } else if ("delete".equals(semantic) || "error".equals(semantic)) {
            variant = "erp-button-danger";
        } else if ("restore".equals(semantic) || "warning".equals(semantic)) {
            variant = "erp-button-warning";
        } else if ("complete".equals(semantic)) {
            variant = "erp-button-success";
        } else if ("add".equals(semantic) || "save".equals(semantic) || "backup".equals(semantic)
            || "import".equals(semantic)) {
            variant = "erp-button-primary";
        } else {
            variant = "erp-button-secondary";
        }
        button.getStyleClass().add(variant);
    }

    /** Returns true when a control belongs to a separately styled shell area. */
    private static boolean isInside(Node node, String styleClass) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains(styleClass)) return true;
        }
        return false;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceFirst("^[^\\p{L}\\p{N}#]+\\s*", "");
    }

    /** Maps legacy aliases to the stable semantic vocabulary. */
    private static String normalize(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        return switch (value) {
            case "cart" -> "sale";
            case "users" -> "user";
            case "box" -> "item";
            case "stock" -> "inventory";
            case "upload" -> "import";
            case "check" -> "complete";
            case "close" -> "cancel";
            case "tag", "tags", "brand" -> "category";
            case "percent", "percentage" -> "tax";
            case "measure", "measurement", "uom" -> "unit";
            case "warehouse" -> "inventory";
            case "analytics", "chart" -> "report";
            case "sales" -> "sale";
            case "export" -> "download";
            case "send" -> "sent";
            default -> value.isBlank() ? "unknown" : value;
        };
    }

    /** FontAwesome 5 literal used by Ikonli; no external image file is loaded. */
    private static String literal(String semantic) {
        return switch (semantic) {
            case "dashboard" -> "fas-th-large";
            case "business" -> "fas-building";
            case "application" -> "fas-desktop";
            case "chevron" -> "fas-chevron-right";
            case "sale" -> "fas-shopping-cart";
            case "purchase" -> "fas-briefcase";
            case "quotation" -> "fas-file-alt";
            case "payment" -> "fas-credit-card";
            case "customer" -> "fas-user";
            case "user" -> "fas-user-circle";
            case "supplier" -> "fas-users";
            case "item" -> "fas-box";
            case "inventory" -> "fas-boxes";
            case "master" -> "fas-address-card";
            case "report" -> "fas-chart-bar";
            case "email" -> "fas-envelope";
            case "notification" -> "fas-bell";
            case "menu" -> "fas-bars";
            case "search" -> "fas-search";
            case "sun" -> "fas-sun";
            case "moon" -> "fas-moon";
            case "error" -> "fas-exclamation-triangle";
            case "settings" -> "fas-cog";
            case "view" -> "fas-eye";
            case "edit" -> "fas-pen";
            case "delete" -> "fas-trash-alt";
            case "print" -> "fas-print";
            case "download" -> "fas-download";
            case "excel" -> "fas-file-excel";
            case "pdf" -> "fas-file-pdf";
            case "first" -> "fas-angle-double-left";
            case "previous" -> "fas-angle-left";
            case "next" -> "fas-angle-right";
            case "last" -> "fas-angle-double-right";
            case "reset" -> "fas-undo-alt";
            case "notes" -> "fas-sticky-note";
            case "import" -> "fas-cloud-upload-alt";
            case "save" -> "fas-save";
            case "add" -> "fas-plus";
            case "cancel" -> "fas-times";
            case "refresh" -> "fas-sync-alt";
            case "filter" -> "fas-filter";
            case "attachment" -> "fas-paperclip";
            case "whatsapp" -> "fab-whatsapp";
            case "reminder" -> "fas-clock";
            case "complete" -> "fas-check-circle";
            case "more" -> "fas-ellipsis-h";
            case "actions" -> "fas-list-ul";
            case "backup" -> "fas-database";
            case "rollback" -> "fas-shield-alt";
            case "package" -> "fas-box-open";
            case "database" -> "fas-database";
            case "restore" -> "fas-history";
            case "validate" -> "fas-shield-alt";
            case "folder" -> "fas-folder-open";
            case "copy" -> "fas-copy";
            case "reopen" -> "fas-redo-alt";
            case "snooze" -> "fas-clock";
            case "lock" -> "fas-lock";
            case "return" -> "fas-undo-alt";
            case "calendar" -> "fas-calendar-alt";
            case "phone" -> "fas-phone-alt";
            case "identity" -> "fas-id-card";
            case "document" -> "fas-file-alt";
            case "quantity" -> "fas-sort-numeric-up";
            case "tax" -> "fas-percentage";
            case "discount" -> "fas-tags";
            case "currency" -> "fas-rupee-sign";
            case "debit" -> "fas-arrow-circle-down";
            case "credit" -> "fas-arrow-circle-up";
            case "balance" -> "fas-wallet";
            case "reference" -> "fas-hashtag";
            case "status" -> "fas-tasks";
            case "category" -> "fas-tags";
            case "unit" -> "fas-ruler";
            case "minimum" -> "fas-level-down-alt";
            case "source" -> "fas-database";
            case "role" -> "fas-user-shield";
            case "security" -> "fas-shield-alt";
            case "communication" -> "fas-comments";
            case "location" -> "fas-map-marker-alt";
            case "warning" -> "fas-exclamation-circle";
            case "confirmation" -> "fas-question-circle";
            case "sent" -> "fas-paper-plane";
            case "history" -> "fas-history";
            case "link" -> "fas-link";
            case "info" -> "fas-info-circle";
            case "adjust" -> "fas-sliders-h";
            case "workspace" -> "fas-folder-open";
            case "bank" -> "fas-university";
            case "delivery" -> "fas-truck";
            case "update" -> "fas-cloud-download-alt";
            case "permission" -> "fas-user-shield";
            case "select" -> "fas-hand-pointer";
            case "login" -> "fas-sign-in-alt";
            case "register" -> "fas-user-plus";
            case "test" -> "fas-vial";
            case "draft" -> "fas-file-signature";
            case "restart" -> "fas-redo";
            case "version" -> "fas-code-branch";
            case "compatibility" -> "fas-link";
            case "workflow" -> "fas-project-diagram";
            case "recovery" -> "fas-life-ring";
            case "database-backup" -> "fas-database";
            case "snapshot" -> "fas-camera";
            case "preserve" -> "fas-lock";
            case "installer" -> "fas-compact-disc";
            default -> "fas-question-circle";
        };
    }

    /** Assigns a recognisable business colour to each semantic icon. */
    private static String colour(String semantic) {
        return switch (semantic) {
            case "sale", "complete", "add", "import", "whatsapp", "save", "validate", "excel" -> "green";
            case "purchase", "item", "filter", "reminder", "calendar", "warning", "snooze", "quantity", "tax", "discount", "category", "minimum", "source", "reference", "rollback", "package" -> "orange";
            case "quotation", "document", "master", "return", "settings", "more", "actions", "status", "reopen", "role", "security", "reset", "notes", "print", "application" -> "purple";
            case "report", "delete", "error", "cancel", "pdf", "debit" -> "pink";
            case "inventory", "supplier", "attachment", "phone", "location", "communication", "unit", "email" -> "teal";
            case "payment", "customer", "user", "dashboard", "view", "download", "identity", "sent", "currency", "confirmation", "refresh", "restore", "folder", "copy", "backup", "database", "first", "previous", "next", "last", "history", "workspace", "select", "balance", "business", "chevron" -> "blue";
            case "credit" -> "green";
            case "adjust", "bank", "delivery", "update", "permission", "register", "draft", "restart", "workflow", "version" -> "purple";
            case "compatibility", "database-backup", "snapshot", "recovery" -> "blue";
            case "preserve" -> "green";
            case "installer" -> "orange";
            case "login", "test" -> "blue";
            default -> "indigo";
        };
    }

    /** Maps user-facing labels to semantic icons without touching their actions. */

    /** Resolves action semantics from label, fx:id and style classes. */
    private static String semantic(ButtonBase button) {
        String byText = semantic(button.getText());
        if (byText != null) return byText;
        String id = button.getId() == null ? "" : button.getId();
        String styles = String.join(" ", button.getStyleClass());
        String byMetadata = semantic(id + " " + styles);
        if (byMetadata != null) return byMetadata;
        if (button instanceof MenuButton) return "actions";
        String text = clean(button.getText());
        return text.isBlank() ? null : "document";
    }

    private static String semantic(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        if (value.isBlank()) return null;
        if (value.equals("×") || value.equals("✕") || value.equals("x")) return "cancel";
        if (value.equals("first") || value.equals("|‹") || value.equals("«")) return "first";
        if (value.equals("previous") || value.equals("‹")) return "previous";
        if (value.equals("next") || value.equals("›")) return "next";
        if (value.equals("last") || value.equals("›|") || value.equals("»")) return "last";
        if (value.equals("undo")) return "reset";
        if (value.equals("redo")) return "reopen";
        if (value.contains("fit page") || value.contains("fit width")) return "view";
        if (value.contains("heading")) return "document";
        if (value.equals("text")) return "notes";
        if (value.equals("image") || value.contains("imported image") || value.contains("company logo") || value.contains("app brand") || value.contains("signature")) return "attachment";
        if (value.contains("payment qr")) return "payment";
        if (value.contains("rectangle")) return "category";
        if (value.equals("line")) return "link";
        if (value.contains("dashboard")) return "dashboard";
        if (value.equals("today") || value.equals("yesterday") || value.contains("days")
            || value.contains("month") || value.contains("custom range")) return "calendar";
        if (value.contains("dark")) return "moon";
        if (value.contains("light")) return "sun";
        if (value.contains("logout") || value.contains("sign out")) return "lock";
        if (value.contains("login") || value.contains("sign in")) return "login";
        if (value.contains("register") || value.contains("create account")) return "register";
        if (value.contains("resend otp")) return "refresh";
        if (value.contains("test connection") || value.contains("test email")) return "test";
        if (value.contains("install") && value.contains("restart")) return "restart";
        if (value.contains("save") && value.contains("print")) return "print";
        if (value.contains("save") && value.contains("draft")) return "draft";
        if (value.contains("send") && value.contains("receipt")) return "email";
        if (value.contains("continue")) return "next";
        if (value.contains("guide") || value.contains("help")) return "document";
        if (value.contains("configure") || value.contains("manage")) return "settings";
        if (value.contains("follow up")) return "reminder";
        if (value.contains("expense")) return "payment";
        if (value.contains("convert to sale")) return "sale";
        if (value.contains("map column") || value.contains("mapping")) return "settings";
        if (value.contains("system health")) return "validate";
        if (value.contains("offline package") || value.contains("install update")) return "update";
        if (value.equals("menu")) return "menu";
        if (value.contains("notification")) return "notification";
        if (value.contains("quick action")) return "actions";
        if (value.contains("top customer")) return "customer";
        if (value.contains("recent activity") || value.contains("activity")) return "history";
        if (value.contains("ageing") || value.contains("aging") || value.contains("receivable") || value.contains("payable")) return "balance";
        if (value.contains("performance") || value.contains("summary")) return "report";
        if (value.contains("financial year") || value.contains("fiscal year")) return "calendar";
        if (value.contains("branch")) return "business";
        if (value.contains("smtp host") || value.equals("host")) return "link";
        if (value.contains("port")) return "reference";
        if (value.contains("repository owner")) return "user";
        if (value.contains("repository name") || value.contains("repository")) return "link";
        if (value.contains("warehouse")) return "inventory";
        if (value.contains("contact person")) return "user";
        if (value.contains("vehicle")) return "delivery";
        if (value.contains("source file")) return "folder";
        if (value.contains("transporter")) return "delivery";
        if (value.contains("priority")) return "warning";
        if (value.contains("title")) return "document";
        if (value.contains("sha-256") || value.contains("sha256") || value.contains("sha-56") || value.contains("checksum")) return "validate";
        if (value.contains("party code")) return "reference";
        if (value.equals("number") || value.endsWith(" number") || value.contains("no.")) return "reference";
        if (value.contains("category")) return "category";
        if (value.contains("company name") || value.contains("business name")) return "business";
        if (value.startsWith("pan") || value.contains(" pan")) return "identity";
        if (value.contains("business type") || value.contains("industry")) return "category";
        if (value.contains("application name")) return "application";
        if (value.contains("tagline")) return "notes";
        if (value.contains("workspace")) return "workspace";
        if (value.contains("date") || value.contains("time zone") || value.equals("time")) return "calendar";
        if (value.contains("reference") || value.contains("cheque") || value.contains("order no")) return "reference";
        if (value.contains("phone") || value.contains("mobile") || value.contains("contact no")) return "phone";
        if (value.contains("address") || value.contains("state") || value.contains("place of supply") || value.contains("location")) return "location";
        if (value.contains("website") || value.contains("url") || value.contains("link")) return "link";
        if (value.contains("currency")) return "currency";
        if (value.contains("account") || value.contains("bank") || value.contains("upi")) return "bank";
        if (value.contains("mode")) return "payment";
        if (value.contains("received from")) return "customer";
        if (value.contains("description") || value.contains("narration")) return "notes";
        if (value.contains("terms")) return "document";
        if (value.contains("delivery")) return "delivery";
        if (value.contains("application update") || value.contains("check update") || value.equals("update")) return "update";
        if (value.contains("permission")) return "permission";
        if (value.contains("role")) return "role";
        if (value.contains("history")) return "history";
        if (value.contains("adjust")) return "adjust";
        if (value.equals("ok") || value.equals("yes") || value.equals("confirm")) return "complete";
        if (value.contains("reopen")) return "reopen";
        if (value.contains("snooze")) return "snooze";
        if (value.contains("reminder")) return "reminder";
        if (value.contains("complete") || value.contains("approve")) return "complete";
        if (value.contains("validate") || value.contains("verify")) return "validate";
        if (value.contains("rollback")) return "rollback";
        if (value.contains("restore")) return "restore";
        if (value.contains("backup")) return "backup";
        if (value.contains("open folder") || value.contains("choose file") || value.contains("choose backup")
            || value.contains("browse") || value.contains("open location")) return "folder";
        if (value.contains("copy") || value.contains("duplicate")) return "copy";
        if (value.contains("lock") || value.contains("password")) return "lock";
        if (value.equals("...") || value.equals("…") || value.equals("⋮")
            || value.equals("actions") || value.equals("action")
            || value.contains("action menu") || value.contains("options")) return "actions";
        if (value.contains("more")) return "more";
        if (value.contains("whatsapp")) return "whatsapp";
        if (value.contains("supplier") || value.contains("hrm")) return "supplier";
        if (value.contains("customer") || value.contains("crm")) return "customer";
        if (value.contains("user") || value.contains("profile")) return "user";
        if (value.contains("quotation")) return "quotation";
        if (value.contains("purchase")) return "purchase";
        if (value.contains("sale")) return "sale";
        if (value.contains("master")) return "master";
        if (value.contains("inventory")) return "inventory";
        if (value.contains("item") || value.contains("product")) return "item";
        if (value.contains("import") || value.contains("upload")) return "import";
        if (value.contains("excel") || value.contains("spreadsheet")) return "excel";
        if (value.contains("pdf")) return "pdf";
        if (value.contains("reset")) return "reset";
        if (value.contains("note") || value.contains("remark")) return "notes";
        if (value.contains("download") || value.contains("export")) return "download";
        if (value.contains("save")) return "save";
        if (value.contains("add") || value.contains("new") || value.contains("create")) return "add";
        if (value.contains("edit") || value.contains("rename")) return "edit";
        if (value.contains("delete") || value.contains("remove")) return "delete";
        if (value.contains("clear filter") || value.contains("reset filter")) return "refresh";
        if (value.contains("clear selection") || value.contains("clear search") || value.equals("clear")) return "cancel";
        if (value.contains("cancel") || value.contains("close") || value.contains("back")) return "cancel";
        if (value.contains("refresh") || value.contains("reset")) return "refresh";
        if (value.contains("filter")) return "filter";
        if (value.contains("print")) return "print";
        if (value.contains("attach")) return "attachment";
        if (value.contains("email")) return "email";
        if (value.contains("payment")) return "payment";
        if (value.contains("amount") || value.contains("balance") || value.contains("price") || value.contains("rate")) return "currency";
        if (value.contains("discount")) return "discount";
        if (value.contains("tax") || value.contains("gst")) return "tax";
        if (value.contains("qty") || value.contains("quantity") || value.contains("stock")) return "quantity";
        if (value.contains("status")) return "status";
        if (value.contains("document") || value.contains("invoice")) return "document";
        if (value.contains("return") || value.contains("refund")) return "return";
        if (value.contains("view") || value.contains("preview") || value.contains("select")) return "view";
        return null;
    }

}
