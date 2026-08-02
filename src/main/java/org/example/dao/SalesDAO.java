package org.example.dao;

import org.example.database.DatabaseManager;
import org.example.model.Party;
import org.example.model.Sales;
import org.example.model.SalesLine;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesDAO {

    //====================================================
    // SAVE SALES
    //====================================================

    public synchronized void save(Sales sales) {

        String headerSql =
            """
            
                INSERT INTO sales_header
            (
                invoice_no,
                invoice_date,
                customer_id,
                subtotal,
                discount_amount,
                gst_amount,
                total_amount,
                remarks,
                created_at,
                email_sent,
                due_date,
                salesperson,
                notes,
                delivery_address,
                payment_terms,
                transporter,
                reference_no
            )
            VALUES
            (
                ?,?,?,?,?,?,?,?,datetime('now'),0,?,?,?,?,?,?,?
            )
            """;


        String lineSql =
                """
            INSERT INTO sales_line
            (
                sales_id,
                item_code,
                quantity,
                rate,
                discount_percent,
                discount_amount,
                gst_percent,
                line_total
            )
            VALUES
            (?,?,?,?,?,?,?,?)
            """;


        String stockSql =
                """
            UPDATE item_master
            SET opening_stock =
            COALESCE(opening_stock,0)-?
            WHERE item_code=? AND COALESCE(opening_stock,0)>=?
            """;


        try(Connection con =
                DatabaseManager.getConnection()) {

            con.setAutoCommit(false);

            // Imported invoices and deleted rows make COUNT(*) based numbering unsafe.
            // Allocate the final number on the same connection immediately before the
            // insert so a new sale never attempts to reuse an existing invoice number.
            if (invoiceNumberExists(con, sales.getInvoiceNo())) {
                sales.setInvoiceNo(nextInvoiceNo(con));
            }

            try(

                PreparedStatement headerPs =
                    con.prepareStatement(
                        headerSql,
                        Statement.RETURN_GENERATED_KEYS
                    );

                PreparedStatement linePs =
                    con.prepareStatement(lineSql);

                PreparedStatement stockPs =
                    con.prepareStatement(stockSql)

            ){

                headerPs.setString(
                    1,
                    sales.getInvoiceNo()
                );

                headerPs.setString(
                    2,
                    sales.getInvoiceDate().toString()
                );

                headerPs.setInt(
                    3,
                    sales.getCustomer().getId()
                );

                headerPs.setDouble(
                    4,
                    sales.getSubtotal()
                );

                headerPs.setDouble(
                    5,
                    sales.getDiscountAmount()
                );

                headerPs.setDouble(
                    6,
                    sales.getGstAmount()
                );

                headerPs.setDouble(
                    7,
                    sales.getTotalAmount()
                );

                headerPs.setString(
                    8,
                    sales.getRemarks()
                );

                headerPs.setString(9, sales.getDueDate() == null ? null : sales.getDueDate().toString());
                headerPs.setString(10, sales.getSalesperson());
                headerPs.setString(11, sales.getNotes());
                headerPs.setString(12, sales.getDeliveryAddress());
                headerPs.setString(13, sales.getPaymentTerms());
                headerPs.setString(14, sales.getTransporter());
                headerPs.setString(15, sales.getReferenceNo());

                headerPs.executeUpdate();

                ResultSet keys =
                    headerPs.getGeneratedKeys();

                keys.next();

                int salesId =
                    keys.getInt(1);

                for(SalesLine line :
                    sales.getLines()) {

                    linePs.setInt(
                        1,
                        salesId
                    );

                    linePs.setString(
                        2,
                        line.getItemCode()
                    );

                    linePs.setDouble(
                        3,
                        line.getQuantity()
                    );

                    linePs.setDouble(
                        4,
                        line.getRate()
                    );

                    linePs.setDouble(
                        5,
                        line.getDiscountPercent()
                    );

                    linePs.setDouble(
                        6,
                        line.getDiscountAmount()
                    );

                    linePs.setDouble(
                        7,
                        line.getGstPercent()
                    );

                    linePs.setDouble(
                        8,
                        line.getTotalAmount()
                    );

                    linePs.addBatch();

                    // Reduce stock after sale
                    stockPs.setDouble(
                        1,
                        line.getQuantity()
                    );

                    stockPs.setString(
                        2,
                        line.getItemCode()
                    );

                    stockPs.setDouble(
                        3,
                        line.getQuantity()
                    );

                    stockPs.addBatch();
                }

                linePs.executeBatch();
                int[] stockResults = stockPs.executeBatch();
                for (int result : stockResults) {
                    if (result == 0) throw new IllegalStateException("Insufficient stock for one or more sale items");
                }

                con.commit();

            }
            catch (Exception e){

                con.rollback();
                throw e;

            }

        }
        catch (Exception e){
            String detail = e.getMessage();
            if ((detail == null || detail.isBlank()) && e.getCause() != null) detail = e.getCause().getMessage();
            throw new RuntimeException(
                detail == null || detail.isBlank() ? "Unable to save sales" : "Unable to save sale: " + detail,
                e
            );
}

    }

    //====================================================
// SALES REGISTER LOAD
//====================================================

    public List<Sales> getAll() {

        List<Sales> sales = new ArrayList<>();

        String sql =
            """
            SELECT
                sh.*,
                pm.party_code,
                pm.name,
                pm.email,
                pm.phone,
                pm.gstin,
                COALESCE(SUM(sl.quantity),0) AS total_qty
            FROM sales_header sh
            LEFT JOIN party_master pm
                ON sh.customer_id = pm.id
            LEFT JOIN sales_line sl
                ON sh.id = sl.sales_id
            GROUP BY sh.id
            ORDER BY
                sh.invoice_date DESC,
                sh.id DESC
            """;

        try (
            Connection con = DatabaseManager.getConnection();

            PreparedStatement ps =
                con.prepareStatement(sql);

            ResultSet rs =
                ps.executeQuery()
        ) {

            while (rs.next()) {

                Sales sale = new Sales();

                sale.setId(
                    rs.getInt("id")
                );

                sale.setInvoiceNo(
                    rs.getString("invoice_no")
                );

                sale.setInvoiceDate(
                    LocalDate.parse(
                        rs.getString("invoice_date")
                    )
                );

                Party customer =
                    new Party();

                customer.setId(
                    rs.getInt("customer_id")
                );

                customer.setPartyCode(
                    rs.getString("party_code")
                );

                customer.setName(
                    rs.getString("name")
                );

                customer.setEmail(
                    rs.getString("email")
                );
                customer.setPhone(rs.getString("phone"));
                customer.setGstin(rs.getString("gstin"));

                sale.setCustomer(customer);

                sale.setQuantity(
                    rs.getDouble("total_qty")
                );

                sale.setSubtotal(
                    rs.getDouble("subtotal")
                );
                sale.setDiscountAmount(rs.getDouble("discount_amount"));

                sale.setGstAmount(
                    rs.getDouble("gst_amount")
                );

                sale.setTotalAmount(
                    rs.getDouble("total_amount")
                );

                sale.setRemarks(
                    rs.getString("remarks")
                );

                sale.setCreatedAt(
                    rs.getString("created_at")
                );

                sale.setEmailSent(
                    rs.getInt("email_sent") == 1
                );

                String dueDate = rs.getString("due_date");
                sale.setDueDate(dueDate == null || dueDate.isBlank() ? null : LocalDate.parse(dueDate));
                sale.setPaidAmount(rs.getDouble("paid_amount"));
                sale.setPaymentStatus(rs.getString("payment_status"));
                sale.setWhatsappSent(rs.getInt("whatsapp_sent") == 1);
                sale.setInvoiceType(rs.getString("invoice_type"));
                sale.setSalesperson(rs.getString("salesperson"));
                sale.setSource(rs.getString("source"));
                sale.setNotes(rs.getString("notes"));
                sale.setDeliveryAddress(rs.getString("delivery_address"));
                sale.setPaymentTerms(rs.getString("payment_terms"));
                sale.setTransporter(rs.getString("transporter"));
                sale.setReferenceNo(rs.getString("reference_no"));

                sales.add(sale);

            }

        }
        catch (SQLException ex) {

            throw new RuntimeException(
                "Unable to load sales register.",
                ex
            );

        }

        return sales;

    }



//====================================================
// NEXT SALES INVOICE
//====================================================

    public String nextInvoiceNo() {
        try (Connection con = DatabaseManager.getConnection()) {
            return nextInvoiceNo(con);
        }
        catch (Exception e) {

            throw new RuntimeException(e);

        }

    }

    /**
     * Finds the next free SAL sequence while ignoring imported document formats.
     * The explicit existence loop also handles gaps and historical duplicate-like
     * values safely without changing any existing invoice.
     */
    private String nextInvoiceNo(Connection con) throws SQLException {
        int highest = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT invoice_no FROM sales_header WHERE UPPER(invoice_no) LIKE 'SAL-%'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String value = rs.getString(1);
                if (value == null) continue;
                String suffix = value.trim().substring(4);
                if (!suffix.matches("\\d+")) continue;
                try {
                    highest = Math.max(highest, Integer.parseInt(suffix));
                } catch (NumberFormatException ignored) {
                    // Very large or malformed imported suffixes do not participate.
                }
            }
        }

        String candidate;
        do {
            candidate = "SAL-" + String.format("%05d", ++highest);
        } while (invoiceNumberExists(con, candidate));
        return candidate;
    }

    private boolean invoiceNumberExists(Connection con, String invoiceNo) throws SQLException {
        if (invoiceNo == null || invoiceNo.isBlank()) return false;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM sales_header WHERE TRIM(UPPER(invoice_no))=TRIM(UPPER(?)) LIMIT 1")) {
            ps.setString(1, invoiceNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    //====================================================
// LOAD SINGLE SALES
//====================================================

    public Sales getByInvoice(
        String invoiceNo
    ){

        String headerSql =
            """
            SELECT
                sh.*,
                pm.party_code,
                pm.name,
                pm.email,
                pm.phone,
                pm.gstin
            FROM sales_header sh
            LEFT JOIN party_master pm
            ON sh.customer_id = pm.id
            WHERE TRIM(UPPER(sh.invoice_no))=TRIM(UPPER(?))
            """;


        String lineSql =
            """
            SELECT
                sl.*,
                im.description
            FROM sales_line sl
            LEFT JOIN item_master im
            ON sl.item_code = im.item_code
            WHERE sl.sales_id=?
            ORDER BY sl.id
            """;


        try(
            Connection con =
                DatabaseManager.getConnection()
        ){

            Sales s = null;

            PreparedStatement ps =
                con.prepareStatement(headerSql);

            ps.setString(
                1,
                invoiceNo
            );

            ResultSet rs =
                ps.executeQuery();

            if(rs.next()){

                s = new Sales();

                s.setId(
                    rs.getInt("id")
                );

                s.setInvoiceNo(
                    rs.getString("invoice_no")
                );

                s.setInvoiceDate(
                    LocalDate.parse(
                        rs.getString("invoice_date")
                    )
                );

                Party customer =
                    new Party();

                customer.setId(
                    rs.getInt("customer_id")
                );

                customer.setPartyCode(
                    rs.getString("party_code")
                );

                customer.setName(
                    rs.getString("name")
                );

                customer.setEmail(
                    rs.getString("email")
                );
                customer.setPhone(rs.getString("phone"));
                customer.setGstin(rs.getString("gstin"));

                s.setCustomer(customer);

                s.setSubtotal(
                    rs.getDouble("subtotal")
                );
                s.setDiscountAmount(rs.getDouble("discount_amount"));

                s.setGstAmount(
                    rs.getDouble("gst_amount")
                );

                s.setTotalAmount(
                    rs.getDouble("total_amount")
                );

                s.setRemarks(
                    rs.getString("remarks")
                );

                s.setCreatedAt(
                    rs.getString("created_at")
                );

                s.setEmailSent(
                    rs.getInt("email_sent") == 1
                );

                String dueDate = rs.getString("due_date");
                s.setDueDate(dueDate == null || dueDate.isBlank() ? null : LocalDate.parse(dueDate));
                s.setPaidAmount(rs.getDouble("paid_amount"));
                s.setPaymentStatus(rs.getString("payment_status"));
                s.setWhatsappSent(rs.getInt("whatsapp_sent") == 1);
                s.setInvoiceType(rs.getString("invoice_type"));
                s.setSalesperson(rs.getString("salesperson"));
                s.setSource(rs.getString("source"));
                s.setNotes(rs.getString("notes"));
                s.setDeliveryAddress(rs.getString("delivery_address"));
                s.setPaymentTerms(rs.getString("payment_terms"));
                s.setTransporter(rs.getString("transporter"));
                s.setReferenceNo(rs.getString("reference_no"));

            }

            if(s == null)
                return null;


            List<SalesLine> lines =
                new ArrayList<>();


            PreparedStatement lp =
                con.prepareStatement(lineSql);

            lp.setInt(
                1,
                s.getId()
            );

            ResultSet lr =
                lp.executeQuery();

            while(lr.next()){

                SalesLine line =
                    new SalesLine();

                line.setItemCode(
                    lr.getString("item_code")
                );

                line.setItemDescription(
                    lr.getString("item_code")
                        + " - "
                        + lr.getString("description")
                );

                line.setQuantity(
                    lr.getDouble("quantity")
                );

                line.setRate(
                    lr.getDouble("rate")
                );

                line.setDiscountPercent(lr.getDouble("discount_percent"));
                line.setDiscountAmount(lr.getDouble("discount_amount"));

                line.setGstPercent(
                    lr.getDouble("gst_percent")
                );

                line.recalculate();
                line.setLineTotal(line.getTotalAmount());

                lines.add(line);

            }

            s.setLines(lines);

            return s;

        }
        catch(Exception e){

            throw new RuntimeException(
                "Unable to load sales",
                e
            );

        }

    }

    public void update(Sales sales) {

        String updateHeader =
            """
            UPDATE sales_header
            SET
                invoice_date = ?,
                customer_id = ?,
                subtotal = ?,
                discount_amount = ?,
                gst_amount = ?,
                total_amount = ?,
                remarks = ?,
                due_date = ?,
                salesperson = ?,
                notes = ?,
                delivery_address = ?,
                payment_terms = ?,
                transporter = ?,
                reference_no = ?
            WHERE id = ?
            """;

        String deleteLines =
            """
            DELETE FROM sales_line
            WHERE sales_id = ?
            """;

        String insertLine =
            """
            INSERT INTO sales_line
            (
                sales_id,
                item_code,
                quantity,
                rate,
                discount_percent,
                discount_amount,
                gst_percent,
                line_total
            )
            VALUES (?,?,?,?,?,?,?,?)
            """;

        String restoreOldStock =
            """
            UPDATE item_master
            SET opening_stock = COALESCE(opening_stock, 0) +
                COALESCE((SELECT SUM(quantity) FROM sales_line
                          WHERE sales_id = ? AND item_code = item_master.item_code), 0)
            WHERE item_code IN (SELECT item_code FROM sales_line WHERE sales_id = ?)
            """;

        String issueNewStock =
            """
            UPDATE item_master
            SET opening_stock = COALESCE(opening_stock, 0) - ?
            WHERE item_code = ? AND COALESCE(opening_stock, 0) >= ?
            """;

        try(Connection con =
                DatabaseManager.getConnection()) {

            con.setAutoCommit(false);

            try(

                PreparedStatement updatePs =
                    con.prepareStatement(updateHeader);

                PreparedStatement deletePs =
                    con.prepareStatement(deleteLines);

                PreparedStatement linePs =
                    con.prepareStatement(insertLine);

                PreparedStatement restorePs =
                    con.prepareStatement(restoreOldStock);

                PreparedStatement stockPs =
                    con.prepareStatement(issueNewStock)

            ){

                restorePs.setInt(1, sales.getId());
                restorePs.setInt(2, sales.getId());
                restorePs.executeUpdate();

                updatePs.setString(
                    1,
                    sales.getInvoiceDate().toString()
                );

                updatePs.setInt(
                    2,
                    sales.getCustomer().getId()
                );

                updatePs.setDouble(
                    3,
                    sales.getSubtotal()
                );

                updatePs.setDouble(4, sales.getDiscountAmount());

                updatePs.setDouble(
                    5,
                    sales.getGstAmount()
                );

                updatePs.setDouble(
                    6,
                    sales.getTotalAmount()
                );

                updatePs.setString(
                    7,
                    sales.getRemarks()
                );

                updatePs.setString(8, sales.getDueDate() == null ? null : sales.getDueDate().toString());
                updatePs.setString(9, sales.getSalesperson());
                updatePs.setString(10, sales.getNotes());
                updatePs.setString(11, sales.getDeliveryAddress());
                updatePs.setString(12, sales.getPaymentTerms());
                updatePs.setString(13, sales.getTransporter());
                updatePs.setString(14, sales.getReferenceNo());

                updatePs.setInt(
                    15,
                    sales.getId()
                );

                updatePs.executeUpdate();

                deletePs.setInt(
                    1,
                    sales.getId()
                );

                deletePs.executeUpdate();

                for(SalesLine line : sales.getLines()){

                    stockPs.setDouble(1, line.getQuantity());
                    stockPs.setString(2, line.getItemCode());
                    stockPs.setDouble(3, line.getQuantity());
                    if (stockPs.executeUpdate() != 1) {
                        throw new IllegalStateException(
                            "Insufficient stock for item " + line.getItemCode());
                    }

                    linePs.setInt(
                        1,
                        sales.getId()
                    );

                    linePs.setString(
                        2,
                        line.getItemCode()
                    );

                    linePs.setDouble(
                        3,
                        line.getQuantity()
                    );

                    linePs.setDouble(
                        4,
                        line.getRate()
                    );

                    linePs.setDouble(
                        5,
                        line.getDiscountPercent()
                    );

                    linePs.setDouble(
                        6,
                        line.getDiscountAmount()
                    );

                    linePs.setDouble(
                        7,
                        line.getGstPercent()
                    );

                    linePs.setDouble(
                        8,
                        line.getTotalAmount()
                    );

                    linePs.addBatch();

                }

                linePs.executeBatch();

                con.commit();

            }
            catch(Exception e){

                con.rollback();

                throw e;

            }

        }
        catch(Exception e){

            throw new RuntimeException(
                "Unable to update sales",
                e
            );

        }

    }

    public void delete(String invoiceNo) {

        String restoreStock =
            """
            UPDATE item_master
            SET opening_stock = COALESCE(opening_stock, 0) +
                COALESCE((SELECT SUM(sl.quantity)
                          FROM sales_line sl JOIN sales_header sh ON sh.id = sl.sales_id
                          WHERE sh.invoice_no = ? AND sl.item_code = item_master.item_code), 0)
            WHERE item_code IN (
                SELECT sl.item_code FROM sales_line sl
                JOIN sales_header sh ON sh.id = sl.sales_id WHERE sh.invoice_no = ?)
            """;

        String deleteLines =
            """
            DELETE FROM sales_line
            WHERE sales_id =
            (
                SELECT id
                FROM sales_header
                WHERE invoice_no = ?
            )
            """;

        String deleteHeader =
            """
            DELETE FROM sales_header
            WHERE invoice_no = ?
            """;

        try(Connection con =
                DatabaseManager.getConnection()) {

            con.setAutoCommit(false);

            try(

                PreparedStatement stockPs =
                    con.prepareStatement(restoreStock);

                PreparedStatement ps1 =
                    con.prepareStatement(deleteLines);

                PreparedStatement ps2 =
                    con.prepareStatement(deleteHeader)

            ){

                stockPs.setString(1, invoiceNo);
                stockPs.setString(2, invoiceNo);
                stockPs.executeUpdate();

                ps1.setString(
                    1,
                    invoiceNo
                );

                ps1.executeUpdate();

                ps2.setString(
                    1,
                    invoiceNo
                );

                ps2.executeUpdate();

                con.commit();

            }
            catch(Exception e){

                con.rollback();

                throw e;

            }

        }
        catch(Exception e){

            throw new RuntimeException(
                "Unable to delete sales.",
                e
            );

        }

    }

    public void markEmailSent(int salesId){

        String sql =
            """
            UPDATE sales_header
            SET email_sent = 1
            WHERE id = ?
            """;

        try(

            Connection con =
                DatabaseManager.getConnection();

            PreparedStatement ps =
                con.prepareStatement(sql)

        ){

            ps.setInt(
                1,
                salesId
            );

            ps.executeUpdate();

        }
        catch(SQLException e){

            throw new RuntimeException(
                "Unable to update email status",
                e
            );

        }

    }


}
