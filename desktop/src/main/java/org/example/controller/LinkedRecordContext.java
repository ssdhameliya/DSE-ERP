package org.example.controller;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** One-shot cross-screen deep-link target for opening the exact linked business record. */
public final class LinkedRecordContext {
    public record Target(String module, Integer recordId, String documentNo, String action, String source) {
        public Target {
            module = normalize(module);
            documentNo = documentNo == null ? "" : documentNo.trim();
            action = normalize(action);
            source = source == null ? "" : source.trim();
        }
        private static String normalize(String value) {
            return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        }
    }

    private static final AtomicReference<Target> TARGET = new AtomicReference<>();
    private LinkedRecordContext() {}

    public static void open(String module, Integer recordId, String documentNo, String action, String source) {
        TARGET.set(new Target(module, recordId, documentNo, action, source));
    }

    public static Target peek() { return TARGET.get(); }

    public static Target consume(String module) {
        String wanted = module == null ? "" : module.trim().toUpperCase(Locale.ROOT);
        Target current = TARGET.get();
        if (current == null || !current.module().equals(wanted)) return null;
        return TARGET.compareAndSet(current, null) ? current : null;
    }

    public static Target consumeAny() { return TARGET.getAndSet(null); }

    public static void clear() { TARGET.set(null); }
}
