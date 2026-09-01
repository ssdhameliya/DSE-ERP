package org.example.service;

import org.example.api.auth.AuthApiClient;
import org.example.model.AppUser;

import java.util.List;

/**
 * Authentication facade. Phase 2 is API-only: credentials and user account
 * persistence are owned by the Spring server, never by the JavaFX process.
 */
public class UserService {
    private final AuthApiClient authApi = new AuthApiClient();

    public AuthApiClient.LoginAttempt authenticate(String identity, String password) { return authApi.authenticate(identity, password); }
    public AppUser completeLoginMfa(String challengeId, String otp) { return authApi.completeLoginMfa(challengeId, otp); }
    public AuthApiClient.LoginMfaChallengeResponse resendLoginMfa(String challengeId) { return authApi.resendLoginMfa(challengeId); }
    public void recordSuccessfulLogin(int id) { authApi.recordSuccessfulLogin(id); }
    public void register(AppUser user) { authApi.register(user); }
    public AuthApiClient.CaptchaResponse registrationCaptcha() { return authApi.registrationCaptcha(); }
    public AuthApiClient.ChallengeResponse requestRegistrationOtp(AppUser user,String captchaId,String captchaAnswer) { return authApi.requestRegistrationOtp(user,captchaId,captchaAnswer); }
    public AuthApiClient.RegistrationMfaSetupResponse verifyRegistrationEmail(AppUser user,String challengeId,String otp) { return authApi.verifyRegistrationEmail(user,challengeId,otp); }
    public void completeRegistrationMfa(long registrationId,String otp) { authApi.completeRegistrationMfa(registrationId,otp); }
    public AuthApiClient.ChallengeResponse requestPasswordReset(String identity) { return authApi.requestPasswordReset(identity); }
    public void completePasswordReset(String challengeId,String otp,String totp,String password) { authApi.completePasswordReset(challengeId,otp,totp,password); }
    public List<AuthApiClient.RoleOption> loginRoles() { return authApi.loginRoles(); }
    public List<AuthApiClient.RoleOption> registrationRoles() { return authApi.registrationRoles(); }
    public void changePassword(int id, String currentPassword, String password) { authApi.changePassword(id, currentPassword, password); }
    public void extendSession() { authApi.extendSession(); }
    public void logout() { authApi.logout(); }
    public void logoutIdle() { authApi.logout("AUTO_LOGOUT_IDLE"); }
}
