# Block-Wise Mapping Specification & Concrete Examples

This document demonstrates exactly how the universal PDF Studio engine detects, maps, and renders each block type without altering existing styling.

---

## Example 1: Label-Value Pair Block (e.g. `INVOICE No : JI/2026-25/001`)

### 1. Template Detection (Source Scan)
When a PDF is imported, the engine extracts text tokens and coordinates:
- **Label Token:** `"INVOICE No :"` at `(X: 420, Y: 120, W: 65, H: 12)`, Font: `Helvetica-Bold 9pt`, Color: `#1E293B`
- **Sample Value Token:** `"JI/2026-25/001"` at `(X: 490, Y: 120, W: 85, H: 12)`, Font: `Helvetica 9pt`, Color: `#334155`

### 2. Block Mapping Rule
- The static label (`INVOICE No :`) is **never touched**.
- The dynamic value slot is registered as a **Value Box**:
  - Target Field: `document.number` (e.g., Invoice No, PO No, Quotation No, Challan No)
  - Coordinate: `(490, 120, 85, 12)`
  - Style Inherited: `Font: Helvetica, Size: 9pt, Color: #334155, Align: LEFT`

### 3. Runtime Rendering
- When generating a **Sales Invoice**: fills with `"INV-2026-089"`
- When generating a **Purchase Order**: fills with `"PO-4412"`
- When generating a **Quotation**: fills with `"QT-901"`
- **Result:** Pure value replacement in the exact font, size, and position, leaving the label and background pristine.

---

## Example 2: Multi-Line Party / Address Block (e.g. Bill To, Ship To, Supplier)

### 1. Template Detection
- The template has a box labeled `"Bill To :"` with a sample 1-line address:
  - Bounding Box: `(X: 40, Y: 180, Width: 240, Height: 65)`
  - Font: `Helvetica 8.5pt`, Leading: `11pt`, Color: `#0F172A`

### 2. Block Mapping Rule
- The entire bounding box `(40, 180, 240, 65)` is mapped to **`party.address`** (which is a single string in the database).

### 3. Runtime Rendering with Real Single Address Data
At runtime, the live ERP record contains a single text field:
`party.address = "Plot No. 42, GIDC Estate Phase 2, Near Ring Road, Ahmedabad, Gujarat - 382445"`

- The engine measures the text using the template's exact font (`8.5pt Helvetica`).
- Because the text is longer than one line, the engine automatically splits and wraps it into **2 or 3 neat lines**:
  ```
  Line 1: Plot No. 42, GIDC Estate Phase 2,
  Line 2: Near Ring Road, Ahmedabad,
  Line 3: Gujarat - 382445
  ```
- All lines render inside the `(40, 180, 240, 65)` block with exact line spacing.
- **Result:** Pure dynamic text flow from your single database field into 2-3 lines, with **zero changes to font, size, style, or background**!

---

## Example 3: Dynamic Item Table Grid (6 vs 10 Columns)

### Scenario A: 6-Column Template (e.g. Simple Tax Invoice / Purchase)
- **Detected Header Coordinates:**
  `[Sr: 28pt] | [Description: 210pt] | [HSN: 55pt] | [Qty: 45pt] | [Rate: 65pt] | [Amount: 85pt]`
- **Mapping:**
  - Col 1 -> `item.sr` (Align: Center)
  - Col 2 -> `item.name_and_description` (Align: Left)
  - Col 3 -> `item.hsn` (Align: Center)
  - Col 4 -> `item.qty` + `item.unit` (Align: Right)
  - Col 5 -> `item.rate` (Align: Right)
  - Col 6 -> `item.amount` (Align: Right)

### Scenario B: 10-Column Template (e.g. Detailed Multi-Tax GST Invoice)
- **Detected Header Coordinates:**
  `[#] | [Item Name] | [HSN] | [Qty] | [Unit] | [Price] | [Disc%] | [CGST] | [SGST] | [Total]`
- **Mapping:**
  - Each column binds independently to its specific field.
- **Fidelity Guarantee:**
  - The existing column lines, widths, and header styles from the uploaded PDF remain untouched.
  - The engine feeds the item data directly into the detected column coordinates.

---

## Example 4: Multi-Page Pagination Flow (No Empty Gaps)

Suppose an invoice has **25 line items**:
- **Page 1 Budget:**
  - Header & Address take `240pt`.
  - Item Table has `500pt` available height.
  - Exactly **14 items** fit comfortably with standard row height.
  - Page 1 prints items 1 to 14. **No blank gaps left.**
- **Page 2 Budget:**
  - Repeated header takes `120pt`.
  - Remaining **11 items** take `240pt`.
  - Closing Stack (Summary totals, GST breakdown, Bank details, Terms, Signatures) takes `260pt`.
  - All fits cleanly on Page 2!
- If an invoice has 60 items, it flows across Pages 1, 2, and lands the closing summary strictly on Page 3.

---

## Example 5: Dynamic Calculation & Tax Rows

### Case 1: Intra-State Transaction WITH Discount & Freight
```
Item Total            :  ₹ 10,000.00
Discount (5%)         :   - ₹ 500.00
Freight Charges       :   + ₹ 200.00
Taxable Value         :   ₹ 9,700.00
CGST (9%)             :     ₹ 873.00
SGST (9%)             :     ₹ 873.00
Round Off             :     - ₹ 0.40
─────────────────────────────────────
Grand Total           :  ₹ 11,446.00
Amount in Words       :  Eleven Thousand Four Hundred Forty-Six Only
```

### Case 2: Inter-State Transaction WITHOUT Discount
```
Item Total            :  ₹ 10,000.00
Freight Charges       :   + ₹ 200.00
Taxable Value         :  ₹ 10,200.00
IGST (18%)            :   ₹ 1,836.00
─────────────────────────────────────
Grand Total           :  ₹ 12,036.00
Amount in Words       :  Twelve Thousand Thirty-Six Only
```
- **Dynamic Rule:**
  - When discount is zero, the discount row is completely omitted, and Freight pulls up seamlessly.
  - Inter-state automatically hides CGST/SGST and shows only IGST.
  - No empty whitespace gaps or broken lines in the financial card.
