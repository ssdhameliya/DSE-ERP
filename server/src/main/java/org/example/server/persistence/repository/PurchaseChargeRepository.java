package org.example.server.persistence.repository;

import org.example.server.persistence.entity.PurchaseChargeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface PurchaseChargeRepository extends JpaRepository<PurchaseChargeEntity,Integer> {
    List<PurchaseChargeEntity> findByPurchaseIdOrderBySequenceNoAscIdAsc(Integer purchaseId);
    List<PurchaseChargeEntity> findByPurchaseIdInOrderByPurchaseIdAscSequenceNoAscIdAsc(Collection<Integer> purchaseIds);
    void deleteByPurchaseId(Integer purchaseId);
}
