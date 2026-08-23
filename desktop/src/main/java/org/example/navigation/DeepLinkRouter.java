package org.example.navigation;

import org.example.controller.LinkedRecordContext;

import java.util.Locale;

/** Shared exact-record router used by Global Search and Notification Center. */
public final class DeepLinkRouter {
    private DeepLinkRouter() {}

    public static boolean open(String targetFxml, String moduleKey, Long recordId, String reference, String source) {
        if (targetFxml == null || targetFxml.isBlank()) return false;
        String key = canonicalModuleKey(moduleKey == null || moduleKey.isBlank()
                ? inferModuleKey(targetFxml, "", reference) : moduleKey);
        Integer id = recordId == null || recordId > Integer.MAX_VALUE || recordId < Integer.MIN_VALUE ? null : recordId.intValue();
        String ref = reference == null ? "" : reference.trim();
        if (id == null && ref.isBlank()) return NavigationManager.navigateOrReport(targetFxml);
        LinkedRecordContext.open(key, id, ref, "VIEW", source);
        boolean opened = NavigationManager.navigateOrReport(targetFxml);
        if (!opened) LinkedRecordContext.clear();
        return opened;
    }

    private static String canonicalModuleKey(String value) {
        String key = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "SALES", "SALES_ORDER", "SALES_ORDERS" -> "SALE";
            case "PURCHASES", "PURCHASE_ORDER", "PURCHASE_ORDERS" -> "PURCHASE";
            default -> key;
        };
    }

    public static String inferModuleKey(String fxml, String category, String reference) {
        String text=((fxml==null?"":fxml)+" "+(category==null?"":category)+" "+(reference==null?"":reference)).toLowerCase(Locale.ROOT);
        if(text.contains("salesreturns") || text.contains("sales return")) return "SALES_RETURN";
        if(text.contains("purchasereturn") || text.contains("purchase return")) return "PURCHASE_RETURN";
        if(text.contains("saleslist")||text.contains("sale")) return "SALE";
        if(text.contains("purchaselist")||text.contains("purchase")) return "PURCHASE";
        if(text.contains("quotation")) return "QUOTATION";
        if(text.contains("itemmaster")||text.contains("inventory")) return "ITEM";
        if(text.contains("customer")) return "CUSTOMER";
        if(text.contains("supplier")) return "SUPPLIER";
        if(text.contains("bankstatement")) return "BANK_STATEMENT";
        if(text.contains("bankexpense")) return "FINANCE";
        if(text.contains("paymenthistory")) return "PAYMENT";
        if(text.contains("reminder")) return "REMINDER";
        if(text.contains("communication")) return "COMMUNICATION";
        if(text.contains("masterdata")) return "MASTER";
        if(text.contains("useraccess")) return "USER";
        return "RECORD";
    }
}
