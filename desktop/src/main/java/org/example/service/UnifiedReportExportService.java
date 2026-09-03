package org.example.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.api.reporting.ReportingApiClient.*;
import org.example.config.ConfigManager;

import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * One renderer for unified ReportResult data. PDF, Excel, CSV and Print therefore
 * cannot silently recalculate finance or Return values differently from the UI.
 */
public final class UnifiedReportExportService {
    private static final DeviceRgb NAVY = new DeviceRgb(15,45,77);
    private static final DeviceRgb BLUE = new DeviceRgb(32,105,210);
    private static final DeviceRgb PALE = new DeviceRgb(239,245,252);
    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private UnifiedReportExportService() {}

    public static void pdf(Path target, ReportResult result, Set<String> visibleKeys,
                           boolean includeSummary, boolean includeFilters) throws IOException {
        Objects.requireNonNull(result,"result");
        List<Integer> positions=positions(result,visibleKeys);
        PageSize page=positions.size()<=6?PageSize.A4:PageSize.A4.rotate();
        Path tmp=Files.createTempFile(target.getParent()==null?Path.of("."):target.getParent(),"dse-report-",".pdf");
        try{
            try(PdfDocument pdf=new PdfDocument(new PdfWriter(tmp.toFile())); Document doc=new Document(pdf,page)){
                doc.setMargins(30,28,34,28);
                addHeader(doc,result);
                if(includeFilters)addFilters(doc,result);
                if(includeSummary)addSummary(doc,result);
                addTable(doc,result,positions);
            }
            stampFooter(tmp,target,result);
        }finally{Files.deleteIfExists(tmp);}
    }

    public static void excel(Path target, ReportResult result, Set<String> visibleKeys) throws IOException {
        List<Integer> positions=positions(result,visibleKeys);
        try(Workbook wb=new XSSFWorkbook()){
            CellStyle title=wb.createCellStyle();Font titleFont=wb.createFont();titleFont.setBold(true);titleFont.setFontHeightInPoints((short)16);title.setFont(titleFont);
            CellStyle heading=wb.createCellStyle();Font hf=wb.createFont();hf.setBold(true);heading.setFont(hf);heading.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money=wb.createCellStyle();money.setDataFormat(wb.createDataFormat().getFormat("₹ #,##0.00"));
            CellStyle number=wb.createCellStyle();number.setDataFormat(wb.createDataFormat().getFormat("#,##0.####"));

            Sheet summary=wb.createSheet("Summary");int row=0;
            Row r=summary.createRow(row++);org.apache.poi.ss.usermodel.Cell tc=r.createCell(0);tc.setCellValue(result.title());tc.setCellStyle(title);
            summary.createRow(row++).createCell(0).setCellValue("Period: "+result.periodFrom()+" to "+result.periodTo());
            summary.createRow(row++).createCell(0).setCellValue("Generated: "+result.generatedAt()+" by "+result.generatedBy());
            row++;
            Row mh=summary.createRow(row++);mh.createCell(0).setCellValue("Metric");mh.createCell(1).setCellValue("Value");mh.getCell(0).setCellStyle(heading);mh.getCell(1).setCellStyle(heading);
            for(ReportMetric m:result.metrics()){Row mr=summary.createRow(row++);mr.createCell(0).setCellValue(m.label());org.apache.poi.ss.usermodel.Cell vc=mr.createCell(1);if("COUNT".equals(m.format()))vc.setCellValue((long)m.value());else{vc.setCellValue(m.value());vc.setCellStyle("MONEY".equals(m.format())?money:number);}}
            row++;
            Row fh=summary.createRow(row++);fh.createCell(0).setCellValue("Applied Filters");fh.getCell(0).setCellStyle(heading);
            for(var e:result.appliedFilters().entrySet()){Row fr=summary.createRow(row++);fr.createCell(0).setCellValue(e.getKey());fr.createCell(1).setCellValue(e.getValue());}
            summary.autoSizeColumn(0);summary.autoSizeColumn(1);

            Sheet details=wb.createSheet("Details");Row header=details.createRow(0);for(int j=0;j<positions.size();j++){org.apache.poi.ss.usermodel.Cell c=header.createCell(j);c.setCellValue(result.columns().get(positions.get(j)).label());c.setCellStyle(heading);}
            int dr=1;for(ReportRow rr:result.rows()){Row er=details.createRow(dr++);for(int j=0;j<positions.size();j++){int p=positions.get(j);ReportColumn col=result.columns().get(p);String value=rr.values().size()>p?rr.values().get(p):"";org.apache.poi.ss.usermodel.Cell c=er.createCell(j);if(col.numeric()){try{c.setCellValue(Double.parseDouble(value));c.setCellStyle("MONEY".equals(col.type())?money:number);}catch(Exception ex){c.setCellValue(value);}}else c.setCellValue(value);}}
            details.createFreezePane(0,1);if(!positions.isEmpty())details.setAutoFilter(new CellRangeAddress(0,Math.max(0,dr-1),0,positions.size()-1));for(int j=0;j<positions.size();j++)details.setColumnWidth(j,Math.min(60*256,Math.max(12*256,(int)result.columns().get(positions.get(j)).preferredWidth()*42)));
            try(OutputStream out=Files.newOutputStream(target)){wb.write(out);}
        }
    }

