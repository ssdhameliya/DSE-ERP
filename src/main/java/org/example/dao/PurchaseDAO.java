package org.example.dao;

import org.example.database.DatabaseManager;
import org.example.model.Party;
import org.example.model.Purchase;
import org.example.model.PurchaseLine;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class PurchaseDAO {
    //====================================================
    // SAVE PURCHASE
    //====================================================
    public void save(Purchase purchase) {


        String headerSql =
            """
            INSERT INTO purchase_header
            (
                invoice_no,
                invoice_date,
                supplier_id,
                subtotal,
                gst_amount,
                total_amount,
                remarks,
                created_at,
                email_sent
            )
            VALUES
            (
                ?,?,?,?,?,?,?,datetime('now'),0
            )
            """;


        String lineSql =
            """
            INSERT INTO purchase_line
            (
                purchase_id,
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
            COALESCE(opening_stock,0)+?
            WHERE item_code=?
            """;



        try(Connection con =
                DatabaseManager.getConnection()) {


            con.setAutoCommit(false);



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
                    purchase.getInvoiceNo()
                );


                headerPs.setString(
                    2,
                    purchase.getInvoiceDate().toString()
                );


                headerPs.setInt(
                    3,
                    purchase.getSupplier().getId()
                );


                headerPs.setDouble(
                    4,
                    purchase.getSubtotal()
                );


                headerPs.setDouble(
                    5,
                    purchase.getGstAmount()
                );


                headerPs.setDouble(
                    6,
                    purchase.getTotalAmount()
                );


                headerPs.setString(
                    7,
                    purchase.getRemarks()
                );


                headerPs.executeUpdate();



                ResultSet keys =
                    headerPs.getGeneratedKeys();


                keys.next();


                int purchaseId =
                    keys.getInt(1);



                for(PurchaseLine line :
                    purchase.getLines()) {


                    linePs.setInt(
                        1,
                        purchaseId
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

                    linePs.setDouble(5, line.getDiscountPercent());
                    linePs.setDouble(6, line.getDiscountAmount());
                    linePs.setDouble(7, line.getGstPercent());
                    linePs.setDouble(8, line.getTotalAmount());


                    linePs.addBatch();



                    stockPs.setDouble(
                        1,
                        line.getQuantity()
                    );


                    stockPs.setString(
                        2,
                        line.getItemCode()
                    );


                    stockPs.addBatch();

                }



                linePs.executeBatch();

                stockPs.executeBatch();


                con.commit();


            }
            catch(Exception e){

                con.rollback();

                throw e;

            }


        }
        catch(Exception e){
            String detail=e.getMessage();
            if((detail==null||detail.isBlank())&&e.getCause()!=null)detail=e.getCause().getMessage();
            throw new RuntimeException(detail==null||detail.isBlank()?"Unable to save purchase":"Unable to save purchase: "+detail,e);

        }

    }
    //====================================================
    // PURCHASE REGISTER LOAD
    //====================================================


    public List<Purchase> getAll() {

        List<Purchase> purchases = new ArrayList<>();


        String sql =
            """

                SELECT
               ph.*,
               pm.party_code,
               pm.name,
               pm.email,
               pm.phone,
               pm.gstin,
               COALESCE(pl.total_qty,0) AS total_qty
           FROM purchase_header ph
           LEFT JOIN party_master pm
               ON ph.supplier_id = pm.id
           LEFT JOIN (
               SELECT purchase_id, SUM(quantity) AS total_qty
               FROM purchase_line
               GROUP BY purchase_id
           ) pl ON ph.id = pl.purchase_id
           ORDER BY
               ph.invoice_date DESC,
               ph.id DESC
            """;


        try (
            Connection con = DatabaseManager.getConnection();

            PreparedStatement ps =
                con.prepareStatement(sql);

            ResultSet rs =
                ps.executeQuery()

        ) {


            while(rs.next()) {


                Purchase purchase =
                    new Purchase();


                purchase.setId(
                    rs.getInt("id")
                );


                purchase.setInvoiceNo(
                    rs.getString("invoice_no")
                );


                purchase.setInvoiceDate(
                    LocalDate.parse(
                        rs.getString("invoice_date")
                    )
                );



                Party supplier =
                    new Party();


                supplier.setId(
                    rs.getInt("supplier_id")
                );


                supplier.setPartyCode(
                    rs.getString("party_code")
                );


                supplier.setName(
                    rs.getString("name")
                );


                supplier.setEmail(
                    rs.getString("email")
                );
                supplier.setPhone(rs.getString("phone"));
                supplier.setGstin(rs.getString("gstin"));


                purchase.setSupplier(
                    supplier
                );

                purchase.setQuantity(
                    rs.getDouble("total_qty")
                );



                purchase.setSubtotal(
                    rs.getDouble("subtotal")
                );


                purchase.setGstAmount(
                    rs.getDouble("gst_amount")
                );


                purchase.setTotalAmount(
                    rs.getDouble("total_amount")
                );


                purchase.setRemarks(
                    rs.getString("remarks")
                );


                purchase.setCreatedAt(
                    rs.getString("created_at")
                );


                purchase.setEmailSent(
                    rs.getInt("email_sent") == 1
                );
                String dueDate=rs.getString("due_date");
                purchase.setDueDate(dueDate==null||dueDate.isBlank()?null:LocalDate.parse(dueDate));
                purchase.setPaidAmount(rs.getDouble("paid_amount"));
                purchase.setPaymentStatus(rs.getString("payment_status"));
                purchase.setDocumentStatus(rs.getString("document_status"));purchase.setWarehouse(rs.getString("warehouse"));purchase.setPaymentTerms(rs.getString("payment_terms"));purchase.setCurrency(rs.getString("currency"));purchase.setReferenceNo(rs.getString("reference_no"));purchase.setGstTreatment(rs.getString("gst_treatment"));purchase.setTransporter(rs.getString("transporter"));purchase.setLrAwbNo(rs.getString("lr_awb_no"));purchase.setDiscountType(rs.getString("discount_type"));purchase.setDiscountAmount(rs.getDouble("discount_amount"));purchase.setAttachmentPath(rs.getString("attachment_path"));purchase.setCreatedBy(rs.getString("created_by"));String delivery=rs.getString("delivery_date");purchase.setDeliveryDate(delivery==null||delivery.isBlank()?null:LocalDate.parse(delivery));


                purchases.add(
                    purchase
                );

            }



        }
        catch(SQLException ex) {


            throw new RuntimeException(
                "Unable to load purchase register.",
                ex
            );

        }


        return purchases;

    }
    //====================================================
    // NEXT INVOICE
    //====================================================
    public String nextInvoiceNo(){

        /*
         * COUNT(*) is not safe for document numbering because deleted rows or
         * imported records can make the next count collide with an existing
         * invoice number. Read the highest numeric PUR- suffix instead.
         */
        String sql =
            """
            SELECT COALESCE(
                MAX(
                    CASE
                        WHEN UPPER(TRIM(invoice_no)) GLOB 'PUR-[0-9]*'
                        THEN CAST(SUBSTR(UPPER(TRIM(invoice_no)), 5) AS INTEGER)
                        ELSE 0
                    END
                ),
                0
            ) + 1
            FROM purchase_header
            """;

        try(
            Connection con = DatabaseManager.getConnection();
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql)
        ){
            int no = rs.next() ? rs.getInt(1) : 1;
            return "PUR-" + String.format("%05d", Math.max(1, no));
        }
        catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public Purchase getByInvoice(String invoiceNo){


        String headerSql =
            """
            SELECT
                ph.*,
                pm.party_code,
                pm.name,
                pm.email,
                pm.phone,
                pm.gstin
            FROM purchase_header ph
            LEFT JOIN party_master pm
            ON ph.supplier_id = pm.id
            WHERE TRIM(UPPER(ph.invoice_no))=TRIM(UPPER(?))
            """;



        String lineSql =
            """
            SELECT
                pl.*,
                im.description
            FROM purchase_line pl
            LEFT JOIN item_master im
            ON pl.item_code = im.item_code
            WHERE pl.purchase_id=?
            ORDER BY pl.id
            """;



        try(
            Connection con =
                DatabaseManager.getConnection()

        ){



            Purchase p=null;



            PreparedStatement ps =
                con.prepareStatement(headerSql);


            ps.setString(
                1,
                invoiceNo
            );


            ResultSet rs =
                ps.executeQuery();



            if(rs.next()){


                p=new Purchase();


                p.setId(
                    rs.getInt("id")
                );


                p.setInvoiceNo(
                    rs.getString("invoice_no")
                );


                p.setInvoiceDate(
                    LocalDate.parse(
                        rs.getString("invoice_date")
                    )
                );


                Party party =
                    new Party();


                party.setId(
                    rs.getInt("supplier_id")
                );


                party.setName(
                    rs.getString("name")
                );


                party.setPartyCode(
                    rs.getString("party_code")
                );


                party.setEmail(
                    rs.getString("email")
                );
                party.setPhone(rs.getString("phone"));
                party.setGstin(rs.getString("gstin"));


                p.setSupplier(party);



                p.setSubtotal(
                    rs.getDouble("subtotal")
                );


                p.setGstAmount(
                    rs.getDouble("gst_amount")
                );


                p.setTotalAmount(
                    rs.getDouble("total_amount")
                );


                p.setRemarks(
                    rs.getString("remarks")
                );
                String dueDate=rs.getString("due_date");
                p.setDueDate(dueDate==null||dueDate.isBlank()?null:LocalDate.parse(dueDate));
                p.setPaidAmount(rs.getDouble("paid_amount"));
                p.setPaymentStatus(rs.getString("payment_status"));
                p.setEmailSent(rs.getInt("email_sent")==1);
                p.setCreatedAt(rs.getString("created_at"));
                p.setDocumentStatus(rs.getString("document_status"));p.setWarehouse(rs.getString("warehouse"));p.setPaymentTerms(rs.getString("payment_terms"));p.setCurrency(rs.getString("currency"));p.setReferenceNo(rs.getString("reference_no"));p.setGstTreatment(rs.getString("gst_treatment"));p.setTransporter(rs.getString("transporter"));p.setLrAwbNo(rs.getString("lr_awb_no"));p.setDiscountType(rs.getString("discount_type"));p.setDiscountAmount(rs.getDouble("discount_amount"));p.setAttachmentPath(rs.getString("attachment_path"));p.setCreatedBy(rs.getString("created_by"));String delivery=rs.getString("delivery_date");p.setDeliveryDate(delivery==null||delivery.isBlank()?null:LocalDate.parse(delivery));

            }



            if(p==null)
                return null;



            List<PurchaseLine> lines =
                new ArrayList<>();



            PreparedStatement lp =
                con.prepareStatement(lineSql);


            lp.setInt(
                1,
                p.getId()
            );


            ResultSet lr =
                lp.executeQuery();



            while (lr.next()) {

                PurchaseLine line =
                    new PurchaseLine();

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

                line.calculateAmounts();
                line.setLineTotal(line.getTotalAmount());

                lines.add(line);

            }


            p.setLines(lines);


            return p;


        }
        catch(Exception e){

            throw new RuntimeException(
                "Unable to load purchase",
                e
            );

        }


    }
    //====================================================
