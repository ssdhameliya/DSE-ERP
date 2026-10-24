package org.example.server.security;

import org.example.server.persistence.entity.UserEntity;
import org.example.server.persistence.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ErpUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionAuthorityService permissions;

    public ErpUserDetailsService(UserRepository userRepository, PermissionAuthorityService permissions) {
        this.userRepository = userRepository;
        this.permissions = permissions;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Username or email cannot be empty");
        }

        UserEntity user = userRepository.findActiveByIdentity(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found or inactive: " + username));

        if (user.isLocked()) {
            throw new UsernameNotFoundException("User account is locked: " + username);
        }

        String role = user.getRoleName() == null ? "USER" : user.getRoleName().trim().toUpperCase();
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

        List<String> userPerms = permissions.permissionKeys(role);
        if (userPerms != null) {
            for (String perm : userPerms) {
                if (perm != null && !perm.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(perm.trim().toUpperCase()));
                }
            }
        }

        return new ErpUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                role,
                user.getFullName(),
                authorities
        );
    }
}
