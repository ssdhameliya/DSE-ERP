package org.example.server.support;

public final class StorageDtos {
    private StorageDtos() { }

    public record Policy(int logRetentionDays, int reportRetentionDays, int exportRetentionDays,
                         int diagnosticRetentionDays, int importResultRetentionDays,
                         int tempRetentionDays, boolean compressLogs) { }

    public record Status(String workspace, long documentsBytes, long attachmentsBytes, long reportsBytes,
                         long exportsBytes, long logsBytes, long backupsBytes, long tempBytes,
                         long totalManagedBytes, String lastCleanupAt, String lastCleanupSummary,
                         Policy policy) { }

    public record CleanupResult(boolean dryRun, int filesDeleted, int filesCompressed,
                                long bytesReclaimed, String completedAt, String summary) { }
}
