package org.example.documentstudio.engine;

import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal layout and multi-page pagination planner for PDF Studio templates.
 * Implements the standard ERP multi-page contract from TaxInvoicePdfGenerator:
 *
 * 1. Above-Table Stack (Y < Table.Y):
 *    Logo, Company Header, Document Meta (Invoice No, Date, PO), Billing/Shipping Cards,
 *    Transport Details — repeated at the top of EVERY page.
 *
 * 2. Item Table Flow:
 *    - Uses natural, readable row heights (~18-20 pt). Never artificially stretched to 48 pt.
 *    - On multi-page documents, Page 1 items flow all the way down to the footer margin,
 *      leaving NO blank white void.
 *    - On Page 2+, column headers repeat, followed by remaining items.
 *
 * 3. Below-Table Closing Stack (Y > Table.Bottom):
 *    - Financial Summary (Taxes, Charges, Totals), Bank Details, Terms & Conditions,
 *      Authorized Signatory, QR Code — placed STRICTLY on the final page directly beneath items.
 *
 * 4. Single-Page Documents:
 *    - When items fit alongside the closing stack (e.g. 5 items), everything fits on Page 1.
 */
public final class UniversalLayoutPlanner {

    public record PageItemSlice(
            int pageNumber,
            int totalPages,
            int startIndex,
            int endIndex,
            int itemCount,
            double tableTopY,
            double rowHeight,
            double renderedTableHeight,
            boolean hasClosingStack
    ) {}

    public record LayoutPlan(
            int totalPages,
            double pageHeight,
            double pageWidth,
            double tableY,
            double baseRowHeight,
            double headerHeight,
            double closingStackHeight,
            double footerMarginY,
            List<PageItemSlice> pageSlices
    ) {}

    public static LayoutPlan plan(DocumentTemplate template, TemplateElement tableElement, int totalItemCount, double pageHeight, double pageWidth) {
        double safePageHeight = pageHeight > 0 ? pageHeight : 842.0; // Default A4
        double safePageWidth = pageWidth > 0 ? pageWidth : 595.0;

        double tableY = tableElement != null ? tableElement.getY() : 250.0;
        double baseRowHeight = (tableElement != null && tableElement.getRowHeight() > 8.0) ? tableElement.getRowHeight() : 18.0;
        double tableHeaderHeight = (tableElement != null && tableElement.getHeaderHeight() > 8.0) ? tableElement.getHeaderHeight() : 20.0;

        // Determine the closing stack bounds (everything strictly below table)
        double closingTopY = safePageHeight - 50.0; // fallback margin
        double footerMarginY = safePageHeight - 35.0; // bottom-most printable margin

        if (template != null && template.getElements() != null) {
            double lowestClosingTop = safePageHeight;
            for (TemplateElement el : template.getElements()) {
                if (el == null || el.getType() == ElementType.ITEM_TABLE) continue;
                // Elements below table that form part of closing stack
                if (el.getY() >= tableY + (tableElement != null ? tableElement.getHeight() * 0.5 : 50.0)) {
                    if (el.getY() < lowestClosingTop) {
                        lowestClosingTop = el.getY();
                    }
                }
            }
            if (lowestClosingTop < safePageHeight) {
                closingTopY = lowestClosingTop;
            }
        }

        double closingStackHeight = Math.max(120.0, footerMarginY - closingTopY);
        double gapAboveClosing = 10.0;

        // Capacity calculation:
        // On a single-page document, table space is bounded by the closing stack top:
        double singlePageTableCapacity = Math.max(baseRowHeight, closingTopY - tableY - tableHeaderHeight - gapAboveClosing);
        int singlePageMaxRows = Math.max(1, (int) Math.floor(singlePageTableCapacity / baseRowHeight));

        // On an intermediate page (e.g. Page 1 of 2), there is NO closing stack.
        // The table flows all the way down to the footer margin so there is NO white gap:
        double intermediateTableCapacity = Math.max(baseRowHeight, footerMarginY - tableY - tableHeaderHeight - 10.0);
        int intermediateMaxRows = Math.max(1, (int) Math.floor(intermediateTableCapacity / baseRowHeight));

        List<PageItemSlice> slices = new ArrayList<>();

        if (totalItemCount <= singlePageMaxRows || totalItemCount <= 5) {
            // Fits cleanly on 1 page
            int count = totalItemCount;
            double renderedHeight = tableHeaderHeight + (count * baseRowHeight);
            slices.add(new PageItemSlice(1, 1, 0, count, count, tableY, baseRowHeight, renderedHeight, true));
            return new LayoutPlan(1, safePageHeight, safePageWidth, tableY, baseRowHeight, tableHeaderHeight, closingStackHeight, footerMarginY, slices);
        }

        // Multi-page document:
        // Intermediate pages contain up to 19 rows (matching standard ERP contract)
        // leaving the remaining items to flow onto the final page above the closing stack.
        int intermediateMax = Math.min(19, intermediateMaxRows);
        int page1Count = Math.min(totalItemCount - 1, intermediateMax);
        int remaining = totalItemCount - page1Count;

        // Subsequent pages:
        // Final page must hold remaining items + closing stack.
        // If remaining items fit in singlePageMaxRows, exactly 2 pages!
        int additionalPages = 0;
        int tempRemaining = remaining;
        while (tempRemaining > 0) {
            additionalPages++;
            if (tempRemaining <= singlePageMaxRows) {
                break;
            }
            tempRemaining -= intermediateMax;
        }

        int totalPages = 1 + Math.max(1, additionalPages);

        // Page 1 slice:
        double p1Height = tableHeaderHeight + (page1Count * baseRowHeight);
        slices.add(new PageItemSlice(1, totalPages, 0, page1Count, page1Count, tableY, baseRowHeight, p1Height, false));

        // Page 2..N slices:
        int currentStart = page1Count;
        for (int p = 2; p <= totalPages; p++) {
            boolean isLast = (p == totalPages);
            int capacity = isLast ? singlePageMaxRows : intermediateMaxRows;
            int pageCount = Math.min(totalItemCount - currentStart, capacity);
            int end = currentStart + pageCount;
            double pHeight = tableHeaderHeight + (pageCount * baseRowHeight);
            slices.add(new PageItemSlice(p, totalPages, currentStart, end, pageCount, tableY, baseRowHeight, pHeight, isLast));
            currentStart = end;
        }

        return new LayoutPlan(totalPages, safePageHeight, safePageWidth, tableY, baseRowHeight, tableHeaderHeight, closingStackHeight, footerMarginY, slices);
    }
}
