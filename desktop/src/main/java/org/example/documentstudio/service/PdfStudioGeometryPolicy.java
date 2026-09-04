package org.example.documentstudio.service;

/** Pure numeric geometry helpers shared by PDF Studio interactions. */
public final class PdfStudioGeometryPolicy {
    private PdfStudioGeometryPolicy() { }

    public static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
