# DSE ERP 7.3.8 PDF Refinement Verification

## Acceptance scope

1. Footer prefixes the existing dynamic company address with `Address :`.
2. Billing and Delivery always remain separate PDF cards, including Same as Billing.
3. Signature uses the full 33% card and a 174 × 60 pt maximum envelope without stretching.
4. Signature blank/transparent outer canvas is trimmed in memory only.
5. Sales Register -> Sale Invoice creates a persistent header/footer-free body-only PDF while retaining original body geometry.
6. Sale Invoice opens through the operating system default PDF application, not the Java `PdfPreviewDialog`.
7. Sale Invoice and Print / Download use different filenames so the body-only file cannot overwrite the official PDF.
8. Print / Download PDF and existing sales PDF consumers remain on the full official renderer.
9. Sales PDF/Create Sale display terminology uses `PO No` while the existing `orderNo` model field remains unchanged.
10. Version remains 7.3.8 without a database schema migration.

## Verification completed in this handoff

- All FXML files parse as XML.
- Parent/desktop/server/shared POM files parse as XML.
- `scripts/audit-postgres-only.py`: PASS.
- `scripts/audit-phase2-data-boundary.py`: PASS.
- `scripts/audit-final-data-architecture.py`: PASS.
- Scoped source acceptance checks confirm no `PdfPreviewDialog` call remains in the Sale Invoice action, body-only output is persistent under Documents, official Print / Download remains `InvoicePdfService.sales(...)`, BODY_ONLY still suppresses header/footer, and PO No labels are present.

## Toolchain limitation

This sandbox provides Java 21 and no Maven, while the repository intentionally targets Java 25 and JavaFX 25.0.2. The release target was not weakened. A full `mvn clean verify` must run with the repository's Java 25 CI/local toolchain before production packaging.
