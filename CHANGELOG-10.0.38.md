# DSE ERP 10.0.38 Release Notes

## Overview
DSE ERP 10.0.38 delivers robust contract deserialization hardening across core operations records (`SaleDto` and `PurchaseDto`), eliminating Jackson primitive type deserialization failures when boolean or numeric fields are omitted by API clients or Data Import Studio bulk loaders.

## Key Highlights

### 1. Operations Record Deserialization Hardening
- **Zero-Null Primitive Protection:** Hardened `SaleDto` and `PurchaseDto` in `OperationDtos.java` with compact constructor default coercers for `emailSent`, `whatsappSent`, and `sameAsBilling`.
- **Jackson `InvalidNullException` Prevention:** Replaced primitive `boolean` component declarations with `Boolean` and automatic default coercers, ensuring that omitted or null fields in incoming JSON payloads gracefully resolve to safe defaults (`false` and `true` respectively) without throwing `InvalidNullException: Cannot map null into type boolean`.

### 2. Universal Data Import Studio Contract Parity
- **End-to-End Resilience:** Ensures bulk loaders for Sales and Purchase registers seamlessly communicate across Web and Desktop without contract mismatch failures.
- **Enterprise Operations API Stability:** Eliminates client-side deserialization traps across all REST API endpoints accepting sales and purchase documents.
