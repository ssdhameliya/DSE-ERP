package org.example.controller;

/** One-shot navigation context for Item Master -> normal Sale/Purchase editors. */
public final class ItemTransactionContext {
    private static String saleItemCode;
    private static String purchaseItemCode;
    private ItemTransactionContext() {}
    public static synchronized void sale(String code){saleItemCode=clean(code);}
    public static synchronized void purchase(String code){purchaseItemCode=clean(code);}
    public static synchronized String consumeSale(){String v=saleItemCode;saleItemCode=null;return v;}
    public static synchronized String consumePurchase(){String v=purchaseItemCode;purchaseItemCode=null;return v;}
    private static String clean(String v){return v==null?null:v.trim();}
}
