package org.example.server.persistence.repository;
import org.example.server.persistence.entity.BankReconciliationAuditEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface BankReconciliationAuditRepository extends JpaRepository<BankReconciliationAuditEntity,Long>{ List<BankReconciliationAuditEntity> findByStatementTransactionIdOrderByIdDesc(Long id); }
