package org.example.server.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Dual-chain Spring Security configuration.
 *
 * <h3>Chain 1 — REST API ({@code /api/**}) — {@code @Order(1)}</h3>
 * <p>Stateless JWT bearer authentication. The existing JavaFX desktop client
 * ({@code ApiRuntime}, {@code ApiSession}, {@code AuthApiClient}) connects
 * through this chain without any modification. CSRF is disabled; sessions are
 * never created.</p>
 *
 * <h3>Chain 2 — Vaadin Web UI ({@code /**}) — {@code @Order(2)}</h3>
 * <p>Stateful {@code HttpOnly} cookie sessions for the Vaadin Flow web
 * interface. CSRF is enabled via {@link CookieCsrfTokenRepository} with the
 * Vaadin internal servlet path excluded. Form login is redirected to
 * {@code VaadinLoginView}.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chain 1 — REST API  (@Order 1, matches /api/**)
    //   • Stateless — no HTTP session ever created
    //   • CSRF disabled (Bearer token is the CSRF protection)
    //   • All existing URL → permission rules live here
    //   • Desktop JavaFX client is 100% unaffected
    // ─────────────────────────────────────────────────────────────────────────
    @Bean
    @Order(1)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain apiFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerFilter)
            throws Exception {

        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Public endpoints ────────────────────────────────────────
                .requestMatchers("/error",
                    "/api/runtime/health",
                    "/api/updates/**",
                    "/api/auth/health",
                    "/api/auth/login",
                    "/api/auth/login/mfa/enrollment",
                    "/api/auth/login/mfa/complete",
                    "/api/auth/login/mfa/resend",
                    "/api/auth/login/mfa/recovery/request",
                    "/api/auth/login/mfa/recovery/complete",
                    "/api/setup/bootstrap",
                    "/api/setup/status",
                    "/api/auth/login-roles",
                    "/api/auth/registration-roles",
                    "/api/auth/registration/captcha",
                    "/api/auth/registration/request",
                    "/api/auth/registration/email/verify",
                    "/api/auth/registration/mfa/complete",
                    "/api/auth/password-reset/request",
                    "/api/auth/password-reset/complete"
                ).permitAll()
                // ── Auth ────────────────────────────────────────────────────
                .requestMatchers("/api/auth/effective-permissions", "/api/auth/session").authenticated()
                // ── Admin — Users ────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/admin/users/**", "/api/admin/roles", "/api/admin/permissions")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.VIEW")
                .requestMatchers(HttpMethod.GET, "/api/admin/registration-role", "/api/admin/registrations/**")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.MANAGE_ROLES")
                .requestMatchers(HttpMethod.PUT, "/api/admin/registration-role")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.MANAGE_ROLES")
                .requestMatchers(HttpMethod.POST, "/api/admin/registrations/**")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.MANAGE_ROLES")
                .requestMatchers(HttpMethod.POST, "/api/admin/users")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.CREATE")
                .requestMatchers(HttpMethod.PUT, "/api/admin/users/**")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/admin/users/**")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.DELETE")
                .requestMatchers(HttpMethod.POST,
                    "/api/admin/users/*/password", "/api/admin/users/*/lock",
                    "/api/admin/users/*/mfa/reset", "/api/admin/audit")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.EDIT")
                .requestMatchers(HttpMethod.PUT, "/api/admin/permissions")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.MANAGE_PERMISSIONS")
                .requestMatchers("/api/auth/register")
                    .hasAnyAuthority("ROLE_ADMIN", "USERS.CREATE")
                // ── Sales ────────────────────────────────────────────────────
                .requestMatchers("/api/operations/sales/approve", "/api/operations/sales/reject")
                    .hasAnyAuthority("ROLE_ADMIN", "SALES.APPROVE")
                .requestMatchers(HttpMethod.GET, "/api/operations/sales/**")
                    .hasAnyAuthority("ROLE_ADMIN", "SALES.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/operations/sales/**")
                    .hasAnyAuthority("ROLE_ADMIN", "SALES.CREATE", "SALES.EDIT")
                .requestMatchers(HttpMethod.PUT, "/api/operations/sales/**")
                    .hasAnyAuthority("ROLE_ADMIN", "SALES.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/operations/sales/**")
                    .hasAnyAuthority("ROLE_ADMIN", "SALES.DELETE")
                // ── Purchases ────────────────────────────────────────────────
                .requestMatchers("/api/operations/purchases/approve", "/api/operations/purchases/reject")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE.APPROVE")
                .requestMatchers(HttpMethod.GET, "/api/operations/purchases/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/operations/purchases/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE.CREATE", "PURCHASE.EDIT")
                .requestMatchers(HttpMethod.PUT, "/api/operations/purchases/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/operations/purchases/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE.DELETE")
                // ── Finance ──────────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/operations/finance/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/operations/finance/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.CREATE")
                .requestMatchers(HttpMethod.PUT, "/api/operations/finance/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/operations/finance/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.DELETE")
                // ── Purchase Recon ────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/purchase-recon/suppliers/**")
                    .hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.VIEW", "PURCHASE_RECON.VIEW", "PURCHASE_RECON.CREATE", "PURCHASE_RECON.EDIT", "PURCHASE_RECON.IMPORT")
                .requestMatchers(HttpMethod.POST, "/api/purchase-recon/suppliers")
                    .hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.CREATE")
                .requestMatchers(HttpMethod.PUT, "/api/purchase-recon/suppliers/**")
                    .hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/purchase-recon/suppliers/**")
                    .hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.DELETE")
                .requestMatchers(HttpMethod.GET, "/api/purchase-recon/records/**", "/api/purchase-recon/metrics")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/purchase-recon/records")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.CREATE")
                .requestMatchers(HttpMethod.PUT, "/api/purchase-recon/records/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/purchase-recon/records/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.DELETE")
                .requestMatchers(HttpMethod.POST, "/api/purchase-recon/imports")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.IMPORT")
                .requestMatchers(HttpMethod.GET, "/api/support/documents/PURCHASE_RECON/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/support/documents/PURCHASE_RECON/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                .requestMatchers(HttpMethod.PUT, "/api/support/documents/PURCHASE_RECON/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/support/documents/PURCHASE_RECON/**")
                    .hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                // ── Bank Reconciliation ───────────────────────────────────────
                .requestMatchers(HttpMethod.DELETE, "/api/bank-statements/imports/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.DELETE")
                .requestMatchers("/api/bank-statements/**", "/api/reconciliation/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.RECONCILE")
                // ── Inventory ────────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/operations/stock/**")
                    .hasAnyAuthority("ROLE_ADMIN", "INVENTORY.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/operations/stock/**")
                    .hasAnyAuthority("ROLE_ADMIN", "INVENTORY.EDIT")
                // ── Dashboard & Reporting ─────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/insights/dashboard")
                    .hasAnyAuthority("ROLE_ADMIN", "DASHBOARD.VIEW")
                .requestMatchers(HttpMethod.GET, "/api/reporting/schedules/**")
                    .hasAnyAuthority("ROLE_ADMIN", "REPORTS.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/reporting/schedules")
                    .hasAnyAuthority("ROLE_ADMIN", "REPORTS.CREATE")
                .requestMatchers(HttpMethod.POST, "/api/reporting/schedules/*/duplicate")
                    .hasAnyAuthority("ROLE_ADMIN", "REPORTS.CREATE")
                .requestMatchers(HttpMethod.PUT, "/api/reporting/schedules/**")
                    .hasAnyAuthority("ROLE_ADMIN", "REPORTS.EDIT")
                .requestMatchers(HttpMethod.POST,
                    "/api/reporting/schedules/*/run",
                    "/api/reporting/schedules/*/pause",
                    "/api/reporting/schedules/*/resume")
                    .hasAnyAuthority("ROLE_ADMIN", "REPORTS.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/reporting/schedules/**")
                    .hasAnyAuthority("ROLE_ADMIN", "REPORTS.DELETE")
                .requestMatchers("/api/insights/reports/**", "/api/reporting/**", "/api/support/business-report")
                    .hasAnyAuthority("ROLE_ADMIN", "REPORTS.VIEW")
                // ── Support & Search ──────────────────────────────────────────
                .requestMatchers("/api/support/search").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/support/sales/*/duplicate")
                    .hasAnyAuthority("ROLE_ADMIN", "SALES.CREATE")
                // ── Master Data ───────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/master/items/**")
                    .hasAnyAuthority("ROLE_ADMIN", "INVENTORY.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/master/items/**")
                    .hasAnyAuthority("ROLE_ADMIN", "INVENTORY.CREATE")
                .requestMatchers(HttpMethod.PUT, "/api/master/items/**")
                    .hasAnyAuthority("ROLE_ADMIN", "INVENTORY.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/master/items/**")
                    .hasAnyAuthority("ROLE_ADMIN", "INVENTORY.DELETE")
                .requestMatchers(HttpMethod.POST, "/api/master/lookups/**", "/api/master/categories/**")
                    .hasAnyAuthority("ROLE_ADMIN", "MASTERS.CREATE", "USERS.MANAGE_ROLES")
                .requestMatchers(HttpMethod.PUT, "/api/master/lookups/**", "/api/master/categories/**")
                    .hasAnyAuthority("ROLE_ADMIN", "MASTERS.EDIT", "USERS.MANAGE_ROLES")
                .requestMatchers(HttpMethod.DELETE, "/api/master/lookups/**", "/api/master/categories/**")
                    .hasAnyAuthority("ROLE_ADMIN", "MASTERS.DELETE", "USERS.MANAGE_ROLES")
                // ── Communication ─────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/authority/email/resend")
                    .hasAnyAuthority("ROLE_ADMIN", "COMMUNICATION.RESEND")
                .requestMatchers(HttpMethod.POST, "/api/authority/email")
                    .hasAnyAuthority("ROLE_ADMIN", "COMMUNICATION.CREATE")
                // ── Document Studio ───────────────────────────────────────────
                .requestMatchers(HttpMethod.PUT, "/api/pdf-studio/templates/**")
                    .hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_STUDIO.EDIT", "DOCUMENT_STUDIO.MANAGE_TEMPLATES")
                .requestMatchers(HttpMethod.DELETE, "/api/pdf-studio/templates/**")
                    .hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_STUDIO.EDIT", "DOCUMENT_STUDIO.MANAGE_TEMPLATES")
                .requestMatchers(HttpMethod.GET, "/api/authority/resources/EXCEL_TEMPLATE/**")
                    .hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_STUDIO.VIEW")
                .requestMatchers(HttpMethod.PUT, "/api/authority/resources/EXCEL_TEMPLATE/**")
                    .hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_STUDIO.EDIT", "DOCUMENT_STUDIO.MANAGE_TEMPLATES")
                .requestMatchers(HttpMethod.DELETE, "/api/authority/resources/EXCEL_TEMPLATE/**")
                    .hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_STUDIO.EDIT", "DOCUMENT_STUDIO.MANAGE_TEMPLATES")
                // ── Backups ───────────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/authority/backups/recovery-package")
                    .hasAuthority("ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/authority/backups/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.VIEW")
                .requestMatchers(HttpMethod.POST, "/api/authority/backups")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.CREATE")
                .requestMatchers(HttpMethod.POST, "/api/authority/backups/import")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.CREATE")
                .requestMatchers(HttpMethod.POST, "/api/authority/backups/*/validate")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.VIEW")
                .requestMatchers(HttpMethod.POST,
                    "/api/authority/backups/*/restore/stage",
                    "/api/authority/backups/restore/stage")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.EDIT")
                .requestMatchers(HttpMethod.DELETE, "/api/authority/backups/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.DELETE")
                .requestMatchers(HttpMethod.GET, "/api/support/backup/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.VIEW")
                .requestMatchers("/api/support/backup/**")
                    .hasAnyAuthority("ROLE_ADMIN", "BACKUP.EDIT")
                // ── Settings ──────────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/support/settings", "/api/support/settings/**")
                    .authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/support/settings", "/api/support/settings/**")
                    .hasAnyAuthority("ROLE_ADMIN", "SETTINGS.EDIT")
                .requestMatchers(HttpMethod.GET, "/api/authority/resources/BUSINESS_ASSET/**")
                    .authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/authority/resources/BUSINESS_ASSET/**")
                    .hasAnyAuthority("ROLE_ADMIN", "SETTINGS.EDIT")
                .requestMatchers("/api/authority/email/settings", "/api/authority/email/test")
                    .hasAuthority("ROLE_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/authority/**")
                    .hasAuthority("ROLE_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/authority/**")
                    .hasAuthority("ROLE_ADMIN")
                // ── Default ───────────────────────────────────────────────────
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, error) -> {
                    Object reason = request.getAttribute(BearerTokenAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
                    String code = reason == null ? TokenService.Status.MISSING.code() : String.valueOf(reason);
                    writeError(response, 401, code, "Authentication required");
                })
                .accessDeniedHandler((request, response, error) ->
                    writeError(response, 403, "AUTH_PERMISSION_DENIED", "Insufficient permission"))
            )
            .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chain 2 — Vaadin Web UI  (@Order 2, matches /**)
    //   • Stateful HttpOnly cookie sessions (IF_REQUIRED)
    //   • CSRF via CookieCsrfTokenRepository (Vaadin JS reads XSRF-TOKEN cookie)
    //   • /vaadinServlet/** excluded from CSRF (Vaadin internal push/heartbeat)
    //   • formLogin → /login (served by VaadinLoginView @Route("login"))
    //   • /login, /VAADIN/**, /PUSH/** are public (required for Vaadin to boot)
    // ─────────────────────────────────────────────────────────────────────────
    @Bean
    @Order(2)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain vaadinFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerFilter)
            throws Exception {

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    "/vaadinServlet/**",    // Vaadin server push & heartbeat
                    "/VAADIN/**"            // Vaadin static resources bootstrap
                )
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                // Vaadin static resources and login page are public
                .requestMatchers(
                    "/VAADIN/**",
                    "/vaadinServlet/**",
                    "/login",
                    "/login/**",
                    "/PUSH/**",
                    "/favicon.ico",
                    "/manifest.webmanifest",
                    "/sw.js",
                    "/sw-runtime-resources-precache.js",
                    "/offline.html",
                    "/icons/**",
                    "/images/**",
                    "/frontend/**",
                    "/webjars/**"
                ).permitAll()
                // All other Vaadin views require authentication;
                // fine-grained access is enforced by @RolesAllowed on each @Route view class
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, error) -> {
                    String uri = request.getRequestURI();
                    if (uri != null && uri.startsWith("/login")) {
                        return;
                    }
                    response.sendRedirect("/login");
                })
                .accessDeniedHandler((request, response, error) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permission"))
            )
            // Also run the bearer filter for web users who authenticate via token
            // (enables API calls from within the Vaadin UI without double-auth)
            .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
            "{\"status\":" + status
            + ",\"title\":\"" + json(httpTitle(status)) + "\""
            + ",\"code\":\"" + json(code) + "\""
            + ",\"detail\":\"" + json(message) + "\""
            + ",\"message\":\"" + json(message) + "\"}"
        );
    }

    private static String httpTitle(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            default  -> "Internal Server Error";
        };
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
