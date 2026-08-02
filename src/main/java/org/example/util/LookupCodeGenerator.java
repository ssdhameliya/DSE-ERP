package org.example.util;

import java.util.Map;

public final class LookupCodeGenerator {

    private LookupCodeGenerator() {
    }

    private static final Map<String, String> PREFIXES = Map.of(
            "CATEGORY", "CAT",
            "UNIT", "UNT",
            "MATERIAL", "MAT",
            "BRAND", "BRD",
            "GST", "GST"
    );

    public static String getPrefix(String lookupType) {
        return PREFIXES.getOrDefault(lookupType, "GEN");
    }

}