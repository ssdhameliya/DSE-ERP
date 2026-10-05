package org.example.server.persistence.repository;

import org.example.server.persistence.entity.AutomationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AutomationRuleRepository extends JpaRepository<AutomationRuleEntity, Long> {
    Optional<AutomationRuleEntity> findByRuleCode(String ruleCode);
    List<AutomationRuleEntity> findByCategory(String category);
    List<AutomationRuleEntity> findAllByOrderByCategoryAscRuleNameAsc();
}
