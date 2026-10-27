package org.example.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Universal centered modal inspection dialog for register and master screens.
 *
 * <p>Replaces all legacy docked side drawers across Bank Entry, Expense Entry,
 * Purchase Recon, Recon Supplier, User Access, Items Master, Inventory,
 * Customers, Suppliers and Master Data with a consistent, rich floating modal popup.</p>
 */
public final class RegisterDetailDrawer extends VBox {
    public record Field(String label, String value, String semantic) {
        public Field(String label, String value) {
            this(label, value, IconFactory.semanticForLabel(label));
        }
    }

    private final Label title = new Label("Details");
    private final Label subtitle = new Label("Review the selected record.");
    private final GridPane fieldsGrid = new GridPane();
    private final VBox extra = new VBox(10);
    private final HBox actions = new HBox(8);

    private Node ownerNode;
    private OwnedDialog<ButtonType> activeDialog;
    private Runnable closeAction = this::hideDrawer;

    public RegisterDetailDrawer() {
        // Keep VBox non-managed and invisible so embedding in legacy layouts has zero visual footprint
        setManaged(false);
        setVisible(false);
        setMinSize(0, 0);
        setPrefSize(0, 0);
        setMaxSize(0, 0);

        fieldsGrid.setHgap(14);
        fieldsGrid.setVgap(10);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setPercentWidth(50);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        fieldsGrid.getColumnConstraints().addAll(c0, c1);
    }

    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction == null ? this::hideDrawer : closeAction;
    }

    public void showRecord(String heading, String caption, Field... recordFields) {
        showRecord(heading, caption, recordFields == null ? List.of() : List.of(recordFields));
    }

    public void showRecord(String heading, String caption, List<Field> recordFields) {
        fieldsGrid.getChildren().clear();
        fieldsGrid.getRowConstraints().clear();
        if (recordFields != null) {
            int row = 0;
            int col = 0;
            for (Field field : recordFields) {
                if (field == null) continue;
                Node fieldNode = fieldRow(field);
                boolean spanFull = isWideField(field);

                if (spanFull) {
                    if (col != 0) {
                        row++;
                        col = 0;
                    }
                    fieldsGrid.add(fieldNode, 0, row, 2, 1);
                    row++;
                    col = 0;
                } else {
                    fieldsGrid.add(fieldNode, col, row);
                    if (col == 0) {
                        col = 1;
                    } else {
                        col = 0;
                        row++;
                    }
                }
            }
        }

        buildAndShowDialog(heading, caption);
    }

    private void buildAndShowDialog(String heading, String caption) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buildAndShowDialog(heading, caption));
            return;
        }

        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.close();
            activeDialog = null;
        }

        Node anchor = ownerNode != null ? ownerNode : this;
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(anchor);
        this.activeDialog = dialog;

        String safeHeading = safe(heading, "Record Details");
        dialog.setTitle(safeHeading);
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("approved-dialog", "erp-document-inspection-dialog", "erp-detail-drawer-dialog", "erp-global-detail-drawer");
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Node defaultButtonBar = pane.lookup(".button-bar");
        if (defaultButtonBar != null) {
            defaultButtonBar.setVisible(false);
            defaultButtonBar.setManaged(false);
        }

        // Header
        Label iconLabel = new Label();
        String semantic = resolveHeadingSemantic(safeHeading);
        iconLabel.setGraphic(IconFactory.compactIcon(semantic, 22));

        title.setText(safeHeading);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #ffffff;");

        subtitle.setText(safe(caption, "Review selected record."));
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        subtitle.setWrapText(true);

        VBox titleBox = new VBox(2, title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button btnCloseTop = new Button("×");
        btnCloseTop.getStyleClass().addAll("dialog-close-btn");
        btnCloseTop.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 22px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 6;");
        btnCloseTop.setOnAction(e -> hideDrawer());

        HBox header = new HBox(12, iconLabel, titleBox, btnCloseTop);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 20, 14, 20));
        header.setStyle("-fx-background-color: rgba(15, 23, 42, 0.95); -fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-width: 0 0 1 0;");

        // Body Content
        VBox body = new VBox(14, fieldsGrid, extra);
        body.setPadding(new Insets(16, 20, 16, 20));
        body.setStyle("-fx-background-color: #0b1325;");

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #0b1325; -fx-background-color: #0b1325; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Footer / Action Bar
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnClose.setGraphic(IconFactory.compactIcon("cancel", 14));
        btnClose.setOnAction(e -> hideDrawer());

        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setSpacing(8);

        HBox footer = new HBox(10, spacer, actions, btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 20, 14, 20));
        footer.setStyle("-fx-background-color: rgba(15, 23, 42, 0.95); -fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-width: 1 0 0 0;");

        VBox dialogRoot = new VBox(header, scroll, footer);
        dialogRoot.setStyle("-fx-background-color: #0b1325; -fx-background-radius: 14; -fx-border-color: rgba(255, 255, 255, 0.12); -fx-border-radius: 14; -fx-border-width: 1;");
        dialogRoot.setPrefSize(680, 560);
        dialogRoot.setMinSize(540, 400);

        pane.setContent(dialogRoot);
        pane.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        dialog.setOnHidden(e -> {
            this.activeDialog = null;
            if (closeAction != null) {
                closeAction.run();
            }
        });

        dialog.show();
        ProfessionalUiEnhancer.enhance(dialogRoot);
    }

    public void setExtra(Node... nodes) {
        extra.getChildren().clear();
        if (nodes != null) {
            for (Node node : nodes) if (node != null) extra.getChildren().add(node);
        }
        extra.setManaged(!extra.getChildren().isEmpty());
        extra.setVisible(!extra.getChildren().isEmpty());
        ProfessionalUiEnhancer.enhance(extra);
    }

    public void setActions(Node... nodes) {
        actions.getChildren().clear();
        if (nodes != null) {
            for (Node node : nodes) if (node != null) actions.getChildren().add(node);
        }
        actions.setManaged(!actions.getChildren().isEmpty());
        actions.setVisible(!actions.getChildren().isEmpty());
        ProfessionalUiEnhancer.enhance(actions);
    }

    public void hideDrawer() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.close();
        }
        activeDialog = null;
        setVisible(false);
        setManaged(false);
    }

    public boolean isOpen() {
        return activeDialog != null && activeDialog.isShowing();
    }

    public void attachBesideTable(TableView<?> table) {
        if (table != null) {
            this.ownerNode = table;
        }
    }

    public static Field field(String label, Object value, String semantic) {
        return new Field(label, display(value), semantic);
    }

    public static Field field(String label, Object value) {
        return new Field(label, display(value), IconFactory.semanticForLabel(label));
    }

    public static String statusSemantic(String status) {
        String value = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (value.contains("reconciled") || value.contains("active") || value.contains("paid") || value.contains("complete") || value.contains("deposit") || value.contains("credit") || value.contains("in stock")) return "complete";
        if (value.contains("partial")) return "partial";
        if (value.contains("review") || value.contains("pending") || value.contains("low") || value.contains("open")) return "warning";
        if (value.contains("inactive") || value.contains("locked") || value.contains("withdraw") || value.contains("debit") || value.contains("out of stock") || value.contains("overdue")) return "error";
        return "status";
    }

    private static String resolveHeadingSemantic(String heading) {
        if (heading == null) return "details";
        String lower = heading.toLowerCase(Locale.ROOT);
        if (lower.contains("bank")) return "bank";
        if (lower.contains("expense")) return "currency";
        if (lower.contains("recon")) return "reconcile";
        if (lower.contains("user")) return "user";
        if (lower.contains("item") || lower.contains("stock")) return "item";
        if (lower.contains("customer") || lower.contains("supplier") || lower.contains("party")) return "supplier";
        if (lower.contains("lookup") || lower.contains("master")) return "settings";
        return "details";
    }

    private static boolean isWideField(Field field) {
        if (field == null) return false;
        String lbl = field.label() == null ? "" : field.label().toLowerCase(Locale.ROOT);
        if (lbl.contains("description") || lbl.contains("narration") || lbl.contains("notes")
                || lbl.contains("remarks") || lbl.contains("address") || lbl.contains("tax review")) {
            return true;
        }
        return field.value() != null && field.value().length() > 42;
    }

    private Node fieldRow(Field field) {
        String semantic = field.semantic() == null || field.semantic().isBlank()
            ? IconFactory.semanticForLabel(field.label()) : field.semantic();
        if (semantic == null || semantic.isBlank()) semantic = "identity";

        Label caption = new Label(safe(field.label(), "Field"));
        caption.getStyleClass().addAll("erp-drawer-caption", "detail-label", "field-label");
        caption.setGraphic(IconFactory.compactIcon(semantic, 13));
        caption.setGraphicTextGap(6);
        IconFactory.applySemanticLabelColour(caption, semantic);
        caption.getProperties().put("erp.label.icon.skip", true);
        caption.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #94a3b8;");

        Label value = new Label(safe(field.value(), "—"));
        value.setWrapText(true);
        value.getStyleClass().addAll("erp-drawer-value", "erp-drawer-value-" + colourClass(semantic));
        value.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: " + colourHex(semantic) + ";");
        VBox.setVgrow(value, Priority.NEVER);

        VBox row = new VBox(4, caption, value);
        row.getStyleClass().add("erp-detail-drawer-field");
        row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.03); -fx-background-radius: 8; -fx-border-color: rgba(255, 255, 255, 0.06); -fx-border-radius: 8; -fx-border-width: 1; -fx-padding: 8 12;");
        return row;
    }

    private static String colourClass(String semantic) {
        if (semantic == null) return "indigo";
        return switch (semantic.toLowerCase(Locale.ROOT)) {
            case "complete", "credit", "sale", "save" -> "green";
            case "error", "delete", "debit" -> "red";
            case "warning", "partial", "tax", "category", "reference" -> "orange";
            case "supplier", "attachment", "email", "phone", "inventory", "location" -> "teal";
            case "bank", "status", "reconcile", "notes", "calendar", "role", "security" -> "purple";
            default -> "blue";
        };
    }

    private static String colourHex(String semantic) {
        if (semantic == null) return "#f1f5f9";
        return switch (semantic.toLowerCase(Locale.ROOT)) {
            case "complete", "credit", "sale", "save" -> "#34d399";
            case "error", "delete", "debit" -> "#f87171";
            case "warning", "partial", "tax", "category", "reference" -> "#fbbf24";
            case "supplier", "attachment", "email", "phone", "inventory", "location" -> "#38bdf8";
            case "bank", "status", "reconcile", "notes", "calendar", "role", "security" -> "#c084fc";
            default -> "#f1f5f9";
        };
    }

    private static String display(Object value) {
        if (value == null) return "—";
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "—" : text;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
