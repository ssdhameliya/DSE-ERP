# What's New in DSE ERP v10.0.35

### Centralized Cloud & Multi-User Business Assets
- **Authoritative Database Storage:** Application banners, mark logos, invoice logos, digital signatures, and UPI QR codes are now stored directly in the central PostgreSQL database (`server_resource`).
- **Universal Synchronization:** Any branding image updated on Desktop or Web is instantly synchronized across all workstations and users.
- **Legacy File Auto-Migration:** Existing local image paths on disk are automatically imported into the central repository without manual re-uploading.

### Cross-Platform Web Portal Compatibility
- Full DTO schema alignment for Data Import Studio loaders.
- Concurrent permission matrix locking resolution.
- Dynamic report filter grids and cross-platform report favorites synchronization.
