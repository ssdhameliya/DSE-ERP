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
                reference_no,
                po_date,
                billing_address,
                gst_type,
                door_delivery,
                vehicle_number,
                contact_person,
                transport_note,
                order_no,
                gstin,
                charge_type,
                charge_amount,
                contact_person_mobile,
                document_status
            )
            VALUES
            (
                ?,?,?,?,?,?,?,?,datetime('now'),0,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING'
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
            if (sales.getOrderNo() == null || sales.getOrderNo().isBlank() || orderNumberExists(con, sales.getOrderNo())) {
                sales.setOrderNo(nextOrderNo(con));
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
                headerPs.setString(16, sales.getPoDate() == null ? null : sales.getPoDate().toString());
                headerPs.setString(17, sales.getBillingAddress());
                headerPs.setString(18, sales.getGstType());
                headerPs.setString(19, sales.getDoorDelivery());
                headerPs.setString(20, sales.getVehicleNumber());
                headerPs.setString(21, sales.getContactPerson());
                headerPs.setString(22, sales.getTransportNote());
                headerPs.setString(23, sales.getOrderNo());
                headerPs.setString(24, sales.getGstin());
                headerPs.setString(25, sales.getChargeType());
                headerPs.setDouble(26, sales.getChargeAmount());
                headerPs.setString(27, sales.getContactPersonMobile());

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
                pm.gstin AS party_gstin,
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
                customer.setGstin(rs.getString("party_gstin"));

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
                sale.setDocumentStatus(rs.getString("document_status"));
                sale.setWhatsappSent(rs.getInt("whatsapp_sent") == 1);
                sale.setInvoiceType(rs.getString("invoice_type"));
                sale.setSalesperson(rs.getString("salesperson"));
                sale.setSource(rs.getString("source"));
                sale.setNotes(rs.getString("notes"));
                sale.setDeliveryAddress(rs.getString("delivery_address"));
                sale.setPaymentTerms(rs.getString("payment_terms"));
                sale.setTransporter(rs.getString("transporter"));
                sale.setReferenceNo(rs.getString("reference_no"));
                String poDate = rs.getString("po_date");
                sale.setPoDate(poDate == null || poDate.isBlank() ? null : LocalDate.parse(poDate));
                sale.setBillingAddress(rs.getString("billing_address"));
                sale.setGstType(rs.getString("gst_type"));
                sale.setDoorDelivery(rs.getString("door_delivery"));
                sale.setVehicleNumber(rs.getString("vehicle_number"));
                sale.setContactPerson(rs.getString("contact_person"));
                sale.setTransportNote(rs.getString("transport_note"));
                sale.setOrderNo(rs.getString("order_no"));
                sale.setGstin(rs.getString("gstin"));
                sale.setChargeType(rs.getString("charge_type"));
                sale.setChargeAmount(rs.getDouble("charge_amount"));
                sale.setContactPersonMobile(rs.getString("contact_person_mobile"));

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


    /**
     * Generates the next Order No. from the master-driven PO DATE FORMATE pattern.
     * The visible master category can be renamed because the stable category code
     * PO_DATE_FORMAT is used to resolve the configured format.
     */
    public String nextOrderNo() {
        try (Connection con = DatabaseManager.getConnection()) {
            return nextOrderNo(con);
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate Order No.", e);
        }
    }

    private String nextOrderNo(Connection con) throws SQLException {
        String format = "PO/DD-MM-YYYY/XXXX";
        String sql = """
            SELECT lm.lookup_value
            FROM lookup_master lm
            JOIN master_category mc ON mc.category_name = lm.lookup_type
            WHERE mc.category_code = 'PO_DATE_FORMAT'
              AND mc.is_active = 1
              AND lm.is_active = 1
            ORDER BY lm.display_order, lm.id
            LIMIT 1
            """;
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getString(1) != null && !rs.getString(1).isBlank()) {
                format = rs.getString(1).trim();
            }
        }

        LocalDate today = LocalDate.now();
        String dated = format
            .replace("DD-MM-YYYY", today.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")))
            .replace("DD/MM/YYYY", today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .replace("YYYY-MM-DD", today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        java.util.regex.Matcher token = java.util.regex.Pattern.compile("X{2,}").matcher(dated);
        if (!token.find()) {
            // A format without a sequence token still gets a safe sequence suffix.
            dated = dated + "/XXXX";
            token = java.util.regex.Pattern.compile("X{2,}").matcher(dated);
            token.find();
        }
        int width = token.end() - token.start();
        String prefix = dated.substring(0, token.start());
        String suffix = dated.substring(token.end());

        int max = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT order_no FROM sales_header WHERE order_no IS NOT NULL AND order_no LIKE ?")) {
            ps.setString(1, prefix + "%" + suffix);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String existing = rs.getString(1);
                    if (existing == null || !existing.startsWith(prefix) || !existing.endsWith(suffix)) continue;
                    int end = existing.length() - suffix.length();
                    if (end < prefix.length()) continue;
                    String seq = existing.substring(prefix.length(), end);
                    try { max = Math.max(max, Integer.parseInt(seq)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        int next = max + 1;
        String candidate;
        do {
            candidate = prefix + String.format(java.util.Locale.ROOT, "%0" + width + "d", next++) + suffix;
        } while (orderNumberExists(con, candidate));
        return candidate;
    }

    private boolean orderNumberExists(Connection con, String orderNo) throws SQLException {
        if (orderNo == null || orderNo.isBlank()) return false;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM sales_header WHERE TRIM(UPPER(order_no))=TRIM(UPPER(?)) LIMIT 1")) {
            ps.setString(1, orderNo);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public String nextInvoiceNo() {
        try (Connection con = DatabaseManager.getConnection()) {
            return nextInvoiceNo(con);
        }
        catch (Exception e) {

            throw new RuntimeException(e);

        }

    }

    /**
     * Generates the next Sales Invoice No. from the master-driven SALES INVOICE FORMAT pattern.
     * The visible master name can change; the stable category code keeps Create Sale working.
     */
    private String nextInvoiceNo(Connection con) throws SQLException {
        String format = "IN/DD-MM-YYYY/XXXX";
        String sql = """
            SELECT lm.lookup_value
            FROM lookup_master lm
            JOIN master_category mc ON mc.category_name = lm.lookup_type
            WHERE mc.category_code = 'SALES_INVOICE_FORMAT'
              AND mc.is_active = 1
              AND lm.is_active = 1
            ORDER BY lm.display_order, lm.id
            LIMIT 1
            """;
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getString(1) != null && !rs.getString(1).isBlank()) {
                format = rs.getString(1).trim();
            }
        }

        LocalDate today = LocalDate.now();
        String dated = format
            .replace("DD-MM-YYYY", today.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")))
            .replace("DD/MM/YYYY", today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            .replace("YYYY-MM-DD", today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        java.util.regex.Matcher token = java.util.regex.Pattern.compile("X{2,}").matcher(dated);
        if (!token.find()) {
            dated = dated + "/XXXX";
            token = java.util.regex.Pattern.compile("X{2,}").matcher(dated);
            token.find();
        }
        int width = token.end() - token.start();
        String prefix = dated.substring(0, token.start());
        String suffix = dated.substring(token.end());

        int max = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT invoice_no FROM sales_header WHERE invoice_no IS NOT NULL AND invoice_no LIKE ?")) {
            ps.setString(1, prefix + "%" + suffix);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String existing = rs.getString(1);
                    if (existing == null || !existing.startsWith(prefix) || !existing.endsWith(suffix)) continue;
                    int end = existing.length() - suffix.length();
                    if (end < prefix.length()) continue;
                    String seq = existing.substring(prefix.length(), end);
                    try { max = Math.max(max, Integer.parseInt(seq)); } catch (NumberFormatException ignored) {}
                }
            }
        }

        int next = max + 1;
        String candidate;
        do {
            candidate = prefix + String.format(java.util.Locale.ROOT, "%0" + width + "d", next++) + suffix;
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
                pm.gstin AS party_gstin
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
                customer.setGstin(rs.getString("party_gstin"));

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
                s.setDocumentStatus(rs.getString("document_status"));
                s.setWhatsappSent(rs.getInt("whatsapp_sent") == 1);
                s.setInvoiceType(rs.getString("invoice_type"));
                s.setSalesperson(rs.getString("salesperson"));
                s.setSource(rs.getString("source"));
                s.setNotes(rs.getString("notes"));
                s.setDeliveryAddress(rs.getString("delivery_address"));
                s.setPaymentTerms(rs.getString("payment_terms"));
                s.setTransporter(rs.getString("transporter"));
                s.setReferenceNo(rs.getString("reference_no"));
                String poDate = rs.getString("po_date");
                s.setPoDate(poDate == null || poDate.isBlank() ? null : LocalDate.parse(poDate));
                s.setBillingAddress(rs.getString("billing_address"));
                s.setGstType(rs.getString("gst_type"));
                s.setDoorDelivery(rs.getString("door_delivery"));
                s.setVehicleNumber(rs.getString("vehicle_number"));
                s.setContactPerson(rs.getString("contact_person"));
                s.setTransportNote(rs.getString("transport_note"));
                s.setOrderNo(rs.getString("order_no"));
                s.setGstin(rs.getString("gstin"));
                s.setChargeType(rs.getString("charge_type"));
                s.setChargeAmount(rs.getDouble("charge_amount"));
                s.setContactPersonMobile(rs.getString("contact_person_mobile"));

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
                reference_no = ?,
                po_date = ?,
                billing_address = ?,
                gst_type = ?,
                door_delivery = ?,
                vehicle_number = ?,
                contact_person = ?,
                transport_note = ?,
                order_no = ?,
                gstin = ?,
                charge_type = ?,
                charge_amount = ?,
                contact_person_mobile = ?
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
                updatePs.setString(15, sales.getPoDate() == null ? null : sales.getPoDate().toString());
                updatePs.setString(16, sales.getBillingAddress());
                updatePs.setString(17, sales.getGstType());
                updatePs.setString(18, sales.getDoorDelivery());
                updatePs.setString(19, sales.getVehicleNumber());
                updatePs.setString(20, sales.getContactPerson());
                updatePs.setString(21, sales.getTransportNote());
                updatePs.setString(22, sales.getOrderNo());
                updatePs.setString(23, sales.getGstin());
                updatePs.setString(24, sales.getChargeType());
                updatePs.setDouble(25, sales.getChargeAmount());
                updatePs.setString(26, sales.getContactPersonMobile());

                updatePs.setInt(
                    27,
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
        changeDocumentStatusAndRestoreStock(invoiceNo, "DELETED");
    }

    public void cancel(String invoiceNo) {
        changeDocumentStatusAndRestoreStock(invoiceNo, "CANCELLED");
    }

    private void changeDocumentStatusAndRestoreStock(String invoiceNo, String targetStatus) {
        try (Connection con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                int salesId;
                String currentStatus;
                try (PreparedStatement header = con.prepareStatement(
                        "SELECT id, COALESCE(document_status,'PENDING') FROM sales_header WHERE invoice_no=?")) {
                    header.setString(1, invoiceNo);
                    try (ResultSet rs = header.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Sales document not found: " + invoiceNo);
                        salesId = rs.getInt(1);
                        currentStatus = rs.getString(2);
                    }
                }

                if ("CANCELLED".equalsIgnoreCase(currentStatus) || "DELETED".equalsIgnoreCase(currentStatus)) {
                    if (targetStatus.equalsIgnoreCase(currentStatus)) {
                        con.rollback();
                        return;
                    }
                    // Stock was already restored by the earlier terminal action; only change the status.
                } else {
                    try (PreparedStatement lines = con.prepareStatement(
                            "SELECT item_code, quantity FROM sales_line WHERE sales_id=?");
                         PreparedStatement stock = con.prepareStatement(
                            "UPDATE item_master SET opening_stock=COALESCE(opening_stock,0)+? WHERE item_code=?")) {
                        lines.setInt(1, salesId);
                        try (ResultSet rs = lines.executeQuery()) {
                            while (rs.next()) {
                                stock.setDouble(1, rs.getDouble("quantity"));
                                stock.setString(2, rs.getString("item_code"));
                                stock.addBatch();
                            }
                        }
                        stock.executeBatch();
                    }
                }

                try (PreparedStatement update = con.prepareStatement(
                        "UPDATE sales_header SET document_status=?, payment_status=? WHERE id=?")) {
                    update.setString(1, targetStatus);
                    update.setString(2, targetStatus);
                    update.setInt(3, salesId);
                    update.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to " + targetStatus.toLowerCase(java.util.Locale.ROOT)
                + " sales document " + invoiceNo, e);
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
