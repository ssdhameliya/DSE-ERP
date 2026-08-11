package org.example.util;

import javafx.animation.AnimationTimer;
import java.util.concurrent.atomic.AtomicLong;

/** Development-safe detector for long JavaFX pulse gaps. Logging only; no UI impact. */
public final class FxThreadWatchdog {
    private static final AtomicLong LAST_WARNING = new AtomicLong();
    private static AnimationTimer timer;
    private FxThreadWatchdog() { }
    public static synchronized void install() {
        if (timer != null) return;
        timer = new AnimationTimer() {
            private long previous;
            @Override public void handle(long now) {
                if (previous != 0) {
                    long gapMs = (now - previous) / 1_000_000L;
                    long epoch = System.currentTimeMillis();
                    if (gapMs >= 250 && epoch - LAST_WARNING.get() >= 2_000) {
                        LAST_WARNING.set(epoch);
                        PerformanceMonitor.event("fx-thread-stall", gapMs + " ms pulse gap");
                    }
                }
                previous = now;
            }
        };
        timer.start();
    }
}
