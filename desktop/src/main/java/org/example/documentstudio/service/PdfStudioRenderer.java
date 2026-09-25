package org.example.documentstudio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.example.documentstudio.model.*;
import org.example.invoice.calculation.InvoiceTaxCalculator;
import org.example.invoice.calculation.AmountInWordsConverter;
import org.example.invoice.pdf.TaxInvoicePdfGenerator;
import org.example.invoice.model.CompanyProfile;
import org.example.invoice.model.InvoiceParty;
import org.example.invoice.model.TaxInvoiceDocument;
import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceCharge;
import org.example.invoice.model.TaxInvoiceItem;
import org.example.shared.DocumentCalculationEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Renders Document Studio templates while keeping the uploaded PDF as the
 * protected background. v8.2.2 adds map-first text fitting, source-aware masks,
 * explicit repeated-page rules and dynamic unlimited charge tables.
 */
public final class PdfStudioRenderer {
    private static final String DYNAMIC_FINANCIAL_SUMMARY_MARKER = "DYNAMIC_FINANCIAL_SUMMARY";
    /*
     * A PDFont owns COS objects that are adopted by the first PDDocument using it.
     * Reusing static PDFont instances across invoice files can leave the next PDF
     * with dangling font resources. Keep a fresh cache per rendering thread and
     * clear it at the beginning of every render invocation.
     */
    private static final ThreadLocal<EnumMap<Standard14Fonts.FontName, PDFont>> RENDER_FONTS =
            ThreadLocal.withInitial(() -> new EnumMap<>(Standard14Fonts.FontName.class));
    private static final ThreadLocal<PDDocument> CURRENT_DOCUMENT = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, PDFont>> UNICODE_FONTS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<String, byte[]>> SOURCE_EMBEDDED_FONTS = ThreadLocal.withInitial(HashMap::new);

    private PdfStudioRenderer() {}

    private static PDFont font(Standard14Fonts.FontName name) {
        return RENDER_FONTS.get().computeIfAbsent(name, PDType1Font::new);
    }

    public static Path renderPurchase(DocumentTemplate template, org.example.model.Purchase purchase, Path output) throws IOException {
        return render(template, TemplateDataFactory.fromPurchase(purchase), output);
    }

    public static Path renderSample(DocumentTemplate template, Path output) throws IOException {
        return render(template, TemplateDataFactory.sampleFor(template == null ? DocumentType.GENERAL_PDF : template.getDocumentType()), output);
    }

