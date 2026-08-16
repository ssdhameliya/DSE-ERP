# DSE ERP 7.3.7 — Semantic UI, Settings Navigation & Payment Integrity

## Enhanced
- Expanded the existing `IconFactory` so form labels in normal pages and Add/Edit dialog layouts receive meaningful semantic icons through central structural detection, with explicit business, identity, tax, category, application, date, reference, customer, supplier, item, quantity, amount, bank, payment and notes semantics.
- Rebuilt Settings navigation as a sidebar accordion consistent with Sales, Purchase and Bank & Expense. The seven submenu entries reuse the existing Settings page/controller and persistence logic: Company & Billing, Payment & Bank, Invoice & Delivery, Notifications, Email Settings, Workspace & Storage and Application Updates.
- Standardized the visible Sale, Purchase and Quotation item presentation to `Remarks • Description` while retaining Item Code and existing persistence/stock references internally.
- Added a protected Payment History `Actions` menu with Edit Payment, View Proof and Open Proof Folder. Payment edits run through the server transaction layer, re-sum all payment records, recalculate invoice paid/balance/status values and write an activity entry. Bank-reconciliation-created payments are intentionally protected from direct edits.
- Removed Advance Payment from Record Payment and strengthened the Balance After Payment presentation.
- Set Sales, Purchase and Quotation registers to a consistent inclusive 7-day default range: today minus 6 days through today, including Reset.
- Protected Bank Statement Bulk Actions sizing for icon + label + arrow.
- Renamed Document Studio's final card control to `Actions` and reserved sufficient width to prevent truncation.
- Consolidated JavaFX DatePicker structural styling and improved dark-theme month/year spinner, navigation, day-name, today, selected and adjacent-month states.
- Added explicit close/chevron icon semantics and converted sidebar chevrons to the shared semantic icon system.
- Added Sales Register → Sale Invoice as an in-application PDF-only preview action using the authoritative generated invoice PDF.
- Changed the three Sales Tax Invoice closing sections to 65 / 2 / 33 and aligned the Grand Total label left with the amount at the far right.

## Compatibility
- Application version: 7.3.7
- Database/schema generation remains unchanged.
- Existing Item Code, stock references and persisted document data remain compatible.
- Version 7.3.6 remains in rollback compatibility history.
- Existing Windows/macOS packaging workflows are retained; no packaging architecture was replaced.

## Create Sale Description-column correction
- Fixed a global table-header decoration ordering issue that could treat the first real business column as a legacy blank selection/index column after semantic header text was converted to an icon+label graphic.
- Create Sale now retains its first `Description` column and displays the intended `Remarks • Description` value instead of allowing the global row-number fallback to replace it.
- The correction is centralized in `ProfessionalUiEnhancer`, so other tables with a legitimate first business column are protected from the same issue.
