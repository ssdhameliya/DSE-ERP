package org.example.update;

import org.example.api.runtime.RuntimeApiClient;

/**
 * Schema migrations are server-owned. The desktop only verifies that the
 * Spring runtime is READY; Spring executes versioned migrations before this
 * point in the startup lifecycle.
 */
public final class DatabaseMigrationManager {
    private DatabaseMigrationManager() {}

    public static MigrationResult migrate() {
        RuntimeApiClient.RuntimeStatus status = new RuntimeApiClient().status();
        if (status == null || !status.ready()) throw new IllegalStateException("Spring schema service is not ready");
        int target = BuildInfo.databaseMigrationVersion();
        return new MigrationResult(target, target, false);
    }

    public record MigrationResult(int fromVersion,int toVersion,boolean changed) {}
}
