# DSE ERP 2.1.6 Phase 2

## Included
- Background loading for dashboard summary database work.
- Reusable loading overlay and failure feedback.
- Navigation and dashboard timing log in the workspace Config folder.
- Shared TableView fixed-cell-size optimization.
- Dashboard quick actions changed to a two-row/four-column layout and protected from first-load icon replacement.
- Compact display sizing for dashboard shortcuts.
- Existing v2.1.5 macOS dialog ownership and shared semantic UI retained.

## Dashboard action-button root cause
The previous TilePane used three columns with eight 94-pixel-high tiles. This required three rows and could exceed the no-scroll dashboard height. In addition, the icons were not marked as preserved, allowing a later enhancement pass to replace or lose them. Phase 2 uses four columns/two rows, explicit visible/managed state, compact vector icons, and preservation markers.
