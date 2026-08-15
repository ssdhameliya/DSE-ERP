# Verification — DSE ERP 7.3.2 Reminder Reliability & Control Styling

## Root-cause verification
1. `JpaNativeRepository.query(...)` uses positional `Object[]` rows and intentionally does not populate alias names.
2. 7.3.1 `InsightsService.reminders()` used alias getters (`getInt("id")`, `getString("title")`, etc.).
3. Therefore every Reminder DTO could be mapped with ID `0` and blank fields.
4. Edit then called `/reminders/0`, producing HTTP 400, while create could insert a valid record but fail to find its returned ID in the malformed list, producing HTTP 500.
5. 7.3.2 uses positional columns 1–9 for reminder mapping and direct ID reload after create/update.

## Database migration
- `V7_3_2__reminder_reliability.sql` contains plain statements compatible with `SecurityFinancialMigrationRunner.splitStatements`.
- Migration is explicitly registered in `SecurityFinancialMigrationRunner`.
- Required Reminder columns are verified during backend startup.
- Migration does not delete Reminder rows.

## CSS/control verification
- Shared `ui-components.css` no longer defines `.row-actions` as `GRAPHIC_ONLY`/38px.
- Dark theme no longer has a broad top-level `.list-cell` padding rule; normal ListView cells are scoped through `.list-view .list-cell`.
- Final shared and theme contracts explicitly preserve ComboBox selected text and Action menu labels/arrows.
- Data Import mapping ComboBoxes are 34px high with compact selected-value padding.
- Standard dropdown Actions columns are at least 120px wide.

## Static validation performed in build environment
- All 43 FXML files parse as XML.
- All 4 Maven POM files parse as XML.
- No duplicate `fx:id` values were found.
- All 423 FXML handler names resolve to Java method names in the desktop source tree.
- Modified Java files passed lexical brace/parenthesis/string-balance validation.
- All shared CSS files passed brace-balance validation.
- The new reminder migration is split into 12 valid plain SQL statements by an equivalent of the application's migration splitter.

## Environment limitation
The build workspace has JDK 21 and no Maven installed. DSE ERP targets JDK 25, so the final full `mvn clean verify` must be executed in the normal JDK 25 development environment before release promotion.
