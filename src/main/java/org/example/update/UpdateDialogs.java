package org.example.update;

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

public final class UpdateDialogs {
    private UpdateDialogs() {}

    public static void checkForUpdates(Window owner, boolean quietWhenCurrent) {
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
            ConfigManager.set("update.lastChecked", java.time.Instant.now().toString());
            if (service.isNewer(release)) showRelease(owner, service, release);
            else if (!quietWhenCurrent) info(owner, "You are up to date", "DSE ERP " + service.currentVersion() + " is the latest available version.");
        });
        task.setOnFailed(e -> {
            checking.close();
            error(owner, "Update check failed", rootMessage(task.getException()));
        });
        task.setOnCancelled(e -> checking.close());
        checking.setOnShown(e -> Thread.ofVirtual().name("erp-update-check").start(task));
        checking.show();
    }

    public static void showRelease(Window owner, UpdateService service, UpdateRelease release) {
        Label badge = new Label("NEW RELEASE"); badge.getStyleClass().add("update-badge");
        Label title = new Label("DSE ERP " + release.version()); title.getStyleClass().add("update-release-title");
        TextArea notes = new TextArea(release.notes().isBlank() ? "This release does not include release notes." : release.notes());
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
                UpdateHistoryStore.append(release.version().toString(), ConfigManager.get("update.channel", "STABLE"), "READY", "Installer=" + file.getFileName() + "; Backup=" + backup.getFileName());
                return file;
            }
        };
        bar.progressProperty().bind(task.progressProperty()); detail.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            dialog.close();
            Path installer = task.getValue();
            Alert ready = new Alert(Alert.AlertType.CONFIRMATION);
            ready.initOwner(owner); ready.setTitle("Update Ready to Install"); ready.setHeaderText("DSE ERP " + release.version() + " is ready");
            ready.setContentText("A verified installer and safety backup are ready. The ERP will close after opening the installer.\n\nInstaller: " + installer.getFileName());
            ButtonType install = new ButtonType("Install Now", ButtonBar.ButtonData.OK_DONE);
            ready.getButtonTypes().setAll(ButtonType.CANCEL, install);
            ready.showAndWait().ifPresent(b -> {
                if (b == install) try {
                    service.launchInstaller(installer);
                    UpdateHistoryStore.append(release.version().toString(), ConfigManager.get("update.channel", "STABLE"), "INSTALLER_STARTED", installer.toString());
                    Platform.exit();
                } catch (Exception ex) { error(owner, "Unable to start installer", rootMessage(ex)); }
            });
        });
        task.setOnFailed(e -> { dialog.close(); UpdateHistoryStore.append(release.version().toString(), ConfigManager.get("update.channel", "STABLE"), "FAILED", rootMessage(task.getException())); error(owner, "Update preparation failed", rootMessage(task.getException())); });
        dialog.setOnCloseRequest(e -> task.cancel());
        dialog.setOnShown(e -> Thread.ofVirtual().name("erp-update-download").start(task));
        dialog.show();
    }

    public static void showHistory(Window owner) {
        TableView<UpdateHistoryStore.Entry> table = new TableView<>();
        TableColumn<UpdateHistoryStore.Entry,String> version = column("Version", e -> e.version());
        TableColumn<UpdateHistoryStore.Entry,String> installed = column("Date & Time", e -> DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault()).format(e.timestamp()));
        TableColumn<UpdateHistoryStore.Entry,String> channel = column("Channel", UpdateHistoryStore.Entry::channel);
        TableColumn<UpdateHistoryStore.Entry,String> result = column("Result", UpdateHistoryStore.Entry::result);
        TableColumn<UpdateHistoryStore.Entry,String> detail = column("Details", UpdateHistoryStore.Entry::detail);
        table.getColumns().addAll(version, installed, channel, result, detail);
        table.getItems().setAll(UpdateHistoryStore.read()); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Dialog<Void> dialog = baseDialog(owner, "Update History", table, 920, 560); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.showAndWait();
    }

    public static void showOfflineUpdate(Window owner) {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Select DSE ERP Update Package");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Installer packages", "*.exe", "*.msi", "*.dmg", "*.pkg"), new FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File selected = chooser.showOpenDialog(owner); if (selected == null) return;
        TextInputDialog checksumDialog = new TextInputDialog(); checksumDialog.initOwner(owner); checksumDialog.setTitle("Verify Offline Update"); checksumDialog.setHeaderText("Optional SHA-256 checksum"); checksumDialog.setContentText("Paste the published SHA-256 checksum, or leave blank only for a trusted local package:");
        checksumDialog.showAndWait().ifPresent(checksum -> {
            try {
                UpdateService service = new UpdateService(); Path file = service.verifyOfflinePackage(selected.toPath(), checksum.trim()); Path backup = service.createPreUpdateBackup();
                UpdateHistoryStore.append("OFFLINE", "OFFLINE", "READY", "Installer=" + file.getFileName() + "; Backup=" + backup.getFileName());
                Alert ready = new Alert(Alert.AlertType.CONFIRMATION, "The package is ready and a safety backup was created. Open the installer now?", ButtonType.CANCEL, ButtonType.OK); ready.initOwner(owner); ready.setHeaderText("Offline update package verified");
                if (ready.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) { service.launchInstaller(file); Platform.exit(); }
            } catch (Exception ex) { error(owner, "Offline update failed", rootMessage(ex)); }
        });
    }

    public static void showSystemHealth(Window owner) {
        GridPane grid = new GridPane(); grid.setHgap(24); grid.setVgap(12); grid.setPadding(new Insets(18));
        String[][] rows = {
                {"Application Version", ConfigManager.get("app.version", UpdateService.DEFAULT_VERSION)},
                {"Database Schema", String.valueOf(BackupManager.CURRENT_SCHEMA_VERSION)},
                {"Database File", org.example.config.ConfigManager.getDatabasePath().toString()},
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
    private static <T> Dialog<T> baseDialog(Window owner, String title, javafx.scene.Node content, double width, double height) { Dialog<T> d=new Dialog<>(); if(owner!=null)d.initOwner(owner); d.setTitle(title); d.getDialogPane().setContent(content); d.getDialogPane().setPrefSize(width,height); d.getDialogPane().getStyleClass().add("update-dialog"); return d; }
    private static void info(Window owner,String header,String message){Alert a=new Alert(Alert.AlertType.INFORMATION,message,ButtonType.OK);if(owner!=null)a.initOwner(owner);a.setHeaderText(header);a.showAndWait();}
    private static void error(Window owner,String header,String message){Alert a=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK);if(owner!=null)a.initOwner(owner);a.setHeaderText(header);a.showAndWait();}
    private static String safeBackupCount() { try { return BackupManager.countValidBackups() + " valid backup(s)"; } catch (Exception e) { return "Unavailable: " + rootMessage(e); } }
    private static String rootMessage(Throwable t){if(t==null)return "Unknown error";while(t.getCause()!=null)t=t.getCause();return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    private static String humanSize(long bytes){if(bytes<=0)return "Unknown";double value=bytes;String[] units={"B","KB","MB","GB"};int i=0;while(value>=1024&&i<units.length-1){value/=1024;i++;}return String.format(Locale.ROOT,"%.1f %s",value,units[i]);}
}
