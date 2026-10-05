package org.example.server.persistence.repository;

import org.example.server.persistence.entity.ChartOfAccountsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccountsEntity, Long> {
    Optional<ChartOfAccountsEntity> findByAccountCode(String accountCode);
    List<ChartOfAccountsEntity> findAllByIsActiveTrueOrderByAccountCodeAsc();
    List<ChartOfAccountsEntity> findByAccountTypeOrderByAccountCodeAsc(String accountType);
}
