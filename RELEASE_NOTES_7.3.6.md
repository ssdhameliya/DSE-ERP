# DSE ERP 7.3.6 — UI Reliability & Consistency Maintenance

## Fixed
- Restored distinct semantic colored table-header icons across the application instead of repeated/default header glyphs.
- Expanded master/lookup icon semantics so BANK, BRAND, GST, MATERIAL, UNIT, DISCOUNT, transport and related categories use meaningful icons.
- Added semantic header icons to the shared Create Sales Return / Create Purchase Return item table.
- Repaired the JavaFX DatePicker popup layout and dark-theme calendar surface so dates remain visible and aligned.
- Standardized item search in Create Sale, Create Purchase and Quotation as a TextField with a colorful search icon and filtered suggestions.
- Aligned Settings header, category strip and all active sub-panels to the same left/right page gutter.
- Made the three Brand & Identity asset cards use equal available width.
- Increased application-footer readability and distributed existing company/contact/time fields across available width.
- Preserved returned-document status handling and existing Sales/Purchase amount, paid and balance semantic colors.
- Added the missing `ContentDisplay` import required by the Dashboard Document Studio control.

## Compatibility
- Application version: 7.3.6
- Database/schema version remains unchanged.
- Version 7.3.5 remains in rollback compatibility history.
