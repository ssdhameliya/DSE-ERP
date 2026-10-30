# What's New in DSE ERP v11.0.11

### Unified Enterprise Inspection Modals & Double-Header Fix
- **Single-Header Symmetry**: Resolved double-header rendering in System & Application Diagnostics and Audit Trail modals, eliminating the duplicate outer shell.
- **Solid Host Framing**: Standardized all 7 document inspection and system diagnostic dialogs with a persistent, solid opaque background (`#0d1527` dark / `#ffffff` light), 14px radius, ambient elevation shadows, and top-right close buttons.
- **Window Dragging**: Added smooth window dragging support across all inspection and diagnostic hero header bars.

### Lightning-Fast Search & Dropdown Filtering
- **Pre-Indexed Memory Search**: Background ingestion and flattening threads pre-index searchable strings, reducing search evaluations to a single instant substring comparison.
- **250ms Input & Dropdown Debounce**: Smooth, fluid filtering without keystroke lag or dropdown selection stutter.
- **Cell Tooltip Reuse & O(1) Indexing**: Eliminated GC churn from repetitive tooltip allocations and optimized row numbering performance.
