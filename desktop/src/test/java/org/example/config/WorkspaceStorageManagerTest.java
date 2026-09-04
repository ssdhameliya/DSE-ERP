package org.example.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceStorageManagerTest {
    @Test
    void financialYearUsesIndianAprilToMarchBoundary() {
        assertEquals("2026-27", WorkspaceStorageManager.financialYear(LocalDate.of(2026, 4, 1)));
        assertEquals("2026-27", WorkspaceStorageManager.financialYear(LocalDate.of(2027, 3, 31)));
        assertEquals("2027-28", WorkspaceStorageManager.financialYear(LocalDate.of(2027, 4, 1)));
    }

    @Test
    void storageSegmentsCannotEscapeWorkspace() {
        assertEquals("INV-2026-001", WorkspaceStorageManager.safeSegment("INV/2026:001"));
        assertEquals("Unspecified", WorkspaceStorageManager.safeSegment(".."));
        assertFalse(WorkspaceStorageManager.safeFileName("A/B:C?.pdf").contains("/"));
    }
}
