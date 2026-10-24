package org.example.server.webui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.authority.ServerBackupService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "admin/backup", layout = MainAppLayout.class)
@PageTitle("Server Backup & Disaster Recovery | DSE ERP")
@PermitAll
public class BackupRestoreView extends VerticalLayout {

    private final ServerBackupService backupService;
    private final Grid<ServerBackupService.BackupFile> grid = new Grid<>();

    public BackupRestoreView(ServerBackupService backupService) {
        this.backupService = backupService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("PostgreSQL Database Backup & Disaster Recovery");
        title.getStyle().set("margin", "0");

        Button createBackupBtn = new Button("Create Instant Database Backup", VaadinIcon.DATABASE.create(), e -> handleCreateBackup());
        createBackupBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button refreshBtn = new Button("Refresh List", VaadinIcon.REFRESH.create(), e -> refreshGrid());

        header.add(title, createBackupBtn, refreshBtn);
        header.expand(title);
        add(header);

        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(ServerBackupService.BackupFile::name).setHeader("Backup File Name").setFlexGrow(1).setSortable(true);
        grid.addColumn(b -> formatSize(b.size())).setHeader("Archive Size").setAutoWidth(true);
        grid.addColumn(ServerBackupService.BackupFile::createdAt).setHeader("Created Timestamp").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(b -> {
            Span s = new Span(b.source());
            s.getElement().getThemeList().add("badge");
            if ("SERVER".equalsIgnoreCase(b.source())) s.getElement().getThemeList().add("primary");
            else s.getElement().getThemeList().add("contrast");
            return s;
        }).setHeader("Origin").setAutoWidth(true);

        grid.addComponentColumn(b -> {
            Button verifyBtn = new Button("Validate Archive", VaadinIcon.CHECK_CIRCLE.create(), e -> handleValidate(b));
            verifyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);
            return verifyBtn;
        }).setHeader("Integrity Check").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            List<ServerBackupService.BackupFile> list = backupService.list();
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading backup files: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleCreateBackup() {
        try {
            ServerBackupService.BackupFile file = backupService.create("WEB_MANUAL");
            Notification.show("Backup archive created successfully: " + file.name() + " (" + formatSize(file.size()) + ")", 4000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshGrid();
        } catch (Exception ex) {
            Notification.show("Backup creation failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleValidate(ServerBackupService.BackupFile file) {
        if (file == null) return;
        try {
            ServerBackupService.Validation validation = backupService.validate(file.name());
            if (validation.valid()) {
                Notification.show("Integrity Verified: " + file.name() + " is valid and ready for full disaster recovery restore.", 4000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                Notification.show("Validation Warning: " + validation.message(), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        } catch (Exception ex) {
            Notification.show("Verification failed: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
