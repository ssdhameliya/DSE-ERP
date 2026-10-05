package org.example.server.persistence.repository;

import org.example.server.persistence.entity.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {
    Optional<JournalEntryEntity> findByEntryNumber(String entryNumber);
    List<JournalEntryEntity> findByEntryDateBetweenOrderByEntryDateDescIdDesc(LocalDate startDate, LocalDate endDate);
    List<JournalEntryEntity> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
    List<JournalEntryEntity> findTop100ByOrderByEntryDateDescIdDesc();
}
