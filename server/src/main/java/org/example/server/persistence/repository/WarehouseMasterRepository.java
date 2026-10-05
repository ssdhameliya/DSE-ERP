package org.example.server.persistence.repository;

import org.example.server.persistence.entity.WarehouseMasterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WarehouseMasterRepository extends JpaRepository<WarehouseMasterEntity, Long> {
    Optional<WarehouseMasterEntity> findByWarehouseCode(String warehouseCode);
    List<WarehouseMasterEntity> findByIsActiveTrueOrderByWarehouseCodeAsc();
    Optional<WarehouseMasterEntity> findByIsDefaultTrue();
}
