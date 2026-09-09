package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.TemplateElement;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves PDF Studio block/container style inheritance without changing object geometry or data bindings.
 * Child objects inherit the parent style until a property is explicitly overridden on the child.
 */
public final class PdfStyleResolver {
    private PdfStyleResolver() { }

    public static TemplateElement effective(DocumentTemplate template, TemplateElement element) {
        if (element == null) return null;
        return effective(template, element, new HashSet<>());
    }

    private static TemplateElement effective(DocumentTemplate template, TemplateElement element, Set<String> visiting) {
        TemplateElement view = element.snapshotCopy();
        if (template == null || !element.isInheritParentStyle() || element.getParentId().isBlank()) return view;
        if (!visiting.add(element.getId())) return view;
        TemplateElement parent = template.getElements().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), element.getParentId()))
                .findFirst().orElse(null);
        if (parent == null) return view;
        TemplateElement parentStyle = effective(template, parent, visiting);
        inherit(parentStyle, view, element);
        return view;
    }


    /** Parent containers also control visibility. Child visibility remains its own override when the parent is shown. */
    public static boolean effectivelyVisible(DocumentTemplate template, TemplateElement element) {
        if (element == null || !element.isVisible()) return false;
        return effectivelyVisible(template, element, new HashSet<>());
    }

    private static boolean effectivelyVisible(DocumentTemplate template, TemplateElement element, Set<String> visiting) {
        if (element == null || !element.isVisible()) return false;
        if (template == null || element.getParentId().isBlank()) return true;
        if (!visiting.add(element.getId())) return true;
        TemplateElement parent = template.getElements().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), element.getParentId()))
                .findFirst().orElse(null);
        return parent == null || effectivelyVisible(template, parent, visiting);
    }

    /** Re-evaluates which child style values are genuine overrides of the effective parent style. */
    public static void updateOverrides(DocumentTemplate template, TemplateElement element) {
        if (template == null || element == null || !element.isInheritParentStyle() || element.getParentId().isBlank()) return;
        TemplateElement parent = template.getElements().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), element.getParentId()))
                .findFirst().orElse(null);
        if (parent == null) return;
        TemplateElement p = effective(template, parent);
        set(element, "fontFamily", !Objects.equals(element.getFontFamily(), p.getFontFamily()));
        set(element, "fontSize", different(element.getFontSize(), p.getFontSize()));
        set(element, "bold", element.isBold() != p.isBold());
        set(element, "italic", element.isItalic() != p.isItalic());
        set(element, "textFit", !Objects.equals(element.getTextFit(), p.getTextFit()));
        set(element, "textAlignment", !Objects.equals(element.getTextAlignment(), p.getTextAlignment()));
        set(element, "textColor", !Objects.equals(element.getTextColor(), p.getTextColor()));
        set(element, "fillColor", !Objects.equals(element.getFillColor(), p.getFillColor()));
        set(element, "strokeColor", !Objects.equals(element.getStrokeColor(), p.getStrokeColor()));
        set(element, "strokeWidth", different(element.getStrokeWidth(), p.getStrokeWidth()));
        set(element, "fillEnabled", element.isFillEnabled() != p.isFillEnabled());
        set(element, "strokeEnabled", element.isStrokeEnabled() != p.isStrokeEnabled());
        set(element, "borderRadius", different(element.getBorderRadius(), p.getBorderRadius()));
        set(element, "paddingTop", different(element.getPaddingTop(), p.getPaddingTop()));
        set(element, "paddingRight", different(element.getPaddingRight(), p.getPaddingRight()));
        set(element, "paddingBottom", different(element.getPaddingBottom(), p.getPaddingBottom()));
        set(element, "paddingLeft", different(element.getPaddingLeft(), p.getPaddingLeft()));
        set(element, "lineSpacing", different(element.getLineSpacing(), p.getLineSpacing()));
        set(element, "opacity", different(element.getOpacity(), p.getOpacity()));
    }

    /** Preserve the currently visible inherited appearance before detaching from the parent style. */
    public static void freezeEffectiveStyle(DocumentTemplate template, TemplateElement element) {
        if (element == null) return;
        TemplateElement effective = effective(template, element);
        copyStyle(effective, element);
        element.setInheritParentStyle(false);
        element.clearStyleOverrides();
    }

    public static void copyStyle(TemplateElement source, TemplateElement target) {
        if (source == null || target == null) return;
        target.setFontFamily(source.getFontFamily());
        target.setFontSize(source.getFontSize());
        target.setBold(source.isBold());
        target.setItalic(source.isItalic());
        target.setTextFit(source.getTextFit());
        target.setTextAlignment(source.getTextAlignment());
        target.setTextColor(source.getTextColor());
        target.setFillColor(source.getFillColor());
        target.setStrokeColor(source.getStrokeColor());
        target.setStrokeWidth(source.getStrokeWidth());
        target.setFillEnabled(source.isFillEnabled());
        target.setStrokeEnabled(source.isStrokeEnabled());
        target.setBorderRadius(source.getBorderRadius());
        target.setPaddingTop(source.getPaddingTop());
        target.setPaddingRight(source.getPaddingRight());
        target.setPaddingBottom(source.getPaddingBottom());
        target.setPaddingLeft(source.getPaddingLeft());
        target.setLineSpacing(source.getLineSpacing());
        target.setOpacity(source.getOpacity());
    }

    private static void inherit(TemplateElement parent, TemplateElement target, TemplateElement source) {
        if (!source.hasStyleOverride("fontFamily")) target.setFontFamily(parent.getFontFamily());
        if (!source.hasStyleOverride("fontSize")) target.setFontSize(parent.getFontSize());
        if (!source.hasStyleOverride("bold")) target.setBold(parent.isBold());
        if (!source.hasStyleOverride("italic")) target.setItalic(parent.isItalic());
        if (!source.hasStyleOverride("textFit")) target.setTextFit(parent.getTextFit());
        if (!source.hasStyleOverride("textAlignment")) target.setTextAlignment(parent.getTextAlignment());
        if (!source.hasStyleOverride("textColor")) target.setTextColor(parent.getTextColor());
        if (!source.hasStyleOverride("fillColor")) target.setFillColor(parent.getFillColor());
        if (!source.hasStyleOverride("strokeColor")) target.setStrokeColor(parent.getStrokeColor());
        if (!source.hasStyleOverride("strokeWidth")) target.setStrokeWidth(parent.getStrokeWidth());
        if (!source.hasStyleOverride("fillEnabled")) target.setFillEnabled(parent.isFillEnabled());
        if (!source.hasStyleOverride("strokeEnabled")) target.setStrokeEnabled(parent.isStrokeEnabled());
        if (!source.hasStyleOverride("borderRadius")) target.setBorderRadius(parent.getBorderRadius());
        if (!source.hasStyleOverride("paddingTop")) target.setPaddingTop(parent.getPaddingTop());
        if (!source.hasStyleOverride("paddingRight")) target.setPaddingRight(parent.getPaddingRight());
        if (!source.hasStyleOverride("paddingBottom")) target.setPaddingBottom(parent.getPaddingBottom());
        if (!source.hasStyleOverride("paddingLeft")) target.setPaddingLeft(parent.getPaddingLeft());
        if (!source.hasStyleOverride("lineSpacing")) target.setLineSpacing(parent.getLineSpacing());
        if (!source.hasStyleOverride("opacity")) target.setOpacity(parent.getOpacity());
    }

    private static void set(TemplateElement element, String property, boolean value) {
        element.setStyleOverride(property, value);
    }

    private static boolean different(double left, double right) {
        return Math.abs(left - right) > 0.0001;
    }
}
