package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.ModernDialog;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.config.ConfigManager;
import org.example.api.insights.InsightsApiClient;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.util.IconFactory;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Database-backed reminder inbox with CRUD, completion and snooze workflows. */
public class ReminderCenterController {
    private final InsightsApiClient insightsApi = new InsightsApiClient();

    @FXML private Label lblOpen, lblOverdue, lblDueToday, lblUpcoming;
    @FXML private Label lblResultCount;
    @FXML private StackPane reminderPageIcon;
    @FXML private Label lblDetailTitle, lblDetailReference, lblDetailDue,
            lblDetailPriority, lblDetailStatus, lblDetailNotes;
    @FXML private StackPane openMetricIcon, overdueMetricIcon,
            todayMetricIcon, upcomingMetricIcon, emptyStateIcon;
    @FXML private MenuButton detailMoreActions;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus, cmbPriority, cmbPeriod;
    @FXML private TableView<ReminderRow> table;
    @FXML private SplitPane reminderWorkspace;
    @FXML private VBox reminderDetailPanel;
    @FXML private TableColumn<ReminderRow, String> colTitle, colReference,
            colDue, colPriority, colStatus, colCreatedBy;
    @FXML private TableColumn<ReminderRow, Void> colActions;

    private final ObservableList<ReminderRow> source = FXCollections.observableArrayList();
    private FilteredList<ReminderRow> filtered;
    private ReminderRow detailRow;

    @FXML
    public void initialize() {
        if (reminderPageIcon != null) reminderPageIcon.getChildren().setAll(IconFactory.icon("reminder", 24));
        configureExplicitTableHeaderIcons();
        configureFilters();
        configureColumns();
        configureTable();
        configureListeners();
        configureVisualIcons();
        configureDetailActionMenu();
        refresh();
    }

    private void configureFilters() {
        cmbStatus.getItems().setAll("All Statuses", "OPEN", "COMPLETED", "SNOOZED");
        cmbPriority.getItems().setAll("All Priorities", "LOW", "NORMAL", "HIGH", "URGENT");
        cmbPeriod.getItems().setAll("All Dates", "Overdue", "Due Today", "Next 7 Days", "Next 30 Days");

        cmbStatus.setValue("All Statuses");
        cmbPriority.setValue("All Priorities");
        cmbPeriod.setValue("All Dates");
    }

    private void configureColumns() {
        colTitle.setCellValueFactory(value -> value.getValue().title);
        colReference.setCellValueFactory(value -> value.getValue().reference);
        colDue.setCellValueFactory(value -> value.getValue().due);
        colPriority.setCellValueFactory(value -> value.getValue().priority);
        colStatus.setCellValueFactory(value -> value.getValue().status);
        colCreatedBy.setCellValueFactory(value -> value.getValue().createdBy);

        colDue.setCellFactory(column -> dueDateCell());
        colPriority.setCellFactory(column -> badgeCell("reminder-priority-badge", "priority-"));
        colStatus.setCellFactory(column -> badgeCell("reminder-status-badge", "status-"));
        colActions.setCellFactory(column -> actionCell());
    }

