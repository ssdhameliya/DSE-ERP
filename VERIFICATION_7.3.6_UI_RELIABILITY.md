# Verification — DSE ERP 7.3.6 UI Reliability Maintenance

## Fresh checks performed in the handoff environment
- Parsed all desktop FXML files as XML successfully.
- Checked shared/light/dark CSS brace balance and invalid `-fx-font-weight: 850` regressions.
- Verified no fixed day-cell width/height rules remain in the DatePicker reliability contract.
- Verified Purchase and Quotation no longer reference the old editable `cmbItem` control.
- Verified Sale, Purchase and Quotation use the shared TextField item-search contract.
- Verified shared return-editor table applies explicit semantic icons to every business column.
- Verified Settings active panel has no extra left/right host gutter and the three Brand & Identity cards use equal-width growth.
- Verified footer fields use distributed horizontal growth and the normal application footer overrides authentication-size typography.
- Verified current version metadata is 7.3.6; 7.3.5 remains rollback history.
- Ran the repository PostgreSQL/data-boundary/final-architecture audit scripts successfully.
- Performed Java source structural balance checks across the project.

## Toolchain limitation
A full Maven Java 25 compile was not executed in this sandbox because the available runtime is Java 21 and Maven is not installed. Run `mvn clean verify` in the normal JDK 25 development/CI environment before publishing installers.
