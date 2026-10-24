package org.example.server.webui;

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
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import org.example.server.security.AuthenticatedUser;
import org.example.server.security.CurrentUser;
import org.example.server.webui.dashboard.DashboardView;
import org.example.server.webui.sales.SalesListView;
import org.example.server.webui.purchase.PurchaseListView;
import org.example.server.webui.master.ItemMasterView;
import org.example.server.webui.master.CustomerView;
import org.example.server.webui.master.SupplierView;
import org.example.server.webui.master.InventoryView;
import org.example.server.webui.finance.GeneralLedgerView;
import org.example.server.webui.finance.BankExpenseView;
import org.example.server.webui.gst.GstComplianceCenterView;
import org.example.server.webui.reporting.ReportsView;
import org.example.server.webui.documents.DocumentStudioView;
import org.example.server.webui.settings.SettingsView;
import org.example.server.webui.profile.ProfileView;
import org.example.server.webui.admin.UserAccessView;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@PermitAll
public class MainAppLayout extends AppLayout {

    public MainAppLayout() {
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Toggle navigation");
        toggle.getStyle().set("color", "#64748b");

        // Brand Emblem + Title
        Div logoBadge = new Div();
        logoBadge.getStyle()
                .set("width", "32px")
                .set("height", "32px")
                .set("border-radius", "8px")
                .set("background", "linear-gradient(135deg, #6366f1 0%, #3b82f6 100%)")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("box-shadow", "0 2px 8px rgba(99, 102, 241, 0.4)");

        Icon logoIcon = new Icon(VaadinIcon.BUILDING);
        logoIcon.setSize("16px");
        logoIcon.setColor("#ffffff");
        logoBadge.add(logoIcon);

        H1 title = new H1("DSE ERP");
        title.getStyle()
                .set("font-size", "1.15rem")
                .set("font-weight", "800")
                .set("margin", "0")
                .set("letter-spacing", "-0.3px")
                .set("color", "var(--lumo-header-text-color)");

        Span envBadge = new Span("ENTERPRISE");
        envBadge.getStyle()
                .set("background", "rgba(16, 185, 129, 0.15)")
                .set("color", "#10b981")
                .set("padding", "2px 8px")
                .set("border-radius", "999px")
                .set("font-size", "0.68rem")
                .set("font-weight", "700")
                .set("border", "1px solid rgba(16, 185, 129, 0.3)");

        HorizontalLayout brandLayout = new HorizontalLayout(toggle, logoBadge, title, envBadge);
        brandLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        brandLayout.setSpacing(true);

        // User info & action pill
        HorizontalLayout userActions = new HorizontalLayout();
        userActions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        userActions.setSpacing(true);

        String username = "User";
        String role = "ADMIN";
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

        String initial = (username.length() > 0 ? username.substring(0, 1).toUpperCase() : "U");
        Div avatar = new Div(new Span(initial));
        avatar.getStyle()
                .set("width", "30px")
                .set("height", "30px")
                .set("border-radius", "50%")
                .set("background", "linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)")
                .set("color", "#ffffff")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "0.85rem")
                .set("font-weight", "700");

        Span rolePill = new Span(role);
        rolePill.getStyle()
                .set("background", "var(--lumo-contrast-10pct)")
                .set("padding", "2px 8px")
                .set("border-radius", "6px")
                .set("font-size", "0.72rem")
                .set("font-weight", "600");

        Button userBtn = new Button(username, avatar);
        userBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        userBtn.getStyle()
                .set("font-weight", "600")
                .set("gap", "8px");

        ContextMenu userMenu = new ContextMenu(userBtn);
        userMenu.setOpenOnClick(true);
        userMenu.addItem("My Profile", e -> UI.getCurrent().navigate("profile"));
        userMenu.addItem("System Settings", e -> UI.getCurrent().navigate("settings"));
        userMenu.addItem("Sign Out", e -> UI.getCurrent().getPage().setLocation("/logout"));

        userActions.add(rolePill, userBtn);

        HorizontalLayout header = new HorizontalLayout(brandLayout, userActions);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle()
                .set("padding", "0 18px")
                .set("height", "56px")
                .set("background", "var(--lumo-base-color)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.04)");

