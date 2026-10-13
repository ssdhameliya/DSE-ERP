# DSE ERP 10.0.32 Release Notes

## Overview
DSE ERP 10.0.32 resolves the critical public user self-registration and password reset OTP failure caused by read-only transaction flags, hardens authentication exception reporting, and includes QuickBooks-style workflow focus tabbing and high-visibility field tracking.

## Key Highlights

### 1. Authentication Transaction & Self-Registration Read-Write Fix
- **Self-Registration OTP Generation:** Resolved `PSQLException: ERROR: cannot execute UPDATE in a read-only transaction` by removing `readOnly = true` from `@Transactional` on `requestRegistrationOtp` in `AuthService`. Both `cleanupExpiredRegistrations()` (`UPDATE registration_request`) and `otp.issue()` (`DELETE`/`INSERT auth_challenge`) now execute within a read-write transaction boundary.
- **Password Reset & MFA Resend Fixes:** Removed `readOnly = true` from `requestPasswordReset` and `resendLoginMfa` to ensure OTP tokens and challenges can be written to PostgreSQL without constraint or transaction errors.
- **Exception Reporting Transparency:** Updated `AuthApiExceptionHandler` to return specific exception messages instead of masking unexpected errors behind a generic `"The request could not be completed"` response.
- **Automated Security Contract Tests:** Added dedicated regression contract tests in `PreAuthMfaSecurityContractTest` and end-to-end scenario tests in `AuthMfaEnrollmentScenarioTest` verifying all pre-authentication challenge issuance methods execute in read-write transactions.

### 2. QuickBooks-Style Workflow Focus Tabbing & High-Visibility Field Tracking
- **Sequential Focus Flow:** Pressing Enter advances focus sequentially through logical transaction fields (Search → Date → Account/Party → Line Items → Quantities/Rates → Notes → Save) across Sale, Purchase, Quotation, and Sales Register.
- **Universal Auto-Select on Focus:** Every text field, numeric input, currency cell, and combobox editor automatically highlights all text on focus for rapid one-stroke overwrites.
- **High-Visibility Cursor Outline:** Active inputs are outlined with a high-contrast 2px border, ambient glow, and background tint in both Light and Dark themes.
- **Rapid Line Addition Loop:** Submitting a line item automatically posts the entry and returns focus to Item Search for high-speed barcode scanning.
- **Reclaimed Workstation Screen Space:** Removed obsolete hint bars from Sale, Purchase, and Quotation pages, reclaiming 35 to 45 vertical pixels directly for invoice table rows.

### 3. Web Admin Studio Hardening & Cache Sync
- **Registration Form Safety:** Added pre-flight validation ensuring CAPTCHA challenge tokens are loaded prior to form dispatch and provided fallback defaults for registration roles.
- **Cache-Busting Update:** Bounded all web script assets to cache version `v10.0.31.9` ensuring all clients receive updated registration and header handlers immediately.
