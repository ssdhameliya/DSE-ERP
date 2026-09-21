package org.example.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseHighlightsCurrentContractTest {
    @Test
    void currentReleaseNotesAreApplicationOwnedAndDescribeTheCurrentCorrectionSet() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/update/ReleaseHighlights.java"));
        assertTrue(source.contains("Excel Studio server saves now execute inside the API transaction boundary"));
        assertTrue(source.contains("Action clicks now keep that selected row in its current visible position"));
        assertTrue(source.contains("duplicate-value highlighting"));
        assertTrue(source.contains("Test Email is now shown only on the Email section"));
        assertTrue(source.contains("Customer-facing branding"));

        int start = source.indexOf("public static String resolve(");
        String resolve = source.substring(start);
        assertTrue(resolve.contains("if (BuildInfo.version().equals(version)) return fallback"),
                "The running build must use its packaged application-owned release summary");
        assertTrue(resolve.contains("generatedGitHubSummary"),
                "Historical/other release notes should still reject auto-generated GitHub summaries");
    }
}
