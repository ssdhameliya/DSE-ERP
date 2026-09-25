package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;
import org.example.documentstudio.model.TemplateData;

import java.util.List;

/**
 * Single PDF Studio runtime-flow calculation shared by validation and rendering.
 *
 * <p>It never mutates saved template geometry.  Review/Validate/Publish use the same final-page
 * capacity that Preview/Runtime use, eliminating the previous duplicate formulas.</p>
 */
public final class PdfStudioRuntimeFlowPlanner {
    private PdfStudioRuntimeFlowPlanner() { }

    public record ItemFlowPlan(double rowHeight, double bodyTop, double finalBottom,
                               double intermediateBottom, int finalRows, int intermediateRows) {
        public int pagesFor(int itemCount) {
            if (itemCount <= 0 || itemCount <= finalRows) return 1;
            int beforeFinal = Math.max(0, itemCount - finalRows);
            return 1 + (int)Math.ceil(beforeFinal / (double)Math.max(1, intermediateRows));
        }

        public int[] rangeFor(int itemCount, int part, int pages) {
            if (itemCount <= 0) return new int[]{0,0};
            if (pages <= 1) return new int[]{0,itemCount};
            int from = 0;
            for (int p = 0; p < part && p < pages - 1; p++) {
                int remaining = itemCount - from;
                from += Math.min(intermediateRows, Math.max(0, remaining - 1));
            }
            if (part >= pages - 1) return new int[]{Math.min(from,itemCount), itemCount};
            int remaining = itemCount - from;
            int take = Math.min(intermediateRows, Math.max(0, remaining - 1));
            return new int[]{from, Math.min(itemCount, from + take)};
        }

        public TemplateElement tableForPart(TemplateElement source, int part, int pages) {
            TemplateElement table = source.copy();
            table.setRowHeight(rowHeight);
            double bottom = part == pages - 1 ? finalBottom : intermediateBottom;
            table.setHeight(Math.max(table.getHeaderHeight() + rowHeight, bottom - table.getY()));
            return table;
        }
    }


    /**
     * Authoritative runtime layout pass used by Preview/Publish/Runtime.  It resolves multiline
     * growth, follower movement, anchors, dynamic financial growth and item-table displacement
     * before item pagination is calculated.  Saved template geometry is never mutated.
     */
    public static List<TemplateElement> adjustLayout(List<TemplateElement> elements, TemplateData data, double pageHeight) throws java.io.IOException {
        return PdfStudioRenderer.computeAdjustedFlowElements(elements, data, pageHeight);
    }

    public static ItemFlowPlan plan(DocumentTemplate template, TemplateElement item, double pageHeight, int itemCount) {
        if (template == null || item == null) return null;
        return plan(template.getElements(), item, pageHeight, itemCount, template.isFlowFixedLayout());
    }

    public static ItemFlowPlan plan(List<TemplateElement> elements, TemplateElement item,
                                    double pageHeight, int itemCount, boolean flowFixed) {
        if (item == null) return null;
        double bodyTop = item.getY() + Math.max(0, item.getHeaderHeight());
        double originalBottom = item.getY() + item.getHeight();
        if (!flowFixed) {
            double body = Math.max(1, originalBottom - bodyTop);
            double row = Math.max(1, item.getRowHeight());
            int rows = Math.max(1, (int)Math.floor(body / row));
            return new ItemFlowPlan(row, bodyTop, originalBottom, originalBottom, rows, rows);
        }

        List<TemplateElement> safe = elements == null ? List.of() : elements;
        double closingTop = safe.stream()
                .filter(e -> e != null && e != item && e.isVisible())
                .filter(e -> ("LAST".equals(e.getPageRule()) || e.getType() == ElementType.CHARGE_TABLE
                        || "FINANCIAL_SUMMARY".equals(e.getFlowRole())
                        || "DYNAMIC_FINANCIAL_SUMMARY".equals(e.getReplacementGroupId())))
                .filter(e -> e.getY() > bodyTop + 1)
                .mapToDouble(TemplateElement::getY).min().orElse(originalBottom + 4.0);
        double finalBottom = Math.max(originalBottom, closingTop - 4.0);
        double finalBody = Math.max(1.0, finalBottom - bodyTop);

        double footerTop = safe.stream()
                .filter(e -> e != null && e != item && e.isVisible() && "LAST".equals(e.getPageRule())
                        && e.getY() > pageHeight * .88)
                .mapToDouble(TemplateElement::getY).min().orElse(pageHeight - 36.0);
        double intermediateBottom = Math.max(finalBottom, footerTop - 4.0);
        double intermediateBody = Math.max(1.0, intermediateBottom - bodyTop);

        double readableMin = Math.max(6.0, item.getFontSize() * 1.18);
        double baseRowHeight = Math.max(readableMin, item.getRowHeight() > 0 ? item.getRowHeight() : readableMin);
        int baseFinalRows = Math.max(1, (int)Math.floor(finalBody / baseRowHeight));
        double rowHeight = baseRowHeight;
        if (itemCount > baseFinalRows) {
            int naturalIntermediateRows = Math.max(1, (int)Math.floor(intermediateBody / baseRowHeight));
            int firstRealRows = Math.max(1, Math.min(naturalIntermediateRows, Math.max(1, itemCount - 1)));
            double expanded = intermediateBody / firstRealRows;
            rowHeight = Math.max(baseRowHeight, Math.min(48.0, expanded));
        }

        int finalRows = Math.max(1, (int)Math.floor(finalBody / rowHeight));
        int intermediateRows = Math.max(1, (int)Math.floor(intermediateBody / rowHeight));
        return new ItemFlowPlan(rowHeight, bodyTop, finalBottom, intermediateBottom, finalRows, intermediateRows);
    }
}
