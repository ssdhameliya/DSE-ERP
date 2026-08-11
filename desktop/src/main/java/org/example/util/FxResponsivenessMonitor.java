package org.example.util;

import javafx.animation.AnimationTimer;

/** Detects long JavaFX pulse gaps without doing file I/O on the JavaFX thread. */
public final class FxResponsivenessMonitor extends AnimationTimer {
    private long previous;
    private long armedAt;

    @Override public void start() {
        previous = 0;
        armedAt = System.nanoTime();
        super.start();
    }

    @Override public void handle(long now) {
        if (previous != 0 && now - armedAt > 2_000_000_000L) {
            long gap = (now - previous) / 1_000_000L;
            if (gap > PerformanceBudgets.FX_FREEZE_MS) {
                PerformanceMonitor.event("fx-freeze",
                        "gap=" + gap + " ms | budget=" + PerformanceBudgets.FX_FREEZE_MS + " ms");
            }
        }
        previous = now;
    }
}
