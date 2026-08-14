package org.example.server.setup;

public final class SetupDtos {
    private SetupDtos() {}
    public record BootstrapRequest(String companyName,String phone,String companyEmail,String gstin,String address,String adminName,String adminUsername,String adminEmail,String adminPassword) {}
    public record BootstrapResponse(boolean success,String message) {}
    public record SetupStatus(boolean required,long userCount,long adminCount) {}
}
