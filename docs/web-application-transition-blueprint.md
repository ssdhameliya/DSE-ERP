# DSE ERP — Universal Web Application Transition Blueprint
**Document Status:** 📋 Active Specification & Requirement Gathering (No Code Implementation Until Confirmed)  
**Target Architecture:** Dual-Portal Web Platform (Public Worldwide Business Showcase + Web ERP Admin Panel)  
**Host Environment:** Existing Oracle Cloud Always Free Tier (200GB Storage) + Hostinger DNS  
**Version Alignment:** DSE ERP Server & Shared Contracts v10.0.32  

---

## 1. Executive Vision & Core Objectives

The objective is to expand DSE ERP from a localized desktop-only system into a modern, 24/7 globally accessible web platform comprising two interconnected portals driven by a single unified PostgreSQL database:

```
                                      GLOBAL INTERNET
                                             │
                      ┌──────────────────────┴──────────────────────┐
                      ▼                                             ▼
          [ 1. Public Worldwide Portal ]               [ 2. Web ERP Admin Studio ]
          (Customers, Prospects, Global Market)        (Internal Management & Accounts)
          Domain: www.yourcompany.com                  Domain: erp.yourcompany.com
          ─────────────────────────────────────        ─────────────────────────────────────
          • Worldwide Business & Machine Showcase      • 100% Parity with Desktop ERP
          • Live Product & Material Catalog            • Sales Invoicing, Challans & Credit Notes
          • Online Request for Quotation (RFQ)         • Purchase Inward, Material POs & Debit Notes
          • Customer Dispatch Tracking & QR Verification• Universal Barcode & Thermal Label Studio
          • Direct PDF Tax Invoice Download            • Full GST Compliance (GSTR-1, 3B, Recon)
          • Contact & Inquiry Management               • General Ledger, Banking & Balance Sheet
                      │                                             │
                      └──────────────────────┬──────────────────────┘
                                             │ HTTPS / REST API / JWT
                                             ▼
                       [ Existing Spring Boot REST Backend ]
                           (org.example.server - Port 8080)
                                             │
                                             ▼
                       [ PostgreSQL Relational Database ]
                           (Single Multi-User Source of Truth)
```

---

## 2. Infrastructure & Hosting Strategy ($0 Added Cost)

You already have the ideal cloud environment running. There is no need for any additional monthly cloud hosting expense.

### Current Cloud Assets & Allocation
* **Cloud Provider:** Oracle Cloud Infrastructure (OCI) Always Free Tier.
* **Storage Allocation:** 200 GB NVMe High-Performance Block Volume (plenty of room for 10+ years of attachments, backups, and invoices).
* **Compute Capacity:** Up to 4 Ampere A1 ARM OCPUs with 24 GB RAM (or 2 AMD instances).
* **Bandwidth:** 10 TB/month free outbound traffic.
* **IP Address:** Static Public Cloud IPv4.
* **DNS Manager:** Hostinger DNS Zone Management.
* **Security & SSL:** Automated Let's Encrypt SSL (`certbot`) with Nginx reverse proxy.

### Hostinger DNS Mapping Strategy

| Record Type | Host / Subdomain | Points To | Purpose |
| :---: | :--- | :--- | :--- |
| **A** | `@` / `www` | OCI Public IP | **Public Worldwide Business Showcase** |
| **A** | `erp` (or `app`) | OCI Public IP | **Secure Web ERP Admin Workspace** |
| **A** | `api` | OCI Public IP | **Production Backend Gateway** (Serves both Web & Desktop) |
| **A** | `api-uat` | OCI Public IP | **UAT Testing Gateway** |

### Concurrent Dual-Run Architecture (Desktop + Web)
* The **Desktop ERP** and the **Web ERP** connect to the **same Spring Boot API and PostgreSQL database**.
* Any transaction recorded on the web is instantaneously visible on the desktop, and vice versa.
* Zero migration risk: staff can continue using the desktop app while testing and adopting the web app at their own pace.

---

## 3. Detailed Portal Functional Specifications

### Portal A: Public Worldwide Business Showcase (`www.yourcompany.com`)
1. **Corporate & Engineering Portfolio:**
   * High-definition showcase of company infrastructure, manufacturing machinery, facility photos, and ISO/quality certifications.
   * About Us, management profile, client industries served, and global export capabilities.
