package org.example.navigation;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dynamic Multi-Document Workspace Tab Manager for DSE ERP.
 * Manages open tabs, tab switching, and keyboard shortcuts across all screens.
 */
public final class WorkspaceTabManager {

    private static WorkspaceTabManager instance;

    private HBox tabContainer;
    private StackPane contentPane;
    private final List<TabEntry> openTabs = new ArrayList<>();
    private TabEntry activeTab;
    private static boolean acceleratorsInstalled = false;

    public static final class TabEntry {
        private final String id;
        private final String title;
        private final String fxml;
        private Node content;
        private Object controller;
        private final boolean closeable;
        private final HBox uiNode;

        public TabEntry(String id, String title, String fxml, Node content, Object controller, boolean closeable, HBox uiNode) {
            this.id = id;
            this.title = title;
            this.fxml = fxml;
            this.content = content;
            this.controller = controller;
            this.closeable = closeable;
            this.uiNode = uiNode;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getFxml() { return fxml; }
        public Node getContent() { return content; }
        public void setContent(Node content) { this.content = content; }
        public Object getController() { return controller; }
        public void setController(Object controller) { this.controller = controller; }
        public boolean isCloseable() { return closeable; }
        public HBox getUiNode() { return uiNode; }
    }

    private WorkspaceTabManager() {}

    public static synchronized WorkspaceTabManager getInstance() {
        if (instance == null) {
            instance = new WorkspaceTabManager();
        }
        return instance;
    }

    public static boolean isAttached() {
        return instance != null && instance.tabContainer != null && instance.contentPane != null;
    }

    public static void bind(HBox container, StackPane pane) {
        WorkspaceTabManager mgr = getInstance();
        mgr.tabContainer = container;
        mgr.contentPane = pane;
        mgr.openTabs.clear();
        mgr.activeTab = null;
        if (container != null) {
            container.getChildren().clear();
        }
    }

    public List<TabEntry> getOpenTabs() {
        return Collections.unmodifiableList(openTabs);
    }

    public TabEntry getActiveTab() {
        return activeTab;
    }

    public void onPageLoaded(String fxml, Node content, Object controller) {
        if (tabContainer == null || contentPane == null) return;

        // Check if page already has an open tab
        for (TabEntry tab : openTabs) {
            if (Objects.equals(tab.getFxml(), fxml)) {
                if (content != null) tab.setContent(content);
                if (controller != null) tab.setController(controller);
                activateTab(tab);
                return;
            }
        }

        // Create new tab
        String title = resolveTitle(fxml);
        String iconSvg = resolveIconSvg(fxml);
        boolean closeable = !isPermanentTab(fxml);
        String tabId = fxml + "-" + System.currentTimeMillis();

        HBox tabNode = createTabUi(title, iconSvg, closeable);
        TabEntry newTab = new TabEntry(tabId, title, fxml, content, controller, closeable, tabNode);

        tabNode.setOnMouseClicked(event -> {
            activateTab(newTab);
            event.consume();
        });

        openTabs.add(newTab);
        tabContainer.getChildren().add(tabNode);
        activateTab(newTab);
    }

    public void activateTab(TabEntry tab) {
        if (tab == null || !openTabs.contains(tab)) return;

        if (activeTab != null && activeTab != tab && activeTab.getController() instanceof ScreenLifecycle lifecycle) {
            try { lifecycle.onScreenHidden(); } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        }

        activeTab = tab;

        // Update UI styles
        for (TabEntry entry : openTabs) {
            entry.getUiNode().getStyleClass().remove("active");
        }
        if (!tab.getUiNode().getStyleClass().contains("active")) {
            tab.getUiNode().getStyleClass().add("active");
        }

        // Attach content to contentPane
        if (contentPane != null) {
            contentPane.getChildren().setAll(tab.getContent());
        }

        // Notify shown
        if (tab.getController() instanceof ScreenLifecycle lifecycle) {
            try { lifecycle.onScreenShown(true); } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        }
    }

    public void closeTabByFxml(String fxml) {
        if (fxml == null) return;
        for (TabEntry tab : new ArrayList<>(openTabs)) {
            if (Objects.equals(tab.getFxml(), fxml) && tab.isCloseable()) {
                closeTab(tab);
                break;
            }
        }
    }

    public void closeTab(TabEntry tab) {
        if (tab == null || !tab.isCloseable()) return;

        int index = openTabs.indexOf(tab);
        if (index < 0) return;

        if (tab.getController() instanceof ScreenLifecycle lifecycle) {
            try { lifecycle.onScreenHidden(); } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
        }

        openTabs.remove(tab);
        tabContainer.getChildren().remove(tab.getUiNode());

        if (activeTab == tab) {
            if (!openTabs.isEmpty()) {
                int nextIndex = Math.min(index, openTabs.size() - 1);
                activateTab(openTabs.get(nextIndex));
            } else {
                NavigationManager.navigateOrReport("/fxml/pages/DashboardHome.fxml");
            }
        }
    }

    public void selectNextTab() {
        if (openTabs.size() <= 1 || activeTab == null) return;
        int idx = openTabs.indexOf(activeTab);
        int next = (idx + 1) % openTabs.size();
        activateTab(openTabs.get(next));
    }

