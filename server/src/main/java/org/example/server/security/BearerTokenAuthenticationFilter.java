package org.example.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTH_FAILURE_ATTRIBUTE = "dse.auth.failure";

    private final TokenService tokens;
    private final PermissionAuthorityService permissions;

    public BearerTokenAuthenticationFilter(TokenService tokens, PermissionAuthorityService permissions) {
        this.tokens = tokens;
        this.permissions = permissions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, TokenService.Status.MISSING.code());
            chain.doFilter(request, response);
            return;
        }

        String bearer = header.substring(7).trim();
        TokenService.AuthenticationResult result = tokens.inspect(bearer);
        if (result.authenticated()) request.removeAttribute(AUTH_FAILURE_ATTRIBUTE);
        else request.setAttribute(AUTH_FAILURE_ATTRIBUTE, result.status().code());
        result.user().ifPresent(user -> {
            var authorities = new ArrayList<SimpleGrantedAuthority>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().trim().toUpperCase()));
            for (String permission : permissions.permissionKeys(user.role())) {
                if (permission != null && !permission.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(permission.trim().toUpperCase()));
                }
            }
            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
        chain.doFilter(request, response);
    }
}
