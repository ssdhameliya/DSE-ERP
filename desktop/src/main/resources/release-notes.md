# What's New in DSE ERP v10.0.50

### Vaadin Production Bundle & Webapp Classpath Packaging
- **Vaadin Production Precompiled Bundle:** Integrated `com.vaadin:vaadin-prod-bundle` dependency into `server/pom.xml`, providing precompiled web component bundles, stats manifest, and chunk mappings.
- **Embedded Webapp index.html & Static Assets:** Packaged `META-INF/VAADIN/webapp/index.html` along with precompiled frontend builds and service worker assets into the server JAR classpath, resolving the HTTP 500 `IOException: Unable to find index.html` on cloud servers.
- **Production Manifest Alignment:** Embedded production `flow-build-info.json` and `stats.json` directly into `META-INF/VAADIN/config/`, ensuring seamless standalone execution in production mode.

# What's New in DSE ERP v10.0.49

### Official Vaadin Security Configurer & Forward Dispatch Authorization
- **Official Vaadin Security Configurer Integration:** Upgraded Spring Security Chain 2 to utilize official `VaadinSecurityConfigurer.vaadin()`, configuring specialized CSRF exemptions, request caching, and seamless `loginView(VaadinLoginView.class)` lifecycle handling.
- **DispatcherType FORWARD & ERROR Permitted:** Explicitly permitted `DispatcherType.FORWARD` and `DispatcherType.ERROR` in `authorizeHttpRequests`, completely eliminating the infinite redirect loop caused by Spring Security 6/7 intercepting internal servlet forwards.
- **Caddy Configuration Privilege Fix:** Updated candidate cleanup in `configure-caddy-uat.sh` and `configure-caddy-prod.sh` to use `sudo rm -f`, ensuring zero permission errors during automated proxy reconfiguration.

# What's New in DSE ERP v10.0.48

### Cloud Reverse Proxy & Vaadin Security Hardening
- **Vaadin AntPathRequestMatcher Security Hardening:** Updated Spring Security Chain 2 (`vaadinFilterChain`) to use explicit `AntPathRequestMatcher` for all public Vaadin static resources and login routes, ensuring robust path matching independent of Spring MVC handlers.
- **Infinite Redirect Loop Guard:** Added defensive loop detection in `vaadinFilterChain` authentication entry point to prevent infinite 302 redirects when requesting `/login`.
- **Brace-Balanced Caddyfile Parser:** Upgraded `configure-caddy-uat.sh` and `configure-caddy-prod.sh` with a comprehensive curly-brace-balanced parser to cleanly detect and replace legacy static file server blocks (`root * /srv/dse-erp/web/admin` and `handle /api/*`), guaranteeing exact routing to the pure Java Vaadin Flow application.

# What's New in DSE ERP v10.0.47

### Cloud Reverse Proxy Automation & Dual Environment Hardening
- **Production Web Proxy Automation:** Added safe automated Caddy configuration workflow and script (`configure-caddy-prod.sh`) routing `erp.jasviindustries.in` directly to the pure Java Vaadin Flow web application on port 8080.
- **Accurate Block Parsing:** Upgraded Caddy block detection in `configure-caddy-uat.sh` and `configure-caddy-prod.sh` to precisely inspect domain configuration blocks, preventing false positives from companion `api` endpoints.
- **Dual Reverse Proxy Deployment Workflows:** Added `.github/workflows/deploy-prod-caddy.yml` alongside `deploy-uat-caddy.yml` with pre-flight health gates and automatic rollback.

# What's New in DSE ERP v10.0.46

### Cloud Reverse Proxy & Native Forward Headers Hardening
- **Spring Boot Native Forward Headers Strategy:** Enabled `server.forward-headers-strategy=native` in `application.properties` to ensure embedded Tomcat honors `X-Forwarded-Proto` and `X-Forwarded-For` from Caddy/Nginx reverse proxies, preventing infinite HTTPS redirect loops.
- **UAT Web Proxy Automation:** Added safe automated Caddy configuration workflow and script (`configure-caddy-uat.sh`) routing `erp-uat.jasviindustries.in` directly to the pure Java Vaadin Flow web application on port 8081.

# What's New in DSE ERP v10.0.45

### Cloud Deployment Standalone Production Fix
- **Eliminated Frontend Prepare Plugin:** Removed `vaadin-maven-plugin:prepare-frontend` execution from `server/pom.xml` to prevent compile-time overwriting of `flow-build-info.json` in pure Java server-side Flow applications.
- **Embedded Static Production Mode Token:** Packaged static `flow-build-info.json` with `"productionMode": true` directly into the server JAR, bypassing runner directory validation (`verifyFolderExists`) on Oracle Cloud Infrastructure (OCI) Linux instances.

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
