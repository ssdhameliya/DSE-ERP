package org.example.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Canonical read-only detail drawer for register/master screens.
 *
 * <p>The drawer intentionally owns presentation only. Controllers supply record
 * values and explicit actions; create/edit workflows remain in their existing
 * dialogs. This gives every current and future register the same "click = view"
 * contract without coupling the shared component to business services.</p>
 */
public final class RegisterDetailDrawer extends VBox {
    public record Field(String label, String value, String semantic) {
        public Field(String label, String value) {
            this(label, value, IconFactory.semanticForLabel(label));
        }
    }

    private final Label title = new Label("Details");
    private final Label subtitle = new Label("Review the selected record.");
    private final VBox fields = new VBox(9);
    private final VBox extra = new VBox(10);
    private final HBox actions = new HBox(8);
    private Runnable closeAction = this::hideDrawer;

    public RegisterDetailDrawer() {
        getStyleClass().addAll("detail-drawer", "erp-detail-drawer-card", "erp-global-detail-drawer");
        setSpacing(0);
        setMinWidth(300);
        setPrefWidth(340);
        setMaxWidth(390);
        setMinHeight(0);
        setManaged(false);
        setVisible(false);

        title.getStyleClass().addAll("section-title", "erp-detail-drawer-title");
        subtitle.getStyleClass().addAll("screen-subtitle", "erp-detail-drawer-subtitle");
        subtitle.setWrapText(true);

        Button close = new Button("Close");
        close.getStyleClass().addAll("approved-button", "approved-secondary-button", "erp-detail-drawer-close");
        close.setGraphic(IconFactory.compactIcon("cancel", 14));
        close.setOnAction(event -> closeAction.run());

        VBox heading = new VBox(3, title, subtitle);
        HBox.setHgrow(heading, Priority.ALWAYS);
        HBox header = new HBox(8, heading, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("erp-detail-drawer-header");
        header.setPadding(new Insets(14, 14, 12, 14));

        fields.getStyleClass().add("erp-detail-drawer-fields");
        extra.getStyleClass().add("erp-detail-drawer-extra");
        VBox body = new VBox(12, fields, extra);
        body.getStyleClass().add("erp-detail-drawer-body");
        body.setPadding(new Insets(12, 14, 14, 14));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("erp-detail-drawer-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("erp-detail-drawer-actions");
        actions.setPadding(new Insets(10, 14, 14, 14));

        getChildren().addAll(header, new Separator(), scroll, actions);
        ProfessionalUiEnhancer.enhance(this);
    }

    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction == null ? this::hideDrawer : closeAction;
    }

    public void showRecord(String heading, String caption, Field... recordFields) {
        showRecord(heading, caption, recordFields == null ? List.of() : List.of(recordFields));
    }

    public void showRecord(String heading, String caption, List<Field> recordFields) {
        title.setText(safe(heading, "Details"));
        subtitle.setText(safe(caption, "Review the selected record."));
        fields.getChildren().clear();
        if (recordFields != null) {
            for (Field field : recordFields) {
                if (field == null) continue;
                fields.getChildren().add(fieldRow(field));
            }
        }
        setManaged(true);
        setVisible(true);
        ProfessionalUiEnhancer.enhance(this);
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
        setVisible(false);
        setManaged(false);
    }

    public boolean isOpen() {
        return isVisible() && isManaged();
    }

    /**
     * Wraps the table's current card beside this drawer. No FXML rewrite is
     * required, which lets legacy and future master screens adopt the contract
     * while preserving their existing headers, filters, footers and actions.
     */
    public void attachBesideTable(TableView<?> table) {
        if (table == null || table.getParent() == null) return;
        Node content = table.getParent();
        Parent parent = content.getParent();
        if (parent == null || parent == this || Boolean.TRUE.equals(content.getProperties().get("erp.detail.drawer.wrapped"))) return;

        HBox workspace = new HBox(12);
        workspace.getStyleClass().add("erp-global-detail-workspace");
        workspace.setMinHeight(0);

        // Replace the content in its original parent before re-parenting it into
        // the workspace. JavaFX nodes may only have one Parent at a time; doing
        // this in the opposite order can detach the table card before we know
        // where to insert the workspace.
        boolean installed = false;
        if (parent instanceof VBox box) {
            int index = box.getChildren().indexOf(content);
            if (index >= 0) {
                Priority grow = VBox.getVgrow(content);
                Insets margin = VBox.getMargin(content);
                box.getChildren().set(index, workspace);
                VBox.setVgrow(workspace, grow == null ? Priority.ALWAYS : grow);
                VBox.setMargin(workspace, margin);
                installed = true;
            }
        } else if (parent instanceof HBox box) {
            int index = box.getChildren().indexOf(content);
            if (index >= 0) {
                Priority grow = HBox.getHgrow(content);
                Insets margin = HBox.getMargin(content);
                box.getChildren().set(index, workspace);
                HBox.setHgrow(workspace, grow == null ? Priority.ALWAYS : grow);
                HBox.setMargin(workspace, margin);
                installed = true;
            }
        } else if (parent instanceof StackPane pane) {
            int index = pane.getChildren().indexOf(content);
            if (index >= 0) {
                Pos alignment = StackPane.getAlignment(content);
                Insets margin = StackPane.getMargin(content);
                pane.getChildren().set(index, workspace);
                StackPane.setAlignment(workspace, alignment);
                StackPane.setMargin(workspace, margin);
                installed = true;
            }
        } else if (parent instanceof AnchorPane pane) {
            int index = pane.getChildren().indexOf(content);
            if (index >= 0) {
                Double top = AnchorPane.getTopAnchor(content), right = AnchorPane.getRightAnchor(content);
                Double bottom = AnchorPane.getBottomAnchor(content), left = AnchorPane.getLeftAnchor(content);
                pane.getChildren().set(index, workspace);
                AnchorPane.setTopAnchor(workspace, top); AnchorPane.setRightAnchor(workspace, right);
                AnchorPane.setBottomAnchor(workspace, bottom); AnchorPane.setLeftAnchor(workspace, left);
                installed = true;
            }
        } else if (parent instanceof BorderPane pane) {
            if (pane.getCenter() == content) { pane.setCenter(workspace); installed = true; }
            else if (pane.getTop() == content) { pane.setTop(workspace); installed = true; }
            else if (pane.getBottom() == content) { pane.setBottom(workspace); installed = true; }
            else if (pane.getLeft() == content) { pane.setLeft(workspace); installed = true; }
            else if (pane.getRight() == content) { pane.setRight(workspace); installed = true; }
        } else if (parent instanceof Pane pane) {
            int index = pane.getChildren().indexOf(content);
            if (index >= 0) { pane.getChildren().set(index, workspace); installed = true; }
        }

        if (!installed) return;
        workspace.getChildren().addAll(content, this);
        HBox.setHgrow(content, Priority.ALWAYS);
        if (content instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        content.getProperties().put("erp.detail.drawer.wrapped", true);
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

        Label value = new Label(safe(field.value(), "—"));
        value.setWrapText(true);
        value.getStyleClass().addAll("erp-drawer-value", "erp-drawer-value-" + colourClass(semantic));
        VBox.setVgrow(value, Priority.NEVER);

        VBox row = new VBox(4, caption, value);
        row.getStyleClass().add("erp-detail-drawer-field");
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

    private static String display(Object value) {
        if (value == null) return "—";
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "—" : text;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
