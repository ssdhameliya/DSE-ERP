package org.example.ui;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.example.api.ApiSession;
import org.example.config.ConfigManager;
import org.example.service.BrandingService;
import org.example.util.ClockService;
import org.example.util.IconFactory;
import org.example.util.UiTaskExecutor;

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

    private final Label trust = new Label("Secure   Local   Reliable");
    private final Label company = new Label();
    private final Label phone = new Label();
    private final Label email = new Label();
    private final Label website = new Label();
    private final Label clock = new Label();

    public SharedApplicationFooter() {
        getStyleClass().add("universal-app-footer");
        trust.getStyleClass().add("universal-footer-trust");
        company.getStyleClass().add("universal-footer-company");
        phone.getStyleClass().add("universal-footer-detail");
        email.getStyleClass().add("universal-footer-detail");
        website.getStyleClass().add("universal-footer-detail");
        clock.getStyleClass().add("universal-footer-clock");

        company.setGraphic(IconFactory.compactIcon("identity", 13));
        phone.setGraphic(IconFactory.compactIcon("phone", 13));
        email.setGraphic(IconFactory.compactIcon("email", 13));
        website.setGraphic(IconFactory.compactIcon("link", 13));
        clock.setGraphic(IconFactory.compactIcon("calendar", 13));
        for (Label label : List.of(company, phone, email, website, clock)) label.setGraphicTextGap(6);

        for (Label label : List.of(trust, company, phone, email, website, clock)) {
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
        }
        INSTANCES.add(new WeakReference<>(this));
        refresh();
        ClockService.start(clock);
    }


    public void refresh() {
        if (Platform.isFxApplicationThread() && ConfigManager.isSharedClient() && ApiSession.isEstablished()) {
            // Never make company-server calls from FXML construction or a Settings refresh.
            // Paint immediately from the local/cached identity, then apply the remote footer snapshot.
            company.setText(nonBlank(BrandingService.companyName(), BrandingService.technicalProductName()));
            rebuildChildren();
            String taskKey = "shared-application-footer-" + System.identityHashCode(this);
            UiTaskExecutor.submitLatest(
                    taskKey,
                    this::readSnapshot,
                    this::applySnapshot,
                    failure -> { }
            );
            return;
        }
        applySnapshot(readSnapshot());
    }

    private FooterSnapshot readSnapshot() {
        return new FooterSnapshot(
                nonBlank(BrandingService.companyName(), BrandingService.technicalProductName()),
                ConfigManager.get("company.phone", ""),
                ConfigManager.get("company.email", ""),
                ConfigManager.get("company.website", "")
        );
    }

    private void applySnapshot(FooterSnapshot snapshot) {
        company.setText(snapshot.companyName());
        updateOptional(phone, snapshot.phone());
        updateOptional(email, snapshot.email());
        updateOptional(website, snapshot.website());
        rebuildChildren();
    }

    private record FooterSnapshot(String companyName, String phone, String email, String website) { }

    private static void updateOptional(Label label, String value) {
        String clean = value == null ? "" : value.trim();
        boolean show = !clean.isBlank();
        label.setText(show ? clean : "");
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
        getChildren().addAll(fields);
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
