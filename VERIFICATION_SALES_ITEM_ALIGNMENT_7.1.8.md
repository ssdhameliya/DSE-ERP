# DSE ERP 7.1.8 — Sales Item Entry Alignment Verification

## Scope
UI-only alignment change in `desktop/src/main/resources/fxml/pages/Sale.fxml`. Version remains 7.1.8. No controller, calculation, PO lifecycle, API, server, database, or persistence logic was changed.

## Implemented layout
The Sales invoice item-entry row now mirrors the editable table columns:

- Item search aligns with **Item Name** (`270`)
- Quantity aligns with **Qty** (`75`)
- Rate aligns with **Rate (₹)** (`105`)
- Discount % aligns with **Discount %** (`92`)
- **Discount (₹)** keeps a blank calculated slot (`112`)
- GST % aligns with **GST %** (`78`)
- Add Item / Remove remain anchored at the far-right side

## Verification evidence
- `Sale.fxml` XML parse: PASS
- `cmbItem` preserved exactly once: PASS
- `txtQuantity` preserved exactly once: PASS
- `txtRate` preserved exactly once: PASS
- `txtLineDiscount` preserved exactly once: PASS
- `txtGST` preserved exactly once: PASS
- `btnAddLine` preserved exactly once: PASS
- `btnRemoveLine` preserved exactly once: PASS
- `#addLine` handler preserved exactly once: PASS
- `#removeLine` handler preserved exactly once: PASS
- Toolbar widths mirror table column preferred widths: PASS
- `audit-desktop-jdbc.py`: PASS
- `audit-phase2-data-boundary.py`: PASS
- `audit-postgres-only.py`: PASS
- `audit-final-data-architecture.py`: PASS

## Build environment limitation
The source requires Java 25. A full Maven/Java 25 compile must still be run on a Java 25 development machine or GitHub Actions before production release.
