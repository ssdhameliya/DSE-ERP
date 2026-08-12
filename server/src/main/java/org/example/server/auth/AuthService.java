package org.example.server.auth;

import org.example.server.persistence.entity.UserEntity;
import org.example.server.persistence.repository.RoleRepository;
import org.example.server.persistence.repository.UserRepository;
import org.example.server.security.AuthenticatedUser;
import org.example.server.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "MANAGER", "SALES");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        String raw = request == null || request.password() == null ? "" : request.password();
        if (identity.isBlank() || raw.isBlank()) return failedLogin();
        UserEntity user = users.findActiveByIdentity(identity).orElse(null);
        if (user == null || user.isLocked() || !passwordMatches(raw, user.getPassword())) return failedLogin();
        String role = user.getRoleName() == null ? "" : user.getRoleName().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(role)) return failedLogin();
        if (!isBcrypt(user.getPassword())) user.setPassword(passwords.encode(raw));
        user.recordSuccessfulLogin();
        var issued = tokens.issue(new AuthenticatedUser(user.getId(), user.getUsername(), role));
        return new AuthDtos.LoginResponse(true, payload(user), "OK", issued.value(), issued.expiresAt().toString());
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
    public AuthDtos.OperationResponse completeLogin(AuthDtos.UserIdRequest request, AuthenticatedUser current) {
        if (request == null || request.userId() != current.id()) throw new SecurityException("A user can update only their own session");
        UserEntity user = users.findById(current.id()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.recordSuccessfulLogin();
        return new AuthDtos.OperationResponse(true, "OK");
    }

    @Transactional
    public AuthDtos.OperationResponse register(AuthDtos.RegisterRequest request) {
        if (request == null || request.username() == null || request.username().isBlank())
            return new AuthDtos.OperationResponse(false, "Username is required");
        String passwordError = passwordError(request.password());
        if (passwordError != null) return new AuthDtos.OperationResponse(false, passwordError);
        if (users.findActiveByIdentity(request.username().trim()).isPresent())
            return new AuthDtos.OperationResponse(false, "Username is already registered");
        String requestedRole = request.role() == null ? "" : request.role().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(requestedRole))
            return new AuthDtos.OperationResponse(false, "Select a valid role: Admin, Manager or Sale");
        var role = roles.findByNameIgnoreCase(requestedRole).orElse(null);
        if (role == null || !role.isActive()) return new AuthDtos.OperationResponse(false, "Selected role is unavailable");
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setPassword(passwords.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
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
        return List.of(new AuthDtos.RoleOption("ADMIN", "Admin"),
                        new AuthDtos.RoleOption("MANAGER", "Manager"),
                        new AuthDtos.RoleOption("SALES", "Sale"))
                .stream().filter(option -> roles.findByNameIgnoreCase(option.code()).map(role -> role.isActive()).orElse(false))
                .toList();
    }

    @Transactional
    public AuthDtos.OperationResponse changePassword(AuthDtos.ChangePasswordRequest request, AuthenticatedUser current) {
        if (request == null || request.userId() != current.id()) throw new SecurityException("A user can change only their own password");
        String error = passwordError(request.password());
        if (error != null) return new AuthDtos.OperationResponse(false, error);
        UserEntity user = users.findById(current.id()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwords.encode(request.password()));
        return new AuthDtos.OperationResponse(true, "Password updated");
    }

    public void logout(String token) {
        tokens.revoke(token);
    }

    private AuthDtos.LoginResponse failedLogin() {
        return new AuthDtos.LoginResponse(false, null, "Invalid credentials", null, null);
    }

    private String passwordError(String value) {
        if (value == null || value.length() < 8) return "Password must contain at least 8 characters";
        if (!value.matches(".*[A-Za-z].*") || !value.matches(".*[0-9].*")) return "Password must contain a letter and a number";
        return null;
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
