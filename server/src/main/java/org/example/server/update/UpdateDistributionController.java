package org.example.server.update;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

/** Public pre-login update gateway. Only release binaries/metadata are exposed; source and GitHub credentials stay private. */
@RestController
@RequestMapping("/api/updates")
public final class UpdateDistributionController {
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            "content-type", "content-length", "content-range", "accept-ranges", "etag", "last-modified", "content-disposition");

    private final UpdateDistributionService updates;

    public UpdateDistributionController(UpdateDistributionService updates) {
        this.updates = updates;
    }

    @GetMapping("/releases/latest")
    public UpdateDistributionService.ReleaseView latest(
            @RequestParam(defaultValue = "false") boolean includePrerelease) throws Exception {
        return updates.latest(includePrerelease);
    }

    @GetMapping("/releases")
    public List<UpdateDistributionService.ReleaseView> releases(
            @RequestParam(defaultValue = "false") boolean includePrerelease,
            @RequestParam(defaultValue = "50") int limit) throws Exception {
        return updates.releases(includePrerelease, limit);
    }

    @GetMapping("/releases/{version}")
    public UpdateDistributionService.ReleaseView byVersion(@PathVariable String version) throws Exception {
        return updates.byVersion(version);
    }

    @GetMapping("/assets/{assetId}")
    public void asset(@PathVariable long assetId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String range = request.getHeader("Range");
        HttpResponse<InputStream> upstream = updates.openAsset(assetId, range);
        response.setStatus(upstream.statusCode());
        upstream.headers().map().forEach((name, values) -> {
            if (!FORWARDED_HEADERS.contains(name.toLowerCase())) return;
            for (String value : values) response.addHeader(name, value);
        });
        try (InputStream input = upstream.body()) {
            input.transferTo(response.getOutputStream());
        }
    }
}
