# Jasvi Ecosystem Master Protocol & Unified Environment Constitution

> **Single Source of Truth** for the unified Jasvi ERP ecosystem (`Jasvi-Desktop`, `Jasvi-Admin`, `Jasvi-UserView`, `Jasvi-Mobile`): Architecture, Workspace Boundaries, Desktop-Web Parity Inspection, Automation Scripts, Server Infrastructure, and Cloud Deployment.
> **Antigravity Enforcement:** This document is automatically loaded by Antigravity on every conversation. All rules, boundaries, and checklists herein are strictly binding.
> **Last Updated:** 2026-11-01 — Unified 4-tier ecosystem layout (`D:\Antigravity-Dev\`, `D:\JavaProject\`, `D:\GitHub\ReleaseSource-*`, `D:\GitHub\Jasvi-*`). Root `CHANGELOG-*.md` files permanently banned.

---

## 1. Workspace Directory Map & Strict Protection Boundaries

The entire ecosystem follows a strict, uniform 4-tier workspace pattern across all 4 repositories:
1. **Tier 1: Antigravity Dev (`D:\Antigravity-Dev\<Repo>`)** — Active development, refactoring, compiling, and testing by AI agent.
2. **Tier 2: User IntelliJ Test (`D:\JavaProject\<Repo>`)** — User's protected local testing environment (`.idea` strictly preserved).
3. **Tier 3: Staging Mirror (`D:\GitHub\ReleaseSource-<Repo>`)** — Verified staging snapshot before release.
4. **Tier 4: Target Git Repo (`D:\GitHub\<Repo>`)** — 1-to-1 Git repository connected to `https://github.com/ssdhameliya/<Repo>`.

```
=============================================================================================================
                                     JASVI UNIFIED 4-TIER WORKSPACE ARCHITECTURE
=============================================================================================================

[ TIER 1: ACTIVE DEV ]         [ TIER 2: PROTECTED USER COPY ]    [ TIER 3: STAGING MIRROR ]    [ TIER 4: GIT TARGET ]
D:\Antigravity-Dev\            D:\JavaProject\                    D:\GitHub\ReleaseSource-*     D:\GitHub\
(AI Active Coding)             (User IntelliJ / Local Test)       (Pre-Release Verified)        (GitHub Remotes)
-------------------------------------------------------------------------------------------------------------
Jasvi-Desktop          ──►     Jasvi-Desktop (.idea protected) ──►ReleaseSource-Desktop  ──►    Jasvi-Desktop
Jasvi-Admin            ──►     Jasvi-Admin                     ──►ReleaseSource-Admin    ──►    Jasvi-Admin
Jasvi-UserView         ──►     Jasvi-UserView                  ──►ReleaseSource-UserView ──►    Jasvi-UserView
Jasvi-Mobile           ──►     Jasvi-Mobile                    ──►ReleaseSource-Mobile   ──►    Jasvi-Mobile

[ LOCAL RUNTIME DATABASE ]
D:\DSE Production (PostgreSQL 16 on port 55432)
*** STRICTLY PROTECTED: NEVER RUN DESTRUCTIVE DDL/DML, TRUNCATE, DROP, OR DELETE WITHOUT USER CONFIRMATION ***
=============================================================================================================
```

### 1.1 Desktop & Server Workspaces (`Jasvi-Desktop`)
1. **Antigravity Active Development Workspace:**  
   `D:\Antigravity-Dev\Jasvi-Desktop`  
   *All active coding, refactoring, compiler checks, and tests are performed here first.*
2. **User's IntelliJ Copy (PROTECTED LOCAL WORKSPACE):**  
   `D:\JavaProject\Jasvi-Desktop`  
   *Reserved exclusively for the user's local IntelliJ workflow. Antigravity must never run active experimental edits directly here.*  
   * **Mandatory Sync Protocol:** Whenever the user commands **"ready to test for local"** or when a **PR is raised/ready**, Antigravity MUST automatically synchronize `D:\JavaProject\Jasvi-Desktop` from the verified release staging (`D:\GitHub\ReleaseSource-Desktop`), strictly excluding `.git`, `target`, and `.idea` (to protect IntelliJ configurations).  
   * **File Change Reporting Mandate:** Antigravity MUST explicitly count and report to the user the exact number of files copied, modified, or updated during the sync so the user's local workspace is **never left behind**.
3. **Local Runtime Workspace (STRICTLY PROTECTED):**  
   `D:\DSE Production`  
   *Contains the live local PostgreSQL database (`port 55432`), configuration files, runtime logs, document attachments, and backups. **NEVER** run destructive SQL (`TRUNCATE`, `DROP`, `DELETE`), clean scripts, or modify files here unless explicitly requested.*
4. **Desktop/Server Release Staging:**  
   `D:\GitHub\ReleaseSource-Desktop`  
   *Mirrored staging copy verified before pushing releases to GitHub.*
5. **Desktop/Server GitHub Target Repository:**  
   `D:\GitHub\Jasvi-Desktop` (`https://github.com/ssdhameliya/Jasvi-Desktop`)

### 1.2 Web Admin Client Workspaces (`Jasvi-Admin`)
> **Direct Cloud Testing Policy:** The local development machine does not host the PostgreSQL database for web testing. All web application verification is conducted directly against the live **UAT** cloud environment (`https://erp-uat.jasviindustries.in/`).
1. **Web Working Directory (Decoupled HTML5/CSS3/JS):**  
   `D:\Antigravity-Dev\Jasvi-Admin` *(Legacy alias: `D:\ERP Website\admin`)*  
   *The single source of truth for the enterprise web application client.*
2. **User's Testing Workspace:** `D:\JavaProject\Jasvi-Admin`
3. **Web Release Staging:** `D:\GitHub\ReleaseSource-Admin`
4. **Web GitHub Target Repository:** `D:\GitHub\Jasvi-Admin` (`web/admin` directory on branch `main` at `https://github.com/ssdhameliya/Jasvi-Admin`).
5. **Live Web UAT URL:** `https://erp-uat.jasviindustries.in/`
6. **Live Web PROD URL:** `https://erp.jasviindustries.in/`

### 1.3 Public UserView & Dynamic Showcase Workspaces (`Jasvi-UserView`)
1. **UserView Working Directory (Interactive 3D / Claymorphism):**  
   `D:\Antigravity-Dev\Jasvi-UserView` *(Legacy alias: `D:\ERP USERSHOW`)*  
   *The single source of truth for the public showcase, machinery catalog, and instant RFQ portal.*
2. **User's Testing Workspace:** `D:\JavaProject\Jasvi-UserView`
3. **UserView Release Staging:** `D:\GitHub\ReleaseSource-UserView`
4. **UserView GitHub Target Repository:** `D:\GitHub\Jasvi-UserView` (`https://github.com/ssdhameliya/Jasvi-UserView`)
5. **Live UserView UAT URL:** `https://web-uat.jasviindustries.in/` (`/srv/dse-erp/web/public` on OCI UAT `144.24.109.1`)
6. **Live UserView PROD URLs:** `https://jasviindustries.in/` & `https://www.jasviindustries.in/` (`/srv/dse-erp/web/public` on OCI PROD `130.210.35.1`)

### 1.4 Cross-Platform Mobile Workspaces (`Jasvi-Mobile`)
1. **Mobile Working Directory (KMP / Compose Multiplatform & PWA):**  
   `D:\Antigravity-Dev\Jasvi-Mobile`
2. **User's Testing Workspace:** `D:\JavaProject\Jasvi-Mobile`
3. **Mobile Release Staging:** `D:\GitHub\ReleaseSource-Mobile`
4. **Mobile GitHub Target Repository:** `D:\GitHub\Jasvi-Mobile` (`https://github.com/ssdhameliya/Jasvi-Mobile`)
5. **Live Mobile PWA Gateway:** `https://mobile-uat.jasviindustries.in/`

---

## 2. Server Infrastructure, DNS & Environment Inventory

All verified IP addresses, DNS mappings, ports, databases, and SSH credentials across Hostinger and Oracle Cloud Infrastructure (OCI):

### 2.1 Hostinger DNS Records (Domain: `jasviindustries.in`)
| Domain / Subdomain | Target IP / Host | Environment | Destination / Role | Source Repository |
| :--- | :--- | :---: | :--- | :--- |
| **`jasviindustries.in`** | `130.210.35.1` | **PROD** | Public 3D Showcase / Root (`/srv/dse-erp/web/public`) | `ssdhameliya/Jasvi-UserView` |
| **`www.jasviindustries.in`** | `130.210.35.1` | **PROD** | Public 3D Showcase (CNAME to `@`) | `ssdhameliya/Jasvi-UserView` |
| **`erp.jasviindustries.in`** | `130.210.35.1` | **PROD** | Production Web ERP Admin Studio (`/srv/dse-erp/web/admin`) | `ssdhameliya/Jasvi-Admin` |
| **`api.jasviindustries.in`** | `130.210.35.1` | **PROD** | Production REST Backend API (Port `8080`) | `ssdhameliya/Jasvi-Desktop` |
| **`web-uat.jasviindustries.in`** | `144.24.109.1` | **UAT** | UAT Public 3D Showcase Staging (`/srv/dse-erp/web/public`) | `ssdhameliya/Jasvi-UserView` |
| **`erp-uat.jasviindustries.in`** | `144.24.109.1` | **UAT** | UAT Web ERP Testing Studio (`/srv/dse-erp/web/admin`) | `ssdhameliya/Jasvi-Admin` |
| **`api-uat.jasviindustries.in`** | `144.24.109.1` | **UAT** | UAT REST Backend API (Port `8081`) | `ssdhameliya/Jasvi-Desktop` |
| **`mobile-uat.jasviindustries.in`**| `ssdhameliya.github.io` | **UAT** | Mobile PWA Gateway | `ssdhameliya/Jasvi-Mobile` |

### 2.2 Oracle Cloud (OCI) Server Specification & Paths
| Parameter | Production (PROD) | Testing (UAT) |
| :--- | :--- | :--- |
| **Server Public IP** | `130.210.35.1` | `144.24.109.1` |
| **SSH User** | `opc` | `opc` |
| **SSH Private Key (Local)** | `~/.ssh/dse-erp-github-prod` | `~/.ssh/dse-erp-github-uat` |
| **Spring Server Port** | `8080` | `8081` |
| **Target Database** | `dse_erp` (PostgreSQL 16+) | `dse_erp_uat` (PostgreSQL 16+) |
| **Public Showcase Root** | `/srv/dse-erp/web/public` | `/srv/dse-erp/web/public` |
| **Web ERP Root** | `/srv/dse-erp/web/admin` | `/srv/dse-erp/web/admin` |
| **Web Server / SSL** | Caddy `/etc/caddy/Caddyfile` (Auto TLS, HTTP/3, Static `file_server` + `/api/*` reverse proxy) | Caddy `/etc/caddy/Caddyfile` (Auto TLS, HTTP/3, Static `file_server` + `/api/*` reverse proxy) |

---

## 3. Decoupled Multi-Platform Architecture

Jasvi ERP uses a **clean decoupled architecture**. The backend server is a stateless, high-performance Spring Boot REST API. Desktop, Web, UserView, and Mobile clients connect to the unified backend via REST endpoints with Bearer JWT authentication:

```
                                      MULTI-USER CLOUD PLATFORM
                                                  │
         ┌────────────────────────┬───────────────┴───────────────┬────────────────────────┐
         ▼                        ▼                               ▼                        ▼
[ Desktop (JavaFX 25) ]  [ Web Admin (HTML5/CSS3/JS) ]  [ UserView 3D Showcase ]  [ Mobile (KMP / PWA) ]
70 FXML Pages            MVC SPA (Jasvi-Admin)          Dynamic 3D Portal         Kotlin Multiplatform / PWA
Bearer JWT Auth (/api/*) Bearer JWT Auth (/api/*)       Public Catalog / RFQ      Offline sync / REST API
Local Workstation        Served via Caddy on OCI        Served via Caddy on OCI   mobile-uat.jasviindustries.in
         │                        │                               │                        │
         └────────────────────────┴───────────────┬───────────────┴────────────────────────┘
                                                  │
                                                  ▼
                                 [ Spring Boot Backend Server ]
                                 (org.example.server - Port 8080 / 8081)
                                 Stateless JWT SecurityConfig (/api/**)
                                 227 Business Services & Repositories
                                 Zero Embedded UI / Zero Vaadin
                                                  │
                                                  ▼
                                 [ Shared Pure Java Contracts ]
                                 (org.example.shared)
                                 DocumentCalculationEngine, PermissionCatalog,
                                 AmountInWordsConverter, Code128Encoder
                                                  │
                                                  ▼
                                 [ PostgreSQL Relational Database ]
                                 (Single Source of Truth, 60+ Flyway Migrations)
```

---

## 4. Mandatory 7-Point First-Principle Inspection Checklist

> **CRITICAL RULE:** Before touching any code or responding to a feature request, Antigravity MUST execute this 7-point inspection against the desktop reference codebase (`D:\Antigravity-Dev\Jasvi-Desktop`).
> **"Each and everything means each and everything."** Never build half-finished screens, never guess formulas, and **NEVER use mock or dummy data.**

### Point 1: Front-End & User Interface Structure
* Locate the desktop FXML & Controller in `desktop/src/main/java/org/example/controller/` and `desktop/src/main/resources/fxml/pages/`.
* Mirror exact layouts: tables, column ordering, headers, alignments, widths, summary cards, status badges, action buttons, side drawers, modals, and tabs.

### Point 2: Default Screen Open & Initialization Logic
* **Bootstrap APIs:** Determine what master data is loaded on screen init (e.g. `GET /api/master/sales-entry-bootstrap` for payment terms, GST types, transporters, customer dropdowns).
* **Default Filters:** Apply Desktop defaults: past 6 months (`today.minusMonths(6)` to `today`), active status filters, next sequence number generation, and default focus.

### Point 3: Dynamic Logic & Calculation Engines
* Inspect `DocumentCalculationEngine.java` in `shared/src/main/java/org/example/shared/`.
* Formula mandates:
  * **Item Taxable:** `Quantity * Rate * (1 - Discount% / 100)`
  * **Charges Taxable vs Non-Taxable:** Handle separately based on tax treatment.
  * **GST Splitting:** Intra-state (`GST`) -> `CGST = Round(Tax / 2)`, `SGST = Tax - CGST`. Inter-state (`IGST`) -> `IGST = Tax`, `CGST = 0`, `SGST = 0`.
  * **Rounding:** Exact decimal scales (2 decimals for currency, 4 for quantity/unit cost) using `RoundingMode.HALF_UP`.
  * **Grand Total:** `Item Taxable + Line Tax + Charges Amount + Charges Tax`.

### Point 4: API Endpoints, Payloads & DTO Contracts
* Inspect Spring Boot `@RestController` and DTOs in `server/src/main/java/org/example/server/`.
* Verify exact URL paths, HTTP verbs (`GET`, `POST`, `PUT`, `DELETE`), query parameters, and JSON payload keys match 100%.

### Point 5: Database Schema & Master Data Models
* Inspect JPA entities and repositories (`LookupMaster`, `PartyMaster`, `ItemMaster`, `sales_header`, `purchase_header`, `payment_record`).
* Verify dropdown categories (`TRANSPORTER`, `CHARGE_TYPE`, `PAYMENT_TERMS`, `UNIT`, `PAYMENT_MODE`, `BANK_ACCOUNT`).
* Ensure foreign keys and constraints match PostgreSQL database schema.

### Point 6: UI Behaviors for Every Single Event
* **`onChange` / `onInput`:**
  * Select Customer -> auto-populate Billing Address, Shipping Address, GSTIN, State Code, default Payment Terms, Credit Limit.
  * Select Item -> auto-populate HSN/SAC, Unit, Sell Rate, Tax Rate.
  * Toggle GST Type -> trigger immediate recalculation of GST split.
* **Secondary Popups:** Additional Charges dialog (`DocumentChargeDialog`), Action Drawers (View, Audit, Edit, Duplicate, Print, Barcode, Excel, Payment, Cancel, Delete).

### Point 7: Functional Completeness & Error Handling
* Saving must persist real database records via the API.
* Errors and validation messages must match desktop wording.
* **RFC 7807 Compliance:** All API errors return `ProblemDetail` with `Content-Type: application/problem+json`.
* **Zero Mock Fallbacks:** Never return fake fallback arrays or dummy values.

---

## 5. Version Management & Release Automation Scripts

### 5.1 Version Governance & Clean Root Policy
* **Desktop / Server Version:** Governed strictly by `.mvn/maven.config` (`-Drevision=11.0.0`).
* **Web Admin Client Version:** Governed by `admin/version.json` and `admin/js/version.js` (`11.0.0.01`, tag `web-v11.0.0.01`).
* **UserView Showcase Version:** Governed by git tags in `ssdhameliya/Jasvi-UserView` (`showcase-v1.0.0`) and `data/showcase.json` (`"version": 1`).
* **Mobile Client Version:** Governed by `MobileBuildInfo.kt` and git tags in `ssdhameliya/Jasvi-Mobile`.
* **Release Notes (Single Source of Truth):** Every release must update `desktop/src/main/resources/release-notes.md`. This is the ONLY release notes file needed; it is packaged directly into the desktop runtime JAR.
* **Prohibition of Root `CHANGELOG-*.md` Files (Strict Cleanliness Mandate):**
  - **NEVER create, generate, or sync `CHANGELOG-*.md` files in the project root.**
  - All historical `CHANGELOG-*.md` files have been permanently removed.
  - During synchronization across `D:\Antigravity-Dev\`, `D:\GitHub\ReleaseSource-*`, `D:\JavaProject\`, and `D:\GitHub\`, root changelog files must never be created or re-introduced.

---

### 5.2 PowerShell Script 1: Desktop/Server Release & PR Automation
```powershell
$Version = "11.0.0"
$Note    = "Pure Decoupled Architecture Rollback"

$Repo    = "ssdhameliya/Jasvi-Desktop"
$Branch  = "release-$Version"
$Project = "D:\GitHub\Jasvi-Desktop"
$Source  = "D:\GitHub\ReleaseSource-Desktop"

$env:GIT_PAGER = "cat"
$env:GH_PAGER  = "cat"

function Check($Message) {
    if ($LASTEXITCODE -ne 0) { throw "STOP: $Message" }
}

Set-Location $Project

# 1. Clean main
git switch main
Check "Could not switch to main."
git fetch origin --prune
Check "Git fetch failed."
git reset --hard origin/main
Check "Could not reset main."

# 2. Remove old PR / Release branch
$OldPr = gh pr list --repo $Repo --head $Branch --state open --json number --jq '.[0].number'
if ($OldPr) {
    Write-Host "Closing old PR #$OldPr"
    gh pr close $OldPr --repo $Repo
}
if (git branch --list $Branch) {
    git branch -D $Branch
}
git ls-remote --exit-code --heads origin $Branch *> $null
if ($LASTEXITCODE -eq 0) {
    git push origin --delete $Branch
}

# 3. Create fresh release branch + copy source
git switch -c $Branch origin/main
Check "Could not create $Branch."
robocopy $Source $Project /MIR /COPY:DAT /R:1 /W:1 /XD .git target .idea
if ($LASTEXITCODE -gt 7) { throw "STOP: Robocopy failed with code $LASTEXITCODE." }

# 4. Verify version
$Revision = (Get-Content .mvn\maven.config).Trim()
if ($Revision -ne "-Drevision=$Version") {
    throw "STOP: Expected -Drevision=$Version but found '$Revision'."
}

# 5. Full build + release audit
.\mvnw.cmd clean test -pl server -am
Check "Server regression tests failed."
python scripts/audit-release-gates.py
Check "Release audit failed."

# 6. Commit & Push
git add -A
git commit -m "Release v$Version - $Note"
Check "Commit failed."
git push -u origin $Branch
Check "GitHub push failed."

# 7. Create PR
gh pr create --repo $Repo --base main --head $Branch --title "Release v$Version - $Note" --body @"
Release v$Version

$Note

Validation completed:
- Version confirmed: $Version
- .\mvnw.cmd clean test -pl server -am
- scripts/audit-release-gates.py (All 32 gates passed)
"@
gh pr view $Branch --repo $Repo --web

# 8. Sync User's Local IntelliJ Workspace & Report Changes
$LocalUserCopy = "D:\JavaProject\Jasvi-Desktop"
robocopy $Source $LocalUserCopy /MIR /COPY:DAT /R:1 /W:1 /XD .git target .idea
Write-Host "Synchronized $Source -> $LocalUserCopy (Robocopy Exit Code: $LASTEXITCODE)"
```

---

### 5.3 PowerShell Script 2: Desktop/Server Tag Push & Automated UAT Deployment
```powershell
$Version = "11.0.0"
$Note    = "Pure Decoupled Architecture Rollback"
$Repo    = "ssdhameliya/Jasvi-Desktop"

$env:GIT_PAGER = "cat"
$env:GH_PAGER  = "cat"

function Check($Message) {
    if ($LASTEXITCODE -ne 0) { throw "STOP: $Message" }
}

Set-Location "D:\GitHub\Jasvi-Desktop"
git fetch origin main --tags
Check "Git fetch failed."
$Main = git rev-parse origin/main
$Revision = (git show origin/main:.mvn/maven.config).Trim()
if ($Revision -ne "-Drevision=$Version") {
    throw "STOP: origin/main is '$Revision', expected -Drevision=$Version."
}

$LocalExists = git tag -l "v$Version"
if ($LocalExists) {
    $LocalCommit = git rev-list -n 1 "v$Version"
    if ($LocalCommit -ne $Main) {
        throw "STOP: Existing local v$Version points to $LocalCommit instead of $Main."
    }
} else {
    git tag -a "v$Version" $Main -m "Release v$Version - $Note"
    Check "Could not create local tag."
}

$RemotePeeled = git ls-remote origin "refs/tags/v$Version^{}"
if (-not $RemotePeeled) {
    git push origin "v$Version"
    Check "GitHub tag push failed."
    Write-Host "Tag v$Version pushed successfully! Release & UAT deploy workflow triggered."
} else {
    Write-Host "Tag v$Version already exists on origin."
}
```

---

### 5.4 PowerShell Script 3: Web Admin Release Script (`Jasvi-Admin`)
```powershell
$WebVersion = "11.0.0.01" # Format: <DesktopVersion>.<WebBuild: 2 digits>
$Tag        = "web-v$WebVersion"
$Note       = "Enterprise Web Client Decoupled Rollback"

$SourceDir  = "D:\Antigravity-Dev\Jasvi-Admin"
$StagingDir = "D:\GitHub\ReleaseSource-Admin"
$GitTarget  = "D:\GitHub\Jasvi-Admin"
$UserCopy   = "D:\JavaProject\Jasvi-Admin"

# 1. Sync Dev to Staging Mirror
robocopy $SourceDir $StagingDir /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea
if ($LASTEXITCODE -gt 7) { throw "Robocopy to staging failed" }

# 2. Sync Staging to User IntelliJ Copy
robocopy $StagingDir $UserCopy /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea
if ($LASTEXITCODE -gt 7) { throw "Robocopy to user copy failed" }

# 3. Sync Staging to Git Target Repository web/admin
robocopy $StagingDir "$GitTarget\web\admin" /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea
if ($LASTEXITCODE -gt 7) { throw "Robocopy to git target failed" }

# 4. Commit, tag, and push in GitHub repo
Set-Location $GitTarget
git switch main
git pull origin main
git add web/admin .github/workflows
git commit -m "Web Release $Tag: $Note"
git tag -f $Tag -m "Release $Tag - $Note"
git push origin main
git push -f origin $Tag

Write-Host "Triggered automated deployment to UAT and Production via GitHub Actions."
Write-Host "Live UAT: https://erp-uat.jasviindustries.in/"
Write-Host "Live PROD: https://erp.jasviindustries.in/"
```

---

### 5.5 PowerShell Script 4: UserView Showcase Release Script (`Jasvi-UserView`)
```powershell
$Tag  = "showcase-v1.0.0"
$Note = "Production Launch of Dynamic 3D UserView Showcase"

$SourceDir  = "D:\Antigravity-Dev\Jasvi-UserView"
$StagingDir = "D:\GitHub\ReleaseSource-UserView"
$GitTarget  = "D:\GitHub\Jasvi-UserView"
$UserCopy   = "D:\JavaProject\Jasvi-UserView"

# 1. Sync Dev to Staging Mirror
robocopy $SourceDir $StagingDir /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea
if ($LASTEXITCODE -gt 7) { throw "Robocopy to staging failed" }

# 2. Sync Staging to User IntelliJ Copy
robocopy $StagingDir $UserCopy /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea
if ($LASTEXITCODE -gt 7) { throw "Robocopy to user copy failed" }

# 3. Sync Staging to Git Target Repository
robocopy $StagingDir $GitTarget /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea .github
if ($LASTEXITCODE -gt 7) { throw "Robocopy to git target failed" }

# 4. Commit, tag, and push to GitHub
Set-Location $GitTarget
git switch main
git pull origin main
git add -A
git commit -m "Release $Tag: $Note"
git tag -f $Tag -m "Release $Tag - $Note"
git push -u origin main
git push -f origin $Tag

Write-Host "Triggered UserView deployment pipeline via GitHub Actions."
Write-Host "Live UAT:  https://web-uat.jasviindustries.in/"
Write-Host "Live PROD: https://jasviindustries.in/"
```

---

### 5.6 PowerShell Script 5: Mobile Application Release Script (`Jasvi-Mobile`)
```powershell
$Version = "1.2.7"
$Tag     = "mobile-v$Version"
$Note    = "Jasvi Mobile Suite Release"

$SourceDir  = "D:\Antigravity-Dev\Jasvi-Mobile"
$StagingDir = "D:\GitHub\ReleaseSource-Mobile"
$GitTarget  = "D:\GitHub\Jasvi-Mobile"
$UserCopy   = "D:\JavaProject\Jasvi-Mobile"

# 1. Sync Dev to Staging Mirror
robocopy $SourceDir $StagingDir /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea
if ($LASTEXITCODE -gt 7) { throw "Robocopy to staging failed" }

# 2. Sync Staging to User IntelliJ Copy
robocopy $StagingDir $UserCopy /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea
if ($LASTEXITCODE -gt 7) { throw "Robocopy to user copy failed" }

# 3. Sync Staging to Git Target Repository
robocopy $StagingDir $GitTarget /MIR /COPY:DAT /R:1 /W:1 /XD .git .idea .github
if ($LASTEXITCODE -gt 7) { throw "Robocopy to git target failed" }

# 4. Commit, tag, and push to GitHub
Set-Location $GitTarget
git switch main
git pull origin main
git add -A
git commit -m "Release $Tag: $Note"
git tag -f $Tag -m "Release $Tag - $Note"
git push -u origin main
git push -f origin $Tag

Write-Host "Triggered Mobile deployment pipeline via GitHub Actions."
Write-Host "Live Gateway: https://mobile-uat.jasviindustries.in/"
```

---

### 5.7 CI/CD Pipefail Hardening Rule
> **MANDATORY LESSON LEARNED:** Under GitHub Actions bash with `set -euo pipefail`, never pipe large curl outputs directly into `grep -q` (e.g. `curl ... | grep -q ...`). `grep -q` closes stdin immediately after finding the first match, causing curl to receive `SIGPIPE` / `EPIPE` (`exit code 23`).  
> **Always buffer the response in a variable or use native bash pattern matching:**
> ```bash
> RESP=$(curl --fail --silent --show-error --max-time 20 "$URL")
> [[ "$RESP" == *"Expected String"* ]] || { echo "Verification failed" >&2; exit 1; }
> ```

---

## 6. Cloud Deployment & OCI Production Reference

### 6.1 Systemd Service Unit (`/etc/systemd/system/dse-erp-prod.service`)
```ini
[Unit]
Description=DSE-ERP Cloud Platform Service (Spring Boot REST API)
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=opc
Group=opc
WorkingDirectory=/srv/dse-erp
ExecStart=/usr/bin/java \
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -Xms2g -Xmx6g \
    -Dspring.profiles.active=prod \
    -Dfile.encoding=UTF-8 \
    -jar /srv/dse-erp/dse-erp-server.jar
Restart=always
RestartSec=10
LimitNOFILE=65536
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### 6.2 Caddy Configuration (`/etc/caddy/Caddyfile`)
```caddy
# Public Company Showcase
jasviindustries.in, www.jasviindustries.in {
    root * /srv/dse-erp/web/public
    file_server
    encode gzip zstd
}

# Production Web ERP Admin Studio & REST API Proxy
erp.jasviindustries.in {
    root * /srv/dse-erp/web/admin
    encode gzip zstd
    file_server

    handle /api/* {
        reverse_proxy 127.0.0.1:8080 {
            header_up Host {host}
            header_up X-Real-IP {remote_host}
            header_up X-Forwarded-For {remote_host}
            header_up X-Forwarded-Proto {scheme}
        }
    }

    try_files {path} {path}/ /index.html
}

# Production Standalone Backend REST API
api.jasviindustries.in {
    reverse_proxy 127.0.0.1:8080
    encode gzip zstd
}

# UAT Web ERP Testing Studio & REST API Proxy
erp-uat.jasviindustries.in {
    root * /srv/dse-erp/web/admin
    encode gzip zstd
    file_server

    handle /api/* {
        reverse_proxy 127.0.0.1:8081 {
            header_up Host {host}
            header_up X-Real-IP {remote_host}
            header_up X-Forwarded-For {remote_host}
            header_up X-Forwarded-Proto {scheme}
        }
    }

    try_files {path} {path}/ /index.html
}

# UAT Standalone Backend REST API
api-uat.jasviindustries.in {
    reverse_proxy 127.0.0.1:8081
    encode gzip zstd
}
```

### 6.3 PostgreSQL OCI Production Tuning (`postgresql.conf`)
* **`shared_buffers`**: `2560MB` (25% of total RAM on 12GB instances)
* **`effective_cache_size`**: `6GB` (50–75% of total RAM)
* **`work_mem`**: `16MB`
* **`maintenance_work_mem`**: `512MB`
* **`listen_addresses`**: `'127.0.0.1'` (Loopback-only binding; protected behind local firewall/proxy)
* **`max_connections`**: `100`

---

## 7. Master Rollback History & Parity Audit Reference

For full historical reference on the pure decoupled architecture rollback, consult [`rollback.md`](file:///D:/ERP%20Website/rollback.md) in the project workspace.
* Core Git Tag: `v11.0.0` (PR #70)
* Web Git Tag: `web-v11.0.0.01`
* Live status: Verified healthy across all production and UAT endpoints.
