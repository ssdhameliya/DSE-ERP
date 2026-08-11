package org.example.server.auth;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(String identity, String password) {}
    public record LookupRequest(String identity) {}
    public record UserIdRequest(int userId) {}
    public record ChangePasswordRequest(int userId, String password) {}
    public record RegisterRequest(String username, String password, String fullName, String email, String role) {}
    public record LoginResponse(boolean success, UserPayload user, String message) {}
    public record LookupResponse(boolean found, UserPayload user) {}
    public record OperationResponse(boolean success, String message) {}
    public record RoleOption(String code, String displayName) {}
    public record UserPayload(int id, String username, String fullName, String role, Integer roleId,
                              String email, boolean active, String department, String branch,
                              String accessLevel, boolean locked, boolean mfaEnabled) {}
}
