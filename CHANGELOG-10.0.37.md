# DSE ERP 10.0.37 Release Notes

## Overview
DSE ERP 10.0.37 delivers actionable, user-friendly backend server error diagnostics, eliminates opaque generic error masking, and provides detailed constraint violation feedback for enterprise database operations and Data Import Studio bulk loaders.

## Key Highlights

### 1. User-Friendly Backend Server Error Propagation
- **Root-Cause Diagnostic Clarity:** Eliminated the generic masked string (*"The ERP server could not complete this operation. Check the server log for the underlying error."*) in favor of extracting and returning the exact underlying root cause from Spring Boot and persistence providers.
- **Database Integrity Violation Transparency:** Added dedicated `@ExceptionHandler(DataIntegrityViolationException.class)` to clarify unique constraint violations (duplicate document keys), missing required database columns (NOT NULL constraint failures), foreign key relationship errors, and business check constraints.
- **Request Format & Validation Error Handling:** Specific error handlers for `HttpMessageNotReadableException` (invalid JSON formats/payloads) and `MethodArgumentNotValidException` (field validation failures with exact field-by-field messages).

### 2. Enterprise Data Import Studio Reliability
- **Transparent Bulk Import Reporting:** Detailed error messages are surfaced directly back to the Web and Desktop Data Import Studio for Sales Register and Purchase Register batch operations.
- **Constraint-Aware Failure Dialogs:** Users receive immediate, actionable explanations when a duplicate document number or invalid master reference is encountered during bulk imports.
