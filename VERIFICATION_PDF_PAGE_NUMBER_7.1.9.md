# DSE ERP 7.1.9 — Multi-page Footer / Final-page Verification

## Scope
This pass changes only multi-page PDF pagination/footer presentation.

## Acceptance checks
- PASS: Single-page invoice does not render a page indicator (`totalPages <= 1`).
- PASS: Multi-page invoice renders `Page X of Y` for every physical page.
- PASS: Page indicator is overlaid in the existing bottom-right footer bar.
- PASS: Existing footer address remains center-aligned and its geometry is unchanged.
- PASS: Full company header, invoice meta, billing/delivery and transporter repeat on continuation pages.
- PASS: Non-final page logical item limit remains 20, bounded by measured physical fit.
- PASS: Non-final pages do not introduce filler rows.
- PASS: Final-page item capacity remains measured from the live top area down to Bank/Calculation with the standard 5pt gap.
- PASS: Only the final page may use blank filler rows before the closing financial stack.

## Repository checks
- PASS: All four architecture guards.
- PASS: Parent, desktop, server and shared Maven POM XML parse.
- PASS: ZIP archive integrity after packaging.

## Environment limitation
The execution environment provides JDK 21 and no Maven command. The project targets Java 25, so the final `mvn clean verify` and visual PDF generation remain a local/GitHub Actions release gate.
