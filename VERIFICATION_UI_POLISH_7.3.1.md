# DSE ERP 7.3.1 — Unified Enterprise UI & Workflow Polish Verification

Baseline: DSE ERP 7.3.0 Document Studio Stabilized + Settings Fixed.

## Scope
- Standardized identity/header geometry using Sales Register as the list/register reference.
- Preserved Create Sale Invoice FXML and `SalesController` byte-for-byte from the supplied baseline.
- Added consistent page/card gutters to the requested dashboard/register/master/bank/import/settings/rollback surfaces.
- Enhanced Sales/Purchase detail drawers with existing financial/contact information and clear edge spacing.
- Added searchable item selection to Create Quotation and Create Purchase using their existing item sources.
- Added KPI presentation to Customer/Supplier and visible semantic KPI icons to Bank/Expense and User Access.
- Improved Reminder reference handling and semantic action colors.
- Standardized dynamic action menus to an Actions icon + visible `Actions` label.
- Prevented automatic decorative icons from being added to CheckBox controls.
- Aligned Splash, Login, Registration and Email/OTP setup into the common two-panel authentication family.

## Static validation completed in build environment
- 43 FXML resources parsed as valid XML.
- Root + desktop + server + shared Maven POMs parsed as valid XML.
- No duplicate `fx:id` values were found.
- 387 FXML event handlers were checked against controller methods, including inherited handlers: 0 unresolved.
- Modified Java sources were passed through the JDK compiler parser; no Java syntax/grammar errors were found.
- Version metadata synchronized to 7.3.1 across Maven modules, desktop metadata, server metadata and runtime manifest.
- Safe Rollback compatibility retains 7.3.0 and includes 7.3.1 on schema generation 1.

## Regression boundaries checked
- `desktop/src/main/resources/fxml/pages/Sale.fxml`: unchanged from baseline.
- `desktop/src/main/java/org/example/controller/SalesController.java`: unchanged from baseline.
- `desktop/src/main/java/org/example/service/InvoicePdfService.java`: unchanged from baseline.
- No database migration was added.

## Local verification required
This environment provides JDK 21 and does not provide Maven, while DSE ERP targets JDK 25. Run the following on the normal development machine before production release:

```powershell
mvn clean verify
```

Then smoke-test Dashboard, Sales Register detail drawer, Quotation item search, Purchase Register/Create Purchase, Customer/Supplier KPI cards, Reminder creation/reference, User Access, Bank/Expense, Bank Statement, Data Import, Document Studio, Settings, Safe Rollback and all authentication screens in both light/dark themes.