    public static Path render(DocumentTemplate template, TemplateData data, Path output) throws IOException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(output, "output");
        RENDER_FONTS.get().clear();
        UNICODE_FONTS.get().clear();
        SOURCE_EMBEDDED_FONTS.get().clear();
        data = enrichPartyLocationData(ErpDocumentJsonService.normalize(template.getDocumentType(), enrichPdfData(data)));
        Path source = TemplateStorageService.sourcePdf(template);
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);

        List<TemplateElement> elements = template.getElements();
        try (PDDocument sourceDoc = Loader.loadPDF(source.toFile()); PDDocument targetDoc = new PDDocument()) {
            CURRENT_DOCUMENT.set(targetDoc);
            if (sourceDoc.getNumberOfPages() == 0) throw new IOException("Template PDF has no pages.");
            captureEmbeddedFonts(sourceDoc);
            PdfSourceTextSuppressionService.suppress(sourceDoc, elements);
            Set<String> objectReplacementGroups = PdfSourceTextSuppressionService.objectReplacementGroups(elements);

            TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout = null;
            // Imported ERP PDFs use FLOW_FIXED and must keep their own geometry on every page.
            // The shared Standard Sales closing stack is only appropriate for the built-in/strict
            // renderer. Applying it to FLOW_FIXED caused Template B to inherit Template A's bank,
            // totals, terms and signature layout on continuation/final pages.
            if (template.getDocumentType() == DocumentType.SALES_INVOICE
                    && !template.isFlowFixedLayout()
                    && !"MAPPED_FIXED".equals(template.getLayoutMode())) {
                try {
                    salesLayout = TaxInvoicePdfGenerator.layoutPlan(toSalesLayoutDocument(data));
                } catch (Exception ex) {
                    throw new IOException("Unable to calculate shared Standard Sales layout plan.", ex);
                }
            }

            Map<Integer, List<TemplateElement>> runtimeElementsByPage = new HashMap<>();
            Map<Integer, FlowPlan> plans = new HashMap<>();
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                final int page = sourceIndex;
                List<TemplateElement> pageElements = elements.stream().filter(e -> PdfStyleResolver.effectivelyVisible(template,e)).filter(e -> e.getPageIndex() == page).toList();
                double runtimePageHeight=sourceDoc.getPage(sourceIndex).getMediaBox().getHeight();
                List<TemplateElement> runtime = PdfStudioRuntimeFlowPlanner.adjustLayout(pageElements, data, runtimePageHeight);
                runtimeElementsByPage.put(sourceIndex, runtime);
                plans.put(sourceIndex, FlowPlan.forPage(runtime, data, salesLayout, sourceDoc.getPage(sourceIndex).getMediaBox().getHeight(), template.isFlowFixedLayout()));
            }

            Map<Integer, List<Integer>> sourceToOutputPages = new HashMap<>();
            LayerUtility layer = new LayerUtility(targetDoc);
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                int copies = plans.get(sourceIndex).totalCopies();
                List<Integer> mapped = new ArrayList<>();
                for (int copy = 0; copy < copies; copy++) {
                    PDPage sourcePage = sourceDoc.getPage(sourceIndex);
                    PDRectangle box = sourcePage.getMediaBox();
                    PDPage targetPage = new PDPage(new PDRectangle(box.getWidth(), box.getHeight()));
                    targetPage.setRotation(sourcePage.getRotation());
                    targetDoc.addPage(targetPage);
                    mapped.add(targetDoc.getNumberOfPages() - 1);
                    PDFormXObject form = layer.importPageAsForm(sourceDoc, sourcePage);
                    try (PDPageContentStream cs = new PDPageContentStream(targetDoc, targetPage,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.saveGraphicsState();
                        cs.transform(Matrix.getTranslateInstance(-box.getLowerLeftX(), -box.getLowerLeftY()));
                        cs.drawForm(form);
                        cs.restoreGraphicsState();
                    }
                }
                sourceToOutputPages.put(sourceIndex, mapped);
            }

            int totalPages = targetDoc.getNumberOfPages();
            for (int sourceIndex = 0; sourceIndex < sourceDoc.getNumberOfPages(); sourceIndex++) {
                final int page = sourceIndex;
                List<TemplateElement> renderElements = runtimeElementsByPage.getOrDefault(sourceIndex, List.of());
                List<Integer> outputPages = sourceToOutputPages.getOrDefault(sourceIndex, List.of());
                FlowPlan plan = plans.get(sourceIndex);
                for (int part = 0; part < outputPages.size(); part++) {
                    // PDFBox Standard-14 font objects must not be reused across page resource
                    // dictionaries. Reusing one PDType1Font on multiple pages can corrupt the
                    // first page /Font references when the document is saved. Keep a tiny
                    // per-page cache instead: the page still reuses Helvetica/Bold internally,
                    // while every page owns valid font resource dictionaries.
                    RENDER_FONTS.get().clear();
                    int outputIndex = outputPages.get(part);
                    PDPage outputPage = targetDoc.getPage(outputIndex);
                    List<TaxInvoiceItem> itemChunk = plan.itemChunk(data.items(), part);
                    List<TemplateCharge> chargeChunk = plan.chargeChunk(data.charges(), part);
                    try (PDPageContentStream cs = new PDPageContentStream(targetDoc, outputPage,
                            PDPageContentStream.AppendMode.APPEND, true, true)) {
                        boolean sharedSalesLayout = template.getDocumentType() == DocumentType.SALES_INVOICE && salesLayout != null;
                        boolean replaceFlowClosing = sharedSalesLayout && plan.flowFixed() != null && plan.totalCopies() > 1;
                        boolean dynamicSalesClosing = sharedSalesLayout && (plan.flowFixed() == null || replaceFlowClosing);
                        if (replaceFlowClosing) {
                            prepareDynamicSalesPage(outputPage, cs, plan, part, salesLayout);
                        } else if (plan.flowFixed() != null) {
                            prepareFlowFixedPage(outputPage, cs, plan, part);
                        } else if (sharedSalesLayout) {
                            prepareDynamicSalesPage(outputPage, cs, plan, part, salesLayout);
                        }
                        // Replacement masks always render before live fields/tables. This prevents a later
                        // continuation-page WHITEOUT from erasing item rows that were already drawn.
                        for (TemplateElement e : renderElements) {
                            if (e.getType() == ElementType.WHITEOUT && shouldDraw(e, plan, part)
                                    && !objectReplacementGroups.contains(e.getReplacementGroupId())
                                    && !(dynamicSalesClosing && isLegacyFixedSalesClosingElement(e))) {
                                drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages, salesLayout);
                            }
                        }
                        for (TemplateElement e : renderElements) {
                            if (e.getType() == ElementType.WHITEOUT) continue;
                            if (dynamicSalesClosing && isLegacyFixedSalesClosingElement(e)) continue;
                            if (e.getType() == ElementType.ITEM_TABLE) {
                                if (plan.drawItemTable(part)) {
                                    TemplateElement liveTable = (replaceFlowClosing || (sharedSalesLayout && plan.flowFixed() == null))
                                            ? sharedSalesTableElement(e, plan, part, salesLayout)
                                            : (plan.flowFixed() != null ? plan.flowFixed().tableForPart(e, part, plan.totalCopies()) : e);
                                    drawElement(targetDoc, outputPage, cs, template, data, liveTable, itemChunk, chargeChunk, outputIndex + 1, totalPages, salesLayout);
                                }
                                continue;
                            }
                            if (e.getType() == ElementType.CHARGE_TABLE) {
                                if (plan.drawChargeTable(part)) drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages, salesLayout);
                                continue;
                            }
                            if (shouldDraw(e, plan, part)) {
                                drawElement(targetDoc, outputPage, cs, template, data, e, itemChunk, chargeChunk, outputIndex + 1, totalPages, salesLayout);
                            }
                        }
                        if (dynamicSalesClosing && part == plan.totalCopies() - 1) {
                            drawDynamicSalesClosing(targetDoc, outputPage, cs, data, salesLayout, plan);
                        }
                    }
                }
            }
            targetDoc.save(output.toFile());
        } finally {
            CURRENT_DOCUMENT.remove();
            UNICODE_FONTS.get().clear();
            SOURCE_EMBEDDED_FONTS.get().clear();
            RENDER_FONTS.get().clear();
        }
        if (Files.size(output) < 100) throw new IOException("Template renderer produced an invalid PDF.");
        return output;
    }

    private static boolean shouldDraw(TemplateElement e, FlowPlan plan, int part) {
        if (plan.flowFixed() != null && plan.itemTable() != null && part < plan.totalCopies() - 1) {
            double sourceTableBottom = plan.itemTable().getY() + plan.itemTable().getHeight() - 2.0;
            if (e.getY() >= sourceTableBottom && e.getY() < plan.flowFixed().intermediateBottom() && !"INTERMEDIATE".equals(e.getPageRule())) return false;
        }
        // Explicit page rules must also be honored for a one-page document.
        // In particular, INTERMEDIATE means "all pages except the last"; for a
        // single-page invoice there is no intermediate page.  The old early return
        // drew INTERMEDIATE whiteouts on one-page invoices and erased the complete
        // bank/totals/terms/signature closing stack.
        return switch (e.getPageRule()) {
            case "FIRST", "FIXED" -> part == 0;
            case "EVERY" -> true;
            case "CONTINUATION" -> part > 0;
            case "INTERMEDIATE" -> part < plan.totalCopies() - 1;
            case "LAST" -> part == plan.totalCopies() - 1;
            case "MULTI" -> plan.totalCopies() > 1;
            default -> plan.totalCopies() <= 1 || legacyAutoRule(e, plan.primaryTable(), part, plan.totalCopies());
        };
    }

    private static boolean legacyAutoRule(TemplateElement e, TemplateElement table, int part, int copies) {
        if (table == null || copies <= 1) return true;
        boolean above = e.getY() + e.getHeight() <= table.getY() + 2;
        boolean below = e.getY() >= table.getY() + table.getHeight() - 2;
        if (above) return true;
        if (below) return part == copies - 1;
        return part == 0;
    }

    private static int rowsPerPage(TemplateElement table) {
        double usable = Math.max(table.getRowHeight(), table.getHeight() - Math.max(0, table.getHeaderHeight()));
        return Math.max(1, (int) Math.floor(usable / table.getRowHeight()));
    }

    private static int requiredPages(TemplateElement table, int count) {
        if (table == null || count <= 0) return 1;
        return Math.max(1, (int) Math.ceil(count / (double) rowsPerPage(table)));
    }

    /**
     * Runtime-only layout plan for imported FLOW_FIXED templates. Saved template geometry is never
     * changed. Multiline fields may grow inside their semantic flow group, item capacity is reduced
     * instead of colliding with the party/financial blocks, and financial summaries may grow upward.
     */
    static List<TemplateElement> adjustedFlowElements(List<TemplateElement> source, TemplateData data, double pageHeight) throws IOException {
        return PdfStudioRuntimeFlowPlanner.adjustLayout(source, data, pageHeight);
    }

    /** Implementation used only by the central PdfStudioRuntimeFlowPlanner. */
    static List<TemplateElement> computeAdjustedFlowElements(List<TemplateElement> source, TemplateData data, double pageHeight) throws IOException {
        if (source == null || source.isEmpty()) return List.of();
        List<TemplateElement> out = source.stream().map(TemplateElement::snapshotCopy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Map<String,TemplateElement> originals = source.stream().collect(java.util.stream.Collectors.toMap(
                TemplateElement::getId, TemplateElement::snapshotCopy, (a,b)->a, LinkedHashMap::new));

        List<TemplateElement> flowText = out.stream().filter(e -> {
            boolean liveText=e.getType()==ElementType.FIELD||e.getType()==ElementType.TEXT;
            return liveText && "WRAP".equals(e.getTextFit()) && e.isAutoHeight();
        }).sorted(Comparator.comparingDouble(TemplateElement::getY)).toList();

        for (TemplateElement field : flowText) {
            String value = field.getType()==ElementType.TEXT
                    ? resolveExpression(field.getText(),data,1,1)
                    : data.value(field.getFieldKey());
            if (value == null || value.isBlank()) continue;
            PDFont f = fontFor(field);
            float size = (float)field.getFontSize();
            float width = (float)Math.max(1, field.getWidth()-field.getPaddingLeft()-field.getPaddingRight());
            List<String> lines = wrap(safePdfText(value), f, size, width);
            double needed = wrappedHeight(size,(float)field.getLineSpacing(),lines.size()) + field.getPaddingTop()+field.getPaddingBottom();
            TemplateElement original=originals.get(field.getId());
            double base=original==null?field.getHeight():original.getHeight();
            double actual=Math.max(base,needed);

            // Party/address fields are allowed to consume free space above the item table, but not
            // to grow past the page or destroy the minimum paginating table slot. When the source
            // sample was short/narrow, a real address can otherwise request hundreds of points of
            // height and fail before drawText's WRAP fitter gets a chance to reduce the font.
            double safeHeight=maxSafeFlowHeight(field,out,pageHeight);
            if(safeHeight>0)actual=Math.min(actual,safeHeight);
            actual=Math.max(1,actual);
            double delta=Math.max(0,actual-base);
            if("UP".equals(field.getGrowthDirection())&&delta>=.01)field.setY(Math.max(0,field.getY()-delta));
            field.setHeight(actual);
            if(delta<.01)continue;

            String group=field.getFlowGroupId();
            if(group!=null&&!group.isBlank()){
                double originalBottom=(original==null?field.getY():original.getY())+base;
                for(TemplateElement follower:out){
                    if(follower==field||!group.equals(follower.getFlowGroupId()))continue;
                    TemplateElement of=originals.get(follower.getId()); double oy=of==null?follower.getY():of.getY();
                    if(oy>=originalBottom-2)follower.setY(follower.getY()+delta);
                }
            }
        }

        // Dynamic financial blocks own row composition. If a template gave them too little height,
        // grow upward so the fixed footer/bottom anchor remains stable.
        for(TemplateElement financial:out){
            if(financial.getType()!=ElementType.BLOCK||!DYNAMIC_FINANCIAL_SUMMARY_MARKER.equals(financial.getReplacementGroupId()))continue;
            int normalRows=mappedFinancialRows(data).size();
            // Source-captured calculation blocks may have a physically separate Grand Total strip.
            // Reserve that strip + its source gap before deciding whether the normal dynamic rows fit.
            double minimumReadable=8.5;
            double totalReserve=financial.getSummaryTotalHeight()>0
                    ? financial.getSummaryTotalHeight()+financial.getSummaryTotalGap()
                    : minimumReadable;
            double needed=normalRows*minimumReadable+totalReserve;
            if(needed>financial.getHeight()+.01&&"UP".equals(financial.getGrowthDirection())){
                double delta=needed-financial.getHeight();financial.setY(Math.max(0,financial.getY()-delta));financial.setHeight(needed);
            }
        }

        // Resolve explicit page/block anchors after every dynamic height is known. This is the
        // central runtime-only dependency pass: saved X/Y values remain untouched, while a block
        // may follow another block (AFTER/BEFORE) or stay pinned to the page (TOP/BOTTOM).
        applyFlowAnchors(out,pageHeight);

        // Party growth consumes table space; it never overwrites the table. The paginator will use
        // the reduced runtime height and add continuation pages when required.
        for(TemplateElement table:out){
            if(table.getType()!=ElementType.ITEM_TABLE)continue;
            double top=table.getY();
            double partyBottom=out.stream().filter(e->e!=table&&e.getY()<top&&isPartyLayoutElement(e))
                    .mapToDouble(e->e.getY()+e.getHeight()).max().orElse(-1);
            if(partyBottom>=0&&partyBottom+4>table.getY()){
                double shift=partyBottom+4-table.getY();
                table.setY(table.getY()+shift);table.setHeight(Math.max(table.getHeaderHeight()+table.getRowHeight(),table.getHeight()-shift));
            }
            double nearestClosing=out.stream().filter(e->e!=table&&e.getY()>table.getY())
                    .filter(e->"FINANCIAL_SUMMARY".equals(e.getFlowRole())||DYNAMIC_FINANCIAL_SUMMARY_MARKER.equals(e.getReplacementGroupId()))
                    .mapToDouble(TemplateElement::getY).min().orElse(Double.MAX_VALUE);
            if(nearestClosing<Double.MAX_VALUE&&table.getY()+table.getHeight()>nearestClosing-4)
                table.setHeight(Math.max(table.getHeaderHeight()+table.getRowHeight(),nearestClosing-table.getY()-4));
        }
        for(TemplateElement e:out){
            if(e.getType()==ElementType.WHITEOUT||"PAGINATE".equals(e.getOverflowPolicy()))continue;
            if((e.getY() < -0.01 || e.getY()+e.getHeight() > pageHeight+0.01) && "ERROR".equals(e.getOverflowPolicy()))
                throw new IOException("PDF Studio runtime flow exceeds the page for "
                        +(e.getFieldKey().isBlank()?e.getFlowRole():e.getFieldKey())
                        +". Increase the mapped block or use a paginating region.");
        }
        return out;
    }

    /** Package-visible for regression coverage; runtime callers still enter through adjustedFlowElements. */
    static void applyFlowAnchors(List<TemplateElement> elements,double pageHeight) throws IOException {
        if(elements==null||elements.isEmpty())return;
        Map<String,TemplateElement> byId=new LinkedHashMap<>();
        for(TemplateElement e:elements)if(e!=null&&!e.getId().isBlank())byId.put(e.getId(),e);
        Set<String> resolved=new HashSet<>();
        for(TemplateElement e:elements)if(e!=null&&"ABSOLUTE".equals(e.getFlowAnchorMode()))resolved.add(e.getId());

        int remaining=elements.size()+1;
        boolean changed=true;
        while(changed&&remaining-->0){
            changed=false;
            for(TemplateElement e:elements){
                if(e==null||resolved.contains(e.getId()))continue;
                String mode=e.getFlowAnchorMode();
                switch(mode){
                    case "TOP"->{e.setY(Math.max(0,e.getFlowGap()));resolved.add(e.getId());changed=true;}
                    case "BOTTOM"->{e.setY(Math.max(0,pageHeight-e.getFlowGap()-e.getHeight()));resolved.add(e.getId());changed=true;}
                    case "AFTER","BEFORE"->{
                        TemplateElement anchor=byId.get(e.getFlowAnchorId());
                        if(anchor==null)throw new IOException("PDF Studio flow anchor is missing for "+flowName(e)+".");
                        if(!resolved.contains(anchor.getId())&&!"ABSOLUTE".equals(anchor.getFlowAnchorMode()))continue;
                        double y="AFTER".equals(mode)
                                ? anchor.getY()+anchor.getHeight()+e.getFlowGap()
                                : anchor.getY()-e.getFlowGap()-e.getHeight();
                        e.setY(Math.max(0,y));resolved.add(e.getId());changed=true;
                    }
                    default->{resolved.add(e.getId());changed=true;}
                }
            }
        }
        for(TemplateElement e:elements){
            if(e!=null&&!resolved.contains(e.getId()))
                throw new IOException("PDF Studio flow anchor cycle detected for "+flowName(e)+".");
        }
    }

    private static String flowName(TemplateElement e){
        if(e==null)return "element";
        if(!e.getFieldKey().isBlank())return e.getFieldKey();
        if(!e.getFlowRole().isBlank())return e.getFlowRole();
        return e.getType().name()+" "+e.getId().substring(0,Math.min(8,e.getId().length()));
    }

    private static boolean isPartyLayoutElement(TemplateElement e){
        if(e==null)return false;
        if(e.getFlowRole()!=null&&e.getFlowRole().startsWith("PARTY"))return true;
        String block=e.getMappingBlockType()==null?"":e.getMappingBlockType();
        return "BILLING".equals(block)||"DELIVERY".equals(block);
    }

    private static double maxSafeFlowHeight(TemplateElement field,List<TemplateElement> elements,double pageHeight){
        if(field==null)return 0;
        boolean party=isPartyLayoutElement(field);
        if(!party)return Math.max(1,pageHeight-field.getY()-4);

        double safeBottom=Math.max(field.getY()+1,pageHeight-8);
        TemplateElement table=elements.stream()
                .filter(e->e!=null&&e!=field&&e.getType()==ElementType.ITEM_TABLE&&e.getY()>field.getY())
                .min(Comparator.comparingDouble(TemplateElement::getY)).orElse(null);
        if(table!=null){
            double minimumTable=Math.max(1,table.getHeaderHeight())+Math.max(1,table.getRowHeight());
            double movable=Math.max(0,table.getHeight()-minimumTable);
            safeBottom=Math.min(safeBottom,table.getY()+movable-4);
        }
        double available=safeBottom-field.getY();
        // Never make the runtime box smaller than one readable line merely because the imported
        // source geometry is unusually tight. WRAP fitting will reduce text only when needed.
        double oneLine=Math.max(4,field.getFontSize()+field.getPaddingTop()+field.getPaddingBottom());
        return Math.max(oneLine,available);
    }


    private static void drawElement(PDDocument doc, PDPage page, PDPageContentStream cs,
                                    DocumentTemplate template, TemplateData data, TemplateElement e,
                                    List<TaxInvoiceItem> tableItems, List<TemplateCharge> tableCharges,
                                    int pageNumber, int totalPages,
                                    TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout) throws IOException {
        TemplateElement draw = PdfStyleResolver.effective(template, e);
        boolean transformable = draw.getType() != ElementType.WHITEOUT;
        if (transformable) {
            cs.saveGraphicsState();
            if (draw.getOpacity() < 0.999) {
                PDExtendedGraphicsState state = new PDExtendedGraphicsState();
                state.setNonStrokingAlphaConstant((float) draw.getOpacity());
                state.setStrokingAlphaConstant((float) draw.getOpacity());
                cs.setGraphicsStateParameters(state);
            }
            if (Math.abs(draw.getRotation()) > 0.01) {
                float centerX = (float) (draw.getX() + draw.getWidth() / 2.0);
                float centerY = toPdfY(page, draw.getY() + draw.getHeight() / 2.0);
                cs.transform(Matrix.getTranslateInstance(centerX, centerY));
                cs.transform(Matrix.getRotateInstance(Math.toRadians(draw.getRotation()), 0, 0));
                cs.transform(Matrix.getTranslateInstance(-centerX, -centerY));
            }
        }
        try {
            switch (draw.getType()) {
                case TEXT -> drawText(page, cs, draw, resolveExpression(draw.getText(), data, pageNumber, totalPages));
                case FIELD -> drawText(page, cs, draw, fieldValue(data, draw.getFieldKey(), pageNumber, totalPages));
                case IMAGE -> drawImage(doc, page, cs, draw, TemplateStorageService.resolveAsset(template, draw.getImagePath()));
                case IMAGE_FIELD -> drawImage(doc, page, cs, draw, data.image(draw.getFieldKey()));
                case RECTANGLE -> drawRectangle(page, cs, draw, false);
                case BLOCK -> {
                    if (DYNAMIC_FINANCIAL_SUMMARY_MARKER.equals(draw.getReplacementGroupId()))
                        drawMappedFinancialSummary(page, cs, draw, data);
                    else drawRectangle(page, cs, draw, false);
                }
                case WHITEOUT -> drawRectangle(page, cs, draw, true);
                case LINE -> drawLine(page, cs, draw);
                case PATH -> drawPath(page, cs, draw);
                case ITEM_TABLE -> drawItemTable(page, cs, draw, tableItems == null ? List.of() : tableItems, data.gstType(), pageNumber, totalPages,
                        salesLayout == null ? Math.max(18.0, draw.getRowHeight()) : Math.max(18.0, salesLayout.standardRowMinHeight()));
                case CHARGE_TABLE -> drawChargeTable(page, cs, draw, tableCharges == null ? List.of() : tableCharges, data.gstType());
            }
        } finally {
            if (transformable) cs.restoreGraphicsState();
        }
    }

    private static String fieldValue(TemplateData data, String key, int pageNumber, int totalPages) {
        if ("document.pageNumber".equals(key)) return Integer.toString(pageNumber);
        if ("document.totalPages".equals(key)) return Integer.toString(totalPages);
        if (key != null && key.startsWith("item.") && !data.items().isEmpty())
            return itemValue(key, data.items().getFirst(), data.gstType(), 1);
        if (key != null && key.startsWith("charge.") && !data.charges().isEmpty())
            return chargeValue(key, data.charges().getFirst(), data.gstType(), 1);
        return data.value(key);
    }

    /**
     * PDF-specific calculated values.  The PDF renderer uses the same shared invoice calculator
     * as the business-document flows but keeps these additions local to PDF Studio so Excel Studio
     * remains byte-for-byte independent of this editor redesign.
     */
    private static TemplateData enrichPdfData(TemplateData data) {
        if (data == null) return new TemplateData(Map.of(), Map.of(), List.of(), List.of(), "");
        Map<String,String> values = new LinkedHashMap<>(data.values());
        if (!data.items().isEmpty()) {
            try {
                List<TaxInvoiceCharge> charges = data.charges().stream()
                        .filter(Objects::nonNull)
                        .map(c -> new TaxInvoiceCharge(c.type(), c.amount(), c.taxable(), c.gstPercent()))
                        .toList();
                InvoiceTotals totals = InvoiceTaxCalculator.calculate(data.items(), charges, data.gstType());
                double chargeAmount = 0, chargeTax = 0, chargeTotal = 0;
                for (TemplateCharge charge : data.charges()) {
                    if (charge == null) continue;
                    DocumentCalculationEngine.ChargeResult result = DocumentCalculationEngine.charge(
                            charge.amount(), charge.taxable(), charge.gstPercent());
                    chargeAmount += result.amount();
                    chargeTax += result.taxAmount();
                    chargeTotal += result.totalAmount();
                }
                double tax = DocumentCalculationEngine.money(totals.cgst() + totals.sgst() + totals.igst());
                double preRound = DocumentCalculationEngine.money(totals.grandTotal() - totals.roundOff());
                double grossBeforeTax = DocumentCalculationEngine.money(
                        totals.basicAmount() - totals.discountAmount() + totals.chargesAmount());
                values.put("totals.basicAmount", money(totals.basicAmount()));
                values.put("totals.discountAmount", money(totals.discountAmount()));
                values.put("totals.taxableAmount", money(totals.taxableAmount()));
                values.put("totals.cgstAmount", money(totals.cgst()));
                values.put("totals.sgstAmount", money(totals.sgst()));
                values.put("totals.igstAmount", money(totals.igst()));
                values.put("totals.gstAmount", money(tax));
                values.put("totals.chargesAmount", money(DocumentCalculationEngine.money(chargeAmount)));
                values.put("totals.chargeTaxAmount", money(DocumentCalculationEngine.money(chargeTax)));
                values.put("totals.chargesTotal", money(DocumentCalculationEngine.money(chargeTotal)));
                values.put("totals.grossBeforeTax", money(grossBeforeTax));
                values.put("totals.preRoundTotal", money(preRound));
                values.put("totals.roundOff", money(totals.roundOff()));
                values.put("totals.roundedGrandTotal", money(totals.grandTotal()));
            } catch (Exception ignored) {
                // Keep the original ERP values if a legacy/non-invoice TemplateData cannot be recalculated.
            }
        }
        return new TemplateData(values, data.images(), data.items(), data.charges(), data.gstType());
    }


    /** Derived canonical party location fields used by imported invoice artwork. */
    private static TemplateData enrichPartyLocationData(TemplateData data) {
        if (data == null) return new TemplateData(Map.of(), Map.of(), List.of(), List.of(), "");
        Map<String,String> values = new LinkedHashMap<>(data.values());
        String gstin = firstNonBlank(values.get("party.billingGstin"), values.get("party.gstin"),
                values.get("sales.billingGstin"), values.get("sales.gstin"));
        if (blank(values.get("party.stateCode")) && gstin != null) {
            String compact = gstin.replaceAll("\\s+", "");
            if (compact.length() >= 2 && Character.isDigit(compact.charAt(0)) && Character.isDigit(compact.charAt(1)))
                values.put("party.stateCode", compact.substring(0, 2));
        }
        if (blank(values.get("party.placeOfSupply"))) {
            String address = firstNonBlank(values.get("party.billingAddress"), values.get("party.deliveryAddress"),
                    values.get("sales.billingAddress"), values.get("sales.deliveryAddress"));
            String state = stateFromAddress(address);
            if (!blank(state)) values.put("party.placeOfSupply", state);
        }
        return new TemplateData(values, data.images(), data.items(), data.charges(), data.gstType());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private static String stateFromAddress(String address) {
        if (address == null || address.isBlank()) return "";
        String text = address.replace('\n',' ').replaceAll("\\s+", " ").trim();
        String[] hyphen = text.split("\\s+-\\s+");
        if (hyphen.length > 1) {
            String last = hyphen[hyphen.length - 1].replaceAll("^[0-9\\s-]+", "").trim();
            if (!last.isBlank() && last.length() <= 40) return last;
        }
        String[] comma = text.split(",");
        if (comma.length > 1) {
            String last = comma[comma.length - 1].replaceAll("[0-9-]", "").trim();
            if (!last.isBlank() && last.length() <= 40) return last;
        }
        return "";
    }

    /** Resolve literal text mixed with {{erp.field}} expressions. */
    private static String resolveExpression(String text, TemplateData data, int pageNumber, int totalPages) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}")
                .matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = fieldValue(data, matcher.group(1), pageNumber, totalPages);
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void drawText(PDPage page, PDPageContentStream cs, TemplateElement e, String value) throws IOException {
        String text = safePdfText(value);
        if (e.isFillEnabled() || e.isStrokeEnabled()) drawRectangle(page, cs, e, false);
        if (text.isBlank()) return;
        PDFont font = fontFor(e);
        float configuredSize = (float) e.getFontSize();
        float x = (float) (e.getX() + e.getPaddingLeft());
        float topY = (float) (e.getY() + e.getPaddingTop());
        float width = (float) Math.max(1, e.getWidth() - e.getPaddingLeft() - e.getPaddingRight());
        float height = (float) Math.max(1, e.getHeight() - e.getPaddingTop() - e.getPaddingBottom());
        float top = toPdfY(page, topY);
        String mode = e.getTextFit();

        if ("SHRINK".equals(mode) && !text.contains("\n")) {
            float size = shrinkToFit(text, font, configuredSize, width, height);
            drawSingleLine(cs, font, size, text, x, top - size, width, e.getTextAlignment(), e.getTextColor());
            return;
        }
        if ("CLIP".equals(mode)) {
            cs.saveGraphicsState();
            try {
                cs.addRect(x, toPdfY(page, topY + height), width, height);
                cs.clip();
                drawSingleLine(cs, font, configuredSize, text.replace('\n', ' '), x, top - configuredSize, width, e.getTextAlignment(), e.getTextColor());
            } finally { cs.restoreGraphicsState(); }
            return;
        }
        if ("FIXED".equals(mode)) {
            drawSingleLine(cs, font, configuredSize, text.replace('\n', ' '), x, top - configuredSize, width, e.getTextAlignment(), e.getTextColor());
            return;
        }

        // WRAP is flow-aware rather than a silent clip. Imported ERP PDFs often expose
        // compact AcroForm rectangles that fit sample addresses but not real customer or
        // supplier data. Keep the field geometry/template artwork unchanged, first tighten
        // line leading, then reduce the font only as much as required to show every wrapped
        // line. This is generic for every template/document type; no template id/name checks.
        WrappedTextFit fitted = fitWrappedText(text, font, configuredSize, width, height, e.getLineSpacing());
        float textSize = fitted.fontSize();
        float lineHeight = fitted.lineHeight();
        List<String> lines = fitted.lines();
        setNonStroke(cs, e.getTextColor());
        float y = top - textSize;
        float bottom = top - height;
        for (String line : lines) {
            if (y < bottom - 0.01f) break;
            float drawX = alignedX(font, textSize, line, x, width, e.getTextAlignment());
            cs.beginText(); cs.setFont(font, textSize); cs.newLineAtOffset(drawX, y); cs.showText(line); cs.endText();
            y -= lineHeight;
        }
    }

    private record WrappedTextFit(float fontSize, float lineHeight, List<String> lines) { }

    private static WrappedTextFit fitWrappedText(String text, PDFont font, float configured,
                                                  float width, float height, double configuredSpacing) throws IOException {
        float originalSize = Math.max(1f, configured);
        float originalSpacing = (float)Math.max(.5, configuredSpacing);
        List<String> originalLines = wrap(text, font, originalSize, width);
        if (wrappedHeight(originalSize, originalSpacing, originalLines.size()) <= height + .01f)
            return new WrappedTextFit(originalSize, originalSize * originalSpacing, originalLines);

        // Compact leading before sacrificing readable font size. 0.92 still keeps separate
        // baselines for small invoice metadata while recovering space from imported form boxes.
        float compactSpacing = Math.min(originalSpacing, .92f);
        if (wrappedHeight(originalSize, compactSpacing, originalLines.size()) <= height + .01f)
            return new WrappedTextFit(originalSize, originalSize * compactSpacing, originalLines);

        // Never collapse text to an unreadable speck. Imported templates may already use a
        // small font, so preserve at least 4pt when possible, or 82% of an already-smaller size.
        float minimumSize = originalSize >= 4f ? 4f : Math.max(3.4f, originalSize * .82f);
        for (float size = originalSize - .1f; size >= minimumSize - .001f; size -= .1f) {
            List<String> lines = wrap(text, font, size, width);
            float spacing = compactSpacing;
            if (wrappedHeight(size, spacing, lines.size()) <= height + .01f)
                return new WrappedTextFit(size, size * spacing, lines);
        }
        List<String> minimumLines = wrap(text, font, minimumSize, width);
        throw new IOException("Mapped text does not fit inside its PDF Studio box at the minimum readable size; text was not clipped: " + abbreviateForError(text));
    }

    private static float wrappedHeight(float size, float spacing, int lineCount) {
        if (lineCount <= 0) return 0f;
        return size + Math.max(0, lineCount - 1) * size * spacing;
    }

    private static float shrinkToFit(String text, PDFont font, float configured, float width, float height) throws IOException {
        float size = Math.max(5f, configured);
        float maxByHeight = Math.max(5f, height * .88f);
        size = Math.min(size, maxByHeight);
        float natural = textWidth(font, size, text);
        if (natural > width && natural > 0) size = Math.max(5f, size * width / natural);
        return size;
    }

    private static void drawSingleLine(PDPageContentStream cs, PDFont font, float size, String text,
                                       float x, float y, float width, String alignment, String color) throws IOException {
        setNonStroke(cs, color);
        float drawX = alignedX(font, size, text, x, width, alignment);
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(drawX, y); cs.showText(text); cs.endText();
    }

    private static float alignedX(PDFont font, float size, String text, float x, float width, String alignment) throws IOException {
        float tw = textWidth(font, size, text);
        if ("RIGHT".equals(alignment)) return x + Math.max(0, width - tw);
        if ("CENTER".equals(alignment)) return x + Math.max(0, (width - tw) / 2f);
        return x;
    }

    private static float textWidth(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(text == null ? "" : text) / 1000f * size;
    }

    private static PDFont fontFor(TemplateElement e) {
        String family=e.getFontFamily();boolean bold=e.isBold(),italic=e.isItalic();
        if("TIMES".equals(family))return font(bold&&italic?Standard14Fonts.FontName.TIMES_BOLD_ITALIC:bold?Standard14Fonts.FontName.TIMES_BOLD:italic?Standard14Fonts.FontName.TIMES_ITALIC:Standard14Fonts.FontName.TIMES_ROMAN);
        if("COURIER".equals(family))return font(bold&&italic?Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE:bold?Standard14Fonts.FontName.COURIER_BOLD:italic?Standard14Fonts.FontName.COURIER_OBLIQUE:Standard14Fonts.FontName.COURIER);
        if(!"HELVETICA".equals(family)){
            try{PDFont embedded=embeddedSourceFont(family,bold,italic);if(embedded!=null)return embedded;}catch(Exception ignored){}
            try{PDFont custom=systemFont(family,bold,italic);if(custom!=null)return custom;}catch(Exception ignored){}
        }
        return font(bold&&italic?Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE:bold?Standard14Fonts.FontName.HELVETICA_BOLD:italic?Standard14Fonts.FontName.HELVETICA_OBLIQUE:Standard14Fonts.FontName.HELVETICA);
    }

    private static void captureEmbeddedFonts(PDDocument sourceDoc){
        Map<String,byte[]> out=SOURCE_EMBEDDED_FONTS.get();
        for(PDPage page:sourceDoc.getPages()){
            PDResources resources=page.getResources();if(resources==null)continue;
            for(var name:resources.getFontNames()){
                try{
                    PDFont font=resources.getFont(name);if(font==null||font.getFontDescriptor()==null)continue;
                    PDStream stream=font.getFontDescriptor().getFontFile2();
                    if(stream==null)stream=font.getFontDescriptor().getFontFile3();
                    if(stream==null)continue;
                    String raw=font.getName();String token=fontToken(raw);if(token.isBlank())continue;
                    out.putIfAbsent(token,stream.toByteArray());
                }catch(Exception ignored){}
            }
        }
    }

    private static PDFont embeddedSourceFont(String family,boolean bold,boolean italic) throws IOException{
        PDDocument doc=CURRENT_DOCUMENT.get();if(doc==null)return null;
        String wanted=fontToken(family);if(wanted.isBlank())return null;
        byte[] bytes=SOURCE_EMBEDDED_FONTS.get().get(wanted);
        if(bytes==null){
            for(var entry:SOURCE_EMBEDDED_FONTS.get().entrySet()){
                if(entry.getKey().contains(wanted)||wanted.contains(entry.getKey())){bytes=entry.getValue();break;}
            }
        }
        if(bytes==null)return null;
        String key="embedded-source-font|"+wanted+"|"+bold+"|"+italic;
        PDFont cached=UNICODE_FONTS.get().get(key);if(cached!=null)return cached;
        try{
            PDFont loaded=PDType0Font.load(doc,new java.io.ByteArrayInputStream(bytes),true);
            UNICODE_FONTS.get().put(key,loaded);return loaded;
        }catch(Exception ignored){return null;}
    }

    private static PDFont systemFont(String family,boolean bold,boolean italic) throws IOException {
        PDDocument doc=CURRENT_DOCUMENT.get();if(doc==null)return null;
        String key="source-font|"+family+"|"+bold+"|"+italic;PDFont cached=UNICODE_FONTS.get().get(key);if(cached!=null)return cached;
        Path path=findSystemFont(family,bold,italic);if(path==null)return null;
        PDFont loaded=PDType0Font.load(doc,path.toFile());UNICODE_FONTS.get().put(key,loaded);return loaded;
    }

    private static Path findSystemFont(String family,boolean bold,boolean italic){
        String wanted=fontToken(family);if(wanted.isBlank())return null;
        List<Path> roots=List.of(Path.of("C:/Windows/Fonts"),Path.of("/usr/share/fonts"),Path.of("/usr/local/share/fonts"),
                Path.of(System.getProperty("user.home","."),".fonts"),Path.of("/System/Library/Fonts"),Path.of("/Library/Fonts"));
        Path best=null;int bestScore=Integer.MIN_VALUE;
        for(Path root:roots){if(!Files.isDirectory(root))continue;try(var stream=Files.walk(root,5)){
            for(Path candidate:stream.filter(Files::isRegularFile).filter(f->{String n=f.getFileName().toString().toLowerCase(Locale.ROOT);return n.endsWith(".ttf")||n.endsWith(".otf");}).toList()){
                String name=fontToken(candidate.getFileName().toString());if(!name.contains(wanted)&&!wanted.contains(name))continue;
                String lower=candidate.getFileName().toString().toLowerCase(Locale.ROOT);int score=100-Math.abs(name.length()-wanted.length());
                boolean cb=lower.contains("bold")||lower.contains("semibold")||lower.contains("demi")||lower.contains("black");
                boolean ci=lower.contains("italic")||lower.contains("oblique");if(cb==bold)score+=20;if(ci==italic)score+=20;
                if(score>bestScore){bestScore=score;best=candidate;}
            }
        }catch(Exception ignored){}}
        return best;
    }

    private static String fontToken(String value){
        if(value==null)return"";String n=value.toUpperCase(Locale.ROOT).replaceFirst("^[A-Z]{6}\\+","");
        return n.replaceAll("(?i)(BOLD|SEMIBOLD|DEMI|BLACK|ITALIC|OBLIQUE|REGULAR|ROMAN|PSMT|MT)","").replaceAll("[^A-Z0-9]","");
    }

    private static PDFont fontForVariant(TemplateElement base, boolean bold) {
        TemplateElement copy=base.snapshotCopy();copy.setBold(bold);return fontFor(copy);
    }

    private static void drawRectangle(PDPage page, PDPageContentStream cs, TemplateElement e, boolean replacementMask) throws IOException {
        float x = (float) e.getX();
        float y = toPdfY(page, e.getY() + e.getHeight());
        float w = (float) e.getWidth();
        float h = (float) e.getHeight();
        boolean fill = replacementMask || e.isFillEnabled();
        boolean stroke = !replacementMask && e.isStrokeEnabled() && e.getStrokeWidth() > 0;
        if (!fill && !stroke) return;
        if (fill) setNonStroke(cs, e.getFillColor());
        if (stroke) { setStroke(cs, e.getStrokeColor()); cs.setLineWidth((float)e.getStrokeWidth()); }
        float radius = (float)Math.min(Math.max(0, e.getBorderRadius()), Math.min(w,h)/2f);
        if (radius > .1f) roundedRect(cs, x, y, w, h, radius);
        else cs.addRect(x,y,w,h);
        if (fill && stroke) cs.fillAndStroke();
        else if (fill) cs.fill();
        else cs.stroke();
    }

    private static void roundedRect(PDPageContentStream cs, float x, float y, float w, float h, float r) throws IOException {
        float k = .55228475f, c = r * k;
        cs.moveTo(x+r,y);
        cs.lineTo(x+w-r,y); cs.curveTo(x+w-r+c,y,x+w,y+r-c,x+w,y+r);
        cs.lineTo(x+w,y+h-r); cs.curveTo(x+w,y+h-r+c,x+w-r+c,y+h,x+w-r,y+h);
        cs.lineTo(x+r,y+h); cs.curveTo(x+r-c,y+h,x,y+h-r+c,x,y+h-r);
        cs.lineTo(x,y+r); cs.curveTo(x,y+r-c,x+r-c,y,x+r,y);
        cs.closePath();
    }

    private static void drawLine(PDPage page, PDPageContentStream cs, TemplateElement e) throws IOException {
        if (!e.isStrokeEnabled()) return;
        setStroke(cs, e.getStrokeColor());
        cs.setLineWidth((float) Math.max(0.5, e.getStrokeWidth()));
        float x1 = (float) e.getX(), y1 = toPdfY(page, e.getY());
        float x2 = (float) (e.getX() + (e.getWidth() <= 1.001 ? 0 : e.getWidth()));
        float y2 = toPdfY(page, e.getY() + (e.getHeight() <= 1.001 ? 0 : e.getHeight()));
        cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }

    private static void drawPath(PDPage page, PDPageContentStream cs, TemplateElement e) throws IOException {
        if (e.getPathCommands() == null || e.getPathCommands().isEmpty()) return;
        for (PathCommand command : e.getPathCommands()) {
            switch (command.getType()) {
                case "M" -> cs.moveTo(pathX(e, command.getX1()), pathY(page, e, command.getY1()));
                case "L" -> cs.lineTo(pathX(e, command.getX1()), pathY(page, e, command.getY1()));
                case "C" -> cs.curveTo(pathX(e, command.getX1()), pathY(page, e, command.getY1()),
                        pathX(e, command.getX2()), pathY(page, e, command.getY2()),
                        pathX(e, command.getX3()), pathY(page, e, command.getY3()));
                case "Z" -> cs.closePath();
                default -> { }
            }
        }
        boolean fill = e.isFillEnabled() && e.isPathFilled();
        boolean stroke = e.isStrokeEnabled() && e.isPathStroked();
        if (fill) setNonStroke(cs, e.getFillColor());
        if (stroke) { setStroke(cs, e.getStrokeColor()); cs.setLineWidth((float) Math.max(.5, e.getStrokeWidth())); }
        if (fill && stroke) cs.fillAndStroke();
        else if (fill) cs.fill();
        else if (stroke) cs.stroke();
    }

    private static float pathX(TemplateElement e, double normalized) { return (float) (e.getX() + normalized * e.getWidth()); }
    private static float pathY(PDPage page, TemplateElement e, double normalized) { return toPdfY(page, e.getY() + normalized * e.getHeight()); }

    private static void drawImage(PDDocument doc, PDPage page, PDPageContentStream cs, TemplateElement e, Path imagePath) throws IOException {
        if (e.isFillEnabled() || e.isStrokeEnabled()) drawRectangle(page, cs, e, false);
        if (imagePath == null || !Files.isRegularFile(imagePath)) return;
        PDImageXObject image = PDImageXObject.createFromFileByContent(imagePath.toFile(), doc);
        float boxX = (float) (e.getX() + e.getPaddingLeft());
        float boxTop = (float) (e.getY() + e.getPaddingTop());
        float boxW = (float) Math.max(1, e.getWidth() - e.getPaddingLeft() - e.getPaddingRight());
        float boxH = (float) Math.max(1, e.getHeight() - e.getPaddingTop() - e.getPaddingBottom());
        float boxY = toPdfY(page, boxTop + boxH);
        float drawW = boxW, drawH = boxH;
        String fit = e.getImageFit();
        if (!"STRETCH".equals(fit) || e.isPreserveAspectRatio()) {
            float iw = Math.max(1, image.getWidth()), ih = Math.max(1, image.getHeight());
            float sx = boxW / iw, sy = boxH / ih;
            float factor = "FILL".equals(fit) ? Math.max(sx, sy) : Math.min(sx, sy);
            drawW = iw * factor; drawH = ih * factor;
        }
        float drawX = boxX + (boxW - drawW) / 2f, drawY = boxY + (boxH - drawH) / 2f;
        cs.saveGraphicsState();
        try {
            if ("FILL".equals(fit)) { cs.addRect(boxX, boxY, boxW, boxH); cs.clip(); }
            cs.drawImage(image, drawX, drawY, drawW, drawH);
        } finally { cs.restoreGraphicsState(); }
    }

    private static void drawItemTable(PDPage page, PDPageContentStream cs, TemplateElement e,
                                      List<TaxInvoiceItem> items, String gstType, int pageNumber, int totalPages,
                                      double fillerRowHeight) throws IOException {
        List<Column> columns = itemColumns(e);
        if (columns.isEmpty()) columns = itemColumns(List.of("serial", "descriptionWithRemarks", "quantity", "rate", "total"));

        TemplateElement effective = e;
        int count = items == null ? 0 : items.size();
        if (e.isUseSourceTableDesign()) {
            effective = e.copy();
            // Fixed-top / flow-table / fixed-bottom templates must preserve the exact
            // mapped row rhythm on the final (or only) page. Using the legacy 18pt
            // fallback here made compact imported templates draw too few blank rows
            // (for example a one-line invoice could leave most of its 5-row table area
            // visually empty). The live table geometry already carries the central
            // paginator's row height, so filler rows must use that same value.
            double sourceFillerRowHeight = effective.getRowHeight() > 0
                    ? effective.getRowHeight()
                    : fillerRowHeight;
            rebuildSourceSalesGridDynamic(page, cs, effective, count, pageNumber == totalPages, sourceFillerRowHeight);
        }

        drawTableScaffold(page, cs, effective, columns);
        for (int r = 0; r < count; r++) drawItemRow(page, cs, effective, columns, r, items.get(r), gstType);
    }

    private static void rebuildSourceSalesGrid(PDPage page, PDPageContentStream cs, TemplateElement e, double bottomTopLeftY) throws IOException {
        float x = (float)e.getX();
        float width = (float)e.getWidth();
        float bodyTopY = (float)(e.getY() + e.getHeaderHeight());
        float bottomY = (float)bottomTopLeftY;
        float pdfBottom = toPdfY(page, bottomY);
        float pdfTop = toPdfY(page, bodyTopY);

        boolean captured=e.isSourceStyleCaptured();
        if(!captured||e.isSourceMaskSafe()){
            setNonStroke(cs,captured?e.getFillColor():"#FFFFFF");
            cs.addRect(x + 0.7f, pdfBottom + 0.7f, width - 1.4f, Math.max(1f, pdfTop - pdfBottom - 1.4f));
            cs.fill();
        }
        boolean drawGrid=!captured||e.isStrokeEnabled();
        if(drawGrid){
            setStroke(cs,captured?e.getStrokeColor():"#7FA4D3");
            cs.setLineWidth((float)(captured?Math.max(.25,e.getStrokeWidth()):.45));
            List<Float> widths = columnWidths(e, itemColumns(e), width);
            float cursor = x;
            cs.moveTo(x, pdfTop); cs.lineTo(x + width, pdfTop); cs.stroke();
            for (float w : widths) { cs.moveTo(cursor, pdfTop); cs.lineTo(cursor, pdfBottom); cs.stroke(); cursor += w; }
            cs.moveTo(x + width, pdfTop); cs.lineTo(x + width, pdfBottom); cs.stroke();
            double rh = e.getRowHeight();
            for (double y = bodyTopY; y < bottomY - 0.5; y += rh) {
                float py = toPdfY(page, Math.min(bottomY, y)); cs.moveTo(x, py); cs.lineTo(x + width, py); cs.stroke();
            }
            cs.moveTo(x, pdfBottom); cs.lineTo(x + width, pdfBottom); cs.stroke();
        }
    }


    private static void rebuildSourceSalesGridDynamic(PDPage page, PDPageContentStream cs, TemplateElement e,
                                                      int realRows, boolean finalPage, double fillerRowHeight) throws IOException {
        float x = (float)e.getX();
        float width = (float)e.getWidth();
        double bodyTop = e.getY() + e.getHeaderHeight();
        double cursorY = bodyTop;
        List<Double> lines = new ArrayList<>();
        lines.add(bodyTop);
        for (int i = 0; i < realRows; i++) {
            cursorY += e.getRowHeight();
            lines.add(cursorY);
        }
        double targetBottom = e.getY() + e.getHeight();
        // Fixed-zone contract: blank rows belong only to the final (or only) page.
        // Intermediate pages must never draw empty grid rows while real item rows remain
        // for a later page. The source closing artwork is already cleared separately.
        if (finalPage) {
            while (cursorY + fillerRowHeight <= targetBottom + 0.6) {
                cursorY += fillerRowHeight;
                lines.add(cursorY);
            }
        }
        float pdfTop = toPdfY(page, bodyTop);
        float pdfBottom = toPdfY(page, cursorY);

        boolean captured=e.isSourceStyleCaptured();
        if(!captured||e.isSourceMaskSafe()){
            setNonStroke(cs,captured?e.getFillColor():"#FFFFFF");
            cs.addRect(x + 0.7f, pdfBottom + 0.7f, width - 1.4f, Math.max(1f, pdfTop - pdfBottom - 1.4f));
            cs.fill();
        }
        boolean drawGrid=!captured||e.isStrokeEnabled();
        if(drawGrid){
            setStroke(cs,captured?e.getStrokeColor():"#7FA4D3");
            cs.setLineWidth((float)(captured?Math.max(.25,e.getStrokeWidth()):.45));
            List<Float> widths = columnWidths(e, itemColumns(e), width);
            float colX = x;
            for (float w : widths) { cs.moveTo(colX, pdfTop); cs.lineTo(colX, pdfBottom); cs.stroke(); colX += w; }
            cs.moveTo(x + width, pdfTop); cs.lineTo(x + width, pdfBottom); cs.stroke();
            for (double y : lines) { float py = toPdfY(page, y); cs.moveTo(x, py); cs.lineTo(x + width, py); cs.stroke(); }
        }
    }

    private static void drawChargeTable(PDPage page, PDPageContentStream cs, TemplateElement e,
                                        List<TemplateCharge> charges, String gstType) throws IOException {
        List<Column> columns = chargeColumns(e.getTableColumns());
        if (columns.isEmpty()) columns = chargeColumns(List.of("type", "amount", "gstPercent", "taxAmount", "total"));
        drawTableScaffold(page, cs, e, columns);
        int count = Math.min(rowsPerPage(e), charges.size());
        for (int r = 0; r < count; r++) drawChargeRow(page, cs, e, columns, r, charges.get(r), gstType);
    }

    private static void drawTableScaffold(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns) throws IOException {
        if (e.isUseSourceTableDesign()) return;
        float x = (float) e.getX(), top = toPdfY(page, e.getY()), width = (float) e.getWidth();
        float headerH = (float) e.getHeaderHeight();
        List<Float> widths = columnWidths(e, columns, width);
        setNonStroke(cs, "#EEF4FF"); cs.addRect(x, top - headerH, width, headerH); cs.fill();
        setStroke(cs, "#9FB3C8"); cs.setLineWidth(0.65f); cs.addRect(x, top - (float)e.getHeight(), width, (float)e.getHeight()); cs.stroke();
        float cursorX = x;
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            float cw = widths.get(i);
            drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.4f, column.label(), cursorX + 3, top - headerH + 7, cw - 6, Math.max(5, headerH - 5), "#24364B");
            cursorX += cw;
            if (cursorX < x + width - .5f) { cs.moveTo(cursorX, top); cs.lineTo(cursorX, top - (float)e.getHeight()); cs.stroke(); }
        }
        cs.moveTo(x, top - headerH); cs.lineTo(x + width, top - headerH); cs.stroke();
    }

    private static void drawItemRow(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns,
                                    int row, TaxInvoiceItem item, String gstType) throws IOException {
        drawDataRow(page, cs, e, columns, row, key -> itemValue(key, item, gstType, item.getSerialNo() > 0 ? item.getSerialNo() : row + 1));
    }

    private static void drawChargeRow(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns,
                                      int row, TemplateCharge charge, String gstType) throws IOException {
        drawDataRow(page, cs, e, columns, row, key -> chargeValue(key, charge, gstType, row + 1));
    }

    private static void drawDataRow(PDPage page, PDPageContentStream cs, TemplateElement e, List<Column> columns,
                                    int row, java.util.function.Function<String,String> value) throws IOException {
        float x = (float)e.getX(), top = toPdfY(page, e.getY()), width = (float)e.getWidth();
        float headerH = (float)Math.max(0, e.getHeaderHeight()), rowH = (float)e.getRowHeight();
        float rowTop = top - headerH - row * rowH, rowBottom = rowTop - rowH;
        List<Float> widths = columnWidths(e, columns, width);
        float cursorX = x;
        if (!e.isUseSourceTableDesign()) {
            setStroke(cs, "#9FB3C8"); cs.setLineWidth(.65f); cs.moveTo(x, rowBottom); cs.lineTo(x + width, rowBottom); cs.stroke();
        }
        PDFont font = fontFor(e);
        float fontSize = (float)Math.max(5, Math.min(e.getFontSize(), rowH * .58));
        List<String> alignments = columnAlignments(e, columns.size());
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            float cw = widths.get(i);
            String alignment = alignments != null && i < alignments.size() ? alignments.get(i) : "LEFT";
            drawCellTextAligned(cs, font, fontSize, value.apply(column.key()), cursorX + 3, rowBottom + 2,
                    Math.max(4, cw - 6), Math.max(4, rowH - 3), e.getTextColor(), alignment);
            cursorX += cw;
        }
    }

    private static float totalWeight(List<Column> columns) { return (float)columns.stream().mapToDouble(Column::weight).sum(); }

    /** Exact source-header geometry wins; legacy templates keep tableColumnWidths/semantic weights. */
    private static List<Float> columnWidths(TemplateElement element, List<Column> columns, float totalWidth) {
        // Source-designed tables may acquire review-only bindings during import/upgrade. The physical
        // body grid must not move merely because richer metadata was added. The persisted legacy width
        // vector is the renderer's proven geometry mirror for source tables and is kept synchronized
        // whenever the user intentionally edits current bindings.
        List<Double> exact = element == null ? List.of() : element.getTableColumnWidths();
        if (element != null && element.isUseSourceTableDesign() && exact != null && exact.size() == columns.size()
                && exact.stream().allMatch(v -> v != null && v > 0)) {
            double supplied = exact.stream().mapToDouble(Double::doubleValue).sum();
            if (supplied > 0) {
                double scale = totalWidth / supplied;
                return exact.stream().map(v -> (float)(v * scale)).toList();
            }
        }
        if(element!=null&&!element.getTableColumnBindings().isEmpty()&&element.getTableColumnBindings().size()==columns.size()){
            List<Double> bound=element.getTableColumnBindings().stream().map(TemplateColumnBinding::getWidth).toList();
            if(bound.stream().allMatch(v->v!=null&&v>0)){
                double sum=bound.stream().mapToDouble(Double::doubleValue).sum();
                if(sum>0){double scale=totalWidth/sum;return bound.stream().map(v->(float)(v*scale)).toList();}
            }
        }
        if (exact != null && exact.size() == columns.size() && exact.stream().allMatch(v -> v != null && v > 0)) {
            double supplied = exact.stream().mapToDouble(Double::doubleValue).sum();
            if (supplied > 0) {
                double scale = totalWidth / supplied;
                return exact.stream().map(v -> (float)(v * scale)).toList();
            }
        }
        float totalWeight = Math.max(.001f,totalWeight(columns));
        return columns.stream().map(column -> totalWidth * (float)column.weight() / totalWeight).toList();
    }

    private static List<String> columnAlignments(TemplateElement element,int count){
        if(element!=null&&!element.getTableColumnBindings().isEmpty()&&element.getTableColumnBindings().size()==count)
            return element.getTableColumnBindings().stream().map(TemplateColumnBinding::getAlignment).toList();
        return element==null?List.of():element.getTableColumnAlignments();
    }

    private static String itemValue(String key, TaxInvoiceItem item, String gstType, int serial) {
        if (item == null) return "";
        String k = key == null ? "" : key.replaceFirst("^item\\.", "");
        // Backward-compatible column aliases from legacy PDF templates.
        k = switch (k) { case "qty" -> "quantity"; case "discount" -> "discountPercent"; case "gst" -> "gstPercent"; case "amount" -> "total"; default -> k; };
        DocumentCalculationEngine.LineResult result = DocumentCalculationEngine.line(
                item.getQuantity(), item.getRate(), item.getDiscountPercent(), item.getGstPercent());
        TaxSplit split = taxSplit(item.getGstPercent(), result.taxAmount(), gstType);
        return switch (k) {
            case "serial" -> Integer.toString(serial);
            case "code" -> safe(item.getItemCode());
            case "hsn" -> safe(item.getHsn());
            case "description" -> safe(item.getDescription());
            case "descriptionWithRemarks" -> descriptionWithRemarks(item.getDescription(), item.getRemarks());
            case "remarks" -> safe(item.getRemarks());
            case "category" -> safe(item.getCategory());
            case "brand" -> safe(item.getBrand());
            case "material" -> safe(item.getMaterial());
            case "size" -> safe(item.getSize());
            case "quantity" -> quantityNumber(item.getQuantity());
            case "unit" -> safe(item.getUnit());
            case "rate" -> money(item.getRate());
            case "discountPercent" -> number(item.getDiscountPercent());
            case "discountAmount" -> money(result.discountAmount());
            case "grossAmount" -> money(result.grossAmount());
            case "taxable" -> money(result.taxableAmount());
            case "gstPercent" -> number(item.getGstPercent());
            case "gstAmount" -> money(result.taxAmount());
            case "cgstPercent" -> number(split.cgstPercent());
            case "cgstAmount" -> money(split.cgstAmount());
            case "sgstPercent" -> number(split.sgstPercent());
            case "sgstAmount" -> money(split.sgstAmount());
            case "igstPercent" -> number(split.igstPercent());
            case "igstAmount" -> money(split.igstAmount());
            case "total" -> money(result.totalAmount());
            case "location" -> safe(item.getLocation());
            case "purchasePrice" -> money(item.getPurchasePrice());
            case "sellingPrice" -> money(item.getSellingPrice());
            case "availableStock" -> number(item.getAvailableStock());
            case "openingStock" -> number(item.getOpeningStock());
            case "minimumStock" -> number(item.getMinimumStock());
            case "reservedStock" -> number(item.getReservedStock());
            case "masterGstPercent" -> number(item.getMasterGstPercent());
            case "masterDiscountPercent" -> number(item.getMasterDiscountPercent());
            default -> "";
        };
    }

    private static String chargeValue(String key, TemplateCharge charge, String gstType, int serial) {
        if (charge == null) return "";
        String k = key == null ? "" : key.replaceFirst("^charge\\.", "");
        DocumentCalculationEngine.ChargeResult result = DocumentCalculationEngine.charge(
                charge.amount(), charge.taxable(), charge.gstPercent());
        TaxSplit split = taxSplit(charge.gstPercent(), result.taxAmount(), gstType);
        return switch (k) {
            case "serial" -> Integer.toString(serial);
            case "type" -> safe(charge.type());
            case "amount" -> money(result.amount());
            case "taxable" -> charge.taxable() ? "Yes" : "No";
            case "taxableAmount" -> money(result.taxableAmount());
            case "gstPercent" -> number(charge.taxable() ? charge.gstPercent() : 0);
            case "taxAmount" -> money(result.taxAmount());
            case "cgstPercent" -> number(split.cgstPercent());
            case "cgstAmount" -> money(split.cgstAmount());
            case "sgstPercent" -> number(split.sgstPercent());
            case "sgstAmount" -> money(split.sgstAmount());
            case "igstPercent" -> number(split.igstPercent());
            case "igstAmount" -> money(split.igstAmount());
            case "total" -> money(result.totalAmount());
            default -> "";
        };
    }

    private record TaxSplit(double cgstPercent, double cgstAmount, double sgstPercent, double sgstAmount, double igstPercent, double igstAmount) { }

    private static TaxSplit taxSplit(double gstPercent, double taxAmount, String gstType) {
        double rate = DocumentCalculationEngine.percent(gstPercent);
        double tax = DocumentCalculationEngine.money(taxAmount);
        if (DocumentCalculationEngine.taxMode(gstType) == DocumentCalculationEngine.TaxMode.IGST)
            return new TaxSplit(0, 0, 0, 0, rate, tax);
        double cgstRate = rate / 2d, sgstRate = rate - cgstRate;
        double cgst = DocumentCalculationEngine.money(tax / 2d), sgst = DocumentCalculationEngine.money(tax - cgst);
        return new TaxSplit(cgstRate, cgst, sgstRate, sgst, 0, 0);
    }

    private static String descriptionWithRemarks(String description, String remarks) {
        String d = safe(description), r = safe(remarks);
        if (r.isBlank()) return d;
        if (d.isBlank()) return r;
        return d + "\n" + r;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static List<Column> itemColumns(TemplateElement element) {
        if(element==null||element.getTableColumnBindings().isEmpty())return itemColumns(element==null?List.of():element.getTableColumns());
        Map<String,Column> catalogue=new LinkedHashMap<>();for(Column c:itemColumns(List.of(
                "serial","code","hsn","description","descriptionWithRemarks","remarks","category","brand","material","size",
                "quantity","unit","rate","discountPercent","discountAmount","taxable","gstPercent","gstAmount","cgstPercent","cgstAmount",
                "sgstPercent","sgstAmount","igstPercent","igstAmount","grossAmount","total","location","purchasePrice","sellingPrice",
                "availableStock","openingStock","minimumStock","reservedStock","masterGstPercent","masterDiscountPercent")))catalogue.put(c.key(),c);
        List<Column> out=new ArrayList<>();
        for(TemplateColumnBinding binding:element.getTableColumnBindings()){
            String key=ManualTemplateMappingService.normalizeItemColumn(binding.getFieldKey());
            Column base=catalogue.get(key);
            String label=binding.getSourceLabel().isBlank()?(base==null?"":base.label()):binding.getSourceLabel();
            double weight=binding.getWidth()>0?binding.getWidth():(base==null?1:base.weight());
            out.add(new Column(base==null?key:base.key(),label,weight));
        }
        return out;
    }

    private static List<Column> itemColumns(List<String> keys) {
        List<Column> all = List.of(
                new Column("serial", "Sr", .55), new Column("code", "Item Code", .95), new Column("hsn", "HSN", .85),
                new Column("description", "Description", 3.2), new Column("descriptionWithRemarks", "Description / Remarks", 3.6),
                new Column("remarks", "Remarks", 1.6), new Column("category", "Category", 1.0), new Column("brand", "Brand", 1.0),
                new Column("material", "Material", 1.0), new Column("size", "Size", .8),
                new Column("quantity", "Qty", .75), new Column("unit", "Unit", .7), new Column("rate", "Rate", 1.15),
                new Column("discountPercent", "Disc %", .8), new Column("discountAmount", "Discount", 1.0),
                new Column("taxable", "Taxable", 1.2), new Column("gstPercent", "GST %", .8), new Column("gstAmount", "GST", 1.0),
                new Column("cgstPercent", "CGST %", .8), new Column("cgstAmount", "CGST", 1.0),
                new Column("sgstPercent", "SGST %", .8), new Column("sgstAmount", "SGST", 1.0),
                new Column("igstPercent", "IGST %", .8), new Column("igstAmount", "IGST", 1.0), new Column("grossAmount", "Amount", 1.3), new Column("total", "Total", 1.3),
                new Column("location", "Location", 1.0), new Column("purchasePrice", "Purchase Price", 1.1),
                new Column("sellingPrice", "Selling Price", 1.1), new Column("availableStock", "Available", .9),
                new Column("openingStock", "Opening", .9), new Column("minimumStock", "Minimum", .9),
                new Column("reservedStock", "Reserved", .9), new Column("masterGstPercent", "Master GST %", .9),
                new Column("masterDiscountPercent", "Master Disc %", .9));
        return chooseColumnsWithAliases(all, keys, true);
    }

    private static List<Column> chargeColumns(List<String> keys) {
        List<Column> all = List.of(
                new Column("serial", "Sr", .55), new Column("type", "Charge", 2.5), new Column("amount", "Amount", 1.2),
                new Column("taxable", "Taxable", .9), new Column("taxableAmount", "Taxable Amount", 1.15),
                new Column("gstPercent", "GST %", .9), new Column("taxAmount", "Tax", 1.1),
                new Column("cgstPercent", "CGST %", .85), new Column("cgstAmount", "CGST", 1.0),
                new Column("sgstPercent", "SGST %", .85), new Column("sgstAmount", "SGST", 1.0),
                new Column("igstPercent", "IGST %", .85), new Column("igstAmount", "IGST", 1.0),
                new Column("total", "Total", 1.25));
        return chooseColumnsWithAliases(all, keys, false);
    }

    private static List<Column> chooseColumnsWithAliases(List<Column> all, List<String> keys, boolean item) {
        List<String> normalized = new ArrayList<>();
        for (String raw : keys == null ? List.<String>of() : keys) {
            if (raw == null) continue;
            String key = raw.trim().replaceFirst(item ? "^item\\." : "^charge\\.", "");
            if (item) key = switch (key) { case "qty" -> "quantity"; case "discount" -> "discountPercent"; case "gst" -> "gstPercent"; case "amount" -> "total"; default -> key; };
            if (!key.isBlank()) normalized.add(key);
        }
        return chooseColumns(all, normalized);
    }

    private static List<Column> chooseColumns(List<Column> all, List<String> keys) {
        // Preserve the template's explicit column order. Filtering the catalogue order
        // silently swapped fields such as rate/unit when a source PDF used a different
        // order from the generic catalogue.
        Map<String, Column> byKey = new LinkedHashMap<>();
        for (Column column : all) byKey.put(column.key(), column);
        List<Column> selected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String key : keys == null ? List.<String>of() : keys) {
            Column column = byKey.get(key);
            if (column != null && seen.add(column.key())) selected.add(column);
        }
        return selected;
    }

    private static void drawCellText(PDPageContentStream cs, PDFont font, float fontSize,
                                     String text, float x, float y, float width, float height, String color) throws IOException {
        drawCellTextAligned(cs, font, fontSize, text, x, y, width, height, color, "LEFT");
    }

    private static void drawCellTextAligned(PDPageContentStream cs, PDFont font, float fontSize,
                                            String text, float x, float y, float width, float height,
                                            String color, String alignment) throws IOException {
        String safe = safePdfText(text);
        PDFont effectiveFont = fontForText(font, safe);
        float size = Math.max(4f, fontSize);
        List<String> lines = wrap(safe, effectiveFont, size, Math.max(5, width));
        while (!wrappedCellFits(effectiveFont, lines, size, width, height) && size > 4.01f) {
            size = Math.max(4f, size - .25f);
            lines = wrap(safe, effectiveFont, size, Math.max(5, width));
        }
        if (!wrappedCellFits(effectiveFont, lines, size, width, height))
            throw new IOException("Table cell text does not fit at the minimum readable size; text was not discarded: " + abbreviateForError(safe));
        setNonStroke(cs, color);
        float lineHeight = size * 1.08f, cy = y + height - size;
        for (String line : lines) {
            float drawX = alignedX(effectiveFont, size, line, x, width, alignment == null ? "LEFT" : alignment.toUpperCase(Locale.ROOT));
            cs.beginText(); cs.setFont(effectiveFont, size); cs.newLineAtOffset(drawX, cy); cs.showText(line); cs.endText();
            cy -= lineHeight;
        }
    }

    private static boolean wrappedCellFits(PDFont font, List<String> lines, float size, float width, float height) throws IOException {
        if (lines == null || lines.isEmpty()) return true;
        if (size + Math.max(0, lines.size()-1) * size * 1.08f > height + .01f) return false;
        for (String line : lines) if (textWidth(font, size, line) > width + .01f) return false;
        return true;
    }

    private static void drawSingleLineCentered(PDPageContentStream cs, PDFont font, float fontSize,
                                               String text, float x, float bottomY, float width, float height,
                                               String color, String alignment) throws IOException {
        String line = safePdfText(text == null ? "" : text);
        font = fontForText(font, line);
        setNonStroke(cs, color);
        String effectiveAlignment = alignment == null ? "LEFT" : alignment.toUpperCase(Locale.ROOT);
        float drawX = alignedX(font, fontSize, line, x, width, effectiveAlignment);
        float ascent = fontSize;
        float descent = 0f;
        if (font.getFontDescriptor() != null) {
            float fdAscent = font.getFontDescriptor().getAscent();
            float fdDescent = font.getFontDescriptor().getDescent();
            if (fdAscent != 0f) ascent = fdAscent / 1000f * fontSize;
            descent = fdDescent / 1000f * fontSize;
        }
        float glyphHeight = Math.max(fontSize * 0.75f, ascent - descent);
        float baseline = bottomY + Math.max(0f, (height - glyphHeight) / 2f) - descent;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(drawX, baseline);
        cs.showText(line);
        cs.endText();
    }

    private static List<String> wrap(String text, PDFont font, float fontSize, float width) throws IOException {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> result = new ArrayList<>();
        for (String paragraph : text.replace('\r', '\n').split("\\n")) {
            if (paragraph.isBlank()) { result.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(font, fontSize, candidate) <= width || line.isEmpty()) {
                    line.setLength(0); line.append(candidate);
                } else {
                    result.add(line.toString()); line.setLength(0); line.append(word);
                }
            }
            if (!line.isEmpty()) result.add(line.toString());
        }
        return result;
    }

    private static float toPdfY(PDPage page, double topLeftY) { return page.getMediaBox().getHeight() - (float)topLeftY; }
    private static void setNonStroke(PDPageContentStream cs, String hex) throws IOException { cs.setNonStrokingColor(awtColor(hex)); }
    private static void setStroke(PDPageContentStream cs, String hex) throws IOException { cs.setStrokingColor(awtColor(hex)); }

    private static java.awt.Color awtColor(String hex) {
        String h = hex == null || !hex.matches("#[0-9a-fA-F]{6}") ? "#172033" : hex;
        return new java.awt.Color(Integer.parseInt(h.substring(1,3),16), Integer.parseInt(h.substring(3,5),16), Integer.parseInt(h.substring(5,7),16));
    }

    private static String safePdfText(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (cp == '\t') out.append(' ');
            else if (cp == '\r') out.append('\n');
            else if (cp == '\n' || cp >= 32) out.appendCodePoint(cp);
        });
        return out.toString();
    }

    private static PDFont fontForText(PDFont fallback, String text) throws IOException {
        if (text == null || text.codePoints().allMatch(cp -> cp == '\n' || cp == '\r' || cp == '\t' || (cp >= 32 && cp <= 126))) return fallback;
        PDDocument doc = CURRENT_DOCUMENT.get();
        if (doc == null) throw new IOException("Unicode PDF font requested outside an active render.");
        String key = "unicode-regular";
        PDFont cached = UNICODE_FONTS.get().get(key);
        if (cached != null) return cached;
        for (String candidate : List.of(
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                "C:/Windows/Fonts/arial.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/System/Library/Fonts/Supplemental/Arial.ttf")) {
            Path path = Path.of(candidate);
            if (!Files.isRegularFile(path)) continue;
            PDFont loaded = PDType0Font.load(doc, path.toFile());
            UNICODE_FONTS.get().put(key, loaded);
            return loaded;
        }
        throw new IOException("Unicode text is present but no supported Unicode font is installed; text was not replaced with question marks.");
    }

    private static String abbreviateForError(String text) {
        String one=(text==null?"":text).replace('\n',' ').trim();
        return one.length()<=80?one:one.substring(0,77)+"...";
    }

    private static String money(double value) { return String.format(Locale.ENGLISH, "%,.2f", value); }
    private static String number(double value) {
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(Locale.ENGLISH, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String quantityNumber(double value) {
        double quantity = DocumentCalculationEngine.quantity(value);
        return java.math.BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString();
    }


    private static TaxInvoiceDocument toSalesLayoutDocument(TemplateData data) {
        List<TaxInvoiceCharge> charges = data.charges().stream()
                .map(c -> new TaxInvoiceCharge(c.type(), c.amount(), c.taxable(), c.gstPercent()))
                .toList();
        InvoiceTotals totals = InvoiceTaxCalculator.calculate(data.items(), charges, data.gstType());
        CompanyProfile company = new CompanyProfile(
                data.value("company.name"), data.value("company.address"), data.value("company.gstin"),
                data.value("company.email"), data.value("company.alternateEmail"), data.value("company.phone"),
                data.value("payment.bankName"), data.value("payment.branch"), data.value("payment.accountNumber"),
                data.value("payment.ifsc"), data.value("payment.accountType"), data.value("payment.mode"),
                data.value("company.terms"), pathText(data.image("company.logo")), pathText(data.image("company.signature")),
                data.value("company.certificationText"));
        InvoiceParty billing = new InvoiceParty(data.value("party.name"), data.value("party.billingAddress"),
                data.value("party.billingGstin"), data.value("party.contactPerson"), data.value("party.contact"));
        InvoiceParty delivery = new InvoiceParty(data.value("party.name"), data.value("party.deliveryAddress"),
                data.value("party.deliveryGstin"), data.value("party.contactPerson"), data.value("party.contact"));
        String words = data.value("totals.amountInWords");
        if (words.isBlank()) words = "INR : " + AmountInWordsConverter.indianRupees(totals.grandTotal());
        return new TaxInvoiceDocument(company,
                data.value("document.number"), parseDate(data.value("document.date")),
                data.value("document.poNumber"), parseDate(data.value("document.poDate")), data.value("document.paymentTerms"),
                billing, delivery, data.value("transport.name"), data.value("transport.gstin"), data.value("transport.vehicleNumber"),
                data.value("transport.contactPerson"), data.value("transport.contact"), data.items(), data.gstType(), charges, totals, words);
    }

    private static String pathText(Path path) { return path == null ? "" : path.toString(); }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return LocalDate.now();
        for (DateTimeFormatter f : List.of(DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"), DateTimeFormatter.ISO_LOCAL_DATE)) {
            try { return LocalDate.parse(value.trim(), f); } catch (Exception ignored) { }
        }
        return LocalDate.now();
    }

    private static boolean isLegacyFixedSalesClosingElement(TemplateElement e) {
        if (e == null) return false;
        boolean closingRule = "LAST".equals(e.getPageRule()) || "INTERMEDIATE".equals(e.getPageRule());
        return closingRule && e.getY() >= 600.0 && e.getY() < 812.0;
    }

    private static TemplateElement sharedSalesTableElement(TemplateElement source, FlowPlan flow, int part,
                                                            TaxInvoicePdfGenerator.SalesLayoutPlan layout) {
        TemplateElement table = source.copy();
        boolean finalPage = part == flow.totalCopies() - 1;
        double capacity;
        if (finalPage) {
            double pageHeight = 841.8898; // A4 points; source templates retain A4 geometry.
            double financialTopTopLeft = pageHeight - (layout.financialY() + layout.financialHeight());
            capacity = financialTopTopLeft - table.getY() - 5.0;
        } else {
            capacity = layout.firstIntermediateCapacity();
        }
        table.setHeight(Math.max(table.getHeaderHeight() + 20.0, capacity));
        table.setRowHeight(Math.max(18.0, layout.physicalRowMinHeight()));
        return table;
    }

    /**
     * Generic fixed-artwork continuation flow.  The source header/footer remain the template's
     * artwork; only the mapped item body is cleared and rebuilt.  Intermediate pages reuse the
     * closing area for item rows, while the last page preserves the template's own totals/terms/
     * bank/signature artwork.  No template id/name checks are permitted here.
     */
    private static void prepareFlowFixedPage(PDPage page, PDPageContentStream cs, FlowPlan flow, int part) throws IOException {
        PdfStudioRuntimeFlowPlanner.ItemFlowPlan g = flow.flowFixed();
        if (g == null || flow.itemTable() == null) return;
        boolean last = part == flow.totalCopies() - 1;
        double bodyTop = g.bodyTop();
        double bodyBottom = last ? g.finalBottom() : g.intermediateBottom();
        float x = (float) flow.itemTable().getX();
        float w = (float) flow.itemTable().getWidth();
        float pdfBottom = toPdfY(page, bodyBottom);
        float pdfTop = toPdfY(page, bodyTop);
        TemplateElement sourceTable=flow.itemTable();
        boolean captured=sourceTable!=null&&sourceTable.isSourceStyleCaptured();
        if(!captured||sourceTable.isSourceMaskSafe()){
            setNonStroke(cs,captured?sourceTable.getFillColor():"#FFFFFF");
            cs.addRect(x, pdfBottom, w, Math.max(1f, pdfTop - pdfBottom));
            cs.fill();
        }
    }

    private static void prepareDynamicSalesPage(PDPage page, PDPageContentStream cs, FlowPlan flow, int part,
                                                TaxInvoicePdfGenerator.SalesLayoutPlan layout) throws IOException {
        // Clear the fixed source closing artwork. The actual closing stack is rebuilt from
        // measured Standard-Sales geometry on the final page; intermediate pages reuse this
        // region for real item rows.
        float top = flow != null && flow.itemTable() != null
                ? (float)(flow.itemTable().getY() + flow.itemTable().getHeaderHeight()) : 270.15f;
        float bottom = Math.min((float)page.getMediaBox().getHeight() - 30f, 808.0f);
        float x = flow != null && flow.itemTable() != null ? (float)flow.itemTable().getX() : 23.8f;
        float width = flow != null && flow.itemTable() != null ? (float)flow.itemTable().getWidth() : 547.4f;
        setNonStroke(cs, "#FFFFFF");
        float pyBottom = toPdfY(page, bottom);
        float pyTop = toPdfY(page, top);
        cs.addRect(x, pyBottom, width, Math.max(1f, pyTop - pyBottom));
        cs.fill();
    }

    private static void drawDynamicSalesClosing(PDDocument doc, PDPage page, PDPageContentStream cs,
                                                TemplateData data, TaxInvoicePdfGenerator.SalesLayoutPlan layout,
                                                FlowPlan flow) throws IOException {
        final float left = flow != null && flow.itemTable() != null ? (float)flow.itemTable().getX() : 24f;
        final float width = flow != null && flow.itemTable() != null ? (float)flow.itemTable().getWidth() : 547f;
        final float leftW = width * .65f;
        final float gapW = width * .02f;
        final float rightW = width * .33f;
        final String stroke = "#7599C6";
        final String navy = "#1E437B";

        float financialH = layout.financialHeight();
        float financialTop = page.getMediaBox().getHeight() - (layout.financialY() + financialH);
        float financialBottomPdf = toPdfY(page, financialTop + financialH);
        drawRoundedCard(cs, left, financialBottomPdf, leftW, financialH, 5f, "#FFFFFF", stroke);
        drawRoundedCard(cs, left + leftW + gapW, financialBottomPdf, rightW, financialH, 5f, "#FFFFFF", stroke);

        List<String[]> bankRows = new ArrayList<>();
        addIfValue(bankRows, "Supplier GST NO", data.value("company.gstin"));
        addIfValue(bankRows, "BANK NAME", data.value("payment.bankName"));
        addIfValue(bankRows, "BRANCH", data.value("payment.branch"));
        addIfValue(bankRows, "A/c NO", data.value("payment.accountNumber"));
        addIfValue(bankRows, "IFSC CODE", data.value("payment.ifsc"));
        addIfValue(bankRows, "ACCOUNT TYPE", data.value("payment.accountType"));
        addIfValue(bankRows, "PAYMENT MODE", data.value("payment.mode"));
        bankRows.add(new String[]{"PAYMENT TERMS", blankAs(data.value("document.paymentTerms"), "NA")});
        List<String[]> totals = dynamicTotalsRows(data);
        // Give multiline Payment Terms as many natural row units as its wrapped value needs.
        // This mirrors the standard Sales table and removes OS/font-mapper dependent failures.
        float bankValueWidth = leftW * .69f - 7f;
        String paymentValue = ":  " + bankRows.get(bankRows.size()-1)[1];
        PDFont bankFont = fontForText(font(Standard14Fonts.FontName.HELVETICA), safePdfText(paymentValue));
        int paymentUnits = Math.max(1, wrap(safePdfText(paymentValue), bankFont, 6.1f, Math.max(5, bankValueWidth)).size());
        int bankUnits = Math.max(1, bankRows.size()-1 + paymentUnits);
        int naturalFinancialRows = Math.max(1, Math.max(bankUnits, totals.size()));
        float naturalRowH = financialH / naturalFinancialRows;
        float bankTop = financialTop;
        for (int i = 0; i < bankRows.size(); i++) {
            String[] row = bankRows.get(i);
            int rowUnits = i == bankRows.size()-1 ? paymentUnits : 1;
            float bankRowH = naturalRowH * rowUnits;
            float contentH = Math.max(1f, bankRowH - 2f);
            drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 6.1f, row[0], left + 6f,
                    toPdfY(page, bankTop + bankRowH - 2.0f), leftW * .31f - 8f, contentH, "#000000");
            drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA), 6.1f, ":  " + row[1], left + leftW * .31f,
                    toPdfY(page, bankTop + bankRowH - 2.0f), bankValueWidth, contentH,
                    (i == 0 || i == bankRows.size() - 1) ? navy : "#000000");
            bankTop += bankRowH;
        }

        float calcX = left + leftW + gapW;
        float calcRowH = naturalRowH;
        setStroke(cs, stroke); cs.setLineWidth(.35f);
        for (int i = 1; i < totals.size(); i++) {
            float y = toPdfY(page, financialTop + i * calcRowH);
            cs.moveTo(calcX, y); cs.lineTo(calcX + rightW, y); cs.stroke();
        }
        for (int i = 0; i < totals.size(); i++) {
            float topY = financialTop + i * calcRowH;
            String[] row = totals.get(i);
            PDFont lf = (i == 0 || row[0].startsWith("TAXABLE")) ? font(Standard14Fonts.FontName.HELVETICA_BOLD) : font(Standard14Fonts.FontName.HELVETICA);
            float rowBottom = toPdfY(page, topY + calcRowH);
            // Calculation rows are a single continuous row: label uses the full left edge,
            // amount uses the full right edge, and both share the same vertically-centred baseline.
            // Do not use drawCellText here: that generic helper intentionally top-aligns wrapped text.
            drawSingleLineCentered(cs, lf, 6.0f, row[0], calcX + 4f, rowBottom, rightW - 8f, calcRowH, "#000000", "LEFT");
            drawSingleLineCentered(cs, lf, 6.0f, row[1], calcX + 4f, rowBottom, rightW - 8f, calcRowH, "#000000", "RIGHT");
        }

        float closingH = layout.closingHeight();
        float closingTop = page.getMediaBox().getHeight() - (layout.closingY() + closingH);
        float closingBottomPdf = toPdfY(page, closingTop + closingH);
        drawRoundedCard(cs, left, closingBottomPdf, leftW, closingH, 5f, "#DFF5E3", stroke);
        drawRoundedCard(cs, calcX, closingBottomPdf, rightW, closingH, 5f, "#DFF5E3", stroke);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.0f, "INR :", left + 6f,
                closingBottomPdf + 2f, 38f, closingH - 3f, navy);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA), 6.8f,
                blankAs(data.value("totals.amountInWordsText"), data.value("totals.amountInWords")), left + 45f,
                closingBottomPdf + 2f, leftW - 50f, closingH - 3f, "#000000");
        // Grand Total follows the same continuous-row rule as Calculation: full left label,
        // full right amount, both optically centred between the top/bottom card rules.
        drawSingleLineCentered(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.0f, "G R A N D   T O T A L",
                calcX + 5f, closingBottomPdf, rightW - 10f, closingH, navy, "LEFT");
        drawSingleLineCentered(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 8.0f, data.value("totals.roundedGrandTotal"),
                calcX + 5f, closingBottomPdf, rightW - 10f, closingH, navy, "RIGHT");

        float termsH = layout.termsHeight();
        float termsTop = page.getMediaBox().getHeight() - (layout.termsY() + termsH);
        float termsBottomPdf = toPdfY(page, termsTop + termsH);
        drawRoundedCard(cs, left, termsBottomPdf, leftW, termsH, 5f, "#FFFFFF", stroke);
        drawRoundedCard(cs, calcX, termsBottomPdf, rightW, termsH, 5f, "#FFFFFF", stroke);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 7.6f, "TERMS & CONDITIONS", left + 7f,
                toPdfY(page, termsTop + 12f), leftW - 14f, 10f, navy);
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA), 6.7f, data.value("company.terms"), left + 7f,
                termsBottomPdf + 5f, leftW - 14f, Math.max(10f, termsH - 18f), "#000000");
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 8.5f, "For, " + data.value("company.name"), calcX + 6f,
                toPdfY(page, termsTop + 13f), rightW - 12f, 10f, navy);
        Path signature = data.image("company.signature");
        if (signature != null && Files.isRegularFile(signature)) {
            PDImageXObject image = PDImageXObject.createFromFileByContent(signature.toFile(), doc);
            float maxW = rightW - 18f, maxH = Math.max(8f, termsH - 31f);
            float scale = Math.min(maxW / Math.max(1f, image.getWidth()), maxH / Math.max(1f, image.getHeight()));
            float iw = image.getWidth() * scale, ih = image.getHeight() * scale;
            cs.drawImage(image, calcX + (rightW - iw) / 2f, termsBottomPdf + 13f, iw, ih);
        }
        drawCellText(cs, font(Standard14Fonts.FontName.HELVETICA_BOLD), 6.1f, "AUTHORIZED SIGNATORY", calcX + 8f,
                termsBottomPdf + 2f, rightW - 16f, 9f, "#000000");
    }

    private static void drawRoundedCard(PDPageContentStream cs, float x, float y, float w, float h, float radius,
                                        String fill, String stroke) throws IOException {
        setNonStroke(cs, fill); setStroke(cs, stroke); cs.setLineWidth(.55f);
        roundedRect(cs, x, y, w, h, radius); cs.fillAndStroke();
    }


    /**
     * Generic dynamic financial block for imported FLOW_FIXED artwork.  The template owns
     * geometry/colors; the renderer owns only ERP row composition so charges, GST/IGST/no-GST,
     * discount and round-off never depend on hard-coded sample labels from the uploaded PDF.
     */
    private static void drawMappedFinancialSummary(PDPage page, PDPageContentStream cs,
                                                    TemplateElement box, TemplateData data) throws IOException {
        List<String[]> rows = mappedFinancialRows(data);
        String grand = blankAs(data.value("totals.roundedGrandTotal"), data.value("totals.grandTotal"));
        float x=(float)box.getX(), width=(float)box.getWidth(), boxH=(float)box.getHeight();
        int normalCount=rows.size();
        boolean captured=box.isSourceStyleCaptured();

        // A source PDF can have a calculation body and a physically separate Grand Total strip
        // (often with a small gap between them). Treating both as one uniform grid is incorrect:
        // tax/charge rows drift downward and overwrite the source Grand Total artwork.
        float totalBandH=(float)Math.min(boxH, Math.max(0, box.getSummaryTotalHeight()));
        float totalGap=(float)Math.min(Math.max(0, boxH-totalBandH), Math.max(0, box.getSummaryTotalGap()));
        boolean separatedTotal=totalBandH>.1f;
        float bodyH=separatedTotal ? Math.max(1f, boxH-totalBandH-totalGap) : boxH;
        float sourceRowH=(float)Math.max(0, box.getRowHeight());
        int sourceSlots=sourceRowH>.1f ? Math.max(1,(int)Math.floor((bodyH+.6f)/sourceRowH)) : 0;
        boolean keepSourceGrid=captured && separatedTotal && sourceSlots>0 && normalCount<=sourceSlots;

        float preferred=captured && sourceRowH>.1f
                ? sourceRowH
                : (float)Math.max(12.5,Math.min(20.5,box.getFontSize()*2.0));
        float rowH=keepSourceGrid ? sourceRowH : Math.min(preferred,bodyH/Math.max(1,normalCount));
        if(rowH<8.5f)throw new IOException("Dynamic Financial Summary does not have enough readable space for "+normalCount+" rows. Increase the mapped calculation block height.");

        float bodyTop=(float)box.getY();
        if(!separatedTotal && "UP".equals(box.getGrowthDirection())) {
            float legacyTotalH=rowH*Math.max(1,normalCount+1);
            bodyTop=(float)(box.getY()+box.getHeight()-legacyTotalH);
            bodyH=legacyTotalH-rowH;
        }
        float bodyBottomTop=bodyTop+bodyH;
        String fill=box.getFillColor(), grid=captured?box.getStrokeColor():"#AFC2D8", text=box.getTextColor();
        float lineWidth=(float)(captured?Math.max(.25,box.getStrokeWidth()):.45);
        float split=x+width*(float)box.getSummaryLabelRatio();

        if(!keepSourceGrid){
            // Rebuild only the body interior when the dynamic row count cannot use the source row
            // rhythm. Keep the source outer rounded border and separate total strip untouched.
            float inset=captured?.8f:0f;
            float pdfBodyBottom=toPdfY(page,bodyBottomTop-inset);
            float pdfBodyTop=toPdfY(page,bodyTop+inset);
            setNonStroke(cs,fill);
            cs.addRect(x+inset,pdfBodyBottom,Math.max(1f,width-inset*2),Math.max(1f,pdfBodyTop-pdfBodyBottom));
            cs.fill();
            setStroke(cs,grid);cs.setLineWidth(lineWidth);
            cs.moveTo(split,toPdfY(page,bodyTop));cs.lineTo(split,toPdfY(page,bodyBottomTop));cs.stroke();
            for(int i=1;i<normalCount;i++){
                float y=bodyTop+i*rowH;cs.moveTo(x,toPdfY(page,y));cs.lineTo(x+width,toPdfY(page,y));cs.stroke();
            }
            if(!captured){cs.addRect(x,toPdfY(page,bodyBottomTop),width,bodyH);cs.stroke();}
        }

        float baseFont=(float)Math.max(5,Math.min(box.getFontSize(),rowH*.55));
        for(int i=0;i<normalCount;i++){
            float rowTop=bodyTop+i*rowH;
            String[] row=rows.get(i);float bottom=toPdfY(page,rowTop+rowH);
            boolean emphasize=i==0||row[0].startsWith("TAXABLE");
            drawSingleLineCentered(cs,fontForVariant(box,emphasize),baseFont,row[0],x+6f,bottom,split-x-10f,rowH,text,"LEFT");
            drawSingleLineCentered(cs,fontForVariant(box,true),baseFont,row[1],split+4f,bottom,x+width-split-9f,rowH,text,"RIGHT");
        }

        if(separatedTotal){
            float totalTop=(float)(box.getY()+box.getHeight()-totalBandH);
            float totalBottom=toPdfY(page,totalTop+totalBandH);
            // Source-captured total artwork is authoritative; only live label/value text is drawn.
            if(!captured){
                String totalFill=box.getSummaryTotalFillColor();
                if(totalFill.isBlank())totalFill="#0E3F79";
                setNonStroke(cs,totalFill);cs.addRect(x,totalBottom,width,totalBandH);cs.fill();
            }
            String totalText=box.getSummaryTotalTextColor();
            if(totalText.isBlank())totalText=captured?text:"#FFFFFF";
            float totalFont=Math.min(Math.max(baseFont+.5f,5.5f),totalBandH*.58f);
            drawSingleLineCentered(cs,fontForVariant(box,true),totalFont,"GRAND TOTAL",x+6f,totalBottom,split-x-10f,totalBandH,totalText,"LEFT");
            drawSingleLineCentered(cs,fontForVariant(box,true),Math.min(totalFont+.3f,totalBandH*.60f),grand,split+4f,totalBottom,x+width-split-9f,totalBandH,totalText,"RIGHT");
            return;
        }

        // Backward-compatible uniform summary for templates that do not carry source total-band metadata.
        float grandTop=bodyTop+normalCount*rowH,grandBottom=toPdfY(page,grandTop+rowH);
        String grandFill=box.getSummaryTotalFillColor();
        boolean explicitGrandFill=!grandFill.isBlank();
        if(grandFill.isBlank()&&!captured)grandFill="#0E3F79";
        String grandText=box.getSummaryTotalTextColor();
        if(grandText.isBlank())grandText=captured?text:"#FFFFFF";
        if(!grandFill.isBlank()&&(!captured||explicitGrandFill)){setNonStroke(cs,grandFill);cs.addRect(x,grandBottom,width,rowH);cs.fill();}
        if(box.isStrokeEnabled()){setStroke(cs,grid);cs.setLineWidth(lineWidth);cs.moveTo(x,toPdfY(page,grandTop));cs.lineTo(x+width,toPdfY(page,grandTop));cs.stroke();}
        drawSingleLineCentered(cs,fontForVariant(box,true),Math.min(baseFont+.5f,rowH*.58f),"GRAND TOTAL",x+6f,grandBottom,split-x-10f,rowH,grandText,"LEFT");
        drawSingleLineCentered(cs,fontForVariant(box,true),Math.min(baseFont+.8f,rowH*.60f),grand,split+4f,grandBottom,x+width-split-9f,rowH,grandText,"RIGHT");
    }

    private static List<String[]> mappedFinancialRows(TemplateData data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"BASIC AMOUNT", blankAs(data.value("totals.basicAmount"), "0.00")});
        if (!isZeroMoney(data.value("totals.discountAmount")))
            rows.add(new String[]{"DISCOUNT", data.value("totals.discountAmount")});
        for (TemplateCharge charge : data.charges()) {
            String label = charge.type() == null || charge.type().isBlank() ? "CHARGE" : charge.type().trim().toUpperCase(Locale.ROOT);
            rows.add(new String[]{label, money(charge.total())});
        }
        rows.add(new String[]{"TAXABLE AMOUNT", blankAs(data.value("totals.taxableAmount"), "0.00")});
        String mode = data.gstType() == null ? "" : data.gstType().trim().toUpperCase(Locale.ROOT);
        if (mode.contains("IGST") || mode.contains("INTER")) {
            rows.add(new String[]{blankAs(data.value("tax.primaryLabel"), "IGST"), blankAs(data.value("totals.igstAmount"), "0.00")});
        } else if (!(mode.contains("NO_GST") || mode.contains("NO GST") || mode.contains("NONE") || mode.contains("EXEMPT"))) {
            rows.add(new String[]{blankAs(data.value("tax.primaryLabel"), "CGST"), blankAs(data.value("totals.cgstAmount"), "0.00")});
            rows.add(new String[]{blankAs(data.value("tax.secondaryLabel"), "SGST"), blankAs(data.value("totals.sgstAmount"), "0.00")});
        }
        if (!isZeroMoney(data.value("totals.roundOff")))
            rows.add(new String[]{"ROUND OFF", data.value("totals.roundOff")});
        return rows;
    }

    private static void addIfValue(List<String[]> rows, String label, String value) {
        if (value != null && !value.isBlank()) rows.add(new String[]{label, value.trim()});
    }

    private static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }

    private static List<String[]> dynamicTotalsRows(TemplateData data) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"BASIC AMOUNT", data.value("totals.basicAmount")});
        if (!isZeroMoney(data.value("totals.discountAmount"))) rows.add(new String[]{"DISCOUNT", data.value("totals.discountAmount")});
        for (TemplateCharge charge : data.charges()) {
            rows.add(new String[]{charge.type().toUpperCase(Locale.ROOT), money(charge.amount())});
        }
        rows.add(new String[]{"TAXABLE AMOUNT", data.value("totals.taxableAmount")});
        if (data.gstType().toUpperCase(Locale.ROOT).contains("IGST") || data.gstType().toUpperCase(Locale.ROOT).contains("INTER")) {
            rows.add(new String[]{blankAs(data.value("tax.primaryLabel"), "IGST"), data.value("totals.igstAmount")});
        } else {
            rows.add(new String[]{blankAs(data.value("tax.primaryLabel"), "CGST"), data.value("totals.cgstAmount")});
            rows.add(new String[]{blankAs(data.value("tax.secondaryLabel"), "SGST"), data.value("totals.sgstAmount")});
        }
        rows.add(new String[]{"ROUND OFF", blankAs(data.value("totals.roundOff"), "-")});
        return rows;
    }

    private static boolean isZeroMoney(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) return true;
        try { return Math.abs(Double.parseDouble(value.replace(",", "").trim())) < .004; }
        catch (Exception ignored) { return false; }
    }

    private record Column(String key, String label, double weight) {}

    private record FlowPlan(TemplateElement itemTable, TemplateElement chargeTable,
                            int itemPages, int chargePages, int totalCopies, int chargeStartPart,
                            TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout, PdfStudioRuntimeFlowPlanner.ItemFlowPlan flowFixed) {
        static FlowPlan forPage(List<TemplateElement> elements, TemplateData data,
                                TaxInvoicePdfGenerator.SalesLayoutPlan salesLayout,
                                double pageHeight, boolean useFlowFixed) {
            TemplateElement item = elements.stream().filter(e -> e.getType() == ElementType.ITEM_TABLE).findFirst().orElse(null);
            TemplateElement charge = elements.stream().filter(e -> e.getType() == ElementType.CHARGE_TABLE).findFirst().orElse(null);
            PdfStudioRuntimeFlowPlanner.ItemFlowPlan fixed = useFlowFixed && item != null
                    ? PdfStudioRuntimeFlowPlanner.plan(elements, item, pageHeight, data.items() == null ? 0 : data.items().size(), true) : null;
            if (item != null && salesLayout != null && salesLayout.totalPages() > 0) {
                return new FlowPlan(item, charge, salesLayout.totalPages(), 0,
                        salesLayout.totalPages(), 0, salesLayout, fixed);
            }
            if (item != null && fixed != null) {
                int itemPages = fixed.pagesFor(data.items() == null ? 0 : data.items().size());
                int chargePages = charge == null || data.charges() == null || data.charges().isEmpty() ? 0 : requiredPages(charge, data.charges().size());
                int start = Math.max(0, itemPages - 1);
                int total = chargePages <= 0 ? itemPages : Math.max(itemPages, start + chargePages);
                return new FlowPlan(item, charge, itemPages, chargePages, Math.max(1,total), start, null, fixed);
            }
            int ip = item == null ? 0 : requiredPages(item, data.items().size());
            int cp = charge == null ? 0 : requiredPages(charge, data.charges().size());
            if (item == null && charge == null) return new FlowPlan(null, null, 0, 0, 1, 0, null, null);
            if (item != null && charge == null) return new FlowPlan(item, null, ip, 0, Math.max(1, ip), 0, null, null);
            if (item == null) return new FlowPlan(null, charge, 0, cp, Math.max(1, cp), 0, null, null);
            int start = Math.max(0, ip - 1);
            int total = Math.max(1, ip + Math.max(0, cp - 1));
            return new FlowPlan(item, charge, ip, cp, total, start, null, null);
        }

        TemplateElement primaryTable() { return itemTable != null ? itemTable : chargeTable; }
        boolean drawItemTable(int part) { return itemTable != null && part < Math.max(1, itemPages); }
        boolean drawChargeTable(int part) {
            if (salesLayout != null) return false;
            if (chargeTable == null) return false;
            int chargePart = part - chargeStartPart;
            return chargePart >= 0 && chargePart < Math.max(1, chargePages);
        }
        List<TaxInvoiceItem> itemChunk(List<TaxInvoiceItem> items, int part) {
            if (!drawItemTable(part) || items == null || items.isEmpty()) return List.of();
            if (salesLayout != null && part < salesLayout.pages().size()) {
                var page = salesLayout.pages().get(part);
                int from = Math.min(items.size(), page.fromIndex());
                int to = Math.min(items.size(), page.toIndex());
                return items.subList(from, to);
            }
            if (flowFixed != null) {
                int[] range = flowFixed.rangeFor(items.size(), part, Math.max(1,itemPages));
                return items.subList(range[0], range[1]);
            }
            int rows = rowsPerPage(itemTable), from = Math.min(items.size(), part * rows), to = Math.min(items.size(), from + rows);
            return items.subList(from, to);
        }
        List<TemplateCharge> chargeChunk(List<TemplateCharge> charges, int part) {
            if (!drawChargeTable(part) || charges == null || charges.isEmpty()) return List.of();
            int chargePart = part - chargeStartPart, rows = rowsPerPage(chargeTable);
            int from = Math.min(charges.size(), chargePart * rows), to = Math.min(charges.size(), from + rows);
            return charges.subList(from, to);
        }
    }}
