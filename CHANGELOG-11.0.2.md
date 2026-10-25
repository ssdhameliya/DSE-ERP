# Changelog — DSE ERP v11.0.2

## Release 11.0.2 — Reference Format Tokens, Email Modal Resizing & Purchase Return Email Preview

- **Release Date:** 2026-10-25
- **Baseline:** Git tag `v11.0.1`
- **Type:** Patch (enhancement & bug-fix, no breaking changes)

---

## Bug Fixes & Enhancements

### 1. Dynamic Reference Format Tokens (`DD`, `MM`, `YYYY`, `YY`)
- **Issue:** When user configured auto-generated reference formats such as `DD-YYYY-XXXX` or `MM-YYYY-XXXX`, the system replaced `YYYY` with the year (e.g. `2026`), but left `DD` and `MM` as literal strings (`DD-2026-0001` / `MM-2026-0001`).
- **Root Cause:** In `BusinessOperationsService.java`, `datedReferenceFormat` only replaced compound dates (`DD-MM-YYYY`, `DD/MM/YYYY`, `YYYY-MM-DD`) and `YYYY`/`YY`, leaving individual `DD` and `MM` unhandled.
- **Fix:** Enhanced `datedReferenceFormat` to replace `DD` with 2-digit day and `MM` with 2-digit month individually. Formats like `DD-YYYY-XXXX` resolve to `25-2026-0001`, `MM-YYYY-XXXX` to `10-2026-0001`, and `DDMMYYYY-XXXX` to `25102026-0001`. Sequence `XXXX` continues to increment and auto-expand beyond 9999.

### 2. Email Transmission Modal Clipping & Fixed Dimensions
- **Issue:** In the email dispatch modal, the message body was clipped to roughly two visible lines, preventing users from seeing invoice summaries and signature blocks.
- **Root Cause:** `EmailTransmissionDialog.fxml` had a fixed height of 650 with unconstrained vertical growth, and `OwnedDialog` was set to `resizable=false`.
- **Fix:** Increased default dialog dimensions to 720x760 (minimum 620x560), enabled `dialog.setResizable(true)`, and configured `txtBody` with `prefRowCount="12"`, `minHeight="220"`, and `VBox.vgrow="ALWAYS"`.

### 3. Purchase Return Register — Missing Pre-Send Email Preview
- **Issue:** Clicking "Send Email" on a Purchase Return dispatched the email directly in the background without opening the preview dialog.
- **Root Cause:** `PurchaseReturnsController.java` called `EmailService.send(...)` directly without constructing an `EmailTransmissionDialog.EmailDraft` or invoking `EmailTransmissionDialog.show(...)`.
- **Fix:** 
  - Added `PURCHASE_RETURN` to `MessageTemplateService.DocumentType` with customizable default email subjects, debit note bodies, and formatting utilities.
  - Wired `Purchase Return (Email)` into Settings → Email Delivery templates.
  - Refactored `PurchaseReturnsController.java` to construct `EmailDraft` and display `EmailTransmissionDialog.show(...)` with attached debit note PDF preview and live progress reporting via `LiveOperationDialog`.

---

## Release Gate Status
All 32 release gates pass — `RELEASE_GATES_CURRENT_OK version=11.0.2`
