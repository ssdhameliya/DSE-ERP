package org.example.server.admin;
import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/admin") public class AdminController {
 private final AdminService s; public AdminController(AdminService s){this.s=s;}
 @GetMapping("/users") public List<AdminDtos.UserDto> users(){return s.users();}
 @GetMapping("/users/{id}") public AdminDtos.UserDto user(@PathVariable int id){return s.user(id);}
 @PostMapping("/users") public AdminDtos.UserDto addUser(@RequestBody AdminDtos.UserSaveRequest d){return s.saveUser(d);}
 @PutMapping("/users/{id}") public AdminDtos.UserDto updateUser(@PathVariable int id,@RequestBody AdminDtos.UserSaveRequest d){return s.saveUser(new AdminDtos.UserSaveRequest(id,d.username(),d.password(),d.fullName(),d.email(),d.role(),d.department(),d.accessLevel(),d.branch(),d.active(),d.locked(),d.mfaEnabled()));}
 @DeleteMapping("/users/{id}") public AdminDtos.Ok deleteUser(@PathVariable int id){s.deleteUser(id);return ok("Deleted");}
 @PostMapping("/users/{id}/password") public AdminDtos.Ok password(@PathVariable int id,@RequestBody AdminDtos.PasswordRequest d){s.resetPassword(id,d.password());return ok("Updated");}
 @PostMapping("/users/{id}/lock") public AdminDtos.Ok lock(@PathVariable int id,@RequestBody AdminDtos.LockRequest d){s.setLocked(id,d.locked());return ok("Updated");}
 @GetMapping("/roles") public List<AdminDtos.RoleDto> roles(){return s.roles();}
 @PostMapping("/roles") public AdminDtos.RoleDto addRole(@RequestBody AdminDtos.RoleSaveRequest d){return s.saveRole(d);}
 @PutMapping("/roles/{id}") public AdminDtos.RoleDto updateRole(@PathVariable int id,@RequestBody AdminDtos.RoleSaveRequest d){return s.saveRole(new AdminDtos.RoleSaveRequest(id,d.name(),d.description(),d.active()));}
 @DeleteMapping("/roles/{id}") public AdminDtos.Ok deleteRole(@PathVariable int id){s.deleteRole(id);return ok("Deleted");}
 @GetMapping("/permissions") public List<AdminDtos.PermissionDto> permissions(@RequestParam String role){return s.permissions(role);}
 @PutMapping("/permissions") public AdminDtos.Ok permissions(@RequestBody AdminDtos.PermissionSaveRequest d){s.savePermissions(d);return ok("Saved");}
 @PostMapping("/audit") public AdminDtos.Ok audit(@RequestBody AdminDtos.AuditRequest d){s.audit(d);return ok("Saved");}
 private AdminDtos.Ok ok(String m){return new AdminDtos.Ok(true,m);}
}
