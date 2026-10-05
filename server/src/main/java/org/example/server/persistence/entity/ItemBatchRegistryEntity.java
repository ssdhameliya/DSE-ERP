package org.example.server.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "item_batch_registry")
public class ItemBatchRegistryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "batch_number", nullable = false, length = 80)
    private String batchNumber;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity", precision = 12, scale = 4, nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "unit_cost", precision = 15, scale = 4, nullable = false)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "is_quarantine", nullable = false)
    private Boolean isQuarantine = false;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (unitCost == null) unitCost = BigDecimal.ZERO;
        if (isQuarantine == null) isQuarantine = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getItemId() { return itemId; }
    public void setItemId(Integer itemId) { this.itemId = itemId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public Boolean getIsQuarantine() { return isQuarantine; }
    public void setIsQuarantine(Boolean isQuarantine) { this.isQuarantine = isQuarantine; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
