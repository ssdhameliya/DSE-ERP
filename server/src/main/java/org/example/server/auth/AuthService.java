package org.example.server.auth;

import org.example.server.persistence.entity.UserEntity;
import org.example.server.persistence.repository.UserRepository;
import org.example.server.persistence.repository.RoleRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthService(UserRepository users, RoleRepository roles) { this.users = users; this.roles = roles; }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        String raw = request == null || request.password() == null ? "" : request.password();
        if (identity.isBlank() || raw.isBlank()) return new AuthDtos.LoginResponse(false, null, "Invalid credentials");
        UserEntity user = users.findActiveByIdentity(identity).orElse(null);
        if (user == null || !passwordMatches(raw, user.getPassword())) {
            return new AuthDtos.LoginResponse(false, null, "Invalid credentials");
        }
        if (!isBcrypt(user.getPassword())) user.setPassword(passwords.encode(raw));
        return new AuthDtos.LoginResponse(true, payload(user), "OK");
    }

    @Transactional(readOnly = true)
    public AuthDtos.LookupResponse lookup(AuthDtos.LookupRequest request) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        if (identity.isBlank()) return new AuthDtos.LookupResponse(false, null);
        return users.findActiveByIdentity(identity)
                .map(user -> new AuthDtos.LookupResponse(true, payload(user)))
                .orElseGet(() -> new AuthDtos.LookupResponse(false, null));
    }

    @Transactional
    public AuthDtos.OperationResponse completeLogin(AuthDtos.UserIdRequest request) {
        UserEntity user = users.findById(request.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.recordSuccessfulLogin();
        return new AuthDtos.OperationResponse(true, "OK");
    }

    @Transactional
    public AuthDtos.OperationResponse register(AuthDtos.RegisterRequest request) {
        if (request == null || request.username() == null || request.username().isBlank())
            return new AuthDtos.OperationResponse(false, "Username is required");
        if (request.password() == null || request.password().length() < 6)
            return new AuthDtos.OperationResponse(false, "Password must contain at least 6 characters");
        if (users.findActiveByIdentity(request.username().trim()).isPresent())
            return new AuthDtos.OperationResponse(false, "Username is already registered");
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setPassword(passwords.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        String requestedRole = request.role() == null ? "" : request.role().trim().toUpperCase(Locale.ROOT);
        if (!List.of("ADMIN", "MANAGER", "SALES").contains(requestedRole)) {
            return new AuthDtos.OperationResponse(false, "Select a valid role: ADMIN, MANAGER or SALES");
        }
        var role = roles.findByNameIgnoreCase(requestedRole).orElse(null);
        if (role == null || !role.isActive()) {
            return new AuthDtos.OperationResponse(false, requestedRole + " role is not available");
        }
        user.setAssignedRole(role);
        user.setRole(role.getName());
        user.setActive(true);
        user.setLocked(false);
        user.setMfaEnabled(false);
        user.setAccessLevel("STANDARD");
        users.save(user);
        return new AuthDtos.OperationResponse(true, "User registered");
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.RoleOption> registrationRoles() {
        return List.of(
                new AuthDtos.RoleOption("ADMIN", "Admin"),
                new AuthDtos.RoleOption("MANAGER", "Manager"),
                new AuthDtos.RoleOption("SALES", "Sales")
        ).stream()
         .filter(option -> roles.findByNameIgnoreCase(option.code()).map(role -> role.isActive()).orElse(false))
         .toList();
    }

    @Transactional
    public AuthDtos.OperationResponse changePassword(AuthDtos.ChangePasswordRequest request) {
        if (request.password() == null || request.password().length() < 6) {
            return new AuthDtos.OperationResponse(false, "Password must contain at least 6 characters");
        }
        UserEntity user = users.findById(request.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwords.encode(request.password()));
        return new AuthDtos.OperationResponse(true, "Password updated");
    }

    private boolean passwordMatches(String raw, String stored) {
        return stored != null && (isBcrypt(stored) ? passwords.matches(raw, stored) : stored.equals(raw));
    }

    private boolean isBcrypt(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    private AuthDtos.UserPayload payload(UserEntity user) {
        return new AuthDtos.UserPayload(user.getId(), user.getUsername(), user.getFullName(), user.getRoleName(),
                user.getRoleId(), user.getEmail(), user.isActive(), user.getDepartment(), user.getBranch(),
                user.getAccessLevel(), user.isLocked(), user.isMfaEnabled());
    }
}
