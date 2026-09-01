package org.example.server.customer360;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class Customer360Service {
    private final JpaNativeRepository jdbc;
    public Customer360Service(JpaNativeRepository jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public Customer360Dtos.Summary summary(int customerId){
        requireCustomer(customerId); CurrentUser.requirePermission("CUSTOMERS.VIEW","View Customer 360");
        var customer=customer(customerId);
        BigDecimal outstanding=has("SALES.VIEW")?money("SELECT COALESCE(SUM(GREATEST(COALESCE(total_amount,0)-COALESCE(paid_amount,0),0)),0) FROM sales_header WHERE customer_id=? AND UPPER(COALESCE(document_status,'')) NOT IN ('DELETED','CANCELLED','REJECTED','PENDING APPROVAL')",customerId):BigDecimal.ZERO;
        BigDecimal quoteValue=BigDecimal.ZERO; long quoteCount=0;
        List<Customer360Dtos.QuotationRow> recentQuotes=List.of();
        if(has("QUOTATION.VIEW")){
            quoteValue=money("SELECT COALESCE(SUM(total_amount),0) FROM quotation_header WHERE customer_id=? AND UPPER(COALESCE(status,'')) NOT IN ('EXPIRED','REJECTED','DELETED','CONVERTED')",customerId);
            quoteCount=count("SELECT COUNT(*) FROM quotation_header WHERE customer_id=? AND UPPER(COALESCE(status,'')) NOT IN ('EXPIRED','REJECTED','DELETED','CONVERTED')",customerId);
            recentQuotes=quotations(customerId,5);
        }
        BigDecimal orderValue=BigDecimal.ZERO; long orderCount=0, projectCount=0;
        List<Customer360Dtos.WorkflowRow> recentOrders=List.of();
        if(has("PROJECT_EXECUTION.VIEW")){
            orderValue=money("SELECT COALESCE(SUM(total_amount),0) FROM workflow_document WHERE document_type='SALES_ORDER' AND (party_id=? OR (party_id IS NULL AND LOWER(TRIM(COALESCE(party_name,'')))=LOWER(TRIM(?)))) AND UPPER(COALESCE(status,'')) NOT IN ('CANCELLED','REJECTED','CLOSED')",customerId,customer.name());
            orderCount=count("SELECT COUNT(*) FROM workflow_document WHERE document_type='SALES_ORDER' AND (party_id=? OR (party_id IS NULL AND LOWER(TRIM(COALESCE(party_name,'')))=LOWER(TRIM(?)))) AND UPPER(COALESCE(status,'')) NOT IN ('CANCELLED','REJECTED','CLOSED')",customerId,customer.name());
            projectCount=count("SELECT COUNT(*) FROM workflow_document WHERE document_type='PROJECT' AND (party_id=? OR (party_id IS NULL AND LOWER(TRIM(COALESCE(party_name,'')))=LOWER(TRIM(?)))) AND UPPER(COALESCE(status,'')) NOT IN ('COMPLETED','CANCELLED','CLOSED')",customerId,customer.name());
            recentOrders=workflow(customerId,"SALES_ORDER",5);
        }
        BigDecimal totalSales=has("SALES.VIEW")?money("SELECT COALESCE(SUM(total_amount),0) FROM sales_header WHERE customer_id=? AND UPPER(COALESCE(document_status,'')) NOT IN ('DELETED','CANCELLED','REJECTED','PENDING APPROVAL')",customerId):BigDecimal.ZERO;
        BigDecimal lastPayment=BigDecimal.ZERO; String lastPaymentDate="";
        if(has("SALES.VIEW")){
            var p=jdbc.query("SELECT COALESCE(pr.amount,0),COALESCE(pr.payment_date::text,'') FROM payment_record pr JOIN sales_header sh ON sh.id=pr.document_id AND pr.document_type='SALE' WHERE sh.customer_id=? ORDER BY dse_safe_date(pr.payment_date) DESC,pr.id DESC LIMIT 1",(r,i)->new Object[]{r.getBigDecimal(1),r.getString(2)},customerId);
            if(!p.isEmpty()){lastPayment=p.getFirst()[0] instanceof BigDecimal b?b:BigDecimal.ZERO;lastPaymentDate=Objects.toString(p.getFirst()[1],"");}
        }
        List<Customer360Dtos.InvoiceRow> recentInvoices=has("SALES.VIEW")?invoices(customerId,5):List.of();
        return new Customer360Dtos.Summary(customer,outstanding,quoteValue,quoteCount,orderValue,orderCount,projectCount,totalSales,lastPayment,lastPaymentDate,recentQuotes,recentOrders,recentInvoices);
    }

    @Transactional(readOnly=true) public List<Customer360Dtos.QuotationRow> quotations(int customerId){CurrentUser.requirePermission("CUSTOMERS.VIEW","View Customer 360");CurrentUser.requirePermission("QUOTATION.VIEW","View customer quotations");requireCustomer(customerId);return quotations(customerId,500);}
    private List<Customer360Dtos.QuotationRow> quotations(int id,int limit){return jdbc.query("SELECT id,COALESCE(quotation_no,''),COALESCE(quotation_date::text,''),COALESCE(valid_until::text,''),COALESCE(salesperson,''),COALESCE(total_amount,0),COALESCE(status,''),COALESCE(follow_up_date::text,'') FROM quotation_header WHERE customer_id=? ORDER BY dse_safe_date(quotation_date) DESC,id DESC LIMIT "+limit,(r,i)->new Customer360Dtos.QuotationRow(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBigDecimal(6),r.getString(7),r.getString(8)),id);}

    @Transactional(readOnly=true) public List<Customer360Dtos.WorkflowRow> salesOrders(int customerId){CurrentUser.requirePermission("CUSTOMERS.VIEW","View Customer 360");CurrentUser.requirePermission("PROJECT_EXECUTION.VIEW","View customer Sales Orders");return workflow(customerId,"SALES_ORDER",500);}
    @Transactional(readOnly=true) public List<Customer360Dtos.InvoiceRow> directSales(int customerId){
        CurrentUser.requirePermission("CUSTOMERS.VIEW","View Customer 360");
        requireCustomer(customerId);
        if(!has("SALES.VIEW")) return List.of();
        return jdbc.query("""
            SELECT sh.id,COALESCE(sh.invoice_no,''),COALESCE(sh.invoice_date::text,''),
                   COALESCE(sh.sales_order_no,''),COALESCE(sh.project_no,''),
                   COALESCE(sh.total_amount,0),COALESCE(sh.paid_amount,0),
                   GREATEST(COALESCE(sh.total_amount,0)-COALESCE(sh.paid_amount,0),0),
                   COALESCE(sh.payment_status,''),COALESCE(sh.document_status,'')
            FROM sales_header sh
            WHERE sh.customer_id=?
              AND UPPER(COALESCE(sh.document_status,''))<>'DELETED'
              AND NOT EXISTS (
                  SELECT 1 FROM workflow_document w
                  WHERE (NULLIF(TRIM(COALESCE(sh.sales_order_no,'')),'') IS NOT NULL
                         AND w.document_type='SALES_ORDER'
                         AND UPPER(TRIM(w.document_no))=UPPER(TRIM(sh.sales_order_no)))
                     OR (NULLIF(TRIM(COALESCE(sh.project_no,'')),'') IS NOT NULL
                         AND w.document_type='PROJECT'
                         AND UPPER(TRIM(w.document_no))=UPPER(TRIM(sh.project_no)))
              )
            ORDER BY dse_safe_date(sh.invoice_date) DESC,sh.id DESC
            """,(r,i)->new Customer360Dtos.InvoiceRow(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getString(9),r.getString(10)),customerId);
    }
    @Transactional(readOnly=true) public List<Customer360Dtos.WorkflowRow> projects(int customerId){CurrentUser.requirePermission("CUSTOMERS.VIEW","View Customer 360");CurrentUser.requirePermission("PROJECT_EXECUTION.VIEW","View customer Projects");return workflow(customerId,"PROJECT",500);}
    private List<Customer360Dtos.WorkflowRow> workflow(int customerId,String type,int limit){var c=customer(customerId);return jdbc.query("SELECT id,document_type,document_no,COALESCE(document_date::text,''),COALESCE(project_no,''),COALESCE(parent_no,''),COALESCE(customer_po_no,''),COALESCE(expected_date::text,''),COALESCE(total_amount,0),COALESCE(status,'') FROM workflow_document WHERE document_type=? AND (party_id=? OR (party_id IS NULL AND LOWER(TRIM(COALESCE(party_name,'')))=LOWER(TRIM(?)))) ORDER BY document_date DESC,id DESC LIMIT "+limit,(r,i)->new Customer360Dtos.WorkflowRow(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getBigDecimal(9),r.getString(10)),type,customerId,c.name());}

    @Transactional(readOnly=true) public List<Customer360Dtos.InvoiceRow> invoices(int customerId){CurrentUser.requirePermission("CUSTOMERS.VIEW","View Customer 360");CurrentUser.requirePermission("SALES.VIEW","View customer invoices");requireCustomer(customerId);return invoices(customerId,500);}
    private List<Customer360Dtos.InvoiceRow> invoices(int customerId,int limit){return jdbc.query("SELECT id,invoice_no,COALESCE(invoice_date::text,''),COALESCE(sales_order_no,''),COALESCE(project_no,''),COALESCE(total_amount,0),COALESCE(paid_amount,0),GREATEST(COALESCE(total_amount,0)-COALESCE(paid_amount,0),0),COALESCE(payment_status,''),COALESCE(document_status,'') FROM sales_header WHERE customer_id=? AND UPPER(COALESCE(document_status,''))<>'DELETED' ORDER BY dse_safe_date(invoice_date) DESC,id DESC LIMIT "+limit,(r,i)->new Customer360Dtos.InvoiceRow(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getString(9),r.getString(10)),customerId);}

    @Transactional(readOnly=true) public List<Customer360Dtos.PaymentRow> payments(int customerId){CurrentUser.requirePermission("CUSTOMERS.VIEW","View Customer 360");CurrentUser.requirePermission("SALES.VIEW","View customer payments");requireCustomer(customerId);return jdbc.query("SELECT pr.id,COALESCE(pr.payment_date::text,''),COALESCE(pr.reference_no,''),COALESCE(pr.payment_mode,''),COALESCE(pr.amount,0),COALESCE(sh.invoice_no,''),COALESCE(pr.notes,'') FROM payment_record pr JOIN sales_header sh ON sh.id=pr.document_id AND pr.document_type='SALE' WHERE sh.customer_id=? ORDER BY dse_safe_date(pr.payment_date) DESC,pr.id DESC",(r,i)->new Customer360Dtos.PaymentRow(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getString(6),r.getString(7)),customerId);}

    @Transactional(readOnly=true) public List<Customer360Dtos.ContactRow> contacts(int customerId){CurrentUser.requirePermission("CUSTOMERS.VIEW","View customer contacts");requireCustomer(customerId);return jdbc.query("SELECT id,party_id,contact_name,COALESCE(designation,''),COALESCE(department,''),COALESCE(mobile,''),COALESCE(email,''),is_primary,COALESCE(notes,''),row_version,COALESCE(created_by,''),COALESCE(created_at::text,''),COALESCE(updated_by,''),COALESCE(updated_at::text,'') FROM party_contact WHERE party_id=? ORDER BY is_primary DESC,contact_name,id",(r,i)->new Customer360Dtos.ContactRow(r.getLong(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getBoolean(8),r.getString(9),r.getLong(10),r.getString(11),r.getString(12),r.getString(13),r.getString(14)),customerId);}
    @Transactional public Customer360Dtos.ContactRow saveContact(int customerId,Customer360Dtos.ContactSave d){CurrentUser.requirePermission("CUSTOMERS.EDIT","Manage customer contacts");requireCustomer(customerId);if(d==null||blank(d.name()))throw new IllegalArgumentException("Contact name is required");if(d.primary()){if(d.id()==null)jdbc.update("UPDATE party_contact SET is_primary=FALSE,row_version=row_version+1,updated_by=?,updated_at=? WHERE party_id=? AND is_primary=TRUE",user(),BusinessClock.nowUtcText(),customerId);else jdbc.update("UPDATE party_contact SET is_primary=FALSE,row_version=row_version+1,updated_by=?,updated_at=? WHERE party_id=? AND is_primary=TRUE AND id<>?",user(),BusinessClock.nowUtcText(),customerId,d.id());}long id;if(d.id()==null){id=jdbc.queryForObject("INSERT INTO party_contact(party_id,contact_name,designation,department,mobile,email,is_primary,notes,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,customerId,trim(d.name()),trim(d.designation()),trim(d.department()),trim(d.mobile()),trim(d.email()),d.primary(),trim(d.notes()),user(),BusinessClock.nowUtcText(),user(),BusinessClock.nowUtcText());}else{int n=jdbc.update("UPDATE party_contact SET contact_name=?,designation=?,department=?,mobile=?,email=?,is_primary=?,notes=?,row_version=row_version+1,updated_by=?,updated_at=? WHERE id=? AND party_id=? AND row_version=?",trim(d.name()),trim(d.designation()),trim(d.department()),trim(d.mobile()),trim(d.email()),d.primary(),trim(d.notes()),user(),BusinessClock.nowUtcText(),d.id(),customerId,d.rowVersion());if(n!=1)throw new org.example.server.web.ConcurrentEditException("Customer contact");id=d.id();}return contacts(customerId).stream().filter(x->x.id()==id).findFirst().orElseThrow();}
    @Transactional public void deleteContact(int customerId,long contactId,long rowVersion){CurrentUser.requirePermission("CUSTOMERS.EDIT","Delete customer contact");requireCustomer(customerId);int n=jdbc.update("DELETE FROM party_contact WHERE id=? AND party_id=? AND row_version=?",contactId,customerId,rowVersion);if(n!=1)throw new org.example.server.web.ConcurrentEditException("Customer contact");}

    @Transactional(readOnly=true) public List<Customer360Dtos.NoteRow> notes(int customerId){CurrentUser.requirePermission("CUSTOMERS.VIEW","View customer notes");requireCustomer(customerId);return jdbc.query("SELECT id,party_id,note_text,COALESCE(created_by,''),COALESCE(created_at::text,''),COALESCE(updated_by,''),COALESCE(updated_at::text,''),row_version FROM party_note WHERE party_id=? ORDER BY id DESC",(r,i)->new Customer360Dtos.NoteRow(r.getLong(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getLong(8)),customerId);}
    @Transactional public Customer360Dtos.NoteRow saveNote(int customerId,Customer360Dtos.NoteSave d){CurrentUser.requirePermission("CUSTOMERS.EDIT","Manage customer notes");requireCustomer(customerId);if(d==null||blank(d.note()))throw new IllegalArgumentException("Note is required");long id;if(d.id()==null){id=jdbc.queryForObject("INSERT INTO party_note(party_id,note_text,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?) RETURNING id",Long.class,customerId,trim(d.note()),user(),BusinessClock.nowUtcText(),user(),BusinessClock.nowUtcText());}else{int n=jdbc.update("UPDATE party_note SET note_text=?,row_version=row_version+1,updated_by=?,updated_at=? WHERE id=? AND party_id=? AND row_version=?",trim(d.note()),user(),BusinessClock.nowUtcText(),d.id(),customerId,d.rowVersion());if(n!=1)throw new org.example.server.web.ConcurrentEditException("Customer note");id=d.id();}return notes(customerId).stream().filter(x->x.id()==id).findFirst().orElseThrow();}
    @Transactional public void deleteNote(int customerId,long noteId,long rowVersion){CurrentUser.requirePermission("CUSTOMERS.EDIT","Delete customer note");requireCustomer(customerId);int n=jdbc.update("DELETE FROM party_note WHERE id=? AND party_id=? AND row_version=?",noteId,customerId,rowVersion);if(n!=1)throw new org.example.server.web.ConcurrentEditException("Customer note");}

    private Customer360Dtos.Customer customer(int id){var rows=jdbc.query("SELECT id,party_code,name,COALESCE(contact_person,''),COALESCE(phone,''),COALESCE(email,''),COALESCE(gstin,''),COALESCE(address,''),COALESCE(opening_balance,0),COALESCE(is_active,1),COALESCE(row_version,0) FROM party_master WHERE id=? AND UPPER(party_type)='CUSTOMER'",(r,i)->new Customer360Dtos.Customer(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getBigDecimal(9),r.getInt(10)!=0,r.getLong(11)),id);if(rows.isEmpty())throw new IllegalArgumentException("Customer not found");return rows.getFirst();}
    private void requireCustomer(int id){customer(id);}
    private boolean has(String p){return CurrentUser.hasPermission(p);}
    private String user(){return CurrentUser.require().username();}
    private static boolean blank(String v){return v==null||v.trim().isBlank();}
    private static String trim(String v){return v==null?"":v.trim();}
    private long count(String sql,Object...a){Long n=jdbc.queryForObject(sql,Long.class,a);return n==null?0:n;}
    private BigDecimal money(String sql,Object...a){BigDecimal n=jdbc.queryForObject(sql,BigDecimal.class,a);return n==null?BigDecimal.ZERO:n;}
}
