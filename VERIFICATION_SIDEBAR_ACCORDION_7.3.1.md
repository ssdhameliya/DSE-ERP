# DSE ERP 7.3.1 — Compact Accordion Sidebar Verification

## Scope
This refinement changes only the shared ERP shell/sidebar navigation. No business module, database, PDF, calculation, backup, rollback, email or document-studio logic is changed.

## Behavior
- Sales, Purchase and Bank & Expense child menus are collapsed at login.
- Clicking a parent toggles only its submenu; it no longer navigates immediately.
- Only one submenu can be expanded at a time.
- Clicking a child navigates to the existing page and keeps the parent group visibly active.
- Navigation shortcuts and programmatic Bank navigation synchronize the sidebar automatically.
- Hidden submenus use both `visible=false` and `managed=false`, so they consume no sidebar height.
- Sales keeps Quotations accessible when a user has Quotation permission even if Sales access itself is unavailable.
- A compact right-side chevron rotates when a group expands/collapses.

## Files changed from the approved 7.3.1 baseline
- `desktop/src/main/resources/fxml/pages/Dashboard.fxml`
- `desktop/src/main/java/org/example/controller/DashboardController.java`
- `desktop/src/main/resources/css/light-theme.css`
- `desktop/src/main/resources/css/dark-theme.css`

## Static verification performed
- 43/43 FXML files parse as valid XML.
- Dashboard FXML has no duplicate `fx:id` values.
- All Dashboard `onAction` handlers resolve to controller methods.
- All new accordion FXML IDs are backed by controller fields.
- Java source was passed through `javac` parsing in the available environment; only missing JavaFX dependency errors were reported, with no Java syntax/parser errors detected.
- Version metadata remains 7.3.1.

## Required workstation verification
Run with the project's required JDK/Maven environment:

```powershell
mvn clean verify
```

Then smoke-test: login -> all groups collapsed -> Sales expand/collapse -> Purchase auto-collapses Sales -> Bank & Expense -> child navigation -> F2/F3/F6/F7/F8 shortcuts -> role-limited user navigation.
