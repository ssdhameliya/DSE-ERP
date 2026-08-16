package org.example.server.persistence.repository;

import jakarta.persistence.LockModeType;
import org.example.server.persistence.entity.BankStatementTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
        select * from bank_statement_transaction t
        where t.import_id = :importId
          and (:status = '' or upper(coalesce(t.status,'UNMATCHED')) = :status)
          and (:direction = 'ALL'
               or (:direction = 'CREDIT' and coalesce(t.credit_amount,0) > 0)
               or (:direction = 'DEBIT' and coalesce(t.debit_amount,0) > 0))
          and (:fromDate = '' or coalesce(t.transaction_date,'') >= :fromDate)
          and (:toDate = '' or coalesce(t.transaction_date,'') <= :toDate)
          and (:queryText = ''
               or lower(coalesce(t.original_description,'')) like concat('%', lower(:queryText), '%')
               or lower(coalesce(t.original_reference,'')) like concat('%', lower(:queryText), '%')
               or lower(coalesce(t.status,'')) like concat('%', lower(:queryText), '%')
               or cast(coalesce(t.debit_amount,0) as text) like concat('%', :queryText, '%')
               or cast(coalesce(t.credit_amount,0) as text) like concat('%', :queryText, '%'))
        order by t.transaction_timestamp asc nulls last, t.id asc
        """, countQuery = """
        select count(*) from bank_statement_transaction t
        where t.import_id = :importId
          and (:status = '' or upper(coalesce(t.status,'UNMATCHED')) = :status)
          and (:direction = 'ALL'
               or (:direction = 'CREDIT' and coalesce(t.credit_amount,0) > 0)
               or (:direction = 'DEBIT' and coalesce(t.debit_amount,0) > 0))
          and (:fromDate = '' or coalesce(t.transaction_date,'') >= :fromDate)
          and (:toDate = '' or coalesce(t.transaction_date,'') <= :toDate)
          and (:queryText = ''
               or lower(coalesce(t.original_description,'')) like concat('%', lower(:queryText), '%')
               or lower(coalesce(t.original_reference,'')) like concat('%', lower(:queryText), '%')
               or lower(coalesce(t.status,'')) like concat('%', lower(:queryText), '%')
               or cast(coalesce(t.debit_amount,0) as text) like concat('%', :queryText, '%')
               or cast(coalesce(t.credit_amount,0) as text) like concat('%', :queryText, '%'))
        """, nativeQuery = true)
    Page<BankStatementTransactionEntity> searchPage(@Param("importId") Long importId,
            @Param("status") String status, @Param("direction") String direction,
            @Param("fromDate") String fromDate, @Param("toDate") String toDate,
            @Param("queryText") String queryText, Pageable pageable);

    @Query(value = """
        select count(*) as total,
          coalesce(sum(case when upper(coalesce(status,'UNMATCHED'))='UNMATCHED' then 1 else 0 end),0) as unmatched,
          coalesce(sum(case when upper(coalesce(status,''))='SUGGESTED' then 1 else 0 end),0) as suggested,
          coalesce(sum(case when upper(coalesce(status,''))='MATCHED' then 1 else 0 end),0) as matched,
          coalesce(sum(case when upper(coalesce(status,''))='EXPENSE' then 1 else 0 end),0) as expenses,
          coalesce(sum(case when upper(coalesce(status,''))='IGNORED' then 1 else 0 end),0) as ignored,
          coalesce(sum(case when upper(coalesce(status,''))='REVIEW' then 1 else 0 end),0) as review,
          coalesce(sum(credit_amount),0) as "totalCredits",
          coalesce(sum(debit_amount),0) as "totalDebits",
          coalesce(sum(case when upper(coalesce(status,'')) in ('MATCHED','EXPENSE','IGNORED') then 1 else 0 end),0) as reconciled
        from bank_statement_transaction where import_id=:importId
        """, nativeQuery = true)
    MetricsProjection aggregateMetrics(@Param("importId") Long importId);

    interface MetricsProjection {
        long getTotal(); long getUnmatched(); long getSuggested(); long getMatched();
        long getExpenses(); long getIgnored(); long getReview();
        double getTotalCredits(); double getTotalDebits(); long getReconciled();
    }
}