        addToNavbar(true, header);
    }

    private void createDrawer() {
        VerticalLayout navContainer = new VerticalLayout();
        navContainer.setPadding(true);
        navContainer.setSpacing(false);
        navContainer.getStyle()
                .set("gap", "2px")
                .set("padding", "12px 10px 40px 10px");

        // Dashboard (Home)
        navContainer.add(createNavLink("Executive Dashboard", VaadinIcon.DASHBOARD, "#3b82f6", DashboardView.class));

        // Sales Group
        if (hasAny("SALES.VIEW", "SALES.CREATE", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Sales & Revenue", "#3b82f6"));
            navContainer.add(createNavLink("Sales Register", VaadinIcon.FILE_TEXT_O, "#3b82f6", SalesListView.class));
            navContainer.add(createNavLink("Quotations", VaadinIcon.RECORDS, "#3b82f6", "quotations"));
            navContainer.add(createNavLink("Sales Returns", VaadinIcon.ARROW_BACKWARD, "#3b82f6", "sales/returns"));
            navContainer.add(createNavLink("Credit Notes", VaadinIcon.CREDIT_CARD, "#3b82f6", "sales/credit-notes"));
        }

        // Procurement Group
        if (hasAny("PURCHASE.VIEW", "PURCHASE.CREATE", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Procurement", "#10b981"));
            navContainer.add(createNavLink("Purchase Register", VaadinIcon.CART, "#10b981", PurchaseListView.class));
            navContainer
                    .add(createNavLink("Purchase Returns", VaadinIcon.ARROW_BACKWARD, "#10b981", "purchases/returns"));
            navContainer.add(createNavLink("Debit Notes", VaadinIcon.MONEY, "#10b981", "purchases/debit-notes"));
            navContainer.add(createNavLink("Vendor Payments", VaadinIcon.INVOICE, "#10b981", "purchases/payments"));
        }

        // Master Catalog
        if (hasAny("MASTERS.VIEW", "INVENTORY.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Master Catalog", "#f59e0b"));
            navContainer.add(createNavLink("Customers", VaadinIcon.USERS, "#f59e0b", CustomerView.class));
            navContainer.add(createNavLink("Suppliers", VaadinIcon.TRUCK, "#f59e0b", SupplierView.class));
            navContainer.add(createNavLink("Item Master", VaadinIcon.PACKAGE, "#f59e0b", ItemMasterView.class));
            navContainer.add(createNavLink("Inventory Stock", VaadinIcon.STORAGE, "#f59e0b", InventoryView.class));
            navContainer
                    .add(createNavLink("Lookups & Categories", VaadinIcon.LIST_SELECT, "#f59e0b", "master/lookups"));
        }

        // Finance & Accounts
        if (hasAny("BANK_EXPENSE.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Finance & Banking", "#8b5cf6"));
            navContainer.add(createNavLink("General Ledger", VaadinIcon.BOOK, "#8b5cf6", GeneralLedgerView.class));
            navContainer.add(createNavLink("Bank Expenses", VaadinIcon.PIGGY_BANK, "#8b5cf6", BankExpenseView.class));
            navContainer.add(createNavLink("Record Payment", VaadinIcon.CASH, "#8b5cf6", "finance/record-payment"));
            navContainer.add(createNavLink("Aging Analysis", VaadinIcon.CALENDAR_CLOCK, "#8b5cf6", "finance/aging"));
            navContainer
                    .add(createNavLink("Financial Statements", VaadinIcon.CHART_LINE, "#8b5cf6", "finance/statements"));
            navContainer.add(
                    createNavLink("Bank Reconciliation", VaadinIcon.EXCHANGE, "#8b5cf6", "banking/reconciliation"));
        }

        // Compliance & Tax
        if (hasAny("REPORTS.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Tax & Compliance", "#6366f1"));
            navContainer
                    .add(createNavLink("GST Compliance", VaadinIcon.DIPLOMA, "#6366f1", GstComplianceCenterView.class));
        }

        // Business Intelligence
        if (hasAny("REPORTS.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Intelligence & Reports", "#ec4899"));
            navContainer.add(createNavLink("Reports Hub", VaadinIcon.CHART, "#ec4899", ReportsView.class));
            navContainer.add(createNavLink("Customer 360", VaadinIcon.USER_CARD, "#ec4899", "crm/customer-360"));
            navContainer.add(createNavLink("Supplier 360", VaadinIcon.BUILDING, "#ec4899", "crm/supplier-360"));
        }

        // Document Studio
        if (hasAny("DOCUMENT_STUDIO.VIEW", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Studio & Print", "#06b6d4"));
            navContainer.add(
                    createNavLink("Document Studio", VaadinIcon.FILE_PICTURE, "#06b6d4", DocumentStudioView.class));
            navContainer.add(createNavLink("Barcode Studio", VaadinIcon.BARCODE, "#06b6d4", "documents/barcodes"));
        }

        // Administration
        if (hasAny("USERS.VIEW", "USERS.MANAGE_ROLES", "ROLE_ADMIN")) {
            navContainer.add(createSectionHeader("Administration", "#ef4444"));
            navContainer.add(createNavLink("User Management", VaadinIcon.KEY, "#ef4444", UserAccessView.class));
            navContainer.add(createNavLink("Role Management", VaadinIcon.SHIELD, "#ef4444", "admin/roles"));
            navContainer.add(createNavLink("Permission Matrix", VaadinIcon.GRID_SMALL, "#ef4444", "admin/permissions"));
            navContainer.add(createNavLink("Global Audit", VaadinIcon.EYE, "#ef4444", "admin/audit"));
            navContainer.add(createNavLink("Backup & Recovery", VaadinIcon.DATABASE, "#ef4444", "admin/backup"));
        }

        // System
        navContainer.add(createSectionHeader("System", "#64748b"));
        navContainer.add(createNavLink("Settings", VaadinIcon.COGS, "#64748b", SettingsView.class));
        navContainer.add(createNavLink("Profile", VaadinIcon.USER, "#64748b", ProfileView.class));

        Scroller scroller = new Scroller(navContainer);
        scroller.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-right", "1px solid var(--lumo-contrast-10pct)");
        addToDrawer(scroller);
    }

    private Div createSectionHeader(String title, String accentColor) {
        Div header = new Div(new Span(title));
        header.getStyle()
                .set("font-size", "0.7rem")
                .set("font-weight", "700")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.8px")
                .set("margin-top", "14px")
                .set("margin-bottom", "4px")
                .set("padding-left", "10px")
                .set("border-left", "2px solid " + accentColor);
        return header;
    }

    private RouterLink createNavLink(String text, VaadinIcon icon, String iconColor,
            Class<? extends com.vaadin.flow.component.Component> target) {
        RouterLink link = new RouterLink(target);
        link.add(buildLinkContent(text, icon, iconColor));
        styleRouterLink(link);
        return link;
    }

    private RouterLink createNavLink(String text, VaadinIcon icon, String iconColor, String route) {
        RouterLink link = new RouterLink();
        link.getElement().setAttribute("href", route);
        link.add(buildLinkContent(text, icon, iconColor));
        styleRouterLink(link);
        return link;
    }

    private HorizontalLayout buildLinkContent(String text, VaadinIcon icon, String iconColor) {
        Icon linkIcon = icon.create();
        linkIcon.getStyle()
                .set("width", "16px")
                .set("height", "16px")
                .set("color", iconColor)
                .set("flex-shrink", "0");

        Span label = new Span(text);
        label.getStyle()
                .set("font-size", "0.85rem")
                .set("font-weight", "500")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");

        HorizontalLayout content = new HorizontalLayout(linkIcon, label);
        content.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        content.setSpacing(true);
        content.getStyle()
                .set("padding", "7px 10px")
                .set("border-radius", "8px")
                .set("width", "100%")
                .set("color", "var(--lumo-body-text-color)");
        return content;
    }

    private void styleRouterLink(RouterLink link) {
        link.getStyle()
                .set("text-decoration", "none")
                .set("display", "block")
                .set("width", "100%")
                .set("border-radius", "8px")
                .set("transition", "background 0.15s ease");
    }

    private boolean hasAny(String... authorities) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            return false;
        for (String authority : authorities) {
            if ("ROLE_ADMIN".equalsIgnoreCase(authority)) {
                boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
                if (isAdmin)
                    return true;
            }
            boolean match = auth.getAuthorities().stream()
                    .anyMatch(a -> authority.equalsIgnoreCase(a.getAuthority())
                            || ("ROLE_" + authority).equalsIgnoreCase(a.getAuthority()));
            if (match)
                return true;
        }
        return false;
    }
}
