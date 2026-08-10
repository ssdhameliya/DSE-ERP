package org.example.server.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public AuthDtos.OperationResponse loginComplete(@RequestBody AuthDtos.UserIdRequest request) {
        return auth.completeLogin(request);
    }

    @PostMapping("/password")
    public ResponseEntity<AuthDtos.OperationResponse> password(@RequestBody AuthDtos.ChangePasswordRequest request) {
        AuthDtos.OperationResponse result = auth.changePassword(request);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
