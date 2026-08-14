# Verification - Master Soft Deactivation & Payment Navigation - 7.2.4

- Sales Register row factory no longer installs a double-click action.
- Sales Register payment navigation validates invoice context and handles a failed NavigationManager load with one FX-pulse retry and visible feedback.
- Payment History Record Payment uses the same guarded navigation contract.
- Lookup DELETE endpoint now persists `active=0` with `saveAndFlush`; it does not physically delete lookup rows.
- Category DELETE endpoint now persists category `active=0` and child lookup `active=0` atomically; it does not physically delete rows.
- Existing active-only lookup/value queries remain unchanged, so inactive values are excluded from future business-entry choices.
- Version remains 7.2.4.
