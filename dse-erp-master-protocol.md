# DSE-ERP Master Protocol & Unified Environment Constitution

> **Single Source of Truth** for DSE-ERP Architecture, Workspace Boundaries, Desktop-Web Parity Inspection, Automation Scripts, Server Infrastructure, and Cloud Deployment.
> **Antigravity Enforcement:** This document is automatically loaded by Antigravity on every conversation. All rules, boundaries, and checklists herein are strictly binding.

---

## 1. Workspace Directory Map & Strict Protection Boundaries

```
[ Active Dev / Fixing ]                    [ Protected Local Runtime ]
c:\...\DSE-ERP-10.0.25                    D:\DSE Production (DB 55432)
(Active Code Modifications)               (STRICTLY PROTECTED - NEVER ALTER)
         │
         ├──► [ Staging Mirror ] ─────────► [ GitHub Target Project ]
         │    D:\GitHub\ReleaseSource       D:\GitHub\DSE-ERP-Primary (ssdhameliya/DSE-ERP)
         │
         └──► [ Protected Desktop Copy ]
              D:\JavaProject\DSE-ERP-Final
              (STRICTLY PROTECTED - DESKTOP ONLY - NEVER TOUCH FOR WEB)

[ Web Working Directory ]                  [ Web GitHub Target Project ]
D:\ERP Website\admin                      D:\GitHub\DSE-ERP-Enterprise (web/admin)
(Decoupled HTML5/CSS3/JS)                 (Automated Cloud Deployment via Caddy)
```

### 1.1 Desktop & Server Workspaces
1. **Antigravity Active Development Workspace:**  
   `c:\Users\JATIN DHAMELIYA\Downloads\DSE-ERP-10.0.25-INTELLIJ-READY-FINAL-V19\DSE-ERP-10.0.25`  
   *All active coding, refactoring, compiler checks, and tests are performed here first.*
2. **User's IntelliJ Copy (PROTECTED LOCAL WORKSPACE):**  
   `D:\JavaProject\DSE-ERP-Final`  
   *Reserved exclusively for the user's local IntelliJ workflow. Antigravity must never run active experimental edits directly here.*  
   * **Mandatory Sync Protocol:** Whenever the user commands **"ready to test for local"** or when a **PR is raised/ready**, Antigravity MUST automatically synchronize `D:\JavaProject\DSE-ERP-Final` from the verified release staging (`D:\GitHub\ReleaseSource`), strictly excluding `.git`, `target`, and `.idea` (to protect IntelliJ configurations).  
   * **File Change Reporting Mandate:** Antigravity MUST explicitly count and report to the user the exact number of files copied, modified, or updated during the sync so the user's local workspace is **never left behind**.
3. **Local Runtime Workspace (STRICTLY PROTECTED):**  
   `D:\DSE Production`  
   *Contains the live local PostgreSQL database (`port 55432`), configuration files, runtime logs, document attachments, and backups. **NEVER** run destructive SQL (`TRUNCATE`, `DROP`, `DELETE`), clean scripts, or modify files here unless explicitly requested.*
4. **Desktop/Server Release Staging:**  
   `D:\GitHub\ReleaseSource`  
   *Mirrored staging copy verified before pushing releases to GitHub.*
5. **Desktop/Server GitHub Target Repository:**  
   `D:\GitHub\DSE-ERP-Primary` (`https://github.com/ssdhameliya/DSE-ERP`)

### 1.2 Web Application Workspaces & Direct UAT Testing Policy
> **Direct Cloud Testing Policy:** The local development machine does not host the PostgreSQL database for web testing. All web application verification is conducted directly against the live **UAT** cloud environment (`https://erp-uat.jasviindustries.in/`).
1. **Web Working Directory (Decoupled HTML5/CSS3/JS):** `D:\ERP Website\admin`  
   *The single source of truth for the enterprise web application client.*
2. **Web Release Staging & GitHub Target Repository:** `D:\GitHub\DSE-ERP-Enterprise` (`web/admin` directory on branch `main`).
3. **Live Web UAT URL:** `https://erp-uat.jasviindustries.in/`
4. **Live Web PROD URL:** `https://erp.jasviindustries.in/`

---

## 2. Server Infrastructure, DNS & Environment Inventory

All verified IP addresses, DNS mappings, ports, databases, and SSH credentials across Hostinger and Oracle Cloud Infrastructure (OCI):

### 2.1 Hostinger DNS Records (Domain: `jasviindustries.in`)
| Domain / Subdomain | Target IP / Host | Environment | Destination / Role |
| :--- | :--- | :---: | :--- |
| **`jasviindustries.in`** | `130.210.35.1` | **PROD** | Public Company Showcase / Root |
| **`www.jasviindustries.in`** | `130.210.35.1` | **PROD** | Public Company Showcase (CNAME to `@`) |
| **`erp.jasviindustries.in`** | `130.210.35.1` | **PROD** | Production Decoupled Web ERP Admin Studio |
| **`api.jasviindustries.in`** | `130.210.35.1` | **PROD** | Production REST Backend API (Port `8080`) |
| **`web-uat.jasviindustries.in`** | `144.24.109.1` | **UAT** | UAT Public Showcase Staging |
| **`erp-uat.jasviindustries.in`** | `144.24.109.1` | **UAT** | UAT Decoupled Web ERP Testing Studio |
| **`api-uat.jasviindustries.in`** | `144.24.109.1` | **UAT** | UAT REST Backend API (Port `8081`) |
| **`mobile-uat.jasviindustries.in`**| `ssdhameliya.github.io` | **UAT** | Mobile PWA Gateway |

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

