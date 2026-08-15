# DSE ERP 7.3.4 — Action Menu, Communication & Logging Reliability

## Fixed

- Corrected the invalid JavaFX CSS font weight (`850`) that generated a parser warning while loading screens.
- Added the desktop Log4j runtime provider required by Apache POI, removing the `Log4j API could not find a logging provider` runtime error.
- Stabilized all table **Actions** menu buttons so legacy compact/icon-only CSS can no longer shrink the text-bearing control.
- Increased shared ContextMenu/MenuItem vertical geometry so action-list labels are not visually clipped at the bottom.
- Restored Communication Center **Re-send** as a full text action with the existing resend/refresh icon plus `Re-send` label.
- Removed stray patch-marker lines from both light and dark theme stylesheets.

## Compatibility

- No database schema change. Schema remains generation 1.
- No business logic, invoice/PDF calculation, permissions, email workflow, reminder logic, PostgreSQL runtime architecture, EXE packaging architecture, or DMG packaging architecture was changed.
- Version synchronized to 7.3.4 across parent/modules, runtime contract, application resources, startup scripts, database metadata, and visible version labels.
