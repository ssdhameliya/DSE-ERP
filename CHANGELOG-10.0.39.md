# DSE ERP 10.0.39 Release Notes

## Overview
DSE ERP 10.0.39 introduces universal canonical document rendering parity across the backend server endpoint `/api/documents/render`, extending full PDF and Excel rendering support to Quotation, Sales Return, Purchase Return, Credit Note, and Debit Note with 2-way generation workflow.

## Key Highlights

### 1. Canonical Document Service Multi-Type Support
- **Universal Document Rendering:** Extended `CanonicalDocumentService` to handle `QUOTATION`, `SALES_RETURN`, `PURCHASE_RETURN`, `CREDIT_NOTE`, and `DEBIT_NOTE` alongside Sales and Purchase.
- **2-Way Rendering Pipeline:** Preserved full support for custom default templates via PDF/Excel Studio (`TemplateStorageService.defaultFor(type)` / `/api/pdf-studio/templates`) while guaranteeing seamless fallback to standard built-in rendering (`ProfessionalDocumentRenderer` and `ExcelTemplateRenderer.renderBuiltIn`).
- **Data Factory Expansion:** Added Quotation and Return mapping in `CanonicalDocumentDataFactory` for both `TemplateData` and `ProfessionalDocumentRenderer.Data`.

### 2. Desktop-Web Visual & Structural Parity
- **Excel Output Parity:** Web export now renders the exact byte-for-byte identical formatted Excel workbook produced by Desktop (company header, dark-blue `#193a74` table headers, tax breakdown, amount in words, and authorized signatory).
- **Branded PDF Generation:** Enables Web users to preview, download, and email authentic high-fidelity PDFs generated directly by the server.
