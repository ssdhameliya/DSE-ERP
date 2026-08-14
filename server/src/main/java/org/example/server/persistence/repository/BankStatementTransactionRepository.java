package org.example.server.persistence.repository;

import jakarta.persistence.LockModeType;
import org.example.server.persistence.entity.BankStatementTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BankStatementTransactionRepository extends JpaRepository<BankStatementTransactionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from BankStatementTransactionEntity transaction where transaction.id=:id")
    Optional<BankStatementTransactionEntity> findByIdForUpdate(@Param("id") Long id);
    boolean existsByTransactionFingerprint(String fingerprint);
    List<BankStatementTransactionEntity> findByImportBatchIdOrderByTransactionTimestampAscIdAsc(Long importId);
    long countByImportBatchIdAndStatusIn(Long importId, Collection<String> statuses);
}
