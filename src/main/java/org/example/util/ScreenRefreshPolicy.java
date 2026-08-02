package org.example.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Central refresh policy for cached screens. */
public final class ScreenRefreshPolicy {
    public enum Mode { ALWAYS, WHEN_STALE, MANUAL, NEVER_CACHE }
    private static final Map<String, Instant> LAST_REFRESH = new ConcurrentHashMap<>();
    private static final Duration DEFAULT_STALE_AFTER = Duration.ofSeconds(30);
    private ScreenRefreshPolicy() { }
    public static boolean shouldRefresh(String screenKey, Mode mode) {
        return shouldRefresh(screenKey, mode, DEFAULT_STALE_AFTER);
    }
    public static boolean shouldRefresh(String screenKey, Mode mode, Duration staleAfter) {
        if (mode == Mode.ALWAYS) return true;
        if (mode == Mode.MANUAL) return false;
        Instant last = LAST_REFRESH.get(screenKey);
        return last == null || Duration.between(last, Instant.now()).compareTo(staleAfter) >= 0;
    }
    public static void markRefreshed(String screenKey) { if (screenKey != null) LAST_REFRESH.put(screenKey, Instant.now()); }
    public static void invalidate(String screenKey) { if (screenKey != null) LAST_REFRESH.remove(screenKey); }
    public static void invalidateAll() { LAST_REFRESH.clear(); }
}
