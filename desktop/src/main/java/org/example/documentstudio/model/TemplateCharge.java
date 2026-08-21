package org.example.documentstudio.model;

/** Generic charge row exposed to Document Studio PDF/Excel renderers. */
public record TemplateCharge(String type, double amount, boolean taxable, double gstPercent, double taxAmount, double total) {
    public TemplateCharge {
        type = type == null ? "" : type;
    }
}
