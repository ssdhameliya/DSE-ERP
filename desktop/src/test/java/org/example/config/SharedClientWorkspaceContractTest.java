package org.example.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SharedClientWorkspaceContractTest {
    @Test
    void managedSharedClientStorageLivesUnderApplicationData() {
        assertTrue(WorkspaceManager.getManagedSharedClientRoot().startsWith(WorkspaceManager.getPointerFolder()));
        assertNotEquals(WorkspaceManager.getSuggestedWorkspace(), WorkspaceManager.getManagedSharedClientRoot());
    }

    @Test
    void detachingSharedClientCopiesConnectionButNotLocalDatabaseSecrets() throws Exception {
        Path source = Files.createTempDirectory("dse-shared-source-");
        Path target = Files.createTempDirectory("dse-shared-target-");
        try {
            Files.createDirectories(source.resolve("Config"));
            Path sourceConfig = source.resolve("Config/config.properties");
            Files.writeString(sourceConfig, String.join("\n",
                    "deployment.mode=SHARED_CLIENT",
                    "deployment.environment=UAT",
                    "server.baseUrl=https://api-uat.example.test",
                    "db.url=jdbc:postgresql://localhost:5432/local",
                    "db.username=local_user",
                    "db.password=secret",
                    "smtp.appPassword=mail-secret",
                    "update.channel=STABLE",
                    "setup.completed=true",
                    ""));
            byte[] sourceBefore = Files.readAllBytes(sourceConfig);

            Method migrate = WorkspaceManager.class.getDeclaredMethod(
                    "migrateSharedClientConfiguration", Path.class, Path.class);
            migrate.setAccessible(true);
            migrate.invoke(null, source, target);

            Properties detached = new Properties();
            try (var in = Files.newInputStream(target.resolve("Config/config.properties"))) {
                detached.load(in);
            }
            assertEquals("SHARED_CLIENT", detached.getProperty("deployment.mode"));
            assertEquals("UAT", detached.getProperty("deployment.environment"));
            assertEquals("https://api-uat.example.test", detached.getProperty("server.baseUrl"));
            assertEquals("BETA", detached.getProperty("update.channel"));
            assertEquals(org.example.update.BuildInfo.version(), detached.getProperty("app.version"),
                    "Creating managed shared-client storage must stamp the running build and avoid a false first-login update toast");
            assertNull(detached.getProperty("db.url"));
            assertNull(detached.getProperty("db.username"));
            assertNull(detached.getProperty("db.password"));
            assertNull(detached.getProperty("smtp.appPassword"));
            assertArrayEquals(sourceBefore, Files.readAllBytes(sourceConfig),
                    "Detaching to managed client storage must not alter the preserved local workspace");
        } finally {
            deleteTree(source);
            deleteTree(target);
        }
    }


    @Test
    void connectingLocalWorkspaceToExistingServerLeavesRollbackConfigUntouched() throws Exception {
        Path source = Files.createTempDirectory("dse-local-source-");
        Path target = Files.createTempDirectory("dse-shared-target-");
        try {
            Files.createDirectories(source.resolve("Config"));
            Path sourceConfig = source.resolve("Config/config.properties");
            Files.writeString(sourceConfig, String.join("\n",
                    "deployment.mode=LOCAL",
                    "deployment.environment=LOCAL",
                    "server.baseUrl=",
                    "db.url=jdbc:postgresql://localhost:5432/local",
                    "db.username=local_user",
                    "db.password=secret",
                    "postgres.binPath=C:/local/postgres/bin",
                    "postgres.dataPath=C:/local/postgres/data",
                    "smtp.appPassword=mail-secret",
                    "theme=DARK",
                    "setup.completed=true",
                    ""));
            byte[] sourceBefore = Files.readAllBytes(sourceConfig);

            Method create = WorkspaceManager.class.getDeclaredMethod(
                    "createSharedClientConfigurationFromLocal", Path.class, Path.class, String.class, String.class);
            create.setAccessible(true);
            create.invoke(null, source, target, "https://api-uat.example.test", "UAT");

            Properties shared = new Properties();
            try (var in = Files.newInputStream(target.resolve("Config/config.properties"))) {
                shared.load(in);
            }
            assertEquals("SHARED_CLIENT", shared.getProperty("deployment.mode"));
            assertEquals("UAT", shared.getProperty("deployment.environment"));
            assertEquals("https://api-uat.example.test", shared.getProperty("server.baseUrl"));
            assertEquals("BETA", shared.getProperty("update.channel"));
            assertEquals("external", shared.getProperty("runtime.postgres.mode"));
            assertEquals("DARK", shared.getProperty("theme"));
            assertEquals("true", shared.getProperty("setup.completed"));
            assertEquals(org.example.update.BuildInfo.version(), shared.getProperty("app.version"),
                    "Connecting a LOCAL workspace to an existing server must not masquerade as an application update");
            assertNull(shared.getProperty("db.url"));
            assertNull(shared.getProperty("db.username"));
            assertNull(shared.getProperty("db.password"));
            assertNull(shared.getProperty("postgres.binPath"));
            assertNull(shared.getProperty("postgres.dataPath"));
            assertNull(shared.getProperty("smtp.appPassword"));
            assertArrayEquals(sourceBefore, Files.readAllBytes(sourceConfig),
                    "Connect to Existing Server must leave the LOCAL rollback configuration byte-for-byte untouched");
        } finally {
            deleteTree(source);
            deleteTree(target);
        }
    }

    @Test
    void managedSharedClientConnectionCanBeUpdatedInPlaceWithoutRestoringLocalSecrets() throws Exception {
        Path target = Files.createTempDirectory("dse-shared-update-");
        try {
            Properties source = new Properties();
            source.setProperty("deployment.mode", "SHARED_CLIENT");
            source.setProperty("deployment.environment", "UAT");
            source.setProperty("server.baseUrl", "https://api-uat.example.test");
            source.setProperty("db.url", "jdbc:postgresql://localhost:5432/should-not-survive");
            source.setProperty("db.username", "local_user");
            source.setProperty("db.password", "secret");
            source.setProperty("smtp.appPassword", "mail-secret");
            source.setProperty("setup.completed", "true");

            Method writer = WorkspaceManager.class.getDeclaredMethod(
                    "writeManagedSharedClientConfiguration", Properties.class, Path.class, String.class, String.class);
            writer.setAccessible(true);
            writer.invoke(null, source, target, "https://api.example.test", "PROD");

            Properties updated = new Properties();
            try (var in = Files.newInputStream(target.resolve("Config/config.properties"))) {
                updated.load(in);
            }
            assertEquals("SHARED_CLIENT", updated.getProperty("deployment.mode"));
            assertEquals("PROD", updated.getProperty("deployment.environment"));
            assertEquals("https://api.example.test", updated.getProperty("server.baseUrl"));
            assertEquals("STABLE", updated.getProperty("update.channel"));
            assertEquals("external", updated.getProperty("runtime.postgres.mode"));
            assertNull(updated.getProperty("db.url"));
            assertNull(updated.getProperty("db.username"));
            assertNull(updated.getProperty("db.password"));
            assertNull(updated.getProperty("smtp.appPassword"));
        } finally {
            deleteTree(target);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
