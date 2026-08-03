# DSE ERP 2.1.9 — All-Screens Approved UI Release

## Scope

This release applies the approved visual system directly to all 36 FXML screens while retaining the existing controllers, business logic, navigation, database behavior, semantic icons, owned dialogs, macOS sizing, and performance framework.

## Application-wide implementation

Every FXML root now includes:

- `approved-ui`
- `approved-screen`
- a screen-specific class such as `approved-screen-sales-list`

Controls are explicitly classified in FXML and reinforced by `ApprovedUiSystem`:

- tables and lists
- search, date, choice, combo and text inputs
- primary, secondary, danger and icon-only buttons
- table action menu buttons
- filter bars and toolbars
- KPI, card, panel and section regions
- page titles, subtitles and status badges

## Theme coverage

Both `light-theme.css` and `dark-theme.css` contain the complete approved component rules. The same semantic structure is used in both themes; only presentation colors differ.

## macOS and display protection

The all-screen installer is idempotent and does not call `applyCss()` or `layout()`. Existing macOS navigation, Retina/HiDPI, monitor sizing, owned-dialog, toast and performance behavior remains intact. Large shadows are disabled on macOS surfaces.

## Dashboard

The chart-free executive Dashboard remains the only screen whose content structure was fully replaced in the previous approved-UI phase. The remaining screens retain their current functional section structure while now using the same approved design components throughout.

## Verification

- 36 FXML files parsed successfully.
- Every FXML root contains the approved UI and screen classes.
- 142 Java source files were indexed for internal import resolution.
- The only apparent nested-class import is the valid `GlobalSearchService.SearchResult` import.
- `pom.xml` and `app-version.properties` both report 2.1.9.
- Archive integrity was checked after packaging.

## Runtime verification required

Run `mvn clean install` and `mvn javafx:run` from the local repository, then review all screens in Light and Dark modes on Windows and macOS before tagging the release.
