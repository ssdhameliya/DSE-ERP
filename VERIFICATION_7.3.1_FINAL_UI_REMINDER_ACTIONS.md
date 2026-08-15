# DSE ERP 7.3.1 final live-line correction

Same application version: **7.3.1**.

Included:
- Sales/Purchase register outer gutter alignment.
- Purchase KPI height reduced to the Sales Register density.
- Reminder CRUD/status/snooze/detail visibility and TEXT-date persistence repair.
- Customer/Supplier row Actions menus.
- Master Data identity/search card alignment and color surfaces.
- Bank/Expense 14px outer gutter override.
- Data Import 1/2/3/4 steps grouped in one visible parent panel.
- Document Studio semantic KPI icons/cards.
- Global row action menus forced to semantic actions-list icon + literal `Actions`; removed graphic-only CSS behavior.

Protected:
- Version remains 7.3.1.
- Sales/Purchase calculation logic unchanged.
- Document Studio PDF engine unchanged.
- Safe Rollback/Backup/Settings/accordion navigation unchanged.

## Global Actions contract
- All row-level action MenuButtons are normalized to a semantic list/actions glyph plus the literal text `Actions`.
- Legacy CSS rules that forced row action menus to `graphic-only` are explicitly overridden at the end of both light and dark themes.
- Sales/Purchase returns, Backup/Restore, User Access and other action columns use a visible `Actions` header and adequate width.
- CheckBox controls remain excluded from automatic icon decoration.
