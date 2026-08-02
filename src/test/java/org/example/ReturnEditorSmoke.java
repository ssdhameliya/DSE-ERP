package org.example;

import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.example.service.ReturnEditorService;
import org.example.service.ReturnWorkflowService;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

/**
 * Verifies the exact shared return persistence path without opening interactive
 * windows. It uses an isolated APPDATA database seeded with its own fixtures.
 */
public final class ReturnEditorSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        DatabaseManager.initialize();
        Fixture fixture = fixture();

        String salesReturn = saveAllAvailable(ReturnEditorService.Type.SALES,
            "SMOKE-SALE-RETURN", fixture.customerId(), fixture.item());
        require(close(stock(fixture.item().code()), fixture.stock() + 1),
            "Sales return did not add returned stock");

        String purchaseReturn = saveAllAvailable(ReturnEditorService.Type.PURCHASE,
            "SMOKE-PURCHASE-RETURN", fixture.supplierId(), fixture.item());
        require(close(stock(fixture.item().code()), fixture.stock()),
            "Purchase return did not remove returned stock");

        ReturnWorkflowService.delete(salesReturn, true);
        ReturnWorkflowService.delete(purchaseReturn, false);
        require(close(stock(fixture.item().code()), fixture.stock()),
            "Deleting returns did not restore original stock");
        System.out.println("RETURN_EDITOR_OK sales=" + salesReturn +
            " purchase=" + purchaseReturn + " stock=" + stock(fixture.item().code()));
    }

    /** Calls the private production validation/save methods so the test covers
     * the same SQL transaction used by the dialog, including all stock rules. */
    @SuppressWarnings("unchecked")
    private static String saveAllAvailable(ReturnEditorService.Type type,
                                           String invoiceNo, int partyId,
                                           ReturnEditorService.InvoiceItem item) throws Exception {
        Class<ReturnEditorService> service = ReturnEditorService.class;
        Method load = service.getDeclaredMethod("loadReturnableLines",
            ReturnEditorService.Type.class, String.class, List.class);
        load.setAccessible(true);
        List<Object> rows = (List<Object>) load.invoke(null, type, invoiceNo, List.of(item));
        require(rows.size() == 1, "Expected one returnable line");

        Object row = rows.getFirst();
        Method available = row.getClass().getDeclaredMethod("available");
        Method setSelected = row.getClass().getDeclaredMethod("setSelected", boolean.class);
        Method setQuantity = row.getClass().getDeclaredMethod("setReturnQuantity", double.class);
        available.setAccessible(true);
        setSelected.setAccessible(true);
        setQuantity.setAccessible(true);
        double quantity = (double) available.invoke(row);
        setQuantity.invoke(row, quantity);
        setSelected.invoke(row, true);

        Method validate = service.getDeclaredMethod("validate", List.class, LocalDate.class);
        validate.setAccessible(true);
        List<Object> selected = (List<Object>) validate.invoke(null, rows, LocalDate.now());

        Method save = service.getDeclaredMethod("save", ReturnEditorService.Type.class,
            String.class, int.class, LocalDate.class, List.class);
        save.setAccessible(true);
        return (String) save.invoke(null, type, invoiceNo, partyId, LocalDate.now(), selected);
    }

    private static Fixture fixture() throws Exception {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT OR IGNORE INTO party_master " +
                "(party_type,party_code,name,phone,email,address,is_active) VALUES " +
                "('CUSTOMER','SMOKE-CUSTOMER','Return Smoke Customer','9000000001'," +
                "'customer@example.test','Test customer address',1)");
            statement.executeUpdate("INSERT OR IGNORE INTO party_master " +
                "(party_type,party_code,name,phone,email,address,is_active) VALUES " +
                "('SUPPLIER','SMOKE-SUPPLIER','Return Smoke Supplier','9000000002'," +
                "'supplier@example.test','Test supplier address',1)");
            statement.executeUpdate("INSERT OR IGNORE INTO item_master " +
                "(item_code,description,unit,gst,purchase_price,selling_price,opening_stock,minimum_stock) " +
                "VALUES ('SMOKE-RETURN-ITEM','Return Smoke Item','Nos',18,100,120,20,1)");

            int customerId = id(statement, "SMOKE-CUSTOMER");
            int supplierId = id(statement, "SMOKE-SUPPLIER");
            try (ResultSet result = statement.executeQuery(
                "SELECT item_code,description,opening_stock,purchase_price,gst " +
                    "FROM item_master WHERE item_code='SMOKE-RETURN-ITEM'")) {
                require(result.next(), "Smoke item was not created");
                double stock = result.getDouble(3);
                ReturnEditorService.InvoiceItem item = new ReturnEditorService.InvoiceItem(
                    result.getString(1), result.getString(2), 1,
                    result.getDouble(4), result.getDouble(5));
                return new Fixture(customerId, supplierId, stock, item);
            }
        }
    }

    private static int id(Statement statement, String code) throws Exception {
        try (ResultSet result = statement.executeQuery(
            "SELECT id FROM party_master WHERE party_code='" + code + "'")) {
            require(result.next(), "Smoke party was not created: " + code);
            return result.getInt(1);
        }
    }

    private static double stock(String itemCode) throws Exception {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT opening_stock FROM item_master WHERE item_code='" +
                     itemCode.replace("'", "''") + "'")) {
            require(result.next(), "Smoke item disappeared");
            return result.getDouble(1);
        }
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) < 0.0001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Fixture(int customerId, int supplierId, double stock,
                           ReturnEditorService.InvoiceItem item) {
    }
}
