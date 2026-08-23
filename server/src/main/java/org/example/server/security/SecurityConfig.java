package org.example.server.security;

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
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerFilter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/runtime/health", "/api/auth/health", "/api/auth/login", "/api/auth/login/mfa/complete", "/api/auth/login/mfa/resend", "/api/setup/bootstrap", "/api/setup/status",
                                "/api/auth/registration-roles", "/api/auth/registration/request", "/api/auth/registration/complete",
                                "/api/auth/password-reset/request", "/api/auth/password-reset/complete").permitAll()
                        .requestMatchers("/api/admin/**", "/api/auth/register").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/authority/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/authority/**").hasRole("ADMIN")
                        .requestMatchers("/api/authority/backups/**").hasRole("ADMIN")
                        .requestMatchers("/api/authority/email/settings", "/api/authority/email/test").hasRole("ADMIN")
                        .requestMatchers("/api/support/backup/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/support/settings/**").hasRole("ADMIN")
                        .requestMatchers("/api/reconciliation/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/operations/purchases/**", "/api/operations/finance/**", "/api/operations/stock/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/insights/reports/**", "/api/support/business-report", "/api/support/search", "/api/support/purchases/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/master/items/**", "/api/master/lookups/**", "/api/master/categories/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/master/items/**", "/api/master/lookups/**", "/api/master/categories/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/master/items/**", "/api/master/lookups/**", "/api/master/categories/**").hasAnyRole("ADMIN", "MANAGER")
                        .anyRequest().hasAnyRole("ADMIN", "MANAGER", "SALES"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, error) -> writeError(response, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, error) -> writeError(response, 403, "Insufficient permission")))
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
