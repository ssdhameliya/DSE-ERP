package org.example.server.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerFilter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error", "/api/runtime/health", "/api/auth/health", "/api/auth/login", "/api/auth/login/mfa/complete", "/api/auth/login/mfa/resend", "/api/setup/bootstrap", "/api/setup/status",
                                "/api/auth/login-roles", "/api/auth/registration-roles", "/api/auth/registration/request", "/api/auth/registration/complete",
                                "/api/auth/password-reset/request", "/api/auth/password-reset/complete").permitAll()
                        .requestMatchers("/api/auth/effective-permissions", "/api/auth/session").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/admin/users/**", "/api/admin/roles", "/api/admin/permissions").hasAnyAuthority("ROLE_ADMIN", "USERS.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/admin/users").hasAnyAuthority("ROLE_ADMIN", "USERS.CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/users/**").hasAnyAuthority("ROLE_ADMIN", "USERS.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/users/**").hasAnyAuthority("ROLE_ADMIN", "USERS.DELETE")
                        .requestMatchers(HttpMethod.POST, "/api/admin/users/*/password", "/api/admin/users/*/lock", "/api/admin/audit").hasAnyAuthority("ROLE_ADMIN", "USERS.EDIT")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/permissions").hasAnyAuthority("ROLE_ADMIN", "USERS.MANAGE_PERMISSIONS")
                        .requestMatchers("/api/auth/register").hasAnyAuthority("ROLE_ADMIN", "USERS.CREATE")
                        .requestMatchers("/api/operations/sales/approve", "/api/operations/sales/reject").hasAnyAuthority("ROLE_ADMIN", "SALES.APPROVE")
                        .requestMatchers("/api/operations/purchases/approve", "/api/operations/purchases/reject").hasAnyAuthority("ROLE_ADMIN", "PURCHASE.APPROVE")
                        .requestMatchers(HttpMethod.GET, "/api/operations/sales/**").hasAnyAuthority("ROLE_ADMIN", "SALES.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/operations/sales/**").hasAnyAuthority("ROLE_ADMIN", "SALES.CREATE", "SALES.EDIT")
                        .requestMatchers(HttpMethod.PUT, "/api/operations/sales/**").hasAnyAuthority("ROLE_ADMIN", "SALES.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/operations/sales/**").hasAnyAuthority("ROLE_ADMIN", "SALES.DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/operations/purchases/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/operations/purchases/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE.CREATE", "PURCHASE.EDIT")
                        .requestMatchers(HttpMethod.PUT, "/api/operations/purchases/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/operations/purchases/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE.DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/operations/finance/**").hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/operations/finance/**").hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/operations/finance/**").hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/operations/finance/**").hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/purchase-recon/suppliers/**").hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.VIEW", "PURCHASE_RECON.VIEW", "PURCHASE_RECON.CREATE", "PURCHASE_RECON.EDIT", "PURCHASE_RECON.IMPORT")
                        .requestMatchers(HttpMethod.POST, "/api/purchase-recon/suppliers").hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/purchase-recon/suppliers/**").hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/purchase-recon/suppliers/**").hasAnyAuthority("ROLE_ADMIN", "RECON_SUPPLIER.DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/purchase-recon/records/**", "/api/purchase-recon/metrics").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/purchase-recon/records").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/purchase-recon/records/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/purchase-recon/records/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.DELETE")
                        .requestMatchers(HttpMethod.POST, "/api/purchase-recon/imports").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.IMPORT")
                        .requestMatchers(HttpMethod.GET, "/api/support/documents/PURCHASE_RECON/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/support/documents/PURCHASE_RECON/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                        .requestMatchers(HttpMethod.PUT, "/api/support/documents/PURCHASE_RECON/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/support/documents/PURCHASE_RECON/**").hasAnyAuthority("ROLE_ADMIN", "PURCHASE_RECON.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/bank-statements/imports/**").hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.DELETE")
                        .requestMatchers("/api/bank-statements/**", "/api/reconciliation/**").hasAnyAuthority("ROLE_ADMIN", "BANK_EXPENSE.RECONCILE")
                        .requestMatchers(HttpMethod.GET, "/api/operations/stock/**").hasAnyAuthority("ROLE_ADMIN", "INVENTORY.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/operations/stock/**").hasAnyAuthority("ROLE_ADMIN", "INVENTORY.EDIT")
                        .requestMatchers("/api/insights/reports/**", "/api/support/business-report").hasAnyAuthority("ROLE_ADMIN", "REPORTS.VIEW")
                        .requestMatchers("/api/support/search").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/support/sales/*/duplicate").hasAnyAuthority("ROLE_ADMIN", "SALES.CREATE")
                        .requestMatchers(HttpMethod.GET, "/api/master/items/**").hasAnyAuthority("ROLE_ADMIN", "INVENTORY.VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/master/items/**").hasAnyAuthority("ROLE_ADMIN", "INVENTORY.CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/master/items/**").hasAnyAuthority("ROLE_ADMIN", "INVENTORY.EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/master/items/**").hasAnyAuthority("ROLE_ADMIN", "INVENTORY.DELETE")
                        .requestMatchers(HttpMethod.POST, "/api/master/lookups/**", "/api/master/categories/**").hasAnyAuthority("ROLE_ADMIN", "MASTERS.CREATE", "USERS.MANAGE_ROLES")
                        .requestMatchers(HttpMethod.PUT, "/api/master/lookups/**", "/api/master/categories/**").hasAnyAuthority("ROLE_ADMIN", "MASTERS.EDIT", "USERS.MANAGE_ROLES")
                        .requestMatchers(HttpMethod.DELETE, "/api/master/lookups/**", "/api/master/categories/**").hasAnyAuthority("ROLE_ADMIN", "MASTERS.DELETE", "USERS.MANAGE_ROLES")
                        .requestMatchers(HttpMethod.POST, "/api/authority/email").hasAnyAuthority("ROLE_ADMIN", "COMMUNICATION.CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/pdf-studio/templates/**").hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_STUDIO.MANAGE_TEMPLATES")
                        .requestMatchers(HttpMethod.DELETE, "/api/pdf-studio/templates/**").hasAnyAuthority("ROLE_ADMIN", "DOCUMENT_STUDIO.MANAGE_TEMPLATES")
                        .requestMatchers(HttpMethod.PUT, "/api/authority/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/authority/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/authority/backups/**", "/api/authority/email/settings", "/api/authority/email/test", "/api/support/backup/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/support/settings/**").hasAnyAuthority("ROLE_ADMIN", "SETTINGS.EDIT")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, error) -> {
                            Object reason = request.getAttribute(BearerTokenAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
                            String code = reason == null ? TokenService.Status.MISSING.code() : String.valueOf(reason);
                            writeError(response, 401, code, "Authentication required");
                        })
                        .accessDeniedHandler((request, response, error) -> writeError(response, 403, "AUTH_PERMISSION_DENIED", "Insufficient permission")))
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + json(code)
                + "\",\"message\":\"" + json(message) + "\"}");
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