2. **Interactive Product & Materials Catalog:**
   * Dynamic catalog linked to approved items from the ERP master data.
   * High-resolution product images, technical specifications, CAD/PDF spec sheet downloads, and HSN details.
3. **Smart RFQ (Request for Quotation) Ingestion:**
   * Prospective clients can select items, specify quantities and delivery requirements, and submit a quote request.
   * Submissions feed directly into the ERP `Quotation` review pipeline with automated email notifications.
4. **Customer Self-Service Tracking Bay:**
   * Secure lookup by Document Number (e.g., `JI/25-2026/0131`) or Dispatch Barcode.
   * Displays package count (e.g., Box 1 of 1), dispatch date, carrier details, and security seal verification.
   * Authorized clients can securely download signed PDF Tax Invoices without calling the office.
5. **SEO & International Performance:**
   * Server-side rendered (SSR) or pre-rendered for maximum Google Search indexing worldwide.
   * Multi-language ready (e.g., English, Gujarati, Hindi).

---

### Portal B: Secure Web ERP Admin Studio (`erp.yourcompany.com`)
Direct 1-to-1 feature parity with every module present in the current desktop application:

1. **Sales & Distribution Management:**
   * Tax Invoices, Delivery Challans, Quotations, Proforma Invoices, Credit Notes.
   * QuickBooks-style keyboard-first inline item grid (<kbd>Tab</kbd> / <kbd>Enter</kbd> row navigation, auto-calculating GST, Round-off).
   * Direct PDF rendering, emailing, and print dialogs.
2. **Procurement & Goods Inward Management:**
   * Purchase Orders, Goods Receipt Notes (GRN), Supplier Bills, Debit Notes.
   * Supplier 360 ledger profiles, outstanding aging analysis, payment recording.
3. **Universal Order Barcode & Shipping Studio:**
   * The newly standardized 5-column shipping label (Description, HSN Code, Item Remarks, Qty, Barcode).
   * Direct thermal printing (4" × 6" standard packaging labels) and vector PDF export.
   * Code-128 master barcode and 2D QR Code security verification seal.
4. **GST Compliance & E-Way Bill Engine:**
   * Canonical GSTR-1 (B2B, B2C, HSN Summary, Doc Issue Register) exports.
   * GSTR-3B monthly return computation.
   * Purchase 2A / 2B reconciliation with mismatch detection.
   * Integrated E-Way Bill generation.
5. **Banking & Double-Entry Financial Accounting:**
   * General Ledger, Cash Book, Multi-Account Bank Registers.
   * Smart Bank Reconciliation with CSV/OFX statement parsing and auto-matching rules.
   * Balance Sheet, Profit & Loss Statement, Trial Balance with 4-decimal precision.
6. **Customer 360 & Master Catalog:**
   * Comprehensive customer directory, billing/shipping address management, GSTIN validation.
   * Master product catalog with HSN codes, default units, tax rates, and pricing tiers.
7. **Security, RBAC & Multi-User Governance:**
   * Role-Based Access Control: Super Admin, Accountant, Sales Executive, Warehouse Dispatcher.
   * Row-level concurrency locking (`row_version` optimistic locks) preventing edit collisions.
   * Comprehensive audit trail tracking all actions, logins, and modifications.

---

## 4. Technical Architecture & Technology Stack

