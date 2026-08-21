package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.documentstudio.model.TemplateData;
import org.example.invoice.model.TaxInvoiceItem;

import java.util.List;
import java.util.Map;

/** Regression check: inactive tax branches and absent charges render as visible numeric 0.00. */
public final class ExcelTemplateVisibleZeroSmoke {
    private ExcelTemplateVisibleZeroSmoke() { }

    public static void main(String[] args) throws Exception {
        TemplateData data=new TemplateData(Map.of("totals.subtotal","100.00"),Map.of(),List.of(new TaxInvoiceItem(1,"1","Item","",1,"NOS",100,0,18)),List.of(),"GST");
        try(Workbook workbook=new XSSFWorkbook()){
            var sheet=workbook.createSheet("Invoice");
            sheet.createRow(0).createCell(0).setCellValue("{{totals.igstAmount}}");
            sheet.createRow(1).createCell(0).setCellValue("{{totals.chargesAmount}}");
            sheet.createRow(2).createCell(0).setCellValue("{{totals.subtotal}}");
            sheet.createRow(3).createCell(0).setCellValue("{{totals.cgstAmount}}");
            sheet.createRow(4).createCell(0).setCellValue("{{totals.roundedGrandTotal}}");
            ExcelTemplateRenderer.fillWorkbook(workbook,data,List.of());
            assertVisibleZero(sheet.getRow(0).getCell(0));
            assertVisibleZero(sheet.getRow(1).getCell(0));
            assertTwoDecimals(sheet.getRow(2).getCell(0),100d);
            assertTwoDecimals(sheet.getRow(3).getCell(0),9d);
            assertTwoDecimals(sheet.getRow(4).getCell(0),118d);
        }
        System.out.println("EXCEL_VISIBLE_ZERO_TOTALS_OK");
    }

    private static void assertVisibleZero(Cell cell){
        assertTwoDecimals(cell,0d);
    }

    private static void assertTwoDecimals(Cell cell,double expected){
        if(cell.getCellType()!=CellType.NUMERIC||cell.getNumericCellValue()!=expected)throw new AssertionError("Expected numeric "+expected);
        if(!cell.getCellStyle().getDataFormatString().contains("0.00"))throw new AssertionError("Zero format does not display 0.00: "+cell.getCellStyle().getDataFormatString());
    }
}
