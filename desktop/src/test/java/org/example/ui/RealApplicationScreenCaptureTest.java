package org.example.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.controller.DashboardController;
import org.example.controller.DashboardHomeController;
import org.example.controller.SalesController;
import org.example.model.AppUser;
import org.example.model.Party;
import org.example.model.Sales;
import org.example.model.SalesLine;
import org.example.navigation.WorkspaceTabManager;
import org.example.service.SessionService;
import org.example.theme.ThemeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Programmatic real JavaFX screen capture probe.
 * Renders actual FXML views with live CSS themes and saves authentic screenshots
 * directly to the artifacts directory (NO AI-generated images).
 */
@EnabledIfSystemProperty(named = "dse.screen.capture.enabled", matches = "true")
public class RealApplicationScreenCaptureTest {

    private static final Path ARTIFACTS_DIR = Path.of(
            System.getProperty("dse.screen.capture.dir", "target/screenshots")
    );

    @Test
    public void captureRealApplicationScreens() throws Exception {
        Files.createDirectories(ARTIFACTS_DIR);
        Path testWorkspace = ARTIFACTS_DIR.resolve("scratch").resolve("ui_snapshot_workspace");
        Files.createDirectories(testWorkspace);

        try (AutoCloseable ignored = WorkspaceTestSupport.useTransientWorkspace(testWorkspace)) {
            ConfigManager.load();

            // Set authentic administrator test session
            AppUser testAdmin = new AppUser();
            testAdmin.setId(1);
            testAdmin.setUsername("admin");
            testAdmin.setFullName("Jatin Dhameliya (Admin)");
            testAdmin.setRole("ADMIN");
            testAdmin.setActive(true);
            SessionService.signIn(testAdmin);

            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Runnable execution = () -> {
                try {
                    renderAndCaptureAllScreens();
                } catch (Throwable t) {
                    errorRef.set(t);
                } finally {
                    latch.countDown();
                }
            };

            try {
                Platform.startup(execution);
            } catch (IllegalStateException alreadyStarted) {
                Platform.runLater(execution);
            }

            boolean completed = latch.await(45, TimeUnit.SECONDS);
            assertTrue(completed, "Screen capture timed out after 45 seconds");
            if (errorRef.get() != null) {
                throw new AssertionError("Screen capture execution failed", errorRef.get());
            }

            // Verify all 4 images were generated
            assertTrue(Files.exists(ARTIFACTS_DIR.resolve("real_dashboard_dark.png")));
            assertTrue(Files.exists(ARTIFACTS_DIR.resolve("real_dashboard_light.png")));
            assertTrue(Files.exists(ARTIFACTS_DIR.resolve("real_sale_order_dark.png")));
            assertTrue(Files.exists(ARTIFACTS_DIR.resolve("real_sale_order_light.png")));
            System.out.println("ALL 4 REAL JAVAFX SCREENSHOTS SUCCESSFULLY CAPTURED!");
        }
    }

