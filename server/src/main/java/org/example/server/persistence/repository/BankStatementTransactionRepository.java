package org.example.server.persistence.repository;
import org.example.server.persistence.entity.BankStatementTransactionEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface BankStatementTransactionRepository extends JpaRepository<BankStatementTransactionEntity,Long>{ boolean existsByTransactionFingerprint(String fingerprint); List<BankStatementTransactionEntity> findByImportBatchIdOrderByTransactionTimestampAscIdAsc(Long importId); long countByImportBatchIdAndStatusIn(Long importId,Collection<String> statuses); }
