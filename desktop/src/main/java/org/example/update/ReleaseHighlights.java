package org.example.update;

/**
 * Small offline fallback for the in-app What's New dialog. The published
 * GitHub Release body remains the authoritative release notes when online.
 */
public final class ReleaseHighlights {
    private ReleaseHighlights() { }

    public static String forVersion(String version) {
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
