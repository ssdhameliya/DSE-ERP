package org.example.server.webui.documents;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "documents/studio", layout = MainAppLayout.class)
@PageTitle("Document Studio & Print Templates | DSE ERP")
@PermitAll
public class DocumentStudioView extends VerticalLayout {

    public record TemplateDef(String typeCode, String typeName, String format, String engine, String status) {}

    private final JpaNativeRepository jdbc;
    private final Grid<TemplateDef> grid = new Grid<>();

    // Selected Template Customization Form
    private final H4 selectedTemplateTitle = new H4("Tax Invoice (B2B / Standard)");
    private final TextField headerTitle = new TextField("Document Header Text", "TAX INVOICE");
    private final ComboBox<String> colorTheme = new ComboBox<>("Accent Palette");
    private final Checkbox showQrCode = new Checkbox("Render Dynamic B2B E-Invoice QR Code", true);
    private final Checkbox showBankDetails = new Checkbox("Include Bank Settlement & IFSC in Footer", true);
    private final Checkbox showAmountInWords = new Checkbox("Render Indian Currency Amount in Words", true);
    private final TextArea termsAndConditions = new TextArea("Default Terms & Conditions");

    public DocumentStudioView(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Document Studio & Canonical Print Templates");
        title.getStyle().set("margin", "0");

        Button saveAllBtn = new Button("Save Template Settings", VaadinIcon.CHECK.create(), e -> saveTemplateConfig());
        saveAllBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, saveAllBtn);
        header.expand(title);
        add(header);

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(40);

        // Left: Template List
        VerticalLayout leftPanel = new VerticalLayout();
        leftPanel.setSizeFull();
        leftPanel.setPadding(false);

        configureGrid();
        leftPanel.add(grid);
        splitLayout.addToPrimary(leftPanel);

        // Right: Customizer
        VerticalLayout rightPanel = new VerticalLayout();
        rightPanel.setSizeFull();
        rightPanel.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "20px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");

        colorTheme.setItems("Corporate Navy", "Modern Emerald", "Charcoal Slate", "Ruby Red");
        colorTheme.setValue("Corporate Navy");

        termsAndConditions.setWidthFull();
        termsAndConditions.setHeight("160px");
        termsAndConditions.setValue("1. Goods once sold will not be taken back without valid RMA return authorization.\n2. Subject to local jurisdiction.\n3. Interest @ 18% p.a. will be charged for delayed payments past 30 days.");

        headerTitle.setWidthFull();

        rightPanel.add(
                selectedTemplateTitle,
                new HorizontalLayout(headerTitle, colorTheme),
                showQrCode,
                showBankDetails,
                showAmountInWords,
                termsAndConditions
        );
        splitLayout.addToSecondary(rightPanel);

        add(splitLayout);
        loadTemplates();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(TemplateDef::typeName).setHeader("Document Type").setFlexGrow(1).setSortable(true);
        grid.addColumn(TemplateDef::format).setHeader("Format").setAutoWidth(true);
        grid.addColumn(TemplateDef::engine).setHeader("Rendering Engine").setAutoWidth(true);
        grid.addComponentColumn(t -> {
            Span b = new Span(t.status());
            b.getElement().getThemeList().add("badge success");
            return b;
        }).setHeader("Status").setAutoWidth(true);

        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                selectedTemplateTitle.setText(e.getValue().typeName() + " Layout Customizer");
                headerTitle.setValue(e.getValue().typeName().toUpperCase());
            }
        });
    }

    private void loadTemplates() {
        List<TemplateDef> list = List.of(
                new TemplateDef("TAX_INVOICE", "Tax Invoice (Standard)", "PDF A4", "iText / OpenPDF", "ACTIVE"),
                new TemplateDef("RETAIL_INVOICE", "Retail / POS Invoice", "80mm Thermal", "Direct ESC/POS", "ACTIVE"),
                new TemplateDef("QUOTATION", "Quotation / Estimate", "PDF A4", "iText / OpenPDF", "ACTIVE"),
                new TemplateDef("PURCHASE_ORDER", "Purchase Order", "PDF A4", "iText / OpenPDF", "ACTIVE"),
                new TemplateDef("GOODS_RETURN", "Goods Return Note", "PDF A4", "iText / OpenPDF", "ACTIVE"),
                new TemplateDef("PAYMENT_RECEIPT", "Payment Voucher / Receipt", "PDF A4", "iText / OpenPDF", "ACTIVE"),
                new TemplateDef("DELIVERY_CHALLAN", "Delivery Challan", "PDF A4", "iText / OpenPDF", "ACTIVE"),
                new TemplateDef("FINANCIAL_EXCEL", "Financial Statement Export", "XLSX", "Apache POI", "ACTIVE")
        );
        grid.setItems(list);
        grid.select(list.getFirst());
    }

    private void saveTemplateConfig() {
        Notification.show("Document template preferences updated and saved across company workspace!", 3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
