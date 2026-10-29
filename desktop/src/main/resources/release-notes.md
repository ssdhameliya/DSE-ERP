# What's New in DSE ERP v11.0.10

### Enterprise System & Application Diagnostics Ledger (`Ctrl+Shift+L`)
- **Centralized Diagnostic Stream**: Live unified stream of desktop client exceptions, background task execution failures, and server platform events.
- **Plain-English Error Guidance**: Automatically translates raw technical stack traces into human-readable problem descriptions and actionable resolution steps via `UserFacingErrorMapper`.
- **Zero-SSH Cloud Log Streaming**: Seamlessly streams server logs across Local, UAT (`api-uat.jasviindustries.in`), and PROD (`api.jasviindustries.in`) via authenticated REST diagnostics API, with automatic local disk fallback.
- **Diagnostic Management Suite**: Real-time KPI counter cards, instant CSV export, log directory launcher, and instant filtering by log level, component, and source.

### Context-Aware Return Routing & Master Lookup
- **Dynamic Action Button**: Sales and Purchase document inspection popups now evaluate return status dynamically: shows **"View Return"** if returned, or **"Create Return"** if unreturned.
- **Dynamic Return Reasons**: Fully integrated with master lookup table, allowing configurable return reasons.

### High-Performance Party Search & Dropdown Autocomplete
- **Debounced Keystroke Handling**: 300ms debounce eliminates input freezing and typing delays on Sales, Purchase, and Quotation screens.
- **Asynchronous Search Worker**: Background thread filtering maintains 60 FPS responsive typing even with thousands of party records.

### Global Header Modernization
- **System Diagnostics Button**: Replaced WhatsApp icon in the header toolbar with dedicated System Diagnostics launcher (`settings` icon).
- **Classy3DLoadingView**: Tactile skeleton loading views for smooth document and ledger transitions.
