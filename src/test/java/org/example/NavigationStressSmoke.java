package org.example;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.example.navigation.NavigationManager;
import org.example.theme.ThemeManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression test for repeated Sales and Purchase parent/submenu navigation. */
public final class NavigationStressSmoke {
    public static void main(String[] args) throws Exception {
        System.setProperty("prism.order", "sw");
        ConfigManager.load();
        DatabaseManager.initialize();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            failure.compareAndSet(null, error);
            error.printStackTrace();
        });
        Platform.startup(() -> {
            try {
                StackPane content = new StackPane();
                Scene scene = new Scene(content, 1600, 900);
                ThemeManager.applyTheme(scene);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                NavigationManager navigation = new NavigationManager(content);
                List<String> pages = List.of(
                    "/fxml/pages/DashboardHome.fxml", "/fxml/pages/SalesList.fxml",
                    "/fxml/pages/Sale.fxml", "/fxml/pages/SalesReturns.fxml",
                    "/fxml/pages/Quotations.fxml", "/fxml/pages/PurchaseList.fxml",
                    "/fxml/pages/Purchase.fxml", "/fxml/pages/PurchaseReturns.fxml",
                    "/fxml/pages/ItemMaster.fxml", "/fxml/pages/Masterdata.fxml",
                    "/fxml/pages/Inventory.fxml", "/fxml/pages/Customer.fxml",
                    "/fxml/pages/Suppliers.fxml", "/fxml/pages/Reports.fxml",
                    "/fxml/pages/CommunicationCenter.fxml", "/fxml/pages/EmailSettings.fxml",
                    "/fxml/pages/ReminderCenter.fxml", "/fxml/pages/UserAccess.fxml",
                    "/fxml/pages/BackupRestore.fxml", "/fxml/pages/Settings.fxml",
                    "/fxml/pages/Import.fxml"
                );

                /*
                 * Navigate one page per JavaFX pulse.  This deliberately
                 * allows controller Platform.runLater callbacks, CSS, layout
                 * and the real window renderer to execute between pages.
                 */
                int passes = 5;
                AtomicInteger index = new AtomicInteger();
                Runnable[] next = new Runnable[1];
                next[0] = () -> {
                    try {
                        int current = index.getAndIncrement();
                        if (current >= pages.size() * passes) {
                            stage.close();
                            done.countDown();
                            Platform.exit();
                            return;
                        }
                        String page = pages.get(current % pages.size());
                        int pass = current / pages.size() + 1;
                        if (!navigation.loadPage(page)) {
                            throw new IllegalStateException("Navigation failed: " + page);
                        }
                        content.applyCss();
                        content.layout();
                        System.out.println("NAV_OK pass=" + pass + " page=" + page);
                        PauseTransition pulse = new PauseTransition(Duration.millis(100));
                        pulse.setOnFinished(event -> next[0].run());
                        pulse.play();
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                        error.printStackTrace();
                        stage.close();
                        done.countDown();
                        Platform.exit();
                    }
                };
                Platform.runLater(next[0]);
            } catch (Throwable error) {
                failure.set(error);
                error.printStackTrace();
                done.countDown();
                Platform.exit();
            }
        });
        if (!done.await(120, TimeUnit.SECONDS)) throw new IllegalStateException("Navigation stress test timed out");
        if (failure.get() != null) throw new RuntimeException("Navigation stress test failed", failure.get());
    }
}