```
┌────────────────────────────────────────────────────────────────────────┐
│                          FRONTEND APPLICATION                          │
│                                                                        │
│  • Framework: Next.js / React (TypeScript)                             │
│  • Styling: Tailwind CSS + Radix UI / Ant Design Enterprise Components │
│  • Data Grid: Ag-Grid / TanStack Table (Rapid keyboard-driven editing) │
│  • State Management: TanStack Query (React Query) + Zustand            │
│  • Printing: Web PDF rendering (iText/PDF.js) + Browser Print Dialogs  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTPS / REST (JSON)
┌───────────────────────────────────▼────────────────────────────────────┐
│                       EXISTING SPRING BOOT SERVER                      │
│                                                                        │
│  • Framework: Spring Boot 3 + Java 25 (`dse-erp-server`)               │
│  • 35+ Active Controllers: Auth, Sales, Purchase, GST, Ledger, Barcode │
│  • Security: Spring Security + JWT Bearer Tokens                       │
│  • Reports & Documents: iText 7 + Apache POI (Excel)                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ JDBC / JPA
┌───────────────────────────────────▼────────────────────────────────────┐
│                         POSTGRESQL DATABASE                            │
│                                                                        │
│  • Version: PostgreSQL 16+ on OCI Always Free                          │
│  • Features: ACID Transactions, Row Version Locks, Flyway Migrations   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Phased Execution Roadmap (For Future Execution)

> [!IMPORTANT]
> **This roadmap is for planning only.** No code or project scaffolding will begin until the user explicitly commands: *"I'm ready to build web application."*

1. **Phase 1: Requirements & UI Wireframe Alignment (Current Phase)**
   * Define public page layout preferences (color schemes, company branding, product catalog categories).
   * Review admin portal navigation and screen ergonomics.
2. **Phase 2: Cloud Nginx & Subdomain Preparation**
   * Configure Hostinger DNS records (`www`, `erp`, `api`).
   * Update OCI Nginx configuration with Let's Encrypt SSL certificates.
3. **Phase 3: Public Business Portal Build**
   * Construct the worldwide showcase website and RFQ inquiry intake.
4. **Phase 4: Web ERP Core Modules Build**
   * Module 1: Auth & Central Dashboard
   * Module 2: Sales, Invoicing & Barcode Studio
   * Module 3: Purchases & Inventory
   * Module 4: Banking, Ledgers & Financial Statements
   * Module 5: GST Compliance & Reports
5. **Phase 5: Dual-Run Testing & UAT Sign-off**
   * Parallel validation alongside desktop application on live Oracle Cloud database.

---

## 6. Open Decision Items for Discussion
* What branding/color scheme is preferred for the public showcase website?
* Should the public catalog display pricing, or strictly "Request a Quote / Inquire"?
* Do you want an automated WhatsApp / Email notification when a customer submits an online inquiry or when an order is dispatched?

---

## 7. Official Repository Ecosystem Mapping

| Platform | Repository URL | Purpose & Target Application |
| :--- | :--- | :--- |
| **Desktop Application** | `https://github.com/ssdhameliya/DSE-ERP` | Local & LAN Desktop Client (`dse-erp-desktop`, JavaFX, installer packages) |
| **Mobile Application** | `https://github.com/ssdhameliya/DES_Mobile` | Android / iOS mobile ERP & dispatch companion app |
| **Web Application** | `https://github.com/ssdhameliya/DSE-ERP-Enterprise` | **Universal Web Platform** (Public Showcase + Web ERP Admin Studio) |

---

## 8. Verified Infrastructure & Deployment Inventory

These verified technical parameters have been captured and confirmed for **Oracle Cloud (OCI)** and **Hostinger**:

### A. Hostinger DNS Inventory (100% Verified & Live)
1. **Primary Domain Name:** `jasviindustries.in`
2. **Live Resolved DNS Records (Verified via Google DNS 8.8.8.8):**
   * `@` (`jasviindustries.in`) ➔ `130.210.35.1` (Production Root / Showcase)
   * `www` (`www.jasviindustries.in`) ➔ `130.210.35.1` (CNAME to `@` - Production Showcase)
   * `erp` (`erp.jasviindustries.in`) ➔ `130.210.35.1` (Production Web ERP Admin Studio)
   * `api` (`api.jasviindustries.in`) ➔ `130.210.35.1` (Production REST Backend API)
   * `web-uat` (`web-uat.jasviindustries.in`) ➔ `144.24.109.1` (UAT Public Showcase Staging)
   * `erp-uat` (`erp-uat.jasviindustries.in`) ➔ `144.24.109.1` (UAT Testing Web ERP Studio)
   * `api-uat` (`api-uat.jasviindustries.in`) ➔ `144.24.109.1` (UAT Testing REST Backend API)
   * `mobile-uat` (`mobile-uat.jasviindustries.in`) ➔ `ssdhameliya.github.io` (Mobile PWA Gateway)

> **Architectural Note on Showcase Domains (`@` and `www`):**
> Both `jasviindustries.in` and `www.jasviindustries.in` point to `130.210.35.1`. They represent the same public website so that visitors typing with or without "www" reach the exact same destination without 404 or connection errors.

