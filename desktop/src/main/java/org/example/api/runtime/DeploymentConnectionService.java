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
        if (!RuntimeContract.BUILD_REVISION.equals(status.buildRevision())) throw new IllegalStateException("The company server build is not compatible with this desktop. Server is "+status.buildRevision()+"; desktop requires "+RuntimeContract.BUILD_REVISION+".");
        if (!RuntimeContract.APP_VERSION.equals(status.version())) throw new IllegalStateException("Desktop version " + RuntimeContract.APP_VERSION
                + " requires company server version " + RuntimeContract.APP_VERSION + ", but the server reports " + status.version() + ".");
        return status;
    }
}
