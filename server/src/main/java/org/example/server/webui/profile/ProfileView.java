package org.example.server.webui.profile;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
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
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.webui.MainAppLayout;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Route(value = "profile", layout = MainAppLayout.class)
@PageTitle("User Profile & Security | DSE ERP")
@PermitAll
public class ProfileView extends VerticalLayout {

    private final JpaNativeRepository jdbc;
    private final PasswordEncoder passwordEncoder;

    private final Span usernameSpan = new Span();
    private final Span roleSpan = new Span();
    private final Span fullNameSpan = new Span();
    private final Span emailSpan = new Span();
    private final Span departmentSpan = new Span();

    private final PasswordField currentPassword = new PasswordField("Current Password");
    private final PasswordField newPassword = new PasswordField("New Password");
    private final PasswordField confirmPassword = new PasswordField("Confirm New Password");

    public ProfileView(JpaNativeRepository jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Personal Account Profile & Credentials");
        title.getStyle().set("margin", "0");

        header.add(title);
        add(header);

        // Profile Identity Card
        VerticalLayout profileCard = new VerticalLayout();
        profileCard.setWidthFull();
        profileCard.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "20px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");

        H4 profileHeading = new H4("Active Operator Identity");
        profileHeading.getStyle().set("margin", "0 0 12px 0");

        HorizontalLayout infoRow = new HorizontalLayout();
        infoRow.setWidthFull();
        infoRow.add(createFieldBadge("Username", usernameSpan));
        infoRow.add(createFieldBadge("Role / Authority", roleSpan));
        infoRow.add(createFieldBadge("Full Name", fullNameSpan));
        infoRow.add(createFieldBadge("Email Address", emailSpan));
        infoRow.add(createFieldBadge("Department", departmentSpan));

        profileCard.add(profileHeading, infoRow);
        add(profileCard);

        // Security / Change Password Card
        VerticalLayout securityCard = new VerticalLayout();
        securityCard.setWidth("500px");
        securityCard.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "20px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");

        H4 secHeading = new H4("Change Account Password");
        secHeading.getStyle().set("margin", "0 0 12px 0");

        currentPassword.setWidthFull();
        newPassword.setWidthFull();
        confirmPassword.setWidthFull();

        Button updatePasswordBtn = new Button("Update Password", VaadinIcon.KEY.create(), e -> handleChangePassword());
        updatePasswordBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        securityCard.add(secHeading, currentPassword, newPassword, confirmPassword, updatePasswordBtn);
        add(securityCard);

        loadUserData();
    }

    private VerticalLayout createFieldBadge(String label, Span valSpan) {
        VerticalLayout vl = new VerticalLayout();
        vl.setPadding(false);
        vl.setSpacing(false);
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        valSpan.getStyle().set("font-weight", "600").set("font-size", "15px");
        vl.add(lbl, valSpan);
        return vl;
    }

    private void loadUserData() {
        var user = CurrentUser.require();
        usernameSpan.setText(user.username());
        roleSpan.setText(user.role());

        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT COALESCE(full_name, username), COALESCE(email, ''), COALESCE(department, '') FROM users WHERE id=?", user.id());
            fullNameSpan.setText(String.valueOf(row.get("full_name")));
            emailSpan.setText(String.valueOf(row.get("email")));
            departmentSpan.setText(String.valueOf(row.get("department")));
        } catch (Exception ex) {
            fullNameSpan.setText(user.username());
            emailSpan.setText("-");
            departmentSpan.setText("-");
        }
    }

    private void handleChangePassword() {
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Notification.show("Please fill all password fields", 2500, Notification.Position.MIDDLE);
            return;
        }

        if (!newPassword.getValue().equals(confirmPassword.getValue())) {
            Notification.show("New password and confirm password do not match", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        if (newPassword.getValue().length() < 6) {
            Notification.show("New password must be at least 6 characters long", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        var user = CurrentUser.require();
        try {
            String dbPass = jdbc.queryForObject("SELECT password FROM users WHERE id=?", String.class, user.id());
            if (dbPass != null && !passwordEncoder.matches(currentPassword.getValue(), dbPass)) {
                Notification.show("Current password is incorrect", 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            String newEncoded = passwordEncoder.encode(newPassword.getValue());
            jdbc.update("UPDATE users SET password=? WHERE id=?", newEncoded, user.id());

            Notification.show("Password changed successfully!", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            currentPassword.clear();
            newPassword.clear();
            confirmPassword.clear();
        } catch (Exception ex) {
            Notification.show("Failed to update password: " + ex.getMessage(), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
