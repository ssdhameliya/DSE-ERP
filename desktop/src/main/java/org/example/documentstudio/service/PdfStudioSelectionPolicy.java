package org.example.documentstudio.service;

import org.example.documentstudio.model.TemplateElement;

import java.util.*;

/** Pure PDF Studio element-selection and descendant expansion policy. */
public final class PdfStudioSelectionPolicy {
    private PdfStudioSelectionPolicy() { }

    public static TemplateElement single(Collection<String> selectedIds, List<TemplateElement> elements) {
        if (selectedIds == null || selectedIds.size() != 1) return null;
        return find(elements, selectedIds.iterator().next());
    }

    public static List<TemplateElement> selected(Collection<String> selectedIds, List<TemplateElement> elements) {
        if (selectedIds == null || selectedIds.isEmpty() || elements == null) return List.of();
        Set<String> ids = new LinkedHashSet<>(selectedIds);
        return elements.stream().filter(element -> ids.contains(element.getId())).toList();
    }

    public static List<TemplateElement> selectedWithDescendants(Collection<String> selectedIds, List<TemplateElement> elements) {
        Set<String> ids = idsWithDescendants(selectedIds, elements);
        if (elements == null || ids.isEmpty()) return List.of();
        return elements.stream().filter(element -> ids.contains(element.getId())).toList();
    }

    public static Set<String> idsWithDescendants(Collection<String> roots, List<TemplateElement> elements) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(roots == null ? List.of() : roots);
        if (elements == null) return ids;
        boolean changed;
        do {
            changed = false;
            for (TemplateElement element : elements) {
                if (!element.getParentId().isBlank() && ids.contains(element.getParentId()) && ids.add(element.getId())) changed = true;
            }
        } while (changed);
        return ids;
    }

    public static TemplateElement find(List<TemplateElement> elements, String id) {
        if (elements == null || id == null) return null;
        return elements.stream().filter(element -> Objects.equals(element.getId(), id)).findFirst().orElse(null);
    }
}
