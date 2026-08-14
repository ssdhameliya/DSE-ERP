package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedAlert;

import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.example.config.ConfigManager;
import org.example.api.support.SupportApiClient;
import org.example.backup.BackupManager;
import org.example.service.NotificationService;
import org.example.util.IconFactory;

import java.awt.Desktop;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class BackupRestoreController {
    private final SupportApiClient supportApi = new SupportApiClient();

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final DateTimeFormatter DISPLAY_TIMESTAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @FXML private Label lblDatabase;
    @FXML private Label lblStatus;
    @FXML private Label lblLastBackup;
    @FXML private Label lblBackupCount;
    @FXML private Label lblDatabaseSize;
    @FXML private Label lblDatabaseHealth;
    @FXML private Label lblBackupCountCaption;
    @FXML private Label lblLastBackupCaption;
    @FXML private Label lblRetentionSummary;
    @FXML private Label lblScheduleSummary;
    @FXML private Label lblNextBackup;
    @FXML private Label lblHistoryCount;

    @FXML private ComboBox<String> cmbSchedule;
    @FXML private Spinner<Integer> spRetention;
    @FXML private Button btnRestoreSelected;

    @FXML private TableView<BackupRow> backupTable;
    @FXML private TableColumn<BackupRow, String> colBackupName;
    @FXML private TableColumn<BackupRow, String> colCreated;
    @FXML private TableColumn<BackupRow, String> colSize;
    @FXML private TableColumn<BackupRow, String> colStatus;
    @FXML private TableColumn<BackupRow, String> colSource;
    @FXML private TableColumn<BackupRow, Void> colActions;

    @FXML private StackPane headerIconHolder;
    @FXML private StackPane databaseSizeIconHolder;
    @FXML private StackPane backupCountIconHolder;
    @FXML private StackPane lastBackupIconHolder;
    @FXML private StackPane retentionIconHolder;
    @FXML private StackPane scheduleIconHolder;
    @FXML private StackPane restoreIconHolder;
    @FXML private StackPane dropZoneIconHolder;
    @FXML private StackPane nextBackupIconHolder;
    @FXML private StackPane safetyIconHolder;
    @FXML private StackPane statusIconHolder;

    private final Path database = BackupManager.databasePath();
    private final Path backupFolder = BackupManager.backupFolder();
    private final ObservableList<BackupRow> backupRows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        installIcons();
        configureExplicitTableHeaderIcons();
        configureTable();
        configureScheduleControls();

        lblDatabase.setText(database.toString());
        lblDatabase.setTooltip(new Tooltip(database.toString()));

        backupTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> btnRestoreSelected.setDisable(selected == null)
        );

        loadSettings();
        refresh();
    }

    private void installIcons() {
        setIcon(headerIconHolder, "backup", 27);
        setIcon(databaseSizeIconHolder, "backup", 22);
        setIcon(backupCountIconHolder, "backup", 22);
        setIcon(lastBackupIconHolder, "complete", 22);
        setIcon(retentionIconHolder, "calendar", 22);
        setIcon(scheduleIconHolder, "calendar", 19);
        setIcon(restoreIconHolder, "import", 19);
        setIcon(dropZoneIconHolder, "import", 24);
        setCompactIcon(nextBackupIconHolder, "reminder", 14);
        setCompactIcon(safetyIconHolder, "complete", 14);
        setCompactIcon(statusIconHolder, "status", 14);

    }

    private void setIcon(StackPane holder, String semantic, double size) {
        if (holder != null) holder.getChildren().setAll(IconFactory.icon(semantic, size));
    }

    private void setCompactIcon(StackPane holder, String semantic, double size) {
        if (holder != null) holder.getChildren().setAll(IconFactory.compactIcon(semantic, size));
    }

    private void configureScheduleControls() {
        cmbSchedule.getItems().setAll("MANUAL", "DAILY", "WEEKLY", "MONTHLY");
        spRetention.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 30)
        );
    }

    private void configureTable() {
        colBackupName.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().name())
        );
        colCreated.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().created())
        );
        colSize.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().size())
        );
        colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().status())
        );
        colSource.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().source())
        );

        colStatus.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(status);
                badge.getStyleClass().setAll(
                        "backup-status-badge",
                        switch (status.toUpperCase(Locale.ROOT)) {
                            case "VERIFIED" -> "backup-status-verified";
                            case "INVALID" -> "backup-status-invalid";
                            default -> "backup-status-available";
                        }
                );
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(badge);
            }
        });

        colActions.setCellFactory(column -> createActionCell());
        backupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        backupTable.setItems(backupRows);

        Label placeholder = new Label(
                "No backups available\nCreate a backup to protect your ERP data."
        );
        placeholder.setWrapText(true);
        placeholder.setGraphic(IconFactory.icon("backup", 30));
        placeholder.setContentDisplay(ContentDisplay.TOP);
        placeholder.setGraphicTextGap(10);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.getStyleClass().add("backup-empty-state");
        backupTable.setPlaceholder(placeholder);
    }

    private TableCell<BackupRow, Void> createActionCell() {
        return new TableCell<>() {
            private final MenuButton actions = new MenuButton();
            private BackupRow currentRow;

            private final MenuItem viewDetails = menuItem("View Details", "view", () -> showDetails(currentRow));
            private final MenuItem validate = menuItem("Validate Backup", "complete", () -> validateRow(currentRow));
            private final MenuItem openLocation = menuItem("Open Location", "location", () -> openBackupLocation(currentRow));
            private final MenuItem restore = menuItem("Restore Backup", "backup", () -> restoreRow(currentRow));
            private final MenuItem delete = menuItem("Delete Backup", "delete", () -> deleteRow(currentRow));

            {
                actions.getItems().setAll(
                        viewDetails,
                        validate,
                        openLocation,
                        new SeparatorMenuItem(),
                        restore,
                        delete
                );
                actions.getStyleClass().addAll("backup-row-actions", "square-action");
                actions.setFocusTraversable(false);
                actions.setTooltip(new Tooltip("Backup actions"));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    currentRow = null;
                    setGraphic(null);
                    return;
                }

                currentRow = getTableView().getItems().get(getIndex());
                setAlignment(Pos.CENTER);
                setGraphic(actions);
            }
        };
    }

    private MenuItem menuItem(String text, String icon, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setGraphic(IconFactory.compactIcon(icon, 15));
        item.setOnAction(event -> {
            if (action != null) action.run();
        });
        return item;
    }

    @FXML
    private void refresh() {
        try {
            Files.createDirectories(backupFolder);

            var rows = new java.util.ArrayList<BackupRow>();
            try (var stream = Files.list(backupFolder)) {
                stream.filter(this::isDatabaseBackup)
                        .sorted(Comparator.comparing(this::modified).reversed())
                        .map(this::toRow)
                        .forEach(rows::add);
            }

            backupRows.setAll(rows);
            updateSummaryCards();
            setStatus(rows.size() + " backup(s) available in " + backupFolder);
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private boolean isDatabaseBackup(Path path) {
        return Files.isRegularFile(path)
                && (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".db")
                    || path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pgbackup"));
    }

    private BackupRow toRow(Path path) {
        String filename = path.getFileName().toString();
        var metadata = BackupManager.metadataFor(path);
        String status = metadata.map(BackupManager.BackupMetadata::status).orElse("Available");
        String source = metadata.map(BackupManager.BackupMetadata::source).orElse(sourceFor(filename));
        return new BackupRow(
                path,
                filename,
                formatModified(path),
                safeSize(path),
                titleCase(status),
                titleCase(source)
        );
    }

    private String sourceFor(String filename) {
        if (filename.startsWith("Imported-")) return "Imported";
        if (filename.startsWith("Before-Restore-")) return "Safety";
        return "ERP Backup";
    }

    private void updateSummaryCards() throws Exception {
        int count = backupRows.size();
        lblBackupCount.setText(String.valueOf(count));
        lblBackupCountCaption.setText(count == 1 ? "1 backup available" : count + " backups available");
        lblHistoryCount.setText(count == 1 ? "1 backup" : count + " backups");

        if (Files.exists(database)) {
            lblDatabaseSize.setText(human(Files.size(database)));
            lblDatabaseHealth.setText("Database file is available");
            lblDatabaseHealth.getStyleClass().setAll("backup-metric-caption", "backup-caption-positive");
        } else {
            lblDatabaseSize.setText("Not found");
            lblDatabaseHealth.setText("Database file could not be located");
            lblDatabaseHealth.getStyleClass().setAll("backup-metric-caption", "backup-caption-negative");
        }

        if (backupRows.isEmpty()) {
            lblLastBackup.setText("Never");
            lblLastBackupCaption.setText("Create your first backup");
        } else {
            BackupRow latest = backupRows.getFirst();
            lblLastBackup.setText(latest.created());
            lblLastBackupCaption.setText(latest.name());
        }

        updateScheduleSummary();
    }

    @FXML
    private void createBackup() {
        runOperation(
                "Creating a consistent database backup...",
                BackupManager::createManualBackup,
                target -> {
                    NotificationService.add("ERP database backup created: " + target.getFileName());
                    refresh();
                    selectPath(target);
                    setStatus("Backup created and verified: " + target.getFileName());
                }
        );
    }

    @FXML
    private void restoreBackup() {
        BackupRow selected = backupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Select a backup first.");
            return;
        }
        restoreRow(selected);
    }

    private void restoreRow(BackupRow row) {
        if (row == null) return;
        backupTable.getSelectionModel().select(row);

        Alert confirmation = new OwnedAlert(
                Alert.AlertType.CONFIRMATION,
                "Stage " + row.name() + " for restore?\n\n"
                        + "The active database will not be replaced now. The restore will be applied safely "
                        + "before any database connection opens on the next application start.",
                ButtonType.YES,
                ButtonType.NO
        );
        confirmation.setHeaderText("Stage database restore");
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        runOperation(
                "Validating and staging restore...",
                () -> {
                    BackupManager.stageRestore(row.path());
                    return row.path();
                },
                ignored -> {
                    NotificationService.add("Database restore staged from " + row.name());
                    Alert staged = new OwnedAlert(
                            Alert.AlertType.INFORMATION,
                            "The restore has been staged safely.\n\n"
                                    + "Close DSE ERP and start it again. A verified safety backup of the current "
                                    + "database will be created automatically before the staged restore is applied."
                    );
                    staged.setHeaderText("Restore ready for next startup");
                    staged.showAndWait();
                    setStatus("Restore staged. Restart DSE ERP to apply it.");
                }
        );
    }

    @FXML
    private void browseBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select DSE ERP Backup");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ERP backup", "*.db", "*.pgbackup")
        );

        var file = chooser.showOpenDialog(backupTable.getScene().getWindow());
        if (file != null) importBackup(file.toPath());
    }

    @FXML
    private void dragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()
                && event.getDragboard().getFiles().size() == 1
                && event.getDragboard().getFiles().getFirst().getName()
                        .toLowerCase(Locale.ROOT).matches(".*\\.(db|pgbackup)$")) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    @FXML
    private void dropBackup(DragEvent event) {
        if (event.getDragboard().hasFiles() && !event.getDragboard().getFiles().isEmpty()) {
            Path file = event.getDragboard().getFiles().getFirst().toPath();
            importBackup(file);
            event.setDropCompleted(true);
        } else {
            event.setDropCompleted(false);
        }
        event.consume();
    }

    private void importBackup(Path file) {
        runOperation(
                "Validating and importing backup...",
                () -> BackupManager.importBackup(file),
                target -> {
                    NotificationService.add("External backup imported: " + file.getFileName());
                    refresh();
                    selectPath(target);
                    setStatus("Backup imported and verified: " + target.getFileName());
                }
        );
    }

    private void validateRow(BackupRow row) {
        if (row == null) return;
        runOperation(
                "Validating backup integrity and ERP compatibility...",
                () -> BackupManager.validateBackup(row.path()),
                validation -> {
                    BackupManager.updateValidationStatus(row.path(), validation);
                    refresh();
                    selectPath(row.path());
                    if (!validation.valid()) {
                        showWarning(validation.message());
                        return;
                    }
                    setStatus("Validation passed: " + row.name());
                    Alert alert = new OwnedAlert(
                            Alert.AlertType.INFORMATION,
                            validation.message() + "\n\nCompatibility: " + validation.compatibility()
                    );
                    alert.setHeaderText(row.name());
                    alert.showAndWait();
                }
        );
    }

    private void deleteRow(BackupRow row) {
        if (row == null) return;

        Alert confirmation = new OwnedAlert(
                Alert.AlertType.CONFIRMATION,
                "Move " + row.name() + " to the backup recycle folder?\n\n"
                        + "Deleted backups are retained for seven days before permanent cleanup.",
                ButtonType.YES,
                ButtonType.NO
        );
        confirmation.setHeaderText("Delete backup safely");
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        runOperation(
                "Moving backup to recycle storage...",
                () -> {
                    BackupManager.deleteBackupSafely(row.path());
                    return row.path();
                },
                ignored -> {
                    refresh();
                    setStatus("Backup moved to recycle storage: " + row.name());
                }
        );
    }

    /**
     * Executes a backup operation away from the JavaFX Application Thread and
     * delivers the successful result back on the JavaFX thread.
     */
    private <T> void runOperation(
            String statusMessage,
            Callable<T> operation,
            Consumer<T> onSuccess
    ) {
        if (operation == null) {
            throw new IllegalArgumentException("Backup operation must not be null.");
        }

        setStatus(statusMessage);
        setOperationRunning(true);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };

        task.setOnSucceeded(event -> {
            setOperationRunning(false);
            try {
                if (onSuccess != null) {
                    onSuccess.accept(task.getValue());
                }
            } catch (Exception exception) {
                setStatus("Backup operation completed, but the screen could not be refreshed.");
                showError(exception);
            }
        });

        task.setOnFailed(event -> {
            setOperationRunning(false);
            Throwable failure = task.getException();
            Exception exception = failure instanceof Exception existing
                    ? existing
                    : new RuntimeException("Backup operation failed.", failure);
            setStatus("Backup operation failed.");
            showError(exception);
        });

        task.setOnCancelled(event -> {
            setOperationRunning(false);
            setStatus("Backup operation cancelled.");
        });

        Thread worker = new Thread(task, "dse-erp-backup-operation");
        worker.setDaemon(true);
        worker.start();
    }

    private void setOperationRunning(boolean running) {
        if (backupTable != null && backupTable.getScene() != null
                && backupTable.getScene().getRoot() != null) {
            backupTable.getScene().getRoot().setDisable(running);
        } else {
            backupTable.setDisable(running);
            cmbSchedule.setDisable(running);
            spRetention.setDisable(running);
        }

        if (btnRestoreSelected != null) {
            btnRestoreSelected.setDisable(
                    running || backupTable.getSelectionModel().getSelectedItem() == null
            );
        }
    }

    private void showDetails(BackupRow row) {
        if (row == null) return;

        Alert details = new OwnedAlert(Alert.AlertType.INFORMATION);
        details.setHeaderText(row.name());
        details.setContentText(
                "Created: " + row.created() + "\n"
                        + "Size: " + row.size() + "\n"
                        + "Status: " + row.status() + "\n"
                        + "Source: " + row.source() + "\n\n"
                        + row.path()
        );
        details.showAndWait();
    }

    private void openBackupLocation(BackupRow row) {
        if (row == null) return;
        openFolder(row.path().getParent());
    }

    @FXML
    private void openDatabaseFolder() {
        openFolder(database.getParent());
    }

    private void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Opening folders is not supported on this computer.");
            }
            Desktop.getDesktop().open(folder.toFile());
        } catch (Exception exception) {
            showError(exception);
        }
    }

    @FXML
    private void copyDatabasePath() {
        ClipboardContent content = new ClipboardContent();
        content.putString(database.toString());
        Clipboard.getSystemClipboard().setContent(content);
        setStatus("Database path copied to clipboard.");
    }

    @FXML
    private void saveSettings() {
        try {
            supportApi.setSetting("backup.schedule", cmbSchedule.getValue());
            supportApi.setSetting("backup.retention", String.valueOf(spRetention.getValue()));
            NotificationService.add("Backup preferences saved."); updateScheduleSummary();
            int removed = BackupManager.applyRetention(spRetention.getValue()); refresh();
            setStatus("Backup preferences saved. " + removed + " expired backup(s) moved to recycle storage.");
        } catch (Exception exception) { showError(exception); }
    }

    private void loadSettings() {
        try {
            cmbSchedule.setValue(supportApi.setting("backup.schedule", "MANUAL"));
            spRetention.getValueFactory().setValue(Integer.parseInt(supportApi.setting("backup.retention", "30")));
        } catch (Exception ignored) { cmbSchedule.setValue("MANUAL"); }
        if (cmbSchedule.getValue() == null) cmbSchedule.setValue("MANUAL");
        updateScheduleSummary();
    }

    private void updateScheduleSummary() {
        String schedule = cmbSchedule.getValue() == null ? "MANUAL" : cmbSchedule.getValue();
        Integer retention = spRetention.getValue();

        lblRetentionSummary.setText((retention == null ? 30 : retention) + " Days");
        lblScheduleSummary.setText(
                schedule.equals("MANUAL")
                        ? "Manual backup schedule"
                        : titleCase(schedule) + " backup schedule"
        );
        lblNextBackup.setText(
                schedule.equals("MANUAL")
                        ? "Backups are created manually when requested."
                        : "The ERP checks the " + schedule.toLowerCase(Locale.ROOT)
                        + " schedule at startup and hourly while running."
        );
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) return "Manual";
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void markStatus(Path path, String status) {
        for (int index = 0; index < backupRows.size(); index++) {
            BackupRow row = backupRows.get(index);
            if (row.path().equals(path)) {
                BackupRow updated = new BackupRow(
                        row.path(), row.name(), row.created(), row.size(), status, row.source()
                );
                backupRows.set(index, updated);
                backupTable.getSelectionModel().select(updated);
                break;
            }
        }
    }

    private void selectPath(Path path) {
        backupRows.stream()
                .filter(row -> row.path().equals(path))
                .findFirst()
                .ifPresent(row -> {
                    backupTable.getSelectionModel().select(row);
                    backupTable.scrollTo(row);
                });
    }

    private String formatModified(Path path) {
        try {
            Instant instant = Files.getLastModifiedTime(path).toInstant();
            return DISPLAY_TIMESTAMP.format(instant.atZone(BusinessClock.zone()));
        } catch (Exception ignored) {
            return "Unknown";
        }
    }

    private String safeSize(Path path) {
        try {
            return human(Files.size(path));
        } catch (Exception ignored) {
            return "—";
        }
    }

    private long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024d);
        return String.format("%.1f MB", bytes / 1024d / 1024d);
    }

    private void setStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message == null ? "" : message);
        }
    }

    private void showWarning(String message) {
        Alert warning = new OwnedAlert(Alert.AlertType.WARNING, message);
        warning.setHeaderText("Backup & Restore");
        warning.showAndWait();
    }

    private void showError(Exception exception) {
        Alert error = new OwnedAlert(
                Alert.AlertType.ERROR,
                "Backup operation failed.\n\n" + exception.getMessage()
        );
        error.setHeaderText("Backup & Restore");
        error.showAndWait();
    }

    public record BackupRow(
            Path path,
            String name,
            String created,
            String size,
            String status,
            String source
    ) {
    }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colBackupName, "backup");
        IconFactory.applyTableHeaderIcon(colCreated, "calendar");
        IconFactory.applyTableHeaderIcon(colSize, "quantity");
        IconFactory.applyTableHeaderIcon(colStatus, "status");
        IconFactory.applyTableHeaderIcon(colSource, "source");
        colActions.setText(""); IconFactory.applyTableHeaderIcon(colActions, "more");
    }
}
