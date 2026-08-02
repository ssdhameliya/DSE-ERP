# DSE ERP 2.1.3 — Shared Semantic UI Rebuild

## Root cause
Sales, Purchase and other modules mixed controller-specific cell factories with a global enhancer. The same business state (for example success) was allowed to choose the icon, which erased the column meaning. Button icons were also assigned with different helpers and sizes.

## Correction
- Added `SemanticTableCells`, the single renderer for semantic status and due-date cells.
- The column role chooses the icon; the value chooses only the state colour.
- Added `UiActionIcons`, the single explicit button-icon assignment utility.
- Sales and Purchase now use the same renderer and toolbar setup.
- Excel, PDF and Print retain distinct semantics.
- Explicit graphics are preserved by the existing enhancer and survive first load/theme changes.

## Compatibility
No navigation cache, window sizing, maximize/restore, dialog ownership, toast positioning or macOS responsive code was changed.

## Verification
- 36 FXML files parsed successfully.
- Source references to the new shared utilities were checked.
- ZIP integrity was checked after packaging.
- A dependency-backed Maven compile still needs to be run in IntelliJ/CI because Maven dependencies are unavailable in this container.
