package org.example.server.webui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.admin.AdminDtos;
import org.example.server.admin.AdminService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "admin/users", layout = MainAppLayout.class)
@PageTitle("User Access & Security | DSE ERP")
@PermitAll
public class UserAccessView extends VerticalLayout {

    private final AdminService adminService;
    private final Grid<AdminDtos.UserDto> grid = new Grid<>();

    public UserAccessView(AdminService adminService) {
        this.adminService = adminService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("User Management & Enterprise Access Control");
        title.getStyle().set("margin", "0");

        Button newBtn = new Button("Add User Account", VaadinIcon.PLUS.create(), e -> openEditDialog(null));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshGrid());

        header.add(title, newBtn, refreshBtn);
        header.expand(title);
        add(header);

        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(AdminDtos.UserDto::username).setHeader("Username").setAutoWidth(true).setSortable(true);
        grid.addColumn(AdminDtos.UserDto::fullName).setHeader("Full Name").setFlexGrow(1).setSortable(true);
        grid.addColumn(AdminDtos.UserDto::email).setHeader("Email Address").setAutoWidth(true);

        grid.addComponentColumn(u -> {
            Span b = new Span(u.role());
            b.getElement().getThemeList().add("badge");
            if ("ADMIN".equalsIgnoreCase(u.role())) b.getElement().getThemeList().add("error");
            else if ("MANAGER".equalsIgnoreCase(u.role())) b.getElement().getThemeList().add("warning");
            else b.getElement().getThemeList().add("primary");
            return b;
        }).setHeader("Role").setAutoWidth(true);

        grid.addColumn(AdminDtos.UserDto::department).setHeader("Department").setAutoWidth(true);

        grid.addComponentColumn(u -> {
            Span b = new Span(u.active() ? "ACTIVE" : "INACTIVE");
            b.getElement().getThemeList().add("badge");
            if (u.active()) b.getElement().getThemeList().add("success");
            else b.getElement().getThemeList().add("contrast");
            return b;
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(u -> {
            Span b = new Span(u.locked() ? "LOCKED" : "UNLOCKED");
            b.getElement().getThemeList().add("badge");
            if (u.locked()) b.getElement().getThemeList().add("error");
            return b;
        }).setHeader("Security").setAutoWidth(true);

        grid.addComponentColumn(u -> {
            Span b = new Span(u.mfaEnabled() ? "ENABLED" : "OFF");
            b.getElement().getThemeList().add("badge");
            if (u.mfaEnabled()) b.getElement().getThemeList().add("success");
            return b;
        }).setHeader("MFA").setAutoWidth(true);

        grid.addColumn(AdminDtos.UserDto::lastLogin).setHeader("Last Login").setAutoWidth(true);

        grid.addComponentColumn(u -> {
            Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(u));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button lockToggle = new Button(u.locked() ? VaadinIcon.UNLOCK.create() : VaadinIcon.LOCK.create(), e -> handleLockToggle(u));
            lockToggle.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(edit, lockToggle);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            List<AdminDtos.UserDto> users = adminService.users();
            grid.setItems(users);
        } catch (Exception ex) {
            Notification.show("Error loading users: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleLockToggle(AdminDtos.UserDto u) {
        if (u == null) return;
        try {
            boolean targetLocked = !u.locked();
            adminService.saveUser(new AdminDtos.UserSaveRequest(
                    u.id(), u.username(), null, u.fullName(), u.email(), u.role(),
                    u.department(), u.accessLevel(), u.branch(), u.active(),
                    targetLocked, u.mfaEnabled(), u.rowVersion()
            ));
            Notification.show(targetLocked ? "Account locked" : "Account unlocked", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshGrid();
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openEditDialog(AdminDtos.UserDto existing) {
        Dialog d = new Dialog();
        boolean editing = existing != null;
        d.setHeaderTitle(editing ? "Edit User Account (" + existing.username() + ")" : "Create User Account");
        d.setWidth("550px");

        TextField username = new TextField("Username");
        PasswordField password = new PasswordField(editing ? "New Password (leave blank to keep unchanged)" : "Initial Password");
        TextField fullName = new TextField("Full Name");
        TextField email = new TextField("Email Address");

        ComboBox<String> roleCombo = new ComboBox<>("Security Role");
        List<String> roleCodes = adminService.roles().stream().map(AdminDtos.RoleDto::code).toList();
        roleCombo.setItems(roleCodes.isEmpty() ? List.of("ADMIN", "MANAGER", "USER") : roleCodes);
        roleCombo.setValue(editing ? existing.role() : "USER");

        TextField department = new TextField("Department");
        TextField branch = new TextField("Branch Office");

        Checkbox active = new Checkbox("Account Active", true);
        Checkbox mfa = new Checkbox("Mandatory 2FA / MFA", false);

        if (editing) {
            username.setValue(existing.username());
            username.setReadOnly(true);
            fullName.setValue(existing.fullName() != null ? existing.fullName() : "");
            email.setValue(existing.email() != null ? existing.email() : "");
            department.setValue(existing.department() != null ? existing.department() : "");
            branch.setValue(existing.branch() != null ? existing.branch() : "");
            active.setValue(existing.active());
            mfa.setValue(existing.mfaEnabled());
        }

        VerticalLayout form = new VerticalLayout(
                username, password, fullName, email,
                new HorizontalLayout(roleCombo, department),
                branch,
                new HorizontalLayout(active, mfa)
        );
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Save User", e -> {
            if (username.isEmpty() || (!editing && password.isEmpty())) {
                Notification.show("Username and password are required", 2500, Notification.Position.MIDDLE);
                return;
            }
            try {
                adminService.saveUser(new AdminDtos.UserSaveRequest(
                        editing ? existing.id() : null,
                        username.getValue(),
                        password.getValue(),
                        fullName.getValue(),
                        email.getValue(),
                        roleCombo.getValue(),
                        department.getValue(),
                        "FULL",
                        branch.getValue(),
                        active.getValue(),
                        editing && existing.locked(),
                        mfa.getValue(),
                        editing ? existing.rowVersion() : 0L
                ));
                Notification.show("User account saved successfully!", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, save);
        d.open();
    }
}
