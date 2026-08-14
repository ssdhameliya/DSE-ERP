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
    private static final List<String> PUBLIC_REGISTRATION_ROLES = List.of("MANAGER", "SALES");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuthOtpService otp;
    private final SmtpMailService mail;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwords, TokenService tokens,
                       AuthOtpService otp, SmtpMailService mail) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.tokens = tokens;
        this.otp = otp;
        this.mail = mail;
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
    public AuthDtos.ChallengeResponse requestRegistrationOtp(AuthDtos.RegistrationOtpRequest request) {
        String validation = publicRegistrationError(request == null ? null : request.username(),
                request == null ? null : request.fullName(), request == null ? null : request.email(),
                request == null ? null : request.role());
        if (validation != null) return new AuthDtos.ChallengeResponse(false, null, validation);
        String username = request.username().trim();
        String email = request.email().trim();
        String role = normalizeRole(request.role());
        if (users.existsByUsernameIgnoreCase(username))
            return new AuthDtos.ChallengeResponse(false, null, "Username is already registered");
        if (users.existsByEmailIgnoreCase(email))
            return new AuthDtos.ChallengeResponse(false, null, "Email is already registered");
        var issued = otp.issue(AuthOtpService.Purpose.REGISTRATION, email.toLowerCase(Locale.ROOT),
                registrationBinding(username, email, role), null, email, mail);
        return new AuthDtos.ChallengeResponse(true, issued.challengeId(), issued.sent()
                ? "Verification code sent to your email"
                : "A verification code was already sent. Please use the latest code");
    }

    @Transactional
    public AuthDtos.OperationResponse completeRegistration(AuthDtos.RegistrationCompleteRequest request) {
        String validation = publicRegistrationError(request == null ? null : request.username(),
                request == null ? null : request.fullName(), request == null ? null : request.email(),
                request == null ? null : request.role());
        if (validation != null) return new AuthDtos.OperationResponse(false, validation);
        String passwordError = passwordError(request.password());
        if (passwordError != null) return new AuthDtos.OperationResponse(false, passwordError);
        String username = request.username().trim();
        String email = request.email().trim();
        String roleName = normalizeRole(request.role());
        otp.verify(AuthOtpService.Purpose.REGISTRATION, request.challengeId(), request.otp(),
                registrationBinding(username, email, roleName));
        if (users.existsByUsernameIgnoreCase(username))
            return new AuthDtos.OperationResponse(false, "Username is already registered");
        if (users.existsByEmailIgnoreCase(email))
            return new AuthDtos.OperationResponse(false, "Email is already registered");
        var role = roles.findByNameIgnoreCase(roleName).filter(value -> value.isActive()).orElse(null);
        if (role == null) return new AuthDtos.OperationResponse(false, "Selected role is unavailable");
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwords.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
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
    public AuthDtos.ChallengeResponse requestPasswordReset(AuthDtos.PasswordResetOtpRequest request) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        if (identity.isBlank()) return new AuthDtos.ChallengeResponse(false, null, "Email or username is required");
        mail.requireConfigured();
        UserEntity user = users.findActiveByIdentity(identity).orElse(null);
        Integer userId = user == null ? null : user.getId();
        String recipient = user == null ? null : user.getEmail();
        if (recipient != null && recipient.isBlank()) recipient = null;
        String key = userId == null ? "identity:" + identity.toLowerCase(Locale.ROOT) : "user:" + userId;
        var issued = otp.issue(AuthOtpService.Purpose.PASSWORD_RESET, key, "", userId, recipient, mail);
        return new AuthDtos.ChallengeResponse(true, issued.challengeId(),
                "If the account is eligible, a reset code has been sent to its registered email");
    }

    @Transactional
    public AuthDtos.OperationResponse completePasswordReset(AuthDtos.PasswordResetCompleteRequest request) {
        String passwordError = passwordError(request == null ? null : request.password());
        if (passwordError != null) return new AuthDtos.OperationResponse(false, passwordError);
        var verified = otp.verify(AuthOtpService.Purpose.PASSWORD_RESET, request.challengeId(), request.otp(), "");
        if (verified.userId() == null) throw new IllegalArgumentException("The verification code is invalid or expired");
        UserEntity user = users.findById(verified.userId())
                .orElseThrow(() -> new IllegalArgumentException("The verification code is invalid or expired"));
        user.setPassword(passwords.encode(request.password()));
        tokens.revokeUser(user.getId());
        return new AuthDtos.OperationResponse(true, "Password updated");
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.RoleOption> registrationRoles() {
        return List.of(new AuthDtos.RoleOption("MANAGER", "Manager"),
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
        if (!passwordMatches(request.currentPassword(), user.getPassword())) {
            return new AuthDtos.OperationResponse(false, "Current password is incorrect");
        }
        user.setPassword(passwords.encode(request.password()));
        tokens.revokeUser(user.getId());
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

    private String publicRegistrationError(String username, String fullName, String email, String role) {
        if (username == null || username.isBlank()) return "Username is required";
        if (fullName == null || fullName.isBlank()) return "Full name is required";
        if (email == null || !email.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))
            return "A valid email address is required";
        String normalizedRole = normalizeRole(role);
        if (!PUBLIC_REGISTRATION_ROLES.contains(normalizedRole)) return "Select Manager or Sale";
        if (roles.findByNameIgnoreCase(normalizedRole).map(value -> value.isActive()).orElse(false)) return null;
        return "Selected role is unavailable";
    }

    private String registrationBinding(String username, String email, String role) {
        return username.toLowerCase(Locale.ROOT) + "\u0000" + email.toLowerCase(Locale.ROOT) + "\u0000" + role;
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
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
