package org.example.update;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public record UpdateRelease(String tag, String name, String notes, Instant publishedAt,
                            boolean prerelease, List<Asset> assets, URI htmlUrl) {
    public UpdateRelease { assets = List.copyOf(assets); }
    public SemanticVersion version() { return SemanticVersion.parse(tag); }
    public record Asset(String name, long size, URI downloadUrl, String contentType) {}
}
