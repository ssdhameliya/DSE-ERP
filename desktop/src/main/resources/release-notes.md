# What's New in DSE ERP v10.0.41

### Universal PDF Date Format & Configuration Parity
- **Unified Company Date Format:** Company Settings date format selections (such as `DD-MM-YYYY`, `YYYY-MM-DD`, `MM/DD/YYYY`, `DD MMM YYYY`) now reliably apply across all generated PDFs (Sales Invoices, Quotations, Return Credit/Debit Notes, and Purchase Inward Bills).
- **Persistent Global Policy Engine:** Background PDF render threads and canonical export workers now retain immediate access to authoritative company date formatting policies.
- **Robust Multi-Pattern Date Parsing:** Upgraded date formatters to automatically normalize and format ISO, legacy, and diverse user date string representations without falling back to system defaults.
- **Cross-Platform Document Parity:** Aligned Desktop and Web PDF outputs, Document Studio templates, and email transmission previews for consistent visual appearance.
