package org.example.update;

import org.example.util.BusinessClock;
import org.example.util.IconFactory;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.OwnedTextInputDialog;
import org.example.util.PopupTableWorkspace;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.example.backup.BackupManager;
import org.example.config.ConfigManager;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateDialogs {
    private UpdateDialogs() {}

    public static void showWhatsNew(Window owner) {
        showWhatsNew(owner, false);
    }

    public static void showWhatsNewOnce(Window owner) {
        String version = BuildInfo.version();
        String pending = ConfigManager.get("update.releaseNotesPending", "").trim();
        String seen = ConfigManager.get("update.releaseNotesSeen", "").trim();
        if (!version.equals(pending) && version.equals(seen)) return;
        showWhatsNew(owner, true);
    }

    private static void showWhatsNew(Window owner, boolean markSeen) {
        String version = BuildInfo.version();
        Task<String> task = new Task<>() {
            @Override protected String call() {
                try {
                    String ownerName = ConfigManager.get("update.github.owner", UpdateService.DEFAULT_GITHUB_OWNER).trim();
                    String repository = ConfigManager.get("update.github.repository", UpdateService.DEFAULT_GITHUB_REPOSITORY).trim();
                    UpdateRelease release = new GitHubReleaseClient().byVersion(ownerName, repository, version);
                    return ReleaseHighlights.resolve(version, release.notes());
                } catch (Exception ignored) { }
                return ReleaseHighlights.forVersion(version);
            }
        };
        task.setOnSucceeded(event -> {
            TextArea notes = new TextArea(task.getValue());
            notes.setEditable(false);
            notes.setWrapText(true);
            notes.setPrefRowCount(18);
            notes.setMinHeight(360);
            notes.setPrefHeight(430);
            notes.setMaxHeight(Double.MAX_VALUE);
            notes.setMaxWidth(Double.MAX_VALUE);
            VBox.setVgrow(notes, Priority.ALWAYS);
            VBox content = new VBox(10, new Label("What’s New in DSE ERP " + version), notes);
            content.setFillWidth(true);
            content.setMinHeight(440);
            content.setPadding(new Insets(8));
            Dialog<Void> dialog = baseDialog(owner, "What’s New", content, 760, 590);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
            if (markSeen) markReleaseNotesSeen(version);
        });
        task.setOnFailed(event -> {
            String fallback = ReleaseHighlights.forVersion(version);
            TextArea notes = new TextArea(fallback);
            notes.setEditable(false);
            notes.setWrapText(true);
            notes.setMinHeight(400);
            notes.setPrefHeight(460);
            notes.setMaxHeight(Double.MAX_VALUE);
            notes.setMaxWidth(Double.MAX_VALUE);
            Dialog<Void> dialog = baseDialog(owner, "What’s New", notes, 760, 590);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
            if (markSeen) markReleaseNotesSeen(version);
        });
        Thread.ofVirtual().name("erp-release-notes").start(task);
    }

    private static void markReleaseNotesSeen(String version) {
        ConfigManager.set("update.releaseNotesSeen", version);
        if (version.equals(ConfigManager.get("update.releaseNotesPending", "").trim())) {
            ConfigManager.set("update.releaseNotesPending", "");
        }
    }

    public static void checkForUpdates(Window owner, boolean quietWhenCurrent) {
        checkForUpdates(owner, quietWhenCurrent, null);
    }

    public static void checkForUpdates(Window owner, boolean quietWhenCurrent, Runnable stateChanged) {
        org.example.service.PermissionService.require("APPLICATION_UPDATES.CHECK", "check for application updates");
        UpdateService service = new UpdateService();
        ProgressIndicator indicator = new ProgressIndicator();
        Label message = new Label("Checking GitHub Releases for the latest DSE ERP version...");
        VBox content = new VBox(18, indicator, message);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPadding(new Insets(28));
        Dialog<Void> checking = baseDialog(owner, "Check for Updates", content, 460, 230);
        checking.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Task<UpdateRelease> task = new Task<>() {
            @Override protected UpdateRelease call() throws Exception { return service.check(); }
        };
        task.setOnSucceeded(e -> {
            checking.close();
            UpdateRelease release = task.getValue();
            UpdateState.recordSuccess(release);
            if (stateChanged != null) stateChanged.run();
            if (service.isNewer(release)) showRelease(owner, service, release);
            else if (!quietWhenCurrent) info(owner, "You are up to date", "DSE ERP " + service.currentVersion() + " is the latest available version.");
        });
        task.setOnFailed(e -> {
            checking.close();
            UpdateState.recordFailure(task.getException());
            if (stateChanged != null) stateChanged.run();
            error(owner, "Update check failed", rootMessage(task.getException()));
        });
        task.setOnCancelled(e -> checking.close());
        checking.setOnShown(e -> Thread.ofVirtual().name("erp-update-check").start(task));
        checking.show();
    }

    public static void showRelease(Window owner, UpdateService service, UpdateRelease release) {
        Label badge = new Label("NEW RELEASE"); badge.getStyleClass().add("update-badge");
        Label title = new Label("DSE ERP " + release.version()); title.getStyleClass().add("update-release-title");
        TextArea notes = new TextArea(ReleaseHighlights.resolve(release.version().toString(), release.notes()));
        notes.setEditable(false); notes.setWrapText(true); notes.setPrefRowCount(9);
        String size = PlatformPackage.select(release).map(a -> humanSize(a.size())).orElse("Installer not found");
        GridPane facts = new GridPane(); facts.setHgap(28); facts.setVgap(6);
        facts.addRow(0, new Label("Current Version"), new Label(service.currentVersion()), new Label("Latest Version"), new Label(release.version().toString()), new Label("Release Size"), new Label(size));
        VBox content = new VBox(12, badge, title, notes, facts); content.setPadding(new Insets(8));
        Dialog<ButtonType> dialog = baseDialog(owner, "New Version Available", content, 720, 560);
        ButtonType releaseNotes = new ButtonType("Open GitHub Release", ButtonBar.ButtonData.LEFT);
        ButtonType later = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType update = new ButtonType("Update Now", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(releaseNotes, later, update);
        dialog.setResultConverter(b -> b);
        dialog.showAndWait().ifPresent(result -> {
            if (result == update) downloadAndPrepare(owner, service, release);
            else if (result == releaseNotes) {
                try { service.openRelease(release); } catch (Exception ex) { error(owner, "Unable to open release", rootMessage(ex)); }
            }
        });
    }

    private static volatile String deferredCompatibleUpdateVersion = "";

    public static boolean isCompatibleUpdateDeferredForSession() {
        return deferredCompatibleUpdateVersion != null && !deferredCompatibleUpdateVersion.isBlank();
    }

    /**
     * Pre-login Shared Client update path for a newer server that still supports this desktop.
     * Choosing Not Now resumes the normal startup/login transition. The deferral is intentionally
     * process-local so the user is offered the update again on the next application launch.
     */
    public static void offerCompatibleClientUpdate(Window owner, String availableVersion,
                                                   String minimumSupportedDesktopVersion,
                                                   Runnable continueStartup) {
        String version = availableVersion == null ? "" : availableVersion.trim();
        Runnable resume = () -> {
            if (!version.isBlank()) deferredCompatibleUpdateVersion = version;
            if (continueStartup != null) continueStartup.run();
        };
        if (version.isBlank() || version.equals(BuildInfo.version()) || version.equals(deferredCompatibleUpdateVersion)) {
            if (continueStartup != null) continueStartup.run();
            return;
        }
        offerPreLoginClientUpdate(owner, version, minimumSupportedDesktopVersion, false, resume);
    }

    /** Mandatory pre-login update used during normal application startup. */
    public static void offerRequiredClientUpdateAtStartup(Window owner, String requiredVersion) {
        offerPreLoginClientUpdate(owner, requiredVersion, requiredVersion, true, Platform::exit);
    }

    /**
     * Required-update dialog retained for Setup/Settings connection testing. Declining it keeps
     * the current setup screen open instead of exiting the application.
     */
    public static void offerRequiredClientUpdate(Window owner, String requiredVersion) {
        offerPreLoginClientUpdate(owner, requiredVersion, requiredVersion, true, null);
    }

    private static void offerPreLoginClientUpdate(Window owner, String targetVersion,
                                                  String minimumSupportedDesktopVersion,
                                                  boolean required, Runnable onNoUpdate) {
        Runnable fallback = once(onNoUpdate);
        String version = targetVersion == null ? "" : targetVersion.trim();
        if (version.isBlank()) {
            error(owner, required ? "Update required" : "Update available",
                    required
                            ? "The company server requires a newer DSE ERP desktop, but its version could not be determined."
                            : "The company server has a newer compatible DSE ERP desktop, but its version could not be determined.");
            runIfPresent(fallback);
            return;
        }

        ButtonType secondary = new ButtonType(required && onNoUpdate != null ? "Exit" : "Not Now",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType update = new ButtonType("Download & Install " + version, ButtonBar.ButtonData.OK_DONE);
        String minimum = minimumSupportedDesktopVersion == null ? "" : minimumSupportedDesktopVersion.trim();
        String message;
        String header;
        if (required) {
            header = "Desktop update required";
            message = "The company server is running DSE ERP " + version + ", but this desktop is "
                    + BuildInfo.version() + ".\n\n"
                    + "This desktop is below the server's supported compatibility range. "
                    + "DSE ERP must be updated before login.";
        } else {
            header = "Desktop update available";
            message = "The company server is running DSE ERP " + version + ", while this desktop is "
                    + BuildInfo.version() + ".\n\n"
                    + (minimum.isBlank() ? "The server confirms this desktop is still compatible."
                    : "The server currently supports desktop " + minimum + " or newer.")
                    + " You can update now or choose Not Now and continue to login with this compatible version.";
        }

        Alert alert = new OwnedAlert(required ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
                message, secondary, update);
        if (owner != null) alert.initOwner(owner);
        alert.setHeaderText(header);
        if (alert.showAndWait().orElse(secondary) != update) {
            runIfPresent(fallback);
            return;
        }

        loadPreLoginRelease(owner, version, required, fallback);
    }

    private static void loadPreLoginRelease(Window owner, String version, boolean required, Runnable onNoUpdate) {
        ProgressIndicator indicator = new ProgressIndicator();
        Label message = new Label("Loading the official DSE ERP " + version + " release...");
        VBox content = new VBox(18, indicator, message);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPadding(new Insets(28));
        Dialog<Void> loading = baseDialog(owner, required ? "Required Update" : "Available Update", content, 500, 240);
        loading.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Task<UpdateRelease> task = new Task<>() {
            @Override protected UpdateRelease call() throws Exception {
                String ownerName = ConfigManager.get("update.github.owner", UpdateService.DEFAULT_GITHUB_OWNER).trim();
                String repository = ConfigManager.get("update.github.repository", UpdateService.DEFAULT_GITHUB_REPOSITORY).trim();
                return new GitHubReleaseClient().byVersion(ownerName, repository, version);
            }
        };
        task.setOnSucceeded(event -> {
            loading.close();
            UpdateRelease release = task.getValue();
            if (!release.version().toString().equals(version)) {
                error(owner, required ? "Required update unavailable" : "Update unavailable",
                        "The published release does not match the company server version. Expected: " + version
                                + "; published: " + release.version() + ".");
                runIfPresent(onNoUpdate);
                return;
            }
            downloadPreLoginClientUpdate(owner, new UpdateService(), release, required, onNoUpdate);
        });
        task.setOnFailed(event -> {
            loading.close();
            error(owner, required ? "Required update unavailable" : "Update unavailable", rootMessage(task.getException()));
            runIfPresent(onNoUpdate);
        });
        task.setOnCancelled(event -> {
            loading.close();
            runIfPresent(onNoUpdate);
        });
        loading.setOnCloseRequest(event -> { if (!task.isDone()) task.cancel(); });
        loading.setOnShown(event -> Thread.ofVirtual().name("erp-prelogin-update-check").start(task));
        loading.show();
    }

    private static void downloadPreLoginClientUpdate(Window owner, UpdateService service, UpdateRelease release,
                                                     boolean required, Runnable onNoUpdate) {
        UpdateRelease.Asset asset;
        try { asset = service.assetFor(release); }
        catch (Exception exception) {
            error(owner, "Installer unavailable", rootMessage(exception));
            runIfPresent(onNoUpdate);
            return;
        }

        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label status = new Label("Downloading " + asset.name());
        Label detail = new Label("Preparing download...");
        VBox content = new VBox(14, status, bar, detail);
        content.setPadding(new Insets(18));
        Dialog<Void> dialog = baseDialog(owner, required ? "Downloading Required Update" : "Downloading Update", content, 580, 270);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Task<Path> task = new Task<>() {
            @Override protected Path call() throws Exception {
                updateMessage("Downloading official installer...");
                Path file = service.download(asset, progress -> updateProgress(progress, 1));
                updateMessage("Verifying SHA-256 checksum...");
                String checksum = service.expectedChecksum(release, asset.name());
                if (checksum.isBlank()) {
                    throw new SecurityException("The GitHub Release must include checksums.txt with a SHA-256 entry for " + asset.name() + ".");
                }
                ChecksumVerifier.verify(file, checksum);
                updateMessage("Installer verified.");
                return file;
            }
        };
        bar.progressProperty().bind(task.progressProperty());
        detail.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            dialog.close();
            Path installer = task.getValue();
            ButtonType cancel = new ButtonType(required ? "Exit" : "Not Now", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType install = new ButtonType("Install & Restart", ButtonBar.ButtonData.OK_DONE);
            Alert ready = new OwnedAlert(Alert.AlertType.CONFIRMATION,
                    "The official DSE ERP " + release.version() + " installer was downloaded and SHA-256 verified.\n\n"
                            + (required
                            ? "DSE ERP must close and install this version before login."
                            : "Install now, or choose Not Now to continue to login with the currently supported desktop."),
                    cancel, install);
            if (owner != null) ready.initOwner(owner);
            ready.setHeaderText(required ? "Required update verified" : "Update verified");
            if (ready.showAndWait().orElse(cancel) != install) {
                runIfPresent(onNoUpdate);
                return;
            }
            try {
                service.launchInstaller(installer, release.version().toString());
                Platform.exit();
            } catch (Exception exception) {
                error(owner, "Unable to start installer", rootMessage(exception));
                runIfPresent(onNoUpdate);
            }
        });
        task.setOnFailed(event -> {
            dialog.close();
            error(owner, "Update preparation failed", rootMessage(task.getException()));
            runIfPresent(onNoUpdate);
        });
        task.setOnCancelled(event -> {
            dialog.close();
            runIfPresent(onNoUpdate);
        });
        dialog.setOnCloseRequest(event -> { if (!task.isDone()) task.cancel(); });
        dialog.setOnShown(event -> Thread.ofVirtual().name("erp-prelogin-update-download").start(task));
        dialog.show();
    }

    private static Runnable once(Runnable action) {
        if (action == null) return null;
        AtomicBoolean invoked = new AtomicBoolean(false);
        return () -> {
            if (invoked.compareAndSet(false, true)) action.run();
        };
    }

    private static void runIfPresent(Runnable action) {
        if (action != null) action.run();
    }

    private static void downloadAndPrepare(Window owner, UpdateService service, UpdateRelease release) {
        UpdateRelease.Asset asset;
        try { asset = service.assetFor(release); }
        catch (Exception ex) { error(owner, "Installer unavailable", rootMessage(ex)); return; }

        ProgressBar bar = new ProgressBar(0); bar.setMaxWidth(Double.MAX_VALUE);
        Label status = new Label("Downloading " + asset.name());
        Label detail = new Label("Preparing download...");
        VBox content = new VBox(14, status, bar, detail); content.setPadding(new Insets(18));
        Dialog<Void> dialog = baseDialog(owner, "Downloading Update", content, 560, 260);
        ButtonType cancel = ButtonType.CANCEL; dialog.getDialogPane().getButtonTypes().add(cancel);

        Task<Path> task = new Task<>() {
            @Override protected Path call() throws Exception {
                updateMessage("Downloading installer...");
                Path file = service.download(asset, p -> updateProgress(p, 1));
                updateMessage("Verifying SHA-256 checksum...");
                String checksum = service.expectedChecksum(release, asset.name());
                if (checksum.isBlank()) throw new SecurityException("The GitHub Release must include checksums.txt with a SHA-256 entry for " + asset.name() + ".");
                ChecksumVerifier.verify(file, checksum);
                updateMessage("Creating pre-update database backup...");
                Path backup = service.createPreUpdateBackup();
                UpdateHistoryStore.append(release.version().toString(), ConfigManager.getEffectiveUpdateChannel(), "READY", "Installer=" + file.getFileName() + "; Backup=" + backup.getFileName());
                return file;
            }
        };
        bar.progressProperty().bind(task.progressProperty()); detail.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            dialog.close();
            Path installer = task.getValue();
            Alert ready = new OwnedAlert(Alert.AlertType.CONFIRMATION);
            ready.initOwner(owner); ready.setTitle("Update Ready to Install"); ready.setHeaderText("DSE ERP " + release.version() + " is ready");
            ready.setContentText("A verified installer and safety backup are ready. DSE ERP will close, install the update automatically, and restart.\n\nInstaller: " + installer.getFileName());
            ButtonType install = new ButtonType("Install & Restart", ButtonBar.ButtonData.OK_DONE);
            ready.getButtonTypes().setAll(ButtonType.CANCEL, install);
            ready.showAndWait().ifPresent(b -> {
                if (b == install) try {
                    org.example.service.PermissionService.require("APPLICATION_UPDATES.INSTALL", "install an application update");
                    service.launchInstaller(installer, release.version().toString());
                    UpdateHistoryStore.append(release.version().toString(), ConfigManager.getEffectiveUpdateChannel(), "INSTALLER_STARTED", installer.toString());
                    Platform.exit();
                } catch (Exception ex) { error(owner, "Unable to start installer", rootMessage(ex)); }
            });
        });
        task.setOnFailed(e -> { dialog.close(); UpdateHistoryStore.append(release.version().toString(), ConfigManager.getEffectiveUpdateChannel(), "FAILED", rootMessage(task.getException())); error(owner, "Update preparation failed", rootMessage(task.getException())); });
        dialog.setOnCloseRequest(e -> task.cancel());
        dialog.setOnShown(e -> Thread.ofVirtual().name("erp-update-download").start(task));
        dialog.show();
    }

    public static void showHistory(Window owner) {
        TableView<UpdateHistoryStore.Entry> table = new TableView<>();
        PopupTableWorkspace.prepareTable(table, "erp-table-profile-history");
        TableColumn<UpdateHistoryStore.Entry,String> version = column("Version", UpdateHistoryStore.Entry::version);
        TableColumn<UpdateHistoryStore.Entry,String> installed = column("Date & Time", e -> DateTimeFormatter.ofPattern(BusinessClock.datePattern() + ", hh:mm a").withZone(BusinessClock.zone()).format(e.timestamp()));
        TableColumn<UpdateHistoryStore.Entry,String> channel = column("Channel", UpdateHistoryStore.Entry::channel);
        TableColumn<UpdateHistoryStore.Entry,String> result = column("Result", UpdateHistoryStore.Entry::result);
        TableColumn<UpdateHistoryStore.Entry,String> detail = column("Details", UpdateHistoryStore.Entry::detail);
        IconFactory.applyTableHeaderIcon(version, "version");
        IconFactory.applyTableHeaderIcon(installed, "calendar");
        IconFactory.applyTableHeaderIcon(channel, "communication");
        IconFactory.applyTableHeaderIcon(result, "status");
        IconFactory.applyTableHeaderIcon(detail, "notes");
        table.getColumns().addAll(version, installed, channel, result, detail);
        List<UpdateHistoryStore.Entry> history = UpdateHistoryStore.read();
        table.getItems().setAll(history);
        table.setPrefHeight(390);
        String lastUpdated = history.isEmpty() ? "No history" : DateTimeFormatter.ofPattern(BusinessClock.datePattern()).withZone(BusinessClock.zone()).format(history.getFirst().timestamp());
        HBox metrics = PopupTableWorkspace.metricStrip(
            PopupTableWorkspace.metricCard("Current Version", BuildInfo.version(), "version"),
            PopupTableWorkspace.metricCard("Last Updated", lastUpdated, "calendar"),
            PopupTableWorkspace.metricCard("Channel", ConfigManager.getEffectiveUpdateChannel(), "communication")
        );
        Label footer = PopupTableWorkspace.footerText(history.size()+" update histor"+(history.size()==1?"y record":"y records"));
        VBox content = PopupTableWorkspace.content(metrics, table, footer);
        Dialog<Void> dialog = new OwnedDialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Update History");
        dialog.setHeaderText("View version and release activity recorded on this workstation.");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefHeight(610);
        PopupTableWorkspace.prepareDialog(dialog, 980);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    public static void showOfflineUpdate(Window owner) {
        org.example.service.PermissionService.require("APPLICATION_UPDATES.INSTALL", "install an offline update package");
        FileChooser chooser = new FileChooser(); chooser.setTitle("Select DSE ERP Update Package");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Installer packages", "*.exe", "*.msi", "*.dmg", "*.pkg"), new FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File selected = chooser.showOpenDialog(owner); if (selected == null) return;
        TextInputDialog checksumDialog = new OwnedTextInputDialog(); checksumDialog.initOwner(owner); checksumDialog.setTitle("Verify Offline Update"); checksumDialog.setHeaderText("Optional SHA-256 checksum"); checksumDialog.setContentText("Paste the published SHA-256 checksum, or leave blank only for a trusted local package:");
        checksumDialog.showAndWait().ifPresent(checksum -> {
            try {
                UpdateService service = new UpdateService(); Path file = service.verifyOfflinePackage(selected.toPath(), checksum.trim()); Path backup = service.createPreUpdateBackup();
                UpdateHistoryStore.append("OFFLINE", "OFFLINE", "READY", "Installer=" + file.getFileName() + "; Backup=" + backup.getFileName());
                Alert ready = new OwnedAlert(Alert.AlertType.CONFIRMATION, "The package is ready and a safety backup was created. Open the installer now?", ButtonType.CANCEL, ButtonType.OK); ready.initOwner(owner); ready.setHeaderText("Offline update package verified");
                if (ready.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) { service.launchInstaller(file, "offline"); Platform.exit(); }
            } catch (Exception ex) { error(owner, "Offline update failed", rootMessage(ex)); }
        });
    }

    public static void showSystemHealth(Window owner) {
        GridPane grid = new GridPane(); grid.setHgap(24); grid.setVgap(12); grid.setPadding(new Insets(18));
        String[][] rows = {
                {"Application Version", BuildInfo.version()},
                {"Database Schema", String.valueOf(BackupManager.CURRENT_SCHEMA_VERSION)},
                {"Database", org.example.config.ConfigManager.getDatabaseDescription()},
                {"Backup Status", safeBackupCount()},
                {"Java Runtime", System.getProperty("java.version")},
                {"Operating System", System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")"},
                {"Update Platform", PlatformPackage.current().name()},
                {"Update Repository", ConfigManager.get("update.github.owner", UpdateService.DEFAULT_GITHUB_OWNER) + "/" + ConfigManager.get("update.github.repository", UpdateService.DEFAULT_GITHUB_REPOSITORY)}
        };
        for (int i=0;i<rows.length;i++) { Label key=new Label(rows[i][0]); key.getStyleClass().add("settings-form-label"); grid.add(key,0,i); Label value=new Label(rows[i][1]); value.setWrapText(true); grid.add(value,1,i); }
        Dialog<Void> dialog = baseDialog(owner, "System Health", grid, 760, 470); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.showAndWait();
    }

    private static <T> TableColumn<T,String> column(String title, java.util.function.Function<T,String> getter) { TableColumn<T,String> c=new TableColumn<>(title); c.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(getter.apply(v.getValue()))); return c; }
    private static <T> Dialog<T> baseDialog(Window owner, String title, javafx.scene.Node content, double width, double height) { Dialog<T> d=new OwnedDialog<>(); if(owner!=null)d.initOwner(owner); d.setTitle(title); d.getDialogPane().setContent(content); d.getDialogPane().setPrefSize(width,height); d.getDialogPane().getStyleClass().add("update-dialog"); return d; }
    private static void info(Window owner,String header,String message){Alert a=new OwnedAlert(Alert.AlertType.INFORMATION,message,ButtonType.OK);if(owner!=null)a.initOwner(owner);a.setHeaderText(header);a.showAndWait();}
    private static void error(Window owner,String header,String message){Alert a=new OwnedAlert(Alert.AlertType.ERROR,message,ButtonType.OK);if(owner!=null)a.initOwner(owner);a.setHeaderText(header);a.showAndWait();}
    private static String safeBackupCount() { try { return BackupManager.countValidBackups() + " valid backup(s)"; } catch (Exception e) { return "Unavailable: " + rootMessage(e); } }
    private static String rootMessage(Throwable t){if(t==null)return "Unknown error";while(t.getCause()!=null)t=t.getCause();return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    private static String humanSize(long bytes){if(bytes<=0)return "Unknown";double value=bytes;String[] units={"B","KB","MB","GB"};int i=0;while(value>=1024&&i<units.length-1){value/=1024;i++;}return String.format(Locale.ROOT,"%.1f %s",value,units[i]);}
}
