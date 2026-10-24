package org.example.server.webui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.admin.AdminDtos;
import org.example.server.admin.AdminService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "admin/registrations", layout = MainAppLayout.class)
@PageTitle("User Registration Approvals | DSE ERP")
@PermitAll
public class RegistrationApprovalsView extends VerticalLayout {

    private final AdminService adminService;
    private final Grid<AdminDtos.RegistrationRequestDto> grid = new Grid<>();

    public RegistrationApprovalsView(AdminService adminService) {
        this.adminService = adminService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Self-Registration Audit & Provisioning Requests");
        title.getStyle().set("margin", "0");

        Button refreshBtn = new Button("Refresh Requests", VaadinIcon.REFRESH.create(), e -> refreshGrid());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, refreshBtn);
        header.expand(title);
        add(header);

        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(AdminDtos.RegistrationRequestDto::username).setHeader("Username").setAutoWidth(true).setSortable(true);
        grid.addColumn(AdminDtos.RegistrationRequestDto::fullName).setHeader("Applicant Name").setFlexGrow(1).setSortable(true);
        grid.addColumn(AdminDtos.RegistrationRequestDto::email).setHeader("Email Address").setAutoWidth(true);
        grid.addColumn(AdminDtos.RegistrationRequestDto::requestedRole).setHeader("Requested Role").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Span b = new Span(r.emailVerified() ? "VERIFIED" : "UNVERIFIED");
            b.getElement().getThemeList().add("badge");
            if (r.emailVerified()) b.getElement().getThemeList().add("success");
            else b.getElement().getThemeList().add("warning");
            return b;
        }).setHeader("Email").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Span b = new Span(r.status());
            b.getElement().getThemeList().add("badge");
            if ("PENDING".equalsIgnoreCase(r.status())) b.getElement().getThemeList().add("warning");
            else if ("APPROVED".equalsIgnoreCase(r.status())) b.getElement().getThemeList().add("success");
            else b.getElement().getThemeList().add("error");
            return b;
        }).setHeader("Status").setAutoWidth(true);

        grid.addColumn(AdminDtos.RegistrationRequestDto::requestedAt).setHeader("Requested At").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(r -> {
            if ("PENDING".equalsIgnoreCase(r.status())) {
                Button approve = new Button("Approve", VaadinIcon.CHECK.create(), e -> openApproveDialog(r));
                approve.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);

                Button reject = new Button("Reject", VaadinIcon.CLOSE.create(), e -> openRejectDialog(r));
                reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

                return new HorizontalLayout(approve, reject);
            } else {
                return new Span(r.reviewedBy() != null ? "Reviewed by " + r.reviewedBy() : "-");
            }
        }).setHeader("Decision Actions").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            List<AdminDtos.RegistrationRequestDto> list = adminService.registrations("PENDING_ADMIN_APPROVAL");
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading registrations: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openApproveDialog(AdminDtos.RegistrationRequestDto r) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Approve & Provision User: " + r.username());

        ComboBox<String> roleCombo = new ComboBox<>("Assign Security Role");
        List<String> roles = adminService.roles().stream().map(AdminDtos.RoleDto::code).toList();
        roleCombo.setItems(roles);
        roleCombo.setValue(roles.contains(r.requestedRole()) ? r.requestedRole() : "SALES");

        VerticalLayout form = new VerticalLayout(
                new Span("Confirm provisioning user account for " + r.fullName() + " (" + r.email() + ")."),
                roleCombo
        );
        form.setPadding(false);
        d.add(form);

        Button approveBtn = new Button("Authorize & Provision", e -> {
            try {
                adminService.approveRegistration(r.id(), new AdminDtos.RegistrationDecisionRequest(roleCombo.getValue(), "", r.rowVersion()));
                Notification.show("User " + r.username() + " provisioned successfully!", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Failed to approve: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        approveBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, approveBtn);
        d.open();
    }

    private void openRejectDialog(AdminDtos.RegistrationRequestDto r) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Reject Registration: " + r.username());

        TextField reasonField = new TextField("Rejection Reason");
        reasonField.setWidthFull();
        reasonField.setValue("Account not approved by company administration.");

        VerticalLayout form = new VerticalLayout(
                new Span("Provide the reason for rejecting " + r.fullName() + "'s registration request:"),
                reasonField
        );
        form.setPadding(false);
        d.add(form);

        Button rejectBtn = new Button("Confirm Rejection", e -> {
            try {
                adminService.rejectRegistration(r.id(), new AdminDtos.RegistrationDecisionRequest("", reasonField.getValue(), r.rowVersion()));
                Notification.show("Registration request rejected", 2500, Notification.Position.TOP_CENTER);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Failed: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        rejectBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, rejectBtn);
        d.open();
    }
}
