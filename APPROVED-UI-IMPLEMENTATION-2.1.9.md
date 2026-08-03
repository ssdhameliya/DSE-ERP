# DSE ERP v2.1.9 Approved UI Implementation

This cumulative source applies the approved visual system to all screens through the shared UI bootstrap and replaces the Dashboard with the approved chart-free executive layout.

## Implemented application-wide
- Unified premium buttons, inputs, tables, cards, lists and menus.
- Light and dark theme rules.
- One-time UI classification to avoid repeated macOS CSS/layout work.
- Semantic icons remain managed by the existing IconFactory system.
- Existing controller workflows, database services, dialogs and actions remain intact.

## Dashboard
- Removed LineChart, PieChart and BarChart nodes and their data-building calls.
- Replaced them with KPI cards, customer rankings, receivable ageing, recent activity and quick actions.
- Retains background database loading and the loading overlay.
- No chart animation, chart CSS or chart rendering on first load.

## Performance protection
- Shared UI is installed once per root.
- Only one deferred icon decoration pass remains after scene attachment.
- macOS removes expensive card shadows in the approved UI system.
- Tables use a fixed row size for virtualization.

The generated mockups are visual specifications. Exact business data and available actions remain driven by the existing application permissions and services.
