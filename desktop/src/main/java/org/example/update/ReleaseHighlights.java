package org.example.update;

/**
 * Small offline fallback for the in-app What's New dialog. The published
 * GitHub Release body remains the authoritative release notes when online.
 */
public final class ReleaseHighlights {
    private ReleaseHighlights() { }

    public static String forVersion(String version) {
        if ("9.0.75".equals(version)) {
            return """
                    DSE ERP 9.0.75 — Automatic Workspace Recovery

                    • Starts automatically with the saved valid workspace instead of reopening the workspace/setup chooser only because an older setup.completed marker is missing or false.
                    • Verifies the existing PostgreSQL/Spring company state first and repairs only the local setup marker when the workspace is already initialized.
                    • Keeps the Setup Wizard for genuine first-time setup and keeps Select Existing Workspace / Move Workspace available under Settings.
                    • Preserves the 9.0.74 import reliability, dialog semantics, immediate action feedback, Java 25, JavaFX 25.0.2 and exactly two runtime CSS themes.
                    """;
        }
        if ("9.0.74".equals(version)) {
            return """
                    DSE ERP 9.0.74 — Import Reliability & User Feedback

                    • Fixes Bank Statement fresh imports so new versioned transactions commit once and exact same-source re-imports remain idempotent.
                    • Makes Customer, Supplier, Item and Master Value dry-run previews honor the selected duplicate policy and existing database records before commit.
                    • Keeps explicit dialog types authoritative so successful import summaries containing “Failed: 0” no longer render as rejection/error dialogs.
                    • Standardizes immediate success feedback for completed mutation actions across Returns, Customer 360, Bank Statement, Inventory, Quotations, User Access, Party Master and Notification Center.
                    • Routes remaining runtime and activity modal dialogs through the shared owned-dialog presentation system for consistent ownership, theme and semantics.
                    • Preserves Java 25, JavaFX 25.0.2, PostgreSQL ownership, existing document calculations, PDF Studio behavior and exactly two runtime CSS themes.
                    """;
        }
        if ("9.0.73".equals(version)) {
            return """
                    DSE ERP 9.0.73 — PDF Studio Activation & Template Deletion Reliability

                    • Completes PDF Studio Publish / Set as Default sample validation with realistic Item Master remarks required by the shared Standard Sales PDF layout.
                    • Keeps GST/IGST sample validation on supported tax modes instead of business-treatment labels such as Registered Business.
                    • Fixes deletion of the mapped DS Engineers Sales starter so an intentional delete stays deleted instead of being silently reinstalled on refresh.
                    • Preserves intentional deletion/fallback behavior for the Jasvi default Sales template and the Settings → Select Existing Workspace workflow from 9.0.72.
                    • Preserves portable PDF Studio template import/export, dynamic Sales PDF layout and the two-theme UI architecture.
                    """;
        }
        if ("9.0.63".equals(version)) {
            return """
                    DSE ERP 9.0.63 — Workspace, Master Data & Email Stability

                    • Adds permanent Existing Workspace recovery for missing pointers, moved workspaces and startup/runtime errors without recreating company/admin/business data.
                    • Repairs Item Master category registrations for CATEGORY, UNIT, MATERIAL, BRAND and GST on fresh and upgraded PostgreSQL databases.
                    • Preserves Category/HSN/Unit snapshots across Sales, Purchase and Quotation lines and exposes the missing Item metadata in transaction tables/search.
                    • Sanitizes registration SMTP failures, keeps raw provider diagnostics out of public UI, and refreshes CAPTCHA only for CAPTCHA failures.
                    • Protects company-server SMTP credentials by returning only a configured/not-configured state and retaining the stored app password when an Admin leaves the replacement field blank.
                    • Preserves the 9.0.62 Project Execution removal, PDF Studio mapping/layout fixes, normal Sales/Purchase/Returns/Quotation flows and two-theme UI architecture.
                    """;
        }
        if ("9.0.62".equals(version)) {
            return """
                    DSE ERP 9.0.62 — Project Execution Removal

                    • Removes the Project Execution module and its Projects, Sales Orders, Purchase Orders, GRN and Dispatch screens, APIs, permissions and server persistence layer.
                    • Removes Project Execution reference fields and handoffs from normal Sales and Purchase invoices while preserving Customer PO / Order No. as the standard customer reference.
                    • Simplifies Customer 360° to customer-owned Quotations, Invoices, Payments, Contacts, Notes and Documents without Project/Sales Order split or unlinked-sale classification.
                    • Removes Project/Order/GRN/Dispatch reference formats from Master Data and provides an upgrade migration that drops the obsolete workflow tables, linkage columns and permissions from existing PostgreSQL databases.
                    • Preserves PDF Studio 9.0.61 universal JSON/template mapping, GSTIN alignment, normal Sales/Purchase/Returns/Quotation flows, stock, payments, reporting and the two-theme UI architecture.
                    """;
        }
        if ("9.0.61".equals(version)) {
            return """
                    DSE ERP 9.0.61 — Universal PDF Studio JSON & Template Mapping

                    • Lets users import an existing PDF or create a blank PDF for General PDF and every supported ERP document type, including Purchase Return, Quotation, Sales Return, Delivery Challan, Credit/Debit Note and Payment Receipt.
                    • Adds the universal PDF Studio JSON v2 contract across ERP templates: document.*, party.*, transport.*, tax.*, totals.*, items[] and charges[], while retaining all 9.0.60 legacy mapping keys.
                    • Adds a read-only View JSON Data action in PDF Studio so users can verify the exact record payload being mapped without manually editing JSON.
                    • Corrects Billing and Delivery GSTIN placement in the built-in Jasvi Sales Invoice by aligning mapped values to the source PDF GST-IN baseline without changing the approved artwork or page geometry.
                    • Preserves the 9.0.60 Project Execution fixes, per-render PDF font isolation, fixed-layout pagination behavior, PostgreSQL ownership and exactly two runtime CSS themes.
                    """;
        }
        if ("9.0.58".equals(version)) {
            return """
                    DSE ERP 9.0.58 — GST Returns, Admin MFA & Scoped UI Stabilization

                    • Reconciles approved Sales/Purchase Returns into the GST report as negative taxable/GST movements while preserving GST-inclusive refund/settlement totals.
                    • Adds an administrator-owned MFA policy (Required / Admin Controlled / Disabled) and makes the existing per-user MFA flag authoritative when Admin Controlled is selected.
                    • Applies bounded responsive KPI geometry only to Dashboard and Safe Rollback; every other KPI container retains its existing behavior.
                    • Cleans duplicate semantic mappings, prioritizes business-specific fields over generic reference inference, and separates Import/Export/View/Project/SO/GRN/Dispatch icon identities and colours.
                    • Keeps the release-gate structure generic/current, preserves all historical database migrations, exactly two runtime CSS files and no Markdown packaging artifacts.
                    """;
        }
        if ("9.0.57".equals(version)) {
            return """
                    DSE ERP 9.0.57 — Audit Contract & Version Alignment

                    • Advances desktop, server, shared runtime contract, manifests, launchers and update identity together to 9.0.57.
                    • Removes the stale hard-coded 9.0.52/9.0.18 expectation from the Auth / Shortcut / Recon UI audit and validates the active shared runtime contract dynamically.
                    • Adds the Auth / Shortcut / Recon UI contract to the current aggregate release gate so stale version assertions cannot silently return.
                    • Preserves the 9.0.56 KPI rollback, Project Execution, Customer 360, Sales stock, session, registration, attachment and semantic UI corrections unchanged.
                    """;
        }
        if ("9.0.56".equals(version)) {
            return """
                    DSE ERP 9.0.56 — UI Standards, Customer 360 & Configuration Stabilization

                    • Synchronizes desktop, server, shared runtime contract, manifests, launcher and update identity on 9.0.56.
                    • Repairs Customer 360 contacts, notes and customer-document schema/runtime guards and makes attachment timestamps PostgreSQL-safe.
                    • Compacts Customer 360 customer details and KPI spacing so the active business tab receives more working space, with separated approved nine-tab navigation.
                    • Standardizes Project Execution and Customer 360 owned dialogs on the existing ERP dialog language used by mature screens such as Bank Statement History.
                    • Moves Project Execution document reference formats into Master Data REFERENCE FORMAT while retaining atomic number allocation only on successful Save.
                    • Adds Settings → Security & Session with administrator-controlled inactivity timeout and warning values; defaults remain 10 minutes / 2 minutes.
                    • Routes the idle-session warning through the central OwnedDialog presentation standard rather than a controller-owned custom window.
                    • Reworks Registration to use the available desktop space without normal-screen scrolling and preserves automatic authenticator QR enrollment after Email OTP.
                    • Tightens semantic field/action mappings so Project, Sales Order, Purchase Order, GRN, Dispatch, Import and Export retain distinct icons and theme-owned colours.
                    • Preserves existing financial/payment/return calculations, server-owned persistence, multi-user locking, stock reservation and exactly two runtime CSS themes.
                    """;
        }
        if ("9.0.53".equals(version)) {
            return """
                    DSE ERP 9.0.53 — Session Security & Workflow Focus

                    • Adds a 10-minute user-inactivity timeout with a high-priority two-minute countdown and Stay Signed In / Log Out Now actions.
                    • Extends the authenticated server token only when the user explicitly stays signed in; automatic logout clears the desktop session and returns to Login.
                    • Adds centralized business-order focus handling for keyboard-first transaction entry, including context-aware Customer 360° → New Sale focus and Ctrl+S Save.
                    • Improves Sales Order entry with live Item Master stock visibility: on-hand, reserved, free-to-promise, requested quantity and post-order balance.
                    • Makes Sales Orders reserve/release Item Master reserved stock transactionally so multiple users see meaningful free-to-promise quantities.
                    • Warns, but does not silently block, when a Sales Order exceeds free-to-promise stock; the server re-reads and updates reservations under row locks.
                    • Removes decorative per-item icons from order-entry item search results while preserving the existing semantic icon system everywhere else.
                    • Preserves Customer 360°, existing Sale/Purchase/Payment calculations, PostgreSQL ownership, row-version protection and the two-theme UI architecture.
                    """;
        }
        if ("9.0.52".equals(version)) {
            return """
                    DSE ERP 9.0.52 — Customer 360°

                    • Adds Customer 360° as a server-backed business overview opened from the existing Customers register.
                    • Keeps the approved scope focused on Overview, Contacts, Quotations, Sales Orders, Projects, Invoices, Payments, Notes and Documents.
                    • Uses Back to Customers, Edit Customer and New Sale as the primary header actions; New Sale opens the existing Sale screen with the customer preselected.
                    • Reuses existing Quotation, Project Execution, Sales Invoice and Payment records rather than duplicating transaction logic or financial calculations.
                    • Adds server-owned multi-contact and customer-note records with optimistic row-version protection for multi-user edits.
                    • Extends the existing managed attachment framework to Customer documents with the same server-side storage and permission model.
                    • Preserves the 9.0.51 UI stabilization, 9.0.50 registration security, 9.0.49 project execution, PostgreSQL ownership and existing Sale/Payment authority.
                    """;
        }
        if ("9.0.51".equals(version)) {
            return """
                    DSE ERP 9.0.51 — UI Stabilization

                    • Standardizes application actions on one 3D dual-tone semantic button system while preserving every existing action label, icon and handler.
                    • Normalizes Back/Previous/Next/Close navigation presentation across screens and keeps destructive actions visually distinct.
                    • Removes Save View and Saved Views from Sales Register, Purchase Register and Quotation Register to simplify crowded register toolbars.
                    • Makes password reveal controls transparent and theme-safe instead of inheriting the blue action-button surface.
                    • Extends semantic field-label treatment and improves compact input sizing so important values remain readable in both light and dark modes.
                    • Makes What’s New reliable offline: the packaged release carries full notes and uses them whenever the online release body is missing or too short.
                    • Preserves 9.0.50 registration security, 9.0.49 project execution, existing Sale/Purchase calculations, payments, PostgreSQL data ownership and multi-user behavior.
                    """;
        }
        if ("9.0.50".equals(version)) {
            return """
                    DSE ERP 9.0.50 — Registration Security

                    • Adds non-Admin self-registration with CAPTCHA, email OTP, authenticator TOTP enrollment and Pending Admin Approval.
                    • Blocks pending/rejected non-Admin accounts from receiving a login session even when credentials are otherwise correct.
                    • Prevents Admin/Super-Admin self-registration; privileged accounts remain controlled by existing Admin User Management.
                    • Adds the Registration Approvals workspace with final role assignment, approve/reject actions and concurrent-review protection.
                    • Aligns non-Admin Forgot Password with CAPTCHA, email OTP and existing authenticator verification while preserving the existing Admin exception.
                    • Keeps an upgrade bridge for pre-9.0.50 non-Admin accounts so existing users are not unexpectedly locked out before TOTP migration.
                    """;
        }
        if ("9.0.49".equals(version)) {
            return """
                    DSE ERP 9.0.49

                    • Unifies document arithmetic on one HALF_UP calculation authority: 2dp money/rates, 4dp quantities and inventory unit costs, and canonical PDF/XLSX totals.
                    • Preserves exact return value allocation down to the final paise, keeps original line discounts in Return previews/documents, and serializes concurrent Returns against the source document.
                    • Rebuilds payment totals from authoritative ordinary payments plus active bank-reconciliation rounding adjustments so later payment edits cannot lose settlement paise.
                    • Adds optimistic multi-user protection to Quotations, User Administration and Permission Matrix, including a transactional guard that always keeps at least one active unlocked Admin.
                    • Adds checksum-based optimistic publishing for server-owned Document Studio/PDF Studio templates so one user's stale copy cannot silently overwrite another user's published revision.
                    """;
        }
        if ("9.0.46".equals(version)) {
            return """
                    DSE ERP 9.0.46

                    • Cleans historical GENxxx Master references into category-specific CAT/UNT/MAT/BRD/GST/... codes while preserving database ids, business values and legacy aliases.
                    • Keeps Role Master lookup_value as the only security identity; hidden ROLxxx technical IDs never drive users, permissions, MFA or approvals.
                    • Makes the public registration role configurable from Role Management instead of hard-coding SALES, while preserving SALES as the upgrade-safe default.
                    • Keeps new roles dynamic: create PURCHASE (or any future role) in Master Data → Role, grant its permissions in Permission Matrix, then assign it in User Access with no Java change.
                    • Adds compatibility resolution for old GENxxx imports/support lookups and prevents new GENxxx Master codes from being created.
                    """;
        }
        if ("9.0.45".equals(version)) {
            return """
                    DSE ERP 9.0.45

                    • Rebuilds Reporting around a table-first workspace: compact report identity, filters and result controls leave the majority of the screen to report data.
                    • Gives Report Center a full-width responsive catalog with report-specific semantic icons instead of a tall empty category rail.
                    • Restores icon + visible text Actions across register/table rows and hardens dynamic action-column measurement against clipping.
                    • Keeps Bank Statement's eight KPI cards in a single desktop row while retaining shared responsive KPI behavior elsewhere.
                    • Aligns Reporting field labels, headers and controls with the colorful semantic icon system and improves light/dark control contrast without adding extra stylesheets.
                    """;
        }
        if ("9.0.44".equals(version)) {
            return """
                    DSE ERP 9.0.44

                    • Completes the Reporting and Shortcut Settings replacement rebuild while retaining the existing JavaFX/Spring/JPA/PostgreSQL architecture and exactly two runtime themes.
                    • Keeps Role Master security identity on lookup_value, hides generated ROLxxx identifiers from the user-facing role code, and refreshes role-backed administration screens after Master changes.
                    • Fixes self-registration role loading to use the active SALES role safely and migrates Item registration lookups to immutable Master category codes.
                    • Removes confirmed orphan desktop screens/services and unreachable private methods while preserving shared API clients and active backend behavior.
                    • Hardens the current GitHub CI/release contracts for Java 25, FXML, two-theme CSS, reporting, shortcuts, Master data and PostgreSQL integration.
                    """;
        }
        if ("9.0.42".equals(version)) {
            return """
                    DSE ERP 9.0.42

                    • Restores the full IntelliJ runtime shell while keeping the latest 9.0.41 UI-stabilized application source as the code baseline.
                    • Keeps exactly two runtime CSS themes and removes legacy stylesheet ownership conflicts.
                    • Stabilizes dynamic TableView first paint and preserves icon + Actions text without clipping when drawers or side panels reduce table width.
                    • Cleans Reporting presentation, removes per-report KPI strips, keeps responsive dashboard KPI bands, standardizes search/action areas, and adds CSV to Dashboard export.
                    • Fixes Scheduled Reports so PDF + XLSX is normalized and generated together in the same scheduled run; individual PDF, XLSX and CSV outputs remain unchanged.
                    """;
        }
        if ("9.0.41".equals(version)) {
            return """
                    DSE ERP 9.0.41

                    • Preserves the approved 9.0.40 view, navigation, dialogs, confirmations, warnings and business workflows behind repeatable UI/behavior freeze audits.
                    • Consolidates runtime styling to exactly two canonical themes: light-theme.css and dark-theme.css.
                    • Adds the shared semantic icon/colour registry for field labels, TableView headers, KPI captions and business-state values.
                    • Makes KPI bands responsive so managed cards automatically share all available width when cards are added, hidden or removed.
                    • Adds one DynamicTableLayoutManager for every ERP TableView: header icon/text and live row content drive widths, saved views retain visibility/order only, and readable horizontal scrolling replaces fixed controller/FXML column widths.
                    • Keeps table row density visually compatible while removing Java row-height/resize-policy conflicts and enforcing exactly two CSS files in CI/release validation.
                    """;
        }
        if ("9.0.40".equals(version)) {
            return """
                    DSE ERP 9.0.40

                    • Corrects partial-return lifecycle accounting without rewriting historical Sale/Purchase payments, finance entries or bank allocations.
                    • Separates invoice Payment Status from Return Status and Refund Status; non-return transactions display N/A.
                    • Makes Sales, Purchase and Quotation Saved Views apply atomically with one final reload.
                    • Adds the approved Reporting hub with Dashboard, Report Center, Saved Reports and scheduler-ready architecture.
                    • Makes management exports return-aware and keeps approved Return values separate from original invoice payment history.
                    • Retains the pinned Maven Wrapper and production build tooling for reproducible 9.0.40 builds.
                    """;
        }
        if ("9.0.38".equals(version)) {
            return """
                    DSE ERP 9.0.38

                    • Promotes the release identity to 9.0.38 while retaining the approved 9.0.37 functionality and UI corrections.
                    • Fixes the GitHub Actions engineering-hardening audit path so CI and release validation execute the matching 9.0.38 contract.
                    • Preserves immediate committed Sales/Purchase workflows and the existing Quotation → Sale Order document and activity-history protections.
                    • Preserves Create Quotation Customer and Source loading through the established master/bootstrap paths.
                    """;
        }
        if ("9.0.34".equals(version)) {
            return """
                    DSE ERP 9.0.34

                    • Adds a pinned Maven Wrapper and Java 25 CI release gate, including a real PostgreSQL integration-test lane for the Sales Return/refund lifecycle.
                    • Adds structured desktop diagnostics and a support-safe Export Diagnostic Package action in Settings.
                    • Adds a session-scoped Master/reference cache with mutation and sign-in/sign-out invalidation to reduce repeated Customer/Supplier/Item/Lookup loads.
                    • Keeps Sales and Purchase transaction Save as the immediate committed workflow: normal approval/posting rules apply, with no new transaction Draft mode.
                    • Adds a reusable read-only Activity Timeline for Sales, Purchase and Quotation records.
                    • Adds shared register UI helpers, per-user register column visibility/width/order persistence, and Saved Views across Sales, Purchase and Quotation registers.
                    • Retains all 9.0.33 Return, financial-authority, Bank Statement workspace, Quotation bootstrap and Dashboard Global Search protections.
                    """;
        }
        if ("9.0.33".equals(version)) {
            return """
                    DSE ERP 9.0.33

                    • Fixes Create Quotation first-open Customer/Source loading through one server bootstrap response and aligns the full page to the same 14px outer gutter used by Sale/Purchase.
                    • Changes Bank/Expense register defaults and Reset to January 1 through today.
                    • Rebuilds Bank Statement Browse Statements as an independent right overlay so transaction-table minimum widths can no longer squeeze History to ~300px.
                    • Stops operational screens from automatically focusing Search when users navigate to them; their table/work area receives neutral focus instead. Dashboard Global Search is unchanged.
                    • Dashboard Quick Actions now open the actual Create/Add workflows: Sale, Purchase, Customer, Quotation, Item Master, Supplier, Bank Entry and Expense Entry.
                    • Retains the 9.0.32 register-search cosmetic work, Payment Due column behavior, Return lifecycle, Master numbering and financial-authority corrections.
                    """;
        }
        if ("9.0.31".equals(version)) {
            return """
                    DSE ERP 9.0.31

                    • Separates Payment Status from Payment Due in Sales and Purchase registers so Return Pending/Partial/Paid never reuse due-date wording.
                    • Keeps overdue timing exclusively in Payment Due and removes OVERDUE from the Payment Status filter.
                    • Fixes Bank Statement Browse Statements sizing at the real controller authority: the history workspace now receives roughly 52–60% of available width instead of being snapped back to 520–680 px.
                    • Preserves Dashboard Global Search and all 9.0.30 Return, Master, financial-authority, register-search and sidebar behavior.
                    """;
        }
        if ("9.0.30".equals(version)) {
            return """
                    DSE ERP 9.0.30

                    • Moves Sales, Purchase and Quotation register search into each page header immediately before its primary New action while leaving Dashboard Global Search untouched.
                    • Aligns visible register filters, adds colorful quick-date controls and Payment Due support for Purchase, and keeps Advanced Filters hidden.
                    • Cleans Sales/Purchase Return side-panel actions, adds lifecycle-aware Approve/Reject controls, and preserves PDF/email actions in the row action menu.
                    • Aligns Bank/Expense Search, Type and From/To Date filters with visible refresh feedback.
                    • Fixes sidebar hide/show workspace reflow and gives Bank Statement History an adaptive, usable right-side width.
                    • Retains the 9.0.28 financial authority/integrity, 9.0.27 sidebar shortcut, 9.0.26 Master numbering and 9.0.25 Return lifecycle corrections.
                    """;
        }
        if ("9.0.28".equals(version)) {
            return """
                    DSE ERP 9.0.28

                    • Adds a global Show / Hide Sidebar control so users can reclaim the full workspace width on register and detail-heavy pages without changing page business logic.
                    • Adds Ctrl/Cmd + B as the default configurable sidebar shortcut in Keyboard Shortcuts, including while focus is inside normal search/text fields.
                    • Remembers sidebar visibility per signed-in desktop user and preserves the current navigation selection/submenu state when the sidebar is hidden and restored.
                    • Retains the complete 9.0.26 Master numbering correction and 9.0.25 Sales/Purchase Return lifecycle/register UI corrections.
                    """;
        }
        if ("9.0.26".equals(version)) {
            return """
                    DSE ERP 9.0.26

                    • Fixes Master Add numbering so Bank, Transporter, Payment Terms, Payment Mode, Expense Category, Charges and other Masters no longer share the generic GEN sequence.
                    • Keys Master numbering to immutable category_code, so renaming a Master category does not reset or fork its sequence.
                    • Keeps the server authoritative for final allocation while the Add dialog previews the same next Master-specific code.
                    • Preserves all existing GENxxx historical IDs; only new Master values use the corrected category-specific numbering.
                    • Retains the complete 9.0.25 Sales/Purchase Return lifecycle and register UI corrections.
                    """;
        }
        if ("9.0.25".equals(version)) {
            return """
                    DSE ERP 9.0.25

                    • Refactors Quotation Source to the same generic Master Data lookup path used by Transporter, Brand, Category and other Master-backed fields.
                    • Removes quotation-only Source fallback, alias scanning, request-time seeding and special runtime Master logic.
                    • Desktop Quotation create/edit and register filters now read QUOTATION_SOURCE through LookupService.getValuesByCategoryCode(), matching other Master controls.
                    • Adds a one-time compatibility migration that moves historical SOURCE/Quotation Source values into the canonical QUOTATION_SOURCE category; future runtime reads are generic only.
                    • Keeps /api/quotations/sources as a backward-compatible mobile endpoint that delegates to the same generic Master service.
                    """;
        }
        if ("9.0.21".equals(version)) {
            return """
                    DSE ERP 9.0.21

                    • Assures Quotation Source Master values on the /api/quotations/sources request itself instead of relying only on startup ordering.
                    • Refreshes Quotation Source whenever the editor dropdown is opened, so newly added Master values and delayed server readiness are reflected immediately.
                    • Logs the resolved Quotation Source category/value count on the server for precise runtime diagnostics and never silently returns an empty list after assurance.
                    • Fixes the confirmed Dashboard cash-position SQL ambiguity by qualifying return_refund.amount in joined refund queries.
                    • Keeps the existing quotation REST paths/DTOs backward-compatible with current iOS and Android clients and preserves unrelated business behavior.
                    """;
        }
        if ("9.0.20".equals(version)) {
            return """
                    DSE ERP 9.0.20

                    • Fixes the remaining Quotation Source runtime gap when user-maintained Master categories are named Lead Source, Sales Source, Customer Source or another source-like alias.
                    • Ensures a generic SOURCE category can no longer suppress startup assurance for the canonical QUOTATION_SOURCE category.
                    • Keeps Quotation Source fully Master-driven, prefers the canonical category, and uses one best active source-like Master group without mixing unrelated values.
                    • Keeps the existing /api/quotations/sources REST contract unchanged for desktop, iOS and Android clients.
                    • Preserves all v9.0.19 Sales, Purchase, PDF/email, banking and unrelated business behavior.
                    """;
        }
        if ("9.0.19".equals(version)) {
            return """
                    DSE ERP 9.0.19

                    • Fixes Quotation Source loading when Master category codes differ only by case/spacing or values live under the historical SOURCE category.
                    • Keeps MasterDataService as the single runtime authority and copies legacy SOURCE values into the canonical Quotation Source category without deleting the original Master data.
                    • Keeps the existing /api/quotations/sources REST contract unchanged for desktop, iOS and Android clients.
                    • Preserves all v9.0.18 Sales, Purchase, PDF/email, banking and other unrelated business behavior.
                    """;
        }
        if ("9.0.18".equals(version)) {
            return """
                    DSE ERP 9.0.18

                    • Makes Quotation Source use the canonical Master Data category-code service for both dropdown choices and server validation.
                    • Repairs lookup values written under historical/category-code lookup types and prevents the desktop Master Import from recreating that mismatch.
                    • Preserves a quotation's saved historical Source visibly during edit instead of showing a blank field when that value is no longer active.
                    • Keeps all existing REST endpoint paths and request/response contracts backward-compatible with the current iOS and Android mobile apps.
                    • Preserves v9.0.17 Sales, Purchase, PDF/email, banking and other unrelated business behavior.
                    """;
        }
        if ("9.0.17".equals(version)) {
            return """
                    DSE ERP 9.0.17

                    • Moves IntelliJ development-server execution outside Maven target so Windows clean/rebuild is never blocked by the running backend JAR.
                    • Cleans orphaned project-owned development backends safely while leaving active and packaged/shared servers untouched.
                    • Hardens Hikari/PostgreSQL connection lifetime, keepalive, validation and idle-pool behavior for restart/recovery stability.
                    • Preserves all v9.0.16 business calculations, Sales/Purchase flows, document generation and production behavior.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.17.
                    """;
        }
        if ("9.0.16".equals(version)) {
            return """
                    DSE ERP 9.0.16

                    • Loads Quotation Source from historical and canonical Master Data category spellings so existing source values are selectable.
                    • Moves Open Sale beneath Convert to Sale in Quotation quick actions.
                    • Deep-links converted Quotations to the exact Sales Register invoice and visibly highlights the linked row.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.16.
                    """;
        }
        if ("9.0.15".equals(version)) {
            return """
                    DSE ERP 9.0.15

                    • Makes configured XX/XXX/XXXX reference widths permanent minimum padding: sequences expand automatically beyond 99/999/9999 instead of blocking record creation.
                    • Keeps Item Master save feedback visible on the parent screen and treats notification delivery as secondary to the authoritative Item save.
                    • Makes Data Import Step 4 policies explicit by module and locks the policy while an import is running.
                    • Loads Quotation Source through the Quotation API from active Master Data values, including compatibility with historical category-code storage.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.15.
                    """;
        }
        if ("9.0.14".equals(version)) {
            return """
                    DSE ERP 9.0.14

                    • Reworks Quotation Create/Edit into a Sale-style workspace with Master-driven Source, reliable item search, preserved transaction-line values and side-panel notes/attachments.
                    • Adds direct navigation from converted Quotations to the linked Sale and removes the side-panel WhatsApp action.
                    • Adds Sales/Purchase Document Status filtering without enlarging the register filter panels and corrects Purchase Total Items to distinct item codes rather than quantity sum.
                    • Preserves saved quantity/rate/discount/GST values while editing existing Sale, Purchase and Quotation lines.
                    • Treats successful no-op imports as completed rather than failed and fixes the Return Refund ScreenRefreshPolicy compile import.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.14.
                    """;
        }
        if ("9.0.13".equals(version)) {
            return """
                    DSE ERP 9.0.13

                    • Refreshes Sales, Purchase and Return registers immediately after successful business mutations rather than waiting for cache expiry.
                    • Aligns Sales/Purchase register KPI values with the active filters and clarifies outstanding values as Pending / Total Pending.
                    • Separates successful imports from post-save warnings so committed records are never reported as failed only because an attachment step failed.
                    • Makes Purchase Recon bank references, Permission Matrix Special values and Bank Statement History easier to read and act on.
                    • Adds keyboard-first login and securely encrypted Remember Me password storage tied to the DSE ERP installation/user key.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.13.
                    """;
        }
        if ("9.0.12".equals(version)) {
            return """
                    DSE ERP 9.0.12

                    • Preserves the complete v9.0.11 corrective and 20-defect scope.
                    • Normalizes historical blank Sales GST mode so View/Download Excel and document previews remain compatible with older invoices.
                    • Refreshes Sales and Purchase registers automatically after payment create/update so PAID/PARTIAL status is visible immediately on return.
                    • Converts known technical exception strings into user-facing ERP messages while retaining technical detail in logs.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.12.
                    """;
        }
        if ("9.0.11".equals(version)) {
            return """
                    DSE ERP 9.0.11

                    • Preserves the complete v9.0.10 / 20-defect corrective scope.
                    • Fixes Sale/Purchase startup tax-mode initialization so screens do not fail before GST masters finish loading.
                    • Repairs Finance runtime schema prerequisites used by Bank Entry and Expense Entry and keeps technical API failures out of normal user dialogs.
                    • Makes Permission Matrix special permissions readable, constrains Keyboard Shortcuts to a scrollable screen-safe dialog and improves PDF Studio toolbar/mapping usability without changing production PDF generation.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.11.
                    """;
        }
        if ("9.0.10".equals(version)) {
            return """
                    DSE ERP 9.0.10

                    • Preserves the full v9.0.9 consolidated 20-defect corrective scope.
                    • Fixes Purchase Recon compilation by keeping imported Other Adjustment at the defined zero value.
                    • Rejects stale IntelliJ development-server cache JARs whose manifest version does not match the current runtime contract.
                    • Synchronizes desktop, server, runtime, bootstrap and update release identity to 9.0.10.
                    """;
        }
        if ("9.0.9".equals(version)) {
            return """
                    DSE ERP 9.0.9

                    • Corrects server build completeness and DTO aggregation for Sales and Purchases.
                    • Hardens Bank reconciliation, business email, PDF Studio, reminders, communications, Item Master and duplicate-Sale permissions.
                    • Preserves Purchase supplier/item snapshots and protects reconciled Finance entries.
                    • Fixes Purchase import rerun identity, reference-format validation, legacy-date reporting, WhatsApp status persistence and dashboard cash calculations.
                    • Adds last-admin, party-master, category-rename and quotation-action integrity safeguards.
                    """;
        }
        if ("9.0.8".equals(version)) {
            return """
                    DSE ERP 9.0.8

                    • PDF Studio rebuilt as a non-destructive object-driven designer with Design, Data Preview and Final PDF modes.
                    • Uploaded PDF files remain protected; imported text, images and vector regions can be mapped, moved, styled, hidden or replaced through Studio overlays.
                    • Draft, preview and Publish are isolated from production document generation. Only explicit Mark as Default activation creates the runtime snapshot.
                    • Published candidates and active runtime snapshots are physically separated, so later editing or publishing cannot silently change Sales/PDF/Print/Preview/Email output.
                    • Existing Sales invoice and PDF/email generation entry points remain unchanged.
                    • Desktop and Spring Boot runtime version/build contracts are synchronized to 9.0.8.
                    """;
        }
        if ("9.0.7".equals(version)) {
            return """
                    DSE ERP 9.0.7
                    • Reference previews are now read-only; authoritative master-format references are allocated only inside the transaction that saves the record, preventing unsaved forms from consuming numbers.
                    • Manual Customer, Supplier, Item and Master IDs are finalized by the server on Save while explicit import reference codes remain preserved.
                    • Bank Statement is now available directly from Data Import and continues to use the existing bank-statement CSV import engine.
                    • Data Import readiness now uses one validated state: file, mapping, module or import-mode changes invalidate preflight, and a successful preflight enables the bottom Import Records action reliably.
                    • Desktop and Spring Boot runtime version/build contracts are synchronized to 9.0.7.
                    """.strip();
        }
        if ("9.0.6".equals(version)) {
            return """
                    DSE ERP 9.0.6
                    • Hardened server trust boundaries for Sales, Purchase, Returns and Quotations so payment state, lifecycle, totals, tax mode and return values are validated or calculated server-side.
                    • Strengthened quotation, registration, support-mutation, backup/restore and SMTP-secret security controls.
                    • Added post-rounding Bank allocation validation, strict bank debit/credit direction, non-negative reconciliation/finance/item validations and reserved-stock enforcement.
                    • Corrected reporting for payment dates, returns, opening balances, filters, full-dataset KPIs, gross profit and historical inventory costing.
                    • Added immutable Sales invoice party/item snapshots, paise-accurate invoice totals/tax splits/amount-in-words and safer reference numbering.
                    • Added the 9.0.6 business-integrity database migration and CI contract while preserving existing bank-reconciled payment and return-refund protections.
                    • Desktop and Spring Boot runtime version/build contracts are synchronized to 9.0.6.
                    """.strip();
        }
        if ("9.0.5".equals(version)) {
            return """
                    DSE ERP 9.0.5
                    • Match Transaction no longer preselects low-confidence candidates and now separates bank allocation from document settlement/residual status.
                    • Purchase Recon and Bank/Expense linked navigation now deep-links to the exact Bank Statement transaction, with direct Bank Statement access from Purchase Recon.
                    • Normal register/master/history/admin row selection uses the shared purple palette instead of legacy blue selection.
                    • Purchase Return loads reliably on entry, defaults to all records and auto-applies search/date/supplier/status filters without an Apply Filters button.
                    • Bank Statement History is a wider resizable split workspace and its Bank Account filter/import ownership comes from BANK ACCOUNT Master Data.
                    • Bank/Expense Add/Edit dialogs use a balanced two-column workspace so available modal width is used cleanly.
                    • Desktop and Spring Boot runtime version/build contracts are synchronized to 9.0.5.
                    """.strip();
        }
        if ("9.0.4".equals(version)) {
            return """
                    DSE ERP 9.0.4
                    • Bank Statement suggestions now use the locked 50/45/5 confidence model: exact amount, useful party token and ±7-day date proximity.
                    • Bank Statement imports can be permanently deleted with permission-protected double confirmation and transactional rollback of linked reconciliation effects.
                    • Added explicit configurable Bank Match round-off handling so small final residuals can settle Sales, Purchase, Purchase Recon and Returns without changing the actual bank amount.
                    • Reconciliation reversal restores both the bank allocation and any round-off adjustment.
                    • Desktop and Spring Boot runtime version/build contracts are synchronized to 9.0.4.
                    """.strip();
        }
        if ("9.0.3".equals(version)) {
            return """
                    DSE ERP 9.0.3
                    • Separated Import Data Preview from Validation Results so mapping previews remain visible and unchanged during preflight checks.
                    • Added safe multi-sheet Purchase Recon import with source sheet/row traceability, supplier reuse, create/update/already-current decisions and bank-linked conflict protection.
                    • Made Purchase Recon workbook fingerprints audit history instead of a permanent re-import blocker while preserving business-key duplicate safety.
                    • Replaced the unbounded Bank Statement import selector with recent statements plus paginated, searchable statement history.
                    • Exact/overlapping Bank Statement re-imports now reuse or skip existing transactions without overwriting reconciled bank data.
                    • Desktop and Spring Boot runtime version/build contracts are synchronized to 9.0.3.
                    """.strip();
        }
        if ("9.0.2".equals(version)) {
            return """
                    DSE ERP 9.0.2
                    • Standardized record viewing across Finance, Reconciliation and master registers: row selection opens a read-only right-side details drawer while New/Edit remain explicit form actions.
                    • Added the shared RegisterDetailDrawer contract to Bank Entry, Expense Entry, Purchase Recon, Recon Supplier, Customers, Suppliers, Item Master, Inventory, User Access and Master Data.
                    • Removed hidden double-click-to-edit behavior from standardized registers and Reminder Center so record selection never silently enters edit mode.
                    • Extended global semantic field decoration to finance labels and all standard FXML/OwnedDialog form surfaces for consistent icons and colors in light and dark themes.
                    • Added a release/CI UI consistency gate so future ordinary register screens and dialogs cannot silently bypass the shared interaction and presentation contract.
                    """.strip();
        }
        if ("9.0.1".equals(version)) {
            return """
                    DSE ERP 9.0.1
                    • Hardened Data Import so server-side validation and real persistence failures are surfaced instead of being masked as successful validation or duplicate skips.
                    • Fixed User Permission checkbox editing and improved scalable Customer, Supplier and Recon Supplier search.
                    • Reworked Bank/Expense, Purchase Recon and Recon Supplier workspaces around full-width tables, centralized dialogs, semantic Actions menus and purple row selection.
                    • Payment and refund screens now default to Full payment/refund and use the shared colorful page identity icons.
                    • Purchase Recon bank matching records the trader/supplier name as the Bank Entry description for direct register recognition.
                    • Improved Purchase create layout, Quotation/Return register styling and Keyboard Shortcut readability/toggles/icons.
                    """.strip();
        }
        if ("9.0.0".equals(version)) {
            return """
                    DSE ERP 9.0.0
                    • Phase 1 safety foundation separates cancellable background reads from reliable non-cancellable save/action work.
                    • Bank Statement imports, matching, reversals and other financial actions now use a reliable serialized client action queue rather than latest-result-wins execution.
                    • Duplicate in-flight actions are rejected explicitly, and queue saturation now reports a failure instead of silently discarding a requested action.
                    • Server-managed document, return, payment-proof and refund-proof attachments now enforce separate view/edit permissions and only resolve files inside the managed Attachments workspace.
                    • Removed the legacy raw payment attachment-path update route so payment proofs must use managed server upload/download storage.
                    • Existing Sales, Purchase, Purchase Recon, Bank Statement business calculations and UI layouts remain unchanged in Phase 1.
                    • Phase 2 moves high-impact Purchase, Inventory, Operations, Master Data, payment, return, quotation, reminder and access-management reads off the JavaFX Application Thread.
                    • Create/edit master dialogs now load lookups and generated references asynchronously; edit screens preserve their existing identifiers instead of allocating a new create reference during background bootstrap.
                    • Master Data search is debounced, stale read work is cancelled when supported screens are hidden, and reliable Phase 1 action execution is used for the migrated save/update/delete operations.
                    • Purchase Select From PO now loads draft orders and the selected draft asynchronously, while existing calculations, server APIs, database schema, FXML and CSS remain unchanged in Phase 2.
                    • Phase 3 adds true server-side paging, filtering and totals for Sales, Purchase, Finance/Expense, Sales Returns, Purchase Returns, Quotations and Purchase Recon registers; Bank Statement keeps its existing server-paged implementation.
                    • Full-register exports now fetch every record matching the active server filters rather than exporting only the visible page, while export work remains off the JavaFX Application Thread.
                    • Linked Finance records use direct record lookup, and document reference allocation now uses the central reference counter without rescanning historical documents after the counter scope has been initialized.
                    • Phase 4 consolidates repeated register paging state, page navigation and row/detail-drawer interaction into shared JavaFX utilities used by Sales, Purchase, Returns, Quotations and Purchase Recon without changing their Phase 3 server queries.
                    • Attachment preview materialisation is now shared across Sales, Purchase, payment, quotation, refund and Purchase Recon workflows; remote preview/count reads stay off the JavaFX Application Thread.
                    • Phase 4 is a desktop maintainability refactor only: existing business calculations, Spring/Hibernate services, PostgreSQL schema, FXML layouts, CSS and document templates remain unchanged.
                    • Phase 5 adds optimistic multi-user edit protection to Sales, Purchase, Finance, Party, Item, Master Data, Recon Supplier and Purchase Recon records using server-owned row versions and HTTP 409 conflict responses instead of silent overwrites.
                    • Critical payment, return and attachment mutations advance the owning Sales/Purchase version so already-open edit screens detect that another user changed the record.
                    • Server-side mutation permissions and audit logging are strengthened across core document, finance, return, inventory, master-data and Purchase Recon workflows; recorded actors come from the authenticated server session rather than client-supplied usernames.
                    • Phase 5 registers and verifies the 9.0.0 row-version migration at server startup while preserving the existing pessimistic locks for financial and stock-sensitive operations.
                    • Phase 6 standardizes operational loading, empty and load-error states across the main Sales, Purchase, Return, Quotation, Banking, Inventory, Master and Purchase Recon workspaces using the existing Phase 5 design classes; all seven CSS files and FXML layouts remain unchanged.
                    • Register search fields receive consistent keyboard focus, and existing detail drawers on supported registers can be dismissed with Escape without changing their layouts or actions.
                    • Customer/Supplier Master loading and Bank/Expense KPI loading now use the existing background-read executor, closing the last identified UI-thread network waits without changing APIs or business calculations.
                    """.strip();
        }
        if ("8.5.8".equals(version)) {
            return """
                    DSE ERP 8.5.8
                    • Added the isolated Recon Supplier master and Purchase Recon register without changing the existing Supplier Master or normal Purchase workflow.
                    • Added manual Purchase Recon entry, server-managed attachments, configurable RSP/PRC references and Purchase Recon import with automatic missing Recon Supplier creation.
                    • Integrated Purchase Recon into Bank Statement matching, partial/reconciled status tracking, generated Bank Entry linkage and reverse/unmatch behavior.
                    • Added dedicated permission-matrix capabilities for Recon Supplier and Purchase Recon, preserving single-user local-server and multi-user shared-server operation.
                    • Hardened import handling for normalized supplier-name matching, business duplicates, tax-review warnings and spreadsheet summary/total rows.
                    """.strip();
        }
        if ("8.5.7".equals(version)) {
            return """
                    DSE ERP 8.5.7
                    • Fixed the GitHub final data-architecture gate by moving effective-permission persistence out of AuthController into PermissionAuthorityService.
                    • REST authentication controllers now delegate permission reads through the service layer while preserving the complete v8.5.5/v8.5.6 custom-role permission behavior.
                    • Revalidated all source architecture audits and the JavaFX UI source contract used by GitHub CI.
                    """.strip();
        }
        if ("8.5.6".equals(version)) {
            return """
                    DSE ERP 8.5.6
                    • Corrected desktop compile regressions in Sales, Purchase and Shortcut Manager introduced during the v8.5.5 polish.
                    • Preserves the complete v8.5.5 User Access, custom-role and effective-permissions model unchanged.
                    """.strip();
        }
        if ("8.5.5".equals(version)) {
            return """
                    DSE ERP 8.5.5
                    • Reworked User Access so saved Role Master permissions are effective for every signed-in role, including custom and Purchase roles.
                    • Added a signed-in effective-permissions API so non-Admin users no longer fall back to legacy SALES/MANAGER permission defaults.
                    • Spring Security now receives saved permission authorities and protects core Sales, Purchase, Finance, Inventory, Reports and User Access operations by capability.
                    • Added a first-class Purchase role with safe baseline Purchase/Supplier/Inventory access; administrators can further customize it in Permission Matrix.
                    • Hardened permission saving against duplicate/stale permission rows and repaired legacy role_permission schema constraints that could surface HTTP 409 conflicts.
                    • User role changes now invalidate stale sessions, and duplicate username/email conflicts return clear field-level messages instead of generic 400/409 errors.
                    • Replaced legacy SALES-only server checks in payments, returns, supplier access and support navigation with saved module permissions.
                    """.strip();
        }
        if ("8.5.4".equals(version)) {
            return """
                    DSE ERP 8.5.4
                    • Removed the legacy standalone Payment History screen and routed payment search/notifications to the current Sales or Purchase payment workspace.
                    • Global Search result details now use a wrapped, padded detail rail so dates, amounts, statuses and separators remain fully readable.
                    • Notification rows/details now use roomier wrapping, current order-status wording and reliable exact-record opening for legacy and new linked records.
                    • Shortcut Manager now exposes only Application Actions, Quick Create and Navigation, with three full-height columns that use the available workspace.
                    • Existing v8.5.3 Global Search and Notification Center semantic coloring/polish remains preserved.
                    """.strip();
        }
        if ("8.5.3".equals(version)) {
            return """
                    DSE ERP 8.5.3
                    • Global Search typography is larger and clearer across the search bar, module rail, result groups and values.
                    • Search result totals are split into colorful Visible, Total Results and Modules summary chips, with complete module/value semantic coloring.
                    • Notification Center typography is larger in the list, filters and details panel, with full date + time centered in each notification row.
                    • Generic “Notification” titles are suppressed; informational unread items show NEW while ACTION appears only for genuine workflow actions.
                    • Clear History is restored with confirmation, while exact-record Search and Notification navigation from v8.5.2 remains unchanged.
                    • Existing Role Master and Shortcut Manager corrections remain preserved.
                    """.strip();
        }
        if ("8.5.1".equals(version)) {
            return """
                    DSE ERP 8.5.1
                    • ROLE Master Value is now the only role identity used by login, users, permissions, MFA and approvals; generated ROLxxx Master IDs are informational only.
                    • Role matching is case-insensitive, custom roles such as Purchase remain independent, and role renames cascade safely to assigned users and permissions.
                    • Shortcut Manager now uses fixed preview cards plus a virtualized full-height list, so large categories never break the layout.
                    • The Add/Edit drawer exposes the full permitted ERP action catalog, configurable Applies To scope, real Advanced options and isolated shortcut-only CSS.
                    • Existing production security, approval, search, notification and runtime protections remain preserved.
                    """;
        }
        if ("8.5.0".equals(version)) {
            return """
                    DSE ERP 8.5.0

                    User Access & Sign-In
                    • Login and User Access now use the active server Role Master as the canonical role source, removing Sale/SALES mismatches.
                    • New User includes Password and Confirm Password with embedded eye controls.
                    • Admin is exempt from login OTP by policy; every other active role is server-enforced for password + OTP/MFA sign-in.

                    Approval Workflow
                    • New Sales and Purchases created by non-Admin users are submitted as Pending Approval.
                    • Pending documents do not post inventory and cannot receive payments, returns or bank reconciliation until Admin approval.
                    • Admin approval/rejection is server-owned and surfaced through exact-record notifications and register actions.

                    Global Search & Notifications
                    • Rebuilt Global Search as the approved full workspace with module counts, all permitted matches and exact-record navigation.
                    • Rebuilt Notification Center with category filters, action-needed visibility, read controls and exact linked-record navigation.

                    Sales Register
                    • Rebuilt the Sales detail drawer using the stable Purchase-style scrollable layout while retaining semantic field icons.
                    • Shipping address correctly falls back to Billing Address when Same as Billing is selected.

                    Keyboard Shortcuts
                    • Reworked the user-specific Shortcut Manager into one compact desktop sheet.
                    • Application-wide Save, Edit, Refresh, New, Open, Delete, Print, Export and Back commands remain configurable; New Sale defaults to F9.

                    Reliability
                    • Preserved the JavaFX password-reveal reparenting fix and the transactional SMTP Spring proxy/startup protection.
                    """.strip();
        }
        if ("8.4.8".equals(version)) {
            return """
                    DSE ERP 8.4.8

                    Startup UI
                    • Fixed light-mode splash startup title and description contrast on the white content card.

                    Bank Statement
                    • Removed the right-side reconciliation process/status guide panel.
                    • Reclaimed that width for the search, filters and transaction table so more bank data is visible at once.
                    """.strip();
        }
        if ("8.4.7".equals(version)) {
            return """
                    DSE ERP 8.4.7

                    Startup Reliability
                    • Fixed Spring Boot startup failure caused by the transactional SMTP service being declared final.
                    • Kept SMTP settings transactional while restoring Spring AOP/CGLIB proxy compatibility.

                    Maintenance
                    • Added a regression test to prevent the transactional SMTP service from becoming non-proxyable again.
                    """.strip();
        }
        if ("8.4.6".equals(version)) {
            return """
                    DSE ERP 8.4.6

                    Permissions
                    • Rebuilt the Permission Matrix into one compact role workspace with module rows and capability columns.
                    • Added role templates, copy-from-role, search, granted-only filtering and an effective-access preview.
                    • Added tri-state row and column controls so bulk grants remain clear without a page-level scroll.
                    • Preserved module-specific capabilities through a dedicated Special menu instead of hiding uncommon permissions.
                    • Kept permission persistence server-owned so LOCAL and company-server deployments use the same role matrix.

                    Maintenance
                    • Added shared permission catalog metadata so future modules and capabilities can be surfaced without adding FXML checkboxes for every permission.
                    • Scoped Permission Matrix styling to the workspace to avoid cascading changes into unrelated screens.
                    """.strip();
        }
        if ("8.4.5".equals(version)) {
            return """
                    DSE ERP 8.4.5

                    Security
                    • Added five-attempt password lockout with clear attempt counters and automatic account locking.
                    • Added five-attempt MFA verification lockout across sign-in challenges.
                    • Added explicit lock reasons so automatic security locks remain distinct from administrator locks.
                    • Verified password reset can clear automatic security locks, while administrator locks remain protected.
                    • Administrator unlock now clears failed-attempt counters and security lock state.

                    Audit
                    • Added security audit events for failed sign-in attempts, MFA failures, automatic locks and administrator lock/unlock actions.
                    """.strip();
        }
        if ("8.4.4".equals(version)) {
            return """
                    DSE ERP 8.4.4

                    Security
                    • Added real server-enforced multi-factor sign-in for accounts with MFA enabled.
                    • Aligned MFA settings between administrator-created and self-registered accounts.

                    Multi-User
                    • Strengthened shared-server Backup & Restore and simultaneous reference allocation.
                    • Added safer standalone/company-server deployment transitions.

                    Backup & Recovery
                    • Corrected database-size reporting to use the connected PostgreSQL database.
                    • Added clearer confirmations and warnings for backup and restore actions.

                    Email
                    • Unified company-server SMTP settings and simplified App Password visibility controls.

                    Excel Studio
                    • Improved merged/border property visibility and semantic color consistency.

                    Updates
                    • Added an in-app What's New view so release changes remain easy to review.
                    """.strip();
        }
        return "DSE ERP " + version + "\n\nRelease notes are unavailable offline for this version.";
    }

    public static String resolve(String version, String onlineNotes) {
        String fallback = forVersion(version);
        String online = onlineNotes == null ? "" : onlineNotes.strip();
        long meaningfulLines = online.lines().map(String::strip).filter(s -> !s.isBlank()).count();
        if (online.isBlank() || meaningfulLines < 3) return fallback;
        return online;
    }

}
