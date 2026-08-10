package org.example.server.auth;

import org.example.server.persistence.entity.UserEntity;
import org.example.server.persistence.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthService(UserRepository users) { this.users = users; }

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
