# Universal PDF Studio Engine — Architecture & Rebuild Specification

## 1. Core Architectural Mandate
- **Universal Across All Transaction Types:**
  The engine is not restricted to Sales. It is a **Transaction-Agnostic PDF Studio Engine** serving:
  - `SALES_INVOICE` & `SALES_RETURN` (Credit Notes)
  - `PURCHASE_INVOICE` & `PURCHASE_RETURN` (Debit Notes)
  - `PURCHASE_ORDER`
  - `QUOTATION` & Proforma Invoices
  - `DELIVERY_CHALLAN`
  - `PAYMENT_RECEIPT`
  - `CUSTOM_ERP` documents
- **Zero Impact on Standard Flows:**
  - For every transaction type, existing standard renderers (`SalesTaxInvoiceService`, `ProfessionalDocumentRenderer`, etc.) remain completely untouched.
  - A PDF Studio template only takes effect when a user explicitly marks that template as **Default** for that transaction type. If unset or disabled, the standard flow runs seamlessly.

---

## 2. Universal Block-Wise Detection & Pure Value Replacement
The system parses any PDF template into semantic, non-destructive physical blocks:

### A. Label & Value Pairs (e.g. `INVOICE No : JI/2026-25/001`, `PO No : 4421`, `Date : ...`)
- **Detection:** Automatic extraction of the fixed label (`[Label] :`) and the associated value box (`[Value]`).
- **Fidelity Guarantee:** **Zero changes** to existing font family, font size, text style (bold/italic), text color, alignment, or background.
- **Runtime:** Only the dynamic value bounding box is updated with the real record data. The label stays completely unchanged.

### B. Media & Asset Blocks
- **Logo Block:** Scaled cleanly with aspect ratio preserved within the detected header bounds.
- **QR Code Block:** Dynamically renders transaction-appropriate QR (UPI payment, E-Invoice IRN QR, or verification).
- **Signature Block:** Dedicated authorized signatory block with image rendering and designation.

### C. Universal Multi-Line Text Blocks (Party, Addresses, Transporter)
- **Applicable To:** Bill To, Ship To, Supplier, Buyer, Consignee, Dispatch From, Transporter, Branch Details.
- **Dynamic Line Count Handling:** If an imported template shows 1 sample line but the live record has 3 or 5 lines (Street, Area, City, State, PIN):
  - The engine measures font metrics within the mapped block bounding box.
  - Text wraps gracefully line-by-line without overlapping other blocks or overflowing.
  - **No changes to font, size, style, or background.**

### D. Universal Terms, Conditions & Remarks
- **Single-Shot Mapping:** The user maps the terms block once.
- **Dynamic Expansion:** Whether the transaction has 2 lines today or 6 lines in the future, the block dynamically renders all lines cleanly within the allocated section.

---

## 3. Universal Dynamic Item Table Grid (Any Header Count: 4, 6, 8, 10+ Columns)
- **Independent Column Detection:**
  - Works with any template table design:
    - 4 columns (e.g. Quotation: Item, Description, Qty, Rate)
    - 6 columns (e.g. Simple Bill: Sr, Description, HSN, Qty, Rate, Total)
    - 10+ columns (e.g. Full GST: Sr, Product, HSN, Qty, Unit, Rate, Discount, CGST, SGST, IGST, Amount)
  - Identifies column x-coordinates, widths, and alignments (left/center/right).
- **Dynamic Data Binding:**
  - Table columns bind to corresponding line item fields dynamically.
