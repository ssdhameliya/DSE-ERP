# What's New in DSE ERP v10.0.38

### Contract Deserialization Hardening & Zero-Null Primitive Protection
- **Record Deserialization Resilience:** Hardened `SaleDto` and `PurchaseDto` in `OperationDtos.java` with compact constructor default coercers, gracefully mapping null or omitted boolean and numeric fields to safe defaults (`false` / `0.0`) instead of throwing Jackson `InvalidNullException`.
- **Data Import Studio Parity:** Ensured Sales and Purchase Register bulk loaders across Web and Desktop communicate over a 100% resilient contract without deserialization crashes.
- **Enterprise Operations API Stability:** Universal protection against primitive deserialization failures across all REST client endpoints.
