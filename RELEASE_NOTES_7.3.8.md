# DSE ERP 7.3.8 Release Notes

## PDF refinement release

DSE ERP 7.3.8 is a focused Sales Tax Invoice PDF refinement built from the live 7.3.7 corrected baseline, with the Sale Invoice delivery-flow correction included in the same 7.3.8 version.

### Changes

- Official Sales Tax Invoice footer shows `Address : <configured company address>` while retaining the existing dynamic Settings value and footer geometry.
- Billing Address and Delivery Address are always rendered as separate 49 / 2 / 49 cards, even when Same as Billing produces identical values.
- Built-in Sales Tax Invoice signature rendering uses the complete 33% signature card, with a 174 × 60 pt fitting envelope and preserved aspect ratio.
- Transparent/near-white outer canvas around the configured signature is trimmed in memory for PDF rendering only. The original uploaded signature file is never modified.
- Settings recommends a 750 × 200 px or larger transparent PNG for a large, crisp PDF signature.
- Sales Register -> Sale Invoice now behaves like Print / Download PDF: it creates a persistent PDF in the DSE ERP Documents folder and opens it with the operating system's default PDF application. It no longer opens `PdfPreviewDialog`.
- The Sale Invoice file is the body-only variant: company header/footer are omitted while their layout space remains reserved, so TAX INVOICE through Terms/Signature stays at the same coordinates as the official PDF.
- Body-only Sale Invoice uses its own `Sale-Invoice-<invoice>.pdf` filename and therefore cannot overwrite the full official `Sales-Tax-Invoice-<invoice>.pdf`.
- Sales PDF terminology is standardized from Order No / PO Order No to **PO No**; the underlying `orderNo` data contract is unchanged.
- Sales Register -> Print / Download PDF continues to generate the full official PDF with company header/footer and remains unchanged for existing export/share workflows.

### Compatibility

- Version remains 7.3.8.
- Database schema generation remains unchanged.
- No customer, sales, GST, payment, item, email or WhatsApp persistence contract was changed.
- Existing Java 25 / JavaFX 25.0.2 runtime target and packaging architecture are retained.
