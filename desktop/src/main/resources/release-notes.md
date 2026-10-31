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
