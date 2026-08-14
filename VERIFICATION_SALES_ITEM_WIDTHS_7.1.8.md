# DSE ERP 7.1.8 - Sales Item Width Verification

Scope: Sales invoice item-entry/table visual sizing only.

Verified changes:
- Item Name table column preferred width increased from 270 to 320.
- Search Item entry column preferred width increased from 270 to 320.
- Discount % preferred width set to 105.
- Discount (₹) preferred width set to 105.
- Discount entry and calculated discount columns are therefore equal width.
- Runtime toolbar bindings remain active, so entry controls follow actual live TableColumn widths.
- No calculation, PO lifecycle, API, server, database, or persistence logic changed.
