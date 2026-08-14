# DSE ERP 7.2.5

## Purchase Document Studio — Custom PDF Template Designer

DSE ERP 7.2.5 introduces the first production-ready **Purchase-only** Document Studio foundation. Sales PDF generation is intentionally left on the established Sales route and is not intercepted by this feature.

### New Purchase workflow
- Added **Purchase Document Studio** to the shared ERP shell and user menu.
- Users can import an existing Purchase Invoice PDF without modifying the original source file.
- Users can create a blank A4 Purchase Invoice template.
- Template Library supports search, status filtering, preview, edit, duplicate, archive, delete, and default-template selection.
- Templates are stored under the existing workspace `Templates/DocumentStudio` folder so they remain portable with the workspace.

### PDF Designer
- Added a full-width JavaFX PDF designer with PDF page rendering and zoom controls.
- Added draggable overlay objects using PDF-point coordinates rather than screen pixels, keeping output consistent across display scaling and platforms.
- Added Text, Image, Whiteout/Replace Area, Rectangle, Line, ERP Dynamic Field and Dynamic Item Table objects.
- Added object properties for position, size, font size, bold, lock state, text/fill/stroke colours and table-column configuration.
- Added duplicate/delete, Undo/Redo, keyboard shortcuts, optional snap-to-grid and automatic saving.
- Added page navigation for multi-page imported PDFs.

### Purchase ERP data mapping and preview
- Added user-friendly dynamic fields for Company, Purchase, Supplier, Totals and Payment data.
- Added Company Logo and Authorized Signature image fields.
- Added sample-data preview plus optional preview using an existing real Purchase Invoice from the ERP.
- Added test-PDF export from the designer.
- Added a dynamic Purchase line-item table with configurable columns and automatic overflow pagination.

### Purchase PDF / Email / WhatsApp integration
- `InvoicePdfService.purchase(...)` now checks for an active default Purchase Invoice template before using the existing built-in Purchase renderer.
- Existing Purchase Preview, Print, Email and WhatsApp actions continue to use the same `InvoicePdfService` gateway, so an active custom Purchase template is automatically reused.
- If a custom Purchase template is missing, damaged, incomplete or cannot be rendered, DSE ERP logs the problem and safely falls back to the established built-in Purchase PDF renderer.
- The imported source PDF is kept unchanged; Document Studio stores overlay metadata and copied user assets separately.

### Sales regression boundary
- `InvoicePdfService.sales(...)` remains on the existing `SalesTaxInvoiceService.generate(...)` route.
- The 7.2.5 Document Studio code does not resolve, select or render templates for Sales.
- Sales Invoice, Sales Return and Quotation rendering are not migrated to Document Studio in this release.

### Version alignment
- Maven parent/modules, shared runtime contract, Spring backend health contract, desktop resources, runtime manifest and packaging labels are aligned to **7.2.5**.
