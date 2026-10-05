package org.example.server.persistence.repository;

import org.example.server.persistence.entity.TdsTcsEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TdsTcsEntryRepository extends JpaRepository<TdsTcsEntryEntity, Long> {
    List<TdsTcsEntryEntity> findByPanNumberAndFinancialYear(String panNumber, String financialYear);
    List<TdsTcsEntryEntity> findBySectionCodeAndFinancialYear(String sectionCode, String financialYear);
    List<TdsTcsEntryEntity> findByFinancialYearOrderByCreatedAtDesc(String financialYear);
}
