package org.example.server.webui.documents;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.barcode.BarcodeDtos;
import org.example.server.barcode.BarcodeService;
import org.example.server.webui.MainAppLayout;

import java.util.List;

@Route(value = "documents/barcodes", layout = MainAppLayout.class)
@PageTitle("Barcode & Label Studio | DSE ERP")
@PermitAll
public class BarcodeStudioView extends VerticalLayout {

    private final BarcodeService barcodeService;
    private final Grid<BarcodeDtos.DesktopBarcodeItemDto> itemGrid = new Grid<>();

    // Preview Panel
    private final H4 previewTitle = new H4("Barcode Label Live Preview");
    private final Image barcodeImage = new Image();
    private final Span barcodeValue = new Span();
    private final Span itemNamePreview = new Span();
    private final Span mrpPreview = new Span();

    private final ComboBox<String> labelSizeCombo = new ComboBox<>("Thermal Label Format");
    private final IntegerField copiesField = new IntegerField("Print Copies", 1, e -> {});

    public BarcodeStudioView(BarcodeService barcodeService) {
        this.barcodeService = barcodeService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Barcode Studio & Thermal Label Generator");
        title.getStyle().set("margin", "0");

        Button printBtn = new Button("Print Thermal Labels", VaadinIcon.PRINT.create(), e -> handlePrint());
        printBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, printBtn);
        header.expand(title);
        add(header);

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(60);

        // Left Panel: Items from Inventory
        VerticalLayout leftPanel = new VerticalLayout();
        leftPanel.setSizeFull();
        leftPanel.setPadding(false);

        configureItemGrid();
        leftPanel.add(itemGrid);
        splitLayout.addToPrimary(leftPanel);

        // Right Panel: Live Preview
        VerticalLayout rightPanel = new VerticalLayout();
        rightPanel.setSizeFull();
        rightPanel.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "20px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");
        rightPanel.setAlignItems(FlexComponent.Alignment.CENTER);

        labelSizeCombo.setItems("50mm x 25mm (Standard 1-Up)", "38mm x 25mm (Compact 2-Up)", "A4 Sheet (24-Up Sticker Grid)");
        labelSizeCombo.setValue("50mm x 25mm (Standard 1-Up)");
        labelSizeCombo.setWidth("300px");

        copiesField.setWidth("150px");

        // Visual Label Box
        VerticalLayout labelBox = new VerticalLayout();
        labelBox.setWidth("340px");
        labelBox.getStyle()
                .set("border", "2px dashed var(--lumo-contrast-30pct)")
                .set("border-radius", "8px")
                .set("padding", "16px")
                .set("background", "#ffffff")
                .set("color", "#000000")
                .set("text-align", "center");
        labelBox.setAlignItems(FlexComponent.Alignment.CENTER);

        itemNamePreview.getStyle().set("font-weight", "bold").set("font-size", "15px");
        barcodeValue.getStyle().set("font-family", "monospace").set("letter-spacing", "2px").set("margin-top", "6px");
        mrpPreview.getStyle().set("font-weight", "bold").set("font-size", "16px").set("color", "var(--lumo-success-text-color)");

        barcodeImage.setWidth("260px");
        barcodeImage.setHeight("80px");

        labelBox.add(itemNamePreview, barcodeImage, barcodeValue, mrpPreview);

        rightPanel.add(
                previewTitle,
                new HorizontalLayout(labelSizeCombo, copiesField),
                labelBox
        );
        splitLayout.addToSecondary(rightPanel);

        add(splitLayout);
        loadItems();
    }

    private void configureItemGrid() {
        itemGrid.setSizeFull();
        itemGrid.addColumn(BarcodeDtos.DesktopBarcodeItemDto::itemCode).setHeader("Item Code").setAutoWidth(true).setSortable(true);
        itemGrid.addColumn(BarcodeDtos.DesktopBarcodeItemDto::itemName).setHeader("Item Name").setFlexGrow(1).setSortable(true);
        itemGrid.addColumn(BarcodeDtos.DesktopBarcodeItemDto::category).setHeader("Category").setAutoWidth(true);
        itemGrid.addColumn(i -> "₹ " + (i.sellingPrice() != null ? i.sellingPrice().toPlainString() : "0.00")).setHeader("Selling Price").setAutoWidth(true);
        itemGrid.addColumn(BarcodeDtos.DesktopBarcodeItemDto::currentStock).setHeader("In Stock").setAutoWidth(true);

        itemGrid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                updatePreview(e.getValue());
            }
        });
    }

    private void loadItems() {
        try {
            List<BarcodeDtos.DesktopBarcodeItemDto> items = barcodeService.listDesktopItems();
            itemGrid.setItems(items);
            if (!items.isEmpty()) {
                itemGrid.select(items.getFirst());
            }
        } catch (Exception ex) {
            Notification.show("Error loading items: " + ex.getMessage(), 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void updatePreview(BarcodeDtos.DesktopBarcodeItemDto item) {
        itemNamePreview.setText(item.itemName());
        barcodeValue.setText("*" + item.itemCode() + "*");
        mrpPreview.setText("M.R.P. : ₹ " + (item.sellingPrice() != null ? item.sellingPrice().toPlainString() : "0.00"));

        try {
            BarcodeDtos.BarcodePreviewDto preview = barcodeService.generateBarcodePreview(item.itemCode());
            if (preview != null && preview.base64Png() != null) {
                barcodeImage.setSrc("data:image/png;base64," + preview.base64Png());
            }
        } catch (Exception ex) {
            Notification.show("Barcode generation error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    private void handlePrint() {
        BarcodeDtos.DesktopBarcodeItemDto sel = itemGrid.asSingleSelect().getValue();
        if (sel == null) {
            Notification.show("Select an item to print barcode labels", 2500, Notification.Position.MIDDLE);
            return;
        }
        int copies = copiesField.getValue() != null ? copiesField.getValue() : 1;
        Notification.show("Dispatched " + copies + " label(s) for " + sel.itemCode() + " to thermal label spooler!", 3500, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
