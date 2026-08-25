package org.example.server.persistence.repository;

import org.example.server.persistence.entity.BankReconciliationAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BankReconciliationAllocationRepository extends JpaRepository<BankReconciliationAllocationEntity,Long>{
    List<BankReconciliationAllocationEntity> findByStatementTransactionIdAndReversedAtIsNull(Long id);
    List<BankReconciliationAllocationEntity> findByStatementTransactionIdInAndReversedAtIsNull(Collection<Long> ids);
    List<BankReconciliationAllocationEntity> findByStatementTransactionIdIn(Collection<Long> ids);
    List<BankReconciliationAllocationEntity> findByFinanceEntryIdAndReversedAtIsNull(Integer financeEntryId);
}
