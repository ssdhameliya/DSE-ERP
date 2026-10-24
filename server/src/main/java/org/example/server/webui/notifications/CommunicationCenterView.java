package org.example.server.webui.notifications;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.auth.SmtpMailService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "notifications/communication", layout = MainAppLayout.class)
@PageTitle("Communication Center | DSE ERP")
@PermitAll
public class CommunicationCenterView extends VerticalLayout {

    public record CommsRow(Long id, String channel, String recipient, String subject, String status, String createdBy, String createdAt) {}

    private final JpaNativeRepository jdbc;
    private final SmtpMailService mailService;
    private final Grid<CommsRow> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public CommunicationCenterView(JpaNativeRepository jdbc, SmtpMailService mailService) {
        this.jdbc = jdbc;
        this.mailService = mailService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Communication Outbox & Dispatch Logs");
        title.getStyle().set("margin", "0");

        Button composeBtn = new Button("Compose Email", VaadinIcon.ENVELOPE.create(), e -> openComposeDialog());
        composeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshGrid());

        header.add(title, composeBtn, refreshBtn);
        header.expand(title);
        add(header);

        searchField.setPlaceholder("Search Recipient, Subject or Status...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("350px");
        searchField.addValueChangeListener(e -> refreshGrid());
        add(searchField);

        configureGrid();
        add(grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addComponentColumn(c -> {
            Span b = new Span(c.channel());
            b.getElement().getThemeList().add("badge");
            if ("EMAIL".equalsIgnoreCase(c.channel())) b.getElement().getThemeList().add("primary");
            else b.getElement().getThemeList().add("success");
            return b;
        }).setHeader("Channel").setAutoWidth(true);

        grid.addColumn(CommsRow::recipient).setHeader("Recipient").setAutoWidth(true).setSortable(true);
        grid.addColumn(CommsRow::subject).setHeader("Subject").setFlexGrow(1).setSortable(true);

        grid.addComponentColumn(c -> {
            Span b = new Span(c.status());
            b.getElement().getThemeList().add("badge");
            if ("SENT".equalsIgnoreCase(c.status()) || "DELIVERED".equalsIgnoreCase(c.status())) b.getElement().getThemeList().add("success");
            else if ("FAILED".equalsIgnoreCase(c.status())) b.getElement().getThemeList().add("error");
            else b.getElement().getThemeList().add("warning");
            return b;
        }).setHeader("Status").setAutoWidth(true);

        grid.addColumn(CommsRow::createdBy).setHeader("Dispatched By").setAutoWidth(true);
        grid.addColumn(CommsRow::createdAt).setHeader("Time").setAutoWidth(true).setSortable(true);
    }

    private void refreshGrid() {
        try {
            String q = searchField.getValue() != null ? "%" + searchField.getValue().trim() + "%" : "%";
            List<CommsRow> list = jdbc.query("""
                SELECT id, COALESCE(channel, 'EMAIL'), COALESCE(recipient, ''), COALESCE(subject, '(No Subject)'),
                       COALESCE(status, 'SENT'), COALESCE(created_by, 'System'), COALESCE(created_at::text, '')
                FROM communication_log
                WHERE (recipient ILIKE ? OR subject ILIKE ? OR status ILIKE ?)
                ORDER BY id DESC LIMIT 200
                """, (rs, idx) -> new CommsRow(
                    rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)
            ), q, q, q);
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading communications: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openComposeDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Compose & Send Business Email");
        d.setWidth("600px");

        TextField toField = new TextField("Recipient Email");
        toField.setWidthFull();

        TextField subjectField = new TextField("Subject");
        subjectField.setWidthFull();

        TextArea bodyField = new TextArea("Message Body");
        bodyField.setWidthFull();
        bodyField.setHeight("200px");

        VerticalLayout form = new VerticalLayout(toField, subjectField, bodyField);
        form.setPadding(false);
        d.add(form);

        Button send = new Button("Send Email", e -> {
            if (toField.isEmpty() || subjectField.isEmpty() || bodyField.isEmpty()) {
                Notification.show("Please fill all required email fields", 2500, Notification.Position.MIDDLE);
                return;
            }
            try {
                // Send via SmtpMailService
                mailService.sendBusiness(toField.getValue(), subjectField.getValue(), bodyField.getValue(), null, null);
                // Log to communication_log
                jdbc.update("""
                    INSERT INTO communication_log (channel, recipient, subject, status, created_by, created_at)
                    VALUES ('EMAIL', ?, ?, 'SENT', 'WEB_USER', CURRENT_TIMESTAMP)
                    """, toField.getValue(), subjectField.getValue());

                Notification.show("Email dispatched successfully!", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                // Log failure
                jdbc.update("""
                    INSERT INTO communication_log (channel, recipient, subject, status, error_message, created_by, created_at)
                    VALUES ('EMAIL', ?, ?, 'FAILED', ?, 'WEB_USER', CURRENT_TIMESTAMP)
                    """, toField.getValue(), subjectField.getValue(), ex.getMessage());

                Notification.show("Delivery failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                refreshGrid();
            }
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, send);
        d.open();
    }
}
