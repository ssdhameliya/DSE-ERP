# DSE ERP 7.2.5 — Purchase Document Studio Verification

## Implementation contract
- Purchase Document Studio is reachable from the shared Dashboard shell.
- Document Studio is limited to `PURCHASE_INVOICE` in 7.2.5.
- Imported PDF source is copied into the workspace and never edited in place.
- Template coordinates are stored as PDF points from the top-left, independent of JavaFX zoom and monitor DPI.
- Template metadata is JSON persisted under `Workspace/Templates/DocumentStudio/<template-id>/template.json`.
- User-imported image assets are copied under the template's `assets` folder.
- One active default Purchase template can be selected.
- Purchase Invoice generation resolves the active custom template through `InvoicePdfService.purchase(...)`.
- Custom Purchase-template failures fall back to the established `ProfessionalDocumentRenderer` Purchase Invoice renderer.

## Designer capability included in 7.2.5
- PDF preview, page navigation and zoom.
- Text and dynamic Purchase ERP fields.
- Company logo/signature dynamic images.
- User images.
- Whiteout / replace-area overlays.
- Rectangle and line objects.
- Dynamic item table with configurable columns and overflow pages.
- Object move, dimensions, colours, font size, bold and lock.
- Undo/Redo, duplicate/delete, keyboard shortcuts, snap and autosave.
- Sample preview, real Purchase Invoice preview and test-PDF export.

## Regression boundary
- Sales is intentionally excluded from Document Studio in 7.2.5.
- `InvoicePdfService.sales(...)` is unchanged from the 7.2.4 Sales implementation and still calls `SalesTaxInvoiceService.generate(invoice)`.
- Quotation and Return automatic generation remain on their existing renderers.
- EmailService and WhatsappService were not rewritten; Purchase communication receives the generated Purchase PDF through the existing `InvoicePdfService.purchase(...)` gateway.

## Validation performed in this build workspace
- 7.2.5 version markers are aligned across the parent POM, module POMs, desktop resources, runtime manifest and server application properties.
- FXML resources are checked for XML well-formedness.
- Purchase-only Document Studio source is checked for stale Sales template enum/UI references.
- Sales method body is compared against the 7.2.4 baseline to maintain the requested regression boundary.
- Full Maven compilation could not be executed in this build container because Maven/JDK 25 tooling is not installed here; the project remains configured for Java 25 exactly as the supplied 7.2.4 baseline.


## Compile fix - lambda capture

- Fixed `PdfTemplateRenderer` compilation error: `local variables referenced from a lambda expression must be final or effectively final`.
- The renderer now copies the mutable `sourceIndex` loop counter into `final int currentSourceIndex` before using it in the stream filter.
- Scope remains Purchase Document Studio only. No Sales PDF route was changed by this fix.
