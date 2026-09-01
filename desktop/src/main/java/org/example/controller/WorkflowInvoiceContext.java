package org.example.controller;

/** One-shot handoff from the pre-invoice workflow into the existing production invoice screens. */
public final class WorkflowInvoiceContext {
    public record Link(String projectNo, String orderNo, String sourceNo, String customerPoNo) { }
    private static Link pendingSale;
    private static Link pendingPurchase;
    private WorkflowInvoiceContext() { }
    public static synchronized void prepareSale(String projectNo, String salesOrderNo, String dispatchNo, String customerPoNo) {
        pendingSale = new Link(s(projectNo), s(salesOrderNo), s(dispatchNo), s(customerPoNo));
    }
    public static synchronized void preparePurchase(String projectNo, String purchaseOrderNo, String grnNo) {
        pendingPurchase = new Link(s(projectNo), s(purchaseOrderNo), s(grnNo), "");
    }
    public static synchronized Link consumeSale() { Link v=pendingSale; pendingSale=null; return v; }
    public static synchronized Link consumePurchase() { Link v=pendingPurchase; pendingPurchase=null; return v; }
    private static String s(String v){ return v==null?"":v.trim(); }
}
