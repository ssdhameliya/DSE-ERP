package org.example.server.webui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Sign In | DSE ERP")
@AnonymousAllowed
public class VaadinLoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public VaadinLoginView() {
        addClassName("dse-login-view");
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        getStyle().set("background", "var(--lumo-base-color)");

        H1 header = new H1("DSE ERP");
        header.getStyle()
                .set("font-size", "2.2rem")
                .set("font-weight", "800")
                .set("margin-bottom", "4px")
                .set("color", "var(--lumo-primary-color)");

        Paragraph subtitle = new Paragraph("Enterprise Production & Distribution Studio");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0")
                .set("margin-bottom", "16px");

        Span versionBadge = new Span("v10.0.41 Enterprise Cloud");
        versionBadge.getElement().getThemeList().add("badge small contrast");
        versionBadge.getStyle().set("margin-bottom", "24px");

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("Sign In to Account");
        i18n.getForm().setUsername("Username or Email");
        i18n.getForm().setPassword("Password");
        i18n.getForm().setSubmit("Sign In");
        i18n.getErrorMessage().setTitle("Invalid Credentials");
        i18n.getErrorMessage().setMessage("Check your username and password and try again.");
        login.setI18n(i18n);

        login.setAction("login");

        VerticalLayout card = new VerticalLayout(header, subtitle, versionBadge, login);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setWidth("420px");
        card.getStyle()
                .set("padding", "32px")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "0 4px 20px rgba(0, 0, 0, 0.08)")
                .set("background", "var(--lumo-tint-5pct)");

        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        if (beforeEnterEvent.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")) {
            login.setError(true);
        }
    }
}
