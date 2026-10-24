package org.example.server.webui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
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
import org.example.server.master.RoleMasterService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "admin/roles", layout = MainAppLayout.class)
@PageTitle("Role Master Management | DSE ERP")
@PermitAll
public class RoleManagementView extends VerticalLayout {

    private final RoleMasterService roleMasterService;
    private final JpaNativeRepository jdbc;
    private final Grid<RoleMasterService.RoleDefinition> grid = new Grid<>();

    public RoleManagementView(RoleMasterService roleMasterService, JpaNativeRepository jdbc) {
        this.roleMasterService = roleMasterService;
        this.jdbc = jdbc;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Enterprise Role Directory & Authorities");
        title.getStyle().set("margin", "0");

        Button newBtn = new Button("New Custom Role", VaadinIcon.PLUS.create(), e -> openEditDialog());
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button permBtn = new Button("Open Permission Matrix", VaadinIcon.GRID.create(), e -> {
            getUI().ifPresent(ui -> ui.navigate(PermissionMatrixView.class));
        });

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshGrid());

        header.add(title, newBtn, permBtn, refreshBtn);
        header.expand(title);
        add(header);

        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(RoleMasterService.RoleDefinition::code).setHeader("Security Identity").setAutoWidth(true).setSortable(true);
        grid.addColumn(RoleMasterService.RoleDefinition::displayName).setHeader("Display Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(RoleMasterService.RoleDefinition::description).setHeader("Description").setFlexGrow(1);
        grid.addColumn(r -> r.userCount() + " assigned user(s)").setHeader("Users").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Span b = new Span(r.active() ? "ACTIVE" : "INACTIVE");
            b.getElement().getThemeList().add("badge");
            if (r.active()) b.getElement().getThemeList().add("success");
            else b.getElement().getThemeList().add("contrast");
            return b;
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Button manage = new Button("Permissions", VaadinIcon.KEY.create(), e -> {
                getUI().ifPresent(ui -> ui.navigate(PermissionMatrixView.class));
            });
            manage.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY);
            return manage;
        }).setHeader("Authority").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            List<RoleMasterService.RoleDefinition> list = roleMasterService.roles(false);
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading roles: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openEditDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Create New Security Role");
        d.setWidth("500px");

        TextField roleName = new TextField("Role Name (e.g. AUDITOR, DISPATCH_HEAD)");
        roleName.setWidthFull();

        TextField description = new TextField("Description");
        description.setWidthFull();

        Checkbox active = new Checkbox("Role Active", true);

        VerticalLayout form = new VerticalLayout(roleName, description, active);
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Create Role", e -> {
            if (roleName.isEmpty()) {
                Notification.show("Role name is required", 2500, Notification.Position.MIDDLE);
                return;
            }
            try {
                String cleanName = roleName.getValue().trim().toUpperCase();
                jdbc.update("""
                    INSERT INTO lookup_master (lookup_type, lookup_code, lookup_value, description, is_active, display_order)
                    VALUES ('ROLE', 'ROL-' || SUBSTRING(MD5(RANDOM()::text), 1, 6), ?, ?, ?, 99)
                    """, cleanName, description.getValue(), active.getValue() ? 1 : 0);
                Notification.show("Role created successfully!", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error creating role: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, save);
        d.open();
    }
}
