# DSE ERP 7.3.1 — Unified Enterprise UI & Workflow Polish

- Sales Register and Create Sale are the visual reference contracts.
- Unified page identity headers, outer gutters, panel spacing and KPI presentation across the requested screens.
- Sales/Purchase detail drawers gain balanced gutters and richer existing-data summaries.
- Quotation and Purchase item selectors support type-ahead search.
- Create Purchase exposes existing purchase metadata in a Sale-style organized workspace without schema changes.
- Customer/Supplier and User Access KPI presentation improved.
- Reminder reference handling and semantic action colors improved.
- Actions now use an Actions icon + label; CheckBoxes retain only their native tick.
- Splash, Login, Registration and Email/OTP setup share the same two-panel visual family.
- Sales creation logic and other protected business routes remain unchanged.

## Final QA refinements
- Action menus now use a semantic Actions icon plus the visible `Actions` label instead of graphic-only/settings-style controls.
- Checkboxes retain only the native checked/unchecked affordance; no additional generic icon is injected.
- Safe Rollback compatibility retains 7.3.0 and adds 7.3.1 on the same schema generation.
- Reminder creation retrieves the exact inserted row by PostgreSQL `RETURNING id`, preventing duplicate-title ambiguity.
- Runtime/build-facing version metadata and packaging helper text are synchronized to 7.3.1.

### Protected areas
- Create Sale Invoice layout/business behavior is not redesigned.
- Sales/Purchase/Quotation calculations, PostgreSQL schema, PDF generation routes, Backup & Restore, Safe Rollback mechanics, email/WhatsApp, and Document Studio business logic are not altered by the UI consistency work.
