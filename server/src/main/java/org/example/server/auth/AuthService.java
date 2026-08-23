package org.example.server.auth;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.persistence.entity.UserEntity;
import org.example.server.persistence.repository.RoleRepository;
import org.example.server.persistence.repository.UserRepository;
import org.example.server.security.AuthenticatedUser;
import org.example.server.security.TokenService;
import org.example.server.util.BusinessClock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private static final int MAX_PASSWORD_ATTEMPTS = 5;
    private static final int MAX_MFA_ATTEMPTS = 5;
    private static final String LOCK_FAILED_PASSWORD = "FAILED_PASSWORD";
    private static final String LOCK_FAILED_MFA = "FAILED_MFA";
    private static final String LOCK_ADMIN = "ADMIN";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuthOtpService otp;
    private final SmtpMailService mail;
    private final JpaNativeRepository db;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwords, TokenService tokens,
                       AuthOtpService otp, SmtpMailService mail, JpaNativeRepository db) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.tokens = tokens;
        this.otp = otp;
        this.mail = mail;
        this.db = db;
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        String raw = request == null || request.password() == null ? "" : request.password();
        if (identity.isBlank() || raw.isBlank()) return failedLogin("Invalid email/username or password.");

        UserEntity user = users.findForAuthentication(identity).orElse(null);
        if (user == null) return failedLogin("Invalid email/username or password.");
        if (user.isLocked()) return failedLogin(lockMessage(user));
        if (!passwordMatches(raw, user.getPassword())) return failedPassword(user);

        String role = normalizeRole(user.getRoleName());
        if (role.isBlank() || roles.findByNameIgnoreCase(role).filter(value -> value.isActive()).isEmpty())
            return failedLogin("This account role is not active in Role Master.");

        // Role Master is authoritative for the sign-in policy. ADMIN is password-only;
        // every other active role always requires the email OTP factor. Keep the persisted
        // flag normalized so User Access, audit and future clients show the same policy.
        boolean mfaRequired = requiresMfa(role);
        if (user.isMfaEnabled() != mfaRequired) user.setMfaEnabled(mfaRequired);

        // The password factor was proved successfully. Keep its failure counter independent from MFA failures.
        user.resetPasswordFailures();
        if (!isBcrypt(user.getPassword())) user.setPassword(passwords.encode(raw));

        if (mfaRequired) {
            String email = user.getEmail() == null ? "" : user.getEmail().trim();
            if (email.isBlank()) {
                audit(user.getId(), "MFA_LOGIN_BLOCKED", "MFA is enabled but the account has no registered email", user.getUsername());
                throw new IllegalStateException("MFA is enabled for this account, but no registered email address is available. Contact an administrator.");
            }
            mail.requireConfigured();
            var issued = otp.issue(AuthOtpService.Purpose.LOGIN_MFA, "user:" + user.getId(),
                    loginMfaBinding(user), user.getId(), email, mail);
            audit(user.getId(), "MFA_CHALLENGE_ISSUED", "Sign-in verification code requested", user.getUsername());
            return new AuthDtos.LoginResponse(true, payload(user),
                    issued.sent() ? "Verification code sent" : "Use the latest verification code",
                    null, null, true, issued.challengeId(), maskEmail(email));
        }

        return authenticatedLogin(user, role);
    }

    @Transactional
    public AuthDtos.LoginResponse completeLoginMfa(AuthDtos.LoginMfaCompleteRequest request) {
        if (request == null || request.challengeId() == null || request.challengeId().isBlank()) {
            throw new IllegalArgumentException("The verification challenge is invalid or expired");
        }
        Integer userId = otp.challengeUser(AuthOtpService.Purpose.LOGIN_MFA, request.challengeId());
        UserEntity user = users.findByIdForAuthentication(userId)
                .orElseThrow(() -> new IllegalArgumentException("The verification challenge is invalid or expired"));
        String role = normalizeRole(user.getRoleName());
        if (!user.isActive() || user.isLocked() || role.isBlank()
                || roles.findByNameIgnoreCase(role).filter(value -> value.isActive()).isEmpty()
                || !requiresMfa(role)) {
            audit(userId, "MFA_LOGIN_BLOCKED", "Account state or Role Master policy changed before MFA completion", user.getUsername());
            return failedLogin(user.isLocked() ? lockMessage(user) : "This account is not available for sign in.");
        }

        try {
            var verified = otp.verify(AuthOtpService.Purpose.LOGIN_MFA, request.challengeId(), request.otp());
            if (verified.userId() == null || !verified.userId().equals(userId)) {
                throw new IllegalArgumentException("The verification code is invalid or expired");
            }
        } catch (IllegalArgumentException exception) {
            return failedMfa(user);
        }

        user.resetMfaFailures();
        audit(userId, "MFA_LOGIN_SUCCESS", "Sign-in verification completed", user.getUsername());
        return authenticatedLogin(user, role);
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginMfaChallengeResponse resendLoginMfa(AuthDtos.LoginMfaResendRequest request) {
        if (request == null || request.challengeId() == null || request.challengeId().isBlank()) {
            throw new IllegalArgumentException("The verification challenge is invalid or expired");
        }
        Integer userId = otp.challengeUser(AuthOtpService.Purpose.LOGIN_MFA, request.challengeId());
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("The verification challenge is invalid or expired"));
        String role = normalizeRole(user.getRoleName());
        if (!user.isActive() || user.isLocked() || role.isBlank()
                || roles.findByNameIgnoreCase(role).filter(value -> value.isActive()).isEmpty()
                || !requiresMfa(role)) {
            throw new SecurityException("This account is not available for MFA sign in");
        }
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (email.isBlank()) throw new IllegalStateException("The account has no registered email address");
        mail.requireConfigured();
        var issued = otp.issue(AuthOtpService.Purpose.LOGIN_MFA, "user:" + user.getId(),
                loginMfaBinding(user), user.getId(), email, mail);
        return new AuthDtos.LoginMfaChallengeResponse(true, issued.challengeId(),
                issued.sent() ? "A new verification code was sent"
                        : "Please wait before requesting another code. Use the latest code already sent",
                maskEmail(email));
    }

    private AuthDtos.LoginResponse authenticatedLogin(UserEntity user, String role) {
        user.recordSuccessfulLogin();
        var issued = tokens.issue(new AuthenticatedUser(user.getId(), user.getUsername(), role));
        audit(user.getId(), "LOGIN_SUCCESS", "Authentication completed", user.getUsername());
        return new AuthDtos.LoginResponse(true, payload(user), "OK", issued.value(), issued.expiresAt().toString(),
                false, null, null);
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
        String requestedRole = normalizeRole(request.role());
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
        user.setMfaEnabled(requiresMfa(role.getName()));
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
                registrationBinding(username, email, role, requiresMfa(role)), null, email, mail);
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
                registrationBinding(username, email, roleName, requiresMfa(roleName)));
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
        user.setMfaEnabled(requiresMfa(role.getName()));
        user.setAccessLevel("STANDARD");
        users.save(user);
        return new AuthDtos.OperationResponse(true, "User registered");
    }

    @Transactional(readOnly = true)
    public AuthDtos.ChallengeResponse requestPasswordReset(AuthDtos.PasswordResetOtpRequest request) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        if (identity.isBlank()) return new AuthDtos.ChallengeResponse(false, null, "Email or username is required");
        mail.requireConfigured();
        UserEntity user = users.findActiveByIdentityIncludingLocked(identity).orElse(null);
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
        String priorLockReason = user.getLockReason();
        user.clearAutomaticLock();
        tokens.revokeUser(user.getId());
        audit(user.getId(), "PASSWORD_RESET_COMPLETED", "Password reset completed through verified email OTP", user.getUsername());
        if (LOCK_ADMIN.equals(priorLockReason) && user.isLocked()) {
            return new AuthDtos.OperationResponse(true, "Password updated. This account remains locked by an administrator.");
        }
        return new AuthDtos.OperationResponse(true, "Password updated. Automatic sign-in lock cleared.");
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.RoleOption> loginRoles() {
        return roles.findAll().stream()
                .filter(value -> value != null && value.isActive() && value.getName() != null && !value.getName().isBlank())
                .sorted(Comparator.comparingInt(value -> roleOrder(value.getName())))
                .map(value -> new AuthDtos.RoleOption(normalizeRole(value.getName()), displayRole(value.getName())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.RoleOption> registrationRoles() {
        return loginRoles().stream().filter(option -> !"ADMIN".equals(option.code())).toList();
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

    private AuthDtos.LoginResponse failedLogin(String message) {
        return new AuthDtos.LoginResponse(false, null, message, null, null, false, null, null);
    }

    private AuthDtos.LoginResponse failedPassword(UserEntity user) {
        int attempt = user.recordFailedPasswordAttempt();
        audit(user.getId(), "LOGIN_FAILED", "Incorrect password (attempt " + attempt + " of " + MAX_PASSWORD_ATTEMPTS + ")", user.getUsername());
        if (attempt >= MAX_PASSWORD_ATTEMPTS) {
            autoLock(user, LOCK_FAILED_PASSWORD, "Five incorrect password attempts");
            return failedLogin("Incorrect password. Failed attempt 5 of 5. Your account has been locked. Use Forgot Password or contact an administrator.");
        }
        String suffix = attempt == MAX_PASSWORD_ATTEMPTS - 1
                ? " One attempt remains before this account is locked." : "";
        return failedLogin("Incorrect password. Failed attempt " + attempt + " of " + MAX_PASSWORD_ATTEMPTS + "." + suffix);
    }

    private AuthDtos.LoginResponse failedMfa(UserEntity user) {
        int attempt = user.recordFailedMfaAttempt();
        audit(user.getId(), "MFA_LOGIN_FAILED", "Incorrect sign-in verification code (attempt " + attempt + " of " + MAX_MFA_ATTEMPTS + ")", user.getUsername());
        if (attempt >= MAX_MFA_ATTEMPTS) {
            autoLock(user, LOCK_FAILED_MFA, "Five incorrect MFA verification-code attempts");
            otp.invalidate(AuthOtpService.Purpose.LOGIN_MFA, "user:" + user.getId());
            return failedLogin("Incorrect verification code. Failed attempt 5 of 5. Your account has been locked. Use Forgot Password or contact an administrator.");
        }
        String suffix = attempt == MAX_MFA_ATTEMPTS - 1
                ? " One attempt remains before this account is locked." : "";
        return failedLogin("Incorrect verification code. Failed attempt " + attempt + " of " + MAX_MFA_ATTEMPTS + "." + suffix);
    }

    private void autoLock(UserEntity user, String reason, String detail) {
        user.setLocked(true);
        user.setLockReason(reason);
        tokens.revokeUser(user.getId());
        audit(user.getId(), "ACCOUNT_AUTO_LOCKED", detail, "SYSTEM");
    }

    private String lockMessage(UserEntity user) {
        return switch (user.getLockReason()) {
            case LOCK_FAILED_PASSWORD -> "This account is locked after 5 incorrect password attempts. Use Forgot Password or contact an administrator.";
            case LOCK_FAILED_MFA -> "This account is locked after 5 incorrect verification-code attempts. Use Forgot Password or contact an administrator.";
            case LOCK_ADMIN -> "This account is locked by an administrator. Contact an administrator.";
            default -> "This account is locked. Contact an administrator.";
        };
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
        if ("ADMIN".equals(normalizedRole) || roles.findByNameIgnoreCase(normalizedRole).filter(value -> value.isActive()).isEmpty()) return "Select an active non-Admin role from Role Master";
        if (roles.findByNameIgnoreCase(normalizedRole).map(value -> value.isActive()).orElse(false)) return null;
        return "Selected role is unavailable";
    }

    private String registrationBinding(String username, String email, String role, boolean mfaEnabled) {
        return username.toLowerCase(Locale.ROOT) + "\u0000" + email.toLowerCase(Locale.ROOT) + "\u0000" + role
                + "\u0000" + mfaEnabled;
    }

    private String loginMfaBinding(UserEntity user) {
        return "LOGIN_MFA\u0000" + user.getId() + "\u0000"
                + (user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase(Locale.ROOT));
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "registered email";
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) return "registered email";
        String local = value.substring(0, at);
        String masked = local.length() <= 1 ? "*" : local.substring(0, 1) + "***";
        return masked + value.substring(at);
    }

    private void audit(Integer userId, String action, String detail, String actor) {
        if (userId == null) return;
        try {
            db.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) "
                            + "VALUES('USER',?,?,?,?,?)", userId, action, detail,
                    actor == null || actor.isBlank() ? "SYSTEM" : actor, BusinessClock.nowUtcText());
        } catch (RuntimeException ignored) {
            // Authentication must not fail solely because non-critical audit persistence is unavailable.
        }
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private boolean requiresMfa(String role) {
        return !"ADMIN".equals(normalizeRole(role));
    }

    private String displayRole(String role) {
        String code = normalizeRole(role);
        if ("ADMIN".equals(code)) return "Admin";
        if ("MANAGER".equals(code)) return "Manager";
        if ("SALES".equals(code)) return "Sales";
        if (code.isBlank()) return "";
        return code.charAt(0) + code.substring(1).toLowerCase(Locale.ROOT);
    }

    private int roleOrder(String role) {
        return switch (normalizeRole(role)) {
            case "ADMIN" -> 0;
            case "MANAGER" -> 1;
            case "SALES" -> 2;
            default -> 100;
        };
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
