# Changelog — DSE ERP v11.0.3

## Release 11.0.3 — Financial Drill-Down Modal Overhaul, Bank Auto-Match Review, Navigation Record Isolation & Email Signature Delivery

- **Release Date:** 2026-10-25
- **Baseline:** Git tag `v11.0.2`
- **Type:** Feature & Enhancement Release (no breaking schema changes)

---

## Key Features & Improvements

### 1. Financial Drill-Down Inspection Modal Overhaul
- **Problem:** Inspect / Drill-Down popup on Financial Statements opened an unstyled JavaFX alert without metric cards, lacked table header icons and colors, and displayed raw ISO dates (`2026-10-25`) rather than adhering to user-configured business date formats.
- **Solution:**
  - Modernized `showDrillDownModal` in `FinancialStatementsController.java` using `PopupTableWorkspace.prepareDialog(dialog, 980)` and `prepareTable(table, "erp-table-profile-dialog")`.
  - Added a responsive metric strip highlighting Category, Transaction Count, and Net Total.
  - Decorated every column header (`Date`, `Voucher / Ref`, `Party / Account`, `Description`, `Debit`, `Credit`, `Balance`) with centralized semantic icons and themed colors via `IconFactory.applyTableHeaderIcon`.
  - Enforced business date formatting (`BusinessClock.formatDate`) in both cell rendering and CSV export.
  - Monetary values styled with semantic color tokens (`SemanticTableCells.moneyString("orange")`, `"green"`, `"blue"`).

### 2. Bank Statement Bulk Auto-Match Suggestions Dialog
- **Problem:** Users could only match bank transactions one by one, lacking a centralized screen to review all high-confidence auto-matched suggestions before bulk approval.
- **Solution:**
  - Added "Auto Match Suggestions" button to the bank bulk action toolbar in `BankStatement.fxml`.
  - Built an interactive bulk review popup in `BankStatementController.java` with complete transaction metadata: Selection Checkbox, Bank Date, Reference / Cheque No., Description / Narration, Bank Amount, Matched Document, Customer / Supplier, Document Date, Document Outstanding Amount, and Confidence Score.
  - Integrated top metric summary cards and real-time search filtering.
  - Implemented interactive "Refresh Suggestions" and "Confirm Match" buttons.
  - Bulk matching executes step-by-step with real-time feedback through `LiveOperationDialog`, creating corresponding Bank Entries and payment/refund allocations.

### 3. Bank Statement KPI Cards Interactive Filtering
- **Problem:** The 8th `Reconciled` KPI card crowded the top strip, and cards were not interactive.
- **Solution:**
  - Removed the redundant 8th card (`kpiReconciled`) from `BankStatement.fxml`.
  - Transformed `Total Transactions`, `Unmatched`, `Suggested`, `Matched`, and `Expenses` cards into clickable filters with pointer cursor (`Cursor.HAND`).
  - Clicking any card immediately filters the statement table to that status.

### 4. Universal Navigation Isolation Across All Registers
- **Problem:** Traveling to records from the global search bar, notification drawer, or linked buttons (Bank Statement, Bank Expense, Sales, Purchase, Quotation, Item Master, Party Master) loaded all 50-100 rows, making it hard to identify the target record.
- **Solution:**
  - Enhanced `DeepLinkSupport.java` and controller navigation handlers (`ItemMasterController`, `PartyMasterController`, `SalesListController`, `PurchaseListController`, `QuotationController`, `BankStatementController`) to isolate and display **only that single target record**.
  - Automatically populated the search bar with the target reference or code.

### 5. Email Signature Inline Image Delivery
- **Problem:** Signature and stamp images were not visible in email clients when sent via shared client-server mode.
- **Solution:**
  - Enhanced `SmtpMailService.java` to package signature images into a MIME `multipart/related` structure with `cid:signatureImage`.
  - Overloaded `BusinessEmailClient`, `BusinessEmailController`, and `EmailService` to transmit Base64-encoded signature image payloads seamlessly in shared client mode.

---

## Release Gate Status
All 32 release gates pass — `RELEASE_GATES_CURRENT_OK version=11.0.3`
