package org.example.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe in-memory store for strings.
 * Provides add, getAll (joined by newline), and clear operations.
 */
public final class SimpleStringStore {

    // Synchronized list to allow concurrent access.
    private static final List<String> STORE = Collections.synchronizedList(new ArrayList<>());

    private SimpleStringStore() {}

    /**
     * Adds a non-null string to the store.
     * Null values are ignored.
     *
     * @param s the string to add
     */
    public static void add(String s) {
        if (s == null) return;
        STORE.add(s);
    }

    /**
     * Returns all stored entries joined by a newline.
     * The returned string is a snapshot (thread-safe).
     *
     * @return joined entries or empty string if none
     */
    public static String getAll() {
        synchronized (STORE) {
            if (STORE.isEmpty()) return "";
            return String.join("\n", STORE);
        }
    }

    /**
     * Clears all stored entries.
     */
    public static void clear() {
        synchronized (STORE) {
            STORE.clear();
        }
    }
}
