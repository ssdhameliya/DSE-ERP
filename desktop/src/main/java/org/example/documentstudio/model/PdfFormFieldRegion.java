package org.example.documentstudio.model;

/** Selectable AcroForm widget region detected in an imported PDF page. */
public record PdfFormFieldRegion(int pageIndex, String fieldName, String value, double x, double y, double width, double height) { }
