package org.example.documentstudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.documentstudio.model.TemplateData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts the JSON business contract into the existing renderer's flat lookup map without changing PDF rendering. */
public final class JsonTemplateDataAdapter {
    private JsonTemplateDataAdapter() { }

    public static TemplateData fromJson(JsonNode json, TemplateData legacy) {
        TemplateData base = legacy == null ? new TemplateData(Map.of(), Map.of(), java.util.List.of(), java.util.List.of(), "") : legacy;
        Map<String,String> values = new LinkedHashMap<>(base.values());
        flatten("", json, values);
        return new TemplateData(values, base.images(), base.items(), base.charges(), base.gstType());
    }

    private static void flatten(String prefix, JsonNode node, Map<String,String> values) {
        if (node == null || node.isNull()) return;
        if (node.isValueNode()) {
            if (!prefix.isBlank()) values.put(prefix, node.asText(""));
            return;
        }
        if (node.isArray()) return; // Repeaters remain strongly typed in TemplateData for renderer compatibility.
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
            flatten(key, entry.getValue(), values);
        }
    }
}
