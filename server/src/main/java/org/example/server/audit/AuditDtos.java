package org.example.server.audit;

import java.util.List;

public final class AuditDtos {
    private AuditDtos() { }
    public record ChangeRow(long id,String fieldName,String oldValue,String newValue) { }
    public record EventRow(long id,String entityType,long entityId,String referenceNo,String action,String category,
                           String detail,String createdBy,String createdAt,String source,String legacySource,
                           List<ChangeRow> changes) { }
    public record GlobalPage(List<EventRow> rows,long total,int page,int size,int totalPages,
                             long businessChanges,long financialEvents,long documentEvents,long communications) { }
}
