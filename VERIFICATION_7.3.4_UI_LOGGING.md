# Verification — DSE ERP 7.3.4 UI / Logging Maintenance

## Acceptance checks

1. `ui-components.css` contains no unsupported `-fx-font-weight: 850`.
2. Light/dark theme files contain no standalone patch-marker (`+`) lines.
3. Text-bearing table action menus have a high-specificity 122/130/148 px geometry contract.
4. Context menu rows reserve 36 px height with explicit bottom label allowance.
5. Communication Center retains Java-side `refresh` icon + `Re-send` label and has a high-specificity table-cell CSS exception.
6. Desktop Maven dependencies include `org.apache.logging.log4j:log4j-core` at runtime.
7. Current application version surfaces are synchronized to 7.3.4; 7.3.3 remains in rollback history and historical release notes.
8. No database schema generation change is introduced.
