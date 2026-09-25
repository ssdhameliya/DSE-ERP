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
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, String sourceAddress) {
        String identity = request == null || request.identity() == null ? "" : request.identity().trim();
        String raw = request == null || request.password() == null ? "" : request.password();
        String source = normalizeLoginSource(sourceAddress);
        if (identity.isBlank() || raw.isBlank()) {
            recordLoginFailure(source, identity);
            return failedLogin("Invalid email/username or password.");
        }
        if (loginBlocked(source, identity)) {
            return failedLogin("Too many sign-in attempts. Try again later.");
        }

        UserEntity user = users.findForAuthentication(identity).orElse(null);
        if (user == null) {
            var pending = pendingRegistration(identity);
            if (pending != null && passwordMatches(raw, String.valueOf(pending.get("password_hash")))) {
                clearLoginThrottle(source, identity);
                return failedLogin("Account Approval Pending. Your verified registration is awaiting administrator approval. You cannot sign in until an administrator approves your account.");
            }
            recordLoginFailure(source, identity);
            return failedLogin("Invalid email/username or password.");
        }

        // Release gate: plaintext/legacy password values are never compared during sign-in.
        if (!isBcrypt(user.getPassword())) {
            audit(user.getId(), "LEGACY_PASSWORD_BLOCKED", "Legacy password format requires secure reset", user.getUsername());
            return failedLogin("This account uses a legacy password format. Reset the password before signing in.");
        }
        if (!passwordMatches(raw, user.getPassword())) {
            recordLoginFailure(source, identity);
            return failedPassword(user);
        }
        clearLoginThrottle(source, identity);

        if (!"APPROVED".equals(user.getApprovalStatus()) || !user.isActive())
            return failedLogin("Account Approval Pending. This account is not active for sign in.");
        if (user.isLocked()) return failedLogin(lockMessage(user));

        String role = normalizeRole(user.getRoleName());
        if (role.isBlank() || !roleMaster.isActive(role))
            return failedLogin("This account role is not active in Role Master.");

        boolean mfaRequired = requiresMfa(user, role);
        user.resetPasswordFailures();

        if (mfaRequired) {
            boolean enrollmentPending = enrollmentPending(user.getId());
            if (user.getTotpSecretEnc() == null || user.getTotpSecretEnc().isBlank()) {
                String email = user.getEmail() == null ? "" : user.getEmail().trim();
                TotpService.Setup setup = totp.createSetup(user.getUsername(), email);
                user.setTotpSecretEnc(setup.encryptedSecret());
                user.setMfaEnabled(true);
                markEnrollmentPending(user.getId());
                enrollmentPending = true;
                audit(user.getId(), "MFA_ENROLLMENT_PROVISIONED",
                        "Authenticator enrollment was provisioned after password verification", user.getUsername());
            }
            String challenge = totp.issueLogin(user.getId());
            if (enrollmentPending) {
                audit(user.getId(), "MFA_ENROLLMENT_CHALLENGE_ISSUED", "Authenticator enrollment verification requested", user.getUsername());
                return new AuthDtos.LoginResponse(true, payload(user), "Set up Google or Microsoft Authenticator, then verify the current 6-digit code",
                        null, null, true, challenge, "Authenticator enrollment");
            }
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
        boolean wasEnrollmentPending = authenticator && enrollmentPending(userId);
        boolean verified;
        if(authenticator){ verified=totp.verifyEncrypted(user.getTotpSecretEnc(),request.otp()); if(verified)totp.consumeLogin(request.challengeId()); }
        else { try{var v=otp.verify(AuthOtpService.Purpose.LOGIN_MFA,request.challengeId(),request.otp());verified=v.userId()!=null&&v.userId().equals(userId);}catch(IllegalArgumentException ex){verified=false;} }
        if(!verified)return failedMfa(user);
        if(wasEnrollmentPending){
            clearEnrollmentPending(userId);
            audit(userId,"MFA_ENROLLMENT_COMPLETED","Authenticator enrollment verified and activated",user.getUsername());
        }
        user.resetMfaFailures();audit(userId,authenticator?"MFA_LOGIN_SUCCESS":"LEGACY_MFA_LOGIN_SUCCESS",authenticator?"Authenticator verification completed":"Existing-account email OTP verification completed",user.getUsername());return authenticatedLogin(user,role);
    }

    @Transactional
    public AuthDtos.LoginMfaEnrollmentResponse loginMfaEnrollment(AuthDtos.LoginMfaEnrollmentRequest request) {
        if(request==null||request.challengeId()==null||request.challengeId().isBlank())
            throw new IllegalArgumentException("The MFA enrollment challenge is invalid or expired");
        Integer userId=totp.peekLogin(request.challengeId());
        if(userId==null||!enrollmentPending(userId))
            throw new IllegalArgumentException("The MFA enrollment challenge is invalid or expired");
        UserEntity user=users.findByIdForAuthentication(userId).orElseThrow(()->new IllegalArgumentException("The MFA enrollment challenge is invalid or expired"));
        String role=normalizeRole(user.getRoleName());
        if(!user.isActive()||user.isLocked()||!requiresMfa(user,role))
            throw new IllegalArgumentException("This account is not available for authenticator enrollment");
        if(user.getTotpSecretEnc()==null||user.getTotpSecretEnc().isBlank())
            throw new IllegalStateException("Authenticator enrollment is not provisioned for this account");
        TotpService.Setup setup=totp.existingSetup(user.getUsername(),user.getEmail(),user.getTotpSecretEnc());
        return new AuthDtos.LoginMfaEnrollmentResponse(true,request.challengeId(),setup.manualSecret(),setup.provisioningUri(),
                "Scan the QR code with Google or Microsoft Authenticator, then enter the current 6-digit code.");
    }

    @Transactional
    public AuthDtos.LoginMfaChallengeResponse requestLoginMfaRecovery(AuthDtos.LoginMfaRecoveryRequest request) {
        if (request == null || request.challengeId() == null || request.challengeId().isBlank())
            throw new IllegalArgumentException("The MFA challenge is invalid or expired");
        Integer userId = totp.peekLogin(request.challengeId());
        if (userId == null) throw new IllegalArgumentException("The MFA challenge is invalid or expired");
        UserEntity user = users.findByIdForAuthentication(userId)
                .orElseThrow(() -> new IllegalArgumentException("The MFA challenge is invalid or expired"));
        String role = normalizeRole(user.getRoleName());
        if (!user.isActive() || user.isLocked() || !"APPROVED".equals(user.getApprovalStatus())
                || role.isBlank() || !roleMaster.isActive(role) || !requiresMfa(user, role))
            throw new IllegalArgumentException("This account is not available for authenticator recovery");
        if (enrollmentPending(userId))
            throw new IllegalStateException("Authenticator enrollment is already required. Reopen the QR setup instead of resetting again.");
        if (user.getTotpSecretEnc() == null || user.getTotpSecretEnc().isBlank())
            throw new IllegalStateException("Authenticator enrollment is already required for this account.");
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (email.isBlank())
            throw new IllegalStateException("No registered email is available for self-service authenticator recovery. Contact an administrator.");
        mail.requireConfigured();
        var issued = otp.issue(AuthOtpService.Purpose.MFA_RECOVERY, "user:" + userId,
                mfaRecoveryBinding(user, request.challengeId()), userId, email, mail);
        audit(userId, "MFA_RECOVERY_EMAIL_ISSUED",
                "Authenticator recovery verification was requested after password verification", user.getUsername());
        return new AuthDtos.LoginMfaChallengeResponse(true, issued.challengeId(),
                issued.sent() ? "A verification code was sent to the registered email"
                        : "Use the latest authenticator recovery code already sent",
                maskEmail(email));
    }

    @Transactional
    public AuthDtos.LoginMfaEnrollmentResponse completeLoginMfaRecovery(AuthDtos.LoginMfaRecoveryCompleteRequest request) {
        if (request == null || request.loginChallengeId() == null || request.loginChallengeId().isBlank()
                || request.recoveryChallengeId() == null || request.recoveryChallengeId().isBlank())
            throw new IllegalArgumentException("Authenticator recovery is invalid or expired");
        Integer userId = totp.peekLogin(request.loginChallengeId());
        if (userId == null) throw new IllegalArgumentException("Authenticator recovery is invalid or expired");
        UserEntity user = users.findByIdForAuthentication(userId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticator recovery is invalid or expired"));
        String role = normalizeRole(user.getRoleName());
        if (!user.isActive() || user.isLocked() || !"APPROVED".equals(user.getApprovalStatus())
                || role.isBlank() || !roleMaster.isActive(role) || !requiresMfa(user, role))
            throw new IllegalArgumentException("This account is not available for authenticator recovery");
        if (enrollmentPending(userId))
            throw new IllegalStateException("Authenticator enrollment is already required. Reopen the QR setup instead of resetting again.");

        var verified = otp.verify(AuthOtpService.Purpose.MFA_RECOVERY, request.recoveryChallengeId(),
                request.otp(), mfaRecoveryBinding(user, request.loginChallengeId()));
        if (verified.userId() == null || !verified.userId().equals(userId))
            throw new IllegalArgumentException("The verification code is invalid or expired");

        TotpService.Setup setup = totp.createSetup(user.getUsername(), user.getEmail());
        // Only after the registered-email factor succeeds do we invalidate the lost phone.
        totp.invalidateUser(userId);
        tokens.revokeUser(userId);
        user.setTotpSecretEnc(setup.encryptedSecret());
        user.setMfaEnabled(true);
        user.resetMfaFailures();
        markEnrollmentPending(userId);
        String newLoginChallenge = totp.issueLogin(userId);
        audit(userId, "MFA_SELF_SERVICE_RESET_COMPLETED",
                "Old authenticator invalidated after password + registered-email verification; new enrollment required",
                user.getUsername());
        return new AuthDtos.LoginMfaEnrollmentResponse(true, newLoginChallenge, setup.manualSecret(),
                setup.provisioningUri(),
                "Registered email verified. The old authenticator is no longer valid. Scan this new QR code and verify the current 6-digit code.");
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
        String passwordError = passwordError(request.password());
        if (passwordError != null) return new AuthDtos.OperationResponse(false, passwordError);
        String username = request.username().trim();
        String email = request.email() == null ? "" : request.email().trim();
        if (users.existsByUsernameIgnoreCase(username))
            return new AuthDtos.OperationResponse(false, "Username is already registered");
        if (!email.isBlank() && users.existsByEmailIgnoreCase(email))
            return new AuthDtos.OperationResponse(false, "Email is already registered");

        String requestedRole = normalizeRole(request.role());
        RoleMasterService.RoleDefinition role;
        try { role = roleMaster.requireActive(requestedRole); }
        catch (IllegalArgumentException ignored) { return new AuthDtos.OperationResponse(false, "Selected role is unavailable"); }

        boolean mfa = mfaForRequestedUser(role.code(), request.mfaEnabled());

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwords.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(email);
        user.setRole(role.code());
        user.setActive(true);
        user.setApprovalStatus("APPROVED");
        user.setLocked(false);
        user.setMfaEnabled(mfa);
        user.setAccessLevel("STANDARD");
        users.saveAndFlush(user);

        if (mfa) audit(user.getId(), "MFA_ENROLLMENT_REQUIRED",
                "Authenticator enrollment required at the user's next sign-in", "SYSTEM");
        return new AuthDtos.OperationResponse(true,
                mfa ? "User registered. Authenticator enrollment is required at the next sign-in." : "User registered");
    }

    @Transactional(readOnly = true)
    public AuthDtos.CaptchaResponse registrationCaptcha(){ return captcha.issue(); }

    @Transactional(readOnly = true)
    public AuthDtos.ChallengeResponse requestRegistrationOtp(AuthDtos.RegistrationOtpRequest request) {
        cleanupExpiredRegistrations();
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
        cleanupExpiredRegistrations();
        String validation=publicRegistrationError(request==null?null:request.username(),request==null?null:request.fullName(),request==null?null:request.email(),request==null?null:request.role());
        if(validation!=null)throw new IllegalArgumentException(validation);
        String passwordError=passwordError(request.password());if(passwordError!=null)throw new IllegalArgumentException(passwordError);
        String username=request.username().trim(),email=request.email().trim(),role=normalizeRole(request.role());
        if("ADMIN".equals(role))throw new SecurityException("Administrator accounts cannot be created through self-registration.");
        otp.verify(AuthOtpService.Purpose.REGISTRATION,request.challengeId(),request.otp(),registrationBinding(username,email,role,true));
        if(users.existsByUsernameIgnoreCase(username)||openRegistrationExists("username",username))throw new IllegalArgumentException("Username is already registered or awaiting approval");
        if(users.existsByEmailIgnoreCase(email)||openRegistrationExists("email",email))throw new IllegalArgumentException("Email is already registered or awaiting approval");
        var setup=totp.createSetup(username,email);
        Long id=db.queryForObject("INSERT INTO registration_request(username,password_hash,full_name,email,requested_role,totp_secret_enc,email_verified,mfa_verified,status,requested_at,expires_at,mfa_attempts,row_version) VALUES(?,?,?,?,?,?,1,0,'MFA_ENROLLMENT_PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 minutes',0,0) RETURNING id",Long.class,username,passwords.encode(request.password()),request.fullName().trim(),email,role,setup.encryptedSecret());
        return new AuthDtos.RegistrationMfaSetupResponse(true,id,setup.manualSecret(),setup.provisioningUri(),"Email verified. Add " + mail.companyName() + " to Google Authenticator or Microsoft Authenticator, then enter the current 6-digit code.");
    }

    @Transactional
    public AuthDtos.OperationResponse completeRegistrationMfa(AuthDtos.RegistrationMfaCompleteRequest request){
        if(request==null||request.registrationId()<=0)
            return new AuthDtos.OperationResponse(false,"Registration request is invalid");
        cleanupExpiredRegistrations();
        var row=db.queryForMap("""
                SELECT id,totp_secret_enc,status,username,email,COALESCE(mfa_attempts,0) mfa_attempts,expires_at
                FROM registration_request WHERE id=? FOR UPDATE
                """,request.registrationId());
        String status=String.valueOf(row.get("status"));
        if(!"MFA_ENROLLMENT_PENDING".equals(status))
            return new AuthDtos.OperationResponse(false,"This registration request has already been submitted, expired, or processed");
        Boolean active=db.queryForObject("SELECT expires_at>CURRENT_TIMESTAMP FROM registration_request WHERE id=?",Boolean.class,request.registrationId());
        if(!Boolean.TRUE.equals(active)){
            db.update("UPDATE registration_request SET status='EXPIRED',row_version=row_version+1 WHERE id=? AND status='MFA_ENROLLMENT_PENDING'",request.registrationId());
            return new AuthDtos.OperationResponse(false,"Authenticator enrollment expired. Start registration again.");
        }
        int attempts=row.get("mfa_attempts") instanceof Number n?n.intValue():0;
        if(!totp.verifyEncrypted(String.valueOf(row.get("totp_secret_enc")),request.otp())){
            int next=attempts+1;
            if(next>=MAX_MFA_ATTEMPTS){
                db.update("UPDATE registration_request SET mfa_attempts=?,mfa_last_attempt_at=CURRENT_TIMESTAMP,status='EXPIRED',row_version=row_version+1 WHERE id=?",
                        next,request.registrationId());
                return new AuthDtos.OperationResponse(false,"Too many incorrect authenticator codes. Start registration again.");
            }
            db.update("UPDATE registration_request SET mfa_attempts=?,mfa_last_attempt_at=CURRENT_TIMESTAMP,row_version=row_version+1 WHERE id=?",
                    next,request.registrationId());
            return new AuthDtos.OperationResponse(false,"Authenticator code is incorrect or expired.");
        }
        db.update("UPDATE registration_request SET mfa_verified=1,status='PENDING_ADMIN_APPROVAL',mfa_attempts=0,mfa_last_attempt_at=CURRENT_TIMESTAMP,expires_at=NULL,row_version=row_version+1 WHERE id=?",
                request.registrationId());
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
        if (request == null) return new AuthDtos.OperationResponse(false, "Invalid password reset request");
        String passwordError=passwordError(request.password()); if(passwordError!=null)return new AuthDtos.OperationResponse(false,passwordError);
        var verified=otp.verifyWithoutConsume(AuthOtpService.Purpose.PASSWORD_RESET,request.challengeId(),request.otp(),"");
        if(verified.userId()==null)throw new IllegalArgumentException("The verification code is invalid or expired");
        UserEntity user=users.findById(verified.userId()).orElseThrow(()->new IllegalArgumentException("The verification code is invalid or expired"));
        String role=normalizeRole(user.getRoleName());
        boolean mfaRequired=requiresMfa(user,role);
        boolean pendingEnrollment=mfaRequired && enrollmentPending(user.getId());
        if(mfaRequired && !pendingEnrollment && user.getTotpSecretEnc()!=null && !user.getTotpSecretEnc().isBlank()){
            if(!totp.verifyEncrypted(user.getTotpSecretEnc(),request.totp())){
                int attempt = user.recordFailedMfaAttempt();
                audit(user.getId(), "PASSWORD_RESET_MFA_FAILED", "Incorrect authenticator code during reset (attempt " + attempt + " of " + MAX_MFA_ATTEMPTS + ")", user.getUsername());
                if (attempt >= MAX_MFA_ATTEMPTS) {
                    autoLock(user, LOCK_FAILED_MFA, "Five incorrect MFA verification-code attempts during password reset");
                    otp.invalidate(AuthOtpService.Purpose.PASSWORD_RESET, "user:" + user.getId());
                    return new AuthDtos.OperationResponse(false, "Incorrect authenticator code. Failed attempt 5 of 5. Your account has been locked. Contact an administrator.");
                }
                String suffix = attempt == MAX_MFA_ATTEMPTS - 1
                        ? " One attempt remains before this account is locked." : "";
                return new AuthDtos.OperationResponse(false, "Authenticator code is incorrect. Failed attempt " + attempt + " of " + MAX_MFA_ATTEMPTS + "." + suffix);
            }
        }
        otp.consume(AuthOtpService.Purpose.PASSWORD_RESET,request.challengeId());
        user.setPassword(passwords.encode(request.password()));
        user.resetMfaFailures();
        String prior=user.getLockReason();
        user.clearAutomaticLock();
        tokens.revokeUser(user.getId());
        String resetDetail = pendingEnrollment
                ? "Password reset completed through email OTP; authenticator re-enrollment remains required"
                : mfaRequired ? "Password reset completed through email OTP + authenticator"
                : "Password reset completed through email OTP under current MFA policy";
        audit(user.getId(),"PASSWORD_RESET_COMPLETED",resetDetail,user.getUsername());
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
        audit(user.getId(), "LOGIN_FAILED", "Incorrect password attempt " + attempt, user.getUsername());
        // Password failures are throttled by source+identity. They do not permanently lock
        // the account because a public caller must not be able to lock another user's account.
        return failedLogin("Invalid email/username or password.");
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

    private String mfaRecoveryBinding(UserEntity user, String loginChallengeId) {
        return "MFA_RECOVERY\u0000" + user.getId() + "\u0000"
                + (user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase(Locale.ROOT))
                + "\u0000" + (loginChallengeId == null ? "" : loginChallengeId.trim());
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
        cleanupExpiredRegistrations();
        var rows=db.queryForList("""
                SELECT id,username,email,password_hash,status
                FROM registration_request
                WHERE (LOWER(username)=LOWER(?) OR LOWER(email)=LOWER(?))
                  AND (status='PENDING_ADMIN_APPROVAL'
                       OR (status='MFA_ENROLLMENT_PENDING' AND expires_at>CURRENT_TIMESTAMP))
                ORDER BY id DESC LIMIT 1
                """,identity,identity);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private boolean openRegistrationExists(String field,String value){
        if(!"username".equals(field)&&!"email".equals(field))throw new IllegalArgumentException("Invalid registration field");
        cleanupExpiredRegistrations();
        Long count=db.queryForObject("SELECT COUNT(*) FROM registration_request WHERE LOWER("+field+")=LOWER(?) AND (status='PENDING_ADMIN_APPROVAL' OR (status='MFA_ENROLLMENT_PENDING' AND expires_at>CURRENT_TIMESTAMP))",Long.class,value);
        return count!=null&&count>0;
    }
    private void auditRegistration(Long id,String action,String detail,String actor){
        try{db.update("INSERT INTO activity_log(entity_type,entity_id,action,detail,created_by,created_at) VALUES('REGISTRATION',?,?,?,?,?)",id,action,detail,actor==null||actor.isBlank()?"SYSTEM":actor,BusinessClock.nowUtcText());}catch(RuntimeException ignored){}
    }

    private void cleanupExpiredRegistrations() {
        db.update("""
                UPDATE registration_request
                SET status='EXPIRED',row_version=row_version+1
                WHERE status='MFA_ENROLLMENT_PENDING'
                  AND expires_at IS NOT NULL AND expires_at<=CURRENT_TIMESTAMP
                """);
    }

    private void sendAuthenticatorEnrollment(String email, String username, TotpService.Setup setup) {
        String body = "Authenticator enrollment for " + mail.companyName() + "\n"
                + "Username: " + username + "\n"
                + "Manual setup key: " + setup.manualSecret() + "\n"
                + "Provisioning URI: " + setup.provisioningUri() + "\n\n"
                + "Add this account to Google Authenticator, Microsoft Authenticator, or another RFC-6238 compatible app before signing in.";
        mail.sendBusiness(email, mail.companyName() + " authenticator enrollment", body, java.util.List.of());
    }

    private boolean loginBlocked(String source, String identity) {
        cleanupLoginThrottle();
        return throttleBlocked("IP|" + source) || throttleBlocked("PAIR|" + source + "|" + normalizeThrottleIdentity(identity));
    }

    private boolean throttleBlocked(String key) {
        Long count = db.queryForObject(
                "SELECT COUNT(*) FROM login_throttle WHERE throttle_key=? AND blocked_until>CURRENT_TIMESTAMP",
                Long.class, key);
        return count != null && count > 0;
    }

    private void recordLoginFailure(String source, String identity) {
        recordThrottle("IP|" + source, 20);
        if (identity != null && !identity.isBlank())
            recordThrottle("PAIR|" + source + "|" + normalizeThrottleIdentity(identity), 5);
    }

    private void recordThrottle(String key, int threshold) {
        db.update("""
                INSERT INTO login_throttle(throttle_key,window_started,attempts,blocked_until,updated_at)
                VALUES(?,CURRENT_TIMESTAMP,1,NULL,CURRENT_TIMESTAMP)
                ON CONFLICT(throttle_key) DO UPDATE SET
                    attempts=CASE
                        WHEN login_throttle.window_started < CURRENT_TIMESTAMP - INTERVAL '10 minutes' THEN 1
                        ELSE login_throttle.attempts+1 END,
                    window_started=CASE
                        WHEN login_throttle.window_started < CURRENT_TIMESTAMP - INTERVAL '10 minutes' THEN CURRENT_TIMESTAMP
                        ELSE login_throttle.window_started END,
                    blocked_until=CASE
                        WHEN (CASE WHEN login_throttle.window_started < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                                   THEN 1 ELSE login_throttle.attempts+1 END) >= ?
                            THEN CURRENT_TIMESTAMP + INTERVAL '15 minutes'
                        WHEN login_throttle.blocked_until>CURRENT_TIMESTAMP THEN login_throttle.blocked_until
                        ELSE NULL END,
                    updated_at=CURRENT_TIMESTAMP
                """, key, threshold);
    }

    private void clearLoginThrottle(String source, String identity) {
        if (identity != null && !identity.isBlank())
            db.update("DELETE FROM login_throttle WHERE throttle_key=?", "PAIR|" + source + "|" + normalizeThrottleIdentity(identity));
    }

    private void cleanupLoginThrottle() {
        db.update("DELETE FROM login_throttle WHERE updated_at<CURRENT_TIMESTAMP - INTERVAL '2 days'");
    }

    private static String normalizeLoginSource(String source) {
        String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) value = "unknown";
        value = value.replaceAll("[^a-z0-9:._-]", "_");
        return value.length() <= 120 ? value : value.substring(0,120);
    }

    private static String normalizeThrottleIdentity(String identity) {
        String value = identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT);
        return value.length() <= 240 ? value : value.substring(0,240);
    }

    private boolean enrollmentPending(int userId) {
        Long count=db.queryForObject("SELECT COUNT(*) FROM auth_totp_enrollment_pending WHERE user_id=?",Long.class,userId);
        return count!=null&&count>0;
    }

    private void markEnrollmentPending(int userId) {
        db.update("INSERT INTO auth_totp_enrollment_pending(user_id,created_at) VALUES(?,CURRENT_TIMESTAMP) " +
                "ON CONFLICT(user_id) DO UPDATE SET created_at=CURRENT_TIMESTAMP",userId);
    }

    private void clearEnrollmentPending(int userId) {
        db.update("DELETE FROM auth_totp_enrollment_pending WHERE user_id=?",userId);
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
        String value = db.queryForObject("SELECT setting_value FROM application_setting WHERE setting_key='security.auth.mfa.policy'", String.class);
        String normalized = value == null ? "REQUIRED" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) { case "ADMIN_CONTROLLED", "DISABLED" -> normalized; default -> "REQUIRED"; };
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
