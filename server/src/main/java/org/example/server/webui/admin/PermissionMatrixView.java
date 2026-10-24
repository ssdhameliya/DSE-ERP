package org.example.server.webui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.admin.AdminDtos;
import org.example.server.admin.AdminService;
import org.example.server.webui.MainAppLayout;

import java.util.*;

@Route(value = "admin/permissions", layout = MainAppLayout.class)
@PageTitle("Role-Based Permission Matrix | DSE ERP")
@PermitAll
public class PermissionMatrixView extends VerticalLayout {

    public static class PermissionItem {
        private final long id;
        private final String module;
        private final String action;
        private final String description;
        private boolean allowed;

        public PermissionItem(long id, String module, String action, String description, boolean allowed) {
            this.id = id;
            this.module = module;
            this.action = action;
            this.description = description;
            this.allowed = allowed;
        }

        public long getId() { return id; }
        public String getModule() { return module; }
        public String getAction() { return action; }
        public String getDescription() { return description; }
        public boolean isAllowed() { return allowed; }
        public void setAllowed(boolean allowed) { this.allowed = allowed; }
    }

    private final AdminService adminService;
    private final ComboBox<String> roleCombo = new ComboBox<>("Select Security Role");
    private final Grid<PermissionItem> grid = new Grid<>();
    private final TextField searchField = new TextField();

    private final List<PermissionItem> currentItems = new ArrayList<>();
    private long currentRowVersion = 0L;

    public PermissionMatrixView(AdminService adminService) {
        this.adminService = adminService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Enterprise Role-Based Permission Matrix (RBAC)");
        title.getStyle().set("margin", "0");

        roleCombo.setWidth("280px");
        roleCombo.addValueChangeListener(e -> loadPermissions(e.getValue()));

        Button saveBtn = new Button("Save Changes", VaadinIcon.CHECK.create(), e -> savePermissions());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button grantAllBtn = new Button("Grant All", VaadinIcon.CHECK_SQUARE_O.create(), e -> toggleAll(true));
        Button revokeAllBtn = new Button("Revoke All", VaadinIcon.CLOSE_SMALL.create(), e -> toggleAll(false));

        header.add(title, roleCombo, grantAllBtn, revokeAllBtn, saveBtn);
        header.expand(title);
        add(header);

        searchField.setPlaceholder("Filter by Module name or Action description...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("350px");
        searchField.addValueChangeListener(e -> filterGrid());
        add(searchField);

        configureGrid();
        add(grid);

        loadRoles();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(PermissionItem::getModule).setHeader("Module").setAutoWidth(true).setSortable(true);
        grid.addColumn(PermissionItem::getAction).setHeader("Action").setAutoWidth(true).setSortable(true);
        grid.addColumn(PermissionItem::getDescription).setHeader("Permission Scope & Description").setFlexGrow(1);

        grid.addComponentColumn(p -> {
            Checkbox cb = new Checkbox();
            cb.setValue(p.isAllowed());
            boolean isAdmin = "ADMIN".equalsIgnoreCase(roleCombo.getValue());
            if (isAdmin) {
                cb.setEnabled(false);
            } else {
                cb.addValueChangeListener(ev -> p.setAllowed(ev.getValue()));
            }
            return cb;
        }).setHeader("Allowed").setAutoWidth(true);
    }

    private void loadRoles() {
        try {
            List<String> roleCodes = adminService.roles().stream().map(AdminDtos.RoleDto::code).toList();
            roleCombo.setItems(roleCodes);
            if (!roleCodes.isEmpty()) {
                roleCombo.setValue(roleCodes.contains("SALES") ? "SALES" : roleCodes.getFirst());
            }
        } catch (Exception ex) {
            Notification.show("Error loading roles: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void loadPermissions(String role) {
        if (role == null || role.isBlank()) return;
        try {
            AdminDtos.PermissionSetDto set = adminService.permissionSet(role);
            currentRowVersion = set.rowVersion();
            currentItems.clear();
            if (set.permissions() != null) {
                for (AdminDtos.PermissionDto dto : set.permissions()) {
                    currentItems.add(new PermissionItem(dto.id(), dto.module(), dto.action(), dto.description(), dto.allowed()));
                }
            }
            filterGrid();
        } catch (Exception ex) {
            Notification.show("Error loading permissions for " + role + ": " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void filterGrid() {
        String q = searchField.getValue() != null ? searchField.getValue().trim().toLowerCase() : "";
        if (q.isBlank()) {
            grid.setItems(currentItems);
        } else {
            grid.setItems(currentItems.stream().filter(i ->
                    i.getModule().toLowerCase().contains(q) ||
                    i.getAction().toLowerCase().contains(q) ||
                    (i.getDescription() != null && i.getDescription().toLowerCase().contains(q))
            ).toList());
        }
    }

    private void toggleAll(boolean allowed) {
        if ("ADMIN".equalsIgnoreCase(roleCombo.getValue())) return;
        for (PermissionItem item : currentItems) {
            item.setAllowed(allowed);
        }
        grid.getDataProvider().refreshAll();
    }

    private void savePermissions() {
        String role = roleCombo.getValue();
        if ("ADMIN".equalsIgnoreCase(role)) {
            Notification.show("The ADMIN superuser role possesses immutable system permissions by default.", 3500, Notification.Position.MIDDLE);
            return;
        }

        try {
            List<AdminDtos.PermissionSave> saves = currentItems.stream()
                    .map(i -> new AdminDtos.PermissionSave(i.getId(), i.isAllowed()))
                    .toList();

            adminService.savePermissions(new AdminDtos.PermissionSaveRequest(role, saves, currentRowVersion));
            Notification.show("Permission matrix for role " + role + " saved successfully!", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadPermissions(role);
        } catch (Exception ex) {
            Notification.show("Error saving permissions: " + ex.getMessage(), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
