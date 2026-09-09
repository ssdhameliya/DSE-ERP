package org.example.update;

import org.example.config.ConfigManager;
import java.time.Instant;

/** Persistent snapshot of the latest successfully observed release. */
public final class UpdateState {
    private UpdateState() {}

    public static void recordSuccess(UpdateRelease release) {
        if (release == null) return;
        ConfigManager.setWithoutSaving("update.latestVersion", release.version().toString());
        ConfigManager.setWithoutSaving("update.latestReleaseUrl", release.htmlUrl() == null ? "" : release.htmlUrl().toString());
        ConfigManager.setWithoutSaving("update.latestPublishedAt", release.publishedAt() == null ? "" : release.publishedAt().toString());
        ConfigManager.setWithoutSaving("update.latestChannel", ConfigManager.getEffectiveUpdateChannel());
        ConfigManager.setWithoutSaving("update.lastChecked", Instant.now().toString());
        ConfigManager.setWithoutSaving("update.lastCheckError", "");
        ConfigManager.save();
    }

    public static void recordFailure(Throwable failure) {
        ConfigManager.setWithoutSaving("update.lastCheckError", rootMessage(failure));
        ConfigManager.setWithoutSaving("update.lastCheckAttempt", Instant.now().toString());
        ConfigManager.save();
    }

    public static String latestVersion() { return ConfigManager.get("update.latestVersion", "").trim(); }
    public static String lastError() { return ConfigManager.get("update.lastCheckError", "").trim(); }

    public static String statusText() {
        String latest = latestVersion();
        if (latest.isBlank()) return lastError().isBlank() ? "Not checked yet" : "Unable to refresh";
        int comparison = SemanticVersion.parse(latest).compareTo(SemanticVersion.parse(BuildInfo.version()));
        String base = comparison > 0 ? "Update available" : "Up to date";
        return lastError().isBlank() ? base : base + " • latest known";
    }

    private static String rootMessage(Throwable t) {
        if (t == null) return "Unable to refresh GitHub Releases";
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null || t.getMessage().isBlank() ? t.getClass().getSimpleName() : t.getMessage();
    }
}