### B. Oracle Cloud (OCI) Server Inventory (Confirmed)
1. **Production Server Identity (`130.210.35.1`):**
   * **Host:** `130.210.35.1`
   * **SSH User:** `opc`
   * **SSH Private Key (Local):** `~/.ssh/dse-erp-github-prod` / `~/.ssh/dse-erp-github-prod-ci`
   * **Web Endpoints:** `jasviindustries.in`, `www.jasviindustries.in`, `erp.jasviindustries.in`
   * **Backend API:** `api.jasviindustries.in` (Port 8080 ➔ Live Production DB)
2. **UAT Testing Server Identity (`144.24.109.1`):**
   * **Host:** `144.24.109.1`
   * **SSH User:** `opc`
   * **SSH Private Key (Local):** `~/.ssh/dse-erp-github-uat`
   * **Web Endpoints:** `web-uat.jasviindustries.in` (Staging Showcase), `erp-uat.jasviindustries.in` (Isolated UAT Web Testing Studio)
   * **Backend API:** `api-uat.jasviindustries.in` (Port 8081 ➔ Isolated `dse_erp_uat` DB)
3. **Filesystem Target Layout on OCI Servers:**
   * PROD Server (`130.210.35.1`):
     * Showcase root: `/srv/dse-erp/web/public`
     * Production Web ERP root: `/srv/dse-erp/web/admin`
   * UAT Server (`144.24.109.1`):
     * UAT Showcase root: `/srv/dse-erp/web/public`
     * UAT Web ERP root: `/srv/dse-erp/web/admin`
4. **Web Server & SSL Automation (Caddy):**
   * Config File: `/etc/caddy/Caddyfile`
   * SSL: Built-in automated Let's Encrypt SSL & HTTP/3 via Caddy (Zero manual certbot maintenance)
5. **Web Repository Ecosystem & 3-Tier Staging Workflow:**
   * **Active Local Working Copy:** `D:\ERP Website` (Local development, instant browser preview)
   * **Release Staging Copy:** `D:\GitHub\ReleaseSource Website` (Protected pre-release staging)
   * **GitHub Target Repository:** `D:\GitHub\DSE-ERP-Enterprise` (`https://github.com/ssdhameliya/DSE-ERP-Enterprise`)
   * **Release Flow:** Work in `D:\ERP Website` ➔ Test locally ➔ Sync to `D:\GitHub\ReleaseSource Website` ➔ Sync to `D:\GitHub\DSE-ERP-Enterprise` ➔ Push Git to trigger GitHub Actions (Auto UAT ➔ Approved PROD).

---

## 9. Oracle Cloud Server Execution Tracker

| Step | Action Item | Target Server | Status | Command / Details |
| :---: | :--- | :---: | :---: | :--- |
| **1** | **Hostinger DNS** | Global | **COMPLETE (VERIFIED) ✅** | All DNS records resolving worldwide via 8.8.8.8. |
| **2** | **PROD Web Directories** | `130.210.35.1` | **COMPLETE (VERIFIED) ✅** | Created `/srv/dse-erp/web/{public,admin}` + SELinux labels. |
| **3** | **PROD Caddy Setup** | `130.210.35.1` | **COMPLETE (VERIFIED) ✅** | Configured `jasviindustries.in`, `www`, `erp` in `/etc/caddy/Caddyfile`. |
| **4** | **PROD SSL Verification** | `130.210.35.1` | **COMPLETE (VERIFIED) ✅** | Live HTTPS, HTTP/3, and SSL certificates active. |
| **5** | **UAT Web Directories** | `144.24.109.1` | **COMPLETE (VERIFIED) ✅** | Created `/srv/dse-erp/web/{public,admin}` + SELinux labels. |
| **6** | **UAT Caddy Setup** | `144.24.109.1` | **COMPLETE (VERIFIED) ✅** | Configured `web-uat` & `erp-uat` in `/etc/caddy/Caddyfile`. |
| **7** | **UAT SSL Verification** | `144.24.109.1` | **COMPLETE (VERIFIED) ✅** | Live HTTPS, HTTP/3, and SSL certificates active. |
| **8** | **Live Browser Gate** | All | **COMPLETE (VERIFIED) ✅** | All 5 web URLs verified live via HTTP/S probe. |
| **9** | **GitHub Secrets Setup** | GitHub Repo | **COMPLETE (VERIFIED) ✅** | All 6 SSH credentials configured on `ssdhameliya/DSE-ERP-Enterprise`. |
| **10** | **Legacy Workflows Purge**| GitHub Repo | **COMPLETE (VERIFIED) ✅** | Cleaned `ci.yml`, `deploy-prod.yml`, `pwa-uat-cors.yml`, `release.yml`. |
| **11** | **PR #29 Review & Merge** | `main` branch | **COMPLETE (VERIFIED) ✅** | Feature PR #29 merged cleanly into `main`. |
| **12** | **Release Tag `web-v1.0.0`**| Global OCI | **COMPLETE (VERIFIED) ✅** | Sequential CI/CD Run #38133464914: **UAT deployed 1st (21s) ➔ PROD deployed 2nd (23s)**. |
| **13** | **Live Global Verification**| All 5 Endpoints | **COMPLETE (VERIFIED) ✅** | All public showcase and ERP admin endpoints live (HTTP 200 OK). |
| **14** | **Centralized Admin Studio**| `erp-uat.jasviindustries.in` | **COMPLETE (VERIFIED) ✅** | Built & deployed via PR #30 (Run #38135108246) — 3D cards, audio engine, keyboard grid. |
| **15** | **PROD Promotion `web-v1.0.1`**| All Global Endpoints | **COMPLETE (VERIFIED) ✅** | Sequential Run #38148187022: **UAT deployed 1st (24s) ➔ PROD deployed 2nd (25s)**. |

