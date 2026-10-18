# DSE ERP 10.0.35 Release Notes

## Overview
DSE ERP 10.0.35 introduces authoritative centralized business asset storage in PostgreSQL (`server_resource`), establishing universal cross-platform synchronization of application branding, invoice headers, digital signatures, and payment QR codes across Desktop workstations and the Web Enterprise portal.

## Key Highlights

### 1. Centralized Business Asset Storage (`server_resource`)
- **Shared Asset Bridge Whitelist Expansion:** Updated `SharedAssetBridge.java` to whitelist all five canonical branding keys:
  - `application.brandImagePath` (Application Top Banner)
  - `application.markImagePath` (Application Mark / Splash Logo)
  - `company.logoPath` (Tax Invoice Header Logo)
  - `company.signaturePath` (Authorized Digital Signature / Seal)
  - `payment.qrImagePath` (UPI Payment QR Code)
- **Automatic Multi-User Publishing:** Selecting or modifying any of the five assets in Desktop Settings automatically uploads the binary content to the authoritative server endpoint `PUT /api/authority/resources/BUSINESS_ASSET/{key}` with SHA-256 integrity verification.
- **Client Cache Resolution:** Desktop clients automatically download and cache server assets locally in `workspace/temp/server-assets/`, providing seamless offline resilience with instantaneous server sync.

### 2. Automatic Legacy Disk Asset Migration
- **Startup Auto-Migration:** Server initialization scans `application_setting` on startup. Any legacy local Windows file paths (`.png` / `D:\...`) are read once, ingested into PostgreSQL `server_resource` under `BUSINESS_ASSET`, and updated to canonical pointers (`server-resource:{key}`).
- **Filesystem Decoupling:** Permanently eliminates dependencies on local workstation drive paths, allowing seamless multi-user client connectivity, Linux/Docker server deployments, and complete preservation of branding assets in standard PostgreSQL database backups.

### 3. REST API & Cross-Platform Web Integration Support
- **MIME & Content-Type Integrity:** Authoritative resource endpoint `/api/authority/resources/BUSINESS_ASSET/{key}` streams image data with exact image MIME types (`image/png`, `image/jpeg`) and custom `X-Resource-Name` / `X-Resource-SHA256` headers.
- **Enterprise Web Portal Alignment:** Prepares the server backend for Web release `web-v10.0.35.01`, ensuring unified RBAC permission matrix synchronizations (`/api/admin/permissions/set`) and full DTO contracts for Data Import Studio loaders.
