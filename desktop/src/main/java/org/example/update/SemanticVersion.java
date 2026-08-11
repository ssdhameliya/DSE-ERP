package org.example.update;

import java.util.*;

public record SemanticVersion(int major, int minor, int patch, List<String> preRelease) implements Comparable<SemanticVersion> {
    public SemanticVersion { preRelease = List.copyOf(preRelease == null ? List.of() : preRelease); }

    public static SemanticVersion parse(String raw) {
        String value = Objects.requireNonNullElse(raw, "0.0.0").trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        value = value.split("\\+", 2)[0];
        String[] mainAndPre = value.split("-", 2);
        String[] numbers = mainAndPre[0].split("\\.");
        int major = number(numbers, 0), minor = number(numbers, 1), patch = number(numbers, 2);
        List<String> pre = mainAndPre.length == 2 ? Arrays.asList(mainAndPre[1].split("\\.")) : List.of();
        return new SemanticVersion(major, minor, patch, pre);
    }

    private static int number(String[] values, int index) {
        if (index >= values.length) return 0;
        String digits = values[index].replaceAll("[^0-9].*$", "");
        return digits.isBlank() ? 0 : Integer.parseInt(digits);
    }

    @Override public int compareTo(SemanticVersion other) {
        int c = Integer.compare(major, other.major); if (c != 0) return c;
        c = Integer.compare(minor, other.minor); if (c != 0) return c;
        c = Integer.compare(patch, other.patch); if (c != 0) return c;
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0;
        if (preRelease.isEmpty()) return 1;
        if (other.preRelease.isEmpty()) return -1;
        int max = Math.max(preRelease.size(), other.preRelease.size());
        for (int i=0;i<max;i++) {
            if (i >= preRelease.size()) return -1;
            if (i >= other.preRelease.size()) return 1;
            String a=preRelease.get(i), b=other.preRelease.get(i);
            boolean an=a.matches("\\d+"), bn=b.matches("\\d+");
            c = an && bn ? Integer.compare(Integer.parseInt(a), Integer.parseInt(b)) : an ? -1 : bn ? 1 : a.compareToIgnoreCase(b);
            if (c != 0) return c;
        }
        return 0;
    }

    @Override public String toString() { return major + "." + minor + "." + patch + (preRelease.isEmpty() ? "" : "-" + String.join(".", preRelease)); }
}
