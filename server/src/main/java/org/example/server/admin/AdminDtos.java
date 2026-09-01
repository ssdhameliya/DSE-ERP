package org.example.server.admin;
import java.util.List;
public final class AdminDtos { private AdminDtos(){}
 public record UserDto(int id,String username,String fullName,String email,String role,String department,String accessLevel,String branch,boolean active,boolean locked,boolean mfaEnabled,String lastLogin,long rowVersion){}
 public record UserSaveRequest(Integer id,String username,String password,String fullName,String email,String role,String department,String accessLevel,String branch,boolean active,boolean locked,boolean mfaEnabled,long rowVersion){}
 public record RoleDto(int id,String code,String displayName,String description,boolean active,long userCount){}
 public record RoleSaveRequest(Integer id,String name,String description,boolean active){}
 public record RegistrationRoleDto(String code,String displayName){}
 public record RegistrationRoleSaveRequest(String role){}
 public record PermissionDto(long id,String module,String action,String description,boolean allowed){}
 public record PermissionSave(long id,boolean allowed){}
 public record PermissionSetDto(long rowVersion,List<PermissionDto> permissions){}
 public record PermissionSaveRequest(String role,List<PermissionSave> permissions,long rowVersion){}
 public record PasswordRequest(String password){}
 public record LockRequest(boolean locked){}
 public record AuditRequest(int userId,String action,String detail){}
 public record Ok(boolean success,String message){}
}
