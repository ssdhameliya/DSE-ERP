package org.example.server.webui.settings;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.webui.MainAppLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "settings", layout = MainAppLayout.class)
@PageTitle("System Settings | DSE ERP")
@PermitAll
public class SettingsView extends VerticalLayout {

    private final JpaNativeRepository jdbc;

    // Company Profile
    private final TextField companyName = new TextField("Company Legal Name");
    private final TextField companyGstin = new TextField("GSTIN");
    private final TextField companyPhone = new TextField("Support Phone");
    private final TextField companyEmail = new TextField("Official Email");
    private final TextField companyAddress = new TextField("Registered Office Address");

    // Numbering Rules
    private final TextField salesPrefix = new TextField("Sales Invoice Prefix", "INV-");
    private final TextField purchasePrefix = new TextField("Purchase Bill Prefix", "PUR-");
    private final TextField quotationPrefix = new TextField("Quotation Prefix", "QUO-");
    private final TextField returnPrefix = new TextField("Return Document Prefix", "RET-");

    // Email SMTP
    private final TextField smtpHost = new TextField("SMTP Host", "smtp.gmail.com");
    private final TextField smtpPort = new TextField("SMTP Port", "587");
    private final TextField smtpUsername = new TextField("SMTP Username / Email");
    private final PasswordField smtpPassword = new PasswordField("App Password / Secret");

    public SettingsView(JpaNativeRepository jdbc) {
        this.jdbc = jdbc;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Enterprise Global Configuration & Parameters");
        title.getStyle().set("margin", "0");

        Button saveAllBtn = new Button("Save All Settings", VaadinIcon.CHECK.create(), e -> saveAllSettings());
        saveAllBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, saveAllBtn);
        header.expand(title);
        add(header);

        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        tabSheet.add("Company Organization Profile", buildCompanyTab());
        tabSheet.add("Document Sequences & Prefixes", buildPrefixTab());
        tabSheet.add("Outbound Email Server (SMTP)", buildSmtpTab());

        add(tabSheet);
        loadCurrentSettings();
    }

    private VerticalLayout buildCompanyTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        companyName.setWidth("450px");
        companyGstin.setWidth("300px");
        companyPhone.setWidth("300px");
        companyEmail.setWidth("350px");
        companyAddress.setWidthFull();

        layout.add(
                new HorizontalLayout(companyName, companyGstin),
                new HorizontalLayout(companyPhone, companyEmail),
                companyAddress
        );
        return layout;
    }

    private VerticalLayout buildPrefixTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.add(
                new HorizontalLayout(salesPrefix, purchasePrefix),
                new HorizontalLayout(quotationPrefix, returnPrefix)
        );
        return layout;
    }

    private VerticalLayout buildSmtpTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        smtpUsername.setWidth("350px");
        smtpPassword.setWidth("350px");

        layout.add(
                new HorizontalLayout(smtpHost, smtpPort),
                new HorizontalLayout(smtpUsername, smtpPassword)
        );
        return layout;
    }

    private void loadCurrentSettings() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT setting_key, setting_value FROM application_setting");
            Map<String, String> map = new HashMap<>();
            for (Map<String, Object> r : rows) {
                map.put(String.valueOf(r.get("setting_key")), String.valueOf(r.get("setting_value")));
            }

            if (map.containsKey("company.name")) companyName.setValue(map.get("company.name"));
            if (map.containsKey("company.gstin")) companyGstin.setValue(map.get("company.gstin"));
            if (map.containsKey("company.phone")) companyPhone.setValue(map.get("company.phone"));
            if (map.containsKey("company.email")) companyEmail.setValue(map.get("company.email"));
            if (map.containsKey("company.address")) companyAddress.setValue(map.get("company.address"));

            if (map.containsKey("sequence.sale.prefix")) salesPrefix.setValue(map.get("sequence.sale.prefix"));
            if (map.containsKey("sequence.purchase.prefix")) purchasePrefix.setValue(map.get("sequence.purchase.prefix"));
            if (map.containsKey("sequence.quotation.prefix")) quotationPrefix.setValue(map.get("sequence.quotation.prefix"));

            if (map.containsKey("mail.smtp.host")) smtpHost.setValue(map.get("mail.smtp.host"));
            if (map.containsKey("mail.smtp.port")) smtpPort.setValue(map.get("mail.smtp.port"));
            if (map.containsKey("mail.smtp.user")) smtpUsername.setValue(map.get("mail.smtp.user"));
        } catch (Exception ignored) {}
    }

    private void saveAllSettings() {
        try {
            saveSetting("company.name", companyName.getValue());
            saveSetting("company.gstin", companyGstin.getValue());
            saveSetting("company.phone", companyPhone.getValue());
            saveSetting("company.email", companyEmail.getValue());
            saveSetting("company.address", companyAddress.getValue());

            saveSetting("sequence.sale.prefix", salesPrefix.getValue());
            saveSetting("sequence.purchase.prefix", purchasePrefix.getValue());
            saveSetting("sequence.quotation.prefix", quotationPrefix.getValue());

            saveSetting("mail.smtp.host", smtpHost.getValue());
            saveSetting("mail.smtp.port", smtpPort.getValue());
            saveSetting("mail.smtp.user", smtpUsername.getValue());
            if (!smtpPassword.isEmpty()) {
                saveSetting("mail.smtp.password", smtpPassword.getValue());
            }

            Notification.show("Settings successfully updated and saved across system!", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            Notification.show("Failed to save settings: " + ex.getMessage(), 3500, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void saveSetting(String key, String value) {
        if (value == null) return;
        jdbc.update("""
            INSERT INTO application_setting (setting_key, setting_value)
            VALUES (?, ?)
            ON CONFLICT (setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value
            """, key, value);
    }
}