    private void renderAndCaptureAllScreens() throws Exception {
        // --- SCREEN 1: DASHBOARD SHELL WITH MULTI-DOCUMENT WORKSPACE TABS ---
        FXMLLoader dashLoader = new FXMLLoader(getClass().getResource("/fxml/pages/Dashboard.fxml"));
        Parent dashRoot = dashLoader.load();

        Scene scene = new Scene(dashRoot, 1440, 900);
        if (ThemeManager.getCurrentTheme() != ThemeManager.Theme.DARK) {
            ThemeManager.toggle(scene);
        } else {
            ThemeManager.applyTheme(scene);
        }

        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("DSE ERP - Enterprise Resource Planning");
        stage.show();

        // 1. Prepare Dashboard Home View
        FXMLLoader homeLoader = new FXMLLoader(getClass().getResource("/fxml/pages/DashboardHome.fxml"));
        Parent homeRoot = homeLoader.load();
        DashboardHomeController homeCtrl = homeLoader.getController();

        // 2. Prepare Rapid Order Entry (Sale.fxml) View
        FXMLLoader saleLoader = new FXMLLoader(getClass().getResource("/fxml/pages/Sale.fxml"));
        Parent saleRoot = saleLoader.load();
        SalesController saleCtrl = saleLoader.getController();

        // 3. Mount Multi-Document Workspace Tab Bar
        StackPane contentPane = (StackPane) scene.lookup("#contentPane");
        HBox tabContainer = (HBox) scene.lookup("#workspaceTabContainer");

        WorkspaceTabManager tabMgr = WorkspaceTabManager.getInstance();
        if (tabContainer != null && contentPane != null) {
            WorkspaceTabManager.bind(tabContainer, contentPane);

            // Add Tab 1: Dashboard Home
            tabMgr.onPageLoaded("/fxml/pages/DashboardHome.fxml", homeRoot, homeCtrl);

            // Add Tab 2: Real Sale Order Screen
            tabMgr.onPageLoaded("/fxml/pages/Sale.fxml", saleRoot, saleCtrl);

            // Add Tab 3: Customer 360 Workspace
            StackPane dummyC360Pane = new StackPane(new Label("Customer 360 Workspace"));
            tabMgr.onPageLoaded("/fxml/pages/Customer360.fxml", dummyC360Pane, null);

            // Add Tab 4: GSTR-3B Reconciliation Center
            StackPane dummyGstPane = new StackPane(new Label("GSTR-3B Reconciliation"));
            tabMgr.onPageLoaded("/fxml/pages/GstComplianceCenter.fxml", dummyGstPane, null);

            // Activate Tab 1: Dashboard
            tabMgr.activateTab(tabMgr.getOpenTabs().get(0));
        }

        // Allow background tasks to settle then populate metrics
        Thread.sleep(600);
        populateDashboardData(homeCtrl, homeRoot);

        // Capture Dashboard in Dark Theme
        saveSceneSnapshot(scene, ARTIFACTS_DIR.resolve("real_dashboard_dark.png"));

        // Toggle to Light Theme and Capture Dashboard
        ThemeManager.toggle(scene);
        saveSceneSnapshot(scene, ARTIFACTS_DIR.resolve("real_dashboard_light.png"));

        // --- SWITCH TO TAB 2: RAPID ORDER ENTRY (Sale.fxml) ---
        tabMgr.activateTab(tabMgr.getOpenTabs().get(1));

        // Update top breadcrumb to reflect Sales Order
        Label lblPageTitle = (Label) scene.lookup("#lblPageTitle");
        if (lblPageTitle != null) lblPageTitle.setText("Sales Order #1042");

        // Populate Sale Order data using official controller method
        populateSaleOrderData(saleCtrl);

        // Switch back to Dark Theme for Sale Order capture
        if (ThemeManager.getCurrentTheme() != ThemeManager.Theme.DARK) {
            ThemeManager.toggle(scene);
        }
        saveSceneSnapshot(scene, ARTIFACTS_DIR.resolve("real_sale_order_dark.png"));

        // Toggle to Light Theme and Capture Sale Order
        ThemeManager.toggle(scene);
        saveSceneSnapshot(scene, ARTIFACTS_DIR.resolve("real_sale_order_light.png"));

        stage.close();
    }

