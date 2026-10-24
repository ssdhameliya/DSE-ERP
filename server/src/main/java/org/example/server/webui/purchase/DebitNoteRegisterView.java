package org.example.server.webui.purchase;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.creditdebit.CreditDebitNoteDtos;
import org.example.server.creditdebit.CreditDebitNoteService;
import org.example.server.webui.MainAppLayout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Route(value = "purchases/debit-notes", layout = MainAppLayout.class)
@PageTitle("Debit Note Register | DSE ERP")
@PermitAll
public class DebitNoteRegisterView extends VerticalLayout {

    private final CreditDebitNoteService noteService;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Grid<CreditDebitNoteDtos.DebitNoteDto> grid = new Grid<>();
    private final TextField searchField = new TextField();

    public DebitNoteRegisterView(CreditDebitNoteService noteService) {
        this.noteService = noteService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createToolbar();
        createGrid();

        refreshData();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout titleBox = new VerticalLayout();
        titleBox.setPadding(false);
        titleBox.setSpacing(false);

        H2 title = new H2("Debit Note Register");
        title.getStyle().set("margin", "0").set("font-size", "1.6rem").set("font-weight", "700");
        Paragraph sub = new Paragraph("GST-compliant Debit Notes issued against Supplier Inward Bills");
        sub.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        titleBox.add(title, sub);

        Button backBtn = new Button("Back to Purchases", new Icon(VaadinIcon.ARROW_LEFT), e -> UI.getCurrent().navigate("purchases"));
        backBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        header.add(titleBox, backBtn);
        add(header);
    }

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        searchField.setPlaceholder("Search debit note #, supplier bill, vendor name, GSTIN...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setWidth("380px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshData());

        Button refreshBtn = new Button("Refresh", new Icon(VaadinIcon.REFRESH), e -> refreshData());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        toolbar.add(searchField, refreshBtn);
        add(toolbar);
    }

    private void createGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);

        grid.addColumn(CreditDebitNoteDtos.DebitNoteDto::debitNoteNo)
                .setHeader("Debit Note #")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(dto -> dto.noteDate() != null ? dto.noteDate().toString() : "—")
                .setHeader("Date")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(CreditDebitNoteDtos.DebitNoteDto::originalBillNo)
                .setHeader("Supplier Bill #")
                .setAutoWidth(true);

        grid.addColumn(CreditDebitNoteDtos.DebitNoteDto::supplierName)
                .setHeader("Vendor / Supplier")
                .setFlexGrow(1);

        grid.addColumn(dto -> dto.taxableAmount() != null ? currencyFormat.format(dto.taxableAmount()) : "—")
                .setHeader("Taxable Value")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addColumn(dto -> dto.totalAmount() != null ? currencyFormat.format(dto.totalAmount()) : "—")
                .setHeader("Debit Total")
                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
                .setAutoWidth(true);

        grid.addComponentColumn(dto -> {
            String status = dto.status() != null ? dto.status().toUpperCase() : "ISSUED";
            Span badge = new Span(status);
            badge.getElement().getThemeList().add("badge small success");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        add(grid);
        setFlexGrow(1, grid);
    }

    private void refreshData() {
        try {
            List<CreditDebitNoteDtos.DebitNoteDto> list = noteService.listDebitNotes();
            String q = searchField.getValue();
            if (q != null && !q.isBlank()) {
                String term = q.toLowerCase(Locale.ROOT);
                list = list.stream().filter(n ->
                        (n.debitNoteNo() != null && n.debitNoteNo().toLowerCase().contains(term)) ||
                        (n.originalBillNo() != null && n.originalBillNo().toLowerCase().contains(term)) ||
                        (n.supplierName() != null && n.supplierName().toLowerCase().contains(term)) ||
                        (n.supplierGstin() != null && n.supplierGstin().toLowerCase().contains(term))
                ).toList();
            }
            grid.setItems(list);
        } catch (Exception ex) {
            Notification.show("Error loading debit notes: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }
}
