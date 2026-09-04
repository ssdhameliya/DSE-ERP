package org.example.service;

import org.example.util.PerformanceMonitor;

import java.util.ArrayList;
import java.util.List;

/** Batch-loads Settings asset previews without owning JavaFX controls. */
public final class SettingsAssetPreviewLoader {
    private SettingsAssetPreviewLoader() { }

    public record Request(String configKey, BrandAssetPolicy.Role role) { }
    public record Result(Request request, SettingsAssetService.Preview preview) { }

    public static List<Result> load(List<Request> requests) {
        long started = System.nanoTime();
        List<Result> results = new ArrayList<>(requests == null ? 0 : requests.size());
        if (requests != null) {
            for (Request request : requests) {
                results.add(new Result(request, SettingsAssetService.loadPreview(request.configKey(), request.role())));
            }
        }
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        if (elapsed >= 20) PerformanceMonitor.event("controller-phase", "settings-preview-background | " + elapsed + " ms");
        return results;
    }
}
