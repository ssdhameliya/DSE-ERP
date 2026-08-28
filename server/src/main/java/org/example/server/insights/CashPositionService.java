package org.example.server.insights;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One authoritative cash/bank-position calculation for Dashboard and Finance KPIs. */
@Service
public class CashPositionService {
    private final JpaNativeRepository jdbc;
    public CashPositionService(JpaNativeRepository jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public double cashPosition(){
        double opening=n("SELECT COALESCE(SUM(opening_balance),0) FROM (SELECT DISTINCT ON (COALESCE(bank_account,'')) opening_balance FROM bank_statement_import WHERE opening_balance IS NOT NULL ORDER BY COALESCE(bank_account,''),dse_safe_date(statement_from) ASC NULLS LAST,imported_at ASC,id ASC) x");
        double saleReceipts=n("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)='SALE'");
        double purchasePayments=n("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE UPPER(document_type)='PURCHASE'");
        double salesRefunds=n("SELECT COALESCE(SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)),0) FROM return_refund rr JOIN return_register r ON r.return_no=rr.return_no WHERE UPPER(COALESCE(r.return_type,'')) IN ('SALE RETURN','SALES RETURN') AND UPPER(COALESCE(r.status,'')) NOT IN ('CANCELLED','DELETED')");
        double purchaseRefunds=n("SELECT COALESCE(SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)),0) FROM return_refund rr JOIN return_register r ON r.return_no=rr.return_no WHERE UPPER(COALESCE(r.return_type,''))='PURCHASE RETURN' AND UPPER(COALESCE(r.status,'')) NOT IN ('CANCELLED','DELETED')");
        double deposits=n("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(COALESCE(voucher_type,''))='BANK DEPOSIT'");
        double withdrawals=n("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(COALESCE(voucher_type,''))='BANK WITHDRAWAL'");
        double expenses=n("SELECT COALESCE(SUM(amount),0) FROM finance_register WHERE UPPER(COALESCE(voucher_type,''))='EXPENSE'");
        return opening+saleReceipts+purchaseRefunds+deposits-purchasePayments-salesRefunds-withdrawals-expenses;
    }

    private double n(String sql){Double v=jdbc.queryForObject(sql,Double.class);return v==null?0:v;}
}
