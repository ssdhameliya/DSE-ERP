package org.example.controller;

import org.example.model.Party;

/** One-shot navigation context used only to seed the normal Create Purchase workflow. */
public final class SupplierPurchaseContext {
    private static Party selected;
    private SupplierPurchaseContext() {}
    public static synchronized void select(Party supplier) { selected = supplier; }
    public static synchronized Party consume() { Party value = selected; selected = null; return value; }
}