    public static void csv(Path target, ReportResult result, Set<String> visibleKeys) throws IOException {
        List<Integer> positions=positions(result,visibleKeys);
        try(BufferedWriter out=Files.newBufferedWriter(target,StandardCharsets.UTF_8)){
            for(int j=0;j<positions.size();j++){if(j>0)out.write(',');out.write(csv(result.columns().get(positions.get(j)).label()));}out.newLine();
            for(ReportRow r:result.rows()){for(int j=0;j<positions.size();j++){if(j>0)out.write(',');int p=positions.get(j);out.write(csv(r.values().size()>p?r.values().get(p):""));}out.newLine();}
        }
    }

    public static Path print(ReportResult result, Set<String> visibleKeys) throws IOException {
        Path file=Files.createTempFile("dse-report-print-",".pdf");pdf(file,result,visibleKeys,true,true);
        if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.PRINT))throw new IOException("System PDF printing is not available on this workstation. Export the PDF and print it from the PDF viewer.");
        Desktop.getDesktop().print(file.toFile());return file;
    }

    private static void addHeader(Document doc,ReportResult r){
        Table h=new Table(UnitValue.createPercentArray(new float[]{60,40})).useAllAvailableWidth();
        Cell company=new Cell().setBorder(null).setPadding(0);company.add(new Paragraph(ConfigManager.get("company.name","DSE ERP")).setBold().setFontSize(14).setFontColor(NAVY));String address=ConfigManager.get("company.address","").trim();if(!address.isBlank())company.add(new Paragraph(address).setFontSize(7));String contact=join(ConfigManager.get("company.phone",""),ConfigManager.get("company.email",""),ConfigManager.get("company.website",""));if(!contact.isBlank())company.add(new Paragraph(contact).setFontSize(7));String gst=ConfigManager.get("company.gstin","").trim();if(!gst.isBlank())company.add(new Paragraph("GSTIN: "+gst).setFontSize(7).setBold());h.addCell(company);
        Cell report=new Cell().setBorder(null).setTextAlignment(TextAlignment.RIGHT).setPadding(0);report.add(new Paragraph(r.title().toUpperCase(Locale.ROOT)).setBold().setFontSize(15).setFontColor(BLUE));report.add(new Paragraph(r.periodFrom()+" to "+r.periodTo()).setFontSize(8));report.add(new Paragraph("Generated "+r.generatedAt()).setFontSize(7));report.add(new Paragraph("By "+r.generatedBy()).setFontSize(7));h.addCell(report);doc.add(h);doc.add(new Paragraph(r.description()).setFontSize(8).setFontColor(new DeviceRgb(70,85,100)).setMarginTop(5).setMarginBottom(6));
    }
    private static void addFilters(Document doc,ReportResult r){if(r.appliedFilters()==null||r.appliedFilters().isEmpty())return;StringBuilder b=new StringBuilder("Filters: ");boolean first=true;for(var e:r.appliedFilters().entrySet()){if(!first)b.append("  |  ");first=false;b.append(e.getKey()).append(": ").append(e.getValue());}doc.add(new Paragraph(b.toString()).setFontSize(7.5f).setBackgroundColor(PALE).setPadding(5).setMarginBottom(7));}
    private static void addSummary(Document doc,ReportResult r){if(r.metrics()==null||r.metrics().isEmpty())return;int count=Math.min(6,r.metrics().size());Table t=new Table(UnitValue.createPercentArray(count)).useAllAvailableWidth().setMarginBottom(8);for(int i=0;i<count;i++){ReportMetric m=r.metrics().get(i);Cell c=new Cell().setPadding(5).setBackgroundColor(PALE).setBorder(new SolidBorder(new DeviceRgb(205,218,234), 0.75f));c.add(new Paragraph(m.label()).setFontSize(6.5f).setFontColor(new DeviceRgb(85,100,118)));c.add(new Paragraph(formatMetric(m)).setBold().setFontSize(9).setFontColor(NAVY));t.addCell(c);}doc.add(t);}
    private static void addTable(Document doc,ReportResult r,List<Integer> positions){if(positions.isEmpty()){doc.add(new Paragraph("No visible columns selected."));return;}float[] widths=new float[positions.size()];for(int i=0;i<positions.size();i++)widths[i]=(float)Math.max(60,r.columns().get(positions.get(i)).preferredWidth());Table table=new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth().setFontSize(6.8f);for(int p:positions){ReportColumn col=r.columns().get(p);Cell h=new Cell().add(new Paragraph(col.label()).setBold()).setBackgroundColor(NAVY).setFontColor(ColorConstants.WHITE).setPadding(4).setTextAlignment(col.numeric()?TextAlignment.RIGHT:TextAlignment.LEFT);table.addHeaderCell(h);}String group=null;for(ReportRow rr:r.rows()){if(rr.groupKey()!=null&&!rr.groupKey().isBlank()&&!Objects.equals(group,rr.groupKey())){group=rr.groupKey();table.addCell(new Cell(1,positions.size()).add(new Paragraph(group).setBold()).setBackgroundColor(PALE).setFontColor(NAVY).setPadding(4));}for(int p:positions){ReportColumn col=r.columns().get(p);String raw=rr.values().size()>p?rr.values().get(p):"";Cell c=new Cell().add(new Paragraph(formatValue(col,raw))).setPadding(3.5f).setTextAlignment(col.numeric()?TextAlignment.RIGHT:TextAlignment.LEFT);table.addCell(c);}}if(r.rows().isEmpty())table.addCell(new Cell(1,positions.size()).add(new Paragraph("No transactions found for the selected criteria.")).setTextAlignment(TextAlignment.CENTER).setPadding(15));doc.add(table);}
    private static void stampFooter(Path source,Path target,ReportResult r)throws IOException{try(PdfDocument pdf=new PdfDocument(new PdfReader(source.toFile()),new PdfWriter(target.toFile()))){int pages=pdf.getNumberOfPages();for(int i=1;i<=pages;i++){var page=pdf.getPage(i);PdfCanvas pc=new PdfCanvas(page.newContentStreamAfter(),page.getResources(),pdf);try(Canvas canvas=new Canvas(pc,page.getPageSize())){String left="DSE ERP | "+r.title()+" | Generated "+r.generatedAt();canvas.showTextAligned(new Paragraph(left).setFontSize(6.5f).setFontColor(new DeviceRgb(90,100,112)),page.getPageSize().getLeft()+28,page.getPageSize().getBottom()+15,TextAlignment.LEFT);canvas.showTextAligned(new Paragraph("Page "+i+" of "+pages).setFontSize(6.5f).setFontColor(new DeviceRgb(90,100,112)),page.getPageSize().getRight()-28,page.getPageSize().getBottom()+15,TextAlignment.RIGHT);}}}}
    private static List<Integer> positions(ReportResult r,Set<String> visible){List<Integer> p=new ArrayList<>();Set<String> keys=visible==null?Set.of():visible;for(int i=0;i<r.columns().size();i++){ReportColumn c=r.columns().get(i);if(keys.isEmpty()?c.defaultVisible():keys.contains(c.key()))p.add(i);}return p;}
    private static String formatMetric(ReportMetric m){return switch(m.format()==null?"":m.format()){case "MONEY"->INR.format(m.value());case "PERCENT"->String.format("%,.2f%%",m.value());case "COUNT"->String.format("%,.0f",m.value());default->String.format("%,.4f",m.value()).replaceAll("\\.?0+$","");};}
    private static String formatValue(ReportColumn c,String raw){if(raw==null)return "";if(!c.numeric())return raw;try{double v=Double.parseDouble(raw);if("MONEY".equals(c.type()))return INR.format(v);if("PERCENT".equals(c.type()))return String.format("%,.2f%%",v);return String.format("%,.4f",v).replaceAll("\\.?0+$","");}catch(Exception e){return raw;}}
    private static String csv(String v){String s=spreadsheetSafe(v);if(s.contains(",")||s.contains("\"")||s.contains("\n"))return "\""+s.replace("\"","\"\"")+"\"";return s;}
    private static String spreadsheetSafe(String v){String s=v==null?"":v;String t=s.stripLeading();if(t.isEmpty())return s;char c=t.charAt(0);boolean numericNegative=c=='-'&&t.matches("-\\d+(?:\\.\\d+)?");return c=='='||c=='+'||c=='@'||(c=='-'&&!numericNegative)?"'"+s:s;}
    private static String join(String...values){StringJoiner j=new StringJoiner("  |  ");for(String v:values)if(v!=null&&!v.isBlank())j.add(v.trim());return j.toString();}
}
