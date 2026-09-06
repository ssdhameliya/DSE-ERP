package org.example.api.runtime;

import org.example.shared.RuntimeContract;

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

    public static RuntimeApiClient.RuntimeStatus test(String address) {
        String normalized = normalize(address);
        RuntimeApiClient.RuntimeStatus status = new RuntimeApiClient(normalized).status();
        if (!status.ready()) throw new IllegalStateException(status.message() == null ? "Company server is not ready." : status.message());
        if (!RuntimeContract.SERVICE_NAME.equals(status.service())) throw new IllegalStateException("The address is not a DSE ERP server.");
        if (!RuntimeContract.API_REVISION.equals(status.apiRevision())) throw new IllegalStateException("The company server API is not compatible with this desktop.");
        if (!org.example.update.BuildInfo.buildRevision().equals(status.buildRevision())) throw new IllegalStateException("The company server build is not compatible with this desktop. Server is "+status.buildRevision()+"; desktop requires "+org.example.update.BuildInfo.buildRevision()+".");
        if (!org.example.update.BuildInfo.version().equals(status.version())) throw new IllegalStateException("Desktop version " + org.example.update.BuildInfo.version()
                + " requires company server version " + org.example.update.BuildInfo.version() + ", but the server reports " + status.version() + ".");
        String expectedEnvironment = org.example.config.ConfigManager.getConfiguredDeploymentEnvironment();
        if (!"LOCAL".equals(expectedEnvironment) && !expectedEnvironment.equalsIgnoreCase(status.environment()))
            throw new IllegalStateException("This desktop is configured for " + expectedEnvironment + " but the company server reports " + status.environment() + ".");
        return status;
    }
}
