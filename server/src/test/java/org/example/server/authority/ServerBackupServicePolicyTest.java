package org.example.server.authority;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerBackupServicePolicyTest {
    @Test
    void stagedRestoreIsNotAVisibleOrRetainedOrdinaryBackup() {
        assertFalse(ServerBackupService.isOrdinaryBackupName("restore-pending.pgbackup"));
        assertFalse(ServerBackupService.isOrdinaryBackupName("restore-validation-123.pgbackup"));
        assertTrue(ServerBackupService.isOrdinaryBackupName("DSE-ERP-Server-20260906-141635.pgbackup"));
        assertTrue(ServerBackupService.isOrdinaryBackupName("Imported-20260906-141635-source.pgbackup"));
    }
}
