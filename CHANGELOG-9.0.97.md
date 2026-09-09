# DSE ERP 9.0.97

## Corrective release

- Fixed sidebar navigation identity so Data Import, Backup & Restore, Create Sale, and Create Purchase cannot inherit primary-action highlighting while another page is active.
- Added Communication to the normal sidebar selection state machine.
- Restored semantic Amount, Paid, and Pending value colours in Sales and Purchase registers using the canonical table-value colour classes.
- Hardened Excel Studio Save / Save & Default synchronization: one publish per workbook save, no server refresh in the middle of default activation, authoritative server checksum tracking, and best-effort cleanup of older defaults after the new default is safely active.
- Reduced Excel template server synchronization to the current workbook and metadata only; local version history is preserved locally and no longer inflates every API upload.
- Preserved the existing 9.0.95 multi-row / first-item repeating-block renderer without modification.
