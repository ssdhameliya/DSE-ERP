package org.example.documentstudio.model;

import java.nio.file.Path;

/** Selectable raster image placement detected in an imported PDF page. */
public record PdfImageRegion(int pageIndex, double x, double y, double width, double height, Path extractedImage) { }
