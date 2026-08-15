# DSE ERP 7.3.0 - Splash Branding + Settings Workspace Finalization

## Scope
- Load workspace config before Splash.fxml for configured workspaces.
- Refresh splash branding after background config reload.
- Application name, tagline, startup message and application brand image come from Settings with DSE ERP only as fallback.
- Startup/finalizing/opening text and primary stage title use configured application name.
- Remove redundant Settings Workspace title/subtitle/hint row.
- Reduce Settings header and outer workspace padding.
- Enlarge category tiles so full category labels are visible; horizontal scrolling remains available on narrow screens.
- Keep selected category directly below the category strip and preserve all existing settings handlers.

## Protected Areas
- Universal Document Studio logic unchanged.
- Sales route unchanged.
- Purchase/Quotation business calculations unchanged.
- Safe Rollback and Backup/Restore logic unchanged.
- No database migration added.
