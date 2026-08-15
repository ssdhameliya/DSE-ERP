package org.example.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.example.config.ConfigManager;
import org.example.service.BrandingService;
import org.example.util.ClockService;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One application-wide footer contract used by authentication screens and the
 * logged-in shell. Optional company fields disappear instead of rendering
 * placeholders; all clocks share the application's single ClockService pulse.
 */
public final class SharedApplicationFooter extends HBox {
    private static final List<WeakReference<SharedApplicationFooter>> INSTANCES = new CopyOnWriteArrayList<>();

    private final Label trust = new Label("Secure • Local • Reliable");
    private final Label company = new Label();
    private final Label phone = new Label();
    private final Label email = new Label();
    private final Label website = new Label();
    private final Label clock = new Label();

    public SharedApplicationFooter() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setMinHeight(38);
        setPrefHeight(38);
        getStyleClass().addAll("universal-app-footer", "auth-footer");
        trust.getStyleClass().add("universal-footer-trust");
        company.getStyleClass().add("universal-footer-company");
        phone.getStyleClass().add("universal-footer-detail");
        email.getStyleClass().add("universal-footer-detail");
        website.getStyleClass().add("universal-footer-detail");
        clock.getStyleClass().addAll("universal-footer-clock", "auth-footer-clock");

        for (Label label : List.of(trust, company, phone, email, website, clock)) {
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
        }
        trust.setAlignment(Pos.CENTER_LEFT);
        company.setAlignment(Pos.CENTER_LEFT);
        phone.setAlignment(Pos.CENTER);
        email.setAlignment(Pos.CENTER);
        website.setAlignment(Pos.CENTER);
        clock.setAlignment(Pos.CENTER_RIGHT);
        setStyle("-fx-padding: 8 14 8 14;");
        INSTANCES.add(new WeakReference<>(this));
        refresh();
        ClockService.start(clock);
    }

    private Label separator() {
        Label label = new Label("•");
        label.getStyleClass().add("universal-footer-separator");
        return label;
    }

    public void refresh() {
        company.setText(nonBlank(BrandingService.companyName(), BrandingService.applicationName()));
        updateOptional(phone, ConfigManager.get("company.phone", ""), "☎ " );
        updateOptional(email, ConfigManager.get("company.email", ""), "✉ " );
        updateOptional(website, ConfigManager.get("company.website", ""), "🌐 " );
        rebuildChildren();
    }

    private static void updateOptional(Label label, String value, String prefix) {
        String clean = value == null ? "" : value.trim();
        boolean show = !clean.isBlank();
        label.setText(show ? prefix + clean : "");
        label.setVisible(show);
        label.setManaged(show);
    }

    private void rebuildChildren() {
        getChildren().clear();
        List<Label> fields = new java.util.ArrayList<>();
        fields.add(trust);
        fields.add(company);
        if (phone.isManaged()) fields.add(phone);
        if (email.isManaged()) fields.add(email);
        if (website.isManaged()) fields.add(website);
        fields.add(clock);
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) getChildren().add(separator());
            getChildren().add(fields.get(i));
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Refreshes every currently alive footer after Settings are saved. */
    public static void refreshAll() {
        INSTANCES.removeIf(ref -> ref.get() == null);
        for (WeakReference<SharedApplicationFooter> ref : INSTANCES) {
            SharedApplicationFooter footer = ref.get();
            if (footer != null) footer.refresh();
        }
    }
}