---

### A. 3-Tier Staging Architecture
To mirror the protected development flow of the Desktop and Mobile applications, the Web platform strictly adheres to a 3-tier promotion strategy:

```
[ Tier 1: Local Working Copy ] ──(Sync Script)──► [ Tier 2: Release Staging ] ──(Sync Script)──► [ Tier 3: Active Git Repo ]
Path: D:\ERP Website                              Path: D:\GitHub\ReleaseSource Website         Path: D:\GitHub\DSE-ERP-Enterprise
• Local HTML5 / CSS3 / JS                         • Pristine release staging                    • Branch: main
• Local test: Preview Local Website.bat           • Protected from dirty edits                  • Triggers GitHub Actions
• Instant browser reload                          • Mirrors production tree                     • Auto UAT -> Approved PROD
```

### B. Canary "Launching Soon" Page Specification
Before authoring full ERP web application modules, a dynamic Canary showcase is deployed to validate the entire global DNS and CI/CD delivery pipeline end-to-end:
1. **Interactive 3D Particle Canvas:** Real-time particle physics simulating modern industrial precision engineering.
2. **Dynamic 30-Day Countdown Timer:** Real-time countdown (Days, Hours, Minutes, Seconds) targeting the official platform unveiling.
3. **Smart ERP Login Router:**
   - Detects origin hostname in JavaScript (`window.location.hostname`).
   - If accessed on `web-uat.jasviindustries.in` or local preview, routes to `https://erp-uat.jasviindustries.in`.
   - If accessed on `jasviindustries.in` or `www.jasviindustries.in`, routes to `https://erp.jasviindustries.in`.
4. **Preserved Admin Studios:** Existing Web ERP testing consoles (`erp-uat.jasviindustries.in` and `erp.jasviindustries.in`) remain 100% untouched and functional.
5. **Corporate Contact Grid:** Direct email, phone, location, and security seal credentials.

### C. GitHub Repository Secrets (`ssdhameliya/DSE-ERP-Enterprise`)
All 6 SSH credentials have been configured in repository secrets via GitHub CLI:

| Secret Name | Value / Source | Verified Status |
| :--- | :--- | :---: |
| `DSE_UAT_SSH_HOST` | `144.24.109.1` | **SET & VERIFIED ✅** |
| `DSE_UAT_SSH_USER` | `opc` | **SET & VERIFIED ✅** |
| `DSE_UAT_SSH_KEY` | `~/.ssh/dse-erp-github-uat` | **SET & VERIFIED ✅** |
| `DSE_PROD_SSH_HOST` | `130.210.35.1` | **SET & VERIFIED ✅** |
| `DSE_PROD_SSH_USER` | `opc` | **SET & VERIFIED ✅** |
| `DSE_PROD_SSH_KEY` | `~/.ssh/dse-erp-github-prod-ci` | **SET & VERIFIED ✅** |

