package org.example.documentstudio.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Serializable object placed on top of an imported PDF page. */
public class TemplateElement {
    private String id = UUID.randomUUID().toString();
    private ElementType type = ElementType.TEXT;
    private int pageIndex;
    private double x = 72;
    private double y = 72;
    private double width = 160;
    private double height = 28;
    private String text = "Text";
    private String fieldKey = "";
    private double fontSize = 10;
    private boolean bold;
    private String textColor = "#172033";
    private String fillColor = "#FFFFFF";
    private String strokeColor = "#94A3B8";
    private double strokeWidth = 1;
    private String imagePath = "";
    private double opacity = 1.0;
    private double rotation;
    private boolean preserveAspectRatio = true;
    private String imageFit = "FIT";
    private boolean locked;
    private List<String> tableColumns = new ArrayList<>(List.of(
            "serial", "hsn", "description", "qty", "unit", "rate", "gst", "amount"));
    private double rowHeight = 22;
    private double headerHeight = 24;

    public TemplateElement() {}

    public static TemplateElement of(ElementType type, int pageIndex, double x, double y, double width, double height) {
        TemplateElement element = new TemplateElement();
        element.setType(type);
        element.setPageIndex(pageIndex);
        element.setX(x);
        element.setY(y);
        element.setWidth(width);
        element.setHeight(height);
        if (type == ElementType.WHITEOUT) {
            element.setFillColor("#FFFFFF");
            element.setStrokeColor("#FFFFFF");
        }
        return element;
    }

    public TemplateElement copy() {
        TemplateElement c = new TemplateElement();
        c.id = UUID.randomUUID().toString();
        c.type = type;
        c.pageIndex = pageIndex;
        c.x = x; c.y = y; c.width = width; c.height = height;
        c.text = text; c.fieldKey = fieldKey; c.fontSize = fontSize; c.bold = bold;
        c.textColor = textColor; c.fillColor = fillColor; c.strokeColor = strokeColor;
        c.strokeWidth = strokeWidth; c.imagePath = imagePath; c.opacity = opacity; c.rotation = rotation;
        c.preserveAspectRatio = preserveAspectRatio; c.imageFit = imageFit; c.locked = locked;
        c.tableColumns = new ArrayList<>(tableColumns == null ? List.of() : tableColumns);
        c.rowHeight = rowHeight; c.headerHeight = headerHeight;
        return c;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id; }
    public ElementType getType() { return type; }
    public void setType(ElementType type) { this.type = type == null ? ElementType.TEXT : type; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = Math.max(0, pageIndex); }
    public double getX() { return x; }
    public void setX(double x) { this.x = Math.max(0, x); }
    public double getY() { return y; }
    public void setY(double y) { this.y = Math.max(0, y); }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = Math.max(1, width); }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = Math.max(1, height); }
    public String getText() { return text == null ? "" : text; }
    public void setText(String text) { this.text = text == null ? "" : text; }
    public String getFieldKey() { return fieldKey == null ? "" : fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey == null ? "" : fieldKey; }
    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = Math.max(5, Math.min(72, fontSize)); }
    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }
    public String getTextColor() { return textColor == null ? "#172033" : textColor; }
    public void setTextColor(String textColor) { this.textColor = safeColor(textColor, "#172033"); }
    public String getFillColor() { return fillColor == null ? "#FFFFFF" : fillColor; }
    public void setFillColor(String fillColor) { this.fillColor = safeColor(fillColor, "#FFFFFF"); }
    public String getStrokeColor() { return strokeColor == null ? "#94A3B8" : strokeColor; }
    public void setStrokeColor(String strokeColor) { this.strokeColor = safeColor(strokeColor, "#94A3B8"); }
    public double getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(double strokeWidth) { this.strokeWidth = Math.max(0, Math.min(12, strokeWidth)); }
    public String getImagePath() { return imagePath == null ? "" : imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath == null ? "" : imagePath; }
    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.05, Math.min(1.0, opacity)); }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) {
        double normalized = rotation % 360.0;
        if (normalized < 0) normalized += 360.0;
        this.rotation = normalized;
    }
    public boolean isPreserveAspectRatio() { return preserveAspectRatio; }
    public void setPreserveAspectRatio(boolean preserveAspectRatio) { this.preserveAspectRatio = preserveAspectRatio; }
    public String getImageFit() { return imageFit == null || imageFit.isBlank() ? "FIT" : imageFit; }
    public void setImageFit(String imageFit) {
        String value = imageFit == null ? "FIT" : imageFit.trim().toUpperCase();
        this.imageFit = switch (value) { case "FILL", "STRETCH" -> value; default -> "FIT"; };
    }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public List<String> getTableColumns() { return tableColumns == null ? List.of() : tableColumns; }
    public void setTableColumns(List<String> tableColumns) { this.tableColumns = new ArrayList<>(tableColumns == null ? List.of() : tableColumns); }
    public double getRowHeight() { return rowHeight; }
    public void setRowHeight(double rowHeight) { this.rowHeight = Math.max(12, Math.min(60, rowHeight)); }
    public double getHeaderHeight() { return headerHeight; }
    public void setHeaderHeight(double headerHeight) { this.headerHeight = Math.max(14, Math.min(60, headerHeight)); }

    private static String safeColor(String value, String fallback) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return fallback;
        return value.toUpperCase();
    }
}
