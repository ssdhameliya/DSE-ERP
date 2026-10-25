# What's New in DSE ERP v11.0.4

### Auto-Match Suggestions Review & Fast Discovery
- **Real-Time Live Discovery Dialog**: Clicking "Auto Match Suggestions" instantly launches the interactive review dialog with live progress, eliminating background delays.
- **Enhanced Match Heuristics**: Expanded discovery rules to surface exact monetary matches alongside high-confidence candidates, ensuring no eligible reconciliation opportunity is missed.

### Dynamic Table Layout & Full Text Wrapping
- **Dynamic Narration & Reference Wrapping**: Table text cells dynamically wrap multi-line text instead of truncating with ellipses, enabling full visibility of extended bank narrations.
- **Compact Selection Checkbox**: Streamlined the record selection column width across Bank Statement and Reconciliation tables, eliminating awkward whitespace.

### High-Contrast Themed KPI Cards
- **Vibrant Accent Colors**: All metric cards in Bank Statement, Bank Entry, and Expense Entry now render in high-contrast thematic colors (Cyan, Emerald Green, Coral Red, Amber, and Purple).
- **Responsive Sizing & Spacing**: Increased value font sizes to 19px with dedicated vertical spacing to eliminate overlapping notes and cramped captions.

### Universal Navigation & Record Isolation
- **Direct Register Routing**: Clicking linked document numbers in Bank Statement navigates directly to Sales Register, Purchase Register, or Reconciliation detail views.
- **Isolated Target Record**: Pre-fills register filters and isolates solely the selected document in view rather than leaving hundreds of unrelated records on screen.

### Email Transmission Viewport & Signature Preview
- **Instant Body Viewport Rendering**: Resolved JavaFX TextArea layout pass timing, ensuring email message bodies render immediately on dialog launch without requiring keypresses or clicks.
- **Dedicated Signature Preview**: Added signature / stamp card preview dialog to inspect company stamps prior to client transmission.

### Duplicate Statement Prevention & Cleanup
- **Strict Duplicate Detection**: Server and client guard against duplicate statement imports for identical accounts and overlapping date periods.
- **Instant Deletion UI Reset**: Deleting a statement immediately clears all visible transactions and metric counters from the screen.
