package org.example.documentstudio.service;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Makes selected native PDF text-show operations invisible without painting over the PDF artwork.
 *
 * <p>The original operator still executes with text rendering mode 3, so its text matrix advances
 * exactly as before and later source text keeps its original geometry. Background fills, gradients,
 * images, watermarks, borders and vector art are never touched. PDF Studio then draws only the live
 * ERP value above the protected source artwork.</p>
 */
public final class PdfSourceTextSuppressionService {
    private static final Set<String> SHOW = Set.of("Tj", "TJ", "'", "\"");
    private PdfSourceTextSuppressionService() { }

    public static Set<String> objectReplacementGroups(List<TemplateElement> elements) {
        Set<String> groups = new HashSet<>();
        if (elements == null) return groups;
        for (TemplateElement e : elements) {
            if (e == null || e.getType() == ElementType.WHITEOUT) continue;
            if ("OBJECT".equals(e.getSourceReplacementMode()) && !e.getReplacementGroupId().isBlank()) groups.add(e.getReplacementGroupId());
        }
        return groups;
    }

    public static void suppress(PDDocument document, List<TemplateElement> elements) throws IOException {
        if (document == null || elements == null || elements.isEmpty()) return;
        Set<String> groups = objectReplacementGroups(elements);
        if (groups.isEmpty()) return;
        Map<Integer,List<Target>> byPage = new HashMap<>();
        for (TemplateElement e : elements) {
            if (e == null || !groups.contains(e.getReplacementGroupId()) || e.getType() != ElementType.WHITEOUT) continue;
            Target target = Target.fromSourceKey(e.getReplacementSourceKey(), e.getX(), e.getY(), e.getWidth(), e.getHeight());
            if (target != null) byPage.computeIfAbsent(e.getPageIndex(), ignored -> new ArrayList<>()).add(target);
        }
        for (var entry : byPage.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= document.getNumberOfPages()) continue;
            suppressPage(document, document.getPage(entry.getKey()), entry.getKey(), entry.getValue());
        }
    }

    private static void suppressPage(PDDocument document, PDPage page, int pageIndex, List<Target> targets) throws IOException {
        OperatorLocator locator = new OperatorLocator(pageIndex);
        locator.setSortByPosition(false);
        locator.setStartPage(pageIndex + 1);
        locator.setEndPage(pageIndex + 1);
        locator.getText(document);
        Set<Integer> suppress = new HashSet<>();
        for (Occurrence occurrence : locator.occurrences.values()) {
            if (occurrence.text.isBlank()) continue;
            for (Target target : targets) {
                if (target.matches(occurrence)) { suppress.add(occurrence.index); break; }
            }
        }
        if (suppress.isEmpty()) return;

        List<Object> tokens;
        PDFStreamParser parser = new PDFStreamParser(page);
        try { tokens = parser.parse(); }
        finally { parser.close(); }
        List<Object> output = new ArrayList<>(tokens.size() + suppress.size() * 4);
        List<Object> operands = new ArrayList<>();
        int showIndex = -1;
        int renderMode = 0;
        for (Object token : tokens) {
            if (!(token instanceof Operator operator)) { operands.add(token); continue; }
            String name = operator.getName();
            if ("Tr".equals(name) && !operands.isEmpty() && operands.getLast() instanceof COSNumber n) renderMode = n.intValue();
            if (SHOW.contains(name)) {
                showIndex++;
                if (suppress.contains(showIndex)) {
                    output.add(COSInteger.get(3)); output.add(Operator.getOperator("Tr"));
                    output.addAll(operands); output.add(operator);
                    output.add(COSInteger.get(renderMode)); output.add(Operator.getOperator("Tr"));
                    operands.clear();
                    continue;
                }
            }
            output.addAll(operands); output.add(operator); operands.clear();
        }
        output.addAll(operands);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ContentStreamWriter(bytes).writeTokens(output);
        page.setContents(new PDStream(document, new java.io.ByteArrayInputStream(bytes.toByteArray())));
    }

    private static final class OperatorLocator extends PDFTextStripper {
        private final int pageIndex;
        private final Map<Integer,Occurrence> occurrences = new LinkedHashMap<>();
        private int showIndex = -1;
        private int current = -1;
        private OperatorLocator(int pageIndex) throws IOException { this.pageIndex = pageIndex; }
        @Override protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (SHOW.contains(operator.getName())) { current = ++showIndex; occurrences.putIfAbsent(current, new Occurrence(current)); }
            try { super.processOperator(operator, operands); } finally { current = -1; }
        }
        @Override protected void processTextPosition(TextPosition p) {
            if (current >= 0 && p != null) occurrences.get(current).add(p);
            super.processTextPosition(p);
        }
    }

    private static final class Occurrence {
        private final int index;
        private String text = "";
        private double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX, maxY;
        private Occurrence(int index) { this.index = index; }
        private void add(TextPosition p) {
            text += p.getUnicode() == null ? "" : p.getUnicode();
            double x = p.getXDirAdj(), h = Math.max(1, p.getHeightDir()), y = Math.max(0, p.getYDirAdj() - h);
            minX = Math.min(minX,x); minY = Math.min(minY,y); maxX = Math.max(maxX,x + Math.max(1,p.getWidthDirAdj())); maxY = Math.max(maxY,y+h);
        }
    }

    private record Target(int page, double x, double y, double w, double h, String normalized) {
        static Target fromSourceKey(String key,double x,double y,double w,double h) {
            if (key == null || !key.startsWith("PDF_TEXT|")) return null;
            String[] p = key.split("\\|",8);
            if (p.length < 8) return new Target(-1,x,y,w,h,"");
            try { return new Target(Integer.parseInt(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]),Double.parseDouble(p[4]),Double.parseDouble(p[5]),p[7]); }
            catch (Exception ignored) { return new Target(-1,x,y,w,h,p.length>7?p[7]:""); }
        }
        boolean matches(Occurrence o) {
            if (o.minX == Double.MAX_VALUE) return false;
            double ix = Math.max(0, Math.min(x+w,o.maxX)-Math.max(x,o.minX));
            double iy = Math.max(0, Math.min(y+h,o.maxY)-Math.max(y,o.minY));
            double overlap = ix*iy;
            double minArea = Math.max(1, Math.min(w*h,(o.maxX-o.minX)*(o.maxY-o.minY)));
            String n = PdfAutoMappingService.normalize(o.text);
            boolean textMatch = normalized == null || normalized.isBlank() || n.contains(normalized) || normalized.contains(n);
            return textMatch && overlap/minArea >= .18;
        }
    }
}
