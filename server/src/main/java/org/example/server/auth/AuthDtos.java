package org.example.server.auth;
public final class AuthDtos { private AuthDtos() {}
 public record LoginRequest(String identity,String password){}
 public record LoginMfaCompleteRequest(String challengeId,String otp){}
 public record LoginMfaResendRequest(String challengeId){}
 public record UserIdRequest(int userId){}
 public record ChangePasswordRequest(int userId,String currentPassword,String password){}
 public record RegisterRequest(String username,String password,String fullName,String email,String role,boolean mfaEnabled){}
 public record CaptchaResponse(String challengeId,String question,String expiresIn){}
 public record RegistrationOtpRequest(String username,String fullName,String email,String role,boolean mfaEnabled,String captchaChallengeId,String captchaAnswer){}
 public record RegistrationEmailVerifyRequest(String challengeId,String otp,String username,String password,String fullName,String email,String role,boolean mfaEnabled){}
 public record RegistrationMfaSetupResponse(boolean success,Long registrationId,String manualSecret,String provisioningUri,String message){}
 public record RegistrationMfaCompleteRequest(long registrationId,String otp){}
 public record PasswordResetOtpRequest(String identity){}
 public record PasswordResetCompleteRequest(String challengeId,String otp,String totp,String password){}
 public record ChallengeResponse(boolean success,String challengeId,String message){}
 public record LoginMfaChallengeResponse(boolean success,String challengeId,String message,String maskedDestination){}
 public record LoginResponse(boolean success,UserPayload user,String message,String accessToken,String expiresAt,boolean mfaRequired,String challengeId,String maskedDestination){}
 public record OperationResponse(boolean success,String message){}
 public record RoleOption(String code,String displayName){}
 public record EffectivePermission(String module,String action,String description){}
 public record UserPayload(int id,String username,String fullName,String role,Integer roleId,String email,boolean active,String department,String branch,String accessLevel,boolean locked,boolean mfaEnabled){}
 public record SessionExtendResponse(boolean success,String message,String accessToken,String expiresAt){}
}
