package org.example.server.webui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Sign In | DSE ERP Studio")
@AnonymousAllowed
public class VaadinLoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public VaadinLoginView() {
        addClassName("dse-modern-login-view");
        setSizeFull();
        setMargin(false);
        setPadding(false);
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        // Futuristic 3D deep radial gradient backdrop
        getStyle()
                .set("background", "radial-gradient(circle at 20% 15%, #1e1b4b 0%, #0f172a 45%, #020617 100%)")
                .set("min-height", "100vh")
                .set("position", "relative")
                .set("overflow", "hidden");

        // Subtle ambient neon glow orbs in background
        Div ambientGlowTop = new Div();
        ambientGlowTop.getStyle()
                .set("position", "absolute")
                .set("width", "500px")
                .set("height", "500px")
                .set("border-radius", "50%")
                .set("background", "radial-gradient(circle, rgba(99, 102, 241, 0.15) 0%, rgba(0, 0, 0, 0) 70%)")
                .set("top", "-100px")
                .set("left", "-100px")
                .set("pointer-events", "none");

        Div ambientGlowBottom = new Div();
        ambientGlowBottom.getStyle()
                .set("position", "absolute")
                .set("width", "600px")
                .set("height", "600px")
                .set("border-radius", "50%")
                .set("background", "radial-gradient(circle, rgba(14, 165, 233, 0.12) 0%, rgba(0, 0, 0, 0) 70%)")
                .set("bottom", "-150px")
                .set("right", "-150px")
                .set("pointer-events", "none");

        add(ambientGlowTop, ambientGlowBottom);

        // Card Container: 3D elevated glassmorphic panel
        VerticalLayout card = new VerticalLayout();
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setWidth("460px");
        card.setMaxWidth("92vw");
        card.getStyle()
                .set("padding", "36px 32px 30px 32px")
                .set("border-radius", "20px")
                .set("background", "rgba(15, 23, 42, 0.82)")
                .set("backdrop-filter", "blur(20px)")
                .set("-webkit-backdrop-filter", "blur(20px)")
                .set("border", "1px solid rgba(255, 255, 255, 0.12)")
                .set("box-shadow", "0 25px 60px -15px rgba(0, 0, 0, 0.6), 0 0 1px 1px rgba(255, 255, 255, 0.1) inset")
                .set("z-index", "1")
                .set("animation", "fadeInUp 0.4s ease-out");

        // Glowing emblem badge
        Div emblem = new Div();
        emblem.getStyle()
                .set("width", "56px")
                .set("height", "56px")
                .set("border-radius", "16px")
                .set("background", "linear-gradient(135deg, #6366f1 0%, #3b82f6 100%)")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("box-shadow", "0 8px 24px -4px rgba(99, 102, 241, 0.5)")
                .set("margin-bottom", "12px");

        Icon emblemIcon = new Icon(VaadinIcon.BUILDING);
        emblemIcon.setSize("28px");
        emblemIcon.setColor("#ffffff");
        emblem.add(emblemIcon);

        // Brand Title
        H1 header = new H1("DSE ERP STUDIO");
        header.getStyle()
                .set("font-size", "1.95rem")
                .set("font-weight", "800")
                .set("letter-spacing", "-0.5px")
                .set("margin", "0 0 4px 0")
                .set("background", "linear-gradient(135deg, #ffffff 0%, #cbd5e1 100%)")
                .set("-webkit-background-clip", "text")
                .set("-webkit-text-fill-color", "transparent");

        Paragraph subtitle = new Paragraph("Enterprise Resource Planning & Production Studio");
        subtitle.getStyle()
                .set("color", "#94a3b8")
                .set("font-size", "0.88rem")
                .set("margin", "0 0 14px 0")
                .set("text-align", "center")
                .set("font-weight", "400");

        // Environment + Version Tag
        HorizontalLayout badges = new HorizontalLayout();
        badges.setSpacing(true);
        badges.getStyle().set("margin-bottom", "18px");

        Span envBadge = new Span("ENTERPRISE CLOUD");
        envBadge.getStyle()
                .set("background", "rgba(99, 102, 241, 0.2)")
                .set("color", "#a5b4fc")
                .set("padding", "3px 10px")
                .set("border-radius", "999px")
                .set("font-size", "0.72rem")
                .set("font-weight", "700")
                .set("letter-spacing", "0.6px")
                .set("border", "1px solid rgba(99, 102, 241, 0.35)");

        Span versionBadge = new Span("Enterprise Studio");
        versionBadge.getStyle()
                .set("background", "rgba(148, 163, 184, 0.12)")
                .set("color", "#cbd5e1")
                .set("padding", "3px 10px")
                .set("border-radius", "999px")
                .set("font-size", "0.72rem")
                .set("font-weight", "600");

        badges.add(envBadge, versionBadge);

        // Login Form I18n configuration
        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("Sign In to Account");
        i18n.getForm().setUsername("Username or Email");
        i18n.getForm().setPassword("Password");
        i18n.getForm().setSubmit("Launch Studio");
        i18n.getForm().setForgotPassword("Forgot Password?");
        i18n.getErrorMessage().setTitle("Authentication Failed");
        i18n.getErrorMessage().setMessage("Invalid credentials or account is currently locked. Check details and retry.");
        login.setI18n(i18n);

        login.setAction("login");
        login.setForgotPasswordButtonVisible(true);
        login.addForgotPasswordListener(e -> {
            login.getElement().executeJs("window.location.href = '/api/auth/password-reset/request';");
        });

        login.getStyle()
                .set("width", "100%")
                .set("margin-top", "0");

        // Footer Security Notice
        HorizontalLayout securityRow = new HorizontalLayout();
        securityRow.setSpacing(true);
        securityRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        securityRow.getStyle()
                .set("margin-top", "16px")
                .set("opacity", "0.75");

        Icon lockIcon = new Icon(VaadinIcon.LOCK);
        lockIcon.setSize("12px");
        lockIcon.setColor("#94a3b8");

        Span secureNotice = new Span("256-bit AES cryptographic session isolation");
        secureNotice.getStyle()
                .set("color", "#94a3b8")
                .set("font-size", "0.75rem");

        securityRow.add(lockIcon, secureNotice);

        card.add(emblem, header, subtitle, badges, login, securityRow);
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
