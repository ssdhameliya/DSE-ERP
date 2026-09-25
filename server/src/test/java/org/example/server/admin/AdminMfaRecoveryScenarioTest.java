package org.example.server.admin;

import org.example.server.master.RoleMasterService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.AuthenticatedUser;
import org.example.server.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminMfaRecoveryScenarioTest {
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void lostPhoneResetInvalidatesOldSecretRevokesSessionsAndKeepsMfaRequired() {
        actor(1, "owner");
        RecoveryDb db = new RecoveryDb("ADMIN", true, "ADMIN_CONTROLLED");
        TokenService tokens = mock(TokenService.class);
        AdminService service = new AdminService(db, mock(PasswordEncoder.class), tokens, mock(RoleMasterService.class));

        AdminDtos.MfaState state = service.resetAuthenticator(7);

        assertTrue(state.required());
        assertEquals("ENROLLMENT_REQUIRED", state.status());
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("totp_secret_enc=NULL")));
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("DELETE FROM auth_totp_enrollment_pending")));
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("INSERT INTO auth_totp_enrollment_pending")),
                "Reset must persist enrollment-required state so password recovery never depends on the lost authenticator");
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("DELETE FROM auth_totp_login_challenge")));
        assertTrue(db.sql.stream().noneMatch(s -> s.contains("mfa_enabled=0")), "Lost-phone recovery must not disable MFA");
        verify(tokens).revokeUser(7);
    }

    @Test
    void resetIsRejectedWhenMfaPolicyDisablesAuthenticator() {
        actor(1, "owner");
        RecoveryDb db = new RecoveryDb("ADMIN", true, "DISABLED");
        TokenService tokens = mock(TokenService.class);
        AdminService service = new AdminService(db, mock(PasswordEncoder.class), tokens, mock(RoleMasterService.class));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.resetAuthenticator(7));
        assertTrue(error.getMessage().contains("not required"));
        verifyNoInteractions(tokens);
    }

    private static void actor(int id, String username) {
        var principal = new AuthenticatedUser(id, username, "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of()));
    }

    private static final class RecoveryDb extends JpaNativeRepository {
        final String role; final boolean enabled; final String policy; final List<String> sql = new ArrayList<>();
        RecoveryDb(String role, boolean enabled, String policy) { this.role = role; this.enabled = enabled; this.policy = policy; }
        @Override public Map<String,Object> queryForMap(String query, Object... args) {
            Map<String,Object> row = new HashMap<>(); row.put("role", role); row.put("mfa_enabled", enabled); return row;
        }
        @Override public <T> T queryForObject(String query, Class<T> type, Object... args) {
            if (query.contains("security.auth.mfa.policy")) return type.cast(policy);
            if (query.contains("auth_totp_enrollment_pending")) return type.cast(Long.valueOf(0));
            throw new AssertionError("Unexpected query: " + query);
        }
        @Override public int update(String query, Object... args) { sql.add(query.replaceAll("\\s+", " ")); return 1; }
    }
}
