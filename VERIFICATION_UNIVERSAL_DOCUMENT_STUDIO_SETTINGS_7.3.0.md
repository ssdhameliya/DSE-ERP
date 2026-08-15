# Verification — DSE ERP 7.3.0

Scope: Universal Document Studio + Settings Workspace redesign, based on the approved 7.2.7 Advanced Editing source.

## Static verification completed in the build environment

- All 43 JavaFX FXML files are well-formed XML.
- Root, desktop, server and shared Maven POM files are well-formed XML.
- Universal Document Studio model classes compile successfully with the locally available JDK for syntax/type verification of the model layer.
- Document Studio FXML action handlers were checked against PdfDesignerController methods.
- Application version references used by runtime/build metadata were synchronized to 7.3.0.
- Safe Rollback compatibility catalog retains prior compatible versions and includes 7.3.0.
- The `InvoicePdfService.sales(...)` method was compared with the 7.2.7 baseline and remains byte-for-byte identical.
- Existing 7.2.7 template JSON remains loadable: missing `category` metadata is derived from the existing `documentType`.

## Production build certification required on the development workstation

This execution environment has JDK 21 and does not contain Maven or the JDK 25/JavaFX 25 dependency set required by this project. Therefore a complete Maven build is not claimed here.

Run on the normal DSE ERP development workstation:

```powershell
mvn clean verify
```

Expected result:

```text
BUILD SUCCESS
```

Recommended smoke test:

1. Open Document Studio and import an arbitrary PDF as General PDF.
2. Select existing PDF text, replace it, add text/image objects, add/rotate/delete a workspace page, preview, and export.
3. Connect the same General PDF to Purchase data and verify Purchase fields appear.
4. Open an existing 7.2.7 Purchase template and verify it remains available and renders with Purchase data.
5. Create a Quotation template, set it default, and generate/preview/email a Quotation; verify fallback still works if the custom template is unavailable.
6. Generate a Sales PDF and verify the existing Sales layout/runtime behavior is unchanged.
7. Open Settings and click each horizontal category; verify the selected panel fills the content area and Save Settings/Test Email continue to work.
8. Open Safe Rollback and verify version 7.3.0 is recognized without changing current database-preservation behavior.
