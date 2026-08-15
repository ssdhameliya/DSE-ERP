# DSE ERP 7.3.5 — UI Consistency, Returns & Sales Entry Polish

## Fixed
- Prevented top-bar user names from clipping descender characters by giving the shared user MenuButton safe vertical text space.
- Restored explicit Document Studio and Forgot Password icons so theme/skin refreshes cannot leave those controls text-only.
- Unified explicit TableColumn headers on the same colourful semantic badge system used by the global table enhancer, including Release/Safe Rollback and Sales/Purchase Return tables.
- Stabilized Sales and Purchase register financial colours: Amount is red, Paid is green when paid, and open Balance is blue (settled balance remains green).
- Creating a Sales or Purchase Return now changes the original document status to RETURNED; cancelling/deleting the return restores the source document workflow status.
- RETURNED is rendered as a successful return workflow with a return icon rather than as an error state.
- Create Sale item entry is now a TextField-only search experience with a colourful search badge and filtered suggestion menu; item selection still fills rate, GST and discount from Item Master.
- Create Sale PO Date is now a read-only TextField driven by Invoice Date + Payment Terms, preserving the existing calculation and stored PO-date logic.
- Modernized JavaFX DatePicker popups with larger date targets, clearer month navigation, stronger Today/Selected states and consistent light/dark styling.
- Restored Settings outer gutters that had been reset to zero by shared CSS. Company & Billing, Payment & Bank, Invoice & Delivery, Notifications, Email, Workspace and Updates now share the same left/right panel spacing.

## Compatibility
- Database schema generation remains unchanged at schema version 1.
- Existing PostgreSQL data, invoice/return records, PDF/email/WhatsApp workflows and EXE/DMG packaging architecture are preserved.
- DSE ERP 7.3.4 remains a recognized rollback-compatible version.
