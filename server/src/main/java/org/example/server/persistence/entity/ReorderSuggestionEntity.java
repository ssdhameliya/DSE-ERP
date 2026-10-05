package org.example.server.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reorder_suggestion")
public class ReorderSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    @Column(name = "current_stock", precision = 12, scale = 4, nullable = false)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "reorder_level", precision = 12, scale = 4, nullable = false)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "suggested_qty", precision = 12, scale = 4, nullable = false)
    private BigDecimal suggestedQty = BigDecimal.ZERO;

    @Column(name = "primary_supplier_id")
    private Integer primarySupplierId;

    @Column(name = "estimated_cost", precision = 15, scale = 4, nullable = false)
    private BigDecimal estimatedCost = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "draft_po_id")
    private Long draftPoId;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (generatedAt == null) generatedAt = LocalDateTime.now();
        if (status == null) status = "OPEN";
        if (currentStock == null) currentStock = BigDecimal.ZERO;
        if (reorderLevel == null) reorderLevel = BigDecimal.ZERO;
        if (suggestedQty == null) suggestedQty = BigDecimal.ZERO;
        if (estimatedCost == null) estimatedCost = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getItemId() { return itemId; }
    public void setItemId(Integer itemId) { this.itemId = itemId; }
    public BigDecimal getCurrentStock() { return currentStock; }
    public void setCurrentStock(BigDecimal currentStock) { this.currentStock = currentStock; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
    public BigDecimal getSuggestedQty() { return suggestedQty; }
    public void setSuggestedQty(BigDecimal suggestedQty) { this.suggestedQty = suggestedQty; }
    public Integer getPrimarySupplierId() { return primarySupplierId; }
    public void setPrimarySupplierId(Integer primarySupplierId) { this.primarySupplierId = primarySupplierId; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDraftPoId() { return draftPoId; }
    public void setDraftPoId(Long draftPoId) { this.draftPoId = draftPoId; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
