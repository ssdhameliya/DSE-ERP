# DSE ERP 2.1.3 — macOS deep UI/performance audit

## Scope inspected
- 138 Java source files
- 36 FXML screens
- 2 global theme stylesheets
- primary-stage startup, saved/maximized bounds, navigation, dialogs, toasts and updater dialogs

## Corrections in this build
1. Primary-window restore no longer saves maximized/full-screen bounds as ordinary bounds.
2. Restored bounds are clamped to the current monitor visual area (menu bar and Dock excluded).
3. Maximization is applied after the stage is shown, which is more reliable on macOS.
4. Forced `toFront/requestFocus` on every startup was removed.
5. Responsive classes are installed for every scene loaded through SceneManager.
6. Navigation now uses access-ordered LRU caching (8 screens maximum).
7. Transaction/edit/setup/import/settings screens are deliberately not cached.
8. Re-selecting the current page no longer triggers a redundant refresh.
9. Remaining manually-created customer, supplier and user stages use the shared owned-modal configuration.

## Important architectural finding
JavaFX `Stage` and `Dialog` are native secondary windows. Ownership/modality improves focus and placement but cannot make them true children of the main scene. A complete single-window experience requires migrating forms to a StackPane overlay host. That is a larger controller/FXML lifecycle change and should be implemented as a dedicated feature after this stabilization build.

## Static layout finding
The FXML set contains many fixed preferred dimensions. They are not all defects: table columns and form controls often need fixed hints. Blindly removing them would damage Windows layouts. Responsive correction therefore remains centralized at shell/window level in this build; individual screens should be visually tested at 1200x700, 1440x900 and a Retina maximized window before changing their form grids.
