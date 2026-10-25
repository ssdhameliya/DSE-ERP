# Changelog — DSE ERP v11.0.1

## Release 11.0.1 — Email Signature & Transmission Dialog Patch

- **Release Date:** 2026-10-25
- **Baseline:** Git tag `v11.0.0`
- **Type:** Patch (bug-fix only, no breaking changes)

---

## Bug Fixes

### Email Delivery — Signature & Stamp Images Not Sending
- **Root Cause:** `EmailService` was building a plain-text MIME body. Signature/stamp images configured under Settings → Email Delivery were never attached or inlined.
- **Fix:** Rebuilt MIME structure to `multipart/mixed → multipart/related` with HTML body and inline `Content-ID` image parts. Signature/stamp images now render visually inside the recipient's email client.

### SMTP Password Wiped on Settings Save
- **Root Cause:** `SettingsController` overwrote the encrypted SMTP app-password with an empty string whenever the user saved other settings fields (e.g., signature image upload) without re-typing the password.
- **Fix:** Added blank-password guard — if the password field is left empty on save, the existing encrypted credential is preserved.

### Signature Block Not Appended to Email Templates
- **Root Cause:** `MessageTemplateService` generated template bodies for Sales, Purchase, Quotation, and Sales Returns without reading or appending the configured signature block.
- **Fix:** Auto-appends the stored signature block (text or image path) to all four outbound templates.

---

## New Features

### Email Transmission Dialog (Pre-Send Preview)
- New `EmailTransmissionDialog` (FXML + Controller) shown before every document email dispatch.
- Displays: recipient list, editable subject & body, live signature/stamp image thumbnail.
- **[Preview PDF]** button — opens the attached PDF in the system viewer before sending.
- Integrated into: Sales Invoices, Purchase Orders, Quotations, Sales Returns.

---

## Files Changed

| File | Change |
|------|--------|
| `SettingsController.java` | Blank-password guard |
| `EmailService.java` | multipart/related inline image MIME |
| `MessageTemplateService.java` | Auto-append signature block |
| `EmailTransmissionDialog.java` | New — pre-send interactive modal |
| `EmailTransmissionDialog.fxml` | New — FXML layout for modal |
| `SalesListController.java` | Integrated EmailTransmissionDialog |
| `PurchaseListController.java` | Integrated EmailTransmissionDialog |
| `QuotationController.java` | Integrated EmailTransmissionDialog |
| `SalesReturnsController.java` | Integrated EmailTransmissionDialog |
| `app-dialog.css` | Dialog styling for transmission modal |
| `audit-release-gates.py` | FXML count → 72 |

---

## Release Gate Status
All 32 release gates pass — `RELEASE_GATES_CURRENT_OK version=11.0.1`
