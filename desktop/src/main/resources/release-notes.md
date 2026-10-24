# What's New in DSE ERP v10.0.42

### Cloud Migration & Pure Java Vaadin Flow Web UI
- **Pure Java Enterprise Web Portal:** Added 38 responsive, full-featured web views built with Vaadin Flow directly inside the backend server, enabling browser-based ERP access across all desktop, tablet, and mobile browsers.
- **Dual-Tier Security Architecture:** Implemented two independent Spring Security filter chains — `@Order(1)` stateless JWT authentication for REST API consumers and `@Order(2)` cookie-based session security with CSRF protection for Web UI users.
- **Deduplicated Calculation Engines:** Consolidated business calculation logic and number-to-words currency conversion into `dse-erp-shared` for a single source of truth across desktop and cloud.
- **Cloud-Ready Infrastructure Configurations:** Included production-grade deployment configurations for systemd service isolation, Caddy reverse proxy with automated TLS, and PostgreSQL 16 performance tuning for Oracle Cloud Infrastructure (OCI).
