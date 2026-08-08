package org.example.invoice.model;

public record InvoiceParty(String name, String address, String gstin, String contactPerson, String phone) {
    public InvoiceParty {
        name = safe(name);
        address = safe(address);
        gstin = safe(gstin);
        contactPerson = safe(contactPerson);
        phone = safe(phone);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
