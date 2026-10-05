package org.example.server.persistence.repository;

import org.example.server.persistence.entity.ReorderSuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestionEntity, Long> {
    List<ReorderSuggestionEntity> findByStatusOrderByGeneratedAtDesc(String status);
    Optional<ReorderSuggestionEntity> findByItemIdAndStatus(Integer itemId, String status);
}
