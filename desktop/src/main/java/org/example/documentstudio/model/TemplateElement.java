package org.example.documentstudio.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serializable editable object placed on top of an imported PDF page.
 *
 * <p>PDF Studio V2 intentionally keeps geometry permissive: objects may sit outside the
 * printable page while the editor shows a warning. Export clips naturally at the PDF page.
 * This model is PDF-only and is not shared with Excel Studio.</p>
 */
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
    private boolean italic;
    private String fontFamily = "HELVETICA";
    private String textFit = "SHRINK";
    private String textAlignment = "LEFT";
    private String pageRule = "AUTO";
    private String textColor = "#172033";
    private String fillColor = "#FFFFFF";
    private String strokeColor = "#94A3B8";
    private double strokeWidth = 1;
    private boolean fillEnabled;
    private boolean strokeEnabled;
    private double borderRadius;
    private double paddingTop;
    private double paddingRight;
    private double paddingBottom;
    private double paddingLeft;
    private double lineSpacing = 1.22;
    private String imagePath = "";
    private double opacity = 1.0;
    private double rotation;
    private boolean preserveAspectRatio = true;
    private String imageFit = "FIT";
    private boolean locked;
    private boolean visible = true;
    private String parentId = "";
    private boolean inheritParentStyle;
    private List<String> styleOverrides = new ArrayList<>();
    private String replacementGroupId = "";
    private String replacementSourceKey = "";
    private List<String> tableColumns = new ArrayList<>(List.of(
            "serial", "hsn", "description", "qty", "unit", "rate", "gst", "amount"));
    /** Optional exact column widths in PDF points; when absent the renderer uses semantic weights. */
    private List<Double> tableColumnWidths = new ArrayList<>();
    /** Optional per-column LEFT/CENTER/RIGHT alignment for fixed source grids. */
    private List<String> tableColumnAlignments = new ArrayList<>();
    private double rowHeight = 22;
    private double headerHeight = 24;
    private boolean useSourceTableDesign;
    private List<PathCommand> pathCommands = new ArrayList<>();
    private boolean pathFilled;
    private boolean pathStroked = true;

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
        c.text = text; c.fieldKey = fieldKey; c.fontSize = fontSize; c.bold = bold; c.italic = italic;
        c.fontFamily = fontFamily; c.textFit = textFit; c.textAlignment = textAlignment; c.pageRule = pageRule;
        c.textColor = textColor; c.fillColor = fillColor; c.strokeColor = strokeColor;
        c.strokeWidth = strokeWidth; c.fillEnabled = fillEnabled; c.strokeEnabled = strokeEnabled; c.borderRadius = borderRadius;
        c.paddingTop = paddingTop; c.paddingRight = paddingRight; c.paddingBottom = paddingBottom; c.paddingLeft = paddingLeft;
        c.lineSpacing = lineSpacing;
        c.imagePath = imagePath; c.opacity = opacity; c.rotation = rotation;
        c.preserveAspectRatio = preserveAspectRatio; c.imageFit = imageFit; c.locked = locked; c.visible = visible;
        c.parentId = parentId; c.inheritParentStyle = inheritParentStyle;
        c.styleOverrides = new ArrayList<>(styleOverrides == null ? List.of() : styleOverrides);
        c.replacementGroupId = replacementGroupId; c.replacementSourceKey = replacementSourceKey;
        c.tableColumns = new ArrayList<>(tableColumns == null ? List.of() : tableColumns);
        c.tableColumnWidths = new ArrayList<>(tableColumnWidths == null ? List.of() : tableColumnWidths);
        c.tableColumnAlignments = new ArrayList<>(tableColumnAlignments == null ? List.of() : tableColumnAlignments);
        c.rowHeight = rowHeight; c.headerHeight = headerHeight; c.useSourceTableDesign = useSourceTableDesign;
        c.pathCommands = pathCommands == null ? new ArrayList<>() : pathCommands.stream().map(PathCommand::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        c.pathFilled = pathFilled; c.pathStroked = pathStroked;
        return c;
    }

    /** Copy keeping the same persistent identity; used by undo/redo snapshots. */
    public TemplateElement snapshotCopy() {
        TemplateElement c = copy();
        c.id = id;
        return c;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id; }
    public ElementType getType() { return type; }
    public void setType(ElementType type) {
        this.type = type == null ? ElementType.TEXT : type;
        switch (this.type) {
            case WHITEOUT, RECTANGLE, BLOCK -> { fillEnabled = true; strokeEnabled = true; }
            case LINE -> { fillEnabled = false; strokeEnabled = true; }
            case PATH -> { fillEnabled = pathFilled; strokeEnabled = pathStroked; }
            default -> { /* text/image/repeaters keep explicit/default flags */ }
        }
    }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = Math.max(0, pageIndex); }
    public double getX() { return x; }
    public void setX(double x) { this.x = finite(x, 0); }
    public double getY() { return y; }
    public void setY(double y) { this.y = finite(y, 0); }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = Math.max(0.5, finite(width, 1)); }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = Math.max(0.5, finite(height, 1)); }
    public String getText() { return text == null ? "" : text; }
    public void setText(String text) { this.text = text == null ? "" : text; }
    public String getFieldKey() { return fieldKey == null ? "" : fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey == null ? "" : fieldKey; }
    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = Math.max(1, finite(fontSize, 10)); }
    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }
    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }
    public String getFontFamily() { return fontFamily == null || fontFamily.isBlank() ? "HELVETICA" : fontFamily; }
    public void setFontFamily(String fontFamily) {
        String value = fontFamily == null ? "HELVETICA" : fontFamily.trim().toUpperCase(java.util.Locale.ROOT);
        this.fontFamily = switch (value) { case "TIMES", "COURIER", "ARIAL" -> value; default -> "HELVETICA"; };
    }
    public String getTextFit() { return textFit == null || textFit.isBlank() ? "SHRINK" : textFit; }
    public void setTextFit(String textFit) {
        String value = textFit == null ? "SHRINK" : textFit.trim().toUpperCase(java.util.Locale.ROOT);
        this.textFit = switch (value) { case "WRAP", "CLIP", "FIXED" -> value; default -> "SHRINK"; };
    }
    public String getTextAlignment() { return textAlignment == null || textAlignment.isBlank() ? "LEFT" : textAlignment; }
    public void setTextAlignment(String textAlignment) {
        String value = textAlignment == null ? "LEFT" : textAlignment.trim().toUpperCase(java.util.Locale.ROOT);
        this.textAlignment = switch (value) { case "CENTER", "RIGHT" -> value; default -> "LEFT"; };
    }
    public String getPageRule() { return pageRule == null || pageRule.isBlank() ? "AUTO" : pageRule; }
    public void setPageRule(String pageRule) {
        String value = pageRule == null ? "AUTO" : pageRule.trim().toUpperCase(java.util.Locale.ROOT);
        this.pageRule = switch (value) { case "FIRST", "EVERY", "CONTINUATION", "LAST", "FIXED" -> value; default -> "AUTO"; };
    }
    public String getTextColor() { return textColor == null ? "#172033" : textColor; }
    public void setTextColor(String textColor) { this.textColor = safeColor(textColor, "#172033"); }
    public String getFillColor() { return fillColor == null ? "#FFFFFF" : fillColor; }
    public void setFillColor(String fillColor) { this.fillColor = safeColor(fillColor, "#FFFFFF"); }
    public String getStrokeColor() { return strokeColor == null ? "#94A3B8" : strokeColor; }
    public void setStrokeColor(String strokeColor) { this.strokeColor = safeColor(strokeColor, "#94A3B8"); }
    public double getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(double strokeWidth) { this.strokeWidth = Math.max(0, finite(strokeWidth, 0)); }
    public boolean isFillEnabled() { return fillEnabled; }
    public void setFillEnabled(boolean fillEnabled) { this.fillEnabled = fillEnabled; }
    public boolean isStrokeEnabled() { return strokeEnabled; }
    public void setStrokeEnabled(boolean strokeEnabled) { this.strokeEnabled = strokeEnabled; }
    public double getBorderRadius() { return borderRadius; }
    public void setBorderRadius(double borderRadius) { this.borderRadius = Math.max(0, finite(borderRadius, 0)); }
    public double getPaddingTop() { return paddingTop; }
    public void setPaddingTop(double value) { paddingTop = Math.max(0, finite(value, 0)); }
    public double getPaddingRight() { return paddingRight; }
    public void setPaddingRight(double value) { paddingRight = Math.max(0, finite(value, 0)); }
    public double getPaddingBottom() { return paddingBottom; }
    public void setPaddingBottom(double value) { paddingBottom = Math.max(0, finite(value, 0)); }
    public double getPaddingLeft() { return paddingLeft; }
    public void setPaddingLeft(double value) { paddingLeft = Math.max(0, finite(value, 0)); }
    public double getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(double lineSpacing) { this.lineSpacing = Math.max(0.5, finite(lineSpacing, 1.22)); }
    public String getImagePath() { return imagePath == null ? "" : imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath == null ? "" : imagePath; }
    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0, Math.min(1, finite(opacity, 1))); }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) {
        double normalized = finite(rotation, 0) % 360.0;
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
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public String getParentId() { return parentId == null ? "" : parentId; }
    public void setParentId(String parentId) { this.parentId = parentId == null ? "" : parentId; }
    public boolean isInheritParentStyle() { return inheritParentStyle; }
    public void setInheritParentStyle(boolean inheritParentStyle) { this.inheritParentStyle = inheritParentStyle; }
    public List<String> getStyleOverrides() { return styleOverrides == null ? List.of() : List.copyOf(styleOverrides); }
    public void setStyleOverrides(List<String> styleOverrides) {
        this.styleOverrides = new ArrayList<>(styleOverrides == null ? List.of() : styleOverrides);
    }
    public boolean hasStyleOverride(String property) {
        return property != null && styleOverrides != null && styleOverrides.contains(property);
    }
    public void setStyleOverride(String property, boolean overridden) {
        if (property == null || property.isBlank()) return;
        if (styleOverrides == null) styleOverrides = new ArrayList<>();
        if (overridden) { if (!styleOverrides.contains(property)) styleOverrides.add(property); }
        else styleOverrides.remove(property);
    }
    public void clearStyleOverrides() { if (styleOverrides != null) styleOverrides.clear(); }
    public String getReplacementGroupId() { return replacementGroupId == null ? "" : replacementGroupId; }
    public void setReplacementGroupId(String replacementGroupId) { this.replacementGroupId = replacementGroupId == null ? "" : replacementGroupId; }
    public String getReplacementSourceKey() { return replacementSourceKey == null ? "" : replacementSourceKey; }
    public void setReplacementSourceKey(String replacementSourceKey) { this.replacementSourceKey = replacementSourceKey == null ? "" : replacementSourceKey; }
    public List<String> getTableColumns() { return tableColumns == null ? List.of() : tableColumns; }
    public void setTableColumns(List<String> tableColumns) { this.tableColumns = new ArrayList<>(tableColumns == null ? List.of() : tableColumns); }
    public List<Double> getTableColumnWidths() { return tableColumnWidths == null ? List.of() : tableColumnWidths; }
    public void setTableColumnWidths(List<Double> tableColumnWidths) { this.tableColumnWidths = new ArrayList<>(tableColumnWidths == null ? List.of() : tableColumnWidths); }
    public List<String> getTableColumnAlignments() { return tableColumnAlignments == null ? List.of() : tableColumnAlignments; }
    public void setTableColumnAlignments(List<String> values) { this.tableColumnAlignments = new ArrayList<>(values == null ? List.of() : values); }
    public double getRowHeight() { return rowHeight; }
    public void setRowHeight(double rowHeight) { this.rowHeight = Math.max(1, finite(rowHeight, 22)); }
    public double getHeaderHeight() { return headerHeight; }
    public void setHeaderHeight(double headerHeight) { this.headerHeight = Math.max(0, finite(headerHeight, 24)); }
    public boolean isUseSourceTableDesign() { return useSourceTableDesign; }
    public void setUseSourceTableDesign(boolean useSourceTableDesign) { this.useSourceTableDesign = useSourceTableDesign; }
    public List<PathCommand> getPathCommands() { return pathCommands == null ? List.of() : pathCommands; }
    public void setPathCommands(List<PathCommand> pathCommands) { this.pathCommands = new ArrayList<>(pathCommands == null ? List.of() : pathCommands); }
    public boolean isPathFilled() { return pathFilled; }
    public void setPathFilled(boolean pathFilled) { this.pathFilled = pathFilled; }
    public boolean isPathStroked() { return pathStroked; }
    public void setPathStroked(boolean pathStroked) { this.pathStroked = pathStroked; }

    private static String safeColor(String value, String fallback) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return fallback;
        return value.toUpperCase();
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
