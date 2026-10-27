# Changelog — DSE ERP v11.0.7

## Release 11.0.7 — Responsive 88% Screen Pop-up Inspection Modals (Sales, Purchase, Returns, Quotation) & Header Floor Protection

- **Release Date:** 2026-10-27
- **Baseline:** Git tag `v11.0.6`
- **Type:** UI Responsiveness, Geometry Expansion & Table Inspection Release

---

## Key Features & Improvements

### 1. Responsive 88% Visual Bounds Document Inspection Pop-ups
- **Problem:** Document inspection dialogs (Purchase, Sales, Quotation, Returns) were hardcoded to a fixed `1080 × 740 px`, occupying only ~56% of a 1080p display. This created excessive blank margins and squeezed dense 11-column line-item tables.
- **Solution:**
  - Added centralized `PopupTableWorkspace.applyInspectionSizing(dialog, ownerNode)`.
  - Automatically queries the active display's visual bounds (excluding taskbars) and dynamically calculates `targetWidth = bounds.getWidth() * 0.88` and `targetHeight = bounds.getHeight() * 0.88`.
  - On 1920×1080 displays, inspection pop-ups now cleanly expand to `~1680 × 920 px`.
  - Applied across:
    - `PurchaseDocumentInspectionDialog.java`
    - `SalesDocumentInspectionDialog.java`
    - `ReturnDocumentInspectionDialog.java` (Sales Return & Purchase Return)
    - `QuotationDocumentInspectionDialog.java`
    - `ReturnEditorService.java`

### 2. Table Column Header Minimum & Dense Floor Protection
- **Problem:** In constrained viewports, column headers such as "Quantity" and "Discount" were compressed below readable thresholds, causing them to clip to "Quantit" and "Discou".
- **Solution:**
  - Updated `DynamicTableLayoutManager.java`:
    - `readableMinimum`: increased to `82.0px` for Quantity and Discount.
    - `denseFloor`: increased to `76.0px` for Quantity and Discount.
  - Table headers never truncate even when compressed.

### 4. Universal Centered Modal Dialogs (Zero Squeezed Side Drawers)
- Replaced legacy inline docked side drawers across all registers and masters (Bank Entry, Expense Entry, Purchase Recon, Recon Supplier, User Access, Items Master, Inventory, Customers, Suppliers, Master Data) with a canonical centered modal inspection popup (`RegisterDetailDrawer`), ensuring registers retain 100% viewport width without horizontal compression.

### 5. Dialog Visual Polish & Inspection Stability
- **Dialog White Bottom Strip Flash Fix:** Synchronous bounds sizing and transparent fill in `AppDialogRenderer` preventing transient white window render flushes.
- **Credit/Debit Note Inspection:** Restored themed dark background (`#0b1325`), synchronous data initialization, and owned dialog hierarchy.
- **Customer/Supplier 360:** Fixed label truncation on phone and email attributes with explicit column constraints.
- **Items Master:** Added Reset button with complete filter and table reset capability.
- **Purchase & Sales Registers:** Reorganized export/print actions to the primary filter toolbar and embedded financial summaries in the pagination footer.
- **Purchase Register Print Action:** Properly bound `@FXML private void printRegister()` handler.

---

## Release Gate Status
All 33 release gates pass — `RELEASE_GATES_CURRENT_OK version=11.0.7`
