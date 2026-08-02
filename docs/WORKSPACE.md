# Workspace and application data

DSE ERP stores business data outside the installed application. On the first run, the setup wizard asks where the business workspace should be created.

The workspace contains:

- `Database/`
- `Config/`
- `Backups/`
- `Reports/`
- `Imports/`
- `Exports/`
- `Attachments/`
- `Templates/`
- `Logs/`
- `Temp/`
- `Updates/`
- `Documents/`

A small pointer file in the normal operating-system application-data folder remembers the chosen workspace location. This allows the SQLite database and business files to live on another internal drive or an external SSD.

Existing installations are adopted automatically. Legacy `JavaAppERP.db` and `config.properties` files are reorganized into the workspace without deleting business data.

## Moving a workspace

Use **Settings → Workspace & Storage** to schedule a move. The move is applied on the next application start, before SQLite opens. The previous workspace remains available as a recovery copy until the user removes it manually.

## Backup recommendation

A workspace on a separate drive protects against failure of the system drive, but it is not a complete backup strategy. Keep at least one verified backup on a different physical device or trusted off-device location.
