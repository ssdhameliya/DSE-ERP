package org.example.documentstudio.model;

import org.example.invoice.model.TaxInvoiceItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Runtime values supplied to a saved template. */
public record TemplateData(
        Map<String, String> values,
        Map<String, Path> images,
        List<TaxInvoiceItem> items,
        String gstType) {
    public TemplateData {
        values = values == null ? Map.of() : Map.copyOf(values);
        images = images == null ? Map.of() : Map.copyOf(images);
        items = items == null ? List.of() : List.copyOf(items);
        gstType = gstType == null ? "" : gstType;
    }

    public String value(String key) { return values.getOrDefault(key, ""); }
    public Path image(String key) { return images.get(key); }
}
