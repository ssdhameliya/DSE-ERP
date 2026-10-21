# What's New in DSE ERP v10.0.40

### Server Resource Startup Resilience & Business Branding Synchronization
- **Server Startup Resilience:** Hardened application startup initialization so that missing, blank, or partially configured branding images never trigger unexpected transaction rollbacks or service restarts.
- **Authoritative Database Alignment:** Aligned business resource queries with canonical PostgreSQL `resource_key` schema across all 5 company branding assets.
- **Cross-Platform Branding Sync:** Seamless two-way synchronization for Application Banner, Application Mark, Company Logo, Signature Seal, and Payment QR Code between Web and Desktop clients via centralized server storage.
