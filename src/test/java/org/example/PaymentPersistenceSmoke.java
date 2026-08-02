package org.example;

import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

/** Verifies that a payment row and its invoice balance update commit atomically. */
public final class PaymentPersistenceSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load();
        DatabaseManager.initialize();

        int saleId;
        double paidBefore;
        double total;
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT id,COALESCE(paid_amount,0),total_amount FROM sales_header " +
                     "WHERE total_amount>COALESCE(paid_amount,0) ORDER BY id LIMIT 1")) {
            if (!result.next()) throw new IllegalStateException("Smoke test requires an unpaid sale");
            saleId = result.getInt(1);
            paidBefore = result.getDouble(2);
            total = result.getDouble(3);
        }

        double amount = Math.min(1, total - paidBefore);
        String reference = "SMOKE-" + System.nanoTime();
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO payment_record(document_type,document_id,payment_date,amount," +
                         "payment_mode,reference_no,notes,created_by) VALUES('SALE',?,?,?,?,?,?,?)");
                 PreparedStatement update = connection.prepareStatement(
                     "UPDATE sales_header SET paid_amount=COALESCE(paid_amount,0)+?," +
                         "payment_status=CASE WHEN COALESCE(paid_amount,0)+?>=total_amount " +
                         "THEN 'PAID' ELSE 'PARTIAL' END WHERE id=?")) {
                insert.setInt(1, saleId);
                insert.setString(2, LocalDate.now().toString());
                insert.setDouble(3, amount);
                insert.setString(4, "Bank Transfer");
                insert.setString(5, reference);
                insert.setString(6, "Automated payment smoke test");
                insert.setString(7, "Automated Test");
                insert.executeUpdate();

                update.setDouble(1, amount);
                update.setDouble(2, amount);
                update.setInt(3, saleId);
                update.executeUpdate();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT paid_amount,payment_status,(SELECT COUNT(*) FROM payment_record " +
                     "WHERE document_type='SALE' AND document_id=? AND reference_no=?) " +
                     "FROM sales_header WHERE id=?")) {
            query.setInt(1, saleId);
            query.setString(2, reference);
            query.setInt(3, saleId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()
                    || Math.abs(result.getDouble(1) - (paidBefore + amount)) > .001
                    || result.getInt(3) != 1) {
                    throw new IllegalStateException("Payment did not persist correctly");
                }
                System.out.println("PAYMENT_SMOKE_OK paid=" + paidBefore + "->" +
                    result.getDouble(1) + " status=" + result.getString(2));
            }
        }
    }
}
