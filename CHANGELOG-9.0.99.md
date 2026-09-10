# DSE ERP 9.0.99

## Final corrective release

- Fixed the final table-rendering CSS authority so semantic values remain visibly coloured in JavaFX child `Text` nodes. Sales/Purchase Amount, Paid and Pending values now keep their canonical semantic colours in normal, hover and selected rows. Status semantic text is protected by the same final authority.
- Added a server-owned `minimumSupportedDesktopVersion` runtime contract. UAT/PROD can set `DSE_MINIMUM_SUPPORTED_DESKTOP_VERSION` without rebuilding the desktop; if omitted, the server safely falls back to requiring its own version.
- Compatible older Shared Clients now receive an optional pre-login update prompt. Choosing **Not Now** resumes startup and opens Login instead of leaving the splash/bootstrap screen stranded.
- A deferred compatible update is suppressed only for the current application session and is offered again after restart.
- Desktops below the server minimum, API-revision mismatches, newer-desktop/older-server combinations, and same-version build mismatches remain blocked.
- Mandatory startup update decline/failure exits cleanly instead of leaving an unusable bootstrap screen.

## Preserved corrections from the partial release

- Central sidebar navigation identity for Data Import, Backup & Restore, Create Sale and Create Purchase.
- Communication sidebar selection state.
- Canonical Sales/Purchase Amount, Paid and Pending semantic cell classes.
- Excel Studio Save / Save & Make Default server synchronization hardening.
- Excel Studio mapped-item validation supports `item.remarks`, `item.description`, `item.descriptionWithRemarks`, and mapped item code identity.
- The 9.0.95 desktop/server repeating-row renderers remain unchanged.
