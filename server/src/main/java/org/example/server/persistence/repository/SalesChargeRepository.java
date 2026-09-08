package org.example.server.persistence.repository;

import org.example.server.persistence.entity.SalesChargeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface SalesChargeRepository extends JpaRepository<SalesChargeEntity,Integer> {
    List<SalesChargeEntity> findBySalesIdOrderBySequenceNoAscIdAsc(Integer salesId);
    List<SalesChargeEntity> findBySalesIdInOrderBySalesIdAscSequenceNoAscIdAsc(Collection<Integer> salesIds);
    void deleteBySalesId(Integer salesId);
}
