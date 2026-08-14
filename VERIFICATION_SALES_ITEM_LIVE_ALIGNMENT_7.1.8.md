# DSE ERP 7.1.8 - Sales Item Entry Live Alignment Verification

Scope: Sales invoice item entry row only.

## Implemented behavior
- Search Item control follows the live Item Name table-column width.
- Qty follows the live Qty table-column width.
- Rate follows the live Rate table-column width.
- Discount % follows the live Discount % table-column width.
- The calculated Discount (₹) column keeps a matching reserved slot.
- GST % follows the live GST % table-column width.
- Add Item / Remove occupy the final Taxable / GST Amount / Amount area and stay right-aligned.
- The entry toolbar horizontal padding is neutralized so its left edge matches the TableView edge.
- No calculation, PO lifecycle, REST, database, or server behavior was changed.

## Root cause corrected
The TableView uses CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN. JavaFX therefore changes actual TableColumn widths at runtime. The prior toolbar used fixed FXML widths and drifted out of alignment. The new GridPane ColumnConstraints are bound to each TableColumn.widthProperty(), so the entry row tracks the real runtime widths.

## Verification
- Sale.fxml parses as valid XML: PASS
- 9 entry ColumnConstraints map to 9 TableColumns: PASS
- Existing fx:id controls preserved: PASS
- addLine/removeLine handlers preserved: PASS
- Project version remains 7.1.8: PASS
- audit-desktop-jdbc.py: PASS
- audit-phase2-data-boundary.py: PASS
- audit-postgres-only.py: PASS
- audit-final-data-architecture.py: PASS

Full Java 25 Maven compilation remains to be run locally/GitHub Actions because Maven/JDK 25 are not available in the execution environment.
