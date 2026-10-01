package org.example.documentstudio.engine;

import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal block resolver that classifies template elements relative to the physical Item Table.
 *
 * Guaranteed Universal Classification:
 * - UPPER_STACK: Elements located at Y < Table.Y. These form the branding, identity,
 *   metadata, and party/address sections that must repeat on EVERY page.
 * - ITEM_TABLE: The dynamic item table grid element.
 * - CLOSING_STACK: Elements located at Y >= Table.Y + Table.Height. These form the
 *   financial totals, bank details, terms & conditions, and signatures that render
 *   STRICTLY on the final page directly below the item rows.
 * - FOOTER_STACK: Elements located at the very bottom margin (e.g. Page Numbers, Footer Notice).
 */
public final class UniversalBlockResolver {

    public enum BlockCategory {
        UPPER_STACK,
        ITEM_TABLE,
        CLOSING_STACK,
        FOOTER_STACK
    }

    public record ResolvedBlocks(
            TemplateElement tableElement,
            List<TemplateElement> upperStack,
            List<TemplateElement> closingStack,
            List<TemplateElement> footerStack,
            double tableY,
            double tableBottomY
    ) {}

    public static ResolvedBlocks resolve(DocumentTemplate template, double pageHeight) {
        double safePageHeight = pageHeight > 0 ? pageHeight : 842.0;
        TemplateElement table = null;

        if (template != null && template.getElements() != null) {
            for (TemplateElement el : template.getElements()) {
                if (el != null && el.getType() == ElementType.ITEM_TABLE) {
                    table = el;
                    break;
                }
            }
        }

        double tableY = table != null ? table.getY() : 250.0;
        double tableHeight = table != null ? table.getHeight() : 180.0;
        double tableBottomY = tableY + tableHeight;
        double footerThresholdY = safePageHeight - 35.0;

        List<TemplateElement> upper = new ArrayList<>();
        List<TemplateElement> closing = new ArrayList<>();
        List<TemplateElement> footer = new ArrayList<>();

        if (template != null && template.getElements() != null) {
            for (TemplateElement el : template.getElements()) {
                if (el == null) continue;
                if (el.getType() == ElementType.ITEM_TABLE) continue;

                double ey = el.getY();
                if (ey >= footerThresholdY) {
                    footer.add(el);
                } else if (ey < tableY) {
                    upper.add(el);
                } else {
                    closing.add(el);
                }
            }
        }

        return new ResolvedBlocks(table, upper, closing, footer, tableY, tableBottomY);
    }
}
