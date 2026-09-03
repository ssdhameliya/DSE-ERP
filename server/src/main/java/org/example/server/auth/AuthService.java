package org.example.server.auth;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.persistence.entity.UserEntity;
import org.example.server.master.RoleMasterService;
import org.example.server.persistence.repository.UserRepository;
import org.example.server.security.AuthenticatedUser;
import org.example.server.security.TokenService;
import org.example.server.util.BusinessClock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RoleMasterService roleMaster;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuthOtpService otp;
    private final SmtpMailService mail;
    private final JpaNativeRepository db;
    private final TotpService totp;
    private final RegistrationCaptchaService captcha;

    public AuthService(UserRepository users, RoleMasterService roleMaster, PasswordEncoder passwords, TokenService tokens,
                       AuthOtpService otp, SmtpMailService mail, JpaNativeRepository db, TotpService totp, RegistrationCaptchaService captcha) {
        this.users = users;
        this.roleMaster = roleMaster;
        this.passwords = passwords;
        this.tokens = tokens;
        this.otp = otp;
        this.mail = mail;
        this.db = db;
        this.totp = totp;
        this.captcha = captcha;
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        String raw = request == null || request.password() == null ? "" : request.password();
        if (identity.isBlank() || raw.isBlank()) return failedLogin("Invalid email/username or password.");

        UserEntity user = users.findForAuthentication(identity).orElse(null);
        if (user == null) {
            var pending = pendingRegistration(identity);
            if (pending != null && passwordMatches(raw, String.valueOf(pending.get("password_hash"))))
                return failedLogin("Account Approval Pending. Your verified registration is awaiting administrator approval. You cannot sign in until an administrator approves your account.");
            return failedLogin("Invalid email/username or password.");
        }
        if (!"APPROVED".equals(user.getApprovalStatus()) || !user.isActive())
            return failedLogin("Account Approval Pending. This account is not active for sign in.");
        if (user.isLocked()) return failedLogin(lockMessage(user));
        // Release gate: plaintext/legacy password values are never compared during sign-in.
        // Existing legacy accounts must be reset once by an administrator (or via Forgot Password),
        // which writes the normal BCrypt value used by all current account flows.
        if (!isBcrypt(user.getPassword())) {
            audit(user.getId(), "LEGACY_PASSWORD_BLOCKED", "Legacy password format requires secure reset", user.getUsername());
            return failedLogin("This account uses a legacy password format. Reset the password before signing in.");
        }
        if (!passwordMatches(raw, user.getPassword())) return failedPassword(user);

        String role = normalizeRole(user.getRoleName());
        if (role.isBlank() || !roleMaster.isActive(role))
            return failedLogin("This account role is not active in Role Master.");

        // ADMIN keeps the existing password-only production flow. Every non-Admin account
        // uses an RFC-6238 authenticator token enrolled during approved registration.
        boolean mfaRequired = requiresMfa(user, role);
        user.resetPasswordFailures();

        if (mfaRequired) {
            if (user.getTotpSecretEnc() == null || user.getTotpSecretEnc().isBlank()) {
                // Safe upgrade bridge for accounts created before 9.0.50: retain their existing email-OTP
                // factor instead of locking them out. Every newly approved registration has TOTP.
                String email=user.getEmail()==null?"":user.getEmail().trim();
                if(email.isBlank()) return failedLogin("MFA setup is incomplete for this legacy account. Contact an administrator.");
                mail.requireConfigured();
                var issued=otp.issue(AuthOtpService.Purpose.LOGIN_MFA,"user:"+user.getId(),loginMfaBinding(user),user.getId(),email,mail);
                audit(user.getId(),"LEGACY_MFA_CHALLENGE_ISSUED","Existing account email-OTP compatibility bridge",user.getUsername());
                return new AuthDtos.LoginResponse(true,payload(user),"Verification code sent to registered email",null,null,true,issued.challengeId(),maskEmail(email));
            }
            String challenge = totp.issueLogin(user.getId());
            audit(user.getId(), "MFA_CHALLENGE_ISSUED", "Authenticator token requested", user.getUsername());
            return new AuthDtos.LoginResponse(true, payload(user), "Enter the current 6-digit code from your authenticator app",
                    null, null, true, challenge, "Authenticator app");
        }

        return authenticatedLogin(user, role);
    }

    @Transactional
    public AuthDtos.LoginResponse completeLoginMfa(AuthDtos.LoginMfaCompleteRequest request) {
        if(request==null||request.challengeId()==null||request.challengeId().isBlank())throw new IllegalArgumentException("The MFA challenge is invalid or expired");
        Integer userId=totp.peekLogin(request.challengeId()); boolean authenticator=userId!=null;
        if(userId==null){ try{userId=otp.challengeUser(AuthOtpService.Purpose.LOGIN_MFA,request.challengeId());}catch(RuntimeException ignored){userId=null;} }
        if(userId==null)throw new IllegalArgumentException("The MFA challenge is invalid or expired");
        UserEntity user=users.findByIdForAuthentication(userId).orElseThrow(()->new IllegalArgumentException("The MFA challenge is invalid or expired"));
        String role=normalizeRole(user.getRoleName());
        if(!user.isActive()||user.isLocked()||!"APPROVED".equals(user.getApprovalStatus())||role.isBlank()||!roleMaster.isActive(role)||!requiresMfa(user,role))return failedLogin(user.isLocked()?lockMessage(user):"This account is not available for sign in.");
        boolean verified;
        if(authenticator){ verified=totp.verifyEncrypted(user.getTotpSecretEnc(),request.otp()); if(verified)totp.consumeLogin(request.challengeId()); }
        else { try{var v=otp.verify(AuthOtpService.Purpose.LOGIN_MFA,request.challengeId(),request.otp());verified=v.userId()!=null&&v.userId().equals(userId);}catch(IllegalArgumentException ex){verified=false;} }
        if(!verified)return failedMfa(user);
        user.resetMfaFailures();audit(userId,authenticator?"MFA_LOGIN_SUCCESS":"LEGACY_MFA_LOGIN_SUCCESS",authenticator?"Authenticator verification completed":"Existing-account email OTP verification completed",user.getUsername());return authenticatedLogin(user,role);
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginMfaChallengeResponse resendLoginMfa(AuthDtos.LoginMfaResendRequest request) {
        if(request==null||request.challengeId()==null||request.challengeId().isBlank())throw new IllegalArgumentException("The MFA challenge is invalid or expired");
        Integer authenticatorUser=totp.peekLogin(request.challengeId());
        if(authenticatorUser!=null)return new AuthDtos.LoginMfaChallengeResponse(true,request.challengeId(),"Authenticator codes refresh automatically every 30 seconds. Enter the current 6-digit code.","Authenticator app");
        Integer userId=otp.challengeUser(AuthOtpService.Purpose.LOGIN_MFA,request.challengeId());UserEntity user=users.findById(userId).orElseThrow(()->new IllegalArgumentException("The MFA challenge is invalid or expired"));String email=user.getEmail()==null?"":user.getEmail().trim();mail.requireConfigured();var issued=otp.issue(AuthOtpService.Purpose.LOGIN_MFA,"user:"+user.getId(),loginMfaBinding(user),user.getId(),email,mail);return new AuthDtos.LoginMfaChallengeResponse(true,issued.challengeId(),issued.sent()?"A new verification code was sent":"Use the latest verification code already sent",maskEmail(email));
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
        // Authenticated Admin/User Management only. Public self-registration never calls this path.
        if (request == null || request.username() == null || request.username().isBlank())
            return new AuthDtos.OperationResponse(false, "Username is required");
        String passwordError = passwordError(request.password()); if (passwordError != null) return new AuthDtos.OperationResponse(false, passwordError);
        if (users.findActiveByIdentity(request.username().trim()).isPresent()) return new AuthDtos.OperationResponse(false, "Username is already registered");
        String requestedRole=normalizeRole(request.role()); RoleMasterService.RoleDefinition role;
        try { role=roleMaster.requireActive(requestedRole); } catch(IllegalArgumentException ignored){return new AuthDtos.OperationResponse(false,"Selected role is unavailable");}
        UserEntity user=new UserEntity(); user.setUsername(request.username().trim()); user.setPassword(passwords.encode(request.password()));
        user.setFullName(request.fullName()); user.setEmail(request.email()); user.setRole(role.code()); user.setActive(true); user.setApprovalStatus("APPROVED"); user.setLocked(false);
        // Preserve existing Admin behavior. Admin-created non-Admin users must enroll an authenticator before login.
        user.setMfaEnabled(mfaForRequestedUser(role.code(), request.mfaEnabled())); user.setAccessLevel("STANDARD"); users.save(user);
        return new AuthDtos.OperationResponse(true,"User registered");
    }

    @Transactional(readOnly = true)
    public AuthDtos.CaptchaResponse registrationCaptcha(){ return captcha.issue(); }

    @Transactional(readOnly = true)
    public AuthDtos.ChallengeResponse requestRegistrationOtp(AuthDtos.RegistrationOtpRequest request) {
        String validation=publicRegistrationError(request==null?null:request.username(),request==null?null:request.fullName(),request==null?null:request.email(),request==null?null:request.role());
        if(validation!=null)return new AuthDtos.ChallengeResponse(false,null,validation);
        if(request==null || !captcha.verify(request.captchaChallengeId(),request.captchaAnswer()))
            return new AuthDtos.ChallengeResponse(false,null,"CAPTCHA verification failed or expired. Refresh the CAPTCHA and try again.");
        String username=request.username().trim(), email=request.email().trim(), role=normalizeRole(request.role());
        if("ADMIN".equals(role))return new AuthDtos.ChallengeResponse(false,null,"Administrator accounts cannot be created through self-registration.");
        if(users.existsByUsernameIgnoreCase(username)||openRegistrationExists("username",username))return new AuthDtos.ChallengeResponse(false,null,"Username is already registered or awaiting approval");
        if(users.existsByEmailIgnoreCase(email)||openRegistrationExists("email",email))return new AuthDtos.ChallengeResponse(false,null,"Email is already registered or awaiting approval");
        mail.requireConfigured();
        var issued=otp.issue(AuthOtpService.Purpose.REGISTRATION,email.toLowerCase(Locale.ROOT),registrationBinding(username,email,role,true),null,email,mail);
        return new AuthDtos.ChallengeResponse(true,issued.challengeId(),issued.sent()?"Verification code sent to your email":"A verification code was already sent. Please use the latest code");
    }

    @Transactional
    public AuthDtos.RegistrationMfaSetupResponse verifyRegistrationEmail(AuthDtos.RegistrationEmailVerifyRequest request) {
        String validation=publicRegistrationError(request==null?null:request.username(),request==null?null:request.fullName(),request==null?null:request.email(),request==null?null:request.role());
        if(validation!=null)throw new IllegalArgumentException(validation);
        String passwordError=passwordError(request.password());if(passwordError!=null)throw new IllegalArgumentException(passwordError);
        String username=request.username().trim(),email=request.email().trim(),role=normalizeRole(request.role());
        if("ADMIN".equals(role))throw new SecurityException("Administrator accounts cannot be created through self-registration.");
        otp.verify(AuthOtpService.Purpose.REGISTRATION,request.challengeId(),request.otp(),registrationBinding(username,email,role,true));
        if(users.existsByUsernameIgnoreCase(username)||openRegistrationExists("username",username))throw new IllegalArgumentException("Username is already registered or awaiting approval");
        if(users.existsByEmailIgnoreCase(email)||openRegistrationExists("email",email))throw new IllegalArgumentException("Email is already registered or awaiting approval");
        var setup=totp.createSetup(username,email);
        Long id=db.queryForObject("INSERT INTO registration_request(username,password_hash,full_name,email,requested_role,totp_secret_enc,email_verified,mfa_verified,status,requested_at,row_version) VALUES(?,?,?,?,?,?,1,0,'MFA_ENROLLMENT_PENDING',CURRENT_TIMESTAMP,0) RETURNING id",Long.class,username,passwords.encode(request.password()),request.fullName().trim(),email,role,setup.encryptedSecret());
        return new AuthDtos.RegistrationMfaSetupResponse(true,id,setup.manualSecret(),setup.provisioningUri(),"Email verified. Add DSE ERP to Google Authenticator or Microsoft Authenticator, then enter the current 6-digit code.");
    }

    @Transactional
    public AuthDtos.OperationResponse completeRegistrationMfa(AuthDtos.RegistrationMfaCompleteRequest request){
        if(request==null||request.registrationId()<=0)return new AuthDtos.OperationResponse(false,"Registration request is invalid");
        var row=db.queryForMap("SELECT id,totp_secret_enc,status,username,email FROM registration_request WHERE id=? FOR UPDATE",request.registrationId());
        String status=String.valueOf(row.get("status")); if(!"MFA_ENROLLMENT_PENDING".equals(status))return new AuthDtos.OperationResponse(false,"This registration request has already been submitted or processed");
        if(!totp.verifyEncrypted(String.valueOf(row.get("totp_secret_enc")),request.otp()))return new AuthDtos.OperationResponse(false,"Authenticator code is incorrect. Enter the current 6-digit code.");
        db.update("UPDATE registration_request SET mfa_verified=1,status='PENDING_ADMIN_APPROVAL',row_version=row_version+1 WHERE id=?",request.registrationId());
        db.update("INSERT INTO notifications(title,message,severity,category,is_read,target_fxml,reference_no,module_key,record_id,action_code,created_at) VALUES(?,?,?,?,0,?,?,?,?,?,?)",
                "New User Approval Required", String.valueOf(row.get("username"))+" completed email and authenticator verification and is awaiting Admin approval.",
                "INFO","SECURITY","/fxml/pages/RegistrationApprovals.fxml",String.valueOf(request.registrationId()),"USER_REGISTRATION",request.registrationId(),"REVIEW",System.currentTimeMillis());
        auditRegistration(request.registrationId(),"REGISTRATION_PENDING_APPROVAL","Email and authenticator verified; awaiting administrator approval",String.valueOf(row.get("username")));
        return new AuthDtos.OperationResponse(true,"Registration submitted successfully. Your account is awaiting administrator approval. You cannot sign in until it is approved.");
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
        String passwordError=passwordError(request==null?null:request.password()); if(passwordError!=null)return new AuthDtos.OperationResponse(false,passwordError);
        var verified=otp.verify(AuthOtpService.Purpose.PASSWORD_RESET,request.challengeId(),request.otp(),"");
        if(verified.userId()==null)throw new IllegalArgumentException("The verification code is invalid or expired");
        UserEntity user=users.findById(verified.userId()).orElseThrow(()->new IllegalArgumentException("The verification code is invalid or expired"));
        String role=normalizeRole(user.getRoleName());
        if(requiresMfa(user,role) && user.getTotpSecretEnc()!=null && !user.getTotpSecretEnc().isBlank()){
            if(!totp.verifyEncrypted(user.getTotpSecretEnc(),request.totp()))return new AuthDtos.OperationResponse(false,"Authenticator code is incorrect");
        }
        user.setPassword(passwords.encode(request.password())); String prior=user.getLockReason(); user.clearAutomaticLock(); tokens.revokeUser(user.getId());
        audit(user.getId(),"PASSWORD_RESET_COMPLETED",requiresMfa(user,role)?"Password reset completed through email OTP + authenticator":"Password reset completed through email OTP under current MFA policy",user.getUsername());
        if(LOCK_ADMIN.equals(prior)&&user.isLocked())return new AuthDtos.OperationResponse(true,"Password updated. This account remains locked by an administrator.");
        return new AuthDtos.OperationResponse(true,"Password updated. Automatic sign-in lock cleared.");
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.RoleOption> loginRoles() {
        return roleMaster.activeRoles().stream()
                .map(value -> new AuthDtos.RoleOption(value.code(), value.displayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.RoleOption> registrationRoles() {
        try {
            RoleMasterService.RoleDefinition role = roleMaster.selfRegistrationRole();
            return List.of(new AuthDtos.RoleOption(role.code(), role.displayName()));
        } catch (IllegalStateException unavailable) {
            return List.of();
        }
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

    @Transactional
    public AuthDtos.SessionExtendResponse extendSession(String currentToken, AuthenticatedUser current) {
        if (current == null) throw new SecurityException("Authentication required");
        UserEntity user = users.findById(current.id()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isActive() || user.isLocked() || !"APPROVED".equals(user.getApprovalStatus()))
            throw new SecurityException("Account is not available for session extension");
        var issued = tokens.issue(new AuthenticatedUser(user.getId(), user.getUsername(), normalizeRole(user.getRoleName())));
        tokens.revoke(currentToken);
        audit(user.getId(), "SESSION_EXTENDED", "Idle-timeout session extended", user.getUsername());
        return new AuthDtos.SessionExtendResponse(true, "Session extended", issued.value(), issued.expiresAt().toString());
    }

    public void logout(String token, AuthenticatedUser current, String reason) {
        if (current != null) {
            String action = "AUTO_LOGOUT_IDLE".equalsIgnoreCase(reason) ? "AUTO_LOGOUT_IDLE" : "MANUAL_LOGOUT";
            audit(current.id(), action, action.equals("AUTO_LOGOUT_IDLE") ? "Session ended after inactivity timeout" : "User signed out", current.username());
        }
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
        RoleMasterService.RoleDefinition registrationRole;
        try { registrationRole = roleMaster.selfRegistrationRole(); }
        catch (IllegalStateException unavailable) { return "Public registration is not available because no active non-Admin registration role is configured"; }
        if (!registrationRole.code().equalsIgnoreCase(normalizedRole))
            return "Public registration is restricted to the active " + registrationRole.displayName() + " role";
        return null;
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

    private java.util.Map<String,Object> pendingRegistration(String identity){
        if(identity==null||identity.isBlank())return null;
        var rows=db.queryForList("SELECT id,username,email,password_hash,status FROM registration_request WHERE (LOWER(username)=LOWER(?) OR LOWER(email)=LOWER(?)) AND status IN ('MFA_ENROLLMENT_PENDING','PENDING_ADMIN_APPROVAL') ORDER BY id DESC LIMIT 1",identity,identity);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private boolean openRegistrationExists(String field,String value){
        if(!"username".equals(field)&&!"email".equals(field))throw new IllegalArgumentException("Invalid registration field");
        Long count=db.queryForObject("SELECT COUNT(*) FROM registration_request WHERE LOWER("+field+")=LOWER(?) AND status IN ('MFA_ENROLLMENT_PENDING','PENDING_ADMIN_APPROVAL')",Long.class,value);
        return count!=null&&count>0;
    }
    private void auditRegistration(Long id,String action,String detail,String actor){
        try{db.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('REGISTRATION',?,?,?,?,?)",id,action,detail,actor==null||actor.isBlank()?"SYSTEM":actor,BusinessClock.nowUtcText());}catch(RuntimeException ignored){}
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private boolean requiresMfa(UserEntity user, String role) {
        String policy = mfaPolicy();
        if ("DISABLED".equals(policy)) return false;
        if ("ADMIN_CONTROLLED".equals(policy)) return user != null && user.isMfaEnabled();
        return !"ADMIN".equals(normalizeRole(role));
    }

    private boolean mfaForRequestedUser(String role, boolean requested) {
        String policy = mfaPolicy();
        if ("DISABLED".equals(policy)) return false;
        if ("ADMIN_CONTROLLED".equals(policy)) return requested;
        return !"ADMIN".equals(normalizeRole(role));
    }

    private String mfaPolicy() {
        try {
            String value = db.queryForObject("SELECT setting_value FROM application_setting WHERE setting_key='security.auth.mfa.policy'", String.class);
            String normalized = value == null ? "REQUIRED" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            return switch (normalized) { case "ADMIN_CONTROLLED", "DISABLED" -> normalized; default -> "REQUIRED"; };
        } catch (RuntimeException ignored) { return "REQUIRED"; }
    }

    private boolean passwordMatches(String raw, String stored) {
        return isBcrypt(stored) && passwords.matches(raw, stored);
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
