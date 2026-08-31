package org.example.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.config.WorkspaceManager;
import org.example.navigation.NavigationManager;
import org.example.rollback.RollbackService;
import org.example.update.BuildInfo;
import org.example.util.IconFactory;
import org.example.util.BusinessClock;
import org.example.util.OwnedAlert;
import org.example.util.OwnedTextInputDialog;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Controller for Settings-adjacent production-safe application rollback. */
public class SafeRollbackController {
    private final RollbackService service = new RollbackService();

    @FXML private StackPane headerIconHolder;
    @FXML private StackPane versionIconHolder;
    @FXML private StackPane schemaIconHolder;
    @FXML private StackPane safetyIconHolder;
    @FXML private StackPane packageIconHolder;
    @FXML private StackPane shieldIconHolder;
    @FXML private StackPane recoveryIconHolder;
    @FXML private StackPane packagesSectionIconHolder;
    @FXML private StackPane sequenceIconHolder;
    @FXML private StackPane activityIconHolder;
    @FXML private StackPane step1IconHolder;
    @FXML private StackPane step2IconHolder;
    @FXML private StackPane step3IconHolder;
    @FXML private StackPane step4IconHolder;
    @FXML private StackPane step5IconHolder;
    @FXML private StackPane step6IconHolder;

    @FXML private Label lblCurrentVersion;
    @FXML private Label lblDatabaseSchema;
    @FXML private Label lblSafetyStatus;
    @FXML private Label lblPackageCount;
    @FXML private Label lblRecoveryPoint;
    @FXML private Label lblHistoryCount;
    @FXML private Label lblStatus;

    @FXML private TableView<RollbackService.Candidate> candidateTable;
    @FXML private TableColumn<RollbackService.Candidate, String> colVersion;
    @FXML private TableColumn<RollbackService.Candidate, String> colPackage;
    @FXML private TableColumn<RollbackService.Candidate, String> colSchema;
    @FXML private TableColumn<RollbackService.Candidate, String> colCompatibility;
    @FXML private TableColumn<RollbackService.Candidate, String> colStatus;
    @FXML private TableColumn<RollbackService.Candidate, Void> colActions;

    @FXML private TableView<RollbackService.HistoryEntry> historyTable;
    @FXML private TableColumn<RollbackService.HistoryEntry, String> colHistoryTime;
    @FXML private TableColumn<RollbackService.HistoryEntry, String> colHistoryAction;
    @FXML private TableColumn<RollbackService.HistoryEntry, String> colHistoryVersion;
    @FXML private TableColumn<RollbackService.HistoryEntry, String> colHistoryResult;
    @FXML private TableColumn<RollbackService.HistoryEntry, String> colHistoryDetail;

    @FXML
    private void initialize() {
        installIcons();
        configureTables();
        lblCurrentVersion.setText(BuildInfo.version());
        lblDatabaseSchema.setText("Schema " + BuildInfo.databaseMigrationVersion());
        lblSafetyStatus.setText("Current data preserved");
        refresh();
    }

    private void installIcons() {
        if (headerIconHolder != null) headerIconHolder.getChildren().setAll(IconFactory.icon("rollback", 30));
        if (versionIconHolder != null) versionIconHolder.getChildren().setAll(IconFactory.icon("update", 22));
        if (schemaIconHolder != null) schemaIconHolder.getChildren().setAll(IconFactory.icon("database", 22));
        if (safetyIconHolder != null) safetyIconHolder.getChildren().setAll(IconFactory.icon("security", 22));
        if (packageIconHolder != null) packageIconHolder.getChildren().setAll(IconFactory.icon("package", 22));
        if (shieldIconHolder != null) shieldIconHolder.getChildren().setAll(IconFactory.icon("rollback", 27));
        if (recoveryIconHolder != null) recoveryIconHolder.getChildren().setAll(IconFactory.icon("recovery", 25));
        if (packagesSectionIconHolder != null) packagesSectionIconHolder.getChildren().setAll(IconFactory.icon("package", 20));
        if (sequenceIconHolder != null) sequenceIconHolder.getChildren().setAll(IconFactory.icon("workflow", 20));
        if (activityIconHolder != null) activityIconHolder.getChildren().setAll(IconFactory.icon("history", 20));
        if (step1IconHolder != null) step1IconHolder.getChildren().setAll(IconFactory.compactIcon("compatibility", 15));
        if (step2IconHolder != null) step2IconHolder.getChildren().setAll(IconFactory.compactIcon("database-backup", 15));
        if (step3IconHolder != null) step3IconHolder.getChildren().setAll(IconFactory.compactIcon("snapshot", 15));
        if (step4IconHolder != null) step4IconHolder.getChildren().setAll(IconFactory.compactIcon("preserve", 15));
        if (step5IconHolder != null) step5IconHolder.getChildren().setAll(IconFactory.compactIcon("installer", 15));
        if (step6IconHolder != null) step6IconHolder.getChildren().setAll(IconFactory.compactIcon("restart", 15));
    }

