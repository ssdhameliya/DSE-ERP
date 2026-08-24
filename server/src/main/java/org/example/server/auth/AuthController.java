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
    private final org.example.server.persistence.JpaNativeRepository jdbc;
    public AuthController(AuthService auth, org.example.server.security.PermissionAuthorityService permissions,
                          org.example.server.persistence.JpaNativeRepository jdbc) {
        this.auth = auth; this.permissions = permissions; this.jdbc = jdbc;
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

    @PostMapping("/registration/request")
    public ResponseEntity<AuthDtos.ChallengeResponse> requestRegistrationOtp(
            @RequestBody AuthDtos.RegistrationOtpRequest request) {
        AuthDtos.ChallengeResponse result = auth.requestRegistrationOtp(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/registration/complete")
    public ResponseEntity<AuthDtos.OperationResponse> completeRegistration(
            @RequestBody AuthDtos.RegistrationCompleteRequest request) {
        AuthDtos.OperationResponse result = auth.completeRegistration(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
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
    public AuthDtos.OperationResponse logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : null;
        auth.logout(token);
        return new AuthDtos.OperationResponse(true, "Signed out");
    }
    @GetMapping("/effective-permissions")
    public List<AuthDtos.EffectivePermission> effectivePermissions(
            @AuthenticationPrincipal org.example.server.security.AuthenticatedUser current) {
        if (current == null) throw new SecurityException("Authentication required");
        String role = current.role() == null ? "" : current.role().trim().toUpperCase(java.util.Locale.ROOT);
        if ("ADMIN".equals(role)) {
            return jdbc.query("SELECT module_name,action_name,COALESCE(description,'') FROM permissions WHERE active=1 ORDER BY module_name,action_name",
                    (row,index) -> new AuthDtos.EffectivePermission(row.getString(1), row.getString(2), row.getString(3)));
        }
        return jdbc.query("SELECT p.module_name,p.action_name,COALESCE(p.description,'') FROM role_permission rp JOIN permissions p ON p.id=rp.permission_id " +
                        "WHERE UPPER(TRIM(COALESCE(rp.role_code,'')))=? AND p.active=1 AND COALESCE(rp.allowed,0)=1 ORDER BY p.module_name,p.action_name",
                (row,index) -> new AuthDtos.EffectivePermission(row.getString(1), row.getString(2), row.getString(3)), role);
    }

}
