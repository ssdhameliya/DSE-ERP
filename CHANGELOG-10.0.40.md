# DSE ERP 10.0.40 Release Notes

## Overview
DSE ERP 10.0.40 hardens centralized business branding asset storage and server startup resilience, correcting a PostgreSQL column identifier alignment and eliminating unhandled rollback exceptions during application ready initialization.

## Key Highlights

### 1. Business Asset Storage & Migration Alignment
- **PostgreSQL Column Alignment:** Corrected `server_resource` lookup query from `key` to canonical `resource_key` (`SELECT resource_key FROM server_resource WHERE resource_type='BUSINESS_ASSET' AND resource_key=?`).
- **Zero-Rollback Startup Resilience:** Removed the outer `@Transactional` boundary from `ServerResourceService.migrateLegacyDiskAssets()`, allowing individual asset inspections to safely manage their own transactions without marking application startup as rollback-only.
- **Safe Empty Setting Handling:** Added immediate early exit for blank or unconfigured settings (`if (current.isBlank() || current.startsWith("server-resource:")) continue;`), preventing unnecessary database queries or disk access on unconfigured branding images.

### 2. Multi-Platform Realtime Branding Synchronization
- **Desktop & Web Two-Way Sync:** Preserves authoritative multi-user synchronization for all 5 business branding assets (Application Banner, Application Mark Logo, Company Logo, Signature Seal, and Payment QR Code) between Desktop and Web clients via `/api/authority/resources/BUSINESS_ASSET/{key}`.