// UPDATE PURCHASE
//====================================================

    public void update(Purchase purchase) {


        String updateHeader =
            """
            UPDATE purchase_header
            SET
                invoice_date = ?,
                supplier_id = ?,
                subtotal = ?,
                gst_amount = ?,
                total_amount = ?,
                remarks = ?
            WHERE id = ?
            """;


        String deleteLines =
            """
            DELETE FROM purchase_line
            WHERE purchase_id = ?
            """;


        String insertLine =
            """
            INSERT INTO purchase_line
            (
                purchase_id,
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

        String reverseOldStock =
            """
            UPDATE item_master
            SET opening_stock = COALESCE(opening_stock, 0) -
                COALESCE((SELECT SUM(quantity) FROM purchase_line
                          WHERE purchase_id = ? AND item_code = item_master.item_code), 0)
            WHERE item_code IN (SELECT item_code FROM purchase_line WHERE purchase_id = ?)
            """;

        String receiveNewStock =
            """
            UPDATE item_master SET opening_stock = COALESCE(opening_stock, 0) + ?
            WHERE item_code = ?
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

                PreparedStatement reversePs =
                    con.prepareStatement(reverseOldStock);

                PreparedStatement stockPs =
                    con.prepareStatement(receiveNewStock)

            ){


                reversePs.setInt(1, purchase.getId());
                reversePs.setInt(2, purchase.getId());
                reversePs.executeUpdate();

                updatePs.setString(
                    1,
                    purchase.getInvoiceDate().toString()
                );


                updatePs.setInt(
                    2,
                    purchase.getSupplier().getId()
                );


                updatePs.setDouble(
                    3,
                    purchase.getSubtotal()
                );


                updatePs.setDouble(
                    4,
                    purchase.getGstAmount()
                );


                updatePs.setDouble(
                    5,
                    purchase.getTotalAmount()
                );


                updatePs.setString(
                    6,
                    purchase.getRemarks()
                );


                updatePs.setInt(
                    7,
                    purchase.getId()
                );


                updatePs.executeUpdate();



                // remove old lines

                deletePs.setInt(
                    1,
                    purchase.getId()
                );


                deletePs.executeUpdate();



                // insert updated lines

                for(PurchaseLine line :
                    purchase.getLines()) {

                    stockPs.setDouble(1, line.getQuantity());
                    stockPs.setString(2, line.getItemCode());
                    if (stockPs.executeUpdate() != 1) {
                        throw new IllegalStateException(
                            "Unknown inventory item " + line.getItemCode());
                    }


                    linePs.setInt(
                        1,
                        purchase.getId()
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

                    linePs.setDouble(5, line.getDiscountPercent());
                    linePs.setDouble(6, line.getDiscountAmount());
                    linePs.setDouble(7, line.getGstPercent());
                    linePs.setDouble(8, line.getTotalAmount());


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
                "Unable to update purchase",
                e
            );

        }

    }

    public void delete(String invoiceNo) {

        String reverseStock =
            """
            UPDATE item_master
            SET opening_stock = COALESCE(opening_stock, 0) -
                COALESCE((SELECT SUM(pl.quantity)
                          FROM purchase_line pl JOIN purchase_header ph ON ph.id = pl.purchase_id
                          WHERE ph.invoice_no = ? AND pl.item_code = item_master.item_code), 0)
            WHERE item_code IN (
                SELECT pl.item_code FROM purchase_line pl
                JOIN purchase_header ph ON ph.id = pl.purchase_id
                WHERE ph.invoice_no = ? AND COALESCE(ph.document_status, 'COMPLETED') <> 'CANCELLED')
            """;

        String deleteLines =
            """
            DELETE FROM purchase_line
            WHERE purchase_id =
            (
                SELECT id
                FROM purchase_header
                WHERE invoice_no = ?
            )
            """;


        String deleteHeader =
            """
            DELETE FROM purchase_header
            WHERE invoice_no = ?
            """;


        try(Connection con = DatabaseManager.getConnection()) {

            con.setAutoCommit(false);


            try(
                PreparedStatement stockPs =
                    con.prepareStatement(reverseStock);

                PreparedStatement ps1 =
                    con.prepareStatement(deleteLines);

                PreparedStatement ps2 =
                    con.prepareStatement(deleteHeader)
            ) {


                stockPs.setString(1, invoiceNo);
                stockPs.setString(2, invoiceNo);
                stockPs.executeUpdate();

                ps1.setString(1, invoiceNo);
                ps1.executeUpdate();


                ps2.setString(1, invoiceNo);
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
                "Unable to delete purchase.",
                e
            );

        }

    }

    public void markEmailSent(int purchaseId){


        String sql =
            """
            UPDATE purchase_header
            SET email_sent=1
            WHERE id=?
            """;


        try(
            Connection con =
                DatabaseManager.getConnection();

            PreparedStatement ps =
                con.prepareStatement(sql)

        ){


            ps.setInt(
                1,
                purchaseId
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
