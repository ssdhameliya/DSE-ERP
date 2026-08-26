package org.example.shared;

/** Shared desktop/server runtime contract. Keep wire-level runtime identifiers in one module. */
public final class RuntimeContract {
    public static final String SERVICE_NAME = "dse-erp-server";
    public static final String HEALTH_PATH = "/api/runtime/health";
    public static final String API_REVISION = "spring-security-bearer-v5";
    public static final String BUILD_REVISION = "9.0.15";
    public static final String APP_VERSION = "9.0.15";
    private RuntimeContract() {}
}
