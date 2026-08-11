package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;

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
import org.example.database.DatabaseManager;
import org.example.config.ConfigManager;
import org.example.api.insights.InsightsApiClient;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.util.IconFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Database-backed reminder inbox with CRUD, completion and snooze workflows. */
public class ReminderCenterController {
    private final InsightsApiClient insightsApi = new InsightsApiClient();
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

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
    @FXML private TableColumn<ReminderRow, String> colTitle, colReference,
            colDue, colPriority, colStatus, colCreatedBy;
    @FXML private TableColumn<ReminderRow, Void> colActions;

    private final ObservableList<ReminderRow> source = FXCollections.observableArrayList();
    private FilteredList<ReminderRow> filtered;

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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        filtered = new FilteredList<>(source, row -> true);
        table.setItems(filtered);

        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldRow, newRow) -> showDetails(newRow)
        );

        table.setRowFactory(tableView -> {
            TableRow<ReminderRow> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()
                        && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    edit(row.getItem());
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

        detailMoreActions.setGraphic(IconFactory.compactIcon("more", 15));
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

        MenuItem snooze = menuItem("Snooze", "notification", event -> snooze(row));
        MenuItem reopen = menuItem("Reopen", "refresh", event -> reopen(row));
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
        MenuItem snooze = menuItem("Snooze", "notification", event -> snooze(row.getItem()));
        MenuItem reopen = menuItem("Reopen", "refresh", event -> reopen(row.getItem()));
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
        return menu;
    }

    @FXML
    private void refresh() {
        source.clear();
        if (ConfigManager.isApiDataEnabled()) {
            try { for (var d : insightsApi.reminders()) source.add(new ReminderRow(d)); }
            catch (Exception exception) { error("Reminders could not be loaded", exception); }
            updateMetrics(); applyFilters(); if(!filtered.isEmpty()) table.getSelectionModel().selectFirst(); else showDetails(null); return;
        }

        String sql = "SELECT * FROM reminder_register "
                + "ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'SNOOZED' THEN 1 ELSE 2 END, "
                + "due_date, id DESC";

        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                source.add(new ReminderRow(resultSet));
            }
        } catch (Exception exception) {
            error("Reminders could not be loaded", exception);
        }

        updateMetrics();
        applyFilters();

        if (!filtered.isEmpty()) {
            table.getSelectionModel().selectFirst();
        } else {
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
        dialog.setHeaderText(row == null
                ? "Create a business follow-up"
                : "Update reminder details");

        TextField title = new TextField(row == null ? "" : row.title.get());
        TextField reference = new TextField(row == null ? "" : row.reference.get());
        DatePicker due = new DatePicker(
                row == null ? LocalDate.now() : parse(row.due.get(), LocalDate.now())
        );

        ComboBox<String> priority = new ComboBox<>();
        priority.getItems().setAll("LOW", "NORMAL", "HIGH", "URGENT");
        priority.setValue(row == null ? "NORMAL" : row.priority.get());

        TextArea notes = new TextArea(row == null ? "" : row.notes);
        notes.setPrefRowCount(4);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.addRow(0, new Label("Title *"), title);
        grid.addRow(1, new Label("Reference"), reference);
        grid.addRow(2, new Label("Due Date *"), due);
        grid.addRow(3, new Label("Priority"), priority);
        grid.addRow(4, new Label("Notes"), notes);

        dialog.getDialogPane().setContent(grid);

        ButtonType save = new ButtonType("Save Reminder", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        dialog.showAndWait().filter(save::equals).ifPresent(button -> {
            if (title.getText().isBlank() || due.getValue() == null) {
                warning("Title and due date are required.");
                return;
            }

            if (ConfigManager.isApiDataEnabled()) {
                try {
                    var dto=new InsightsApiClient.ReminderDto(row==null?null:row.id,title.getText().trim(),reference.getText().trim(),due.getValue().toString(),priority.getValue(),notes.getText(),row==null?"OPEN":row.status.get(),currentUser(),null);
                    if(row==null) insightsApi.saveReminder(dto); else insightsApi.updateReminder(dto);
                    NotificationService.add((row==null?"Reminder created: ":"Reminder updated: ")+title.getText().trim()); refresh();
                } catch(Exception exception){ error("Reminder could not be saved",exception); }
                return;
            }

            String sql = row == null
                    ? "INSERT INTO reminder_register(title,reference_no,due_date,priority,notes,status,created_by,updated_at) "
                    + "VALUES(?,?,?,?,?,'OPEN',?,CURRENT_TIMESTAMP)"
                    : "UPDATE reminder_register SET title=?,reference_no=?,due_date=?,priority=?,notes=?,"
                    + "updated_at=CURRENT_TIMESTAMP WHERE id=?";

            try (Connection connection = DatabaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, title.getText().trim());
                statement.setString(2, reference.getText().trim());
                statement.setString(3, due.getValue().toString());
                statement.setString(4, priority.getValue());
                statement.setString(5, notes.getText());

                if (row == null) {
                    statement.setString(6, currentUser());
                } else {
                    statement.setInt(6, row.id);
                }

                statement.executeUpdate();
                NotificationService.add(
                        (row == null ? "Reminder created: " : "Reminder updated: ")
                                + title.getText().trim()
                );
                refresh();
            } catch (Exception exception) {
                error("Reminder could not be saved", exception);
            }
        });
    }

    private void complete(ReminderRow row) {
        changeStatus(row, "COMPLETED", "completed_at=CURRENT_TIMESTAMP,snoozed_until=NULL");
    }

    private void reopen(ReminderRow row) {
        changeStatus(row, "OPEN", "completed_at=NULL,snoozed_until=NULL");
    }

    private void changeStatus(ReminderRow row, String status, String extra) {
        if (row == null) {
            return;
        }

        if (ConfigManager.isApiDataEnabled()) {
            try { insightsApi.reminderStatus(row.id,status,null); NotificationService.add("Reminder "+row.title.get()+" marked "+status.toLowerCase(Locale.ROOT)+"."); refresh(); }
            catch(Exception exception){error("Reminder status could not be changed",exception);} return;
        }
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "UPDATE reminder_register SET status='" + status + "',"
                            + extra + ",updated_at=CURRENT_TIMESTAMP WHERE id=" + row.id
            );

            NotificationService.add(
                    "Reminder " + row.title.get() + " marked "
                            + status.toLowerCase(Locale.ROOT) + "."
            );
            refresh();
        } catch (Exception exception) {
            error("Reminder status could not be changed", exception);
        }
    }

    private void snooze(ReminderRow row) {
        if (row == null) {
            return;
        }

        DatePicker picker = new DatePicker(LocalDate.now().plusDays(1));
        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle("Snooze Reminder");
        dialog.setHeaderText(row.title.get());
        dialog.getDialogPane().setContent(
                new javafx.scene.layout.VBox(8, new Label("Snooze until"), picker)
        );

        ButtonType save = new ButtonType("Snooze", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        dialog.showAndWait().filter(save::equals).ifPresent(button -> {
            if(ConfigManager.isApiDataEnabled()){try{insightsApi.reminderStatus(row.id,"SNOOZED",picker.getValue().toString());refresh();}catch(Exception exception){error("Reminder could not be snoozed",exception);}return;}
            try (Connection connection = DatabaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE reminder_register SET status='SNOOZED',snoozed_until=?,"
                                 + "updated_at=CURRENT_TIMESTAMP WHERE id=?"
                 )) {

                statement.setString(1, picker.getValue().toString());
                statement.setInt(2, row.id);
                statement.executeUpdate();
                refresh();
            } catch (Exception exception) {
                error("Reminder could not be snoozed", exception);
            }
        });
    }

    private void delete(ReminderRow row) {
        if (row == null || !confirm("Delete reminder '" + row.title.get() + "'?")) {
            return;
        }

        if(ConfigManager.isApiDataEnabled()){try{insightsApi.deleteReminder(row.id);refresh();}catch(Exception exception){error("Reminder could not be deleted",exception);}return;}
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM reminder_register WHERE id=?"
             )) {

            statement.setInt(1, row.id);
            statement.executeUpdate();
            refresh();
        } catch (Exception exception) {
            error("Reminder could not be deleted", exception);
        }
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
                actions.setText("•••");
                actions.setGraphic(null);
                actions.setContentDisplay(ContentDisplay.TEXT_ONLY);
                actions.getStyleClass().addAll("reminder-action-button", "reminder-three-dot-button");
                setAlignment(Pos.CENTER);

                actions.setOnShowing(event -> rebuildActionMenu());
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
                MenuItem snooze = menuItem("Snooze", "notification", event -> snooze(row));
                MenuItem reopen = menuItem("Reopen", "refresh", event -> reopen(row));
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

                if (!completed && date.isBefore(LocalDate.now())) {
                    getStyleClass().add("reminder-due-overdue");
                } else if (!completed && date.equals(LocalDate.now())) {
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
            LocalDate today = LocalDate.now();
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
        LocalDate today = LocalDate.now();

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
        clearDetailBadgeClasses();

        if (detailMoreActions != null) {
            rebuildDetailActionMenu();
        }

        if (row == null) {
            lblDetailTitle.setText("Select a reminder");
            lblDetailReference.setText("—");
            lblDetailDue.setText("—");
            lblDetailPriority.setText("—");
            lblDetailStatus.setText("—");
            lblDetailNotes.setText("Select a row to review its notes and available actions.");
            return;
        }

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

    private void clearDetailBadgeClasses() {
        lblDetailPriority.getStyleClass().removeIf(style -> style.startsWith("priority-"));
        lblDetailStatus.getStyleClass().removeIf(style -> style.startsWith("status-"));
    }

    private static String formatDate(String value) {
        LocalDate date = parse(value, null);
        return date == null ? blank(value, "—") : DISPLAY_DATE.format(date);
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

    private void error(String title, Exception exception) {
        exception.printStackTrace();
        new OwnedAlert(
                Alert.AlertType.ERROR,
                title + ".\n\n" + exception.getMessage()
        ).showAndWait();
    }

    public static final class ReminderRow {
        final int id;
        final SimpleStringProperty title;
        final SimpleStringProperty reference;
        final SimpleStringProperty due;
        final SimpleStringProperty priority;
        final SimpleStringProperty status;
        final SimpleStringProperty createdBy;
        final String notes;

        ReminderRow(InsightsApiClient.ReminderDto d) {
            id=d.id()==null?0:d.id(); title=new SimpleStringProperty(blank(d.title(),"")); reference=new SimpleStringProperty(blank(d.referenceNo(),"—")); due=new SimpleStringProperty(blank(d.dueDate(),"")); priority=new SimpleStringProperty(blank(d.priority(),"NORMAL")); status=new SimpleStringProperty(blank(d.status(),"OPEN")); createdBy=new SimpleStringProperty(blank(d.createdBy(),"System")); notes=blank(d.notes(),"");
        }

        ReminderRow(ResultSet resultSet) throws SQLException {
            id = resultSet.getInt("id");
            title = new SimpleStringProperty(resultSet.getString("title"));
            reference = new SimpleStringProperty(
                    blank(resultSet.getString("reference_no"), "—")
            );
            due = new SimpleStringProperty(resultSet.getString("due_date"));
            priority = new SimpleStringProperty(resultSet.getString("priority"));
            status = new SimpleStringProperty(resultSet.getString("status"));
            createdBy = new SimpleStringProperty(
                    blank(resultSet.getString("created_by"), "System")
            );
            notes = blank(resultSet.getString("notes"), "");
        }
    }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colTitle, "reminder");
        IconFactory.applyTableHeaderIcon(colReference, "document");
        IconFactory.applyTableHeaderIcon(colDue, "calendar");
        IconFactory.applyTableHeaderIcon(colPriority, "warning");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colCreatedBy, "user");
        colActions.setText("");
        IconFactory.applyTableHeaderIcon(colActions, "actions");
    }
}
