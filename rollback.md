# DSE ERP — MASTER ROLLBACK & MIGRATION PLAN
## Pure Decoupled Architecture: Desktop `v11.0.0` & Web Platform `v11.0.0.01`

> **Document Status:** 📋 OFFICIAL EXECUTION BLUEPRINT  
> **Target Desktop / Server Release:** `v11.0.0` (Baseline: Git tag `v10.0.41` commit `5a758e05`)  
> **Target Web Platform Release:** `v11.0.0.01` (Tag: `web-v11.0.0.01`, Baseline: `v10.0.41.13` / `v10.0.41.14`)  
> **Primary Objective:** Completely remove all Vaadin Flow code, dependencies, and proxy overrides; restore pure, rock-solid Spring Boot REST API backend + pure HTML5/JS/CSS client portal + JavaFX desktop application.

---

## 1. Executive Summary & Core Rationale

Between versions `v10.0.42` and `v10.0.51`, experimental Vaadin Flow code was integrated into the embedded Tomcat server (`server.jar`). This introduced:
1. Heavy memory and CPU overhead inside embedded Tomcat.
2. Incomplete desktop feature parity and missing screens.
3. Routing conflicts and reverse-proxy complexity in Caddy on cloud servers.

### The Solution:
1. **Desktop & Server (`DSE-ERP`)**:
   - Roll back to the rock-solid, fully stable pre-Vaadin state: **Git tag `v10.0.41`** (commit `5a758e05`).
   - Bump version to **`11.0.0`**.
   - Bumping to major version `11.0.0` ensures that all release gates, Maven build scripts, package managers, and auto-updaters cleanly recognize it as an upgrade over `10.0.51`.
2. **Web Platform (`DSE-ERP-Enterprise`)**:
   - Keep the rich, mature HTML5 + JavaScript + CSS client portal located in `D:\ERP Website\admin` (42 views, 26 controllers).
   - Set version to **`v11.0.0.01`** (`web-v11.0.0.01`).
3. **Cloud Infrastructure (UAT & Production)**:
   - Revert Caddy reverse proxy on `130.210.35.1` to serve the static HTML5/JS website directly from `/srv/dse-erp/web/admin` via `file_server`, with reverse proxy only for `/api/*` to the Spring Boot backend on port `8080`.

---

## 2. Directory & Workspace Alignment Matrix

| Workspace / Target | Location / Repository | Current State | Target State After Rollback |
| :--- | :--- | :--- | :--- |
| **Active Dev Workspace** | `c:\Users\JATIN DHAMELIYA\Downloads\DSE-ERP-10.0.25-INTELLIJ-READY-FINAL-V19\DSE-ERP-10.0.25` | `10.0.51` (with Vaadin `webui`) | **`11.0.0`** (Clean baseline from `v10.0.41`) |
| **IntelliJ Workspace (Protected)** | `D:\JavaProject\DSE-ERP-Final` | `10.0.51` | **`11.0.0`** (Synced only after local test passes) |
| **Local Runtime / DB (Protected)** | `D:\DSE Production` (Port `55432`) | Untouched | Untouched (Database preserved) |
| **Release Staging** | `D:\GitHub\ReleaseSource` | `10.0.51` | **`11.0.0`** (Synced alongside IntelliJ copy) |
| **GitHub Core Project** | `D:\GitHub\DSE-ERP-Primary` (`ssdhameliya/DSE-ERP`) | `10.0.51` (commit `d42a6f21`) | **`v11.0.0`** (Tagged release) |
| **Web Workspace** | `D:\ERP Website` | `10.0.41.14` | **`v11.0.0.01`** (Updated `version.json` & `version.js`) |
| **Web Release Staging** | `D:\GitHub\ReleaseSource Website` | `10.0.41.14` | **`v11.0.0.01`** (Synced via batch script) |
| **GitHub Web Project** | `D:\GitHub\DSE-ERP-Enterprise` (`ssdhameliya/DSE-ERP-Enterprise`) | `web-v10.0.41.14` | **`web-v11.0.0.01`** (Tagged release) |

