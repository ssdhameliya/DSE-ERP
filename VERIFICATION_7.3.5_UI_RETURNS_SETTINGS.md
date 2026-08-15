# Verification — DSE ERP 7.3.5 UI / Returns / Settings Maintenance

## Acceptance checks

1. Top-shell user MenuButton reserves descender-safe vertical space in the shared UI contract.
2. Document Studio and Forgot Password receive explicit preserved semantic icons after generic decoration.
3. Explicit TableColumn icons use the self-contained colourful semantic header badge system.
4. Sales Return and Purchase Return headers keep distinct semantic icons and no later controller assignment overwrites them.
5. Sales/Purchase financial cells use the requested semantic colours: Amount red, Paid green when positive, Balance blue while open; zero/settled states remain meaningful.
6. Creating an active sales/purchase return updates the original document status to `RETURNED`; cancelling/deleting the final active return recalculates/restores the source status.
7. Create Sale item lookup is a TextField with a colourful search badge and suggestion popup, not an editable ComboBox.
8. Create Sale PO Date is a read-only TextField while retaining Invoice Date + Payment Terms calculation and persisted PO-date loading/saving.
9. DatePicker popups use the new shared premium calendar geometry and light/dark theme styling.
10. Settings retains the 16px page gutter and every Company/Billing/Payment/Invoice/Notifications/Email/Workspace/Updates panel receives a shared 10px panel-host left/right gutter.
11. Current application/version metadata is synchronized to 7.3.5 while 7.3.4 remains rollback-compatible history.
12. XML/FXML/POM parsing, CSS structure, invalid-font-weight scan and architecture audits pass.

## Fresh verification performed in the handoff environment

- XML/FXML/POM parse: PASS
- CSS brace balance: PASS
- Standalone patch marker (`+`) scan: PASS
- Unsupported `-fx-font-weight: 850` scan: PASS
- 27 focused source acceptance assertions: PASS
- `scripts/audit-desktop-jdbc.py`: PASS
- `scripts/audit-phase2-data-boundary.py`: PASS
- `scripts/audit-postgres-only.py`: PASS
- `scripts/audit-final-data-architecture.py`: PASS

## Build-toolchain limitation

A full Maven/JDK 25 build could not be executed inside the handoff sandbox because the available runtime is Java 21, Maven is not installed, and external toolchain installation is blocked by the environment. The source was therefore not represented as a freshly compiled JDK 25 build. Run `mvn clean verify` with the project's JDK 25 toolchain on the Windows/CI environment before publishing installers.
