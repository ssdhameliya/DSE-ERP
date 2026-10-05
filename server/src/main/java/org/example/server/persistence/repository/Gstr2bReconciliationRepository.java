package org.example.server.persistence.repository;

import org.example.server.persistence.entity.Gstr2bReconciliationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface Gstr2bReconciliationRepository extends JpaRepository<Gstr2bReconciliationEntity, Long> {
    List<Gstr2bReconciliationEntity> findByReturnPeriodOrderByInvoiceDateDesc(String returnPeriod);
    List<Gstr2bReconciliationEntity> findByReturnPeriodAndMatchStatus(String returnPeriod, String matchStatus);
    Optional<Gstr2bReconciliationEntity> findBySupplierGstinAndNormalizedInvoiceNoAndReturnPeriod(
            String supplierGstin, String normalizedInvoiceNo, String returnPeriod);
}