- **Universal Multi-Page Pagination Specification:**
  - **1. Above-Table Section (Repeated on ALL Pages):**
    - The entire upper identity section—**Company Logo, Company Header, Document Meta (Number, Date, PO, Vehicle), and Party/Address Cards (Bill To, Ship To)**—is **kept and repeated across all pages** (Page 1, Page 2, etc.).
    - Every page maintains complete branding, invoice identity, and customer details.
  - **2. Item Table Flow (No Blank "Whitepage" Gap on Page 1):**
    - When a document requires 2 or more pages, the closing section (totals, bank, terms) does **not** take up space on Page 1.
    - Instead, the Item Table on Page 1 **continues all the way down** the page, filling the available vertical space with item rows.
    - Page 1 **never leaves an empty white void** where totals would normally sit.
    - On Page 2 (and subsequent continuation pages), the table header row repeats directly underneath the repeated header/party section, followed by the remaining item rows.
  - **3. Below-Table Closing Section (Strictly on the LAST Page):**
    - Anything down after the item table—**Dynamic Financial Summary (Taxes, Totals), Bank Account Details, Terms & Conditions, and Authorized Signature / QR Code**—is placed **strictly on the final page**.
    - On non-final pages (Page 1 of 2), this closing block is omitted so the table has maximum room. On the last page, it renders cleanly directly beneath the final item rows.

---

## 4. Universal Dynamic Calculation & Tax Summary Engine
- **Conditional Visibility of Calculation Rows:**
  - **Discounts:** If discount is 0 or not applicable, the discount row and label are suppressed, and subsequent rows pull up cleanly.
  - **Additional Charges:** Dynamically accommodates 0, 1, or multiple charges (Freight, Packaging, Insurance, Rounding) without hardcoded slot limits.
  - **GST Breakdown:**
    - Intra-State: Shows **CGST** and **SGST** rows dynamically.
    - Inter-State: Shows **IGST** row only (CGST and SGST rows are suppressed).
    - Exempt / Non-GST / Overseas: Shows appropriate zero or exempt tax lines.
- **Totals, Round-Off & Amount in Words:**
  - Automatically calculates subtotal, total tax, round-off, net amount, and generates accurate words representation.

---

## 5. Universal Block-Wise Mapping UI Architecture
- **Elimination of Monolithic Popups:**
  - Previous designs used a single monolithic popup listing 50–70 raw fields simultaneously, causing confusion and cognitive overload.
  - The rebuilt PDF Studio introduces a **Modular Block-Wise Mapping Architecture**:
    1. **Block-Wise Hub (`UniversalBlockWorkspace`):**
       - Displays document sections as distinct visual cards: Document Header, Company/Seller, Billing Address, Shipping Address, Transport, Item Table, Financial Totals, Bank Details, and Terms & Signatures.
    2. **Dedicated Block Mapping Dialog (`PdfBlockMappingDialog`):**
       - Opening a block opens a dedicated dialog containing **ONLY** fields and column bindings relevant to that specific block.
       - Users configure and confirm one block cleanly in seconds.
    3. **Toolbar & Canvas Direct Actions:**
       - "Block-Wise Mapping" button directly available on the PDF Studio toolbar.
       - Double-clicking or mapping a block on the canvas opens that specific block dialog directly.

---

## 6. Implementation Status (Fresh Rebuild Completed & Verified)
- **Engine Package:** `org.example.documentstudio.engine`
  - `UniversalLayoutPlanner.java`: Implements standard ERP pagination (Page 1 fills down to footer margin with natural ~18 pt row height; repeating upper stack; closing stack on final page).
  - `UniversalBlockResolver.java`: Resolves template elements into Upper Stack, Item Table, Closing Stack, and Footer Stack.
  - `UniversalPdfEngine.java`: Fresh Apache PDFBox engine with pure value replacement, dynamic item grid, and final-page closing stack.
  - `UniversalBlockDefinition.java`: Specification of standard and detected blocks for block-wise mapping.
- **View Package:** `org.example.documentstudio.view`
  - `PdfBlockMappingDialog.java`: Focused modal dialog for a single block.
  - `UniversalBlockWorkspace.java`: Interactive block cards hub.
- **Verified Tests:**
  - `UniversalPdfEngineTest`: 5 / 5 tests passing (1-page flow, 2-page flow with no Page 1 white gap, block resolution, single & multi-page rendering).
  - `PdfStudioEditorLayoutContractTest`: 12 / 12 tests passing.
  - `PdfStudioBlockMappingUatTest`: 5 / 5 tests passing.
  - `PdfStudioTemplatePackageCompatibilityTest`: 1 / 1 tests passing.
