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
import javafx.stage.DirectoryChooser;
import org.example.config.ConfigManager;
import org.example.api.support.SupportApiClient;
import org.example.api.authority.ServerBackupClient;
import org.example.backup.BackupManager;
import org.example.backup.LocalRecoveryManager;
import org.example.service.NotificationService;
import org.example.service.SessionService;
import org.example.util.IconFactory;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class BackupRestoreController {
    private final SupportApiClient supportApi = new SupportApiClient();
    private final ServerBackupClient serverBackups = new ServerBackupClient();

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
    @FXML private Button btnLocalRecovery;
    @FXML private Button btnExportRecovery;
    @FXML private Button btnOpenDatabaseFolder;
    @FXML private Button btnCopyDatabasePath;

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
        configureTable();
        configureScheduleControls();

        String databaseDescription = ConfigManager.isSharedClient()
                ? "Company server • " + ConfigManager.getConfiguredServerUrl()
                : database.toString();
        lblDatabase.setText(databaseDescription);
        lblDatabase.setTooltip(new Tooltip(databaseDescription));

        backupTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> btnRestoreSelected.setDisable(selected == null)
        );
        boolean recoveryAvailable = ConfigManager.isSharedClient() && SessionService.isAdmin();
        if (btnLocalRecovery != null) {
            btnLocalRecovery.setVisible(recoveryAvailable);
            btnLocalRecovery.setManaged(recoveryAvailable);
            btnLocalRecovery.setGraphic(IconFactory.compactIcon("restore", 14));
            btnLocalRecovery.setTooltip(new Tooltip("Create a fresh company-server recovery package and prepare an explicit LOCAL recovery workspace."));
        }
        if (btnExportRecovery != null) {
            btnExportRecovery.setVisible(recoveryAvailable);
            btnExportRecovery.setManaged(recoveryAvailable);
            btnExportRecovery.setGraphic(IconFactory.compactIcon("export", 14));
            btnExportRecovery.setTooltip(new Tooltip("Save an off-server disaster-recovery package containing a verified database snapshot and business files."));
        }
        if (ConfigManager.isSharedClient()) {
            if (btnOpenDatabaseFolder != null) {
                btnOpenDatabaseFolder.setText("Server Managed");
                btnOpenDatabaseFolder.setDisable(true);
                btnOpenDatabaseFolder.setTooltip(new Tooltip(
                        "The active PostgreSQL database and server backups are stored on the company server, not on this workstation."));
            }
            if (btnCopyDatabasePath != null) {
                btnCopyDatabasePath.setText("Copy Server Address");
                btnCopyDatabasePath.setTooltip(new Tooltip("Copy the configured company-server address."));
            }
        } else {
            if (btnOpenDatabaseFolder != null) {
                btnOpenDatabaseFolder.setText("Open Folder");
                btnOpenDatabaseFolder.setTooltip(new Tooltip("Open the local database folder."));
            }
            if (btnCopyDatabasePath != null) {
                btnCopyDatabasePath.setText("Copy Path");
                btnCopyDatabasePath.setTooltip(new Tooltip("Copy the local database path."));
            }
        }

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
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 2)
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
                actions.getStyleClass().addAll("backup-row-actions", "table-action-menu");
                actions.setText("Actions");
                actions.setGraphic(IconFactory.compactIcon("actions", 15));
                actions.setContentDisplay(ContentDisplay.LEFT);
                actions.setGraphicTextGap(6);

                actions.setFocusTraversable(false);
                actions.setTooltip(new Tooltip("Backup actions"));
                IconFactory.decorateActionMenu(actions);
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
            if (ConfigManager.isSharedClient()) {
                var rows = serverBackups.list().stream().map(this::toRemoteRow).toList();
                backupRows.setAll(rows);
                updateSummaryCards();
                setStatus(rows.size() + " backup(s) available on the company server");
                return;
            }

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

    private BackupRow toRemoteRow(ServerBackupClient.BackupFile backup) {
        String created;
        try {
            created = BusinessClock.formatInstant(Instant.parse(backup.createdAt()), "hh:mm a");
        } catch (Exception ignored) {
            created = backup.createdAt() == null ? "Unknown" : backup.createdAt();
        }
        return new BackupRow(Path.of(backup.name()), backup.name(), created, human(backup.size()),
                "Available", titleCase(backup.source()));
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

        try {
            ServerBackupClient.DatabaseMetrics metrics = serverBackups.metrics();
            lblDatabaseSize.setText(human(metrics.sizeBytes()));
            lblDatabaseHealth.setText((metrics.databaseName() == null || metrics.databaseName().isBlank() ? "PostgreSQL" : metrics.databaseName())
                    + (ConfigManager.isSharedClient() ? " • Company server" : " • This PC"));
            lblDatabaseHealth.getStyleClass().setAll("backup-metric-caption", "backup-caption-positive");
            lblDatabaseSize.setTooltip(new Tooltip("Authoritative PostgreSQL size from pg_database_size(current_database())"));
        } catch (Exception metricFailure) {
            lblDatabaseSize.setText("Unavailable");
            lblDatabaseHealth.setText("Database size could not be read from PostgreSQL");
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
    private void exportRecoveryPackage() {
        if (!ConfigManager.isSharedClient() || !SessionService.isAdmin()) {
            showWarning("Recovery package export is available only to an administrator while this PC is connected to the company server.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Disaster Recovery Package");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DSE ERP recovery package (*.zip)", "*.zip"));
        chooser.setInitialFileName("DSE-ERP-Recovery-" + java.time.LocalDate.now() + ".zip");
        File selected = chooser.showSaveDialog(backupTable.getScene().getWindow());
        if (selected == null) return;
        Path target = selected.toPath().toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            target = target.resolveSibling(target.getFileName() + ".zip");
        }
        Path finalTarget = target;
        runOperation("Creating and exporting a fresh off-server recovery package...", () ->
                serverBackups.downloadRecoveryPackage(finalTarget), result -> {
            NotificationService.add("Disaster recovery package exported: " + result.file());
            OwnedAlert ready = new OwnedAlert(Alert.AlertType.INFORMATION,
                    "A fresh verified disaster-recovery package was saved outside the company server.\n\n"
                            + "File: " + result.file() + "\n"
                            + "Source: " + result.environment() + " • " + result.applicationVersion() + "\n\n"
                            + "Keep this file secure. It contains a database snapshot and company business files and can be used for explicit LOCAL recovery if the server is unavailable.");
            ready.setHeaderText("Recovery package exported");
            ready.showAndWait();
        });
    }

    @FXML
    private void emergencyLocalRecovery() {
        if (!ConfigManager.isSharedClient() || !SessionService.isAdmin()) {
            showWarning("Emergency Local Recovery is available only to an administrator while this PC is connected to the company server.");
            return;
        }

        ButtonType continueRecovery = new ButtonType("Prepare LOCAL Recovery", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        OwnedAlert warning = new OwnedAlert(Alert.AlertType.WARNING,
                "Use this only for a planned server-to-local move or a serious cloud/server incident.\n\n"
                        + "Before continuing, stop business activity on every other DSE ERP PC. The recovery package is a point-in-time copy; changes made on the company server after this package is created will not be merged automatically.\n\n"
                        + "DSE ERP will create a fresh verified server database snapshot, include server-owned Attachments, Documents and Templates, stage them into a LOCAL workspace, and then close this Shared Client. No automatic stale-local fallback is used.",
                cancel, continueRecovery);
        warning.setHeaderText("Emergency LOCAL disaster recovery");
        if (warning.showAndWait().orElse(cancel) != continueRecovery) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose LOCAL Recovery Workspace");
        File selected = chooser.showDialog(backupTable.getScene().getWindow());
        if (selected == null) return;
        Path target = selected.toPath().toAbsolutePath().normalize();
        var inspection = org.example.config.WorkspaceManager.inspectLocalRecoveryTarget(target);
        if (!inspection.valid()) {
            showWarning(inspection.message());
            return;
        }

        OwnedAlert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION,
                "Recovery target:\n" + target + "\n\n" + inspection.message() + "\n\n"
                        + "After preparation, DSE ERP will close. On the next start this PC will open the LOCAL workspace, restore the server snapshot before login, and only then apply the staged business files.\n\n"
                        + "Continue?",
                cancel, continueRecovery);
        confirmation.setHeaderText(inspection.existingLocal() ? "Recover into preserved LOCAL workspace" : "Create new LOCAL recovery workspace");
        if (confirmation.showAndWait().orElse(cancel) != continueRecovery) return;

        Path download = org.example.config.WorkspaceManager.getTempFolder().resolve(
                "DSE-ERP-Recovery-download-" + java.time.Instant.now().toEpochMilli() + ".zip");
        runOperation("Creating and downloading a fresh company-server recovery package...", () -> {
            try {
                ServerBackupClient.RecoveryPackage packageInfo = serverBackups.downloadRecoveryPackage(download);
                return LocalRecoveryManager.stageForLocal(packageInfo.file(), target, ConfigManager.getConfiguredServerUrl());
            } finally {
                try { Files.deleteIfExists(download); } catch (Exception ignored) { }
            }
        }, staged -> {
            NotificationService.add("LOCAL disaster recovery prepared from company server " + staged.sourceEnvironment()
                    + " " + staged.sourceVersion() + ".");
            OwnedAlert ready = new OwnedAlert(Alert.AlertType.INFORMATION,
                    "The recovery package was verified and staged successfully.\n\n"
                            + "Source: " + staged.sourceEnvironment() + " • " + staged.sourceVersion() + " • " + staged.databaseName() + "\n"
                            + "LOCAL workspace: " + staged.workspace() + "\n"
                            + "Off-PC/server recovery package retained at: " + staged.retainedPackage() + "\n\n"
                            + "The current Shared Client session will now close. Start DSE ERP again to perform the LOCAL database restore before login. The existing company-server data is not modified by this recovery preparation.");
            ready.setHeaderText("LOCAL recovery prepared");
            ready.showAndWait();
            Platform.exit();
        });
    }

    @FXML
    private void createBackup() {
        org.example.service.PermissionService.require("BACKUP.CREATE", "create a database backup");
        if (!confirm("Create database backup", "Create a new verified DSE ERP database backup now?")) return;
        if (ConfigManager.isSharedClient()) {
            runOperation("Creating a company-server database backup...", serverBackups::create, target -> {
                NotificationService.add("Company-server backup created: " + target.name());
                refresh();
                selectPath(Path.of(target.name()));
                setStatus("Server backup created: " + target.name());
            });
            return;
        }
        runOperation("Creating a consistent database backup...", BackupManager::createManualBackup, target -> {
            NotificationService.add("ERP database backup created: " + target.getFileName());
            refresh(); selectPath(target); setStatus("Backup created and verified: " + target.getFileName());
        });
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
        org.example.service.PermissionService.require("BACKUP.EDIT", "stage a database restore");
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
                    if (ConfigManager.isSharedClient()) serverBackups.stageRestore(row.name());
                    else BackupManager.stageRestore(row.path());
                    return row.path();
                },
                ignored -> {
                    NotificationService.add("Database restore staged from " + row.name());
                    boolean shared = ConfigManager.isSharedClient();
                    String detail = shared
                            ? "The restore has been staged safely on the company server.\n\n"
                            + "Apply the staged restore through the DSE ERP Company Server restore procedure, then restart the company server. "
                            + "Restarting this workstation alone does not replace the shared database."
                            : "The restore has been staged safely.\n\n"
                            + "Close DSE ERP and start it again. A verified safety backup of the current "
                            + "database will be created automatically before the staged restore is applied.";
                    Alert staged = new OwnedAlert(Alert.AlertType.INFORMATION, detail);
                    staged.setHeaderText(shared ? "Restore staged on company server" : "Restore ready for next startup");
                    staged.showAndWait();
                    setStatus(shared ? "Server restore staged. Apply it during the company-server restart procedure."
                            : "Restore staged. Restart DSE ERP to apply it.");
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
        org.example.service.PermissionService.require("BACKUP.CREATE", "import a database backup");
        if (file == null) return;
        if (!confirm("Import database backup", "Validate and import " + file.getFileName() + " into DSE ERP backup history?")) return;
        if (ConfigManager.isSharedClient()) {
            runOperation("Uploading, validating and importing backup on the company server...", () -> serverBackups.importBackup(file), target -> {
                NotificationService.add("External backup imported to company server: " + target.name());
                refresh(); selectPath(Path.of(target.name())); setStatus("Server backup imported and verified: " + target.name());
            });
            return;
        }
        runOperation("Validating and importing backup...", () -> BackupManager.importBackup(file), target -> {
            NotificationService.add("External backup imported: " + file.getFileName());
            refresh(); selectPath(target); setStatus("Backup imported and verified: " + target.getFileName());
        });
    }

    private void validateRow(BackupRow row) {
        org.example.service.PermissionService.require("BACKUP.VIEW", "validate a database backup");
        if (row == null) return;
        if (ConfigManager.isSharedClient()) {
            runOperation("Validating backup integrity on the company server...", () -> serverBackups.validate(row.name()), validation -> {
                refresh(); selectPath(Path.of(row.name()));
                if (!validation.valid()) { showWarning(validation.message()); return; }
                setStatus("Validation passed: " + row.name());
                Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION, validation.message());
                alert.setHeaderText(row.name()); alert.showAndWait();
            });
            return;
        }
        runOperation("Validating backup integrity and ERP compatibility...", () -> BackupManager.validateBackup(row.path()), validation -> {
            BackupManager.updateValidationStatus(row.path(), validation); refresh(); selectPath(row.path());
            if (!validation.valid()) { showWarning(validation.message()); return; }
            setStatus("Validation passed: " + row.name());
            Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION, validation.message() + "\n\nCompatibility: " + validation.compatibility());
            alert.setHeaderText(row.name()); alert.showAndWait();
        });
    }

    private void deleteRow(BackupRow row) {
        org.example.service.PermissionService.require("BACKUP.DELETE", "delete a database backup");
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
                    if (ConfigManager.isSharedClient()) serverBackups.delete(row.name());
                    else BackupManager.deleteBackupSafely(row.path());
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
                        + (ConfigManager.isSharedClient() ? "Stored on company server" : row.path())
        );
        details.showAndWait();
    }

    private void openBackupLocation(BackupRow row) {
        if (row == null) return;
        if (ConfigManager.isSharedClient()) {
            Alert info = new OwnedAlert(Alert.AlertType.INFORMATION, "This backup is stored and managed on the company server.");
            info.setHeaderText(row.name()); info.showAndWait(); return;
        }
        openFolder(row.path().getParent());
    }

    @FXML
    private void openDatabaseFolder() {
        if (ConfigManager.isSharedClient()) {
            Alert info = new OwnedAlert(Alert.AlertType.INFORMATION, "The active PostgreSQL database is managed by the company server and has no workstation folder to open.");
            info.setHeaderText("Company server database"); info.showAndWait(); return;
        }
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
        content.putString(ConfigManager.isSharedClient()
                ? ConfigManager.getConfiguredServerUrl() + " • Company server PostgreSQL database"
                : database.toString());
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(ConfigManager.isSharedClient()
                ? "Company server address copied to clipboard."
                : "Database path copied to clipboard.");
    }

    @FXML
    private void saveSettings() {
        org.example.service.PermissionService.require("BACKUP.EDIT", "change backup schedule or retention settings");
        if (!confirm("Save backup schedule", "Save the selected backup schedule and retention settings?")) return;
        try {
            supportApi.setSetting("backup.schedule", cmbSchedule.getValue());
            supportApi.setSetting("backup.retention", String.valueOf(spRetention.getValue()));
            NotificationService.add("Backup preferences saved."); updateScheduleSummary();
            if (ConfigManager.isSharedClient()) {
                refresh(); setStatus("Company-server backup preferences saved.");
            } else {
                int removed = BackupManager.applyRetention(spRetention.getValue()); refresh();
                setStatus("Backup preferences saved. " + removed + " expired backup(s) moved to recycle storage.");
            }
        } catch (Exception exception) { showError(exception); }
    }

    private void loadSettings() {
        try {
            cmbSchedule.setValue(supportApi.setting("backup.schedule", "MANUAL"));
            spRetention.getValueFactory().setValue(Integer.parseInt(supportApi.setting("backup.retention", "2")));
        } catch (Exception ignored) { cmbSchedule.setValue("MANUAL"); }
        if (cmbSchedule.getValue() == null) cmbSchedule.setValue("MANUAL");
        updateScheduleSummary();
    }

    private void updateScheduleSummary() {
        String schedule = cmbSchedule.getValue() == null ? "MANUAL" : cmbSchedule.getValue();
        Integer retention = spRetention.getValue();

        lblRetentionSummary.setText((retention == null ? 2 : retention) + " backups");
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
            return BusinessClock.formatInstant(instant, "hh:mm a");
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

    private boolean confirm(String title, String message) {
        Alert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText(title);
        return confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
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
}
