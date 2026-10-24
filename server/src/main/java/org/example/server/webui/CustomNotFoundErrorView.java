package org.example.server.webui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.ParentLayout;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import org.example.server.webui.dashboard.DashboardView;

@ParentLayout(MainAppLayout.class)
@PageTitle("Page Not Found | DSE ERP Studio")
@PermitAll
public class CustomNotFoundErrorView extends VerticalLayout implements HasErrorParameter<NotFoundException> {

    private final Span pathLabel = new Span();

    public CustomNotFoundErrorView() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        getStyle().set("background", "var(--lumo-base-color)");

        VerticalLayout card = new VerticalLayout();
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setWidth("440px");
        card.getStyle()
                .set("padding", "36px 32px")
                .set("border-radius", "16px")
                .set("background", "var(--lumo-tint-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("box-shadow", "0 10px 25px -5px rgba(0,0,0,0.08)");

        Icon icon = new Icon(VaadinIcon.WARNING);
        icon.setSize("48px");
        icon.setColor("var(--lumo-warning-color)");
        icon.getStyle().set("margin-bottom", "12px");

        H2 title = new H2("Requested View Not Found");
        title.getStyle()
                .set("font-size", "1.4rem")
                .set("font-weight", "800")
                .set("margin", "0 0 8px 0");

        Paragraph desc = new Paragraph("The route you are navigating to is unavailable or restricted.");
        desc.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.85rem")
                .set("margin", "0 0 16px 0")
                .set("text-align", "center");

        pathLabel.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "4px 10px")
                .set("border-radius", "6px")
                .set("font-family", "monospace")
                .set("font-size", "0.8rem")
                .set("margin-bottom", "20px");

        Button returnBtn = new Button("Return to Executive Dashboard", new Icon(VaadinIcon.ARROW_LEFT));
        returnBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        returnBtn.getStyle()
                .set("background", "linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)")
                .set("box-shadow", "0 2px 8px rgba(59, 130, 246, 0.4)");
        returnBtn.addClickListener(e -> UI.getCurrent().navigate(DashboardView.class));

        card.add(icon, title, desc, pathLabel, returnBtn);
        add(card);
    }

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<NotFoundException> parameter) {
        String path = event.getLocation().getPath();
        pathLabel.setText("Route: /" + (path.isEmpty() ? " (root)" : path));
        return HttpServletResponse.SC_NOT_FOUND;
    }
}
