package org.example.server.customer360;

import java.math.BigDecimal;
import java.util.List;

public final class Customer360Dtos {
    private Customer360Dtos() {}

    public record Customer(int id,String code,String name,String contactPerson,String phone,String email,
                           String gstin,String address,BigDecimal openingBalance,boolean active,long rowVersion) {}
    public record Summary(Customer customer,BigDecimal outstandingReceivable,BigDecimal openQuotationValue,long openQuotationCount,
                          BigDecimal openSalesOrderValue,long openSalesOrderCount,long activeProjectCount,
                          BigDecimal totalSales,BigDecimal lastPaymentAmount,String lastPaymentDate,
                          List<QuotationRow> recentQuotations,List<WorkflowRow> recentSalesOrders,List<InvoiceRow> recentInvoices) {}
    public record QuotationRow(int id,String no,String date,String valid,String salesperson,BigDecimal amount,String status,String followUp) {}
    public record WorkflowRow(int id,String documentType,String documentNo,String documentDate,String projectNo,String parentNo,
                              String customerPoNo,String expectedDate,BigDecimal totalAmount,String status) {}
    public record InvoiceRow(int id,String invoiceNo,String invoiceDate,String salesOrderNo,String projectNo,BigDecimal totalAmount,
                             BigDecimal paidAmount,BigDecimal outstanding,String paymentStatus,String documentStatus) {}
    public record PaymentRow(int id,String paymentDate,String referenceNo,String paymentMode,BigDecimal amount,String invoiceNo,String notes) {}
    public record ContactRow(long id,int partyId,String name,String designation,String department,String mobile,String email,
                             boolean primary,String notes,long rowVersion,String createdBy,String createdAt,String updatedBy,String updatedAt) {}
    public record ContactSave(Long id,String name,String designation,String department,String mobile,String email,boolean primary,String notes,long rowVersion) {}
    public record NoteRow(long id,int partyId,String note,String createdBy,String createdAt,String updatedBy,String updatedAt,long rowVersion) {}
    public record NoteSave(Long id,String note,long rowVersion) {}
    public record Ok(boolean success,String message) {}
}
