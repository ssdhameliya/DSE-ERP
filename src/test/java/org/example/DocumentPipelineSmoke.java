package org.example;
import com.itextpdf.kernel.pdf.*;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.dao.*;
import org.example.database.DatabaseManager;
import org.example.model.*;
import org.example.service.InvoicePdfService;
import java.nio.file.*;import java.sql.*;
public final class DocumentPipelineSmoke {
 public static void main(String[]a)throws Exception{WorkspaceManager.initialize();ConfigManager.load();DatabaseManager.initialize();int sales=0,purchases=0,quotations=0;
  try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT invoice_no FROM sales_header ORDER BY id")){SalesDAO d=new SalesDAO();while(r.next()){String no=r.getString(1);Sales x=d.getByInvoice("  "+no.toLowerCase()+"  ");if(x==null)throw new IllegalStateException("Sales detail lookup failed: "+no);validate(InvoicePdfService.sales(x),no);sales++;}}
  try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT invoice_no FROM purchase_header ORDER BY id")){PurchaseDAO d=new PurchaseDAO();while(r.next()){String no=r.getString(1);Purchase x=d.getByInvoice("  "+no.toLowerCase()+"  ");if(x==null)throw new IllegalStateException("Purchase detail lookup failed: "+no);validate(InvoicePdfService.purchase(x),no);purchases++;}}
  try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT quotation_no FROM quotation_header ORDER BY id")){while(r.next()){String no=r.getString(1);validate(InvoicePdfService.quotation(no),no);quotations++;}}
  System.out.println("DOCUMENT_PIPELINE_OK sales="+sales+" purchases="+purchases+" quotations="+quotations);
 }
 private static void validate(Path p,String no)throws Exception{if(!Files.exists(p)||Files.size(p)<100)throw new IllegalStateException("Missing PDF "+no);try(PdfDocument pdf=new PdfDocument(new PdfReader(p.toFile()))){if(pdf.getNumberOfPages()<1)throw new IllegalStateException("PDF has no pages "+no);}}
}
