# DSE ERP 10.0.41 Release Notes

## Overview
DSE ERP 10.0.41 resolves universal date format parity across generated PDFs, canonical document services, and runtime configuration policies in both Desktop and Web environments.

## Key Highlights

### 1. Unified Company Date Format Engine Across Generated PDFs
- **Global Settings Cache:** Enhanced `ConfigManager` with a persistent concurrent settings map (`GLOBAL_SETTINGS`), ensuring all background document generation threads and PDF generators have immediate access to configured business policies.
- **Server Support Service Startup Sync:** Added `@PostConstruct` initialization in `SupportService` to seed `ConfigManager` from `application_setting` at server launch, and dynamically synchronizes all runtime policy mutations.
- **Format Normalization & Fault Tolerance:** Enhanced `BusinessClock.normalizeDatePattern` to sanitize standard patterns (e.g. `YYYY-MM-DD`, `DD-MM-YYYY`, `DD/MM/YYYY`, `MM/DD/YYYY`, `DD MMM YYYY`) preventing `DateTimeFormatter` pattern errors, and upgraded `formatDate(String)` to parse all standard ISO, delimiter-based, and text date variants.
- **Precedence Correction:** Configured `company.dateFormat` as the primary key before legacy fallback `date.format`, ensuring user-configured company settings are faithfully honored.
- **Professional PDF Renderer Date Parity:** Wrapped all invoice, return, quotation, and purchase date references in `displayDate(...)`, ensuring consistent formatting in headers, details cards, and settlement metadata across both Desktop and Web PDF outputs.

### 2. Runtime Bootstrapper & Document Studio Alignment
- **Canonical Execution Scope:** Wrapped `TaxInvoicePdfGenerator.generate` and `PdfStudioRenderer.render` inside `ConfigManager.withValues(...)` for deterministic runtime configuration binding.
- **Desktop Template Data Factory:** Delegated template display dates directly to `BusinessClock.formatDate(...)` for unified formatting behavior across all Document Studio and professional templates.
