# DSE ERP 7.3.0 — Document Studio Stabilization Verification

## Baseline
Built from `DSE-ERP-7.3.0-IntelliJ-Splash-Branding-Settings-Final(2).zip`.

## Static verification completed in build environment
- 43 FXML files parse as valid XML.
- All 47 `PdfDesigner.fxml` action handlers resolve to methods in `PdfDesignerController`.
- All 23 `Settings.fxml` action/mouse handlers resolve to methods in `SettingsController`.
- `InvoicePdfService`, `SalesController`, and `SalesListController` are byte-for-byte unchanged from the supplied 7.3.0 baseline.
- Template JSON reader is tolerant of unknown metadata and computed `erpConnected` is excluded from serialization.
- Import pipeline preserves `original.pdf`, creates normalized `source.pdf`, and verifies saved metadata by loading it again.
- Passwords are passed only in memory to PDFBox and are not written to template metadata, settings, or logs.

## Runtime validation required on target workstation
Run with project JDK 25:

```powershell
mvn clean verify
```

Then smoke test:
1. General PDF import (unprotected PDF).
2. Blank PDF creation.
3. Purchase/Quotation ERP template creation.
4. Password-protected PDF with valid owner password.
5. Restricted PDF using user password, then owner-password retry.
6. Existing text replacement.
7. Imported raster-image conversion, move/resize/replace.
8. AcroForm field replacement.
9. Settings: Company & Billing -> Payment & Bank -> Invoice -> back to Company/Payment; each should reset to top and size fresh.
10. Confirm Sales PDF output remains unchanged.

## Final Settings fit correction
- Company & Billing uses a 4-column business grid plus compact application-identity row.
- Payment & Bank uses a 3-column / 2-row bank grid and compact QR preview.
- Duplicate Application Tagline label removed.
- Shared Settings ScrollPane is explicitly injected and reset/re-laid out on each category switch.
