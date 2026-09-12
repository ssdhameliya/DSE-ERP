package org.example.api.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AuthSharedClientCompatibilityContractTest {
    @Test void authLoginMfaAndSessionDelegateToCentralSharedClientCompatibilityPolicy() throws Exception {
        String auth = Files.readString(Path.of("src/main/java/org/example/api/auth/AuthApiClient.java"));
        assertTrue(auth.contains("DeploymentConnectionService.inspect(normalizeBaseUrl(serverBaseUrl))"));
        assertTrue(auth.contains("requireCompatibleRuntime(loginBase);"));
        assertTrue(auth.contains("requireCompatibleRuntime(issuingBaseUrl);"));
        assertFalse(auth.contains("BuildInfo.version().equals(status.version())"));
        assertFalse(auth.contains("BuildInfo.buildRevision().equals(status.buildRevision())"));
        assertFalse(auth.contains("org.example.update.BuildInfo.version().equals(status.version())"));
        assertFalse(auth.contains("org.example.update.BuildInfo.buildRevision().equals(status.buildRevision())"));
    }

    @Test void bootstrapCannotReintroduceExactSharedClientVersionEquality() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/org/example/api/runtime/RuntimeBootstrapper.java"));
        assertTrue(bootstrap.contains("if (ConfigManager.isSharedClient())"));
        assertTrue(bootstrap.contains("DeploymentConnectionService.validateCompatibility(status);"));
        assertTrue(bootstrap.contains("} else {"));
        assertTrue(bootstrap.contains("BuildInfo.version().equals(status.version())")
                || bootstrap.contains("org.example.update.BuildInfo.version().equals(status.version())"));
        assertTrue(bootstrap.contains("BuildInfo.buildRevision().equals(status.buildRevision())")
                || bootstrap.contains("org.example.update.BuildInfo.buildRevision().equals(status.buildRevision())"));
    }
}
