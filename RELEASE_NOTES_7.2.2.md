# DSE ERP 7.2.2

## Release focus
- Master/Lookup soft-deactivation instead of destructive deletion.
- Real-time PostgreSQL persistence and refresh after Add/Edit/Deactivate.
- Inactive master values are excluded from future business-entry selections while historical records remain intact.
- Sales Register double-click navigation removed.
- Sales Register View / Record Payments navigation hardened.
- Invoice Details and Payment History Record Payment actions now use guarded navigation with visible failure handling.
- Existing 7.2.0 dynamic application-branding work retained.

## Release validation
Run `mvn clean verify` with the production Java 25 environment before tagging and publishing.
