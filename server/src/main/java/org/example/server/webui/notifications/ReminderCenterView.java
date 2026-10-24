package org.example.server.webui.notifications;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.webui.MainAppLayout;

import java.time.LocalDate;
import java.util.List;

@Route(value = "notifications/reminders", layout = MainAppLayout.class)
@PageTitle("Reminder Center | DSE ERP")
@PermitAll
public class ReminderCenterView extends VerticalLayout {

    public record ReminderItem(Long id, String refNo, String title, String dueDate, String priority, String status, String notes) {}

    private final JpaNativeRepository jdbc;
    private final Grid<ReminderItem> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public ReminderCenterView(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Operational Reminder & Task Management");
        title.getStyle().set("margin", "0");

        Button newBtn = new Button("New Reminder", VaadinIcon.PLUS.create(), e -> openEditDialog(null));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshGrid());

        header.add(title, newBtn, refreshBtn);
        header.expand(title);
        add(header);

        searchField.setPlaceholder("Search Reminder Title, Reference or Priority...");
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
        grid.addColumn(ReminderItem::refNo).setHeader("Ref #").setAutoWidth(true);
        grid.addColumn(ReminderItem::title).setHeader("Reminder Task").setFlexGrow(1).setSortable(true);
        grid.addColumn(ReminderItem::dueDate).setHeader("Due Date").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(r -> {
            Span b = new Span(r.priority());
            b.getElement().getThemeList().add("badge");
            if ("HIGH".equalsIgnoreCase(r.priority()) || "URGENT".equalsIgnoreCase(r.priority())) b.getElement().getThemeList().add("error");
            else if ("MEDIUM".equalsIgnoreCase(r.priority())) b.getElement().getThemeList().add("warning");
            else b.getElement().getThemeList().add("contrast");
            return b;
        }).setHeader("Priority").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Span b = new Span(r.status());
            b.getElement().getThemeList().add("badge");
            if ("COMPLETED".equalsIgnoreCase(r.status())) b.getElement().getThemeList().add("success");
            else b.getElement().getThemeList().add("primary");
            return b;
        }).setHeader("Status").setAutoWidth(true);

        grid.addColumn(ReminderItem::notes).setHeader("Notes").setFlexGrow(1);

        grid.addComponentColumn(r -> {
            Button doneBtn = new Button(VaadinIcon.CHECK.create(), e -> toggleComplete(r));
            doneBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);

            Button delBtn = new Button(VaadinIcon.TRASH.create(), e -> deleteReminder(r));
            delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(doneBtn, delBtn);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            String q = searchField.getValue() != null ? "%" + searchField.getValue().trim() + "%" : "%";
            List<ReminderItem> list = jdbc.query("""
                SELECT id, COALESCE(reference_no, 'REM-' || CAST(id AS text)), COALESCE(title, 'Reminder'),
                       COALESCE(due_date::text, ''), COALESCE(priority, 'MEDIUM'), COALESCE(status, 'PENDING'),
                       COALESCE(notes, '')
                FROM reminder_register
                WHERE (title ILIKE ? OR COALESCE(reference_no, '') ILIKE ? OR COALESCE(priority, '') ILIKE ?)
                ORDER BY id DESC LIMIT 200
                """, (rs, idx) -> new ReminderItem(
                    rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)
            ), q, q, q);
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading reminders: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void toggleComplete(ReminderItem r) {
        if (r == null) return;
        try {
            String nextStatus = "COMPLETED".equalsIgnoreCase(r.status()) ? "PENDING" : "COMPLETED";
            jdbc.update("UPDATE reminder_register SET status=? WHERE id=?", nextStatus, r.id());
            Notification.show("Reminder marked as " + nextStatus, 2000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshGrid();
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    private void deleteReminder(ReminderItem r) {
        if (r == null) return;
        try {
            jdbc.update("DELETE FROM reminder_register WHERE id=?", r.id());
            Notification.show("Reminder deleted", 2000, Notification.Position.TOP_CENTER);
            refreshGrid();
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    private void openEditDialog(ReminderItem existing) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Create New Follow-Up Reminder");
        d.setWidth("500px");

        TextField titleField = new TextField("Task / Reminder Title");
        titleField.setWidthFull();

        TextField refField = new TextField("Reference Document # (Optional)");
        DatePicker duePicker = new DatePicker("Due Date", LocalDate.now().plusDays(2));

        ComboBox<String> priorityCombo = new ComboBox<>("Priority");
        priorityCombo.setItems("LOW", "MEDIUM", "HIGH", "URGENT");
        priorityCombo.setValue("MEDIUM");

        TextField notesField = new TextField("Notes");
        notesField.setWidthFull();

        VerticalLayout form = new VerticalLayout(
                titleField,
                new HorizontalLayout(refField, duePicker),
                priorityCombo,
                notesField
        );
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Save Reminder", e -> {
            if (titleField.isEmpty() || duePicker.isEmpty()) {
                Notification.show("Title and due date are required", 2500, Notification.Position.MIDDLE);
                return;
            }
            try {
                jdbc.update("""
                    INSERT INTO reminder_register (title, reference_no, due_date, priority, status, notes)
                    VALUES (?, ?, ?, ?, 'PENDING', ?)
                    """, titleField.getValue(), refField.getValue(), duePicker.getValue(), priorityCombo.getValue(), notesField.getValue());
                Notification.show("Reminder saved!", 2500, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error saving: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> d.close());

        d.getFooter().add(cancel, save);
        d.open();
    }
}
