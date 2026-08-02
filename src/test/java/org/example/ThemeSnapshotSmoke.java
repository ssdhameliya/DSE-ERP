package org.example;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.example.config.ConfigManager;
import org.example.controller.SalesScreenContext;
import org.example.dao.SalesDAO;
import org.example.database.DatabaseManager;
import org.example.theme.ThemeManager;
import org.example.util.ProfessionalUiEnhancer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Produces real light/dark renders for visual regression inspection. */
public final class ThemeSnapshotSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load(); DatabaseManager.initialize();
        var availableSales = new SalesDAO().getAll();
        if (!availableSales.isEmpty()) SalesScreenContext.select(availableSales.getFirst().getInvoiceNo());
        Path output = Path.of(args.length == 0 ? "target/theme-snapshots" : args[0]);
        Files.createDirectories(output);
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                // Every database/table workspace changed by the shared UX pass is
                // rendered so sign-off is based on the complete application set.
                List<String> screens = List.of(
                    "CommunicationCenter.fxml", "Customer.fxml", "DashboardHome.fxml",
                    "Import.fxml", "Inventory.fxml", "ItemMaster.fxml", "Masterdata.fxml",
                    "Operations.fxml", "PaymentHistory.fxml", "Purchase.fxml",
                    "PurchaseList.fxml", "PurchaseReturnDetails.fxml", "PurchaseReturns.fxml",
                    "Quotations.fxml", "RecordPayment.fxml", "ReminderCenter.fxml", "Reports.fxml", "Sale.fxml",
                    "SalesInvoiceDetails.fxml", "SalesList.fxml", "SalesReturns.fxml",
                    "Settings.fxml", "Suppliers.fxml", "UserAccess.fxml", "BackupRestore.fxml"
                );
                ThemeManager.Theme original = ThemeManager.getCurrentTheme();
                for (int pass = 0; pass < 2; pass++) {
                    String theme = ThemeManager.getCurrentTheme().name().toLowerCase();
                    for (String screen : screens) {
                        Parent root = FXMLLoader.load(ThemeSnapshotSmoke.class.getResource("/fxml/pages/" + screen));
                        ProfessionalUiEnhancer.enhance(root);
                        Scene scene = new Scene(root, 1600, 900);
                        ThemeManager.applyTheme(scene);
                        Stage stage = new Stage(); stage.setScene(scene); stage.show();
                        root.applyCss(); root.layout();
                        WritableImage image = scene.snapshot(null);
                        BufferedImage rendered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < rendered.getHeight(); y++) for (int x = 0; x < rendered.getWidth(); x++) rendered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                        ImageIO.write(rendered, "png", output.resolve(theme + "-" + screen.replace(".fxml", ".png")).toFile());
                        stage.close();
                    }
                    ThemeManager.toggle(new Scene(new javafx.scene.layout.Pane()));
                }
                if (ThemeManager.getCurrentTheme() != original) ThemeManager.toggle(new Scene(new javafx.scene.layout.Pane()));
            } catch (Exception exception) { throw new RuntimeException(exception); }
            finally { done.countDown(); Platform.exit(); }
        });
        done.await();
        System.out.println("THEME_SNAPSHOTS_OK " + output.toAbsolutePath());
    }
}
