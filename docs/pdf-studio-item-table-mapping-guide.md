# How Users Map the Item Table in PDF Studio

This document explains the exact step-by-step user workflow for mapping any Item Table in PDF Studio, whether it has 4, 6, 10, or more columns.

---

## 1. Automatic Column Detection (Zero-Effort on Import)
When a user imports an existing PDF:
1. **Grid & Header Detection:** The engine automatically detects the physical table boundary, the header row, and the vertical column separator lines.
2. **Header Text Recognition:** The engine reads the exact text printed in each header cell (e.g. `"Sr."`, `"Item Description"`, `"HSN/SAC"`, `"Qty"`, `"Rate"`, `"Amount"`).
3. **Auto-Binding:** The engine automatically pairs each column with the most probable ERP line item field based on semantic matching.

---

## 2. Visual Table Mapping in the UI
When the user clicks the Item Table on the PDF Studio canvas:
- The **Right Inspector Panel** displays the table's detected columns in a clear, interactive list:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Item Table Columns (Detected: 6 Columns)                              │
├─────┬──────────────────────┬─────────────────────────┬────────┬───────┤
│ Col │ Template Header Text │ ERP Line Item Field     │ Align  │ Width │
├─────┼──────────────────────┼─────────────────────────┼────────┼───────┤
│  1  │ Sr No                │ [ item.serial       ▼ ] │ Center │  28pt │
│  2  │ Description of Goods │ [ item.description  ▼ ] │ Left   │ 210pt │
│  3  │ HSN/SAC              │ [ item.hsn          ▼ ] │ Center │  55pt │
│  4  │ Quantity             │ [ item.qty_with_unit▼ ] │ Right  │  45pt │
│  5  │ Unit Price           │ [ item.rate         ▼ ] │ Right  │  65pt │
│  6  │ Total Amount         │ [ item.amount       ▼ ] │ Right  │  85pt │
└─────┴──────────────────────┴─────────────────────────┴────────┴───────┘
```

---

## 3. How the User Adjusts or Customizes Mapping

### A. Changing a Column's Bound Field
- If the user wants Column 2 to display both **Item Name and Description**, they simply click the dropdown for Column 2 and pick:
  `item.name_and_description`
- If a column is a custom field (e.g., `Batch No`, `Expiry`, `Part No`, `Discount %`), they pick that field from the dropdown.

### B. Adjusting Column Alignment
- Beside each column is an alignment toggle: `[Left] [Center] [Right]`.
- Numeric columns (Qty, Rate, Amount) default to `Right`.
- Identifiers (Sr, HSN, Unit) default to `Center`.
- Descriptions default to `Left`.

### C. Formatting & Compound Fields
- Users can choose compound fields directly from the catalog:
  - `item.qty_with_unit` (e.g. `"10 PCS"`) vs `item.qty` (`"10"`)
  - `item.rate_with_currency` (e.g. `"₹ 250.00"`) vs `item.rate` (`"250.00"`)
  - `item.name_and_description` vs `item.name` only

---

## 4. Why Original Styling Stays 100% Intact
1. **Original Headers & Lines Untouched:**
   The template's printed table header, borders, vertical lines, and background shading come straight from the imported PDF. The system does not redraw the header.
2. **Data Alignment to Physical Columns:**
   At runtime, line item data is rendered strictly inside each column's detected horizontal slice:
   - Row 1: Item 1 values at `(Col 1 X, Col 2 X, ... Col N X)`
   - Row 2: Item 2 values...
3. **No Overlap:** Text in each column is clipped or wrapped within that column's exact width (`width`), guaranteeing zero column-to-column overlap.

---

## 5. Summary of User Actions
To map an item table, the user only has to:
1. Click the table on the canvas.
2. Verify the pre-filled dropdown for each column in the Inspector.
3. If any column needs changing, pick the desired ERP field from the dropdown.
4. Click **"Save"** or **"Save as Default"**.
