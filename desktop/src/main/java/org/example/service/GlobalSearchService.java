package org.example.service;

import org.example.api.support.SupportApiClient;
import java.util.List;

/** Performs one server-backed search across the ERP's operational modules. */
public final class GlobalSearchService {
    private final SupportApiClient api = new SupportApiClient();

    public record SearchResult(String module, String reference, String description,
                               String detail, String targetFxml) {
        @Override public String toString() {
            return module + "  •  " + reference + "\n" + description +
                (detail == null || detail.isBlank() ? "" : "  •  " + detail);
        }
    }

    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return api.search(query).stream()
            .map(r -> new SearchResult(r.module(), r.reference(), r.description(), r.detail(), r.targetFxml()))
            .toList();
    }
}
