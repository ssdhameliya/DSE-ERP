# What's New in DSE ERP v11.0.3

### Financial Drill-Down Inspection Modal Overhaul
- **Centralized UI & Semantic Architecture**: The "Inspect / Drill-Down" modal in Financial Statements (Profit & Loss and Balance Sheet) has been rebuilt using `PopupTableWorkspace` and `DynamicTableLayoutManager`.
- **Top Metric Strip**: Added live metric cards showing Category, Total Transactions, and Net Total with semantic icons and themed values.
- **Colored Table Headers & Icons**: Table columns (`Date`, `Voucher / Ref`, `Party / Account`, `Description`, `Debit`, `Credit`, `Balance`) feature centralized semantic header icons and themed color labels.
- **Configured Date Format Enforcement**: The date column and CSV export strictly follow the user-configured system business date format via `BusinessClock.formatDate`.
- **Monetary Highlighting**: Debit, Credit, and Balance columns utilize semantic colored cell factories (`orange`, `green`, `blue`).
- **Real-Time Search & CSV Export**: Instant table filtering across party, voucher, or narration with full ledger CSV export support.

### Bank Statement Bulk Auto-Match Suggestions
- **Review Before Approval Dialog**: Added "Auto Match Suggestions" button to the bank reconciliation toolbar, opening an interactive review workspace for high-confidence suggestions before bulk confirmation.
- **Complete Transaction Metadata**: Displays Checkbox, Bank Date (formatted to system setting), Reference / Cheque No., Description / Narration, Bank Amount, Matched ERP Document, Customer / Supplier, Document Date, Document Outstanding Amount, and Confidence Score.
- **Top Metrics & Instant Search**: Live summary strip displaying Total Suggestions, Selected to Match, Total Amount, and Average Confidence, paired with instant search filtering.
- **Live Progress & Allocation**: Confirming matches executes via `LiveOperationDialog`, creating corresponding Bank Entries and payment/refund allocations with live step-by-step progress.
- **Interactive KPI Card Filters**: Removed redundant Reconciled card. `Total`, `Unmatched`, `Suggested`, `Matched`, and `Expense` cards are clickable filters with hand cursor, filtering the register instantly on click.

### Universal Single-Record Navigation Isolation
- **Focused Navigation**: Traveling from the global search bar, notification drawer, or cross-screen linked buttons (Bank Statement, Bank Expense, Sales, Purchase, Quotation, Item Master, Party Master) isolates and displays **only the targeted record**.
- **Search Query Autofill**: Pre-fills the register search box with the document reference or party code, preventing visual clutter from unrelated rows.

### Email Signature Inline Image Transmission
- **MIME Multipart/Related Integration**: `SmtpMailService` now packages company signature and stamp images as inline CID attachments (`cid:signatureImage`), correctly rendering signatures inside the email body across standard email clients.
- **Full Shared Client Support**: `BusinessEmailClient` and `BusinessEmailController` transport signature image payloads in client-server shared architecture.
