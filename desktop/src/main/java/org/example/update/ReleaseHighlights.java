package org.example.update;

/**
 * Small offline fallback for the in-app What's New dialog. The published
 * GitHub Release body remains the authoritative release notes when online.
 */
public final class ReleaseHighlights {
    private ReleaseHighlights() { }

    public static String forVersion(String version) {
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