---

## 3. Detailed Phase-by-Phase Execution Plan

### Phase 1: Core Backend & Desktop Rollback (`v11.0.0`)

#### Step 1.1: Extract Clean Code from Tag `v10.0.41`
- In `D:\GitHub\DSE-ERP-Primary`, checkout the exact tree at `v10.0.41` (commit `5a758e05`).
- Sync this clean state into the dev workspace:
  `c:\Users\JATIN DHAMELIYA\Downloads\DSE-ERP-10.0.25-INTELLIJ-READY-FINAL-V19\DSE-ERP-10.0.25`.
- This automatically eliminates:
  - All 56 Vaadin Flow classes in `server/src/main/java/org/example/server/webui/`.
  - `vaadin-spring-boot-starter` from `server/pom.xml`.
  - `flow-build-info.json` from `server/src/main/resources/META-INF/VAADIN/`.
  - Vaadin security filter chain from `SecurityConfig.java`.
  - Caddy override scripts `configure-caddy-prod.sh` and `configure-caddy-uat.sh`.

#### Step 1.2: Upgrade Version to `11.0.0`
- Update `.mvn/maven.config`:
  ```properties
  -Drevision=11.0.0
  ```
- Update release version properties across:
  - `desktop/src/main/resources/release-notes.md`
  - `CHANGELOG-11.0.0.md`

#### Step 1.3: Build & Release Gate Validation
- Execute clean test compilation:
  ```powershell
  .\mvnw.cmd clean test -pl server -am
  ```
- Run full automated release gate validation:
  ```powershell
  python scripts/audit-release-gates.py
  ```
- Build production shaded JAR artifacts:
  ```powershell
  .\mvnw.cmd package -DskipTests
  ```
- **Acceptance Gate**: 0 compile errors, 0 test failures, all release gates 100% green.

---

### Phase 2: Web Platform Upgrade (`v11.0.0.01`)

#### Step 2.1: Update Version Artifacts in `D:\ERP Website`
- In `D:\ERP Website\admin\version.json`:
  ```json
  {
      "desktopVersion": "11.0.0",
      "webBuild": 1,
      "webVersion": "11.0.0.01",
      "tag": "web-v11.0.0.01",
      "releaseDate": "2026-10-25T00:30:00+05:30",
      "environment": "Production / Enterprise",
      "summary": "v11.0.0.01 Enterprise Web Client Rollback to Pure HTML5/JS/CSS Decoupled Architecture"
  }
  ```
- In `D:\ERP Website\admin\js\version.js`:
  ```javascript
  window.APP_VERSION = "11.0.0.01";
  window.APP_TAG = "web-v11.0.0.01";
  ```

#### Step 2.2: Sync Web Platform to Staging & Git Target
- Execute `D:\ERP Website\Sync to Staging and Git.bat`:
  - Syncs `D:\ERP Website\admin` &rarr; `D:\GitHub\ReleaseSource Website\admin`.
  - Syncs `D:\ERP Website\admin` &rarr; `D:\GitHub\DSE-ERP-Enterprise\web\admin`.
  - Syncs `D:\ERP Website\index.html` &rarr; `D:\GitHub\DSE-ERP-Enterprise\web\public\index.html`.

---

### Phase 3: Synchronization of Local Protected Copies

1. **User's IntelliJ Copy (`D:\JavaProject\DSE-ERP-Final`)**:
   - Mirror clean `11.0.0` source from dev workspace after validation.
2. **Release Staging Copy (`D:\GitHub\ReleaseSource`)**:
   - Mirror clean `11.0.0` source from dev workspace after validation.
3. **Database Workspace (`D:\DSE Production`)**:
   - Preserve database (`55432`) intact. Zero schema changes needed.

---

### Phase 4: Cloud Environment Rollback (UAT & Production)

#### Step 4.1: Restore Caddy Reverse Proxy Configuration
On production server `130.210.35.1`:
1. Restore pre-Vaadin Caddyfile backup:
   ```bash
   sudo cp /etc/caddy/Caddyfile.pre-vaadin-web /etc/caddy/Caddyfile
   sudo caddy validate --config /etc/caddy/Caddyfile
   sudo systemctl reload caddy
   ```
