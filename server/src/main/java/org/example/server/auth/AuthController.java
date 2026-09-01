package org.example.server.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final org.example.server.security.PermissionAuthorityService permissions;
    public AuthController(AuthService auth, org.example.server.security.PermissionAuthorityService permissions) {
        this.auth = auth; this.permissions = permissions;
    }

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("status", "UP"); }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.LoginRequest request) {
        AuthDtos.LoginResponse result = auth.login(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.status(401).body(result);
    }

    @PostMapping("/login/mfa/complete")
    public ResponseEntity<AuthDtos.LoginResponse> completeLoginMfa(
            @RequestBody AuthDtos.LoginMfaCompleteRequest request) {
        AuthDtos.LoginResponse result = auth.completeLoginMfa(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.status(401).body(result);
    }

    @PostMapping("/login/mfa/resend")
    public ResponseEntity<AuthDtos.LoginMfaChallengeResponse> resendLoginMfa(
            @RequestBody AuthDtos.LoginMfaResendRequest request) {
        return ResponseEntity.ok(auth.resendLoginMfa(request));
    }

    @PostMapping("/login-complete")
    public AuthDtos.OperationResponse loginComplete(@RequestBody AuthDtos.UserIdRequest request,
                                                     @AuthenticationPrincipal org.example.server.security.AuthenticatedUser current) {
        return auth.completeLogin(request, current);
    }

    @PostMapping("/password")
    public ResponseEntity<AuthDtos.OperationResponse> password(@RequestBody AuthDtos.ChangePasswordRequest request,
                                                                @AuthenticationPrincipal org.example.server.security.AuthenticatedUser current) {
        AuthDtos.OperationResponse result = auth.changePassword(request, current);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/login-roles")
    public List<AuthDtos.RoleOption> loginRoles() { return auth.loginRoles(); }

    @GetMapping("/registration-roles")
    public List<AuthDtos.RoleOption> registrationRoles() { return auth.registrationRoles(); }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.OperationResponse> register(@RequestBody AuthDtos.RegisterRequest request) {
        AuthDtos.OperationResponse result = auth.register(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/registration/captcha")
    public AuthDtos.CaptchaResponse registrationCaptcha() { return auth.registrationCaptcha(); }

    @PostMapping("/registration/request")
    public ResponseEntity<AuthDtos.ChallengeResponse> requestRegistrationOtp(@RequestBody AuthDtos.RegistrationOtpRequest request) {
        AuthDtos.ChallengeResponse result=auth.requestRegistrationOtp(request);
        return result.success()?ResponseEntity.ok(result):ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/registration/email/verify")
    public ResponseEntity<AuthDtos.RegistrationMfaSetupResponse> verifyRegistrationEmail(@RequestBody AuthDtos.RegistrationEmailVerifyRequest request) {
        return ResponseEntity.ok(auth.verifyRegistrationEmail(request));
    }

    @PostMapping("/registration/mfa/complete")
    public ResponseEntity<AuthDtos.OperationResponse> completeRegistrationMfa(@RequestBody AuthDtos.RegistrationMfaCompleteRequest request) {
        AuthDtos.OperationResponse result=auth.completeRegistrationMfa(request);
        return result.success()?ResponseEntity.ok(result):ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<AuthDtos.ChallengeResponse> requestPasswordReset(
            @RequestBody AuthDtos.PasswordResetOtpRequest request) {
        AuthDtos.ChallengeResponse result = auth.requestPasswordReset(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<AuthDtos.OperationResponse> completePasswordReset(
            @RequestBody AuthDtos.PasswordResetCompleteRequest request) {
        AuthDtos.OperationResponse result = auth.completePasswordReset(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/logout")
    public AuthDtos.OperationResponse logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestHeader(value = "X-DSE-Logout-Reason", required = false) String reason,
                                             @AuthenticationPrincipal org.example.server.security.AuthenticatedUser current) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : null;
        auth.logout(token, current, reason);
        return new AuthDtos.OperationResponse(true, "Signed out");
    }
    @PostMapping("/session/extend")
    public AuthDtos.SessionExtendResponse extendSession(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @AuthenticationPrincipal org.example.server.security.AuthenticatedUser current) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : null;
        if (token == null || token.isBlank()) throw new SecurityException("Authentication required");
        return auth.extendSession(token, current);
    }

    @GetMapping("/session")
    public AuthDtos.OperationResponse session(
            @AuthenticationPrincipal org.example.server.security.AuthenticatedUser current) {
        if (current == null) throw new SecurityException("Authentication required");
        return new AuthDtos.OperationResponse(true, "Authenticated as " + current.username());
    }

    @GetMapping("/effective-permissions")
    public List<AuthDtos.EffectivePermission> effectivePermissions(
            @AuthenticationPrincipal org.example.server.security.AuthenticatedUser current) {
        if (current == null) throw new SecurityException("Authentication required");
        return permissions.effectivePermissions(current.role()).stream()
                .map(p -> new AuthDtos.EffectivePermission(p.module(), p.action(), p.description()))
                .toList();
    }

}
