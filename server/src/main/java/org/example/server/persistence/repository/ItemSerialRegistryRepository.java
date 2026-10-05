package org.example.server.persistence.repository;

import org.example.server.persistence.entity.ItemSerialRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ItemSerialRegistryRepository extends JpaRepository<ItemSerialRegistryEntity, Long> {
    Optional<ItemSerialRegistryEntity> findBySerialNumber(String serialNumber);
    List<ItemSerialRegistryEntity> findByItemIdAndStatus(Integer itemId, String status);
    List<ItemSerialRegistryEntity> findByItemId(Integer itemId);
}