2. Verify public endpoints:
   - `https://erp.jasviindustries.in/` &rarr; Serves HTML5/JS client (`Content-Type: text/html`).
   - `https://api.jasviindustries.in/api/runtime/health` &rarr; Returns `{"ready":true}`.

#### Step 4.2: Deploy Clean `server.jar` (`v11.0.0`)
- Deploy `server.jar` without any Vaadin memory/CPU overhead.
- Service listens on port `8080` handling all REST requests.

---

### Phase 5: GitHub Push & Tagging

Once all local testing and builds are confirmed:
1. **In `D:\GitHub\DSE-ERP-Primary`**:
   - Commit: `Release v11.0.0 - Pure Decoupled Architecture Rollback`
   - Tag: `git tag -a v11.0.0 -m "Release v11.0.0 - Pure Decoupled Architecture Rollback"`
   - Push to `origin main` and push tag `v11.0.0`.
2. **In `D:\GitHub\DSE-ERP-Enterprise`**:
   - Commit: `Release web-v11.0.0.01 - Enterprise Web Client Decoupled Rollback`
   - Tag: `git tag -a web-v11.0.0.01 -m "Release web-v11.0.0.01"`
   - Push to `origin main` and push tag `web-v11.0.0.01`.

---

## 4. Rollback Verification Checklist
 
- [x] Dev Workspace: Zero Vaadin classes in `server/src/main/java/org/example/server/webui/`.
- [x] Dev Workspace: Zero Vaadin dependencies in `server/pom.xml`.
- [x] Dev Workspace: `.mvn/maven.config` set to `-Drevision=11.0.0`.
- [x] Maven: `.\mvnw.cmd test -pl server -am` passes with 0 failures (94 tests run, 0 failures).
- [x] Maven: `.\mvnw.cmd package -DskipTests` produces valid `server.jar` and shaded desktop jar.
- [x] Release Gates: `python scripts/audit-release-gates.py` reports `RELEASE_GATES_CURRENT_OK version=11.0.0` (all 32 gates passed).
- [x] Web Client: `D:\ERP Website\admin\version.json` set to `11.0.0.01`.
- [x] Web Client: Double-click `Preview Local Admin.bat` successfully launches HTML/JS admin portal.
- [x] Protected Copies: `D:\JavaProject\DSE-ERP-Final` and `D:\GitHub\ReleaseSource` synced to `11.0.0`.
- [x] Cloud Servers: Caddy restored to serve `/srv/dse-erp/web/admin` via `file_server` on both UAT and PROD.
- [x] GitHub: PR #70 merged, tags `v11.0.0` and `web-v11.0.0.01` pushed.
- [x] Live Deployments: Both UAT and Production web deployments succeeded 100%.

---

## 5. Live Production & UAT Endpoints Verification

| Service | Endpoint URL | Status | Details |
|---|---|:---:|---|
| **UAT Web Showcase** | `https://web-uat.jasviindustries.in` | **200 OK** | Global Digital Showcase Portal |
| **UAT Admin Studio** | `https://erp-uat.jasviindustries.in` | **200 OK** | Jasvi Industries ERP — Centralized Enterprise Studio (`v11.0.0.01`) |
| **UAT Backend REST API** | `https://erp-uat.jasviindustries.in/api/runtime/health` | **200 OK** | `{"service":"dse-erp-server","version":"11.0.0","ready":true,"environment":"UAT"}` |
| **PROD Web Showcase** | `https://jasviindustries.in` | **200 OK** | Global Digital Showcase Portal |
| **PROD Admin Studio** | `https://erp.jasviindustries.in` | **200 OK** | Jasvi Industries ERP — Centralized Enterprise Studio (`v11.0.0.01`) |
| **PROD Backend REST API** | `https://erp.jasviindustries.in/api/runtime/health` | **200 OK** | `{"service":"dse-erp-server","version":"11.0.0","ready":true,"environment":"PROD"}` |

