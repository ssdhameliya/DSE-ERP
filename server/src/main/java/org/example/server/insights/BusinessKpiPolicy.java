package org.example.server.insights;

/**
 * Canonical SQL fragments for business KPIs and effective financial state.
 *
 * <p>Registers, Dashboard, Reports and Global Search must use the same
 * accounting rules. Header payment history remains untouched; an active Return
 * is projected as an effective financial overlay.</p>
 */
public final class BusinessKpiPolicy {
    private BusinessKpiPolicy() { }

    public static String salesActive(String alias) {
        return "UPPER(COALESCE(" + alias + ".document_status,'')) NOT IN " +
                "('DELETED','CANCELLED','DRAFT','REJECTED','PENDING APPROVAL')";
    }

    public static String purchasesActive(String alias) {
        return "UPPER(COALESCE(" + alias + ".document_status,'')) NOT IN " +
                "('DELETED','CANCELLED','DRAFT','REJECTED','PENDING APPROVAL') " +
                "AND COALESCE(" + alias + ".inventory_posted,false)=true";
    }

    /** Only approved Returns are accounting-active. Pending/rejected Returns are workflow records only. */
    public static String returnsActive(String alias) {
        return "UPPER(COALESCE(" + alias + ".status,''))='APPROVED'";
    }

    public static String quotationsVisible(String alias) {
        return "UPPER(COALESCE(" + alias + ".status,''))<>'DELETED'";
    }

    /**
     * Header-paid is retained for backward compatibility, while payment_record
     * is authoritative when it contains a larger posted total. A PAID/SETTLED
     * header is treated as fully paid even on older imported data.
     */
    public static String effectivePaid(String headerAlias, String paymentAlias) {
        return "LEAST(GREATEST(COALESCE(" + headerAlias + ".total_amount,0),0)," +
                "GREATEST(COALESCE(" + headerAlias + ".paid_amount,0)," +
                "COALESCE(" + paymentAlias + ".recorded_paid,0)," +
                "CASE WHEN UPPER(COALESCE(" + headerAlias + ".payment_status,'')) IN ('PAID','SETTLED') " +
                "THEN COALESCE(" + headerAlias + ".total_amount,0) ELSE 0 END))";
    }

    public static String outstanding(String headerAlias, String paymentAlias) {
        return "GREATEST(COALESCE(" + headerAlias + ".total_amount,0)-(" +
                effectivePaid(headerAlias, paymentAlias) + "),0)";
    }

    /** Correlated payment expression for screens that do not already have a payment aggregate join. */
    public static String effectivePaidCorrelated(String headerAlias, String documentType) {
        String type = documentType(documentType);
        return "LEAST(GREATEST(COALESCE(" + headerAlias + ".total_amount,0),0)," +
                "GREATEST(COALESCE(" + headerAlias + ".paid_amount,0)," +
                "COALESCE((SELECT SUM(px.amount) FROM payment_record px WHERE UPPER(px.document_type)='" + type + "' AND px.document_id=" + headerAlias + ".id),0)," +
                "CASE WHEN UPPER(COALESCE(" + headerAlias + ".payment_status,'')) IN ('PAID','SETTLED') " +
                "THEN COALESCE(" + headerAlias + ".total_amount,0) ELSE 0 END))";
    }

    /** Original invoice outstanding. Approved Return refunds are a separate liability/receivable lifecycle. */
    public static String effectiveOutstanding(String headerAlias, String documentType) {
        return "GREATEST(COALESCE(" + headerAlias + ".total_amount,0)-(" + effectivePaidCorrelated(headerAlias, documentType) + "),0)";
    }

    /** Original invoice payment state; Return/refund lifecycle is presented separately. */
    public static String effectivePaymentStatus(String headerAlias, String documentType) {
        String paid = effectivePaidCorrelated(headerAlias, documentType);
        return "CASE WHEN COALESCE(" + headerAlias + ".total_amount,0)>0 AND (" + paid + ")+0.0001>=COALESCE(" + headerAlias + ".total_amount,0) THEN 'PAID' " +
                "WHEN (" + paid + ")>0.0001 THEN 'PARTIAL' ELSE COALESCE(NULLIF(UPPER(" + headerAlias + ".payment_status),''),'PENDING') END";
    }

    /** Original invoice due date. Return settlement due date is reported in the Return lifecycle, not here. */
    public static String effectiveDueDate(String headerAlias, String documentType, String baseDueDateSql) {
        return "(" + baseDueDateSql + ")";
    }

    private static String pendingApprovalReturnCount(String headerAlias, String documentType) {
        String type = returnType(documentType);
        return "COALESCE((SELECT COUNT(*) FROM (SELECT rpa.return_no FROM return_register rpa WHERE rpa.invoice_no=" + headerAlias + ".invoice_no AND UPPER(COALESCE(rpa.return_type,''))='" + type + "' GROUP BY rpa.return_no HAVING MAX(UPPER(COALESCE(rpa.status,'PENDING APPROVAL')))='PENDING APPROVAL') pending_returns),0)";
    }

    private static String approvedReturnTotal(String headerAlias, String documentType) {
        String type = returnType(documentType);
        return "COALESCE((SELECT SUM(ra.amount) FROM return_register ra WHERE ra.invoice_no=" + headerAlias + ".invoice_no AND UPPER(COALESCE(ra.return_type,''))='" + type + "' AND UPPER(COALESCE(ra.status,''))='APPROVED'),0)";
    }

    private static String settledReturnTotal(String headerAlias, String documentType) {
        String type = returnType(documentType);
        return "COALESCE((SELECT SUM(rr.amount+COALESCE(rr.rounding_adjustment,0)) FROM return_refund rr WHERE rr.return_no IN (SELECT DISTINCT rs.return_no FROM return_register rs WHERE rs.invoice_no=" + headerAlias + ".invoice_no AND UPPER(COALESCE(rs.return_type,''))='" + type + "' AND UPPER(COALESCE(rs.status,''))='APPROVED')),0)";
    }

    private static String documentType(String value) {
        return "PURCHASE".equalsIgnoreCase(value) ? "PURCHASE" : "SALE";
    }

    private static String returnType(String value) {
        return "PURCHASE".equalsIgnoreCase(value) ? "PURCHASE RETURN" : "SALES RETURN";
    }
}