    public void selectPreviousTab() {
        if (openTabs.size() <= 1 || activeTab == null) return;
        int idx = openTabs.indexOf(activeTab);
        int prev = (idx - 1 + openTabs.size()) % openTabs.size();
        activateTab(openTabs.get(prev));
    }

    public void closeActiveTab() {
        if (activeTab != null && activeTab.isCloseable()) {
            closeTab(activeTab);
        }
    }

    public static void installAccelerators(Scene scene) {
        if (scene == null || acceleratorsInstalled) return;
        acceleratorsInstalled = true;

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.TAB) {
                if (event.isShiftDown()) {
                    getInstance().selectPreviousTab();
                } else {
                    getInstance().selectNextTab();
                }
                event.consume();
            } else if ((event.isControlDown() && event.getCode() == KeyCode.W)
                    || (event.isAltDown() && event.getCode() == KeyCode.W)) {
                getInstance().closeActiveTab();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.T) {
                Node search = scene.lookup("#txtSearch");
                if (search != null) search.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.F9) {
                NavigationManager.navigateOrReport("/fxml/pages/Sale.fxml");
                event.consume();
            }
        });
    }

    private HBox createTabUi(String title, String svgPath, boolean closeable) {
        HBox tabNode = new HBox(7);
        tabNode.setAlignment(Pos.CENTER_LEFT);
        tabNode.getStyleClass().add("workspace-tab");

        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.getStyleClass().add("workspace-tab-icon");

        Label label = new Label(title);
        label.getStyleClass().add("workspace-tab-label");

        tabNode.getChildren().addAll(icon, label);

        if (closeable) {
            Label closeBtn = new Label("✕");
            closeBtn.getStyleClass().add("workspace-tab-close");
            closeBtn.setOnMouseClicked(event -> {
                event.consume();
                TabEntry target = findTabByNode(tabNode);
                if (target != null) {
                    closeTab(target);
                }
            });
            tabNode.getChildren().add(closeBtn);
        }

        return tabNode;
    }

    private TabEntry findTabByNode(HBox node) {
        for (TabEntry entry : openTabs) {
            if (entry.getUiNode() == node) return entry;
        }
        return null;
    }

    private boolean isPermanentTab(String fxml) {
        return fxml.contains("DashboardHome") || fxml.contains("Dashboard.fxml");
    }

    private String resolveTitle(String fxml) {
        if (fxml.contains("DashboardHome") || fxml.contains("Dashboard.fxml")) return "Dashboard";
        if (fxml.contains("Sale.fxml")) return "Sale Invoice";
        if (fxml.contains("Purchase.fxml")) return "Purchase Invoice";
        if (fxml.contains("SalesList.fxml")) return "Sales Register";
        if (fxml.contains("PurchaseList.fxml")) return "Purchase Register";
        if (fxml.contains("Customer.fxml") || fxml.contains("Customer360.fxml")) return "Customer 360";
        if (fxml.contains("Supplier.fxml") || fxml.contains("Supplier360.fxml")) return "Supplier 360";
        if (fxml.contains("ItemMaster.fxml")) return "Item Master";
        if (fxml.contains("Inventory.fxml")) return "Inventory";
        if (fxml.contains("QuotationEditor.fxml") || fxml.contains("Quotation.fxml")) return "Quotation";
        if (fxml.contains("BankExpense.fxml")) return "Bank & Expense";
        if (fxml.contains("GstReport.fxml")) return "GST Reports";
        if (fxml.contains("ReportingDashboard.fxml")) return "Reports";
        if (fxml.contains("Settings.fxml")) return "Settings";
        if (fxml.contains("Profile.fxml")) return "Profile";
        if (fxml.contains("BarcodeStudio.fxml")) return "Barcode Studio";
        if (fxml.contains("Import.fxml")) return "Data Import";
        if (fxml.contains("AutomationCenter.fxml")) return "Automation";

        // Clean filename fallback: "GeneralLedger.fxml" -> "General Ledger"
        String name = fxml.substring(fxml.lastIndexOf('/') + 1).replace(".fxml", "");
        return name.replaceAll("([A-Z])", " $1").trim();
    }

    private String resolveIconSvg(String fxml) {
        if (fxml.contains("Dashboard")) {
            return "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z";
        }
        if (fxml.contains("Sale")) {
            return "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z";
        }
        if (fxml.contains("Purchase")) {
            return "M19 6h-2c0-2.76-2.24-5-5-5S7 3.24 7 6H5c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-7-3c1.66 0 3 1.34 3 3H9c0-1.66 1.34-3 3-3zm7 17H5V8h14v12z";
        }
        if (fxml.contains("Customer") || fxml.contains("Supplier") || fxml.contains("Party")) {
            return "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
        }
        if (fxml.contains("Item") || fxml.contains("Inventory")) {
            return "M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z";
        }
        if (fxml.contains("Report") || fxml.contains("Gst")) {
            return "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z";
        }
        if (fxml.contains("Bank") || fxml.contains("Expense") || fxml.contains("Payment")) {
            return "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";
        }
        return "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
    }
}
