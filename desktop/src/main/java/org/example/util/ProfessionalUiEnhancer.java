package org.example.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.collections.ListChangeListener;

import java.util.Locale;

/**
 * Applies the ERP-wide table and date conventions after an FXML page is loaded.
 * Screen controllers remain responsible for business actions; this class keeps
 * resizing, placeholder selection columns and blank dates consistent.
 */
public final class ProfessionalUiEnhancer {
    private ProfessionalUiEnhancer() {}

    /** Enhances every supported control below the supplied page root. */
    public static void enhance(Node root) {
        if (root == null || Boolean.TRUE.equals(root.getProperties().get("erp-ui-enhanced"))) return;
        root.getProperties().put("erp-ui-enhanced", true);
        walk(root);
        SharedUiFramework.install(root);
    }

    private static void walk(Node node) {
        if (node instanceof TableView<?> table) enhanceTable(table);
        if (node instanceof DialogPane pane) enhanceDialog(pane);
        if (node instanceof PasswordField passwordField) schedulePasswordReveal(passwordField);
        if (node instanceof Parent parent) {
            installDynamicChildEnhancement(parent);
            for (Node child : parent.getChildrenUnmodifiable()) walk(child);
        }
    }


    /**
     * Adds one consistent reveal button to every PasswordField without changing
     * the controller's original PasswordField reference or stored value.
     */
    private static void schedulePasswordReveal(PasswordField field) {
        if (field == null
                || Boolean.TRUE.equals(field.getProperties().get("erp.password.reveal.installed"))
                || Boolean.TRUE.equals(field.getProperties().get("erp.password.reveal.pending"))) return;
        field.getProperties().put("erp.password.reveal.pending", true);
        Platform.runLater(() -> installPasswordReveal(field));
    }

    private static void installPasswordReveal(PasswordField field) {
        if (field == null) return;
        if (Boolean.TRUE.equals(field.getProperties().get("erp.password.reveal.installed"))) {
            field.getProperties().remove("erp.password.reveal.pending");
            return;
        }
        Parent parent = field.getParent();
        if (!(parent instanceof Pane pane)) {
            field.getProperties().remove("erp.password.reveal.pending");
            return;
        }
        int index = pane.getChildren().indexOf(field);
        if (index < 0) {
            field.getProperties().remove("erp.password.reveal.pending");
            return;
        }

        TextField plain = new TextField();
        plain.getStyleClass().setAll(field.getStyleClass());
        if (!plain.getStyleClass().contains("password-reveal-input")) plain.getStyleClass().add("password-reveal-input");
        if (!field.getStyleClass().contains("password-reveal-input")) field.getStyleClass().add("password-reveal-input");
        plain.textProperty().bindBidirectional(field.textProperty());
        plain.promptTextProperty().bind(field.promptTextProperty());
        plain.setMaxWidth(Double.MAX_VALUE);
        field.setMaxWidth(Double.MAX_VALUE);
        plain.setVisible(false);
        plain.setManaged(false);

        Button reveal = new Button();
        reveal.getStyleClass().addAll("password-reveal-button", "approved-button", "approved-icon-button");
        reveal.setGraphic(IconFactory.compactIcon("view", 14));
        reveal.getProperties().put("erp.icon.skip", true);
        reveal.getProperties().put("erp-icon-preserve", true);
        reveal.getProperties().put("erp.icon.semantic", "view");
        reveal.setFocusTraversable(false);
        reveal.setAccessibleText("Show password");
        reveal.setTooltip(new Tooltip("Show password"));

        // A Node can only have one JavaFX parent. Creating StackPane(plain, field, reveal)
        // while field still belongs to pane implicitly reparents/removes the field first,
        // making the previously captured index stale. That was the cause of the intermittent
        // VetoableListDecorator.remove(index) IndexOutOfBoundsException on JavaFX 25.
        copyLayoutConstraints(field, plain);
        if (!pane.getChildren().remove(field)) {
            field.getProperties().remove("erp.password.reveal.pending");
            return;
        }

        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().add("password-reveal-wrapper");
        wrapper.setMaxWidth(Double.MAX_VALUE);
        copyLayoutConstraints(field, wrapper);
        // Mark before attaching the wrapper so the dynamic enhancer cannot schedule the
        // same PasswordField again during the child-list mutation callback.
        field.getProperties().put("erp.password.reveal.installed", true);
        wrapper.getProperties().put("erp.password.reveal.wrapper", true);
        field.getProperties().remove("erp.password.reveal.pending");
        wrapper.getChildren().addAll(plain, field, reveal);
        StackPane.setAlignment(reveal, Pos.CENTER_RIGHT);
        StackPane.setMargin(reveal, new javafx.geometry.Insets(0, 7, 0, 0));
        pane.getChildren().add(Math.min(index, pane.getChildren().size()), wrapper);

        reveal.setOnAction(event -> {
            boolean show = !plain.isVisible();
            int caret = show ? field.getCaretPosition() : plain.getCaretPosition();
            plain.setVisible(show); plain.setManaged(show);
            field.setVisible(!show); field.setManaged(!show);
            reveal.setGraphic(IconFactory.compactIcon(show ? "hide" : "view", 14));
            reveal.getProperties().put("erp.icon.semantic", show ? "hide" : "view");
            reveal.setAccessibleText(show ? "Hide password" : "Show password");
            reveal.setTooltip(new Tooltip(show ? "Hide password" : "Show password"));
            TextField target = show ? plain : field;
            target.requestFocus();
            target.positionCaret(Math.max(0, Math.min(caret, target.getLength())));
        });

    }

