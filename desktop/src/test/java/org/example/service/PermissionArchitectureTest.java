package org.example.service;

import org.example.model.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PermissionArchitectureTest {
    @AfterEach
    void cleanup() {
        SessionService.clear();
    }

    @Test
    void administratorAliasesResolveToOneCanonicalAdminRole() {
        assertEquals("ADMIN", SessionService.canonicalRole("admin"));
        assertEquals("ADMIN", SessionService.canonicalRole(" Administrator "));
        assertTrue(SessionService.isAdminRole("Admin"));
        assertTrue(SessionService.isAdminRole("ADMINISTRATOR"));
        assertFalse(SessionService.isAdminRole("MANAGER"));
    }

    @Test
    void adminPermissionBypassDoesNotDependOnPermissionMatrixApi() {
        AppUser admin = new AppUser();
        admin.setRole("Administrator");
        SessionService.signIn(admin);

        assertTrue(PermissionService.allowed("USERS.VIEW"));
        assertTrue(PermissionService.allowed("BACKUP.VIEW"));
        assertTrue(PermissionService.allowed("SAFE_ROLLBACK.VIEW"));
        assertTrue(PermissionService.allowed("SAFE_ROLLBACK.EXECUTE"));
    }

    @Test
    void controllersDoNotInterpretCurrentSessionRoleDirectly() throws Exception {
        Path controllers = Path.of("src/main/java/org/example/controller");
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(controllers)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (source.contains("SessionService.current().getRole")) {
                    violations.add(path.toString());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Controllers must use SessionService.isAdmin()/canonicalRole() instead of decoding the live session role directly: "
                        + String.join(", ", violations));
    }

    @Test
    void permissionRefreshNotifiesLiveShellListenersForAdminWithoutApiCall() {
        AppUser admin = new AppUser();
        admin.setRole("ADMIN");
        SessionService.signIn(admin);
        AtomicInteger notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        PermissionService.addChangeListener(listener);
        try {
            PermissionService.refreshStrict();
            assertEquals(1, notifications.get());
        } finally {
            PermissionService.removeChangeListener(listener);
        }
    }
}
