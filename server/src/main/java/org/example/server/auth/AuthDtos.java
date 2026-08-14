package org.example.server.auth;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(String identity, String password) {}
    public record UserIdRequest(int userId) {}
    public record ChangePasswordRequest(int userId, String currentPassword, String password) {}
    public record RegisterRequest(String username, String password, String fullName, String email, String role) {}
    public record RegistrationOtpRequest(String username, String fullName, String email, String role) {}
    public record RegistrationCompleteRequest(String challengeId, String otp, String username, String password,
                                               String fullName, String email, String role) {}
    public record PasswordResetOtpRequest(String identity) {}
    public record PasswordResetCompleteRequest(String challengeId, String otp, String password) {}
    public record ChallengeResponse(boolean success, String challengeId, String message) {}
    public record LoginResponse(boolean success, UserPayload user, String message,
                                String accessToken, String expiresAt) {}
    public record OperationResponse(boolean success, String message) {}
    public record RoleOption(String code, String displayName) {}
    public record UserPayload(int id, String username, String fullName, String role, Integer roleId,
                              String email, boolean active, String department, String branch,
                              String accessLevel, boolean locked, boolean mfaEnabled) {}
}