DSE-ERP uses a **clean decoupled architecture**. The backend server is a stateless, high-performance Spring Boot REST API. Both Desktop and Web clients connect to the identical REST endpoints using Bearer JWT authentication:

```
                                  MULTI-USER CLOUD PLATFORM
                                               │
                       ┌───────────────────────┴───────────────────────┐
                       ▼                                               ▼
          [ Desktop Client (JavaFX 25) ]                 [ Web Client (HTML5 / CSS3 / JS) ]
          70 FXML Pages                                  MVC SPA Architecture (D:\ERP Website\admin)
          Bearer JWT Auth (/api/**)                      Bearer JWT Auth (/api/**)
          Local Workstation Runtime                      Served via Caddy file_server on OCI VM
                       │                                               │
                       └───────────────────────┬───────────────────────┘
                                               │
                                               ▼
                              [ Spring Boot 4.1.0 Backend ]
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

### 3.1 Web Client Architecture Standards (`D:\ERP Website\admin`)
* **Pure Decoupled Client:** The web portal is written in standard, high-performance HTML5, CSS3, and modern Vanilla JavaScript adhering strictly to MVC separation:
  - `admin/index.html`: Unified layout shell, navigation drawer, status bar, and universal modals.
  - `admin/js/controllers/`: Screen controllers mapping 1-to-1 to Desktop controllers (`SalesController.js`, `PurchaseController.js`, `FinanceController.js`, etc.).
  - `admin/js/services/`: Centralized API clients (`ApiService.js`, `AuthService.js`, `MasterService.js`, etc.).
  - `admin/js/util/`: Desktop parity calculation engines (`DocumentCalculationEngine.js`, `AmountInWordsConverter.js`, `AppDialogService.js`, etc.).
* **Zero Server UI Dependencies:** All Vaadin Flow code has been permanently removed from the Spring Boot backend (`dse-erp-server-11.0.0.jar`). The backend only serves pure REST endpoints under `/api/**`.
* **Universal Modal Dialog Policy:**
  - `window.alert`, `window.confirm`, and `window.prompt` are strictly forbidden. All user alerts, confirmations, and prompts must use `AppDialogService` (`modal-app-dialog`) for an enterprise look & feel matching the desktop.
* **Universal Attachment & Document Lightbox:**
  - Attachments across Sales, Purchase, Inward, Outward, and Master records are previewed via `window.openUniversalPreviewModal()` and fetched with Bearer JWT tokens via `ApiService.viewFile()`.

### 3.2 Backend Security & Filter Chain (`SecurityConfig.java`)
* **Stateless JWT Security (`/api/**`):** Stateless Bearer token authentication (`SessionCreationPolicy.STATELESS`). Exclusively serves both the JavaFX Desktop Client and the Web Client API requests.
* **CORS & Forward Headers:** Configured for cross-origin web access and native reverse-proxy forwarding (`server.forward-headers-strategy=native`).

---

## 4. Mandatory 7-Point First-Principle Inspection Checklist

> **CRITICAL RULE:** Before touching any code or responding to a feature request, Antigravity MUST execute this 7-point inspection against the desktop reference codebase (`c:\...\DSE-ERP-10.0.25`).
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

### 5.1 Version Governance
* **Desktop / Server Version:** Governed strictly by `.mvn/maven.config` (`-Drevision=11.0.0`).
* **Web Client Version:** Governed by `admin/version.json` and `admin/js/version.js` (`11.0.0.01`, tag `web-v11.0.0.01`).
* **Release Notes:** Every release must update `desktop/src/main/resources/release-notes.md` and add `CHANGELOG-<version>.md`.

---

### 5.2 PowerShell Script 1: Desktop/Server Release & PR Automation
```powershell
$Version = "11.0.0"
$Note    = "Pure Decoupled Architecture Rollback"

$Repo    = "ssdhameliya/DSE-ERP"
$Branch  = "release-$Version"
$Project = "D:\GitHub\DSE-ERP-Primary"
$Source  = "D:\GitHub\ReleaseSource"

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
$LocalUserCopy = "D:\JavaProject\DSE-ERP-Final"
robocopy $Source $LocalUserCopy /MIR /COPY:DAT /R:1 /W:1 /XD .git target .idea
Write-Host "Synchronized $Source -> $LocalUserCopy (Robocopy Exit Code: $LASTEXITCODE)"
```

---

### 5.3 PowerShell Script 2: Tag Push & Automated UAT Deployment
```powershell
$Version = "11.0.0"
$Note    = "Pure Decoupled Architecture Rollback"
$Repo    = "ssdhameliya/DSE-ERP"

$env:GIT_PAGER = "cat"
$env:GH_PAGER  = "cat"

function Check($Message) {
    if ($LASTEXITCODE -ne 0) { throw "STOP: $Message" }
}

Set-Location "D:\GitHub\DSE-ERP-Primary"
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

### 5.4 PowerShell Script 3: Web Application Release Script
```powershell
$WebVersion = "11.0.0.01" # Format: <DesktopVersion>.<WebBuild: 2 digits>
$Tag        = "web-v$WebVersion"
$Note       = "Enterprise Web Client Decoupled Rollback"

$WebWorking = "D:\ERP Website\admin"
$WebRepo    = "D:\GitHub\DSE-ERP-Enterprise"

# 1. Sync working directory to Enterprise repo
robocopy $WebWorking "$WebRepo\web\admin" /MIR /R:1 /W:1 /XD .git .idea

# 2. Stage, commit, tag, and push in GitHub Enterprise repo
Set-Location $WebRepo
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

### 5.5 CI/CD Pipefail Hardening Rule
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

### 6.2 Caddy Decoupled Configuration (`/etc/caddy/Caddyfile`)
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
* Live status: Verified healthy across all 6 production and UAT endpoints.
