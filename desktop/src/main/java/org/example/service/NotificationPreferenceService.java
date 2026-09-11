package org.example.service;

import org.example.api.insights.InsightsApiClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-backed, per-user notification preferences with a small desktop cache for toast gating. */
public final class NotificationPreferenceService {
    public static final List<String> CATEGORY_KEYS = List.of(
            "sales", "purchases", "quotations", "returns", "payments", "inventory", "banking", "reports",
            "reminders", "communication", "approval", "imports", "backup", "update", "security", "system"
    );

    public record Preferences(boolean enabled, boolean toasts, Map<String, Boolean> categories) {
        public Preferences {
            LinkedHashMap<String, Boolean> normalized = new LinkedHashMap<>();
            for (String key : CATEGORY_KEYS) normalized.put(key, categories == null || categories.getOrDefault(key, true));
            normalized.put("security", true);
            categories = Map.copyOf(normalized);
        }
        public boolean categoryEnabled(String key) { return categories.getOrDefault(key, true); }
    }

    private static volatile Preferences cached = defaults();

    private NotificationPreferenceService() { }

    public static Preferences current() { return cached; }

    public static Preferences refreshStrict() {
        InsightsApiClient.NotificationPreferences remote = new InsightsApiClient().notificationPreferences();
        Preferences value = new Preferences(remote.enabled(), remote.toasts(), remote.categories());
        cached = value;
        return value;
    }

    public static Preferences save(Preferences preferences) {
        Preferences requested = preferences == null ? defaults() : preferences;
        InsightsApiClient.NotificationPreferences remote = new InsightsApiClient().saveNotificationPreferences(
                new InsightsApiClient.NotificationPreferences(requested.enabled(), requested.toasts(), requested.categories()));
        Preferences saved = new Preferences(remote.enabled(), remote.toasts(), remote.categories());
        cached = saved;
        return saved;
    }

    public static boolean toastsEnabled() { return cached.toasts(); }

    public static void reset() { cached = defaults(); }

    private static Preferences defaults() {
        LinkedHashMap<String, Boolean> categories = new LinkedHashMap<>();
        for (String key : CATEGORY_KEYS) categories.put(key, true);
        return new Preferences(true, true, categories);
    }
}