    private void configureTables() {

        // Phase 11: shared table profiles are the single resize authority.

        colVersion.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().version()));
        colPackage.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().installer().getFileName().toString()));
        colSchema.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().databaseSchema() > 0 ? "Schema " + v.getValue().databaseSchema() : "Unknown"));
        colCompatibility.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().compatibility().label()));
        colStatus.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().compatibility().safe() ? "Ready" : "Blocked"));
        colCompatibility.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().removeAll("rollback-status-safe", "rollback-status-blocked");
                if (!empty) getStyleClass().add("Safe".equalsIgnoreCase(value) ? "rollback-status-safe" : "rollback-status-blocked");
            }
        });
        colActions.setCellFactory(column -> new TableCell<>() {
            private final MenuButton actions = new MenuButton("Actions");
            private RollbackService.Candidate candidate;
            {
                actions.getStyleClass().addAll("table-action-menu", "approved-row-action", "safe-rollback-row-actions");
                actions.setGraphic(IconFactory.compactIcon("actions", 15));
                actions.setOnShowing(event -> rebuildMenu());
                IconFactory.decorateActionMenu(actions);
            }
            private void rebuildMenu() {
                actions.getItems().clear();
                if (candidate == null) return;
                MenuItem rollback = new MenuItem("Roll Back to " + candidate.version(), IconFactory.compactIcon("rollback", 15));
                rollback.setDisable(!candidate.compatibility().safe());
                rollback.setOnAction(event -> confirmAndRollback(candidate));
                MenuItem compatibility = new MenuItem("View Compatibility", IconFactory.compactIcon("compatibility", 15));
                compatibility.setOnAction(event -> info("Compatibility", candidate.compatibility().message()));
                MenuItem folder = new MenuItem("Open Package Folder", IconFactory.compactIcon("folder", 15));
                folder.setOnAction(event -> openPackageFolder());
                actions.getItems().setAll(rollback, compatibility, new SeparatorMenuItem(), folder);
            }
            @Override protected void updateItem(Void ignored, boolean empty) {
                super.updateItem(ignored, empty);
                candidate = empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()
                        ? null : getTableView().getItems().get(getIndex());
                if (candidate == null) {
                    actions.hide();
                    actions.getItems().clear();
                    setGraphic(null);
                } else {
                    rebuildMenu();
                    actions.setTooltip(new Tooltip(candidate.compatibility().message()));
                    setGraphic(actions);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        colHistoryTime.setCellValueFactory(v -> new SimpleStringProperty(BusinessClock.formatInstant(v.getValue().timestamp(), "hh:mm a")));
        colHistoryAction.setCellValueFactory(v -> new SimpleStringProperty(pretty(v.getValue().action())));
        colHistoryVersion.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().targetVersion()));
        colHistoryResult.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().result()));
        colHistoryDetail.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().detail()));
    }

    @FXML
    private void refresh() {
        try {
            var candidates = service.candidates();
            candidateTable.getItems().setAll(candidates);
            lblPackageCount.setText(Integer.toString(candidates.size()));
            var history = service.history();
            historyTable.getItems().setAll(history);
            lblHistoryCount.setText(history.size() + (history.size() == 1 ? " event" : " events"));
            lblRecoveryPoint.setText(service.latestRecoveryPoint()
                    .map(path -> path.getFileName().toString())
                    .orElse("No rollback recovery point yet"));
            lblStatus.setText(candidates.isEmpty()
                    ? "No previous installer is currently retained. Import one or download a published version."
                    : "Select a compatible previous version. The current PostgreSQL database will be preserved.");
        } catch (Exception error) {
            lblStatus.setText("Rollback status could not be refreshed: " + rootMessage(error));
        }
    }

    @FXML
    private void importPreviousInstaller() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Previous DSE ERP Installer");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("DSE ERP installers", "*.exe", "*.msi", "*.dmg", "*.pkg"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        var selected = chooser.showOpenDialog(owner());
        if (selected == null) return;
        runTask("Importing rollback package...", () -> service.importPackage(selected.toPath()), candidate -> {
            refresh();
            info("Previous version retained", "DSE ERP " + candidate.version() + " is now available in Safe Rollback.\n\n" + candidate.compatibility().message());
        });
    }

    @FXML
    private void downloadPreviousVersion() {
        OwnedTextInputDialog prompt = new OwnedTextInputDialog("9.0.7");
        if (owner() != null) prompt.initOwner(owner());
        prompt.setTitle("Download Previous Release");
        prompt.setHeaderText("Download a verified DSE ERP release from GitHub");
        prompt.setContentText("Version:");
        prompt.showAndWait().map(String::trim).filter(value -> !value.isBlank()).ifPresent(version ->
                runTask("Downloading and verifying DSE ERP " + version + "...",
                        () -> service.downloadPublishedVersion(version, ignored -> { }),
                        candidate -> {
                            refresh();
                            info("Rollback package ready", "DSE ERP " + candidate.version() + " was downloaded and SHA-256 verified.\n\n" + candidate.compatibility().message());
                        }));
    }

    private void confirmAndRollback(RollbackService.Candidate candidate) {
        if (!candidate.compatibility().safe()) {
            error("Rollback blocked", candidate.compatibility().message());
            return;
        }
        Alert confirm = new OwnedAlert(Alert.AlertType.CONFIRMATION);
        if (owner() != null) confirm.initOwner(owner());
        confirm.setTitle("Safe Application Rollback");
        confirm.setHeaderText("Roll back DSE ERP " + BuildInfo.version() + " → " + candidate.version() + "?");
        confirm.setContentText("Your CURRENT business data will be preserved.\n\n"
                + "✓ PostgreSQL transactions stay current\n"
                + "✓ Sales, purchases, payments and inventory stay current\n"
                + "✓ Attachments, documents and templates stay in the workspace\n"
                + "✓ A verified database safety backup is created first\n"
                + "✓ Config and templates are copied into a recovery point\n\n"
                + "This action changes the application version only. It does NOT restore an older database.");
        ButtonType rollback = new ButtonType("Create Safety Backup & Roll Back", ButtonBar.ButtonData.OK_DONE);
        confirm.getButtonTypes().setAll(ButtonType.CANCEL, rollback);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != rollback) return;

        runTask("Creating safety backup and recovery point...",
                () -> service.prepareRollback(candidate),
                preparation -> {
                    Alert ready = new OwnedAlert(Alert.AlertType.CONFIRMATION);
                    if (owner() != null) ready.initOwner(owner());
                    ready.setTitle("Rollback Ready");
                    ready.setHeaderText("Safety checks completed successfully");
                    ready.setContentText("Recovery point: " + preparation.id()
                            + "\nDatabase backup: " + preparation.databaseBackup().getFileName()
                            + "\n\nDSE ERP will close, install version " + preparation.targetVersion()
                            + ", and restart while keeping the current database.");
                    ButtonType restart = new ButtonType("Roll Back & Restart", ButtonBar.ButtonData.OK_DONE);
                    ready.getButtonTypes().setAll(ButtonType.CANCEL, restart);
                    if (ready.showAndWait().orElse(ButtonType.CANCEL) == restart) {
                        try {
                            service.launch(preparation);
                            Platform.exit();
                        } catch (Exception error) {
                            error("Unable to start rollback", rootMessage(error));
                        }
                    } else {
                        refresh();
                    }
                });
    }

    @FXML
    private void openPackageFolder() {
        openFolder(service.packagesFolder());
    }

    @FXML
    private void openRecoveryFolder() {
        openFolder(service.recoveryFolder());
    }

    @FXML
    private void openFullRecovery() {
        NavigationManager.getInstance().loadPage("/fxml/pages/BackupRestore.fxml");
    }

    private <T> void runTask(String message, Work<T> work, java.util.function.Consumer<T> success) {
        ProgressIndicator indicator = new ProgressIndicator();
        Label text = new Label(message);
        text.setWrapText(true);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(14, indicator, text);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(420);
        Dialog<Void> dialog = new org.example.util.OwnedDialog<>();
        if (owner() != null) dialog.initOwner(owner());
        dialog.setTitle("Safe Rollback");
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.run(); }
        };
        task.setOnSucceeded(event -> {
            dialog.close();
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            dialog.close();
            error("Safe rollback operation failed", rootMessage(task.getException()));
        });
        task.setOnCancelled(event -> dialog.close());
        dialog.setOnShown(event -> Thread.ofVirtual().name("dse-safe-rollback").start(task));
        dialog.show();
    }

    private void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
            Path absolute = folder.toAbsolutePath().normalize();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

            // Packaged runtimes can report Desktop as unsupported even though the
            // operating system has a perfectly valid file manager. Prefer the
            // native file-manager command and keep Desktop as a portable fallback.
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", absolute.toString()).start();
                return;
            }
            if (os.contains("mac")) {
                new ProcessBuilder("open", absolute.toString()).start();
                return;
            }
            if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
                new ProcessBuilder("xdg-open", absolute.toString()).start();
                return;
            }
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(absolute.toFile());
                return;
            }

            info("Recovery folder ready", "The folder exists, but the operating system did not expose a file-manager launcher.\n\n" + absolute);
        } catch (Exception error) {
            error("Unable to open folder", rootMessage(error) + "\n\nFolder: " + folder.toAbsolutePath().normalize());
        }
    }

    private Window owner() {
        return candidateTable == null || candidateTable.getScene() == null ? null : candidateTable.getScene().getWindow();
    }

    private void info(String header, String message) {
        Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        if (owner() != null) alert.initOwner(owner());
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private void error(String header, String message) {
        Alert alert = new OwnedAlert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (owner() != null) alert.initOwner(owner());
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private static String pretty(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "Unknown error";
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @FunctionalInterface
    private interface Work<T> { T run() throws Exception; }
}
