# DSE ERP 9.0.98

## Excel Studio mapped-item validation correction

- Fixed Save & Make Default post-render validation so the first line item is validated using the item identity field actually mapped by the workbook.
- `item.remarks` is now a valid first-item validation identity, matching the existing repeating-block rule used by clients that print remarks instead of item descriptions.
- Preserved support for `item.description`, `item.descriptionWithRemarks`, and mapped `item.code` as validation identities.
- Kept the complete repeating item block requirement, real-record preview requirement, unresolved-token checks, document identity checks, and Grand Total validation unchanged.
- Preserved the 9.0.95 repeating-row renderer and the 9.0.97 sidebar, register-colour, and Excel server-synchronization corrections without modification.
