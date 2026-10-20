# DSE ERP 10.0.36 Release Notes

## Overview
DSE ERP 10.0.36 delivers system-wide date and timestamp standardization adhering strictly to user Company Settings, responsive dialog and preview window sizing, searchable order validation in Barcode & QR dispatch, and essential Quotation editor usability improvements.

## Key Highlights

### 1. System-Wide Date and DateTime Standardization
- **Customer 360 & Supplier 360 Parity:** All date columns (Date, Invoice Date, Follow-up Date, Payment Date) format cleanly per `company.dateFormat`, and timestamp fields (Notes, Uploaded documents) display per `company.timeFormat`.
- **E-Way Bill & E-Invoice Centers:** Table columns for E-way bill generation dates, invoice dates, and credit/debit note dates conform to standard business date settings.
- **Document & PDF Generation Parity:** Tax Invoice PDF generator, Branded Register PDF export, and Unified Reporting engines dynamically pull and apply Company Settings date formatting (`dd/MM/yyyy`, `dd-MM-yyyy`, etc.) rather than hardcoded ISO or English month representations.

### 2. Dialog Sizing & UI Ergonomics
- **Responsive Workspace Dialog Sizing:** Updated `WindowUtilsFx` and `AppDialogRenderer` to adapt dialog widths dynamically up to screen boundaries with `ScrollBarPolicy.AS_NEEDED`.
- **Clipping Fixes:** Resolved truncated chips, timeline nodes, and preview control action buttons in Audit Trail (`ActivityTimelineDialog`), Bank Entry (`BankExpenseController`), and Thermal Label previews (`BarcodeStudioController`).
- **Credit & Debit Note Registers:** Canonical purple table column header and action icons applied to Credit Note and Debit Note registers (`CreditNoteRegister`, `DebitNoteRegister`).

### 3. Quotation & Dispatch Usability Improvements
- **Quotation Customer Dropdown:** Fixed customer dropdown clipping and single-item display on initial click; properly configures visible row counts and triggers popup re-evaluation.
- **Source Selection Cleanliness:** Corrected Quotation source selection to avoid automatically defaulting to "Direct" when no source was picked.
- **Barcode & QR Order Dispatch Validation:** Replaced free-text order number input with a searchable dropdown restricted to active, valid Sales Invoices and Purchase Orders, preventing manual typos and duplicate dispatch records.
