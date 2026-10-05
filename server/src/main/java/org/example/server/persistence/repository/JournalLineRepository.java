package org.example.server.persistence.repository;

import org.example.server.persistence.entity.JournalLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLineEntity, Long> {
    List<JournalLineEntity> findByAccountIdOrderByCreatedAtAsc(Long accountId);
    List<JournalLineEntity> findByPartyIdOrderByCreatedAtAsc(Integer partyId);
}
