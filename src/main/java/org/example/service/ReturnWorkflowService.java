package org.example.service;

import org.example.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Accounting-safe state changes shared by sales and purchase returns. */
public final class ReturnWorkflowService {
    private ReturnWorkflowService() {
    }

    /** Applies one document-level refund without duplicating it on every item row. */
    public static void recordRefund(String returnNo, double refundAmount) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                double total = documentTotal(connection, returnNo);
                if (refundAmount <= 0 || refundAmount > total + 0.0001) {
                    throw new IllegalArgumentException("Refund amount must be greater than zero and cannot exceed the return total.");
                }
                String refundStatus = refundAmount + 0.0001 >= total ? "REFUNDED" : "PARTIAL";
                String returnStatus = refundStatus.equals("REFUNDED") ? "COMPLETED" : "PARTIAL";
                try (PreparedStatement clear = connection.prepareStatement(
                    "UPDATE return_register SET refund_amount=0,refund_status=?,status=?,updated_at=datetime('now') WHERE return_no=?")) {
                    clear.setString(1, refundStatus);
                    clear.setString(2, returnStatus);
                    clear.setString(3, returnNo);
                    clear.executeUpdate();
                }
                try (PreparedStatement first = connection.prepareStatement(
                    "UPDATE return_register SET refund_amount=? WHERE id=(SELECT MIN(id) FROM return_register WHERE return_no=?)")) {
                    first.setDouble(1, refundAmount);
                    first.setString(2, returnNo);
                    first.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sql) throw sql;
                throw new SQLException(exception.getMessage(), exception);
            }
        }
    }

    /** Deletes an unrefunded return and reverses every stock movement atomically. */
    public static void delete(String returnNo, boolean salesReturn) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertUnrefunded(connection, returnNo);
                reverseStock(connection, returnNo, salesReturn);
                try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM return_register WHERE return_no=?")) {
                    delete.setString(1, returnNo);
                    delete.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sql) throw sql;
                throw new SQLException(exception.getMessage(), exception);
            }
        }
    }

    /** Cancels an unrefunded return and reverses stock once. */
    public static void cancel(String returnNo, boolean salesReturn) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertUnrefunded(connection, returnNo);
                try (PreparedStatement check = connection.prepareStatement(
                    "SELECT COUNT(*) FROM return_register WHERE return_no=? AND status='CANCELLED'")) {
                    check.setString(1, returnNo);
                    try (ResultSet result = check.executeQuery()) {
                        if (result.next() && result.getInt(1) > 0) {
                            throw new IllegalStateException("This return is already cancelled.");
                        }
                    }
                }
                reverseStock(connection, returnNo, salesReturn);
                try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE return_register SET status='CANCELLED',updated_at=datetime('now') WHERE return_no=?")) {
                    update.setString(1, returnNo);
                    update.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sql) throw sql;
                throw new SQLException(exception.getMessage(), exception);
            }
        }
    }

    private static void assertUnrefunded(Connection connection, String returnNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COALESCE(SUM(refund_amount),0),MAX(COALESCE(refund_status,'PENDING')) FROM return_register WHERE return_no=?")) {
            statement.setString(1, returnNo);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Return " + returnNo + " was not found.");
                if (result.getDouble(1) > 0.0001 || !"PENDING".equalsIgnoreCase(result.getString(2))) {
                    throw new IllegalStateException("A refunded or partially refunded return cannot be deleted or cancelled because it would corrupt accounting history.");
                }
            }
        }
    }

    private static double documentTotal(Connection connection, String returnNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COALESCE(SUM(amount),0) FROM return_register WHERE return_no=?")) {
            statement.setString(1, returnNo);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getDouble(1) <= 0) throw new IllegalStateException("Return " + returnNo + " was not found.");
                return result.getDouble(1);
            }
        }
    }

    private static void reverseStock(Connection connection, String returnNo, boolean salesReturn) throws SQLException {
        String stockSql = salesReturn
            ? "UPDATE item_master SET opening_stock=MAX(0,COALESCE(opening_stock,0)-?) WHERE item_code=?"
            : "UPDATE item_master SET opening_stock=COALESCE(opening_stock,0)+? WHERE item_code=?";
        try (PreparedStatement lines = connection.prepareStatement(
            "SELECT item_code,quantity FROM return_register WHERE return_no=?");
             PreparedStatement stock = connection.prepareStatement(stockSql)) {
            lines.setString(1, returnNo);
            try (ResultSet result = lines.executeQuery()) {
                while (result.next()) {
                    stock.setDouble(1, result.getDouble("quantity"));
                    stock.setString(2, result.getString("item_code"));
                    stock.addBatch();
                }
            }
            stock.executeBatch();
        }
    }
}
