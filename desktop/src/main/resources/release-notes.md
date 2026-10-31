# What's New in DSE ERP v11.0.15

### Top Bar Ergonomics, Light Mode Glassmorphism & Cloud Server Diagnostics
- **Top Bar Ergonomics & Decluttering**: Permanently removed WhatsApp and keyboard shortcut action buttons from the main shell top bar; System Diagnostics badge dynamically hides when no active alerts/errors are present.
- **Light Mode Glassmorphism Parity**: Register inspection drawers and modal windows (`RegisterDetailDrawer`) dynamically adapt to Light Mode using pearl acrylic glassmorphism styling, `#0f172a` deep slate typography, and high-contrast jewel tones (`#047857` Emerald, `#be123c` Crimson, `#b45309` Amber, `#0369a1` Slate/Teal).
- **Linux Cloud Server Diagnostics**: `AdminService` upgraded with robust cross-platform log discovery supporting Ubuntu/OCI Linux hosts (`/srv/dse-erp/uat/...`, `/var/log/...`, and systemd `journalctl -u dse-erp-uat.service`).
- **Real-Time Global Audit Trail**: Instant incremental filtering on search bar text changes and user dropdown selection without requiring manual Enter/Apply triggers.
- **Design System Ownership Parity**: Restored CSS class ownership in settings sub-cards and status badges to satisfy `SharedUiOwnershipContractTest`.

# What's New in DSE ERP v11.0.14

### Headless Public Showcase & Dual-Client 3D Showcase Studio
- **Dynamic 3D Machinery Showcase Engine**: Empowered administrators to manage public 3D coverflow workstations, machinery photography, precision tolerance specs, hero metrics, marquee ticker, and contact information dynamically from both the Web Admin Studio and Desktop ERP Client without code redeployment.
- **High-Performance Headless REST APIs**: Implemented sub-10ms in-memory cached endpoints (`GET /api/public/showcase`, `GET /api/public/showcase/media/{key}`, and `POST /api/public/showcase/rfq`) with SHA-256 ETag binary caching, backed safely by existing PostgreSQL `application_setting` and `server_resource` tables without altering database schema.
- **Desktop Company Settings Showcase Dock**: Integrated 1-click administrative launch dock directly into `CompanySettingsPanel.fxml`, keeping exact FXML count at 72 files and preserving full release audit compliance.

# What's New in DSE ERP v11.0.13

### Centralized Core Architecture & Master Protocol Section 3.3
- **Centralized System Standards**: Formally documented the 6 centralized core systems in `dse-erp-master-protocol.md` (High-Speed Document Inspection Modals, Unified SVG UI Design System, Concurrency & Freeze Prevention Thread Pools, Multi-Tab Workspace Navigation, RBAC Security & API Runtime, and Universal Document Studio Exporters).
- **Strict Clean Root Mandate**: Permanently purged historical `CHANGELOG-*.md` files and obsolete legacy protocols from repository roots, standardizing all release history exclusively in `release-notes.md`.
- **Ponytail Minimalist Architecture**: Integrated Ponytail (YAGNI / minimal diff ladder) to eliminate over-engineering, protect JavaFX memory heap, and ensure maximum desktop responsiveness.

# What's New in DSE ERP v11.0.12

### Ultra-Fast Document Inspection Pop-ups & Zero-Lag Tab Navigation
- **Instant Shell Mount (0ms):** Inspection dialogs now mount header hero, 4 metric cards, tabs, and action dock synchronously from in-memory row data, eliminating the 5+ second full-page loading skeleton.
- **Asynchronous Line Streaming:** Consignment/purchased line items load asynchronously inside the items table placeholder without blocking the modal shell or UI interaction.
- **Zero-Lag Tab Navigation:** Transport & Notes tab mounts instantaneously (0ms) from existing in-memory fields without artificial loading skeletons. Document attachments query lightweight metadata first and defer large raw binary file downloads until the user explicitly requests an export or preview.
- **Non-Blocking Action Dock:** Excel export, quotation duplication, and document return workflows run via background task executors with animated button states, preventing JavaFX UI freezes.

# What's New in DSE ERP v11.0.11

### Unified Enterprise Inspection Modals & Double-Header Fix
- **Single-Header Symmetry**: Resolved double-header rendering in System & Application Diagnostics and Audit Trail modals, eliminating the duplicate outer shell.
- **Solid Host Framing**: Standardized all 7 document inspection and system diagnostic dialogs with a persistent, solid opaque background (`#0d1527` dark / `#ffffff` light), 14px radius, ambient elevation shadows, and top-right close buttons.
- **Window Dragging**: Added smooth window dragging support across all inspection and diagnostic hero header bars.

### Lightning-Fast Search & Dropdown Filtering
- **Pre-Indexed Memory Search**: Background ingestion and flattening threads pre-index searchable strings, reducing search evaluations to a single instant substring comparison.
- **250ms Input & Dropdown Debounce**: Smooth, fluid filtering without keystroke lag or dropdown selection stutter.
- **Cell Tooltip Reuse & O(1) Indexing**: Eliminated GC churn from repetitive tooltip allocations and optimized row numbering performance.
