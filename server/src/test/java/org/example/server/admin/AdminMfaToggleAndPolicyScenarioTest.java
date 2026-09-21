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

import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminMfaToggleAndPolicyScenarioTest {
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void currentAdminTurningMfaOnRevokesSessionClearsOldStateAndForcesEnrollment() {
        actor(7, "admin");
        ToggleDb db = new ToggleDb(false, "ADMIN_CONTROLLED");
        TokenService tokens = mock(TokenService.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        when(roles.requireActive("ADMIN")).thenReturn(new RoleMasterService.RoleDefinition(1,"ADMIN","Admin","",true,0,1));
        AdminService service = service(db,tokens,roles,true);

        AdminDtos.UserDto saved = service.saveUser(request(true));

        assertTrue(saved.mfaEnabled());
        verify(tokens).revokeUser(7);
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("totp_secret_enc=NULL")), "Changing MFA state must invalidate any old authenticator secret");
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("DELETE FROM auth_totp_enrollment_pending")));
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("DELETE FROM auth_totp_login_challenge")));
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("MFA_ENROLLMENT_REQUIRED")));

        AdminDtos.MfaState state = new AdminService(new PolicyDb("ADMIN",true,"ADMIN_CONTROLLED"), mock(PasswordEncoder.class), tokens, roles).mfaState(7);
        assertTrue(state.required());
        assertEquals("ENROLLMENT_REQUIRED", state.status());
    }

    @Test
    void currentAdminTurningMfaOffRevokesSessionAndInvalidatesAuthenticator() {
        actor(7, "admin");
        ToggleDb db = new ToggleDb(true, "ADMIN_CONTROLLED");
        TokenService tokens = mock(TokenService.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        when(roles.requireActive("ADMIN")).thenReturn(new RoleMasterService.RoleDefinition(1,"ADMIN","Admin","",true,0,1));
        AdminService service = service(db,tokens,roles,false);

        AdminDtos.UserDto saved = service.saveUser(request(false));

        assertFalse(saved.mfaEnabled());
        verify(tokens).revokeUser(7);
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("totp_secret_enc=NULL")));
        assertTrue(db.sql.stream().anyMatch(s -> s.contains("MFA_DISABLED")));
    }

    @Test
    void mfaPolicyMatrixMatchesRequiredAdminControlledAndDisabledRules() {
        TokenService tokens = mock(TokenService.class);
        RoleMasterService roles = mock(RoleMasterService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);

        assertState(new AdminService(new PolicyDb("ADMIN",false,"REQUIRED"),passwords,tokens,roles), false, "DISABLED");
        assertState(new AdminService(new PolicyDb("SALES",false,"REQUIRED"),passwords,tokens,roles), true, "ENROLLMENT_REQUIRED");
        assertState(new AdminService(new PolicyDb("ADMIN",false,"ADMIN_CONTROLLED"),passwords,tokens,roles), false, "DISABLED");
        assertState(new AdminService(new PolicyDb("ADMIN",true,"ADMIN_CONTROLLED"),passwords,tokens,roles), true, "ENROLLMENT_REQUIRED");
        assertState(new AdminService(new PolicyDb("SALES",true,"DISABLED"),passwords,tokens,roles), false, "DISABLED");
    }

    private static void assertState(AdminService service, boolean required, String status) {
        AdminDtos.MfaState state = service.mfaState(7);
        assertEquals(required,state.required());
        assertEquals(status,state.status());
    }

    private static AdminService service(ToggleDb db, TokenService tokens, RoleMasterService roles, boolean resultingMfa) {
        return new AdminService(db,mock(PasswordEncoder.class),tokens,roles) {
            @Override public AdminDtos.UserDto user(int id) {
                return new AdminDtos.UserDto(id,"admin","System Administrator","admin@company.com","ADMIN","Administration","FULL ACCESS","Head Office",true,false,resultingMfa,"",5);
            }
        };
    }

    private static AdminDtos.UserSaveRequest request(boolean mfa) {
        return new AdminDtos.UserSaveRequest(7,"admin","","System Administrator","admin@company.com","ADMIN","Administration","FULL ACCESS","Head Office",true,false,mfa,4);
    }

    private static void actor(int id, String username) {
        var principal = new AuthenticatedUser(id,username,"ADMIN");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",List.of()));
    }

    private static final class ToggleDb extends JpaNativeRepository {
        final boolean previousMfa; final String policy; final List<String> sql = new ArrayList<>();
        ToggleDb(boolean previousMfa,String policy){this.previousMfa=previousMfa;this.policy=policy;}
        @Override public int update(String query,Object...args){sql.add(query.replaceAll("\\s+"," ") + " ARGS=" + java.util.Arrays.toString(args));return 1;}
        @Override public Map<String,Object> queryForMap(String query,Object...args){
            if(query.contains("row_version") && query.contains("FOR UPDATE")) return Map.of("row_version",4L);
            if(query.contains("COALESCE(role,'') role") && query.contains("active")) return Map.of("role","ADMIN","active",1,"locked",0);
            throw new AssertionError("Unexpected map query: "+query);
        }
        @Override public <T> T queryForObject(String query,Class<T> type,Object...args){
            if(query.contains("security.auth.mfa.policy")) return type.cast(policy);
            if(query.contains("COUNT(*) FROM users") && query.contains("username")) return type.cast(Long.valueOf(0));
            if(query.contains("COUNT(*) FROM users") && query.contains("email")) return type.cast(Long.valueOf(0));
            if(query.contains("SELECT mfa_enabled FROM users")) return type.cast(Boolean.valueOf(previousMfa));
            if(query.contains("SELECT locked FROM users")) return type.cast(Boolean.FALSE);
            if(query.contains("SELECT COALESCE(role,'') FROM users")) return type.cast("ADMIN");
            throw new AssertionError("Unexpected object query: "+query);
        }
        @Override public <T> List<T> query(String query, BiFunction<NativeRow,Integer,T> mapper,Object...args){
            if(query.contains("pg_advisory_xact_lock")) return List.of();
            throw new AssertionError("Unexpected row query: "+query);
        }
    }

    private static final class PolicyDb extends JpaNativeRepository {
        final String role; final boolean mfa; final String policy;
        PolicyDb(String role,boolean mfa,String policy){this.role=role;this.mfa=mfa;this.policy=policy;}
        @Override public Map<String,Object> queryForMap(String q,Object...args){return Map.of("role",role,"mfa_enabled",mfa,"totp_secret_enc","");}
        @Override public <T> T queryForObject(String q,Class<T> type,Object...args){
            if(q.contains("security.auth.mfa.policy")) return type.cast(policy);
            if(q.contains("auth_totp_enrollment_pending")) return type.cast(Long.valueOf(0));
            throw new AssertionError("Unexpected query: "+q);
        }
    }
}
