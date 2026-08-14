# DSE ERP 7.2.2 - Master Data Real-Time Persistence Verification

## Behavior implemented
- Lookup Add uses POST to the Spring API and server `saveAndFlush`.
- Lookup Edit uses PUT to the Spring API and server `saveAndFlush`.
- Lookup Delete is committed immediately but is blocked when the value is referenced by supported business/master records.
- Category Add/Rename/Delete is transactional and explicitly flushed.
- Category Delete validates every child lookup before removal.
- Master Data reloads from the server after CRUD actions and whenever a cached Master Data screen is shown again.
- Fresh application startup loads categories and values from the server/PostgreSQL source of truth.
- No SQL migration/reseed script is generated for user CRUD.

## Protected live references
- Item category, brand, material, unit, GST and discount.
- Expense category.
- Finance/payment mode.
- Sales payment terms and GST type.
- Sales charge types/names/codes.

## Verification performed in build environment
- audit-desktop-jdbc.py: PASS
- audit-phase2-data-boundary.py: PASS
- audit-postgres-only.py: PASS
- audit-final-data-architecture.py: PASS
- Maven POM XML parsing: PASS
- Java brace/source structure checks: PASS
- Version contract 7.2.2: PASS

## Remaining release gate
The execution environment provides Java 21 and no Maven executable. Run `mvn clean verify` locally with the project's Java 25 toolchain before publishing.
