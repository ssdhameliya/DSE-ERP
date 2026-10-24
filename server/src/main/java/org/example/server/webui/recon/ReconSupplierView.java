package org.example.server.webui.recon;

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
import org.example.server.recon.PurchaseReconDtos;
import org.example.server.recon.PurchaseReconService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "recon/suppliers", layout = MainAppLayout.class)
@PageTitle("Recon Suppliers Directory | DSE ERP")
@PermitAll
public class ReconSupplierView extends VerticalLayout {

    private final PurchaseReconService reconService;
    private final Grid<PurchaseReconDtos.SupplierDto> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public ReconSupplierView(PurchaseReconService reconService) {
        this.reconService = reconService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Reconciliation Suppliers Directory");
        title.getStyle().set("margin", "0");

        Button newBtn = new Button("New Recon Supplier", VaadinIcon.PLUS.create(), e -> openEditDialog(null));
        newBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button refreshBtn = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refreshGrid());

        header.add(title, newBtn, refreshBtn);
        header.expand(title);
        add(header);

        searchField.setPlaceholder("Search Supplier Name, GSTIN, Phone...");
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
        grid.addColumn(PurchaseReconDtos.SupplierDto::reference).setHeader("Ref #").setAutoWidth(true).setSortable(true);
        grid.addColumn(PurchaseReconDtos.SupplierDto::legalName).setHeader("Legal Name").setFlexGrow(1).setSortable(true);
        grid.addColumn(PurchaseReconDtos.SupplierDto::gstin).setHeader("GSTIN").setAutoWidth(true);
        grid.addColumn(PurchaseReconDtos.SupplierDto::pan).setHeader("PAN").setAutoWidth(true);
        grid.addColumn(PurchaseReconDtos.SupplierDto::contactPerson).setHeader("Contact Person").setAutoWidth(true);
        grid.addColumn(PurchaseReconDtos.SupplierDto::phone).setHeader("Phone").setAutoWidth(true);
        grid.addColumn(PurchaseReconDtos.SupplierDto::email).setHeader("Email").setAutoWidth(true);
        grid.addColumn(s -> s.reconCount() + " bills").setHeader("Recons").setAutoWidth(true);

        grid.addComponentColumn(s -> {
            Span b = new Span(s.status());
            b.getElement().getThemeList().add("badge");
            if ("ACTIVE".equalsIgnoreCase(s.status())) b.getElement().getThemeList().add("success");
            else b.getElement().getThemeList().add("contrast");
            return b;
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(s -> {
            Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditDialog(s));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button del = new Button(VaadinIcon.TRASH.create(), e -> handleDelete(s));
            del.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(edit, del);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void refreshGrid() {
        try {
            List<PurchaseReconDtos.SupplierDto> list = reconService.searchSuppliers(searchField.getValue(), 100);
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading suppliers: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleDelete(PurchaseReconDtos.SupplierDto s) {
        if (s == null) return;
        try {
            reconService.deleteSupplier(s.id());
            Notification.show("Supplier deleted", 2500, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshGrid();
        } catch (Exception ex) {
            Notification.show("Failed: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openEditDialog(PurchaseReconDtos.SupplierDto existing) {
        Dialog d = new Dialog();
        boolean editing = existing != null;
        d.setHeaderTitle(editing ? "Edit Supplier (" + existing.reference() + ")" : "Create Recon Supplier");
        d.setWidth("550px");

        TextField legalName = new TextField("Legal Name");
        TextField gstin = new TextField("GSTIN");
        TextField pan = new TextField("PAN");
        TextField contact = new TextField("Contact Person");
        TextField phone = new TextField("Phone");
        TextField email = new TextField("Email");

        ComboBox<String> status = new ComboBox<>("Status");
        status.setItems("ACTIVE", "INACTIVE");
        status.setValue("ACTIVE");

        TextField notes = new TextField("Notes");
        notes.setWidthFull();

        if (editing) {
            legalName.setValue(existing.legalName());
            gstin.setValue(existing.gstin() != null ? existing.gstin() : "");
            pan.setValue(existing.pan() != null ? existing.pan() : "");
            contact.setValue(existing.contactPerson() != null ? existing.contactPerson() : "");
            phone.setValue(existing.phone() != null ? existing.phone() : "");
            email.setValue(existing.email() != null ? existing.email() : "");
            status.setValue(existing.status() != null ? existing.status() : "ACTIVE");
            notes.setValue(existing.notes() != null ? existing.notes() : "");
        }

        VerticalLayout form = new VerticalLayout(
                legalName,
                new HorizontalLayout(gstin, pan),
                new HorizontalLayout(contact, phone),
                new HorizontalLayout(email, status),
                notes
        );
        form.setPadding(false);
        d.add(form);

        Button save = new Button("Save", e -> {
            if (legalName.isEmpty()) {
                Notification.show("Legal name is required", 2500, Notification.Position.MIDDLE);
                return;
            }
            try {
                reconService.saveSupplier(new PurchaseReconDtos.SupplierSaveRequest(
                        editing ? existing.id() : null,
                        legalName.getValue(),
                        gstin.getValue(),
                        pan.getValue(),
                        contact.getValue(),
                        phone.getValue(),
                        email.getValue(),
                        notes.getValue(),
                        status.getValue(),
                        editing ? existing.rowVersion() : 0L
                ));
                Notification.show("Supplier saved successfully!", 2500, Notification.Position.TOP_CENTER)
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
