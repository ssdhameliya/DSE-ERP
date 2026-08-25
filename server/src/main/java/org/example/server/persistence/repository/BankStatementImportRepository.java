package org.example.server.persistence.repository;

import org.example.server.persistence.entity.BankStatementImportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BankStatementImportRepository extends JpaRepository<BankStatementImportEntity,Long>{
 Optional<BankStatementImportEntity> findBySourceFingerprint(String fingerprint);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from BankStatementImportEntity b where b.id=:id") Optional<BankStatementImportEntity> findByIdForUpdate(@Param("id") Long id);
 @Query("select b from BankStatementImportEntity b where " +
        "(:account='' or lower(coalesce(b.bankAccount,'')) like lower(concat('%',:account,'%')) or lower(coalesce(b.bankName,'')) like lower(concat('%',:account,'%'))) and " +
        "(:status='' or upper(coalesce(b.status,''))=upper(:status)) and " +
        "(:fromDate='' or coalesce(b.statementTo,'') >= :fromDate) and " +
        "(:toDate='' or coalesce(b.statementFrom,'') <= :toDate) and " +
        "(:q='' or lower(coalesce(b.bankName,'')) like lower(concat('%',:q,'%')) or lower(coalesce(b.bankAccount,'')) like lower(concat('%',:q,'%')) or lower(coalesce(b.accountHolder,'')) like lower(concat('%',:q,'%')) or lower(coalesce(b.sourceFileName,'')) like lower(concat('%',:q,'%')) or lower(coalesce(b.importedBy,'')) like lower(concat('%',:q,'%')))")
 Page<BankStatementImportEntity> search(@Param("account") String account,@Param("status") String status,@Param("fromDate") String fromDate,@Param("toDate") String toDate,@Param("q") String q,Pageable pageable);
}
