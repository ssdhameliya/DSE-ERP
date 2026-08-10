package org.example.server.persistence.repository;
import org.example.server.persistence.entity.PurchaseLineEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PurchaseLineRepository extends JpaRepository<PurchaseLineEntity,Integer>{List<PurchaseLineEntity> findByPurchaseIdOrderByIdAsc(Integer purchaseId); void deleteByPurchaseId(Integer purchaseId);}
