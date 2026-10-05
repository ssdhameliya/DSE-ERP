package org.example.server.persistence.repository;

import org.example.server.persistence.entity.ItemBatchRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ItemBatchRegistryRepository extends JpaRepository<ItemBatchRegistryEntity, Long> {
    List<ItemBatchRegistryEntity> findByItemIdOrderByExpiryDateAsc(Integer itemId);
    Optional<ItemBatchRegistryEntity> findByItemIdAndBatchNumber(Integer itemId, String batchNumber);
}
