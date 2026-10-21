# What's New in DSE ERP v10.0.39

### Canonical Document Rendering Parity & Universal Multi-Document Output
- **Universal Backend Canonical Rendering:** Extended `/api/documents/render` to natively process `QUOTATION`, `SALES_RETURN`, `PURCHASE_RETURN`, `CREDIT_NOTE`, and `DEBIT_NOTE` across both PDF and Excel formats.
- **2-Way Rendering Parity:** Complete support for both PDF/Excel Studio custom active templates and standard built-in engines (`ProfessionalDocumentRenderer` & `ExcelTemplateRenderer`) across all document categories.
- **Enterprise Web & Desktop Parity:** Web clients can now stream server-authoritative, byte-for-byte identical PDF and Excel documents with complete company branding, dark-blue `#193a74` table headers, tax breakdown, amount in words, and authorized signatory blocks.
- **Audit & Email Dispatch Support:** Return and Quotation dispatch services now bundle high-fidelity server-rendered PDF attachments for seamless email and WhatsApp transmission.
