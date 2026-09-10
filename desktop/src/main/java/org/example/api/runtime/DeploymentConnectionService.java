package org.example.api.runtime;

import org.example.shared.RuntimeContract;
import org.example.update.BuildInfo;
import org.example.update.SemanticVersion;

import java.net.URI;

/** Validates a user-supplied company server before shared-client mode is persisted. */
public final class DeploymentConnectionService {
    private DeploymentConnectionService() {}

    public static String normalize(String address) {
        String value = address == null ? "" : address.trim().replaceAll("/+$", "");
        if (value.isBlank()) throw new IllegalArgumentException("Enter the company server address.");
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("The company server address is not valid."); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
            throw new IllegalArgumentException("The company server address must start with https:// or http://.");
        if (uri.getHost() == null || uri.getHost().isBlank())
            throw new IllegalArgumentException("The company server address must include a host name or IP address.");
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath())))
            throw new IllegalArgumentException("Enter only the server address, without credentials, query text or an API path.");
        return value;
    }

    /**
     * Existing Shared Clients validate against their persisted environment.
     * Setup/Settings flows may use the overload that supplies the environment currently selected by the user.
     */
    public static RuntimeApiClient.RuntimeStatus test(String address) {
        return test(address, org.example.config.ConfigManager.getConfiguredDeploymentEnvironment());
    }

    public static RuntimeApiClient.RuntimeStatus test(String address, String expectedEnvironment) {
        RuntimeApiClient.RuntimeStatus status = inspect(address);
        validateEnvironment(status.environment(), expectedEnvironment);
        return status;
    }

    /**
     * Validates server identity and the server-owned desktop compatibility policy.
     * A newer server is allowed when this desktop is still at or above the minimum supported
     * desktop version. API revision mismatches remain a hard block.
     */
    public static RuntimeApiClient.RuntimeStatus inspect(String address) {
        String normalized = normalize(address);
        RuntimeApiClient.RuntimeStatus status = new RuntimeApiClient(normalized).status();
        if (!status.ready()) throw new IllegalStateException(status.message() == null ? "Company server is not ready." : status.message());
        validateCompatibility(status);
        return status;
    }

    static void validateCompatibility(RuntimeApiClient.RuntimeStatus status) {
        validateCompatibility(status, BuildInfo.version(), BuildInfo.buildRevision());
    }

    static void validateCompatibility(RuntimeApiClient.RuntimeStatus status, String desktopVersion, String desktopBuild) {
        if (!RuntimeContract.SERVICE_NAME.equals(status.service()))
            throw new IllegalStateException("The address is not a DSE ERP server.");
        if (!RuntimeContract.API_REVISION.equals(status.apiRevision()))
            throw new IllegalStateException("The company server API is not compatible with this desktop.");

        String desktop = safe(desktopVersion);
        String desktopRevision = safe(desktopBuild);
        String server = safe(status.version());
        String serverRevision = safe(status.buildRevision());
        if (server.isBlank()) throw new IllegalStateException("The company server did not report its DSE ERP version.");

        String minimum = effectiveMinimumSupportedDesktopVersion(status);
        if (compareVersions(minimum, server) > 0) {
            throw new IllegalStateException("The company server compatibility policy is invalid. Minimum supported desktop "
                    + minimum + " is newer than server version " + server + ".");
        }

        if (compareVersions(desktop, minimum) < 0) {
            throw new ClientUpdateRequiredException(
                    server,
                    serverRevision,
                    desktop,
                    desktopRevision,
                    minimum);
        }

        int serverVsDesktop = compareVersions(server, desktop);
        if (serverVsDesktop > 0) {
            // Compatible older desktop: the server is authoritative for the minimum supported
            // desktop version. Main will offer the update, but Not Now may continue to login.
            return;
        }
        if (serverVsDesktop < 0) {
            throw new IllegalStateException("The company server is older than this desktop. Server is "
                    + server + "; desktop is " + desktop + ". Update the company server before using this desktop.");
        }

        // Same application version must still be the same build. A differing build under the same
        // version is not a supported compatibility window; it usually indicates a stale/rebuilt server.
        if (!desktopRevision.equals(serverRevision)) {
            throw new IllegalStateException("The company server build is not compatible with this desktop. Server is "
                    + (serverRevision.isBlank() ? server : serverRevision)
                    + "; desktop requires " + desktopRevision + ".");
        }
    }

    public static boolean isCompatibleClientUpdateAvailable(RuntimeApiClient.RuntimeStatus status) {
        return isCompatibleClientUpdateAvailable(status, BuildInfo.version());
    }

    static boolean isCompatibleClientUpdateAvailable(RuntimeApiClient.RuntimeStatus status, String desktopVersion) {
        if (status == null || !RuntimeContract.API_REVISION.equals(status.apiRevision())) return false;
        String server = safe(status.version());
        String desktop = safe(desktopVersion);
        if (server.isBlank() || desktop.isBlank()) return false;
        String minimum = effectiveMinimumSupportedDesktopVersion(status);
        return compareVersions(server, desktop) > 0 && compareVersions(desktop, minimum) >= 0;
    }

    public static String effectiveMinimumSupportedDesktopVersion(RuntimeApiClient.RuntimeStatus status) {
        if (status == null) return "";
        String configured = safe(status.minimumSupportedDesktopVersion());
        // Backward-compatible safe fallback: older servers do not publish this field, therefore
        // exact server version remains required instead of silently widening compatibility.
        return configured.isBlank() ? safe(status.version()) : configured;
    }

    static void validateEnvironment(String serverEnvironment, String expectedEnvironment) {
        String expected = expectedEnvironment == null ? "LOCAL" : expectedEnvironment.trim().toUpperCase(java.util.Locale.ROOT);
        if (expected.isBlank() || "LOCAL".equals(expected)) return;
        if (serverEnvironment == null || !expected.equalsIgnoreCase(serverEnvironment.trim())) {
            throw new IllegalStateException("Selected environment is " + expected
                    + " but the company server reports " + (serverEnvironment == null || serverEnvironment.isBlank() ? "UNKNOWN" : serverEnvironment) + ".");
        }
    }

    private static int compareVersions(String left, String right) {
        try { return SemanticVersion.parse(left).compareTo(SemanticVersion.parse(right)); }
        catch (Exception exception) {
            throw new IllegalStateException("DSE ERP version compatibility could not be evaluated: " + left + " vs " + right + ".", exception);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class ClientUpdateRequiredException extends IllegalStateException {
        private final String requiredVersion;
        private final String serverBuild;
        private final String desktopVersion;
        private final String desktopBuild;
        private final String minimumSupportedDesktopVersion;

        public ClientUpdateRequiredException(String requiredVersion, String serverBuild, String desktopVersion,
                                             String desktopBuild, String minimumSupportedDesktopVersion) {
            super("Update required. The company server is DSE ERP " + requiredVersion
                    + " and supports desktop " + minimumSupportedDesktopVersion + " or newer, while this desktop is "
                    + desktopVersion + ".");
            this.requiredVersion = requiredVersion;
            this.serverBuild = serverBuild;
            this.desktopVersion = desktopVersion;
            this.desktopBuild = desktopBuild;
            this.minimumSupportedDesktopVersion = minimumSupportedDesktopVersion;
        }

        public String requiredVersion() { return requiredVersion; }
        public String serverBuild() { return serverBuild; }
        public String desktopVersion() { return desktopVersion; }
        public String desktopBuild() { return desktopBuild; }
        public String minimumSupportedDesktopVersion() { return minimumSupportedDesktopVersion; }
    }
}
