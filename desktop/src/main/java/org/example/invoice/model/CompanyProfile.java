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
        String logoPath,
        String signaturePath,
        String certificationText) {
    public CompanyProfile {
        name = safe(name);
        address = safe(address);
        gstin = safe(gstin);
        email = safe(email);
        alternateEmail = safe(alternateEmail);
        phone = safe(phone);
        bankName = safe(bankName);
        bankBranch = safe(bankBranch);
        accountNumber = safe(accountNumber);
        ifsc = safe(ifsc);
        accountType = safe(accountType);
        paymentMode = safe(paymentMode);
        terms = safe(terms);
        logoPath = safe(logoPath);
        signaturePath = safe(signaturePath);
        certificationText = safe(certificationText);
    }


    public CompanyProfile(String name, String address, String gstin, String email, String alternateEmail,
                          String phone, String bankName, String bankBranch, String accountNumber, String ifsc,
                          String accountType, String paymentMode, String terms, String logoPath) {
        this(name, address, gstin, email, alternateEmail, phone, bankName, bankBranch, accountNumber, ifsc,
                accountType, paymentMode, terms, logoPath, "", "");
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String def(String value, String fallback) {
        String v = safe(value);
        return v.isBlank() ? fallback : v;
    }
}
