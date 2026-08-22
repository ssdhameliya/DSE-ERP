package org.example.config;

/** Controls whether this desktop owns local services or connects to one company server. */
public enum DeploymentMode {
    LOCAL,
    SHARED_CLIENT;

    public static DeploymentMode parse(String value) {
        if (value == null) return LOCAL;
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return LOCAL; }
    }
}
