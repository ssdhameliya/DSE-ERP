package org.example.invoice.model;

public record CompanyProfile(
        String name,
        String address,
        String gstin,
        String email,
        String alternateEmail,
        String phone,
        String bankName,
        String bankBranch,
        String accountNumber,
        String ifsc,
        String accountType,
        String paymentMode,
        String terms,
        String logoPath) {
    public CompanyProfile {
        name = def(name, "JASVI INDUSTRIES");
        address = safe(address);
        gstin = safe(gstin);
        email = safe(email);
        alternateEmail = safe(alternateEmail);
        phone = safe(phone);
        bankName = safe(bankName);
        bankBranch = safe(bankBranch);
        accountNumber = safe(accountNumber);
        ifsc = safe(ifsc);
        accountType = def(accountType, "CURRENT");
        paymentMode = def(paymentMode, "AGAINST DELIVERY");
        terms = safe(terms);
        logoPath = safe(logoPath);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String def(String value, String fallback) {
        String v = safe(value);
        return v.isBlank() ? fallback : v;
    }
}
