# Universal Block-Wise Popup Mapping Specification

## 1. Unified Block-Click Architecture Across the Entire PDF
Every single visual section in the PDF template is an interactive, clickable block. Clicking **ANY** block opens a popup customized to that block type:

---

## 2. Block Popup Examples Across All Sections

### A. Meta & Dates Block Popup (Click on Header / Details)
When clicking the invoice details block:
```
┌────────────────────────────────────────────────────────┐
│  Map Document Details Block                        [X] │
├────────────────────────────────────────────────────────┤
│ Detected Fields in this Block: 4                       │
│                                                        │
│ Label in PDF       ERP Field Value Mapping             │
│ ────────────────── ─────────────────────────────────── │
│ "Invoice No :"     [ document.number               ▼ ] │
│ "Invoice Date :"   [ document.date                 ▼ ] │
│ "Due Date :"       [ document.dueDate              ▼ ] │
│ "PO Ref No :"      [ document.poNumber             ▼ ] │
│                                                        │
│                                   [ Cancel ]  [ Save ] │
└────────────────────────────────────────────────────────┘
```

---

### B. Logo & Media Block Popup (Click on Logo / QR / Sign)
When clicking on the Logo or QR box:
```
┌────────────────────────────────────────────────────────┐
│  Map Media / Asset Block                           [X] │
├────────────────────────────────────────────────────────┤
│ Block Type: Company Logo                               │
│                                                        │
│ Source Image Asset:                                    │
│ [ company.logoPath (From Settings)                 ▼ ] │
│                                                        │
│ Sizing Policy:                                         │
│ (*) Preserve Aspect Ratio   ( ) Stretch to Fit         │
│ Alignment: [ Center ▼ ]                                │
│                                                        │
│                                   [ Cancel ]  [ Save ] │
└────────────────────────────────────────────────────────┘
```

---

### C. Party / Address Block Popup (Click on Bill To / Ship To / Supplier)
```
┌────────────────────────────────────────────────────────┐
│  Map Party Address Block                           [X] │
├────────────────────────────────────────────────────────┤
│ Target Party Role: [ Buyer / Customer Address      ▼ ] │
│                                                        │
│ Mapped Field:                                          │
│ Field: [ party.address (Single DB Text Field)      ▼ ] │
│                                                        │
│ Multi-Line Wrapping:                                   │
│ (*) Auto-wrap full address into 2 or 3 lines to fit    │
│     the block bounds without changing font or size.    │
│                                                        │
│                                   [ Cancel ]  [ Save ] │
└────────────────────────────────────────────────────────┘
```

---

### D. Item Table Block Popup (Click on Table)
```
┌────────────────────────────────────────────────────────┐
│  Map Item Table Columns                            [X] │
├────────────────────────────────────────────────────────┤
│ Detected Columns: 6                                    │
│                                                        │
│ Col  Header in PDF    ERP Field Mapping      Alignment │
│ ──── ─────────────── ────────────────────── ────────── │
│ [1]  "Sr."           [ item.serial      ▼ ] [ Center ] │
│ [2]  "Item Name"     [ item.name        ▼ ] [ Left   ] │
│ [3]  "HSN"           [ item.hsn         ▼ ] [ Center ] │
│ [4]  "Qty"           [ item.qty         ▼ ] [ Right  ] │
│ [5]  "Rate"          [ item.rate        ▼ ] [ Right  ] │
│ [6]  "Amount"        [ item.amount      ▼ ] [ Right  ] │
│                                                        │
│ Multi-Item: Repeated row-by-row for all items.         │
│                                   [ Cancel ]  [ Save ] │
└────────────────────────────────────────────────────────┘
```

---

### E. Financial Summary & Tax Popup (Click on Totals)
```
┌────────────────────────────────────────────────────────┐
│  Map Financial & Tax Summary                       [X] │
├────────────────────────────────────────────────────────┤
│ [X] Subtotal / Item Total      [ summary.itemTotal   ] │
│ [X] Discount (Omit if 0)       [ summary.discount    ] │
│ [X] Charges (Freight/Delivery) [ summary.charges     ] │
│ [X] GST (Intra: CGST+SGST, Inter: IGST) [ summary.gst] │
│ [X] Round Off                  [ summary.roundOff    ] │
│ [X] Grand Total                [ summary.grandTotal  ] │
│ [X] Amount in Words            [ summary.totalWords  ] │
│                                                        │
│                                   [ Cancel ]  [ Save ] │
└────────────────────────────────────────────────────────┘
```
