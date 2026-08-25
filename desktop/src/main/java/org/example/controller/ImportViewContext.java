package org.example.controller;

/** One-shot navigation hint so freshly imported historical documents are not hidden by register defaults. */
final class ImportViewContext {
    private static String module;
    private ImportViewContext() {}
    static synchronized void request(String value) { module = value; }
    static synchronized boolean consume(String value) {
        if (module != null && module.equalsIgnoreCase(value)) { module = null; return true; }
        return false;
    }
}
