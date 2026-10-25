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
- **Root Cause:** `SettingsController` overwrote the encrypted SMTP app-password with an empty string whenever the user saved other settings fields without re-typing the password.
- **Fix:** Added blank-password guard — if the password field is left empty on save, the existing encrypted credential is preserved.

### Signature Block Not Appended to Email Templates
- **Root Cause:** `MessageTemplateService` generated template bodies without reading or appending the configured signature block.
- **Fix:** Auto-appends the stored signature block (text or image path) to all four outbound templates (Sales, Purchase, Quotation, Returns).

---

## New Features

### Email Transmission Dialog (Pre-Send Preview)
- New `EmailTransmissionDialog` (FXML + Controller) shown before every document email dispatch.
- Displays: recipient list, editable subject & body, live signature/stamp image thumbnail.
- **[Preview PDF]** button — opens the attached PDF in the system viewer before sending.
- Integrated into: Sales Invoices, Purchase Orders, Quotations, Sales Returns.

---

## Release Gate Status
All 32 release gates pass — `RELEASE_GATES_CURRENT_OK version=11.0.1`
