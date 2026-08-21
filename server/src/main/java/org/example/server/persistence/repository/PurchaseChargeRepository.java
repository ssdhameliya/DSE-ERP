package org.example.server.persistence.repository;

import org.example.server.persistence.entity.PurchaseChargeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PurchaseChargeRepository extends JpaRepository<PurchaseChargeEntity,Integer> {
    List<PurchaseChargeEntity> findByPurchaseIdOrderBySequenceNoAscIdAsc(Integer purchaseId);
    void deleteByPurchaseId(Integer purchaseId);
}
