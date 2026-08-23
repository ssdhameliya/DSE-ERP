package org.example.service;

import org.example.api.support.SupportApiClient;
import java.util.List;

/** Performs one server-backed search across all permitted ERP record modules. */
public final class GlobalSearchService {
    private final SupportApiClient api = new SupportApiClient();

    public record SearchResult(String module, String moduleKey, Long recordId, String reference, String description,
                               String detail, String targetFxml, String permission) {
        @Override public String toString() {
            return module + "  •  " + reference + "\n" + description +
                (detail == null || detail.isBlank() ? "" : "  •  " + detail);
        }
    }

    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return api.search(query).stream()
            .filter(r -> r.permission() == null || r.permission().isBlank() || PermissionService.allowed(r.permission()))
            .map(r -> new SearchResult(r.module(), r.moduleKey(), r.recordId(), r.reference(), r.description(), r.detail(), r.targetFxml(), r.permission()))
            .toList();
    }
}