    private static void populateDashboardData(DashboardHomeController homeCtrl, Parent homeRoot) {
        StackPane loadingOverlay = (StackPane) homeRoot.lookup("#loadingOverlay");
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false);
            loadingOverlay.setManaged(false);
        }

        setField(homeCtrl, "lblSalesValue", "₹ 1,42,85,600");
        setField(homeCtrl, "lblSalesNote", "Active Orders (18)");
        setField(homeCtrl, "lblPurchaseValue", "₹ 98,42,100");
        setField(homeCtrl, "lblPurchaseNote", "Vendor Bills (12)");
        setField(homeCtrl, "lblStockValue", "₹ 34,50,000"); // Receivables
        setField(homeCtrl, "lblReceivableNote", "Pending Invoices (8)");
        setField(homeCtrl, "lblLowStock", "₹ 18,20,000"); // Payables
        setField(homeCtrl, "lblStockNote", "Due within 15 days");
        setField(homeCtrl, "lblCash", "₹ 24,15,300");
        setField(homeCtrl, "lblLowStockValue", "14 SKUs");
        setField(homeCtrl, "lblLowStockNote", "Below reorder level");

        setField(homeCtrl, "lblCustomers", "148");
        setField(homeCtrl, "lblProducts", "1,240");
        setField(homeCtrl, "lblOrders", "32");
        setField(homeCtrl, "lblPurchases", "19");
        setField(homeCtrl, "lblReminderCount", "3");
        setField(homeCtrl, "lblReminderSummary", "3 follow-ups scheduled for today");

        // Populate insight lists
        @SuppressWarnings("unchecked")
        ListView<String> topCustomers = (ListView<String>) getField(homeCtrl, "topCustomerList");
        if (topCustomers != null) {
            topCustomers.setItems(FXCollections.observableArrayList(
                    "Apex Steel & Alloys • ₹ 38,40,000",
                    "Shree Ram Infratech • ₹ 24,15,000",
                    "Gujarat Metal Works • ₹ 18,90,000",
                    "Bharat Engineering • ₹ 14,20,000",
                    "Surat Heavy Forgings • ₹ 11,50,000"
            ));
        }

        @SuppressWarnings("unchecked")
        ListView<String> agingList = (ListView<String>) getField(homeCtrl, "agingList");
        if (agingList != null) {
            agingList.setItems(FXCollections.observableArrayList(
                    "0 - 30 Days: ₹ 22,10,000 (Current)",
                    "31 - 60 Days: ₹ 8,40,000 (Attention)",
                    "61 - 90 Days: ₹ 2,80,000 (Follow up)",
                    "90+ Days: ₹ 1,20,000 (Escalated)"
            ));
        }

        @SuppressWarnings("unchecked")
        ListView<String> activityList = (ListView<String>) getField(homeCtrl, "activityList");
        if (activityList != null) {
            activityList.setItems(FXCollections.observableArrayList(
                    "Sales Order #1042 approved by Admin • ₹ 70,44,275",
                    "E-Way Bill 451098234120 generated for Vehicle GJ-01-ET-8421",
                    "Payment ₹ 12,50,000 received from Apex Steel & Alloys",
                    "GST Return GSTR-3B filed successfully for Q2",
                    "Purchase Invoice PINV-882 booked from Tata Steel Ltd"
            ));
        }
    }

    private static void populateSaleOrderData(SalesController saleCtrl) {
        Party customer = new Party();
        customer.setId(101);
        customer.setName("Apex Steel & Alloys Pvt Ltd");
        customer.setGstin("24AAACA1234A1Z5");
        customer.setAddress("Plot 42, GIDC Industrial Estate, Odhav, Ahmedabad - 382415");
        customer.setPhone("+91 98250 12345");

        Sales sale = new Sales();
        sale.setId(1042);
        sale.setInvoiceNo("INV-2026-1042");
        sale.setInvoiceDate(LocalDate.now());
        sale.setCustomer(customer);
        sale.setBillingAddress("Plot 42, GIDC Industrial Estate, Odhav, Ahmedabad - 382415");
        sale.setBillingGstin("24AAACA1234A1Z5");
        sale.setDeliveryAddress("Plot 42, GIDC Industrial Estate, Odhav, Ahmedabad - 382415");
        sale.setDeliveryGstin("24AAACA1234A1Z5");
        sale.setSameAsBilling(true);
        sale.setPaymentTerms("15 Days");
        sale.setGstType("GST");
        sale.setSalesperson("Admin");
        sale.setVehicleNumber("GJ-01-ET-8421");
        sale.setContactPerson("Rajesh Patel");
        sale.setContactPersonMobile("+91 98250 12345");
        sale.setRemarks("Delivery within 2 business days. Mill Test Certificate attached.");

        SalesLine l1 = new SalesLine();
        l1.setItemCode("STL-TMT-12");
        l1.setItemDescription("TMT Rebar 12mm Fe550D (Primary)");
        l1.setItemCategory("Rebars");
        l1.setItemHsn("72142090");
        l1.setItemUnit("MT");
        l1.setQuantity(50.0);
        l1.setRate(52400.0);
        l1.setGstPercent(18.0);
        l1.setDiscountPercent(2.0);
        l1.setDiscountAmount(52400.0);
        l1.setNetAmount(2567600.0);
        l1.setGstAmount(462168.0);
        l1.setTotalAmount(3029768.0);

        SalesLine l2 = new SalesLine();
        l2.setItemCode("STL-ANG-50");
        l2.setItemDescription("MS Angle 50x50x6mm IS 2062 Grade A");
        l2.setItemCategory("Structural");
        l2.setItemHsn("72162100");
        l2.setItemUnit("MT");
        l2.setQuantity(25.0);
        l2.setRate(48200.0);
        l2.setGstPercent(18.0);
        l2.setDiscountPercent(1.5);
        l2.setDiscountAmount(18075.0);
        l2.setNetAmount(1186925.0);
        l2.setGstAmount(213646.5);
        l2.setTotalAmount(1400571.5);

        SalesLine l3 = new SalesLine();
        l3.setItemCode("STL-HRS-315");
        l3.setItemDescription("HR Sheet 3.15mm IS2062 Coils/Plate");
        l3.setItemCategory("Flat Products");
        l3.setItemHsn("72083940");
        l3.setItemUnit("MT");
        l3.setQuantity(40.0);
        l3.setRate(56800.0);
        l3.setGstPercent(18.0);
        l3.setDiscountPercent(2.5);
        l3.setDiscountAmount(56800.0);
        l3.setNetAmount(2215200.0);
        l3.setGstAmount(398736.0);
        l3.setTotalAmount(2613936.0);

        sale.setLines(new ArrayList<>(List.of(l1, l2, l3)));
        sale.setSubtotal(5969725.0);
        sale.setGstAmount(1074550.5);
        sale.setDiscountAmount(127275.0);
        sale.setTotalAmount(7044275.5);

        // Load into controller
        saleCtrl.loadSale(sale);

        // Populate rapid line toolbar inputs
        setField(saleCtrl, "txtItemSearch", "TMT Rebar 12mm Fe550D");
        setField(saleCtrl, "txtQuantity", "50.00");
        setField(saleCtrl, "txtRate", "52400.00");
        setField(saleCtrl, "txtGST", "18");
        setField(saleCtrl, "txtLineDiscount", "2.0");
    }

    private static void setField(Object target, String fieldName, String value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object node = field.get(target);
            if (node instanceof TextInputControl tic) {
                tic.setText(value);
            } else if (node instanceof Labeled labeled) {
                labeled.setText(value);
            }
        } catch (Exception e) {
            // Ignore if field doesn't exist
        }
    }

    private static Object getField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveSceneSnapshot(Scene scene, Path outputPath) throws Exception {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        // Allow JavaFX layout pass to settle
        Thread.sleep(350);

        WritableImage image = scene.snapshot(null);
        BufferedImage buffered = new BufferedImage(
                (int) image.getWidth(),
                (int) image.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }

        ImageIO.write(buffered, "png", outputPath.toFile());
        System.out.println("Saved real screenshot: " + outputPath.getFileName() + " (" + Files.size(outputPath) + " bytes)");
    }
}
