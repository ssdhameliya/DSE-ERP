package org.example.controller;

/** Carries the requested import module while navigating to the shared import screen. */
public final class ImportScreenContext {
    private static String requestedModule;
    private ImportScreenContext() { }
    public static void select(String module) { requestedModule = module; }
    public static String consume() {
        String value = requestedModule;
        requestedModule = null;
        return value;
    }
}
