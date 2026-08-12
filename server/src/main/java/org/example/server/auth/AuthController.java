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
    public AuthController(AuthService auth) { this.auth = auth; }

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("status", "UP"); }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.LoginRequest request) {
        AuthDtos.LoginResponse result = auth.login(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.status(401).body(result);
    }

    @PostMapping("/lookup")
    public AuthDtos.LookupResponse lookup(@RequestBody AuthDtos.LookupRequest request) { return auth.lookup(request); }

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

    @GetMapping("/registration-roles")
    public List<AuthDtos.RoleOption> registrationRoles() { return auth.registrationRoles(); }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.OperationResponse> register(@RequestBody AuthDtos.RegisterRequest request) {
        AuthDtos.OperationResponse result = auth.register(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/logout")
    public AuthDtos.OperationResponse logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : null;
        auth.logout(token);
        return new AuthDtos.OperationResponse(true, "Signed out");
    }
}
