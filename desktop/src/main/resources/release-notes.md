# What's New in DSE ERP v11.0.0

### Pure Decoupled Architecture Rollback
- **High-Performance REST Backend:** Restored clean Spring Boot backend architecture, eliminating embedded webui memory/CPU overhead.
- **Unified Company Date Format:** Company Settings date format selections (such as `DD-MM-YYYY`, `YYYY-MM-DD`, `MM/DD/YYYY`, `DD MMM YYYY`) reliably apply across all generated PDFs (Sales Invoices, Quotations, Return Credit/Debit Notes, and Purchase Inward Bills).
- **Persistent Global Policy Engine:** Background PDF render threads and canonical export workers retain immediate access to authoritative company date formatting policies.
- **Robust Multi-Pattern Date Parsing:** Upgraded date formatters to automatically normalize and format ISO, legacy, and diverse user date string representations without falling back to system defaults.
- **Decoupled Web Client Parity:** Fully aligned with the enterprise HTML5/JS client portal (`v11.0.0.01`).