    private void configureTable() {

        filtered = new FilteredList<>(source, row -> true);
        table.setItems(filtered);

        table.setRowFactory(tableView -> {
            TableRow<ReminderRow> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (row.isEmpty() || event.getButton() != MouseButton.PRIMARY || interactiveTableTarget(event.getPickResult().getIntersectedNode(), row)) return;
                if (event.getClickCount() == 1) {
                    ReminderRow clicked = row.getItem();
                    if (reminderDetailPanel.isVisible() && detailRow == clicked) closeDetails();
                    else { table.getSelectionModel().select(clicked); showDetails(clicked); }
                    event.consume();
                } else if (event.getClickCount() == 2) {
                    edit(row.getItem());
                    event.consume();
                }
            });

            ContextMenu contextMenu = buildRowContextMenu(row);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings
                            .when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            return row;
        });
    }

    private boolean interactiveTableTarget(Node target, TableRow<?> row) { for (Node node=target; node!=null && node!=row; node=node.getParent()) if (node instanceof ButtonBase || node instanceof TextInputControl || node instanceof ComboBoxBase<?>) return true; return false; }

    private void configureListeners() {
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        cmbStatus.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        cmbPriority.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        cmbPeriod.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void configureVisualIcons() {
        installIcon(openMetricIcon, "reminder", 18, "reminder-metric-glyph");
        installIcon(overdueMetricIcon, "warning", 18, "reminder-metric-glyph");
        installIcon(todayMetricIcon, "calendar", 18, "reminder-metric-glyph");
        installIcon(upcomingMetricIcon, "calendar", 18, "reminder-metric-glyph");
        installIcon(emptyStateIcon, "reminder", 28, "reminder-empty-glyph");

        detailMoreActions.setText("Actions");
        detailMoreActions.setGraphic(IconFactory.compactIcon("actions", 15));
        detailMoreActions.setContentDisplay(ContentDisplay.LEFT);
        detailMoreActions.setGraphicTextGap(6);
        detailMoreActions.setFocusTraversable(false);
    }

    private void installIcon(
            StackPane host,
            String semantic,
            double size,
            String styleClass
    ) {
        if (host == null) {
            return;
        }

        Node icon = IconFactory.compactIcon(semantic, size);
        icon.getStyleClass().add(styleClass);
        host.getChildren().setAll(icon);
    }

    private void configureDetailActionMenu() {
        detailMoreActions.setOnShowing(event -> rebuildDetailActionMenu());
        rebuildDetailActionMenu();
    }

    private void rebuildDetailActionMenu() {
        ReminderRow row = table.getSelectionModel().getSelectedItem();

        MenuItem snooze = menuItem("Snooze", "snooze", event -> snooze(row));
        MenuItem reopen = menuItem("Reopen", "reopen", event -> reopen(row));
        MenuItem delete = menuItem("Delete Reminder", "delete", event -> delete(row));
        delete.getStyleClass().add("reminder-danger-menu-item");

        boolean completed = row != null && "COMPLETED".equals(row.status.get());
        snooze.setDisable(row == null || completed);
        reopen.setDisable(row == null || !completed);
        delete.setDisable(row == null);

        detailMoreActions.getItems().setAll(
                snooze,
                reopen,
                new SeparatorMenuItem(),
                delete
        );
        detailMoreActions.setDisable(row == null);
    }

    private ContextMenu buildRowContextMenu(TableRow<ReminderRow> row) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("reminder-action-menu");

        MenuItem edit = menuItem("Edit Reminder", "edit", event -> edit(row.getItem()));
        MenuItem complete = menuItem("Mark Complete", "complete", event -> complete(row.getItem()));
        MenuItem snooze = menuItem("Snooze", "snooze", event -> snooze(row.getItem()));
        MenuItem reopen = menuItem("Reopen", "reopen", event -> reopen(row.getItem()));
        MenuItem delete = menuItem("Delete", "delete", event -> delete(row.getItem()));
        delete.getStyleClass().add("reminder-danger-menu-item");

        menu.setOnShowing(event -> updateActionAvailability(
                row.getItem(), complete, snooze, reopen, delete
        ));

        menu.getItems().addAll(
                edit,
                complete,
                snooze,
                reopen,
                new SeparatorMenuItem(),
                delete
        );
        IconFactory.decorateActionMenu(menu);
        return menu;
    }

    @FXML
    private void refresh() {
        ReminderRow selected = table.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? -1L : selected.id;

        source.clear();
        try { for (var d : insightsApi.reminders()) source.add(new ReminderRow(d)); }
        catch (Exception exception) { error("Reminders could not be loaded", exception); }
        updateMetrics();
        applyFilters();

        ReminderRow restored = selectedId < 0 ? null : filtered.stream()
                .filter(row -> row.id == selectedId)
                .findFirst()
                .orElse(null);
        if (restored != null) {
            table.getSelectionModel().select(restored);
            if (detailRow != null) showDetails(restored);
        } else {
            table.getSelectionModel().clearSelection();
            showDetails(null);
        }
    }

    @FXML
    private void clearFilters() {
        txtSearch.clear();
        cmbStatus.setValue("All Statuses");
        cmbPriority.setValue("All Priorities");
        cmbPeriod.setValue("All Dates");
        applyFilters();
    }

    @FXML private void addReminder() { openEditor(null); }
    @FXML private void editSelected() { edit(table.getSelectionModel().getSelectedItem()); }
    @FXML private void completeSelected() { complete(table.getSelectionModel().getSelectedItem()); }
    @FXML private void snoozeSelected() { snooze(table.getSelectionModel().getSelectedItem()); }

    private void edit(ReminderRow row) {
        if (row != null) {
            openEditor(row);
        }
    }

    private void openEditor(ReminderRow row) {
        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle(row == null ? "Add Reminder" : "Edit Reminder");
        dialog.setHeaderText(row == null ? "Create a business follow-up" : "Update reminder details");
        dialog.getDialogPane().getStyleClass().add("reminder-editor-dialog");

        TextField title = new TextField(row == null ? "" : row.title.get());
        title.setPromptText("e.g. Follow up outstanding payment");
        TextField reference = new TextField(row == null ? "" : rawReference(row.reference.get()));
        reference.setPromptText("Invoice, quotation, PO or other reference");
        DatePicker due = new DatePicker(row == null ? BusinessClock.today() : parse(row.due.get(), BusinessClock.today()));
        ComboBox<String> priority = new ComboBox<>();
        priority.getItems().setAll("LOW", "NORMAL", "HIGH", "URGENT");
        priority.setValue(row == null ? "NORMAL" : row.priority.get());
        TextArea notes = new TextArea(row == null ? "" : row.notes);
        notes.setPromptText("Notes, contact details or next action...");
        notes.setPrefRowCount(4);
        notes.setWrapText(true);

        for (Control control : new Control[]{title, reference, due, priority, notes}) {
            control.getStyleClass().add("reminder-editor-input");
            control.setMaxWidth(Double.MAX_VALUE);
        }

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(11);
        grid.getStyleClass().add("reminder-editor-grid");
        javafx.scene.layout.ColumnConstraints labels = new javafx.scene.layout.ColumnConstraints();
        labels.setMinWidth(100);
        labels.setPrefWidth(110);
        javafx.scene.layout.ColumnConstraints fields = new javafx.scene.layout.ColumnConstraints();
        fields.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labels, fields);
        grid.addRow(0, editorLabel("Title *"), title);
        grid.addRow(1, editorLabel("Reference"), reference);
        grid.addRow(2, editorLabel("Due Date *"), due);
        grid.addRow(3, editorLabel("Priority"), priority);
        grid.addRow(4, editorLabel("Notes"), notes);

        VBox content = new VBox(10,
                new Label(row == null
                        ? "Add a reminder and keep the business follow-up visible in one place."
                        : "Update the selected reminder without changing its history or status."),
                grid);
        content.getStyleClass().add("reminder-editor-content");
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);

        ButtonType save = new ButtonType(row == null ? "Add Reminder" : "Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.showAndWait().filter(save::equals).ifPresent(button -> {
            if (title.getText() == null || title.getText().isBlank() || due.getValue() == null) {
                warning("Title and due date are required.");
                return;
            }
            try {
                var dto = new InsightsApiClient.ReminderDto(
                        row == null ? null : row.id,
                        title.getText().trim(),
                        reference.getText() == null ? "" : reference.getText().trim(),
                        due.getValue().toString(),
                        priority.getValue(),
                        notes.getText(),
                        row == null ? "OPEN" : row.status.get(),
                        currentUser(),
                        null
                );
                if (row == null) insightsApi.saveReminder(dto); else insightsApi.updateReminder(dto);
                NotificationService.add((row == null ? "Reminder created: " : "Reminder updated: ") + title.getText().trim());
                refresh();
                information(row == null ? "Reminder created successfully." : "Reminder updated successfully.");
            } catch (Exception exception) {
                error("Reminder could not be saved", exception);
            }
        });
    }

    private Label editorLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("reminder-editor-label");
        return label;
    }

    private static String rawReference(String reference) {
        return "No reference".equalsIgnoreCase(blank(reference, "")) ? "" : blank(reference, "");
    }

    private void complete(ReminderRow row) {
        if (row == null || !confirm("Mark reminder '" + row.title.get() + "' as complete?")) return;
        changeStatus(row, "COMPLETED");
    }

    private void reopen(ReminderRow row) {
        if (row == null || !confirm("Reopen reminder '" + row.title.get() + "'?")) return;
        changeStatus(row, "OPEN");
    }

    private void changeStatus(ReminderRow row, String status) {
        if (row == null) return;
        try {
            insightsApi.reminderStatus(row.id, status, null);
            NotificationService.add("Reminder " + row.title.get() + " marked " + status.toLowerCase(Locale.ROOT) + ".");
            refresh();
            information("COMPLETED".equals(status) ? "Reminder marked complete." : "Reminder reopened successfully.");
        } catch (Exception exception) { error("Reminder status could not be changed", exception); }
    }

    private void snooze(ReminderRow row) {
        if (row == null) return;
        DatePicker picker = new DatePicker(BusinessClock.today().plusDays(1));
        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle("Snooze Reminder"); dialog.setHeaderText(row.title.get());
        dialog.getDialogPane().setContent(new javafx.scene.layout.VBox(8, new Label("Snooze until"), picker));
        ButtonType save = new ButtonType("Snooze", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.showAndWait().filter(save::equals).ifPresent(button -> {
            if (picker.getValue() == null || picker.getValue().isBefore(BusinessClock.today())) {
                warning("Select today or a future date for snooze.");
                return;
            }
            try {
                insightsApi.reminderStatus(row.id, "SNOOZED", picker.getValue().toString());
                NotificationService.add("Reminder snoozed until " + BusinessClock.formatDate(picker.getValue()) + ": " + row.title.get());
                refresh();
                information("Reminder snoozed until " + BusinessClock.formatDate(picker.getValue()) + ".");
            } catch (Exception exception) { error("Reminder could not be snoozed", exception); }
        });
    }

    private void delete(ReminderRow row) {
        if (row == null) return;
        String detail = "Delete reminder '" + row.title.get() + "'?\n\n"
                + "Reference: " + row.reference.get() + "\n"
                + "Due date: " + row.due.get() + "\n\nThis action cannot be undone.";
        if (!confirm(detail)) return;
        try { insightsApi.deleteReminder(row.id); refresh(); information("Reminder deleted successfully."); }
        catch (Exception exception) { error("Reminder could not be deleted", exception); }
    }

    private TableCell<ReminderRow, Void> actionCell() {
        return new TableCell<>() {
            private final MenuButton actions = new MenuButton();
            private ReminderRow currentRow;

            {
                actions.setFocusTraversable(false);
                actions.setTooltip(new Tooltip("Open reminder actions"));
                actions.getProperties().put("erp.icon.skip", true);
                actions.setAccessibleText("Reminder actions");
                actions.setText("Actions");
                actions.setGraphic(IconFactory.compactIcon("actions",15));
                actions.setContentDisplay(ContentDisplay.LEFT);
                actions.setGraphicTextGap(6);
                actions.getStyleClass().addAll("reminder-action-button", "table-action-menu");
                setAlignment(Pos.CENTER);

                actions.setOnShowing(event -> rebuildActionMenu());
                IconFactory.decorateActionMenu(actions);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    currentRow = null;
                    actions.hide();
                    actions.getItems().clear();
                    setGraphic(null);
                    return;
                }

                currentRow = getTableView().getItems().get(getIndex());
                rebuildActionMenu();
                setGraphic(actions);
            }

            private void rebuildActionMenu() {
                ReminderRow row = currentRow;
                if (row == null) {
                    actions.getItems().clear();
                    return;
                }

                MenuItem edit = menuItem("Edit", "edit", event -> edit(row));
                MenuItem complete = menuItem("Mark Complete", "complete", event -> complete(row));
                MenuItem snooze = menuItem("Snooze", "snooze", event -> snooze(row));
                MenuItem reopen = menuItem("Reopen", "reopen", event -> reopen(row));
                MenuItem delete = menuItem("Delete", "delete", event -> delete(row));
                delete.getStyleClass().add("reminder-danger-menu-item");

                updateActionAvailability(row, complete, snooze, reopen, delete);

                actions.getItems().setAll(
                        edit,
                        complete,
                        snooze,
                        reopen,
                        new SeparatorMenuItem(),
                        delete
                );
            }
        };
    }

    private void updateActionAvailability(
            ReminderRow row,
            MenuItem complete,
            MenuItem snooze,
            MenuItem reopen,
            MenuItem delete
    ) {
        boolean completed = row != null && "COMPLETED".equals(row.status.get());
        complete.setDisable(row == null || completed);
        snooze.setDisable(row == null || completed);
        reopen.setDisable(row == null || !completed);
        delete.setDisable(row == null);
    }

    private TableCell<ReminderRow, String> dueDateCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll(
                        "reminder-due-overdue",
                        "reminder-due-today",
                        "reminder-due-future"
                );

                if (empty || value == null || value.isBlank()) {
                    setText(null);
                    return;
                }

                LocalDate date = parse(value, null);
                setText(formatDate(value));

                if (date == null) {
                    return;
                }

                ReminderRow row = getTableRow() == null ? null : getTableRow().getItem();
                boolean completed = row != null && "COMPLETED".equals(row.status.get());

                if (!completed && date.isBefore(BusinessClock.today())) {
                    getStyleClass().add("reminder-due-overdue");
                } else if (!completed && date.equals(BusinessClock.today())) {
                    getStyleClass().add("reminder-due-today");
                } else {
                    getStyleClass().add("reminder-due-future");
                }
            }
        };
    }

    private TableCell<ReminderRow, String> badgeCell(String baseClass, String variantPrefix) {
        return new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);

                if (empty || value == null || value.isBlank()) {
                    setGraphic(null);
                    return;
                }

                badge.setText(toDisplayText(value));
                badge.getStyleClass().setAll(
                        baseClass,
                        variantPrefix + value.toLowerCase(Locale.ROOT)
                );
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(badge);
            }
        };
    }

    private MenuItem menuItem(
            String text,
            String icon,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler
    ) {
        MenuItem item = new MenuItem(text, IconFactory.compactIcon(icon, 16));
        item.getStyleClass().add("reminder-menu-"+icon.toLowerCase(Locale.ROOT));
        item.setOnAction(handler);
        return item;
    }

    private void applyFilters() {
        if (filtered == null) {
            return;
        }

        String search = txtSearch.getText() == null
                ? ""
                : txtSearch.getText().trim().toLowerCase(Locale.ROOT);

        filtered.setPredicate(row -> {
            boolean matchesText = search.isEmpty()
                    || (row.title.get() + " " + row.reference.get() + " " + row.notes)
                    .toLowerCase(Locale.ROOT)
                    .contains(search);

            boolean matchesStatus = cmbStatus.getValue() == null
                    || cmbStatus.getValue().startsWith("All")
                    || row.status.get().equals(cmbStatus.getValue());

            boolean matchesPriority = cmbPriority.getValue() == null
                    || cmbPriority.getValue().startsWith("All")
                    || row.priority.get().equals(cmbPriority.getValue());

            LocalDate due = parse(row.due.get(), LocalDate.MAX);
            LocalDate today = BusinessClock.today();
            String period = cmbPeriod.getValue();

            boolean matchesDate = period == null
                    || period.equals("All Dates")
                    || (period.equals("Overdue")
                    && due.isBefore(today)
                    && !row.status.get().equals("COMPLETED"))
                    || (period.equals("Due Today") && due.equals(today))
                    || (period.equals("Next 7 Days")
                    && !due.isBefore(today)
                    && !due.isAfter(today.plusDays(7)))
                    || (period.equals("Next 30 Days")
                    && !due.isBefore(today)
                    && !due.isAfter(today.plusDays(30)));

            return matchesText && matchesStatus && matchesPriority && matchesDate;
        });

        updateResultCount();
    }

    private void updateResultCount() {
        int count = filtered == null ? 0 : filtered.size();
        lblResultCount.setText("Showing " + count + " Record" + (count == 1 ? "" : "s"));
    }

    private void updateMetrics() {
        LocalDate today = BusinessClock.today();

        long open = source.stream()
                .filter(row -> !row.status.get().equals("COMPLETED"))
                .count();

        long overdue = source.stream()
                .filter(row -> !row.status.get().equals("COMPLETED")
                        && parse(row.due.get(), LocalDate.MAX).isBefore(today))
                .count();

        long dueToday = source.stream()
                .filter(row -> !row.status.get().equals("COMPLETED")
                        && parse(row.due.get(), LocalDate.MAX).equals(today))
                .count();

        long upcoming = source.stream()
                .filter(row -> {
                    LocalDate date = parse(row.due.get(), LocalDate.MAX);
                    return !row.status.get().equals("COMPLETED")
                            && date.isAfter(today)
                            && !date.isAfter(today.plusDays(7));
                })
                .count();

        lblOpen.setText(String.valueOf(open));
        lblOverdue.setText(String.valueOf(overdue));
        lblDueToday.setText(String.valueOf(dueToday));
        lblUpcoming.setText(String.valueOf(upcoming));
    }

    private void showDetails(ReminderRow row) {
        detailRow = row;
        clearDetailBadgeClasses();

        if (row == null) {
            setDetailVisible(false);
            lblDetailTitle.setText("Select a reminder");
            lblDetailReference.setText("—");
            lblDetailDue.setText("—");
            lblDetailPriority.setText("—");
            lblDetailStatus.setText("—");
            lblDetailNotes.setText("Select a row to review its notes and available actions.");
            if (detailMoreActions != null) rebuildDetailActionMenu();
            return;
        }

        setDetailVisible(true);
        if (detailMoreActions != null) rebuildDetailActionMenu();

        lblDetailTitle.setText(row.title.get());
        lblDetailReference.setText(blank(row.reference.get(), "No reference"));
        lblDetailDue.setText(formatDate(row.due.get()));
        lblDetailPriority.setText(toDisplayText(row.priority.get()));
        lblDetailStatus.setText(toDisplayText(row.status.get()));
        lblDetailNotes.setText(blank(row.notes, "No notes"));

        lblDetailPriority.getStyleClass().add(
                "priority-" + row.priority.get().toLowerCase(Locale.ROOT)
        );
        lblDetailStatus.getStyleClass().add(
                "status-" + row.status.get().toLowerCase(Locale.ROOT)
        );
    }

    @FXML
    private void closeDetails() {
        table.getSelectionModel().clearSelection();
        showDetails(null);
    }

    private void setDetailVisible(boolean visible) {
        if (reminderDetailPanel == null) return;
        reminderDetailPanel.setManaged(visible);
        reminderDetailPanel.setVisible(visible);
        if (reminderWorkspace != null) {
            reminderWorkspace.setDividerPositions(visible ? 0.74 : 1.0);
        }
    }

    private void clearDetailBadgeClasses() {
        lblDetailPriority.getStyleClass().removeIf(style -> style.startsWith("priority-"));
        lblDetailStatus.getStyleClass().removeIf(style -> style.startsWith("status-"));
    }

    private static String formatDate(String value) {
        LocalDate date = parse(value, null);
        return date == null ? blank(value, "—") : BusinessClock.formatDate(date);
    }

    private static String toDisplayText(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }

        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static LocalDate parse(String value, LocalDate fallback) {
        try {
            return value == null ? fallback : LocalDate.parse(value);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String currentUser() {
        return SessionService.current() == null
                ? "System"
                : SessionService.current().getUsername();
    }

    private boolean confirm(String message) {
        return new OwnedAlert(
                Alert.AlertType.CONFIRMATION,
                message,
                ButtonType.YES,
                ButtonType.NO
        ).showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void warning(String message) {
        new OwnedAlert(Alert.AlertType.WARNING, message).showAndWait();
    }

    private void information(String message) {
        org.example.util.ToastManager.success(table, "Completed", message);
    }

    private void error(String title, Exception exception) {
        exception.printStackTrace();
        String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The request could not be completed."
                : exception.getMessage().trim();
        ModernDialog.error(table, "Reminder Center", title, detail);
    }

    public static final class ReminderRow {
        final long id;
        final SimpleStringProperty title;
        final SimpleStringProperty reference;
        final SimpleStringProperty due;
        final SimpleStringProperty priority;
        final SimpleStringProperty status;
        final SimpleStringProperty createdBy;
        final String notes;

        ReminderRow(InsightsApiClient.ReminderDto d) {
            id=d.id()==null?0L:d.id();
            title=new SimpleStringProperty(blank(d.title(),""));
            reference=new SimpleStringProperty(cleanReference(d.referenceNo()));
            due=new SimpleStringProperty(blank(d.dueDate(),""));
            priority=new SimpleStringProperty(blank(d.priority(),"NORMAL").toUpperCase(Locale.ROOT));
            status=new SimpleStringProperty(blank(d.status(),"OPEN").toUpperCase(Locale.ROOT));
            createdBy=new SimpleStringProperty(blank(d.createdBy(),"System"));
            notes=blank(d.notes(),"");
        }
    }

    private static String cleanReference(String reference){
        String value = reference == null ? "" : reference.trim();
        return value.isBlank() || value.equalsIgnoreCase("null") || value.equals("—") ? "No reference" : value;
    }

    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colTitle, "reminder");
        IconFactory.applyTableHeaderIcon(colReference, "reference");
        IconFactory.applyTableHeaderIcon(colDue, "calendar");
        IconFactory.applyTableHeaderIcon(colPriority, "warning");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colCreatedBy, "user");
        colActions.setText("Actions");
        IconFactory.applyTableHeaderIcon(colActions, "actions");
    }
}
