# Changelog – DSE ERP v11.0.10

## Release 11.0.10 – Enterprise System & Application Diagnostics, Cross-Environment Server Log Streaming, Context-Aware Return Routing & Debounced Party Search

- **Release Date:** 2026-10-30
- **Baseline:** Git tag `v11.0.9`
- **Type:** Diagnostics Infrastructure, Performance Optimization, Document Inspection & UI Symmetry Release

---

## Key Improvements & Fixes

### 1. Enterprise System & Application Diagnostics Ledger (`Ctrl+Shift+L`)
- **Centralized Diagnostic Stream:** Unified desktop client exceptions, background task execution failures, and server platform events into an authoritative, searchable diagnostics console.
- **Spring Boot 3 Logback Engine:** Implemented high-fidelity pattern matching for Spring Boot 3 structured logs (`timestamp level pid --- [thread] logger : message`) with multi-line stack trace buffering.
- **Plain-English Error Diagnosis:** Integrated `UserFacingErrorMapper` to translate technical Java stack traces, SQL exceptions, and network timeouts into human-readable titles, root-cause explanations, and recommended actions.
- **Zero-SSH Cross-Environment Streaming:** Added authenticated REST endpoint `GET /api/admin/diagnostics/server-logs` with `AdminApiClient.serverDiagnostics(limit)`:
  - **Local:** Streams local server logs (`D:\DSE Production\Logs\Server\dse-erp-server.log`).
  - **UAT:** Securely streams cloud UAT logs (`https://api-uat.jasviindustries.in`).
  - **PROD:** Securely streams production cloud logs (`https://api.jasviindustries.in`).
  - **Offline Fallback:** Automatically falls back to reading the local workstation disk log if unauthenticated or offline.
- **Diagnostic Management Suite:** Added live counter KPI cards (Total Events, Errors, Warnings, Desktop Client, Server Platform), instant CSV export, log directory launcher, and real-time filtering by log level, component, and source.

### 2. Context-Aware Return Routing & Master Lookup Integration
- **Context-Aware Button Actions:** In Sales and Purchase document inspection popups, the return button now dynamically reflects the record's return state:
  - If a return already exists: Displays **"View Return"** and navigates to the associated return document.
  - If not returned: Displays **"Create Return"** and opens the standardized return creation modal.
- **Dynamic Return Reason Master:** Replaced hardcoded return reason lists with dynamic master lookup table values, allowing administrators to customize return reasons without code modifications.

### 3. Keystroke Lag Elimination in Party Search & Dropdowns
- **Debounced Autocomplete:** Fixed 10–15 second typing freezes across Sales, Purchase, and Quotation dropdowns by introducing a 300ms debounce in `PartySearchUi`.
- **Asynchronous Cache Filtering:** Search queries filter against indexed in-memory party caches on a background worker thread rather than blocking the JavaFX Application Thread on each character stroke.

### 4. Header Navigation & Iconography Modernization
- **System Diagnostics Header Shortcut:** Replaced legacy WhatsApp icon in top header toolbar with dedicated System Diagnostics launcher (`settings` icon), providing one-click access alongside `Ctrl+Shift+L`.
- **Classy3DLoadingView Skeleton Animation:** Integrated tactile 3D skeleton loading views during asynchronous ledger and document fetches.