### D. Automated CI/CD Release Pipeline (`.github/workflows/deploy-web.yml`)
The workflow strictly enforces **UAT 1st, then PROD** execution (`needs: deploy-uat`):
* **Phase 1 — Merge PR to `main` (Automatic UAT):**
  * Automatically executes Job 1: `deploy-uat`.
  * Deploys directly to `/srv/dse-erp/web/public/index.html` on UAT server (`144.24.109.1`).
  * Validates live response at `https://web-uat.jasviindustries.in`.
  * Production is completely skipped and protected.
* **Phase 2 — Push Release Tag `v*` (Sequential Promotion):**
  * Step 1: Executes `deploy-uat` to verify and ensure UAT is healthy.
  * Step 2: Only after `deploy-uat` passes successfully (`needs: deploy-uat`), automatically executes `deploy-prod`.
  * Deploys to PROD server (`130.210.35.1`) and validates live status at `https://jasviindustries.in` and `https://www.jasviindustries.in`.
  * **Guarantee:** If UAT fails, PROD deployment is automatically aborted.
* **Manual Dispatch (`workflow_dispatch`):**
  * `uat-only`: Runs Job 1 only.
  * `uat-then-prod`: Runs Job 1 first, then Job 2. PROD can never be deployed independently without UAT passing first.
* **Target Filesystem Paths on OCI:**
  - Remote Destination: `/srv/dse-erp/web/public/index.html`
  - Remote SELinux Context: `httpd_sys_content_t`
  - Permissions: `chmod 644`

### E. 3-Tier Synchronization & GitHub PR Protocol
To propagate changes from local development through Pull Request to production:

```powershell
# 1. Sync Tier 1 (Local) to Tier 2 (Release Staging)
Copy-Item "D:\ERP Website\index.html" "D:\GitHub\ReleaseSource Website\index.html" -Force

# 2. Sync Tier 2 (Release Staging) to Tier 3 (Git Workspace)
Copy-Item "D:\GitHub\ReleaseSource Website\index.html" "D:\GitHub\DSE-ERP-Enterprise\web\public\index.html" -Force

# 3. Create Feature Branch & Pull Request (e.g., PR #29)
cd "D:\GitHub\DSE-ERP-Enterprise"
git checkout -b feat/web-canary-showcase
git add web/public/index.html .github/workflows/deploy-web.yml
git commit -m "feat(web): remove legacy workflows & deploy 3D canary showcase"
git push -u origin feat/web-canary-showcase
gh pr create --title "feat(web): remove legacy workflows & deploy 3D canary showcase"

# 4. Merge PR into main (Auto-deploys to UAT: https://web-uat.jasviindustries.in)
gh pr merge 29 --merge

# 5. Push Release Tag (Auto-deploys to PROD: https://jasviindustries.in)
git checkout main
git pull origin main
git tag v1.0.0
git push origin v1.0.0
```

---

## 11. Web ERP Functional Requirements & Roadmap

> [!CAUTION]
> **IRONCLAD GUARDRAIL:** No code or application scaffolding for the full Web ERP (Next.js / React) will begin until the user gives explicit command: *"I am ready to build web application."*
> The system remains strictly in requirement alignment and canary validation until authorized.

### Phase Plan for Future Implementation:
1. **Milestone 1: Public Worldwide Showcase (`jasviindustries.in`)**
   - Corporate identity, machine capabilities, manufacturing plant overview.
   - Live product & materials catalog linked to ERP master items.
   - RFQ (Request for Quotation) customer submission portal.
   - Order Dispatch tracking bay by barcode / invoice number.

2. **Milestone 2: Web ERP Studio Authentication & Layout (`erp.jasviindustries.in`)**
   - JWT authentication integrated with Spring Boot backend (`org.example.server`).
   - Role-Based Access Control (Admin, Sales, Dispatch, Accounts).
   - Unified sidebar navigation and dark/light mode enterprise UI.

3. **Milestone 3: Core Transaction Modules (Desktop Parity)**
   - **Sales Module:** Tax Invoices, Delivery Challans, Quotations, Credit Notes.
   - **Purchase Module:** Purchase Orders, Goods Receipt (GRN), Supplier Bills.
   - **Barcode Studio:** 5-column standard shipping label with QR verification.
   - **GST Module:** GSTR-1, GSTR-3B, E-Way Bill generation.
   - **Financials:** General Ledger, Bank Reconciliation, Balance Sheet.

4. **Milestone 4: Dual-Run Testing & Production Cutover**
   - Parallel live testing of Desktop and Web applications against PostgreSQL database.
   - User sign-off and production release tag creation.





