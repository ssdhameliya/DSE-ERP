package org.example.server.profile;
public final class ProfileDtos { private ProfileDtos() {}
 public record ProfileDto(int id,String username,String fullName,String email,String role,String department,String branch,String accessLevel,boolean active,boolean locked,boolean mfaEnabled,String lastLogin) {}
 public record ProfileUpdate(String fullName,String email,String department,String branch) {}
}
