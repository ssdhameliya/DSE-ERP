# DSE ERP 7.3.0 - PDFBox Clip Compile Fix

## Issue
`PdfTemplateRenderer` called `PDPageContentStream.endPath()`, but that method is not part of the public PDFBox `PDPageContentStream` API used by the project.

## Fix
The redundant `endPath()` call after `clip()` was removed. PDFBox `clip()` already emits the clipping operator and terminates the current path internally.

## Scope
- Version remains 7.3.0.
- Change is limited to `desktop/src/main/java/org/example/documentstudio/service/PdfTemplateRenderer.java`.
- No Sales, Purchase, Quotation, Settings, Safe Rollback, database, or FXML behavior was changed by this compile fix.
