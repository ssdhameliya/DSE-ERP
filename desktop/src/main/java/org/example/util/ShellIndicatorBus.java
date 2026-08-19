package org.example.util;

import java.util.concurrent.CopyOnWriteArrayList;

/** Lightweight desktop event bus for immediate shell-badge refresh requests. */
public final class ShellIndicatorBus {
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private ShellIndicatorBus() {}

    public static void subscribe(Runnable listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void unsubscribe(Runnable listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    public static void publish() {
        for (Runnable listener : LISTENERS) {
            try { listener.run(); } catch (RuntimeException ignored) { }
        }
    }
}