    private static void copyLayoutConstraints(Node source, Node target) {
        GridPane.setRowIndex(target, GridPane.getRowIndex(source));
        GridPane.setColumnIndex(target, GridPane.getColumnIndex(source));
        GridPane.setRowSpan(target, GridPane.getRowSpan(source));
        GridPane.setColumnSpan(target, GridPane.getColumnSpan(source));
        GridPane.setHalignment(target, GridPane.getHalignment(source));
        GridPane.setValignment(target, GridPane.getValignment(source));
        GridPane.setHgrow(target, GridPane.getHgrow(source));
        GridPane.setVgrow(target, GridPane.getVgrow(source));
        GridPane.setMargin(target, GridPane.getMargin(source));
        HBox.setHgrow(target, HBox.getHgrow(source));
        HBox.setMargin(target, HBox.getMargin(source));
        VBox.setVgrow(target, VBox.getVgrow(source));
        VBox.setMargin(target, VBox.getMargin(source));
        StackPane.setAlignment(target, StackPane.getAlignment(source));
        StackPane.setMargin(target, StackPane.getMargin(source));
        AnchorPane.setTopAnchor(target, AnchorPane.getTopAnchor(source));
        AnchorPane.setRightAnchor(target, AnchorPane.getRightAnchor(source));
        AnchorPane.setBottomAnchor(target, AnchorPane.getBottomAnchor(source));
        AnchorPane.setLeftAnchor(target, AnchorPane.getLeftAnchor(source));
        BorderPane.setAlignment(target, BorderPane.getAlignment(source));
        BorderPane.setMargin(target, BorderPane.getMargin(source));
    }


