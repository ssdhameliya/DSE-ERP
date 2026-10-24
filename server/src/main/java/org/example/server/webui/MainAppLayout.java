package org.example.server.webui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.example.server.security.AuthenticatedUser;
import org.example.server.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class MainAppLayout extends AppLayout {

    public MainAppLayout() {
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Toggle navigation");

        H1 title = new H1("DSE ERP");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", "700")
                .set("margin", "0")
                .set("letter-spacing", "0.5px")
                .set("color", "var(--lumo-primary-color)");

        Span envBadge = new Span("ENTERPRISE");
        envBadge.getElement().getThemeList().add("badge success small");
        envBadge.getStyle().set("margin-left", "8px");

        HorizontalLayout brandLayout = new HorizontalLayout(toggle, title, envBadge);
        brandLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        HorizontalLayout userActions = new HorizontalLayout();
        userActions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        String username = "User";
        String role = "GUEST";
        try {
            AuthenticatedUser currentUser = CurrentUser.require();
            username = currentUser.username();
            role = currentUser.role();
        } catch (Exception ignored) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                username = auth.getName();
            }
        }

        Span roleBadge = new Span(role);
        roleBadge.getElement().getThemeList().add("badge small");

        Button userBtn = new Button(username, new Icon(VaadinIcon.USER));
        userBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        ContextMenu userMenu = new ContextMenu(userBtn);
        userMenu.setOpenOnClick(true);
        userMenu.addItem("My Profile", e -> UI.getCurrent().navigate("profile"));
        userMenu.addItem("Settings", e -> UI.getCurrent().navigate("settings"));
        userMenu.addItem("Sign Out", e -> UI.getCurrent().getPage().setLocation("/logout"));

        userActions.add(roleBadge, userBtn);

        HorizontalLayout header = new HorizontalLayout(brandLayout, userActions);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle()
                .set("padding", "0 16px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.12)");

        addToNavbar(true, header);
    }

    private void createDrawer() {
        VerticalLayout navContainer = new VerticalLayout();
        navContainer.setPadding(true);
        navContainer.setSpacing(false);
        navContainer.getStyle()
                .set("gap", "4px")
                .set("padding-bottom", "32px");

        // Dashboard
        navContainer.add(createNavLink("Dashboard", VaadinIcon.DASHBOARD, ""));

        // Sales Group
        if (hasAny("SALES.VIEW", "SALES.CREATE", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Sales & Distribution"));
            navContainer.add(createNavLink("Sales Register", VaadinIcon.FILE_TEXT_O, "sales"));
            if (hasAny("SALES.CREATE", "ROLE_ADMIN")) {
                navContainer.add(createNavLink("New Invoice", VaadinIcon.PLUS_CIRCLE, "sales/edit"));
            }
            navContainer.add(createNavLink("Sales Returns", VaadinIcon.ARROW_BACKWARD, "sales/returns"));
            navContainer.add(createNavLink("Credit Notes", VaadinIcon.CREDIT_CARD, "sales/credit-notes"));
            navContainer.add(createNavLink("Quotations", VaadinIcon.RECORDS, "quotations"));
        }

        // Purchase Group
        if (hasAny("PURCHASE.VIEW", "PURCHASE.CREATE", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Procurement"));
            navContainer.add(createNavLink("Purchase Register", VaadinIcon.CART, "purchases"));
            if (hasAny("PURCHASE.CREATE", "ROLE_ADMIN")) {
                navContainer.add(createNavLink("New Inward", VaadinIcon.PLUS_CIRCLE, "purchases/edit"));
            }
            navContainer.add(createNavLink("Purchase Returns", VaadinIcon.ARROW_BACKWARD, "purchases/returns"));
            navContainer.add(createNavLink("Debit Notes", VaadinIcon.MONEY, "purchases/debit-notes"));
            navContainer.add(createNavLink("Purchase Payments", VaadinIcon.INVOICE, "purchases/payments"));
        }

        // Master Data Group
        if (hasAny("MASTERS.VIEW", "INVENTORY.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Master Catalog"));
            navContainer.add(createNavLink("Customers", VaadinIcon.USERS, "master/customers"));
            navContainer.add(createNavLink("Suppliers", VaadinIcon.TRUCK, "master/suppliers"));
            navContainer.add(createNavLink("Item Master", VaadinIcon.PACKAGE, "master/items"));
            navContainer.add(createNavLink("Inventory Stock", VaadinIcon.STORAGE, "master/inventory"));
            navContainer.add(createNavLink("Lookup Values", VaadinIcon.LIST_SELECT, "master/lookups"));
        }

        // Finance & Accounts
        if (hasAny("BANK_EXPENSE.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Finance & Banking"));
            navContainer.add(createNavLink("Bank & Expenses", VaadinIcon.PIGGY_BANK, "finance/bank-expenses"));
            navContainer.add(createNavLink("Record Payment", VaadinIcon.CASH, "finance/record-payment"));
            navContainer.add(createNavLink("General Ledger", VaadinIcon.BOOK, "finance/ledger"));
            navContainer.add(createNavLink("Aging Analysis", VaadinIcon.CALENDAR_CLOCK, "finance/aging"));
            navContainer.add(createNavLink("Financial Statements", VaadinIcon.CHART_LINE, "finance/statements"));
            navContainer.add(createNavLink("Bank Reconciliation", VaadinIcon.EXCHANGE, "banking/reconciliation"));
            navContainer.add(createNavLink("Refunds & Returns", VaadinIcon.ROTATE_LEFT, "banking/refunds"));
        }

        // GST & Compliance
        if (hasAny("REPORTS.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Tax & Compliance"));
            navContainer.add(createNavLink("GST Compliance", VaadinIcon.DIPLOMA, "gst/compliance"));
            navContainer.add(createNavLink("E-Way Bill Hub", VaadinIcon.ROAD, "gst/eway-bill"));
        }

        // Reconciliation
        if (hasAny("PURCHASE_RECON.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Reconciliation"));
            navContainer.add(createNavLink("Purchase Recon", VaadinIcon.CHECK_SQUARE_O, "recon/purchase"));
            navContainer.add(createNavLink("Supplier Recon", VaadinIcon.OFFICE, "recon/suppliers"));
        }

        // Reports & Intelligence
        if (hasAny("REPORTS.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Intelligence & Reports"));
            navContainer.add(createNavLink("Reports Hub", VaadinIcon.CHART, "reports"));
            navContainer.add(createNavLink("Customer 360", VaadinIcon.USER_CARD, "crm/customer-360"));
            navContainer.add(createNavLink("Supplier 360", VaadinIcon.BUILDING, "crm/supplier-360"));
        }

        // Document Studio & Barcode
        if (hasAny("DOCUMENT_STUDIO.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Studio & Print"));
            navContainer.add(createNavLink("Document Studio", VaadinIcon.FILE_PICTURE, "documents/studio"));
            navContainer.add(createNavLink("Barcode Studio", VaadinIcon.BARCODE, "documents/barcodes"));
        }

        // Communication
        navContainer.add(createSectionHeader("Communication"));
        navContainer.add(createNavLink("Notifications", VaadinIcon.BELL, "notifications"));
        navContainer.add(createNavLink("Reminders", VaadinIcon.ALARM, "notifications/reminders"));
        navContainer.add(createNavLink("Email Center", VaadinIcon.ENVELOPE_O, "notifications/communication"));

        // Admin
        if (hasAny("USERS.VIEW", "USERS.MANAGE_ROLES", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Administration"));
            navContainer.add(createNavLink("User Management", VaadinIcon.KEY, "admin/users"));
            navContainer.add(createNavLink("Role Management", VaadinIcon.SHIELD, "admin/roles"));
            navContainer.add(createNavLink("Permission Matrix", VaadinIcon.GRID_SMALL, "admin/permissions"));
            navContainer.add(createNavLink("Registrations", VaadinIcon.USER_CHECK, "admin/registrations"));
            navContainer.add(createNavLink("Global Audit", VaadinIcon.EYE, "admin/audit"));
            navContainer.add(createNavLink("Automation Center", VaadinIcon.COG, "admin/automation"));
            navContainer.add(createNavLink("Backup & Restore", VaadinIcon.DATABASE, "admin/backup"));
        }

        // System
        navContainer.add(createSectionHeader("System"));
        navContainer.add(createNavLink("Settings", VaadinIcon.COGS, "settings"));
        navContainer.add(createNavLink("Profile", VaadinIcon.USER, "profile"));

        addToDrawer(new Scroller(navContainer));
    }

    private Div createSectionHeader(String title) {
        Div header = new Div(new Text(title));
        header.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.8px")
                .set("margin-top", "16px")
                .set("margin-bottom", "4px")
                .set("padding-left", "12px");
        return header;
    }

    private Anchor createNavLink(String text, VaadinIcon icon, String route) {
        String href = route.isEmpty() ? "/" : "/" + route;

        Icon linkIcon = icon.create();
        linkIcon.getStyle()
                .set("margin-right", "10px")
                .set("width", "16px")
                .set("height", "16px");

        Span label = new Span(text);

        HorizontalLayout content = new HorizontalLayout(linkIcon, label);
        content.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        content.getStyle()
                .set("padding", "6px 12px")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("color", "var(--lumo-body-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "500")
                .set("transition", "all 0.15s ease");

        Anchor link = new Anchor(href, content);
        link.getStyle().set("text-decoration", "none").set("display", "block");
        return link;
    }

    private boolean hasAny(String... authorities) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        for (String authority : authorities) {
            if ("ROLE_ADMIN".equalsIgnoreCase(authority)) {
                boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
                if (isAdmin) return true;
            }
            boolean match = auth.getAuthorities().stream()
                    .anyMatch(a -> authority.equalsIgnoreCase(a.getAuthority())
                            || ("ROLE_" + authority).equalsIgnoreCase(a.getAuthority()));
            if (match) return true;
        }
        return false;
    }
}
