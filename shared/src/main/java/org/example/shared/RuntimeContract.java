package org.example.shared;

/** Shared desktop/server runtime contract. Keep wire-level runtime identifiers in one module. */
public final class RuntimeContract {
    public static final String SERVICE_NAME = "dse-erp-server";
    public static final String HEALTH_PATH = "/api/runtime/health";
    public static final String API_REVISION = "spring-security-bearer-v3";
    public static final String APP_VERSION = "7.3.11";
    private RuntimeContract() {}
}