    /** Enhances controls added after FXML loading, including dynamic dialog tables and action buttons. */
    private static void installDynamicChildEnhancement(Parent parent) {
        if (Boolean.TRUE.equals(parent.getProperties().get("erp-dynamic-child-listener"))) return;
        parent.getProperties().put("erp-dynamic-child-listener", true);
        parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                for (Node added : change.getAddedSubList()) {
                    // Route dynamic content through the same idempotent entry point.
                    // Cached/navigation pages are already marked erp-ui-enhanced and
                    // therefore return immediately instead of being recursively walked
                    // again each time they are attached to the dashboard content pane.
                    enhance(added);
                }
            }
        });
    }

    /**
     * Applies the shared visual language to legacy JavaFX Alert/Dialog instances.
     * New workflows use ModernDialog; this bridge keeps older controllers
     * consistent without changing their business handlers.
     */
    private static void enhanceDialog(DialogPane pane) {
        // Custom modern dialogs own their complete shell (title bar, graphic,
        // content and action bar). Applying the legacy bridge on top of them
        // creates duplicate icons, nested borders and conflicting padding.
        if (isCustomDialog(pane)) {
            return;
        }

        if (!pane.getStyleClass().contains("erp-modern-dialog")) {
            pane.getStyleClass().add("erp-modern-dialog");
        }
        // Do not inject a generic DialogPane graphic. JavaFX renders pane graphics
        // in a separate .graphic-container outside custom business content, which
        // produced the stray bell/notification icon seen on Bank Statement and
        // other owned dialogs. A dialog may still set its own graphic explicitly.

        Platform.runLater(() -> pane.getButtonTypes().forEach(type -> {
            Node button = pane.lookupButton(type);
            if (button instanceof ButtonBase action) {
                if (action.getText() == null || action.getText().isBlank()) action.setText(type.getText());
                IconFactory.decorate(action);
            }
        }));
    }


    /** Returns true when a dialog explicitly owns its visual presentation. */
    private static boolean isCustomDialog(DialogPane pane) {
        return Boolean.TRUE.equals(pane.getProperties().get("erp-dialog-custom"))
            || pane.getStyleClass().contains("modern-dialog");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void enhanceTable(TableView table) {
        applyTableProfile(table);
        if (!table.getStyleClass().contains("erp-full-width-table")) {
            table.getStyleClass().add("erp-full-width-table");
        }
        table.setMaxWidth(Double.MAX_VALUE);
        table.setMaxHeight(Double.MAX_VALUE);
        if (table.getParent() instanceof VBox) VBox.setVgrow(table, Priority.ALWAYS);
        if (table.getParent() instanceof HBox) HBox.setHgrow(table, Priority.ALWAYS);

        decorateColumns(table.getColumns());
        installCellValueTooltips(table);
        // Native constrained resize is the single register/master sizing authority.
        // Avoid a second asynchronous width pass after the page becomes visible.

        // Controllers add a number of business columns after FXML loading.
        // Keep header decoration live so those columns receive the exact same
        // icon-and-label treatment without requiring screen-specific code.
        if (!Boolean.TRUE.equals(table.getProperties().get("erp-column-listener"))) {
            table.getProperties().put("erp-column-listener", true);
            table.getColumns().addListener((ListChangeListener<TableColumn>) change ->
                decorateColumns(table.getColumns())
            );
        }

        if (!table.getColumns().isEmpty()) {
            TableColumn first = (TableColumn) table.getColumns().getFirst();
            // decorateColumns() renders semantic headers as graphics and clears
            // TableColumn.text. Always inspect the preserved original label here;
            // otherwise every decorated first business column looks blank and can
            // be mistaken for a legacy selection/index column (for example the
            // Create Sale "Description" column).
            Object storedFirstLabel = first.getProperties().get("erp-header-label");
            String heading = storedFirstLabel instanceof String value
                ? value.trim()
                : first.getText() == null ? "" : first.getText().trim();
            String columnId = first.getId() == null ? "" : first.getId().toLowerCase(Locale.ROOT);
            // Register/list tables use a persistent leading checkbox rather than a passive sequence number.
            // Workflow-owned checkbox columns opt out via erp-keep-selection and keep their controller behavior.
            boolean keepSelection = Boolean.TRUE.equals(table.getProperties().get("erp-keep-selection"));
            boolean selectionColumn = !keepSelection && (heading.equals("#")
                    || heading.equals("No.")
                    || heading.equals("✓")
                    || heading.equalsIgnoreCase("select")
                    || columnId.contains("select")
                    || Boolean.TRUE.equals(first.getProperties().get("erp-row-number"))
                    || heading.isBlank());
            if (selectionColumn && !Boolean.TRUE.equals(first.getProperties().get("erp-global-checkbox"))) {
                first.getProperties().remove("erp-row-number");
                first.getProperties().put("erp-global-checkbox", true);
                first.setVisible(true);
                first.setText("");
                first.setSortable(false);
                first.setReorderable(false);
                first.setResizable(false);
                first.setMinWidth(54);
                first.setPrefWidth(54);
                first.setMaxWidth(54);
                table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                CheckBox all = new CheckBox();
                all.setTooltip(new Tooltip("Select all visible rows"));
                all.setOnAction(e -> {
                    if (all.isSelected()) table.getSelectionModel().selectAll();
                    else table.getSelectionModel().clearSelection();
                });
                first.setGraphic(all);
                first.setCellFactory(ignored -> new TableCell<Object, Object>() {
                    private final CheckBox box = new CheckBox();
                    {
                        box.setOnAction(e -> {
                            int index = getIndex();
                            if (index < 0 || index >= getTableView().getItems().size()) return;
                            if (box.isSelected()) getTableView().getSelectionModel().select(index);
                            else getTableView().getSelectionModel().clearSelection(index);
                            e.consume();
                        });
                    }
                    @Override
                    protected void updateItem(Object item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(null);
                        if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                            setGraphic(null);
                            return;
                        }
                        box.setSelected(getTableView().getSelectionModel().isSelected(getIndex()));
                        setGraphic(box);
                        setAlignment(Pos.CENTER);
                    }
                });
                table.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
                    all.setSelected(!table.getItems().isEmpty() && table.getSelectionModel().getSelectedIndices().size() == table.getItems().size());
                    table.refresh();
                });
            }
        }

        // Row context menus are owned by each controller. A global menu caused
        // duplicate/overlapping actions, especially on macOS.

    }


    /**
     * Shows the complete value when a normal text cell is visually clipped.
     * This keeps columns stable on hover and does not replace controller-owned
     * cell factories, graphics, editors, context menus, or business handlers.
     */
    @SuppressWarnings("rawtypes")
    private static void installCellValueTooltips(TableView table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-cell-tooltips"))) return;
        table.getProperties().put("erp-cell-tooltips", true);

        table.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            TableCell cell = findTableCell(event.getPickResult().getIntersectedNode());
            Object previous = table.getProperties().put("erp-hovered-table-cell", cell);
            if (previous instanceof TableCell previousCell && previousCell != cell) {
                clearManagedTooltip(previousCell);
            }
            if (cell == null || cell.isEmpty()) return;

            String value = cell.getText();
            if (value == null || value.isBlank()) {
                clearManagedTooltip(cell);
                return;
            }

            Text measurement = new Text(value);
            measurement.setFont(cell.getFont());
            double availableWidth = Math.max(0, cell.getWidth() - 18);
            boolean clipped = measurement.getLayoutBounds().getWidth() > availableWidth;

            if (clipped) {
                Tooltip tooltip = managedTooltip(cell);
                tooltip.setText(value);
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(460);
                cell.setTooltip(tooltip);
            } else {
                clearManagedTooltip(cell);
            }
        });

        table.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            Object previous = table.getProperties().remove("erp-hovered-table-cell");
            if (previous instanceof TableCell previousCell) {
                clearManagedTooltip(previousCell);
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private static TableCell findTableCell(Node node) {
        Node current = node;
        while (current != null && !(current instanceof TableCell)) {
            current = current.getParent();
        }
        return current instanceof TableCell cell ? cell : null;
    }

    private static Tooltip managedTooltip(TableCell<?, ?> cell) {
        Object existing = cell.getProperties().get("erp-managed-cell-tooltip");
        if (existing instanceof Tooltip tooltip) return tooltip;
        Tooltip tooltip = new Tooltip();
        cell.getProperties().put("erp-managed-cell-tooltip", tooltip);
        return tooltip;
    }

    private static void clearManagedTooltip(TableCell<?, ?> cell) {
        Object managed = cell.getProperties().get("erp-managed-cell-tooltip");
        if (managed != null && cell.getTooltip() == managed) {
            cell.setTooltip(null);
        }
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyTableProfile(TableView table) {
        if (Boolean.TRUE.equals(table.getProperties().get("erp-preserve-resize-policy"))) return;
        String profile = detectTableProfile(table);
        table.getProperties().put("erp-table-profile", profile);
        String profileClass = "erp-table-profile-" + profile;
        if (!table.getStyleClass().contains(profileClass)) table.getStyleClass().add(profileClass);

        switch (profile) {
            case "import" -> table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            case "permission", "summary", "responsive" ->
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            case "register", "master", "history", "administration", "line-item", "detail", "dialog" ->
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            default -> table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        }
    }

    private static String detectTableProfile(TableView<?> table) {
        // Phase 10 contract: an explicit FXML/programmatic profile is authoritative.
        // Heuristics are retained only as a compatibility fallback for legacy dynamic tables.
        for (String styleClass : table.getStyleClass()) {
            String normalized = styleClass == null ? "" : styleClass.toLowerCase(Locale.ROOT).trim();
            if (!normalized.startsWith("erp-table-profile-")) continue;
            String explicit = normalized.substring("erp-table-profile-".length());
            switch (explicit) {
                case "register", "master", "history", "administration", "line-item",
                     "detail", "dialog", "summary", "permission", "import", "responsive" -> {
                    return explicit;
                }
                default -> { }
            }
        }

        String styles = String.join(" ", table.getStyleClass()).toLowerCase(Locale.ROOT);
        String id = table.getId() == null ? "" : table.getId().toLowerCase(Locale.ROOT);
        String key = styles + " " + id;
        if (key.contains("import-preview")) return "import";
        if (key.contains("permission")) return "permission";
        if (key.contains("role-table") || key.contains("user-access") || key.contains("user-table")) return "administration";
        if (key.contains("report-table") || key.contains("dashboard") || key.contains("summary-table")) return "summary";
        if (key.contains("line-item") || key.contains("tablelines") || key.contains("entry-table")) return "line-item";
        if (key.contains("detail") || id.equals("items")) return "detail";
        if (key.contains("dialog-table") || key.contains("compact-table")) return "dialog";
        if (key.contains("history") || key.contains("communication") || key.contains("backup")
            || key.contains("reminder") || key.contains("update")) return "history";
        if (key.contains("entity") || key.contains("master") || key.contains("inventory")
            || key.contains("customer") || key.contains("supplier") || key.contains("item")) return "master";
        if (key.contains("register") || key.contains("sales") || key.contains("purchase")
            || key.contains("quotation") || key.contains("return") || key.contains("payment")
            || key.contains("operation")) return "register";
        return table.getColumns().size() >= 9 ? "register" : "responsive";
    }

    /** Recursively applies one shared renderer to leaf and grouped headers. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void decorateColumns(java.util.List<TableColumn> columns) {
        for (TableColumn column : columns) {
            if (!column.getColumns().isEmpty()) decorateColumns(column.getColumns());

            Object storedLabel = column.getProperties().get("erp-header-label");
            String heading = storedLabel instanceof String value ? value
                : column.getText() == null ? "" : column.getText().trim();
            String columnId = column.getId() == null ? "" : column.getId().trim();

            // A few workflow tables intentionally use an interactive header (for
            // example a Select-all CheckBox). Preserve those custom graphics, but
            // route every semantic header through the canonical IconFactory renderer.
            if (Boolean.TRUE.equals(column.getProperties().get("erp-header-preserve"))
                && !Boolean.TRUE.equals(column.getProperties().get("erp-header-explicit"))) {
                continue;
            }

            Object explicit = column.getProperties().get("erp-header-semantic");
            String semantic = Boolean.TRUE.equals(column.getProperties().get("erp-header-explicit"))
                && explicit instanceof String value && !value.isBlank() ? value : null;
            if (semantic == null) semantic = headerSemantic(heading, columnId);
            if (semantic == null && !heading.isBlank()) semantic = fallbackHeaderSemantic(heading, columnId);

            if (semantic != null) {
                String signature = heading + "|" + semantic;
                if (!signature.equals(column.getProperties().get("erp-header-signature")) || column.getGraphic() == null) {
                    column.getProperties().put("erp-header-signature", signature);
                    column.getProperties().put("erp-header-label", heading);
                    column.getProperties().put("erp-header-semantic", semantic);
                    column.setText("");
                    column.setGraphic(IconFactory.tableHeader(heading, semantic));
                    if (!column.getStyleClass().contains("erp-icon-table-column")) {
                        column.getStyleClass().add("erp-icon-table-column");
                    }
                }
                applyResponsiveWidth(column, heading, semantic);
            }

            // Do not replace factories installed by business controllers. This
            // renderer is only for ordinary string status columns.
            if (isStatusHeading(heading)
                && column.getCellFactory() == TableColumn.DEFAULT_CELL_FACTORY) {
                final String statusSemantic = semantic;
                column.setCellFactory(ignored -> new SemanticStatusCell(statusSemantic));
            }
        }
    }


    @SuppressWarnings("rawtypes")
    private static void applyResponsiveWidth(TableColumn column, String heading, String semantic) {
        String h = heading == null ? "" : heading.toLowerCase(Locale.ROOT);

        // Ordinary business columns keep the widths declared by their screen. This is
        // essential when a details drawer opens: JavaFX can shrink the data columns
        // naturally instead of being blocked by semantic minimums added after FXML load.
        if ("actions".equals(semantic)) {
            column.setMinWidth(156);
            column.setPrefWidth(160);
            column.setMaxWidth(168);
            column.setResizable(false);
            column.setSortable(false);
            return;
        }

        if (h.equals("no.") || h.equals("#")) {
            column.setMinWidth(62);
            column.setPrefWidth(62);
            column.setMaxWidth(72);
            column.setResizable(false);
        }
    }

    private static boolean isStatusHeading(String heading) {
        String value = heading.toLowerCase(Locale.ROOT);
        return value.contains("status") || value.contains("payment due")
            || value.equals("email") || value.contains("whatsapp");
    }

    /** Maps every common ERP table heading to a meaningful business icon. */
    private static String headerSemantic(String heading, String columnId) {
        String value = heading == null ? "" : heading.toLowerCase(Locale.ROOT)
            .replace("&amp;", "and").replace("&", "and").replaceAll("\\s+", " ").trim();
        String id = columnId == null ? "" : columnId.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ").trim();
        String key = (value + " " + id).trim();

        // Deterministic meanings for headings that are ambiguous under substring
        // matching. This keeps the same business vocabulary on every screen.
        String exactSemantic = switch (value) {
            case "access", "allowed", "capability", "what this allows" -> "permission";
            case "account" -> "bank";
            case "action", "actions" -> "actions";
            case "address", "branch", "department", "location" -> "location";
            case "balance", "opening balance" -> "balance";
            case "brand", "category", "type" -> "category";
            case "code", "customer code", "supplier code", "item code" -> "identity";
            case "contact person" -> "user";
            case "converted to", "document", "invoice", "invoice no.", "original invoice",
                 "purchase no.", "quotation no.", "return no.", "voucher no." -> "document";
            case "customer / supplier", "party", "received from" -> "customer";
            case "description", "description / narration", "details", "notes", "reason",
                 "remarks", "subject" -> "notes";
            case "email / username", "recipient" -> "email";
            case "follow up", "payment due", "reminder", "valid upto" -> "reminder";
            case "mfa" -> "security";
            case "mode" -> "category";
            case "payment mode" -> "payment";
            case "priority" -> "warning";
            case "reference", "reference / cheque no.", "reference no.", "target" -> "reference";
            case "refund status", "result", "result / error", "return status" -> "status";
            case "resend" -> "refresh";
            case "role", "role name" -> "role";
            case "source" -> "source";
            case "users" -> "user";
            case "value" -> "master";
            default -> null;
        };
        if (exactSemantic != null) return exactSemantic;

        if (value.equals("no.") || value.equals("no") || value.equals("sr.")
            || value.equals("sr. no.") || value.equals("row") || value.equals("#")
            || id.contains("serial") || id.contains("row number")) return "quantity";
        if ((value.isBlank() || value.equals("✓") || value.equals("select"))
            && !id.contains("select")) return null;

        if (key.contains("whatsapp") || key.contains("whats app")) return "whatsapp";
        if (key.contains("email") || key.contains("mail status")) return "email";
        if (key.contains("action") || key.contains("menu") || key.contains("option")) return "actions";
        if (key.contains("attachment") || key.contains("file attached")) return "attachment";
        if (key.contains("phone") || key.contains("mobile") || key.contains("contact number")) return "phone";

        if (key.contains("payment due") || key.contains("due date") || key.contains("follow up")
            || key.contains("reminder") || key.contains("valid upto") || key.contains("valid until")) return "reminder";
        if (key.contains("priority") || key.contains("severity")) return "warning";
        if (key.contains("status") || key.contains("state") || key.contains("result error")) return "status";
        if (key.contains("mfa")) return "security";
        if (key.contains("permission") || key.contains("access")) return "permission";
        if (key.contains("role")) return "role";

        if (key.contains("date") || key.contains("created on") || key.contains("created at")
            || key.contains("updated") || key.contains("last login") || key.contains("timestamp")
            || key.contains("time")) return "calendar";
        if (key.contains("supplier") || key.contains("vendor")) return "supplier";
        if (key.contains("customer supplier") || key.equals("party") || key.contains("party name")
            || key.contains("received from")) return "customer";
        if (key.equals("user") || key.contains("username") || key.contains("created by")
            || key.contains("updated by") || key.contains("employee") || key.contains("assignee")) return "user";
        if (key.contains("customer") || key.contains("sales person") || key.contains("salesperson")
            || key.contains("full name")) return "customer";
        if (key.contains("address") || key.contains("location") || key.contains("branch")
            || key.contains("department") || key.contains("city") || key.contains("state")) return "location";

        if (value.equals("paid") || key.contains(" paid ") || key.startsWith("paid ")) return "complete";
        if (key.contains("balance")) return "balance";
        if (key.contains("discount")) return "discount";
        if (key.equals("unit") || key.contains(" uom") || key.startsWith("uom ")) return "unit";
        if (key.contains("gst") || key.contains("tax") || key.contains("vat") || key.contains("taxable")) return "tax";
        if (key.contains("credit")) return "credit";
        if (key.contains("debit")) return "debit";
        if (key.contains("pan") || key.contains("hsn") || key.contains("sku") || key.contains("barcode")
            || key.endsWith(" code") || key.equals("code") || key.contains(" id")) return "identity";

        if (key.contains("invoice") || key.contains("quotation") || key.contains("voucher")
            || key.contains("reference") || key.contains("document") || key.contains("converted to")
            || key.contains("order no") || key.contains("bill no") || key.endsWith(" no")
            || key.endsWith(" no.")) return "document";
        if (key.contains("return") || key.contains("refund")) return "return";
        if (key.contains("backup")) return "backup";
        if (key.contains("source")) return "source";
        if (key.contains("channel")) return "communication";
        if (key.contains("payment mode")) return "payment";
        if (key.contains("mode")) return "category";

        if (key.contains("item") || key.contains("product") || key.contains("material")) return "item";
        if (key.contains("qty") || key.contains("quantity") || key.contains("stock")
            || key.contains("unit") || key.contains("available") || key.contains("reserved")
            || key.contains("size") || key.contains("in stock")) return "quantity";
        if (key.equals("type") || key.contains("movement type") || key.contains("transaction type")) return "category";
        if (key.contains("category") || key.contains("brand")) return "category";

        if (key.contains("amount") || key.contains("rate") || key.contains("price")
            || key.contains("total") || key.contains("opening balance") || key.contains("allocate")
            || key.contains("receivable") || key.contains("payable")) return "currency";
        if (key.contains("reason") || key.contains("note") || key.contains("remark")
            || key.contains("description") || key.contains("subject")) return "notes";
        if (key.equals("value")) return "master";
        if (key.equals("user") || key.contains("created by") || key.contains("updated by")) return "user";

        // Unknown headings should not all receive the same document icon.
        return null;
    }


    /** Ensures every visible table heading receives a stable, colourful semantic icon. */
    private static String fallbackHeaderSemantic(String heading, String columnId) {
        String key = ((heading == null ? "" : heading) + " " + (columnId == null ? "" : columnId))
            .toLowerCase(Locale.ROOT);
        if (key.contains("version")) return "update";
        if (key.contains("path") || key.contains("folder")) return "folder";
        if (key.contains("host") || key.contains("port") || key.contains("provider")) return "settings";
        if (key.contains("module") || key.contains("feature")) return "master";
        if (key.contains("read") || key.contains("create") || key.contains("update") || key.contains("delete")) return "permission";
        if (key.contains("frequency") || key.contains("schedule")) return "calendar";
        if (key.contains("method") || key.contains("mode")) return "category";
        if (key.contains("code") || key.contains("identifier")) return "identity";
        if (key.contains("description") || key.contains("reason") || key.contains("remark")) return "notes";
        if (key.contains("value")) return "master";
        if (key.contains("available") || key.contains("invoiced") || key.contains("quantity")) return "quantity";
        if (key.contains("returned") || key.contains("return qty")) return "return";
        if (key.contains("refund")) return "payment";
        if (key.contains("selected") || key.contains("select")) return "select";
        if (key.contains("account")) return "bank";
        if (key.contains("allowed") || key.contains("capability") || key.contains("what this allows")) return "permission";
        if (key.contains("compatibility")) return "compatibility";
        if (key.contains("database")) return "database";
        if (key.contains("installer")) return "installer";
        if (key.contains("match") || key.contains("link")) return "link";
        if (key.contains("receipt")) return "document";
        if (key.contains("recipient")) return "email";
        if (key.contains("resend")) return "refresh";
        if (key.equals("result") || key.contains("result ")) return "status";
        if (key.contains("role")) return "role";
        if (key.contains("target")) return "reference";
        if (key.contains("name") || key.contains("title")) return "master";
        return null;
    }

    /** Default icon-plus-label renderer for status columns without custom logic. */
    private static final class SemanticStatusCell extends TableCell<Object, Object> {
        private final String columnSemantic;

        private SemanticStatusCell(String columnSemantic) {
            this.columnSemantic = columnSemantic;
        }

        @Override protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("status-positive", "status-warning", "status-negative", "status-neutral");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String value = String.valueOf(item).trim();
            String state = state(value);
            String semantic;
            if ("email".equals(columnSemantic)) semantic = "email";
            else if ("whatsapp".equals(columnSemantic)) semantic = "whatsapp";
            else if ("reminder".equals(columnSemantic)) semantic = "reminder";
            else if ("document".equals(columnSemantic) || "status".equals(columnSemantic)) {
                semantic = state.equals("positive") ? "complete"
                    : state.equals("negative") ? "error"
                    : state.equals("warning") ? "status" : "document";
            } else {
                semantic = state.equals("positive") ? "complete"
                    : state.equals("negative") ? "error"
                    : columnSemantic == null ? "warning" : columnSemantic;
            }
            Label label = new Label(value);
            String colour = state.equals("positive") ? "#16a34a"
                : state.equals("negative") ? "#dc2626"
                : state.equals("warning") ? "#d97706" : "#2563eb";
            HBox content = new HBox(6, IconFactory.compactIcon(semantic, 15), label);
            content.setAlignment(Pos.CENTER_LEFT);
            content.getStyleClass().add("erp-status-content");
            setText(null);
            setGraphic(content);
            getStyleClass().add("status-" + state);
        }

        private static String state(String text) {
            String value = text.toLowerCase(Locale.ROOT);
            if (value.contains("not sent") || value.contains("failed") || value.contains("error")
                || value.contains("overdue") || value.contains("rejected") || value.contains("cancel")
                || value.contains("out of stock")) return "negative";
            if (value.contains("paid") || value.contains("complete") || value.contains("approved")
                || value.contains("refunded") || value.equals("sent") || value.contains("received")
                || value.contains("delivered") || value.contains("active") || value.contains("success")) return "positive";
            if (value.contains("pending") || value.contains("partial") || value.contains("due")
                || value.contains("open") || value.contains("draft") || value.contains("not set")) return "warning";
            return "neutral";
        }
    }

    /**
     * Reuses the screen's own business handler instead of duplicating CRUD logic.
     * The closest visible button whose label matches the requested action is fired.
     */
    private static void fireNamedAction(TableView<?> table, String... names) {
        Node root = table.getScene() == null ? table : table.getScene().getRoot();
        ButtonBase action = findAction(root, names);
        if (action != null && !action.isDisabled()) action.fire();
    }

    private static ButtonBase findAction(Node node, String... names) {
        if (node instanceof ButtonBase button && button.isVisible()) {
            String label = button.getText() == null ? "" : button.getText().toLowerCase();
            for (String name : names) if (label.contains(name)) return button;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ButtonBase found = findAction(child, names);
                if (found != null) return found;
            }
        }
        return null;
    }

}
