# What's New in DSE ERP v10.0.37

### User-Friendly Server Error Messages & Constraint Diagnostics
- **Actionable Error Diagnostics:** Replaced generic masked server error messages with clear, root-cause diagnostic feedback across all API operations.
- **Database Integrity Violation Transparency:** Dedicated exception handlers explicitly detect and clarify unique constraint violations (duplicate keys), missing mandatory database columns, and foreign key link errors.
- **Payload & Validation Clarity:** Added specific error reporting for JSON payload formatting issues (`HttpMessageNotReadableException`) and bean validation field errors (`MethodArgumentNotValidException`).
- **Data Import Studio Reliability:** Sales and Purchase Register imports receive detailed, item-by-item root-cause messages in case of validation or business constraint conflicts.
