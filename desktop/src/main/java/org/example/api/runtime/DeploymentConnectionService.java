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

    /** Validates server identity/build without imposing a previously persisted environment. */
    public static RuntimeApiClient.RuntimeStatus inspect(String address) {
        String normalized = normalize(address);
        RuntimeApiClient.RuntimeStatus status = new RuntimeApiClient(normalized).status();
        if (!status.ready()) throw new IllegalStateException(status.message() == null ? "Company server is not ready." : status.message());
        if (!RuntimeContract.SERVICE_NAME.equals(status.service())) throw new IllegalStateException("The address is not a DSE ERP server.");
        if (!RuntimeContract.API_REVISION.equals(status.apiRevision())) throw new IllegalStateException("The company server API is not compatible with this desktop.");

        String desktopVersion = BuildInfo.version();
        String serverVersion = status.version() == null ? "" : status.version().trim();
        boolean sameVersion = desktopVersion.equals(serverVersion);
        boolean sameBuild = BuildInfo.buildRevision().equals(status.buildRevision());
        if (!sameVersion || !sameBuild) {
            if (isNewer(serverVersion, desktopVersion)) {
                throw new ClientUpdateRequiredException(
                        serverVersion,
                        status.buildRevision(),
                        desktopVersion,
                        BuildInfo.buildRevision());
            }
            throw new IllegalStateException("The company server build is not compatible with this desktop. Server is "
                    + (status.buildRevision() == null || status.buildRevision().isBlank() ? serverVersion : status.buildRevision())
                    + "; desktop requires " + BuildInfo.buildRevision() + ".");
        }
        return status;
    }

    static void validateEnvironment(String serverEnvironment, String expectedEnvironment) {
        String expected = expectedEnvironment == null ? "LOCAL" : expectedEnvironment.trim().toUpperCase(java.util.Locale.ROOT);
        if (expected.isBlank() || "LOCAL".equals(expected)) return;
        if (serverEnvironment == null || !expected.equalsIgnoreCase(serverEnvironment.trim())) {
            throw new IllegalStateException("Selected environment is " + expected
                    + " but the company server reports " + (serverEnvironment == null || serverEnvironment.isBlank() ? "UNKNOWN" : serverEnvironment) + ".");
        }
    }

    private static boolean isNewer(String candidate, String current) {
        try { return SemanticVersion.parse(candidate).compareTo(SemanticVersion.parse(current)) > 0; }
        catch (Exception ignored) { return false; }
    }

    public static final class ClientUpdateRequiredException extends IllegalStateException {
        private final String requiredVersion;
        private final String serverBuild;
        private final String desktopVersion;
        private final String desktopBuild;

        public ClientUpdateRequiredException(String requiredVersion, String serverBuild, String desktopVersion, String desktopBuild) {
            super("Update required. The company server is DSE ERP " + requiredVersion
                    + " while this desktop is " + desktopVersion + ".");
            this.requiredVersion = requiredVersion;
            this.serverBuild = serverBuild;
            this.desktopVersion = desktopVersion;
            this.desktopBuild = desktopBuild;
        }

        public String requiredVersion() { return requiredVersion; }
        public String serverBuild() { return serverBuild; }
        public String desktopVersion() { return desktopVersion; }
        public String desktopBuild() { return desktopBuild; }
    }
}
