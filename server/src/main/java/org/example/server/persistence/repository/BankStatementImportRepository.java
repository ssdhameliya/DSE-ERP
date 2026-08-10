package org.example.server.persistence.repository;
import org.example.server.persistence.entity.BankStatementImportEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface BankStatementImportRepository extends JpaRepository<BankStatementImportEntity,Long>{ Optional<BankStatementImportEntity> findBySourceFingerprint(String fingerprint); List<BankStatementImportEntity> findAllByOrderByImportedAtDesc(); }
