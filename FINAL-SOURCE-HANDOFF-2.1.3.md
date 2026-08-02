# DSE ERP 2.1.3 — Final Complete GitHub Source

This directory is the cumulative GitHub-ready source handoff for DSE ERP Professional 2.1.3.

It includes the complete project rather than a partial changed-file patch:

- `pom.xml` and Maven configuration
- `README.md`
- `.github/workflows`
- Java source
- FXML layouts
- CSS/themes
- resources
- Windows and macOS packaging scripts
- cross-platform display/window stabilization
- shared semantic UI utilities
- Sales/Purchase semantic status rendering
- Quotation filters and final Quotation Edit table-header icon correction
- supporting audit and verification notes

## Recommended workflow

1. Extract this ZIP into a new folder.
2. Open the extracted folder in IntelliJ IDEA.
3. Run Maven reload and compile.
4. Run and verify on Windows and macOS.
5. Copy/replace this complete source into the local GitHub repository or use it as the repository working tree.
6. Commit to a feature branch, merge to `develop`, perform the daily verification build, then merge to `main`.

## Version

The Maven project version remains `2.1.3`, matching the current GitHub baseline requested by the user.

## Important

This package supersedes the earlier individual GitHub patch ZIPs. Do not apply the older patches again on top of this complete source package.
