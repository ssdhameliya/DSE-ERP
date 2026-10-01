# Dynamic Calculation & Tax Engine Specification

This document details how PDF Studio dynamically handles **Tax Swapping (CGST/SGST vs IGST)** and **Multiple Charges** even when the imported PDF only had CGST/SGST or 1 charge row.

---

## 1. The Problem
An imported PDF template might have been generated as an intra-state bill, so it visibly shows:
```
Subtotal      : ₹ 10,000.00
CGST (9%)     :    ₹ 900.00
SGST (9%)     :    ₹ 900.00
Grand Total   : ₹ 11,800.00
```
- It has **no printed IGST row**.
- It has **no printed Freight or Insurance charge rows**.
If a real record is an **inter-state transaction with 2 charges**, how does the system render it cleanly without corrupting the layout?

---

## 2. The Solution: Dynamic Flow Block for Calculations

### A. Dynamic Calculation Zone
When the user maps the **Calculation Block**, the system captures:
- **Left Label Anchor:** X-coordinate and font style for labels (e.g., `Helvetica 9pt`).
- **Right Value Anchor:** X-coordinate, alignment (Right), and font style for amounts (e.g., `Helvetica 9pt`).
- **Row Pitch / Height:** The vertical space per row (e.g. `14pt`).

---

### B. Dynamic Tax Swapping (CGST/SGST <--> IGST)

The tax engine evaluates the live ERP transaction state:
```
Transaction GST Type?
├── INTRA-STATE (Same State):
│   ├── Line 1: CGST (e.g. 9%) -> ₹ 900.00
│   └── Line 2: SGST (e.g. 9%) -> ₹ 900.00
│
├── INTER-STATE (Different State):
│   └── Line 1: IGST (e.g. 18%) -> ₹ 1,800.00  (CGST & SGST lines are omitted!)
│
└── EXEMPT / ZERO-TAX / EXPORT:
    └── (Taxes omitted or 0% shown based on company preference)
```

#### How the PDF Background is Handled:
- If the original PDF had static printed text `"CGST"` and `"SGST"`:
  - The engine applies a clean, sampled background mask over that region.
  - For intra-state, it renders both **CGST** and **SGST** in the template's font.
  - For inter-state, it renders **IGST** in that exact position using the template's font and size.
  - **Zero visual artifacts, zero overlapping text!**

---

### C. Dynamic Multiple Charges (0, 1, 2, or More Charges)

In DSE ERP, an invoice can have:
- 0 charges
- 1 charge (e.g. Freight)
- Multiple charges (Freight + Packaging + Insurance + Loading)

#### Dynamic Stack Generation:
At generation time, the engine dynamically builds the financial stack:
```
[1] Subtotal / Item Total
[2] Discount (if > 0, otherwise completely omitted)
[3] Charge 1: Freight (if exists)
[4] Charge 2: Packaging (if exists)
[5] Charge 3: Insurance (if exists)
[6] Taxable Amount
[7] Tax Lines (CGST+SGST OR IGST)
[8] Round-Off (if non-zero)
[9] Grand Total
```

- Each active charge row takes exactly 1 standard row pitch (e.g. `14pt`).
- Grand Total is placed cleanly at the bottom of the stack.
- The font family, size, colors, and decimal formatting stay 100% identical to the template.
