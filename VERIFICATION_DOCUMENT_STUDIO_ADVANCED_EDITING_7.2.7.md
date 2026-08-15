# DSE ERP 7.2.7 — Purchase Document Studio Advanced Editing Verification

## Baseline

Built from `DSE-ERP-7.2.6-IntelliJ-Settings-Rollback-UI-Refined.zip`, the live 7.2.6 baseline.

## Scope

Purchase Document Studio and the Company & Billing Settings layout only, plus version metadata. No Sales document-generation route changes.

## Static verification completed in the build environment

- All 43 FXML files parsed successfully as XML.
- Root, desktop, shared and server Maven POM files parsed successfully as XML.
- New standalone model classes (`TemplateElement`, `PdfTextRegion`, `ElementType`) compile with the available Java compiler.
- Modified Java files were passed through `javac` parsing; dependency errors are expected because JavaFX/PDFBox/Maven dependencies are not installed in this environment, but no Java syntax/parser errors were reported.
- `SalesController.java`, `SalesListController.java` and `InvoicePdfService.java` SHA-256 hashes exactly match the live 7.2.6 baseline.
- Existing Brand & Identity asset storage keys are unchanged.
- No database migration file was added.

## Runtime verification required on the development machine

The project targets Java 25, while this build environment has Java 21 and no Maven installation. Run on the normal DSE ERP JDK 25 workstation:

```powershell
mvn clean verify
```

Then smoke-test:

1. Document Studio → import a text-based Purchase PDF.
2. Open Edit Existing PDF Text and select an existing address.
3. Replace the address and confirm preview/export hides the original and renders the replacement.
4. Select another existing label and convert it to an ERP field.
5. Test Company Logo / Signature / Application Brand / Payment QR quick insert.
6. Test Image Fit / Fill / Stretch, opacity and rotation.
7. Verify Apply is always visible while the properties section scrolls.
8. Confirm left and right side panels are comfortably usable at common desktop resolutions.
9. Settings → Company & Billing: confirm Application Name / Tagline / Startup Message are at the bottom of the top form and all three brand-image cards remain visible below.
10. Generate one existing Sales PDF and confirm its output path/behavior is unchanged.
