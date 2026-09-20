package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.*;
import org.example.documentstudio.model.DocumentSample;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ExcelTemplate;
import org.example.documentstudio.model.TemplateData;
import org.example.config.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One authoritative certification gate for every path that activates an Excel Studio default. */
public final class ExcelDefaultCertification {
    private static final Pattern TOKEN=Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}",Pattern.CASE_INSENSITIVE);
    private ExcelDefaultCertification(){}

    public static void certify(ExcelTemplate template) throws IOException {
        if(template==null||template.getDocumentType()==null)throw new IOException("Excel template document type is missing.");
        DocumentType type=template.getDocumentType();
        if(!DocumentFlowRegistry.isExcelAutomatic(type))throw new IOException(type.label()+" is not ERP-connected and cannot be an automatic Excel default.");
        Path source=ExcelTemplateStorageService.sourceWorkbook(template);
        TemplateData data=realValidationData(type);
        try(Workbook workbook=WorkbookFactory.create(source.toFile())){
            Set<String> mapped=mappedTokens(workbook);
            List<String> missing=new ArrayList<>();
            for(String required:TemplateFieldCatalog.requiredExcelFieldsFor(type))if(!mapped.contains(required.toLowerCase(Locale.ROOT)))missing.add("{{"+required+"}}");
            if(TemplateFieldCatalog.requiresItemRowForDefault(type)&&!ExcelTemplateRenderer.hasCompleteItemRepeatingBlock(workbook))
                missing.add("a complete repeating Items block");
            if(!missing.isEmpty())throw new IOException(type.label()+" Excel mapping is incomplete: "+String.join(", ",missing));
            List<String> unknown=ExcelTemplateRenderer.unknownTokens(workbook,type,data);
            if(!unknown.isEmpty())throw new IOException("Unsupported ERP field(s): "+String.join(", ",unknown));
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Excel default certification could not open the saved workbook: "+root(e),e);}

        Path rendered=WorkspaceManager.getTempFolder().resolve("excel-default-certification-"+template.getId()+"-"+System.nanoTime()+".xlsx");
        try{
            ExcelTemplateRenderer.renderWorkbook(source,type,data,rendered);
            try(Workbook check=WorkbookFactory.create(rendered.toFile())){
                List<String> unresolved=new ArrayList<>();
                for(int si=0;si<check.getNumberOfSheets();si++)for(Row row:check.getSheetAt(si))for(Cell cell:row){
                    if(cell.getCellType()!=CellType.STRING)continue;
                    Matcher matcher=TOKEN.matcher(cell.getStringCellValue());
                    while(matcher.find())unresolved.add(matcher.group(1)+" @ "+check.getSheetName(si)+"!"+cell.getAddress().formatAsString());
                }
                if(!unresolved.isEmpty())throw new IOException("Rendered workbook still contains unresolved ERP fields: "+String.join(", ",unresolved));
            }
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Excel default rendered-output certification failed: "+root(e),e);}
        finally{try{Files.deleteIfExists(rendered);}catch(Exception ignored){}}
    }

    private static TemplateData realValidationData(DocumentType type) throws IOException {
        try{
            if(DocumentDataService.supportsRealData(type)){
                List<DocumentSample> samples=DocumentDataService.listSamples(type);
                if(samples==null||samples.isEmpty())throw new IOException("A real "+type.label()+" record is required before this template can become the default.");
                DocumentSample sample=samples.getFirst();
                TemplateData data=DocumentDataService.load(type,sample.id());
                if(data==null)throw new IOException("The selected "+type.label()+" validation record returned no data.");
                if(TemplateFieldCatalog.requiresItemRowForDefault(type)&&data.items().isEmpty())throw new IOException("The "+type.label()+" validation record contains no line items.");
                return data;
            }
            return DocumentDataService.sample(type);
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Real ERP validation data for "+type.label()+" could not be loaded: "+root(e),e);}
    }

    private static Set<String> mappedTokens(Workbook workbook){
        Set<String> out=new HashSet<>();
        for(int si=0;si<workbook.getNumberOfSheets();si++)for(Row row:workbook.getSheetAt(si))for(Cell cell:row){
            String text=cell.getCellType()==CellType.STRING?cell.getStringCellValue():cell.getCellType()==CellType.FORMULA?cell.getCellFormula():"";
            Matcher m=TOKEN.matcher(text==null?"":text);while(m.find())out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }
    private static String root(Throwable e){Throwable c=e;while(c.getCause()!=null&&c.getCause()!=c)c=c.getCause();return c.getMessage()==null?c.toString():c.getMessage();}
}
