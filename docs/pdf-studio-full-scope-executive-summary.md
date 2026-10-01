# PDF Studio Rebuild — Full Scope Executive Summary

This document summarizes our complete agreed scope and design before any code changes begin.

---

## 1. Safety & Independence (Standard Flow Untouched)
- **100% Preserved:** Existing standard flow generators (`SalesTaxInvoiceService`, `TaxInvoicePdfGenerator`, `ProfessionalDocumentRenderer`) remain completely untouched.
- **Opt-In Only:** A PDF Studio template is only used when the user explicitly marks it as **Default** for that transaction type. Otherwise, the standard flow continues running as normal.

---

## 2. Universal Transaction Support
- The engine is completely transaction-agnostic and supports:
  - **Sales**: Invoices, Sales Returns / Credit Notes
  - **Purchase**: Invoices, Purchase Orders, Debit Notes
  - **Commercial**: Quotations, Delivery Challans, Payment Receipts, Custom ERP Docs

---

## 3. Block-Wise Click-to-Map UI (Popups Across All Blocks)
- Every section on the PDF canvas is an interactive block. Clicking a block opens a dedicated modal dialog:
  1. **Document Meta Popup:** Map Invoice/PO/Quotation No, Dates, Due Dates, Reference Nos.
  2. **Media Block Popup:** Bind Company Logo, Payment/E-Invoice QR, or Signature.
  3. **Party & Address Popup:** Bind Party Name, GSTIN, and Address.
  4. **Item Table Popup:** Automatically detects all N columns (6, 8, 10) and lets the user map each column to line item fields in one place.
  5. **Calculations & Totals Popup:** Map Subtotal, Discount, Charges, Taxes, Words.
  6. **Terms & Remarks Popup:** Map multi-line terms and conditions.

---

## 4. Source Cleaning & Pure Value Replacement (Zero Ghosting)
- **Complete Suppression:** All old sample/dummy text from the imported PDF is completely cleaned/erased for mapped regions.
- **Zero Ghosting:** Old text will never bleed through or show behind new values.
- **Exact Style Preservation:** New values are rendered in the **exact font family, size, weight, color, alignment, and background** of the original template.

---

## 5. Single Database Address Field with Dynamic 2–3 Line Auto-Wrap & Zero Overlap
- The database has a single text field (`party.address`).
- At runtime, the engine automatically measures and wraps this single string into **2 or 3 neat lines** to fit the block boundary without altering font properties.
- **Strict Non-Overlap Guarantee:** Lower fields located below the address (such as `Mobile No:`, `GSTIN: 24BEEPD490PN`, `State Code: 24`, etc.) are 100% protected. Address wrapping will **never collide with or overlap** these fields, and their font, size, style, and positioning remain completely intact.

---

## 6. Dynamic Item Table & Multi-Page Flow
- **Any Column Count:** Handles 4, 6, 8, 10, or more columns dynamically without modifying template headers or grid lines.
- **Map Once, Render Unlimited:** The table layout is mapped once in the popup; runtime automatically repeats rows for 1, 10, 25, or 50 items.
- **Smart Multi-Page Budgeting:** Aligned with standard sales logic—rows fill Page 1 and Page 2 without artificial empty gaps, and closing totals land cleanly on the final page (Page 2 or 3).

---

## 7. Dynamic Financial Calculations & Tax Swapping
- **Intra-State vs Inter-State:** Dynamically prints **CGST + SGST** for intra-state and **IGST** for inter-state (masking the unused tax lines even if IGST wasn't in the original imported PDF).
- **Conditional Rows:** If discount is zero, the discount line is omitted and subsequent rows pull up cleanly.
- **Multiple Charges:** Dynamically supports 0, 1, or multiple charges (Freight, Packaging, Insurance) row-by-row.
