package org.example.server.persistence.repository;

import org.example.server.persistence.entity.ThreeWayMatchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ThreeWayMatchLogRepository extends JpaRepository<ThreeWayMatchLogEntity, Long> {
    List<ThreeWayMatchLogEntity> findByMatchStatusOrderByMatchedAtDesc(String matchStatus);
    List<ThreeWayMatchLogEntity> findTop100ByOrderByMatchedAtDesc();
}
