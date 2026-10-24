package org.example.server.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class ErpUserDetails implements UserDetails {
    private final int id;
    private final String username;
    private final String password;
    private final String role;
    private final String fullName;
    private final Collection<? extends GrantedAuthority> authorities;

    public ErpUserDetails(int id, String username, String password, String role, String fullName,
                          Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
        this.authorities = authorities;
    }

    public int getId() { return id; }
    public String getRole() { return role; }
    public String getFullName() { return fullName; }

    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(id, username, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
