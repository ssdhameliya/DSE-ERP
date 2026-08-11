package org.example.util;

/** Release 5.0.3 interactive performance acceptance budgets, in milliseconds. */
public final class PerformanceBudgets {
    public static final long WARM_STARTUP_MS = 5_000;
    public static final long LOGIN_MS = 1_500;
    public static final long FIRST_REGISTER_MS = 1_500;
    public static final long CACHED_PAGE_MS = 300;
    public static final long FX_FREEZE_MS = 100;

    private PerformanceBudgets() { }

    public static void record(String operation, long elapsed, long budget) {
        PerformanceMonitor.event(elapsed <= budget ? "budget-pass" : "budget-fail",
                operation + " | elapsed=" + elapsed + " ms | budget=" + budget + " ms");
    }
}
