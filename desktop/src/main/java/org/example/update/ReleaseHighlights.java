package org.example.update;

/**
 * Small offline fallback for the in-app What's New dialog. The published
 * GitHub Release body remains the authoritative release notes when online.
 */
public final class ReleaseHighlights {
    private ReleaseHighlights() { }

    public static String forVersion(String version) {
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
}
