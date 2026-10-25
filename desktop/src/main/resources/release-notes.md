# What's New in DSE ERP v11.0.2

### Dynamic Reference Format Tokens
- **Custom Token Combinations Supported**: User-defined reference lookup formats (e.g., `DD-YYYY-XXXX`, `MM-YYYY-XXXX`, `DDMMYYYY-XXXX`, `DD-MM-YYYY-XXXX`) now reliably resolve day (`DD`), month (`MM`), 4-digit year (`YYYY`), and 2-digit year (`YY`) alongside sequence counter `XXXX`.
- **Eliminated Literal Token Artifacts**: Fixed reference format generator in `BusinessOperationsService` where individual `DD` and `MM` remained unparsed as literal text when not configured in legacy compound date strings.
- **Auto-Expanding Sequence**: Zero-padded sequence numbers (e.g., `0001` to `9999`) automatically expand cleanly to 5 and 6 digits (`10000`, `100000`) without truncation.

### Email Transmission Modal Expansion & Auto-Resize
- **Expanded Dimensions & Resizability**: Increased default dialog size to 720x760 (min 620x560) and enabled window resizability so users can expand the modal freely.
- **Unclipped Message Body**: Configured message body `TextArea` with `prefRowCount="12"`, `minHeight="220"`, and `VBox.vgrow="ALWAYS"`. Multi-line invoice summaries and signature blocks are fully visible with comfortable scrolling.

### Purchase Return Email Transmission & Template Parity
- **Interactive Pre-Send Dialog**: Clicking "Send Email" in the Purchase Returns register now presents the full `EmailTransmissionDialog` preview before sending, matching Sales Invoices, Purchase Bills, Quotations, and Sales Returns.
- **Configurable Message Template**: Added `PURCHASE_RETURN` to `MessageTemplateService` and Settings → Email Delivery templates with live sample token previews, customizable subjects, and editable debit note bodies.
- **Live Dispatch Progress**: Integrates with `LiveOperationDialog` to display real-time SMTP connection stages and status logs during transmission.
