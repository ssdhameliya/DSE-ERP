# What's New in DSE ERP v10.0.44

### Cloud Deployment Production Token Fix
- **Embedded Production Mode Token:** Packaged static `flow-build-info.json` and configured `vaadin-maven-plugin` with `<productionMode>true</productionMode>` to prevent runtime verification of compile-time builder folders on Oracle Cloud Linux instances.
- **Automated Zero-Downtime Deployment:** Verified continuous deployment to UAT environment with zero filesystem dependency errors.

# What's New in DSE ERP v10.0.43

### Cloud Runtime Hardening & Standalone Production Mode
- **Vaadin Production Runtime Mode:** Configured `vaadin.productionMode=true` and `vaadin.whitelisted-packages=org.example.server.webui` across all environment profiles (`default`, `uat`, `prod`) for standalone headless cloud execution without compile-time directory lookup dependencies.
- **Automated OCI UAT Deployment:** Ensured zero-downtime, validated server startup on Oracle Cloud Infrastructure Linux instances with automated health check verification.

# What's New in DSE ERP v10.0.42

### Cloud Migration & Pure Java Vaadin Flow Web UI
- **Pure Java Enterprise Web Portal:** Added 38 responsive, full-featured web views built with Vaadin Flow directly inside the backend server, enabling browser-based ERP access across all desktop, tablet, and mobile browsers.
- **Dual-Tier Security Architecture:** Implemented two independent Spring Security filter chains — `@Order(1)` stateless JWT authentication for REST API consumers and `@Order(2)` cookie-based session security with CSRF protection for Web UI users.
- **Deduplicated Calculation Engines:** Consolidated business calculation logic and number-to-words currency conversion into `dse-erp-shared` for a single source of truth across desktop and cloud.
- **Cloud-Ready Infrastructure Configurations:** Included production-grade deployment configurations for systemd service isolation, Caddy reverse proxy with automated TLS, and PostgreSQL 16 performance tuning for Oracle Cloud Infrastructure (OCI).
