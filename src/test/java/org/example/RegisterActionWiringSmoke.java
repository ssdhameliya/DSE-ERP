package org.example;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies that every register action is visible, named and has a handler. */
public final class RegisterActionWiringSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        DatabaseManager.initialize();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                verify("SalesList.fxml", "tableSales", 10);
                verify("PurchaseList.fxml", "tablePurchase", 13);
            } catch (Throwable error) {
                failure.set(error);
                error.printStackTrace();
            } finally {
                done.countDown();
                Platform.exit();
            }
        });
        if (!done.await(45, TimeUnit.SECONDS)) throw new IllegalStateException("Action wiring smoke timed out");
        if (failure.get() != null) throw new RuntimeException("Action wiring smoke failed", failure.get());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verify(String fxml, String tableId, int minimumActions) throws Exception {
        FXMLLoader loader = new FXMLLoader(RegisterActionWiringSmoke.class.getResource("/fxml/pages/" + fxml));
        loader.load();
        TableView table = loader.getNamespace().values().stream()
                .filter(TableView.class::isInstance)
                .map(TableView.class::cast)
                .findFirst()
                .orElse(null);
        if (table == null) throw new IllegalStateException(tableId + " was not found");
        TableColumn actionColumn = null;
        for (Object value : table.getColumns()) {
            TableColumn column = (TableColumn) value;
            if ("Actions".equals(column.getText())) {
                actionColumn = column;
                break;
            }
        }
        if (actionColumn == null) throw new IllegalStateException("Actions column was not found in " + fxml);
        Object cell = actionColumn.getCellFactory().call(actionColumn);
        MenuButton menu = null;
        for (Field field : cell.getClass().getDeclaredFields()) {
            if (!MenuButton.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            menu = (MenuButton) field.get(cell);
            break;
        }
        if (menu == null) throw new IllegalStateException("Action menu was not created for " + fxml);
        if (menu.getItems().size() < minimumActions) throw new IllegalStateException(fxml + " exposes only " + menu.getItems().size() + " actions");
        for (MenuItem item : menu.getItems()) {
            if (item.getText() == null || item.getText().isBlank()) throw new IllegalStateException(fxml + " contains an unnamed action");
            if (item.getGraphic() == null) throw new IllegalStateException(item.getText() + " has no icon in " + fxml);
            if (item.getOnAction() == null) throw new IllegalStateException(item.getText() + " has no handler in " + fxml);
        }
        System.out.println("ACTIONS_OK " + fxml + " count=" + menu.getItems().size());
    }
}
